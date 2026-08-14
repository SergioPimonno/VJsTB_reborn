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
import com.vjstb.ledscheme.settings.SettingsManager;
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
 * Генератор тестовых масок экрана (аналог Pixel Grid из Novastar/Colorlight, в духе
 * стороннего инструмента PixL, показанного пользователем как референс): растр в
 * РЕАЛЬНОМ разрешении экрана с чередующейся по кабинетам заливкой и настраиваемым
 * набором элементов на грид (см. {@link GridRenderOptions}) — раньше сетка/растр/
 * номера рисовались безусловно, теперь каждый включается отдельно per-грид (см.
 * {@code CanvasPlacement}), а окружность/крест/уголковые метки/лого — новые
 * элементы, которых раньше не было вовсе.
 */
public final class PixelGridRenderer {

    private static final Color GRID_LINE = new Color(255, 255, 255, 40);
    private static final int GRID_STEP_PX = 16;
    /** Жёлтый — чтобы не сливаться ни с белой сеткой/номерами, ни с фоном чек-борда. */
    private static final Color MARK_COLOR = new Color(255, 221, 0, 230);

    private PixelGridRenderer() {
    }

    /** Что именно рисовать на маске одного грида (размещённого на канвасе экрана) —
     *  собирается из {@link CanvasPlacement} (свои для КАЖДОГО грида: имя-override,
     *  фон, какие элементы включены) и {@link ContentCanvas} (общие для ВСЕХ гридов
     *  канваса: крупные имена/цвет текста/тень/лого). */
    public record GridRenderOptions(String displayName, MaskColorPreset background,
                                     boolean showGrid, boolean showRaster, boolean showIds,
                                     boolean showCircle, boolean showCross, boolean showCorner,
                                     boolean showLogo, boolean largeGridNames, Color textColor,
                                     boolean dropShadow, BufferedImage logoImage) {

        /** Лого берётся не с канваса, а из профиля пользователя (см.
         *  {@link com.vjstb.ledscheme.settings.UserProfile#getMaskLogoImagePath()}) —
         *  оно индивидуально для человека, который работает в приложении, а не для
         *  конкретного канваса/проекта: настраивается один раз в «Предпочтениях» и
         *  дальше автоматически применяется на любом гриде с включённым «Лого». */
        public static GridRenderOptions of(Screen screen, CanvasPlacement pl, ContentCanvas canvas,
                                            SettingsManager settings) {
            String displayName = pl.getName() != null && !pl.getName().isBlank() ? pl.getName() : screen.getName();
            Color textColor = canvas.getTextColorRgb() != null ? new Color(canvas.getTextColorRgb()) : Color.WHITE;
            String logoPath = settings.activeProfile().getMaskLogoImagePath();
            BufferedImage logo = null;
            if (pl.isShowLogo() && logoPath != null) {
                try {
                    logo = ImageIO.read(new File(logoPath));
                } catch (IOException | RuntimeException unreadableLogo) {
                    logo = null;
                }
            }
            // background -- ОБЩИЙ для экрана (см. Screen#getBackground), не с pl -- та же
            // запись экрана в разных канвасах теперь красится одинаково (2026-08-13,
            // баг-репорт: раньше был per-placement, см. class-javadoc CanvasPlacement).
            return new GridRenderOptions(displayName, screen.getBackground(),
                    pl.isShowGrid(), pl.isShowRaster(), pl.isShowIds(),
                    pl.isShowCircle(), pl.isShowCross(), pl.isShowCorner(), pl.isShowLogo(),
                    canvas.isLargeGridNames(), textColor, canvas.isDropShadow(), logo);
        }

        /** Для отдельного полноэкранного экспорта маски вне канваса (по сцене целиком,
         *  см. VisualizationStagePanel.exportMasks) — прежнее поведение по умолчанию
         *  (сетка+растр+номера, без новых элементов, без канваса), но цвет теперь берётся
         *  из {@link Screen#getBackground()}, а не хардкодится в NORMAL — иначе этот путь
         *  экспорта расходился бы с тем, что реально видно на канвасе (тот же баг-репорт,
         *  что и у {@link #of}). */
        public static GridRenderOptions defaultForScreen(Screen screen) {
            return new GridRenderOptions(screen.getName(), screen.getBackground(),
                    true, true, true, false, false, false, false,
                    false, Color.WHITE, false, null);
        }
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

    public static BufferedImage renderMask(Screen screen, CabinetType defaultType, Workspace workspace,
                                            GridRenderOptions opts) {
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

        for (CabinetInstance cab : screen.getCabinets()) {
            if (cab.isHidden()) {
                continue;
            }
            int x = cabX(cab, defaultType, cellW);
            int y = cabY(cab, defaultType, cellH);

            g2.setColor(opts.background().color((cab.getRowIndex() + cab.getColIndex()) % 2));
            g2.fillRect(x, y, cellW, cellH);

            if (opts.showRaster()) {
                g2.setColor(GRID_LINE);
                g2.setStroke(new BasicStroke(1f));
                for (int gx = x; gx <= x + cellW; gx += GRID_STEP_PX) {
                    g2.drawLine(gx, y, gx, y + cellH);
                }
                for (int gy = y; gy <= y + cellH; gy += GRID_STEP_PX) {
                    g2.drawLine(x, gy, x + cellW, gy);
                }
            }

            if (opts.showGrid()) {
                g2.setColor(new Color(255, 255, 255, 200));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(x, y, cellW, cellH);
            }

            if (opts.showCircle()) {
                drawCircleMark(g2, x, y, cellW, cellH);
            }
            if (opts.showCross()) {
                drawCrossMark(g2, x, y, cellW, cellH);
            }
            if (opts.showCorner()) {
                drawCornerMarks(g2, x, y, cellW, cellH);
            }

            if (opts.showIds()) {
                g2.setColor(Color.WHITE);
                g2.setFont(cellFont);
                String label = cab.getDisplayRow() + "," + cab.getDisplayCol();
                g2.drawString(label, x + 6, y + cellFont.getSize() + 4);
            }
        }

        if (opts.showLogo() && opts.logoImage() != null) {
            drawLogo(g2, w, h, opts.logoImage());
        }

        drawCenterLabel(g2, w, h, opts.displayName(),
                stats.resolutionWidthPx() + "×" + stats.resolutionHeightPx() + " px", opts);

        g2.dispose();
        return img;
    }

    /** Окружность, вписанная в ячейку кабинета — для физической выверки центровки. */
    private static void drawCircleMark(Graphics2D g2, int x, int y, int cellW, int cellH) {
        int d = (int) (Math.min(cellW, cellH) * 0.7);
        int cx = x + cellW / 2 - d / 2;
        int cy = y + cellH / 2 - d / 2;
        g2.setColor(MARK_COLOR);
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(cx, cy, d, d);
    }

    /** Крест через центр ячейки — тот же физический смысл, что окружность. */
    private static void drawCrossMark(Graphics2D g2, int x, int y, int cellW, int cellH) {
        int len = (int) (Math.min(cellW, cellH) * 0.35);
        int cx = x + cellW / 2;
        int cy = y + cellH / 2;
        g2.setColor(MARK_COLOR);
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(cx - len, cy, cx + len, cy);
        g2.drawLine(cx, cy - len, cx, cy + len);
    }

    /** Уголковые метки по 4 углам ячейки (как рамка видоискателя) — тот же принцип,
     *  что круг/крест, но по краям, а не по центру. */
    private static void drawCornerMarks(Graphics2D g2, int x, int y, int cellW, int cellH) {
        int len = (int) (Math.min(cellW, cellH) * 0.22);
        g2.setColor(MARK_COLOR);
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(x, y, x + len, y);
        g2.drawLine(x, y, x, y + len);
        g2.drawLine(x + cellW, y, x + cellW - len, y);
        g2.drawLine(x + cellW, y, x + cellW, y + len);
        g2.drawLine(x, y + cellH, x + len, y + cellH);
        g2.drawLine(x, y + cellH, x, y + cellH - len);
        g2.drawLine(x + cellW, y + cellH, x + cellW - len, y + cellH);
        g2.drawLine(x + cellW, y + cellH, x + cellW, y + cellH - len);
    }

    /** Лого — в правом нижнем углу экрана (не по центру, там уже подпись имени/
     *  разрешения), масштаб ограничен ~12% меньшей стороны экрана. */
    private static void drawLogo(Graphics2D g2, int w, int h, BufferedImage logo) {
        int maxSize = (int) (Math.min(w, h) * 0.12);
        if (maxSize <= 0 || logo.getWidth() <= 0 || logo.getHeight() <= 0) {
            return;
        }
        double scale = Math.min((double) maxSize / logo.getWidth(), (double) maxSize / logo.getHeight());
        int lw = Math.max(1, (int) (logo.getWidth() * scale));
        int lh = Math.max(1, (int) (logo.getHeight() * scale));
        int margin = Math.max(8, (int) (Math.min(w, h) * 0.015));
        g2.drawImage(logo, w - lw - margin, h - lh - margin, lw, lh, null);
    }

    private static void drawCenterLabel(Graphics2D g2, int w, int h, String name, String resolution,
                                         GridRenderOptions opts) {
        float minNameSize = opts.largeGridNames() ? 26f : 18f;
        float nameScale = opts.largeGridNames() ? 0.065f : 0.045f;
        Font nameFont = g2.getFont().deriveFont(Font.BOLD, Math.max(minNameSize, w * nameScale));
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

        int nameX = (w - nameW) / 2;
        int nameY = boxY + 14 + nameFm.getAscent();
        if (opts.dropShadow()) {
            g2.setColor(new Color(0, 0, 0, 200));
            g2.setFont(nameFont);
            g2.drawString(name, nameX + 2, nameY + 2);
        }
        g2.setColor(opts.textColor());
        g2.setFont(nameFont);
        g2.drawString(name, nameX, nameY);

        int resX = (w - resW) / 2;
        int resY = boxY + 20 + nameFm.getHeight() + resFm.getAscent();
        g2.setColor(new Color(0xc0, 0xc8, 0xd0));
        g2.setFont(resFont);
        g2.drawString(resolution, resX, resY);
    }

    /** Маска целого канваса (компоновки контента): чёрный кадр размером с канвас,
     *  в него вклеены маски каждого размещённого экрана на своих позициях, каждая —
     *  со своими настройками (см. {@link GridRenderOptions#of}) — так же используется
     *  как основа под пресеты медиасерверов/Resolume. */
    public static BufferedImage renderCanvasMask(ContentCanvas canvas, AppModel model, SettingsManager settings) {
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
            GridRenderOptions opts = GridRenderOptions.of(scr, pl, canvas, settings);
            BufferedImage screenImg = renderMask(scr, type, model.getWorkspace(), opts);
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
