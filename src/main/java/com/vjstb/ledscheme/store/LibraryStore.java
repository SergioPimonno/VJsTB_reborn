package com.vjstb.ledscheme.store;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CableType;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.InterfaceType;
import com.vjstb.ledscheme.model.Library;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Локальное хранение общей библиотеки в JSON, отдельно от {@code WorkspaceStore}
 * (см. {@link Library}) — по умолчанию {@code ~/.led-scheme/library.json}.
 */
public class LibraryStore {

    private final ObjectMapper mapper;
    private final File libraryFile;

    public LibraryStore() {
        this(defaultLibraryFile());
    }

    public LibraryStore(File libraryFile) {
        this.libraryFile = libraryFile;
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public static File defaultLibraryFile() {
        String home = System.getProperty("user.home", ".");
        File dir = new File(home, ".led-scheme");
        return new File(dir, "library.json");
    }

    public File getLibraryFile() {
        return libraryFile;
    }

    public Library load() {
        if (!libraryFile.exists()) {
            return new Library();
        }
        try {
            return mapper.readValue(libraryFile, Library.class);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось загрузить библиотеку из " + libraryFile + ": " + e.getMessage(), e);
        }
    }

    public void save(Library library) {
        try {
            File dir = libraryFile.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                throw new IOException("не удалось создать каталог " + dir);
            }
            mapper.writeValue(libraryFile, library);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить библиотеку в " + libraryFile + ": " + e.getMessage(), e);
        }
    }

    /** Если {@code library.json} уже существует — обычная загрузка. Иначе, если
     *  найден СТАРЫЙ {@code workspace.json} (до разделения хранилищ библиотека и
     *  проекты жили в одном файле) — одноразово вытаскивает библиотечные поля из
     *  его сырого JSON по именам старых ключей и сохраняет как новый
     *  {@code library.json}; сам {@code workspace.json} при этом не трогается —
     *  он просто перестанет содержать эти поля при следующем обычном сохранении
     *  (Workspace.class их больше не объявляет, а FAIL_ON_UNKNOWN_PROPERTIES и так
     *  выключен, так что старые файлы и без миграции продолжают читаться). */
    public Library loadOrMigrate(File legacyWorkspaceFile) {
        if (libraryFile.exists()) {
            return load();
        }
        if (legacyWorkspaceFile != null && legacyWorkspaceFile.exists()) {
            Library migrated = extractFromLegacyWorkspace(legacyWorkspaceFile);
            save(migrated);
            return migrated;
        }
        return new Library();
    }

    private Library extractFromLegacyWorkspace(File legacyWorkspaceFile) {
        try {
            JsonNode root = mapper.readTree(legacyWorkspaceFile);
            Library library = new Library();
            library.setCabinetTypes(convertList(root, "cabinetTypes", CabinetType.class));
            library.setControllerTypes(convertList(root, "controllerTypes", ControllerType.class));
            library.setEquipmentPresets(convertList(root, "equipmentPresets", EquipmentPreset.class));
            library.setCableTypes(convertList(root, "cableTypes", CableType.class));
            library.setInterfaceTypes(convertList(root, "interfaceTypes", InterfaceType.class));
            // customEquipmentCategories/equipmentCategoryLabelOverrides из старого
            // workspace.json больше НЕ мигрируются (Task #135/v2.0) — это теперь
            // общая справочная данные, приходящая только с сервера (см.
            // Library.serverCustomEquipmentCategories/serverEquipmentCategoryLabels);
            // старое локальное значение конфликтовало бы с ней при первом же синке.
            return library;
        } catch (IOException e) {
            throw new RuntimeException("Не удалось мигрировать библиотеку из " + legacyWorkspaceFile + ": "
                    + e.getMessage(), e);
        }
    }

    private <T> List<T> convertList(JsonNode root, String field, Class<T> type) {
        if (!root.has(field)) {
            return new ArrayList<>();
        }
        CollectionType listType = mapper.getTypeFactory().constructCollectionType(List.class, type);
        return mapper.convertValue(root.get(field), listType);
    }

}
