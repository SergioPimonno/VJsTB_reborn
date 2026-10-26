package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.ModuleNumberingPattern;
import com.vjstb.ledscheme.model.PowerSupplyInstance;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import com.vjstb.ledscheme.model.Screen;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Имена К1…КN (приёмные карты) и БП1…БПn (блоки питания) и «место» (модуль-хост) карты/блока
 * (docs/receiver-cards/PLAN.md, D11, D15, D20, D21).
 *
 * <p>Имена СКВОЗНЫЕ по экрану, без пропусков и НЕ хранятся: после любой правки (карту сняли, блок добавили) они
 * пересчитываются заново, а внутренний {@code id}, на котором держатся цепочки расключения, не меняется. Порядок
 * задаёт параметр Предпочтений {@link ReceiverCardNumbering} (стартовый угол × по строкам/столбцам либо «как нумерация
 * модулей экрана»): карту упорядочивает её «якорь» — модуль, получивший бы №1 при обходе от стартового угла; блок — его
 * место (одна ячейка, поэтому якорь очевиден). Тот же параметр нумерует и блоки — отдельного для них не заведено.
 */
public final class UnitNames {

    public static final String CARD_PREFIX = "К";
    public static final String SUPPLY_PREFIX = "БП";

    private UnitNames() {
    }

    // ------------------------------------------------------------------ место (хост)

    /**
     * Модуль, на котором «стоит» карта/блок: явно заданный {@code hostId}, если он среди модулей, иначе модуль,
     * ближайший к центру описанного прямоугольника (при равенстве — первый в порядке чтения, как в прототипе).
     * {@code null}, если у карты/блока нет ни одного существующего модуля.
     */
    public static String placeOf(Map<String, CabinetInstance> index, List<String> moduleIds, String hostId) {
        List<CabinetInstance> members = new ArrayList<>();
        for (String id : moduleIds) {
            CabinetInstance c = index.get(id);
            if (c != null && !c.isHidden()) {
                members.add(c);
            }
        }
        if (members.isEmpty()) {
            return null;
        }
        if (hostId != null) {
            for (CabinetInstance c : members) {
                if (c.getId().equals(hostId)) {
                    return hostId;
                }
            }
        }
        members.sort(ScreenModules.readingOrder());
        int r0 = Integer.MAX_VALUE;
        int c0 = Integer.MAX_VALUE;
        int r1 = Integer.MIN_VALUE;
        int c1 = Integer.MIN_VALUE;
        for (CabinetInstance c : members) {
            r0 = Math.min(r0, c.getRowIndex());
            r1 = Math.max(r1, c.getRowIndex());
            c0 = Math.min(c0, c.getColIndex());
            c1 = Math.max(c1, c.getColIndex());
        }
        double mr = (r0 + r1) / 2.0;
        double mc = (c0 + c1) / 2.0;
        CabinetInstance best = null;
        double bestD = Double.MAX_VALUE;
        for (CabinetInstance c : members) {
            double d = Math.hypot(c.getRowIndex() - mr, c.getColIndex() - mc);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best.getId();
    }

    public static String placeOf(Screen screen, ReceiverCardInstance card) {
        return placeOf(ScreenModules.index(screen), card.getModuleIds(), card.getHostModuleId());
    }

    public static String placeOf(Screen screen, PowerSupplyInstance supply) {
        return placeOf(ScreenModules.index(screen), supply.getModuleIds(), supply.getHostModuleId());
    }

    // ------------------------------------------------------------------ порядок и имена

    /** Карты экрана в порядке нумерации К1…КN. */
    public static List<ReceiverCardInstance> orderedCards(Screen screen, ReceiverCardNumbering numbering) {
        Map<String, CabinetInstance> idx = ScreenModules.index(screen);
        ModuleNumberingPattern p = resolved(screen, numbering);
        return sortByAnchor(screen.getReceiverCards(), card -> cardAnchor(card, idx, p), p);
    }

    /** Блоки питания экрана в порядке нумерации БП1…БПn (по месту блока). */
    public static List<PowerSupplyInstance> orderedSupplies(Screen screen, ReceiverCardNumbering numbering) {
        return orderedSupplies(screen, screen.getPowerSupplies(), ScreenModules.index(screen), numbering);
    }

    /** То же для произвольного списка блоков (пробные раскладки автоподбора, которых ещё нет в экране). */
    static List<PowerSupplyInstance> orderedSupplies(Screen screen, List<PowerSupplyInstance> supplies,
                                                     Map<String, CabinetInstance> idx,
                                                     ReceiverCardNumbering numbering) {
        ModuleNumberingPattern p = resolved(screen, numbering);
        return sortByAnchor(supplies, s -> {
            String place = placeOf(idx, s.getModuleIds(), s.getHostModuleId());
            CabinetInstance c = place != null ? idx.get(place) : null;
            return c != null ? new int[]{c.getRowIndex(), c.getColIndex()} : null;
        }, p);
    }

    /** {@code id карты → «К1»…}. Порядок обхода карты — порядок нумерации. */
    public static Map<String, String> cardNames(Screen screen, ReceiverCardNumbering numbering) {
        Map<String, String> out = new LinkedHashMap<>();
        int i = 1;
        for (ReceiverCardInstance c : orderedCards(screen, numbering)) {
            out.put(c.getId(), CARD_PREFIX + i++);
        }
        return out;
    }

    /** {@code id блока → «БП1»…}. */
    public static Map<String, String> supplyNames(Screen screen, ReceiverCardNumbering numbering) {
        return supplyNames(orderedSupplies(screen, numbering));
    }

    static Map<String, String> supplyNames(List<PowerSupplyInstance> ordered) {
        Map<String, String> out = new LinkedHashMap<>();
        int i = 1;
        for (PowerSupplyInstance s : ordered) {
            out.put(s.getId(), SUPPLY_PREFIX + i++);
        }
        return out;
    }

    private static ModuleNumberingPattern resolved(Screen screen, ReceiverCardNumbering numbering) {
        ReceiverCardNumbering n = numbering != null ? numbering : ReceiverCardNumbering.DEFAULT;
        return n.resolve(screen.getModuleNumbering());
    }

    /**
     * Якорь карты — её модуль, который был бы №1 при обходе от стартового угла (так «модуль в стартовом углу»
     * определён и для карты, чей описанный прямоугольник в этом углу пуст — Г-образной).
     */
    private static int[] cardAnchor(ReceiverCardInstance card, Map<String, CabinetInstance> idx,
                                    ModuleNumberingPattern p) {
        int r0 = Integer.MAX_VALUE;
        int c0 = Integer.MAX_VALUE;
        int r1 = Integer.MIN_VALUE;
        int c1 = Integer.MIN_VALUE;
        Map<Long, CabinetInstance> cells = new java.util.HashMap<>();
        for (String id : card.getModuleIds()) {
            CabinetInstance c = idx.get(id);
            if (c == null || c.isHidden()) {
                continue;
            }
            r0 = Math.min(r0, c.getRowIndex());
            r1 = Math.max(r1, c.getRowIndex());
            c0 = Math.min(c0, c.getColIndex());
            c1 = Math.max(c1, c.getColIndex());
            cells.put(((long) c.getRowIndex() << 32) | (c.getColIndex() & 0xFFFFFFFFL), c);
        }
        if (cells.isEmpty()) {
            return null;
        }
        for (int[] rc : ModuleNumbering.sequence(c1 - c0 + 1, r1 - r0 + 1, p)) {
            CabinetInstance c = cells.get(((long) (r0 + rc[0]) << 32) | ((c0 + rc[1]) & 0xFFFFFFFFL));
            if (c != null) {
                return new int[]{c.getRowIndex(), c.getColIndex()};
            }
        }
        return null;
    }

    private static <T> List<T> sortByAnchor(List<T> items, Function<T, int[]> anchorOf,
                                            ModuleNumberingPattern p) {
        boolean top = p.top();
        boolean left = p.left();
        boolean byRows = p.getAxis() == ModuleNumberingPattern.Axis.ROWS;
        record Keyed<T>(T item, long a, long b, boolean missing) {
        }
        List<Keyed<T>> keyed = new ArrayList<>();
        for (T item : items) {
            int[] rc = anchorOf.apply(item);
            if (rc == null) {
                keyed.add(new Keyed<>(item, 0, 0, true));
                continue;
            }
            long ar = top ? rc[0] : -rc[0];
            long ac = left ? rc[1] : -rc[1];
            keyed.add(byRows ? new Keyed<>(item, ar, ac, false) : new Keyed<>(item, ac, ar, false));
        }
        keyed.sort(Comparator.<Keyed<T>>comparingInt(k -> k.missing() ? 1 : 0)
                .thenComparingLong(Keyed::a).thenComparingLong(Keyed::b));
        List<T> out = new ArrayList<>(keyed.size());
        for (Keyed<T> k : keyed) {
            out.add(k.item());
        }
        return out;
    }
}
