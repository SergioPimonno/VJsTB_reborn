package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.service.StructureCurveMath.Placement;
import com.vjstb.ledscheme.service.StructureCurveMath.TowerSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Гизмо башни и линии привязки 3D-редактора раскладки (запрос пользователя 2026-10-22: «непонятно, за что
 * тащить; Shift вращает и ни к чему не привязывает — сделай гизмо; зажатый Shift включает линии привязки,
 * чтобы выравнивать башни по другим башням, экрану и т.д.»). Чистая геометрия плана, без Swing/GL.
 *
 * <p><b>Гизмо</b> лежит на земле вокруг центра контура башни: красная ось X и синяя ось Z башни (тащить —
 * двигать строго вдоль оси), жёлтый квадрат в центре (двигать свободно), оранжевое кольцо с ручкой на
 * переднем крае (тащить — вращать вокруг центра). {@link #hit} определяет ручку под точкой земли.
 *
 * <p><b>Линии привязки</b> ({@link RefLine}): рёбра и осевые линии других башен, плоскости лица/тыла и торцы
 * плоских экранов. С Shift перетаскиваемая башня прилипает: позицией — когда её собственная кромка или
 * осевая линия, параллельная опорной, оказывается ближе допуска ({@link #snapMove}); углом — когда её ось
 * становится параллельной или перпендикулярной опорной линии либо кратной шагу ({@link #snapAngle}).
 * Сработавшие линии возвращаются в результате — 3D рисует их яркими.
 */
public final class StructureGizmoMath {

    private StructureGizmoMath() {
    }

    /** Ручка гизмо. */
    public enum Handle { NONE, CENTER, AXIS_X, AXIS_Z, RING, AXIS_Y, RING_PITCH, RING_ROLL }

    /** Радиус центрального квадрата (половина стороны ≈), мм. */
    public static final double CENTER_RADIUS_MM = 220;
    /** Полуширина зоны захвата оси и допуск по радиусу кольца, мм. */
    public static final double GRAB_TOLERANCE_MM = 140;
    /** Вынос оси за контур башни, мм. */
    public static final double AXIS_OVERSHOOT_MM = 700;

    /** Радиус кольца вращения: чуть больше описанной окружности контура. */
    public static double ringRadius(TowerSpec spec) {
        return Math.hypot(spec.outerWidthMm() / 2.0, spec.depthMm() / 2.0) + 250;
    }

    /** Длина оси от центра в каждую сторону: до края контура + вынос. {@code xAxis} — ось X башни. */
    public static double axisReach(TowerSpec spec, boolean xAxis) {
        return (xAxis ? spec.outerWidthMm() : spec.depthMm()) / 2.0 + AXIS_OVERSHOOT_MM;
    }

    /** Центр контура башни в её локальной системе. */
    public static double[] centerLocal(TowerSpec spec) {
        return new double[]{0, -spec.depthMm() / 2.0};
    }

    /** Центр контура в мировой системе. */
    public static double[] centerWorld(Placement world, TowerSpec spec) {
        double[] c = centerLocal(spec);
        return world.toWorld(c[0], c[1]);
    }

    /** Мировое направление (единичный вектор) оси X ({@code xAxis}) или Z башни. */
    public static double[] axisDir(Placement world, boolean xAxis) {
        double a = Math.toRadians(world.yawDeg());
        return xAxis ? new double[]{Math.cos(a), -Math.sin(a)} : new double[]{Math.sin(a), Math.cos(a)};
    }

    /** Ручка гизмо под точкой земли {@code (gx, gz)}: центр, ось X/Z, кольцо или {@link Handle#NONE}.
     *  Приоритет: центр, оси, кольцо (оси пересекают кольцо — на пересечении тащится ось). */
    public static Handle hit(Placement world, TowerSpec spec, double gx, double gz) {
        double[] l = world.toLocal(gx, gz);
        double lx = l[0];
        double lz = l[1] + spec.depthMm() / 2.0; // относительно центра контура
        double r = Math.hypot(lx, lz);
        if (r <= CENTER_RADIUS_MM) {
            return Handle.CENTER;
        }
        if (Math.abs(lz) <= GRAB_TOLERANCE_MM && Math.abs(lx) <= axisReach(spec, true)) {
            return Handle.AXIS_X;
        }
        if (Math.abs(lx) <= GRAB_TOLERANCE_MM && Math.abs(lz) <= axisReach(spec, false)) {
            return Handle.AXIS_Z;
        }
        if (Math.abs(r - ringRadius(spec)) <= GRAB_TOLERANCE_MM) {
            return Handle.RING;
        }
        return Handle.NONE;
    }

    /** Смещение {@code (dx, dz)} (мировое), спроецированное на ось ручки: для {@code AXIS_X}/{@code AXIS_Z}
     *  — только вдоль оси, для {@code CENTER} — как есть. */
    public static double[] constrain(Placement world, Handle handle, double dx, double dz) {
        if (handle != Handle.AXIS_X && handle != Handle.AXIS_Z) {
            return new double[]{dx, dz};
        }
        double[] u = axisDir(world, handle == Handle.AXIS_X);
        double t = dx * u[0] + dz * u[1];
        return new double[]{u[0] * t, u[1] * t};
    }

    // ---- 3D: наклон, крен, высота (запрос 2026-10-22: вращать башню по всем осям) ----

    /** Высота оси «вверх», мм (вниз — {@link #AXIS_DOWN_MM}). */
    public static final double AXIS_UP_MM = 2200;
    public static final double AXIS_DOWN_MM = 500;

    /** Опорная точка башни в мировой системе — центр её основания: через неё проходят все оси и кольца гизмо и
     *  вокруг неё башня вращается (центр основания остаётся на месте). */
    public static double[] pivotWorld(StructureOrient.TowerPose pose, TowerSpec spec) {
        return pose.toWorld(0, 0, -spec.depthMm() / 2.0);
    }

    /** Центр основания в локальной системе башни. */
    public static double[] pivotLocal(TowerSpec spec) {
        return new double[]{0, 0, -spec.depthMm() / 2.0};
    }

    /** Горизонтальное направление оси X ({@code xAxis}) или Z башни — только по рысканию (как оси гизмо на земле). */
    public static double[] horizontalAxis(StructureOrient.TowerPose pose, boolean xAxis) {
        double a = Math.toRadians(pose.yawDeg());
        return xAxis ? new double[]{Math.cos(a), 0, -Math.sin(a)} : new double[]{Math.sin(a), 0, Math.cos(a)};
    }

    /** Ось МИРА, вокруг которой вращает кольцо {@code handle}: рыскание — вертикаль, наклон — горизонтальная ось X
     *  башни, крен — её ось Z (уже наклонённая). */
    public static double[] ringAxis(StructureOrient.TowerPose pose, Handle handle) {
        return switch (handle) {
            case RING_PITCH -> horizontalAxis(pose, true);
            case RING_ROLL -> {
                double[][] m = pose.matrix();
                yield new double[]{m[0][2], m[1][2], m[2][2]};
            }
            default -> new double[]{0, 1, 0};
        };
    }

    private static double[] unit(StructurePickMath.Vec3 v) {
        double len = v.length();
        return new double[]{v.x() / len, v.y() / len, v.z() / len};
    }

    /** Расстояние от точки до луча (луч — {@code o + t·d}, {@code t ≥ 0}, {@code d} единичный). */
    private static double pointRay(double[] p, double[] o, double[] d) {
        double wx = p[0] - o[0];
        double wy = p[1] - o[1];
        double wz = p[2] - o[2];
        double t = Math.max(0, wx * d[0] + wy * d[1] + wz * d[2]);
        return Math.sqrt(Math.pow(wx - t * d[0], 2) + Math.pow(wy - t * d[1], 2) + Math.pow(wz - t * d[2], 2));
    }

    private static double segmentRay(double[] a, double[] b, double[] o, double[] d) {
        double best = Double.POSITIVE_INFINITY;
        int n = 64;
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            best = Math.min(best, pointRay(new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t,
                    a[2] + (b[2] - a[2]) * t}, o, d));
        }
        return best;
    }

    /** Точка кольца радиуса {@code r} в плоскости с нормалью {@code n} через центр {@code c}, угол {@code ang}. */
    public static double[] ringPoint(double[] c, double[] n, double r, double ang) {
        double[] u = Math.abs(n[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double[] v1 = StructureOrient.cross(n, u);
        double l1 = Math.sqrt(StructureOrient.dot(v1, v1));
        v1 = new double[]{v1[0] / l1, v1[1] / l1, v1[2] / l1};
        double[] v2 = StructureOrient.cross(n, v1);
        double l2 = Math.sqrt(StructureOrient.dot(v2, v2));
        v2 = new double[]{v2[0] / l2, v2[1] / l2, v2[2] / l2};
        double cs = Math.cos(ang);
        double sn = Math.sin(ang);
        return new double[]{c[0] + r * (cs * v1[0] + sn * v2[0]), c[1] + r * (cs * v1[1] + sn * v2[1]),
                c[2] + r * (cs * v1[2] + sn * v2[2])};
    }

    private static double ringRay(double[] c, double[] n, double r, double[] o, double[] d) {
        double best = Double.POSITIVE_INFINITY;
        int samples = 144;
        for (int i = 0; i < samples; i++) {
            best = Math.min(best, pointRay(ringPoint(c, n, r, 2 * Math.PI * i / samples), o, d));
        }
        return best;
    }

    /** Ручка гизмо башни под ЛУЧОМ в 3D: центр основания, оси X/Z (горизонтальные) и Y (вертикаль), кольца
     *  рыскания (горизонтальное), наклона и крена (вертикальные плоскости). Приоритет: центр, оси, кольца;
     *  внутри группы — ближайшая к лучу. Допуск — {@link #GRAB_TOLERANCE_MM} (центр — {@link #CENTER_RADIUS_MM}). */
    public static Handle hit3D(StructureOrient.TowerPose pose, TowerSpec spec, StructurePickMath.Ray ray) {
        double[] o = {ray.origin().x(), ray.origin().y(), ray.origin().z()};
        double[] d = unit(ray.direction());
        double[] p = pivotWorld(pose, spec);
        if (pointRay(p, o, d) <= CENTER_RADIUS_MM) {
            return Handle.CENTER;
        }
        double[] ux = horizontalAxis(pose, true);
        double[] uz = horizontalAxis(pose, false);
        double rx = axisReach(spec, true);
        double rz = axisReach(spec, false);
        Handle best = Handle.NONE;
        double bestD = GRAB_TOLERANCE_MM;
        double dx = segmentRay(new double[]{p[0] - ux[0] * rx, p[1] - ux[1] * rx, p[2] - ux[2] * rx},
                new double[]{p[0] + ux[0] * rx, p[1] + ux[1] * rx, p[2] + ux[2] * rx}, o, d);
        double dz = segmentRay(new double[]{p[0] - uz[0] * rz, p[1] - uz[1] * rz, p[2] - uz[2] * rz},
                new double[]{p[0] + uz[0] * rz, p[1] + uz[1] * rz, p[2] + uz[2] * rz}, o, d);
        double dy = segmentRay(new double[]{p[0], p[1] - AXIS_DOWN_MM, p[2]}, new double[]{p[0], p[1] + AXIS_UP_MM,
                p[2]}, o, d);
        if (dx <= bestD) {
            best = Handle.AXIS_X;
            bestD = dx;
        }
        if (dz <= bestD) {
            best = Handle.AXIS_Z;
            bestD = dz;
        }
        if (dy <= bestD) {
            best = Handle.AXIS_Y;
            bestD = dy;
        }
        if (best != Handle.NONE) {
            return best;
        }
        double r = ringRadius(spec);
        bestD = GRAB_TOLERANCE_MM;
        for (Handle h : new Handle[]{Handle.RING, Handle.RING_PITCH, Handle.RING_ROLL}) {
            double dist = ringRay(p, ringAxis(pose, h), r, o, d);
            if (dist <= bestD) {
                bestD = dist;
                best = h;
            }
        }
        return best;
    }

    /** Точка пересечения луча с плоскостью через {@code c} с нормалью {@code n} или {@code null}. */
    public static double[] rayPlane(StructurePickMath.Ray ray, double[] c, double[] n) {
        double[] d = unit(ray.direction());
        double denom = StructureOrient.dot(n, d);
        if (Math.abs(denom) < 1e-9) {
            return null;
        }
        double[] o = {ray.origin().x(), ray.origin().y(), ray.origin().z()};
        double t = (StructureOrient.dot(n, new double[]{c[0] - o[0], c[1] - o[1], c[2] - o[2]})) / denom;
        if (t <= 0) {
            return null;
        }
        return new double[]{o[0] + d[0] * t, o[1] + d[1] * t, o[2] + d[2] * t};
    }

    /** Угол (градусы, правая тройка вокруг {@code axis}) поворота вектора {@code v0} в {@code v1}. */
    public static double angleAbout(double[] axis, double[] v0, double[] v1) {
        double[] cr = StructureOrient.cross(v0, v1);
        double s = StructureOrient.dot(cr, axis);
        double c = StructureOrient.dot(v0, v1);
        return Math.toDegrees(Math.atan2(s, c));
    }

    /** Высота вертикальной прямой через {@code (px, pz)} в точке её наибольшего сближения с лучом (перетаскивание по
     *  оси Y): {@code null}, если луч почти вертикален. */
    public static Double heightOnVerticalLine(StructurePickMath.Ray ray, double px, double pz) {
        double[] d = unit(ray.direction());
        double h2 = d[0] * d[0] + d[2] * d[2];
        if (h2 < 1e-9) {
            return null;
        }
        double t = ((px - ray.origin().x()) * d[0] + (pz - ray.origin().z()) * d[2]) / h2;
        return ray.origin().y() + t * d[1];
    }

    // ---- линии привязки ----

    /** Бесконечная прямая плана: точка и единичное направление. */
    public record RefLine(double px, double pz, double dx, double dz) {

        /** Расстояние со знаком от точки до прямой (по левой нормали). */
        public double signedDistance(double x, double z) {
            return (x - px) * (-dz) + (z - pz) * dx;
        }
    }

    /** Результат привязки: итоговое положение и сработавшие опорные линии (пусто — привязки нет). */
    public record Snap(Placement placement, List<RefLine> active) {

        public boolean snapped() {
            return !active.isEmpty();
        }
    }

    /** Шесть линий башни: три вдоль оси X (z = 0, −D/2, −D — передняя кромка, осевая, задняя) и три вдоль
     *  оси Z (x = −W/2, 0, +W/2). */
    public static List<RefLine> towerLines(Placement world, TowerSpec spec) {
        List<RefLine> lines = new ArrayList<>();
        double[] ux = axisDir(world, true);
        double[] uz = axisDir(world, false);
        double w = spec.outerWidthMm() / 2.0;
        double d = spec.depthMm();
        for (double z : new double[]{0, -d / 2.0, -d}) {
            double[] p = world.toWorld(0, z);
            lines.add(new RefLine(p[0], p[1], ux[0], ux[1]));
        }
        for (double x : new double[]{-w, 0, w}) {
            double[] p = world.toWorld(x, -d / 2.0);
            lines.add(new RefLine(p[0], p[1], uz[0], uz[1]));
        }
        return lines;
    }

    /** Линии плоского экрана с позой {@code pose}: плоскость лица ({@code z = 0}), тыла ({@code z = −depth})
     *  вдоль экрана и торцы ({@code x = 0}, {@code x = W}) поперёк. */
    public static List<RefLine> flatScreenLines(Placement pose, double widthMm, double depthMm) {
        List<RefLine> lines = new ArrayList<>();
        double[] ux = axisDir(pose, true);
        double[] uz = axisDir(pose, false);
        for (double z : new double[]{0, -depthMm}) {
            double[] p = pose.toWorld(0, z);
            lines.add(new RefLine(p[0], p[1], ux[0], ux[1]));
        }
        for (double x : new double[]{0, widthMm}) {
            double[] p = pose.toWorld(x, 0);
            lines.add(new RefLine(p[0], p[1], uz[0], uz[1]));
        }
        return lines;
    }

    private static boolean parallel(double[] a, RefLine r, double tolDeg) {
        double cross = a[0] * r.dz() - a[1] * r.dx();
        return Math.abs(cross) <= Math.sin(Math.toRadians(tolDeg));
    }

    /** Привязка положения (Shift при переносе): кромки и осевые линии башни, параллельные опорным линиям,
     *  притягиваются к ним, если ближе {@code tolMm}. Оси X и Z башни привязываются независимо (угол башни
     *  не меняется). Возвращает сдвинутое положение и сработавшие линии. */
    public static Snap snapMove(Placement cand, TowerSpec spec, List<RefLine> refs, double tolMm) {
        double[] ux = axisDir(cand, true);
        double[] uz = axisDir(cand, false);
        double w = spec.outerWidthMm() / 2.0;
        double d = spec.depthMm();
        List<RefLine> active = new ArrayList<>();
        double shiftX = 0; // вдоль ux
        double shiftZ = 0; // вдоль uz
        double bestX = Double.POSITIVE_INFINITY;
        double bestZ = Double.POSITIVE_INFINITY;
        RefLine refX = null;
        RefLine refZ = null;
        // кромки вдоль ux (z = 0, −D/2, −D) двигаются вдоль uz; кромки вдоль uz (x = −W/2, 0, W/2) — вдоль ux
        for (RefLine r : refs) {
            if (parallel(ux, r, 1.0)) {
                // линия вдоль оси X башни: кромки z = 0, −D/2, −D сдвигаются вдоль оси Z башни
                double alongNormal = uz[0] * (-r.dz()) + uz[1] * r.dx(); // ±1: uz параллельна нормали линии
                for (double z : new double[]{0, -d / 2.0, -d}) {
                    double[] p = cand.toWorld(0, z);
                    double off = r.signedDistance(p[0], p[1]);
                    if (Math.abs(off) <= tolMm && Math.abs(off) < bestZ) {
                        bestZ = Math.abs(off);
                        shiftZ = -off * alongNormal;
                        refZ = r;
                    }
                }
            } else if (parallel(uz, r, 1.0)) {
                double alongNormal = ux[0] * (-r.dz()) + ux[1] * r.dx();
                for (double x : new double[]{-w, 0, w}) {
                    double[] p = cand.toWorld(x, -d / 2.0);
                    double off = r.signedDistance(p[0], p[1]);
                    if (Math.abs(off) <= tolMm && Math.abs(off) < bestX) {
                        bestX = Math.abs(off);
                        shiftX = -off * alongNormal;
                        refX = r;
                    }
                }
            }
        }
        if (refX != null) {
            active.add(refX);
        }
        if (refZ != null) {
            active.add(refZ);
        }
        if (active.isEmpty()) {
            return new Snap(cand, active);
        }
        Placement snapped = new Placement(cand.index(),
                cand.anchorX() + ux[0] * shiftX + uz[0] * shiftZ,
                cand.anchorZ() + ux[1] * shiftX + uz[1] * shiftZ, cand.yawDeg());
        return new Snap(snapped, active);
    }

    /** Привязка угла (Shift при вращении): ось башни становится параллельной или перпендикулярной ближайшей
     *  опорной линии (допуск {@code tolDeg}); если таких нет — угол округляется до {@code stepDeg}. Вращение —
     *  вокруг точки {@code (cx, cz)} локальной системы (центр контура), она остаётся на месте. */
    public static Snap snapAngle(Placement cand, double cx, double cz, List<RefLine> refs, double tolDeg,
            double stepDeg) {
        double best = Double.POSITIVE_INFINITY;
        double bestYaw = cand.yawDeg();
        RefLine bestRef = null;
        for (RefLine r : refs) {
            double phi = Math.toDegrees(Math.atan2(r.dz(), r.dx()));
            for (int k = 0; k < 4; k++) {
                double yaw = StructureLayoutMath.normalizeDeg(-phi + 90.0 * k);
                double diff = Math.abs(StructureLayoutMath.normalizeDeg(yaw - cand.yawDeg()));
                if (diff <= tolDeg && diff < best) {
                    best = diff;
                    bestYaw = yaw;
                    bestRef = r;
                }
            }
        }
        List<RefLine> active = new ArrayList<>();
        double yaw;
        if (bestRef != null) {
            yaw = bestYaw;
            active.add(bestRef);
        } else {
            yaw = StructureLayoutMath.snapAngle(cand.yawDeg(), stepDeg);
        }
        return new Snap(StructureLayoutMath.rotateAbout(cand, cx, cz, yaw), active);
    }
}
