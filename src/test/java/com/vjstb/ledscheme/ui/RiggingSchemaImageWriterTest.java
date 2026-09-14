package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.RiggingCalc;
import com.vjstb.ledscheme.service.TrussCalc;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Смоук-тест {@link RiggingSchemaImageWriter} — баг-репорт «экспортируемая схема не
 *  информативна» заменил экспорт общего вида сцены на сетку ячеек экрана + точки
 *  подвеса + таблицу нагрузки (см. class-javadoc). Проверяет только структурные
 *  инварианты размера картинки (растёт с шириной экрана и числом точек) — не
 *  побайтовое сравнение, т.к. это просто визуальная схема, не бинарный формат
 *  с внешними потребителями (в отличие от NovaLCT). */
class RiggingSchemaImageWriterTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    private CabinetType type() {
        CabinetType ct = new CabinetType();
        ct.setName("Test");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setWeightKg(10);
        return ct;
    }

    @Test
    void renderProducesNonEmptyImageSizedToScreenAndPoints(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 2, 6, 0, 0);

        RiggingCalc.Result result = RiggingCalc.compute(screen, t, model.getWorkspace(), 3);
        TrussCalc.Result truss = TrussCalc.compute(screen, t, model.getWorkspace());
        BufferedImage img = RiggingSchemaImageWriter.render(screen, t, result, truss);

        assertTrue(img.getWidth() >= screen.getCols() * 22, "картинка обязана вмещать всю ширину сетки экрана");
        assertTrue(img.getHeight() > screen.getRows() * 22, "картинка обязана включать не только сетку, но и таблицу под ней");
    }

    @Test
    void renderGrowsWiderWithMorePoints(@TempDir Path dir) {
        // Таблица транспонирована по фидбеку пользователя (точка/X/нагрузка -- три
        // СТРОКИ, одна КОЛОНКА на точку) -- высота таблицы фиксирована (3 строки),
        // растёт с числом точек именно ШИРИНА картинки, не высота.
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 4, 0, 0);

        TrussCalc.Result truss = TrussCalc.compute(screen, t, model.getWorkspace());
        BufferedImage fewPoints = RiggingSchemaImageWriter.render(
                screen, t, RiggingCalc.compute(screen, t, model.getWorkspace(), 2), truss);
        BufferedImage manyPoints = RiggingSchemaImageWriter.render(
                screen, t, RiggingCalc.compute(screen, t, model.getWorkspace(), 8), truss);

        assertTrue(manyPoints.getWidth() > fewPoints.getWidth(),
                "больше колонок в транспонированной таблице -- картинка должна быть шире");
    }

    @Test
    void renderHandlesHiddenCabinetsAndZeroWidthType(@TempDir Path dir) {
        // Скрытые ячейки (арки/вырезы) и defaultType==null (виден в SetupStagePanel,
        // когда тип экрана ещё не определён) не должны падать делением на ноль/NPE.
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 4, 0, 0);
        screen.cabinetAt(0, 3).setHidden(true);

        RiggingCalc.Result result = RiggingCalc.compute(screen, t, model.getWorkspace(), 2);
        TrussCalc.Result trussWithType = TrussCalc.compute(screen, t, model.getWorkspace());
        TrussCalc.Result trussWithoutType = TrussCalc.compute(screen, null, model.getWorkspace());
        BufferedImage withType = RiggingSchemaImageWriter.render(screen, t, result, trussWithType);
        BufferedImage withoutType = RiggingSchemaImageWriter.render(screen, null, result, trussWithoutType);

        assertTrue(withType.getWidth() > 0 && withoutType.getWidth() > 0);
    }
}
