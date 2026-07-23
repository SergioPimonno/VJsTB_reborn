package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.ScreenStats;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JPanel;

/**
 * Раскладка всех экранов текущей сцены по их координатам (мм) на одном холсте —
 * обзор взаимного расположения, аналог общего плана сцены.
 */
public class SceneCanvasPanel extends JPanel {

    private static final int PADDING = 40;

    private final AppModel model;

    public SceneCanvasPanel(AppModel model) {
        this.model = model;
        setBackground(Palette.BG);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Screen s = screenAt(e.getX(), e.getY());
                if (s != null) {
                    model.selectScreen(s);
                }
            }
        });
    }

    private double[] boundsMm() {
        Scene scene = model.getCurrentScene();
        if (scene == null) {
            return null;
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean any = false;
        for (Screen s : scene.getScreens()) {
            CabinetType t = model.typeOf(s);
            if (t == null) {
                continue;
            }
            double w = s.getCols() * t.getWidthMm();
            double h = s.getRows() * t.getHeightMm();
            minX = Math.min(minX, s.getPosXMm());
            minY = Math.min(minY, s.getPosYMm());
            maxX = Math.max(maxX, s.getPosXMm() + w);
            maxY = Math.max(maxY, s.getPosYMm() + h);
            any = true;
        }
        return any ? new double[]{minX, minY, maxX, maxY} : null;
    }

    private Screen screenAt(int px, int py) {
        Scene scene = model.getCurrentScene();
        double[] b = boundsMm();
        if (scene == null || b == null) {
            return null;
        }
        double sc = boundsToScale(b, getWidth(), getHeight());
        for (Screen s : scene.getScreens()) {
            CabinetType t = model.typeOf(s);
            if (t == null) {
                continue;
            }
            int x = PADDING + (int) Math.round((s.getPosXMm() - b[0]) * sc);
            int y = PADDING + (int) Math.round((s.getPosYMm() - b[1]) * sc);
            int w = (int) Math.round(s.getCols() * t.getWidthMm() * sc);
            int h = (int) Math.round(s.getRows() * t.getHeightMm() * sc);
            if (px >= x && px <= x + w && py >= y && py <= y + h) {
                return s;
            }
        }
        return null;
    }

    /** Рендерит текущий вид в изображение заданного размера (для экспорта). */
    public BufferedImage renderImage(int width, int height) {
        BufferedImage img = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        paint(g2, width, height);
        g2.dispose();
        return img;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        paint((Graphics2D) g.create(), getWidth(), getHeight());
    }

    private void paint(Graphics2D g2, int width, int height) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Palette.BG);
        g2.fillRect(0, 0, width, height);

        Scene scene = model.getCurrentScene();
        double[] b = boundsMm();
        if (scene == null || b == null) {
            g2.setColor(Palette.MUTED);
            g2.setFont(getFont().deriveFont(14f));
            String msg = scene == null ? "Выберите сцену, чтобы увидеть раскладку." : "На сцене нет экранов.";
            int tw = g2.getFontMetrics().stringWidth(msg);
            g2.drawString(msg, Math.max(20, (width - tw) / 2), height / 2);
            g2.dispose();
            return;
        }

        double sc = boundsToScale(b, width, height);
        Set<Screen> overlapping = overlappingScreens(scene);
        for (Screen s : scene.getScreens()) {
            CabinetType t = model.typeOf(s);
            if (t == null) {
                continue;
            }
            double wMm = s.getCols() * t.getWidthMm();
            double hMm = s.getRows() * t.getHeightMm();
            int x = PADDING + (int) Math.round((s.getPosXMm() - b[0]) * sc);
            int y = PADDING + (int) Math.round((s.getPosYMm() - b[1]) * sc);
            int w = Math.max(2, (int) Math.round(wMm * sc));
            int h = Math.max(2, (int) Math.round(hMm * sc));

            boolean current = s == model.getCurrentScreen();
            boolean overlapped = overlapping.contains(s);
            g2.setColor(overlapped ? blendRed(Palette.PHASE_NONE) : Palette.PHASE_NONE);
            g2.fillRect(x, y, w, h);
            g2.setColor(overlapped ? Color.RED : (current ? Palette.ACCENT : Palette.BORDER));
            g2.setStroke(overlapped
                    ? new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0, new float[]{6, 4}, 0)
                    : new BasicStroke(current ? 2.5f : 1.4f));
            g2.drawRect(x, y, w, h);

            ScreenStats st = ScreenLogic.stats(s, t, model.getWorkspace());
            g2.setColor(overlapped ? Color.RED : (current ? Palette.ACCENT : new Color(0xc0, 0xc8, 0xd0)));
            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            g2.drawString(s.getName(), x + 4, y + 16);
            g2.setColor(Palette.MUTED);
            g2.setFont(getFont().deriveFont(Font.PLAIN, 10f));
            g2.drawString(st.resolutionWidthPx() + "×" + st.resolutionHeightPx() + " px", x + 4, y + 30);
            g2.drawString(Math.round(wMm) + "×" + Math.round(hMm) + " мм", x + 4, y + 43);
        }

        if (!overlapping.isEmpty()) {
            g2.setColor(Color.RED);
            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            g2.drawString("⚠ Экраны перекрываются — проверьте позиции (X/Y) или нажмите «Расставить без наложения»",
                    PADDING, height - 10);
        }
        g2.dispose();
    }

    /** Экраны, чьи прямоугольники (мм) пересекаются хотя бы с одним другим экраном сцены. */
    private Set<Screen> overlappingScreens(Scene scene) {
        Set<Screen> result = new HashSet<>();
        List<Screen> screens = scene.getScreens();
        for (int i = 0; i < screens.size(); i++) {
            Screen a = screens.get(i);
            CabinetType ta = model.typeOf(a);
            if (ta == null) {
                continue;
            }
            double aw = a.getCols() * ta.getWidthMm();
            double ah = a.getRows() * ta.getHeightMm();
            for (int j = i + 1; j < screens.size(); j++) {
                Screen b = screens.get(j);
                CabinetType tb = model.typeOf(b);
                if (tb == null) {
                    continue;
                }
                double bw = b.getCols() * tb.getWidthMm();
                double bh = b.getRows() * tb.getHeightMm();
                boolean disjoint = a.getPosXMm() + aw <= b.getPosXMm()
                        || b.getPosXMm() + bw <= a.getPosXMm()
                        || a.getPosYMm() + ah <= b.getPosYMm()
                        || b.getPosYMm() + bh <= a.getPosYMm();
                if (!disjoint) {
                    result.add(a);
                    result.add(b);
                }
            }
        }
        return result;
    }

    private static Color blendRed(Color base) {
        return new Color(
                Math.round(base.getRed() * 0.6f + 255 * 0.4f),
                Math.round(base.getGreen() * 0.6f),
                Math.round(base.getBlue() * 0.6f));
    }

    private double boundsToScale(double[] b, int width, int height) {
        double boundW = Math.max(1, b[2] - b[0]);
        double boundH = Math.max(1, b[3] - b[1]);
        double availW = Math.max(50, width - PADDING * 2);
        double availH = Math.max(50, height - PADDING * 2);
        return Math.max(0.0005, Math.min(availW / boundW, availH / boundH));
    }
}
