package com.vjstb.ledscheme.sync;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Логин/регистрация на сервере (см. {@code AuthController} на сервере) — нужны
 * только для отправки предложений в общую библиотеку (см. {@link ProposalClient}),
 * само чтение библиотеки (см. {@link LibrarySyncClient}) анонимно. Стиль — как
 * {@link LibrarySyncClient}: голый {@link HttpClient}, DTO как record.
 */
public class AuthClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public record AuthResult(String token, String role) {
    }

    /** Сервер (после Части 1 этого шага) кладёт человеко-читаемую причину отказа в
     *  поле {@code message} тела ответа (409 "имя занято", 401 "неверный пароль" и
     *  т.д.) — этот текст и есть {@link #getMessage()}, показываем как есть в UI. */
    public static class AuthException extends IOException {
        private final int statusCode;

        AuthException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }

    public AuthResult login(String username, String password) throws IOException, InterruptedException {
        return call("/api/auth/login", username, password);
    }

    public AuthResult register(String username, String password) throws IOException, InterruptedException {
        return call("/api/auth/register", username, password);
    }

    private AuthResult call(String path, String username, String password) throws IOException, InterruptedException {
        String body = MAPPER.writeValueAsString(Map.of("username", username, "password", password));
        HttpClient client = TrustedHttp.client();
        HttpRequest request = HttpRequest.newBuilder(URI.create(LibrarySyncClient.DEFAULT_BASE_URL + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new AuthException(response.statusCode(), extractMessage(response.body()));
        }
        return parseSuccess(response.body());
    }

    /** Вынесено отдельно от {@link #call} ради тестируемости без реальной сети. */
    static AuthResult parseSuccess(String json) throws IOException {
        return MAPPER.readValue(json, AuthResult.class);
    }

    static String extractMessage(String errorBody) {
        try {
            Map<?, ?> parsed = MAPPER.readValue(errorBody, Map.class);
            Object message = parsed.get("message");
            if (message != null && !message.toString().isBlank()) {
                return message.toString();
            }
            Object error = parsed.get("error");
            return error != null ? error.toString() : "неизвестная ошибка";
        } catch (Exception malformedErrorBody) {
            return "неизвестная ошибка";
        }
    }
}
