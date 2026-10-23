package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomFurnitureType;
import com.vjstb.ledscheme.service.room.RoomEditor;
import com.vjstb.ledscheme.service.room.RoomItemSpec;
import com.vjstb.ledscheme.service.room.RoomSelection;
import com.vjstb.ledscheme.service.room.RoomUnits;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListCellRenderer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Палитра библиотеки слева (по прототипу {@code planner.html}): поиск, фильтры-«таблетки» по виду позиции, список
 * по категориям со сворачиваемыми группами и карточки со значком-силуэтом, размерами и кнопкой «+». Карточку можно
 * перетащить на план, в стойку или на вид спереди; «+», двойной клик или Enter кладут сразу. Позиции без габаритов
 * помечены янтарной подписью и пунктиром у значка (запрос 2026-10-23). Служит и корзиной: блок, выброшенный из стойки на
 * палитру, удаляется.
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
    }

    /** Заголовок группы-категории в списке. */
    private record Group(String title, int count, boolean collapsed) {
    }

    private final PlannerSession session;
    private final RoomDnd.Controller dnd;
    private final Consumer<String> toast;
    private final BooleanSupplier frontPlanView;
    private final RoomLibraryActions actions;
    private final DefaultListModel<Object> listModel = new DefaultListModel<>();
    /**
     * Список без собственной обработки перетаскивания. Встроенная в JList реакция на «нажал и повёл» переключает
     * выделение по строкам под указателем и включает автопрокрутку по таймеру — карточка при этом «листала» список,
     * а список перерисовывался и пересчитывал кнопки на каждое движение (подвисания по 60–200 мс в журнале
     * зависаний). Перетаскивание ведёт {@link RoomDnd}, выделение ставится только нажатием.
     */
    private final JList<Object> list = new JList<>(listModel) {
        @Override
        public void updateUI() {
            super.updateUI();
            setAutoscrolls(false);
            for (java.awt.event.MouseMotionListener m : getMouseMotionListeners()) {
                if (m.getClass().getName().contains(".plaf.")) {
                    removeMouseMotionListener(m);
                }
            }
        }
    };
    private final JTextField search = new JTextField();
    private final Map<Filter, JToggleButton> chips = new EnumMap<>(Filter.class);
    private final JToggleButton missingChip = chip("Без габаритов");
    private final JLabel count = new JLabel(" ");
    private final JButton addBtn = new JButton("Добавить");
    private final JButton dimsBtn = new JButton("Габариты…");
    private final JButton newBtn = new JButton("Новая ▾");
    private final JButton editBtn = new JButton("Изменить…");
    private final JButton deleteBtn = new JButton("Удалить…");
    private final JButton proposeBtn = new JButton("Предложить…");
    private final Set<String> collapsed = new HashSet<>();
    private Filter filter = Filter.ALL;
    private boolean trashHot;
    private Object shownCatalog;
    private RoomTheme theme = RoomTheme.current();

    LibraryPalette(PlannerSession session, RoomDnd.Controller dnd, Consumer<String> toast, BooleanSupplier frontPlanView,
                   RoomLibraryActions actions) {
        super(new BorderLayout(0, 4));
        this.session = session;
        this.dnd = dnd;
        this.toast = toast;
        this.frontPlanView = frontPlanView;
        this.actions = actions;
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setPreferredSize(new Dimension(RoomTheme.scale(300), 400));
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

        JPanel chipRow = new JPanel(new WrapLayout(java.awt.FlowLayout.LEFT, 4, 4));
        ButtonGroup kinds = new ButtonGroup();
        for (Filter f : Filter.values()) {
            JToggleButton b = chip(f.label);
            b.setSelected(f == Filter.ALL);
            b.addActionListener(e -> {
                filter = f;
                refill();
            });
            kinds.add(b);
            chips.put(f, b);
            chipRow.add(b);
        }
        missingChip.setToolTipText("Показать только позиции, для которых не указаны габариты");
        missingChip.addActionListener(e -> refill());
        chipRow.add(missingChip);
        // высота ряда зависит от его ширины: когда ширина стала известна (или изменилась), пересчитываем перенос строк
        chipRow.addComponentListener(new java.awt.event.ComponentAdapter() {
            private int lastWidth = -1;

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (chipRow.getWidth() != lastWidth) {
                    lastWidth = chipRow.getWidth();
                    chipRow.revalidate();
                }
            }
        });

        JPanel top = new JPanel();
        top.setLayout(new javax.swing.BoxLayout(top, javax.swing.BoxLayout.Y_AXIS));
        top.add(search);
        top.add(javax.swing.Box.createVerticalStrut(4));
        top.add(chipRow);
        count.setFont(count.getFont().deriveFont(Font.PLAIN, RoomTheme.fontSize(11f)));
        top.add(count);
        search.setAlignmentX(LEFT_ALIGNMENT);
        chipRow.setAlignmentX(LEFT_ALIGNMENT);
        count.setAlignmentX(LEFT_ALIGNMENT);
        add(top, BorderLayout.NORTH);

        list.setCellRenderer(new Renderer());
        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                Rectangle cell = i >= 0 ? list.getCellBounds(i, i) : null;
                if (cell == null || !cell.contains(e.getPoint())) {
                    return;
                }
                Object v = listModel.get(i);
                if (v instanceof Group g) {
                    toggle(g.title());
                } else if (v instanceof RoomItemSpec s) {
                    list.setSelectedIndex(i);
                    if (addButtonRect(cell).contains(e.getPoint())) {
                        addSelected();
                    } else {
                        dnd.press(list, e, RoomDnd.Info.library(s.ref(), s.name()));
                    }
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
                if (e.getClickCount() == 2 && selected() != null) {
                    int i = list.locationToIndex(e.getPoint());
                    Rectangle cell = i >= 0 ? list.getCellBounds(i, i) : null;
                    if (cell != null && !addButtonRect(cell).contains(e.getPoint())) {
                        addSelected();
                    }
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
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() instanceof Group) {
                list.clearSelection();
            }
            updateButtons();
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(theme.line));
        add(scroll, BorderLayout.CENTER);

        addBtn.addActionListener(e -> addSelected());
        dimsBtn.addActionListener(e -> {
            RoomItemSpec s = selected();
            if (s != null) {
                actions.specifyDimensions(s.ref());
            }
        });
        editBtn.addActionListener(e -> {
            RoomItemSpec s = selected();
            if (s != null) {
                actions.edit(s.ref());
            }
        });
        deleteBtn.addActionListener(e -> {
            RoomItemSpec s = selected();
            if (s != null) {
                actions.delete(s.ref());
            }
        });
        proposeBtn.addActionListener(e -> {
            RoomItemSpec s = selected();
            if (s != null) {
                actions.propose(s.ref());
            }
        });
        javax.swing.JPopupMenu newMenu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem miDeck = new javax.swing.JMenuItem("Станок…");
        javax.swing.JMenuItem miFurn = new javax.swing.JMenuItem("Стол, стул, мебель…");
        javax.swing.JMenuItem miMon = new javax.swing.JMenuItem("Монитор…");
        javax.swing.JMenuItem miBlock = new javax.swing.JMenuItem("Блок в стойку или мелочь…");
        javax.swing.JMenuItem miCase = new javax.swing.JMenuItem("Рэк-кофр…");
        miDeck.addActionListener(e -> actions.createFurniture(RoomFurnitureType.Kind.DECK));
        miFurn.addActionListener(e -> actions.createFurniture(RoomFurnitureType.Kind.FURNITURE));
        miMon.addActionListener(e -> actions.createFurniture(RoomFurnitureType.Kind.MONITOR));
        miBlock.addActionListener(e -> actions.createEquipment());
        miCase.addActionListener(e -> actions.createCase());
        newMenu.add(miDeck);
        newMenu.add(miFurn);
        newMenu.add(miMon);
        newMenu.addSeparator();
        newMenu.add(miBlock);
        newMenu.add(miCase);
        newBtn.addActionListener(e -> newMenu.show(newBtn, 0, newBtn.getHeight()));
        newBtn.setToolTipText("Создать свою позицию, если нужной нет в библиотеке");
        editBtn.setToolTipText("Изменить свою позицию (общие правит администратор — для них «Габариты…»)");
        deleteBtn.setToolTipText("Удалить свою позицию из личной библиотеки");
        proposeBtn.setToolTipText("Предложить эту позицию на сервер: после проверки администратором она появится у всех");
        addBtn.setToolTipText("Добавить выбранную позицию в раскладку (или перетащите карточку)");
        dimsBtn.setToolTipText("Указать или поправить габариты выбранной позиции");
        JPanel bottom = new JPanel(new java.awt.GridLayout(0, 2, 4, 4));
        bottom.add(addBtn);
        bottom.add(dimsBtn);
        bottom.add(newBtn);
        bottom.add(editBtn);
        bottom.add(deleteBtn);
        bottom.add(proposeBtn);
        add(bottom, BorderLayout.SOUTH);
        refill();
        updateButtons();
    }

    private static JToggleButton chip(String text) {
        JToggleButton b = new JToggleButton(text);
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.setFocusable(false);
        b.setMargin(new java.awt.Insets(1, 8, 1, 8));
        return b;
    }

    @Override
    public void sessionChanged() {
        theme = RoomTheme.current();
        // перестраивать список нужно, только если поменялась библиотека; смена выбора и правка раскладки её не трогают
        if (session.catalog() != shownCatalog) {
            refill();
        }
        updateButtons();
    }

    /** Выбранная карточка (заголовки групп не выбираются). */
    private RoomItemSpec selected() {
        return list.getSelectedValue() instanceof RoomItemSpec s ? s : null;
    }

    private void toggle(String title) {
        if (!collapsed.remove(title)) {
            collapsed.add(title);
        }
        refill();
    }

    private static int typeRank(RoomItemSpec s) {
        return switch (s.type()) {
            case CASE -> 0;
            case RACK -> 1;
            case LOOSE -> 2;
            case FURNITURE -> 3;
            case DECK -> 4;
            case MONITOR -> 5;
        };
    }

    private static String groupTitle(RoomItemSpec s) {
        String c = s.category();
        if (c != null && !c.isBlank()) {
            return c;
        }
        return switch (s.type()) {
            case CASE -> "Кофры";
            case RACK -> "В стойку";
            case LOOSE -> "Мелочь";
            case FURNITURE -> "Мебель";
            case DECK -> "Станки";
            case MONITOR -> "Мониторы";
        };
    }

    private void refill() {
        RoomItemSpec keep = selected();
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        shownCatalog = session.catalog();
        List<RoomItemSpec> all = new ArrayList<>(session.catalog().all());
        int missing = 0;
        Map<Filter, Integer> counts = new EnumMap<>(Filter.class);
        int missingShown = 0;
        List<RoomItemSpec> shown = new ArrayList<>();
        for (RoomItemSpec s : all) {
            if (!s.complete()) {
                missing++;
            }
            boolean matchesQuery = q.isEmpty() || (s.name() + " " + s.category()).toLowerCase(Locale.ROOT).contains(q);
            if (!matchesQuery) {
                continue;
            }
            if (!s.complete()) {
                missingShown++;
            }
            if (missingChip.isSelected() && s.complete()) {
                continue;
            }
            counts.merge(Filter.ALL, 1, Integer::sum);
            for (Filter f : Filter.values()) {
                if (f != Filter.ALL && f.accepts(s)) {
                    counts.merge(f, 1, Integer::sum);
                }
            }
            if (filter.accepts(s)) {
                shown.add(s);
            }
        }
        for (Filter f : Filter.values()) {
            int n = counts.getOrDefault(f, 0);
            JToggleButton b = chips.get(f);
            b.setText(f.label + (f == Filter.ALL ? "" : " " + n));
            b.setEnabled(n > 0 || f == filter || f == Filter.ALL);
        }
        missingChip.setText("Без габаритов " + missingShown);

        // группы по категориям: сначала по виду (кофры, блоки, мелочь, мебель…), потом по названию категории
        Map<String, List<RoomItemSpec>> groups = new LinkedHashMap<>();
        Map<String, Integer> rank = new LinkedHashMap<>();
        for (RoomItemSpec s : shown) {
            String title = groupTitle(s);
            groups.computeIfAbsent(title, k -> new ArrayList<>()).add(s);
            rank.merge(title, typeRank(s), Math::min);
        }
        List<String> order = new ArrayList<>(groups.keySet());
        order.sort(Comparator.comparingInt((String t) -> rank.get(t)).thenComparing(t -> t.toLowerCase(Locale.ROOT)));
        listModel.clear();
        for (String title : order) {
            List<RoomItemSpec> items = groups.get(title);
            items.sort(Comparator.comparing(RoomItemSpec::name, String.CASE_INSENSITIVE_ORDER));
            boolean closed = q.isEmpty() && collapsed.contains(title);
            listModel.addElement(new Group(title, items.size(), closed));
            if (!closed) {
                for (RoomItemSpec s : items) {
                    listModel.addElement(s);
                }
            }
        }
        if (shown.isEmpty()) {
            listModel.addElement(filter == Filter.ALL && q.isEmpty() && !missingChip.isSelected()
                    ? "В библиотеке пока ничего нет. Создайте позицию кнопкой «Новая ▾»."
                    : "Ничего не найдено. Нужной позиции нет — создайте её кнопкой «Новая ▾».");
        }
        count.setText(all.size() + " поз." + (missing > 0 ? ", без габаритов: " + missing : ""));
        if (keep != null) {
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i) instanceof RoomItemSpec s && s.ref().equals(keep.ref())) {
                    list.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void updateButtons() {
        RoomItemSpec s = selected();
        boolean has = s != null;
        addBtn.setEnabled(has);
        dimsBtn.setEnabled(has);
        editBtn.setEnabled(has && actions.canEdit(s.ref()));
        deleteBtn.setEnabled(has && actions.canEdit(s.ref()));
        proposeBtn.setEnabled(has && actions.canPropose(s.ref()));
    }

    private void addSelected() {
        RoomItemSpec s = selected();
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
        if (rackItem != trashHot) {
            trashHot = rackItem;
            repaint();
        }
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
        if (trashHot) {
            trashHot = false;
            repaint();
        }
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (trashHot) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(new Color(theme.err.getRed(), theme.err.getGreen(), theme.err.getBlue(), 50));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(theme.err);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------- карточки

    private static final int CARD_H = 54;
    private static final int HEADER_H = 28;

    /** Кнопка «+» справа на карточке. */
    private static Rectangle addButtonRect(Rectangle cell) {
        int size = RoomTheme.scale(26);
        return new Rectangle(cell.x + cell.width - size - RoomTheme.scale(8), cell.y + (cell.height - size) / 2, size, size);
    }

    private final class Renderer extends JPanel implements ListCellRenderer<Object> {
        private Object value;
        private boolean selected;

        Renderer() {
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> l, Object v, int index, boolean isSelected, boolean focus) {
            value = v;
            selected = isSelected;
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(10, RoomTheme.scale(value instanceof RoomItemSpec ? CARD_H : value instanceof Group ? HEADER_H : 52));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(theme.floor);
                g.fillRect(0, 0, getWidth(), getHeight());
                if (value instanceof Group grp) {
                    paintHeader(g, grp);
                } else if (value instanceof RoomItemSpec s) {
                    paintCard(g, s);
                } else {
                    g.setColor(theme.muted);
                    g.setFont(RoomFonts.derive(getFont(), Font.PLAIN, RoomTheme.fontSize(11.5f)));
                    paintWrapped(g, String.valueOf(value), RoomTheme.scale(12), RoomTheme.scale(16), getWidth() - RoomTheme.scale(24));
                }
            } finally {
                g.dispose();
            }
        }

        private void paintWrapped(Graphics2D g, String text, int x, int y, int width) {
            FontMetrics fm = g.getFontMetrics();
            StringBuilder line = new StringBuilder();
            int yy = y;
            for (String w : text.split(" ")) {
                if (fm.stringWidth(line + w) > width && line.length() > 0) {
                    g.drawString(line.toString(), x, yy);
                    yy += fm.getHeight();
                    line.setLength(0);
                }
                line.append(w).append(' ');
            }
            g.drawString(line.toString(), x, yy);
        }

        private void paintHeader(Graphics2D g, Group grp) {
            g.setColor(theme.floor);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(theme.line);
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            g.setFont(RoomFonts.derive(getFont(), Font.BOLD, RoomTheme.fontSize(11.5f)));
            FontMetrics fm = g.getFontMetrics();
            int base = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g.setColor(theme.muted);
            g.drawString(grp.collapsed() ? "▸" : "▾", RoomTheme.scale(8), base);
            g.setColor(theme.ink);
            String title = fit(grp.title(), fm, getWidth() - RoomTheme.scale(70));
            g.drawString(title, RoomTheme.scale(26), base);
            g.setColor(theme.muted);
            String n = String.valueOf(grp.count());
            g.drawString(n, getWidth() - RoomTheme.scale(10) - fm.stringWidth(n), base);
        }

        private void paintCard(Graphics2D g, RoomItemSpec s) {
            boolean complete = s.complete();
            Color bg = selected ? theme.accentSoft : complete ? theme.floor : RoomTheme.blend(theme.floor, theme.warn, 0.12);
            g.setColor(bg);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(theme.categoryColor(s));
            g.fillRect(0, RoomTheme.scale(4), RoomTheme.scale(3), getHeight() - RoomTheme.scale(8));
            g.setColor(theme.line);
            g.drawLine(RoomTheme.scale(12), getHeight() - 1, getWidth(), getHeight() - 1);
            paintGlyph(g, s, RoomTheme.scale(12), 0, RoomTheme.scale(40), getHeight(), complete);

            int tx = RoomTheme.scale(60);
            int right = addButtonRect(new Rectangle(0, 0, getWidth(), getHeight())).x - RoomTheme.scale(6);
            g.setFont(RoomFonts.derive(getFont(), Font.BOLD, RoomTheme.fontSize(12f)));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(theme.ink);
            g.drawString(fit(s.name(), fm, right - tx), tx, getHeight() / 2 - RoomTheme.scale(3));
            g.setFont(RoomFonts.derive(getFont(), Font.PLAIN, RoomTheme.fontSize(10.5f)));
            fm = g.getFontMetrics();
            String line2 = complete ? dims(s) : "нет габаритов: " + String.join(", ", s.missing());
            g.setColor(complete ? theme.muted : theme.warn);
            g.drawString(fit(line2, fm, right - tx), tx, getHeight() / 2 + fm.getAscent() + RoomTheme.scale(1));

            Rectangle add = addButtonRect(new Rectangle(0, 0, getWidth(), getHeight()));
            g.setColor(selected ? theme.accent : theme.line);
            g.setStroke(new BasicStroke(1.2f));
            g.drawRoundRect(add.x, add.y, add.width, add.height, 8, 8);
            g.setColor(selected ? theme.accent : theme.muted);
            int cx = add.x + add.width / 2;
            int cy = add.y + add.height / 2;
            int arm = RoomTheme.scale(5);
            g.drawLine(cx - arm, cy, cx + arm, cy);
            g.drawLine(cx, cy - arm, cx, cy + arm);
        }
    }

    /** Силуэт позиции, как в прототипе: блок в стойку — по высоте в юнитах, кофр — рамка, станок, мебель, монитор, мелочь. */
    private void paintGlyph(Graphics2D g, RoomItemSpec s, int x, int y, int w, int h, boolean complete) {
        int cx = x + w / 2;
        int cy = y + h / 2;
        int gw;
        int gh;
        switch (s.type()) {
            case RACK -> {
                gw = RoomTheme.scale(34);
                gh = RoomTheme.scale(Math.min(28, s.rackUnits() * 6 + 2));
                fillBox(g, cx, cy, gw, gh, theme.face, theme.hole, false);
            }
            case CASE -> {
                gw = RoomTheme.scale(30);
                gh = RoomTheme.scale((int) Math.min(28, 10 + s.rackUnits() * 1.5));
                g.setColor(theme.muted);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(cx - gw / 2, cy - gh / 2, gw, gh, 6, 6);
            }
            case MONITOR -> {
                gw = RoomTheme.scale((int) Math.min(34, Math.max(14, s.widthCm() / 3.6)));
                gh = RoomTheme.scale((int) Math.min(22, Math.max(10, s.heightCm() / 3.5)));
                fillBox(g, cx, cy, gw, gh, theme.caseBody, theme.muted, false);
            }
            case DECK -> {
                gw = RoomTheme.scale((int) Math.min(34, Math.max(14, s.widthCm() / 6)));
                gh = RoomTheme.scale((int) Math.min(24, Math.max(8, s.depthCm() / 4)));
                fillBox(g, cx, cy, gw, gh, theme.deck, theme.deckLine, false);
            }
            case LOOSE -> {
                gw = RoomTheme.scale((int) Math.max(8, Math.min(26, s.widthCm() * 1.2)));
                gh = RoomTheme.scale((int) Math.max(5, Math.min(16, s.depthCm() * 1.2)));
                fillBox(g, cx, cy, gw, gh, theme.muted, theme.muted, false);
            }
            default -> {
                gw = RoomTheme.scale((int) Math.min(34, Math.max(14, s.widthCm() / 6)));
                gh = RoomTheme.scale((int) Math.min(24, Math.max(8, s.depthCm() / 4)));
                fillBox(g, cx, cy, gw, gh, theme.furn, theme.muted, false);
            }
        }
        if (!complete) {
            // габариты не указаны: силуэт условный — обводим его янтарным пунктиром
            g.setColor(theme.warn);
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {3, 2}, 0));
            g.drawRect(cx - gw / 2 - 3, cy - gh / 2 - 3, gw + 6, gh + 6);
        }
    }

    private static void fillBox(Graphics2D g, int cx, int cy, int w, int h, Color fill, Color line, boolean unused) {
        g.setColor(fill);
        g.fillRoundRect(cx - w / 2, cy - h / 2, w, h, 4, 4);
        g.setColor(line);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(cx - w / 2, cy - h / 2, w, h, 4, 4);
    }

    private static String fit(String s, FontMetrics fm, int width) {
        if (fm.stringWidth(s) <= width) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && fm.stringWidth(t + "…") > width) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
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
