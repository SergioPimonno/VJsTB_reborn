package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.stage.SetupStagePanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Баг-репорт 2026-09-30: ПКМ на узле проекта → «+ Добавить сцену…» открывал диалог
 *  «Добавить экран» вместо диалога сцены. Причина — выбор узла проекта не сбрасывал
 *  {@code model.getCurrentScene()}, если проект и так уже был текущим (см. javadoc
 *  {@code SetupStagePanel#selectInModel}), а именно по этому полю {@code
 *  addContextualNode()} решает, какой диалог показать. */
class SetupStageProjectSceneSelectionTest {

    private static JTree findTree(Container c) {
        for (Component ch : c.getComponents()) {
            if (ch instanceof JTree t) {
                return t;
            }
            if (ch instanceof Container cc) {
                JTree t = findTree(cc);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static TreePath pathOf(JTree tree, Object userObject) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        java.util.Enumeration<?> en = root.depthFirstEnumeration();
        while (en.hasMoreElements()) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) en.nextElement();
            if (n.getUserObject() == userObject) {
                return new TreePath(n.getPath());
            }
        }
        return null;
    }

    @Test
    void selectingProjectNodeClearsCurrentSceneEvenWhenProjectWasAlreadyCurrent(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");

        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
        CabinetType ct = new CabinetType();
        ct.setName("T 500x500");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        model.addCabinetType(ct);
        Project project = model.addProject("Мини-СИ");
        model.selectProject(project);
        Scene scene = model.addScene("Сцена");
        model.selectScene(scene);
        model.addScreen("Экран 3", ct.getId(), 5, 8, 0, 0);
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));

        JTree[] treeRef = new JTree[1];
        SwingUtilities.invokeAndWait(() -> {
            SetupStagePanel panel = new SetupStagePanel(model, settings);
            treeRef[0] = findTree(panel);
        });
        flushEdt();
        JTree tree = treeRef[0];

        // Экран уже выбран (как в баг-репорте — прериг сцены открыт на экране).
        // model.getCurrentProject() уже указывает на "Мини-СИ" — переключения проекта
        // НЕ происходит, только выбор узла-проекта в дереве.
        SwingUtilities.invokeAndWait(() -> tree.setSelectionPath(pathOf(tree, project)));
        flushEdt();

        assertNull(model.getCurrentScene(),
                "выбор узла проекта должен сбрасывать текущую сцену, иначе «+ Добавить сцену…»"
                        + " (SetupStagePanel#addContextualNode) откроет диалог «Добавить экран»");
    }
}
