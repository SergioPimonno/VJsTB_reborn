package com.vjstb.ledscheme.update;

import com.vjstb.ledscheme.AppInfo;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.time.Duration;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Скачивание и установка выбранной версии (см. {@link VersionManifest}) — по
 * образцу уже проверенного механизма другого приложения автора (dxvfix):
 * <ul>
 *   <li>Windows: приложение распространяется одним исполняемым jar-ом (см. релизы
 *       на GitHub, {@code led-scheme-vX.Y.jar}) — новую версию можно скачать поверх
 *       СТАРОГО файла и перезапустить процесс. Так как Windows не даёт перезаписать
 *       файл работающего jar-а, подмена делается отдельным .bat-скриптом, который
 *       дожидается завершения текущего процесса (по PID), копирует новый jar на
 *       место старого и перезапускает приложение — сам apply() лишь готовит и
 *       запускает этот скрипт, затем вызывающая сторона (UpdateDialog) закрывает JVM.</li>
 *   <li>Другие ОС (macOS и т.д.): приложение распространяется установочным
 *       образом (.dmg), самостоятельная подмена которого не поддерживается —
 *       скачанный файл просто открывается в системном приложении (Finder
 *       смонтирует .dmg), дальше пользователь ставит его как обычно.</li>
 * </ul>
 */
public final class UpdateManager {

    public static final String DOWNLOAD_URL_TEMPLATE =
            "https://github.com/SergioPimonno/VJsTB_reborn/releases/download/%s/%s";

    private static final String RELEASE_API_TEMPLATE =
            "https://api.github.com/repos/SergioPimonno/VJsTB_reborn/releases/tags/%s";

    private UpdateManager() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    /** Имя файла релиза для текущей ОС — см. release-macos.yml / релизные ассеты. */
    public static String assetNameFor(String releaseTag) {
        if (isWindows()) {
            return "led-scheme-" + releaseTag + ".jar";
        }
        return "LED-Scheme-Designer-" + releaseTag + "-mac.dmg";
    }

    public static String downloadUrlFor(String releaseTag) {
        return String.format(DOWNLOAD_URL_TEMPLATE, releaseTag, assetNameFor(releaseTag));
    }

    /** Скачивает ассет релиза во временный файл, периодически вызывая onProgress
     *  (байт скачано, из известных Content-Length; -1, если сервер его не прислал). */
    public static Path download(String releaseTag, LongConsumer onProgress) throws IOException, InterruptedException {
        String url = downloadUrlFor(releaseTag);
        String assetName = assetNameFor(releaseTag);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        HttpResponse<InputStream> response = openStream(client, url);
        if (response.statusCode() == 404) {
            // Прямой URL собран склейкой имени ассета (см. assetNameFor) — хрупко к
            // тому, что реальный файл в релизе назван иначе (уже один раз так ловили
            // ошибку обновления до v1.5, см. Task #14). Вместо немедленного падения —
            // спросить у самого GitHub список ассетов релиза и попробовать ещё раз.
            String fallbackUrl = resolveAssetUrl(client, releaseTag);
            if (fallbackUrl != null) {
                response = openStream(client, fallbackUrl);
            }
        }
        if (response.statusCode() != 200) {
            throw new IOException("Сервер вернул код " + response.statusCode() + " при скачивании " + url);
        }
        Path dest = Files.createTempFile("led-scheme-update-", "-" + assetName);
        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        try (InputStream in = response.body()) {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            try (var out = Files.newOutputStream(dest)) {
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    done += n;
                    onProgress.accept(total > 0 ? (done * 100 / total) : -1);
                }
            }
        }
        if (!validate(dest, assetName)) {
            Files.deleteIfExists(dest);
            throw new IOException("Скачанный файл повреждён или неполон");
        }
        return dest;
    }

    private static HttpResponse<InputStream> openStream(HttpClient client, String url)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    /** Спрашивает GitHub Releases API за реальным списком ассетов релиза и берёт
     *  первый, чьё имя подходит под текущую ОС (.jar для Windows, .dmg для mac) —
     *  устойчиво к расхождению реального имени файла релиза с тем, что строит
     *  {@link #assetNameFor}. null, если запрос не удался или подходящего ассета
     *  нет — тогда вызывающий код падает с прежним понятным сообщением о 404. */
    private static String resolveAssetUrl(HttpClient client, String releaseTag) {
        try {
            String apiUrl = String.format(RELEASE_API_TEMPLATE, releaseTag);
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
            com.fasterxml.jackson.databind.JsonNode assets = root.get("assets");
            if (assets == null || !assets.isArray()) {
                return null;
            }
            String suffix = isWindows() ? ".jar" : ".dmg";
            for (com.fasterxml.jackson.databind.JsonNode asset : assets) {
                String name = asset.path("name").asText("");
                if (name.toLowerCase(java.util.Locale.ROOT).endsWith(suffix)) {
                    String dlUrl = asset.path("browser_download_url").asText(null);
                    if (dlUrl != null) {
                        return dlUrl;
                    }
                }
            }
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    /** Простая проверка целостности — jar это zip-архив (сигнатура "PK"), для .dmg
     *  формальной сигнатуры не проверяем, только разумный минимальный размер. */
    private static boolean validate(Path file, String assetName) throws IOException {
        long size = Files.size(file);
        if (size < 10_000) {
            return false;
        }
        if (assetName.endsWith(".jar")) {
            byte[] header = new byte[2];
            try (InputStream in = Files.newInputStream(file)) {
                if (in.read(header) != 2) {
                    return false;
                }
            }
            return header[0] == 'P' && header[1] == 'K';
        }
        return true;
    }

    /** true, если приложение сейчас запущено из обычного jar-файла (не из IDE/
     *  директории с .class-файлами) — только тогда возможна автоматическая подмена. */
    public static Path currentJarPath() {
        try {
            CodeSource src = UpdateManager.class.getProtectionDomain().getCodeSource();
            if (src == null) {
                return null;
            }
            URI uri = src.getLocation().toURI();
            Path path = Path.of(uri);
            return Files.isRegularFile(path) && path.toString().endsWith(".jar") ? path : null;
        } catch (URISyntaxException | RuntimeException ex) {
            return null;
        }
    }

    /** Windows + запущены из jar-а — можно скачать поверх старого файла и
     *  перезапуститься автоматически; иначе — только открыть скачанный файл
     *  вручную (см. класс-джавадок). */
    public static boolean supportsAutoApply() {
        return isWindows() && currentJarPath() != null;
    }

    /** Готовит и запускает detached-скрипт, который: дожидается завершения ТЕКУЩЕГО
     *  процесса (по PID), копирует скачанный jar на место старого, перезапускает
     *  приложение через {@code javaw -jar}. Вызывать ПОСЛЕДНИМ действием перед
     *  System.exit(0) — сам скрипт ничего не делает, пока этот процесс жив. */
    public static void applyAndRestartWindows(Path downloadedJar) throws IOException {
        Path currentJar = currentJarPath();
        if (currentJar == null) {
            throw new IllegalStateException("Автообновление недоступно — приложение запущено не из jar-файла");
        }
        long pid = ProcessHandle.current().pid();
        Path scriptFile = Files.createTempFile("led-scheme-update-", ".bat");
        // Баг-репорт: "скачало, перезапустилось, осталась старая версия" — copy
        // сразу после выхода процесса иногда молча не срабатывает (антивирус ещё
        // сканирует свежескачанный jar, либо JVM не сразу освобождает файловый
        // хендл на Windows), а старый код не проверял результат copy вообще и
        // просто перезапускал СТАРЫЙ jar как ни в чём не бывало. Теперь — до 15
        // попыток по секунде (сверяем итоговый размер файла с размером скачанного
        // — простая, но достаточная проверка успешности copy) — и НЕ через
        // `timeout /t`, а через `ping -n 2 127.0.0.1`: у detached-процесса без
        // консоли (см. ниже, вывод в NUL) `timeout` мгновенно падает с "Input
        // redirection is not supported" и НЕ ждёт вообще — воспроизведено и
        // проверено локально, именно это на практике сводило все 15 попыток к
        // одной, за доли секунды, пока файл ещё гарантированно занят. `ping`
        // такой зависимости от консоли не имеет — стандартный приём для паузы в
        // detached/service-скриптах на Windows.
        String script = "@echo off\r\n"
                + "setlocal enabledelayedexpansion\r\n"
                + "set TARGET=\"" + currentJar.toAbsolutePath() + "\"\r\n"
                + "set SOURCE=\"" + downloadedJar.toAbsolutePath() + "\"\r\n"
                + ":waitloop\r\n"
                + "tasklist /FI \"PID eq " + pid + "\" | find \"" + pid + "\" >nul\r\n"
                + "if not errorlevel 1 (\r\n"
                + "  ping -n 2 127.0.0.1 >nul\r\n"
                + "  goto waitloop\r\n"
                + ")\r\n"
                + "for %%A in (%SOURCE%) do set SOURCESIZE=%%~zA\r\n"
                + "set RETRIES=15\r\n"
                + ":copyloop\r\n"
                + "copy /Y %SOURCE% %TARGET% >nul 2>&1\r\n"
                + "set TARGETSIZE=0\r\n"
                + "if exist %TARGET% for %%A in (%TARGET%) do set TARGETSIZE=%%~zA\r\n"
                + "if not \"!TARGETSIZE!\"==\"!SOURCESIZE!\" (\r\n"
                + "  set /a RETRIES-=1\r\n"
                + "  if !RETRIES! gtr 0 (\r\n"
                + "    ping -n 2 127.0.0.1 >nul\r\n"
                + "    goto copyloop\r\n"
                + "  )\r\n"
                + ")\r\n"
                + "start \"\" javaw -jar %TARGET%\r\n"
                + "del %SOURCE% >nul 2>&1\r\n"
                + "(goto) 2>nul & del \"%~f0\"\r\n";
        Files.writeString(scriptFile, script, java.nio.charset.StandardCharsets.US_ASCII);
        // cmd /c <script> напрямую — НЕ через "start" (при заголовке в кавычках
        // start ошибочно трактует его как рабочую директорию, известная особенность
        // Windows, воспроизведённая и исправленная в другом приложении автора).
        // Вывод — в NUL, иначе не читаемый до конца пайп подвешивает detached-процесс.
        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", scriptFile.toAbsolutePath().toString());
        pb.redirectOutput(ProcessBuilder.Redirect.to(new java.io.File("NUL")));
        pb.redirectError(ProcessBuilder.Redirect.to(new java.io.File("NUL")));
        pb.start();
    }

    /** Для случаев без автоподмены (macOS, запуск не из jar-а) — просто открывает
     *  скачанный файл системным приложением (для .dmg это монтирует образ в Finder). */
    public static void openDownloaded(Path file) throws IOException {
        if (java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop.getDesktop().open(file.toFile());
        }
    }

    public static List<VersionManifest.Entry> fetchAvailableVersions() throws IOException, InterruptedException {
        return VersionManifest.fetchAvailable();
    }

    public static String currentVersion() {
        return AppInfo.VERSION;
    }
}
