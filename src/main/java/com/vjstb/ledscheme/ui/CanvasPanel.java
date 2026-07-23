package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
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
import javax.swing.JPanel;
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

        // кабинеты и сохранённые цепочки (общая отрисовка с экспортом)
        SchemeRenderer.paintScheme(g2, scr, model.typeOf(scr), power, cw, ch, PADDING, PADDING, model.getWorkspace());

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
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 0, new float[]{4, 3}, 0));
                g2.drawRect(x + 3, y + 3, cw - 6, ch - 6);
            }
        }

        g2.dispose();
    }
}
