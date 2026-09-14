package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.NetworkAttachment;
import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Правка ОДНОГО подключения ({@link NetworkAttachment}) устройства к
 * конкретной сети — IP/маска/шлюз/список портов этой сети (Round 8: адрес
 * переехал сюда с самого устройства, т.к. одно устройство теперь может
 * состоять в нескольких сетях сразу с разными адресами, см. javadoc {@link
 * NetworkAttachment}). Вызывается из {@code NetworkCanvasPanel#showDeviceMenu}
 * — либо на НОВОЕ подключение («Подключить к сети…», {@code attachment}
 * создан вызывающей стороной пустым и ещё не добавлен в {@code
 * device.getAttachments()} — добавляется, только если пользователь нажал
 * «Сохранить»), либо на СУЩЕСТВУЮЩЕЕ («Параметры подключения…»).
 *
 * <p>{@link #portsField} — список номеров портов ЭТОЙ сети, через запятую;
 * пусто = "все порты устройства" (см. {@link NetworkAttachment#coversPort}) —
 * нормальный случай для устройства в одной сети. Явный список нужен только
 * когда устройство разнесено на несколько сетей и порты нужно поделить между
 * ними — НЕ проверяется на пересечение с портами других подключений этого же
 * устройства (тот же принцип "не обязано быть строго провалидировано", что у
 * {@code NetworkLink#getToPort()} после уменьшения portCount, см. её
 * javadoc). */
public class NetworkAttachmentDialog extends JDialog {

    private final JTextField ipField = new JTextField();
    private final JTextField maskField = new JTextField();
    private final JTextField gatewayField = new JTextField();
    private final JTextField portsField = new JTextField();
    private final NetworkAttachment attachment;
    private final NetworkDevicePlacement device;
    private boolean saved;

    public NetworkAttachmentDialog(Window owner, String networkName, NetworkDevicePlacement device,
                                    NetworkAttachment attachment) {
        super(owner, "Подключение к сети «" + networkName + "»", ModalityType.APPLICATION_MODAL);
        this.attachment = attachment;
        this.device = device;
        ipField.setText(attachment.getIpAddress());
        maskField.setText(attachment.getSubnetMask());
        gatewayField.setText(attachment.getGateway());
        portsField.setText(formatPorts(attachment.getPorts()));
        portsField.setToolTipText("Номера портов ЭТОЙ сети через запятую (например \"1,2\") — пусто значит"
                + " \"все порты устройства\" (обычный случай, если устройство состоит только в одной сети)");

        // Удобство (как раньше на самом устройстве): если веб-URL ещё пуст, при потере
        // фокуса полем IP подставляем http://<ip> ОДИН РАЗ -- см. класс-javadoc
        // NetworkDeviceParamsDialog про то же самое поведение, перенесённое сюда вместе
        // с адресом.
        ipField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String ip = ipField.getText().trim();
                if (!ip.isEmpty() && device.getWebInterfaceUrl().isBlank()) {
                    device.setWebInterfaceUrl("http://" + ip);
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
        top.add(row("Порты этой сети", portsField));
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
        setPreferredSize(new java.awt.Dimension(380, 240));
        pack();
        setLocationRelativeTo(owner);
    }

    private static JPanel row(String label, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(new JLabel(label), BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private void onOk() {
        List<Integer> ports;
        try {
            ports = parsePorts(portsField.getText());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Список портов — числа через запятую (например \"1,2\") или пусто",
                    "Проверка данных", JOptionPane.WARNING_MESSAGE);
            return;
        }
        attachment.setIpAddress(ipField.getText().trim());
        attachment.setSubnetMask(maskField.getText().trim());
        attachment.setGateway(gatewayField.getText().trim());
        attachment.setPorts(ports);
        saved = true;
        dispose();
    }

    static String formatPorts(List<Integer> ports) {
        if (ports.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ports.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(ports.get(i));
        }
        return sb.toString();
    }

    static List<Integer> parsePorts(String text) {
        String trimmed = text == null ? "" : text.trim();
        List<Integer> result = new ArrayList<>();
        if (trimmed.isEmpty()) {
            return result;
        }
        for (String part : trimmed.split(",")) {
            String p = part.trim();
            if (!p.isEmpty()) {
                result.add(Integer.parseInt(p));
            }
        }
        return result;
    }

    /** Показывает диалог; {@code true} — пользователь нажал «Сохранить»
     *  (attachment уже изменён на месте — вызывающая сторона сама решает,
     *  добавлять ли его в {@code device.getAttachments()} для НОВОГО
     *  подключения, см. class-javadoc). */
    public boolean showDialog() {
        setVisible(true);
        return saved;
    }
}
