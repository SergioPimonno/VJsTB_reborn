package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.graphimport.GraphImportFixtures;
import com.vjstb.ledscheme.service.graphimport.GraphMlReader;
import com.vjstb.ledscheme.service.graphimport.ImportedGraph;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Окно импорта схемы из .graphml: предвыбор по библиотеке, список экранов у картинок, выбор пользователя доходит до листа. */
class SchemaGraphImportDialogTest {

    private static ImportedGraph load() throws Exception {
        try (InputStream in = SchemaGraphImportDialogTest.class.getResourceAsStream("/graphml/signal-zimmer.graphml")) {
            return GraphMlReader.read(in, "signal-zimmer");
        }
    }

    private static com.vjstb.ledscheme.settings.SettingsManager settingsFor(Path dir) {
        return new com.vjstb.ledscheme.settings.SettingsManager(
                new com.vjstb.ledscheme.settings.SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    private int rowOf(SchemaGraphImportDialog d, String label) {
        for (int i = 0; i < d.rowCountForTest(); i++) {
            if (d.labelOfRowForTest(i).equals(label)) {
                return i;
            }
        }
        throw new IllegalArgumentException(label);
    }

    @Test
    void rowsAreFilledFromTheLibraryAndUserChoicesReachTheNewSheet(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        var lib = model.getWorkspace().getEquipmentPresets();
        lib.add(com.vjstb.ledscheme.service.graphimport.GraphImportFixtures.signal("CVT4k",
                com.vjstb.ledscheme.service.graphimport.GraphImportFixtures.port("Fiber", PortDirection.IN, 2)));
        lib.add(com.vjstb.ledscheme.service.graphimport.GraphImportFixtures.signal("H2",
                com.vjstb.ledscheme.service.graphimport.GraphImportFixtures.port("Fiber", PortDirection.OUT, 4)));
        CabinetType ct = new CabinetType();
        ct.setName("T");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        CabinetType type = model.addCabinetType(ct);
        Screen left = model.addScreen("Left", type.getId(), 2, 2, 0, 0);

        SchemaGraphImportDialog d = SchemaGraphImportDialog.create(null, model, SchemaMode.SIGNAL, load(), settingsFor(dir));
        assertEquals(15, d.rowCountForTest(), "группа-папка в таблицу не попадает");

        int cvt = rowOf(d, "CVT4k");
        assertTrue(d.selectedForTest(cvt).startsWith("CVT4k — совпадает, 100%"), d.selectedForTest(cvt));
        int media = rowOf(d, "Mediabort");
        assertTrue(d.selectedForTest(media).startsWith("Пустой блок «Mediabort»"));
        int server = rowOf(d, "Server");
        d.chooseForTest(server, "— не переносить");

        int image = -1;
        for (int i = 0; i < d.rowCountForTest(); i++) {
            if (d.isImageRowForTest(i)) {
                image = i;
                break;
            }
        }
        assertEquals("— не переносить —", d.selectedForTest(image), "экран выбирает пользователь");
        assertTrue(d.optionsForTest(image).contains("Экран «Left»"));
        d.chooseForTest(image, "Экран «Left»");

        d.importForTest();
        assertTrue(d.imported());
        List<SchemaNode> nodes = model.schemaNodesForCurrentScene(SchemaMode.SIGNAL);
        assertTrue(nodes.stream().anyMatch(n -> n.getType() == SchemaNodeType.SCREEN && left.getId().equals(n.getScreenRefId())));
        assertTrue(nodes.stream().anyMatch(n -> "CVT4k".equals(n.getLabel())));
        assertFalse(nodes.stream().anyMatch(n -> "Server".equals(n.getLabel())), "«не переносить» выполнено");
        assertTrue(nodes.stream().anyMatch(n -> "Mediabort".equals(n.getLabel())));
    }

    @Test
    void choiceForOneBlockIsAppliedToAllBlocksWithTheSameLabel(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        // у библиотеки только общее слово с «Socapex->6x16A» — вариант есть, но сам не выбирается
        model.getWorkspace().getEquipmentPresets().add(GraphImportFixtures.power("Проходная Socapex",
                GraphImportFixtures.port("Socapex", PortDirection.IN, 1)));
        ImportedGraph power;
        try (InputStream in = SchemaGraphImportDialogTest.class.getResourceAsStream("/graphml/power-zimmer.graphml")) {
            power = GraphMlReader.read(in, "power");
        }
        SchemaGraphImportDialog d = SchemaGraphImportDialog.create(null, model, SchemaMode.POWER, power, settingsFor(dir));
        List<Integer> same = new java.util.ArrayList<>();
        for (int i = 0; i < d.rowCountForTest(); i++) {
            if (d.labelOfRowForTest(i).equals("Socapex->6x16A")) {
                same.add(i);
                assertTrue(d.selectedForTest(i).startsWith("Пустой блок"));
            }
        }
        assertEquals(8, same.size());
        d.chooseForTest(same.get(0), "Проходная Socapex");
        for (int i : same) {
            assertTrue(d.selectedForTest(i).startsWith("Проходная Socapex"), d.selectedForTest(i));
        }
    }

    @Test
    void picturesBecomeLabelledEmptyBlocksWhenTheProjectHasNoScreensYet(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        ImportedGraph kish;
        try (InputStream in = SchemaGraphImportDialogTest.class.getResourceAsStream("/graphml/kish-signal.graphml")) {
            kish = GraphMlReader.read(in, "kish");
        }
        SchemaGraphImportDialog d = SchemaGraphImportDialog.create(null, model, SchemaMode.SIGNAL, kish, settingsFor(dir));
        int center = rowOf(d, "Center");
        assertTrue(d.isImageRowForTest(center));
        assertTrue(d.selectedForTest(center).startsWith("Пустой блок «Center»"), d.selectedForTest(center));
        d.importForTest();
        assertTrue(model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).stream().anyMatch(n -> "Center".equals(n.getLabel())));
    }

    @Test
    void pictureLabelEqualToAScreenNamePreselectsThatScreen(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        CabinetType ct = new CabinetType();
        ct.setName("T");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        CabinetType type = model.addCabinetType(ct);
        model.addScreen("center", type.getId(), 2, 2, 0, 0);
        ImportedGraph kish;
        try (InputStream in = SchemaGraphImportDialogTest.class.getResourceAsStream("/graphml/kish-signal.graphml")) {
            kish = GraphMlReader.read(in, "kish");
        }
        SchemaGraphImportDialog d = SchemaGraphImportDialog.create(null, model, SchemaMode.SIGNAL, kish, settingsFor(dir));
        assertEquals("Экран «center»", d.selectedForTest(rowOf(d, "Center")));
        assertEquals("— не переносить —", d.selectedForTest(rowOf(d, "Left Top")), "остальные — на выбор пользователя");
    }
}
