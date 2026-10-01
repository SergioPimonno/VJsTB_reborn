package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.StructureCalc;
import com.vjstb.ledscheme.service.StructureCurveMath;
import java.util.List;

/**
 * Пересчёт конструктива по ползункам 3D-редактора (запрос пользователя 2026-10-01, см.
 * {@link StructureSliderMath}) — без Swing/GL, чтобы коалесцирование отмены было покрыто тестом.
 *
 * <p><b>Тот же путь, что «Предварительный расчёт»</b> ({@code SetupStagePanel
 * #calculateStructure}): форма/радиус/зазор/число башен берутся из ползунков, всё остальное — из
 * ТЕКУЩЕГО экрана (высота башни, вынос, коэффициент балласта, рама/стакан/контейнер, подъём
 * экрана, заметки); число сегментов переднего/заднего ряда и уровней перемычек выводятся теми же
 * формулами {@code StructureCalc.suggest*} от высоты башни и рамы, что у кнопки. Для экрана, у
 * которого конструктив ещё не считался, высота башни — авто (высота экрана), как её показывает
 * поле «Сетапа». Сохраняет {@link AppModel#updateScreenStructureLive}.
 *
 * <p><b>Одна запись отмены на жест</b>: первое событие жеста (ползунок начали тянуть, либо
 * одиночный щелчок/клавиша) кладёт снимок «до» в стек отмены, промежуточные шаги
 * перетаскивания пересчитывают модель без новых записей (и без записи файла), отпускание
 * ({@code adjusting == false}) завершает жест и сохраняет. Ctrl+Z после жеста возвращает
 * состояние до его начала целиком. Если посреди жеста сменился текущий экран — новый жест.
 */
public final class StructureSliderController {

    private final AppModel model;
    private Screen gestureScreen;

    public StructureSliderController(AppModel model) {
        this.model = model;
    }

    /** Идёт ли сейчас незавершённый жест (ползунок держат мышью). */
    public boolean inGesture() {
        return gestureScreen != null;
    }

    /** Ползунки применимы: текущий экран — наземный конструктив. */
    public boolean applicable() {
        Screen s = model.getCurrentScreen();
        return s != null && s.getMountType() == ScreenMountType.STRUCTURE;
    }

    /** Пересчитывает конструктив текущего экрана с новыми формой/радиусом/зазором/числом башен.
     *  {@code adjusting} — ползунок ещё держат (промежуточный шаг жеста). Зазор зажимается по
     *  правилу {@link StructureSliderMath#clampGapMm}, число башен — не меньше 0.
     *
     * @return {@code false}, если текущий экран не наземный конструктив (ничего не изменено). */
    public boolean apply(ScreenCurveType type, double radiusMm, double gapMm, int separateTowerCount,
            boolean adjusting) {
        Screen s = model.getCurrentScreen();
        if (s == null || s.getMountType() != ScreenMountType.STRUCTURE) {
            gestureScreen = null;
            return false;
        }
        ScreenCurveType curve = type != null ? type : ScreenCurveType.FLAT;
        boolean start = gestureScreen != s;
        CabinetType cabinetType = model.typeOf(s);
        StructureFrameType frameType = model.getWorkspace().structureFrameTypeById(s.getStructureFrameTypeId());
        double towerHeight = s.getStructureTowerHeightMm();
        if (s.getStructureFrameCells().isEmpty()) {
            double auto = StructureCalc.suggestTowerHeightMm(s, cabinetType);
            if (auto > 0) {
                towerHeight = auto;
            }
        }
        Screen draft = s.copy();
        draft.setStructureTowerHeightMm(towerHeight);
        int vertical = StructureCalc.suggestVerticalFramesPerTower(draft, frameType);
        double frameH = frameType != null && frameType.getHeightMm() != null && frameType.getHeightMm() > 0
                ? frameType.getHeightMm() : 950.0;
        int back = StructureCalc.suggestBackRowSegments(frameH);
        int levels = StructureCalc.suggestPeremychkaLevels(back * frameH, frameH);
        double gap = StructureSliderMath.clampGapMm(curve, gapMm);
        double radius = radiusMm > 0 ? radiusMm : s.getStructureCurveRadiusMm();
        model.updateScreenStructureLive(s, start, !adjusting, towerHeight, StructureCalc.suggestTowerCount(s, cabinetType),
                vertical, back, levels, s.getStructureBaseExtensionMm(), s.getStructureBallastRatio(),
                s.getStructureFrameTypeId(), s.getStructureCupTypeId(), s.getStructureBallastTypeId(),
                s.getStructureScreenElevationMm(), s.getStructureNotes(), curve, radius, s.isStructureCurveByAngle(),
                gap, Math.max(0, separateTowerCount));
        gestureScreen = adjusting ? s : null;
        return true;
    }

    /** Авто-число башен для текущих формы/радиуса/зазора экрана (без учёта числа пользователя)
     *  — для максимума ползунка и подписи «авто (N)». 0 — экран не в раздельном режиме. */
    public int autoTowerCount() {
        Screen s = model.getCurrentScreen();
        if (s == null || !StructureCurveMath.separateTowers(s)) {
            return 0;
        }
        StructureCurveMath.Curve curve = StructureCurveMath.curveOf(s, model.typeOf(s));
        StructureCurveMath.TowerSpec spec = StructureCurveMath.towerSpecOf(s, model.getWorkspace());
        double gap = StructureCurveMath.effectiveGapMm(s.getStructureCurveType(), s.getStructureTowerGapMm());
        return StructureCurveMath.suggestTowerCount(curve, spec, gap);
    }

    /** Предупреждения раздельных башен текущего экрана (коллизии оснований и минимальный
     *  зазор, край экрана, радиус) — те же, что «Предварительный расчёт» выводит в итоговом
     *  сообщении ({@link StructureCalc.Result#curveWarnings()}). Пусто у стены. */
    public List<String> warnings() {
        Screen s = model.getCurrentScreen();
        if (s == null || s.getMountType() != ScreenMountType.STRUCTURE) {
            return List.of();
        }
        return StructureCalc.compute(s, model.typeOf(s), model.getWorkspace()).curveWarnings();
    }
}
