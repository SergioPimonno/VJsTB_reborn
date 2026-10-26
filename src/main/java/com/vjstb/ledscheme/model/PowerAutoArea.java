package com.vjstb.ledscheme.model;

/**
 * Область экрана, разбитая на блоки питания АВТОМАТИЧЕСКИ (docs/receiver-cards/PLAN.md, 4.2, D19/D23): помнится
 * прямоугольник и тип блока, а не сами блоки. Нужна, чтобы при смене параметров типа (прежде всего
 * «Отдельный выход на карту», который меняет, хватает ли блоку шлейфов) разбить область заново с новым подобранным
 * размером. Блоки, поставленные вручную, записи не имеют и при пересчёте не трогаются; ручной блок поверх области
 * удаляет её запись.
 */
public class PowerAutoArea {

    private GridRect rect = new GridRect();
    private String powerSupplyTypeId;
    /** Ограничения размера блока, заданные инженером (модулей по горизонтали / вертикали, не больше); {@code null} —
     *  не задано, размер подбирается полностью автоматически. Помнятся вместе с областью, чтобы пересчёт при смене
     *  типа ({@code PowerSupplyEditor#onTypeChanged}) подбирал размер при тех же ограничениях. */
    private Integer maxWidth;
    private Integer maxHeight;

    public PowerAutoArea() {
    }

    public PowerAutoArea(GridRect rect, String powerSupplyTypeId) {
        this(rect, powerSupplyTypeId, null, null);
    }

    public PowerAutoArea(GridRect rect, String powerSupplyTypeId, Integer maxWidth, Integer maxHeight) {
        this.rect = rect;
        this.powerSupplyTypeId = powerSupplyTypeId;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    public Integer getMaxWidth() {
        return maxWidth;
    }

    public void setMaxWidth(Integer maxWidth) {
        this.maxWidth = maxWidth;
    }

    public Integer getMaxHeight() {
        return maxHeight;
    }

    public void setMaxHeight(Integer maxHeight) {
        this.maxHeight = maxHeight;
    }

    public GridRect getRect() {
        return rect;
    }

    public void setRect(GridRect rect) {
        this.rect = rect != null ? rect : new GridRect();
    }

    public String getPowerSupplyTypeId() {
        return powerSupplyTypeId;
    }

    public void setPowerSupplyTypeId(String powerSupplyTypeId) {
        this.powerSupplyTypeId = powerSupplyTypeId;
    }

    public PowerAutoArea copy() {
        return new PowerAutoArea(rect.copy(), powerSupplyTypeId, maxWidth, maxHeight);
    }
}
