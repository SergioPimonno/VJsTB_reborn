package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.SchemaNodeType;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Модальный диалог добавления/редактирования пресета оборудования в библиотеке:
 *  категория (тип узла общей схемы), название, описание. Комплектация карт
 *  редактируется отдельно ({@link CardsConfigDialog}) — уже для сохранённого пресета. */
public class EquipmentPresetDialog extends JDialog {

    private final JComboBox<SchemaNodeType> categoryField = new JComboBox<>(SchemaNodeType.values());
    private final JTextField nameField = new JTextField();
    private final JTextField descriptionField = new JTextField();

    public record Result(SchemaNodeType category, String name, String description) {
    }

    private Result result;

    public EquipmentPresetDialog(Window owner, EquipmentPreset existing) {
        super(owner, existing == null ? "Новый пресет оборудования" : "Редактирование пресета",
                ModalityType.APPLICATION_MODAL);

        categoryField.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof SchemaNodeType t) {
                    setText(t.getLabel());
                }
                return this;
            }
        });

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        form.add(new JLabel("Категория"));
        form.add(categoryField);
        form.add(new JLabel("Название"));
        form.add(nameField);
        form.add(new JLabel("Описание"));
        form.add(descriptionField);

        if (existing != null) {
            categoryField.setSelectedItem(existing.getCategory());
            nameField.setText(existing.getName());
            descriptionField.setText(existing.getDescription());
        }

        JButton ok = new JButton("Сохранить");
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
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Укажите название пресета", "Проверка данных",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        result = new Result((SchemaNodeType) categoryField.getSelectedItem(), name, descriptionField.getText().trim());
        dispose();
    }

    /** Показывает диалог; возвращает заполненные данные или null при отмене. */
    public Result showDialog() {
        setVisible(true);
        return result;
    }
}
