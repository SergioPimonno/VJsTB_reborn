package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EdgeRouteMode;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.schema.SchemaFixtures;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.schemalayout.NodePortLayout;
import com.vjstb.ledscheme.settings.SchemaRenderMode;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Приёмка задачи T5.5 (docs/schema-ports-rework/PLAN.md, «Этап 5.5», решение D16):
 * переключатель {@link SchemaRenderMode#CLASSIC} должен полностью восстанавливать
 * дорефакторинговую раскладку/отрисовку/хит-тестинг гнёзд {@link SchemaCanvasPanel} —
 * не просто цвета поверх {@link com.vjstb.ledscheme.service.schemalayout.NodePortLayout}.
 * По умолчанию (без переключения) остаётся {@link SchemaRenderMode#MODERN} — эти тесты
 * НЕ трогают уже существующие тесты MODERN-пути (см. {@link SchemaSocketPositionTest},
 * {@link SchemaEdgeRoutingTest} и т.п.), только добавляют покрытие для CLASSIC.
 */
class SchemaCanvasPanelClassicModeTest {

    private static SettingsManager settingsFor(Path dir, String name) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), name + ".json")));
    }

    /** Пункт приёмки: рендер не падает ни в MODERN (по умолчанию), ни в CLASSIC —
     *  на одной и той же синтетической сцене T0.1 (сигнал и питание разом, экран,
     *  многогрупповые карты, генлок-петля, транзитные силовые щиты). */
    @Test
    void rendersWithoutCrashingInBothModes(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SettingsManager settings = settingsFor(dir, "render-both");
        SchemaCanvasPanel signalCanvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settings);
        SchemaCanvasPanel powerCanvas = new SchemaCanvasPanel(model, SchemaMode.POWER, settings);

        BufferedImage modernSignal = signalCanvas.renderImage(1600, 900, false);
        BufferedImage modernPower = powerCanvas.renderImage(1600, 900, false);
        assertNotNull(modernSignal);
        assertNotNull(modernPower);
        assertEquals(1600, modernSignal.getWidth());
        assertEquals(900, modernSignal.getHeight());

        settings.setSchemaRenderMode(SchemaRenderMode.CLASSIC);
        BufferedImage classicSignal = signalCanvas.renderImage(1600, 900, false);
        BufferedImage classicPower = powerCanvas.renderImage(1600, 900, false);
        assertNotNull(classicSignal);
        assertNotNull(classicPower);
        assertEquals(1600, classicSignal.getWidth());
        assertEquals(900, classicSignal.getHeight());

        // Экран с миниатюрой расключения — отдельный путь отрисовки (drawScreenWiringThumbnail),
        // общий для обоих режимов; проверяем, что и он не падает в CLASSIC.
        BufferedImage classicSignalWiring = signalCanvas.renderImage(1600, 900, true);
        assertNotNull(classicSignalWiring);
    }

    /** Пункт приёмки T3.2/T5.5: гнездо с count==1 (одна физическая точка) должно
     *  давать РАЗНУЮ экранную позицию в MODERN (рамка блока через NodePortLayout) и
     *  в CLASSIC (строка гнёзд у одного из краёв блока) — иначе переключатель менял
     *  бы только цвета, а не реальную геометрию (то, что явно попросил пользователь,
     *  DIALOG.md реплика 4: "не только цвета"). Дополнительно — классическая позиция
     *  не должна совпасть НИ С ОДНИМ пином {@link NodePortLayout} этого узла, что
     *  показывает: CLASSIC не обращается к современной раскладке вовсе, а не просто
     *  считает её и отбрасывает. */
    @Test
    void classicPinPositionDiffersFromModernAndIgnoresNodePortLayout(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SettingsManager settings = settingsFor(dir, "pin-position");
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settings);
        SchemaNode q8 = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "PixelHue Q8");
        CardPort genlockIn = SchemaFixtures.portOnCard(q8, "MVR", "Genlock Tri-Level");
        assertEquals(1, genlockIn.getCount(), "гнездо с count=1 всегда даёт ровно одну точку в обоих режимах");

        Point modernPos = canvas.socketPositionForTest(q8, genlockIn.getId(), null);
        assertNotNull(modernPos, "MODERN должен найти это гнездо (сегодняшний режим по умолчанию)");

        settings.setSchemaRenderMode(SchemaRenderMode.CLASSIC);
        Point classicPos = canvas.socketPositionClassicForTest(q8, genlockIn.getId(), null);
        assertNotNull(classicPos, "CLASSIC должен найти это же гнездо своей собственной раскладкой");

        assertNotEquals(modernPos, classicPos,
                "MODERN (рамка блока) и CLASSIC (строка гнёзд у края) обязаны давать разную геометрию");

        NodePortLayout.Result modernLayout = canvas.nodeLayoutForTest(q8);
        boolean classicMatchesAnyModernPin = modernLayout.pins().stream().anyMatch(pin -> {
            int px = (int) Math.round(q8.getX() + pin.x());
            int py = (int) Math.round(q8.getY() + pin.y());
            return px == classicPos.x && py == classicPos.y;
        });
        assertFalse(classicMatchesAnyModernPin,
                "классическая позиция не должна совпасть ни с одним пином NodePortLayout — CLASSIC не должен"
                        + " обращаться к современной раскладке вовсе");
    }

    /** Пункт приёмки T5.5: клик по пину, нарисованному CLASSIC-раскладкой, должен
     *  через классический хит-тест ({@code socketAtClassic}) резолвиться в ТО ЖЕ
     *  самое гнездо — как до этого плана (когда единственным хит-тестом и был этот
     *  путь). */
    @Test
    void classicHitTestResolvesThePinItDrew(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SettingsManager settings = settingsFor(dir, "hit-test");
        settings.setSchemaRenderMode(SchemaRenderMode.CLASSIC);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settings);
        SchemaNode cvt = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "CVT4K");
        CardPort fiberIn = SchemaFixtures.portOnCard(cvt, "Basic Set", "Fiber");

        Point pos = canvas.socketPositionClassicForTest(cvt, fiberIn.getId(), null);
        assertNotNull(pos, "CLASSIC должен нарисовать это гнездо (у узла есть карта 'Basic Set')");

        CardPort hitPort = canvas.socketAtClassicPortForTest(pos);
        SchemaNode hitNode = canvas.socketAtClassicNodeForTest(pos);
        assertSame(fiberIn, hitPort, "клик ровно по нарисованной точке должен попасть в то же гнездо");
        assertSame(cvt, hitNode);
    }

    /** Пункт приёмки T4.4/T5.5: {@link EdgeRouteMode#AUTO} в MODERN считается через
     *  {@code OrthogonalRouter} (координаты берутся из современных пинов), а в
     *  CLASSIC — НИКОГДА (до этого плана AUTO/OrthogonalRouter не существовало
     *  вовсе, связь всегда идёт по сохранённым изломам/прямой между КЛАССИЧЕСКИМИ
     *  точками гнёзд, см. classicMode() в {@code routePoints}). Само значение
     *  {@code routeMode} на связи при этом не меняется переключателем. */
    @Test
    void classicRouteNeverGoesThroughOrthogonalRouter(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SettingsManager settings = settingsFor(dir, "route-mode");
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settings);
        SchemaNode q8 = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "PixelHue Q8");
        SchemaNode mctrl1 = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "MCTRL4K #1");
        CardPort q8Out = SchemaFixtures.portOnCard(q8, "HDMI+SDI+Fiber Output Card 1", "HDMI 2.0");

        SchemaEdge edge = findEdge(model, q8.getId(), q8Out.getId(), mctrl1.getId());
        model.setEdgeRouteMode(edge, EdgeRouteMode.AUTO);

        // MODERN: начало маршрута — координаты пина СОВРЕМЕННОЙ раскладки.
        java.util.List<double[]> modernRoute = canvas.routePointsForTest(edge);
        Point modernPin = canvas.socketPositionForTest(q8, q8Out.getId(), edge);
        assertNotNull(modernPin);
        assertEquals(modernPin.x, (int) Math.round(modernRoute.get(0)[0]));
        assertEquals(modernPin.y, (int) Math.round(modernRoute.get(0)[1]));

        // CLASSIC: то же AUTO-ребро — начало маршрута теперь координаты КЛАССИЧЕСКОГО
        // гнезда, а не современного пина (OrthogonalRouter не вызывается вовсе).
        settings.setSchemaRenderMode(SchemaRenderMode.CLASSIC);
        java.util.List<double[]> classicRoute = canvas.routePointsForTest(edge);
        Point classicPin = canvas.socketPositionClassicForTest(q8, q8Out.getId(), edge);
        assertNotNull(classicPin);
        assertEquals(classicPin.x, (int) Math.round(classicRoute.get(0)[0]));
        assertEquals(classicPin.y, (int) Math.round(classicRoute.get(0)[1]));

        assertNotEquals(modernPin, classicPin,
                "если бы совпали, тест не отличал бы 'CLASSIC использует свою геометрию' от совпадения по случайности");
    }

    private static SchemaEdge findEdge(AppModel model, String fromNodeId, String fromPortId, String toNodeId) {
        for (SchemaEdge e : model.getCurrentScene().getSchemaEdges()) {
            if (fromNodeId.equals(e.getFromNodeId()) && fromPortId.equals(e.getFromPortId())
                    && toNodeId.equals(e.getToNodeId())) {
                return e;
            }
        }
        throw new IllegalStateException("Связь не найдена в фикстуре: " + fromNodeId + "/" + fromPortId
                + " -> " + toNodeId);
    }
}
