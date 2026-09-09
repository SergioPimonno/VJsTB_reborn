package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.ui.OutputPaths;
import com.vjstb.ledscheme.ui.SchemeRenderer;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

/**
 * Экспорт ОДНОЙ открытой сейчас схемы (расключение экрана / обзор сцены / общая
 * схема площадки) в JPEG — вызывается кнопкой «Экспорт схемы…» на этапах
 * «Питание» и «Сигнал».
 *
 * <p>В отличие от этапа «Вывод» (пакет документации целиком) здесь:
 * <ul>
 *   <li>папка НЕ запоминается — файл спрашивается при каждом нажатии
 *       ({@link JFileChooser#showSaveDialog});</li>
 *   <li>стартовая папка диалога — та же «общая папка полного экспорта» проекта,
 *       что и у пакета документации ({@link OutputPaths#defaultFolder}
 *       с {@code scene == null});</li>
 *   <li>качество (DPI) берётся из той же настройки «Качество экспортируемых
 *       схем», что и пакет документации ({@code UserProfile#getDocExportDpi}).</li>
 * </ul>
 */
public final class CurrentSchemeExporter {

    /** Отрисовка схемы в изображение с заданным множителем качества
     *  ({@code dpiScale == dpi / 72.0}, см. {@code SchemeRenderer#renderImage}). */
    public interface Renderer {
        BufferedImage render(double dpiScale) throws Exception;
    }

    private CurrentSchemeExporter() {
    }

    /**
     * Спрашивает файл и сохраняет туда отрисованную {@code renderer}'ом схему.
     * Тихо выходит, если проект ещё не выбран или пользователь отменил диалог.
     *
     * @param suggestedName имя файла по умолчанию (без расширения) — прогоняется
     *                      через {@link OutputPaths#sanitize}
     */
    public static void export(Component parent, com.vjstb.ledscheme.service.AppModel model,
            SettingsManager settings, String suggestedName, Renderer renderer) {
        Project project = model.getCurrentProject();
        if (project == null) {
            JOptionPane.showMessageDialog(parent, "Сначала выберите проект", "Нет проекта",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int dpi = settings.activeProfile().getDocExportDpi();
        double dpiScale = dpi / 72.0;

        File suggested = suggestedFile(project, settings, suggestedName);
        JFileChooser fc = new JFileChooser(suggested.getParentFile());
        fc.setDialogTitle("Сохранить схему как…");
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setSelectedFile(suggested);
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = withJpegExtension(fc.getSelectedFile());

        try {
            BufferedImage img = renderer.render(dpiScale);
            SchemeRenderer.writeJpeg(img, target, dpi);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Не удалось сохранить схему: " + ex.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int answer = JOptionPane.showConfirmDialog(parent,
                "Схема сохранена:\n" + target.getAbsolutePath() + "\n\nОткрыть папку?",
                "Готово", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            openFolder(target.getParentFile());
        }
    }

    /** Файл по умолчанию в диалоге сохранения: {@code <общая папка полного экспорта
     *  проекта>/<sanitize(suggestedName)>.jpg}. Стартовая папка — ровно та же, что
     *  {@code OutputStagePanel.resolveFolder()} подставляет для пакета документации
     *  (см. {@link OutputPaths#defaultFolder} с {@code scene == null}). */
    static File suggestedFile(Project project, SettingsManager settings, String suggestedName) {
        File dir = OutputPaths.defaultFolder(project, null, settings);
        return new File(dir, OutputPaths.sanitize(suggestedName) + ".jpg");
    }

    /** Гарантирует расширение {@code .jpg} — пользователь мог стереть его в поле
     *  имени диалога, а {@link SchemeRenderer#writeJpeg} пишет именно JPEG. */
    static File withJpegExtension(File chosen) {
        String lower = chosen.getName().toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return chosen;
        }
        return new File(chosen.getParentFile(), chosen.getName() + ".jpg");
    }

    private static void openFolder(File dir) {
        try {
            if (dir != null && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir);
            }
        } catch (Exception ignored) {
            // не критично
        }
    }
}
