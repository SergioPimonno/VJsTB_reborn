package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Дымовой Swing-тест (см. GuiSmokeTest/PowerStagePanelChainRowClickTest — строится
 *  реальный диалог, не показывается, ведётся через настоящие компоненты) для новой
 *  таблицы роли/транзита у групп разъёмов набираемой карты (docs/schema-ports-rework/
 *  PLAN.md, задача T5.1): "угаданная" роль курсивом с пометкой "(угадано)" против
 *  явно проставленной, и массовое назначение роли/транзита сразу нескольким
 *  выделенным группам. Package-private поля {@code CardsConfigDialog.pendingPorts}/
 *  {@code pendingList}/{@code roleCombo}/{@code thruCombo}/{@code connectorPicker}/
 *  {@code directionCombo}/{@code countSpinner} и методы {@code addPendingPort}/
 *  {@code pendingPortLabel}/{@code applyRoleToSelectedPending}/
 *  {@code applyThruToSelectedPending} открыты РОВНО для этого теста (тот же пакет). */
class CardsConfigDialogRoleThruTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    @Test
    void guessedRoleIsItalicWithHintAndExplicitRoleIsNot(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");

        SwingUtilities.invokeAndWait(() -> {
            AppModel model = freshModel(dir);
            EquipmentPreset preset = model.addEquipmentPreset(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER,
                    "MCTRL4K", "", List.of());
            CardsConfigDialog dlg = new CardsConfigDialog(null, "MCTRL4K",
                    CardsConfigDialog.forPreset(model, preset), model);

            // Контрольный случай PLAN.md §2.3: Cat6/RJ45 на CONTROLLER угадывается
            // как LED_DATA (без явной роли) — но группа с явной ролью показывает её
            // без пометки "(угадано)", даже если явная роль ПРОТИВОРЕЧИТ эвристике.
            CardPort guessed = new CardPort("Cat6/RJ45", PortDirection.OUT, 1);
            CardPort explicit = new CardPort("Cat6/RJ45", PortDirection.OUT, 1);
            explicit.setRole(InterfaceRole.CONTROL);
            dlg.pendingPorts.add(guessed);
            dlg.pendingPorts.add(explicit);

            String guessedLabel = dlg.pendingPortLabel(guessed);
            String explicitLabel = dlg.pendingPortLabel(explicit);
            assertTrue(guessedLabel.contains("LED-данные"), guessedLabel);
            assertTrue(guessedLabel.contains("(угадано)"), guessedLabel);
            assertTrue(explicitLabel.contains("Управление"), explicitLabel);
            assertFalse(explicitLabel.contains("(угадано)"), explicitLabel);

            dlg.dispose();
        });
    }

    @Test
    void massAssignRoleAndThruAppliesOnlyToSelectedPorts(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");

        SwingUtilities.invokeAndWait(() -> {
            AppModel model = freshModel(dir);
            EquipmentPreset preset = model.addEquipmentPreset(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER,
                    "MCTRL4K", "", List.of());
            CardsConfigDialog dlg = new CardsConfigDialog(null, "MCTRL4K",
                    CardsConfigDialog.forPreset(model, preset), model);

            CardPort a = new CardPort("HDMI", PortDirection.OUT, 1);
            CardPort b = new CardPort("SDI", PortDirection.OUT, 1);
            CardPort untouched = new CardPort("XLR", PortDirection.OUT, 1);
            dlg.pendingPorts.add(a);
            dlg.pendingPorts.add(b);
            dlg.pendingPorts.add(untouched);

            // Массовое назначение роли — только двум ИЗ ТРЁХ групп (docs/schema-
            // ports-rework/PLAN.md, задача T5.1: "Назначить роль выделенным").
            dlg.applyRoleToSelectedPending(List.of(a, b), InterfaceRole.SYNC);
            assertEquals(InterfaceRole.SYNC, a.getRole());
            assertEquals(InterfaceRole.SYNC, b.getRole());
            assertEquals(null, untouched.getRole(), "Не выделенная группа не должна была измениться");

            dlg.applyThruToSelectedPending(List.of(a), Boolean.TRUE);
            assertEquals(Boolean.TRUE, a.getThru());
            assertEquals(null, b.getThru(), "Транзит назначался только группе a, не b");

            // "по умолчанию"/"авто" (null) тоже должны сбрасывать обратно, а не
            // игнорироваться как "нет изменений".
            dlg.applyRoleToSelectedPending(List.of(a), null);
            assertEquals(null, a.getRole());

            dlg.dispose();
        });
    }

    @Test
    void addPendingPortUsesRoleAndThruCombosNotJustDefaults(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");

        SwingUtilities.invokeAndWait(() -> {
            AppModel model = freshModel(dir);
            EquipmentPreset preset = model.addEquipmentPreset(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER,
                    "MCTRL4K", "", List.of());
            CardsConfigDialog dlg = new CardsConfigDialog(null, "MCTRL4K",
                    CardsConfigDialog.forPreset(model, preset), model);

            dlg.connectorPicker.setValue("Genlock");
            dlg.directionCombo.setSelectedItem(PortDirection.OUT);
            dlg.countSpinner.setValue(1);
            dlg.roleCombo.setSelectedItem(InterfaceRole.SYNC);
            dlg.thruCombo.setSelectedItem(Boolean.TRUE);
            dlg.addPendingPort();

            assertEquals(1, dlg.pendingPorts.size());
            CardPort added = dlg.pendingPorts.get(0);
            assertEquals("Genlock", added.getConnectorType());
            assertEquals(InterfaceRole.SYNC, added.getRole());
            assertEquals(Boolean.TRUE, added.getThru());

            // Сентинелы комбобоксов (null) — группа наследует роль/транзит из
            // библиотеки/эвристики/авто-угадывания, а не получает "явные" значения
            // просто потому, что комбобокс был на сентинеле в момент добавления.
            dlg.roleCombo.setSelectedItem(null);
            dlg.thruCombo.setSelectedItem(null);
            dlg.addPendingPort();
            CardPort secondAdded = dlg.pendingPorts.get(1);
            assertEquals(null, secondAdded.getRole());
            assertEquals(null, secondAdded.getThru());

            dlg.dispose();
        });
    }
}
