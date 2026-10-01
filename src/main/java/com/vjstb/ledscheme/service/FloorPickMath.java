package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.FloorFrameCell;
import java.util.ArrayList;
import java.util.List;

/**
 * Чистая геометрия и picking 3D-редактора пола ({@code ui.FloorPlan3DPanel}, запрос
 * пользователя 2026-10-01 «либо 2D схема как сейчас, либо 3D редактор как для конструктива»)
 * — без JOGL/GL-типов, по образцу {@link StructurePickMath}: ручной CPU-raycast вместо {@code
 * GL_SELECT}/{@code gluUnProject} (им нужен живой GL-контекст), поэтому всё, что решает «по
 * какой раме кликнули», покрыто unit-тестами ({@code FloorPickMathTest}).
 *
 * <p><b>Почему свой класс, а не {@link StructurePickMath}</b>: тот параллельно серьёзно
 * переписывается в другой ветке (claude/curve) — чтобы не завязываться на его API, луч камеры
 * продублирован здесь (десяток строк). И задача проще: пол — плоский, луч достаточно
 * пересечь с ГОРИЗОНТАЛЬНОЙ плоскостью верха пола и перевести точку в кабинет сетки, без
 * AABB-теста по каждой раме (у конструктива рамы стоят друг за другом по глубине, у пола —
 * нет: под каждой точкой плана не больше одной рамы).
 *
 * <p><b>Система координат 3D-вида</b>: X — вдоль ширины экрана ({@code col × ширина
 * кабинета}), Z — вдоль глубины ({@code row × высота кабинета}, ряд 0 — дальний край при
 * виде «спереди»), Y — вверх; земля — {@code Y = 0}. По высоте снизу вверх: ножки
 * ({@link FloorCalc#DISPLAY_LEG_HEIGHT_MM}), рама плашмя ({@link FloorCalc.Layout#frameDepthMm()}),
 * плитки кабинетов ({@link #DISPLAY_CABINET_THICKNESS_MM}).
 */
public final class FloorPickMath {

    private FloorPickMath() {
    }

    /** Толщина плитки кабинета в 3D-виде пола, мм — условная (только для картинки и плоскости
     *  picking'а; в расчёте не участвует). 80 мм — типичный корпус напольного кабинета. */
    public static final double DISPLAY_CABINET_THICKNESS_MM = 80;

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

        public Vec3 normalized() {
            double len = Math.sqrt(x * x + y * y + z * z);
            return len < 1e-9 ? this : new Vec3(x / len, y / len, z / len);
        }
    }

    public record Ray(Vec3 origin, Vec3 direction) {
    }

    /** Луч через пиксель для камеры {@code gluLookAt(eye, center, up)} + {@code
     *  gluPerspective(fovDeg, aspect, ...)} — та же формула, что {@link
     *  StructurePickMath#cameraRay} (см. её javadoc), продублирована сознательно, см.
     *  class-javadoc. {@code pixelY} растёт вниз (координаты AWT). */
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

    /** Точка {@code {x, z}} пересечения луча с горизонтальной плоскостью {@code Y = y}, или
     *  {@code null}, если луч ей параллелен или плоскость позади камеры. */
    public static double[] intersectHorizontalPlane(Ray ray, double y) {
        double dy = ray.direction().y();
        if (Math.abs(dy) < 1e-12) {
            return null;
        }
        double t = (y - ray.origin().y()) / dy;
        if (t < 0) {
            return null;
        }
        return new double[]{ray.origin().x() + ray.direction().x() * t, ray.origin().z() + ray.direction().z() * t};
    }

    /** Высота верхней поверхности пола в 3D-виде: верх плиток кабинетов, если они показаны,
     *  иначе верх рам — по этой плоскости идёт picking (кликают по тому, что видно сверху). */
    public static double topSurfaceY(FloorCalc.Layout layout, boolean cabinetsShown) {
        double y = FloorCalc.DISPLAY_LEG_HEIGHT_MM + layout.frameDepthMm();
        return cabinetsShown ? y + DISPLAY_CABINET_THICKNESS_MM : y;
    }

    /** Пятно рамы на плане, мм: {@code {x, z, длина по X, ширина по Z}} — реальная длина рамы
     *  из библиотеки (950), центрированная в своём отрезке кабинетов (1000), так что между
     *  рамами по короткой стороне остаётся зазор стакана; по глубине так же. */
    public static double[] frameFootprint(FloorCalc.Layout layout, FloorCalc.FramePlacement f) {
        double spanX = f.colCount() * layout.cabinetWidthMm();
        double spanZ = f.rowCount() * layout.cabinetHeightMm();
        double lenX = Math.min(layout.frameLongMm(), spanX);
        double lenZ = Math.min(layout.frameShortMm(), spanZ);
        return new double[]{f.col() * layout.cabinetWidthMm() + (spanX - lenX) / 2.0,
                f.row() * layout.cabinetHeightMm() + (spanZ - lenZ) / 2.0, lenX, lenZ};
    }

    private static boolean spanContains(FloorCalc.Layout l, FloorCalc.FramePlacement f, double x, double z) {
        double x0 = f.col() * l.cabinetWidthMm();
        double z0 = f.row() * l.cabinetHeightMm();
        return x >= x0 && x < x0 + f.colCount() * l.cabinetWidthMm()
                && z >= z0 && z < z0 + f.rowCount() * l.cabinetHeightMm();
    }

    /**
     * Что делает клик в точке плана ({@code x}, {@code z}) — общая логика для клика и подсветки
     * при наведении. Без Ctrl ({@code addMode=false}) — ВИДИМАЯ рама, под отрезком кабинетов
     * которой лежит точка (клик её спрячет); попадание в зазор стакана засчитывается ближайшей
     * раме этого отрезка — иначе узкий зазор был бы «мёртвой зоной». С Ctrl — «призрак» из
     * {@link FloorCalc#addablePositions}: из позиций, накрывающих точку, — с ближайшим к точке
     * центром (клик в левую половину кабинета ставит раму, начинающуюся колонкой левее; в правую
     * — начинающуюся этой колонкой), так раму можно сдвинуть на кабинет. Ровно как у
     * конструктива (Round 24): добавление ТОЛЬКО с Ctrl, призраки без Ctrl в выбор не попадают
     * вовсе — клик по существующей раме не может «промахнуться» в соседний призрак.
     *
     * @return позиция рамы или {@code null}, если под точкой ничего подходящего нет.
     */
    public static FloorCalc.FramePlacement pickTarget(FloorCalc.Layout layout, List<FloorFrameCell> effective,
            double x, double z, boolean addMode) {
        if (!layout.valid()) {
            return null;
        }
        List<FloorCalc.FramePlacement> candidates = new ArrayList<>();
        if (addMode) {
            candidates.addAll(FloorCalc.addablePositions(layout, effective));
        } else {
            for (FloorFrameCell c : effective) {
                if (!c.isHidden()) {
                    candidates.add(layout.placementAt(c.getRow(), c.getCol()));
                }
            }
        }
        FloorCalc.FramePlacement best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (FloorCalc.FramePlacement f : candidates) {
            if (!spanContains(layout, f, x, z)) {
                continue;
            }
            double cx = (f.col() + f.colCount() / 2.0) * layout.cabinetWidthMm();
            double cz = (f.row() + f.rowCount() / 2.0) * layout.cabinetHeightMm();
            double d = (x - cx) * (x - cx) + (z - cz) * (z - cz);
            if (d < bestDist) {
                bestDist = d;
                best = f;
            }
        }
        return best;
    }
}
