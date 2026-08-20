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
 * Отправка предложения нового элемента в общую библиотеку (см. {@code ProposalController}
 * на сервере). Требует токен (см. {@link AuthClient}) — в отличие от анонимного
 * чтения (см. {@link LibrarySyncClient}).
 *
 * <p>Осознанное сужение объёма (см. план): всегда {@code kind=NEW_ITEM} — клиент
 * сейчас не отличает "этот локальный элемент реально с сервера" от "чисто
 * локальный", поэтому не пытается предлагать {@code EDIT_EXISTING}; модератор
 * при необходимости сведёт дубликат вручную.
 */
public class ProposalClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public record ProposalDto(String id, String authorUserId, String kind, String targetItemId,
                               String libraryItemKind, String draftJson, String justification,
                               String status, String moderatorNote) {
    }

    private final String baseUrl;

    public ProposalClient() {
        this(LibrarySyncClient.DEFAULT_BASE_URL);
    }

    public ProposalClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public ProposalDto submit(String token, String libraryItemKind, String draftJson, String justification)
            throws IOException, InterruptedException {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("kind", "NEW_ITEM");
        body.put("targetItemId", null);
        body.put("libraryItemKind", libraryItemKind);
        body.put("draftJson", draftJson);
        body.put("justification", justification);
        String json = MAPPER.writeValueAsString(body);

        HttpClient client = TrustedHttp.clientFor(baseUrl);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/proposals"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new AuthClient.AuthException(response.statusCode(), extractMessage(response.body()));
        }
        return parseSuccess(response.body());
    }

    /** Список предложений "на рассмотрении" — только MODERATOR/ADMIN (см.
     *  SecurityConfig на сервере), обычный USER получит 403. */
    public List<ProposalDto> pending(String token) throws IOException, InterruptedException {
        HttpClient client = TrustedHttp.clientFor(baseUrl);
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(baseUrl + "/api/proposals/pending"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new AuthClient.AuthException(response.statusCode(), extractMessage(response.body()));
        }
        return parsePendingList(response.body());
    }

    /** Вынесено отдельно от {@link #pending} ради тестируемости без реальной сети. */
    static List<ProposalDto> parsePendingList(String json) throws IOException {
        return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, ProposalDto.class));
    }

    public ProposalDto approve(String token, String id, String note) throws IOException, InterruptedException {
        return decide(token, id, "approve", note);
    }

    public ProposalDto reject(String token, String id, String note) throws IOException, InterruptedException {
        return decide(token, id, "reject", note);
    }

    private ProposalDto decide(String token, String id, String action, String note)
            throws IOException, InterruptedException {
        String json = MAPPER.writeValueAsString(Map.of("note", note == null ? "" : note));
        HttpClient client = TrustedHttp.clientFor(baseUrl);
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(baseUrl + "/api/proposals/" + id + "/" + action))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new AuthClient.AuthException(response.statusCode(), extractMessage(response.body()));
        }
        return parseSuccess(response.body());
    }

    /** Вынесено отдельно от {@link #submit} ради тестируемости без реальной сети. */
    static ProposalDto parseSuccess(String json) throws IOException {
        return MAPPER.readValue(json, ProposalDto.class);
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
