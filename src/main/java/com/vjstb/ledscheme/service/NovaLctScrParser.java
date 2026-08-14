package com.vjstb.ledscheme.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Разбор бинарного файла прописи экрана NovaLCT (.scr) — ЗАГОТОВКА для будущего
 * импорта расключения (см. обсуждение с пользователем): формат не документирован
 * NovaStar, поля установлены дифференциальным анализом 6 образцов (по одному
 * изменяемому параметру за раз — экраны/карты/порты/размер), не полной
 * спецификацией производителя. Уверенно установлено:
 *
 * <p>Каждый кабинет хранится 17-байтовой записью с 6-байтовым «якорем»
 * {@code 00 80 00 80 00 01} в начале; далее байт[6] — индекс карты (0-based),
 * байт[7] — индекс порта на карте (0-based), байты[8-9] (LE16) — порядковый
 * номер кабинета в цепочке расключения ЭТОГО порта (0-based, определяет
 * физический порядок прописи — серпантин и т.п.), байт[14] — колонка (0-based),
 * байт[16] — строка (0-based). Байты[10-13] дублируют колонку/строку в виде
 * LE16 «координата×128» — не используются, оставлены как есть.
 *
 * <p>Первый экран файла описан 17-байтовым заголовком БЕЗ якоря (ширина/высота
 * как LE16 в байтах[2-5]), непосредственно перед его первой записью-кабинетом;
 * каждый следующий экран — 27-байтовым заголовком, использующим тот же
 * 6-байтовый якорь (индекс экрана в байте[6], ширина/высота как LE16 в
 * байтах[12-15], индекс карты в байте[16]). Кабинет (0,0) КАЖДОГО экрана не хранится отдельной
 * записью в разобранных образцах — его порт/карта совпадают с портом/картой
 * соседней (0,1)/(1,0) ячейки серпантина, а номер в цепочке восстанавливается
 * методом исключения (единственное недостающее значение в диапазоне [0..N-1]
 * для его порта) — см. {@link ImportedScreen#fillMissingOrigin()}.
 *
 * <p>В конце файла — читаемый JSON-массив {@code [{"si":N,"x1":...},...]} с
 * координатами warp-искажения по экранам (в образцах всегда нулевые) — не
 * разбирается детально, используется только как маркер конца данных по экранам.
 *
 * <p>Многие поля заголовка (контрольные суммы, размеры блоков) остаются
 * НЕ расшифрованными — заготовка сознательно их игнорирует, читая только то,
 * что уверенно проверено на нескольких образцах. Прежде чем подключать это к
 * реальному импорту прописи, стоит проверить парсер на образцах с более чем
 * 2 экранами и с расключением, начатым НЕ с левого нижнего угла — на момент
 * написания все образцы использовали один и тот же паттерн серпантина, поэтому
 * не исключено, что часть выводов (например, какая ячейка теряется) справедлива
 * только для него.
 *
 * <p><b>Известное ограничение, найденное round-trip тестами (см. {@code
 * NovaLctScrParserTest}), не исправлено — нет реального образца для проверки
 * гипотезы:</b> {@link #ANCHOR} зашита как константа {@code 00 80 00 80 00 01}
 * (128×128), написано это было ДО того, как в {@code NovaLctScrWriter.buildAnchor}
 * подтвердилось, что эти два средних байта — реальные пиксельные ширина/высота
 * кабинета, а не фиксированный маркер (см. NOVALCT_EXPORT.md, секция 4). Для
 * любого кабинета, отличного от 128×128, писатель кладёт другой якорь, и этот
 * парсер не находит вообще ни одной записи ({@code parse} бросает
 * IllegalArgumentException «не найдена ни одна запись»). Чинить вслепую — без
 * реального образца с другим разрешением кабинета — не стоит, только гадать
 * (см. общий принцип файла NOVALCT_EXPORT.md: не изобретать без образца).</p>
 */
public final class NovaLctScrParser {

    private static final byte[] MAGIC = {'D', 'S', 'C', 'I'};
    private static final byte[] ANCHOR = {0x00, (byte) 0x80, 0x00, (byte) 0x80, 0x00, 0x01};
    private static final byte[] JSON_MARKER = {'[', '{', '"', 's', 'i', '"', ':'};
    private static final int RECORD_LEN = 17;

    private NovaLctScrParser() {
    }

    public record CabinetEntry(int row, int col, int card, int port, int seq) {
    }

    public static final class ImportedScreen {
        public final int index;
        public final int width;
        public final int height;
        public final List<CabinetEntry> cabinets = new ArrayList<>();

        ImportedScreen(int index, int width, int height) {
            this.index = index;
            this.width = width;
            this.height = height;
        }

        /** Группирует кабинеты по (карта,порт) → список в порядке прописи (по seq). */
        public Map<String, List<CabinetEntry>> chainsByCardPort() {
            Map<String, List<CabinetEntry>> byKey = new LinkedHashMap<>();
            for (CabinetEntry c : cabinets) {
                byKey.computeIfAbsent(c.card() + "/" + c.port(), k -> new ArrayList<>()).add(c);
            }
            for (List<CabinetEntry> list : byKey.values()) {
                list.sort((a, b) -> Integer.compare(a.seq(), b.seq()));
            }
            return byKey;
        }

        /** Восстанавливает недостающую (см. класс-javadoc) ячейку(и) сетки методом
         *  исключения: если ровно ОДНА позиция width×height не встретилась ни в одной
         *  записи и в ТОЧНОСТИ один порт недосчитывается ровно одного значения seq в
         *  диапазоне [0..count-1] этого порта — недостающая ячейка приписывается этому
         *  порту с этим seq. Не занимается ничем, если картина неоднозначна (более
         *  одной дыры, или дыр в seq не найдено) — оставляет как есть, лучше явно
         *  показать неполный результат, чем угадать неверно.
         */
        public void fillMissingOrigin() {
            boolean[][] present = new boolean[height][width];
            for (CabinetEntry c : cabinets) {
                if (c.row() >= 0 && c.row() < height && c.col() >= 0 && c.col() < width) {
                    present[c.row()][c.col()] = true;
                }
            }
            List<int[]> missingCells = new ArrayList<>();
            for (int r = 0; r < height; r++) {
                for (int c = 0; c < width; c++) {
                    if (!present[r][c]) {
                        missingCells.add(new int[]{r, c});
                    }
                }
            }
            if (missingCells.size() != 1) {
                return;
            }
            int[] cell = missingCells.get(0);
            Map<String, List<CabinetEntry>> byKey = chainsByCardPort();
            String onlyKeyWithGap = null;
            int gapSeq = -1;
            for (Map.Entry<String, List<CabinetEntry>> e : byKey.entrySet()) {
                List<CabinetEntry> list = e.getValue();
                int size = list.size();
                // Порт БЕЗ дыры (не содержащий недостающую ячейку) — его seq образуют
                // ПЛОТНЫЙ диапазон [0..size-1] без пропусков; если считать "ожидаемое
                // число" как size+1 для КАЖДОГО порта без разбора, то и у такого
                // "полного" порта формально не хватает значения size — ложное
                // совпадение на каждом порту сразу, из-за чего дыра никогда не находится
                // однозначно. Поэтому сперва проверяем, является ли диапазон [0..size-1]
                // уже полным (наибольший seq = size-1) — тогда это ЗАВЕДОМО не тот порт.
                int maxSeq = -1;
                boolean outOfRange = false;
                for (CabinetEntry c : list) {
                    if (c.seq() < 0 || c.seq() > size) {
                        outOfRange = true;
                        break;
                    }
                    maxSeq = Math.max(maxSeq, c.seq());
                }
                if (outOfRange || maxSeq != size) {
                    // maxSeq == size-1 (и меньше) — плотный диапазон, порт полный, дыры нет.
                    continue;
                }
                boolean[] seen = new boolean[size + 1];
                for (CabinetEntry c : list) {
                    seen[c.seq()] = true;
                }
                int gap = -1;
                int gapCount = 0;
                for (int i = 0; i <= size; i++) {
                    if (!seen[i]) {
                        gap = i;
                        gapCount++;
                    }
                }
                if (gapCount == 1) {
                    if (onlyKeyWithGap != null) {
                        // неоднозначно — несколько портов претендуют на дыру, не угадываем
                        return;
                    }
                    onlyKeyWithGap = e.getKey();
                    gapSeq = gap;
                }
            }
            if (onlyKeyWithGap == null) {
                return;
            }
            String[] parts = onlyKeyWithGap.split("/");
            int card = Integer.parseInt(parts[0]);
            int port = Integer.parseInt(parts[1]);
            cabinets.add(new CabinetEntry(cell[0], cell[1], card, port, gapSeq));
        }
    }

    public record ImportResult(List<ImportedScreen> screens) {
    }

    public static ImportResult parse(byte[] data) {
        if (data.length < MAGIC.length || !startsWith(data, 0, MAGIC)) {
            throw new IllegalArgumentException("Не похоже на файл NovaLCT (.scr): нет сигнатуры DSCI");
        }
        int jsonOffset = indexOf(data, JSON_MARKER, 0);
        if (jsonOffset < 0) {
            throw new IllegalArgumentException("Не найден завершающий JSON-блок — файл повреждён"
                    + " либо это не поддерживаемая версия формата .scr");
        }

        int firstRecord = indexOf(data, ANCHOR, 0);
        if (firstRecord < 0 || firstRecord < 17) {
            throw new IllegalArgumentException("Не найдена ни одна запись кабинета — файл повреждён"
                    + " либо это не поддерживаемая версия формата .scr");
        }

        List<ImportedScreen> screens = new ArrayList<>();
        int width = readU16(data, firstRecord - 15);
        int height = readU16(data, firstRecord - 13);
        int pos = firstRecord;
        int screenIndex = 0;
        while (pos < jsonOffset) {
            ImportedScreen screen = new ImportedScreen(screenIndex, width, height);
            int expectedRecords = Math.max(0, width * height - 1);
            int read = 0;
            while (read < expectedRecords && pos + RECORD_LEN <= jsonOffset && startsWith(data, pos, ANCHOR)) {
                screen.cabinets.add(new CabinetEntry(
                        data[pos + 16] & 0xff,
                        data[pos + 14] & 0xff,
                        data[pos + 6] & 0xff,
                        data[pos + 7] & 0xff,
                        readU16(data, pos + 8)));
                pos += RECORD_LEN;
                read++;
            }
            screen.fillMissingOrigin();
            screens.add(screen);
            screenIndex++;

            // Ищем заголовок следующего экрана: тот же якорь, дальше в файле, ДО
            // начала JSON-блока. Заголовок 27 байт, после него — снова якорь (первая
            // запись-кабинет нового экрана); ищем именно ВТОРОЕ вхождение якоря после
            // текущей позиции, а не первое (первое — это сам заголовок).
            int nextHeader = indexOf(data, ANCHOR, pos);
            if (nextHeader < 0 || nextHeader >= jsonOffset) {
                break;
            }
            int nextRecord = indexOf(data, ANCHOR, nextHeader + ANCHOR.length);
            if (nextRecord < 0 || nextRecord >= jsonOffset) {
                break;
            }
            width = readU16(data, nextHeader + 12);
            height = readU16(data, nextHeader + 14);
            pos = nextRecord;
        }
        return new ImportResult(screens);
    }

    private static boolean startsWith(byte[] data, int offset, byte[] pattern) {
        if (offset < 0 || offset + pattern.length > data.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (data[offset + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        for (int i = Math.max(0, from); i + pattern.length <= data.length; i++) {
            if (startsWith(data, i, pattern)) {
                return i;
            }
        }
        return -1;
    }

    private static int readU16(byte[] data, int offset) {
        if (offset < 0 || offset + 1 >= data.length) {
            return 0;
        }
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }
}
