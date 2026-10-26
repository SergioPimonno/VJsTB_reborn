package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Взгляд на экран как на набор МОДУЛЕЙ (docs/receiver-cards/PLAN.md, D1): модуль — видимая ячейка, чей ЭФФЕКТИВНЫЙ тип
 * кабинета (тип ячейки, иначе тип экрана) имеет флаг {@code receiverCardSeparate}. Набор модулей в данных не
 * хранится — он вычисляется здесь из типов, поэтому смена флага у типа или типа ячейки сразу меняет, что считается
 * модулем, а {@link ModuleIntegrity} убирает из карт и блоков то, что модулем быть перестало.
 *
 * <p>Объект строится один раз на серию вычислений (индексы по id и по ячейке, центры в мм): алгоритмы разбиения и
 * подбора блоков перебирают десятки кандидатов, и линейный поиск кабинета по id в каждом шаге был бы заметен на
 * экранах в тысячи ячеек. Снимок НЕ следит за экраном — после правки сетки/типов нужно построить новый.
 *
 * <p>Размеры: центр модуля в мм — в системе экрана (как {@link ScreenLogic#cabinetExtentMm}): позиция сетки по типу
 * экрана + свободное смещение ячейки + половина ширины/высоты её собственного типа. Длины шлейфов и кабелей (D5)
 * считаются по прямой между такими центрами.
 */
public final class ScreenModules {

    /** Модуль экрана: ячейка, её эффективный тип и центр в мм. */
    public record Module(CabinetInstance cabinet, CabinetType type, double xMm, double yMm) {
        public String id() {
            return cabinet.getId();
        }

        public int row() {
            return cabinet.getRowIndex();
        }

        public int col() {
            return cabinet.getColIndex();
        }
    }

    private static final Comparator<CabinetInstance> READING_ORDER = Comparator
            .comparingInt(CabinetInstance::getRowIndex).thenComparingInt(CabinetInstance::getColIndex);

    private final Screen screen;
    private final Workspace workspace;
    private final CabinetType defaultType;
    private final Map<String, CabinetInstance> byId = new HashMap<>();
    /** Модули в порядке чтения (строка за строкой). */
    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final Map<Long, Module> moduleByCell = new HashMap<>();

    private ScreenModules(Screen screen, Workspace workspace, String ignoreFlagOfTypeId) {
        this.screen = screen;
        this.workspace = workspace;
        this.defaultType = workspace != null ? workspace.cabinetTypeById(screen.getCabinetTypeId()) : null;
        List<CabinetInstance> sorted = new ArrayList<>(screen.getCabinets());
        for (CabinetInstance c : screen.getCabinets()) {
            byId.put(c.getId(), c);
        }
        sorted.sort(READING_ORDER);
        for (CabinetInstance c : sorted) {
            if (c.isHidden()) {
                continue;
            }
            CabinetType eff = ScreenLogic.effectiveType(c, defaultType, workspace);
            if (eff == null || !eff.isReceiverCardSeparate() || eff.getId().equals(ignoreFlagOfTypeId)) {
                continue;
            }
            double[] center = centerOf(c, eff);
            Module m = new Module(c, eff, center[0], center[1]);
            modules.put(c.getId(), m);
            moduleByCell.put(cellKey(c.getRowIndex(), c.getColIndex()), m);
        }
    }

    public static ScreenModules of(Screen screen, Workspace workspace) {
        return new ScreenModules(screen, workspace, null);
    }

    /** Как {@link #of}, но тип {@code ignoreFlagOfTypeId} считается уже БЕЗ флага модуля — для расчёта последствий
     *  снятия флага в библиотеке до самого снятия (подтверждение «N карт и M блоков будут удалены»). */
    public static ScreenModules ofWithoutFlag(Screen screen, Workspace workspace, String ignoreFlagOfTypeId) {
        return new ScreenModules(screen, workspace, ignoreFlagOfTypeId);
    }

    private static long cellKey(int row, int col) {
        return ((long) row << 32) | (col & 0xFFFFFFFFL);
    }

    private double[] centerOf(CabinetInstance c, CabinetType eff) {
        double baseW = defaultType != null ? defaultType.getWidthMm() : (eff != null ? eff.getWidthMm() : 0);
        double baseH = defaultType != null ? defaultType.getHeightMm() : (eff != null ? eff.getHeightMm() : 0);
        double w = eff != null ? eff.getWidthMm() : baseW;
        double h = eff != null ? eff.getHeightMm() : baseH;
        return new double[]{c.getColIndex() * baseW + c.getOffsetXMm() + w / 2,
                c.getRowIndex() * baseH + c.getOffsetYMm() + h / 2};
    }

    /** D1: «модульный» экран — хотя бы одна видимая ячейка с эффективным типом-модулем. */
    public boolean isModular() {
        return !modules.isEmpty();
    }

    /** Все модули в порядке чтения. */
    public List<Module> modules() {
        return Collections.unmodifiableList(new ArrayList<>(modules.values()));
    }

    /** Модуль по id ячейки; {@code null}, если такой ячейки нет, она скрыта или уже не модуль. */
    public Module module(String id) {
        return id != null ? modules.get(id) : null;
    }

    public Module moduleAt(int row, int col) {
        return moduleByCell.get(cellKey(row, col));
    }

    /** Модули внутри прямоугольника, в порядке чтения. */
    public List<Module> modulesIn(GridRect rect) {
        List<Module> out = new ArrayList<>();
        for (Module m : modules.values()) {
            if (rect.contains(m.row(), m.col())) {
                out.add(m);
            }
        }
        return out;
    }

    /** Любая ячейка экрана по id (в том числе скрытая и не-модуль). */
    public CabinetInstance cabinet(String id) {
        return id != null ? byId.get(id) : null;
    }

    /** Видимые ячейки, которые модулями не являются («обычные кабинеты» смешанного экрана, D3), в порядке списка
     *  экрана — для немодульного экрана это в точности прежние (не скрытые) кабинеты. */
    public List<CabinetInstance> ordinaryCabinets() {
        List<CabinetInstance> out = new ArrayList<>();
        for (CabinetInstance c : screen.getCabinets()) {
            if (!c.isHidden() && !modules.containsKey(c.getId())) {
                out.add(c);
            }
        }
        return out;
    }

    /** Эффективный тип ячейки (тип ячейки, иначе тип экрана) или {@code null}. */
    public CabinetType effectiveType(CabinetInstance c) {
        return ScreenLogic.effectiveType(c, defaultType, workspace);
    }

    /** Центр ячейки, мм, в системе экрана. */
    public double[] centerMm(CabinetInstance c) {
        return centerOf(c, effectiveType(c));
    }

    /** Расстояние по прямой между центрами двух ячеек, мм (D5). */
    public double distanceMm(CabinetInstance a, CabinetInstance b) {
        double[] pa = centerMm(a);
        double[] pb = centerMm(b);
        return Math.hypot(pa[0] - pb[0], pa[1] - pb[1]);
    }

    public Screen screen() {
        return screen;
    }

    public Workspace workspace() {
        return workspace;
    }

    /** Индекс «id → ячейка» без разбора типов — для логики, которой нужны только позиции (имена, нумерация). */
    public static Map<String, CabinetInstance> index(Screen screen) {
        Map<String, CabinetInstance> m = new HashMap<>();
        for (CabinetInstance c : screen.getCabinets()) {
            m.put(c.getId(), c);
        }
        return m;
    }

    /** Порядок чтения: строка, затем столбец. */
    public static Comparator<CabinetInstance> readingOrder() {
        return READING_ORDER;
    }
}
