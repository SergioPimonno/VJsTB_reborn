package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetShape;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.ScreenStats;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Отрисовка схемы экрана (сетка кабинетов + цепочки). Используется и на холсте,
 * и при экспорте в изображение. Активная (строящаяся) цепочка здесь не рисуется —
 * это транзиентное состояние редактирования.
 */
public final class SchemeRenderer {

    private SchemeRenderer() {
    }

    /** Размер ячейки кабинета по соотношению сторон типа. */
    public static Dimension cellSize(CabinetType type, int base) {
        double ratio = type != null && type.getHeightMm() > 0 ? type.getWidthMm() / type.getHeightMm() : 1;
        int w = ratio >= 1 ? base : (int) Math.round(base * ratio);
        int h = ratio >= 1 ? (int) Math.round(base / ratio) : base;
        return new Dimension(Math.max(w, 24), Math.max(h, 24));
    }

    /** То же, но с указанием workspace — тогда для ячеек с переопределённым типом
     *  метка формы (форма кабинета) рисуется по фактическому типу ячейки.
     *  powerChains/signalChains — цепочки ВСЕЙ СЦЕНЫ этого экрана (не только его
     *  собственные — цепочки хранятся на уровне сцены, см. Task #78, «независимый
     *  менеджер цепочек»): цепочка физически может продолжаться на другой экран той
     *  же сцены, а drawChain сам пропускает пары кабинетов, не найденные на ЭТОМ
     *  scr — поэтому передача сюда ПОЛНОГО списка сцены (а не отфильтрованного по
     *  экрану) автоматически рисует ровно тот кусок каждой цепочки, что физически
     *  относится к этому экрану, и даёт одинаковый цвет цепочки на всех экранах,
     *  которые она затрагивает (цвет = индекс в ОБЩЕМ списке сцены). Раньше экран,
     *  где цепочка не хранилась целиком, вообще не рисовал свою часть — приходилось
     *  дорисовывать её отдельным проходом в CanvasPanel/SceneCanvasPanel. */
    public static void paintScheme(Graphics2D g2, Screen scr, CabinetType type, boolean power,
                                   int cellW, int cellH, int offX, int offY, Workspace workspace,
                                   List<PowerChain> powerChains, List<SignalChain> signalChains) {
        // Подпись «строка,столбец» физически не помещается в мелкую ячейку (мини-
        // обзор сцены с несколькими экранами целиком, сильный зум-аут) — рисуем её,
        // только если в ячейке реально есть место, иначе текст соседних кабинетов
        // наезжает друг на друга и на линии цепочек, превращаясь в нечитаемое пятно.
        boolean showLabels = cellH >= 16 && cellW >= 16;
        Font labelFont = showLabels ? g2.getFont().deriveFont(Font.PLAIN, Math.max(9f, cellH * 0.14f)) : null;
        for (CabinetInstance cab : scr.getCabinets()) {
            int x = offX + cab.getColIndex() * cellW;
            int y = offY + cab.getRowIndex() * cellH;
            Color fill = power ? Palette.phaseColor(cab.getPhase()) : Palette.PHASE_NONE;
            g2.setColor(cab.isHidden() ? blend(fill, Palette.BG, 0.82f) : fill);

           // g2.fillRect(x, y, cellW, cellH);
            g2.setColor(Palette.BORDER);
            g2.drawRect(x, y, cellW, cellH);

            if (!cab.isHidden()) {
                CabinetType effective = type;
                if (workspace != null && cab.getCabinetTypeId() != null) {
                    CabinetType override = workspace.cabinetTypeById(cab.getCabinetTypeId());
                    if (override != null) {
                        effective = override;
                    }
                }
                CabinetShape shape = cab.getShapeOverride() != null ? cab.getShapeOverride()
                        : (effective != null ? effective.getShape() : null);
                if (shape != null && shape != CabinetShape.RECTANGLE) {
                    drawShapeMarker(g2, x, y, cellW, cellH, shape);
                }
            }

            if (showLabels) {
                g2.setColor(cab.isHidden() ? Palette.MUTED : new Color(0xc0, 0xc8, 0xd0));
                g2.setFont(labelFont);
                g2.drawString(cab.getDisplayRow() + "," + cab.getDisplayCol(), x + 4, y + labelFont.getSize() + 2);
            }
        }

        if (power) {
            for (PowerChain chain : powerChains) {
                // Метка фазы — только у НАЧАЛА цепочки: питание не закольцовывается
                // (в отличие от сигнала с резервным портом на другом конце), поэтому
                // дублировать фазу на последнем кабинете незачем.
                drawChain(g2, scr, chain.getCabinetInstanceIds(), Palette.phaseColor(chain.getPhase()),
                        false, cellW, cellH, offX, offY, "L" + chain.getPhase(), null);
            }
        } else {
            for (int i = 0; i < signalChains.size(); i++) {
                SignalChain chain = signalChains.get(i);
                drawChain(g2, scr, chain.getCabinetInstanceIds(), Palette.signalColor(i),
                        false, cellW, cellH, offX, offY, signalChainLabel(scr, chain, workspace),
                        signalChainEndLabel(scr, chain, workspace));
            }
        }
    }

    /** Порт -> контроллер, которому он принадлежит (порты нумеруются подряд по
     *  назначенным экрану контроллерам: 1..N1 — первый, N1+1..N1+N2 — второй и т.д.).
     *  null, если контроллеров нет (порты вручную) или порт вне диапазона. */
    private static com.vjstb.ledscheme.model.ControllerInstance controllerForPort(
            Screen scr, Workspace workspace, int port) {
        if (workspace == null) {
            return null;
        }
        int offset = 0;
        for (com.vjstb.ledscheme.model.ControllerInstance ci : scr.getControllers()) {
            com.vjstb.ledscheme.model.ControllerType t = workspace.controllerTypeById(ci.getControllerTypeId());
            int count = t != null ? t.effectivePortCount() : 0;
            if (port > offset && port <= offset + count) {
                return ci;
            }
            offset += count;
        }
        return null;
    }

    /** Опознавательная подпись порта: номер порта, и — если на экране назначено
     *  НЕСКОЛЬКО контроллеров (иначе принадлежность и так однозначна) — номер
     *  контроллера, которому этот порт принадлежит. */
    static String portLabel(Screen scr, Workspace workspace, int port) {
        if (scr.getControllers().size() <= 1) {
            return "P" + port;
        }
        com.vjstb.ledscheme.model.ControllerInstance ci = controllerForPort(scr, workspace, port);
        int idx = ci != null ? scr.getControllers().indexOf(ci) + 1 : 0;
        return (idx > 0 ? "C" + idx + "·" : "") + "P" + port;
    }

    /** Метка НАЧАЛА сигнальной цепочки (первый кабинет) — основной порт. */
    static String signalChainLabel(Screen scr, SignalChain chain, Workspace workspace) {
        Integer port = chain.getPortNumber();
        return port == null ? null : portLabel(scr, workspace, port);
    }

    /** Метка КОНЦА сигнальной цепочки (последний кабинет): если у порта назначен
     *  резервный порт — это конец цепочки, где подключён резерв, поэтому метка
     *  показывает резервный порт, а не повторяет основной. */
    static String signalChainEndLabel(Screen scr, SignalChain chain, Workspace workspace) {
        Integer backupPort = chain.getBackupPortNumber();
        return backupPort != null ? portLabel(scr, workspace, backupPort) : signalChainLabel(scr, chain, workspace);
    }

    /** Рисует одну цепочку линиями между центрами кабинетов со стрелками направления —
     *  без опознавательной метки на концах (для строящейся, ещё не сохранённой
     *  цепочки — ей рисовать метку рано, у неё пока нет фиксированного порта/фазы
     *  «на бумаге»). */
    public static void drawChain(Graphics2D g2, Screen scr, List<String> ids, Color color, boolean dashed,
                                 int cellW, int cellH, int offX, int offY) {
        drawChain(g2, scr, ids, color, dashed, cellW, cellH, offX, offY, null);
    }

    /** То же самое, плюс опознавательный кружок с меткой (фаза L1/L2/L3 для питания;
     *  порт, и контроллер — если их несколько на экране, для сигнала) на первом и
     *  последнем кабинете цепочки — иначе на общей схеме с несколькими цепочками
     *  не видно, какая линия куда физически подключена, только цвет.
     *  Толщина линии и размер стрелки масштабируются вниз для мелких ячеек (мини-
     *  обзор сцены целиком) — иначе при сильном зум-ауте стрелка/линия крупнее
     *  самой ячейки и цепочка визуально накладывается на соседние кабинеты/подписи. */
    public static void drawChain(Graphics2D g2, Screen scr, List<String> ids, Color color, boolean dashed,
                                 int cellW, int cellH, int offX, int offY, String label) {
        drawChain(g2, scr, ids, color, dashed, cellW, cellH, offX, offY, label, label);
    }

    /** То же самое, но с РАЗНЫМИ метками начала и конца — нужно для сигнальной
     *  цепочки с назначенным резервным портом: первый кабинет подписан основным
     *  портом, последний — резервным (это конец, куда физически приходит резерв),
     *  а не дублирует основной. */
    public static void drawChain(Graphics2D g2, Screen scr, List<String> ids, Color color, boolean dashed,
                                 int cellW, int cellH, int offX, int offY, String startLabel, String endLabel) {
        int minCell = Math.min(cellW, cellH);
        float strokeWidth = (float) Math.max(0.8, Math.min(2.5, minCell * 0.08));
        g2.setStroke(dashed
                ? new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{6, 5}, 0)
                : new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(color);
        double arrowSize = Math.max(3.0, Math.min(minCell * 0.25, 9.0));
        boolean showArrowHeads = minCell >= 12;
        for (int i = 0; i < ids.size() - 1; i++) {
            CabinetInstance a = scr.cabinetById(ids.get(i));
            CabinetInstance b = scr.cabinetById(ids.get(i + 1));
            if (a == null || b == null) {
                continue;
            }
            int ax = offX + a.getColIndex() * cellW + cellW / 2;
            int ay = offY + a.getRowIndex() * cellH + cellH / 2;
            int bx = offX + b.getColIndex() * cellW + cellW / 2;
            int by = offY + b.getRowIndex() * cellH + cellH / 2;
            g2.drawLine(ax, ay, bx, by);
            if (showArrowHeads) {
                drawArrowHead(g2, ax, ay, bx, by, color, arrowSize);
            }
        }
        g2.setStroke(new BasicStroke(1f));

        if (!ids.isEmpty() && minCell >= 22) {
            if (startLabel != null && !startLabel.isEmpty()) {
                drawChainEndpointLabel(g2, scr, ids.get(0), color, cellW, cellH, offX, offY, startLabel);
            }
            if (ids.size() > 1 && endLabel != null && !endLabel.isEmpty()) {
                drawChainEndpointLabel(g2, scr, ids.get(ids.size() - 1), color, cellW, cellH, offX, offY, endLabel);
            }
        }
    }

    /** Один отрезок цепочки МЕЖДУ ДВУМЯ ЭКРАНАМИ в общем обзоре сцены (сигнальная
     *  цепочка может физически продолжаться с одного экрана на другой) — обычная
     *  {@link #drawChain} привязана к одному {@link Screen} и не может перейти
     *  границу, поэтому здесь обе точки уже готовые АБСОЛЮТНЫЕ пиксельные координаты
     *  (экраны в обзоре сцены рисуются каждый в своих локальных ячейках). Пунктир —
     *  визуально отличить переход между экранами от обычного отрезка внутри одного. */
    public static void drawCrossScreenSegment(Graphics2D g2, int ax, int ay, int bx, int by, Color color, int minCell) {
        float strokeWidth = (float) Math.max(0.8, Math.min(2.5, minCell * 0.08));
        g2.setColor(color);
        g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{7, 5}, 0));
        g2.drawLine(ax, ay, bx, by);
        if (minCell >= 12) {
            double arrowSize = Math.max(3.0, Math.min(minCell * 0.25, 9.0));
            drawArrowHead(g2, ax, ay, bx, by, color, arrowSize);
        }
        g2.setStroke(new BasicStroke(1f));
    }

    /** Кружок с меткой в углу ячейки кабинета — конец цепочки. */
    static void drawChainEndpointLabel(Graphics2D g2, Screen scr, String cabId, Color color,
                                                int cellW, int cellH, int offX, int offY, String label) {
        CabinetInstance cab = scr.cabinetById(cabId);
        if (cab == null) {
            return;
        }
        int x = offX + cab.getColIndex() * cellW;
        int y = offY + cab.getRowIndex() * cellH;
        int d = Math.max(14, Math.min(Math.min(cellW, cellH) / 2, 20));
        int cx = x + cellW - d / 2 - 2;
        int cy = y + d / 2 + 2;
        Font f = g2.getFont().deriveFont(Font.BOLD, Math.max(7f, d * 0.4f));
        g2.setFont(f);
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int textW = fm.stringWidth(label);
        int w = Math.max(d, textW + 6);
        g2.setColor(color);
        g2.fillRoundRect(cx - w / 2, cy - d / 2, w, d, d, d);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(cx - w / 2, cy - d / 2, w, d, d, d);
        g2.drawString(label, cx - textW / 2, cy + fm.getAscent() / 2 - 1);
    }

    /** Метка формы кабинета (треугольная/угловая/круглая) в углу ячейки — сама сетка
     *  остаётся прямоугольной, метка лишь сигнализирует физическую форму кабинета. */
    static void drawShapeMarker(Graphics2D g2, int x, int y, int w, int h, CabinetShape shape) {
        Color prev = g2.getColor();
        g2.setColor(Palette.ACCENT);
        int s = Math.max(8, Math.min(w, h) / 3);
        int mx = x + w - s - 3;
        int my = y + h - s - 3;
        switch (shape) {
            case TRIANGLE:
                g2.fillPolygon(new int[]{mx, mx + s, mx}, new int[]{my, my + s, my + s}, 3);
                break;
            case CORNER:
                g2.fillPolygon(new int[]{mx, mx + s, mx + s, mx + s / 2, mx + s / 2, mx},
                        new int[]{my, my, my + s, my + s, my + s / 2, my + s / 2}, 6);
                break;
            case ROUND:
                g2.fillOval(mx, my, s, s);
                break;
            default:
                break;
        }
        g2.setColor(prev);
    }

    /** Треугольная стрелка на середине отрезка a->b, указывающая направление цепочки. */
    private static void drawArrowHead(Graphics2D g2, int ax, int ay, int bx, int by, Color color, double size) {
        double dx = bx - ax;
        double dy = by - ay;
        double len = Math.hypot(dx, dy);
        if (len < 1) {
            return;
        }
        double ux = dx / len;
        double uy = dy / len;
        double midX = (ax + bx) / 2.0;
        double midY = (ay + by) / 2.0;
        double tipX = midX + ux * size * 0.6;
        double tipY = midY + uy * size * 0.6;
        double backX = midX - ux * size * 0.6;
        double backY = midY - uy * size * 0.6;
        double leftX = backX - uy * size * 0.55;
        double leftY = backY + ux * size * 0.55;
        double rightX = backX + uy * size * 0.55;
        double rightY = backY - ux * size * 0.55;

        int[] xs = {(int) Math.round(tipX), (int) Math.round(leftX), (int) Math.round(rightX)};
        int[] ys = {(int) Math.round(tipY), (int) Math.round(leftY), (int) Math.round(rightY)};
        Color prev = g2.getColor();
        g2.setColor(color);
        g2.fillPolygon(xs, ys, 3);
        g2.setColor(prev);
    }

    /** Рендерит схему экрана в изображение (с заголовком и характеристиками). */
    public static BufferedImage renderImage(Screen scr, CabinetType type, boolean power, int base) {
        return renderImage(scr, type, power, base, null, List.of(), List.of());
    }

    /** То же, но вес/мощность в заголовке учитывают переопределение типа кабинета по ячейкам.
     *  Цепочки хранятся на уровне сцены (Task #78) — вызывающая сторона передаёт сюда только те,
     *  что физически затрагивают ИМЕННО этот экран (например, {@code AppModel.powerChainsTouchingScreen}),
     *  т.к. изображение показывает один изолированный экран без контекста остальной сцены. */
    public static BufferedImage renderImage(Screen scr, CabinetType type, boolean power, int base,
                                             Workspace workspace, List<PowerChain> powerChains,
                                             List<SignalChain> signalChains) {
        Dimension c = cellSize(type, base);
        int pad = 24;
        int headerH = 52;
        int gridW = scr.getCols() * c.width;
        int gridH = scr.getRows() * c.height;
        int w = gridW + pad * 2;
        int h = gridH + pad * 2 + headerH;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setColor(Palette.BG);
        g2.fillRect(0, 0, w, h);

        // заголовок
        ScreenStats stats = ScreenLogic.stats(scr, type, workspace);
        g2.setColor(Palette.TEXT);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 16f));
        String title = "Экран «" + scr.getName() + "» — " + (power ? "Питание" : "Сигнал");
        g2.drawString(title, pad, 24);
        g2.setColor(Palette.MUTED);
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));
        String sub = scr.getCols() + "×" + scr.getRows() + " каб. · "
                + stats.resolutionWidthPx() + "×" + stats.resolutionHeightPx() + " px · "
                + trim(stats.physicalWidthMm()) + "×" + trim(stats.physicalHeightMm()) + " мм · "
                + trim(stats.totalPowerW()) + " Вт · " + trim(stats.totalWeightKg()) + " кг";
        g2.drawString(sub, pad, 42);

        paintScheme(g2, scr, type, power, c.width, c.height, pad, pad + headerH, workspace,
                powerChains, signalChains);
        g2.dispose();
        return img;
    }

    /** Сохраняет изображение в JPEG с высоким качеством. */
    public static void writeJpeg(BufferedImage img, File file) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.92f);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private static Color blend(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() * (1 - t) + b.getRed() * t),
                Math.round(a.getGreen() * (1 - t) + b.getGreen() * t),
                Math.round(a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }
}
