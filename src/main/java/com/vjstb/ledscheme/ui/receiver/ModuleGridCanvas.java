package com.vjstb.ledscheme.ui.receiver;

import com.vjstb.ledscheme.model.GridRect;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;

/**
 * Общий каркас холстов видов «Приёмные карты» и «Блоки питания»: сетка клеток экрана, протяжка мышью по клеткам и
 * клик по клетке. Что значит протяжка (карта, блок, выбор) — решает вид-владелец через {@link Listener}; холст не
 * знает ни про модель, ни про инструменты, поэтому поведение мыши одинаково в обоих видах и проверяется одним
 * тестом реальной доставки событий.
 *
 * <p>Пока кнопка зажата, выделение рисуется подклассом ({@link #dragRect()}). Протяжка в пределах одной клетки —
 * это клик (как в прототипе: «moved» ставится только при смене клетки), иначе дрожание руки при клике по модулю
 * ставило бы карту на одну клетку вместо выбора карты.
 */
abstract class ModuleGridCanvas extends JComponent implements Scrollable {

    static final double MARGIN = 14;
    static final double MAX_CELL_WIDTH = 84;
    static final double MIN_CELL_WIDTH = 26;

    /** Что вид делает с жестами на холсте. */
    interface Listener {
        /** Клик (или протяжка в пределах одной клетки) по клетке. */
        void cellClicked(int row, int col);

        /** Отпущенная протяжка по нескольким клеткам — прямоугольник включительно. */
        void areaDragged(GridRect rect);
    }

    private int rows = 1;
    private int cols = 1;
    private double aspect = 1;
    private Listener listener;
    private int[] pressCell;
    private int[] dragCell;

    ModuleGridCanvas() {
        setOpaque(true);
        setFocusable(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                requestFocusInWindow();
                if (!hasContent()) {
                    return;
                }
                int[] cell = geometry().cellAt(e.getX(), e.getY(), false);
                pressCell = cell;
                dragCell = cell;
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressCell == null) {
                    return;
                }
                dragCell = geometry().cellAt(e.getX(), e.getY(), true);
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (pressCell == null || e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                int[] end = geometry().cellAt(e.getX(), e.getY(), true);
                int[] start = pressCell;
                pressCell = null;
                dragCell = null;
                repaint();
                if (listener == null) {
                    return;
                }
                if (start[0] == end[0] && start[1] == end[1]) {
                    listener.cellClicked(start[0], start[1]);
                } else {
                    listener.areaDragged(new GridRect(start[0], start[1], end[0], end[1]));
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Размер сетки и пропорция клетки; смена размера пересчитывает прокрутку. */
    void setGrid(int rows, int cols, double aspect) {
        int r = Math.max(1, rows);
        int c = Math.max(1, cols);
        double a = aspect > 0 ? aspect : 1;
        boolean sizeChanged = r != this.rows || c != this.cols || a != this.aspect;
        this.rows = r;
        this.cols = c;
        this.aspect = a;
        if (sizeChanged) {
            revalidate();
        }
        repaint();
    }

    /** Геометрия сетки при текущем размере компонента. */
    ModuleGridGeometry geometry() {
        return ModuleGridGeometry.fit(rows, cols, aspect, getWidth(), getHeight(), MARGIN, MAX_CELL_WIDTH);
    }

    /** Протягиваемый сейчас прямоугольник или {@code null}. */
    GridRect dragRect() {
        if (pressCell == null || dragCell == null) {
            return null;
        }
        return new GridRect(pressCell[0], pressCell[1], dragCell[0], dragCell[1]);
    }

    /** Есть ли что рисовать (модульный экран); иначе холст показывает {@link #placeholder()}. */
    abstract boolean hasContent();

    abstract String placeholder();

    abstract void paintGrid(Graphics2D g, ModuleGridGeometry geo, ModuleViewStyle style);

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = ModuleDraw.prepare((Graphics2D) g0);
        try {
            ModuleViewStyle st = ModuleViewStyle.live();
            g.setColor(st.background);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (!hasContent()) {
                String[] lines = placeholder().split("\n");
                g.setColor(st.mutedText);
                java.awt.FontMetrics fm = g.getFontMetrics();
                int y = getHeight() / 2 - (lines.length * fm.getHeight()) / 2 + fm.getAscent();
                for (String line : lines) {
                    g.drawString(line, Math.max(8, (getWidth() - fm.stringWidth(line)) / 2), y);
                    y += fm.getHeight();
                }
                return;
            }
            paintGrid(g, geometry(), st);
        } finally {
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ Scrollable

    @Override
    public Dimension getPreferredSize() {
        return new Dimension((int) Math.ceil(cols * MIN_CELL_WIDTH + 2 * MARGIN),
                (int) Math.ceil(rows * MIN_CELL_WIDTH / aspect + 2 * MARGIN));
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(480, 320);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 24;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 120;
    }

    /** Пока сетка помещается — холст растягивается на окно (клетки крупнее); иначе появляется прокрутка. */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof JViewport vp && vp.getWidth() >= getPreferredSize().width;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport vp && vp.getHeight() >= getPreferredSize().height;
    }
}
