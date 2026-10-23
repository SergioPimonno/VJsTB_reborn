package com.vjstb.ledscheme.service.room;

import com.vjstb.ledscheme.model.RoomRackItem;
import com.vjstb.ledscheme.model.RoomSegment;
import com.vjstb.ledscheme.model.RoomTower;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Итоги одной башни: юниты, высота, вес, мощность и площадь на полу с учётом поворота
 * (прототип: {@code towerCalc}). Кофры считаются по их внешним размерам и юнитам, стопка без
 * кофра — по сумме высот предметов (площадь — по самому широкому и глубокому предмету).
 * Башня без размеров получает условную площадь 20×20 см, чтобы не вырождаться в точку.
 *
 * @param unitsTotal юниты всех кофров
 * @param unitsUsed  занятые юниты: ряды кофра, в которых есть блок (две половинки в одном ряду — один юнит;
 *                   в прототипе они считались дважды, из-за чего выходило «11/10U»)
 * @param widthCm    ширина по самому широкому слою до поворота
 * @param depthCm    глубина по самому глубокому слою до поворота
 * @param footW      ширина на плане с учётом поворота башни
 * @param footD      глубина на плане с учётом поворота башни
 * @param stackItems число предметов в стопках без кофра
 * @param items      блоки в кофрах: для проверок глубины и «тяжёлое сверху»
 */
public record RoomTowerCalc(RoomTower tower, int unitsTotal, int unitsUsed, double heightCm, double weightKg,
                            double powerW, double widthCm, double depthCm, double footW, double footD, int caseCount,
                            int stackItems, double stackHeightCm, List<Placed> items) {

    /** Блок в слоте кофра: позиция, кофр и положение по высоте башни ({@code upper} — в верхней половине). */
    public record Placed(RoomRackItem item, RoomItemSpec spec, RoomSegment segment, RoomItemSpec caseSpec,
                         double mid, boolean upper) {
    }

    public static RoomTowerCalc of(RoomTower t, RoomCatalog cat) {
        int unitsTotal = 0;
        int used = 0;
        int cases = 0;
        int stackItems = 0;
        double h = 0;
        double stackH = 0;
        double kg = 0;
        double p = 0;
        double w = 0;
        double d = 0;
        double off = 0;
        List<Placed> placed = new ArrayList<>();
        List<RoomSegment> top = new ArrayList<>(t.getSegments());
        Collections.reverse(top);
        for (RoomSegment seg : top) {
            if (seg.isStack()) {
                double segH = 0;
                for (RoomRackItem it : seg.getItems()) {
                    RoomItemSpec s = cat.resolve(it.getRef());
                    segH += s.heightCm();
                    kg += s.weightKg();
                    p += s.powerW();
                    w = Math.max(w, s.widthCm());
                    d = Math.max(d, s.depthCm());
                    stackItems++;
                }
                h += segH;
                stackH += segH;
                continue;
            }
            cases++;
            RoomItemSpec k = cat.resolve(seg.getCaseRef());
            unitsTotal += k.rackUnits();
            h += k.heightCm();
            w = Math.max(w, k.widthCm());
            d = Math.max(d, k.depthCm());
            kg += k.weightKg();
            boolean[] rows = new boolean[Math.max(0, k.rackUnits())];
            for (RoomRackItem it : seg.getItems()) {
                RoomItemSpec s = cat.resolve(it.getRef());
                for (int r = it.getU0(); r < it.getU0() + s.rackUnits() && r < rows.length; r++) {
                    if (r >= 0) {
                        rows[r] = true;
                    }
                }
                kg += s.weightKg();
                p += s.powerW();
                placed.add(new Placed(it, s, seg, k, off + it.getU0() + s.rackUnits() / 2.0, false));
            }
            for (boolean r : rows) {
                if (r) {
                    used++;
                }
            }
            off += k.rackUnits();
        }
        List<Placed> out = new ArrayList<>(placed.size());
        for (Placed x : placed) {
            out.add(new Placed(x.item(), x.spec(), x.segment(), x.caseSpec(), x.mid(), x.mid() < unitsTotal / 2.0));
        }
        double ww = w > 0 ? w : 20;
        double dd = d > 0 ? d : 20;
        boolean swap = t.getRotation() % 180 != 0;
        return new RoomTowerCalc(t, unitsTotal, used, h, kg, p, ww, dd, swap ? dd : ww, swap ? ww : dd, cases,
                stackItems, stackH, out);
    }
}
