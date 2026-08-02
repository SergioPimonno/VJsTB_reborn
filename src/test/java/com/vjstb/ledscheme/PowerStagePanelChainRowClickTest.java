package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.stage.PowerStagePanel;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Проверяет клик по строке цепочки в списке через РЕАЛЬНУЮ доставку события Swing
 * (SwingUtilities.getDeepestComponentAt + dispatchEvent на найденный компонент), а
 * не через прямой вызов слушателя на row — Swing доставляет MouseEvent САМОМУ
 * ГЛУБОКОМУ компоненту под курсором (обычно это JLabel текста строки, который
 * BorderLayout растягивает на всю видимую площадь), а не всплывает к JPanel-родителю,
 * если тот же слушатель не добавлен и на дочерние компоненты явно (см. Task #11/v1.6 —
 * этим же приёмом чинился и ПКМ-цвет цепочки из Task #4, который страдал тем же
 * незамеченным багом: тестировался раньше только вызовом AppModel напрямую, никогда
 * реальной доставкой события).
 */
class PowerStagePanelChainRowClickTest {

    private JLabel findLabelContaining(Component root, String needle) {
        if (root instanceof JLabel l && l.getText() != null && l.getText().contains(needle)) {
            return l;
        }
        if (root instanceof java.awt.Container c) {
            for (Component child : c.getComponents()) {
                JLabel found = findLabelContaining(child, needle);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void clickAt(Component target, int x, int y) {
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_RELEASED,
                System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    @Test
    void clickingChainRowLabelResumesEditingOfThatChain(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");

        SwingUtilities.invokeAndWait(() -> {
            AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
            CabinetType ct = new CabinetType();
            ct.setName("RowClick 500");
            ct.setWidthMm(500);
            ct.setHeightMm(500);
            model.addCabinetType(ct);
            model.selectProject(model.addProject("P"));
            model.selectScene(model.addScene("S"));
            Screen scr = model.addScreen("E", ct.getId(), 2, 2, 0, 0);
            model.selectScreen(scr);
            String a = scr.cabinetAt(0, 0).getId();
            String b = scr.cabinetAt(0, 1).getId();
            model.addPowerChain(1, List.of(a));
            PowerChain chain = model.getCurrentScene().getPowerChains().get(0);

            SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
            PowerStagePanel panel = new PowerStagePanel(model, settings);

            JFrame frame = new JFrame();
            frame.setContentPane(panel);
            // pack() реализует peers и прогоняет layout (validate) — реальные
            // границы дочерних компонентов доступны без показа окна на экране
            // (setVisible не нужен: Container.findComponentAt смотрит на isVisible()
            // компонентов, а не на isShowing()/наличие на реальном дисплее).
            frame.pack();
            try {
                JLabel rowLabel = findLabelContaining(panel, "L1");
                assertTrue(rowLabel != null, "Не нашли JLabel строки цепочки в дереве компонентов панели");
                assertTrue(rowLabel.getWidth() > 0 && rowLabel.getHeight() > 0,
                        "Строка должна получить реальные размеры после layout, иначе координаты клика бессмысленны");

                Point center = new Point(rowLabel.getWidth() / 2, rowLabel.getHeight() / 2);
                Component deepest = SwingUtilities.getDeepestComponentAt(rowLabel, center.x, center.y);
                Point local = SwingUtilities.convertPoint(rowLabel, center, deepest);
                clickAt(deepest, local.x, local.y);

                assertTrue(panel.chainController().isChainBuilding(),
                        "Клик по строке цепочки должен войти в режим редактирования (resumeEditing)");
                assertEquals(List.of(a), panel.chainController().activeChainCabIds());

                // Достроить и завершить — должна обновиться ТА ЖЕ цепочка (тот же id), а
                // не появиться вторая новая запись.
                panel.chainController().cabinetClicked(b);
                panel.chainController().finish();

                List<PowerChain> chains = model.getCurrentScene().getPowerChains();
                assertEquals(1, chains.size(), "Редактирование не должно плодить вторую цепочку");
                assertEquals(chain.getId(), chains.get(0).getId());
                assertEquals(List.of(a, b), chains.get(0).getCabinetInstanceIds());
            } finally {
                frame.dispose();
            }
        });
    }
}
