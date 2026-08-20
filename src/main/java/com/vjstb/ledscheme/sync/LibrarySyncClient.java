package com.vjstb.ledscheme.sync;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Тянет дельту общей библиотеки с сервера ({@code ledscheme-server}, задеплоен на
 * dxv) — единственный анонимный (без токена) эндпоинт сервера, авторизация тут не
 * нужна (см. LibraryController на сервере). Стиль — как {@code update.UpdateManager}
 * (голый {@link HttpClient}, доп. библиотека не нужна), это первый и пока
 * единственный сетевой вызов к СВОЕМУ серверу (в отличие от UpdateManager, который
 * ходит на GitHub).
 */
public class LibrarySyncClient {

    /** Адрес по умолчанию — прямое подключение к Caddy на dxv (см. CLAUDE.md секция 2) —
     *  8081 больше не открыт наружу, сервер слушает только 127.0.0.1:8081,
     *  публично доступен только через reverse-proxy на 8443. Сертификат
     *  самоподписанный (домена пока нет) — доверие настраивается точечно,
     *  см. {@link TrustedHttp}. На сетях, блокирующих исходящие соединения на
     *  нестандартные порты (вроде 8443), пользователь может переопределить адрес
     *  вручную в Настройках — см. {@link #resolveBaseUrl}. */
    public static final String DEFAULT_BASE_URL = "https://138.16.177.176:8443";

    /** Все sync-клиенты (см. {@code AuthClient}, {@code ProposalClient},
     *  {@code ProjectArchiveClient}, {@code CabinetConfigClient}) резолвят базовый
     *  адрес через этот метод, а не читают {@link #DEFAULT_BASE_URL} напрямую —
     *  единая точка, где применяется ручной "мост синхронизации"
     *  ({@code AppSettings#getSyncServerUrlOverride}). Пустая/null-строка —
     *  override не задан, используем адрес по умолчанию как раньше. */
    public static String resolveBaseUrl(SettingsManager settings) {
        String override = settings != null ? settings.getSyncServerUrlOverride() : null;
        return override != null && !override.isBlank() ? override.strip() : DEFAULT_BASE_URL;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** Зеркалит {@code LibraryController.LibraryItemDto} на сервере — те же имена
     *  полей, Jackson сопоставляет по имени. */
    public record LibraryItemDto(String id, String kind, String name, String payloadJson,
                                  long globalSeq, boolean deleted) {
    }

    /** Зеркалит {@code LibraryController.ChangesResponse}. */
    public record ChangesResult(List<LibraryItemDto> items, long latestGlobalSeq) {
    }

    private final String baseUrl;

    public LibrarySyncClient() {
        this(DEFAULT_BASE_URL);
    }

    public LibrarySyncClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public ChangesResult fetchChanges(long since) throws IOException, InterruptedException {
        HttpClient client = TrustedHttp.clientFor(baseUrl);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/library/changes?since=" + since))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Сервер вернул код " + response.statusCode());
        }
        return parse(response.body());
    }

    /** Вынесено отдельно от {@link #fetchChanges} ради тестируемости без реальной сети. */
    static ChangesResult parse(String json) throws IOException {
        return MAPPER.readValue(json, ChangesResult.class);
    }
}
