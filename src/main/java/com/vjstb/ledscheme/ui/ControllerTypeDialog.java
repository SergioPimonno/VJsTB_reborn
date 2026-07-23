package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.ControllerType;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Модальный диалог добавления/редактирования типа контроллера в библиотеке. */
public class ControllerTypeDialog extends JDialog {

    private final JTextField nameField = new JTextField();
    private final JTextField vendorField = new JTextField();
    private final JTextField portCountField = new JTextField();
    private final JTextField maxPixelsField = new JTextField();

    private ControllerType result;

    public ControllerTypeDialog(Window owner, ControllerType existing) {
        super(owner, existing == null ? "Новый контроллер" : "Редактирование контроллера", ModalityType.APPLICATION_MODAL);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        form.add(new JLabel("Название/модель"));
        form.add(nameField);
        form.add(new JLabel("Производитель"));
        form.add(vendorField);
        form.add(new JLabel("Число портов"));
        form.add(portCountField);
        form.add(new JLabel("Макс. пикселей на порт"));
        form.add(maxPixelsField);

        ControllerType src = existing != null ? existing : new ControllerType();
        nameField.setText(src.getName());
        vendorField.setText(src.getVendor());
        portCountField.setText(String.valueOf(src.getPortCount()));
        maxPixelsField.setText(String.valueOf(src.getMaxPixelsPerPort()));

        JButton ok = new JButton("Сохранить");
        ok.addActionListener(e -> onOk(existing));
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> { result = null; dispose(); });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(form);
        content.add(buttons);

        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(owner);
    }

    private void onOk(ControllerType existing) {
        try {
            ControllerType ct = existing != null ? existing.copy() : new ControllerType();
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Укажите название контроллера");
            }
            ct.setName(name);
            ct.setVendor(vendorField.getText().trim());
            ct.setPortCount((int) parsePositive(portCountField.getText(), "Число портов"));
            ct.setMaxPixelsPerPort((int) parsePositive(maxPixelsField.getText(), "Макс. пикселей на порт"));
            result = ct;
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Проверка данных", JOptionPane.WARNING_MESSAGE);
        }
    }

    private double parsePositive(String s, String field) {
        try {
            double v = Double.parseDouble(s.trim().replace(',', '.'));
            if (v <= 0) {
                throw new IllegalArgumentException(field + ": значение должно быть больше 0");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + ": введите число");
        }
    }

    /** Показывает диалог; возвращает заполненный тип или null при отмене. */
    public ControllerType showDialog() {
        setVisible(true);
        return result;
    }
}
