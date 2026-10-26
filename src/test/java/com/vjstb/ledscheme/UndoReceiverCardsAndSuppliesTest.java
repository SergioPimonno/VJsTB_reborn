package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.ModuleNumberingPattern;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.EditReport;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Отмена правок карт и блоков питания (docs/receiver-cards/PLAN.md, раздел 5): каждая операция — ОДНА запись отмены,
 * которая возвращает вместе карты, блоки, автообласти, шаблон нумерации И звенья цепочек сцены. По образцу
 * {@code UndoMaskPropsAndSchemaSheetsTest}: операции идут вперемешку с другими отменяемыми правками одним стеком Ctrl+Z.
 * Отдельно — что повторная протяжка, ничего не менявшая, записи в стеке не оставляет.
 */
class UndoReceiverCardsAndSuppliesTest {

    private AppModel model;
    private Screen screen;
    private Scene scene;
    private ReceiverCardType cardType;
    private PowerSupplyType psuType;

    private static GridRect rect(int r0, int c0, int r1, int c1) {
        return new GridRect(r0, c0, r1, c1);
    }

    private void setUp(Path dir) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        CabinetType module = new CabinetType();
        module.setName("Модуль");
        module.setWidthMm(250);
        module.setHeightMm(250);
        module.setResolutionWidth(64);
        module.setResolutionHeight(64);
        module.setReceiverCardSeparate(true);
        model.addCabinetType(module);
        cardType = new ReceiverCardType();
        cardType.setName("К8");
        cardType.setModuleOutputs(8);
        model.addReceiverCardType(cardType);
        psuType = new PowerSupplyType();
        psuType.setName("БП8");
        psuType.setOutputs(8);
        model.addPowerSupplyType(psuType);
        Project project = model.addProject("Проект");
        model.selectProject(project);
        scene = model.addScene("Сцена");
        model.selectScene(scene);
        screen = model.addScreen("A", module.getId(), 2, 4, 0, 0);
        model.selectScreen(screen);
    }

    @Test
    void cardAssignmentAndItsChainLinkUndoTogether(@TempDir Path dir) {
        setUp(dir);
        model.assignReceiverCardsManual(rect(0, 0, 1, 1), cardType.getId());
        String cardId = screen.getReceiverCards().get(0).getId();
        scene.getSignalChains().add(new SignalChain(1, false, List.of(cardId)));

        EditReport r = model.assignReceiverCardsManual(rect(0, 0, 1, 3), cardType.getId());

        assertEquals(List.of(cardId), r.removedIds());
        assertEquals(1, screen.getReceiverCards().size());
        assertTrue(scene.getSignalChains().isEmpty(), "замещённая карта вынесла своё звено из цепочки");

        model.undo();

        assertEquals(1, screen.getReceiverCards().size());
        assertEquals(cardId, screen.getReceiverCards().get(0).getId());
        assertEquals(4, screen.getReceiverCards().get(0).getModuleIds().size());
        assertEquals(1, scene.getSignalChains().size());
        assertEquals(List.of(cardId), scene.getSignalChains().get(0).getCabinetInstanceIds());

        model.undo();
        assertTrue(screen.getReceiverCards().isEmpty());
    }

    @Test
    void autoSupplyAssignmentUndoesBlocksAreaAndPhasesInOneStep(@TempDir Path dir) {
        setUp(dir);
        int depth = model.undoDepth();

        EditReport r = model.assignPowerSuppliesAuto(rect(0, 0, 1, 3), psuType.getId());
        assertEquals(depth + 1, model.undoDepth(), "авторазбиение — одна запись отмены");
        assertEquals(1, screen.getPowerSupplies().size());
        assertEquals(1, screen.getPowerAutoAreas().size());
        assertEquals("Подобрано: 4×2 мод. на блок, блоков: 1", r.autoInfo());
        scene.getPowerChains().add(new PowerChain(1, List.of(screen.getPowerSupplies().get(0).getId())));

        // повторное авторазбиение другого размера пересоздаёт блок и вычищает фазу
        psuType.setOutputs(4);
        model.assignPowerSuppliesAuto(rect(0, 0, 1, 3), psuType.getId());
        assertEquals(2, screen.getPowerSupplies().size());
        assertTrue(scene.getPowerChains().isEmpty());

        model.undo();
        assertEquals(1, screen.getPowerSupplies().size());
        assertEquals(1, scene.getPowerChains().size());

        model.undo();
        assertTrue(screen.getPowerSupplies().isEmpty());
        assertTrue(screen.getPowerAutoAreas().isEmpty());
    }

    @Test
    void numberingPatternPlaceAndManualOrderAreUndoable(@TempDir Path dir) {
        setUp(dir);
        model.assignReceiverCardsManual(rect(0, 0, 1, 1), cardType.getId());
        String card = screen.getReceiverCards().get(0).getId();
        String m00 = screen.cabinetAt(0, 0).getId();
        String m11 = screen.cabinetAt(1, 1).getId();

        model.setModuleNumbering(new ModuleNumberingPattern(ModuleNumberingPattern.Corner.BOTTOM_RIGHT,
                ModuleNumberingPattern.Axis.COLUMNS, true));
        model.setReceiverCardPlace(card, m11);
        model.setReceiverCardManualOrder(card, List.of(m11, m00));

        assertEquals(ModuleNumberingPattern.Corner.BOTTOM_RIGHT, screen.getModuleNumbering().getCorner());
        assertEquals(m11, screen.getReceiverCards().get(0).getHostModuleId());
        assertEquals(List.of(m11, m00), screen.getReceiverCards().get(0).getManualPortOrder());

        model.undo(); // порядок
        assertNull(screen.getReceiverCards().get(0).getManualPortOrder());
        assertEquals(m11, screen.getReceiverCards().get(0).getHostModuleId());
        model.undo(); // место
        assertNull(screen.getReceiverCards().get(0).getHostModuleId());
        assertTrue(screen.getModuleNumbering().isSnake());
        model.undo(); // шаблон
        assertEquals(ModuleNumberingPattern.Corner.TOP_LEFT, screen.getModuleNumbering().getCorner());
        assertFalse(screen.getModuleNumbering().isSnake());
    }

    @Test
    void cardEditsInterleaveWithMaskEditsInOneUndoStack(@TempDir Path dir) {
        setUp(dir);

        model.setMaskMesh(screen, true);                                                   // 1
        model.assignReceiverCardsManual(rect(0, 0, 0, 3), cardType.getId());              // 2
        model.assignPowerSuppliesManual(rect(0, 0, 1, 1), psuType.getId());              // 3
        model.removeReceiverCard(screen.getReceiverCards().get(0).getId());              // 4

        model.undo(); // 4
        assertEquals(1, screen.getReceiverCards().size());
        model.undo(); // 3
        assertTrue(screen.getPowerSupplies().isEmpty());
        assertEquals(1, screen.getReceiverCards().size());
        assertTrue(screen.isMaskMesh());
        model.undo(); // 2
        assertTrue(screen.getReceiverCards().isEmpty());
        assertTrue(screen.isMaskMesh());
        model.undo(); // 1
        assertFalse(screen.isMaskMesh());
    }

    @Test
    void multiStepUndoRollsBackCardsAndBlocksAtOnce(@TempDir Path dir) {
        setUp(dir);
        model.assignReceiverCardsAuto(rect(0, 0, 1, 3), cardType.getId(), 2, 2);
        model.assignPowerSuppliesManual(rect(0, 0, 1, 3), psuType.getId());
        model.setModuleNumbering(new ModuleNumberingPattern(ModuleNumberingPattern.Corner.TOP_RIGHT,
                ModuleNumberingPattern.Axis.ROWS, false));

        model.undo(3);

        assertTrue(screen.getReceiverCards().isEmpty());
        assertTrue(screen.getPowerSupplies().isEmpty());
        assertEquals(ModuleNumberingPattern.Corner.TOP_LEFT, screen.getModuleNumbering().getCorner());
    }

    @Test
    void anOperationThatChangedNothingLeavesNoUndoEntry(@TempDir Path dir) {
        setUp(dir);
        model.assignReceiverCardsManual(rect(0, 0, 1, 1), cardType.getId());
        model.assignPowerSuppliesManual(rect(0, 0, 1, 1), psuType.getId());
        int depth = model.undoDepth();

        assertTrue(model.assignReceiverCardsManual(rect(0, 0, 1, 1), cardType.getId()).noop());
        assertTrue(model.assignPowerSuppliesManual(rect(0, 0, 1, 1), psuType.getId()).noop());
        assertTrue(model.removeReceiverCard("нет такой").noop());
        model.setModuleNumbering(new ModuleNumberingPattern());
        model.setReceiverCardPlace(screen.getReceiverCards().get(0).getId(), null);

        assertEquals(depth, model.undoDepth());
    }

    @Test
    void aRejectedOperationLeavesNoUndoEntryEither(@TempDir Path dir) {
        setUp(dir);
        model.assignReceiverCardsManual(rect(0, 0, 1, 1), cardType.getId());
        int depth = model.undoDepth();

        try {
            model.setReceiverCardPlace(screen.getReceiverCards().get(0).getId(), "чужой-модуль");
        } catch (IllegalArgumentException expected) {
            // место должно быть модулем карты
        }

        assertEquals(depth, model.undoDepth());
    }

    @Test
    void recalcAfterTheDedicatedOutputIsSwitchedIsSavedWithTheTypeAndDropsPhases(@TempDir Path dir) {
        setUp(dir);
        model.assignReceiverCardsManual(rect(0, 0, 1, 3), cardType.getId());
        psuType.setOutputs(8);
        model.assignPowerSuppliesAuto(rect(0, 0, 1, 3), psuType.getId());
        assertEquals(2, screen.getPowerSupplies().size(), "без «К» карта забирает шлейф: 8 модулей + карта не влезают");
        for (var s : screen.getPowerSupplies()) {
            scene.getPowerChains().add(new PowerChain(1, List.of(s.getId())));
        }
        assertEquals(2, scene.getPowerChains().size());

        PowerSupplyType edited = model.getPowerSupplyTypes().get(0).copy();
        edited.setCardOutput(true);
        model.updatePowerSupplyType(edited);

        assertEquals(1, screen.getPowerSupplies().size(), "с отдельным выходом область — один блок");
        assertTrue(scene.getPowerChains().isEmpty(), "фазы пересозданных блоков выпали");
    }
}
