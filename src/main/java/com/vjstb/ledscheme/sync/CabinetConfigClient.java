package com.vjstb.ledscheme.sync;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Чтение файлов настроек приёмной карты (.rcfgx) — см. {@code CabinetConfigController}
 * на сервере. Вход необязателен: без токена видны только файлы с отметкой «видно всем»,
 * с токеном — ещё и файлы команды пользователя (их загружают ADMIN и модераторы команды
 * через {@code ledscheme-admin}). Просроченный токен сервер молча считает отсутствием входа.
 */
public class CabinetConfigClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public record SummaryDto(String id, int refreshHz, int brightnessCd, String fileName, String uploadedAt) {
    }

    public record DownloadedFile(String fileName, byte[] content) {
    }

    private final String baseUrl;
    /** null/пусто — аноним. */
    private final String token;

    public CabinetConfigClient() {
        this(LibrarySyncClient.DEFAULT_BASE_URL);
    }

    public CabinetConfigClient(String baseUrl) {
        this(baseUrl, null);
    }

    public CabinetConfigClient(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    public List<SummaryDto> list(String cabinetTypeName) throws IOException, InterruptedException {
        String query = "?cabinetTypeName=" + URLEncoder.encode(cabinetTypeName, StandardCharsets.UTF_8);
        HttpResponse<String> response = send("/api/cabinet-configs" + query);
        checkOk(response);
        return MAPPER.readValue(response.body(),
                MAPPER.getTypeFactory().constructCollectionType(List.class, SummaryDto.class));
    }

    public DownloadedFile download(String id) throws IOException, InterruptedException {
        HttpResponse<String> response = send("/api/cabinet-configs/" + id);
        checkOk(response);
        var node = MAPPER.readTree(response.body());
        byte[] content = Base64.getDecoder().decode(node.get("contentBase64").asText());
        return new DownloadedFile(node.get("fileName").asText(), content);
    }

    private HttpResponse<String> send(String path) throws IOException, InterruptedException {
        HttpClient client = TrustedHttp.clientFor(baseUrl);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .GET();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void checkOk(HttpResponse<String> response) throws IOException {
        if (response.statusCode() != 200) {
            throw new IOException("Сервер вернул код " + response.statusCode());
        }
    }
}
