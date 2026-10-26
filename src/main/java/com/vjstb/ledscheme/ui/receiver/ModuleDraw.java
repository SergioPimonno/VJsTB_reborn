package com.vjstb.ledscheme.ui.receiver;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Set;

/**
 * Общие приёмы рисования обоих видов: область из клеток (карта или блок питания — не обязательно прямоугольник, после
 * частичной замены по правилу D10/D18 бывают Г-образные), контур по её границе, подпись, умещённая в клетку, плашка.
 * Контур строится по клеткам, а не по описанному прямоугольнику, поэтому не залезает на соседнюю карту.
 */
final class ModuleDraw {

    private ModuleDraw() {
    }

    static long key(int row, int col) {
        return ((long) row << 32) | (col & 0xFFFFFFFFL);
    }

    static int rowOf(long key) {
        return (int) (key >> 32);
    }

    static int colOf(long key) {
        return (int) key;
    }

    static Graphics2D prepare(Graphics2D g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    /** Заливка области: клетки склеены (без зазоров между соседями), с отступом {@code inset} от внешних границ. */
    static void fillRegion(Graphics2D g, ModuleGridGeometry geo, Set<Long> cells, Color fill, double inset) {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO);
        for (long k : cells) {
            int r = rowOf(k);
            int c = colOf(k);
            Rectangle2D rc = geo.cell(r, c);
            double x1 = rc.getX() + (cells.contains(key(r, c - 1)) ? 0 : inset);
            double y1 = rc.getY() + (cells.contains(key(r - 1, c)) ? 0 : inset);
            double x2 = rc.getMaxX() - (cells.contains(key(r, c + 1)) ? 0 : inset);
            double y2 = rc.getMaxY() - (cells.contains(key(r + 1, c)) ? 0 : inset);
            path.append(new Rectangle2D.Double(x1, y1, x2 - x1, y2 - y1), false);
        }
        g.setColor(fill);
        g.fill(path);
    }

    /** Контур области по границе её клеток (внутренние стыки не рисуются). */
    static void outlineRegion(Graphics2D g, ModuleGridGeometry geo, Set<Long> cells, Color color, float width,
                              float[] dash, double inset) {
        Path2D.Double path = new Path2D.Double();
        for (long k : cells) {
            int r = rowOf(k);
            int c = colOf(k);
            Rectangle2D rc = geo.cell(r, c);
            boolean left = cells.contains(key(r, c - 1));
            boolean right = cells.contains(key(r, c + 1));
            boolean up = cells.contains(key(r - 1, c));
            boolean down = cells.contains(key(r + 1, c));
            double xl = rc.getX() + inset;
            double xr = rc.getMaxX() - inset;
            double yt = rc.getY() + inset;
            double yb = rc.getMaxY() - inset;
            if (!up) {
                segment(path, left ? rc.getX() : xl, yt, right ? rc.getMaxX() : xr, yt);
            }
            if (!down) {
                segment(path, left ? rc.getX() : xl, yb, right ? rc.getMaxX() : xr, yb);
            }
            if (!left) {
                segment(path, xl, up ? rc.getY() : yt, xl, down ? rc.getMaxY() : yb);
            }
            if (!right) {
                segment(path, xr, up ? rc.getY() : yt, xr, down ? rc.getMaxY() : yb);
            }
        }
        g.setColor(color);
        g.setStroke(dash != null
                ? new BasicStroke(width, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10f, dash, 0f)
                : new BasicStroke(width, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g.draw(path);
    }

    private static void segment(Path2D.Double p, double x1, double y1, double x2, double y2) {
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
    }

    static Font font(Graphics2D g, double size) {
        return g.getFont().deriveFont(Font.BOLD, (float) size);
    }

    /** Подпись по центру точки; если не умещается в {@code maxWidth}, шрифт уменьшается (но не мельче 6 pt). */
    static void centeredText(Graphics2D g, String s, double cx, double cy, double size, double maxWidth, Color color) {
        if (s == null || s.isEmpty()) {
            return;
        }
        double fs = size;
        Font f = font(g, fs);
        FontMetrics fm = g.getFontMetrics(f);
        while (fm.stringWidth(s) > maxWidth && fs > 6) {
            fs -= 0.5;
            f = font(g, fs);
            fm = g.getFontMetrics(f);
        }
        g.setFont(f);
        g.setColor(color);
        g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
    }

    /** Плашка с короткой подписью («К3», «БП2 +К») по центру точки. */
    static void plate(Graphics2D g, String s, double cx, double cy, double size, Color fill, Color fg) {
        Font f = font(g, size);
        FontMetrics fm = g.getFontMetrics(f);
        double w = fm.stringWidth(s) + size * 0.8;
        double h = size * 1.35;
        g.setColor(fill);
        g.fill(new RoundRectangle2D.Double(cx - w / 2, cy - h / 2, w, h, h, h));
        g.setFont(f);
        g.setColor(fg);
        g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
    }

    static Rectangle2D inset(Rectangle2D r, double d) {
        return new Rectangle2D.Double(r.getX() + d, r.getY() + d, Math.max(0, r.getWidth() - 2 * d),
                Math.max(0, r.getHeight() - 2 * d));
    }
}
