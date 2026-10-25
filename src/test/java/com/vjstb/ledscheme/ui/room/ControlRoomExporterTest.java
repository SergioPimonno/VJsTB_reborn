package com.vjstb.ledscheme.ui.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.ExportSchemaStyle;
import com.vjstb.ledscheme.settings.UserProfile;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.ExportRendering;
import com.vjstb.ledscheme.ui.LafStyle;
import com.vjstb.ledscheme.ui.Palette;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Картинки раскладки Планера пультовой для этапа «Вывод»: каждый вид рисуется, размер зависит от площадки и качества
 * экспорта, над картинкой есть заголовок, печатный стиль даёт светлую картинку при тёмной теме приложения, стойки без
 * башен не рисуются, спецификация текстом содержит башни. Пропускается без дисплея.
 */
class ControlRoomExporterTest {

    private static double meanLuma(BufferedImage img) {
        long sum = 0;
        int n = 0;
        for (int y = 0; y < img.getHeight(); y += 3) {
            for (int x = 0; x < img.getWidth(); x += 3) {
                int rgb = img.getRGB(x, y);
                sum += (int) (0.299 * ((rgb >> 16) & 255) + 0.587 * ((rgb >> 8) & 255) + 0.114 * (rgb & 255));
                n++;
            }
        }
        return sum / (double) n;
    }

    private static AppModel model(Path dir) {
        AppModel m = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        m.selectProject(m.addProject("P"));
        m.selectScene(m.addScene("S"));
        return m;
    }

    @Test
    void everyViewIsDrawnWithATitleBarAndScalesWithTheExportQuality(@TempDir Path dir) throws Exception {
        Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        javax.swing.UIManager.setLookAndFeel(LafStyle.FLAT_DARK.createLaf());
        Palette.applyTheme(true);
        AppModel m = model(dir);
        ControlRoomLayout l = PlannerSample.build(m).layout();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            for (ControlRoomExporter.View v : ControlRoomExporter.View.values()) {
                assertTrue(v.applicableTo(l), v + ": в примере есть башни");
                BufferedImage a = ControlRoomExporter.render(m, "Жара", l, v, 1.0);
                BufferedImage b = ControlRoomExporter.render(m, "Жара", l, v, 2.0);
                assertTrue(a.getWidth() > 300 && a.getHeight() > 150, v + ": картинка не пустая " + a.getWidth() + "×"
                        + a.getHeight());
                assertEquals(a.getWidth() * 2.0, b.getWidth(), 3.0, v + ": качество ×2 — картинка вдвое больше");
                assertTrue(meanLuma(a) > 5 && meanLuma(a) < 250, v + ": на картинке что-то нарисовано");
            }
            // план и вид спереди — размер от площадки: 7×4,5 м
            BufferedImage plan = ControlRoomExporter.render(m, "Жара", l, ControlRoomExporter.View.PLAN, 1.0);
            assertEquals((l.getRoomWidthCm() + 90) * ControlRoomExporter.PX_PER_CM, plan.getWidth(), 2.0);
        });
    }

    @Test
    void printStyleDrawsTheSameViewLightEvenWithTheDarkApplicationTheme(@TempDir Path dir) throws Exception {
        Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        javax.swing.UIManager.setLookAndFeel(LafStyle.FLAT_DARK.createLaf());
        Palette.applyTheme(true);
        AppModel m = model(dir);
        ControlRoomLayout l = PlannerSample.build(m).layout();
        UserProfile screen = new UserProfile();
        screen.setExportSchemaStyle(ExportSchemaStyle.SCREEN);
        UserProfile print = new UserProfile();
        print.setExportSchemaStyle(ExportSchemaStyle.PRINT);
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            BufferedImage dark = ExportRendering.run(screen,
                    () -> ControlRoomExporter.render(m, "S", l, ControlRoomExporter.View.PLAN, 1.0));
            BufferedImage light = ExportRendering.run(print,
                    () -> ControlRoomExporter.render(m, "S", l, ControlRoomExporter.View.PLAN, 1.0));
            assertTrue(meanLuma(light) > meanLuma(dark) + 60,
                    "печатный стиль светлый, экранный тёмный: " + meanLuma(light) + " против " + meanLuma(dark));
            assertTrue(Palette.isDark(), "после печатного рендера тема приложения вернулась");
        });
    }

    @Test
    void racksViewsAreSkippedForALayoutWithoutTowersAndTheSpecIsText(@TempDir Path dir) {
        AppModel m = model(dir);
        ControlRoomLayout empty = m.addControlRoomLayout("Пустая");
        assertFalse(ControlRoomExporter.View.RACKS_FRONT.applicableTo(empty));
        assertFalse(ControlRoomExporter.View.RACKS_TOP.applicableTo(empty));
        assertTrue(ControlRoomExporter.View.PLAN.applicableTo(empty));
        assertTrue(ControlRoomExporter.View.ELEVATION.applicableTo(empty));

        ControlRoomLayout full = PlannerSample.build(m).layout();
        String spec = ControlRoomExporter.specText(m, full);
        assertTrue(spec.contains("Башня A") || spec.contains("A"), "в спецификации есть башни: " + spec);
        assertTrue(spec.length() > 100);
    }
}
