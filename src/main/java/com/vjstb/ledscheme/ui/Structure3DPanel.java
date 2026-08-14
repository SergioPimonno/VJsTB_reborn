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
import com.vjstb.ledscheme.service.StructurePickMath;
import com.vjstb.ledscheme.service.StructurePickMath.Ray;
import com.vjstb.ledscheme.service.StructurePickMath.Vec3;
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
 * <p><b>Объёмная геометрия башни</b> (см. STRUCTURE_CALC_NOTES.md за полным описанием и
 * эскизом, согласованным с пользователем): передний ряд вертикальных рам стоит прямо у
 * экрана (Z = towerZ), задний ряд — на глубину одной короткой рамы позади (Z = towerZ −
 * sectionDepthMm). Перемычки соединяют ряды внутри ОДНОЙ башни на уровнях, кратных 1.5
 * высоты выбранной рамы (см. {@link StructureCalc#peremychkaIntervalMm}, Phase 2.2 — раньше
 * было отдельной настройкой Персонализации в мм, теперь коэффициент от реальной высоты рамы
 * — только реально расставленные уровни хранятся, см.
 * {@link Screen#getStructurePeremychkaCells()}). Базовая рама — секция 0 (ядро, под объёмом
 * башни) плюс секции 1+ выноса назад под балласт, каждая той же глубины sectionDepthMm, с
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
 * <p><b>Picking</b>: клик по СУЩЕСТВУЮЩЕМУ элементу прячет его, клик по наведённому
 * "призрачному" месту добавляет — "призраки" показываются ПО НАВЕДЕНИЮ (только ближайший к
 * курсору). Ручной CPU-side raycast, математика — {@link StructurePickMath}. Тестируется не
 * один луч через курсор, а небольшой крест точек вокруг него ("толстый курсор" — тонкие
 * стойки в перспективе иначе почти невозможно зацепить мышью).
 */
public class Structure3DPanel extends JPanel {

    private static final double DEFAULT_FRAME_HEIGHT_MM = 950;
    private static final double DEFAULT_FRAME_WIDTH_MM = 500;
    private static final double DEFAULT_FRAME_DEPTH_MM = 51;
    private static final double DEFAULT_SECTION_DEPTH_MM = 500;
    /** Гарантированный зазор (мм) между задней гранью экрана и передним рядом конструктива —
     *  см. javadoc {@code computeGeometry}. */
    private static final double SCREEN_CLEARANCE_MM = 400;
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
        super(new BorderLayout());
        this.model = model;
        setPreferredSize(new Dimension(700, 560));
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
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                int moved = Math.abs(e.getX() - pressX) + Math.abs(e.getY() - pressY);
                if (moved <= CLICK_MOVE_THRESHOLD_PX) {
                    handleClick(e.getX(), e.getY());
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
        double scale = distanceMm * 0.0016;
        panXMm += -dxPx * right.x() * scale;
        panZMm += -dxPx * right.z() * scale;
        panYMm += dyPx * scale;
    }

    // ---- камера/геометрия, общие для рендера и picking'а ----

    private record CameraState(Vec3 eye, Vec3 center) {
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
        double yawRad = Math.toRadians(yawDeg);
        double pitchRad = Math.toRadians(pitchDeg);
        double eyeX = centerX + distanceMm * Math.cos(pitchRad) * Math.sin(yawRad);
        double eyeY = centerY + distanceMm * Math.sin(pitchRad);
        double eyeZ = centerZ + distanceMm * Math.cos(pitchRad) * Math.cos(yawRad);
        return new CameraState(new Vec3(eyeX, eyeY, eyeZ), new Vec3(centerX, centerY, centerZ));
    }

    private record StructureGeometry(double frameH, double frameW, double frameD, double sectionDepthMm,
            double baseThickness, double peremychkaThickness, double spacing, double frontZ, double backZ,
            double peremychkaIntervalMm) {
    }

    private StructureGeometry computeGeometry(Screen screen) {
        Workspace ws = model.getWorkspace();
        StructureFrameType frameType = ws.structureFrameTypeById(screen.getStructureFrameTypeId());
        StructureFrameType shortType = ws.structureFrameTypeById(screen.getStructureShortFrameTypeId());
        double frameH = dim(frameType != null ? frameType.getHeightMm() : null, DEFAULT_FRAME_HEIGHT_MM);
        double frameW = dim(frameType != null ? frameType.getWidthMm() : null, DEFAULT_FRAME_WIDTH_MM);
        double frameD = dim(frameType != null ? frameType.getDepthMm() : null, DEFAULT_FRAME_DEPTH_MM);
        double sectionDepthMm = dim(shortType != null ? shortType.getWidthMm() : null, DEFAULT_SECTION_DEPTH_MM);
        double baseThickness = dim(shortType != null ? shortType.getDepthMm() : null, DEFAULT_FRAME_DEPTH_MM);
        double peremychkaThickness = dim(shortType != null ? shortType.getDepthMm() : null, DEFAULT_FRAME_DEPTH_MM);
        double spacing = screen.getStructureTowerSpacingMm();
        // Зазор до экрана -- камера по умолчанию стоит со стороны ПОЛОЖИТЕЛЬНОГО Z и смотрит
        // на центр сцены, то есть большее Z означает БЛИЖЕ к зрителю -- конструктив на
        // ОТРИЦАТЕЛЬНОМ Z, дальше от зрителя, чем лицевая грань экрана (z от -15 до +15).
        // Round 3 (узкая грань рамы к зрителю, см. drawTowerSegment): широкая грань рамы
        // (frameW, ~500мм) теперь уходит В ГЛУБИНУ вдоль Z, поэтому зазор считается от неё,
        // а не от узкой frameD (~51мм), как было раньше -- иначе ближний край рамы вылезал бы
        // за пределы предполагаемого зазора.
        double frontZ = -(SCREEN_CLEARANCE_MM + frameW / 2.0);
        // Round 4 (баг-репорт: "рамы не могут пересекаться или накладываться"): раньше здесь
        // было backZ = frontZ - sectionDepthMm -- расстояние МЕЖДУ ЦЕНТРАМИ переднего и
        // заднего ряда, что при развёрнутой (широкой по Z) раме почти или полностью хоронило
        // задний ряд ВНУТРИ переднего (frameW сопоставим с sectionDepthMm). Теперь
        // sectionDepthMm -- это ЧИСТЫЙ ЗАЗОР между гранями рядов (там и стоит перемычка, см.
        // peremychkaCandidates), поэтому вычитаем ещё и frameW переднего ряда, прежде чем
        // сдвигаться на sectionDepthMm.
        double backZ = frontZ - frameW - sectionDepthMm;
        double peremychkaIntervalMm = StructureCalc.peremychkaIntervalMm(frameH);
        return new StructureGeometry(frameH, frameW, frameD, sectionDepthMm, baseThickness, peremychkaThickness,
                spacing, frontZ, backZ, peremychkaIntervalMm);
    }

    private static double dim(Double value, double fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    /** Диапазон номинальной сетки РАСШИРЕННЫЙ на один шаг с каждой стороны по каждой из трёх
     *  осей, чтобы всегда было видно, куда навести курсор для "добавить лишнюю башню сбоку"/
     *  "добавить сегмент повыше"/"добавить перемычку выше"/"добавить секцию выноса дальше". */
    private record FrameEnvelope(int minTower, int maxTower, int maxSegment, int maxLevel, int maxSection) {
    }

    private FrameEnvelope frameEnvelope(Screen screen) {
        int towerCount = Math.max(0, screen.getStructureTowerCount());
        int verticalPerTower = Math.max(0, screen.getStructureVerticalFramesPerTower());
        int peremychkaLevels = Math.max(0, screen.getStructurePeremychkaLevels());
        int baseSections = 1 + Math.max(0, screen.getStructureExtendedBaseSections());
        int minTower = 0;
        int maxTower = towerCount - 1;
        int maxSeg = verticalPerTower - 1;
        for (StructureFrameCell c : screen.getStructureFrameCells()) {
            minTower = Math.min(minTower, c.getTowerIndex());
            maxTower = Math.max(maxTower, c.getTowerIndex());
            maxSeg = Math.max(maxSeg, c.getSegmentIndex());
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
        return new FrameEnvelope(minTower - 1, maxTower + 1, maxSeg + 1, maxLevel + 1, maxSection + 1);
    }

    private record PickKey(String kind, int a, int b, int c) {
    }

    /** {@code bounds} — {x,y,z,w,h,d}, номинальный прямоугольник ячейки (контур для
     *  "призраков"). {@code pickBoxes} — чем ИМЕННО тестируется луч (для существующих
     *  вертикальных сегментов — две тонкие коробки стоек, иначе совпадает с {@code bounds}). */
    private record Candidate(PickKey key, double[] bounds, List<double[]> pickBoxes, boolean exists,
            Runnable toggle) {
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
        double fallbackH = reinforcement ? StructureCalc.DEFAULT_REINFORCEMENT_FRAME_HEIGHT_MM : g.frameH();
        if (override == null) {
            return new double[]{g.frameW(), fallbackH, g.frameD()};
        }
        return new double[]{dim(override.getWidthMm(), g.frameW()), dim(override.getHeightMm(), fallbackH),
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
        FrameEnvelope env = frameEnvelope(screen);
        List<StructureFrameCell> cells = screen.getStructureFrameCells();
        List<Candidate> result = new ArrayList<>();
        for (int t = env.minTower(); t <= env.maxTower(); t++) {
            for (int row = 0; row < 2; row++) {
                double zCenter = row == 0 ? g.frontZ() : g.backZ();
                for (int seg = 0; seg <= env.maxSegment(); seg++) {
                    final int ft = t;
                    final int frow = row;
                    final int fs = seg;
                    StructureFrameCell existingCell = cells.stream()
                            .filter(c -> c.matches(ft, frow, fs) && !c.isHidden()).findFirst().orElse(null);
                    boolean exists = existingCell != null;
                    double[] dims = resolveFrameDims(existingCell, g, false);
                    double x = t * g.spacing() - dims[2] / 2.0;
                    double y = seg * g.frameH();
                    double z = zCenter - dims[0] / 2.0;
                    double[] bounds = {x, y, z, dims[2], dims[1], dims[0]};
                    double rail = railThickness(dims[0]);
                    List<double[]> pickBoxes = exists
                            ? List.of(new double[]{x, y, z, dims[2], dims[1], rail},
                                    new double[]{x, y, z + dims[0] - rail, dims[2], dims[1], rail})
                            : List.of(bounds);
                    String newCellType = activeNewCellFrameTypeId;
                    result.add(new Candidate(new PickKey("frame", ft, frow, fs), bounds, pickBoxes, exists,
                            () -> model.toggleStructureFrameCell(screen, ft, frow, fs, newCellType)));
                }
            }
        }
        return result;
    }

    /** Усилительные рамы выноса (row == 2 в {@link Screen#getStructureFrameCells()}, Phase 2.2
     *  — "1 метровая рама на секцию, вкл/выкл как всё остальное") — по одной на КАЖДУЮ секцию
     *  выноса (section 0 = ядро, там не нужна — уже есть полноразмерный ряд 0/1), центрирована
     *  по глубине секции. */
    private List<Candidate> reinforcementCandidates(Screen screen, StructureGeometry g) {
        FrameEnvelope env = frameEnvelope(screen);
        List<StructureFrameCell> cells = screen.getStructureFrameCells();
        List<Candidate> result = new ArrayList<>();
        for (int t = env.minTower(); t <= env.maxTower(); t++) {
            for (int section = 1; section <= env.maxSection(); section++) {
                final int ft = t;
                final int fsec = section;
                StructureFrameCell existingCell = cells.stream()
                        .filter(c -> c.matches(ft, 2, fsec) && !c.isHidden()).findFirst().orElse(null);
                boolean exists = existingCell != null;
                double[] dims = resolveFrameDims(existingCell, g, true);
                double x = t * g.spacing() - dims[2] / 2.0;
                double[] zr = baseSectionZRange(g, section);
                double sectionCenterZ = (zr[0] + zr[1]) / 2.0;
                double z = sectionCenterZ - dims[0] / 2.0;
                double[] bounds = {x, 0, z, dims[2], dims[1], dims[0]};
                String newCellType = activeNewCellFrameTypeId;
                result.add(new Candidate(new PickKey("reinforcement", ft, fsec, 0), bounds, List.of(bounds), exists,
                        () -> model.toggleStructureFrameCell(screen, ft, 2, fsec, newCellType)));
            }
        }
        return result;
    }

    /** Перемычка заполняет РОВНО зазор между гранями переднего и заднего ряда (не залезает ни
     *  в один из них — "рамы не могут пересекаться", баг-репорт) — от дальней (по Z) грани
     *  переднего ряда до ближней грани заднего, длина зазора = {@code sectionDepthMm} (ширина
     *  короткой рамы). Крепится к перекладине переднего/заднего ряда по высоте — {@code y}
     *  формула ({@code peremychkaIntervalMm} = 1.5×frameH) уже гарантирует попадание ровно на
     *  перекладину, см. {@code drawTowerSegment} javadoc. */
    private List<Candidate> peremychkaCandidates(Screen screen, StructureGeometry g) {
        FrameEnvelope env = frameEnvelope(screen);
        var cells = screen.getStructurePeremychkaCells();
        double gapStart = g.frontZ() - g.frameW() / 2.0 - g.sectionDepthMm();
        double gapSpan = g.sectionDepthMm();
        List<Candidate> result = new ArrayList<>();
        for (int t = env.minTower(); t <= env.maxTower(); t++) {
            for (int level = 0; level <= env.maxLevel(); level++) {
                final int ft = t;
                final int fl = level;
                boolean exists = cells.stream().anyMatch(c -> c.matches(ft, fl) && !c.isHidden());
                double x = t * g.spacing() - g.frameW() / 2.0;
                double y = (level + 1) * g.peremychkaIntervalMm() - g.peremychkaThickness() / 2.0;
                double[] bounds = {x, y, gapStart, g.frameW(), g.peremychkaThickness(), gapSpan};
                result.add(new Candidate(new PickKey("peremychka", ft, fl, 0), bounds, List.of(bounds), exists,
                        () -> model.toggleStructurePeremychkaCell(screen, ft, fl)));
            }
        }
        return result;
    }

    /** Диапазон Z одной секции опорной рамы — {@code {frontZ, backZ}} (оба отрицательные,
     *  frontZ ближе к экрану). Секция 0 ("ядро") — ЕДИНАЯ плита под ВСЕЙ объёмной частью
     *  башни: от ближней грани переднего ряда до дальней грани заднего (round 4 — раньше
     *  секция 0 занимала только зазор между рядами и физически ПЕРЕСЕКАЛАСЬ с обоими рядами,
     *  "рамы не могут пересекаться"). Секции 1+ — вынос той же глубины {@code sectionDepthMm},
     *  что и зазор между рядами, продолжают ядро назад. */
    private static double[] baseSectionZRange(StructureGeometry g, int section) {
        double coreNearZ = g.frontZ() + g.frameW() / 2.0;
        double coreFarZ = g.backZ() - g.frameW() / 2.0;
        if (section <= 0) {
            return new double[]{coreNearZ, coreFarZ};
        }
        double frontZ = coreFarZ - (section - 1) * g.sectionDepthMm();
        return new double[]{frontZ, frontZ - g.sectionDepthMm()};
    }

    private List<Candidate> baseCandidates(Screen screen, StructureGeometry g) {
        FrameEnvelope env = frameEnvelope(screen);
        var cells = screen.getStructureBaseFrameCells();
        List<Candidate> result = new ArrayList<>();
        for (int t = env.minTower(); t <= env.maxTower(); t++) {
            for (int section = 0; section <= env.maxSection(); section++) {
                final int ft = t;
                final int fsec = section;
                boolean exists = cells.stream().anyMatch(c -> c.matches(ft, fsec) && !c.isHidden());
                double x = t * g.spacing() - g.frameW() / 2.0;
                double[] zr = baseSectionZRange(g, section);
                double sectionSpan = zr[0] - zr[1];
                double[] bounds = {x, -g.baseThickness(), zr[1], g.frameW(), g.baseThickness(), sectionSpan};
                result.add(new Candidate(new PickKey("base", ft, fsec, 0), bounds, List.of(bounds), exists,
                        () -> model.toggleStructureBaseFrameSection(screen, ft, fsec)));
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
        return all;
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
        List<Candidate> candidates = allCandidates(screen, g);
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
                        Double t = StructurePickMath.intersectAabb(ray, new Vec3(b[0], b[1], b[2]),
                                new Vec3(b[0] + b[3], b[1] + b[4], b[2] + b[5]));
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

    private void handleClick(int pixelX, int pixelY) {
        Screen screen = model.getCurrentScreen();
        if (screen == null || screen.getMountType() != ScreenMountType.STRUCTURE) {
            return;
        }
        StructureGeometry g = computeGeometry(screen);
        Candidate best = raycastAtCursor(pixelX, pixelY, screen, g, null);
        if (best != null) {
            best.toggle().run();
            hoveredGhostKey = null;
            gljPanel.repaint();
        }
    }

    private void updateHover(int pixelX, int pixelY) {
        Screen screen = model.getCurrentScreen();
        if (screen == null || screen.getMountType() != ScreenMountType.STRUCTURE) {
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

            CameraState cam = computeCamera();
            glu.gluLookAt(cam.eye().x(), cam.eye().y(), cam.eye().z(),
                    cam.center().x(), cam.center().y(), cam.center().z(), 0, 1, 0);

            // Земля лежит НИЖЕ опорной рамы (не пересекает её, см. javadoc drawGroundPlane) --
            // если экран/конструктив ещё не выбраны, опорной рамы не будет и толщина не важна.
            double baseThicknessMm = screen != null && screen.getMountType() == ScreenMountType.STRUCTURE
                    ? computeGeometry(screen).baseThickness() : 0;
            drawGroundPlane(gl, Math.max(widthMm, heightMm) * 1.5, baseThicknessMm);
            if (screen != null && type != null) {
                drawScreenCabinets(gl, screen, type, model.getWorkspace(), elevationMm);
            } else {
                drawScreenPlaneFallback(gl, widthMm, heightMm, elevationMm);
            }
            if (screen != null && screen.getMountType() == ScreenMountType.STRUCTURE) {
                drawStructure(gl, screen);
            }
        }

        /** Земля лежит НИЖЕ опорной рамы, а не вперемешку с ней по Y (баг-репорт: "опорные рамы
         *  пересекаются с полом, рендерятся с переменным успехом" -- classic Z-fighting: земля
         *  раньше всегда занимала Y=[-20,0], опорная рама -- Y=[-baseThicknessMm,0], полностью
         *  перекрываясь). {@code groundTopY = -baseThicknessMm} -- поверхность земли ровно под
         *  нижней гранью опорной рамы, без нахлёста ни на пиксель. */
        private void drawGroundPlane(GL2 gl, double halfExtentMm, double baseThicknessMm) {
            double groundTopY = -baseThicknessMm;
            gl.glColor3f(0.16f, 0.17f, 0.19f);
            box(gl, -halfExtentMm, groundTopY - 20, -halfExtentMm * 0.3, halfExtentMm * 2, 20, halfExtentMm * 1.6);
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
            MaskColorPreset mask = screen.getBackground() != null ? screen.getBackground() : MaskColorPreset.NORMAL;
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
                double x = cab.getColIndex() * cellW;
                double yTop = totalHeight - cab.getRowIndex() * cellH;
                java.awt.Color front = mask.color((cab.getRowIndex() + cab.getColIndex()) % 2);
                boxFrontColored(gl, x, elevationMm + yTop - h, -15, w, h, 15, front, CABINET_CHASSIS_COLOR);
            }
        }

        private void drawStructure(GL2 gl, Screen screen) {
            StructureGeometry g = computeGeometry(screen);
            List<Candidate> frames = frameCandidates(screen, g);
            List<Candidate> peremychki = peremychkaCandidates(screen, g);
            List<Candidate> bases = baseCandidates(screen, g);
            List<Candidate> reinforcements = reinforcementCandidates(screen, g);

            gl.glColor3f(0.6f, 0.61f, 0.63f);
            for (Candidate c : frames) {
                double[] b = c.bounds();
                if (c.exists()) {
                    drawTowerSegment(gl, b[0], b[1], b[2], b[3], b[4], b[5]);
                } else if (c.key().equals(hoveredGhostKey)) {
                    drawWireBoxGhost(gl, b[0], b[1], b[2], b[3], b[4], b[5]);
                }
            }

            gl.glColor3f(0.58f, 0.59f, 0.61f);
            for (Candidate c : reinforcements) {
                double[] b = c.bounds();
                if (c.exists()) {
                    drawTowerSegment(gl, b[0], b[1], b[2], b[3], b[4], b[5]);
                } else if (c.key().equals(hoveredGhostKey)) {
                    drawWireBoxGhost(gl, b[0], b[1], b[2], b[3], b[4], b[5]);
                }
            }

            for (Candidate c : peremychki) {
                double[] b = c.bounds();
                if (c.exists()) {
                    drawPeremychkaFrame(gl, b[0], b[1], b[2], b[3], b[4], b[5]);
                } else if (c.key().equals(hoveredGhostKey)) {
                    drawWireBoxGhost(gl, b[0], b[1], b[2], b[3], b[4], b[5]);
                }
            }

            for (Candidate c : bases) {
                double[] b = c.bounds();
                if (c.exists()) {
                    drawBaseFrame(gl, b[0], b[1], b[2], b[3], b[4], b[5]);
                } else if (c.key().equals(hoveredGhostKey)) {
                    drawWireBoxGhost(gl, b[0], b[1], b[2], b[3], b[4], b[5]);
                }
            }

            drawCups(gl, screen, g);
        }

        /** Стаканы (соединители) на РЕАЛЬНЫХ стыках — та же логика смежности, что
         *  {@code StructureCalc.compute} использует для подсчёта cupCount, просто как картинка:
         *  вертикальные стыки (в каждом ряду отдельно), стыки перемычек (к переднему/заднему
         *  посту) и стыки базовых секций (к посту над секцией 0 / к соседней секции).
         *
         * <p>Round 3: у вертикальных/усилительных рам рельсы теперь стоят у БЛИЖНЕГО/ДАЛЬНЕГО
         *  по Z края (см. {@link #drawTowerSegment}), не слева/справа по X — стыковые стаканы
         *  на них передвинуты туда же. Стаканы перемычек/базовых секций остались X-based —
         *  геометрия самих перемычек и баз в этом раунде не менялась (см. {@code
         *  #drawPeremychkaFrame}), там стыки по-прежнему у соответствующих рельсов. */
        private void drawCups(GL2 gl, Screen screen, StructureGeometry g) {
            double tube = Math.min(40, g.frameD());
            double cupSize = tube * 1.5;
            double frameRail = railThickness(g.frameW());
            gl.glColor3f(CUP_COLOR.getRed() / 255f, CUP_COLOR.getGreen() / 255f, CUP_COLOR.getBlue() / 255f);

            java.util.Map<String, SortedSet<Integer>> segmentsByTowerRow = new TreeMap<>();
            for (StructureFrameCell c : screen.getStructureFrameCells()) {
                if (!c.isHidden()) {
                    segmentsByTowerRow.computeIfAbsent(c.getTowerIndex() + ":" + c.getRow(), k -> new TreeSet<>())
                            .add(c.getSegmentIndex());
                }
            }
            for (var entry : segmentsByTowerRow.entrySet()) {
                String[] key = entry.getKey().split(":");
                int tower = Integer.parseInt(key[0]);
                int row = Integer.parseInt(key[1]);
                double towerX = tower * g.spacing();
                double zCenter = row == 0 ? g.frontZ() : g.backZ();
                double cupX = towerX - cupSize / 2.0;
                double nearCupZ = zCenter - g.frameW() / 2.0 + frameRail / 2.0 - cupSize / 2.0;
                double farCupZ = zCenter + g.frameW() / 2.0 - frameRail / 2.0 - cupSize / 2.0;
                for (int seg : entry.getValue()) {
                    if (entry.getValue().contains(seg + 1)) {
                        double y = (seg + 1) * g.frameH() - cupSize / 2.0;
                        box(gl, cupX, y, nearCupZ, cupSize, cupSize, cupSize);
                        box(gl, cupX, y, farCupZ, cupSize, cupSize, cupSize);
                    }
                }
            }

            double gapNearZ = g.frontZ() - g.frameW() / 2.0;
            double gapFarZ = g.backZ() + g.frameW() / 2.0;
            for (var c : screen.getStructurePeremychkaCells()) {
                if (c.isHidden()) {
                    continue;
                }
                double towerX = c.getTowerIndex() * g.spacing();
                double y = (c.getLevelIndex() + 1) * g.peremychkaIntervalMm() - cupSize / 2.0;
                box(gl, towerX - g.frameW() / 2.0 + tube / 2.0 - cupSize / 2.0, y,
                        gapNearZ - cupSize / 2.0, cupSize, cupSize, cupSize);
                box(gl, towerX - g.frameW() / 2.0 + tube / 2.0 - cupSize / 2.0, y,
                        gapFarZ - cupSize / 2.0, cupSize, cupSize, cupSize);
            }

            for (var c : screen.getStructureBaseFrameCells()) {
                if (c.isHidden()) {
                    continue;
                }
                double towerX = c.getTowerIndex() * g.spacing();
                double sectionFrontZ = baseSectionZRange(g, c.getSectionIndex())[0];
                box(gl, towerX - g.frameW() / 2.0 + tube / 2.0 - cupSize / 2.0, -cupSize / 2.0,
                        sectionFrontZ - cupSize / 2.0, cupSize, cupSize, cupSize);
                box(gl, towerX + g.frameW() / 2.0 - tube / 2.0 - cupSize / 2.0, -cupSize / 2.0,
                        sectionFrontZ - cupSize / 2.0, cupSize, cupSize, cupSize);
            }

            // Усилительные рамы выноса (row == 2) -- по одной паре стаканов в основании,
            // соединяющей с базовой секцией под ней (не стоят в стеке, см. class-javadoc) --
            // рельсы усилительной рамы тоже у ближнего/дальнего Z-края (см. drawTowerSegment).
            for (StructureFrameCell c : screen.getStructureFrameCells()) {
                if (c.isHidden() || c.getRow() != 2) {
                    continue;
                }
                double towerX = c.getTowerIndex() * g.spacing();
                double[] zr = baseSectionZRange(g, c.getSegmentIndex());
                double reinforcementZCenter = (zr[0] + zr[1]) / 2.0;
                double cupX = towerX - cupSize / 2.0;
                double nearZ = reinforcementZCenter - g.frameW() / 2.0 + frameRail / 2.0 - cupSize / 2.0;
                double farZ = reinforcementZCenter + g.frameW() / 2.0 - frameRail / 2.0 - cupSize / 2.0;
                box(gl, cupX, -cupSize / 2.0, nearZ, cupSize, cupSize, cupSize);
                box(gl, cupX, -cupSize / 2.0, farZ, cupSize, cupSize, cupSize);
            }
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

        /** Перемычка (и, тем же приёмом, опорная рама — см. перегрузку ниже) — та же самая
         *  рама (см. {@link #drawTowerSegment}), просто повёрнутая на 90°: "длинная" ось лежит
         *  вдоль Z, а не вдоль Y — 2 рельса по краям X-ширины через весь span, плюс те же 3
         *  перекладины (у обоих концов span и посередине), тот же принцип "8-образной" формы.
         *  {@code x}/{@code y}/{@code z} — левый-нижний-задний угол bounds (как у остальных
         *  candidate). */
        private void drawPeremychkaFrame(GL2 gl, double x, double y, double z, double frameW, double thickness,
                double span) {
            drawHorizontalLadderFrame(gl, x, y, z, frameW, thickness, span, 0.55f, 0.5f, 0.42f, 0.5f, 0.46f, 0.39f);
        }

        /** Опорная (базовая) рама — тот же горизонтальный "8"-каркас, что у перемычки (round 4,
         *  баг-репорт "перемычки и основания — горизонтальные рамы, крепящиеся к перекладине",
         *  раньше опора рисовалась сплошной плитой) — только серая, под цвет вертикальных рам,
         *  а не бежевая (не путать визуально с перемычкой). */
        private void drawBaseFrame(GL2 gl, double x, double y, double z, double frameW, double thickness,
                double span) {
            drawHorizontalLadderFrame(gl, x, y, z, frameW, thickness, span, 0.42f, 0.43f, 0.45f, 0.37f, 0.38f, 0.4f);
        }

        private void drawHorizontalLadderFrame(GL2 gl, double x, double y, double z, double frameW, double thickness,
                double span, float railR, float railG, float railB, float crossR, float crossG, float crossB) {
            double rail = railThickness(frameW);
            double crossThickness = rail * 0.7;
            gl.glColor3f(railR, railG, railB);
            box(gl, x, y, z, rail, thickness, span);
            box(gl, x + frameW - rail, y, z, rail, thickness, span);

            gl.glColor3f(crossR, crossG, crossB);
            double crossX = x + rail;
            double crossW = frameW - 2 * rail;
            box(gl, crossX, y, z, crossW, thickness, crossThickness);
            box(gl, crossX, y, z + span / 2.0 - crossThickness / 2.0, crossW, thickness, crossThickness);
            box(gl, crossX, y, z + span - crossThickness, crossW, thickness, crossThickness);
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
