package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.SceneStats;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.ScreenStats;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Тесты доменной логики без UI: сценарий проект→сцена→экран→цепочки,
 * пересчёт характеристик, отмена (undo) и круговое сохранение/загрузка JSON.
 */
class AppModelTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    private CabinetType sampleType() {
        CabinetType ct = new CabinetType();
        ct.setName("Test P3 500x500");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setPowerConsumptionW(150);
        ct.setWeightKg(12);
        return ct;
    }

    @Test
    void createsHierarchyAndComputesStats(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        Project project = model.addProject("Проект");
        model.selectProject(project);
        Scene scene = model.addScene("Сцена");
        model.selectScene(scene);
        Screen screen = model.addScreen("Экран", type.getId(), 5, 3, 0, 0);

        assertEquals(15, screen.getCabinets().size());
        ScreenStats stats = ScreenLogic.stats(screen, type);
        assertEquals(3 * 128, stats.resolutionWidthPx());
        assertEquals(5 * 128, stats.resolutionHeightPx());
        assertEquals(15 * 150.0, stats.totalPowerW());
        assertEquals(15 * 12.0, stats.totalWeightKg());
    }

    @Test
    void powerChainAndUndo(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 3, 0, 0);
        model.selectScreen(screen);

        List<String> ids = screen.getCabinets().stream().map(c -> c.getId()).limit(3).toList();
        model.addPowerChain(1, ids);

        assertEquals(1, screen.getPowerChains().size());
        assertEquals(3, screen.getPowerChains().get(0).getCabinetInstanceIds().size());
        assertTrue(model.canUndo());

        model.undo();
        assertEquals(0, screen.getPowerChains().size());
        assertFalse(model.canUndo());
    }

    @Test
    void resizeGridPrunesOutOfBoundsCabinetsFromChains(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 3, 3, 0, 0);
        model.selectScreen(screen);

        // цепочка по нижней строке (row=2), которая исчезнет при сжатии до 2 строк
        List<String> bottomRow = screen.getCabinets().stream()
                .filter(c -> c.getRowIndex() == 2).map(c -> c.getId()).toList();
        model.addPowerChain(2, bottomRow);
        assertEquals(1, screen.getPowerChains().size());

        model.updateScreenGrid(screen, screen.getName(), type.getId(), 2, 3);
        assertEquals(6, screen.getCabinets().size());
        assertTrue(screen.getPowerChains().isEmpty(), "Цепочка из удалённых кабинетов должна исчезнуть");
    }

    @Test
    void savesAndReloadsWorkspace(@TempDir Path dir) {
        File file = new File(dir.toFile(), "workspace.json");
        AppModel model = new AppModel(new WorkspaceStore(file));
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("Проект"));
        model.selectScene(model.addScene("Сцена"));
        model.addScreen("Экран", type.getId(), 2, 2, 100, 200);

        // новый экземпляр читает тот же файл
        WorkspaceStore store2 = new WorkspaceStore(file);
        Workspace reloaded = store2.load();
        assertEquals(1, reloaded.getCabinetTypes().size());
        assertEquals(1, reloaded.getProjects().size());
        Screen screen = reloaded.getProjects().get(0).getScenes().get(0).getScreens().get(0);
        assertNotNull(screen.getCabinetTypeId());
        assertEquals(4, screen.getCabinets().size());
        assertEquals(100.0, screen.getPosXMm());
        assertEquals(200.0, screen.getPosYMm());
    }

    @Test
    void signalBackupTogglesWithoutRequiringCabinets(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);

        assertNull(screen.signalChainByPort(3, true));
        boolean nowBackup = model.toggleSignalPortBackup(3);
        assertTrue(nowBackup);
        SignalChain backup = screen.signalChainByPort(3, true);
        assertNotNull(backup);
        assertTrue(backup.getCabinetInstanceIds().isEmpty(), "Бэкап не должен требовать расключения кабинетов");

        boolean nowBackup2 = model.toggleSignalPortBackup(3);
        assertFalse(nowBackup2);
        assertNull(screen.signalChainByPort(3, true));

        // основная (не бэкап) цепочка на том же порту сосуществует независимо
        List<String> ids = screen.getCabinets().stream().map(c -> c.getId()).limit(2).toList();
        model.addSignalChain(3, false, ids);
        model.toggleSignalPortBackup(3);
        assertNotNull(screen.signalChainByPort(3, false));
        assertNotNull(screen.signalChainByPort(3, true));
    }

    @Test
    void sceneStatsSumsAcrossScreens(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        Project project = model.addProject("P");
        model.selectProject(project);
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        model.addScreen("A", type.getId(), 2, 2, 0, 0);
        model.addScreen("B", type.getId(), 3, 2, 0, 0);

        SceneStats stats = ScreenLogic.sceneStats(scene, model.getWorkspace());
        assertEquals(2, stats.screenCount());
        assertEquals(4 + 6, stats.totalCabinetCount());
        assertEquals((4 + 6) * 150.0, stats.totalPowerW());
        assertEquals((4 + 6) * 12.0, stats.totalWeightKg());
    }

    @Test
    void newScreensAutoPositionWithoutOverlap(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType()); // 500x500мм
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));

        Screen a = model.addScreenAutoPosition("A", type.getId(), 2, 3); // ширина 1500мм
        Screen b = model.addScreenAutoPosition("B", type.getId(), 2, 3);
        Screen c = model.addScreenAutoPosition("C", type.getId(), 2, 3);

        assertEquals(0.0, a.getPosXMm());
        assertEquals(1700.0, b.getPosXMm()); // 1500 + зазор 200
        assertEquals(3400.0, c.getPosXMm());
        // ни один экран не перекрывает предыдущий по X
        assertTrue(b.getPosXMm() >= a.getPosXMm() + a.getCols() * type.getWidthMm());
        assertTrue(c.getPosXMm() >= b.getPosXMm() + b.getCols() * type.getWidthMm());
    }

    @Test
    void autoArrangeFixesExistingOverlap(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        // оба экрана в (0,0) — типичная ситуация «наложились», которую чинит кнопка
        Screen a = model.addScreen("A", type.getId(), 2, 3, 0, 0);
        Screen b = model.addScreen("B", type.getId(), 2, 4, 0, 0);
        assertEquals(a.getPosXMm(), b.getPosXMm());

        model.autoArrangeScreensInScene();

        assertTrue(b.getPosXMm() >= a.getPosXMm() + a.getCols() * type.getWidthMm(),
                "После расстановки экраны не должны перекрываться по X");
    }

    @Test
    void perCellTypeOverrideAffectsWeightAndPowerButNotGridSize(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType base = model.addCabinetType(sampleType()); // 150 Вт, 12 кг
        CabinetType heavy = new CabinetType();
        heavy.setName("Heavy 500x500");
        heavy.setWidthMm(500);
        heavy.setHeightMm(500);
        heavy.setResolutionWidth(128);
        heavy.setResolutionHeight(128);
        heavy.setPowerConsumptionW(300);
        heavy.setWeightKg(20);
        model.addCabinetType(heavy);

        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", base.getId(), 2, 2, 0, 0); // 4 кабинета
        model.selectScreen(screen);

        String firstId = screen.getCabinets().get(0).getId();
        model.setCabinetTypeOverride(firstId, heavy.getId());
        assertEquals(heavy.getId(), screen.cabinetById(firstId).getCabinetTypeId());

        ScreenStats stats = ScreenLogic.stats(screen, base, model.getWorkspace());
        // 1 heavy (300/20) + 3 base (150/12) = 750 Вт, 56 кг
        assertEquals(300 + 3 * 150.0, stats.totalPowerW());
        assertEquals(20 + 3 * 12.0, stats.totalWeightKg());
        // геометрия сетки считается по типу экрана, смешение по ячейкам её не меняет
        assertEquals(2 * 128, stats.resolutionWidthPx());

        // undo возвращает исходный тип ячейки
        model.undo();
        assertNull(screen.cabinetById(firstId).getCabinetTypeId());
    }

    @Test
    void hidingCabinetMasksShapeAndExcludesFromStats(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0); // 4 кабинета
        model.selectScreen(screen);

        String id = screen.getCabinets().get(0).getId();
        model.toggleCabinetHidden(id);
        assertTrue(screen.cabinetById(id).isHidden());

        ScreenStats stats = ScreenLogic.stats(screen, type, model.getWorkspace());
        assertEquals(3, stats.activeCabinetCount());
        assertEquals(3 * 150.0, stats.totalPowerW());
    }

    @Test
    void signalBackupPortLinkPointsToAnotherPort(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);
        model.updateSignalPortCount(screen, 8);

        model.setSignalBackupPortLink(3, 7);
        SignalChain main = screen.signalChainByPort(3, false);
        assertNotNull(main);
        assertEquals(7, main.getBackupPortNumber());

        model.setSignalBackupPortLink(3, null);
        assertNull(screen.signalChainByPort(3, false).getBackupPortNumber());

        assertTrue(assertThrowsRuntime(() -> model.setSignalBackupPortLink(5, 5)));
    }

    @Test
    void controllersDetermineEffectiveSignalPortCount(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);
        model.updateSignalPortCount(screen, 8);
        assertEquals(8, model.effectiveSignalPortCount(screen));

        ControllerType ctA = new ControllerType();
        ctA.setName("MX40 Pro");
        ctA.setPortCount(10);
        model.addControllerType(ctA);
        ControllerType ctB = new ControllerType();
        ctB.setName("VX600");
        ctB.setPortCount(6);
        model.addControllerType(ctB);

        model.addControllerToScreen(screen, ctA.getId());
        assertEquals(10, model.effectiveSignalPortCount(screen), "Один контроллер — по числу его портов");

        ControllerInstance ci2 = model.addControllerToScreen(screen, ctB.getId());
        assertEquals(16, model.effectiveSignalPortCount(screen), "Несколько контроллеров — сумма портов");

        model.removeControllerFromScreen(screen, ci2.getId());
        assertEquals(10, model.effectiveSignalPortCount(screen));
    }

    @Test
    void overlapDetectionAndBottomAlignedAutoArrange(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType tall = model.addCabinetType(sampleType()); // 500x500мм, будет 2 ряда = 1000мм
        CabinetType shortT = new CabinetType();
        shortT.setName("Short 500x500");
        shortT.setWidthMm(500);
        shortT.setHeightMm(300);
        shortT.setResolutionWidth(64);
        shortT.setResolutionHeight(64);
        model.addCabinetType(shortT);

        Project project = model.addProject("P");
        model.selectProject(project);
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen a = model.addScreen("A", tall.getId(), 2, 2, 0, 0); // высота 1000мм
        Screen b = model.addScreen("B", shortT.getId(), 2, 2, 0, 0); // высота 600мм, оба в (0,0)

        assertTrue(model.currentSceneHasOverlap());

        model.autoArrangeScreensInScene();
        assertFalse(model.currentSceneHasOverlap());
        // выравнивание по нижнему краю: более низкий экран сдвинут вниз на разницу высот
        assertEquals(1000.0 - 600.0, b.getPosYMm());
        assertEquals(0.0, a.getPosYMm());
    }

    @Test
    void schemaNodesAndEdgesAreScopedPerSceneAndMode(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);

        SchemaNode source = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "Щит А1", 0, 0, null);
        SchemaNode screenNode = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, screen.getName(),
                200, 0, screen.getId());
        // тот же экран, но в схеме СИГНАЛА — должен существовать независимо от схемы питания
        model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SCREEN, screen.getName(), 200, 0, screen.getId());

        assertEquals(2, model.schemaNodesForCurrentScene(SchemaMode.POWER).size());
        assertEquals(1, model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).size());

        SchemaEdge edge = model.addSchemaEdge(SchemaMode.POWER, source.getId(), screenNode.getId(), "3x2.5");
        assertEquals(1, model.schemaEdgesForCurrentScene(SchemaMode.POWER).size());
        assertEquals("3x2.5", edge.getLabel());

        // повторное соединение тех же двух узлов не дублирует связь
        model.addSchemaEdge(SchemaMode.POWER, source.getId(), screenNode.getId(), null);
        assertEquals(1, model.schemaEdgesForCurrentScene(SchemaMode.POWER).size());

        model.deleteSchemaNode(screenNode);
        assertEquals(1, model.schemaNodesForCurrentScene(SchemaMode.POWER).size());
        assertTrue(model.schemaEdgesForCurrentScene(SchemaMode.POWER).isEmpty(),
                "Удаление узла должно убирать и его связи");
    }

    @Test
    void deletingScreenRemovesItsSchemaNodeReferences(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);

        SchemaNode server = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "Сервер 1", 0, 0, null);
        SchemaNode screenNode = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SCREEN, screen.getName(),
                200, 0, screen.getId());
        model.addSchemaEdge(SchemaMode.SIGNAL, server.getId(), screenNode.getId(), null);

        model.deleteScreen(screen);

        assertEquals(1, model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).size(), "Узел-ссылка на удалённый экран должен исчезнуть");
        assertTrue(model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL).isEmpty());
    }

    private static boolean assertThrowsRuntime(Runnable r) {
        try {
            r.run();
            return false;
        } catch (RuntimeException ex) {
            return true;
        }
    }
}
