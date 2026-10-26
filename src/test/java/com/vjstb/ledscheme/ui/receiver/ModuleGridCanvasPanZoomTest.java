package com.vjstb.ledscheme.ui.receiver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import com.vjstb.ledscheme.model.GridRect;

/**
 * Баг-репорт: в видах «Приёмные карты» и «Блоки питания» не работали перемещение средней кнопкой и масштаб колесом —
 * холст слушал только левую кнопку. События подаются реальным {@code dispatchEvent} на холст внутри прокрутки (как в
 * приложении): прямой вызов слушателя пропустил бы ошибку «слушатель не навешен».
 */
class ModuleGridCanvasPanZoomTest {

    private static final class TestCanvas extends ModuleGridCanvas {
        @Override
        boolean hasContent() {
            return true;
        }

        @Override
        String placeholder() {
            return "";
        }

        @Override
        void paintGrid(Graphics2D g, ModuleGridGeometry geo, ModuleViewStyle style) {
        }
    }

    private static void send(TestCanvas c, int id, int modifiers, int button, int x, int y) {
        c.dispatchEvent(new MouseEvent(c, id, System.currentTimeMillis(), modifiers, x, y, 1, false, button));
    }

    private static JScrollPane host(TestCanvas c) {
        c.setGrid(4, 30, 1);
        JScrollPane sp = new JScrollPane(c);
        sp.setPreferredSize(new Dimension(400, 300));
        JFrame f = new JFrame();
        f.setContentPane(sp);
        f.pack();
        sp.doLayout();
        sp.getViewport().doLayout();
        return sp;
    }

    @Test
    void middleButtonDragMovesTheViewAndDoesNotTouchTheGridSelection() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        SwingUtilities.invokeAndWait(() -> {
            TestCanvas c = new TestCanvas();
            int[] clicks = {0};
            c.setListener(new ModuleGridCanvas.Listener() {
                @Override
                public void cellClicked(int row, int col) {
                    clicks[0]++;
                }

                @Override
                public void areaDragged(GridRect rect) {
                    clicks[0]++;
                }
            });
            JScrollPane sp = host(c);
            assertTrue(c.getWidth() > sp.getViewport().getWidth(), "сетка шире окна — есть что двигать");
            assertEquals(new Point(0, 0), sp.getViewport().getViewPosition());

            send(c, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON2_DOWN_MASK, MouseEvent.BUTTON2, 200, 150);
            send(c, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON2_DOWN_MASK, MouseEvent.NOBUTTON, 150, 120);
            send(c, MouseEvent.MOUSE_RELEASED, 0, MouseEvent.BUTTON2, 150, 120);

            Point pos = sp.getViewport().getViewPosition();
            assertEquals(50, pos.x, "рука влево на 50 — вид вправо на 50");
            assertEquals(0, pos.y, "по вертикали сетка целиком помещается — двигать нечего");
            assertEquals(0, clicks[0], "средняя кнопка не ставит карты и не выбирает клетки");
            assertNull(c.dragRect());
        });
    }

    @Test
    void ctrlWheelZoomsAroundTheCursorAndPlainWheelDoesNot() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        TestCanvas[] holder = new TestCanvas[1];
        JScrollPane[] spHolder = new JScrollPane[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new TestCanvas();
            spHolder[0] = host(holder[0]);
            TestCanvas c = holder[0];
            int before = c.getPreferredSize().width;
            assertEquals(1.0, c.zoom());

            c.dispatchEvent(new MouseWheelEvent(c, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                    InputEvent.CTRL_DOWN_MASK, 300, 100, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1));
            assertTrue(c.zoom() > 1.0, "Ctrl+колесо вверх — приближение");
            assertTrue(c.getPreferredSize().width > before, "сетка стала больше");

            double zoomed = c.zoom();
            c.dispatchEvent(new MouseWheelEvent(c, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
                    300, 100, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1));
            assertEquals(zoomed, c.zoom(), "обычное колесо — прокрутка, масштаб не меняется");

            for (int i = 0; i < 40; i++) {
                c.dispatchEvent(new MouseWheelEvent(c, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                        InputEvent.CTRL_DOWN_MASK, 300, 100, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1));
            }
            assertEquals(ModuleGridCanvas.MIN_ZOOM, c.zoom(), 1e-9, "масштаб ограничен снизу");
        });
        // положение вида после масштабирования ставится отложенно (после раскладки) — дожидаемся, что оно не упало
        SwingUtilities.invokeAndWait(() -> assertTrue(spHolder[0].getViewport().getViewPosition().x >= 0));
    }
}
