package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomLooseItem;
import com.vjstb.ledscheme.model.RoomPoint;
import com.vjstb.ledscheme.model.RoomRackItem;
import com.vjstb.ledscheme.model.RoomSegment;
import com.vjstb.ledscheme.model.RoomTower;
import com.vjstb.ledscheme.service.room.RoomBounds;
import com.vjstb.ledscheme.service.room.RoomEditor;
import com.vjstb.ledscheme.service.room.RoomGeometry;
import com.vjstb.ledscheme.service.room.RoomItemSpec;
import com.vjstb.ledscheme.service.room.RoomSelection;
import com.vjstb.ledscheme.service.room.RoomUnits;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Вид спереди: «смотрим от операторов в сторону сцены» (порт {@code elevationHTML()} прототипа). По горизонтали —
 * x площадки, по вертикали — высота над землёй; всё, что стоит на станках, поднято на высоту станка. Рисует линии
 * глаз сидящего оператора (120 см от пола, на котором стоит стул — запрос 2026-10-23), станки-столы сверху на
 * станках, башни на столе и под ним, мониторы на опорах и мелочь на столешнице. Только рисует и ищет объект под
 * указателем; жесты — в {@link ElevationPanel}.
 */
final class ElevationView {

    /** Что можно схватить на виде: башня или мебель/станок/монитор и высота низа объекта над землёй. */
    record Pick(boolean tower, String id, double x, double baseHeight, double x0, double x1, double top) {
    }

    private static final double VIEW_X0 = -45;
    private static final double VIEW_Y0 = -285;
    private static final double VIEW_H = 325;
    private static final double VIEW_PAD_W = 90;

    private final PlannerSession session;
    private RoomTheme theme = RoomTheme.current();
    private double scale = 1;
    private double offX;
    private double offY;
    private final List<Pick> picks = new ArrayList<>();

    ElevationView(PlannerSession session) {
        this.session = session;
    }

    void refreshTheme() {
        theme = RoomTheme.current();
    }

    private void fit(int w, int h) {
        ControlRoomLayout l = session.layout();
        double ww = (l != null ? l.getRoomWidthCm() : 700) + VIEW_PAD_W;
        scale = Math.max(0.05, Math.min(w / ww, h / VIEW_H));
        offX = (w - ww * scale) / 2 - VIEW_X0 * scale;
        offY = (h - VIEW_H * scale) / 2 - VIEW_Y0 * scale;
    }

    /** Точка экрана в координатах вида: x и высота над землёй, см. */
    RoomPoint toElevation(double sx, double sy) {
        return new RoomPoint((sx - offX) / scale, -((sy - offY) / scale));
    }

    private float px(double pixels) {
        return (float) (pixels / scale);
    }

    void paint(Graphics2D g0, int w, int h) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    session.isLive() ? RenderingHints.VALUE_ANTIALIAS_OFF : RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(theme.panel);
            g.fillRect(0, 0, w, h);
            ControlRoomLayout layout = session.layout();
            picks.clear();
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
        Font base = g.getFont();
        List<RoomBounds> all = session.calc().bounds();
        List<RoomBounds> decks = RoomGeometry.decks(all);

        // шкала высот
        g.setStroke(new BasicStroke(px(0.8)));
        for (int hh = 0; hh <= 250; hh += 50) {
            g.setColor(theme.grid);
            g.draw(new Line2D.Double(0, -hh, rw, -hh));
            g.setColor(theme.muted);
            right(g, base, String.valueOf(hh), -6, -hh, 11);
        }
        right(g, base, "см", -6, -266, 11);

        // линии глаз (подпись рисуется поверх всего, ниже)
        List<RoomGeometry.EyeLevel> eyes = RoomGeometry.eyeLevels(all);
        for (RoomGeometry.EyeLevel e : eyes) {
            double xa = 0;
            double xb = rw;
            if (e.xs() != null && eyes.size() > 1) {
                double lo = Double.MAX_VALUE;
                double hi = -Double.MAX_VALUE;
                for (double x : e.xs()) {
                    lo = Math.min(lo, x);
                    hi = Math.max(hi, x);
                }
                xa = Math.max(0, lo - 70);
                xb = Math.min(rw, hi + 70);
            }
            g.setColor(theme.accent);
            g.setStroke(new BasicStroke(px(1.6), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                    new float[] {px(8), px(5)}, 0));
            g.draw(new Line2D.Double(xa, -e.eyeHeightCm(), xb, -e.eyeHeightCm()));
        }
        // земля
        g.setColor(theme.deckLine);
        g.fill(new Rectangle2D.Double(-20, 0, rw + 40, 6));

        // станки: на полу, затем станки-столы
        List<RoomBounds> sortedDecks = new ArrayList<>(decks);
        sortedDecks.sort(Comparator.<RoomBounds>comparingInt(b -> b.onTop ? 1 : 0).thenComparingDouble(b -> b.y0));
        for (RoomBounds d : sortedDecks) {
            paintDeck(g, d);
        }

        // башни и мебель — дальние (меньший y) рисуются раньше
        List<RoomBounds> objs = new ArrayList<>();
        for (RoomBounds b : all) {
            if (b.isTower() || (b.type == RoomBounds.Type.FURNITURE && !b.deck && !b.monitor)) {
                objs.add(b);
            }
        }
        objs.sort(Comparator.comparingDouble(RoomBounds::cy));
        for (RoomBounds b : objs) {
            if (b.isTower()) {
                paintTower(g, base, b, all);
            } else {
                paintFurniture(g, b, decks);
            }
        }
        for (RoomBounds b : all) {
            if (b.monitor) {
                paintMonitor(g, base, b, all, decks);
            }
        }
        for (RoomLooseItem l : layout.getLoose()) {
            if (l.getMode() != RoomLooseItem.Mode.BOX) {
                paintLoose(g, layout, l, all, decks);
            }
        }
        for (RoomGeometry.EyeLevel e : eyes) {
            double xb = rw;
            if (e.xs() != null && eyes.size() > 1) {
                double hi = -Double.MAX_VALUE;
                for (double x : e.xs()) {
                    hi = Math.max(hi, x);
                }
                xb = Math.min(rw, hi + 70);
            }
            String t = "глаза сидя ≈ " + (int) RoomGeometry.EYE_ABOVE_FLOOR_CM + " см от "
                    + (e.deckHeightCm() > 0
                    ? "пола станка (" + RoomUnits.num(e.eyeHeightCm(), 0) + " см от земли)" : "пола");
            g.setFont(base.deriveFont(Font.PLAIN, RoomTheme.fontSize(11f)));
            java.awt.FontMetrics fm = g.getFontMetrics();
            double tw = fm.stringWidth(t) + 8;
            g.setColor(new Color(theme.panel.getRed(), theme.panel.getGreen(), theme.panel.getBlue(), 215));
            g.fill(new RoundRectangle2D.Double(xb - 4 - tw, -e.eyeHeightCm() - 20, tw, 15, 4, 4));
            g.setColor(theme.accent);
            right(g, base, t, xb - 8, -e.eyeHeightCm() - 12.5, 11);
        }
        g.setColor(theme.muted);
        centered(g, base, RoomUnits.num(rw, 0) + " см · смотрим от операторов в сторону сцены", rw / 2, 26, 12);
    }

    private void paintDeck(Graphics2D g, RoomBounds d) {
        double bs = d.base;
        double tp = bs + d.height;
        Color fill = d.onTop ? theme.tableDeck : theme.deck;
        Color line = d.onTop ? theme.tableDeckLine : theme.deckLine;
        double w = d.x1 - d.x0;
        List<Shape> parts = new ArrayList<>();
        if (d.height > 60) {
            parts.add(new Rectangle2D.Double(d.x0, -tp, w, 8));
            parts.add(new Rectangle2D.Double(d.x0, -tp + 8, 6, d.height - 8));
            parts.add(new Rectangle2D.Double(d.x1 - 6, -tp + 8, 6, d.height - 8));
        } else {
            parts.add(new Rectangle2D.Double(d.x0, -tp, w, d.height));
        }
        for (Shape s : parts) {
            g.setColor(fill);
            g.fill(s);
            g.setColor(line);
            g.setStroke(new BasicStroke(px(1.2)));
            g.draw(s);
        }
        if (isSelected(RoomSelection.Type.FURN, d.id)) {
            g.setColor(theme.accent);
            g.setStroke(new BasicStroke(px(3)));
            g.draw(new Rectangle2D.Double(d.x0, -tp, w, d.height));
        }
        if (session.calc().air().contains(d.id)) {
            g.setColor(theme.warn);
            g.setStroke(new BasicStroke(px(2), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {px(6), px(4)}, 0));
            g.draw(new Rectangle2D.Double(d.x0, -tp, w, d.height));
        }
        picks.add(new Pick(false, d.id, d.cx(), bs, d.x0, d.x1, tp));
    }

    private void paintTower(Graphics2D g, Font base, RoomBounds b, List<RoomBounds> all) {
        RoomTower t = b.tower;
        double dh = RoomGeometry.towerBase(b, all);
        double fw = b.calc.footW();
        boolean front = t.getRotation() % 180 == 0;
        double x = t.getXCm();
        double yBase = -dh;
        double h0 = 0;
        for (RoomSegment seg : t.getSegments()) {
            if (seg.isStack()) {
                for (RoomRackItem it : seg.getItems()) {
                    RoomItemSpec s = session.catalog().resolve(it.getRef());
                    g.setColor(theme.face);
                    Shape r = new Rectangle2D.Double(x - s.widthCm() / 2, yBase - h0 - s.heightCm(), s.widthCm(), s.heightCm());
                    g.fill(r);
                    g.setColor(theme.hole);
                    g.setStroke(new BasicStroke(px(0.8)));
                    g.draw(r);
                    h0 += s.heightCm();
                }
                continue;
            }
            RoomItemSpec k = session.catalog().resolve(seg.getCaseRef());
            g.setColor(theme.caseBody);
            g.fill(new RoundRectangle2D.Double(x - fw / 2, yBase - h0 - k.heightCm(), fw, k.heightCm(), 4, 4));
            if (front) {
                for (RoomRackItem it : seg.getItems()) {
                    RoomItemSpec s = session.catalog().resolve(it.getRef());
                    double ix = x - RoomUnits.RACK_WIDTH_CM / 2 + it.getC0() / 12.0 * RoomUnits.RACK_WIDTH_CM;
                    double iy = yBase - (h0 + k.heightCm() - 8 - it.getU0() * RoomUnits.U_CM);
                    g.setColor(theme.face);
                    g.fill(new Rectangle2D.Double(ix, iy, s.widthTwelfths() / 12.0 * RoomUnits.RACK_WIDTH_CM,
                            s.rackUnits() * RoomUnits.U_CM - .5));
                }
            }
            g.setColor(theme.muted);
            h0 += k.heightCm();
        }
        g.setColor(theme.ink);
        centered(g, base, t.getTag() + " · " + RoomUnitsFormat.height(session.units(), h0, b.calc.unitsTotal()), x,
                yBase - h0 - 8, 12);
        Rectangle2D box = new Rectangle2D.Double(x - fw / 2, yBase - h0, fw, h0);
        if (isTowerSelected(t.getId())) {
            g.setColor(theme.accent);
            g.setStroke(new BasicStroke(px(3)));
            g.draw(box);
        }
        if (session.calc().air().contains(b.id)) {
            g.setColor(theme.warn);
            g.setStroke(new BasicStroke(px(2), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {px(6), px(4)}, 0));
            g.draw(box);
        }
        picks.add(new Pick(true, t.getId(), x, dh, x - fw / 2, x + fw / 2, dh + h0));
    }

    private void paintFurniture(Graphics2D g, RoomBounds b, List<RoomBounds> decks) {
        double dh = Math.max(0, RoomGeometry.deckHeightAt(decks, b.furniture.getXCm(), b.furniture.getYCm()));
        double w = b.x1 - b.x0;
        double x = b.furniture.getXCm();
        double h = b.height;
        g.setColor(theme.furn);
        g.setStroke(new BasicStroke(px(1)));
        if (b.chair) {
            fill(g, new RoundRectangle2D.Double(x - w / 2, -dh - 95, w, 50, 16, 16));
            fill(g, new RoundRectangle2D.Double(x - w / 2, -dh - 47, w, 8, 6, 6));
            fill(g, new Rectangle2D.Double(x - 3, -dh - 39, 6, 39));
        } else if (b.spec != null && b.spec.role() == RoomItemSpec.Role.TABLE) {
            fill(g, new Rectangle2D.Double(x - w / 2, -dh - h, w, 5));
            fill(g, new Rectangle2D.Double(x - w / 2 + 4, -dh - h + 5, 4, h - 5));
            fill(g, new Rectangle2D.Double(x + w / 2 - 8, -dh - h + 5, 4, h - 5));
        } else {
            fill(g, new RoundRectangle2D.Double(x - w / 2, -dh - h, w, h, 4, 4));
        }
        Rectangle2D box = new Rectangle2D.Double(x - w / 2, -dh - (b.chair ? 95 : h), w, b.chair ? 95 : h);
        if (isSelected(RoomSelection.Type.FURN, b.id)) {
            g.setColor(theme.accent);
            g.setStroke(new BasicStroke(px(3)));
            g.draw(box);
        }
        if (session.calc().air().contains(b.id)) {
            g.setColor(theme.warn);
            g.setStroke(new BasicStroke(px(2), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {px(6), px(4)}, 0));
            g.draw(box);
        }
        picks.add(new Pick(false, b.id, x, dh, x - w / 2, x + w / 2, dh + (b.chair ? 95 : h)));
    }

    private void paintMonitor(Graphics2D g, Font base, RoomBounds b, List<RoomBounds> all, List<RoomBounds> decks) {
        double fx = b.furniture.getXCm();
        double fy = b.furniture.getYCm();
        double baseH = Math.max(0, RoomGeometry.deckHeightAt(decks, fx, fy)) + RoomGeometry.supportAt(all, fx, fy);
        double w = b.x1 - b.x0;
        double hh = b.height;
        g.setColor(theme.caseBody);
        g.fill(new RoundRectangle2D.Double(fx - w / 2, -baseH - hh, w, hh * 0.72, 4, 4));
        g.setColor(theme.furn);
        g.fill(new Rectangle2D.Double(fx - 3, -baseH - hh * 0.28, 6, hh * 0.28));
        g.fill(new Rectangle2D.Double(fx - 14, -baseH - 2, 28, 2));
        if (b.spec.diagonalIn() != null) {
            g.setColor(theme.caseInk);
            centered(g, base, RoomUnits.num(b.spec.diagonalIn(), 1) + "″", fx, -baseH - hh + hh * 0.36, 11);
        }
        Rectangle2D box = new Rectangle2D.Double(fx - w / 2, -baseH - hh, w, hh);
        if (isSelected(RoomSelection.Type.FURN, b.id)) {
            g.setColor(theme.accent);
            g.setStroke(new BasicStroke(px(3)));
            g.draw(box);
        }
        if (session.calc().air().contains(b.id)) {
            g.setColor(theme.warn);
            g.setStroke(new BasicStroke(px(2), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {px(6), px(4)}, 0));
            g.draw(box);
        }
        picks.add(new Pick(false, b.id, fx, baseH, fx - w / 2, fx + w / 2, baseH + hh));
    }

    private void paintLoose(Graphics2D g, ControlRoomLayout layout, RoomLooseItem l, List<RoomBounds> all,
                            List<RoomBounds> decks) {
        RoomItemSpec s = session.catalog().resolve(l.getRef());
        RoomPoint p = RoomEditor.locationOf(layout, l);
        double base = Math.max(0, RoomGeometry.deckHeightAt(decks, p.getX(), p.getY()));
        for (RoomBounds b : all) {
            if (b.isTableLike() && b.spec != null && b.spec.role() == RoomItemSpec.Role.TABLE
                    && p.getX() >= b.x0 && p.getX() <= b.x1 && p.getY() >= b.y0 && p.getY() <= b.y1) {
                base = Math.max(0, RoomGeometry.deckHeightAt(decks, b.cx(), b.cy())) + b.height;
            }
        }
        double hh = Math.max(s.heightCm(), 3);
        g.setColor(theme.categoryColor(s));
        g.fill(new Rectangle2D.Double(p.getX() - s.widthCm() / 2, -(base + hh), s.widthCm(), hh));
    }

    private void fill(Graphics2D g, Shape s) {
        g.setColor(theme.furn);
        g.fill(s);
        g.setColor(theme.deckLine);
        g.draw(s);
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

    private static void centered(Graphics2D g, Font base, String text, double cx, double cy, double size) {
        g.setFont(RoomFonts.derive(base, Font.PLAIN, (float) size));
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) (cy + fm.getAscent() / 2.0 - fm.getDescent() / 2.0));
    }

    private static void right(Graphics2D g, Font base, String text, double x, double cy, double size) {
        g.setFont(RoomFonts.derive(base, Font.PLAIN, (float) size));
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (x - fm.stringWidth(text)), (float) (cy + fm.getAscent() / 2.0 - fm.getDescent() / 2.0));
    }

    private static void left(Graphics2D g, Font base, String text, double x, double cy, double size) {
        g.setFont(RoomFonts.derive(base, Font.PLAIN, (float) size));
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) x, (float) (cy + fm.getAscent() / 2.0 - fm.getDescent() / 2.0));
    }

    /** Объект под точкой вида (x, высота); ближние рисуются позже, поэтому проверяем с конца. */
    Pick pickAt(double x, double height) {
        for (int i = picks.size() - 1; i >= 0; i--) {
            Pick p = picks.get(i);
            if (x >= p.x0() && x <= p.x1() && height >= p.baseHeight() - 2 && height <= p.top() + 2) {
                return p;
            }
        }
        return null;
    }
}
