package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.settings.UserProfile;
import java.awt.Color;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Запрос пользователя 2026-09-30: «механизм палитры из общих схем, но память цветов масок —
 *  отдельная». Проверяет, что канал {@link RecentColorsChooserPanel.Channel#MASKS} не смешивается
 *  с цветами линий ни в памяти процесса, ни в профиле, и переживает перезапуск; прежний
 *  API (без канала) остаётся на линиях. */
class RecentMaskColorsChannelTest {

    @BeforeEach
    void reset() {
        RecentColorsChooserPanel.clearForTests();
    }

    private static SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void maskColorsAndLineColorsAreRememberedSeparately(@TempDir Path dir) {
        SettingsManager s = settings(dir);
        RecentColorsChooserPanel.remember(Color.RED, s, RecentColorsChooserPanel.Channel.MASKS);
        RecentColorsChooserPanel.remember(Color.BLUE, s); // прежний вызов -- линии

        assertEquals(List.of(Color.RED),
                RecentColorsChooserPanel.recentSnapshotForTests(RecentColorsChooserPanel.Channel.MASKS));
        assertEquals(List.of(Color.BLUE), RecentColorsChooserPanel.recentSnapshotForTests());
        assertEquals(List.of(Color.RED.getRGB()), s.activeProfile().getRecentMaskColors());
        assertEquals(List.of(Color.BLUE.getRGB()), s.activeProfile().getRecentLineColors());
    }

    @Test
    void loadPersistedSeedsOnlyTheRequestedChannel(@TempDir Path dir) {
        SettingsManager s = settings(dir);
        s.rememberRecentMaskColor(Color.GREEN.getRGB());
        s.rememberRecentLineColor(Color.YELLOW.getRGB());

        RecentColorsChooserPanel.loadPersisted(s, RecentColorsChooserPanel.Channel.MASKS);

        assertEquals(List.of(Color.GREEN),
                RecentColorsChooserPanel.recentSnapshotForTests(RecentColorsChooserPanel.Channel.MASKS));
        assertTrue(RecentColorsChooserPanel.recentSnapshotForTests().isEmpty(), "линии не подмешались");
    }

    @Test
    void maskColorsSurviveReloadNewestFirstAndAreCappedAtTwentyFour(@TempDir Path dir) {
        File file = new File(dir.toFile(), "settings.json");
        SettingsManager first = new SettingsManager(new SettingsStore(file));
        first.rememberRecentMaskColor(0xAA0000);
        first.rememberRecentMaskColor(0x00BB00);
        first.rememberRecentMaskColor(0xAA0000);
        SettingsManager reopened = new SettingsManager(new SettingsStore(file));
        assertEquals(List.of(0xAA0000, 0x00BB00), reopened.activeProfile().getRecentMaskColors());
        assertTrue(reopened.activeProfile().getRecentLineColors().isEmpty());

        for (int i = 0; i < 40; i++) {
            first.rememberRecentMaskColor(i);
        }
        assertTrue(first.activeProfile().getRecentMaskColors().size() <= 24);
    }

    @Test
    void profileCopyKeepsMaskColorsAndNewProfileReturnsEmptyList(@TempDir Path dir) {
        SettingsManager s = settings(dir);
        assertTrue(s.activeProfile().getRecentMaskColors().isEmpty());
        s.rememberRecentMaskColor(0x123456);
        UserProfile copy = s.activeProfile().copy();
        assertEquals(List.of(0x123456), copy.getRecentMaskColors());
    }
}
