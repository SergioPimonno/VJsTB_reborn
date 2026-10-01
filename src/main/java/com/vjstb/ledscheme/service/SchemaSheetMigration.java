package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaSheet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Инвариант листов общей схемы (запрос пользователя 2026-09-30, «несколько
 * блок-схем на сцену», docs/masks-and-schema-sheets/PLAN.md, трек C1): у каждой
 * сцены на КАЖДЫЙ режим ({@link SchemaMode}) есть хотя бы один лист; каждый узел и
 * каждая связь ссылаются на существующий лист СВОЕГО режима.
 *
 * <p>Нарушается инвариант в двух случаях, и оба — не ошибка пользователя:
 * проект сохранён до появления листов (узлы/связи без {@code sheetId}, листов нет
 * вовсе) или новый проект пересохранил старый клиент (он не знает полей {@code
 * schemaSheets}/{@code sheetId} и молча их теряет — в {@code ObjectMapper}'ах
 * выключен {@code FAIL_ON_UNKNOWN_PROPERTIES}). В обоих случаях всё содержимое
 * режима уходит в ПЕРВЫЙ лист режима — старый проект открывается ровно как раньше,
 * «одна схема питания + одна схема сигнала».
 *
 * <p>Идемпотентно и без undo/сохранения — часть загрузки, а не действие
 * пользователя (по образцу {@code AppModel#seedScreenMaskColorsFromLegacyPlacements}).
 * Вызывается при загрузке рабочего пространства, при появлении проекта/сцены в
 * рабочем списке (импорт, архив, облако, новая сцена) и лениво из аксессоров
 * листов {@code AppModel} — чтобы сцена, собранная в обход этих путей (тесты,
 * фикстуры), тоже всегда была согласована.
 */
public final class SchemaSheetMigration {

    /** Имя листа питания, заводимого миграцией/для новой сцены. */
    public static final String DEFAULT_POWER_SHEET_NAME = "Схема питания";
    /** Имя листа сигнала, заводимого миграцией/для новой сцены. */
    public static final String DEFAULT_SIGNAL_SHEET_NAME = "Схема сигнала";

    private SchemaSheetMigration() {
    }

    public static String defaultSheetName(SchemaMode mode) {
        return mode == SchemaMode.SIGNAL ? DEFAULT_SIGNAL_SHEET_NAME : DEFAULT_POWER_SHEET_NAME;
    }

    /** Приводит сцену к инварианту (см. class-javadoc). @return true — что-то
     *  пришлось поправить (завести лист или перепривязать узел/связь). */
    public static boolean ensure(Scene scene) {
        if (scene == null) {
            return false;
        }
        boolean changed = false;
        for (SchemaMode mode : SchemaMode.values()) {
            if (sheetsOf(scene, mode).isEmpty()) {
                scene.getSchemaSheets().add(new SchemaSheet(mode, defaultSheetName(mode), 0));
                changed = true;
            }
        }
        for (SchemaNode n : scene.getSchemaNodes()) {
            SchemaMode mode = n.getMode() != null ? n.getMode() : SchemaMode.POWER;
            if (!isSheetOfMode(scene, n.getSheetId(), mode)) {
                n.setSheetId(firstSheet(scene, mode).getId());
                changed = true;
            }
        }
        for (SchemaEdge e : scene.getSchemaEdges()) {
            SchemaMode mode = e.getMode() != null ? e.getMode() : SchemaMode.POWER;
            if (!isSheetOfMode(scene, e.getSheetId(), mode)) {
                e.setSheetId(firstSheet(scene, mode).getId());
                changed = true;
            }
        }
        return changed;
    }

    /** Листы режима сцены по {@link SchemaSheet#getOrderIndex()} (при равенстве — по
     *  порядку в списке сцены). Инвариант НЕ наводит — может вернуть пустой список. */
    public static List<SchemaSheet> sheetsOf(Scene scene, SchemaMode mode) {
        List<SchemaSheet> out = new ArrayList<>();
        if (scene == null) {
            return out;
        }
        for (SchemaSheet s : scene.getSchemaSheets()) {
            if (s.getMode() == mode) {
                out.add(s);
            }
        }
        out.sort(Comparator.comparingInt(SchemaSheet::getOrderIndex));
        return out;
    }

    /** Первый лист режима — туда уходит «ничейное» содержимое и пишет
     *  автозаполнение (решение D2). Наводит инвариант, поэтому не {@code null}
     *  для не-{@code null} сцены. */
    public static SchemaSheet firstSheet(Scene scene, SchemaMode mode) {
        List<SchemaSheet> sheets = sheetsOf(scene, mode);
        if (sheets.isEmpty()) {
            ensure(scene);
            sheets = sheetsOf(scene, mode);
        }
        return sheets.isEmpty() ? null : sheets.get(0);
    }

    /** Лист сцены с таким id или {@code null}. */
    public static SchemaSheet sheetById(Scene scene, String sheetId) {
        if (scene == null || sheetId == null) {
            return null;
        }
        for (SchemaSheet s : scene.getSchemaSheets()) {
            if (sheetId.equals(s.getId())) {
                return s;
            }
        }
        return null;
    }

    /** Перенумеровывает {@code orderIndex} листов режима подряд с 0 в их текущем
     *  порядке — после удаления/вставки/перемещения листа. */
    static void renumber(Scene scene, SchemaMode mode) {
        List<SchemaSheet> sheets = sheetsOf(scene, mode);
        for (int i = 0; i < sheets.size(); i++) {
            sheets.get(i).setOrderIndex(i);
        }
    }

    private static boolean isSheetOfMode(Scene scene, String sheetId, SchemaMode mode) {
        SchemaSheet s = sheetById(scene, sheetId);
        return s != null && s.getMode() == mode;
    }
}
