package com.vjstb.ledscheme.service.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.model.RoomLooseItem;
import com.vjstb.ledscheme.model.RoomOverrides;
import com.vjstb.ledscheme.model.RoomPoint;
import com.vjstb.ledscheme.model.RoomRackItem;
import com.vjstb.ledscheme.model.RoomRun;
import com.vjstb.ledscheme.model.RoomSegment;
import com.vjstb.ledscheme.model.RoomTower;
import java.util.ArrayList;
import java.util.List;

/**
 * Правки раскладки пультовой без Swing — порт операций прототипа ({@code planner.html}:
 * {@code addFromLib}, {@code removeSel}, {@code rotateSel}, {@code findSpot}, {@code elevSnap},
 * {@code placeLoose}, {@code finishDraw}…). Окно и жесты только вызывают эти методы и затем
 * записывают раскладку ({@code AppModel#controlRoomLayoutEdited}); благодаря этому каждое правило
 * проверяется обычными JUnit-тестами, а не кликами.
 *
 * <p>Все методы меняют переданную раскладку на месте. Размеры берутся из каталога
 * ({@link RoomCatalog}), геометрия — из {@link RoomGeometry}; числа шагов — из {@link RoomUnits}.
 */
public final class RoomEditor {

    private RoomEditor() {
    }

    /** Результат добавления: что выбрать теперь и что сказать пользователю. */
    public record Added(RoomSelection selection, String message) {
    }

    /** Куда встал объект при перетаскивании на виде спереди. */
    public record ElevationSnap(double x, double y, RoomTower.Lay lay, boolean on) {
    }

    // ---------------------------------------------------------------- добавление из библиотеки

    /**
     * Кнопка «+» на карточке библиотеки. Кофр — новая башня; блок в стойку — в выбранную (или первую)
     * башню, в первый подходящий слот, иначе сверху стопкой без кофра; мелочь — стопкой в выбранную
     * башню либо на площадку; мебель, станок, монитор — на свободное место подходящего вида.
     *
     * @param frontView открыт вид спереди: станок тогда ставится сверху на станки пола, как стол
     */
    public static Added addFromLibrary(ControlRoomLayout layout, RoomCatalog cat, RoomItemSpec spec,
                                       RoomSelection sel, boolean frontView) {
        RoomLibRef ref = spec.ref();
        switch (spec.type()) {
            case CASE: {
                double[] spot = findSpot(layout, cat, spec.widthCm(), spec.depthCm());
                RoomTower t = RoomRackLayout.newTower(layout, ref, spot[0], spot[1]);
                return new Added(RoomSelection.tower(t.getId()), "Добавлена новая башня");
            }
            case RACK: {
                RoomTower t = targetTower(layout, sel);
                if (t == null) {
                    double[] spot = findSpot(layout, cat, Math.max(spec.widthCm(), 20), Math.max(spec.depthCm(), 20));
                    RoomTower nt = RoomRackLayout.newStackTower(layout, ref, spot[0], spot[1]);
                    return new Added(RoomSelection.tower(nt.getId()), "Новая башня: оборудование лежит без кофра");
                }
                if (RoomRackLayout.autoPlace(cat, t, spec)) {
                    return new Added(RoomSelection.tower(t.getId()),
                            "«" + spec.name() + "» добавлен в башню " + t.getTag());
                }
                RoomRackLayout.addToStack(t, ref);
                return new Added(RoomSelection.tower(t.getId()),
                        "Нет места в кофрах: «" + spec.name() + "» лёг сверху стопкой без кофра");
            }
            case LOOSE: {
                RoomTower t = targetTower(layout, sel);
                if (t != null) {
                    RoomRackItem it = RoomRackLayout.addToStack(t, ref);
                    RoomSegment seg = RoomRackLayout.topStack(t);
                    return new Added(RoomSelection.stackItem(it.getId(), t.getId(), seg.getId()),
                            "«" + spec.name() + "» лёг стопкой в башню " + t.getTag());
                }
                RoomLooseItem l = placeLoose(layout, ref, layout.getRoomWidthCm() * 0.6, layout.getRoomDepthCm() * 0.42);
                return new Added(RoomSelection.loose(l.getId()),
                        "Мелочь добавлена на площадку. Выберите башню, чтобы класть в неё");
            }
            default: {
                double[] spot;
                boolean on = false;
                if (spec.type() == RoomItemSpec.Type.DECK) {
                    spot = frontView ? findDeckOnSpot(layout, cat, spec, null) : findDeckSpot(layout, cat, spec);
                    on = spot.length > 2 && spot[2] > 0;
                } else if (spec.type() == RoomItemSpec.Type.MONITOR || spec.type() == RoomItemSpec.Type.DEVICE) {
                    spot = findMonitorSpot(layout, cat, spec);
                } else {
                    spot = findSpot(layout, cat, spec.widthCm(), spec.depthCm());
                }
                RoomFurniture f = new RoomFurniture();
                f.setRef(ref);
                f.setXCm(spot[0]);
                f.setYCm(spot[1]);
                f.setOnTop(on);
                layout.getFurniture().add(f);
                return new Added(RoomSelection.furniture(f.getId()), "«" + spec.name() + "» добавлен на план");
            }
        }
    }

    /** Башня для «+»: выбранная, башня выбранного блока, иначе первая; {@code null} — башен нет. */
    private static RoomTower targetTower(ControlRoomLayout layout, RoomSelection sel) {
        if (sel != null && sel.towerId() != null) {
            RoomTower t = tower(layout, sel.towerId());
            if (t != null) {
                return t;
            }
        }
        return layout.getTowers().isEmpty() ? null : layout.getTowers().get(0);
    }

    public static RoomTower tower(ControlRoomLayout layout, String id) {
        for (RoomTower t : layout.getTowers()) {
            if (t.getId().equals(id)) {
                return t;
            }
        }
        return null;
    }

    public static RoomFurniture furniture(ControlRoomLayout layout, String id) {
        for (RoomFurniture f : layout.getFurniture()) {
            if (f.getId().equals(id)) {
                return f;
            }
        }
        return null;
    }

    public static RoomLooseItem loose(ControlRoomLayout layout, String id) {
        for (RoomLooseItem l : layout.getLoose()) {
            if (l.getId().equals(id)) {
                return l;
            }
        }
        return null;
    }

    public static RoomRun run(ControlRoomLayout layout, String id) {
        for (RoomRun r : layout.getRuns()) {
            if (r.getId().equals(id)) {
                return r;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- свободное место

    /** Первое свободное место размером w×d: сверху вниз от переднего края, слева направо, шаг 10 см. */
    public static double[] findSpot(ControlRoomLayout layout, RoomCatalog cat, double w, double d) {
        List<RoomBounds> all = RoomGeometry.bounds(layout, cat);
        for (double y = layout.getRoomDepthCm() - d / 2 - 10; y >= d / 2 + 10; y -= 10) {
            for (double x = w / 2 + 10; x <= layout.getRoomWidthCm() - w / 2 - 10; x += 10) {
                if (free(all, x - w / 2, x + w / 2, y - d / 2, y + d / 2)) {
                    return new double[] {x, y};
                }
            }
        }
        return new double[] {layout.getRoomWidthCm() / 2, layout.getRoomDepthCm() / 2};
    }

    private static boolean free(List<RoomBounds> all, double x0, double x1, double y0, double y1) {
        for (RoomBounds o : all) {
            if (x0 < o.x1 - .5 && x1 > o.x0 + .5 && y0 < o.y1 - .5 && y1 > o.y0 + .5) {
                return false;
            }
        }
        return true;
    }

    /** Место для станка на полу: рядом с уже стоящими (по сетке 50 см), пусто — у левой стены посередине. */
    public static double[] findDeckSpot(ControlRoomLayout layout, RoomCatalog cat, RoomItemSpec spec) {
        List<RoomBounds> decks = RoomGeometry.decks(RoomGeometry.bounds(layout, cat));
        double w = spec.widthCm();
        double d = spec.depthCm();
        List<double[]> cand = new ArrayList<>();
        if (decks.isEmpty()) {
            cand.add(new double[] {0, Math.round(layout.getRoomDepthCm() / 2 / 50) * 50});
        }
        for (RoomBounds b : decks) {
            cand.add(new double[] {b.x1, b.y0});
            cand.add(new double[] {b.x0 - w, b.y0});
            cand.add(new double[] {b.x0, b.y1});
            cand.add(new double[] {b.x0, b.y0 - d});
        }
        for (double[] c : cand) {
            if (c[0] < 0 || c[1] < 0 || c[0] + w > layout.getRoomWidthCm() || c[1] + d > layout.getRoomDepthCm()) {
                continue;
            }
            if (free(decks, c[0], c[0] + w, c[1], c[1] + d)) {
                return new double[] {c[0] + w / 2, c[1] + d / 2};
            }
        }
        return new double[] {w / 2, d / 2};
    }

    /**
     * Место для станка-стола сверху на станках пола (вид спереди, запрос 2026-10-23): внутри
     * прямоугольника, занятого станками пола, по сетке 50 см, чтобы все четыре угла опирались на станки и не
     * пересекался другой станок-стол. Третий элемент результата — 1, если нашлось место сверху; иначе
     * вернётся место на полу.
     */
    public static double[] findDeckOnSpot(ControlRoomLayout layout, RoomCatalog cat, RoomItemSpec spec,
                                          String excludeFurnitureId) {
        List<RoomBounds> all = new ArrayList<>(RoomGeometry.bounds(layout, cat));
        all.removeIf(b -> b.furniture != null && b.furniture.getId().equals(excludeFurnitureId));
        List<RoomBounds> floor = new ArrayList<>();
        List<RoomBounds> up = new ArrayList<>();
        for (RoomBounds b : all) {
            if (b.deck) {
                (b.onTop ? up : floor).add(b);
            }
        }
        if (floor.isEmpty()) {
            double[] s = findDeckSpot(layout, cat, spec);
            return new double[] {s[0], s[1], 0};
        }
        double x0 = Double.MAX_VALUE;
        double y0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE;
        double y1 = -Double.MAX_VALUE;
        for (RoomBounds b : floor) {
            x0 = Math.min(x0, b.x0);
            x1 = Math.max(x1, b.x1);
            y0 = Math.min(y0, b.y0);
            y1 = Math.max(y1, b.y1);
        }
        double w = spec.widthCm();
        double d = spec.depthCm();
        for (double y = y0; y + d <= y1 + .1; y += 50) {
            for (double x = x0; x + w <= x1 + .1; x += 50) {
                boolean covered = RoomGeometry.deckHeightAt(floor, x + .5, y + .5) >= 0
                        && RoomGeometry.deckHeightAt(floor, x + w - .5, y + .5) >= 0
                        && RoomGeometry.deckHeightAt(floor, x + .5, y + d - .5) >= 0
                        && RoomGeometry.deckHeightAt(floor, x + w - .5, y + d - .5) >= 0;
                if (covered && free(up, x, x + w, y, y + d)) {
                    return new double[] {x + w / 2, y + d / 2, 1};
                }
            }
        }
        double[] s = findDeckSpot(layout, cat, spec);
        return new double[] {s[0], s[1], 0};
    }

    /** Место для монитора: на первом столе у дальнего края, рядом с уже стоящими; нет столов — просто свободное место. */
    public static double[] findMonitorSpot(ControlRoomLayout layout, RoomCatalog cat, RoomItemSpec spec) {
        List<RoomBounds> all = RoomGeometry.bounds(layout, cat);
        List<RoomBounds> monitors = new ArrayList<>();
        for (RoomBounds b : all) {
            if (b.monitor) {
                monitors.add(b);
            }
        }
        double w = spec.widthCm();
        double d = spec.depthCm();
        for (RoomBounds t : all) {
            boolean table = t.isTableLike() && t.spec != null && t.spec.role() == RoomItemSpec.Role.TABLE
                    && t.furniture != null && t.furniture.getRotation() % 180 == 0;
            if (!table) {
                continue;
            }
            double y = t.y0 + d / 2 + 3;
            for (double x = t.x0 + w / 2 + 3; x + w / 2 <= t.x1; x += w + 6) {
                if (free(monitors, x - w / 2, x + w / 2, y - d / 2, y + d / 2)) {
                    return new double[] {step(x), step(y)};
                }
            }
        }
        return findSpot(layout, cat, w, d);
    }

    // ---------------------------------------------------------------- привязка и перенос

    /** Ближайший шаг перемещения (5 см). */
    public static double step(double v) {
        return Math.round(v / RoomUnits.MOVE_STEP_CM) * RoomUnits.MOVE_STEP_CM;
    }

    /** Станки встают по сетке 50 см (по краю), остальное — шагом 5 см по центру. */
    public static double[] snapPosition(boolean deck, double w, double d, double x, double y) {
        if (deck) {
            double g = RoomUnits.DECK_GRID_CM;
            return new double[] {Math.round((x - w / 2) / g) * g + w / 2, Math.round((y - d / 2) / g) * g + d / 2};
        }
        return new double[] {step(x), step(y)};
    }

    /** Перенести башню на (x, y) шагом 5 см. */
    public static void moveTower(ControlRoomLayout layout, String id, double x, double y) {
        RoomTower t = tower(layout, id);
        if (t != null) {
            t.setXCm(step(x));
            t.setYCm(step(y));
        }
    }

    /** Перенести мебель/станок/монитор; станок — по сетке 50 см. */
    public static void moveFurniture(ControlRoomLayout layout, RoomCatalog cat, String id, double x, double y) {
        RoomFurniture f = furniture(layout, id);
        if (f == null) {
            return;
        }
        RoomItemSpec s = RoomGeometry.effective(f, cat);
        boolean swap = f.getRotation() % 180 != 0;
        double[] p = snapPosition(s.type() == RoomItemSpec.Type.DECK, swap ? s.depthCm() : s.widthCm(),
                swap ? s.widthCm() : s.depthCm(), x, y);
        f.setXCm(p[0]);
        f.setYCm(p[1]);
    }

    /** Края настила, за которые его можно тянуть (маска: можно сочетать два соседних — угол). */
    public static final int EDGE_LEFT = 1;
    public static final int EDGE_RIGHT = 2;
    public static final int EDGE_TOP = 4;
    public static final int EDGE_BOTTOM = 8;

    /**
     * Потянуть край (или угол) настила к точке плана (x, y). Край встаёт на сетку 50 см, противоположный остаётся на
     * месте, меньше одной клетки настил не делается. Размер пишется в переопределения этого настила
     * ({@link RoomOverrides}): запись в библиотеке не меняется. У повёрнутого на 90° настила ширина и глубина
     * библиотеки меняются местами, поэтому тянущийся размер по оси плана попадает в «чужое» поле.
     */
    public static void resizeDeck(ControlRoomLayout layout, RoomCatalog cat, String id, int edges, double x, double y) {
        RoomFurniture f = furniture(layout, id);
        if (f == null) {
            return;
        }
        RoomItemSpec s = RoomGeometry.effective(f, cat);
        if (s.type() != RoomItemSpec.Type.DECK) {
            return;
        }
        boolean swap = f.getRotation() % 180 != 0;
        double w = swap ? s.depthCm() : s.widthCm();
        double d = swap ? s.widthCm() : s.depthCm();
        double x0 = f.getXCm() - w / 2;
        double x1 = f.getXCm() + w / 2;
        double y0 = f.getYCm() - d / 2;
        double y1 = f.getYCm() + d / 2;
        double g = RoomUnits.DECK_GRID_CM;
        double sx = Math.round(x / g) * g;
        double sy = Math.round(y / g) * g;
        if ((edges & EDGE_LEFT) != 0) {
            x0 = Math.min(sx, x1 - g);
        }
        if ((edges & EDGE_RIGHT) != 0) {
            x1 = Math.max(sx, x0 + g);
        }
        if ((edges & EDGE_TOP) != 0) {
            y0 = Math.min(sy, y1 - g);
        }
        if ((edges & EDGE_BOTTOM) != 0) {
            y1 = Math.max(sy, y0 + g);
        }
        RoomOverrides o = f.getOverrides() != null ? f.getOverrides() : new RoomOverrides();
        if ((edges & (EDGE_LEFT | EDGE_RIGHT)) != 0) {
            if (swap) {
                o.setDepthCm(x1 - x0);
            } else {
                o.setWidthCm(x1 - x0);
            }
        }
        if ((edges & (EDGE_TOP | EDGE_BOTTOM)) != 0) {
            if (swap) {
                o.setWidthCm(y1 - y0);
            } else {
                o.setDepthCm(y1 - y0);
            }
        }
        f.setOverrides(o);
        f.setXCm((x0 + x1) / 2);
        f.setYCm((y0 + y1) / 2);
    }

    /** Положение мелочи на плане: на трассе — точка на ней, иначе своё место. */
    public static RoomPoint locationOf(ControlRoomLayout layout, RoomLooseItem l) {
        if (l.getMode() == RoomLooseItem.Mode.RUN) {
            RoomRun r = run(layout, l.getRunId());
            if (r != null && r.getPoints().size() > 1) {
                return RoomRunMath.pointAt(r, l.getSCm() != null ? l.getSCm() : 0);
            }
        }
        return new RoomPoint(l.getXCm() != null ? l.getXCm() : 0, l.getYCm() != null ? l.getYCm() : 0);
    }

    /**
     * Мелочь, которая по библиотеке теперь «Отдельно» (монитор, пульт — предмет на плане): переносится в объекты плана на
     * то же место. Раньше такой предмет ставили мелочью-пином (пока в библиотеке была «Россыпью»), а смена установки
     * на уже стоящие объекты не действовала — их нельзя было повернуть. Мелочь «в кофре» остаётся как есть.
     * Возвращает число перенесённых.
     */
    public static int promoteStandaloneLoose(ControlRoomLayout layout, RoomCatalog cat) {
        int moved = 0;
        for (RoomLooseItem l : new ArrayList<>(layout.getLoose())) {
            if (l.getMode() == RoomLooseItem.Mode.BOX) {
                continue;
            }
            RoomItemSpec s = cat.resolve(l.getRef());
            if (s.type() != RoomItemSpec.Type.DEVICE && s.type() != RoomItemSpec.Type.MONITOR) {
                continue;
            }
            RoomPoint p = locationOf(layout, l);
            RoomFurniture f = new RoomFurniture();
            f.setRef(l.getRef());
            f.setXCm(step(p.getX()));
            f.setYCm(step(p.getY()));
            layout.getFurniture().add(f);
            layout.getLoose().remove(l);
            moved++;
        }
        return moved;
    }

    /** Новая мелочь на площадке: ближе 30 см к трассе — встаёт на неё и запоминает расстояние от ввода. */
    public static RoomLooseItem placeLoose(ControlRoomLayout layout, RoomLibRef ref, double x, double y) {
        RoomLooseItem l = new RoomLooseItem();
        l.setRef(ref);
        dropLoose(layout, l, x, y);
        layout.getLoose().add(l);
        return l;
    }

    /** Поставить уже существующую мелочь в точку: привязка к трассе в пределах {@link RoomUnits#SNAP_RUN_CM}. */
    public static void dropLoose(ControlRoomLayout layout, RoomLooseItem l, double x, double y) {
        RoomRunMath.Nearest n = RoomRunMath.snapToRuns(layout.getRuns(), x, y);
        if (n != null) {
            l.setMode(RoomLooseItem.Mode.RUN);
            l.setRunId(n.runId());
            l.setSCm(n.s());
            l.setXCm(null);
            l.setYCm(null);
        } else {
            l.setMode(RoomLooseItem.Mode.FREE);
            l.setXCm(x);
            l.setYCm(y);
            l.setRunId(null);
            l.setSCm(null);
        }
    }

    /** Переключатель «Свободно / На трассе / В кофре» в панели мелочи. */
    public static void setLooseMode(ControlRoomLayout layout, RoomLooseItem l, RoomLooseItem.Mode mode, String runId) {
        RoomPoint here = locationOf(layout, l);
        switch (mode) {
            case FREE -> {
                l.setMode(RoomLooseItem.Mode.FREE);
                l.setXCm(here.getX());
                l.setYCm(here.getY());
                l.setRunId(null);
                l.setSCm(null);
            }
            case RUN -> {
                RoomRun r = runId != null ? run(layout, runId) : null;
                RoomRunMath.Nearest n = r != null ? RoomRunMath.nearest(r, here.getX(), here.getY()) : null;
                if (n == null) {
                    n = RoomRunMath.snapToRuns(layout.getRuns(), here.getX(), here.getY());
                }
                if (n == null) {
                    for (RoomRun any : layout.getRuns()) {
                        n = RoomRunMath.nearest(any, here.getX(), here.getY());
                        if (n != null) {
                            break;
                        }
                    }
                }
                if (n == null) {
                    return;
                }
                l.setMode(RoomLooseItem.Mode.RUN);
                l.setRunId(r != null ? r.getId() : n.runId());
                l.setSCm(n.s());
                l.setXCm(null);
                l.setYCm(null);
            }
            case BOX -> {
                l.setMode(RoomLooseItem.Mode.BOX);
                l.setXCm(null);
                l.setYCm(null);
                l.setRunId(null);
                l.setSCm(null);
            }
        }
    }

    // ---------------------------------------------------------------- вид спереди

    /**
     * Куда встанет объект, перетаскиваемый на виде спереди (порт {@code elevSnap}). {@code nx} — новая
     * координата x центра, {@code bh} — высота, на которой отпущен низ объекта. Вбок — по плану; вверх на
     * стол, станок или башню — «сверху» (опора в пределах 25 см от низа), вниз — на пол или «под стол».
     * Станок встаёт по сетке 50 см и может лечь сверху на станки пола (становится столом); башня на столе
     * получает {@code ON}, низкая башня под столом — {@code UNDER}; монитор можно ставить на стол, станок,
     * башню.
     */
    public static ElevationSnap elevationSnap(ControlRoomLayout layout, RoomCatalog cat, RoomSelection.Type kind,
                                              String id, double nx, double bh) {
        List<RoomBounds> all = RoomGeometry.bounds(layout, cat);
        RoomBounds self = null;
        for (RoomBounds b : all) {
            if (b.id.equals(id)) {
                self = b;
            }
        }
        if (self == null) {
            return new ElevationSnap(nx, 0, null, false);
        }
        boolean isTower = kind == RoomSelection.Type.TOWER;
        boolean isDeck = self.deck;
        boolean isMon = self.monitor;
        double w = self.x1 - self.x0;
        double d = self.y1 - self.y0;
        double curY = self.cy();
        double x = isDeck ? Math.round((nx - w / 2) / RoomUnits.DECK_GRID_CM) * RoomUnits.DECK_GRID_CM + w / 2 : step(nx);
        List<RoomBounds> decks = RoomGeometry.decks(all);

        record Host(double x0, double x1, double y0, double y1, double top, String kind, double h) {
        }
        List<Host> hosts = new ArrayList<>();
        for (RoomBounds b : all) {
            if (b == self) {
                continue;
            }
            if (b.deck) {
                if (isDeck && b.onTop) {
                    continue;
                }
                hosts.add(new Host(b.x0, b.x1, b.y0, b.y1, b.top, "deck", b.height));
                continue;
            }
            if (isDeck) {
                continue;
            }
            if (b.isTableLike()) {
                if (!isTower && !isMon) {
                    continue;
                }
                hosts.add(new Host(b.x0, b.x1, b.y0, b.y1, Math.max(0, RoomGeometry.deckHeightAt(decks, b.cx(), b.cy())) + b.height,
                        "table", b.height));
                continue;
            }
            if (b.isTower() && isMon) {
                hosts.add(new Host(b.x0, b.x1, b.y0, b.y1, RoomGeometry.towerBase(b, all) + b.height, "tower", b.height));
            }
        }
        Host cand = null;
        for (Host h : hosts) {
            if (x < h.x0 || x > h.x1) {
                continue;
            }
            if (h.top <= bh + 25 && (cand == null || h.top > cand.top)) {
                cand = h;
            }
        }
        double yy = isDeck ? Math.round((curY - d / 2) / RoomUnits.DECK_GRID_CM) * RoomUnits.DECK_GRID_CM + d / 2 : curY;
        RoomTower.Lay lay = null;
        boolean on = false;
        double outY = isDeck ? yy : curY;
        if (cand != null && bh >= cand.top - 15) {
            double lo = cand.y0 + d / 2;
            double hi = cand.y1 - d / 2;
            outY = step(lo <= hi ? Math.max(lo, Math.min(hi, yy)) : (cand.y0 + cand.y1) / 2);
            on = true;
            if (isTower && cand.kind.equals("table")) {
                lay = RoomTower.Lay.ON;
            }
        } else if (isTower) {
            for (Host h : hosts) {
                if (h.kind.equals("table") && x >= h.x0 && x <= h.x1 && self.height <= h.h - 5
                        && curY + d / 2 > h.y0 && curY - d / 2 < h.y1) {
                    lay = RoomTower.Lay.UNDER;
                    break;
                }
            }
        }
        return new ElevationSnap(x, outY, lay, on);
    }

    /** Применить результат {@link #elevationSnap} к объекту. */
    public static void applyElevation(ControlRoomLayout layout, RoomSelection.Type kind, String id, ElevationSnap s) {
        if (kind == RoomSelection.Type.TOWER) {
            RoomTower t = tower(layout, id);
            if (t != null) {
                t.setXCm(s.x());
                t.setYCm(s.y());
                t.setLay(s.lay());
            }
        } else if (kind == RoomSelection.Type.FURN) {
            RoomFurniture f = furniture(layout, id);
            if (f != null) {
                f.setXCm(s.x());
                f.setYCm(s.y());
                f.setOnTop(s.on());
            }
        }
    }

    // ---------------------------------------------------------------- удаление, поворот

    /** Убрать выбранное. Трасса: мелочь на ней остаётся лежать свободно там, где была. */
    public static void removeSelection(ControlRoomLayout layout, RoomSelection sel) {
        if (sel == null) {
            return;
        }
        switch (sel.type()) {
            case RITEM, SITEM -> {
                for (RoomTower t : layout.getTowers()) {
                    for (RoomSegment seg : t.getSegments()) {
                        if (seg.getItems().removeIf(i -> i.getId().equals(sel.id()))) {
                            return;
                        }
                    }
                }
            }
            case TOWER -> layout.getTowers().removeIf(t -> t.getId().equals(sel.id()));
            case FURN -> layout.getFurniture().removeIf(f -> f.getId().equals(sel.id()));
            case LOOSE -> layout.getLoose().removeIf(l -> l.getId().equals(sel.id()));
            case RUN -> {
                RoomRun r = run(layout, sel.id());
                if (r == null) {
                    return;
                }
                for (RoomLooseItem l : layout.getLoose()) {
                    if (l.getMode() == RoomLooseItem.Mode.RUN && r.getId().equals(l.getRunId())) {
                        RoomPoint p = RoomRunMath.pointAt(r, l.getSCm() != null ? l.getSCm() : 0);
                        l.setMode(RoomLooseItem.Mode.FREE);
                        l.setXCm(p.getX());
                        l.setYCm(p.getY());
                        l.setRunId(null);
                        l.setSCm(null);
                    }
                }
                layout.getRuns().remove(r);
            }
        }
    }

    /** Повернуть башню или мебель на 90°. */
    public static void rotate(ControlRoomLayout layout, RoomSelection sel) {
        if (sel == null) {
            return;
        }
        if (sel.type() == RoomSelection.Type.TOWER) {
            RoomTower t = tower(layout, sel.id());
            if (t != null) {
                t.setRotation((t.getRotation() + 90) % 360);
            }
        } else if (sel.type() == RoomSelection.Type.FURN) {
            RoomFurniture f = furniture(layout, sel.id());
            if (f != null) {
                f.setRotation((f.getRotation() + 90) % 360);
            }
        }
    }

    // ---------------------------------------------------------------- трассы

    /** Новая трасса по нарисованным точкам; меньше двух точек — {@code null}. Название — тип и номер. */
    public static RoomRun addRun(ControlRoomLayout layout, RoomRunKind kind, List<RoomPoint> points) {
        if (points == null || points.size() < 2) {
            return null;
        }
        int n = 1;
        for (RoomRun r : layout.getRuns()) {
            if (RoomRunKind.of(r.getKind()) == kind) {
                n++;
            }
        }
        RoomRun r = new RoomRun();
        r.setKind(kind.key());
        r.setName(kind.title() + (n > 1 ? " " + n : ""));
        for (RoomPoint p : points) {
            r.getPoints().add(new RoomPoint(p.getX(), p.getY()));
        }
        layout.getRuns().add(r);
        return r;
    }

    /** Добавить вершину в середину отрезка {@code segment} (после вершины с этим номером). */
    public static void insertRunPoint(RoomRun run, int segment) {
        List<RoomPoint> p = run.getPoints();
        if (segment < 0 || segment >= p.size() - 1) {
            return;
        }
        RoomPoint a = p.get(segment);
        RoomPoint b = p.get(segment + 1);
        p.add(segment + 1, new RoomPoint(step((a.getX() + b.getX()) / 2), step((a.getY() + b.getY()) / 2)));
    }

    /** Убрать вершину; в трассе остаётся не меньше двух. */
    public static boolean removeRunPoint(RoomRun run, int index) {
        if (run.getPoints().size() <= 2 || index < 0 || index >= run.getPoints().size()) {
            return false;
        }
        run.getPoints().remove(index);
        return true;
    }

    /** Переместить вершину с привязкой к сетке и стенам. */
    public static void moveRunPoint(ControlRoomLayout layout, RoomRun run, int index, double x, double y) {
        if (index < 0 || index >= run.getPoints().size()) {
            return;
        }
        RoomPoint q = RoomRunMath.snapPoint(layout.getRoomWidthCm(), layout.getRoomDepthCm(), x, y);
        run.getPoints().get(index).setX(q.getX());
        run.getPoints().get(index).setY(q.getY());
    }

    // ---------------------------------------------------------------- стойки: слоты, стопки, слои

    /** Куда ляжет блок в слот кофра: {@code ok} — помещается; {@code insert} — со сдвигом соседей ({@code push}). */
    public record SlotPlan(boolean ok, int u0, int c0, int cw, boolean insert, RoomRackLayout.Push push) {
    }

    /**
     * Куда ляжет блок при перетаскивании в кофр (порт {@code target()} прототипа, ветка «slot»).
     * {@code yRows} — положение указателя от верха кофра в юнитах (дробное), {@code relX} — от левого края стойки,
     * 0..1. Сначала пробуется свободное место под указателем; если занято — вставка на ближайшую границу ряда со
     * сдвигом соседей вниз (как на фото пользователя), если и так не помещается — {@code ok == false}.
     */
    public static SlotPlan planSlot(RoomCatalog cat, RoomSegment seg, RoomItemSpec spec, String selfId, double yRows,
                                    double relX) {
        int caseU = cat.resolve(seg.getCaseRef()).rackUnits();
        int u = spec.rackUnits();
        int cw = spec.widthTwelfths();
        int u0 = (int) Math.round(yRows - u / 2.0);
        u0 = Math.max(0, Math.min(caseU - u, u0));
        int kk = (int) Math.max(0, Math.min(12.0 / cw - 1, Math.floor(relX * 12 / cw)));
        int c0 = kk * cw;
        if (RoomRackLayout.fits(cat, seg, u0, u, selfId, c0, cw)) {
            return new SlotPlan(true, u0, c0, cw, false, null);
        }
        int boundary = (int) Math.max(0, Math.min(caseU, Math.round(yRows)));
        RoomRackLayout.Push push = RoomRackLayout.pushLayout(cat, seg, spec, boundary, c0, selfId);
        if (push != null) {
            return new SlotPlan(true, push.u0(), c0, cw, true, push);
        }
        return new SlotPlan(false, u0, c0, cw, false, null);
    }

    /** Найденный блок или предмет и слой, в котором он лежит. */
    public record Located(RoomTower tower, RoomSegment segment, RoomRackItem item) {
    }

    public static Located locateItem(ControlRoomLayout layout, String itemId) {
        for (RoomTower t : layout.getTowers()) {
            for (RoomSegment seg : t.getSegments()) {
                for (RoomRackItem it : seg.getItems()) {
                    if (it.getId().equals(itemId)) {
                        return new Located(t, seg, it);
                    }
                }
            }
        }
        return null;
    }

    public static RoomSegment segment(RoomTower tower, String segId) {
        if (tower != null) {
            for (RoomSegment seg : tower.getSegments()) {
                if (seg.getId().equals(segId)) {
                    return seg;
                }
            }
        }
        return null;
    }

    /** Положить блок по плану {@link #planSlot}; {@code movedId} — переносимый блок (убирается откуда лежал). */
    public static RoomSelection dropIntoSlot(ControlRoomLayout layout, String towerId, String segId, SlotPlan plan,
                                             RoomLibRef ref, String movedId) {
        RoomTower t = tower(layout, towerId);
        RoomSegment seg = segment(t, segId);
        if (seg == null || !plan.ok()) {
            return null;
        }
        if (movedId != null) {
            Located from = locateItem(layout, movedId);
            if (from != null) {
                from.segment().getItems().remove(from.item());
            }
        }
        if (plan.insert() && plan.push() != null) {
            for (RoomRackItem it : seg.getItems()) {
                Integer to = plan.push().moves().get(it.getId());
                if (to != null) {
                    it.setU0(to);
                }
            }
        }
        RoomRackItem item = new RoomRackItem();
        if (movedId != null) {
            item.setId(movedId);
        }
        item.setRef(ref);
        item.setU0(plan.u0());
        item.setC0(plan.c0());
        seg.getItems().add(item);
        return RoomSelection.rackItem(item.getId(), t.getId(), seg.getId());
    }

    /**
     * Положить блок или мелочь в стопку без кофра. {@code towerId == null} — в новую башню-стопку на свободное
     * место; {@code segId == null} — в верхнюю стопку башни (создаётся при необходимости); {@code posTop} — место
     * в стопке сверху (0 — самый верх).
     */
    public static RoomSelection dropIntoStack(ControlRoomLayout layout, RoomCatalog cat, String towerId, String segId,
                                              int posTop, RoomLibRef ref, String movedId) {
        RoomTower t = towerId == null ? null : tower(layout, towerId);
        RoomSegment seg = segment(t, segId);
        boolean same = false;
        RoomRackItem item = new RoomRackItem();
        item.setRef(ref);
        if (movedId != null) {
            Located from = locateItem(layout, movedId);
            if (from != null) {
                item.setId(movedId);
                same = seg != null && from.segment() == seg;
                if (!same) {
                    from.segment().getItems().remove(from.item());
                }
            }
        }
        if (t == null) {
            RoomItemSpec s = cat.resolve(ref);
            double[] spot = findSpot(layout, cat, Math.max(s.widthCm(), 20), Math.max(s.depthCm(), 20));
            t = RoomRackLayout.newStackTower(layout, null, spot[0], spot[1]);
        }
        if (seg == null) {
            seg = RoomRackLayout.topStack(t);
            posTop = 0;
        }
        RoomRackLayout.insertStack(seg, item, posTop, same ? item.getId() : null);
        return RoomSelection.stackItem(item.getId(), t.getId(), seg.getId());
    }

    /** Добавить кофр слоем в башню на позицию {@code posTop} (0 — сверху). */
    public static RoomSelection dropCaseIntoTower(ControlRoomLayout layout, String towerId, int posTop, RoomLibRef caseRef) {
        RoomTower t = tower(layout, towerId);
        if (t == null) {
            return null;
        }
        RoomSegment seg = new RoomSegment();
        seg.setCaseRef(caseRef);
        RoomRackLayout.insertSegment(t, seg, posTop, null);
        return RoomSelection.tower(t.getId());
    }

    /** Перенести слой (кофр или стопку) в другую башню или в другое место этой же; пустая исходная башня исчезает. */
    public static RoomSelection moveSegment(ControlRoomLayout layout, String srcTowerId, String segId, String dstTowerId,
                                            int posTop) {
        RoomTower src = tower(layout, srcTowerId);
        RoomTower dst = tower(layout, dstTowerId);
        RoomSegment seg = segment(src, segId);
        if (src == null || dst == null || seg == null) {
            return null;
        }
        boolean same = src == dst;
        if (!same) {
            src.getSegments().remove(seg);
            if (src.getSegments().isEmpty()) {
                layout.getTowers().remove(src);
            }
        }
        RoomRackLayout.insertSegment(dst, seg, posTop, same ? seg.getId() : null);
        return RoomSelection.tower(dst.getId());
    }

    /** Вынести слой в новую башню на свободное место площадки. */
    public static RoomSelection liftSegmentToNewTower(ControlRoomLayout layout, RoomCatalog cat, String srcTowerId,
                                                      String segId) {
        RoomTower src = tower(layout, srcTowerId);
        RoomSegment seg = segment(src, segId);
        if (src == null || seg == null) {
            return null;
        }
        src.getSegments().remove(seg);
        if (src.getSegments().isEmpty()) {
            layout.getTowers().remove(src);
        }
        double[] spot = findSpot(layout, cat, 60, 70);
        RoomTower nt = RoomRackLayout.newStackTower(layout, null, spot[0], spot[1]);
        nt.getSegments().clear();
        nt.getSegments().add(seg);
        return RoomSelection.tower(nt.getId());
    }

    /** Поднять/опустить предмет в стопке на одно место («Выше» / «Ниже» в панели выбранного). */
    public static void moveStackItem(ControlRoomLayout layout, String itemId, boolean up) {
        Located f = locateItem(layout, itemId);
        if (f == null || !f.segment().isStack()) {
            return;
        }
        List<RoomRackItem> items = f.segment().getItems(); // снизу вверх
        int i = items.indexOf(f.item());
        int j = up ? i + 1 : i - 1;
        if (j < 0 || j >= items.size()) {
            return;
        }
        items.set(i, items.get(j));
        items.set(j, f.item());
    }

    /** Переименовать башню (название в шапке башни; пустое — без названия). */
    public static void renameTower(ControlRoomLayout layout, String towerId, String name) {
        RoomTower t = tower(layout, towerId);
        if (t != null) {
            t.setName(name == null ? "" : name.trim());
        }
    }

    // ---------------------------------------------------------------- перетаскивание на план

    /** Башня под точкой плана (по повёрнутому прямоугольнику); последняя в списке главнее; {@code null} — нет. */
    public static RoomTower towerAt(ControlRoomLayout layout, RoomCatalog cat, double x, double y) {
        RoomTower found = null;
        for (RoomTower t : layout.getTowers()) {
            RoomTowerCalc c = RoomTowerCalc.of(t, cat);
            double a = Math.toRadians(-t.getRotation());
            double dx = x - t.getXCm();
            double dy = y - t.getYCm();
            double lx = dx * Math.cos(a) - dy * Math.sin(a);
            double ly = dx * Math.sin(a) + dy * Math.cos(a);
            if (Math.abs(lx) <= c.widthCm() / 2 && Math.abs(ly) <= c.depthCm() / 2) {
                found = t;
            }
        }
        return found;
    }

    /**
     * Карточка библиотеки брошена на план в точку (x, y) (порт ветки «plan» прототипа). Кофр — новая башня;
     * блок в стойку — в башню под указателем (в слот, иначе стопкой сверху), а мимо башен — новая башня-стопка;
     * мелочь — в башню стопкой либо на площадку с привязкой к трассе; мебель, станок, монитор — на место
     * по сетке (станок — 50 см, остальное — 5 см).
     */
    public static Added dropOnPlan(ControlRoomLayout layout, RoomCatalog cat, RoomItemSpec spec, double x, double y) {
        RoomLibRef ref = spec.ref();
        double sx = step(x);
        double sy = step(y);
        switch (spec.type()) {
            case CASE: {
                RoomTower t = RoomRackLayout.newTower(layout, ref, sx, sy);
                return new Added(RoomSelection.tower(t.getId()), "Новая башня собрана");
            }
            case LOOSE: {
                RoomTower t = towerAt(layout, cat, x, y);
                if (t != null) {
                    RoomRackItem it = RoomRackLayout.addToStack(t, ref);
                    return new Added(RoomSelection.stackItem(it.getId(), t.getId(), RoomRackLayout.topStack(t).getId()),
                            "«" + spec.name() + "» лёг стопкой в башню " + t.getTag());
                }
                RoomLooseItem l = placeLoose(layout, ref, x, y);
                return new Added(RoomSelection.loose(l.getId()), "Мелочь добавлена на площадку");
            }
            case RACK: {
                RoomTower t = towerAt(layout, cat, x, y);
                if (t == null) {
                    RoomTower nt = RoomRackLayout.newStackTower(layout, ref, sx, sy);
                    return new Added(RoomSelection.tower(nt.getId()), "Новая башня: оборудование лежит без кофра");
                }
                boolean ok = RoomRackLayout.autoPlace(cat, t, spec);
                if (!ok) {
                    RoomRackLayout.addToStack(t, ref);
                }
                return new Added(RoomSelection.tower(t.getId()), ok ? "Добавлено в кофр башни " + t.getTag()
                        : "Нет места в кофрах: лёг стопкой сверху башни " + t.getTag());
            }
            default: {
                boolean deck = spec.type() == RoomItemSpec.Type.DECK;
                double[] p = snapPosition(deck, spec.widthCm(), spec.depthCm(), x, y);
                RoomFurniture f = new RoomFurniture();
                f.setRef(ref);
                f.setXCm(p[0]);
                f.setYCm(p[1]);
                layout.getFurniture().add(f);
                return new Added(RoomSelection.furniture(f.getId()), "«" + spec.name() + "» добавлен на план");
            }
        }
    }

    /**
     * Карточка брошена на вид спереди. Принимаются только мебель, станок и монитор; станок можно сразу положить
     * сверху на станки пола. Возвращает {@code null}, если такой вид нельзя поставить на вид спереди.
     */
    public static Added dropOnElevation(ControlRoomLayout layout, RoomCatalog cat, RoomItemSpec spec, double x,
                                        double heightCm) {
        if (spec.type() != RoomItemSpec.Type.FURNITURE && spec.type() != RoomItemSpec.Type.DECK
                && spec.type() != RoomItemSpec.Type.MONITOR && spec.type() != RoomItemSpec.Type.DEVICE) {
            return null;
        }
        RoomFurniture f = new RoomFurniture();
        f.setRef(spec.ref());
        f.setXCm(x);
        f.setYCm(layout.getRoomDepthCm() / 2);
        for (RoomBounds b : RoomGeometry.bounds(layout, cat)) {
            if (b.deck && !b.onTop) {
                f.setYCm(b.cy());
                break;
            }
        }
        layout.getFurniture().add(f);
        ElevationSnap snap = elevationSnap(layout, cat, RoomSelection.Type.FURN, f.getId(), x, heightCm);
        f.setXCm(snap.x());
        f.setYCm(snap.y());
        if (snap.on() && spec.type() == RoomItemSpec.Type.DECK) {
            f.setOnTop(true);
        }
        return new Added(RoomSelection.furniture(f.getId()), "«" + spec.name() + "» добавлен");
    }

    /** Положить мелочь в кейс мелочёвки (карточка брошена на «Кофр мелочёвки»). */
    public static RoomLooseItem addLooseToBox(ControlRoomLayout layout, RoomLibRef ref) {
        RoomLooseItem l = new RoomLooseItem();
        l.setRef(ref);
        l.setMode(RoomLooseItem.Mode.BOX);
        layout.getLoose().add(l);
        return l;
    }
}
