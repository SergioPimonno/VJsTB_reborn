package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CanvasPlacement;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.MaskGeometry;
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
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Генератор тестовых масок экрана (аналог Pixel Grid из Novastar/Colorlight, в духе
 * стороннего инструмента PixL, показанного пользователем как референс): растр в
 * РЕАЛЬНОМ разрешении экрана с чередующейся по кабинетам заливкой и настраиваемым
 * набором элементов на грид (см. {@link GridRenderOptions}) — раньше сетка/растр/
 * номера рисовались безусловно, теперь каждый включается отдельно per-грид (см.
 * {@code CanvasPlacement}), а окружность/крест/уголковые метки/лого — новые
 * элементы, которых раньше не было вовсе.
 *
 * <p>2026-09-30 (запрос пользователя): растр (тонкие линии через 16 px) убран совсем — его
 * место в таблице гридов заняли галочки «Плашка»/«Разрешение»; размеры маски и ячеек
 * берутся ТОЛЬКО из {@link MaskGeometry} (учитывает экран-«сетку» с множителем высоты);
 * цвет клетки — через пару {@link GridRenderOptions#colorEven()}/{@code colorOdd()},
 * полученную из {@link Screen#maskColor(int)} (пресет или собственная пара).
 *
 * <p>2026-09-30 (запрос «убрать ограничение 16k», решение D7): основная форма — paint-методы
 * ({@link #paintMask}, {@link #paintCanvasMask}, {@link #paintCanvasGapMask},
 * {@link #paintCanvasOverlay}), которые рисуют в любой Graphics2D в координатах изображения;
 * маски экранов рисуются прямо в канвас, без промежуточных картинок. Экспорт и превью идут
 * через {@link MaskImage} (полосы / масштаб); {@code render*}-методы, возвращающие
 * {@code BufferedImage} целиком, оставлены обёртками для тестов и мелких размеров.
 */
public final class PixelGridRenderer {

    /** Жёлтый — чтобы не сливаться ни с белой сеткой/номерами, ни с фоном чек-борда. */
    private static final Color MARK_COLOR = new Color(255, 221, 0, 230);

    private PixelGridRenderer() {
    }

    /** Что именно рисовать на маске одного грида (размещённого на канвасе экрана) —
     *  собирается из {@link CanvasPlacement} (свои для КАЖДОГО грида: имя-override,
     *  фон, какие элементы включены) и {@link ContentCanvas} (общие для ВСЕХ гридов
     *  канваса: крупные имена/цвет текста/тень/лого). */
    public record GridRenderOptions(String displayName, Color colorEven, Color colorOdd,
                                     boolean showGrid, boolean showIds,
                                     boolean showCircle, boolean showCross, boolean showCorner,
                                     boolean showLogo, boolean showNameLabel, boolean showResolution,
                                     boolean largeGridNames, Color textColor,
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
            // Цвет -- ОБЩИЙ для экрана (см. Screen#maskColor), не с pl -- та же запись
            // экрана в разных канвасах красится одинаково (2026-08-13, баг-репорт: раньше
            // был per-placement, см. class-javadoc CanvasPlacement). 2026-09-30: сюда
            // приходит уже готовая ПАРА цветов, а не пресет -- так «Свои цвета…» не
            // требуют отдельной ветки в рендерере.
            return new GridRenderOptions(displayName, screen.maskColor(0), screen.maskColor(1),
                    pl.isShowGrid(), pl.isShowIds(),
                    pl.isShowCircle(), pl.isShowCross(), pl.isShowCorner(), pl.isShowLogo(),
                    pl.isShowNameLabel(), pl.isShowResolution(),
                    canvas.isLargeGridNames(), textColor, canvas.isDropShadow(), logo);
        }

        /** Для отдельного полноэкранного экспорта маски вне канваса (по сцене целиком,
         *  см. VisualizationStagePanel.exportMasks) — прежнее поведение по умолчанию
         *  (сетка+номера+плашка имени/разрешения, без новых элементов, без канваса), но
         *  цвет берётся из {@link Screen#maskColor(int)}, а не хардкодится в NORMAL — иначе этот путь
         *  экспорта расходился бы с тем, что реально видно на канвасе (тот же баг-репорт,
         *  что и у {@link #of}). */
        public static GridRenderOptions defaultForScreen(Screen screen) {
            return new GridRenderOptions(screen.getName(), screen.maskColor(0), screen.maskColor(1),
                    true, true, false, false, false, false,
                    true, true,
                    false, Color.WHITE, false, null);
        }
    }

    /** Шрифт по умолчанию у {@code BufferedImage.createGraphics()} — все размеры шрифтов
     *  маски выводятся из него ({@code deriveFont}). paint-методы ставят его явно: им может
     *  прийти Graphics компонента Swing (превью) со шрифтом Look&amp;Feel, и маска в превью
     *  разошлась бы с экспортом. */
    private static final Font BASE_FONT = new Font(Font.DIALOG, Font.PLAIN, 12);

    /** Маска экрана целиком в отдельном изображении — обёртка над {@link #paintMask} для
     *  тестов и мелких размеров. Большие маски — через {@link MaskImage} (полосами). */
    public static BufferedImage renderMask(Screen screen, CabinetType defaultType, Workspace workspace,
                                            GridRenderOptions opts) {
        MaskGeometry geo = MaskGeometry.of(screen, defaultType, workspace);
        BufferedImage img = new BufferedImage(geo.width(), geo.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        try {
            paintMask(g2, screen, geo, opts);
        } finally {
            g2.dispose();
        }
        return img;
    }

    /** Рисует маску экрана в ЛОКАЛЬНЫХ координатах маски ({@code 0..width × 0..height}) в
     *  переданный Graphics — без промежуточного {@code BufferedImage}.
     *
     *  <p>2026-09-30 (запрос пользователя: «убрать ограничение 16k», решение D7): раньше
     *  маска рисовалась только целиком в картинку (4 Б/px), а в маску канваса вклеивалась
     *  {@code drawImage}-ом — память ∝ площади. Теперь один и тот же код рисует и в полную
     *  картинку ({@link #renderMask}), и в полосу потоковой записи ({@link MaskImage#writePng},
     *  через {@code translate}+{@code clip}), и в уменьшенное превью (через {@code scale}).
     *  Кабинеты вне текущего clip пропускаются — при записи полосами это главный выигрыш
     *  по скорости. Состояние {@code g} (transform/clip/цвет/шрифт) не меняется. */
    public static void paintMask(Graphics2D g, Screen screen, CabinetType defaultType, Workspace workspace,
                                 GridRenderOptions opts) {
        paintMask(g, screen, MaskGeometry.of(screen, defaultType, workspace), opts);
    }

    static void paintMask(Graphics2D g, Screen screen, MaskGeometry geo, GridRenderOptions opts) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            int w = geo.width();
            int h = geo.height();
            prepare(g2);
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, w, h);

            int cellW = geo.cellW();
            int cellH = geo.cellH();
            Font cellFont = g2.getFont().deriveFont(Font.BOLD, Math.max(10f, Math.min(cellW, cellH) * 0.16f));
            // Запас вокруг ячейки для отсечения по clip: обводка сетки/меток (2 px) выходит за
            // ячейку на 1 px, а подпись номера «r,c» на мелких ячейках шире самой ячейки.
            int margin = 4 + (opts.showIds() ? (int) Math.ceil(cellFont.getSize2D() * 4) : 0);
            java.awt.Rectangle clip = g2.getClipBounds();

            for (CabinetInstance cab : screen.getCabinets()) {
                if (cab.isHidden()) {
                    continue;
                }
                int x = geo.cabinetX(cab);
                int y = geo.cabinetY(cab);
                if (clip != null && !clip.intersects(x - margin, y - margin, cellW + 2 * margin,
                        cellH + 2 * margin)) {
                    continue;
                }

                g2.setColor((cab.getRowIndex() + cab.getColIndex()) % 2 == 0 ? opts.colorEven() : opts.colorOdd());
                g2.fillRect(x, y, cellW, cellH);

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

            drawCenterLabel(g2, w, h, opts.displayName(), geo.sizeLabel(), opts);
        } finally {
            g2.dispose();
        }
    }

    /** Общие настройки Graphics для всех масок — прежде их ставил каждый render*-метод на
     *  свежей картинке; paint-методам может прийти чужой Graphics (см. {@link #BASE_FONT}). */
    private static void prepare(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(BASE_FONT);
        g2.setComposite(AlphaComposite.SrcOver);
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

    /** Плашка с именем и/или разрешением по центру маски. 2026-09-30 (запрос пользователя:
     *  «видимость плашки с именем экрана; отдельная галочка для разрешения на той же
     *  плашке»): строки включаются независимо ({@link GridRenderOptions#showNameLabel()},
     *  {@link GridRenderOptions#showResolution()}); обе выключены — плашки нет вовсе; одна
     *  строка — плашка ужимается под неё (раньше высота всегда считалась на две строки). */
    private static void drawCenterLabel(Graphics2D g2, int w, int h, String name, String resolution,
                                         GridRenderOptions opts) {
        boolean showName = opts.showNameLabel();
        boolean showRes = opts.showResolution();
        if (!showName && !showRes) {
            return;
        }
        float minNameSize = opts.largeGridNames() ? 26f : 18f;
        float nameScale = opts.largeGridNames() ? 0.065f : 0.045f;
        float nameSize = Math.max(minNameSize, w * nameScale);
        float resSize = Math.max(13f, w * 0.028f);
        Font nameFont = g2.getFont().deriveFont(Font.BOLD, nameSize);
        Font resFont = g2.getFont().deriveFont(Font.PLAIN, resSize);
        FontMetrics nameFm = g2.getFontMetrics(nameFont);
        FontMetrics resFm = g2.getFontMetrics(resFont);

        // Шрифт выше посчитан только по ширине -- на широких, но НИЗКИХ экранах
        // (напр. строка 3840x128) это даёт огромный текст, который по высоте не
        // влезает в кадр вообще (плашка вылезает за границы маски). Если посчитанная
        // так высота плашки не влезает в h -- пропорционально уменьшаем оба шрифта,
        // пока не впишется (с небольшим запасом).
        int fitBoxH = labelBoxHeight(nameFm, resFm, showName, showRes);
        float maxBoxH = h * 0.9f;
        if (fitBoxH > maxBoxH && fitBoxH > 0) {
            float shrink = maxBoxH / fitBoxH;
            nameFont = g2.getFont().deriveFont(Font.BOLD, Math.max(8f, nameSize * shrink));
            resFont = g2.getFont().deriveFont(Font.PLAIN, Math.max(6f, resSize * shrink));
            nameFm = g2.getFontMetrics(nameFont);
            resFm = g2.getFontMetrics(resFont);
        }

        int nameW = showName ? nameFm.stringWidth(name) : 0;
        int resW = showRes ? resFm.stringWidth(resolution) : 0;
        int boxW = Math.max(nameW, resW) + 60;
        int boxH = labelBoxHeight(nameFm, resFm, showName, showRes);
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

        int cursorY = boxY + 14;
        if (showName) {
            int nameX = (w - nameW) / 2;
            int nameY = cursorY + nameFm.getAscent();
            if (opts.dropShadow()) {
                g2.setColor(new Color(0, 0, 0, 200));
                g2.setFont(nameFont);
                g2.drawString(name, nameX + 2, nameY + 2);
            }
            g2.setColor(opts.textColor());
            g2.setFont(nameFont);
            g2.drawString(name, nameX, nameY);
            cursorY += nameFm.getHeight() + 6;
        }
        if (showRes) {
            int resX = (w - resW) / 2;
            int resY = cursorY + resFm.getAscent();
            g2.setColor(new Color(0xc0, 0xc8, 0xd0));
            g2.setFont(resFont);
            g2.drawString(resolution, resX, resY);
        }
    }

    /** Высота плашки под включённые строки. Для двух строк формула прежняя
     *  ({@code nameH + resH + 36}), чтобы уже сгенерированные маски не поменяли вид;
     *  для одной — её высота плюс те же поля (14 сверху + 14 снизу + запас). */
    static int labelBoxHeight(FontMetrics nameFm, FontMetrics resFm, boolean showName, boolean showRes) {
        if (showName && showRes) {
            return nameFm.getHeight() + resFm.getHeight() + 36;
        }
        return (showName ? nameFm.getHeight() : resFm.getHeight()) + 30;
    }

    /** Маска целого канваса (компоновки контента): чёрный кадр размером с канвас,
     *  в него вклеены маски каждого размещённого экрана на своих позициях, каждая —
     *  со своими настройками (см. {@link GridRenderOptions#of}) — так же используется
     *  как основа под пресеты медиасерверов/Resolume.
     *
     * <p>Баг-репорт 2026-09-30: «Экспорт масок» (кнопка со ВСЕМИ канвасами ВСЕХ сцен
     *  проекта, {@code VisualizationStagePanel#exportMasks}) и «Сформировать пакет
     *  документации» (та же ситуация, {@code OutputStagePanel}) рисовали канвасы чужих
     *  сцен ПУСТЫМИ (чёрный прямоугольник без единого экрана) — метод брал {@code
     *  model.getCurrentScene()} вместо сцены, которой РЕАЛЬНО принадлежит {@code canvas},
     *  и {@code pl.getScreenId()} канваса не находил экрана в чужой текущей сцене.
     *  Сцену теперь передают явно, как уже сделано в {@link #renderCanvasGapMask}/
     *  {@link #renderCanvasOverlay} — те же грабли туда просто не успели попасть. */
    public static BufferedImage renderCanvasMask(ContentCanvas canvas, Scene scene, AppModel model,
                                                  SettingsManager settings) {
        int w = Math.max(1, canvas.getWidthPx());
        int h = Math.max(1, canvas.getHeightPx());
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        try {
            paintCanvasMask(g2, w, h, resolvePlacements(canvas, scene, model, settings));
        } finally {
            g2.dispose();
        }
        return img;
    }

    /** Экран, размещённый на канвасе, с уже посчитанной геометрией и настройками маски.
     *  Считается ОДИН раз на задание экспорта ({@link MaskImage}), а не на каждую полосу:
     *  {@link GridRenderOptions#of} читает файл лого с диска. */
    record PlacedScreen(CanvasPlacement placement, Screen screen, MaskGeometry geo, GridRenderOptions opts) {
    }

    /** Размещения канваса, чьи экраны есть в {@code scene} (удалённые экраны пропускаются,
     *  как и раньше). {@code settings == null} — без настроек маски (для пустот/оверлея). */
    static List<PlacedScreen> resolvePlacements(ContentCanvas canvas, Scene scene, AppModel model,
                                                SettingsManager settings) {
        List<PlacedScreen> out = new ArrayList<>();
        for (CanvasPlacement pl : canvas.getPlacements()) {
            Screen scr = screenById(scene, pl.getScreenId());
            if (scr == null) {
                continue;
            }
            MaskGeometry geo = MaskGeometry.of(scr, model.typeOf(scr), model.getWorkspace());
            GridRenderOptions opts = settings != null ? GridRenderOptions.of(scr, pl, canvas, settings) : null;
            out.add(new PlacedScreen(pl, scr, geo, opts));
        }
        return out;
    }

    /** Маска канваса в координатах канваса — см. {@link #renderCanvasMask}. Маски экранов
     *  рисуются ПРЯМО в канвас ({@code translate} + {@code clip} по прямоугольнику маски — то,
     *  что раньше обрезала отдельная картинка экрана, обрезается так же; чёрная подложка под
     *  скрытыми кабинетами — внутри {@link #paintMask}, как раньше), без промежуточного
     *  {@code BufferedImage} размером с экран (2026-09-30, решение D7). Размещения вне clip
     *  пропускаются. */
    public static void paintCanvasMask(Graphics2D g, ContentCanvas canvas, Scene scene, AppModel model,
                                       SettingsManager settings) {
        paintCanvasMask(g, Math.max(1, canvas.getWidthPx()), Math.max(1, canvas.getHeightPx()),
                resolvePlacements(canvas, scene, model, settings));
    }

    static void paintCanvasMask(Graphics2D g, int w, int h, List<PlacedScreen> placed) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            prepare(g2);
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, w, h);

            Font offsetFont = g2.getFont().deriveFont(Font.PLAIN, 11f);
            FontMetrics offsetFm = g2.getFontMetrics(offsetFont);
            java.awt.Rectangle clip = g2.getClipBounds();
            for (PlacedScreen ps : placed) {
                CanvasPlacement pl = ps.placement();
                int sw = ps.geo().width();
                int sh = ps.geo().height();
                String offsetLabel = pl.getX() + ", " + pl.getY() + " px";
                int labelW = offsetFm.stringWidth(offsetLabel);
                int labelH = offsetFont.getSize() + 6;
                if (clip != null && !clip.intersects(pl.getX(), pl.getY(), Math.max(sw, labelW + 8),
                        Math.max(sh, labelH))) {
                    continue;
                }
                Graphics2D gs = (Graphics2D) g2.create();
                try {
                    gs.translate(pl.getX(), pl.getY());
                    gs.clipRect(0, 0, sw, sh);
                    paintMask(gs, ps.screen(), ps.geo(), ps.opts());
                } finally {
                    gs.dispose();
                }

                // Оффсет экрана в пикселях канваса — вместо общего названия/разрешения
                // канваса в углу (контентщику нужна именно позиция каждого экрана).
                // Рисуем на непрозрачной подложке: без неё текст сливался с пиксельной
                // сеткой и номером кабинета «1,1» в том же углу и был нечитаем.
                g2.setFont(offsetFont);
                g2.setColor(new Color(0, 0, 0, 210));
                g2.fillRect(pl.getX(), pl.getY(), labelW + 8, labelH);
                g2.setColor(Color.WHITE);
                g2.drawString(offsetLabel, pl.getX() + 4, pl.getY() + offsetFont.getSize() + 2);
            }
        } finally {
            g2.dispose();
        }
    }

    /** Маска «пустоты» канваса для After Effects — по образцу pixl Grid (пользователь
     *  прислал его экспорт как эталон): PNG размером с канвас, непрозрачно-чёрный там, где
     *  НЕТ ни одного видимого кабинета, и прозрачный поверх экранов. Лежит в композиции
     *  канваса верхним guide-слоем, чтобы контентщик видел, какие зоны канваса никуда не
     *  выводятся. Вырезается по видимым кабинетам (тот же расчёт cabX/cabY, что в
     *  {@link #renderMask}), а не по прямоугольнику экрана — скрытые кабинеты тоже «дыры». */
    public static BufferedImage renderCanvasGapMask(ContentCanvas canvas, Scene scene, AppModel model) {
        int w = Math.max(1, canvas.getWidthPx());
        int h = Math.max(1, canvas.getHeightPx());
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        try {
            paintCanvasGapMask(g2, w, h, resolvePlacements(canvas, scene, model, null));
        } finally {
            g2.dispose();
        }
        return img;
    }

    /** То же, что {@link #renderCanvasGapMask}, рисованием в Graphics (2026-09-30, D7: запись
     *  полосами). Рисовать в изображение С АЛЬФОЙ: «дыры» над кабинетами вырезаются
     *  {@link AlphaComposite#Clear}. */
    public static void paintCanvasGapMask(Graphics2D g, ContentCanvas canvas, Scene scene, AppModel model) {
        paintCanvasGapMask(g, Math.max(1, canvas.getWidthPx()), Math.max(1, canvas.getHeightPx()),
                resolvePlacements(canvas, scene, model, null));
    }

    static void paintCanvasGapMask(Graphics2D g, int w, int h, List<PlacedScreen> placed) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, w, h);
            g2.setComposite(AlphaComposite.Clear);
            java.awt.Rectangle clip = g2.getClipBounds();
            for (PlacedScreen ps : placed) {
                // Размеры ячейки -- только через MaskGeometry (2026-09-30: экран-«сетка» даёт
                // ячейки выше в N раз, и «дыры» должны совпадать с ними, а не с реальным разрешением).
                MaskGeometry geo = ps.geo();
                CanvasPlacement pl = ps.placement();
                for (CabinetInstance cab : ps.screen().getCabinets()) {
                    if (cab.isHidden()) {
                        continue;
                    }
                    int x = pl.getX() + geo.cabinetX(cab);
                    int y = pl.getY() + geo.cabinetY(cab);
                    if (clip != null && !clip.intersects(x, y, geo.cellW(), geo.cellH())) {
                        continue;
                    }
                    g2.fillRect(x, y, geo.cellW(), geo.cellH());
                }
            }
        } finally {
            g2.dispose();
        }
    }

    /** Оверлей-«курсор» канваса для After Effects — по образцу pixl Grid: прозрачный PNG
     *  размером с канвас, тонкая рамка канваса с подписью его разрешения и у каждого экрана —
     *  рамка, имя с разрешением и координата левого верхнего угла «TL:x,y». Только
     *  справочный guide-слой, в рендер не попадает. */
    public static BufferedImage renderCanvasOverlay(ContentCanvas canvas, Scene scene, AppModel model) {
        int w = Math.max(1, canvas.getWidthPx());
        int h = Math.max(1, canvas.getHeightPx());
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        try {
            paintCanvasOverlay(g2, w, h, resolvePlacements(canvas, scene, model, null));
        } finally {
            g2.dispose();
        }
        return img;
    }

    /** То же, что {@link #renderCanvasOverlay}, рисованием в Graphics (2026-09-30, D7). Фон
     *  не заливается — рисовать в прозрачное изображение. */
    public static void paintCanvasOverlay(Graphics2D g, ContentCanvas canvas, Scene scene, AppModel model) {
        paintCanvasOverlay(g, Math.max(1, canvas.getWidthPx()), Math.max(1, canvas.getHeightPx()),
                resolvePlacements(canvas, scene, model, null));
    }

    static void paintCanvasOverlay(Graphics2D g, int w, int h, List<PlacedScreen> placed) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            prepare(g2);
            Font font = g2.getFont().deriveFont(Font.PLAIN, 14f);
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            int lineH = fm.getHeight();
            g2.setStroke(new BasicStroke(1f));

            g2.setColor(new Color(255, 255, 255, 200));
            g2.drawRect(0, 0, w - 1, h - 1);
            String canvasLabel = "Canvas: " + w + "px x " + h + "px";
            drawLabel(g2, fm, canvasLabel, 0, 0);

            for (PlacedScreen ps : placed) {
                CanvasPlacement pl = ps.placement();
                Screen scr = ps.screen();
                int sw = ps.geo().width();
                int sh = ps.geo().height();
                g2.setColor(new Color(255, 255, 255, 160));
                g2.drawRect(pl.getX(), pl.getY(), sw - 1, sh - 1);
                // Подписи экрана — второй/третьей строкой: первая строка в (0,0) занята
                // подписью канваса, и у экрана в левом верхнем углу они бы наложились.
                int ty = pl.getY() + lineH + 2;
                drawLabel(g2, fm, scr.getName() + " " + sw + "x" + sh, pl.getX(), ty);
                drawLabel(g2, fm, "TL:" + pl.getX() + "," + pl.getY(), pl.getX(), ty + lineH + 2);
            }
        } finally {
            g2.dispose();
        }
    }

    /** Подпись на полупрозрачной тёмной плашке — иначе белый текст не читается поверх
     *  светлого контента в AE. */
    private static void drawLabel(Graphics2D g2, FontMetrics fm, String text, int x, int y) {
        int tw = fm.stringWidth(text);
        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRect(x, y, tw + 8, fm.getHeight() + 2);
        g2.setColor(Color.WHITE);
        g2.drawString(text, x + 4, y + 1 + fm.getAscent());
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
