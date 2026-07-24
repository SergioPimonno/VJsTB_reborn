package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetType;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.List;
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

/** Модальный диалог параметров нового экрана: имя, тип кабинета, размер сетки,
 *  базовый офсет (X/Y мм) — предзаполненный подсказанной автопозицией. */
public class NewScreenDialog extends JDialog {

    /** Заполненные и провалидированные параметры нового экрана. */
    public record Result(String name, String cabinetTypeId, int rows, int cols, double posX, double posY) {
    }

    private final JTextField nameField = new JTextField();
    private final JComboBox<CabinetType> typeField = new JComboBox<>();
    private final JSpinner colsField = new JSpinner(new SpinnerNumberModel(3, 1, 200, 1));
    private final JSpinner rowsField = new JSpinner(new SpinnerNumberModel(5, 1, 200, 1));
    private final JTextField xField = new JTextField();
    private final JTextField yField = new JTextField();

    private Result result;

    public NewScreenDialog(Window owner, List<CabinetType> cabinetTypes, String suggestedName,
                            double suggestedX, double suggestedY) {
        super(owner, "Новый экран", ModalityType.APPLICATION_MODAL);

        for (CabinetType t : cabinetTypes) {
            typeField.addItem(t);
        }
        typeField.setRenderer(new CabinetTypeRenderer());
        if (typeField.getItemCount() > 0) {
            typeField.setSelectedIndex(0);
        }
        nameField.setText(suggestedName);
        xField.setText(UiKit.fmt(suggestedX));
        yField.setText(UiKit.fmt(suggestedY));

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        form.add(new JLabel("Название"));
        form.add(nameField);
        form.add(new JLabel("Кабинет"));
        form.add(typeField);
        form.add(new JLabel("Колонны"));
        form.add(colsField);
        form.add(new JLabel("Строки"));
        form.add(rowsField);
        form.add(new JLabel("X (мм)"));
        form.add(xField);
        form.add(new JLabel("Y (мм)"));
        form.add(yField);

        JButton ok = new JButton("Создать");
        ok.addActionListener(e -> onOk());
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

    private void onOk() {
        try {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Укажите название экрана");
            }
            CabinetType type = (CabinetType) typeField.getSelectedItem();
            if (type == null) {
                throw new IllegalArgumentException("Выберите тип кабинета");
            }
            double x = parseDouble(xField.getText(), "X");
            double y = parseDouble(yField.getText(), "Y");
            result = new Result(name, type.getId(), (int) rowsField.getValue(), (int) colsField.getValue(), x, y);
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Проверка данных", JOptionPane.WARNING_MESSAGE);
        }
    }

    private double parseDouble(String s, String field) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + ": введите число");
        }
    }

    /** Показывает диалог; возвращает заполненные параметры или null при отмене. */
    public Result showDialog() {
        setVisible(true);
        return result;
    }
}
