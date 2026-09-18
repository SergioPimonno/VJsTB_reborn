package com.vjstb.ledscheme.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link SchemaFixtures} (docs/schema-ports-rework/PLAN.md, задача T0.1) — проверяет,
 *  что синтетическая сцена реально повторяет структуру, на которой опираются задачи
 *  следующих этапов (многогрупповые карты Q8, генлок-петля MCTRL, транзитный силовой
 *  щит «Проходная») — и что она переживает круговое сохранение/чтение JSON, как и
 *  любая другая сцена. Это не тест раскладки/ролей самих по себе (те появятся в T1.3/
 *  T2.1) — только гарантия, что фикстур, на который они будут опираться, содержит
 *  ожидаемые данные. */
class SchemaFixturesTest {

    @Test
    void signalPartHasMultiGroupCardsMatchingBarMitzvahComposition(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SchemaNode q8 = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "PixelHue Q8");
        assertEquals(11, q8.getCards().size(), "6 карт входа + 4 карты выхода + MVR");
        long inputCards = q8.getCards().stream().filter(c -> c.getName().startsWith("HDMI+DP+SDI Input")).count();
        long outputCards = q8.getCards().stream().filter(c -> c.getName().startsWith("HDMI+SDI+Fiber Output")).count();
        assertEquals(6, inputCards);
        assertEquals(4, outputCards);
        SchemaCard firstInput = q8.getCards().get(0);
        assertEquals(3, firstInput.getPorts().size(), "каждая карта входа — 3 разные группы разъёмов на одной карте");
        assertEquals(12, firstInput.totalInputs(), "4×HDMI + 4×DP + 4×SDI");

        SchemaNode d3main = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "Disguise D3 (Main)");
        assertEquals(5, d3main.getCards().size(), "Basic Set + 4×VFC HDMI2.0");

        SchemaNode mctrl1 = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "MCTRL4K #1");
        SchemaNode mctrl2 = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "MCTRL4K #2");
        assertEquals(1, mctrl1.getCards().size());
        assertEquals(6, mctrl1.getCards().get(0).getPorts().size(), "6 разных групп на одной карте Basic Set");

        // Контрольный случай авто-транзита (PLAN.md §2.3): ровно один вход и один выход
        // Genlock (SDI) на одной карте — физическая петля через контроллер дальше по цепи.
        CardPort mctrl2GenlockIn = SchemaFixtures.portOnCard(mctrl2, "Basic Set", "Genlock (SDI)");
        assertEquals(PortDirection.IN, mctrl2GenlockIn.getDirection());
        long genlockGroupsOnMctrl1 = mctrl1.getCards().get(0).getPorts().stream()
                .filter(p -> p.getConnectorType().equals("Genlock (SDI)")).count();
        assertEquals(2, genlockGroupsOnMctrl1, "ровно одна IN и одна OUT группа Genlock — кандидат на авто-транзит");

        long edgesIntoMctrl2Genlock = model.getCurrentScene().getSchemaEdges().stream()
                .filter(e -> e.getMode() == SchemaMode.SIGNAL && mctrl2GenlockIn.getId().equals(e.getToPortId()))
                .count();
        assertEquals(1, edgesIntoMctrl2Genlock, "MCTRL2 получает генлок ТОЛЬКО транзитом через MCTRL1, не напрямую от источника");
    }

    @Test
    void powerPartHasThruCandidateAndFanOutGroups(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SchemaNode thru1 = SchemaFixtures.nodeByLabel(model, SchemaMode.POWER, "Проходная 1");
        assertEquals(3, thru1.getPowerConnectors().size(), "CEE 32A IN, CEE 32A OUT, CEE 16A OUT");

        // Контрольный случай авто-транзита в питании (PLAN.md §2.3): ровно один вход и
        // один выход того же типа с count==1 каждый.
        CardPort thru32In = SchemaFixtures.powerPort(thru1, "CEE 32A", PortDirection.IN);
        CardPort thru32Out = SchemaFixtures.powerPort(thru1, "CEE 32A", PortDirection.OUT);
        assertEquals(1, thru32In.getCount());
        assertEquals(1, thru32Out.getCount());

        // 6×CEE 16A OUT — это ОТХОДЯЩИЕ (нет входа 16A на этом щите), не транзит.
        CardPort thru16Out = SchemaFixtures.powerPort(thru1, "CEE 16A", PortDirection.OUT);
        assertEquals(6, thru16Out.getCount());
        assertTrue(thru1.getPowerConnectors().stream()
                .noneMatch(p -> p.getConnectorType().equals("CEE 16A") && p.getDirection() == PortDirection.IN));

        SchemaNode alpenBox = SchemaFixtures.nodeByLabel(model, SchemaMode.POWER, "AlpenBox 125A->4x32A");
        assertEquals(5, alpenBox.getPowerConnectors().size());
        CardPort alpen32Out = SchemaFixtures.powerPort(alpenBox, "CEE 32A", PortDirection.OUT);
        assertEquals(4, alpen32Out.getCount(), "4 отходящих CEE 32A, как в реальном проекте");

        long edgesFromAlpen32 = model.getCurrentScene().getSchemaEdges().stream()
                .filter(e -> e.getMode() == SchemaMode.POWER && alpen32Out.getId().equals(e.getFromPortId()))
                .count();
        assertEquals(2, edgesFromAlpen32, "одна группа 4×CEE 32A разведена на обе «Проходные»");
    }

    @Test
    void sceneSurvivesJsonRoundTrip(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        int nodeCount = model.getCurrentScene().getSchemaNodes().size();
        int edgeCount = model.getCurrentScene().getSchemaEdges().size();
        assertTrue(nodeCount > 10 && edgeCount > 10, "фикстура должна быть содержательной");

        AppModel reloaded = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        var scene = reloaded.getWorkspace().getProjects().get(0).getScenes().stream()
                .filter(s -> s.getName().equals("Основной зал")).findFirst().orElseThrow();
        assertEquals(nodeCount, scene.getSchemaNodes().size());
        assertEquals(edgeCount, scene.getSchemaEdges().size());

        SchemaNode q8 = scene.getSchemaNodes().stream()
                .filter(n -> n.getMode() == SchemaMode.SIGNAL && "PixelHue Q8".equals(n.getLabel()))
                .findFirst().orElseThrow();
        assertEquals(11, q8.getCards().size());
        List<CardPort> firstInputPorts = q8.getCards().get(0).getPorts();
        assertNotNull(firstInputPorts.get(0).getId(), "id гнёзд не теряются при перечитывании JSON");
    }
}
