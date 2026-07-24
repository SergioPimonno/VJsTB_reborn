package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.model.Workspace;
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

    /** Заполняет экран сеткой rows x cols новых кабинетов. Цепочки теперь хранятся
     *  на уровне сцены (см. Task #78), а не экрана — для только что созданного
     *  экрана их всё равно ещё ни у кого нет, дополнительная очистка не нужна. */
    public static void buildGrid(Screen screen) {
        List<CabinetInstance> cabinets = new ArrayList<>();
        for (int r = 0; r < screen.getRows(); r++) {
            for (int c = 0; c < screen.getCols(); c++) {
                cabinets.add(new CabinetInstance(r, c));
            }
        }
        screen.setCabinets(cabinets);
    }

    /**
     * Меняет размер сетки, сохраняя существующие кабинеты в пределах новых границ.
     * Кабинеты, вышедшие за границы, удаляются, а ссылки на них — из цепочек СЦЕНЫ
     * (см. Task #78 — цепочки больше не хранятся на самом экране, поэтому для
     * прунинга нужна сцена, которой принадлежит экран; null — прунить нечего,
     * например если экран ещё нигде не числится).
     */
    public static void resizeGrid(Screen screen, int newRows, int newCols, Scene scene) {
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

        if (!removedIds.isEmpty() && scene != null) {
            for (PowerChain chain : scene.getPowerChains()) {
                chain.getCabinetInstanceIds().removeAll(removedIds);
            }
            scene.getPowerChains().removeIf(ch -> ch.getCabinetInstanceIds().isEmpty());
            for (SignalChain chain : scene.getSignalChains()) {
                chain.getCabinetInstanceIds().removeAll(removedIds);
            }
            scene.getSignalChains().removeIf(ch -> ch.getCabinetInstanceIds().isEmpty());
        }
    }

    /** Фактический тип кабинета: переопределение по ячейке (если задано и разрешимо
     *  через workspace), иначе тип экрана по умолчанию. */
    public static CabinetType effectiveType(CabinetInstance c, CabinetType defaultType, Workspace workspace) {
        if (workspace != null && c.getCabinetTypeId() != null) {
            CabinetType override = workspace.cabinetTypeById(c.getCabinetTypeId());
            if (override != null) {
                return override;
            }
        }
        return defaultType;
    }

    private static CabinetInstance findAt(List<CabinetInstance> list, int row, int col) {
        for (CabinetInstance c : list) {
            if (c.getRowIndex() == row && c.getColIndex() == col) {
                return c;
            }
        }
        return null;
    }

    /** Характеристики экрана без учёта переопределения типа кабинета по ячейкам. */
    public static ScreenStats stats(Screen screen, CabinetType defaultType) {
        return stats(screen, defaultType, null);
    }

    /** Суммарные характеристики НЕСКОЛЬКИХ экранов (вся сцена) — для режима «показать
     *  все экраны сцены» на этапах Питание/Сигнал, где статистика одного активного
     *  экрана не даёт полной картины по нагрузке всей сцены (см. Task #71).
     *  Разрешение/физический размер не агрегируются (экраны — не единый прямоугольник,
     *  просто список независимых сеток) — в возвращаемой записи это 0, UI показывает
     *  вместо них количество экранов отдельно. */
    public static ScreenStats aggregateStats(List<Screen> screens,
                                              java.util.function.Function<Screen, CabinetType> typeResolver,
                                              Workspace workspace) {
        int active = 0;
        int[] phaseCounts = new int[4];
        double[] phasePower = new double[4];
        double totalPower = 0;
        double totalWeight = 0;
        for (Screen s : screens) {
            ScreenStats st = stats(s, typeResolver.apply(s), workspace);
            active += st.activeCabinetCount();
            totalPower += st.totalPowerW();
            totalWeight += st.totalWeightKg();
            for (int i = 1; i <= 3; i++) {
                phaseCounts[i] += st.phaseCabinetCounts()[i];
                phasePower[i] += st.phasePowerW()[i];
            }
        }
        return new ScreenStats(0, 0, 0, 0, active, totalPower, totalWeight, phaseCounts, phasePower);
    }

    /**
     * Характеристики экрана. Разрешение/габариты считаются по типу экрана
     * (геометрия сетки — единая), а вес/мощность/нагрузка по фазам — по
     * ФАКТИЧЕСКОМУ типу каждого кабинета (с учётом переопределения по ячейкам,
     * если передан workspace для его разрешения).
     */
    public static ScreenStats stats(Screen screen, CabinetType defaultType, Workspace workspace) {
        double w = defaultType != null ? defaultType.getWidthMm() : 0;
        double h = defaultType != null ? defaultType.getHeightMm() : 0;
        int rw = defaultType != null ? defaultType.getResolutionWidth() : 0;
        int rh = defaultType != null ? defaultType.getResolutionHeight() : 0;

        int active = 0;
        int[] phaseCounts = new int[4];
        double[] phasePower = new double[4];
        double totalPower = 0;
        double totalWeight = 0;
        for (CabinetInstance c : screen.getCabinets()) {
            if (c.isHidden()) {
                continue;
            }
            active++;
            CabinetType effective = effectiveType(c, defaultType, workspace);
            double p = effective != null ? effective.getPowerConsumptionW() : 0;
            double wt = effective != null ? effective.getWeightKg() : 0;
            totalPower += p;
            totalWeight += wt;
            if (c.getPhase() >= 1 && c.getPhase() <= 3) {
                phaseCounts[c.getPhase()]++;
                phasePower[c.getPhase()] += p;
            }
        }

        return new ScreenStats(
                screen.getCols() * w,
                screen.getRows() * h,
                screen.getCols() * rw,
                screen.getRows() * rh,
                active,
                totalPower,
                totalWeight,
                phaseCounts,
                phasePower
        );
    }

    /** Базовая сводка по сцене (прериг): суммарный вес/мощность и разбивка кабинетов
     *  по фактическому типу (для отображения «128 (Base: 96, Heavy: 32)» в прериге). */
    public static SceneStats sceneStats(Scene scene, Workspace workspace) {
        int cabinets = 0;
        double power = 0;
        double weight = 0;
        java.util.Map<CabinetType, Integer> byType = new java.util.LinkedHashMap<>();
        for (Screen s : scene.getScreens()) {
            CabinetType defaultType = workspace.cabinetTypeById(s.getCabinetTypeId());
            ScreenStats st = stats(s, defaultType, workspace);
            cabinets += st.activeCabinetCount();
            power += st.totalPowerW();
            weight += st.totalWeightKg();
            for (CabinetInstance c : s.getCabinets()) {
                if (c.isHidden()) {
                    continue;
                }
                CabinetType effective = effectiveType(c, defaultType, workspace);
                if (effective != null) {
                    byType.merge(effective, 1, Integer::sum);
                }
            }
        }
        return new SceneStats(scene.getScreens().size(), cabinets, power, weight, byType);
    }

    /** Авторасчёт количества точек подвеса по формуле из референсных таблиц
     *  риг-тех расчёта заказчика: «Hanging bar» = ширина экрана в модулях / 2
     *  (стандартный сегмент несущей балки перекрывает 2 модуля по ширине) —
     *  трактуется как минимальное число точек подвеса, округлённое вверх, не
     *  меньше 2 (подвес не бывает на одной точке). Даёт лишь стартовое значение —
     *  пользователь может скорректировать вручную под конкретную ферму/траверс. */
    public static int suggestRiggingPoints(Screen screen) {
        return Math.max(2, (int) Math.ceil(screen.getCols() / 2.0));
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
        live.setSignalPortCount(snapshot.getSignalPortCount());
        live.setRefreshRateHz(snapshot.getRefreshRateHz());
        live.setColorBitDepth(snapshot.getColorBitDepth());
        live.setMountType(snapshot.getMountType());
        live.setRiggingPointsCount(snapshot.getRiggingPointsCount());
        live.setRiggingNotes(snapshot.getRiggingNotes());

        List<CabinetInstance> cabs = new ArrayList<>();
        for (CabinetInstance c : snapshot.getCabinets()) {
            cabs.add(c.copy());
        }
        live.setCabinets(cabs);

        List<com.vjstb.ledscheme.model.ControllerInstance> ctrls = new ArrayList<>();
        for (com.vjstb.ledscheme.model.ControllerInstance c : snapshot.getControllers()) {
            ctrls.add(c.copy());
        }
        live.setControllers(ctrls);
        // Цепочки питания/сигнала больше не восстанавливаются здесь — они хранятся
        // на уровне сцены, а не экрана (см. Task #78); AppModel.undo() снимает и
        // восстанавливает их отдельно, вместе со снимком экрана.
    }
}
