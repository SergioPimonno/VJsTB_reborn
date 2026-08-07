package com.vjstb.ledscheme.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.vjstb.ledscheme.AppInfo;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.Desktop;
import java.net.URI;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Верхнее меню: настройки, база данных, инструменты, персонализация, справка. */
public class MainMenuBar extends JMenuBar {

    private PersonalizationDialog colorsDialog;
    private PreferencesDialog preferencesDialog;
    private HotkeysDialog hotkeysDialog;

    public MainMenuBar(JFrame owner, AppModel model, SettingsManager settings, Runnable onShowShortcuts) {
        add(buildSettingsMenu(owner, model, settings));
        add(buildDatabaseMenu(owner, model, settings));
        add(buildToolsMenu(owner, model));
        add(buildPersonalizationMenu(owner, settings));
        add(buildHelpMenu(onShowShortcuts));
    }

    private JMenu buildSettingsMenu(JFrame owner, AppModel model, SettingsManager settings) {
        JMenu menu = new JMenu("Настройки");

        JMenuItem reportBug = new JMenuItem("Сообщить о баге…");
        reportBug.addActionListener(e -> openUrl(owner, AppInfo.NEW_ISSUE_URL));
        menu.add(reportBug);

        JMenuItem onboarding = new JMenuItem("Показать приветствие снова…");
        onboarding.addActionListener(e -> new OnboardingDialog(owner, model, settings).setVisible(true));
        menu.add(onboarding);

        JMenuItem scenarios = new JMenuItem("Интерактивные примеры…");
        scenarios.addActionListener(e ->
                ScenarioListDialog.show(owner, model.getWorkspace().getLibrary().getInteractiveScenarios()));
        menu.add(scenarios);

        JMenuItem update = new JMenuItem("Обновить версию…");
        update.addActionListener(e -> com.vjstb.ledscheme.ui.UpdateDialog.show(owner));
        menu.add(update);

        menu.addSeparator();
        JMenuItem version = new JMenuItem("Версия: " + AppInfo.VERSION);
        version.setEnabled(false);
        menu.add(version);
        JMenuItem author = new JMenuItem("Автор: " + AppInfo.AUTHOR);
        author.setEnabled(false);
        menu.add(author);

        return menu;
    }

    private void openUrl(JFrame owner, String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, "Не удалось открыть ссылку:\n" + url, "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Всё, что говорит с сервером/общей библиотекой (Postgres на dxv) — синк,
     *  модерация, облачные проекты, скачивание конфигов приёмных карт и вход в
     *  аккаунт (без него первые три недоступны/ограничены анонимным чтением) —
     *  собрано в одном месте вместо того, чтобы быть раскиданным по «Настройки»
     *  и «Инструменты» вперемешку с чисто локальными действиями. */
    private JMenu buildDatabaseMenu(JFrame owner, AppModel model, SettingsManager settings) {
        JMenu menu = new JMenu("База данных");

        JMenuItem librarySync = new JMenuItem("Синхронизировать библиотеку…");
        librarySync.setToolTipText("Забрать обновления общей библиотеки с сервера — анонимно, только чтение"
                + " (первый шаг синхронизации клиент-сервер, без входа в аккаунт)");
        librarySync.addActionListener(e -> LibrarySyncDialog.show(owner, model, settings));
        menu.add(librarySync);

        JMenuItem moderation = new JMenuItem("Модерация предложений…");
        moderation.setToolTipText("Список предложений в общую библиотеку, ожидающих решения — нужна роль"
                + " модератора/админа (Аккаунт…)");
        moderation.addActionListener(e -> ModerationDialog.show(owner, settings));
        menu.add(moderation);

        JMenuItem cloudProjects = new JMenuItem("Облачные проекты…");
        cloudProjects.setToolTipText("Загрузить/скачать свои проекты целиком на сервер — нужен вход в аккаунт"
                + " (Аккаунт…), но подойдёт любая роль");
        cloudProjects.addActionListener(e -> CloudProjectsDialog.show(owner, model, settings));
        menu.add(cloudProjects);

        JMenuItem cabinetConfig = new JMenuItem("Скачать конфиг приёмной карты…");
        cabinetConfig.setToolTipText("Файл настроек приёмной карты NovaLCT (.rcfgx) для типа кабинета —"
                + " по герцовке и требуемой яркости, из общей библиотеки на сервере");
        cabinetConfig.addActionListener(e -> CabinetConfigPickerDialog.show(owner, model));
        menu.add(cabinetConfig);

        menu.addSeparator();
        JMenuItem account = new JMenuItem("Аккаунт…");
        account.setToolTipText("Вход/регистрация — нужны только для отправки предложений в общую библиотеку,"
                + " чтение библиотеки анонимно");
        account.addActionListener(e -> AccountDialog.show(owner, settings));
        menu.add(account);

        return menu;
    }

    private JMenu buildToolsMenu(JFrame owner, AppModel model) {
        JMenu menu = new JMenu("Инструменты");

        // Импорт из NovaLCT (.scr) временно отключён из меню — формат разобран лишь
        // частично (см. NovaLctScrParser/NovaLctImportDialog), доработка в следующих
        // версиях. Классы намеренно оставлены нетронутыми для продолжения работы.

        // Единственный пункт экспорта в NovaLCT — контроллер-центричный
        // (NovaLctControllerExportDialog): старый, привязанный к одному экрану
        // (NovaLctExportDialog), убран из меню и удалён — этот пункт полностью его
        // покрывает (если контроллер трогает ровно один экран, идёт тем же прямым
        // путём NovaLctScrWriter.write, см. class-javadoc диалога), плюс умеет
        // несколько экранов сразу (LCTPresetMaster). И Standard, и Complex Screen
        // полностью реверс-инжинирены (декомпиляция + побайтовое совпадение с
        // реальными файлами NovaLCT).
        JMenuItem novaLctControllerExport = new JMenuItem("Экспорт NovaLCT для контроллера…");
        novaLctControllerExport.addActionListener(e ->
                com.vjstb.ledscheme.ui.NovaLctControllerExportDialog.showExportFlow(owner, model));
        menu.add(novaLctControllerExport);

        JMenuItem videoTiming = new JMenuItem("Калькулятор видеотайминга…");
        videoTiming.setToolTipText("Расчёт частоты пикселей (VESA CVT/CVT-RB/CVT-RBv2) и проверка, помещается ли"
                + " она в полосу DisplayPort/HDMI/SDI/Ethernet без сжатия — полезно при подборе медиасервера"
                + " и разъёма под нестандартное разрешение LED-стены");
        videoTiming.addActionListener(e -> new VideoTimingCalculatorDialog(owner).setVisible(true));
        menu.add(videoTiming);

        JMenuItem projectorCalc = new JMenuItem("Калькулятор проектора…");
        projectorCalc.setToolTipText("Расчёт требуемого throw ratio объектива, диапазона дистанции и"
                + " освещённости экрана по ANSI-люменам/gain — с экспортом проектора в спецификацию проекта");
        projectorCalc.addActionListener(e ->
                new com.vjstb.ledscheme.ui.ProjectorCalculatorDialog(owner, model).setVisible(true));
        menu.add(projectorCalc);

        return menu;
    }

    /** Три раздела персонализации — каждый в своём независимом окошке (цвета/профили,
     *  предпочтения, горячие клавиши), а не в одном большом диалоге со всеми
     *  разделами сразу — можно держать открытыми одновременно, каждое запоминает
     *  и переиспользует свой экземпляр окна (как раньше был единственный диалог). */
    private JMenu buildPersonalizationMenu(JFrame owner, SettingsManager settings) {
        JMenu menu = new JMenu("Персонализация");
        ButtonGroup group = new ButtonGroup();

        JRadioButtonMenuItem dark = new JRadioButtonMenuItem("Тёмная тема", true);
        JRadioButtonMenuItem light = new JRadioButtonMenuItem("Светлая тема", false);
        dark.addActionListener(e -> applyTheme(owner, new FlatDarkLaf()));
        light.addActionListener(e -> applyTheme(owner, new FlatLightLaf()));
        group.add(dark);
        group.add(light);
        menu.add(dark);
        menu.add(light);

        menu.addSeparator();
        JMenuItem colors = new JMenuItem("Цвета и профили…");
        colors.addActionListener(e -> {
            if (colorsDialog == null) {
                colorsDialog = new PersonalizationDialog(owner, settings);
            }
            colorsDialog.setVisible(true);
            colorsDialog.toFront();
        });
        menu.add(colors);

        JMenuItem preferences = new JMenuItem("Предпочтения…");
        preferences.addActionListener(e -> {
            if (preferencesDialog == null) {
                preferencesDialog = new PreferencesDialog(owner, settings);
            }
            preferencesDialog.setVisible(true);
            preferencesDialog.toFront();
        });
        menu.add(preferences);

        JMenuItem hotkeys = new JMenuItem("Горячие клавиши…");
        hotkeys.addActionListener(e -> {
            if (hotkeysDialog == null) {
                hotkeysDialog = new HotkeysDialog(owner, settings);
            }
            hotkeysDialog.setVisible(true);
            hotkeysDialog.toFront();
        });
        menu.add(hotkeys);

        return menu;
    }

    private void applyTheme(JFrame owner, javax.swing.LookAndFeel laf) {
        try {
            UIManager.setLookAndFeel(laf);
            SwingUtilities.updateComponentTreeUI(owner);
        } catch (Exception ignored) {
            // не критично — останется текущая тема
        }
    }

    private JMenu buildHelpMenu(Runnable onShowShortcuts) {
        JMenu menu = new JMenu("Справка");
        JMenuItem shortcuts = new JMenuItem("Горячие клавиши");
        shortcuts.addActionListener(e -> onShowShortcuts.run());
        JMenuItem about = new JMenuItem("О программе");
        about.addActionListener(e -> JOptionPane.showMessageDialog(null,
                "LED Scheme Designer\nв" + AppInfo.VERSION + "\nАвтор: " + AppInfo.AUTHOR
                        + "\n\nПроектирование схем коммутации LED-экранов и видеосопровождения.",
                "О программе", JOptionPane.INFORMATION_MESSAGE));
        menu.add(shortcuts);
        menu.add(about);
        return menu;
    }
}
