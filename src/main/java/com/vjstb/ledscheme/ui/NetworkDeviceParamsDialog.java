package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
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

/** Параметры ОДНОГО устройства (не сети — см. {@link NetworkAttachmentDialog}
 *  для IP/маски/шлюза конкретного подключения) — ПКМ по блоку → «Параметры
 *  устройства…» в {@link NetworkCanvasPanel}. Мутирует переданный {@link
 *  NetworkDevicePlacement} НАПРЯМУЮ, вызывающая сторона сама решает, когда
 *  персистить (см. {@link NetworkCanvasPanel#editParams}).
 *
 * <p><b>Round 8</b> — IP/маска/шлюз/список портов конкретной сети переехали в
 * {@link NetworkAttachmentDialog} (устройство теперь может состоять в
 * нескольких сетях одновременно с разными адресами, см. {@code
 * model.NetworkAttachment}). Здесь остались только поля самого устройства,
 * общие для всех его подключений: наличие/URL веб-интерфейса, примечание, и
 * число ethernet/оптических портов — РАЗДЕЛЬНО (см. {@link
 * #ethernetPortSpinner}/{@link #opticalPortSpinner}), НЕ путать с портами
 * вывода видео на экран у контроллера (явное уточнение пользователя: "в
 * контроллерах ethernet порты для экранов и сетевой порт — это разные
 * порты") — здесь именно порты УПРАВЛЕНИЯ/сети.
 *
 * <p>{@link #hasWebInterfaceCheck} (баг-репорт: "добавь в параметры блоков
 * менеджера галочку есть ли веб интерфейс, если стоит галочка — можно
 * управлять через него") — ОТДЕЛЬНЫЙ флаг от самого поля URL: непустой URL
 * сам по себе не означает, что устройство реально управляемо через браузер.
 * Пока флаг снят — поле URL заблокировано (текст сохраняется, просто
 * недоступен для правки), пункт ПКМ «Открыть веб-интерфейс» на канвасе
 * смотрит именно на этот флаг, не на непустоту URL.
 *
 * <p>Число портов ({@link #ethernetPortSpinner}/{@link #opticalPortSpinner})
 * РЕДАКТИРУЕМО только для устройств, связанных с узлом общей схемы ({@code
 * linkedSchemaNodeId != null}) — у тех нет каталожного типа, источника для
 * этого числа больше неоткуда взять. Для устройств из каталога ({@code
 * deviceTypeId != null}) число портов — паспортная величина ТИПА, спиннеры
 * здесь только ПОКАЗЫВАЮТ значения из библиотеки и заблокированы — см. {@code
 * typeEthernetPortCount}/{@code typeOpticalPortCount} и {@link
 * NetworkCanvasPanel#editParams}, который их вычисляет. */
public class NetworkDeviceParamsDialog extends JDialog {

    private final JTextField webUrlField = new JTextField();
    private final JCheckBox hasWebInterfaceCheck = new JCheckBox("Есть веб-интерфейс (можно управлять через браузер)");
    private final JTextField noteField = new JTextField();
    private final JSpinner ethernetPortSpinner = new JSpinner(new SpinnerNumberModel(4, 0, 128, 1));
    private final JSpinner opticalPortSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 128, 1));
    private final NetworkDevicePlacement device;
    private boolean saved;

    /** {@code typeEthernetPortCount}/{@code typeOpticalPortCount} — не-null, если
     *  устройство каталожное: спиннеры тогда показывают ЭТИ значения и
     *  блокируются (см. class-javadoc). */
    public NetworkDeviceParamsDialog(Window owner, NetworkDevicePlacement device, String deviceLabel,
                                      Integer typeEthernetPortCount, Integer typeOpticalPortCount) {
        super(owner, "Параметры устройства — " + deviceLabel, ModalityType.APPLICATION_MODAL);
        this.device = device;
        webUrlField.setText(device.getWebInterfaceUrl());
        hasWebInterfaceCheck.setSelected(device.isHasWebInterface());
        webUrlField.setEnabled(device.isHasWebInterface());
        hasWebInterfaceCheck.addActionListener(e -> webUrlField.setEnabled(hasWebInterfaceCheck.isSelected()));
        noteField.setText(device.getNote());

        boolean catalogPorts = typeEthernetPortCount != null;
        ethernetPortSpinner.setValue(catalogPorts ? typeEthernetPortCount : Math.max(0, device.getEthernetPortCount()));
        opticalPortSpinner.setValue(catalogPorts ? typeOpticalPortCount : Math.max(0, device.getOpticalPortCount()));
        ethernetPortSpinner.setEnabled(!catalogPorts);
        opticalPortSpinner.setEnabled(!catalogPorts);
        if (catalogPorts) {
            String tip = "Определяется типом оборудования в библиотеке — измените там";
            ethernetPortSpinner.setToolTipText(tip);
            opticalPortSpinner.setToolTipText(tip);
        }

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(hasWebInterfaceCheck);
        top.add(row("Веб-интерфейс (URL)", webUrlField));
        top.add(row("Сетевых портов (Ethernet)", ethernetPortSpinner));
        top.add(row("Оптических портов", opticalPortSpinner));
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
        setPreferredSize(new java.awt.Dimension(420, 260));
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
        device.setWebInterfaceUrl(webUrlField.getText().trim());
        device.setHasWebInterface(hasWebInterfaceCheck.isSelected());
        device.setNote(noteField.getText().trim());
        if (ethernetPortSpinner.isEnabled()) {
            device.setEthernetPortCount((Integer) ethernetPortSpinner.getValue());
            device.setOpticalPortCount((Integer) opticalPortSpinner.getValue());
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
