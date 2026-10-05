package com.vjstb.ledscheme.ui.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.swing.JCheckBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Запрос 2026-10-05: этап «Вывод» — таблица «Состав вывода»: название части, галочка «В общий вывод» и
 *  кнопка отдельного экспорта; пакет документации выгружает отмеченное. Выбор переживает перезапуск. До
 *  галочек в пакет входили питание, сигнал, маски, транспорт, сводка и спецификация — они и остаются
 *  включёнными по умолчанию; таблица экранов и пресеты (Resolume, After Effects) — только по желанию. */
class OutputStagePartsTableTest {

    private static SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    private static void collectChecks(Container c, List<JCheckBox> out) {
        for (Component k : c.getComponents()) {
            if (k instanceof JCheckBox box) {
                out.add(box);
            }
            if (k instanceof Container cc) {
                collectChecks(cc, out);
            }
        }
    }

    @Test
    void defaultsKeepTheFormerPackageContentsAndLeaveNewPartsOff() {
        assertEquals(EnumSet.of(OutputPart.POWER, OutputPart.SIGNAL, OutputPart.MASKS, OutputPart.TRANSPORT,
                OutputPart.REPORT, OutputPart.SPEC), OutputPart.defaults());
        assertEquals(OutputPart.defaults(), OutputPart.fromNames(null), "выбор не делали — состав по умолчанию");
        assertTrue(OutputPart.fromNames(List.of()).isEmpty(), "пустой сохранённый выбор — не отмечено ничего");
        assertEquals(EnumSet.of(OutputPart.SCREENS), OutputPart.fromNames(List.of("SCREENS", "FROM_THE_FUTURE")),
                "неизвестные имена пропускаются");
        assertEquals(List.of("POWER", "SPEC"), OutputPart.toNames(EnumSet.of(OutputPart.SPEC, OutputPart.POWER)));
    }

    @Test
    void tableShowsOneCheckPerPartAndSavesTheChoice(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        SettingsManager settings = settings(dir);
        OutputStagePanel panel = new OutputStagePanel(model, settings);

        List<JCheckBox> checks = new ArrayList<>();
        collectChecks(panel, checks);
        assertEquals(OutputPart.values().length, checks.size());
        for (int i = 0; i < checks.size(); i++) {
            assertEquals(OutputPart.defaults().contains(OutputPart.values()[i]), checks.get(i).isSelected(),
                    OutputPart.values()[i].name());
        }
        assertNull(settings.activeProfile().getOutputParts(), "пока галочки не трогали, в профиле ничего нет");

        JCheckBox screens = checks.get(OutputPart.SCREENS.ordinal());
        screens.doClick();
        JCheckBox power = checks.get(OutputPart.POWER.ordinal());
        power.doClick();

        SettingsManager reloaded = settings(dir);
        assertTrue(OutputPart.fromNames(reloaded.activeProfile().getOutputParts()).contains(OutputPart.SCREENS));
        assertFalse(OutputPart.fromNames(reloaded.activeProfile().getOutputParts()).contains(OutputPart.POWER));
        assertTrue(OutputPart.fromNames(reloaded.activeProfile().copy().getOutputParts())
                .contains(OutputPart.MASKS), "copy() переносит выбор");
    }
}
