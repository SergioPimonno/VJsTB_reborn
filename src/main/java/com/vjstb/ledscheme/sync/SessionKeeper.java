package com.vjstb.ledscheme.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.settings.AppSettings;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Держит сохранённую между запусками сессию входа живой (запрос 2026-10-27). Токен сервера действует 12 часов,
 * поэтому перед синхронизацией библиотеки смотрим на срок в самом токене: если он истёк (или вот-вот истечёт),
 * тихо входим заново по запомненным логину и паролю («Запомнить логин и пароль» в «Аккаунт…»). Сервер отказал
 * (пароль сменили) — сессия сбрасывается; сети нет — токен остаётся как есть, повторим при следующей синхронизации.
 * Без запомненных данных просроченная сессия просто сбрасывается: пользователь войдёт сам.
 */
public final class SessionKeeper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Обновляем заранее, чтобы токен не истёк посреди запроса. */
    static final Duration MARGIN = Duration.ofMinutes(10);

    /** Вход по логину и паролю; вынесено ради теста без сети. */
    @FunctionalInterface
    public interface Login {
        AuthClient.AuthResult login(String username, String password) throws IOException, InterruptedException;
    }

    private SessionKeeper() {
    }

    /** Срок действия токена по полю {@code exp} (секунды) или {@code null}, если это не разобрать. */
    static Instant expiresAt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            JsonNode exp = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1])).get("exp");
            return exp != null && exp.canConvertToLong() ? Instant.ofEpochSecond(exp.asLong()) : null;
        } catch (Exception notAJwt) {
            return null;
        }
    }

    /** Токен истёк или истечёт в ближайшие {@link #MARGIN}; неразборный токен считаем годным — решит сервер. */
    static boolean needsRefresh(String token, Instant now) {
        Instant exp = token == null ? null : expiresAt(token);
        return exp != null && !exp.isAfter(now.plus(MARGIN));
    }

    public static void ensureFresh(SettingsManager settings) {
        ensureFresh(settings, Instant.now(),
                new AuthClient(LibrarySyncClient.resolveBaseUrl(settings))::login);
    }

    static void ensureFresh(SettingsManager settings, Instant now, Login login) {
        AppSettings s = settings.getSettings();
        if (s.getAuthToken() == null || !needsRefresh(s.getAuthToken(), now)) {
            return;
        }
        String username = s.getRememberedUsername();
        String password = settings.rememberedPassword();
        if (username == null || password == null) {
            settings.clearAuthSession();
            return;
        }
        try {
            AuthClient.AuthResult result = login.login(username, password);
            settings.setAuthSession(result.token(), username, result.role(), result.teamName());
        } catch (AuthClient.AuthException rejected) {
            settings.clearAuthSession();
        } catch (IOException | InterruptedException offline) {
            if (offline instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // сети нет — оставляем как есть, попробуем при следующей синхронизации
        }
    }
}
