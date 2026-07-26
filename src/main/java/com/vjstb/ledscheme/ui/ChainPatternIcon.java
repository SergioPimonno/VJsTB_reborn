package com.vjstb.ledscheme.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import javax.swing.Icon;

/**
 * Маленькая пиктограмма шаблона серпантина для радиального меню «Быстрое
 * подключение» — как в NovaLCT: сетка точек + ломаная линия обхода + маркер
 * стартового угла, вместо неинформативного текста "Слева-сверху, по строкам".
 * Сетка условная (не связана с реальным размером выделенной области) — важен
 * только сам маршрут (откуда стартует и как разворачивается змейка).
 */
public final class ChainPatternIcon implements Icon {

    private static final int ROWS = 3;
    private static final int COLS = 4;
    private static final int W = 40;
    private static final int H = 30;
    private static final int MARGIN = 5;
    private static final int DOT_R = 2;

    private final List<int[]> cells;

    public ChainPatternIcon(ChainPatterns.Pattern pattern) {
        this.cells = ChainPatterns.orderedCells(ROWS, COLS, pattern);
    }

    @Override
    public int getIconWidth() {
        return W;
    }

    @Override
    public int getIconHeight() {
        return H;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        if (cells.isEmpty()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(x, y);

        double stepX = (W - 2.0 * MARGIN) / (COLS - 1);
        double stepY = (H - 2.0 * MARGIN) / (ROWS - 1);

        int[] px = new int[cells.size()];
        int[] py = new int[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            px[i] = (int) Math.round(MARGIN + cells.get(i)[1] * stepX);
            py[i] = (int) Math.round(MARGIN + cells.get(i)[0] * stepY);
        }

        // тусклые точки всех ячеек сетки
        g2.setColor(Palette.MUTED);
        for (int i = 0; i < px.length; i++) {
            g2.fillOval(px[i] - DOT_R, py[i] - DOT_R, DOT_R * 2, DOT_R * 2);
        }

        // маршрут — ломаная линия через ячейки в порядке обхода
        g2.setColor(Palette.ACCENT);
        g2.setStroke(new java.awt.BasicStroke(1.6f));
        for (int i = 0; i < px.length - 1; i++) {
            g2.drawLine(px[i], py[i], px[i + 1], py[i + 1]);
        }

        // стрелка на последнем отрезке — направление движения
        if (px.length >= 2) {
            int n = px.length;
            drawArrowHead(g2, px[n - 2], py[n - 2], px[n - 1], py[n - 1]);
        }

        // старт — заметный маркер поверх точки
        g2.setColor(Color.WHITE);
        g2.fillOval(px[0] - DOT_R - 1, py[0] - DOT_R - 1, (DOT_R + 1) * 2, (DOT_R + 1) * 2);
        g2.setColor(Palette.ACCENT);
        g2.drawOval(px[0] - DOT_R - 1, py[0] - DOT_R - 1, (DOT_R + 1) * 2, (DOT_R + 1) * 2);

        g2.dispose();
    }

    private static void drawArrowHead(Graphics2D g2, int ax, int ay, int bx, int by) {
        double angle = Math.atan2(by - ay, bx - ax);
        double len = 5;
        double spread = Math.toRadians(28);
        int x1 = (int) Math.round(bx - len * Math.cos(angle - spread));
        int y1 = (int) Math.round(by - len * Math.sin(angle - spread));
        int x2 = (int) Math.round(bx - len * Math.cos(angle + spread));
        int y2 = (int) Math.round(by - len * Math.sin(angle + spread));
        g2.drawLine(bx, by, x1, y1);
        g2.drawLine(bx, by, x2, y2);
    }
}
