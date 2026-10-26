package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.ReceiverCardType;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Создание/редактирование СВОЕЙ приёмной карты модульных экранов прямо в клиенте (по образцу {@link
 *  CaseTypeDialog}): модальная форма, {@link #showDialog()} возвращает заполненный объект или {@code null}
 *  при отмене; {@code AppModel.addReceiverCardType}/{@code updateReceiverCardType} и «Предложить» вызывает
 *  вызывающий код. Числа необязательны — пустое поле остаётся «не указано» (см. {@link OptionalNumberFields}).
 *  Физический блок (габариты) формой не редактируется — он сохраняется как есть и правится в админ-консоли. */
public class ReceiverCardTypeDialog extends JDialog {

    private final JTextField nameField = new JTextField();
    private final JTextField vendorField = new JTextField();
    private final JTextField companyField = new JTextField();
    private final JTextField outputsField = new JTextField();
    private final JTextField ribbonField = new JTextField();
    private final JTextField maxWidthField = new JTextField();
    private final JTextField maxHeightField = new JTextField();
    private final JTextField maxPixelsField = new JTextField();
    private final JTextField powerField = new JTextField();
    private final JTextField weightField = new JTextField();
    private final JTextArea notesArea = new JTextArea(3, 24);
    private final ReceiverCardType existing;
    private ReceiverCardType result;

    public ReceiverCardTypeDialog(Window owner, ReceiverCardType existing) {
        super(owner, existing == null ? "Новая приёмная карта" : "Редактирование приёмной карты",
                ModalityType.APPLICATION_MODAL);
        this.existing = existing;
        if (existing != null) {
            nameField.setText(existing.getName());
            vendorField.setText(existing.getVendor() == null ? "" : existing.getVendor());
            companyField.setText(existing.getCompany() == null ? "" : existing.getCompany());
            outputsField.setText(OptionalNumberFields.text(existing.getModuleOutputs()));
            ribbonField.setText(OptionalNumberFields.text(existing.getRibbonLengthMm()));
            maxWidthField.setText(OptionalNumberFields.text(existing.getMaxWidthPx()));
            maxHeightField.setText(OptionalNumberFields.text(existing.getMaxHeightPx()));
            maxPixelsField.setText(OptionalNumberFields.text(existing.getMaxPixels()));
            powerField.setText(OptionalNumberFields.text(existing.getPowerW()));
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
        top.add(row("Выходов на модули (шлейфов)", outputsField));
        top.add(row("Длина шлейфа до модуля, мм (по прямой)", ribbonField));
        top.add(row("Макс. ширина области, px", maxWidthField));
        top.add(row("Макс. высота области, px", maxHeightField));
        top.add(row("Макс. пикселей карты", maxPixelsField));
        top.add(row("Мощность карты, Вт", powerField));
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
            JOptionPane.showMessageDialog(this, "Укажите название приёмной карты", "Проверка данных",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            ReceiverCardType t = new ReceiverCardType();
            if (existing != null) {
                t.setId(existing.getId());
                // Габариты формой не правятся — переносим как есть, иначе правка карты их бы стёрла.
                t.setPhysical(existing.getPhysical() != null ? existing.getPhysical().copy() : null);
            }
            t.setName(name);
            t.setVendor(vendorField.getText().trim());
            t.setCompany(companyField.getText().trim());
            t.setModuleOutputs(OptionalNumberFields.parseInt(outputsField, "Выходов на модули"));
            t.setRibbonLengthMm(OptionalNumberFields.parse(ribbonField, "Длина шлейфа"));
            t.setMaxWidthPx(OptionalNumberFields.parseInt(maxWidthField, "Макс. ширина"));
            t.setMaxHeightPx(OptionalNumberFields.parseInt(maxHeightField, "Макс. высота"));
            t.setMaxPixels(OptionalNumberFields.parseInt(maxPixelsField, "Макс. пикселей"));
            t.setPowerW(OptionalNumberFields.parse(powerField, "Мощность"));
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
    public ReceiverCardType showDialog() {
        setVisible(true);
        return result;
    }
}
