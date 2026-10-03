package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Подозрение пользователя 2026-10-03: «объединение линий в шину считается в спецификации как одна
 *  линия, а должно — все вложенные кабели». «Объединить в шину» — только признак отрисовки гнезда
 *  ({@link SchemaNode#getMergedBusPortIds()}), связи в модели остаются отдельными, спецификация
 *  суммирует {@code wireCount} каждой. Тест фиксирует, что итог одинаков до и после объединения и
 *  равен сумме кабелей всех связей гнезда. */
class BusMergeSpecTest {

    private static SchemaCard card(PortDirection dir, int count) {
        List<CardPort> ports = new ArrayList<>();
        ports.add(new CardPort("CEE 32A", dir, count));
        return new SchemaCard("Карта", ports);
    }

    @Test
    void mergingIntoBusDoesNotChangeCableTotals(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);

        SchemaNode src = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Щит", 0, 0, null);
        src.getCards().add(card(PortDirection.OUT, 6));
        String srcPort = src.getCards().get(0).getPorts().get(0).getId();
        int[] counts = {2, 3, 1};
        List<SchemaEdge> edges = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            SchemaNode dst = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Приёмник " + i,
                    300, i * 100, null);
            dst.getCards().add(card(PortDirection.IN, 3));
            String dstPort = dst.getCards().get(0).getPorts().get(0).getId();
            SchemaEdge e = model.addSchemaEdge(SchemaMode.POWER, src.getId(), srcPort, dst.getId(), dstPort, null);
            model.updateSchemaEdgeWire(e, counts[i], "CEE 32A", 10.0);
            edges.add(e);
        }

        var before = SceneSpecCalc.wireTotals(SceneSpecCalc.sheetsOf(model, scene));
        assertEquals(1, before.size());
        assertEquals(6, before.get(0).lineCounts()[0], "2+3+1 кабелей");

        model.setSchemaBusMerged(src, srcPort, true);
        assertTrue(src.isBusMerged(srcPort));

        var after = SceneSpecCalc.wireTotals(SceneSpecCalc.sheetsOf(model, scene));
        assertEquals(6, after.get(0).lineCounts()[0], "шина не меняет число кабелей");
        assertEquals(60.0, after.get(0).lengthsM()[0], 1e-9, "метраж — по кабелям (6×10 м)");

        var wiring = SceneSpecCalc.wiring(SceneSpecCalc.sheetsOf(model, scene), t -> null, t -> null);
        int purchased = wiring.purchase().stream().mapToInt(r -> r.counts()[0]).sum();
        assertEquals(6, purchased, "в закупке тоже 6, а не 1 на шину");
        assertEquals(0, wiring.uncountedEdges());
    }

    /** Проект «Лемана» (баг-репорт 2026-10-03): у гнезда «4×DisplayPort» три связи «1×DP» к PixelHue и
     *  четвёртая, НЕподписанная, к монитору. Схема показывала «4×DP», спецификация — 3. Неподписанная
     *  связь пучка считается за один кабель общего типа, как на чипе шины. */
    @Test
    void unlabeledEdgeOfTheSameSocketInheritsBundleType(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);

        SchemaNode src = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "Resolume", 0, 0, null);
        src.getCards().add(card(PortDirection.OUT, 4));
        String srcPort = src.getCards().get(0).getPorts().get(0).getId();
        SchemaNode hub = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CONVERTER, "PixelHue", 300, 0, null);
        hub.getCards().add(card(PortDirection.IN, 4));
        String hubPort = hub.getCards().get(0).getPorts().get(0).getId();
        SchemaNode monitor = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.MONITOR, "Монитор", 300, 200, null);
        monitor.getCards().add(card(PortDirection.IN, 1));
        String monitorPort = monitor.getCards().get(0).getPorts().get(0).getId();

        for (int i = 0; i < 3; i++) {
            SchemaEdge e = model.addSchemaEdge(SchemaMode.SIGNAL, src.getId(), srcPort, hub.getId(), hubPort, null);
            model.updateSchemaEdgeWire(e, 1, "DP", null);
        }
        model.addSchemaEdge(SchemaMode.SIGNAL, src.getId(), srcPort, monitor.getId(), monitorPort, null);

        var totals = SceneSpecCalc.wireTotals(SceneSpecCalc.sheetsOf(model, scene));
        assertEquals(1, totals.size());
        assertEquals(4, java.util.Arrays.stream(totals.get(0).lineCounts()).sum(),
                "3 подписанных + 1 неподписанная из того же гнезда");
        var wiring = SceneSpecCalc.wiring(SceneSpecCalc.sheetsOf(model, scene), t -> null, t -> null);
        assertEquals(4, wiring.purchase().stream().flatMapToInt(r -> java.util.Arrays.stream(r.counts())).sum());
        assertEquals(0, wiring.uncountedEdges());
    }

    @Test
    void unlabeledEdgeOutsideAnyBundleIsStillNotCounted(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "A", 0, 0, null);
        a.getCards().add(card(PortDirection.OUT, 1));
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CONVERTER, "B", 300, 0, null);
        b.getCards().add(card(PortDirection.IN, 1));
        model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), a.getCards().get(0).getPorts().get(0).getId(), b.getId(),
                b.getCards().get(0).getPorts().get(0).getId(), null);

        var wiring = SceneSpecCalc.wiring(SceneSpecCalc.sheetsOf(model, scene), t -> null, t -> null);
        assertEquals(1, wiring.uncountedEdges());
        assertEquals(0, SceneSpecCalc.wireTotals(SceneSpecCalc.sheetsOf(model, scene)).size());
    }
}

