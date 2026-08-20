package com.vjstb.ledscheme.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.vjstb.ledscheme.AppInfo;
import com.vjstb.ledscheme.model.ContentSection;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Приветственный тур при первом запуске — коротко знакомит с темой оформления и
 * тем, где искать остальную персонализацию (цвета/предпочтения/горячие клавиши).
 * Доступен повторно через Настройки → «Показать приветствие снова…» (см.
 * MainMenuBar) — тогда не трогает флаг «показан один раз» дополнительным разом,
 * это уже не первый показ.
 */
public class OnboardingDialog extends JDialog {

    private static final String[] STEPS = {"welcome", "workflow", "theme", "personalization"};

    /** Шаги «welcome»/«workflow»/«personalization» — текст (заголовок+тело), общая
     *  справочная данные (Task #135/v2.0), редактируется только через отдельную
     *  админ-консоль (ledscheme-admin), см. GuideDialog. Шаг «theme» с переключателями
     *  темы оформления остаётся функциональным/не редактируемым как текст. */
    private static final List<ContentSection> DEFAULT_SECTIONS = List.of(
            new ContentSection("AVE_ToolBox v" + AppInfo.VERSION,
                    "Приложение для проектирования схем коммутации LED-экранов и видеосопровождения — от "
                    + "структуры сцены до итогового пакета документации."
                    + "<br><br>Этот тур в несколько шагов покажет общий порядок работы и где что искать — его "
                    + "можно пропустить и открыть заново в любой момент через «Настройки → Показать приветствие "
                    + "снова…»."),
            new ContentSection("Как устроена работа",
                    "Работа строится сверху вниз: <b>Проект → Сцена → Экран → LED cabinets</b>. Дальше — пять "
                    + "этапов вверху окна, обычно в этом порядке:"
                    + "<br><br><b>Сетап</b> — создаёте экраны, задаёте их сетку и тип кабинета, настраиваете форму "
                    + "(в т.ч. неровную) и монтаж. Здесь же — прериг: сводка по весу/мощности и точки подвеса."
                    + "<br><br><b>Питание</b> — расключаете экраны на силовые цепочки, следите за нагрузкой "
                    + "(авто-расчёт по разъёму/автомату)."
                    + "<br><br><b>Сигнал</b> — контроллеры и их порты, сигнальные цепочки на кабинеты, резервные "
                    + "линии."
                    + "<br><br><b>Генерация масок</b> — превью и экспорт масок под контент; здесь же канвас для "
                    + "точной раскладки нескольких экранов под один видеовыход."
                    + "<br><br><b>Вывод</b> — итоговый пакет документации: схемы, спецификации, экспорт в "
                    + "NovaLCT/Resolume."
                    + "<br><br><b>Библиотеки</b> — не этап по порядку, а общий склад: типы кабинетов/контроллеров, "
                    + "пресеты оборудования, кабели — доступен на любом шаге."),
            new ContentSection("Остальная персонализация",
                    "В верхнем меню «Персонализация» — три независимых окна:"
                    + "<br>· <b>Цвета и профили</b> — цвета фаз питания/сигнальных цепочек, несколько именованных "
                    + "профилей персонализации."
                    + "<br>· <b>Предпочтения</b> — поведенческие переключатели (мини-превью сцены, привязка "
                    + "перетаскивания, коммутация через гнёзда, «защита от дурака», контроль электрической нагрузки "
                    + "и другие)."
                    + "<br>· <b>Горячие клавиши</b> — переназначение любых сочетаний клавиш/мыши под себя."
                    + "<br><br>В «Настройки» — ссылка на баг-трекер и информация о версии."
                    + "<br><br>Подробное руководство по работе со схемами, цепочками и картами — в «Справка → "
                    + "Горячие клавиши» и на панели инструментов каждого этапа."));

    private final CardLayout cards = new CardLayout();
    private final JPanel cardsPanel = new JPanel(cards);
    private final JButton back = new JButton("Назад");
    private final JButton next = new JButton("Далее");
    private final JButton finish = new JButton("Готово");
    private final AppModel model;
    private final SettingsManager settings;
    private javax.swing.JScrollPane welcomePanel;
    private javax.swing.JScrollPane workflowPanel;
    private javax.swing.JScrollPane personalizationPanel;
    private int step = 0;

    public OnboardingDialog(Window owner, AppModel model, SettingsManager settings) {
        super(owner, "Добро пожаловать", ModalityType.APPLICATION_MODAL);
        this.model = model;
        this.settings = settings;

        rebuildTextSteps();
        cardsPanel.add(scrollWrap(buildThemeStep(owner)), STEPS[2]);

        JPanel content = new JPanel(new BorderLayout());
        content.add(cardsPanel, BorderLayout.CENTER);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton skip = new JButton("Пропустить");
        skip.addActionListener(e -> {
            settings.setOnboardingCompleted(true);
            dispose();
        });
        back.addActionListener(e -> goTo(step - 1));
        next.addActionListener(e -> goTo(step + 1));
        finish.addActionListener(e -> {
            settings.setOnboardingCompleted(true);
            dispose();
        });
        nav.add(skip);
        nav.add(back);
        nav.add(next);
        nav.add(finish);
        content.add(nav, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(480, 420);
        setLocationRelativeTo(owner);
        goTo(0);
    }

    private List<ContentSection> sections() {
        List<ContentSection> synced = model.getWorkspace().getLibrary().getOnboardingSections();
        return synced != null && synced.size() == DEFAULT_SECTIONS.size() ? synced : DEFAULT_SECTIONS;
    }

    /** Пересобирает ТОЛЬКО текстовые шаги (welcome/workflow/personalization) — «theme»
     *  с переключателями темы строится один раз в конструкторе и не трогается при
     *  редактировании текста. CardLayout.add с уже занятым именем не убирает
     *  СТАРЫЙ компонент из контейнера сам — убираем его явно, иначе он остаётся
     *  осиротевшим потомком cardsPanel при каждом повторном редактировании. */
    private void rebuildTextSteps() {
        List<ContentSection> s = sections();
        if (welcomePanel != null) {
            cardsPanel.remove(welcomePanel);
        }
        welcomePanel = scrollWrap(step(s.get(0).getTitle(), s.get(0).getBodyHtml()));
        cardsPanel.add(welcomePanel, STEPS[0]);

        if (workflowPanel != null) {
            cardsPanel.remove(workflowPanel);
        }
        workflowPanel = scrollWrap(step(s.get(1).getTitle(), s.get(1).getBodyHtml()));
        cardsPanel.add(workflowPanel, STEPS[1]);

        if (personalizationPanel != null) {
            cardsPanel.remove(personalizationPanel);
        }
        personalizationPanel = scrollWrap(step(s.get(2).getTitle(), s.get(2).getBodyHtml()));
        cardsPanel.add(personalizationPanel, STEPS[3]);
    }

    private void goTo(int newStep) {
        step = Math.max(0, Math.min(STEPS.length - 1, newStep));
        cards.show(cardsPanel, STEPS[step]);
        back.setEnabled(step > 0);
        boolean last = step == STEPS.length - 1;
        next.setVisible(!last);
        finish.setVisible(last);
    }

    /** Без ширины в CSS у HTML-label preferredSize получается ПОЧТИ
     *  неограниченным (естественная ширина в одну строку, проверено эмпирически:
     *  ~1100px на реальном тексте) — со шириной, наоборот, JLabel.getPreferredSize()
     *  её игнорирует (см. {@link NamedRenderer} этой кодовой базы) и снова
     *  отчитывается "естественной" шириной, а рисуется уже по навязанной, более
     *  узкой — середина длинных строк молча пропадает без переноса. Оба пути
     *  ломают отображение в диалоге фиксированного размера (баг-репорт:
     *  обрезанный текст в приветствии). Правильный путь — не бороться с
     *  preferredSize вообще, а сделать саму панель шага {@link Scrollable} с
     *  {@code getScrollableTracksViewportWidth()==true}: тогда JViewport задаёт
     *  ей РЕАЛЬНУЮ ширину вьюпорта (не её собственный preferredSize), HTML
     *  переносится по словам по этой настоящей ширине при обычной раскладке/
     *  отрисовке (без единого ручного View.setSize/setPreferredSize трюка), а
     *  высота остаётся свободной под скролл. */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {
        WidthTrackingPanel(java.awt.LayoutManager layout) {
            super(layout);
        }

        @Override
        public java.awt.Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
            return 100;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private JPanel step(String title, String html) {
        JPanel p = new WidthTrackingPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(20, 24, 10, 24));
        JLabel body = new JLabel("<html><body>"
                + "<div style='font-size:16px;font-weight:bold;margin-bottom:10px;'>" + title + "</div>"
                + html + "</body></html>");
        p.add(body, BorderLayout.NORTH);
        return p;
    }

    /** Оборачивает шаг в {@link javax.swing.JScrollPane} перед добавлением в
     *  {@code cardsPanel} — шаг «Остальная персонализация» (несколько абзацев +
     *  список) при фиксированном размере диалога {@code setSize(480, 360)}
     *  обрезался снизу без скролла (баг-репорт: обрезанный текст в приветствии).
     *  {@link GuideDialog#section} уже решает это так же для своих вкладок — тот
     *  же приём здесь, отдельным шагом от {@link #step}, чтобы {@link #buildThemeStep}
     *  по-прежнему мог дозаполнять возвращённую {@link JPanel} (радиокнопки темы)
     *  до оборачивания. */
    private javax.swing.JScrollPane scrollWrap(JPanel p) {
        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(p);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel buildThemeStep(Window owner) {
        JPanel p = step("Тема оформления", "Выберите, как приложению удобнее выглядеть — можно сменить "
                + "в любой момент через «Персонализация» в верхнем меню.");
        JPanel radios = new JPanel();
        radios.setLayout(new BoxLayout(radios, BoxLayout.Y_AXIS));
        radios.setAlignmentX(Component.LEFT_ALIGNMENT);
        JRadioButton dark = new JRadioButton("Тёмная тема", true);
        JRadioButton light = new JRadioButton("Светлая тема", false);
        dark.setAlignmentX(Component.LEFT_ALIGNMENT);
        light.setAlignmentX(Component.LEFT_ALIGNMENT);
        ButtonGroup group = new ButtonGroup();
        group.add(dark);
        group.add(light);
        dark.addActionListener(e -> applyTheme(owner, new FlatDarkLaf()));
        light.addActionListener(e -> applyTheme(owner, new FlatLightLaf()));
        radios.add(dark);
        radios.add(light);
        JPanel radiosWrap = new JPanel(new BorderLayout());
        radiosWrap.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        radiosWrap.add(radios, BorderLayout.NORTH);
        p.add(radiosWrap, BorderLayout.CENTER);
        return p;
    }

    private void applyTheme(Window owner, javax.swing.LookAndFeel laf) {
        try {
            UIManager.setLookAndFeel(laf);
            SwingUtilities.updateComponentTreeUI(owner);
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception ignored) {
            // не критично — останется текущая тема
        }
    }

}
