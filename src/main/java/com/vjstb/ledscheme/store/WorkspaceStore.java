package com.vjstb.ledscheme.store;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vjstb.ledscheme.model.LibraryBundle;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
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
            Workspace workspace = mapper.readValue(workspaceFile, Workspace.class);
            migrateLegacyChains(workspace);
            return workspace;
        } catch (IOException e) {
            throw new RuntimeException("Не удалось загрузить данные из " + workspaceFile + ": " + e.getMessage(), e);
        }
    }

    /** Одноразовая миграция: в файлах проектов, сохранённых до Task #78, цепочки
     *  питания/сигнала хранились на уровне Screen, а не Scene. Переносим (не
     *  копируем) их наверх в список сцены и очищаем legacy-поля экрана — иначе они
     *  просто молча игнорировались бы всей текущей бизнес-логикой/отрисовкой,
     *  которая теперь читает только Scene.getPowerChains()/getSignalChains(). */
    private static void migrateLegacyChains(Workspace workspace) {
        for (Project project : workspace.getProjects()) {
            for (Scene scene : project.getScenes()) {
                for (Screen screen : scene.getScreens()) {
                    if (!screen.getPowerChains().isEmpty()) {
                        scene.getPowerChains().addAll(screen.getPowerChains());
                        screen.getPowerChains().clear();
                    }
                    if (!screen.getSignalChains().isEmpty()) {
                        scene.getSignalChains().addAll(screen.getSignalChains());
                        screen.getSignalChains().clear();
                    }
                }
            }
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

    /** Экспорт библиотеки ОДНОГО типа (кабинеты/контроллеры/пресеты/кабели) в JSON-файл —
     *  оборачивается в {"kind": kind, "items": [...]}, чтобы импорт мог сам определить
     *  тип файла и не полагаться на ручной выбор пользователя (Task #5/v1.6: неверно
     *  выбранный в комбобоксе тип раньше молча создавал мусорные записи, т.к.
     *  FAIL_ON_UNKNOWN_PROPERTIES выключен и парсинг просто игнорировал лишние поля). */
    public <T> void exportList(List<T> items, File target, String kind) {
        try {
            java.util.Map<String, Object> wrapper = new java.util.LinkedHashMap<>();
            wrapper.put("kind", kind);
            wrapper.put("items", items);
            mapper.writeValue(target, wrapper);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось экспортировать библиотеку: " + e.getMessage(), e);
        }
    }

    /** Тип библиотеки, которым файл сам себя маркирует (см. {@link #exportList}) —
     *  null, если файл в старом формате (голый массив без обёртки) или маркер не
     *  распознан; тогда вызывающий код должен вернуться к ручному выбору типа,
     *  как раньше. */
    public String detectKind(File source) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(source);
            if (root != null && root.isObject() && root.has("kind") && root.has("items")) {
                return root.get("kind").asText(null);
            }
        } catch (IOException e) {
            // Не критично для автоопределения — просто не подскажем тип, ручной выбор остаётся как раньше.
        }
        return null;
    }

    /** Импорт библиотеки ОДНОГО типа из JSON-файла — понимает и старый формат
     *  (голый массив элементов), и новый (обёртка {"kind","items"} из {@link #exportList}). */
    public <T> List<T> importList(File source, Class<T> type) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(source);
            com.fasterxml.jackson.databind.JsonNode itemsNode = root.isObject() && root.has("items")
                    ? root.get("items") : root;
            return mapper.convertValue(itemsNode, mapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (IOException e) {
            throw new RuntimeException("Не удалось импортировать библиотеку: " + e.getMessage(), e);
        }
    }

    /** Экспорт ВСЕХ библиотек разом («Экспорт всего») в один JSON-файл. */
    public void exportBundle(LibraryBundle bundle, File target) {
        try {
            mapper.writeValue(target, bundle);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось экспортировать библиотеки: " + e.getMessage(), e);
        }
    }

    /** Импорт файла, ранее сохранённого через {@link #exportBundle}. */
    public LibraryBundle importBundle(File source) {
        try {
            return mapper.readValue(source, LibraryBundle.class);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось импортировать библиотеки: " + e.getMessage(), e);
        }
    }
}
