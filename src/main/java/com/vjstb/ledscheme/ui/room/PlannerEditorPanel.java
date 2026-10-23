package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.model.RoomRun;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.room.RoomPlanCalc;
import com.vjstb.ledscheme.service.room.RoomRunKind;
import com.vjstb.ledscheme.service.room.RoomSelection;
import com.vjstb.ledscheme.service.room.RoomUnits;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.Timer;

/**
 * Рабочая зона Планнера пультовой: переключатели «Стойки и башни» / «План» и видов «Спереди» / «Сверху»,
 * отмена и возврат, выдвижные библиотека слева и спецификация справа, сама зона (стойки, план сверху, вид
 * спереди), панель выбранного и окошко замечаний внизу. Решение пользователя 2026-10-23 — вариант «фокус»:
 * библиотека и спецификация выезжают по кнопкам, не загромождая экран.
 *
 * <p>Окно {@code ControlRoomPlannerDialog} показывает здесь выбранную раскладку через
 * {@link #showLayout(ControlRoomLayout)} и пересчитывает при изменении библиотеки через {@link #reload()}.
 */
public final class PlannerEditorPanel extends JPanel implements PlannerSession.Listener {

    private enum Tab { RACKS, PLAN }

    private final PlannerSession session;
    private final RoomDnd.Controller dnd;
    private final RacksPanel racks;
    private final PlanPanel plan;
    private final ElevationPanel elevation;
    private final LibraryPalette palette;
    private final SpecPanel spec;
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JPanel planTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    private final javax.swing.JTextArea planHint = RoomText.hint(" ");
    private final JLabel stats = new JLabel(" ");
    private final JLabel toast = new JLabel(" ");
    private final JLabel viewHint = new JLabel(" ");
    private final JToggleButton racksTab = new JToggleButton("Стойки и башни", true);
    private final JToggleButton planTab = new JToggleButton("План");
    private final JToggleButton frontView = new JToggleButton("Спереди", true);
    private final JToggleButton topView = new JToggleButton("Сверху");
    private final JButton undoBtn = new JButton("Отменить");
    private final JButton redoBtn = new JButton("Вернуть");
    private final JToggleButton libToggle = new JToggleButton("Библиотека", true);
    private final JToggleButton specToggle = new JToggleButton("Спецификация");
    private final JButton roomBtn = new JButton("Площадка…");
    private final JComboBox<RoomUnitsFormat.Mode> unitsBox = new JComboBox<>(RoomUnitsFormat.Mode.values());
    private final JSplitPane leftSplit;
    private final JSplitPane rightSplit;
    private final Timer toastTimer;
    private Tab tab = Tab.RACKS;
    private boolean racksTop;
    private boolean planFront;
    private boolean syncing;

    public PlannerEditorPanel(AppModel model, Consumer<RoomLibRef> specifyDimensions) {
        super(new BorderLayout());
        session = new PlannerSession(model);
        dnd = new RoomDnd.Controller(this);
        racks = new RacksPanel(session, dnd);
        plan = new PlanPanel(session, this::refreshPlanTools);
        elevation = new ElevationPanel(session);
        palette = new LibraryPalette(session, dnd, this::showToast, () -> tab == Tab.PLAN && planFront, specifyDimensions);
        spec = new SpecPanel(session, this::showToast);

        center.add(new JScrollPane(racks), "racks");
        JPanel planCard = new JPanel(new BorderLayout());
        planCard.add(planTools, BorderLayout.NORTH);
        planCard.add(plan, BorderLayout.CENTER);
        planHint.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));
        planCard.add(planHint, BorderLayout.SOUTH);
        center.add(planCard, "plan");
        center.add(elevation, "elev");

        leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, palette, center);
        leftSplit.setDividerLocation(280);
        leftSplit.setContinuousLayout(true);
        leftSplit.setBorder(null);
        rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, spec);
        rightSplit.setResizeWeight(1.0);
        rightSplit.setContinuousLayout(true);
        rightSplit.setBorder(null);
        spec.setVisible(false);
        rightSplit.setDividerSize(0);

        add(buildToolbar(), BorderLayout.NORTH);
        add(rightSplit, BorderLayout.CENTER);
        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(new InspectorBar(session));
        south.add(new WarningsBar(session));
        add(south, BorderLayout.SOUTH);

        toastTimer = new Timer(4500, e -> toast.setText(" "));
        toastTimer.setRepeats(false);
        session.addListener(this);
        applyView();
        updateStats();
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        for (JToggleButton b : new JToggleButton[] {racksTab, planTab, frontView, topView, libToggle, specToggle}) {
            // выбранная кнопка рисуется жирным и шире — берём ширину с запасом, иначе подпись обрезается
            b.setFont(b.getFont().deriveFont(Font.BOLD));
            java.awt.Dimension d = b.getPreferredSize();
            b.setPreferredSize(new java.awt.Dimension(d.width + 10, d.height));
        }
        ButtonGroup tabs = new ButtonGroup();
        tabs.add(racksTab);
        tabs.add(planTab);
        racksTab.addActionListener(e -> setTab(Tab.RACKS));
        planTab.addActionListener(e -> setTab(Tab.PLAN));
        left.add(racksTab);
        left.add(planTab);
        left.add(Box.createHorizontalStrut(8));
        ButtonGroup views = new ButtonGroup();
        views.add(frontView);
        views.add(topView);
        frontView.addActionListener(e -> setView(false));
        topView.addActionListener(e -> setView(true));
        left.add(frontView);
        left.add(topView);
        left.add(Box.createHorizontalStrut(8));
        undoBtn.addActionListener(e -> session.undo());
        redoBtn.addActionListener(e -> session.redo());
        undoBtn.setToolTipText("Отменить последнюю правку раскладки (Ctrl+Z)");
        redoBtn.setToolTipText("Вернуть отменённую правку (Ctrl+Y)");
        left.add(undoBtn);
        left.add(redoBtn);
        left.add(Box.createHorizontalStrut(8));
        libToggle.addActionListener(e -> toggleLibrary());
        specToggle.addActionListener(e -> toggleSpec());
        left.add(libToggle);
        left.add(specToggle);
        left.add(Box.createHorizontalStrut(8));
        left.add(new JLabel("Единицы:"));
        unitsBox.setSelectedItem(RoomUnitsFormat.Mode.BOTH);
        unitsBox.setToolTipText("Как подписывать размеры: в юнитах, в сантиметрах или вместе");
        unitsBox.addActionListener(e -> session.setUnits((RoomUnitsFormat.Mode) unitsBox.getSelectedItem()));
        left.add(unitsBox);
        left.add(Box.createHorizontalStrut(4));
        roomBtn.addActionListener(e -> editRoomSize());
        roomBtn.setToolTipText("Размер площадки пультовой: ширина и глубина");
        left.add(roomBtn);
        bar.add(left, BorderLayout.NORTH);
        JPanel info = new JPanel(new BorderLayout(10, 0));
        stats.setFont(stats.getFont().deriveFont(Font.PLAIN, RoomTheme.fontSize(11f)));
        toast.setFont(toast.getFont().deriveFont(Font.BOLD, RoomTheme.fontSize(11f)));
        toast.setForeground(RoomTheme.current().accent);
        viewHint.setFont(viewHint.getFont().deriveFont(Font.PLAIN, RoomTheme.fontSize(11f)));
        viewHint.setForeground(RoomTheme.current().muted);
        JPanel texts = new JPanel(new BorderLayout());
        texts.add(stats, BorderLayout.NORTH);
        texts.add(viewHint, BorderLayout.SOUTH);
        info.add(texts, BorderLayout.CENTER);
        info.add(toast, BorderLayout.EAST);
        info.setBorder(BorderFactory.createEmptyBorder(0, 8, 2, 6));
        bar.add(info, BorderLayout.SOUTH);
        return bar;
    }

    // ---------------------------------------------------------------- публичное

    /** Общее состояние редактора (выбор, раскладка, история) — для окна и тестов. */
    public PlannerSession session() {
        return session;
    }

    /** Показать раскладку (или {@code null}, если у сцены их нет). */
    public void showLayout(ControlRoomLayout l) {
        if (session.layout() == l) {
            session.reload();
        } else {
            session.setLayout(l);
        }
    }

    /** Библиотека изменилась: пересобрать каталог и расчёт. */
    public void reload() {
        session.reload();
    }

    private void editRoomSize() {
        ControlRoomLayout l = session.layout();
        if (l == null) {
            showToast("Сначала создайте раскладку");
            return;
        }
        JTextField w = new JTextField(RoomUnits.num(l.getRoomWidthCm() / 100, 2), 6);
        JTextField d = new JTextField(RoomUnits.num(l.getRoomDepthCm() / 100, 2), 6);
        JPanel p = new JPanel(new java.awt.GridLayout(0, 2, 8, 6));
        p.add(new JLabel("Ширина, м"));
        p.add(w);
        p.add(new JLabel("Глубина (от сцены), м"));
        p.add(d);
        if (JOptionPane.showConfirmDialog(this, p, "Размер площадки", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            double wm = Double.parseDouble(w.getText().trim().replace(',', '.'));
            double dm = Double.parseDouble(d.getText().trim().replace(',', '.'));
            if (wm < 1 || dm < 1 || wm > 60 || dm > 60) {
                throw new NumberFormatException();
            }
            session.edit(() -> {
                l.setRoomWidthCm(Math.round(wm * 100));
                l.setRoomDepthCm(Math.round(dm * 100));
            });
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Размер — число метров от 1 до 60.", "Размер площадки",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    // ---------------------------------------------------------------- переключатели

    private void setTab(Tab t) {
        tab = t;
        applyView();
    }

    private void setView(boolean top) {
        if (tab == Tab.RACKS) {
            racksTop = top;
        } else {
            planFront = !top;
        }
        applyView();
    }

    private void applyView() {
        syncing = true;
        try {
            racksTab.setSelected(tab == Tab.RACKS);
            planTab.setSelected(tab == Tab.PLAN);
            boolean top = tab == Tab.RACKS ? racksTop : !planFront;
            frontView.setSelected(!top);
            topView.setSelected(top);
            racks.setTopView(racksTop);
            if (tab == Tab.RACKS) {
                cards.show(center, "racks");
                viewHint.setText(racksTop ? "Глубина оборудования от передней крышки. Только просмотр"
                        : "Лицевые панели: юниты слева, высота в см справа");
            } else if (planFront) {
                cards.show(center, "elev");
                viewHint.setText("Тяните объекты: вбок — по плану, вверх — на опору, вниз — на пол или под стол");
            } else {
                cards.show(center, "plan");
                viewHint.setText("Перетаскивайте башни, мебель и мелочь");
            }
            refreshPlanTools();
        } finally {
            syncing = false;
        }
        center.revalidate();
        center.repaint();
    }

    private void toggleLibrary() {
        palette.setVisible(libToggle.isSelected());
        leftSplit.setDividerSize(libToggle.isSelected() ? 6 : 0);
        if (libToggle.isSelected()) {
            leftSplit.setDividerLocation(280);
        }
        leftSplit.revalidate();
    }

    private void toggleSpec() {
        spec.setVisible(specToggle.isSelected());
        rightSplit.setDividerSize(specToggle.isSelected() ? 6 : 0);
        if (specToggle.isSelected()) {
            rightSplit.setDividerLocation(Math.max(200, rightSplit.getWidth() - 320));
        }
        rightSplit.revalidate();
    }

    private void showToast(String message) {
        toast.setText(message);
        toastTimer.restart();
    }

    // ---------------------------------------------------------------- сессия

    @Override
    public void sessionChanged() {
        undoBtn.setEnabled(session.canUndo());
        redoBtn.setEnabled(session.canRedo());
        updateStats();
        refreshPlanTools();
    }

    private void updateStats() {
        RoomPlanCalc.Result r = session.calc();
        ControlRoomLayout l = session.layout();
        if (r == null || l == null) {
            stats.setText("Раскладки нет — создайте её кнопкой «Новая…».");
            return;
        }
        RoomPlanCalc.Totals t = r.totals();
        stats.setText("Башен " + t.towers() + " · юниты " + t.unitsUsed() + "/" + t.unitsTotal() + " · кофров " + t.cases()
                + " · " + RoomUnits.num(t.kg(), 0) + " кг · " + RoomUnits.num(t.powerW() / 1000, 2) + " кВт ("
                + RoomUnits.num(t.powerW() / 230, 1) + " А)"
                + (t.deck().count() > 0 ? " · пол из станков " + RoomUnits.num(t.floorM2(), 1) + " м²" : "")
                + " · площадка " + RoomUnits.num(l.getRoomWidthCm() / 100, 1) + "×" + RoomUnits.num(l.getRoomDepthCm() / 100, 1) + " м");
    }

    // ---------------------------------------------------------------- инструменты плана

    private static JLabel swatch(String text, java.awt.Color fill, java.awt.Color line) {
        JLabel l = new JLabel(text);
        l.setIcon(new javax.swing.Icon() {
            @Override
            public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
                g.setColor(fill);
                g.fillRect(x, y, 16, 10);
                g.setColor(line);
                g.drawRect(x, y, 16, 10);
            }

            @Override
            public int getIconWidth() {
                return 17;
            }

            @Override
            public int getIconHeight() {
                return 11;
            }
        });
        l.setFont(l.getFont().deriveFont(Font.PLAIN, RoomTheme.fontSize(11f)));
        return l;
    }

    private void refreshPlanTools() {
        planTools.removeAll();
        boolean show = tab == Tab.PLAN && !planFront;
        planTools.setVisible(show);
        if (!show) {
            planTools.revalidate();
            return;
        }
        PlanView.Mode mode = plan.view().mode;
        ButtonGroup g = new ButtonGroup();
        JToggleButton obj = new JToggleButton("Объекты", mode == PlanView.Mode.OBJECTS);
        JToggleButton runs = new JToggleButton("Трассы", mode != PlanView.Mode.OBJECTS);
        g.add(obj);
        g.add(runs);
        obj.addActionListener(e -> plan.setMode(PlanView.Mode.OBJECTS));
        runs.addActionListener(e -> plan.setMode(PlanView.Mode.RUNS));
        planTools.add(obj);
        planTools.add(runs);
        if (mode != PlanView.Mode.OBJECTS) {
            JComboBox<RoomRunKind> kind = new JComboBox<>(RoomRunKind.values());
            kind.setSelectedItem(plan.view().drawKind);
            kind.setToolTipText("Тип новой трассы");
            kind.addActionListener(e -> plan.setDrawKind((RoomRunKind) kind.getSelectedItem()));
            planTools.add(kind);
            if (mode == PlanView.Mode.DRAW) {
                JButton done = new JButton("Готово");
                done.addActionListener(e -> plan.finishDrawing());
                JButton cancel = new JButton("Отмена");
                cancel.addActionListener(e -> plan.cancelDrawing());
                planTools.add(done);
                planTools.add(cancel);
            } else {
                JButton draw = new JButton("＋ Нарисовать трассу");
                draw.addActionListener(e -> plan.setMode(PlanView.Mode.DRAW));
                planTools.add(draw);
            }
        }
        ControlRoomLayout l = session.layout();
        if (l != null) {
            planTools.add(Box.createHorizontalStrut(10));
            RoomTheme th = RoomTheme.current();
            for (RoomRun r : l.getRuns()) {
                JButton b = new JButton("● " + r.getName() + " · "
                        + RoomUnits.num(com.vjstb.ledscheme.service.room.RoomRunMath.length(r) / 100, 1) + " м");
                b.setForeground(th.runColor(RoomRunKind.of(r.getKind())));
                b.setBorderPainted(false);
                b.setContentAreaFilled(false);
                b.setFocusable(false);
                RoomSelection sel = session.selection();
                if (sel != null && sel.type() == RoomSelection.Type.RUN && sel.id().equals(r.getId())) {
                    b.setFont(b.getFont().deriveFont(Font.BOLD));
                }
                b.addActionListener(e -> session.select(RoomSelection.run(r.getId())));
                planTools.add(b);
            }
            boolean hasDeck = l.getFurniture().stream().anyMatch(f ->
                    session.catalog().resolve(f.getRef()).type() == com.vjstb.ledscheme.service.room.RoomItemSpec.Type.DECK);
            if (hasDeck) {
                planTools.add(swatch("Станок на полу", th.deck, th.deckLine));
                planTools.add(swatch("Станок-стол", th.tableDeck, th.tableDeckLine));
            }
            if (l.getRuns().isEmpty()) {
                JLabel none = new JLabel("Трасс пока нет");
                none.setForeground(th.muted);
                planTools.add(none);
            }
        }
        planHint.setText(switch (mode) {
            case OBJECTS -> "Рамка пина — мелочь в реальном размере. Если отпустить её ближе 30 см к трассе, она встанет на "
                    + "трассу и запомнит расстояние от ввода. Синяя черта у башни — лицевая сторона. Правый клик по мебели "
                    + "или станку открывает параметры.";
            case RUNS -> "Выберите трассу кликом по линии или в списке. Тяните белые точки, чтобы изменить маршрут. Кружок с "
                    + "плюсом на середине отрезка добавляет точку. Двойной клик по точке удаляет её.";
            case DRAW -> "Кликайте по плану, чтобы ставить точки. Двойной клик, Enter или «Готово» завершает, Backspace убирает "
                    + "последнюю точку, Esc отменяет. У стен точки примагничиваются к краю.";
        });
        planTools.revalidate();
        planTools.repaint();
    }
}
