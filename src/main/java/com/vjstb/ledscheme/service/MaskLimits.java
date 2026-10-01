package com.vjstb.ledscheme.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Пределы размера маски/канваса — ОДНА точка для спиннеров канваса, {@link AppModel#addCanvas}/
 * {@link AppModel#updateCanvas}, экспорта масок и пресетов Resolume/After Effects.
 *
 * <p>Запрос пользователя 2026-09-30 (п. 2 плана v2.6, решение D7): «убрать ограничение 16k на
 * сторону при генерации масок и канвасов». Раньше 16384 было зашито прямо в спиннеры
 * {@code VisualizationStagePanel} и в XML Resolume, а реальным пределом была память —
 * маска рисовалась целиком в {@code BufferedImage} (4 байта/px). Теперь запись идёт полосами
 * ({@code ui.StreamingPngWriter}), а здесь — только два порога:
 * <ul>
 *   <li>{@link #WARN_SIDE_PX} = 16384 — типовой предел стороны текстуры GPU/медиасерверов
 *       (и прежний предел Resolume): маску такого размера записать можно, но не всякий
 *       плеер её откроет — поэтому ПРЕДУПРЕЖДЕНИЕ, не запрет;</li>
 *   <li>{@link #MAX_SIDE_PX} = 30000 — предел композиции After Effects (30000×30000) и
 *       решение пользователя D7: больше — канвас не создаётся, маска экрана не
 *       экспортируется (экран на «Сетапе» при этом не ограничиваем — его размер задаётся
 *       там, и запретить его здесь нельзя).</li>
 * </ul>
 * Размеры маски экрана — только через {@link MaskGeometry} (с множителем экрана-«сетки»).
 */
public final class MaskLimits {

    /** Сторона ≥ этого — предупреждение (см. class-javadoc). */
    public static final int WARN_SIDE_PX = 16384;
    /** Сторона &gt; этого — запрет (см. class-javadoc). */
    public static final int MAX_SIDE_PX = 30000;

    private MaskLimits() {
    }

    /** Одна маска/канвас в наборе экспорта: подпись для списка предупреждения и размер. */
    public record Item(String label, int width, int height) {
        /** «&lt;имя&gt; — W×H px» — строка списка в подтверждении. */
        public String describe() {
            return label + " — " + width + "×" + height + " px";
        }
    }

    /** Хотя бы одна сторона ≥ {@link #WARN_SIDE_PX}. */
    public static boolean isLarge(int widthPx, int heightPx) {
        return widthPx >= WARN_SIDE_PX || heightPx >= WARN_SIDE_PX;
    }

    /** Хотя бы одна сторона &gt; {@link #MAX_SIDE_PX}. */
    public static boolean exceedsMax(int widthPx, int heightPx) {
        return widthPx > MAX_SIDE_PX || heightPx > MAX_SIDE_PX;
    }

    /** Проверка размера канваса для {@link AppModel#addCanvas}/{@link AppModel#updateCanvas}:
     *  сторона больше {@link #MAX_SIDE_PX} — {@link IllegalArgumentException} с русским
     *  текстом (UI показывает {@code getMessage()} как есть). */
    public static void checkCanvasSize(int widthPx, int heightPx) {
        if (exceedsMax(widthPx, heightPx)) {
            throw new IllegalArgumentException("Канвас " + widthPx + "×" + heightPx + " px больше допустимого:"
                    + " каждая сторона — не больше " + MAX_SIDE_PX + " px (предел композиции After Effects).");
        }
    }

    /** Подпись рядом со спиннерами канваса: текст предупреждения или {@code null}, если
     *  размер обычный. Сторона больше {@link #MAX_SIDE_PX} — текст об отказе. */
    public static String canvasSizeHint(int widthPx, int heightPx) {
        if (exceedsMax(widthPx, heightPx)) {
            return "Сторона больше " + MAX_SIDE_PX + " px — такой канвас создать нельзя (предел After Effects).";
        }
        if (isLarge(widthPx, heightPx)) {
            return "Сторона ≥ " + WARN_SIDE_PX + " px — не все медиасерверы/GPU откроют такую маску"
                    + " (Resolume и многие плееры ограничены 16384 px).";
        }
        return null;
    }

    /** Элементы набора со стороной ≥ {@link #WARN_SIDE_PX}, но в пределах {@link #MAX_SIDE_PX}. */
    public static List<Item> large(Collection<Item> items) {
        List<Item> out = new ArrayList<>();
        for (Item it : items) {
            if (isLarge(it.width(), it.height()) && !exceedsMax(it.width(), it.height())) {
                out.add(it);
            }
        }
        return out;
    }

    /** Элементы набора со стороной &gt; {@link #MAX_SIDE_PX} — их не экспортируем. */
    public static List<Item> tooLarge(Collection<Item> items) {
        List<Item> out = new ArrayList<>();
        for (Item it : items) {
            if (exceedsMax(it.width(), it.height())) {
                out.add(it);
            }
        }
        return out;
    }

    /** Многострочный список «&lt;имя&gt; — W×H px». */
    public static String describe(Collection<Item> items) {
        StringBuilder sb = new StringBuilder();
        for (Item it : items) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(it.describe());
        }
        return sb.toString();
    }
}
