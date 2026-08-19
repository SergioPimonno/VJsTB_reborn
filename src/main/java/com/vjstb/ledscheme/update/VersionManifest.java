package com.vjstb.ledscheme.update;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.sync.LibrarySyncClient;
import com.vjstb.ledscheme.sync.TrustedHttp;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Список доступных версий приложения — читается с сервера (общая библиотека,
 * синглтон-вид {@code VERSION_MANIFEST}, редактируется через админ-консоль
 * ledscheme-admin, см. серверный class-javadoc LibraryItemKind). Раньше читался из
 * статического {@code versions.txt} в этом же GitHub-репозитории, правившегося
 * вручную при каждом релизе — перенесено на сервер, чтобы список обновлений можно
 * было менять без нового коммита/пуша, и чтобы клиент мог автоматически проверять
 * его при запуске (см. App.checkForUpdatesInBackground).
 *
 * <p>Никакого автовыбора "самой новой версии" тут по-прежнему нет для РУЧНОГО
 * обновления (см. UpdateDialog — пользователь сам выбирает версию из списка);
 * сравнение версий появилось только для автоматической проверки при запуске (см.
 * {@link #isNewer}), которая лишь предупреждает, а не подменяет файл сама.</p>
 */
public final class VersionManifest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public record Entry(String version, String releaseTag, boolean available, String notes) {
        @Override
        public String toString() {
            return version + (notes != null && !notes.isBlank() ? " (" + notes + ")" : "");
        }
    }

    /** Зеркалит payload синглтона VERSION_MANIFEST на сервере (см. серверный
     *  class-javadoc LibraryItemKind и админский VersionManifestPanel) — те же
     *  имена полей у {@link Entry}, Jackson сопоставляет по имени напрямую. */
    private record Payload(List<Entry> versions) {
    }

    private VersionManifest() {
    }

    /** Скачивает и разбирает манифест — бросает IOException при сетевой ошибке
     *  или если синглтон ещё ни разу не сохранён с админ-консоли (см. серверный
     *  {@code LibraryController.singleton}, 404 в этом случае) — вызывающая сторона
     *  показывает это пользователю (см. UpdateDialog) либо тихо пропускает
     *  автопроверку при запуске (см. App).
     *
     * <p>{@code baseUrl} — см. {@link LibrarySyncClient#resolveBaseUrl} (ручной
     * "мост синхронизации"); {@link #fetch()} без параметра — всегда адрес по
     * умолчанию, для мест, где {@code SettingsManager} недоступен. */
    public static List<Entry> fetch(String baseUrl) throws IOException, InterruptedException {
        HttpClient client = TrustedHttp.clientFor(baseUrl);
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(baseUrl + "/api/library/singleton/VERSION_MANIFEST"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Сервер вернул код " + response.statusCode());
        }
        LibrarySyncClient.LibraryItemDto dto = MAPPER.readValue(response.body(), LibrarySyncClient.LibraryItemDto.class);
        Payload payload = MAPPER.readValue(dto.payloadJson(), Payload.class);
        return payload.versions() != null ? payload.versions() : List.of();
    }

    public static List<Entry> fetch() throws IOException, InterruptedException {
        return fetch(LibrarySyncClient.DEFAULT_BASE_URL);
    }

    /** Только доступные (available=true) версии — то, что нужно показать в списке. */
    public static List<Entry> fetchAvailable(String baseUrl) throws IOException, InterruptedException {
        List<Entry> all = fetch(baseUrl);
        List<Entry> result = new ArrayList<>();
        for (Entry e : all) {
            if (e.available()) {
                result.add(e);
            }
        }
        return result;
    }

    public static List<Entry> fetchAvailable() throws IOException, InterruptedException {
        return fetchAvailable(LibrarySyncClient.DEFAULT_BASE_URL);
    }

    /** Простое сравнение точечных версий вида "2.0"/"1.6.1" по числовым сегментам
     *  (отсутствующий сегмент == 0, нечисловой сегмент сравнивается как строка —
     *  этого достаточно для схемы версионирования этого проекта, полноценный
     *  semver тут не нужен). {@code candidate > current}. */
    public static boolean isNewer(String candidate, String current) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        if (current == null || current.isBlank()) {
            return true;
        }
        String[] a = candidate.trim().split("\\.");
        String[] b = current.trim().split("\\.");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            String sa = i < a.length ? a[i] : "0";
            String sb = i < b.length ? b[i] : "0";
            int cmp = compareSegment(sa, sb);
            if (cmp != 0) {
                return cmp > 0;
            }
        }
        return false;
    }

    private static int compareSegment(String a, String b) {
        try {
            return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
        } catch (NumberFormatException ex) {
            return a.compareTo(b);
        }
    }
}
