package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CableLengthProfile;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;

/** Добавление/редактирование каталога доступных длин катушек кабеля определённого
 *  типа (тот же приём для редактируемого списка значений, что и у других
 *  диалогов библиотеки — здесь просто числа вместо строк). */
public class CableLengthProfileDialog extends JDialog {

    private final JTextField nameField = new JTextField();
    private final DefaultListModel<Double> lengthsModel = new DefaultListModel<>();
    private final JList<Double> lengthsList = new JList<>(lengthsModel);
    private final JTextField newLengthField = new JTextField();
    private final JSpinner marginSpinner = new JSpinner(new SpinnerNumberModel(10.0, 0.0, 100.0, 1.0));
    private final String existingId;
    private CableLengthProfile result;

    public CableLengthProfileDialog(Window owner, CableLengthProfile existing) {
        super(owner, existing == null ? "Новый каталог длин кабеля" : "Редактирование каталога длин",
                ModalityType.APPLICATION_MODAL);
        existingId = existing != null ? existing.getId() : null;
        if (existing != null) {
            nameField.setText(existing.getName());
            for (Double len : existing.getAvailableLengthsM()) {
                lengthsModel.addElement(len);
            }
            marginSpinner.setValue(existing.getMarginPercent());
        }
        lengthsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JPanel nameRow = new JPanel(new BorderLayout(6, 0));
        nameRow.add(new JLabel("Метка типа провода (как в подписи связи схемы)"), BorderLayout.NORTH);
        nameRow.add(nameField, BorderLayout.CENTER);
        top.add(nameRow);
        top.add(Box.createVerticalStrut(8));
        JPanel marginRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        marginRow.add(new JLabel("Запас при округлении, %:"));
        marginRow.add(marginSpinner);
        MathFields.enableExpressions(marginSpinner);
        top.add(marginRow);
        top.add(Box.createVerticalStrut(8));
        top.add(new JLabel("Доступные длины катушек, м:"));
        content.add(top, BorderLayout.NORTH);

        JScrollPane lengthsScroll = new JScrollPane(lengthsList);
        lengthsScroll.setPreferredSize(new Dimension(320, 160));
        content.add(lengthsScroll, BorderLayout.CENTER);

        JPanel addRow = new JPanel(new BorderLayout(6, 0));
        newLengthField.putClientProperty("JTextField.placeholderText", "например, 20…");
        addRow.add(newLengthField, BorderLayout.CENTER);
        JButton addLength = new JButton("+ Добавить длину");
        addLength.addActionListener(e -> addLength());
        newLengthField.addActionListener(e -> addLength());
        addRow.add(addLength, BorderLayout.EAST);

        Runnable removeSelectedLength = () -> {
            int idx = lengthsList.getSelectedIndex();
            if (idx >= 0) {
                lengthsModel.remove(idx);
            }
        };
        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(addRow);
        JPanel removeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton removeLength = new JButton("Удалить длину");
        removeLength.addActionListener(e -> removeSelectedLength.run());
        UiKit.bindDeleteKey(lengthsList, removeSelectedLength);
        removeRow.add(removeLength);
        south.add(removeRow);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(south, BorderLayout.CENTER);
        JButton ok = new JButton("Сохранить");
        ok.addActionListener(e -> onOk());
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> { result = null; dispose(); });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(cancel);
        buttons.add(ok);
        bottom.add(buttons, BorderLayout.SOUTH);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    private void addLength() {
        String text = newLengthField.getText().trim().replace(',', '.');
        if (text.isEmpty()) {
            return;
        }
        try {
            double value = Double.parseDouble(text);
            if (value <= 0) {
                throw new NumberFormatException();
            }
            lengthsModel.addElement(value);
            newLengthField.setText("");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Длина должна быть положительным числом", "Проверка данных",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void onOk() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Укажите метку типа провода", "Проверка данных",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (lengthsModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Добавьте хотя бы одну доступную длину", "Проверка данных",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<Double> lengths = new ArrayList<>();
        for (int i = 0; i < lengthsModel.size(); i++) {
            lengths.add(lengthsModel.get(i));
        }
        result = new CableLengthProfile();
        if (existingId != null) {
            result.setId(existingId);
        }
        result.setName(name);
        result.setAvailableLengthsM(lengths);
        result.setMarginPercent((Double) marginSpinner.getValue());
        dispose();
    }

    /** Показывает диалог; возвращает заполненные данные (с id существующей записи,
     *  если редактируем, иначе новым сгенерированным) или null при отмене —
     *  вызывающий сам решает, вызвать AppModel.addCableLengthProfile или
     *  updateCableLengthProfile. */
    public CableLengthProfile showDialog() {
        setVisible(true);
        return result;
    }
}
