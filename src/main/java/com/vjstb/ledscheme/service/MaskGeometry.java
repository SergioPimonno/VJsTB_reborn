package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;

/**
 * Геометрия маски экрана в НАТИВНЫХ пикселях маски — единая точка для всех, кто
 * рисует или раскладывает маски (рендер, маска пустот, оверлей AE, редактор канваса,
 * пресеты Resolume/After Effects, проверка «канвас обрезает маски»).
 *
 * <p>Запрос 2026-09-30: экран из кабинетов-«сеток» ({@link Screen#isMaskMesh()})
 * получает маску выше реального разрешения в {@link Screen#getMaskHeightMultiplier()}
 * раз (по умолчанию 2) — раньше каждый потребитель брал {@code resolutionWidthPx/
 * HeightPx} из {@link ScreenStats} сам, и множитель пришлось бы повторять в каждом.
 *
 * @param width         ширина маски, px
 * @param height        высота маски, px — уже с множителем
 * @param cellW         ширина ячейки кабинета, px
 * @param cellH         высота ячейки кабинета, px — уже с множителем
 * @param resolutionW   реальное разрешение экрана по ширине (без множителя)
 * @param resolutionH   реальное разрешение экрана по высоте (без множителя)
 * @param multiplier    фактический множитель высоты (1 — обычный экран)
 */
public record MaskGeometry(int width, int height, int cellW, int cellH,
                           int resolutionW, int resolutionH, int multiplier, CabinetType type) {

    public static MaskGeometry of(Screen screen, CabinetType defaultType, Workspace workspace) {
        ScreenStats stats = ScreenLogic.stats(screen, defaultType, workspace);
        int resW = Math.max(1, stats.resolutionWidthPx());
        int resH = Math.max(1, stats.resolutionHeightPx());
        int m = screen.effectiveMaskHeightMultiplier();
        int cellW = defaultType != null && screen.getCols() > 0 ? resW / screen.getCols() : resW;
        int baseCellH = defaultType != null && screen.getRows() > 0 ? resH / screen.getRows() : resH;
        return new MaskGeometry(resW, resH * m, cellW, baseCellH * m,
                stats.resolutionWidthPx(), stats.resolutionHeightPx(), m, defaultType);
    }

    /** X ячейки кабинета — сеточная позиция плюс свободное мм-смещение (см.
     *  CabinetInstance.getOffsetXMm, Task #7/v1.6) в масштабе пикселей маски. */
    public int cabinetX(CabinetInstance cab) {
        double dx = type != null ? ScreenLogic.offsetPx(cab.getOffsetXMm(), cellW, type.getWidthMm()) : 0;
        return (int) Math.round(cab.getColIndex() * cellW + dx);
    }

    /** Y ячейки кабинета — как {@link #cabinetX}; смещение масштабируется по уже
     *  умноженной высоте ячейки, т.е. растягивается вместе с маской экрана-сетки. */
    public int cabinetY(CabinetInstance cab) {
        double dy = type != null ? ScreenLogic.offsetPx(cab.getOffsetYMm(), cellH, type.getHeightMm()) : 0;
        return (int) Math.round(cab.getRowIndex() * cellH + dy);
    }

    /** Подпись размера маски: «W×H px», для экрана-сетки с пометкой множителя. */
    public String sizeLabel() {
        return width + "×" + height + " px" + (multiplier > 1 ? " (сетка ×" + multiplier + ")" : "");
    }
}
