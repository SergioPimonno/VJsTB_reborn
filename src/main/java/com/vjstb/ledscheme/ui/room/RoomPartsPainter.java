package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.service.room.RoomPart;
import com.vjstb.ledscheme.service.room.RoomShape;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Рисует позицию из блоков («Разбить на блоки») в координатах Планера: на плане — отпечаток каждого блока, на виде
 * спереди — блоки по высоте, дальние раньше. Один код для плана, вида спереди и предпросмотра в окне блоков, чтобы
 * картинка в редакторе совпадала с тем, что потом увидит план.
 */
public final class RoomPartsPainter {

    private RoomPartsPainter() {
    }

    /**
     * План: блоки относительно центра позиции {@code (cx, cy)}, позиция повёрнута на {@code rotation}°. Нижние блоки
     * рисуются раньше верхних, верхние чуть светлее — перекрытие читается. {@code selected} — номер подсвеченного блока
     * (или -1). Толщина линий — {@code line} в единицах плана.
     */
    public static void plan(Graphics2D g, List<RoomPart> parts, double cx, double cy, int rotation, Color fill,
                            Color stroke, Color accent, float line, int selected) {
        if (parts.isEmpty()) {
            return;
        }
        double top = Math.max(1, RoomShape.bbox(parts)[2]);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble(i -> parts.get(i).zCm() + parts.get(i).hCm()));
        AffineTransform keep = g.getTransform();
        g.translate(cx, cy);
        g.rotate(Math.toRadians(rotation));
        for (int i : order) {
            RoomPart p = parts.get(i);
            double lift = Math.min(1, (p.zCm() + p.hCm()) / top);
            Rectangle2D r = new Rectangle2D.Double(p.xCm() - p.wCm() / 2, p.yCm() - p.dCm() / 2, p.wCm(), p.dCm());
            g.setColor(lighter(fill, lift * 0.35));
            g.fill(r);
            g.setColor(i == selected ? accent : stroke);
            g.setStroke(new BasicStroke(i == selected ? line * 2.2f : line));
            g.draw(r);
        }
        double[] bb = RoomShape.bbox(parts);
        g.setColor(accent);
        g.setStroke(new BasicStroke(line * 1.6f));
        g.draw(new java.awt.geom.Line2D.Double(-bb[0] / 2 + 2, bb[1] / 2, bb[0] / 2 - 2, bb[1] / 2));
        g.setTransform(keep);
    }

    /**
     * Вид спереди: блоки позиции с центром на {@code cx}, низ позиции на высоте {@code baseCm} над землёй (ось y вверх —
     * отрицательная, как на виде спереди). Возвращает прямоугольники блоков {x0, x1, низ, верх} для выбора мышью.
     */
    public static List<double[]> front(Graphics2D g, List<RoomPart> parts, double cx, double baseCm, int rotation,
                                       Color fill, Color stroke, Color accent, float line, int selected) {
        List<double[]> picks = new ArrayList<>();
        if (parts.isEmpty()) {
            return picks;
        }
        List<Integer> order = new ArrayList<>();
        List<double[]> fp = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            order.add(i);
            fp.add(RoomShape.footprint(parts.get(i), rotation));
        }
        // дальние (меньший y) раньше ближних, при равенстве — нижние раньше верхних
        order.sort(Comparator.<Integer>comparingDouble(i -> fp.get(i)[1])
                .thenComparingDouble(i -> parts.get(i).zCm()));
        for (int i : order) {
            RoomPart p = parts.get(i);
            double[] f = fp.get(i);
            double x0 = cx + f[0] - f[2] / 2;
            double bottom = baseCm + p.zCm();
            Rectangle2D r = new Rectangle2D.Double(x0, -(bottom + p.hCm()), f[2], p.hCm());
            g.setColor(fill);
            g.fill(r);
            g.setColor(i == selected ? accent : stroke);
            g.setStroke(new BasicStroke(i == selected ? line * 2.2f : line));
            g.draw(r);
            picks.add(new double[] {x0, x0 + f[2], bottom, bottom + p.hCm()});
        }
        return picks;
    }

    private static Color lighter(Color c, double k) {
        int r = c.getRed() + (int) Math.round((255 - c.getRed()) * k);
        int gr = c.getGreen() + (int) Math.round((255 - c.getGreen()) * k);
        int b = c.getBlue() + (int) Math.round((255 - c.getBlue()) * k);
        return new Color(r, gr, b, c.getAlpha());
    }
}
