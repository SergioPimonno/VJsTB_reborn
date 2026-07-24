package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import java.io.File;

/**
 * Папка вывода по умолчанию для экспортов (маски, пресеты, пакет документации) —
 * ~/Documents/Video/{проект}/{сцена}, создаётся автоматически при первом обращении,
 * так что пользователю не нужно каждый раз вручную выбирать папку перед экспортом.
 * Явный выбор через «Выбрать папку…» по-прежнему имеет приоритет над этим значением.
 */
public final class OutputPaths {

    private OutputPaths() {
    }

    public static String sanitize(String name) {
        String s = name == null ? "" : name.trim();
        s = s.replaceAll("[\\\\/:*?\"<>|]+", "_").replaceAll("\\s+", " ");
        return s.isEmpty() ? "без имени" : s;
    }

    /** ~/Documents/Video/{проект}/{сцена}; scene == null — без последнего уровня
     *  (для экспортов, не привязанных к одной сцене, например пакета документации
     *  всего проекта). Недостающие папки создаются немедленно. */
    public static File defaultFolder(Project project, Scene scene) {
        File dir = new File(System.getProperty("user.home"),
                "Documents" + File.separator + "Video" + File.separator + sanitize(project.getName()));
        if (scene != null) {
            dir = new File(dir, sanitize(scene.getName()));
        }
        dir.mkdirs();
        return dir;
    }
}
