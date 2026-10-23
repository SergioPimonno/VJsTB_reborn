package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Раскладки пультовой через {@link AppModel}: операции пишутся в проект сразу (как схемы и сети),
 * у каждой сцены свой список, перезапуск приложения (новый AppModel на том же файле) возвращает
 * те же раскладки. Решение пользователя 2026-10-23: список раскладок индивидуален для каждой
 * сцены проекта, раскладок может быть несколько.
 */
class ControlRoomLayoutsModelTest {

    private static AppModel open(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    @Test
    void layoutsArePerSceneAndSurviveARestart(@TempDir Path dir) {
        AppModel m = open(dir);
        m.selectProject(m.addProject("Проект"));
        Scene a = m.addScene("Сцена A");
        Scene b = m.addScene("Сцена B");
        m.selectScene(a);
        m.addControlRoomLayout("FOH");
        ControlRoomLayout second = m.addControlRoomLayout("Backstage");
        m.duplicateControlRoomLayout(second);
        m.selectScene(b);
        m.addControlRoomLayout("FOH");
        assertEquals(1, m.controlRoomLayouts().size(), "у сцены B свой список");
        m.selectScene(a);
        assertEquals(3, m.controlRoomLayouts().size());

        AppModel reopened = open(dir);
        Scene ra = reopened.getWorkspace().getProjects().get(0).getScenes().get(0);
        Scene rb = reopened.getWorkspace().getProjects().get(0).getScenes().get(1);
        assertEquals(3, ra.getControlRoomLayouts().size());
        assertEquals("Backstage (копия)", ra.getControlRoomLayouts().get(2).getName());
        assertEquals(1, rb.getControlRoomLayouts().size());
    }

    @Test
    void renameAndDeleteArePersisted(@TempDir Path dir) {
        AppModel m = open(dir);
        m.selectProject(m.addProject("Проект"));
        m.selectScene(m.addScene("Сцена"));
        ControlRoomLayout l = m.addControlRoomLayout("Черновик");
        m.renameControlRoomLayout(l, "Основная");
        ControlRoomLayout extra = m.addControlRoomLayout("Лишняя");
        m.deleteControlRoomLayout(extra);

        AppModel reopened = open(dir);
        Scene s = reopened.getWorkspace().getProjects().get(0).getScenes().get(0);
        assertEquals(1, s.getControlRoomLayouts().size());
        assertEquals("Основная", s.getControlRoomLayouts().get(0).getName());
    }

    @Test
    void withoutASceneThereAreNoLayoutsAndAddingIsRefused(@TempDir Path dir) {
        AppModel m = open(dir);
        assertTrue(m.controlRoomLayouts().isEmpty());
        try {
            m.addControlRoomLayout("X");
            throw new AssertionError("ожидали IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("сцена"));
        }
    }
}
