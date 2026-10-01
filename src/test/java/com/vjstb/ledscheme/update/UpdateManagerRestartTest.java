package com.vjstb.ledscheme.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Запрос пользователя 2026-09-30 (v2.6, пункт 7 — «программа не открывается,
 * если папку положить на диск D:»). Перезапуск/автообновление всегда звали
 * {@code javaw -jar}, а в jpackage-сборке системной Java может не быть, поэтому
 * приложение после «Перезапустить» не поднималось. Здесь закреплено: при заданном
 * {@code jpackage.app-path} команда — сам лаунчер; без него — прежнее
 * {@code javaw -jar} (запуск из обычного jar не изменился); а .bat автообновления
 * остаётся чисто ASCII, пути приходят через переменные окружения (кириллица в
 * пути раньше превращалась в «?» при записи скрипта в US_ASCII).
 */
class UpdateManagerRestartTest {

    @Test
    void restartUsesJpackageLauncherWhenKnown() {
        Path launcher = Path.of("D:\\Программы\\AVE ToolBox\\AVE_ToolBox.exe");
        Path jar = Path.of("D:\\Программы\\AVE ToolBox\\app\\led-scheme.jar");
        List<String> cmd = UpdateManager.restartCommand(jar, launcher);
        assertEquals(List.of(launcher.toAbsolutePath().toString()), cmd);
    }

    @Test
    void restartFallsBackToJavawJarOutsideJpackage() {
        Path jar = Path.of("C:\\tools\\led-scheme.jar");
        List<String> cmd = UpdateManager.restartCommand(jar, null);
        assertEquals(List.of("javaw", "-jar", jar.toAbsolutePath().toString()), cmd);
    }

    @Test
    void restartWithoutJarAndLauncherIsRejected() {
        assertThrows(IllegalStateException.class, () -> UpdateManager.restartCommand(null, null));
    }

    @Test
    void launcherPropertyIgnoredWhenBlankOrFileMissing(@TempDir Path dir) throws IOException {
        assertNull(UpdateManager.launcherFromProperty(null));
        assertNull(UpdateManager.launcherFromProperty("  "));
        assertNull(UpdateManager.launcherFromProperty(dir.resolve("missing.exe").toString()));
        Path exe = Files.createFile(dir.resolve("AVE_ToolBox.exe"));
        assertEquals(exe, UpdateManager.launcherFromProperty(exe.toString()));
    }

    @Test
    void updateScriptIsAsciiAndTakesPathsFromEnvironment() {
        for (boolean viaLauncher : new boolean[] {false, true}) {
            String script = UpdateManager.buildUpdateScript(1234, viaLauncher);
            assertTrue(script.chars().allMatch(c -> c < 128),
                    "скрипт пишется в US_ASCII — любой не-ASCII символ превратился бы в '?'");
            assertTrue(script.contains("%" + UpdateManager.ENV_UPDATE_TARGET + "%"));
            assertTrue(script.contains("%" + UpdateManager.ENV_UPDATE_SOURCE + "%"));
        }
    }

    @Test
    void updateScriptRelaunchesViaLauncherOnlyWhenRequested() {
        String viaLauncher = UpdateManager.buildUpdateScript(1, true);
        assertTrue(viaLauncher.contains("start \"\" \"%" + UpdateManager.ENV_UPDATE_LAUNCHER + "%\""), viaLauncher);
        assertFalse(viaLauncher.contains("javaw"), "в jpackage-сборке системной Java может не быть");

        String viaJavaw = UpdateManager.buildUpdateScript(1, false);
        assertTrue(viaJavaw.contains("start \"\" javaw -jar %TARGET%"), viaJavaw);
        assertFalse(viaJavaw.contains(UpdateManager.ENV_UPDATE_LAUNCHER));
    }

    @Test
    void updateScriptWaitsForTheGivenPid() {
        assertTrue(UpdateManager.buildUpdateScript(98765, false).contains("PID eq 98765"));
    }
}
