package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.PowerAutoArea;
import com.vjstb.ledscheme.model.PowerSupplyInstance;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Правки блоков питания экрана без Swing (docs/receiver-cards/PLAN.md, D17–D23) — порт {@code assignBlocks},
 * {@code takeB}, {@code removeBlock} прототипа {@code prototype-flow.html}. Как и {@link ReceiverCardEditor}, методы
 * меняют экран на месте, а цепочки сцены и историю отмены ведут обёртки {@code AppModel}. Инвариант: модуль питается
 * максимум одним блоком; новый блок целиком заменяет задетые (D18), их модули вне выделения остаются «без питания».
 *
 * <p><b>Автоподбор</b> ({@link #assignAuto}): область дробится на прямоугольники одинакового размера
 * {@code w × h}, где {@code w·h ≤ outputs} типа; размер подбирается перебором. Кандидат проходит, если все его блоки
 * не перегружены (учитывая принудительный шлейф под карту, D19) и все модули в пределах длины кабеля от места
 * блока, а карты, стоящие в области, получили питание. Из прошедших берётся тот, где блоков меньше всего; при равенстве
 * — крупнее, затем ближе к квадрату. Не прошёл никто — 1×1. Неполные блоки у края допустимы.
 */
public final class PowerSupplyEditor {

    private PowerSupplyEditor() {
    }

    /** «Вручную»: один блок типа {@code typeId} на все модули прямоугольника. Повторная протяжка по тому же блоку
     *  тем же типом ничего не делает. Запись об автообласти поверх этого места удаляется (D19). */
    public static EditReport assignManual(Screen screen, Workspace workspace, GridRect rect, String typeId) {
        requireType(typeId);
        ScreenModules sm = ScreenModules.of(screen, workspace);
        List<ScreenModules.Module> all = sm.modulesIn(rect);
        if (all.isEmpty()) {
            return EditReport.nothing();
        }
        List<String> ids = idsOf(all);
        Set<PowerSupplyInstance> hit = suppliesTouching(screen, ids);
        if (hit.size() == 1) {
            PowerSupplyInstance one = hit.iterator().next();
            if (typeId.equals(one.getPowerSupplyTypeId()) && new HashSet<>(one.getModuleIds()).equals(new HashSet<>(ids))) {
                return EditReport.nothing();
            }
        }
        Take taken = take(screen, ids, hit);
        screen.getPowerAutoAreas().removeIf(a -> a.getRect().intersects(rect));
        PowerSupplyInstance supply = new PowerSupplyInstance(typeId, ids);
        screen.getPowerSupplies().add(supply);
        return report(taken, List.of(supply.getId()), 0, 0, true);
    }

    /** «Автоматически»: область дробится на блоки подобранного размера (см. class-javadoc), запоминается как
     *  {@link PowerAutoArea} — чтобы пересчитать при смене типа ({@link #onTypeChanged}). */
    public static EditReport assignAuto(Screen screen, Workspace workspace, GridRect rect, String typeId) {
        return assignAuto(screen, workspace, rect, typeId, null, null);
    }

    /**
     * То же с ограничениями размера блока, заданными инженером (по аналогии с «Г × В» приёмных карт): блок не шире
     * {@code maxWidth} и не выше {@code maxHeight} модулей. {@code null} — не задано; оба {@code null} — подбор
     * полностью автоматический, как раньше. Ограничение — потолок, а не точный размер: внутри него по-прежнему выбирается
     * вариант с наименьшим числом блоков, прошедший проверки (шлейфы, кабель, выход карты).
     */
    public static EditReport assignAuto(Screen screen, Workspace workspace, GridRect rect, String typeId,
                                        Integer maxWidth, Integer maxHeight) {
        requireType(typeId);
        PowerSupplyType type = workspace.powerSupplyTypeById(typeId);
        if (type == null) {
            throw new IllegalArgumentException("Тип блока питания не найден в библиотеке");
        }
        return autoAssign(screen, workspace, rect, typeId, type, positiveOrNull(maxWidth), positiveOrNull(maxHeight));
    }

    private static Integer positiveOrNull(Integer v) {
        return v != null && v >= 1 ? v : null;
    }

    /**
     * Тип блока изменился (прежде всего «Отдельный выход на карту», D19): все автоматически разбитые области
     * ЭТОГО типа разбиваются заново. Блоки, поставленные вручную, записей не имеют и не затрагиваются. Блоки области,
     * которые получились другими, создаются заново с новыми id — их звенья выпадают из фаз (D23), id отдаются в
     * отчёте; если разбиение области не изменилось, блоки остаются как были.
     */
    public static EditReport onTypeChanged(Screen screen, Workspace workspace, String typeId) {
        PowerSupplyType type = workspace.powerSupplyTypeById(typeId);
        if (type == null) {
            return EditReport.nothing();
        }
        List<String> removed = new ArrayList<>();
        List<String> created = new ArrayList<>();
        int replaced = 0;
        int orphaned = 0;
        boolean changed = false;
        for (PowerAutoArea area : new ArrayList<>(screen.getPowerAutoAreas())) {
            if (!typeId.equals(area.getPowerSupplyTypeId())) {
                continue;
            }
            EditReport r = autoAssign(screen, workspace, area.getRect().copy(), typeId, type, area.getMaxWidth(),
                    area.getMaxHeight());
            if (!r.noop()) {
                changed = true;
                removed.addAll(r.removedIds());
                created.addAll(r.createdIds());
                replaced += r.replaced();
                orphaned += r.orphaned();
            }
        }
        if (!changed) {
            return EditReport.nothing();
        }
        return new EditReport(false, replaced, orphaned, removed, created, 0, 0, true,
                "Блоки питания пересчитаны, фазы пересозданных блоков сброшены");
    }

    /** «Снять блок»: его модули остаются «без питания»; запись об автообласти, которой он принадлежал, снимается —
     *  иначе пересчёт типа вернул бы блок, который пользователь убрал сознательно. */
    public static EditReport remove(Screen screen, String supplyId) {
        PowerSupplyInstance supply = screen.powerSupplyById(supplyId);
        if (supply == null) {
            return EditReport.nothing();
        }
        screen.getPowerSupplies().remove(supply);
        dropAreasTouching(screen, supply.getModuleIds());
        int n = supply.getModuleIds().size();
        return new EditReport(false, 0, n, List.of(supply.getId()), List.of(), 0, 0, true,
                "Блок снят, модулей без питания: " + n);
    }

    /** «Место блока…» (D20): модуль внутри области блока, от которого считается длина кабелей. */
    public static void setHost(Screen screen, String supplyId, String moduleId) {
        PowerSupplyInstance supply = requireSupply(screen, supplyId);
        if (!supply.getModuleIds().contains(moduleId)) {
            throw new IllegalArgumentException("Место блока должно быть модулем внутри его области");
        }
        supply.setHostModuleId(moduleId);
    }

    public static void resetHost(Screen screen, String supplyId) {
        requireSupply(screen, supplyId).setHostModuleId(null);
    }

    // ------------------------------------------------------------------ автоподбор

    private static EditReport autoAssign(Screen screen, Workspace workspace, GridRect rect, String typeId,
                                         PowerSupplyType type, Integer maxWidth, Integer maxHeight) {
        ScreenModules sm = ScreenModules.of(screen, workspace);
        List<ScreenModules.Module> all = sm.modulesIn(rect);
        if (all.isEmpty()) {
            return EditReport.nothing();
        }
        // Баг-репорт: у блоков базовой библиотеки число шлейфов не указано (в паспортах клеммники), и «не указано»
        // читалось как «без ограничения» — весь экран из 3072 модулей слился в ОДИН блок. Размер блока подбирается
        // именно по числу шлейфов, поэтому без него автоподбор не работает; вручную блок на выделение поставить можно.
        if (type.getOutputs() == null) {
            return EditReport.refused("У типа «" + type.getName() + "» не указано число шлейфов — укажите его в «Библиотеки → "
                    + "Блоки питания»: автоподбор размера блока без него невозможен. Вручную блок поставить можно.");
        }
        List<String> allIds = idsOf(all);
        Set<PowerSupplyInstance> hit = suppliesTouching(screen, allIds);
        List<PowerSupplyInstance> base = new ArrayList<>(screen.getPowerSupplies());
        base.removeAll(hit);

        Choice chosen = choose(sm, base, rect, typeId, type, allIds, maxWidth, maxHeight);

        if (hit.size() == chosen.tiles().size() && hasAreaEqual(screen, rect, typeId, maxWidth, maxHeight)) {
            Set<Set<String>> tileSets = new HashSet<>();
            for (PowerSupplyInstance t : chosen.tiles()) {
                tileSets.add(new HashSet<>(t.getModuleIds()));
            }
            boolean same = true;
            for (PowerSupplyInstance s : hit) {
                if (!typeId.equals(s.getPowerSupplyTypeId()) || !tileSets.contains(new HashSet<>(s.getModuleIds()))) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return EditReport.nothing();
            }
        }

        Take taken = take(screen, allIds, hit);
        screen.getPowerAutoAreas().removeIf(a -> a.getRect().intersects(rect));
        screen.getPowerAutoAreas().add(new PowerAutoArea(rect.copy(), typeId, maxWidth, maxHeight));
        List<String> created = new ArrayList<>();
        for (PowerSupplyInstance t : chosen.tiles()) {
            screen.getPowerSupplies().add(t);
            created.add(t.getId());
        }
        return report(taken, created, chosen.w(), chosen.h(), chosen.ok());
    }

    private static boolean hasAreaEqual(Screen screen, GridRect rect, String typeId, Integer maxWidth,
                                        Integer maxHeight) {
        for (PowerAutoArea a : screen.getPowerAutoAreas()) {
            if (a.getRect().equals(rect) && typeId.equals(a.getPowerSupplyTypeId())
                    && java.util.Objects.equals(a.getMaxWidth(), maxWidth)
                    && java.util.Objects.equals(a.getMaxHeight(), maxHeight)) {
                return true;
            }
        }
        return false;
    }

    private record Choice(int w, int h, boolean ok, List<PowerSupplyInstance> tiles) {
    }

    private static Choice choose(ScreenModules sm, List<PowerSupplyInstance> base, GridRect rect, String typeId,
                                 PowerSupplyType type, List<String> allIds, Integer maxWidth, Integer maxHeight) {
        Integer outputs = type.getOutputs();
        List<int[]> cands = new ArrayList<>();
        int wLimit = maxWidth != null ? Math.min(rect.colCount(), maxWidth) : rect.colCount();
        int hLimit = maxHeight != null ? Math.min(rect.rowCount(), maxHeight) : rect.rowCount();
        for (int w = 1; w <= wLimit; w++) {
            for (int h = 1; h <= hLimit; h++) {
                if (outputs == null || w * h <= outputs) {
                    cands.add(new int[]{w, h});
                }
            }
        }
        // крупнее — раньше; при равенстве площади ближе к квадрату; порядок остальных сохраняется (стабильная сортировка)
        cands.sort((a, b) -> {
            int byArea = b[0] * b[1] - a[0] * a[1];
            return byArea != 0 ? byArea : Math.abs(a[0] - a[1]) - Math.abs(b[0] - b[1]);
        });
        boolean hasUnit = false;
        for (int[] c : cands) {
            if (c[0] == 1 && c[1] == 1) {
                hasUnit = true;
                break;
            }
        }
        if (!hasUnit) {
            cands.add(new int[]{1, 1});
        }

        Screen screen = sm.screen();
        Map<String, CabinetInstance> idx = ScreenModules.index(screen);
        Set<String> inArea = new HashSet<>(allIds);
        List<ReceiverCardInstance> cardsInArea = new ArrayList<>();
        for (ReceiverCardInstance c : screen.getReceiverCards()) {
            String place = UnitNames.placeOf(idx, c.getModuleIds(), c.getHostModuleId());
            if (place != null && inArea.contains(place)) {
                cardsInArea.add(c);
            }
        }

        Choice best = null;
        Choice fallback = null;
        for (int[] c : cands) {
            int w = c[0];
            int h = c[1];
            List<PowerSupplyInstance> tiles = tilesFor(sm, rect, typeId, w, h);
            List<PowerSupplyInstance> trial = new ArrayList<>(base);
            trial.addAll(tiles);
            PowerSupplyLayout.Result layout = PowerSupplyLayout.compute(sm, trial, ReceiverCardNumbering.DEFAULT);
            boolean ok = true;
            for (PowerSupplyInstance t : tiles) {
                PowerSupplyLayout.Supply s = layout.ofSupply(t.getId());
                if (s == null || s.hasProblem()) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                for (ReceiverCardInstance card : cardsInArea) {
                    if (layout.ofCard(card.getId()) == null) {
                        ok = false;
                        break;
                    }
                }
            }
            Choice cand = new Choice(w, h, ok, tiles);
            if (ok && (best == null || better(cand, best))) {
                best = cand;
            }
            if (w == 1 && h == 1) {
                fallback = cand;
            }
        }
        return best != null ? best : fallback;
    }

    /** Меньше блоков; при равенстве — крупнее; при равенстве — ближе к квадрату (строго лучше, иначе остаётся прежний). */
    private static boolean better(Choice a, Choice b) {
        if (a.tiles().size() != b.tiles().size()) {
            return a.tiles().size() < b.tiles().size();
        }
        if (a.w() * a.h() != b.w() * b.h()) {
            return a.w() * a.h() > b.w() * b.h();
        }
        return Math.abs(a.w() - a.h()) < Math.abs(b.w() - b.h());
    }

    private static List<PowerSupplyInstance> tilesFor(ScreenModules sm, GridRect rect, String typeId, int w, int h) {
        List<PowerSupplyInstance> tiles = new ArrayList<>();
        for (int r = rect.getRow0(); r <= rect.getRow1(); r += h) {
            for (int c = rect.getCol0(); c <= rect.getCol1(); c += w) {
                GridRect tile = new GridRect(r, c, Math.min(r + h - 1, rect.getRow1()), Math.min(c + w - 1, rect.getCol1()));
                List<String> ids = idsOf(sm.modulesIn(tile));
                if (!ids.isEmpty()) {
                    tiles.add(new PowerSupplyInstance(typeId, ids));
                }
            }
        }
        return tiles;
    }

    // ------------------------------------------------------------------ внутреннее

    private static void requireType(String typeId) {
        if (typeId == null || typeId.isBlank()) {
            throw new IllegalArgumentException("Не выбран тип блока питания");
        }
    }

    private static PowerSupplyInstance requireSupply(Screen screen, String supplyId) {
        PowerSupplyInstance s = screen.powerSupplyById(supplyId);
        if (s == null) {
            throw new IllegalArgumentException("Блок питания не найден на экране");
        }
        return s;
    }

    private static List<String> idsOf(List<ScreenModules.Module> modules) {
        List<String> ids = new ArrayList<>(modules.size());
        for (ScreenModules.Module m : modules) {
            ids.add(m.id());
        }
        return ids;
    }

    private static Set<PowerSupplyInstance> suppliesTouching(Screen screen, List<String> selectedIds) {
        Set<String> sel = new HashSet<>(selectedIds);
        Set<PowerSupplyInstance> hit = new LinkedHashSet<>();
        for (PowerSupplyInstance s : screen.getPowerSupplies()) {
            for (String id : s.getModuleIds()) {
                if (sel.contains(id)) {
                    hit.add(s);
                    break;
                }
            }
        }
        return hit;
    }

    private static void dropAreasTouching(Screen screen, List<String> moduleIds) {
        if (screen.getPowerAutoAreas().isEmpty()) {
            return;
        }
        Map<String, CabinetInstance> idx = ScreenModules.index(screen);
        screen.getPowerAutoAreas().removeIf(a -> {
            for (String id : moduleIds) {
                CabinetInstance c = idx.get(id);
                if (c != null && a.getRect().contains(c.getRowIndex(), c.getColIndex())) {
                    return true;
                }
            }
            return false;
        });
    }

    private record Take(int replaced, int orphaned, List<String> removedIds) {
    }

    /** Правило замены (D18, как D10 у карт): задетые блоки удаляются целиком, их модули вне выделения осиротевают. */
    private static Take take(Screen screen, List<String> selectedIds, Set<PowerSupplyInstance> hit) {
        Set<String> sel = new HashSet<>(selectedIds);
        Set<String> orphans = new HashSet<>();
        List<String> removed = new ArrayList<>();
        for (PowerSupplyInstance s : hit) {
            for (String id : s.getModuleIds()) {
                if (!sel.contains(id)) {
                    orphans.add(id);
                }
            }
            removed.add(s.getId());
        }
        screen.getPowerSupplies().removeAll(hit);
        return new Take(hit.size(), orphans.size(), removed);
    }

    private static EditReport report(Take t, List<String> created, int w, int h, boolean ok) {
        String msg = t.replaced() > 0
                ? "Заменено блоков: " + t.replaced() + (t.orphaned() > 0 ? ", модулей без питания: " + t.orphaned() : "")
                : "";
        return new EditReport(false, t.replaced(), t.orphaned(), t.removedIds(), created, w, h, ok, msg);
    }
}
