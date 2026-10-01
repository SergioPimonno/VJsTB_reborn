package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.FloorCalc;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

/**
 * 2D-план напольного каркаса, вид сверху (запрос пользователя 2026-10-01, по его скриншоту):
 * серые квадраты кабинетов, рамы — жирными контурами, стаканы на стыках коротких сторон —
 * тёмными отметками, кабинеты БЕЗ опоры (нет рамы под ними — хвосты цепочек, см. {@link
 * FloorCalc} class-javadoc) — ОРАНЖЕВЫМ, внизу легенда и итоговые числа. Скрытые кабинеты
 * формы экрана рисуются только бледным контуром — чтобы форма читалась, но было видно, что
 * там ни кабинета, ни рамы нет.
 *
 * <p>Обычная лёгкая {@code JPanel} без GL и без собственных окон — поэтому безопасна в
 * headless-тестах: {@link #renderImage(int, int)} рисует в {@link BufferedImage}, ничего не
 * показывая (тот же приём, что у {@code *SchemaImageWriter}). Геометрия раскладки ({@link
 * #cabinetRect}) вынесена в отдельный метод и используется И рисованием, И тестом — иначе
 * тест проверял бы не те пиксели, что реально нарисованы.
 *
 * <p>Кабинеты рисуются по номинальной сетке (колонка × ширина, ряд × высота типа экрана);
 * ручные смещения отдельных кабинетов ({@code CabinetInstance#getOffsetXMm()}) и
 * переопределение типа по ячейке здесь не отражаются — каркас считается по номинальной
 * сетке, план показывает ровно то, что посчитано.
 */
public class FloorPlanPanel extends JPanel {

    static final Color CABINET_FILL = new Color(0xC4C4C4);
    static final Color CABINET_BORDER = new Color(0x8A8A8A);
    /** «Без опоры» — оранжевый, прямое указание пользователя. */
    public static final Color UNSUPPORTED_FILL = new Color(0xFF8C1A);
    static final Color HIDDEN_OUTLINE = new Color(0xE2E2E2);
    static final Color FRAME_STROKE = new Color(0x1F3A60);
    static final Color CUP_FILL = new Color(0xB3261E);
    private static final Color TEXT = new Color(0x202020);

    private static final int MARGIN = 16;
    private static final int HEADER_H = 26;
    private static final int LEGEND_H = 64;

    private final Screen screen;
    private final CabinetType type;
    private final FloorCalc.Result result;

    public FloorPlanPanel(Screen screen, CabinetType type, FloorCalc.Result result) {
        this.screen = screen;
        this.type = type;
        this.result = result;
        setBackground(Color.WHITE);
        setOpaque(true);
        setPreferredSize(new Dimension(900, 560));
    }

    /** Масштаб и левый верхний угол сетки кабинетов в текущих размерах панели. */
    private record Geo(double x0, double y0, double scale) {
    }

    private Geo geo(int width, int height) {
        double cabW = type != null && type.getWidthMm() > 0 ? type.getWidthMm() : 500;
        double cabH = type != null && type.getHeightMm() > 0 ? type.getHeightMm() : 500;
        double gridW = Math.max(1, screen.getCols()) * cabW;
        double gridH = Math.max(1, screen.getRows()) * cabH;
        double availW = Math.max(10, width - 2 * MARGIN);
        double availH = Math.max(10, height - 2 * MARGIN - HEADER_H - LEGEND_H);
        double scale = Math.min(availW / gridW, availH / gridH);
        double x0 = MARGIN + (availW - gridW * scale) / 2.0;
        double y0 = MARGIN + HEADER_H + (availH - gridH * scale) / 2.0;
        return new Geo(x0, y0, scale);
    }

    /** Прямоугольник кабинета (ряд, колонка) в пикселях для панели размера {@code width ×
     *  height} — общий для рисования и тестов. */
    public Rectangle cabinetRect(int row, int col, int width, int height) {
        return cellsRect(row, col, 1, 1, width, height);
    }

    private Rectangle cellsRect(int row, int col, int rowCount, int colCount, int width, int height) {
        Geo g = geo(width, height);
        double cabW = type != null && type.getWidthMm() > 0 ? type.getWidthMm() : 500;
        double cabH = type != null && type.getHeightMm() > 0 ? type.getHeightMm() : 500;
        int x1 = (int) Math.round(g.x0() + col * cabW * g.scale());
        int y1 = (int) Math.round(g.y0() + row * cabH * g.scale());
        int x2 = (int) Math.round(g.x0() + (col + colCount) * cabW * g.scale());
        int y2 = (int) Math.round(g.y0() + (row + rowCount) * cabH * g.scale());
        return new Rectangle(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1));
    }

    /** Рисует план в картинку заданного размера, не показывая никаких окон — для тестов и
     *  на будущее для выгрузки плана в пакет документации. */
    public BufferedImage renderImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            paintPlan(g, width, height);
        } finally {
            g.dispose();
        }
        return img;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        paintPlan((Graphics2D) g, getWidth(), getHeight());
    }

    private void paintPlan(Graphics2D g, int width, int height) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(TEXT);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 13f));
        g.drawString("План пола «" + screen.getName() + "» — вид сверху (" + screen.getCols() + "×"
                + screen.getRows() + " кабинетов)", MARGIN, MARGIN + 14);

        int pad = 1;
        for (int r = 0; r < screen.getRows(); r++) {
            for (int c = 0; c < screen.getCols(); c++) {
                CabinetInstance cab = screen.cabinetAt(r, c);
                Rectangle rc = cabinetRect(r, c, width, height);
                if (cab == null || cab.isHidden()) {
                    g.setColor(HIDDEN_OUTLINE);
                    g.drawRect(rc.x + pad, rc.y + pad, rc.width - 2 * pad - 1, rc.height - 2 * pad - 1);
                    continue;
                }
                g.setColor(result.isUnsupported(r, c) ? UNSUPPORTED_FILL : CABINET_FILL);
                g.fillRect(rc.x + pad, rc.y + pad, rc.width - 2 * pad, rc.height - 2 * pad);
                g.setColor(CABINET_BORDER);
                g.drawRect(rc.x + pad, rc.y + pad, rc.width - 2 * pad - 1, rc.height - 2 * pad - 1);
            }
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(FRAME_STROKE);
        g.setStroke(new BasicStroke(3f));
        for (FloorCalc.FramePlacement f : result.frames()) {
            Rectangle fr = cellsRect(f.row(), f.col(), f.rowCount(), f.colCount(), width, height);
            g.drawRect(fr.x + 3, fr.y + 3, fr.width - 6, fr.height - 6);
        }
        g.setStroke(new BasicStroke(1f));

        // Стаканы: 2 на каждый стык по короткой стороне — по отметке на четверти и трёх
        // четвертях высоты ряда рам, прямо на линии стыка.
        g.setColor(CUP_FILL);
        for (FloorCalc.Joint j : result.shortSideJoints()) {
            Rectangle fr = cellsRect(j.b().row(), j.b().col(), j.b().rowCount(), j.b().colCount(), width, height);
            int s = Math.max(4, Math.min(9, fr.height / 6));
            int x = fr.x - s / 2;
            g.fillRect(x, fr.y + fr.height / 4 - s / 2, s, s);
            g.fillRect(x, fr.y + 3 * fr.height / 4 - s / 2, s, s);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        paintLegend(g, width, height);
    }

    private void paintLegend(Graphics2D g, int width, int height) {
        int y = height - MARGIN - LEGEND_H + 10;
        int x = MARGIN;
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 12f));
        FontMetrics fm = g.getFontMetrics();

        x = legendSwatch(g, fm, x, y, CABINET_FILL, false, "Кабинет на раме");
        x = legendSwatch(g, fm, x, y, UNSUPPORTED_FILL, false, "Без опоры");
        x = legendSwatch(g, fm, x, y, FRAME_STROKE, true, "Рама");
        g.setColor(CUP_FILL);
        g.fillRect(x, y + 3, 8, 8);
        g.setColor(TEXT);
        g.drawString("Стаканы", x + 14, y + 11);

        g.setColor(TEXT);
        String totals = String.format("Рам: %d · Стаканов: %d · Болтов: %d · Ножек: %d · Зубов: %d (%d/каб.)"
                        + " · Без опоры: %d · Средняя нагрузка: %s кг/м²",
                result.frameCount(), result.cupCount(), result.boltCount(), result.legCount(),
                result.toothCount(), result.teethPerCabinet(), result.unsupportedCabinetCount(),
                UiKit.fmt(Math.round(result.averageLoadKgPerM2() * 10) / 10.0));
        g.drawString(totals, MARGIN, y + 32);
        String pitch = String.format("Шаг рам: %.0f × %.0f мм (%d×%d каб. на раму)", result.framePitchXMm(),
                result.framePitchYMm(), result.cabinetsPerFrameX(), result.cabinetsPerFrameY());
        g.drawString(pitch, MARGIN, y + 50);
    }

    private static int legendSwatch(Graphics2D g, FontMetrics fm, int x, int y, Color color, boolean outline,
            String label) {
        if (outline) {
            g.setColor(color);
            g.setStroke(new BasicStroke(3f));
            g.drawRect(x + 1, y + 1, 14, 12);
            g.setStroke(new BasicStroke(1f));
        } else {
            g.setColor(color);
            g.fillRect(x, y, 16, 14);
            g.setColor(CABINET_BORDER);
            g.drawRect(x, y, 15, 13);
        }
        g.setColor(TEXT);
        g.drawString(label, x + 22, y + 11);
        return x + 22 + fm.stringWidth(label) + 18;
    }
}
