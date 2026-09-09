package com.vjstb.ledscheme.ui.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Кнопка «Экспорт схемы…» на этапах «Питание»/«Сигнал»: файл спрашивается каждый
 *  раз, но стартовая папка обязана совпадать с «общей папкой полного экспорта»
 *  проекта (той же, что подставляет этап «Вывод» для пакета документации —
 *  {@code OutputPaths.defaultFolder(project, null, settings)}), а не открываться
 *  в произвольном месте. Расширение {@code .jpg} проставляется принудительно —
 *  {@link com.vjstb.ledscheme.ui.SchemeRenderer#writeJpeg} пишет именно JPEG,
 *  даже если пользователь стёр расширение в поле имени. */
class CurrentSchemeExporterTest {

    private SettingsManager freshSettings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void suggestedFile_sitsInProjectExportRootWithSanitizedJpegName(@TempDir Path dir) {
        SettingsManager settings = freshSettings(dir);
        File customRoot = new File(dir.toFile(), "MyExports");
        settings.setExportRootFolder(customRoot.getAbsolutePath());
        Project project = new Project("Проект: тест");

        File suggested = CurrentSchemeExporter.suggestedFile(project, settings, "Экран A/1 Сила");

        assertEquals(new File(new File(customRoot, "Проект_ тест"), "Экран A_1 Сила.jpg"), suggested);
    }

    @Test
    void withJpegExtension_appendsJpgWhenMissing(@TempDir Path dir) {
        File chosen = new File(dir.toFile(), "схема");

        assertEquals(new File(dir.toFile(), "схема.jpg"), CurrentSchemeExporter.withJpegExtension(chosen));
    }

    @Test
    void withJpegExtension_keepsExistingJpgOrJpeg(@TempDir Path dir) {
        File jpg = new File(dir.toFile(), "схема.jpg");
        File jpeg = new File(dir.toFile(), "Схема.JPEG");

        assertEquals(jpg, CurrentSchemeExporter.withJpegExtension(jpg));
        assertEquals(jpeg, CurrentSchemeExporter.withJpegExtension(jpeg));
    }
}
