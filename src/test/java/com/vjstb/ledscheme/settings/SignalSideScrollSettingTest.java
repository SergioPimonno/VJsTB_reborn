package com.vjstb.ledscheme.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Скорость прокрутки боковой панели этапа «Сигнал» ({@link
 *  UserProfile#getSignalSideScrollUnitPx()}) — баг-репорт: у контроллера со многими
 *  картами панель прокручивалась колесом ~3 px за щелчок (Swing-шаг по умолчанию
 *  1 px на единицу). */
class SignalSideScrollSettingTest {

    private SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void defaultMatchesOtherWindowsStep(@TempDir Path dir) {
        assertEquals(16, settings(dir).activeProfile().getSignalSideScrollUnitPx());
    }

    @Test
    void valueIsClampedAndPersistsAcrossReload(@TempDir Path dir) {
        SettingsManager s = settings(dir);
        s.setSignalSideScrollUnitPx(0);
        assertEquals(1, s.activeProfile().getSignalSideScrollUnitPx());
        s.setSignalSideScrollUnitPx(9999);
        assertEquals(200, s.activeProfile().getSignalSideScrollUnitPx());

        s.setSignalSideScrollUnitPx(48);
        assertEquals(48, settings(dir).activeProfile().getSignalSideScrollUnitPx());
    }
}
