package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.service.room.RoomEditor;
import com.vjstb.ledscheme.service.room.RoomItemSpec;
import com.vjstb.ledscheme.service.room.RoomSelection;
import com.vjstb.ledscheme.service.room.RoomUnits;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Палитра библиотеки слева: оборудование и мебель, которые можно поставить в раскладку. Поиск, фильтр по виду
 * и «только без габаритов»; карточку можно перетащить на план, в стойку или на вид спереди (кнопка «Добавить»
 * или двойной клик кладёт сразу). Позиции без габаритов подсвечены янтарным — запрос пользователя 2026-10-23.
 * Служит и корзиной: блок, выброшенный из стойки на палитру, удаляется.
 */
final class LibraryPalette extends JPanel implements RoomDnd.Target, PlannerSession.Listener {

    private enum Filter {
        ALL("Всё"), CASE("Кофры"), RACK("В стойку"), LOOSE("Мелочь"), FURNITURE("Мебель"), DECK("Станки"), MONITOR("Мониторы");

        final String label;

        Filter(String label) {
            this.label = label;
        }

        boolean accepts(RoomItemSpec s) {
            return this == ALL || s.type().name().equals(name());
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final PlannerSession session;
    private final RoomDnd.Controller dnd;
    private final Consumer<String> toast;
    private final BooleanSupplier frontPlanView;
    private final Consumer<RoomLibRef> specifyDimensions;
    private final DefaultListModel<RoomItemSpec> listModel = new DefaultListModel<>();
    private final JList<RoomItemSpec> list = new JList<>(listModel);
    private final JTextField search = new JTextField();
    private final JComboBox<Filter> kindBox = new JComboBox<>(Filter.values());
    private final JCheckBox onlyMissing = new JCheckBox("Только без габаритов");
    private final JLabel count = new JLabel(" ");
    private final JButton addBtn = new JButton("Добавить");
    private final JButton dimsBtn = new JButton("Габариты…");
    private boolean trashHot;
    private RoomTheme theme = RoomTheme.current();

    LibraryPalette(PlannerSession session, RoomDnd.Controller dnd, Consumer<String> toast, BooleanSupplier frontPlanView,
                   Consumer<RoomLibRef> specifyDimensions) {
        super(new BorderLayout(0, 4));
        this.session = session;
        this.dnd = dnd;
        this.toast = toast;
        this.frontPlanView = frontPlanView;
        this.specifyDimensions = specifyDimensions;
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setPreferredSize(new Dimension(280, 400));
        session.addListener(this);

        search.putClientProperty("JTextField.placeholderText", "Поиск по названию");
        search.setToolTipText("Поиск по названию и категории");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refill();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refill();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refill();
            }
        });
        kindBox.addActionListener(e -> refill());
        onlyMissing.addActionListener(e -> refill());
        JPanel top = new JPanel();
        top.setLayout(new javax.swing.BoxLayout(top, javax.swing.BoxLayout.Y_AXIS));
        top.add(search);
        top.add(javax.swing.Box.createVerticalStrut(4));
        top.add(kindBox);
        top.add(onlyMissing);
        count.setFont(count.getFont().deriveFont(Font.PLAIN, RoomTheme.fontSize(11f)));
        top.add(count);
        search.setAlignmentX(LEFT_ALIGNMENT);
        kindBox.setAlignmentX(LEFT_ALIGNMENT);
        onlyMissing.setAlignmentX(LEFT_ALIGNMENT);
        count.setAlignmentX(LEFT_ALIGNMENT);
        add(top, BorderLayout.NORTH);

        list.setCellRenderer(new Renderer());
        list.setFixedCellHeight(46);
        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                if (i >= 0 && list.getCellBounds(i, i).contains(e.getPoint())) {
                    list.setSelectedIndex(i);
                    RoomItemSpec s = listModel.get(i);
                    dnd.press(list, e, RoomDnd.Info.library(s.ref(), s.name()));
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                dnd.drag(list, e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dnd.isActive()) {
                    dnd.release(list, e);
                } else {
                    dnd.cancel();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
                    addSelected();
                }
            }
        };
        list.addMouseListener(mouse);
        list.addMouseMotionListener(mouse);
        list.getInputMap(WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "add");
        list.getActionMap().put("add", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                addSelected();
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);

        addBtn.addActionListener(e -> addSelected());
        dimsBtn.addActionListener(e -> {
            RoomItemSpec s = list.getSelectedValue();
            if (s != null) {
                specifyDimensions.accept(s.ref());
            }
        });
        addBtn.setToolTipText("Добавить выбранную позицию в раскладку (или перетащите карточку)");
        dimsBtn.setToolTipText("Указать или поправить габариты выбранной позиции");
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        bottom.add(addBtn);
        bottom.add(dimsBtn);
        add(bottom, BorderLayout.SOUTH);
        refill();
    }

    @Override
    public void sessionChanged() {
        theme = RoomTheme.current();
        refill();
    }

    private void refill() {
        RoomItemSpec keep = list.getSelectedValue();
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        Filter f = (Filter) kindBox.getSelectedItem();
        List<RoomItemSpec> all = new ArrayList<>(session.catalog().all());
        int missing = 0;
        List<RoomItemSpec> shown = new ArrayList<>();
        for (RoomItemSpec s : all) {
            if (!s.complete()) {
                missing++;
            }
            if (f != null && !f.accepts(s)) {
                continue;
            }
            if (onlyMissing.isSelected() && s.complete()) {
                continue;
            }
            if (!q.isEmpty() && !(s.name() + " " + s.category()).toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            shown.add(s);
        }
        shown.sort(Comparator.comparing((RoomItemSpec s) -> s.category() == null ? "" : s.category())
                .thenComparing(RoomItemSpec::name));
        listModel.clear();
        for (RoomItemSpec s : shown) {
            listModel.addElement(s);
        }
        count.setText(all.size() + " поз." + (missing > 0 ? ", без габаритов: " + missing : ""));
        if (keep != null) {
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).ref().equals(keep.ref())) {
                    list.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void addSelected() {
        RoomItemSpec s = list.getSelectedValue();
        ControlRoomLayout l = session.layout();
        if (s == null || l == null) {
            if (l == null) {
                toast.accept("Сначала создайте раскладку");
            }
            return;
        }
        String[] message = new String[1];
        boolean front = frontPlanView.getAsBoolean();
        session.editSelecting(() -> {
            RoomEditor.Added a = RoomEditor.addFromLibrary(l, session.catalog(), s, session.selection(), front);
            message[0] = a.message();
            return a.selection();
        });
        if (message[0] != null) {
            toast.accept(message[0]);
        }
    }

    // ---------------------------------------------------------------- корзина

    @Override
    public RoomDnd.Feedback hover(RoomDnd.Info info, Point p) {
        boolean rackItem = info.source() == RoomDnd.Source.RACK_ITEM || info.source() == RoomDnd.Source.STACK_ITEM;
        trashHot = rackItem;
        repaint();
        return rackItem ? RoomDnd.Feedback.DELETE : RoomDnd.Feedback.NONE;
    }

    @Override
    public void drop(RoomDnd.Info info, Point p) {
        trashHot = false;
        repaint();
        ControlRoomLayout l = session.layout();
        if (l == null) {
            return;
        }
        RoomSelection sel = info.source() == RoomDnd.Source.RACK_ITEM
                ? RoomSelection.rackItem(info.itemId(), info.towerId(), info.segId())
                : info.source() == RoomDnd.Source.STACK_ITEM
                ? RoomSelection.stackItem(info.itemId(), info.towerId(), info.segId()) : null;
        if (sel != null) {
            session.edit(() -> RoomEditor.removeSelection(l, sel));
            session.select(null);
            toast.accept("Удалено: «" + info.label() + "». Отменить: Ctrl+Z");
        }
    }

    @Override
    public void dragLeft() {
        trashHot = false;
        repaint();
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (trashHot) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(new Color(theme.err.getRed(), theme.err.getGreen(), theme.err.getBlue(), 50));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(theme.err);
            g2.setStroke(new java.awt.BasicStroke(3f));
            g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------- отрисовка карточки

    private final class Renderer extends JPanel implements ListCellRenderer<RoomItemSpec> {
        private RoomItemSpec spec;
        private boolean selected;

        Renderer() {
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends RoomItemSpec> l, RoomItemSpec value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            spec = value;
            selected = isSelected;
            setToolTipText(null);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean complete = spec.complete();
                Color bg = selected ? theme.accentSoft : complete ? list.getBackground() : theme.warnBg;
                g.setColor(bg);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(theme.categoryColor(spec));
                g.fillRect(0, 3, 5, getHeight() - 6);
                g.setColor(selected ? theme.accent : theme.line);
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                Font f = getFont();
                g.setFont(f.deriveFont(Font.BOLD, RoomTheme.fontSize(12f)));
                g.setColor(theme.ink);
                java.awt.FontMetrics fm = g.getFontMetrics();
                String name = fit(spec.name(), fm, getWidth() - 20);
                g.drawString(name, 12, 17);
                g.setFont(f.deriveFont(Font.PLAIN, RoomTheme.fontSize(10.5f)));
                g.setColor(complete ? theme.muted : theme.warn);
                String line2 = kindLabel(spec) + " · " + dims(spec) + (complete ? "" : " · нет: " + String.join(", ", spec.missing()));
                g.drawString(fit(line2, g.getFontMetrics(), getWidth() - 20), 12, 35);
            } finally {
                g.dispose();
            }
        }
    }

    private static String fit(String s, java.awt.FontMetrics fm, int width) {
        if (fm.stringWidth(s) <= width) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && fm.stringWidth(t + "…") > width) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    private static String kindLabel(RoomItemSpec s) {
        return switch (s.type()) {
            case CASE -> "Кофр";
            case RACK -> "В стойку";
            case LOOSE -> "Мелочь";
            case FURNITURE -> "Мебель";
            case DECK -> "Станок";
            case MONITOR -> "Монитор";
        };
    }

    private static String dims(RoomItemSpec s) {
        return switch (s.type()) {
            case RACK -> s.rackUnits() + "U" + (s.widthTwelfths() < 12 ? " " + s.widthTwelfths() + "/12" : "") + " · гл. "
                    + RoomUnits.num(s.depthCm(), 0) + " см";
            case CASE -> s.rackUnits() + "U · " + RoomUnits.num(s.widthCm(), 0) + "×" + RoomUnits.num(s.heightCm(), 0) + "×"
                    + RoomUnits.num(s.depthCm(), 0) + " см";
            default -> RoomUnits.num(s.widthCm(), 0) + "×" + RoomUnits.num(s.depthCm(), 0) + "×"
                    + RoomUnits.num(s.heightCm(), 0) + " см";
        };
    }
}
