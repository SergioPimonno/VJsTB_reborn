package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomLooseItem;
import com.vjstb.ledscheme.model.RoomPoint;
import com.vjstb.ledscheme.model.RoomRun;
import com.vjstb.ledscheme.model.RoomTower;
import com.vjstb.ledscheme.service.room.RoomBounds;
import com.vjstb.ledscheme.service.room.RoomEditor;
import com.vjstb.ledscheme.service.room.RoomGeometry;
import com.vjstb.ledscheme.service.room.RoomItemSpec;
import com.vjstb.ledscheme.service.room.RoomRunKind;
import com.vjstb.ledscheme.service.room.RoomRunMath;
import com.vjstb.ledscheme.service.room.RoomSelection;
import com.vjstb.ledscheme.service.room.RoomUnits;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Вид сверху: план площадки (порт {@code planInner()} прототипа). Класс только рисует и ищет объект под
 * указателем — события мыши обрабатывает {@link PlanPanel}. Координаты мира — сантиметры; экран получается
 * подгонкой области (площадка плюс поля под размеры и сцену) в размер компонента, поэтому план всегда виден
 * целиком.
 */
final class PlanView {

    /** Режим плана: объекты, правка трасс, рисование новой трассы. */
    enum Mode { OBJECTS, RUNS, DRAW }

    /** Что найдено под указателем. */
    record Hit(Kind kind, String id, int index) {
        enum Kind { NONE, TOWER, FURN, LOOSE, RUN, VERTEX, MID }

        static final Hit NONE = new Hit(Kind.NONE, null, -1);
    }

    /** Подсветка места, куда ляжет перетаскиваемое из библиотеки. */
    record Preview(double x, double y, double w, double d, boolean ok) {
    }

    private static final double VIEW_X0 = -45;
    private static final double VIEW_Y0 = -52;
    private static final double VIEW_PAD_W = 90;
    private static final double VIEW_PAD_D = 110;
    private static final double RING_R = 14;
    private static final double RUN_HIT = 9;
    private static final double HANDLE_R = 7;

    private final PlannerSession session;
    private RoomTheme theme = RoomTheme.current();
    Mode mode = Mode.OBJECTS;
    RoomRunKind drawKind = RoomRunKind.SDI;
    List<RoomPoint> drawPoints = new ArrayList<>();
    RoomPoint drawHover;
    Preview preview;
    String snapHintRun;

    private double scale = 1;
    private double offX;
    private double offY;

    PlanView(PlannerSession session) {
        this.session = session;
    }

    void refreshTheme() {
        theme = RoomTheme.current();
    }

    // ---------------------------------------------------------------- преобразование

    void fit(int w, int h) {
        ControlRoomLayout l = session.layout();
        double ww = (l != null ? l.getRoomWidthCm() : 700) + VIEW_PAD_W;
        double dd = (l != null ? l.getRoomDepthCm() : 450) + VIEW_PAD_D;
        scale = Math.max(0.05, Math.min(w / ww, h / dd));
        offX = (w - ww * scale) / 2 - VIEW_X0 * scale;
        offY = (h - dd * scale) / 2 - VIEW_Y0 * scale;
    }

    RoomPoint toWorld(double sx, double sy) {
        return new RoomPoint((sx - offX) / scale, (sy - offY) / scale);
    }

    double scale() {
        return scale;
    }

    /** Толщина линии в мировых единицах, дающая заданное число экранных пикселей. */
    private float px(double pixels) {
        return (float) (pixels / scale);
    }

    // ---------------------------------------------------------------- рисование

    void paint(Graphics2D g0, int w, int h) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(theme.panel);
            g.fillRect(0, 0, w, h);
            ControlRoomLayout layout = session.layout();
            if (layout == null || session.calc() == null) {
                return;
            }
            fit(w, h);
            g.translate(offX, offY);
            g.scale(scale, scale);
            paintWorld(g, layout);
        } finally {
            g.dispose();
        }
    }

    private void paintWorld(Graphics2D g, ControlRoomLayout layout) {
        double rw = layout.getRoomWidthCm();
        double rd = layout.getRoomDepthCm();
        Font base = g.getFont();
        // пол и сетка
        g.setColor(theme.floor);
        g.fill(new Rectangle2D.Double(0, 0, rw, rd));
        g.setStroke(new BasicStroke(px(1)));
        g.setColor(theme.grid);
        for (double x = 0; x <= rw + .1; x += 50) {
            g.setStroke(new BasicStroke(x % 100 == 0 ? px(1.6) : px(0.8)));
            g.draw(new Line2D.Double(x, 0, x, rd));
        }
        for (double y = 0; y <= rd + .1; y += 50) {
            g.setStroke(new BasicStroke(y % 100 == 0 ? px(1.6) : px(0.8)));
            g.draw(new Line2D.Double(0, y, rw, y));
        }
        // сцена
        g.setColor(theme.stage);
        g.fill(new Rectangle2D.Double(0, -40, rw, 40));
        g.setColor(theme.stageInk);
        centered(g, base, "СЦЕНА", rw / 2, -14, 15, true);

        List<RoomBounds> all = session.calc().bounds();
        // станки: на полу, затем станки-столы
        for (int pass = 0; pass < 2; pass++) {
            for (RoomBounds b : all) {
                if (b.deck && (pass == 1) == b.onTop) {
                    paintDeck(g, base, b);
                }
            }
        }
        paintRuns(g, base, layout);
        g.setColor(theme.wall);
        g.setStroke(new BasicStroke(px(2.5)));
        g.draw(new Rectangle2D.Double(0, 0, rw, rd));
        for (RoomBounds b : all) {
            if (b.type == RoomBounds.Type.FURNITURE && !b.deck && !b.monitor) {
                paintFurniture(g, base, b);
            }
        }
        for (RoomBounds b : all) {
            if (b.isTower()) {
                paintTower(g, base, b, all);
            }
        }
        for (RoomBounds b : all) {
            if (b.monitor) {
                paintMonitor(g, base, b);
            }
        }
        int n = 0;
        for (RoomLooseItem l : layout.getLoose()) {
            if (l.getMode() != RoomLooseItem.Mode.BOX) {
                paintLoose(g, base, layout, l, ++n);
            }
        }
        paintDrawing(g, layout);
        if (preview != null) {
            g.setColor(preview.ok ? withAlpha(theme.accent, 70) : withAlpha(theme.err, 70));
            Shape r = new RoundRectangle2D.Double(preview.x - preview.w / 2, preview.y - preview.d / 2, preview.w,
                    preview.d, 6, 6);
            g.fill(r);
            g.setStroke(new BasicStroke(px(1.5), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {px(5), px(4)}, 0));
            g.setColor(preview.ok ? theme.accent : theme.err);
            g.draw(r);
        }
        // размеры площадки и масштабная линейка
        g.setColor(theme.muted);
        centered(g, base, RoomUnits.num(rw, 0) + " см", rw / 2, rd + 28, 13, false);
        AffineTransform keep = g.getTransform();
        g.translate(-16, rd / 2);
        g.rotate(-Math.PI / 2);
        centered(g, base, RoomUnits.num(rd, 0) + " см", 0, 0, 13, false);
        g.setTransform(keep);
        g.setColor(theme.ink);
        g.setStroke(new BasicStroke(px(2)));
        g.draw(new Line2D.Double(0, rd + 42, 100, rd + 42));
        g.draw(new Line2D.Double(0, rd + 37, 0, rd + 47));
        g.draw(new Line2D.Double(100, rd + 37, 100, rd + 47));
        g.setFont(base.deriveFont(13f));
        g.drawString("1 м", 110, (float) (rd + 46));
    }

    private void paintDeck(Graphics2D g, Font base, RoomBounds b) {
        Rectangle2D r = new Rectangle2D.Double(b.x0, b.y0, b.x1 - b.x0, b.y1 - b.y0);
        g.setColor(b.onTop ? theme.tableDeck : theme.deck);
        g.fill(r);
        g.setColor(b.onTop ? theme.tableDeckLine : theme.deckLine);
        g.setStroke(new BasicStroke(px(1.5)));
        g.draw(r);
        g.setColor(theme.ink);
        String t = RoomUnits.num((b.x1 - b.x0) / 100, 1) + "×" + RoomUnits.num((b.y1 - b.y0) / 100, 1) + " · "
                + RoomUnits.num(b.height, 0) + (b.onTop ? " ↑" : "");
        centered(g, base, t, b.cx(), b.cy(), 11, false);
        outlineState(g, b, r);
    }

    private void paintFurniture(Graphics2D g, Font base, RoomBounds b) {
        double w = b.x1 - b.x0;
        double d = b.y1 - b.y0;
        boolean chair = b.chair;
        Shape r = new RoundRectangle2D.Double(b.x0, b.y0, w, d, chair ? 40 : 6, chair ? 40 : 6);
        g.setColor(theme.furn);
        g.fill(r);
        g.setColor(theme.deckLine);
        g.setStroke(new BasicStroke(px(1.2)));
        g.draw(r);
        if (!chair) {
            g.setColor(theme.ink);
            centered(g, base, b.name, b.cx(), b.cy(), 11, false);
        }
        outlineState(g, b, r);
    }

    private void paintTower(Graphics2D g, Font base, RoomBounds b, List<RoomBounds> all) {
        RoomTower t = b.tower;
        double cw = b.calc.widthCm();
        double cd = b.calc.depthCm();
        AffineTransform keep = g.getTransform();
        g.translate(t.getXCm(), t.getYCm());
        g.rotate(Math.toRadians(t.getRotation()));
        Shape body = new RoundRectangle2D.Double(-cw / 2, -cd / 2, cw, cd, 8, 8);
        g.setColor(theme.caseBody);
        g.fill(body);
        g.setColor(theme.accent);
        g.setStroke(new BasicStroke(px(2.5)));
        g.draw(new Line2D.Double(-cw / 2 + 4, cd / 2 - 1, cw / 2 - 4, cd / 2 - 1));
        g.setTransform(keep);
        g.setColor(theme.caseInk);
        centered(g, base, t.getTag(), t.getXCm(), t.getYCm() + 1, 20, true);
        g.setColor(theme.caseInk);
        centered(g, base, RoomUnits.num(b.calc.footW(), 0) + "×" + RoomUnits.num(b.calc.footD(), 0), t.getXCm(),
                t.getYCm() + 19, 10, false);
        // рамка состояния по повёрнутому контуру
        Shape foot = new Rectangle2D.Double(b.x0, b.y0, b.x1 - b.x0, b.y1 - b.y0);
        RoomBounds host = RoomGeometry.hostOf(b, all);
        boolean under = RoomGeometry.layMode(b, host) == RoomGeometry.EffLay.UNDER;
        if (under) {
            g.setColor(theme.muted);
            g.setStroke(new BasicStroke(px(1.5), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                    new float[] {px(5), px(4)}, 0));
            g.draw(foot);
        }
        outlineState(g, b, foot);
    }

    private void paintMonitor(Graphics2D g, Font base, RoomBounds b) {
        RoomFurniture f = b.furniture;
        double w = b.spec.widthCm();
        double d = b.spec.depthCm();
        AffineTransform keep = g.getTransform();
        g.translate(f.getXCm(), f.getYCm());
        g.rotate(Math.toRadians(f.getRotation()));
        g.setColor(theme.caseBody);
        g.fill(new RoundRectangle2D.Double(-w / 2, -d / 2, w, d, 4, 4));
        g.setColor(theme.accent);
        g.setStroke(new BasicStroke(px(2)));
        g.draw(new Line2D.Double(-w / 2 + 2, d / 2, w / 2 - 2, d / 2));
        g.setTransform(keep);
        if (b.spec.diagonalIn() != null) {
            g.setColor(theme.caseInk);
            centered(g, base, RoomUnits.num(b.spec.diagonalIn(), 1) + "″", f.getXCm(), f.getYCm(), 10, false);
        }
        outlineState(g, b, new Rectangle2D.Double(b.x0, b.y0, b.x1 - b.x0, b.y1 - b.y0));
    }

    private void paintLoose(Graphics2D g, Font base, ControlRoomLayout layout, RoomLooseItem l, int number) {
        RoomItemSpec s = session.catalog().resolve(l.getRef());
        RoomPoint p = RoomEditor.locationOf(layout, l);
        boolean sel = isSelected(RoomSelection.Type.LOOSE, l.getId());
        double w = Math.max(s.widthCm(), 4);
        double d = Math.max(s.depthCm(), 4);
        g.setColor(withAlpha(sel ? theme.accent : theme.muted, 40));
        g.fill(new Ellipse2D.Double(p.getX() - RING_R, p.getY() - RING_R, RING_R * 2, RING_R * 2));
        g.setColor(sel ? theme.accent : theme.muted);
        g.setStroke(new BasicStroke(px(sel ? 2.5 : 1.2)));
        g.draw(new Ellipse2D.Double(p.getX() - RING_R, p.getY() - RING_R, RING_R * 2, RING_R * 2));
        g.setColor(theme.categoryColor(s));
        g.fill(new Rectangle2D.Double(p.getX() - w / 2, p.getY() - d / 2, w, d));
        g.setColor(theme.ink);
        g.setStroke(new BasicStroke(px(0.8)));
        g.draw(new Rectangle2D.Double(p.getX() - w / 2, p.getY() - d / 2, w, d));
        g.setColor(theme.accent);
        g.fill(new Ellipse2D.Double(p.getX() + 12 - 7.5, p.getY() - 12 - 7.5, 15, 15));
        g.setColor(theme.onAccent);
        centered(g, base, String.valueOf(number), p.getX() + 12, p.getY() - 12, 10, true);
        if (!s.complete()) {
            g.setColor(theme.warn);
            g.setStroke(new BasicStroke(px(1.5)));
            g.draw(new Ellipse2D.Double(p.getX() - RING_R - 2, p.getY() - RING_R - 2, RING_R * 2 + 4, RING_R * 2 + 4));
        }
    }

    private void paintRuns(Graphics2D g, Font base, ControlRoomLayout layout) {
        for (RoomRun r : layout.getRuns()) {
            List<RoomPoint> pts = r.getPoints();
            if (pts.size() < 2) {
                continue;
            }
            RoomRunKind kind = RoomRunKind.of(r.getKind());
            Color col = theme.runColor(kind);
            boolean sel = isSelected(RoomSelection.Type.RUN, r.getId());
            boolean hot = r.getId().equals(snapHintRun);
            Path2D path = new Path2D.Double();
            path.moveTo(pts.get(0).getX(), pts.get(0).getY());
            for (int i = 1; i < pts.size(); i++) {
                path.lineTo(pts.get(i).getX(), pts.get(i).getY());
            }
            if (sel || hot) {
                g.setColor(withAlpha(col, 70));
                g.setStroke(new BasicStroke(px(sel ? 11 : 9), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(path);
            }
            g.setColor(col);
            g.setStroke(new BasicStroke(px(sel ? 5 : 3.5), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path);
            RoomPoint e = pts.get(pts.size() - 1);
            g.fill(new RoundRectangle2D.Double(e.getX() - 6, e.getY() - 6, 12, 12, 3, 3));
            RoomRunMath.LabelPos lp = RoomRunMath.labelPos(r);
            AffineTransform keep = g.getTransform();
            g.translate(lp.x(), lp.y());
            g.rotate(Math.toRadians(lp.angleDeg()));
            g.setColor(col);
            centered(g, base, r.getName(), 0, -8, 11, true);
            g.setTransform(keep);
            if (sel && mode == Mode.RUNS) {
                for (int i = 0; i < pts.size() - 1; i++) {
                    double mx = (pts.get(i).getX() + pts.get(i + 1).getX()) / 2;
                    double my = (pts.get(i).getY() + pts.get(i + 1).getY()) / 2;
                    g.setColor(theme.panel);
                    g.fill(new Ellipse2D.Double(mx - HANDLE_R, my - HANDLE_R, HANDLE_R * 2, HANDLE_R * 2));
                    g.setColor(theme.accent);
                    g.setStroke(new BasicStroke(px(1.5)));
                    g.draw(new Ellipse2D.Double(mx - HANDLE_R, my - HANDLE_R, HANDLE_R * 2, HANDLE_R * 2));
                    g.draw(new Line2D.Double(mx - 3.5, my, mx + 3.5, my));
                    g.draw(new Line2D.Double(mx, my - 3.5, mx, my + 3.5));
                }
                for (RoomPoint q : pts) {
                    g.setColor(Color.WHITE);
                    g.fill(new Ellipse2D.Double(q.getX() - HANDLE_R, q.getY() - HANDLE_R, HANDLE_R * 2, HANDLE_R * 2));
                    g.setColor(theme.accent);
                    g.setStroke(new BasicStroke(px(2)));
                    g.draw(new Ellipse2D.Double(q.getX() - HANDLE_R, q.getY() - HANDLE_R, HANDLE_R * 2, HANDLE_R * 2));
                }
            }
        }
    }

    private void paintDrawing(Graphics2D g, ControlRoomLayout layout) {
        if (mode != Mode.DRAW) {
            return;
        }
        Color col = theme.runColor(drawKind);
        List<RoomPoint> pts = new ArrayList<>(drawPoints);
        if (drawHover != null) {
            pts.add(drawHover);
        }
        if (pts.size() > 1) {
            Path2D path = new Path2D.Double();
            path.moveTo(pts.get(0).getX(), pts.get(0).getY());
            for (int i = 1; i < pts.size(); i++) {
                path.lineTo(pts.get(i).getX(), pts.get(i).getY());
            }
            g.setColor(col);
            g.setStroke(new BasicStroke(px(3), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10,
                    new float[] {px(8), px(6)}, 0));
            g.draw(path);
        }
        g.setColor(col);
        for (RoomPoint p : drawPoints) {
            g.fill(new Ellipse2D.Double(p.getX() - 5, p.getY() - 5, 10, 10));
        }
        if (drawHover != null) {
            g.setColor(theme.accent);
            g.setStroke(new BasicStroke(px(2)));
            g.draw(new Ellipse2D.Double(drawHover.getX() - 8, drawHover.getY() - 8, 16, 16));
        }
    }

    /** Рамка состояния: выбран, ошибка пересечения, висит в воздухе. */
    private void outlineState(Graphics2D g, RoomBounds b, Shape shape) {
        boolean sel = (b.isTower() && isTowerSelected(b.id))
                || (!b.isTower() && isSelected(RoomSelection.Type.FURN, b.id));
        boolean bad = session.calc().bad().contains(b.id);
        boolean air = session.calc().air().contains(b.id);
        if (air) {
            g.setColor(theme.warn);
            g.setStroke(new BasicStroke(px(2), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                    new float[] {px(6), px(4)}, 0));
            g.draw(shape);
        }
        if (bad) {
            g.setColor(theme.err);
            g.setStroke(new BasicStroke(px(2.5)));
            g.draw(shape);
        }
        if (sel) {
            g.setColor(withAlpha(theme.accent, 45));
            g.fill(shape);
            g.setColor(theme.accent);
            g.setStroke(new BasicStroke(px(3)));
            g.draw(shape);
        }
    }

    private boolean isSelected(RoomSelection.Type type, String id) {
        RoomSelection s = session.selection();
        return s != null && s.type() == type && s.id().equals(id);
    }

    private boolean isTowerSelected(String towerId) {
        RoomSelection s = session.selection();
        return s != null && (s.type() == RoomSelection.Type.TOWER || s.type() == RoomSelection.Type.RITEM
                || s.type() == RoomSelection.Type.SITEM) && towerId.equals(s.towerId());
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    private static void centered(Graphics2D g, Font base, String text, double cx, double cy, double size, boolean bold) {
        g.setFont(base.deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size));
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) (cy + fm.getAscent() / 2.0 - fm.getDescent() / 2.0));
    }

    // ---------------------------------------------------------------- что под указателем

    /** Объект под точкой мира; сверху вниз: мелочь, мониторы, башни, мебель, станки, трассы. */
    Hit hit(double x, double y) {
        ControlRoomLayout layout = session.layout();
        if (layout == null || session.calc() == null) {
            return Hit.NONE;
        }
        if (mode == Mode.DRAW) {
            return Hit.NONE;
        }
        if (mode == Mode.RUNS) {
            return hitRun(layout, x, y, true);
        }
        List<RoomLooseItem> loose = layout.getLoose();
        for (int i = loose.size() - 1; i >= 0; i--) {
            RoomLooseItem l = loose.get(i);
            if (l.getMode() == RoomLooseItem.Mode.BOX) {
                continue;
            }
            RoomPoint p = RoomEditor.locationOf(layout, l);
            if (Math.hypot(x - p.getX(), y - p.getY()) <= RING_R) {
                return new Hit(Hit.Kind.LOOSE, l.getId(), -1);
            }
        }
        List<RoomBounds> all = session.calc().bounds();
        for (int i = all.size() - 1; i >= 0; i--) {
            RoomBounds b = all.get(i);
            if (b.monitor && inside(b, x, y)) {
                return new Hit(Hit.Kind.FURN, b.id, -1);
            }
        }
        for (int i = all.size() - 1; i >= 0; i--) {
            RoomBounds b = all.get(i);
            if (b.isTower() && insideTower(b, x, y)) {
                return new Hit(Hit.Kind.TOWER, b.id, -1);
            }
        }
        for (int i = all.size() - 1; i >= 0; i--) {
            RoomBounds b = all.get(i);
            if (b.type == RoomBounds.Type.FURNITURE && !b.deck && !b.monitor && inside(b, x, y)) {
                return new Hit(Hit.Kind.FURN, b.id, -1);
            }
        }
        for (int pass = 1; pass >= 0; pass--) {
            for (int i = all.size() - 1; i >= 0; i--) {
                RoomBounds b = all.get(i);
                if (b.deck && b.onTop == (pass == 1) && inside(b, x, y)) {
                    return new Hit(Hit.Kind.FURN, b.id, -1);
                }
            }
        }
        return hitRun(layout, x, y, false);
    }

    private Hit hitRun(ControlRoomLayout layout, double x, double y, boolean handles) {
        RoomSelection sel = session.selection();
        if (handles && sel != null && sel.type() == RoomSelection.Type.RUN) {
            RoomRun r = RoomEditor.run(layout, sel.id());
            if (r != null) {
                List<RoomPoint> p = r.getPoints();
                for (int i = 0; i < p.size(); i++) {
                    if (Math.hypot(x - p.get(i).getX(), y - p.get(i).getY()) <= HANDLE_R + 3) {
                        return new Hit(Hit.Kind.VERTEX, r.getId(), i);
                    }
                }
                for (int i = 0; i < p.size() - 1; i++) {
                    double mx = (p.get(i).getX() + p.get(i + 1).getX()) / 2;
                    double my = (p.get(i).getY() + p.get(i + 1).getY()) / 2;
                    if (Math.hypot(x - mx, y - my) <= HANDLE_R + 3) {
                        return new Hit(Hit.Kind.MID, r.getId(), i);
                    }
                }
            }
        }
        RoomRun best = null;
        double bestD = RUN_HIT;
        for (RoomRun r : layout.getRuns()) {
            RoomRunMath.Nearest n = RoomRunMath.nearest(r, x, y);
            if (n != null && n.distance() <= bestD) {
                bestD = n.distance();
                best = r;
            }
        }
        return best != null ? new Hit(Hit.Kind.RUN, best.getId(), -1) : Hit.NONE;
    }

    private static boolean inside(RoomBounds b, double x, double y) {
        return x >= b.x0 && x <= b.x1 && y >= b.y0 && y <= b.y1;
    }

    private static boolean insideTower(RoomBounds b, double x, double y) {
        RoomTower t = b.tower;
        double a = Math.toRadians(-t.getRotation());
        double dx = x - t.getXCm();
        double dy = y - t.getYCm();
        double lx = dx * Math.cos(a) - dy * Math.sin(a);
        double ly = dx * Math.sin(a) + dy * Math.cos(a);
        return Math.abs(lx) <= b.calc.widthCm() / 2 && Math.abs(ly) <= b.calc.depthCm() / 2;
    }
}
