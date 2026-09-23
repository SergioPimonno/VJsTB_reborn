package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.NetworkDeviceCategory;
import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import com.vjstb.ledscheme.model.NetworkDeviceType;
import com.vjstb.ledscheme.model.NetworkLink;
import com.vjstb.ledscheme.model.NetworkManagerPlan;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.schema.SchemaFixtures;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.ui.NetworkCanvasPanel;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Автоперенос сетей из общей схемы (запрос пользователя после отказа от
 *  статуса портов Novastar — "собери план по автопереносу сетей из общей
 *  схемы в менеджер", см. NETWORK_MANAGER_NOTES.md) — {@link
 *  NetworkCanvasPanel#previewSchemaImport}/{@link
 *  NetworkCanvasPanel#applySchemaImport}, первый потребитель заготовки
 *  {@code AppModel#networkGraphFromScene} (docs/schema-ports-rework/PLAN.md,
 *  T5.4). Разбивка на группы {@link NetworkCanvasPanelRenderTest} не
 *  покрывает — тестируется здесь отдельно от рендера. */
class SchemaImportTest {

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

    @Test
    void groupsByConnectedComponentsAndSuggestsSwitchNameForSingleSwitchGroup(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        NetworkDeviceType type = model.addNetworkDeviceType(switchType());
        SchemaNode swtch = model.addSchemaNodeFromNetworkDevice(type, 2000, 0);
        swtch.setLabel("Свитч сцены");

        NetworkCanvasPanel canvas = new NetworkCanvasPanel(model, settings(dir));
        canvas.setPlan(new NetworkManagerPlan(), null);

        NetworkCanvasPanel.SchemaImportPreview preview = canvas.previewSchemaImport();

        assertEquals(0, preview.alreadyImportedCount());
        assertEquals(2, preview.newGroups().size(), "D3+Q8 (связаны) и одинокий свитч -- две группы");

        SchemaNode d3 = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "Disguise D3 (Main)");
        SchemaNode q8 = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "PixelHue Q8");

        Optional<NetworkCanvasPanel.ImportGroup> deviceGroup = preview.newGroups().stream()
                .filter(g -> !g.devices().isEmpty()).findFirst();
        assertTrue(deviceGroup.isPresent());
        List<String> deviceIds = deviceGroup.get().devices().stream()
                .map(AppModel.NetworkGraphDevice::nodeId).toList();
        assertEquals(2, deviceIds.size());
        assertTrue(deviceIds.contains(d3.getId()));
        assertTrue(deviceIds.contains(q8.getId()));
        assertEquals(1, deviceGroup.get().links().size());
        assertTrue(deviceGroup.get().switches().isEmpty());

        Optional<NetworkCanvasPanel.ImportGroup> switchGroup = preview.newGroups().stream()
                .filter(g -> !g.switches().isEmpty()).findFirst();
        assertTrue(switchGroup.isPresent());
        assertEquals(1, switchGroup.get().switches().size());
        assertTrue(switchGroup.get().devices().isEmpty());
        assertEquals("Свитч сцены", switchGroup.get().suggestedName(),
                "одинокий свитч -- группа называется по его метке, не 'Импорт N'");
    }

    @Test
    void deviceAlreadyPlacedIsSkippedAndDoesNotDragItsPeerAlong(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SchemaNode d3 = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "Disguise D3 (Main)");

        NetworkManagerPlan plan = new NetworkManagerPlan();
        NetworkDevicePlacement already = new NetworkDevicePlacement();
        already.setLinkedSchemaNodeId(d3.getId());
        plan.getDevices().add(already);

        NetworkCanvasPanel canvas = new NetworkCanvasPanel(model, settings(dir));
        canvas.setPlan(plan, null);

        NetworkCanvasPanel.SchemaImportPreview preview = canvas.previewSchemaImport();

        assertEquals(1, preview.alreadyImportedCount());
        boolean anyGroupStillHasD3 = preview.newGroups().stream()
                .flatMap(g -> g.devices().stream())
                .anyMatch(d -> d.nodeId().equals(d3.getId()));
        assertTrue(!anyGroupStillHasD3, "D3 уже размещён -- не должен предлагаться повторно");
    }

    @Test
    void applySchemaImportAssignsIncreasingFreePortsOnTheSameDevice(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        NetworkCanvasPanel canvas = new NetworkCanvasPanel(model, settings(dir));
        NetworkManagerPlan plan = new NetworkManagerPlan();
        canvas.setPlan(plan, null);

        AppModel.NetworkGraphDevice hub = new AppModel.NetworkGraphDevice("hub", "Hub");
        AppModel.NetworkGraphDevice peerB = new AppModel.NetworkGraphDevice("b", "B");
        AppModel.NetworkGraphDevice peerC = new AppModel.NetworkGraphDevice("c", "C");
        List<AppModel.NetworkGraphLink> links = List.of(
                new AppModel.NetworkGraphLink("hub", "b"),
                new AppModel.NetworkGraphLink("hub", "c"));
        NetworkCanvasPanel.ImportGroup group = new NetworkCanvasPanel.ImportGroup(
                "Тест", List.of(hub, peerB, peerC), List.of(), links);

        List<AppModel.NetworkGraphLink> skipped = canvas.applySchemaImport(List.of(group));

        assertTrue(skipped.isEmpty());
        NetworkDevicePlacement hubPlacement = placementFor(plan, "hub");
        List<Integer> hubPorts = plan.getLinks().stream()
                .filter(l -> l.getFromDeviceId().equals(hubPlacement.getId()))
                .map(NetworkLink::getFromPort).sorted().toList();
        assertEquals(List.of(1, 2), hubPorts, "два кабеля с одного устройства -- первый и второй свободные порты");
    }

    @Test
    void applySchemaImportSkipsLinkWhenDeviceRunsOutOfPorts(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        NetworkCanvasPanel canvas = new NetworkCanvasPanel(model, settings(dir));
        canvas.setPlan(new NetworkManagerPlan(), null);

        // Устройство-связка по умолчанию получает 4 Ethernet-порта (см.
        // NetworkDevicePlacement#ethernetPortCount) -- пятая связь с того же
        // "hub" уже не помещается.
        AppModel.NetworkGraphDevice hub = new AppModel.NetworkGraphDevice("hub", "Hub");
        List<AppModel.NetworkGraphDevice> peers = List.of(
                new AppModel.NetworkGraphDevice("p1", "P1"), new AppModel.NetworkGraphDevice("p2", "P2"),
                new AppModel.NetworkGraphDevice("p3", "P3"), new AppModel.NetworkGraphDevice("p4", "P4"),
                new AppModel.NetworkGraphDevice("p5", "P5"));
        List<AppModel.NetworkGraphLink> links = peers.stream()
                .map(p -> new AppModel.NetworkGraphLink("hub", p.nodeId())).toList();
        List<AppModel.NetworkGraphDevice> devices = new java.util.ArrayList<>();
        devices.add(hub);
        devices.addAll(peers);
        NetworkCanvasPanel.ImportGroup group = new NetworkCanvasPanel.ImportGroup("Тест", devices, List.of(), links);

        List<AppModel.NetworkGraphLink> skipped = canvas.applySchemaImport(List.of(group));

        assertEquals(1, skipped.size(), "пятая связь с 4-портового устройства не помещается");
        assertEquals("p5", skipped.get(0).toNodeId());
    }

    private NetworkDevicePlacement placementFor(NetworkManagerPlan plan, String schemaNodeId) {
        return plan.getDevices().stream()
                .filter(d -> schemaNodeId.equals(d.getLinkedSchemaNodeId()))
                .findFirst().orElseThrow();
    }
}
