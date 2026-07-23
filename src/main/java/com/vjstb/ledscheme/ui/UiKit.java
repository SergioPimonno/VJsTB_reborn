package com.vjstb.ledscheme.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Мелкие переиспользуемые построители панелей для страниц этапов. */
public final class UiKit {

    private UiKit() {
    }

    public static JPanel vbox() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    /**
     * Вертикальный бокс с ПРИНУДИТЕЛЬНО фиксированной шириной (высота остаётся
     * динамической — считается живьём через super.getPreferredSize()). Нужен для
     * боковых панелей: без этого ширина «плывёт» от самого широкого потомка
     * (например, сетки кнопок), раздувая всю колонку за пределы окна.
     */
    public static JPanel vboxFixedWidth(int width) {
        JPanel p = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(width, d.height);
            }
        };
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    public static Component vgap() {
        return Box.createVerticalStrut(6);
    }

    public static Component vgap(int px) {
        return Box.createVerticalStrut(px);
    }

    /**
     * Секция с заголовком для СТАТИЧНОГО содержимого (формы, кнопки, текст),
     * которое больше не меняется после вызова. Максимальная высота фиксируется
     * сразу: JViewport растягивает непрокручиваемый контент, который меньше
     * видимой области, поэтому без явного предела BoxLayout отдаёт лишнее место
     * первой попавшейся секции (GridLayout/BorderLayout по умолчанию не ограничены
     * по высоте) вместо завершающего glue.
     *
     * Для содержимого, наполняемого ПОСЛЕ первой сборки (списки, зависящие от
     * данных) используйте {@link #dynamicSection} и вызывайте {@link #recapHeight}
     * после каждого обновления.
     */
    public static JComponent section(String title, JComponent body) {
        JPanel p = sectionPanel(title, body);
        recapHeight(p);
        return p;
    }

    /** Секция для содержимого, которое наполняется позже — высоту нужно пересчитывать явно. */
    public static JComponent dynamicSection(String title, JComponent body) {
        return sectionPanel(title, body);
    }

    /** Заново фиксирует maximumSize секции по текущему preferredSize — вызывать после смены содержимого. */
    public static void recapHeight(JComponent section) {
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, section.getPreferredSize().height));
    }

    private static JPanel sectionPanel(String title, JComponent body) {
        JPanel p = new JPanel(new BorderLayout());
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(4, 4, 6, 4)));
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    public static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Palette.MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    public static String fmt(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.format("%.1f", v);
    }
}
