package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Компактный редактор сетки экрана: режим «Форма» — клик исключает/включает ячейку
 * (так задаётся непрямоугольная форма — треугольная, ступенчатая и т.д.), режим
 * «Тип» — клик (или протяжка ЛКМ) красит ячейку выбранным типом кабинета из
 * библиотеки, отличным от типа экрана по умолчанию.
 */
public class ShapeEditorPanel extends JPanel {

    public enum Mode { SHAPE, TYPE }

    private static final int CELL = 22;

    private final AppModel model;
    private Mode mode = Mode.SHAPE;
    private String paintTypeId; // null в режиме TYPE = «вернуть тип экрана по умолчанию»
    private String lastDragCabId;

    public ShapeEditorPanel(AppModel model) {
        this.model = model;
        setBackground(Palette.BG);
        setToolTipText("Форма: клик — включить/исключить ячейку. Тип: клик — назначить выбранный тип.");

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastDragCabId = null;
                handle(e.getPoint());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (mode == Mode.TYPE && SwingUtilities.isLeftMouseButton(e)) {
                    handle(e.getPoint());
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public void setPaintType(String cabinetTypeId) {
        this.paintTypeId = cabinetTypeId;
    }

    private void handle(Point p) {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        int col = p.x / CELL;
        int row = p.y / CELL;
        CabinetInstance cab = scr.cabinetAt(row, col);
        if (cab == null) {
            return;
        }
        if (mode == Mode.SHAPE) {
            model.toggleCabinetHidden(cab.getId());
        } else {
            if (cab.getId().equals(lastDragCabId)) {
                return;
            }
            lastDragCabId = cab.getId();
            try {
                model.setCabinetTypeOverride(cab.getId(), paintTypeId);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return new Dimension(120, 60);
        }
        return new Dimension(scr.getCols() * CELL + 4, scr.getRows() * CELL + 4);
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension d = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, d.height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (CabinetInstance c : scr.getCabinets()) {
            int x = c.getColIndex() * CELL;
            int y = c.getRowIndex() * CELL;
            Color fill;
            if (c.isHidden()) {
                fill = Palette.BG;
            } else if (c.getCabinetTypeId() != null) {
                fill = Palette.ACCENT;
            } else {
                fill = Palette.PHASE_NONE;
            }
            g2.setColor(fill);
            g2.fillRect(x + 1, y + 1, CELL - 2, CELL - 2);
            g2.setColor(c.isHidden() ? Palette.BORDER : Palette.MUTED);
            g2.drawRect(x + 1, y + 1, CELL - 2, CELL - 2);
        }
        g2.dispose();
    }

    /** Подпись типа для ячейки под курсором — используется во внешней подсказке. */
    public static String describeCell(AppModel model, CabinetInstance c) {
        if (c.isHidden()) {
            return "ячейка исключена (нет кабинета)";
        }
        if (c.getCabinetTypeId() != null) {
            CabinetType t = model.getWorkspace().cabinetTypeById(c.getCabinetTypeId());
            return t != null ? "тип: " + t.getName() : "тип экрана";
        }
        return "тип экрана";
    }
}
