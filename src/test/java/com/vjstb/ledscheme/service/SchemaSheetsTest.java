package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EdgeWaypoint;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.SchemaSheet;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Несколько блок-схем на сцену — модельный слой (запрос пользователя 2026-09-30,
 * docs/masks-and-schema-sheets/PLAN.md, пункт 8, трек C1): листы {@link SchemaSheet},
 * миграция старых проектов (всё — в первый лист режима), API листов в {@link
 * AppModel} (CRUD, ограничения, отмена), фильтрация холста по ТЕКУЩЕМУ листу,
 * автозаполнение только в ПЕРВЫЙ лист (решение D2), легенда/нагрузка/граф сети в
 * пределах листа и формат файла со сдвигом листов по X для старых клиентов
 * (решение D5): в файле схемы одного режима не перекрываются, в памяти координаты
 * локальные, сохранить→загрузить их не меняет.
 */
class SchemaSheetsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static AppModel model(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        return model;
    }

    private static AppModel reload(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    private static CabinetType sampleType() {
        CabinetType ct = new CabinetType();
        ct.setName("Test P3 500x500");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setPowerConsumptionW(150);
        ct.setWeightKg(12);
        return ct;
    }

    private static JsonNode savedScene(Path dir) throws Exception {
        JsonNode root = MAPPER.readTree(new File(dir.toFile(), "workspace.json"));
        return root.get("projects").get(0).get("scenes").get(0);
    }

    // ---- миграция и инвариант ----

    @Test
    void newSceneHasExactlyOneDefaultSheetPerMode(@TempDir Path dir) {
        AppModel model = model(dir);
        List<SchemaSheet> power = model.schemaSheets(SchemaMode.POWER);
        List<SchemaSheet> signal = model.schemaSheets(SchemaMode.SIGNAL);
        assertEquals(1, power.size());
        assertEquals(1, signal.size());
        assertEquals("Схема питания", power.get(0).getName());
        assertEquals("Схема сигнала", signal.get(0).getName());
        assertSame(power.get(0), model.currentSchemaSheet(SchemaMode.POWER));
    }

    @Test
    void legacyProjectWithoutSheetsPutsEveryNodeAndEdgeIntoFirstSheetOfItsModeWithoutMovingIt(@TempDir Path dir)
            throws Exception {
        String legacy = """
                {"projects":[{"id":"p1","name":"Old","scenes":[{"id":"s1","name":"Сцена",
                  "schemaNodes":[
                    {"id":"a","mode":"POWER","type":"CUSTOM","label":"A","x":100,"y":10},
                    {"id":"b","mode":"POWER","type":"CUSTOM","label":"B","x":5000,"y":10},
                    {"id":"c","mode":"SIGNAL","type":"CUSTOM","label":"C","x":70,"y":20}],
                  "schemaEdges":[
                    {"id":"e","mode":"POWER","fromNodeId":"a","toNodeId":"b",
                     "waypoints":[{"x":2500,"y":40}]}]}]}]}
                """;
        Files.writeString(dir.resolve("workspace.json"), legacy, StandardCharsets.UTF_8);

        AppModel model = reload(dir);
        Scene scene = model.getProjects().get(0).getScenes().get(0);
        SchemaSheet power = model.schemaSheets(scene, SchemaMode.POWER).get(0);
        SchemaSheet signal = model.schemaSheets(scene, SchemaMode.SIGNAL).get(0);
        assertEquals(1, model.schemaSheets(scene, SchemaMode.POWER).size());
        assertEquals(1, model.schemaSheets(scene, SchemaMode.SIGNAL).size());
        for (SchemaNode n : scene.getSchemaNodes()) {
            assertEquals(n.getMode() == SchemaMode.POWER ? power.getId() : signal.getId(), n.getSheetId());
        }
        assertEquals(power.getId(), scene.getSchemaEdges().get(0).getSheetId());
        // файл без schemaSheets — вычитать нечего, координаты как в файле
        assertEquals(5000, scene.getSchemaNodes().get(1).getX());
        assertEquals(2500, scene.getSchemaEdges().get(0).getWaypoints().get(0).getX());

        model.selectProject(model.getProjects().get(0));
        model.selectScene(scene);
        assertEquals(2, model.schemaNodesForCurrentScene(SchemaMode.POWER).size());
        assertEquals(1, model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).size());
    }

    @Test
    void nodeReferencingMissingOrForeignModeSheetFallsBackToFirstSheetOfItsMode() {
        Scene scene = new Scene("S");
        SchemaSheetMigration.ensure(scene);
        SchemaSheet signal = SchemaSheetMigration.firstSheet(scene, SchemaMode.SIGNAL);
        SchemaNode ghost = new SchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "g", 0, 0, null);
        ghost.setSheetId("нет-такого");
        SchemaNode foreign = new SchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "f", 0, 0, null);
        foreign.setSheetId(signal.getId());
        scene.getSchemaNodes().add(ghost);
        scene.getSchemaNodes().add(foreign);

        assertTrue(SchemaSheetMigration.ensure(scene));
        String powerId = SchemaSheetMigration.firstSheet(scene, SchemaMode.POWER).getId();
        assertEquals(powerId, ghost.getSheetId());
        assertEquals(powerId, foreign.getSheetId());
        assertFalse(SchemaSheetMigration.ensure(scene), "Повторный вызов на согласованной сцене ничего не меняет");
    }

    // ---- CRUD, ограничения, отмена ----

    @Test
    void addRenameDuplicateMoveAndDeleteSheetsWithUndo(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaSheet first = model.currentSchemaSheet(SchemaMode.POWER);
        SchemaSheet second = model.addSchemaSheet(SchemaMode.POWER, "  Сцена Б  ");
        assertEquals("Сцена Б", second.getName());
        assertSame(second, model.currentSchemaSheet(SchemaMode.POWER), "Новая схема сразу открывается");
        assertEquals(List.of(first, second), model.schemaSheets(SchemaMode.POWER));
        assertEquals(1, model.schemaSheets(SchemaMode.SIGNAL).size(), "Режимы независимы");

        model.renameSchemaSheet(second, "Зона Б");
        assertEquals("Зона Б", second.getName());
        model.undo();
        assertEquals("Сцена Б", model.schemaSheetById(model.getCurrentScene(), second.getId()).getName());

        model.moveSchemaSheet(second, 0);
        assertEquals(second.getId(), model.schemaSheets(SchemaMode.POWER).get(0).getId());
        model.undo();
        assertEquals(first.getId(), model.schemaSheets(SchemaMode.POWER).get(0).getId());

        model.deleteSchemaSheet(second);
        assertEquals(1, model.schemaSheets(SchemaMode.POWER).size());
        assertEquals(first.getId(), model.currentSchemaSheet(SchemaMode.POWER).getId(),
                "Удалённая текущая схема — открывается первая");
        model.undo();
        assertEquals(2, model.schemaSheets(SchemaMode.POWER).size(), "Удаление схемы отменяется");

        model.undo(); // добавление второй схемы
        assertEquals(1, model.schemaSheets(SchemaMode.POWER).size());
        assertEquals(first.getId(), model.currentSchemaSheet(SchemaMode.POWER).getId());
    }

    @Test
    void sheetNamesMustBeNonEmptyAndUniqueWithinModeButMayRepeatAcrossModes(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaSheet power = model.currentSchemaSheet(SchemaMode.POWER);
        assertThrows(IllegalArgumentException.class, () -> model.addSchemaSheet(SchemaMode.POWER, "   "));
        IllegalArgumentException dup = assertThrows(IllegalArgumentException.class,
                () -> model.addSchemaSheet(SchemaMode.POWER, "схема ПИТАНИЯ"));
        assertTrue(dup.getMessage().contains("уже есть"));
        assertThrows(IllegalArgumentException.class, () -> model.renameSchemaSheet(power, ""));
        assertNull(model.schemaSheetNameProblem(SchemaMode.POWER, "Схема питания", power),
                "Собственное имя при переименовании — не конфликт");

        SchemaSheet signalNamedLikePower = model.addSchemaSheet(SchemaMode.SIGNAL, "Схема питания");
        assertNotNull(signalNamedLikePower, "Имя схемы другого режима не мешает");
        assertEquals("Схема питания 2", model.suggestSchemaSheetName(SchemaMode.POWER));
    }

    @Test
    void lastSheetOfModeCannotBeDeleted(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaSheet only = model.currentSchemaSheet(SchemaMode.SIGNAL);
        assertThrows(IllegalStateException.class, () -> model.deleteSchemaSheet(only));
        assertEquals(1, model.schemaSheets(SchemaMode.SIGNAL).size());
    }

    @Test
    void deletingSheetRemovesOnlyItsNodesAndEdges(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a1 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "A1", 0, 0, null);
        SchemaSheet second = model.addSchemaSheet(SchemaMode.POWER, "Вторая");
        SchemaNode b1 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "B1", 0, 0, null);
        SchemaNode b2 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "B2", 300, 0, null);
        model.addSchemaEdge(SchemaMode.POWER, b1.getId(), b2.getId(), null);

        model.deleteSchemaSheet(second);
        assertEquals(List.of(a1), model.getCurrentScene().getSchemaNodes());
        assertTrue(model.getCurrentScene().getSchemaEdges().isEmpty());
    }

    // ---- текущий лист: фильтрация и куда пишут добавления ----

    @Test
    void canvasAccessorsAndAdditionsFollowTheCurrentSheet(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaSheet first = model.currentSchemaSheet(SchemaMode.SIGNAL);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "A", 0, 0, null);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "B", 300, 0, null);
        SchemaEdge ab = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), b.getId(), null);
        assertEquals(first.getId(), a.getSheetId());
        assertEquals(first.getId(), ab.getSheetId());

        SchemaSheet second = model.addSchemaSheet(SchemaMode.SIGNAL, "Резерв");
        assertTrue(model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).isEmpty(), "Новая схема пуста");
        assertTrue(model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL).isEmpty());

        SchemaNode c = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "C", 0, 0, null);
        SchemaNode legend = model.addLineLegendNode(SchemaMode.SIGNAL, 0, 300);
        List<SchemaNode> pasted = model.pasteSchemaNodes(SchemaMode.SIGNAL, List.of(a.copy(), b.copy()),
                List.of(ab.copy()), 20, 20);
        assertEquals(second.getId(), c.getSheetId());
        assertEquals(second.getId(), legend.getSheetId());
        for (SchemaNode p : pasted) {
            assertEquals(second.getId(), p.getSheetId(), "Вставка из буфера — в открытую схему");
        }
        assertEquals(4, model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).size());
        assertEquals(1, model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL).size());
        assertEquals(second.getId(), model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL).get(0).getSheetId());

        model.selectSchemaSheet(first);
        assertEquals(List.of(a, b), model.schemaNodesForCurrentScene(SchemaMode.SIGNAL));
        assertEquals(List.of(ab), model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL));
    }

    @Test
    void selectingAnotherSceneOpensFirstSheetAgain(@TempDir Path dir) {
        AppModel model = model(dir);
        Scene hall = model.getCurrentScene();
        SchemaSheet second = model.addSchemaSheet(SchemaMode.POWER, "Вторая");
        model.selectScene(hall); // та же сцена (перестройка UI) — выбор не сбрасывается
        assertEquals(second.getId(), model.currentSchemaSheet(SchemaMode.POWER).getId());

        Scene other = model.addScene("Фойе");
        model.selectScene(other);
        model.selectScene(hall);
        assertEquals(model.schemaSheets(SchemaMode.POWER).get(0).getId(),
                model.currentSchemaSheet(SchemaMode.POWER).getId());
    }

    @Test
    void clearSchemaClearsOnlyTheCurrentSheet(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode keep = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "Keep", 0, 0, null);
        model.addSchemaSheet(SchemaMode.POWER, "Вторая");
        SchemaNode x = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "X", 0, 0, null);
        SchemaNode y = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "Y", 300, 0, null);
        model.addSchemaEdge(SchemaMode.POWER, x.getId(), y.getId(), null);

        model.clearSchema(SchemaMode.POWER);
        assertTrue(model.schemaNodesForCurrentScene(SchemaMode.POWER).isEmpty());
        assertEquals(List.of(keep), model.getCurrentScene().getSchemaNodes());
        assertEquals(2, model.schemaSheets(SchemaMode.POWER).size(), "Сам лист остаётся");
        model.undo();
        assertEquals(2, model.schemaNodesForCurrentScene(SchemaMode.POWER).size());
    }

    @Test
    void autoPopulateWritesOnlyIntoFirstSheetEvenWhenAnotherIsOpen(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);
        EquipmentPreset ctrl = model.addControllerPreset("C", "", 4, 1000, 0, false, List.of());
        model.addControllerToScreen(screen, ctrl.getId(), null);
        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addSignalChain(1, false, List.of(ids.get(0)));

        SchemaSheet first = model.currentSchemaSheet(SchemaMode.SIGNAL);
        SchemaSheet second = model.addSchemaSheet(SchemaMode.SIGNAL, "Вторая");
        model.autoPopulateSchema(SchemaMode.SIGNAL, true);

        assertTrue(model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).isEmpty(),
                "Открытая (вторая) схема автозаполнением не трогается");
        assertEquals(second.getId(), model.currentSchemaSheet(SchemaMode.SIGNAL).getId(),
                "Автозаполнение не переключает открытую схему");
        List<SchemaNode> firstNodes = model.schemaNodesOfSheet(model.getCurrentScene(), first.getId());
        assertEquals(2, firstNodes.size(), "Экран и контроллер — в первой схеме");
        List<SchemaEdge> firstEdges = model.schemaEdgesOfSheet(model.getCurrentScene(), first.getId());
        assertEquals(1, firstEdges.size(), "Автосвязь — тоже в первой схеме");

        model.autoPopulateSchema(SchemaMode.SIGNAL, true);
        assertEquals(2, model.schemaNodesOfSheet(model.getCurrentScene(), first.getId()).size(), "Без дублей");
    }

    @Test
    void duplicateSheetCopiesNodesWithFreshIdsAndRebindsEdges(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaSheet src = model.currentSchemaSheet(SchemaMode.POWER);
        src.setDefaultFontSize(15);
        SchemaNode a = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "A", 0, 0, null);
        CardPort out = model.addPowerConnectorToNode(a, "CEE 32A", PortDirection.OUT, 1);
        SchemaNode b = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "B", 300, 0, null);
        CardPort in = model.addPowerConnectorToNode(b, "CEE 32A", PortDirection.IN, 1);
        model.addSchemaEdge(SchemaMode.POWER, a.getId(), out.getId(), b.getId(), in.getId(), null);
        SchemaSheet tail = model.addSchemaSheet(SchemaMode.POWER, "Хвост");

        SchemaSheet copy = model.duplicateSchemaSheet(src);
        assertEquals("Схема питания (копия)", copy.getName());
        assertEquals(15, copy.getDefaultFontSize());
        assertEquals(List.of(src.getId(), copy.getId(), tail.getId()),
                model.schemaSheets(SchemaMode.POWER).stream().map(SchemaSheet::getId).toList(),
                "Копия встаёт сразу после исходной");
        assertEquals(copy.getId(), model.currentSchemaSheet(SchemaMode.POWER).getId());

        List<SchemaNode> nodes = model.schemaNodesForCurrentScene(SchemaMode.POWER);
        List<SchemaEdge> edges = model.schemaEdgesForCurrentScene(SchemaMode.POWER);
        assertEquals(2, nodes.size());
        assertEquals(1, edges.size());
        SchemaNode a2 = nodes.stream().filter(n -> n.getLabel().equals("A")).findFirst().orElseThrow();
        SchemaNode b2 = nodes.stream().filter(n -> n.getLabel().equals("B")).findFirst().orElseThrow();
        assertNotEquals(a.getId(), a2.getId());
        assertEquals(a2.getId(), edges.get(0).getFromNodeId());
        assertEquals(b2.getId(), edges.get(0).getToNodeId());
        assertEquals(out.getId(), edges.get(0).getFromPortId(), "id гнёзд в копии не меняются");
        assertEquals(out.getId(), a2.getPowerConnectors().get(0).getId());

        assertEquals("Схема питания (копия 2)", model.duplicateSchemaSheet(src).getName());
        model.undo();
        model.undo();
        assertEquals(2, model.schemaSheets(SchemaMode.POWER).size());
        assertEquals(2, model.getCurrentScene().getSchemaNodes().size());
    }

    // ---- расчёты в пределах листа ----

    @Test
    void lineLegendSeesOnlyEdgesOfItsOwnSheet(@TempDir Path dir) {
        AppModel model = model(dir);
        Scene scene = model.getCurrentScene();
        SchemaSheet first = model.currentSchemaSheet(SchemaMode.POWER);
        SchemaNode s1 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "S1", 0, 0, null);
        CardPort o32 = model.addPowerConnectorToNode(s1, "CEE 32A", PortDirection.OUT, 1);
        SchemaNode t1 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "T1", 300, 0, null);
        model.addSchemaEdge(SchemaMode.POWER, s1.getId(), o32.getId(), t1.getId(), null, null);

        SchemaSheet second = model.addSchemaSheet(SchemaMode.POWER, "Вторая");
        SchemaNode s2 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "S2", 0, 0, null);
        CardPort o16 = model.addPowerConnectorToNode(s2, "CEE 16A", PortDirection.OUT, 1);
        SchemaNode t2 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "T2", 300, 0, null);
        model.addSchemaEdge(SchemaMode.POWER, s2.getId(), o16.getId(), t2.getId(), null, null);

        assertEquals(List.of("CEE 32A"), model.lineLegendPowerNominals(scene, first.getId()));
        assertEquals(List.of("CEE 16A"), model.lineLegendPowerNominals(scene, second.getId()));
        assertEquals(List.of("CEE 16A"), model.lineLegendPowerNominals(scene),
                "Без явного листа — открытая схема");
        assertEquals(List.<InterfaceRole>of(), model.lineLegendRoles(scene));
    }

    @Test
    void schemaLoadCalcIgnoresEdgesOfNeighbourSheet(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Scene scene = model.getCurrentScene();
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0); // 4 × 150 = 600 Вт

        SchemaNode screenNode = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, "E", 0, 0, screen.getId());
        SchemaNode source = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "Щит", 0, 200, null);
        model.addPowerConnectorToNode(source, "CEE 16A", PortDirection.IN, 1, 1, null);
        model.addSchemaEdge(SchemaMode.POWER, source.getId(), screenNode.getId(), null);
        double alone = SchemaLoadCalc.evaluate(source, scene, model).loadWatts();
        assertEquals(600, alone, 1e-6);

        // Связь соседнего листа, по ошибке ссылающаяся на тот же узел-экран (3 линии), —
        // раньше делила бы нагрузку экрана (1 из 4 линий) и добавлялась бы щиту.
        SchemaSheet second = model.addSchemaSheet(SchemaMode.POWER, "Вторая");
        SchemaEdge foreign = new SchemaEdge(SchemaMode.POWER, source.getId(), screenNode.getId(), null);
        foreign.setWireCount(3);
        foreign.setSheetId(second.getId());
        scene.getSchemaEdges().add(foreign);

        assertEquals(600, SchemaLoadCalc.evaluate(source, scene, model).loadWatts(), 1e-6);
    }

    @Test
    void networkGraphFromSheetSeesOnlyDevicesOfThatSheet(@TempDir Path dir) {
        AppModel model = model(dir);
        Scene scene = model.getCurrentScene();
        SchemaSheet first = model.currentSchemaSheet(SchemaMode.SIGNAL);
        SchemaNode sw1 = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "SW1", 0, 0, null);
        sw1.setNetworkDeviceTypeId("t1");
        SchemaSheet second = model.addSchemaSheet(SchemaMode.SIGNAL, "Вторая");
        SchemaNode sw2 = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "SW2", 0, 0, null);
        sw2.setNetworkDeviceTypeId("t2");

        assertEquals(List.of("SW1"), model.networkGraphFromSheet(scene, first.getId()).switches().stream()
                .map(AppModel.NetworkGraphDevice::label).toList());
        assertEquals(List.of("SW2"), model.networkGraphFromSheet(scene, second.getId()).switches().stream()
                .map(AppModel.NetworkGraphDevice::label).toList());
        assertEquals(List.of("SW1"), model.networkGraphFromScene(scene).switches().stream()
                .map(AppModel.NetworkGraphDevice::label).toList(), "Старая сигнатура — первая схема сигнала");
    }

    // ---- формат файла (решение D5) ----

    @Test
    void savedFileLaysSheetsOfOneModeSideBySideAndReloadRestoresLocalCoordinates(@TempDir Path dir)
            throws Exception {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "A", 100, 0, null);
        SchemaNode b = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "B", 800, 50, null);
        SchemaEdge ab = model.addSchemaEdge(SchemaMode.POWER, a.getId(), b.getId(), null);
        model.setSchemaEdgeWaypoints(ab, List.of(new EdgeWaypoint(1200, 30))); // излом правее узлов
        model.addSchemaSheet(SchemaMode.POWER, "Вторая");
        SchemaNode c = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "C", 10, 0, null);
        SchemaNode d = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "D", 400, 0, null);
        SchemaEdge cd = model.addSchemaEdge(SchemaMode.POWER, c.getId(), d.getId(), null);
        model.setSchemaEdgeWaypoints(cd, List.of(new EdgeWaypoint(200, 90)));
        model.addSchemaSheet(SchemaMode.POWER, "Третья");
        SchemaNode e = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "E", 0, 0, null);
        SchemaNode sig = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "Sig", 5, 5, null);

        // В памяти — локальные координаты, сериализация их не трогает.
        assertEquals(10, c.getX());

        JsonNode scene = savedScene(dir);
        java.util.Map<String, Double> offsets = new java.util.HashMap<>();
        for (JsonNode s : scene.get("schemaSheets")) {
            offsets.put(s.get("id").asText(), s.get("storageOffsetX").asDouble());
        }
        java.util.Map<String, JsonNode> nodes = new java.util.HashMap<>();
        for (JsonNode n : scene.get("schemaNodes")) {
            nodes.put(n.get("label").asText(), n);
        }
        // Лист 1: правый край = излом 1200 (дальше узла B: 800+175) → лист 2 с 1600.
        assertEquals(0, offsets.get(a.getSheetId()));
        assertEquals(1200 + Scene.STORAGE_SHEET_GAP, offsets.get(c.getSheetId()));
        assertEquals(100, nodes.get("A").get("x").asDouble());
        assertEquals(10 + 1600, nodes.get("C").get("x").asDouble());
        // Лист 2: правый край 400+175=575 → лист 3 с 1600+575+400.
        assertEquals(1600 + 575 + 400, offsets.get(e.getSheetId()));
        assertEquals(2575, nodes.get("E").get("x").asDouble());
        assertEquals(5, nodes.get("Sig").get("x").asDouble(), "Сигнал — своя раскладка с нуля");
        assertEquals(0, offsets.get(sig.getSheetId()));
        for (JsonNode edge : scene.get("schemaEdges")) {
            double wx = edge.get("waypoints").get(0).get("x").asDouble();
            if (edge.get("id").asText().equals(cd.getId())) {
                assertEquals(200 + 1600, wx, "Точка излома сдвинута вместе с листом");
            } else {
                assertEquals(1200, wx);
            }
        }
        // Листы одного режима в файле не пересекаются по X.
        double sheet1Right = 1200;
        double sheet2Left = nodes.get("C").get("x").asDouble();
        double sheet2Right = nodes.get("D").get("x").asDouble() + nodes.get("D").get("width").asDouble();
        assertTrue(sheet2Left > sheet1Right);
        assertTrue(nodes.get("E").get("x").asDouble() > sheet2Right);

        AppModel again = reload(dir);
        Scene reloaded = again.getProjects().get(0).getScenes().get(0);
        java.util.Map<String, SchemaNode> byLabel = new java.util.HashMap<>();
        for (SchemaNode n : reloaded.getSchemaNodes()) {
            byLabel.put(n.getLabel(), n);
        }
        assertEquals(100, byLabel.get("A").getX());
        assertEquals(10, byLabel.get("C").getX());
        assertEquals(400, byLabel.get("D").getX());
        assertEquals(0, byLabel.get("E").getX());
        assertEquals(5, byLabel.get("Sig").getX());
        for (SchemaEdge edge : reloaded.getSchemaEdges()) {
            double wx = edge.getWaypoints().get(0).getX();
            assertEquals(edge.getId().equals(cd.getId()) ? 200 : 1200, wx);
        }
        for (SchemaSheet s : reloaded.getSchemaSheets()) {
            assertEquals(0, s.getStorageOffsetX(), "В памяти сдвиг всегда 0");
        }
        assertEquals(3, again.schemaSheets(reloaded, SchemaMode.POWER).size());
        assertEquals(c.getSheetId(), byLabel.get("C").getSheetId());
    }

    @Test
    void sceneJsonRoundTripIsStableAndNegativeCoordinatesNeverOverlapPreviousSheet() throws Exception {
        Scene scene = new Scene("S");
        SchemaSheetMigration.ensure(scene);
        SchemaSheet first = SchemaSheetMigration.firstSheet(scene, SchemaMode.POWER);
        SchemaSheet second = new SchemaSheet(SchemaMode.POWER, "Вторая", 1);
        scene.getSchemaSheets().add(second);
        SchemaNode left = new SchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "L", 0, 0, null);
        left.setSheetId(first.getId());
        SchemaNode negative = new SchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "N", -300, 0, null);
        negative.setSheetId(second.getId());
        scene.getSchemaNodes().add(left);
        scene.getSchemaNodes().add(negative);

        String json = MAPPER.writeValueAsString(scene);
        JsonNode tree = MAPPER.readTree(json);
        double firstRight = 175;
        double storedNegative = tree.get("schemaNodes").get(1).get("x").asDouble();
        assertTrue(storedNegative >= firstRight + Scene.STORAGE_SHEET_GAP,
                "Узел левее нуля не должен заезжать на предыдущий лист: " + storedNegative);
        assertEquals(-300, negative.getX(), "Запись не меняет живые объекты");

        Scene back = MAPPER.readValue(json, Scene.class);
        assertEquals(-300, back.getSchemaNodes().get(1).getX());
        assertEquals(json, MAPPER.writeValueAsString(back), "Прочитать→записать даёт тот же JSON");
        assertEquals(json, MAPPER.writeValueAsString(scene), "Запись детерминирована");
    }

    @Test
    void jsonWithoutSheetsIsReadWithoutSubtractingAnything() throws Exception {
        String json = """
                {"name":"S","schemaNodes":[{"id":"n","mode":"POWER","label":"N","x":3000,"y":0}],
                 "schemaEdges":[{"id":"e","mode":"POWER","fromNodeId":"n","toNodeId":"n",
                                  "waypoints":[{"x":3100,"y":0}]}]}
                """;
        Scene scene = MAPPER.readValue(json, Scene.class);
        assertEquals(3000, scene.getSchemaNodes().get(0).getX());
        assertEquals(3100, scene.getSchemaEdges().get(0).getWaypoints().get(0).getX());
        assertTrue(scene.getSchemaSheets().isEmpty(), "Листы заводит миграция, не десериализация");
        assertNull(scene.getSchemaNodes().get(0).getSheetId());
    }

    @Test
    void sheetsInFileAreReadInAnyKeyOrder() throws Exception {
        // schemaSheets ПОСЛЕ узлов — вычитание всё равно применяется (ленивый флаг).
        String json = """
                {"name":"S",
                 "schemaNodes":[{"id":"n","mode":"POWER","label":"N","x":1700,"y":0,"sheetId":"b"}],
                 "schemaEdges":[],
                 "schemaSheets":[{"id":"a","name":"A","mode":"POWER","orderIndex":0,"storageOffsetX":0},
                                 {"id":"b","name":"B","mode":"POWER","orderIndex":1,"storageOffsetX":1600}]}
                """;
        Scene scene = MAPPER.readValue(json, Scene.class);
        assertEquals(100, scene.getSchemaNodes().get(0).getX());
        assertEquals(0, scene.getSchemaSheets().get(1).getStorageOffsetX());
    }

    @Test
    void adminStyleReaderStillSeesFlatSchemaNodesList(@TempDir Path dir) throws Exception {
        AppModel model = model(dir);
        model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "A", 0, 0, null);
        model.addSchemaSheet(SchemaMode.POWER, "Вторая");
        model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "B", 0, 0, null);
        JsonNode scene = savedScene(dir);
        assertTrue(scene.get("schemaNodes").isArray());
        assertEquals(2, scene.get("schemaNodes").size(), "Узлы всех листов — одним плоским списком");
        assertTrue(scene.get("schemaNodes").get(1).get("sheetId").isTextual());
    }

    @Test
    void schemaCardSurvivesDuplicateAndPortPlacementIdsStayValid(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode server = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "Srv", 0, 0, null);
        SchemaCard card = model.addCardToNode(server, "Card", List.of(new CardPort("HDMI", PortDirection.OUT, 2)));
        String portId = card.getPorts().get(0).getId();
        model.setGroupCollapsed(server, portId, Boolean.TRUE);

        model.duplicateSchemaSheet(model.currentSchemaSheet(SchemaMode.SIGNAL));
        SchemaNode copy = model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).get(0);
        assertNotEquals(server.getId(), copy.getId());
        assertNotNull(copy.findPortPlacement(portId), "Раскладка гнёзд копии указывает на её же гнёзда");
    }
}
