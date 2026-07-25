package com.vjstb.ledscheme.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.Desktop;
import java.io.File;
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

    private PersonalizationDialog personalizationDialog;

    public MainMenuBar(JFrame owner, AppModel model, SettingsManager settings, Runnable onShowShortcuts) {
        add(buildSettingsMenu(owner, model));
        add(buildToolsMenu(owner, model));
        add(buildPersonalizationMenu(owner, settings));
        add(buildHelpMenu(onShowShortcuts));
    }

    private JMenu buildSettingsMenu(JFrame owner, AppModel model) {
        JMenu menu = new JMenu("Настройки");
        JMenuItem openDataFolder = new JMenuItem("Открыть папку с данными…");
        openDataFolder.addActionListener(e -> {
            File file = model.getStore().getWorkspaceFile().getParentFile();
            try {
                if (Desktop.isDesktopSupported() && file != null) {
                    Desktop.getDesktop().open(file);
                }
            } catch (Exception ignored) {
                // не критично
            }
        });
        menu.add(openDataFolder);

        JMenuItem update = new JMenuItem("Обновить версию…");
        update.addActionListener(e -> com.vjstb.ledscheme.ui.UpdateDialog.show(owner));
        menu.add(update);

        return menu;
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
            if (personalizationDialog == null) {
                personalizationDialog = new PersonalizationDialog(owner, settings);
            }
            personalizationDialog.setVisible(true);
            personalizationDialog.toFront();
        });
        menu.add(colors);
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
                "LED Scheme Designer\nv1.1\n\nПроектирование схем коммутации LED-экранов и видеосопровождения.",
                "О программе", JOptionPane.INFORMATION_MESSAGE));
        menu.add(shortcuts);
        menu.add(about);
        return menu;
    }
}
