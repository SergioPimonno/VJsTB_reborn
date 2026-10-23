package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomLooseItem;
import com.vjstb.ledscheme.model.RoomPoint;
import com.vjstb.ledscheme.model.RoomRun;
import com.vjstb.ledscheme.model.RoomTower;
import com.vjstb.ledscheme.service.room.RoomEditor;
import com.vjstb.ledscheme.service.room.RoomItemSpec;
import com.vjstb.ledscheme.service.room.RoomRunKind;
import com.vjstb.ledscheme.service.room.RoomRunMath;
import com.vjstb.ledscheme.service.room.RoomSelection;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Вид сверху: план площадки с жестами (порт обработчиков плана прототипа). Объекты переносятся мышью
 * (башни и мебель — шагом 5 см, станки — по сетке 50 см, мелочь прилипает к трассе ближе 30 см), правый клик
 * открывает действия над объектом, в режиме «Трассы» правятся вершины, в режиме рисования кликами ставятся
 * новые точки. Принимает карточки библиотеки, брошенные на план. Рисует {@link PlanView}.
 */
final class PlanPanel extends JPanel implements RoomDnd.Target, PlannerSession.Listener {

    private static final double DRAG_THRESHOLD_PX = 3;

    private final PlannerSession session;
    private final PlanView view;
    private final Runnable onModeChanged;

    // состояние жеста
    private PlanView.Hit pressed = PlanView.Hit.NONE;
    private double pressWorldX;
    private double pressWorldY;
    private double grabDx;
    private double grabDy;
    private Point pressScreen;
    private boolean dragging;

    PlanPanel(PlannerSession session, Runnable onModeChanged) {
        this.session = session;
        this.view = new PlanView(session);
        this.onModeChanged = onModeChanged;
        setFocusable(true);
        setPreferredSize(new Dimension(640, 420));
        setOpaque(true);
        session.addListener(this);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                handlePress(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                handleDrag(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleRelease(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (view.mode == PlanView.Mode.DRAW) {
                    RoomPoint w = view.toWorld(e.getX(), e.getY());
                    ControlRoomLayout l = session.layout();
                    if (l != null) {
                        view.drawHover = RoomRunMath.snapPoint(l.getRoomWidthCm(), l.getRoomDepthCm(), w.getX(), w.getY());
                        repaint();
                    }
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        bindKeys();
    }

    PlanView view() {
        return view;
    }

    void setMode(PlanView.Mode mode) {
        view.mode = mode;
        view.drawPoints.clear();
        view.drawHover = null;
        repaint();
        onModeChanged.run();
    }

    void setDrawKind(RoomRunKind kind) {
        view.drawKind = kind;
        repaint();
    }

    @Override
    public void sessionLive() {
        repaint();
    }

    @Override
    public void sessionChanged() {
        view.refreshTheme();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        view.paint((Graphics2D) g, getWidth(), getHeight());
    }

    // ---------------------------------------------------------------- мышь

    private void handlePress(MouseEvent e) {
        ControlRoomLayout layout = session.layout();
        if (layout == null) {
            return;
        }
        RoomPoint w = view.toWorld(e.getX(), e.getY());
        if (view.mode == PlanView.Mode.DRAW) {
            handleDrawPress(e, layout, w);
            return;
        }
        PlanView.Hit hit = view.hit(w.getX(), w.getY());
        if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
            selectHit(hit);
            showPopup(e, hit);
            return;
        }
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return;
        }
        pressed = hit;
        pressWorldX = w.getX();
        pressWorldY = w.getY();
        pressScreen = e.getPoint();
        dragging = false;
        grabDx = 0;
        grabDy = 0;
        switch (hit.kind()) {
            case NONE -> session.select(null);
            case TOWER -> {
                RoomTower t = RoomEditor.tower(layout, hit.id());
                session.select(RoomSelection.tower(hit.id()));
                grabDx = t.getXCm() - w.getX();
                grabDy = t.getYCm() - w.getY();
            }
            case FURN -> {
                RoomFurniture f = RoomEditor.furniture(layout, hit.id());
                session.select(RoomSelection.furniture(hit.id()));
                grabDx = f.getXCm() - w.getX();
                grabDy = f.getYCm() - w.getY();
            }
            case LOOSE -> session.select(RoomSelection.loose(hit.id()));
            case RUN -> session.select(RoomSelection.run(hit.id()));
            case VERTEX -> {
                session.select(RoomSelection.run(hit.id()));
                if (e.getClickCount() == 2) {
                    RoomRun r = RoomEditor.run(layout, hit.id());
                    int idx = hit.index();
                    if (r != null && r.getPoints().size() > 2) {
                        session.edit(() -> RoomEditor.removeRunPoint(r, idx));
                    }
                    pressed = PlanView.Hit.NONE;
                }
            }
            case MID -> {
                RoomRun r = RoomEditor.run(layout, hit.id());
                session.select(RoomSelection.run(hit.id()));
                if (r != null) {
                    session.beginLive();
                    RoomEditor.insertRunPoint(r, hit.index());
                    session.live();
                    pressed = new PlanView.Hit(PlanView.Hit.Kind.VERTEX, hit.id(), hit.index() + 1);
                    dragging = true;
                }
            }
        }
    }

    private void handleDrawPress(MouseEvent e, ControlRoomLayout layout, RoomPoint w) {
        if (SwingUtilities.isRightMouseButton(e)) {
            if (!view.drawPoints.isEmpty()) {
                view.drawPoints.remove(view.drawPoints.size() - 1);
                repaint();
            }
            return;
        }
        RoomPoint p = RoomRunMath.snapPoint(layout.getRoomWidthCm(), layout.getRoomDepthCm(), w.getX(), w.getY());
        if (e.getClickCount() >= 2) {
            finishDrawing();
            return;
        }
        view.drawPoints.add(p);
        repaint();
    }

    private void handleDrag(MouseEvent e) {
        ControlRoomLayout layout = session.layout();
        if (layout == null || pressed.kind() == PlanView.Hit.Kind.NONE) {
            return;
        }
        if (!dragging) {
            if (pressScreen == null || e.getPoint().distance(pressScreen) < DRAG_THRESHOLD_PX) {
                return;
            }
            boolean movable = pressed.kind() == PlanView.Hit.Kind.TOWER || pressed.kind() == PlanView.Hit.Kind.FURN
                    || pressed.kind() == PlanView.Hit.Kind.LOOSE || pressed.kind() == PlanView.Hit.Kind.VERTEX;
            if (!movable) {
                return;
            }
            dragging = true;
            session.beginLive();
        }
        RoomPoint w = view.toWorld(e.getX(), e.getY());
        switch (pressed.kind()) {
            case TOWER -> RoomEditor.moveTower(layout, pressed.id(), w.getX() + grabDx, w.getY() + grabDy);
            case FURN -> RoomEditor.moveFurniture(layout, session.catalog(), pressed.id(), w.getX() + grabDx,
                    w.getY() + grabDy);
            case LOOSE -> dragLoose(layout, w);
            case VERTEX -> {
                RoomRun r = RoomEditor.run(layout, pressed.id());
                if (r != null) {
                    RoomEditor.moveRunPoint(layout, r, pressed.index(), w.getX(), w.getY());
                }
            }
            default -> {
            }
        }
        session.live();
    }

    private void dragLoose(ControlRoomLayout layout, RoomPoint w) {
        RoomLooseItem l = RoomEditor.loose(layout, pressed.id());
        if (l == null) {
            return;
        }
        RoomRunMath.Nearest n = RoomRunMath.snapToRuns(layout.getRuns(), w.getX(), w.getY());
        if (n != null) {
            l.setMode(RoomLooseItem.Mode.RUN);
            l.setRunId(n.runId());
            l.setSCm(n.s());
            l.setXCm(null);
            l.setYCm(null);
            view.snapHintRun = n.runId();
        } else {
            l.setMode(RoomLooseItem.Mode.FREE);
            l.setRunId(null);
            l.setSCm(null);
            l.setXCm(Math.max(-30, Math.min(layout.getRoomWidthCm() + 30, w.getX())));
            l.setYCm(Math.max(-30, Math.min(layout.getRoomDepthCm() + 30, w.getY())));
            view.snapHintRun = null;
        }
    }

    private void handleRelease(MouseEvent e) {
        if (e.isPopupTrigger()) {
            RoomPoint w = view.toWorld(e.getX(), e.getY());
            showPopup(e, view.hit(w.getX(), w.getY()));
        }
        boolean was = dragging;
        dragging = false;
        pressed = PlanView.Hit.NONE;
        view.snapHintRun = null;
        if (was) {
            session.commitLive();
        }
        repaint();
    }

    private void selectHit(PlanView.Hit hit) {
        switch (hit.kind()) {
            case TOWER -> session.select(RoomSelection.tower(hit.id()));
            case FURN -> session.select(RoomSelection.furniture(hit.id()));
            case LOOSE -> session.select(RoomSelection.loose(hit.id()));
            case RUN, VERTEX, MID -> session.select(RoomSelection.run(hit.id()));
            default -> {
            }
        }
    }

    private void showPopup(MouseEvent e, PlanView.Hit hit) {
        JPopupMenu menu = new JPopupMenu();
        switch (hit.kind()) {
            case FURN -> {
                RoomFurniture f = RoomEditor.furniture(session.layout(), hit.id());
                if (f == null) {
                    return;
                }
                menu.add(item("Параметры…", () -> RoomOverridesDialog.show(SwingUtilities.getWindowAncestor(this), session, f)));
                menu.add(item("Повернуть на 90°", this::rotateSelected));
                menu.add(item("Убрать", this::removeSelected));
            }
            case TOWER -> {
                menu.add(item("Повернуть на 90°", this::rotateSelected));
                menu.add(item("Убрать башню", this::removeSelected));
            }
            case LOOSE -> menu.add(item("Убрать", this::removeSelected));
            case RUN, VERTEX, MID -> menu.add(item("Удалить трассу", this::removeSelected));
            default -> {
                return;
            }
        }
        menu.show(this, e.getX(), e.getY());
    }

    private JMenuItem item(String text, Runnable action) {
        JMenuItem m = new JMenuItem(text);
        m.addActionListener(e -> action.run());
        return m;
    }

    // ---------------------------------------------------------------- действия

    void removeSelected() {
        RoomSelection sel = session.selection();
        if (sel != null && session.layout() != null) {
            session.edit(() -> RoomEditor.removeSelection(session.layout(), sel));
        }
    }

    void rotateSelected() {
        RoomSelection sel = session.selection();
        if (sel != null && session.layout() != null) {
            session.edit(() -> RoomEditor.rotate(session.layout(), sel));
        }
    }

    void finishDrawing() {
        ControlRoomLayout l = session.layout();
        if (l == null || view.drawPoints.size() < 2) {
            return;
        }
        java.util.List<RoomPoint> pts = new java.util.ArrayList<>(view.drawPoints);
        RoomRunKind kind = view.drawKind;
        session.editSelecting(() -> {
            RoomRun r = RoomEditor.addRun(l, kind, pts);
            return r == null ? null : RoomSelection.run(r.getId());
        });
        view.drawPoints.clear();
        view.drawHover = null;
        setMode(PlanView.Mode.RUNS);
    }

    void cancelDrawing() {
        view.drawPoints.clear();
        view.drawHover = null;
        setMode(PlanView.Mode.RUNS);
    }

    private void nudge(int dx, int dy) {
        RoomSelection sel = session.selection();
        ControlRoomLayout l = session.layout();
        if (sel == null || l == null) {
            return;
        }
        if (sel.type() == RoomSelection.Type.TOWER) {
            RoomTower t = RoomEditor.tower(l, sel.id());
            if (t != null) {
                session.edit(() -> RoomEditor.moveTower(l, t.getId(), t.getXCm() + dx, t.getYCm() + dy));
            }
        } else if (sel.type() == RoomSelection.Type.FURN) {
            RoomFurniture f = RoomEditor.furniture(l, sel.id());
            if (f != null) {
                session.edit(() -> RoomEditor.moveFurniture(l, session.catalog(), f.getId(), f.getXCm() + dx,
                        f.getYCm() + dy));
            }
        }
    }

    private void bindKeys() {
        bind("DELETE", () -> {
            if (view.mode == PlanView.Mode.DRAW) {
                return;
            }
            removeSelected();
        });
        bind("BACK_SPACE", () -> {
            if (view.mode == PlanView.Mode.DRAW) {
                if (!view.drawPoints.isEmpty()) {
                    view.drawPoints.remove(view.drawPoints.size() - 1);
                    repaint();
                }
            } else {
                removeSelected();
            }
        });
        bind("ENTER", () -> {
            if (view.mode == PlanView.Mode.DRAW) {
                finishDrawing();
            }
        });
        bind("ESCAPE", () -> {
            if (view.mode == PlanView.Mode.DRAW) {
                cancelDrawing();
            } else {
                session.select(null);
            }
        });
        bind("R", this::rotateSelected);
        bind("LEFT", () -> nudge((int) -5, 0));
        bind("RIGHT", () -> nudge(5, 0));
        bind("UP", () -> nudge(0, -5));
        bind("DOWN", () -> nudge(0, 5));
        bind("control Z", session::undo);
        bind("control Y", session::redo);
    }

    private void bind(String keystroke, Runnable action) {
        KeyStroke ks = KeyStroke.getKeyStroke(keystroke);
        if (ks == null) {
            ks = KeyStroke.getKeyStroke(KeyEvent.getExtendedKeyCodeForChar(keystroke.charAt(0)), 0);
        }
        String key = "plan-" + keystroke;
        getInputMap(WHEN_FOCUSED).put(ks, key);
        getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    // ---------------------------------------------------------------- приём из библиотеки

    @Override
    public RoomDnd.Feedback hover(RoomDnd.Info info, Point p) {
        ControlRoomLayout l = session.layout();
        if (l == null || info.source() != RoomDnd.Source.LIBRARY) {
            setPreview(null);
            return RoomDnd.Feedback.NONE;
        }
        RoomPoint w = view.toWorld(p.x, p.y);
        if (w.getX() < -40 || w.getX() > l.getRoomWidthCm() + 40 || w.getY() < -40 || w.getY() > l.getRoomDepthCm() + 40) {
            setPreview(null);
            return RoomDnd.Feedback.NONE;
        }
        RoomItemSpec spec = session.catalog().resolve(info.ref());
        double x = w.getX();
        double y = w.getY();
        double pw = Math.max(spec.widthCm(), 12);
        double pd = Math.max(spec.depthCm(), 12);
        boolean ok = true;
        view.snapHintRun = null;
        switch (spec.type()) {
            case RACK -> {
                RoomTower t = RoomEditor.towerAt(l, session.catalog(), x, y);
                ok = t != null;
                if (t != null) {
                    var c = com.vjstb.ledscheme.service.room.RoomTowerCalc.of(t, session.catalog());
                    pw = c.footW();
                    pd = c.footD();
                    x = t.getXCm();
                    y = t.getYCm();
                } else {
                    pw = 48;
                    pd = 30;
                }
            }
            case LOOSE -> {
                RoomRunMath.Nearest n = RoomRunMath.snapToRuns(l.getRuns(), x, y);
                view.snapHintRun = n != null ? n.runId() : null;
                RoomTower t = RoomEditor.towerAt(l, session.catalog(), x, y);
                ok = true;
                if (t != null) {
                    var c = com.vjstb.ledscheme.service.room.RoomTowerCalc.of(t, session.catalog());
                    pw = c.footW();
                    pd = c.footD();
                    x = t.getXCm();
                    y = t.getYCm();
                }
            }
            case CASE, FURNITURE, DECK, MONITOR -> {
                double[] q = RoomEditor.snapPosition(spec.type() == RoomItemSpec.Type.DECK, spec.widthCm(),
                        spec.depthCm(), x, y);
                x = q[0];
                y = q[1];
            }
            default -> {
            }
        }
        setPreview(new PlanView.Preview(x, y, pw, pd, ok));
        return RoomDnd.Feedback.OK;
    }

    @Override
    public void drop(RoomDnd.Info info, Point p) {
        ControlRoomLayout l = session.layout();
        view.preview = null;
        view.snapHintRun = null;
        if (l == null || info.source() != RoomDnd.Source.LIBRARY) {
            repaint();
            return;
        }
        RoomPoint w = view.toWorld(p.x, p.y);
        if (w.getX() < -40 || w.getX() > l.getRoomWidthCm() + 40 || w.getY() < -40 || w.getY() > l.getRoomDepthCm() + 40) {
            repaint();
            return;
        }
        RoomItemSpec spec = session.catalog().resolve(info.ref());
        session.editSelecting(() -> RoomEditor.dropOnPlan(l, session.catalog(), spec, w.getX(), w.getY()).selection());
    }

    @Override
    public void dragLeft() {
        view.snapHintRun = null;
        setPreview(null);
    }

    /** Подсветка места под переносимым; перерисовывает план, только если она действительно изменилась. */
    private void setPreview(PlanView.Preview p) {
        if (!java.util.Objects.equals(p, view.preview)) {
            view.preview = p;
            repaint();
        }
    }
}
