package com.vjstb.ledscheme.service.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomLooseItem;
import com.vjstb.ledscheme.model.RoomRackItem;
import com.vjstb.ledscheme.model.RoomRun;
import com.vjstb.ledscheme.model.RoomSegment;
import com.vjstb.ledscheme.model.RoomTower;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Текст спецификации раскладки для копирования («Копировать спецификацию», порт {@code specText()}
 * прототипа): итоги, по башням содержимое кофров сверху вниз, пол из станков, мониторы, мелочь по
 * трассам и кейс мелочёвки. Чистая функция от раскладки, каталога и результата расчёта.
 */
public final class RoomSpecText {

    private RoomSpecText() {
    }

    public static String of(ControlRoomLayout layout, RoomCatalog cat, RoomPlanCalc.Result calc) {
        List<String> out = new ArrayList<>();
        RoomPlanCalc.Totals tot = calc.totals();
        out.add("ПЛАННЕР ПУЛЬТОВОЙ · площадка " + RoomUnits.num(layout.getRoomWidthCm() / 100, 1) + " × "
                + RoomUnits.num(layout.getRoomDepthCm() / 100, 1) + " м");
        out.add("Итого: " + tot.towers() + " башн., " + tot.unitsUsed() + "/" + tot.unitsTotal() + "U, " + tot.cases()
                + " кофр., " + RoomUnits.num(tot.kg(), 0) + " кг, " + RoomUnits.num(tot.powerW() / 1000, 2) + " кВт ("
                + RoomUnits.num(tot.powerW() / 230, 1) + " А)");
        for (RoomTowerCalc c : calc.towers()) {
            RoomTower t = c.tower();
            out.add("");
            out.add("Башня " + t.getTag() + " — " + t.getName() + ": " + c.unitsTotal() + "U, "
                    + RoomUnits.num(c.heightCm(), 0) + " см высота, " + RoomUnits.num(c.widthCm(), 0) + "×"
                    + RoomUnits.num(c.depthCm(), 0) + " см, " + RoomUnits.num(c.weightKg(), 0) + " кг");
            List<RoomSegment> top = new ArrayList<>(t.getSegments());
            Collections.reverse(top);
            for (RoomSegment seg : top) {
                if (seg.isStack()) {
                    out.add("  Без кофра, стопкой (сверху вниз):");
                    List<RoomRackItem> items = new ArrayList<>(seg.getItems());
                    Collections.reverse(items);
                    for (RoomRackItem it : items) {
                        RoomItemSpec s = cat.resolve(it.getRef());
                        out.add("    " + s.name() + "  (" + dim(s) + " см)");
                    }
                    continue;
                }
                RoomItemSpec k = cat.resolve(seg.getCaseRef());
                out.add("  Кофр " + k.rackUnits() + "U (снаружи " + dim(k) + " см)");
                List<RoomRackItem> items = new ArrayList<>(seg.getItems());
                items.sort(Comparator.comparingInt(RoomRackItem::getU0));
                for (RoomRackItem it : items) {
                    RoomItemSpec s = cat.resolve(it.getRef());
                    String rows = "U" + (it.getU0() + 1) + (s.rackUnits() > 1 ? "–" + (it.getU0() + s.rackUnits()) : "");
                    String frac = fraction(s.widthTwelfths());
                    out.add("    " + rows + "  " + s.name() + "  (" + s.rackUnits() + "U" + (frac.isEmpty() ? "" : " " + frac)
                            + ", гл. " + RoomUnits.num(s.depthCm(), 1) + " см)");
                }
            }
        }
        deckLines(layout, cat, calc, out);
        monitorLines(layout, cat, out);
        deviceLines(layout, cat, out);
        out.add("");
        out.add("Мелочь:");
        for (RoomRun r : layout.getRuns()) {
            List<RoomLooseItem> onRun = new ArrayList<>();
            for (RoomLooseItem l : layout.getLoose()) {
                if (l.getMode() == RoomLooseItem.Mode.RUN && r.getId().equals(l.getRunId())) {
                    onRun.add(l);
                }
            }
            onRun.sort(Comparator.comparingDouble(l -> l.getSCm() != null ? l.getSCm() : 0));
            for (RoomLooseItem l : onRun) {
                out.add("  " + r.getName() + ", " + RoomUnits.num((l.getSCm() != null ? l.getSCm() : 0) / 100, 1)
                        + " м от ввода: " + cat.resolve(l.getRef()).name());
            }
        }
        for (RoomLooseItem l : layout.getLoose()) {
            if (l.getMode() == RoomLooseItem.Mode.FREE) {
                out.add("  свободно: " + cat.resolve(l.getRef()).name());
            }
        }
        for (RoomLooseItem l : layout.getLoose()) {
            if (l.getMode() == RoomLooseItem.Mode.BOX) {
                out.add("  в кофре: " + cat.resolve(l.getRef()).name());
            }
        }
        RoomPlanCalc.BoxCalc box = calc.box();
        if (box.count() > 0) {
            out.add("  Кейс мелочёвки: " + (box.pick() != null
                    ? (int) box.pick()[0] + "×" + (int) box.pick()[1] + "×" + (int) box.pick()[2] + " см"
                    : "не помещается в 80×50×30"));
        }
        return String.join("\n", out);
    }

    private static void deckLines(ControlRoomLayout layout, RoomCatalog cat, RoomPlanCalc.Result calc, List<String> out) {
        Map<String, Integer> decks = new LinkedHashMap<>();
        for (RoomFurniture f : layout.getFurniture()) {
            RoomItemSpec s = RoomGeometry.effective(f, cat);
            if (s.type() == RoomItemSpec.Type.DECK) {
                decks.merge(s.name() + " · выс. " + RoomUnits.num(s.heightCm(), 0) + " см", 1, Integer::sum);
            }
        }
        RoomGeometry.DeckInfo di = calc.totals().deck();
        if (di != null && di.count() > 0) {
            out.add("");
            out.add("Пол из станков: " + RoomUnits.num(di.widthCm() / 100, 1) + " × " + RoomUnits.num(di.depthCm() / 100, 1)
                    + " м, " + RoomUnits.num(di.areaM2(), 1) + " м², " + di.count() + " шт.");
            decks.forEach((k, n) -> out.add("  " + n + " × " + k));
        }
    }

    /** Отдельно стоящее оборудование («Установка → Отдельно»): пульты, ноутбуки и т. п. */
    private static void deviceLines(ControlRoomLayout layout, RoomCatalog cat, List<String> out) {
        Map<String, Integer> dev = new LinkedHashMap<>();
        for (RoomFurniture f : layout.getFurniture()) {
            RoomItemSpec s = RoomGeometry.effective(f, cat);
            if (s.type() == RoomItemSpec.Type.DEVICE) {
                dev.merge(s.name(), 1, Integer::sum);
            }
        }
        if (!dev.isEmpty()) {
            out.add("");
            out.add("Оборудование отдельно:");
            dev.forEach((k, n) -> out.add("  " + n + " × " + k));
        }
    }

    private static void monitorLines(ControlRoomLayout layout, RoomCatalog cat, List<String> out) {
        Map<String, Integer> mon = new LinkedHashMap<>();
        for (RoomFurniture f : layout.getFurniture()) {
            RoomItemSpec s = RoomGeometry.effective(f, cat);
            if (s.type() == RoomItemSpec.Type.MONITOR) {
                mon.merge(s.name(), 1, Integer::sum);
            }
        }
        if (!mon.isEmpty()) {
            out.add("");
            out.add("Мониторы:");
            mon.forEach((k, n) -> out.add("  " + n + " × " + k));
        }
    }

    /** Размеры «ширина×высота×глубина», см. */
    static String dim(RoomItemSpec s) {
        return RoomUnits.num(s.widthCm(), 1) + "×" + RoomUnits.num(s.heightCm(), 1) + "×" + RoomUnits.num(s.depthCm(), 1);
    }

    /** Доля ширины стойки словами: 12/12 — пусто. */
    static String fraction(int twelfths) {
        return switch (twelfths) {
            case 6 -> "1/2";
            case 4 -> "1/3";
            case 3 -> "1/4";
            default -> "";
        };
    }
}
