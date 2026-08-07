package com.vjstb.ledscheme.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.Library;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.AppModel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Разделение библиотеки и воркспейса на два файла (см. AppModel/LibraryStore) —
 *  в частности одноразовая миграция старых workspace.json (сохранённых ДО
 *  разделения, где библиотечные списки лежали прямо в корне вместе с проектами). */
class LibraryStoreTest {

    /** Пишет legacy-формат workspace.json ДО разделения — библиотечные поля лежат
     *  прямо в корне JSON рядом с "projects", как их сериализовывал старый
     *  Workspace.class. Специально не переиспользует WorkspaceStore/Workspace,
     *  чтобы тест не сломался вместе с текущим кодом при последующих правках. */
    private File writeLegacyWorkspaceJson(Path dir, CabinetType cabinet, ControllerType controller) throws IOException {
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("cabinetTypes", List.of(cabinet));
        legacy.put("controllerTypes", List.of(controller));
        legacy.put("equipmentPresets", List.of());
        legacy.put("cableTypes", List.of());
        legacy.put("interfaceTypes", List.of());
        legacy.put("customEquipmentCategories", List.of("Свет"));
        legacy.put("equipmentCategoryLabelOverrides", Map.of("SOURCE", "Источники"));
        legacy.put("projects", List.of());
        File file = new File(dir.toFile(), "workspace.json");
        new ObjectMapper().writeValue(file, legacy);
        return file;
    }

    @Test
    void migratesLegacyWorkspaceJsonIntoLibraryJson(@TempDir Path dir) throws IOException {
        CabinetType cabinet = new CabinetType();
        cabinet.setName("Legacy Cabinet");
        ControllerType controller = new ControllerType();
        controller.setName("Legacy Controller");
        File legacyWorkspace = writeLegacyWorkspaceJson(dir, cabinet, controller);

        File libraryFile = new File(dir.toFile(), "library.json");
        LibraryStore libraryStore = new LibraryStore(libraryFile);
        Library migrated = libraryStore.loadOrMigrate(legacyWorkspace);

        assertEquals(1, migrated.getCabinetTypes().size());
        assertEquals("Legacy Cabinet", migrated.getCabinetTypes().get(0).getName());
        assertEquals(1, migrated.getControllerTypes().size());
        assertEquals("Legacy Controller", migrated.getControllerTypes().get(0).getName());
        // customEquipmentCategories/equipmentCategoryLabelOverrides из старого
        // workspace.json больше НЕ мигрируются (Task #135/v2.0) — это теперь общая
        // справочная данные, приходящая только с сервера (см. LibraryStore).
        assertTrue(migrated.getServerCustomEquipmentCategoriesById().isEmpty());
        assertTrue(migrated.getServerEquipmentCategoryLabels().isEmpty());
        assertTrue(libraryFile.exists(), "миграция должна была создать library.json");

        // повторный вызов не должен снова читать legacy-файл — library.json уже есть
        Library reloaded = libraryStore.loadOrMigrate(new File(dir.toFile(), "not-a-real-file.json"));
        assertEquals(1, reloaded.getCabinetTypes().size());
    }

    @Test
    void freshInstallWithNoFilesYieldsEmptyLibrary(@TempDir Path dir) {
        LibraryStore libraryStore = new LibraryStore(new File(dir.toFile(), "library.json"));
        Library library = libraryStore.loadOrMigrate(new File(dir.toFile(), "workspace.json"));
        assertTrue(library.getCabinetTypes().isEmpty());
        assertTrue(library.getControllerTypes().isEmpty());
    }

    @Test
    void appModelKeepsLibraryOutOfWorkspaceJson(@TempDir Path dir) throws IOException {
        File workspaceFile = new File(dir.toFile(), "workspace.json");
        AppModel model = new AppModel(new WorkspaceStore(workspaceFile));

        CabinetType type = new CabinetType();
        type.setName("Split Cabinet");
        model.addCabinetType(type);
        model.selectProject(model.addProject("P"));

        File libraryFile = new File(dir.toFile(), "library.json");
        assertTrue(libraryFile.exists(), "library.json должен появиться после первого изменения библиотеки");

        String rawWorkspaceJson = java.nio.file.Files.readString(workspaceFile.toPath());
        assertFalse(rawWorkspaceJson.contains("Split Cabinet"),
                "workspace.json не должен содержать библиотечные данные после разделения");

        Library rawLibrary = new ObjectMapper().readValue(libraryFile, Library.class);
        assertEquals(1, rawLibrary.getCabinetTypes().size());
        assertEquals("Split Cabinet", rawLibrary.getCabinetTypes().get(0).getName());
    }
}
