package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaSheet;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.UserProfile;
import com.vjstb.ledscheme.ui.OutputPaths;
import com.vjstb.ledscheme.ui.SchemeImageWriter;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

/**
 * Экспорт ОДНОЙ открытой сейчас схемы (расключение экрана / обзор сцены / общая
 * схема площадки / таблица экранов / легенда портов) в файл — вызывается кнопками
 * «Экспорт схемы…» и «Экспорт экранов» на этапах «Настройка», «Питание» и «Сигнал».
 *
 * <p>В отличие от этапа «Вывод» (пакет документации целиком) здесь:
 * <ul>
 *   <li>папка НЕ запоминается — файл спрашивается при каждом нажатии
 *       ({@link JFileChooser#showSaveDialog});</li>
 *   <li>стартовая папка диалога — та же «общая папка полного экспорта» проекта,
 *       что и у пакета документации ({@link OutputPaths#defaultFolder}
 *       с {@code scene == null});</li>
 *   <li>формат, качество (DPI) и сжатие берутся из тех же «Параметров экспорта», что и пакет
 *       документации ({@code UserProfile#getDocExportFormat}/{@code getDocExportDpi}/{@code
 *       getDocExportQuality}, запись — {@link SchemeImageWriter#write}). Раньше схема всегда
 *       уходила в JPEG, а таблица экранов и легенда портов — в PNG, независимо от выбранного
 *       формата (запрос 2026-10-05: «используем общие параметры вывода»).</li>
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
     * Имя файла по умолчанию для кнопки «Экспорт схемы…» в виде общей схемы:
     * «&lt;Сцена&gt; &lt;Схема&gt;» (запрос пользователя 2026-09-30, несколько схем на
     * сцену, docs/masks-and-schema-sheets/PLAN.md, трек C2). Раньше было
     * «&lt;Сцена&gt; Сила/Сигнал» — теперь на режим приходится несколько схем, и режим
     * уже содержится в названии схемы по умолчанию («Схема питания»); пользовательское
     * название точнее различает файлы, чем общий суффикс.
     */
    public static String currentSchemaFileName(String sceneName, String sheetName) {
        String scene = sceneName == null || sceneName.isBlank() ? "Схема" : sceneName.trim();
        String sheet = sheetName == null ? "" : sheetName.trim();
        return sheet.isEmpty() ? scene : scene + " " + sheet;
    }

    /**
     * Базовые имена файлов общих схем одного режима для пакета документации — по
     * одному на схему из {@code sheets}, в том же порядке, УЖЕ прогнанные через
     * {@link OutputPaths#sanitize} и уникальные внутри режима.
     *
     * <p>Единственная схема режима называется как раньше —
     * «&lt;Сцена&gt; Сила»/«&lt;Сцена&gt; Сигнал» (так не меняются имена файлов у всех, кто
     * схем не плодил, и сохраняются ссылки на них в чужих документах); при нескольких —
     * «&lt;Сцена&gt; Сила &lt;Схема&gt;»: каждая схема своим файлом с её именем (запрос 2026-09-30,
     * пункт 8). Названия, совпавшие после очистки недопустимых символов («A/B» и
     * «A:B» → «A_B») или отличающиеся лишь регистром (Windows считает имена файлов
     * регистронезависимыми), получают числовой суффикс — иначе вторая схема молча
     * перезаписала бы первую.
     */
    public static List<String> packageSchemaBaseNames(String sceneName, SchemaMode mode, List<SchemaSheet> sheets) {
        String suffix = mode == SchemaMode.POWER ? " Сила" : " Сигнал";
        List<String> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (SchemaSheet sheet : sheets) {
            String raw = sheets.size() <= 1
                    ? sceneName + suffix
                    : sceneName + suffix + " " + (sheet.getName() == null ? "" : sheet.getName());
            String base = OutputPaths.sanitize(raw);
            String candidate = base;
            for (int i = 2; !used.add(candidate.toLowerCase()); i++) {
                candidate = base + " " + i;
            }
            out.add(candidate);
        }
        return out;
    }

    /**
     * Спрашивает файл и сохраняет туда отрисованную {@code renderer}'ом схему в формате,
     * DPI и качестве из «Параметров экспорта» (JPG/PNG/WebP/PDF). Тихо выходит, если
     * проект ещё не выбран или пользователь отменил диалог.
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

        UserProfile profile = settings.activeProfile();
        int dpi = profile.getDocExportDpi();
        double dpiScale = dpi / 72.0;
        SchemeImageWriter.Format format = SchemeImageWriter.Format.fromId(profile.getDocExportFormat());

        File suggested = suggestedFile(project, settings, suggestedName, format.ext);
        JFileChooser fc = new JFileChooser(suggested.getParentFile());
        fc.setDialogTitle("Сохранить схему как…");
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setSelectedFile(suggested);
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = withExtension(fc.getSelectedFile(), format.ext);

        try {
            BufferedImage img = com.vjstb.ledscheme.ui.ExportRendering.run(profile, () -> renderer.render(dpiScale));
            SchemeImageWriter.write(img, target.getParentFile(), baseNameOf(target, format.ext), format, dpi,
                    profile.getDocExportQuality());
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
     *  проекта>/<sanitize(suggestedName)>.<ext>}. Стартовая папка — ровно та же, что
     *  {@code OutputStagePanel.resolveFolder()} подставляет для пакета документации
     *  (см. {@link OutputPaths#defaultFolder} с {@code scene == null}). */
    static File suggestedFile(Project project, SettingsManager settings, String suggestedName, String ext) {
        File dir = OutputPaths.defaultFolder(project, null, settings);
        return new File(dir, OutputPaths.sanitize(suggestedName) + "." + ext);
    }

    /** Гарантирует расширение выбранного формата — пользователь мог стереть его в поле имени
     *  диалога, а писатель формата кладёт файл именно с ним. */
    static File withExtension(File chosen, String ext) {
        String lower = chosen.getName().toLowerCase();
        if (lower.endsWith("." + ext)) {
            return chosen;
        }
        return new File(chosen.getParentFile(), chosen.getName() + "." + ext);
    }

    /** Имя файла без расширения {@code ext} — {@link SchemeImageWriter#write} дописывает его сам. */
    static String baseNameOf(File target, String ext) {
        String name = target.getName();
        return name.substring(0, name.length() - ext.length() - 1);
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
