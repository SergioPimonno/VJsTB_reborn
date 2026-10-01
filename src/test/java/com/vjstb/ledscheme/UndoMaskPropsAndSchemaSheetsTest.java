package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.MaskColorPreset;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.SchemaSheet;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Color;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Интеграция треков A и C1 v2.6 (2026-09-30): оба расширили один и тот же снимок отмены
 *  ({@code AppModel.UndoEntry}) -- A добавила свойства масок экранов (цвет/своя пара/«сетка»),
 *  C1 -- листы общей схемы. По отдельности каждый трек проверял свою отмену; этот тест
 *  гоняет их вперемешку одним стеком Ctrl+Z, чтобы слияние не потеряло ни одно из двух. */
class UndoMaskPropsAndSchemaSheetsTest {

    private AppModel model;
    private CabinetType type;

    private Screen setUp(Path dir) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        type = new CabinetType();
        type.setName("Base");
        type.setResolutionWidth(128);
        type.setResolutionHeight(128);
        model.addCabinetType(type);
        Project project = model.addProject("Проект");
        model.selectProject(project);
        Scene scene = model.addScene("Сцена");
        model.selectScene(scene);
        return model.addScreen("A", type.getId(), 2, 2, 0, 0);
    }

    @Test
    void interleavedMaskAndSchemaSheetEditsUndoOneByOne(@TempDir Path dir) {
        Screen scr = setUp(dir);

        model.setMaskMesh(scr, true);                                       // 1
        SchemaSheet second = model.addSchemaSheet(SchemaMode.POWER, "Вторая"); // 2
        model.setMaskCustomColors(scr, new Color(0x102030), new Color(0x405060)); // 3
        model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "B", 0, 0, null); // 4

        assertEquals(1, model.schemaNodesForCurrentScene(SchemaMode.POWER).size());

        model.undo(); // 4: блок во второй схеме исчез, лист и маска на месте
        assertEquals(0, model.schemaNodesForCurrentScene(SchemaMode.POWER).size());
        assertEquals(2, model.schemaSheets(SchemaMode.POWER).size());
        assertEquals(MaskColorPreset.CUSTOM, scr.getBackground());

        model.undo(); // 3: своя пара цветов откатилась, лист всё ещё есть
        assertFalse(scr.getBackground() == MaskColorPreset.CUSTOM);
        assertEquals(2, model.schemaSheets(SchemaMode.POWER).size());
        assertTrue(scr.isMaskMesh());

        model.undo(); // 2: лист исчез, «сетка» ещё включена
        assertEquals(1, model.schemaSheets(SchemaMode.POWER).size());
        assertTrue(scr.isMaskMesh());

        model.undo(); // 1: «сетка» выключена
        assertFalse(scr.isMaskMesh());
        assertEquals(1, model.schemaSheets(SchemaMode.POWER).size());
        assertSame(second.getMode(), SchemaMode.POWER);
    }

    @Test
    void multiStepUndoRestoresBothKindsAtOnce(@TempDir Path dir) {
        Screen scr = setUp(dir);

        model.addSchemaSheet(SchemaMode.SIGNAL, "Резерв");                  // 1
        model.setMaskMesh(scr, true);                                       // 2
        model.setMaskCustomColors(scr, new Color(0x111111), new Color(0x222222)); // 3

        model.undo(3); // всё разом: до первого действия

        assertEquals(1, model.schemaSheets(SchemaMode.SIGNAL).size());
        assertFalse(scr.isMaskMesh());
        assertFalse(scr.getBackground() == MaskColorPreset.CUSTOM);
    }

    @Test
    void deletedSchemaSheetComesBackWithItsNodesAfterMaskEdit(@TempDir Path dir) {
        Screen scr = setUp(dir);

        SchemaSheet second = model.addSchemaSheet(SchemaMode.POWER, "Вторая");
        model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.CUSTOM, "B1", 0, 0, null);
        model.deleteSchemaSheet(second);
        model.setMaskMesh(scr, true);

        model.undo(); // маска
        assertFalse(scr.isMaskMesh());
        assertEquals(1, model.schemaSheets(SchemaMode.POWER).size());

        model.undo(); // удаление листа: вернулся вместе с блоком
        assertEquals(2, model.schemaSheets(SchemaMode.POWER).size());
        model.selectSchemaSheet(model.schemaSheets(SchemaMode.POWER).get(1));
        assertEquals(1, model.schemaNodesForCurrentScene(SchemaMode.POWER).size());
    }
}
