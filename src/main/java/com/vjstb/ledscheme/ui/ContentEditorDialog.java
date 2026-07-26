package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.settings.ContentSection;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

/**
 * Общий редактор разделов текстового контента (Руководство/приветствие) —
 * список разделов слева (добавить/удалить/переставить), заголовок + текст
 * справа. Текст хранится и вводится как HTML (перевод строки → &lt;br&gt;
 * делает сам предпросмотр в GuideDialog/OnboardingDialog — здесь работает
 * "как есть", простые теги вроде &lt;b&gt;/&lt;br&gt; можно вписать вручную).
 */
public class ContentEditorDialog extends JDialog {

    private final DefaultListModel<ContentSection> listModel = new DefaultListModel<>();
    private final JList<ContentSection> list = new JList<>(listModel);
    private final JTextField titleField = new JTextField();
    private final JTextArea bodyArea = new JTextArea();
    private final List<ContentSection> defaults;

    public ContentEditorDialog(Window owner, String title, List<ContentSection> initial,
                                List<ContentSection> defaults, Consumer<List<ContentSection>> onSave) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.defaults = defaults;

        for (ContentSection s : initial) {
            listModel.addElement(s.copy());
        }

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new NamedRenderer<ContentSection>(ContentSection::getTitle, null));
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedIntoFields();
            }
        });

        JPanel listPanel = new JPanel(new BorderLayout(4, 4));
        listPanel.add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel listBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("+ Раздел");
        add.addActionListener(e -> {
            flushFieldsIntoSelected();
            ContentSection s = new ContentSection("Новый раздел", "");
            listModel.addElement(s);
            list.setSelectedValue(s, true);
        });
        JButton remove = new JButton("Удалить");
        remove.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0) {
                listModel.remove(idx);
                if (!listModel.isEmpty()) {
                    list.setSelectedIndex(Math.min(idx, listModel.size() - 1));
                } else {
                    titleField.setText("");
                    bodyArea.setText("");
                }
            }
        });
        JButton up = new JButton("▲");
        up.addActionListener(e -> moveSelected(-1));
        JButton down = new JButton("▼");
        down.addActionListener(e -> moveSelected(1));
        listBtns.add(add);
        listBtns.add(remove);
        listBtns.add(up);
        listBtns.add(down);
        listPanel.add(listBtns, BorderLayout.SOUTH);
        listPanel.setPreferredSize(new Dimension(200, 360));

        JPanel form = new JPanel(new BorderLayout(6, 6));
        form.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        JPanel titleRow = new JPanel(new BorderLayout(6, 0));
        titleRow.add(new JLabel("Заголовок:"), BorderLayout.WEST);
        titleRow.add(titleField, BorderLayout.CENTER);
        form.add(titleRow, BorderLayout.NORTH);
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        form.add(new JScrollPane(bodyArea), BorderLayout.CENTER);
        form.setPreferredSize(new Dimension(420, 360));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPanel, form);
        split.setContinuousLayout(true);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(split, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        JButton resetAll = new JButton("Восстановить по умолчанию");
        resetAll.addActionListener(e -> resetToDefaults());
        bottom.add(resetAll, BorderLayout.WEST);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> dispose());
        JButton save = new JButton("Сохранить");
        save.addActionListener(e -> {
            flushFieldsIntoSelected();
            if (listModel.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Должен остаться хотя бы один раздел",
                        "Проверка данных", JOptionPane.WARNING_MESSAGE);
                return;
            }
            List<ContentSection> result = new ArrayList<>();
            for (int i = 0; i < listModel.size(); i++) {
                result.add(listModel.get(i));
            }
            onSave.accept(result);
            dispose();
        });
        rightBtns.add(cancel);
        rightBtns.add(save);
        bottom.add(rightBtns, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(680, 460);
        setLocationRelativeTo(owner);

        if (!listModel.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private void resetToDefaults() {
        if (JOptionPane.showConfirmDialog(this,
                "Заменить все разделы встроенным текстом по умолчанию? Текущие правки в этом окне будут потеряны.",
                "Восстановить по умолчанию", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        listModel.clear();
        for (ContentSection s : defaults) {
            listModel.addElement(s.copy());
        }
        if (!listModel.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private ContentSection lastSelected;

    private void loadSelectedIntoFields() {
        flushFieldsIntoSelected();
        ContentSection sel = list.getSelectedValue();
        lastSelected = sel;
        titleField.setText(sel != null ? sel.getTitle() : "");
        bodyArea.setText(sel != null ? sel.getBodyHtml() : "");
    }

    private void flushFieldsIntoSelected() {
        if (lastSelected != null) {
            lastSelected.setTitle(titleField.getText());
            lastSelected.setBodyHtml(bodyArea.getText());
            list.repaint();
        }
    }

    private void moveSelected(int delta) {
        flushFieldsIntoSelected();
        int idx = list.getSelectedIndex();
        int target = idx + delta;
        if (idx < 0 || target < 0 || target >= listModel.size()) {
            return;
        }
        ContentSection s = listModel.remove(idx);
        listModel.add(target, s);
        list.setSelectedIndex(target);
    }
}
