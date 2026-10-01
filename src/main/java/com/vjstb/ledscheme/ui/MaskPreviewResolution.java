package com.vjstb.ledscheme.ui;

/**
 * Разрешение превью масок — как выпадающий список «Resolution» в окне композиции After
 * Effects (Full / Half / Third / Quarter / Custom / Auto). Запрос пользователя 2026-09-30
 * (решение D7: «уменьшенное превью»): после снятия предела 16k маска размещения может быть
 * до 30000×30000 px, и прежний кэш превью {@code CanvasEditorPanel.maskCache} в ПОЛНОМ
 * разрешении съел бы гигабайты.
 * <ul>
 *   <li>{@link #AUTO} — маска рисуется ровно в размере отображения (но не крупнее нативного,
 *       как у AE);</li>
 *   <li>дробь — маска рисуется в доле нативного разрешения и масштабируется при показе: видны
 *       реальные потери детализации, как в AE.</li>
 * </ul>
 * Бюджет памяти: если превью ОДНОГО размещения при выбранной дроби больше
 * {@link #BUDGET_PX} (~64 Мпикс), берётся следующая меньшая дробь ({@link #choose}), и
 * холст показывает пометку «превью понижено до 1/N». Значение хранится в профиле
 * ({@code UserProfile.maskPreviewResolution}, по умолчанию «Авто»).
 */
public enum MaskPreviewResolution {
    FULL("Полное", 1),
    HALF("1/2", 2),
    THIRD("1/3", 3),
    QUARTER("1/4", 4),
    EIGHTH("1/8", 8),
    AUTO("Авто", 0);

    /** ~64 Мпикс на одно превью (×4 Б = 256 МБ) — 8192×8192 ещё влезает целиком. */
    public static final long BUDGET_PX = 64L * 1024 * 1024;

    private final String label;
    private final int divisor;

    MaskPreviewResolution(String label, int divisor) {
        this.label = label;
        this.divisor = divisor;
    }

    public String label() {
        return label;
    }

    /** Знаменатель дроби (1 — полное), 0 — «Авто». */
    public int divisor() {
        return divisor;
    }

    @Override
    public String toString() {
        return label;
    }

    /** Из строки профиля; неизвестное/пустое — {@link #AUTO} (значение по умолчанию). */
    public static MaskPreviewResolution fromName(String name) {
        if (name != null) {
            for (MaskPreviewResolution r : values()) {
                if (r.name().equalsIgnoreCase(name)) {
                    return r;
                }
            }
        }
        return AUTO;
    }

    /**
     * Фактический масштаб рендера превью.
     *
     * @param scale      масштаб рендера (1 — нативное разрешение маски)
     * @param divisor    знаменатель фактической дроби (0 — «Авто» без понижения)
     * @param downgraded {@code true}, если из-за бюджета памяти взята меньшая дробь, чем выбрана
     */
    public record Choice(double scale, int divisor, boolean downgraded) {
        /** Пометка для угла холста, {@code null} — понижения не было. */
        public String note() {
            return downgraded ? "превью понижено до 1/" + divisor : null;
        }
    }

    public static Choice choose(MaskPreviewResolution preset, double displayScale, int widthPx, int heightPx) {
        return choose(preset, displayScale, widthPx, heightPx, BUDGET_PX);
    }

    /** Чистая функция выбора масштаба (тестируется без UI): см. class-javadoc. Последовательность
     *  понижения — 1, 1/2, 1/3, 1/4, 1/8, дальше вдвое (1/16, 1/32…) — для экранов шире
     *  30000 px, которые на «Сетапе» не ограничены. */
    public static Choice choose(MaskPreviewResolution preset, double displayScale, int widthPx, int heightPx,
                                long budgetPx) {
        MaskPreviewResolution p = preset != null ? preset : AUTO;
        if (p == AUTO) {
            double s = Math.min(1.0, displayScale > 0 ? displayScale : 1.0);
            if (pixels(widthPx, heightPx, s) <= budgetPx) {
                return new Choice(s, 0, false);
            }
            int n = 1;
            while (pixels(widthPx, heightPx, 1.0 / n) > budgetPx || 1.0 / n > s) {
                n = nextDivisor(n);
            }
            return new Choice(1.0 / n, n, true);
        }
        int n = p.divisor;
        while (pixels(widthPx, heightPx, 1.0 / n) > budgetPx) {
            n = nextDivisor(n);
        }
        return new Choice(1.0 / n, n, n != p.divisor);
    }

    static int nextDivisor(int n) {
        return switch (n) {
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 4;
            case 4 -> 8;
            default -> n * 2;
        };
    }

    private static long pixels(int w, int h, double scale) {
        return (long) MaskImage.scaledSide(Math.max(1, w), scale) * MaskImage.scaledSide(Math.max(1, h), scale);
    }
}
