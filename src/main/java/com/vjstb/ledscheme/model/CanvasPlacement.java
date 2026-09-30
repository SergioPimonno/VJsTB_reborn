package com.vjstb.ledscheme.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Экран, размещённый внутри канваса (компоновка контента) — позиция в пикселях
 *  канваса, плюс собственная настройка ЭЛЕМЕНТОВ маски этого "грида" (см. class-javadoc
 *  PixelGridRenderer.GridRenderOptions) — набор show* полей ниже. Цвет чек-борда —
 *  см. {@link #background} — 2026-08-13, вернулись к тому, что он снова общий для
 *  экрана (см. {@code Screen#getBackground}), не per-placement: та же запись,
 *  размещённая в двух канвасах, показывала РАЗНЫЙ цвет маски, что оказалось не тем,
 *  что нужно (баг-репорт). Между этим и предыдущим (общий на Screen) заходом был
 *  короткий период per-placement цвета — если видите код/комментарии,
 *  предполагающие цвет на CanvasPlacement, они устарели. */
public class CanvasPlacement {

    private String id = UUID.randomUUID().toString();
    private String screenId;
    private int x;
    private int y;
    /** null — использовать имя экрана как есть; иначе замещает его только в маске
     *  (Setup не трогает). */
    private String name;
    /** УСТАРЕЛО (2026-08-13) — цвет чек-борда снова общий для экрана, см.
     *  {@code Screen#getBackground}/{@code AppModel#setMaskColor}. Поле и геттер/сеттер
     *  оставлены ТОЛЬКО для десериализации уже сохранённых проектов (Jackson не должен
     *  падать на старом JSON) и для одноразовой миграции при загрузке (см.
     *  {@code AppModel#seedScreenMaskColorsFromLegacyPlacements}) — новый код должен
     *  читать/писать цвет через {@code Screen}, не через это поле. */
    private MaskColorPreset background = MaskColorPreset.NORMAL;
    /** Дефолты трёх ниже — true, чтобы уже сохранённые (до появления этих полей)
     *  проекты продолжали рисовать маску ровно как раньше (сетка+растр+номера были
     *  безусловными). Остальные четыре — новые элементы, раньше не существовавшие,
     *  дефолт false. */
    private boolean showGrid = true;
    /** УСТАРЕЛО (2026-09-30, запрос пользователя «убрать колонку Растр — не используется»):
     *  растр (линии через каждые 16 px) больше нигде не рисуется. Поле оставлено ТОЛЬКО
     *  чтобы Jackson читал старый JSON, где оно есть; в новые файлы не пишется (см.
     *  {@link #isShowRaster()}). */
    private boolean showRaster = true;
    private boolean showIds = true;
    /** Плашка с именем экрана в центре маски (2026-09-30, запрос пользователя: вместо
     *  колонки «Растр» — видимость плашки с именем). true по умолчанию — старые проекты
     *  рисовали плашку безусловно, поведение не меняется. */
    private boolean showNameLabel = true;
    /** Строка разрешения на той же плашке — отдельная галочка (тот же запрос). Плашка
     *  рисуется, если включена хотя бы одна из двух строк; при одной — высота плашки
     *  ужимается под одну строку. true по умолчанию по той же причине, что и выше. */
    private boolean showResolution = true;
    private boolean showCircle;
    private boolean showCross;
    private boolean showCorner;
    private boolean showLogo;

    public CanvasPlacement() {
    }

    public CanvasPlacement(String screenId, int x, int y) {
        this.screenId = screenId;
        this.x = x;
        this.y = y;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getScreenId() {
        return screenId;
    }

    public void setScreenId(String screenId) {
        this.screenId = screenId;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** @deprecated см. class-javadoc — читайте {@code Screen#getBackground()}. */
    @Deprecated
    public MaskColorPreset getBackground() {
        return background != null ? background : MaskColorPreset.NORMAL;
    }

    /** @deprecated см. class-javadoc — пишите через {@code AppModel#setMaskColor}. */
    @Deprecated
    public void setBackground(MaskColorPreset background) {
        this.background = background != null ? background : MaskColorPreset.NORMAL;
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    /** @deprecated растр убран (2026-09-30) — не пишется в JSON, значение игнорируется
     *  рендером; оставлен только сеттер для чтения старых файлов. */
    @Deprecated
    @JsonIgnore
    public boolean isShowRaster() {
        return showRaster;
    }

    /** @deprecated см. {@link #isShowRaster()} — нужен Jackson'у для старого JSON. */
    @Deprecated
    @JsonProperty("showRaster")
    public void setShowRaster(boolean showRaster) {
        this.showRaster = showRaster;
    }

    public boolean isShowNameLabel() {
        return showNameLabel;
    }

    public void setShowNameLabel(boolean showNameLabel) {
        this.showNameLabel = showNameLabel;
    }

    public boolean isShowResolution() {
        return showResolution;
    }

    public void setShowResolution(boolean showResolution) {
        this.showResolution = showResolution;
    }

    public boolean isShowIds() {
        return showIds;
    }

    public void setShowIds(boolean showIds) {
        this.showIds = showIds;
    }

    public boolean isShowCircle() {
        return showCircle;
    }

    public void setShowCircle(boolean showCircle) {
        this.showCircle = showCircle;
    }

    public boolean isShowCross() {
        return showCross;
    }

    public void setShowCross(boolean showCross) {
        this.showCross = showCross;
    }

    public boolean isShowCorner() {
        return showCorner;
    }

    public void setShowCorner(boolean showCorner) {
        this.showCorner = showCorner;
    }

    public boolean isShowLogo() {
        return showLogo;
    }

    public void setShowLogo(boolean showLogo) {
        this.showLogo = showLogo;
    }

    public CanvasPlacement copy() {
        CanvasPlacement p = new CanvasPlacement();
        p.id = id;
        p.screenId = screenId;
        p.x = x;
        p.y = y;
        p.name = name;
        p.background = background;
        p.showGrid = showGrid;
        p.showRaster = showRaster;
        p.showNameLabel = showNameLabel;
        p.showResolution = showResolution;
        p.showIds = showIds;
        p.showCircle = showCircle;
        p.showCross = showCross;
        p.showCorner = showCorner;
        p.showLogo = showLogo;
        return p;
    }
}
