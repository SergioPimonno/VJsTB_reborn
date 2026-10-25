package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Дымовой тест каркаса «Планера пультовой»: окно строится на модели с несколькими раскладками
 *  и подхватывает смену сцены (у каждой сцены свой список). Пропускается без дисплея. */
class ControlRoomPlannerDialogTest {

    @SuppressWarnings("unchecked")
    private static JComboBox<Object> combo(java.awt.Container c) {
        for (java.awt.Component k : c.getComponents()) {
            if (k instanceof JComboBox<?> b) {
                return (JComboBox<Object>) b;
            }
            if (k instanceof java.awt.Container sub) {
                JComboBox<Object> found = combo(sub);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void followsTheCurrentSceneLayouts(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        SwingUtilities.invokeAndWait(() -> {
            AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
            model.selectProject(model.addProject("P"));
            var a = model.addScene("A");
            var b = model.addScene("B");
            model.selectScene(a);
            model.addControlRoomLayout("FOH");
            model.addControlRoomLayout("Запасная");
            model.selectScene(b);
            ControlRoomPlannerDialog dialog = new ControlRoomPlannerDialog(null, model);
            JComboBox<Object> box = combo(dialog.getContentPane());
            assertEquals(0, box.getItemCount(), "у сцены B раскладок нет");
            model.selectScene(a);
            assertEquals(2, box.getItemCount(), "окно перечитывает список при смене сцены");
            dialog.dispose();
        });
    }
}
