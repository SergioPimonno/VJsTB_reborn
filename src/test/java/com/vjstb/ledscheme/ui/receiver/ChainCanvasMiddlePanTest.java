package com.vjstb.ledscheme.ui.receiver;

import static com.vjstb.ledscheme.ui.receiver.ReceiverViewFixture.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.ui.CanvasPanel;
import com.vjstb.ledscheme.ui.ChainInteractionController;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Баг-репорт: в «Расключении экрана» не работало перемещение по схеме средней кнопкой (на остальных холстах
 * работает). События — реальным {@code dispatchEvent} на холст внутри прокрутки.
 */
class ChainCanvasMiddlePanTest {

    @Test
    void middleButtonDragMovesTheChainCanvasInsideItsScrollPane(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 30, 40);
        onEdt(() -> {
            f.model.setMode(AppModel.Mode.SIGNAL);
            CanvasPanel canvas = new CanvasPanel(f.model, f.settings, new ChainInteractionController(f.model, () -> { }));
            JScrollPane sp = new JScrollPane(canvas);
            sp.setPreferredSize(new Dimension(400, 300));
            JFrame frame = new JFrame();
            frame.setContentPane(sp);
            frame.pack();
            try {
                assertTrue(canvas.getPreferredSize().width > 400, "схема шире окна — есть что двигать");
                assertEquals(new Point(0, 0), sp.getViewport().getViewPosition());

                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0L,
                        InputEvent.BUTTON2_DOWN_MASK, 200, 150, 1, false, MouseEvent.BUTTON2));
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0L,
                        InputEvent.BUTTON2_DOWN_MASK, 120, 90, 1, false, MouseEvent.NOBUTTON));
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0L, 0, 120, 90, 1, false,
                        MouseEvent.BUTTON2));

                Point pos = sp.getViewport().getViewPosition();
                assertEquals(80, pos.x, "рука влево на 80 — схема вправо на 80");
                assertEquals(60, pos.y);
            } finally {
                frame.dispose();
            }
        });
    }
}
