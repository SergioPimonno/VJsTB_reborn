package com.vjstb.ledscheme.ui.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.ui.room.ControlRoomExporter.View;
import java.io.File;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Выбор видов Планера пультовой для вывода: значения по умолчанию, запоминание в профиле, неизвестные имена. */
class ControlRoomExportOptionsTest {

    private static SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void withoutSavedChoiceTheMainViewsAndTheSpecAreOn(@TempDir Path dir) {
        ControlRoomExportOptions o = ControlRoomExportOptions.fromProfile(settings(dir));
        assertEquals(EnumSet.of(View.RACKS_FRONT, View.PLAN, View.ELEVATION), o.views());
        assertTrue(o.spec());
        assertFalse(o.isEmpty());
    }

    @Test
    void choiceIsSavedInTheProfileAndSurvivesARestart(@TempDir Path dir) {
        settings(dir).setControlRoomExportOptions(List.of("PLAN", "RACKS_TOP"), false);
        ControlRoomExportOptions back = ControlRoomExportOptions.fromProfile(settings(dir));
        assertEquals(EnumSet.of(View.PLAN, View.RACKS_TOP), back.views());
        assertFalse(back.spec());
        assertTrue(settings(dir).activeProfile().copy().getControlRoomExportViews().contains("PLAN"),
                "copy() профиля переносит выбор");
    }

    @Test
    void unknownNamesFromAFutureVersionAreSkippedAndNothingChosenMeansEmpty(@TempDir Path dir) {
        settings(dir).setControlRoomExportOptions(List.of("PLAN", "VIEW_FROM_THE_FUTURE"), false);
        assertEquals(EnumSet.of(View.PLAN), ControlRoomExportOptions.fromProfile(settings(dir)).views());
        settings(dir).setControlRoomExportOptions(List.of(), false);
        assertTrue(ControlRoomExportOptions.fromProfile(settings(dir)).isEmpty());
    }

    @Test
    void theNewPartIsOffByDefaultSoTheUsualPackageDoesNotGrow() {
        assertFalse(OutputPart.defaults().contains(OutputPart.CONTROL_ROOM));
        assertTrue(OutputPart.fromNames(List.of("CONTROL_ROOM")).contains(OutputPart.CONTROL_ROOM));
    }
}
