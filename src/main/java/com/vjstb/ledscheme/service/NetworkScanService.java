package com.vjstb.ledscheme.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Сканирование диапазона IP-адресов Сетевого менеджера (запрос пользователя:
 * "добавим возможность сканировать диапазон IP для обнаружения устройств с
 * неизвестными адресами" — многие устройства площадки подключаются по DHCP
 * или их адрес просто забыт/не задокументирован, найти их вручную
 * перебором в другой программе неудобно).
 *
 * <p>В отличие от {@link NetworkPingService} (непрерывный поток вывода ОДНОГО
 * пинга, показывается пользователю живьём построчно) — здесь по КАЖДОМУ
 * адресу диапазона запускается ОДИН короткий пинг (см. {@link
 * #buildOneShotPingCommand}), решение "жив/не жив" — по КОДУ ВОЗВРАТА
 * процесса (0 = получен ответ и на Windows, и на POSIX `ping -c 1`), а не
 * разбором текста вывода — сам вывод не читается вообще (перенаправлен в
 * {@code Redirect.DISCARD}), поэтому не нужна ни возня с кодировкой консоли
 * (см. class-javadoc {@code NetworkPingService} про {@code chcp 65001}), ни
 * shell-обёртка вообще: адрес передаётся ОТДЕЛЬНЫМ аргументом {@link
 * ProcessBuilder} (список, не строка команды) — command injection в принципе
 * невозможен, отдельная валидация вида {@code HOST_PATTERN} не нужна (адреса
 * и так собираются из уже провалидированных целых 0-255, см. {@link
 * #expandRange}).
 *
 * <p>Диапазон сканируется ПАРАЛЛЕЛЬНО ограниченным пулом потоков ({@link
 * #PARALLELISM}) — последовательно по одному адресу диапазон в сотни хостов
 * занял бы минуты даже с коротким таймаутом на каждый. Результаты приходят
 * вызывающей стороне ПО МЕРЕ готовности (не единым списком после конца
 * скана) — тот же принцип "живого" отклика, что и у {@link
 * NetworkPingService#startPing}, просто на уровне отдельных хостов, а не
 * строк одного хоста.
 *
 * <p><b>Диапазон адресов — простая пара "От"/"До"</b> (запрос пользователя,
 * ПОСЛЕ короткого захода на "IP + маска" — тот интерфейс понравился меньше:
 * "диапазон IP выглядел лучше... убери ограничение на маску /24") — {@link
 * #expandRange} принимает ЛЮБЫЕ два IPv4-адреса, {@code from <= to} как
 * 32-битные числа, БЕЗ требования совпадать в каких-либо октетах (диапазон
 * может пересекать границу /24, например {@code 192.168.1.250} —
 * {@code 192.168.2.10}). Ограничен {@link #MAX_SCAN_ADDRESSES} — без потолка
 * случайно широкий диапазон занял бы часы даже с параллельным пулом и мог бы
 * восприниматься как зависшее приложение; отклоняется явной ошибкой с
 * объяснением, а не тихо режется до первых N адресов. */
public final class NetworkScanService {

    private static final int PARALLELISM = 32;
    private static final int PING_TIMEOUT_MS = 400;
    /** Потолок числа адресов за один скан — см. {@link #expandRange} javadoc. */
    private static final int MAX_SCAN_ADDRESSES = 1024;

    private NetworkScanService() {
    }

    public record ScanResult(String ip, boolean reachable) {
    }

    /** Позволяет остановить сканирование, ещё не дошедшее до конца — убивает уже
     *  запущенные процессы ping (по образцу {@code NetworkPingService.PingSession
     *  #stop}, дерево процессов через {@code descendants()}, не только сам
     *  процесс) и не даёт запуститься ещё не начатым. */
    public interface ScanHandle {
        void cancel();
    }

    /** Запускает сканирование АСИНХРОННО — {@code onResult} вызывается на EDT
     *  для КАЖДОГО адреса по мере готовности (не гарантирован порядок — параллельные
     *  потоки завершаются в произвольном порядке), {@code onDone} на EDT один раз,
     *  когда обработаны все адреса (в т.ч. после {@link ScanHandle#cancel()} —
     *  недостающим адресам подставляется {@code reachable=false} без реального
     *  пинга, чтобы {@code onDone} гарантированно наступил). */
    public static ScanHandle scanRange(List<String> ips, Consumer<ScanResult> onResult, Runnable onDone) {
        if (ips.isEmpty()) {
            SwingUtilities.invokeLater(onDone);
            return () -> { };
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(PARALLELISM, ips.size()));
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicInteger remaining = new AtomicInteger(ips.size());
        // Гарантирует, что onDone вызывается РОВНО ОДИН РАЗ, даже если естественное
        // завершение (remaining достиг нуля) и cancel() гонятся друг с другом -- см.
        // javadoc ScanHandle#cancel про то, почему без этого onDone мог вообще не
        // наступить при отмене (pool.shutdownNow() выбрасывает ещё НЕ НАЧАТЫЕ задачи
        // из очереди без выполнения -- их remaining.decrementAndGet() просто никогда
        // не случится).
        AtomicBoolean doneFired = new AtomicBoolean(false);
        List<Process> liveProcesses = Collections.synchronizedList(new ArrayList<>());
        Runnable fireDoneOnce = () -> {
            if (doneFired.compareAndSet(false, true)) {
                SwingUtilities.invokeLater(onDone);
            }
        };

        for (String ip : ips) {
            pool.submit(() -> {
                if (cancelled.get()) {
                    if (remaining.decrementAndGet() == 0) {
                        fireDoneOnce.run();
                    }
                    return;
                }
                boolean reachable = pingOnce(ip, liveProcesses);
                SwingUtilities.invokeLater(() -> onResult.accept(new ScanResult(ip, reachable)));
                if (remaining.decrementAndGet() == 0) {
                    fireDoneOnce.run();
                }
            });
        }
        pool.shutdown(); // новые задачи не принимаются, уже отправленные доработают

        return () -> {
            cancelled.set(true);
            synchronized (liveProcesses) {
                for (Process p : liveProcesses) {
                    p.descendants().forEach(ProcessHandle::destroy);
                    p.destroy();
                }
            }
            // Выбрасывает ещё НЕ НАЧАТЫЕ задачи из очереди без выполнения -- поэтому
            // onDone форсируется явно ниже, а не полагается на то, что каждая из них
            // сама доберётся до своего remaining.decrementAndGet().
            pool.shutdownNow();
            fireDoneOnce.run();
        };
    }

    private static boolean pingOnce(String ip, List<Process> liveProcesses) {
        try {
            List<String> command = buildOneShotPingCommand(System.getProperty("os.name", ""), ip);
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectErrorStream(true)
                    .start();
            liveProcesses.add(process);
            boolean finished = process.waitFor(PING_TIMEOUT_MS + 500L, TimeUnit.MILLISECONDS);
            liveProcesses.remove(process);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /** Package-private ради теста (без реального запуска процесса). Один пакет,
     *  короткий таймаут — {@code -w}/{@code -W} у ping'а ОС, а не только {@link
     *  Process#waitFor} со стороны Java (тот тоже подстрахован выше на случай,
     *  если сам ping почему-то не уложился в свой заявленный таймаут). */
    static List<String> buildOneShotPingCommand(String osName, String ip) {
        boolean windows = osName != null && osName.toLowerCase().contains("win");
        if (windows) {
            return List.of("ping", "-n", "1", "-w", String.valueOf(PING_TIMEOUT_MS), ip);
        }
        return List.of("ping", "-c", "1", "-W", "1", ip);
    }

    /** Разбирает диапазон {@code fromIp}..{@code toIp} (включительно) в список
     *  адресов — оба конца просто сравниваются как 32-битные числа, БЕЗ
     *  требования совпадать в каких-либо октетах (см. class-javadoc — по
     *  прямому запросу пользователя, после того как более "умный" вариант
     *  "IP + маска подсети" понравился меньше простой пары "От"/"До").
     *  Диапазон крупнее {@link #MAX_SCAN_ADDRESSES} отклоняется явной ошибкой
     *  (см. class-javadoc) — вызывающий диалог показывает текст исключения
     *  пользователю как есть. */
    public static List<String> expandRange(String fromIp, String toIp) {
        long from = toLong(parseIPv4(fromIp));
        long to = toLong(parseIPv4(toIp));
        if (from > to) {
            throw new IllegalArgumentException("Начальный адрес должен быть меньше или равен конечному");
        }

        long count = to - from + 1;
        if (count > MAX_SCAN_ADDRESSES) {
            throw new IllegalArgumentException(
                    "Диапазон слишком большой (" + count + " адресов, максимум " + MAX_SCAN_ADDRESSES + ") — сузьте его");
        }

        List<String> result = new ArrayList<>();
        for (long a = from; a <= to; a++) {
            result.add(fromLong(a));
        }
        return result;
    }

    private static int[] parseIPv4(String ip) {
        String trimmed = ip == null ? "" : ip.trim();
        String[] parts = trimmed.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Некорректный IPv4-адрес: \"" + ip + "\"");
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                octets[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Некорректный IPv4-адрес: \"" + ip + "\"");
            }
            if (octets[i] < 0 || octets[i] > 255) {
                throw new IllegalArgumentException("Некорректный IPv4-адрес: \"" + ip + "\"");
            }
        }
        return octets;
    }

    private static long toLong(int[] octets) {
        return ((long) octets[0] << 24) | (octets[1] << 16) | (octets[2] << 8) | octets[3];
    }

    private static String fromLong(long value) {
        return ((value >> 24) & 0xFF) + "." + ((value >> 16) & 0xFF) + "." + ((value >> 8) & 0xFF) + "." + (value & 0xFF);
    }
}
