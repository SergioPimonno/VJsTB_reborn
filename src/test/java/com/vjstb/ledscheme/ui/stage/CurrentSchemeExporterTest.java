package com.vjstb.ledscheme.ui.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Кнопки «Экспорт схемы…»/«Экспорт экранов»/«Экспорт легенды портов…»: файл спрашивается каждый
 *  раз, но стартовая папка обязана совпадать с «общей папкой полного экспорта»
 *  проекта (той же, что подставляет этап «Вывод» для пакета документации —
 *  {@code OutputPaths.defaultFolder(project, null, settings)}), а не открываться
 *  в произвольном месте. Расширение — того формата, что выбран в «Параметрах экспорта»
 *  (запрос 2026-10-05: раньше схема всегда уходила в JPEG, таблица экранов и легенда — в PNG,
 *  формат из параметров игнорировался); оно проставляется принудительно, даже если
 *  пользователь стёр его в поле имени диалога. */
class CurrentSchemeExporterTest {

    private SettingsManager freshSettings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void suggestedFile_sitsInProjectExportRootWithSanitizedName(@TempDir Path dir) {
        SettingsManager settings = freshSettings(dir);
        File customRoot = new File(dir.toFile(), "MyExports");
        settings.setExportRootFolder(customRoot.getAbsolutePath());
        Project project = new Project("Проект: тест");

        File jpg = CurrentSchemeExporter.suggestedFile(project, settings, "Экран A/1 Сила", "jpg");
        File pdf = CurrentSchemeExporter.suggestedFile(project, settings, "Сцена — экраны", "pdf");

        assertEquals(new File(new File(customRoot, "Проект_ тест"), "Экран A_1 Сила.jpg"), jpg);
        assertEquals(new File(new File(customRoot, "Проект_ тест"), "Сцена — экраны.pdf"), pdf);
    }

    @Test
    void withExtension_appendsGivenExtensionWhenMissing(@TempDir Path dir) {
        File chosen = new File(dir.toFile(), "легенда");

        assertEquals(new File(dir.toFile(), "легенда.png"), CurrentSchemeExporter.withExtension(chosen, "png"));
        assertEquals(new File(dir.toFile(), "легенда.webp"), CurrentSchemeExporter.withExtension(chosen, "webp"));
    }

    @Test
    void withExtension_keepsExistingMatchingExtension(@TempDir Path dir) {
        File png = new File(dir.toFile(), "легенда.png");
        File upper = new File(dir.toFile(), "Схема.JPG");

        assertEquals(png, CurrentSchemeExporter.withExtension(png, "png"));
        assertEquals(upper, CurrentSchemeExporter.withExtension(upper, "jpg"));
    }

    @Test
    void baseNameOf_stripsTheFormatExtensionThatTheWriterAppendsItself(@TempDir Path dir) {
        assertEquals("Зал Схема питания",
                CurrentSchemeExporter.baseNameOf(new File(dir.toFile(), "Зал Схема питания.pdf"), "pdf"));
        assertEquals("a.b", CurrentSchemeExporter.baseNameOf(new File(dir.toFile(), "a.b.png"), "png"));
    }
}
