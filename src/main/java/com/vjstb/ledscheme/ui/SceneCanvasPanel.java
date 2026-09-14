package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.ScreenStats;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JPanel;

/**
 * Раскладка всех экранов текущей сцены по их координатам (мм) на одном холсте —
 * обзор взаимного расположения, аналог общего плана сцены.
 */
public class SceneCanvasPanel extends JPanel {

    private static final int PADDING = 40;
    private static final int PADDING_COMPACT = 10;
    private static final int PADDING_DETAIL_FIT = 16;
    private static final double DETAIL_PX_PER_MM = 0.25;
    /** Ниже этой ширины ячейки кабинета детальная сетка/цепочки уже нечитаемы —
     *  показываем упрощённый прямоугольник с названием, как в обычном обзоре. */
    private static final int DETAIL_MIN_CELL_PX = 5;
    /** Заливка сегментов фермы подвеса (см. {@link #drawRiggingTruss}) — высококонтрастный
     *  светло-серый ("металл"), фиксированный (не переключается темой/профилем, как {@link
     *  Palette#WARN}) — прежний {@code Palette.MUTED} на тёмном фоне почти сливался с ним
     *  (баг-репорт "ферму почти не видно"). */
    private static final Color TRUSS_FILL = new Color(0xc9d1d9);
    private final AppModel model;
    private final SettingsManager settings;
    private boolean compact;
    private boolean detailMode;
    private boolean detailPower;
    private boolean detailFit;
    private double detailZoom = 1.0;
    /** Если задан — детальный режим ("Показать все экраны сцены") становится
     *  интерактивным: клик/протяжка по кабинету АКТИВНОГО экрана прописывает
     *  цепочку через тот же контроллер, что и одиночный CanvasPanel, — раньше
     *  «показать все» был только для просмотра, прописать экран в нём было
     *  нельзя, хотя весь смысл режима был именно в этом (видеть всю сцену и
     *  осознанно распределять нагрузку между экранами). Клик по ДРУГОМУ экрану
     *  просто переключает активный, как и раньше. */
    private CanvasPanel.Controller chainController;
    private String lastDragCabId;
    /** Точки подвеса нужны только в мини-превью прерига (Сетап) — в прописи
     *  питания/сигнала (в т.ч. корнер-виджет и «показать все экраны») это уже
     *  просто лишний визуальный шум, не относящийся к текущей задаче. */
    private boolean showRiggingPoints;

    /** v3.0: ферма/точки подвеса рисуются НАД экраном, клик по этой полосе открывает
     *  панель редактирования подвеса в {@code SetupStagePanel} (см. {@link
     *  #rigLevelListener}/{@link #rigLevelAt}) для ЭТОГО экрана, а не двигает его, как
     *  обычный клик. Экран — единственный отмеченный элемент (см. class-javadoc
     *  {@link #rigLevelAt}), поэтому колбэк передаёт только сам экран, без отдельного
     *  "уровня". */
    public interface RigLevelListener {
        void onRigLevelClicked(Screen screen);
    }

    private RigLevelListener rigLevelListener;

    public void setRigLevelListener(RigLevelListener listener) {
        this.rigLevelListener = listener;
    }

    /** Перетаскивание ЦЕЛОГО экрана (Task #7/v1.6) — только когда chainController
     *  не задан (т.е. этот виджет используется как прериг-превью, а не как
     *  интерактивная пропись цепочек Питания/Сигнала — там клик по экрану/кабинету
     *  уже означает совсем другое, см. mousePressed). Перетаскивание — прямая
     *  мутация live-объекта для отклика "на лету" (как и перетаскивание узла схемы/
     *  размещения канваса — SchemaCanvasPanel/CanvasEditorPanel), коммит через
     *  AppModel только один раз по отпусканию кнопки (см. mouseReleased). */
    private Screen draggingScreen;
    private double dragScreenStartX, dragScreenStartY;
    private int dragScreenPressPxX, dragScreenPressPxY;
    private boolean dragScreenMoved;

    /** Перетаскивание ОТДЕЛЬНОГО кабинета (Task #7/v1.6) — доступно только в
     *  detailMode (там вообще видны отдельные кабинеты) и тоже только когда
     *  chainController не задан. Shift во время перетаскивания — привязка к
     *  соседним (по сетке) кабинетам, см. {@link #snapCabinetOffset}. */
    private CabinetInstance draggingCabinet;
    private Screen draggingCabinetScreen;
    private double dragCabStartOffX, dragCabStartOffY;
    private int dragCabPressPxX, dragCabPressPxY;
    private boolean dragCabMoved;
    /** Направляющая привязки кабинета (см. snapCabinetOffset) — в АБСОЛЮТНЫХ мм
     *  относительно начала сетки экрана (та же система координат, что candAbsX/Y
     *  внутри snapCabinetOffset), не в экранных px — переводится в px только при
     *  отрисовке (paintComponent), где уже есть готовый screenBoxes с офсетом
     *  сетки конкретного экрана в px. null — сейчас нет привязки по этой оси. */
    private Double snapGuideAbsMmX;
    private Double snapGuideAbsMmY;

    /** v3.0: Shift-прилипание ЦЕЛОГО экрана к соседним экранам сцены (не к сетке
     *  кабинетов внутри одного экрана, см. {@link #snapCabinetOffset}) — отдельная
     *  пара направляющих ({@link #snapScreenPosition}), т.к. цель тут в АБСОЛЮТНЫХ
     *  мм сцены, а не в мм относительно сетки одного экрана, как у {@link
     *  #snapGuideAbsMmX}/{@link #snapGuideAbsMmY}. */
    private Double screenSnapGuideAbsMmX;
    private Double screenSnapGuideAbsMmY;

    /** v3.0: зум компактного (не detailMode) обзора сцены — Ctrl+колесо, см. {@link
     *  #mouseWheelMoved}; 1.0 = обычная подгонка под окно, без искусственного минимума
     *  (в отличие от {@link #detailZoom}, тут уменьшать некуда — фит уже минимален). */
    private double compactZoom = 1.0;

    /** v3.0: панорамирование средней кнопкой мыши — двигает viewport окружающего
     *  JScrollPane напрямую (та же логика, что у полосы прокрутки), работает и в
     *  compact, и в detailMode. */
    private boolean panning;
    private int panPressScreenX, panPressScreenY;
    private java.awt.Point panViewStart;

    /** v3.0: ПКМ по кабинету в detailMode (прериг, не построение цепочки) открывает
     *  радиальное меню ячейки — перенесено из убранного отдельного окна «Форма
     *  экрана» (см. {@code ShapeEditorPanel}, баг-репорт 2026-09-14 "избавляемся от
     *  отдельного окна"; ЛКМ-перетаскивание кабинета в detailMode уже было и здесь,
     *  не менялось). {@code radialMenuActive} — тот же приём защиты от click-through
     *  RadialMenu (окно-попап на части платформ не перехватывает клик мыши само,
     *  см. showCabinetRadialMenu): пока меню открыто, обычные ЛКМ-клики по холсту
     *  игнорируются, иначе клик по пункту меню дополнительно долетал бы досюда и
     *  начинал перетаскивание/скрытие ячейки под меню как побочный эффект. */
    private boolean radialMenuActive;

    public SceneCanvasPanel(AppModel model, SettingsManager settings) {
        this.model = model;
        this.settings = settings;
        setBackground(Palette.BG);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (javax.swing.SwingUtilities.isMiddleMouseButton(e)) {
                    java.awt.Container parent = getParent();
                    if (parent instanceof javax.swing.JViewport viewport) {
                        panning = true;
                        panPressScreenX = e.getXOnScreen();
                        panPressScreenY = e.getYOnScreen();
                        panViewStart = viewport.getViewPosition();
                        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
                    }
                    return;
                }
                if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    if (detailMode && chainController != null && chainController.isChainBuilding()) {
                        // Кабинет для удаления из строящейся цепочки может принадлежать
                        // ЛЮБОМУ экрану — цепочка сигнала может физически продолжаться
                        // с одного экрана на другой, не только текущему.
                        Object[] hit = screenAndCabinetAt(e.getX(), e.getY());
                        if (hit != null && hit[1] != null) {
                            chainController.removeFromActive(((CabinetInstance) hit[1]).getId());
                        }
                    } else if (detailMode && chainController == null) {
                        // v3.0: радиальное меню ячейки (скрыть/восстановить, форма, угол,
                        // тип) — переехало сюда из убранного отдельного «Форма экрана»
                        // (баг-репорт 2026-09-14 "избавляемся от отдельного окна"), только
                        // в контексте прерига (не при построении цепочки — там ПКМ уже
                        // занят удалением из цепочки, см. ветку выше).
                        Object[] hit = screenAndCabinetAt(e.getX(), e.getY());
                        if (hit != null && hit[1] != null) {
                            showCabinetRadialMenu(e, (Screen) hit[0], (CabinetInstance) hit[1]);
                        }
                    }
                    return;
                }
                // Перетаскивание экрана/кабинета (Task #7/v1.6) — только когда виджет
                // используется как ПРОСМОТР/ПРЕРИГ (chainController не задан); при
                // реальной интерактивной прописке цепочек (Питание/Сигнал, chainController
                // задан) клик по экрану/кабинету означает совсем другое — ветка ниже.
                if (chainController == null && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    if (radialMenuActive) {
                        return;
                    }
                    if (!detailMode && showRiggingPoints) {
                        Screen rigHit = rigLevelAt(e.getX(), e.getY());
                        if (rigHit != null) {
                            model.selectScreen(rigHit);
                            if (rigLevelListener != null) {
                                rigLevelListener.onRigLevelClicked(rigHit);
                            }
                            return;
                        }
                    }
                    if (detailMode) {
                        Object[] hit = screenAndCabinetAt(e.getX(), e.getY());
                        if (hit != null) {
                            Screen s = (Screen) hit[0];
                            CabinetInstance cab = (CabinetInstance) hit[1];
                            model.selectScreen(s);
                            if (cab != null) {
                                draggingCabinetScreen = s;
                                draggingCabinet = cab;
                                dragCabStartOffX = cab.getOffsetXMm();
                                dragCabStartOffY = cab.getOffsetYMm();
                                dragCabPressPxX = e.getX();
                                dragCabPressPxY = e.getY();
                                dragCabMoved = false;
                            } else {
                                draggingScreen = s;
                                dragScreenStartX = s.getPosXMm();
                                dragScreenStartY = s.getPosYMm();
                                dragScreenPressPxX = e.getX();
                                dragScreenPressPxY = e.getY();
                                dragScreenMoved = false;
                            }
                            repaint();
                        }
                        return;
                    }
                    Screen s = screenAt(e.getX(), e.getY());
                    if (s != null) {
                        model.selectScreen(s);
                        draggingScreen = s;
                        dragScreenStartX = s.getPosXMm();
                        dragScreenStartY = s.getPosYMm();
                        dragScreenPressPxX = e.getX();
                        dragScreenPressPxY = e.getY();
                        dragScreenMoved = false;
                        repaint();
                    }
                    return;
                }
                Object[] hit = detailMode ? screenAndCabinetAt(e.getX(), e.getY()) : null;
                if (hit != null) {
                    Screen s = (Screen) hit[0];
                    CabinetInstance cab = (CabinetInstance) hit[1];
                    boolean building = chainController != null && chainController.isChainBuilding();
                    if (building && cab != null) {
                        // Пока идёт построение — не переключаем активный экран даже если
                        // кликнули по кабинету ДРУГОГО экрана: цепочка сигнала может
                        // физически продолжаться туда (общий даунлинк на смежный экран).
                        lastDragCabId = cab.getId();
                        chainController.cabinetClicked(cab.getId());
                    } else if (s != model.getCurrentScreen()) {
                        model.selectScreen(s);
                    }
                    return;
                }
                Screen s = screenAt(e.getX(), e.getY());
                if (s != null) {
                    model.selectScreen(s);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (panning) {
                    panning = false;
                    setCursor(java.awt.Cursor.getDefaultCursor());
                    return;
                }
                lastDragCabId = null;
                snapGuideAbsMmX = null;
                snapGuideAbsMmY = null;
                screenSnapGuideAbsMmX = null;
                screenSnapGuideAbsMmY = null;
                if (draggingScreen != null) {
                    if (dragScreenMoved) {
                        // Тот же приём, что и у перетаскивания подписи связи в общей схеме
                        // (SchemaCanvasPanel, Task #3): live-мутация во время драга уже
                        // записала в объект ИТОГОВУЮ позицию — возвращаем поле к исходному
                        // значению ПЕРЕД вызовом AppModel-метода, чтобы его pushUndo()
                        // (снимающий состояние ДО мутации) на самом деле снял состояние
                        // ДО перетаскивания, а не уже сдвинутое.
                        double finalX = draggingScreen.getPosXMm();
                        double finalY = draggingScreen.getPosYMm();
                        draggingScreen.setPosXMm(dragScreenStartX);
                        draggingScreen.setPosYMm(dragScreenStartY);
                        model.updateScreenPosition(draggingScreen, finalX, finalY);
                    }
                    draggingScreen = null;
                    repaint();
                    return;
                }
                if (draggingCabinet != null) {
                    if (dragCabMoved) {
                        double finalOffX = draggingCabinet.getOffsetXMm();
                        double finalOffY = draggingCabinet.getOffsetYMm();
                        draggingCabinet.setOffsetXMm(dragCabStartOffX);
                        draggingCabinet.setOffsetYMm(dragCabStartOffY);
                        model.updateCabinetOffset(draggingCabinet, finalOffX, finalOffY);
                    }
                    draggingCabinet = null;
                    draggingCabinetScreen = null;
                    repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (panning) {
                    java.awt.Container parent = getParent();
                    if (parent instanceof javax.swing.JViewport viewport && panViewStart != null) {
                        int dx = e.getXOnScreen() - panPressScreenX;
                        int dy = e.getYOnScreen() - panPressScreenY;
                        int nx = Math.max(0, panViewStart.x - dx);
                        int ny = Math.max(0, panViewStart.y - dy);
                        viewport.setViewPosition(new java.awt.Point(nx, ny));
                    }
                    return;
                }
                if (!javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (draggingScreen != null) {
                    double[] b = boundsMm();
                    if (b == null) {
                        return;
                    }
                    double sc = scaleFor(b, getWidth(), getHeight());
                    double dxMm = (e.getX() - dragScreenPressPxX) / sc;
                    double dyMm = (e.getY() - dragScreenPressPxY) / sc;
                    if (!dragScreenMoved && Math.hypot(e.getX() - dragScreenPressPxX, e.getY() - dragScreenPressPxY) > 3) {
                        dragScreenMoved = true;
                    }
                    double candidateX = dragScreenStartX + dxMm;
                    double candidateY = dragScreenStartY + dyMm;
                    if (e.isShiftDown()) {
                        double[] snapped = snapScreenPosition(draggingScreen, candidateX, candidateY, sc);
                        candidateX = snapped[0];
                        candidateY = snapped[1];
                    } else {
                        screenSnapGuideAbsMmX = null;
                        screenSnapGuideAbsMmY = null;
                    }
                    draggingScreen.setPosXMm(candidateX);
                    draggingScreen.setPosYMm(candidateY);
                    repaint();
                    return;
                }
                if (draggingCabinet != null) {
                    double[] b = boundsMm();
                    if (b == null) {
                        return;
                    }
                    double sc = scaleFor(b, getWidth(), getHeight());
                    double dxMm = (e.getX() - dragCabPressPxX) / sc;
                    double dyMm = (e.getY() - dragCabPressPxY) / sc;
                    if (!dragCabMoved && Math.hypot(e.getX() - dragCabPressPxX, e.getY() - dragCabPressPxY) > 3) {
                        dragCabMoved = true;
                    }
                    double candidateOffX = dragCabStartOffX + dxMm;
                    double candidateOffY = dragCabStartOffY + dyMm;
                    if (e.isShiftDown()) {
                        double[] snapped = snapCabinetOffset(draggingCabinetScreen, draggingCabinet,
                                candidateOffX, candidateOffY, sc);
                        candidateOffX = snapped[0];
                        candidateOffY = snapped[1];
                    } else {
                        snapGuideAbsMmX = null;
                        snapGuideAbsMmY = null;
                    }
                    draggingCabinet.setOffsetXMm(candidateOffX);
                    draggingCabinet.setOffsetYMm(candidateOffY);
                    repaint();
                    return;
                }
                if (!detailMode || chainController == null || !chainController.isChainBuilding()) {
                    return;
                }
                Object[] hit = screenAndCabinetAt(e.getX(), e.getY());
                if (hit == null) {
                    return;
                }
                // Как и в mousePressed — протяжка может продолжить цепочку на другой
                // экран, поэтому кабинет не ограничен текущим активным экраном.
                CabinetInstance cab = (CabinetInstance) hit[1];
                if (cab != null && !cab.getId().equals(lastDragCabId)) {
                    lastDragCabId = cab.getId();
                    chainController.cabinetClicked(cab.getId());
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (!detailMode || chainController == null) {
                    return;
                }
                Object[] hit = screenAndCabinetAt(e.getX(), e.getY());
                CabinetInstance cab = hit != null ? (CabinetInstance) hit[1] : null;
                chainController.cabinetHovered(cab != null ? cab.getId() : null);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (detailMode && !detailFit && e.isControlDown()) {
                    double factor = e.getWheelRotation() < 0 ? 1.1 : 1 / 1.1;
                    detailZoom = Math.max(0.2, Math.min(4.0, detailZoom * factor));
                    revalidate();
                    repaint();
                } else if (compact && !detailMode && e.isControlDown()) {
                    // v3.0: зум компактного обзора сцены (мини-превью прерига) — тот же жест
                    // (Ctrl+колесо), что и detailZoom выше, отдельное поле (compactZoom),
                    // т.к. базовый масштаб здесь всегда "подгонка под окно" (boundsToScale),
                    // а не фиксированный DETAIL_PX_PER_MM.
                    double factor = e.getWheelRotation() < 0 ? 1.1 : 1 / 1.1;
                    compactZoom = Math.max(1.0, Math.min(6.0, compactZoom * factor));
                    revalidate();
                    repaint();
                } else if (getParent() != null) {
                    getParent().dispatchEvent(e);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    /** Включает интерактивную прописку цепочек в детальном режиме "Показать все
     *  экраны сцены" — вызывается только из Power/Signal StagePanel, где есть
     *  реальный ChainInteractionController; для мини-превью прерига/корнер-виджета
     *  не вызывается вовсе (остаются только для просмотра, как раньше). */
    public void setChainController(CanvasPanel.Controller controller) {
        this.chainController = controller;
        setFocusable(controller != null);
    }

    /** Экран и (если попали внутрь его сетки) конкретный кабинет под точкой — для
     *  интерактивной прописки из общего обзора сцены. Кабинет в результате может
     *  быть null (попали на экран, но не на кабинет — паддинг сетки/ячейка скрыта). */
    private Object[] screenAndCabinetAt(int px, int py) {
        Scene scene = model.getCurrentScene();
        double[] b = boundsMm();
        if (scene == null || b == null) {
            return null;
        }
        double sc = scaleFor(b, getWidth(), getHeight());
        int padding = padding();
        for (Screen s : scene.getScreens()) {
            CabinetType t = model.typeOf(s);
            if (t == null) {
                continue;
            }
            int cellW = (int) Math.round(t.getWidthMm() * sc);
            int cellH = (int) Math.round(t.getHeightMm() * sc);
            if (cellW <= 0 || cellH <= 0) {
                continue;
            }
            // Расширенный бокс (см. screenBoxPx) — та же грубая отбраковка "попал ли
            // курсор в экран вообще", что рисует paint(), иначе кабинет, вытащенный
            // свободным смещением ЗА номинальную сетку, был бы виден, но недоступен
            // для клика/перетаскивания (баг-репорт).
            int[] box = screenBoxPx(s, t, b, sc, padding);
            if (px >= box[0] && px <= box[0] + box[2] && py >= box[1] && py <= box[1] + box[3]) {
                int gridX = screenGridX(s, b, sc, padding);
                int gridY = screenGridY(s, b, sc, padding);
                CabinetInstance cab = cabinetAtPoint(s, t, px, py, cellW, cellH, gridX, gridY);
                return new Object[]{s, cab};
            }
        }
        return null;
    }

    /** Кабинет экрана под точкой — линейный перебор с учётом свободного мм-смещения
     *  ячейки (Task #7/v1.6): при офсете ячейка может физически оказаться не там, где
     *  её кладёт голая формула row*cellH/col*cellW (та годится лишь для грубой
     *  отбраковки экрана целиком, см. вызывающий код выше), поэтому точный кабинет
     *  под курсором ищем перебором актуальных прямоугольников — экраны обычно от
     *  единиц до сотен кабинетов, перебор на глаз не заметен. */
    private CabinetInstance cabinetAtPoint(Screen s, CabinetType t, int px, int py,
                                            int cellW, int cellH, int offX, int offY) {
        for (CabinetInstance cab : s.getCabinets()) {
            int cx = cabX(cab, t, cellW, offX);
            int cy = cabY(cab, t, cellH, offY);
            if (px >= cx && px < cx + cellW && py >= cy && py < cy + cellH) {
                return cab;
            }
        }
        return null;
    }

    /** ПКМ (зажать и повести к пункту, как в Krita) по кабинету в detailMode —
     *  2-уровневое радиальное меню: 1 уровень — скрыть/восстановить ячейку, «Форма»,
     *  «Угол», «Тип»; 2 уровень (для «Форма»/«Угол»/«Тип») — сам выбор конкретного
     *  значения. Перенесено дословно (та же логика веток/подпунктов) из убранного
     *  отдельного «Форма экрана» (см. class-javadoc {@link #radialMenuActive}). */
    private void showCabinetRadialMenu(MouseEvent e, Screen scr, CabinetInstance cab) {
        String cabId = cab.getId();
        List<RadialMenu.Item> items = new ArrayList<>();
        items.add(RadialMenu.Item.leaf(cab.isHidden() ? "Восстановить кабинет" : "Удалить кабинет",
                Palette.MUTED, () -> model.toggleCabinetHidden(cabId)));
        // Пункт "Форма" виден только если у эффективного типа этого кабинета вообще
        // есть выбор — если физически возможна только одна форма (обычный случай),
        // пункт скрывается целиком: выбирать всё равно не из чего.
        CabinetType effective = ScreenLogic.effectiveType(cab, model.typeOf(scr), model.getWorkspace());
        if (effective != null && effective.getAllowedShapes().size() > 1) {
            items.add(RadialMenu.Item.branch("Форма", Palette.BORDER, shapeSubmenu(cabId, effective)));
        }
        // "Угол" — только если ФАКТИЧЕСКАЯ форма ячейки (с учётом переопределения)
        // вообще непрямоугольная: прямоугольнику ориентировать нечего.
        com.vjstb.ledscheme.model.CabinetShape effectiveShape = cab.getShapeOverride() != null
                ? cab.getShapeOverride() : (effective != null ? effective.getShape() : null);
        if (effectiveShape != null && effectiveShape != com.vjstb.ledscheme.model.CabinetShape.RECTANGLE) {
            items.add(RadialMenu.Item.branch("Угол", Palette.ACCENT, rotationSubmenu(cabId, cab)));
        }
        items.add(RadialMenu.Item.branch("Тип", Palette.ACCENT, typeSubmenu(cabId)));

        java.awt.Point screenPt = e.getLocationOnScreen();
        radialMenuActive = true;
        // onClose срабатывает синхронно ВНУТРИ обработки того же события мыши, что
        // его вызвало — см. class-javadoc radialMenuActive про click-through.
        // SwingUtilities.invokeLater откладывает снятие флага на следующий проход
        // цикла событий, защита остаётся активной до конца обработки текущего клика.
        RadialMenu.show(this, screenPt.x, screenPt.y, items,
                () -> javax.swing.SwingUtilities.invokeLater(() -> radialMenuActive = false));
    }

    private List<RadialMenu.Item> shapeSubmenu(String cabId, CabinetType effective) {
        List<RadialMenu.Item> items = new ArrayList<>();
        items.add(RadialMenu.Item.leaf("По умолчанию", Palette.PHASE_NONE, () -> applyShape(cabId, null)));
        // Только формы, физически возможные для ЭТОГО типа кабинета.
        for (com.vjstb.ledscheme.model.CabinetShape shape : effective.getAllowedShapes()) {
            items.add(RadialMenu.Item.leaf(shape.getLabel(), Palette.signalColor(shape.ordinal()),
                    () -> applyShape(cabId, shape)));
        }
        return items;
    }

    /** 5 пунктов: "По умолчанию" (снять переопределение — угол типа кабинета из
     *  библиотеки), 4 стандартных угла с шагом в четверть оборота, и "Другое…" —
     *  свободный ввод произвольного угла. */
    private List<RadialMenu.Item> rotationSubmenu(String cabId, CabinetInstance cab) {
        List<RadialMenu.Item> items = new ArrayList<>();
        items.add(RadialMenu.Item.leaf("По умолчанию", Palette.PHASE_NONE, () -> applyRotation(cabId, null)));
        for (int deg : new int[]{0, 90, 180, 270}) {
            items.add(RadialMenu.Item.leaf(deg + "°", Palette.signalColor(deg / 90), () -> applyRotation(cabId, deg)));
        }
        items.add(RadialMenu.Item.leaf("Другое…", Palette.MUTED, () -> promptCustomRotation(cabId, cab)));
        return items;
    }

    private void promptCustomRotation(String cabId, CabinetInstance cab) {
        Integer current = cab.getRotationOverride();
        String initial = current != null ? String.valueOf(current) : "0";
        String input = javax.swing.JOptionPane.showInputDialog(this,
                "Угол, градусы (0° — прямой угол слева-снизу, далее по часовой стрелке):", initial);
        if (input == null) {
            return;
        }
        try {
            int deg = (int) Math.round(Double.parseDouble(input.trim().replace(',', '.')));
            applyRotation(cabId, deg);
        } catch (NumberFormatException ex) {
            javax.swing.JOptionPane.showMessageDialog(this, "Введите число", "Проверка данных",
                    javax.swing.JOptionPane.WARNING_MESSAGE);
        }
    }

    private void applyRotation(String cabId, Integer rotationDeg) {
        model.setCabinetRotationOverride(cabId, rotationDeg);
    }

    private List<RadialMenu.Item> typeSubmenu(String cabId) {
        List<RadialMenu.Item> items = new ArrayList<>();
        List<CabinetType> types = model.getCabinetTypes();
        items.add(RadialMenu.Item.leaf("По умолчанию", Palette.PHASE_NONE, () -> applyType(cabId, null)));
        for (CabinetType t : types) {
            // Тот же typeColor(...), что и при отрисовке ячеек в detailMode — иначе
            // цвет пункта меню и итоговый цвет закрашенной ячейки расходятся.
            items.add(RadialMenu.Item.leaf(t.getName(), typeColorFor(types, t), () -> applyType(cabId, t.getId())));
        }
        return items;
    }

    private void applyType(String cabId, String typeId) {
        try {
            model.setCabinetTypeOverride(cabId, typeId);
        } catch (RuntimeException ex) {
            javax.swing.JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyShape(String cabId, com.vjstb.ledscheme.model.CabinetShape shape) {
        model.setCabinetShapeOverride(cabId, shape);
    }

    /** Стабильный цвет по позиции типа в библиотеке — та же палитра, что и в самом
     *  радиальном меню, чтобы цвет пункта совпадал с цветом соответствующего типа. */
    private static Color typeColorFor(List<CabinetType> types, CabinetType type) {
        int idx = types.indexOf(type);
        return Palette.signalColor(Math.max(0, idx));
    }

    /** Подсветка кабинетов с переопределением типа (см. {@link #showCabinetRadialMenu}
     *  → "Тип") — тонированная заливка цветом типа поверх обычной раскраски {@code
     *  paintScheme}, и переопределения формы (→ "Форма"/"Угол") — реальный контур
     *  формы, а не декоративная метка. Только в {@code detailMode}: тот же принцип,
     *  что был у убранного {@code ShapeEditorPanel} — переопределение должно быть
     *  видно СРАЗУ, не только через тултип/диалог параметров. */
    private void drawCabinetOverrideMarks(Graphics2D g2, Screen s, CabinetType defaultType,
                                           int cellW, int cellH, int offX, int offY) {
        List<CabinetType> types = model.getCabinetTypes();
        for (CabinetInstance c : s.getCabinets()) {
            if (c.isHidden()) {
                continue;
            }
            int x = cabX(c, defaultType, cellW, offX);
            int y = cabY(c, defaultType, cellH, offY);
            if (c.getCabinetTypeId() != null) {
                CabinetType override = model.getWorkspace().cabinetTypeById(c.getCabinetTypeId());
                Color typeColor = override != null ? typeColorFor(types, override) : Palette.ACCENT;
                g2.setColor(blend(Palette.PHASE_NONE, typeColor, 0.55f));
                g2.fillRect(x + 1, y + 1, cellW - 2, cellH - 2);
                g2.setColor(Palette.ACCENT);
                g2.drawRect(x + 1, y + 1, cellW - 2, cellH - 2);
                // Заливка выше рисуется ПОВЕРХ paintScheme (см. javadoc метода) и без этого
                // молча перекрывала подпись «строка,столбец» — баг-репорт 2026-09-14.
                SchemeRenderer.drawCabinetIndexLabel(g2, c, x, y, cellW, cellH);
            }
            com.vjstb.ledscheme.model.CabinetShape shape = c.getShapeOverride();
            if (shape != null && shape != com.vjstb.ledscheme.model.CabinetShape.RECTANGLE) {
                CabinetType effective = ScreenLogic.effectiveType(c, defaultType, model.getWorkspace());
                double rotationDeg = SchemeRenderer.effectiveRotationDeg(c, effective);
                g2.setColor(Palette.TEXT);
                SchemeRenderer.outlineCabinetShape(g2, x + 1, y + 1, cellW - 2, cellH - 2, shape, rotationDeg);
            }
        }
    }

    private static Color blend(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() * (1 - t) + b.getRed() * t),
                Math.round(a.getGreen() * (1 - t) + b.getGreen() * t),
                Math.round(a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    /** Режим «показать все экраны сцены» (Питание/Сигнал): вместо простого
     *  прямоугольника с названием — полная сетка кабинетов КАЖДОГО экрана сцены
     *  с текущей прописью цепочек этапа (питание или сигнал), как в SchemeRenderer.
     *  При fitToViewport=false панель становится «настоящего» размера (мм × масштаб)
     *  и рассчитана на прокрутку/зум колесом+Ctrl (общий обзор сцены в «Показать все
     *  экраны»); при fitToViewport=true масштаб всегда подгоняется под текущий размер
     *  контейнера — вся сцена целиком видна сразу, без скролла (для маленького всегда
     *  видимого корнер-виджета в углу холста). */
    public void setDetailMode(boolean enabled, boolean power) {
        setDetailMode(enabled, power, false);
    }

    public void setDetailMode(boolean enabled, boolean power, boolean fitToViewport) {
        this.detailMode = enabled;
        this.detailPower = power;
        this.detailFit = fitToViewport;
        revalidate();
        repaint();
    }

    /** Включает отрисовку точек подвеса — используется только мини-превью прерига
     *  (Сетап), где именно эта информация нужна; по умолчанию выключено. */
    public void setShowRiggingPoints(boolean show) {
        this.showRiggingPoints = show;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        if (detailMode && !detailFit) {
            double[] b = boundsMm();
            double scale = DETAIL_PX_PER_MM * detailZoom;
            int w = b != null ? (int) Math.round((b[2] - b[0]) * scale) + PADDING * 2 : 600;
            int h = b != null ? (int) Math.round((b[3] - b[1]) * scale) + PADDING * 2 : 400;
            return new Dimension(Math.max(200, w), Math.max(200, h));
        }
        // v3.0: зум компактного обзора (см. compactZoom/mouseWheelMoved) — раздувает
        // preferredSize пропорционально, чтобы окружающий JScrollPane начал скроллить
        // (панорамирование средней кнопкой — panning выше — двигает именно этот
        // скролл). Базовый ("подогнанный") масштаб считаем от размера ВЬЮПОРТА, а не
        // текущего getWidth()/getHeight() этой панели — та уже могла быть раздута
        // предыдущим зумом, взять фит-масштаб от неё же значило бы всё время
        // отталкиваться от уже увеличенного размера (дрейф при повторном зуме).
        if (compact && !detailMode && compactZoom != 1.0) {
            double[] b = boundsMm();
            if (b != null) {
                Dimension vp = viewportSize();
                double fit = boundsToScale(b, vp.width, Math.max(1, vp.height - rigHeadroomPx()));
                double scale = fit * compactZoom;
                int w = (int) Math.round((b[2] - b[0]) * scale) + padding() * 2;
                int h = (int) Math.round((b[3] - b[1]) * scale) + padding() * 2 + rigHeadroomPx();
                return new Dimension(Math.max(vp.width, w), Math.max(vp.height, h));
            }
        }
        return super.getPreferredSize();
    }

    /** Размер окружающего {@link javax.swing.JViewport}, если панель внутри
     *  {@link javax.swing.JScrollPane} (обычный случай для этого класса) — иначе
     *  текущий собственный размер. Нужен для {@link #getPreferredSize} при зуме:
     *  масштаб "подгонки" должен считаться от размера ВЬЮПОРТА (что реально видно),
     *  а не от уже раздутого зумом текущего getWidth()/getHeight(). */
    private Dimension viewportSize() {
        java.awt.Container parent = getParent();
        if (parent instanceof javax.swing.JViewport viewport) {
            return viewport.getExtentSize();
        }
        return getSize();
    }

    private double[] boundsMm() {
        Scene scene = model.getCurrentScene();
        if (scene == null) {
            return null;
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean any = false;
        for (Screen s : scene.getScreens()) {
            CabinetType t = model.typeOf(s);
            if (t == null) {
                continue;
            }
            double[] ext = screenExtentMm(s, t);
            minX = Math.min(minX, s.getPosXMm() + ext[0]);
            minY = Math.min(minY, s.getPosYMm() + ext[1]);
            maxX = Math.max(maxX, s.getPosXMm() + ext[2]);
            maxY = Math.max(maxY, s.getPosYMm() + ext[3]);
            any = true;
        }
        return any ? new double[]{minX, minY, maxX, maxY} : null;
    }

    /** Границы экрана В ЕГО ЛОКАЛЬНЫХ координатах (0,0 = левый верхний угол сетки
     *  без смещений) — [minX, minY, maxX, maxY] в мм. По умолчанию это просто
     *  номинальная сетка [0,0, cols*widthMm, rows*heightMm], НО если какой-то
     *  кабинет свободным смещением (Task #7/v1.6) вытащен ЗА эти пределы, граница
     *  расширяется, чтобы включить его — иначе (баг-репорт) вытащенный за номинальную
     *  сетку кабинет становился недоступен для клика/перетаскивания (весь расчёт
     *  «попал ли курсор в экран» проверял только номинальный прямоугольник) и мог
     *  визуально обрезаться масштабом/паддингом всей сцены, посчитанными без него. */
    private double[] screenExtentMm(Screen s, CabinetType t) {
        return ScreenLogic.cabinetExtentMm(s, t, model.getWorkspace());
    }

    /** Прямоугольник экрана на экране (px) — РАСШИРЕННЫЙ до фактических границ его
     *  кабинетов (см. {@link #screenExtentMm}), используется для отрисовки рамки/
     *  заливки И для хит-теста (screenAt/screenAndCabinetAt) — обе стороны должны
     *  видеть ОДНИ И ТЕ ЖЕ границы, иначе клик и картинка разъедутся (тот же принцип,
     *  что и у SchemaCanvasPanel.computeSocketRects). */
    private int[] screenBoxPx(Screen s, CabinetType t, double[] b, double sc, int padding) {
        double[] ext = screenExtentMm(s, t);
        int x = padding + (int) Math.round((s.getPosXMm() + ext[0] - b[0]) * sc);
        int y = padding + rigHeadroomPx() + (int) Math.round((s.getPosYMm() + ext[1] - b[1]) * sc);
        int w = Math.max(2, (int) Math.round((ext[2] - ext[0]) * sc));
        int h = Math.max(2, (int) Math.round((ext[3] - ext[1]) * sc));
        return new int[]{x, y, w, h};
    }

    /** Номинальное начало сетки экрана (col=0,row=0, БЕЗ учёта того, что вытащенный
     *  наружу кабинет мог расширить видимый бокс, см. {@link #screenBoxPx}) — именно
     *  этот якорь передаётся в SchemeRenderer.paintScheme/cabX/cabY, у которых
     *  смещение каждой ячейки уже само по себе может быть отрицательным. */
    private int screenGridX(Screen s, double[] b, double sc, int padding) {
        return padding + (int) Math.round((s.getPosXMm() - b[0]) * sc);
    }

    private int screenGridY(Screen s, double[] b, double sc, int padding) {
        return padding + rigHeadroomPx() + (int) Math.round((s.getPosYMm() - b[1]) * sc);
    }

    /** Компактный режим для мини-превью (прериг): без подписей и без разметки под
     *  экраном, минимальные отступы, только выделение текущего экрана среди
     *  притушенных остальных — переиспользуется с этими же данными и в полноразмерном
     *  виде (обзор сцены), и в мини-виджете. */
    public void setCompact(boolean compact) {
        this.compact = compact;
        repaint();
    }

    private int padding() {
        if (detailMode && detailFit) {
            return PADDING_DETAIL_FIT;
        }
        return compact ? PADDING_COMPACT : PADDING;
    }

    private Screen screenAt(int px, int py) {
        Scene scene = model.getCurrentScene();
        double[] b = boundsMm();
        if (scene == null || b == null) {
            return null;
        }
        double sc = scaleFor(b, getWidth(), getHeight());
        int padding = padding();
        for (Screen s : scene.getScreens()) {
            CabinetType t = model.typeOf(s);
            if (t == null) {
                continue;
            }
            int[] box = screenBoxPx(s, t, b, sc, padding);
            if (px >= box[0] && px <= box[0] + box[2] && py >= box[1] && py <= box[1] + box[3]) {
                return s;
            }
        }
        return null;
    }

    private double scaleFor(double[] b, int width, int height) {
        if (detailMode && !detailFit) {
            return DETAIL_PX_PER_MM * detailZoom;
        }
        return boundsToScale(b, width, Math.max(1, height - rigHeadroomPx()));
    }

    /** v3.0: полосы уровня подвеса (лебёдки/ферма/точки, см. {@link #rigBands}) рисуются
     *  НАД прямоугольником экрана — резервируем эту высоту сверху КАЖДЫЙ раз, когда они
     *  вообще могут появиться (см. paint() — не рисуются в detailMode вовсе), иначе у
     *  экранов на самом верху раскладки полосы уезжают за пределы холста (баг-репорт
     *  2026-09-14 «ферма где-то за областью»). Централизованно здесь и в {@link
     *  #screenBoxPx}/{@link #screenGridY} (не на каждом отдельном вызывающем месте) —
     *  все места, где считается позиция экрана (отрисовка, хит-тест клика/драга),
     *  автоматически остаются согласованными друг с другом. */
    private int rigHeadroomPx() {
        return (showRiggingPoints && !detailMode) ? RIG_TOP_HEADROOM_PX : 0;
    }

    /** Рендерит текущий вид в изображение заданного размера (для экспорта). */
    public BufferedImage renderImage(int width, int height) {
        return renderImage(width, height, 1.0);
    }

    /** {@code dpiScale} — множитель качества экспорта (см. {@code UserProfile#getDocExportDpi},
     *  1.0 = прежнее поведение), см. аналогичный параметр
     *  {@code SchemaCanvasPanel#renderImage(int, int, boolean, double)}. */
    public BufferedImage renderImage(int width, int height, double dpiScale) {
        BufferedImage img = new BufferedImage(Math.max(1, (int) Math.round(width * dpiScale)),
                Math.max(1, (int) Math.round(height * dpiScale)), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.scale(dpiScale, dpiScale);
        paint(g2, width, height);
        g2.dispose();
        return img;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        paint((Graphics2D) g.create(), getWidth(), getHeight());
    }

    private void paint(Graphics2D g2, int width, int height) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Palette.BG);
        g2.fillRect(0, 0, width, height);

        Scene scene = model.getCurrentScene();
        double[] b = boundsMm();
        if (scene == null || b == null) {
            g2.setColor(Palette.MUTED);
            g2.setFont(getFont().deriveFont(compact ? 11f : 14f));
            String msg = compact ? "Нет экранов"
                    : (scene == null ? "Выберите сцену, чтобы увидеть раскладку." : "На сцене нет экранов.");
            int tw = g2.getFontMetrics().stringWidth(msg);
            g2.drawString(msg, Math.max(4, (width - tw) / 2), height / 2);
            g2.dispose();
            return;
        }

        double sc = scaleFor(b, width, height);
        int padding = padding();
        Set<Screen> overlapping = overlappingScreens(scene);
        // Цепочки хранятся на уровне сцены (Task #78) — один и тот же полный список
        // передаётся paintScheme для КАЖДОГО экрана сцены ниже; каждый экран сам
        // рисует ровно тот кусок каждой цепочки, что резолвится на его кабинеты,
        // поэтому переход цепочки с одного экрана на другой корректно рисуется на
        // обоих без отдельного прохода "через границу" (см. также CanvasPanel).
        List<com.vjstb.ledscheme.model.PowerChain> scenePowerChains = scene.getPowerChains();
        List<com.vjstb.ledscheme.model.SignalChain> sceneSignalChains = scene.getSignalChains();
        // Экран + прямоугольник его детальной сетки — заполняется ниже для каждого
        // экрана, где реально нарисован paintScheme, нужно только чтобы потом
        // дорисовать САМ ПЕРЕХОД цепочки через границу между экранами (см.
        // drawCrossScreenBridges) — единственное, что paintScheme нарисовать не
        // может (обе точки перехода в разных локальных системах координат разных
        // экранов); внутренние отрезки цепочки внутри каждого экрана рисует сам
        // paintScheme, т.к. ему передаётся ПОЛНЫЙ список цепочек сцены.
        java.util.Map<String, int[]> screenBoxes = new java.util.HashMap<>();
        for (Screen s : scene.getScreens()) {
            CabinetType t = model.typeOf(s);
            if (t == null) {
                continue;
            }
            int[] box = screenBoxPx(s, t, b, sc, padding);
            int x = box[0], y = box[1], w = box[2], h = box[3];
            int gridX = screenGridX(s, b, sc, padding);
            int gridY = screenGridY(s, b, sc, padding);

            boolean current = s == model.getCurrentScreen();
            boolean overlapped = overlapping.contains(s);
            // В detailMode сплошная заливка всего прямоугольника экрана избыточна —
            // SchemeRenderer.paintScheme ниже сам закрашивает каждый кабинет отдельно —
            // и, что важнее, при физическом наложении экранов эта заливка была бы
            // НЕПРОЗРАЧНОЙ и стирала бы уже нарисованную сетку соседнего экрана в
            // зоне пересечения (см. комментарий у paintScheme ниже), поэтому в
            // detailMode её не рисуем вовсе.
            if (!detailMode) {
                Color fill;
                if (overlapped) {
                    fill = blendRed(Palette.PHASE_NONE);
                } else if (compact) {
                    // Компактный обзор (мини-превью прерига) раньше показывал экран
                    // пустым НЕЗАВИСИМО от факта расключения — зелёный оттенок,
                    // пропорциональный доле уже расключённых кабинетов, даёт видеть
                    // текущее состояние расключения, не переключаясь на детальный вид.
                    // Цветная метка зоны/площадки (v3.0), если назначена, подмешивается
                    // В ТУ ЖЕ базу ДО зелёного оттенка расключения — оба сигнала должны
                    // читаться одновременно (метка не должна тонуть под "всё расключено").
                    java.awt.Color tag = s.getTagColor().color();
                    Color base = tag != null ? blendTag(current ? Palette.PHASE_NONE : Palette.PANEL, tag)
                            : (current ? Palette.PHASE_NONE : Palette.PANEL);
                    fill = blendGreen(base, wiredFraction(s) * 0.6);
                } else {
                    fill = Palette.PHASE_NONE;
                }
                g2.setColor(fill);
                g2.fillRect(x, y, w, h);
            }
            g2.setColor(overlapped ? Color.RED : (current ? Palette.ACCENT : Palette.BORDER));
            g2.setStroke(overlapped
                    ? new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0, new float[]{6, 4}, 0)
                    : new BasicStroke(current ? 2.5f : 1.4f));
            g2.drawRect(x, y, w, h);

            if (showRiggingPoints && !detailMode && s.getMountType() == com.vjstb.ledscheme.model.ScreenMountType.RIGGED
                    && s.getRiggingPointsCount() > 0) {
                double screenWidthMm = t.getWidthMm() * s.getCols();
                com.vjstb.ledscheme.service.RiggingCalc.Result riggingResult =
                        com.vjstb.ledscheme.service.RiggingCalc.compute(s, t, model.getWorkspace(),
                                s.getRiggingPointsCount());
                com.vjstb.ledscheme.service.TrussCalc.Result trussResult =
                        com.vjstb.ledscheme.service.TrussCalc.compute(s, t, model.getWorkspace());
                RigBands rb = rigBands(y);
                int neededHeight = rb.trussBottom() - rb.pointsTop();
                if (hasRoomAboveForRigBand(scene, s, b, sc, padding, x, y, w, neededHeight)) {
                    drawRiggingPoints(g2, riggingResult, screenWidthMm, x, rb.pointsTop(), rb.pointsBottom(), w);
                    if (!trussResult.profileMissing() && !trussResult.catalogEmpty()) {
                        drawRiggingTruss(g2, trussResult, screenWidthMm, x, rb.trussTop(), rb.trussBottom(), w);
                    }
                } else {
                    // Полоса не влезает (см. hasRoomAboveForRigBand) -- тонкая белая
                    // черта прямо над экраном вместо полного исчезновения: знак "здесь
                    // есть ферма", без попытки нарисовать её в деталях там, где места
                    // объективно нет (баг-репорт 2026-09-14).
                    Color prevColor = g2.getColor();
                    g2.setColor(Color.WHITE);
                    g2.fillRect(x, y - WHITE_STRIP_H, w, WHITE_STRIP_H);
                    g2.setColor(prevColor);
                }
            }

            if (detailMode) {
                int cellW = (int) Math.round(t.getWidthMm() * sc);
                int cellH = (int) Math.round(t.getHeightMm() * sc);
                if (cellW >= DETAIL_MIN_CELL_PX && cellH >= DETAIL_MIN_CELL_PX) {
                    // Экраны, физически перекрывающиеся по координатам (мм), рисуются здесь
                    // друг за другом в одних и тех же пикселях — без прозрачности каждый
                    // следующий экран (кабинеты которого тоже красятся сплошной заливкой)
                    // полностью стирал бы уже нарисованную сетку предыдущего в зоне
                    // пересечения, и казалось бы, что там нет ни сетки, ни цепочки вовсе
                    // (хотя данные коммутации на самом деле никак не зависят от наложения —
                    // у каждого экрана своя независимая сетка кабинетов). Полупрозрачность
                    // делает оба экрана в зоне пересечения видимыми одновременно.
                    if (overlapped) {
                        Graphics2D go = (Graphics2D) g2.create();
                        go.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
                        SchemeRenderer.paintScheme(go, s, t, detailPower, cellW, cellH, gridX, gridY, model.getWorkspace(),
                                scenePowerChains, sceneSignalChains, model.controllersInScene(scene),
                                settings.activeProfile().isPowerUnitKw());
                        if (chainController != null && chainController.isChainBuilding()) {
                            drawChainBuildingOverlay(go, s, cellW, cellH, gridX, gridY);
                        }
                        go.dispose();
                    } else {
                        SchemeRenderer.paintScheme(g2, s, t, detailPower, cellW, cellH, gridX, gridY, model.getWorkspace(),
                                scenePowerChains, sceneSignalChains, model.controllersInScene(scene),
                                settings.activeProfile().isPowerUnitKw());
                        if (chainController != null && chainController.isChainBuilding()) {
                            drawChainBuildingOverlay(g2, s, cellW, cellH, gridX, gridY);
                        }
                    }
                    // v3.0: подсветка кабинетов с переопределением типа/формы — раньше
                    // это рисовал сам ShapeEditorPanel в своей отдельной упрощённой сетке
                    // (баг-репорт 2026-09-14: "поправь отображение изменённых кабинетов" —
                    // после переноса правки в этот, общий, рендер сетки типа/формы
                    // визуально никак не выделялись, хотя переопределение реально
                    // применялось). Отдельным проходом ПОВЕРХ paintScheme — тот целиком
                    // общий с реальным холстом расключения (CanvasPanel), туда эту
                    // подсветку добавлять не нужно.
                    drawCabinetOverrideMarks(g2, s, t, cellW, cellH, gridX, gridY);
                    // NB: НОМИНАЛЬНОЕ начало сетки (gridX/gridY), не расширенный бокс x/y —
                    // locateCabinet ниже вызывает cabX/cabY, которые сами уже прибавляют
                    // (возможно отрицательное) мм-смещение конкретного кабинета поверх
                    // этого якоря, поэтому якорь должен оставаться «как если бы смещений
                    // не было», иначе смещение учлось бы дважды.
                    screenBoxes.put(s.getId(), new int[]{gridX, gridY, cellW, cellH});
                }
                g2.setColor(overlapped ? Color.RED : (current ? Palette.ACCENT : new Color(0xc0, 0xc8, 0xd0)));
                g2.setFont(getFont().deriveFont(Font.BOLD, detailFit ? 10f : 12f));
                g2.drawString(s.getName(), x + 3, y - (detailFit ? 3 : 4));
                continue;
            }

            if (compact) {
                if (current) {
                    g2.setColor(Palette.ACCENT);
                    g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
                    String name = s.getName();
                    if (g2.getFontMetrics().stringWidth(name) <= w - 6 && h >= 18) {
                        g2.drawString(name, x + 3, y + 13);
                    }
                }
                continue;
            }

            ScreenStats st = ScreenLogic.stats(s, t, model.getWorkspace());
            g2.setColor(overlapped ? Color.RED : (current ? Palette.ACCENT : new Color(0xc0, 0xc8, 0xd0)));
            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            g2.drawString(s.getName(), x + 4, y + 16);
            g2.setColor(Palette.MUTED);
            g2.setFont(getFont().deriveFont(Font.PLAIN, 10f));
            g2.drawString(st.resolutionWidthPx() + "×" + st.resolutionHeightPx() + " px", x + 4, y + 30);
            g2.drawString(Math.round(s.getCols() * t.getWidthMm()) + "×" + Math.round(s.getRows() * t.getHeightMm())
                    + " мм", x + 4, y + 43);
        }

        // Сам переход цепочки через границу между экранами — единственное, что
        // paintScheme нарисовать не может (см. комментарий выше). Регрессия Task #78:
        // раньше это рисовалось только для сигнала (drawCrossScreenChainSegments,
        // Task #69) — при переносе цепочек на уровень сцены весь метод был удалён
        // как якобы избыточный, но избыточной была только часть, дублирующая ВНУТРЕННИЕ
        // отрезки (это теперь и правда делает paintScheme) — сам переход границы
        // по-прежнему нужно рисовать отдельно, и для питания тоже (раньше не рисовался
        // вовсе). Только для РЕЖИМА, который сейчас показан (detailPower) — иначе
        // мост чужого режима протекал бы поверх текущего вида (см. Task #78 follow-up).
        if (detailMode) {
            if (detailPower) {
                drawCrossScreenBridges(g2, scene, scenePowerChains, List.of(), screenBoxes);
            } else {
                drawCrossScreenBridges(g2, scene, List.of(), sceneSignalChains, screenBoxes);
            }
            // Переход ЕЩЁ НЕ СОХРАНЁННОЙ (строящейся) цепочки через границу экранов —
            // тем же приёмом, что и для уже сохранённых цепочек выше, но по активному
            // списку кабинетов контроллера, а не по scene.getPowerChains()/getSignalChains()
            // (Task #8/v1.6: раньше сам переход между экранами при построении был
            // не виден, пока не завершишь Esc-ом и он не станет сохранённой цепочкой).
            if (chainController != null && chainController.isChainBuilding()) {
                Color activeColor = detailPower ? Palette.phaseColor(model.getActivePhase()) : Color.WHITE;
                drawBridgeSegments(g2, scene, chainController.activeChainCabIds(), activeColor, screenBoxes);
            }
        }

        if (!overlapping.isEmpty() && !compact) {
            g2.setColor(Color.RED);
            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            g2.drawString("⚠ Экраны перекрываются — проверьте позиции (X/Y) или нажмите «Расставить без наложения»",
                    padding, height - 10);
        }

        drawSnapGuides(g2, screenBoxes, b, sc, padding, width, height);

        g2.dispose();
    }

    /** Направляющие линии Shift-прилипания кабинета (см. snapCabinetOffset) — тот же
     *  стиль, что и в SchemaCanvasPanel/CanvasEditorPanel. snapGuideAbsMmX/Y хранят
     *  цель в мм относительно начала СЕТКИ ЭКРАНА — переводим в экранные px через
     *  {@code screenBoxes} (тот же offset/масштаб, что уже посчитан для отрисовки
     *  сетки этого экрана чуть выше в этом же вызове paintComponent). */
    private void drawSnapGuides(Graphics2D g2, java.util.Map<String, int[]> screenBoxes, double[] b, double sc,
                                 int padding, int width, int height) {
        if ((snapGuideAbsMmX != null || snapGuideAbsMmY != null) && draggingCabinetScreen != null) {
            int[] box = screenBoxes.get(draggingCabinetScreen.getId());
            if (box != null) {
                int gridX = box[0];
                int gridY = box[1];
                g2.setColor(Color.MAGENTA);
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0, new float[]{4, 4}, 0));
                if (snapGuideAbsMmX != null) {
                    int x = (int) Math.round(gridX + snapGuideAbsMmX * sc);
                    g2.drawLine(x, 0, x, height);
                }
                if (snapGuideAbsMmY != null) {
                    int y = (int) Math.round(gridY + snapGuideAbsMmY * sc);
                    g2.drawLine(0, y, width, y);
                }
            }
        }
        // v3.0: направляющие Shift-прилипания ЦЕЛОГО экрана (см. snapScreenPosition) —
        // цель в АБСОЛЮТНЫХ мм сцены (не мм относительно сетки одного экрана, как у
        // кабинетных направляющих выше), переводим в px той же формулой, что и
        // screenBoxPx/screenGridY для координаты экрана (padding + rigHeadroomPx()).
        if ((screenSnapGuideAbsMmX != null || screenSnapGuideAbsMmY != null) && draggingScreen != null) {
            g2.setColor(Color.MAGENTA);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0, new float[]{4, 4}, 0));
            if (screenSnapGuideAbsMmX != null) {
                int x = padding + (int) Math.round((screenSnapGuideAbsMmX - b[0]) * sc);
                g2.drawLine(x, 0, x, height);
            }
            if (screenSnapGuideAbsMmY != null) {
                int y = padding + rigHeadroomPx() + (int) Math.round((screenSnapGuideAbsMmY - b[1]) * sc);
                g2.drawLine(0, y, width, y);
            }
        }
    }

    /** Живая отрисовка ЕЩЁ НЕ сохранённой цепочки поверх детальной сетки конкретного
     *  экрана — аналог блока isChainBuilding() в CanvasPanel.paintComponent, которого
     *  раньше не было в общем обзоре сцены (Task #8/v1.6): построение цепочки из
     *  «показать все экраны» уже было возможно (см. setChainController), но сам
     *  процесс был не виден, пока не завершишь Esc-ом. active может содержать id
     *  кабинетов ДРУГИХ экранов сцены (цепочка сигнала может физически продолжаться
     *  туда) — SchemeRenderer.drawChain сам молча пропускает отрезки, где кабинет не
     *  резолвится на этот scr, как и paintScheme выше для сохранённых цепочек. */
    private void drawChainBuildingOverlay(Graphics2D g2, Screen s, int cellW, int cellH, int offX, int offY) {
        List<String> active = chainController.activeChainCabIds();
        Color c = detailPower ? Palette.phaseColor(model.getActivePhase()) : Color.WHITE;
        CabinetType type = model.typeOf(s);
        java.awt.Stroke prevStroke = g2.getStroke();
        Color prevColor = g2.getColor();
        for (String id : active) {
            CabinetInstance cab = s.cabinetById(id);
            if (cab != null) {
                int x = cabX(cab, type, cellW, offX);
                int y = cabY(cab, type, cellH, offY);
                int ew = effW(cab, type, cellW);
                int eh = effH(cab, type, cellH);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(x + 1, y + 1, ew - 2, eh - 2);
            }
        }
        SchemeRenderer.drawChain(g2, s, active, c, true, cellW, cellH, offX, offY, model.typeOf(s), model.getWorkspace());
        if (s == model.getCurrentScreen()) {
            int cr = chainController.cursorRow();
            int cc = chainController.cursorCol();
            if (cr >= 0 && cc >= 0) {
                int x = offX + cc * cellW;
                int y = offY + cr * cellH;
                g2.setColor(Color.YELLOW);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4, 3}, 0));
                g2.drawRect(x + 3, y + 3, cellW - 6, cellH - 6);
            }
        }
        g2.setStroke(prevStroke);
        g2.setColor(prevColor);
    }

    /** Полосы подвеса НАД экраном, в пикселях текущей раскладки — сверху вниз: точки
     *  (жёлтые треугольники — то, что пользователь называет "лебёдками", верхняя
     *  точка подвеса), затем ферма прямо над экраном (баг-репорт 2026-09-14: раньше
     *  порядок был обратный — исправлено). Отдельного визуального "колпака" лебёдок
     *  больше нет (тоже баг-репорт — был лишним, самих лебёдок как таковых на схеме
     *  не рисуем, только точку подвеса). Общая геометрия и для отрисовки ({@link
     *  #paint}), и для хит-теста клика ({@link #rigLevelAt}) — один источник истины.
     *  <b>Экран — единственная помеченная/кликабельная сущность</b>: вся полоса
     *  целиком (или {@link #WHITE_STRIP_H заглушка}, если места не хватило) — ОДНА
     *  зона клика, относящаяся к экрану. */
    private record RigBands(int pointsTop, int pointsBottom, int trussTop, int trussBottom) {
    }

    private static final int RIG_POINTS_GAP = 6;
    private static final int RIG_BAND_GAP = 4;
    /** Фиксированные размеры — баг-репорт 2026-09-14: раньше высота полос/размер
     *  точек масштабировались от пиксельной ширины экрана ({@code w}), из-за чего
     *  крупные экраны получали заметно более крупные метки фермы/точек, чем мелкие —
     *  по фидбэку размер должен быть ОДИНАКОВЫМ для всех экранов независимо от их
     *  собственной ширины. */
    private static final int RIG_TRUSS_H = 10;
    private static final int RIG_POINT_SIZE = 9;
    /** Суммарная высота полос + зазоров (см. {@link #rigBands}): pointSize(9) +
     *  gap(4) + trussH(10) + gap(6) = 29, с запасом — см. {@link #rigHeadroomPx}. */
    private static final int RIG_TOP_HEADROOM_PX = 34;
    /** Высота полосы-заглушки ("здесь есть ферма, но полностью не показана из-за
     *  нехватки места") — см. {@link #hasRoomAboveForRigBand}/{@link #paint}. */
    private static final int WHITE_STRIP_H = 3;

    private static RigBands rigBands(int screenTopY) {
        int trussBottom = screenTopY - RIG_POINTS_GAP;
        int trussTop = trussBottom - RIG_TRUSS_H;
        int pointsBottom = trussTop - RIG_BAND_GAP;
        int pointsTop = pointsBottom - RIG_POINT_SIZE;
        return new RigBands(pointsTop, pointsBottom, trussTop, trussBottom);
    }

    /** true, если между верхним краем экрана ({@code y}) и {@code neededHeight}
     *  пикселей выше него НЕТ другого экрана сцены — при вертикальной стыковке
     *  экранов (см. "экраны могут располагаться друг под другом") полный размер
     *  полосы туда физически не влезает; вызывающий код рисует/кликает {@link
     *  #WHITE_STRIP_H заглушку} вместо полной полосы (баг-репорт 2026-09-14: по
     *  фидбэку — НЕ подсвечивать столкновение красным, как обычное физическое
     *  наложение кабинетов, а именно схлопывать полосу до тонкой белой черты; при
     *  приближении, когда места снова достаточно, полоса рисуется полностью).
     *  Смотрит на РЕАЛЬНЫЕ боксы всех экранов сцены (не только соседей по списку),
     *  совпадение по X — пересечение прямоугольников, не только точное совпадение
     *  краёв. */
    private boolean hasRoomAboveForRigBand(Scene scene, Screen self, double[] b, double sc, int padding,
                                            int x, int y, int w, int neededHeight) {
        int bandTop = y - neededHeight;
        for (Screen other : scene.getScreens()) {
            if (other == self) {
                continue;
            }
            CabinetType ot = model.typeOf(other);
            if (ot == null) {
                continue;
            }
            int[] ob = screenBoxPx(other, ot, b, sc, padding);
            int ox = ob[0], oy = ob[1], ow = ob[2], oh = ob[3];
            boolean overlapsX = x < ox + ow && ox < x + w;
            boolean bottomInBand = oy + oh > bandTop && oy + oh <= y;
            if (overlapsX && bottomInBand) {
                return false;
            }
        }
        return true;
    }

    /** Экран под пикселем ({@code px},{@code py}) в полосе подвеса НАД ним (ферма/
     *  точки — см. {@link #rigBands} — либо {@link #WHITE_STRIP_H заглушка}, если
     *  {@link #hasRoomAboveForRigBand} не хватило места), для клика на холсте.
     *  <b>Одна и та же метка на весь экран</b> (баг-репорт v3.0 2026-09-14: раньше
     *  лебёдки и ферма были самостоятельными кликабельными уровнями со своими
     *  карточками — по фидбэку экран должен быть единственным помеченным элементом):
     *  клик по любой части полосы (или заглушки) ЛЮБОГО RIGGED-экрана сцены
     *  возвращает {@code screen} — {@code SetupStagePanel} открывает для него ОДНУ
     *  карточку "Подвес" со всеми полями сразу. Геометрия — та же, что рисует {@link
     *  #paint}, пересчитанная заново из состояния модели (тот же приём, что {@link
     *  #screenAt}: без кэша между отрисовкой и кликом). {@code null}, если клик мимо
     *  полосы ЛЮБОГО экрана сцены — в т.ч. клик по самому прямоугольнику экрана (тот
     *  должен ловить обычный {@link #screenAt}, не эта функция). */
    private Screen rigLevelAt(int px, int py) {
        Scene scene = model.getCurrentScene();
        double[] b = boundsMm();
        if (scene == null || b == null) {
            return null;
        }
        double sc = scaleFor(b, getWidth(), getHeight());
        int padding = padding();
        for (Screen s : scene.getScreens()) {
            if (s.getMountType() != com.vjstb.ledscheme.model.ScreenMountType.RIGGED
                    || s.getRiggingPointsCount() <= 0) {
                continue;
            }
            CabinetType t = model.typeOf(s);
            if (t == null) {
                continue;
            }
            int[] box = screenBoxPx(s, t, b, sc, padding);
            int x = box[0], y = box[1], w = box[2];
            if (px < x || px > x + w) {
                continue;
            }
            RigBands rb = rigBands(y);
            int neededHeight = rb.trussBottom() - rb.pointsTop();
            boolean hasRoom = hasRoomAboveForRigBand(scene, s, b, sc, padding, x, y, w, neededHeight);
            int top = hasRoom ? rb.pointsTop() : y - WHITE_STRIP_H;
            if (py >= top && py <= y) {
                return s;
            }
        }
        return null;
    }

    /** Точки подвеса — маркеры-треугольники (визуализация результата «Рассчитать точки
     *  подвеса» из прерига), позиционированные по РЕАЛЬНЫМ координатам расчёта ({@link
     *  com.vjstb.ledscheme.service.RiggingCalc.PointLoad#xMm()}, считанным от краёв фермы —
     *  см. {@code RiggingCalc} class-javadoc), а не равномерным делением по числу точек, как
     *  раньше — иначе при свесе/отступе фермы (см. {@link #drawRiggingTruss}) точки на этой
     *  картинке визуально разъехались бы с фермой, нарисованной над ними. Полоса ({@code
     *  topY}..{@code bottomY}) — {@link #rigBands}, слегка НАД самим экраном (баг-репорт
     *  v3.0 — раньше треугольник начинался прямо на верхнем крае экрана, сливаясь с ним).
     *  {@code widthMm <= 0} (тип кабинета неизвестен) — точки не рисуются вовсе
     *  (масштабировать нечем). Рисуются в общей раскладке сцены (этот же класс — «Показать
     *  все экраны» и мини-превью прерига), а не только как число в поле. */
    private static void drawRiggingPoints(Graphics2D g2, com.vjstb.ledscheme.service.RiggingCalc.Result result,
            double widthMm, int x, int topY, int bottomY, int w) {
        int n = result.points().size();
        if (n == 0 || widthMm <= 0) {
            return;
        }
        int size = bottomY - topY;
        Color prevColor = g2.getColor();
        java.awt.Stroke prevStroke = g2.getStroke();
        g2.setColor(Color.YELLOW);
        g2.setStroke(new BasicStroke(1f));
        for (com.vjstb.ledscheme.service.RiggingCalc.PointLoad p : result.points()) {
            int px = x + (int) Math.round(p.xMm() / widthMm * w);
            int[] xs = {px - size / 2, px + size / 2, px};
            int[] ys = {topY, topY, bottomY};
            g2.fillPolygon(xs, ys, 3);
            g2.drawPolygon(xs, ys, 3);
        }
        g2.setColor(prevColor);
        g2.setStroke(prevStroke);
    }

    /** Ферма подвеса — сегменты каталожных длин (см. {@code service.TrussCalc}) рисуются в
     *  полосе ({@code topY}..{@code bottomY}, из {@link #rigBands}) НАД рядом точек, той же
     *  пиксельной шкалой (мм экрана → px), со сдвигом на {@code truss.leftOffsetMm()} — та
     *  же формула перевода координат, что {@code RiggingCalc.compute} использует для {@code
     *  PointLoad#xMm()}. {@code result.pieces()} разворачивается в ОТДЕЛЬНЫЕ сегменты (не
     *  один прямоугольник на группу одинаковых длин — иначе стык между двумя кусками одной
     *  длины визуально терялся бы), отсортированные по убыванию длины и уложенные слева
     *  направо в координатах, локальных для фермы — раскладка ДЕТЕРМИНИРОВАНА, но не
     *  привязана к реальному проектному порядку сегментов (тот нигде не хранится). Рисуются
     *  РЕАЛЬНОЙ физической длиной каждого куска, БЕЗ обрезки по целевой длине экрана —
     *  баг-репорт 2026-09-14: раньше последний сегмент клэмпился {@code
     *  Math.min(cursorMm+len, targetMm)}, из-за чего ферма из одного куска 2м на 1.5м экран
     *  визуально выглядела как ~1м (обрезанная по краю экрана), маскируя реальный физический
     *  излишек, который как раз важно ВИДЕТЬ. Диапазон закономерно может выходить за {@code
     *  [x, x+w]} и при свесе фермы, и при излишке комплекта — это ожидаемо, не обрезается. */
    private static void drawRiggingTruss(Graphics2D g2, com.vjstb.ledscheme.service.TrussCalc.Result truss,
            double widthMm, int x, int topY, int bottomY, int w) {
        if (truss.pieces() == null || truss.pieces().isEmpty() || widthMm <= 0) {
            return;
        }
        List<Double> segMm = new ArrayList<>();
        for (com.vjstb.ledscheme.service.CableSpecCalc.Piece p : truss.pieces()) {
            for (int i = 0; i < p.count(); i++) {
                segMm.add(p.lengthM() * 1000.0);
            }
        }
        segMm.sort(java.util.Comparator.reverseOrder());

        int trussH = bottomY - topY;
        double leftOffsetMm = truss.leftOffsetMm();
        double cursorMm = 0;
        Color prevColor = g2.getColor();
        for (double len : segMm) {
            double startMm = cursorMm;
            double endMm = cursorMm + len;
            int px1 = x + (int) Math.round((startMm - leftOffsetMm) / widthMm * w);
            int px2 = x + (int) Math.round((endMm - leftOffsetMm) / widthMm * w);
            if (px2 > px1) {
                g2.setColor(TRUSS_FILL);
                g2.fillRect(px1, topY, px2 - px1, trussH);
                g2.setColor(Color.BLACK);
                g2.drawRect(px1, topY, px2 - px1, trussH);
            }
            cursorMm = endMm;
        }
        g2.setColor(prevColor);
    }

    /** Кабинет + экран, которому он принадлежит, + абсолютный центр в пикселях
     *  текущего обзора сцены — null, если кабинет не найден или его экран не
     *  отрисован детально (см. screenBoxes в paint()). */
    private record CabinetLoc(Screen screen, int cx, int cy) { }

    private CabinetLoc locateCabinet(Scene scene, java.util.Map<String, int[]> screenBoxes, String cabId) {
        for (Screen s : scene.getScreens()) {
            CabinetInstance cab = s.cabinetById(cabId);
            if (cab == null) {
                continue;
            }
            int[] box = screenBoxes.get(s.getId());
            if (box == null) {
                return null;
            }
            CabinetType defaultType = model.typeOf(s);
            int x = cabX(cab, defaultType, box[2], box[0]);
            int y = cabY(cab, defaultType, box[3], box[1]);
            // Точка привязки моста между экранами — тот же принцип, что и внутри
            // одного экрана (см. SchemeRenderer.cabinetConnectionAnchor, Task #93/v1.5):
            // для непрямоугольного кабинета не геометрический центр ячейки, а центр
            // тяжести самой фигуры.
            CabinetType effective = cab.getCabinetTypeId() != null
                    ? model.getWorkspace().cabinetTypeById(cab.getCabinetTypeId()) : null;
            if (effective == null) {
                effective = defaultType;
            }
            com.vjstb.ledscheme.model.CabinetShape shape = cab.getShapeOverride() != null ? cab.getShapeOverride()
                    : (effective != null ? effective.getShape() : null);
            double rotationDeg = SchemeRenderer.effectiveRotationDeg(cab, effective);
            java.awt.Point p = SchemeRenderer.cabinetConnectionAnchor(x, y, box[2], box[3], shape, rotationDeg);
            return new CabinetLoc(s, p.x, p.y);
        }
        return null;
    }

    /** Дорисовывает сам ПЕРЕХОД цепочки (питания или сигнала) через границу между
     *  двумя экранами сцены — единственный отрезок, который {@link SchemeRenderer#paintScheme}
     *  нарисовать не может (обе точки в разных локальных системах координат разных
     *  экранов). Внутренние отрезки цепочки внутри каждого отдельного экрана рисует
     *  сам paintScheme (ему передаётся полный список цепочек сцены — см. paint()). */
    private void drawCrossScreenBridges(Graphics2D g2, Scene scene,
                                         List<com.vjstb.ledscheme.model.PowerChain> powerChains,
                                         List<com.vjstb.ledscheme.model.SignalChain> signalChains,
                                         java.util.Map<String, int[]> screenBoxes) {
        for (com.vjstb.ledscheme.model.PowerChain chain : powerChains) {
            drawBridgeSegments(g2, scene, chain.getCabinetInstanceIds(),
                    Palette.phaseColor(chain.getPhase()), screenBoxes);
        }
        for (int i = 0; i < signalChains.size(); i++) {
            drawBridgeSegments(g2, scene, signalChains.get(i).getCabinetInstanceIds(),
                    Palette.signalColor(i), screenBoxes);
        }
    }

    private void drawBridgeSegments(Graphics2D g2, Scene scene, List<String> ids, Color color,
                                     java.util.Map<String, int[]> screenBoxes) {
        for (int k = 0; k < ids.size() - 1; k++) {
            CabinetLoc a = locateCabinet(scene, screenBoxes, ids.get(k));
            CabinetLoc b = locateCabinet(scene, screenBoxes, ids.get(k + 1));
            if (a == null || b == null || a.screen() == b.screen()) {
                continue;
            }
            int[] boxA = screenBoxes.get(a.screen().getId());
            int[] boxB = screenBoxes.get(b.screen().getId());
            int minCell = Math.min(Math.min(boxA[2], boxA[3]), Math.min(boxB[2], boxB[3]));
            SchemeRenderer.drawCrossScreenSegment(g2, a.cx(), a.cy(), b.cx(), b.cy(), color, minCell);
        }
    }

    /** Экраны, чьи прямоугольники (мм) пересекаются хотя бы с одним другим экраном
     *  сцены — чисто физическое наложение кабинетов (без учёта полосы подвеса, см.
     *  {@link #hasRoomAboveForRigBand} — та коллизия обрабатывается отдельно, белой
     *  полосой-заглушкой, не этим красным/пунктирным выделением). */
    private Set<Screen> overlappingScreens(Scene scene) {
        Set<Screen> result = new HashSet<>();
        List<Screen> screens = scene.getScreens();
        for (int i = 0; i < screens.size(); i++) {
            Screen a = screens.get(i);
            CabinetType ta = model.typeOf(a);
            if (ta == null) {
                continue;
            }
            double aw = a.getCols() * ta.getWidthMm();
            double ah = a.getRows() * ta.getHeightMm();
            for (int j = i + 1; j < screens.size(); j++) {
                Screen b = screens.get(j);
                CabinetType tb = model.typeOf(b);
                if (tb == null) {
                    continue;
                }
                double bw = b.getCols() * tb.getWidthMm();
                double bh = b.getRows() * tb.getHeightMm();
                boolean disjoint = a.getPosXMm() + aw <= b.getPosXMm()
                        || b.getPosXMm() + bw <= a.getPosXMm()
                        || a.getPosYMm() + ah <= b.getPosYMm()
                        || b.getPosYMm() + bh <= a.getPosYMm();
                if (!disjoint) {
                    result.add(a);
                    result.add(b);
                }
            }
        }
        return result;
    }

    private static Color blendRed(Color base) {
        return new Color(
                Math.round(base.getRed() * 0.6f + 255 * 0.4f),
                Math.round(base.getGreen() * 0.6f),
                Math.round(base.getBlue() * 0.6f));
    }

    private static Color blendGreen(Color base, double t) {
        float f = (float) Math.max(0, Math.min(1, t));
        return new Color(
                Math.round(base.getRed() * (1 - f)),
                Math.round(base.getGreen() * (1 - f) + 0xa0 * f),
                Math.round(base.getBlue() * (1 - f) + 0x40 * f));
    }

    /** Цветная метка зоны/площадки (v3.0) — тонирует базовую заливку в сторону цвета
     *  метки, не заменяет её целиком (иначе на тёмном фоне холста тусклые метки типа
     *  {@link com.vjstb.ledscheme.model.ScreenTagColor#YELLOW} выглядели бы грязно,
     *  а различить current/не-current экран по заливке стало бы нельзя). */
    private static Color blendTag(Color base, Color tag) {
        float f = 0.45f;
        return new Color(
                Math.round(base.getRed() * (1 - f) + tag.getRed() * f),
                Math.round(base.getGreen() * (1 - f) + tag.getGreen() * f),
                Math.round(base.getBlue() * (1 - f) + tag.getBlue() * f));
    }

    /** Доля НЕ скрытых кабинетов экрана, уже занятых какой-либо цепочкой (питания
     *  или сигнала, включая цепочку, физически продолжающуюся сюда с ДРУГОГО экрана
     *  сцены) — используется, чтобы в компактном обзоре (мини-превью прерига) экран
     *  визуально показывал текущее состояние расключения, а не выглядел пустым
     *  независимо от факта. Цепочки хранятся на уровне сцены (Task #78), поэтому
     *  здесь достаточно одного прохода по общему списку сцены — без перебора
     *  "чужих" экранов, как раньше. */
    private double wiredFraction(Screen s) {
        int total = 0;
        for (CabinetInstance c : s.getCabinets()) {
            if (!c.isHidden()) {
                total++;
            }
        }
        if (total == 0) {
            return 0;
        }
        Scene scene = model.getCurrentScene();
        java.util.Set<String> wired = new HashSet<>();
        if (scene != null) {
            for (com.vjstb.ledscheme.model.PowerChain pc : scene.getPowerChains()) {
                wired.addAll(pc.getCabinetInstanceIds());
            }
            for (com.vjstb.ledscheme.model.SignalChain sc : scene.getSignalChains()) {
                wired.addAll(sc.getCabinetInstanceIds());
            }
        }
        int wiredCount = 0;
        for (CabinetInstance c : s.getCabinets()) {
            if (!c.isHidden() && wired.contains(c.getId())) {
                wiredCount++;
            }
        }
        return (double) wiredCount / total;
    }

    /** Привязка кандидата свободного мм-смещения кабинета к соседям по СЕТКЕ (те же
     *  4 непосредственных соседа, что даёт rowIndex±1/colIndex±1 — Task #7/v1.6):
     *  сравнивает абсолютную (относительно левого верхнего угла экрана, в мм) позицию
     *  кандидата с гранями каждого соседа (его собственная позиция ± ширина/высота
     *  типа) и с «родной» позицией сетки самого кабинета (без смещения — чтобы легко
     *  вернуть кабинет на штатное место), примерно как CanvasEditorPanel.snap() для
     *  размещений канваса, только здесь сравнение сразу в мм (без промежуточного
     *  перевода в px — то, что двигаем, само по себе мм-величина). Кросс-экранные
     *  соседи сознательно не учитываются (эта же ячейка может быть физически рядом
     *  с кабинетом другого экрана только по счастливой координате — надёжно определить
     *  такое соседство без дополнительной геометрии нельзя, разумный первый шаг —
     *  сетка одного экрана). */
    private double[] snapCabinetOffset(Screen s, CabinetInstance cab, double candidateOffX, double candidateOffY,
                                        double sc) {
        snapGuideAbsMmX = null;
        snapGuideAbsMmY = null;
        CabinetType t = model.typeOf(s);
        if (t == null || t.getWidthMm() <= 0 || t.getHeightMm() <= 0) {
            return new double[]{candidateOffX, candidateOffY};
        }
        // Порог задан в ЭКРАННЫХ пикселях (не в мм!), переводится в мм через текущий
        // масштаб здесь же — фиксированный порог в мм на практике оказывался то
        // слишком тугим, то почти неощутимым в зависимости от масштаба прериг-
        // превью: при сильном уменьшении (много экранов сразу) пара мм — это доля
        // экранного пикселя, курсором столько не поймать.
        double thresholdMm = settings.activeProfile().getSnapThresholdPx() / Math.max(0.0001, sc);
        int strength = settings.activeProfile().getSnapStrengthPercent();
        double myW = t.getWidthMm();
        double myH = t.getHeightMm();
        double candAbsX = cab.getColIndex() * myW + candidateOffX;
        double candAbsY = cab.getRowIndex() * myH + candidateOffY;
        List<Double> xTargets = new java.util.ArrayList<>();
        List<Double> yTargets = new java.util.ArrayList<>();
        xTargets.add(cab.getColIndex() * myW);
        yTargets.add(cab.getRowIndex() * myH);
        int[][] neighborDeltas = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : neighborDeltas) {
            CabinetInstance n = s.cabinetAt(cab.getRowIndex() + d[0], cab.getColIndex() + d[1]);
            if (n == null) {
                continue;
            }
            double nAbsX = n.getColIndex() * myW + n.getOffsetXMm();
            double nAbsY = n.getRowIndex() * myH + n.getOffsetYMm();
            xTargets.add(nAbsX);
            xTargets.add(nAbsX + myW);
            xTargets.add(nAbsX - myW);
            yTargets.add(nAbsY);
            yTargets.add(nAbsY + myH);
            yTargets.add(nAbsY - myH);
        }
        double snappedAbsX = closestMm(candAbsX, xTargets, thresholdMm, strength, true);
        double snappedAbsY = closestMm(candAbsY, yTargets, thresholdMm, strength, false);
        return new double[]{snappedAbsX - cab.getColIndex() * myW, snappedAbsY - cab.getRowIndex() * myH};
    }

    private double closestMm(double value, List<Double> targets, double threshold, int strengthPercent,
                              boolean isXAxis) {
        double best = value;
        double bestDist = threshold;
        Double bestTarget = null;
        for (double target : targets) {
            double d = Math.abs(target - value);
            if (d < bestDist) {
                bestDist = d;
                bestTarget = target;
            }
        }
        if (bestTarget != null) {
            best = SnapMath.blend(value, bestTarget, strengthPercent);
            if (isXAxis) {
                snapGuideAbsMmX = bestTarget;
            } else {
                snapGuideAbsMmY = bestTarget;
            }
        }
        return best;
    }

    /** Технологический зазор между экранами (кабель-каналы/обслуживание) — вторая
     *  "встык"-цель у {@link #snapScreenPosition}, рядом с полностью вплотную
     *  (0 мм): пользователь двигает экран — сначала ловит примыкание ВПЛОТНУЮ к
     *  соседу, чуть дальше (тот же порог, просто другая абсолютная позиция) —
     *  примыкание ЧЕРЕЗ этот зазор, а дальше отпускает и ищет следующего соседа.
     *  Фиксированное значение первой версии — не читается из профиля настроек
     *  (в отличие от {@code getSnapThresholdPx}/{@code getSnapStrengthPercent}). */
    private static final double SCREEN_GAP_MM = 20;

    /** v3.0: привязка кандидата позиции ЦЕЛОГО экрана (posXMm/posYMm, не свободного
     *  смещения кабинета внутри сетки — см. {@link #snapCabinetOffset} для того
     *  случая) к краям ДРУГИХ экранов сцены — тот же принцип (порог в px текущего
     *  масштаба, сила прилипания из профиля), но 6 целей на каждого соседа по
     *  каждой оси (экраны, в отличие от однотипных кабинетов, могут быть разной
     *  ширины/высоты, поэтому "выравнять по левому краю" и "выравнять по правому
     *  краю" — не взаимозаменяемые цели, как у кабинетов одного типа): левый край к
     *  левому краю соседа, левый край к правому краю соседа (встык вплотную справа
     *  от соседа), левый край к правому краю соседа + {@link #SCREEN_GAP_MM}
     *  (встык через зазор справа), правый край к правому краю соседа, правый край
     *  к левому краю соседа (встык вплотную слева), правый край к левому краю
     *  соседа − {@link #SCREEN_GAP_MM} (встык через зазор слева). {@link
     *  #closestScreenMm} сам находит ближайшую из ВСЕХ целей на каждый вызов (не
     *  запоминает "уже прилипло") — поэтому при продолжении перетаскивания мимо
     *  одной цели пользователь естественно попадает на следующую подходящую, без
     *  специальной логики "отпустить и искать дальше". Цели считаются сразу в
     *  терминах posXMm/posYMm (не "левый/верхний край"), поэтому результат можно
     *  писать в Screen#setPosXMm/setPosYMm без обратного пересчёта. */
    private double[] snapScreenPosition(Screen dragging, double candidateX, double candidateY, double sc) {
        screenSnapGuideAbsMmX = null;
        screenSnapGuideAbsMmY = null;
        Scene scene = model.getCurrentScene();
        CabinetType myType = model.typeOf(dragging);
        if (scene == null || myType == null) {
            return new double[]{candidateX, candidateY};
        }
        double[] myExt = screenExtentMm(dragging, myType);
        double thresholdMm = settings.activeProfile().getSnapThresholdPx() / Math.max(0.0001, sc);
        int strength = settings.activeProfile().getSnapStrengthPercent();
        List<Double> xTargets = new java.util.ArrayList<>();
        List<Double> yTargets = new java.util.ArrayList<>();
        for (Screen other : scene.getScreens()) {
            if (other == dragging) {
                continue;
            }
            CabinetType ot = model.typeOf(other);
            if (ot == null) {
                continue;
            }
            double[] oExt = screenExtentMm(other, ot);
            double oLeft = other.getPosXMm() + oExt[0];
            double oRight = other.getPosXMm() + oExt[2];
            double oTop = other.getPosYMm() + oExt[1];
            double oBottom = other.getPosYMm() + oExt[3];
            xTargets.add(oLeft - myExt[0]);
            xTargets.add(oRight - myExt[0]);
            xTargets.add(oRight + SCREEN_GAP_MM - myExt[0]);
            xTargets.add(oRight - myExt[2]);
            xTargets.add(oLeft - myExt[2]);
            xTargets.add(oLeft - SCREEN_GAP_MM - myExt[2]);
            yTargets.add(oTop - myExt[1]);
            yTargets.add(oBottom - myExt[1]);
            yTargets.add(oBottom + SCREEN_GAP_MM - myExt[1]);
            yTargets.add(oBottom - myExt[3]);
            yTargets.add(oTop - myExt[3]);
            yTargets.add(oTop - SCREEN_GAP_MM - myExt[3]);
        }
        double snappedX = closestScreenMm(candidateX, xTargets, thresholdMm, strength, true);
        double snappedY = closestScreenMm(candidateY, yTargets, thresholdMm, strength, false);
        return new double[]{snappedX, snappedY};
    }

    private double closestScreenMm(double value, List<Double> targets, double threshold, int strengthPercent,
                                    boolean isXAxis) {
        double best = value;
        double bestDist = threshold;
        Double bestTarget = null;
        for (double target : targets) {
            double d = Math.abs(target - value);
            if (d < bestDist) {
                bestDist = d;
                bestTarget = target;
            }
        }
        if (bestTarget != null) {
            best = SnapMath.blend(value, bestTarget, strengthPercent);
            if (isXAxis) {
                screenSnapGuideAbsMmX = bestTarget;
            } else {
                screenSnapGuideAbsMmY = bestTarget;
            }
        }
        return best;
    }

    /** Экранная позиция ячейки — сеточная позиция плюс свободное мм-смещение (см.
     *  CabinetInstance.getOffsetXMm/getOffsetYMm, Task #7/v1.6), переведённое в
     *  пиксели ТЕКУЩЕГО масштаба через {@link ScreenLogic#offsetPx} — тот же приём,
     *  что и в SchemeRenderer.cabX/cabY (независимая копия: этот класс сам не
     *  использует SchemeRenderer для отрисовки сетки в detailMode, паддинг/сетку
     *  считает сам). type может быть null — тогда смещение не применяется. */
    private static int cabX(CabinetInstance cab, CabinetType type, int cellW, int offX) {
        double dx = type != null ? ScreenLogic.offsetPx(cab.getOffsetXMm(), cellW, type.getWidthMm()) : 0;
        return offX + (int) Math.round(cab.getColIndex() * cellW + dx);
    }

    private static int cabY(CabinetInstance cab, CabinetType type, int cellH, int offY) {
        double dy = type != null ? ScreenLogic.offsetPx(cab.getOffsetYMm(), cellH, type.getHeightMm()) : 0;
        return offY + (int) Math.round(cab.getRowIndex() * cellH + dy);
    }

    /** Размер overlay-прямоугольника (подсветка строящейся цепочки) для ячейки с
     *  переопределённым типом другого физического размера — см. CanvasPanel.effW/
     *  effH и SchemeRenderer.paintScheme (ScreenLogic.effectiveCellW/H). */
    private int effW(CabinetInstance cab, CabinetType defaultType, int cellW) {
        CabinetType eff = ScreenLogic.effectiveType(cab, defaultType, model.getWorkspace());
        return (int) Math.round(ScreenLogic.effectiveCellW(eff, defaultType, cellW));
    }

    private int effH(CabinetInstance cab, CabinetType defaultType, int cellH) {
        CabinetType eff = ScreenLogic.effectiveType(cab, defaultType, model.getWorkspace());
        return (int) Math.round(ScreenLogic.effectiveCellH(eff, defaultType, cellH));
    }

    private double boundsToScale(double[] b, int width, int height) {
        double boundW = Math.max(1, b[2] - b[0]);
        double boundH = Math.max(1, b[3] - b[1]);
        int padding = padding();
        double availW = Math.max(50, width - padding * 2);
        double availH = Math.max(50, height - padding * 2);
        return Math.max(0.0005, Math.min(availW / boundW, availH / boundH));
    }
}
