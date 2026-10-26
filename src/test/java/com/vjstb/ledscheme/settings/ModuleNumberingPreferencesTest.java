package com.vjstb.ledscheme.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Предпочтения модульных экранов (D14, D15): «Обозначение номера модуля» и «Нумерация приёмных карт». Окно настроек
 * (трек C плана receiver-cards) только читает и пишет эти поля, поэтому здесь стережётся то, на что оно рассчитывает:
 * значения по умолчанию, замена пустого/неизвестного на умолчание и сохранение между запусками (баг-репорт
 * 2026-10-03 про «вход связей» показал, как легко поле профиля не попасть в settings.json).
 */
class ModuleNumberingPreferencesTest {

    private static SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void defaultsAreThePlainNumberAndLeftToRightTopToBottom(@TempDir Path dir) {
        UserProfile p = settings(dir).activeProfile();
        assertEquals("{n}", p.getModuleNumberFormat());
        assertEquals(ReceiverCardNumbering.TL_ROWS, p.getReceiverCardNumbering());
    }

    @Test
    void blankFormatFallsBackToThePlainNumber() {
        UserProfile p = new UserProfile();
        p.setModuleNumberFormat("   ");
        assertEquals("{n}", p.getModuleNumberFormat());
        p.setModuleNumberFormat(null);
        assertEquals("{n}", p.getModuleNumberFormat());
        p.setReceiverCardNumbering(null);
        assertEquals(ReceiverCardNumbering.DEFAULT, p.getReceiverCardNumbering());
    }

    @Test
    void choicesSurviveARestartAndACopyOfTheProfile(@TempDir Path dir) {
        SettingsManager s = settings(dir);
        s.setModuleNumberFormat("{card}.{n}");
        s.setReceiverCardNumbering(ReceiverCardNumbering.AS_MODULES);

        UserProfile back = settings(dir).activeProfile();
        assertEquals("{card}.{n}", back.getModuleNumberFormat());
        assertEquals(ReceiverCardNumbering.AS_MODULES, back.getReceiverCardNumbering());
        assertEquals("{card}.{n}", back.copy().getModuleNumberFormat());
        assertEquals(ReceiverCardNumbering.AS_MODULES, back.copy().getReceiverCardNumbering());
    }

    @Test
    void profileWrittenBeforeTheFieldsExistedOpensWithDefaults(@TempDir Path dir) throws Exception {
        File f = new File(dir.toFile(), "settings.json");
        Files.writeString(f.toPath(), "{\"profiles\":[{\"id\":\"p1\",\"name\":\"Старый\"}],\"activeProfileId\":\"p1\"}");

        UserProfile p = new SettingsManager(new SettingsStore(f)).activeProfile();

        assertEquals("{n}", p.getModuleNumberFormat());
        assertEquals(ReceiverCardNumbering.TL_ROWS, p.getReceiverCardNumbering());
    }
}
