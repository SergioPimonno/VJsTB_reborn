package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CanvasPlacement;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.MaskColorPreset;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.ScreenStats;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Генератор тестовых масок экрана (аналог Pixel Grid из Novastar/Colorlight):
 * растр в РЕАЛЬНОМ разрешении экрана с чередующейся по кабинетам заливкой,
 * мелкой пиксельной сеткой и номером кабинета в каждой ячейке — чтобы на месте
 * визуально проверить порядок/ориентацию модулей. В центре — крупная подпись
 * с названием экрана и разрешением для контентщиков.
 */
public final class PixelGridRenderer {

    private static final Color GRID_LINE = new Color(255, 255, 255, 40);
    private static final int GRID_STEP_PX = 16;

    private PixelGridRenderer() {
    }

    /** Позиция ячейки в НАТИВНЫХ пикселях маски — сеточная позиция плюс свободное
     *  мм-смещение (см. CabinetInstance.getOffsetXMm/getOffsetYMm, Task #7/v1.6),
     *  переведённое через {@link ScreenLogic#offsetPx} (тот же приём, что и в
     *  SchemeRenderer/CanvasPanel/SceneCanvasPanel — независимая копия, здесь
     *  масштаб пикселей другой: не экранный зум, а реальное разрешение маски). */
    private static int cabX(CabinetInstance cab, CabinetType type, int cellW) {
        double dx = type != null ? ScreenLogic.offsetPx(cab.getOffsetXMm(), cellW, type.getWidthMm()) : 0;
        return (int) Math.round(cab.getColIndex() * cellW + dx);
    }

    private static int cabY(CabinetInstance cab, CabinetType type, int cellH) {
        double dy = type != null ? ScreenLogic.offsetPx(cab.getOffsetYMm(), cellH, type.getHeightMm()) : 0;
        return (int) Math.round(cab.getRowIndex() * cellH + dy);
    }

    public static BufferedImage renderMask(Screen screen, CabinetType defaultType, Workspace workspace) {
        ScreenStats stats = ScreenLogic.stats(screen, defaultType, workspace);
        int w = Math.max(1, stats.resolutionWidthPx());
        int h = Math.max(1, stats.resolutionHeightPx());
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, w, h);

        int cellW = defaultType != null && screen.getCols() > 0 ? w / screen.getCols() : w;
        int cellH = defaultType != null && screen.getRows() > 0 ? h / screen.getRows() : h;
        Font cellFont = g2.getFont().deriveFont(Font.BOLD, Math.max(10f, Math.min(cellW, cellH) * 0.16f));
        MaskColorPreset colorPreset = screen.getMaskColorPreset();

        for (CabinetInstance cab : screen.getCabinets()) {
            if (cab.isHidden()) {
                continue;
            }
            int x = cabX(cab, defaultType, cellW);
            int y = cabY(cab, defaultType, cellH);

            g2.setColor(colorPreset.color((cab.getRowIndex() + cab.getColIndex()) % 2));
            g2.fillRect(x, y, cellW, cellH);

            g2.setColor(GRID_LINE);
            g2.setStroke(new BasicStroke(1f));
            for (int gx = x; gx <= x + cellW; gx += GRID_STEP_PX) {
                g2.drawLine(gx, y, gx, y + cellH);
            }
            for (int gy = y; gy <= y + cellH; gy += GRID_STEP_PX) {
                g2.drawLine(x, gy, x + cellW, gy);
            }

            g2.setColor(new Color(255, 255, 255, 200));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(x, y, cellW, cellH);

            g2.setColor(Color.WHITE);
            g2.setFont(cellFont);
            String label = cab.getDisplayRow() + "," + cab.getDisplayCol();
            g2.drawString(label, x + 6, y + cellFont.getSize() + 4);
        }

        drawCenterLabel(g2, w, h, screen.getName(), stats.resolutionWidthPx() + "×" + stats.resolutionHeightPx() + " px");

        g2.dispose();
        return img;
    }

    private static void drawCenterLabel(Graphics2D g2, int w, int h, String name, String resolution) {
        Font nameFont = g2.getFont().deriveFont(Font.BOLD, Math.max(18f, w * 0.045f));
        Font resFont = g2.getFont().deriveFont(Font.PLAIN, Math.max(13f, w * 0.028f));
        FontMetrics nameFm = g2.getFontMetrics(nameFont);
        FontMetrics resFm = g2.getFontMetrics(resFont);

        int nameW = nameFm.stringWidth(name);
        int resW = resFm.stringWidth(resolution);
        int boxW = Math.max(nameW, resW) + 60;
        int boxH = nameFm.getHeight() + resFm.getHeight() + 36;
        int boxX = (w - boxW) / 2;
        int boxY = (h - boxH) / 2;

        Graphics2D gb = (Graphics2D) g2.create();
        gb.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.72f));
        gb.setColor(new Color(0x0d1117));
        gb.fillRoundRect(boxX, boxY, boxW, boxH, 18, 18);
        gb.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        gb.setColor(new Color(0x58a6ff));
        gb.setStroke(new BasicStroke(2f));
        gb.drawRoundRect(boxX, boxY, boxW, boxH, 18, 18);
        gb.dispose();

        g2.setColor(Color.WHITE);
        g2.setFont(nameFont);
        g2.drawString(name, (w - nameW) / 2, boxY + 14 + nameFm.getAscent());

        g2.setColor(new Color(0xc0, 0xc8, 0xd0));
        g2.setFont(resFont);
        g2.drawString(resolution, (w - resW) / 2, boxY + 20 + nameFm.getHeight() + resFm.getAscent());
    }

    /** Маска целого канваса (компоновки контента): чёрный кадр размером с канвас,
     *  в него вклеены маски каждого размещённого экрана на своих позициях — так же
     *  используется как основа под пресеты медиасерверов/Resolume. */
    public static BufferedImage renderCanvasMask(ContentCanvas canvas, AppModel model) {
        int w = Math.max(1, canvas.getWidthPx());
        int h = Math.max(1, canvas.getHeightPx());
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, w, h);

        Scene scene = model.getCurrentScene();
        Font offsetFont = g2.getFont().deriveFont(Font.PLAIN, 11f);
        for (CanvasPlacement pl : canvas.getPlacements()) {
            Screen scr = screenById(scene, pl.getScreenId());
            if (scr == null) {
                continue;
            }
            CabinetType type = model.typeOf(scr);
            BufferedImage screenImg = renderMask(scr, type, model.getWorkspace());
            g2.drawImage(screenImg, pl.getX(), pl.getY(), null);

            // Оффсет экрана в пикселях канваса — вместо общего названия/разрешения
            // канваса в углу (контентщику нужна именно позиция каждого экрана).
            // Рисуем на непрозрачной подложке: без неё текст сливался с пиксельной
            // сеткой и номером кабинета «1,1» в том же углу и был нечитаем.
            g2.setFont(offsetFont);
            FontMetrics offsetFm = g2.getFontMetrics();
            String offsetLabel = pl.getX() + ", " + pl.getY() + " px";
            int labelW = offsetFm.stringWidth(offsetLabel);
            g2.setColor(new Color(0, 0, 0, 210));
            g2.fillRect(pl.getX(), pl.getY(), labelW + 8, offsetFont.getSize() + 6);
            g2.setColor(Color.WHITE);
            g2.drawString(offsetLabel, pl.getX() + 4, pl.getY() + offsetFont.getSize() + 2);
        }

        g2.dispose();
        return img;
    }

    private static Screen screenById(Scene scene, String screenId) {
        if (scene == null) {
            return null;
        }
        for (Screen s : scene.getScreens()) {
            if (s.getId().equals(screenId)) {
                return s;
            }
        }
        return null;
    }

    public static void writePng(BufferedImage img, File file) throws IOException {
        // ImageIO бросает нечитаемое "Can't create an ImageOutputStream!" (без
        // указания причины), если родительской папки не существует — например,
        // если пользователь удалил/переименовал её после открытия диалога
        // предпросмотра, но до нажатия «Сохранить». Гарантируем её наличие прямо
        // перед записью, а не только один раз при открытии диалога.
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        ImageIO.write(img, "png", file);
    }
}
