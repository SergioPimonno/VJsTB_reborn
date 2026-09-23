package com.vjstb.ledscheme.settings;

/** Как показывать палитру кабинетов (see {@code CabinetType#isVisibleInPalette()})
 *  в меню выбора типа кабинета на ячейке (see {@code ui.SceneCanvasPanel}) —
 *  RADIAL: как раньше, круговое меню Krita-style; DROPDOWN: обычный
 *  выпадающий список. Оба режима показывают ОДИН и тот же (отфильтрованный по
 *  палитре) список типов — переключатель меняет только форму отображения, не
 *  состав. Профильная настройка ({@link UserProfile}), переключается в
 *  {@code ui.PersonalizationDialog}. */
public enum CabinetPaletteViewMode {
    RADIAL("Радиальное меню"),
    DROPDOWN("Выпадающий список");

    private final String label;

    CabinetPaletteViewMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
