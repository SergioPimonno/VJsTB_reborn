package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.CableSpecCalc;
import com.vjstb.ledscheme.service.RiggingCalc;
import com.vjstb.ledscheme.service.TrussCalc;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Рендерит PNG-схему для кнопки «Рассчитать точки подвеса» в
 * {@code ui.stage.SetupStagePanel} — сетка ЯЧЕЕК экрана (как в {@link ShapeEditorPanel},
 * не общий вид сцены сверху, как раньше через {@code SceneCanvasPanel.renderImage}:
 * баг-репорт «экспортируемая схема не информативна» — вид сцены целиком не показывал
 * отдельные кабинеты/вырезы, только сплошной прямоугольник экрана) с треугольниками
 * точек подвеса поверх верхней кромки и ГОРИЗОНТАЛЬНОЙ (транспонированной, по фидбеку
 * пользователя — длинный вертикальный список из 29+ строк на широких экранах listать
 * неудобно) таблицей нагрузки под сеткой: одна КОЛОНКА на точку, три строки внутри
 * (номер / X, мм / нагрузка, кг), а не одна строка на точку.
 *
 * <p>Треугольники ставятся РОВНО на {@link RiggingCalc.PointLoad#xMm()} — те же
 * координаты, что использует сам расчёт (метод грузовых площадей, см.
 * {@link RiggingCalc#compute}), а НЕ формула {@code SceneCanvasPanel.drawRiggingPoints}
 * (которая размещает точки по центрам {@code count} равных сегментов — визуально
 * похоже, но иные X-координаты, не привязанные к реальному распределению нагрузки).
 * Расхождение между этими двумя способами отрисовки уже существовало до этого файла
 * (живой прериг сцены рисует по-старому) — не устранено здесь, т.к. отдельная задача.
 *
 * <p>Колонки таблицы НЕ выровнены под соответствующими треугольниками сетки —
 * сознательно: при большом числе точек на узком экране (см. {@link
 * RiggingCalc#suggestPointCount} — точек не может быть больше числа занятых колонок)
 * ширины треугольникам ячейки не хватило бы на читаемый текст. Равномерная ширина
 * колонки ({@link #POINT_COL_W}) остаётся читаемой при любом количестве точек за счёт
 * округления X/нагрузки до целых (см. {@link #row}) — само значение X в таблице уже
 * достаточно, чтобы соотнести колонку с реальной позицией на экране.
 *
 * <p><b>Ферма подвеса</b> (см. {@code service.TrussCalc}, RIGGING_CALC_NOTES.md) — ряд
 * прямоугольников сегментов рисуется НАД треугольниками точек, той же пиксельной шкалой
 * (мм экрана → px сетки), что и они. Свес фермы за края экрана
 * ({@link TrussCalc.Result#leftOffsetMm()}/{@code rightOffsetMm()} {@code > 0}) раздвигает
 * левый край сетки ({@code gridX})/итоговую ширину картинки, чтобы сегменты и треугольники
 * с отрицательной или превышающей ширину экрана {@code xMm} (см. {@link RiggingCalc}
 * class-javadoc — координаты теперь считаются от фермы, не от экрана) не обрезались по
 * краю изображения. Недостача (ферма короче экрана) картинку не раздвигает — рисуется
 * просто короче сетки, плюс текстовое предупреждение под заголовком.
 */
public final class RiggingSchemaImageWriter {

    private static final int CELL = 22;
    private static final int MARGIN = 16;
    private static final int TRIANGLE_H = 14;
    /** Заметно толще ряда точек (баг-репорт 2026-09-14 "ферму почти не видно") — ферма
     *  физически заметно массивнее самих точек подвеса. */
    private static final int TRUSS_H = 18;
    private static final int TRUSS_GAP = 4;
    /** Высококонтрастный светло-серый ("металл"), фиксированный — прежний
     *  {@code Palette.MUTED} на тёмном фоне почти сливался с ним. */
    private static final Color TRUSS_FILL = new Color(0xc9d1d9);
    private static final int POINT_COL_W = 56;
    private static final int ROW_H = 20;
    private static final String[] ROW_LABELS = {"Точка", "X, мм", "Нагрузка, кг"};

    private RiggingSchemaImageWriter() {
    }

    public static BufferedImage render(Screen screen, CabinetType type, RiggingCalc.Result result,
            TrussCalc.Result truss) {
        int gridW = Math.max(1, screen.getCols() * CELL);
        int gridH = Math.max(1, screen.getRows() * CELL);
        int pointCount = result.points().size();

        double widthMm = screen.getCols() * (type != null ? type.getWidthMm() : 0);
        double pxPerMm = widthMm > 0 ? gridW / widthMm : 0;
        // Комплект сегментов не всегда бьёт целевую длину РОВНО (каталог может не содержать
        // подходящей комбинации, см. MinimalKitCalc.solveMinimizingOverage) -- остаток
        // излишка ложится хвостом СПРАВА (drawTruss укладывает сегменты слева направо без
        // обрезки, см. её javadoc) и должен ТОЖЕ раздвигать картинку, иначе этот хвост
        // обрежется по правому краю BufferedImage -- тот же баг-репорт "фермy не видно
        // целиком", просто другая причина (не свес, а нехватка подходящей длины в каталоге).
        double kitOverageMm = truss.pieces() != null ? Math.max(0, truss.totalKitLengthMm() - truss.targetLengthMm())
                : 0;
        int extraLeftPx = pxPerMm > 0 ? Math.max(0, (int) Math.round(truss.leftOffsetMm() * pxPerMm)) : 0;
        int extraRightPx = pxPerMm > 0
                ? Math.max(0, (int) Math.round((truss.rightOffsetMm() + kitOverageMm) * pxPerMm)) : 0;

        int labelColW = labelColumnWidth();
        int tableW = labelColW + pointCount * POINT_COL_W;
        int width = Math.max(gridW + extraLeftPx + extraRightPx, tableW) + MARGIN * 2;

        boolean warnShort = truss.shorterThanScreenWarning();
        int titleY = MARGIN + 14;
        int warnY = warnShort ? titleY + 16 : titleY;
        int gridY = warnY + 10 + TRUSS_H + TRUSS_GAP + TRIANGLE_H;
        int tableTitleY = gridY + gridH + MARGIN + 14;
        int tableTopY = tableTitleY + 10;
        int height = tableTopY + ROW_LABELS.length * ROW_H + MARGIN;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Palette.BG);
        g2.fillRect(0, 0, width, height);

        g2.setColor(Palette.TEXT);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 15f));
        g2.drawString(screen.getName() + " — точки подвеса", MARGIN, titleY);
        if (warnShort) {
            g2.setColor(Palette.WARN);
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 12f));
            g2.drawString("Ферма короче ширины экрана", MARGIN, warnY);
        }

        int gridX = MARGIN + extraLeftPx;
        drawTruss(g2, truss, gridX, gridY - TRIANGLE_H - TRUSS_GAP - TRUSS_H, pxPerMm);
        g2.setColor(Color.YELLOW);
        for (RiggingCalc.PointLoad p : result.points()) {
            int px = gridX + (widthMm > 0 ? (int) Math.round(p.xMm() / widthMm * gridW) : gridW / 2);
            drawDownwardTriangle(g2, px, gridY, TRIANGLE_H);
        }

        for (CabinetInstance c : screen.getCabinets()) {
            int x = gridX + c.getColIndex() * CELL;
            int y = gridY + c.getRowIndex() * CELL;
            g2.setColor(c.isHidden() ? Palette.BG : Palette.PHASE_NONE);
            g2.fillRect(x + 1, y + 1, CELL - 2, CELL - 2);
            g2.setColor(c.isHidden() ? Palette.BORDER : Palette.MUTED);
            g2.drawRect(x + 1, y + 1, CELL - 2, CELL - 2);
        }

        g2.setColor(Palette.TEXT);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 13f));
        g2.drawString("Нагрузка по точкам подвеса", MARGIN, tableTitleY);

        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 11f));
        for (int r = 0; r < ROW_LABELS.length; r++) {
            g2.setColor(Palette.TEXT);
            g2.drawString(ROW_LABELS[r], MARGIN, tableTopY + r * ROW_H + ROW_H - 6);
        }
        g2.setColor(Palette.BORDER);
        int dividerX = MARGIN + labelColW - 8;
        g2.drawLine(dividerX, tableTopY - 4, dividerX, tableTopY + ROW_LABELS.length * ROW_H);

        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10f));
        FontMetrics fm = g2.getFontMetrics();
        int tableX = MARGIN + labelColW;
        for (RiggingCalc.PointLoad p : result.points()) {
            int colCenter = tableX + p.index() * POINT_COL_W + POINT_COL_W / 2;
            g2.setColor(p.overCapacity() ? Palette.WARN : Palette.TEXT);
            drawCentered(g2, fm, String.valueOf(p.index() + 1), colCenter, tableTopY + ROW_H - 6);
            drawCentered(g2, fm, row(p.xMm()), colCenter, tableTopY + 2 * ROW_H - 6);
            drawCentered(g2, fm, row(p.loadKg()), colCenter, tableTopY + 3 * ROW_H - 6);
        }

        g2.dispose();
        return img;
    }

    /** Целое округление — точная десятая доля мм/кг для читаемости в узкой колонке
     *  роли не играет, реальную позицию/нагрузку уже даёт живой диалог после расчёта
     *  ({@code SetupStagePanel.calculateRiggingPoints}), эта картинка — обзорная схема. */
    private static String row(double v) {
        return UiKit.fmt(Math.round(v));
    }

    private static int labelColumnWidth() {
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = probe.createGraphics();
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 11f));
        FontMetrics fm = g2.getFontMetrics();
        int w = 0;
        for (String label : ROW_LABELS) {
            w = Math.max(w, fm.stringWidth(label));
        }
        g2.dispose();
        return w + 16;
    }

    private static void drawCentered(Graphics2D g2, FontMetrics fm, String text, int centerX, int baselineY) {
        g2.drawString(text, centerX - fm.stringWidth(text) / 2, baselineY);
    }

    /** Тот же визуальный стиль (жёлтый треугольник вершиной вниз, на верхнюю кромку),
     *  что и {@code SceneCanvasPanel.drawRiggingPoints} — только X берётся из реальных
     *  координат расчёта, см. class-javadoc. */
    private static void drawDownwardTriangle(Graphics2D g2, int centerX, int tipY, int size) {
        int[] xs = {centerX - size / 2, centerX + size / 2, centerX};
        int[] ys = {tipY - size, tipY - size, tipY};
        g2.fillPolygon(xs, ys, 3);
    }

    /** Раскладывает {@code truss.pieces()} в ОТДЕЛЬНЫЕ сегменты (не один прямоугольник на
     *  группу одинаковых длин — иначе стык между двумя кусками одной длины визуально
     *  терялся бы), сортированные по убыванию длины и уложенные слева направо в
     *  координатах, локальных для фермы (0 — левый край фермы), затем переведённые в
     *  пиксели той же шкалой {@code pxPerMm}, что и точки/сетка, со сдвигом на {@code
     *  truss.leftOffsetMm()} — та же формула перевода координат, что {@code
     *  RiggingCalc.compute} использует для {@code PointLoad#xMm()} (см. class-javadoc).
     *  Каждый сегмент — отдельный прямоугольник с рамкой (граница = стык), нарисованный
     *  РЕАЛЬНОЙ физической длиной, БЕЗ обрезки по целевой длине экрана — баг-репорт
     *  2026-09-14: раньше последний сегмент клэмпился {@code Math.min(cursorMm+len,
     *  targetMm)}, из-за чего ферма из одного куска 2м на 1.5м экран визуально выглядела
     *  как ~1м (обрезанная по краю экрана), маскируя реальный физический излишек, который
     *  как раз важно ВИДЕТЬ на этой схеме. Ничего не рисует, если профиль не выбран/каталог
     *  пуст/{@code pxPerMm <= 0} (ширина экрана неизвестна — нечем масштабировать). */
    private static void drawTruss(Graphics2D g2, TrussCalc.Result truss, int gridX, int trussY, double pxPerMm) {
        if (truss.pieces() == null || truss.pieces().isEmpty() || pxPerMm <= 0) {
            return;
        }
        List<Double> segMm = new ArrayList<>();
        for (CableSpecCalc.Piece p : truss.pieces()) {
            for (int i = 0; i < p.count(); i++) {
                segMm.add(p.lengthM() * 1000.0);
            }
        }
        segMm.sort(Comparator.reverseOrder());

        double leftOffsetMm = truss.leftOffsetMm();
        double cursorMm = 0;
        Color prevColor = g2.getColor();
        for (double len : segMm) {
            double startMm = cursorMm;
            double endMm = cursorMm + len;
            int px1 = gridX + (int) Math.round((startMm - leftOffsetMm) * pxPerMm);
            int px2 = gridX + (int) Math.round((endMm - leftOffsetMm) * pxPerMm);
            if (px2 > px1) {
                g2.setColor(TRUSS_FILL);
                g2.fillRect(px1, trussY, px2 - px1, TRUSS_H);
                g2.setColor(Color.BLACK);
                g2.drawRect(px1, trussY, px2 - px1, TRUSS_H);
            }
            cursorMm = endMm;
        }
        g2.setColor(prevColor);
    }
}
