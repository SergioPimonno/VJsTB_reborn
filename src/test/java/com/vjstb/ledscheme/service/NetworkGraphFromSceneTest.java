package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.NetworkDeviceCategory;
import com.vjstb.ledscheme.model.NetworkDeviceType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.schema.SchemaFixtures;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Заготовка «сеть из схемы» (docs/schema-ports-rework/PLAN.md, D11/задача T5.4) —
 *  {@link AppModel#networkGraphFromScene}, контрольный случай из самого текста задачи:
 *  "D3 — устройство, свитч — коммутатор, данные LED не попадают" — на фикстуре
 *  {@link SchemaFixtures} (T0.1), которая уже содержит ровно оба вида связей: D3
 *  (Ethernet Cat6 OUT) -> Q8 (MVR, Ethernet Cat5e IN) — настоящая IP-сеть, и
 *  D3->Q8 (HDMI), Q8->MCTRL (HDMI), MCTRL->CVT4K (Fiber), CVT4K->экран (Cat6/RJ45) —
 *  все LED-данные/видео, не сеть. */
class NetworkGraphFromSceneTest {

    private static NetworkDeviceType switchType() {
        NetworkDeviceType t = new NetworkDeviceType();
        t.setName("Aruba 2930F");
        t.setCategory(NetworkDeviceCategory.SWITCH);
        t.setEthernetPortCount(24);
        return t;
    }

    @Test
    void d3IsADeviceSwitchIsASwitchLedDataDoesNotAppear(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        Scene scene = model.getCurrentScene();
        NetworkDeviceType type = model.addNetworkDeviceType(switchType());
        SchemaNode swtch = model.addSchemaNodeFromNetworkDevice(type, 2000, 0);

        AppModel.NetworkGraph graph = model.networkGraphFromScene(scene);

        SchemaNode d3main = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "Disguise D3 (Main)");
        SchemaNode q8 = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "PixelHue Q8");
        SchemaNode mctrl1 = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "MCTRL4K #1");
        SchemaNode cvt = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "CVT4K");
        SchemaNode screenNode = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "Экран 1");

        List<String> deviceIds = graph.devices().stream().map(AppModel.NetworkGraphDevice::nodeId).toList();
        assertTrue(deviceIds.contains(d3main.getId()), "D3 — устройство: его Ethernet-гнездо задействовано сетевой связью");
        assertTrue(deviceIds.contains(q8.getId()), "Q8 — тоже устройство: принимающий конец той же сетевой связи");
        assertFalse(deviceIds.contains(mctrl1.getId()), "MCTRL4K — LED-данные (RJ45/Fiber), не сеть, не должен попасть");
        assertFalse(deviceIds.contains(cvt.getId()), "CVT4K — LED-данные (Fiber/RJ45), не сеть");
        assertFalse(deviceIds.contains(screenNode.getId()), "экран подключён по LED-данным (Ethernet роль LED_DATA), не сеть");

        List<String> switchIds = graph.switches().stream().map(AppModel.NetworkGraphDevice::nodeId).toList();
        assertEquals(List.of(swtch.getId()), switchIds, "коммутатор — только блок с networkDeviceTypeId");
        assertFalse(deviceIds.contains(swtch.getId()), "свитч не подключён ни одной связью — не 'устройство', а именно коммутатор");

        boolean hasD3ToQ8NetworkLink = graph.links().stream()
                .anyMatch(l -> l.fromNodeId().equals(d3main.getId()) && l.toNodeId().equals(q8.getId()));
        assertTrue(hasD3ToQ8NetworkLink, "единственная реальная сетевая связь фикстуры должна попасть в links");
        assertEquals(1, graph.links().size(), "LED-данные и видео-связи фикстуры не должны попадать в links");
    }

    @Test
    void switchAppearsAsASwitchEvenBeforeAnyCableIsConnected(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        Scene scene = model.getCurrentScene();
        NetworkDeviceType type = model.addNetworkDeviceType(switchType());
        SchemaNode swtch = model.addSchemaNodeFromNetworkDevice(type, 2000, 0);

        AppModel.NetworkGraph graph = model.networkGraphFromScene(scene);

        assertEquals(1, graph.switches().size());
        assertEquals(swtch.getId(), graph.switches().get(0).nodeId());
    }

    @Test
    void emptySceneReturnsEmptyGraph(@TempDir Path dir) {
        AppModel model = new AppModel(new com.vjstb.ledscheme.store.WorkspaceStore(
                new java.io.File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));

        AppModel.NetworkGraph graph = model.networkGraphFromScene(model.getCurrentScene());

        assertTrue(graph.devices().isEmpty());
        assertTrue(graph.links().isEmpty());
        assertTrue(graph.switches().isEmpty());
    }
}
