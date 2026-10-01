package com.vjstb.ledscheme.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Запрос пользователя 2026-10-01: приветственный тур показывается снова после каждого
 *  обновления (там страница «Что нового» и переход к интерактивным сценариям — иначе
 *  человек сам их вряд ли найдёт). Раньше хватало одного флага {@code onboardingCompleted}:
 *  один раз пройденный тур больше не появлялся. Теперь учитывается ещё и версия, на которой
 *  его закрывали. */
class OnboardingDueTest {

    private SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void freshInstallShowsTheTour(@TempDir Path dir) {
        assertTrue(settings(dir).isOnboardingDue("2.6"));
    }

    @Test
    void afterFinishingOnThisVersionItIsNotShownAgain(@TempDir Path dir) {
        SettingsManager s = settings(dir);
        s.markOnboardingDone("2.6");

        assertFalse(s.isOnboardingDue("2.6"));
    }

    @Test
    void anUpdateShowsTheTourAgain(@TempDir Path dir) {
        SettingsManager s = settings(dir);
        s.markOnboardingDone("2.6");

        assertTrue(s.isOnboardingDue("2.7"));
    }

    @Test
    void settingsSavedBeforeTheVersionFieldExistedShowTheTourOnceAfterUpdate(@TempDir Path dir) {
        SettingsManager s = settings(dir);
        // файл настроек старой версии: тур уже пройден, а версии, на которой это случилось, нет
        s.setOnboardingCompleted(true);

        assertTrue(s.isOnboardingDue("2.6"));
        s.markOnboardingDone("2.6");
        assertFalse(s.isOnboardingDue("2.6"));
    }

    @Test
    void seenVersionSurvivesRestart(@TempDir Path dir) {
        settings(dir).markOnboardingDone("2.6");

        SettingsManager reloaded = settings(dir);
        assertFalse(reloaded.isOnboardingDue("2.6"));
        assertTrue(reloaded.isOnboardingDue("2.6.1"));
        assertEquals(true, reloaded.isOnboardingCompleted());
    }
}
