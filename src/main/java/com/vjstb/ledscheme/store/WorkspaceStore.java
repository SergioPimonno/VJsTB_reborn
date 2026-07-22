package com.vjstb.ledscheme.store;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Workspace;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Локальное хранение рабочего пространства в JSON.
 * По умолчанию — файл в домашней папке пользователя (~/.led-scheme/workspace.json),
 * чтобы данные не зависели от каталога запуска.
 */
public class WorkspaceStore {

    private final ObjectMapper mapper;
    private final File workspaceFile;

    public WorkspaceStore() {
        this(defaultWorkspaceFile());
    }

    public WorkspaceStore(File workspaceFile) {
        this.workspaceFile = workspaceFile;
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public static File defaultWorkspaceFile() {
        String home = System.getProperty("user.home", ".");
        File dir = new File(home, ".led-scheme");
        return new File(dir, "workspace.json");
    }

    public File getWorkspaceFile() {
        return workspaceFile;
    }

    public Workspace load() {
        if (!workspaceFile.exists()) {
            return new Workspace();
        }
        try {
            return mapper.readValue(workspaceFile, Workspace.class);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось загрузить данные из " + workspaceFile + ": " + e.getMessage(), e);
        }
    }

    public void save(Workspace workspace) {
        try {
            File dir = workspaceFile.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                throw new IOException("не удалось создать каталог " + dir);
            }
            mapper.writeValue(workspaceFile, workspace);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить данные в " + workspaceFile + ": " + e.getMessage(), e);
        }
    }

    /** Экспорт библиотеки кабинетов в отдельный JSON-файл. */
    public void exportLibrary(List<CabinetType> cabinetTypes, File target) {
        try {
            mapper.writeValue(target, cabinetTypes);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось экспортировать библиотеку: " + e.getMessage(), e);
        }
    }

    /** Импорт библиотеки кабинетов из JSON-файла (массив кабинетов). */
    public List<CabinetType> importLibrary(File source) {
        try {
            return mapper.readValue(source,
                    mapper.getTypeFactory().constructCollectionType(List.class, CabinetType.class));
        } catch (IOException e) {
            throw new RuntimeException("Не удалось импортировать библиотеку: " + e.getMessage(), e);
        }
    }
}
