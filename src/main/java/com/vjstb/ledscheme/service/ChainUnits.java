package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.PowerSupplyInstance;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Звенья цепочек расключения — ЕДИНСТВЕННЫЙ способ развернуть id из {@code SignalChain/PowerChain.cabinetInstanceIds}
 * (docs/receiver-cards/PLAN.md, D3, D24–D26, раздел 5). Звено — то, что можно положить в цепочку:
 * <ul>
 *   <li>обычный экран (без модулей) — ровно прежние кабинеты: {@code units(...)} возвращает все видимые ячейки в
 *   порядке списка экрана, регрессия обязана быть нулевой;</li>
 *   <li>модульный экран, сигнал — приёмные карты и обычные (не-модульные) кабинеты смешанного экрана;</li>
 *   <li>модульный экран, питание — блоки питания и обычные кабинеты. Модуль звеном не бывает никогда (D26): без
 *   карты/блока в цепочку не попадает.</li>
 * </ul>
 * Для звена считаются пиксели (карта — сумма модулей), ватты (блок — номинальная мощность {@code ratedPowerW}, D22),
 * центр-«якорь» для линии цепочки (центр карты / место блока / центр кабинета) и имя (К3 / БП2).
 *
 * <p>Объект строится на экран один раз на серию запросов. Перевод остальных 13 файлов, читающих
 * {@code getCabinetInstanceIds()}, на этот класс — отдельный трек D плана.
 */
public final class ChainUnits {

    public enum Mode {
        SIGNAL, POWER
    }

    public enum Kind {
        CABINET, RECEIVER_CARD, POWER_SUPPLY
    }

    /**
     * Звено цепочки.
     *
     * @param id       id для списка цепочки (кабинета / карты / блока)
     * @param name     «К3» / «БП2»; для кабинета {@code null}
     * @param cabinets ячейки звена: сам кабинет либо модули карты/блока (в порядке чтения)
     * @param card     карта, если {@code kind == RECEIVER_CARD}
     * @param supply   блок, если {@code kind == POWER_SUPPLY}
     */
    public record Unit(String id, Kind kind, Screen screen, String name, List<CabinetInstance> cabinets,
                       ReceiverCardInstance card, PowerSupplyInstance supply) {
    }

    private final Screen screen;
    private final Workspace workspace;
    private final ScreenModules sm;
    private final ReceiverCardNumbering numbering;
    private Map<String, Unit> byId;

    private ChainUnits(Screen screen, Workspace workspace, ReceiverCardNumbering numbering) {
        this.screen = screen;
        this.workspace = workspace;
        this.sm = ScreenModules.of(screen, workspace);
        this.numbering = numbering != null ? numbering : ReceiverCardNumbering.DEFAULT;
    }

    public static ChainUnits of(Screen screen, Workspace workspace) {
        return new ChainUnits(screen, workspace, ReceiverCardNumbering.DEFAULT);
    }

    /** С порядком имён К1…КN / БП1…БПn из Предпочтений (влияет на порядок звеньев в списке и на {@link #name}). */
    public static ChainUnits of(Screen screen, Workspace workspace, ReceiverCardNumbering numbering) {
        return new ChainUnits(screen, workspace, numbering);
    }

    /** D1: экран модульный — тогда звенья карты/блоки; иначе прежние кабинеты. */
    public boolean isModular() {
        return sm.isModular();
    }

    /** Все звенья, которые можно класть в цепочку этого режима: на модульном экране — карты (сигнал) или блоки
     *  (питание), затем обычные кабинеты; на обычном — все видимые кабинеты в порядке списка экрана. */
    public List<Unit> units(Mode mode) {
        List<Unit> out = new ArrayList<>();
        if (sm.isModular()) {
            if (mode == Mode.SIGNAL) {
                Map<String, String> names = UnitNames.cardNames(screen, numbering);
                for (ReceiverCardInstance c : UnitNames.orderedCards(screen, numbering)) {
                    Unit u = cardUnit(c, names.get(c.getId()));
                    if (u != null) {
                        out.add(u);
                    }
                }
            } else {
                Map<String, String> names = UnitNames.supplyNames(screen, numbering);
                for (PowerSupplyInstance s : UnitNames.orderedSupplies(screen, numbering)) {
                    Unit u = supplyUnit(s, names.get(s.getId()));
                    if (u != null) {
                        out.add(u);
                    }
                }
            }
        }
        for (CabinetInstance c : sm.ordinaryCabinets()) {
            out.add(cabinetUnit(c));
        }
        return out;
    }

    /** Звено по id (кабинет, карта или блок экрана); {@code null} — id неизвестен, ячейка скрыта или это модуль
     *  (модуль сам звеном не является). */
    public Unit unitOf(String id) {
        if (byId == null) {
            byId = new HashMap<>();
            for (Unit u : units(Mode.SIGNAL)) {
                byId.put(u.id(), u);
            }
            for (Unit u : units(Mode.POWER)) {
                byId.putIfAbsent(u.id(), u);
            }
        }
        return byId.get(id);
    }

    /** Звено по id среди всех экранов сцены (цепочка может идти через несколько экранов); {@code null} — не найдено. */
    public static Unit find(Scene scene, Workspace workspace, String id) {
        for (Screen s : scene.getScreens()) {
            if (s.cabinetById(id) != null || s.receiverCardById(id) != null || s.powerSupplyById(id) != null) {
                Unit u = ChainUnits.of(s, workspace).unitOf(id);
                if (u != null) {
                    return u;
                }
            }
        }
        return null;
    }

    /** Ячейки звена: для кабинета — он сам; для карты/блока — их модули. */
    public List<CabinetInstance> cabinetsOf(Unit unit) {
        return unit.cabinets();
    }

    /** Пиксели звена: кабинет — его разрешение; карта (и блок) — сумма разрешений модулей (D24). */
    public long pixels(Unit unit) {
        long sum = 0;
        for (CabinetInstance c : unit.cabinets()) {
            CabinetType t = sm.effectiveType(c);
            if (t != null) {
                sum += (long) t.getResolutionWidth() * t.getResolutionHeight();
            }
        }
        return sum;
    }

    /** Мощность звена, Вт: кабинет — потребление типа; блок — номинал {@code ratedPowerW} (D22, нагрузка фазы по
     *  блокам), а если он не указан — сумма потребления его модулей; карта — сумма потребления модулей. */
    public double watts(Unit unit) {
        if (unit.kind() == Kind.POWER_SUPPLY && unit.supply() != null && workspace != null
                && unit.supply().getPowerSupplyTypeId() != null) {
            PowerSupplyType t = workspace.powerSupplyTypeById(unit.supply().getPowerSupplyTypeId());
            if (t != null && t.getRatedPowerW() != null) {
                return t.getRatedPowerW();
            }
        }
        double sum = 0;
        for (CabinetInstance c : unit.cabinets()) {
            CabinetType t = sm.effectiveType(c);
            if (t != null) {
                sum += t.getPowerConsumptionW();
            }
        }
        return sum;
    }

    /** Точка звена для линии цепочки, мм (система экрана): центр кабинета; центр масс модулей карты; место блока. */
    public double[] anchor(Unit unit) {
        if (unit.kind() == Kind.POWER_SUPPLY && unit.supply() != null) {
            CabinetInstance place = sm.cabinet(UnitNames.placeOf(ChainLinks.indexOf(screen),
                    unit.supply().getModuleIds(), unit.supply().getHostModuleId()));
            if (place != null) {
                return sm.centerMm(place);
            }
        }
        double x = 0;
        double y = 0;
        int n = 0;
        for (CabinetInstance c : unit.cabinets()) {
            double[] p = sm.centerMm(c);
            x += p[0];
            y += p[1];
            n++;
        }
        return n == 0 ? new double[]{0, 0} : new double[]{x / n, y / n};
    }

    /** «К3» / «БП2» либо пустая строка для обычного кабинета. */
    public String name(Unit unit) {
        return unit.name() != null ? unit.name() : "";
    }

    // ------------------------------------------------------------------ целостность чтения

    /**
     * Отбрасывает из цепочек сцены id, которым не соответствует ни кабинет, ни карта, ни блок ни одного экрана
     * сцены (карта удалена, проект пересохранён старым клиентом без карт). Пустые после этого цепочки НЕ удаляются —
     * порт/фаза остаются за ними. Возвращает число отброшенных id.
     */
    public static int pruneUnknown(Scene scene) {
        Set<String> known = new HashSet<>();
        for (Screen s : scene.getScreens()) {
            for (CabinetInstance c : s.getCabinets()) {
                known.add(c.getId());
            }
            for (ReceiverCardInstance c : s.getReceiverCards()) {
                known.add(c.getId());
            }
            for (PowerSupplyInstance p : s.getPowerSupplies()) {
                known.add(p.getId());
            }
        }
        int removed = 0;
        for (SignalChain ch : scene.getSignalChains()) {
            int before = ch.getCabinetInstanceIds().size();
            ch.getCabinetInstanceIds().removeIf(id -> !known.contains(id));
            removed += before - ch.getCabinetInstanceIds().size();
        }
        for (PowerChain ch : scene.getPowerChains()) {
            int before = ch.getCabinetInstanceIds().size();
            ch.getCabinetInstanceIds().removeIf(id -> !known.contains(id));
            removed += before - ch.getCabinetInstanceIds().size();
        }
        return removed;
    }

    // ------------------------------------------------------------------ сборка звеньев

    private Unit cabinetUnit(CabinetInstance c) {
        return new Unit(c.getId(), Kind.CABINET, screen, null, List.of(c), null, null);
    }

    private Unit cardUnit(ReceiverCardInstance card, String name) {
        List<CabinetInstance> mods = modulesOf(card.getModuleIds());
        return mods.isEmpty() ? null : new Unit(card.getId(), Kind.RECEIVER_CARD, screen, name, mods, card, null);
    }

    private Unit supplyUnit(PowerSupplyInstance supply, String name) {
        List<CabinetInstance> mods = modulesOf(supply.getModuleIds());
        return mods.isEmpty() ? null : new Unit(supply.getId(), Kind.POWER_SUPPLY, screen, name, mods, null, supply);
    }

    private List<CabinetInstance> modulesOf(List<String> ids) {
        List<CabinetInstance> out = new ArrayList<>();
        for (String id : ids) {
            ScreenModules.Module m = sm.module(id);
            if (m != null) {
                out.add(m.cabinet());
            }
        }
        out.sort(ScreenModules.readingOrder());
        return out;
    }
}
