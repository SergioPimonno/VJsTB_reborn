package com.vjstb.ledscheme.settings;

/**
 * Режим отображения окна плана напольного каркаса ({@code ui.FloorPlanViewPanel}) — запрос
 * пользователя 2026-10-01: «для пола давай сделаем переключаемый режим отображения — либо 2D
 * схема как сейчас, либо 3D редактор как для конструктива». Последний выбор запоминается в
 * профиле ({@link UserProfile#getFloorPlanViewMode()}), по умолчанию — 2D, как было до
 * 3D-редактора.
 */
public enum FloorPlanViewMode {
    PLAN_2D("2D схема"),
    EDITOR_3D("3D редактор");

    private final String label;

    FloorPlanViewMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
