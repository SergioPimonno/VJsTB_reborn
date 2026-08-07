package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * СТРУКТУРНЫЕ (не golden-байтовые) тесты {@link NovaLctScrWriter#writeStandardMultiScreen}.
 * Пользователь восстановил реальный 2-экранный образец ({@code 2scr.scr}) из Корзины, и формулы
 * ниже (счётчик экранов, длина/содержимое хвостового блока — {@code 231×N}/{@code screenCount}
 * в {@code tail[2:4]}/{@code tail[132]}) сверены с ним точно (см. javadoc
 * {@link NovaLctScrWriter#writeStandardMultiScreen}) — НО сам образец описывает два ПУСТЫХ экрана
 * с плейсхолдер-подобными полями (cols=100/rows=0 у экрана 0), поэтому побайтовое воспроизведение
 * ЦЕЛОГО файла как golden-теста не имеет смысла (наш writer никогда не строит такую вырожденную
 * сетку) — эти тесты по-прежнему проверяют только внутреннюю согласованность метода на
 * "нормальных" входных данных, не сами байты 2scr.scr.
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
}
