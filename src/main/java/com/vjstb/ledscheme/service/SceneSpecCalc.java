package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CableLengthProfile;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.SchemaSheet;
import com.vjstb.ledscheme.model.Screen;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Агрегация спецификации ОДНОЙ СЦЕНЫ по её схемам — без POI, чтобы считать и
 * тестировать отдельно от {@code ui.stage.OutputStagePanel} (там остаётся только
 * раскладка готовых строк по листам .xlsx).
 *
 * <p>Запрос пользователя 2026-09-30 (docs/masks-and-schema-sheets/PLAN.md, пункт 8,
 * решения D1 и D6): на сцене может быть несколько блок-схем питания и сигнала
 * ({@link SchemaSheet}), а спецификация раньше была одним файлом на ВЕСЬ проект и
 * складывала все узлы/связи всех сцен в одну колонку «Кол-во». Теперь:
 * <ul>
 * <li>(D6) спецификация строится на каждую сцену отдельно — сюда вообще не попадают
 * экраны, узлы и связи других сцен (вызывающая сторона передаёт одну сцену);</li>
 * <li>(D1) внутри сцены КАЖДАЯ схема считается отдельно: одна и та же позиция в
 * разных схемах пишется в одну строку, а числа — в разные колонки, подписанные
 * названиями схем. Какие блоки разных схем «общие» (один и тот же физический щит
 * нарисован дважды) узнать нельзя, поэтому колонки «Итого» нет намеренно — сумма
 * вводила бы в заблуждение.</li>
 * </ul>
 *
 * <p>Все результаты индексируются порядком {@link #columns}: сначала схемы
 * питания, затем сигнала, внутри режима — по {@link SchemaSheet#getOrderIndex()}.
 * Массив {@code counts[i]} строки — число для {@code i}-й колонки; {@code 0}
 * означает «позиции нет в этой схеме» (ячейка остаётся пустой — настоящих нулевых
 * количеств в спецификации не бывает).
 */
public final class SceneSpecCalc {

    private SceneSpecCalc() {
    }

    /** Содержимое одной схемы сцены: узлы и связи листа в порядке колонок. */
    public record SheetData(String sheetId, SchemaMode mode, String name, List<SchemaNode> nodes,
            List<SchemaEdge> edges) {
    }

    /** Колонка спецификации — по одной на схему сцены. {@code title} — заголовок с
     *  уже разведёнными совпадениями имён (см. {@link #columns}). */
    public record Column(String sheetId, SchemaMode mode, String title) {
    }

    /** Строка оборудования: ключ (режим, тип узла, подпись) + количество по колонкам. */
    public record EquipmentRow(String modeLabel, String typeLabel, String label, int[] counts) {
    }

    /** Строка листа «Коммутация — сводная»: {@code lengthM == null} — куска нет (не
     *  зарегистрированный тип провода или пустой каталог), {@code note} — пояснение. */
    public record WirePurchaseRow(String modeLabel, String wireType, Double lengthM, int[] counts, String note) {
    }

    /** Строка листа «Коммутация — сплайсовка»: {@code lineCounts} линий требуемой длины
     *  {@code rawLengthM}, собранных комплектом {@code kit}, по колонкам. */
    public record WireSpliceRow(String modeLabel, String wireType, double rawLengthM, int[] lineCounts, String kit) {
    }

    /** Итог по типу провода для листа «Общий список»: число линий и их суммарный метраж
     *  по колонкам; {@code unit} — готовая подпись единицы. */
    public record WireTotalRow(String modeLabel, String wireType, int[] lineCounts, double[] lengthsM, String unit) {
    }

    /** Результат разбора связей: оба листа коммутации и число связей без
     *  структурированной подписи (не учтены в подсчёте). */
    public record Wiring(List<WirePurchaseRow> purchase, List<WireSpliceRow> splices, int uncountedEdges) {
    }

    /** Подпись режима в первой колонке листов спецификации. */
    public static String modeLabel(SchemaMode mode) {
        return mode == SchemaMode.POWER ? "Питание" : "Сигнал";
    }

    // ---- сбор данных сцены ----

    /** Схемы сцены с их узлами и связями в порядке колонок (питание, затем сигнал;
     *  внутри режима по порядку листов). Только ЭТА сцена — спецификация сцены не
     *  видит узлы/связи других сцен (решение D6). Каждая схема содержит узлы только
     *  своего листа (в разных схемах одни и те же id гнёзд не пересекаются, см.
     *  {@link AppModel#schemaNodesOfSheet}). */
    public static List<SheetData> sheetsOf(AppModel model, Scene scene) {
        List<SheetData> result = new ArrayList<>();
        for (SchemaMode mode : new SchemaMode[]{SchemaMode.POWER, SchemaMode.SIGNAL}) {
            for (SchemaSheet sheet : model.schemaSheets(scene, mode)) {
                result.add(new SheetData(sheet.getId(), mode, sheet.getName(),
                        model.schemaNodesOfSheet(scene, sheet.getId()),
                        model.schemaEdgesOfSheet(scene, sheet.getId())));
            }
        }
        return result;
    }

    /** Кабинеты СЦЕНЫ по эффективному типу (тип экрана или переопределение кабинета),
     *  скрытые не считаются — раньше это был цикл по всем сценам проекта в
     *  {@code OutputStagePanel}. */
    public static Map<CabinetType, Integer> cabinetCounts(AppModel model, Scene scene) {
        Map<CabinetType, Integer> cabinets = new LinkedHashMap<>();
        for (Screen scr : scene.getScreens()) {
            CabinetType defaultType = model.typeOf(scr);
            for (CabinetInstance c : scr.getCabinets()) {
                if (c.isHidden()) {
                    continue;
                }
                CabinetType effective = defaultType;
                if (c.getCabinetTypeId() != null) {
                    CabinetType override = model.getWorkspace().cabinetTypeById(c.getCabinetTypeId());
                    if (override != null) {
                        effective = override;
                    }
                }
                if (effective != null) {
                    cabinets.merge(effective, 1, Integer::sum);
                }
            }
        }
        return cabinets;
    }

    // ---- колонки ----

    /** Колонки по схемам в порядке {@code sheets}. Если имя схемы питания совпало с
     *  именем схемы сигнала (без учёта регистра и пробелов по краям), обоим
     *  заголовкам добавляется « (питание)»/« (сигнал)» — иначе в таблице две
     *  одинаковые колонки и не понять, где что. Пустое имя заменяется на «Схема
     *  питания»/«Схема сигнала». */
    public static List<Column> columns(List<SheetData> sheets) {
        List<String> base = new ArrayList<>();
        for (SheetData s : sheets) {
            String name = s.name() == null ? "" : s.name().trim();
            base.add(name.isEmpty() ? (s.mode() == SchemaMode.POWER ? "Схема питания" : "Схема сигнала") : name);
        }
        List<Column> result = new ArrayList<>();
        for (int i = 0; i < sheets.size(); i++) {
            SheetData s = sheets.get(i);
            String title = base.get(i);
            boolean clash = false;
            for (int j = 0; j < sheets.size(); j++) {
                if (j != i && sheets.get(j).mode() != s.mode()
                        && base.get(j).toLowerCase(Locale.ROOT).equals(title.toLowerCase(Locale.ROOT))) {
                    clash = true;
                    break;
                }
            }
            if (clash) {
                title += s.mode() == SchemaMode.POWER ? " (питание)" : " (сигнал)";
            }
            result.add(new Column(s.sheetId(), s.mode(), title));
        }
        return result;
    }

    // ---- оборудование ----

    /** Оборудование схем: группировка по (режим, тип узла, подпись) — одинаково
     *  подписанные узлы одного типа считаются одной моделью. Узлы-экраны не считаются
     *  (это ссылка на уже посчитанный экран), авто-блок «Легенда портов» тоже (баг-
     *  репорт 2026-09-16: справочная таблица, не оборудование). Одна и та же позиция
     *  разных схем — одна строка, числа в разных колонках. */
    public static List<EquipmentRow> equipment(List<SheetData> sheets,
            Function<SchemaNodeType, String> categoryLabel) {
        Map<List<String>, int[]> rows = new LinkedHashMap<>();
        for (int col = 0; col < sheets.size(); col++) {
            SheetData sheet = sheets.get(col);
            for (SchemaNode n : sheet.nodes()) {
                if (n.getType() == SchemaNodeType.SCREEN || n.isAutoPortLegend()) {
                    continue;
                }
                String typeLabel = categoryLabel.apply(n.getType());
                String label = n.getLabel() == null || n.getLabel().isBlank() ? typeLabel : n.getLabel();
                rows.computeIfAbsent(List.of(modeLabel(sheet.mode()), typeLabel, label),
                        k -> new int[sheets.size()])[col]++;
            }
        }
        List<EquipmentRow> result = new ArrayList<>();
        for (var e : rows.entrySet()) {
            result.add(new EquipmentRow(e.getKey().get(0), e.getKey().get(1), e.getKey().get(2), e.getValue()));
        }
        return result;
    }

    // ---- коммутация ----

    /** Листы «Коммутация — сводная»/«сплайсовка» по схемам. Комплектация кусками
     *  кабеля ({@link CableSpecCalc}) выполняется для КАЖДОЙ схемы отдельно (решение
     *  D1: потребность двух схем не складывается в общий комплект), одинаковые
     *  позиции схем объединяются в строки.
     *
     *  @param profileOf  каталог длин по типу провода ({@code null} — не заведён)
     *  @param adapterFixedLengthOf  фиксированная длина зарегистрированного
     *         переходника по типу провода ({@code null} — не переходник) */
    public static Wiring wiring(List<SheetData> sheets, Function<String, CableLengthProfile> profileOf,
            Function<String, Double> adapterFixedLengthOf) {
        List<Column> columns = columns(sheets);
        int n = sheets.size();
        int uncounted = 0;
        // (режим -> тип провода -> [линии по колонкам])
        Map<SchemaMode, Map<String, List<List<double[]>>>> byMode = new LinkedHashMap<>();
        for (SchemaMode mode : new SchemaMode[]{SchemaMode.POWER, SchemaMode.SIGNAL}) {
            byMode.put(mode, new LinkedHashMap<>());
        }
        for (int col = 0; col < n; col++) {
            SheetData sheet = sheets.get(col);
            for (SchemaEdge edge : sheet.edges()) {
                if (!edge.hasStructuredWire()) {
                    uncounted++;
                    continue;
                }
                List<List<double[]>> perColumn = byMode.get(sheet.mode()).computeIfAbsent(edge.getWireType(), k -> {
                    List<List<double[]>> l = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        l.add(new ArrayList<>());
                    }
                    return l;
                });
                perColumn.get(col).add(new double[]{edge.getLengthM() != null ? edge.getLengthM() : 0,
                        edge.getWireCount()});
            }
        }

        List<WirePurchaseRow> purchase = new ArrayList<>();
        List<WireSpliceRow> splices = new ArrayList<>();
        for (var modeEntry : byMode.entrySet()) {
            String modeLabel = modeLabel(modeEntry.getKey());
            for (var typeEntry : modeEntry.getValue().entrySet()) {
                String wireType = typeEntry.getKey();
                List<List<double[]>> perColumn = typeEntry.getValue();
                CableLengthProfile profile = profileOf.apply(wireType);
                Double fixed = profile == null ? adapterFixedLengthOf.apply(wireType) : null;

                // Строки этого типа провода — в порядке первого появления; ключ строки —
                // вид позиции (+ длина куска), чтобы одинаковые позиции схем сливались.
                Map<String, WirePurchaseRow> rows = new LinkedHashMap<>();
                Map<String, WireSpliceRow> spliceRows = new LinkedHashMap<>();
                double[] freeLengths = new double[n];
                for (int col = 0; col < n; col++) {
                    List<double[]> lines = perColumn.get(col);
                    if (lines.isEmpty()) {
                        continue;
                    }
                    int totalCount = 0;
                    double totalLength = 0;
                    for (double[] l : lines) {
                        totalCount += (int) Math.round(l[1]);
                        totalLength += l[0] * l[1];
                    }
                    if (profile == null) {
                        if (fixed != null) {
                            addPurchase(rows, "fixed:" + fixed, modeLabel, wireType, fixed, n, col, totalCount,
                                    "переходник, фиксированная длина");
                        } else {
                            addPurchase(rows, "free", modeLabel, wireType, null, n, col, totalCount, null);
                            freeLengths[col] = totalLength;
                        }
                        continue;
                    }
                    CableSpecCalc.Breakdown breakdown = CableSpecCalc.breakdown(lines, profile);
                    for (var byLen : breakdown.countByRoundedLengthM().entrySet()) {
                        addPurchase(rows, "len:" + byLen.getKey(), modeLabel, wireType, byLen.getKey(), n, col,
                                byLen.getValue(), null);
                    }
                    if (breakdown.uncoveredCount() > 0) {
                        addPurchase(rows, "uncovered", modeLabel, wireType, null, n, col,
                                breakdown.uncoveredCount(), "каталог длин пуст — докупите бухты вручную");
                    }
                    for (CableSpecCalc.SpliceInfo splice : breakdown.spliced()) {
                        StringBuilder kit = new StringBuilder();
                        for (CableSpecCalc.Piece piece : splice.pieces()) {
                            if (kit.length() > 0) {
                                kit.append(" + ");
                            }
                            kit.append(piece.count()).append('×').append(fmt(piece.lengthM())).append(" м");
                        }
                        String key = splice.rawLengthM() + "|" + kit;
                        spliceRows.computeIfAbsent(key,
                                k -> new WireSpliceRow(modeLabel, wireType, splice.rawLengthM(), new int[n],
                                        kit.toString())).lineCounts()[col] += splice.lineCount();
                    }
                }
                WirePurchaseRow free = rows.get("free");
                if (free != null) {
                    rows.put("free", new WirePurchaseRow(free.modeLabel(), free.wireType(), null, free.counts(),
                            freeNote(columns, freeLengths)));
                }
                purchase.addAll(rows.values());
                splices.addAll(spliceRows.values());
            }
        }
        return new Wiring(purchase, splices, uncounted);
    }

    private static void addPurchase(Map<String, WirePurchaseRow> rows, String key, String modeLabel, String wireType,
            Double lengthM, int n, int col, int count, String note) {
        rows.computeIfAbsent(key, k -> new WirePurchaseRow(modeLabel, wireType, lengthM, new int[n], note))
                .counts()[col] += count;
    }

    /** Пояснение для типа провода, не заведённого в библиотеку: суммарный метраж —
     *  одним числом, если длина задана только в одной схеме, иначе по схемам. */
    private static String freeNote(List<Column> columns, double[] lengths) {
        List<String> parts = new ArrayList<>();
        double sum = 0;
        for (int i = 0; i < lengths.length; i++) {
            if (lengths[i] > 0) {
                parts.add(columns.get(i).title() + " — " + fmt(lengths[i]) + " м");
                sum += lengths[i];
            }
        }
        if (parts.size() <= 1) {
            return "не зарегистрирован в библиотеке — суммарно " + fmt(sum) + " м";
        }
        return "не зарегистрирован в библиотеке — суммарно: " + String.join("; ", parts);
    }

    /** Строки «Коммутация» листа «Общий список»: по типу провода число линий и метраж
     *  по схемам (раньше одна сумма на проект). {@code unit} — «шт линий (~X м
     *  суммарно)»; если провод есть в нескольких схемах — метраж по схемам. */
    public static List<WireTotalRow> wireTotals(List<SheetData> sheets) {
        List<Column> columns = columns(sheets);
        int n = sheets.size();
        Map<String, WireTotalRow> rows = new LinkedHashMap<>();
        for (SchemaMode mode : new SchemaMode[]{SchemaMode.POWER, SchemaMode.SIGNAL}) {
            for (int col = 0; col < n; col++) {
                SheetData sheet = sheets.get(col);
                if (sheet.mode() != mode) {
                    continue;
                }
                for (SchemaEdge edge : sheet.edges()) {
                    if (!edge.hasStructuredWire()) {
                        continue;
                    }
                    WireTotalRow row = rows.computeIfAbsent(modeLabel(mode) + ": " + edge.getWireType(),
                            k -> new WireTotalRow(modeLabel(mode), edge.getWireType(), new int[n], new double[n], null));
                    row.lineCounts()[col] += edge.getWireCount();
                    row.lengthsM()[col] += (edge.getLengthM() != null ? edge.getLengthM() : 0) * edge.getWireCount();
                }
            }
        }
        List<WireTotalRow> result = new ArrayList<>();
        for (WireTotalRow row : rows.values()) {
            List<String> parts = new ArrayList<>();
            double sum = 0;
            for (int i = 0; i < n; i++) {
                if (row.lineCounts()[i] > 0) {
                    parts.add(columns.get(i).title() + " — " + fmt(row.lengthsM()[i]) + " м");
                    sum += row.lengthsM()[i];
                }
            }
            String unit = parts.size() <= 1 ? "шт линий (~" + fmt(sum) + " м суммарно)"
                    : "шт линий (~" + String.join("; ", parts) + ")";
            result.add(new WireTotalRow(row.modeLabel(), row.wireType(), row.lineCounts(), row.lengthsM(), unit));
        }
        return result;
    }

    /** Число без хвоста «.0», как {@code UiKit.fmt} (сервис не зависит от UI). */
    static String fmt(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.format("%.1f", v);
    }

    /** Значение ячейки по колонке: {@code 0} -> {@code null} (пустая ячейка). */
    public static Integer cell(int[] counts, int col) {
        return counts[col] == 0 ? null : counts[col];
    }

    /** Все значения строки как ячейки, пустые вместо нулей. */
    public static List<Object> cells(int[] counts) {
        List<Object> out = new ArrayList<>(counts.length);
        for (int i = 0; i < counts.length; i++) {
            out.add(cell(counts, i));
        }
        return out;
    }
}
