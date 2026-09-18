package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** docs/schema-ports-rework/PLAN.md, задача T3.4/D11: "защита от дурака" (см.
 *  {@link SchemaCanvasPanel}) не должна мешать соединять сетевое оборудование —
 *  роль {@link InterfaceRole#NETWORK} снимает проверку направления даже там, где
 *  оба гнезда СТРОГО одного однонаправленного направления (например, "Ethernet
 *  Cat6 OUT" у медиасервера в реальных пресетах, без IN_OUT). Без этой роли то же
 *  сочетание направлений по-прежнему должно ловиться — тест проверяет ОБА случая
 *  одной фикстурой, чтобы не потерять регресс на самой проверке направления. */
class SchemaNetworkDirectionErrorTest {

    private static AppModel model(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        return model;
    }

    private static SchemaCanvasPanel canvas(AppModel model, Path dir) {
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        return new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settings);
    }

    @Test
    void networkRoleOnEitherEndBypassesTheOutToOutCheck(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "A", 0, 0, null);
        CardPort aOut = new CardPort("Ethernet Cat6", PortDirection.OUT, 1);
        aOut.setRole(InterfaceRole.NETWORK);
        model.addCardToNode(a, "Сеть", java.util.List.of(aOut));

        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "B", 300, 0, null);
        CardPort bOut = new CardPort("Ethernet Cat6", PortDirection.OUT, 1);
        bOut.setRole(InterfaceRole.NETWORK);
        model.addCardToNode(b, "Сеть", java.util.List.of(bOut));

        SchemaCanvasPanel canvas = canvas(model, dir);
        assertNull(canvas.directionErrorForTest(a, aOut, b, bOut),
                "оба конца — роль NETWORK, ВЫХОД-ВЫХОД должен быть разрешён");
    }

    @Test
    void sameOutToOutWithoutNetworkRoleIsStillRejected(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 0, null);
        CardPort aOut = new CardPort("HDMI", PortDirection.OUT, 1);
        model.addCardToNode(a, "Видео", java.util.List.of(aOut));

        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 300, 0, null);
        CardPort bOut = new CardPort("HDMI", PortDirection.OUT, 1);
        model.addCardToNode(b, "Видео", java.util.List.of(bOut));

        SchemaCanvasPanel canvas = canvas(model, dir);
        assertNotNull(canvas.directionErrorForTest(a, aOut, b, bOut),
                "без роли NETWORK ВЫХОД-ВЫХОД должен по-прежнему запрещаться (регресс проверки)");
    }
}
