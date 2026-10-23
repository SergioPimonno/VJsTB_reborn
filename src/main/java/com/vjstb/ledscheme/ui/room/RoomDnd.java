package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.RoomLibRef;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * Перетаскивание между панелями Планнера (карточка библиотеки → план или стойка, блок стойки → корзина
 * в библиотеке). Стандартный Swing DnD тут не нужен: все панели в одном окне, поэтому достаточно
 * «призрака» на стеклянной панели окна и поиска панели-приёмника под указателем.
 *
 * <p>Источник пересылает события своей мыши в {@link Controller}; тот после порога в 5 пикселей начинает
 * жест, показывает призрак с подписью и сообщает приёмнику ({@link Target}), что над ним проносят. Жест, не
 * пустивший приёмника, просто отменяется.
 */
final class RoomDnd {

    private RoomDnd() {
    }

    /** Что переносят. */
    enum Source {
        /** Карточка библиотеки. */
        LIBRARY,
        /** Блок в слоте кофра. */
        RACK_ITEM,
        /** Предмет в стопке без кофра. */
        STACK_ITEM,
        /** Целый слой башни (кофр или стопка). */
        SEGMENT
    }

    /** Описание переносимого: источник, позиция библиотеки и, для переносимых из стоек, где оно лежало. */
    record Info(Source source, RoomLibRef ref, String itemId, String towerId, String segId, String label) {

        static Info library(RoomLibRef ref, String label) {
            return new Info(Source.LIBRARY, ref, null, null, null, label);
        }
    }

    /** Реакция приёмника на то, что над ним проносят. */
    enum Feedback { NONE, OK, DELETE }

    /** Панель, принимающая перенос. Точки — в координатах самой панели. */
    interface Target {
        Feedback hover(Info info, Point p);

        void drop(Info info, Point p);

        void dragLeft();
    }

    /** Ведёт жест: призрак, поиск приёмника, доставка. Один на окно. */
    static final class Controller {

        private final Component anchor;
        private JRootPane root;
        private final Ghost ghost = new Ghost();
        private Info info;
        private Point startRoot;
        private boolean active;
        private Target current;
        private Component currentComponent;
        private Feedback feedback = Feedback.NONE;

        /** {@code anchor} — любой компонент окна Планнера; корневая панель берётся при первом жесте. */
        Controller(Component anchor) {
            this.anchor = anchor;
        }

        private JRootPane root() {
            if (root == null) {
                root = SwingUtilities.getRootPane(anchor);
                if (root != null) {
                    root.setGlassPane(ghost);
                }
            }
            return root;
        }

        /** Нажатие на источнике: запомнить, что собираемся переносить. */
        void press(Component source, MouseEvent e, Info what) {
            info = what;
            JRootPane r = root();
            startRoot = r == null ? e.getPoint() : SwingUtilities.convertPoint(source, e.getPoint(), r);
            active = false;
        }

        void drag(Component source, MouseEvent e) {
            JRootPane r = root();
            if (info == null || r == null) {
                return;
            }
            Point p = SwingUtilities.convertPoint(source, e.getPoint(), r);
            if (!active) {
                if (p.distance(startRoot) < 5) {
                    return;
                }
                active = true;
                ghost.setVisible(true);
            }
            ghost.move(p, info.label(), feedback);
            Target t = null;
            Component tc = null;
            Point inContent = SwingUtilities.convertPoint(r, p, r.getContentPane());
            Component deepest = SwingUtilities.getDeepestComponentAt(r.getContentPane(), inContent.x, inContent.y);
            for (Component c = deepest; c != null; c = c.getParent()) {
                if (c instanceof Target target) {
                    t = target;
                    tc = c;
                    break;
                }
            }
            if (t != current) {
                if (current != null) {
                    current.dragLeft();
                }
                current = t;
                currentComponent = tc;
            }
            feedback = current == null ? Feedback.NONE
                    : current.hover(info, SwingUtilities.convertPoint(r, p, currentComponent));
            ghost.setFeedback(feedback);
        }

        void release(Component source, MouseEvent e) {
            Info done = info;
            Target t = current;
            Component tc = currentComponent;
            boolean was = active;
            Feedback fb = feedback;
            reset();
            if (done == null || !was || t == null || fb == Feedback.NONE) {
                if (t != null) {
                    t.dragLeft();
                }
                return;
            }
            Point p = SwingUtilities.convertPoint(source, e.getPoint(), tc);
            t.dragLeft();
            t.drop(done, p);
        }

        boolean isActive() {
            return active;
        }

        void cancel() {
            Target t = current;
            reset();
            if (t != null) {
                t.dragLeft();
            }
        }

        private void reset() {
            info = null;
            active = false;
            current = null;
            currentComponent = null;
            feedback = Feedback.NONE;
            ghost.setVisible(false);
        }
    }

    /** Прозрачная для мыши стеклянная панель с подписью у указателя. Перерисовывает только свой прямоугольник. */
    private static final class Ghost extends JComponent {
        private Point at;
        private String label = "";
        private Feedback feedback = Feedback.NONE;
        private java.awt.Rectangle box;

        Ghost() {
            setOpaque(false);
            setVisible(false);
        }

        @Override
        public boolean contains(int x, int y) {
            return false;
        }

        private Font labelFont() {
            Font f = getFont() != null ? getFont() : new Font(Font.DIALOG, Font.PLAIN, 12);
            return RoomFonts.derive(f, Font.BOLD, RoomTheme.fontSize(12f));
        }

        void move(Point p, String text, Feedback fb) {
            java.awt.Rectangle old = box;
            at = p;
            label = text == null ? "" : text;
            feedback = fb;
            if (label.isEmpty()) {
                box = null;
            } else {
                FontMetrics fm = getFontMetrics(labelFont());
                box = new java.awt.Rectangle(p.x + 12, p.y + 8, fm.stringWidth(label) + 18, fm.getHeight() + 8);
            }
            java.awt.Rectangle dirty = old == null ? box : box == null ? old : old.union(box);
            if (dirty != null) {
                repaint(dirty.x - 2, dirty.y - 2, dirty.width + 4, dirty.height + 4);
            }
        }

        void setFeedback(Feedback fb) {
            if (fb != feedback) {
                feedback = fb;
                if (box != null) {
                    repaint(box.x - 2, box.y - 2, box.width + 4, box.height + 4);
                }
            }
        }

        @Override
        public void setVisible(boolean v) {
            if (!v) {
                box = null;
            }
            super.setVisible(v);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            if (box == null) {
                return;
            }
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(labelFont());
                FontMetrics fm = g.getFontMetrics();
                RoomTheme th = RoomTheme.current();
                Color bg = feedback == Feedback.DELETE ? th.err : feedback == Feedback.NONE ? th.muted : th.accent;
                g.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 230));
                g.fillRoundRect(box.x, box.y, box.width, box.height, 10, 10);
                g.setColor(RoomTheme.readableOn(bg));
                g.drawString(label, box.x + 9, box.y + 4 + fm.getAscent());
            } finally {
                g.dispose();
            }
        }
    }
}
