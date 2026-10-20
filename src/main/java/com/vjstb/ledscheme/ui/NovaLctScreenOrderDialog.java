package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.service.NovaLctScreenOrder;
import com.vjstb.ledscheme.service.NovaLctScreenOrder.Entry;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/** Шаг экспорта в NovaLCT «Порядок экранов»: какой экран проекта станет Screen1,
 *  какой Screen2 и т.д. (запрос пользователя — на одном контроллере в NovaLCT несколько
 *  Screen, и назначать на них экраны проекта нужно самому). Показывается, когда в
 *  итоговом файле 2+ NovaLCT-экрана (раздельные экраны, смесь Standard + Complex);
 *  Complex-экраны не размещаются на сетке {@link LctPresetMasterDialog}, поэтому
 *  только здесь они и видны.
 *
 * <p>Порядок по умолчанию — автоматический, по (Sending Card, Port). Менять его
 * можно ↑/↓, но NovaLCT отклоняет файл, если на одной Sending Card экран с большим
 * портом идёт раньше экрана с меньшим — такой порядок блокирует «Далее» (см.
 * {@link NovaLctScreenOrder#violation}). */
public final class NovaLctScreenOrderDialog extends JDialog {

    private final DefaultListModel<Entry> model = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(model);
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton upBtn = new JButton("↑ Выше");
    private final JButton downBtn = new JButton("↓ Ниже");
    private final JButton autoBtn = new JButton("Авто (по карте и порту)");
    private final JButton okBtn = new JButton("Далее");

    private List<Entry> result;

    private NovaLctScreenOrderDialog(Window owner, List<Entry> entries) {
        super(owner, "Порядок экранов NovaLCT (Screen1…Screen" + entries.size() + ")", ModalityType.APPLICATION_MODAL);
        for (Entry e : NovaLctScreenOrder.autoOrder(entries)) {
            model.addElement(e);
        }

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(Math.max(4, Math.min(12, entries.size())));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                            boolean isSelected, boolean hasFocus) {
                super.getListCellRendererComponent(l, value, index, isSelected, hasFocus);
                if (value instanceof Entry e) {
                    setText("Screen" + (index + 1) + " — " + e.label() + (e.isComplex() ? "  [Complex]" : "")
                            + "   (Sending Card " + (e.card() + 1) + ", порт " + (e.port() + 1) + ")");
                }
                return this;
            }
        });
        list.addListSelectionListener(e -> refresh());
        list.setSelectedIndex(0);

        upBtn.addActionListener(e -> move(-1));
        downBtn.addActionListener(e -> move(1));
        autoBtn.addActionListener(e -> {
            List<Entry> auto = NovaLctScreenOrder.autoOrder(currentOrder());
            model.clear();
            for (Entry en : auto) {
                model.addElement(en);
            }
            list.setSelectedIndex(0);
            refresh();
        });
        okBtn.addActionListener(e -> {
            result = currentOrder();
            dispose();
        });
        JButton cancelBtn = new JButton("Отмена");
        cancelBtn.addActionListener(e -> { result = null; dispose(); });

        JPanel hint = new JPanel(new BorderLayout());
        hint.add(UiKit.muted("Экраны проекта на этом контроллере → номера Screen в NovaLCT. По умолчанию порядок"
                + " по возрастанию Sending Card и порта. Если на одной карте экран с большим портом поставить"
                + " раньше — NovaLCT отклонит файл (меняйте расключение)."), BorderLayout.CENTER);

        JPanel side = new JPanel();
        side.setLayout(new java.awt.GridLayout(0, 1, 0, 4));
        side.add(upBtn);
        side.add(downBtn);
        side.add(autoBtn);

        JPanel sideWrap = new JPanel(new BorderLayout());
        sideWrap.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        sideWrap.add(side, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(560, 220));

        JPanel center = new JPanel(new BorderLayout());
        center.add(scroll, BorderLayout.CENTER);
        center.add(sideWrap, BorderLayout.EAST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancelBtn);
        buttons.add(okBtn);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(statusLabel, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(6, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(hint, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        getRootPane().setDefaultButton(okBtn);
        refresh();
        pack();
        setLocationRelativeTo(owner);
    }

    private List<Entry> currentOrder() {
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            out.add(model.get(i));
        }
        return out;
    }

    private void move(int delta) {
        int i = list.getSelectedIndex();
        int j = i + delta;
        if (i < 0 || j < 0 || j >= model.size()) {
            return;
        }
        Entry e = model.remove(i);
        model.add(j, e);
        list.setSelectedIndex(j);
        refresh();
    }

    private void refresh() {
        int i = list.getSelectedIndex();
        upBtn.setEnabled(i > 0);
        downBtn.setEnabled(i >= 0 && i < model.size() - 1);
        List<Entry> order = currentOrder();
        String violation = NovaLctScreenOrder.violation(order);
        String warning = violation == null ? NovaLctScreenOrder.warning(order) : null;
        okBtn.setEnabled(violation == null);
        if (violation != null) {
            statusLabel.setForeground(Color.RED);
            statusLabel.setText("<html><body style='width:560px'>" + escape(violation) + "</body></html>");
        } else if (warning != null) {
            statusLabel.setForeground(new Color(0xff, 0xa0, 0x00));
            statusLabel.setText("<html><body style='width:560px'>" + escape(warning) + "</body></html>");
        } else {
            statusLabel.setForeground(Palette.MUTED);
            statusLabel.setText(" ");
        }
        list.repaint();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Показывает диалог; возвращает порядок Screen1..N либо {@code null} при отмене. */
    public static List<Entry> showDialog(Window owner, List<Entry> entries) {
        NovaLctScreenOrderDialog dlg = new NovaLctScreenOrderDialog(owner, entries);
        dlg.setVisible(true);
        return dlg.result;
    }
}
