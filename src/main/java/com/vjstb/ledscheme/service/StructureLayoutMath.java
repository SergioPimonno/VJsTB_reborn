package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.StructureTowerPlacement;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.StructureCurveMath.Placement;
import com.vjstb.ledscheme.service.StructureCurveMath.Setup;
import com.vjstb.ledscheme.service.StructureCurveMath.TowerSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Ручная расстановка наземного конструктива и конструктив ГРУППЫ экранов (запрос пользователя
 * 2026-10-22): башни стоят там и под тем углом, куда их поставил инженер — например, две башни
 * встык под 90° — а экраны группы стоят в плане под углом друг к другу (в проекте «Жара» левая и
 * правая группы экранов собираются под 90°, центральная — под 120°). Чистая геометрия плана, без
 * Swing/GL, на тех же примитивах, что {@link StructureCurveMath}.
 *
 * <p><b>Системы координат</b> (вид сверху, x вправо, z к зрителю, поворот — как {@code
 * glRotate(yawDeg, 0, 1, 0)}, см. {@link Placement}):
 * <ul>
 *   <li><i>система экрана</i> — начало в левой точке лицевой поверхности, развёрнутая ширина
 *       {@code [0, W]}; в ней башни (авто — {@link StructureCurveMath#placement}, вручную —
 *       {@link Screen#getStructureTowerPlacements()});</li>
 *   <li><i>система группы</i> — общая для всех экранов группы; поза экрана в ней — {@link
 *       #screenPose} ({@link Screen#getStructurePoseXMm()} и др.). Мировое положение башни —
 *       композиция {@link #compose}: поза экрана ∘ положение башни.</li>
 * </ul>
 * Одиночный экран имеет нулевую позу, и всё сводится к системе экрана.
 *
 * <p>Это только ПРЕДУПРЕЖДЕНИЯ и расстановка: ничего не запрещается (касание контуров —
 * допустимо, пересечение — предупреждение, как у раздельных башен). Высота в плане не
 * участвует — как и в {@link StructureCurveMath#analyze}.
 */
public final class StructureLayoutMath {

    private StructureLayoutMath() {
    }

    /** Сторона башни в её локальной системе: {@code FRONT} — передняя плоскость (к экрану,
     *  {@code z = 0}), {@code BACK} — дальняя, {@code LEFT}/{@code RIGHT} — боковые грани. */
    public enum Side { FRONT, BACK, LEFT, RIGHT }

    // ---- преобразования ----

    /** Композиция {@code outer ∘ inner}: {@code inner} задан в системе, чьё положение в мировой —
     *  {@code outer}. Повороты вокруг одной оси складываются. Индекс берётся у {@code inner}. */
    public static Placement compose(Placement outer, Placement inner) {
        double[] a = outer.toWorld(inner.anchorX(), inner.anchorZ());
        return new Placement(inner.index(), a[0], a[1], normalizeDeg(outer.yawDeg() + inner.yawDeg()));
    }

    /** Поза экрана в системе группы. */
    public static Placement screenPose(Screen screen) {
        return new Placement(0, screen.getStructurePoseXMm(), screen.getStructurePoseZMm(),
                screen.getStructurePoseYawDeg());
    }

    /** Положение, заданное в МИРОВОЙ системе, — в системе {@code frame}: обратное к {@link
     *  #compose} ({@code compose(frame, toFrame(frame, w))} даёт {@code w}). */
    public static Placement toFrame(Placement frame, Placement world) {
        double[] l = frame.toLocal(world.anchorX(), world.anchorZ());
        return new Placement(world.index(), l[0], l[1], normalizeDeg(world.yawDeg() - frame.yawDeg()));
    }

    /** Угол в {@code (−180°, 180°]}. */
    public static double normalizeDeg(double deg) {
        double d = deg % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        } else if (d <= -180.0) {
            d += 360.0;
        }
        return d;
    }

    // ---- ширина башни ----

    /** Шаг столбов башни {@code tower}, мм: стандартный {@link StructureCalc#DEFAULT_TOWER_SPACING_MM}
     *  (рама 950 + стакан 50), а у «узкой» башни — высота выбранной рамы + стакан (короткая 850 + 50 =
     *  900 мм): горизонтальные рамы башни (перемычки, основание) короче, и столбы стоят ближе. */
    public static double towerPostSpacingMm(Screen screen, Workspace workspace, int tower) {
        String id = screen.towerWidthFrameTypeOf(tower);
        if (id == null || workspace == null) {
            return StructureCalc.DEFAULT_TOWER_SPACING_MM;
        }
        com.vjstb.ledscheme.model.StructureFrameType t = workspace.structureFrameTypeById(id);
        if (t == null || t.getHeightMm() == null || t.getHeightMm() <= 0) {
            return StructureCalc.DEFAULT_TOWER_SPACING_MM;
        }
        return t.getHeightMm() + StructureCupMath.cupHeightMm(screen, workspace);
    }

    // ---- положение башен ----

    /** Положение башни {@code k} в системе ЭКРАНА: при ручной расстановке — запись {@link
     *  Screen#towerPlacementOf} (нет записи — расчётное положение, чтобы 3D не падало на битых
     *  данных), иначе расчёт по форме экрана. */
    public static Placement towerPlacementInScreen(Screen screen, Setup setup, int k) {
        if (screen.isStructureManualLayout()) {
            StructureTowerPlacement p = screen.towerPlacementOf(k);
            if (p != null) {
                return new Placement(k, p.getAnchorXMm(), p.getAnchorZMm(), p.getYawDeg());
            }
        }
        return StructureCurveMath.placement(setup.curve(), setup.tower(), setup.towerCount(), setup.gapMm(), k);
    }

    /** То же в системе ГРУППЫ ({@link #compose} с позой экрана). */
    public static Placement towerPlacementInGroup(Screen screen, Setup setup, int k) {
        return compose(screenPose(screen), towerPlacementInScreen(screen, setup, k));
    }

    /** Авто-раскладка экрана в виде записей ручного режима — стартовая раскладка при включении
     *  «вручную» (решение пользователя: текущая авто-раскладка копируется, дальше инженер двигает
     *  её сам). У «стены» (общие столбы, раздельного режима нет) башни ставятся по ширине экрана с
     *  зазором {@link StructureCurveMath#MIN_TOWER_GAP_MM}; у раздельных — ровно как они стоят. */
    public static List<StructureTowerPlacement> autoLayoutOf(Screen screen, CabinetType type,
            Workspace workspace) {
        StructureCurveMath.Curve curve = StructureCurveMath.curveOf(screen, type);
        TowerSpec spec = StructureCurveMath.towerSpecOf(screen, workspace);
        double gap = StructureCurveMath.effectiveGapMm(screen.getStructureCurveType(),
                screen.getStructureTowerGapMm());
        if (gap <= 0) {
            gap = StructureCurveMath.MIN_TOWER_GAP_MM;
        }
        int n;
        if (screen.isStructureManualLayout() && !screen.getStructureTowerPlacements().isEmpty()) {
            n = screen.getStructureTowerPlacements().size();
        } else if (StructureCurveMath.separateTowers(screen) && screen.getStructureSeparateTowerCount() > 0) {
            n = screen.getStructureSeparateTowerCount();
        } else {
            n = StructureCurveMath.suggestTowerCount(curve, spec, gap);
        }
        List<StructureTowerPlacement> out = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            Placement p = StructureCurveMath.placement(curve, spec, n, gap, k);
            out.add(new StructureTowerPlacement(k, p.anchorX(), p.anchorZ(), p.yawDeg()));
        }
        return out;
    }

    // ---- «встык» ----

    /** {@code {nx, nz, mx, mz}}: внешняя нормаль и середина стороны башни в её локальной системе
     *  (передняя плоскость на {@code z = 0}, башня уходит в {@code −z} на глубину {@code D}). */
    private static double[] sideInfo(TowerSpec t, Side side) {
        double w = t.outerWidthMm();
        double d = t.depthMm();
        return switch (side) {
            case FRONT -> new double[]{0, 1, 0, 0};
            case BACK -> new double[]{0, -1, 0, -d};
            case LEFT -> new double[]{-1, 0, -w / 2.0, -d / 2.0};
            case RIGHT -> new double[]{1, 0, w / 2.0, -d / 2.0};
        };
    }

    private static double angleDeg(double x, double z) {
        return Math.toDegrees(Math.atan2(z, x));
    }

    /** Положение башни-«перемещаемой» (в МИРОВОЙ системе), при котором её сторона {@code
     *  moverSide} прилегает к стороне {@code targetSide} башни-цели, лицом к лицу (нормали
     *  противоположны), поворот выходит сам: например, {@code RIGHT} цели + {@code FRONT}
     *  перемещаемой — башни встык под 90°; {@code RIGHT} + {@code LEFT} — рядом, параллельно.
     *  {@code alongMm} — сдвиг вдоль стороны цели от её середины (по обходу {@code (nz, −nx)} в
     *  мировой системе), {@code gapMm} — зазор между гранями (0 — вплотную). Индекс результата 0. */
    public static Placement attachTowerFlush(Placement targetWorld, TowerSpec targetSpec, Side targetSide,
            TowerSpec moverSpec, Side moverSide, double alongMm, double gapMm) {
        double[] ts = sideInfo(targetSpec, targetSide);
        double[] ms = sideInfo(moverSpec, moverSide);
        Placement rotT = new Placement(0, 0, 0, targetWorld.yawDeg());
        double[] nT = rotT.toWorld(ts[0], ts[1]);
        double[] tT = rotT.toWorld(ts[1], -ts[0]);
        double[] mT = targetWorld.toWorld(ts[2], ts[3]);
        double px = mT[0] + tT[0] * alongMm + nT[0] * gapMm;
        double pz = mT[1] + tT[1] * alongMm + nT[1] * gapMm;
        double yaw = normalizeDeg(angleDeg(ms[0], ms[1]) - angleDeg(-nT[0], -nT[1]));
        double[] off = new Placement(0, 0, 0, yaw).toWorld(ms[2], ms[3]);
        return new Placement(0, px - off[0], pz - off[1], yaw);
    }

    // ---- перетаскивание в 3D-редакторе ----

    /** Башня-цель для прилипания: положение в мировой системе и габариты. */
    public record SnapTarget(Placement world, TowerSpec spec) {
    }

    /** Точка {@code {x, z}} пересечения луча с горизонтальной плоскостью {@code y = planeY}
     *  (перетаскивание по земле). {@code null} — луч параллелен плоскости или плоскость позади. */
    public static double[] rayGroundPoint(StructurePickMath.Ray ray, double planeY) {
        double dy = ray.direction().y();
        if (Math.abs(dy) < 1e-9) {
            return null;
        }
        double t = (planeY - ray.origin().y()) / dy;
        if (t <= 0) {
            return null;
        }
        return new double[]{ray.origin().x() + ray.direction().x() * t, ray.origin().z() + ray.direction().z() * t};
    }

    /** Положение, повёрнутое на {@code newYawDeg} ТАК, чтобы точка {@code (localCx, localCz)} (в
     *  локальной системе положения — например, центр контура башни) осталась на месте в мировой
     *  системе: поворот башни вокруг своего центра, а не вокруг середины передней плоскости. */
    public static Placement rotateAbout(Placement p, double localCx, double localCz, double newYawDeg) {
        double[] fixed = p.toWorld(localCx, localCz);
        double yaw = normalizeDeg(newYawDeg);
        double[] off = new Placement(0, 0, 0, yaw).toWorld(localCx, localCz);
        return new Placement(p.index(), fixed[0] - off[0], fixed[1] - off[1], yaw);
    }

    /** Угол, округлённый до ближайшего кратного {@code stepDeg} (шаг ≤ 0 — без округления). */
    public static double snapAngle(double deg, double stepDeg) {
        if (stepDeg <= 0) {
            return normalizeDeg(deg);
        }
        return normalizeDeg(Math.round(deg / stepDeg) * stepDeg);
    }

    /** Длина стороны башни, мм. */
    private static double sideLength(TowerSpec t, Side side) {
        return side == Side.FRONT || side == Side.BACK ? t.outerWidthMm() : t.depthMm();
    }

    /** Прилипание «встык» при перетаскивании: если башня {@code mover} (мировое положение) почти
     *  лицом к лицу с какой-то стороной башни-цели — по углу в пределах {@code angTolDeg} и по
     *  расстоянию между гранями в пределах {@code distTolMm}, причём вдоль стороны они
     *  перекрываются, — возвращает точное положение «встык» ({@link #attachTowerFlush}, зазор 0,
     *  угол выровнен), иначе {@code null}. Из нескольких вариантов берётся ближайший по расстоянию. */
    public static Placement snapFlush(Placement mover, TowerSpec moverSpec, List<SnapTarget> targets,
            double distTolMm, double angTolDeg) {
        Placement best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (SnapTarget target : targets) {
            for (Side ts : Side.values()) {
                for (Side ms : Side.values()) {
                    Placement flush = attachTowerFlush(target.world(), target.spec(), ts, moverSpec, ms, 0, 0);
                    if (Math.abs(normalizeDeg(mover.yawDeg() - flush.yawDeg())) > angTolDeg) {
                        continue;
                    }
                    double[] t = sideInfo(target.spec(), ts);
                    double[] m = sideInfo(moverSpec, ms);
                    Placement rotT = new Placement(0, 0, 0, target.world().yawDeg());
                    double[] n = rotT.toWorld(t[0], t[1]);
                    double[] tan = rotT.toWorld(t[1], -t[0]);
                    double[] mid = target.world().toWorld(t[2], t[3]);
                    // середина стороны перемещаемой башни при ТЕКУЩЕМ положении, но выровненном угле
                    double[] mm = new Placement(0, mover.anchorX(), mover.anchorZ(), flush.yawDeg())
                            .toWorld(m[2], m[3]);
                    double dx = mm[0] - mid[0];
                    double dz = mm[1] - mid[1];
                    double dist = dx * n[0] + dz * n[1];
                    double along = dx * tan[0] + dz * tan[1];
                    double reach = sideLength(target.spec(), ts) / 2.0 + sideLength(moverSpec, ms) / 2.0;
                    if (Math.abs(dist) > distTolMm || Math.abs(along) > reach) {
                        continue;
                    }
                    if (Math.abs(dist) < bestDist) {
                        bestDist = Math.abs(dist);
                        best = attachTowerFlush(target.world(), target.spec(), ts, moverSpec, ms, along, 0);
                    }
                }
            }
        }
        return best;
    }

    // ---- экраны группы ----

    /** Поза экрана B (в системе группы), примыкающего к экрану A под углом: торцы сходятся в одной
     *  точке лицевой поверхности (угол стыка — как у двух стен с общим ребром). {@code
     *  interiorAngleDeg} — угол МЕЖДУ лицевыми поверхностями экранов, 180° — продолжение в линию,
     *  90° — угол комнаты, 120° — излом на 60°. {@code towardViewer} — экраны загибаются к зрителю
     *  (вогнутая «чаша»), иначе от него (выпуклый угол). {@code toRightEdgeOfA} — B справа от A
     *  (иначе слева). Угол вне {@code (0°, 180°]} зажимается. */
    public static Placement attachScreen(Placement poseA, double widthA, boolean toRightEdgeOfA,
            double widthB, double interiorAngleDeg, boolean towardViewer) {
        double interior = Math.max(1.0, Math.min(180.0, interiorAngleDeg));
        double delta = 180.0 - interior;
        if (toRightEdgeOfA) {
            double yaw = normalizeDeg(poseA.yawDeg() + (towardViewer ? -delta : delta));
            double[] corner = poseA.toWorld(widthA, 0);
            return new Placement(0, corner[0], corner[1], yaw);
        }
        double yaw = normalizeDeg(poseA.yawDeg() + (towardViewer ? delta : -delta));
        double[] corner = poseA.toWorld(0, 0);
        double[] off = new Placement(0, 0, 0, yaw).toWorld(widthB, 0);
        return new Placement(0, corner[0] - off[0], corner[1] - off[1], yaw);
    }

    /** Поза КРАЙНЕЙ точки лицевой поверхности экрана в системе экрана: точка и направление
     *  лицевой поверхности в ней (у прямого экрана — {@code (0, 0, 0°)} слева и {@code (W, 0, 0°)}
     *  справа; у изогнутого — край крайней колонны и её поворот). От неё считается стык экранов. */
    public static Placement screenEdgePose(Screen screen, CabinetType type, boolean right) {
        StructureCurveMath.Curve curve = StructureCurveMath.curveOf(screen, type);
        List<Placement> cols = StructureCurveMath.cabinetColumns(curve);
        if (cols.isEmpty()) {
            return new Placement(0, right ? curve.screenWidthMm() : 0, 0, 0);
        }
        Placement col = cols.get(right ? cols.size() - 1 : 0);
        double[] p = col.toWorld(right ? curve.cabinetWidthMm() / 2.0 : -curve.cabinetWidthMm() / 2.0, 0);
        return new Placement(0, p[0], p[1], col.yawDeg());
    }

    /** Поза экрана-«перемещаемого» (в системе группы), примыкающего краем к краю экрана-опоры под
     *  углом — общий случай {@link #attachScreen}, работает и для изогнутых экранов. {@code refEdgeWorld}
     *  — мировая поза края опоры ({@link #screenEdgePose} ∘ поза опоры), {@code moverEdgeLocal} — поза
     *  того края перемещаемого экрана, которым он стыкуется (левого, если он справа от опоры; иначе
     *  правого), в ЕГО системе. Смысл {@code interiorAngleDeg}/{@code towardViewer} — как у
     *  {@link #attachScreen}. */
    public static Placement attachScreenByEdges(Placement refEdgeWorld, boolean moverToRight,
            Placement moverEdgeLocal, double interiorAngleDeg, boolean towardViewer) {
        double interior = Math.max(1.0, Math.min(180.0, interiorAngleDeg));
        double delta = 180.0 - interior;
        double turn = moverToRight == towardViewer ? -delta : delta;
        double edgeYaw = normalizeDeg(refEdgeWorld.yawDeg() + turn);
        double yaw = normalizeDeg(edgeYaw - moverEdgeLocal.yawDeg());
        double[] off = new Placement(0, 0, 0, yaw).toWorld(moverEdgeLocal.anchorX(), moverEdgeLocal.anchorZ());
        return new Placement(0, refEdgeWorld.anchorX() - off[0], refEdgeWorld.anchorZ() - off[1], yaw);
    }

    /** Экраны группы ещё не расставлены в плане: их больше одного и у всех поза нулевая — в 3D они
     *  лежали бы друг на друге. */
    public static boolean groupNeedsArranging(List<Screen> screens) {
        if (screens.size() < 2) {
            return false;
        }
        for (Screen s : screens) {
            if (s.getStructurePoseXMm() != 0 || s.getStructurePoseZMm() != 0 || s.getStructurePoseYawDeg() != 0) {
                return false;
            }
        }
        return true;
    }

    // ---- контуры и предупреждения ----

    /** Контуры башен экрана в мировой системе (система группы), по одному списку прямоугольников на
     *  башню; габариты каждой — её собственные ({@link StructureCurveMath#towerSpecOf(Screen,
     *  Workspace, int)}). */
    public static List<List<double[][]>> towerRects(Screen screen, CabinetType type, Workspace workspace) {
        Setup setup = StructureCurveMath.setupOf(screen, type, workspace);
        List<List<double[][]>> out = new ArrayList<>();
        for (int k = 0; k < setup.towerCount(); k++) {
            Placement p = towerPlacementInGroup(screen, setup, k);
            out.add(StructureCurveMath.worldRects(p, StructureCurveMath.towerSpecOf(screen, workspace, k)));
        }
        return out;
    }

    /** В каких колонках экрана есть хотя бы один видимый (не скрытый) кабинет. */
    public static boolean[] visibleColumns(Screen screen, int cols) {
        boolean[] visible = new boolean[Math.max(0, cols)];
        for (CabinetInstance c : screen.getCabinets()) {
            if (!c.isHidden() && c.getColIndex() >= 0 && c.getColIndex() < visible.length) {
                visible[c.getColIndex()] = true;
            }
        }
        return visible;
    }

    /** Контуры колонн кабинетов экрана в мировой системе: прямоугольник {@code [−w/2, w/2] ×
     *  [−глубина, 0]} на каждую колонну, где есть хотя бы один видимый кабинет. */
    public static List<double[][]> screenRects(Screen screen, CabinetType type) {
        List<double[][]> out = new ArrayList<>();
        if (type == null) {
            return out;
        }
        StructureCurveMath.Curve curve = StructureCurveMath.curveOf(screen, type);
        Placement pose = screenPose(screen);
        boolean[] visible = visibleColumns(screen, curve.cols());
        double hw = curve.cabinetWidthMm() / 2.0;
        double d = curve.cabinetDepthMm();
        List<Placement> cols = StructureCurveMath.cabinetColumns(curve);
        double[][] local = {{-hw, 0}, {hw, 0}, {hw, -d}, {-hw, -d}};
        for (int i = 0; i < cols.size(); i++) {
            if (!visible[i]) {
                continue;
            }
            Placement w = compose(pose, cols.get(i));
            double[][] r = new double[4][];
            for (int j = 0; j < 4; j++) {
                r[j] = w.toWorld(local[j][0], local[j][1]);
            }
            out.add(r);
        }
        return out;
    }

    /** Башни «встык»: расстояние между контурами не больше этого, мм. */
    public static final double TIE_CONTACT_TOLERANCE_MM = 15;

    /** Наименьшее расстояние между двумя наборами прямоугольников плана, мм (0 — пересекаются или касаются). */
    public static double minGap(List<double[][]> a, List<double[][]> b) {
        if (StructureCurveMath.anyIntersect(a, b)) {
            return 0;
        }
        double best = Double.POSITIVE_INFINITY;
        for (double[][] ra : a) {
            for (double[][] rb : b) {
                for (int i = 0; i < 4; i++) {
                    for (int j = 0; j < 4; j++) {
                        best = Math.min(best, pointSegment(ra[i], rb[j], rb[(j + 1) % 4]));
                        best = Math.min(best, pointSegment(rb[j], ra[i], ra[(i + 1) % 4]));
                    }
                }
            }
        }
        return best;
    }

    private static double pointSegment(double[] p, double[] a, double[] b) {
        double dx = b[0] - a[0];
        double dz = b[1] - a[1];
        double len2 = dx * dx + dz * dz;
        double t = len2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dz) / len2));
        return Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dz));
    }

    /** Число стыков встык стоящих башен (в пределах одного экрана и между экранами списка), где хотя бы у одного из
     *  двух экранов включена пометка «считать стяжки» ({@link Screen#isStructureCountTies()}). Стык — пара башен с
     *  расстоянием между контурами не больше {@link #TIE_CONTACT_TOLERANCE_MM}. */
    public static int tieContacts(List<Screen> screens, Function<Screen, CabinetType> typeOf, Workspace workspace) {
        return tieStats(screens, typeOf, workspace)[0];
    }

    /** Число стяжек: сумма по стыкам; на стык берётся параметр «стяжек на стык» экрана с пометкой (у двух
     *  помеченных — большее). */
    public static int tieCount(List<Screen> screens, Function<Screen, CabinetType> typeOf, Workspace workspace) {
        return tieStats(screens, typeOf, workspace)[1];
    }

    /** {@code {стыков, стяжек}}. Стык — коробки башен (3D, с учётом наклона и высоты) пересекаются или ближе
     *  {@link #TIE_CONTACT_TOLERANCE_MM}. */
    private static int[] tieStats(List<Screen> screens, Function<Screen, CabinetType> typeOf, Workspace workspace) {
        List<Screen> owners = new ArrayList<>();
        List<StructureOrient.OrientedBox> boxes = new ArrayList<>();
        for (Screen s : screens) {
            if (s.getMountType() != com.vjstb.ledscheme.model.ScreenMountType.STRUCTURE
                    || !StructureCurveMath.separateTowers(s)) {
                continue;
            }
            for (StructureOrient.OrientedBox box : towerBoxes(s, typeOf.apply(s), workspace)) {
                owners.add(s);
                boxes.add(box);
            }
        }
        int contacts = 0;
        int ties = 0;
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                Screen a = owners.get(i);
                Screen b = owners.get(j);
                if (!a.isStructureCountTies() && !b.isStructureCountTies()) {
                    continue;
                }
                if (boxes.get(i).touches(boxes.get(j), TIE_CONTACT_TOLERANCE_MM)) {
                    contacts++;
                    ties += Math.max(a.isStructureCountTies() ? a.getStructureTiesPerContact() : 0,
                            b.isStructureCountTies() ? b.getStructureTiesPerContact() : 0);
                }
            }
        }
        return new int[]{contacts, ties};
    }

    /** Поза башни {@code k} в системе ГРУППЫ: положение в плане (поза экрана ∘ положение башни), высота над
     *  землёй, наклон и крен (у авто-раскладки — нули). */
    public static StructureOrient.TowerPose towerPoseInGroup(Screen screen, Setup setup, int k) {
        Placement g = towerPlacementInGroup(screen, setup, k);
        StructureTowerPlacement p = screen.isStructureManualLayout() ? screen.towerPlacementOf(k) : null;
        return new StructureOrient.TowerPose(g.anchorX(), p != null ? p.getElevationMm() : 0, g.anchorZ(), g.yawDeg(),
                p != null ? p.getPitchDeg() : 0, p != null ? p.getRollDeg() : 0);
    }

    /** 3D-коробки башен экрана (мировая система группы): ширина контура, собранная высота башни, глубина по
     *  стоящим рамам; поза — {@link #towerPoseInGroup}. */
    public static List<StructureOrient.OrientedBox> towerBoxes(Screen screen, CabinetType type, Workspace workspace) {
        Setup setup = StructureCurveMath.setupOf(screen, type, workspace);
        List<StructureOrient.OrientedBox> out = new ArrayList<>();
        for (int k = 0; k < setup.towerCount(); k++) {
            TowerSpec spec = StructureCurveMath.towerSpecOf(screen, workspace, k);
            out.add(StructureOrient.OrientedBox.ofTower(towerPoseInGroup(screen, setup, k), spec.outerWidthMm(),
                    StructureCupMath.towerHeightMm(screen, workspace, k), spec.depthMm()));
        }
        return out;
    }

    /** 3D-коробки колонн кабинетов экрана (мировая система группы): высота — от подъёма экрана на высоту экрана. */
    public static List<StructureOrient.OrientedBox> screenBoxes(Screen screen, CabinetType type) {
        List<StructureOrient.OrientedBox> out = new ArrayList<>();
        if (type == null) {
            return out;
        }
        StructureCurveMath.Curve curve = StructureCurveMath.curveOf(screen, type);
        Placement pose = screenPose(screen);
        boolean[] visible = visibleColumns(screen, curve.cols());
        List<Placement> cols = StructureCurveMath.cabinetColumns(curve);
        double height = screen.getRows() * type.getHeightMm();
        for (int i = 0; i < cols.size(); i++) {
            if (!visible[i]) {
                continue;
            }
            Placement w = compose(pose, cols.get(i));
            out.add(StructureOrient.OrientedBox.ofTower(new StructureOrient.TowerPose(w.anchorX(),
                    screen.getStructureScreenElevationMm(), w.anchorZ(), w.yawDeg(), 0, 0), curve.cabinetWidthMm(),
                    height, curve.cabinetDepthMm()));
        }
        return out;
    }

    /** Предупреждения ручной расстановки ОДНОГО экрана: пересечения башен друг с другом и вход башни в объём
     *  своего экрана — по 3D-коробкам (наклон и высота учитываются: башня, лежащая поверх других, им не мешает).
     *  Пусто, если расстановка не ручная (авто-режим проверяет {@link StructureCurveMath#analyze}). */
    public static List<String> analyzeManual(Screen screen, CabinetType type, Workspace workspace) {
        List<String> warnings = new ArrayList<>();
        if (screen == null || !screen.isStructureManualLayout()) {
            return warnings;
        }
        List<StructureOrient.OrientedBox> towers = towerBoxes(screen, type, workspace);
        List<StructureOrient.OrientedBox> cabinets = screenBoxes(screen, type);
        for (int i = 0; i < towers.size(); i++) {
            for (int j = i + 1; j < towers.size(); j++) {
                if (towers.get(i).overlaps(towers.get(j))) {
                    warnings.add(String.format("Башни %d и %d пересекаются.", i + 1, j + 1));
                }
            }
            if (anyOverlap(towers.get(i), cabinets)) {
                warnings.add(String.format("Башня %d заходит в объём экрана.", i + 1));
            }
        }
        return warnings;
    }

    private static boolean anyOverlap(StructureOrient.OrientedBox box, List<StructureOrient.OrientedBox> others) {
        for (StructureOrient.OrientedBox o : others) {
            if (box.overlaps(o)) {
                return true;
            }
        }
        return false;
    }

    /** Предупреждения конструктива ГРУППЫ экранов: пересечения башен РАЗНЫХ экранов между собой и вход башни в
     *  объём ДРУГОГО экрана группы (внутри одного экрана — {@link #analyzeManual}); 3D-коробки. Экраны друг с
     *  другом не сравниваются: на стыке под углом их корпуса соприкасаются по построению. Только предупреждения. */
    public static List<String> analyzeGroup(List<Screen> screens, Function<Screen, CabinetType> typeOf,
            Workspace workspace) {
        List<String> warnings = new ArrayList<>();
        int n = screens.size();
        List<List<StructureOrient.OrientedBox>> towers = new ArrayList<>();
        List<List<StructureOrient.OrientedBox>> cabinets = new ArrayList<>();
        for (Screen s : screens) {
            CabinetType type = typeOf.apply(s);
            towers.add(towerBoxes(s, type, workspace));
            cabinets.add(screenBoxes(s, type));
        }
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                if (a == b) {
                    continue;
                }
                String nameA = screens.get(a).getName();
                String nameB = screens.get(b).getName();
                for (int i = 0; i < towers.get(a).size(); i++) {
                    if (anyOverlap(towers.get(a).get(i), cabinets.get(b))) {
                        warnings.add(String.format("Башня %d экрана «%s» заходит в объём экрана «%s».", i + 1,
                                nameA, nameB));
                    }
                    if (b > a) {
                        for (int j = 0; j < towers.get(b).size(); j++) {
                            if (towers.get(a).get(i).overlaps(towers.get(b).get(j))) {
                                warnings.add(String.format(
                                        "Башня %d экрана «%s» пересекается с башней %d экрана «%s».",
                                        i + 1, nameA, j + 1, nameB));
                            }
                        }
                    }
                }
            }
        }
        return warnings;
    }
}
