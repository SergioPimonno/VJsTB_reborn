package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import java.awt.Color;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Мост между {@link RecentColorsChooserPanel} (общий {@code static}-список в
 *  памяти процесса, см. {@link RecentColorsChooserPanelTest}) и {@link
 *  SettingsManager} (персистентное хранилище) — баг-репорт (уточнение
 *  2026-09-17): "палитру нужно сохранять между перезапусками", про цвета линий
 *  соединений/цепочек расключения. */
class RecentColorsChooserPanelPersistenceTest {

    @BeforeEach
    void reset() {
        RecentColorsChooserPanel.clearForTests();
    }

    private SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void rememberWithSettingsAlsoPersistsToProfile(@TempDir Path dir) {
        SettingsManager settings = settings(dir);
        RecentColorsChooserPanel.remember(Color.RED, settings);

        assertEquals(List.of(Color.RED), RecentColorsChooserPanel.recentSnapshotForTests());
        assertEquals(List.of(Color.RED.getRGB()), settings.activeProfile().getRecentLineColors());
    }

    @Test
    void loadPersistedSeedsTheInMemoryListFromThePersistedProfile(@TempDir Path dir) {
        SettingsManager settings = settings(dir);
        settings.rememberRecentLineColor(Color.BLUE.getRGB());
        settings.rememberRecentLineColor(Color.GREEN.getRGB());

        // RECENT пуст (сброшен в @BeforeEach) — как при первом обращении к диалогу
        // за эту сессию приложения.
        RecentColorsChooserPanel.loadPersisted(settings);

        assertEquals(List.of(Color.GREEN, Color.BLUE), RecentColorsChooserPanel.recentSnapshotForTests());
    }

    @Test
    void loadPersistedDoesNotOverwriteColorsAlreadyAccumulatedThisSession(@TempDir Path dir) {
        SettingsManager settings = settings(dir);
        settings.rememberRecentLineColor(Color.BLUE.getRGB());

        // Что-то уже выбрали В ЭТОЙ сессии, ДО повторного открытия диалога —
        // loadPersisted не должен затирать это старым списком из профиля.
        RecentColorsChooserPanel.remember(Color.RED);
        RecentColorsChooserPanel.loadPersisted(settings);

        assertEquals(List.of(Color.RED), RecentColorsChooserPanel.recentSnapshotForTests());
    }
}
