package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ScreenLogic;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/**
 * Холст схемы: рисует сетку кабинетов активного экрана и цепочки, обрабатывает
 * клики и протяжку мышью (зажатая ЛКМ) для построения цепочки.
 */
public class CanvasPanel extends JPanel {

    public interface Controller {
        boolean isChainBuilding();
        List<String> activeChainCabIds();
        /** -1, если клавиатурный курсор не установлен. */
        int cursorRow();
        int cursorCol();
        /** Вызывается и на клик, и на вход указателя в новую ячейку при зажатой ЛКМ. */
        void cabinetClicked(String cabId);
        void cabinetHovered(String cabId);
        /** ПКМ по кабинету во время построения — убрать его из строящейся цепочки. */
        void removeFromActive(String cabId);
    }

    /** Область кабинетов, выделенная протяжкой в режиме «Быстрое подключение»
     *  (см. setQuickConnectMode) — вызывающая панель (Power/Signal) должна
     *  показать выбор шаблона (см. ChainPatterns.Pattern) в точке (screenX, screenY)
     *  и применить его к диапазону [rowStart..rowEnd] x [colStart..colEnd]. */
    public interface QuickConnectListener {
        void onRegionSelected(int rowStart, int rowEnd, int colStart, int colEnd, int screenX, int screenY);
    }

    private static final int PADDING = 30;
    private static final int BASE = 84;

    private final AppModel model;
    private final com.vjstb.ledscheme.settings.SettingsManager settings;
    private final Controller controller;
    private double zoom = 1.0;
    private String lastDragCabId;
    private boolean quickConnectMode;
    private QuickConnectListener quickConnectListener;
    private Point marqueeStart;
    private Point marqueeEnd;

    public CanvasPanel(AppModel model, com.vjstb.ledscheme.settings.SettingsManager settings, Controller controller) {
        this.model = model;
        this.settings = settings;
        this.controller = controller;
        setBackground(Palette.BG);
        setFocusable(true);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (quickConnectMode) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        marqueeStart = e.getPoint();
                        marqueeEnd = e.getPoint();
                        repaint();
                    }
                    return;
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    handleRightClick(e);
                    return;
                }
                CabinetInstance cab = cabinetAt(e.getPoint());
                lastDragCabId = cab != null ? cab.getId() : null;
                if (cab != null) {
                    controller.cabinetClicked(cab.getId());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (quickConnectMode) {
                    if (marqueeStart != null && marqueeEnd != null) {
                        int[] region = regionFromMarquee();
                        java.awt.Point screenPt = e.getLocationOnScreen();
                        marqueeStart = null;
                        marqueeEnd = null;
                        repaint();
                        if (region != null && quickConnectListener != null) {
                            quickConnectListener.onRegionSelected(region[0], region[1], region[2], region[3],
                                    screenPt.x, screenPt.y);
                        }
                    }
                    return;
                }
                lastDragCabId = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (quickConnectMode) {
                    if (marqueeStart != null) {
                        marqueeEnd = e.getPoint();
                        repaint();
                    }
                    return;
                }
                CabinetInstance cab = cabinetAt(e.getPoint());
                if (cab != null && !cab.getId().equals(lastDragCabId)) {
                    lastDragCabId = cab.getId();
                    controller.cabinetClicked(cab.getId());
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                CabinetInstance cab = cabinetAt(e.getPoint());
                controller.cabinetHovered(cab != null ? cab.getId() : null);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.isControlDown()) {
                    double factor = e.getWheelRotation() < 0 ? 1.1 : 1 / 1.1;
                    zoom = Math.max(0.2, Math.min(4.0, zoom * factor));
                    revalidate();
                    repaint();
                } else {
                    getParent().dispatchEvent(e);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    public void resetZoom() {
        zoom = 1.0;
        revalidate();
        repaint();
    }

    /** Режим «Быстрое подключение» (см. QuickConnectListener) — протяжка ЛКМ
     *  выделяет прямоугольную область вместо клика-по-кабинету/построения цепочки;
     *  выключение на середине протяжки просто сбрасывает недостроенное выделение. */
    public void setQuickConnectMode(boolean enabled) {
        this.quickConnectMode = enabled;
        marqueeStart = null;
        marqueeEnd = null;
        repaint();
    }

    public boolean isQuickConnectMode() {
        return quickConnectMode;
    }

    public void setQuickConnectListener(QuickConnectListener listener) {
        this.quickConnectListener = listener;
    }

    /** Диапазон [rowStart, rowEnd, colStart, colEnd] (включительно, обрезан по
     *  факту размера экрана), накрытый прямоугольником marqueeStart..marqueeEnd —
     *  null, если экран не выбран. */
    private int[] regionFromMarquee() {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return null;
        }
        int cw = cabW();
        int ch = cabH();
        int c0 = clamp((Math.min(marqueeStart.x, marqueeEnd.x) - PADDING) / cw, 0, scr.getCols() - 1);
        int c1 = clamp((Math.max(marqueeStart.x, marqueeEnd.x) - PADDING) / cw, 0, scr.getCols() - 1);
        int r0 = clamp((Math.min(marqueeStart.y, marqueeEnd.y) - PADDING) / ch, 0, scr.getRows() - 1);
        int r1 = clamp((Math.max(marqueeStart.y, marqueeEnd.y) - PADDING) / ch, 0, scr.getRows() - 1);
        return new int[]{r0, r1, c0, c1};
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** ПКМ во время построения цепочки — убирает кабинет под курсором из
     *  строящейся цепочки (а не разрывает уже сохранённую связь, см. ниже). */
    private void handleRightClick(MouseEvent e) {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        if (controller.isChainBuilding()) {
            CabinetInstance cab = cabinetAt(e.getPoint());
            if (cab != null) {
                controller.removeFromActive(cab.getId());
            }
            return;
        }
        handleRightClickOnSavedChain(e, scr);
    }

    /** ПКМ по отрезку СОХРАНЁННОЙ (не строящейся) цепочки — контекстное меню
     *  «Разорвать связь здесь», без удаления всей цепочки. */
    private void handleRightClickOnSavedChain(MouseEvent e, Screen scr) {
        int cw = cabW();
        int ch = cabH();
        boolean power = model.getMode() == AppModel.Mode.POWER;
        // Цепочки хранятся на уровне сцены (Task #78) — link-хит-тест по-прежнему
        // только в координатах ЭТОГО экрана (linkIndexAt пропускает пары, не
        // резолвящиеся на scr), поэтому чужие (не рисуемые здесь) цепочки просто
        // не находят совпадения, как и раньше.
        Scene scene = model.getCurrentScene();
        Runnable split;
        if (power) {
            split = null;
            List<PowerChain> chains = scene != null ? scene.getPowerChains() : List.of();
            for (PowerChain chain : chains) {
                int idx = linkIndexAt(scr, chain.getCabinetInstanceIds(), e.getPoint(), cw, ch);
                if (idx >= 0) {
                    split = () -> model.splitPowerChainLink(chain.getId(), idx);
                    break;
                }
            }
        } else {
            Runnable found = null;
            List<SignalChain> chains = scene != null ? scene.getSignalChains() : List.of();
            for (SignalChain chain : chains) {
                int idx = linkIndexAt(scr, chain.getCabinetInstanceIds(), e.getPoint(), cw, ch);
                if (idx >= 0) {
                    found = () -> model.splitSignalChainLink(chain.getId(), idx);
                    break;
                }
            }
            split = found;
        }
        if (split == null) {
            return;
        }
        Runnable action = split;
        JPopupMenu menu = new JPopupMenu();
        JMenuItem item = new JMenuItem("Разорвать связь здесь");
        item.addActionListener(ev -> action.run());
        menu.add(item);
        menu.show(this, e.getX(), e.getY());
    }

    /** Индекс связи (между ids[i] и ids[i+1]), рядом с которой находится точка p,
     *  или -1. Хит-тест по расстоянию до отрезка между центрами кабинетов — теми
     *  же координатами, что использует SchemeRenderer.drawChain для отрисовки. */
    private int linkIndexAt(Screen scr, List<String> ids, Point p, int cw, int ch) {
        CabinetType type = model.typeOf(scr);
        for (int i = 0; i < ids.size() - 1; i++) {
            CabinetInstance a = scr.cabinetById(ids.get(i));
            CabinetInstance b = scr.cabinetById(ids.get(i + 1));
            if (a == null || b == null) {
                continue;
            }
            double ax = cabX(a, type, cw, PADDING) + cw / 2.0;
            double ay = cabY(a, type, ch, PADDING) + ch / 2.0;
            double bx = cabX(b, type, cw, PADDING) + cw / 2.0;
            double by = cabY(b, type, ch, PADDING) + ch / 2.0;
            if (distanceToSegment(p.x, p.y, ax, ay, bx, by) < 8) {
                return i;
            }
        }
        return -1;
    }

    /** Экранная позиция ячейки — сеточная позиция плюс свободное мм-смещение (см.
     *  CabinetInstance.getOffsetXMm/getOffsetYMm, Task #7/v1.6), переведённое в
     *  пиксели ТЕКУЩЕГО масштаба через {@link ScreenLogic#offsetPx} (тот же приём,
     *  что и в SchemeRenderer.cabX/cabY — независимая копия, этот класс сам
     *  вычисляет свои cw/ch через cabW()/cabH(), не через SchemeRenderer.cellSize).
     *  type может быть null — тогда смещение не применяется. */
    private static int cabX(CabinetInstance cab, CabinetType type, int cellW, int offX) {
        double dx = type != null ? ScreenLogic.offsetPx(cab.getOffsetXMm(), cellW, type.getWidthMm()) : 0;
        return offX + (int) Math.round(cab.getColIndex() * cellW + dx);
    }

    private static int cabY(CabinetInstance cab, CabinetType type, int cellH, int offY) {
        double dy = type != null ? ScreenLogic.offsetPx(cab.getOffsetYMm(), cellH, type.getHeightMm()) : 0;
        return offY + (int) Math.round(cab.getRowIndex() * cellH + dy);
    }

    /** Размер overlay-прямоугольника (дим занятости, подсветка строящейся цепочки,
     *  курсор) для ячейки с переопределённым типом другого физического размера —
     *  тем же приёмом, что и SchemeRenderer.paintScheme (ScreenLogic.effectiveCellW/H),
     *  иначе такой overlay остаётся размером нижней ячейки и визуально "отрезает"
     *  часть уже увеличенного (см. paintScheme) кабинета. */
    private int effW(CabinetInstance cab, CabinetType defaultType, int cellW) {
        CabinetType eff = ScreenLogic.effectiveType(cab, defaultType, model.getWorkspace());
        return (int) Math.round(ScreenLogic.effectiveCellW(eff, defaultType, cellW));
    }

    private int effH(CabinetInstance cab, CabinetType defaultType, int cellH) {
        CabinetType eff = ScreenLogic.effectiveType(cab, defaultType, model.getWorkspace());
        return (int) Math.round(ScreenLogic.effectiveCellH(eff, defaultType, cellH));
    }

    private static double distanceToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
        double cx = ax + t * dx, cy = ay + t * dy;
        return Math.hypot(px - cx, py - cy);
    }

    private int cabW() {
        CabinetType t = model.typeOf(model.getCurrentScreen());
        double ratio = t != null && t.getHeightMm() > 0 ? t.getWidthMm() / t.getHeightMm() : 1;
        int w = ratio >= 1 ? BASE : (int) Math.round(BASE * ratio);
        return (int) Math.round(Math.max(w, 24) * zoom);
    }

    private int cabH() {
        CabinetType t = model.typeOf(model.getCurrentScreen());
        double ratio = t != null && t.getHeightMm() > 0 ? t.getWidthMm() / t.getHeightMm() : 1;
        int h = ratio >= 1 ? (int) Math.round(BASE / ratio) : BASE;
        return (int) Math.round(Math.max(h, 24) * zoom);
    }

    private CabinetInstance cabinetAt(Point p) {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return null;
        }
        int cw = cabW();
        int ch = cabH();
        if (p.x < PADDING || p.y < PADDING) {
            return null;
        }
        // Линейный перебор с учётом свободного мм-смещения ячейки (Task #7/v1.6) —
        // при офсете ячейка может физически оказаться не там, где её кладёт голая
        // формула row*ch/col*cw, поэтому точный кабинет под курсором ищем перебором
        // актуальных прямоугольников (экраны обычно от единиц до сотен кабинетов).
        CabinetType type = model.typeOf(scr);
        for (CabinetInstance cab : scr.getCabinets()) {
            int x = cabX(cab, type, cw, PADDING);
            int y = cabY(cab, type, ch, PADDING);
            if (p.x >= x && p.x < x + cw && p.y >= y && p.y < y + ch) {
                return cab;
            }
        }
        return null;
    }

    @Override
    public Dimension getPreferredSize() {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return new Dimension(600, 400);
        }
        return new Dimension(scr.getCols() * cabW() + PADDING * 2, scr.getRows() * cabH() + PADDING * 2);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            g2.setColor(Palette.MUTED);
            g2.setFont(getFont().deriveFont(14f));
            String msg = "Выберите или создайте экран, чтобы увидеть схему.";
            int tw = g2.getFontMetrics().stringWidth(msg);
            g2.drawString(msg, Math.max(20, (getWidth() - tw) / 2), getHeight() / 2);
            g2.dispose();
            return;
        }

        int cw = cabW();
        int ch = cabH();
        boolean power = model.getMode() == AppModel.Mode.POWER;

        // Цепочки хранятся на уровне СЦЕНЫ, а не экрана (см. Task #78, независимый
        // менеджер цепочек) — передаём paintScheme ПОЛНЫЙ список сцены (не только
        // то, что "принадлежит" этому экрану): он сам рисует ровно тот кусок каждой
        // цепочки, что резолвится на scr, поэтому цепочка, физически начинающаяся
        // на ДРУГОМ экране сцены, здесь тоже корректно продолжает рисоваться, без
        // отдельного "довеска" для чужих экранов, как было раньше (Task #64/#65).
        Scene scene = model.getCurrentScene();
        List<PowerChain> scenePowerChains = scene != null ? scene.getPowerChains() : List.of();
        List<SignalChain> sceneSignalChains = scene != null ? scene.getSignalChains() : List.of();
        SchemeRenderer.paintScheme(g2, scr, model.typeOf(scr), power, cw, ch, PADDING, PADDING, model.getWorkspace(),
                scenePowerChains, sceneSignalChains, settings.activeProfile().isPowerUnitKw());

        // Кабинеты, уже занятые сигнальной цепочкой, но без видимого локального
        // отрезка на ЭТОМ экране (например, кабинет — единственный представитель
        // экрана в цепочке, оба соседа по цепочке физически на другом экране, тогда
        // отрезка, целиком лежащего на scr, для него не находится) — тускло
        // закрашиваем, чтобы не выглядело, будто кабинет ещё можно расключить
        // отдельно. Для питания ячейка и так закрашена цветом фазы через
        // paintScheme, дополнительный дим не нужен и только испортил бы цвет фазы.
        CabinetType type = model.typeOf(scr);
        if (!power) {
            for (CabinetInstance cab : scr.getCabinets()) {
                if (cab.isHidden()) {
                    continue;
                }
                boolean ownChain = sceneSignalChains.stream()
                        .anyMatch(sc -> sc.getCabinetInstanceIds().contains(cab.getId()));
                if (!ownChain && model.isCabinetWiredForSignal(cab.getId())) {
                    int x = cabX(cab, type, cw, PADDING);
                    int y = cabY(cab, type, ch, PADDING);
                    int ew = effW(cab, type, cw);
                    int eh = effH(cab, type, ch);
                    g2.setColor(new Color(0, 0, 0, 140));
                    g2.fillRect(x + 1, y + 1, ew - 2, eh - 2);
                }
            }
        }

        // строящаяся цепочка + подсветка выбранных
        if (controller.isChainBuilding()) {
            List<String> active = controller.activeChainCabIds();
            Color c = power ? Palette.phaseColor(model.getActivePhase()) : Color.WHITE;
            for (String id : active) {
                CabinetInstance cab = scr.cabinetById(id);
                if (cab != null) {
                    int x = cabX(cab, type, cw, PADDING);
                    int y = cabY(cab, type, ch, PADDING);
                    int ew = effW(cab, type, cw);
                    int eh = effH(cab, type, ch);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRect(x + 1, y + 1, ew - 2, eh - 2);
                }
            }
            SchemeRenderer.drawChain(g2, scr, active, c, true, cw, ch, PADDING, PADDING,
                    model.typeOf(scr), model.getWorkspace());

            int cr = controller.cursorRow();
            int cc = controller.cursorCol();
            if (cr >= 0 && cc >= 0) {
                CabinetInstance cursorCab = scr.cabinetAt(cr, cc);
                int x = cursorCab != null ? cabX(cursorCab, type, cw, PADDING) : PADDING + cc * cw;
                int y = cursorCab != null ? cabY(cursorCab, type, ch, PADDING) : PADDING + cr * ch;
                int cursorW = cursorCab != null ? effW(cursorCab, type, cw) : cw;
                int cursorH = cursorCab != null ? effH(cursorCab, type, ch) : ch;
                g2.setColor(Color.YELLOW);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4, 3}, 0));
                g2.drawRect(x + 3, y + 3, cursorW - 6, cursorH - 6);
            }
        }

        // Прямоугольник выделения области в режиме «Быстрое подключение» — рисуется
        // ПОВЕРХ схемы, независимо от isChainBuilding (эти два режима не пересекаются).
        if (marqueeStart != null && marqueeEnd != null) {
            g2.setColor(Palette.ACCENT);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0,
                    new float[]{6, 4}, 0));
            int x = Math.min(marqueeStart.x, marqueeEnd.x);
            int y = Math.min(marqueeStart.y, marqueeEnd.y);
            int w = Math.abs(marqueeEnd.x - marqueeStart.x);
            int h = Math.abs(marqueeEnd.y - marqueeStart.y);
            g2.drawRect(x, y, w, h);
        }

        g2.dispose();
    }
}
