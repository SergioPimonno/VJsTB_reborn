package com.vjstb.ledscheme.service.room;

import com.vjstb.ledscheme.model.RoomLooseItem;
import com.vjstb.ledscheme.model.RoomPoint;
import com.vjstb.ledscheme.model.RoomRun;
import java.util.Collections;
import java.util.List;

/**
 * Геометрия трасс кабеля на плане: длина, точка на расстоянии от начала, ближайшая точка
 * (привязка мелочи к трассе), привязка вершин к стенам и положение подписи названия.
 * Вынесено из прототипа ({@code planner.html}: {@code runLen}, {@code pointAt},
 * {@code nearestOnRun}, {@code snapRunPt}, {@code runLabel}); чистая логика без Swing.
 */
public final class RoomRunMath {

    /** Ближайшая точка трассы: расстояние до неё, расстояние вдоль трассы и координаты. */
    public record Nearest(double distance, double s, double x, double y, String runId) {
    }

    /** Положение подписи трассы: центр отрезка и угол поворота в градусах, всегда в диапазоне
     *  [−90°, 90°), чтобы текст не оказывался вверх ногами. */
    public record LabelPos(double x, double y, double angleDeg) {
    }

    private RoomRunMath() {
    }

    public static double length(RoomRun run) {
        List<RoomPoint> p = run.getPoints();
        double s = 0;
        for (int i = 1; i < p.size(); i++) {
            s += Math.hypot(p.get(i).getX() - p.get(i - 1).getX(), p.get(i).getY() - p.get(i - 1).getY());
        }
        return s;
    }

    /** Точка на расстоянии {@code s} от первой вершины (ограничено длиной трассы). */
    public static RoomPoint pointAt(RoomRun run, double s) {
        List<RoomPoint> p = run.getPoints();
        if (p.isEmpty()) {
            return new RoomPoint(0, 0);
        }
        double rest = Math.max(0, Math.min(length(run), s));
        for (int i = 1; i < p.size(); i++) {
            RoomPoint a = p.get(i - 1);
            RoomPoint b = p.get(i);
            double l = Math.hypot(b.getX() - a.getX(), b.getY() - a.getY());
            if (rest <= l || i == p.size() - 1) {
                double t = l > 0 ? rest / l : 0;
                return new RoomPoint(a.getX() + (b.getX() - a.getX()) * t, a.getY() + (b.getY() - a.getY()) * t);
            }
            rest -= l;
        }
        return new RoomPoint(p.get(0).getX(), p.get(0).getY());
    }

    /** Ближайшая точка трассы к (x, y); {@code null} для трассы короче двух точек. */
    public static Nearest nearest(RoomRun run, double x, double y) {
        List<RoomPoint> p = run.getPoints();
        Nearest best = null;
        double acc = 0;
        for (int i = 1; i < p.size(); i++) {
            RoomPoint a = p.get(i - 1);
            RoomPoint b = p.get(i);
            double dx = b.getX() - a.getX();
            double dy = b.getY() - a.getY();
            double l2 = dx * dx + dy * dy;
            double l = Math.sqrt(l2);
            double t = l2 > 0 ? ((x - a.getX()) * dx + (y - a.getY()) * dy) / l2 : 0;
            t = Math.max(0, Math.min(1, t));
            double px = a.getX() + dx * t;
            double py = a.getY() + dy * t;
            double d = Math.hypot(x - px, y - py);
            if (best == null || d < best.distance()) {
                best = new Nearest(d, acc + l * t, px, py, run.getId());
            }
            acc += l;
        }
        return best;
    }

    /** Ближайшая из всех трасс в пределах {@link RoomUnits#SNAP_RUN_CM}; {@code null}, если рядом нет трассы. */
    public static Nearest snapToRuns(List<RoomRun> runs, double x, double y) {
        Nearest best = null;
        for (RoomRun r : runs) {
            Nearest n = nearest(r, x, y);
            if (n != null && (best == null || n.distance() < best.distance())) {
                best = n;
            }
        }
        return best != null && best.distance() < RoomUnits.SNAP_RUN_CM ? best : null;
    }

    /** Вершина трассы при рисовании/перетаскивании: шаг 5 см, у стены (≤ 12 см) — на стену,
     *  внутри площадки. */
    public static RoomPoint snapPoint(double roomW, double roomD, double x, double y) {
        double sx = Math.round(x / RoomUnits.MOVE_STEP_CM) * RoomUnits.MOVE_STEP_CM;
        double sy = Math.round(y / RoomUnits.MOVE_STEP_CM) * RoomUnits.MOVE_STEP_CM;
        if (Math.abs(sy) <= RoomUnits.WALL_SNAP_CM) {
            sy = 0;
        } else if (Math.abs(sy - roomD) <= RoomUnits.WALL_SNAP_CM) {
            sy = roomD;
        }
        if (Math.abs(sx) <= RoomUnits.WALL_SNAP_CM) {
            sx = 0;
        } else if (Math.abs(sx - roomW) <= RoomUnits.WALL_SNAP_CM) {
            sx = roomW;
        }
        return new RoomPoint(Math.max(0, Math.min(roomW, sx)), Math.max(0, Math.min(roomD, sy)));
    }

    /** Где писать название трассы: на первом отрезке от ввода, если он не короче 60 см, иначе на
     *  самом длинном (там обычно нет мелочи). Текст идёт вдоль отрезка. */
    public static LabelPos labelPos(RoomRun run) {
        List<RoomPoint> p = run.getPoints();
        if (p.size() < 2) {
            return p.isEmpty() ? new LabelPos(0, 0, 0) : new LabelPos(p.get(0).getX(), p.get(0).getY(), 0);
        }
        int best = 0;
        double first = Math.hypot(p.get(1).getX() - p.get(0).getX(), p.get(1).getY() - p.get(0).getY());
        if (first < 60) {
            double bl = 0;
            for (int i = 0; i < p.size() - 1; i++) {
                double l = Math.hypot(p.get(i + 1).getX() - p.get(i).getX(), p.get(i + 1).getY() - p.get(i).getY());
                if (l > bl) {
                    bl = l;
                    best = i;
                }
            }
        }
        RoomPoint a = p.get(best);
        RoomPoint b = p.get(best + 1);
        double ang = Math.toDegrees(Math.atan2(b.getY() - a.getY(), b.getX() - a.getX()));
        while (ang >= 90) {
            ang -= 180;
        }
        while (ang < -90) {
            ang += 180;
        }
        return new LabelPos((a.getX() + b.getX()) / 2, (a.getY() + b.getY()) / 2, ang);
    }

    /** Развернуть направление трассы: вершины в обратном порядке, расстояния мелочи на ней
     *  становятся {@code длина − s}, чтобы предметы остались на тех же местах. */
    public static void reverse(RoomRun run, List<RoomLooseItem> loose) {
        double len = length(run);
        for (RoomLooseItem l : loose) {
            if (l.getMode() == RoomLooseItem.Mode.RUN && run.getId().equals(l.getRunId()) && l.getSCm() != null) {
                l.setSCm(len - l.getSCm());
            }
        }
        Collections.reverse(run.getPoints());
    }
}
