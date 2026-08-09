package com.vjstb.ledscheme.sync;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Общее командное облачное хранилище проектов (см. {@code ProjectArchiveController}
 * на сервере) — видят и правят ВСЕ авторизованные пользователи, не только автор.
 * Обязательный контроль версий: {@link #update} требует {@code baseRevision} (та,
 * с которой начато редактирование) и бросает {@link ProjectConflictException},
 * если сервер уже принял более новую версию — тихой перезаписи не бывает, вызывающий
 * код (см. {@code CloudProjectsDialog}) обязан сначала предложить обновиться.
 * Требует токен (см. {@link AuthClient}), стиль — как {@link ProposalClient}.
 */
public class ProjectArchiveClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public record ProjectSummaryDto(String id, String name, String ownerUsername, int revision, String updatedAt) {
    }

    public record ProjectDto(String id, String name, String projectJson, int revision, String updatedAt) {
    }

    public record VersionSummaryDto(int revision, String savedByUsername, String savedAt) {
    }

    public record VersionDto(int revision, String projectJson, String savedByUsername, String savedAt) {
    }

    /** Сервер отклонил сохранение — {@code baseRevision} клиента отстала от
     *  {@code currentRevision} на сервере (кто-то уже сохранил новее). */
    public static class ProjectConflictException extends IOException {
        private final int currentRevision;

        ProjectConflictException(int currentRevision) {
            super("Проект уже изменён кем-то другим — текущая ревизия " + currentRevision);
            this.currentRevision = currentRevision;
        }

        public int currentRevision() {
            return currentRevision;
        }
    }

    public List<ProjectSummaryDto> list(String token) throws IOException, InterruptedException {
        HttpResponse<String> response = send(token, "GET", "/api/projects", null);
        checkOk(response, false);
        return MAPPER.readValue(response.body(),
                MAPPER.getTypeFactory().constructCollectionType(List.class, ProjectSummaryDto.class));
    }

    public ProjectDto get(String token, String id) throws IOException, InterruptedException {
        HttpResponse<String> response = send(token, "GET", "/api/projects/" + id, null);
        checkOk(response, false);
        return MAPPER.readValue(response.body(), ProjectDto.class);
    }

    public ProjectDto create(String token, String name, String projectJson) throws IOException, InterruptedException {
        String body = MAPPER.writeValueAsString(Map.of("name", name, "projectJson", projectJson));
        HttpResponse<String> response = send(token, "POST", "/api/projects", body);
        checkOk(response, false);
        return MAPPER.readValue(response.body(), ProjectDto.class);
    }

    /** @throws ProjectConflictException если {@code baseRevision} устарела — см.
     *  class-javadoc. */
    public ProjectDto update(String token, String id, int baseRevision, String name, String projectJson)
            throws IOException, InterruptedException {
        String body = MAPPER.writeValueAsString(Map.of(
                "baseRevision", baseRevision, "name", name, "projectJson", projectJson));
        HttpResponse<String> response = send(token, "PUT", "/api/projects/" + id, body);
        checkOk(response, true);
        return MAPPER.readValue(response.body(), ProjectDto.class);
    }

    public void delete(String token, String id) throws IOException, InterruptedException {
        HttpResponse<String> response = send(token, "DELETE", "/api/projects/" + id, null);
        checkOk(response, false);
    }

    public List<VersionSummaryDto> versions(String token, String id) throws IOException, InterruptedException {
        HttpResponse<String> response = send(token, "GET", "/api/projects/" + id + "/versions", null);
        checkOk(response, false);
        return MAPPER.readValue(response.body(),
                MAPPER.getTypeFactory().constructCollectionType(List.class, VersionSummaryDto.class));
    }

    public VersionDto getVersion(String token, String id, int revision) throws IOException, InterruptedException {
        HttpResponse<String> response = send(token, "GET", "/api/projects/" + id + "/versions/" + revision, null);
        checkOk(response, false);
        return MAPPER.readValue(response.body(), VersionDto.class);
    }

    public ProjectDto restore(String token, String id, int revision) throws IOException, InterruptedException {
        HttpResponse<String> response = send(token, "POST", "/api/projects/" + id + "/restore/" + revision, "");
        checkOk(response, false);
        return MAPPER.readValue(response.body(), ProjectDto.class);
    }

    private HttpResponse<String> send(String token, String method, String path, String body)
            throws IOException, InterruptedException {
        HttpClient client = TrustedHttp.client();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(LibrarySyncClient.DEFAULT_BASE_URL + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token);
        switch (method) {
            case "POST" -> builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
            case "PUT" -> builder.header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body));
            case "DELETE" -> builder.DELETE();
            default -> builder.GET();
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** {@code isUpdate} — 409 значит "конфликт версий" только для {@link #update},
     *  единственного эндпоинта, который вообще может его вернуть. */
    private void checkOk(HttpResponse<String> response, boolean isUpdate) throws IOException {
        if (isUpdate && response.statusCode() == 409) {
            throw new ProjectConflictException(extractCurrentRevision(response.body()));
        }
        if (response.statusCode() / 100 != 2) {
            throw new AuthClient.AuthException(response.statusCode(), extractMessage(response.body()));
        }
    }

    private static int extractCurrentRevision(String body) {
        try {
            Map<?, ?> parsed = MAPPER.readValue(body, Map.class);
            Object value = parsed.get("currentRevision");
            return value == null ? -1 : Integer.parseInt(value.toString());
        } catch (Exception malformedBody) {
            return -1;
        }
    }

    private static String extractMessage(String errorBody) {
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
