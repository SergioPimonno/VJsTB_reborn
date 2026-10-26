package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ModuleIntegrity;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Настройки модульных экранов (docs/receiver-cards/PLAN.md, 6.4) и подтверждение при снятии флага модуля у типа:
 * цвет «Приёмные карты» в профиле и палитре, «Обозначение номера модуля» и «Нумерация приёмных карт» в окне
 * «Предпочтения», текст подтверждения «N карт, M блоков и их цепочки будут удалены».
 */
class ReceiverCardSettingsUiTest {

    private static SettingsManager newSettings(File dir) {
        return new SettingsManager(new SettingsStore(new File(dir, "settings.json")));
    }

    @Test
    void receiverCardColorIsStoredInProfileAndAppliedToPalette(@TempDir Path dir) {
        SettingsManager settings = newSettings(dir.toFile());
        try {
            settings.setReceiverCardColor(0x112233);
            Palette.applyProfile(settings.activeProfile());
            assertEquals(new Color(0x112233), Palette.RECEIVER_CARD);
            // переживает перезапуск
            assertEquals(Integer.valueOf(0x112233), newSettings(dir.toFile()).activeProfile().getReceiverCardColor());

            settings.setReceiverCardColor(null);
            Palette.applyProfile(settings.activeProfile());
            assertEquals(new Color(0x639922), Palette.RECEIVER_CARD, "сброс — встроенный цвет");
        } finally {
            settings.setReceiverCardColor(null);
            Palette.applyProfile(settings.activeProfile());
        }
    }

    @Test
    void moduleNumberFormatAndCardNumberingSurviveRestart(@TempDir Path dir) {
        SettingsManager settings = newSettings(dir.toFile());
        settings.setModuleNumberFormat("J{n}");
        settings.setReceiverCardNumbering(ReceiverCardNumbering.TR_ROWS);
        SettingsManager again = newSettings(dir.toFile());
        assertEquals("J{n}", again.activeProfile().getModuleNumberFormat());
        assertEquals(ReceiverCardNumbering.TR_ROWS, again.activeProfile().getReceiverCardNumbering());
    }

    @Test
    void preferencesDialogEditsFormatAndNumbering(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        SettingsManager settings = newSettings(dir.toFile());
        SwingUtilities.invokeAndWait(() -> {
            PreferencesDialog dlg = new PreferencesDialog(null, settings);
            try {
                @SuppressWarnings("unchecked")
                JComboBox<String> format = (JComboBox<String>) find(dlg, c -> c instanceof JComboBox<?> b
                        && b.isEditable() && b.getItemCount() > 0 && "{n}".equals(b.getItemAt(0)));
                @SuppressWarnings("unchecked")
                JComboBox<ReceiverCardNumbering> numbering = (JComboBox<ReceiverCardNumbering>) find(dlg,
                        c -> c instanceof JComboBox<?> b && b.getItemCount() > 0
                                && b.getItemAt(0) instanceof ReceiverCardNumbering);
                assertNotNull(format, "в «Предпочтениях» есть «Обозначение номера модуля»");
                assertNotNull(numbering, "и «Нумерация приёмных карт»");
                assertEquals(ReceiverCardNumbering.values().length, numbering.getItemCount());

                format.setSelectedItem("{card}.{n}");
                assertEquals("{card}.{n}", settings.activeProfile().getModuleNumberFormat());
                numbering.setSelectedItem(ReceiverCardNumbering.BR_COLUMNS);
                assertEquals(ReceiverCardNumbering.BR_COLUMNS, settings.activeProfile().getReceiverCardNumbering());
            } finally {
                dlg.dispose();
            }
        });
    }

    @Test
    void personalizationDialogBuildsWithReceiverCardColorRow(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        SettingsManager settings = newSettings(dir.toFile());
        SwingUtilities.invokeAndWait(() -> {
            PersonalizationDialog dlg = new PersonalizationDialog(null, settings);
            try {
                assertNotNull(find(dlg, c -> c instanceof javax.swing.JLabel l
                        && l.getText() != null && l.getText().startsWith("Приёмные карты")));
            } finally {
                dlg.dispose();
            }
        });
    }

    @Test
    void flagRemovalConfirmationNamesCardsAndBlocks(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
        CabinetType module = new CabinetType();
        module.setName("Модуль");
        module.setWidthMm(250);
        module.setHeightMm(250);
        module.setResolutionWidth(64);
        module.setResolutionHeight(64);
        module.setReceiverCardSeparate(true);
        model.addCabinetType(module);
        ReceiverCardType card = model.addReceiverCardType(rcType());
        PowerSupplyType psu = model.addPowerSupplyType(psType());
        model.selectProject(model.addProject("П"));
        model.selectScene(model.addScene("С"));
        model.selectScreen(model.addScreen("Э", module.getId(), 2, 4, 0, 0));

        assertFalse(model.moduleFlagRemovalImpact(module.getId()).any(), "пока карт и блоков нет — спрашивать не о чем");

        model.assignReceiverCardsManual(new GridRect(0, 0, 0, 1), card.getId());
        model.assignReceiverCardsManual(new GridRect(0, 2, 0, 3), card.getId());
        model.assignPowerSuppliesManual(new GridRect(0, 0, 1, 3), psu.getId());
        ModuleIntegrity.Impact impact = model.moduleFlagRemovalImpact(module.getId());
        assertTrue(impact.any());
        String text = CabinetTypeDialog.removalMessage(impact);
        assertTrue(text.contains("2 карты, 1 блок питания и их цепочки будут удалены"), text);
    }

    @Test
    void flagRemovalMessageHandlesPluralsAndShrunkParts() {
        String one = CabinetTypeDialog.removalMessage(new ModuleIntegrity.Impact(1, 0, 0, 0));
        assertTrue(one.contains("1 карта и их цепочки будут удалены"), one);
        String many = CabinetTypeDialog.removalMessage(new ModuleIntegrity.Impact(5, 3, 2, 1));
        assertTrue(many.contains("5 карт, 3 блока питания и их цепочки будут удалены"), many);
        assertTrue(many.contains("2 карты, 1 блок питания"), many);
    }

    private static ReceiverCardType rcType() {
        ReceiverCardType t = new ReceiverCardType();
        t.setName("К");
        t.setModuleOutputs(8);
        return t;
    }

    private static PowerSupplyType psType() {
        PowerSupplyType t = new PowerSupplyType();
        t.setName("Б");
        t.setOutputs(8);
        return t;
    }

    private static Component find(Container root, java.util.function.Predicate<Component> match) {
        for (Component c : root.getComponents()) {
            if (match.test(c)) {
                return c;
            }
            if (c instanceof Container cc) {
                Component found = find(cc, match);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
