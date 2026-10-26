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

    public PowerAutoArea() {
    }

    public PowerAutoArea(GridRect rect, String powerSupplyTypeId) {
        this.rect = rect;
        this.powerSupplyTypeId = powerSupplyTypeId;
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
        return new PowerAutoArea(rect.copy(), powerSupplyTypeId);
    }
}
