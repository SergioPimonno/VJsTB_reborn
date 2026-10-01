package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vjstb.ledscheme.model.NetworkDeviceCategory;
import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import com.vjstb.ledscheme.model.NetworkDeviceType;
import com.vjstb.ledscheme.model.NetworkManagerPlan;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaSheet;
import com.vjstb.ledscheme.schema.SchemaFixtures;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.NetworkDeviceLabels;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.ui.NetworkCanvasPanel;
import com.vjstb.ledscheme.ui.SchemaImportDialog;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * «Перенести из схемы…» с несколькими схемами сигнала на сцену (запрос пользователя
 * 2026-09-30, docs/masks-and-schema-sheets/PLAN.md, пункт 8, трек C3): предпросмотр
 * строится по ВЫБРАННОЙ схеме ({@code NetworkCanvasPanel#previewSchemaImport(String)}),
 * устройства другой схемы в него не попадают; выбор схемы в диалоге показывается только
 * когда схем несколько; ссылка размещения менеджера на узел удалённой схемы не роняет
 * ни подписи устройств, ни импорт (контракт {@code NetworkDevicePlacement}: ссылка на
 * исчезнувший узел игнорируется).
 */
class SchemaImportSheetsTest {

    private static NetworkDeviceType switchType() {
        NetworkDeviceType t = new NetworkDeviceType();
        t.setName("Aruba 2930F");
        t.setCategory(NetworkDeviceCategory.SWITCH);
        t.setEthernetPortCount(24);
        return t;
    }

    private SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    /** Фикстура + вторая схема сигнала с одиноким коммутатором (D3/Q8 остаются в первой). */
    private record Scenario(AppModel model, SchemaSheet first, SchemaSheet second, SchemaNode switchNode) {
    }

    private Scenario scenario(Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SchemaSheet first = model.schemaSheets(SchemaMode.SIGNAL).get(0);
        SchemaSheet second = model.addSchemaSheet(SchemaMode.SIGNAL, "Резерв");
        model.selectSchemaSheet(second);
        NetworkDeviceType type = model.addNetworkDeviceType(switchType());
        SchemaNode swtch = model.addSchemaNodeFromNetworkDevice(type, 0, 0);
        swtch.setLabel("Свитч резерва");
        return new Scenario(model, first, second, swtch);
    }

    @Test
    void previewOfSelectedSheetDoesNotSeeDevicesOfAnotherSheet(@TempDir Path dir) {
        Scenario sc = scenario(dir);
        NetworkCanvasPanel canvas = new NetworkCanvasPanel(sc.model(), settings(dir));
        canvas.setPlan(new NetworkManagerPlan(), null);

        var fromSecond = canvas.previewSchemaImport(sc.second().getId());
        var fromFirst = canvas.previewSchemaImport(sc.first().getId());

        assertEquals(1, fromSecond.newGroups().size());
        assertEquals(List.of(sc.switchNode().getId()),
                fromSecond.newGroups().get(0).switches().stream().map(AppModel.NetworkGraphDevice::nodeId).toList());
        assertTrue(fromSecond.newGroups().get(0).devices().isEmpty(), "D3/Q8 лежат в первой схеме, не во второй");

        boolean firstHasSwitch = fromFirst.newGroups().stream()
                .flatMap(g -> g.switches().stream()).anyMatch(s -> s.nodeId().equals(sc.switchNode().getId()));
        assertFalse(firstHasSwitch, "коммутатор второй схемы не виден из первой");
        assertEquals(2, fromFirst.newGroups().stream().mapToInt(g -> g.devices().size()).sum(),
                "в первой схеме остались D3 и Q8");
    }

    @Test
    void noArgPreviewKeepsTheOldMeaningOfFirstSignalSheet(@TempDir Path dir) {
        Scenario sc = scenario(dir);
        NetworkCanvasPanel canvas = new NetworkCanvasPanel(sc.model(), settings(dir));
        canvas.setPlan(new NetworkManagerPlan(), null);

        var noArg = canvas.previewSchemaImport();
        var first = canvas.previewSchemaImport(sc.first().getId());

        assertEquals(first.newGroups().size(), noArg.newGroups().size());
        assertTrue(noArg.newGroups().stream().flatMap(g -> g.switches().stream())
                .noneMatch(s -> s.nodeId().equals(sc.switchNode().getId())));
    }

    @Test
    void placementPointingAtDeletedSheetNodeDoesNotBreakImportOrLabel(@TempDir Path dir) {
        Scenario sc = scenario(dir);
        NetworkCanvasPanel canvas = new NetworkCanvasPanel(sc.model(), settings(dir));
        NetworkManagerPlan plan = new NetworkManagerPlan();
        canvas.setPlan(plan, null);
        canvas.applySchemaImport(canvas.previewSchemaImport(sc.second().getId()).newGroups());
        NetworkDevicePlacement placed = plan.getDevices().get(0);
        assertEquals(sc.switchNode().getId(), placed.getLinkedSchemaNodeId());
        assertEquals("Свитч резерва", NetworkDeviceLabels.resolveLabel(placed, sc.model()));

        sc.model().deleteSchemaSheet(sc.second()); // узел исчез вместе со схемой, размещение осталось

        assertNotNull(NetworkDeviceLabels.resolveLabel(placed, sc.model()), "подпись берётся из следующих источников");
        assertFalse(NetworkDeviceLabels.resolveLabel(placed, sc.model()).isBlank());
        var preview = canvas.previewSchemaImport(sc.first().getId());
        assertNotNull(preview);
        assertEquals(0, preview.alreadyImportedCount(), "размещение удалённого узла не мешает импорту первой схемы");
        assertNull(sc.model().schemaSheetById(sc.model().getCurrentScene(), sc.second().getId()));
    }

    // ---- диалог ----

    private static JComboBox<?> findCombo(Container c) {
        for (Component child : c.getComponents()) {
            if (child instanceof JComboBox<?> box) {
                return box;
            }
            if (child instanceof Container inner) {
                JComboBox<?> found = findCombo(inner);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void dialogShowsSheetChoiceOnlyForSeveralSheetsAndRecomputesPreviewOnChange(@TempDir Path dir) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "диалог — Swing, нужен дисплей");
        Scenario sc = scenario(dir);
        NetworkCanvasPanel canvas = new NetworkCanvasPanel(sc.model(), settings(dir));
        canvas.setPlan(new NetworkManagerPlan(), null);
        List<String> requested = new ArrayList<>();

        SchemaImportDialog several = new SchemaImportDialog(null, sc.model().schemaSheets(SchemaMode.SIGNAL),
                sc.first().getId(), id -> {
                    requested.add(id);
                    return canvas.previewSchemaImport(id);
                });
        JComboBox<?> combo = findCombo(several);
        assertNotNull(combo, "схем сигнала две — комбобокс выбора есть");
        assertEquals(sc.first().getId(), several.getSelectedSheetId());
        combo.setSelectedIndex(1);
        assertEquals(sc.second().getId(), several.getSelectedSheetId());
        assertEquals(List.of(sc.first().getId(), sc.second().getId()), requested,
                "смена схемы пересчитала предпросмотр по новой схеме");
        several.dispose();

        SchemaImportDialog single = new SchemaImportDialog(null, List.of(sc.first()), sc.first().getId(),
                id -> canvas.previewSchemaImport(id));
        assertNull(findCombo(single), "схема одна — выбирать нечего, окно как раньше");
        single.dispose();
    }
}
