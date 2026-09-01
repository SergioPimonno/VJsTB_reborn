package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.NetworkDeviceCategory;
import com.vjstb.ledscheme.model.NetworkDeviceType;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/** Создание/редактирование СВОЕГО типа сетевого оборудования прямо в клиенте
 *  (тот же приём, что {@link CaseTypeDialog} — Сетевой менеджер не должен
 *  упираться в то, что библиотека не предусмотрела конкретный свитч/роутер).
 *  Модальная форма, {@link #showDialog()} возвращает заполненный объект или
 *  {@code null} при отмене — вызывающий код сам решает, {@code
 *  AppModel.addNetworkDeviceType}/{@code updateNetworkDeviceType} и
 *  предлагать ли результат на модерацию (см. {@link ProposeDialog}). */
public class NetworkDeviceTypeDialog extends JDialog {

    private final JTextField nameField = new JTextField();
    private final JComboBox<NetworkDeviceCategory> categoryCombo =
            new JComboBox<>(NetworkDeviceCategory.values());
    private final JTextField descriptionField = new JTextField();
    private final JTextField companyField = new JTextField();
    private final JSpinner portCountSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 128, 1));
    private final String existingId;
    private NetworkDeviceType result;

    public NetworkDeviceTypeDialog(Window owner, NetworkDeviceType existing) {
        super(owner, existing == null ? "Новый тип сетевого оборудования" : "Редактирование типа оборудования",
                ModalityType.APPLICATION_MODAL);
        existingId = existing != null ? existing.getId() : null;
        if (existing != null) {
            nameField.setText(existing.getName());
            categoryCombo.setSelectedItem(existing.getCategory());
            descriptionField.setText(existing.getDescription());
            companyField.setText(existing.getCompany());
            portCountSpinner.setValue(Math.max(1, existing.getPortCount()));
        }

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(row("Название", nameField));
        top.add(row("Категория", categoryCombo));
        top.add(row("Число портов Ethernet", portCountSpinner));
        top.add(row("Описание", descriptionField));
        top.add(row("Производитель", companyField));
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

    private static JPanel row(String label, JComboBox<?> field) {
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

    private static JPanel row(String label, JSpinner field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(new JLabel(label), BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private void onOk() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Укажите название типа оборудования", "Проверка данных",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        result = new NetworkDeviceType();
        if (existingId != null) {
            result.setId(existingId);
        }
        result.setName(name);
        result.setCategory((NetworkDeviceCategory) categoryCombo.getSelectedItem());
        result.setDescription(descriptionField.getText().trim());
        result.setCompany(companyField.getText().trim());
        result.setPortCount((Integer) portCountSpinner.getValue());
        dispose();
    }

    /** Показывает диалог; возвращает заполненные данные (с id существующей записи,
     *  если редактируем, иначе новым сгенерированным) или null при отмене. */
    public NetworkDeviceType showDialog() {
        setVisible(true);
        return result;
    }
}
