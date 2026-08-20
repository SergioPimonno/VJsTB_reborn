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

    /** Внешний хук перерисовки (см. {@code Structure3DDialog#refresh}) — эта панель НЕ
     *  подписывается на {@code AppModel.addListener} сама (у {@code AppModel} нет
     *  {@code removeListener}, а новая панель создаётся при каждом открытии диалога, так что
     *  подписка бы утекала) — вызывающий код (см. {@code SetupStagePanel#calculateStructure})
     *  явно дёргает этот метод после пересчёта конструктива, если диалог уже открыт, чтобы
     *  показанная модель не расходилась с только что сохранёнными значениями. */
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
            double baseThickness, double peremychkaThickness, double spacing,
            double frontZ, double backZ, double peremychkaIntervalMm, double reinforcementHeightMm) {
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
        // ОТРИЦАТЕЛЬНОМ Z, дальше от зрителя, чем лицевая грань экрана (z от -15 до +15).
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
        return new StructureGeometry(frameH, frameW, frameD, sectionDepthMm, baseThickness,
                peremychkaThickness, spacing, frontZ, backZ, peremychkaIntervalMm, reinforcementHeightMm);
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
        return new FrameEnvelope(minTower - 1, maxTower + 1, maxFrontSeg + 1, maxBackSeg + 1, maxLevel + 1,
                maxSection + 1);
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
     *  выноса (ядро, {@code [0, CORE_BASE_SECTION_COUNT)}, там не нужна — уже есть
     *  полноразмерный ряд 0/1, см. {@code StructureCalc#CORE_BASE_SECTION_COUNT}), центрирована
     *  по глубине секции. */
    private List<Candidate> reinforcementCandidates(Screen screen, StructureGeometry g) {
        FrameEnvelope env = frameEnvelope(screen, g);
        List<StructureFrameCell> cells = screen.getStructureFrameCells();
        List<Candidate> result = new ArrayList<>();
        int coreSections = StructureCalc.CORE_BASE_SECTION_COUNT;
        for (int t = env.minTower(); t <= env.maxTower(); t++) {
            for (int section = coreSections; section <= env.maxSection(); section++) {
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
                        () -> model.toggleStructureFrameCell(screen, ft, 2, fsec, newCellType)));
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
            for (int row = 0; row < 2; row++) {
                double zCenter = row == 0 ? g.frontZ() : g.backZ();
                // Баг-репорт: "изменение типа рамы не влияет на перемычки, а должно, башня
                // может быть уже за счёт коротких рам" -- зазор/глубина считались от НОМИНАЛЬНОГО
                // типа экрана (g.frameD()/g.frameW()), даже если конкретная стойка соседней башни
                // переопределена per-cell на другой (например, более узкий) тип. Берём реальные
                // габариты СТОЕК этого зазора (resolveTowerPostDims), а не номинал.
                double[] leftDims = resolveTowerPostDims(screen, g, gapIdx, row);
                double[] rightDims = resolveTowerPostDims(screen, g, gapIdx + 1, row);
                double xSpan = g.spacing() - leftDims[2] / 2.0 - rightDims[2] / 2.0;
                if (xSpan <= 0) {
                    continue;
                }
                double zDepth = Math.min(leftDims[0], rightDims[0]);
                double rail = railThickness(xSpan);
                for (int level = 0; level <= env.maxLevel(); level++) {
                    final int fg = gapIdx;
                    final int fr = row;
                    final int fl = level;
                    boolean exists = cells.stream().anyMatch(c -> c.matches(fg, fr, fl) && !c.isHidden());
                    double x = gapIdx * g.spacing() + leftDims[2] / 2.0;
                    double y = (level + 1) * g.peremychkaIntervalMm() - g.peremychkaThickness() / 2.0;
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
                            () -> model.toggleStructurePeremychkaCell(screen, fg, fr, fl)));
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
            double xSpan = g.spacing() - leftD / 2.0 - rightD / 2.0;
            if (xSpan <= 0) {
                continue;
            }
            double rail = railThickness(xSpan);
            for (int section = 0; section <= env.maxSection(); section++) {
                final int fg = gapIdx;
                final int fsec = section;
                boolean exists = cells.stream().anyMatch(c -> c.matches(fg, fsec) && !c.isHidden());
                double x = gapIdx * g.spacing() + leftD / 2.0;
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
                        () -> model.toggleStructureBaseFrameSection(screen, fg, fsec)));
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
                    drawInterTowerPeremychka(gl, b[0], b[1], b[2], b[3], b[4], b[5]);
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
