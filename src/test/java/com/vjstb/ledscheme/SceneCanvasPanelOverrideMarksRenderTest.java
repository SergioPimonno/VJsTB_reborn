package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.Palette;
import com.vjstb.ledscheme.ui.SceneCanvasPanel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Баг-репорт пользователя: отредактировал в библиотеке размеры типа кабинета
 *  (по образцу MG14/MG15 — сузил с 500 до 250 мм по ширине) и назначил его
 *  ПЕРЕОПРЕДЕЛЕНИЕМ типа отдельной ячейке экрана ("Тип" в радиальном меню
 *  {@link SceneCanvasPanel#showCabinetRadialMenu} / {@code AppModel#
 *  setCabinetTypeOverride}) — сам контур ячейки на схеме (детальный вид,
 *  {@code SchemeRenderer#paintScheme}) корректно уменьшился (учитывает
 *  {@code ScreenLogic#effectiveCellW/H}), а подсветка переопределения поверх
 *  ({@link SceneCanvasPanel#drawCabinetOverrideMarks}) — нет, оставалась
 *  нарисованной на всю НОМИНАЛЬНУЮ (старую) ширину ячейки экрана, визуально
 *  маскируя то, что кабинет уже уменьшился. Тест рендерит панель напрямую
 *  (как {@code NetworkCanvasPanelRenderTest}) и проверяет пиксели: подсветка
 *  обязана быть ровно внутри РЕАЛЬНОГО (уменьшенного) контура, а не за его
 *  пределами в старых номинальных границах ячейки. */
class SceneCanvasPanelOverrideMarksRenderTest {

    private AppModel model(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
    }

    private SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void overrideHighlightShrinksWithNarrowerOverriddenCabinetType(@TempDir Path dir) {
        AppModel model = model(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));

        CabinetType wide = new CabinetType();
        wide.setName("Wide 500x500");
        wide.setWidthMm(500);
        wide.setHeightMm(500);
        model.addCabinetType(wide);

        // MG14/MG15-подобный тип из баг-репорта: вдвое ýже (250 мм), та же высота.
        CabinetType narrow = new CabinetType();
        narrow.setName("MG14 250x500");
        narrow.setWidthMm(250);
        narrow.setHeightMm(500);
        model.addCabinetType(narrow);

        Screen screen = model.addScreen("E", wide.getId(), 1, 1, 0, 0);
        model.selectScreen(screen);
        String cabId = screen.getCabinets().get(0).getId();
        model.setCabinetTypeOverride(cabId, narrow.getId());

        SceneCanvasPanel canvas = new SceneCanvasPanel(model, settings(dir));
        canvas.setDetailMode(true, false);
        BufferedImage img = canvas.renderImage(300, 300);

        // Геометрия детального вида (см. SceneCanvasPanel: PADDING=40,
        // DETAIL_PX_PER_MM=0.25, detailZoom=1.0 по умолчанию): единственная ячейка
        // экрана начинается в (40,40), номинальная ширина 500мм*0.25=125px,
        // эффективная (переопределённый тип вдвое ýже) — round(125*250/500)=63px.
        int gridX = 40;
        int gridY = 40;
        int nominalCellW = 125;
        int effectiveCellW = 63;

        // Внутри РЕАЛЬНОГО (уменьшенного) контура — подсветка обязана быть видна,
        // иначе тест ничего не проверяет. Точка взята с запасом от краёв ячейки,
        // чтобы не попасть под сглаживание контура/заливки.
        int insideX = gridX + effectiveCellW / 2;
        int insideY = gridY + nominalCellW / 2;
        assertNotEquals(Palette.BG.getRGB(), img.getRGB(insideX, insideY),
                "подсветка переопределённого типа должна закрашивать его реальный (уменьшенный) контур");

        // Правее — уже за пределами уменьшенного кабинета, но внутри его СТАРОЙ
        // номинальной ширины: до фикса drawCabinetOverrideMarks красил подсветку
        // на всю номинальную cellW/cellH, а не на эффективный (уменьшенный)
        // размер, поэтому здесь ничего рисоваться не должно — фон экрана.
        int outsideX = gridX + (effectiveCellW + nominalCellW) / 2;
        int outsideY = insideY;
        assertEquals(Palette.BG.getRGB(), img.getRGB(outsideX, outsideY),
                "подсветка не должна вылезать за пределы реального (уменьшенного) контура кабинета"
                        + " — иначе на схеме кажется, что размер типа не изменился");
    }
}
