package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.service.room.RoomEditor;
import com.vjstb.ledscheme.service.room.RoomPlanCalc;
import com.vjstb.ledscheme.service.room.RoomSelection;
import com.vjstb.ledscheme.service.room.RoomWarning;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * Окошко «Замечания» снизу (запрос 2026-10-23: «замечания вынеси отдельным окошком снизу»): счётчики по
 * уровням в шапке, по клику раскрывается список, клик по замечанию выбирает объект, к которому оно относится.
 * Тексты замечаний приходят готовыми из {@link RoomPlanCalc}.
 */
final class WarningsBar extends JPanel implements PlannerSession.Listener {

    private final PlannerSession session;
    private final JButton head = new JButton();
    private final JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JLabel first = new JLabel(" ");
    private final DefaultListModel<RoomWarning> model = new DefaultListModel<>();
    private final JList<RoomWarning> list = new JList<>(model);
    private final JScrollPane scroll = new JScrollPane(list);
    private boolean open;

    WarningsBar(PlannerSession session) {
        super(new BorderLayout());
        this.session = session;
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, RoomTheme.current().line));
        head.setText("Замечания");
        head.setFont(head.getFont().deriveFont(Font.BOLD));
        head.setFocusable(false);
        head.setBorderPainted(false);
        head.setContentAreaFilled(false);
        head.addActionListener(e -> toggle());
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(head);
        left.add(chips);
        top.add(left, BorderLayout.WEST);
        top.add(first, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        list.setCellRenderer(new Renderer());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                select(list.getSelectedValue());
            }
        });
        scroll.setPreferredSize(new Dimension(100, 120));
        session.addListener(this);
        render();
    }

    private void toggle() {
        open = !open;
        render();
        revalidate();
    }

    @Override
    public void sessionChanged() {
        render();
    }

    private void render() {
        RoomPlanCalc.Result calc = session.calc();
        List<RoomWarning> ws = calc == null ? List.of() : calc.warnings();
        int err = 0;
        int warn = 0;
        int info = 0;
        for (RoomWarning w : ws) {
            switch (w.level()) {
                case ERROR -> err++;
                case WARNING -> warn++;
                default -> info++;
            }
        }
        RoomTheme th = RoomTheme.current();
        chips.removeAll();
        if (ws.isEmpty()) {
            chips.add(chip("Всё в порядке", th.ok));
        } else {
            if (err > 0) {
                chips.add(chip("Ошибки " + err, th.err));
            }
            if (warn > 0) {
                chips.add(chip("Предупреждения " + warn, th.warn));
            }
            if (info > 0) {
                chips.add(chip("К сведению " + info, th.muted));
            }
        }
        RoomWarning firstW = null;
        for (RoomWarning w : ws) {
            if (w.level() != RoomWarning.Level.INFO) {
                firstW = w;
                break;
            }
        }
        if (firstW == null && !ws.isEmpty()) {
            firstW = ws.get(0);
        }
        first.setText(open || firstW == null ? " " : firstW.text());
        head.setText((open ? "▾ " : "▴ ") + "Замечания");
        model.clear();
        ws.forEach(model::addElement);
        chips.revalidate();
        chips.repaint();
        remove(scroll);
        if (open && !ws.isEmpty()) {
            add(scroll, BorderLayout.CENTER);
        }
        revalidate();
        repaint();
    }

    private JLabel chip(String text, Color c) {
        JLabel l = new JLabel(text);
        l.setForeground(c);
        l.setFont(l.getFont().deriveFont(Font.BOLD, RoomTheme.fontSize(11f)));
        java.awt.Dimension d = l.getPreferredSize();
        l.setPreferredSize(new Dimension(d.width + 8, d.height));
        return l;
    }

    private void select(RoomWarning w) {
        RoomWarning.Target t = w.target();
        if (t == null || session.layout() == null) {
            return;
        }
        switch (t.type()) {
            case "tower" -> session.select(RoomSelection.tower(t.id()));
            case "furn" -> session.select(RoomSelection.furniture(t.id()));
            case "loose" -> session.select(RoomSelection.loose(t.id()));
            case "run" -> session.select(RoomSelection.run(t.id()));
            case "ritem", "sitem" -> {
                RoomEditor.Located loc = RoomEditor.locateItem(session.layout(), t.id());
                if (loc != null) {
                    session.select("ritem".equals(t.type())
                            ? RoomSelection.rackItem(t.id(), loc.tower().getId(), loc.segment().getId())
                            : RoomSelection.stackItem(t.id(), loc.tower().getId(), loc.segment().getId()));
                } else if (t.towerId() != null) {
                    session.select(RoomSelection.tower(t.towerId()));
                }
            }
            default -> {
            }
        }
        list.clearSelection();
    }

    private final class Renderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
            RoomWarning w = (RoomWarning) value;
            JLabel c = (JLabel) super.getListCellRendererComponent(l, w.text(), index, sel, focus);
            RoomTheme th = RoomTheme.current();
            c.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            if (!sel) {
                c.setForeground(switch (w.level()) {
                    case ERROR -> th.err;
                    case WARNING -> th.warn;
                    default -> th.muted;
                });
            }
            return c;
        }
    }
}
