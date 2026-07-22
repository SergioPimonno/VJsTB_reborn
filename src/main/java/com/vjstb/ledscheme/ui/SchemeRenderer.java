package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.ScreenStats;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Отрисовка схемы экрана (сетка кабинетов + цепочки). Используется и на холсте,
 * и при экспорте в изображение. Активная (строящаяся) цепочка здесь не рисуется —
 * это транзиентное состояние редактирования.
 */
public final class SchemeRenderer {

    private SchemeRenderer() {
    }

    /** Размер ячейки кабинета по соотношению сторон типа. */
    public static Dimension cellSize(CabinetType type, int base) {
        double ratio = type != null && type.getHeightMm() > 0 ? type.getWidthMm() / type.getHeightMm() : 1;
        int w = ratio >= 1 ? base : (int) Math.round(base * ratio);
        int h = ratio >= 1 ? (int) Math.round(base / ratio) : base;
        return new Dimension(Math.max(w, 24), Math.max(h, 24));
    }

    /** Рисует кабинеты и сохранённые цепочки указанного режима. */
    public static void paintScheme(Graphics2D g2, Screen scr, CabinetType type, boolean power,
                                   int cellW, int cellH, int offX, int offY) {
        Font labelFont = g2.getFont().deriveFont(Font.PLAIN, Math.max(9f, cellH * 0.14f));
        for (CabinetInstance cab : scr.getCabinets()) {
            int x = offX + cab.getColIndex() * cellW;
            int y = offY + cab.getRowIndex() * cellH;
            Color fill = power ? Palette.phaseColor(cab.getPhase()) : Palette.PHASE_NONE;
            g2.setColor(cab.isHidden() ? blend(fill, Palette.BG, 0.82f) : fill);

           // g2.fillRect(x, y, cellW, cellH);
            g2.setColor(Palette.BORDER);
            g2.drawRect(x, y, cellW, cellH);

            g2.setColor(cab.isHidden() ? Palette.MUTED : new Color(0xc0, 0xc8, 0xd0));
            g2.setFont(labelFont);
            g2.drawString(cab.getDisplayRow() + "," + cab.getDisplayCol(), x + 4, y + labelFont.getSize() + 2);
        }

        if (power) {
            for (PowerChain chain : scr.getPowerChains()) {
                drawChain(g2, scr, chain.getCabinetInstanceIds(), Palette.phaseColor(chain.getPhase()),
                        false, cellW, cellH, offX, offY);
            }
        } else {
            List<SignalChain> chains = scr.getSignalChains();
            for (int i = 0; i < chains.size(); i++) {
                drawChain(g2, scr, chains.get(i).getCabinetInstanceIds(), Palette.signalColor(i),
                        false, cellW, cellH, offX, offY);
            }
        }
    }

    /** Рисует одну цепочку линиями между центрами кабинетов. */
    public static void drawChain(Graphics2D g2, Screen scr, List<String> ids, Color color, boolean dashed,
                                 int cellW, int cellH, int offX, int offY) {
        g2.setStroke(dashed
                ? new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{6, 5}, 0)
                : new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(color);
        for (int i = 0; i < ids.size() - 1; i++) {
            CabinetInstance a = scr.cabinetById(ids.get(i));
            CabinetInstance b = scr.cabinetById(ids.get(i + 1));
            if (a == null || b == null) {
                continue;
            }
            int ax = offX + a.getColIndex() * cellW + cellW / 2;
            int ay = offY + a.getRowIndex() * cellH + cellH / 2;
            int bx = offX + b.getColIndex() * cellW + cellW / 2;
            int by = offY + b.getRowIndex() * cellH + cellH / 2;
            g2.drawLine(ax, ay, bx, by);
        }
        g2.setStroke(new BasicStroke(1f));
    }

    /** Рендерит схему экрана в изображение (с заголовком и характеристиками). */
    public static BufferedImage renderImage(Screen scr, CabinetType type, boolean power, int base) {
        Dimension c = cellSize(type, base);
        int pad = 24;
        int headerH = 52;
        int gridW = scr.getCols() * c.width;
        int gridH = scr.getRows() * c.height;
        int w = gridW + pad * 2;
        int h = gridH + pad * 2 + headerH;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setColor(Palette.BG);
        g2.fillRect(0, 0, w, h);

        // заголовок
        ScreenStats stats = ScreenLogic.stats(scr, type);
        g2.setColor(Palette.TEXT);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 16f));
        String title = "Экран «" + scr.getName() + "» — " + (power ? "Питание" : "Сигнал");
        g2.drawString(title, pad, 24);
        g2.setColor(Palette.MUTED);
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));
        String sub = scr.getCols() + "×" + scr.getRows() + " каб. · "
                + stats.resolutionWidthPx() + "×" + stats.resolutionHeightPx() + " px · "
                + trim(stats.physicalWidthMm()) + "×" + trim(stats.physicalHeightMm()) + " мм · "
                + trim(stats.totalPowerW()) + " Вт · " + trim(stats.totalWeightKg()) + " кг";
        g2.drawString(sub, pad, 42);

        paintScheme(g2, scr, type, power, c.width, c.height, pad, pad + headerH);
        g2.dispose();
        return img;
    }

    /** Сохраняет изображение в JPEG с высоким качеством. */
    public static void writeJpeg(BufferedImage img, File file) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.92f);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private static Color blend(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() * (1 - t) + b.getRed() * t),
                Math.round(a.getGreen() * (1 - t) + b.getGreen() * t),
                Math.round(a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }
}
