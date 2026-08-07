package com.vjstb.ledscheme.model;

import java.util.UUID;

/** Экран, размещённый внутри канваса (компоновка контента) — позиция в пикселях
 *  канваса, плюс собственная настройка маски этого "грида" (см. class-javadoc
 *  PixelGridRenderer.GridRenderOptions) — раньше цвет чек-борда хранился на самом
 *  {@code Screen} и был общим для всех канвасов, теперь у каждого размещения он
 *  свой, как и набор включённых элементов маски. */
public class CanvasPlacement {

    private String id = UUID.randomUUID().toString();
    private String screenId;
    private int x;
    private int y;
    /** null — использовать имя экрана как есть; иначе замещает его только в маске
     *  (Setup не трогает). */
    private String name;
    private MaskColorPreset background = MaskColorPreset.NORMAL;
    /** Дефолты трёх ниже — true, чтобы уже сохранённые (до появления этих полей)
     *  проекты продолжали рисовать маску ровно как раньше (сетка+растр+номера были
     *  безусловными). Остальные четыре — новые элементы, раньше не существовавшие,
     *  дефолт false. */
    private boolean showGrid = true;
    private boolean showRaster = true;
    private boolean showIds = true;
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

    public MaskColorPreset getBackground() {
        return background != null ? background : MaskColorPreset.NORMAL;
    }

    public void setBackground(MaskColorPreset background) {
        this.background = background != null ? background : MaskColorPreset.NORMAL;
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    public boolean isShowRaster() {
        return showRaster;
    }

    public void setShowRaster(boolean showRaster) {
        this.showRaster = showRaster;
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
        p.showIds = showIds;
        p.showCircle = showCircle;
        p.showCross = showCross;
        p.showCorner = showCorner;
        p.showLogo = showLogo;
        return p;
    }
}
