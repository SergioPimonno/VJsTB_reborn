package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.PowerConnectorType;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ChainLinks;
import com.vjstb.ledscheme.service.ChainUnits;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Узел экрана общей схемы и «гнёзда кабинетов» модульного экрана (docs/receiver-cards/PLAN.md, раздел 7, трек E): узел
 * экрана не меняется, вводные «гнёзда» и авто-гнёзда узла строятся по звеньям цепочек — картам (сигнал) и блокам
 * (питание). Трек D перевёл эти места на {@code ChainLinks}; тест фиксирует поведение, чтобы оно не откатилось.
 */
class ModularSchemaSocketsTest {

    private AppModel model;
    private Scene scene;
    private Screen screen;
    private ReceiverCardType cardType;
    private PowerSupplyType psuType;

    private void setUp(Path dir) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        CabinetType module = new CabinetType();
        module.setName("Модуль");
        module.setWidthMm(250);
        module.setHeightMm(250);
        module.setResolutionWidth(64);
        module.setResolutionHeight(64);
        module.setPowerConsumptionW(30);
        module.setPowerConnectorType(PowerConnectorType.POWERCON);
        module.setReceiverCardSeparate(true);
        model.addCabinetType(module);
        cardType = new ReceiverCardType();
        cardType.setName("К");
        cardType.setModuleOutputs(16);
        model.addReceiverCardType(cardType);
        psuType = new PowerSupplyType();
        psuType.setName("БП");
        psuType.setOutputs(16);
        psuType.setRatedPowerW(300.0);
        model.addPowerSupplyType(psuType);
        model.selectProject(model.addProject("П"));
        scene = model.addScene("С");
        model.selectScene(scene);
        screen = model.addScreen("Э", module.getId(), 2, 4, 0, 0);
        model.selectScreen(screen);
    }

    private String unit(int row, int col, ChainUnits.Mode mode) {
        return ChainLinks.unitContaining(screen, screen.cabinetAt(row, col).getId(), mode);
    }

    @Test
    void powerSocketsOfScreenNodeFollowChainsOfSupplies(@TempDir Path dir) {
        setUp(dir);
        model.assignPowerSuppliesManual(new GridRect(0, 0, 1, 1), psuType.getId());
        model.assignPowerSuppliesManual(new GridRect(0, 2, 1, 3), psuType.getId());
        SchemaNode node = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, "Э", 0, 0, screen.getId());

        model.addPowerChain(1, List.of(unit(0, 0, ChainUnits.Mode.POWER)));
        assertEquals(1, node.getPowerConnectors().size());
        assertEquals(1, node.getPowerConnectors().get(0).getCount(), "один блок на фазе — один ввод");

        model.addPowerChain(2, List.of(unit(0, 2, ChainUnits.Mode.POWER)));
        assertEquals(1, node.getPowerConnectors().size(), "тот же тип разъёма — одна группа");
        assertEquals(2, node.getPowerConnectors().get(0).getCount(), "две цепочки блоков — два ввода, а не 8 модулей");
    }

    @Test
    void signalSocketsOfScreenNodeFollowChainsOfCards(@TempDir Path dir) {
        setUp(dir);
        model.assignReceiverCardsManual(new GridRect(0, 0, 1, 1), cardType.getId());
        model.assignReceiverCardsManual(new GridRect(0, 2, 1, 3), cardType.getId());
        SchemaNode node = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SCREEN, "Э", 0, 0, screen.getId());

        model.addSignalChain(1, false, List.of(unit(0, 0, ChainUnits.Mode.SIGNAL), unit(0, 2, ChainUnits.Mode.SIGNAL)));

        SchemaCard auto = node.getCards().stream().filter(c -> "Вводы сигнала".equals(c.getName())).findFirst()
                .orElseThrow();
        assertEquals(1, auto.getPorts().stream().mapToInt(CardPort::getCount).sum(),
                "одна цепочка из двух карт — один сигнальный ввод");
    }

    @Test
    void chainEndpointSocketsAreFirstUnitsAndStandOnTheirPlaceModule(@TempDir Path dir) {
        setUp(dir);
        model.assignReceiverCardsManual(new GridRect(0, 0, 1, 1), cardType.getId());
        model.assignReceiverCardsManual(new GridRect(0, 2, 1, 3), cardType.getId());
        model.assignPowerSuppliesManual(new GridRect(0, 0, 1, 3), psuType.getId());
        String cardA = unit(0, 0, ChainUnits.Mode.SIGNAL);
        String cardB = unit(0, 2, ChainUnits.Mode.SIGNAL);
        String supply = unit(0, 0, ChainUnits.Mode.POWER);
        model.addSignalChain(1, false, List.of(cardA, cardB));
        model.addPowerChain(1, List.of(supply));

        Set<String> signal = model.chainEndpointSocketCabinetIds(SchemaMode.SIGNAL, screen);
        Set<String> power = model.chainEndpointSocketCabinetIds(SchemaMode.POWER, screen);

        assertEquals(Set.of(cardA), signal, "вводное звено сигнала — первая карта цепочки");
        assertEquals(Set.of(supply), power, "вводное звено питания — блок");
        assertTrue(ChainLinks.place(screen, cardA) != null, "рисуется на модуле-месте карты");
        assertTrue(ChainLinks.place(screen, supply) != null, "рисуется на модуле-месте блока");
    }
}
