package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link UiKit#fmtPower} — единая точка форматирования мощности/нагрузки в Вт/кВт
 *  (баг-репорт/фича: переключатель единиц в «Персонализация → Предпочтения» должен
 *  действовать одинаково везде, см. STRUCTURE_CALC_NOTES.md-style hedge о том, что
 *  внутреннее хранение остаётся в ваттах). Дробная часть сравнивается через
 *  {@code String.format} с теми же спецификаторами, что и сама реализация, а не
 *  захардкоженной точкой -- {@code String.format("%.1f", ...)} без явной {@code Locale}
 *  зависит от локали по умолчанию (запятая в ru_RU), это уже так у {@link UiKit#fmt}
 *  и не предмет этого теста. */
class UiKitTest {

    @Test
    void fmtPowerWatts_usesPlainFmtWithSuffix() {
        assertEquals("1234 Вт", UiKit.fmtPower(1234.0, false));
        assertEquals(String.format("%.1f", 1234.5) + " Вт", UiKit.fmtPower(1234.5, false));
    }

    @Test
    void fmtPowerKilowatts_scalesAndRoundsToTwoDecimals() {
        assertEquals(String.format("%.2f", 1.234) + " кВт", UiKit.fmtPower(1234.0, true));
        assertEquals("2 кВт", UiKit.fmtPower(2000.0, true), "целое число кВт -- без лишних нулей после запятой");
        assertEquals(String.format("%.2f", 0.5) + " кВт", UiKit.fmtPower(500.0, true));
    }

    @Test
    void fmtPowerZero_bothUnits() {
        assertEquals("0 Вт", UiKit.fmtPower(0.0, false));
        assertEquals("0 кВт", UiKit.fmtPower(0.0, true));
    }
}
