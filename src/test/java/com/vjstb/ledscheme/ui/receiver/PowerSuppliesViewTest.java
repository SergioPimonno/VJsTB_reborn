package com.vjstb.ledscheme.ui.receiver;

import static com.vjstb.ledscheme.ui.receiver.ReceiverViewFixture.click;
import static com.vjstb.ledscheme.ui.receiver.ReceiverViewFixture.drag;
import static com.vjstb.ledscheme.ui.receiver.ReceiverViewFixture.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.PowerSupplyInstance;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.Screen;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Вид «Блоки питания» (docs/receiver-cards/PLAN.md, 6.2) на настоящем {@code AppModel}: ручной и автоматический
 * блок, замена, место блока, Delete, замечания. Жесты — реальные {@code MouseEvent} на холст.
 */
class PowerSuppliesViewTest {

    @BeforeEach
    void needsDisplay() {
        assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
    }

    private static PowerSupplyInstance onlySupply(Screen s) {
        assertEquals(1, s.getPowerSupplies().size());
        return s.getPowerSupplies().get(0);
    }

    @Test
    void buildsAndPaintsWithBlocksCardsAndCables(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        f.model.assignReceiverCardsManual(new GridRect(0, 0, 1, 2), f.cardType.getId());
        onEdt(() -> {
            PowerSuppliesView view = new PowerSuppliesView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                assertTrue(view.canvas().hasContent());
                assertEquals(f.psuType.getId(), view.assignPanel().selectedTypeId());
                drag(view.canvas(), 0, 0, 1, 2);
                view.cablesCheck().doClick();
                BufferedImage img = new BufferedImage(ReceiverViewFixture.WIDTH, ReceiverViewFixture.HEIGHT,
                        BufferedImage.TYPE_INT_ARGB);
                var g = img.createGraphics();
                view.canvas().paint(g);
                g.dispose();
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void manualDragPlacesOneBlockAsOneUndoStep(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        onEdt(() -> {
            PowerSuppliesView view = new PowerSuppliesView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                int depth = f.model.undoDepth();
                drag(view.canvas(), 0, 0, 1, 2);
                PowerSupplyInstance s = onlySupply(f.screen());
                assertEquals(6, s.getModuleIds().size());
                assertEquals(f.psuType.getId(), s.getPowerSupplyTypeId());
                assertEquals(depth + 1, f.model.undoDepth());
                assertEquals(s.getId(), view.activeSupplyId());
                assertEquals(1, view.assignPanel().supplyList().getModel().getSize());
                assertTrue(view.assignPanel().supplyList().getModel().getElementAt(0).left().startsWith("БП1 · Блок Б"));
                assertEquals("6/8", view.assignPanel().supplyList().getModel().getElementAt(0).right());

                f.model.undo();
                assertEquals(0, f.screen().getPowerSupplies().size());
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void autoDragSplitsAreaAndShowsChosenSize(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        onEdt(() -> {
            PowerSuppliesView view = new PowerSuppliesView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                view.assignPanel().autoRadio().doClick();
                int depth = f.model.undoDepth();
                drag(view.canvas(), 0, 0, 3, 5);

                int blocks = f.screen().getPowerSupplies().size();
                assertTrue(blocks >= 3, "24 модулей по ≤8 на блок — не меньше трёх блоков");
                assertEquals(depth + 1, f.model.undoDepth(), "авторазбиение — одна запись отмены");
                int covered = f.screen().getPowerSupplies().stream().mapToInt(s -> s.getModuleIds().size()).sum();
                assertEquals(24, covered, "каждый модуль области попал в блок");
                Matcher m = Pattern.compile("Подобрано: (\\d+)×(\\d+) мод\\. на блок, блоков: (\\d+)")
                        .matcher(view.assignPanel().autoInfoText());
                assertTrue(m.matches(), view.assignPanel().autoInfoText());
                assertEquals(blocks, Integer.parseInt(m.group(3)));
                assertTrue(Integer.parseInt(m.group(1)) * Integer.parseInt(m.group(2)) <= 8, "не больше числа шлейфов");

                int again = f.model.undoDepth();
                drag(view.canvas(), 0, 0, 3, 5);
                assertEquals(again, f.model.undoDepth(), "повторная протяжка с тем же результатом — ничего");
                assertEquals(blocks, f.screen().getPowerSupplies().size());
                assertEquals(m.group(0), view.assignPanel().autoInfoText(), "строка «Подобрано» не пропала");
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void replacingPartOfABlockReportsOrphans(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        onEdt(() -> {
            PowerSuppliesView view = new PowerSuppliesView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                drag(view.canvas(), 0, 0, 1, 1);
                drag(view.canvas(), 0, 1, 1, 1);
                assertEquals(2, onlySupply(f.screen()).getModuleIds().size());
                assertEquals("Заменено блоков: 1, модулей без питания: 2", view.note());
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void placeToolMovesBlockHostAndClickSelectsBlock(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        onEdt(() -> {
            PowerSuppliesView view = new PowerSuppliesView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                drag(view.canvas(), 0, 0, 0, 2);
                drag(view.canvas(), 1, 0, 1, 2);
                assertEquals(2, f.screen().getPowerSupplies().size());
                String first = f.screen().getPowerSupplies().get(0).getId();

                click(view.canvas(), 0, 1);
                assertEquals(first, view.activeSupplyId(), "клик по модулю блока выбирает блок");
                assertEquals(2, f.screen().getPowerSupplies().size());

                view.hostButton().doClick();
                assertEquals(PowerSuppliesView.Tool.HOST, view.tool());
                click(view.canvas(), 0, 2);
                assertEquals(f.id(0, 2), f.screen().powerSupplyById(first).getHostModuleId());
                assertEquals(first, view.activeSupplyId());
                click(view.canvas(), 3, 3); // модуль без блока — ничего
                assertEquals(2, f.screen().getPowerSupplies().size());
                view.hostButton().doClick();
                assertEquals(PowerSuppliesView.Tool.SELECT, view.tool());
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void deleteKeyAndButtonRemoveActiveBlock(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        onEdt(() -> {
            PowerSuppliesView view = new PowerSuppliesView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                drag(view.canvas(), 0, 0, 1, 1);
                Object key = view.canvas().getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                        .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0));
                assertNotNull(key);
                view.canvas().getActionMap().get(key).actionPerformed(new ActionEvent(view.canvas(), 0, ""));
                assertEquals(0, f.screen().getPowerSupplies().size());
                assertEquals("Блок снят, модулей без питания: 4", view.note());
                assertNull(view.activeSupplyId());
                assertFalse(view.removeButton().isEnabled());

                drag(view.canvas(), 0, 0, 1, 1);
                view.removeButton().doClick();
                assertEquals(0, f.screen().getPowerSupplies().size());
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void warningsAndRowsShowOverloadAndLongCables(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        PowerSupplyType tiny = new PowerSupplyType();
        tiny.setName("Блок мини");
        tiny.setOutputs(2);
        tiny.setCableLengthMm(300.0);
        f.model.addPowerSupplyType(tiny);
        onEdt(() -> {
            PowerSuppliesView view = new PowerSuppliesView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                view.assignPanel().typeList().setSelectedValue(tiny, false);
                drag(view.canvas(), 0, 0, 1, 1);

                var texts = view.warningsBar().warnings().stream().map(ReceiverWarningsBar.Warning::text).toList();
                assertTrue(texts.stream().anyMatch(t -> t.contains("4/2")), "перегруз: " + texts);
                assertTrue(texts.stream().anyMatch(t -> t.contains("длиннее 300 мм")), "кабель: " + texts);
                assertTrue(texts.stream().anyMatch(t -> t.startsWith("Модулей без питания: 20")), texts.toString());
                var row = view.assignPanel().supplyList().getModel().getElementAt(0);
                assertTrue(row.right().startsWith("4/2"), row.right());
                assertTrue(row.right().contains("⚠"), row.right());
                assertTrue(row.warn());
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void cardInTheAreaIsFedByTheBlockAndShownInItsRow(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        PowerSupplyType withK = new PowerSupplyType();
        withK.setName("Блок с К");
        withK.setOutputs(8);
        withK.setCardOutput(true);
        withK.setRatedPowerW(300.0);
        f.model.addPowerSupplyType(withK);
        f.model.assignReceiverCardsManual(new GridRect(0, 0, 1, 1), f.cardType.getId());
        onEdt(() -> {
            PowerSuppliesView view = new PowerSuppliesView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                drag(view.canvas(), 0, 0, 1, 1); // тип Блок Б — без отдельного выхода: карта забирает шлейф
                var row = view.assignPanel().supplyList().getModel().getElementAt(0);
                assertEquals("БП1 · Блок Б · К1", row.left());
                assertEquals("5/8", row.right());

                view.assignPanel().typeList().setSelectedValue(withK, false);
                drag(view.canvas(), 0, 0, 1, 1); // заменить блоком с выходом «К»
                row = view.assignPanel().supplyList().getModel().getElementAt(0);
                assertEquals("БП1 · Блок с К · К1", row.left());
                assertEquals("4/8 +К", row.right());
            } finally {
                frame.dispose();
            }
        });
    }
}
