package com.vjstb.ledscheme.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.vjstb.ledscheme.AppInfo;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import javax.swing.ButtonGroup;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Верхнее меню: настройки, инструменты, персонализация, справка. */
public class MainMenuBar extends JMenuBar {

    private PersonalizationDialog colorsDialog;
    private PreferencesDialog preferencesDialog;
    private HotkeysDialog hotkeysDialog;

    public MainMenuBar(JFrame owner, AppModel model, SettingsManager settings, Runnable onShowShortcuts) {
        add(buildSettingsMenu(owner, settings));
        add(buildToolsMenu(owner, model));
        add(buildPersonalizationMenu(owner, settings));
        add(buildHelpMenu(onShowShortcuts));
    }

    private JMenu buildSettingsMenu(JFrame owner, SettingsManager settings) {
        JMenu menu = new JMenu("Настройки");

        JMenuItem reportBug = new JMenuItem("Сообщить о баге…");
        reportBug.addActionListener(e -> openUrl(owner, AppInfo.NEW_ISSUE_URL));
        menu.add(reportBug);

        JMenuItem onboarding = new JMenuItem("Показать приветствие снова…");
        onboarding.addActionListener(e -> new OnboardingDialog(owner, settings).setVisible(true));
        menu.add(onboarding);

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

    private JMenu buildToolsMenu(JFrame owner, AppModel model) {
        JMenu menu = new JMenu("Инструменты");

        JMenuItem exportLib = new JMenuItem("Экспорт библиотеки кабинетов…");
        exportLib.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("led-cabinet-library.json"));
            fc.setFileFilter(new FileNameExtensionFilter("JSON", "json"));
            if (fc.showSaveDialog(owner) == JFileChooser.APPROVE_OPTION) {
                try {
                    model.exportLibrary(fc.getSelectedFile());
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(owner, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JMenuItem importLib = new JMenuItem("Импорт библиотеки кабинетов…");
        importLib.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("JSON", "json"));
            if (fc.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
                try {
                    int n = model.importLibrary(fc.getSelectedFile());
                    JOptionPane.showMessageDialog(owner, "Импортировано кабинетов: " + n, "Импорт",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(owner, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        menu.add(exportLib);
        menu.add(importLib);
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
