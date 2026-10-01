package com.vjstb.ledscheme.service;

/**
 * Чистая математика picking'а для орбитальной камеры {@code ui.Structure3DPanel} (Phase 2 —
 * интерактивный 3D-редактор конструктива, см. STRUCTURE_CALC_NOTES.md) — сознательно БЕЗ
 * JOGL/GL-типов, в отличие от остальной части той панели: ручной CPU-side raycast вместо
 * {@code GL_SELECT}/{@code gluUnProject}, которым нужен живой GL-контекст на потоке рендера
 * (их нельзя вызвать из обычного обработчика клика мышью и уж тем более из теста) — а этот
 * класс благодаря этому реально покрыт unit-тестами, единственный кусок 3D-панели, для
 * которого это возможно.
 */
public final class StructurePickMath {

    private StructurePickMath() {
    }

    public record Vec3(double x, double y, double z) {
        public Vec3 minus(Vec3 o) {
            return new Vec3(x - o.x, y - o.y, z - o.z);
        }

        public Vec3 plus(Vec3 o) {
            return new Vec3(x + o.x, y + o.y, z + o.z);
        }

        public Vec3 scale(double s) {
            return new Vec3(x * s, y * s, z * s);
        }

        public Vec3 cross(Vec3 o) {
            return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
        }

        public double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }

        /** Нулевой вектор (вырожденный базис — камера смотрит точно вдоль up) возвращается как
         *  есть, а не делится на 0 -- вызывающая сторона (реальная камера с ненулевым eye/center)
         *  на практике этого не достигает, это просто защита от NaN. */
        public Vec3 normalized() {
            double len = length();
            return len < 1e-9 ? this : new Vec3(x / len, y / len, z / len);
        }
    }

    public record Ray(Vec3 origin, Vec3 direction) {
    }

    /** Восстанавливает мировой луч через пиксель экрана для той же перспективной камеры, что
     *  задают {@code gluLookAt(eye, center, up)} и {@code gluPerspective(fovDeg, aspect, ...)}
     *  в {@code Structure3DPanel} -- fovDeg/aspect единственное, что влияет на НАПРАВЛЕНИЕ луча
     *  (near/far влияют только на глубину в проекционной матрице, не на направление), поэтому
     *  near/far здесь не нужны вовсе. {@code pixelY} растёт вниз (координаты AWT-события мыши),
     *  переворачивается внутри в NDC, растущий вверх. */
    public static Ray cameraRay(Vec3 eye, Vec3 center, Vec3 up, double fovDeg, double aspect,
            double pixelX, double pixelY, double viewportW, double viewportH) {
        Vec3 forward = center.minus(eye).normalized();
        Vec3 right = forward.cross(up).normalized();
        Vec3 camUp = right.cross(forward);
        double ndcX = viewportW <= 0 ? 0 : (2.0 * pixelX / viewportW - 1.0);
        double ndcY = viewportH <= 0 ? 0 : (1.0 - 2.0 * pixelY / viewportH);
        double halfHeight = Math.tan(Math.toRadians(fovDeg) / 2.0);
        double halfWidth = halfHeight * aspect;
        Vec3 dir = forward.plus(right.scale(ndcX * halfWidth)).plus(camUp.scale(ndcY * halfHeight)).normalized();
        return new Ray(eye, dir);
    }

    /** Стандартный slab-тест луч/AABB -- расстояние {@code t >= 0} до точки входа вдоль луча,
     *  или {@code null}, если луч не задевает коробку (либо коробка целиком позади начала луча). */
    public static Double intersectAabb(Ray ray, Vec3 boxMin, Vec3 boxMax) {
        double tMin = Double.NEGATIVE_INFINITY;
        double tMax = Double.POSITIVE_INFINITY;
        double[] origin = {ray.origin().x(), ray.origin().y(), ray.origin().z()};
        double[] dir = {ray.direction().x(), ray.direction().y(), ray.direction().z()};
        double[] min = {boxMin.x(), boxMin.y(), boxMin.z()};
        double[] max = {boxMax.x(), boxMax.y(), boxMax.z()};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dir[axis]) < 1e-12) {
                if (origin[axis] < min[axis] || origin[axis] > max[axis]) {
                    return null;
                }
                continue;
            }
            double t1 = (min[axis] - origin[axis]) / dir[axis];
            double t2 = (max[axis] - origin[axis]) / dir[axis];
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return null;
            }
        }
        if (tMax < 0) {
            return null;
        }
        return tMin >= 0 ? tMin : tMax;
    }

    /** Локальная система раздельной башни изогнутого экрана (запрос 2026-10-01, см. {@link
     *  StructureCurveMath}): {@code world = anchor + Rot_y(yawDeg)·(local − localOffset)}, где
     *  {@code Rot_y} — тот же поворот, что {@code glRotate(yawDeg, 0, 1, 0)} ({@code x' = x·cos
     *  + z·sin}, {@code z' = −x·sin + z·cos}). {@code localOffset} — точка «плоской» сетки
     *  3D-панели, которая попадает в {@code anchor} (середина передней плоскости башни): так
     *  код кандидатов панели строит рамы в прежних координатах стены, а этот слой переносит и
     *  поворачивает башню целиком. {@link #IDENTITY} — прямой экран с зазором 0, ничего не
     *  меняется. */
    public record YawTransform(double anchorX, double anchorZ, double yawDeg, double localOffsetX,
            double localOffsetZ) {

        public static final YawTransform IDENTITY = new YawTransform(0, 0, 0, 0, 0);

        public boolean isIdentity() {
            return anchorX == 0 && anchorZ == 0 && yawDeg == 0 && localOffsetX == 0 && localOffsetZ == 0;
        }

        public Vec3 toWorld(Vec3 local) {
            double a = Math.toRadians(yawDeg);
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            double x = local.x() - localOffsetX;
            double z = local.z() - localOffsetZ;
            return new Vec3(anchorX + x * cos + z * sin, local.y(), anchorZ - x * sin + z * cos);
        }

        public Vec3 toLocal(Vec3 world) {
            Vec3 d = rotateInverse(new Vec3(world.x() - anchorX, world.y(), world.z() - anchorZ));
            return new Vec3(d.x() + localOffsetX, d.y(), d.z() + localOffsetZ);
        }

        /** Направление (без переноса) в локальную систему. */
        public Vec3 rotateInverse(Vec3 v) {
            double a = Math.toRadians(yawDeg);
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            return new Vec3(v.x() * cos - v.z() * sin, v.y(), v.x() * sin + v.z() * cos);
        }

        public Ray toLocal(Ray ray) {
            return new Ray(toLocal(ray.origin()), rotateInverse(ray.direction()));
        }
    }

    /** Луч против ПОВЁРНУТОЙ коробки (OBB): луч переводится в локальную систему башни, там —
     *  обычный {@link #intersectAabb}. Поворот ортонормированный, поэтому {@code t} (расстояние
     *  вдоль луча) совпадает с мировым и сравним с попаданиями в другие башни.
     *  {@code transform == null} или единичный — ровно {@link #intersectAabb}. */
    public static Double intersectObb(Ray ray, Vec3 boxMin, Vec3 boxMax, YawTransform transform) {
        if (transform == null || transform.isIdentity()) {
            return intersectAabb(ray, boxMin, boxMax);
        }
        return intersectAabb(transform.toLocal(ray), boxMin, boxMax);
    }
}
