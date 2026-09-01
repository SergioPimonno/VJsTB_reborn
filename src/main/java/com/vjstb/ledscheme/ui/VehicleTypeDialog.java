package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.VehicleType;
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
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/** Создание/редактирование СВОЕГО типа машины прямо в клиенте (запрос
 *  пользователя: "в менюшке выбора количества кофров нет кнопки добавить тип
 *  машины ... для ситуации, когда нужного варианта в библиотеке нету" —
 *  калькулятор транспорта уже умел это для типов кофров ({@link
 *  CaseTypeDialog}), для машин симметричной кнопки не было). Точная копия
 *  структуры {@link CaseTypeDialog} под поля {@link VehicleType} — модальная
 *  форма, {@link #showDialog()} возвращает заполненный объект или {@code
 *  null} при отмене; вызывающий код сам решает, {@code
 *  AppModel.addVehicleType}/{@code updateVehicleType} и предлагать ли
 *  результат на модерацию (см. {@link ProposeDialog}) — та же связка, что и
 *  у типов кофров/сетевого оборудования. */
public class VehicleTypeDialog extends JDialog {

    private final JTextField nameField = new JTextField();
    private final JSpinner cargoLengthSpinner = new JSpinner(new SpinnerNumberModel(4000.0, 0.0, 100_000.0, 100.0));
    private final JSpinner cargoWidthSpinner = new JSpinner(new SpinnerNumberModel(2000.0, 0.0, 100_000.0, 100.0));
    private final JSpinner cargoHeightSpinner = new JSpinner(new SpinnerNumberModel(2000.0, 0.0, 100_000.0, 100.0));
    private final JSpinner payloadSpinner = new JSpinner(new SpinnerNumberModel(1500.0, 0.0, 100_000_000.0, 100.0));
    private final String existingId;
    private VehicleType result;

    public VehicleTypeDialog(Window owner, VehicleType existing) {
        super(owner, existing == null ? "Новый тип машины" : "Редактирование типа машины",
                ModalityType.APPLICATION_MODAL);
        existingId = existing != null ? existing.getId() : null;
        if (existing != null) {
            nameField.setText(existing.getName());
            cargoLengthSpinner.setValue(existing.getCargoLengthMm());
            cargoWidthSpinner.setValue(existing.getCargoWidthMm());
            cargoHeightSpinner.setValue(existing.getCargoHeightMm());
            payloadSpinner.setValue(existing.getPayloadKg());
        }

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(row("Название", nameField));
        top.add(row("Длина кузова, мм", cargoLengthSpinner));
        top.add(row("Ширина кузова, мм", cargoWidthSpinner));
        top.add(row("Высота кузова, мм", cargoHeightSpinner));
        top.add(row("Грузоподъёмность, кг", payloadSpinner));
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
            JOptionPane.showMessageDialog(this, "Укажите название машины", "Проверка данных",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        result = new VehicleType();
        if (existingId != null) {
            result.setId(existingId);
        }
        result.setName(name);
        result.setCargoLengthMm((Double) cargoLengthSpinner.getValue());
        result.setCargoWidthMm((Double) cargoWidthSpinner.getValue());
        result.setCargoHeightMm((Double) cargoHeightSpinner.getValue());
        result.setPayloadKg((Double) payloadSpinner.getValue());
        dispose();
    }

    /** Показывает диалог; возвращает заполненные данные (с id существующей записи,
     *  если редактируем, иначе новым сгенерированным) или null при отмене. */
    public VehicleType showDialog() {
        setVisible(true);
        return result;
    }
}
