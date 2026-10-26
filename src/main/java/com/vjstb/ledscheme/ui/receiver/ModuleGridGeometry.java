package com.vjstb.ledscheme.ui.receiver;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import java.awt.geom.Rectangle2D;

/**
 * Раскладка клеток экрана на холсте видов «Приёмные карты» и «Блоки питания»: начало сетки, размер клетки, перевод
 * «пиксели ↔ (строка, столбец)». Отдельный неизменяемый объект, а не поля Swing-компонента, потому что те же клетки
 * рисует и экспорт «Карта подключения» (трек E) на картинке/в PDF другого размера: рендер получает геометрию
 * параметром и ничего не знает ни про окно, ни про масштаб интерфейса.
 *
 * <p>Клетка не обязательно квадратная: пропорция берётся из типа кабинета экрана ({@link #aspectOf}), иначе экран
 * из модулей 500×250 выглядел бы на холсте как квадраты и не совпадал бы с тем, что пользователь видит в Сетапе.
 */
public final class ModuleGridGeometry {

    private final double x0;
    private final double y0;
    private final double cellW;
    private final double cellH;
    private final int rows;
    private final int cols;

    private ModuleGridGeometry(double x0, double y0, double cellW, double cellH, int rows, int cols) {
        this.x0 = x0;
        this.y0 = y0;
        this.cellW = cellW;
        this.cellH = cellH;
        this.rows = rows;
        this.cols = cols;
    }

    /** Геометрия с заданными началом и размерами клетки (для печатного рендера, где размер задаёт вызывающий). */
    public static ModuleGridGeometry of(double x0, double y0, double cellW, double cellH, int rows, int cols) {
        return new ModuleGridGeometry(x0, y0, cellW, cellH, Math.max(1, rows), Math.max(1, cols));
    }

    /**
     * Вписывает сетку {@code rows × cols} клеток пропорции {@code aspect} (ширина/высота клетки) в область
     * {@code width × height}, по центру, с полями {@code margin}. Клетка не крупнее {@code maxCellWidth}: на маленьком
     * экране из четырёх модулей клетка во всё окно выглядит нелепо.
     */
    public static ModuleGridGeometry fit(int rows, int cols, double aspect, double width, double height,
                                         double margin, double maxCellWidth) {
        int r = Math.max(1, rows);
        int c = Math.max(1, cols);
        double a = aspect > 0 ? aspect : 1;
        double availW = Math.max(1, width - 2 * margin);
        double availH = Math.max(1, height - 2 * margin);
        double cw = Math.min(availW / c, availH * a / r);
        cw = Math.max(1, Math.min(cw, maxCellWidth));
        double ch = cw / a;
        double gridW = cw * c;
        double gridH = ch * r;
        return new ModuleGridGeometry((width - gridW) / 2, (height - gridH) / 2, cw, ch, r, c);
    }

    /** Пропорция клетки (ширина/высота) по типу кабинета экрана; 1, если типа нет, и не более 2,5 в любую сторону. */
    public static double aspectOf(Screen screen, Workspace workspace) {
        CabinetType t = screen != null && workspace != null ? workspace.cabinetTypeById(screen.getCabinetTypeId()) : null;
        if (t == null || t.getWidthMm() <= 0 || t.getHeightMm() <= 0) {
            return 1;
        }
        return Math.max(0.4, Math.min(2.5, t.getWidthMm() / t.getHeightMm()));
    }

    public int rows() {
        return rows;
    }

    public int cols() {
        return cols;
    }

    public double cellWidth() {
        return cellW;
    }

    public double cellHeight() {
        return cellH;
    }

    /** Меньшая сторона клетки — от неё считаются шрифты и отступы. */
    public double cellMin() {
        return Math.min(cellW, cellH);
    }

    /** Прямоугольник клетки. */
    public Rectangle2D cell(int row, int col) {
        return new Rectangle2D.Double(x0 + col * cellW, y0 + row * cellH, cellW, cellH);
    }

    public double centerX(int col) {
        return x0 + col * cellW + cellW / 2;
    }

    public double centerY(int row) {
        return y0 + row * cellH + cellH / 2;
    }

    /** Прямоугольник всей сетки. */
    public Rectangle2D bounds() {
        return new Rectangle2D.Double(x0, y0, cellW * cols, cellH * rows);
    }

    /**
     * Клетка под точкой: {@code {строка, столбец}}. Без {@code clamp} точка вне сетки даёт {@code null}; с ним — ближайшую
     * клетку (протяжка за край холста продолжает выделение до края сетки, как в прототипе).
     */
    public int[] cellAt(double x, double y, boolean clamp) {
        int col = (int) Math.floor((x - x0) / cellW);
        int row = (int) Math.floor((y - y0) / cellH);
        if (clamp) {
            return new int[]{Math.max(0, Math.min(rows - 1, row)), Math.max(0, Math.min(cols - 1, col))};
        }
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            return null;
        }
        return new int[]{row, col};
    }
}
