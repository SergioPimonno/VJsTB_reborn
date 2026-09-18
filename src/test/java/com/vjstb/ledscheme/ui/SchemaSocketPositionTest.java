package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.schema.SchemaFixtures;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.schemalayout.NodePortLayout;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import java.awt.Point;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Пункт приёмки T3.2 (docs/schema-ports-rework/PLAN.md): "клик по пину и привязка
 *  линии совпадают с нарисованным" — {@link SchemaCanvasPanel#socketPosition} должен
 *  возвращать РОВНО ту же точку, что {@link SchemaCanvasPanel#drawPin} нарисовал по
 *  {@link NodePortLayout}, иначе конец связи визуально не попадал бы в гнездо, а клик
 *  по видимому гнезду не находил бы его ({@link SchemaCanvasPanel#socketAt} использует
 *  ту же {@link SchemaCanvasPanel#nodeLayoutForTest}, поэтому это покрывает и хит-тест). */
class SchemaSocketPositionTest {

    private static SchemaCanvasPanel canvas(AppModel model, SchemaMode mode, Path dir, String name) {
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), name + ".json")));
        return new SchemaCanvasPanel(model, mode, settings);
    }

    private static Point expectedPoint(SchemaNode node, NodePortLayout.Pin pin) {
        return new Point((int) Math.round(node.getX() + pin.x()), (int) Math.round(node.getY() + pin.y()));
    }

    @Test
    void singleSlotPortResolvesToItsOwnPin(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SchemaCanvasPanel canvas = canvas(model, SchemaMode.SIGNAL, dir, "single");
        SchemaNode q8 = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "PixelHue Q8");
        CardPort genlockIn = SchemaFixtures.portOnCard(q8, "MVR", "Genlock Tri-Level");
        assertEquals(1, genlockIn.getCount(), "гнездо с count=1 всегда даёт ровно один пин");

        NodePortLayout.Pin pin = onlyPinFor(canvas, q8, genlockIn.getId());
        Point expected = expectedPoint(q8, pin);

        assertEquals(expected, canvas.socketPositionForTest(q8, genlockIn.getId(), null));
    }

    @Test
    void expandedGroupPicksThePinMatchingEachEdgeInCreationOrder(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "expanded.json")));
        // По умолчанию (ALWAYS_COLLAPSED) генлок был бы одним свёрнутым гнездом
        // независимо от связей — этому тесту нужна именно РАЗВЁРНУТАЯ группа
        // (несколько пинов на один CardPort), поэтому включаем AUTO явно (при
        // занятой группе AUTO разворачивает её, как и ALWAYS_EXPANDED).
        settings.setSignalGroupDisplay(com.vjstb.ledscheme.settings.GroupDisplayMode.AUTO);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settings);
        SchemaNode blackmagic = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "Blackmagic");
        CardPort genlockOut = SchemaFixtures.portOnCard(blackmagic, "Sync Generator", "Genlock (SDI)");

        List<SchemaEdge> edgesFromGenlock = new ArrayList<>();
        for (SchemaEdge e : model.getCurrentScene().getSchemaEdges()) {
            if (genlockOut.getId().equals(e.getFromPortId())) {
                edgesFromGenlock.add(e);
            }
        }
        assertTrue(edgesFromGenlock.size() > 1, "фикстура должна давать генлок с несколькими связями");

        List<NodePortLayout.Pin> pins = pinsFor(canvas, blackmagic, genlockOut.getId());
        assertTrue(pins.size() >= edgesFromGenlock.size(),
                "у раскладки должно быть хотя бы по одному пину на каждую связь этого гнезда");

        for (int i = 0; i < edgesFromGenlock.size(); i++) {
            Point expected = expectedPoint(blackmagic, pins.get(i));
            Point actual = canvas.socketPositionForTest(blackmagic, genlockOut.getId(), edgesFromGenlock.get(i));
            assertEquals(expected, actual, "ordinal " + i);
        }
    }

    @Test
    void unknownPortIdResolvesToNull(@TempDir Path dir) {
        AppModel model = SchemaFixtures.buildScene(dir);
        SchemaCanvasPanel canvas = canvas(model, SchemaMode.SIGNAL, dir, "unknown");
        SchemaNode cvt = SchemaFixtures.nodeByLabel(model, SchemaMode.SIGNAL, "CVT4K");

        assertNull(canvas.socketPositionForTest(cvt, "no-such-port", null));
    }

    private static NodePortLayout.Pin onlyPinFor(SchemaCanvasPanel canvas, SchemaNode node, String portId) {
        List<NodePortLayout.Pin> pins = pinsFor(canvas, node, portId);
        assertEquals(1, pins.size(), "ожидался ровно один пин для " + portId);
        return pins.get(0);
    }

    private static List<NodePortLayout.Pin> pinsFor(SchemaCanvasPanel canvas, SchemaNode node, String portId) {
        List<NodePortLayout.Pin> result = new ArrayList<>();
        NodePortLayout.Result layout = canvas.nodeLayoutForTest(node);
        assertNotNull(layout);
        for (NodePortLayout.Pin p : layout.pins()) {
            if (p.port().getId().equals(portId)) {
                result.add(p);
            }
        }
        return result;
    }
}
