package com.vjstb.ledscheme.service.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomSegment;
import com.vjstb.ledscheme.model.RoomTower;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Геометрия плана пультовой: габариты объектов с поворотом, пересечения, уровни станков,
 * опора под центром и положение башни относительно стола/станка. Вынесено из прототипа
 * ({@code planner.html}: {@code bounds}, {@code overlaps}, {@code deckHAt}, {@code deckInfo},
 * {@code hostOf}, {@code layMode}, {@code towerBase}, {@code supportAt}); чистая логика без
 * Swing. Решения пользователя, которые здесь воплощены: станки работают как пол или как стол
 * (станок можно поставить сверху на станки пола), башню можно ставить на стол/станок и под
 * них, линия глаз оператора отсчитывается от пола станка.
 */
public final class RoomGeometry {

    /** Эффективное положение башни относительно опоры под её центром. */
    public enum EffLay {
        /** Опоры под центром нет (или это пол). */
        FLOOR,
        /** Стоит сверху на столе/станке. */
        ON,
        /** Стоит под столом/станком. */
        UNDER
    }

    /** Итоги по полу из станков: число станков, габариты нижнего ряда и его площадь. */
    public record DeckInfo(int count, double widthCm, double depthCm, double areaM2) {
    }

    /** Линия глаз сидящего оператора: высота пола под стулом, высота глаз от земли и x стульев
     *  ({@code null} — стульев нет, линия на всю ширину). */
    public record EyeLevel(double deckHeightCm, double eyeHeightCm, List<Double> xs) {
    }

    /** Глаза сидящего оператора над полом, на котором стоит стул, см. */
    public static final double EYE_ABOVE_FLOOR_CM = 120;

    private RoomGeometry() {
    }

    /** Параметры мебели на плане с переопределениями объекта. */
    public static RoomItemSpec effective(RoomFurniture f, RoomCatalog cat) {
        return cat.resolve(f.getRef()).withOverrides(f.getOverrides());
    }

    public static List<RoomBounds> bounds(ControlRoomLayout layout, RoomCatalog cat) {
        List<RoomBounds> out = new ArrayList<>();
        for (RoomTower t : layout.getTowers()) {
            RoomTowerCalc c = RoomTowerCalc.of(t, cat);
            boolean onlyStack = true;
            for (RoomSegment s : t.getSegments()) {
                if (!s.isStack()) {
                    onlyStack = false;
                }
            }
            out.add(new RoomBounds(RoomBounds.Type.TOWER, t.getId(), "Башня " + t.getTag(), t.getXCm() - c.footW() / 2,
                    t.getXCm() + c.footW() / 2, t.getYCm() - c.footD() / 2, t.getYCm() + c.footD() / 2, c.heightCm(),
                    c.weightKg(), false, false, false, false, onlyStack, t.getLay(), t, null, null, c));
        }
        for (RoomFurniture f : layout.getFurniture()) {
            RoomItemSpec s = effective(f, cat);
            boolean swap = f.getRotation() % 180 != 0;
            double w = swap ? s.depthCm() : s.widthCm();
            double d = swap ? s.widthCm() : s.depthCm();
            out.add(new RoomBounds(RoomBounds.Type.FURNITURE, f.getId(), s.name(), f.getXCm() - w / 2,
                    f.getXCm() + w / 2, f.getYCm() - d / 2, f.getYCm() + d / 2, s.heightCm(), s.weightKg(),
                    s.type() == RoomItemSpec.Type.DECK, s.type() == RoomItemSpec.Type.MONITOR,
                    s.role() == RoomItemSpec.Role.CHAIR, f.isOnTop(), false, null, null, f, s, null));
        }
        List<RoomBounds> floorDecks = new ArrayList<>();
        for (RoomBounds b : out) {
            if (b.deck && !b.onTop) {
                floorDecks.add(b);
            }
        }
        for (RoomBounds b : out) {
            if (!b.deck) {
                continue;
            }
            b.base = 0;
            b.top = b.height;
            if (b.onTop) {
                double under = 0;
                for (RoomBounds d : floorDecks) {
                    if (d.containsCenterOf(b)) {
                        under = Math.max(under, d.height);
                    }
                }
                b.base = under;
                b.top = under + b.height;
            }
        }
        return out;
    }

    public static boolean overlaps(RoomBounds a, RoomBounds b) {
        return a.x0 < b.x1 - .5 && a.x1 > b.x0 + .5 && a.y0 < b.y1 - .5 && a.y1 > b.y0 + .5;
    }

    public static List<RoomBounds> decks(List<RoomBounds> all) {
        List<RoomBounds> d = new ArrayList<>();
        for (RoomBounds b : all) {
            if (b.deck) {
                d.add(b);
            }
        }
        return d;
    }

    /** Высота верхней плоскости станков в точке (максимальная из накрывающих); −1 — станков нет. */
    public static double deckHeightAt(List<RoomBounds> decks, double x, double y) {
        double h = -1;
        for (RoomBounds d : decks) {
            if (x >= d.x0 - .01 && x <= d.x1 + .01 && y >= d.y0 - .01 && y <= d.y1 + .01) {
                h = Math.max(h, d.top);
            }
        }
        return h;
    }

    /** То же, но 0 вместо −1 (на земле). */
    public static double floorHeightAt(List<RoomBounds> all, double x, double y) {
        return Math.max(0, deckHeightAt(decks(all), x, y));
    }

    public static DeckInfo deckInfo(List<RoomBounds> all) {
        List<RoomBounds> every = decks(all);
        if (every.isEmpty()) {
            return new DeckInfo(0, 0, 0, 0);
        }
        List<RoomBounds> lower = new ArrayList<>();
        for (RoomBounds b : every) {
            if (!b.onTop) {
                lower.add(b);
            }
        }
        List<RoomBounds> base = lower.isEmpty() ? every : lower;
        double x0 = Double.MAX_VALUE;
        double y0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE;
        double y1 = -Double.MAX_VALUE;
        double area = 0;
        for (RoomBounds b : base) {
            x0 = Math.min(x0, b.x0);
            y0 = Math.min(y0, b.y0);
            x1 = Math.max(x1, b.x1);
            y1 = Math.max(y1, b.y1);
            area += (b.x1 - b.x0) * (b.y1 - b.y0) / 10000.0;
        }
        return new DeckInfo(every.size(), x1 - x0, y1 - y0, area);
    }

    /** Опора под центром башни: стол/тумба приоритетнее станка; {@code null} — опоры нет. */
    public static RoomBounds hostOf(RoomBounds tower, List<RoomBounds> all) {
        RoomBounds best = null;
        int bestRank = 0;
        for (RoomBounds o : all) {
            if (o.type != RoomBounds.Type.FURNITURE || o.monitor || o.chair) {
                continue;
            }
            if (o.containsCenterOf(tower)) {
                int rank = o.deck ? 1 : 2;
                if (best == null || rank > bestRank) {
                    best = o;
                    bestRank = rank;
                }
            }
        }
        return best;
    }

    /**
     * Положение башни. Явно заданное {@code lay} главнее. Иначе: станок — «на»; стол — «на», если
     * башня только из стопки без кофров или выше {@code стол − 5 см}, иначе «под» (низкая башня с
     * кофрами прячется под стол).
     */
    public static EffLay layMode(RoomBounds tower, RoomBounds host) {
        if (host == null) {
            return EffLay.FLOOR;
        }
        if (tower.lay == RoomTower.Lay.ON) {
            return EffLay.ON;
        }
        if (tower.lay == RoomTower.Lay.UNDER) {
            return EffLay.UNDER;
        }
        if (host.deck) {
            return EffLay.ON;
        }
        return (tower.onlyStack || tower.height > host.height - 5) ? EffLay.ON : EffLay.UNDER;
    }

    /** Высота основания башни над землёй: на столе — уровень пола под ней плюс высота стола, под
     *  столом — уровень пола под ним, под станком — земля. */
    public static double towerBase(RoomBounds tower, List<RoomBounds> all) {
        RoomBounds host = hostOf(tower, all);
        double dat = floorHeightAt(all, tower.cx(), tower.cy());
        if (host == null) {
            return dat;
        }
        if (layMode(tower, host) == EffLay.UNDER) {
            return host.deck ? 0 : dat;
        }
        return dat + (host.deck ? 0 : host.height);
    }

    /** Высота опоры (стол, тумба или башня) под точкой — на неё ставится монитор. */
    public static double supportAt(List<RoomBounds> all, double x, double y) {
        double h = 0;
        for (RoomBounds o : all) {
            boolean support = o.isTower() || o.isTableLike();
            if (support && x >= o.x0 && x <= o.x1 && y >= o.y0 && y <= o.y1) {
                h = Math.max(h, o.height);
            }
        }
        return h;
    }

    /** Линии глаз: по одной на высоту пола под стульями; без стульев — одна от самого высокого станка. */
    public static List<EyeLevel> eyeLevels(List<RoomBounds> all) {
        List<RoomBounds> decks = decks(all);
        Map<Double, List<Double>> byFloor = new LinkedHashMap<>();
        for (RoomBounds o : all) {
            if (o.chair) {
                double eh = Math.max(0, deckHeightAt(decks, o.cx(), o.cy()));
                byFloor.computeIfAbsent(eh, k -> new ArrayList<>()).add(o.cx());
            }
        }
        List<EyeLevel> out = new ArrayList<>();
        if (byFloor.isEmpty()) {
            double max = 0;
            for (RoomBounds d : decks) {
                max = Math.max(max, d.top);
            }
            out.add(new EyeLevel(max, max + EYE_ABOVE_FLOOR_CM, null));
        } else {
            for (Map.Entry<Double, List<Double>> e : byFloor.entrySet()) {
                out.add(new EyeLevel(e.getKey(), e.getKey() + EYE_ABOVE_FLOOR_CM, e.getValue()));
            }
        }
        return out;
    }
}
