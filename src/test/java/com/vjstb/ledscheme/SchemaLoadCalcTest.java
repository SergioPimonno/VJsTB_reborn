package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.PowerCalc;
import com.vjstb.ledscheme.service.SchemaLoadCalc;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Тесты сквозного расчёта нагрузки общей схемы питания (Task #87): узел (щит/
 *  дистрибьютор) сравнивает нагрузку, уходящую через его исходящие связи, с
 *  ёмкостью его входных разъёмов — включая рекурсию через промежуточные узлы. */
class SchemaLoadCalcTest {

    /** PowerCalc.Defaults теперь mutable static state (Task #135/v2.0, синк
     *  CALC_DEFAULTS) — сбрасываем к зашитым значениям перед каждым тестом, чтобы
     *  тесты, пиннящие "текущий дефолт по умолчанию", не текли от других тестов,
     *  трогающих Defaults.apply(...). */
    @BeforeEach
    void resetCalcDefaults() {
        PowerCalc.Defaults.reset();
    }

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    private CabinetType sampleType(AppModel model) {
        CabinetType ct = new CabinetType();
        ct.setName("Test");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setPowerConsumptionW(150);
        ct.setWeightKg(12);
        return model.addCabinetType(ct);
    }

    @Test
    void detectsOverloadAgainstDefaultDeratingSinglePhase(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = sampleType(model); // 150 Вт/каб.
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        // 6×5 = 30 кабинетов × 150 Вт = 4500 Вт суммарной нагрузки экрана
        Screen screen = model.addScreen("E", type.getId(), 6, 5, 0, 0);

        SchemaNode screenNode = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, "E", 0, 0, screen.getId());
        // SOURCE ("Источник/щит"), а не DISTRO — у DISTRO запас по умолчанию теперь
        // 100% (см. тест distroTypeDefaultsToFullCapacityWithNoDerating ниже), этот
        // тест специально проверяет ОБЩИЙ запас по умолчанию (~92.6%).
        SchemaNode source = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "Щит", 0, 200, null);
        // 16А × 220В × ~92.6% запаса по умолчанию ≈ 3260 Вт ёмкости одной фазы
        model.addPowerConnectorToNode(source, "CEE 16A", PortDirection.IN, 1, 1, null);
        model.addSchemaEdge(SchemaMode.POWER, source.getId(), screenNode.getId(), "CEE 16A → щит");

        SchemaLoadCalc.NodeLoad load = SchemaLoadCalc.evaluate(source, scene, model);
        assertTrue(load.capacityKnown());
        assertTrue(load.overloaded(), "4500 Вт нагрузки не должно помещаться в ~3260 Вт ёмкости одной фазы CEE 16A");
    }

    @Test
    void distroTypeDefaultsToFullCapacityWithNoDerating(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = sampleType(model); // 150 Вт/каб.
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        // 4×5 = 20 каб. × 150 = 3000 Вт — превышает ~92.6% (3260 Вт для CEE 16A), но
        // не превышает 100% (3520 Вт) — различает, какой из двух дефолтов реально применился.
        Screen screen = model.addScreen("E", type.getId(), 4, 5, 0, 0);
        SchemaNode screenNode = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, "E", 0, 0, screen.getId());
        SchemaNode distro = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Проходная", 0, 200, null);
        model.addPowerConnectorToNode(distro, "CEE 16A", PortDirection.IN, 1, 1, null); // без ручного переопределения запаса
        model.addSchemaEdge(SchemaMode.POWER, distro.getId(), screenNode.getId(), "CEE 16A → проходная");

        SchemaLoadCalc.NodeLoad load = SchemaLoadCalc.evaluate(distro, scene, model);
        assertTrue(load.capacityKnown());
        assertFalse(load.overloaded(),
                "Проходная (DISTRO) должна по умолчанию считаться на 100% (3520 Вт), а не ~92.6% (3260 Вт)");
    }

    @Test
    void phaseCountAndZeroDeratingRaiseCapacityEnoughToAvoidOverload(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = sampleType(model); // 150 Вт/каб.
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 2, 5, 0, 0); // 10 каб. × 150 = 1500 Вт

        SchemaNode screenNode = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, "E", 0, 0, screen.getId());
        SchemaNode distro = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Щит", 0, 200, null);
        model.addPowerConnectorToNode(distro, "CEE 32A", PortDirection.IN, 1, 3, null); // 3 фазы
        model.setSchemaNodeLoadDeratingPercent(distro, 100.0); // без запаса: 3×32×220 = 21120 Вт
        model.addSchemaEdge(SchemaMode.POWER, distro.getId(), screenNode.getId(), "3×CEE 32A → щит");

        SchemaLoadCalc.NodeLoad load = SchemaLoadCalc.evaluate(distro, scene, model);
        assertTrue(load.capacityKnown());
        assertFalse(load.overloaded(), "1500 Вт должно свободно помещаться в 21120 Вт ёмкости трёхфазного CEE 32A без запаса");
    }

    @Test
    void screenFedByTwoProxiesSplitsLoadByLineCountNotFullTotal(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = sampleType(model); // 150 Вт/каб.
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        // 6×5 = 30 каб. × 150 = 4500 Вт суммарной нагрузки экрана, запитанного СРАЗУ
        // с двух разных проходных (по разным линиям/фазам) — раньше каждая из них
        // получала ВСЮ нагрузку экрана целиком, из-за чего обе ложно "перегружались".
        Screen screen = model.addScreen("E", type.getId(), 6, 5, 0, 0);
        SchemaNode screenNode = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, "E", 0, 0, screen.getId());

        SchemaNode proxy1 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Proxy1", 0, 200, null);
        model.addPowerConnectorToNode(proxy1, "CEE 63A", PortDirection.IN, 1, 1, null); // огромная ёмкость сама по себе
        model.setSchemaNodeLoadDeratingPercent(proxy1, 100.0); // 63×220 = 13860 Вт — заведомо хватит на половину
        SchemaEdge edge1 = model.addSchemaEdge(SchemaMode.POWER, proxy1.getId(), screenNode.getId(), "1×CEE 16A");
        edge1.setWireCount(1);

        SchemaNode proxy2 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Proxy2", 0, 400, null);
        model.addPowerConnectorToNode(proxy2, "CEE 63A", PortDirection.IN, 1, 1, null);
        model.setSchemaNodeLoadDeratingPercent(proxy2, 100.0);
        SchemaEdge edge2 = model.addSchemaEdge(SchemaMode.POWER, proxy2.getId(), screenNode.getId(), "1×CEE 16A");
        edge2.setWireCount(1);

        SchemaLoadCalc.NodeLoad load1 = SchemaLoadCalc.evaluate(proxy1, scene, model);
        SchemaLoadCalc.NodeLoad load2 = SchemaLoadCalc.evaluate(proxy2, scene, model);

        assertTrue(Math.abs(load1.loadWatts() - 2250.0) < 0.001,
                "Proxy1 должна получить лишь половину (по числу линий), получено: " + load1.loadWatts());
        assertTrue(Math.abs(load2.loadWatts() - 2250.0) < 0.001,
                "Proxy2 должна получить лишь половину (по числу линий), получено: " + load2.loadWatts());
        assertFalse(load1.overloaded(), "Ни одна проходная не должна ложно перегружаться половиной нагрузки экрана");
        assertFalse(load2.overloaded(), "Ни одна проходная не должна ложно перегружаться половиной нагрузки экрана");
    }

    @Test
    void understatedWireCountOnOneEdgeDoesNotInflateShareOfAnotherEdge(@TempDir Path dir) {
        // Баг-репорт: "6 вводных показывают общую нагрузку в 22,4кВт, в то время как
        // в окне расключения 6 вводных в сумме дают 20,16кВт (правильное значение)" —
        // экран реально запитан 4 цепочками (2 каб. × 150 Вт = 300 Вт каждая, итого
        // 1200 Вт на 8 кабинетов экрана), но связи в общей схеме промаркированы
        // "1×" и "2×" (сумма меток = 3, а НЕ 4 — типичная забытая правка после
        // добавления ещё одной цепочки через ту же проходную). Связь "2×" должна
        // получить РОВНО половину реальной нагрузки (2 цепочки из 4), 600 Вт — а НЕ
        // 2/3 от общей (800 Вт), как считал бы старый код по сумме меток.
        AppModel model = freshModel(dir);
        CabinetType type = sampleType(model); // 150 Вт/каб.
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 2, 4, 0, 0); // 8 каб. × 150 = 1200 Вт
        model.selectScreen(screen); // addPowerChain требует currentScreen
        SchemaNode screenNode = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, "E", 0, 0, screen.getId());

        java.util.List<String> ids = screen.getCabinets().stream().map(c -> c.getId()).toList();
        model.addPowerChain(1, ids.subList(0, 2));
        model.addPowerChain(2, ids.subList(2, 4));
        model.addPowerChain(3, ids.subList(4, 6));
        model.addPowerChain(1, ids.subList(6, 8)); // 4 реальные цепочки, а не 3

        SchemaNode proxy1 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Proxy1", 0, 200, null);
        model.addPowerConnectorToNode(proxy1, "CEE 63A", PortDirection.IN, 1, 1, null);
        model.setSchemaNodeLoadDeratingPercent(proxy1, 100.0); // заведомо не перегружен
        SchemaEdge edge1 = model.addSchemaEdge(SchemaMode.POWER, proxy1.getId(), screenNode.getId(), "1×CEE 16A");
        edge1.setWireCount(1);

        SchemaNode proxy2 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Proxy2", 0, 400, null);
        model.addPowerConnectorToNode(proxy2, "CEE 63A", PortDirection.IN, 1, 1, null);
        model.setSchemaNodeLoadDeratingPercent(proxy2, 100.0);
        SchemaEdge edge2 = model.addSchemaEdge(SchemaMode.POWER, proxy2.getId(), screenNode.getId(), "2×CEE 16A");
        edge2.setWireCount(2); // помечено 2 линии из "3" (1+2), реально -- 2 из 4

        SchemaLoadCalc.NodeLoad load2 = SchemaLoadCalc.evaluate(proxy2, scene, model);
        assertTrue(Math.abs(load2.loadWatts() - 600.0) < 0.001,
                "2 цепочки из 4 реальных = половина 1200 Вт = 600 Вт, получено: " + load2.loadWatts());
    }

    @Test
    void sumsLoadRecursivelyThroughIntermediateNode(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = sampleType(model); // 150 Вт/каб.
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screenA = model.addScreen("A", type.getId(), 1, 2, 0, 0); // 300 Вт
        Screen screenB = model.addScreen("B", type.getId(), 1, 2, 1000, 0); // 300 Вт

        SchemaNode nodeA = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, "A", 0, 0, screenA.getId());
        SchemaNode nodeB = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, "B", 200, 0, screenB.getId());
        SchemaNode subDistro = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Разветвитель", 0, 200, null);
        SchemaNode mainDistro = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Щит", 0, 400, null);

        model.addPowerConnectorToNode(mainDistro, "CEE 63A", PortDirection.IN, 1, 1, null);
        model.setSchemaNodeLoadDeratingPercent(mainDistro, 100.0); // 63×220 = 13860 Вт — заведомо не перегружен

        model.addSchemaEdge(SchemaMode.POWER, subDistro.getId(), nodeA.getId(), "к A");
        model.addSchemaEdge(SchemaMode.POWER, subDistro.getId(), nodeB.getId(), "к B");
        model.addSchemaEdge(SchemaMode.POWER, mainDistro.getId(), subDistro.getId(), "к разветвителю");

        SchemaLoadCalc.NodeLoad load = SchemaLoadCalc.evaluate(mainDistro, scene, model);
        assertFalse(load.overloaded());
        // 300 + 300 = 600 Вт должно дойти до главного щита через промежуточный узел
        assertTrue(Math.abs(load.loadWatts() - 600.0) < 0.001,
                "Ожидалась сумма нагрузки обоих экранов через промежуточный узел, получено: " + load.loadWatts());
    }
}
