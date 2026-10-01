package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.model.StructureBaseFrameCell;
import com.vjstb.ledscheme.model.StructureFrameCell;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.List;

/**
 * Чистая геометрия изогнутых экранов и РАЗДЕЛЬНЫХ башен наземного конструктива (запрос
 * пользователя 2026-10-01) — без Swing/GL, чтобы и расчёт ({@link StructureCalc},
 * {@link ScreenLogic#regenerateStructureCells}), и 3D ({@code ui.Structure3DPanel}), и форма
 * ({@code ui.stage.SetupStagePanel}) опирались на одни и те же формулы, а инварианты
 * («ничего не заходит в экран», коллизии оснований) были покрыты тестами.
 *
 * <p><b>Терминология пользователя</b>: «башня» = ДВА столба вертикальных рам (передний +
 * задний ряд, в коде {@code towerIndex} — это СТОЛБ), скреплённых перемычками, плюс рамы
 * основания между ними. Внешняя ширина башни = шаг столбов (1000 мм) + толщина столба (51
 * мм). Правило построения (решение пользователя): изогнутый экран — башни ВСЕГДА раздельные с
 * зазором не меньше {@link #MIN_TOWER_GAP_MM}; прямой с зазором 0 — прежняя «стена» с общими
 * столбами (этот класс тогда не участвует вовсе); прямой с зазором &gt; 0 — тоже раздельные.
 *
 * <p><b>Система координат</b> — та же, что у 3D-панели, вид сверху (x, z): x — вдоль ширины
 * экрана (развёрнутый прямой экран занимает {@code [0, W]}), z — к зрителю, лицевая
 * поверхность экрана в центре проходит через {@code (W/2, 0)}. Вогнутый: центр кривизны
 * {@code (W/2, +R)} (со стороны зрителя); выпуклый: {@code (W/2, −R)} (за экраном). Изгиб
 * только вокруг вертикали — высота (y) в геометрии плана не участвует.
 *
 * <p><b>Кабинеты плоские</b>: соседние кабинеты стоят под углом θ, их лицевые грани — хорды
 * окружности радиуса R ({@code R = w / (2·sin(θ/2))}). Поэтому тыльная поверхность выпуклого
 * экрана берётся консервативно по середине тыльной грани кабинета: {@code Rb = √(R² − w²/4) − d}
 * (апофема минус глубина; на практике на несколько мм меньше «R − d» из постановки — башня
 * гарантированно не задевает плоский кабинет, а не только идеальную дугу). У вогнутого
 * {@code Rb = R + d} — все точки кабинетов не дальше от центра, чем R + d.
 *
 * <p><b>Локальная система башни</b>: {@code xc} поперёк башни (центр между столбами, ±Wout/2),
 * {@code zc ≤ 0} — от передней плоскости башни (ближняя к экрану грань переднего ряда) вглубь.
 * Мировые координаты — {@link Placement#toWorld}: поворот вокруг вертикали на {@code yawDeg} +
 * перенос в {@code anchor} (середина передней плоскости башни).
 */
public final class StructureCurveMath {

    private StructureCurveMath() {
    }

    /** Зазор от ЛИЦЕВОЙ поверхности экрана до передней плоскости конструктива — то же число,
     *  что исторически в {@code ui.Structure3DPanel} (400 мм; там передний ряд рам начинается
     *  на z = −400). Вынесено сюда, чтобы прямой экран в раздельном режиме стоял на том же
     *  расстоянии, что и «стена». */
    public static final double SCREEN_CLEARANCE_MM = 400;
    /** Минимальный зазор от ТЫЛЬНОЙ поверхности экрана до башни — если кабинет глубже
     *  {@link #SCREEN_CLEARANCE_MM} − этого числа, башня отодвигается дальше, а не врезается. */
    public static final double MIN_BACK_CLEARANCE_MM = 100;
    /** Глубина кабинета, если в библиотеке она не задана (постановка 2026-10-01). */
    public static final double DEFAULT_CABINET_DEPTH_MM = 100;
    /** Минимальный зазор между раздельными башнями — решение пользователя «от 0,5 м». */
    public static final double MIN_TOWER_GAP_MM = 500;
    /** Толщина столба (узкая грань рамы), если тип рамы не выбран. */
    public static final double DEFAULT_POST_THICKNESS_MM = 51;
    private static final double EPS = 1e-6;

    // ---- радиус ↔ угол ----

    /** {@code R = w / (2·sin(θ/2))} — радиус лицевой поверхности по углу между соседними
     *  кабинетами. NaN — угол вне (0°, 180°) или ширина кабинета не задана. */
    public static double radiusFromCabinetAngle(double cabinetWidthMm, double angleDeg) {
        double half = Math.toRadians(angleDeg) / 2.0;
        if (!(cabinetWidthMm > 0) || !(half > 0) || half >= Math.PI / 2.0) {
            return Double.NaN;
        }
        return cabinetWidthMm / (2.0 * Math.sin(half));
    }

    /** Обратная к {@link #radiusFromCabinetAngle}: {@code θ = 2·asin(w / 2R)}, градусы. Радиус
     *  меньше половины кабинета физически невозможен — NaN. */
    public static double cabinetAngleDeg(double cabinetWidthMm, double radiusMm) {
        if (!(cabinetWidthMm > 0) || !(radiusMm > 0) || cabinetWidthMm > 2.0 * radiusMm + EPS) {
            return Double.NaN;
        }
        return Math.toDegrees(2.0 * Math.asin(Math.min(1.0, cabinetWidthMm / (2.0 * radiusMm))));
    }

    // ---- экран ----

    /** Геометрия экрана в плане. {@code cols} колонок кабинетов шириной {@code cabinetWidthMm}
     *  и глубиной {@code cabinetDepthMm}; {@code radiusMm} — к лицевой поверхности (для FLAT
     *  игнорируется). */
    public record Curve(ScreenCurveType type, double radiusMm, double cabinetWidthMm, double cabinetDepthMm,
            int cols) {

        public Curve {
            type = type != null ? type : ScreenCurveType.FLAT;
            cols = Math.max(0, cols);
            cabinetWidthMm = cabinetWidthMm > 0 ? cabinetWidthMm : 500;
            cabinetDepthMm = cabinetDepthMm > 0 ? cabinetDepthMm : DEFAULT_CABINET_DEPTH_MM;
        }

        public boolean curved() {
            return type.isCurved();
        }

        /** Развёрнутая ширина — от изгиба не зависит (кабинеты те же). */
        public double screenWidthMm() {
            return cols * cabinetWidthMm;
        }

        /** Угол между соседними кабинетами, радианы (0 у прямого). */
        public double cabinetAngleRad() {
            if (!curved()) {
                return 0;
            }
            double deg = cabinetAngleDeg(cabinetWidthMm, radiusMm);
            return Double.isNaN(deg) ? Math.PI : Math.toRadians(deg);
        }

        /** Угол всей дуги экрана, радианы. */
        public double arcAngleRad() {
            return cols * cabinetAngleRad();
        }

        /** Расстояние от центра кривизны до середины лицевой грани кабинета. */
        public double apothemMm() {
            return Math.sqrt(Math.max(0, radiusMm * radiusMm - cabinetWidthMm * cabinetWidthMm / 4.0));
        }

        /** Радиус тыльной поверхности, по которому расставляются башни (см. class-javadoc):
         *  вогнутый {@code R + d}, выпуклый {@code √(R² − w²/4) − d}. У прямого — бесконечность. */
        public double backRadiusMm() {
            return switch (type) {
                case CONCAVE -> radiusMm + cabinetDepthMm;
                case CONVEX -> apothemMm() - cabinetDepthMm;
                default -> Double.POSITIVE_INFINITY;
            };
        }

        /** Зазор от тыльной поверхности до передней плоскости башни. */
        public double backClearanceMm() {
            return Math.max(MIN_BACK_CLEARANCE_MM, SCREEN_CLEARANCE_MM - cabinetDepthMm);
        }

        /** Центр кривизны (x, z); у прямого не определён. */
        public double centerX() {
            return screenWidthMm() / 2.0;
        }

        public double centerZ() {
            return type == ScreenCurveType.CONVEX ? -radiusMm : radiusMm;
        }

        /** Хорда лицевой поверхности между крайними точками экрана, мм. */
        public double chordMm() {
            if (!curved()) {
                return screenWidthMm();
            }
            return 2.0 * radiusMm * Math.abs(Math.sin(Math.min(Math.PI, arcAngleRad() / 2.0)));
        }

        /** Стрела прогиба лицевой поверхности (насколько середина отстоит от хорды крайних
         *  точек), мм. */
        public double sagittaMm() {
            if (!curved()) {
                return 0;
            }
            return radiusMm * (1.0 - Math.cos(Math.min(Math.PI, arcAngleRad() / 2.0)));
        }

        /** Радиус физически реализуем: больше половины кабинета и дуга меньше полного круга. */
        public boolean valid() {
            if (!curved()) {
                return true;
            }
            return radiusMm > cabinetWidthMm / 2.0 + EPS && arcAngleRad() < 2.0 * Math.PI - EPS;
        }
    }

    /** Геометрия экрана из {@link Screen}: колонки, ширина/глубина кабинета из {@code type}
     *  (глубины нет — {@link #DEFAULT_CABINET_DEPTH_MM}), тип и радиус изгиба. */
    public static Curve curveOf(Screen screen, CabinetType type) {
        double w = type != null && type.getWidthMm() > 0 ? type.getWidthMm() : 500;
        double d = type != null && type.getDepthMm() != null && type.getDepthMm() > 0
                ? type.getDepthMm() : DEFAULT_CABINET_DEPTH_MM;
        return new Curve(screen.getStructureCurveType(), screen.getStructureCurveRadiusMm(), w, d, screen.getCols());
    }

    // ---- башня ----

    /** Габариты одной (пользовательской) башни: шаг столбов (между осями), толщина столба,
     *  глубина модуля основания и полная глубина башни (столбы + основание + вынос). */
    public record TowerSpec(double postSpacingMm, double postThicknessMm, double sectionDepthMm, double depthMm) {

        public TowerSpec {
            postSpacingMm = postSpacingMm > 0 ? postSpacingMm : StructureCalc.DEFAULT_TOWER_SPACING_MM;
            postThicknessMm = postThicknessMm > 0 ? postThicknessMm : DEFAULT_POST_THICKNESS_MM;
            sectionDepthMm = sectionDepthMm > 0 ? sectionDepthMm : StructureCalc.DEFAULT_SECTION_DEPTH_MM;
            depthMm = depthMm > 0 ? depthMm : sectionDepthMm;
        }

        /** Внешняя ширина башни вдоль экрана: 1000 + 51 мм для штатной рамы. */
        public double outerWidthMm() {
            return postSpacingMm + postThicknessMm;
        }

        public TowerSpec withDepth(double newDepthMm) {
            return new TowerSpec(postSpacingMm, postThicknessMm, sectionDepthMm, newDepthMm);
        }
    }

    /** Прямоугольник в ЛОКАЛЬНОЙ системе башни (вид сверху). */
    public record Rect(double x0, double x1, double z0, double z1) {
        public double[][] corners() {
            return new double[][]{{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
        }
    }

    /** Footprint башни в плане: два столба (на всю глубину — над секциями выноса стоят
     *  усилительные рамы, row 2) и все секции основания между столбами (последняя может быть
     *  неполной, если глубина не кратна модулю). Объединение = внешний прямоугольник башни. */
    public static List<Rect> footprintLocal(TowerSpec t) {
        double half = t.outerWidthMm() / 2.0;
        double th = t.postThicknessMm();
        double depth = t.depthMm();
        List<Rect> rects = new ArrayList<>();
        rects.add(new Rect(-half, -half + th, -depth, 0));
        rects.add(new Rect(half - th, half, -depth, 0));
        double z = 0;
        while (z < depth - EPS) {
            double next = Math.min(depth, z + t.sectionDepthMm());
            rects.add(new Rect(-half + th, half - th, -next, -z));
            z = next;
        }
        return rects;
    }

    /** Положение одной башни (или колонны кабинетов): середина передней плоскости {@code
     *  (anchorX, anchorZ)} и поворот вокруг вертикали. Поворот — тот же, что {@code
     *  glRotate(yawDeg, 0, 1, 0)}: {@code x' = x·cos + z·sin}, {@code z' = −x·sin + z·cos}. */
    public record Placement(int index, double anchorX, double anchorZ, double yawDeg) {

        public double[] toWorld(double xc, double zc) {
            double a = Math.toRadians(yawDeg);
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            return new double[]{anchorX + xc * cos + zc * sin, anchorZ - xc * sin + zc * cos};
        }

        public double[] toLocal(double xw, double zw) {
            double a = Math.toRadians(yawDeg);
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            double dx = xw - anchorX;
            double dz = zw - anchorZ;
            return new double[]{dx * cos - dz * sin, dx * sin + dz * cos};
        }
    }

    // ---- правило «прямой/изогнутый/зазор» ----

    /** Раздельные башни: экран изогнут ИЛИ задан зазор &gt; 0 (решение пользователя
     *  2026-10-01). Прямой с зазором 0 — прежняя стена. */
    public static boolean separateTowers(ScreenCurveType type, double gapMm) {
        return (type != null && type.isCurved()) || gapMm > EPS;
    }

    public static boolean separateTowers(Screen screen) {
        return screen != null && separateTowers(screen.getStructureCurveType(), screen.getStructureTowerGapMm());
    }

    /** Зазор, с которым реально считается раздельный режим: не меньше {@link
     *  #MIN_TOWER_GAP_MM} (у прямого экрана 0 — это стена, раздельного режима нет). */
    public static double effectiveGapMm(ScreenCurveType type, double gapMm) {
        if (!separateTowers(type, gapMm)) {
            return 0;
        }
        return Math.max(MIN_TOWER_GAP_MM, gapMm);
    }

    // ---- расстановка ----

    /** Положение башни {@code k} (0..n−1; вне диапазона — экстраполяция по той же дуге):
     *  башни симметричны относительно центра экрана, шаг центров = внешняя ширина башни +
     *  зазор, измеренный по тыльной поверхности экрана. Вогнутый — передняя плоскость
     *  касается тыльной поверхности СНАРУЖИ (заход в экран невозможен) плюс зазор до экрана;
     *  выпуклый — плоскость, касательная к тыльной поверхности, дополнительно сдвинута назад
     *  на стрелу {@code e = Rb − √(Rb² − (Wout/2)²)}, чтобы углы башни не заходили в экран. */
    public static Placement placement(Curve c, TowerSpec t, int towerCount, double gapMm, int k) {
        double pitch = t.outerWidthMm() + gapMm;
        double s = (k - (towerCount - 1) / 2.0) * pitch;
        double w = c.screenWidthMm();
        double cb = c.backClearanceMm();
        switch (c.type()) {
            case CONCAVE -> {
                double rb = c.backRadiusMm();
                double phi = s / rb;
                double r = rb + cb;
                return new Placement(k, w / 2.0 + r * Math.sin(phi), c.radiusMm() - r * Math.cos(phi),
                        -Math.toDegrees(phi));
            }
            case CONVEX -> {
                double rb = c.backRadiusMm();
                double phi = rb > 0 ? s / rb : 0;
                double h = convexFrontPlaneDistance(c, t);
                return new Placement(k, w / 2.0 + h * Math.sin(phi), -c.radiusMm() + h * Math.cos(phi),
                        Math.toDegrees(phi));
            }
            default -> {
                return new Placement(k, w / 2.0 + s, -(c.cabinetDepthMm() + cb), 0);
            }
        }
    }

    /** Выпуклый: расстояние от центра кривизны до передней плоскости башни — {@code
     *  √(Rb² − (Wout/2)²) − зазор}, т.е. касательная к тыльной поверхности, сдвинутая назад на
     *  стрелу {@link #convexShiftMm} и ещё на зазор до экрана. */
    static double convexFrontPlaneDistance(Curve c, TowerSpec t) {
        double rb = c.backRadiusMm();
        double half = t.outerWidthMm() / 2.0;
        return Math.sqrt(Math.max(0, rb * rb - half * half)) - c.backClearanceMm();
    }

    /** Стрела сдвига назад для выпуклого экрана: {@code e = Rb − √(Rb² − (Wout/2)²)}. */
    public static double convexShiftMm(Curve c, TowerSpec t) {
        double rb = c.backRadiusMm();
        double half = t.outerWidthMm() / 2.0;
        return rb - Math.sqrt(Math.max(0, rb * rb - half * half));
    }

    public static List<Placement> placements(Curve c, TowerSpec t, int towerCount, double gapMm) {
        List<Placement> list = new ArrayList<>();
        for (int k = 0; k < towerCount; k++) {
            list.add(placement(c, t, towerCount, gapMm, k));
        }
        return list;
    }

    /** Колонны кабинетов изогнутого экрана: середина ЛИЦЕВОЙ грани колонны {@code i} и её
     *  поворот (локально кабинет занимает {@code x ∈ [−w/2, w/2]}, {@code z ∈ [−d, 0]}). У
     *  прямого экрана — обычная сетка без поворота. */
    public static List<Placement> cabinetColumns(Curve c) {
        List<Placement> list = new ArrayList<>();
        double w = c.cabinetWidthMm();
        double theta = c.cabinetAngleRad();
        double a = c.apothemMm();
        for (int i = 0; i < c.cols(); i++) {
            double gamma = (i + 0.5 - c.cols() / 2.0) * theta;
            switch (c.type()) {
                case CONCAVE -> list.add(new Placement(i, c.centerX() + a * Math.sin(gamma),
                        c.radiusMm() - a * Math.cos(gamma), -Math.toDegrees(gamma)));
                case CONVEX -> list.add(new Placement(i, c.centerX() + a * Math.sin(gamma),
                        -c.radiusMm() + a * Math.cos(gamma), Math.toDegrees(gamma)));
                default -> list.add(new Placement(i, i * w + w / 2.0, 0, 0));
            }
        }
        return list;
    }

    /** Отрезок развёрнутой ширины экрана {@code [uMin, uMax]} (мм от левого края), который
     *  башня {@code k} закрывает собой — по нему проверяется, есть ли под башней кабинеты
     *  нижнего ряда (то же правило, что «стена» применяет к столбу). */
    public static double[] towerScreenSpanMm(Curve c, TowerSpec t, int towerCount, double gapMm, int k) {
        double pitch = t.outerWidthMm() + gapMm;
        double s = (k - (towerCount - 1) / 2.0) * pitch;
        double half = t.outerWidthMm() / 2.0;
        double scale = 1.0;
        if (c.curved()) {
            double rb = c.backRadiusMm();
            double theta = c.cabinetAngleRad();
            // дуга по тыльной поверхности → угол → развёрнутая ширина по лицевой (w на θ)
            scale = rb > 0 && theta > 0 ? c.cabinetWidthMm() / (theta * rb) : 1.0;
        }
        double center = c.screenWidthMm() / 2.0 + s * scale;
        return new double[]{center - half * scale, center + half * scale};
    }

    /** Длина тыльной поверхности экрана, вдоль которой расставляются башни. */
    public static double backLengthMm(Curve c) {
        if (!c.curved()) {
            return c.screenWidthMm();
        }
        return c.arcAngleRad() * Math.max(0, c.backRadiusMm());
    }

    /** Авто-число башен: сколько башен с заданным зазором помещается вдоль тыльной поверхности
     *  экрана, не меньше 1. */
    public static int suggestTowerCount(Curve c, TowerSpec t, double gapMm) {
        double length = backLengthMm(c);
        int n = (int) Math.floor((length + gapMm + EPS) / (t.outerWidthMm() + gapMm));
        return Math.max(1, n);
    }

    // ---- «не проходить сквозь экран» ----

    /** Точка плана лежит внутри какого-либо кабинета (плоские кабинеты, см. class-javadoc). */
    public static boolean insideScreenVolume(Curve c, double x, double z) {
        double hw = c.cabinetWidthMm() / 2.0;
        for (Placement col : cabinetColumns(c)) {
            double[] l = col.toLocal(x, z);
            if (l[0] > -hw + EPS && l[0] < hw - EPS && l[1] > -c.cabinetDepthMm() + EPS && l[1] < -EPS) {
                return true;
            }
        }
        return false;
    }

    /** Точка внутри кольцевой полосы между лицевой (R) и тыльной (R ± d) поверхностями в
     *  пределах угла дуги — формулировка инварианта в постановке 2026-10-01. Для прямого —
     *  слой {@code z ∈ [−d, 0]} над {@code x ∈ [0, W]}. */
    public static boolean insideScreenAnnulus(Curve c, double x, double z) {
        if (!c.curved()) {
            return x > EPS && x < c.screenWidthMm() - EPS && z > -c.cabinetDepthMm() + EPS && z < -EPS;
        }
        double dx = x - c.centerX();
        double dz = z - c.centerZ();
        double r = Math.hypot(dx, dz);
        double phi = c.type() == ScreenCurveType.CONCAVE ? Math.atan2(dx, -dz) : Math.atan2(dx, dz);
        if (Math.abs(phi) >= c.arcAngleRad() / 2.0) {
            return false;
        }
        double inner = c.type() == ScreenCurveType.CONCAVE ? c.radiusMm() : c.radiusMm() - c.cabinetDepthMm();
        double outer = c.type() == ScreenCurveType.CONCAVE ? c.radiusMm() + c.cabinetDepthMm() : c.radiusMm();
        return r > inner + EPS && r < outer - EPS;
    }

    /** Все вершины footprint'а башни (мировые координаты плана). */
    public static List<double[]> towerVerticesWorld(Placement p, TowerSpec t) {
        List<double[]> pts = new ArrayList<>();
        for (Rect r : footprintLocal(t)) {
            for (double[] corner : r.corners()) {
                pts.add(p.toWorld(corner[0], corner[1]));
            }
        }
        return pts;
    }

    /** Ни одна вершина ни одной башни не лежит в объёме экрана (ни в кабинетах, ни в
     *  кольцевой полосе) — инвариант, который {@link #placement} обязан соблюдать всегда. */
    public static boolean towersClearOfScreen(Curve c, TowerSpec t, int towerCount, double gapMm) {
        for (Placement p : placements(c, t, towerCount, gapMm)) {
            for (double[] v : towerVerticesWorld(p, t)) {
                if (insideScreenVolume(c, v[0], v[1]) || insideScreenAnnulus(c, v[0], v[1])) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---- коллизии (SAT) ----

    /** Пересечение двух выпуклых четырёхугольников плана (теорема о разделяющей оси); касание
     *  по ребру пересечением не считается. */
    public static boolean intersects(double[][] a, double[][] b) {
        return !hasSeparatingAxis(a, b) && !hasSeparatingAxis(b, a);
    }

    private static boolean hasSeparatingAxis(double[][] poly, double[][] other) {
        for (int i = 0; i < poly.length; i++) {
            double[] p0 = poly[i];
            double[] p1 = poly[(i + 1) % poly.length];
            double nx = -(p1[1] - p0[1]);
            double nz = p1[0] - p0[0];
            double len = Math.hypot(nx, nz);
            if (len < 1e-12) {
                continue;
            }
            nx /= len;
            nz /= len;
            double minA = Double.POSITIVE_INFINITY;
            double maxA = Double.NEGATIVE_INFINITY;
            for (double[] v : poly) {
                double d = v[0] * nx + v[1] * nz;
                minA = Math.min(minA, d);
                maxA = Math.max(maxA, d);
            }
            double minB = Double.POSITIVE_INFINITY;
            double maxB = Double.NEGATIVE_INFINITY;
            for (double[] v : other) {
                double d = v[0] * nx + v[1] * nz;
                minB = Math.min(minB, d);
                maxB = Math.max(maxB, d);
            }
            if (maxA <= minB + 1e-6 || maxB <= minA + 1e-6) {
                return true;
            }
        }
        return false;
    }

    private static List<double[][]> worldRects(Placement p, TowerSpec t) {
        List<double[][]> out = new ArrayList<>();
        for (Rect r : footprintLocal(t)) {
            double[][] corners = r.corners();
            double[][] w = new double[4][];
            for (int i = 0; i < 4; i++) {
                w[i] = p.toWorld(corners[i][0], corners[i][1]);
            }
            out.add(w);
        }
        return out;
    }

    /** Пары башен (i &lt; j), footprint'ы которых пересекаются (любой прямоугольник одной с
     *  любым другой). Проверяются ВСЕ пары, не только соседние. */
    public static List<int[]> collisions(Curve c, TowerSpec t, int towerCount, double gapMm) {
        List<Placement> ps = placements(c, t, towerCount, gapMm);
        List<List<double[][]>> rects = new ArrayList<>();
        for (Placement p : ps) {
            rects.add(worldRects(p, t));
        }
        List<int[]> result = new ArrayList<>();
        for (int i = 0; i < ps.size(); i++) {
            for (int j = i + 1; j < ps.size(); j++) {
                if (anyIntersect(rects.get(i), rects.get(j))) {
                    result.add(new int[]{i, j});
                }
            }
        }
        return result;
    }

    private static boolean anyIntersect(List<double[][]> a, List<double[][]> b) {
        for (double[][] ra : a) {
            for (double[][] rb : b) {
                if (intersects(ra, rb)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Минимальный зазор (мм, не меньше {@link #MIN_TOWER_GAP_MM}, округлён вверх до 10 мм),
     *  при котором footprint'ы башен не пересекаются — бисекция (с ростом зазора башни на дуге
     *  расходятся, пересечение монотонно пропадает). NaN — не помогает даже зазор 100 м. */
    public static double minimalGapMm(Curve c, TowerSpec t, int towerCount) {
        int n = Math.max(2, towerCount);
        double lo = MIN_TOWER_GAP_MM;
        if (collisions(c, t, n, lo).isEmpty()) {
            return lo;
        }
        double hi = lo * 2;
        while (!collisions(c, t, n, hi).isEmpty()) {
            hi *= 2;
            if (hi > 100_000) {
                return Double.NaN;
            }
        }
        for (int i = 0; i < 50 && hi - lo > 0.5; i++) {
            double mid = (lo + hi) / 2.0;
            if (collisions(c, t, n, mid).isEmpty()) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        double rounded = Math.ceil(hi / 10.0) * 10.0;
        return collisions(c, t, n, rounded).isEmpty() ? rounded : hi;
    }

    /** Максимальная глубина башни (мм) при заданном зазоре, при которой footprint'ы не
     *  пересекаются — бисекция по глубине (глубже — прямоугольник только растёт). {@code
     *  +∞} — пересечений нет при любой разумной глубине (вогнутый/прямой); 0 — пересекаются уже
     *  при минимальной глубине. */
    public static double maxDepthMm(Curve c, TowerSpec t, int towerCount, double gapMm) {
        int n = Math.max(2, towerCount);
        double limit = 50_000;
        if (collisions(c, t.withDepth(limit), n, gapMm).isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        double lo = 1;
        if (!collisions(c, t.withDepth(lo), n, gapMm).isEmpty()) {
            return 0;
        }
        double hi = limit;
        for (int i = 0; i < 60 && hi - lo > 0.5; i++) {
            double mid = (lo + hi) / 2.0;
            if (collisions(c, t.withDepth(mid), n, gapMm).isEmpty()) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return Math.floor(lo);
    }

    // ---- сводка для экрана ----

    /** Всё, что нужно расчёту/3D/форме о раздельных башнях экрана. */
    public record Setup(boolean separate, Curve curve, TowerSpec tower, int towerCount, double gapMm) {
    }

    /** Параметры раздельного режима ЭКРАНА: геометрия экрана, габариты башни (тип рамы из
     *  библиотеки, глубина — по введённому выносу и по реально стоящим секциям), число башен
     *  (сохранённое число столбов / 2; если столбов ещё нет — пользовательское или авто) и
     *  эффективный зазор. {@code separate == false} — экран строится прежней «стеной». */
    public static Setup setupOf(Screen screen, CabinetType type, Workspace workspace) {
        Curve curve = curveOf(screen, type);
        TowerSpec tower = towerSpecOf(screen, workspace);
        boolean separate = separateTowers(screen);
        double gap = effectiveGapMm(screen.getStructureCurveType(), screen.getStructureTowerGapMm());
        int n;
        if (screen.getStructureTowerCount() > 0) {
            n = Math.max(1, (screen.getStructureTowerCount() + 1) / 2);
        } else if (screen.getStructureSeparateTowerCount() > 0) {
            n = screen.getStructureSeparateTowerCount();
        } else {
            n = suggestTowerCount(curve, tower, gap);
        }
        return new Setup(separate, curve, tower, n, gap);
    }

    /** Габариты башни экрана: шаг столбов — {@link StructureCalc#DEFAULT_TOWER_SPACING_MM},
     *  толщина столба и модуль основания — из выбранного типа рамы, глубина — максимум из
     *  введённого выноса ({@link StructureCalc#totalBaseSections}), номинальной сетки и
     *  реально стоящих (не скрытых) секций/усилительных рам. */
    public static TowerSpec towerSpecOf(Screen screen, Workspace workspace) {
        StructureFrameType frameType = workspace != null
                ? workspace.structureFrameTypeById(screen.getStructureFrameTypeId()) : null;
        double frameW = frameType != null && frameType.getWidthMm() != null && frameType.getWidthMm() > 0
                ? frameType.getWidthMm() : StructureCalc.DEFAULT_FRAME_WIDTH_MM;
        double frameD = frameType != null && frameType.getDepthMm() != null && frameType.getDepthMm() > 0
                ? frameType.getDepthMm() : DEFAULT_POST_THICKNESS_MM;
        int sections = Math.max(StructureCalc.totalBaseSections(screen.getStructureBaseExtensionMm(), frameW,
                screen.getStructureBackRowSegments()),
                StructureCalc.CORE_BASE_SECTION_COUNT + screen.getStructureExtendedBaseSections());
        for (StructureBaseFrameCell c : screen.getStructureBaseFrameCells()) {
            if (!c.isHidden()) {
                sections = Math.max(sections, c.getSectionIndex() + 1);
            }
        }
        for (StructureFrameCell c : screen.getStructureFrameCells()) {
            if (!c.isHidden() && c.getRow() == 2) {
                sections = Math.max(sections, c.getSegmentIndex() + 1);
            } else if (!c.isHidden() && c.getRow() == 1) {
                sections = Math.max(sections, 2);
            }
        }
        return new TowerSpec(StructureCalc.DEFAULT_TOWER_SPACING_MM, frameD, frameW, sections * frameW);
    }

    /** Итог проверки раздельных башен. {@code minGapMm}/{@code maxDepthMm} считаются только
     *  при наличии коллизий (иначе NaN). {@code edgeMarginMm} — сколько развёрнутой ширины
     *  экрана остаётся НЕ закрытым башнями с каждой стороны (отрицательное — башни выходят за
     *  край). */
    public record Report(Setup setup, List<Placement> placements, List<int[]> collisions, double minGapMm,
            double maxDepthMm, double edgeMarginMm, boolean clearOfScreen, List<String> warnings) {
    }

    public static Report analyze(Setup s) {
        List<String> warnings = new ArrayList<>();
        Curve c = s.curve();
        TowerSpec t = s.tower();
        List<Placement> ps = placements(c, t, s.towerCount(), s.gapMm());
        if (!c.valid()) {
            warnings.add(c.arcAngleRad() >= 2 * Math.PI - EPS
                    ? "Дуга экрана получается не меньше 360° — уменьшите изгиб (увеличьте радиус)."
                    : String.format("Радиус %.0f мм меньше половины кабинета — такой изгиб невозможен.",
                            c.radiusMm()));
        }
        if (c.type() == ScreenCurveType.CONVEX && c.backRadiusMm() <= t.outerWidthMm() / 2.0) {
            warnings.add(String.format("Радиус %.0f мм слишком мал: башня шириной %.0f мм не помещается за"
                    + " выпуклым экраном.", c.radiusMm(), t.outerWidthMm()));
        }
        List<int[]> col = collisions(c, t, s.towerCount(), s.gapMm());
        double minGap = Double.NaN;
        double maxDepth = Double.NaN;
        if (!col.isEmpty()) {
            minGap = minimalGapMm(c, t, s.towerCount());
            maxDepth = maxDepthMm(c, t, s.towerCount(), s.gapMm());
            StringBuilder pairs = new StringBuilder();
            for (int i = 0; i < Math.min(4, col.size()); i++) {
                if (i > 0) {
                    pairs.append(", ");
                }
                pairs.append(col.get(i)[0] + 1).append('–').append(col.get(i)[1] + 1);
            }
            if (col.size() > 4) {
                pairs.append(", …");
            }
            StringBuilder msg = new StringBuilder(String.format(
                    "Основания соседних башен пересекаются (башни %s). ", pairs));
            msg.append(Double.isNaN(minGap) ? "Подходящий зазор не найден."
                    : String.format("Минимальный зазор без пересечения: %.0f мм.", minGap));
            if (maxDepth > 0 && Double.isFinite(maxDepth)) {
                int sections = (int) Math.floor(maxDepth / t.sectionDepthMm());
                msg.append(String.format(" При зазоре %.0f мм глубина башни — не больше %.0f мм (%d секц. по %.0f мм).",
                        s.gapMm(), maxDepth, sections, t.sectionDepthMm()));
            }
            warnings.add(msg.toString());
        }
        double margin = edgeMarginMm(c, t, s.towerCount(), s.gapMm());
        double pitch = t.outerWidthMm() + s.gapMm();
        if (margin < -1) {
            warnings.add(String.format("Крайние башни выходят за край экрана на %.0f мм с каждой стороны.", -margin));
        } else if (margin > pitch / 2.0) {
            warnings.add(String.format("Башни не доходят до краёв экрана на %.0f мм с каждой стороны"
                    + " — можно добавить башню или уменьшить зазор.", margin));
        }
        boolean clear = towersClearOfScreen(c, t, s.towerCount(), s.gapMm());
        if (!clear) {
            warnings.add("Башня заходит в объём экрана — проверьте радиус и глубину кабинета.");
        }
        return new Report(s, ps, col, minGap, maxDepth, margin, clear, warnings);
    }

    /** Незакрытая башнями развёрнутая ширина экрана с каждой стороны (симметрично). */
    public static double edgeMarginMm(Curve c, TowerSpec t, int towerCount, double gapMm) {
        if (towerCount <= 0) {
            return c.screenWidthMm() / 2.0;
        }
        double[] first = towerScreenSpanMm(c, t, towerCount, gapMm, 0);
        return first[0];
    }
}
