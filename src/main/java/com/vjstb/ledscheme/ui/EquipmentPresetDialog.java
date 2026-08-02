package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.AppModel;
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
 *  категория (тип узла общей схемы), название, описание, а для категории
 *  «Прочее оборудование» — ещё и подкатегория из справочника админ-режима
 *  (Task #12: раньше AdminDialog умел редактировать список подкатегорий, но
 *  сохранить подкатегорию на пресете было негде — этот диалог был единственным
 *  местом, где категория пресета вообще задаётся). Комплектация карт
 *  редактируется отдельно ({@link CardsConfigDialog}) — уже для сохранённого пресета. */
public class EquipmentPresetDialog extends JDialog {

    /** Сентинел «без подкатегории» в комбобоксе — не может совпасть с реальным
     *  названием подкатегории (пустая строка запрещена при добавлении, см.
     *  AppModel.addCustomEquipmentCategory). */
    private static final String NO_SUBCATEGORY = "— без подкатегории —";

    private final JComboBox<SchemaNodeType> categoryField = new JComboBox<>(SchemaNodeType.values());
    private final JTextField nameField = new JTextField();
    private final JTextField descriptionField = new JTextField();
    private final JComboBox<String> subcategoryField = new JComboBox<>();
    private final JLabel subcategoryLabel = new JLabel("Подкатегория");

    public record Result(SchemaNodeType category, String name, String description, String customCategoryLabel) {
    }

    private Result result;

    public EquipmentPresetDialog(Window owner, AppModel model, EquipmentPreset existing) {
        this(owner, model, existing, null);
    }

    /** initialCategory — категория, выбранная по умолчанию для НОВОГО пресета
     *  (existing == null), например текущая подсвеченная категория в дереве
     *  библиотеки — без этого «Добавить» в категории Y всегда предлагал бы
     *  первую по счёту категорию (SOURCE) вместо ожидаемой Y. Игнорируется при
     *  редактировании существующего пресета (тогда категория берётся из него). */
    public EquipmentPresetDialog(Window owner, AppModel model, EquipmentPreset existing, SchemaNodeType initialCategory) {
        super(owner, existing == null ? "Новый пресет оборудования" : "Редактирование пресета",
                ModalityType.APPLICATION_MODAL);

        categoryField.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof SchemaNodeType t) {
                    setText(model.categoryLabel(t));
                }
                return this;
            }
        });

        subcategoryField.addItem(NO_SUBCATEGORY);
        for (String c : model.getCustomEquipmentCategories()) {
            subcategoryField.addItem(c);
        }
        Runnable syncSubcategoryVisibility = () -> {
            boolean isCustom = categoryField.getSelectedItem() == SchemaNodeType.CUSTOM;
            subcategoryLabel.setVisible(isCustom);
            subcategoryField.setVisible(isCustom);
        };
        categoryField.addActionListener(e -> syncSubcategoryVisibility.run());

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        form.add(new JLabel("Категория"));
        form.add(categoryField);
        form.add(new JLabel("Название"));
        form.add(nameField);
        form.add(new JLabel("Описание"));
        form.add(descriptionField);
        form.add(subcategoryLabel);
        form.add(subcategoryField);

        if (existing != null) {
            categoryField.setSelectedItem(existing.getCategory());
            nameField.setText(existing.getName());
            descriptionField.setText(existing.getDescription());
            String cur = existing.getCustomCategoryLabel();
            if (cur != null && !cur.isEmpty()) {
                subcategoryField.setSelectedItem(cur);
            }
        } else if (initialCategory != null) {
            categoryField.setSelectedItem(initialCategory);
        }
        syncSubcategoryVisibility.run();

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
        SchemaNodeType category = (SchemaNodeType) categoryField.getSelectedItem();
        String subcategory = category == SchemaNodeType.CUSTOM ? (String) subcategoryField.getSelectedItem() : null;
        if (NO_SUBCATEGORY.equals(subcategory)) {
            subcategory = null;
        }
        result = new Result(category, name, descriptionField.getText().trim(), subcategory);
        dispose();
    }

    /** Показывает диалог; возвращает заполненные данные или null при отмене. */
    public Result showDialog() {
        setVisible(true);
        return result;
    }
}
