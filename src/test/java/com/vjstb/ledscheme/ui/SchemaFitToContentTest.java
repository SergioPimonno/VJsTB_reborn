package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.Scene;
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

/** Баг-репорт 2026-10-02 («почему перезапуск заставляет блоки так растягиваться?»): вчерашняя
 *  одноразовая «подгонка размеров блоков при открытии сцены» растянула блоки, размер которых
 *  пользователь задал руками, до минимума под ВСЕ развёрнутые группы (H2 стал 1764 px высотой).
 *  Подгонку при открытии убрали — этот тест фиксирует, что открытие сцены размеров не трогает;
 *  для починки уже раздутых блоков есть явное «Подогнать размер» (по текущим настройкам групп). */
class SchemaFitToContentTest {

    private static SchemaNode controllerWithCards(AppModel model) {
        SchemaNode n = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "H2", 40, 40, null);
        model.addCardToNode(n, "Output card", List.of(
                new CardPort("Fiber MMF LC", PortDirection.OUT, 2), new CardPort("Ethernet Cat5e", PortDirection.OUT, 16)));
        return n;
    }

    @Test
    void openingASceneDoesNotResizeHandSizedBlocks(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        SchemaNode n = controllerWithCards(model);
        n.setWidth(215);
        n.setHeight(172); // пользователь сжал блок вручную, группы в схеме свёрнуты

        model.selectScene(scene);

        assertEquals(215, n.getWidth());
        assertEquals(172, n.getHeight(), "открытие сцены не должно растягивать блок");
    }

    @Test
    void fitToContentShrinksAnInflatedBlockAndIsOneUndoStep(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "s.json")));
        SchemaNode n = controllerWithCards(model);
        n.setWidth(493);
        n.setHeight(1764);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settings);

        int fitted = canvas.fitNodesToContent(List.of(n));

        assertEquals(1, fitted);
        assertTrue(n.getHeight() < 1764, "раздутый блок усаживается до размера под содержимое: " + n.getHeight());
        assertTrue(n.getHeight() >= 44);

        model.undo();
        SchemaNode restored = model.getCurrentScene().getSchemaNodes().stream()
                .filter(x -> x.getId().equals(n.getId())).findFirst().orElseThrow();
        assertEquals(1764, restored.getHeight(), "подгонка отменяется одной записью");
    }
}
