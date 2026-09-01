package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import com.vjstb.ledscheme.model.NetworkDeviceType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Тесты приоритета источников имени устройства (своя пометка > узел схемы >
 *  каталожный тип > заглушка) — общая логика для {@code
 *  ui.NetworkCanvasPanel} и {@code ui.NetworkAddressTableDialog}, см. class-
 *  javadoc {@link NetworkDeviceLabels}. */
class NetworkDeviceLabelsTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    @Test
    void fallsBackToPlaceholderWhenNothingResolves(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        NetworkDevicePlacement p = new NetworkDevicePlacement();

        assertEquals("(устройство)", NetworkDeviceLabels.resolveLabel(p, model));
    }

    @Test
    void customLabelTakesPriorityOverEverythingElse(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        SchemaNode node = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "Медиасервер", 0, 0, null);

        NetworkDevicePlacement p = new NetworkDevicePlacement();
        p.setLinkedSchemaNodeId(node.getId());
        p.setCustomLabel("Моя подпись");

        assertEquals("Моя подпись", NetworkDeviceLabels.resolveLabel(p, model));
    }

    @Test
    void resolvesLinkedSchemaNodeLabelLive(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        SchemaNode node = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "Медиасервер", 0, 0, null);

        NetworkDevicePlacement p = new NetworkDevicePlacement();
        p.setLinkedSchemaNodeId(node.getId());

        assertEquals("Медиасервер", NetworkDeviceLabels.resolveLabel(p, model));

        node.setLabel("Переименованный сервер");
        assertEquals("Переименованный сервер", NetworkDeviceLabels.resolveLabel(p, model),
                "переименование узла схемы должно сразу отразиться -- резолв живой, не копия");
    }

    @Test
    void resolvesCatalogTypeNameWhenNoLinkedNode(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        NetworkDeviceType type = new NetworkDeviceType();
        type.setName("Netgear GS108");
        NetworkDeviceType saved = model.addNetworkDeviceType(type);

        NetworkDevicePlacement p = new NetworkDevicePlacement();
        p.setDeviceTypeId(saved.getId());

        assertEquals("Netgear GS108", NetworkDeviceLabels.resolveLabel(p, model));
    }
}
