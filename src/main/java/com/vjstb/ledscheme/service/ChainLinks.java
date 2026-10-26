package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.PowerSupplyInstance;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Лёгкие поиски по id звена цепочки без разбора типов (docs/receiver-cards/PLAN.md, раздел 7, трек D): в
 * {@code SignalChain/PowerChain.cabinetInstanceIds} лежат id КАБИНЕТОВ, приёмных карт или блоков питания (UUID не
 * пересекаются), и десятки мест кода, раньше делавших {@code screen.cabinetById(id)}, теперь спрашивают здесь.
 *
 * <p>Главное свойство для регрессии: на экране без карт и блоков {@code receiverCardById/powerSupplyById} всегда
 * {@code null}, поэтому каждый метод сводится к прежнему {@code cabinetById} — поведение немодульных проектов не
 * меняется. Полный разбор звена (пиксели, ватты, имя) — {@link ChainUnits}; здесь только принадлежность экрану,
 * ячейки звена и перевод «клик по ячейке → звено».
 */
public final class ChainLinks {

    private ChainLinks() {
    }

    // ------------------------------------------------------------------ индекс на время отрисовки

    private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();

    /**
     * Индексы экрана на время одной отрисовки. Баг-репорт: «Расключение экрана» на экране из ~3000 модулей и ~400
     * карт тормозило — отрисовка на каждый модуль перебирала все карты ({@link #unitContaining}), а на каждое
     * звено цепочки линейно искала ячейку ({@code Screen.cabinetById}) и заново строила индекс экрана. Пока открыт
     * {@code Scope}, эти поиски идут по словарям, собранным один раз; модель за время отрисовки не меняется (она идёт
     * на потоке Swing), поэтому устареть словарь не может. Вне {@code Scope} всё работает как раньше.
     */
    public static final class Scope implements AutoCloseable {
        private final Screen screen;
        private final Scope previous;
        private Map<String, CabinetInstance> index;
        private Map<String, String> cardOfModule;
        private Map<String, String> supplyOfModule;

        private Scope(Screen screen, Scope previous) {
            this.screen = screen;
            this.previous = previous;
        }

        Map<String, CabinetInstance> index() {
            if (index == null) {
                index = ScreenModules.index(screen);
            }
            return index;
        }

        String unitOf(String cabinetId, ChainUnits.Mode mode) {
            if (mode == ChainUnits.Mode.SIGNAL) {
                if (cardOfModule == null) {
                    cardOfModule = new HashMap<>();
                    for (ReceiverCardInstance c : screen.getReceiverCards()) {
                        for (String m : c.getModuleIds()) {
                            cardOfModule.putIfAbsent(m, c.getId());
                        }
                    }
                }
                return cardOfModule.get(cabinetId);
            }
            if (supplyOfModule == null) {
                supplyOfModule = new HashMap<>();
                for (PowerSupplyInstance p : screen.getPowerSupplies()) {
                    for (String m : p.getModuleIds()) {
                        supplyOfModule.putIfAbsent(m, p.getId());
                    }
                }
            }
            return supplyOfModule.get(cabinetId);
        }

        @Override
        public void close() {
            if (previous == null) {
                SCOPE.remove();
            } else {
                SCOPE.set(previous);
            }
        }
    }

    /** Открывает {@link Scope} для экрана; закрывать в {@code finally} / try-with-resources. */
    public static Scope scope(Screen screen) {
        Scope s = new Scope(screen, SCOPE.get());
        SCOPE.set(s);
        return s;
    }

    private static Scope scopeOf(Screen screen) {
        Scope s = SCOPE.get();
        return s != null && s.screen == screen ? s : null;
    }

    /** {@code screen.cabinetById(id)}, но по словарю, если открыт {@link Scope} этого экрана. */
    public static CabinetInstance cabinet(Screen screen, String id) {
        Scope sc = scopeOf(screen);
        return sc != null ? sc.index().get(id) : screen.cabinetById(id);
    }

    /** Индекс «id → ячейка» экрана: общий из {@link Scope}, если он открыт, иначе новый. */
    public static Map<String, CabinetInstance> indexOf(Screen screen) {
        Scope sc = scopeOf(screen);
        return sc != null ? sc.index() : ScreenModules.index(screen);
    }

    /** true — id кабинета, карты или блока этого экрана. */
    public static boolean owns(Screen screen, String id) {
        return id != null && (cabinet(screen, id) != null || screen.receiverCardById(id) != null
                || screen.powerSupplyById(id) != null);
    }

    /** true — id именно карты или блока (а не кабинета) этого экрана. */
    public static boolean isUnit(Screen screen, String id) {
        return id != null && (screen.receiverCardById(id) != null || screen.powerSupplyById(id) != null);
    }

    /** Экран сцены, которому принадлежит звено {@code id}; {@code null} — такого звена в сцене нет. */
    public static Screen screenOf(Scene scene, String id) {
        if (scene == null) {
            return null;
        }
        for (Screen s : scene.getScreens()) {
            if (owns(s, id)) {
                return s;
            }
        }
        return null;
    }

    /** true — хотя бы одно из звеньев {@code ids} принадлежит экрану. */
    public static boolean touches(Screen screen, List<String> ids) {
        for (String id : ids) {
            if (owns(screen, id)) {
                return true;
            }
        }
        return false;
    }

    /** true — есть звено, НЕ принадлежащее экрану (цепочка продолжается на другом экране). */
    public static boolean leaves(Screen screen, List<String> ids) {
        for (String id : ids) {
            if (!owns(screen, id)) {
                return true;
            }
        }
        return false;
    }

    /** Ячейки звена: сам кабинет либо существующие модули карты/блока в порядке чтения (скрытые не отбрасываются —
     *  решает вызывающий). Пустой список — id экрану не принадлежит. */
    public static List<CabinetInstance> cabinetsOf(Screen screen, String id) {
        List<CabinetInstance> out = new ArrayList<>();
        if (id == null) {
            return out;
        }
        CabinetInstance cab = cabinet(screen, id);
        if (cab != null) {
            out.add(cab);
            return out;
        }
        List<String> moduleIds = moduleIdsOf(screen, id);
        if (moduleIds != null) {
            for (String m : moduleIds) {
                CabinetInstance c = cabinet(screen, m);
                if (c != null) {
                    out.add(c);
                }
            }
            out.sort(ScreenModules.readingOrder());
        }
        return out;
    }

    /** Ячейка-«якорь» звена: кабинет — он сам; карта/блок — её первый модуль в порядке чтения (левый верхний угол).
     *  {@code null} — id экрану не принадлежит. Резолвер NovaLCT ({@code NovaLctControllerResolver}) помечает запись
     *  порта этой ячейкой, а размер «кабинета» NovaLCT (прямоугольник всех модулей карты в px) и положение на полотне
     *  писатель берёт у {@code NovaLctModularGrid} по тому же якорю; порядок на порту — порядок звеньев цепочки. */
    public static CabinetInstance anchorCabinet(Screen screen, String id) {
        List<CabinetInstance> all = cabinetsOf(screen, id);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Ячейки звена по всей сцене (цепочка может идти через несколько экранов). */
    public static List<CabinetInstance> cabinetsOf(Scene scene, String id) {
        Screen s = screenOf(scene, id);
        return s != null ? cabinetsOf(s, id) : new ArrayList<>();
    }

    /** Ячейка, которой звено «стоит» на холсте: кабинет — он сам; карта/блок — его место (явный хост либо модуль у
     *  центра, D11/D20), а если места нет — первый модуль. {@code null} — звена на экране нет. */
    public static CabinetInstance place(Screen screen, String id) {
        if (id == null) {
            return null;
        }
        CabinetInstance cab = cabinet(screen, id);
        if (cab != null) {
            return cab;
        }
        ReceiverCardInstance card = screen.receiverCardById(id);
        PowerSupplyInstance supply = card == null ? screen.powerSupplyById(id) : null;
        if (card == null && supply == null) {
            return null;
        }
        List<String> moduleIds = card != null ? card.getModuleIds() : supply.getModuleIds();
        String host = card != null ? card.getHostModuleId() : supply.getHostModuleId();
        String placeId = UnitNames.placeOf(indexOf(screen), moduleIds, host);
        CabinetInstance placed = placeId != null ? cabinet(screen, placeId) : null;
        if (placed != null) {
            return placed;
        }
        List<CabinetInstance> all = cabinetsOf(screen, id);
        return all.isEmpty() ? null : all.get(0);
    }

    /** То же по всей сцене. */
    public static CabinetInstance place(Scene scene, String id) {
        Screen s = screenOf(scene, id);
        return s != null ? place(s, id) : null;
    }

    /** id карты (сигнал) / блока (питание), в которой состоит ячейка; {@code null} — такой нет. */
    public static String unitContaining(Screen screen, String cabinetId, ChainUnits.Mode mode) {
        Scope sc = scopeOf(screen);
        if (sc != null) {
            return sc.unitOf(cabinetId, mode);
        }
        if (mode == ChainUnits.Mode.SIGNAL) {
            for (ReceiverCardInstance c : screen.getReceiverCards()) {
                if (c.getModuleIds().contains(cabinetId)) {
                    return c.getId();
                }
            }
        } else {
            for (PowerSupplyInstance p : screen.getPowerSupplies()) {
                if (p.getModuleIds().contains(cabinetId)) {
                    return p.getId();
                }
            }
        }
        return null;
    }

    /** id карт И блоков сцены, в которых состоит ячейка (для «занят ли кабинет цепочкой» без указания режима). */
    public static List<String> unitsContaining(Scene scene, String cabinetId) {
        List<String> out = new ArrayList<>();
        if (scene == null) {
            return out;
        }
        for (Screen s : scene.getScreens()) {
            for (ReceiverCardInstance c : s.getReceiverCards()) {
                if (c.getModuleIds().contains(cabinetId)) {
                    out.add(c.getId());
                }
            }
            for (PowerSupplyInstance p : s.getPowerSupplies()) {
                if (p.getModuleIds().contains(cabinetId)) {
                    out.add(p.getId());
                }
            }
        }
        return out;
    }

    /**
     * Звено, в которое превращается клик по ячейке {@code cabinetId} (D26): обычный кабинет — он сам (и всё на
     * немодульном экране); модуль — его карта (сигнал) или блок (питание); {@code null} — модуль без карты/блока,
     * в цепочку он не попадает. Id, который уже является картой или блоком, возвращается как есть.
     */
    public static String unitIdFor(Screen screen, Workspace workspace, ChainUnits.Mode mode, String cabinetId) {
        if (cabinetId == null) {
            return null;
        }
        if (screen.receiverCardById(cabinetId) != null || screen.powerSupplyById(cabinetId) != null) {
            return cabinetId;
        }
        if (screen.cabinetById(cabinetId) == null) {
            return null;
        }
        // Обычный кабинет (и любая ячейка немодульного экрана) — сам себе звено; модуль звеном не бывает.
        if (!isModule(screen, workspace, cabinetId)) {
            return cabinetId;
        }
        return unitContaining(screen, cabinetId, mode);
    }

    /** Быстрая проверка «может ли ячейка быть модулем» без построения снимка экрана. */
    private static boolean isModuleCandidate(Screen screen, Workspace workspace, String cabinetId) {
        CabinetInstance c = screen.cabinetById(cabinetId);
        if (c == null || workspace == null) {
            return false;
        }
        var t = ScreenLogic.effectiveType(c, workspace.cabinetTypeById(screen.getCabinetTypeId()), workspace);
        return t != null && t.isReceiverCardSeparate();
    }

    /** D2: есть ли в сцене модульный экран (хоть одна видимая ячейка с типом-модулем) — от этого зависит видимость
     *  кнопок видов «Приёмные карты» / «Блоки питания». */
    public static boolean sceneHasModularScreen(Scene scene, Workspace workspace) {
        if (scene == null || workspace == null) {
            return false;
        }
        for (Screen s : scene.getScreens()) {
            if (ScreenModules.of(s, workspace).isModular()) {
                return true;
            }
        }
        return false;
    }

    /** Модуль ли ячейка (видимая, с типом-модулем). */
    public static boolean isModule(Screen screen, Workspace workspace, String cabinetId) {
        return isModuleCandidate(screen, workspace, cabinetId) && !screen.cabinetById(cabinetId).isHidden();
    }

    // ------------------------------------------------------------------ подписи «3 карты, 2 каб.»

    /** Состав цепочки словами, как на экране (D4): «3 карты, 2 каб.», «4 блока, 1 каб.», «40 каб.». Для цепочки без
     *  карт и блоков — ровно прежнее «N каб.». */
    public static String countsText(Scene scene, List<String> ids) {
        int cards = 0;
        int supplies = 0;
        int cabinets = 0;
        for (String id : ids) {
            Screen s = screenOf(scene, id);
            if (s != null && s.receiverCardById(id) != null) {
                cards++;
            } else if (s != null && s.powerSupplyById(id) != null) {
                supplies++;
            } else {
                cabinets++;
            }
        }
        List<String> parts = new ArrayList<>();
        if (cards > 0) {
            parts.add(cards + " " + plural(cards, "карта", "карты", "карт"));
        }
        if (supplies > 0) {
            parts.add(supplies + " " + plural(supplies, "блок", "блока", "блоков"));
        }
        if (cabinets > 0 || parts.isEmpty()) {
            parts.add(cabinets + " каб.");
        }
        return String.join(", ", parts);
    }

    /** Русское склонение по числу: 1 карта, 2–4 карты, 5–20 карт, 21 карта… */
    public static String plural(int n, String one, String few, String many) {
        int mod100 = Math.abs(n) % 100;
        int mod10 = mod100 % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return many;
        }
        if (mod10 == 1) {
            return one;
        }
        if (mod10 >= 2 && mod10 <= 4) {
            return few;
        }
        return many;
    }

    private static List<String> moduleIdsOf(Screen screen, String id) {
        ReceiverCardInstance card = screen.receiverCardById(id);
        if (card != null) {
            return card.getModuleIds();
        }
        PowerSupplyInstance supply = screen.powerSupplyById(id);
        return supply != null ? supply.getModuleIds() : null;
    }
}
