package com.vjstb.ledscheme.ui.receiver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.ConnectionMapTables;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * «Карта подключения модулей» (docs/receiver-cards/PLAN.md, раздел 7, трек E): таблицы «карта → модель → место → номер →
 * модуль» и «блок → модель → место → шлейф → модуль / карта», картинки обоих видов в печатном стиле (белый фон), файлы
 * части вывода и видимость строки в таблице «Состав вывода» (только для проектов с модульным экраном).
 */
class ConnectionMapExportTest {

    private static final GridRect ALL_LEFT = new GridRect(0, 0, 1, 1);
    private static final GridRect RIGHT = new GridRect(0, 2, 1, 3);

    private ReceiverViewFixture modularProject(Path dir) {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 2, 4);
        f.model.assignReceiverCardsManual(ALL_LEFT, f.cardType.getId());
        f.model.assignReceiverCardsManual(RIGHT, f.cardType.getId());
        f.model.assignPowerSuppliesManual(new GridRect(0, 0, 1, 3), f.psuType.getId());
        return f;
    }

    @Test
    void cardsTableListsModulesInPortOrderWithModelAndPlace(@TempDir Path dir) {
        ReceiverViewFixture f = modularProject(dir);
        Screen screen = f.screen();

        ConnectionMapTables.Table t = ConnectionMapTables.cards(List.of(screen), f.model.getWorkspace(),
                ReceiverCardNumbering.DEFAULT, "{n}");

        assertEquals("Приёмные карты", t.sheetName());
        assertEquals(8, t.rows().size(), "две карты по 4 модуля, ни одного модуля без карты");
        Object[] first = t.rows().get(0);
        assertEquals("Э", first[0]);
        assertEquals("К1", first[1]);
        assertEquals("Карта А", first[2]);
        assertEquals(1, first[4], "первый по порядку модуль карты — номер 1");
        assertEquals(1, first[6], "верхний левый модуль: ряд 1");
        assertEquals(1, first[7], "столбец 1");
        Object[] ninthOfSecond = t.rows().get(7);
        assertEquals("К2", ninthOfSecond[1]);
        assertEquals(4, ninthOfSecond[4]);
        assertEquals(2, ninthOfSecond[6], "последний модуль карты К2 — правый нижний");
        assertEquals(4, ninthOfSecond[7]);
    }

    @Test
    void cardsTableUsesModuleNumberFormatAndMarksModulesWithoutCard(@TempDir Path dir) {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 2, 2);
        f.model.assignReceiverCardsManual(new GridRect(0, 0, 0, 1), f.cardType.getId());

        ConnectionMapTables.Table t = ConnectionMapTables.cards(List.of(f.screen()), f.model.getWorkspace(),
                ReceiverCardNumbering.DEFAULT, "{card}.{n}");

        assertEquals("К1.1", t.rows().get(0)[5], "обозначение на модуле — по шаблону Предпочтений");
        assertEquals(4, t.rows().size());
        Object[] orphan = t.rows().get(3);
        assertEquals(ConnectionMapTables.NO_CARD, orphan[1]);
        assertEquals(2, orphan[6], "модуль без карты — во втором ряду");
    }

    @Test
    void suppliesTableListsFeedsWithModulesAndCards(@TempDir Path dir) {
        ReceiverViewFixture f = modularProject(dir);

        ConnectionMapTables.Table t = ConnectionMapTables.supplies(List.of(f.screen()), f.model.getWorkspace(),
                ReceiverCardNumbering.DEFAULT);

        assertEquals("Блоки питания", t.sheetName());
        List<Object[]> rows = t.rows();
        long cardRows = rows.stream().filter(r -> String.valueOf(r[5]).startsWith("карта")).count();
        long moduleRows = rows.stream().filter(r -> "модуль".equals(r[5])).count();
        assertEquals(2, cardRows, "обе карты стоят в области единственного блока, он их питает");
        assertEquals(8, moduleRows, "все восемь модулей на шлейфах");
        Object[] row = rows.get(0);
        assertEquals("БП1", row[1]);
        assertEquals("Блок Б", row[2]);
        assertTrue(String.valueOf(row[3]).startsWith("ряд "), "место блока — ряд и столбец");
    }

    @Test
    void imagesArePrintedOnWhiteAndSizedByTheScreen(@TempDir Path dir) {
        ReceiverViewFixture f = modularProject(dir);

        BufferedImage cards = ConnectionMapExport.renderCards(f.scene, f.screen(), f.model.getWorkspace(),
                ReceiverCardNumbering.DEFAULT, "{n}", 2.0);
        BufferedImage supplies = ConnectionMapExport.renderSupplies(f.scene, f.screen(), f.model.getWorkspace(),
                ReceiverCardNumbering.DEFAULT, 2.0);

        for (BufferedImage img : List.of(cards, supplies)) {
            assertTrue(img.getWidth() > 300 && img.getHeight() > 200, img.getWidth() + "x" + img.getHeight());
            assertEquals(Color.WHITE.getRGB(), img.getRGB(2, 2), "печатный стиль: белый фон");
            assertEquals(Color.WHITE.getRGB(), img.getRGB(img.getWidth() - 3, img.getHeight() - 3));
        }
        boolean nonWhite = false;
        for (int x = 0; x < cards.getWidth() && !nonWhite; x += 7) {
            for (int y = 0; y < cards.getHeight(); y += 7) {
                if (cards.getRGB(x, y) != Color.WHITE.getRGB()) {
                    nonWhite = true;
                    break;
                }
            }
        }
        assertTrue(nonWhite, "на картинке нарисованы модули и карты");
    }

    @Test
    void exportWritesImagesPerModularScreenAndOneWorkbookPerScene(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = modularProject(dir);
        File sceneFolder = dir.resolve("out").toFile();
        List<String> written = new ArrayList<>();
        List<String> steps = new ArrayList<>();

        int files = ConnectionMapExport.export(f.scene, f.model.getWorkspace(), ReceiverCardNumbering.DEFAULT, "{n}",
                sceneFolder, 1.0, (img, d, base) -> {
                    ImageIO.write(img, "png", new File(d, base + ".png"));
                    written.add(base);
                }, steps::add);

        assertEquals(3, files, "картинка карт, картинка блоков и одна таблица");
        assertEquals(List.of("Э Карты", "Э Блоки питания"), written);
        assertEquals(3, steps.size());
        File dirOut = new File(sceneFolder, ConnectionMapExport.FOLDER_NAME);
        assertTrue(new File(dirOut, "Э Карты.png").isFile());
        File xlsx = new File(dirOut, "С Карта подключения.xlsx");
        assertTrue(xlsx.isFile());
        try (FileInputStream in = new FileInputStream(xlsx); Workbook wb = new XSSFWorkbook(in)) {
            Sheet cards = wb.getSheet("Приёмные карты");
            Sheet supplies = wb.getSheet("Блоки питания");
            assertNotNull(cards);
            assertNotNull(supplies);
            assertEquals("Экран", cards.getRow(0).getCell(0).getStringCellValue());
            assertEquals(8, cards.getLastRowNum(), "заголовок + 8 модулей");
            assertEquals(1.0, cards.getRow(1).getCell(4).getNumericCellValue(), "номер выхода — числовая ячейка");
        }
    }

    @Test
    void sceneWithoutModularScreensProducesNothing(@TempDir Path dir) throws Exception {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 2, 2);
        f.moduleType.setReceiverCardSeparate(false);
        File sceneFolder = dir.resolve("out").toFile();

        int files = ConnectionMapExport.export(f.scene, f.model.getWorkspace(), ReceiverCardNumbering.DEFAULT, "{n}",
                sceneFolder, 1.0, (img, d, base) -> {
                    throw new AssertionError("картинок быть не должно");
                }, null);

        assertEquals(0, files);
        assertFalse(new File(sceneFolder, ConnectionMapExport.FOLDER_NAME).exists());
        assertEquals(0, ConnectionMapExport.stepsFor(f.scene, f.model.getWorkspace()));
        assertFalse(ConnectionMapExport.projectHasModularScreen(f.model.getCurrentProject(), f.model.getWorkspace()));
    }

    // ------------------------------------------------------------------ строка в «Составе вывода»

    private static boolean partRowVisible(Component root, String text) {
        List<JLabel> labels = new ArrayList<>();
        collectLabels(root, labels);
        for (JLabel l : labels) {
            if (text.equals(l.getText())) {
                return l.isVisible();
            }
        }
        throw new AssertionError("нет строки «" + text + "»");
    }

    private static void collectLabels(Component c, List<JLabel> out) {
        if (c instanceof JLabel l) {
            out.add(l);
        }
        if (c instanceof Container cc) {
            for (Component k : cc.getComponents()) {
                collectLabels(k, out);
            }
        }
    }

    @Test
    void outputTableShowsConnectionMapRowOnlyForModularProjects(@TempDir Path dir) {
        ReceiverViewFixture f = new ReceiverViewFixture(dir, 2, 2);
        com.vjstb.ledscheme.ui.stage.OutputStagePanel panel =
                new com.vjstb.ledscheme.ui.stage.OutputStagePanel(f.model, f.settings);
        String label = com.vjstb.ledscheme.ui.stage.OutputPart.CONNECTION_MAP.label();
        assertTrue(partRowVisible(panel, label), "в проекте есть модульный экран");

        f.moduleType.setReceiverCardSeparate(false);
        f.model.updateCabinetType(f.moduleType);
        assertFalse(partRowVisible(panel, label), "модульных экранов не осталось — часть скрыта");
    }
}
