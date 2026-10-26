package com.vjstb.ledscheme.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Вход сохраняется между запусками (запрос 2026-10-27): токен лежит в файле только зашифрованным, а просроченный
 *  токен тихо обновляется по запомненным учётным данным. Сеть заменена {@link SessionKeeper.Login}. */
class SessionKeeperTest {

    private static String jwt(Instant exp) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(("{\"sub\":\"u\",\"exp\":" + exp.getEpochSecond() + "}")
                .getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".sig";
    }

    private static SettingsManager manager(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void readsExpiryFromTheTokenAndRefreshesBeforeItEnds() {
        Instant now = Instant.parse("2026-10-27T12:00:00Z");
        assertTrue(SessionKeeper.needsRefresh(jwt(now.minusSeconds(60)), now), "истёк");
        assertTrue(SessionKeeper.needsRefresh(jwt(now.plusSeconds(300)), now), "истекает через 5 минут");
        assertFalse(SessionKeeper.needsRefresh(jwt(now.plusSeconds(3600)), now), "ещё час");
        assertFalse(SessionKeeper.needsRefresh("not-a-jwt", now), "неразборный токен решает сервер");
        assertFalse(SessionKeeper.needsRefresh(null, now));
    }

    @Test
    void sessionSurvivesARestartAndTheTokenIsNotInThePlainFile(@TempDir Path dir) throws IOException {
        SettingsManager first = manager(dir);
        String token = jwt(Instant.now().plusSeconds(3600));
        first.setAuthSession(token, "anna", "USER", "Team A");

        SettingsManager second = manager(dir);
        assertEquals(token, second.getSettings().getAuthToken());
        assertEquals("anna", second.getSettings().getAuthUsername());
        assertEquals("USER", second.getSettings().getAuthRole());
        assertEquals("Team A", second.getSettings().getAuthTeamName());

        String file = Files.readString(dir.resolve("settings.json"));
        assertFalse(file.contains(token), "токен в файле только зашифрованным");
        assertNotNull(second.getSettings().getAuthTokenEnc());

        second.clearAuthSession();
        SettingsManager third = manager(dir);
        assertNull(third.getSettings().getAuthToken());
        assertNull(third.getSettings().getAuthUsername());
    }

    @Test
    void anExpiredSessionLogsInAgainWithTheRememberedCredentials(@TempDir Path dir) {
        SettingsManager settings = manager(dir);
        Instant now = Instant.parse("2026-10-27T12:00:00Z");
        settings.setAuthSession(jwt(now.minusSeconds(10)), "anna", "USER", "Team A");
        settings.rememberCredentials("anna", "secret");
        String fresh = jwt(now.plusSeconds(43200));

        AtomicInteger calls = new AtomicInteger();
        SessionKeeper.ensureFresh(settings, now, (user, password) -> {
            calls.incrementAndGet();
            assertEquals("anna", user);
            assertEquals("secret", password);
            return new AuthClient.AuthResult(fresh, "USER", "Team B");
        });

        assertEquals(1, calls.get());
        assertEquals(fresh, settings.getSettings().getAuthToken());
        assertEquals("Team B", settings.getSettings().getAuthTeamName());
    }

    @Test
    void aValidSessionIsLeftAlone(@TempDir Path dir) {
        SettingsManager settings = manager(dir);
        Instant now = Instant.parse("2026-10-27T12:00:00Z");
        String token = jwt(now.plusSeconds(3600));
        settings.setAuthSession(token, "anna", "USER", null);
        settings.rememberCredentials("anna", "secret");

        SessionKeeper.ensureFresh(settings, now, (user, password) -> {
            throw new AssertionError("повторный вход не нужен");
        });
        assertEquals(token, settings.getSettings().getAuthToken());
    }

    @Test
    void withoutRememberedCredentialsAnExpiredSessionIsDropped(@TempDir Path dir) {
        SettingsManager settings = manager(dir);
        Instant now = Instant.parse("2026-10-27T12:00:00Z");
        settings.setAuthSession(jwt(now.minusSeconds(10)), "anna", "USER", null);

        SessionKeeper.ensureFresh(settings, now, (user, password) -> {
            throw new AssertionError("учётных данных нет — входить нечем");
        });
        assertNull(settings.getSettings().getAuthToken());
        assertNull(settings.getSettings().getAuthTokenEnc());
    }

    @Test
    void offlineKeepsTheSessionAndARejectedPasswordDropsIt(@TempDir Path dir) {
        SettingsManager settings = manager(dir);
        Instant now = Instant.parse("2026-10-27T12:00:00Z");
        String expired = jwt(now.minusSeconds(10));
        settings.setAuthSession(expired, "anna", "USER", null);
        settings.rememberCredentials("anna", "secret");

        SessionKeeper.ensureFresh(settings, now, (user, password) -> {
            throw new IOException("нет сети");
        });
        assertEquals(expired, settings.getSettings().getAuthToken(), "сети нет — повторим позже");

        SessionKeeper.ensureFresh(settings, now, (user, password) -> {
            throw new AuthClient.AuthException(401, "Неверный пароль");
        });
        assertNull(settings.getSettings().getAuthToken(), "пароль отвергнут — сессия сброшена");
    }
}
