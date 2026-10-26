package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.PowerSupplyType;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Создание/редактирование СВОЕГО блока питания модульных экранов прямо в клиенте (по образцу {@link
 *  ReceiverCardTypeDialog}). «Отдельный выход на карту» — +1 выход сверх шлейфов, только под приёмную карту:
 *  с ним карта в области блока питается от него, без него занимает один из шлейфов (решение D19 плана).
 *  Числа необязательны — пустое поле остаётся «не указано». Физический блок формой не редактируется. */
public class PowerSupplyTypeDialog extends JDialog {

    private final JTextField nameField = new JTextField();
    private final JTextField vendorField = new JTextField();
    private final JTextField companyField = new JTextField();
    private final JTextField outputsField = new JTextField();
    private final JTextField ratedPowerField = new JTextField();
    private final JTextField cableLengthField = new JTextField();
    private final JCheckBox cardOutputBox = new JCheckBox("Отдельный выход на карту (+1 выход сверх шлейфов, только под карту)");
    private final JTextField inputVoltageField = new JTextField();
    private final JTextField weightField = new JTextField();
    private final JTextArea notesArea = new JTextArea(3, 24);
    private final PowerSupplyType existing;
    private PowerSupplyType result;

    public PowerSupplyTypeDialog(Window owner, PowerSupplyType existing) {
        super(owner, existing == null ? "Новый блок питания" : "Редактирование блока питания",
                ModalityType.APPLICATION_MODAL);
        this.existing = existing;
        if (existing != null) {
            nameField.setText(existing.getName());
            vendorField.setText(existing.getVendor() == null ? "" : existing.getVendor());
            companyField.setText(existing.getCompany() == null ? "" : existing.getCompany());
            outputsField.setText(OptionalNumberFields.text(existing.getOutputs()));
            ratedPowerField.setText(OptionalNumberFields.text(existing.getRatedPowerW()));
            cableLengthField.setText(OptionalNumberFields.text(existing.getCableLengthMm()));
            cardOutputBox.setSelected(existing.isCardOutput());
            inputVoltageField.setText(existing.getInputVoltage() == null ? "" : existing.getInputVoltage());
            weightField.setText(OptionalNumberFields.text(existing.getWeightKg()));
            notesArea.setText(existing.getNotes() == null ? "" : existing.getNotes());
        }
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(row("Название", nameField));
        top.add(row("Производитель", vendorField));
        top.add(row("Компания (чья техника)", companyField));
        top.add(row("Шлейфов питания модулей", outputsField));
        top.add(row("Номинальная мощность, Вт", ratedPowerField));
        top.add(row("Длина кабеля до модуля, мм (по прямой)", cableLengthField));
        JPanel cardRow = new JPanel(new BorderLayout(6, 0));
        cardRow.add(cardOutputBox, BorderLayout.WEST);
        top.add(cardRow);
        top.add(row("Входное напряжение (справочно)", inputVoltageField));
        top.add(row("Вес, кг", weightField));
        top.add(row("Заметки", new JScrollPane(notesArea)));
        top.add(new JLabel("Пустое числовое поле — «не указано», такой параметр не проверяется."));
        content.add(top, BorderLayout.NORTH);

        JButton ok = new JButton("Сохранить");
        ok.addActionListener(e -> onOk());
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(cancel);
        buttons.add(ok);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    private static JPanel row(String label, java.awt.Component field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(new JLabel(label), BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private void onOk() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Укажите название блока питания", "Проверка данных",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            PowerSupplyType t = new PowerSupplyType();
            if (existing != null) {
                t.setId(existing.getId());
                // Габариты формой не правятся — переносим как есть, иначе правка блока их бы стёрла.
                t.setPhysical(existing.getPhysical() != null ? existing.getPhysical().copy() : null);
            }
            t.setName(name);
            t.setVendor(vendorField.getText().trim());
            t.setCompany(companyField.getText().trim());
            t.setOutputs(OptionalNumberFields.parseInt(outputsField, "Шлейфов питания"));
            t.setRatedPowerW(OptionalNumberFields.parse(ratedPowerField, "Номинальная мощность"));
            t.setCableLengthMm(OptionalNumberFields.parse(cableLengthField, "Длина кабеля"));
            t.setCardOutput(cardOutputBox.isSelected());
            t.setInputVoltage(inputVoltageField.getText().trim());
            t.setWeightKg(OptionalNumberFields.parse(weightField, "Вес"));
            t.setNotes(notesArea.getText().trim());
            result = t;
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Проверка данных", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Показывает диалог; возвращает заполненные данные (с id существующей записи, если редактируем,
     *  иначе новым сгенерированным) или null при отмене. */
    public PowerSupplyType showDialog() {
        setVisible(true);
        return result;
    }
}
