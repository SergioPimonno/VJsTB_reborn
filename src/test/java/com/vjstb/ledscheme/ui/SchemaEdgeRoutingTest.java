package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.EdgeRouteMode;
import com.vjstb.ledscheme.model.EdgeWaypoint;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.NodeOrientation;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SchemaStylePreset;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Color;
import java.awt.Point;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** docs/schema-ports-rework/PLAN.md, задача T4.4 — интеграция OrthogonalRouter в
 *  холст: AUTO-маршрут, превращение в MANUAL при перетаскивании, ортогональное
 *  редактирование при переносе блока, разрешение цвета линии (D9). */
class SchemaEdgeRoutingTest {

    private static AppModel model(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        return model;
    }

    private static SchemaCanvasPanel canvas(AppModel model, Path dir) {
        return canvas(model, dir, SchemaStylePreset.SCREEN);
    }

    private static SchemaCanvasPanel canvas(AppModel model, Path dir, SchemaStylePreset preset) {
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        settings.setSchemaStylePreset(preset);
        return new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settings);
    }

    /** Баг-репорт пользователя 2026-09-18: "если подключать линию к блоку экрана
     *  без режима кабинеты-тоже гнёзда, то линия не трассируется под углом. если
     *  включить режим гнёзд — то трассировка работает" — обычная связь узел-узел
     *  БЕЗ гнезда вовсе ({@code fromPortId}/{@code toPortId} оба {@code null},
     *  ровно так соединяются узлы, когда у них нет привязки к конкретному
     *  разъёму/кабинету) раньше была ЦЕЛИКОМ выключена из авто-трассировки — та же
     *  причина, что и у уже исправленного гнезда-кабинета (T4.4 не покрывала этот
     *  случай, см. {@link #routeEndpointFor}, который это чинит через {@link
     *  #clipToBorder}). */
    @Test
    void autoRouteWorksForAPlainNodeToNodeEdgeWithoutAnySocketAtAll(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 200, null);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 500, 0, null);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), b.getId(), null);
        model.setEdgeRouteMode(edge, EdgeRouteMode.AUTO);

        SchemaCanvasPanel canvas = canvas(model, dir);
        List<double[]> pts = canvas.routePointsForTest(edge);

        assertTrue(pts.size() > 2, "A и B по диагонали друг от друга — авто-маршрут обязан свернуть под 90°");
        for (int i = 0; i + 1 < pts.size(); i++) {
            double[] p1 = pts.get(i), p2 = pts.get(i + 1);
            boolean horizontal = Math.abs(p1[1] - p2[1]) < 1e-6;
            boolean vertical = Math.abs(p1[0] - p2[0]) < 1e-6;
            assertTrue(horizontal || vertical, "сегмент " + i + " не ортогонален — связь без гнёзд не трассируется");
        }
    }

    @Test
    void autoRouteModeProducesAnAxisAlignedOrthogonalPath(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 200, null);
        CardPort aOut = model.addCardToNode(a, "Видео", List.of(
                new CardPort("HDMI", PortDirection.OUT, 1))).getPorts().get(0);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 500, 0, null);
        CardPort bIn = model.addCardToNode(b, "Видео", List.of(
                new CardPort("HDMI", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), aOut.getId(), null,
                b.getId(), bIn.getId(), null, null, EdgeRouteMode.AUTO);

        SchemaCanvasPanel canvas = canvas(model, dir);
        List<double[]> pts = canvas.routePointsForTest(edge);

        assertTrue(pts.size() >= 2);
        for (int i = 0; i + 1 < pts.size(); i++) {
            double[] p1 = pts.get(i), p2 = pts.get(i + 1);
            boolean horizontal = Math.abs(p1[1] - p2[1]) < 1e-6;
            boolean vertical = Math.abs(p1[0] - p2[0]) < 1e-6;
            assertTrue(horizontal || vertical, "сегмент " + i + " не ортогонален");
        }
    }

    /** Баг-репорт пользователя 2026-09-18: "линии не перетрассировываются по прямым
     *  углам (выбран режим авто)" — воспроизведено на связи от гнезда-кабинета
     *  расключения экрана (миниатюра на узле {@code SCREEN}), а не от обычного
     *  гнезда карты. Причина была в {@code autoRoutePoints}: связи с {@code
     *  fromCabinetInstanceId != null} безусловно откатывались на прямую линию,
     *  T4.4 никогда их не трассировала — см. {@link SchemaCanvasPanel#
     *  routeEndpointFor}, которая это чинит (сторона считается как ближайшая грань
     *  рамки узла к точке кабинета на миниатюре, см. {@link SchemaCanvasPanel#
     *  nearestSide}). */
    @Test
    void autoRouteFromACabinetSocketOnAScreenWiringThumbnailIsAlsoOrthogonal(@TempDir Path dir) {
        AppModel model = model(dir);
        CabinetType type = new CabinetType();
        type.setName("P3 500x500");
        type.setWidthMm(500);
        type.setHeightMm(500);
        type.setResolutionWidth(128);
        type.setResolutionHeight(128);
        type = model.addCabinetType(type);
        Screen screen = model.addScreen("Экран", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);

        ControllerType ct = new ControllerType();
        ct.setName("Контроллер");
        ct.getCards().add(new SchemaCard("Карта 1", List.of(new CardPort("RJ45", PortDirection.OUT, 4))));
        ct = model.addControllerType(ct);
        model.addControllerToScreen(screen, ct.getId());

        List<String> cabIds = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addSignalChain(2, false, List.of(cabIds.get(0), cabIds.get(1)));
        model.autoPopulateSchema(SchemaMode.SIGNAL, true);

        List<SchemaEdge> edges = model.schemaEdgesForCurrentScene(SchemaMode.SIGNAL);
        SchemaEdge edge = edges.stream().filter(e -> e.getFromCabinetInstanceId() != null).findFirst()
                .orElseThrow(() -> new IllegalStateException("автозаполнение не создало связь от гнезда-кабинета"));
        model.setEdgeRouteMode(edge, EdgeRouteMode.AUTO);

        SchemaCanvasPanel canvas = canvas(model, dir);
        List<double[]> pts = canvas.routePointsForTest(edge);

        assertTrue(pts.size() > 2, "должна появиться хотя бы одна точка излома — иначе это по-прежнему прямая");
        for (int i = 0; i + 1 < pts.size(); i++) {
            double[] p1 = pts.get(i), p2 = pts.get(i + 1);
            boolean horizontal = Math.abs(p1[1] - p2[1]) < 1e-6;
            boolean vertical = Math.abs(p1[0] - p2[0]) < 1e-6;
            assertTrue(horizontal || vertical, "сегмент " + i + " не ортогонален (связь от гнезда-кабинета)");
        }
    }

    /** Баг-репорт пользователя 2026-09-18: "если гнездо слева, а линия идёт справа
     *  или снизу, то она заходит под блок и заходит напрямую в гнездо, это
     *  некрасиво". Причина: свой же узел одного из концов связи был ЦЕЛИКОМ
     *  исключён из препятствий (иначе раздутие затянуло бы гнездо, лежащее РОВНО
     *  на его границе, "внутрь" препятствия) — но это же позволяло связующему
     *  участку маршрута срезать прямо ЧЕРЕЗ тело этого узла, если гнездо на одной
     *  стороне, а сосед — с другой. Фикс — свой же узел тоже препятствие, раздутое
     *  с 3 сторон, кроме той, где само гнездо ({@code SchemaCanvasPanel
     *  .selfObstacleWithClearance}). */
    @Test
    void autoRouteNeverCutsThroughTheOwnBodyOfEitherEndpointNode(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 300, 300, null);
        CardPort aIn = model.addCardToNode(a, "Видео", List.of(
                new CardPort("HDMI", PortDirection.IN, 1))).getPorts().get(0);
        a.setWidth(200);
        a.setHeight(120);

        // B — правее и на той же высоте, что и A: гнездо A смотрит ВЛЕВО, но чтобы
        // добраться до B, маршруту нужно уйти вправо ЗА пределы A — если A не
        // считается препятствием, кратчайший путь просто идёт по прямой на высоте
        // пина A, прямо через тело A, а не в обход сверху/снизу.
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 700, 340, null);
        b.setOrientation(NodeOrientation.LEFT);
        CardPort bOut = model.addCardToNode(b, "Видео", List.of(
                new CardPort("HDMI", PortDirection.OUT, 1))).getPorts().get(0);

        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, b.getId(), bOut.getId(), null,
                a.getId(), aIn.getId(), null, null, EdgeRouteMode.AUTO);

        SchemaCanvasPanel canvas = canvas(model, dir);
        List<double[]> pts = canvas.routePointsForTest(edge);

        assertTrue(pts.size() > 2, "фикстура должна вынуждать обход — иначе тест ничего не проверяет");
        for (int i = 0; i + 1 < pts.size(); i++) {
            assertFalse(crossesInterior(pts.get(i), pts.get(i + 1), a),
                    "сегмент " + i + " проходит через тело узла A — гнездо этого же узла");
        }
    }

    /** Баг-репорт пользователя 2026-09-18: "отступ линии снизу/сверху блока не
     *  работает" — обход СВОЕГО ЖЕ узла (со сторон, отличных от той, где гнездо
     *  этого конца связи) должен держать тот же отступ, что и от чужих блоков, а
     *  не идти вплотную к границе (раньше — 0, см. {@code
     *  SchemaCanvasPanel.selfObstacleWithClearance}). */
    @Test
    void ownNodeBypassKeepsTheSameClearanceAsOtherBlocks(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 300, 300, null);
        CardPort aIn = model.addCardToNode(a, "Видео", List.of(
                new CardPort("HDMI", PortDirection.IN, 1))).getPorts().get(0);
        a.setWidth(200);
        a.setHeight(120);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 700, 340, null);
        b.setOrientation(NodeOrientation.LEFT);
        CardPort bOut = model.addCardToNode(b, "Видео", List.of(
                new CardPort("HDMI", PortDirection.OUT, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, b.getId(), bOut.getId(), null,
                a.getId(), aIn.getId(), null, null, EdgeRouteMode.AUTO);

        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        settings.setSchemaRouteStubPx(30);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settings);
        List<double[]> pts = canvas.routePointsForTest(edge);

        double aRight = a.getX() + a.getWidth();
        double closestX = pts.stream().mapToDouble(p -> p[0]).filter(x -> x >= aRight - 1e-6).min().orElse(Double.NaN);
        assertFalse(Double.isNaN(closestX), "маршрут должен пройти правее A хотя бы одной точкой");
        assertTrue(closestX - aRight >= 30 - 1e-6,
                "обход своего же узла A (не с той стороны, где гнездо) держится вплотную (" + (closestX - aRight)
                        + "px) вместо настроенного отступа (30px)");
    }

    private static boolean crossesInterior(double[] p1, double[] p2, SchemaNode n) {
        double left = n.getX(), right = n.getX() + n.getWidth();
        double top = n.getY(), bottom = n.getY() + n.getHeight();
        double midX = (p1[0] + p2[0]) / 2, midY = (p1[1] + p2[1]) / 2;
        boolean midInside = midX > left && midX < right && midY > top && midY < bottom;
        boolean p1Inside = p1[0] > left && p1[0] < right && p1[1] > top && p1[1] < bottom;
        boolean p2Inside = p2[0] > left && p2[0] < right && p2[1] > top && p2[1] < bottom;
        return midInside || p1Inside || p2Inside;
    }

    /** Пожелание пользователя 2026-09-18: "красивее, когда у последнего отрезка
     *  есть определённая длина, зафиксированная (можно вынести в настройки)" —
     *  раньше ус был жёстко зашит в {@code OrthogonalRouter.STUB = 12},
     *  независимо от настроек; теперь длина берётся из {@code
     *  UserProfile.getSchemaRouteStubPx()}. */
    @Test
    void firstAndLastSegmentLengthMatchesTheConfiguredStubSetting(@TempDir Path dir) {
        // Точная длина проверена на уровне чистой функции — OrthogonalRouterTest
        // .customStubLengthOverloadRespectsTheConfiguredMinimum (там же объяснение,
        // почему "не короче", а не "равно": simplify() сливает ус со следующим
        // коллинеарным отрезком грид-пути, если тот продолжает то же направление —
        // это не баг, а как раз то, что убирает лишние технические точки излома).
        // Здесь — что настройка ДЕЙСТВИТЕЛЬНО доходит от UserProfile до вызова
        // OrthogonalRouter, а не игнорируется по пути.
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 200, null);
        CardPort aOut = model.addCardToNode(a, "Видео", List.of(
                new CardPort("HDMI", PortDirection.OUT, 1))).getPorts().get(0);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 500, 0, null);
        CardPort bIn = model.addCardToNode(b, "Видео", List.of(
                new CardPort("HDMI", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), aOut.getId(), null,
                b.getId(), bIn.getId(), null, null, EdgeRouteMode.AUTO);

        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        settings.setSchemaRouteStubPx(40);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settings);
        List<double[]> pts = canvas.routePointsForTest(edge);

        double firstLen = Math.hypot(pts.get(1)[0] - pts.get(0)[0], pts.get(1)[1] - pts.get(0)[1]);
        double lastLen = Math.hypot(pts.get(pts.size() - 1)[0] - pts.get(pts.size() - 2)[0],
                pts.get(pts.size() - 1)[1] - pts.get(pts.size() - 2)[1]);
        assertTrue(firstLen >= 40 - 1e-6, "первый отрезок должен быть не короче настроенного уса (40)");
        assertTrue(lastLen >= 40 - 1e-6, "последний отрезок должен быть не короче настроенного уса (40)");
    }

    /** Пожелание пользователя 2026-09-18: "чтобы этот отступ применялся к блокам
     *  целиком" — та же настройка длины уса теперь и отступ, с которым
     *  {@code OrthogonalRouter} огибает ЧУЖИЕ блоки (раньше был отдельной жёстко
     *  зашитой константой в 10px, не связанной с усом вообще). Проверяем на
     *  препятствии, которое связь обязана обогнуть — со стубом побольше обход
     *  должен пройти заметно дальше от реальной границы препятствия, а не на том
     *  же расстоянии, что и с маленьким. */
    @Test
    void detourAroundAnotherBlockGrowsWithTheConfiguredStubSetting(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 100, null);
        CardPort aOut = model.addCardToNode(a, "Видео", List.of(
                new CardPort("HDMI", PortDirection.OUT, 1))).getPorts().get(0);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 700, 100, null);
        CardPort bIn = model.addCardToNode(b, "Видео", List.of(
                new CardPort("HDMI", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), aOut.getId(), null,
                b.getId(), bIn.getId(), null, null, EdgeRouteMode.AUTO);

        SettingsManager probeSettings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "probe.json")));
        SchemaCanvasPanel probeCanvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, probeSettings);
        Point pinA = probeCanvas.socketPositionForTest(a, aOut.getId(), edge);
        Point pinB = probeCanvas.socketPositionForTest(b, bIn.getId(), edge);
        double wallTop = Math.min(pinA.y, pinB.y) - 30;
        SchemaNode wall = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "Стена",
                (pinA.x + pinB.x) / 2.0 - 20, wallTop, null);
        wall.setWidth(40);
        wall.setHeight(60);

        double smallClearance = bypassClearanceAboveWall(model, dir, edge, wall, 10);
        double bigClearance = bypassClearanceAboveWall(model, dir, edge, wall, 80);

        assertTrue(bigClearance > smallClearance + 30,
                "обход с бОльшим настроенным усом (80) должен держаться заметно дальше от стены, чем с маленьким"
                        + " (10): " + smallClearance + " vs " + bigClearance);
    }

    /** Строит холст с {@code stubPx}, перетрассировывает {@code edge} и возвращает
     *  расстояние по Y между горизонтальным отрезком обхода (тем, что выше стены) и
     *  РЕАЛЬНОЙ (не раздутой) верхней границей стены — т.е. фактический зазор, а не
     *  настроенное число само по себе (важно проверить именно результат геометрии,
     *  не что "куда-то передалась переменная"). */
    private static double bypassClearanceAboveWall(AppModel model, Path dir, SchemaEdge edge, SchemaNode wall,
                                                     int stubPx) {
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings-" + stubPx + ".json")));
        settings.setSchemaRouteStubPx(stubPx);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settings);
        List<double[]> pts = canvas.routePointsForTest(edge);
        double wallTop = wall.getY();
        double bypassY = Double.NaN;
        for (double[] p : pts) {
            if (p[1] < wallTop) {
                bypassY = p[1];
                break;
            }
        }
        assertTrue(!Double.isNaN(bypassY), "маршрут должен пройти ВЫШЕ стены хотя бы одной точкой (ус " + stubPx + ")");
        return wallTop - bypassY;
    }

    @Test
    void legacyEdgeWithoutRouteModeStaysAStraightLineEvenWithConcretePorts(@TempDir Path dir) {
        // "Старый проект открывается с прежними маршрутами" (PLAN.md §2.6/T4.4) —
        // связь без явного routeMode (как из старого workspace.json) должна остаться
        // прямой, даже если у обоих концов теперь есть привязка к гнезду.
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 200, null);
        CardPort aOut = model.addCardToNode(a, "Видео", List.of(
                new CardPort("HDMI", PortDirection.OUT, 1))).getPorts().get(0);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 500, 0, null);
        CardPort bIn = model.addCardToNode(b, "Видео", List.of(
                new CardPort("HDMI", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), aOut.getId(), b.getId(), bIn.getId(), null);

        SchemaCanvasPanel canvas = canvas(model, dir);
        List<double[]> pts = canvas.routePointsForTest(edge);

        assertEquals(2, pts.size(), "без явного routeMode и без изломов — та же прямая линия узел-узел, что и раньше");
    }

    @Test
    void materializingAnAutoEdgeConvertsItToManualWithTheCurrentRoute(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 100, null);
        CardPort aOut = model.addCardToNode(a, "Видео", List.of(
                new CardPort("HDMI", PortDirection.OUT, 1))).getPorts().get(0);
        // B заметно дальше от A, чем в остальных тестах файла (а не просто 300) —
        // с настраиваемой длиной уса (по умолчанию 24, доводка T4.4, тоже пожелание
        // пользователя 2026-09-18: "чтобы этот отступ применялся к блокам целиком")
        // отступ от ЧУЖИХ препятствий вырос вместе с усом; слишком узкий зазор между
        // гнёздами и стеной раздувал бы стену настолько, что она поглотила бы сами
        // точки усов — вырожденный случай, гридновский поиск пути не запускается
        // вовсе, откат на fallback-прямую (не то, что здесь проверяется).
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 600, 100, null);
        CardPort bIn = model.addCardToNode(b, "Видео", List.of(
                new CardPort("HDMI", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), aOut.getId(), null,
                b.getId(), bIn.getId(), null, null, EdgeRouteMode.AUTO);

        SchemaCanvasPanel canvas = canvas(model, dir);
        // Препятствие ровно на прямой между гнёздами (координаты — из РЕАЛЬНОЙ
        // раскладки, не угаданы по номинальной позиции/ширине узла — та растёт под
        // подписи и заранее неизвестна) — авто-маршрут обязан свернуть, иначе у него
        // не будет ВНУТРЕННИХ точек и тест ничего не проверит.
        Point pinA = canvas.socketPositionForTest(a, aOut.getId(), edge);
        Point pinB = canvas.socketPositionForTest(b, bIn.getId(), edge);
        assertTrue(pinB.x > pinA.x + 200, "фикстура должна оставлять зазор между гнёздами шире препятствия и усов");
        double wallX = (pinA.x + pinB.x) / 2.0 - 20;
        SchemaNode wall = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "Стена",
                wallX, Math.min(pinA.y, pinB.y) - 100, null);
        wall.setWidth(40);
        wall.setHeight(200);
        List<double[]> before = canvas.routePointsForTest(edge);
        assertTrue(before.size() > 2, "фикстура должна давать хотя бы один излом — иначе материализация тривиальна");

        canvas.materializeAutoRouteIfNeededForTest(edge);

        assertEquals(EdgeRouteMode.MANUAL, edge.getRouteMode());
        assertEquals(before.size() - 2, edge.getWaypoints().size());
        for (int i = 0; i < edge.getWaypoints().size(); i++) {
            EdgeWaypoint w = edge.getWaypoints().get(i);
            assertEquals(before.get(i + 1)[0], w.getX(), 1e-6);
            assertEquals(before.get(i + 1)[1], w.getY(), 1e-6);
        }
        // После материализации маршрут не должен визуально прыгнуть.
        List<double[]> after = canvas.routePointsForTest(edge);
        assertEquals(before.size(), after.size());
    }

    @Test
    void materializingAlreadyManualEdgeDoesNothing(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 0, null);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 300, 0, null);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), b.getId(), null);
        model.setSchemaEdgeWaypoints(edge, List.of(new EdgeWaypoint(150, 50)));
        model.setEdgeRouteMode(edge, EdgeRouteMode.MANUAL);

        SchemaCanvasPanel canvas = canvas(model, dir);
        canvas.materializeAutoRouteIfNeededForTest(edge);

        assertEquals(1, edge.getWaypoints().size());
        assertEquals(150, edge.getWaypoints().get(0).getX(), 1e-6);
    }

    @Test
    void keepOrthogonalWaypointsAdjustsNearEndAfterNodeMoves(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 100, null);
        CardPort aOut = model.addCardToNode(a, "Видео", List.of(
                new CardPort("HDMI", PortDirection.OUT, 1))).getPorts().get(0);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 400, 300, null);
        CardPort bIn = model.addCardToNode(b, "Видео", List.of(
                new CardPort("HDMI", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), aOut.getId(), b.getId(), bIn.getId(), null);
        model.setSchemaEdgeWaypoints(edge, List.of(new EdgeWaypoint(200, 110), new EdgeWaypoint(200, 310)));
        model.setEdgeRouteMode(edge, EdgeRouteMode.MANUAL);

        SchemaCanvasPanel canvas = canvas(model, dir);
        double pinYBefore = canvas.socketPositionForTest(a, aOut.getId(), edge).y;
        assertEquals(110.0, edge.getWaypoints().get(0).getY(), 1e-6);

        // Двигаем A вниз на 40 — HDMI OUT на RIGHT-стороне (роль VIDEO), значит
        // ближний излом должен подтянуться по Y к НОВОЙ высоте пина.
        a.setY(a.getY() + 40);
        canvas.keepOrthogonalWaypointsForNodeForTest(a);

        double pinYAfter = canvas.socketPositionForTest(a, aOut.getId(), edge).y;
        assertEquals(40.0, pinYAfter - pinYBefore, 1e-6, "фикстура должна реально сдвинуть пин, иначе тест не проверяет ничего");
        assertEquals(pinYAfter, edge.getWaypoints().get(0).getY(), 1e-6,
                "ближний излом должен встать на новую высоту пина (отрезок остаётся горизонтальным)");
        assertEquals(200.0, edge.getWaypoints().get(0).getX(), 1e-6, "X ближнего излома трогать не нужно");
        // Дальний конец (у B) не должен был измениться вовсе.
        assertEquals(310.0, edge.getWaypoints().get(1).getY(), 1e-6);
    }

    @Test
    void keepOrthogonalWaypointsDoesNothingWhenSettingIsDisabled(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 100, null);
        CardPort aOut = model.addCardToNode(a, "Видео", List.of(
                new CardPort("HDMI", PortDirection.OUT, 1))).getPorts().get(0);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 400, 300, null);
        CardPort bIn = model.addCardToNode(b, "Видео", List.of(
                new CardPort("HDMI", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), aOut.getId(), b.getId(), bIn.getId(), null);
        model.setSchemaEdgeWaypoints(edge, List.of(new EdgeWaypoint(200, 110), new EdgeWaypoint(200, 310)));
        model.setEdgeRouteMode(edge, EdgeRouteMode.MANUAL);

        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings-off.json")));
        settings.activeProfile().setOrthogonalEdgeEditing(false);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settings);

        a.setY(a.getY() + 40);
        canvas.keepOrthogonalWaypointsForNodeForTest(a);

        assertEquals(110.0, edge.getWaypoints().get(0).getY(), 1e-6, "настройка выключена — излом трогать нельзя");
    }

    @Test
    void printPresetResolvesRoleAndNominalColorsWhenEdgeHasNoCustomColor(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 0, null);
        CardPort sync = model.addCardToNode(a, "Синхро", List.of(
                new CardPort("Genlock (SDI)", PortDirection.OUT, 1))).getPorts().get(0);
        sync.setRole(InterfaceRole.SYNC);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 300, 0, null);
        CardPort bIn = model.addCardToNode(b, "Синхро", List.of(
                new CardPort("Genlock (SDI)", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), sync.getId(), b.getId(), bIn.getId(), null);

        SchemaCanvasPanel canvas = canvas(model, dir, SchemaStylePreset.PRINT);
        canvas.renderImage(10, 10, false, 1.0); // прогревает style-поле (см. paint())
        Color color = canvas.edgeDefaultColorForTest(edge);

        assertEquals(new Color(0x7A, 0x1F, 0xA3), color, "«Печатный»: цвет линии для роли SYNC — фиолетовый");
    }

    @Test
    void screenPresetIsUnaffectedByRoleColorsAndKeepsTheOldDefault(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 0, null);
        CardPort sync = model.addCardToNode(a, "Синхро", List.of(
                new CardPort("Genlock (SDI)", PortDirection.OUT, 1))).getPorts().get(0);
        sync.setRole(InterfaceRole.SYNC);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 300, 0, null);
        CardPort bIn = model.addCardToNode(b, "Синхро", List.of(
                new CardPort("Genlock (SDI)", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), sync.getId(), b.getId(), bIn.getId(), null);

        SchemaCanvasPanel canvas = canvas(model, dir, SchemaStylePreset.SCREEN);
        canvas.renderImage(10, 10, false, 1.0);
        Color color = canvas.edgeDefaultColorForTest(edge);

        assertEquals(com.vjstb.ledscheme.ui.Palette.MUTED, color,
                "«Экранный» пресет пока не задаёт цвета ролей — поведение не должно поменяться");
    }

    @Test
    void customEdgeColorAlwaysWinsOverRoleColor(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 0, null);
        CardPort sync = model.addCardToNode(a, "Синхро", List.of(
                new CardPort("Genlock (SDI)", PortDirection.OUT, 1))).getPorts().get(0);
        sync.setRole(InterfaceRole.SYNC);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 300, 0, null);
        CardPort bIn = model.addCardToNode(b, "Синхро", List.of(
                new CardPort("Genlock (SDI)", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), sync.getId(), b.getId(), bIn.getId(), null);
        model.setSchemaEdgeColor(edge, Color.PINK.getRGB());

        SchemaCanvasPanel canvas = canvas(model, dir, SchemaStylePreset.PRINT);
        canvas.renderImage(10, 10, false, 1.0);

        // edgeDefaultColor() сам по себе не смотрит на edge.getColor() — приоритет
        // (D9: пользовательский цвет → цвет роли → цвет стиля по умолчанию) собирает
        // paint() снаружи ИМЕННО в этом порядке (см. SchemaCanvasPanel.paint):
        // customColor != null ? customColor : edgeDefaultColor(edge). Тест фиксирует,
        // что эта сборка не даёт роли (фиолетовый SYNC) выиграть у явного цвета.
        Color roleColor = canvas.edgeDefaultColorForTest(edge);
        Color used = edge.getColor() != null ? new Color(edge.getColor()) : roleColor;
        assertEquals(Color.PINK, used);
    }
}
