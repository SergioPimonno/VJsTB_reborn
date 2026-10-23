package com.vjstb.ledscheme.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;

/**
 * Значок загрузки силового блока общей схемы в его правом верхнем углу: круговой
 * индикатор, пока перегрузки нет, и предупреждающий треугольник при перегрузке.
 *
 * <p>Раньше перегрузку показывал текстовый глиф «⚠» цвета {@link Palette#WARN} — тот же
 * оранжевый, что и заливка силовых блоков, поэтому значок сливался с блоком и был виден
 * лишь по подсказке (баг-репорт со скриншотом, 2026-10-17); глиф ещё и зависит от
 * шрифта. Теперь значки — векторные фигуры с собственным контрастом (тёмная подложка у
 * кольца, тёмная обводка у треугольника), не зависящие ни от заливки блока, ни от пресета
 * оформления схемы, ни от шрифта.
 */
final class LoadBadge {

    /** Радиус подложки кольца и половина стороны хит-зоны, модельные пиксели. */
    private static final int DISC_RADIUS = 11;
    private static final int HIT_HALF = 13;
    private static final int TRIANGLE_SIDE = 22;

    private static final Color DISC = new Color(13, 17, 23, 224);
    private static final Color TRACK = new Color(255, 255, 255, 56);
    private static final Color OK = new Color(0x3f, 0xb9, 0x50);
    private static final Color HIGH = new Color(0xe3, 0xb3, 0x41);
    private static final Color CRITICAL = new Color(0xff, 0x5a, 0x36);
    private static final Color WARN_FILL = new Color(0xff, 0xd4, 0x00);
    private static final Color WARN_INK = new Color(0x1b, 0x1f, 0x23);

    private LoadBadge() {
    }

    /** Цвет дуги по загрузке (1.0 = 100%): до 70% — зелёный, 70–90% — жёлтый, дальше —
     *  оранжево-красный. */
    static Color ringColor(double fraction) {
        if (fraction < 0.70) {
            return OK;
        }
        return fraction < 0.90 ? HIGH : CRITICAL;
    }

    /** Центр значка для узла: отступ от правого верхнего угла рамки. */
    static int centerX(int nodeX, int nodeW) {
        return nodeX + nodeW - 16;
    }

    static int centerY(int nodeY) {
        return nodeY + 16;
    }

    /** Область наведения мыши (для подсказки) вокруг значка с центром ({@code cx},
     *  {@code cy}) — одна и та же для кольца и треугольника. */
    static Rectangle hitRect(int cx, int cy) {
        return new Rectangle(cx - HIT_HALF, cy - HIT_HALF, HIT_HALF * 2, HIT_HALF * 2);
    }

    /** Круговой индикатор: тёмная подложка, бледная дорожка и цветная дуга от верха по
     *  часовой стрелке. {@code fraction} — доля загрузки, ограничивается 0..1 (значение
     *  выше 1 — это перегрузка, её рисует {@link #paintWarning}). */
    static void paintRing(Graphics2D g2, int cx, int cy, double fraction) {
        double f = Math.max(0, Math.min(1, fraction));
        java.awt.Stroke oldStroke = g2.getStroke();
        g2.setColor(DISC);
        g2.fill(new Ellipse2D.Double(cx - DISC_RADIUS, cy - DISC_RADIUS, DISC_RADIUS * 2, DISC_RADIUS * 2));
        double r = 7.5;
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g2.setColor(TRACK);
        g2.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        if (f > 0) {
            g2.setColor(ringColor(f));
            g2.draw(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, 90, -360 * f, Arc2D.OPEN));
        }
        g2.setStroke(oldStroke);
    }

    /** Предупреждающий треугольник перегрузки: жёлтая заливка, тёмная обводка и «!». */
    static void paintWarning(Graphics2D g2, int cx, int cy) {
        java.awt.Stroke oldStroke = g2.getStroke();
        int half = TRIANGLE_SIDE / 2;
        int h = (int) Math.round(TRIANGLE_SIDE * 0.9);
        int top = cy - h / 2;
        int bottom = cy + h / 2;
        Polygon tri = new Polygon(new int[]{cx, cx + half, cx - half}, new int[]{top, bottom, bottom}, 3);
        g2.setColor(WARN_FILL);
        g2.fillPolygon(tri);
        g2.setColor(WARN_INK);
        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawPolygon(tri);
        g2.fillRect(cx - 1, top + h / 3, 2, Math.max(3, h / 4));
        g2.fill(new Ellipse2D.Double(cx - 1.3, bottom - 5.2, 2.6, 2.6));
        g2.setStroke(oldStroke);
    }
}
