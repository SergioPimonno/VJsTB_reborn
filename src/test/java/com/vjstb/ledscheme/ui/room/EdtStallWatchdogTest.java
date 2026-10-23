package com.vjstb.ledscheme.ui.room;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Сторож зависаний: долгое задание в потоке интерфейса попадает в журнал вместе с методом, где оно «сидело». */
class EdtStallWatchdogTest {

    /** Намеренно долгий метод — по его имени проверяем, что сторож назвал виновника. */
    private static void slowOnTheEventThread() {
        long end = System.currentTimeMillis() + 250;
        while (System.currentTimeMillis() < end) {
            Thread.onSpinWait();
        }
    }

    @Test
    void aSlowEventIsLoggedWithItsStack(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("logs").resolve("stalls.log");
        EdtStallWatchdog w = EdtStallWatchdog.start(log, 60);
        try {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(120);
            SwingUtilities.invokeAndWait(EdtStallWatchdogTest::slowOnTheEventThread);
            Thread.sleep(300);
        } finally {
            w.stop();
        }
        assertTrue(Files.exists(log), "зависание записано");
        String text = Files.readString(log);
        assertTrue(text.contains("зависание"), text);
        assertTrue(text.contains("slowOnTheEventThread"), "в журнале назван метод, который держал поток: " + text);
    }

    @Test
    void aQuietInterfaceWritesNothing(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("quiet.log");
        EdtStallWatchdog w = EdtStallWatchdog.start(log, 60);
        try {
            for (int i = 0; i < 10; i++) {
                SwingUtilities.invokeAndWait(() -> { });
                Thread.sleep(30);
            }
        } finally {
            w.stop();
        }
        assertTrue(!Files.exists(log), "без зависаний журнал не создаётся");
    }
}
