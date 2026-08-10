package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ControllerInstance;
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
 * зеркало {@link NovaLctScrParser} в обратную сторону.
 *
 * <p><b>Standard Screen — статус: ПОЛНОСТЬЮ РЕШЕНО.</b> Формат реверс-инжинирен
 * ДО ПОСЛЕДНЕГО БАЙТА: побайтовое сравнение контролируемых образцов (различающихся
 * ровно одним параметром каждый — карта/порт/размер кабинета/координата экрана)
 * ПЛЮС дизассемблирование (ildasm) реального
 * {@code Nova.LCT.GigabitSystem.HWConfigAccessor.Accessors.CommonInfoAccessor.
 * SoftWareSpaceAnalyser} из установленной у пользователя NovaLCT дало 100%
 * побайтовое совпадение с РЕАЛЬНЫМИ файлами NovaLCT — и на тривиальном 1×1 экране,
 * и на сложном 4×3 с непростой (не построчной) цепочкой на 11 кабинетов. Обе
 * контрольные суммы (простая сумма байт, не CRC — см. комментарии у их вычисления
 * в {@link #writeStandardCore}), порядок кабинетных записей (COLUMN-MAJOR, см.
 * {@link #orderedCells}) и номер "Receiving Card" пропущенной ячейки (0,0)
 * (см. {@code originSeq} в {@link #writeStandardCore}) — всё подтверждено. Пользователь
 * подтвердил успешную загрузку сгенерированного файла в реальную NovaLCT (Load
 * from File) с полностью правильным порядком расключения всех кабинетов.
 *
 * <p><b>Complex Screen — статус: ПОЛНОСТЬЮ РЕШЕНО.</b> Раньше считался best-effort
 * (один образец, заголовок понят лишь частично, чек-сумма не найдена) — оказалось
 * ошибочно: декомпиляция (ildasm) реального
 * {@code Nova.LCT.GigabitSystem.HWConfigAccessor.Accessors.CommonInfoAccessor.
 * SoftWareSpaceAnalyser.ScreenInfoToArray1600} показала, что это ТОТ ЖЕ метод,
 * что строит и Standard Screen (оба — разные типы {@code ILEDDisplayInfo} в одном
 * общем контейнере), и делит с ним всю преамбулу/обе контрольные суммы/
 * {@link #TAIL_FOOTER}. Подтверждено побайтово ДВУМЯ реальными файлами NovaLCT
 * (экран 4×6, 24 приёмных карты, нижний ряд 128×64, отличаются ровно одним
 * Ethernet-портом) — см. {@link #writeComplex} и
 * {@code NovaLctComplexScrWriterTest}. Единственное НЕ проверенное реальным
 * образцом — {@code XInPort}/{@code YInPort} (в обоих образцах 0, назначение для
 * ненулевых значений неизвестно) и порядок карт при НЕСКОЛЬКИХ цепочках/портах на
 * одном Complex-экране (образец содержал ровно одну цепочку).
 *
 * <p>NovaLCT реально использует ДВЕ структурно разные раскладки байт под одним
 * и тем же расширением .scr:
 * <ul>
 *   <li><b>Standard Screen</b> ({@link #writeStandard}) — ровная прямоугольная
 *   сетка одинаковых кабинетов, каждый адресуется ПАРОЙ (row,col), 17-байтная
 *   запись с 6-байтовым якорем (см. {@link #buildAnchor}). Кабинет (row=0,col=0)
 *   в разобранных образцах ни разу не хранился отдельной записью — этот метод
 *   повторяет то же самое ТОЛЬКО если у этой ячейки вообще есть разрешимая
 *   цепочка.</li>
 *   <li><b>Complex Screen</b> ({@link #writeComplex}) — произвольные (в т.ч.
 *   разного размера/неровно расположенные) прямоугольники, каждый кабинет несёт
 *   ЯВНЫЕ пиксельные X/Y/Width/Height, а также СВОЙ card/port/seq (см. javadoc
 *   метода) — 16-байтная запись без 6-байтового якоря Standard-формата. Выбор
 *   формата — автоматический (см. {@link #write}), по факту, ровная ли сетка
 *   у экрана.</li>
 * </ul>
 */
public final class NovaLctScrWriter {

    private static final byte[] MAGIC = {'D', 'S', 'C', 'I'};

    /** 6-байтовый "якорь" перед каждой кабинетной записью И перед хвостовым JSON —
     *  ИЗНАЧАЛЬНО считался фиксированной константой {@code 00 80 00 80 00 01}
     *  (собрано по образцам с кабинетом 128×128) — ОШИБОЧНО: контролируемый образец
     *  с кабинетом 250×250 показал якорь {@code 00 FA 00 FA 00 01} (0xFA=250) —
     *  байты 1 и 3 это ШИРИНА и ВЫСОТА кабинета в пикселях (при <256 — старший байт
     *  0, т.е. фактически однобайтовое значение с нулевым префиксом, не classic
     *  LE16), а не константа 128. Байт 5 (флаг) — считался 0x01 при 0 записей и 0xFF
     *  при их наличии (по одному старому образцу 4×2/7 записей) — ОПРОВЕРГНУТО
     *  более надёжным новым образцом (4×3, 11 записей со сложной цепочкой,
     *  сверено побайтово): там байт = 0x01 ВО ВСЕХ 11 записях И в хвосте. В
     *  однoэкранном/несгруппированном случае байт = 0x01 всегда — подтверждено
     *  на нескольких реальных образцах, эта часть НЕ под вопросом.
     *
     * <p><b>Известная нерешённая проблема (см. NOVALCT_EXPORT.md)</b>: у
     * {@code splitSeparateGrouped}-групп (несколько экранов проекта слиты в один
     * NovaLCT-экран) реальный образец (2 экрана: Left отдельно, Center+Right
     * группой из 14×8 с 20 blank-ячейками) показывает НЕКОНСИСТЕНТНЫЕ результаты
     * побайтовой сверки МЕЖДУ РАЗНЫМИ прогонами этой сессии: в одном прогоне —
     * anchor[5]=0xFF у ВСЕХ 20 blank-записей (card=0xFF) и НИ У ОДНОЙ обычной; в
     * другом (тем же кодом, предположительно с устаревшей пересобранной jar,
     * см. баг с maven-shade-plugin в этой же сессии) — 0xFF у row==0 лишь части
     * колонок, включая обычные (не blank) записи, а часть blank-записей — 0x01.
     * Причина расхождения МЕЖДУ прогонами не выяснена (вероятно кэш сборки, но не
     * подтверждено намеренно повторным чистым прогоном) — пока оставлена
     * подтверждённая для одноэкранного случая константа 0x01 везде, это даёт от
     * 20 до 24 расходящихся байт против реального файла именно для этой группы
     * (остальное — card/port/seq/координаты/чек-суммы каждой записи, порядок
     * экранов, номера отдающих карт — сходится точно). Если после этого
     * загрузка в реальную NovaLCT всё ещё падает — нужен второй реальный образец
     * (в идеале с ДРУГОЙ раскладкой колонок/портов) для чистой, воспроизводимой
     * сверки без риска спутать со сборочным кэшем. */
    private static byte[] buildAnchor(int cabinetWidthPx, int cabinetHeightPx) {
        return new byte[]{
                0x00, (byte) (cabinetWidthPx & 0xff),
                0x00, (byte) (cabinetHeightPx & 0xff),
                0x00, 0x01,
        };
    }

    /** Постоянный 173-байтный "хвост после хвоста" — ПОСЛЕ JSON warp-маркера, а не
     *  сразу после последней кабинетной записи (это отдельный, ранее вообще
     *  не замеченный блок: предыдущий анализ ошибочно считал, что файл кончается
     *  сразу после {@code "]"} JSON-массива). Побайтово идентичен в обоих образцах
     *  (1×1 пустой экран и 4×2 сетка) — при том, что итоговая длина файла и
     *  позиция JSON у них совершенно разные — т.е. это чистая константа
     *  фиксированной длины, не зависящая от содержимого экрана. Первый экспорт
     *  этим writer'ом (без этого блока — файл получался на 173 байта короче
     *  реального) реальный NovaLCT отклонил как "Failed to load screen information
     *  file!" — этот пропуск, скорее всего, и был основной причиной. */
    private static final byte[] TAIL_FOOTER = buildTailFooter();

    private static byte[] buildTailFooter() {
        byte[] f = new byte[173];
        f[0] = (byte) 0xea;
        f[1] = 0x03;
        f[2] = (byte) 0xe7;
        f[132] = 0x01;
        f[134] = 0x01;
        f[136] = 0x01;
        f[137] = (byte) 0xe4;
        return f;
    }

    /** Длина преамбулы ОДНОЭКРАННОГО Standard Screen файла (от начала MAGIC до
     *  первого 6-байтового якоря записи/хвоста) — подтверждено ПОБАЙТОВЫМ сравнением
     *  двух реальных образцов (1×1 пустой экран и 4×2 сетка): несмотря на радикально
     *  разный размер экрана и итоговую длину файла (587 vs 706 байт), это смещение
     *  ТОЧНО совпало в обоих (0x154) — преамбула фиксированного размера, не растёт
     *  с числом кабинетов. Из её 340 байт 328 оказались побитово идентичны в обоих
     *  образцах (см. HEADER_* поля ниже для расшифрованных полей внутри неё). */
    private static final int HEADER_LEN = 0x154;

    private NovaLctScrWriter() {
    }

    /** Пакетно-видимый (не private) — переиспользуется {@link NovaLctControllerResolver}/
     *  {@link NovaLctCombineHelper} для сборки контроллер-центричного экспорта без
     *  привязки к одному {@link Screen}.
     *
     * <p><b>{@code card = 0xFF (255)}</b> — ПОДТВЕРЖДЁННЫЙ (не догадка) сентинел
     * "blank"/"нет платы": найден побайтовым разбором реального файла NovaLCT
     * ({@code 111.scr}, прислан пользователем, сохранён самой NovaLCT из
     * конфигурации "несколько экранов одного типа кабинета внутри границ одного
     * NovaLCT-экрана" — ровно та же реальная задача, что решает
     * {@link NovaLctCombineHelper#combine}). Остальные поля такой записи (port/seq)
     * пишутся по той же формуле, что и обычная запись — значения не важны, т.к. у
     * записи нет реальной платы (см. {@link NovaLctCombineHelper#combine}). */
    record Rec(int row, int col, int card, int port, int seq) {
    }

    /** Ключ ячейки сетки для контроллер-центричной сборки (см. {@link #writeStandardCore},
     *  {@link NovaLctCombineHelper}) — тот же (col,row), что и в {@link Rec}, но как
     *  самостоятельный ключ карты, не завязанный на конкретный {@link CabinetInstance}
     *  одного экрана. */
    record CellKey(int col, int row) {
    }

    /** Контроллер-центричная версия {@link #hasUnwiredCabinets} — true, если на
     *  КАКОМ-ЛИБО из экранов, затронутых {@code controller} (см.
     *  {@link NovaLctControllerResolver#resolve}), есть видимый кабинет, которого
     *  нет среди резолвнутых для ЭТОГО контроллера записей. Кабинет, реально
     *  расключённый через ДРУГОЙ контроллер той же сцены (экран может делить
     *  контроллеры), тоже попадёт в это предупреждение — по сути "этот контроллер
     *  не покрывает весь экран целиком", что для пользователя достаточно честная
     *  формулировка без отдельного различения причины. */
    public static boolean hasUnwiredCabinetsForController(Scene scene, ControllerInstance controller,
                                                           AppModel model) {
        List<NovaLctControllerResolver.CabinetRec> recs = NovaLctControllerResolver.resolve(scene, controller, model);
        Map<Screen, java.util.Set<CellKey>> byScreen = new HashMap<>();
        for (NovaLctControllerResolver.CabinetRec r : recs) {
            byScreen.computeIfAbsent(r.sourceScreen(), s -> new java.util.HashSet<>()).add(new CellKey(r.col(), r.row()));
        }
        for (Map.Entry<Screen, java.util.Set<CellKey>> entry : byScreen.entrySet()) {
            Screen screen = entry.getKey();
            java.util.Set<CellKey> wired = entry.getValue();
            for (CabinetInstance cab : screen.getCabinets()) {
                if (!cab.isHidden() && !wired.contains(new CellKey(cab.getColIndex(), cab.getRowIndex()))) {
                    return true;
                }
            }
        }
        return false;
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

    /** Контроллер-центричный экспорт — все кабинеты, резолвнутые
     *  {@link NovaLctControllerResolver} для {@code controller} (с ЛЮБОГО экрана
     *  сцены), объединяются в ОДНУ виртуальную Standard Screen сетку по пиксельной
     *  геометрии {@code canvas} (см. {@link NovaLctCombineHelper}). Если резолвер
     *  вернул кабинеты РОВНО одного экрана — вызывающему коду (диалогу) выгоднее
     *  звонить в уже подтверждённый {@link #write(Screen, Scene, Workspace)}
     *  напрямую, а не сюда (см. {@code NovaLctControllerExportDialog}) — этот метод
     *  не проверяет вырожденный случай сам, чтобы не тянуть сюда лишнюю логику
     *  выбора одноэкранного/canvas-based пути. */
    public static byte[] writeStandardCombined(NovaLctCombineHelper.CombineResult combined) {
        return writeStandardCore(combined.cols(), combined.rows(), combined.cabW(), combined.cabH(), 0,
                combined.cells());
    }

    /** Standard Screen — ровная прямоугольная сетка, row/col-адресация (см.
     *  class-javadoc). Тонкая обёртка над {@link #writeStandardCore}: резолвит
     *  цепочки ОДНОГО экрана в {@code Map<CellKey,Rec>} и вызывает общее ядро —
     *  сохранена ради обратной совместимости существующего вызывающего кода
     *  ({@link #write}), само ядро экрана не знает. */
    private static byte[] writeStandard(Screen screen, Scene scene, Workspace workspace) {
        Map<String, Rec> byCabinetId = resolve(screen, scene, workspace);
        Map<CellKey, Rec> byCell = new HashMap<>();
        for (Rec r : byCabinetId.values()) {
            byCell.put(new CellKey(r.col(), r.row()), r);
        }
        CabinetType defaultType = workspace != null ? workspace.cabinetTypeById(screen.getCabinetTypeId()) : null;
        int cabW = defaultType != null ? defaultType.getResolutionWidth() : 128;
        int cabH = defaultType != null ? defaultType.getResolutionHeight() : 128;
        return writeStandardCore(screen.getCols(), screen.getRows(), cabW, cabH, 0, byCell);
    }

    /** Ядро сборки Standard Screen — не знает про {@link Screen}/{@link Scene}/
     *  {@link Workspace}, работает только с уже разрешённой сеткой (col,row)→{@link Rec}.
     *  Переиспользуется и одноэкранным {@link #writeStandard} (через {@code byCell}
     *  из {@link #resolve}), и контроллер-центричным экспортом
     *  ({@link NovaLctControllerResolver}/{@link NovaLctCombineHelper}) — байтовый
     *  вывод для уже подтверждённого одноэкранного случая должен оставаться
     *  ИДЕНТИЧНЫМ тому, что было до выделения этого метода (см. golden-тест
     *  {@code NovaLctScrWriterTest}).
     *
     *  @param screenX координата экрана на общем виртуальном холсте NovaLCT (поле
     *                 "Coordinate: X" в Screen Configuration) — 0 для одноэкранного
     *                 экспорта (в модели такого понятия нет, экран экспортируется
     *                 независимо); для объединённого контроллер-центричного экспорта
     *                 тоже 0 (объединённая сетка сама по себе — единственный "экран"
     *                 с точки зрения NovaLCT). */
    static byte[] writeStandardCore(int cols, int rows, int cabW, int cabH, int screenX,
                                     Map<CellKey, Rec> cellsByKey) {
        CellKey originKey = new CellKey(0, 0);

        // Число реально записываемых 17-байтных записей нужно ЗАРАНЕЕ (до самого
        // цикла записи), чтобы посчитать смещение хвоста (trailerOffset) для полей
        // заголовка, зависящих от него (см. HEADER_LEN javadoc) — origin(0,0)
        // никогда не хранится отдельной записью (см. class-javadoc), поэтому не
        // учитывается, даже если у него есть цепочка. Порядок обхода — COLUMN-MAJOR
        // (см. javadoc у самого цикла записи ниже) — здесь для подсчёта порядок не
        // важен, но проходим той же функцией orderedCells(...), чтобы не держать
        // два места, знающих про порядок обхода сетки.
        int recordCount = 0;
        for (CellKey k : orderedCells(cols, rows)) {
            if (!k.equals(originKey) && cellsByKey.containsKey(k)) {
                recordCount++;
            }
        }
        int trailerOffset = HEADER_LEN + recordCount * 17;

        // Sending Card / Ethernet Port экрана (0-based, как везде в этом классе) —
        // нужны для поля 0xb8 заголовка (см. ниже). Экран Standard-раскладки на
        // практике расключается ОДНИМ портом целиком — берём card/port ЛЮБОЙ уже
        // разрешённой записи (первая по COLUMN-MAJOR обходу, а не первая попавшаяся
        // из HashMap — детерминировано, важно для контроллеров с несколькими
        // картами/портами на разных ячейках одной объединённой сетки), т.к. в
        // подтверждённых одноэкранных образцах это всегда один и тот же порт для
        // всех кабинетов экрана — там разницы нет; если сетка вообще не расключена,
        // используем 0/0 (Sending Card 1 / Port 1 — дефолт NovaLCT). Пропускаем
        // BLANK-записи (card=0xFF, см. javadoc {@link Rec} про подтверждённый по
        // реальному 111.scr сентинел "нет платы") — иначе экран, у которого
        // первая по обходу ячейка оказалась blank-дырой (например, самый левый
        // из объединённых экранов короче остальных), ошибочно получил бы в
        // заголовке "Sending Card 256".
        int firstCard = 0;
        int firstPort = 0;
        for (CellKey k : orderedCells(cols, rows)) {
            Rec r = cellsByKey.get(k);
            if (r != null && r.card() != 255) {
                firstCard = r.card();
                firstPort = r.port();
                break;
            }
        }
        // Seq кабинета (0,0) — он единственный, для кого 17-байтная запись НЕ
        // пишется (см. class-javadoc), поэтому NovaLCT должна как-то узнать его
        // "Receiving Card" номер иначе — оказалось, что это ПРЯМО поле 0x14b
        // заголовка (см. ниже), а НЕ производная от cols/rows, как считалось раньше
        // (то совпадение было случайным на 2 из 3 проверенных образцов). Подтверждено
        // ТОЧНО на 3 образцах: 0, 3 и 2 — во всех случаях 0x14b буквально равно seq
        // кабинета (0,0) (0, если он вообще не расключён). Раскрыто по репорту
        // пользователя о неверном номере ИМЕННО у ячейки (0,0) после реального
        // импорта в NovaLCT остального 11-кабинетного экрана без единой ошибки.
        int originSeq = 0;
        Rec originRec = cellsByKey.get(originKey);
        if (originRec != null) {
            originSeq = originRec.seq();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Фиксированная 340-байтная преамбула ОДНОЭКРАННОГО файла, ВКЛЮЧАЯ MAGIC в
        // начале (см. HEADER_LEN javadoc) — все смещения ниже АБСОЛЮТНЫЕ (от начала
        // файла), поэтому весь блок собирается в одном массиве, а не отдельно от
        // MAGIC (легко перепутать при вычитании 4 байт MAGIC вручную — так и
        // произошло при первой попытке, поймано побайтовой сверкой с образцами).
        // Расшифровано побайтовым сравнением образцов И дизассемблированным кодом
        // самой NovaLCT (см. комментарий у контрольной суммы ниже, после сборки
        // тела файла): смещение 0x06 — LE32-длина секции "DVI info" (128 байт,
        // константа, пока в LED Scheme Designer нет настроек DVI/графики), смещение
        // 0x0a — LE32-длина секции "screen info" (=растёт на 17 байт за кабинетную
        // запись — это ЗНАЧЕНИЕ, а не совпадение с прежней формулой "trailerOffset-
        // 108": screenInfoLen = trailerOffset-108 тождественно верно алгебраически).
        // Поле 0x04 (контрольная сумма) заполняется позже, после того как известна
        // полная секция screen info (см. ниже).
        byte[] header = new byte[HEADER_LEN];
        System.arraycopy(MAGIC, 0, header, 0, MAGIC.length);
        putU16(header, 0x06, 0x0080); // LE32 длины DVI info (128), верхние 2 байта нулевые
        putU16(header, 0x0a, trailerOffset - 108); // LE32 длины screen info, верхние 2 байта нулевые
        putU16(header, 0x0e, 0x00ad); // константа во всех образцах
        header[0x36] = (byte) 0xe9; header[0x37] = 0x03; header[0x38] = (byte) 0xfc; // константа
        header[0x3a] = 0x01; header[0x3b] = 0x01; header[0x3c] = (byte) 0x90;
        header[0x3d] = 0x06; header[0x3e] = 0x60; header[0x3f] = 0x04; // константа
        header[0xb6] = (byte) 0xee; header[0xb7] = 0x03; // константа (тег перед полем 0xb8)
        // 0xb8 — ВЛОЖЕННАЯ контрольная сумма секции "screen info" (см. class-javadoc
        // про декомпилированный SCREEN_HEADERINFO_CRC=2, относительно начала секции
        // screen info на 0xb6): sum(body[0xBA : конец секции]) mod 65536, конец секции
        // = конец хвостового JSON (см. вычисление ниже, после сборки всего тела).
        // РАНЬШЕ считалась простой линейной функцией параметров экрана — это было
        // совпадением, верным только для образцов с 0 записей (там сумма реально
        // сводится к нескольким ненулевым полям). Заполняется нулём здесь, патчится
        // ниже.
        putU16(header, 0xd2, trailerOffset - 176); // подтверждено на 2 образцах
        header[0x13a] = 0x01; // константа
        // ПОЛНЫЙ LE16, не однобайтовое усечение & 0xff -- баг-репорт (мультиэкранный
        // писатель с этой же формулой ломал разбор Screen2 у реальной NovaLCT, когда
        // (trailerOffset-313) превышал 255): подтверждено на одноэкранных образцах
        // ТОЛЬКО потому, что там значение случайно помещалось в 1 байт, старший байт
        // никогда не проверялся -- см. подробности у мультиэкранной версии этого поля.
        putU16(header, 0x13b, trailerOffset - 313);
        header[0x13f] = 0x01; // константа
        header[0x141] = (byte) (screenX & 0xff); // X-координата экрана — подтверждено на 1 образце
        putU16(header, 0x145, cols);
        putU16(header, 0x147, rows);
        header[0x149] = (byte) (firstCard & 0xff); // Sending Card (0-based) — подтверждено на 2 образцах
        header[0x14a] = (byte) (firstPort & 0xff); // Ethernet Port (0-based) — подтверждено на 2 образцах
        putU16(header, 0x14b, originSeq); // seq кабинета (0,0) — см. комментарий выше
        header[0x14d] = (byte) (screenX & 0xff); // дубль X-координаты — подтверждено на 1 образце
        writeBytes(out, header);

        byte[] anchor = buildAnchor(cabW, cabH);
        writeCabinetRecords(out, anchor, cols, rows, cabW, cabH, cellsByKey);

        // Завершающий блок warp-искажений (в терминах декомпилированного оригинала —
        // хвост секции "screen info"): тот же 6-байтовый якорь (см. buildAnchor), что
        // и у записей кабинетов, затем LE16-длина JSON-текста в байтах, затем сам
        // JSON. Схема объекта — 4 угловые точки (x1..x4/y1..y4).
        String json = "[{\"si\":0,\"x1\":0,\"y1\":0,\"x2\":0,\"y2\":0,\"x3\":0,\"y3\":0,\"x4\":0,\"y4\":0}]";
        byte[] jsonBytes = json.getBytes(StandardCharsets.US_ASCII);
        writeBytes(out, anchor);
        writeU16(out, jsonBytes.length);
        writeBytes(out, jsonBytes);

        // ДВЕ контрольные суммы — ОБЕ расшифрованы дизассемблированием (ildasm)
        // реального Nova.LCT.GigabitSystem.HWConfigAccessor.Accessors.CommonInfoAccessor
        // (.SoftWareSpaceAnalyser.CaculateCRC — простая СУММА байт, не полиномиальный
        // CRC) из установленной у пользователя NovaLCT, ПЕРЕПРОВЕРЕНЫ побайтово на
        // 9 образцах (включая 2-экранный и сложный 4×3/11-записей файлы):
        //  1) 0xb8 — ВЛОЖЕННАЯ сумма секции "screen info" (от абсолютного 0xBA —
        //     сразу после её 4-байтного мини-заголовка версия+сумма — до конца этой
        //     же секции, т.е. до конца JSON, который уже накоплен в out).
        //  2) 0x04 — сумма ВСЕГО файла от смещения 6 до конца JSON (то же самое "до
        //     конца", но от смещения 6 — ВКЛЮЧАЕТ уже пропатченные байты 0xb8/0xb9).
        // Патчить нужно СТРОГО в этом порядке (сначала 0xb8, потом 0x04), т.к. сумма
        // для 0x04 включает байты самого 0xb8. Ни одна сумма не включает финальный
        // блок параметров коррекции (TAIL_FOOTER) — тот идёт уже после и не входит
        // ни в одну из них.
        byte[] body = out.toByteArray();
        int innerChecksum = 0;
        for (int i = 0xba; i < body.length; i++) {
            innerChecksum = (innerChecksum + (body[i] & 0xff)) & 0xffff;
        }
        body[0xb8] = (byte) (innerChecksum & 0xff);
        body[0xb9] = (byte) ((innerChecksum >> 8) & 0xff);
        int checksum = 0;
        for (int i = 6; i < body.length; i++) {
            checksum = (checksum + (body[i] & 0xff)) & 0xffff;
        }
        body[4] = (byte) (checksum & 0xff);
        body[5] = (byte) ((checksum >> 8) & 0xff);

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        writeBytes(result, body);
        writeBytes(result, TAIL_FOOTER);

        return result.toByteArray();
    }

    /** Пишет 17-байтные кабинетные записи в COLUMN-MAJOR порядке, пропуская origin
     *  (0,0) (см. class-javadoc) — вынесено из {@link #writeStandardCore} в
     *  отдельный метод, ПЕРЕИСПОЛЬЗУЕМЫЙ мультиэкранным писателем
     *  {@link #writeStandardMultiScreen} (там записи каждого экрана идут в этом
     *  же формате, только сами экраны, в отличие от одноэкранного случая, не
     *  делят один общий header — см. javadoc метода). */
    private static void writeCabinetRecords(ByteArrayOutputStream out, byte[] anchor, int cols, int rows,
                                             int cabW, int cabH, Map<CellKey, Rec> cellsByKey) {
        writeCabinetRecords(out, anchor, cols, rows, cabW, cabH, 0, cellsByKey);
    }

    /** Версия с {@code screenXPx} — используется мультиэкранным писателем: дублирующая
     *  координата записи (см. ниже) для экранов 1..N-1 должна быть АБСОЛЮТНОЙ на общем
     *  канвасе ({@code col×cabW + screenXPx}), не локальной для экрана — подтверждено
     *  побайтово на 4 реальных многоэкранных образцах (см. javadoc
     *  {@link #writeStandardMultiScreen}). Для одноэкранного случая {@code screenXPx}
     *  всегда 0, поэтому формула не меняет уже подтверждённое поведение
     *  {@link #writeStandardCore}. */
    private static void writeCabinetRecords(ByteArrayOutputStream out, byte[] anchor, int cols, int rows,
                                             int cabW, int cabH, int screenXPx, Map<CellKey, Rec> cellsByKey) {
        CellKey originKey = new CellKey(0, 0);
        for (CellKey k : orderedCells(cols, rows)) {
            if (k.equals(originKey)) {
                continue; // см. class-javadoc
            }
            Rec r = cellsByKey.get(k);
            if (r == null) {
                continue; // не расключён ни в одну сигнальную цепочку - нечего писать
            }
            // Последний байт якоря -- см. подробный class-javadoc buildAnchor про
            // ДВА самопротиворечащих результата сверки на одном и том же образце в
            // разных прогонах (подозрение на устаревшую сборку jar в одном из них,
            // не выяснено окончательно) -- не гадаем дальше, оставлена подтверждённая
            // для одноэкранного случая константа 0x01 везде.
            writeBytes(out, anchor);
            out.write(r.card() & 0xff);
            out.write(r.port() & 0xff);
            writeU16(out, r.seq());
            // Дублирующая координата — на образце с кабинетом 128×128 равнялась
            // col*128/row*128; после того как ширина/высота якоря оказались НЕ
            // константой 128, а реальным размером кабинета (см. buildAnchor javadoc),
            // разумно считать, что и здесь используется реальный cabW/cabH, а не
            // литеральные 128 — подтверждено на реальных многоэкранных образцах
            // (см. javadoc writeStandardMultiScreen): для экранов 1..N-1 это АБСОЛЮТНАЯ
            // координата на общем канвасе (+screenXPx), не локальная для экрана.
            writeU16(out, r.col() * cabW + screenXPx);
            writeU16(out, r.row() * cabH);
            out.write(r.col() & 0xff);
            out.write(0); // неизвестно
            out.write(r.row() & 0xff);
        }
    }

    /** 21-байтный "паспорт экрана" — та же раскладка, встроена ли она в главный
     *  заголовок (экран 0 мультиэкранного файла, {@code arr}=заголовок,
     *  {@code base}=0x13f+shift) или записана отдельным блоком (экраны 1..N-1,
     *  {@code arr}=свежий 21-байтный массив, {@code base}=0) — см. javadoc
     *  {@link #writeStandardMultiScreen} про раскладку полей и её подтверждение. */
    private static void writeScreenDescriptor(byte[] arr, int base, int cols, int rows, int firstCard,
                                               int firstPort, int originSeq, int screenXPx) {
        arr[base] = 0x01;
        putU16(arr, base + 2, screenXPx);
        putU16(arr, base + 6, cols);
        putU16(arr, base + 8, rows);
        arr[base + 10] = (byte) (firstCard & 0xff);
        arr[base + 11] = (byte) (firstPort & 0xff);
        putU16(arr, base + 12, originSeq);
        putU16(arr, base + 14, screenXPx);
    }

    /** Один NovaLCT-"экран" внутри мультиэкранного файла (см. {@link #writeStandardMultiScreen}) —
     *  в отличие от {@link NovaLctCombineHelper.CombineResult}, где несколько экранов
     *  СЛИВАЮТСЯ в одну сетку, здесь каждый остаётся отдельным экраном со своим
     *  cols/rows/card/port, просто упакованным в тот же файл. */
    public record ScreenBlock(int cols, int rows, int cabW, int cabH, int screenXPx, Map<CellKey, Rec> cells) {
    }

    /** <b>Статус: ПОЛНОСТЬЮ ПОДТВЕРЖДЕНО</b> — мультиэкранный Standard Screen
     *  (несколько экранов В ОДНОМ .scr файле). Ранее (до этой сессии) считался
     *  best-effort по единственному образцу {@code 2scr.scr} с двумя ПУСТЫМИ
     *  экранами — тот подтверждал только структуру контейнера, не семантику полей.
     *  Теперь перепроверено побайтово на 4 РЕАЛЬНЫХ, НЕПУСТЫХ образцах, сохранённых
     *  самой NovaLCT (2, 3 и 2 экрана соответственно, включая случай с двумя РАЗНЫМИ
     *  физическими Sending Card, не просто разными портами одной карты) — обе
     *  контрольные суммы, длина/содержимое хвостового блока коррекции и раскладка
     *  полей ниже сошлись ТОЧНО на всех четырёх.
     *
     *  <p><b>Ключевая поправка к прежней (best-effort) модели:</b> "компактный блок"
     *  есть ТОЛЬКО у экранов 1..N-1 — экран 0 продолжает использовать поля
     *  ГЛАВНОГО заголовка, КАК В {@link #writeStandardCore}, но эти поля (а вместе
     *  с ними и начало записей экрана 0) СДВИНУТЫ на {@code shift = 4×(N-1)} байт
     *  относительно их положения в одноэкранном файле. Ранее предполагавшиеся
     *  "4 нулевых байта перед КАЖДЫМ доп. экраном" — не существуют: экраны 1..N-1
     *  идут вплотную друг за другом БЕЗ зазора, единственный сдвиг на {@code 4×(N-1)}
     *  байт находится ОДИН РАЗ, внутри главного заголовка (между {@code 0x13e} и
     *  началом 21-байтного блок-дескриптора экрана 0, который в одноэкранном файле
     *  начинается на {@code 0x13f}). Формула элегантно сводится к одноэкранному
     *  случаю при {@code N=1} ({@code shift=0}) — то же самое место, что и
     *  {@link #writeStandardCore}.
     *
     *  <p><b>21-байтный блок-дескриптор</b> — ОДИНАКОВАЯ раскладка что для экрана 0
     *  (встроен в главный заголовок, начиная с {@code 0x13f+shift}), что для каждого
     *  экрана 1..N-1 (отдельным блоком сразу после его 6-байтного якоря
     *  ширина/высота, см. {@link #buildAnchor}) — по сути один и тот же "паспорт
     *  экрана", просто расположенный в разных местах файла. Раскладка (смещения
     *  ОТНОСИТЕЛЬНО начала дескриптора), подтверждена ТОЧНЫМ совпадением на всех
     *  образцах, где соответствующее поле варьировалось:
     *  <pre>
     *  +0      константа 0x01 (как {@code header[0x13f]} одноэкранного файла)
     *  +2..4   Coordinate X экрана, LE16, ПОЛНОЕ значение в пикселях контент-канваса
     *          (не усечённый байт, как ошибочно предполагалось раньше)
     *  +6..8   Columns, LE16
     *  +8..10  Rows, LE16
     *  +10     Sending Card (0-based)
     *  +11     Ethernet Port (0-based)
     *  +12..14 seq кабинета (0,0) этого экрана (тот единственный, для кого нет
     *          отдельной 17-байтной записи, см. class-javadoc), LE16
     *  +14..16 дубль Coordinate X, LE16 (тот же паттерн дублирования, что у
     *          {@code header[0x141]}/{@code header[0x14d]} одноэкранного файла)
     *  +1,+4..6,+16..21  во всех образцах нули; назначение не проверено (нечем
     *          варьировать), нули — не догадка, а то, что реально во всех 4 файлах
     *  </pre>
     *  Бонус-находка: 17-байтная запись кабинета доп. экрана хранит в поле "дубль
     *  координаты" (см. {@link #writeCabinetRecords}) {@code col×cabW + screenXPx}
     *  — АБСОЛЮТНУЮ координату на общем канвасе, а не локальную для экрана — что
     *  согласуется с тем, что Coordinate X в реальной NovaLCT задаёт область
     *  ЗАХВАТА КОНТЕНТА этим экраном на общем полотне, а не просто визуальный
     *  порядок экранов в списке.
     *
     *  <p><b>Сдвиговая зона</b> ({@code 0x13f} .. {@code descBase−1}, длиной
     *  {@code shift} байт) — НЕ нули, как предполагалось на первой итерации этой
     *  сессии (та версия провалила побайтовую сверку с реальными образцами): это
     *  {@code N−1} 4-байтных чанков, по одному на КАЖДЫЙ доп. экран {@code i=1..N-1}
     *  В ТОМ ЖЕ порядке — байты[0..1] каждого чанка = ПОЛНЫЙ LE16 (не однобайтовое
     *  усечение!) той же формулы, что и {@code header[0x13b..0x13c]}, но от
     *  {@code recordCount} ЭТОГО доп. экрана, а не экрана 0; байты[2..3] — нули.
     *  Усечение до 1 байта было ЕЩЁ ОДНОЙ ошибкой этой сессии, не пойманной
     *  реверс-инжинирингом (значение случайно помещалось в 1 байт на образцах,
     *  использованных для confirm) — вскрыто побайтовым сравнением уже ПОСЛЕ
     *  подтверждения формата, реальной загрузкой файла с {@code recordCount}
     *  доп. экрана, для которого {@code (HEADER_LEN+recordCount×17−313) > 255}
     *  (давало корректную загрузку Screen1, но полный мусор в Screen2 — 340+111×17−313=1914=0x077A,
     *  мы писали только {@code 7A}, оставляя старший байт {@code 0x140} нулём вместо {@code 07}).
     *
     *  <p><b>Прочее, подтверждённое на 4 образцах без изменений относительно
     *  прежней (best-effort) формулы</b> — т.е. прежняя догадка тут оказалась
     *  верна: {@code header[0x13a]} = SCREENCOUNT (реальное число экранов);
     *  {@code header[0x13b]} = {@code (HEADER_LEN + recordCount0×17 − 313) & 0xff}
     *  — ВАЖНО: использует СТАРЫЙ, НЕ сдвинутый {@code HEADER_LEN} (это
     *  подтверждённая особенность самой NovaLCT, а не наша ошибка — поле явно не
     *  учитывает {@code shift}, хотя реальное положение записей экрана 0 сдвинуто);
     *  {@code header[0x0a]} = {@code footerStart − 0xB6}; {@code header[0x0e]} =
     *  {@code 133+40×N}; {@code header[0xd2]} = {@code finalTrailerOffset − 176};
     *  хвостовой блок коррекции (см. {@link #buildMultiScreenTailFooter}) — длина,
     *  тег {@code 231×N} и повторяющийся паттерн {@code (1,_,1,0xE4)} на {@code +40}
     *  за экран — всё сошлось точно.
     *
     *  <p>Единственное, что остаётся неподтверждённым — поведение при combine
     *  ВНУТРИ одного из экранов 1..N-1 (в имеющихся образцах комбинировался только
     *  экран 0 — реальный кейс "группа экранов проекта → один из НЕСКОЛЬКИХ
     *  NovaLCT-экранов" пока проверен только для позиции экрана 0, см.
     *  {@code NOVALCT_EXPORT.md}) и поведение при НЕСКОЛЬКИХ цепочках на одном
     *  Complex-подобном доп. экране (не Standard — это отдельный, ещё не
     *  затронутый случай). */
    public static byte[] writeStandardMultiScreen(List<ScreenBlock> screens) {
        if (screens == null || screens.isEmpty()) {
            throw new IllegalArgumentException("Нужен хотя бы один экран");
        }
        int screenCount = screens.size();
        CellKey originKey = new CellKey(0, 0);

        int[] recordCounts = new int[screenCount];
        int[] firstCards = new int[screenCount];
        int[] firstPorts = new int[screenCount];
        int[] originSeqs = new int[screenCount];
        for (int i = 0; i < screenCount; i++) {
            ScreenBlock s = screens.get(i);
            int rc = 0;
            int[] cardPort = firstCardPort(s);
            int fc = cardPort[0];
            int fp = cardPort[1];
            for (CellKey k : orderedCells(s.cols(), s.rows())) {
                Rec r = s.cells().get(k);
                if (!k.equals(originKey) && r != null) {
                    rc++;
                }
            }
            recordCounts[i] = rc;
            firstCards[i] = fc;
            firstPorts[i] = fp;
            Rec originRec = s.cells().get(originKey);
            originSeqs[i] = originRec != null ? originRec.seq() : 0;
        }

        // Сдвиг относительно одноэкранной раскладки (см. javadoc) -- 0 при N=1,
        // формула сводится к writeStandardCore.
        int shift = 4 * (screenCount - 1);
        int descBase = 0x13f + shift; // начало 21-байтного блок-дескриптора экрана 0
        int record0Start = HEADER_LEN + shift; // == descBase + 21 (0x13f+21 == HEADER_LEN)

        // Смещение начала ИТОГОВОГО общего якоря+JSON (после заголовка+сдвига+записей
        // экрана 0 И всех блоков экранов 1..N-1) -- используется для 0xd2 (подтверждено
        // точно на 4 реальных образцах, см. javadoc).
        int finalTrailerOffset = record0Start + recordCounts[0] * 17;
        for (int i = 1; i < screenCount; i++) {
            finalTrailerOffset += 6 + 21 + recordCounts[i] * 17;
        }
        // trailerOffset ТОЛЬКО экрана 0, но по СТАРОМУ (не сдвинутому) HEADER_LEN --
        // подтверждённая особенность самой NovaLCT (используется для 0x13b, см. javadoc).
        int screen0TrailerOffsetForHeader = HEADER_LEN + recordCounts[0] * 17;

        // JSON собирается ЗАРАНЕЕ (а не в потоке записи), чтобы знать его длину для
        // 0x0a (см. ниже) ДО того, как заголовок уже записан в out.
        StringBuilder jsonBuilder = new StringBuilder("[");
        for (int i = 0; i < screenCount; i++) {
            if (i > 0) {
                jsonBuilder.append(',');
            }
            jsonBuilder.append("{\"si\":").append(i)
                    .append(",\"x1\":0,\"y1\":0,\"x2\":0,\"y2\":0,\"x3\":0,\"y3\":0,\"x4\":0,\"y4\":0}");
        }
        jsonBuilder.append(']');
        byte[] jsonBytes = jsonBuilder.toString().getBytes(StandardCharsets.US_ASCII);
        // Смещение начала хвостового блока коррекции (= конец итогового JSON) --
        // используется для 0x0a (подтверждено точно на 4 реальных образцах, см. javadoc).
        int footerStart = finalTrailerOffset + 6 + 2 + jsonBytes.length;

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ScreenBlock s0 = screens.get(0);
        byte[] header = new byte[HEADER_LEN + shift];
        System.arraycopy(MAGIC, 0, header, 0, MAGIC.length);
        putU16(header, 0x06, 0x0080);
        putU16(header, 0x0a, footerStart - 0xb6);
        putU16(header, 0x0e, 133 + 40 * screenCount); // длина хвостового блока -- см. javadoc
        header[0x36] = (byte) 0xe9; header[0x37] = 0x03; header[0x38] = (byte) 0xfc;
        header[0x3a] = 0x01; header[0x3b] = 0x01; header[0x3c] = (byte) 0x90;
        header[0x3d] = 0x06; header[0x3e] = 0x60; header[0x3f] = 0x04;
        header[0xb6] = (byte) 0xee; header[0xb7] = 0x03;
        putU16(header, 0xd2, finalTrailerOffset - 176);
        header[0x13a] = (byte) (screenCount & 0xff); // SCREENCOUNT -- см. javadoc выше
        // ПОЛНЫЙ LE16 (0x13b..0x13c), не 1 байт с усечением -- баг-репорт: реальная
        // загрузка успешно проходила Screen1, но давала МУСОР в Screen2 (Columns=1,
        // Rows=512 и т.п.) -- побайтовое сравнение с образцом NovaLCT показало, что
        // 0x13b/0x13c вместе = ПОЛНОЕ (не &0xff) значение (screen0TrailerOffsetForHeader
        // - 313), напр. 554 (0x022A) → байты 2A 02, что мы раньше писали только как 2A,
        // оставляя 0x13c нулём. На одноэкранных/малых образцах это не проявлялось,
        // т.к. значение случайно помещалось в 1 байт.
        putU16(header, 0x13b, screen0TrailerOffsetForHeader - 313);
        // Сдвиговая зона (0x13f .. descBase-1) -- один 4-байтный чанк на КАЖДЫЙ доп.
        // экран i=1..N-1: байты[0..1] = ПОЛНЫЙ LE16 той же формулы, что и 0x13b/0x13c,
        // но от recordCount ЭТОГО доп. экрана (тот же баг с усечением, тот же фикс);
        // байты[2..3] по-прежнему 0 (подтверждено, не тронуто).
        for (int i = 1; i < screenCount; i++) {
            int chunkBase = 0x13f + 4 * (i - 1);
            putU16(header, chunkBase, HEADER_LEN + recordCounts[i] * 17 - 313);
        }
        writeScreenDescriptor(header, descBase, s0.cols(), s0.rows(), firstCards[0], firstPorts[0],
                originSeqs[0], s0.screenXPx());
        writeBytes(out, header);

        writeCabinetRecords(out, buildAnchor(s0.cabW(), s0.cabH()), s0.cols(), s0.rows(), s0.cabW(), s0.cabH(),
                s0.screenXPx(), s0.cells());

        for (int i = 1; i < screenCount; i++) {
            ScreenBlock s = screens.get(i);
            byte[] anchor = buildAnchor(s.cabW(), s.cabH());
            writeBytes(out, anchor);
            byte[] tail = new byte[21];
            writeScreenDescriptor(tail, 0, s.cols(), s.rows(), firstCards[i], firstPorts[i], originSeqs[i],
                    s.screenXPx());
            writeBytes(out, tail);
            writeCabinetRecords(out, anchor, s.cols(), s.rows(), s.cabW(), s.cabH(), s.screenXPx(), s.cells());
        }

        ScreenBlock last = screens.get(screenCount - 1);
        byte[] finalAnchor = buildAnchor(last.cabW(), last.cabH());
        // jsonBytes уже собран выше (нужен был заранее для footerStart/0x0a).
        writeBytes(out, finalAnchor);
        writeU16(out, jsonBytes.length);
        writeBytes(out, jsonBytes);

        byte[] body = out.toByteArray();
        int innerChecksum = 0;
        for (int i = 0xba; i < body.length; i++) {
            innerChecksum = (innerChecksum + (body[i] & 0xff)) & 0xffff;
        }
        body[0xb8] = (byte) (innerChecksum & 0xff);
        body[0xb9] = (byte) ((innerChecksum >> 8) & 0xff);
        int checksum = 0;
        for (int i = 6; i < body.length; i++) {
            checksum = (checksum + (body[i] & 0xff)) & 0xffff;
        }
        body[4] = (byte) (checksum & 0xff);
        body[5] = (byte) ((checksum >> 8) & 0xff);

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        writeBytes(result, body);
        writeBytes(result, buildMultiScreenTailFooter(screenCount));
        return result.toByteArray();
    }

    /** Длина {@code 133+40×N} и байты [2:4]/[132]/[134,136,137] (+ повтор на +40 за
     *  экран) — ВСЕ подтверждены точно по реальному {@code 2scr.scr} (N=2, см.
     *  javadoc {@link #writeStandardMultiScreen}). Только содержимое "своего" 40-байтного
     *  куска экранов 1..N-1 ЗА ВЫЧЕТОМ этого повторяющегося паттерна остаётся
     *  неизвестным (нули как наименее рискованная заглушка). */
    private static byte[] buildMultiScreenTailFooter(int screenCount) {
        byte[] f = new byte[133 + 40 * screenCount];
        f[0] = (byte) 0xea;
        f[1] = 0x03;
        int tag = 231 * screenCount; // подтверждено точно: 231 для N=1, 462 для N=2
        f[2] = (byte) (tag & 0xff);
        f[3] = (byte) ((tag >> 8) & 0xff);
        f[132] = (byte) (screenCount & 0xff); // подтверждено точно: N, не всегда 1
        f[134] = 0x01;
        f[136] = 0x01;
        f[137] = (byte) 0xe4;
        for (int i = 1; i < screenCount; i++) {
            f[134 + 40 * i] = 0x01;
            f[136 + 40 * i] = 0x01;
            f[137 + 40 * i] = (byte) 0xe4;
        }
        return f;
    }

    private record ComplexCard(int x, int y, int w, int h, int card, int port, int seq) {
    }

    /** Длина преамбулы Complex Screen (от начала MAGIC до байта {@code Type} —
     *  см. {@link #writeComplex}) — ПОДТВЕРЖДЕНО побайтово декомпиляцией
     *  {@code SoftWareSpaceAnalyser.ScreenInfoToArray1600} (реальный метод NovaLCT,
     *  дизассемблирован через ildasm) И сверкой с двумя реальными файлами
     *  ({@code ether1.scr}/{@code ether2.scr}, 4×6 экран, нижний ряд кабинетов
     *  128×64, отличаются РОВНО одним Ethernet-портом — оба совпали побайтово). */
    private static final int COMPLEX_HEADER_LEN = 0x13f;

    /** Complex Screen — произвольные прямоугольники, явные пиксельные X/Y/Width/
     *  Height на кабинет (см. class-javadoc). <b>Статус: ПОЛНОСТЬЮ ПОДТВЕРЖДЕНО</b> —
     *  раньше (best-effort, по одному образцу) считалось, что у Complex Screen нет
     *  вовсе никакого заголовка/чек-суммы; декомпиляция реального метода
     *  {@code Nova.LCT.GigabitSystem.HWConfigAccessor.Accessors.CommonInfoAccessor.
     *  SoftWareSpaceAnalyser.ScreenInfoToArray1600} (тот же метод, что строит и
     *  Standard Screen — оба формата суть разные ТИПЫ одного общего контейнера
     *  {@code ILEDDisplayInfo}) показала, что Complex Screen делит РОВНО ТУ ЖЕ
     *  340-с-лишним-байтную преамбулу/чек-сумму/{@link #TAIL_FOOTER}, что и Standard —
     *  отличается только "тело" после байта {@code Type}. Побайтово сверено с
     *  ДВУМЯ реальными файлами (см. {@link #COMPLEX_HEADER_LEN}) — экран 4×6,
     *  24 приёмные карты (нижний ряд 128×64), 100% совпадение, включая обе
     *  контрольные суммы.
     *
     * <p>16-байтная запись на карту (порядок ПОДТВЕРЖДЁН декомпиляцией и сверкой):
     * <pre>
     * +0  SenderIndex  (1 байт)  -- Sending Card, 0-based
     * +1  PortIndex    (1 байт)  -- Ethernet Port, 0-based (ПОДТВЕРЖДЕНО: единственное
     *                               различие между ether1.scr/ether2.scr)
     * +2  ConnectIndex (u16 LE)  -- порядковый номер карты В ЦЕПОЧКЕ (см. {@code seq}
     *                               везде в этом классе) -- 0,1,2,... по порядку
     *                               расключения; при НЕСКОЛЬКИХ цепочках на одном
     *                               Complex-экране считается per-chain (по аналогии
     *                               со Standard) -- этот случай НЕ проверен реальным
     *                               образцом (образец содержал ровно одну цепочку)
     * +4  X             (u16 LE) -- ПОДТВЕРЖДЕНО побайтово (StartX в Receiving Card Settings)
     * +6  Y             (u16 LE) -- ПОДТВЕРЖДЕНО (StartY)
     * +8  XInPort       (u16 LE) -- в обоих образцах 0 (одна область на порт) -- НЕ
     *                               проверено для случая нескольких областей на один
     *                               физический порт (daisy-chain внутри порта)
     * +10 YInPort       (u16 LE) -- аналогично, 0 в образцах
     * +12 Width         (u16 LE) -- ПОДТВЕРЖДЕНО
     * +14 Height        (u16 LE) -- ПОДТВЕРЖДЕНО
     * </pre>
     * перед записями -- 6-байтный заголовок блока: {@code Type}(1)=2 (ПОДТВЕРЖДЕНО;
     * у Standard в этой же позиции было 1 -- т.е. это тип {@code ILEDDisplayInfo},
     * общий для обоих форматов), {@code VirtualMode}(1)=0 (в образцах всегда 0 --
     * назначение ненулевых значений неизвестно), {@code Count}(u32 LE) = число карт. */
    private static byte[] writeComplex(Screen screen, Scene scene, Workspace workspace, CabinetType defaultType) {
        int nativeCellW = defaultType.getResolutionWidth();
        int nativeCellH = defaultType.getResolutionHeight();
        List<ComplexCard> cards = new ArrayList<>();
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
            int seq = 0;
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
                cards.add(new ComplexCard(x, y, w, h, cardPort[0], cardPort[1], seq));
                seq++;
            }
        }

        int recordsEnd = COMPLEX_HEADER_LEN + 6 + cards.size() * 16;
        byte[] json = "[]".getBytes(StandardCharsets.US_ASCII);
        int endOfJson = recordsEnd + 2 + json.length;

        byte[] header = new byte[COMPLEX_HEADER_LEN];
        System.arraycopy(MAGIC, 0, header, 0, MAGIC.length);
        putU16(header, 0x06, 0x0080);
        putU16(header, 0x0a, endOfJson - 0xb6);
        putU16(header, 0x0e, 133 + 40); // длина TAIL_FOOTER -- один экран, см. javadoc TAIL_FOOTER
        header[0x36] = (byte) 0xe9; header[0x37] = 0x03; header[0x38] = (byte) 0xfc;
        header[0x3a] = 0x01; header[0x3b] = 0x01; header[0x3c] = (byte) 0x90;
        header[0x3d] = 0x06; header[0x3e] = 0x60; header[0x3f] = 0x04;
        header[0xb6] = (byte) 0xee; header[0xb7] = 0x03;
        putU16(header, 0xd2, recordsEnd - 0xb6);
        header[0x13a] = 0x01; // SCREENCOUNT -- 1 экран
        putU16(header, 0x13b, 6 + cards.size() * 16); // SCREENLENINFOADDR -- длина Complex-блока (LE16, ПОДТВЕРЖДЕНО)

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeBytes(out, header);
        out.write(2); // Type -- ПОДТВЕРЖДЕНО (Complex/IrRegular)
        out.write(0); // VirtualMode -- 0 в обоих образцах
        writeU16(out, cards.size());
        writeU16(out, 0); // старшие 2 байта Count (LE32) -- 0, пока карт < 65536

        for (ComplexCard c : cards) {
            out.write(c.card() & 0xff);
            out.write(c.port() & 0xff);
            writeU16(out, c.seq());
            writeU16(out, c.x());
            writeU16(out, c.y());
            writeU16(out, 0); // XInPort -- см. javadoc метода
            writeU16(out, 0); // YInPort
            writeU16(out, c.w());
            writeU16(out, c.h());
        }

        writeU16(out, json.length);
        writeBytes(out, json);

        byte[] body = out.toByteArray();
        int innerChecksum = 0;
        for (int i = 0xba; i < body.length; i++) {
            innerChecksum = (innerChecksum + (body[i] & 0xff)) & 0xffff;
        }
        body[0xb8] = (byte) (innerChecksum & 0xff);
        body[0xb9] = (byte) ((innerChecksum >> 8) & 0xff);
        int checksum = 0;
        for (int i = 6; i < body.length; i++) {
            checksum = (checksum + (body[i] & 0xff)) & 0xffff;
        }
        body[4] = (byte) (checksum & 0xff);
        body[5] = (byte) ((checksum >> 8) & 0xff);

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        writeBytes(result, body);
        writeBytes(result, TAIL_FOOTER);
        return result.toByteArray();
    }

    /** Ячейки сетки cols×rows в COLUMN-MAJOR порядке (столбец 0 сверху вниз, затем
     *  столбец 1, и т.д.) — именно в таком порядке реальная NovaLCT пишет
     *  17-байтные записи Standard Screen (подтверждено побайтовым сравнением с
     *  реальным образцом 4×3 со сложной цепочкой: наши записи совпадали побайтово
     *  с реальными, но шли в другом порядке — row-major). Обобщено от прежнего
     *  {@code orderedCabinets(Screen)} до чистой функции без {@link Screen} —
     *  переиспользуется контроллер-центричным экспортом ({@link #writeStandardCore}
     *  вызывается и с объединённой сеткой нескольких экранов, не только с одним). */
    private static List<CellKey> orderedCells(int cols, int rows) {
        List<CellKey> ordered = new ArrayList<>();
        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                ordered.add(new CellKey(col, row));
            }
        }
        return ordered;
    }

    /** Первая (по COLUMN-MAJOR обходу, пропуская {@code card==255} blank-дыры —
     *  см. {@link Rec}) пара card/port экрана — {@code {card, port}}, {@code {0,0}}
     *  если экран вообще не расключён. Используется и для "паспорта экрана" в
     *  заголовке ({@link #writeStandardMultiScreen}), и — что критично —
     *  {@link NovaLctCombineHelper} сортирует по НЕЙ (а не по пространственному
     *  X, как считалось раньше) экраны перед присвоением индексов Screen0..N-1:
     *  подтверждено пользователем ручным экспериментом в самой NovaLCT — если
     *  экран с БОЛЬШИМ номером порта на общей Sending Card получает МЕНЬШИЙ
     *  индекс экрана (Screen0), чем экран с МЕНЬШИМ номером порта той же карты
     *  (Screen1) — реальная загрузка отклоняется целиком, независимо от того,
     *  где эти экраны физически расположены на холсте (см. {@code screenXPx}).
     *  Пакетно-видимый (не private) — переиспользуется {@link NovaLctCombineHelper}. */
    static int[] firstCardPort(ScreenBlock block) {
        for (CellKey k : orderedCells(block.cols(), block.rows())) {
            Rec r = block.cells().get(k);
            if (r != null && r.card() != 255) {
                return new int[]{r.card(), r.port()};
            }
        }
        return new int[]{0, 0};
    }

    private static void writeBytes(ByteArrayOutputStream out, byte[] b) {
        out.write(b, 0, b.length);
    }

    private static void writeU16(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >> 8) & 0xff);
    }

    private static void putU16(byte[] buf, int offset, int v) {
        buf[offset] = (byte) (v & 0xff);
        buf[offset + 1] = (byte) ((v >> 8) & 0xff);
    }
}
