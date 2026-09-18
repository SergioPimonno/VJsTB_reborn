package com.vjstb.ledscheme.schema;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EdgeWaypoint;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Синтетическая сцена общей схемы для тестов переработки гнёзд/связей
 * (docs/schema-ports-rework/PLAN.md, задача T0.1) — повторяет СТРУКТУРУ комплектации
 * оборудования реального проекта пользователя «Бармицва» (см. таблицу «Состав блоков»
 * в DIALOG.md), но не сами данные проекта: репозиторий публичный, реальный проект в
 * него не кладём (Золотое правило проекта, PLAN.md §0.9), только форму — многогрупповые
 * карты, генлок-петлю, транзитные силовые щиты — на которой имеет смысл проверять
 * раскладку гнёзд, определение ролей/транзита и трассировку связей.
 *
 * <p>Сигнальная и силовая части лежат в ОДНОЙ сцене (как и в реальном проекте — обе
 * части общей схемы площадки сосуществуют, различаясь только {@link SchemaEdge#getMode()}/
 * {@link SchemaNode#getMode()}), поэтому один и тот же фикстур покрывает оба режима
 * схемы разом.
 */
public final class SchemaFixtures {

    private SchemaFixtures() {
    }

    /** Строит AppModel с проектом "P", сценой "Основной зал", заполненной сигнальной И
     *  силовой частями схемы (см. {@link #buildSignal}/{@link #buildPower}). Каждый вызов
     *  создаёт отдельный файл workspace (см. {@code dir}) — тесты не делят состояние. */
    public static AppModel buildScene(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Основной зал"));
        buildSignal(model);
        buildPower(model);
        return model;
    }

    /** Узел общей схемы сцены с данным режимом и подписью — бросает, если не нашёлся
     *  (опечатка в тесте лучше падает сразу, чем молча возвращает null). */
    public static SchemaNode nodeByLabel(AppModel model, SchemaMode mode, String label) {
        Scene scene = model.getCurrentScene();
        for (SchemaNode n : scene.getSchemaNodes()) {
            if (n.getMode() == mode && label.equals(n.getLabel())) {
                return n;
            }
        }
        throw new IllegalStateException("Узел не найден в фикстуре: [" + mode + "] " + label);
    }

    /** Первая группа разъёмов с данным типом на данной карте узла (по имени карты) —
     *  удобный доступ к id гнезда для тестов раскладки/ролей, без ручного перебора. */
    public static CardPort portOnCard(SchemaNode node, String cardName, String connectorType) {
        for (SchemaCard c : node.getCards()) {
            if (!cardName.equals(c.getName())) {
                continue;
            }
            for (CardPort p : c.getPorts()) {
                if (p.getConnectorType().equals(connectorType)) {
                    return p;
                }
            }
        }
        throw new IllegalStateException("Гнездо не найдено: карта '" + cardName + "', тип " + connectorType
                + " на узле '" + node.getLabel() + "'");
    }

    /** Первая группа разъёмов ПИТАНИЯ данного типа и направления на узле (см.
     *  {@link SchemaNode#getPowerConnectors()} — в отличие от {@link #portOnCard},
     *  разъёмы питания не сгруппированы по картам, поэтому направление обязательно:
     *  у "Проходной" совпадающий тип CEE 32A встречается и как IN, и как OUT). */
    public static CardPort powerPort(SchemaNode node, String connectorType, PortDirection direction) {
        for (CardPort p : node.getPowerConnectors()) {
            if (p.getConnectorType().equals(connectorType) && p.getDirection() == direction) {
                return p;
            }
        }
        throw new IllegalStateException("Разъём питания не найден: " + connectorType + " " + direction
                + " на узле '" + node.getLabel() + "'");
    }

    // ---- сигнал ----

    /** PixelHue Q8 (6 карт входа 4×HDMI2.0+4×DP1.2+4×SDI IN, 4 карты выхода
     *  4×HDMI2.0+4×SDI+8×Fiber OUT, MVR), Disguise D3 ×2 (Basic Set + 4×VFC HDMI2.0),
     *  MCTRL4K ×2 с генлок-петлёй между ними, CVT4K, Blackmagic, экран — плюс связи
     *  D3→Q8, Q8→MCTRL (HDMI2.0), генлок Blackmagic→MCTRL/D3, MCTRL↔MCTRL (генлок-петля),
     *  MCTRL→CVT4K (Fiber), CVT4K→экран. */
    private static void buildSignal(AppModel model) {
        SchemaMode m = SchemaMode.SIGNAL;

        SchemaNode q8 = model.addSchemaNode(m, SchemaNodeType.CUSTOM, "PixelHue Q8", 500, 400, null);
        for (int i = 1; i <= 6; i++) {
            model.addCardToNode(q8, "HDMI+DP+SDI Input Card " + i, List.of(
                    new CardPort("HDMI 2.0", PortDirection.IN, 4),
                    new CardPort("DisplayPort 1.2", PortDirection.IN, 4),
                    new CardPort("SDI", PortDirection.IN, 4)));
        }
        for (int i = 1; i <= 4; i++) {
            model.addCardToNode(q8, "HDMI+SDI+Fiber Output Card " + i, List.of(
                    new CardPort("HDMI 2.0", PortDirection.OUT, 4),
                    new CardPort("SDI", PortDirection.OUT, 4),
                    new CardPort("Fiber", PortDirection.OUT, 8)));
        }
        model.addCardToNode(q8, "MVR", List.of(
                new CardPort("HDMI 1.4", PortDirection.OUT, 4),
                new CardPort("Genlock Tri-Level", PortDirection.IN, 1),
                new CardPort("Genlock Tri-Level", PortDirection.OUT, 1),
                new CardPort("Ethernet Cat5e", PortDirection.IN, 1)));

        SchemaNode d3main = buildDisguiseD3(model, "Disguise D3 (Main)", 50, 300);
        SchemaNode d3backup = buildDisguiseD3(model, "Disguise D3 (Backup)", 50, 700);

        SchemaNode mctrl1 = buildMctrl4k(model, "MCTRL4K #1", 900, 250);
        SchemaNode mctrl2 = buildMctrl4k(model, "MCTRL4K #2", 900, 450);

        SchemaNode cvt = model.addSchemaNode(m, SchemaNodeType.CONVERTER, "CVT4K", 1250, 350, null);
        model.addCardToNode(cvt, "Basic Set", List.of(
                new CardPort("Fiber", PortDirection.IN, 2),
                new CardPort("Cat6/RJ45", PortDirection.OUT, 16)));

        SchemaNode bm = model.addSchemaNode(m, SchemaNodeType.CONVERTER, "Blackmagic", 500, 100, null);
        model.addCardToNode(bm, "Sync Generator", List.of(new CardPort("Genlock (SDI)", PortDirection.OUT, 6)));

        CabinetType type = model.addCabinetType(sampleCabinetType());
        Screen screen = model.addScreen("Экран 1", type.getId(), 2, 2, 0, 0);
        SchemaNode screenNode = model.addSchemaNode(m, SchemaNodeType.SCREEN, "Экран 1", 1600, 350, screen.getId());
        model.addCardToNode(screenNode, "Вводы сигнала", List.of(new CardPort("Ethernet", PortDirection.IN, 7)));

        // D3 (VFC HDMI2.0 OUT) -> Q8 (input card 1, HDMI 2.0 IN) — по 4 линии на каждый D3,
        // как в реальном проекте (каждая из 4 карт VFC — отдельная линия).
        CardPort q8In1Hdmi = portOnCard(q8, "HDMI+DP+SDI Input Card 1", "HDMI 2.0");
        for (SchemaCard c : d3main.getCards()) {
            if (c.getName().startsWith("VFC")) {
                model.addSchemaEdge(m, d3main.getId(), c.getPorts().get(0).getId(), q8.getId(), q8In1Hdmi.getId(), null);
            }
        }
        for (SchemaCard c : d3backup.getCards()) {
            if (c.getName().startsWith("VFC")) {
                model.addSchemaEdge(m, d3backup.getId(), c.getPorts().get(0).getId(), q8.getId(), q8In1Hdmi.getId(), null);
            }
        }

        // Q8 (output card 1, HDMI 2.0 OUT) -> оба MCTRL4K (HDMI 2.0 IN).
        CardPort q8Out1Hdmi = portOnCard(q8, "HDMI+SDI+Fiber Output Card 1", "HDMI 2.0");
        CardPort mctrl1HdmiIn = portOnCard(mctrl1, "Basic Set", "HDMI 2.0");
        CardPort mctrl2HdmiIn = portOnCard(mctrl2, "Basic Set", "HDMI 2.0");
        model.addSchemaEdge(m, q8.getId(), q8Out1Hdmi.getId(), mctrl1.getId(), mctrl1HdmiIn.getId(), null);
        model.addSchemaEdge(m, q8.getId(), q8Out1Hdmi.getId(), mctrl2.getId(), mctrl2HdmiIn.getId(), null);

        // Генлок: Blackmagic -> MCTRL1 (первый в столбике) -> MCTRL2 (петля через OUT
        // соседнего контроллера, как в реальном проекте — не КАЖДЫЙ контроллер получает
        // отдельную линию от источника), плюс Blackmagic -> оба D3 -> MVR Q8 напрямую.
        // См. PLAN.md §2.3, контрольный случай "транзит": Genlock IN×1 + OUT×1 на одной
        // карте MCTRL — это ровно тот составной случай, на котором должен сработать
        // авто-транзит (петля идёт транзитом ЧЕРЕЗ контроллер дальше по цепи).
        CardPort bmGenlock = portOnCard(bm, "Sync Generator", "Genlock (SDI)");
        CardPort mctrl1GenlockIn = portOnCard(mctrl1, "Basic Set", "Genlock (SDI)");
        model.addSchemaEdge(m, bm.getId(), bmGenlock.getId(), mctrl1.getId(), mctrl1GenlockIn.getId(), null);
        CardPort d3mainGenlockIn = portOnCard(d3main, "Basic Set", "Genlock Blackburst");
        model.addSchemaEdge(m, bm.getId(), bmGenlock.getId(), d3main.getId(), d3mainGenlockIn.getId(), null);
        CardPort d3backupGenlockIn = portOnCard(d3backup, "Basic Set", "Genlock Blackburst");
        model.addSchemaEdge(m, bm.getId(), bmGenlock.getId(), d3backup.getId(), d3backupGenlockIn.getId(), null);
        CardPort mvrGenlockIn = portOnCard(q8, "MVR", "Genlock Tri-Level");
        model.addSchemaEdge(m, bm.getId(), bmGenlock.getId(), q8.getId(), mvrGenlockIn.getId(), null);

        // MCTRL1 генлок OUT -> MCTRL2 генлок IN — единственный вход MCTRL2 (петля по
        // столбику контроллеров, как в реальном проекте).
        CardPort mctrl1GenlockOut = mctrl1.getCards().get(0).getPorts().stream()
                .filter(p -> p.getConnectorType().equals("Genlock (SDI)") && p.getDirection() == PortDirection.OUT)
                .findFirst().orElseThrow();
        CardPort mctrl2GenlockIn = portOnCard(mctrl2, "Basic Set", "Genlock (SDI)");
        model.addSchemaEdge(m, mctrl1.getId(), mctrl1GenlockOut.getId(), mctrl2.getId(), mctrl2GenlockIn.getId(), null);

        // MCTRL (Fiber OUT) -> CVT4K (Fiber IN) -> экран (Ethernet IN).
        CardPort mctrl1FiberOut = portOnCard(mctrl1, "Basic Set", "Fiber");
        CardPort cvtFiberIn = portOnCard(cvt, "Basic Set", "Fiber");
        model.addSchemaEdge(m, mctrl1.getId(), mctrl1FiberOut.getId(), cvt.getId(), cvtFiberIn.getId(), null);
        CardPort cvtRj45Out = portOnCard(cvt, "Basic Set", "Cat6/RJ45");
        CardPort screenEthIn = portOnCard(screenNode, "Вводы сигнала", "Ethernet");
        model.addSchemaEdge(m, cvt.getId(), cvtRj45Out.getId(), screenNode.getId(), screenEthIn.getId(), null);

        // D3 (Ethernet Cat6 OUT) -> Q8 (MVR, Ethernet Cat5e IN) — обе "сеть", не LED-данные
        // (см. контрольные случаи PLAN.md §2.3).
        CardPort d3mainEth = portOnCard(d3main, "Basic Set", "Ethernet Cat6");
        CardPort mvrEthIn = portOnCard(q8, "MVR", "Ethernet Cat5e");
        model.addSchemaEdge(m, d3main.getId(), d3mainEth.getId(), q8.getId(), mvrEthIn.getId(), null);
    }

    private static SchemaNode buildDisguiseD3(AppModel model, String label, double x, double y) {
        SchemaNode d3 = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, label, x, y, null);
        model.addCardToNode(d3, "Basic Set", List.of(
                new CardPort("DisplayPort 1.2", PortDirection.OUT, 1),
                new CardPort("Ethernet Cat6", PortDirection.OUT, 3),
                new CardPort("Genlock Blackburst", PortDirection.IN, 1),
                new CardPort("XLR", PortDirection.IN, 2),
                new CardPort("XLR", PortDirection.OUT, 2),
                new CardPort("BNC", PortDirection.IN, 16)));
        for (int i = 1; i <= 4; i++) {
            model.addCardToNode(d3, "VFC HDMI2.0 #" + i, List.of(new CardPort("HDMI 2.0", PortDirection.OUT, 1)));
        }
        return d3;
    }

    private static SchemaNode buildMctrl4k(AppModel model, String label, double x, double y) {
        SchemaNode mctrl = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER, label, x, y, null);
        model.addCardToNode(mctrl, "Basic Set", List.of(
                new CardPort("HDMI 2.0", PortDirection.IN, 1),
                new CardPort("DisplayPort 1.2", PortDirection.IN, 1),
                new CardPort("Cat6/RJ45", PortDirection.OUT, 16),
                new CardPort("Fiber", PortDirection.OUT, 2),
                new CardPort("Genlock (SDI)", PortDirection.IN, 1),
                new CardPort("Genlock (SDI)", PortDirection.OUT, 1)));
        return mctrl;
    }

    private static CabinetType sampleCabinetType() {
        CabinetType ct = new CabinetType();
        ct.setName("Fixture P3 500x500");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setPowerConsumptionW(150);
        ct.setWeightKg(12);
        return ct;
    }

    // ---- питание ----

    /** Фишка 125A -> AlpenBox (ввод 125A, 4×CEE 32A отходящих + прочие группы) -> 2
     *  «Проходные» (CEE 32A IN + CEE 32A OUT транзит + 6×CEE 16A OUT отходящих) -> экран
     *  и UPS. Часть связей — с точками излома (имитация уже вручную разведённых под 90°
     *  линий реального проекта, см. DIALOG.md). */
    private static void buildPower(AppModel model) {
        SchemaMode m = SchemaMode.POWER;

        SchemaNode fishka = model.addSchemaNode(m, SchemaNodeType.SOURCE, "Фишка 125A", 50, 50, null);
        CardPort fishkaOut = model.addPowerConnectorToNode(fishka, "CEE 125A", PortDirection.OUT, 1, 3, null);

        SchemaNode alpenBox = model.addSchemaNode(m, SchemaNodeType.SOURCE, "AlpenBox 125A->4x32A", 300, 50, null);
        CardPort alpenIn = model.addPowerConnectorToNode(alpenBox, "CEE 125A", PortDirection.IN, 1, 3, null);
        CardPort alpen32Out = model.addPowerConnectorToNode(alpenBox, "CEE 32A", PortDirection.OUT, 4, 3, null);
        model.addPowerConnectorToNode(alpenBox, "CEE 63A", PortDirection.OUT, 2, 3, null);
        model.addPowerConnectorToNode(alpenBox, "Schuko", PortDirection.OUT, 3, 1, null);
        model.addPowerConnectorToNode(alpenBox, "CEE 16A", PortDirection.OUT, 3, 1, null);

        SchemaNode thru1 = buildProkhodnaya(model, "Проходная 1", 600, 30);
        SchemaNode thru2 = buildProkhodnaya(model, "Проходная 2", 600, 200);

        SchemaNode ups = model.addSchemaNode(m, SchemaNodeType.CUSTOM, "UPS FOH", 900, 400, null);
        model.addPowerConnectorToNode(ups, "CEE 16A", PortDirection.IN, 1);

        CabinetType type = model.addCabinetType(samplePowerCabinetType());
        Screen screen = model.addScreen("Экран П1", type.getId(), 2, 2, 200, 200);
        SchemaNode screenNode = model.addSchemaNode(m, SchemaNodeType.SCREEN, "Экран П1", 900, 30, screen.getId());
        CardPort screenTrueConIn = model.addPowerConnectorToNode(screenNode, "TRUEcon", PortDirection.IN, 6);

        SchemaEdge feed1 = model.addSchemaEdge(m, fishka.getId(), fishkaOut.getId(), alpenBox.getId(), alpenIn.getId(), null);
        model.setSchemaEdgeWaypoints(feed1, List.of(new EdgeWaypoint(150, 50)));

        SchemaEdge toThru1 = model.addSchemaEdge(m, alpenBox.getId(), alpen32Out.getId(), thru1.getId(),
                powerPort(thru1, "CEE 32A", PortDirection.IN).getId(), null);
        model.setSchemaEdgeWaypoints(toThru1, List.of(new EdgeWaypoint(500, 50), new EdgeWaypoint(500, 60)));
        model.addSchemaEdge(m, alpenBox.getId(), alpen32Out.getId(), thru2.getId(),
                powerPort(thru2, "CEE 32A", PortDirection.IN).getId(), null);

        CardPort thru1SixteenOut = powerPort(thru1, "CEE 16A", PortDirection.OUT);
        SchemaEdge toScreen = model.addSchemaEdge(m, thru1.getId(), thru1SixteenOut.getId(), screenNode.getId(),
                screenTrueConIn.getId(), "6xCEE 16A → TrueCON");
        model.setSchemaEdgeWaypoints(toScreen, List.of(new EdgeWaypoint(780, 30)));

        CardPort thru2SixteenOut = powerPort(thru2, "CEE 16A", PortDirection.OUT);
        model.addSchemaEdge(m, thru2.getId(), thru2SixteenOut.getId(), ups.getId(),
                ups.getPowerConnectors().get(0).getId(), "1xCEE 16A");
    }

    private static SchemaNode buildProkhodnaya(AppModel model, String label, double x, double y) {
        SchemaNode n = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, label, x, y, null);
        model.addPowerConnectorToNode(n, "CEE 32A", PortDirection.IN, 1, 3, null);
        model.addPowerConnectorToNode(n, "CEE 32A", PortDirection.OUT, 1, 3, null);
        model.addPowerConnectorToNode(n, "CEE 16A", PortDirection.OUT, 6, 1, null);
        return n;
    }

    private static CabinetType samplePowerCabinetType() {
        CabinetType ct = new CabinetType();
        ct.setName("Fixture Power P3 500x500");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setPowerConsumptionW(150);
        ct.setWeightKg(12);
        return ct;
    }
}
