package com.vjstb.ledscheme.service.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.model.RoomRackItem;
import com.vjstb.ledscheme.model.RoomSegment;
import com.vjstb.ledscheme.model.RoomTower;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Раскладка блоков внутри башни: слоты кофра (ряд юнитов × колонка в двенадцатых), вставка
 * «между позициями» со сдвигом соседей, стопки без кофра и порядок слоёв башни. Вынесено из
 * прототипа ({@code planner.html}: {@code fits}, {@code pushLayout}, {@code autoPlace},
 * {@code insertStack}, {@code insertSeg}, {@code topStack}, {@code addToStack}); чистая логика
 * без Swing, размеры берёт у каталога ({@link RoomCatalog}).
 *
 * <p>Мотивация вставки со сдвигом (запрос пользователя 2026-10-23): блок, брошенный на занятое
 * место, должен вставляться между соседями — как в стопке, — а не отвергаться; нижние блоки
 * сдвигаются вниз только настолько, насколько нужно, и только если в кофре хватает места.
 *
 * <p>Слои башни ({@link RoomTower#getSegments()}) и элементы стопки хранятся СНИЗУ ВВЕРХ, а
 * позиции вставки в параметрах ({@code posTop}) считаются СВЕРХУ ВНИЗ — как их видит
 * пользователь на экране.
 */
public final class RoomRackLayout {

    /** Результат вставки: верхний юнит нового блока и новые верхние юниты сдвинутых соседей. */
    public record Push(int u0, Map<String, Integer> moves) {
    }

    private RoomRackLayout() {
    }

    /** Помещается ли блок высотой {@code u} и шириной {@code cw} (в двенадцатых) в слот
     *  {@code (u0, c0)} кофра, не пересекаясь с остальными (блок {@code selfId} не считается). */
    public static boolean fits(RoomCatalog cat, RoomSegment seg, int u0, int u, String selfId, int c0, int cw) {
        int caseU = cat.resolve(seg.getCaseRef()).rackUnits();
        if (u0 < 0 || u0 + u > caseU || c0 < 0 || c0 + cw > 12) {
            return false;
        }
        for (RoomRackItem it : seg.getItems()) {
            if (it.getId().equals(selfId)) {
                continue;
            }
            RoomItemSpec s = cat.resolve(it.getRef());
            boolean rows = u0 + u <= it.getU0() || u0 >= it.getU0() + s.rackUnits();
            boolean cols = c0 + cw <= it.getC0() || c0 >= it.getC0() + s.widthTwelfths();
            if (!rows && !cols) {
                return false;
            }
        }
        return true;
    }

    /**
     * Вставка блока на границу ряда {@code boundary} со сдвигом соседей вниз. Граница не может
     * резать блок в тех же колонках — тогда она сдвигается к ближайшему краю этого блока.
     * Соседи сдвигаются каскадом и минимально; если что-то не помещается в кофр — {@code null}.
     */
    public static Push pushLayout(RoomCatalog cat, RoomSegment seg, RoomItemSpec spec, int boundary, int c0,
                                  String selfId) {
        int caseU = cat.resolve(seg.getCaseRef()).rackUnits();
        int cw = spec.widthTwelfths();
        int b = boundary;
        List<int[]> its = new ArrayList<>(); // {u0, u, c0, cw}
        List<String> ids = new ArrayList<>();
        for (RoomRackItem it : seg.getItems()) {
            if (it.getId().equals(selfId)) {
                continue;
            }
            RoomItemSpec s = cat.resolve(it.getRef());
            its.add(new int[] {it.getU0(), s.rackUnits(), it.getC0(), s.widthTwelfths()});
            ids.add(it.getId());
        }
        for (int[] x : its) {
            boolean sameCols = x[2] < c0 + cw && x[2] + x[3] > c0;
            if (sameCols && x[0] < b && b < x[0] + x[1]) {
                b = (b - x[0] <= x[0] + x[1] - b) ? x[0] : x[0] + x[1];
            }
        }
        if (b + spec.rackUnits() > caseU) {
            return null;
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < its.size(); i++) {
            order.add(i);
        }
        order.sort((p, q) -> Integer.compare(its.get(p)[0], its.get(q)[0]));
        List<int[]> placed = new ArrayList<>();
        placed.add(new int[] {b, spec.rackUnits(), c0, cw});
        Map<String, Integer> moves = new HashMap<>();
        for (int idx : order) {
            int[] x = its.get(idx);
            int top = x[0];
            boolean again = true;
            int guard = 0;
            while (again && guard++ < 60) {
                again = false;
                for (int[] pl : placed) {
                    boolean cols = pl[2] < x[2] + x[3] && pl[2] + pl[3] > x[2];
                    if (cols && top < pl[0] + pl[1] && top + x[1] > pl[0]) {
                        top = pl[0] + pl[1];
                        again = true;
                    }
                }
            }
            if (top + x[1] > caseU) {
                return null;
            }
            if (top != x[0]) {
                moves.put(ids.get(idx), top);
            }
            placed.add(new int[] {top, x[1], x[2], x[3]});
        }
        return new Push(b, moves);
    }

    /** Положить блок в первый подходящий слот первого кофра сверху вниз; {@code false} — места нет. */
    public static boolean autoPlace(RoomCatalog cat, RoomTower tower, RoomItemSpec spec) {
        List<RoomSegment> top = new ArrayList<>(tower.getSegments());
        Collections.reverse(top);
        int cw = spec.widthTwelfths();
        for (RoomSegment seg : top) {
            if (seg.isStack()) {
                continue;
            }
            int caseU = cat.resolve(seg.getCaseRef()).rackUnits();
            for (int u0 = 0; u0 + spec.rackUnits() <= caseU; u0++) {
                for (int c0 = 0; c0 + cw <= 12; c0 += cw) {
                    if (fits(cat, seg, u0, spec.rackUnits(), null, c0, cw)) {
                        RoomRackItem item = new RoomRackItem();
                        item.setRef(spec.ref());
                        item.setU0(u0);
                        item.setC0(c0);
                        seg.getItems().add(item);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Верхний слой башни, если это стопка; иначе новая пустая стопка сверху. */
    public static RoomSegment topStack(RoomTower tower) {
        List<RoomSegment> segs = tower.getSegments();
        if (!segs.isEmpty() && segs.get(segs.size() - 1).isStack()) {
            return segs.get(segs.size() - 1);
        }
        RoomSegment seg = new RoomSegment();
        seg.setStack(true);
        segs.add(seg);
        return seg;
    }

    /** Вставка в стопку на позицию {@code posTop} (0 — самый верхний). {@code movedId} — id
     *  переносимого внутри этой же стопки элемента: он сначала убирается, позиция корректируется. */
    public static void insertStack(RoomSegment seg, RoomRackItem item, int posTop, String movedId) {
        List<RoomRackItem> top = new ArrayList<>(seg.getItems());
        Collections.reverse(top);
        int pos = posTop;
        if (movedId != null) {
            int oi = -1;
            for (int k = 0; k < top.size(); k++) {
                if (top.get(k).getId().equals(movedId)) {
                    oi = k;
                }
            }
            if (oi >= 0) {
                top.remove(oi);
                if (oi < pos) {
                    pos--;
                }
            }
        }
        top.add(Math.max(0, Math.min(top.size(), pos)), item);
        Collections.reverse(top);
        seg.setItems(top);
    }

    /** Положить позицию сверху стопки башни (стопка создаётся при необходимости). */
    public static RoomRackItem addToStack(RoomTower tower, RoomLibRef ref) {
        RoomSegment seg = topStack(tower);
        RoomRackItem item = new RoomRackItem();
        item.setRef(ref);
        insertStack(seg, item, 0, null);
        return item;
    }

    /** Вставка слоя в башню на позицию {@code posTop} (0 — самый верхний слой). */
    public static void insertSegment(RoomTower tower, RoomSegment seg, int posTop, String movedId) {
        List<RoomSegment> top = new ArrayList<>(tower.getSegments());
        Collections.reverse(top);
        int pos = posTop;
        if (movedId != null) {
            int oi = -1;
            for (int k = 0; k < top.size(); k++) {
                if (top.get(k).getId().equals(movedId)) {
                    oi = k;
                }
            }
            if (oi >= 0) {
                top.remove(oi);
                if (oi < pos) {
                    pos--;
                }
            }
        }
        top.add(Math.max(0, Math.min(top.size(), pos)), seg);
        Collections.reverse(top);
        tower.setSegments(top);
    }

    /** Следующая свободная буква башни (A, B, …). */
    public static String nextTag(ControlRoomLayout layout) {
        Set<String> used = new java.util.HashSet<>();
        for (RoomTower t : layout.getTowers()) {
            used.add(t.getTag());
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            if (!used.contains(String.valueOf(c))) {
                return String.valueOf(c);
            }
        }
        return "?";
    }

    /** Новая башня с одним слоем-кофром. */
    public static RoomTower newTower(ControlRoomLayout layout, RoomLibRef caseRef, double xCm, double yCm) {
        RoomTower t = baseTower(layout, xCm, yCm);
        RoomSegment seg = new RoomSegment();
        seg.setCaseRef(caseRef);
        t.getSegments().add(seg);
        layout.getTowers().add(t);
        return t;
    }

    /** Новая башня со стопкой без кофра; {@code firstItem} — первый предмет или {@code null}. */
    public static RoomTower newStackTower(ControlRoomLayout layout, RoomLibRef firstItem, double xCm, double yCm) {
        RoomTower t = baseTower(layout, xCm, yCm);
        RoomSegment seg = new RoomSegment();
        seg.setStack(true);
        if (firstItem != null) {
            RoomRackItem it = new RoomRackItem();
            it.setRef(firstItem);
            seg.getItems().add(it);
        }
        t.getSegments().add(seg);
        layout.getTowers().add(t);
        return t;
    }

    private static RoomTower baseTower(ControlRoomLayout layout, double xCm, double yCm) {
        RoomTower t = new RoomTower();
        t.setTag(nextTag(layout));
        t.setName("Башня " + t.getTag());
        t.setXCm(xCm);
        t.setYCm(yCm);
        return t;
    }
}
