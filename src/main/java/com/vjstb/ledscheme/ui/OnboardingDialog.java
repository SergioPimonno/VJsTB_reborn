package com.vjstb.ledscheme.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.vjstb.ledscheme.AppInfo;
import com.vjstb.ledscheme.settings.ContentSection;
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

    private static final String CONTENT_KEY = "onboarding";
    private static final String[] STEPS = {"welcome", "theme", "personalization"};

    /** Только шаги «welcome» и «personalization» — текст (заголовок+тело), полностью
     *  переписываемый через ContentEditorDialog. Шаг «theme» с переключателями темы
     *  оформления остаётся функциональным/не редактируемым как текст. */
    private static final List<ContentSection> DEFAULT_SECTIONS = List.of(
            new ContentSection("LED Scheme Designer v" + AppInfo.VERSION,
                    "Приложение для проектирования схем коммутации LED-экранов и видеосопровождения: "
                    + "проекты → сцены → экраны → LED cabinets, библиотека оборудования, схемы расключения "
                    + "питания и сигнала."
                    + "<br><br>Этот короткий тур поможет настроить пару вещей перед началом работы — его можно "
                    + "пропустить и открыть заново в любой момент через «Настройки → Показать приветствие снова…»."),
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
    private final SettingsManager settings;
    private JPanel welcomePanel;
    private JPanel personalizationPanel;
    private int step = 0;

    public OnboardingDialog(Window owner, SettingsManager settings) {
        super(owner, "Добро пожаловать", ModalityType.APPLICATION_MODAL);
        this.settings = settings;

        rebuildTextSteps();
        cardsPanel.add(buildThemeStep(owner), STEPS[1]);

        JPanel content = new JPanel(new BorderLayout());
        content.add(cardsPanel, BorderLayout.CENTER);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton editText = new JButton("✎ Редактировать текст");
        editText.addActionListener(e -> openEditor());
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
        nav.add(editText);
        nav.add(skip);
        nav.add(back);
        nav.add(next);
        nav.add(finish);
        content.add(nav, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(480, 360);
        setLocationRelativeTo(owner);
        goTo(0);
    }

    private List<ContentSection> sections() {
        List<ContentSection> custom = settings.getCustomContent(CONTENT_KEY);
        return custom != null && custom.size() == DEFAULT_SECTIONS.size() ? custom : DEFAULT_SECTIONS;
    }

    /** Пересобирает ТОЛЬКО текстовые шаги (welcome/personalization) — «theme» с
     *  переключателями темы строится один раз в конструкторе и не трогается при
     *  редактировании текста. CardLayout.add с уже занятым именем не убирает
     *  СТАРЫЙ компонент из контейнера сам — убираем его явно, иначе он остаётся
     *  осиротевшим потомком cardsPanel при каждом повторном редактировании. */
    private void rebuildTextSteps() {
        List<ContentSection> s = sections();
        if (welcomePanel != null) {
            cardsPanel.remove(welcomePanel);
        }
        welcomePanel = step(s.get(0).getTitle(), s.get(0).getBodyHtml());
        cardsPanel.add(welcomePanel, STEPS[0]);

        if (personalizationPanel != null) {
            cardsPanel.remove(personalizationPanel);
        }
        personalizationPanel = step(s.get(1).getTitle(), s.get(1).getBodyHtml());
        cardsPanel.add(personalizationPanel, STEPS[2]);
    }

    private void openEditor() {
        ContentEditorDialog dlg = new ContentEditorDialog(this, "Редактировать текст приветствия", sections(),
                DEFAULT_SECTIONS, saved -> {
                    settings.setCustomContent(CONTENT_KEY, saved);
                    rebuildTextSteps();
                    cards.show(cardsPanel, STEPS[step]);
                });
        dlg.setVisible(true);
    }

    private void goTo(int newStep) {
        step = Math.max(0, Math.min(STEPS.length - 1, newStep));
        cards.show(cardsPanel, STEPS[step]);
        back.setEnabled(step > 0);
        boolean last = step == STEPS.length - 1;
        next.setVisible(!last);
        finish.setVisible(last);
    }

    private JPanel step(String title, String html) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(20, 24, 10, 24));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(titleLabel);
        p.add(Box.createVerticalStrut(12));
        JLabel body = new JLabel("<html><body style='width: 380px'>" + html + "</body></html>");
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(body);
        return p;
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
        p.add(Box.createVerticalStrut(10));
        p.add(radios);
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
