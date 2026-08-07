package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.ControllerType;
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

/** Тесты {@link NovaLctControllerResolver} — резолвер должен находить кабинеты
 *  ЛЮБОГО экрана сцены (не только текущего) и правильно раскладывать
 *  сквозной scene-wide номер порта на (карта, порт-в-пуле) для КОНКРЕТНОГО
 *  контроллера, минуя известное ограничение {@link ScreenLogic#cardAndLocalPort}. */
class NovaLctControllerResolverTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    private CabinetType type128() {
        CabinetType ct = new CabinetType();
        ct.setName("Test 128x128");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        return ct;
    }

    @Test
    void resolvesSimpleSingleCardController(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 1, 2, 0, 0);
        model.selectScreen(screen);

        ControllerType ct = new ControllerType();
        ct.setName("Simple");
        ct.setPortCount(4);
        ct.getCards().add(new SchemaCard("Карта 1", List.of(new CardPort("Ethernet", PortDirection.OUT, 4))));
        model.addControllerType(ct);
        ControllerInstance controller = model.addControllerToScreen(screen, ct.getId());

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addSignalChain(1, false, ids);

        List<NovaLctControllerResolver.CabinetRec> recs =
                NovaLctControllerResolver.resolve(scene, controller, model);

        assertEquals(2, recs.size());
        for (NovaLctControllerResolver.CabinetRec r : recs) {
            assertEquals(screen, r.sourceScreen());
            assertEquals(0, r.cardIndex());
            assertEquals(0, r.portInPool());
        }
    }

    @Test
    void resolvesH2LikeTwoCardController(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 1, 4, 0, 0);
        model.selectScreen(screen);

        ControllerType h2 = new ControllerType();
        h2.setName("H2");
        h2.setPortCount(8);
        h2.getCards().add(new SchemaCard("Карта 1", List.of(new CardPort("Ethernet", PortDirection.OUT, 2))));
        h2.getCards().add(new SchemaCard("Карта 2", List.of(new CardPort("Ethernet", PortDirection.OUT, 2))));
        model.addControllerType(h2);
        ControllerInstance controller = model.addControllerToScreen(screen, h2.getId());

        CabinetInstance c0 = screen.cabinetAt(0, 0);
        CabinetInstance c1 = screen.cabinetAt(0, 1);
        CabinetInstance c2 = screen.cabinetAt(0, 2);
        CabinetInstance c3 = screen.cabinetAt(0, 3);
        model.addSignalChain(1, false, List.of(c0.getId())); // card 0, port 0
        model.addSignalChain(2, false, List.of(c1.getId())); // card 0, port 1
        model.addSignalChain(3, false, List.of(c2.getId())); // card 1, port 0
        model.addSignalChain(4, false, List.of(c3.getId())); // card 1, port 1

        List<NovaLctControllerResolver.CabinetRec> recs =
                NovaLctControllerResolver.resolve(scene, controller, model);
        assertEquals(4, recs.size());

        long card0Count = recs.stream().filter(r -> r.cardIndex() == 0).count();
        long card1Count = recs.stream().filter(r -> r.cardIndex() == 1).count();
        assertEquals(2, card0Count, "Карта 1 (index 0) должна получить 2 кабинета");
        assertEquals(2, card1Count, "Карта 2 (index 1) должна получить 2 кабинета");
    }

    @Test
    void resolvesChainCrossingTwoScreensOfSameScene(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screenA = model.addScreen("A", type.getId(), 1, 1, 0, 0);
        Screen screenB = model.addScreen("B", type.getId(), 1, 1, 1000, 0);
        model.selectScreen(screenA);

        ControllerType ct = new ControllerType();
        ct.setName("Simple");
        ct.setPortCount(4);
        ct.getCards().add(new SchemaCard("Карта 1", List.of(new CardPort("Ethernet", PortDirection.OUT, 4))));
        model.addControllerType(ct);
        // Контроллер физически хранится под экраном A, но пул портов -- общий для
        // сцены (Task #58) -- резолвер должен найти кабинет экрана B тоже.
        ControllerInstance controller = model.addControllerToScreen(screenA, ct.getId());

        CabinetInstance cabA = screenA.cabinetAt(0, 0);
        CabinetInstance cabB = screenB.cabinetAt(0, 0);
        model.addSignalChain(1, false, List.of(cabA.getId(), cabB.getId()));

        List<NovaLctControllerResolver.CabinetRec> recs =
                NovaLctControllerResolver.resolve(scene, controller, model);

        assertEquals(2, recs.size());
        assertTrue(recs.stream().anyMatch(r -> r.sourceScreen() == screenA && r.seq() == 0));
        assertTrue(recs.stream().anyMatch(r -> r.sourceScreen() == screenB && r.seq() == 1));
    }
}
