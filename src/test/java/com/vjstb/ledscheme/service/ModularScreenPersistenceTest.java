package com.vjstb.ledscheme.service;

import static com.vjstb.ledscheme.service.ModularFixture.rect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ModuleNumberingPattern;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Карты и блоки в файле проекта (docs/receiver-cards/PLAN.md, 4.2): круговой обмен через диск без потерь, старые
 * проекты без новых полей открываются с пустыми списками, а проект, пересохранённый СТАРЫМ клиентом (тот карт и блоков
 * не знает и теряет, а цепочки с их id остаются), открывается без битых звеньев в цепочках.
 */
class ModularScreenPersistenceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private File file;

    private AppModel open(Path dir) {
        file = new File(dir.toFile(), "w.json");
        return new AppModel(new WorkspaceStore(file));
    }

    private Screen build(AppModel m) {
        CabinetType module = new CabinetType();
        module.setName("Модуль");
        module.setReceiverCardSeparate(true);
        module.setWidthMm(250);
        module.setHeightMm(250);
        m.addCabinetType(module);
        ReceiverCardType card = new ReceiverCardType();
        card.setName("К8");
        card.setModuleOutputs(8);
        m.addReceiverCardType(card);
        PowerSupplyType psu = new PowerSupplyType();
        psu.setName("БП8");
        psu.setOutputs(8);
        psu.setCardOutput(true);
        m.addPowerSupplyType(psu);
        m.selectProject(m.addProject("П"));
        Scene scene = m.addScene("С");
        m.selectScene(scene);
        Screen s = m.addScreen("Э", module.getId(), 2, 4, 0, 0);
        m.selectScreen(s);
        m.assignReceiverCardsAuto(rect(0, 0, 1, 3), card.getId(), 2, 2);
        m.assignPowerSuppliesAuto(rect(0, 0, 1, 3), psu.getId());
        m.setModuleNumbering(new ModuleNumberingPattern(ModuleNumberingPattern.Corner.BOTTOM_RIGHT,
                ModuleNumberingPattern.Axis.COLUMNS, true));
        ReceiverCardInstance first = s.getReceiverCards().get(0);
        m.setReceiverCardPlace(first.getId(), first.getModuleIds().get(1));
        m.setReceiverCardManualOrder(first.getId(), List.of(first.getModuleIds().get(3), first.getModuleIds().get(0)));
        scene.getSignalChains().add(new SignalChain(1, false, List.of(s.getReceiverCards().get(0).getId(),
                s.getReceiverCards().get(1).getId())));
        scene.getPowerChains().add(new PowerChain(2, List.of(s.getPowerSupplies().get(0).getId())));
        m.updateScreenNotes(s, "сохранить"); // любая правка с записью проекта на диск
        return s;
    }

    @Test
    void cardsBlocksAreasPatternAndChainsSurviveARestart(@TempDir Path dir) {
        AppModel m = open(dir);
        Screen s = build(m);
        String firstCard = s.getReceiverCards().get(0).getId();

        AppModel again = new AppModel(new WorkspaceStore(file));
        Screen back = again.getWorkspace().getProjects().get(0).getScenes().get(0).getScreens().get(0);

        assertEquals(2, back.getReceiverCards().size());
        ReceiverCardInstance c0 = back.getReceiverCards().get(0);
        assertEquals(firstCard, c0.getId());
        assertEquals(s.getReceiverCards().get(0).getModuleIds(), c0.getModuleIds());
        assertEquals(s.getReceiverCards().get(0).getHostModuleId(), c0.getHostModuleId());
        assertEquals(s.getReceiverCards().get(0).getManualPortOrder(), c0.getManualPortOrder());
        assertNull(back.getReceiverCards().get(1).getManualPortOrder(), "null — порядок по шаблону — не превращается в []");
        assertEquals(ModuleNumberingPattern.Corner.BOTTOM_RIGHT, back.getModuleNumbering().getCorner());
        assertEquals(ModuleNumberingPattern.Axis.COLUMNS, back.getModuleNumbering().getAxis());
        assertTrue(back.getModuleNumbering().isSnake());
        assertEquals(s.getPowerSupplies().size(), back.getPowerSupplies().size());
        assertEquals(s.getPowerSupplies().get(0).getId(), back.getPowerSupplies().get(0).getId());
        assertEquals(1, back.getPowerAutoAreas().size());
        assertEquals(s.getPowerAutoAreas().get(0).getRect(), back.getPowerAutoAreas().get(0).getRect());
        assertEquals(s.getPowerAutoAreas().get(0).getPowerSupplyTypeId(),
                back.getPowerAutoAreas().get(0).getPowerSupplyTypeId());
        Scene scene = again.getWorkspace().getProjects().get(0).getScenes().get(0);
        assertEquals(2, scene.getSignalChains().get(0).getCabinetInstanceIds().size(), "звенья-карты читаются как есть");
        assertEquals(1, scene.getPowerChains().get(0).getCabinetInstanceIds().size());
    }

    @Test
    void anOldProjectWithoutTheNewFieldsOpensWithEmptyLists(@TempDir Path dir) throws Exception {
        AppModel m = open(dir);
        build(m);
        // имитация файла, записанного до появления модульных экранов
        stripFields(Set.of("receiverCards", "moduleNumbering", "powerSupplies", "powerAutoAreas"));

        AppModel again = new AppModel(new WorkspaceStore(file));
        Screen back = again.getWorkspace().getProjects().get(0).getScenes().get(0).getScreens().get(0);

        assertTrue(back.getReceiverCards().isEmpty());
        assertTrue(back.getPowerSupplies().isEmpty());
        assertTrue(back.getPowerAutoAreas().isEmpty());
        assertNotNull(back.getModuleNumbering());
        assertEquals(ModuleNumberingPattern.Corner.TOP_LEFT, back.getModuleNumbering().getCorner());
        assertFalse(back.getModuleNumbering().isSnake());
    }

    @Test
    void chainsLeftBehindByAnOldClientLoseTheirDeadUnitsButKeepTheirPorts(@TempDir Path dir) throws Exception {
        AppModel m = open(dir);
        build(m);
        stripFields(Set.of("receiverCards", "powerSupplies", "powerAutoAreas"));

        AppModel again = new AppModel(new WorkspaceStore(file));
        Scene scene = again.getWorkspace().getProjects().get(0).getScenes().get(0);

        assertEquals(1, scene.getSignalChains().size(), "цепочка и её порт остались");
        assertEquals(1, scene.getSignalChains().get(0).getPortNumber());
        assertTrue(scene.getSignalChains().get(0).getCabinetInstanceIds().isEmpty());
        assertEquals(2, scene.getPowerChains().get(0).getPhase());
        assertTrue(scene.getPowerChains().get(0).getCabinetInstanceIds().isEmpty());
    }

    /** Убирает поля из всех объектов файла проекта, как будто файл писал клиент, их не знающий. */
    private void stripFields(Set<String> names) throws Exception {
        JsonNode root = JSON.readTree(file);
        strip(root, names);
        Files.writeString(file.toPath(), JSON.writeValueAsString(root));
    }

    private static void strip(JsonNode node, Set<String> names) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            names.forEach(obj::remove);
            Iterator<JsonNode> it = obj.elements();
            while (it.hasNext()) {
                strip(it.next(), names);
            }
        } else if (node.isArray()) {
            node.forEach(n -> strip(n, names));
        }
    }
}
