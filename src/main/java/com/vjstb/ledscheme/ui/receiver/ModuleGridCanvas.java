package com.vjstb.ledscheme.ui.receiver;

import com.vjstb.ledscheme.model.GridRect;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;

/**
 * Общий каркас холстов видов «Приёмные карты» и «Блоки питания»: сетка клеток экрана, протяжка мышью по клеткам и
 * клик по клетке. Что значит протяжка (карта, блок, выбор) — решает вид-владелец через {@link Listener}; холст не
 * знает ни про модель, ни про инструменты, поэтому поведение мыши одинаково в обоих видах и проверяется одним
 * тестом реальной доставки событий.
 *
 * <p>Перемещение и масштаб — как на остальных холстах приложения (баг-репорт: здесь не работали ни средняя кнопка,
 * ни колесо): зажатая средняя кнопка двигает сетку в прокрутке, Ctrl+колесо — масштаб вокруг курсора, обычное колесо
 * и Shift+колесо — прокрутка. Ctrl+0 возвращает масштаб 1.
 *
 * <p>Пока кнопка зажата, выделение рисуется подклассом ({@link #dragRect()}). Протяжка в пределах одной клетки —
 * это клик (как в прототипе: «moved» ставится только при смене клетки), иначе дрожание руки при клике по модулю
 * ставило бы карту на одну клетку вместо выбора карты.
 */
abstract class ModuleGridCanvas extends JComponent implements Scrollable {

    static final double MARGIN = 14;
    static final double MAX_CELL_WIDTH = 84;
    static final double MIN_CELL_WIDTH = 26;
    static final double MIN_ZOOM = 0.25;
    static final double MAX_ZOOM = 8;

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
    private double zoom = 1.0;
    /** Начало перемещения средней кнопкой: точка в координатах вьюпорта (не экрана — она не зависит от того, что
     *  сам холст едет под рукой) и положение вида на момент нажатия. */
    private Point panStart;
    private Point panStartView;

    ModuleGridCanvas() {
        setOpaque(true);
        setFocusable(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    requestFocusInWindow();
                    startPan(e);
                    return;
                }
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
                if (panStart != null) {
                    pan(e);
                    return;
                }
                if (pressCell == null) {
                    return;
                }
                dragCell = geometry().cellAt(e.getX(), e.getY(), true);
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (panStart != null && e.getButton() == MouseEvent.BUTTON2) {
                    panStart = null;
                    panStartView = null;
                    setCursor(Cursor.getDefaultCursor());
                    return;
                }
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
        addMouseWheelListener(this::wheel);
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_0,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "module-grid-zoom-reset");
        getActionMap().put("module-grid-zoom-reset", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setZoom(1.0, null);
            }
        });
    }

    private JViewport viewport() {
        return getParent() instanceof JViewport vp ? vp : null;
    }

    private void startPan(MouseEvent e) {
        JViewport vp = viewport();
        if (vp == null) {
            return;
        }
        panStart = SwingUtilities.convertPoint(this, e.getPoint(), vp);
        panStartView = vp.getViewPosition();
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    }

    private void pan(MouseEvent e) {
        JViewport vp = viewport();
        if (vp == null || panStartView == null) {
            return;
        }
        Point now = SwingUtilities.convertPoint(this, e.getPoint(), vp);
        vp.setViewPosition(clampView(vp, panStartView.x + (panStart.x - now.x), panStartView.y + (panStart.y - now.y)));
    }

    private static Point clampView(JViewport vp, int x, int y) {
        Dimension ext = vp.getExtentSize();
        Dimension view = vp.getViewSize();
        return new Point(Math.max(0, Math.min(Math.max(0, view.width - ext.width), x)),
                Math.max(0, Math.min(Math.max(0, view.height - ext.height), y)));
    }

    /** Ctrl+колесо — масштаб вокруг курсора; без Ctrl — обычная прокрутка (Shift — по горизонтали). */
    private void wheel(MouseWheelEvent e) {
        if (e.isControlDown()) {
            setZoom(zoom * Math.pow(1.15, -e.getPreciseWheelRotation()), e.getPoint());
            return;
        }
        JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (sp != null) {
            JScrollBar bar = e.isShiftDown() ? sp.getHorizontalScrollBar() : sp.getVerticalScrollBar();
            bar.setValue(bar.getValue() + e.getUnitsToScroll() * bar.getUnitIncrement());
        }
    }

    double zoom() {
        return zoom;
    }

    /** Масштаб; точка {@code anchor} (в координатах холста) остаётся под курсором, {@code null} — центр вида. */
    void setZoom(double value, Point anchor) {
        double z = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
        if (z == zoom) {
            return;
        }
        JViewport vp = viewport();
        Point pin = null;
        double fx = 0.5;
        double fy = 0.5;
        if (vp != null && getWidth() > 0 && getHeight() > 0) {
            Point a = anchor != null ? anchor : new Point(vp.getViewPosition().x + vp.getWidth() / 2,
                    vp.getViewPosition().y + vp.getHeight() / 2);
            fx = a.x / (double) getWidth();
            fy = a.y / (double) getHeight();
            pin = new Point(a.x - vp.getViewPosition().x, a.y - vp.getViewPosition().y);
        }
        zoom = z;
        revalidate();
        repaint();
        if (vp != null && pin != null) {
            double ffx = fx;
            double ffy = fy;
            Point fpin = pin;
            // размер холста обновится только после раскладки — положение вида ставим уже после неё
            SwingUtilities.invokeLater(() -> {
                vp.validate();
                Dimension view = vp.getViewSize();
                vp.setViewPosition(clampView(vp, (int) Math.round(ffx * view.width) - fpin.x,
                        (int) Math.round(ffy * view.height) - fpin.y));
            });
        }
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
        return ModuleGridGeometry.fit(rows, cols, aspect, getWidth(), getHeight(), MARGIN, MAX_CELL_WIDTH * zoom);
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
        return new Dimension((int) Math.ceil(cols * MIN_CELL_WIDTH * zoom + 2 * MARGIN),
                (int) Math.ceil(rows * MIN_CELL_WIDTH * zoom / aspect + 2 * MARGIN));
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
