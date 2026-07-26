package com.vjstb.ledscheme.ui;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/**
 * Радиальное (круговое) меню в духе Krita: появляется в точке нажатия ПКМ, пункты
 * разложены по кругу. Если открыть зажатой кнопкой и, не отпуская, повести мышь к
 * пункту — при отпускании выбирается пункт под курсором (жест press-drag-release);
 * обычный клик по пункту после открытия работает так же. Отпускание в «мёртвой
 * зоне» у центра — отмена, без выбора. Подписи — в непрозрачных «чипах» с всегда
 * контрастным (белым) текстом, радиус кольца растёт с числом пунктов, чтобы
 * соседние подписи не наезжали друг на друга.
 *
 * <p>Поддерживает 2 уровня вложенности: пункт-«ветка» ({@link Item#branch}) при
 * выборе не выполняет действие, а закрывает текущее кольцо и тут же открывает
 * следующее (той же геометрии, в той же точке) со своими дочерними пунктами;
 * пункт-«лист» ({@link Item#leaf}) выполняет своё действие сразу.
 */
public final class RadialMenu {

    public static final class Item {
        final String label;
        final Color color;
        final Runnable action;
        final List<Item> children;
        final javax.swing.Icon icon;

        private Item(String label, Color color, Runnable action, List<Item> children, javax.swing.Icon icon) {
            this.label = label;
            this.color = color;
            this.action = action;
            this.children = children;
            this.icon = icon;
        }

        /** Пункт-лист: выбор сразу выполняет action и закрывает меню. */
        public static Item leaf(String label, Color color, Runnable action) {
            return new Item(label, color, action, null, null);
        }

        /** Пункт-лист с картинкой-пиктограммой вместо однотонного кружка-«свотча» —
         *  для случаев, когда сам цвет ничего не объясняет (например, 8 шаблонов
         *  серпантина «Быстрого подключения», см. ChainPatternIcon): текстовая
         *  подпись одна не даёт быстро понять маршрут, нужна картинка как в NovaLCT. */
        public static Item leaf(String label, javax.swing.Icon icon, Runnable action) {
            return new Item(label, null, action, null, icon);
        }

        /** Пункт-ветка: выбор открывает следующее кольцо с children вместо действия. */
        public static Item branch(String label, Color color, List<Item> children) {
            return new Item(label, color, null, children, null);
        }

        boolean isBranch() {
            return children != null;
        }
    }

    private static final int DEAD_ZONE = 30;
    private static final int SWATCH_R = 14;
    private static final int MAX_LABEL_CHARS = 16;

    private RadialMenu() {
    }

    /** Показывает меню с центром в точке screenX/screenY. Выбор пункта-листа выполняет
     *  его action; выбор пункта-ветки закрывает это кольцо и сразу открывает следующее
     *  (в той же точке) с его дочерними пунктами — так получаются уровни вложенности. */
    public static void show(Component invoker, int screenX, int screenY, List<Item> items) {
        show(invoker, screenX, screenY, items, () -> { });
    }

    /** То же самое, но с колбэком, который вызывается РОВНО ОДИН РАЗ — когда меню
     *  окончательно закрывается (выбран лист, отмена кликом мимо), а НЕ при переходе
     *  между уровнями вложенности (ветка -> следующее кольцо). Позволяет вызывающему
     *  компоненту (например, ShapeEditorPanel) знать, когда меню активно, чтобы не
     *  обрабатывать свои собственные клики по холсту, пока идёт выбор в меню. */
    public static void show(Component invoker, int screenX, int screenY, List<Item> items, Runnable onClose) {
        if (items.isEmpty()) {
            onClose.run();
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(invoker);
        JWindow win = new JWindow(owner);
        win.setType(Window.Type.POPUP);
        win.setFocusableWindowState(false);
        boolean translucent = tryEnableTranslucency(win);

        RadialContent content = new RadialContent(items, translucent);
        win.setContentPane(content);
        int size = content.computeWindowSize();
        win.setSize(size, size);

        // не даём окну вылезти за экран
        GraphicsConfiguration gc = invoker.getGraphicsConfiguration();
        java.awt.Rectangle screenBounds = gc != null ? gc.getBounds()
                : new java.awt.Rectangle(java.awt.Toolkit.getDefaultToolkit().getScreenSize());
        int wx = Math.max(screenBounds.x, Math.min(screenX - win.getWidth() / 2, screenBounds.x + screenBounds.width - win.getWidth()));
        int wy = Math.max(screenBounds.y, Math.min(screenY - win.getHeight() / 2, screenBounds.y + screenBounds.height - win.getHeight()));
        win.setLocation(wx, wy);
        win.setAlwaysOnTop(true);
        win.setVisible(true);

        int centerX = wx + win.getWidth() / 2;
        int centerY = wy + win.getHeight() / 2;
        double menuRadius = win.getWidth() / 2.0;

        AWTEventListener[] listenerHolder = new AWTEventListener[1];
        Runnable cleanup = () -> {
            if (listenerHolder[0] != null) {
                Toolkit.getDefaultToolkit().removeAWTEventListener(listenerHolder[0]);
            }
            win.dispose();
        };

        listenerHolder[0] = event -> {
            if (!(event instanceof MouseEvent me)) {
                return;
            }
            int id = me.getID();
            if (id != MouseEvent.MOUSE_MOVED && id != MouseEvent.MOUSE_DRAGGED
                    && id != MouseEvent.MOUSE_RELEASED && id != MouseEvent.MOUSE_PRESSED) {
                return;
            }
            Point p = MouseInfo.getPointerInfo() != null ? MouseInfo.getPointerInfo().getLocation() : null;
            if (p == null) {
                return;
            }
            int idx = content.hoverIndexFor(p.x - centerX, p.y - centerY);
            content.setHover(idx);

            if (id == MouseEvent.MOUSE_RELEASED) {
                cleanup.run();
                if (idx >= 0) {
                    Item selected = items.get(idx);
                    if (selected.isBranch()) {
                        show(invoker, screenX, screenY, selected.children, onClose);
                        return;
                    } else if (selected.action != null) {
                        selected.action.run();
                    }
                }
                onClose.run();
            } else if (id == MouseEvent.MOUSE_PRESSED) {
                // Отмена только для клика ЗАМЕТНО мимо визуальных границ меню (по
                // реальному расстоянию от центра). Раньше здесь проверялось, попал ли
                // компонент события в окно меню (SwingUtilities.getWindowAncestor(...)
                // != win) — но JWindow меню это Window.Type.POPUP с
                // setFocusableWindowState(false), и на многих платформах такие окна не
                // получают события мыши сами (click-through): ЛЮБОЙ клик по пункту
                // меню (в т.ч. второго уровня — после перехода по ветке предыдущий
                // жест press-drag-release уже завершился отпусканием кнопки, поэтому
                // выбор листа требует НОВОГО отдельного клика) на самом деле доставлялся
                // компоненту ПОД меню, и такой клик ошибочно считался «мимо» и сразу
                // отменял меню — из-за этого выбор второго уровня (Тип/Форма) вообще
                // не мог сработать: жест PRESS отменял меню раньше, чем успевал дойти
                // парный RELEASE, которым и должен был определяться реальный выбор.
                double dist = Math.hypot(p.x - centerX, p.y - centerY);
                if (dist > menuRadius) {
                    cleanup.run();
                    onClose.run();
                }
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(listenerHolder[0], AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    private static boolean tryEnableTranslucency(JWindow win) {
        try {
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            if (gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
                win.setBackground(new Color(0, 0, 0, 0));
                return true;
            }
        } catch (Exception ignored) {
            // штатный откат на непрозрачный фон ниже
        }
        return false;
    }

    private static final class RadialContent extends JPanel {
        private final List<Item> items;
        private final boolean translucent;
        private int hover = -1;
        private int ringRadius;
        private int labelRadius;
        private int pieRadius;

        RadialContent(List<Item> items, boolean translucent) {
            this.items = items;
            this.translucent = translucent;
            setOpaque(false);
        }

        /** Считает геометрию под текущее число пунктов и подписывает размер окна с запасом
         *  под самый широкий «чип» подписи, чтобы соседние пункты не наезжали друг на друга. */
        int computeWindowSize() {
            int n = items.size();
            ringRadius = Math.max(58, 11 * n);
            boolean hasIcon = items.stream().anyMatch(it -> it.icon != null);
            if (hasIcon) {
                // картинки-пиктограммы (см. Item.leaf(label, Icon, action)) заметно
                // шире плоского кружка-«свотча» — без увеличения радиуса кольца
                // соседние пункты накладываются друг на друга при большом n.
                ringRadius = Math.max(ringRadius, 95);
            }
            labelRadius = ringRadius + 42;

            // Пункты-картинки не рисуют подпись-чип (см. paintComponent) — им запас
            // нужен только под саму пиктограмму у кольца, а не у внешнего радиуса
            // подписей, иначе окно оставалось бы неоправданно большим с пустым краем.
            FontMetrics fm = getFontMetrics(labelFont());
            int maxChipHalf = 0;
            int maxIconHalf = 0;
            for (Item it : items) {
                if (it.icon != null) {
                    maxIconHalf = Math.max(maxIconHalf, Math.max(it.icon.getIconWidth(), it.icon.getIconHeight()) / 2 + 8);
                } else {
                    int w = fm.stringWidth(shorten(displayLabel(it), MAX_LABEL_CHARS)) + 16;
                    maxChipHalf = Math.max(maxChipHalf, w / 2);
                }
            }
            pieRadius = ringRadius + Math.max(26, maxIconHalf);
            int size = 2 * (labelRadius + maxChipHalf) + 24;
            return Math.max(size, 2 * pieRadius + 80);
        }

        private Font labelFont() {
            return getFont() != null ? getFont().deriveFont(Font.PLAIN, 12f) : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }

        int hoverIndexFor(int dx, int dy) {
            double dist = Math.hypot(dx, dy);
            if (dist < DEAD_ZONE) {
                return -1;
            }
            double step = 2 * Math.PI / items.size();
            // 0 рад — «север» (вверх), по часовой стрелке
            double theta = Math.atan2(dx, -dy);
            if (theta < 0) {
                theta += 2 * Math.PI;
            }
            int idx = (int) Math.round(theta / step) % items.size();
            return idx;
        }

        void setHover(int idx) {
            if (idx != hover) {
                hover = idx;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;

            g2.setColor(translucent ? new Color(13, 17, 23, 235) : Palette.PANEL);
            g2.fillOval(cx - pieRadius, cy - pieRadius, pieRadius * 2, pieRadius * 2);
            g2.setColor(Palette.BORDER);
            g2.drawOval(cx - pieRadius, cy - pieRadius, pieRadius * 2, pieRadius * 2);

            int n = items.size();
            double step = 2 * Math.PI / n;
            g2.setFont(labelFont());
            FontMetrics fm = g2.getFontMetrics();

            for (int i = 0; i < n; i++) {
                double theta = i * step; // 0 = север, по часовой
                double sx = Math.sin(theta);
                double sy = -Math.cos(theta);
                boolean isHover = i == hover;
                Item item = items.get(i);

                int swX = cx + (int) Math.round(sx * ringRadius);
                int swY = cy + (int) Math.round(sy * ringRadius);
                if (item.icon != null) {
                    int iw = item.icon.getIconWidth();
                    int ih = item.icon.getIconHeight();
                    int bw = iw + 10, bh = ih + 10;
                    g2.setColor(translucent ? new Color(13, 17, 23, 235) : Palette.PANEL);
                    g2.fillRoundRect(swX - bw / 2, swY - bh / 2, bw, bh, 8, 8);
                    g2.setColor(isHover ? Color.WHITE : Palette.BORDER);
                    g2.setStroke(new java.awt.BasicStroke(isHover ? 2.5f : 1f));
                    g2.drawRoundRect(swX - bw / 2, swY - bh / 2, bw, bh, 8, 8);
                    item.icon.paintIcon(this, g2, swX - iw / 2, swY - ih / 2);
                } else {
                    Color swatch = item.color != null ? item.color : Palette.MUTED;
                    g2.setColor(swatch);
                    g2.fillOval(swX - SWATCH_R, swY - SWATCH_R, SWATCH_R * 2, SWATCH_R * 2);
                    g2.setColor(isHover ? Color.WHITE : Palette.BORDER);
                    g2.setStroke(new java.awt.BasicStroke(isHover ? 2.5f : 1f));
                    g2.drawOval(swX - SWATCH_R, swY - SWATCH_R, SWATCH_R * 2, SWATCH_R * 2);
                }

                // Пункты-картинки (см. Item.leaf(label, Icon, action)) сознательно БЕЗ
                // текстовой подписи-чипа рядом — самой пиктограммы достаточно, подпись
                // рядом с ней только загромождала бы плотное кольцо из 8 пунктов.
                if (item.icon == null) {
                    String label = shorten(displayLabel(item), MAX_LABEL_CHARS);
                    int labelW = fm.stringWidth(label);
                    int chipX = cx + (int) Math.round(sx * labelRadius);
                    int chipY = cy + (int) Math.round(sy * labelRadius);
                    int chipW = labelW + 16;
                    int chipH = fm.getHeight() + 6;

                    g2.setColor(isHover ? Palette.ACCENT : new Color(0x0d, 0x11, 0x17, 235));
                    g2.fillRoundRect(chipX - chipW / 2, chipY - chipH / 2, chipW, chipH, 10, 10);
                    g2.setColor(isHover ? Palette.ACCENT.darker() : Palette.BORDER);
                    g2.setStroke(new java.awt.BasicStroke(1f));
                    g2.drawRoundRect(chipX - chipW / 2, chipY - chipH / 2, chipW, chipH, 10, 10);

                    // текст всегда контрастный (белый) независимо от подложки/наведения
                    g2.setColor(Color.WHITE);
                    g2.drawString(label, chipX - labelW / 2, chipY + fm.getAscent() / 2 - 1);
                }
            }

            // мёртвая зона в центре — вместо пустого кольца показывает ПОЛНУЮ (без сокращения)
            // подпись наведённого пункта, чтобы усечённый текст в чипе всегда можно было проверить
            // (для пунктов-картинок подписи нет вовсе — см. выше, поэтому и здесь её не показываем)
            if (hover >= 0 && items.get(hover).icon == null) {
                String full = displayLabel(items.get(hover));
                Font bold = labelFont().deriveFont(Font.BOLD, 11f);
                g2.setFont(bold);
                FontMetrics bfm = g2.getFontMetrics();
                int maxW = (DEAD_ZONE - 6) * 2;
                String shown = full;
                while (bfm.stringWidth(shown) > maxW && shown.length() > 1) {
                    shown = shown.substring(0, shown.length() - 1);
                }
                if (!shown.equals(full)) {
                    while (bfm.stringWidth(shown + "…") > maxW && shown.length() > 1) {
                        shown = shown.substring(0, shown.length() - 1);
                    }
                    shown = shown + "…";
                }
                g2.setColor(Palette.ACCENT);
                g2.fillOval(cx - DEAD_ZONE, cy - DEAD_ZONE, DEAD_ZONE * 2, DEAD_ZONE * 2);
                g2.setColor(Color.WHITE);
                g2.drawString(shown, cx - bfm.stringWidth(shown) / 2, cy + bfm.getAscent() / 2 - 1);
            } else {
                g2.setColor(Palette.MUTED);
                g2.drawOval(cx - DEAD_ZONE, cy - DEAD_ZONE, DEAD_ZONE * 2, DEAD_ZONE * 2);
            }

            g2.dispose();
        }

        /** Подпись пункта для отображения — у веток добавляется «›», чтобы было видно,
         *  что выбор откроет следующее кольцо, а не выполнит действие сразу. */
        private static String displayLabel(Item item) {
            return item.isBranch() ? item.label + " ›" : item.label;
        }

        private static String shorten(String s, int max) {
            return s.length() <= max ? s : s.substring(0, max - 1) + "…";
        }
    }
}
