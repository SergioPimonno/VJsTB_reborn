package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Логика построения цепочки на холсте, общая для питания и сигнала: выбор фазы/порта
 * сразу начинает новую цепочку; кабинеты добавляются кликом, протяжкой (зажатая ЛКМ)
 * или стрелками клавиатуры; Esc завершает и сохраняет цепочку (если она не пуста).
 */
public class ChainInteractionController implements CanvasPanel.Controller {

    private final AppModel model;
    private final Runnable onChange;

    private Consumer<List<String>> commitHandler;
    private boolean building;
    private final List<String> activeIds = new ArrayList<>();
    private int cursorRow = -1;
    private int cursorCol = -1;
    private String hoveredCabId;

    public ChainInteractionController(AppModel model, Runnable onChange) {
        this.model = model;
        this.onChange = onChange;
    }

    /** Начинает (перезапускает) построение новой цепочки для цели (фаза/порт). Прежняя — сохраняется. */
    public void startFor(Consumer<List<String>> commitHandler) {
        commitCurrentSilently();
        this.commitHandler = commitHandler;
        activeIds.clear();
        cursorRow = -1;
        cursorCol = -1;
        building = true;
        onChange.run();
    }

    /** Esc: завершить построение, сохранив цепочку, если в ней есть кабинеты. */
    public void finish() {
        if (!building) {
            return;
        }
        commitCurrentSilently();
        building = false;
        commitHandler = null;
        onChange.run();
    }

    private void commitCurrentSilently() {
        if (building && !activeIds.isEmpty() && commitHandler != null) {
            commitHandler.accept(new ArrayList<>(activeIds));
        }
        activeIds.clear();
    }

    public String hoveredCabinetId() {
        return hoveredCabId;
    }

    public void moveCursor(int dRow, int dCol) {
        if (!building) {
            return;
        }
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        if (cursorRow < 0 || cursorCol < 0) {
            if (!activeIds.isEmpty()) {
                CabinetInstance last = scr.cabinetById(activeIds.get(activeIds.size() - 1));
                cursorRow = last != null ? last.getRowIndex() : 0;
                cursorCol = last != null ? last.getColIndex() : 0;
            } else {
                cursorRow = 0;
                cursorCol = 0;
            }
        } else {
            cursorRow = clamp(cursorRow + dRow, 0, scr.getRows() - 1);
            cursorCol = clamp(cursorCol + dCol, 0, scr.getCols() - 1);
        }
        CabinetInstance cab = scr.cabinetAt(cursorRow, cursorCol);
        if (cab != null && !activeIds.contains(cab.getId())) {
            activeIds.add(cab.getId());
        }
        onChange.run();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---- CanvasPanel.Controller ----

    @Override
    public boolean isChainBuilding() {
        return building;
    }

    @Override
    public List<String> activeChainCabIds() {
        return activeIds;
    }

    @Override
    public int cursorRow() {
        return cursorRow;
    }

    @Override
    public int cursorCol() {
        return cursorCol;
    }

    @Override
    public void cabinetClicked(String cabId) {
        if (!building || cabId == null) {
            return;
        }
        Screen scr = model.getCurrentScreen();
        CabinetInstance cab = scr != null ? scr.cabinetById(cabId) : null;
        if (cab == null) {
            return;
        }
        if (!activeIds.contains(cabId)) {
            activeIds.add(cabId);
        }
        cursorRow = cab.getRowIndex();
        cursorCol = cab.getColIndex();
        onChange.run();
    }

    @Override
    public void cabinetHovered(String cabId) {
        hoveredCabId = cabId;
    }
}
