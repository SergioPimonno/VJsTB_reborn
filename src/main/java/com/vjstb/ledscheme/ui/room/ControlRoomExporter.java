package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.room.RoomSpecText;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Картинки раскладки «Планера пультовой» для этапа «Вывод»: те же виды, что в окне инструмента, но без выбора, ручек
 * и подсказок, с заголовком (сцена, раскладка, вид). Рисуют те же классы, что и окно ({@link PlanView},
 * {@link ElevationView}, {@link RacksPanel}), поэтому картинка в документации совпадает с тем, что видно в планере;
 * оформление — как у остальных схем при экспорте: вызывающий оборачивает вызов в {@code ExportRendering.run}
 * (печатный стиль включает светлую палитру, и темы видов подхватывают её при создании).
 *
 * <p>Размер картинки — от размера площадки: {@link #PX_PER_CM} пикселей на сантиметр при 72 dpi, умноженные на
 * масштаб качества экспорта ({@code dpi / 72}); стойки рисуются по естественному размеру панели, тоже с этим масштабом.
 */
public final class ControlRoomExporter {

    /** Пикселей на сантиметр плана и вида спереди при 72 dpi: 7-метровая площадка ≈ 1100 px шириной. */
    static final double PX_PER_CM = 1.2;

    /** Вид раскладки, который можно выгрузить в картинку. */
    public enum View {
        RACKS_FRONT("Стойки — лицевые панели", "Стойки"),
        RACKS_TOP("Стойки — вид сверху (глубина)", "Стойки сверху"),
        PLAN("План площадки — вид сверху", "План"),
        ELEVATION("Вид спереди — высоты и линия глаз", "Вид спереди");

        private final String label;
        private final String fileTag;

        View(String label, String fileTag) {
            this.label = label;
            this.fileTag = fileTag;
        }

        /** Название в диалоге выбора. */
        public String label() {
            return label;
        }

        /** Короткая подпись для имени файла и заголовка картинки. */
        public String fileTag() {
            return fileTag;
        }

        /** Есть ли что рисовать: стойки — только если в раскладке есть башни. */
        public boolean applicableTo(ControlRoomLayout layout) {
            return (this != RACKS_FRONT && this != RACKS_TOP) || !layout.getTowers().isEmpty();
        }
    }

    private ControlRoomExporter() {
    }

    /**
     * Картинка одного вида раскладки с заголовком. {@code dpiScale} — качество экспорта ({@code dpi / 72}).
     * Вызывать на потоке интерфейса и в оформлении экспорта (см. описание класса).
     */
    public static BufferedImage render(AppModel model, String sceneName, ControlRoomLayout layout, View view,
                                       double dpiScale) {
        PlannerSession session = new PlannerSession(model);
        session.setLayout(layout);
        RoomTheme theme = RoomTheme.current();
        double s = PX_PER_CM * Math.max(0.5, dpiScale);
        BufferedImage body = switch (view) {
            case PLAN -> {
                Dimension d = PlanView.sizeFor(layout, s);
                PlanView plan = new PlanView(session);
                yield paint(d, g -> plan.paint(g, d.width, d.height), theme);
            }
            case ELEVATION -> {
                Dimension d = ElevationView.sizeFor(layout, s);
                ElevationView elev = new ElevationView(session);
                yield paint(d, g -> elev.paint(g, d.width, d.height), theme);
            }
            case RACKS_FRONT, RACKS_TOP -> {
                RacksPanel racks = new RacksPanel(session, null);
                racks.setTopView(view == View.RACKS_TOP);
                Dimension natural = racks.getPreferredSize();
                racks.setSize(natural);
                Dimension d = new Dimension((int) Math.ceil(natural.width * Math.max(0.5, dpiScale)),
                        (int) Math.ceil(natural.height * Math.max(0.5, dpiScale)));
                double k = Math.max(0.5, dpiScale);
                yield paint(d, g -> {
                    g.scale(k, k);
                    racks.paint(g);
                }, theme);
            }
        };
        return withTitle(body, sceneName, layout, view, theme, dpiScale);
    }

    /** Спецификация раскладки текстом — то же, что кнопка «Копировать спецификацию» в окне планера. */
    public static String specText(AppModel model, ControlRoomLayout layout) {
        PlannerSession session = new PlannerSession(model);
        session.setLayout(layout);
        return RoomSpecText.of(layout, session.catalog(), session.calc());
    }

    private static BufferedImage paint(Dimension size, java.util.function.Consumer<Graphics2D> body, RoomTheme theme) {
        BufferedImage img = new BufferedImage(Math.max(1, size.width), Math.max(1, size.height),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(theme.panel);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            body.accept(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    /** Заголовок над картинкой: «Планер пультовой · сцена · раскладка · вид» и сводка расчёта. */
    private static BufferedImage withTitle(BufferedImage body, String sceneName, ControlRoomLayout layout, View view,
                                           RoomTheme theme, double dpiScale) {
        float k = (float) Math.max(0.5, dpiScale);
        int pad = Math.round(10 * k);
        Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(15 * k));
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D pg = probe.createGraphics();
        FontMetrics fm = pg.getFontMetrics(titleFont);
        pg.dispose();
        int bar = fm.getHeight() + pad * 2;
        BufferedImage out = new BufferedImage(body.getWidth(), body.getHeight() + bar, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(theme.panel);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.drawImage(body, 0, bar, null);
            g.setColor(theme.ink);
            g.setFont(titleFont);
            String title = "Планер пультовой · " + sceneName + " · " + layout.getName() + " · " + view.fileTag();
            g.drawString(title, pad, pad + fm.getAscent());
            g.setColor(theme.line);
            g.drawLine(0, bar - 1, out.getWidth(), bar - 1);
        } finally {
            g.dispose();
        }
        return out;
    }
}
