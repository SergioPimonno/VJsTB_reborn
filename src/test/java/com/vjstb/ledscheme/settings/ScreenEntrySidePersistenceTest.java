package com.vjstb.ledscheme.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.SchemaMode;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Баг-репорт 2026-10-03: «вход связей в блоки экранов» не сохранялся в предпочтениях при
 *  перезапуске. Причина — у полей {@code schemaScreenEntrySideSignal/Power} были только методы с
 *  параметром {@link SchemaMode}, а Jackson пишет в settings.json лишь свойства с геттером/сеттером
 *  без параметров. Второй тест страхует от того же в любом другом поле профиля. */
class ScreenEntrySidePersistenceTest {

    private SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void entrySideSurvivesRestartSeparatelyForSignalAndPower(@TempDir Path dir) {
        SettingsManager s = settings(dir);
        assertEquals(ScreenEntrySide.BOTTOM, s.activeProfile().getSchemaScreenEntrySide(SchemaMode.SIGNAL));

        s.setSchemaScreenEntrySide(SchemaMode.SIGNAL, ScreenEntrySide.TOP);
        s.setSchemaScreenEntrySide(SchemaMode.POWER, ScreenEntrySide.NEAREST);

        SettingsManager reloaded = settings(dir);
        assertEquals(ScreenEntrySide.TOP, reloaded.activeProfile().getSchemaScreenEntrySide(SchemaMode.SIGNAL));
        assertEquals(ScreenEntrySide.NEAREST, reloaded.activeProfile().getSchemaScreenEntrySide(SchemaMode.POWER));
    }

    @Test
    void everyProfileFieldIsWrittenToJson() throws Exception {
        JsonNode json = new ObjectMapper().valueToTree(new UserProfile());
        List<String> missing = new ArrayList<>();
        for (Field f : UserProfile.class.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) || Modifier.isTransient(m) || f.isSynthetic()) {
                continue;
            }
            if (!json.has(f.getName())) {
                missing.add(f.getName());
            }
        }
        assertTrue(missing.isEmpty(), "поля профиля, которые не попадают в settings.json: " + missing);
    }
}
