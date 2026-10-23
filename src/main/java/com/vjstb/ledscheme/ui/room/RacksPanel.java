package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.model.RoomLooseItem;
import com.vjstb.ledscheme.model.RoomRackItem;
import com.vjstb.ledscheme.model.RoomSegment;
import com.vjstb.ledscheme.model.RoomTower;
import com.vjstb.ledscheme.service.room.RoomEditor;
import com.vjstb.ledscheme.service.room.RoomItemSpec;
import com.vjstb.ledscheme.service.room.RoomPlanCalc;
import com.vjstb.ledscheme.service.room.RoomSelection;
import com.vjstb.ledscheme.service.room.RoomUnits;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;

/**
 * «Стойки и башни»: башни рядом, у каждой слои сверху вниз — кофры с рядами по юнитам (лицевые панели, юниты
 * слева, высота в сантиметрах справа) и стопки без кофра; вид сверху показывает глубину оборудования в кофре.
 * Все жесты, о которых просил пользователь, здесь: перетащить блок в слот (при занятом месте — вставка между
 * позициями со сдвигом соседей, «очень удобная фича, должна работать везде»), положить в стопку без кофра, вынести
 * блок за пределы башни — удалить, перенести слой в другую башню или в новую. Геометрию считает
 * {@link RackGeometry}, правки делает {@link RoomEditor}.
 */
final class RacksPanel extends JPanel implements RoomDnd.Target, PlannerSession.Listener, Scrollable {

    /** Что под указателем. */
    private enum Kind { NONE, CLOSE_TOWER, CLOSE_SEG, ADD_TOP, ADD_BOTTOM, RACK_ITEM, STACK_ITEM, SEG_HEAD, TOWER, PILL }

    private record Hit(Kind kind, RackGeometry.TowerBox tower, RackGeometry.SegBox seg, String itemId, RoomItemSpec spec,
                       String looseId) {
        static final Hit NONE = new Hit(Kind.NONE, null, null, null, null, null);
    }

    /** Куда сейчас ляжет переносимое (для подсветки) и что сделает отпускание. */
    private enum TargetType { SLOT, STACK, TOWER_STACK, NEW_STACK, SEG_INSERT, NEW_TOWER, LIFT, BOX, TRASH }

    private record Target(TargetType type, RackGeometry.TowerBox tower, RackGeometry.SegBox seg, int pos,
                          RoomEditor.SlotPlan plan, RoomItemSpec spec) {
    }

    private final PlannerSession session;
    private final RoomDnd.Controller dnd;
    private RoomTheme theme = RoomTheme.current();
    private RackGeometry geo = RackGeometry.build(List.of(), RoomPlanCalcEmpty.CAT, 0);
    private boolean topView;
    private Hit pressedHit = Hit.NONE;
    private Target hoverTarget;

    RacksPanel(PlannerSession session, RoomDnd.Controller dnd) {
        this.session = session;
        this.dnd = dnd;
        setFocusable(true);
        session.addListener(this);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                press(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                dnd.drag(RacksPanel.this, e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                release(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !topView) {
                    Hit h = hitAt(e.getPoint());
                    if (h.kind() == Kind.TOWER && h.tower() != null) {
                        renameTower(h.tower().tower());
                    }
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        bind("DELETE", this::removeSelected);
        bind("BACK_SPACE", this::removeSelected);
        bind("ESCAPE", () -> session.select(null));
        bind("control Z", session::undo);
        bind("control Y", session::redo);
        rebuild();
    }

    /** Заглушка пустого каталога для первичного построения до появления раскладки. */
    private static final class RoomPlanCalcEmpty {
        static final com.vjstb.ledscheme.service.room.RoomCatalog CAT = new com.vjstb.ledscheme.service.room.MapRoomCatalog();
    }

    /** Текущая раскладка прямоугольников — для тестов жестов. */
    RackGeometry geometry() {
        return geo;
    }

    void setTopView(boolean top) {
        topView = top;
        rebuild();
    }

    private void bind(String ks, Runnable action) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(ks), "rack-" + ks);
        getActionMap().put("rack-" + ks, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    private void removeSelected() {
        RoomSelection sel = session.selection();
        if (sel != null && session.layout() != null) {
            session.edit(() -> RoomEditor.removeSelection(session.layout(), sel));
        }
    }

    @Override
    public void sessionChanged() {
        theme = RoomTheme.current();
        rebuild();
    }

    private void rebuild() {
        RoomPlanCalc.Result calc = session.calc();
        geo = RackGeometry.build(calc == null ? List.of() : calc.towers(), session.catalog(),
                getParent() != null ? getParent().getHeight() : 0);
        if (topView) {
            geo.size = new Dimension(geo.size.width, Math.max(geo.size.height, measureTop() + 40));
        }
        setPreferredSize(geo.size);
        revalidate();
        repaint();
    }

    // ---------------------------------------------------------------- мышь

    private Hit hitAt(Point p) {
        for (RackGeometry.TowerBox t : geo.towers) {
            if (!t.card.contains(p)) {
                continue;
            }
            if (t.close.contains(p)) {
                return new Hit(Kind.CLOSE_TOWER, t, null, null, null, null);
            }
            if (topView) {
                return new Hit(Kind.TOWER, t, null, null, null, null);
            }
            if (t.addTop.contains(p)) {
                return new Hit(Kind.ADD_TOP, t, null, null, null, null);
            }
            if (t.addBottom.contains(p)) {
                return new Hit(Kind.ADD_BOTTOM, t, null, null, null, null);
            }
            for (RackGeometry.SegBox s : t.segs) {
                if (s.close.contains(p)) {
                    return new Hit(Kind.CLOSE_SEG, t, s, null, null, null);
                }
                if (s.head.contains(p)) {
                    return new Hit(Kind.SEG_HEAD, t, s, null, null, null);
                }
                for (RackGeometry.SlotItem it : s.slotItems) {
                    if (it.rect().contains(p)) {
                        return new Hit(Kind.RACK_ITEM, t, s, it.item().getId(), it.spec(), null);
                    }
                }
                for (RackGeometry.StackItem it : s.stackItems) {
                    if (it.rect().contains(p)) {
                        return new Hit(Kind.STACK_ITEM, t, s, it.item().getId(), it.spec(), null);
                    }
                }
            }
            return new Hit(Kind.TOWER, t, null, null, null, null);
        }
        ControlRoomLayout l = session.layout();
        if (l != null && geo.boxCard.contains(p)) {
            int y = geo.boxCard.y + 150;
            int x = geo.boxCard.x + 12;
            for (RoomLooseItem li : l.getLoose()) {
                if (li.getMode() != RoomLooseItem.Mode.BOX) {
                    continue;
                }
                RoomItemSpec s = session.catalog().resolve(li.getRef());
                int w = textWidth(s.name()) + 14;
                if (x + w > geo.boxCard.x + geo.boxCard.width - 8) {
                    x = geo.boxCard.x + 12;
                    y += 22;
                }
                if (new Rectangle(x, y, w, 18).contains(p)) {
                    return new Hit(Kind.PILL, null, null, null, s, li.getId());
                }
                x += w + 6;
            }
        }
        return Hit.NONE;
    }

    private int textWidth(String s) {
        FontMetrics fm = getFontMetrics(getFont().deriveFont(RoomTheme.fontSize(11)));
        return fm.stringWidth(s);
    }

    private void press(MouseEvent e) {
        pressedHit = Hit.NONE;
        if (!SwingUtilities.isLeftMouseButton(e) || session.layout() == null) {
            return;
        }
        Hit h = hitAt(e.getPoint());
        pressedHit = h;
        switch (h.kind()) {
            case RACK_ITEM -> {
                session.select(RoomSelection.rackItem(h.itemId(), h.tower().tower().getId(), h.seg().seg.getId()));
                dnd.press(this, e, new RoomDnd.Info(RoomDnd.Source.RACK_ITEM, h.spec().ref(), h.itemId(),
                        h.tower().tower().getId(), h.seg().seg.getId(), h.spec().name()));
            }
            case STACK_ITEM -> {
                session.select(RoomSelection.stackItem(h.itemId(), h.tower().tower().getId(), h.seg().seg.getId()));
                dnd.press(this, e, new RoomDnd.Info(RoomDnd.Source.STACK_ITEM, h.spec().ref(), h.itemId(),
                        h.tower().tower().getId(), h.seg().seg.getId(), h.spec().name()));
            }
            case SEG_HEAD -> {
                session.select(RoomSelection.tower(h.tower().tower().getId()));
                String label = h.seg().seg.isStack() ? "Стопка без кофра"
                        : "Кофр " + h.seg().caseSpec.rackUnits() + "U";
                dnd.press(this, e, new RoomDnd.Info(RoomDnd.Source.SEGMENT, null, null, h.tower().tower().getId(),
                        h.seg().seg.getId(), label));
            }
            case TOWER -> session.select(RoomSelection.tower(h.tower().tower().getId()));
            case PILL -> session.select(RoomSelection.loose(h.looseId()));
            default -> {
            }
        }
    }

    private void release(MouseEvent e) {
        if (dnd.isActive()) {
            dnd.release(this, e);
            pressedHit = Hit.NONE;
            hoverTarget = null;
            repaint();
            return;
        }
        dnd.cancel();
        Hit h = pressedHit;
        pressedHit = Hit.NONE;
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return;
        }
        switch (h.kind()) {
            case CLOSE_TOWER -> removeTower(h.tower().tower());
            case CLOSE_SEG -> removeSegment(h.tower().tower(), h.seg().seg);
            case ADD_TOP -> showAddMenu(h.tower().tower(), 0, e.getPoint());
            case ADD_BOTTOM -> showAddMenu(h.tower().tower(), h.tower().tower().getSegments().size(), e.getPoint());
            default -> {
            }
        }
    }

    private void removeTower(RoomTower t) {
        session.edit(() -> RoomEditor.removeSelection(session.layout(), RoomSelection.tower(t.getId())));
    }

    private void removeSegment(RoomTower t, RoomSegment seg) {
        session.edit(() -> {
            t.getSegments().remove(seg);
            if (t.getSegments().isEmpty()) {
                session.layout().getTowers().remove(t);
            }
        });
    }

    private void renameTower(RoomTower t) {
        String name = (String) JOptionPane.showInputDialog(this, "Название башни:", "Башня " + t.getTag(),
                JOptionPane.PLAIN_MESSAGE, null, null, t.getName());
        if (name != null) {
            session.edit(() -> RoomEditor.renameTower(session.layout(), t.getId(), name));
        }
    }

    /** Кнопки «＋ сверху…» / «＋ снизу…»: кофр или стопка без кофра. {@code posTop} — позиция среди слоёв сверху. */
    private void showAddMenu(RoomTower t, int posTop, Point at) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem stack = new JMenuItem("Стопка без кофра");
        stack.addActionListener(e -> session.edit(() -> {
            RoomSegment seg = new RoomSegment();
            seg.setStack(true);
            com.vjstb.ledscheme.service.room.RoomRackLayout.insertSegment(t, seg, posTop, null);
        }));
        menu.add(stack);
        menu.addSeparator();
        List<RoomItemSpec> cases = new ArrayList<>();
        for (RoomItemSpec s : session.catalog().all()) {
            if (s.type() == RoomItemSpec.Type.CASE && s.rackUnits() > 0) {
                cases.add(s);
            }
        }
        cases.sort(Comparator.comparing(RoomItemSpec::name));
        for (RoomItemSpec s : cases) {
            JMenuItem mi = new JMenuItem(s.name() + (s.complete() ? "" : "  — нет габаритов"));
            mi.addActionListener(e -> session.edit(() -> RoomEditor.dropCaseIntoTower(session.layout(), t.getId(), posTop, s.ref())));
            menu.add(mi);
        }
        if (cases.isEmpty()) {
            JMenuItem none = new JMenuItem("В библиотеке нет рэк-кофров");
            none.setEnabled(false);
            menu.add(none);
        }
        menu.show(this, at.x, at.y);
    }

    // ---------------------------------------------------------------- приём перетаскивания

    private Target resolve(RoomDnd.Info info, Point p) {
        ControlRoomLayout l = session.layout();
        if (l == null) {
            return null;
        }
        boolean top = topView;
        RackGeometry.TowerBox tower = geo.towerAt(p);
        boolean inZone = geo.newTowerZone.contains(p);
        if (info.source() == RoomDnd.Source.SEGMENT) {
            if (top) {
                return null;
            }
            if (inZone) {
                return new Target(TargetType.LIFT, null, null, 0, null, null);
            }
            return tower == null ? null
                    : new Target(TargetType.SEG_INSERT, tower, null, geo.segInsertPosition(tower, p), null, null);
        }
        RoomItemSpec spec = session.catalog().resolve(info.ref());
        boolean fromRack = info.source() == RoomDnd.Source.RACK_ITEM || info.source() == RoomDnd.Source.STACK_ITEM;
        switch (spec.type()) {
            case RACK -> {
                if (tower != null && !top) {
                    RackGeometry.SegBox seg = geo.segAt(tower, p);
                    if (seg != null && !seg.seg.isStack() && seg.slots.contains(p)) {
                        double yRows = (p.y - seg.slots.y) / (double) RackGeometry.UH;
                        double relX = (p.x - seg.slots.x) / (double) seg.slots.width;
                        String self = info.source() == RoomDnd.Source.RACK_ITEM ? info.itemId() : null;
                        RoomEditor.SlotPlan plan = RoomEditor.planSlot(session.catalog(), seg.seg, spec, self, yRows, relX);
                        return new Target(TargetType.SLOT, tower, seg, 0, plan, spec);
                    }
                    if (seg != null && seg.seg.isStack()) {
                        return new Target(TargetType.STACK, tower, seg, geo.stackInsertPosition(seg, p), null, spec);
                    }
                    return new Target(TargetType.TOWER_STACK, tower, null, 0, null, spec);
                }
                if (inZone) {
                    return new Target(TargetType.NEW_STACK, null, null, 0, null, spec);
                }
            }
            case LOOSE -> {
                if (tower != null && !top) {
                    RackGeometry.SegBox seg = geo.segAt(tower, p);
                    if (seg != null && seg.seg.isStack()) {
                        return new Target(TargetType.STACK, tower, seg, geo.stackInsertPosition(seg, p), null, spec);
                    }
                    return new Target(TargetType.TOWER_STACK, tower, null, 0, null, spec);
                }
                if (inZone) {
                    return new Target(TargetType.NEW_STACK, null, null, 0, null, spec);
                }
                if (geo.boxCard.contains(p) && info.source() == RoomDnd.Source.LIBRARY) {
                    return new Target(TargetType.BOX, null, null, 0, null, spec);
                }
            }
            case CASE -> {
                if (inZone) {
                    return new Target(TargetType.NEW_TOWER, null, null, 0, null, spec);
                }
                if (tower != null && !top) {
                    return new Target(TargetType.SEG_INSERT, tower, null, geo.segInsertPosition(tower, p), null, spec);
                }
                if (tower != null) {
                    return new Target(TargetType.NEW_TOWER, null, null, 0, null, spec);
                }
            }
            default -> {
            }
        }
        if (fromRack && !geo.boxCard.contains(p)) {
            return new Target(TargetType.TRASH, null, null, 0, null, spec);
        }
        return null;
    }

    @Override
    public RoomDnd.Feedback hover(RoomDnd.Info info, Point p) {
        Target t = resolve(info, p);
        hoverTarget = t;
        repaint();
        if (t == null) {
            return RoomDnd.Feedback.NONE;
        }
        if (t.type() == TargetType.TRASH) {
            return RoomDnd.Feedback.DELETE;
        }
        if (t.type() == TargetType.SLOT && !t.plan().ok()) {
            return RoomDnd.Feedback.NONE;
        }
        return RoomDnd.Feedback.OK;
    }

    @Override
    public void drop(RoomDnd.Info info, Point p) {
        Target t = resolve(info, p);
        hoverTarget = null;
        repaint();
        ControlRoomLayout l = session.layout();
        if (t == null || l == null) {
            return;
        }
        String moved = info.source() == RoomDnd.Source.RACK_ITEM || info.source() == RoomDnd.Source.STACK_ITEM
                ? info.itemId() : null;
        RoomLibRef ref = info.ref();
        switch (t.type()) {
            case SLOT -> {
                if (t.plan().ok()) {
                    session.editSelecting(() -> RoomEditor.dropIntoSlot(l, t.tower().tower().getId(), t.seg().seg.getId(),
                            t.plan(), ref, moved));
                }
            }
            case STACK -> session.editSelecting(() -> RoomEditor.dropIntoStack(l, session.catalog(),
                    t.tower().tower().getId(), t.seg().seg.getId(), t.pos(), ref, moved));
            case TOWER_STACK -> session.editSelecting(() -> RoomEditor.dropIntoStack(l, session.catalog(),
                    t.tower().tower().getId(), null, 0, ref, moved));
            case NEW_STACK -> session.editSelecting(() -> RoomEditor.dropIntoStack(l, session.catalog(), null, null, 0,
                    ref, moved));
            case SEG_INSERT -> {
                if (info.source() == RoomDnd.Source.SEGMENT) {
                    session.editSelecting(() -> RoomEditor.moveSegment(l, info.towerId(), info.segId(),
                            t.tower().tower().getId(), t.pos()));
                } else {
                    session.editSelecting(() -> RoomEditor.dropCaseIntoTower(l, t.tower().tower().getId(), t.pos(), ref));
                }
            }
            case NEW_TOWER -> {
                RoomItemSpec s = session.catalog().resolve(ref);
                session.editSelecting(() -> RoomEditor.addFromLibrary(l, session.catalog(), s, null, false).selection());
            }
            case LIFT -> session.editSelecting(() -> RoomEditor.liftSegmentToNewTower(l, session.catalog(), info.towerId(),
                    info.segId()));
            case BOX -> session.editSelecting(() -> RoomSelection.loose(RoomEditor.addLooseToBox(l, ref).getId()));
            case TRASH -> {
                RoomSelection sel = info.source() == RoomDnd.Source.RACK_ITEM
                        ? RoomSelection.rackItem(info.itemId(), info.towerId(), info.segId())
                        : RoomSelection.stackItem(info.itemId(), info.towerId(), info.segId());
                session.edit(() -> RoomEditor.removeSelection(l, sel));
                session.select(null);
            }
        }
    }

    @Override
    public void dragLeft() {
        hoverTarget = null;
        repaint();
    }

    // ---------------------------------------------------------------- рисование

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(theme.panel);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (session.layout() == null || session.calc() == null) {
                return;
            }
            if (topView) {
                paintTop(g);
            } else {
                for (RackGeometry.TowerBox t : geo.towers) {
                    paintTower(g, t);
                }
            }
            paintZone(g);
            paintBox(g);
            paintHover(g);
        } finally {
            g.dispose();
        }
    }

    private Color cardBg() {
        return theme.floor;
    }

    private void paintTower(Graphics2D g, RackGeometry.TowerBox t) {
        RoomTower tw = t.tower;
        boolean sel = session.selection() != null && session.selection().touchesTower(tw.getId());
        g.setColor(cardBg());
        g.fillRoundRect(t.card.x, t.card.y, t.card.width, t.card.height, 12, 12);
        g.setColor(sel ? theme.accent : theme.line);
        g.setStroke(new BasicStroke(sel ? 2.5f : 1f));
        g.drawRoundRect(t.card.x, t.card.y, t.card.width, t.card.height, 12, 12);
        // шапка
        g.setColor(theme.accent);
        g.fillRoundRect(t.header.x + 8, t.header.y + 5, 24, 24, 8, 8);
        g.setColor(theme.onAccent);
        text(g, tw.getTag(), t.header.x + 20, t.header.y + 17, 14, true, true);
        g.setColor(theme.ink);
        text(g, tw.getName().isEmpty() ? "Башня " + tw.getTag() : tw.getName(), t.header.x + 40, t.header.y + 17, 13, true, false);
        closeButton(g, t.close);
        // скобка высоты
        g.setColor(theme.muted);
        RoomPlanCalc.Result calc = session.calc();
        text(g, RoomUnitsFormat.height(session.units(), t.calc.heightCm(), t.calc.unitsTotal()),
                t.card.x + t.card.width / 2, t.header.y + HEADER_BRACKET_Y(), 11, false, true);
        button(g, t.addTop, "＋ сверху…");
        for (RackGeometry.SegBox s : t.segs) {
            paintSegment(g, t, s);
        }
        button(g, t.addBottom, "＋ снизу…");
        g.setColor(theme.muted);
        String foot = (t.calc.unitsTotal() > 0 ? t.calc.unitsUsed() + "/" + t.calc.unitsTotal() + "U" : "без кофра")
                + (t.calc.unitsTotal() > 0 && t.calc.stackItems() > 0 ? " · +" + t.calc.stackItems() + " без кофра" : "")
                + " · " + RoomUnits.num(t.calc.widthCm(), 0) + "×" + RoomUnits.num(t.calc.depthCm(), 0) + " см · "
                + RoomUnits.num(t.calc.weightKg(), 0) + " кг · " + RoomUnits.num(t.calc.powerW(), 0) + " Вт";
        text(g, foot, t.footer.x, t.footer.y + 12, 11, false, false);
    }

    private static int HEADER_BRACKET_Y() {
        return RackGeometry.HEADER_H + 4 + RackGeometry.BRACKET_H / 2;
    }

    private void paintSegment(Graphics2D g, RackGeometry.TowerBox t, RackGeometry.SegBox s) {
        // шапка слоя
        g.setColor(theme.caseBody);
        g.fillRoundRect(s.head.x, s.head.y, s.head.width, s.head.height + 6, 8, 8);
        g.setColor(theme.caseInk);
        if (s.seg.isStack()) {
            text(g, "Без кофра · стопкой", s.head.x + 8, s.head.y + 12, 11, true, false);
            text(g, s.seg.getItems().size() + " шт.", s.head.x + s.head.width - 48, s.head.y + 12, 11, false, true);
        } else {
            RoomItemSpec k = s.caseSpec;
            int used = 0;
            for (RackGeometry.SlotItem it : s.slotItems) {
                used += it.spec().rackUnits();
            }
            text(g, RoomUnitsFormat.caseHead(session.units(), k), s.head.x + 8, s.head.y + 12, 11, true, false);
            String right = used + "/" + k.rackUnits() + "U";
            text(g, right, s.head.x + s.head.width - 48, s.head.y + 12, 11, false, true);
            if (!k.complete()) {
                g.setColor(theme.warn);
                text(g, "нет габаритов", s.head.x + 100, s.head.y + 12, 10, false, false);
            }
        }
        closeButton(g, s.close);
        // тело
        if (s.seg.isStack()) {
            g.setColor(theme.slot);
            g.fillRect(s.slots.x, s.slots.y, s.slots.width, s.slots.height);
            if (s.stackItems.isEmpty()) {
                g.setColor(theme.muted);
                text(g, "Перетащите сюда оборудование", s.slots.x + s.slots.width / 2, s.slots.y + s.slots.height / 2, 11,
                        false, true);
            }
            for (RackGeometry.StackItem it : s.stackItems) {
                paintBlock(g, it.rect(), it.spec(), false, isSelectedItem(it.item().getId()));
            }
            return;
        }
        int u = Math.max(1, s.caseSpec.rackUnits());
        g.setColor(theme.rail);
        g.fillRect(s.slots.x - 3, s.slots.y, 3, s.slots.height);
        g.fillRect(s.slots.x + s.slots.width, s.slots.y, 3, s.slots.height);
        g.setColor(theme.slot);
        g.fillRect(s.slots.x, s.slots.y, s.slots.width, s.slots.height);
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < u; i++) {
            g.setColor(theme.slotLine);
            g.drawLine(s.slots.x, s.slots.y + i * RackGeometry.UH, s.slots.x + s.slots.width, s.slots.y + i * RackGeometry.UH);
            g.setColor(theme.muted);
            text(g, String.valueOf(i + 1), s.slots.x - 18, s.slots.y + i * RackGeometry.UH + RackGeometry.UH / 2, 10, false, true);
            int rem = u - i;
            if (rem % 2 == 0) {
                text(g, String.valueOf(Math.round(rem * RoomUnits.U_CM)), s.slots.x + s.slots.width + 18,
                        s.slots.y + i * RackGeometry.UH + RackGeometry.UH / 2, 10, false, true);
            }
        }
        for (RackGeometry.SlotItem it : s.slotItems) {
            paintBlock(g, it.rect(), it.spec(), it.tooDeep(), isSelectedItem(it.item().getId()));
        }
    }

    private boolean isSelectedItem(String id) {
        RoomSelection sel = session.selection();
        return sel != null && (sel.type() == RoomSelection.Type.RITEM || sel.type() == RoomSelection.Type.SITEM)
                && sel.id().equals(id);
    }

    private void paintBlock(Graphics2D g, Rectangle r, RoomItemSpec s, boolean tooDeep, boolean selected) {
        g.setColor(theme.face);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 4, 4);
        g.setColor(tooDeep ? theme.err : theme.categoryColor(s));
        g.fillRect(r.x, r.y + r.height - 3, r.width, 3);
        g.setColor(selected ? theme.accent : theme.hole);
        g.setStroke(new BasicStroke(selected ? 2.5f : 1f));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 4, 4);
        Rectangle clip = g.getClipBounds();
        g.clipRect(r.x + 2, r.y, r.width - 4, r.height);
        g.setColor(theme.faceInk);
        text(g, s.name(), r.x + 6, r.y + Math.min(r.height / 2, 11), 11, true, false);
        if (r.height > 30) {
            g.setColor(theme.hole);
            text(g, dims(s), r.x + 6, r.y + 24, 10, false, false);
        }
        g.setClip(clip);
        if (tooDeep) {
            g.setColor(theme.err);
            text(g, "!", r.x + r.width - 10, r.y + 10, 13, true, true);
        }
        if (!s.complete()) {
            g.setColor(theme.warn);
            g.fillOval(r.x + r.width - 12, r.y + r.height - 14, 7, 7);
        }
    }

    private String dims(RoomItemSpec s) {
        return RoomUnitsFormat.block(session.units(), s);
    }

    private void button(Graphics2D g, Rectangle r, String label) {
        g.setColor(theme.line);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {4, 3}, 0));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 8, 8);
        g.setColor(theme.muted);
        text(g, label, r.x + r.width / 2, r.y + r.height / 2, 11, false, true);
    }

    private void closeButton(Graphics2D g, Rectangle r) {
        g.setColor(theme.muted);
        g.setStroke(new BasicStroke(1.6f));
        int cx = r.x + r.width / 2;
        int cy = r.y + r.height / 2;
        g.drawLine(cx - 4, cy - 4, cx + 4, cy + 4);
        g.drawLine(cx - 4, cy + 4, cx + 4, cy - 4);
    }

    private void paintZone(Graphics2D g) {
        Rectangle z = geo.newTowerZone;
        boolean hot = hoverTarget != null && (hoverTarget.type() == TargetType.NEW_STACK
                || hoverTarget.type() == TargetType.NEW_TOWER || hoverTarget.type() == TargetType.LIFT);
        g.setColor(hot ? new Color(theme.accent.getRed(), theme.accent.getGreen(), theme.accent.getBlue(), 50) : cardBg());
        g.fillRoundRect(z.x, z.y, z.width, z.height, 12, 12);
        g.setColor(hot ? theme.accent : theme.line);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {6, 4}, 0));
        g.drawRoundRect(z.x, z.y, z.width, z.height, 12, 12);
        g.setColor(theme.muted);
        String[] lines = {"Перетащите сюда рэк-кофр,", "чтобы собрать башню, или", "любое оборудование: оно ляжет", "стопкой без кофра"};
        for (int i = 0; i < lines.length; i++) {
            text(g, lines[i], z.x + z.width / 2, z.y + 32 + i * 18, 12, false, true);
        }
    }

    private void paintBox(Graphics2D g) {
        Rectangle b = geo.boxCard;
        boolean hot = hoverTarget != null && hoverTarget.type() == TargetType.BOX;
        g.setColor(hot ? new Color(theme.accent.getRed(), theme.accent.getGreen(), theme.accent.getBlue(), 50) : cardBg());
        g.fillRoundRect(b.x, b.y, b.width, b.height, 12, 12);
        g.setColor(hot ? theme.accent : theme.line);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(b.x, b.y, b.width, b.height, 12, 12);
        g.setColor(theme.ink);
        text(g, "Кофр мелочёвки", b.x + 12, b.y + 18, 13, true, false);
        g.setColor(theme.muted);
        RoomPlanCalc.BoxCalc box = session.calc().box();
        if (box.count() == 0) {
            text(g, "Пока пусто. Перетащите сюда мелочь, если", b.x + 12, b.y + 42, 11, false, false);
            text(g, "она остаётся лежать в кейсе.", b.x + 12, b.y + 58, 11, false, false);
        } else {
            text(g, RoomUnits.num(box.volumeLiters(), 1) + " л предметов · " + box.count() + " шт. · "
                    + RoomUnits.num(box.kg(), 1) + " кг", b.x + 12, b.y + 42, 11, false, false);
            if (box.pick() != null) {
                g.setColor(theme.ink);
                text(g, "Подойдёт кейс " + (int) box.pick()[0] + "×" + (int) box.pick()[1] + "×" + (int) box.pick()[2] + " см",
                        b.x + 12, b.y + 62, 11, true, false);
                g.setColor(theme.line);
                g.fillRoundRect(b.x + 12, b.y + 74, b.width - 24, 8, 6, 6);
                g.setColor(box.fill() > .8 ? theme.warn : theme.ok);
                g.fillRoundRect(b.x + 12, b.y + 74, (int) ((b.width - 24) * Math.min(1, box.fill())), 8, 6, 6);
                g.setColor(theme.muted);
                text(g, "заполнение с запасом на поролон: " + Math.round(box.fill() * 100) + "%", b.x + 12, b.y + 96, 10,
                        false, false);
            } else {
                g.setColor(theme.err);
                text(g, "Больше самого большого кейса (80×50×30 см).", b.x + 12, b.y + 62, 11, false, false);
                text(g, "Разбейте на два.", b.x + 12, b.y + 78, 11, false, false);
            }
        }
        g.setColor(theme.muted);
        text(g, "В кейсе сейчас", b.x + 12, b.y + 130, 11, true, false);
        int x = b.x + 12;
        int y = b.y + 150;
        ControlRoomLayout l = session.layout();
        for (RoomLooseItem li : l.getLoose()) {
            if (li.getMode() != RoomLooseItem.Mode.BOX) {
                continue;
            }
            RoomItemSpec s = session.catalog().resolve(li.getRef());
            int w = textWidth(s.name()) + 14;
            if (x + w > b.x + b.width - 8) {
                x = b.x + 12;
                y += 22;
            }
            boolean sel = session.selection() != null && session.selection().type() == RoomSelection.Type.LOOSE
                    && session.selection().id().equals(li.getId());
            g.setColor(sel ? theme.accentSoft : theme.line);
            g.fillRoundRect(x, y, w, 18, 10, 10);
            g.setColor(theme.ink);
            text(g, s.name(), x + 7, y + 9, 11, false, false);
            x += w + 6;
        }
    }

    private void paintHover(Graphics2D g) {
        Target t = hoverTarget;
        if (t == null) {
            return;
        }
        Color ok = theme.accent;
        switch (t.type()) {
            case SLOT -> {
                RoomEditor.SlotPlan plan = t.plan();
                Rectangle s = t.seg().slots;
                int x = s.x + (int) Math.round(plan.c0() / 12.0 * RackGeometry.RACK_W);
                int w = (int) Math.round(plan.cw() / 12.0 * RackGeometry.RACK_W);
                int y = s.y + plan.u0() * RackGeometry.UH;
                int h = t.spec().rackUnits() * RackGeometry.UH;
                Color c = plan.ok() ? ok : theme.err;
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 90));
                g.fillRect(x, y, w, h);
                g.setColor(c);
                g.setStroke(new BasicStroke(2f));
                g.drawRect(x, y, w, h);
                if (plan.insert()) {
                    g.setColor(theme.warn);
                    g.setStroke(new BasicStroke(3f));
                    g.drawLine(s.x, y - 1, s.x + s.width, y - 1);
                }
            }
            case STACK -> {
                int y = geo.stackInsertY(t.seg(), t.pos());
                g.setColor(theme.accent);
                g.setStroke(new BasicStroke(3f));
                g.drawLine(t.seg().slots.x, y, t.seg().slots.x + t.seg().slots.width, y);
            }
            case SEG_INSERT -> {
                int y = geo.segInsertY(t.tower(), t.pos());
                g.setColor(theme.accent);
                g.setStroke(new BasicStroke(3f));
                g.drawLine(t.tower().card.x + 12, y, t.tower().card.x + t.tower().card.width - 12, y);
            }
            case TOWER_STACK -> {
                g.setColor(theme.accent);
                g.setStroke(new BasicStroke(2.5f));
                g.drawRoundRect(t.tower().card.x, t.tower().card.y, t.tower().card.width, t.tower().card.height, 12, 12);
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- вид сверху

    /** Рисует (или только измеряет, если {@code g == null}) вид сверху на кофры; возвращает высоту содержимого. */
    private int layoutTop(Graphics2D g) {
        int maxY = 0;
        double scale = 3.4;
        int x = RackGeometry.MARGIN;
        for (RackGeometry.TowerBox tb : geo.towers) {
            int y = RackGeometry.MARGIN;
            if (g != null) {
                g.setColor(cardBg());
                g.fillRoundRect(tb.card.x, tb.card.y, tb.card.width, Math.max(tb.card.height, 60), 12, 12);
                g.setColor(theme.line);
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(tb.card.x, tb.card.y, tb.card.width, Math.max(tb.card.height, 60), 12, 12);
                g.setColor(theme.accent);
                g.fillRoundRect(x + 8, y + 5, 24, 24, 8, 8);
                g.setColor(theme.onAccent);
                text(g, tb.tower.getTag(), x + 20, y + 17, 14, true, true);
                g.setColor(theme.ink);
                text(g, tb.tower.getName().isEmpty() ? "Башня " + tb.tower.getTag() : tb.tower.getName(), x + 40, y + 17, 13,
                        true, false);
                closeButton(g, tb.close);
            }
            y += RackGeometry.HEADER_H + 8;
            for (RackGeometry.SegBox sb : tb.segs) {
                y = paintTopSegment(g, tb, sb, x + RackGeometry.PAD, y, scale) + 10;
            }
            maxY = Math.max(maxY, y);
            x += RackGeometry.CARD_W + RackGeometry.GAP;
        }
        return maxY;
    }

    private int measureTop() {
        return layoutTop(null);
    }

    private void paintTop(Graphics2D g) {
        // карточки в виде сверху имеют высоту по содержимому
        layoutTop(g);
    }

    private int paintTopSegment(Graphics2D g, RackGeometry.TowerBox tb, RackGeometry.SegBox sb, int x, int y, double scale) {
        session.catalog();
        if (sb.seg.isStack()) {
            double mw = 10;
            double md = 10;
            for (RackGeometry.StackItem it : sb.stackItems) {
                mw = Math.max(mw, it.spec().widthCm());
                md = Math.max(md, it.spec().depthCm());
            }
            int w = (int) Math.round(Math.max(mw, 48) * scale);
            int h = (int) Math.round(Math.max(md, 28) * scale);
            if (g != null) {
                g.setColor(theme.muted);
                text(g, "Без кофра · сверху · " + RoomUnits.num(mw, 0) + "×" + RoomUnits.num(md, 0) + " см", x, y + 6, 11, true,
                        false);
                int oy = y + 16;
                g.setColor(theme.line);
                g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {4, 3}, 0));
                g.drawRect(x, oy, w, h);
                List<RackGeometry.StackItem> byArea = new ArrayList<>(sb.stackItems);
                byArea.sort((a, b) -> Double.compare(b.spec().widthCm() * b.spec().depthCm(), a.spec().widthCm() * a.spec().depthCm()));
                for (RackGeometry.StackItem it : byArea) {
                    int iw = (int) Math.round(it.spec().widthCm() * scale);
                    int id = (int) Math.round(it.spec().depthCm() * scale);
                    Rectangle r = new Rectangle(x + (w - iw) / 2, oy + h - id, iw, id);
                    g.setColor(theme.categoryColor(it.spec()));
                    g.fill(r);
                    g.setColor(theme.onAccent);
                    Rectangle clip = g.getClipBounds();
                    g.clipRect(r.x, r.y, r.width, r.height);
                    text(g, it.spec().name(), r.x + 4, r.y + 9, 10, false, false);
                    g.setClip(clip);
                }
            }
            return y + 16 + h;
        }
        RoomItemSpec k = sb.caseSpec;
        double ow = k.widthCm();
        double od = k.depthCm();
        int w = (int) Math.round(ow * scale);
        int h = (int) Math.round(od * scale);
        if (g != null) {
            g.setColor(theme.muted);
            text(g, "Кофр " + k.rackUnits() + "U · сверху · гл. " + RoomUnits.num(k.innerDepthCm(), 0) + " см", x, y + 6, 11,
                    true, false);
            int oy = y + 16;
            double lid = 8.5;
            double rw = RoomUnits.RACK_WIDTH_CM;
            double rx = (ow - rw) / 2;
            g.setColor(theme.caseBody);
            g.fillRoundRect(x, oy, w, h, 8, 8);
            g.setColor(theme.slot);
            g.fillRect(x + (int) Math.round(rx * scale), oy + (int) Math.round(lid * scale), (int) Math.round(rw * scale),
                    (int) Math.round(k.innerDepthCm() * scale));
            double front = od - lid;
            double maxD = 0;
            for (RackGeometry.SlotItem it : sb.slotItems) {
                maxD = Math.max(maxD, it.spec().depthCm());
            }
            for (RackGeometry.SlotItem it : sb.slotItems) {
                RoomItemSpec s = it.spec();
                int ix = x + (int) Math.round((rx + it.item().getC0() / 12.0 * rw) * scale);
                int iw = (int) Math.round(s.widthTwelfths() / 12.0 * rw * scale);
                int iy = oy + (int) Math.round((front - s.depthCm()) * scale);
                g.setColor(it.tooDeep() ? theme.err : theme.categoryColor(s));
                g.fillRect(ix, iy, iw, (int) Math.round(s.depthCm() * scale));
                g.setColor(theme.onAccent);
                Rectangle clip = g.getClipBounds();
                g.clipRect(ix, iy, iw, (int) Math.round(s.depthCm() * scale));
                text(g, s.name(), ix + 3, iy + 9, 10, false, false);
                g.setClip(clip);
            }
            double gap = k.innerDepthCm() - maxD;
            g.setColor(gap < 5 ? theme.warn : theme.caseInk);
            text(g, "сзади свободно " + RoomUnits.num(Math.max(gap, 0), 0) + " см", x + w / 2, oy + (int) Math.round(lid * scale / 2), 10,
                    false, true);
            g.setColor(theme.caseInk);
            text(g, "ПЕРЕД", x + w / 2, oy + h - 6, 10, false, true);
        }
        return y + 16 + h;
    }

    // ---------------------------------------------------------------- рисование текста

    private void text(Graphics2D g, String s, int x, int y, int size, boolean bold, boolean centered) {
        g.setFont(getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, RoomTheme.fontSize(size)));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(s);
        int bx = centered ? x - w / 2 : x;
        g.drawString(s, bx, y + fm.getAscent() / 2 - fm.getDescent() / 2 + 1);
    }

    // ---------------------------------------------------------------- Scrollable

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(900, 520);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 40;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == javax.swing.SwingConstants.HORIZONTAL ? visibleRect.width - 40 : visibleRect.height - 40;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() != null && getParent().getWidth() > getPreferredSize().width;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() != null && getParent().getHeight() > getPreferredSize().height;
    }
}
