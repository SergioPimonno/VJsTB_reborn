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

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.ModuleNumberingPattern;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.ModuleNumbering;
import com.vjstb.ledscheme.service.ScreenModules;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JFrame;
import javax.swing.JTextField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Вид «Приёмные карты» (docs/receiver-cards/PLAN.md, 6.1) на настоящем {@code AppModel}: жесты подаются реальными
 * {@code MouseEvent} на холст ({@code dispatchEvent}), результат читается из данных экрана. Окно строится ({@code pack}),
 * но не показывается; без дисплея тесты пропускаются.
 */
class ReceiverCardsViewTest {

    @BeforeEach
    void needsDisplay() {
        assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
    }

    private ReceiverCardInstance onlyCard(Screen s) {
        assertEquals(1, s.getReceiverCards().size());
        return s.getReceiverCards().get(0);
    }

    @Test
    void buildsAndPaintsModularScreen(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        onEdt(() -> {
            ReceiverCardsView view = new ReceiverCardsView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                assertTrue(view.canvas().hasContent());
                assertEquals(f.cardType.getId(), view.assignPanel().selectedTypeId(), "первый тип выбран сам");
                BufferedImage img = new BufferedImage(ReceiverViewFixture.WIDTH, ReceiverViewFixture.HEIGHT,
                        BufferedImage.TYPE_INT_ARGB);
                var g = img.createGraphics();
                view.canvas().paint(g);
                g.dispose();
                // с картой, шлейфами и ручным порядком рисуется без исключений
                drag(view.canvas(), 0, 0, 1, 2);
                view.ribbonsCheck().doClick();
                view.canvas().paint(img.createGraphics());
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void manualDragPlacesOneCardAsOneUndoStep(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        onEdt(() -> {
            ReceiverCardsView view = new ReceiverCardsView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                int depth = f.model.undoDepth();
                drag(view.canvas(), 0, 0, 1, 2);

                ReceiverCardInstance card = onlyCard(f.screen());
                assertEquals(6, card.getModuleIds().size());
                assertEquals(f.cardType.getId(), card.getReceiverCardTypeId());
                assertEquals(depth + 1, f.model.undoDepth(), "одна протяжка — одна запись отмены");
                assertEquals(card.getId(), view.activeCardId(), "новая карта выбрана");
                assertEquals(1, view.assignPanel().cardsTable().getRowCount());
                assertTrue(view.assignPanel().cardsTable().getValueAt(0, 0).toString().startsWith("К1"));

                f.model.undo();
                assertEquals(0, f.screen().getReceiverCards().size());
                assertNull(view.activeCardId(), "после отмены выбранной карты уже нет");
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void clickOnEmptyModulePlacesSingleCardAndClickOnCardOnlySelectsIt(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        onEdt(() -> {
            ReceiverCardsView view = new ReceiverCardsView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                drag(view.canvas(), 0, 0, 0, 1);
                drag(view.canvas(), 1, 0, 1, 1);
                assertEquals(2, f.screen().getReceiverCards().size());
                String second = view.activeCardId();
                String first = cardOf(f.screen(), f.id(0, 0)).getId();

                click(view.canvas(), 0, 0);
                assertEquals(2, f.screen().getReceiverCards().size(), "клик по карте не ставит новую");
                assertEquals(first, view.activeCardId());
                assertTrue(!first.equals(second));

                click(view.canvas(), 3, 5);
                assertEquals(3, f.screen().getReceiverCards().size(), "клик по пустому модулю — карта на один модуль");
                assertEquals(1, cardOf(f.screen(), f.id(3, 5)).getModuleIds().size());
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void replacingPartOfACardReportsOrphansAndRepeatIsNoop(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        onEdt(() -> {
            ReceiverCardsView view = new ReceiverCardsView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                drag(view.canvas(), 0, 0, 1, 1);
                String old = onlyCard(f.screen()).getId();

                drag(view.canvas(), 0, 1, 1, 1);
                ReceiverCardInstance now = onlyCard(f.screen());
                assertEquals(2, now.getModuleIds().size());
                assertTrue(!old.equals(now.getId()), "старая карта заменена целиком");
                assertEquals("Заменено карт: 1, модулей без карты: 2", view.note());

                int depth = f.model.undoDepth();
                drag(view.canvas(), 0, 1, 1, 1);
                assertEquals(now.getId(), onlyCard(f.screen()).getId(), "повторная протяжка тем же типом — ничего");
                assertEquals(depth, f.model.undoDepth(), "и записи отмены не оставляет");
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void autoDragSplitsAreaIntoCardsWithRemainderAndMatchesPreview(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        onEdt(() -> {
            ReceiverCardsView view = new ReceiverCardsView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                CardAssignPanel panel = view.assignPanel();
                panel.autoRadio().doClick();
                panel.widthField().setText("2");
                panel.heightField().setText("2");
                assertEquals(2, panel.autoW());
                assertEquals(2, panel.autoH());

                // пунктир-предпросмотр во время протяжки (без отпускания) и итог при отпускании — те же прямоугольники
                GridRect area = new GridRect(0, 0, 2, 4);
                ScreenModules sm = ScreenModules.of(f.screen(), f.model.getWorkspace());
                List<GridRect> preview = ReceiverCardsCanvas.autoTiles(sm, area, 2, 2);
                assertEquals(6, preview.size());

                drag(view.canvas(), 0, 0, 2, 4);

                assertEquals(6, f.screen().getReceiverCards().size());
                Set<Set<String>> expected = new HashSet<>();
                for (GridRect t : preview) {
                    Set<String> ids = new HashSet<>();
                    sm.modulesIn(t).forEach(m -> ids.add(m.id()));
                    expected.add(ids);
                }
                Set<Set<String>> actual = new HashSet<>();
                f.screen().getReceiverCards().forEach(c -> actual.add(new HashSet<>(c.getModuleIds())));
                assertEquals(expected, actual, "карты встали ровно там, где был пунктир");
                List<Integer> sizes = f.screen().getReceiverCards().stream().map(c -> c.getModuleIds().size())
                        .sorted().toList();
                assertEquals(List.of(1, 2, 2, 2, 4, 4), sizes, "остаток у правого и нижнего края — меньшие карты");
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void autoFieldsValidateAndFocusSwitchesToAutoMode(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        onEdt(() -> {
            ReceiverCardsView view = new ReceiverCardsView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                CardAssignPanel panel = view.assignPanel();
                assertFalse(panel.autoMode());
                JTextField w = panel.widthField();
                for (FocusListener l : w.getFocusListeners()) {
                    l.focusGained(new FocusEvent(w, FocusEvent.FOCUS_GAINED));
                }
                assertTrue(panel.autoMode(), "фокус в поле Г × В включает «Автоматически»");

                w.setText("0");
                assertEquals("Укажите целое число от 1", panel.errorText());
                assertEquals(4, panel.autoW(), "неверное число не затирает прежнее");
                w.setText("3");
                assertEquals("", panel.errorText());
                assertEquals(3, panel.autoW());
                panel.heightField().setText("abc");
                assertEquals("Укажите целое число от 1", panel.errorText());
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void manualOrderToolNumbersModulesByClicksAndResets(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        onEdt(() -> {
            ReceiverCardsView view = new ReceiverCardsView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                drag(view.canvas(), 0, 0, 1, 1);
                ReceiverCardInstance card = onlyCard(f.screen());

                view.orderButton().doClick();
                assertEquals(ReceiverCardsView.Tool.ORDER, view.tool());
                assertEquals(List.of(), onlyCard(f.screen()).getManualPortOrder(), "режим включён, ничего не кликнуто");
                int depth = f.model.undoDepth();
                click(view.canvas(), 1, 1);
                click(view.canvas(), 0, 0);
                click(view.canvas(), 0, 1);
                assertEquals(depth + 3, f.model.undoDepth());
                assertEquals(List.of(f.id(1, 1), f.id(0, 0), f.id(0, 1)), onlyCard(f.screen()).getManualPortOrder());
                assertEquals(0, ModuleNumbering.numberOf(onlyCard(f.screen()), f.screen(), f.id(1, 0)),
                        "не кликнутый модуль — без номера («?»)");
                assertTrue(view.resetOrderButton().isEnabled());
                assertTrue(view.assignPanel().cardsTable().getValueAt(0, 0).toString().endsWith("вручную"));
                assertTrue(view.warningsBar().warnings().stream().anyMatch(w -> w.text().contains("без номера")));

                click(view.canvas(), 1, 0);
                assertEquals(f.id(1, 0), onlyCard(f.screen()).getManualPortOrder().get(3));

                view.resetOrderButton().doClick();
                assertNull(onlyCard(f.screen()).getManualPortOrder(), "«Сбросить к шаблону»");
                assertFalse(view.resetOrderButton().isEnabled());

                assertEquals(ReceiverCardsView.Tool.ORDER, view.tool(), "режим держится до «Готово»");
                view.orderButton().doClick(); // «Готово»
                assertEquals(ReceiverCardsView.Tool.SELECT, view.tool());
                assertEquals(card.getId(), onlyCard(f.screen()).getId());
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void placeToolMovesCardHostToClickedModule(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        onEdt(() -> {
            ReceiverCardsView view = new ReceiverCardsView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                drag(view.canvas(), 0, 0, 1, 2);
                assertNull(onlyCard(f.screen()).getHostModuleId());

                view.hostButton().doClick();
                click(view.canvas(), 1, 2);
                assertEquals(f.id(1, 2), onlyCard(f.screen()).getHostModuleId());
                assertEquals(1, view.assignPanel().cardsTable().getRowCount());
                assertEquals(ReceiverCardsView.Tool.HOST, view.tool(), "режим держится до «Готово»");

                click(view.canvas(), 3, 3); // модуль без карты — ничего не происходит
                assertEquals(1, f.screen().getReceiverCards().size());
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void deleteKeyRemovesActiveCard(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        onEdt(() -> {
            ReceiverCardsView view = new ReceiverCardsView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                drag(view.canvas(), 0, 0, 1, 1);
                assertTrue(view.removeButton().isEnabled());

                Object key = view.canvas().getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                        .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0));
                assertNotNull(key, "Delete привязан к холсту");
                view.canvas().getActionMap().get(key).actionPerformed(new ActionEvent(view.canvas(), 0, ""));

                assertEquals(0, f.screen().getReceiverCards().size());
                assertEquals("Карта снята, модулей без карты: 4", view.note());
                assertFalse(view.removeButton().isEnabled());

                drag(view.canvas(), 0, 0, 1, 1);
                view.removeButton().doClick();
                assertEquals(0, f.screen().getReceiverCards().size());
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void numberingPaletteChangesScreenPatternAndNumbers(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 2, 3);
        onEdt(() -> {
            ReceiverCardsView view = new ReceiverCardsView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                drag(view.canvas(), 0, 0, 0, 2);
                ReceiverCardInstance card = onlyCard(f.screen());
                assertEquals(1, ModuleNumbering.numberOf(card, f.screen(), f.id(0, 0)));
                assertEquals("Первый модуль — слева сверху, дальше вправо по строке", view.palette().description());

                view.palette().clickTile(1); // справа сверху, по строкам
                ModuleNumberingPattern p = f.screen().getModuleNumbering();
                assertEquals(ModuleNumberingPattern.Corner.TOP_RIGHT, p.getCorner());
                assertEquals(ModuleNumberingPattern.Axis.ROWS, p.getAxis());
                assertEquals(1, ModuleNumbering.numberOf(onlyCard(f.screen()), f.screen(), f.id(0, 2)));
                assertEquals("Первый модуль — справа сверху, дальше влево по строке", view.palette().description());

                view.palette().clickTile(5); // справа сверху, по столбцам
                assertEquals(ModuleNumberingPattern.Axis.COLUMNS, f.screen().getModuleNumbering().getAxis());
                assertEquals("Первый модуль — справа сверху, дальше вниз по столбцу", view.palette().description());

                view.palette().snakeCheck().doClick();
                assertTrue(f.screen().getModuleNumbering().isSnake());
                assertEquals(ModuleNumberingPattern.Corner.TOP_RIGHT, f.screen().getModuleNumbering().getCorner());
                assertTrue(view.palette().description().endsWith(", змейкой"));
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void warningsAndTableShowLimitsOfTheCardType(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        ReceiverCardType small = new ReceiverCardType();
        small.setName("Карта малая");
        small.setModuleOutputs(4);
        small.setRibbonLengthMm(300.0);
        f.model.addReceiverCardType(small);
        onEdt(() -> {
            ReceiverCardsView view = new ReceiverCardsView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                view.assignPanel().typeList().setSelectedValue(small, false);
                assertEquals(small.getId(), view.assignPanel().selectedTypeId());
                drag(view.canvas(), 0, 0, 1, 2);

                var texts = view.warningsBar().warnings().stream().map(ReceiverWarningsBar.Warning::text).toList();
                assertTrue(texts.stream().anyMatch(t -> t.contains("6/4")), "модулей больше выходов: " + texts);
                assertTrue(texts.stream().anyMatch(t -> t.contains("длиннее 300 мм")), "длинные шлейфы: " + texts);
                assertTrue(texts.stream().anyMatch(t -> t.startsWith("Модулей без карты: 18")), texts.toString());

                var table = view.assignPanel().cardsTable();
                assertTrue(table.getValueAt(0, 1).toString().startsWith("6/4"), table.getValueAt(0, 1).toString());
                assertEquals("192×128", table.getValueAt(0, 2));
                assertEquals("24 576", table.getValueAt(0, 3));
                assertTrue(view.assignPanel().statsText().contains("без карты: 18"));
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void nonModularScreenShowsHintInsteadOfCanvasAndDisablesTools(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 4, 6);
        CabinetType plain = new CabinetType();
        plain.setName("Обычный");
        plain.setWidthMm(500);
        plain.setHeightMm(500);
        f.model.addCabinetType(plain);
        Screen modular = f.screen();
        Screen ordinary = f.model.addScreen("Обычный", plain.getId(), 2, 2, 0, 0);
        onEdt(() -> {
            ReceiverCardsView view = new ReceiverCardsView(f.model, f.settings);
            JFrame frame = f.frameOf(view, view.canvas());
            try {
                f.model.selectScreen(modular);
                view.refresh();
                assertTrue(view.canvas().hasContent());
                drag(view.canvas(), 0, 0, 1, 1);
                assertNotNull(view.activeCardId());
                assertTrue(view.orderButton().isEnabled());

                f.model.selectScreen(ordinary);
                view.refresh();
                assertFalse(view.canvas().hasContent());
                assertTrue(view.canvas().placeholder().contains("Приёмная карта отдельно"));
                assertFalse(view.orderButton().isEnabled());
                assertFalse(view.hostButton().isEnabled());
                assertFalse(view.removeButton().isEnabled());
                assertNull(view.activeCardId(), "выбор карты сбрасывается при смене экрана");
                assertTrue(view.warningsBar().warnings().isEmpty());
                // клик по холсту обычного экрана ничего не делает
                click(view.canvas(), 0, 0);
                assertEquals(0, ordinary.getReceiverCards().size());
            } finally {
                frame.dispose();
            }
        });
    }

    private static ReceiverCardInstance cardOf(Screen s, String moduleId) {
        for (ReceiverCardInstance c : s.getReceiverCards()) {
            if (c.getModuleIds().contains(moduleId)) {
                return c;
            }
        }
        return null;
    }
}
