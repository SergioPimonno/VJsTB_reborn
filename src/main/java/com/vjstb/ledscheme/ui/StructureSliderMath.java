package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.service.StructureCurveMath;

/**
 * Маппинг «положение ползунка ↔ параметр экрана» для ползунков радиуса, зазора и числа башен
 * в окне 3D-редактора конструктива (запрос пользователя 2026-10-01: «давай параметры радиуса,
 * зазора и количества башен продублируем в 3д редакторе и сделаем их как в виджете ползунками,
 * это очень удобно»). Без Swing/GL — чтобы границы, шаги и зажим зазора были покрыты тестами
 * ({@code StructureSliderMathTest}), а {@link Structure3DControlsPanel} только раскладывал
 * компоненты.
 *
 * <p>JSlider целочисленный, поэтому каждый ползунок — номер шага («тик»):
 * <ul>
 *   <li>радиус — 2,5…30 м с шагом 0,5 м (тик 0 = 2,5 м); значение экрана вне диапазона
 *       показывается подписью как есть, ползунок встаёт на ближайший край;</li>
 *   <li>зазор — 0…5 м с шагом 500 мм; у изогнутого экрана минимум 500 мм (тик 1, правило
 *       пользователя {@link StructureCurveMath#MIN_TOWER_GAP_MM}), у прямого 0 = башни вплотную
 *       стеной (прежнее поведение);</li>
 *   <li>число башен — 0 = авто, максимум — вдвое больше авто-подбора, но не меньше
 *       {@link #TOWER_COUNT_MIN_MAX} (и не больше 200 — как у поля «Число башен» в «Сетапе»).</li>
 * </ul>
 */
public final class StructureSliderMath {

    public static final double RADIUS_MIN_MM = 2_500;
    public static final double RADIUS_MAX_MM = 30_000;
    public static final double RADIUS_STEP_MM = 500;
    public static final double GAP_STEP_MM = 500;
    public static final double GAP_MAX_MM = 5_000;
    /** Нижняя граница максимума ползунка числа башен — чтобы у короткого экрана было куда
     *  двигать (авто = 1–3 башни). */
    public static final int TOWER_COUNT_MIN_MAX = 12;
    /** Как у спиннера «Число башен (0 — авто)» в {@code SetupStagePanel}. */
    public static final int TOWER_COUNT_HARD_MAX = 200;

    private StructureSliderMath() {
    }

    public static int radiusTickCount() {
        return (int) Math.round((RADIUS_MAX_MM - RADIUS_MIN_MM) / RADIUS_STEP_MM);
    }

    /** Ближайший тик для радиуса (мм), зажат в диапазон ползунка. */
    public static int radiusToTick(double radiusMm) {
        if (!(radiusMm > 0) || Double.isNaN(radiusMm)) {
            return 0;
        }
        long t = Math.round((radiusMm - RADIUS_MIN_MM) / RADIUS_STEP_MM);
        return (int) Math.max(0, Math.min(radiusTickCount(), t));
    }

    public static double tickToRadiusMm(int tick) {
        return RADIUS_MIN_MM + Math.max(0, Math.min(radiusTickCount(), tick)) * RADIUS_STEP_MM;
    }

    public static int gapTickCount() {
        return (int) Math.round(GAP_MAX_MM / GAP_STEP_MM);
    }

    /** Минимальный тик зазора: у изогнутого экрана 500 мм (тик 1), у прямого 0 (стена). */
    public static int minGapTick(ScreenCurveType type) {
        return type != null && type.isCurved() ? (int) Math.round(StructureCurveMath.MIN_TOWER_GAP_MM / GAP_STEP_MM) : 0;
    }

    public static int gapToTick(double gapMm) {
        if (!(gapMm > 0)) {
            return 0;
        }
        long t = Math.round(gapMm / GAP_STEP_MM);
        return (int) Math.max(0, Math.min(gapTickCount(), t));
    }

    /** Зазор (мм) по тику — с тем же зажимом, что «Предварительный расчёт»
     *  ({@link StructureCurveMath#effectiveGapMm}): у изогнутого не меньше 500, у прямого 0 —
     *  стена. */
    public static double tickToGapMm(int tick, ScreenCurveType type) {
        int t = Math.max(minGapTick(type), Math.min(gapTickCount(), tick));
        return clampGapMm(type, t * GAP_STEP_MM);
    }

    /** Зажим зазора: изогнутый — не меньше 500 мм; прямой — 0 (стена) или не меньше 500. */
    public static double clampGapMm(ScreenCurveType type, double gapMm) {
        return StructureCurveMath.effectiveGapMm(type, Math.max(0, gapMm));
    }

    /** Максимум ползунка числа башен при текущем авто-подборе. */
    public static int maxTowerCount(int autoCount) {
        return Math.min(TOWER_COUNT_HARD_MAX, Math.max(TOWER_COUNT_MIN_MAX, 2 * Math.max(0, autoCount)));
    }

    public static int clampTowerCount(int count, int max) {
        return Math.max(0, Math.min(max, count));
    }

    /** Подпись ползунка радиуса: радиус и производные — угол между кабинетами, угол дуги. Если
     *  изгиб у экрана задан углом — угол первым (как в поле «Сетапа»). {@code curved == false}
     *  — прямой экран, ползунок неактивен. */
    public static String radiusLabel(boolean curved, boolean byAngle, double radiusMm, double cabinetAngleDeg,
            double arcAngleDeg) {
        if (!curved) {
            return "Радиус: прямой экран";
        }
        String angle = Double.isNaN(cabinetAngleDeg) ? "—" : String.format("%.2f°", cabinetAngleDeg);
        String arc = Double.isNaN(arcAngleDeg) ? "—" : String.format("%.1f°", arcAngleDeg);
        if (byAngle) {
            return String.format("Угол %s между кабинетами · R %.1f м · дуга %s", angle, radiusMm / 1000.0, arc);
        }
        return String.format("Радиус %.1f м · %s между кабинетами · дуга %s", radiusMm / 1000.0, angle, arc);
    }

    public static String gapLabel(boolean separate, double gapMm) {
        if (!separate) {
            return "Зазор: 0 — стена (вплотную)";
        }
        return String.format("Зазор между башнями %.0f мм", gapMm);
    }

    /** {@code towers} — сколько башен реально стоит (у стены — число столбов). */
    public static String towerLabel(boolean separate, int userCount, int towers) {
        if (!separate) {
            return String.format("Башен: стена, %d столбов", towers);
        }
        return userCount > 0 ? String.format("Башен: %d", userCount) : String.format("Башен: авто (%d)", towers);
    }
}
