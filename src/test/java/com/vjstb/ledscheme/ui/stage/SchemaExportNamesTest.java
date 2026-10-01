package com.vjstb.ledscheme.ui.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaSheet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Имена файлов экспорта общих схем при нескольких схемах на режим (запрос
 * пользователя 2026-09-30, пункт 8, docs/masks-and-schema-sheets/PLAN.md, трек C2):
 * кнопка «Экспорт схемы…» предлагает «&lt;Сцена&gt; &lt;Схема&gt;», пакет документации пишет
 * КАЖДУЮ схему режима отдельным файлом с её именем. Единственная схема режима
 * называется по-старому («&lt;Сцена&gt; Сила/Сигнал») — имена файлов у тех, кто схем не
 * плодил, не меняются; совпавшие после очистки имена получают числовой суффикс,
 * иначе вторая схема молча перезаписала бы первую.
 */
class SchemaExportNamesTest {

    private static List<SchemaSheet> sheets(SchemaMode mode, String... names) {
        java.util.ArrayList<SchemaSheet> out = new java.util.ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            out.add(new SchemaSheet(mode, names[i], i));
        }
        return out;
    }

    @Test
    void currentSchemaFileNameIsSceneThenSheet() {
        assertEquals("Зал Схема питания", CurrentSchemeExporter.currentSchemaFileName("Зал", "Схема питания"));
        assertEquals("Зал Резерв", CurrentSchemeExporter.currentSchemaFileName(" Зал ", " Резерв "));
    }

    @Test
    void currentSchemaFileNameSurvivesMissingParts() {
        assertEquals("Схема Резерв", CurrentSchemeExporter.currentSchemaFileName(null, "Резерв"));
        assertEquals("Зал", CurrentSchemeExporter.currentSchemaFileName("Зал", null));
    }

    @Test
    void singleSheetKeepsTheLegacyPackageFileName() {
        assertEquals(List.of("Зал Сила"), CurrentSchemeExporter.packageSchemaBaseNames("Зал", SchemaMode.POWER,
                sheets(SchemaMode.POWER, "Схема питания")));
        assertEquals(List.of("Зал Сигнал"), CurrentSchemeExporter.packageSchemaBaseNames("Зал", SchemaMode.SIGNAL,
                sheets(SchemaMode.SIGNAL, "Что угодно")));
    }

    @Test
    void severalSheetsGetOneFileEachNamedAfterTheSheet() {
        assertEquals(List.of("Зал Сила Основная", "Зал Сила Резерв"),
                CurrentSchemeExporter.packageSchemaBaseNames("Зал", SchemaMode.POWER,
                        sheets(SchemaMode.POWER, "Основная", "Резерв")));
    }

    @Test
    void sheetNamesCollidingAfterSanitizingGetNumericSuffix() {
        assertEquals(List.of("Зал Сигнал A_B", "Зал Сигнал A_B 2", "Зал Сигнал a_b 3"),
                CurrentSchemeExporter.packageSchemaBaseNames("Зал", SchemaMode.SIGNAL,
                        sheets(SchemaMode.SIGNAL, "A/B", "A:B", "a|b")),
                "Windows не различает регистр в именах файлов — «a_b» тоже занято");
    }

    @Test
    void sceneNameWithForbiddenCharactersIsSanitized() {
        assertEquals(List.of("Зал_ 1 Сила"), CurrentSchemeExporter.packageSchemaBaseNames("Зал: 1", SchemaMode.POWER,
                sheets(SchemaMode.POWER, "Схема питания")));
    }
}
