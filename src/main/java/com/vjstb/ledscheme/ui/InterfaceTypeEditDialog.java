package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.InterfaceType;
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
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

/** Добавление/редактирование вида интерфейса и списка его версий (см. AdminDialog,
 *  InterfaceTypeVersionPicker) — например, "HDMI" со списком версий "1.4/2.0/2.1". */
public class InterfaceTypeEditDialog extends JDialog {

    private final JTextField nameField = new JTextField();
    private final DefaultListModel<String> versionsModel = new DefaultListModel<>();
    private final JList<String> versionsList = new JList<>(versionsModel);
    private final JTextField newVersionField = new JTextField();
    private InterfaceType result;

    public InterfaceTypeEditDialog(Window owner, InterfaceType existing) {
        super(owner, existing == null ? "Новый вид интерфейса" : "Редактирование вида интерфейса",
                ModalityType.APPLICATION_MODAL);
        if (existing != null) {
            nameField.setText(existing.getName());
            for (String v : existing.getVersions()) {
                versionsModel.addElement(v);
            }
        }
        versionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JPanel nameRow = new JPanel(new BorderLayout(6, 0));
        nameRow.add(new JLabel("Название вида (например, HDMI)"), BorderLayout.NORTH);
        nameRow.add(nameField, BorderLayout.CENTER);
        top.add(nameRow);
        top.add(Box.createVerticalStrut(8));
        top.add(new JLabel("Версии (пусто — вид используется без версии, например XLR/BNC)"));
        content.add(top, BorderLayout.NORTH);

        JScrollPane versionsScroll = new JScrollPane(versionsList);
        versionsScroll.setPreferredSize(new Dimension(320, 160));
        content.add(versionsScroll, BorderLayout.CENTER);

        JPanel addRow = new JPanel(new BorderLayout(6, 0));
        newVersionField.putClientProperty("JTextField.placeholderText", "например, 2.1…");
        addRow.add(newVersionField, BorderLayout.CENTER);
        JButton addVersion = new JButton("+ Добавить версию");
        addVersion.addActionListener(e -> addVersion());
        newVersionField.addActionListener(e -> addVersion());
        addRow.add(addVersion, BorderLayout.EAST);

        Runnable removeSelectedVersion = () -> {
            int idx = versionsList.getSelectedIndex();
            if (idx >= 0) {
                versionsModel.remove(idx);
            }
        };
        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(addRow);
        JPanel removeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton removeVersion = new JButton("Удалить версию");
        removeVersion.addActionListener(e -> removeSelectedVersion.run());
        UiKit.bindDeleteKey(versionsList, removeSelectedVersion);
        removeRow.add(removeVersion);
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

    private void addVersion() {
        String v = newVersionField.getText().trim();
        if (!v.isEmpty()) {
            versionsModel.addElement(v);
            newVersionField.setText("");
        }
    }

    private void onOk() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Укажите название вида интерфейса", "Проверка данных",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<String> versions = new ArrayList<>();
        for (int i = 0; i < versionsModel.size(); i++) {
            versions.add(versionsModel.get(i));
        }
        result = new InterfaceType(name, versions);
        dispose();
    }

    /** Показывает диалог; возвращает заполненные данные (name+versions, БЕЗ id —
     *  вызывающий сам решает, создать новую запись или применить к существующей
     *  через AppModel.updateInterfaceType) или null при отмене. */
    public InterfaceType showDialog() {
        setVisible(true);
        return result;
    }
}
