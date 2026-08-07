package com.vjstb.ledscheme.service;

/**
 * Расчёт throw ratio/объектива и освещённости экрана для проектора (Task #135/v2.0) —
 * упрощённые, но стандартные для отрасли формулы (тот же уровень строгости, что у
 * калькуляторов throw-дистанции Epson/BenQ/Christie: throw ratio считается по ширине
 * изображения, не по диагонали; освещённость — lumens*gain/площадь, без учёта угла
 * падения и оптических потерь). Бакеты {@link AmbientLight} — ориентировочная
 * практика инсталляций, НЕ нормативная таблица (аналогично тому, как VideoTimingCalc
 * намеренно не переносит таблицы DMT/CEA — см. его class-javadoc).
 */
public final class ProjectorCalc {

    private ProjectorCalc() {
    }

    public enum AspectRatio {
        R16_9("16:9", 16, 9), R16_10("16:10", 16, 10), R4_3("4:3", 4, 3), R1_1("1:1", 1, 1);

        private final String label;
        private final double w;
        private final double h;

        AspectRatio(String label, double w, double h) {
            this.label = label;
            this.w = w;
            this.h = h;
        }

        public double w() {
            return w;
        }

        public double h() {
            return h;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record ImageSize(double widthM, double heightM) {
        public double areaM2() {
            return widthM * heightM;
        }
    }

    /** Ширина/высота из диагонали и соотношения сторон (теорема Пифагора:
     *  диагональ² = ширина² + высота², ширина/высота = ar.w()/ar.h()). */
    public static ImageSize imageSizeFromDiagonal(double diagonalM, AspectRatio ar) {
        double ratio = Math.hypot(ar.w(), ar.h());
        double widthM = diagonalM * ar.w() / ratio;
        double heightM = diagonalM * ar.h() / ratio;
        return new ImageSize(widthM, heightM);
    }

    /** Throw ratio = дистанция / ширина изображения — индустриальный стандарт
     *  (throw-калькуляторы считают именно по ширине, не по диагонали). */
    public static double requiredThrowRatio(double throwDistanceM, ImageSize size) {
        return throwDistanceM / size.widthM();
    }

    public enum LensFitStatus {
        FITS, TOO_SHORT_INCREASE_DISTANCE, TOO_LONG_DECREASE_DISTANCE
    }

    public record LensFit(LensFitStatus status, double requiredRatio, double lensMin, double lensMax) {
    }

    public static LensFit checkLensFit(double throwDistanceM, ImageSize size, double lensMinRatio,
            double lensMaxRatio) {
        double req = requiredThrowRatio(throwDistanceM, size);
        LensFitStatus status = req < lensMinRatio ? LensFitStatus.TOO_LONG_DECREASE_DISTANCE
                : req > lensMaxRatio ? LensFitStatus.TOO_SHORT_INCREASE_DISTANCE
                : LensFitStatus.FITS;
        return new LensFit(status, req, lensMinRatio, lensMaxRatio);
    }

    /** Обратная задача: диапазон дистанций, на которых требуемый throw ratio лежит
     *  внутри зума объектива [lensMinRatio, lensMaxRatio]. */
    public record ThrowDistanceRange(double minM, double maxM) {
    }

    public static ThrowDistanceRange throwDistanceRange(ImageSize size, double lensMinRatio, double lensMaxRatio) {
        return new ThrowDistanceRange(lensMinRatio * size.widthM(), lensMaxRatio * size.widthM());
    }

    /** Вертикальный сдвиг объектива (lens shift) задаётся производителями в % от
     *  высоты изображения — конвертация в физическое смещение для визуализатора. */
    public static double verticalOffsetM(ImageSize size, double offsetPercent) {
        return size.heightM() * offsetPercent / 100.0;
    }

    // ---- яркость ----

    public enum AmbientLight {
        DARK_ROOM("Тёмное помещение", 5, 20),
        DIM("Приглушённый свет", 20, 80),
        OFFICE("Офисное освещение", 80, 300),
        BRIGHT("Яркое помещение", 300, 1000);

        private final String label;
        private final double minLux;
        private final double maxLux;

        AmbientLight(String label, double minLux, double maxLux) {
            this.label = label;
            this.minLux = minLux;
            this.maxLux = maxLux;
        }

        public double minLux() {
            return minLux;
        }

        public double maxLux() {
            return maxLux;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum BrightnessStatus {
        PASS, MARGINAL, FAIL
    }

    public record BrightnessCheck(double screenLux, AmbientLight bucket, BrightnessStatus status) {
    }

    /** Освещённость экрана = ANSI-люмены * gain экрана / площадь(м²) — упрощённая
     *  формула без учёта конуса рассеяния/угла падения/потерь оптики. */
    public static double screenIlluminanceLux(double ansiLumens, double screenGain, ImageSize size) {
        return ansiLumens * screenGain / size.areaM2();
    }

    public static BrightnessCheck checkBrightness(double ansiLumens, double screenGain, ImageSize size,
            AmbientLight ambient) {
        double lux = screenIlluminanceLux(ansiLumens, screenGain, size);
        BrightnessStatus status = lux >= ambient.maxLux() ? BrightnessStatus.PASS
                : lux >= ambient.minLux() ? BrightnessStatus.MARGINAL
                : BrightnessStatus.FAIL;
        return new BrightnessCheck(lux, ambient, status);
    }
}
