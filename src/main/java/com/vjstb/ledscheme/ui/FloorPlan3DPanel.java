package com.vjstb.ledscheme.ui;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.glu.GLU;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.FloorFrameCell;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.FloorCalc;
import com.vjstb.ledscheme.service.FloorPickMath;
import com.vjstb.ledscheme.service.FloorPickMath.Ray;
import com.vjstb.ledscheme.service.FloorPickMath.Vec3;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * 3D-редактор напольного каркаса — запрос пользователя 2026-10-01: «для пола давай сделаем
 * переключаемый режим отображения — либо 2D схема как сейчас, либо 3D редактор как для
 * конструктива». Живёт во второй вкладке-режиме окна плана пола ({@link FloorPlanViewPanel},
 * переключатель «2D схема» / «3D редактор»).
 *
 * <p><b>Что рисуется</b> (снизу вверх, система координат — см. {@link FloorPickMath}): земля;
 * ножки — 4 на раму, условной высоты {@link FloorCalc#DISPLAY_LEG_HEIGHT_MM} (в расчёте высота
 * не участвует); рамы, лежащие плашмя, — «лестница» из двух длинных рельсов и трёх перекладин
 * (как рама конструктива, только горизонтально), реальные габариты из библиотеки; стаканы —
 * красные кубики в зазоре на стыках коротких сторон, по 2 на стык; плитки кабинетов — верх в
 * цвет маски экрана (шахматка {@code Screen#maskColor}), НЕОПЁРТЫЕ — оранжевые, как на
 * 2D-плане; зубы — маленькие светлые метки по углам опёртых кабинетов (2/3/4 по настройке
 * экрана), без перегруза картинки. Скрытые кабинеты формы экрана не рисуются вовсе. Плитки
 * можно выключить флажком «Кабинеты» — тогда видны рамы, стаканы и зубы на рамах.
 *
 * <p><b>Редактирование — как у конструктива</b> ({@code Structure3DPanel}, Phase 2 + Round
 * 24): ЛКМ-клик по раме (или по плитке над ней) прячет раму ({@code hidden}, запись не
 * удаляется); с зажатым Ctrl под курсором показывается зелёный «призрак» допустимой позиции,
 * Ctrl+клик возвращает/добавляет раму. Без Ctrl призраков нет вовсе — клик не может
 * «промахнуться» в призрак. Наведение без Ctrl подсвечивает красным контуром раму, которую
 * уберёт клик. После каждой правки весь расчёт ({@link FloorCalc#compute}) берётся заново —
 * стыки, стаканы, болты, ножки, зубы, неопёртые кабинеты; 2D-план и сводка того же окна
 * обновляются через слушателя модели. Отмена — Ctrl+Z ({@code AppModel#toggleFloorFrameCell}
 * пишет запись отмены).
 *
 * <p><b>Мышь</b>: ЛКМ — клик (picking); перетаскивание ЛКМ или ПКМ — орбита (смещение больше
 * {@link #CLICK_MOVE_THRESHOLD_PX} — это вращение, не клик, тот же приём, что Phase 2
 * конструктива); средняя кнопка — панорамирование; колесо — зум.
 *
 * <p><b>Новый класс, а не правка {@code Structure3DPanel}</b>: тот параллельно переписывается в
 * другой ветке (claude/curve). Небольшие куски (box/wire-box, формула орбиты) продублированы
 * сознательно. Picking — {@link FloorPickMath#pickTarget} (чистая функция, покрыта тестами);
 * камера и геометрия считаются одними и теми же методами для рендера и для клика.
 *
 * <p><b>Без GL</b> (headless, нет нативов JOGL — macOS/Linux, см. pom.xml) конструктор ловит
 * {@code Throwable} и показывает ту же текстовую заглушку «3D недоступно на этой системе», что
 * {@code Structure3DPanel}; 2D-режим окна при этом работает как раньше.
 */
public class FloorPlan3DPanel extends JPanel {

    private static final double FOV_DEG = 45;
    private static final int CLICK_MOVE_THRESHOLD_PX = 4;
    private static final double LEG_SIZE_MM = 40;
    private static final double CABINET_GAP_MM = 4;
    private static final double TOOTH_SIZE_MM = 36;
    private static final double TOOTH_HEIGHT_MM = 10;
    private static final double TOOTH_INSET_MM = 70;
    private static final Color CUP_COLOR = FloorPlanPanel.CUP_FILL;
    private static final Color CHASSIS_COLOR = new Color(0x2a, 0x2c, 0x30);
    private static final Color TOOTH_COLOR = new Color(0xE8, 0xE4, 0xA0);

    private final AppModel model;
    private final Screen screen;
    private GLJPanel gljPanel;
    private double yawDeg = -25;
    private double pitchDeg = 35;
    private double distanceMm = -1; // < 0 — подобрать по размеру пола при первом кадре
    private double panXMm;
    private double panZMm;
    private int lastMouseX;
    private int lastMouseY;
    private int pressX;
    private int pressY;
    private boolean showCabinets = true;
    private volatile FloorCalc.FramePlacement hoveredGhost;
    private volatile FloorCalc.FramePlacement hoveredFrame;
    private Consumer<String> statusListener = s -> { };

    /**
     * @param tryGl {@code false} — сразу заглушка без попытки создать GL (тесты, headless);
     *              в headless-среде GL не пробуется в любом случае.
     */
    public FloorPlan3DPanel(AppModel model, Screen screen, boolean tryGl) {
        super(new BorderLayout());
        this.model = model;
        this.screen = screen;
        setPreferredSize(new Dimension(800, 520));
        if (!tryGl || GraphicsEnvironment.isHeadless()) {
            add(unavailableLabel(null), BorderLayout.CENTER);
            return;
        }
        try {
            GLProfile profile = GLProfile.get(GLProfile.GL2);
            GLCapabilities caps = new GLCapabilities(profile);
            caps.setDepthBits(24);
            gljPanel = new GLJPanel(caps);
            gljPanel.addGLEventListener(new Renderer());
            wireMouse(gljPanel);
            add(gljPanel, BorderLayout.CENTER);
        } catch (Throwable t) {
            gljPanel = null;
            add(unavailableLabel(t), BorderLayout.CENTER);
        }
    }

    /** {@code true}, если GL-вид создан (иначе показана заглушка). */
    public boolean isGlAvailable() {
        return gljPanel != null;
    }

    /** Подсказки/отказы редактора (например, «экран не текущий») — в строку состояния окна. */
    public void setStatusListener(Consumer<String> listener) {
        this.statusListener = listener != null ? listener : s -> { };
    }

    public void setShowCabinets(boolean show) {
        this.showCabinets = show;
        hoveredFrame = null;
        hoveredGhost = null;
        refresh();
    }

    public boolean isShowCabinets() {
        return showCabinets;
    }

    /** Стандартный вид (кнопки «Сверху»/«Спереди»/«Сбоку» окна) — меняется только угол. */
    public void setViewAngle(double yawDeg, double pitchDeg) {
        this.yawDeg = yawDeg;
        this.pitchDeg = Math.max(5, Math.min(89, pitchDeg));
        refresh();
    }

    public void refresh() {
        if (gljPanel != null) {
            gljPanel.repaint();
        }
    }

    private static JLabel unavailableLabel(Throwable t) {
        JLabel msg = new JLabel("<html><center>3D недоступно на этой системе"
                + (t != null ? "<br>(" + t.getClass().getSimpleName() + ")" : "")
                + "<br>Переключитесь на «2D схема».</center></html>", SwingConstants.CENTER);
        msg.setForeground(Palette.MUTED);
        return msg;
    }

    private void wireMouse(GLJPanel gl) {
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
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    pan(dx, dy);
                } else {
                    yawDeg -= dx * 0.4;
                    pitchDeg = Math.max(5, Math.min(89, pitchDeg + dy * 0.4));
                }
                gl.repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY(), e.isControlDown());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                int moved = Math.abs(e.getX() - pressX) + Math.abs(e.getY() - pressY);
                if (moved <= CLICK_MOVE_THRESHOLD_PX) {
                    handleClick(e.getX(), e.getY(), e.isControlDown());
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoveredGhost != null || hoveredFrame != null) {
                    hoveredGhost = null;
                    hoveredFrame = null;
                    gl.repaint();
                }
            }
        };
        gl.addMouseListener(mouse);
        gl.addMouseMotionListener(mouse);
        gl.addMouseWheelListener(e -> {
            distanceMm = currentDistance() * Math.pow(1.1, e.getWheelRotation());
            distanceMm = Math.max(500, Math.min(200_000, distanceMm));
            gl.repaint();
        });
    }

    // ---- камера: общая для рендера и picking'а ----

    private record Camera(Vec3 eye, Vec3 center) {
    }

    private double floorWidthMm() {
        CabinetType t = model.typeOf(screen);
        return t != null && t.getWidthMm() > 0 ? Math.max(1, screen.getCols()) * t.getWidthMm() : 4000;
    }

    private double floorDepthMm() {
        CabinetType t = model.typeOf(screen);
        return t != null && t.getHeightMm() > 0 ? Math.max(1, screen.getRows()) * t.getHeightMm() : 3000;
    }

    private double currentDistance() {
        if (distanceMm < 0) {
            distanceMm = Math.max(floorWidthMm(), floorDepthMm()) * 1.05 + 1200;
        }
        return distanceMm;
    }

    private Camera camera() {
        double cx = floorWidthMm() / 2.0 + panXMm;
        double cz = floorDepthMm() / 2.0 + panZMm;
        double cy = FloorCalc.DISPLAY_LEG_HEIGHT_MM;
        double d = currentDistance();
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        Vec3 eye = new Vec3(cx + d * Math.cos(pitch) * Math.sin(yaw), cy + d * Math.sin(pitch),
                cz + d * Math.cos(pitch) * Math.cos(yaw));
        return new Camera(eye, new Vec3(cx, cy, cz));
    }

    /** Панорамирование средней кнопкой — сдвиг цели орбиты в плоскости пола по «право»/
     *  «вперёд» камеры (пол горизонтальный, вертикаль не нужна). */
    private void pan(int dxPx, int dyPx) {
        Camera cam = camera();
        Vec3 forward = cam.center().minus(cam.eye());
        Vec3 flatForward = new Vec3(forward.x(), 0, forward.z()).normalized();
        Vec3 right = flatForward.cross(new Vec3(0, 1, 0)).normalized();
        double scale = currentDistance() * 0.0016;
        panXMm += -dxPx * right.x() * scale + dyPx * flatForward.x() * scale;
        panZMm += -dxPx * right.z() * scale + dyPx * flatForward.z() * scale;
    }

    // ---- picking ----

    private double[] hitPoint(int px, int py, FloorCalc.Layout layout) {
        Camera cam = camera();
        double w = Math.max(1, gljPanel.getWidth());
        double h = Math.max(1, gljPanel.getHeight());
        Ray ray = FloorPickMath.cameraRay(cam.eye(), cam.center(), new Vec3(0, 1, 0), FOV_DEG, w / h, px, py, w, h);
        return FloorPickMath.intersectHorizontalPlane(ray, FloorPickMath.topSurfaceY(layout, showCabinets));
    }

    private FloorCalc.FramePlacement pickAt(int px, int py, boolean addMode) {
        FloorCalc.Layout layout = FloorCalc.layout(screen, model.typeOf(screen), model.getWorkspace());
        if (!layout.valid()) {
            return null;
        }
        double[] hit = hitPoint(px, py, layout);
        if (hit == null) {
            return null;
        }
        return FloorPickMath.pickTarget(layout, FloorCalc.effectiveCells(screen, layout), hit[0], hit[1], addMode);
    }

    private void handleClick(int px, int py, boolean addMode) {
        FloorCalc.FramePlacement target = pickAt(px, py, addMode);
        if (target == null) {
            if (addMode) {
                statusListener.accept("Здесь раму поставить нельзя: позиция занята, выходит за край экрана или"
                        + " накрывает скрытый кабинет.");
            }
            return;
        }
        if (model.getCurrentScreen() != screen) {
            statusListener.accept("Правка доступна только для текущего экрана — выберите экран «" + screen.getName()
                    + "» в списке.");
            return;
        }
        if (model.toggleFloorFrameCell(screen, target.row(), target.col())) {
            statusListener.accept(addMode ? "Рама добавлена (Ctrl+Z — отменить)." : "Рама убрана (Ctrl+Z — отменить).");
        }
        hoveredGhost = null;
        hoveredFrame = null;
        refresh();
    }

    private void updateHover(int px, int py, boolean addMode) {
        FloorCalc.FramePlacement target = pickAt(px, py, addMode);
        FloorCalc.FramePlacement ghost = addMode ? target : null;
        FloorCalc.FramePlacement frame = addMode ? null : target;
        if (!Objects.equals(ghost, hoveredGhost) || !Objects.equals(frame, hoveredFrame)) {
            hoveredGhost = ghost;
            hoveredFrame = frame;
            refresh();
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
            gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_AMBIENT, new float[]{0.4f, 0.4f, 0.42f, 1f}, 0);
            gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, new float[]{0.8f, 0.8f, 0.76f, 1f}, 0);
        }

        @Override
        public void dispose(GLAutoDrawable drawable) {
        }

        @Override
        public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
            GL2 gl = drawable.getGL().getGL2();
            gl.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glLoadIdentity();
            glu.gluPerspective(FOV_DEG, (double) Math.max(1, width) / Math.max(1, height), 20, 500_000);
            gl.glMatrixMode(GL2.GL_MODELVIEW);
        }

        @Override
        public void display(GLAutoDrawable drawable) {
            GL2 gl = drawable.getGL().getGL2();
            gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
            gl.glMatrixMode(GL2.GL_MODELVIEW);
            gl.glLoadIdentity();
            Camera cam = camera();
            glu.gluLookAt(cam.eye().x(), cam.eye().y(), cam.eye().z(),
                    cam.center().x(), cam.center().y(), cam.center().z(), 0, 1, 0);
            // Свет — в мировых координатах над полом (позиция задаётся после gluLookAt).
            gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, new float[]{
                    (float) (floorWidthMm() * 0.3), 9000f, (float) (floorDepthMm() * 1.2), 1f}, 0);

            double w = floorWidthMm();
            double d = floorDepthMm();
            gl.glColor3f(0.16f, 0.17f, 0.19f);
            box(gl, -w * 0.15 - 500, -20, -d * 0.15 - 500, w * 1.3 + 1000, 20, d * 1.3 + 1000);

            CabinetType type = model.typeOf(screen);
            FloorCalc.Layout layout = FloorCalc.layout(screen, type, model.getWorkspace());
            if (!layout.valid()) {
                return;
            }
            FloorCalc.Result result = FloorCalc.compute(screen, type, model.getWorkspace());
            List<FloorFrameCell> effective = FloorCalc.effectiveCells(screen, layout);
            double legH = FloorCalc.DISPLAY_LEG_HEIGHT_MM;
            double frameTop = legH + layout.frameDepthMm();

            for (FloorCalc.FramePlacement f : result.frames()) {
                double[] fp = FloorPickMath.frameFootprint(layout, f);
                drawLegs(gl, fp, legH);
                drawFlatFrame(gl, fp, legH, layout.frameDepthMm());
            }
            drawCups(gl, layout, result, legH);

            double surfaceTop = frameTop;
            if (showCabinets) {
                drawCabinets(gl, layout, result, frameTop);
                surfaceTop = frameTop + FloorPickMath.DISPLAY_CABINET_THICKNESS_MM;
            }
            drawTeeth(gl, layout, result, surfaceTop);

            FloorCalc.FramePlacement ghost = hoveredGhost;
            if (ghost != null && FloorCalc.addablePositions(layout, effective).contains(ghost)) {
                double[] fp = FloorPickMath.frameFootprint(layout, ghost);
                wireBox(gl, fp[0], legH, fp[1], fp[2], surfaceTop - legH + 10, fp[3], 0.35f, 0.85f, 0.45f);
            }
            FloorCalc.FramePlacement hovered = hoveredFrame;
            if (hovered != null && result.frames().contains(hovered)) {
                double[] fp = FloorPickMath.frameFootprint(layout, hovered);
                wireBox(gl, fp[0], legH, fp[1], fp[2], surfaceTop - legH + 10, fp[3], 0.95f, 0.3f, 0.3f);
            }
        }

        private void drawLegs(GL2 gl, double[] fp, double legH) {
            gl.glColor3f(0.30f, 0.31f, 0.33f);
            double s = LEG_SIZE_MM;
            double[] xs = {fp[0] + s * 0.25, fp[0] + fp[2] - s * 1.25};
            double[] zs = {fp[1] + s * 0.25, fp[1] + fp[3] - s * 1.25};
            for (double x : xs) {
                for (double z : zs) {
                    box(gl, x, 0, z, s, legH, s);
                }
            }
        }

        /** Рама плашмя — два длинных рельса вдоль X у ближнего/дальнего края + три перекладины
         *  вдоль Z (концы и середина) — та же «8-образная» лестница, что у рамы конструктива. */
        private void drawFlatFrame(GL2 gl, double[] fp, double y, double thickness) {
            double rail = Math.min(Math.max(20, Math.min(60, fp[3] * 0.1)), fp[3] * 0.45);
            gl.glColor3f(0.62f, 0.63f, 0.65f);
            box(gl, fp[0], y, fp[1], fp[2], thickness, rail);
            box(gl, fp[0], y, fp[1] + fp[3] - rail, fp[2], thickness, rail);
            gl.glColor3f(0.55f, 0.56f, 0.58f);
            double cross = rail * 0.8;
            double cz = fp[1] + rail;
            double clen = fp[3] - 2 * rail;
            box(gl, fp[0], y, cz, cross, thickness * 0.9, clen);
            box(gl, fp[0] + fp[2] / 2.0 - cross / 2.0, y, cz, cross, thickness * 0.9, clen);
            box(gl, fp[0] + fp[2] - cross, y, cz, cross, thickness * 0.9, clen);
        }

        /** Стаканы — по 2 на стык коротких сторон, в зазоре между рамами на линии рельсов. */
        private void drawCups(GL2 gl, FloorCalc.Layout layout, FloorCalc.Result result, double legH) {
            gl.glColor3f(CUP_COLOR.getRed() / 255f, CUP_COLOR.getGreen() / 255f, CUP_COLOR.getBlue() / 255f);
            double size = 50;
            for (FloorCalc.Joint j : result.shortSideJoints()) {
                double[] a = FloorPickMath.frameFootprint(layout, j.a());
                double[] b = FloorPickMath.frameFootprint(layout, j.b());
                double gapStart = a[0] + a[2];
                double gapEnd = b[0];
                double cx = (gapStart + gapEnd) / 2.0;
                double cupW = Math.max(size * 0.6, gapEnd - gapStart + 20);
                double[] zs = {a[1] + a[3] * 0.18, a[1] + a[3] * 0.82};
                for (double z : zs) {
                    box(gl, cx - cupW / 2.0, legH - 10, z - size / 2.0, cupW, layout.frameDepthMm() + 20, size);
                }
            }
        }

        private void drawCabinets(GL2 gl, FloorCalc.Layout layout, FloorCalc.Result result, double y) {
            double cw = layout.cabinetWidthMm();
            double ch = layout.cabinetHeightMm();
            double t = FloorPickMath.DISPLAY_CABINET_THICKNESS_MM;
            for (int r = 0; r < layout.rows(); r++) {
                for (int c = 0; c < layout.cols(); c++) {
                    if (!layout.isVisible(r, c)) {
                        continue;
                    }
                    Color top = result.isUnsupported(r, c) ? FloorPlanPanel.UNSUPPORTED_FILL
                            : screen.maskColor((r + c) % 2);
                    boxTopColored(gl, c * cw + CABINET_GAP_MM, y, r * ch + CABINET_GAP_MM,
                            cw - 2 * CABINET_GAP_MM, t, ch - 2 * CABINET_GAP_MM, top, CHASSIS_COLOR);
                }
            }
        }

        /** Зубы — маленькие метки по углам ОПЁРТЫХ кабинетов: 4 — все углы, 3 — три, 2 — по
         *  диагонали. Лежат на верхней поверхности (плитка или рама, если плитки скрыты). */
        private void drawTeeth(GL2 gl, FloorCalc.Layout layout, FloorCalc.Result result, double y) {
            int n = result.teethPerCabinet();
            double cw = layout.cabinetWidthMm();
            double ch = layout.cabinetHeightMm();
            double inset = Math.min(TOOTH_INSET_MM, Math.min(cw, ch) / 4.0);
            double s = TOOTH_SIZE_MM;
            gl.glColor3f(TOOTH_COLOR.getRed() / 255f, TOOTH_COLOR.getGreen() / 255f, TOOTH_COLOR.getBlue() / 255f);
            for (FloorCalc.FramePlacement f : result.frames()) {
                for (int r = f.row(); r < f.row() + f.rowCount(); r++) {
                    for (int c = f.col(); c < f.colEnd(); c++) {
                        double x0 = c * cw + inset - s / 2.0;
                        double x1 = (c + 1) * cw - inset - s / 2.0;
                        double z0 = r * ch + inset - s / 2.0;
                        double z1 = (r + 1) * ch - inset - s / 2.0;
                        double[][] corners = n >= 4 ? new double[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}
                                : n == 3 ? new double[][]{{x0, z0}, {x1, z0}, {x0, z1}}
                                : new double[][]{{x0, z0}, {x1, z1}};
                        for (double[] p : corners) {
                            box(gl, p[0], y, p[1], s, TOOTH_HEIGHT_MM, s);
                        }
                    }
                }
            }
        }

        private void boxTopColored(GL2 gl, double x, double y, double z, double w, double h, double d, Color top,
                Color chassis) {
            gl.glColor3f(chassis.getRed() / 255f, chassis.getGreen() / 255f, chassis.getBlue() / 255f);
            box(gl, x, y, z, w, h - 1, d);
            gl.glColor3f(top.getRed() / 255f, top.getGreen() / 255f, top.getBlue() / 255f);
            float x0 = (float) x;
            float z0 = (float) z;
            float x1 = (float) (x + w);
            float z1 = (float) (z + d);
            float yt = (float) (y + h);
            gl.glBegin(GL2.GL_QUADS);
            gl.glNormal3f(0, 1, 0);
            gl.glVertex3f(x0, yt, z0);
            gl.glVertex3f(x0, yt, z1);
            gl.glVertex3f(x1, yt, z1);
            gl.glVertex3f(x1, yt, z0);
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

        private void wireBox(GL2 gl, double x, double y, double z, double w, double h, double d, float r, float g,
                float b) {
            gl.glDisable(GL2.GL_LIGHTING);
            gl.glLineWidth(2f);
            gl.glColor3f(r, g, b);
            float x0 = (float) x;
            float y0 = (float) y;
            float z0 = (float) z;
            float x1 = (float) (x + w);
            float y1 = (float) (y + h);
            float z1 = (float) (z + d);
            float[][] edges = {
                    {x0, y0, z0, x1, y0, z0}, {x1, y0, z0, x1, y0, z1}, {x1, y0, z1, x0, y0, z1}, {x0, y0, z1, x0, y0, z0},
                    {x0, y1, z0, x1, y1, z0}, {x1, y1, z0, x1, y1, z1}, {x1, y1, z1, x0, y1, z1}, {x0, y1, z1, x0, y1, z0},
                    {x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x1, y0, z1, x1, y1, z1}, {x0, y0, z1, x0, y1, z1}};
            gl.glBegin(GL2.GL_LINES);
            for (float[] e : edges) {
                gl.glVertex3f(e[0], e[1], e[2]);
                gl.glVertex3f(e[3], e[4], e[5]);
            }
            gl.glEnd();
            gl.glLineWidth(1f);
            gl.glEnable(GL2.GL_LIGHTING);
        }
    }
}
