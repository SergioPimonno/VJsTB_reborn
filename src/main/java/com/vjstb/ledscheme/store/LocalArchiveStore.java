package com.vjstb.ledscheme.store;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vjstb.ledscheme.model.Project;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Локальный архив проектов (см. {@code ui.LocalArchiveDialog}) — отдельная папка на
 * диске, путь к которой пользователь задаёт один раз в Настройках ({@code
 * SettingsManager#getArchiveFolder}). Проект в архиве хранится ОДНИМ JSON-файлом
 * {@code <id проекта>.json} и не участвует ни в одном {@code AppModel.changed()}/
 * автосохранении workspace.json — контроль версий/автосохранение для архива
 * сознательно не ведутся (аналог "облачных проектов", но без сервера и без истории
 * ревизий): чтобы внести правки, проект извлекается обратно в рабочий список
 * ({@code AppModel#restoreProjectFromArchive}), редактируется как обычно, и затем
 * снова убирается в архив ({@code AppModel#removeProjectForArchive}).
 */
public class LocalArchiveStore {

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** Список заархивированных проектов — повреждённый/чужой файл в папке архива
     *  молча пропускается, а не роняет список целиком (папку мог тронуть руками
     *  сам пользователь, например скопировать туда посторонний .json). */
    public List<Project> list(File dir) {
        List<Project> result = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) {
            return result;
        }
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".json"));
        if (files == null) {
            return result;
        }
        for (File f : files) {
            try {
                result.add(mapper.readValue(f, Project.class));
            } catch (IOException e) {
                // не критично для списка -- пропускаем нечитаемый файл, остальные показываем
            }
        }
        result.sort(Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /** Записывает проект в архив — вызывающая сторона должна убедиться, что запись
     *  прошла успешно, ДО того как убирать проект из рабочего списка (см.
     *  {@code LocalArchiveDialog#archiveSelected}), иначе при сбое записи проект
     *  бесследно исчез бы и из рабочего списка, и из архива. */
    public void save(File dir, Project project) {
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("не удалось создать каталог " + dir);
            }
            mapper.writeValue(fileFor(dir, project.getId()), project);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось поместить проект в архив: " + e.getMessage(), e);
        }
    }

    /** Удаляет файл архива — вызывается и при окончательном удалении из архива, и
     *  при извлечении обратно в рабочий список (в последнем случае проект продолжает
     *  жить, просто уже не в архиве, а в {@code workspace.json}). */
    public void delete(File dir, String projectId) {
        File f = fileFor(dir, projectId);
        if (f.exists() && !f.delete()) {
            throw new RuntimeException("Не удалось удалить файл архива: " + f);
        }
    }

    private File fileFor(File dir, String projectId) {
        return new File(dir, projectId + ".json");
    }
}
