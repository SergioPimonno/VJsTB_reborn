package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.NetworkDeviceCategory;
import com.vjstb.ledscheme.model.NetworkDeviceType;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** docs/schema-ports-rework/PLAN.md, задача T3.4 (D11) — блок сетевого оборудования
 *  из библиотеки {@link NetworkDeviceType} на общей схеме СИГНАЛА: узел {@code
 *  CUSTOM} с картой "Сеть" (Ethernet/Fiber, оба {@link PortDirection#IN_OUT}, роль
 *  {@link InterfaceRole#NETWORK}), и обновление её из библиотеки по команде. */
class NetworkDeviceSchemaNodeTest {

    private static AppModel model(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        return model;
    }

    private static NetworkDeviceType type(String name, int eth, int optical) {
        NetworkDeviceType t = new NetworkDeviceType();
        t.setName(name);
        t.setCategory(NetworkDeviceCategory.SWITCH);
        t.setEthernetPortCount(eth);
        t.setOpticalPortCount(optical);
        return t;
    }

    @Test
    void addsCustomNodeWithNetworkCardMatchingDeviceType(@TempDir Path dir) {
        AppModel model = model(dir);
        NetworkDeviceType swtch = model.addNetworkDeviceType(type("Aruba 2930F", 24, 2));

        SchemaNode node = model.addSchemaNodeFromNetworkDevice(swtch, 100, 100);

        assertEquals(SchemaNodeType.CUSTOM, node.getType());
        assertEquals(SchemaMode.SIGNAL, node.getMode());
        assertEquals(swtch.getId(), node.getNetworkDeviceTypeId());
        assertEquals(1, node.getCards().size());
        SchemaCard card = node.getCards().get(0);
        assertEquals("Сеть", card.getName());
        assertEquals(2, card.getPorts().size(), "Ethernet и Fiber — обе группы заданы у этого типа");

        CardPort eth = card.getPorts().get(0);
        assertEquals("Ethernet", eth.getConnectorType());
        assertEquals(PortDirection.IN_OUT, eth.getDirection());
        assertEquals(24, eth.getCount());
        assertEquals(InterfaceRole.NETWORK, eth.getRole());

        CardPort fiber = card.getPorts().get(1);
        assertEquals("Fiber", fiber.getConnectorType());
        assertEquals(PortDirection.IN_OUT, fiber.getDirection());
        assertEquals(2, fiber.getCount());
        assertEquals(InterfaceRole.NETWORK, fiber.getRole());
    }

    @Test
    void skipsFiberGroupWhenDeviceHasNoOpticalPorts(@TempDir Path dir) {
        AppModel model = model(dir);
        NetworkDeviceType ap = model.addNetworkDeviceType(type("Ubiquiti AP", 1, 0));

        SchemaNode node = model.addSchemaNodeFromNetworkDevice(ap, 0, 0);

        SchemaCard card = node.getCards().get(0);
        assertEquals(1, card.getPorts().size(), "opticalPortCount == 0 — группы Fiber быть не должно");
        assertEquals("Ethernet", card.getPorts().get(0).getConnectorType());
    }

    @Test
    void addingNodeUndoesAsOneStep(@TempDir Path dir) {
        AppModel model = model(dir);
        NetworkDeviceType swtch = model.addNetworkDeviceType(type("Switch", 8, 0));
        int before = model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).size();

        model.addSchemaNodeFromNetworkDevice(swtch, 0, 0);
        assertEquals(before + 1, model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).size());

        model.undo();
        assertEquals(before, model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).size(),
                "один Ctrl+Z должен убрать узел целиком (карта не даёт отдельной записи отмены)");
    }

    @Test
    void refreshRebuildsPortsFromCurrentLibraryData(@TempDir Path dir) {
        AppModel model = model(dir);
        NetworkDeviceType swtch = model.addNetworkDeviceType(type("Switch 8", 8, 0));
        SchemaNode node = model.addSchemaNodeFromNetworkDevice(swtch, 0, 0);

        // Библиотеку правят УЖЕ ПОСЛЕ того, как блок стоит на схеме — типичный
        // сценарий пункта меню «Обновить порты из библиотеки».
        swtch.setEthernetPortCount(16);
        swtch.setOpticalPortCount(4);
        model.updateNetworkDeviceType(swtch);

        model.refreshNetworkDevicePorts(node);

        SchemaCard card = node.getCards().get(0);
        assertEquals(2, card.getPorts().size());
        assertEquals(16, card.getPorts().get(0).getCount());
        assertEquals(4, card.getPorts().get(1).getCount());
    }

    @Test
    void refreshThrowsWhenNodeHasNoNetworkDeviceLink(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode plain = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "Обычный узел", 0, 0, null);

        assertThrows(IllegalStateException.class, () -> model.refreshNetworkDevicePorts(plain));
    }

    @Test
    void refreshThrowsWhenTypeWasDeletedFromLibrary(@TempDir Path dir) {
        AppModel model = model(dir);
        NetworkDeviceType swtch = model.addNetworkDeviceType(type("Temp switch", 4, 0));
        SchemaNode node = model.addSchemaNodeFromNetworkDevice(swtch, 0, 0);

        model.deleteNetworkDeviceType(swtch.getId());

        assertThrows(IllegalStateException.class, () -> model.refreshNetworkDevicePorts(node));
    }

    /** "Защита от дурака пропускает NETWORK" (см. {@code
     *  SchemaCanvasPanel.directionErrorForTest}) — на уровне модели проверяем
     *  только то, что относится к AppModel: гнёзда действительно приходят с ролью
     *  NETWORK, а не что-то другое по ошибке эвристики (сама проверка направления —
     *  {@code SchemaNetworkDirectionErrorTest} в пакете ui). */
    @Test
    void bothPortGroupsAlwaysGetNetworkRoleRegardlessOfCounts(@TempDir Path dir) {
        AppModel model = model(dir);
        NetworkDeviceType t = model.addNetworkDeviceType(type("Router", 4, 1));
        SchemaNode node = model.addSchemaNodeFromNetworkDevice(t, 0, 0);

        for (CardPort p : node.getCards().get(0).getPorts()) {
            assertEquals(InterfaceRole.NETWORK, p.getRole());
        }
        assertTrue(node.getPowerConnectors().isEmpty(), "сетевое устройство не заводит попутно разъёмы питания");
        assertEquals(1, node.getCards().size());
    }
}
