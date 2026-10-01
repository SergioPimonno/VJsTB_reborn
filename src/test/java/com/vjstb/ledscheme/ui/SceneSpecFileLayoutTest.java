package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import java.io.File;
import org.junit.jupiter.api.Test;

/**
 * Файлы спецификации по сценам (запрос пользователя 2026-09-30, решение D6): один
 * .xlsx на каждую сцену в папке этой сцены — {@code <корень>/<Сцена>/<Проект>_<Сцена>_
 * спецификация.xlsx}; общепроектного {@code <Проект>_спецификация.xlsx} больше нет.
 * Папка сцены — та же, что пакет документации уже создаёт для схем и масок.
 */
class SceneSpecFileLayoutTest {

    private static Project project(String name) {
        return new Project(name);
    }

    @Test
    void specFileLivesInTheSceneFolderAndNamesProjectAndScene() {
        File root = new File("out");
        File f = OutputPaths.sceneSpecFile(root, project("Барметцва"), new Scene("Главный зал"));

        assertEquals(new File(new File(root, "Главный зал"), "Барметцва_Главный зал_спецификация.xlsx"), f);
    }

    @Test
    void eachSceneGetsItsOwnFileInItsOwnFolder() {
        File root = new File("out");
        Project p = project("Проект");

        File a = OutputPaths.sceneSpecFile(root, p, new Scene("Зал"));
        File b = OutputPaths.sceneSpecFile(root, p, new Scene("Фойе"));

        assertNotEquals(a, b);
        assertNotEquals(a.getParentFile(), b.getParentFile());
        assertEquals("Зал", a.getParentFile().getName());
    }

    @Test
    void forbiddenCharactersInNamesAreSanitizedLikeTheRestOfThePackage() {
        File f = OutputPaths.sceneSpecFile(new File("out"), project("П/р:о"), new Scene("Сц*ена?"));

        assertEquals("Сц_ена_", f.getParentFile().getName());
        assertEquals("П_р_о_Сц_ена__спецификация.xlsx", f.getName());
    }

    @Test
    void thereIsNoProjectWideSpecFileNameAnyMore() {
        File f = OutputPaths.sceneSpecFile(new File("out"), project("Проект"), new Scene("Зал"));

        assertNotEquals("Проект_спецификация.xlsx", f.getName(),
                "прежнее общепроектное имя не используется (решение D6)");
    }
}
