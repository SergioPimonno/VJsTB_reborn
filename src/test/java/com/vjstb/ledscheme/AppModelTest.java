package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CableLengthProfile;
import com.vjstb.ledscheme.model.CanvasPlacement;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.InterfaceType;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.ProjectorInstance;
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
import com.vjstb.ledscheme.sync.LibrarySyncClient;
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

        // новый экземпляр (имитация перезапуска приложения) читает те же файлы —
        // библиотека и проекты хранятся раздельно (см. LibraryStore), поэтому
        // перечитываем именно через AppModel, а не голый WorkspaceStore.load()
        AppModel model2 = new AppModel(new WorkspaceStore(file));
        Workspace reloaded = model2.getWorkspace();
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
    void cabinetLibraryExportImportRoundTripsAndUpdatesByName(@TempDir Path dir) throws Exception {
        AppModel model = freshModel(dir);
        model.addCabinetType(sampleType());
        File file = new File(dir.toFile(), "cabinets.json");
        model.exportCabinetLibrary(file);

        AppModel other = freshModel(dir.resolve("other"));
        int n = other.importCabinetLibrary(file);
        assertEquals(1, n);
        assertEquals(1, other.getCabinetTypes().size());
        assertEquals("Test P3 500x500", other.getCabinetTypes().get(0).getName());

        // повторный импорт того же файла не плодит дубликат — обновляет существующий по имени
        other.importCabinetLibrary(file);
        assertEquals(1, other.getCabinetTypes().size());
    }

    @Test
    void cableLibraryExportImportMatchesByModeAndLabel(@TempDir Path dir) throws Exception {
        AppModel model = freshModel(dir);
        model.addCableType(SchemaMode.SIGNAL, "Optical fiber");
        File file = new File(dir.toFile(), "cables.json");
        model.exportCableLibrary(file);

        AppModel other = freshModel(dir.resolve("other"));
        int n = other.importCableLibrary(file);
        assertEquals(1, n);
        assertEquals(1, other.getCableTypes().size());

        other.importCableLibrary(file);
        assertEquals(1, other.getCableTypes().size());
    }

    @Test
    void allLibrariesBundleExportImportCoversEveryType(@TempDir Path dir) throws Exception {
        AppModel model = freshModel(dir);
        model.addCabinetType(sampleType());
        com.vjstb.ledscheme.model.ControllerType controllerType = new com.vjstb.ledscheme.model.ControllerType();
        controllerType.setName("MCTRL4K");
        model.addControllerType(controllerType);
        model.addEquipmentPreset(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "Media Server", "", List.of());
        model.addCableType(SchemaMode.POWER, "CEE 32A");

        File file = new File(dir.toFile(), "all.json");
        model.exportAllLibraries(file);

        AppModel other = freshModel(dir.resolve("other"));
        int total = other.importAllLibraries(file);
        assertEquals(4, total);
        assertEquals(1, other.getCabinetTypes().size());
        assertEquals(1, other.getWorkspace().getControllerTypes().size());
        assertEquals(1, other.getEquipmentPresets().size());
        assertEquals(1, other.getCableTypes().size());

        // повторный импорт не плодит дубликаты
        other.importAllLibraries(file);
        assertEquals(1, other.getCabinetTypes().size());
        assertEquals(1, other.getWorkspace().getControllerTypes().size());
        assertEquals(1, other.getEquipmentPresets().size());
        assertEquals(1, other.getCableTypes().size());
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
        // вызов напрямую, в обход UI, должен точно так же получить отказ. Резервный
        // порт не получает собственной цепочки — на общей схеме он появляется как
        // гнездо на ПОСЛЕДНЕМ кабинете основной цепочки (см.
        // chainEndpointSocketCabinetIdsCoversPowerEntryAndSignalMainPlusBackupEntries),
        // без отдельной проводки.
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
    void schemaEdgeCanAnchorToChainEndpointCabinetSocket(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);
        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addPowerChain(1, List.of(ids.get(0), ids.get(1)));

        SchemaNode source = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "Щит А1", 0, 0, null);
        SchemaNode screenNode = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, screen.getName(),
                200, 0, screen.getId());

        SchemaEdge edge = model.addSchemaEdge(SchemaMode.POWER, source.getId(), null, null,
                screenNode.getId(), null, ids.get(0), null);
        assertNull(edge.getFromCabinetInstanceId());
        assertEquals(ids.get(0), edge.getToCabinetInstanceId());
        assertNull(edge.getToPortId(), "Кабинет-гнездо и CardPort-гнездо — разные оси, обе не заданы одновременно");

        // Ссылка на кабинет переживает copy() (см. SchemaEdge.copy) — важно для
        // undo/redo и клонирования сцены, где рёбра копируются целиком.
        SchemaEdge copied = edge.copy();
        assertEquals(ids.get(0), copied.getToCabinetInstanceId());
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

    @Test
    void setMaskColorIsSharedAcrossAllPlacementsOfTheSameScreen(@TempDir Path dir) {
        // Баг-репорт 2026-08-13: цвет чек-борда маски раньше хранился per-CanvasPlacement,
        // одна и та же запись экрана могла показывать РАЗНЫЙ цвет в разных канвасах.
        // Теперь цвет общий на Screen — один вызов setMaskColor обязан отразиться сразу
        // на всех местах, читающих screen.getBackground() (PixelGridRenderer и т.д.),
        // независимо от того, сколько канвасов/размещений у этого экрана.
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen a = model.addScreen("A", type.getId(), 2, 2, 0, 0);
        assertEquals(com.vjstb.ledscheme.model.MaskColorPreset.NORMAL, a.getBackground());

        ContentCanvas canvas1 = model.addCanvas("Канвас 1", 1920, 1080);
        ContentCanvas canvas2 = model.addCanvas("Канвас 2", 1280, 720);
        model.addScreenToCanvas(canvas1, a.getId(), 0, 0);
        model.addScreenToCanvas(canvas2, a.getId(), 0, 0);

        model.setMaskColor(a, com.vjstb.ledscheme.model.MaskColorPreset.RED_GRAY);

        assertEquals(com.vjstb.ledscheme.model.MaskColorPreset.RED_GRAY, a.getBackground(),
                "цвет должен примениться напрямую к экрану");
        // Оба размещения читают цвет ЧЕРЕЗ экран (см. PixelGridRenderer.GridRenderOptions.of),
        // а не хранят собственную копию — здесь просто подтверждаем, что модель их не трогает.
        assertEquals(1, canvas1.getPlacements().size());
        assertEquals(1, canvas2.getPlacements().size());
    }

    @Test
    @SuppressWarnings("deprecation") // CanvasPlacement.setBackground -- намеренно, имитирует старый проект
    void legacyPerPlacementMaskColorIsSeededOntoScreenOnLoad(@TempDir Path dir) {
        // Миграция при загрузке (AppModel constructor -> seedScreenMaskColorsFromLegacyPlacements):
        // проект, сохранённый ДО переноса цвета на Screen, имел цвет только на
        // CanvasPlacement.background — при следующей загрузке того же файла новый AppModel
        // обязан перенести его на Screen, а не молча сбросить выбор пользователя на NORMAL.
        File workspaceFile = new File(dir.toFile(), "workspace.json");
        AppModel model1 = new AppModel(new WorkspaceStore(workspaceFile));
        CabinetType type = model1.addCabinetType(sampleType());
        model1.selectProject(model1.addProject("P"));
        model1.selectScene(model1.addScene("S"));
        Screen scr = model1.addScreen("A", type.getId(), 2, 2, 0, 0);
        ContentCanvas canvas = model1.addCanvas("Канвас", 1920, 1080);
        CanvasPlacement pl = model1.addScreenToCanvas(canvas, scr.getId(), 0, 0);
        // Прямая запись через устаревший (но всё ещё десериализуемый) сеттер -- имитирует
        // проект, сохранённый старой версией приложения, до появления Screen#background.
        pl.setBackground(com.vjstb.ledscheme.model.MaskColorPreset.FULL_GREEN);
        model1.movePlacement(pl, 1, 1); // любой мутатор -- лишь бы вызвать changed()/persist()

        AppModel model2 = new AppModel(new WorkspaceStore(workspaceFile));
        // getCurrentProject/Scene не восстанавливаются сами по себе при загрузке (см. остальные
        // тесты в этом файле) -- достаём экран напрямую из дерева workspace.
        Screen reloadedScreen = model2.getWorkspace().getProjects().get(0).getScenes().get(0).getScreens().get(0);
        assertEquals(com.vjstb.ledscheme.model.MaskColorPreset.FULL_GREEN, reloadedScreen.getBackground(),
                "цвет из старого per-placement поля должен перенестись на экран при загрузке");
    }

    @Test
    void addProjectorAppendsToCurrentSceneProjectorList(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));

        ProjectorInstance p = new ProjectorInstance();
        p.setLabel("Проектор 1");
        model.addProjector(p);

        assertEquals(1, model.projectorsForCurrentScene().size());
        assertEquals("Проектор 1", model.getCurrentScene().getProjectors().get(0).getLabel());
    }

    @Test
    void deleteProjectorRemovesById(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));

        ProjectorInstance p = new ProjectorInstance();
        model.addProjector(p);

        model.deleteProjector(p.getId());
        assertTrue(model.projectorsForCurrentScene().isEmpty());
    }

    @Test
    void addProjectorWithoutSelectedSceneThrows(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        assertTrue(assertThrowsRuntime(() -> model.addProjector(new ProjectorInstance())));
    }

    private static boolean assertThrowsRuntime(Runnable r) {
        try {
            r.run();
            return false;
        } catch (RuntimeException ex) {
            return true;
        }
    }

    @Test
    void librarySyncAddsNewItemWithServerId(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        var dto = new LibrarySyncClient.LibraryItemDto("srv-1", "CABINET", "Synced Cabinet",
                "{\"name\":\"Synced Cabinet\"}", 5, false);

        AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(List.of(dto));

        assertEquals(1, summary.added());
        assertEquals(0, summary.updated());
        // Синк пишет в ОБЩУЮ библиотеку (Task #135/v2.0), не в личную.
        assertEquals(1, model.getWorkspace().getSharedCabinetTypes().size());
        assertEquals("srv-1", model.getWorkspace().getSharedCabinetTypes().get(0).getId());
        assertEquals("Synced Cabinet", model.getWorkspace().getSharedCabinetTypes().get(0).getName());
        assertTrue(model.getWorkspace().getCabinetTypes().isEmpty(), "личная библиотека не тронута синком");
    }

    @Test
    void librarySyncUpdatesExistingItemInPlaceByServerId(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-1", "CABINET", "A", "{\"name\":\"A\"}", 5, false)));

        AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-1", "CABINET", "B", "{\"name\":\"B\"}", 6, false)));

        assertEquals(0, summary.added());
        assertEquals(1, summary.updated());
        assertEquals(1, model.getWorkspace().getSharedCabinetTypes().size(), "не должно быть дубликата");
        assertEquals("B", model.getWorkspace().getSharedCabinetTypes().get(0).getName());
    }

    @Test
    void librarySyncPromotesMatchingPersonalItemAndUnionStaysSingle(@TempDir Path dir) {
        // Регрессия на реальный баг: личная библиотека уже содержала "MG12" под
        // своим локальным id (заведено вручную/до синка); та же запись массово
        // залита на сервер через админ-консоль под НОВЫМ id (сервер не знает про
        // локальный id) — синк не должен завести дубликат, а "продвинуть" запись:
        // общая библиотека получает синхронизированную версию, личная — теряет
        // свою (Task #135/v2.0: "личная библиотека у каждого нулевая").
        AppModel model = freshModel(dir);
        model.addCabinetType(new com.vjstb.ledscheme.model.CabinetType());
        model.getWorkspace().getCabinetTypes().get(0).setName("MG12");
        String localId = model.getWorkspace().getCabinetTypes().get(0).getId();

        var dto = new LibrarySyncClient.LibraryItemDto("srv-new-id", "CABINET", "MG12",
                "{\"name\":\"MG12\"}", 5, false);
        AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(List.of(dto));

        assertEquals(1, summary.added());
        assertEquals(1, model.getWorkspace().getSharedCabinetTypes().size());
        assertEquals("srv-new-id", model.getWorkspace().getSharedCabinetTypes().get(0).getId());
        assertNotEquals(localId, model.getWorkspace().getSharedCabinetTypes().get(0).getId());
        assertTrue(model.getWorkspace().getCabinetTypes().isEmpty(), "личная запись должна исчезнуть после продвижения");
        assertEquals(1, model.getCabinetTypes().size(), "объединённый список не должен задублировать");
        assertEquals("srv-new-id", model.getCabinetTypes().get(0).getId());
    }

    @Test
    void librarySyncPromotesMatchingPersonalCableByLabel(@TempDir Path dir) {
        // Тот же приём продвижения, но для вида с именующим полем getLabel(), а не
        // getName() — не должен быть завязан только на кабинет-специфичный путь.
        AppModel model = freshModel(dir);
        model.addCableType(com.vjstb.ledscheme.model.SchemaMode.POWER, "CEE 16A");

        var dto = new LibrarySyncClient.LibraryItemDto("srv-cable-1", "CABLE", "CEE 16A",
                "{\"mode\":\"POWER\",\"label\":\"CEE 16A\"}", 5, false);
        model.applyLibrarySyncItems(List.of(dto));

        assertEquals(1, model.getWorkspace().getSharedCableTypes().size());
        assertTrue(model.getWorkspace().getCableTypes().isEmpty(), "личная запись должна исчезнуть после продвижения");
        assertEquals(1, model.getCableTypes().size());
        assertEquals("srv-cable-1", model.getCableTypes().get(0).getId());
    }

    @Test
    void unionGetterShowsBothSharedAndPersonal(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.addCabinetType(new com.vjstb.ledscheme.model.CabinetType());
        model.getWorkspace().getCabinetTypes().get(0).setName("Personal-A");
        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-1", "CABINET", "Shared-B", "{\"name\":\"Shared-B\"}", 5, false)));

        assertEquals(2, model.getCabinetTypes().size());
        assertTrue(model.getCabinetTypes().stream().anyMatch(c -> c.getName().equals("Personal-A")));
        assertTrue(model.getCabinetTypes().stream().anyMatch(c -> c.getName().equals("Shared-B")));
        assertTrue(model.isSharedCabinetType(model.getWorkspace().getSharedCabinetTypes().get(0).getId()));
        assertFalse(model.isSharedCabinetType(model.getWorkspace().getCabinetTypes().get(0).getId()));
    }

    @Test
    void addCabinetTypeNeverTouchesSharedList(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-1", "CABINET", "Shared-B", "{\"name\":\"Shared-B\"}", 5, false)));

        model.addCabinetType(new com.vjstb.ledscheme.model.CabinetType());
        model.getWorkspace().getCabinetTypes().get(0).setName("Personal-A");

        assertEquals(1, model.getWorkspace().getSharedCabinetTypes().size());
        assertEquals(1, model.getWorkspace().getCabinetTypes().size());
    }

    @Test
    void cableLengthProfileCrud(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CableLengthProfile p = new CableLengthProfile();
        p.setName("XLR");
        p.setAvailableLengthsM(List.of(10.0, 20.0, 30.0));
        p.setMarginPercent(10);

        model.addCableLengthProfile(p);
        assertEquals(1, model.getCableLengthProfiles().size());
        assertNotNull(model.cableLengthProfileByName("xlr"), "поиск должен быть без учёта регистра");

        assertThrowsRuntime(() -> {
            CableLengthProfile dup = new CableLengthProfile();
            dup.setName("XLR");
            model.addCableLengthProfile(dup);
        });

        CableLengthProfile edited = p.copy();
        edited.setMarginPercent(15);
        model.updateCableLengthProfile(edited);
        assertEquals(15, model.getCableLengthProfiles().get(0).getMarginPercent());

        model.deleteCableLengthProfile(p.getId());
        assertTrue(model.getCableLengthProfiles().isEmpty());
    }

    @Test
    void librarySyncAddsCableLengthProfile(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        var dto = new LibrarySyncClient.LibraryItemDto("srv-clp-1", "CABLE_LENGTH_PROFILE", "XLR",
                "{\"name\":\"XLR\",\"availableLengthsM\":[10.0,20.0],\"marginPercent\":10.0}", 5, false);

        AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(List.of(dto));

        assertEquals(1, summary.added());
        assertEquals(1, model.getCableLengthProfiles().size());
        assertEquals("srv-clp-1", model.getCableLengthProfiles().get(0).getId());
        assertEquals("XLR", model.getCableLengthProfiles().get(0).getName());
        assertEquals(List.of(10.0, 20.0), model.getCableLengthProfiles().get(0).getAvailableLengthsM());
    }

    @Test
    void librarySyncSkipsDeletedItemsWithoutTouchingLocalLibrary(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        var dto = new LibrarySyncClient.LibraryItemDto("srv-1", "CABINET", "Ghost", "{}", 5, true);

        AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(List.of(dto));

        assertEquals(0, summary.added());
        assertEquals(0, summary.updated());
        assertEquals(1, summary.skippedDeleted());
        assertTrue(model.getWorkspace().getCabinetTypes().isEmpty());
    }

    @Test
    void librarySyncAppliesInteractiveScenarios(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        var dto = new LibrarySyncClient.LibraryItemDto("global-interactive-scenarios", "INTERACTIVE_SCENARIOS",
                "Интерактивные сценарии",
                "{\"scenarios\":[{\"id\":\"s1\",\"title\":\"Первый запуск\",\"steps\":["
                        + "{\"title\":\"Шаг 1\",\"bodyHtml\":\"Нажмите сюда\",\"imageBase64\":\"AAA=\","
                        + "\"hotspotX\":0.1,\"hotspotY\":0.2,\"hotspotWidth\":0.3,\"hotspotHeight\":0.4},"
                        + "{\"title\":\"Шаг 2\",\"bodyHtml\":\"Готово\"}]}]}", 5, false);

        AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(List.of(dto));

        assertEquals(1, summary.added());
        List<com.vjstb.ledscheme.model.Scenario> scenarios = model.getWorkspace().getLibrary().getInteractiveScenarios();
        assertEquals(1, scenarios.size());
        assertEquals("Первый запуск", scenarios.get(0).getTitle());
        assertEquals(2, scenarios.get(0).getSteps().size());
        com.vjstb.ledscheme.model.ScenarioStep step1 = scenarios.get(0).getSteps().get(0);
        assertTrue(step1.hasHotspot());
        assertEquals(0.1, step1.getHotspotX());
        com.vjstb.ledscheme.model.ScenarioStep step2 = scenarios.get(0).getSteps().get(1);
        assertFalse(step2.hasHotspot(), "шаг без координат хотспота должен требовать кнопку «Далее»");
    }

    @Test
    void librarySyncIgnoresUnknownKindWithoutThrowing(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        var dto = new LibrarySyncClient.LibraryItemDto("srv-1", "FUTURE_KIND", "?", "{}", 5, false);

        AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(List.of(dto));

        assertEquals(0, summary.added());
        assertEquals(0, summary.updated());
        assertEquals(0, summary.skippedDeleted());
    }

    @Test
    void librarySyncMatchesSharedItemByNameWhenReuploadedUnderNewServerId(@TempDir Path dir) {
        // Регрессия на сценарий из комментария applyOne(): запись уже есть в общей
        // библиотеке (заведена предыдущим синком под id "srv-1"), затем та же запись
        // массово перезалита на сервер через админ-консоль под НОВЫМ id "srv-2" (ре-
        // импорт не сохраняет исходные id) — синк должен найти её по имени и обновить
        // на месте, а не завести второй экземпляр в общей библиотеке.
        AppModel model = freshModel(dir);
        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-1", "CABINET", "MG5", "{\"name\":\"MG5\"}", 5, false)));

        AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-2", "CABINET", "MG5", "{\"name\":\"MG5\"}", 6, false)));

        assertEquals(0, summary.added());
        assertEquals(1, summary.updated());
        assertEquals(1, model.getWorkspace().getSharedCabinetTypes().size(), "не должно быть дубликата в общей библиотеке");
        assertEquals("srv-2", model.getWorkspace().getSharedCabinetTypes().get(0).getId(),
                "запись должна принять новый id сервера, а не остаться под старым");
    }

    @Test
    void librarySyncNameMatchIsCaseInsensitive(@TempDir Path dir) {
        // Тот же приём подбора по имени, но с другим регистром — сверка идёт через
        // equalsIgnoreCase, иначе "MG5"/"mg5" считались бы разными записями и синк
        // завёл бы дубликат только из-за регистра.
        AppModel model = freshModel(dir);
        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-1", "CABINET", "MG5", "{\"name\":\"MG5\"}", 5, false)));

        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-2", "CABINET", "mg5", "{\"name\":\"mg5\"}", 6, false)));

        assertEquals(1, model.getWorkspace().getSharedCabinetTypes().size(),
                "регистронезависимое совпадение имени не должно создать дубликат");
    }

    @Test
    void librarySyncAddsHoistType(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        var dto = new LibrarySyncClient.LibraryItemDto("srv-hoist-1", "HOIST", "CM Lodestar 1t",
                "{\"name\":\"CM Lodestar 1t\",\"wllKg\":1000.0}", 5, false);

        AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(List.of(dto));

        assertEquals(1, summary.added());
        assertEquals(1, model.getHoistTypes().size());
        assertEquals("srv-hoist-1", model.getHoistTypes().get(0).getId());
        assertEquals("CM Lodestar 1t", model.getHoistTypes().get(0).getName());
        assertEquals(1000.0, model.getHoistTypes().get(0).getWllKg(), 1e-6);
    }

    @Test
    void librarySyncMigratesScreenHoistTypeReferenceWhenPersonalTypeIsPromoted(@TempDir Path dir) {
        // Тот же перенос ссылок, что и у CABINET/CONTROLLER (см. соседние тесты), но
        // для Screen.riggingHoistTypeId -- личная запись HoistType "продвигается"
        // под новым id, экран, уже на неё ссылающийся, не должен остаться с повисшим FK.
        AppModel model = freshModel(dir);
        com.vjstb.ledscheme.model.HoistType personal = new com.vjstb.ledscheme.model.HoistType();
        personal.setName("CM Lodestar 1t");
        personal = model.addHoistType(personal);
        String oldId = personal.getId();
        CabinetType cabinetType = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", cabinetType.getId(), 2, 3, 0, 0);
        screen.setRiggingHoistTypeId(oldId);

        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-hoist-1", "HOIST", "CM Lodestar 1t", "{\"name\":\"CM Lodestar 1t\",\"wllKg\":1000.0}", 5, false)));

        assertTrue(model.getWorkspace().getHoistTypes().isEmpty(), "личная запись должна быть продвинута (удалена)");
        assertEquals("srv-hoist-1", screen.getRiggingHoistTypeId(),
                "ссылка экрана должна перенестись на новый id общей записи, а не остаться повисшей на старом личном id");
    }

    @Test
    void librarySyncKeepsSharedHoistTypeDeletionIfScreenStillReferencesIt(@TempDir Path dir) {
        // Тот же защищённый путь удаления, что у CABINET (см.
        // librarySyncKeepsSharedCabinetTypeDeletionIfScreenStillReferencesIt) --
        // экран реально ссылается на этот HoistType через riggingHoistTypeId.
        AppModel model = freshModel(dir);
        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-hoist-1", "HOIST", "CM Lodestar 1t", "{\"name\":\"CM Lodestar 1t\",\"wllKg\":1000.0}", 5, false)));
        CabinetType cabinetType = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", cabinetType.getId(), 2, 3, 0, 0);
        screen.setRiggingHoistTypeId("srv-hoist-1");

        AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-hoist-1", "HOIST", "CM Lodestar 1t", "{\"name\":\"CM Lodestar 1t\"}", 6, true)));

        assertEquals(0, summary.deleted());
        assertEquals(1, summary.skippedDeleted());
        assertEquals(1, model.getWorkspace().getSharedHoistTypes().size(),
                "запись должна остаться — на неё ссылается riggingHoistTypeId реального экрана");
    }

    @Test
    void librarySyncAddsStructureFrameType(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        var dto = new LibrarySyncClient.LibraryItemDto("srv-frame-1", "STRUCTURE_FRAME", "Рама 950",
                "{\"name\":\"Рама 950\",\"kind\":\"FRAME\",\"heightMm\":950.0,\"widthMm\":500.0,"
                        + "\"depthMm\":51.0,\"weightKg\":12.0}", 5, false);

        AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(List.of(dto));

        assertEquals(1, summary.added());
        assertEquals(1, model.getStructureFrameTypes().size());
        assertEquals("srv-frame-1", model.getStructureFrameTypes().get(0).getId());
        assertEquals("Рама 950", model.getStructureFrameTypes().get(0).getName());
        assertEquals(com.vjstb.ledscheme.model.StructureFrameType.Kind.FRAME,
                model.getStructureFrameTypes().get(0).getKind());
        assertEquals(950.0, model.getStructureFrameTypes().get(0).getHeightMm(), 1e-6);
    }

    @Test
    void librarySyncMigratesScreenStructureFrameTypeReferenceWhenPersonalTypeIsPromoted(@TempDir Path dir) {
        // Тот же перенос ссылок, что и у HOIST/CABINET/CONTROLLER (см. соседние тесты), но
        // для Screen.structureFrameTypeId -- личная запись StructureFrameType "продвигается"
        // под новым id, экран, уже на неё ссылающийся, не должен остаться с повисшим FK.
        AppModel model = freshModel(dir);
        com.vjstb.ledscheme.model.StructureFrameType personal = new com.vjstb.ledscheme.model.StructureFrameType();
        personal.setName("Рама 950");
        personal.setKind(com.vjstb.ledscheme.model.StructureFrameType.Kind.FRAME);
        personal = model.addStructureFrameType(personal);
        String oldId = personal.getId();
        CabinetType cabinetType = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", cabinetType.getId(), 2, 3, 0, 0);
        screen.setStructureFrameTypeId(oldId);

        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-frame-1", "STRUCTURE_FRAME", "Рама 950",
                "{\"name\":\"Рама 950\",\"kind\":\"FRAME\",\"heightMm\":950.0}", 5, false)));

        assertTrue(model.getWorkspace().getStructureFrameTypes().isEmpty(),
                "личная запись должна быть продвинута (удалена)");
        assertEquals("srv-frame-1", screen.getStructureFrameTypeId(),
                "ссылка экрана должна перенестись на новый id общей записи, а не остаться повисшей на старом личном id");
    }

    @Test
    void librarySyncKeepsSharedStructureFrameTypeDeletionIfScreenStillReferencesIt(@TempDir Path dir) {
        // Тот же защищённый путь удаления, что у HOIST/CABINET -- экран реально
        // ссылается на этот StructureFrameType через одно из четырёх FK-полей
        // (здесь -- structureCupTypeId, чтобы покрыть не только "основное" поле).
        AppModel model = freshModel(dir);
        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-cup-1", "STRUCTURE_FRAME", "Стакан",
                "{\"name\":\"Стакан\",\"kind\":\"CUP\",\"weightKg\":0.3}", 5, false)));
        CabinetType cabinetType = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", cabinetType.getId(), 2, 3, 0, 0);
        screen.setStructureCupTypeId("srv-cup-1");

        AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-cup-1", "STRUCTURE_FRAME", "Стакан", "{\"name\":\"Стакан\",\"kind\":\"CUP\"}", 6, true)));

        assertEquals(0, summary.deleted());
        assertEquals(1, summary.skippedDeleted());
        assertEquals(1, model.getWorkspace().getSharedStructureFrameTypes().size(),
                "запись должна остаться — на неё ссылается structureCupTypeId реального экрана");
    }

    @Test
    void librarySyncMigratesScreenCabinetTypeReferenceWhenPersonalTypeIsPromoted(@TempDir Path dir) {
        // Регрессия на реальный баг, найденный на живых данных пользователя:
        // личный тип кабинета "продвигается" (удаляется из личной библиотеки),
        // когда та же запись приходит с сервера под новым id — но экран, уже
        // ссылающийся на СТАРЫЙ id личной записи, оставался с повисшей ссылкой
        // (мощность/вес считались как 0, экран пропадал из прерига). Синк должен
        // переносить такие ссылки на новый id общей записи.
        AppModel model = freshModel(dir);
        CabinetType personal = sampleType();
        personal.setName("MG5");
        personal = model.addCabinetType(personal);
        String oldId = personal.getId();
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", oldId, 2, 3, 0, 0);

        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-1", "CABINET", "MG5", "{\"name\":\"MG5\"}", 5, false)));

        assertTrue(model.getWorkspace().getCabinetTypes().isEmpty(), "личная запись должна быть продвинута (удалена)");
        assertEquals("srv-1", screen.getCabinetTypeId(),
                "ссылка экрана должна перенестись на новый id общей записи, а не остаться повисшей на старом личном id");
    }

    @Test
    void librarySyncMigratesControllerInstanceReferenceWhenPersonalTypeIsPromoted(@TempDir Path dir) {
        // Тот же перенос ссылок, но для типов контроллеров: ControllerInstance,
        // уже подключённый к экрану через личный тип, должен остаться рабочим
        // после того как тот же тип контроллера "продвигается" синком под новым
        // id общей библиотеки.
        AppModel model = freshModel(dir);
        CabinetType cabinetType = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", cabinetType.getId(), 2, 3, 0, 0);

        ControllerType personal = new ControllerType();
        personal.setName("MCTRL4k");
        personal.setPortCount(4);
        personal = model.addControllerType(personal);
        String oldId = personal.getId();
        ControllerInstance controller = model.addControllerToScreen(screen, oldId);

        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-ctrl-1", "CONTROLLER", "MCTRL4k", "{\"name\":\"MCTRL4k\",\"portCount\":4}", 5, false)));

        assertTrue(model.getWorkspace().getControllerTypes().isEmpty(), "личная запись должна быть продвинута (удалена)");
        assertEquals("srv-ctrl-1", controller.getControllerTypeId(),
                "ссылка контроллера должна перенестись на новый id общей записи, а не остаться повисшей на старом личном id");
    }

    @Test
    void getInterfaceTypesDedupesSharedAndPersonalByName(@TempDir Path dir) {
        // Регрессия на реальный баг: freshModel сеет личные дефолты (HDMI/
        // DisplayPort/... — см. seedDefaultInterfaceTypesIfEmpty), и если синк
        // приносит с сервера общую запись с ТЕМ ЖЕ именем, но её точное
        // совпадение по имени не сработало при "продвижении" (например, из-за
        // лишнего пробела на конце — на практике так и произошло у пользователя),
        // личная запись оставалась висеть — и getInterfaceTypes() показывал её
        // ДВАЖДЫ (общую + личную). Список должен схлопывать такие дубли по имени.
        AppModel model = freshModel(dir);
        long hdmiBefore = model.getInterfaceTypes().stream().filter(t -> t.getName().equalsIgnoreCase("HDMI")).count();
        assertEquals(1, hdmiBefore, "личный дефолт HDMI должен быть один");

        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-hdmi", "INTERFACE", "HDMI",
                "{\"name\":\"HDMI\",\"versions\":[\"1.4\",\"2.0\",\"2.1\"]}", 1, false)));

        List<InterfaceType> hdmiEntries = model.getInterfaceTypes().stream()
                .filter(t -> t.getName().equalsIgnoreCase("HDMI")).toList();
        assertEquals(1, hdmiEntries.size(), "после синка в списке должна остаться только одна запись HDMI, не две");
        assertEquals("srv-hdmi", hdmiEntries.get(0).getId(), "должна остаться общая (продвинутая) запись");
    }

    @Test
    void applyOnePromotionMatchesNameIgnoringSurroundingWhitespace(@TempDir Path dir) {
        // Раньше сверка имени при "продвижении" личной записи не обрезала
        // пробелы (equalsIgnoreCase без trim) — запись с лишним пробелом на
        // сервере ("HDMI ") не "продвигала" личную "HDMI", и обе оставались в
        // объединённом списке одновременно. Теперь сверка обрезает пробелы.
        AppModel model = freshModel(dir);
        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-hdmi", "INTERFACE", "HDMI ",
                "{\"name\":\"HDMI \",\"versions\":[\"1.4\",\"2.0\",\"2.1\"]}", 1, false)));

        boolean personalHdmiStillPresent = model.getWorkspace().getInterfaceTypes().stream()
                .anyMatch(t -> t.getName().trim().equalsIgnoreCase("HDMI"));
        assertFalse(personalHdmiStillPresent, "личная запись HDMI должна быть продвинута несмотря на пробел в имени с сервера");
    }

    @Test
    void deleteInterfaceTypeRemovesOnlyPersonalEntry(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        int before = model.getWorkspace().getInterfaceTypes().size();
        InterfaceType hdmi = model.getInterfaceTypes().stream()
                .filter(t -> t.getName().equalsIgnoreCase("HDMI")).findFirst().orElseThrow();

        model.deleteInterfaceType(hdmi.getId());

        assertEquals(before - 1, model.getWorkspace().getInterfaceTypes().size());
        assertTrue(model.getInterfaceTypes().stream().noneMatch(t -> t.getName().equalsIgnoreCase("HDMI")));
    }

    @Test
    void librarySyncRemovesUnreferencedSharedCabinetTypeWhenServerDeletesIt(@TempDir Path dir) {
        // Регрессия на реальный баг-репорт: тестовая запись ("ROE 7mm new"),
        // удалённая на сервере, годами не пропадала из общей библиотеки клиента
        // при повторных синках — deleted=true просто пропускался безусловно.
        // Если запись НИЧЕМ локально не используется, синк теперь должен убрать
        // её из общей библиотеки, а не хранить вечно.
        AppModel model = freshModel(dir);
        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-roe", "CABINET", "ROE 7mm new", "{\"name\":\"ROE 7mm new\"}", 5, false)));
        assertEquals(1, model.getWorkspace().getSharedCabinetTypes().size());

        AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-roe", "CABINET", "ROE 7mm new", "{\"name\":\"ROE 7mm new\"}", 6, true)));

        assertEquals(1, summary.deleted());
        assertEquals(0, summary.skippedDeleted());
        assertTrue(model.getWorkspace().getSharedCabinetTypes().isEmpty());
    }

    @Test
    void librarySyncKeepsSharedCabinetTypeDeletionIfScreenStillReferencesIt(@TempDir Path dir) {
        // То же удаление, но экран сцены реально использует этот тип кабинета —
        // синк не должен обрушивать его мощность/вес тихим фоновым шагом
        // (тот же класс бага, что чинили для "продвижения" — см.
        // librarySyncMigratesScreenCabinetTypeReferenceWhenPersonalTypeIsPromoted).
        AppModel model = freshModel(dir);
        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-roe", "CABINET", "ROE 7mm new", "{\"name\":\"ROE 7mm new\",\"widthMm\":600,\"heightMm\":600,"
                        + "\"resolutionWidth\":80,\"resolutionHeight\":80,\"powerConsumptionW\":250,\"weightKg\":10}",
                5, false)));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        model.addScreen("E", "srv-roe", 2, 3, 0, 0);

        AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-roe", "CABINET", "ROE 7mm new", "{\"name\":\"ROE 7mm new\"}", 6, true)));

        assertEquals(0, summary.deleted());
        assertEquals(1, summary.skippedDeleted());
        assertEquals(1, model.getWorkspace().getSharedCabinetTypes().size(),
                "запись должна остаться — на неё ссылается реальный экран");
    }

    @Test
    void librarySyncAlwaysRemovesDeletedInterfaceTypeRegardlessOfUsage(@TempDir Path dir) {
        // Виды интерфейса нигде в workspace по id не хранятся (в отличие от
        // CABINET/CONTROLLER) — для них удаление безусловно безопасно.
        AppModel model = freshModel(dir);
        model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-hdmi2", "INTERFACE", "HDMI 2.2", "{\"name\":\"HDMI 2.2\"}", 5, false)));
        assertEquals(1, model.getWorkspace().getSharedInterfaceTypes().size());

        AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                "srv-hdmi2", "INTERFACE", "HDMI 2.2", "{\"name\":\"HDMI 2.2\"}", 6, true)));

        assertEquals(1, summary.deleted());
        assertTrue(model.getWorkspace().getSharedInterfaceTypes().isEmpty());
    }

    @Test
    void chainEndpointSocketCabinetIdsCoversPowerEntryAndSignalMainPlusBackupEntries(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 3, 3, 0, 0);
        model.selectScreen(screen);
        model.updateSignalPortCount(screen, 8);
        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();

        // Силовая цепочка: только вводной (первый) кабинет — гнездо.
        model.addPowerChain(1, List.of(ids.get(0), ids.get(1)));
        // Сигнал без резерва: только вводной кабинет основной цепочки — гнездо.
        model.addSignalChain(2, false, List.of(ids.get(2), ids.get(3)));

        // Режимы НЕ смешиваются (баг-репорт: на общей схеме питания появлялось лишнее
        // гнездо от сигнальной цепочки того же экрана, и наоборот) — схема питания
        // видит ТОЛЬКО силовой вводной кабинет, схема сигнала — ТОЛЬКО сигнальный.
        var power = model.chainEndpointSocketCabinetIds(SchemaMode.POWER, screen);
        assertEquals(java.util.Set.of(ids.get(0)), power);
        var signal = model.chainEndpointSocketCabinetIds(SchemaMode.SIGNAL, screen);
        assertEquals(java.util.Set.of(ids.get(2)), signal);

        // Порт 2 получает резерв — порт 5. Резерв НЕ заводит отдельную цепочку: это
        // тот же физический прогон, просто резервный кабель заходит с ПРОТИВОПОЛОЖНОГО
        // конца (см. SchemeRenderer.signalChainEndLabel — последний кабинет цепочки уже
        // подписывается номером резервного порта, без каких-либо отдельных данных).
        // Поэтому гнездом должен стать ПОСЛЕДНИЙ кабинет ТОЙ ЖЕ цепочки (ids.get(3)),
        // а не первый кабинет отдельно расключённого порта 5.
        model.setSignalBackupPortLink(2, 5);
        var signalWithBackup = model.chainEndpointSocketCabinetIds(SchemaMode.SIGNAL, screen);
        assertEquals(java.util.Set.of(ids.get(2), ids.get(3)), signalWithBackup,
                "Последний кабинет цепочки порта 2 должен стать гнездом резерва, без отдельной проводки");
        assertEquals(power, model.chainEndpointSocketCabinetIds(SchemaMode.POWER, screen),
                "Назначение сигнального резерва не должно влиять на гнёзда питания");
    }

    @Test
    void autoPopulateSchemaAddsOnlyWiredScreensAndUsedControllersWithoutDuplicating(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen wired = model.addScreen("Wired", type.getId(), 2, 2, 0, 0);
        Screen unwired = model.addScreen("Unwired", type.getId(), 2, 2, 1000, 0);
        model.selectScreen(wired);

        ControllerType used = new ControllerType();
        used.setName("Used");
        used.setPortCount(4);
        used = model.addControllerType(used);
        ControllerInstance usedCi = model.addControllerToScreen(wired, used.getId());

        ControllerType unusedType = new ControllerType();
        unusedType.setName("Unused");
        unusedType.setPortCount(4);
        unusedType = model.addControllerType(unusedType);
        model.addControllerToScreen(wired, unusedType.getId());

        List<String> ids = wired.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addSignalChain(1, false, List.of(ids.get(0)));

        model.autoPopulateSchema(SchemaMode.SIGNAL, false);

        List<SchemaNode> nodes = model.schemaNodesForCurrentScene(SchemaMode.SIGNAL);
        assertTrue(nodes.stream().anyMatch(n -> n.getType() == SchemaNodeType.SCREEN
                && wired.getId().equals(n.getScreenRefId())), "Расключенный экран должен появиться в схеме");
        assertTrue(nodes.stream().noneMatch(n -> n.getType() == SchemaNodeType.SCREEN
                        && unwired.getId().equals(n.getScreenRefId())),
                "Нерасключенный экран не должен появиться");
        assertTrue(nodes.stream().anyMatch(n -> n.getType() == SchemaNodeType.CONTROLLER
                && usedCi.getId().equals(n.getControllerInstanceRefId())),
                "Использованный (с прописанным портом) контроллер должен появиться");
        assertEquals(1, nodes.stream().filter(n -> n.getType() == SchemaNodeType.CONTROLLER).count(),
                "Неиспользованный контроллер добавляться не должен");

        // Ручное добавление узла того же экрана до автозаполнения — повторный вызов
        // не должен плодить дубли ни для него, ни для уже добавленного контроллера.
        model.autoPopulateSchema(SchemaMode.SIGNAL, false);
        assertEquals(2, model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).size(),
                "Повторный вызов не должен создавать дублирующиеся узлы");
    }

    @Test
    void autoPopulateSchemaConnectsCabinetSocketsToMatchingControllerPortGroupWhenRequested(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 3, 3, 0, 0);
        model.selectScreen(screen);

        // Модульный контроллер из 2 карт (4 + 2 порта) — резерв порта 2 (карта 1)
        // назначен на порт 5 (карта 2), проверяет попадание в РАЗНЫЕ группы портов.
        ControllerType h = new ControllerType();
        h.setName("H2");
        h.getCards().add(new SchemaCard("Карта 1", List.of(new CardPort("RJ45", PortDirection.OUT, 4))));
        h.getCards().add(new SchemaCard("Карта 2", List.of(new CardPort("RJ45", PortDirection.OUT, 2))));
        h = model.addControllerType(h);
        model.addControllerToScreen(screen, h.getId());

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addSignalChain(2, false, List.of(ids.get(0), ids.get(1)));
        model.setSignalBackupPortLink(2, 5);

        model.autoPopulateSchema(SchemaMode.SIGNAL, true);

        List<SchemaEdge> edges = model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL);
        assertEquals(2, edges.size(), "По одной связи на основной вход и на резервный (другая карта)");

        SchemaNode controllerNode = model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).stream()
                .filter(n -> n.getType() == SchemaNodeType.CONTROLLER).findFirst().orElseThrow();
        String card1PortId = controllerNode.getCards().get(0).getPorts().get(0).getId();
        String card2PortId = controllerNode.getCards().get(1).getPorts().get(0).getId();

        assertTrue(edges.stream().anyMatch(e -> ids.get(0).equals(e.getFromCabinetInstanceId())
                        && card1PortId.equals(e.getToPortId())),
                "Вводной кабинет основного порта 2 должен соединиться с картой 1");
        assertTrue(edges.stream().anyMatch(e -> ids.get(1).equals(e.getFromCabinetInstanceId())
                        && card2PortId.equals(e.getToPortId())),
                "Последний кабинет цепочки (резерв, порт 5) должен соединиться с картой 2");

        // Повторный вызов — без дублей связей.
        model.autoPopulateSchema(SchemaMode.SIGNAL, true);
        assertEquals(2, model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL).size());
    }

    @Test
    void autoPopulateSchemaForPowerAddsOnlyScreensNeverControllers(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);

        ControllerType ct = new ControllerType();
        ct.setName("Any");
        ct.setPortCount(4);
        ct = model.addControllerType(ct);
        model.addControllerToScreen(screen, ct.getId());

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addPowerChain(1, List.of(ids.get(0)));

        // Ни одного узла «Распределение» на схеме ещё нет — автосвязи заводить некуда,
        // у питания нет портов контроллера в принципе (см. addSchemaNodeForController).
        model.autoPopulateSchema(SchemaMode.POWER, true);

        List<SchemaNode> nodes = model.schemaNodesForCurrentScene(SchemaMode.POWER);
        assertEquals(1, nodes.size());
        assertEquals(SchemaNodeType.SCREEN, nodes.get(0).getType());
        assertTrue(model.schemaEdgesForCurrentScene(SchemaMode.POWER).isEmpty());
    }

    @Test
    void autoPopulateSchemaForPowerFillsExistingDistroConnectorsInOrderWithoutCreatingNewNodes(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);

        // Первая проходная — 1 свободный разъём, вторая — 2 (заполняются по очереди,
        // ПЕРВАЯ — до упора, прежде чем переходить ко второй).
        SchemaNode distro1 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Проходная 1", 0, 0, null);
        CardPort d1p1 = model.addPowerConnectorToNode(distro1, "CEE 16A", PortDirection.OUT, 1);
        SchemaNode distro2 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Проходная 2", 300, 0, null);
        CardPort d2p1 = model.addPowerConnectorToNode(distro2, "CEE 16A", PortDirection.OUT, 1);
        CardPort d2p2 = model.addPowerConnectorToNode(distro2, "CEE 16A", PortDirection.OUT, 1);

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addPowerChain(1, List.of(ids.get(0)));
        model.addPowerChain(2, List.of(ids.get(1)));
        model.addPowerChain(3, List.of(ids.get(2)));

        model.autoPopulateSchema(SchemaMode.POWER, true);

        List<SchemaEdge> edges = model.schemaEdgesForCurrentScene(SchemaMode.POWER);
        assertEquals(3, edges.size());
        assertTrue(edges.stream().anyMatch(e -> ids.get(0).equals(e.getFromCabinetInstanceId())
                        && d1p1.getId().equals(e.getToPortId())),
                "Первый вводной кабинет занимает единственный разъём первой проходной");
        assertTrue(edges.stream().anyMatch(e -> ids.get(1).equals(e.getFromCabinetInstanceId())
                        && d2p1.getId().equals(e.getToPortId())),
                "Проходная 1 уже занята — второй кабинет уходит на первый разъём проходной 2");
        assertTrue(edges.stream().anyMatch(e -> ids.get(2).equals(e.getFromCabinetInstanceId())
                && d2p2.getId().equals(e.getToPortId())));
        assertTrue(model.schemaNodesForCurrentScene(SchemaMode.POWER).stream()
                .noneMatch(n -> n.getType() == SchemaNodeType.CONTROLLER));

        // Повторный вызов — без дублей связей, новые проходные не создаются.
        model.autoPopulateSchema(SchemaMode.POWER, true);
        assertEquals(3, model.schemaEdgesForCurrentScene(SchemaMode.POWER).size());
        assertEquals(2, model.schemaNodesForCurrentScene(SchemaMode.POWER).stream()
                .filter(n -> n.getType() == SchemaNodeType.DISTRO).count());
    }

    @Test
    void autoPopulateSchemaForPowerLeavesEntryUnconnectedWhenDistroCapacityExhausted(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);

        SchemaNode distro = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Проходная", 0, 0, null);
        model.addPowerConnectorToNode(distro, "CEE 16A", PortDirection.OUT, 1);

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addPowerChain(1, List.of(ids.get(0)));
        model.addPowerChain(2, List.of(ids.get(1)));

        model.autoPopulateSchema(SchemaMode.POWER, true);

        assertEquals(1, model.schemaEdgesForCurrentScene(SchemaMode.POWER).size(),
                "Разъёмов на всех проходных не хватает на обе цепочки — вторая остаётся неподключённой,"
                        + " новый узел не создаётся");
    }

    @Test
    void autoPopulateSchemaForPowerSkipsMismatchedSpareConnectorAndMovesToNextDistro(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);

        // Проходная 1: основная группа CEE16A×2 + одиночный запасной транзитный
        // CEE32A другого номинала — не должен использоваться автозаполнением.
        SchemaNode distro1 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Проходная 1", 0, 0, null);
        CardPort d1main = model.addPowerConnectorToNode(distro1, "CEE 16A", PortDirection.OUT, 2);
        CardPort d1spare = model.addPowerConnectorToNode(distro1, "CEE 32A", PortDirection.OUT, 1);
        SchemaNode distro2 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Проходная 2", 300, 0, null);
        CardPort d2main = model.addPowerConnectorToNode(distro2, "CEE 16A", PortDirection.OUT, 2);

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addPowerChain(1, List.of(ids.get(0)));
        model.addPowerChain(2, List.of(ids.get(1)));
        model.addPowerChain(3, List.of(ids.get(2)));

        model.autoPopulateSchema(SchemaMode.POWER, true);

        List<SchemaEdge> edges = model.schemaEdgesForCurrentScene(SchemaMode.POWER);
        assertEquals(3, edges.size());
        assertTrue(edges.stream().noneMatch(e -> d1spare.getId().equals(e.getToPortId())),
                "Запасной разъём другого номинала (CEE 32A) не должен использоваться автозаполнением");
        assertTrue(edges.stream().filter(e -> d1main.getId().equals(e.getToPortId())).count() == 2,
                "Основная группа первой проходной заполняется полностью (2 из 2)");
        assertTrue(edges.stream().anyMatch(e -> ids.get(2).equals(e.getFromCabinetInstanceId())
                        && d2main.getId().equals(e.getToPortId())),
                "Третий кабинет уходит на основную группу СЛЕДУЮЩЕЙ проходной, а не на несовпадающий"
                        + " запасной разъём первой");
    }

    @Test
    void autoPopulateSchemaOnlyAutoConnectsScreenAddedInThisCall(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);

        ControllerType ct = new ControllerType();
        ct.setName("H2");
        ct.setPortCount(4);
        ct = model.addControllerType(ct);
        model.addControllerToScreen(screen, ct.getId());

        // Узел экрана уже добавлен на схему ВРУЧНУЮ (или предыдущим импортом) ДО
        // того, как для него провели хоть одну цепочку, — т.е. не будет "свежим"
        // на момент вызова autoPopulateSchema.
        model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SCREEN, screen.getName(), 0, 0, screen.getId());

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addSignalChain(1, false, List.of(ids.get(0)));

        model.autoPopulateSchema(SchemaMode.SIGNAL, true);

        assertTrue(model.schemaNodesForCurrentScene(SchemaMode.SIGNAL).stream()
                        .anyMatch(n -> n.getType() == SchemaNodeType.CONTROLLER),
                "Использованный контроллер всё равно добавляется как узел");
        assertTrue(model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL).isEmpty(),
                "Узел экрана НЕ был добавлен ЭТИМ вызовом — автосвязь для его кабинетов не проводится,"
                        + " даже хотя у него есть непрописанный (несвязанный) кабинет");
    }

    @Test
    void autoPopulateSchemaDoesNotReconnectManuallyRemovedEdgeOnSubsequentCall(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);

        ControllerType ct = new ControllerType();
        ct.setName("H2");
        ct.setPortCount(4);
        ct = model.addControllerType(ct);
        model.addControllerToScreen(screen, ct.getId());

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addSignalChain(1, false, List.of(ids.get(0)));

        model.autoPopulateSchema(SchemaMode.SIGNAL, true);
        assertEquals(1, model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL).size());

        // Инженер сознательно разрывает автосвязь (например, кабель физически ушёл
        // в другое место) — порт должен остаться свободным навсегда, а не
        // перезаполняться заново при каждом следующем переходе на общую схему.
        model.deleteSchemaEdge(model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL).get(0));
        assertTrue(model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL).isEmpty());

        model.autoPopulateSchema(SchemaMode.SIGNAL, true);
        assertTrue(model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL).isEmpty(),
                "Узел экрана уже существовал до этого вызова — разорванная связь не должна восстановиться");
    }

    private static long visibleFrames(Screen s) {
        return s.getStructureFrameCells().stream().filter(c -> !c.isHidden()).count();
    }

    @Test
    void updateScreenStructureSeedsFullUniformGrid(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 6, 0, 0);

        // towerCount=2, verticalFramesPerTower=3 (в КАЖДОМ из 2 рядов), peremychkaLevels=2,
        // extendedBaseSections=0 (только ядро).
        model.updateScreenStructure(screen, 3000, 1000, 2, 3, 3, 2, 0, null, null, null, null, 0, null);

        assertEquals(12, screen.getStructureFrameCells().size(), "2 башни x 2 ряда x 3 сегмента");
        assertTrue(screen.getStructureFrameCells().stream().noneMatch(com.vjstb.ledscheme.model.StructureFrameCell::isHidden),
                "свежесгенерированная сетка целиком видима");
        assertEquals(4, screen.getStructurePeremychkaCells().size(), "2 башни x 2 уровня перемычек");
        assertEquals(2, screen.getStructureBaseFrameCells().size(), "2 башни x 1 секция (ядро)");
        assertTrue(screen.getStructureBaseFrameCells().stream()
                .noneMatch(com.vjstb.ledscheme.model.StructureBaseFrameCell::isHidden));
    }

    @Test
    void toggleStructureFrameCellHidesThenUnhidesTheSameCell(@TempDir Path dir) {
        // Клик по существующей ячейке ПРЯЧЕТ её (hidden=true), а не удаляет из списка --
        // иначе следующий «Рассчитать конструктив» не смог бы отличить «эту ячейку убрали
        // руками» от «она никогда не считалась» и молча вернул бы её (см.
        // recalculatingStructurePreservesManualRemovalsWithinNewBounds ниже).
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 6, 0, 0);
        model.updateScreenStructure(screen, 3000, 1000, 2, 3, 3, 2, 0, null, null, null, null, 0, null);

        model.toggleStructureFrameCell(screen, 0, 0, 1);
        assertEquals(12, screen.getStructureFrameCells().size(), "запись остаётся, просто прячется");
        assertEquals(11, visibleFrames(screen));
        assertTrue(screen.getStructureFrameCells().stream().anyMatch(c -> c.matches(0, 0, 1) && c.isHidden()));

        model.toggleStructureFrameCell(screen, 0, 0, 1);
        assertEquals(12, visibleFrames(screen), "повторный клик по тому же месту должен вернуть видимость");
    }

    @Test
    void togglePeremychkaCellAndBaseFrameSectionToggleIndependently(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 6, 0, 0);
        model.updateScreenStructure(screen, 3000, 1000, 2, 3, 3, 2, 0, null, null, null, null, 0, null);

        model.toggleStructurePeremychkaCell(screen, 0, 1);
        assertEquals(3, screen.getStructurePeremychkaCells().stream().filter(c -> !c.isHidden()).count(),
                "4 всего - 1 спрятанная");

        model.toggleStructureBaseFrameSection(screen, 1, 0);
        assertTrue(screen.getStructureBaseFrameCells().stream().anyMatch(c -> c.matches(1, 0) && c.isHidden()));
        assertTrue(screen.getStructureBaseFrameCells().stream().anyMatch(c -> c.matches(0, 0) && !c.isHidden()),
                "башня 0 не должна была пострадать");
    }

    @Test
    void recalculatingStructurePreservesManualRemovalsWithinNewBounds(@TempDir Path dir) {
        // Баг-репорт (сценарий из ScreenLogic.resizeGrid для кабинетов, тот же принцип для
        // конструктива): «Рассчитать конструктив» после мелкой правки (например, высоты
        // башни) не должен молча возвращать вручную убранные пользователем сегменты.
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 6, 0, 0);
        model.updateScreenStructure(screen, 3000, 1000, 3, 3, 3, 3, 0, null, null, null, null, 0, null);

        // Убрали средний сегмент ПЕРЕДНЕГО ряда башни 1.
        model.toggleStructureFrameCell(screen, 1, 0, 1);

        // Те же границы сетки (3 башни x 3 сегмента) -- пересчёт НЕ должен вернуть убранное.
        model.updateScreenStructure(screen, 3200, 1000, 3, 3, 3, 3, 0, null, null, null, null, 0, null);
        assertEquals(17, visibleFrames(screen), "18 (3 башни x 2 ряда x 3 сегмента) - 1 убранный");
        assertTrue(screen.getStructureFrameCells().stream().anyMatch(c -> c.matches(1, 0, 1) && c.isHidden()),
                "убранный сегмент не должен молча вернуться видимым при пересчёте в тех же границах");

        // Уменьшили сетку, но убранная ячейка (1,0,1) ВСЁ РАВНО внутри новых границ
        // (1<2 башня, 1<2 сегмент) -- остаётся скрытой как есть, а не воскресает только
        // потому что сетка сжалась.
        model.updateScreenStructure(screen, 3200, 1000, 2, 2, 2, 2, 0, null, null, null, null, 0, null);
        assertEquals(8, screen.getStructureFrameCells().size(),
                "2 башни x 2 ряда x 2 сегмента, включая скрытую (1,0,1)");
        assertEquals(7, visibleFrames(screen));
        assertTrue(screen.getStructureFrameCells().stream().anyMatch(c -> c.matches(1, 0, 1) && c.isHidden()));
    }

    @Test
    void toggleStructureFrameCellWithNewCellTypeOverrideAppliesOnlyOnCreation(@TempDir Path dir) {
        // Phase 2.2: "менюшка с выбором текущей рамы для построения" -- при СОЗДАНИИ новой
        // ячейки (позиция ещё не встречалась) можно сразу задать переопределение типа рамы
        // (например, короткая рама на краю), но повторное переключение УЖЕ существующей
        // записи (hidden туда-обратно) тип не меняет.
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 6, 0, 0);
        model.updateScreenStructure(screen, 3000, 1000, 2, 3, 3, 0, 0, null, null, null, null, 0, null);

        // Позиция (5, 0, 0) вне номинальной сетки (только 2 башни, индексы 0/1) -- создаётся
        // новая запись с переопределением типа.
        model.toggleStructureFrameCell(screen, 5, 0, 0, "short-frame-id");
        var created = screen.getStructureFrameCells().stream().filter(c -> c.matches(5, 0, 0)).findFirst()
                .orElseThrow();
        assertEquals("short-frame-id", created.getFrameTypeId());

        // Повторный клик по ТОЙ ЖЕ позиции прячет её -- тип не трогается никаким параметром.
        model.toggleStructureFrameCell(screen, 5, 0, 0, "ignored-id");
        assertTrue(created.isHidden());
        assertEquals("short-frame-id", created.getFrameTypeId(), "тип существующей ячейки не переопределяется");
    }

    @Test
    void toggleStructureFrameCellSupportsRow2ReinforcementInExtensionSection(@TempDir Path dir) {
        // Phase 2.2: row == 2 -- усилительная рама выноса, segmentIndex переиспользуется как
        // номер секции выноса (см. StructureFrameCell class-javadoc), toggle работает так же,
        // как для рядов 0/1.
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(sampleType());
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen screen = model.addScreen("E", type.getId(), 2, 6, 0, 0);
        // 1 доп. секция выноса -> regenerateStructureCells уже посеял 1 усилительную раму на
        // башню в секции 1 (см. StructureCalcTest.reinforcementFramesInExtensionSectionsCounts...).
        model.updateScreenStructure(screen, 3000, 1000, 2, 3, 3, 0, 1, null, null, null, null, 0, null);

        assertTrue(screen.getStructureFrameCells().stream().anyMatch(c -> c.matches(0, 2, 1) && !c.isHidden()),
                "усилительная рама башни 0 в секции 1 сгенерирована и видима");

        model.toggleStructureFrameCell(screen, 0, 2, 1);
        assertTrue(screen.getStructureFrameCells().stream().anyMatch(c -> c.matches(0, 2, 1) && c.isHidden()),
                "клик по существующей усилительной раме прячет её");
    }
}
