package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CableLengthProfile;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.SchemaSheet;
import com.vjstb.ledscheme.service.SceneSpecCalc.Column;
import com.vjstb.ledscheme.service.SceneSpecCalc.SheetData;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Спецификация сцены по схемам (запрос пользователя 2026-09-30, решения D1 и D6,
 * docs/masks-and-schema-sheets/PLAN.md, трек C3) — {@link SceneSpecCalc}, агрегация
 * без POI: сцена не видит экраны/узлы/связи другой сцены, одинаковые позиции разных
 * схем — одна строка с числами в разных колонках, имена схем питания и сигнала с
 * совпадением разводятся суффиксами, кабель комплектуется по КАЖДОЙ схеме отдельно.
 */
class SceneSpecCalcTest {

    private static SchemaNode node(SchemaMode mode, SchemaNodeType type, String label) {
        return new SchemaNode(mode, type, label, 0, 0, null);
    }

    private static SchemaEdge wire(SchemaMode mode, int count, String type, Double lengthM) {
        SchemaEdge e = new SchemaEdge(mode, "a", "b", null);
        e.setWireCount(count);
        e.setWireType(type);
        e.setLengthM(lengthM);
        return e;
    }

    private static SheetData sheet(String id, SchemaMode mode, String name, List<SchemaNode> nodes,
            List<SchemaEdge> edges) {
        return new SheetData(id, mode, name, nodes, edges);
    }

    private static CableLengthProfile profile(double... lengths) {
        CableLengthProfile p = new CableLengthProfile();
        p.setName("Кабель");
        p.setMarginPercent(0);
        List<Double> list = new ArrayList<>();
        for (double l : lengths) {
            list.add(l);
        }
        p.setAvailableLengthsM(list);
        return p;
    }

    private static String label(SchemaNodeType t) {
        return t.getLabel();
    }

    // ---- оборудование ----

    @Test
    void equipmentOfTwoSheetsSharesOneRowButSplitsCountsIntoColumns() {
        SheetData main = sheet("m", SchemaMode.SIGNAL, "Основная", List.of(
                node(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER, "MCTRL4K"),
                node(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER, "MCTRL4K"),
                node(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "D3")), List.of());
        SheetData backup = sheet("b", SchemaMode.SIGNAL, "Резерв", List.of(
                node(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER, "MCTRL4K"),
                node(SchemaMode.SIGNAL, SchemaNodeType.CONVERTER, "CVT")), List.of());

        var rows = SceneSpecCalc.equipment(List.of(main, backup), SceneSpecCalcTest::label);

        assertEquals(3, rows.size(), "MCTRL4K общий для обеих схем -- одна строка, плюс D3 и CVT");
        var mctrl = rows.stream().filter(r -> r.label().equals("MCTRL4K")).findFirst().orElseThrow();
        assertArrayEquals(new int[]{2, 1}, mctrl.counts());
        var d3 = rows.stream().filter(r -> r.label().equals("D3")).findFirst().orElseThrow();
        assertArrayEquals(new int[]{1, 0}, d3.counts(), "позиции нет в схеме -- 0 (пустая ячейка)");
        var cvt = rows.stream().filter(r -> r.label().equals("CVT")).findFirst().orElseThrow();
        assertArrayEquals(new int[]{0, 1}, cvt.counts());
        assertNull(SceneSpecCalc.cell(d3.counts(), 1));
        assertEquals(1, SceneSpecCalc.cell(d3.counts(), 0));
    }

    @Test
    void screenNodesAndAutoPortLegendAreNotEquipment() {
        SchemaNode legend = node(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "Легенда портов");
        legend.setAutoPortLegend(true);
        SheetData s = sheet("s", SchemaMode.SIGNAL, "Сигнал", List.of(
                node(SchemaMode.SIGNAL, SchemaNodeType.SCREEN, "Экран 1"), legend,
                node(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "")), List.of());

        var rows = SceneSpecCalc.equipment(List.of(s), SceneSpecCalcTest::label);

        assertEquals(1, rows.size());
        assertEquals(SchemaNodeType.CUSTOM.getLabel(), rows.get(0).label(),
                "пустая подпись заменяется названием категории");
    }

    @Test
    void thereIsNoTotalColumn() {
        SheetData a = sheet("a", SchemaMode.POWER, "А", List.of(node(SchemaMode.POWER, SchemaNodeType.SOURCE, "Щит")),
                List.of());
        SheetData b = sheet("b", SchemaMode.POWER, "Б", List.of(node(SchemaMode.POWER, SchemaNodeType.SOURCE, "Щит")),
                List.of());
        var rows = SceneSpecCalc.equipment(List.of(a, b), SceneSpecCalcTest::label);
        assertEquals(2, rows.get(0).counts().length, "ровно колонка на схему, без «Итого» (решение D1)");
    }

    // ---- колонки ----

    @Test
    void equalNamesOfPowerAndSignalSheetsGetModeSuffixes() {
        List<Column> cols = SceneSpecCalc.columns(List.of(
                sheet("p", SchemaMode.POWER, "Основная", List.of(), List.of()),
                sheet("s", SchemaMode.SIGNAL, " основная ", List.of(), List.of()),
                sheet("s2", SchemaMode.SIGNAL, "Резерв", List.of(), List.of())));

        assertEquals("Основная (питание)", cols.get(0).title());
        assertEquals("основная (сигнал)", cols.get(1).title());
        assertEquals("Резерв", cols.get(2).title(), "уникальное имя суффикса не получает");
    }

    @Test
    void blankSheetNameFallsBackToDefaultModeName() {
        List<Column> cols = SceneSpecCalc.columns(List.of(
                sheet("p", SchemaMode.POWER, " ", List.of(), List.of()),
                sheet("s", SchemaMode.SIGNAL, null, List.of(), List.of())));
        assertEquals("Схема питания", cols.get(0).title());
        assertEquals("Схема сигнала", cols.get(1).title());
    }

    // ---- коммутация ----

    @Test
    void cableKitsAreComputedPerSheetNotSummed() {
        // Каталог 10 и 20 м. Линия 25 м не покрывается одним куском -> сплайсовка 20+10.
        SheetData a = sheet("a", SchemaMode.SIGNAL, "А", List.of(), List.of(
                wire(SchemaMode.SIGNAL, 2, "Cat6", 8.0), wire(SchemaMode.SIGNAL, 1, "Cat6", 25.0)));
        SheetData b = sheet("b", SchemaMode.SIGNAL, "Б", List.of(), List.of(
                wire(SchemaMode.SIGNAL, 3, "Cat6", 8.0), wire(SchemaMode.SIGNAL, 2, "Cat6", 25.0)));

        var wiring = SceneSpecCalc.wiring(List.of(a, b), t -> profile(10, 20), t -> null);

        var ten = wiring.purchase().stream().filter(r -> Double.valueOf(10).equals(r.lengthM())).findFirst()
                .orElseThrow();
        var twenty = wiring.purchase().stream().filter(r -> Double.valueOf(20).equals(r.lengthM())).findFirst()
                .orElseThrow();
        // А: 2 линии по 8 м -> 2x10; линия 25 м -> 20+10. Итого 10 м: 3, 20 м: 1.
        assertArrayEquals(new int[]{3, 5}, ten.counts(), "10 м: А 2+1, Б 3+2 -- каждая схема отдельной колонкой");
        assertArrayEquals(new int[]{1, 2}, twenty.counts());
        assertEquals(2, wiring.purchase().size(), "одинаковые длины разных схем -- одна строка");

        assertEquals(1, wiring.splices().size(), "одна и та же сплайсовка 25 м -- одна строка");
        assertArrayEquals(new int[]{1, 2}, wiring.splices().get(0).lineCounts());
        assertEquals(25.0, wiring.splices().get(0).rawLengthM());
    }

    @Test
    void wiringOfPowerAndSignalSheetsKeepsModeSeparateButUsesOneColumnSet() {
        SheetData p = sheet("p", SchemaMode.POWER, "Сила", List.of(), List.of(
                wire(SchemaMode.POWER, 4, "Socapex", 5.0)));
        SheetData s = sheet("s", SchemaMode.SIGNAL, "Сигнал", List.of(), List.of(
                wire(SchemaMode.SIGNAL, 2, "Cat6", 5.0)));

        var wiring = SceneSpecCalc.wiring(List.of(p, s), t -> null, t -> null);

        assertEquals(2, wiring.purchase().size());
        var power = wiring.purchase().get(0);
        assertEquals("Питание", power.modeLabel());
        assertArrayEquals(new int[]{4, 0}, power.counts(), "питание -- только в колонке схемы питания");
        var signal = wiring.purchase().get(1);
        assertEquals("Сигнал", signal.modeLabel());
        assertArrayEquals(new int[]{0, 2}, signal.counts());
    }

    @Test
    void adapterWithFixedLengthAndUnregisteredWireAreReportedPerSheet() {
        SheetData a = sheet("a", SchemaMode.SIGNAL, "А", List.of(), List.of(
                wire(SchemaMode.SIGNAL, 2, "HDMI-переходник", null), wire(SchemaMode.SIGNAL, 1, "Свободный", 12.0)));
        SheetData b = sheet("b", SchemaMode.SIGNAL, "Б", List.of(), List.of(
                wire(SchemaMode.SIGNAL, 3, "HDMI-переходник", null), wire(SchemaMode.SIGNAL, 4, "Свободный", 3.0)));

        var wiring = SceneSpecCalc.wiring(List.of(a, b), t -> null, t -> "HDMI-переходник".equals(t) ? 1.5 : null);

        var adapter = wiring.purchase().stream().filter(r -> r.wireType().equals("HDMI-переходник")).findFirst()
                .orElseThrow();
        assertEquals(1.5, adapter.lengthM());
        assertArrayEquals(new int[]{2, 3}, adapter.counts());
        assertEquals("переходник, фиксированная длина", adapter.note());

        var free = wiring.purchase().stream().filter(r -> r.wireType().equals("Свободный")).findFirst().orElseThrow();
        assertNull(free.lengthM());
        assertArrayEquals(new int[]{1, 4}, free.counts());
        assertTrue(free.note().contains("А — 12 м") && free.note().contains("Б — 12 м"),
                "метраж свободного провода -- по схемам: А 12 м, Б 3x4=12 м: " + free.note());
    }

    @Test
    void edgesWithoutStructuredWireAreCountedAsUncounted() {
        SchemaEdge plain = new SchemaEdge(SchemaMode.SIGNAL, "a", "b", "просто линия");
        SheetData a = sheet("a", SchemaMode.SIGNAL, "А", List.of(), List.of(plain, plain,
                wire(SchemaMode.SIGNAL, 1, "Cat6", 1.0)));
        SheetData b = sheet("b", SchemaMode.POWER, "Б", List.of(), List.of(plain));

        var wiring = SceneSpecCalc.wiring(List.of(a, b), t -> null, t -> null);

        assertEquals(3, wiring.uncountedEdges(), "связи без структурированной подписи из ВСЕХ схем сцены");
    }

    @Test
    void wireTotalsListLinesAndMetersPerSheet() {
        SheetData a = sheet("a", SchemaMode.SIGNAL, "А", List.of(), List.of(wire(SchemaMode.SIGNAL, 2, "Cat6", 10.0)));
        SheetData b = sheet("b", SchemaMode.SIGNAL, "Б", List.of(), List.of(wire(SchemaMode.SIGNAL, 3, "Cat6", 5.0)));
        SheetData single = sheet("c", SchemaMode.POWER, "С", List.of(), List.of(wire(SchemaMode.POWER, 1, "P", 7.0)));

        var totals = SceneSpecCalc.wireTotals(List.of(single, a, b));

        var cat6 = totals.stream().filter(r -> r.wireType().equals("Cat6")).findFirst().orElseThrow();
        assertArrayEquals(new int[]{0, 2, 3}, cat6.lineCounts());
        assertTrue(cat6.unit().contains("А — 20 м") && cat6.unit().contains("Б — 15 м"), cat6.unit());
        var power = totals.stream().filter(r -> r.wireType().equals("P")).findFirst().orElseThrow();
        assertEquals("шт линий (~7 м суммарно)", power.unit());
        assertEquals("Питание: P", power.modeLabel() + ": " + power.wireType());
    }

    // ---- сцена целиком (с AppModel): D6 ----

    private static AppModel model(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        return model;
    }

    private static CabinetType type() {
        CabinetType ct = new CabinetType();
        ct.setName("Test P3");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setPowerConsumptionW(150);
        ct.setWeightKg(12);
        return ct;
    }

    @Test
    void sceneSpecDoesNotSeeScreensNodesAndEdgesOfAnotherScene(@TempDir Path dir) {
        AppModel model = model(dir);
        CabinetType ct = model.addCabinetType(type());

        Scene hall = model.addScene("Зал");
        model.selectScene(hall);
        model.addScreen("Главный", ct.getId(), 2, 3, 0, 0);
        model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER, "Только в зале", 0, 0, null);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CONVERTER, "Конвертер", 10, 0, null);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CONVERTER, "Конвертер2", 20, 0, null);
        SchemaEdge hallEdge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), b.getId(), null);
        model.updateSchemaEdgeWire(hallEdge, 2, "Cat6", 10.0);

        Scene foyer = model.addScene("Фойе");
        model.selectScene(foyer);
        model.addScreen("Малый", ct.getId(), 1, 1, 0, 0);
        model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "Только в фойе", 0, 0, null);

        // сцены считаются независимо, независимо от «текущей» сцены модели
        assertEquals(6, SceneSpecCalc.cabinetCounts(model, hall).get(ct), "в зале 2x3 кабинета, малый экран фойе не примешан");
        assertEquals(1, SceneSpecCalc.cabinetCounts(model, foyer).get(ct));

        List<SheetData> hallSheets = SceneSpecCalc.sheetsOf(model, hall);
        List<SheetData> foyerSheets = SceneSpecCalc.sheetsOf(model, foyer);
        var hallEquipment = SceneSpecCalc.equipment(hallSheets, SceneSpecCalcTest::label);
        var foyerEquipment = SceneSpecCalc.equipment(foyerSheets, SceneSpecCalcTest::label);

        assertTrue(hallEquipment.stream().anyMatch(r -> r.label().equals("Только в зале")));
        assertFalse(hallEquipment.stream().anyMatch(r -> r.label().equals("Только в фойе")));
        assertTrue(foyerEquipment.stream().anyMatch(r -> r.label().equals("Только в фойе")));
        assertFalse(foyerEquipment.stream().anyMatch(r -> r.label().equals("Только в зале")));

        var hallWiring = SceneSpecCalc.wiring(hallSheets, t -> null, t -> null);
        var foyerWiring = SceneSpecCalc.wiring(foyerSheets, t -> null, t -> null);
        assertEquals(1, hallWiring.purchase().size(), "кабель зала есть в спецификации зала");
        assertTrue(foyerWiring.purchase().isEmpty(), "и отсутствует в спецификации фойе");
        assertTrue(SceneSpecCalc.wireTotals(foyerSheets).isEmpty());
    }

    @Test
    void sheetsOfOrdersPowerSheetsFirstThenSignalByOrderIndex(@TempDir Path dir) {
        AppModel model = model(dir);
        Scene scene = model.addScene("Сцена");
        model.selectScene(scene);
        SchemaSheet power2 = model.addSchemaSheet(SchemaMode.POWER, "Сила 2");
        SchemaSheet signal2 = model.addSchemaSheet(SchemaMode.SIGNAL, "Сигнал 2");
        model.selectSchemaSheet(signal2);
        model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER, "C", 0, 0, null);
        model.selectSchemaSheet(power2);
        model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "S", 0, 0, null);

        List<SheetData> sheets = SceneSpecCalc.sheetsOf(model, scene);
        List<Column> cols = SceneSpecCalc.columns(sheets);

        assertEquals(List.of("Схема питания", "Сила 2", "Схема сигнала", "Сигнал 2"),
                cols.stream().map(Column::title).toList());
        assertEquals(4, sheets.size());
        var rows = SceneSpecCalc.equipment(sheets, SceneSpecCalcTest::label);
        var s = rows.stream().filter(r -> r.label().equals("S")).findFirst().orElseThrow();
        assertArrayEquals(new int[]{0, 1, 0, 0}, s.counts(), "узел вставлен в открытую (вторую) схему питания");
        var c = rows.stream().filter(r -> r.label().equals("C")).findFirst().orElseThrow();
        assertArrayEquals(new int[]{0, 0, 0, 1}, c.counts());
    }

    @Test
    void sceneWithoutScreensAndSchemesStillHasColumnsAndEmptyAggregates(@TempDir Path dir) {
        AppModel model = model(dir);
        Scene empty = model.addScene("Пустая");
        model.selectScene(empty);

        List<SheetData> sheets = SceneSpecCalc.sheetsOf(model, empty);

        assertEquals(2, SceneSpecCalc.columns(sheets).size(), "по схеме на режим есть всегда (инвариант листов)");
        assertTrue(SceneSpecCalc.cabinetCounts(model, empty).isEmpty());
        assertTrue(SceneSpecCalc.equipment(sheets, SceneSpecCalcTest::label).isEmpty());
        var wiring = SceneSpecCalc.wiring(sheets, t -> null, t -> null);
        assertTrue(wiring.purchase().isEmpty());
        assertTrue(wiring.splices().isEmpty());
        assertEquals(0, wiring.uncountedEdges());
        assertEquals(Map.of(), SceneSpecCalc.cabinetCounts(model, empty));
    }
}
