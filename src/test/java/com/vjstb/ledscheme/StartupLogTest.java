package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Запрос пользователя 2026-09-30 (v2.6, пункт 7 — «программа не открывается на
 * диске D:»): причину отказа запуска под {@code javaw}/jpackage-лаунчером нигде не
 * было видно. {@link StartupLog} пишет её в {@code ~/.led-scheme/logs/startup.log};
 * тесты — на временном каталоге, не трогая настоящий домашний каталог.
 */
class StartupLogTest {

    @Test
    void writesTimestampedLinesAndStacktraceIntoLogFile(@TempDir Path dir) throws IOException {
        StartupLog log = new StartupLog(dir.resolve("logs"));
        log.write("привет");
        log.writeThrowable("Не удалось запустить", new IllegalStateException("boom", new RuntimeException("корень")));

        String text = Files.readString(log.file(), StandardCharsets.UTF_8);
        assertTrue(text.contains("привет"), text);
        assertTrue(text.contains("Не удалось запустить"), text);
        assertTrue(text.contains("IllegalStateException: boom"), text);
        assertTrue(text.contains("Caused by: java.lang.RuntimeException: корень"), text);
        assertTrue(text.matches("(?s)^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} привет.*"), text);
    }

    @Test
    void rotatesIntoAtMostFiveFilesOfBoundedSize(@TempDir Path dir) throws IOException {
        StartupLog log = new StartupLog(dir);
        // забиваем текущий файл до порога ротации несколько раз подряд
        for (int round = 0; round < 7; round++) {
            Files.write(log.file(), new byte[(int) StartupLog.MAX_BYTES]);
            log.write("раунд " + round);
        }
        try (var files = Files.list(dir)) {
            long count = files.filter(p -> p.getFileName().toString().startsWith("startup")).count();
            assertEquals(StartupLog.KEEP_FILES, count, "startup.log + startup.1..4.log");
        }
        assertTrue(Files.exists(dir.resolve("startup.4.log")));
        assertFalse(Files.exists(dir.resolve("startup.5.log")));
        assertTrue(Files.readString(log.file()).contains("раунд 6"));
        assertTrue(Files.size(log.file()) < StartupLog.MAX_BYTES);
    }

    @Test
    void writeFailureIsSwallowed(@TempDir Path dir) throws IOException {
        // «каталог логов» — на самом деле обычный файл: createDirectories упадёт
        Path blocker = Files.writeString(dir.resolve("logs"), "not a directory");
        StartupLog log = new StartupLog(blocker);
        log.write("не должно бросать исключений");
        log.writeThrowable("и это тоже", new RuntimeException("x"));
        log.writeEnvironment();
    }

    @Test
    void environmentDescriptionContainsLaunchDiagnostics() {
        Map<String, String> props = Map.of(
                "os.name", "Windows 11", "os.version", "10.0", "os.arch", "amd64",
                "java.version", "21.0.10", "java.vendor", "Oracle", "java.home", "D:\\Программы\\AVE\\runtime",
                "user.dir", "D:\\Программы\\AVE", "user.home", "C:\\Users\\u",
                "jpackage.app-path", "D:\\Программы\\AVE\\AVE_ToolBox.exe");
        String text = StartupLog.describeEnvironment(props::get, "file:/D:/Программы/AVE/app/led-scheme.jar",
                512L * 1024 * 1024);
        assertTrue(text.contains(AppInfo.VERSION), text);
        assertTrue(text.contains("D:\\Программы\\AVE\\runtime"), text);
        assertTrue(text.contains("D:\\Программы\\AVE\\AVE_ToolBox.exe"), text);
        assertTrue(text.contains("file:/D:/Программы/AVE/app/led-scheme.jar"), text);
        assertTrue(text.contains("512 МБ"), text);
        assertTrue(text.contains("Windows 11"), text);
    }

    @Test
    void defaultDirIsUnderLedSchemeHomeFolder() {
        Path dir = StartupLog.defaultDir();
        assertEquals("logs", dir.getFileName().toString());
        assertEquals(".led-scheme", dir.getParent().getFileName().toString());
    }

    @Test
    void uncaughtHandlerLogsAndChainsToPreviousHandler(@TempDir Path dir) throws Exception {
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        try {
            java.util.concurrent.atomic.AtomicReference<Throwable> chained = new java.util.concurrent.atomic.AtomicReference<>();
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> chained.set(e));
            StartupLog log = new StartupLog(dir);
            log.installUncaughtHandler();

            Thread worker = new Thread(() -> {
                throw new IllegalArgumentException("сбой потока");
            }, "диагностический-поток");
            worker.start();
            worker.join();

            String text = Files.readString(log.file());
            assertTrue(text.contains("диагностический-поток"), text);
            assertTrue(text.contains("IllegalArgumentException: сбой потока"), text);
            assertEquals("сбой потока", chained.get().getMessage());
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original);
        }
    }
}
