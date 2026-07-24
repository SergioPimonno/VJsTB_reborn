package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.stage.SignalStagePanel;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Регрессия: выбор порта (хоткей/кнопка), а затем клик по кабинету должен
 * реально начать и сохранить цепочку сигнала (см. {@link SignalStagePanel#selectPortByHotkey}).
 * До фикса {@code refresh()} безусловно обнулял только что выбранный порт сразу
 * после его установки (вызывалось и как слушатель модели, и явно из
 * {@code selectPort}), из-за чего клик по кабинету никогда не находил активный
 * порт и построение цепочки молча не начиналось.
 */
class SignalStagePanelTest {

    @Test
    void selectingPortThenClickingCabinetsBuildsChain(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");

        SwingUtilities.invokeAndWait(() -> {
            AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
            CabinetType ct = new CabinetType();
            ct.setName("T 500x500");
            model.addCabinetType(ct);

            ControllerType controllerType = new ControllerType();
            controllerType.setName("VX1000");
            controllerType.setPortCount(4);
            model.addControllerType(controllerType);

            model.selectProject(model.addProject("P"));
            model.selectScene(model.addScene("S"));
            Screen scr = model.addScreen("E", ct.getId(), 2, 2, 0, 0);
            model.selectScreen(scr);
            model.addControllerToScreen(scr, controllerType.getId());

            SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
            SignalStagePanel panel = new SignalStagePanel(model, settings);

            panel.selectPortByHotkey(1);
            String cab1 = scr.getCabinets().get(0).getId();
            String cab2 = scr.getCabinets().get(1).getId();
            panel.chainController().cabinetClicked(cab1);
            panel.chainController().cabinetClicked(cab2);
            panel.chainController().finish();

            assertEquals(1, model.getCurrentScene().getSignalChains().size());
            SignalChain chain = model.getCurrentScene().getSignalChains().get(0);
            assertEquals(1, chain.getPortNumber());
            assertEquals(2, chain.getCabinetInstanceIds().size());
        });
    }
}
