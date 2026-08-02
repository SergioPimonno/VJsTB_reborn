package com.vjstb.ledscheme.service;

import java.util.List;

/**
 * Расчёт видеотайминга (VESA CVT / CVT-RB / CVT-RBv2) и требуемой полосы канала —
 * аналог tomverbeure.github.io/video_timings_calculator, портированный по формулам
 * VESA CVT 1.2 (см. приложенный образец), но БЕЗ таблиц готовых стандартных режимов
 * DMT/CEA-861: у LED-стен разрешение почти всегда нестандартное (произвольные
 * панели × колонны/строки), совпадение с готовым DMT/CEA режимом — редкое
 * исключение, а сами таблицы — тысячи строк готовых разрешений, не несущие
 * пользы для этой задачи. Раздел «Пользовательский» (Custom) — по аналогии с
 * колонкой Custom оригинала: фиксированные H/V Blank вместо расчёта по CVT.
 */
public final class VideoTimingCalc {

    private VideoTimingCalc() {
    }

    public enum Standard { CVT, CVT_RB, CVT_RB2, CUSTOM }

    public enum ColorFormat {
        RGB_444("RGB 4:4:4", 3.0),
        YUV_444("YUV 4:4:4", 3.0),
        YUV_422("YUV 4:2:2", 2.0),
        YUV_420("YUV 4:2:0", 1.5);

        private final String label;
        private final double multiplier;

        ColorFormat(String label, double multiplier) {
            this.label = label;
            this.multiplier = multiplier;
        }

        public String getLabel() {
            return label;
        }

        public double getMultiplier() {
            return multiplier;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Входные параметры расчёта — общие для всех 4 колонок (CVT/CVT-RB/CVT-RBv2/Custom). */
    public record Input(int horizPixels, int vertPixels, double refreshRate, boolean margins,
                         boolean interlaced, boolean videoOptimized, int customHBlank, int customVBlank) {
    }

    /** Один расчитанный режим — некоторые поля (порчи/полярность/аспект) не определены
     *  для Custom (null), т.к. там нет разбивки бланка на front/sync/back porch. */
    public record Timing(String aspectRatio, double pixelClockHz,
                          int hTotal, int hActive, int hBlank, Integer hFrontPorch, Integer hSync, Integer hBackPorch,
                          Character hPol, double hFreqHz, double hPeriodUs,
                          int vTotal, int vActive, int vBlank, Integer vFrontPorch, Integer vSync, Integer vBackPorch,
                          Character vPol, double vFreqHz, double vPeriodMs, double vBlankDurationUs) {

        /** Xorg/Linux modeline — совпадает форматом со строкой оригинала (без front/sync/back
         *  porch модлайн построить нельзя, поэтому для Custom возвращается null). */
        public String modeline(String name) {
            if (hFrontPorch == null || hSync == null || vFrontPorch == null || vSync == null) {
                return null;
            }
            double clockMhz = Math.round(pixelClockHz / 1000.0) / 1000.0;
            StringBuilder sb = new StringBuilder();
            sb.append("Modeline \"").append(name).append("\" ").append(clockMhz);
            sb.append(' ').append(hActive);
            sb.append(' ').append(hActive + hFrontPorch);
            sb.append(' ').append(hActive + hFrontPorch + hSync);
            sb.append(' ').append(hTotal);
            sb.append(' ').append(vActive);
            sb.append(' ').append(vActive + vFrontPorch);
            sb.append(' ').append(vActive + vFrontPorch + vSync);
            sb.append(' ').append(vTotal);
            sb.append(hPol == '+' ? " +HSync" : " -HSync");
            sb.append(vPol == '+' ? " +VSync" : " -VSync");
            return sb.toString();
        }
    }

    /** Требуемая полоса канала для данного тайминга при заданных бит/компонент и
     *  формате цвета — все значения в бит/с. */
    public record Bandwidth(double peakBps, double peakBpsDsc8bpp, double lineBps, double activeBps) {
    }

    public static Bandwidth bandwidth(Timing t, int bpc, ColorFormat colorFormat) {
        double peak = t.pixelClockHz() * bpc * colorFormat.getMultiplier();
        double peakDsc = t.pixelClockHz() * 8;
        double line = peak * t.hActive() / (double) t.hTotal();
        double active = t.vFreqHz() * bpc * t.vActive() * t.hActive() * colorFormat.getMultiplier();
        return new Bandwidth(peak, peakDsc, line, active);
    }

    public static Timing compute(Standard standard, Input in) {
        if (in.horizPixels() <= 0 || in.vertPixels() <= 0 || in.refreshRate() <= 0) {
            return null;
        }
        if (standard == Standard.CUSTOM) {
            return computeCustom(in);
        }
        return computeCvt(standard, in);
    }

    private static Timing computeCustom(Input in) {
        int hActive = in.horizPixels();
        int hBlank = Math.max(0, in.customHBlank());
        int hTotal = hActive + hBlank;
        int vActive = in.vertPixels();
        int vBlank = Math.max(0, in.customVBlank());
        int vTotal = vActive + vBlank;
        double pixelClock = (double) hTotal * vTotal * in.refreshRate();
        double hFreq = pixelClock / hTotal;
        double vFreq = pixelClock / ((double) vTotal * hTotal);
        double hPeriodUs = 1_000_000.0 / hFreq;
        double vPeriodMs = 1000.0 / vFreq;
        double vBlankDurationUs = vBlank * hPeriodUs;
        return new Timing("Unknown", pixelClock, hTotal, hActive, hBlank, null, null, null, null,
                hFreq, hPeriodUs, vTotal, vActive, vBlank, null, null, null, null,
                vFreq, vPeriodMs, vBlankDurationUs);
    }

    // ---- VESA CVT 1.2 (см. VESA-CVT-1.2.pdf, разделы 5.2/5.3/5.4) ----

    private static final double H_SYNC_PER = 0.08;
    private static final int MIN_V_PORCH_RND = 3;
    private static final double MIN_VSYNC_BP = 550;
    private static final double MARGIN_PER = 1.8;
    private static final double C_PRIME = 30;
    private static final double M_PRIME = 300;
    private static final int CELL_GRAN = 8;

    private static Timing computeCvt(Standard standard, Input in) {
        double clockStep;
        double clockStepInv;
        double rbHBlank;
        double rbMinVBlank;
        int rbVFrontPorch;
        double refreshMultiplier;
        char hPolConst;
        char vPolConst;
        int minVBackPorch = 6;

        // clockStepInv задан ЛИТЕРАЛОМ, а не как 1/clockStep — для CVT-RBv2
        // clockStep=0.001 не представим в double точно, обратное деление могло бы
        // дать 999.9999999999999 вместо 1000 и сместить Math.floor на соседнем
        // граничном значении (см. оригинальный JS, где CLOCK_STEP_INV — тоже
        // отдельная явная константа, а не производная).
        switch (standard) {
            case CVT -> {
                clockStep = 0.25;
                clockStepInv = 4;
                rbHBlank = 160;
                rbMinVBlank = 460;
                rbVFrontPorch = 3;
                refreshMultiplier = 1;
                hPolConst = '-';
                vPolConst = '+';
            }
            case CVT_RB -> {
                clockStep = 0.25;
                clockStepInv = 4;
                rbHBlank = 160;
                rbMinVBlank = 460;
                rbVFrontPorch = 3;
                refreshMultiplier = 1;
                hPolConst = '+';
                vPolConst = '-';
            }
            case CVT_RB2 -> {
                clockStep = 0.001;
                clockStepInv = 1000;
                rbHBlank = 80;
                rbMinVBlank = 460;
                rbVFrontPorch = 1;
                refreshMultiplier = in.videoOptimized() ? 1000.0 / 1001.0 : 1;
                hPolConst = '+';
                vPolConst = '-';
            }
            default -> throw new IllegalArgumentException("Unexpected: " + standard);
        }

        double vFieldRateRqd = in.interlaced() ? in.refreshRate() * 2 : in.refreshRate();
        int cellGranRnd = CELL_GRAN;

        int hPixelsRnd = (int) Math.floor(in.horizPixels() / (double) cellGranRnd) * cellGranRnd;
        int leftMargin = in.margins()
                ? (int) Math.floor((hPixelsRnd * MARGIN_PER / 100) / cellGranRnd) * cellGranRnd : 0;
        int totalActivePixels = hPixelsRnd + 2 * leftMargin;

        int vLinesRnd = in.interlaced() ? (int) Math.floor(in.vertPixels() / 2.0) : in.vertPixels();
        int topMargin = in.margins() ? (int) Math.floor(vLinesRnd * MARGIN_PER / 100) : 0;
        double interlace = in.interlaced() ? 0.5 : 0;

        String aspectRatio = aspectRatioOf(in.interlaced() ? 2 * vLinesRnd : vLinesRnd, hPixelsRnd, cellGranRnd);

        int vSyncRnd;
        if (standard == Standard.CVT_RB2) {
            vSyncRnd = 8;
        } else {
            vSyncRnd = switch (aspectRatio) {
                case "4:3" -> 4;
                case "16:9" -> 5;
                case "16:10" -> 6;
                case "5:4", "15:9" -> 7;
                default -> 10;
            };
        }

        int hBlank;
        int hSync;
        int hFrontPorch;
        int hBackPorch;
        int vBlank;
        int vFrontPorch;
        int vBackPorch;
        int totalVLines;
        int totalPixels;
        double actPixelFreqMhz;

        if (standard == Standard.CVT) {
            double hPeriodEstUs = ((1.0 / vFieldRateRqd) - MIN_VSYNC_BP / 1_000_000.0)
                    / (vLinesRnd + 2 * topMargin + MIN_V_PORCH_RND + interlace) * 1_000_000.0;

            int vSyncBp = (int) Math.floor(MIN_VSYNC_BP / hPeriodEstUs) + 1;
            if (vSyncBp < vSyncRnd + minVBackPorch) {
                vSyncBp = vSyncRnd + minVBackPorch;
            }

            vBlank = vSyncBp + MIN_V_PORCH_RND;
            vFrontPorch = MIN_V_PORCH_RND;
            vBackPorch = vSyncBp - vSyncRnd;
            totalVLines = (int) Math.round(vLinesRnd + 2 * topMargin + vSyncBp + interlace + MIN_V_PORCH_RND);

            double idealDutyCycle = C_PRIME - M_PRIME * hPeriodEstUs / 1000;
            int cellGran2 = 2 * cellGranRnd;
            if (idealDutyCycle < 20) {
                hBlank = (int) Math.floor(totalActivePixels * 20.0 / 80.0 / cellGran2) * cellGran2;
            } else {
                hBlank = (int) Math.floor(totalActivePixels * idealDutyCycle / (100 - idealDutyCycle) / cellGran2) * cellGran2;
            }
            totalPixels = totalActivePixels + hBlank;

            hSync = (int) Math.floor(H_SYNC_PER * totalPixels / cellGranRnd) * cellGranRnd;
            hBackPorch = hBlank / 2;
            hFrontPorch = hBlank - hSync - hBackPorch;

            actPixelFreqMhz = clockStep * Math.floor(totalPixels / hPeriodEstUs / clockStep);
        } else {
            double hPeriodEstUs = (1_000_000.0 / vFieldRateRqd - rbMinVBlank) / (vLinesRnd + 2 * topMargin);
            hBlank = (int) rbHBlank;

            int vbiLines = (int) Math.floor(rbMinVBlank / hPeriodEstUs) + 1;
            int rbMinVbi = rbVFrontPorch + vSyncRnd + minVBackPorch;
            int actVbiLines = Math.max(vbiLines, rbMinVbi);

            totalVLines = (int) Math.round(actVbiLines + vLinesRnd + 2 * topMargin + interlace);
            totalPixels = (int) rbHBlank + totalActivePixels;

            actPixelFreqMhz = Math.floor(vFieldRateRqd * totalVLines * totalPixels * clockStepInv / 1_000_000.0)
                    * refreshMultiplier / clockStepInv;

            if (standard == Standard.CVT_RB2) {
                vBlank = actVbiLines;
                vFrontPorch = actVbiLines - vSyncRnd - 6;
                vBackPorch = 6;
                hSync = 32;
                hBackPorch = 40;
                hFrontPorch = hBlank - hSync - hBackPorch;
            } else {
                vBlank = actVbiLines;
                vFrontPorch = 3;
                vBackPorch = actVbiLines - vFrontPorch - vSyncRnd;
                hSync = 32;
                hBackPorch = 80;
                hFrontPorch = hBlank - hSync - hBackPorch;
            }
        }

        double pixelClockHz = actPixelFreqMhz * 1_000_000.0;
        double hFreqHz = pixelClockHz / totalPixels;
        double vFreqHz = pixelClockHz / ((double) totalVLines * totalPixels);
        double hPeriodUs = 1_000_000.0 / hFreqHz;
        double vPeriodMs = 1000.0 / vFreqHz;
        double vBlankDurationUs = vBlank * hPeriodUs;

        return new Timing(aspectRatio, pixelClockHz, totalPixels, totalActivePixels, hBlank,
                hFrontPorch, hSync, hBackPorch, hPolConst, hFreqHz, hPeriodUs,
                totalVLines, vLinesRnd, vBlank, vFrontPorch, vSyncRnd, vBackPorch, vPolConst,
                vFreqHz, vPeriodMs, vBlankDurationUs);
    }

    /** Оригинал домножает/делит промежуточный Math.round(...) на CELL_GRAN_RND —
     *  математически это взаимно уничтожается (x*8/8=x), реальное правило — точное
     *  совпадение округлённого до целого соотношения с H_PIXELS_RND, cellGranRnd
     *  здесь не участвует (сохранено в сигнатуре только чтобы звать без магических
     *  чисел на стороне вызова). */
    private static String aspectRatioOf(int verPixels, int hPixelsRnd, int cellGranRnd) {
        long h43 = Math.round(verPixels * 4.0 / 3.0);
        long h169 = Math.round(verPixels * 16.0 / 9.0);
        long h1610 = Math.round(verPixels * 16.0 / 10.0);
        long h54 = Math.round(verPixels * 5.0 / 4.0);
        long h159 = Math.round(verPixels * 15.0 / 9.0);
        long h4318 = Math.round(verPixels * 43.0 / 18.0);
        long h6427 = Math.round(verPixels * 64.0 / 27.0);
        long h125 = Math.round(verPixels * 12.0 / 5.0);
        if (h43 == hPixelsRnd) return "4:3";
        if (h169 == hPixelsRnd) return "16:9";
        if (h1610 == hPixelsRnd) return "16:10";
        if (h54 == hPixelsRnd) return "5:4";
        if (h159 == hPixelsRnd) return "15:9";
        if (h4318 == hPixelsRnd) return "43:18";
        if (h6427 == hPixelsRnd) return "64:27";
        if (h125 == hPixelsRnd) return "12:5";
        return "Unknown";
    }

    // ---- пропускная способность транспортов (DisplayPort/HDMI/DVI/SDI/RFC4175) ----

    /** Один стандарт передачи — максимальная полоса ПОСЛЕ вычета служебного
     *  кодирования (8b/10b, 128b/132b, 16b/18b) и, где обязательно, FEC —
     *  те же цифры, что в исходном калькуляторе (см. transports{} в его JS). */
    public record LinkStandard(String group, String name, double maxBps, boolean dscCapable) {
        public double maxMbit() {
            return maxBps / 1_000_000.0;
        }
    }

    private static final double ETHERNET_MTU1500 = 1500.0 / 1542;
    private static final double RFC4175_OVERHEAD = 20 + 8 + 12 + 13;
    private static final double RFC4175_FACTOR = ETHERNET_MTU1500 * (1500 - RFC4175_OVERHEAD) / 1500;

    public static final List<LinkStandard> LINK_STANDARDS = List.of(
            new LinkStandard("DisplayPort", "DisplayPort UBR20 (20 GHz)", 20_000_000_000.0 * 4 / 132 * 128 / 198 * 194, true),
            new LinkStandard("DisplayPort", "DisplayPort UBR13.5 (13.5 GHz)", 13_500_000_000.0 * 4 / 132 * 128 / 198 * 194, true),
            new LinkStandard("DisplayPort", "DisplayPort UBR10 (10 GHz)", 10_000_000_000.0 * 4 / 132 * 128 / 198 * 194, true),
            new LinkStandard("DisplayPort", "DisplayPort HBR3 (8.1 GHz)", 8_100_000_000.0 * 4 / 10 * 8, true),
            new LinkStandard("DisplayPort", "DisplayPort HBR2 (5.4 GHz)", 5_400_000_000.0 * 4 / 10 * 8, true),
            new LinkStandard("DisplayPort", "DisplayPort HBR (2.7 GHz)", 2_700_000_000.0 * 4 / 10 * 8, true),
            new LinkStandard("DisplayPort", "DisplayPort RBR (1.64 GHz)", 1_640_000_000.0 * 4 / 10 * 8, true),

            new LinkStandard("DisplayPort Type-C Alt Mode (2 линии)", "DP UBR20 Type-C Alt Mode (20 GHz)", 20_000_000_000.0 * 2 / 132 * 128 / 198 * 194, true),
            new LinkStandard("DisplayPort Type-C Alt Mode (2 линии)", "DP UBR13.5 Type-C Alt Mode (13.5 GHz)", 13_500_000_000.0 * 2 / 132 * 128 / 198 * 194, true),
            new LinkStandard("DisplayPort Type-C Alt Mode (2 линии)", "DP UBR10 Type-C Alt Mode (10 GHz)", 10_000_000_000.0 * 2 / 132 * 128 / 198 * 194, true),
            new LinkStandard("DisplayPort Type-C Alt Mode (2 линии)", "DP HBR3 Type-C Alt Mode (8.1 GHz)", 8_100_000_000.0 * 2 / 10 * 8, true),
            new LinkStandard("DisplayPort Type-C Alt Mode (2 линии)", "DP HBR2 Type-C Alt Mode (5.4 GHz)", 5_400_000_000.0 * 2 / 10 * 8, true),
            new LinkStandard("DisplayPort Type-C Alt Mode (2 линии)", "DP HBR Type-C Alt Mode (2.7 GHz)", 2_700_000_000.0 * 2 / 10 * 8, true),
            new LinkStandard("DisplayPort Type-C Alt Mode (2 линии)", "DP RBR Type-C Alt Mode (1.64 GHz)", 1_640_000_000.0 * 2 / 10 * 8, true),

            new LinkStandard("HDMI FRL", "HDMI 2.2 FRL12 (24 GHz/4 линии)", 24_000_000_000.0 * 4 / 18 * 16 / 544 * 514, true),
            new LinkStandard("HDMI FRL", "HDMI 2.2 FRL10 (20 GHz/4 линии)", 20_000_000_000.0 * 4 / 18 * 16 / 544 * 514, true),
            new LinkStandard("HDMI FRL", "HDMI 2.2 FRL8 (16 GHz/4 линии)", 16_000_000_000.0 * 4 / 18 * 16 / 544 * 514, true),
            new LinkStandard("HDMI FRL", "HDMI 2.1 FRL6 (12 GHz/4 линии)", 12_000_000_000.0 * 4 / 18 * 16 / 544 * 514, true),
            new LinkStandard("HDMI FRL", "HDMI 2.1 FRL5 (10 GHz/4 линии)", 10_000_000_000.0 * 4 / 18 * 16 / 544 * 514, true),
            new LinkStandard("HDMI FRL", "HDMI 2.1 FRL4 (8 GHz/4 линии)", 8_000_000_000.0 * 4 / 18 * 16 / 544 * 514, true),
            new LinkStandard("HDMI FRL", "HDMI 2.1 FRL3 (6 GHz/4 линии)", 6_000_000_000.0 * 4 / 18 * 16 / 544 * 514, true),
            new LinkStandard("HDMI FRL", "HDMI 2.1 FRL2 (6 GHz/3 линии)", 6_000_000_000.0 * 3 / 18 * 16 / 544 * 514, true),
            new LinkStandard("HDMI FRL", "HDMI 2.1 FRL1 (3 GHz/3 линии)", 3_000_000_000.0 * 3 / 18 * 16 / 544 * 514, true),

            new LinkStandard("HDMI TMDS", "HDMI 2.0 (600 MHz)", 600_000_000.0 * 24, false),
            new LinkStandard("HDMI TMDS", "HDMI 1.3/1.4 (340 MHz)", 340_000_000.0 * 24, false),
            new LinkStandard("HDMI TMDS", "HDMI 1.0/1.1/1.2 (165 MHz)", 165_000_000.0 * 24, false),

            new LinkStandard("DVI", "DVI-DL (двухканальный, 165 MHz)", 330_000_000.0 * 24, false),
            new LinkStandard("DVI", "DVI-D (одноканальный, 165 MHz)", 165_000_000.0 * 24, false),

            new LinkStandard("SDI", "12G-SDI", 11_880_000_000.0, false),
            new LinkStandard("SDI", "6G-SDI", 5_940_000_000.0, false),
            new LinkStandard("SDI", "3G-SDI", 2_970_000_000.0, false),
            new LinkStandard("SDI", "HD-SDI", 1_485_000_000.0, false),
            new LinkStandard("SDI", "SD-SDI", 270_000_000.0, false),

            new LinkStandard("RFC4175", "RFC4175 / 100M Ethernet", 100_000_000.0 * RFC4175_FACTOR, false),
            new LinkStandard("RFC4175", "RFC4175 / 1 GigE", 1_000_000_000.0 * RFC4175_FACTOR, false),
            new LinkStandard("RFC4175", "RFC4175 / 10 GigE", 10_000_000_000.0 * RFC4175_FACTOR, false)
    );

    public enum LinkStatus { OK, FAIL, DSC_OK, INVALID_8BPC_ONLY, INVALID_RGB_ONLY }

    public record LinkCheck(LinkStatus status, int percentOfMax, Double dscBpp) {
    }

    /** Требуемые DVI-специфичные ограничения (8 бит/компонент, RGB) сверяются
     *  первыми — то же самое исключение, что в оригинале (для DVI отдельного DSC
     *  fallback не существует, поэтому даже формально проходящий по полосе режим
     *  недоступен на DVI, если бит/компонент или формат цвета не подходят). */
    public static LinkCheck checkLink(LinkStandard std, Timing t, int bpc, ColorFormat colorFormat) {
        boolean isDvi = std.name().startsWith("DVI-");
        if (isDvi && bpc != 8) {
            return new LinkCheck(LinkStatus.INVALID_8BPC_ONLY, 0, null);
        }
        if (isDvi && colorFormat != ColorFormat.RGB_444) {
            return new LinkCheck(LinkStatus.INVALID_RGB_ONLY, 0, null);
        }
        Bandwidth bw = bandwidth(t, bpc, colorFormat);
        int percent = (int) Math.round(bw.peakBps() / std.maxBps() * 100);
        if (bw.peakBps() <= std.maxBps()) {
            return new LinkCheck(LinkStatus.OK, percent, null);
        }
        if (std.dscCapable() && bw.peakBpsDsc8bpp() <= std.maxBps()) {
            double dscBpp = Math.round(std.maxBps() / t.pixelClockHz() * 4) / 4.0;
            return new LinkCheck(LinkStatus.DSC_OK, percent, dscBpp);
        }
        return new LinkCheck(LinkStatus.FAIL, percent, null);
    }
}
