package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.service.ProjectorCalc.ImageSize;
import com.vjstb.ledscheme.service.ProjectorCalc.LensFit;
import com.vjstb.ledscheme.service.ProjectorCalc.LensFitStatus;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/**
 * Схематичный вид проекции сбоку (пол — экран справа — проектор слева на throw-
 * дистанции, со смещением объектива по вертикали) — тот же стиль пунктирных
 * направляющих с подписями, что и {@code CanvasEditorPanel.drawSnapGuides}/
 * {@code SchemaCanvasPanel} ("как в yEd"). Чисто иллюстративный вид (без точной
 * оптической модели хода луча объектива) — цель дать наглядное ощущение геометрии
 * throw-дистанции и сдвига, а не точный чертёж.
 */
public class ProjectorGeometryVisualizer extends JPanel {

    private static final int PADDING = 40;
    private static final Color GOOD = new Color(0x3f, 0xb9, 0x50);

    private ImageSize size;
    private double throwDistanceM;
    private double verticalOffsetM;
    private double verticalOffsetPercent;
    private LensFit fit;

    public ProjectorGeometryVisualizer() {
        setBackground(Palette.BG);
        setPreferredSize(new Dimension(560, 260));
    }

    public void update(ImageSize size, double throwDistanceM, double verticalOffsetM, double verticalOffsetPercent,
            LensFit fit) {
        this.size = size;
        this.throwDistanceM = throwDistanceM;
        this.verticalOffsetM = verticalOffsetM;
        this.verticalOffsetPercent = verticalOffsetPercent;
        this.fit = fit;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (size == null || size.widthM() <= 0 || size.heightM() <= 0 || throwDistanceM <= 0) {
            g2.setColor(Palette.MUTED);
            g2.setFont(getFont().deriveFont(13f));
            g2.drawString("Введите параметры для расчёта.", PADDING, PADDING + 16);
            g2.dispose();
            return;
        }

        int availableW = Math.max(1, getWidth() - 2 * PADDING);
        int availableH = Math.max(1, getHeight() - 2 * PADDING - 30);
        double offsetAbsM = Math.abs(verticalOffsetM);
        double scale = Math.min(availableW / (throwDistanceM * 1.2), availableH / (size.heightM() + offsetAbsM * 2));

        int floorY = PADDING + availableH;
        g2.setColor(Palette.BORDER);
        g2.drawLine(PADDING, floorY, getWidth() - PADDING, floorY);

        int screenHeightPx = (int) Math.round(size.heightM() * scale);
        int screenX = getWidth() - PADDING - 10;
        int screenBottomY = floorY;
        int screenTopY = screenBottomY - screenHeightPx;
        int screenCenterY = (screenTopY + screenBottomY) / 2;

        g2.setColor(Palette.PANEL);
        g2.fillRect(screenX - 6, screenTopY, 6, screenHeightPx);
        g2.setColor(Palette.ACCENT);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRect(screenX - 6, screenTopY, 6, screenHeightPx);

        int projectorX = (int) Math.round(screenX - throwDistanceM * scale);
        int projectorY = screenCenterY + (int) Math.round(verticalOffsetM * scale);

        boolean fits = fit == null || fit.status() == LensFitStatus.FITS;
        Color lineColor = fits ? GOOD : Palette.WARN;

        g2.setColor(lineColor);
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0, new float[]{4, 4}, 0));
        g2.drawLine(projectorX, projectorY, screenX - 6, projectorY);

        g2.setColor(Palette.MUTED);
        g2.drawLine(screenX + 12, projectorY, screenX + 12, screenCenterY);

        g2.setColor(Palette.PANEL);
        g2.fillRect(projectorX - 14, projectorY - 9, 28, 18);
        g2.setColor(lineColor);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRect(projectorX - 14, projectorY - 9, 28, 18);

        Font labelFont = getFont().deriveFont(11f);
        g2.setFont(labelFont);
        g2.setColor(Palette.TEXT);
        String distLabel = String.format("%.2f м", throwDistanceM);
        int distLabelW = g2.getFontMetrics().stringWidth(distLabel);
        g2.drawString(distLabel, (projectorX + screenX - 6) / 2 - distLabelW / 2, projectorY - 6);

        if (Math.abs(verticalOffsetM) > 1e-6) {
            String offsetLabel = String.format("сдвиг %.2f м (%.0f%%)", verticalOffsetM, verticalOffsetPercent);
            g2.setColor(Palette.MUTED);
            g2.drawString(offsetLabel, screenX + 18, (projectorY + screenCenterY) / 2);
        }

        g2.setColor(Palette.MUTED);
        g2.setFont(getFont().deriveFont(10f));
        g2.drawString("Проектор", projectorX - 14, projectorY + 24);
        g2.drawString(String.format("Экран %.2f×%.2f м", size.widthM(), size.heightM()), screenX - 90, floorY + 16);

        g2.dispose();
    }
}
