package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenGroup;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Конструктив ГРУППЫ экранов (запрос пользователя 2026-10-22): экраны объединены в {@link
 * ScreenGroup} и у всех способ монтажа — наземный конструктив, тогда конструктив собирается для
 * группы целиком: экраны стоят в общем плане под углом и со смещением друг к другу (поза экрана —
 * {@link Screen#getStructurePoseXMm()}, {@code StructureLayoutMath}), башни — свои у каждого экрана
 * и едут вместе с ним.
 *
 * <p>Железо группы — СУММА расчётов экранов ({@link StructureCalc#compute}): рамы, стаканы, болты и
 * балласт считаются по ячейкам и весу каждого экрана, как и раньше; башня на стыке двух экранов
 * принадлежит одному из них (владельца выбирает инженер). Крепление между башнями (стяжки) по
 * умолчанию НЕ считается — по решению пользователя это будет отдельная пометка. К предупреждениям
 * экранов добавляются предупреждения группы ({@link StructureLayoutMath#analyzeGroup}):
 * пересечения башен разных экранов и вход башни в объём соседнего экрана.
 */
public final class StructureGroupCalc {

    private StructureGroupCalc() {
    }

    /** Итог группы: {@code perScreen} — расчёт каждого экрана (в порядке сцены, ключ — id экрана),
     *  {@code total} — сумма железа, {@code groupWarnings} — предупреждения именно ГРУППЫ (без
     *  предупреждений отдельных экранов, они в {@code perScreen}). */
    public record Result(Map<String, StructureCalc.Result> perScreen, StructureCalc.Result total,
                         List<String> groupWarnings, int tieContacts, int tieCount) {

        public Result {
            perScreen = java.util.Collections.unmodifiableMap(perScreen);
            groupWarnings = List.copyOf(groupWarnings);
        }
    }

    /** Экраны группы {@code group} сцены {@code scene} в порядке сцены. */
    public static List<Screen> members(Scene scene, ScreenGroup group) {
        return scene.screensOf(group);
    }

    /** Экраны, которые показывает 3D-редактор конструктива для текущего экрана: вся его группа, если
     *  в ней больше одного экрана и конструктив группы возможен ({@link #eligible}), иначе только
     *  сам экран. Порядок — порядок сцены; текущий экран всегда в списке. */
    public static List<Screen> viewScreens(Scene scene, Screen current) {
        List<Screen> out = new ArrayList<>();
        if (current == null) {
            return out;
        }
        ScreenGroup group = scene != null ? scene.groupOf(current) : null;
        if (group != null && eligible(scene, group)) {
            List<Screen> members = members(scene, group);
            if (members.size() > 1 && members.contains(current)) {
                return members;
            }
        }
        out.add(current);
        return out;
    }

    /** Текст спецификации группы для окна «Спецификация группы»: по строке на экран (рамы, стаканы,
     *  болты, балласт), итог по группе, предупреждения экранов и группы, пометка про то, что не
     *  считается. Без Swing — проверяется тестом. */
    public static String specText(String groupName, List<Screen> screens, Result result) {
        StringBuilder msg = new StringBuilder();
        msg.append("Спецификация конструктива — группа «").append(groupName).append("» (")
                .append(screens.size()).append(" экр.)\n\n");
        for (Screen s : screens) {
            StructureCalc.Result r = result.perScreen().get(s.getId());
            if (r == null) {
                continue;
            }
            msg.append(String.format("«%s»: рам %d, стаканов %d, болтов %d", s.getName(), r.totalFrameCount(),
                    r.cupCount(), r.boltCount()));
            if (r.requiredBallastKg() > 0) {
                msg.append(String.format(", балласт ≈%.1f кг (%d отгр.)", r.requiredBallastKg(),
                        r.ballastContainerCount()));
            }
            msg.append('\n');
        }
        StructureCalc.Result t = result.total();
        msg.append(String.format("%nИтого по группе:%nРам: %d (вертикальных %d, перемычек %d, секций базы %d)%n",
                t.totalFrameCount(), t.verticalFrameCount(), t.peremychkaCount(), t.baseFrameCount()));
        msg.append(String.format("Стаканов: %d%nБолтов: %d%n", t.cupCount(), t.boltCount()));
        if (t.requiredBallastKg() > 0) {
            msg.append(String.format("Отгрузов: %d (≈%.1f кг балласта)%n", t.ballastContainerCount(),
                    t.requiredBallastKg()));
        }
        for (String w : t.curveWarnings()) {
            msg.append("\nВНИМАНИЕ: ").append(w).append('\n');
        }
        for (String w : result.groupWarnings()) {
            msg.append("\nВНИМАНИЕ: ").append(w).append('\n');
        }
        if (result.tieContacts() > 0) {
            msg.append(String.format("%nСтяжек между встык стоящими башнями: %d (%d стыков)%n", result.tieCount(),
                    result.tieContacts()));
        } else {
            msg.append("\nКрепление между башнями (стяжки) не считается — включается пометкой «Считать стяжки» у экрана.");
        }
        msg.append("\nТребует независимой инженерной перепроверки перед монтажом — см. STRUCTURE_CALC_NOTES.md.");
        return msg.toString();
    }

    /** Конструктив группы возможен: в группе есть экраны и у ВСЕХ способ монтажа — наземный
     *  конструктив (иначе сводить нечего: подвес, ферма и пол считаются по-своему). */
    public static boolean eligible(Scene scene, ScreenGroup group) {
        List<Screen> members = members(scene, group);
        if (members.isEmpty()) {
            return false;
        }
        for (Screen s : members) {
            if (s.getMountType() != ScreenMountType.STRUCTURE) {
                return false;
            }
        }
        return true;
    }

    /** Расчёт группы: сумма экранов + предупреждения группы. {@code typeOf} — тип кабинета экрана
     *  (обычно {@code model::typeOf}). Пустой список экранов даёт нулевой итог. */
    public static Result compute(List<Screen> screens, Function<Screen, CabinetType> typeOf,
            Workspace workspace) {
        Map<String, StructureCalc.Result> per = new LinkedHashMap<>();
        int vertical = 0;
        int peremychka = 0;
        int base = 0;
        int total = 0;
        int cups = 0;
        int bolts = 0;
        double ballastKg = 0;
        int containers = 0;
        double maxHeight = 0;
        boolean unsafe = false;
        boolean aboveScreen = false;
        List<String> screenWarnings = new ArrayList<>();
        for (Screen s : screens) {
            StructureCalc.Result r = StructureCalc.compute(s, typeOf.apply(s), workspace);
            per.put(s.getId(), r);
            vertical += r.verticalFrameCount();
            peremychka += r.peremychkaCount();
            base += r.baseFrameCount();
            total += r.totalFrameCount();
            cups += r.cupCount();
            bolts += r.boltCount();
            ballastKg += r.requiredBallastKg();
            containers += r.ballastContainerCount();
            maxHeight = Math.max(maxHeight, r.totalTowerHeightMm());
            unsafe |= r.exceedsSafeHeightWarning();
            aboveScreen |= r.exceedsScreenHeightWarning();
            for (String w : r.curveWarnings()) {
                screenWarnings.add("«" + s.getName() + "»: " + w);
            }
        }
        StructureCalc.Result sum = new StructureCalc.Result(vertical, peremychka, base, total, cups, bolts,
                ballastKg, containers, maxHeight, unsafe, aboveScreen, screenWarnings);
        List<String> groupWarnings = StructureLayoutMath.analyzeGroup(screens, typeOf, workspace);
        return new Result(per, sum, groupWarnings, StructureLayoutMath.tieContacts(screens, typeOf, workspace),
                StructureLayoutMath.tieCount(screens, typeOf, workspace));
    }
}
