package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.service.StructurePickMath.YawTransform;

/**
 * Ориентация башни в пространстве (запрос пользователя 2026-10-22: «вращать башню по всем осям, чтобы собрать
 * башню, лежащую на боку, — рамы в ряд на высоте»). Чистая математика без Swing/GL.
 *
 * <p>Поза башни {@link TowerPose}: точка привязки {@code (x, y, z)} (середина передней плоскости у основания, y —
 * высота над землёй) и три угла в порядке {@code R = Ry(рыскание)·Rx(наклон)·Rz(крен)} — тот же порядок, что
 * {@code glRotate} в 3D и {@link YawTransform}. Рыскание — вращение вокруг вертикали (прежний поворот в плане),
 * наклон — вокруг горизонтальной оси X башни (падает вперёд/назад), крен — вокруг её оси Z (падает вбок); при
 * нулевых наклоне и крене всё сводится к прежней плоской раскладке. Локальные оси башни: X — поперёк, Y — вверх,
 * Z — вглубь (передняя плоскость на {@code z = 0}, тыл на {@code z = −D}).
 *
 * <p>Вращение мышью (кольца гизмо) — вокруг ОСИ МИРА через опорную точку: {@link TowerPose#rotatedAbout} умножает
 * матрицу на поворот и раскладывает обратно в три угла ({@link TowerPose#euler}); опорная точка (центр основания)
 * при этом остаётся на месте.
 */
public final class StructureOrient {

    private StructureOrient() {
    }

    /** Поза башни в мировой системе (или системе группы). */
    public record TowerPose(double x, double y, double z, double yawDeg, double pitchDeg, double rollDeg) {

        public double[][] matrix() {
            return new YawTransform(0, 0, yawDeg, 0, 0, 0, pitchDeg, rollDeg).rotation();
        }

        /** Точка башни (локальные координаты) в мировой системе. */
        public double[] toWorld(double lx, double ly, double lz) {
            double[][] m = matrix();
            return new double[]{x + m[0][0] * lx + m[0][1] * ly + m[0][2] * lz,
                    y + m[1][0] * lx + m[1][1] * ly + m[1][2] * lz,
                    z + m[2][0] * lx + m[2][1] * ly + m[2][2] * lz};
        }

        /** Башня стоит прямо (наклон и крен нулевые): работает прежняя плоская геометрия. */
        public boolean upright() {
            return Math.abs(pitchDeg) < 1e-6 && Math.abs(rollDeg) < 1e-6;
        }

        /** Те же углы {@code yaw/pitch/roll}, но опорная точка {@code pivotLocal} (локальные координаты — центр
         *  основания) остаётся на месте в мировой системе. */
        public TowerPose withAnglesKeepingPivot(double[] pivotLocal, double yaw, double pitch, double roll) {
            double[] p = toWorld(pivotLocal[0], pivotLocal[1], pivotLocal[2]);
            double[][] m = new YawTransform(0, 0, yaw, 0, 0, 0, pitch, roll).rotation();
            return new TowerPose(p[0] - apply(m, pivotLocal)[0], p[1] - apply(m, pivotLocal)[1],
                    p[2] - apply(m, pivotLocal)[2], normalize(yaw), pitch, roll);
        }

        /** Поворот на {@code deltaDeg} вокруг оси МИРА {@code axis} (правая тройка) через опорную точку
         *  {@code pivotLocal}: {@code R' = Rot(axis, Δ)·R}, углы пересчитываются, опорная точка не сдвигается. */
        public TowerPose rotatedAbout(double[] axis, double deltaDeg, double[] pivotLocal) {
            double[] p = toWorld(pivotLocal[0], pivotLocal[1], pivotLocal[2]);
            double[][] rn = multiply(rotationAbout(axis, deltaDeg), matrix());
            double[] angles = euler(rn);
            double[] off = apply(rn, pivotLocal);
            return new TowerPose(p[0] - off[0], p[1] - off[1], p[2] - off[2], angles[0], angles[1], angles[2]);
        }
    }

    /** Угол в {@code (−180°, 180°]}. */
    public static double normalize(double deg) {
        return StructureLayoutMath.normalizeDeg(deg);
    }

    public static double[] apply(double[][] m, double[] v) {
        return new double[]{m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
                m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
                m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]};
    }

    public static double[][] multiply(double[][] a, double[][] b) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
            }
        }
        return r;
    }

    /** Поворот на {@code deg} вокруг оси {@code axis} (формула Родрига, правая тройка). */
    public static double[][] rotationAbout(double[] axis, double deg) {
        double len = Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
        double x = axis[0] / len;
        double y = axis[1] / len;
        double z = axis[2] / len;
        double c = Math.cos(Math.toRadians(deg));
        double s = Math.sin(Math.toRadians(deg));
        double t = 1 - c;
        return new double[][]{
                {t * x * x + c, t * x * y - s * z, t * x * z + s * y},
                {t * x * y + s * z, t * y * y + c, t * y * z - s * x},
                {t * x * z - s * y, t * y * z + s * x, t * z * z + c}};
    }

    /** Углы {@code {рыскание, наклон, крен}} по матрице {@code Ry·Rx·Rz}. На полюсе (наклон ±90°) рыскание и крен
     *  вырождаются в одно вращение — тогда крен берётся нулевым. */
    public static double[] euler(double[][] m) {
        double sp = Math.max(-1, Math.min(1, -m[1][2]));
        double pitch = Math.asin(sp);
        double yaw;
        double roll;
        if (Math.abs(sp) < 1 - 1e-9) {
            yaw = Math.atan2(m[0][2], m[2][2]);
            roll = Math.atan2(m[1][0], m[1][1]);
        } else {
            roll = 0;
            yaw = Math.atan2(-m[2][0], m[0][0]);
        }
        return new double[]{normalize(Math.toDegrees(yaw)), Math.toDegrees(pitch), normalize(Math.toDegrees(roll))};
    }

    // ---- 3D-коробки: пересечения и стыки ----

    /** Ориентированная коробка: центр, три оси (строки — единичные векторы) и полуразмеры. */
    public record OrientedBox(double[] c, double[][] ax, double[] h) {

        /** Коробка башни: локально {@code x ∈ [−W/2, W/2]}, {@code y ∈ [0, H]}, {@code z ∈ [−D, 0]}. */
        public static OrientedBox ofTower(TowerPose pose, double widthMm, double heightMm, double depthMm) {
            double[][] m = pose.matrix();
            double[] c = pose.toWorld(0, heightMm / 2.0, -depthMm / 2.0);
            double[][] ax = {{m[0][0], m[1][0], m[2][0]}, {m[0][1], m[1][1], m[2][1]}, {m[0][2], m[1][2], m[2][2]}};
            return new OrientedBox(c, ax, new double[]{widthMm / 2.0, heightMm / 2.0, depthMm / 2.0});
        }

        /** Разделены ли коробки с запасом {@code margin}, мм: {@code margin < 0} — «пересекаются глубже, чем на
         *  |margin|» не считается разделением (касание — не пересечение); {@code margin > 0} — расстояние между
         *  коробками не меньше {@code margin} (стык с допуском). Теорема о разделяющей оси, 15 осей. */
        public boolean separated(OrientedBox o, double margin) {
            double[] d = {o.c[0] - c[0], o.c[1] - c[1], o.c[2] - c[2]};
            java.util.List<double[]> axes = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) {
                axes.add(ax[i]);
                axes.add(o.ax[i]);
            }
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    double[] cr = cross(ax[i], o.ax[j]);
                    double len = Math.sqrt(cr[0] * cr[0] + cr[1] * cr[1] + cr[2] * cr[2]);
                    if (len > 1e-9) {
                        axes.add(new double[]{cr[0] / len, cr[1] / len, cr[2] / len});
                    }
                }
            }
            for (double[] l : axes) {
                double ra = 0;
                double rb = 0;
                for (int i = 0; i < 3; i++) {
                    ra += h[i] * Math.abs(dot(ax[i], l));
                    rb += o.h[i] * Math.abs(dot(o.ax[i], l));
                }
                if (Math.abs(dot(d, l)) >= ra + rb + margin) {
                    return true;
                }
            }
            return false;
        }

        /** Пересекаются (проникновение больше 0,5 мм): касание гранью пересечением не считается. */
        public boolean overlaps(OrientedBox o) {
            return !separated(o, -0.5);
        }

        /** Стык: коробки пересекаются или расстояние между ними не больше {@code tolMm}. */
        public boolean touches(OrientedBox o, double tolMm) {
            return !separated(o, tolMm);
        }
    }

    public static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    public static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
}
