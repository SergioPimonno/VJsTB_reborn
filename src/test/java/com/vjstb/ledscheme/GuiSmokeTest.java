package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.MainFrame;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Дымовой тест сборки UI: строит главное окно с реальными данными (проект, сцена,
 * экран, цепочка) и закрывает его, не показывая. Ловит ошибки связывания панелей.
 * Пропускается в headless-среде (без дисплея).
 */
class GuiSmokeTest {

    @Test
    void buildsMainFrameWithData(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");

        SwingUtilities.invokeAndWait(() -> {
            AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
            CabinetType ct = new CabinetType();
            ct.setName("Smoke 500");
            model.addCabinetType(ct);
            model.selectProject(model.addProject("P"));
            model.selectScene(model.addScene("S"));
            model.selectScreen(model.addScreen("E", ct.getId(), 2, 2, 0, 0));
            model.addPowerChain(1, java.util.List.of(model.getCurrentScreen().getCabinets().get(0).getId()));

            MainFrame frame = new MainFrame(model);
            frame.pack();
            frame.dispose();
        });
    }
}
