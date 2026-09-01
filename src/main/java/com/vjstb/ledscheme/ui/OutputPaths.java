package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.io.File;

/**
 * Папка вывода по умолчанию для экспортов (маски, пресеты, пакет документации) —
 * {@code <корень>/{проект}/{сцена}}, создаётся автоматически при первом обращении,
 * так что пользователю не нужно каждый раз вручную выбирать папку перед экспортом.
 * Явный выбор через «Выбрать папку…» по-прежнему имеет приоритет над этим значением.
 *
 * <p>Корень — {@link SettingsManager#activeProfile()}{@code
 * .getExportRootFolder()} (запрос пользователя: "добавить путь по умолчанию для
 * экспорта схем... в предпочтениях" — раньше был жёстко зашит как {@code
 * ~/Documents/Video}, теперь настраивается один раз в «Предпочтения → Генерация
 * масок», см. {@code ui.PreferencesDialog}), пусто/не задан — прежний
 * встроенный дефолт {@code ~/Documents/Video}. */
public final class OutputPaths {

    private static final String BUILTIN_DEFAULT_SUBPATH = "Documents" + File.separator + "Video";

    private OutputPaths() {
    }

    public static String sanitize(String name) {
        String s = name == null ? "" : name.trim();
        s = s.replaceAll("[\\\\/:*?\"<>|]+", "_").replaceAll("\\s+", " ");
        return s.isEmpty() ? "без имени" : s;
    }

    /** {@code <корень>/{проект}/{сцена}}; scene == null — без последнего уровня
     *  (для экспортов, не привязанных к одной сцене, например пакета документации
     *  всего проекта). Недостающие папки создаются немедленно. {@code settings}
     *  может быть {@code null} (нет активного профиля) — тогда используется
     *  встроенный дефолт, как и при пустом/незаданном {@code exportRootFolder}. */
    public static File defaultFolder(Project project, Scene scene, SettingsManager settings) {
        File dir = new File(resolveRoot(settings), sanitize(project.getName()));
        if (scene != null) {
            dir = new File(dir, sanitize(scene.getName()));
        }
        dir.mkdirs();
        return dir;
    }

    /** Package-private ради теста (см. {@code OutputPathsTest}) — не запускает
     *  {@code mkdirs()}, в отличие от {@link #defaultFolder}, поэтому тест
     *  builtin-дефолта не создаёт реальных папок в домашней директории машины,
     *  на которой гоняются тесты. */
    static File resolveRoot(SettingsManager settings) {
        String custom = settings != null ? settings.activeProfile().getExportRootFolder() : null;
        if (custom != null && !custom.isBlank()) {
            return new File(custom.trim());
        }
        return new File(System.getProperty("user.home"), BUILTIN_DEFAULT_SUBPATH);
    }
}
