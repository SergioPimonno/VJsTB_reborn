package com.vjstb.ledscheme.ui.receiver;

import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.ConnectionMapTables;
import com.vjstb.ledscheme.service.ReceiverCardChecks;
import com.vjstb.ledscheme.service.ScreenModules;
import com.vjstb.ledscheme.service.SpecXlsxWriter;
import com.vjstb.ledscheme.ui.OutputPaths;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * «Карта подключения модулей» — часть «Состава вывода» (docs/receiver-cards/PLAN.md, раздел 7, трек E): на каждый
 * МОДУЛЬНЫЙ экран сцены — картинка «Приёмные карты» (какой модуль в какую карту и под каким номером; плашка места
 * карты) и картинка «Блоки питания» (области блоков, номера шлейфов) плюс одна таблица {@code .xlsx} на сцену
 * ({@link ConnectionMapTables}). Картинки рисуют те же статические {@link ReceiverCardsCanvas#render} и
 * {@link PowerSuppliesCanvas#render}, что и виды приложения, — схема в файле не может разойтись с тем, что видит
 * пользователь; но в {@link ModuleViewStyle#print() печатном стиле} (белый фон, тёмный текст) независимо от темы
 * интерфейса: карту подключения печатают и несут технику, который выставляет номера модулей в NovaLCT (.rcfgx
 * приложение не пишет, поэтому эта схема — единственный носитель порядка модулей в карте).
 *
 * <p>Формат картинки (JPG/PNG/WebP/PDF), DPI и сжатие выбирает вызывающий через {@link ImageSink}: часть идёт через тот
 * же {@code writeScheme} «Вывода», что и схемы расключения. Сцена без модульных экранов не даёт ни одного файла.
 */
public final class ConnectionMapExport {

    /** Запись одной картинки в выбранном пользователем формате ({@code OutputStagePanel#writeScheme}). */
    @FunctionalInterface
    public interface ImageSink {
        void write(BufferedImage img, File dir, String baseName) throws IOException;
    }

    /** Отметка очередного шага для окна прогресса ({@code ExportProgressDialog#step}). */
    @FunctionalInterface
    public interface Steps {
        void step(String what);
    }

    /** Папка части внутри папки сцены. */
    public static final String FOLDER_NAME = "Карта подключения";

    /** Предельная сторона картинки, px: WebP/PDF-страницы и просмотрщики не любят больше, а клетка мельче этого всё
     *  равно не читается. */
    private static final double MAX_SIDE_PX = 7000;

    private ConnectionMapExport() {
    }

    /** Модульные экраны сцены в порядке списка. */
    public static List<Screen> modularScreens(Scene scene, Workspace ws) {
        List<Screen> out = new ArrayList<>();
        if (scene != null && ws != null) {
            for (Screen s : scene.getScreens()) {
                if (ScreenModules.of(s, ws).isModular()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** true — в проекте есть хоть один модульный экран (иначе часть «Карта подключения» не показывается). */
    public static boolean projectHasModularScreen(Project project, Workspace ws) {
        if (project != null) {
            for (Scene scene : project.getScenes()) {
                if (!modularScreens(scene, ws).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Сколько шагов прогресса займёт сцена: две картинки на модульный экран и одна таблица. */
    public static int stepsFor(Scene scene, Workspace ws) {
        int n = modularScreens(scene, ws).size();
        return n == 0 ? 0 : n * 2 + 1;
    }

    /**
     * Выгружает карту подключения сцены в {@code <папка сцены>/Карта подключения/}.
     *
     * @return число записанных файлов (картинки и таблица); 0 — в сцене нет модульных экранов
     */
    public static int export(Scene scene, Workspace ws, ReceiverCardNumbering numbering, String numberFormat,
                             File sceneFolder, double dpiScale, ImageSink sink, Steps steps) throws IOException {
        List<Screen> screens = modularScreens(scene, ws);
        if (screens.isEmpty()) {
            return 0;
        }
        File dir = new File(sceneFolder, FOLDER_NAME);
        dir.mkdirs();
        int files = 0;
        Set<String> used = new HashSet<>();
        for (Screen screen : screens) {
            String base = OutputPaths.sanitize(screen.getName());
            for (int k = 2; !used.add(base); k++) {
                base = OutputPaths.sanitize(screen.getName()) + " (" + k + ")";
            }
            if (steps != null) {
                steps.step(scene.getName() + " · " + screen.getName() + " · приёмные карты");
            }
            sink.write(renderCards(scene, screen, ws, numbering, numberFormat, dpiScale), dir, base + " Карты");
            files++;
            if (steps != null) {
                steps.step(scene.getName() + " · " + screen.getName() + " · блоки питания");
            }
            sink.write(renderSupplies(scene, screen, ws, numbering, dpiScale), dir, base + " Блоки питания");
            files++;
        }
        if (steps != null) {
            steps.step(scene.getName() + " · таблицы карты подключения");
        }
        Workbook wb = SpecXlsxWriter.newWorkbook();
        ConnectionMapTables.cards(screens, ws, numbering, numberFormat).addTo(wb);
        ConnectionMapTables.supplies(screens, ws, numbering).addTo(wb);
        SpecXlsxWriter.write(wb, new File(dir, OutputPaths.sanitize(scene.getName()) + " Карта подключения.xlsx"));
        return files + 1;
    }

    // ------------------------------------------------------------------ картинки

    /** Картинка «Приёмные карты» экрана в печатном стиле. */
    public static BufferedImage renderCards(Scene scene, Screen screen, Workspace ws, ReceiverCardNumbering numbering,
                                            String numberFormat, double dpiScale) {
        ReceiverCardsCanvas.Data data = ReceiverCardsCanvas.Data.of(screen, ws, numbering, numberFormat);
        ReceiverCardChecks.Result checks = data.checks();
        int problems = 0;
        for (ReceiverCardChecks.CardCheck c : checks.cards()) {
            problems += c.hasProblem() ? 1 : 0;
        }
        String subtitle = "Карт: " + data.cards().size() + " · модулей: " + data.modules().modules().size()
                + (checks.modulesWithoutCard().isEmpty() ? ""
                : " · без карты: " + checks.modulesWithoutCard().size())
                + (problems == 0 ? "" : " · карт с замечаниями: " + problems);
        String legend = "На модуле — номер его выхода на карте (в обозначении по Предпочтениям), плашка — место карты."
                + " Оранжевым — модуль без карты, красным — выход сверх числа выходов карты и шлейф длиннее"
                + " допустимого. Порядок модулей внутри карты в файл NovaLCT (.rcfgx) не попадает — его выставляют"
                + " в NovaLCT по этой схеме.";
        return renderPage(scene, screen, ws, dpiScale, "приёмные карты", subtitle, legend,
                (g, geo, style) -> ReceiverCardsCanvas.render(g, data, ReceiverCardsCanvas.Options.NONE, geo, style));
    }

    /** Картинка «Блоки питания» экрана в печатном стиле. */
    public static BufferedImage renderSupplies(Scene scene, Screen screen, Workspace ws,
                                               ReceiverCardNumbering numbering, double dpiScale) {
        PowerSuppliesCanvas.Data data = PowerSuppliesCanvas.Data.of(screen, ws, numbering);
        int over = 0;
        for (var sup : data.checks().layout().supplies()) {
            over += sup.hasProblem() ? 1 : 0;
        }
        String subtitle = "Блоков: " + data.checks().layout().supplies().size() + " · модулей: "
                + data.modules().modules().size()
                + (data.checks().modulesWithoutSupply().isEmpty() ? ""
                : " · без питания: " + data.checks().modulesWithoutSupply().size())
                + (over == 0 ? "" : " · блоков с замечаниями: " + over);
        String legend = "На модуле — номер его шлейфа в блоке; контур и цвет области — какой блок питает; плашка «БПn» —"
                + " место блока («+К» — блок питает приёмную карту). Оранжевым — модуль без питания, красным —"
                + " перегруз блока и кабель длиннее допустимого.";
        return renderPage(scene, screen, ws, dpiScale, "блоки питания", subtitle, legend,
                (g, geo, style) -> PowerSuppliesCanvas.render(g, data, PowerSuppliesCanvas.Options.NONE, geo, style));
    }

    @FunctionalInterface
    private interface GridPainter {
        void paint(Graphics2D g, ModuleGridGeometry geo, ModuleViewStyle style);
    }

    /** Страница: заголовок, строка итогов, сетка экрана, пояснение внизу. Размер сетки — по пропорции клетки типа
     *  кабинета, но не крупнее {@link #MAX_SIDE_PX} по любой стороне. */
    private static BufferedImage renderPage(Scene scene, Screen screen, Workspace ws, double dpiScale, String what,
                                            String subtitle, String legend, GridPainter painter) {
        int rows = Math.max(1, screen.getRows());
        int cols = Math.max(1, screen.getCols());
        double aspect = ModuleGridGeometry.aspectOf(screen, ws);
        double scale = Math.max(1.0, dpiScale);
        double cellW = Math.max(14, Math.min(56 * scale, MAX_SIDE_PX / cols));
        double cellH = cellW / aspect;
        if (cellH * rows > MAX_SIDE_PX) {
            cellH = MAX_SIDE_PX / rows;
            cellW = Math.max(1, cellH * aspect);
        }
        double k = Math.max(0.8, Math.min(3.0, cellW / 56.0));
        int margin = (int) Math.round(24 * k);
        int gridW = (int) Math.ceil(cellW * cols);
        int gridH = (int) Math.ceil(cellH * rows);
        int width = Math.max(gridW + 2 * margin, (int) Math.round(560 * k));
        ModuleViewStyle style = ModuleViewStyle.print();

        // Высоту текста нужно знать до создания картинки, поэтому меряем на временной.
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D pg = probe.createGraphics();
        Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.round(18 * k));
        Font textFont = new Font(Font.SANS_SERIF, Font.PLAIN, (int) Math.round(12 * k));
        List<String> legendLines = wrap(pg.getFontMetrics(textFont), legend, width - 2 * margin);
        int lineH = pg.getFontMetrics(textFont).getHeight();
        int titleH = pg.getFontMetrics(titleFont).getHeight();
        pg.dispose();

        int top = margin + titleH + lineH + margin / 2;
        int height = top + gridH + margin / 2 + legendLines.size() * lineH + margin;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(style.background);
            g.fillRect(0, 0, width, height);
            g.setColor(style.text);
            g.setFont(titleFont);
            g.drawString("Сцена «" + scene.getName() + "» · экран «" + screen.getName() + "» — " + what, margin,
                    margin + g.getFontMetrics().getAscent());
            g.setFont(textFont);
            g.setColor(style.mutedText);
            g.drawString(subtitle, margin, margin + titleH + g.getFontMetrics().getAscent());
            ModuleGridGeometry geo = ModuleGridGeometry.of((width - gridW) / 2.0, top, cellW, cellH, rows, cols);
            painter.paint(g, geo, style);
            g.setFont(textFont);
            g.setColor(style.mutedText);
            int y = top + gridH + margin / 2 + g.getFontMetrics().getAscent();
            for (String line : legendLines) {
                g.drawString(line, margin, y);
                y += lineH;
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    /** Перенос по словам под ширину {@code maxWidth}. */
    private static List<String> wrap(FontMetrics fm, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && fm.stringWidth(candidate) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** Цвет фона страницы (для тестов: углы картинки обязаны быть белыми независимо от темы). */
    static Color pageBackground() {
        return ModuleViewStyle.print().background;
    }
}
