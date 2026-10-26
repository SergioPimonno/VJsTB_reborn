package com.vjstb.ledscheme.ui.receiver;

import com.vjstb.ledscheme.model.ModuleNumberingPattern;
import com.vjstb.ledscheme.service.ModuleNumbering;
import com.vjstb.ledscheme.ui.Palette;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Палитра нумерации модулей под холстом «Приёмных карт» (D7): одна строка из восьми плиток — четыре угла «по
 * строкам» и четыре «по столбцам», на каждой цифры 1…9 в порядке нумерации; рядом флажок «змейка» и фраза словами
 * («Первый модуль — слева сверху, дальше вправо по строке»). Шаблон один на весь экран: смешивать порядки на одном
 * экране нежелательно — техник читает карту подключения по единому правилу.
 *
 * <p>Плитки рисуются по {@link ModuleNumbering#sequence}, а не своей копией обхода, поэтому картинка на плитке всегда
 * совпадает с тем, как номера ляжут на модули.
 */
final class NumberingPalette extends JPanel {

    private final Consumer<ModuleNumberingPattern> onChange;
    private final List<Tile> tiles = new ArrayList<>();
    private final JCheckBox snake = new JCheckBox("змейка");
    private final JLabel text = new JLabel(" ");
    private ModuleNumberingPattern current = new ModuleNumberingPattern();
    private boolean updating;

    NumberingPalette(Consumer<ModuleNumberingPattern> onChange) {
        super(new BorderLayout(0, 4));
        this.onChange = onChange;
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Palette.BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JLabel title = new JLabel("Нумерация модулей в карте — весь экран");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        head.add(title, BorderLayout.WEST);
        snake.setOpaque(false);
        snake.setToolTipText("Каждая следующая строка (столбец) идёт в обратную сторону — кабель не возвращается к началу.");
        snake.addActionListener(e -> {
            if (!updating) {
                fire(new ModuleNumberingPattern(current.getCorner(), current.getAxis(), snake.isSelected()));
            }
        });
        head.add(snake, BorderLayout.EAST);

        JPanel groups = new JPanel(new GridLayout(1, 2, 8, 0));
        groups.setOpaque(false);
        groups.add(muted("По строкам"));
        groups.add(muted("По столбцам"));

        JPanel row = new JPanel(new GridLayout(1, 8, 4, 0));
        row.setOpaque(false);
        for (ModuleNumberingPattern p : ModuleNumberingPattern.palette()) {
            Tile t = new Tile(p);
            tiles.add(t);
            row.add(t);
        }

        JPanel body = new JPanel(new BorderLayout(0, 2));
        body.setOpaque(false);
        body.add(groups, BorderLayout.NORTH);
        body.add(row, BorderLayout.CENTER);

        add(head, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        add(text, BorderLayout.SOUTH);
        setPattern(current);
    }

    private static JLabel muted(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(Palette.MUTED);
        l.setFont(l.getFont().deriveFont(l.getFont().getSize2D() - 1f));
        return l;
    }

    private void fire(ModuleNumberingPattern p) {
        onChange.accept(p);
    }

    /** Показать шаблон экрана (вызывается при каждом обновлении вида). */
    void setPattern(ModuleNumberingPattern p) {
        current = p.copy();
        updating = true;
        snake.setSelected(p.isSnake());
        updating = false;
        for (Tile t : tiles) {
            t.repaint();
        }
        text.setText(describe(p));
    }

    ModuleNumberingPattern pattern() {
        return current;
    }

    /** Плитки палитры — для тестов: нажатие = выбор шаблона. */
    List<? extends JComponent> tiles() {
        return tiles;
    }

    void clickTile(int index) {
        tiles.get(index).choose();
    }

    JCheckBox snakeCheck() {
        return snake;
    }

    String description() {
        return text.getText();
    }

    static String describe(ModuleNumberingPattern p) {
        boolean rows = p.getAxis() == ModuleNumberingPattern.Axis.ROWS;
        String dir = rows
                ? (p.left() ? "вправо" : "влево") + " по строке"
                : (p.top() ? "вниз" : "вверх") + " по столбцу";
        return "Первый модуль — " + corner(p) + ", дальше " + dir + (p.isSnake() ? ", змейкой" : "");
    }

    private static String corner(ModuleNumberingPattern p) {
        return (p.left() ? "слева" : "справа") + " " + (p.top() ? "сверху" : "снизу");
    }

    /** Одна плитка: мини-сетка 3×3 с цифрами в порядке нумерации; первая клетка — синим кружком. */
    private final class Tile extends JComponent {
        private final ModuleNumberingPattern base;

        Tile(ModuleNumberingPattern base) {
            this.base = base;
            setPreferredSize(new Dimension(54, 54));
            setToolTipText((base.getAxis() == ModuleNumberingPattern.Axis.ROWS ? "По строкам" : "По столбцам")
                    + ", первый — " + corner(base));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    choose();
                }
            });
        }

        void choose() {
            fire(new ModuleNumberingPattern(base.getCorner(), base.getAxis(), current.isSnake()));
        }

        boolean selected() {
            return current.getCorner() == base.getCorner() && current.getAxis() == base.getAxis();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = ModuleDraw.prepare((Graphics2D) g0);
            try {
                double side = Math.min(getWidth(), getHeight()) - 6;
                double x0 = (getWidth() - side) / 2;
                double y0 = (getHeight() - side) / 2;
                double cell = side / 3;
                ModuleViewStyle st = ModuleViewStyle.live();
                if (selected()) {
                    g.setColor(ModuleViewStyle.alpha(st.selectionStroke, 0.25));
                    g.fill(new RoundRectangle2D.Double(1, 1, getWidth() - 2, getHeight() - 2, 8, 8));
                    g.setColor(st.selectionStroke);
                    g.setStroke(new BasicStroke(2f));
                    g.draw(new RoundRectangle2D.Double(1, 1, getWidth() - 2, getHeight() - 2, 8, 8));
                } else {
                    g.setColor(Palette.BORDER);
                    g.setStroke(new BasicStroke(1f));
                    g.draw(new RoundRectangle2D.Double(1, 1, getWidth() - 2, getHeight() - 2, 8, 8));
                }
                List<int[]> seq = ModuleNumbering.sequence(3, 3,
                        new ModuleNumberingPattern(base.getCorner(), base.getAxis(), current.isSnake()));
                for (int i = 0; i < seq.size(); i++) {
                    int r = seq.get(i)[0];
                    int c = seq.get(i)[1];
                    Rectangle2D rc = new Rectangle2D.Double(x0 + c * cell + 1, y0 + r * cell + 1, cell - 2, cell - 2);
                    g.setColor(st.moduleFill);
                    g.fill(new RoundRectangle2D.Double(rc.getX(), rc.getY(), rc.getWidth(), rc.getHeight(), 3, 3));
                    boolean first = i == 0;
                    if (first) {
                        g.setColor(new java.awt.Color(0x185fa5));
                        double d = Math.min(rc.getWidth(), rc.getHeight()) - 1;
                        g.fill(new Ellipse2D.Double(rc.getCenterX() - d / 2, rc.getCenterY() - d / 2, d, d));
                    }
                    ModuleDraw.centeredText(g, Integer.toString(i + 1), rc.getCenterX(), rc.getCenterY(),
                            Math.max(7, cell * 0.55), cell, first ? java.awt.Color.WHITE : st.text);
                }
            } finally {
                g.dispose();
            }
        }
    }
}
