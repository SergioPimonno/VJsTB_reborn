package com.vjstb.ledscheme.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Список доступных версий приложения — читается из простого текстового файла
 * в репозитории (не JSON, чтобы его было легко править вручную при выпуске новой
 * версии): по строке на версию, формат {@code версия|тег релиза|available|заметка},
 * где available — «yes»/«no» (строки со «no» или без этого поля скрыты от
 * пользователя — черновик/снятая с публикации версия). Строки, начинающиеся с
 * {@code #}, и пустые строки игнорируются.
 *
 * <p>Подход и формат сознательно взяты из уже проверенного в другом приложении
 * автора (dxvfix) механизма самообновления — никакого сравнения версий тут нет:
 * пользователь сам выбирает нужную версию из списка доступных (см. UpdateDialog),
 * а не получает автоматически "самую новую".</p>
 */
public final class VersionManifest {

    /** Сырой текстовый файл в репозитории — публикуется вручную при выпуске
     *  релиза (сам этот класс его не пишет, только читает). */
    public static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/SergioPimonno/VJsTB_reborn/main/versions.txt";

    public record Entry(String version, String releaseTag, boolean available, String notes) {
        @Override
        public String toString() {
            return version + (notes != null && !notes.isBlank() ? " (" + notes + ")" : "");
        }
    }

    private VersionManifest() {
    }

    /** Скачивает и разбирает манифест — бросает IOException при сетевой ошибке
     *  (вызывающая сторона показывает это пользователю, см. UpdateDialog). */
    public static List<Entry> fetch() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(MANIFEST_URL))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Сервер вернул код " + response.statusCode());
        }
        return parse(response.body());
    }

    /** Только доступные (available=yes) версии — то, что нужно показать в списке. */
    public static List<Entry> fetchAvailable() throws IOException, InterruptedException {
        List<Entry> all = fetch();
        List<Entry> result = new ArrayList<>();
        for (Entry e : all) {
            if (e.available()) {
                result.add(e);
            }
        }
        return result;
    }

    public static List<Entry> parse(String text) {
        List<Entry> result = new ArrayList<>();
        for (String rawLine : text.split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length < 2) {
                continue;
            }
            String version = parts[0].strip();
            String tag = parts[1].strip();
            boolean available = parts.length > 2 && "yes".equalsIgnoreCase(parts[2].strip());
            String notes = parts.length > 3 ? parts[3].strip() : "";
            if (version.isEmpty() || tag.isEmpty()) {
                continue;
            }
            result.add(new Entry(version, tag, available, notes));
        }
        return result;
    }
}
