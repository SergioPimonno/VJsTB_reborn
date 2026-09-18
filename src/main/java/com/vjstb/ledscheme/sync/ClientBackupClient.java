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
 * Аварийная копия локальных файлов клиента на сервере (см. {@code
 * ClientBackupController}) — по запросу пользователя после инцидента 2026-09-15
 * (не-атомарная запись + обрыв процесса оставили {@code workspace.json} пустым,
 * весь рабочий стол чуть не потерялся; сама не-атомарность уже исправлена в
 * {@code WorkspaceStore#save}, это — дополнительная, отдельная страховка). Требует
 * логин (см. {@link AuthClient}) — сознательно НЕ анонимный путь, в отличие от
 * {@link LibrarySyncClient} (см. javadoc {@code ClientBackup} на сервере), поэтому
 * это молча не работает для незалогиненных пользователей — см. вызывающий код
 * ({@code ClientBackupSync}), который просто пропускает загрузку без токена.
 */
public class ClientBackupClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public record BackupDto(String fileKind, String content, String clientVersion, String updatedAt) {
    }

    private final String baseUrl;

    public ClientBackupClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void upload(String token, String fileKind, String content, String clientVersion)
            throws IOException, InterruptedException {
        String body = MAPPER.writeValueAsString(Map.of("content", content, "clientVersion",
                clientVersion == null ? "" : clientVersion));
        HttpClient client = TrustedHttp.clientFor(baseUrl);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/client-backups/" + fileKind))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Сервер отклонил резервную копию (" + fileKind + "): HTTP " + response.statusCode());
        }
    }

    /** {@code null}, если резервной копии ещё нет (404) — не ошибка. */
    public BackupDto fetch(String token, String fileKind) throws IOException, InterruptedException {
        HttpClient client = TrustedHttp.clientFor(baseUrl);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/client-backups/" + fileKind))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Не удалось получить резервную копию (" + fileKind + "): HTTP "
                    + response.statusCode());
        }
        return MAPPER.readValue(response.body(), BackupDto.class);
    }
}
