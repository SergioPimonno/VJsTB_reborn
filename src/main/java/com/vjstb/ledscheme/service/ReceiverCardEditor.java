package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.ModuleNumberingPattern;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Правки приёмных карт экрана без Swing (docs/receiver-cards/PLAN.md, D8–D11) — порт {@code assignCards},
 * {@code take}, {@code removeCard} прототипа {@code prototype-flow.html} (по образцу {@code service.room.RoomEditor}).
 * Вид и жесты только вызывают эти методы; каждое правило проверяется обычными JUnit-тестами, а не кликами.
 *
 * <p>Методы меняют переданный экран на месте и НЕ трогают цепочки сцены и историю отмены: это делают обёртки
 * {@code AppModel} (одна запись отмены на операцию, id удалённых карт — из {@link EditReport#removedIds()} — убираются
 * из цепочек). Инвариант, который держит класс: модуль состоит максимум в одной карте, карта никогда не перекрывает
 * другую — новая карта целиком ЗАМЕНЯЕТ задетые (D10), а их модули вне выделения остаются «без карты».
 */
public final class ReceiverCardEditor {

    private ReceiverCardEditor() {
    }

    /**
     * «Вручную»: одна карта типа {@code typeId} на все модули выделенного прямоугольника. Повторная протяжка по той
     * же карте тем же типом ничего не делает (id карты и её звено в цепочке сохраняются).
     */
    public static EditReport assignManual(Screen screen, Workspace workspace, GridRect rect, String typeId) {
        requireType(typeId);
        ScreenModules sm = ScreenModules.of(screen, workspace);
        List<ScreenModules.Module> all = sm.modulesIn(rect);
        if (all.isEmpty()) {
            return EditReport.nothing();
        }
        List<String> ids = idsOf(all);
        Set<ReceiverCardInstance> hit = cardsTouching(screen, ids);
        if (hit.size() == 1) {
            ReceiverCardInstance one = hit.iterator().next();
            if (typeId.equals(one.getReceiverCardTypeId()) && new HashSet<>(one.getModuleIds()).equals(new HashSet<>(ids))) {
                return EditReport.nothing();
            }
        }
        Take taken = take(screen, ids, hit);
        ReceiverCardInstance card = new ReceiverCardInstance(typeId, ids);
        screen.getReceiverCards().add(card);
        return report(taken, List.of(card.getId()));
    }

    /**
     * «Автоматически»: прямоугольник разбивается на карты {@code w × h} модулей (w — по горизонтали, h — по
     * вертикали), остаток у правого и нижнего края — карты меньшего размера. Повторная протяжка, дающая ровно те же
     * карты (тот же тип, те же наборы модулей), ничего не делает.
     */
    public static EditReport assignAuto(Screen screen, Workspace workspace, GridRect rect, String typeId, int w, int h) {
        requireType(typeId);
        if (w < 1 || h < 1) {
            throw new IllegalArgumentException("Размер карты в модулях должен быть не меньше 1×1");
        }
        ScreenModules sm = ScreenModules.of(screen, workspace);
        List<ScreenModules.Module> all = sm.modulesIn(rect);
        if (all.isEmpty()) {
            return EditReport.nothing();
        }
        List<List<String>> tiles = new ArrayList<>();
        for (int r = rect.getRow0(); r <= rect.getRow1(); r += h) {
            for (int c = rect.getCol0(); c <= rect.getCol1(); c += w) {
                GridRect tile = new GridRect(r, c, Math.min(r + h - 1, rect.getRow1()), Math.min(c + w - 1, rect.getCol1()));
                List<String> tileIds = idsOf(sm.modulesIn(tile));
                if (!tileIds.isEmpty()) {
                    tiles.add(tileIds);
                }
            }
        }
        List<String> allIds = idsOf(all);
        Set<ReceiverCardInstance> hit = cardsTouching(screen, allIds);
        if (hit.size() == tiles.size()) {
            Set<Set<String>> tileSets = new HashSet<>();
            for (List<String> t : tiles) {
                tileSets.add(new HashSet<>(t));
            }
            boolean same = true;
            for (ReceiverCardInstance c : hit) {
                if (!typeId.equals(c.getReceiverCardTypeId()) || !tileSets.contains(new HashSet<>(c.getModuleIds()))) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return EditReport.nothing();
            }
        }
        Take taken = take(screen, allIds, hit);
        List<String> created = new ArrayList<>();
        for (List<String> t : tiles) {
            ReceiverCardInstance card = new ReceiverCardInstance(typeId, t);
            screen.getReceiverCards().add(card);
            created.add(card.getId());
        }
        return report(taken, created);
    }

    /** «Снять карту»: её модули остаются «без карты». */
    public static EditReport remove(Screen screen, String cardId) {
        ReceiverCardInstance card = screen.receiverCardById(cardId);
        if (card == null) {
            return EditReport.nothing();
        }
        screen.getReceiverCards().remove(card);
        int n = card.getModuleIds().size();
        return new EditReport(false, 0, n, List.of(card.getId()), List.of(), 0, 0, true,
                "Карта снята, модулей без карты: " + n);
    }

    /** «Место карты…» (D11): модуль, на котором стоит карта; должен входить в карту. */
    public static void setHost(Screen screen, String cardId, String moduleId) {
        ReceiverCardInstance card = requireCard(screen, cardId);
        if (!card.getModuleIds().contains(moduleId)) {
            throw new IllegalArgumentException("Место карты должно быть одним из её модулей");
        }
        card.setHostModuleId(moduleId);
    }

    /** Возвращает место к автоматическому (ближайший к центру карты модуль). */
    public static void resetHost(Screen screen, String cardId) {
        requireCard(screen, cardId).setHostModuleId(null);
    }

    /**
     * «Порядок вручную…» (D8): полный порядок подключения одним списком (первый — выход 1). Лишние и повторяющиеся
     * id отбрасываются; модули карты, которых в списке нет, останутся без номера («?»). Пустой список допустим —
     * это состояние «режим включён, ничего ещё не кликнуто».
     */
    public static void setManualOrder(Screen screen, String cardId, List<String> moduleIds) {
        ReceiverCardInstance card = requireCard(screen, cardId);
        if (moduleIds == null) {
            card.setManualPortOrder(null);
            return;
        }
        Set<String> members = new HashSet<>(card.getModuleIds());
        Set<String> order = new LinkedHashSet<>();
        for (String id : moduleIds) {
            if (members.contains(id)) {
                order.add(id);
            }
        }
        card.setManualPortOrder(new ArrayList<>(order));
    }

    /** Один клик режима «Порядок вручную»: модуль получает следующий номер; повторный клик по тому же — ничего. */
    public static void appendManualOrder(Screen screen, String cardId, String moduleId) {
        ReceiverCardInstance card = requireCard(screen, cardId);
        if (!card.getModuleIds().contains(moduleId)) {
            return;
        }
        List<String> order = card.getManualPortOrder() != null
                ? new ArrayList<>(card.getManualPortOrder()) : new ArrayList<>();
        if (!order.contains(moduleId)) {
            order.add(moduleId);
        }
        card.setManualPortOrder(order);
    }

    /** «Сбросить к шаблону»: карта снова нумеруется по шаблону экрана. */
    public static void resetOrder(Screen screen, String cardId) {
        requireCard(screen, cardId).setManualPortOrder(null);
    }

    /** Шаблон нумерации модулей экрана (палитра под холстом + «змейка»). */
    public static void setPattern(Screen screen, ModuleNumberingPattern pattern) {
        screen.setModuleNumbering(pattern != null ? pattern.copy() : new ModuleNumberingPattern());
    }

    // ------------------------------------------------------------------ внутреннее

    private static void requireType(String typeId) {
        if (typeId == null || typeId.isBlank()) {
            throw new IllegalArgumentException("Не выбран тип приёмной карты");
        }
    }

    private static ReceiverCardInstance requireCard(Screen screen, String cardId) {
        ReceiverCardInstance card = screen.receiverCardById(cardId);
        if (card == null) {
            throw new IllegalArgumentException("Приёмная карта не найдена на экране");
        }
        return card;
    }

    private static List<String> idsOf(List<ScreenModules.Module> modules) {
        List<String> ids = new ArrayList<>(modules.size());
        for (ScreenModules.Module m : modules) {
            ids.add(m.id());
        }
        return ids;
    }

    /** Карты, чьи модули попали в выделение, в порядке списка экрана. */
    private static Set<ReceiverCardInstance> cardsTouching(Screen screen, List<String> selectedIds) {
        Set<String> sel = new HashSet<>(selectedIds);
        Set<ReceiverCardInstance> hit = new LinkedHashSet<>();
        for (ReceiverCardInstance c : screen.getReceiverCards()) {
            for (String id : c.getModuleIds()) {
                if (sel.contains(id)) {
                    hit.add(c);
                    break;
                }
            }
        }
        return hit;
    }

    private record Take(int replaced, int orphaned, List<String> removedIds) {
    }

    /** Правило замены D10: задетые карты удаляются целиком, их модули вне выделения считаются осиротевшими. */
    private static Take take(Screen screen, List<String> selectedIds, Set<ReceiverCardInstance> hit) {
        Set<String> sel = new HashSet<>(selectedIds);
        int orphaned = 0;
        List<String> removed = new ArrayList<>();
        Map<String, Boolean> seen = new HashMap<>();
        for (ReceiverCardInstance c : hit) {
            for (String id : c.getModuleIds()) {
                if (!sel.contains(id) && seen.put(id, true) == null) {
                    orphaned++;
                }
            }
            removed.add(c.getId());
        }
        screen.getReceiverCards().removeAll(hit);
        return new Take(hit.size(), orphaned, removed);
    }

    private static EditReport report(Take t, List<String> created) {
        String msg = t.replaced() > 0
                ? "Заменено карт: " + t.replaced() + (t.orphaned() > 0 ? ", модулей без карты: " + t.orphaned() : "")
                : "";
        return new EditReport(false, t.replaced(), t.orphaned(), t.removedIds(), created, 0, 0, true, msg);
    }
}
