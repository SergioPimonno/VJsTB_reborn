package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.VehicleType;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Рендерит схему загрузки ОДНОЙ машины (см. {@code ui.VehicleLoadVisualizerDialog}/
 * {@code VehicleLoadCanvasPanel}) в статичное изображение для пакета документации
 * проекта — по прямому запросу пользователя: «рендер схемы размещения аналогично
 * рендеру схем расключения» (см. {@link SchemeRenderer#renderImage}). Тот же приём
 * {@code dpiScale} (1.0 = 72dpi = экранное качество, множитель поверх — печатное,
 * пользователь настраивает через {@code UserProfile#getDocExportDpi}, обычно
 * запрашивают 300): изображение создаётся размером {@code (логическая ширина ×
 * dpiScale)}, весь рисунок затем равномерно растягивается через {@link
 * Graphics2D#scale} — пропорции остаются теми же, что при 1.0, просто больше
 * пикселей на тот же физический размер при печати. Реальный DPI дописывается в
 * метаданные файла тем же {@link SchemeRenderer#writeJpeg}, этот класс сам не
 * пишет файл, только рисует {@link BufferedImage}.
 *
 * <p>Визуальный стиль (цвета {@link VehicleLoadCanvasPanel#COLOR_CARRIES_CABINETS}/
 * {@link VehicleLoadCanvasPanel#COLOR_OTHER}, штабель-бейдж, вертикальные подписи
 * у "повёрнутых" кофров) — тот же, что у живого интерактивного холста, но это
 * НЕЗАВИСИМАЯ статичная отрисовка (без мыши/выделения/зума) поверх persisted-данных
 * ({@code model.VehicleLoadSection}/{@code VehicleLoadPlacement}, уже
 * разрешённых в реальные {@link CaseType}/{@link VehicleType} вызывающим кодом —
 * см. {@link PlacedCase}), не общий код с самим {@code VehicleLoadCanvasPanel}
 * (та же дублирующая конвенция, что и у {@link RiggingSchemaImageWriter} vs
 * {@code SceneCanvasPanel.drawRiggingPoints}).
 */
public final class VehicleLoadSchemaImageWriter {

    private static final int MARGIN = 20;
    private static final double BASE_LOGICAL_W = 900;
    private static final double BASE_LOGICAL_H = 500;
    private static final double MIN_SCALE = 0.02;

    private VehicleLoadSchemaImageWriter() {
    }

    /** Один размещённый кофр с УЖЕ разрешёнными ссылками (см. class-javadoc) —
     *  вызывающий код резолвит {@code caseTypeId} в {@link CaseType} через
     *  {@code Workspace#caseTypeById} до вызова {@link #render}. */
    public record PlacedCase(CaseType type, double xMm, double yMm, boolean rotated, int stackCount, String note) {
    }

    public static BufferedImage render(VehicleType vehicle, List<PlacedCase> placements, String title,
                                        double dpiScale) {
        double scale = Math.max(MIN_SCALE,
                Math.min(BASE_LOGICAL_W / Math.max(1, vehicle.getCargoLengthMm()),
                        BASE_LOGICAL_H / Math.max(1, vehicle.getCargoWidthMm())));
        int cw = (int) Math.round(vehicle.getCargoLengthMm() * scale);
        int ch = (int) Math.round(vehicle.getCargoWidthMm() * scale);

        int titleY = MARGIN + 16;
        int cargoY = titleY + 14;
        int logicalW = cw + MARGIN * 2;
        int logicalH = cargoY + ch + MARGIN + 18;

        BufferedImage img = new BufferedImage(Math.max(1, (int) Math.round(logicalW * dpiScale)),
                Math.max(1, (int) Math.round(logicalH * dpiScale)), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.scale(dpiScale, dpiScale);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Palette.BG);
        g2.fillRect(0, 0, logicalW, logicalH);

        g2.setColor(Palette.TEXT);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 15f));
        g2.drawString((title != null && !title.isBlank() ? title + " — " : "") + vehicle.getName(), MARGIN, titleY);

        g2.setColor(Palette.PANEL);
        g2.fillRect(MARGIN, cargoY, cw, ch);
        g2.setColor(Palette.ACCENT);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRect(MARGIN, cargoY, cw, ch);

        Font labelFont = g2.getFont().deriveFont(Font.BOLD, 11f);
        Font noteFont = g2.getFont().deriveFont(Font.ITALIC, 10f);
        for (PlacedCase p : placements) {
            double footW = p.rotated() ? p.type().getWidthMm() : p.type().getLengthMm();
            double footH = p.rotated() ? p.type().getLengthMm() : p.type().getWidthMm();
            int x = MARGIN + (int) Math.round(p.xMm() * scale);
            int y = cargoY + (int) Math.round(p.yMm() * scale);
            int w = Math.max(2, (int) Math.round(footW * scale));
            int h = Math.max(2, (int) Math.round(footH * scale));

            g2.setColor(p.type().isCarriesCabinets()
                    ? VehicleLoadCanvasPanel.COLOR_CARRIES_CABINETS : VehicleLoadCanvasPanel.COLOR_OTHER);
            g2.fillRect(x, y, w, h);
            g2.setColor(Palette.BORDER);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRect(x, y, w, h);

            drawLabel(g2, labelFont, noteFont, p, x, y, w, h);

            if (p.stackCount() > 1) {
                drawStackBadge(g2, x, y, w, h, p.stackCount());
            }
        }

        g2.setColor(Palette.MUTED);
        g2.setFont(g2.getFont().deriveFont(10f));
        g2.drawString("Кузов " + (int) vehicle.getCargoLengthMm() + "×" + (int) vehicle.getCargoWidthMm() + " мм",
                MARGIN, cargoY + ch + 16);

        g2.dispose();
        return img;
    }

    /** Та же логика "подпись следует повороту кофра" (имя+примечание вертикально
     *  вдвоём при {@code p.rotated}, с фит-проверкой как страховкой), что и в
     *  {@link VehicleLoadCanvasPanel#paintComponent} — намеренное дублирование
     *  (статичная отрисовка не переиспользует Swing-панель, см. class-javadoc). */
    private static void drawLabel(Graphics2D g2, Font labelFont, Font noteFont, PlacedCase p,
                                   int x, int y, int w, int h) {
        g2.setFont(labelFont);
        FontMetrics nameFm = g2.getFontMetrics();
        String name = p.type().getName();
        boolean nameFitsH = nameFm.stringWidth(name) < w - 4 && h > 14;
        boolean nameFitsV = nameFm.stringWidth(name) < h - 4 && w > 14;
        boolean nameVertical = p.rotated() ? (nameFitsV || !nameFitsH) : (!nameFitsH && nameFitsV);
        boolean nameDrawn = nameVertical ? nameFitsV : nameFitsH;
        if (nameDrawn) {
            g2.setColor(Color.WHITE);
            if (nameVertical) {
                drawVertical(g2, name, x + nameFm.getAscent() + 2, y + h - 3);
            } else {
                g2.drawString(name, x + 3, y + nameFm.getAscent() + 1);
            }
        }

        String note = p.note();
        if (note == null || note.isBlank()) {
            return;
        }
        g2.setFont(noteFont);
        FontMetrics noteFm = g2.getFontMetrics();
        boolean noteFitsH = noteFm.stringWidth(note) < w - 4 && h > (nameDrawn && !nameVertical ? 28 : 14);
        boolean noteFitsV = noteFm.stringWidth(note) < h - 4 && w > 14;
        boolean noteVertical = p.rotated() ? (noteFitsV || !noteFitsH) : (!noteFitsH && noteFitsV);
        boolean noteDrawn = noteVertical ? noteFitsV : noteFitsH;
        if (!noteDrawn) {
            return;
        }
        g2.setColor(new Color(0xffd866));
        if (noteVertical) {
            int noteColumnX = x + noteFm.getAscent() + 2 + (nameDrawn && nameVertical ? nameFm.getHeight() + 2 : 0);
            drawVertical(g2, note, noteColumnX, y + h - 3);
        } else {
            int lineY = nameDrawn && !nameVertical ? y + nameFm.getAscent() + 1 + noteFm.getAscent() + 2
                    : y + noteFm.getAscent() + 1;
            g2.drawString(note, x + 3, lineY);
        }
    }

    private static void drawVertical(Graphics2D g2, String text, int baseX, int baseY) {
        Graphics2D gv = (Graphics2D) g2.create();
        gv.translate(baseX, baseY);
        gv.rotate(-Math.PI / 2);
        gv.drawString(text, 0, 0);
        gv.dispose();
    }

    private static void drawStackBadge(Graphics2D g2, int x, int y, int w, int h, int stackCount) {
        String text = "×" + stackCount;
        float fontSize = (float) Math.max(9, Math.min(13, Math.min(w, h) * 0.4));
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, fontSize));
        FontMetrics fm = g2.getFontMetrics();
        int textW = fm.stringWidth(text);
        int textAscent = fm.getAscent();
        int padX = 4;
        int padY = 2;
        int bw = textW + padX * 2;
        int bh = textAscent + padY * 2;
        int bx = Math.min(x + w - bw - 1, x + w - 1);
        int by = Math.min(y + h - bh - 1, y + h - 1);
        if (bw > w) {
            bx = x;
        }
        if (bh > h) {
            by = y;
        }
        g2.setColor(new Color(0, 0, 0, 220));
        g2.fillRoundRect(bx, by, bw, bh, 6, 6);
        g2.setColor(Color.WHITE);
        g2.drawString(text, bx + padX, by + padY + textAscent);
    }
}
