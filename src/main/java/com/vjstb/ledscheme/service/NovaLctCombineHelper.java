package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Объединяет несколько экранов, резолвнутых {@link NovaLctControllerResolver}, в
 *  ОДНУ виртуальную Standard Screen сетку для NovaLCT — размещение задаётся
 *  {@link ScreenSlot} (эфемерная раскладка из {@code LctPresetMasterDialog}, живёт
 *  только на время диалога экспорта), НЕ {@code ContentCanvas}: канвас
 *  ("Генерация масок") создан для позиционирования экранов под видео-контент
 *  (произвольные пиксельные координаты) — для этой задачи (уместить несколько
 *  экранов ОДНОГО типа кабинета в границы одного NovaLCT-экрана, как это делает
 *  сама NovaLCT) нужна СЕТОЧНАЯ, не пиксельная раскладка, и канвас оказался не тем
 *  инструментом (баг-репорт пользователя: комбинирование экранов разной высоты
 *  через канвас давало в реальной NovaLCT "Failed to load screen information
 *  file!" — см. javadoc {@link #combine} про настоящую причину и её решение). */
public final class NovaLctCombineHelper {

    private NovaLctCombineHelper() {
    }

    /** Порядок экранов перед присвоением индексов Screen0..N-1 в мультиэкранном
     *  файле — по возрастанию (Sending Card, Ethernet Port) первой невырожденной
     *  записи экрана ({@link NovaLctScrWriter#firstCardPort}), а НЕ по
     *  пространственному {@code screenXPx} (та прежняя гипотеза "экран 0 — самый
     *  левый" была ОПРОВЕРГНУТА реальной загрузкой в NovaLCT: если экран с БОЛЬШИМ
     *  номером порта общей Sending Card получал МЕНЬШИЙ индекс экрана, чем экран с
     *  МЕНЬШИМ номером порта той же карты — файл отклонялся целиком, независимо от
     *  их взаимного расположения на холсте). {@code screenXPx} остаётся тем, чем и
     *  был — координатой X экрана внутри самого NovaLCT (поле "Coordinate"), это
     *  используется только как разрешение ничьи между экранами с одинаковой
     *  card/port парой. */
    private static final Comparator<NovaLctScrWriter.ScreenBlock> SCREEN_ORDER = (a, b) -> {
        int[] pa = NovaLctScrWriter.firstCardPort(a);
        int[] pb = NovaLctScrWriter.firstCardPort(b);
        if (pa[0] != pb[0]) {
            return Integer.compare(pa[0], pb[0]);
        }
        if (pa[1] != pb[1]) {
            return Integer.compare(pa[1], pb[1]);
        }
        return Integer.compare(a.screenXPx(), b.screenXPx());
    };

    /** Размещение одного экрана на комбинированной NovaLCT-сетке — офсет уже В
     *  ЯЧЕЙКАХ кабинета (не px, в отличие от бывшего {@code CanvasPlacement}), т.к.
     *  вся раскладка строится в единицах "ячейка сетки", как в Basic Information
     *  реальной NovaLCT (Columns/Rows), а не в пикселях видео-контента. */
    public record ScreenSlot(Screen screen, int colOffset, int rowOffset) {
    }

    public record CombineResult(int cols, int rows, int cabW, int cabH,
                                 Map<NovaLctScrWriter.CellKey, NovaLctScrWriter.Rec> cells) {
    }

    /** Причина, по которой {@code slots} не годятся для объединения в один экран —
     *  {@code null}, если годятся. Дыры (ячейки, не покрытые НИ ОДНИМ экраном)
     *  ЗДЕСЬ НЕ являются ошибкой — это штатный NovaLCT-кейс ("blank"-ячейки, см.
     *  {@link #combine}), они просто получат {@code card=0xFF} при сборке. */
    public static String validateSlots(List<ScreenSlot> slots, AppModel model) {
        if (slots == null || slots.isEmpty()) {
            return "Не размещено ни одного экрана";
        }
        Integer cabW = null;
        Integer cabH = null;
        for (ScreenSlot slot : slots) {
            CabinetType t = model.typeOf(slot.screen());
            int w = t != null ? t.getResolutionWidth() : 0;
            int h = t != null ? t.getResolutionHeight() : 0;
            if (w <= 0 || h <= 0) {
                return "У экрана «" + slot.screen().getName() + "» не задан тип кабинета";
            }
            if (cabW == null) {
                cabW = w;
                cabH = h;
            } else if (!cabW.equals(w) || !cabH.equals(h)) {
                return "У экранов разный физический размер кабинета — объединение в 1 экран невозможно";
            }
        }
        return validateNoOverlap(slots);
    }

    /** Перекрытие (одна и та же ячейка объединённой сетки принадлежит НЕСКОЛЬКИМ
     *  экранам сразу) — в отличие от дыр, это настоящий конфликт данных (какая
     *  запись "победит" в карте ячеек), не связан с blank-механизмом и НЕ
     *  подтверждён реальным образцом как поддерживаемый — остаётся под запретом. */
    private static String validateNoOverlap(List<ScreenSlot> slots) {
        Map<Long, ScreenSlot> owner = new HashMap<>();
        for (ScreenSlot slot : slots) {
            Screen s = slot.screen();
            for (int c = slot.colOffset(); c < slot.colOffset() + s.getCols(); c++) {
                for (int r = slot.rowOffset(); r < slot.rowOffset() + s.getRows(); r++) {
                    long key = (((long) c) << 32) | (r & 0xffffffffL);
                    ScreenSlot prev = owner.putIfAbsent(key, slot);
                    if (prev != null && prev != slot) {
                        return "Экраны «" + prev.screen().getName() + "» и «" + s.getName()
                                + "» перекрываются в объединённой сетке — объединение в 1 экран невозможно";
                    }
                }
            }
        }
        return null;
    }

    /** Строит объединённую сетку размером {@code overallCols×overallRows} (задаётся
     *  явно — как поля Columns/Rows в Basic Information реальной NovaLCT, могут
     *  быть БОЛЬШЕ, чем bounding box размещённых экранов, см. {@code 111.scr}: там
     *  Columns=18 совпало с суммой ширин 4+10+4, но в общем случае могут отличаться).
     *
     * <p><b>Blank-ячейки</b> — любая ячейка внутри {@code [0,overallCols)×[0,overallRows)},
     * НЕ входящая в footprint ни одного {@code slot} (т.е. физически не
     * принадлежащая никакому реальному экрану — в отличие от кабинета, который
     * просто не расключён ни в одну цепочку, тот по-прежнему не пишется вовсе),
     * получает синтетическую запись {@code Rec(row, col, 255, 0, 0)}. {@code 0xFF}
     * — ПОДТВЕРЖДЁННЫЙ побайтовым разбором реального {@code 111.scr} (файл,
     * сохранённый самой NovaLCT из конфигурации "несколько экранов одного типа
     * кабинета внутри границ одного NovaLCT-экрана", присланный пользователем)
     * сентинел "blank" — ровно так реальная NovaLCT помечает неиспользуемые
     * ячейки при таком объединении, это НЕ догадка. Остальные поля синтетической
     * записи (port/seq) роли не играют (в образце port=1,seq=1, здесь используются
     * нейтральные 0/0 — оба варианта дают одинаковый визуальный/функциональный
     * результат в NovaLCT, т.к. у записи нет реальной платы). */
    public static CombineResult combine(List<ScreenSlot> slots, int overallCols, int overallRows,
                                         List<NovaLctControllerResolver.CabinetRec> recs, AppModel model) {
        int cabW = 128;
        int cabH = 128;
        for (NovaLctControllerResolver.CabinetRec r : recs) {
            CabinetType t = model.typeOf(r.sourceScreen());
            if (t != null && t.getResolutionWidth() > 0) {
                cabW = t.getResolutionWidth();
                cabH = t.getResolutionHeight();
                break;
            }
        }

        Map<String, ScreenSlot> slotByScreenId = new HashMap<>();
        for (ScreenSlot slot : slots) {
            slotByScreenId.put(slot.screen().getId(), slot);
        }

        Map<NovaLctScrWriter.CellKey, NovaLctScrWriter.Rec> combined = new HashMap<>();
        for (NovaLctControllerResolver.CabinetRec r : recs) {
            ScreenSlot slot = slotByScreenId.get(r.sourceScreen().getId());
            if (slot == null) {
                continue; // экран не размещён -- см. validateSlots()
            }
            int globalCol = slot.colOffset() + r.col();
            int globalRow = slot.rowOffset() + r.row();
            combined.put(new NovaLctScrWriter.CellKey(globalCol, globalRow),
                    new NovaLctScrWriter.Rec(globalRow, globalCol, r.cardIndex(), r.portInPool(), r.seq()));
        }

        // Footprint каждого экрана (ВСЯ его сетка, а не только расключённые
        // кабинеты) -- чтобы отличить "ячейка принадлежит экрану, но просто не
        // расключена" (не пишется вовсе, как и в одноэкранном случае) от "ячейка
        // не принадлежит вообще никакому экрану" (blank, card=0xFF).
        boolean[][] covered = new boolean[overallCols][overallRows];
        for (ScreenSlot slot : slots) {
            Screen s = slot.screen();
            for (int c = slot.colOffset(); c < slot.colOffset() + s.getCols() && c < overallCols; c++) {
                for (int r = slot.rowOffset(); r < slot.rowOffset() + s.getRows() && r < overallRows; r++) {
                    if (c >= 0 && r >= 0) {
                        covered[c][r] = true;
                    }
                }
            }
        }
        for (int c = 0; c < overallCols; c++) {
            for (int r = 0; r < overallRows; r++) {
                if (!covered[c][r]) {
                    combined.put(new NovaLctScrWriter.CellKey(c, r), new NovaLctScrWriter.Rec(r, c, 255, 0, 0));
                }
            }
        }

        return new CombineResult(overallCols, overallRows, cabW, cabH, combined);
    }

    /** Best-effort вариант для {@link NovaLctScrWriter#writeStandardMultiScreen} — экраны
     *  НЕ сливаются в одну сетку (в отличие от {@link #combine}), каждый остаётся
     *  своим {@link NovaLctScrWriter.ScreenBlock} с собственными cols/rows/card/port.
     *  Экраны упорядочены по {@link #SCREEN_ORDER} (Sending Card/Port, НЕ
     *  пространственно — см. её javadoc про опровергнутую гипотезу). */
    public static List<NovaLctScrWriter.ScreenBlock> splitSeparate(List<ScreenSlot> slots,
            List<NovaLctControllerResolver.CabinetRec> recs, AppModel model) {
        Map<String, ScreenSlot> slotByScreenId = new HashMap<>();
        for (ScreenSlot slot : slots) {
            slotByScreenId.put(slot.screen().getId(), slot);
        }

        Map<Screen, List<NovaLctControllerResolver.CabinetRec>> byScreen = new LinkedHashMap<>();
        for (NovaLctControllerResolver.CabinetRec r : recs) {
            byScreen.computeIfAbsent(r.sourceScreen(), s -> new ArrayList<>()).add(r);
        }

        List<NovaLctScrWriter.ScreenBlock> blocks = new ArrayList<>();
        for (Screen s : byScreen.keySet()) {
            CabinetType t = model.typeOf(s);
            int cabW = t != null ? t.getResolutionWidth() : 128;
            int cabH = t != null ? t.getResolutionHeight() : 128;
            ScreenSlot slot = slotByScreenId.get(s.getId());
            int screenXPx = slot != null ? slot.colOffset() * cabW : 0;
            Map<NovaLctScrWriter.CellKey, NovaLctScrWriter.Rec> cells = new HashMap<>();
            for (NovaLctControllerResolver.CabinetRec r : byScreen.get(s)) {
                cells.put(new NovaLctScrWriter.CellKey(r.col(), r.row()),
                        new NovaLctScrWriter.Rec(r.row(), r.col(), r.cardIndex(), r.portInPool(), r.seq()));
            }
            blocks.add(new NovaLctScrWriter.ScreenBlock(s.getCols(), s.getRows(), cabW, cabH, screenXPx, cells));
        }
        blocks.sort(SCREEN_ORDER);
        return blocks;
    }

    /** Как {@link #splitSeparate}, но с ГРУППИРОВКОЙ: экраны, у которых в
     *  {@code groupIdByScreenId} одно и то же значение, сливаются в ОДИН
     *  NovaLCT-экран тем же механизмом, что и {@link #combine} (blank-дыры
     *  {@code card=0xFF} внутри границ ГРУППЫ, а не всей сетки — тот же
     *  подтверждённый по {@code 111.scr} сентинел, см. {@link #combine}), вместо
     *  того чтобы остаться раздельными экранами — реальный кейс "несколько
     *  экранов проекта образуют один из НЕСКОЛЬКИХ NovaLCT-экранов" (LCTPresetMaster
     *  UI, см. {@code LctPresetMasterDialog}). Экран без записи в карте (или с
     *  уникальным для него значением) ведёт себя РОВНО как в {@link #splitSeparate}
     *  — группировка полностью опциональна, {@code groupIdByScreenId} может быть
     *  {@code null} или пустой картой, тогда результат идентичен {@link #splitSeparate}.
     *
     * <p>Coordinate X группы — {@code colOffset} её САМОГО ЛЕВОГО слота × cabW, тем же
     * способом, что и для одиночного экрана в {@link #splitSeparate} — это чисто
     * позиционное поле "Coordinate" в самой NovaLCT, НЕ влияет на индекс экрана.
     * Итоговые блоки упорядочены по {@link #SCREEN_ORDER} (Sending Card/Port, см.
     * её javadoc). */
    public static List<NovaLctScrWriter.ScreenBlock> splitSeparateGrouped(List<ScreenSlot> slots,
            Map<String, Integer> groupIdByScreenId,
            List<NovaLctControllerResolver.CabinetRec> recs, AppModel model) {
        Map<Screen, List<NovaLctControllerResolver.CabinetRec>> recsByScreen = new LinkedHashMap<>();
        for (NovaLctControllerResolver.CabinetRec r : recs) {
            recsByScreen.computeIfAbsent(r.sourceScreen(), s -> new ArrayList<>()).add(r);
        }

        Map<Object, List<ScreenSlot>> byGroup = new LinkedHashMap<>();
        for (ScreenSlot slot : slots) {
            Integer gid = groupIdByScreenId != null ? groupIdByScreenId.get(slot.screen().getId()) : null;
            Object key = gid != null ? gid : slot.screen().getId();
            byGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(slot);
        }

        List<NovaLctScrWriter.ScreenBlock> blocks = new ArrayList<>();
        for (List<ScreenSlot> groupSlots : byGroup.values()) {
            if (groupSlots.size() == 1 && !recsByScreen.containsKey(groupSlots.get(0).screen())) {
                continue; // как в splitSeparate -- нерасключённый ОДИНОЧНЫЙ экран пропускается;
                          // экран внутри настоящей группы (2+) остаётся ради footprint/blank,
                          // см. buildGroupBlock
            }
            blocks.add(buildGroupBlock(groupSlots, recsByScreen, model));
        }
        blocks.sort(SCREEN_ORDER);
        return blocks;
    }

    /** Собирает ОДИН {@link NovaLctScrWriter.ScreenBlock} из слотов одной группы —
     *  границы = bounding box слотов группы (не всей сетки, в отличие от
     *  {@link #combine}), координаты кабинетов переводятся в ЛОКАЛЬНЫЕ для группы
     *  (вычитанием {@code min(colOffset)}/{@code min(rowOffset)} группы), дыры
     *  внутри этого bounding box — {@code card=0xFF} (см. {@link #combine} про
     *  подтверждение сентинела). Для группы из ОДНОГО слота с полностью
     *  расключённым экраном вырождается в то же самое, что строил {@link #splitSeparate}
     *  (bounding box == сам экран, дыр нет). */
    private static NovaLctScrWriter.ScreenBlock buildGroupBlock(List<ScreenSlot> groupSlots,
            Map<Screen, List<NovaLctControllerResolver.CabinetRec>> recsByScreen, AppModel model) {
        int cabW = 128;
        int cabH = 128;
        for (ScreenSlot slot : groupSlots) {
            CabinetType t = model.typeOf(slot.screen());
            if (t != null && t.getResolutionWidth() > 0) {
                cabW = t.getResolutionWidth();
                cabH = t.getResolutionHeight();
                break;
            }
        }

        int minCol = Integer.MAX_VALUE;
        int minRow = Integer.MAX_VALUE;
        int maxCol = Integer.MIN_VALUE;
        int maxRow = Integer.MIN_VALUE;
        for (ScreenSlot slot : groupSlots) {
            Screen s = slot.screen();
            minCol = Math.min(minCol, slot.colOffset());
            minRow = Math.min(minRow, slot.rowOffset());
            maxCol = Math.max(maxCol, slot.colOffset() + s.getCols());
            maxRow = Math.max(maxRow, slot.rowOffset() + s.getRows());
        }
        int groupCols = maxCol - minCol;
        int groupRows = maxRow - minRow;
        int screenXPx = minCol * cabW;

        Map<NovaLctScrWriter.CellKey, NovaLctScrWriter.Rec> cells = new HashMap<>();
        boolean[][] covered = new boolean[groupCols][groupRows];
        for (ScreenSlot slot : groupSlots) {
            Screen s = slot.screen();
            int localColOffset = slot.colOffset() - minCol;
            int localRowOffset = slot.rowOffset() - minRow;
            for (int c = localColOffset; c < localColOffset + s.getCols() && c < groupCols; c++) {
                for (int r = localRowOffset; r < localRowOffset + s.getRows() && r < groupRows; r++) {
                    if (c >= 0 && r >= 0) {
                        covered[c][r] = true;
                    }
                }
            }
            List<NovaLctControllerResolver.CabinetRec> screenRecs = recsByScreen.get(s);
            if (screenRecs == null) {
                continue; // экран группы без единой расключённой цепочки -- вносит только footprint
            }
            for (NovaLctControllerResolver.CabinetRec r : screenRecs) {
                int globalCol = localColOffset + r.col();
                int globalRow = localRowOffset + r.row();
                cells.put(new NovaLctScrWriter.CellKey(globalCol, globalRow),
                        new NovaLctScrWriter.Rec(globalRow, globalCol, r.cardIndex(), r.portInPool(), r.seq()));
            }
        }
        for (int c = 0; c < groupCols; c++) {
            for (int r = 0; r < groupRows; r++) {
                if (!covered[c][r]) {
                    // port=1/seq=1, НЕ 0/0 -- баг-репорт (NullReferenceException при
                    // загрузке в реальной NovaLCT): побайтовое сравнение с образцом,
                    // сохранённым самой NovaLCT для ЭТОЙ ЖЕ группы (Center+Right,
                    // 20 blank-ячеек), показало port=1/seq=1 у ВСЕХ 20 blank-записей,
                    // а не 0/0, как считалось раньше (см. {@link #combine}, где 0/0
                    // тоже применяется -- та формула отдельно подтверждена по 111.scr
                    // и здесь НЕ трогается, т.к. другого прямого свидетельства для
                    // неё нет).
                    cells.put(new NovaLctScrWriter.CellKey(c, r), new NovaLctScrWriter.Rec(r, c, 255, 1, 1));
                }
            }
        }

        return new NovaLctScrWriter.ScreenBlock(groupCols, groupRows, cabW, cabH, screenXPx, cells);
    }
}
