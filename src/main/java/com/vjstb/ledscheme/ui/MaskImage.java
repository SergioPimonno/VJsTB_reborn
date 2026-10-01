package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.MaskGeometry;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * «Задание» маски: размер, есть ли альфа и КАК её нарисовать — без самой картинки в памяти.
 *
 * <p>Запрос пользователя 2026-09-30 (п. 2 плана v2.6, решение D7): «убрать ограничение 16k на
 * сторону при генерации масок и канвасов», не раздувая память. Раньше экспорт и диалог
 * предпросмотра держали готовые {@code BufferedImage} полного разрешения (канвас 30000×8000 —
 * ~1 ГБ каждый, и все сразу). Теперь в памяти живут задания, а пиксели появляются только:
 * <ul>
 *   <li>{@link #render(double)} — в масштабе (превью «1/2…1/8»/«Авто», см.
 *       {@link MaskPreviewResolution}), через {@code g.scale};</li>
 *   <li>{@link #thumbnail(int, int)} — миниатюра для списка в диалоге предпросмотра;</li>
 *   <li>{@link #writePng(File)} — на диск полосами через {@link StreamingPngWriter}: память
 *       ∝ ширине × высоте полосы ({@link #defaultStripRows}).</li>
 * </ul>
 * Потоковый PNG попиксельно совпадает с {@code PixelGridRenderer.renderX()} + {@code ImageIO}
 * (главный тест — {@code MaskStreamingExportTest}): каждая полоса — тот же paint-код со
 * сдвигом {@code translate(0, -y0)} и clip по полосе.
 */
public final class MaskImage {

    /** Рисует маску в координатах ИЗОБРАЖЕНИЯ (0..width × 0..height) в переданный Graphics;
     *  его transform/clip может задать вызывающий (полоса, масштаб). */
    @FunctionalInterface
    public interface Painter {
        void paint(Graphics2D g);
    }

    /** Ход записи файла и отмена — для окна прогресса (см. {@link ExportProgressDialog#runInBackground}). */
    public interface WriteProgress {
        WriteProgress NONE = (done, total) -> { };

        /** Записано {@code rowsDone} строк из {@code totalRows}. Вызывается из фонового потока. */
        void progress(int rowsDone, int totalRows);

        /** {@code true} — пользователь нажал «Отмена»: запись прерывается, недописанный файл удаляется. */
        default boolean cancelled() {
            return false;
        }
    }

    /** Ориентир по памяти на одну полосу (пикселей): 16 Мпикс × 4 Б = 64 МБ. */
    private static final long STRIP_BUDGET_PX = 16L * 1024 * 1024;

    private final int width;
    private final int height;
    private final boolean alpha;
    private final Painter painter;

    public MaskImage(int width, int height, boolean alpha, Painter painter) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Размер маски должен быть положительным: " + width + "×" + height);
        }
        this.width = width;
        this.height = height;
        this.alpha = alpha;
        this.painter = painter;
    }

    /** Маска экрана — размер по {@link MaskGeometry} (с множителем экрана-«сетки»). */
    public static MaskImage screen(Screen screen, CabinetType type, Workspace workspace,
                                   PixelGridRenderer.GridRenderOptions opts) {
        MaskGeometry geo = MaskGeometry.of(screen, type, workspace);
        return new MaskImage(geo.width(), geo.height(), false, g -> PixelGridRenderer.paintMask(g, screen, geo, opts));
    }

    /** Маска канваса (см. {@code PixelGridRenderer.renderCanvasMask}). Размещения и их
     *  настройки (включая файл лого) разрешаются здесь один раз, а не на каждую полосу. */
    public static MaskImage canvas(ContentCanvas canvas, Scene scene, AppModel model, SettingsManager settings) {
        int w = Math.max(1, canvas.getWidthPx());
        int h = Math.max(1, canvas.getHeightPx());
        List<PixelGridRenderer.PlacedScreen> placed = PixelGridRenderer.resolvePlacements(canvas, scene, model, settings);
        return new MaskImage(w, h, false, g -> PixelGridRenderer.paintCanvasMask(g, w, h, placed));
    }

    /** Маска пустот канваса для After Effects (RGBA, см. {@code renderCanvasGapMask}). */
    public static MaskImage canvasGap(ContentCanvas canvas, Scene scene, AppModel model) {
        int w = Math.max(1, canvas.getWidthPx());
        int h = Math.max(1, canvas.getHeightPx());
        List<PixelGridRenderer.PlacedScreen> placed = PixelGridRenderer.resolvePlacements(canvas, scene, model, null);
        return new MaskImage(w, h, true, g -> PixelGridRenderer.paintCanvasGapMask(g, w, h, placed));
    }

    /** Оверлей-разметка канваса для After Effects (RGBA, см. {@code renderCanvasOverlay}). */
    public static MaskImage canvasOverlay(ContentCanvas canvas, Scene scene, AppModel model) {
        int w = Math.max(1, canvas.getWidthPx());
        int h = Math.max(1, canvas.getHeightPx());
        List<PixelGridRenderer.PlacedScreen> placed = PixelGridRenderer.resolvePlacements(canvas, scene, model, null);
        return new MaskImage(w, h, true, g -> PixelGridRenderer.paintCanvasOverlay(g, w, h, placed));
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean alpha() {
        return alpha;
    }

    /** Рисует маску в переданный Graphics (координаты изображения). */
    public void paint(Graphics2D g) {
        painter.paint(g);
    }

    /** Размер изображения в масштабе {@code scale}: {@code ceil(side × scale)}, не меньше 1
     *  (с допуском на погрешность double — 1/3 от 300 даёт ровно 100, а не 101). */
    public static int scaledSide(int side, double scale) {
        return Math.max(1, (int) Math.ceil(side * scale - 1e-9));
    }

    /** Маска в масштабе {@code scale} (1 — нативное разрешение): рисуется заново в меньшую
     *  картинку через {@code g.scale}, а не уменьшается из полной — видны реальные потери
     *  детализации, как у пресетов «Resolution» в After Effects. */
    public BufferedImage render(double scale) {
        if (!(scale > 0)) {
            throw new IllegalArgumentException("Масштаб должен быть положительным: " + scale);
        }
        int w = scaledSide(width, scale);
        int h = scaledSide(height, scale);
        BufferedImage img = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            if (scale != 1.0) {
                g.scale(scale, scale);
            }
            painter.paint(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    /** Миниатюра, вписанная в {@code maxW × maxH} (не крупнее нативного размера). */
    public BufferedImage thumbnail(int maxW, int maxH) {
        double scale = Math.min(1.0, Math.min((double) maxW / width, (double) maxH / height));
        return render(scale);
    }

    /** Высота полосы для потоковой записи: ~512–1024 строк, но не больше ~16 Мпикс на полосу
     *  (у канваса шириной 30000 px — ~560 строк, ~64 МБ). */
    public static int defaultStripRows(int width) {
        long rows = STRIP_BUDGET_PX / Math.max(1, width);
        return (int) Math.max(64, Math.min(1024, rows));
    }

    public void writePng(File target) throws IOException {
        writePng(target, defaultStripRows(width), WriteProgress.NONE);
    }

    /** Пишет PNG полосами по {@code stripRows} строк (память ∝ ширине × stripRows). При ошибке
     *  или отмене ({@link WriteProgress#cancelled()} → {@link CancellationException})
     *  недописанный файл удаляется — иначе в папке остался бы битый PNG с правильным именем. */
    public void writePng(File target, int stripRows, WriteProgress progress) throws IOException {
        int rowsPer = Math.max(1, Math.min(stripRows, height));
        WriteProgress p = progress != null ? progress : WriteProgress.NONE;
        boolean ok = false;
        StreamingPngWriter writer = new StreamingPngWriter(target, width, height, alpha);
        try {
            BufferedImage strip = new BufferedImage(width, rowsPer,
                    alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            int[] data = ((DataBufferInt) strip.getRaster().getDataBuffer()).getData();
            for (int y0 = 0; y0 < height; y0 += rowsPer) {
                if (p.cancelled()) {
                    throw new CancellationException("Запись «" + target.getName() + "» отменена");
                }
                int rows = Math.min(rowsPer, height - y0);
                // Полоса переиспользуется: обнуляем как свежую картинку (чёрная / прозрачная).
                Arrays.fill(data, 0);
                Graphics2D g = strip.createGraphics();
                try {
                    g.translate(0, -y0);
                    g.setClip(0, y0, width, rows);
                    painter.paint(g);
                } finally {
                    g.dispose();
                }
                writer.writeRows(strip, rows);
                p.progress(y0 + rows, height);
            }
            ok = true;
        } finally {
            try {
                writer.close();
            } catch (IOException closeError) {
                if (ok) {
                    target.delete();
                    throw closeError;
                }
                // уже летит исходная ошибка/отмена — недописанный файл просто удаляем ниже
            }
            if (!ok) {
                target.delete();
            }
        }
    }
}
