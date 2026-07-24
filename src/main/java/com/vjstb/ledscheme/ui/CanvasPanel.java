package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.service.AppModel;
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

    private static final int PADDING = 30;
    private static final int BASE = 84;

    private final AppModel model;
    private final Controller controller;
    private double zoom = 1.0;
    private String lastDragCabId;

    public CanvasPanel(AppModel model, Controller controller) {
        this.model = model;
        this.controller = controller;
        setBackground(Palette.BG);
        setFocusable(true);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
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
                lastDragCabId = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
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
        for (int i = 0; i < ids.size() - 1; i++) {
            CabinetInstance a = scr.cabinetById(ids.get(i));
            CabinetInstance b = scr.cabinetById(ids.get(i + 1));
            if (a == null || b == null) {
                continue;
            }
            double ax = PADDING + a.getColIndex() * cw + cw / 2.0;
            double ay = PADDING + a.getRowIndex() * ch + ch / 2.0;
            double bx = PADDING + b.getColIndex() * cw + cw / 2.0;
            double by = PADDING + b.getRowIndex() * ch + ch / 2.0;
            if (distanceToSegment(p.x, p.y, ax, ay, bx, by) < 8) {
                return i;
            }
        }
        return -1;
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
        int col = (p.x - PADDING) / cw;
        int row = (p.y - PADDING) / ch;
        if (p.x < PADDING || p.y < PADDING || col < 0 || row < 0 || col >= scr.getCols() || row >= scr.getRows()) {
            return null;
        }
        return scr.cabinetAt(row, col);
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
                scenePowerChains, sceneSignalChains);

        // Кабинеты, уже занятые сигнальной цепочкой, но без видимого локального
        // отрезка на ЭТОМ экране (например, кабинет — единственный представитель
        // экрана в цепочке, оба соседа по цепочке физически на другом экране, тогда
        // отрезка, целиком лежащего на scr, для него не находится) — тускло
        // закрашиваем, чтобы не выглядело, будто кабинет ещё можно расключить
        // отдельно. Для питания ячейка и так закрашена цветом фазы через
        // paintScheme, дополнительный дим не нужен и только испортил бы цвет фазы.
        if (!power) {
            for (CabinetInstance cab : scr.getCabinets()) {
                if (cab.isHidden()) {
                    continue;
                }
                boolean ownChain = sceneSignalChains.stream()
                        .anyMatch(sc -> sc.getCabinetInstanceIds().contains(cab.getId()));
                if (!ownChain && model.isCabinetWiredForSignal(cab.getId())) {
                    int x = PADDING + cab.getColIndex() * cw;
                    int y = PADDING + cab.getRowIndex() * ch;
                    g2.setColor(new Color(0, 0, 0, 140));
                    g2.fillRect(x + 1, y + 1, cw - 2, ch - 2);
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
                    int x = PADDING + cab.getColIndex() * cw;
                    int y = PADDING + cab.getRowIndex() * ch;
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRect(x + 1, y + 1, cw - 2, ch - 2);
                }
            }
            SchemeRenderer.drawChain(g2, scr, active, c, true, cw, ch, PADDING, PADDING);

            int cr = controller.cursorRow();
            int cc = controller.cursorCol();
            if (cr >= 0 && cc >= 0) {
                int x = PADDING + cc * cw;
                int y = PADDING + cr * ch;
                g2.setColor(Color.YELLOW);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4, 3}, 0));
                g2.drawRect(x + 3, y + 3, cw - 6, ch - 6);
            }
        }

        g2.dispose();
    }
}
