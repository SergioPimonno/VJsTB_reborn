package com.vjstb.ledscheme.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

/**
 * «Руководство» — единое место для пояснений, как пользоваться приложением.
 * Раньше эти же тексты были разбросаны прямо по рабочим панелям (Питание,
 * Сигнал, Сетап, Библиотеки) в виде вечно видимых подписей, загромождавших
 * рабочее пространство — теперь они собраны здесь, по разделам, каждый —
 * с небольшой схемой-иллюстрацией.
 */
public class GuideDialog extends JDialog {

    public GuideDialog(Window owner) {
        super(owner, "Руководство", ModalityType.MODELESS);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Построение цепочек", buildChainSection());
        tabs.addTab("Контроллеры и порты", buildControllersSection());
        tabs.addTab("Радиальное меню (Сетап)", buildRadialMenuSection());
        tabs.addTab("Коммутация через гнёзда", buildSocketSection());
        tabs.addTab("Библиотека карт", buildCardsSection());
        tabs.addTab("Контроль нагрузки", buildLoadTrackingSection());

        JPanel content = new JPanel(new BorderLayout());
        content.add(tabs, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        bottom.add(close);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(560, 480);
        setLocationRelativeTo(owner);
    }

    private JScrollPane section(Illustration illustration, String html) {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        illustration.setAlignmentX(LEFT_ALIGNMENT);
        body.add(illustration);
        body.add(Box.createVerticalStrut(10));
        JLabel label = new JLabel("<html><body style='width: 460px'>" + html + "</body></html>");
        label.setAlignmentX(LEFT_ALIGNMENT);
        body.add(label);
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    // ---- Построение цепочек ----

    private JScrollPane buildChainSection() {
        Illustration ill = new Illustration(460, 150, g2 -> {
            int cell = 40;
            int x0 = 20;
            int y0 = 20;
            // сетка 4x1 кабинетов
            for (int i = 0; i < 4; i++) {
                g2.setColor(Palette.PANEL);
                g2.fillRect(x0 + i * cell, y0, cell - 4, cell - 4);
                g2.setColor(Palette.BORDER);
                g2.drawRect(x0 + i * cell, y0, cell - 4, cell - 4);
            }
            // цепочка: клик на первом (точка), стрелки до третьего
            Color chain = Palette.ACCENT;
            g2.setColor(chain);
            g2.setStroke(new BasicStroke(2.5f));
            int cy = y0 + (cell - 4) / 2;
            g2.fillOval(x0 + (cell - 4) / 2 - 4, cy - 4, 8, 8);
            drawArrow(g2, x0 + cell / 2, cy, x0 + cell + cell / 2, cy);
            drawArrow(g2, x0 + cell + cell / 2, cy, x0 + 2 * cell + cell / 2, cy);
            // ПКМ — крестик на четвёртом (убрать из цепочки)
            g2.setColor(new Color(0xff6b6b));
            int rx = x0 + 3 * cell + (cell - 4) / 2;
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(rx - 8, cy - 8, rx + 8, cy + 8);
            g2.drawLine(rx - 8, cy + 8, rx + 8, cy - 8);

            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10f));
            g2.setColor(Palette.MUTED);
            g2.drawString("1) клик — начало", x0, y0 + cell + 14);
            g2.drawString("2) ЛКМ/протяжка/стрелки — добавить", x0, y0 + cell + 30);
            g2.drawString("3) ПКМ — убрать из строящейся цепочки", x0, y0 + cell + 46);
            g2.drawString("Esc (или переназначенная клавиша) — завершить и сохранить", x0, y0 + cell + 62);
        });
        return section(ill, "Клик по непрописанному кабинету — начать цепочку для выбранной фазы/порта."
                + "<br>Клик, зажатая ЛКМ или стрелки на клавиатуре — добавить ещё кабинеты в строящуюся цепочку."
                + "<br>ПКМ по кабинету во время построения — убрать его из цепочки (не путать с ПКМ по уже "
                + "сохранённой цепочке — это разрыв связи в конкретном месте, отдельное действие)."
                + "<br>Esc — завершить и сохранить цепочку (клавишу можно переназначить: Персонализация → "
                + "«Горячие клавиши»)."
                + "<br><br>В режиме «Показать все экраны сцены» цепочка может физически продолжаться с одного "
                + "экрана на другой — просто продолжайте кликать по кабинетам на соседнем экране, не завершая "
                + "текущую цепочку.");
    }

    // ---- Контроллеры и порты ----

    private JScrollPane buildControllersSection() {
        Illustration ill = new Illustration(460, 120, g2 -> {
            drawControllerBox(g2, 20, 20, 160, 60, "Контроллер 1", "1 2 3 4 5 6 7 8", true);
            drawControllerBox(g2, 220, 20, 160, 60, "Контроллер 2", "1 2 3 4", false);
            g2.setColor(Palette.MUTED);
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10f));
            g2.drawString("ЛКМ по строке контроллера слева — выбрать его для показа/расключения справа",
                    20, 105);
        });
        return section(ill, "Контроллеры общие для всей сцены — их видят все экраны сцены."
                + "<br>ЛКМ по строке контроллера в списке — выбрать его: сетка портов справа всегда показывает "
                + "порты ТОЛЬКО одного выбранного контроллера, локальными номерами (1..N), а не сквозной суммой "
                + "по всем контроллерам сцены."
                + "<br>ПКМ по строке контроллера — пометить его «в резерв», следующий ЛКМ по ДРУГОМУ контроллеру "
                + "создаёт связку основной→резерв (весь контроллер подхватывает сигнал другого)."
                + "<br>2×клик по кнопке порта — назначить резервный порт (в пределах текущего контроллера)."
                + "<br>Если контроллеров в сцене несколько, порт в списке цепочек показывается как «C{номер "
                + "контроллера}·P{локальный номер}», например «C2·3» — третий порт второго контроллера.");
    }

    // ---- Радиальное меню ----

    private JScrollPane buildRadialMenuSection() {
        Illustration ill = new Illustration(460, 160, g2 -> {
            int cx = 230, cy = 75, r = 55;
            g2.setColor(Palette.PANEL);
            g2.fillOval(cx - r, cy - r, r * 2, r * 2);
            g2.setColor(Palette.BORDER);
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
            String[] labels = {"Скрыть", "Форма ›", "Тип ›"};
            Color[] colors = {Palette.MUTED, Palette.BORDER, Palette.ACCENT};
            for (int i = 0; i < 3; i++) {
                double theta = i * 2 * Math.PI / 3;
                int sx = cx + (int) Math.round(Math.sin(theta) * r * 0.65);
                int sy = cy - (int) Math.round(Math.cos(theta) * r * 0.65);
                g2.setColor(colors[i]);
                g2.fillOval(sx - 14, sy - 14, 28, 28);
                g2.setColor(Color.WHITE);
                g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 9f));
                java.awt.FontMetrics fm = g2.getFontMetrics();
                g2.drawString(labels[i], sx - fm.stringWidth(labels[i]) / 2, sy + 25);
            }
            g2.setColor(Palette.MUTED);
            g2.drawOval(cx - 15, cy - 15, 30, 30);
        });
        return section(ill, "В Сетапе, в редакторе формы экрана: клик (или протяжка ЛКМ) по ячейке — "
                + "включить/исключить кабинет (задаёт непрямоугольную форму экрана)."
                + "<br>ПКМ — зажать и повести курсор к нужному пункту (как в Krita) — открывает радиальное меню: "
                + "«Скрыть/Показать», «Форма ›» и «Тип ›» (у последних двух — второй уровень со списком "
                + "конкретных значений)."
                + "<br>Отпускание в центре (мёртвая зона) — отмена без выбора."
                + "<br>Del/Backspace над ячейкой — то же самое, что «Скрыть/Показать», но с клавиатуры."
                + "<br>Ctrl+колесо — масштаб сетки.");
    }

    // ---- Гнёзда разъёмов ----

    private JScrollPane buildSocketSection() {
        Illustration ill = new Illustration(460, 130, g2 -> {
            drawNodeBox(g2, 20, 20, 150, 70, "Проходная", "6×CEE16A");
            drawNodeBox(g2, 290, 20, 150, 70, "Экран 2", "");
            g2.setColor(Palette.ACCENT);
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(170, 55, 290, 55);
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10f));
            g2.drawString("4×CEE16A", 190, 48);
            g2.setColor(Palette.MUTED);
            g2.drawString("Тип кабеля определяется автоматически по типу гнезда — вводите только количество.",
                    20, 115);
        });
        return section(ill, "Если в Персонализации включена «коммутация через гнёзда разъёмов», в общей схеме "
                + "соединение цепляется за конкретное гнездо карты/разъёма, а не за узел целиком."
                + "<br>Тип кабеля определяется автоматически по типу гнезда — при подписи связи нужно указать "
                + "только количество линий."
                + "<br>У каждого гнезда есть предел — сколько ещё линий можно провести (по числу разъёмов "
                + "группы за вычетом уже занятых другими связями). Предел — минимум из ДВУХ концов связи: если "
                + "лимит меньше ожидаемого, окно подписи связи покажет, какой именно узел его ограничивает."
                + "<br>От одного и того же гнезда (например 6×CEE16A) можно провести НЕСКОЛЬКО отдельных связей "
                + "к РАЗНЫМ узлам — общий лимит на группу разъёмов один, но получателей может быть несколько.");
    }

    // ---- Библиотека карт ----

    private JScrollPane buildCardsSection() {
        Illustration ill = new Illustration(460, 130, g2 -> {
            drawListBox(g2, 20, 20, 190, 90, "Состав узла", new String[]{"Карта A", "Карта B", "Карта A"});
            drawListBox(g2, 250, 20, 190, 90, "Библиотека карт", new String[]{"Карта A", "Карта B", "Карта C"});
            drawArrow(g2, 250, 65, 210, 65);
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 9f));
            g2.setColor(Palette.MUTED);
            g2.drawString("двойной клик / перетаскивание", 205, 55);
        });
        return section(ill, "При добавлении узла оборудования из пресета с картами открывается диалог сборки: "
                + "слева — состав узла (порядок = порядок отрисовки), справа — библиотека доступных карт-шаблонов."
                + "<br>Двойной клик по карте справа или перетаскивание её в список слева — добавить ЭКЗЕМПЛЯР "
                + "карты (одинаковых карт может быть несколько — просто добавьте нужный шаблон повторно)."
                + "<br>▲/▼ или перетаскивание внутри левого списка — изменить порядок карт в узле."
                + "<br>«Удалить» — убрать карту из состава (карта остаётся в библиотеке, доступна снова)."
                + "<br>Название карты на схеме пишется один раз на группу её портов — если карт с одинаковым "
                + "именем несколько (несколько экземпляров одного шаблона), у каждого экземпляра — своя подпись.");
    }

    // ---- Контроль нагрузки ----

    private JScrollPane buildLoadTrackingSection() {
        Illustration ill = new Illustration(460, 130, g2 -> {
            drawNodeBox(g2, 20, 30, 150, 60, "Щит", "1×CEE 16A");
            drawNodeBox(g2, 290, 30, 150, 60, "Экран", "4500 Вт");
            g2.setColor(new Color(0xf0883e));
            g2.setStroke(new BasicStroke(3f));
            g2.drawRoundRect(19, 29, 152, 62, 10, 10);
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 14f));
            g2.drawString("⚠", 150, 44);
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(170, 60, 290, 60);
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10f));
            g2.setColor(Palette.MUTED);
            g2.drawString("4500 Вт нагрузки не помещается в ~3260 Вт ёмкости одной фазы CEE 16A", 20, 110);
        });
        return section(ill, "Приложение автоматически считает электрическую нагрузку и сравнивает её с ёмкостью "
                + "разъёма/автомата — и для отдельной цепочки питания экрана (список цепочек на этапе «Питание»), "
                + "и суммарно для силового узла общей схемы (щит/дистрибьютор — оранжевая рамка + ⚠, если сумма "
                + "того, что уходит через его исходящие связи, превышает ёмкость входа)."
                + "<br>Расчёт: 220В, cosφ=1; номинал PowerCon/TRUEcon фиксирован — 16А (по вводному кабелю за "
                + "разъёмом), для CEE/Schuko — по названию (16/32/63/125А), для «Другой» тип разъёма кабинета — "
                + "задаётся вручную в библиотеке кабинетов."
                + "<br>Запас по умолчанию — около 92.6% от номинала (инженерное правило: CEE125A физически ~81кВт, "
                + "реально нагружают не больше ~75кВт). Для конкретного силового узла схемы запас можно "
                + "переопределить в диалоге «Разъёмы питания…» (например, 100% для проходного разветвителя, "
                + "а не вводного щита) — там же задаётся число фаз, реально разведённых на группе разъёмов, и "
                + "номинал автомата, если он ниже номинала самой розетки."
                + "<br>Неподтверждённая перегрузка цепочки блокирует «Сформировать пакет документации» на этапе "
                + "«Вывод» — кнопка «Я знаю» в списке цепочек подтверждает её (предупреждение вернётся, если "
                + "нагрузка вырастет ещё больше)."
                + "<br>Если случай нестандартный и расчёт ему не подходит — выключите «Контроль электрической "
                + "нагрузки» целиком в Персонализации → Предпочтения и считайте нагрузку самостоятельно.");
    }

    // ---- рисовалки ----

    private static void drawArrow(Graphics2D g2, int x1, int y1, int x2, int y2) {
        g2.drawLine(x1, y1, x2, y2);
        double angle = Math.atan2(y2 - y1, x2 - x1);
        int ah = 7;
        g2.drawLine(x2, y2, (int) (x2 - ah * Math.cos(angle - Math.PI / 6)), (int) (y2 - ah * Math.sin(angle - Math.PI / 6)));
        g2.drawLine(x2, y2, (int) (x2 - ah * Math.cos(angle + Math.PI / 6)), (int) (y2 - ah * Math.sin(angle + Math.PI / 6)));
    }

    private static void drawControllerBox(Graphics2D g2, int x, int y, int w, int h, String title, String ports,
                                           boolean selected) {
        g2.setColor(selected ? Palette.ACCENT.darker() : Palette.PANEL);
        g2.fillRoundRect(x, y, w, h, 8, 8);
        g2.setColor(selected ? Palette.ACCENT : Palette.BORDER);
        g2.setStroke(new BasicStroke(selected ? 2f : 1f));
        g2.drawRoundRect(x, y, w, h, 8, 8);
        g2.setColor(Color.WHITE);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 11f));
        g2.drawString(title, x + 10, y + 20);
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10f));
        g2.drawString(ports, x + 10, y + 42);
    }

    private static void drawNodeBox(Graphics2D g2, int x, int y, int w, int h, String title, String sub) {
        g2.setColor(Palette.PANEL);
        g2.fillRoundRect(x, y, w, h, 8, 8);
        g2.setColor(Palette.BORDER);
        g2.drawRoundRect(x, y, w, h, 8, 8);
        g2.setColor(Color.WHITE);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 11f));
        g2.drawString(title, x + 10, y + 20);
        if (!sub.isEmpty()) {
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10f));
            g2.setColor(Palette.MUTED);
            g2.drawString(sub, x + 10, y + 40);
        }
        g2.setColor(Palette.ACCENT);
        g2.fillOval(x + w - 8, y + h / 2 - 4, 8, 8);
    }

    private static void drawListBox(Graphics2D g2, int x, int y, int w, int h, String title, String[] items) {
        g2.setColor(Palette.PANEL);
        g2.fillRoundRect(x, y, w, h, 8, 8);
        g2.setColor(Palette.BORDER);
        g2.drawRoundRect(x, y, w, h, 8, 8);
        g2.setColor(Color.WHITE);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 10f));
        g2.drawString(title, x + 8, y + 15);
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10f));
        g2.setColor(Palette.MUTED);
        for (int i = 0; i < items.length; i++) {
            g2.drawString("• " + items[i], x + 8, y + 32 + i * 16);
        }
    }

    /** Маленькая схема-иллюстрация — просто область, рисуемая переданным колбэком. */
    private static final class Illustration extends JPanel {
        Illustration(int w, int h, Consumer<Graphics2D> painter) {
            setPreferredSize(new Dimension(w, h));
            setMaximumSize(new Dimension(w, h));
            this.painter = painter;
            setOpaque(false);
        }

        private final Consumer<Graphics2D> painter;

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            painter.accept(g2);
            g2.dispose();
        }
    }
}
