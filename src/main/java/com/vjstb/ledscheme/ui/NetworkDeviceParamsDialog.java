package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/** Правка сетевых параметров одного устройства (IP/маска/шлюз/URL веб-
 *  интерфейса/примечание) — ПКМ по блоку → «Параметры сети…» в {@link
 *  NetworkCanvasPanel}. Мутирует переданный {@link NetworkDevicePlacement}
 *  НАПРЯМУЮ (не копирует и не создаёт новый) — вызывающая сторона сама решает,
 *  когда персистить (см. {@link NetworkCanvasPanel#editParams}).
 *
 * <p>{@link #hasWebInterfaceCheck} (баг-репорт: "добавь в параметры блоков
 * менеджера галочку есть ли веб интерфейс, если стоит галочка — можно
 * управлять через него") — ОТДЕЛЬНЫЙ флаг от самого поля URL (см. javadoc
 * {@link NetworkDevicePlacement#isHasWebInterface}): непустой URL сам по себе
 * не означает, что устройство реально управляемо через браузер (поле URL
 * автоматически подставляется по IP ниже, даже для устройств без веб-
 * интерфейса вообще). Пока флаг снят — поле URL заблокировано (текст
 * сохраняется, просто недоступен для правки, чтобы не создавать иллюзию, что
 * он используется), пункт ПКМ «Открыть веб-интерфейс» на канвасе смотрит
 * именно на этот флаг, не на непустоту URL.
 *
 * <p>Удобство (запрос из плана фичи): если поле URL веб-интерфейса ещё пусто,
 * при потере фокуса полем IP оно ОДИН РАЗ подставляется как {@code
 * http://<ip>} — стартовое предложение, не насильно на каждое сохранение
 * (пользователь может стереть/переписать под https/нестандартный порт). Сам
 * по себе автоподстановка НЕ включает {@link #hasWebInterfaceCheck} —
 * пользователь должен явно подтвердить, что веб-интерфейс есть.
 *
 * <p>Число портов ({@link #portCountSpinner}) РЕДАКТИРУЕМО только для устройств,
 * связанных с узлом общей схемы ({@code linkedSchemaNodeId != null}) — у тех нет
 * каталожного типа, источника для этого числа больше неоткуда взять. Для
 * устройств из каталога ({@code deviceTypeId != null}) число портов — паспортная
 * величина ТИПА (баг-репорт: "количество портов ethernet для устройств должно
 * определяться в параметрах"), спиннер здесь только ПОКАЗЫВАЕТ текущее значение
 * из библиотеки и заблокирован — см. {@code typePortCount} и {@link
 * NetworkCanvasPanel#editParams}, который его вычисляет. */
public class NetworkDeviceParamsDialog extends JDialog {

    private final JTextField ipField = new JTextField();
    private final JTextField maskField = new JTextField();
    private final JTextField gatewayField = new JTextField();
    private final JTextField webUrlField = new JTextField();
    private final JCheckBox hasWebInterfaceCheck = new JCheckBox("Есть веб-интерфейс (можно управлять через браузер)");
    private final JTextField noteField = new JTextField();
    private final JSpinner portCountSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 128, 1));
    private final NetworkDevicePlacement device;
    private boolean saved;

    /** {@code typePortCount} — не-null, если устройство каталожное: спиннер тогда
     *  показывает ЭТО значение и блокируется (см. class-javadoc). */
    public NetworkDeviceParamsDialog(Window owner, NetworkDevicePlacement device, String deviceLabel,
                                      Integer typePortCount) {
        super(owner, "Параметры сети — " + deviceLabel, ModalityType.APPLICATION_MODAL);
        this.device = device;
        ipField.setText(device.getIpAddress());
        maskField.setText(device.getSubnetMask());
        gatewayField.setText(device.getGateway());
        webUrlField.setText(device.getWebInterfaceUrl());
        hasWebInterfaceCheck.setSelected(device.isHasWebInterface());
        webUrlField.setEnabled(device.isHasWebInterface());
        hasWebInterfaceCheck.addActionListener(e -> webUrlField.setEnabled(hasWebInterfaceCheck.isSelected()));
        noteField.setText(device.getNote());
        if (typePortCount != null) {
            portCountSpinner.setValue(typePortCount);
            portCountSpinner.setEnabled(false);
            portCountSpinner.setToolTipText("Определяется типом оборудования в библиотеке — измените там");
        } else {
            portCountSpinner.setValue(Math.max(1, device.getPortCount()));
        }

        ipField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String ip = ipField.getText().trim();
                if (!ip.isEmpty() && webUrlField.getText().isBlank()) {
                    webUrlField.setText("http://" + ip);
                }
            }
        });

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(row("IP-адрес", ipField));
        top.add(row("Маска подсети", maskField));
        top.add(row("Шлюз", gatewayField));
        top.add(hasWebInterfaceCheck);
        top.add(row("Веб-интерфейс (URL)", webUrlField));
        top.add(row("Число портов", portCountSpinner));
        top.add(row("Примечание", noteField));
        content.add(top, BorderLayout.NORTH);

        JButton ok = new JButton("Сохранить");
        ok.addActionListener(e -> onOk());
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(cancel);
        buttons.add(ok);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        setPreferredSize(new java.awt.Dimension(420, 290));
        pack();
        setLocationRelativeTo(owner);
    }

    private static JPanel row(String label, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(new JLabel(label), BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private static JPanel row(String label, JSpinner field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(new JLabel(label), BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private void onOk() {
        device.setIpAddress(ipField.getText().trim());
        device.setSubnetMask(maskField.getText().trim());
        device.setGateway(gatewayField.getText().trim());
        device.setWebInterfaceUrl(webUrlField.getText().trim());
        device.setHasWebInterface(hasWebInterfaceCheck.isSelected());
        device.setNote(noteField.getText().trim());
        if (portCountSpinner.isEnabled()) {
            device.setPortCount((Integer) portCountSpinner.getValue());
        }
        saved = true;
        dispose();
    }

    /** Показывает диалог; возвращает {@code true}, если пользователь нажал
     *  «Сохранить» (устройство уже изменено на месте), {@code false} при отмене. */
    public boolean showDialog() {
        setVisible(true);
        return saved;
    }
}
