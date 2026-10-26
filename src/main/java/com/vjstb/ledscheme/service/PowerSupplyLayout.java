package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.PowerSupplyInstance;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Раскладка выходов блоков питания (docs/receiver-cards/PLAN.md, D17–D21) — порт {@code psuList} прототипа
 * {@code prototype-flow.html}. Для каждого блока вычисляется, КТО на каком его выходе сидит:
 * <ol>
 *   <li>приёмные карты, чьё МЕСТО попало в область блока (D19: карта питается от своего блока): первая карта — на
 *   отдельном выходе «К», если у типа включён {@code cardOutput}; остальные (и все, если «К» нет) ПРИНУДИТЕЛЬНО
 *   забирают по одному шлейфу блока — могут перегрузить блок;</li>
 *   <li>затем модули блока в порядке чтения (строка за строкой) — каждый на своём шлейфе.</li>
 * </ol>
 * Шлейфы нумеруются с 1: сначала карты (кроме сидящей на «К»), затем модули. Перегруз = занято больше шлейфов, чем
 * {@code outputs} типа; «длинный» модуль — дальше {@code cableLengthMm} от места блока по прямой (D5). Числовые
 * параметры типа необязательны: {@code null} — не проверять.
 *
 * <p>Результат вычисляется, а не хранится (нумерация не должна расходиться с данными после любой правки), поэтому
 * вид, проверки и экспорт берут его одной функцией {@link #compute}.
 */
public final class PowerSupplyLayout {

    private PowerSupplyLayout() {
    }

    /** Карта на выходе блока: {@code dedicated} — на отдельном выходе «К»; иначе {@code output} — номер шлейфа. */
    public record CardFeed(String cardId, boolean dedicated, int output) {
        /** Подпись выхода: «К» или номер шлейфа. */
        public String label() {
            return dedicated ? "К" : Integer.toString(output);
        }
    }

    /** Модуль на шлейфе блока. */
    public record ModuleFeed(String moduleId, int output) {
    }

    /** Один блок питания со всем вычисленным. */
    public static final class Supply {
        private final PowerSupplyInstance instance;
        private final PowerSupplyType type;
        private String name;
        private String placeModuleId;
        private final List<CardFeed> cards = new ArrayList<>();
        private final List<ModuleFeed> modules = new ArrayList<>();
        private int used;
        private int over;
        private final List<String> longModuleIds = new ArrayList<>();

        Supply(PowerSupplyInstance instance, PowerSupplyType type) {
            this.instance = instance;
            this.type = type;
        }

        public PowerSupplyInstance instance() {
            return instance;
        }

        /** Тип из библиотеки; {@code null}, если тип пропал — проверки лимитов тогда пропускаются. */
        public PowerSupplyType type() {
            return type;
        }

        /** «БП1»… — сквозное имя по экрану. */
        public String name() {
            return name;
        }

        /** Id модуля, на котором стоит блок. */
        public String placeModuleId() {
            return placeModuleId;
        }

        /** Карты, которые питает этот блок, в порядке нумерации карт. */
        public List<CardFeed> cards() {
            return cards;
        }

        /** Модули на шлейфах в порядке чтения. */
        public List<ModuleFeed> modules() {
            return modules;
        }

        /** Занято шлейфов (без отдельного выхода «К»). */
        public int used() {
            return used;
        }

        /** Шлейфов в типе или {@code null}, если не указано. */
        public Integer outputs() {
            return type != null ? type.getOutputs() : null;
        }

        /** На сколько занятых шлейфов больше, чем выходов (0 — не перегружен или лимит не указан). */
        public int over() {
            return over;
        }

        /** Модули дальше допустимой длины кабеля от места блока. */
        public List<String> longModuleIds() {
            return longModuleIds;
        }

        /** Есть карта на отдельном выходе «К» — «+К» в списке. */
        public boolean hasDedicatedCard() {
            for (CardFeed c : cards) {
                if (c.dedicated()) {
                    return true;
                }
            }
            return false;
        }

        /** Блок питает хотя бы одну карту («+К» на плашке блока — D21). */
        public boolean feedsCards() {
            return !cards.isEmpty();
        }

        /** Есть ли проблема: перегруз или длинные кабели (красное в виде). */
        public boolean hasProblem() {
            return over > 0 || !longModuleIds.isEmpty();
        }

        /** Номер шлейфа модуля или 0, если блок его не питает. */
        public int outputOf(String moduleId) {
            for (ModuleFeed m : modules) {
                if (m.moduleId().equals(moduleId)) {
                    return m.output();
                }
            }
            return 0;
        }
    }

    /** Результат: блоки в порядке БП1…БПn и быстрые обратные поиски. */
    public static final class Result {
        private final List<Supply> supplies;
        private final Map<String, Supply> byModule = new HashMap<>();
        private final Map<String, Supply> byCard = new HashMap<>();
        private final Map<String, Supply> bySupplyId = new HashMap<>();

        Result(List<Supply> supplies) {
            this.supplies = supplies;
            for (Supply s : supplies) {
                bySupplyId.put(s.instance().getId(), s);
                for (ModuleFeed m : s.modules()) {
                    byModule.put(m.moduleId(), s);
                }
                for (CardFeed c : s.cards()) {
                    byCard.put(c.cardId(), s);
                }
            }
        }

        public List<Supply> supplies() {
            return supplies;
        }

        /** Блок, питающий модуль; {@code null} — «без питания». */
        public Supply ofModule(String moduleId) {
            return byModule.get(moduleId);
        }

        /** Блок, питающий карту (в области которого её место); {@code null} — у карты нет питания. */
        public Supply ofCard(String cardId) {
            return byCard.get(cardId);
        }

        public Supply ofSupply(String supplyId) {
            return bySupplyId.get(supplyId);
        }
    }

    public static Result compute(Screen screen, Workspace workspace, ReceiverCardNumbering numbering) {
        return compute(ScreenModules.of(screen, workspace), screen.getPowerSupplies(), numbering);
    }

    /**
     * То же для явного списка блоков — автоподбор пробует кандидатов-раскладки, которых в экране ещё нет. Карты и
     * геометрия берутся из {@code sm}.
     */
    static Result compute(ScreenModules sm, List<PowerSupplyInstance> supplies, ReceiverCardNumbering numbering) {
        Screen screen = sm.screen();
        Map<String, CabinetInstance> idx = ScreenModules.index(screen);
        List<PowerSupplyInstance> ordered = UnitNames.orderedSupplies(screen, supplies, idx, numbering);
        Map<String, String> names = UnitNames.supplyNames(ordered);

        List<Supply> list = new ArrayList<>(ordered.size());
        Map<String, Supply> supplyOfModule = new HashMap<>();
        for (PowerSupplyInstance inst : ordered) {
            PowerSupplyType type = sm.workspace() != null && inst.getPowerSupplyTypeId() != null
                    ? sm.workspace().powerSupplyTypeById(inst.getPowerSupplyTypeId()) : null;
            Supply s = new Supply(inst, type);
            s.name = names.get(inst.getId());
            s.placeModuleId = UnitNames.placeOf(idx, inst.getModuleIds(), inst.getHostModuleId());
            list.add(s);
            for (String id : inst.getModuleIds()) {
                supplyOfModule.putIfAbsent(id, s);
            }
        }

        // Карты — в порядке нумерации; каждая садится на блок, в области которого стоит её место (D19).
        Map<Supply, List<ReceiverCardInstance>> cardsBySupply = new LinkedHashMap<>();
        for (ReceiverCardInstance card : UnitNames.orderedCards(screen, numbering)) {
            String place = UnitNames.placeOf(idx, card.getModuleIds(), card.getHostModuleId());
            Supply own = place != null ? supplyOfModule.get(place) : null;
            if (own != null) {
                cardsBySupply.computeIfAbsent(own, k -> new ArrayList<>()).add(card);
            }
        }

        for (Supply s : list) {
            int o = 0;
            boolean dedicatedTaken = false;
            boolean hasDedicatedOutput = s.type != null && s.type.isCardOutput();
            for (ReceiverCardInstance card : cardsBySupply.getOrDefault(s, List.of())) {
                if (hasDedicatedOutput && !dedicatedTaken) {
                    dedicatedTaken = true;
                    s.cards.add(new CardFeed(card.getId(), true, 0));
                } else {
                    s.cards.add(new CardFeed(card.getId(), false, ++o));
                }
            }
            List<CabinetInstance> mods = new ArrayList<>();
            for (String id : s.instance.getModuleIds()) {
                CabinetInstance c = idx.get(id);
                if (c != null && !c.isHidden()) {
                    mods.add(c);
                }
            }
            mods.sort(ScreenModules.readingOrder());
            CabinetInstance place = s.placeModuleId != null ? idx.get(s.placeModuleId) : null;
            Double cable = s.type != null ? s.type.getCableLengthMm() : null;
            for (CabinetInstance c : mods) {
                s.modules.add(new ModuleFeed(c.getId(), ++o));
                if (cable != null && place != null && sm.distanceMm(place, c) > cable) {
                    s.longModuleIds.add(c.getId());
                }
            }
            s.used = o;
            Integer outputs = s.outputs();
            s.over = outputs != null ? Math.max(0, o - outputs) : 0;
        }
        return new Result(list);
    }
}
