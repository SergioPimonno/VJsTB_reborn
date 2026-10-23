package com.vjstb.ledscheme.service.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomLooseItem;
import com.vjstb.ledscheme.model.RoomTower;
import com.vjstb.ledscheme.service.room.RoomWarning.Target;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Расчёт раскладки пультовой: итоги (юниты, вес, нагрузка, пол из станков, число кофров),
 * кейс мелочёвки и ВСЕ замечания с текстами из прототипа ({@code planner.html}: {@code calc},
 * {@code layerPair}, {@code boxCalc}). Чистая логика без Swing; решения пользователя,
 * воплощённые в проверках: башни и мебель на станках не считаются пересечением, башню можно
 * ставить на стол/станок и под них, «стол/станок в воздухе» — ошибка.
 *
 * <p>Пороги и тексты — PLAN.md раздел 7 (не менять без согласования): запас до задней крышки
 * &lt; 5 см, тяжёлое (≥ 15 кг) в верхней половине башни, башня выше 1,5 м, нагрузка &gt; 3680 Вт
 * (16 А при 230 В), башня тяжелее 40 кг на столе, башня под столом ниже стола минус 5 см (под
 * станком — минус 8 см).
 */
public final class RoomPlanCalc {

    /** Нагрузка одной линии 16 А при 230 В, Вт. */
    public static final double LINE_16A_W = 3680;
    /** Размеры кейсов мелочёвки {ширина, глубина, высота}, см — от меньшего к большему. */
    public static final double[][] BOXES = {{35, 25, 15}, {45, 32, 20}, {60, 40, 25}, {80, 50, 30}};

    public record Totals(int towers, int unitsTotal, int unitsUsed, double kg, double powerW, double floorM2, int cases,
                         RoomGeometry.DeckInfo deck) {
    }

    /** Кейс мелочёвки: {@code pick} — подобранный размер {ширина, глубина, высота} или {@code null},
     *  если не помещается даже в самый большой; {@code fill} — заполнение с запасом на поролон. */
    public record BoxCalc(int count, double volumeLiters, double needLiters, double[] pick, double fill, double kg,
                          double powerW) {
    }

    public record Result(List<RoomTowerCalc> towers, List<RoomWarning> warnings, Set<String> bad, Set<String> air,
                         Totals totals, BoxCalc box, List<RoomBounds> bounds) {
    }

    private RoomPlanCalc() {
    }

    public static Result calc(ControlRoomLayout layout, RoomCatalog cat) {
        List<RoomTowerCalc> towers = new ArrayList<>();
        for (RoomTower t : layout.getTowers()) {
            towers.add(RoomTowerCalc.of(t, cat));
        }
        List<RoomBounds> b = RoomGeometry.bounds(layout, cat);
        Set<String> bad = new HashSet<>();
        Set<String> air = new HashSet<>();
        List<RoomWarning> warns = new ArrayList<>();

        for (int i = 0; i < b.size(); i++) {
            RoomBounds a = b.get(i);
            if (a.x0 < 0 || a.y0 < 0 || a.x1 > layout.getRoomWidthCm() || a.y1 > layout.getRoomDepthCm()) {
                bad.add(a.id);
                warns.add(RoomWarning.error(a.name + ": выходит за границы площадки", target(a)));
            }
            for (int j = i + 1; j < b.size(); j++) {
                RoomBounds c = b.get(j);
                if (layerPair(a, c, bad, warns)) {
                    continue;
                }
                if (a.deck != c.deck || a.monitor != c.monitor || (a.deck && c.deck && a.onTop != c.onTop)) {
                    continue;
                }
                if (RoomGeometry.overlaps(a, c)) {
                    bad.add(a.id);
                    bad.add(c.id);
                    warns.add(RoomWarning.error(a.name + " пересекается с «" + c.name + "»", target(a)));
                }
            }
        }

        // станок-стол: должен целиком стоять на нижних станках
        List<RoomBounds> floorDecks = new ArrayList<>();
        for (RoomBounds o : b) {
            if (o.deck && !o.onTop) {
                floorDecks.add(o);
            }
        }
        for (RoomBounds o : b) {
            if (!o.deck || !o.onTop) {
                continue;
            }
            int cov = 0;
            for (double[] q : corners(o)) {
                if (RoomGeometry.deckHeightAt(floorDecks, q[0], q[1]) >= 0) {
                    cov++;
                }
            }
            if (cov < 4) {
                air.add(o.id);
                warns.add(RoomWarning.error(o.name + (cov > 0 ? ": висит в воздухе, часть опоры за краем нижних станков"
                        : ": висит в воздухе, под ним нет станков"), target(o)));
            }
        }
        // всё остальное, что стоит на станках: опора целиком и на одной высоте
        List<RoomBounds> allDecks = RoomGeometry.decks(b);
        if (!allDecks.isEmpty()) {
            for (RoomBounds o : b) {
                if (o.deck || o.lay == RoomTower.Lay.UNDER) {
                    continue;
                }
                List<double[]> pts = new ArrayList<>(corners(o));
                pts.add(new double[] {o.cx(), o.cy()});
                double[] hs = new double[pts.size()];
                boolean any = false;
                boolean all = true;
                for (int k = 0; k < pts.size(); k++) {
                    hs[k] = RoomGeometry.deckHeightAt(allDecks, pts.get(k)[0], pts.get(k)[1]);
                    any |= hs[k] >= 0;
                    all &= hs[k] >= 0;
                }
                if (any && !all) {
                    air.add(o.id);
                    warns.add(o.monitor
                            ? RoomWarning.warning(o.name + ": выходит за край станка", target(o))
                            : RoomWarning.error(o.name + ": висит в воздухе, часть опоры за краем станка", target(o)));
                } else if (all) {
                    boolean differs = false;
                    for (double h : hs) {
                        differs |= h != hs[0];
                    }
                    if (differs) {
                        air.add(o.id);
                        String text = o.name + ": стоит на станках разной высоты, часть опоры в воздухе";
                        warns.add(o.monitor ? RoomWarning.warning(text, target(o)) : RoomWarning.error(text, target(o)));
                    }
                }
            }
        }

        for (RoomTowerCalc c : towers) {
            RoomTower t = c.tower();
            for (RoomTowerCalc.Placed p : c.items()) {
                Target tg = new Target("ritem", p.item().getId(), t.getId());
                double inner = p.caseSpec().innerDepthCm();
                if (p.spec().depthCm() <= inner && inner - p.spec().depthCm() < 5) {
                    warns.add(RoomWarning.warning(p.spec().name() + ": до задней крышки "
                            + RoomUnits.num(inner - p.spec().depthCm(), 0) + " см, кабелям тесно", tg));
                }
                if (p.spec().depthCm() > inner) {
                    warns.add(RoomWarning.error(p.spec().name() + ": глубина " + RoomUnits.num(p.spec().depthCm(), 0)
                            + " см больше, чем внутри кофра (" + RoomUnits.num(inner, 0) + " см)", tg));
                }
                if (p.spec().weightKg() >= 15 && p.upper()) {
                    warns.add(RoomWarning.warning("Башня " + t.getTag() + ": «" + p.spec().name() + "» ("
                            + RoomUnits.num(p.spec().weightKg(), 0) + " кг) в верхней половине — тяжёлое лучше вниз", tg));
                }
            }
            if (c.heightCm() > 150) {
                warns.add(RoomWarning.warning("Башня " + t.getTag() + " выше 1,5 м (" + RoomUnits.num(c.heightCm(), 0)
                        + " см) — может закрывать обзор", new Target("tower", t.getId(), null)));
            }
            if (!t.getSegments().isEmpty() && c.unitsUsed() == 0 && c.stackItems() == 0) {
                warns.add(RoomWarning.info("Башня " + t.getTag() + " пока пустая", new Target("tower", t.getId(), null)));
            }
        }
        long free = layout.getLoose().stream().filter(l -> l.getMode() == RoomLooseItem.Mode.FREE).count();
        if (free > 0) {
            warns.add(RoomWarning.info("Без привязки лежит: " + free
                    + " шт. Это нормально для мелочи, но кабель до неё надо учесть", null));
        }

        int unitsTotal = 0;
        int unitsUsed = 0;
        int cases = 0;
        double kg = 0;
        double p = 0;
        double floor = 0;
        for (RoomTowerCalc c : towers) {
            unitsTotal += c.unitsTotal();
            unitsUsed += c.unitsUsed();
            kg += c.weightKg();
            p += c.powerW();
            floor += c.footW() * c.footD() / 10000.0;
            cases += c.caseCount();
        }
        for (RoomFurniture f : layout.getFurniture()) {
            RoomItemSpec s = RoomGeometry.effective(f, cat);
            kg += s.weightKg();
            p += s.powerW();
        }
        BoxCalc box = box(layout, cat);
        kg += box.kg();
        p += box.powerW();
        if (box.count() > 0) {
            cases += 1;
        }
        if (p > LINE_16A_W) {
            warns.add(RoomWarning.warning("Нагрузка " + RoomUnits.num(p / 230, 1) + " А — больше одной линии 16 А", null));
        }
        Totals totals = new Totals(towers.size(), unitsTotal, unitsUsed, kg, p, floor, cases, RoomGeometry.deckInfo(b));
        return new Result(towers, warns, bad, air, totals, box, b);
    }

    /** Кейс мелочёвки по ВСЕЙ мелочи раскладки (она едет в одном кейсе независимо от того, где разложена). */
    public static BoxCalc box(ControlRoomLayout layout, RoomCatalog cat) {
        int n = 0;
        double vol = 0;
        double kg = 0;
        double p = 0;
        for (RoomLooseItem l : layout.getLoose()) {
            RoomItemSpec s = cat.resolve(l.getRef());
            n++;
            vol += s.widthCm() * s.heightCm() * s.depthCm() / 1000.0;
            kg += s.weightKg();
            p += s.powerW();
        }
        double need = vol * 1.6;
        double[] pick = null;
        double fill = 0;
        for (double[] box : BOXES) {
            double avail = box[0] * box[1] * box[2] / 1000.0 * 0.8;
            if (avail >= need) {
                pick = box;
                fill = need / avail;
                break;
            }
        }
        return new BoxCalc(n, vol, need, pick, fill, kg, p);
    }

    private static Target target(RoomBounds o) {
        return new Target(o.isTower() ? "tower" : "furn", o.id, null);
    }

    /** Четыре угла опоры с отступом 0,5 см внутрь. */
    private static List<double[]> corners(RoomBounds o) {
        List<double[]> c = new ArrayList<>();
        c.add(new double[] {o.x0 + .5, o.y0 + .5});
        c.add(new double[] {o.x1 - .5, o.y0 + .5});
        c.add(new double[] {o.x0 + .5, o.y1 - .5});
        c.add(new double[] {o.x1 - .5, o.y1 - .5});
        return c;
    }

    /**
     * Пара «башня — мебель»: если пересечение разрешено положением башни, оно не ошибка; здесь же
     * проверки «целиком на столе» и «помещается под столом/станком». Возвращает {@code true},
     * если пара полностью обработана и обычная проверка пересечения не нужна.
     */
    static boolean layerPair(RoomBounds a, RoomBounds b, Set<String> bad, List<RoomWarning> w) {
        if (a.type == b.type || (!a.isTower() && !b.isTower())) {
            return false;
        }
        RoomBounds tw = a.isTower() ? a : b;
        RoomBounds fu = a.isTower() ? b : a;
        if (fu.monitor) {
            return true;
        }
        if (fu.chair) {
            return false;
        }
        if (!RoomGeometry.overlaps(a, b)) {
            return true;
        }
        Target tg = new Target("tower", tw.id, null);
        if (fu.deck) {
            if (tw.lay == RoomTower.Lay.UNDER && tw.height > fu.height - 8) {
                bad.add(tw.id);
                w.add(RoomWarning.error(tw.name + " (высота " + RoomUnits.num(tw.height, 0) + " см) не помещается под «"
                        + fu.name + "» (" + RoomUnits.num(fu.height, 0) + " см)", tg));
            }
            return true;
        }
        boolean inside = fu.containsCenterOf(tw);
        RoomGeometry.EffLay mode;
        if (tw.lay == RoomTower.Lay.ON) {
            mode = RoomGeometry.EffLay.ON;
        } else if (tw.lay == RoomTower.Lay.UNDER) {
            mode = RoomGeometry.EffLay.UNDER;
        } else if (inside) {
            mode = (tw.onlyStack || tw.height > fu.height - 5) ? RoomGeometry.EffLay.ON : RoomGeometry.EffLay.UNDER;
        } else {
            mode = RoomGeometry.EffLay.FLOOR;
        }
        if (mode == RoomGeometry.EffLay.FLOOR) {
            bad.add(tw.id);
            bad.add(fu.id);
            w.add(RoomWarning.error(tw.name + " пересекается с «" + fu.name
                    + "»: поставьте её целиком на стол или выберите положение «под столом»", tg));
        } else if (mode == RoomGeometry.EffLay.ON) {
            boolean full = tw.x0 >= fu.x0 - .5 && tw.x1 <= fu.x1 + .5 && tw.y0 >= fu.y0 - .5 && tw.y1 <= fu.y1 + .5;
            if (!full) {
                bad.add(tw.id);
                bad.add(fu.id);
                w.add(RoomWarning.error(tw.name + " не помещается целиком на «" + fu.name + "»", tg));
            } else if (tw.weightKg > 40) {
                w.add(RoomWarning.warning(tw.name + " весит " + RoomUnits.num(tw.weightKg, 0)
                        + " кг: проверьте, что «" + fu.name + "» выдержит", tg));
            }
        } else if (tw.height > fu.height - 5) {
            bad.add(tw.id);
            bad.add(fu.id);
            w.add(RoomWarning.error(tw.name + " (высота " + RoomUnits.num(tw.height, 0) + " см) не помещается под «"
                    + fu.name + "» (" + RoomUnits.num(fu.height, 0) + " см)", tg));
        }
        return true;
    }
}
