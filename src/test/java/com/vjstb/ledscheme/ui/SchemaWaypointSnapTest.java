package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EdgeWaypoint;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Отзыв пользователя 2026-09-16 (docs/schema-ports-rework/DIALOG.md): Shift-перетаскивание
 *  точки излома провода "срабатывало очень криво, непонятно куда" после переноса гнёзд на
 *  рамку блока (T3.2) — единственным крупным ориентиром для привязки остались точки ДРУГИХ
 *  линий, а рамки блоков (кроме их же собственных гнёзд) кандидатами не были вовсе. Тест
 *  проверяет саму причину и её починку: {@link SchemaCanvasPanel#snapWaypointPositionForTest}
 *  должен подхватывать сторону СТОРОННЕГO узла (даже без единой связи с перетаскиваемой
 *  линией) как кандидат, а не только точки других проводов. */
class SchemaWaypointSnapTest {

    private static AppModel model(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        return model;
    }

    private static SchemaCanvasPanel canvas(AppModel model, Path dir) {
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        return new SchemaCanvasPanel(model, SchemaMode.POWER, settings);
    }

    @Test
    void snapsWaypointToBorderOfANodeNotConnectedToThisEdgeAtAll(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "A", 0, 200, null);
        CardPort aOut = model.addPowerConnectorToNode(a, "CEE 32A", PortDirection.OUT, 1);
        SchemaNode b = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "B", 500, 200, null);
        CardPort bIn = model.addPowerConnectorToNode(b, "CEE 32A", PortDirection.IN, 1);
        // "C" не связан НИ ОДНИМ проводом с A/B — единственный источник его границ как
        // ориентира это НОВЫЙ кандидат "рамка узла", не "точки других линий" (их для C нет).
        SchemaNode c = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "C", 200, 0, null);
        model.addPowerConnectorToNode(c, "CEE 16A", PortDirection.OUT, 1);

        SchemaEdge edge = model.addSchemaEdge(SchemaMode.POWER, a.getId(), aOut.getId(), b.getId(), bIn.getId(), null);
        model.setSchemaEdgeWaypoints(edge, List.of(new EdgeWaypoint(250, 210)));

        SchemaCanvasPanel canvas = canvas(model, dir);
        double bottomOfC = c.getY() + c.getHeight();
        assertTrue(Math.abs(bottomOfC - 210.0) > 5,
                "фикстура должна давать нижнюю грань C заметно НЕ там, где уже стоит излом");

        // Курсор рядом (в пределах порога 10px) с нижней гранью C по Y, X — там же, где
        // излом уже стоит (чтобы не зависеть от снапа по X вовсе).
        double candidateX = 250;
        double candidateY = bottomOfC + 3;
        double[] snapped = canvas.snapWaypointPositionForTest(edge, 0, candidateX, candidateY);

        assertEquals(bottomOfC, snapped[1], 0.001,
                "излом должен притянуться к нижней грани стороннего узла C, а не остаться там, где курсор");
    }

    @Test
    void doesNotSnapToTheCenterOfANode(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "A", 0, 200, null);
        CardPort aOut = model.addPowerConnectorToNode(a, "CEE 32A", PortDirection.OUT, 1);
        SchemaNode b = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "B", 500, 200, null);
        CardPort bIn = model.addPowerConnectorToNode(b, "CEE 32A", PortDirection.IN, 1);
        SchemaNode c = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "C", 200, 0, null);
        model.addPowerConnectorToNode(c, "CEE 16A", PortDirection.OUT, 1);

        SchemaEdge edge = model.addSchemaEdge(SchemaMode.POWER, a.getId(), aOut.getId(), b.getId(), bIn.getId(), null);
        double centerY = c.getY() + c.getHeight() / 2.0;
        // Курсор специально далеко (>10px) от ЛЮБОЙ грани C, но у самого его центра —
        // до починки (баг-репорт) центр блока тоже притягивал, чего мы сознательно не
        // возвращаем (см. javadoc snapWaypointPosition).
        model.setSchemaEdgeWaypoints(edge, List.of(new EdgeWaypoint(400, centerY)));

        SchemaCanvasPanel canvas = canvas(model, dir);
        assertTrue(c.getHeight() > 30, "нужен узел ощутимой высоты, чтобы верх/низ не путались с центром в пределах порога");
        double candidateX = 400;
        // В пределах порога (10px) ТОЛЬКО от центра — до ближайшей грани c дальше 10px.
        double candidateY = centerY + 3;
        double[] snapped = canvas.snapWaypointPositionForTest(edge, 0, candidateX, candidateY);

        assertTrue(Math.abs(snapped[1] - centerY) > 0.001, "центр узла НЕ должен быть кандидатом привязки");
    }
}
