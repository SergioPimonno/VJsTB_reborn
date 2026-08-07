package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Scenario;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/** Список интерактивных сценариев (см. {@link Scenario}/{@link ScenarioPlayerDialog}) —
 *  точка входа, открываемая и из меню «Настройки», и кнопкой в {@link GuideDialog}. */
public class ScenarioListDialog extends JDialog {

    public static void show(Window owner, List<Scenario> scenarios) {
        new ScenarioListDialog(owner, scenarios).setVisible(true);
    }

    public ScenarioListDialog(Window owner, List<Scenario> scenarios) {
        super(owner, "Интерактивные примеры", ModalityType.MODELESS);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        if (scenarios.isEmpty()) {
            content.add(UiKit.muted("Пока нет интерактивных примеров."), BorderLayout.CENTER);
        } else {
            DefaultListModel<Scenario> listModel = new DefaultListModel<>();
            scenarios.forEach(listModel::addElement);
            JList<Scenario> list = new JList<>(listModel);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setSelectedIndex(0);
            list.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                        boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
                    if (value instanceof Scenario s) {
                        setText(s.getTitle() == null || s.getTitle().isBlank() ? "(без названия)" : s.getTitle());
                    }
                    return this;
                }
            });
            JScrollPane scroll = new JScrollPane(list);
            scroll.setPreferredSize(new Dimension(360, 240));
            content.add(scroll, BorderLayout.CENTER);

            Runnable openSelected = () -> {
                Scenario sel = list.getSelectedValue();
                if (sel != null) {
                    ScenarioPlayerDialog.show(owner, sel);
                }
            };
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        openSelected.run();
                    }
                }
            });

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
            JButton openBtn = new JButton("Открыть");
            openBtn.addActionListener(e -> openSelected.run());
            bottom.add(openBtn);
            content.add(bottom, BorderLayout.SOUTH);
        }

        add(content, BorderLayout.CENTER);
        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        closeRow.add(close);
        add(closeRow, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }
}
