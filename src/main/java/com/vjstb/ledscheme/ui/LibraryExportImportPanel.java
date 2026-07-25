package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.service.AppModel;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Единый инструмент экспорта/импорта библиотек: выпадающий список типа
 * библиотеки (включая «Всё сразу») + кнопки «Экспорт»/«Импорт». Общий для
 * этапа «Библиотеки» и меню «Инструменты» (см. MainMenuBar) — чтобы список
 * типов и правила совпадения при импорте не расходились в двух разных местах.
 */
public class LibraryExportImportPanel extends JPanel {

    private enum Kind {
        CABINET("Кабинеты", "led-cabinet-library.json"),
        CONTROLLER("Контроллеры", "led-controller-library.json"),
        EQUIPMENT("Оборудование (пресеты)", "led-equipment-presets.json"),
        CABLE("Кабели", "led-cable-library.json"),
        ALL("Всё сразу", "led-all-libraries.json");

        final String label;
        final String defaultFileName;

        Kind(String label, String defaultFileName) {
            this.label = label;
            this.defaultFileName = defaultFileName;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public LibraryExportImportPanel(AppModel model) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JComboBox<Kind> kindBox = new JComboBox<>(Kind.values());
        JButton exportBtn = new JButton("Экспорт…");
        JButton importBtn = new JButton("Импорт…");

        exportBtn.addActionListener(e -> {
            Kind kind = (Kind) kindBox.getSelectedItem();
            Window owner = SwingUtilities.getWindowAncestor(this);
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File(kind.defaultFileName));
            fc.setFileFilter(new FileNameExtensionFilter("JSON", "json"));
            if (fc.showSaveDialog(owner) == JFileChooser.APPROVE_OPTION) {
                try {
                    exportKind(model, kind, fc.getSelectedFile());
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(owner, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        importBtn.addActionListener(e -> {
            Kind kind = (Kind) kindBox.getSelectedItem();
            Window owner = SwingUtilities.getWindowAncestor(this);
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("JSON", "json"));
            if (fc.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
                try {
                    int n = importKind(model, kind, fc.getSelectedFile());
                    JOptionPane.showMessageDialog(owner, "Импортировано записей: " + n, "Импорт",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(owner, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        add(new JLabel("Библиотека:"));
        add(kindBox);
        add(exportBtn);
        add(importBtn);
    }

    private static void exportKind(AppModel model, Kind kind, File file) {
        switch (kind) {
            case CABINET -> model.exportCabinetLibrary(file);
            case CONTROLLER -> model.exportControllerLibrary(file);
            case EQUIPMENT -> model.exportEquipmentPresetLibrary(file);
            case CABLE -> model.exportCableLibrary(file);
            case ALL -> model.exportAllLibraries(file);
        }
    }

    private static int importKind(AppModel model, Kind kind, File file) {
        return switch (kind) {
            case CABINET -> model.importCabinetLibrary(file);
            case CONTROLLER -> model.importControllerLibrary(file);
            case EQUIPMENT -> model.importEquipmentPresetLibrary(file);
            case CABLE -> model.importCableLibrary(file);
            case ALL -> model.importAllLibraries(file);
        };
    }
}
