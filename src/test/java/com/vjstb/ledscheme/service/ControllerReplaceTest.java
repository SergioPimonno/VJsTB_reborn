package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Запрос 2026-10-03: «Заменить контроллер». Замена идёт ПО МЕСТУ (тот же экземпляр, список
 *  контроллеров не растёт), подходят модели, где хватает входов и Ethernet-портов под ВСЕ занятые
 *  порты (основные и резервные), расключение переносится по номерам портов, а если занятые порты не
 *  помещаются на свои номера — уплотняется по порядку (пример пользователя: MCTRL4k с портами 1–5 и
 *  резервами 9–13 заменяется на VX1000 с 10 портами). Номера портов контроллеров ПОСЛЕ заменённого
 *  сдвигаются на разницу в числе портов. */
class ControllerReplaceTest {

    private AppModel model;
    private Scene scene;
    private Screen screen;

    private void setup(Path dir) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        CabinetType ct = new CabinetType();
        ct.setName("T");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        CabinetType type = model.addCabinetType(ct);
        model.selectProject(model.addProject("P"));
        scene = model.addScene("S");
        model.selectScene(scene);
        screen = model.addScreen("E", type.getId(), 1, 4, 0, 0);
        model.selectScreen(screen);
    }

    private EquipmentPreset preset(String name, int ethernetPorts, int inputs) {
        List<CardPort> ports = new java.util.ArrayList<>();
        ports.add(new CardPort("Ethernet", PortDirection.OUT, ethernetPorts));
        if (inputs > 0) {
            ports.add(new CardPort("HDMI", PortDirection.IN, inputs));
        }
        SchemaCard card = new SchemaCard("Карта", ports);
        return model.addControllerPreset(name, "", ethernetPorts, 1000, inputs, false, List.of(card));
    }

    private String cab(int i) {
        return screen.getCabinets().get(i).getId();
    }

    @Test
    void replacesInPlaceAndShiftsFollowingControllers(@org.junit.jupiter.api.io.TempDir Path dir) {
        setup(dir);
        EquipmentPreset small = preset("Small", 4, 1);
        EquipmentPreset big = preset("Big", 8, 1);
        ControllerInstance c1 = model.addControllerToScreen(screen, small.getId(), null);
        ControllerInstance c2 = model.addControllerToScreen(screen, small.getId(), null);
        model.addSignalChain(1, false, List.of(cab(0)));
        model.addSignalChain(5, false, List.of(cab(1))); // порт 1 второго контроллера

        model.replaceController(scene, c1.getId(), big.getId(), null);

        List<ControllerInstance> all = model.controllersInScene(scene);
        assertEquals(2, all.size(), "список контроллеров не растёт");
        assertEquals(c1.getId(), all.get(0).getId());
        assertEquals(big.getId(), all.get(0).getControllerTypeId());
        assertEquals(8, all.get(0).effectivePortCount());
        assertEquals(1, scene.getSignalChains().get(0).getPortNumber(), "свои порты — по номерам");
        assertEquals(9, scene.getSignalChains().get(1).getPortNumber(), "порты второго контроллера сдвинулись на +4");
    }

    @Test
    void compactsOccupiedPortsWhenTheyDoNotFitTheirNumbers(@org.junit.jupiter.api.io.TempDir Path dir) {
        setup(dir);
        EquipmentPreset wide = preset("Wide", 16, 1);
        EquipmentPreset narrow = preset("Narrow", 8, 1);
        ControllerInstance c = model.addControllerToScreen(screen, wide.getId(), null);
        model.addSignalChain(1, false, List.of(cab(0)));
        model.addSignalChain(12, false, List.of(cab(1)));

        var mapping = model.replaceController(scene, c.getId(), narrow.getId(), null);

        assertEquals(2, mapping.get(12));
        assertEquals(1, scene.getSignalChains().get(0).getPortNumber());
        assertEquals(2, scene.getSignalChains().get(1).getPortNumber(), "порт 12 уплотнён на 2");
    }

    @Test
    void notEnoughPortsHidesThePresetAndBlocksReplace(@org.junit.jupiter.api.io.TempDir Path dir) {
        setup(dir);
        EquipmentPreset wide = preset("Wide", 16, 2);
        EquipmentPreset tiny = preset("Tiny", 1, 2);
        EquipmentPreset fewerInputs = preset("FewerInputs", 16, 1);
        EquipmentPreset ok = preset("Ok", 2, 2);
        ControllerInstance c = model.addControllerToScreen(screen, wide.getId(), null);
        model.addSignalChain(1, false, List.of(cab(0)));
        model.addSignalChain(7, false, List.of(cab(1)));

        var options = model.controllerReplacementOptions(scene, c.getId());

        List<String> names = options.suitable().stream().map(EquipmentPreset::getName).toList();
        assertTrue(names.contains("Ok"), "двух портов хватает на два занятых");
        assertFalse(names.contains("Tiny"), "один порт на две цепочки — мало");
        assertTrue(names.contains("FewerInputs"), "число входов не ограничивает замену");
        assertFalse(names.contains("Wide"), "сам себя не предлагает");
        assertEquals(1, options.hidden());
        assertThrows(IllegalArgumentException.class,
                () -> model.replaceController(scene, c.getId(), tiny.getId(), null));
        assertEquals(wide.getId(), model.controllersInScene(scene).get(0).getControllerTypeId(),
                "после отказа контроллер не изменился");
    }

    @Test
    void modularPresetIsOfferedAndTooFewCardsResetThisControllersChains(@org.junit.jupiter.api.io.TempDir Path dir) {
        setup(dir);
        EquipmentPreset wide = preset("Wide", 16, 1);
        ControllerInstance c = model.addControllerToScreen(screen, wide.getId(), null);
        model.addSignalChain(1, false, List.of(cab(0)));
        model.addSignalChain(7, false, List.of(cab(1)));
        // модульная модель: два шаблона карт по одному Ethernet-порту — порты зависят от сборки
        EquipmentPreset modular = model.addControllerPreset("Modular", "", 0, 1000, 0, false, List.of(
                new SchemaCard("Карта A", new java.util.ArrayList<>(List.of(new CardPort("Ethernet", PortDirection.OUT, 1)))),
                new SchemaCard("Карта B", new java.util.ArrayList<>(List.of(new CardPort("Ethernet", PortDirection.OUT, 1))))));

        assertTrue(model.controllerReplacementOptions(scene, c.getId()).suitable().contains(modular),
                "модульная модель предлагается всегда");
        var need = model.minimumCardsToKeepWiring(scene, c.getId(), modular);
        assertEquals(2, need.occupied());
        assertEquals(2, need.cardsNeeded(), "2 занятых порта при 1 порте на карту — минимум 2 карты");

        List<String> oneCard = List.of(model.cardTemplatesOf(modular).get(0).getId());
        assertTrue(model.controllerReplacementProblem(scene, c.getId(), modular.getId(), oneCard) != null);
        assertThrows(IllegalArgumentException.class,
                () -> model.replaceController(scene, c.getId(), modular.getId(), oneCard));

        model.replaceController(scene, c.getId(), modular.getId(), oneCard, true);
        assertTrue(scene.getSignalChains().isEmpty(), "цепочки контроллера сброшены");
        assertEquals(1, model.controllersInScene(scene).get(0).effectivePortCount());
    }
}
