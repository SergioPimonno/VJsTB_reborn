package com.vjstb.ledscheme.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Баг-репорт (уточнение 2026-09-17): "палитру нужно сохранять между
 *  перезапусками" — про переопределение цветов ЛИНИЙ соединений в общей схеме и
 *  цепочек расключения ({@code RecentColorsChooserPanel}, до этой правки — чисто
 *  {@code static} в памяти процесса, см. её собственный class-javadoc), не про
 *  типы кабинетов. {@link SettingsManager#rememberRecentLineColor} — персистентная
 *  часть; сама подгрузка/использование в диалоге — {@code RecentColorsChooserPanel
 *  .loadPersisted}/{@code remember(Color, SettingsManager)}, покрыто отдельным
 *  тестом в пакете {@code ui} (тот пакет уже держит {@code
 *  RecentColorsChooserPanelTest} для чисто-статической части). */
class RecentLineColorPersistenceTest {

    private SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void rememberedColorPersistsNewestFirst(@TempDir Path dir) {
        SettingsManager s = settings(dir);
        s.rememberRecentLineColor(0xFF0000);
        s.rememberRecentLineColor(0x00FF00);
        s.rememberRecentLineColor(0x0000FF);

        assertEquals(List.of(0x0000FF, 0x00FF00, 0xFF0000), s.activeProfile().getRecentLineColors());
    }

    @Test
    void rememberingTheSameColorAgainMovesItToFrontInsteadOfDuplicating(@TempDir Path dir) {
        SettingsManager s = settings(dir);
        s.rememberRecentLineColor(0xFF0000);
        s.rememberRecentLineColor(0x00FF00);
        s.rememberRecentLineColor(0xFF0000);

        assertEquals(List.of(0xFF0000, 0x00FF00), s.activeProfile().getRecentLineColors());
    }

    @Test
    void listIsCappedAtTwentyFour(@TempDir Path dir) {
        SettingsManager s = settings(dir);
        for (int i = 0; i < 40; i++) {
            s.rememberRecentLineColor(i);
        }
        assertTrue(s.activeProfile().getRecentLineColors().size() <= 24);
    }

    @Test
    void survivesReloadingSettingsFromDisk(@TempDir Path dir) {
        File file = new File(dir.toFile(), "settings.json");
        SettingsManager first = new SettingsManager(new SettingsStore(file));
        first.rememberRecentLineColor(0xABCDEF);

        // Новый SettingsManager на ТОМ ЖЕ файле — имитирует перезапуск приложения.
        SettingsManager reopened = new SettingsManager(new SettingsStore(file));
        assertEquals(List.of(0xABCDEF), reopened.activeProfile().getRecentLineColors(),
                "список должен переживать перезапуск (пересоздание SettingsManager на том же файле)");
    }

    @Test
    void newProfileWithoutAnyRecentColorsReturnsEmptyListNotNull(@TempDir Path dir) {
        SettingsManager s = settings(dir);
        assertTrue(s.activeProfile().getRecentLineColors().isEmpty());
    }

    @Test
    void copyPreservesRecentLineColors(@TempDir Path dir) {
        SettingsManager s = settings(dir);
        s.rememberRecentLineColor(0x123456);
        UserProfile copy = s.activeProfile().copy();
        assertEquals(List.of(0x123456), copy.getRecentLineColors());
    }
}
