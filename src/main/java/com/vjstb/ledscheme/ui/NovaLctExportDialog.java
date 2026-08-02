package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.NovaLctScrWriter;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Экспорт шаблона расключения экрана в бинарный формат NovaLCT (.scr) — выбор
 *  экрана текущей сцены + сохранение файла (см. {@link NovaLctScrWriter}). Как и
 *  импорт (см. {@link NovaLctImportDialog}), формат разобран лишь по нескольким
 *  контролируемым образцам, а не по документации производителя — прежде чем
 *  открыть результат в текущей сцене нужно явно предупредить пользователя. */
public final class NovaLctExportDialog {

    private NovaLctExportDialog() {
    }

    public static void showExportFlow(Frame owner, AppModel model) {
        Scene scene = model.getCurrentScene();
        List<Screen> screens = scene != null ? scene.getScreens() : List.of();
        if (screens.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "В текущей сцене нет ни одного экрана.",
                    "Экспорт шаблона NovaLCT", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JComboBox<Screen> combo = new JComboBox<>(screens.toArray(new Screen[0]));
        combo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                                                                     int index, boolean isSelected, boolean hasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
                if (value instanceof Screen s) {
                    setText(s.getName());
                }
                return this;
            }
        });
        Screen current = model.getCurrentScreen();
        if (current != null) {
            combo.setSelectedItem(current);
        }

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("Экран для экспорта шаблона:"), BorderLayout.NORTH);
        panel.add(combo, BorderLayout.CENTER);
        panel.add(new JLabel("<html><body style='width:320px'><b>Формат не документирован NovaStar</b> —"
                + " разобран дифференциальным анализом нескольких образцов, часть полей (контрольные суммы,"
                + " заголовок) заполняется нулями-заглушками. Файл ни разу не проверялся импортом в реальный"
                + " NovaLCT — протестируйте его сначала на тестовом контроллере, не на боевой инсталляции."
                + "</body></html>"), BorderLayout.SOUTH);

        int r = JOptionPane.showConfirmDialog(owner, panel, "Экспорт шаблона NovaLCT",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.OK_OPTION) {
            return;
        }
        Screen screen = (Screen) combo.getSelectedItem();
        if (screen == null) {
            return;
        }

        // Экран не ровная прямоугольная сетка (скрытые ячейки, свободное смещение
        // кабинета или кабинет другого физического размера) — NovaLctScrWriter
        // молча переключится на Complex-раскладку (см. class-javadoc): она разобрана
        // всего по ОДНОМУ образцу (в отличие от 6 образцов Standard-раскладки) и
        // заведомо рискованнее — предупреждаем явно, отдельно от общего дисклеймера
        // выше, а не полагаемся на то, что пользователь запомнил общую фразу.
        if (NovaLctScrWriter.isComplexExport(screen, model.getWorkspace())) {
            int rc = JOptionPane.showConfirmDialog(owner,
                    "У этого экрана неровная форма (скрытые ячейки, сдвинутый кабинет или кабинет"
                            + " другого физического размера) — экспорт пойдёт по формату NovaLCT «Complex Screen»."
                            + "\nЭтот путь разобран всего по ОДНОМУ образцу (в отличие от обычной прямоугольной"
                            + " сетки) — заголовок цепочки понят лишь частично, многопортовые/многоэкранные"
                            + " файлы такого вида вообще не проверялись."
                            + "\nПродолжить экспорт?",
                    "Экспорт шаблона NovaLCT — Complex Screen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (rc != JOptionPane.YES_OPTION) {
                return;
            }
        }

        if (NovaLctScrWriter.hasUnwiredCabinets(screen, scene, model.getWorkspace())) {
            int rr = JOptionPane.showConfirmDialog(owner,
                    "Экран расключён не полностью (сигнальные цепочки заведены не на все кабинеты).\n"
                            + "Экспортировать шаблон как есть?",
                    "Экспорт шаблона NovaLCT", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (rr != JOptionPane.YES_OPTION) {
                return;
            }
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Файлы прописи NovaLCT (*.scr)", "scr"));
        chooser.setSelectedFile(new File(safeFileName(screen.getName()) + ".scr"));
        if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".scr")) {
            target = new File(target.getParentFile(), target.getName() + ".scr");
        }
        try {
            byte[] data = NovaLctScrWriter.write(screen, scene, model.getWorkspace());
            Files.write(target.toPath(), data);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, "Не удалось сохранить файл:\n" + ex.getMessage(),
                    "Экспорт шаблона NovaLCT", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(owner, "Шаблон сохранён:\n" + target.getAbsolutePath(),
                "Экспорт шаблона NovaLCT", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String safeFileName(String name) {
        String s = name == null ? "screen" : name.trim();
        if (s.isEmpty()) {
            s = "screen";
        }
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
