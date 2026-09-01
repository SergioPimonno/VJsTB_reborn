package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Тесты папки вывода по умолчанию (запрос пользователя: "добавить путь по
 *  умолчанию для экспорта схем... в предпочтениях" — см. class-javadoc {@link
 *  OutputPaths}). {@link OutputPaths#resolveRoot} тестируется НАПРЯМУЮ (не
 *  через {@link OutputPaths#defaultFolder}) для builtin-дефолта — та вызывает
 *  {@code mkdirs()}, реально создавая папки на диске, и не должна делать это
 *  в РЕАЛЬНОЙ домашней директории машины, на которой гоняются тесты. */
class OutputPathsTest {

    private SettingsManager freshSettings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void resolveRoot_fallsBackToBuiltInHomeVideoPathWhenNoOverride(@TempDir Path dir) {
        SettingsManager settings = freshSettings(dir);

        File root = OutputPaths.resolveRoot(settings);

        assertEquals(new File(System.getProperty("user.home"), "Documents" + File.separator + "Video"), root);
    }

    @Test
    void resolveRoot_fallsBackToBuiltInWhenOverrideIsBlank(@TempDir Path dir) {
        SettingsManager settings = freshSettings(dir);
        settings.setExportRootFolder("   ");

        File root = OutputPaths.resolveRoot(settings);

        assertEquals(new File(System.getProperty("user.home"), "Documents" + File.separator + "Video"), root);
    }

    @Test
    void resolveRoot_fallsBackToBuiltInWhenSettingsIsNull() {
        File root = OutputPaths.resolveRoot(null);

        assertEquals(new File(System.getProperty("user.home"), "Documents" + File.separator + "Video"), root);
    }

    @Test
    void resolveRoot_usesConfiguredExportRootWhenSet(@TempDir Path dir) {
        SettingsManager settings = freshSettings(dir);
        File customRoot = new File(dir.toFile(), "MyExports");
        settings.setExportRootFolder(customRoot.getAbsolutePath());

        File root = OutputPaths.resolveRoot(settings);

        assertEquals(customRoot, root);
    }

    @Test
    void defaultFolder_nestsProjectAndSceneUnderConfiguredRoot(@TempDir Path dir) {
        SettingsManager settings = freshSettings(dir);
        File customRoot = new File(dir.toFile(), "MyExports");
        settings.setExportRootFolder(customRoot.getAbsolutePath());
        Project project = new Project("Проект: тест");
        Scene scene = new Scene("Сцена 1");

        File folder = OutputPaths.defaultFolder(project, scene, settings);

        assertEquals(new File(customRoot, "Проект_ тест" + File.separator + "Сцена 1"), folder);
        assertTrue(folder.isDirectory(), "папка должна быть создана автоматически");
    }

    @Test
    void defaultFolder_omitsSceneLevelWhenSceneIsNull(@TempDir Path dir) {
        SettingsManager settings = freshSettings(dir);
        File customRoot = new File(dir.toFile(), "MyExports");
        settings.setExportRootFolder(customRoot.getAbsolutePath());
        Project project = new Project("Проект");

        File folder = OutputPaths.defaultFolder(project, null, settings);

        assertEquals(new File(customRoot, "Проект"), folder);
    }
}
