package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CaseType;
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
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/** Создание/редактирование СВОЕГО типа кофра прямо в клиенте (запрос
 *  пользователя — калькулятор транспорта не должен упираться в то, что
 *  библиотека не предусмотрела какой-то конкретный кофр). По образцу {@link
 *  CableLengthProfileDialog} — модальная форма, {@link #showDialog()}
 *  возвращает заполненный объект или {@code null} при отмене; вызывающий код
 *  сам решает, {@code AppModel.addCaseType}/{@code updateCaseType} и
 *  предлагать ли результат на модерацию (см. {@link ProposeDialog}). */
public class CaseTypeDialog extends JDialog {

    private final JTextField nameField = new JTextField();
    private final JSpinner lengthSpinner = new JSpinner(new SpinnerNumberModel(600.0, 0.0, 100_000.0, 10.0));
    private final JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(400.0, 0.0, 100_000.0, 10.0));
    private final JSpinner heightSpinner = new JSpinner(new SpinnerNumberModel(400.0, 0.0, 100_000.0, 10.0));
    private final JSpinner weightSpinner = new JSpinner(new SpinnerNumberModel(20.0, 0.0, 1_000_000.0, 1.0));
    private final JSpinner maxStackSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
    private final JSpinner clearanceSpinner = new JSpinner(new SpinnerNumberModel(50.0, 0.0, 10_000.0, 5.0));
    private final JCheckBox carriesCabinetsBox = new JCheckBox("Несёт кабинеты/модули");
    private final JSpinner cabinetsPerCaseSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10_000, 1));
    private final String existingId;
    private CaseType result;

    public CaseTypeDialog(Window owner, CaseType existing) {
        super(owner, existing == null ? "Новый тип кофра" : "Редактирование типа кофра",
                ModalityType.APPLICATION_MODAL);
        existingId = existing != null ? existing.getId() : null;
        if (existing != null) {
            nameField.setText(existing.getName());
            lengthSpinner.setValue(existing.getLengthMm());
            widthSpinner.setValue(existing.getWidthMm());
            heightSpinner.setValue(existing.getHeightMm());
            weightSpinner.setValue(existing.getWeightKg());
            maxStackSpinner.setValue(existing.getMaxStackCount());
            clearanceSpinner.setValue(existing.getClearanceMm());
            carriesCabinetsBox.setSelected(existing.isCarriesCabinets());
            cabinetsPerCaseSpinner.setValue(
                    existing.getCabinetsPerCase() != null ? existing.getCabinetsPerCase() : 1);
        }
        cabinetsPerCaseSpinner.setEnabled(carriesCabinetsBox.isSelected());
        carriesCabinetsBox.addActionListener(e -> cabinetsPerCaseSpinner.setEnabled(carriesCabinetsBox.isSelected()));

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(row("Название", nameField));
        top.add(row("Длина, мм", lengthSpinner));
        top.add(row("Ширина, мм", widthSpinner));
        top.add(row("Высота, мм", heightSpinner));
        top.add(row("Вес гружёного кофра, кг", weightSpinner));
        top.add(row("Макс. штук друг на друга", maxStackSpinner));
        top.add(row("Запас по периметру (между кофрами), мм", clearanceSpinner));
        JPanel carriesRow = new JPanel(new BorderLayout(6, 0));
        carriesRow.add(carriesCabinetsBox, BorderLayout.WEST);
        top.add(carriesRow);
        top.add(row("Кабинетов в кофре", cabinetsPerCaseSpinner));
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

    private static JPanel row(String label, JSpinner field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(new JLabel(label), BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private static JPanel row(String label, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(new JLabel(label), BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private void onOk() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Укажите название кофра", "Проверка данных",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        result = new CaseType();
        if (existingId != null) {
            result.setId(existingId);
        }
        result.setName(name);
        result.setLengthMm((Double) lengthSpinner.getValue());
        result.setWidthMm((Double) widthSpinner.getValue());
        result.setHeightMm((Double) heightSpinner.getValue());
        result.setWeightKg((Double) weightSpinner.getValue());
        result.setMaxStackCount((Integer) maxStackSpinner.getValue());
        result.setClearanceMm((Double) clearanceSpinner.getValue());
        result.setCarriesCabinets(carriesCabinetsBox.isSelected());
        result.setCabinetsPerCase(carriesCabinetsBox.isSelected() ? (Integer) cabinetsPerCaseSpinner.getValue() : null);
        dispose();
    }

    /** Показывает диалог; возвращает заполненные данные (с id существующей записи,
     *  если редактируем, иначе новым сгенерированным) или null при отмене. */
    public CaseType showDialog() {
        setVisible(true);
        return result;
    }
}
