package com.vjstb.ledscheme.ui.receiver;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Общая заготовка GUI-тестов видов «Приёмные карты» / «Блоки питания»: настоящий {@code AppModel} с экраном из
 * модулей 250×250 мм (64×64 px), по одному типу карты и блока в библиотеке, вид в окне, которое строится
 * ({@code pack}), но не показывается. Жесты на холсте подаются реальными {@code MouseEvent} через
 * {@code dispatchEvent} — как в {@code PowerStagePanelChainRowClickTest}: прямой вызов слушателя пропустил бы
 * ошибки доставки события.
 */
final class ReceiverViewFixture {

    static final int WIDTH = 900;
    static final int HEIGHT = 560;

    final AppModel model;
    final SettingsManager settings;
    final CabinetType moduleType;
    final ReceiverCardType cardType;
    final PowerSupplyType psuType;
    final Scene scene;

    ReceiverViewFixture(Path dir, int rows, int cols) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
        settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        moduleType = new CabinetType();
        moduleType.setName("Модуль 250");
        moduleType.setWidthMm(250);
        moduleType.setHeightMm(250);
        moduleType.setResolutionWidth(64);
        moduleType.setResolutionHeight(64);
        moduleType.setPowerConsumptionW(30);
        moduleType.setReceiverCardSeparate(true);
        model.addCabinetType(moduleType);
        cardType = new ReceiverCardType();
        cardType.setName("Карта А");
        cardType.setModuleOutputs(12);
        cardType.setRibbonLengthMm(600.0);
        model.addReceiverCardType(cardType);
        psuType = new PowerSupplyType();
        psuType.setName("Блок Б");
        psuType.setOutputs(8);
        psuType.setRatedPowerW(300.0);
        psuType.setCableLengthMm(700.0);
        model.addPowerSupplyType(psuType);
        model.selectProject(model.addProject("П"));
        scene = model.addScene("С");
        model.selectScene(scene);
        model.selectScreen(model.addScreen("Э", moduleType.getId(), rows, cols, 0, 0));
    }

    Screen screen() {
        return model.getCurrentScreen();
    }

    String id(int row, int col) {
        return screen().cabinetAt(row, col).getId();
    }

    boolean headless() {
        return GraphicsEnvironment.isHeadless();
    }

    /** Окно с видом: pack без показа; холсту задан определённый размер — геометрия клеток стабильна. */
    JFrame frameOf(Component view, ModuleGridCanvas canvas) {
        JFrame frame = new JFrame();
        frame.setContentPane((java.awt.Container) view);
        frame.pack();
        canvas.setSize(WIDTH, HEIGHT);
        return frame;
    }

    // ------------------------------------------------------------------ жесты

    private static void send(ModuleGridCanvas canvas, int id, double x, double y, int button) {
        canvas.dispatchEvent(new MouseEvent(canvas, id, System.currentTimeMillis(), 0, (int) Math.round(x),
                (int) Math.round(y), 1, false, button));
    }

    /** Протяжка от центра клетки (r0, c0) до центра (r1, c1) с промежуточными шагами. */
    static void drag(ModuleGridCanvas canvas, int r0, int c0, int r1, int c1) {
        ModuleGridGeometry g = canvas.geometry();
        send(canvas, MouseEvent.MOUSE_PRESSED, g.centerX(c0), g.centerY(r0), MouseEvent.BUTTON1);
        send(canvas, MouseEvent.MOUSE_DRAGGED, (g.centerX(c0) + g.centerX(c1)) / 2, (g.centerY(r0) + g.centerY(r1)) / 2,
                MouseEvent.NOBUTTON);
        send(canvas, MouseEvent.MOUSE_DRAGGED, g.centerX(c1), g.centerY(r1), MouseEvent.NOBUTTON);
        send(canvas, MouseEvent.MOUSE_RELEASED, g.centerX(c1), g.centerY(r1), MouseEvent.BUTTON1);
    }

    /** Клик без движения по центру клетки. */
    static void click(ModuleGridCanvas canvas, int row, int col) {
        drag(canvas, row, col, row, col);
    }

    // ------------------------------------------------------------------ EDT

    interface Body {
        void run() throws Exception;
    }

    /** Выполняет тело на потоке Swing и пробрасывает исходное исключение (а не InvocationTargetException). */
    static void onEdt(Body body) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        Throwable t = failure.get();
        if (t instanceof Exception e) {
            throw e;
        }
        if (t instanceof Error e) {
            throw e;
        }
    }
}
