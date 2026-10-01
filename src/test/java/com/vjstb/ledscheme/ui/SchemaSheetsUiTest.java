package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.SchemaSheet;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * UI нескольких схем на сцену (запрос пользователя 2026-09-30, пункт 8,
 * docs/masks-and-schema-sheets/PLAN.md, трек C2): строка {@link ContextBar} в режиме
 * «схема» (вместо «Экран:» — «Схема:» со списком схем режима), проверка названия в
 * {@link SchemaSheetNameDialog} (без окна — статический {@code problemOf}) и сброс
 * выделения холста при смене текущей схемы ({@link
 * SchemaCanvasPanel#syncWithCurrentSheet}) — иначе Delete удалил бы по выделению
 * блоки, которых на экране уже нет. Всё без показа окон, поэтому работает и в headless.
 */
class SchemaSheetsUiTest {

    private static AppModel model(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        return model;
    }

    private static List<SchemaSheet> comboItems(ContextBar bar) {
        List<SchemaSheet> out = new ArrayList<>();
        for (int i = 0; i < bar.schemaComboForTest().getItemCount(); i++) {
            out.add(bar.schemaComboForTest().getItemAt(i));
        }
        return out;
    }

    // ---- ContextBar ----

    @Test
    void defaultModeShowsScreenComboAndHidesSchemaCombo(@TempDir Path dir) {
        ContextBar bar = new ContextBar(model(dir), true);

        assertTrue(bar.screenComboVisibleForTest());
        assertFalse(bar.schemaComboVisibleForTest());
        assertNull(bar.getSchemaMode());
    }

    @Test
    void schemaModeSwapsScreenComboForSchemaCombo(@TempDir Path dir) {
        ContextBar bar = new ContextBar(model(dir), true);

        bar.setSchemaMode(SchemaMode.POWER);
        assertFalse(bar.screenComboVisibleForTest(), "«Экран:» в виде общей схемы не нужен");
        assertTrue(bar.schemaComboVisibleForTest());

        bar.setSchemaMode(null);
        assertTrue(bar.screenComboVisibleForTest());
        assertFalse(bar.schemaComboVisibleForTest());
    }

    @Test
    void barWithoutScreenNeverShowsScreenCombo(@TempDir Path dir) {
        // Этапы «Маски»/«Вывод» создают строку без экрана.
        ContextBar bar = new ContextBar(model(dir), false);
        assertFalse(bar.screenComboVisibleForTest());

        bar.setSchemaMode(SchemaMode.POWER);
        assertFalse(bar.screenComboVisibleForTest());
        bar.setSchemaMode(null);
        assertFalse(bar.screenComboVisibleForTest(), "возврат из режима схемы не должен «включать» экран там, где его нет");
    }

    @Test
    void schemaComboListsSheetsOfTheModeInOrderAndSelectsCurrent(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaSheet second = model.addSchemaSheet(SchemaMode.POWER, "Резерв");
        model.addSchemaSheet(SchemaMode.SIGNAL, "Сигнал B");
        ContextBar bar = new ContextBar(model, true);

        bar.setSchemaMode(SchemaMode.POWER);

        assertEquals(model.schemaSheets(SchemaMode.POWER), comboItems(bar));
        assertEquals(2, comboItems(bar).size(), "сигнальные схемы в списке питания не показываются");
        assertEquals(second.getId(), ((SchemaSheet) bar.schemaComboForTest().getSelectedItem()).getId(),
                "новая схема становится текущей — она же выбрана в списке");

        bar.setSchemaMode(SchemaMode.SIGNAL);
        assertEquals(model.schemaSheets(SchemaMode.SIGNAL), comboItems(bar));
    }

    @Test
    void choosingSheetInComboMakesItCurrentInModel(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaSheet first = model.schemaSheets(SchemaMode.POWER).get(0);
        model.addSchemaSheet(SchemaMode.POWER, "Резерв");
        ContextBar bar = new ContextBar(model, true);
        bar.setSchemaMode(SchemaMode.POWER);

        bar.schemaComboForTest().setSelectedIndex(0);

        assertEquals(first.getId(), model.currentSchemaSheet(SchemaMode.POWER).getId());
    }

    @Test
    void modelChangesRefreshTheSchemaCombo(@TempDir Path dir) {
        AppModel model = model(dir);
        ContextBar bar = new ContextBar(model, true);
        bar.setSchemaMode(SchemaMode.SIGNAL);
        assertEquals(1, comboItems(bar).size());

        SchemaSheet added = model.addSchemaSheet(SchemaMode.SIGNAL, "Зона B");
        assertEquals(2, comboItems(bar).size());
        model.renameSchemaSheet(added, "Зона C");
        assertEquals("Зона C", comboItems(bar).get(1).getName());
    }

    @Test
    void deleteButtonIsDisabledForTheOnlySheetAndEnabledOtherwise(@TempDir Path dir) {
        AppModel model = model(dir);
        ContextBar bar = new ContextBar(model, true);
        bar.setSchemaMode(SchemaMode.POWER);
        assertFalse(bar.schemaDeleteButtonForTest().isEnabled(), "единственную схему режима удалить нельзя");
        assertNotNull(bar.schemaDeleteButtonForTest().getToolTipText());

        model.addSchemaSheet(SchemaMode.POWER, "Резерв");
        assertTrue(bar.schemaDeleteButtonForTest().isEnabled());

        model.deleteSchemaSheet(model.currentSchemaSheet(SchemaMode.POWER));
        assertFalse(bar.schemaDeleteButtonForTest().isEnabled());
    }

    // ---- диалог названия ----

    @Test
    void nameProblemRejectsEmptyAndDuplicateNamesWithinTheMode(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaSheet first = model.schemaSheets(SchemaMode.POWER).get(0);
        String duplicate = first.getName();

        assertNotNull(SchemaSheetNameDialog.problemOf(model, SchemaMode.POWER, "   ", null));
        assertNotNull(SchemaSheetNameDialog.problemOf(model, SchemaMode.POWER, duplicate.toUpperCase(), null),
                "совпадение без учёта регистра — тоже дубль");
        assertNull(SchemaSheetNameDialog.problemOf(model, SchemaMode.POWER, "Резерв", null));
    }

    @Test
    void nameProblemIgnoresTheSheetBeingRenamedAndOtherModes(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaSheet power = model.schemaSheets(SchemaMode.POWER).get(0);

        assertNull(SchemaSheetNameDialog.problemOf(model, SchemaMode.POWER, power.getName(), power),
                "оставить прежнее имя при переименовании — не ошибка");
        assertNull(SchemaSheetNameDialog.problemOf(model, SchemaMode.SIGNAL, power.getName(), null),
                "имя схемы питания не занято в режиме сигнала");
    }

    @Test
    void nameProblemTextMatchesWhatModelWouldThrow(@TempDir Path dir) {
        AppModel model = model(dir);
        String name = model.schemaSheets(SchemaMode.POWER).get(0).getName();

        String problem = SchemaSheetNameDialog.problemOf(model, SchemaMode.POWER, name, null);
        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> model.addSchemaSheet(SchemaMode.POWER, name));
        assertEquals(ex.getMessage(), problem, "диалог показывает ту же ошибку, что бросит модель");
    }

    // ---- сброс выделения холста при смене схемы ----

    @Test
    void switchingSheetClearsCanvasSelectionSoDeleteCannotHitTheOtherSheet(@TempDir Path dir) {
        AppModel model = model(dir);
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settings);

        SchemaSheet first = model.currentSchemaSheet(SchemaMode.SIGNAL);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 0, null);
        CardPort out = model.addCardToNode(a, "Видео", List.of(new CardPort("HDMI", PortDirection.OUT, 1)))
                .getPorts().get(0);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 300, 0, null);
        CardPort in = model.addCardToNode(b, "Видео", List.of(new CardPort("HDMI", PortDirection.IN, 1)))
                .getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), out.getId(), b.getId(), in.getId(), null);
        canvas.syncWithCurrentSheet();
        canvas.selectSingleEdgeForTest(edge);
        assertSame(edge, canvas.getSelectedEdge());

        model.addSchemaSheet(SchemaMode.SIGNAL, "Вторая");
        canvas.syncWithCurrentSheet();

        assertNull(canvas.getSelectedEdge(), "выделенная связь первой схемы не должна пережить смену схемы");
        assertTrue(canvas.getSelectedNodes().isEmpty());
        canvas.deleteSelected();
        assertEquals(1, model.schemaEdgesOfSheet(model.getCurrentScene(), first.getId()).size(),
                "Delete после смены схемы не трогает связи прежней");
    }

    @Test
    void syncWithTheSameSheetKeepsSelection(@TempDir Path dir) {
        AppModel model = model(dir);
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settings);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 0, null);
        CardPort out = model.addCardToNode(a, "Видео", List.of(new CardPort("HDMI", PortDirection.OUT, 1)))
                .getPorts().get(0);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 300, 0, null);
        CardPort in = model.addCardToNode(b, "Видео", List.of(new CardPort("HDMI", PortDirection.IN, 1)))
                .getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), out.getId(), b.getId(), in.getId(), null);
        canvas.syncWithCurrentSheet();
        canvas.selectSingleEdgeForTest(edge);

        canvas.syncWithCurrentSheet();

        assertSame(edge, canvas.getSelectedEdge());
    }
}
