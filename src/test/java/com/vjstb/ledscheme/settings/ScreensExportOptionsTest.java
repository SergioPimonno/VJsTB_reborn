package com.vjstb.ledscheme.settings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Запрос 2026-10-02: диалог экспорта таблицы экранов спрашивает, печатать ли легенду кабинетов
 *  и/или статистику по сцене, и запоминает выбор в профиле (по умолчанию — печатать оба блока). */
class ScreensExportOptionsTest {

    private SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void defaultsPrintBothAndTheChoiceSurvivesRestart(@TempDir Path dir) {
        SettingsManager s = settings(dir);
        assertTrue(s.activeProfile().isScreensExportLegend());
        assertTrue(s.activeProfile().isScreensExportStats());

        s.setScreensExportOptions(false, true);

        SettingsManager reloaded = settings(dir);
        assertFalse(reloaded.activeProfile().isScreensExportLegend());
        assertTrue(reloaded.activeProfile().isScreensExportStats());
        assertFalse(reloaded.activeProfile().copy().isScreensExportLegend(), "copy() переносит выбор");
    }
}
