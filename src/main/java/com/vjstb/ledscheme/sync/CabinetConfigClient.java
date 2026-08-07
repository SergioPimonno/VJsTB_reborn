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
 * Анонимное чтение файлов настроек приёмной карты (.rcfgx) — см. {@code CabinetConfigController}
 * на сервере, тот же доверительный уровень, что у {@link LibrarySyncClient} (скачивание
 * не требует входа, загружает только ADMIN через {@code ledscheme-admin}).
 */
public class CabinetConfigClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public record SummaryDto(String id, int refreshHz, int brightnessCd, String fileName, String uploadedAt) {
    }

    public record DownloadedFile(String fileName, byte[] content) {
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
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(LibrarySyncClient.DEFAULT_BASE_URL + path))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void checkOk(HttpResponse<String> response) throws IOException {
        if (response.statusCode() != 200) {
            throw new IOException("Сервер вернул код " + response.statusCode());
        }
    }
}
