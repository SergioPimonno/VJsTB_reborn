package com.vjstb.ledscheme.settings;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;

/**
 * Локальное хранение пользовательских настроек (персонализация, профили) —
 * отдельный файл рядом с workspace.json, т.к. это настройки рабочего места,
 * а не данные проекта.
 */
public class SettingsStore {

    private final ObjectMapper mapper;
    private final File file;

    public SettingsStore() {
        this(defaultSettingsFile());
    }

    public SettingsStore(File file) {
        this.file = file;
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public static File defaultSettingsFile() {
        String home = System.getProperty("user.home", ".");
        File dir = new File(home, ".led-scheme");
        return new File(dir, "settings.json");
    }

    public AppSettings load() {
        if (!file.exists()) {
            return withDefaultProfile(new AppSettings());
        }
        try {
            AppSettings s = mapper.readValue(file, AppSettings.class);
            if (s.getProfiles() == null || s.getProfiles().isEmpty()) {
                return withDefaultProfile(new AppSettings());
            }
            return s;
        } catch (IOException e) {
            return withDefaultProfile(new AppSettings());
        }
    }

    private AppSettings withDefaultProfile(AppSettings s) {
        UserProfile p = new UserProfile();
        p.setName("По умолчанию");
        s.getProfiles().add(p);
        s.setActiveProfileId(p.getId());
        return s;
    }

    public void save(AppSettings settings) {
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                throw new IOException("не удалось создать каталог " + dir);
            }
            mapper.writeValue(file, settings);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить настройки в " + file + ": " + e.getMessage(), e);
        }
    }
}
