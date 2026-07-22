package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Операции над экраном: построение/перестроение сетки кабинетов, пересчёт
 * характеристик, снимок/восстановление состояния (для «отменить»).
 * Не зависит от UI.
 */
public final class ScreenLogic {

    private ScreenLogic() {
    }

    /** Заполняет экран сеткой rows x cols новых кабинетов. */
    public static void buildGrid(Screen screen) {
        List<CabinetInstance> cabinets = new ArrayList<>();
        for (int r = 0; r < screen.getRows(); r++) {
            for (int c = 0; c < screen.getCols(); c++) {
                cabinets.add(new CabinetInstance(r, c));
            }
        }
        screen.setCabinets(cabinets);
        screen.getPowerChains().clear();
        screen.getSignalChains().clear();
    }

    /**
     * Меняет размер сетки, сохраняя существующие кабинеты в пределах новых границ.
     * Кабинеты, вышедшие за границы, удаляются, а ссылки на них — из цепочек.
     */
    public static void resizeGrid(Screen screen, int newRows, int newCols) {
        Set<String> removedIds = new HashSet<>();
        List<CabinetInstance> kept = new ArrayList<>();
        for (CabinetInstance c : screen.getCabinets()) {
            if (c.getRowIndex() < newRows && c.getColIndex() < newCols) {
                kept.add(c);
            } else {
                removedIds.add(c.getId());
            }
        }
        for (int r = 0; r < newRows; r++) {
            for (int c = 0; c < newCols; c++) {
                if (findAt(kept, r, c) == null) {
                    kept.add(new CabinetInstance(r, c));
                }
            }
        }
        screen.setCabinets(kept);
        screen.setRows(newRows);
        screen.setCols(newCols);

        if (!removedIds.isEmpty()) {
            for (PowerChain chain : screen.getPowerChains()) {
                chain.getCabinetInstanceIds().removeAll(removedIds);
            }
            screen.getPowerChains().removeIf(ch -> ch.getCabinetInstanceIds().isEmpty());
            for (SignalChain chain : screen.getSignalChains()) {
                chain.getCabinetInstanceIds().removeAll(removedIds);
            }
            screen.getSignalChains().removeIf(ch -> ch.getCabinetInstanceIds().isEmpty());
        }
    }

    private static CabinetInstance findAt(List<CabinetInstance> list, int row, int col) {
        for (CabinetInstance c : list) {
            if (c.getRowIndex() == row && c.getColIndex() == col) {
                return c;
            }
        }
        return null;
    }

    public static ScreenStats stats(Screen screen, CabinetType type) {
        double w = type != null ? type.getWidthMm() : 0;
        double h = type != null ? type.getHeightMm() : 0;
        int rw = type != null ? type.getResolutionWidth() : 0;
        int rh = type != null ? type.getResolutionHeight() : 0;
        double power = type != null ? type.getPowerConsumptionW() : 0;
        double weight = type != null ? type.getWeightKg() : 0;

        int active = 0;
        int[] phaseCounts = new int[4];
        for (CabinetInstance c : screen.getCabinets()) {
            if (c.isHidden()) {
                continue;
            }
            active++;
            if (c.getPhase() >= 1 && c.getPhase() <= 3) {
                phaseCounts[c.getPhase()]++;
            }
        }
        double[] phasePower = new double[4];
        for (int p = 1; p <= 3; p++) {
            phasePower[p] = phaseCounts[p] * power;
        }

        return new ScreenStats(
                screen.getCols() * w,
                screen.getRows() * h,
                screen.getCols() * rw,
                screen.getRows() * rh,
                active,
                active * power,
                active * weight,
                phaseCounts,
                phasePower
        );
    }

    // ---- undo: снимок и восстановление состояния экрана ----

    public static Screen snapshot(Screen screen) {
        return screen.copy();
    }

    /** Восстанавливает изменяемое состояние экрана из снимка, сохраняя сам объект. */
    public static void restore(Screen live, Screen snapshot) {
        live.setName(snapshot.getName());
        live.setCabinetTypeId(snapshot.getCabinetTypeId());
        live.setRows(snapshot.getRows());
        live.setCols(snapshot.getCols());
        live.setPosXMm(snapshot.getPosXMm());
        live.setPosYMm(snapshot.getPosYMm());

        List<CabinetInstance> cabs = new ArrayList<>();
        for (CabinetInstance c : snapshot.getCabinets()) {
            cabs.add(c.copy());
        }
        live.setCabinets(cabs);

        List<PowerChain> pc = new ArrayList<>();
        for (PowerChain c : snapshot.getPowerChains()) {
            pc.add(c.copy());
        }
        live.setPowerChains(pc);

        List<SignalChain> sc = new ArrayList<>();
        for (SignalChain c : snapshot.getSignalChains()) {
            sc.add(c.copy());
        }
        live.setSignalChains(sc);
    }
}
