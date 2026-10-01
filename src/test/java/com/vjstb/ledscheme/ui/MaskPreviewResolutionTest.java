package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.settings.UserProfile;
import org.junit.jupiter.api.Test;

/** Запрос пользователя 2026-09-30 (решение D7: «уменьшенное превью», пресеты «как в After
 *  Effects»): после снятия предела 16k превью маски размещения на холсте больше не держится в
 *  полном разрешении — масштаб выбирает чистая функция {@link MaskPreviewResolution#choose},
 *  которая при превышении бюджета памяти (~64 Мпикс на размещение) берёт следующую меньшую
 *  дробь. Полное 30000×30000 в превью не должно появляться никогда. */
class MaskPreviewResolutionTest {

    @Test
    void fractionPresetsRenderAtTheirFractionWhenWithinBudget() {
        MaskPreviewResolution.Choice half = MaskPreviewResolution.choose(MaskPreviewResolution.HALF, 0.1, 4000, 2000);
        assertEquals(0.5, half.scale());
        assertEquals(2, half.divisor());
        assertFalse(half.downgraded());
        assertNull(half.note());

        assertEquals(1.0, MaskPreviewResolution.choose(MaskPreviewResolution.FULL, 0.1, 1920, 1080).scale());
        assertEquals(1.0 / 8, MaskPreviewResolution.choose(MaskPreviewResolution.EIGHTH, 0.1, 1920, 1080).scale());
    }

    @Test
    void autoRendersAtDisplayScaleButNeverAboveNative() {
        MaskPreviewResolution.Choice auto = MaskPreviewResolution.choose(MaskPreviewResolution.AUTO, 0.137, 30000, 30000);
        assertEquals(0.137, auto.scale(), 1e-12);
        assertFalse(auto.downgraded());
        assertEquals(1.0, MaskPreviewResolution.choose(MaskPreviewResolution.AUTO, 3.0, 640, 480).scale(),
                "крупнее нативного «Авто» не рисует, как в AE");
    }

    @Test
    void fullOf30000SquareIsDowngradedToAQuarter() {
        // 30000² = 900 Мпикс; 1/2 → 225; 1/3 → 100; 1/4 → 56,25 ≤ 64 Мпикс.
        MaskPreviewResolution.Choice c = MaskPreviewResolution.choose(MaskPreviewResolution.FULL, 0.05, 30000, 30000);
        assertTrue(c.downgraded());
        assertEquals(4, c.divisor());
        assertEquals(0.25, c.scale());
        assertEquals("превью понижено до 1/4", c.note());
    }

    @Test
    void downgradeContinuesPastEighthForScreensWiderThanTheLimit() {
        // Экран на «Сетапе» не ограничен: 200000×200000 даже в 1/8 — 625 Мпикс.
        MaskPreviewResolution.Choice c = MaskPreviewResolution.choose(MaskPreviewResolution.EIGHTH, 0.01, 200000, 200000);
        assertTrue(c.downgraded());
        assertTrue(c.divisor() > 8);
        long px = (long) MaskImage.scaledSide(200000, c.scale()) * MaskImage.scaledSide(200000, c.scale());
        assertTrue(px <= MaskPreviewResolution.BUDGET_PX);
    }

    @Test
    void budgetIsAParameterOfThePureFunction() {
        MaskPreviewResolution.Choice c = MaskPreviewResolution.choose(MaskPreviewResolution.FULL, 1, 1000, 1000, 300_000);
        // 1000² = 1 М > 300к; 1/2 → 250к ≤ 300к.
        assertEquals(2, c.divisor());
        assertTrue(c.downgraded());
    }

    @Test
    void profileDefaultsToAutoAndUnknownNamesFallBackToAuto() {
        assertEquals("AUTO", new UserProfile().getMaskPreviewResolution());
        assertEquals(MaskPreviewResolution.AUTO, MaskPreviewResolution.fromName(null));
        assertEquals(MaskPreviewResolution.AUTO, MaskPreviewResolution.fromName("что-то"));
        assertEquals(MaskPreviewResolution.THIRD, MaskPreviewResolution.fromName("THIRD"));
        UserProfile p = new UserProfile();
        p.setMaskPreviewResolution("QUARTER");
        assertEquals("QUARTER", p.copy().getMaskPreviewResolution(), "копия профиля сохраняет пресет");
    }
}
