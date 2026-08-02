package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.model.Workspace;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Экспорт шаблона расключения ОДНОГО экрана в бинарный формат NovaLCT (.scr) —
 * зеркало {@link NovaLctScrParser} в обратную сторону. BEST-EFFORT: как и парсер,
 * пишет только те поля, что уверенно установлены дифференциальным анализом
 * образцов. Файл, сгенерированный этим классом, ни разу не проверялся импортом
 * в настоящий NovaLCT — прежде чем полагаться на результат в проде, нужно
 * протестировать его на реальном ПО (сначала на тестовом контроллере, не на
 * боевой инсталляции).
 *
 * <p>NovaLCT реально использует ДВЕ структурно разные раскладки байт под одним
 * и тем же расширением .scr (подтверждено побайтовым разбором реального образца
 * Complex Screen, присланного пользователем):
 * <ul>
 *   <li><b>Standard Screen</b> ({@link #writeStandard}) — ровная прямоугольная
 *   сетка одинаковых кабинетов, каждый адресуется ПАРОЙ (row,col), 17-байтная
 *   запись с 6-байтовым якорем {@code 00 80 00 80 00 01} (см. javadoc парсера,
 *   собран по 6 образцам этого варианта). Кабинет (row=0,col=0) в разобранных
 *   образцах ни разу не хранился отдельной записью — этот метод повторяет то же
 *   самое ТОЛЬКО если у этой ячейки вообще есть разрешимая цепочка.</li>
 *   <li><b>Complex Screen</b> ({@link #writeComplex}) — произвольные (в т.ч.
 *   разного размера/неровно расположенные) прямоугольники, каждый кабинет несёт
 *   ЯВНЫЕ пиксельные X/Y/Width/Height — НЕТ ни 6-байтового якоря, ни JSON-
 *   маркера простого формата вовсе (проверено прямым поиском по байтам образца —
 *   их там просто нет). Разобран по ОДНОМУ образцу (один экран, один порт,
 *   7 карт) — заголовок цепочки перед первой записью понят лишь частично
 *   (уверенно установлено только количество записей, остальные поля — лучшие
 *   догадки, см. комментарии в самом методе), многопортовые/многоэкранные
 *   Complex Screen файлы вообще не проверялись. Выбор формата — автоматический
 *   (см. {@link #write}), по факту, ровная ли сетка у экрана.</li>
 * </ul>
 */
public final class NovaLctScrWriter {

    private static final byte[] MAGIC = {'D', 'S', 'C', 'I'};
    private static final byte[] ANCHOR = {0x00, (byte) 0x80, 0x00, (byte) 0x80, 0x00, 0x01};

    private NovaLctScrWriter() {
    }

    private record Rec(int row, int col, int card, int port, int seq) {
    }

    /** true, если хотя бы один видимый кабинет экрана не входит ни в одну сигнальную
     *  цепочку с назначенным портом — тогда шаблон будет неполным (см. класс-javadoc):
     *  вызывающий код должен явно предупредить пользователя перед экспортом. */
    public static boolean hasUnwiredCabinets(Screen screen, Scene scene, Workspace workspace) {
        Map<String, Rec> byId = resolve(screen, scene, workspace);
        for (CabinetInstance cab : screen.getCabinets()) {
            if (!cab.isHidden() && !byId.containsKey(cab.getId())) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Rec> resolve(Screen screen, Scene scene, Workspace workspace) {
        Map<String, Rec> byCabinetId = new HashMap<>();
        List<SignalChain> chains = scene != null ? scene.getSignalChains() : List.of();
        for (SignalChain chain : chains) {
            Integer port = chain.getPortNumber();
            if (port == null) {
                continue;
            }
            int[] cardPort = workspace != null ? ScreenLogic.cardAndLocalPort(screen, workspace, port) : new int[]{0, 0};
            if (cardPort == null) {
                continue;
            }
            List<String> ids = chain.getCabinetInstanceIds();
            for (int seq = 0; seq < ids.size(); seq++) {
                CabinetInstance cab = screen.cabinetById(ids.get(seq));
                if (cab == null || cab.isHidden()) {
                    continue;
                }
                byCabinetId.put(cab.getId(), new Rec(cab.getRowIndex(), cab.getColIndex(), cardPort[0], cardPort[1], seq));
            }
        }
        return byCabinetId;
    }

    /** Собирает шаблон расключения ОДНОГО экрана в бинарный .scr — автоматически
     *  выбирает Standard- или Complex-раскладку (см. class-javadoc) по факту, ровная
     *  ли сетка кабинетов у экрана (см. {@link ScreenLogic#isUniformRectangularGrid}) —
     *  вызывающему коду (диалогу экспорта) не нужно знать про формат заранее. */
    public static byte[] write(Screen screen, Scene scene, Workspace workspace) {
        CabinetType defaultType = workspace != null ? workspace.cabinetTypeById(screen.getCabinetTypeId()) : null;
        if (defaultType != null && !ScreenLogic.isUniformRectangularGrid(screen, defaultType, workspace)) {
            return writeComplex(screen, scene, workspace, defaultType);
        }
        return writeStandard(screen, scene, workspace);
    }

    /** true — для ЭТОГО экрана экспорт пойдёт по Complex-раскладке (см. write) —
     *  вызывающий UI должен явно предупредить пользователя, что этот путь разобран
     *  всего по одному образцу и рискованнее обычного (см. class-javadoc). */
    public static boolean isComplexExport(Screen screen, Workspace workspace) {
        CabinetType defaultType = workspace != null ? workspace.cabinetTypeById(screen.getCabinetTypeId()) : null;
        return defaultType == null || !ScreenLogic.isUniformRectangularGrid(screen, defaultType, workspace);
    }

    /** Standard Screen — ровная прямоугольная сетка, row/col-адресация (см.
     *  class-javadoc). */
    private static byte[] writeStandard(Screen screen, Scene scene, Workspace workspace) {
        Map<String, Rec> byCabinetId = resolve(screen, scene, workspace);

        CabinetInstance origin = screen.cabinetAt(0, 0);
        String originId = origin != null ? origin.getId() : null;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeBytes(out, MAGIC);

        // Заголовок единственного экрана — 17 байт БЕЗ якоря (см. NovaLctScrParser:
        // индекс первой записи-кабинета минус 17 = начало этого заголовка). Между
        // MAGIC и заголовком в реальных файлах, вероятно, есть ещё не расшифрованный
        // блок (версия/контрольная сумма) — мы его не пишем вовсе, т.к. ни его размер,
        // ни содержимое не установлены ни одним из разобранных образцов.
        writeU16(out, 0); // неизвестно
        writeU16(out, screen.getCols());
        writeU16(out, screen.getRows());
        for (int i = 0; i < 11; i++) {
            out.write(0); // неизвестно
        }

        for (CabinetInstance cab : screen.getCabinets()) {
            if (cab.isHidden()) {
                continue;
            }
            Rec r = byCabinetId.get(cab.getId());
            if (r == null) {
                continue; // не расключён ни в одну сигнальную цепочку - нечего писать
            }
            if (cab.getId().equals(originId)) {
                continue; // см. class-javadoc
            }
            writeBytes(out, ANCHOR);
            out.write(r.card() & 0xff);
            out.write(r.port() & 0xff);
            writeU16(out, r.seq());
            // Предположительно дублирующая координата "×128" (см. javadoc парсера) —
            // НЕ подтверждено ни одним образцом, парсер сам их не читает.
            writeU16(out, r.col() * 128);
            writeU16(out, r.row() * 128);
            out.write(r.col() & 0xff);
            out.write(0); // неизвестно
            out.write(r.row() & 0xff);
        }

        // Завершающий JSON-маркер warp-искажений — в образцах координаты всегда были
        // нулевыми, а полная схема полей NovaLCT не установлена; пишем минимально
        // правдоподобный объект — этого достаточно, чтобы парсер (свой и, будем
        // надеяться, оригинальный NovaLCT) нашёл конец данных экрана по маркеру.
        String json = "[{\"si\":0,\"x1\":0,\"y1\":0,\"x2\":0,\"y2\":0}]";
        writeBytes(out, json.getBytes(StandardCharsets.US_ASCII));

        return out.toByteArray();
    }

    private record ComplexRec(int x, int y, int w, int h) {
    }

    /** Complex Screen — произвольные прямоугольники, явные пиксельные X/Y/Width/
     *  Height на кабинет (см. class-javadoc). Разобран по ОДНОМУ образцу (1 экран,
     *  1 порт/цепочка, 7 приёмных карт неровной формы — включая одну карту вдвое
     *  выше соседних) — сверено с таблицей "Receiving Card Settings" реального
     *  NovaLCT для этого образца координата-в-координату (X/Y/Width/Height каждой
     *  из 7 карт совпали ТОЧНО). Заголовок цепочки понят лишь ЧАСТИЧНО — см.
     *  комментарии по ходу метода про то, что подтверждено, а что — догадка.
     *  Многопортовые/многоэкранные Complex Screen файлы вообще не проверялись. */
    private static byte[] writeComplex(Screen screen, Scene scene, Workspace workspace, CabinetType defaultType) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeBytes(out, MAGIC);
        // Намеренно НИКАКОЙ преамбулы между MAGIC и первым блоком цепочки: в
        // образце там было заведомо больше сотни байт неопознанного содержимого
        // (возможно версия/контрольная сумма/общие калибровочные поля), но мы не
        // знаем ни его реальной длины в общем случае, ни смысла — фабриковать
        // конкретное число байт, подсмотренное в ОДНОМ образце, значило бы делать
        // вид, что мы знаем то, чего на самом деле не знаем.
        int nativeCellW = defaultType.getResolutionWidth();
        int nativeCellH = defaultType.getResolutionHeight();
        List<SignalChain> chains = scene != null ? scene.getSignalChains() : List.of();
        for (SignalChain chain : chains) {
            Integer port = chain.getPortNumber();
            if (port == null) {
                continue;
            }
            int[] cardPort = workspace != null ? ScreenLogic.cardAndLocalPort(screen, workspace, port) : null;
            if (cardPort == null) {
                continue;
            }
            List<ComplexRec> records = new ArrayList<>();
            for (String id : chain.getCabinetInstanceIds()) {
                CabinetInstance cab = screen.cabinetById(id);
                if (cab == null || cab.isHidden()) {
                    continue; // не на этом экране, либо деактивирован -- нет физической карты
                }
                CabinetType eff = ScreenLogic.effectiveType(cab, defaultType, workspace);
                int x = (int) Math.round(cab.getColIndex() * nativeCellW
                        + ScreenLogic.offsetPx(cab.getOffsetXMm(), nativeCellW, defaultType.getWidthMm()));
                int y = (int) Math.round(cab.getRowIndex() * nativeCellH
                        + ScreenLogic.offsetPx(cab.getOffsetYMm(), nativeCellH, defaultType.getHeightMm()));
                int w = eff != null ? eff.getResolutionWidth() : nativeCellW;
                int h = eff != null ? eff.getResolutionHeight() : nativeCellH;
                records.add(new ComplexRec(x, y, w, h));
            }
            if (records.isEmpty()) {
                continue;
            }
            // Заголовок цепочки (перед первой записью-картой) — в образце занимал
            // 15 байт. Байт[7] = количество записей (7 в образце, 7 карт) —
            // ЕДИНСТВЕННОЕ поле здесь, чьё назначение подтверждено уверенно (ровно
            // совпало с числом карт цепочки). Байт[0] = локальный порт НА карте
            // (card/port из ScreenLogic.cardAndLocalPort) и байты[2-3] = индекс
            // карты (LE16) — правдоподобные, но НЕ независимо подтверждённые догадки
            // (в разобранном образце было "Sending Card 1, Port 1", что совместимо
            // с этой раскладкой, но с одним образцом это не более чем совпадение
            // могло быть проверено). Остальные байты заголовка — нули-заглушки.
            out.write((cardPort[1] + 1) & 0xff);
            out.write(0);
            writeU16(out, cardPort[0]);
            out.write(0);
            out.write(0);
            out.write(0);
            out.write(records.size() & 0xff);
            for (int i = 0; i < 7; i++) {
                out.write(0);
            }
            int seq = 0;
            for (ComplexRec r : records) {
                seq++;
                writeU16(out, r.x());
                writeU16(out, r.y());
                writeU16(out, r.x()); // дубль X -- см. javadoc парсера про такую же пару в Standard-формате
                writeU16(out, r.y()); // дубль Y
                writeU16(out, r.w());
                writeU16(out, r.h());
                writeU16(out, 0); // неизвестно
                writeU16(out, seq); // "Receiving Card" 1-based -- подтверждено сверкой с образцом
            }
        }
        // В образце после последней записи шёл литерал "[]" (пустой массив warp-
        // искажений) — в отличие от Standard-формата, здесь НЕТ JSON-маркера
        // "[{\"si\":..." вовсе (проверено прямым поиском по байтам образца).
        writeBytes(out, "[]".getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private static void writeBytes(ByteArrayOutputStream out, byte[] b) {
        out.write(b, 0, b.length);
    }

    private static void writeU16(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >> 8) & 0xff);
    }
}
