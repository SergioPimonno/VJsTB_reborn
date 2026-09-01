package com.vjstb.ledscheme.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Непрерывный пинг устройства из Сетевого менеджера (ПКМ по блоку → «Пинг…»,
 * см. NETWORK_MANAGER_NOTES.md) — шеллится в РЕАЛЬНУЮ бесконечную команду
 * {@code ping} ОС (Windows {@code -t}, POSIX без счётчика — по умолчанию до
 * остановки), а не {@link java.net.InetAddress#isReachable}: тот без прав
 * администратора часто откатывается на ненадёжный TCP-echo зонд и просто
 * врёт. Каждая строка вывода передаётся вызывающей стороне ЖИВЬЁМ, по мере
 * поступления (баг-репорт: раньше ждали фиксированные 4 пакета и показывали
 * весь вывод одним куском — пользователю нужно отслеживать ответы в реальном
 * времени) — см. {@link #startPing}. Остановка — {@link PingSession#stop()},
 * убивает процесс (`Process#destroy()`, гарантированно завершает {@code
 * ping.exe}/{@code ping} независимо от того, слушает ли тот сигналы).
 *
 * <p><b>Кодировка (баг-репорт "белиберда вместо текста")</b>: `ping.exe` на
 * Windows пишет в OEM-кодировку консоли (обычно CP866 для русской локали),
 * даже когда вывод перенаправлен в канал — декодирование как UTF-8 (было в
 * первой версии) давало нечитаемую кашу. Простое чтение в CP866 работало бы
 * только для русской локали Windows, не переносимо. Вместо этого команда
 * оборачивается в {@code cmd /c "chcp 65001>nul && ping ..."} — переключает
 * кодовую страницу КОНКРЕТНОЙ консольной сессии (в которой затем сразу
 * запускается ping) на UTF-8 (65001), после чего вывод действительно можно
 * декодировать как UTF-8 надёжно, независимо от локали ОС.
 *
 * <p><b>Безопасность</b>: {@code host} подставляется в СТРОКУ команды cmd
 * (нужно для {@code &&}-цепочки chcp+ping в одной консольной сессии) — чтобы
 * это не превратилось в command injection через спецсимволы shell'а (`&`,
 * `|`, `"`, ...), {@code host} проверяется по строгому allow-list {@link
 * #HOST_PATTERN} (буквы/цифры/точки/двоеточия/дефисы/подчёркивания — то, что
 * реально бывает в IPv4/IPv6/hostname) ДО того, как попасть в команду;
 * невалидный ввод отклоняется без запуска процесса вообще. */
public final class NetworkPingService {

    private NetworkPingService() {
    }

    private static final Pattern HOST_PATTERN = Pattern.compile("[A-Za-z0-9.:_-]{1,253}");

    public interface PingSession {
        void stop();
    }

    /** Запускает непрерывный пинг АСИНХРОННО — {@code onLine} вызывается на EDT
     *  для каждой строки вывода по мере поступления, {@code onExit} на EDT один
     *  раз, когда процесс завершается (в т.ч. после {@link PingSession#stop()}).
     *  Если {@code host} не проходит {@link #HOST_PATTERN} или процесс не
     *  удалось запустить — {@code onLine} получает одну строку с объяснением и
     *  сразу вызывается {@code onExit}, процесс не стартует. */
    public static PingSession startPing(String host, Consumer<String> onLine, Runnable onExit) {
        String trimmed = host == null ? "" : host.trim();
        if (!HOST_PATTERN.matcher(trimmed).matches()) {
            onLine.accept("Некорректный адрес: \"" + host + "\"");
            onExit.run();
            return () -> { };
        }
        Process process;
        try {
            List<String> cmd = buildPingCommand(System.getProperty("os.name", ""), trimmed);
            process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        } catch (IOException e) {
            onLine.accept("Не удалось запустить ping: " + e.getMessage());
            onExit.run();
            return () -> { };
        }
        Process finalProcess = process;
        Charset charset = pingOutputCharset();
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(finalProcess.getInputStream(), charset))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String toShow = line;
                    SwingUtilities.invokeLater(() -> onLine.accept(toShow));
                }
            } catch (IOException ignored) {
                // поток оборвался из-за stop()/завершения процесса — ожидаемо, не ошибка
            } finally {
                SwingUtilities.invokeLater(onExit);
            }
        }, "network-ping-reader");
        reader.setDaemon(true);
        reader.start();
        // На Windows команда -- "cmd /c chcp ... && ping ..." (см. class-javadoc про
        // кодировку): finalProcess -- это САМ cmd.exe, ping.exe -- его ДОЧЕРНИЙ процесс.
        // Убить только cmd.exe (Process#destroy()) не убивает ping.exe -- тот остался бы
        // сиротой, продолжая пинговать в фоне даже после того, как пользователь закрыл
        // диалог и решил, что остановил его. Убиваем всё дерево через descendants().
        return () -> {
            finalProcess.descendants().forEach(ProcessHandle::destroy);
            finalProcess.destroy();
        };
    }

    /** Кодировка вывода команды ping — см. class-javadoc про {@code chcp 65001}:
     *  на Windows команда сама переключает консоль на UTF-8, поэтому и читаем
     *  как UTF-8; на POSIX локаль консоли и так обычно UTF-8. */
    private static Charset pingOutputCharset() {
        return StandardCharsets.UTF_8;
    }

    /** Package-private ради теста (без реального запуска процесса — см.
     *  NetworkPingServiceTest). {@code osName} — сырое значение {@code
     *  System.getProperty("os.name")}, не нормализованное вызывающей стороной.
     *  {@code host} УЖЕ должен быть проверен по {@link #HOST_PATTERN} вызывающей
     *  стороной ({@link #startPing}) — эта функция сама ничего не валидирует. */
    static List<String> buildPingCommand(String osName, String host) {
        boolean windows = osName != null && osName.toLowerCase().contains("win");
        if (windows) {
            return List.of("cmd", "/c", "chcp 65001>nul && ping -t " + host);
        }
        return List.of("ping", host);
    }
}
