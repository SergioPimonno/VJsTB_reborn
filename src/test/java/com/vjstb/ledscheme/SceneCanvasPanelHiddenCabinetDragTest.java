package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.SceneCanvasPanel;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Баг-репорт: "удалённые кабинеты не должны перемещаться" — скрытый ("удалённый",
 *  {@link CabinetInstance#isHidden()}) кабинет невидим, но хит-тест ({@code
 *  SceneCanvasPanel#screenAndCabinetAt}) всё равно его находил, и левый клик-
 *  перетаскивание по пустому на вид месту молча двигало его через offsetXMm/
 *  offsetYMm — он появлялся сдвинутым при последующем восстановлении. Тест шлёт
 *  РЕАЛЬНЫЕ MouseEvent на панель (press → drag → release), как {@code
 *  PowerStagePanelChainRowClickTest}. */
class SceneCanvasPanelHiddenCabinetDragTest {

    private void send(SceneCanvasPanel panel, int id, int x, int y) {
        panel.dispatchEvent(new MouseEvent(panel, id, System.currentTimeMillis(), 0, x, y, 1, false,
                MouseEvent.BUTTON1));
    }

    @Test
    void draggingOverAHiddenCabinetDoesNotMoveIt(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));

        CabinetType type = new CabinetType();
        type.setName("Wide 500x500");
        type.setWidthMm(500);
        type.setHeightMm(500);
        model.addCabinetType(type);

        Screen screen = model.addScreen("E", type.getId(), 1, 1, 0, 0);
        model.selectScreen(screen);
        CabinetInstance cab = screen.getCabinets().get(0);
        model.toggleCabinetHidden(cab.getId());
        assertEquals(0.0, cab.getOffsetXMm(), 1e-9);
        assertEquals(0.0, cab.getOffsetYMm(), 1e-9);

        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        SceneCanvasPanel canvas = new SceneCanvasPanel(model, settings);
        canvas.setDetailMode(true, false);
        canvas.setSize(300, 300);

        // Та же геометрия, что в SceneCanvasPanelOverrideMarksRenderTest: сетка
        // начинается в (40,40), шаг ячейки 500мм×0.25=125px — клик и протяжка
        // внутри неё, ровно там, где невидимый (скрытый) кабинет физически стоит.
        int x0 = 40 + 60, y0 = 40 + 60;
        send(canvas, MouseEvent.MOUSE_PRESSED, x0, y0);
        send(canvas, MouseEvent.MOUSE_DRAGGED, x0 + 40, y0 + 30);
        send(canvas, MouseEvent.MOUSE_DRAGGED, x0 + 80, y0 + 60);
        send(canvas, MouseEvent.MOUSE_RELEASED, x0 + 80, y0 + 60);

        assertEquals(0.0, cab.getOffsetXMm(), 1e-9,
                "перетаскивание по невидимому скрытому кабинету не должно менять его позицию");
        assertEquals(0.0, cab.getOffsetYMm(), 1e-9);
    }
}
