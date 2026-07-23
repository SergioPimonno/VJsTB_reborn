package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.ChainInteractionController;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Логика построения цепочки (без Swing/мыши): старт по выбору цели, добавление
 * кликом/протяжкой и стрелками, завершение по Esc с сохранением через commit-колбэк.
 */
class ChainInteractionControllerTest {

    private AppModel freshModelWithScreen(Path dir, int rows, int cols) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        CabinetType ct = new CabinetType();
        ct.setName("T 500x500");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setPowerConsumptionW(150);
        ct.setWeightKg(12);
        model.addCabinetType(ct);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen scr = model.addScreen("E", ct.getId(), rows, cols, 0, 0);
        model.selectScreen(scr);
        return model;
    }

    @Test
    void clickAddsCabinetsInOrderAndEscCommits(@TempDir Path dir) {
        AppModel model = freshModelWithScreen(dir, 2, 3);
        Screen scr = model.getCurrentScreen();
        List<List<String>> committed = new ArrayList<>();
        ChainInteractionController ctrl = new ChainInteractionController(model, () -> { });

        ctrl.startFor(committed::add);
        assertTrue(ctrl.isChainBuilding());

        String a = scr.cabinetAt(0, 0).getId();
        String b = scr.cabinetAt(0, 1).getId();
        String c = scr.cabinetAt(1, 1).getId();
        ctrl.cabinetClicked(a);
        ctrl.cabinetClicked(b);
        ctrl.cabinetClicked(c);
        assertEquals(List.of(a, b, c), ctrl.activeChainCabIds());

        ctrl.finish();
        assertFalse(ctrl.isChainBuilding());
        assertEquals(1, committed.size());
        assertEquals(List.of(a, b, c), committed.get(0));
    }

    @Test
    void clickingSameCabinetTwiceDoesNotDuplicate(@TempDir Path dir) {
        AppModel model = freshModelWithScreen(dir, 2, 2);
        Screen scr = model.getCurrentScreen();
        List<List<String>> committed = new ArrayList<>();
        ChainInteractionController ctrl = new ChainInteractionController(model, () -> { });

        String a = scr.cabinetAt(0, 0).getId();
        ctrl.startFor(committed::add);
        ctrl.cabinetClicked(a);
        ctrl.cabinetClicked(a);
        ctrl.cabinetClicked(a);
        assertEquals(1, ctrl.activeChainCabIds().size());
        ctrl.finish();
        assertEquals(List.of(a), committed.get(0));
    }

    @Test
    void escOnEmptyChainCommitsNothing(@TempDir Path dir) {
        AppModel model = freshModelWithScreen(dir, 2, 2);
        List<List<String>> committed = new ArrayList<>();
        ChainInteractionController ctrl = new ChainInteractionController(model, () -> { });

        ctrl.startFor(committed::add);
        ctrl.finish();
        assertTrue(committed.isEmpty());
        assertFalse(ctrl.isChainBuilding());
    }

    @Test
    void startForWhileBuildingCommitsPreviousChainFirst(@TempDir Path dir) {
        AppModel model = freshModelWithScreen(dir, 2, 2);
        Screen scr = model.getCurrentScreen();
        List<List<String>> committedToFirst = new ArrayList<>();
        List<List<String>> committedToSecond = new ArrayList<>();
        ChainInteractionController ctrl = new ChainInteractionController(model, () -> { });

        String a = scr.cabinetAt(0, 0).getId();
        ctrl.startFor(committedToFirst::add);
        ctrl.cabinetClicked(a);

        // выбор новой цели (другая фаза/порт) должен зафиксировать текущую цепочку
        String b = scr.cabinetAt(0, 1).getId();
        ctrl.startFor(committedToSecond::add);
        assertEquals(List.of(a), committedToFirst.get(0));
        assertTrue(ctrl.activeChainCabIds().isEmpty());

        ctrl.cabinetClicked(b);
        ctrl.finish();
        assertEquals(List.of(b), committedToSecond.get(0));
    }

    @Test
    void arrowKeysMoveCursorAndAppendCabinets(@TempDir Path dir) {
        AppModel model = freshModelWithScreen(dir, 3, 3);
        Screen scr = model.getCurrentScreen();
        List<List<String>> committed = new ArrayList<>();
        ChainInteractionController ctrl = new ChainInteractionController(model, () -> { });

        ctrl.startFor(committed::add);
        // первое нажатие стрелки только помещает курсор в стартовую позицию (0,0)
        ctrl.moveCursor(0, 1);
        assertEquals(0, ctrl.cursorRow());
        assertEquals(0, ctrl.cursorCol());
        assertEquals(List.of(scr.cabinetAt(0, 0).getId()), ctrl.activeChainCabIds());

        // последующие — реально двигают курсор и добавляют кабинет под ним
        ctrl.moveCursor(0, 1);
        assertEquals(0, ctrl.cursorRow());
        assertEquals(1, ctrl.cursorCol());
        ctrl.moveCursor(1, 0);
        assertEquals(1, ctrl.cursorRow());
        assertEquals(1, ctrl.cursorCol());

        ctrl.finish();
        List<String> ids = committed.get(0);
        assertEquals(3, ids.size());
        assertEquals(scr.cabinetAt(0, 0).getId(), ids.get(0));
        assertEquals(scr.cabinetAt(0, 1).getId(), ids.get(1));
        assertEquals(scr.cabinetAt(1, 1).getId(), ids.get(2));
    }

    @Test
    void cursorClampsAtGridBounds(@TempDir Path dir) {
        AppModel model = freshModelWithScreen(dir, 2, 2);
        ChainInteractionController ctrl = new ChainInteractionController(model, () -> { });
        ctrl.startFor(ids -> { });
        ctrl.moveCursor(-5, -5); // старт всегда (0,0) независимо от знака первой дельты
        assertEquals(0, ctrl.cursorRow());
        assertEquals(0, ctrl.cursorCol());
        ctrl.moveCursor(-5, -5); // клампится к границе сетки, не уходит в минус
        assertEquals(0, ctrl.cursorRow());
        assertEquals(0, ctrl.cursorCol());
        ctrl.moveCursor(5, 5); // клампится к максимуму (rows-1, cols-1)
        assertEquals(1, ctrl.cursorRow());
        assertEquals(1, ctrl.cursorCol());
    }
}
