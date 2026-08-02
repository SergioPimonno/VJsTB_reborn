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
            Window owner = SwingUtilities.getWindowAncestor(this);
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("JSON", "json"));
            if (fc.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File selected = fc.getSelectedFile();
            // Файл сам подсказывает свой тип (Task #5) — если распознали и он не
            // совпадает с выбором в комбобоксе, используем подсказку файла и
            // синхронизируем комбобокс, а не заставляем угадывать вручную (раньше
            // неверный ручной выбор тихо создавал мусорные записи, т.к. импорт не
            // проверял соответствие полей). Если подсказки нет (старый формат без
            // обёртки, либо «Всё сразу» — bundle-формат не маркируется так же) —
            // используем то, что выбрано в комбобоксе, как раньше.
            Kind kind = (Kind) kindBox.getSelectedItem();
            String detected = model.detectLibraryKind(selected);
            if (detected != null) {
                try {
                    Kind detectedKind = Kind.valueOf(detected);
                    if (detectedKind != kind) {
                        kind = detectedKind;
                        kindBox.setSelectedItem(detectedKind);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Неизвестная метка kind (например, из более новой версии) — остаёмся
                    // на ручном выборе, не падаем.
                }
            }
            try {
                int n = importKind(model, kind, selected);
                JOptionPane.showMessageDialog(owner, "Импортировано записей: " + n, "Импорт",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(owner, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
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
