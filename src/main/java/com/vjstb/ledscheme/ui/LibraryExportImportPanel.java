package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.LibraryXlsx;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Единый инструмент экспорта/импорта библиотек: выпадающий список типа
 * библиотеки (включая «Всё сразу») + кнопки «Экспорт»/«Импорт». Общий для
 * этапа «Библиотеки» и меню «Инструменты» (см. MainMenuBar) — чтобы список
 * типов и правила совпадения при импорте не расходились в двух разных местах.
 *
 * <p>Отдельно от JSON — импорт из Excel по шаблону ({@link LibraryXlsx}): кнопки
 * «Импорт из Excel…» и «Шаблон Excel…». Он не зависит от выбранного в списке типа
 * библиотеки — один файл содержит кабинеты, оборудование и кабели сразу.
 */
public class LibraryExportImportPanel extends JPanel {

    private enum Kind {
        CABINET("Кабинеты", "led-cabinet-library.json"),
        // Контроллеры больше не отдельный вид — слиты в EQUIPMENT (category ==
        // CONTROLLER, 2026-09-23), экспортируются/импортируются вместе с остальным
        // оборудованием; importAllLibraries/exportAllLibraries ("Всё сразу") по-прежнему
        // читает СТАРЫЕ файлы с отдельным controllerTypes (см. AppModel).
        EQUIPMENT("Оборудование (пресеты, вкл. контроллеры)", "led-equipment-presets.json"),
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

        JButton xlsxImportBtn = new JButton("Импорт из Excel…");
        xlsxImportBtn.setToolTipText("Загрузить кабинеты, оборудование и кабели из таблицы Excel по шаблону");
        xlsxImportBtn.addActionListener(e -> importFromExcel(model));
        JButton templateBtn = new JButton("Шаблон Excel…");
        templateBtn.setToolTipText("Сохранить пустой шаблон таблицы со списком оборудования для заполнения");
        templateBtn.addActionListener(e -> saveTemplate());

        add(new JLabel("Библиотека:"));
        add(kindBox);
        add(exportBtn);
        add(importBtn);
        add(xlsxImportBtn);
        add(templateBtn);
    }

    private void saveTemplate() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("led-library-template.xlsx"));
        fc.setFileFilter(new FileNameExtensionFilter("Excel (*.xlsx)", "xlsx"));
        if (fc.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = withXlsxExtension(fc.getSelectedFile());
        if (target.exists() && JOptionPane.showConfirmDialog(owner, "Файл «" + target.getName()
                + "» уже существует. Заменить?", "Шаблон Excel", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            LibraryXlsx.writeTemplate(target);
            JOptionPane.showMessageDialog(owner, "Шаблон сохранён: " + target.getAbsolutePath()
                    + "\nЗаполните листы и загрузите файл кнопкой «Импорт из Excel…».", "Шаблон Excel",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(owner, "Не удалось сохранить шаблон: " + ex.getMessage(), "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importFromExcel(AppModel model) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Excel (*.xlsx, *.xls)", "xlsx", "xls"));
        if (fc.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            LibraryXlsx.Report report = model.importLibraryXlsx(fc.getSelectedFile());
            showReport(owner, report);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(owner, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Итог импорта: сводка по видам и, если что-то пропущено или применено иначе, прокручиваемый список предупреждений. */
    static void showReport(Window owner, LibraryXlsx.Report report) {
        String head = report.total() == 0 ? "Ничего не импортировано." : report.summary();
        List<String> warnings = report.warnings();
        if (warnings.isEmpty()) {
            JOptionPane.showMessageDialog(owner, head, "Импорт из Excel", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JTextArea area = new JTextArea(head + "\n\nПредупреждения (" + warnings.size() + "):\n• "
                + String.join("\n• ", warnings));
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(640, 320));
        JOptionPane.showMessageDialog(owner, scroll, "Импорт из Excel", report.total() == 0
                ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
    }

    private static File withXlsxExtension(File f) {
        return f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")
                ? f : new File(f.getParentFile(), f.getName() + ".xlsx");
    }

    private static void exportKind(AppModel model, Kind kind, File file) {
        switch (kind) {
            case CABINET -> model.exportCabinetLibrary(file);
            case EQUIPMENT -> model.exportEquipmentPresetLibrary(file);
            case CABLE -> model.exportCableLibrary(file);
            case ALL -> model.exportAllLibraries(file);
        }
    }

    private static int importKind(AppModel model, Kind kind, File file) {
        return switch (kind) {
            case CABINET -> model.importCabinetLibrary(file);
            case EQUIPMENT -> model.importEquipmentPresetLibrary(file);
            case CABLE -> model.importCableLibrary(file);
            case ALL -> model.importAllLibraries(file);
        };
    }
}
