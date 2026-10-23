package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.room.RoomHistory;
import com.vjstb.ledscheme.service.room.RoomLibraryAdapter;
import com.vjstb.ledscheme.service.room.RoomPlanCalc;
import com.vjstb.ledscheme.service.room.RoomSelection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Общее состояние редактора Планнера, которое делят все панели окна: текущая раскладка, каталог,
 * результат расчёта, выбор, история отмены. Панели только читают его и вызывают {@link #edit} /
 * {@link #beginLive} — сами раскладку не пишут, поэтому каждая правка проходит один и тот же путь:
 * снимок для отмены → изменение → сохранение проекта → пересчёт → оповещение панелей.
 *
 * <p>Перетаскивание идёт «вживую»: {@link #beginLive} делает один снимок, {@link #live} пересчитывает и
 * перерисовывает без записи на диск, {@link #commitLive} сохраняет. История у каждой раскладки своя (как
 * вкладки со схемами), поэтому смена раскладки не мешает отмене в другой.
 */
public final class PlannerSession {

    /** Слушатель изменений состояния: раскладка, выбор или расчёт. */
    public interface Listener {
        void sessionChanged();
    }

    private final AppModel model;
    private final List<Listener> listeners = new ArrayList<>();
    private final Map<String, RoomHistory> histories = new HashMap<>();
    private ControlRoomLayout layout;
    private RoomLibraryAdapter catalog;
    private RoomPlanCalc.Result calc;
    private RoomSelection selection;
    private boolean liveStarted;
    private RoomUnitsFormat.Mode units = RoomUnitsFormat.Mode.BOTH;

    public PlannerSession(AppModel model) {
        this.model = model;
        this.catalog = RoomLibraryAdapter.of(model);
    }

    public AppModel model() {
        return model;
    }

    public ControlRoomLayout layout() {
        return layout;
    }

    public RoomLibraryAdapter catalog() {
        return catalog;
    }

    /** Результат расчёта текущей раскладки; {@code null}, если раскладки нет. */
    public RoomPlanCalc.Result calc() {
        return calc;
    }

    public RoomSelection selection() {
        return selection;
    }

    /** Как подписывать размеры: в юнитах, в сантиметрах или вместе (переключатель «U / см» из прототипа). */
    public RoomUnitsFormat.Mode units() {
        return units;
    }

    public void setUnits(RoomUnitsFormat.Mode mode) {
        if (mode != null && mode != units) {
            units = mode;
            fire();
        }
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private RoomHistory history() {
        return layout == null ? null : histories.computeIfAbsent(layout.getId(), k -> new RoomHistory());
    }

    public boolean canUndo() {
        RoomHistory h = history();
        return h != null && h.canUndo();
    }

    public boolean canRedo() {
        RoomHistory h = history();
        return h != null && h.canRedo();
    }

    /** Показать другую раскладку (или {@code null}); выбор сбрасывается. */
    public void setLayout(ControlRoomLayout l) {
        if (l == layout) {
            return;
        }
        layout = l;
        selection = null;
        reload();
    }

    /** Библиотека или раскладка изменились извне: пересобрать каталог и расчёт. */
    public void reload() {
        catalog = RoomLibraryAdapter.of(model);
        recalc();
        fire();
    }

    private void recalc() {
        calc = layout == null ? null : RoomPlanCalc.calc(layout, catalog);
        if (selection != null && !exists(selection)) {
            selection = null;
        }
    }

    public void select(RoomSelection s) {
        if (java.util.Objects.equals(s, selection)) {
            return;
        }
        selection = s;
        fire();
    }

    /** Одна обычная правка: снимок для отмены, изменение, сохранение, пересчёт. */
    public void edit(Runnable change) {
        if (layout == null) {
            return;
        }
        history().push(layout);
        change.run();
        saveAndRefresh();
    }

    /** Как {@link #edit}, но правка возвращает, что выбрать после неё ({@code null} — оставить выбор). */
    public void editSelecting(java.util.function.Supplier<RoomSelection> change) {
        if (layout == null) {
            return;
        }
        history().push(layout);
        RoomSelection next = change.get();
        if (next != null) {
            selection = next;
        }
        saveAndRefresh();
    }

    /** Начало перетаскивания: один снимок на весь жест. */
    public void beginLive() {
        if (layout != null && !liveStarted) {
            history().push(layout);
            liveStarted = true;
        }
    }

    /** Промежуточный шаг жеста: пересчитать и перерисовать, не сохраняя проект. */
    public void live() {
        recalc();
        fire();
    }

    /** Конец жеста: сохранить проект. */
    public void commitLive() {
        if (liveStarted) {
            liveStarted = false;
            saveAndRefresh();
        }
    }

    public void undo() {
        if (layout != null && history().undo(layout)) {
            saveAndRefresh();
        }
    }

    public void redo() {
        if (layout != null && history().redo(layout)) {
            saveAndRefresh();
        }
    }

    private void saveAndRefresh() {
        model.controlRoomLayoutEdited();
        recalc();
        fire();
    }

    private void fire() {
        for (Listener l : new ArrayList<>(listeners)) {
            l.sessionChanged();
        }
    }

    /** Есть ли выбранный объект в текущей раскладке (после отмены или удаления выбор сбрасывается). */
    private boolean exists(RoomSelection s) {
        if (layout == null) {
            return false;
        }
        return switch (s.type()) {
            case TOWER -> com.vjstb.ledscheme.service.room.RoomEditor.tower(layout, s.id()) != null;
            case FURN -> com.vjstb.ledscheme.service.room.RoomEditor.furniture(layout, s.id()) != null;
            case LOOSE -> com.vjstb.ledscheme.service.room.RoomEditor.loose(layout, s.id()) != null;
            case RUN -> com.vjstb.ledscheme.service.room.RoomEditor.run(layout, s.id()) != null;
            case RITEM, SITEM -> com.vjstb.ledscheme.service.room.RoomEditor.locateItem(layout, s.id()) != null;
        };
    }
}
