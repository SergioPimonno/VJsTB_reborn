package com.vjstb.ledscheme;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * Лог запуска и необработанных исключений — {@code ~/.led-scheme/logs/startup.log}.
 *
 * <p>Запрос пользователя 2026-09-30 (v2.6, пункт 7): «программа не открывается,
 * если папку положить на диск D:». Под {@code javaw}/jpackage-лаунчером консоли
 * нет, а {@code App.main} раньше делал лишь {@code printStackTrace()} — то есть
 * причина отказа запуска нигде не оставалась и диагностировать удалённо было
 * нечем. Теперь любой сбой старта и любое необработанное исключение любого
 * потока (в т.ч. EDT) пишется сюда вместе со сведениями о среде (версия,
 * {@code java.home}, {@code user.dir}, путь jar/лаунчера, {@code
 * jpackage.app-path}, ОС) — пользователь присылает один файл вместо пересказа
 * симптома.
 *
 * <p>Ротация: {@code startup.log} + {@code startup.1.log} … {@code startup.4.log}
 * (всего 5 файлов, каждый не больше {@link #MAX_BYTES}); при переполнении
 * текущего файла он сдвигается в {@code .1}, самый старый удаляется. Любые
 * ошибки записи глушатся: лог — вспомогательный инструмент и не должен сам
 * становиться причиной падения запуска (каталог только для чтения, диск полон
 * и т.п.).
 */
public final class StartupLog {

    /** Размер, при достижении которого текущий файл уходит в ротацию. */
    public static final long MAX_BYTES = 1024 * 1024;
    /** Сколько файлов хранить всего (текущий + архивные). */
    public static final int KEEP_FILES = 5;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Path dir;

    public StartupLog(Path dir) {
        this.dir = dir;
    }

    /** Каталог логов по умолчанию — рядом с остальными данными пользователя
     *  ({@code ~/.led-scheme/}, см. SettingsStore/WorkspaceStore). */
    public static Path defaultDir() {
        return Path.of(System.getProperty("user.home", "."), ".led-scheme", "logs");
    }

    public static StartupLog forDefaultLocation() {
        return new StartupLog(defaultDir());
    }

    /** Текущий файл лога. */
    public Path file() {
        return dir.resolve("startup.log");
    }

    private Path archived(int index) {
        return dir.resolve("startup." + index + ".log");
    }

    /** Дописывает строку с отметкой времени; ошибки записи молча игнорируются. */
    public synchronized void write(String message) {
        try {
            Files.createDirectories(dir);
            rotateIfNeeded();
            String line = LocalDateTime.now().format(TS) + " " + message + System.lineSeparator();
            Files.writeString(file(), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) {
            // см. class-javadoc: лог не должен ронять запуск
        }
    }

    /** Пишет контекст и полный стектрейс (включая причины). */
    public void writeThrowable(String context, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        write(context + System.lineSeparator() + sw.toString().stripTrailing());
    }

    /** Пишет сведения о среде текущего процесса — заголовок записи о запуске. */
    public void writeEnvironment() {
        write(describeEnvironment(System::getProperty, codeSourceLocation(), Runtime.getRuntime().maxMemory()));
    }

    /**
     * Текстовое описание среды запуска. Вынесено в чистую функцию (источник
     * свойств — параметр), чтобы тестироваться без реального процесса.
     *
     * @param property       доступ к системным свойствам ({@code System::getProperty})
     * @param codeSource     путь jar/каталога, откуда загружен {@link App} (или null)
     * @param maxHeapBytes   {@link Runtime#maxMemory()}
     */
    public static String describeEnvironment(Function<String, String> property, String codeSource,
            long maxHeapBytes) {
        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder("=== Запуск AVE_ToolBox ===").append(nl);
        sb.append("  версия:             ").append(AppInfo.VERSION).append(nl);
        sb.append("  ОС:                 ").append(property.apply("os.name")).append(' ')
                .append(property.apply("os.version")).append(" (").append(property.apply("os.arch")).append(')')
                .append(nl);
        sb.append("  java:               ").append(property.apply("java.version")).append(" / ")
                .append(property.apply("java.vendor")).append(nl);
        sb.append("  java.home:          ").append(property.apply("java.home")).append(nl);
        sb.append("  user.dir:           ").append(property.apply("user.dir")).append(nl);
        sb.append("  user.home:          ").append(property.apply("user.home")).append(nl);
        sb.append("  jar/каталог кода:   ").append(codeSource).append(nl);
        sb.append("  jpackage.app-path:  ").append(property.apply("jpackage.app-path")).append(nl);
        sb.append("  java.class.path:    ").append(property.apply("java.class.path")).append(nl);
        sb.append("  кодировка:          file.encoding=").append(property.apply("file.encoding"))
                .append(", native.encoding=").append(property.apply("native.encoding")).append(nl);
        sb.append("  java.io.tmpdir:     ").append(property.apply("java.io.tmpdir")).append(nl);
        sb.append("  макс. heap:         ").append(maxHeapBytes / (1024 * 1024)).append(" МБ");
        return sb.toString();
    }

    private static String codeSourceLocation() {
        try {
            var src = App.class.getProtectionDomain().getCodeSource();
            if (src == null) {
                return null;
            }
            // Path, а не сырой URL: в URL кириллица/пробелы percent-encoded и лог
            // нечитаем именно для тех путей, ради которых он и нужен.
            try {
                return Path.of(src.getLocation().toURI()).toString();
            } catch (java.net.URISyntaxException | RuntimeException ex) {
                return String.valueOf(src.getLocation());
            }
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Ставит глобальный обработчик необработанных исключений: пишет в лог, затем
     * передаёт прежнему обработчику (стандартное поведение — печать в stderr),
     * так что запуск из IDE/консоли выглядит как раньше.
     */
    public void installUncaughtHandler() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            writeThrowable("Необработанное исключение в потоке \"" + thread.getName() + "\"", ex);
            if (previous != null) {
                previous.uncaughtException(thread, ex);
            } else {
                ex.printStackTrace();
            }
        });
    }

    private void rotateIfNeeded() throws IOException {
        Path current = file();
        if (!Files.exists(current) || Files.size(current) < MAX_BYTES) {
            return;
        }
        Files.deleteIfExists(archived(KEEP_FILES - 1));
        for (int i = KEEP_FILES - 2; i >= 1; i--) {
            Path from = archived(i);
            if (Files.exists(from)) {
                Files.move(from, archived(i + 1), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.move(current, archived(1), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
