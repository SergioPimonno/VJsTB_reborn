package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.AppModel;
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
}
