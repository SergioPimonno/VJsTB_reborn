package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ChainLinks;
import com.vjstb.ledscheme.service.ChainUnits;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.SchemeRenderer;
import com.vjstb.ledscheme.ui.stage.PowerStagePanel;
import com.vjstb.ledscheme.ui.stage.SignalStagePanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Этапы «Сигнал» и «Питание» на модульном экране (docs/receiver-cards/PLAN.md, трек D): кнопки видов «Приёмные карты»
 * / «Блоки питания» (D2) видны только при модульном экране сцены и стоят перед «Расключением экрана»; строки правой
 * панели «3 карты, 2 каб.» (D4); подложка «Расключения» рисуется на модульном экране и не рисуется на обычном.
 */
class ModularStageViewsTest {

    private AppModel model;
    private SettingsManager settings;
    private Screen screen;
    private Scene scene;

    private static GridRect rect(int r0, int c0, int r1, int c1) {
        return new GridRect(r0, c0, r1, c1);
    }

    private CabinetType moduleType() {
        CabinetType module = new CabinetType();
        module.setName("Модуль");
        module.setWidthMm(250);
        module.setHeightMm(250);
        module.setResolutionWidth(64);
        module.setResolutionHeight(64);
        module.setPowerConsumptionW(30);
        module.setReceiverCardSeparate(true);
        return module;
    }

    private CabinetType plainType() {
        CabinetType ct = new CabinetType();
        ct.setName("Обычный");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setPowerConsumptionW(100);
        return ct;
    }

    private void setUp(Path dir, boolean modular) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
        settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        CabinetType type = modular ? moduleType() : plainType();
        model.addCabinetType(type);
        model.selectProject(model.addProject("P"));
        scene = model.addScene("S");
        model.selectScene(scene);
        screen = model.addScreen("E", type.getId(), 2, 4, 0, 0);
        model.selectScreen(screen);
    }

    private static void collect(Component c, List<Component> out) {
        out.add(c);
        if (c instanceof Container ct) {
            for (Component child : ct.getComponents()) {
                collect(child, out);
            }
        }
    }

    private static JToggleButton toggle(Component root, String text) {
        List<Component> all = new ArrayList<>();
        collect(root, all);
        for (Component c : all) {
            if (c instanceof JToggleButton b && text.equals(b.getText())) {
                return b;
            }
        }
        return null;
    }

    private static boolean hasLabel(Component root, String fragment) {
        List<Component> all = new ArrayList<>();
        collect(root, all);
        for (Component c : all) {
            if (c instanceof JLabel l && l.getText() != null && l.getText().contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ видимость кнопок видов

    @Test
    void cardViewButtonOnlyWithModularScreenAndBeforeChainView(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        SwingUtilities.invokeAndWait(() -> {
            setUp(dir, false);
            SignalStagePanel panel = new SignalStagePanel(model, settings);
            JToggleButton cards = toggle(panel, "Приёмные карты");
            JToggleButton chain = toggle(panel, "Расключение экрана");
            assertNotNull(cards);
            assertNotNull(chain);
            assertFalse(cards.isVisible(), "на немодульной сцене кнопки нет");

            // появился модульный экран — кнопка возникает; стоит перед «Расключением экрана»
            CabinetType module = moduleType();
            model.addCabinetType(module);
            Screen modular = model.addScreen("M", module.getId(), 1, 2, 5000, 0);
            model.selectScreen(modular);
            assertTrue(cards.isVisible());
            Container row = cards.getParent();
            assertEquals(row, chain.getParent());
            assertTrue(row.getComponentZOrder(cards) < row.getComponentZOrder(chain), "перед «Расключением экрана»");

            // баг-репорт: на ОБЫЧНОМ экране той же сцены кнопка не нужна — нужен именно выбранный модульный экран
            model.selectScreen(screen);
            assertFalse(cards.isVisible(), "выбран обычный экран сцены с модульным — кнопки нет");
            model.selectScreen(modular);
            assertTrue(cards.isVisible());

            // выбрана и пропала — возврат на «Расключение»
            cards.doClick();
            assertTrue(cards.isSelected());
            model.selectScreen(screen);
            assertFalse(cards.isVisible());
            assertTrue(chain.isSelected(), "выбран обычный экран — возврат на «Расключение экрана»");
            model.selectScreen(modular);
            cards.doClick();
            model.deleteScreen(modular);
            assertFalse(cards.isVisible());
            assertTrue(chain.isSelected(), "возврат на «Расключение экрана»");
        });
    }

    @Test
    void suppliesViewButtonOnlyWithModularScreenAndBeforeChainView(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        SwingUtilities.invokeAndWait(() -> {
            setUp(dir, false);
            PowerStagePanel panel = new PowerStagePanel(model, settings);
            JToggleButton supplies = toggle(panel, "Блоки питания");
            JToggleButton chain = toggle(panel, "Расключение экрана");
            assertNotNull(supplies);
            assertFalse(supplies.isVisible());

            CabinetType module = moduleType();
            model.addCabinetType(module);
            Screen modular = model.addScreen("M", module.getId(), 1, 2, 5000, 0);
            model.selectScreen(modular);
            assertTrue(supplies.isVisible());
            Container row = supplies.getParent();
            assertTrue(row.getComponentZOrder(supplies) < row.getComponentZOrder(chain));

            model.selectScreen(screen);
            assertFalse(supplies.isVisible(), "выбран обычный экран сцены с модульным — кнопки нет");
            model.selectScreen(modular);
            assertTrue(supplies.isVisible());

            supplies.doClick();
            model.selectScreen(screen);
            assertFalse(supplies.isVisible());
            assertTrue(chain.isSelected());
            model.selectScreen(modular);
            supplies.doClick();
            model.deleteScreen(modular);
            assertFalse(supplies.isVisible());
            assertTrue(chain.isSelected());
        });
    }

    @Test
    void ordinaryScreenKeepsPlainButtons(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        SwingUtilities.invokeAndWait(() -> {
            setUp(dir, false);
            SignalStagePanel signal = new SignalStagePanel(model, settings);
            PowerStagePanel power = new PowerStagePanel(model, settings);
            assertNotNull(toggle(signal, "Расключение экрана"));
            assertFalse(toggle(signal, "Приёмные карты").isVisible());
            assertFalse(toggle(power, "Блоки питания").isVisible());
            assertNotNull(toggle(signal, "Общая схема сигнала"));
            assertNotNull(toggle(power, "Общая схема питания"));
        });
    }

    // ------------------------------------------------------------------ строки правой панели

    @Test
    void rightPanelRowsCountUnitsInWords(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        SwingUtilities.invokeAndWait(() -> {
            setUp(dir, true);
            ReceiverCardType cardType = new ReceiverCardType();
            cardType.setName("К8");
            cardType.setModuleOutputs(8);
            model.addReceiverCardType(cardType);
            PowerSupplyType psuType = new PowerSupplyType();
            psuType.setName("БП8");
            psuType.setOutputs(8);
            psuType.setRatedPowerW(300.0);
            model.addPowerSupplyType(psuType);
            CabinetType ordinary = plainType();
            model.addCabinetType(ordinary);
            model.assignReceiverCardsManual(rect(0, 0, 0, 1), cardType.getId());
            model.assignReceiverCardsManual(rect(1, 0, 1, 1), cardType.getId());
            model.assignPowerSuppliesManual(rect(0, 0, 1, 1), psuType.getId());
            model.assignPowerSuppliesManual(rect(0, 2, 1, 2), psuType.getId());
            String plain = screen.cabinetAt(0, 3).getId();
            model.setCabinetTypeOverride(plain, ordinary.getId());

            SignalStagePanel signal = new SignalStagePanel(model, settings);
            PowerStagePanel power = new PowerStagePanel(model, settings);
            assertFalse(hasLabel(signal, "карты, 1 каб."));
            model.addSignalChain(1, false, List.of(
                    ChainLinks.unitContaining(screen, screen.cabinetAt(0, 0).getId(), ChainUnits.Mode.SIGNAL),
                    ChainLinks.unitContaining(screen, screen.cabinetAt(1, 0).getId(), ChainUnits.Mode.SIGNAL),
                    plain));
            model.addPowerChain(1, List.of(
                    ChainLinks.unitContaining(screen, screen.cabinetAt(0, 0).getId(), ChainUnits.Mode.POWER),
                    ChainLinks.unitContaining(screen, screen.cabinetAt(0, 2).getId(), ChainUnits.Mode.POWER),
                    plain));

            // 2 карты по 2 модуля (4 × 4096) + обычный кабинет 128×128
            assertTrue(hasLabel(signal, "2 карты, 1 каб."), "строка цепочки сигнала словами");
            assertTrue(hasLabel(signal, "32768 px"), "пиксели: сумма модулей карт и кабинета");
            assertTrue(hasLabel(power, "2 блока, 1 каб."), "строка цепочки питания словами");
        });
    }

    @Test
    void ordinaryRowsStayThePreviousWording(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        SwingUtilities.invokeAndWait(() -> {
            setUp(dir, false);
            SignalStagePanel signal = new SignalStagePanel(model, settings);
            PowerStagePanel power = new PowerStagePanel(model, settings);
            model.addSignalChain(1, false, List.of(screen.cabinetAt(0, 0).getId(), screen.cabinetAt(0, 1).getId()));
            model.addPowerChain(1, List.of(screen.cabinetAt(0, 0).getId(), screen.cabinetAt(0, 1).getId()));
            assertTrue(hasLabel(signal, " · 2 каб. · "));
            assertTrue(hasLabel(power, "L1 · 2 каб. · "));
        });
    }

    // ------------------------------------------------------------------ подложка «Расключения»

    private static int greenish(BufferedImage img) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                if (g > r + 15 && g > b + 15) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    void signalRenderPaintsCardsOnModularScreenOnly(@TempDir Path dir) {
        setUp(dir, true);
        ReceiverCardType cardType = new ReceiverCardType();
        cardType.setName("К8");
        cardType.setModuleOutputs(8);
        model.addReceiverCardType(cardType);
        model.assignReceiverCardsManual(rect(0, 0, 1, 1), cardType.getId());
        model.assignReceiverCardsManual(rect(0, 2, 1, 3), cardType.getId());
        String a = ChainLinks.unitContaining(screen, screen.cabinetAt(0, 0).getId(), ChainUnits.Mode.SIGNAL);
        model.addSignalChain(1, false, List.of(a));

        BufferedImage img = SchemeRenderer.renderImage(screen, model.typeOf(screen), false, 60, model.getWorkspace(),
                model.powerChainsTouchingScreen(screen), model.signalChainsTouchingScreen(screen),
                model.controllersInScene(screen), false, 1.0);

        assertNotNull(img);
        assertTrue(greenish(img) > 200, "карты зелёной подложкой поверх серых модулей");
    }

    @Test
    void renderOfOrdinaryScreenHasNoOverlay(@TempDir Path dir) {
        setUp(dir, false);
        BufferedImage signal = SchemeRenderer.renderImage(screen, model.typeOf(screen), false, 60,
                model.getWorkspace(), List.of(), List.of(), List.of(), false, 1.0);
        BufferedImage power = SchemeRenderer.renderImage(screen, model.typeOf(screen), true, 60,
                model.getWorkspace(), List.of(), List.of(), List.of(), false, 1.0);
        assertEquals(0, greenish(signal));
        assertEquals(0, greenish(power));
    }

    @Test
    void powerRenderDrawsSupplyAreasWithoutCards(@TempDir Path dir) {
        setUp(dir, true);
        ReceiverCardType cardType = new ReceiverCardType();
        cardType.setName("К8");
        cardType.setModuleOutputs(8);
        model.addReceiverCardType(cardType);
        PowerSupplyType psuType = new PowerSupplyType();
        psuType.setName("БП8");
        psuType.setOutputs(8);
        psuType.setRatedPowerW(300.0);
        model.addPowerSupplyType(psuType);
        model.assignReceiverCardsManual(rect(0, 0, 1, 3), cardType.getId());
        model.assignPowerSuppliesManual(rect(0, 0, 1, 3), psuType.getId());
        String s = ChainLinks.unitContaining(screen, screen.cabinetAt(0, 0).getId(), ChainUnits.Mode.POWER);
        model.addPowerChain(1, List.of(s));

        BufferedImage img = SchemeRenderer.renderImage(screen, model.typeOf(screen), true, 60, model.getWorkspace(),
                model.powerChainsTouchingScreen(screen), model.signalChainsTouchingScreen(screen),
                model.controllersInScene(screen), false, 1.0);

        assertNotNull(img);
        assertEquals(0, greenish(img), "карты в расключении питания не рисуются (D25)");
    }
}
