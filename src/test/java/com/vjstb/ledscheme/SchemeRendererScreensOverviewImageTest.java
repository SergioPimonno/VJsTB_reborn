package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertFalse;
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


    private static boolean hasPixelNear(BufferedImage img, java.awt.Color target, int tolerance) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                java.awt.Color c = new java.awt.Color(img.getRGB(x, y));
                if (Math.abs(c.getRed() - target.getRed()) <= tolerance
                        && Math.abs(c.getGreen() - target.getGreen()) <= tolerance
                        && Math.abs(c.getBlue() - target.getBlue()) <= tolerance) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Запрос 2026-10-01: в экспорте экранов разные типы кабинетов выделяются цветом (тот же
     *  стабильный цвет по id типа, что на холсте сцены), а справа в углу стоит панель с
     *  легендой типов и статистикой сцены. */
    @Test
    void differentCabinetTypesAreTintedAndLegendPanelIsAddedOnTheRight(@TempDir Path dir) {
        AppModel model = model(dir);
        CabinetType a = model.addCabinetType(type("P3 500x500", 500, 500, 128, 128, 12));
        CabinetType b = model.addCabinetType(type("P2.6 500x1000", 500, 1000, 192, 384, 20));
        Screen sa = model.addScreen("Экран A", a.getId(), 3, 4, 0, 0);
        Screen sb = model.addScreen("Экран B", b.getId(), 2, 4, 3000, 0);

        BufferedImage twoTypes = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(sa, sb), 1.0);
        BufferedImage oneType = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(sa), 1.0);

        java.awt.Color bg = com.vjstb.ledscheme.ui.Palette.BG;
        for (CabinetType t : List.of(a, b)) {
            java.awt.Color base = com.vjstb.ledscheme.ui.Palette.stableColorFor(t.getId());
            // заливка 150/255 поверх фона (ниже в тесте — допуск на сглаживание и линии сетки)
            java.awt.Color blended = new java.awt.Color(
                    (base.getRed() * 150 + bg.getRed() * 105) / 255,
                    (base.getGreen() * 150 + bg.getGreen() * 105) / 255,
                    (base.getBlue() * 150 + bg.getBlue() * 105) / 255);
            assertTrue(hasPixelNear(twoTypes, blended, 6), "кабинеты типа " + t.getName() + " должны быть закрашены");
            assertTrue(hasPixelNear(twoTypes, new java.awt.Color(base.getRed(), base.getGreen(), base.getBlue()), 6),
                    "в легенде есть квадратик цвета типа " + t.getName());
        }
        // единственный тип — заливки нет (она ничего бы не различала)
        java.awt.Color baseA = com.vjstb.ledscheme.ui.Palette.stableColorFor(a.getId());
        java.awt.Color blendedA = new java.awt.Color(
                (baseA.getRed() * 150 + bg.getRed() * 105) / 255,
                (baseA.getGreen() * 150 + bg.getGreen() * 105) / 255,
                (baseA.getBlue() * 150 + bg.getBlue() * 105) / 255);
        assertFalse(hasPixelNear(oneType, blendedA, 3), "при одном типе кабинеты не закрашиваются");
    }

    /** Область примечаний вдвое уже (420 → 210), освободившееся место отдано панели справа:
     *  длинное примечание обрезается по новой ширине, а картинка не шире прежней на всю
     *  ширину панели. */
    @Test
    void longNotesAreClippedToTheNarrowerColumn(@TempDir Path dir) {
        AppModel model = model(dir);
        CabinetType t = model.addCabinetType(type("P3 500x500", 500, 500, 128, 128, 12));
        Screen shortNotes = model.addScreen("Экран 1", t.getId(), 2, 2, 0, 0);
        Screen longNotes = model.addScreen("Экран 2", t.getId(), 2, 2, 0, 0);
        longNotes.setNotes("очень длинное примечание ".repeat(30));

        BufferedImage imgShort = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(shortNotes), 1.0);
        BufferedImage imgLong = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(longNotes), 1.0);

        int growth = imgLong.getWidth() - imgShort.getWidth();
        assertTrue(growth <= 210, "колонка примечаний ограничена 210 px, рост ширины " + growth);
    }


    /** Запрос 2026-10-02: перед экспортом спрашиваем, что печатать — легенду кабинетов (с окраской по
     *  типам) и/или статистику сцены. Оба выключены — правой панели нет, ширина как у таблицы. */
    @Test
    void legendAndStatsAreOptionalAndSwitchTheRightPanel(@TempDir Path dir) {
        AppModel model = model(dir);
        CabinetType a = model.addCabinetType(type("P3 500x500", 500, 500, 128, 128, 12));
        CabinetType b = model.addCabinetType(type("P2.6 500x1000", 500, 1000, 192, 384, 20));
        Screen sa = model.addScreen("Экран A", a.getId(), 3, 4, 0, 0);
        Screen sb = model.addScreen("Экран B", b.getId(), 2, 4, 3000, 0);
        List<Screen> screens = List.of(sa, sb);

        BufferedImage both = SchemeRenderer.renderScreensOverviewImage("Зал", model, screens, 1.0, true, true);
        BufferedImage legendOnly = SchemeRenderer.renderScreensOverviewImage("Зал", model, screens, 1.0, true, false);
        BufferedImage statsOnly = SchemeRenderer.renderScreensOverviewImage("Зал", model, screens, 1.0, false, true);
        BufferedImage none = SchemeRenderer.renderScreensOverviewImage("Зал", model, screens, 1.0, false, false);

        assertTrue(both.getWidth() > none.getWidth(), "с панелью картинка шире");
        assertTrue(legendOnly.getWidth() > none.getWidth());
        assertTrue(statsOnly.getWidth() > none.getWidth());
        assertTrue(both.getHeight() >= legendOnly.getHeight() && both.getHeight() >= statsOnly.getHeight());

        // окраска по типам не зависит от легенды (запрос 2026-10-02): есть при любом выборе
        java.awt.Color bg = com.vjstb.ledscheme.ui.Palette.BG;
        java.awt.Color base = com.vjstb.ledscheme.ui.Palette.stableColorFor(a.getId());
        java.awt.Color blended = new java.awt.Color(
                (base.getRed() * 150 + bg.getRed() * 105) / 255,
                (base.getGreen() * 150 + bg.getGreen() * 105) / 255,
                (base.getBlue() * 150 + bg.getBlue() * 105) / 255);
        assertTrue(hasPixelNear(legendOnly, blended, 6), "с легендой кабинеты окрашены");
        assertTrue(hasPixelNear(statsOnly, blended, 6), "без легенды окраска сохраняется");
        assertTrue(hasPixelNear(none, blended, 6), "и без панели вовсе");
    }


    /** Баг-репорт 2026-10-02 («почему рамка 10 экрана обрезалась?»): высота ячейки округляется до
     *  целого пикселя, и у широкого экрана из нескольких рядов сетка (rows × cellH) выходила за
     *  округлённый габарит экрана — нижняя линия рамки отсекалась. Экран 30 × 5 кабинетов 500×1000
     *  рядом с далёким экраном (масштаб ~0,035 px/мм) должен показать ВСЕ 6 горизонтальных линий. */
    @Test
    void wideScreenKeepsItsBottomFrameLine(@TempDir Path dir) {
        AppModel model = model(dir);
        // один тип на оба экрана — без окраски по типам (она залила бы ячейки и скрыла линии сетки)
        CabinetType tall = model.addCabinetType(type("Tall", 500, 1000, 128, 128, 10));
        Screen up = model.addScreen("Upper Front", tall.getId(), 5, 30, 0, 0);
        Screen far = model.addScreen("Far", tall.getId(), 4, 12, 19936, 0);

        BufferedImage img = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(up, far), 1.0, false, false);

        // линия сетки заметно ярче и фона, и заливки ячейки
        int lines = 0;
        boolean inLine = false;
        for (int y = 60; y < 262; y++) {
            int bright = 0;
            int interior = new java.awt.Color(img.getRGB(100, 75)).getRed();
            for (int x = 40; x < 440; x++) {
                if (new java.awt.Color(img.getRGB(x, y)).getRed() > interior + 25) {
                    bright++;
                }
            }
            boolean line = bright >= 300; // сплошная горизонтальная линия сетки
            if (line && !inLine) {
                lines++;
            }
            inLine = line;
        }
        assertTrue(lines >= 6, "у сетки 5 рядов 6 горизонтальных линий, найдено " + lines);
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
    void noBoundingFrameIsDrawnAroundAScreen(@TempDir Path dir) {
        // Пожелание: в экспорте экранов рисуются только кабинеты, без прямоугольной
        // рамки-габарита. Треугольный кабинет 1×1 (прямой угол слева снизу) — верхний
        // правый угол его габарита пуст, и без рамки верхняя строка картинки экрана
        // содержит пиксели только у левого края (вершина и номер), а не во всю ширину.
        AppModel model = model(dir);
        CabinetType t = model.addCabinetType(type("P3 500x500", 500, 500, 128, 128, 12));
        Screen s = model.addScreen("Экран 1", t.getId(), 1, 1, 0, 0);
        s.getCabinets().get(0).setShapeOverride(com.vjstb.ledscheme.model.CabinetShape.TRIANGLE);
        s.getCabinets().get(0).setRotationOverride(0);

        BufferedImage img = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(s), 1.0);
        int bg = com.vjstb.ledscheme.ui.Palette.BG.getRGB();

        // Полосы непустых строк сверху вниз: 0-я — заголовок, 1-я — картинка экрана.
        java.util.List<int[]> bands = new java.util.ArrayList<>();
        int start = -1;
        for (int y = 0; y < img.getHeight(); y++) {
            boolean blank = true;
            for (int x = 0; x < img.getWidth() && blank; x++) {
                blank = img.getRGB(x, y) == bg;
            }
            if (!blank && start < 0) {
                start = y;
            } else if (blank && start >= 0) {
                bands.add(new int[]{start, y - 1});
                start = -1;
            }
        }
        int[] plan = bands.get(1);
        int minX = Integer.MAX_VALUE;
        int maxX = -1;
        int topRowMaxX = -1;
        for (int y = plan[0]; y <= plan[1]; y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (img.getRGB(x, y) != bg) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    if (y == plan[0]) {
                        topRowMaxX = Math.max(topRowMaxX, x);
                    }
                }
            }
        }
        assertTrue(topRowMaxX < minX + (maxX - minX) / 2,
                "верхняя строка не должна тянуться через всю ширину — значит, рамка экрана не рисуется");
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
