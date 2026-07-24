package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.ScreenStats;
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

    private final AppModel model;
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

    public SceneCanvasPanel(AppModel model) {
        this.model = model;
        setBackground(Palette.BG);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    if (detailMode && chainController != null && chainController.isChainBuilding()) {
                        // Кабинет для удаления из строящейся цепочки может принадлежать
                        // ЛЮБОМУ экрану — цепочка сигнала может физически продолжаться
                        // с одного экрана на другой, не только текущему.
                        Object[] hit = screenAndCabinetAt(e.getX(), e.getY());
                        if (hit != null && hit[1] != null) {
                            chainController.removeFromActive(((CabinetInstance) hit[1]).getId());
                        }
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
                lastDragCabId = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!detailMode || chainController == null || !chainController.isChainBuilding()
                        || !javax.swing.SwingUtilities.isLeftMouseButton(e)) {
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
            int x = padding + (int) Math.round((s.getPosXMm() - b[0]) * sc);
            int y = padding + (int) Math.round((s.getPosYMm() - b[1]) * sc);
            int cellW = (int) Math.round(t.getWidthMm() * sc);
            int cellH = (int) Math.round(t.getHeightMm() * sc);
            if (cellW <= 0 || cellH <= 0) {
                continue;
            }
            int w = s.getCols() * cellW;
            int h = s.getRows() * cellH;
            if (px >= x && px <= x + w && py >= y && py <= y + h) {
                int col = (px - x) / cellW;
                int row = (py - y) / cellH;
                CabinetInstance cab = s.cabinetAt(row, col);
                return new Object[]{s, cab};
            }
        }
        return null;
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
        if (!detailMode || detailFit) {
            return super.getPreferredSize();
        }
        double[] b = boundsMm();
        double scale = DETAIL_PX_PER_MM * detailZoom;
        int w = b != null ? (int) Math.round((b[2] - b[0]) * scale) + PADDING * 2 : 600;
        int h = b != null ? (int) Math.round((b[3] - b[1]) * scale) + PADDING * 2 : 400;
        return new Dimension(Math.max(200, w), Math.max(200, h));
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
            double w = s.getCols() * t.getWidthMm();
            double h = s.getRows() * t.getHeightMm();
            minX = Math.min(minX, s.getPosXMm());
            minY = Math.min(minY, s.getPosYMm());
            maxX = Math.max(maxX, s.getPosXMm() + w);
            maxY = Math.max(maxY, s.getPosYMm() + h);
            any = true;
        }
        return any ? new double[]{minX, minY, maxX, maxY} : null;
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
            int x = padding + (int) Math.round((s.getPosXMm() - b[0]) * sc);
            int y = padding + (int) Math.round((s.getPosYMm() - b[1]) * sc);
            int w = (int) Math.round(s.getCols() * t.getWidthMm() * sc);
            int h = (int) Math.round(s.getRows() * t.getHeightMm() * sc);
            if (px >= x && px <= x + w && py >= y && py <= y + h) {
                return s;
            }
        }
        return null;
    }

    private double scaleFor(double[] b, int width, int height) {
        if (detailMode && !detailFit) {
            return DETAIL_PX_PER_MM * detailZoom;
        }
        return boundsToScale(b, width, height);
    }

    /** Рендерит текущий вид в изображение заданного размера (для экспорта). */
    public BufferedImage renderImage(int width, int height) {
        BufferedImage img = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
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
            double wMm = s.getCols() * t.getWidthMm();
            double hMm = s.getRows() * t.getHeightMm();
            int x = padding + (int) Math.round((s.getPosXMm() - b[0]) * sc);
            int y = padding + (int) Math.round((s.getPosYMm() - b[1]) * sc);
            int w = Math.max(2, (int) Math.round(wMm * sc));
            int h = Math.max(2, (int) Math.round(hMm * sc));

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
                    Color base = current ? Palette.PHASE_NONE : Palette.PANEL;
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

            if (showRiggingPoints && s.getMountType() == com.vjstb.ledscheme.model.ScreenMountType.RIGGED
                    && s.getRiggingPointsCount() > 0) {
                drawRiggingPoints(g2, s.getRiggingPointsCount(), x, y, w);
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
                        SchemeRenderer.paintScheme(go, s, t, detailPower, cellW, cellH, x, y, model.getWorkspace(),
                                scenePowerChains, sceneSignalChains);
                        go.dispose();
                    } else {
                        SchemeRenderer.paintScheme(g2, s, t, detailPower, cellW, cellH, x, y, model.getWorkspace(),
                                scenePowerChains, sceneSignalChains);
                    }
                    screenBoxes.put(s.getId(), new int[]{x, y, cellW, cellH});
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
            g2.drawString(Math.round(wMm) + "×" + Math.round(hMm) + " мм", x + 4, y + 43);
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
        }

        if (!overlapping.isEmpty() && !compact) {
            g2.setColor(Color.RED);
            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            g2.drawString("⚠ Экраны перекрываются — проверьте позиции (X/Y) или нажмите «Расставить без наложения»",
                    padding, height - 10);
        }
        g2.dispose();
    }

    /** Точки подвеса — маркеры-треугольники, равномерно разнесённые по верхнему
     *  краю экрана (визуализация результата «Рассчитать точки подвеса» из прерига).
     *  Рисуются в общей раскладке сцены (этот же класс — «Показать все экраны» и
     *  мини-превью прерига), а не только как число в поле. */
    private static void drawRiggingPoints(Graphics2D g2, int count, int x, int y, int w) {
        int s = Math.max(5, Math.min(12, w / (count * 3 + 1)));
        Color prevColor = g2.getColor();
        java.awt.Stroke prevStroke = g2.getStroke();
        g2.setColor(Color.YELLOW);
        g2.setStroke(new BasicStroke(1f));
        for (int i = 0; i < count; i++) {
            int px = x + (int) Math.round((i + 0.5) * w / (double) count);
            int[] xs = {px - s / 2, px + s / 2, px};
            int[] ys = {y, y, y + s};
            g2.fillPolygon(xs, ys, 3);
            g2.drawPolygon(xs, ys, 3);
        }
        g2.setColor(prevColor);
        g2.setStroke(prevStroke);
    }

    /** Кабинет + экран, которому он принадлежит, + абсолютный центр в пикселях
     *  текущего обзора сцены — null, если кабинет не найден или его экран не
     *  отрисован детально (см. screenBoxes в paint()). */
    private record CabinetLoc(Screen screen, int cx, int cy) { }

    private static CabinetLoc locateCabinet(Scene scene, java.util.Map<String, int[]> screenBoxes, String cabId) {
        for (Screen s : scene.getScreens()) {
            CabinetInstance cab = s.cabinetById(cabId);
            if (cab == null) {
                continue;
            }
            int[] box = screenBoxes.get(s.getId());
            if (box == null) {
                return null;
            }
            int cx = box[0] + cab.getColIndex() * box[2] + box[2] / 2;
            int cy = box[1] + cab.getRowIndex() * box[3] + box[3] / 2;
            return new CabinetLoc(s, cx, cy);
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

    /** Экраны, чьи прямоугольники (мм) пересекаются хотя бы с одним другим экраном сцены. */
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

    private double boundsToScale(double[] b, int width, int height) {
        double boundW = Math.max(1, b[2] - b[0]);
        double boundH = Math.max(1, b[3] - b[1]);
        int padding = padding();
        double availW = Math.max(50, width - padding * 2);
        double availH = Math.max(50, height - padding * 2);
        return Math.max(0.0005, Math.min(availW / boundW, availH / boundH));
    }
}
