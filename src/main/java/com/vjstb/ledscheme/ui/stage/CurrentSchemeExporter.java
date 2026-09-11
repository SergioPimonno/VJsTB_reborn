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

    /**
     * Как {@link #export}, но PNG вместо JPEG (без сжатия с потерями — для
     * скриншотоподобного содержимого вроде таблиц/текста, где артефакты JPEG на
     * резких границах глифов и линий были бы заметнее, чем на фотографичной схеме
     * расключения) и без DPI-метаданных (у {@link SchemeRenderer#writeJpeg} они
     * нужны, т.к. JFIF без них печатается 72dpi "крупно"; писатель PNG в этом
     * проекте пока не пишет pHYs-чанк — картинка просто получается более чёткой
     * при том же физическом размере, что для этого экспорта и нужно). Та же
     * стартовая папка и настройка качества (DPI влияет на РАЗМЕР в пикселях, см.
     * {@code dpiScale} у {@link Renderer#render}), тот же диалог "готово/открыть
     * папку" — специально ЗЕРКАЛИТ {@link #export}, а не переиспользует его целиком,
     * чтобы не менять поведение/сигнатуру уже протестированного JPEG-пути.
     */
    public static void exportPng(Component parent, com.vjstb.ledscheme.service.AppModel model,
            SettingsManager settings, String suggestedName, Renderer renderer) {
        Project project = model.getCurrentProject();
        if (project == null) {
            JOptionPane.showMessageDialog(parent, "Сначала выберите проект", "Нет проекта",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int dpi = settings.activeProfile().getDocExportDpi();
        double dpiScale = dpi / 72.0;

        File suggested = suggestedFile(project, settings, suggestedName, "png");
        JFileChooser fc = new JFileChooser(suggested.getParentFile());
        fc.setDialogTitle("Сохранить как…");
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setSelectedFile(suggested);
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = withExtension(fc.getSelectedFile(), "png");

        try {
            BufferedImage img = renderer.render(dpiScale);
            javax.imageio.ImageIO.write(img, "png", target);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Не удалось сохранить файл: " + ex.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int answer = JOptionPane.showConfirmDialog(parent,
                "Файл сохранён:\n" + target.getAbsolutePath() + "\n\nОткрыть папку?",
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
        return suggestedFile(project, settings, suggestedName, "jpg");
    }

    /** Как выше, но с произвольным расширением — нужен {@link #exportPng}. */
    static File suggestedFile(Project project, SettingsManager settings, String suggestedName, String ext) {
        File dir = OutputPaths.defaultFolder(project, null, settings);
        return new File(dir, OutputPaths.sanitize(suggestedName) + "." + ext);
    }

    /** Гарантирует расширение {@code .jpg} — пользователь мог стереть его в поле
     *  имени диалога, а {@link SchemeRenderer#writeJpeg} пишет именно JPEG. */
    static File withJpegExtension(File chosen) {
        String lower = chosen.getName().toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return chosen;
        }
        return withExtension(chosen, "jpg");
    }

    /** Как выше, но с произвольным расширением (без синонимов вроде jpg/jpeg) —
     *  нужен {@link #exportPng}. */
    static File withExtension(File chosen, String ext) {
        String lower = chosen.getName().toLowerCase();
        if (lower.endsWith("." + ext)) {
            return chosen;
        }
        return new File(chosen.getParentFile(), chosen.getName() + "." + ext);
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
