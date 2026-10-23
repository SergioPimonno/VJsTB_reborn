package com.vjstb.ledscheme.ui;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.glu.GLU;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.MaskColorPreset;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructureFrameCell;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.StructureCalc;
import com.vjstb.ledscheme.service.StructureCupMath;
import com.vjstb.ledscheme.service.StructureCurveMath;
import com.vjstb.ledscheme.service.StructureGizmoMath;
import com.vjstb.ledscheme.service.StructureGizmoMath.Handle;
import com.vjstb.ledscheme.service.StructureGizmoMath.RefLine;
import com.vjstb.ledscheme.service.StructureGroupCalc;
import com.vjstb.ledscheme.service.StructureLayoutMath;
import com.vjstb.ledscheme.service.StructurePickMath;
import com.vjstb.ledscheme.service.StructurePickMath.Ray;
import com.vjstb.ledscheme.service.StructurePickMath.Vec3;
import com.vjstb.ledscheme.service.StructurePickMath.YawTransform;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * 3D-превью наземного конструктива (Phase 2.1 — объёмная башня, см. STRUCTURE_CALC_NOTES.md)
 * — рисует реально существующие вертикальные рамы (передний+задний ряд, см.
 * {@link Screen#getStructureFrameCells()}), перемычки между рядами
 * ({@link Screen#getStructurePeremychkaCells()}) и секции базовой рамы
 * ({@link Screen#getStructureBaseFrameCells()}), плюс сам экран как сетку кабинетов для
 * масштаба. Интерактивное поячеечное редактирование прямо здесь кликом мыши — см. javadoc
 * про picking ниже.
 *
 * <p>Выбран JOGL (не LWJGL) специально ради {@link GLJPanel} — рендерит в offscreen-буфер
 * и компонуется как обычный Swing-компонент (живёт в отдельном немодальном
 * {@code ui.Structure3DDialog}, как {@code ui.VideoTimingCalculatorDialog}). LWJGL 3
 * открывает собственное нативное GLFW-окно без штатной Swing-интеграции — не вписывался бы
 * в архитектуру этого приложения без обходных путей.
 *
 * <p>Фиксированный конвейер GL2 ({@code glBegin}/{@code glTranslatef}/матричный стек), не
 * GL3+/шейдеры — здесь рисуются несколько десятков коробок для предпросмотра, не
 * производительная 3D-сцена, ради которой стоило бы городить VBO/шейдеры/ручную математику
 * матриц.
 *
 * <p><b>Только Windows-нативы упакованы в этом заходе</b> (см. pom.xml) — на macOS/Linux
 * конструктор ниже поймает исключение при инициализации GL и покажет понятное сообщение
 * вместо падения всего приложения.
 *
 * <p><b>Объёмная геометрия башни</b> (см. STRUCTURE_CALC_NOTES.md за полным описанием):
 * передний ряд вертикальных рам стоит прямо у экрана, задний ряд примыкает к нему ВПЛОТНУЮ,
 * без зазора (Round 5 — раньше между рядами был зазор с перемычкой в нём, эта роль
 * упразднена). Перемычки (Round 5) соединяют ДВЕ СОСЕДНИЕ БАШНИ В ПРЕДЕЛАХ ОДНОГО ряда (не
 * передний↔задний ряд одной башни) на уровнях, кратных 1.5 высоты выбранной рамы (см.
 * {@link StructureCalc#peremychkaIntervalMm}) — только реально расставленные уровни хранятся,
 * см. {@link Screen#getStructurePeremychkaCells()}. Базовая рама — секция 0 (ядро, под
 * объёмом башни) плюс секции 1+ выноса назад под балласт, каждая глубины sectionDepthMm, с
 * опциональной усилительной рамой (row == 2 в {@link Screen#getStructureFrameCells()}) поверх
 * каждой секции выноса.
 *
 * <p><b>Переопределение типа рамы поячеечно</b> (Phase 2.2, "конструктор с предварительным
 * аппаратно рассчитанным шаблоном" — прямая цитата пользователя): расчёт расставляет НОМИНАЛЬНЫЙ
 * тип рамы везде, но у КАЖДОЙ вертикальной ячейки (ряд 0/1/2) можно переопределить конкретный
 * тип из библиотеки — например, короткая рама на крайней башне, где номинальная не помещается
 * впритык к краю экрана. Какой тип используется для СЛЕДУЮЩЕЙ добавляемой (кликом по призраку)
 * ячейки, выбирается селектором "Рама для добавления" в {@code ui.Structure3DDialog} — см.
 * {@link #setActiveNewCellFrameTypeId}.
 *
 * <p><b>Управление мышью</b>: ЛКМ — только клик (picking), не двигает камеру; ПКМ +
 * перетаскивание — орбита (yaw/pitch); средняя кнопка + перетаскивание — панорамирование
 * цели орбиты; колесо — зум.
 *
 * <p><b>Picking</b>: клик по СУЩЕСТВУЮЩЕМУ элементу прячет его. Добавление — ТОЛЬКО с
 * зажатым Ctrl (Round 17, баг-репорт: без разделения режимов клик по стойке нередко "пробивал"
 * её и добавлял соседний призрак вместо удаления самой стойки, см. javadoc
 * {@link #handleClick}) — без Ctrl призраки не рисуются и не участвуют в тесте луча вообще, с
 * Ctrl показывается ближайший к курсору "призрак" и клик по нему добавляет элемент. Ручной
 * CPU-side raycast, математика — {@link StructurePickMath}. Тестируется не один луч через
 * курсор, а небольшой крест точек вокруг него ("толстый курсор" — тонкие стойки в перспективе
 * иначе почти невозможно зацепить мышью).
 *
 * <p><b>Изогнутые экраны и раздельные башни (запрос 2026-10-01)</b>: экран рисуется колоннами
 * повёрнутых кабинетов по дуге ({@link StructureCurveMath#cabinetColumns}), а каждая
 * раздельная башня (пара столбов {@code 2k}/{@code 2k+1}) — жёсткий блок, построенный ТЕМ ЖЕ
 * кодом кандидатов в координатах стены и перенесённый/повёрнутый одним преобразованием
 * {@link YawTransform} ({@link #towerTransform}); picking переводит луч в локальную систему
 * башни (OBB, {@link StructurePickMath#intersectObb}). Между раздельными башнями нет ни
 * перемычек/оснований, ни их призраков, призраков лишнего столба сбоку тоже нет. Прямой экран
 * с зазором 0 идёт прежним путём: преобразование {@code null}, ни одного лишнего вызова GL.
 */
public class Structure3DPanel extends JPanel {

    private static final double DEFAULT_FRAME_HEIGHT_MM = 950;
    private static final double DEFAULT_FRAME_WIDTH_MM = 500;
    private static final double DEFAULT_FRAME_DEPTH_MM = 51;
    /** Гарантированный зазор (мм) между задней гранью экрана и передним рядом конструктива —
     *  см. javadoc {@code computeGeometry}. С 2026-10-01 — общее число с геометрией изогнутых
     *  экранов ({@link StructureCurveMath#SCREEN_CLEARANCE_MM}), значение не менялось. */
    private static final double SCREEN_CLEARANCE_MM = StructureCurveMath.SCREEN_CLEARANCE_MM;
    private static final java.awt.Color CABINET_CHASSIS_COLOR = new java.awt.Color(0x1c, 0x1e, 0x22);
    private static final java.awt.Color CUP_COLOR = new java.awt.Color(0xc7, 0x8a, 0x2e);
    private static final double FOV_DEG = 45;
    private static final int CLICK_MOVE_THRESHOLD_PX = 4;
    private static final int[] PICK_TOLERANCE_OFFSETS = {0, -5, 5, -10, 10};

    private final AppModel model;
    // yaw=-35 смотрел почти прямо в лицевую грань экрана -- сам конструктив (позади экрана)
    // был почти целиком закрыт непрозрачным экраном. yaw=-65 сразу даёт вид на 3/4.
    private double yawDeg = -65;
    private double pitchDeg = 22;
    private double distanceMm = 10_000;
    private double panXMm;
    private double panYMm;
    private double panZMm;
    private int lastMouseX;
    private int lastMouseY;
    private int pressX;
    private int pressY;
    private int viewportW = 1;
    private int viewportH = 1;
    private GLJPanel gljPanel;
    private volatile PickKey hoveredGhostKey;
    /** Тип рамы (id {@code StructureFrameType}, {@code null} = номинальный тип экрана), с
     *  которым будет создана СЛЕДУЮЩАЯ добавляемая кликом по призраку ячейка — см.
     *  {@link #setActiveNewCellFrameTypeId}. */
    private String activeNewCellFrameTypeId;

    public Structure3DPanel(AppModel model) {
        this(model, true);
    }

    /** {@code createGl == false} — без GL-канваса (только геометрия кандидатов и picking):
     *  для тестов picking'а повёрнутых башен (2026-10-01), которые должны идти и в headless-
     *  среде, где JOGL не инициализируется. */
    Structure3DPanel(AppModel model, boolean createGl) {
        super(new BorderLayout());
        this.model = model;
        setPreferredSize(new Dimension(700, 560));
        if (!createGl) {
            return;
        }
        try {
            GLProfile profile = GLProfile.get(GLProfile.GL2);
            GLCapabilities caps = new GLCapabilities(profile);
            caps.setDepthBits(24);
            gljPanel = new GLJPanel(caps);
            gljPanel.addGLEventListener(new Renderer());
            wireMouseControls(gljPanel);
            add(gljPanel, BorderLayout.CENTER);
        } catch (Throwable t) {
            add(unavailableLabel(t), BorderLayout.CENTER);
        }
    }

    /** Вызывается из {@code ui.Structure3DDialog} при выборе в селекторе "Рама для добавления" —
     *  {@code null} значит "как обычно" (номинальный тип экрана для этого ряда/усилительный
     *  дефолт для выноса). Не требует repaint — влияет только на СЛЕДУЮЩИЙ клик по призраку. */
    public void setActiveNewCellFrameTypeId(String frameTypeId) {
        this.activeNewCellFrameTypeId = frameTypeId;
    }

    /** Стандартный CAD-вид ("Спереди"/"Сбоку"/"Сзади"/"Сверху" в {@code ui.Structure3DDialog})
     *  — задаёт направление взгляда, дистанция/панорамирование не трогаются (масштаб/цель
     *  остаются как есть, меняется только угол). {@code pitchDeg} около ±90 (вид сверху/снизу)
     *  намеренно не доходит до самого полюса — на 90° "верх" камеры вырождается (gimbal lock
     *  в формуле {@code gluLookAt(..., 0, 1, 0)}). */
    public void setViewAngle(double yawDeg, double pitchDeg) {
        this.yawDeg = yawDeg;
        this.pitchDeg = Math.max(-89, Math.min(89, pitchDeg));
        if (gljPanel != null) {
            gljPanel.repaint();
        }
    }

    /** Внешний хук перерисовки (см. {@code Structure3DDialog#refresh}) — эта панель НЕ
     *  подписывается на {@code AppModel.addListener} сама (у {@code AppModel} нет
     *  {@code removeListener}, а новая панель создаётся при каждом открытии диалога, так что
     *  подписка бы утекала) — вызывающий код (см. {@code SetupStagePanel#calculateStructure})
     *  явно дёргает этот метод после пересчёта конструктива, если диалог уже открыт, чтобы
     *  показанная модель не расходилась с только что сохранёнными значениями. С 2026-10-01
     *  {@code Structure3DDialog} сам подписан на модель (появился {@code removeListener}) и зовёт
     *  этот метод после любого изменения — в т.ч. от ползунков радиуса/зазора/числа башен. */
    public void refresh() {
        if (gljPanel != null) {
            gljPanel.repaint();
        }
    }

    private static JLabel unavailableLabel(Throwable t) {
        JLabel msg = new JLabel("<html><center>3D недоступно на этой системе<br>("
                + t.getClass().getSimpleName() + ")</center></html>", SwingConstants.CENTER);
        msg.setForeground(Palette.MUTED);
        return msg;
    }

    private void wireMouseControls(GLJPanel gljPanel) {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMouseX = e.getX();
                lastMouseY = e.getY();
                pressX = e.getX();
                pressY = e.getY();
                if (layoutMode && SwingUtilities.isLeftMouseButton(e)) {
                    layoutPress(pixelRay(e.getX(), e.getY()));
                    gljPanel.repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - lastMouseX;
                int dy = e.getY() - lastMouseY;
                lastMouseX = e.getX();
                lastMouseY = e.getY();
                if (SwingUtilities.isRightMouseButton(e)) {
                    yawDeg -= dx * 0.4;
                    pitchDeg = Math.max(-85, Math.min(85, pitchDeg + dy * 0.4));
                    gljPanel.repaint();
                } else if (SwingUtilities.isMiddleMouseButton(e)) {
                    pan(dx, dy);
                    gljPanel.repaint();
                } else if (SwingUtilities.isLeftMouseButton(e) && dragKind != DragKind.NONE) {
                    // Shift -- линии привязки и прилипание (к башням, экрану, углам); без него -- свободно
                    layoutDrag(pixelRay(e.getX(), e.getY()), e.isShiftDown());
                    gljPanel.repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (layoutMode) {
                    layoutHover(pixelRay(e.getX(), e.getY()));
                }
                updateHover(e.getX(), e.getY(), e.isControlDown());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    int movedR = Math.abs(e.getX() - pressX) + Math.abs(e.getY() - pressY);
                    if (movedR <= CLICK_MOVE_THRESHOLD_PX) {
                        handleRightClick(e.getX(), e.getY(), e.getX(), e.getY());
                    }
                    return;
                }
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                if (dragKind != DragKind.NONE) {
                    layoutRelease();
                    gljPanel.repaint();
                    return;
                }
                if (layoutMode) {
                    return; // в режиме «Раскладка» клик по рамам не переключает их
                }
                int moved = Math.abs(e.getX() - pressX) + Math.abs(e.getY() - pressY);
                if (moved <= CLICK_MOVE_THRESHOLD_PX) {
                    handleClick(e.getX(), e.getY(), e.isControlDown());
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoveredGhostKey != null) {
                    hoveredGhostKey = null;
                    gljPanel.repaint();
                }
            }
        };
        gljPanel.addMouseListener(mouse);
        gljPanel.addMouseMotionListener(mouse);
        gljPanel.addMouseWheelListener(e -> {
            distanceMm *= Math.pow(1.1, e.getWheelRotation());
            distanceMm = Math.max(800, Math.min(60_000, distanceMm));
            gljPanel.repaint();
        });
    }

    /** Панорамирование средней кнопкой -- сдвигает цель орбиты по её текущим "право"/"вверх"
     *  (право всегда лежит в горизонтальной плоскости земли по построению — cross с мировым
     *  Y, поэтому пан по X И Z одновременно, не только X — иначе на нулевом yaw панорамирование
     *  "вбок" работало бы, а на любом другом угле было бы перекошено). Вертикаль (dy) двигает
     *  цель строго по мировому Y (не по "верху" камеры — тот наклоняется вместе с pitch). */
    private void pan(int dxPx, int dyPx) {
        CameraState cam = computeCamera();
        Vec3 forward = cam.center().minus(cam.eye()).normalized();
        Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalized();
        // «Верх» камеры (экранная вертикаль в мировых координатах): в виде сверху это ось Z плана, в виде спереди
        // — высота. Раньше вертикаль двигала цель строго по мировому Y, и в виде сверху перетаскивание вверх-вниз
        // меняло высоту вместо глубины (баг-репорт 2026-10-22).
        Vec3 camUp = right.cross(forward).normalized();
        if (Math.abs(pitchDeg) >= 75) {
            // вид почти сверху/снизу: двигаемся строго ВДОЛЬ ПОЛА (X и Z), высота не меняется; «верх» экрана —
            // горизонтальное направление взгляда
            double fh = Math.hypot(forward.x(), forward.z());
            if (fh > 1e-6) {
                camUp = new Vec3(forward.x() / fh, 0, forward.z() / fh);
            }
        }
        double scale = distanceMm * 0.0016;
        panXMm += -dxPx * right.x() * scale + dyPx * camUp.x() * scale;
        panYMm += -dxPx * right.y() * scale + dyPx * camUp.y() * scale;
        panZMm += -dxPx * right.z() * scale + dyPx * camUp.z() * scale;
    }

    /** Текущее смещение цели орбиты (для тестов): {@code {x, y, z}}. */
    double[] panOffset() {
        return new double[]{panXMm, panYMm, panZMm};
    }

    /** Панорамирование на {@code dx}/{@code dy} пикселей — тот же путь, что у средней кнопки (для тестов). */
    void panBy(int dxPx, int dyPx) {
        pan(dxPx, dyPx);
    }

    // ---- камера/геометрия, общие для рендера и picking'а ----

    private record CameraState(Vec3 eye, Vec3 center) {
    }

    /** Экраны, которые рисует редактор: вся группа текущего экрана, если конструктив группы возможен
     *  (все экраны на конструктиве, их больше одного), иначе один текущий экран. */
    List<Screen> viewScreens() {
        return StructureGroupCalc.viewScreens(model.getCurrentScene(), model.getCurrentScreen());
    }

    /** Габариты группы в плане: {@code {minX, maxX, minZ, maxZ}} по контурам кабинетов всех экранов
     *  (мировая система группы). Для пустого списка -- область вокруг начала координат. */
    private double[] groupBounds(List<Screen> screens) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Screen s : screens) {
            CabinetType t = model.typeOf(s);
            for (double[][] r : StructureLayoutMath.screenRects(s, t)) {
                for (double[] v : r) {
                    minX = Math.min(minX, v[0]);
                    maxX = Math.max(maxX, v[0]);
                    minZ = Math.min(minZ, v[1]);
                    maxZ = Math.max(maxZ, v[1]);
                }
            }
        }
        if (minX > maxX) {
            return new double[]{0, 4000, -100, 0};
        }
        return new double[]{minX, maxX, minZ, maxZ};
    }

    /** Ключ группы, под которую расстояние камеры уже подобрано -- при смене группы (или выходе из
     *  неё) камера отъезжает ровно один раз, а не перебивает зум пользователя на каждом кадре. */
    private String fittedGroupKey = "";

    private void fitDistanceToGroup(List<Screen> group, double[] bounds) {
        StringBuilder key = new StringBuilder();
        for (Screen s : group) {
            key.append(s.getId()).append(';');
        }
        if (!key.toString().equals(fittedGroupKey)) {
            fittedGroupKey = key.toString();
            double extent = Math.max(bounds[1] - bounds[0], bounds[3] - bounds[2]);
            distanceMm = Math.max(10_000, Math.min(60_000, extent * 1.6));
        }
    }

    private CameraState computeCamera() {
        Screen screen = model.getCurrentScreen();
        CabinetType type = model.typeOf(screen);
        double widthMm = screen != null && type != null ? screen.getCols() * type.getWidthMm() : 4000;
        double heightMm = screen != null && type != null ? screen.getRows() * type.getHeightMm() : 2000;
        double elevationMm = screen != null ? screen.getStructureScreenElevationMm() : 0;
        double centerX = widthMm / 2.0 + panXMm;
        double centerY = elevationMm + heightMm / 2.0 + panYMm;
        double centerZ = panZMm;
        List<Screen> group = viewScreens();
        if (group.size() > 1) {
            // группа: цель орбиты -- середина плана группы, а не середина одного экрана
            double[] b = groupBounds(group);
            centerX = (b[0] + b[1]) / 2.0 + panXMm;
            centerZ = (b[2] + b[3]) / 2.0 + panZMm;
            fitDistanceToGroup(group, b);
        }
        double yawRad = Math.toRadians(yawDeg);
        double pitchRad = Math.toRadians(pitchDeg);
        double eyeX = centerX + distanceMm * Math.cos(pitchRad) * Math.sin(yawRad);
        double eyeY = centerY + distanceMm * Math.sin(pitchRad);
        double eyeZ = centerZ + distanceMm * Math.cos(pitchRad) * Math.cos(yawRad);
        return new CameraState(new Vec3(eyeX, eyeY, eyeZ), new Vec3(centerX, centerY, centerZ));
    }

    /** {@code curveSetup} — раздельные башни изогнутого экрана / прямого с зазором (запрос
     *  2026-10-01, {@link StructureCurveMath}); {@code null} — прежняя «стена», все кандидаты
     *  строятся и рисуются ровно как до изгиба (единичное преобразование). */
    private record StructureGeometry(double frameH, double frameW, double frameD, double sectionDepthMm,
            double baseThickness, double peremychkaThickness, double spacing,
            double frontZ, double backZ, double peremychkaIntervalMm, double reinforcementHeightMm,
            StructureCurveMath.Setup curveSetup, Screen screen, java.util.function.IntToDoubleFunction towerSpacing) {

        boolean separate() {
            return curveSetup != null;
        }
    }

    /** X столба {@code post} в координатах «стены». Стена — {@code post·шаг}. У раздельной башни {@code k}
     *  столбы стоят симметрично относительно центра башни {@code (2k+0.5)·шаг} на её собственном шаге —
     *  у «узкой» башни (короткие горизонтальные рамы, 2026-10-22) он меньше стандартного, центр и
     *  преобразование башни не меняются. */
    private static double postX(StructureGeometry g, int post) {
        if (!g.separate()) {
            return post * g.spacing();
        }
        int k = Math.floorDiv(post, 2);
        double half = g.towerSpacing().applyAsDouble(k) / 2.0;
        double center = (2 * k + 0.5) * g.spacing();
        return Math.floorMod(post, 2) == 0 ? center - half : center + half;
    }

    /** Общий слой «локальная система башни» (запрос 2026-10-01): все кандидаты по-прежнему
     *  строятся в координатах СТЕНЫ (столб {@code p} на {@code x = p·spacing}, передний ряд от
     *  {@code z = −SCREEN_CLEARANCE_MM}), а для раздельной башни {@code k} (столбы {@code 2k},
     *  {@code 2k+1}) это преобразование переносит и поворачивает её целиком на своё место на
     *  дуге ({@link StructureCurveMath#placement}). В режиме стены — {@code null}: рендер и
     *  picking идут прежним путём без единой лишней операции. */
    private static YawTransform towerTransform(StructureGeometry g, int userTower) {
        if (!g.separate()) {
            return wallTransform(g);
        }
        // Положение башни в системе ГРУППЫ: поза экрана ∘ (ручное положение башни или расчётное по
        // форме экрана) — см. StructureLayoutMath. У одиночного экрана поза нулевая, и числа те же,
        // что до группового режима.
        StructureCurveMath.Placement p = StructureLayoutMath.towerPlacementInGroup(g.screen(), g.curveSetup(),
                userTower);
        return new YawTransform(p.anchorX(), p.anchorZ(), p.yawDeg(), (2 * userTower + 0.5) * g.spacing(),
                -SCREEN_CLEARANCE_MM);
    }

    /** «Стена» (общие столбы) стоит в системе экрана; сдвигается и поворачивается только позой
     *  экрана в группе. Нулевая поза — {@code null}: ни одного лишнего вызова GL, как раньше. */
    private static YawTransform wallTransform(StructureGeometry g) {
        StructureCurveMath.Placement pose = StructureLayoutMath.screenPose(g.screen());
        if (pose.anchorX() == 0 && pose.anchorZ() == 0 && pose.yawDeg() == 0) {
            return null;
        }
        return new YawTransform(pose.anchorX(), pose.anchorZ(), pose.yawDeg(), 0, 0);
    }

    /** Преобразование для СТОЛБА {@code post} (раздельная башня = пара столбов). */
    private static YawTransform postTransform(StructureGeometry g, int post) {
        return g.separate() ? towerTransform(g, Math.floorDiv(post, 2)) : wallTransform(g);
    }

    private StructureGeometry computeGeometry(Screen screen) {
        Workspace ws = model.getWorkspace();
        StructureFrameType frameType = ws.structureFrameTypeById(screen.getStructureFrameTypeId());
        double frameH = dim(frameType != null ? frameType.getHeightMm() : null, DEFAULT_FRAME_HEIGHT_MM);
        double frameW = dim(frameType != null ? frameType.getWidthMm() : null, DEFAULT_FRAME_WIDTH_MM);
        double frameD = dim(frameType != null ? frameType.getDepthMm() : null, DEFAULT_FRAME_DEPTH_MM);
        // Round 16 (баг-репорт: "чаще всего перемычка делается из обычной рамы... уберём
        // короткие рамы, переделываем всё под обычные") -- отдельный "короткий" тип рамы
        // (structureShortFrameTypeId) упразднён целиком. Перемычка/база/усилительные рамы
        // выноса теперь берут габариты из ТОГО ЖЕ frameType, что и вертикальные рамы башни:
        // sectionDepthMm/reinforcementHeightMm = широкая/высокая грань рамы (frameW/frameH,
        // те же величины, что уже посчитаны выше), baseThickness/peremychkaThickness = узкая
        // грань (frameD, толщина рельса) -- физически перемычка/база РЕАЛЬНО чаще всего
        // делаются из обычной рамы, отдельного каталожного типа для них не требуется. Если
        // инженеру на месте нужна именно короткая рама где-то конкретно -- это по-прежнему
        // доступно per-cell через селектор "Рама для добавления" в Structure3DDialog (см.
        // {@code StructureFrameCell#getFrameTypeId()}), просто больше не встроено в номинальный
        // расчёт всей сетки.
        double sectionDepthMm = frameW;
        double baseThickness = frameD;
        double peremychkaThickness = frameD;
        double reinforcementHeightMm = frameH;
        // Round 17 -- "Шаг башен" упразднён как отдельный редактируемый параметр экрана (не
        // влиял ни на что реальное), заменён фиксированной константой StructureCalc
        // #DEFAULT_TOWER_SPACING_MM.
        double spacing = StructureCalc.DEFAULT_TOWER_SPACING_MM;
        // Зазор до экрана -- камера по умолчанию стоит со стороны ПОЛОЖИТЕЛЬНОГО Z и смотрит
        // на центр сцены, то есть большее Z означает БЛИЖЕ к зрителю -- конструктив на
        // ОТРИЦАТЕЛЬНОМ Z, дальше от зрителя, чем лицевая грань экрана (z = 0; корпус кабинетов
        // уходит назад на их глубину из библиотеки, см. drawScreenCabinets).
        // Round 3 (узкая грань рамы к зрителю, см. drawTowerSegment): широкая грань рамы
        // (frameW, ~500мм) теперь уходит В ГЛУБИНУ вдоль Z, поэтому зазор считается от неё,
        // а не от узкой frameD (~51мм), как было раньше -- иначе ближний край рамы вылезал бы
        // за пределы предполагаемого зазора.
        double frontZ = -(SCREEN_CLEARANCE_MM + frameW / 2.0);
        // Round 5 (баг-репорт с фото/эскизом реальной башни: "разные ряды вертикальных рам
        // примыкают друг к другу вплотную") -- передний и задний ряд ТЕПЕРЬ КАСАЮТСЯ БЕЗ
        // ЗАЗОРА (перемычка больше не заполняет зазор между ними -- она соединяет СОСЕДНИЕ
        // БАШНИ в пределах ОДНОГО ряда, см. StructurePeremychkaCell class-javadoc). backZ -
        // frontZ = -frameW ровно закрывает зазор: дальняя грань переднего (frontZ-frameW/2)
        // совпадает с ближней гранью заднего (backZ+frameW/2).
        double backZ = frontZ - frameW;
        double peremychkaIntervalMm = StructureCalc.peremychkaIntervalMm(frameH);
        StructureCurveMath.Setup curveSetup = StructureCurveMath.separateTowers(screen)
                ? StructureCurveMath.setupOf(screen, model.typeOf(screen), ws) : null;
        return new StructureGeometry(frameH, frameW, frameD, sectionDepthMm, baseThickness,
                peremychkaThickness, spacing, frontZ, backZ, peremychkaIntervalMm, reinforcementHeightMm,
                curveSetup, screen, k -> StructureLayoutMath.towerPostSpacingMm(screen, ws, k));
    }

    private static double dim(Double value, double fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    /** Диапазон номинальной сетки РАСШИРЕННЫЙ на один шаг с каждой стороны по каждой из осей,
     *  чтобы всегда было видно, куда навести курсор для "добавить лишнюю башню сбоку"/
     *  "добавить сегмент повыше"/"добавить перемычку выше"/"добавить секцию выноса дальше".
     *  Round 5: {@code maxFrontSegment}/{@code maxBackSegment} разделены — задний ряд (row=1)
     *  теперь короткий и независим от переднего (row=0), см. {@code ScreenLogic
     *  #regenerateStructureCells}. */
    private record FrameEnvelope(int minTower, int maxTower, int maxFrontSegment, int maxBackSegment, int maxLevel,
            int maxSection) {
    }

    private FrameEnvelope frameEnvelope(Screen screen, StructureGeometry g) {
        int towerCount = Math.max(0, screen.getStructureTowerCount());
        int verticalPerTower = Math.max(0, screen.getStructureVerticalFramesPerTower());
        int backRowSegments = Math.max(0, screen.getStructureBackRowSegments());
        int peremychkaLevels = Math.max(0, screen.getStructurePeremychkaLevels());
        // Round 19 -- "ядро" базовой рамы -- фиксированная константа StructureCalc
        // #CORE_BASE_SECTION_COUNT (1 модуль), больше не зависит от габаритов рамы.
        int coreSections = StructureCalc.CORE_BASE_SECTION_COUNT;
        int baseSections = coreSections + Math.max(0, screen.getStructureExtendedBaseSections());
        int minTower = 0;
        int maxTower = towerCount - 1;
        int maxFrontSeg = verticalPerTower - 1;
        int maxBackSeg = backRowSegments - 1;
        for (StructureFrameCell c : screen.getStructureFrameCells()) {
            minTower = Math.min(minTower, c.getTowerIndex());
            maxTower = Math.max(maxTower, c.getTowerIndex());
            if (c.getRow() == 0) {
                maxFrontSeg = Math.max(maxFrontSeg, c.getSegmentIndex());
            } else if (c.getRow() == 1) {
                maxBackSeg = Math.max(maxBackSeg, c.getSegmentIndex());
            }
        }
        int maxLevel = peremychkaLevels - 1;
        for (var c : screen.getStructurePeremychkaCells()) {
            maxLevel = Math.max(maxLevel, c.getLevelIndex());
        }
        int maxSection = baseSections - 1;
        for (var c : screen.getStructureBaseFrameCells()) {
            maxSection = Math.max(maxSection, c.getSectionIndex());
        }
        for (StructureFrameCell c : screen.getStructureFrameCells()) {
            if (c.getRow() == 2) {
                maxSection = Math.max(maxSection, c.getSegmentIndex());
            }
        }
        if (g.separate()) {
            // Раздельные башни (2026-10-01): «призраков» лишнего столба сбоку нет -- столб вне
            // пары не образует башню, а между башнями соединений нет по правилу пользователя.
            // Диапазон столбов -- целыми парами (башнями); число башен задаётся в форме.
            int lo = Math.floorDiv(Math.min(0, minTower), 2) * 2;
            int hi = Math.floorDiv(Math.max(maxTower, 2 * g.curveSetup().towerCount() - 1), 2) * 2 + 1;
            return new FrameEnvelope(lo, hi, maxFrontSeg + 1, maxBackSeg + 1, maxLevel + 1, maxSection + 1);
        }
        return new FrameEnvelope(minTower - 1, maxTower + 1, maxFrontSeg + 1, maxBackSeg + 1, maxLevel + 1,
                maxSection + 1);
    }

    private record PickKey(String kind, int a, int b, int c) {
    }

    /** {@code bounds} — {x,y,z,w,h,d}, номинальный прямоугольник ячейки (контур для
     *  "призраков"). {@code pickBoxes} — чем ИМЕННО тестируется луч (для существующих
     *  вертикальных сегментов — две тонкие коробки стоек, иначе совпадает с {@code bounds}).
     *  {@code xf} — локальная система раздельной башни ({@link #towerTransform}): {@code bounds}/
     *  {@code pickBoxes} заданы в координатах стены, рисуются под этим преобразованием, а луч
     *  picking'а переводится в них ({@link StructurePickMath#intersectObb}); {@code null} — стена. */
    private record Candidate(PickKey key, double[] bounds, List<double[]> pickBoxes, boolean exists,
            Runnable toggle, YawTransform xf) {
    }

    /** Реальные габариты рамы для конкретной ЯЧЕЙКИ — если у неё задано переопределение типа
     *  ({@link StructureFrameCell#getFrameTypeId()}), берём габариты ИЗ БИБЛИОТЕКИ, иначе
     *  номинальные {@code g}/усилительный дефолт для выноса. {@code cell == null} — это призрак
     *  (позиция ещё не существует) — в этом случае показываем габариты ТИПА, который будет
     *  использован, если по призраку кликнуть прямо сейчас ({@link #activeNewCellFrameTypeId}),
     *  чтобы контур призрака уже отражал выбор в селекторе. */
    private double[] resolveFrameDims(StructureFrameCell cell, StructureGeometry g, boolean reinforcement) {
        String overrideId = cell != null ? cell.getFrameTypeId() : activeNewCellFrameTypeId;
        StructureFrameType override = overrideId != null
                ? model.getWorkspace().structureFrameTypeById(overrideId) : null;
        double fallbackH = reinforcement ? g.reinforcementHeightMm() : g.frameH();
        if (override == null) {
            return new double[]{g.frameW(), fallbackH, g.frameD()};
        }
        return new double[]{dim(override.getWidthMm(), g.frameW()), dim(override.getHeightMm(), fallbackH),
                dim(override.getDepthMm(), g.frameD())};
    }

    /** Реальные габариты стойки КОНКРЕТНОЙ башни/ряда — по первому переопределению типа рамы
     *  среди уже существующих (не hidden) ячеек этой башни+ряда (типичный случай — вся башня/
     *  ряд собрана из одного типа, см. {@link #activeNewCellFrameTypeId}), иначе номинальный
     *  тип экрана ({@code g}). Баг-репорт: "изменение типа рамы не влияет на перемычки, а
     *  должно, башня может быть уже за счёт коротких рам" — {@link #resolveFrameDims} уже
     *  учитывает per-cell override ПРИ ОТРИСОВКЕ самой стойки, но {@link #peremychkaCandidates}/
     *  {@link #baseCandidates} использовали только номинальные {@code g.frameD()}/{@code
     *  g.frameW()}, поэтому зазор между башнями не сужался/расширялся вслед за реальной
     *  переопределённой шириной стойки. Этот метод — та же логика {@code resolveFrameDims}, но
     *  по факту существующих ячеек башни, без ветки "призрака". */
    private double[] resolveTowerPostDims(Screen screen, StructureGeometry g, int towerIndex, int row) {
        String overrideId = screen.getStructureFrameCells().stream()
                .filter(c -> c.getTowerIndex() == towerIndex && c.getRow() == row && !c.isHidden()
                        && c.getFrameTypeId() != null)
                .map(StructureFrameCell::getFrameTypeId)
                .findFirst().orElse(null);
        StructureFrameType override = overrideId != null
                ? model.getWorkspace().structureFrameTypeById(overrideId) : null;
        if (override == null) {
            return new double[]{g.frameW(), g.frameH(), g.frameD()};
        }
        return new double[]{dim(override.getWidthMm(), g.frameW()), dim(override.getHeightMm(), g.frameH()),
                dim(override.getDepthMm(), g.frameD())};
    }

    /** Толщина рельса рамы (для отрисовки И для приблизительной позиции стаканов) — не
     *  больше 45% от "длинной" грани, чтобы у узких/некорректно заполненных типов из
     *  библиотеки рельсы не перекрывали друг друга и не схлопывались в сплошной "столбик"
     *  (баг-репорт: в библиотеке пользователя у "рамы" оказалось widthMm=50 вместо 500). */
    private static double railThickness(double spanW) {
        return Math.min(Math.max(20, Math.min(70, spanW * 0.12)), spanW * 0.45);
    }

    /** {@code bounds}/{@code pickBoxes} для вертикальной рамы — {@code w}=толщина по X
     *  (frameD, узкая грань смотрит на зрителя, round 3), {@code d}=длина по Z (frameW,
     *  широкая грань уходит в глубину башни, там же — перекладины, видны ИЗНУТРИ башни
     *  тому, кто стоит между передним и задним рядом, как у настоящей лестничной рамы). */
    private List<Candidate> frameCandidates(Screen screen, StructureGeometry g) {
        FrameEnvelope env = frameEnvelope(screen, g);
        List<StructureFrameCell> cells = screen.getStructureFrameCells();
        List<Candidate> result = new ArrayList<>();
        for (int t = env.minTower(); t <= env.maxTower(); t++) {
            for (int row = 0; row < 2; row++) {
                double zCenter = row == 0 ? g.frontZ() : g.backZ();
                int maxSeg = row == 0 ? env.maxFrontSegment() : env.maxBackSegment();
                for (int seg = 0; seg <= maxSeg; seg++) {
                    final int ft = t;
                    final int frow = row;
                    final int fs = seg;
                    StructureFrameCell existingCell = cells.stream()
                            .filter(c -> c.matches(ft, frow, fs) && !c.isHidden()).findFirst().orElse(null);
                    boolean exists = existingCell != null;
                    double[] dims = resolveFrameDims(existingCell, g, false);
                    double x = postX(g, t) - dims[2] / 2.0;
                    // реальный низ рамы: сумма высот рам ниже + стаканы на стыках (StructureCupMath)
                    double y = StructureCupMath.frameBottomY(screen, model.getWorkspace(), ft, frow, fs, g.frameH());
                    double z = zCenter - dims[0] / 2.0;
                    double[] bounds = {x, y, z, dims[2], dims[1], dims[0]};
                    double rail = railThickness(dims[0]);
                    List<double[]> pickBoxes = exists
                            ? List.of(new double[]{x, y, z, dims[2], dims[1], rail},
                                    new double[]{x, y, z + dims[0] - rail, dims[2], dims[1], rail})
                            : List.of(bounds);
                    String newCellType = activeNewCellFrameTypeId;
                    result.add(new Candidate(new PickKey("frame", ft, frow, fs), bounds, pickBoxes, exists,
                            () -> model.toggleStructureFrameCell(screen, ft, frow, fs, newCellType),
                            postTransform(g, ft)));
                }
            }
        }
        return result;
    }

    /** Усилительные рамы выноса (row == 2 в {@link Screen#getStructureFrameCells()}, Phase 2.2
     *  — "1 метровая рама на секцию, вкл/выкл как всё остальное") — по одной на КАЖДУЮ секцию
     *  выноса (ядро, {@code [0, CORE_BASE_SECTION_COUNT)}, там не нужна — уже есть
     *  полноразмерный ряд 0/1, см. {@code StructureCalc#CORE_BASE_SECTION_COUNT}), центрирована
     *  по глубине секции. */
    private List<Candidate> reinforcementCandidates(Screen screen, StructureGeometry g) {
        FrameEnvelope env = frameEnvelope(screen, g);
        List<StructureFrameCell> cells = screen.getStructureFrameCells();
        List<Candidate> result = new ArrayList<>();
        // Round 25 -- усилительная рама только над НАСТОЯЩИМ выносом, не над секцией, на
        // которой уже стоит задний ряд (см. StructureCalc#baseFootprintSectionCount).
        int coreSections = StructureCalc.baseFootprintSectionCount(screen.getStructureBackRowSegments());
        for (int t = env.minTower(); t <= env.maxTower(); t++) {
            for (int section = coreSections; section <= env.maxSection(); section++) {
                final int ft = t;
                final int fsec = section;
                StructureFrameCell existingCell = cells.stream()
                        .filter(c -> c.matches(ft, 2, fsec) && !c.isHidden()).findFirst().orElse(null);
                boolean exists = existingCell != null;
                double[] dims = resolveFrameDims(existingCell, g, true);
                double x = postX(g, t) - dims[2] / 2.0;
                double[] zr = baseSectionZRange(g, section);
                double sectionCenterZ = (zr[0] + zr[1]) / 2.0;
                double z = sectionCenterZ - dims[0] / 2.0;
                double[] bounds = {x, 0, z, dims[2], dims[1], dims[0]};
                // Баг-репорт: "определение того, на что нацелен курсор считается криво" --
                // усилительная рама рисуется тем же drawTowerSegment, что и вертикальная (полая
                // "8"-форма, рельсы у ближнего/дальнего Z-края, между ними видно насквозь), но
                // pickBox тут был ВСЕЙ сплошной bounds-коробкой -- клик по пустому месту между
                // рельсами засчитывался как попадание. Тот же тесный pickBox по рельсам, что
                // уже был у frameCandidates (см. её javadoc), просто раньше не применили сюда.
                double rail = railThickness(dims[0]);
                List<double[]> pickBoxes = exists
                        ? List.of(new double[]{x, 0, z, dims[2], dims[1], rail},
                                new double[]{x, 0, z + dims[0] - rail, dims[2], dims[1], rail})
                        : List.of(bounds);
                String newCellType = activeNewCellFrameTypeId;
                result.add(new Candidate(new PickKey("reinforcement", ft, fsec, 0), bounds, pickBoxes, exists,
                        () -> model.toggleStructureFrameCell(screen, ft, 2, fsec, newCellType),
                        postTransform(g, ft)));
            }
        }
        return result;
    }

    /** Перемычка соединяет ДВЕ СОСЕДНИЕ БАШНИ (towerIndex/towerIndex+1) В ПРЕДЕЛАХ ОДНОГО ряда
     *  (Round 5, баг-репорт с обведённым фото реальной башни — "перемычки должны находиться
     *  между вертикальными рамами ОДНОГО ряда, разные ряды примыкают друг к другу вплотную") —
     *  РОВНО заполняет зазор между рамами по X (не залезает ни в одну из них), полной глубины
     *  {@code frameW} по Z (совпадает с footprint-ом ряда, вплотную к обоим постам). Крепится к
     *  перекладине по высоте — {@code y} формула ({@code peremychkaIntervalMm} = 1.5×frameH)
     *  гарантирует попадание ровно на перекладину, см. {@code drawTowerSegment} javadoc.
     *
     * <p>Round 8 (баг-репорт: "перемычка слишком короткая, не соответствует длине даже
     *  короткой рамы") — зазор по X считался как {@code spacing - frameW}, но после Round 3
     *  (узкая грань рамы {@code frameD} смотрит на зрителя, см. {@code frameCandidates})
     *  реальная ширина стойки по X — это {@code frameD} (~51мм), а НЕ {@code frameW} (~500мм,
     *  та грань ушла в глубину вдоль Z ещё в Round 3). Использование {@code frameW} тут
     *  вычитало из зазора на порядок больше, чем реально занимает стойка, отсюда перемычка
     *  получалась вдвое короче настоящего зазора. Все X-величины ниже переведены на
     *  {@code frameD}; Z (глубина/footprint ряда) по-прежнему {@code frameW} — эта ось не
     *  менялась. */
    private List<Candidate> peremychkaCandidates(Screen screen, StructureGeometry g) {
        FrameEnvelope env = frameEnvelope(screen, g);
        var cells = screen.getStructurePeremychkaCells();
        List<Candidate> result = new ArrayList<>();
        for (int gapIdx = env.minTower(); gapIdx < env.maxTower(); gapIdx++) {
            // Раздельные башни (2026-10-01): перемычка только ВНУТРИ башни (2k → 2k+1), в
            // промежутке между башнями -- ни ячеек, ни призраков.
            if (g.separate() && Math.floorMod(gapIdx, 2) != 0) {
                continue;
            }
            for (int row = 0; row < 2; row++) {
                double zCenter = row == 0 ? g.frontZ() : g.backZ();
                // Баг-репорт: "изменение типа рамы не влияет на перемычки, а должно, башня
                // может быть уже за счёт коротких рам" -- зазор/глубина считались от НОМИНАЛЬНОГО
                // типа экрана (g.frameD()/g.frameW()), даже если конкретная стойка соседней башни
                // переопределена per-cell на другой (например, более узкий) тип. Берём реальные
                // габариты СТОЕК этого зазора (resolveTowerPostDims), а не номинал.
                double[] leftDims = resolveTowerPostDims(screen, g, gapIdx, row);
                double[] rightDims = resolveTowerPostDims(screen, g, gapIdx + 1, row);
                double xSpan = postX(g, gapIdx + 1) - postX(g, gapIdx) - leftDims[2] / 2.0 - rightDims[2] / 2.0;
                if (xSpan <= 0) {
                    continue;
                }
                double zDepth = Math.min(leftDims[0], rightDims[0]);
                double rail = railThickness(xSpan);
                // Положение перемычки — ПЕРЕКЛАДИНА вертикальной рамы (запрос 2026-10-22): у каждой рамы стопки
                // три — низ, середина, верх (номер = рама·3 + 0/1/2); поставить можно на любую, у которой
                // есть рама в обоих столбах зазора (две перемычки на соседних перекладинах — как на фото
                // реальной башни). Стоящие перемычки рисуются всегда, призраки — по существующим рамам.
                java.util.TreeSet<Integer> rungs = new java.util.TreeSet<>();
                for (var c : cells) {
                    if (c.getTowerIndex() == gapIdx && c.getRow() == row) {
                        rungs.add(c.getLevelIndex());
                    }
                }
                int maxSeg = row == 0 ? env.maxFrontSegment() : env.maxBackSegment();
                for (int seg = 0; seg <= maxSeg; seg++) {
                    final int fseg = seg;
                    final int left = gapIdx;
                    final int right = gapIdx + 1;
                    final int frr = row;
                    boolean both = screen.getStructureFrameCells().stream().anyMatch(
                            c -> c.matches(left, frr, fseg) && !c.isHidden())
                            && screen.getStructureFrameCells().stream().anyMatch(
                            c -> c.matches(right, frr, fseg) && !c.isHidden());
                    if (both) {
                        for (int r = 0; r < 3; r++) {
                            rungs.add(seg * 3 + r);
                        }
                    }
                }
                double crossT = StructureCupMath.crossbarThicknessMm(g.frameW());
                for (int level : rungs) {
                    final int fg = gapIdx;
                    final int fr = row;
                    final int fl = level;
                    boolean exists = cells.stream().anyMatch(c -> c.matches(fg, fr, fl) && !c.isHidden());
                    double x = postX(g, gapIdx) + leftDims[2] / 2.0;
                    // центр перекладины реальной стопки левого столба зазора (стаканы и короткие рамы сдвигают её)
                    double y = StructureCupMath.rungCenterY(screen, model.getWorkspace(), fg, fr, fl, g.frameH(),
                            crossT) - g.peremychkaThickness() / 2.0;
                    double z = zCenter - zDepth / 2.0;
                    double[] bounds = {x, y, z, xSpan, g.peremychkaThickness(), zDepth};
                    // Баг-репорт: "определение того, на что нацелен курсор считается криво" --
                    // drawGapSpanningLadderFrame рисует полую "8"-форму (рельсы вдоль X у
                    // ближнего/дальнего Z-края, между ними видно насквозь), но pickBox был ВСЕЙ
                    // сплошной bounds-коробкой -- клик в пустоту между рельсами засчитывался.
                    // Тесный pickBox по рельсам (тот же railThickness, что и в самой отрисовке).
                    List<double[]> pickBoxes = exists
                            ? List.of(new double[]{x, y, z, xSpan, g.peremychkaThickness(), rail},
                                    new double[]{x, y, z + zDepth - rail, xSpan, g.peremychkaThickness(), rail})
                            : List.of(bounds);
                    result.add(new Candidate(new PickKey("peremychka", fg, fr, fl), bounds, pickBoxes, exists,
                            () -> model.toggleStructurePeremychkaCell(screen, fg, fr, fl), postTransform(g, fg)));
                }
            }
        }
        return result;
    }

    /** Диапазон Z одной секции опорной рамы — {@code {frontZ, backZ}} (оба отрицательные,
     *  frontZ ближе к экрану). Round 10 (баг-репорт: "эта пластина должна не рендериться как
     *  N рам, а являться N отдельными рамами") — КАЖДАЯ секция, включая "ядро", теперь
     *  ОДИНАКОВОЙ глубины {@code sectionDepthMm} (ширина короткой рамы, реальный каталожный
     *  размер), считая от ближней грани переднего ряда назад — раньше секция 0 была ОДНОЙ
     *  сплошной плитой на всю глубину {@code 2*frameW} под обоими рядами сразу (произвольный
     *  размер, см. историю Round 4/7 в STRUCTURE_CALC_NOTES.md); сколько таких секций реально
     *  покрывает "ядро" до начала выноса под балласт — фиксированная константа {@code
     *  StructureCalc#CORE_BASE_SECTION_COUNT} (Round 19, было раньше вычисляемым числом секций
     *  через удалённый метод {@code coreBaseSectionCount}), вызывающая сторона ({@code
     *  ScreenLogic#regenerateStructureCells} на стороне данных, {@code
     *  reinforcementCandidates}/{@code frameEnvelope} на стороне рендера) знает эту границу
     *  отдельно — сама функция теперь не различает "ядро"/"вынос", это чисто равномерный шаг. */
    private static double[] baseSectionZRange(StructureGeometry g, int section) {
        double coreNearZ = g.frontZ() + g.frameW() / 2.0;
        double frontZ = coreNearZ - section * g.sectionDepthMm();
        return new double[]{frontZ, frontZ - g.sectionDepthMm()};
    }

    /** Round 7 (баг-репорт: "рамы основания должны ставиться точно так же как перемычки, но на
     *  уровне пола, сейчас они центрируются по вертикальным рамам, это неверно") — опорная рама
     *  соединяет ДВЕ СОСЕДНИЕ БАШНИ по X (тот же {@code xSpan}, что у {@link
     *  #peremychkaCandidates}), не центрируется на одной башне. {@code y = -baseThickness}
     *  (уровень пола) вместо высоты перекладины — единственное содержательное отличие от
     *  перемычки. */
    private List<Candidate> baseCandidates(Screen screen, StructureGeometry g) {
        FrameEnvelope env = frameEnvelope(screen, g);
        var cells = screen.getStructureBaseFrameCells();
        List<Candidate> result = new ArrayList<>();
        for (int gapIdx = env.minTower(); gapIdx < env.maxTower(); gapIdx++) {
            if (g.separate() && Math.floorMod(gapIdx, 2) != 0) {
                continue; // основание только внутри раздельной башни, как и перемычка
            }
            // Баг-репорт: "изменение типа рамы не влияет на перемычки/базу, а должно" -- та же
            // причина, что у peremychkaCandidates (см. её javadoc): зазор считался от НОМИНАЛЬНОГО
            // g.frameD(), игнорируя per-cell override реальной стойки. База лежит ПОД обоими
            // рядами сразу, поэтому берём ШИРШУЮ из стоек переднего/заднего ряда каждой башни --
            // так плита гарантированно не перекрывает ни одну реальную стойку, независимо от того,
            // какой ряд переопределён.
            double leftD = Math.max(resolveTowerPostDims(screen, g, gapIdx, 0)[2],
                    resolveTowerPostDims(screen, g, gapIdx, 1)[2]);
            double rightD = Math.max(resolveTowerPostDims(screen, g, gapIdx + 1, 0)[2],
                    resolveTowerPostDims(screen, g, gapIdx + 1, 1)[2]);
            double xSpan = postX(g, gapIdx + 1) - postX(g, gapIdx) - leftD / 2.0 - rightD / 2.0;
            if (xSpan <= 0) {
                continue;
            }
            double rail = railThickness(xSpan);
            for (int section = 0; section <= env.maxSection(); section++) {
                final int fg = gapIdx;
                final int fsec = section;
                boolean exists = cells.stream().anyMatch(c -> c.matches(fg, fsec) && !c.isHidden());
                double x = postX(g, gapIdx) + leftD / 2.0;
                double[] zr = baseSectionZRange(g, section);
                double sectionSpan = zr[0] - zr[1];
                double[] bounds = {x, -g.baseThickness(), zr[1], xSpan, g.baseThickness(), sectionSpan};
                // Баг-репорт: "определение того, на что нацелен курсор считается криво" -- та же
                // причина, что у peremychkaCandidates выше: полая "8"-форма отрисовки, но
                // pickBox был ВСЕЙ сплошной bounds-коробкой. Тесный pickBox по рельсам.
                List<double[]> pickBoxes = exists
                        ? List.of(new double[]{x, -g.baseThickness(), zr[1], xSpan, g.baseThickness(), rail},
                                new double[]{x, -g.baseThickness(), zr[1] + sectionSpan - rail, xSpan,
                                        g.baseThickness(), rail})
                        : List.of(bounds);
                result.add(new Candidate(new PickKey("base", fg, fsec, 0), bounds, pickBoxes, exists,
                        () -> model.toggleStructureBaseFrameSection(screen, fg, fsec), postTransform(g, fg)));
            }
        }
        return result;
    }

    private List<Candidate> allCandidates(Screen screen, StructureGeometry g) {
        List<Candidate> all = new ArrayList<>();
        all.addAll(frameCandidates(screen, g));
        all.addAll(peremychkaCandidates(screen, g));
        all.addAll(baseCandidates(screen, g));
        all.addAll(reinforcementCandidates(screen, g));
        // призраки убранных стаканов (Ctrl+клик возвращает); существующие стаканы — только ПКМ
        for (Candidate c : cupCandidates(screen, g)) {
            if (!c.exists()) {
                all.add(c);
            }
        }
        return all;
    }

    /** Два блока стакана на стыке перед рамой: у ближнего и дальнего рельса (по Z) ряда. */
    private static List<double[]> cupBoxes(StructureGeometry g, int post, int row, double y, double cupH) {
        double cupSize = Math.min(40, g.frameD()) * 1.5;
        double frameRail = railThickness(g.frameW());
        double zCenter = row == 0 ? g.frontZ() : g.backZ();
        double cupX = postX(g, post) - cupSize / 2.0;
        double near = zCenter - g.frameW() / 2.0 + frameRail / 2.0 - cupSize / 2.0;
        double far = zCenter + g.frameW() / 2.0 - frameRail / 2.0 - cupSize / 2.0;
        return List.of(new double[]{cupX, y, near, cupSize, cupH, cupSize},
                new double[]{cupX, y, far, cupSize, cupH, cupSize});
    }

    /** Стаканы 50 мм между вертикальными рамами (запрос 2026-10-22): по стыку на каждую пару смежных
     *  не спрятанных рам столба и ряда. Есть стакан ({@code exists}) — цель ПКМ «убрать»; стакан убран
     *  вручную — призрак для Ctrl+клика «вернуть». При сборке без стаканов на экране — пусто. */
    private List<Candidate> cupCandidates(Screen screen, StructureGeometry g) {
        List<Candidate> result = new ArrayList<>();
        if (screen.isStructureWithoutCups()) {
            return result;
        }
        double cupH = StructureCupMath.cupHeightMm(screen, model.getWorkspace());
        FrameEnvelope env = frameEnvelope(screen, g);
        for (int t = env.minTower(); t <= env.maxTower(); t++) {
            for (int row = 0; row < 2; row++) {
                int maxSeg = row == 0 ? env.maxFrontSegment() : env.maxBackSegment();
                for (int seg = 1; seg <= maxSeg; seg++) {
                    final int ft = t;
                    final int frow = row;
                    final int fs = seg;
                    boolean upper = screen.getStructureFrameCells().stream()
                            .anyMatch(c -> c.matches(ft, frow, fs) && !c.isHidden());
                    boolean lower = screen.getStructureFrameCells().stream()
                            .anyMatch(c -> c.matches(ft, frow, fs - 1) && !c.isHidden());
                    if (!upper || !lower) {
                        continue;
                    }
                    boolean exists = StructureCupMath.jointHasCup(screen, t, row, seg);
                    double y = StructureCupMath.frameBottomY(screen, model.getWorkspace(), t, row, seg, g.frameH())
                            - cupH;
                    List<double[]> boxes = cupBoxes(g, t, row, y, cupH);
                    double[] near = boxes.get(0);
                    double[] far = boxes.get(1);
                    double[] bounds = {near[0], y, near[2], near[3], cupH, far[2] + far[5] - near[2]};
                    result.add(new Candidate(new PickKey("cup", ft, frow, fs), bounds,
                            exists ? boxes : List.of(bounds), exists,
                            () -> model.toggleStructureCup(screen, ft, frow, fs), postTransform(g, ft)));
                }
            }
        }
        return result;
    }

    // ---- picking ----

    private Ray pixelRay(int pixelX, int pixelY) {
        CameraState cam = computeCamera();
        double panelW = Math.max(1, gljPanel.getWidth());
        double panelH = Math.max(1, gljPanel.getHeight());
        double aspect = panelW / panelH;
        return StructurePickMath.cameraRay(cam.eye(), cam.center(), new Vec3(0, 1, 0), FOV_DEG, aspect,
                pixelX, pixelY, panelW, panelH);
    }

    private Candidate raycastAtCursor(int pixelX, int pixelY, Screen screen, StructureGeometry g,
            Boolean existsFilter) {
        return raycastIn(allCandidates(screen, g), pixelX, pixelY, existsFilter);
    }

    private Candidate raycastIn(List<Candidate> candidates, int pixelX, int pixelY, Boolean existsFilter) {
        Candidate best = null;
        double bestT = Double.POSITIVE_INFINITY;
        for (int ox : PICK_TOLERANCE_OFFSETS) {
            for (int oy : PICK_TOLERANCE_OFFSETS) {
                Ray ray = pixelRay(pixelX + ox, pixelY + oy);
                for (Candidate c : candidates) {
                    if (existsFilter != null && c.exists() != existsFilter) {
                        continue;
                    }
                    for (double[] b : c.pickBoxes()) {
                        // OBB (2026-10-01): у раздельной башни луч переводится в её локальную
                        // систему; у стены xf == null -- ровно прежний intersectAabb.
                        Double t = StructurePickMath.intersectObb(ray, new Vec3(b[0], b[1], b[2]),
                                new Vec3(b[0] + b[3], b[1] + b[4], b[2] + b[5]), c.xf());
                        if (t != null && t < bestT) {
                            bestT = t;
                            best = c;
                        }
                    }
                }
            }
        }
        return best;
    }

    // ---- тестовые хуки picking'а (2026-10-01): без GL, на текущем экране модели ----

    private static String keyString(PickKey k) {
        return k.kind() + ":" + k.a() + ":" + k.b() + ":" + k.c();
    }

    /** Ключи всех кандидатов текущего экрана ({@code kind:a:b:c}), только существующие или
     *  только призраки. */
    List<String> candidateKeys(boolean existing) {
        Screen screen = model.getCurrentScreen();
        StructureGeometry g = computeGeometry(screen);
        List<String> keys = new ArrayList<>();
        for (Candidate c : allCandidates(screen, g)) {
            if (c.exists() == existing) {
                keys.add(keyString(c.key()));
            }
        }
        return keys;
    }

    /** Мировой центр первой pick-коробки кандидата {@code key} (через его локальную систему). */
    double[] pickBoxWorldCenter(String key) {
        Screen screen = model.getCurrentScreen();
        StructureGeometry g = computeGeometry(screen);
        for (Candidate c : allCandidates(screen, g)) {
            if (keyString(c.key()).equals(key)) {
                double[] b = c.pickBoxes().get(0);
                Vec3 local = new Vec3(b[0] + b[3] / 2.0, b[1] + b[4] / 2.0, b[2] + b[5] / 2.0);
                Vec3 w = c.xf() != null ? c.xf().toWorld(local) : local;
                return new double[]{w.x(), w.y(), w.z()};
            }
        }
        return null;
    }

    /** Ближайший по лучу кандидат — тот же тест, что у клика ({@link #raycastAtCursor}), но для
     *  готового луча. {@code null} — промах. */
    String pickKeyAlongRay(Ray ray, boolean existing) {
        Screen screen = model.getCurrentScreen();
        StructureGeometry g = computeGeometry(screen);
        Candidate best = null;
        double bestT = Double.POSITIVE_INFINITY;
        for (Candidate c : allCandidates(screen, g)) {
            if (c.exists() != existing) {
                continue;
            }
            for (double[] b : c.pickBoxes()) {
                Double t = StructurePickMath.intersectObb(ray, new Vec3(b[0], b[1], b[2]),
                        new Vec3(b[0] + b[3], b[1] + b[4], b[2] + b[5]), c.xf());
                if (t != null && t < bestT) {
                    bestT = t;
                    best = c;
                }
            }
        }
        return best != null ? keyString(best.key()) : null;
    }

    /** Round 17 (баг-репорт: "рейтрейсинг от курсора — вместо удаления рам часто добавляются
     *  новые") — раньше клик ({@code existsFilter=null}) искал ближайшего по лучу кандидата
     *  СРЕДИ ОБОИХ множеств сразу: у "призрака" {@code pickBoxes} — это весь номинальный объём
     *  ячейки (крупная коробка), у существующей стойки — узкие коробки самих стоек (см. javadoc
     *  {@link Candidate}), поэтому объёмный призрак соседней пустой позиции нередко оказывался
     *  БЛИЖЕ по лучу, чем тонкая стойка реальной рамы прямо под курсором, и клик добавлял новую
     *  раму вместо удаления имеющейся. Теперь добавление — ТОЛЬКО с зажатым Ctrl (см.
     *  {@code e.isControlDown()} у вызывающей стороны): без Ctrl в тест луча вообще не попадают
     *  кандидаты-призраки ({@code existsFilter=true}, удаление), с Ctrl — только они
     *  ({@code existsFilter=false}, добавление). Простой клик по существующей раме больше не
     *  может "промахнуться" в невидимый призрак позади/вокруг нее. */
    /** ПКМ по стакану (запрос 2026-10-22: «бывает что собираем без них — ПКМ по стакану, убрать, и
     *  пересчитываем башню»): меню «Убрать стакан» (рамы выше опустятся на высоту стакана, высота башни и
     *  ведомость пересчитываются) и «Собирать без стаканов» (все стаканы экрана; число рам подбирается
     *  заново). Не стакан под курсором — ничего. */
    private void handleRightClick(int pixelX, int pixelY, int menuX, int menuY) {
        Screen screen = model.getCurrentScreen();
        if (screen == null || screen.getMountType() != ScreenMountType.STRUCTURE) {
            return;
        }
        StructureGeometry g = computeGeometry(screen);
        Candidate cup = raycastIn(cupCandidates(screen, g), pixelX, pixelY, true);
        if (cup == null) {
            return;
        }
        double cupH = StructureCupMath.cupHeightMm(screen, model.getWorkspace());
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem remove = new javax.swing.JMenuItem(
                String.format("Убрать стакан (рамы выше опустятся на %.0f мм)", cupH));
        remove.addActionListener(ev -> {
            cup.toggle().run();
            gljPanel.repaint();
        });
        menu.add(remove);
        javax.swing.JMenuItem all = new javax.swing.JMenuItem("Собирать без стаканов (убрать все на экране)");
        all.addActionListener(ev -> {
            model.setStructureCupsEnabled(screen, false);
            gljPanel.repaint();
        });
        menu.add(all);
        menu.show(gljPanel, menuX, menuY);
    }

    /** Кандидаты-стаканы текущего экрана для тестов ({@code kind:a:b:c}, только существующие или
     *  только призраки). */
    List<String> cupKeys(boolean existing) {
        Screen screen = model.getCurrentScreen();
        List<String> keys = new ArrayList<>();
        for (Candidate c : cupCandidates(screen, computeGeometry(screen))) {
            if (c.exists() == existing) {
                keys.add(keyString(c.key()));
            }
        }
        return keys;
    }

    /** Ближайший по лучу существующий стакан ({@code cup:post:row:seg}) или {@code null}. */
    String pickCupAlongRay(Ray ray) {
        Screen screen = model.getCurrentScreen();
        Candidate best = null;
        double bestT = Double.POSITIVE_INFINITY;
        for (Candidate c : cupCandidates(screen, computeGeometry(screen))) {
            if (!c.exists()) {
                continue;
            }
            for (double[] b : c.pickBoxes()) {
                Double t = StructurePickMath.intersectObb(ray, new Vec3(b[0], b[1], b[2]),
                        new Vec3(b[0] + b[3], b[1] + b[4], b[2] + b[5]), c.xf());
                if (t != null && t < bestT) {
                    bestT = t;
                    best = c;
                }
            }
        }
        return best != null ? keyString(best.key()) : null;
    }

    private void handleClick(int pixelX, int pixelY, boolean addMode) {
        Screen screen = model.getCurrentScreen();
        if (screen == null || screen.getMountType() != ScreenMountType.STRUCTURE) {
            return;
        }
        StructureGeometry g = computeGeometry(screen);
        Candidate best = raycastAtCursor(pixelX, pixelY, screen, g, !addMode);
        if (best != null) {
            best.toggle().run();
            hoveredGhostKey = null;
            gljPanel.repaint();
        }
    }

    /** Без зажатого Ctrl призраки не только не кликабельны (см. {@link #handleClick}), но и не
     *  подсвечиваются при наведении — тест луча на них вообще не выполняется, поэтому в режиме
     *  "только удаление" в сцене нет никаких контуров, обещающих добавление, которого не будет. */
    private void updateHover(int pixelX, int pixelY, boolean addMode) {
        Screen screen = model.getCurrentScreen();
        if (layoutMode || !addMode || screen == null || screen.getMountType() != ScreenMountType.STRUCTURE) {
            if (hoveredGhostKey != null) {
                hoveredGhostKey = null;
                gljPanel.repaint();
            }
            return;
        }
        StructureGeometry g = computeGeometry(screen);
        Candidate best = raycastAtCursor(pixelX, pixelY, screen, g, false);
        PickKey newKey = best != null ? best.key() : null;
        if (!Objects.equals(newKey, hoveredGhostKey)) {
            hoveredGhostKey = newKey;
            gljPanel.repaint();
        }
    }

    // ---- режим «Раскладка» (2026-10-22): выбор и перетаскивание башен и экранов ----

    /** Прилипание «встык» при перетаскивании башни: расстояние между гранями, мм, и допуск по
     *  углу, градусы ({@link StructureLayoutMath#snapFlush}). Alt — без прилипания. */
    private static final double SNAP_DIST_MM = 150;
    private static final double SNAP_ANGLE_DEG = 7;
    /** Шаг угла при вращении мышью (Shift + перетаскивание); Alt — свободный угол. */
    private static final double ROTATE_STEP_DEG = 15;

    /** Режим «Раскладка»: ЛКМ выбирает башню или экран и тащит его по земле (Shift — вращает),
     *  клики по рамам не переключают их; режим «Рамы» (по умолчанию) — как раньше. */
    private boolean layoutMode;
    private int selectedTower = -1;
    private String selectionScreenId;
    private Runnable selectionListener;

    private enum DragKind { NONE, TOWER, SCREEN }

    private DragKind dragKind = DragKind.NONE;
    private Screen dragScreen;
    private int dragTower;
    private double dragPlaneY;
    private double[] dragStartGround;
    private StructureCurveMath.Placement dragStart;
    private double dragCenterX;
    private double dragCenterZ;
    private StructureCurveMath.Placement dragLast;
    private boolean dragGestureStarted;
    /** Ручка гизмо, за которую тащат ({@link Handle#CENTER} — тело башни/экрана), и ручка под курсором. */
    private Handle dragHandle = Handle.NONE;
    private Handle hoverHandle = Handle.NONE;
    /** Shift при перетаскивании: показаны линии привязки, {@code activeGuides} — сработавшие. */
    private boolean guidesOn;
    private List<RefLine> activeGuides = new ArrayList<>();

    public void setLayoutMode(boolean on) {
        layoutMode = on;
        hoveredGhostKey = null;
        refresh();
    }

    public boolean isLayoutMode() {
        return layoutMode;
    }

    /** Номер выбранной башни ТЕКУЩЕГО экрана (ручная расстановка) или -1. */
    public int selectedTower() {
        Screen s = model.getCurrentScreen();
        if (s == null || !s.getId().equals(selectionScreenId) || !s.isStructureManualLayout()
                || s.towerPlacementOf(selectedTower) == null) {
            return -1;
        }
        return selectedTower;
    }

    /** Выбирает башню текущего экрана ({@code -1} — снять выбор); панель параметров зовёт это при
     *  выборе в списке башен. */
    public void selectTower(int tower) {
        Screen s = model.getCurrentScreen();
        selectedTower = tower;
        selectionScreenId = s != null ? s.getId() : null;
        if (selectionListener != null) {
            selectionListener.run();
        }
        refresh();
    }

    /** Слушатель смены выбора башни (клик в 3D) — для синхронизации списка в панели параметров. */
    public void setSelectionListener(Runnable listener) {
        this.selectionListener = listener;
    }

    private record LayoutHit(Screen screen, int tower, double t) {
    }

    /** Ближайший по лучу объект режима «Раскладка»: башня текущего экрана (если расстановка
     *  ручная) или экран группы — что видно ближе, то и выбирается. {@code null} — промах. */
    LayoutHit layoutHitAlongRay(Ray ray) {
        Screen active = model.getCurrentScreen();
        if (active == null) {
            return null;
        }
        LayoutHit best = null;
        if (active.getMountType() == ScreenMountType.STRUCTURE && active.isStructureManualLayout()) {
            StructureGeometry g = computeGeometry(active);
            for (Candidate c : allCandidates(active, g)) {
                if (!c.exists()) {
                    continue;
                }
                for (double[] b : c.pickBoxes()) {
                    Double t = StructurePickMath.intersectObb(ray, new Vec3(b[0], b[1], b[2]),
                            new Vec3(b[0] + b[3], b[1] + b[4], b[2] + b[5]), c.xf());
                    if (t != null && (best == null || t < best.t())) {
                        best = new LayoutHit(active, Math.floorDiv(c.key().a(), 2), t);
                    }
                }
            }
        }
        for (Screen s : viewScreens()) {
            Double t = screenHitT(ray, s);
            if (t != null && (best == null || t < best.t())) {
                best = new LayoutHit(s, -1, t);
            }
        }
        return best;
    }

    private Double screenHitT(Ray ray, Screen s) {
        CabinetType type = model.typeOf(s);
        if (type == null) {
            return null;
        }
        StructureCurveMath.Curve curve = StructureCurveMath.curveOf(s, type);
        List<StructureCurveMath.Placement> cols = StructureCurveMath.cabinetColumns(curve);
        boolean[] visible = StructureLayoutMath.visibleColumns(s, curve.cols());
        double hw = curve.cabinetWidthMm() / 2.0;
        double elev = s.getStructureScreenElevationMm();
        double h = s.getRows() * type.getHeightMm();
        StructureCurveMath.Placement pose = StructureLayoutMath.screenPose(s);
        Double best = null;
        for (int i = 0; i < cols.size(); i++) {
            if (!visible[i]) {
                continue;
            }
            StructureCurveMath.Placement w = StructureLayoutMath.compose(pose, cols.get(i));
            Double t = StructurePickMath.intersectObb(ray, new Vec3(-hw, elev, -curve.cabinetDepthMm()),
                    new Vec3(hw, elev + h, 0), new YawTransform(w.anchorX(), w.anchorZ(), w.yawDeg(), 0, 0));
            if (t != null && (best == null || t < best)) {
                best = t;
            }
        }
        return best;
    }

    /** Нажатие ЛКМ в режиме «Раскладка»: выбирает башню/экран под лучом и, если попали, начинает жест
     *  перетаскивания. Экран, не бывший текущим, становится текущим (выбор в дереве «Сетапа» следует
     *  за кликом в 3D). Промах — снимает выбор башни. */
    boolean layoutPress(Ray ray) {
        dragKind = DragKind.NONE;
        dragGestureStarted = false;
        dragLast = null;
        Screen active = model.getCurrentScreen();
        if (!layoutMode || active == null) {
            return false;
        }
        dragHandle = Handle.NONE;
        int selected = selectedTower();
        if (selected >= 0 && active.getMountType() == ScreenMountType.STRUCTURE) {
            // гизмо выбранной башни лежит на земле: ручка под лучом — центр, ось X/Z или кольцо вращения
            double[] g0 = StructureLayoutMath.rayGroundPoint(ray, 0);
            if (g0 != null) {
                StructureCurveMath.Setup gSetup = StructureCurveMath.setupOf(active, model.typeOf(active),
                        model.getWorkspace());
                StructureCurveMath.Placement gw = StructureLayoutMath.towerPlacementInGroup(active, gSetup, selected);
                StructureCurveMath.TowerSpec gSpec = StructureCurveMath.towerSpecOf(active, model.getWorkspace(),
                        selected);
                Handle h = StructureGizmoMath.hit(gw, gSpec, g0[0], g0[1]);
                if (h != Handle.NONE) {
                    dragPlaneY = 0;
                    dragStartGround = g0;
                    dragStart = gw;
                    double[] cl = StructureGizmoMath.centerLocal(gSpec);
                    dragCenterX = cl[0];
                    dragCenterZ = cl[1];
                    dragScreen = active;
                    dragTower = selected;
                    dragKind = DragKind.TOWER;
                    dragHandle = h;
                    return true;
                }
            }
        }
        // тело, попавшее под луч, важнее кольца (что видно ближе, то и берётся): кольцо — только по «пустому» месту
        if (selected < 0 && viewScreens().size() > 1 && model.typeOf(active) != null && layoutHitAlongRay(ray) == null) {
            // кольцо вращения ЭКРАНА группы (запрос 2026-10-22): большой круг на земле вокруг центра экрана
            double[] g0 = StructureLayoutMath.rayGroundPoint(ray, 0);
            if (g0 != null) {
                StructureCurveMath.Curve curve = StructureCurveMath.curveOf(active, model.typeOf(active));
                StructureCurveMath.Placement pose = StructureLayoutMath.screenPose(active);
                double cx = curve.screenWidthMm() / 2.0;
                double cz = -curve.cabinetDepthMm() / 2.0;
                double[] c = pose.toWorld(cx, cz);
                double r = Math.hypot(g0[0] - c[0], g0[1] - c[1]);
                if (Math.abs(r - screenRingRadius(active)) <= SCREEN_RING_GRAB_MM) {
                    dragPlaneY = 0;
                    dragStartGround = g0;
                    dragStart = pose;
                    dragCenterX = cx;
                    dragCenterZ = cz;
                    dragScreen = active;
                    dragKind = DragKind.SCREEN;
                    dragHandle = Handle.RING;
                    return true;
                }
            }
        }
        LayoutHit hit = layoutHitAlongRay(ray);
        if (hit == null) {
            if (selectedTower() >= 0) {
                selectTower(-1);
            }
            return false;
        }
        Vec3 point = ray.origin().plus(ray.direction().scale(hit.t()));
        dragPlaneY = point.y();
        dragStartGround = new double[]{point.x(), point.z()};
        if (hit.tower() >= 0) {
            selectTower(hit.tower());
            CabinetType type = model.typeOf(active);
            StructureCurveMath.Setup setup = StructureCurveMath.setupOf(active, type, model.getWorkspace());
            dragStart = StructureLayoutMath.towerPlacementInGroup(active, setup, hit.tower());
            StructureCurveMath.TowerSpec spec = StructureCurveMath.towerSpecOf(active, model.getWorkspace(),
                    hit.tower());
            dragCenterX = 0;
            dragCenterZ = -spec.depthMm() / 2.0;
            dragScreen = active;
            dragTower = hit.tower();
            dragKind = DragKind.TOWER;
            dragHandle = Handle.CENTER;
            return true;
        }
        Screen target = hit.screen();
        if (target != active) {
            model.selectScreen(target);
        }
        selectTower(-1);
        if (viewScreens().size() > 1) {
            CabinetType type = model.typeOf(target);
            StructureCurveMath.Curve curve = StructureCurveMath.curveOf(target, type);
            dragStart = StructureLayoutMath.screenPose(target);
            dragCenterX = curve.screenWidthMm() / 2.0;
            dragCenterZ = -curve.cabinetDepthMm() / 2.0;
            dragScreen = target;
            dragKind = DragKind.SCREEN;
            dragHandle = Handle.CENTER;
            return true;
        }
        return false;
    }

    /** Движение мыши с зажатой ЛКМ. Что делает — определяет ручка гизмо ({@link #layoutPress}): тело башни
     *  и жёлтый центр — свободный перенос по земле (на высоте, где взяли), красная/синяя ось — перенос строго
     *  вдоль оси башни, оранжевое КОЛЬЦО — вращение вокруг центра контура. {@code snap} (зажатый Shift) —
     *  линии привязки: кромки и осевые линии башни прилипают к параллельным линиям других башен и экрана,
     *  башня встаёт «встык», угол при вращении — к направлениям линий и шагу {@link #ROTATE_STEP_DEG};
     *  сработавшие линии рисуются ярко. Без Shift всё свободно. Весь жест — одна запись отмены. */
    void layoutDrag(Ray ray, boolean snap) {
        if (dragKind == DragKind.NONE) {
            return;
        }
        double[] g = StructureLayoutMath.rayGroundPoint(ray, dragPlaneY);
        if (g == null) {
            return;
        }
        guidesOn = snap && (dragKind == DragKind.TOWER || dragHandle == Handle.RING);
        activeGuides = new ArrayList<>();
        StructureCurveMath.Placement next;
        if (dragHandle == Handle.RING) {
            double[] c = dragStart.toWorld(dragCenterX, dragCenterZ);
            double v0x = dragStartGround[0] - c[0];
            double v0z = dragStartGround[1] - c[1];
            double v1x = g[0] - c[0];
            double v1z = g[1] - c[1];
            if (Math.hypot(v0x, v0z) < 50 || Math.hypot(v1x, v1z) < 50) {
                return;
            }
            double turn = Math.toDegrees(Math.atan2(v1z, v1x) - Math.atan2(v0z, v0x));
            next = StructureLayoutMath.rotateAbout(dragStart, dragCenterX, dragCenterZ, dragStart.yawDeg() - turn);
            if (snap) {
                StructureGizmoMath.Snap sn = StructureGizmoMath.snapAngle(next, dragCenterX, dragCenterZ,
                        referenceLines(), SNAP_ANGLE_DEG, ROTATE_STEP_DEG);
                next = sn.placement();
                activeGuides = sn.active();
            }
        } else {
            double[] d = StructureGizmoMath.constrain(dragStart, dragHandle, g[0] - dragStartGround[0],
                    g[1] - dragStartGround[1]);
            next = new StructureCurveMath.Placement(dragStart.index(), dragStart.anchorX() + d[0],
                    dragStart.anchorZ() + d[1], dragStart.yawDeg());
            if (snap && dragKind == DragKind.TOWER) {
                StructureCurveMath.TowerSpec spec = StructureCurveMath.towerSpecOf(dragScreen, model.getWorkspace(),
                        dragTower);
                StructureCurveMath.Placement flush = dragHandle == Handle.CENTER
                        ? StructureLayoutMath.snapFlush(next, spec, otherTowerTargets(), SNAP_DIST_MM, SNAP_ANGLE_DEG)
                        : null;
                if (flush != null) {
                    next = flush;
                } else {
                    StructureGizmoMath.Snap sn = StructureGizmoMath.snapMove(next, spec, referenceLines(),
                            SNAP_DIST_MM);
                    // вдоль оси привязка не должна уводить башню с оси: берём только составляющую вдоль неё
                    double[] shift = StructureGizmoMath.constrain(dragStart, dragHandle,
                            sn.placement().anchorX() - next.anchorX(), sn.placement().anchorZ() - next.anchorZ());
                    next = new StructureCurveMath.Placement(next.index(), next.anchorX() + shift[0],
                            next.anchorZ() + shift[1], next.yawDeg());
                    activeGuides = sn.active();
                }
            }
        }
        applyDrag(next, false);
    }

    /** Радиус кольца вращения экрана группы и допуск захвата, мм. */
    private static final double SCREEN_RING_GRAB_MM = 220;

    private double screenRingRadius(Screen s) {
        StructureCurveMath.Curve curve = StructureCurveMath.curveOf(s, model.typeOf(s));
        return Math.hypot(curve.screenWidthMm() / 2.0, curve.cabinetDepthMm() / 2.0) + 350;
    }

    /** Есть ли у текущего экрана кольцо вращения (для тестов): экран группы и не выбрана башня. */
    double[] screenRingCentre() {
        Screen s = model.getCurrentScreen();
        if (s == null || selectedTower() >= 0 || viewScreens().size() <= 1 || model.typeOf(s) == null) {
            return null;
        }
        StructureCurveMath.Curve curve = StructureCurveMath.curveOf(s, model.typeOf(s));
        return StructureLayoutMath.screenPose(s).toWorld(curve.screenWidthMm() / 2.0, -curve.cabinetDepthMm() / 2.0);
    }

    double screenRingRadiusForTest() {
        return screenRingRadius(model.getCurrentScreen());
    }

    /** Ручка гизмо выбранной башни под лучом (подсветка при наведении); {@code NONE} — нет выбора/промах. */
    Handle layoutHover(Ray ray) {
        Handle h = Handle.NONE;
        Screen active = model.getCurrentScreen();
        int sel = selectedTower();
        if (layoutMode && dragKind == DragKind.NONE && sel >= 0 && active != null) {
            double[] g0 = StructureLayoutMath.rayGroundPoint(ray, 0);
            if (g0 != null) {
                StructureCurveMath.Setup setup = StructureCurveMath.setupOf(active, model.typeOf(active),
                        model.getWorkspace());
                h = StructureGizmoMath.hit(StructureLayoutMath.towerPlacementInGroup(active, setup, sel),
                        StructureCurveMath.towerSpecOf(active, model.getWorkspace(), sel), g0[0], g0[1]);
            }
        }
        if (h != hoverHandle) {
            hoverHandle = h;
            refresh();
        }
        return h;
    }

    Handle hoverHandle() {
        return hoverHandle;
    }

    Handle dragHandle() {
        return dragHandle;
    }

    /** Сработавшие линии привязки текущего жеста (для тестов и отрисовки). */
    List<RefLine> activeGuides() {
        return activeGuides;
    }

    /** Опорные линии привязки: кромки и осевые линии остальных башен группы, плоскости лица/тыла и торцы
     *  плоских экранов (изогнутые экраны не дают линий). */
    private List<RefLine> referenceLines() {
        List<RefLine> lines = new ArrayList<>();
        for (Screen s : viewScreens()) {
            if (dragKind == DragKind.SCREEN && s == dragScreen) {
                continue; // вращаемый экран не привязывается сам к себе
            }
            CabinetType t = model.typeOf(s);
            if (t != null && s.getStructureCurveType() == com.vjstb.ledscheme.model.ScreenCurveType.FLAT) {
                StructureCurveMath.Curve curve = StructureCurveMath.curveOf(s, t);
                lines.addAll(StructureGizmoMath.flatScreenLines(StructureLayoutMath.screenPose(s),
                        curve.screenWidthMm(), curve.cabinetDepthMm()));
            }
            if (s.getMountType() == ScreenMountType.STRUCTURE && StructureCurveMath.separateTowers(s)) {
                StructureCurveMath.Setup setup = StructureCurveMath.setupOf(s, t, model.getWorkspace());
                for (int k = 0; k < setup.towerCount(); k++) {
                    if (s == dragScreen && k == dragTower && dragKind == DragKind.TOWER) {
                        continue;
                    }
                    lines.addAll(StructureGizmoMath.towerLines(StructureLayoutMath.towerPlacementInGroup(s, setup, k),
                            StructureCurveMath.towerSpecOf(s, model.getWorkspace(), k)));
                }
            }
        }
        return lines;
    }

    /** Конец жеста: последнее положение фиксируется (файл пишется, жест замыкается). */
    void layoutRelease() {
        if (dragKind != DragKind.NONE && dragGestureStarted && dragLast != null) {
            applyDrag(dragLast, true);
        }
        dragKind = DragKind.NONE;
        dragHandle = Handle.NONE;
        dragLast = null;
        dragGestureStarted = false;
        guidesOn = false;
        activeGuides = new ArrayList<>();
    }

    private void applyDrag(StructureCurveMath.Placement world, boolean end) {
        boolean first = !dragGestureStarted;
        if (dragKind == DragKind.TOWER) {
            StructureCurveMath.Placement local = StructureLayoutMath.toFrame(
                    StructureLayoutMath.screenPose(dragScreen), world);
            model.setStructureTowerPlacement(dragScreen, dragTower, local.anchorX(), local.anchorZ(), local.yawDeg(),
                    first, end);
        } else if (dragKind == DragKind.SCREEN) {
            model.setStructureScreenPose(dragScreen, world.anchorX(), world.anchorZ(), world.yawDeg(), first, end);
        }
        dragGestureStarted = true;
        dragLast = world;
    }

    /** Остальные башни группы (всех раздельных/ручных экранов) как цели прилипания «встык». */
    private List<StructureLayoutMath.SnapTarget> otherTowerTargets() {
        List<StructureLayoutMath.SnapTarget> targets = new ArrayList<>();
        for (Screen s : viewScreens()) {
            if (s.getMountType() != ScreenMountType.STRUCTURE || !StructureCurveMath.separateTowers(s)) {
                continue;
            }
            StructureCurveMath.Setup setup = StructureCurveMath.setupOf(s, model.typeOf(s), model.getWorkspace());
            for (int k = 0; k < setup.towerCount(); k++) {
                if (s == dragScreen && k == dragTower) {
                    continue;
                }
                targets.add(new StructureLayoutMath.SnapTarget(StructureLayoutMath.towerPlacementInGroup(s, setup, k),
                        StructureCurveMath.towerSpecOf(s, model.getWorkspace(), k)));
            }
        }
        return targets;
    }

    // ---- тестовые хуки режима «Раскладка» (без GL) ----

    Screen layoutHitScreen(Ray ray) {
        LayoutHit h = layoutHitAlongRay(ray);
        return h != null ? h.screen() : null;
    }

    int layoutHitTower(Ray ray) {
        LayoutHit h = layoutHitAlongRay(ray);
        return h != null ? h.tower() : -1;
    }

    boolean layoutDragging() {
        return dragKind != DragKind.NONE;
    }

    private final class Renderer implements GLEventListener {
        private final GLU glu = new GLU();

        @Override
        public void init(GLAutoDrawable drawable) {
            GL2 gl = drawable.getGL().getGL2();
            gl.glClearColor(0.07f, 0.08f, 0.10f, 1f);
            gl.glEnable(GL2.GL_DEPTH_TEST);
            gl.glEnable(GL2.GL_LIGHTING);
            gl.glEnable(GL2.GL_LIGHT0);
            gl.glEnable(GL2.GL_COLOR_MATERIAL);
            gl.glColorMaterial(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT_AND_DIFFUSE);
            gl.glEnable(GL2.GL_NORMALIZE);
            gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, new float[]{4000f, 9000f, 7000f, 1f}, 0);
            gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_AMBIENT, new float[]{0.35f, 0.35f, 0.38f, 1f}, 0);
            gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, new float[]{0.85f, 0.85f, 0.8f, 1f}, 0);
        }

        @Override
        public void dispose(GLAutoDrawable drawable) {
        }

        @Override
        public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
            GL2 gl = drawable.getGL().getGL2();
            viewportW = Math.max(1, width);
            viewportH = Math.max(1, height);
            gl.glViewport(0, 0, viewportW, viewportH);
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glLoadIdentity();
            double aspect = (double) viewportW / viewportH;
            glu.gluPerspective(FOV_DEG, aspect, 20, 300_000);
            gl.glMatrixMode(GL2.GL_MODELVIEW);
        }

        @Override
        public void display(GLAutoDrawable drawable) {
            GL2 gl = drawable.getGL().getGL2();
            gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
            gl.glMatrixMode(GL2.GL_MODELVIEW);
            gl.glLoadIdentity();

            Screen screen = model.getCurrentScreen();
            CabinetType type = model.typeOf(screen);
            double widthMm = screen != null && type != null ? screen.getCols() * type.getWidthMm() : 4000;
            double heightMm = screen != null && type != null ? screen.getRows() * type.getHeightMm() : 2000;
            double elevationMm = screen != null ? screen.getStructureScreenElevationMm() : 0;
            List<Screen> screens = viewScreens();

            CameraState cam = computeCamera();
            glu.gluLookAt(cam.eye().x(), cam.eye().y(), cam.eye().z(),
                    cam.center().x(), cam.center().y(), cam.center().z(), 0, 1, 0);

            // Земля лежит НИЖЕ опорной рамы (не пересекает её, см. javadoc drawGroundPlane) --
            // если экран/конструктив ещё не выбраны, опорной рамы не будет и толщина не важна.
            double baseThicknessMm = screen != null && screen.getMountType() == ScreenMountType.STRUCTURE
                    ? computeGeometry(screen).baseThickness() : 0;
            double[] bounds = groupBounds(screens);
            double ground = Math.max(Math.max(bounds[1] - bounds[0], bounds[3] - bounds[2]),
                    Math.max(widthMm, heightMm)) * 1.5;
            drawGroundPlane(gl, (bounds[0] + bounds[1]) / 2.0, (bounds[2] + bounds[3]) / 2.0, ground,
                    baseThicknessMm);
            if (screens.isEmpty()) {
                drawScreenPlaneFallback(gl, widthMm, heightMm, elevationMm);
            }
            // Группа экранов (2026-10-22): все экраны группы стоят в общем плане со своей позой
            // (положение + угол), конструктив каждого -- тем же кодом кандидатов; редактируется
            // (призраки, picking) только структура текущего экрана, остальные -- для контекста.
            for (Screen s : screens) {
                CabinetType t = model.typeOf(s);
                if (t != null) {
                    StructureCurveMath.Placement pose = StructureLayoutMath.screenPose(s);
                    gl.glPushMatrix();
                    gl.glTranslated(pose.anchorX(), 0, pose.anchorZ());
                    gl.glRotated(pose.yawDeg(), 0, 1, 0);
                    drawScreenCabinets(gl, s, t, model.getWorkspace(), s.getStructureScreenElevationMm());
                    gl.glPopMatrix();
                } else if (s == screen) {
                    drawScreenPlaneFallback(gl, widthMm, heightMm, elevationMm);
                }
                if (s.getMountType() == ScreenMountType.STRUCTURE) {
                    drawStructure(gl, s, s == screen);
                }
            }
            if (layoutMode) {
                drawLayoutOverlay(gl);
            }
            drawAxisGizmo(gl, cam);
        }

        /** Индикатор осей в углу вида (запрошено пользователем: "чтобы я хотя бы мог сказать
         *  вокруг какой оси тебе надо элементы выстраивать и вращать") — красный=X (право/лево
         *  сцены), зелёный=Y (верх/низ), синий=Z (к экрану/от экрана, "глубина" башни).
         *  Направление совпадает с текущим поворотом камеры (та же yaw/pitch), но БЕЗ её
         *  положения/зума/панорамы — отдельный крохотный вьюпорт в углу с своей проекцией,
         *  восстанавливается сразу после отрисовки.
         *
         * <p><b>Баг-репорт "вид на сцену обрезался"</b>: восстановление вьюпорта раньше брало
         *  {@code gljPanel.getWidth()/getHeight()} — ЛОГИЧЕСКИЕ пиксели (как для луча picking'а,
         *  см. {@link #pixelRay}), а {@code reshape()} настраивает и проекцию, И
         *  {@code viewportW}/{@code viewportH} в ФИЗИЧЕСКИХ пикселях (на HiDPI-экране это разные
         *  числа) — восстановление меньшим логическим размером оставляло реальный вьюпорт GL
         *  урезанным до угла окна. Теперь используются те же {@code viewportW}/{@code viewportH}
         *  поля, что и {@code reshape()}, размер самого индикатора масштабируется тем же
         *  отношением физических/логических пикселей. */
        private void drawAxisGizmo(GL2 gl, CameraState cam) {
            double dpiScale = viewportW / (double) Math.max(1, gljPanel.getWidth());
            int size = (int) Math.round(84 * dpiScale);
            int margin = (int) Math.round(10 * dpiScale);
            gl.glViewport(margin, viewportH - size - margin, size, size);
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPushMatrix();
            gl.glLoadIdentity();
            glu.gluPerspective(35, 1.0, 0.1, 10);
            gl.glMatrixMode(GL2.GL_MODELVIEW);
            gl.glPushMatrix();
            gl.glLoadIdentity();
            Vec3 dir = cam.eye().minus(cam.center()).normalized();
            double dist = 3.0;
            glu.gluLookAt(dir.x() * dist, dir.y() * dist, dir.z() * dist, 0, 0, 0, 0, 1, 0);
            gl.glClear(GL2.GL_DEPTH_BUFFER_BIT);
            gl.glDisable(GL2.GL_LIGHTING);
            gl.glLineWidth(3f);
            gl.glBegin(GL2.GL_LINES);
            gl.glColor3f(0.95f, 0.3f, 0.3f);
            gl.glVertex3f(0, 0, 0);
            gl.glVertex3f(1, 0, 0);
            gl.glColor3f(0.35f, 0.9f, 0.35f);
            gl.glVertex3f(0, 0, 0);
            gl.glVertex3f(0, 1, 0);
            gl.glColor3f(0.35f, 0.55f, 1f);
            gl.glVertex3f(0, 0, 0);
            gl.glVertex3f(0, 0, 1);
            gl.glEnd();
            gl.glLineWidth(1f);
            gl.glEnable(GL2.GL_LIGHTING);
            gl.glPopMatrix();
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPopMatrix();
            gl.glMatrixMode(GL2.GL_MODELVIEW);
            gl.glViewport(0, 0, viewportW, viewportH);
        }

        /** Земля лежит НИЖЕ опорной рамы, а не вперемешку с ней по Y (баг-репорт: "опорные рамы
         *  пересекаются с полом, рендерятся с переменным успехом" -- classic Z-fighting: земля
         *  раньше всегда занимала Y=[-20,0], опорная рама -- Y=[-baseThicknessMm,0], полностью
         *  перекрываясь). {@code groundTopY = -baseThicknessMm} -- поверхность земли ровно под
         *  нижней гранью опорной рамы, без нахлёста ни на пиксель. */
        private void drawGroundPlane(GL2 gl, double centerX, double centerZ, double halfExtentMm,
                double baseThicknessMm) {
            double groundTopY = -baseThicknessMm;
            gl.glColor3f(0.16f, 0.17f, 0.19f);
            // Пол ОДНОСТОРОННИЙ (2026-10-22): один квадрат, видимый только сверху (отсечение обратных граней).
            // Раньше это была коробка 20 мм — камера под землёй упиралась в её нижнюю грань и закрывала всю
            // сцену; теперь снизу пол прозрачен, и конструктив видно с любого ракурса.
            float x0 = (float) (centerX - halfExtentMm);
            float x1 = (float) (centerX + halfExtentMm);
            float z0 = (float) (centerZ - halfExtentMm);
            float z1 = (float) (centerZ + halfExtentMm);
            float y = (float) groundTopY;
            gl.glEnable(GL2.GL_CULL_FACE);
            gl.glCullFace(GL2.GL_BACK);
            gl.glBegin(GL2.GL_QUADS);
            gl.glNormal3f(0, 1, 0);
            gl.glVertex3f(x0, y, z1);
            gl.glVertex3f(x1, y, z1);
            gl.glVertex3f(x1, y, z0);
            gl.glVertex3f(x0, y, z0);
            gl.glEnd();
            gl.glDisable(GL2.GL_CULL_FACE);
        }

        private void drawScreenPlaneFallback(GL2 gl, double widthMm, double heightMm, double elevationMm) {
            gl.glColor3f(0.10f, 0.35f, 0.55f);
            box(gl, 0, elevationMm, -15, widthMm, heightMm, 15);
        }

        /** Экран как реальная сетка КОНКРЕТНЫХ кабинетов, приподнятая на {@code elevationMm}
         *  над землёй (баг-репорт «экран может стоять на подъёме, отрази это») — учитывает
         *  скрытые/переопределённые по типу ячейки формы экрана. Лицевая грань красится
         *  реальной маской экрана, остальные 5 граней — нейтральный тёмный цвет корпуса. */
        private void drawScreenCabinets(GL2 gl, Screen screen, CabinetType defaultType, Workspace ws,
                double elevationMm) {
            double cellW = defaultType.getWidthMm();
            double cellH = defaultType.getHeightMm();
            double totalHeight = screen.getRows() * cellH;
            // Изогнутый экран наземного конструктива (2026-10-01): каждая колонна кабинетов —
            // повёрнутая коробка реальной глубины кабинета, лицевые грани — хорды окружности
            // радиуса R (StructureCurveMath#cabinetColumns; клин-зазоры между тыльными углами
            // соседних колонн — так и есть у плоских кабинетов). Прямой экран рисуется как раньше.
            // Объём кабинета (2026-10-22): и прямой, и изогнутый экран рисуются коробками РЕАЛЬНОЙ
            // глубины из библиотеки (у кабинета с переопределённым типом — его глубины), лицевая грань
            // на z = 0, корпус уходит назад. Раньше прямой экран был плитой 15 мм.
            List<StructureCurveMath.Placement> columns = null;
            if (screen.getMountType() == ScreenMountType.STRUCTURE && screen.getStructureCurveType().isCurved()) {
                columns = StructureCurveMath.cabinetColumns(StructureCurveMath.curveOf(screen, defaultType));
            }
            for (var cab : screen.getCabinets()) {
                if (cab.isHidden()) {
                    continue;
                }
                CabinetType effective = defaultType;
                if (cab.getCabinetTypeId() != null && ws != null) {
                    CabinetType override = ws.cabinetTypeById(cab.getCabinetTypeId());
                    if (override != null) {
                        effective = override;
                    }
                }
                double w = effective.getWidthMm();
                double h = effective.getHeightMm();
                double depth = StructureCurveMath.cabinetDepthOf(effective);
                double x = cab.getColIndex() * cellW;
                double yTop = totalHeight - cab.getRowIndex() * cellH;
                java.awt.Color front = screen.maskColor((cab.getRowIndex() + cab.getColIndex()) % 2);
                if (columns != null && cab.getColIndex() >= 0 && cab.getColIndex() < columns.size()) {
                    StructureCurveMath.Placement col = columns.get(cab.getColIndex());
                    gl.glPushMatrix();
                    gl.glTranslated(col.anchorX(), 0, col.anchorZ());
                    gl.glRotated(col.yawDeg(), 0, 1, 0);
                    boxFrontColored(gl, -cellW / 2.0, elevationMm + yTop - h, -depth, w, h, depth, front,
                            CABINET_CHASSIS_COLOR);
                    gl.glPopMatrix();
                    continue;
                }
                boxFrontColored(gl, x, elevationMm + yTop - h, -depth, w, h, depth, front, CABINET_CHASSIS_COLOR);
            }
        }

        /** Выделение режима «Раскладка»: контур текущего экрана (синий), у выбранной башни — оранжевая
         *  рамка по её контуру на всю высоту, кольцо на земле вокруг центра и стрелка «вперёд» (к
         *  экрану). Всё без освещения и поверх геометрии, чтобы читалось на любом фоне. */
        private void drawLayoutOverlay(GL2 gl) {
            Screen active = model.getCurrentScreen();
            if (active == null) {
                return;
            }
            CabinetType type = model.typeOf(active);
            gl.glDisable(GL2.GL_LIGHTING);
            gl.glDisable(GL2.GL_DEPTH_TEST);
            gl.glLineWidth(2.5f);
            if (type != null) {
                double y0 = active.getStructureScreenElevationMm();
                double y1 = y0 + active.getRows() * type.getHeightMm();
                gl.glColor3f(0.35f, 0.65f, 1f);
                gl.glBegin(GL2.GL_LINES);
                for (double[][] r : StructureLayoutMath.screenRects(active, type)) {
                    for (int i = 0; i < 4; i++) {
                        double[] a = r[i];
                        double[] b = r[(i + 1) % 4];
                        gl.glVertex3d(a[0], y1, a[1]);
                        gl.glVertex3d(b[0], y1, b[1]);
                        gl.glVertex3d(a[0], y0, a[1]);
                        gl.glVertex3d(b[0], y0, b[1]);
                    }
                }
                gl.glEnd();
            }
            double[] ringC = screenRingCentre();
            if (ringC != null) {
                // кольцо вращения экрана группы: оранжевое; при вращении — толще; ручка «вперёд» (к зрителю)
                double rr = screenRingRadius(active);
                StructureCurveMath.Placement pose = StructureLayoutMath.screenPose(active);
                boolean hotRing = dragKind == DragKind.SCREEN && dragHandle == Handle.RING;
                gl.glLineWidth(hotRing ? 5f : 2.5f);
                gl.glColor3f(1f, hotRing ? 0.85f : 0.62f, hotRing ? 0.5f : 0.15f);
                gl.glBegin(GL2.GL_LINES);
                int segs = 96;
                for (int i = 0; i < segs; i++) {
                    double a0 = 2 * Math.PI * i / segs;
                    double a1 = 2 * Math.PI * (i + 1) / segs;
                    line3(gl, ringC[0] + rr * Math.cos(a0), ringC[1] + rr * Math.sin(a0),
                            ringC[0] + rr * Math.cos(a1), ringC[1] + rr * Math.sin(a1), 3);
                }
                gl.glEnd();
                double[] front = new double[]{Math.sin(Math.toRadians(pose.yawDeg())),
                        Math.cos(Math.toRadians(pose.yawDeg()))};
                gl.glBegin(GL2.GL_QUADS);
                double kx = ringC[0] + front[0] * rr;
                double kz = ringC[1] + front[1] * rr;
                gl.glVertex3d(kx - 120, 4, kz - 120);
                gl.glVertex3d(kx + 120, 4, kz - 120);
                gl.glVertex3d(kx + 120, 4, kz + 120);
                gl.glVertex3d(kx - 120, 4, kz + 120);
                gl.glEnd();
            }
            int k = selectedTower();
            if (k >= 0 && active.getMountType() == ScreenMountType.STRUCTURE) {
                StructureCurveMath.Setup setup = StructureCurveMath.setupOf(active, type, model.getWorkspace());
                StructureCurveMath.Placement w = StructureLayoutMath.towerPlacementInGroup(active, setup, k);
                StructureCurveMath.TowerSpec spec = StructureCurveMath.towerSpecOf(active, model.getWorkspace(), k);
                double hw = spec.outerWidthMm() / 2.0;
                double depth = spec.depthMm();
                double height = Math.max(1000, active.getStructureTowerHeightMm());
                gl.glPushMatrix();
                gl.glTranslated(w.anchorX(), 0, w.anchorZ());
                gl.glRotated(w.yawDeg(), 0, 1, 0);
                gl.glColor3f(1f, 0.62f, 0.15f);
                gl.glBegin(GL2.GL_LINES);
                double[][] c = {{-hw, 0}, {hw, 0}, {hw, -depth}, {-hw, -depth}};
                for (int i = 0; i < 4; i++) {
                    double[] a = c[i];
                    double[] b = c[(i + 1) % 4];
                    gl.glVertex3d(a[0], 3, a[1]);
                    gl.glVertex3d(b[0], 3, b[1]);
                    gl.glVertex3d(a[0], height, a[1]);
                    gl.glVertex3d(b[0], height, b[1]);
                    gl.glVertex3d(a[0], 3, a[1]);
                    gl.glVertex3d(a[0], height, a[1]);
                }
                gl.glEnd();
                drawGizmo(gl, spec);
                gl.glPopMatrix();
            }
            if (guidesOn) {
                drawGuides(gl);
            }
            gl.glEnable(GL2.GL_DEPTH_TEST);
            gl.glEnable(GL2.GL_LIGHTING);
        }

        private void line3(GL2 gl, double x0, double z0, double x1, double z1, double y) {
            gl.glVertex3d(x0, y, z0);
            gl.glVertex3d(x1, y, z1);
        }

        /** Гизмо выбранной башни в её локальной системе (вызывается внутри матрицы башни): красная ось X,
         *  синяя ось Z (стрелки на обоих концах), жёлтый центр, оранжевое кольцо с ручкой «вперёд». Ручка под
         *  курсором или в работе — ярче и толще. */
        private void drawGizmo(GL2 gl, StructureCurveMath.TowerSpec spec) {
            Handle hot = dragKind == DragKind.TOWER ? dragHandle : hoverHandle;
            double cz = -spec.depthMm() / 2.0;
            double rx = StructureGizmoMath.axisReach(spec, true);
            double rz = StructureGizmoMath.axisReach(spec, false);
            double arrow = 170;
            double y = 3;

            gl.glLineWidth(hot == Handle.AXIS_X ? 5f : 2.5f);
            if (hot == Handle.AXIS_X) {
                gl.glColor3f(1f, 0.7f, 0.7f);
            } else {
                gl.glColor3f(0.95f, 0.3f, 0.3f);
            }
            gl.glBegin(GL2.GL_LINES);
            line3(gl, -rx, cz, rx, cz, y);
            line3(gl, rx, cz, rx - arrow, cz + 90, y);
            line3(gl, rx, cz, rx - arrow, cz - 90, y);
            line3(gl, -rx, cz, -rx + arrow, cz + 90, y);
            line3(gl, -rx, cz, -rx + arrow, cz - 90, y);
            gl.glEnd();

            gl.glLineWidth(hot == Handle.AXIS_Z ? 5f : 2.5f);
            if (hot == Handle.AXIS_Z) {
                gl.glColor3f(0.7f, 0.8f, 1f);
            } else {
                gl.glColor3f(0.35f, 0.55f, 1f);
            }
            gl.glBegin(GL2.GL_LINES);
            line3(gl, 0, cz - rz, 0, cz + rz, y);
            line3(gl, 0, cz + rz, 90, cz + rz - arrow, y);
            line3(gl, 0, cz + rz, -90, cz + rz - arrow, y);
            line3(gl, 0, cz - rz, 90, cz - rz + arrow, y);
            line3(gl, 0, cz - rz, -90, cz - rz + arrow, y);
            gl.glEnd();

            // кольцо вращения
            double radius = StructureGizmoMath.ringRadius(spec);
            gl.glLineWidth(hot == Handle.RING ? 5f : 2.5f);
            if (hot == Handle.RING) {
                gl.glColor3f(1f, 0.85f, 0.5f);
            } else {
                gl.glColor3f(1f, 0.62f, 0.15f);
            }
            gl.glBegin(GL2.GL_LINES);
            int seg = 64;
            for (int i = 0; i < seg; i++) {
                double a0 = 2 * Math.PI * i / seg;
                double a1 = 2 * Math.PI * (i + 1) / seg;
                line3(gl, radius * Math.cos(a0), cz + radius * Math.sin(a0), radius * Math.cos(a1),
                        cz + radius * Math.sin(a1), y);
            }
            gl.glEnd();
            // ручка кольца «вперёд» (к экрану): квадрат на кольце + короткая стрелка наружу
            double kz = cz + radius;
            gl.glBegin(GL2.GL_QUADS);
            gl.glVertex3d(-90, y, kz - 90);
            gl.glVertex3d(90, y, kz - 90);
            gl.glVertex3d(90, y, kz + 90);
            gl.glVertex3d(-90, y, kz + 90);
            gl.glEnd();

            // центр — свободный перенос
            gl.glColor3f(hot == Handle.CENTER ? 1f : 0.95f, hot == Handle.CENTER ? 1f : 0.85f,
                    hot == Handle.CENTER ? 0.7f : 0.25f);
            gl.glBegin(GL2.GL_QUADS);
            double h = StructureGizmoMath.CENTER_RADIUS_MM * 0.6;
            gl.glVertex3d(-h, y + 1, cz - h);
            gl.glVertex3d(h, y + 1, cz - h);
            gl.glVertex3d(h, y + 1, cz + h);
            gl.glVertex3d(-h, y + 1, cz + h);
            gl.glEnd();
            gl.glLineWidth(2.5f);
        }

        /** Линии привязки (с Shift при перетаскивании): все опорные — бледно, сработавшие — ярко. */
        private void drawGuides(GL2 gl) {
            if (dragStart == null) {
                return;
            }
            StructureCurveMath.Placement at = dragLast != null ? dragLast : dragStart;
            double[] c = at.toWorld(dragCenterX, dragCenterZ);
            gl.glLineWidth(1.5f);
            gl.glColor3f(0.22f, 0.5f, 0.55f);
            gl.glBegin(GL2.GL_LINES);
            for (RefLine r : referenceLines()) {
                guideSegment(gl, r, c, 7000);
            }
            gl.glEnd();
            gl.glLineWidth(4f);
            gl.glColor3f(0.3f, 1f, 1f);
            gl.glBegin(GL2.GL_LINES);
            for (RefLine r : activeGuides) {
                guideSegment(gl, r, c, 12000);
            }
            gl.glEnd();
            gl.glLineWidth(2.5f);
        }

        private void guideSegment(GL2 gl, RefLine r, double[] around, double halfLength) {
            // отрезок линии вокруг проекции точки {@code around} на неё
            double t = (around[0] - r.px()) * r.dx() + (around[1] - r.pz()) * r.dz();
            double cx = r.px() + r.dx() * t;
            double cz = r.pz() + r.dz() * t;
            line3(gl, cx - r.dx() * halfLength, cz - r.dz() * halfLength, cx + r.dx() * halfLength,
                    cz + r.dz() * halfLength, 5);
        }

        private void drawStructure(GL2 gl, Screen screen, boolean interactive) {
            StructureGeometry g = computeGeometry(screen);
            List<Candidate> frames = frameCandidates(screen, g);
            List<Candidate> peremychki = peremychkaCandidates(screen, g);
            List<Candidate> bases = baseCandidates(screen, g);
            List<Candidate> reinforcements = reinforcementCandidates(screen, g);

            gl.glColor3f(0.6f, 0.61f, 0.63f);
            for (Candidate c : frames) {
                double[] b = c.bounds();
                boolean pushed = beginTransform(gl, c.xf());
                if (c.exists()) {
                    drawTowerSegment(gl, b[0], b[1], b[2], b[3], b[4], b[5]);
                } else if (interactive && c.key().equals(hoveredGhostKey)) {
                    drawWireBoxGhost(gl, b[0], b[1], b[2], b[3], b[4], b[5]);
                }
                endTransform(gl, pushed);
            }

            gl.glColor3f(0.58f, 0.59f, 0.61f);
            for (Candidate c : reinforcements) {
                double[] b = c.bounds();
                boolean pushed = beginTransform(gl, c.xf());
                if (c.exists()) {
                    drawTowerSegment(gl, b[0], b[1], b[2], b[3], b[4], b[5]);
                } else if (interactive && c.key().equals(hoveredGhostKey)) {
                    drawWireBoxGhost(gl, b[0], b[1], b[2], b[3], b[4], b[5]);
                }
                endTransform(gl, pushed);
            }

            for (Candidate c : peremychki) {
                double[] b = c.bounds();
                boolean pushed = beginTransform(gl, c.xf());
                if (c.exists()) {
                    drawInterTowerPeremychka(gl, b[0], b[1], b[2], b[3], b[4], b[5]);
                } else if (interactive && c.key().equals(hoveredGhostKey)) {
                    drawWireBoxGhost(gl, b[0], b[1], b[2], b[3], b[4], b[5]);
                }
                endTransform(gl, pushed);
            }

            for (Candidate c : bases) {
                double[] b = c.bounds();
                boolean pushed = beginTransform(gl, c.xf());
                if (c.exists()) {
                    drawBaseFrame(gl, b[0], b[1], b[2], b[3], b[4], b[5]);
                } else if (interactive && c.key().equals(hoveredGhostKey)) {
                    drawWireBoxGhost(gl, b[0], b[1], b[2], b[3], b[4], b[5]);
                }
                endTransform(gl, pushed);
            }

            drawCups(gl, screen, g);
        }

        /** Локальная система раздельной башни (2026-10-01) — то же преобразование, что
         *  {@link YawTransform#toWorld} (перенос в anchor, поворот вокруг вертикали, сдвиг
         *  координат стены в начало башни). {@code null}/единичное — стена: матрица не
         *  трогается вовсе, кадр рисуется теми же вызовами GL, что и раньше. */
        private boolean beginTransform(GL2 gl, YawTransform xf) {
            if (xf == null || xf.isIdentity()) {
                return false;
            }
            gl.glPushMatrix();
            gl.glTranslated(xf.anchorX(), 0, xf.anchorZ());
            gl.glRotated(xf.yawDeg(), 0, 1, 0);
            gl.glTranslated(-xf.localOffsetX(), 0, -xf.localOffsetZ());
            return true;
        }

        private void endTransform(GL2 gl, boolean pushed) {
            if (pushed) {
                gl.glPopMatrix();
            }
        }

        /** Стаканы (соединители) на РЕАЛЬНЫХ стыках — та же логика смежности, что
         *  {@code StructureCalc.compute} использует для подсчёта cupCount, просто как картинка:
         *  вертикальные стыки (в каждом ряду отдельно), стыки перемычек (к переднему/заднему
         *  посту) и стыки базовых секций (к посту над секцией 0 / к соседней секции).
         *
         * <p>Round 3: у вертикальных/усилительных рам рельсы теперь стоят у БЛИЖНЕГО/ДАЛЬНЕГО
         *  по Z края (см. {@link #drawTowerSegment}), не слева/справа по X — стыковые стаканы
         *  на них передвинуты туда же. Стаканы перемычек/базы (Round 5/7) — у обоих концов
         *  X-пролёта между соседними башнями, на Z-глубине соответствующего ряда/секции. */
        private void drawCups(GL2 gl, Screen screen, StructureGeometry g) {
            double tube = Math.min(40, g.frameD());
            double cupSize = tube * 1.5;
            double frameRail = railThickness(g.frameW());
            gl.glColor3f(CUP_COLOR.getRed() / 255f, CUP_COLOR.getGreen() / 255f, CUP_COLOR.getBlue() / 255f);

            // Round 9 (баг-репорт: "при добавлении рам вглубь появляются стаканы вверх в
            // воздухе") -- этот блок группирует по (towerIndex, row) и трактует segmentIndex
            // как позицию В СТЕКЕ (y = (seg+1)*frameH), что верно ТОЛЬКО для row 0/1. Для
            // row == 2 (усилительные рамы выноса) segmentIndex на самом деле означает НОМЕР
            // СЕКЦИИ ВЫНОСА (см. class-javadoc StructureFrameCell), не позицию по высоте --
            // без фильтра `row < 2` две соседние по номеру секции усилительные рамы читались
            // как "смежный стык в стеке" и стакан улетал на y=(section+1)*frameH, в воздух.
            // StructureCalc.compute уже фильтрует так же при подсчёте verticalJoints -- этот
            // рендер-цикл просто не был приведён в соответствие.
            java.util.Map<String, SortedSet<Integer>> segmentsByTowerRow = new TreeMap<>();
            for (StructureFrameCell c : screen.getStructureFrameCells()) {
                if (!c.isHidden() && c.getRow() < 2) {
                    segmentsByTowerRow.computeIfAbsent(c.getTowerIndex() + ":" + c.getRow(), k -> new TreeSet<>())
                            .add(c.getSegmentIndex());
                }
            }
            for (var entry : segmentsByTowerRow.entrySet()) {
                String[] key = entry.getKey().split(":");
                int tower = Integer.parseInt(key[0]);
                int row = Integer.parseInt(key[1]);
                double towerX = postX(g, tower);
                double zCenter = row == 0 ? g.frontZ() : g.backZ();
                double cupX = towerX - cupSize / 2.0;
                double nearCupZ = zCenter - g.frameW() / 2.0 + frameRail / 2.0 - cupSize / 2.0;
                double farCupZ = zCenter + g.frameW() / 2.0 - frameRail / 2.0 - cupSize / 2.0;
                boolean pushed = beginTransform(gl, postTransform(g, tower));
                // Стакан — блок высотой в стакан (50 мм) ровно в зазоре между рамами (2026-10-22);
                // убранный стык (ПКМ) или сборка без стаканов — рамы стоят вплотную, блока нет.
                double cupH = StructureCupMath.cupHeightMm(screen, model.getWorkspace());
                for (int seg : entry.getValue()) {
                    if (entry.getValue().contains(seg + 1)
                            && StructureCupMath.jointHasCup(screen, tower, row, seg + 1)) {
                        double y = StructureCupMath.frameBottomY(screen, model.getWorkspace(), tower, row, seg + 1,
                                g.frameH()) - cupH;
                        box(gl, cupX, y, nearCupZ, cupSize, cupH, cupSize);
                        box(gl, cupX, y, farCupZ, cupSize, cupH, cupSize);
                    }
                }
                endTransform(gl, pushed);
            }

            // Round 10 (баг-репорт: "теперь стаканы появляются на 0 высоте, они там не нужны.
            // стаканы ставятся сверху рам, в случае если над ней ставится еще одна рама и
            // только тогда") -- пользователь сузил правило до единственного случая, уже
            // реализованного выше: стакан существует ТОЛЬКО там, где одна рама физически стоит
            // СВЕРХУ другой рамы ТОГО ЖЕ типа (вертикальный стек, row 0/1, цикл выше). Ни
            // перемычка (Round 8), ни база, ни усилительная рама этому условию не отвечают --
            // они примыкают сбоку/снизу, а не стоят друг на друге -- стаканы для базы и
            // усилительных рам (были на уровне пола, "0 высоты") убраны вслед за перемычкой.
            // StructureCalc.compute больше НЕ учитывает baseJoints/reinforcementCount в
            // cupCount -- см. её javadoc.
        }

        /** Один сегмент вертикальной рамы башни — 2 стойки (толстые рельсы у ближнего/дальнего
         *  по Z края) + РОВНО 3 перекладины (низ/середина/верх — "8-образная" форма), БЕЗ
         *  диагональных раскосов — форма взята с референсных фото пользователя.
         *
         * <p><b>Round 3 — узкая грань к зрителю</b> (баг-репорт: "рама должна быть повёрнута
         * на 90°, перекладины видны сбоку/изнутри башни, как настоящая лестница, по которой
         * лезут, а не в лицо зрителю"): широкая грань рамы (frameW≈500мм, {@code d} здесь)
         * теперь уходит В ГЛУБИНУ вдоль Z (а не поперёк вдоль X, как было раньше) — рельсы
         * стоят у БЛИЖНЕГО и ДАЛЬНЕГО (по Z) края, перекладины тянутся вдоль Z между ними,
         * узкая грань (frameD≈51мм, {@code w} здесь) — единственное, что видно СО СТОРОНЫ
         * экрана. Параметры {@code x,y,z,w,h,d} — чистый угол+протяжённость (как у
         * {@code bounds} кандидата, {@link #box}), без скрытого "центра" — так исключается
         * рассинхрон с {@link #drawWireBoxGhost}, которая рисует ТОТ ЖЕ bounds. */
        private void drawTowerSegment(GL2 gl, double x, double y, double z, double w, double h, double d) {
            double rail = railThickness(d);
            double crossThickness = rail * 0.7;

            gl.glColor3f(0.62f, 0.63f, 0.65f);
            box(gl, x, y, z, w, h, rail);
            box(gl, x, y, z + d - rail, w, h, rail);

            gl.glColor3f(0.58f, 0.59f, 0.61f);
            double crossLen = d - 2 * rail;
            double crossZ = z + rail;
            box(gl, x, y, crossZ, w, crossThickness, crossLen);
            box(gl, x, y + h / 2.0 - crossThickness / 2.0, crossZ, w, crossThickness, crossLen);
            box(gl, x, y + h - crossThickness, crossZ, w, crossThickness, crossLen);
            gl.glColor3f(0.6f, 0.61f, 0.63f);
        }

        /** Перемычка между соседними башнями (Round 5) — форма см. {@link
         *  #drawGapSpanningLadderFrame}. {@code x}/{@code y}/{@code z} — левый-нижний-задний
         *  угол bounds. */
        private void drawInterTowerPeremychka(GL2 gl, double x, double y, double z, double xSpan, double thickness,
                double zWidth) {
            drawGapSpanningLadderFrame(gl, x, y, z, xSpan, thickness, zWidth, 0.55f, 0.5f, 0.42f, 0.5f, 0.46f, 0.39f);
        }

        /** Опорная (базовая) рама — Round 7 (баг-репорт: "рамы основания должны ставиться точно
         *  так же как перемычки, но на уровне пола") — тот же "гэп-каркас" (рельсы вдоль X,
         *  между соседними башнями), что у {@link #drawInterTowerPeremychka}, только серая, под
         *  цвет вертикальных рам, а не бежевая (не путать визуально с перемычкой), и на уровне
         *  пола вместо высоты перекладины. */
        private void drawBaseFrame(GL2 gl, double x, double y, double z, double xSpan, double thickness,
                double zWidth) {
            drawGapSpanningLadderFrame(gl, x, y, z, xSpan, thickness, zWidth, 0.42f, 0.43f, 0.45f, 0.37f, 0.38f, 0.4f);
        }

        /** Общая форма для перемычки/опоры (Round 7) — рельсы вдоль X (между соседними
         *  башнями, {@code xSpan}) у ближнего/дальнего по Z края (совпадают с footprint-ом
         *  ряда, {@code zWidth}), 3 перекладины перпендикулярно (вдоль Z) в начале/середине/
         *  конце X-пролёта — та же "8-образная" форма, что у {@link #drawTowerSegment}, просто
         *  третья axis-permutation. */
        private void drawGapSpanningLadderFrame(GL2 gl, double x, double y, double z, double xSpan, double thickness,
                double zWidth, float railR, float railG, float railB, float crossR, float crossG, float crossB) {
            double rail = railThickness(xSpan);
            double crossThickness = rail * 0.7;
            gl.glColor3f(railR, railG, railB);
            box(gl, x, y, z, xSpan, thickness, rail);
            box(gl, x, y, z + zWidth - rail, xSpan, thickness, rail);

            gl.glColor3f(crossR, crossG, crossB);
            double crossZLen = zWidth - 2 * rail;
            double crossZ = z + rail;
            box(gl, x, y, crossZ, crossThickness, thickness, crossZLen);
            box(gl, x + xSpan / 2.0 - crossThickness / 2.0, y, crossZ, crossThickness, thickness, crossZLen);
            box(gl, x + xSpan - crossThickness, y, crossZ, crossThickness, thickness, crossZLen);
            gl.glColor3f(railR, railG, railB);
        }

        private void boxFrontColored(GL2 gl, double x, double y, double z, double w, double h, double d,
                java.awt.Color front, java.awt.Color chassis) {
            float x0 = (float) x;
            float y0 = (float) y;
            float z0 = (float) z;
            float x1 = (float) (x + w);
            float y1 = (float) (y + h);
            float z1 = (float) (z + d);

            gl.glColor3f(front.getRed() / 255f, front.getGreen() / 255f, front.getBlue() / 255f);
            gl.glBegin(GL2.GL_QUADS);
            gl.glNormal3f(0, 0, 1);
            gl.glVertex3f(x0, y0, z1);
            gl.glVertex3f(x1, y0, z1);
            gl.glVertex3f(x1, y1, z1);
            gl.glVertex3f(x0, y1, z1);
            gl.glEnd();

            gl.glColor3f(chassis.getRed() / 255f, chassis.getGreen() / 255f, chassis.getBlue() / 255f);
            gl.glBegin(GL2.GL_QUADS);
            gl.glNormal3f(0, 0, -1);
            gl.glVertex3f(x1, y0, z0);
            gl.glVertex3f(x0, y0, z0);
            gl.glVertex3f(x0, y1, z0);
            gl.glVertex3f(x1, y1, z0);

            gl.glNormal3f(0, 1, 0);
            gl.glVertex3f(x0, y1, z0);
            gl.glVertex3f(x0, y1, z1);
            gl.glVertex3f(x1, y1, z1);
            gl.glVertex3f(x1, y1, z0);

            gl.glNormal3f(0, -1, 0);
            gl.glVertex3f(x0, y0, z1);
            gl.glVertex3f(x0, y0, z0);
            gl.glVertex3f(x1, y0, z0);
            gl.glVertex3f(x1, y0, z1);

            gl.glNormal3f(1, 0, 0);
            gl.glVertex3f(x1, y0, z1);
            gl.glVertex3f(x1, y0, z0);
            gl.glVertex3f(x1, y1, z0);
            gl.glVertex3f(x1, y1, z1);

            gl.glNormal3f(-1, 0, 0);
            gl.glVertex3f(x0, y0, z0);
            gl.glVertex3f(x0, y0, z1);
            gl.glVertex3f(x0, y1, z1);
            gl.glVertex3f(x0, y1, z0);
            gl.glEnd();
        }

        private void box(GL2 gl, double x, double y, double z, double w, double h, double d) {
            float x0 = (float) x;
            float y0 = (float) y;
            float z0 = (float) z;
            float x1 = (float) (x + w);
            float y1 = (float) (y + h);
            float z1 = (float) (z + d);

            gl.glBegin(GL2.GL_QUADS);
            gl.glNormal3f(0, 0, 1);
            gl.glVertex3f(x0, y0, z1);
            gl.glVertex3f(x1, y0, z1);
            gl.glVertex3f(x1, y1, z1);
            gl.glVertex3f(x0, y1, z1);

            gl.glNormal3f(0, 0, -1);
            gl.glVertex3f(x1, y0, z0);
            gl.glVertex3f(x0, y0, z0);
            gl.glVertex3f(x0, y1, z0);
            gl.glVertex3f(x1, y1, z0);

            gl.glNormal3f(0, 1, 0);
            gl.glVertex3f(x0, y1, z0);
            gl.glVertex3f(x0, y1, z1);
            gl.glVertex3f(x1, y1, z1);
            gl.glVertex3f(x1, y1, z0);

            gl.glNormal3f(0, -1, 0);
            gl.glVertex3f(x0, y0, z1);
            gl.glVertex3f(x0, y0, z0);
            gl.glVertex3f(x1, y0, z0);
            gl.glVertex3f(x1, y0, z1);

            gl.glNormal3f(1, 0, 0);
            gl.glVertex3f(x1, y0, z1);
            gl.glVertex3f(x1, y0, z0);
            gl.glVertex3f(x1, y1, z0);
            gl.glVertex3f(x1, y1, z1);

            gl.glNormal3f(-1, 0, 0);
            gl.glVertex3f(x0, y0, z0);
            gl.glVertex3f(x0, y0, z1);
            gl.glVertex3f(x0, y1, z1);
            gl.glVertex3f(x0, y1, z0);
            gl.glEnd();
        }

        private void drawWireBoxGhost(GL2 gl, double x, double y, double z, double w, double h, double d) {
            gl.glDisable(GL2.GL_LIGHTING);
            gl.glColor3f(0.35f, 0.75f, 0.45f);
            float x0 = (float) x;
            float y0 = (float) y;
            float z0 = (float) z;
            float x1 = (float) (x + w);
            float y1 = (float) (y + h);
            float z1 = (float) (z + d);
            gl.glBegin(GL2.GL_LINES);
            edge(gl, x0, y0, z0, x1, y0, z0);
            edge(gl, x1, y0, z0, x1, y0, z1);
            edge(gl, x1, y0, z1, x0, y0, z1);
            edge(gl, x0, y0, z1, x0, y0, z0);
            edge(gl, x0, y1, z0, x1, y1, z0);
            edge(gl, x1, y1, z0, x1, y1, z1);
            edge(gl, x1, y1, z1, x0, y1, z1);
            edge(gl, x0, y1, z1, x0, y1, z0);
            edge(gl, x0, y0, z0, x0, y1, z0);
            edge(gl, x1, y0, z0, x1, y1, z0);
            edge(gl, x1, y0, z1, x1, y1, z1);
            edge(gl, x0, y0, z1, x0, y1, z1);
            gl.glEnd();
            gl.glEnable(GL2.GL_LIGHTING);
        }

        private void edge(GL2 gl, float x0, float y0, float z0, float x1, float y1, float z1) {
            gl.glVertex3f(x0, y0, z0);
            gl.glVertex3f(x1, y1, z1);
        }
    }
}
