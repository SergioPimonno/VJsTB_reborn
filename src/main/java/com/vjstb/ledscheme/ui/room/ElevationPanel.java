package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomPoint;
import com.vjstb.ledscheme.service.room.RoomEditor;
import com.vjstb.ledscheme.service.room.RoomItemSpec;
import com.vjstb.ledscheme.service.room.RoomSelection;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Вид спереди с жестами (запрос 2026-10-23: «на виде спереди хочу иметь возможность двигать объекты тоже»).
 * Объект тянется вбок — меняется положение по плану; вверх на стол, станок или башню — встаёт сверху; вниз — на
 * пол или под стол. Станок, брошенный сверху на станки пола, становится столом. Глубина (расстояние от сцены)
 * здесь меняется только при постановке на опору. Принимает мебель, станки и мониторы из библиотеки.
 */
final class ElevationPanel extends JPanel implements RoomDnd.Target, PlannerSession.Listener {

    private static final double DRAG_THRESHOLD_PX = 3;

    private final PlannerSession session;
    private final ElevationView view;
    private ElevationView.Pick grabbed;
    private double grabDx;
    private double grabDh;
    private Point pressScreen;
    private boolean dragging;

    ElevationPanel(PlannerSession session) {
        this.session = session;
        this.view = new ElevationView(session);
        setFocusable(true);
        setPreferredSize(new Dimension(640, 360));
        session.addListener(this);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                press(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                drag(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                release();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        bind("DELETE", this::removeSelected);
        bind("BACK_SPACE", this::removeSelected);
        bind("ESCAPE", () -> session.select(null));
        bind("control Z", session::undo);
        bind("control Y", session::redo);
    }

    private void bind(String ks, Runnable action) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(ks), "elev-" + ks);
        getActionMap().put("elev-" + ks, new AbstractAction() {
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
        view.refreshTheme();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        view.paint((Graphics2D) g, getWidth(), getHeight());
    }

    private void press(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e) || session.layout() == null) {
            return;
        }
        RoomPoint p = view.toElevation(e.getX(), e.getY());
        ElevationView.Pick pick = view.pickAt(p.getX(), p.getY());
        grabbed = pick;
        dragging = false;
        pressScreen = e.getPoint();
        if (pick == null) {
            session.select(null);
            return;
        }
        session.select(pick.tower() ? RoomSelection.tower(pick.id()) : RoomSelection.furniture(pick.id()));
        grabDx = pick.x() - p.getX();
        grabDh = pick.baseHeight() - p.getY();
    }

    private void drag(MouseEvent e) {
        ControlRoomLayout layout = session.layout();
        if (grabbed == null || layout == null) {
            return;
        }
        if (!dragging) {
            if (e.getPoint().distance(pressScreen) < DRAG_THRESHOLD_PX) {
                return;
            }
            dragging = true;
            session.beginLive();
        }
        RoomPoint p = view.toElevation(e.getX(), e.getY());
        RoomSelection.Type kind = grabbed.tower() ? RoomSelection.Type.TOWER : RoomSelection.Type.FURN;
        RoomEditor.ElevationSnap snap = RoomEditor.elevationSnap(layout, session.catalog(), kind, grabbed.id(),
                p.getX() + grabDx, p.getY() + grabDh);
        RoomEditor.applyElevation(layout, kind, grabbed.id(), snap);
        session.live();
    }

    private void release() {
        boolean was = dragging;
        dragging = false;
        grabbed = null;
        if (was) {
            session.commitLive();
        }
    }

    // ---------------------------------------------------------------- приём из библиотеки

    @Override
    public RoomDnd.Feedback hover(RoomDnd.Info info, Point p) {
        if (info.source() != RoomDnd.Source.LIBRARY) {
            return RoomDnd.Feedback.NONE;
        }
        RoomItemSpec s = session.catalog().resolve(info.ref());
        return allowed(s) ? RoomDnd.Feedback.OK : RoomDnd.Feedback.NONE;
    }

    private static boolean allowed(RoomItemSpec s) {
        return s.type() == RoomItemSpec.Type.FURNITURE || s.type() == RoomItemSpec.Type.DECK
                || s.type() == RoomItemSpec.Type.MONITOR;
    }

    @Override
    public void drop(RoomDnd.Info info, Point p) {
        ControlRoomLayout l = session.layout();
        if (l == null || info.source() != RoomDnd.Source.LIBRARY) {
            return;
        }
        RoomItemSpec s = session.catalog().resolve(info.ref());
        if (!allowed(s)) {
            return;
        }
        RoomPoint w = view.toElevation(p.x, p.y);
        session.editSelecting(() -> {
            RoomEditor.Added a = RoomEditor.dropOnElevation(l, session.catalog(), s, w.getX(), w.getY());
            return a == null ? null : a.selection();
        });
    }

    @Override
    public void dragLeft() {
        // подсветки у вида спереди нет
    }
}
