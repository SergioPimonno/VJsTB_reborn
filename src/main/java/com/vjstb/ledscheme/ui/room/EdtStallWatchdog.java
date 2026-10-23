package com.vjstb.ledscheme.ui.room;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * Сторож зависаний интерфейса. Пока окно Планнера открыто, фоновый поток раз в ~25 мс отправляет в поток
 * интерфейса (EDT) пустое задание и ждёт ответа; если ответ идёт дольше порога, во время ожидания несколько раз
 * снимает стек EDT и после ответа дописывает в журнал, сколько длилось зависание и где именно оно «сидело»
 * (первые кадры стека с кодом приложения). Нужен для «тормозит, а непонятно почему»: на видео и в тестах видно только
 * следствие, а сторож называет метод. Нагрузка ничтожна (40 пустых заданий в секунду).
 *
 * <p>Журнал небольшой: при превышении размера пересоздаётся.
 */
public final class EdtStallWatchdog {

    private static final long THRESHOLD_MS = 60;
    private static final long SAMPLE_EVERY_MS = 25;
    private static final long MAX_LOG_BYTES = 512 * 1024;

    private final Path log;
    private final long thresholdMs;
    private volatile boolean running = true;
    private volatile Thread edt;
    private final Thread thread;

    private EdtStallWatchdog(Path log, long thresholdMs) {
        this.log = log;
        this.thresholdMs = thresholdMs;
        SwingUtilities.invokeLater(() -> edt = Thread.currentThread());
        thread = new Thread(this::loop, "planner-edt-watchdog");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY + 1);
        thread.start();
    }

    /** Запустить сторожа; ошибки журнала не должны мешать работе окна. */
    public static EdtStallWatchdog start(Path log) {
        return new EdtStallWatchdog(log, THRESHOLD_MS);
    }

    /** Для тестов: свой порог. */
    static EdtStallWatchdog start(Path log, long thresholdMs) {
        return new EdtStallWatchdog(log, thresholdMs);
    }

    public void stop() {
        running = false;
        thread.interrupt();
    }

    private void loop() {
        try {
            while (running) {
                long t0 = System.nanoTime();
                boolean[] acked = {false};
                SwingUtilities.invokeLater(() -> acked[0] = true);
                List<StackTraceElement[]> samples = new ArrayList<>();
                long lastSample = 0;
                while (!acked[0] && running) {
                    Thread.sleep(5);
                    long elapsed = (System.nanoTime() - t0) / 1_000_000;
                    if (elapsed >= thresholdMs && elapsed - lastSample >= SAMPLE_EVERY_MS && samples.size() < 8) {
                        Thread e = edt;
                        if (e != null) {
                            samples.add(e.getStackTrace());
                        }
                        lastSample = elapsed;
                    }
                }
                long total = (System.nanoTime() - t0) / 1_000_000;
                if (total >= thresholdMs && !samples.isEmpty()) {
                    write(total, samples);
                }
                Thread.sleep(SAMPLE_EVERY_MS);
            }
        } catch (InterruptedException e) {
            // остановлен
        }
    }

    private void write(long totalMs, List<StackTraceElement[]> samples) {
        StringBuilder sb = new StringBuilder();
        sb.append(LocalTime.now().withNano(0)).append("  зависание ").append(totalMs).append(" мс\n");
        int shown = 0;
        for (StackTraceElement[] st : samples) {
            if (shown++ >= 3) {
                break;
            }
            sb.append("  стек:\n");
            int lines = 0;
            boolean appFrameSeen = false;
            for (StackTraceElement f : st) {
                boolean app = f.getClassName().startsWith("com.vjstb");
                appFrameSeen |= app;
                if (lines < 6 || app) {
                    sb.append("    ").append(f).append('\n');
                    lines++;
                }
                if (lines >= 22 || (appFrameSeen && lines >= 14 && !app)) {
                    break;
                }
            }
        }
        try {
            Files.createDirectories(log.getParent());
            if (Files.exists(log) && Files.size(log) > MAX_LOG_BYTES) {
                Files.delete(log);
            }
            Files.writeString(log, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // журнал — удобство, а не часть работы
        }
    }
}
