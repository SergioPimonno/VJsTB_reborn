package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CanvasPlacement;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.SchemaCard;
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

        assertEquals(1, model.getCurrentScene().getPowerChains().size());
        assertEquals(3, model.getCurrentScene().getPowerChains().get(0).getCabinetInstanceIds().size());
        assertTrue(model.canUndo());

        model.undo();
        assertEquals(0, model.getCurrentScene().getPowerChains().size());
        assertFalse(model.canUndo());
    }

    @Test
    void powerChainLoadWattsSumsEffectiveCabinetPower(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType()); // 150 Вт/каб.
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 2, 3, 0, 0);
        model.selectScreen(screen);

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).limit(3).toList();
        model.addPowerChain(1, ids);
        PowerChain chain = scene.getPowerChains().get(0);

        assertEquals(450.0, model.powerChainLoadWatts(scene, chain), 0.001);
    }

    @Test
    void powerChainOverloadDetectedAgainstCustomConnectorRating(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = sampleType(); // 150 Вт/каб.
        type.setPowerConnectorType(com.vjstb.ledscheme.model.PowerConnectorType.OTHER);
        type.setCustomConnectorAmpRating(0.5); // ёмкость ~102 Вт при запасе по умолчанию — один кабинет уже перегрузка
        type = model.addCabinetType(type);
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 1, 1, 0, 0);
        model.selectScreen(screen);

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addPowerChain(1, ids);
        PowerChain chain = scene.getPowerChains().get(0);

        AppModel.ChainLoadStatus status = model.powerChainLoadStatus(scene, chain);
        assertTrue(status.capacityKnown());
        assertTrue(status.overloaded());
        assertTrue(status.blocksExport(), "Неподтверждённая перегрузка должна блокировать экспорт");

        model.acknowledgePowerChainOverload(scene, chain);
        AppModel.ChainLoadStatus afterAck = model.powerChainLoadStatus(scene, chain);
        assertTrue(afterAck.overloaded());
        assertFalse(afterAck.blocksExport(), "После «Я знаю» экспорт не должен блокироваться при той же нагрузке");
    }

    @Test
    void powerChainAcknowledgementResetsWhenLoadGrowsFurther(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = sampleType();
        type.setPowerConnectorType(com.vjstb.ledscheme.model.PowerConnectorType.OTHER);
        type.setCustomConnectorAmpRating(0.5);
        type = model.addCabinetType(type);
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 1, 2, 0, 0);
        model.selectScreen(screen);

        List<String> allIds = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addPowerChain(1, List.of(allIds.get(0)));
        PowerChain chain = scene.getPowerChains().get(0);
        model.acknowledgePowerChainOverload(scene, chain);
        assertFalse(model.powerChainLoadStatus(scene, chain).blocksExport());

        // Добавили в цепочку ещё один кабинет — нагрузка выросла сверх подтверждённой,
        // предупреждение должно вернуться автоматически.
        chain.getCabinetInstanceIds().add(allIds.get(1));
        assertTrue(model.powerChainLoadStatus(scene, chain).blocksExport(),
                "Рост нагрузки сверх подтверждённой должен снова заблокировать экспорт");
    }

    @Test
    void startingPowerChainAutoAddsConnectorSocketToScreenSchemaNode(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = sampleType();
        type.setPowerConnectorType(com.vjstb.ledscheme.model.PowerConnectorType.POWERCON);
        type = model.addCabinetType(type);
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 1, 4, 0, 0);
        model.selectScreen(screen);
        SchemaNode screenNode = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, "E", 0, 0, screen.getId());

        List<String> firstHalf = screen.getCabinets().stream().map(CabinetInstance::getId).limit(2).toList();
        model.addPowerChain(1, firstHalf);
        assertEquals(1, screenNode.getPowerConnectors().size());
        assertEquals("PowerCon", screenNode.getPowerConnectors().get(0).getConnectorType());
        assertEquals(1, screenNode.getPowerConnectors().get(0).getCount());

        List<String> secondHalf = screen.getCabinets().stream().map(CabinetInstance::getId).skip(2).toList();
        model.addPowerChain(2, secondHalf);
        assertEquals(1, screenNode.getPowerConnectors().size(), "Второй ввод того же типа должен увеличить count, а не создать вторую группу");
        assertEquals(2, screenNode.getPowerConnectors().get(0).getCount());
    }

    @Test
    void startingSignalChainAndBackupBothAddSocketsToScreenSchemaNode(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 1, 4, 0, 0);
        model.selectScreen(screen);
        SchemaNode screenNode = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SCREEN, "E", 0, 0, screen.getId());

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addSignalChain(1, false, ids);
        SchemaCard card = screenNode.getCards().stream().filter(c -> "Вводы сигнала".equals(c.getName()))
                .findFirst().orElse(null);
        assertNotNull(card);
        assertEquals(1, card.getPorts().get(0).getCount());

        model.addSignalChain(2, true, ids); // резервный порт — тоже физическая линия
        assertEquals(2, card.getPorts().get(0).getCount(), "Резервный порт тоже должен увеличить счётчик гнёзд");
    }

    @Test
    void addingScreenSchemaNodeAfterWiringBackfillsExistingSockets(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = sampleType();
        type.setPowerConnectorType(com.vjstb.ledscheme.model.PowerConnectorType.TRUECON);
        type = model.addCabinetType(type);
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 1, 4, 0, 0);
        model.selectScreen(screen);

        // Расключаем экран ДО того, как для него появится блок в общей схеме.
        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addPowerChain(1, ids);

        SchemaNode screenNode = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, "E", 0, 0, screen.getId());
        assertEquals(1, screenNode.getPowerConnectors().size(),
                "Блок, добавленный ПОСЛЕ расключения, должен сразу увидеть уже существующую цепочку");
        assertEquals("TRUEcon", screenNode.getPowerConnectors().get(0).getConnectorType());
        assertEquals(1, screenNode.getPowerConnectors().get(0).getCount());
    }

    @Test
    void rewiringChainDoesNotAccumulateSocketCountUnboundedly(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 1, 4, 0, 0);
        model.selectScreen(screen);
        SchemaNode screenNode = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, "E", 0, 0, screen.getId());

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        for (int i = 0; i < 3; i++) {
            model.addPowerChain(1, ids);
            String chainId = scene.getPowerChains().get(0).getId();
            model.deletePowerChain(chainId);
        }
        assertTrue(screenNode.getPowerConnectors().isEmpty(),
                "После удаления всех цепочек авто-гнёзда должны исчезнуть, а не накопиться");

        model.addPowerChain(1, ids);
        assertEquals(1, screenNode.getPowerConnectors().size());
        assertEquals(1, screenNode.getPowerConnectors().get(0).getCount(),
                "Пересборка цепочки не должна плодить гнёзда — count должен отражать РЕАЛЬНОЕ текущее число вводов");
    }

    @Test
    void manuallyAddedSocketSurvivesAutoResync(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 1, 4, 0, 0);
        model.selectScreen(screen);
        SchemaNode screenNode = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, "E", 0, 0, screen.getId());

        // Инженер вручную добавляет гнездо, не связанное с авто-отслеживанием.
        model.addPowerConnectorToNode(screenNode, "Резервный ввод", com.vjstb.ledscheme.model.PortDirection.IN, 1);

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addPowerChain(1, ids);

        assertEquals(2, screenNode.getPowerConnectors().size(),
                "Пересчёт авто-гнёзд не должен затрагивать вручную добавленное гнездо");
        assertTrue(screenNode.getPowerConnectors().stream()
                .anyMatch(p -> "Резервный ввод".equals(p.getConnectorType())));
    }

    @Test
    void cableTypeLibraryCrudAndDedup(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        com.vjstb.ledscheme.model.CableType c1 = model.addCableType(SchemaMode.POWER, "CEE 32A → 6×PowerCon");
        assertEquals(1, model.getCableTypes().size());
        assertEquals(1, model.cableTypesForMode(SchemaMode.POWER).size());
        assertEquals(0, model.cableTypesForMode(SchemaMode.SIGNAL).size());

        // Повторное сохранение той же подписи (без учёта регистра) для того же
        // режима не должно создавать дубликат — возвращает существующий кабель.
        com.vjstb.ledscheme.model.CableType c2 = model.addCableType(SchemaMode.POWER, "cee 32a → 6×powercon");
        assertEquals(c1.getId(), c2.getId());
        assertEquals(1, model.getCableTypes().size());

        model.addCableType(SchemaMode.SIGNAL, "Мой оптический кабель");
        assertEquals(2, model.getCableTypes().size());
        assertEquals(1, model.cableTypesForMode(SchemaMode.SIGNAL).size());

        model.deleteCableType(c1);
        assertEquals(1, model.getCableTypes().size());
        assertEquals(0, model.cableTypesForMode(SchemaMode.POWER).size());
    }

    @Test
    void hidingCabinetRemovesItFromPowerAndSignalChains(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 1, 4, 0, 0);
        model.selectScreen(screen);

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addPowerChain(1, ids);
        model.addSignalChain(1, false, ids); // порт 1

        String middleId = ids.get(1);
        model.toggleCabinetHidden(middleId);

        PowerChain pc = model.getCurrentScene().getPowerChains().get(0);
        assertFalse(pc.getCabinetInstanceIds().contains(middleId),
                "Скрытый кабинет не должен оставаться прописанным в силовой цепочке");
        SignalChain sc = model.getCurrentScene().getSignalChains().get(0);
        assertFalse(sc.getCabinetInstanceIds().contains(middleId),
                "Скрытый кабинет не должен оставаться прописанным в сигнальной цепочке");
    }

    @Test
    void hidingEveryCabinetOfAChainRemovesTheWholeChain(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 1, 2, 0, 0);
        model.selectScreen(screen);

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addPowerChain(1, ids);
        for (String id : ids) {
            model.toggleCabinetHidden(id);
        }
        assertEquals(0, model.getCurrentScene().getPowerChains().size(),
                "Цепочка, опустевшая после скрытия всех её кабинетов, должна удаляться целиком");
    }

    @Test
    void splitPowerChainLinkBreaksOneLinkNotWholeChain(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 1, 4, 0, 0);
        model.selectScreen(screen);

        List<String> ids = screen.getCabinets().stream().map(c -> c.getId()).toList();
        model.addPowerChain(1, ids);
        String chainId = model.getCurrentScene().getPowerChains().get(0).getId();

        model.splitPowerChainLink(chainId, 1); // разрыв между 2-м и 3-м кабинетом
        assertEquals(2, model.getCurrentScene().getPowerChains().size(), "Должно остаться две цепочки по обе стороны разрыва");
        List<Integer> sizes = model.getCurrentScene().getPowerChains().stream()
                .map(c -> c.getCabinetInstanceIds().size()).sorted().toList();
        assertEquals(List.of(2, 2), sizes);
        assertTrue(model.getCurrentScene().getPowerChains().stream().allMatch(c -> c.getPhase() == 1));

        model.undo();
        assertEquals(1, model.getCurrentScene().getPowerChains().size(), "Отмена должна вернуть исходную единую цепочку");
        assertEquals(4, model.getCurrentScene().getPowerChains().get(0).getCabinetInstanceIds().size());
    }

    @Test
    void splitSignalChainLinkPreservesPortAndBackup(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 1, 3, 0, 0);
        model.selectScreen(screen);

        List<String> ids = screen.getCabinets().stream().map(c -> c.getId()).toList();
        model.addSignalChain(2, false, ids);
        String chainId = model.getCurrentScene().getSignalChains().get(0).getId();

        model.splitSignalChainLink(chainId, 0); // разрыв после 1-го кабинета
        assertEquals(2, model.getCurrentScene().getSignalChains().size());
        for (SignalChain c : model.getCurrentScene().getSignalChains()) {
            assertEquals(2, c.getPortNumber());
            assertFalse(c.isBackup());
        }
        List<Integer> sizes = model.getCurrentScene().getSignalChains().stream()
                .map(c -> c.getCabinetInstanceIds().size()).sorted().toList();
        assertEquals(List.of(1, 2), sizes);
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
        assertEquals(1, model.getCurrentScene().getPowerChains().size());

        model.updateScreenGrid(screen, screen.getName(), type.getId(), 2, 3);
        assertEquals(6, screen.getCabinets().size());
        assertTrue(model.getCurrentScene().getPowerChains().isEmpty(), "Цепочка из удалённых кабинетов должна исчезнуть");
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
    void cabinetTypeAllowedShapesDefaultsToItsOwnShape() {
        // Старые файлы проектов (и просто типы без явно заданных allowedShapes)
        // должны трактоваться как "только их собственная форма" — без этого все
        // старые типы кабинетов внезапно позволяли бы назначать ЛЮБУЮ форму.
        CabinetType t = new CabinetType();
        t.setShape(com.vjstb.ledscheme.model.CabinetShape.TRIANGLE);
        assertEquals(java.util.Set.of(com.vjstb.ledscheme.model.CabinetShape.TRIANGLE), t.getAllowedShapes());

        t.setAllowedShapes(java.util.Set.of(com.vjstb.ledscheme.model.CabinetShape.TRIANGLE,
                com.vjstb.ledscheme.model.CabinetShape.ROUND));
        assertEquals(2, t.getAllowedShapes().size());
    }

    @Test
    void changingCabinetTypeClearsShapeOverrideNoLongerAllowed(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType rectOnly = model.addCabinetType(sampleType()); // allowedShapes -> {RECTANGLE} по умолчанию
        CabinetType roundCapable = new CabinetType();
        roundCapable.setName("Round-capable");
        roundCapable.setWidthMm(500);
        roundCapable.setHeightMm(500);
        roundCapable.setResolutionWidth(128);
        roundCapable.setResolutionHeight(128);
        roundCapable.setShape(com.vjstb.ledscheme.model.CabinetShape.ROUND);
        roundCapable.setAllowedShapes(java.util.Set.of(com.vjstb.ledscheme.model.CabinetShape.ROUND,
                com.vjstb.ledscheme.model.CabinetShape.TRIANGLE));
        model.addCabinetType(roundCapable);

        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", rectOnly.getId(), 1, 2, 0, 0);
        model.selectScreen(screen);
        String cabId = screen.getCabinets().get(0).getId();

        // Переключаем ячейку на "круглый" тип и вручную ставим "Треугольная" — валидно для этого типа.
        model.setCabinetTypeOverride(cabId, roundCapable.getId());
        model.setCabinetShapeOverride(cabId, com.vjstb.ledscheme.model.CabinetShape.TRIANGLE);
        assertEquals(com.vjstb.ledscheme.model.CabinetShape.TRIANGLE, screen.cabinetById(cabId).getShapeOverride());

        // Возврат к типу экрана по умолчанию (RECTANGLE-only) делает "Треугольная"
        // невозможной для этой ячейки — переопределение должно сброситься само,
        // а не остаться висеть недопустимой комбинацией.
        model.setCabinetTypeOverride(cabId, null);
        assertNull(screen.cabinetById(cabId).getShapeOverride());
    }

    @Test
    void cabinetRotationOverrideRoundTrips(@TempDir Path dir) {
        // Task #92/v1.5: угол непрямоугольной формы правится точечно по ячейке
        // (радиальное меню "Угол" в ShapeEditorPanel), не только на уровне типа.
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 1, 1, 0, 0);
        model.selectScreen(screen);
        String cabId = screen.getCabinets().get(0).getId();

        assertNull(screen.cabinetById(cabId).getRotationOverride());
        model.setCabinetRotationOverride(cabId, 270);
        assertEquals(270, screen.cabinetById(cabId).getRotationOverride());
        model.setCabinetRotationOverride(cabId, null);
        assertNull(screen.cabinetById(cabId).getRotationOverride());
    }

    @Test
    void updateCabinetTypePreservesAllFieldsIncludingPowerConnectorType(@TempDir Path dir) {
        // Task #94/v1.5: баг-репорт — при редактировании УЖЕ СОЗДАННОГО пресета
        // кабинета выбор разъёма питания (и угол/допустимые формы/номинал "Другой")
        // не сохранялся. Причина — AppModel.updateCabinetType копировал поля вручную
        // по отдельному списку, забывшему про несколько полей, добавленных позже.
        // Воспроизводим ровно тот же путь, что и CabinetTypeDialog: existing.copy(),
        // правим копию, отдаём её в updateCabinetType — ВСЕ поля должны примениться.
        AppModel model = freshModel(dir);
        CabinetType original = model.addCabinetType(sampleType());

        CabinetType edited = original.copy();
        edited.setPowerConnectorType(com.vjstb.ledscheme.model.PowerConnectorType.TRUECON);
        edited.setShape(com.vjstb.ledscheme.model.CabinetShape.TRIANGLE);
        edited.setAllowedShapes(java.util.Set.of(com.vjstb.ledscheme.model.CabinetShape.TRIANGLE,
                com.vjstb.ledscheme.model.CabinetShape.RECTANGLE));
        edited.setRotationDeg(270);
        edited.setCustomConnectorAmpRating(20.0);

        model.updateCabinetType(edited);

        CabinetType stored = model.getWorkspace().cabinetTypeById(original.getId());
        assertEquals(com.vjstb.ledscheme.model.PowerConnectorType.TRUECON, stored.getPowerConnectorType());
        assertEquals(com.vjstb.ledscheme.model.CabinetShape.TRIANGLE, stored.getShape());
        assertEquals(2, stored.getAllowedShapes().size());
        assertEquals(270.0, stored.getRotationDeg());
        assertEquals(20.0, stored.getCustomConnectorAmpRating());
    }

    @Test
    void updateControllerTypePreservesLoopPort(@TempDir Path dir) {
        // Тот же класс бага, что и выше, но для библиотеки контроллеров: флажок
        // "Есть Loop-порт" терялся при редактировании (AppModel.updateControllerType
        // не копировал loopPort обратно в хранимый объект).
        AppModel model = freshModel(dir);
        com.vjstb.ledscheme.model.ControllerType original = new com.vjstb.ledscheme.model.ControllerType();
        original.setName("MCTRL4K");
        model.addControllerType(original);

        com.vjstb.ledscheme.model.ControllerType edited = original.copy();
        edited.setLoopPort(true);
        model.updateControllerType(edited);

        com.vjstb.ledscheme.model.ControllerType stored = model.getWorkspace().controllerTypeById(original.getId());
        assertTrue(stored.isLoopPort());
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
        SignalChain main = model.signalChainByPort(screen, 3, false);
        assertNotNull(main);
        assertEquals(7, main.getBackupPortNumber());
        // Порт 7 теперь целиком отдан под резерв порта 3 — свою ручную цепочку получать не должен.
        assertTrue(model.isPortReservedAsBackup(screen, 7));
        assertFalse(model.isPortReservedAsBackup(screen, 3));
        assertEquals(1, model.backupPortLinkCount(screen));

        model.setSignalBackupPortLink(3, null);
        assertNull(model.signalChainByPort(screen, 3, false).getBackupPortNumber());
        assertFalse(model.isPortReservedAsBackup(screen, 7));
        assertEquals(0, model.backupPortLinkCount(screen));

        assertTrue(assertThrowsRuntime(() -> model.setSignalBackupPortLink(5, 5)));

        // Нельзя отдать под резерв порт, уже несущий собственную цепочку с кабинетами.
        model.selectScreen(screen);
        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).limit(1).toList();
        model.addSignalChain(4, false, ids);
        assertTrue(assertThrowsRuntime(() -> model.setSignalBackupPortLink(1, 4)));
    }

    @Test
    void addSignalChainRejectsReservedBackupPortDirectly(@TempDir Path dir) {
        // Защита должна срабатывать на уровне AppModel.addSignalChain САМОМ ПО СЕБЕ,
        // а не только через проверку в UI-колбэке (SignalStagePanel.onPortSelected) —
        // вызов напрямую, в обход UI, должен точно так же получить отказ.
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);
        model.updateSignalPortCount(screen, 8);

        model.setSignalBackupPortLink(2, 4);
        assertTrue(model.isPortReservedAsBackup(screen, 4));

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).limit(1).toList();
        assertTrue(assertThrowsRuntime(() -> model.addSignalChain(4, false, ids)));
        // Порт, который НЕ зарезервирован, по-прежнему можно расключить как обычно.
        model.addSignalChain(5, false, ids);
        assertNotNull(model.signalChainByPort(screen, 5, false));
    }

    @Test
    void signalAndPowerChainsCanBothSpanMultipleScreens(@TempDir Path dir) {
        // И питание, и сигнал физически могут продолжаться на другой экран сцены
        // (общий силовой ввод/проходной щит или сигнальный даунлинк на смежные
        // экраны) — раньше питание было строго ограничено текущим экраном и любая
        // попытка завершить цепочку, зашедшую на другой экран, бросала исключение,
        // из-за которого пользователь физически не мог завершить построение (см.
        // Task #64). Обе цепочки теперь проверяются по всей сцене одинаково.
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen a = model.addScreen("A", type.getId(), 2, 2, 0, 0);
        Screen b = model.addScreen("B", type.getId(), 2, 2, 1000, 0);
        model.selectScreen(a);

        String lastOfA = a.getCabinets().get(a.getCabinets().size() - 1).getId();
        String firstOfB = b.getCabinets().get(0).getId();

        model.addSignalChain(1, false, List.of(lastOfA, firstOfB));
        assertEquals(1, model.getCurrentScene().getSignalChains().size());
        assertTrue(model.isCabinetWiredForSignal(firstOfB), "Кабинет экрана B занят цепочкой, живущей на экране A");
        assertTrue(model.isCabinetWiredForSignal(lastOfA));
        assertFalse(model.isCabinetWiredForSignal(b.getCabinets().get(1).getId()));

        String lastOfA2 = a.getCabinets().get(a.getCabinets().size() - 2).getId();
        String secondOfB = b.getCabinets().get(1).getId();
        model.addPowerChain(2, List.of(lastOfA2, secondOfB));
        assertEquals(1, model.getCurrentScene().getPowerChains().size());
        assertTrue(model.isCabinetWiredForPower(lastOfA2));
        assertTrue(model.isCabinetWiredForPower(secondOfB), "Кабинет экрана B занят силовой цепочкой, живущей на экране A");
        assertFalse(model.isCabinetWiredForPower(firstOfB));
        // Фаза применяется к кабинету на ЕГО СОБСТВЕННОМ экране (B), а не только к
        // кабинетам currentScreen (A) — иначе addPowerChain молча пропускал бы фазу
        // для кросс-экранных кабинетов (та же причина, что и Task #64).
        assertEquals(2, b.cabinetById(secondOfB).getPhase());
    }

    @Test
    void addSignalChainFillsExistingBackupStubInsteadOfDuplicating(@TempDir Path dir) {
        // Регрессия: setSignalBackupPortLink(2, 5), назначенный ДО построения
        // цепочки порта 2, создаёт цепочку-заглушку (0 кабинетов) только чтобы
        // было где хранить резервный порт. Реальное построение цепочки на порту 2
        // должно заполнить именно эту заглушку, а не завести вторую отдельную
        // запись «Порт 2» — иначе в списке цепочек одна и та же прокладка
        // раздваивается на «0 каб. · резерв: порт 5» и «N каб.».
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);
        model.updateSignalPortCount(screen, 8);

        model.setSignalBackupPortLink(2, 5);
        assertEquals(1, model.getCurrentScene().getSignalChains().size());

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).limit(2).toList();
        model.addSignalChain(2, false, ids);

        assertEquals(1, model.getCurrentScene().getSignalChains().size());
        SignalChain chain = model.signalChainByPort(screen, 2, false);
        assertNotNull(chain);
        assertEquals(2, chain.getCabinetInstanceIds().size());
        assertEquals(5, chain.getBackupPortNumber());
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
    void controllersAreSharedAcrossAllScreensOfSameScene(@TempDir Path dir) {
        // Task #58: контроллеры общие для СЦЕНЫ, а не привязаны к одному экрану —
        // экран B должен видеть контроллер, добавленный (физически) под экраном A
        // той же сцены, и наоборот.
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen a = model.addScreen("A", type.getId(), 2, 2, 0, 0);
        Screen b = model.addScreen("B", type.getId(), 2, 2, 1000, 0);

        ControllerType ct = new ControllerType();
        ct.setName("VX1000");
        ct.setPortCount(4);
        model.addControllerType(ct);

        ControllerInstance ci = model.addControllerToScreen(a, ct.getId());
        assertEquals(4, model.effectiveSignalPortCount(a));
        assertEquals(4, model.effectiveSignalPortCount(b), "Экран B должен видеть контроллер, добавленный на A");

        List<ControllerInstance> sceneControllers = model.controllersInScene(model.getCurrentScene());
        assertEquals(1, sceneControllers.size());
        assertTrue(sceneControllers.contains(ci));

        // Удаление через сцену (не важно, под каким экраном физически хранится) —
        // после удаления оба экрана возвращаются к ручному signalPortCount (по
        // умолчанию 8 — см. Screen), а не к нулю.
        model.removeControllerFromScene(model.getCurrentScene(), ci.getId());
        assertEquals(8, model.effectiveSignalPortCount(a));
        assertEquals(8, model.effectiveSignalPortCount(b));
        assertTrue(model.controllersInScene(model.getCurrentScene()).isEmpty());
    }

    @Test
    void controllerLevelBackupReservesAllItsPorts(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);

        ControllerType ct = new ControllerType();
        ct.setName("VX1000");
        ct.setPortCount(4);
        model.addControllerType(ct);

        ControllerInstance main = model.addControllerToScreen(screen, ct.getId());
        ControllerInstance backup = model.addControllerToScreen(screen, ct.getId());
        ControllerInstance third = model.addControllerToScreen(screen, ct.getId());
        assertEquals(12, model.effectiveSignalPortCount(screen));

        assertEquals(main, model.controllerForPort(screen, 1));
        assertEquals(backup, model.controllerForPort(screen, 5));
        assertFalse(model.isPortReservedAsBackup(screen, 5));

        model.setControllerBackupLink(screen, main.getId(), backup.getId());
        assertEquals(backup.getId(), main.getBackupControllerId());
        // ВСЕ порты резервного контроллера (5-8) зарезервированы, порты основного (1-4) — нет.
        assertTrue(model.isPortReservedAsBackup(screen, 5));
        assertTrue(model.isPortReservedAsBackup(screen, 8));
        assertFalse(model.isPortReservedAsBackup(screen, 1));
        assertTrue(model.isControllerReservedAsBackup(screen, backup.getId()));

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).limit(1).toList();
        assertTrue(assertThrowsRuntime(() -> model.addSignalChain(5, false, ids)));
        model.addSignalChain(1, false, ids); // порт основного контроллера по-прежнему доступен

        // нельзя назначить резервным контроллер, у которого уже есть собственная цепочка (порт 9 — у third)
        model.addSignalChain(9, false, ids);
        assertTrue(assertThrowsRuntime(() -> model.setControllerBackupLink(screen, main.getId(), third.getId())));

        // удаление резервного контроллера снимает связку с основного (не остаётся висячей ссылки)
        model.removeControllerFromScreen(screen, backup.getId());
        assertNull(main.getBackupControllerId());
    }

    @Test
    void cardBasedControllerPortCountOverridesManualCount() {
        // Novastar H-серии и подобные модульные контроллеры: если заданы карты,
        // эффективное число портов считается по ним, а не по ручному portCount.
        ControllerType h = new ControllerType();
        h.setName("H2");
        h.setPortCount(8); // ручное значение — должно игнорироваться при наличии карт
        assertEquals(8, h.effectivePortCount());

        h.getCards().add(new SchemaCard("Карта вывода 1", List.of(new CardPort("RJ45", PortDirection.OUT, 4))));
        h.getCards().add(new SchemaCard("Карта вывода 2", List.of(new CardPort("оптика", PortDirection.OUT, 2))));
        assertEquals(6, h.effectivePortCount());

        ControllerType copy = h.copy();
        assertEquals(6, copy.effectivePortCount());
        assertEquals(2, copy.getCards().size());
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

        // между той же парой узлов можно провести ещё одну связь другого типа/цвета —
        // не дедуплицируется (несколько параллельных линий разного назначения)
        model.addSchemaEdge(SchemaMode.POWER, source.getId(), screenNode.getId(), null);
        assertEquals(2, model.schemaEdgesForCurrentScene(SchemaMode.POWER).size());

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

    @Test
    void schemaEdgeCanAnchorToSpecificConnectorSockets(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));

        SchemaNode pdu = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "PDU", 0, 0, null);
        CardPort in = model.addPowerConnectorToNode(pdu, "CEE 32A", PortDirection.IN, 1);
        SchemaNode box = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Box", 200, 0, null);
        CardPort out = model.addPowerConnectorToNode(box, "Schuko", PortDirection.OUT, 1);

        // Обычное соединение (без гнёзд) — portId остаются null, поведение как раньше.
        SchemaEdge plain = model.addSchemaEdge(SchemaMode.POWER, pdu.getId(), box.getId(), null);
        assertNull(plain.getFromPortId());
        assertNull(plain.getToPortId());

        // Соединение через конкретные гнёзда.
        SchemaEdge socketEdge = model.addSchemaEdge(SchemaMode.POWER, pdu.getId(), in.getId(),
                box.getId(), out.getId(), null);
        assertEquals(in.getId(), socketEdge.getFromPortId());
        assertEquals(out.getId(), socketEdge.getToPortId());
        assertEquals(2, model.schemaEdgesForCurrentScene(SchemaMode.POWER).size());
    }

    @Test
    void structuredWireLabelComposesAndFeedsSpec(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        model.addScreen("E", type.getId(), 2, 2, 0, 0);

        SchemaNode a = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "Щит", 0, 0, null);
        SchemaNode b = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Бокс", 200, 0, null);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.POWER, a.getId(), b.getId(), null);
        assertFalse(edge.hasStructuredWire());

        model.updateSchemaEdgeWire(edge, 3, "CEE 32A", 25.0);
        assertTrue(edge.hasStructuredWire());
        assertEquals("3×CEE 32A, 25м", edge.displayLabel());
        assertEquals(edge.displayLabel(), edge.getLabel());

        assertTrue(assertThrowsRuntime(() -> model.updateSchemaEdgeWire(edge, 0, "CEE 32A", null)));
        assertTrue(assertThrowsRuntime(() -> model.updateSchemaEdgeWire(edge, 2, " ", null)));

        // свободная подпись сбрасывает структуру
        model.updateSchemaEdgeLabel(edge, "просто заметка");
        assertFalse(edge.hasStructuredWire());
        assertEquals("просто заметка", edge.displayLabel());
    }

    @Test
    void schemaNodeCardsSumInputsOutputs(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));

        SchemaNode server = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "E2", 0, 0, null);
        assertTrue(server.getCards().isEmpty());

        SchemaCard c1 = model.addCardToNode(server, "Вход 4xHDMI",
                List.of(new CardPort("HDMI", PortDirection.IN, 4)));
        model.addCardToNode(server, "Выход 2xSDI", List.of(new CardPort("SDI", PortDirection.OUT, 2)));
        assertEquals(2, server.getCards().size());
        int totalIn = server.getCards().stream().mapToInt(SchemaCard::totalInputs).sum();
        int totalOut = server.getCards().stream().mapToInt(SchemaCard::totalOutputs).sum();
        assertEquals(4, totalIn);
        assertEquals(2, totalOut);

        model.removeCardFromNode(server, c1.getId());
        assertEquals(1, server.getCards().size());
    }

    @Test
    void schemaNodePowerConnectorsIndependentFromCards(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));

        SchemaNode distro = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Щит", 0, 0, null);
        assertTrue(distro.getPowerConnectors().isEmpty());

        CardPort in = model.addPowerConnectorToNode(distro, "CEE 32A", PortDirection.IN, 1);
        model.addPowerConnectorToNode(distro, "Schuko", PortDirection.OUT, 8);
        assertEquals(2, distro.getPowerConnectors().size());
        // не смешивается с видеокартами (cards остаётся отдельным списком)
        assertTrue(distro.getCards().isEmpty());

        model.removePowerConnectorFromNode(distro, in.getId());
        assertEquals(1, distro.getPowerConnectors().size());
        assertEquals("Schuko", distro.getPowerConnectors().get(0).getConnectorType());
    }

    @Test
    void equipmentPresetPowerConnectorsCopiedToNewNode(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));

        var preset = model.addEquipmentPreset(SchemaMode.POWER, SchemaNodeType.DISTRO, "PDU-16", "Дистрибьютор", null);
        model.addPowerConnectorToPreset(preset, "CEE 32A", PortDirection.IN, 1);
        model.addPowerConnectorToPreset(preset, "Schuko", PortDirection.OUT, 8);
        assertEquals(2, preset.getPowerConnectors().size());

        SchemaNode node = model.addSchemaNodeFromPreset(SchemaMode.POWER, preset, 10, 20);
        assertEquals("PDU-16", node.getLabel());
        assertEquals(2, node.getPowerConnectors().size());
        // копии, а не общие ссылки — правка узла не должна задевать пресет
        model.removePowerConnectorFromNode(node, node.getPowerConnectors().get(0).getId());
        assertEquals(1, node.getPowerConnectors().size());
        assertEquals(2, preset.getPowerConnectors().size());
    }

    @Test
    void equipmentPresetCrudAndApplyToNewNode(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));

        assertTrue(model.getEquipmentPresets().isEmpty());
        var preset = model.addEquipmentPreset(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "Barco E2", "Медиасервер", null);
        model.addCardToPreset(preset, "Вход 4xHDMI", List.of(new CardPort("HDMI", PortDirection.IN, 4)));
        model.addCardToPreset(preset, "Выход 2xSDI", List.of(new CardPort("SDI", PortDirection.OUT, 2)));
        assertEquals(1, model.getEquipmentPresets().size());
        assertEquals(2, preset.getCards().size());
        assertEquals(1, model.presetsForCategory(SchemaMode.SIGNAL, SchemaNodeType.SERVER).size());
        assertTrue(model.presetsForCategory(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER).isEmpty());
        assertTrue(model.presetsForCategory(SchemaMode.POWER, SchemaNodeType.SERVER).isEmpty());

        SchemaNode node = model.addSchemaNodeFromPreset(SchemaMode.SIGNAL, preset, 10, 20);
        assertEquals("Barco E2", node.getLabel());
        assertEquals(SchemaNodeType.SERVER, node.getType());
        assertEquals(2, node.getCards().size());
        // копии карт, а не общие ссылки — правка узла не должна задевать пресет
        model.removeCardFromNode(node, node.getCards().get(0).getId());
        assertEquals(1, node.getCards().size());
        assertEquals(2, preset.getCards().size());

        model.updateEquipmentPreset(preset, SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER, "PixelHue Q8", "Видеопроцессор");
        assertEquals(SchemaNodeType.CONTROLLER, preset.getCategory());
        assertEquals("PixelHue Q8", preset.getName());
        assertTrue(assertThrowsRuntime(() ->
                model.updateEquipmentPreset(preset, SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER, " ", "")));

        model.deleteEquipmentPreset(preset);
        assertTrue(model.getEquipmentPresets().isEmpty());
    }

    @Test
    void multipleNodesFromSamePresetGetIndependentPortIds(@TempDir Path dir) {
        // Регрессия: несколько узлов схемы, созданных из ОДНОГО пресета (например,
        // три отдельные физические "Проходные" коробки), раньше получали гнёзда с
        // ОДИНАКОВЫМИ id (SchemaCard.copy()/CardPort.copy() сохраняют id как есть).
        // SchemaCanvasPanel.usedCount считает занятые линии гнезда по совпадению
        // portId — с совпадающими id лимит "Максимум линий на этом гнезде" одного
        // физического блока ошибочно делился на ВСЕ узлы того же пресета сразу,
        // вместо того чтобы считаться отдельно для каждого. Каждое применение
        // пресета теперь обязано генерировать НОВЫЕ id для карт/гнёзд/разъёмов.
        AppModel model = freshModel(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));

        var preset = model.addEquipmentPreset(SchemaMode.POWER, SchemaNodeType.DISTRO, "Проходная", "Power passthrough", null);
        model.addPowerConnectorToPreset(preset, "CEE 32A", PortDirection.IN, 1);
        model.addPowerConnectorToPreset(preset, "CEE 16A", PortDirection.OUT, 6);
        model.addCardToPreset(preset, "Карта", List.of(new CardPort("HDMI", PortDirection.OUT, 2)));

        SchemaNode nodeA = model.addSchemaNodeFromPreset(SchemaMode.POWER, preset, 0, 0);
        SchemaNode nodeB = model.addSchemaNodeFromPreset(SchemaMode.POWER, preset, 200, 0);

        // Структура (число карт/разъёмов) не меняется — регенерация id не создаёт и
        // не теряет ни одной карты/гнезда по сравнению с пресетом.
        assertEquals(preset.getPowerConnectors().size(), nodeA.getPowerConnectors().size());
        assertEquals(preset.getPowerConnectors().size(), nodeB.getPowerConnectors().size());
        assertEquals(preset.getCards().size(), nodeA.getCards().size());
        assertEquals(preset.getCards().size(), nodeB.getCards().size());
        assertEquals(preset.getCards().get(0).getPorts().size(), nodeA.getCards().get(0).getPorts().size());

        for (int i = 0; i < preset.getPowerConnectors().size(); i++) {
            String presetId = preset.getPowerConnectors().get(i).getId();
            String idA = nodeA.getPowerConnectors().get(i).getId();
            String idB = nodeB.getPowerConnectors().get(i).getId();
            assertNotEquals(presetId, idA, "Гнездо узла не должно совпадать с id гнезда пресета");
            assertNotEquals(presetId, idB);
            assertNotEquals(idA, idB, "Два узла из одного пресета не должны делить id одного и того же гнезда");
            // Сами данные (тип/направление/количество) при этом совпадают с пресетом.
            assertEquals(preset.getPowerConnectors().get(i).getConnectorType(), nodeA.getPowerConnectors().get(i).getConnectorType());
            assertEquals(preset.getPowerConnectors().get(i).getCount(), nodeA.getPowerConnectors().get(i).getCount());
        }

        String presetCardId = preset.getCards().get(0).getId();
        String cardIdA = nodeA.getCards().get(0).getId();
        String cardIdB = nodeB.getCards().get(0).getId();
        assertNotEquals(presetCardId, cardIdA);
        assertNotEquals(presetCardId, cardIdB);
        assertNotEquals(cardIdA, cardIdB);

        String presetPortId = preset.getCards().get(0).getPorts().get(0).getId();
        String portIdA = nodeA.getCards().get(0).getPorts().get(0).getId();
        String portIdB = nodeB.getCards().get(0).getPorts().get(0).getId();
        assertNotEquals(presetPortId, portIdA);
        assertNotEquals(presetPortId, portIdB);
        assertNotEquals(portIdA, portIdB, "Два узла из одного пресета не должны делить id одного и того же порта карты");
        assertEquals(preset.getCards().get(0).getPorts().get(0).getConnectorType(),
                nodeA.getCards().get(0).getPorts().get(0).getConnectorType());
    }

    @Test
    void newScreenDefaultsToRiggedMountWithSuggestedPoints(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = sampleType();
        model.addCabinetType(type);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));

        // Формула из риг-тех таблиц заказчика: "Hanging bar" = ширина в модулях / 2,
        // округление вверх, минимум 2 точки.
        Screen narrow = model.addScreen("Узкий", type.getId(), 5, 3, 0, 0);
        assertEquals(com.vjstb.ledscheme.model.ScreenMountType.RIGGED, narrow.getMountType());
        assertEquals(2, narrow.getRiggingPointsCount()); // ceil(3/2)=2, floor 2

        Screen wide = model.addScreen("Широкий", type.getId(), 5, 12, 0, 0);
        assertEquals(6, wide.getRiggingPointsCount()); // 12/2=6

        Screen odd = model.addScreen("Нечётный", type.getId(), 5, 7, 0, 0);
        assertEquals(4, odd.getRiggingPointsCount()); // ceil(7/2)=4
    }

    @Test
    void controllerPortBandwidthAndPixelsAreInterdependent() {
        // опорная точка из документации NovaStar: 1 Гбит/с @ 60Гц/8бит = 650 000 px/порт
        assertEquals(650_000, ControllerType.maxPixelsFor(1000, 60, 8));
        // при той же пропускной способности бОльшая герцовка/глубина цвета — меньше пикселей
        assertTrue(ControllerType.maxPixelsFor(1000, 120, 8) < 650_000);
        assertTrue(ControllerType.maxPixelsFor(1000, 60, 10) < 650_000);

        ControllerType ct = new ControllerType();
        ct.setPortBandwidthMbps(1000);
        assertEquals(650_000, ct.referencePixelsPerPort());

        // обратный пересчёт: сколько Мбит/с нужно для 650 000 px @60/8 — снова ~1000
        double mbps = ControllerType.bandwidthForPixels(650_000, 60, 8);
        assertEquals(1000.0, mbps, 1.0);
    }

    @Test
    void screenSignalSpecAffectsEffectivePortCapacity(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);
        assertEquals(60, screen.getRefreshRateHz());
        assertEquals(8, screen.getColorBitDepth());

        model.updateScreenSignalSpec(screen, 120, 10);
        assertEquals(120, screen.getRefreshRateHz());
        assertEquals(10, screen.getColorBitDepth());

        assertTrue(assertThrowsRuntime(() -> model.updateScreenSignalSpec(screen, 0, 8)));
        assertTrue(assertThrowsRuntime(() -> model.updateScreenSignalSpec(screen, 60, 0)));

        model.undo();
        assertEquals(60, screen.getRefreshRateHz());
        assertEquals(8, screen.getColorBitDepth());
    }

    @Test
    void canvasHoldsScreenPlacementsAndCleansUpOnScreenDelete(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen a = model.addScreen("A", type.getId(), 2, 2, 0, 0);
        Screen b = model.addScreen("B", type.getId(), 2, 2, 900, 0);

        assertTrue(model.canvasesForCurrentScene().isEmpty());
        ContentCanvas canvas = model.addCanvas("Resolume 1080p", 1920, 1080);
        assertEquals(1, model.canvasesForCurrentScene().size());

        CanvasPlacement pa = model.addScreenToCanvas(canvas, a.getId(), 0, 0);
        model.addScreenToCanvas(canvas, b.getId(), 300, 0);
        assertEquals(2, canvas.getPlacements().size());

        // повторное добавление того же экрана не дублирует размещение
        model.addScreenToCanvas(canvas, a.getId(), 999, 999);
        assertEquals(2, canvas.getPlacements().size());

        model.movePlacement(pa, 50, 60);
        assertEquals(50, pa.getX());
        assertEquals(60, pa.getY());

        model.updateCanvas(canvas, "Renamed", 3840, 2160);
        assertEquals("Renamed", canvas.getName());
        assertEquals(3840, canvas.getWidthPx());

        model.deleteScreen(a);
        assertEquals(1, canvas.getPlacements().size(), "Удаление экрана должно убрать его размещение из канваса");

        model.deleteCanvas(canvas);
        assertTrue(model.canvasesForCurrentScene().isEmpty());
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
