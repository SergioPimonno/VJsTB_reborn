package com.vjstb.ledscheme.ui.receiver;

import com.vjstb.ledscheme.ui.Palette;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * Полоса «Замечания» снизу видов «Приёмные карты» и «Блоки питания» — по образцу {@code WarningsBar} Планнера
 * пультовой: счётчики по уровням в шапке, первое важное замечание в строке, по клику список; клик по замечанию
 * выбирает карту/блок, к которой оно относится ({@code targetId}). Тексты готовит вид из результатов проверок
 * (сами проверки — {@code ReceiverCardChecks} / {@code PowerSupplyChecks} трека B), полоса только показывает.
 */
final class ReceiverWarningsBar extends JPanel {

    enum Level { ERROR, WARNING, INFO }

    /** Одно замечание; {@code targetId} — id карты/блока (или {@code null}, если замечание про экран в целом). */
    record Warning(Level level, String text, String targetId) {
    }

    private static final Color OK = new Color(0x3fb950);
    private static final Color ERR = new Color(0xe24b4a);

    private final Consumer<String> onTarget;
    private final JButton head = new JButton("Замечания");
    private final JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JLabel first = new JLabel(" ");
    private final DefaultListModel<Warning> model = new DefaultListModel<>();
    private final JList<Warning> list = new JList<>(model);
    private final JScrollPane scroll = new JScrollPane(list);
    private List<Warning> warnings = List.of();
    private boolean open;

    ReceiverWarningsBar(Consumer<String> onTarget) {
        super(new BorderLayout());
        this.onTarget = onTarget;
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.BORDER));
        head.setFont(head.getFont().deriveFont(Font.BOLD));
        head.setFocusable(false);
        head.setBorderPainted(false);
        head.setContentAreaFilled(false);
        head.addActionListener(e -> {
            open = !open;
            render();
        });
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
            Warning w = list.getSelectedValue();
            if (!e.getValueIsAdjusting() && w != null) {
                if (w.targetId() != null) {
                    onTarget.accept(w.targetId());
                }
                list.clearSelection();
            }
        });
        scroll.setPreferredSize(new Dimension(100, 110));
        render();
    }

    void setWarnings(List<Warning> next) {
        this.warnings = new ArrayList<>(next);
        render();
    }

    List<Warning> warnings() {
        return warnings;
    }

    private void render() {
        int err = 0;
        int warn = 0;
        int info = 0;
        for (Warning w : warnings) {
            switch (w.level()) {
                case ERROR -> err++;
                case WARNING -> warn++;
                default -> info++;
            }
        }
        chips.removeAll();
        if (warnings.isEmpty()) {
            chips.add(chip("Всё в порядке", OK));
        } else {
            if (err > 0) {
                chips.add(chip("Ошибки " + err, ERR));
            }
            if (warn > 0) {
                chips.add(chip("Предупреждения " + warn, Palette.WARN));
            }
            if (info > 0) {
                chips.add(chip("К сведению " + info, Palette.MUTED));
            }
        }
        Warning firstW = null;
        for (Warning w : warnings) {
            if (w.level() != Level.INFO) {
                firstW = w;
                break;
            }
        }
        if (firstW == null && !warnings.isEmpty()) {
            firstW = warnings.get(0);
        }
        first.setText(open || firstW == null ? " " : firstW.text());
        head.setText((open ? "▾ " : "▴ ") + "Замечания");
        model.clear();
        warnings.forEach(model::addElement);
        remove(scroll);
        if (open && !warnings.isEmpty()) {
            add(scroll, BorderLayout.CENTER);
        }
        chips.revalidate();
        revalidate();
        repaint();
    }

    private static JLabel chip(String text, Color c) {
        JLabel l = new JLabel(text);
        l.setForeground(c);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    private static final class Renderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
            Warning w = (Warning) value;
            JLabel c = (JLabel) super.getListCellRendererComponent(l, w.text(), index, sel, focus);
            c.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            if (!sel) {
                c.setForeground(switch (w.level()) {
                    case ERROR -> ERR;
                    case WARNING -> Palette.WARN;
                    default -> Palette.MUTED;
                });
            }
            return c;
        }
    }
}
