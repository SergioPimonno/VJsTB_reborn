package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.StructureCupRemoval;
import com.vjstb.ledscheme.model.StructureFrameCell;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.model.Workspace;
import java.util.List;

/**
 * Вертикальная раскладка башни: высоты рам и стаканов (запрос пользователя 2026-10-22). Раньше рамы
 * стояли с шагом, равным высоте номинальной рамы, а стаканы рисовались кубиками на стыках без
 * собственной высоты. На деле нормальная рама 950 мм, стакан между рамами даёт ещё 50 — ровно метр на
 * модуль; бывает, что собирают без стаканов, тогда башня ниже. И в стойку можно поставить короткую
 * раму (850) — башня получается ниже ещё. Поэтому низ каждой рамы считается как СУММА реальных высот
 * рам ниже (у ячейки с переопределённым типом — высота этого типа) плюс стаканы на стыках, где они
 * есть; 3D, ведомость, перемычки и высота башни опираются на эти же функции.
 *
 * <p>Стык {@code (столб, ряд, seg)} — стакан между рамами {@code seg − 1} и {@code seg}. Стакан есть,
 * если стаканы на экране включены ({@link Screen#isStructureWithoutCups()} выключено) и стык не убран
 * ({@link Screen#getStructureRemovedCups()}); спрятанная рама своё место в стопке сохраняет — рамы
 * выше неё не «проваливаются» (как и раньше с шагом по номиналу).
 */
public final class StructureCupMath {

    private StructureCupMath() {
    }

    /** Высота стакана по умолчанию, мм (рама 950 + стакан 50 = 1000 мм). Из библиотеки берётся, если у
     *  записи CUP задан {@code heightMm} (как и у напольного каркаса, {@link FloorCalc#DEFAULT_CUP_GAP_MM}). */
    public static final double DEFAULT_CUP_HEIGHT_MM = 50;

    public static double cupHeightMm(Screen screen, Workspace workspace) {
        StructureFrameType cup = workspace != null && screen != null
                ? workspace.structureFrameTypeById(screen.getStructureCupTypeId()) : null;
        return cup != null && cup.getHeightMm() != null && cup.getHeightMm() > 0
                ? cup.getHeightMm() : DEFAULT_CUP_HEIGHT_MM;
    }

    public static boolean isRemoved(Screen screen, int post, int row, int seg) {
        for (StructureCupRemoval r : screen.getStructureRemovedCups()) {
            if (r.matches(post, row, seg)) {
                return true;
            }
        }
        return false;
    }

    /** На стыке перед рамой {@code seg} стоит стакан (стаканы включены и стык не убран). */
    public static boolean jointHasCup(Screen screen, int post, int row, int seg) {
        return seg >= 1 && !screen.isStructureWithoutCups() && !isRemoved(screen, post, row, seg);
    }

    /** Высота рамы ячейки {@code (столб, ряд, seg)}: у ячейки с переопределённым типом — высота
     *  этого типа, иначе номинальная {@code nominalH}. */
    public static double frameHeightMm(Screen screen, Workspace workspace, int post, int row, int seg,
            double nominalH) {
        for (StructureFrameCell c : screen.getStructureFrameCells()) {
            if (c.matches(post, row, seg) && c.getFrameTypeId() != null && workspace != null) {
                StructureFrameType t = workspace.structureFrameTypeById(c.getFrameTypeId());
                if (t != null && t.getHeightMm() != null && t.getHeightMm() > 0) {
                    return t.getHeightMm();
                }
            }
        }
        return nominalH;
    }

    /** Низ рамы {@code seg}: сумма высот рам {@code 0..seg−1} + стаканы на стыках {@code 1..seg}. */
    public static double frameBottomY(Screen screen, Workspace workspace, int post, int row, int seg,
            double nominalH) {
        if (seg <= 0) {
            return seg * nominalH;
        }
        double cup = cupHeightMm(screen, workspace);
        double y = 0;
        for (int j = 0; j < seg; j++) {
            y += frameHeightMm(screen, workspace, post, row, j, nominalH);
        }
        for (int j = 1; j <= seg; j++) {
            if (jointHasCup(screen, post, row, j)) {
                y += cup;
            }
        }
        return y;
    }

    /** Точка на высоте {@code y0} номинальной стопки (перекладина, к которой крепится перемычка) →
     *  та же перекладина в реальной стопке столба: рама номинальной стопки, в которую попала {@code y0},
     *  берётся со своим реальным низом, смещение внутри рамы сохраняется. */
    public static double rungY(Screen screen, Workspace workspace, int post, int row, double y0,
            double nominalH) {
        if (nominalH <= 0) {
            return y0;
        }
        int f = (int) Math.floor(y0 / nominalH + 1e-9);
        double offset = y0 - f * nominalH;
        return frameBottomY(screen, workspace, post, row, f, nominalH) + offset;
    }

    /** Собранная высота башни по переднему ряду: верх самой высокой НЕ спрятанной рамы столба. 0 —
     *  рам нет. */
    public static double assembledHeightMm(Screen screen, Workspace workspace, double nominalH) {
        double best = 0;
        for (StructureFrameCell c : screen.getStructureFrameCells()) {
            if (c.isHidden() || c.getRow() != 0) {
                continue;
            }
            double top = frameBottomY(screen, workspace, c.getTowerIndex(), 0, c.getSegmentIndex(), nominalH)
                    + frameHeightMm(screen, workspace, c.getTowerIndex(), 0, c.getSegmentIndex(), nominalH);
            best = Math.max(best, top);
        }
        return best;
    }

    /** Толщина перекладины вертикальной рамы ширины {@code frameW}: 0,7 толщины рельса (рельс — {@code
     *  min(max(20, min(70, 12% ширины)), 45% ширины)}, как в 3D). */
    public static double crossbarThicknessMm(double frameW) {
        double rail = Math.min(Math.max(20, Math.min(70, frameW * 0.12)), frameW * 0.45);
        return rail * 0.7;
    }

    /** Центр перекладины {@code rung} (рама·3 + 0 низ / 1 середина / 2 верх) в стопке столба {@code post}
     *  ряда {@code row}: низ рамы {@code seg} + смещение перекладины внутри рамы её реальной высоты. */
    public static double rungCenterY(Screen screen, Workspace workspace, int post, int row, int rung,
            double nominalH, double crossT) {
        int seg = Math.floorDiv(rung, 3);
        int r = Math.floorMod(rung, 3);
        double h = frameHeightMm(screen, workspace, post, row, seg, nominalH);
        double bottom = frameBottomY(screen, workspace, post, row, seg, nominalH);
        return bottom + (r == 0 ? crossT / 2.0 : r == 1 ? h / 2.0 : h - crossT / 2.0);
    }

    /** Перекладины АВТО-расстановки перемычек (запрос 2026-10-22: «по умолчанию — на интервале из
     *  авторасчёта (1500), а фактически — на любой перекладине»): для высот {@code k·interval} (k = 1…) в
     *  пределах стопки {@code segments} рам (номинальная рама {@code nominalH}, стакан {@code cupH} между
     *  рамами — 0, если без стаканов) берётся ближайшая по высоте перекладина (при равенстве — нижняя),
     *  не больше {@code maxLevels} штук и не выше верха стопки (с допуском 10% интервала). Дубли
     *  отбрасываются. */
    public static List<Integer> autoRungs(double nominalH, double cupH, double crossT, int segments,
            double intervalMm, int maxLevels) {
        List<Integer> rungs = new java.util.ArrayList<>();
        if (segments <= 0 || intervalMm <= 0 || nominalH <= 0 || maxLevels <= 0) {
            return rungs;
        }
        double top = segments * nominalH + (segments - 1) * cupH;
        // допуск 10% интервала выше верха стопки: на 3 рамах (2950) уровень 3000 — верхняя перекладина,
        // а не «выше башни»
        for (int k = 1; k <= maxLevels; k++) {
            double target = k * intervalMm;
            if (target > top + 0.1 * intervalMm + 1e-9) {
                break;
            }
            int best = -1;
            double bestDist = Double.POSITIVE_INFINITY;
            for (int seg = 0; seg < segments; seg++) {
                double bottom = seg * (nominalH + cupH);
                double[] centers = {bottom + crossT / 2.0, bottom + nominalH / 2.0, bottom + nominalH - crossT / 2.0};
                for (int r = 0; r < 3; r++) {
                    double d = Math.abs(centers[r] - target);
                    if (d < bestDist - 1e-9) {
                        bestDist = d;
                        best = seg * 3 + r;
                    }
                }
            }
            if (best >= 0 && !rungs.contains(best)) {
                rungs.add(best);
            }
        }
        return rungs;
    }

    /** Собранная высота ОДНОЙ башни (столбы {@code 2·tower}/{@code 2·tower+1}): верх самой высокой не спрятанной
     *  рамы стопки; рам нет — введённая высота башни экрана. */
    public static double towerHeightMm(Screen screen, Workspace workspace, int tower) {
        StructureFrameType nominal = workspace != null
                ? workspace.structureFrameTypeById(screen.getStructureFrameTypeId()) : null;
        double nominalH = nominal != null && nominal.getHeightMm() != null && nominal.getHeightMm() > 0
                ? nominal.getHeightMm() : 950;
        double best = 0;
        for (StructureFrameCell c : screen.getStructureFrameCells()) {
            if (c.isHidden() || c.getRow() > 1 || Math.floorDiv(c.getTowerIndex(), 2) != tower) {
                continue;
            }
            best = Math.max(best, frameBottomY(screen, workspace, c.getTowerIndex(), c.getRow(), c.getSegmentIndex(),
                    nominalH) + frameHeightMm(screen, workspace, c.getTowerIndex(), c.getRow(), c.getSegmentIndex(),
                    nominalH));
        }
        return best > 0 ? best : Math.max(1000, screen.getStructureTowerHeightMm());
    }

    /** Число стыков со стаканом: обе рамы стыка есть (не спрятаны) и стакан не убран. */
    public static int presentJoints(Screen screen) {
        int joints = 0;
        for (StructureFrameCell c : screen.getStructureFrameCells()) {
            if (c.isHidden() || c.getRow() >= 2 || c.getSegmentIndex() < 1
                    || !jointHasCup(screen, c.getTowerIndex(), c.getRow(), c.getSegmentIndex())) {
                continue;
            }
            boolean below = false;
            for (StructureFrameCell o : screen.getStructureFrameCells()) {
                if (!o.isHidden() && o.matches(c.getTowerIndex(), c.getRow(), c.getSegmentIndex() - 1)) {
                    below = true;
                    break;
                }
            }
            if (below) {
                joints++;
            }
        }
        return joints;
    }

    /** Короткая рама, которой можно добить башню сверху: первая запись библиотеки вида {@code
     *  SHORT_FRAME} с заданной высотой (в библиотеке пользователя — 850 мм) или {@code null}. */
    public static StructureFrameType shortTopType(List<StructureFrameType> library) {
        for (StructureFrameType t : library) {
            if (t.getKind() == StructureFrameType.Kind.SHORT_FRAME && t.getHeightMm() != null
                    && t.getHeightMm() > 0) {
                return t;
            }
        }
        return null;
    }
}
