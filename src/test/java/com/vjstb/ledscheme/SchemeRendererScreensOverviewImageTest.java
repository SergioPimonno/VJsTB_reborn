package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.SchemeRenderer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** «Экспорт таблицы экранов…» (SetupStagePanel) — по образцу {@code
 *  SchemeRendererPortLegendImageTest}: только структурные инварианты размера
 *  (растёт с числом экранов/длиной подписи, не падает на пустом списке), не
 *  побайтовое сравнение (см. TESTS.md, скилл run-tests, конвенция визуальных схем). */
class SchemeRendererScreensOverviewImageTest {

    private static AppModel model(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        return model;
    }

    private static CabinetType type(String name, double w, double h, int rw, int rh, double weight) {
        CabinetType t = new CabinetType();
        t.setName(name);
        t.setWidthMm(w);
        t.setHeightMm(h);
        t.setResolutionWidth(rw);
        t.setResolutionHeight(rh);
        t.setWeightKg(weight);
        return t;
    }

    @Test
    void emptyScreenListStillProducesValidNonEmptyImage(@TempDir Path dir) {
        AppModel model = model(dir);
        BufferedImage img = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(), 1.0);
        assertTrue(img.getWidth() > 0);
        assertTrue(img.getHeight() > 0);
    }

    @Test
    void moreScreensProduceATallerImage(@TempDir Path dir) {
        AppModel model = model(dir);
        CabinetType t = model.addCabinetType(type("P3 500x500", 500, 500, 128, 128, 12));
        Screen s1 = model.addScreen("Экран 1", t.getId(), 2, 2, 0, 0);

        BufferedImage imgOne = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(s1), 1.0);

        Screen s2 = model.addScreen("Экран 2", t.getId(), 2, 2, 0, 0);
        Screen s3 = model.addScreen("Экран 3", t.getId(), 2, 2, 0, 0);
        BufferedImage imgThree = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(s1, s2, s3), 1.0);

        assertTrue(imgThree.getHeight() > imgOne.getHeight());
    }

    @Test
    void veryWideScreenWidensTheImage(@TempDir Path dir) {
        AppModel model = model(dir);
        CabinetType square = model.addCabinetType(type("Square", 500, 500, 128, 128, 12));
        CabinetType wide = model.addCabinetType(type("Wide", 500, 500, 128, 128, 12));
        Screen narrow = model.addScreen("Узкий", square.getId(), 2, 2, 0, 0);
        Screen veryWide = model.addScreen("Широкий", wide.getId(), 2, 20, 0, 0);

        BufferedImage imgNarrow = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(narrow), 1.0);
        BufferedImage imgWide = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(veryWide), 1.0);

        assertTrue(imgWide.getWidth() > imgNarrow.getWidth());
    }

    @Test
    void dpiScaleMultipliesPixelDimensions(@TempDir Path dir) {
        AppModel model = model(dir);
        CabinetType t = model.addCabinetType(type("P3 500x500", 500, 500, 128, 128, 12));
        Screen s = model.addScreen("Экран 1", t.getId(), 2, 2, 0, 0);

        BufferedImage img1x = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(s), 1.0);
        BufferedImage img2x = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(s), 2.0);

        assertTrue(img2x.getWidth() >= img1x.getWidth() * 2 - 2);
        assertTrue(img2x.getHeight() >= img1x.getHeight() * 2 - 2);
    }

    @Test
    void mountTypeAndWeightAreReflectedWithoutCrashing(@TempDir Path dir) {
        AppModel model = model(dir);
        CabinetType t = model.addCabinetType(type("P3 500x500", 500, 500, 128, 128, 12));
        Screen s = model.addScreen("Экран 1", t.getId(), 2, 2, 0, 0, ScreenMountType.FLOOR);

        BufferedImage img = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(s), 1.0);
        assertTrue(img.getWidth() > 0);
    }

    @Test
    void notesWidenTheImageWhenPresent(@TempDir Path dir) {
        // Примечания берутся из общего Screen.notes (не riggingNotes/structureNotes,
        // которые видны только при соответствующем типе монтажа — баг-репорт: "для
        // экранов сейчас негде писать примечания").
        AppModel model = model(dir);
        CabinetType t = model.addCabinetType(type("P3 500x500", 500, 500, 128, 128, 12));
        Screen withoutNotes = model.addScreen("Экран 1", t.getId(), 1, 1, 0, 0, ScreenMountType.RIGGED);
        BufferedImage imgWithout = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(withoutNotes), 1.0);

        Screen withNotes = model.addScreen("Экран 2", t.getId(), 1, 1, 0, 0, ScreenMountType.RIGGED);
        withNotes.setNotes("Точки согласованы с площадкой, см. акт от 12.03 — не переносить без пересчёта");
        BufferedImage imgWith = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(withNotes), 1.0);

        assertTrue(imgWith.getWidth() > imgWithout.getWidth(),
                "непустое примечание должно расширить колонку/картинку по сравнению с прочерком");
    }

    @Test
    void loadIsFormattedInKilowattsAboveOneKilowatt(@TempDir Path dir) {
        AppModel model = model(dir);
        // 12 кг веса тут ни при чём — мощность отдельное поле CabinetType, берём с
        // запасом (см. addPowerConnectorToNode-независимый путь: powerConsumptionW).
        CabinetType t = type("Heavy 500x500", 500, 500, 128, 128, 12);
        t.setPowerConsumptionW(600); // 2×2 кабинета × 600 Вт = 2400 Вт = 2.4 кВт
        model.addCabinetType(t);
        Screen s = model.addScreen("Экран 1", t.getId(), 2, 2, 0, 0);

        BufferedImage img = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(s), 1.0);
        assertTrue(img.getWidth() > 0, "не должно падать при мощности выше 1 кВт");
    }

    @Test
    void screensAtRealPositionsAreNotForcedIntoAUniformRow(@TempDir Path dir) {
        // Баг-репорт: "раскладка экранов пусть соответствует той что в окне
        // сетапа, не просто же так инженер их там расставляет" — раньше слоты
        // экранов шли слева направо независимо от их реальных X/Y; теперь
        // раздвинутые по сцене экраны должны раздвигать и картинку.
        AppModel model = model(dir);
        CabinetType t = model.addCabinetType(type("P3 500x500", 500, 500, 128, 128, 12));
        Screen a = model.addScreen("A", t.getId(), 1, 1, 0, 0);
        Screen b = model.addScreen("B", t.getId(), 1, 1, 0, 0);
        BufferedImage overlapping = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(a, b), 1.0);

        model.updateScreenPosition(b, 8000, 0);
        BufferedImage apart = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(a, b), 1.0);

        assertTrue(apart.getWidth() > overlapping.getWidth(),
                "раскладка должна учитывать реальные X/Y экранов, а не выстраивать их в ряд независимо от позиции");
    }

    @Test
    void screenWithATypeOverrideOnOneCabinetRendersWithoutCrashing(@TempDir Path dir) {
        // Вид "кабинеты по отдельности" теперь реально рисует paintScheme — эта
        // проверка ловит регресс, если кто-то в будущем вернёт отрисовку плоскими
        // прямоугольниками, минуя реальные типы/переопределения ячеек.
        AppModel model = model(dir);
        CabinetType wide = model.addCabinetType(type("Wide 500x500", 500, 500, 128, 128, 12));
        CabinetType narrow = model.addCabinetType(type("MG14 250x500", 250, 500, 64, 128, 7));
        Screen s = model.addScreen("Экран 1", wide.getId(), 1, 2, 0, 0);
        model.selectScreen(s);
        model.setCabinetTypeOverride(s.cabinetAt(0, 1).getId(), narrow.getId());

        BufferedImage img = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(s), 1.0);
        assertTrue(img.getWidth() > 0);
        assertTrue(img.getHeight() > 0);
    }
}
