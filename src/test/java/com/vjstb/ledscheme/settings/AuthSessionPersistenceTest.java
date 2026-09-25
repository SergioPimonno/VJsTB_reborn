package com.vjstb.ledscheme.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Запрос пользователя (2026-09-25): после перезапуска приложения нужно входить заново —
 *  сессия (токен) не пишется в settings.json, — но запомненные логин и пароль переживают
 *  перезапуск, а пароль не лежит в settings.json открытым текстом. */
class AuthSessionPersistenceTest {

    private static SettingsManager open(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void sessionIsNotRestoredAfterRestart(@TempDir Path dir) throws Exception {
        SettingsManager first = open(dir);
        first.setAuthSession("jwt-token", "alice", "USER", "Team");
        assertEquals("jwt-token", first.getSettings().getAuthToken(), "в текущем запуске сессия есть");

        String json = Files.readString(dir.resolve("settings.json"));
        assertFalse(json.contains("jwt-token"), "токен не пишется на диск");

        SettingsManager second = open(dir);
        assertNull(second.getSettings().getAuthToken());
        assertNull(second.getSettings().getAuthUsername());
    }

    @Test
    void legacyTokenInSettingsFileIsIgnored(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("settings.json"),
                "{\"profiles\":[{\"name\":\"p\"}],\"authToken\":\"old\",\"authUsername\":\"bob\"}");
        SettingsManager s = open(dir);
        assertNull(s.getSettings().getAuthToken());
        assertNull(s.getSettings().getAuthUsername());
    }

    @Test
    void rememberedCredentialsSurviveRestartEncrypted(@TempDir Path dir) throws Exception {
        open(dir).rememberCredentials("alice", "s3cret-пароль");

        String json = Files.readString(dir.resolve("settings.json"));
        assertFalse(json.contains("s3cret"), "пароль не лежит в настройках открытым текстом");

        SettingsManager restarted = open(dir);
        assertEquals("alice", restarted.getSettings().getRememberedUsername());
        assertEquals("s3cret-пароль", restarted.rememberedPassword());

        restarted.forgetCredentials();
        SettingsManager again = open(dir);
        assertNull(again.getSettings().getRememberedUsername());
        assertNull(again.rememberedPassword());
    }

    @Test
    void missingKeyFileYieldsNoPasswordInsteadOfCrash(@TempDir Path dir) throws Exception {
        open(dir).rememberCredentials("alice", "pw");
        Files.delete(dir.resolve("credential.key"));
        assertNull(open(dir).rememberedPassword());
    }
}
