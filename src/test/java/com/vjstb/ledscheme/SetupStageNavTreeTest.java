package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenGroup;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.stage.SetupStagePanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Дерево навигации «Сетапа» после доработки 2026-09-24: мультивыбор сохраняется
 * при перестройке дерева, экраны группы лежат под узлом группы, Esc снимает
 * выделение (раньше это делал ПКМ по уже выбранному узлу), Ctrl+D дублирует
 * выделенные экраны в их группе.
 */
class SetupStageNavTreeTest {

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
    void groupNodeHoldsItsScreensMultiSelectionSurvivesRebuildAndEscClears(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");

        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
        CabinetType ct = new CabinetType();
        ct.setName("T 500x500");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        model.addCabinetType(ct);
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen a = model.addScreen("Экран 1", ct.getId(), 2, 2, 0, 0);
        Screen b = model.addScreen("Экран 2", ct.getId(), 2, 2, 1200, 0);
        Screen c = model.addScreen("Экран 3", ct.getId(), 2, 2, 2400, 0);
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));

        JTree[] treeRef = new JTree[1];
        SwingUtilities.invokeAndWait(() -> {
            SetupStagePanel panel = new SetupStagePanel(model, settings);
            treeRef[0] = findTree(panel);
        });
        flushEdt();
        JTree tree = treeRef[0];

        // Shift-выделение двух экранов: оба остаются выделенными после перестройки дерева
        SwingUtilities.invokeAndWait(() -> tree.setSelectionPaths(
                new TreePath[]{pathOf(tree, a), pathOf(tree, b)}));
        flushEdt();
        assertEquals(2, tree.getSelectionCount(), "мультивыбор не должен схлопываться до одного экрана");

        // Группа: экраны переезжают под её узел, сама группа выделена
        SwingUtilities.invokeAndWait(() -> model.groupScreens(scene, List.of(a, b), "Группа 1"));
        flushEdt();
        ScreenGroup g = scene.getScreenGroups().get(0);
        TreePath groupPath = pathOf(tree, g);
        assertTrue(groupPath != null, "узел группы должен появиться в дереве");
        DefaultMutableTreeNode gNode = (DefaultMutableTreeNode) groupPath.getLastPathComponent();
        assertEquals(2, gNode.getChildCount());
        assertEquals(a, ((DefaultMutableTreeNode) gNode.getChildAt(0)).getUserObject());
        assertTrue(tree.isExpanded(groupPath));

        // Свёрнутость группы сохраняется в модели
        SwingUtilities.invokeAndWait(() -> tree.collapsePath(groupPath));
        assertTrue(g.isCollapsed());

        // Выбор группы: активного экрана нет
        SwingUtilities.invokeAndWait(() -> tree.setSelectionPath(groupPath));
        flushEdt();
        assertNull(model.getCurrentScreen());
        assertEquals(1, tree.getSelectionCount());
        assertEquals(g, ((DefaultMutableTreeNode) tree.getSelectionPath().getLastPathComponent()).getUserObject());

        // Ctrl+D на выбранной группе — по копии каждого экрана, в той же группе
        SwingUtilities.invokeAndWait(() -> tree.getActionMap().get("ledScheme.nav.duplicate")
                .actionPerformed(new ActionEvent(tree, ActionEvent.ACTION_PERFORMED, "")));
        flushEdt();
        assertEquals(5, scene.getScreens().size());
        assertEquals(4, scene.screensOf(g).size());
        assertEquals(List.of("Экран 4", "Экран 5"),
                scene.getScreens().subList(2, 4).stream().map(Screen::getName).toList());

        // Esc снимает выделение целиком
        SwingUtilities.invokeAndWait(() -> tree.getActionMap().get("ledScheme.nav.clear")
                .actionPerformed(new ActionEvent(tree, ActionEvent.ACTION_PERFORMED, "")));
        flushEdt();
        assertEquals(0, tree.getSelectionCount());
        assertNull(model.getCurrentProject());
    }
}
