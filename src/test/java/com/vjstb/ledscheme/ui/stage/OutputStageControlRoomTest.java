package com.vjstb.ledscheme.ui.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.room.RoomRackLayout;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Window;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Сквозная проверка части вывода «Планер пультовой»: настоящий {@code runExport} пишет в папку сцены «Пульт» картинки
 * выбранных видов и спецификацию, без раскладок кнопка части не создаёт файлов. Модальные окна итога закрывает таймер.
 * Пропускается без дисплея.
 */
class OutputStageControlRoomTest {

    private static AppModel model(Path dir, boolean withLayout) {
        AppModel m = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        m.selectProject(m.addProject("P"));
        m.selectScene(m.addScene("Жара"));
        if (withLayout) {
            CaseType c = new CaseType();
            c.setName("Рэк-кофр 6U");
            c.setWidthMm(600);
            c.setLengthMm(620);
            c.setHeightMm(430);
            PhysicalSpec cp = new PhysicalSpec();
            cp.setRackUnits(6);
            cp.setDepthMm(450.0);
            c.setPhysical(cp);
            m.addCaseType(c);
            ControlRoomLayout l = m.addControlRoomLayout("FOH");
            RoomRackLayout.newTower(l, new RoomLibRef(RoomLibRef.Kind.CASE, c.getId()), 200, 300);
        }
        return m;
    }

    /** Закрывает все показанные модальные окна (итог экспорта «Открыть папку?») каждые 150 мс. */
    private static Timer autoClose() {
        Timer t = new Timer(150, e -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing() && d.isModal()) {
                    d.dispose();
                }
            }
        });
        t.start();
        return t;
    }

    private static void run(OutputStagePanel panel, File folder, EnumSet<OutputPart> parts, boolean single)
            throws Exception {
        var f = OutputStagePanel.class.getDeclaredField("chosenFolder");
        f.setAccessible(true);
        f.set(panel, folder);
        var m = OutputStagePanel.class.getDeclaredMethod("runExport", java.util.Set.class, boolean.class);
        m.setAccessible(true);
        Timer t = autoClose();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    m.invoke(panel, parts, single);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        } finally {
            t.stop();
        }
    }

    @Test
    void packageExportWritesTheChosenViewsAndTheSpecIntoTheSceneFolder(@TempDir Path dir) throws Exception {
        Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        AppModel m = model(dir, true);
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        settings.setControlRoomExportOptions(List.of("PLAN", "ELEVATION", "RACKS_TOP"), true);
        File out = new File(dir.toFile(), "out");
        run(new OutputStagePanel(m, settings), out, EnumSet.of(OutputPart.CONTROL_ROOM), false);

        File folder = new File(out, "Жара/Пульт");
        String[] names = folder.list();
        assertTrue(names != null, "папка «Пульт» создана: " + Arrays.toString(out.list()));
        assertEquals(4, names.length, "3 вида + спецификация: " + Arrays.toString(names));
        assertTrue(Arrays.stream(names).anyMatch(n -> n.contains("План")), Arrays.toString(names));
        assertTrue(Arrays.stream(names).anyMatch(n -> n.contains("Вид спереди")));
        assertTrue(Arrays.stream(names).anyMatch(n -> n.contains("Стойки сверху")));
        assertTrue(Arrays.stream(names).anyMatch(n -> n.endsWith("Спецификация.txt")));
        assertTrue(Arrays.stream(names).noneMatch(n -> n.contains("Стойки.") || n.endsWith("Стойки.jpg")),
                "не выбранный вид не рисуется");
    }

    @Test
    void withoutLayoutsThePartCreatesNothing(@TempDir Path dir) throws Exception {
        Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        AppModel m = model(dir, false);
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        File out = new File(dir.toFile(), "out");
        run(new OutputStagePanel(m, settings), out, EnumSet.of(OutputPart.CONTROL_ROOM), false);
        assertTrue(!new File(out, "Жара/Пульт").exists(), "раскладок нет — папка не создаётся");
    }
}
