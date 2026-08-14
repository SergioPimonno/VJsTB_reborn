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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** Цвет цепочки питания для отрисовки — свой (Task #4), если задан, иначе
     *  как раньше по фазе (см. Palette.phaseColor). */
    public static Color chainColor(PowerChain chain) {
        return chain.getColor() != null ? new Color(chain.getColor()) : Palette.phaseColor(chain.getPhase());
    }

    /** Цвет цепочки сигнала — свой (Task #4), если задан, иначе по индексу в
     *  списке цепочек экрана, как раньше (см. Palette.signalColor). */
    public static Color chainColor(SignalChain chain, int indexInList) {
        return chain.getColor() != null ? new Color(chain.getColor()) : Palette.signalColor(indexInList);
    }

    /** Размер ячейки кабинета по соотношению сторон типа. */
    public static Dimension cellSize(CabinetType type, int base) {
        double ratio = type != null && type.getHeightMm() > 0 ? type.getWidthMm() / type.getHeightMm() : 1;
        int w = ratio >= 1 ? base : (int) Math.round(base * ratio);
        int h = ratio >= 1 ? (int) Math.round(base / ratio) : base;
        return new Dimension(Math.max(w, 24), Math.max(h, 24));
    }

    /** Экранная позиция ячейки (X/Y) — сеточная позиция (rowIndex/colIndex * размер
     *  ячейки) плюс свободное мм-смещение ячейки (см. CabinetInstance.getOffsetXMm/
     *  getOffsetYMm, Task #7/v1.6), переведённое в пиксели ТЕКУЩЕГО масштаба через
     *  {@link ScreenLogic#offsetPx} — единая точка правды для всех мест, что кладут
     *  ячейку на сетку (используется здесь ВЕЗДЕ вместо прямого offX + col*cellW,
     *  иначе часть отрисовки учитывала бы смещение, а часть — нет, и ячейка визуально
     *  «расползалась» бы между линией цепочки/подписью и своим же контуром).
     *  type может быть null (см. effectiveTypeOf) — тогда смещение не применяется. */
    private static int cabX(CabinetInstance cab, CabinetType type, int cellW, int offX) {
        double dx = type != null ? ScreenLogic.offsetPx(cab.getOffsetXMm(), cellW, type.getWidthMm()) : 0;
        return offX + (int) Math.round(cab.getColIndex() * cellW + dx);
    }

    private static int cabY(CabinetInstance cab, CabinetType type, int cellH, int offY) {
        double dy = type != null ? ScreenLogic.offsetPx(cab.getOffsetYMm(), cellH, type.getHeightMm()) : 0;
        return offY + (int) Math.round(cab.getRowIndex() * cellH + dy);
    }

    /** Экранный прямоугольник ячейки кабинета целиком — та же геометрия, что cabX/cabY
     *  (см. их javadoc), но сразу прямоугольником, для мест, которым нужна вся ячейка,
     *  а не только угол (например, отметить кабинет-«гнездо» подключения на общей
     *  схеме — см. SchemaCanvasPanel). */
    public static java.awt.Rectangle cabinetScreenRect(CabinetInstance cab, CabinetType type, int cellW, int cellH,
            int offX, int offY) {
        return new java.awt.Rectangle(cabX(cab, type, cellW, offX), cabY(cab, type, cellH, offY), cellW, cellH);
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
        paintScheme(g2, scr, type, power, cellW, cellH, offX, offY, workspace, powerChains, signalChains, false);
    }

    /** {@code powerUnitKw} — единица отображения мощности в подписи ячейки (см.
     *  {@code UserProfile#isPowerUnitKw}), только для {@code power == true}. Оставлен
     *  как перегрузка (не единственная сигнатура) — большинство вызывающих кодов вне
     *  Питание/Сигнал этапов не имеют под рукой SettingsManager и рисуют схему без
     *  учёта юнитов (сигнальный режим — там power всегда false, юнит не участвует). */
    public static void paintScheme(Graphics2D g2, Screen scr, CabinetType type, boolean power,
                                   int cellW, int cellH, int offX, int offY, Workspace workspace,
                                   List<PowerChain> powerChains, List<SignalChain> signalChains,
                                   boolean powerUnitKw) {
        // Подпись «строка,столбец» физически не помещается в мелкую ячейку (мини-
        // обзор сцены с несколькими экранами целиком, сильный зум-аут) — рисуем её,
        // только если в ячейке реально есть место, иначе текст соседних кабинетов
        // наезжает друг на друга и на линии цепочек, превращаясь в нечитаемое пятно.
        boolean showLabels = cellH >= 16 && cellW >= 16;
        Font labelFont = showLabels ? g2.getFont().deriveFont(Font.PLAIN, Math.max(9f, cellH * 0.14f)) : null;
        for (CabinetInstance cab : scr.getCabinets()) {
            // Деактивированная (скрытая) ячейка — по определению "не считается, не
            // рисуется, не участвует в цепочках" (см. CabinetInstance.isHidden) —
            // раньше её контур всё равно рисовался (пустой прямоугольник без формы/
            // подписи), что визуально оставляло в сетке "экраны прописи" физически
            // не существующие кабинеты (Task #93/v1.5).
            if (cab.isHidden()) {
                continue;
            }
            int x = cabX(cab, type, cellW, offX);
            int y = cabY(cab, type, cellH, offY);

            CabinetType effective = effectiveTypeOf(cab, type, workspace);
            CabinetShape shape = cab.getShapeOverride() != null ? cab.getShapeOverride()
                    : (effective != null ? effective.getShape() : null);
            double rotationDeg = effectiveRotationDeg(cab, effective);
            // Ячейка с переопределённым типом другого физического размера рисуется
            // ПРОПОРЦИОНАЛЬНО этому размеру (см. ScreenLogic.effectiveCellW/H), а не
            // втискивается в номинальную cellW/cellH — иначе комбинация кабинетов
            // разных габаритов на одном экране визуально неотличима от однородной
            // сетки (баг-репорт про 500×1000мм кабинет в экране 500×500мм).
            int ew = (int) Math.round(ScreenLogic.effectiveCellW(effective, type, cellW));
            int eh = (int) Math.round(ScreenLogic.effectiveCellH(effective, type, cellH));

            g2.setColor(Palette.BORDER);
            outlineCabinetShape(g2, x, y, ew, eh, shape, rotationDeg);

            if (showLabels) {
                g2.setColor(new Color(0xc0, 0xc8, 0xd0));
                g2.setFont(labelFont);
                g2.drawString(cab.getDisplayRow() + "," + cab.getDisplayCol(), x + 4, y + labelFont.getSize() + 2);
            }
        }

        if (power) {
            for (PowerChain chain : powerChains) {
                // Метка фазы — только у НАЧАЛА цепочки: питание не закольцовывается
                // (в отличие от сигнала с резервным портом на другом конце), поэтому
                // дублировать фазу на последнем кабинете незачем.
                drawChain(g2, scr, chain.getCabinetInstanceIds(), chainColor(chain),
                        false, cellW, cellH, offX, offY, type, workspace, "L" + chain.getPhase(), null);
            }
        } else {
            for (int i = 0; i < signalChains.size(); i++) {
                SignalChain chain = signalChains.get(i);
                drawChain(g2, scr, chain.getCabinetInstanceIds(), chainColor(chain, i),
                        false, cellW, cellH, offX, offY, type, workspace, signalChainLabel(scr, chain, workspace),
                        signalChainEndLabel(scr, chain, workspace));
            }
        }
    }

    /** Кабинет принадлежит цепочке под индексом chainIndex (для цвета — тот же
     *  индекс, что использует paintScheme/Palette.signalColor) на позиции seq
     *  (1-based, порядок физического подключения в цепочке). */
    private record ChainMembership(int chainIndex, int seq, Color color, Integer phase, Integer port) { }

    /** "Богатая" схема расключения экрана в духе профессиональных PDF-схем
     *  (сплошная заливка ячейки цветом её цепочки, кружок-разъём и стрелка между
     *  соседними кабинетами одной цепочки, многострочная подпись на кабинете —
     *  фаза/порт, порядковый номер в цепочке, разрешение или мощность) — задумана
     *  для встраиваемой миниатюры узла-экрана в общей схеме площадки (см.
     *  SchemaCanvasPanel), не для основного интерактивного холста прописи (там
     *  по-прежнему {@link #paintScheme} — привычный вид для клика по кабинетам). */
    public static void paintWiringDiagram(Graphics2D g2, Screen scr, CabinetType type, boolean power,
                                           int cellW, int cellH, int offX, int offY, Workspace workspace,
                                           List<PowerChain> powerChains, List<SignalChain> signalChains) {
        paintWiringDiagram(g2, scr, type, power, cellW, cellH, offX, offY, workspace, powerChains, signalChains, false);
    }

    /** {@code powerUnitKw} — см. javadoc {@link #paintScheme}'s перегрузка с тем же параметром. */
    public static void paintWiringDiagram(Graphics2D g2, Screen scr, CabinetType type, boolean power,
                                           int cellW, int cellH, int offX, int offY, Workspace workspace,
                                           List<PowerChain> powerChains, List<SignalChain> signalChains,
                                           boolean powerUnitKw) {
        Map<String, ChainMembership> byCab = new HashMap<>();
        if (power) {
            for (int ci = 0; ci < powerChains.size(); ci++) {
                PowerChain chain = powerChains.get(ci);
                Color color = chainColor(chain);
                List<String> ids = chain.getCabinetInstanceIds();
                for (int i = 0; i < ids.size(); i++) {
                    byCab.put(ids.get(i), new ChainMembership(ci, i + 1, color, chain.getPhase(), null));
                }
            }
        } else {
            for (int ci = 0; ci < signalChains.size(); ci++) {
                SignalChain chain = signalChains.get(ci);
                Color color = chainColor(chain, ci);
                List<String> ids = chain.getCabinetInstanceIds();
                for (int i = 0; i < ids.size(); i++) {
                    byCab.put(ids.get(i), new ChainMembership(ci, i + 1, color, null, chain.getPortNumber()));
                }
            }
        }

        // Многострочная подпись физически не помещается в мелкую ячейку (узел схемы
        // маленького размера) — тогда просто закрашиваем цветом цепочки без текста,
        // как обычная миниатюра, вместо нечитаемой мешанины из строк.
        boolean showLabels = cellH >= 34 && cellW >= 46;
        Font labelFont = showLabels
                ? g2.getFont().deriveFont(Font.PLAIN, Math.max(8f, Math.min(cellH, cellW) * 0.13f)) : null;

        for (CabinetInstance cab : scr.getCabinets()) {
            if (cab.isHidden()) {
                continue;
            }
            int x = cabX(cab, type, cellW, offX);
            int y = cabY(cab, type, cellH, offY);
            ChainMembership m = byCab.get(cab.getId());
            CabinetType effective = effectiveTypeOf(cab, type, workspace);
            CabinetShape shape = cab.getShapeOverride() != null ? cab.getShapeOverride()
                    : (effective != null ? effective.getShape() : null);
            double rotationDeg = effectiveRotationDeg(cab, effective);
            int ew = (int) Math.round(ScreenLogic.effectiveCellW(effective, type, cellW));
            int eh = (int) Math.round(ScreenLogic.effectiveCellH(effective, type, cellH));

            g2.setColor(m != null ? m.color() : Palette.PANEL);
            fillCabinetShape(g2, x, y, ew, eh, shape, rotationDeg);
            g2.setColor(Palette.BORDER);
            outlineCabinetShape(g2, x, y, ew, eh, shape, rotationDeg);

            if (m != null && showLabels) {
                List<String> lines = new ArrayList<>();
                lines.add(power ? "L" + m.phase() : portLabel(scr, workspace, m.port() != null ? m.port() : 0));
                lines.add("#" + m.seq());
                if (effective != null) {
                    lines.add(power ? UiKit.fmtPower(effective.getPowerConsumptionW(), powerUnitKw)
                            : effective.getResolutionWidth() + "×" + effective.getResolutionHeight());
                }
                // Непрямоугольная ячейка закрашена не целиком (треугольник/круг —
                // часть прямоугольника ячейки остаётся фоном) — текст на фиксированной
                // позиции у верхнего левого угла может оказаться в НЕзакрашенной
                // части (например, вырезанный угол треугольника при 270°), выглядя
                // подписью "в воздухе" поверх пустоты. Обрезаем по контуру формы —
                // для прямоугольника это НИКАК не меняет результат (тот же
                // прямоугольник), для остальных форм подпись, не попавшая внутрь
                // формы, просто не рисуется, а не наезжает на пустой фон.
                Graphics2D gc = (Graphics2D) g2.create();
                gc.clip(cabinetShapeOutline(x, y, ew, eh, shape, rotationDeg));
                gc.setFont(labelFont);
                gc.setColor(Color.BLACK);
                java.awt.FontMetrics fm = gc.getFontMetrics();
                int lineH = fm.getHeight();
                int ty = y + lineH;
                int maxTextW = ew - 6;
                for (String line : lines) {
                    if (ty > y + eh - 2) {
                        break;
                    }
                    gc.drawString(clipToWidth(gc, line, maxTextW), x + 3, ty);
                    ty += lineH;
                }
                gc.dispose();
            }
        }

        // Подписи начал цепочек группируются ПО ПОЗИЦИИ первого кабинета и рисуются
        // ОДНИМ проходом после всех линий — резервная цепочка сигнала на практике
        // почти всегда физически идёт через ТЕ ЖЕ кабинеты, что и основная (второй
        // кабель на тот же ряд панелей), т.е. у обеих один и тот же первый кабинет;
        // рисовать подписи независимо, как раньше, означало, что вторая просто
        // затирала первую в том же самом месте — теперь обе подписи одной плашкой.
        Map<String, List<LabelLine>> startLabelsByCabinet = new LinkedHashMap<>();
        // Резервный порт (Task #32/#48): назначается ОДНИМ полем на цепочке
        // (backupPortNumber) — обычно физически это тот же ряд кабинетов, дальний
        // конец которого дополнительно заведён в резервный порт (loop-through), а не
        // отдельная цепочка с собственными кабинетами. Показывается на ПОСЛЕДНЕМ
        // кабинете цепочки — так же, как уже показывалось в интерактивном paintScheme
        // (см. signalChainEndLabel), только раньше этого не было в этом, "богатом",
        // виде схемы вовсе.
        Map<String, List<LabelLine>> endLabelsByCabinet = new LinkedHashMap<>();
        if (power) {
            for (PowerChain chain : powerChains) {
                drawChainWithDots(g2, scr, chain.getCabinetInstanceIds(), cellW, cellH, offX, offY, type, workspace, false);
                addStartLabel(startLabelsByCabinet, chain.getCabinetInstanceIds(), new LabelLine("L" + chain.getPhase(), false));
            }
        } else {
            for (SignalChain chain : signalChains) {
                drawChainWithDots(g2, scr, chain.getCabinetInstanceIds(), cellW, cellH, offX, offY, type, workspace,
                        chain.isBackup());
                if (chain.getPortNumber() != null) {
                    String lbl = portLabel(scr, workspace, chain.getPortNumber());
                    // Раньше резервная цепочка получала префикс "рез:" — на мелких
                    // ячейках плашки бейджа он не помещался и обрезался до нечитаемого
                    // "pe...". Различаем резерв ЦВЕТОМ текста (см. drawStartLabelBadge),
                    // а не текстом, который всё равно негде показать целиком.
                    addStartLabel(startLabelsByCabinet, chain.getCabinetInstanceIds(),
                            new LabelLine(lbl, chain.isBackup()));
                }
                if (chain.getBackupPortNumber() != null && !chain.getCabinetInstanceIds().isEmpty()) {
                    String endLbl = portLabel(scr, workspace, chain.getBackupPortNumber());
                    List<String> ids = chain.getCabinetInstanceIds();
                    endLabelsByCabinet.computeIfAbsent(ids.get(ids.size() - 1), k -> new ArrayList<>())
                            .add(new LabelLine(endLbl, true));
                }
            }
        }
        int minCellGlobal = Math.min(cellW, cellH);
        if (minCellGlobal >= 10) {
            for (var entry : startLabelsByCabinet.entrySet()) {
                CabinetInstance cab = scr.cabinetById(entry.getKey());
                if (cab != null) {
                    drawStartLabelBadge(g2, cab, type, cellW, cellH, offX, offY, entry.getValue(), false);
                }
            }
            for (var entry : endLabelsByCabinet.entrySet()) {
                CabinetInstance cab = scr.cabinetById(entry.getKey());
                if (cab != null) {
                    drawStartLabelBadge(g2, cab, type, cellW, cellH, offX, offY, entry.getValue(), true);
                }
            }
        }
    }

    /** Одна строка плашки-подписи: текст + признак «это резервный порт/цепочка» —
     *  раньше резерв отмечался префиксом "рез:" в самом тексте, но на мелких ячейках
     *  плашка обрезала его до нечитаемого "pe...". Различаем резерв ЦВЕТОМ строки
     *  (см. drawStartLabelBadge) вместо текста, которому всё равно негде поместиться. */
    private record LabelLine(String text, boolean backup) {
    }

    private static void addStartLabel(Map<String, List<LabelLine>> byCabinet, List<String> chainIds, LabelLine label) {
        if (chainIds.isEmpty()) {
            return;
        }
        byCabinet.computeIfAbsent(chainIds.get(0), k -> new ArrayList<>()).add(label);
    }

    /** Плашка с одной или несколькими подписями (основная цепочка + резерв(ы), если
     *  начинаются на том же кабинете) в углу ячейки — независимо от showLabels (тот
     *  отключается для мелких ячеек, но видеть, где начинается/заканчивается КАКАЯ
     *  линия, нужно всегда, пока ячейка вообще различима). bottomRight — рисовать в
     *  правом нижнем углу вместо левого верхнего (для подписи резервного порта на
     *  ПОСЛЕДНЕМ кабинете цепочки — не должна перекрывать подпись начала другой
     *  цепочки, если та начинается в этой же ячейке). */
    private static void drawStartLabelBadge(Graphics2D g2, CabinetInstance cab, CabinetType type, int cellW, int cellH,
                                             int offX, int offY, List<LabelLine> lines, boolean bottomRight) {
        int minCell = Math.min(cellW, cellH);
        int x = cabX(cab, type, cellW, offX);
        int y = cabY(cab, type, cellH, offY);
        Font f = g2.getFont().deriveFont(Font.BOLD, Math.max(7f, Math.min(minCell * 0.32f, 11f)));
        g2.setFont(f);
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int pad = 2;
        int maxTextW = 0;
        for (LabelLine line : lines) {
            maxTextW = Math.max(maxTextW, fm.stringWidth(line.text()));
        }
        int tw = Math.min(cellW - 2, maxTextW + pad * 2);
        int lineH = fm.getHeight();
        int th = lineH * lines.size();
        int bx = bottomRight ? x + cellW - tw - 1 : x + 1;
        int by = bottomRight ? y + cellH - th - 1 : y + 1;
        g2.setColor(new Color(0, 0, 0, 190));
        g2.fillRoundRect(bx, by, tw, th, 4, 4);
        int ty = by + fm.getAscent();
        for (LabelLine line : lines) {
            g2.setColor(line.backup() ? Color.ORANGE : Color.WHITE);
            g2.drawString(clipToWidth(g2, line.text(), tw - pad * 2), bx + pad, ty);
            ty += lineH;
        }
    }

    /** Фактический тип кабинета конкретной ячейки (переопределение по ячейке, если
     *  задано и разрешимо через workspace, иначе тип экрана по умолчанию). */
    private static CabinetType effectiveTypeOf(CabinetInstance cab, CabinetType defaultType, Workspace workspace) {
        if (workspace != null && cab.getCabinetTypeId() != null) {
            CabinetType override = workspace.cabinetTypeById(cab.getCabinetTypeId());
            if (override != null) {
                return override;
            }
        }
        return defaultType;
    }

    /** Фактический угол непрямоугольной формы конкретной ячейки (Task #92/v1.5):
     *  переопределение по ячейке (CabinetInstance.rotationOverride), если задано,
     *  иначе угол эффективного типа кабинета. */
    public static double effectiveRotationDeg(CabinetInstance cab, CabinetType effectiveType) {
        if (cab.getRotationOverride() != null) {
            return cab.getRotationOverride();
        }
        return effectiveType != null ? effectiveType.getRotationDeg() : 0;
    }

    /** Точка привязки линии/точки коммутации в ячейке кабинета (Task #93/v1.5) —
     *  для непрямоугольной формы это НЕ геометрический центр прямоугольника ячейки
     *  (который может попасть в незакрашенный вырезанный угол — например, у
     *  треугольника при некоторых поворотах центр прямоугольника лежит СНАРУЖИ
     *  самого треугольника), а центр тяжести самой фигуры (для треугольника —
     *  среднее трёх вершин). Для прямоугольника/круга/угловой формы — как и раньше,
     *  геометрический центр ячейки (эти формы симметричны относительно него). */
    public static java.awt.Point cabinetConnectionAnchor(int x, int y, int w, int h, CabinetShape shape,
                                                           double rotationDeg) {
        if (shape == CabinetShape.TRIANGLE) {
            java.awt.Polygon p = trianglePolygon(x, y, w, h, rotationDeg);
            int cx = (p.xpoints[0] + p.xpoints[1] + p.xpoints[2]) / 3;
            int cy = (p.ypoints[0] + p.ypoints[1] + p.ypoints[2]) / 3;
            return new java.awt.Point(cx, cy);
        }
        return new java.awt.Point(x + w / 2, y + h / 2);
    }

    /** Точка привязки коммутации КОНКРЕТНОГО кабинета в системе координат сетки
     *  экрана — собирает координаты ячейки + её фактическую форму/угол и делегирует
     *  {@link #cabinetConnectionAnchor}. */
    private static java.awt.Point anchorFor(CabinetInstance cab, int offX, int offY, int cellW, int cellH,
                                             CabinetType type, Workspace workspace) {
        int x = cabX(cab, type, cellW, offX);
        int y = cabY(cab, type, cellH, offY);
        CabinetType effective = effectiveTypeOf(cab, type, workspace);
        CabinetShape shape = cab.getShapeOverride() != null ? cab.getShapeOverride()
                : (effective != null ? effective.getShape() : null);
        double rotationDeg = effectiveRotationDeg(cab, effective);
        int ew = (int) Math.round(ScreenLogic.effectiveCellW(effective, type, cellW));
        int eh = (int) Math.round(ScreenLogic.effectiveCellH(effective, type, cellH));
        return cabinetConnectionAnchor(x, y, ew, eh, shape, rotationDeg);
    }

    /** Обрезает строку по ширине (с "…"), чтобы не наезжала на соседние ячейки при
     *  мелком шрифте миниатюры расключения. */
    private static String clipToWidth(Graphics2D g2, String text, int maxWidth) {
        java.awt.FontMetrics fm = g2.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String s = text;
        while (s.length() > 1 && fm.stringWidth(s + "…") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    /** Как {@link #drawChain}, но дополнительно рисует небольшой кружок-разъём в
     *  центре каждого кабинета цепочки (не только стрелку между соседними) — как в
     *  профессиональных схемах расключения, где виден сам физический разъём/точка
     *  подключения, а не только направление линии. */
    /** dashed — пунктирная линия вместо сплошной; используется для РЕЗЕРВНОЙ
     *  сигнальной цепочки, которая на практике почти всегда физически идёт через
     *  ТЕ ЖЕ кабинеты, что и основная (см. вызов из paintWiringDiagram) — без
     *  визуального различия основной и резервный путь были бы неотличимы, рисуясь
     *  друг поверх друга в одних и тех же точках. */
    private static void drawChainWithDots(Graphics2D g2, Screen scr, List<String> ids,
                                           int cellW, int cellH, int offX, int offY,
                                           CabinetType type, Workspace workspace, boolean dashed) {
        int minCell = Math.min(cellW, cellH);
        float strokeWidth = (float) Math.max(0.8, Math.min(2.5, minCell * 0.08));
        g2.setStroke(dashed
                ? new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                        new float[]{Math.max(3f, minCell * 0.25f), Math.max(2f, minCell * 0.18f)}, 0)
                : new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // ЧЁРНЫЙ, а не цвет цепочки — ячейки здесь залиты СПЛОШНЫМ цветом цепочки
        // (в отличие от обычного paintScheme), линия того же цвета была бы попросту
        // не видна поверх такой заливки. Цвет цепочки и так виден по заливке ячеек —
        // линии нужны только показать САМ путь/порядок подключения.
        Color lineColor = Color.BLACK;
        g2.setColor(lineColor);
        double arrowSize = Math.max(3.0, Math.min(minCell * 0.25, 9.0));
        boolean showArrowHeads = minCell >= 12;
        int dotR = Math.max(2, Math.min(minCell / 8, 5));
        for (int i = 0; i < ids.size() - 1; i++) {
            CabinetInstance a = scr.cabinetById(ids.get(i));
            CabinetInstance b = scr.cabinetById(ids.get(i + 1));
            if (a == null || b == null) {
                continue;
            }
            java.awt.Point pa = anchorFor(a, offX, offY, cellW, cellH, type, workspace);
            java.awt.Point pb = anchorFor(b, offX, offY, cellW, cellH, type, workspace);
            g2.drawLine(pa.x, pa.y, pb.x, pb.y);
            if (showArrowHeads) {
                drawArrowHead(g2, pa.x, pa.y, pb.x, pb.y, lineColor, arrowSize);
            }
        }
        if (minCell >= 18) {
            g2.setColor(Color.WHITE);
            for (String id : ids) {
                CabinetInstance c = scr.cabinetById(id);
                if (c == null) {
                    continue;
                }
                java.awt.Point pc = anchorFor(c, offX, offY, cellW, cellH, type, workspace);
                g2.fillOval(pc.x - dotR, pc.y - dotR, dotR * 2, dotR * 2);
                g2.setColor(Color.BLACK);
                g2.drawOval(pc.x - dotR, pc.y - dotR, dotR * 2, dotR * 2);
                g2.setColor(Color.WHITE);
            }
        }
        g2.setStroke(new BasicStroke(1f));
    }

    /** Компактная сводная полоса контроллеров экрана под сеткой расключения сигнала —
     *  как строка "MCTRL4K-1" с портами и цветом резерва в исходной схеме-образце:
     *  по одному контроллеру на строку, с меткой резервной связки, если она есть. */
    public static int controllerSummaryBarHeight(Screen scr) {
        return scr.getControllers().isEmpty() ? 0 : 14 * scr.getControllers().size() + 4;
    }

    public static void drawControllerSummaryBar(Graphics2D g2, Screen scr, Workspace workspace,
                                                 int x, int y, int width) {
        List<com.vjstb.ledscheme.model.ControllerInstance> controllers = scr.getControllers();
        if (controllers.isEmpty() || workspace == null) {
            return;
        }
        Font f = g2.getFont().deriveFont(Font.PLAIN, 9f);
        g2.setFont(f);
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int offset = 0;
        int rowY = y + fm.getAscent();
        for (int i = 0; i < controllers.size(); i++) {
            com.vjstb.ledscheme.model.ControllerInstance ci = controllers.get(i);
            com.vjstb.ledscheme.model.ControllerType t = workspace.controllerTypeById(ci.getControllerTypeId());
            int count = t != null ? t.effectivePortCount() : 0;
            boolean isBackupOfAnother = controllers.stream().anyMatch(o -> ci.getId().equals(o.getBackupControllerId()));
            String name = t != null ? t.getName() : "?";
            String text = "C" + (i + 1) + " " + name + " · P" + (offset + 1) + "-" + (offset + count)
                    + (ci.getBackupControllerId() != null ? " (резерв: следующий)" : "");
            g2.setColor(isBackupOfAnother ? Palette.MUTED : Color.WHITE);
            g2.drawString(clipToWidth(g2, text, width - 14), x + 12, rowY);
            g2.setColor(isBackupOfAnother ? Color.RED : new Color(0x3f, 0xb9, 0x50));
            g2.fillOval(x + 2, rowY - fm.getAscent() + 2, 8, 8);
            rowY += 14;
            offset += count;
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
                                 int cellW, int cellH, int offX, int offY, CabinetType type, Workspace workspace) {
        drawChain(g2, scr, ids, color, dashed, cellW, cellH, offX, offY, type, workspace, null);
    }

    /** То же самое, плюс опознавательный кружок с меткой (фаза L1/L2/L3 для питания;
     *  порт, и контроллер — если их несколько на экране, для сигнала) на первом и
     *  последнем кабинете цепочки — иначе на общей схеме с несколькими цепочками
     *  не видно, какая линия куда физически подключена, только цвет.
     *  Толщина линии и размер стрелки масштабируются вниз для мелких ячеек (мини-
     *  обзор сцены целиком) — иначе при сильном зум-ауте стрелка/линия крупнее
     *  самой ячейки и цепочка визуально накладывается на соседние кабинеты/подписи. */
    public static void drawChain(Graphics2D g2, Screen scr, List<String> ids, Color color, boolean dashed,
                                 int cellW, int cellH, int offX, int offY, CabinetType type, Workspace workspace,
                                 String label) {
        drawChain(g2, scr, ids, color, dashed, cellW, cellH, offX, offY, type, workspace, label, label);
    }

    /** То же самое, но с РАЗНЫМИ метками начала и конца — нужно для сигнальной
     *  цепочки с назначенным резервным портом: первый кабинет подписан основным
     *  портом, последний — резервным (это конец, куда физически приходит резерв),
     *  а не дублирует основной. type/workspace — чтобы правильно определить точку
     *  привязки линии для непрямоугольных кабинетов (см. cabinetConnectionAnchor,
     *  Task #93/v1.5) — центр ГЕОМЕТРИЧЕСКОЙ ячейки для треугольника мог попасть в
     *  незакрашенный вырезанный угол, линия визуально проходила "мимо" фигуры. */
    public static void drawChain(Graphics2D g2, Screen scr, List<String> ids, Color color, boolean dashed,
                                 int cellW, int cellH, int offX, int offY, CabinetType type, Workspace workspace,
                                 String startLabel, String endLabel) {
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
            java.awt.Point pa = anchorFor(a, offX, offY, cellW, cellH, type, workspace);
            java.awt.Point pb = anchorFor(b, offX, offY, cellW, cellH, type, workspace);
            g2.drawLine(pa.x, pa.y, pb.x, pb.y);
            if (showArrowHeads) {
                drawArrowHead(g2, pa.x, pa.y, pb.x, pb.y, color, arrowSize);
            }
        }
        g2.setStroke(new BasicStroke(1f));

        if (!ids.isEmpty() && minCell >= 22) {
            if (startLabel != null && !startLabel.isEmpty()) {
                drawChainEndpointLabel(g2, scr, ids.get(0), color, type, cellW, cellH, offX, offY, startLabel);
            }
            if (ids.size() > 1 && endLabel != null && !endLabel.isEmpty()) {
                drawChainEndpointLabel(g2, scr, ids.get(ids.size() - 1), color, type, cellW, cellH, offX, offY, endLabel);
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
    static void drawChainEndpointLabel(Graphics2D g2, Screen scr, String cabId, Color color, CabinetType type,
                                                int cellW, int cellH, int offX, int offY, String label) {
        CabinetInstance cab = scr.cabinetById(cabId);
        if (cab == null) {
            return;
        }
        int x = cabX(cab, type, cellW, offX);
        int y = cabY(cab, type, cellH, offY);
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

    /** Заполняет ячейку кабинета РЕАЛЬНОЙ физической формой (Task #91/v1.5) вместо
     *  всегда прямоугольника с декоративной меткой в углу: RECTANGLE — вся ячейка
     *  (уже вычисляется пропорционально физическим width/height, см. cellSize —
     *  прямоугольный кабинет и так рисуется с его настоящим соотношением сторон);
     *  TRIANGLE — настоящий прямоугольный треугольник (см. {@link #trianglePolygon});
     *  ROUND — вписанный эллипс. CORNER без изменений — форма "вырезанного угла" не
     *  уточнена пользователем, остаётся только декоративной меткой (см. ShapeEditorPanel/
     *  drawShapeMarker), полигон для неё не строится. null — как RECTANGLE (тип/форма
     *  неизвестны). */
    static void fillCabinetShape(Graphics2D g2, int x, int y, int w, int h, CabinetShape shape, double rotationDeg) {
        if (shape == CabinetShape.TRIANGLE) {
            g2.fillPolygon(trianglePolygon(x, y, w, h, rotationDeg));
        } else if (shape == CabinetShape.ROUND) {
            g2.fillOval(x, y, w, h);
        } else {
            g2.fillRect(x, y, w, h);
        }
    }

    /** Контур ячейки кабинета той же формой, что и {@link #fillCabinetShape}. */
    static void outlineCabinetShape(Graphics2D g2, int x, int y, int w, int h, CabinetShape shape, double rotationDeg) {
        if (shape == CabinetShape.TRIANGLE) {
            g2.drawPolygon(trianglePolygon(x, y, w, h, rotationDeg));
        } else if (shape == CabinetShape.ROUND) {
            g2.drawOval(x, y, w, h);
        } else {
            g2.drawRect(x, y, w, h);
        }
    }

    /** {@link java.awt.Shape} той же формы — для обрезки (clip) содержимого ячейки
     *  (например, подписи), чтобы оно не попадало в НЕзакрашенную часть ячейки у
     *  непрямоугольных форм (см. использование в paintWiringDiagram). */
    private static java.awt.Shape cabinetShapeOutline(int x, int y, int w, int h, CabinetShape shape,
                                                        double rotationDeg) {
        if (shape == CabinetShape.TRIANGLE) {
            return trianglePolygon(x, y, w, h, rotationDeg);
        } else if (shape == CabinetShape.ROUND) {
            return new java.awt.geom.Ellipse2D.Float(x, y, w, h);
        } else {
            return new java.awt.Rectangle(x, y, w, h);
        }
    }

    /** Прямоугольный треугольник, вписанный в ячейку (x,y,w,h) — прямой угол стоит
     *  в одном из 4 углов ячейки по rotationDeg (см. CabinetType.getRotationDeg):
     *  0° — левый нижний, далее по часовой стрелке (90° — левый верхний, 180° —
     *  правый верхний, 270° — правый нижний). Угол округляется до ближайших 90° —
     *  физическая ориентация треугольного LED-кабинета в реальной инсталляции
     *  осмысленна только с шагом в четверть оборота (иное означало бы кабинет,
     *  висящий не по одной из 4 сторон своей ячейки). Вершины треугольника — это
     *  всегда 3 ИЗ 4 УГЛОВ САМОЙ ЯЧЕЙКИ (не геометрический поворот полигона), что
     *  сохраняет соотношение сторон ячейки даже для непрямоугольного (например,
     *  портретного) кабинета — геометрический поворот полигона на 90° исказил бы
     *  форму, поменяв местами эффективные ширину/высоту. */
    public static java.awt.Polygon trianglePolygon(int x, int y, int w, int h, double rotationDeg) {
        int quadrant = Math.floorMod(Math.round(rotationDeg / 90.0), 4);
        int[] xs;
        int[] ys;
        switch (quadrant) {
            case 1 -> { // 90°: прямой угол — левый верхний (омитим правый нижний)
                xs = new int[]{x, x + w, x};
                ys = new int[]{y + h, y, y};
            }
            case 2 -> { // 180°: прямой угол — правый верхний (омитим левый нижний)
                xs = new int[]{x, x + w, x + w};
                ys = new int[]{y, y, y + h};
            }
            case 3 -> { // 270°: прямой угол — правый нижний (омитим левый верхний)
                xs = new int[]{x + w, x, x + w};
                ys = new int[]{y, y + h, y + h};
            }
            default -> { // 0°: прямой угол — левый нижний (омитим правый верхний)
                xs = new int[]{x, x + w, x};
                ys = new int[]{y, y + h, y + h};
            }
        }
        return new java.awt.Polygon(xs, ys, 3);
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
        return renderImage(scr, type, power, base, null, List.of(), List.of(), false);
    }

    /** То же, но вес/мощность в заголовке учитывают переопределение типа кабинета по ячейкам.
     *  Цепочки хранятся на уровне сцены (Task #78) — вызывающая сторона передаёт сюда только те,
     *  что физически затрагивают ИМЕННО этот экран (например, {@code AppModel.powerChainsTouchingScreen}),
     *  т.к. изображение показывает один изолированный экран без контекста остальной сцены. */
    public static BufferedImage renderImage(Screen scr, CabinetType type, boolean power, int base,
                                             Workspace workspace, List<PowerChain> powerChains,
                                             List<SignalChain> signalChains) {
        return renderImage(scr, type, power, base, workspace, powerChains, signalChains, false);
    }

    /** {@code powerUnitKw} — единица отображения мощности в заголовке/ячейках (см.
     *  {@code UserProfile#isPowerUnitKw}), см. javadoc {@link #paintScheme}'s перегрузка
     *  для того же параметра. */
    public static BufferedImage renderImage(Screen scr, CabinetType type, boolean power, int base,
                                             Workspace workspace, List<PowerChain> powerChains,
                                             List<SignalChain> signalChains, boolean powerUnitKw) {
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
                + UiKit.fmtPower(stats.totalPowerW(), powerUnitKw) + " · " + trim(stats.totalWeightKg()) + " кг";
        g2.drawString(sub, pad, 42);

        paintScheme(g2, scr, type, power, c.width, c.height, pad, pad + headerH, workspace,
                powerChains, signalChains, powerUnitKw);
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

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }
}
