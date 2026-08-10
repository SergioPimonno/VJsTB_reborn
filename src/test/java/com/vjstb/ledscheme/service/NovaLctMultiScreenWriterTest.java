package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Golden-байтовые регрессионные тесты {@link NovaLctScrWriter#writeStandardMultiScreen} — фиксируют
 * РЕАЛЬНЫЙ вывод NovaLCT (не наш собственный), как {@code NovaLctScrWriterTest} для одноэкранного
 * писателя. Раньше (до этой сессии) единственным реальным образцом был {@code 2scr.scr} — два ПУСТЫХ
 * экрана (0 кабинетных записей, placeholder-подобные поля), подтверждавший только структуру
 * контейнера, не семантику полей. Теперь есть 3 НЕПУСТЫХ образца, специально подготовленных и
 * сохранённых пользователем из реальной NovaLCT для проверки именно этого писателя:
 * <ul>
 *   <li>{@code twoPlainScreens} — 2 независимых экрана разного размера с ненулевым зазором
 *   Coordinate X между ними (проверяет, что офсет — реальная пиксельная позиция на канвасе, не
 *   просто визуальный порядок).</li>
 *   <li>{@code threePlusScreens} — 3 экрана (формула хвостового блока {@code 133+40×N} раньше была
 *   экстраполирована всего по 2 точкам N=1,2).</li>
 *   <li>{@code multiCardAcrossScreens} — 2 экрана на РАЗНЫХ физических Sending Card (card0/card1),
 *   не просто разных портах одной карты.</li>
 * </ul>
 * Байтовый разбор всех четырёх (включая упомянутый выше {@code 2scr.scr}) привёл к пересмотру
 * структурной модели метода — см. подробности в его javadoc.
 */
class NovaLctMultiScreenWriterTest {

    @Test
    void rejectsEmptyScreenList() {
        assertThrows(IllegalArgumentException.class, () -> NovaLctScrWriter.writeStandardMultiScreen(List.of()));
    }

    @Test
    void twoScreens_headerReportsRealScreenCountAndSharedJsonHasBothEntries() {
        NovaLctScrWriter.CellKey origin = new NovaLctScrWriter.CellKey(0, 0);
        NovaLctScrWriter.Rec rec = new NovaLctScrWriter.Rec(0, 0, 0, 0, 0);

        NovaLctScrWriter.ScreenBlock s0 = new NovaLctScrWriter.ScreenBlock(1, 1, 128, 128, 0,
                Map.of(origin, rec));
        NovaLctScrWriter.ScreenBlock s1 = new NovaLctScrWriter.ScreenBlock(1, 1, 128, 128, 128,
                Map.of(origin, rec));

        byte[] data = NovaLctScrWriter.writeStandardMultiScreen(List.of(s0, s1));

        // SCREENCOUNT (см. javadoc метода про ScreenInfoRelativeAddress) -- абсолютный 0x13A.
        assertEquals(2, data[0x13a] & 0xff, "Заголовок должен указывать реальное число экранов");

        // Хвостовой блок коррекции -- 133+40*N (см. javadoc), последние байты файла.
        int expectedFooterLen = 133 + 40 * 2;
        byte[] tail = new byte[expectedFooterLen];
        System.arraycopy(data, data.length - expectedFooterLen, tail, 0, expectedFooterLen);
        assertEquals((byte) 0xea, tail[0]);
        assertEquals((byte) 0x03, tail[1]);
        // tail[2:4] (LE16) = 231*N -- подтверждено точно по 2scr.scr (462=0x01CE для N=2).
        int tag = (tail[2] & 0xff) | ((tail[3] & 0xff) << 8);
        assertEquals(231 * 2, tag);
        assertEquals(2, tail[132] & 0xff, "tail[132] должен быть реальным числом экранов, не всегда 1");
        // Повтор паттерна (1,_,1,0xe4) на +40 для каждого доп. экрана (см. javadoc).
        assertEquals(1, tail[134] & 0xff);
        assertEquals(1, tail[136] & 0xff);
        assertEquals(0xe4, tail[137] & 0xff);
        assertEquals(1, tail[174] & 0xff);
        assertEquals(1, tail[176] & 0xff);
        assertEquals(0xe4, tail[177] & 0xff);

        // Общий JSON должен содержать оба "si":N объекта, оба экрана 1x1 -- 0 записей каждый
        // (единственная ячейка (0,0) экрана НИКОГДА не пишется отдельной 17-байтной записью,
        // см. class-javadoc NovaLctScrWriter), поэтому файл заведомо короткий и JSON легко найти.
        String asText = new String(data, StandardCharsets.US_ASCII);
        assertTrue(asText.contains("\"si\":0"), "JSON должен содержать запись экрана 0");
        assertTrue(asText.contains("\"si\":1"), "JSON должен содержать запись экрана 1");
    }

    @Test
    void singleScreenInList_stillGoesThroughMultiScreenPathWithScreenCountOne() {
        NovaLctScrWriter.CellKey origin = new NovaLctScrWriter.CellKey(0, 0);
        NovaLctScrWriter.ScreenBlock s0 = new NovaLctScrWriter.ScreenBlock(1, 1, 128, 128, 0, Map.of());

        byte[] data = NovaLctScrWriter.writeStandardMultiScreen(List.of(s0));

        assertEquals(1, data[0x13a] & 0xff);
        int expectedFooterLen = 133 + 40;
        assertEquals(HEADER_LEN_FOR_TEST + expectedFooterLenExtras(0) + expectedFooterLen, data.length);
    }

    private static final int HEADER_LEN_FOR_TEST = 0x154;

    private static int expectedFooterLenExtras(int recordCount0) {
        // anchor(6) + jsonLen(2) + json(66 для одного пустого si:0 объекта) -- без доп. экранов.
        String json = "[{\"si\":0,\"x1\":0,\"y1\":0,\"x2\":0,\"y2\":0,\"x3\":0,\"y3\":0,\"x4\":0,\"y4\":0}]";
        return recordCount0 * 17 + 6 + 2 + json.length();
    }

    /** Простое расключение "сверху вниз по столбцу" (как в реальных образцах ниже — пользователь
     *  прописывал их не серпантином, а по столбцам подряд) — cell(0,0) получает seq 0 неявно (см.
     *  class-javadoc NovaLctScrWriter, для неё запись не пишется). */
    private static Map<NovaLctScrWriter.CellKey, NovaLctScrWriter.Rec> columnMajorChain(
            int cols, int rows, int card, int port) {
        Map<NovaLctScrWriter.CellKey, NovaLctScrWriter.Rec> cells = new java.util.HashMap<>();
        int seq = 0;
        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                cells.put(new NovaLctScrWriter.CellKey(col, row),
                        new NovaLctScrWriter.Rec(row, col, card, port, seq));
                seq++;
            }
        }
        return cells;
    }

    @Test
    void matchesRealSample_twoPlainScreens_differentSizesWithCanvasGap() {
        NovaLctScrWriter.ScreenBlock a = new NovaLctScrWriter.ScreenBlock(3, 2, 128, 128, 0,
                columnMajorChain(3, 2, 0, 0));
        NovaLctScrWriter.ScreenBlock b = new NovaLctScrWriter.ScreenBlock(2, 3, 128, 128, 640,
                columnMajorChain(2, 3, 0, 1));

        byte[] actual = NovaLctScrWriter.writeStandardMultiScreen(List.of(a, b));
        byte[] expected = decode(TWO_PLAIN_SCREENS_BASE64);
        assertArrayEquals(expected, actual);
    }

    @Test
    void matchesRealSample_threePlusScreens() {
        NovaLctScrWriter.ScreenBlock a = new NovaLctScrWriter.ScreenBlock(2, 2, 128, 128, 0,
                columnMajorChain(2, 2, 0, 0));
        NovaLctScrWriter.ScreenBlock b = new NovaLctScrWriter.ScreenBlock(2, 2, 128, 128, 512,
                columnMajorChain(2, 2, 0, 1));
        NovaLctScrWriter.ScreenBlock c = new NovaLctScrWriter.ScreenBlock(2, 2, 128, 128, 1024,
                columnMajorChain(2, 2, 0, 2));

        byte[] actual = NovaLctScrWriter.writeStandardMultiScreen(List.of(a, b, c));
        byte[] expected = decode(THREE_PLUS_SCREENS_BASE64);
        assertArrayEquals(expected, actual);
    }

    @Test
    void matchesRealSample_multiCardAcrossScreens_differentSendingCards() {
        NovaLctScrWriter.ScreenBlock x = new NovaLctScrWriter.ScreenBlock(2, 2, 128, 128, 0,
                columnMajorChain(2, 2, 0, 0));
        NovaLctScrWriter.ScreenBlock y = new NovaLctScrWriter.ScreenBlock(2, 2, 128, 128, 512,
                columnMajorChain(2, 2, 1, 0));

        byte[] actual = NovaLctScrWriter.writeStandardMultiScreen(List.of(x, y));
        byte[] expected = decode(MULTI_CARD_ACROSS_SCREENS_BASE64);
        assertArrayEquals(expected, actual);
    }

    private static byte[] decode(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    private static final String TWO_PLAIN_SCREENS_BASE64 =
            "RFNDSSw5gAAAAPIBAADVAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA6QP8AAEBkAZgBAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADuA+8xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAbQEAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACcAAAAHAAAAABAAAAAAADAAIAAAAAAAAAAAAAAAAAgACAAAEAAAEAAACAAAAA"
            + "AQCAAIAAAQAAAgCAAAAAAQAAAIAAgAABAAADAIAAgAABAAEAgACAAAEAAAQAAAEAAAIAAACAAIAAAQAABQAAAYAAAgABAIAA"
            + "gAABAQCAAgAAAgADAAABAACAAgAAAAAAAIAAgAABAAEBAIACgAAAAAEAgACAAAEAAQIAgAIAAQAAAgCAAIAAAQABAwAAAwAA"
            + "AQAAAIAAgAABAAEEAAADgAABAAEAgACAAAEAAQUAAAMAAQEAAgCAAIAAAYMAW3sic2kiOjAsIngxIjowLCJ5MSI6MCwieDIi"
            + "OjAsInkyIjowLCJ4MyI6MCwieTMiOjAsIng0IjowLCJ5NCI6MH0seyJzaSI6MSwieDEiOjAsInkxIjowLCJ4MiI6MCwieTIi"
            + "OjAsIngzIjowLCJ5MyI6MCwieDQiOjAsInk0IjowfV3qA84BAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAACAAEAAeQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAHkAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private static final String THREE_PLUS_SCREENS_BASE64 =
            "RFNDSRhIgAAAAEECAAD9AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA6QP8AAEBkAZgBAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADuA6FBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAewEAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADTgAAAE4AAABOAAAAAQAAAAAAAgACAAAAAAAAAAAAAAAAAIAAgAABAAABAAAA"
            + "gAAAAAEAgACAAAEAAAIAgAAAAAEAAACAAIAAAQAAAwCAAIAAAQABAIAAgAABAQAAAgAAAgACAAABAAAAAgAAAAAAAIAAgAAB"
            + "AAEBAAACgAAAAAEAgACAAAEAAQIAgAIAAAEAAACAAIAAAQABAwCAAoAAAQABAIAAgAABAQAABAAAAgACAAACAAAABAAAAAAA"
            + "AIAAgAABAAIBAAAEgAAAAAEAgACAAAEAAgIAgAQAAAEAAACAAIAAAQACAwCABIAAAQABAIAAgAABxABbeyJzaSI6MCwieDEi"
            + "OjAsInkxIjowLCJ4MiI6MCwieTIiOjAsIngzIjowLCJ5MyI6MCwieDQiOjAsInk0IjowfSx7InNpIjoxLCJ4MSI6MCwieTEi"
            + "OjAsIngyIjowLCJ5MiI6MCwieDMiOjAsInkzIjowLCJ4NCI6MCwieTQiOjB9LHsic2kiOjIsIngxIjowLCJ5MSI6MCwieDIi"
            + "OjAsInkyIjowLCJ4MyI6MCwieTMiOjAsIng0IjowLCJ5NCI6MH1d6gO1AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAwABAAHkAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAB"
            + "5AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAeQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAA==";

    private static final String MULTI_CARD_ACROSS_SCREENS_BASE64 =
            "RFNDSXQygAAAAK4BAADVAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA6QP8AAEBkAZgBAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADuA7grAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAKQEAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACTgAAAE4AAAABAAAAAAACAAIAAAAAAAAAAAAAAAAAgACAAAEAAAEAAACAAAAA"
            + "AQCAAIAAAQAAAgCAAAAAAQAAAIAAgAABAAADAIAAgAABAAEAgACAAAEBAAACAAACAAIAAQAAAAACAAAAAAAAgACAAAEBAAEA"
            + "AAKAAAAAAQCAAIAAAQEAAgCAAgAAAQAAAIAAgAABAQADAIACgAABAAEAgACAAAGDAFt7InNpIjowLCJ4MSI6MCwieTEiOjAs"
            + "IngyIjowLCJ5MiI6MCwieDMiOjAsInkzIjowLCJ4NCI6MCwieTQiOjB9LHsic2kiOjEsIngxIjowLCJ5MSI6MCwieDIiOjAs"
            + "InkyIjowLCJ4MyI6MCwieTMiOjAsIng0IjowLCJ5NCI6MH1d6gPOAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgABAAHkAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAB5AAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
}
