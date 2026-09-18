package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetShape;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.AppModel;
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
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
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
     *  схеме — см. SchemaCanvasPanel). Размер — ФАКТИЧЕСКИЙ (см. {@link
     *  ScreenLogic#effectiveCellW}/{@link ScreenLogic#effectiveCellH}), а не
     *  номинальный {@code cellW}/{@code cellH} — иначе для кабинета с переопределённым
     *  типом другого физического размера гнездо подключения рисовалось/хваталось бы
     *  не там, где кабинет реально нарисован (баг-репорт: "привязки для уменьшенных
     *  кабинетов сломались" — та же причина, что и у {@code SceneCanvasPanel
     *  .cabinetAtPoint}/{@code locateCabinet}). {@code workspace} может быть
     *  {@code null} — тогда переопределение не резолвится, как и раньше. */
    public static java.awt.Rectangle cabinetScreenRect(CabinetInstance cab, CabinetType type, int cellW, int cellH,
            int offX, int offY, Workspace workspace) {
        CabinetType effective = effectiveTypeOf(cab, type, workspace);
        int ew = (int) Math.round(ScreenLogic.effectiveCellW(effective, type, cellW));
        int eh = (int) Math.round(ScreenLogic.effectiveCellH(effective, type, cellH));
        return new java.awt.Rectangle(cabX(cab, type, cellW, offX), cabY(cab, type, cellH, offY), ew, eh);
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
        paintScheme(g2, scr, type, power, cellW, cellH, offX, offY, workspace, powerChains, signalChains,
                List.of(), false);
    }

    /** {@code sceneControllers} — контроллеры ВСЕЙ сцены этого экрана (см. {@code
     *  AppModel.controllersInScene}), НЕ только {@code scr.getControllers()} —
     *  контроллеры физически хранятся под конкретным экраном, но используются как
     *  общий для сцены пул (см. {@code AppModel}'s комментарий у {@code
     *  controllersInScene}), поэтому метка порта цепочки должна резолвиться той же
     *  сценовой нумерацией, что и сайдбар прописи ({@code
     *  SignalStagePanel.portDisplayLabel}), а не только контроллерами ЭТОГО экрана
     *  — баг-репорт: "почему на расключении нумерация порта не совпадает с
     *  выбранным портом в контроллере" — контроллер, добавленный, пока был выбран
     *  ДРУГОЙ экран сцены, вообще не находился, и метка тихо откатывалась на сырой
     *  номер порта без резолва пула/карты (см. {@link #controllerForPort}).
     *  {@code powerUnitKw} — единица отображения мощности в подписи ячейки (см.
     *  {@code UserProfile#isPowerUnitKw}), только для {@code power == true}. Оставлен
     *  как перегрузка (не единственная сигнатура) — большинство вызывающих кодов вне
     *  Питание/Сигнал этапов не имеют под рукой SettingsManager и рисуют схему без
     *  учёта юнитов (сигнальный режим — там power всегда false, юнит не участвует). */
    public static void paintScheme(Graphics2D g2, Screen scr, CabinetType type, boolean power,
                                   int cellW, int cellH, int offX, int offY, Workspace workspace,
                                   List<PowerChain> powerChains, List<SignalChain> signalChains,
                                   List<com.vjstb.ledscheme.model.ControllerInstance> sceneControllers,
                                   boolean powerUnitKw) {
        paintScheme(g2, scr, type, power, cellW, cellH, offX, offY, workspace, powerChains, signalChains,
                sceneControllers, powerUnitKw, true);
    }

    /** {@code showCabinetIndexLabels} — подписи "строка,столбец" на кабинетах
     *  (см. {@link #drawCabinetIndexLabel}); {@code false} — для сводных картинок
     *  масштаба целой площадки, где эти подписи всё равно нечитаемы и не нужны
     *  (см. {@link #renderScreensOverviewImage}, баг-репорт: "нумерацию кабинетов
     *  уберём"). Остальные перегрузки всегда рисуют их (интерактивный холст
     *  прописи — привычный вид для клика по кабинетам). */
    public static void paintScheme(Graphics2D g2, Screen scr, CabinetType type, boolean power,
                                   int cellW, int cellH, int offX, int offY, Workspace workspace,
                                   List<PowerChain> powerChains, List<SignalChain> signalChains,
                                   List<com.vjstb.ledscheme.model.ControllerInstance> sceneControllers,
                                   boolean powerUnitKw, boolean showCabinetIndexLabels) {
        paintSchemeGrid(g2, scr, type, cellW, cellH, offX, offY, workspace, showCabinetIndexLabels);
        paintSchemeChains(g2, scr, power, cellW, cellH, offX, offY, type, workspace,
                powerChains, signalChains, sceneControllers);
    }

    /** Часть {@link #paintScheme} — только сетка кабинетов (контур ячейки + подпись
     *  "строка,столбец"), без линий цепочек. Вынесено отдельно, чтобы вызывающий код
     *  мог вставить свой оверлей МЕЖДУ сеткой и цепочками (см. {@link #paintSchemeChains}
     *  и {@code SceneCanvasPanel.drawCabinetOverrideMarks}) — баг-репорт 2026-09-16:
     *  подсветка переопределения типа/формы кабинета рисовалась одним проходом ПОСЛЕ
     *  всего {@code paintScheme} (в т.ч. после цепочек) и своей сплошной заливкой
     *  перекрывала уже нарисованную линию цепочки; правильный порядок — заливка ПОД
     *  цепочкой (между сеткой и цепочками), а не над ней. Обычные вызывающие коды
     *  (CanvasPanel, PortPickerPanel и т.п.), которым этот порядок безразличен,
     *  по-прежнему используют единый {@link #paintScheme}. */
    public static void paintSchemeGrid(Graphics2D g2, Screen scr, CabinetType type,
                                        int cellW, int cellH, int offX, int offY, Workspace workspace) {
        paintSchemeGrid(g2, scr, type, cellW, cellH, offX, offY, workspace, true);
    }

    /** {@code showCabinetIndexLabels} — см. javadoc {@link #paintScheme}'s перегрузка
     *  с тем же параметром ({@link #renderScreensOverviewImage} — единственный
     *  вызывающий код, которому нужен {@code false}; интерактивный холст прописи
     *  через {@link SceneCanvasPanel} по-прежнему зовёт 8-параметровую перегрузку
     *  выше, всегда получая подписи). */
    public static void paintSchemeGrid(Graphics2D g2, Screen scr, CabinetType type,
                                        int cellW, int cellH, int offX, int offY, Workspace workspace,
                                        boolean showCabinetIndexLabels) {
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

            if (showCabinetIndexLabels) {
                drawCabinetIndexLabel(g2, cab, x, y, ew, eh);
            }
        }
    }

    /** Часть {@link #paintScheme} — только линии цепочек, см. javadoc {@link #paintSchemeGrid}. */
    public static void paintSchemeChains(Graphics2D g2, Screen scr, boolean power,
                                         int cellW, int cellH, int offX, int offY, CabinetType type,
                                         Workspace workspace, List<PowerChain> powerChains,
                                         List<SignalChain> signalChains,
                                         List<com.vjstb.ledscheme.model.ControllerInstance> sceneControllers) {
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
                        false, cellW, cellH, offX, offY, type, workspace,
                        signalChainLabel(sceneControllers, chain, workspace),
                        signalChainEndLabel(sceneControllers, chain, workspace));
            }
        }
    }

    /** Подпись «строка,столбец» кабинета — общая точка правды для {@link #paintScheme}
     *  и для оверлеев поверх него (например, {@code SceneCanvasPanel.drawCabinetOverrideMarks},
     *  который красит ячейку сплошной заливкой ПОСЛЕ paintScheme — баг-репорт 2026-09-14
     *  «из-за заливки не видно порядкового номера кабинетов»: подпись рисовалась только
     *  один раз, здесь, и следующий проход её молча перекрывал; теперь оверлеи вызывают
     *  этот метод сами, чтобы подпись оставалась поверх любой заливки).
     *  <p>Не рисует ничего, если ячейка мельче {@code MIN_LABEL_CELL_PX} — иначе на сильном
     *  зум-ауте текст соседних кабинетов наезжает друг на друга и на линии цепочек,
     *  превращаясь в нечитаемое пятно. Размер шрифта пропорционален высоте ячейки
     *  ({@code eh * 0.28}, см. баг-репорт «подписи покрупнее раза в 2»), но дополнительно
     *  ужимается по ширине через {@link #fitFontToWidth} — раньше был жёсткий пол в 18пт
     *  БОЛЬШЕ порога видимости (16px), из-за чего подпись гарантированно вылезала за
     *  собственную ячейку на зуме около этого порога и не уменьшалась дальше при
     *  дальнейшем отдалении (баг-репорт «индексы кабинетов остаются постоянной высоты
     *  шрифта, при отдалении получается грязно») — теперь пол общий с порогом видимости,
     *  а верхняя граница нигде не зажата явно: и высота, и ширина ячейки одинаково
     *  ограничивают размер шрифта, так что подпись всегда вписывается в габариты кабинета. */
    private static final int MIN_LABEL_CELL_PX = 16;

    public static void drawCabinetIndexLabel(Graphics2D g2, CabinetInstance cab, int x, int y, int ew, int eh) {
        if (eh < MIN_LABEL_CELL_PX || ew < MIN_LABEL_CELL_PX) {
            return;
        }
        String text = cab.getDisplayRow() + "," + cab.getDisplayCol();
        float baseSize = Math.max(MIN_LABEL_CELL_PX / 2f, eh * 0.28f);
        float size = fitFontToWidth(g2, text, Font.PLAIN, baseSize, MIN_LABEL_CELL_PX / 2f, ew - 6);
        Font prevFont = g2.getFont();
        Color prevColor = g2.getColor();
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, size));
        g2.setColor(new Color(0xc0, 0xc8, 0xd0));
        g2.drawString(text, x + 4, y + g2.getFont().getSize() + 2);
        g2.setFont(prevFont);
        g2.setColor(prevColor);
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
        paintWiringDiagram(g2, scr, type, power, cellW, cellH, offX, offY, workspace, powerChains, signalChains,
                List.of(), false);
    }

    /** {@code sceneControllers} — см. javadoc {@link #paintScheme}'s перегрузка с тем же
     *  параметром (нужны, чтобы метка порта на кабинете резолвилась сценовой, а не
     *  только пер-экранной нумерацией контроллеров). {@code powerUnitKw} — см. javadoc
     *  {@link #paintScheme}'s перегрузка с тем же параметром. */
    public static void paintWiringDiagram(Graphics2D g2, Screen scr, CabinetType type, boolean power,
                                           int cellW, int cellH, int offX, int offY, Workspace workspace,
                                           List<PowerChain> powerChains, List<SignalChain> signalChains,
                                           List<com.vjstb.ledscheme.model.ControllerInstance> sceneControllers,
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
                lines.add(power ? "L" + m.phase()
                        : portLabel(sceneControllers, workspace, m.port() != null ? m.port() : 0));
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
                    String lbl = portLabel(sceneControllers, workspace, chain.getPortNumber());
                    // Раньше резервная цепочка получала префикс "рез:" — на мелких
                    // ячейках плашки бейджа он не помещался и обрезался до нечитаемого
                    // "pe...". Различаем резерв ЦВЕТОМ текста (см. drawStartLabelBadge),
                    // а не текстом, который всё равно негде показать целиком.
                    addStartLabel(startLabelsByCabinet, chain.getCabinetInstanceIds(),
                            new LabelLine(lbl, chain.isBackup()));
                }
                if (chain.getBackupPortNumber() != null && !chain.getCabinetInstanceIds().isEmpty()) {
                    String endLbl = portLabel(sceneControllers, workspace, chain.getBackupPortNumber());
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

    /** Размер шрифта для строки, которая должна поместиться по ширине {@code maxWidth} —
     *  {@code standardSize} (стандартный, ожидаемый размер в большинстве случаев), уменьшенный
     *  пропорционально, если ИМЕННО этот текст (длинное имя экрана, много цепочек в подписи)
     *  в него не влезает, но не ниже {@code minSize} (используется дальше как обычный шрифт —
     *  если не влезает и он, {@link #clipToWidth} по месту вызова не задействован здесь
     *  намеренно: заголовок схемы — не мелкая ячейка сетки, обрезание с "…" в нём выглядело бы
     *  хуже, чем чуть более мелкий, но полностью читаемый текст). Не меняет текущий шрифт/цвет
     *  {@code g2} — только измеряет, вызывающая сторона сама выставляет финальный шрифт. */
    private static float fitFontToWidth(Graphics2D g2, String text, int style, float standardSize, float minSize,
            int maxWidth) {
        java.awt.FontMetrics fm = g2.getFontMetrics(g2.getFont().deriveFont(style, standardSize));
        int textW = fm.stringWidth(text);
        if (textW <= maxWidth || maxWidth <= 0) {
            return standardSize;
        }
        return Math.max(minSize, standardSize * maxWidth / (float) textW);
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

    // Прежняя построчная полоса контроллеров под сеткой расключения сигнала (видна
    // только у экранов, владеющих контроллером напрямую — баг-репорт "для некоторых
    // экранов текст есть, для некоторых нет") убрана: вместо неё — общий перетаскиваемый
    // авто-блок «Легенда портов» на холсте (см. AppModel.addSignalPortLegendNode,
    // SchemaNode#isAutoPortLegend, SchemaCanvasPanel.drawPortLegendContent).

    /** Контроллер, которому принадлежит порт, ВМЕСТЕ с его смещением (порты
     *  нумеруются подряд по назначенным экрану контроллерам: 1..N1 — первый,
     *  N1+1..N1+N2 — второй и т.д.) — {@code controller == null}, если
     *  контроллеров нет (порты вручную) или порт вне диапазона; тогда
     *  {@code offset} не имеет смысла. */
    private record ControllerAndOffset(com.vjstb.ledscheme.model.ControllerInstance controller, int offset) {
    }

    /** Ищет владельца {@code port} СРЕДИ {@code sceneControllers} (контроллеров ВСЕЙ
     *  сцены — см. javadoc {@link #paintScheme}'s одноимённый параметр), НЕ только
     *  контроллеров одного экрана — та же сценовая нумерация/порядок обхода, что и
     *  {@code AppModel.controllerForPort}/{@code portOffsetOf}, которую использует
     *  сайдбар прописи. Раньше здесь принимался {@code Screen} и обходился только
     *  его собственный {@code scr.getControllers()} — контроллер, физически
     *  добавленный под ДРУГИМ экраном той же сцены, вообще не находился (см. баг-
     *  репорт у {@code paintScheme}). */
    private static ControllerAndOffset controllerForPort(
            List<com.vjstb.ledscheme.model.ControllerInstance> sceneControllers, Workspace workspace, int port) {
        if (workspace == null) {
            return new ControllerAndOffset(null, 0);
        }
        int offset = 0;
        for (com.vjstb.ledscheme.model.ControllerInstance ci : sceneControllers) {
            com.vjstb.ledscheme.model.ControllerType t = workspace.controllerTypeById(ci.getControllerTypeId());
            int count = t != null ? t.effectivePortCount() : 0;
            if (port > offset && port <= offset + count) {
                return new ControllerAndOffset(ci, offset);
            }
            offset += count;
        }
        return new ControllerAndOffset(null, 0);
    }

    /** Опознавательная подпись порта: номер порта, и — если в СЦЕНЕ назначено
     *  НЕСКОЛЬКО контроллеров (иначе принадлежность и так однозначна) — номер
     *  контроллера, которому этот порт принадлежит.
     *
     * <p>Номер порта — НЕ сырой сквозной {@code port} (тот считает подряд ВСЕ
     * выходные порты контроллера, включая fiber-группы — баг-репорт: "почему на
     * расключении нумерация порта не совпадает с выбранным портом в
     * контроллере" — сырой номер расходился с "Портом К1·N", который показывает
     * сайдбар {@code SignalStagePanel.portDisplayLabel}, ровно на число fiber-
     * портов, стоящих перед этим на той же карте), а РЕЗОЛВЛЕННЫЙ через {@link
     * com.vjstb.ledscheme.model.ControllerType#ethernetPoolLocalPort} — тот же
     * номер, который видит пользователь в сетке портов сайдбара. Для контроллера
     * с несколькими картами/пулами Ethernet-нумерации — тот же формат
     * "К{карта}·{порт}", что и в {@code portDisplayLabel}, иначе просто номер
     * порта в пределах ЕГО ethernet-пула (не сырой номер контроллера).
     *
     * <p>{@code sceneControllers} — контроллеры ВСЕЙ сцены (см. javadoc {@link
     * #paintScheme}), а не только {@code scr.getControllers()} — второй баг-репорт
     * на ту же тему: контроллер, добавленный, пока был выбран ДРУГОЙ экран сцены,
     * не находился вовсе, и метка тихо откатывалась на сырой номер порта. */
    static String portLabel(List<com.vjstb.ledscheme.model.ControllerInstance> sceneControllers,
                             Workspace workspace, int port) {
        ControllerAndOffset co = controllerForPort(sceneControllers, workspace, port);
        com.vjstb.ledscheme.model.ControllerInstance ci = co.controller();
        com.vjstb.ledscheme.model.ControllerType t = ci != null && workspace != null
                ? workspace.controllerTypeById(ci.getControllerTypeId()) : null;
        String portPart = resolvedPortPart(t, port - co.offset(), port);
        if (sceneControllers.size() <= 1) {
            return "P" + portPart;
        }
        int idx = ci != null ? sceneControllers.indexOf(ci) + 1 : 0;
        return (idx > 0 ? "C" + idx + "·" : "") + "P" + portPart;
    }

    /** Часть подписи ПОСЛЕ "P"/"C{n}·P" — резолвит {@code controllerLocalPort}
     *  (сырой, в пределах контроллера) через {@link com.vjstb.ledscheme.model
     *  .ControllerType#ethernetPoolLocalPort} в "К{карта}·{порт}" (несколько
     *  Ethernet-пулов) или просто номер порта В ПРЕДЕЛАХ его пула (один пул).
     *  {@code null} от {@code ethernetPoolLocalPort} (fiber-порт или контроллер
     *  не резолвился) — защитный откат на сырой глобальный {@code rawFallbackPort},
     *  не должен встречаться для реально сохранённой цепочки (та строится только
     *  на Ethernet-годных портах), но лучше показать хоть что-то, чем ничего. */
    private static String resolvedPortPart(com.vjstb.ledscheme.model.ControllerType t, int controllerLocalPort,
                                            int rawFallbackPort) {
        if (t == null) {
            return String.valueOf(rawFallbackPort);
        }
        int[] pool = t.ethernetPoolLocalPort(controllerLocalPort);
        if (pool == null) {
            return String.valueOf(rawFallbackPort);
        }
        return t.ethernetPoolCount() > 1 ? "К" + (pool[0] + 1) + "·" + pool[1] : String.valueOf(pool[1]);
    }

    /** Метка НАЧАЛА сигнальной цепочки (первый кабинет) — основной порт.
     *  {@code sceneControllers} — см. javadoc {@link #portLabel}. */
    static String signalChainLabel(List<com.vjstb.ledscheme.model.ControllerInstance> sceneControllers,
                                    SignalChain chain, Workspace workspace) {
        Integer port = chain.getPortNumber();
        return port == null ? null : portLabel(sceneControllers, workspace, port);
    }

    /** Метка КОНЦА сигнальной цепочки (последний кабинет): если у порта назначен
     *  резервный порт — это конец цепочки, где подключён резерв, поэтому метка
     *  показывает резервный порт, а не повторяет основной. */
    static String signalChainEndLabel(List<com.vjstb.ledscheme.model.ControllerInstance> sceneControllers,
                                       SignalChain chain, Workspace workspace) {
        Integer backupPort = chain.getBackupPortNumber();
        return backupPort != null ? portLabel(sceneControllers, workspace, backupPort)
                : signalChainLabel(sceneControllers, chain, workspace);
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

    /** Кружок с меткой порта/фазы на кабинете — начало/конец цепочки. Round (баг-репорт:
     *  "обозначение портов и фаз предлагаю сделать как на блок-схемах — кружок на
     *  практически весь блок") — раньше это была тонкая пилюля до 20px в углу ячейки,
     *  почти незаметная на крупной сетке; теперь полноценный круг, центрированный в самой
     *  ячейке кабинета и занимающий большую часть её меньшей стороны (как круглые
     *  бейджи портов на общей блок-схеме площадки). Текст обрезается по ширине круга
     *  (см. {@link #clipToWidth}) — при длинных подписях (например "C12·P8") на мелкой
     *  сетке лучше усечь, чем вылезти за пределы кружка. */
    static void drawChainEndpointLabel(Graphics2D g2, Screen scr, String cabId, Color color, CabinetType type,
                                                int cellW, int cellH, int offX, int offY, String label) {
        CabinetInstance cab = scr.cabinetById(cabId);
        if (cab == null) {
            return;
        }
        int x = cabX(cab, type, cellW, offX);
        int y = cabY(cab, type, cellH, offY);
        int d = Math.max(16, (int) Math.round(Math.min(cellW, cellH) * 0.5));
        int cx = x + cellW / 2;
        int cy = y + cellH / 2;
        Font f = g2.getFont().deriveFont(Font.BOLD, Math.max(9f, d * 0.24f));
        g2.setFont(f);
        java.awt.FontMetrics fm = g2.getFontMetrics();
        String clipped = clipToWidth(g2, label, (int) Math.round(d * 0.8));
        int textW = fm.stringWidth(clipped);
        g2.setColor(color);
        g2.fillOval(cx - d / 2, cy - d / 2, d, d);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(Math.max(1f, d * 0.03f)));
        g2.drawOval(cx - d / 2, cy - d / 2, d, d);
        g2.drawString(clipped, cx - textW / 2, cy + fm.getAscent() / 2 - 1);
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
        return renderImage(scr, type, power, base, null, List.of(), List.of(), List.of(), false);
    }

    /** То же, но вес/мощность в заголовке учитывают переопределение типа кабинета по ячейкам.
     *  Цепочки хранятся на уровне сцены (Task #78) — вызывающая сторона передаёт сюда только те,
     *  что физически затрагивают ИМЕННО этот экран (например, {@code AppModel.powerChainsTouchingScreen}),
     *  т.к. изображение показывает один изолированный экран без контекста остальной сцены. */
    public static BufferedImage renderImage(Screen scr, CabinetType type, boolean power, int base,
                                             Workspace workspace, List<PowerChain> powerChains,
                                             List<SignalChain> signalChains) {
        return renderImage(scr, type, power, base, workspace, powerChains, signalChains, List.of(), false);
    }

    /** {@code powerUnitKw} — единица отображения мощности в заголовке/ячейках (см.
     *  {@code UserProfile#isPowerUnitKw}), см. javadoc {@link #paintScheme}'s перегрузка
     *  для того же параметра. */
    public static BufferedImage renderImage(Screen scr, CabinetType type, boolean power, int base,
                                             Workspace workspace, List<PowerChain> powerChains,
                                             List<SignalChain> signalChains, boolean powerUnitKw) {
        return renderImage(scr, type, power, base, workspace, powerChains, signalChains, List.of(), powerUnitKw);
    }

    /** {@code dpiScale} — множитель качества экспорта (см. {@code UserProfile#getDocExportDpi},
     *  1.0 = 72dpi = прежнее поведение) — весь рисунок (сетка, текст, линии) равномерно
     *  увеличивается через {@link Graphics2D#scale}, а не пересчётом внутренней геометрии,
     *  поэтому пропорции остаются ТЕМИ ЖЕ, что при 1.0, просто с бОльшим числом пикселей на
     *  тот же физический размер при печати (см. {@link #writeJpeg(BufferedImage, File, int)}
     *  — соответствующее DPI пишется в метаданные файла, иначе увеличенная картинка при
     *  печати просто вышла бы физически крупнее, а не резче). */
    public static BufferedImage renderImage(Screen scr, CabinetType type, boolean power, int base,
                                             Workspace workspace, List<PowerChain> powerChains,
                                             List<SignalChain> signalChains, boolean powerUnitKw, double dpiScale) {
        return renderImage(scr, type, power, base, workspace, powerChains, signalChains, List.of(), powerUnitKw,
                dpiScale);
    }

    /** {@code sceneControllers} — контроллеры ВСЕЙ сцены этого экрана (см. {@link
     *  #paintScheme}'s одноимённый параметр) — нужны, чтобы метки портов цепочек
     *  резолвились ТОЙ ЖЕ нумерацией, что и сайдбар прописи (см. баг-репорт у
     *  {@link #controllerForPort}). Пустой список — как раньше (сырой номер порта). */
    public static BufferedImage renderImage(Screen scr, CabinetType type, boolean power, int base,
                                             Workspace workspace, List<PowerChain> powerChains,
                                             List<SignalChain> signalChains,
                                             List<com.vjstb.ledscheme.model.ControllerInstance> sceneControllers,
                                             boolean powerUnitKw) {
        return renderImage(scr, type, power, base, workspace, powerChains, signalChains, sceneControllers,
                powerUnitKw, 1.0);
    }

    /** Полная сигнатура — см. javadoc у 8-параметрической перегрузки (sceneControllers)
     *  и у перегрузки с dpiScale выше. */
    public static BufferedImage renderImage(Screen scr, CabinetType type, boolean power, int base,
                                             Workspace workspace, List<PowerChain> powerChains,
                                             List<SignalChain> signalChains,
                                             List<com.vjstb.ledscheme.model.ControllerInstance> sceneControllers,
                                             boolean powerUnitKw, double dpiScale) {
        Dimension c = cellSize(type, base);
        int pad = 24;
        int gridW = scr.getCols() * c.width;
        int gridH = scr.getRows() * c.height;
        // Round (баг-репорт "перебор" — headerH=20% полной высоты на большой сетке разрослась
        // в аршинный шрифт, который вылезал за пределы картинки) -- вернулись к ФИКСИРОВАННОМУ
        // headerH, как до предыдущего раунда, просто вдвое больше (52 -> 104), не зависящему
        // от gridH. "Вариативность" — не в headerH, а в самом шрифте заголовка/подзаголовка
        // (см. ниже): стандартный размер — тоже ровно вдвое больше прежнего (16->32, 12->24),
        // но ужимается, если конкретное имя экрана в него не помещается по ширине.
        int headerH = 104;
        int w = gridW + pad * 2;
        int h = gridH + pad * 2 + headerH;

        BufferedImage img = new BufferedImage(Math.max(1, (int) Math.round(w * dpiScale)),
                Math.max(1, (int) Math.round(h * dpiScale)), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.scale(dpiScale, dpiScale);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setColor(Palette.BG);
        g2.fillRect(0, 0, w, h);

        // заголовок -- стандартный размер вдвое больше исходного (16/12 -> 32/24pt), но
        // ужимается (см. fitFontToWidth), если конкретный текст (длинное имя экрана/много
        // цепочек в подзаголовке) не помещается по ширине картинки — не обрезаем и не вылезаем
        // за кадр, а плавно уменьшаем шрифт под конкретный случай.
        ScreenStats stats = ScreenLogic.stats(scr, type, workspace);
        int availW = w - pad * 2;
        String title = "Экран «" + scr.getName() + "» — " + (power ? "Питание" : "Сигнал");
        float titleSize = fitFontToWidth(g2, title, Font.BOLD, 32f, 16f, availW);
        g2.setColor(Palette.TEXT);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, titleSize));
        g2.drawString(title, pad, 48);
        String sub = scr.getCols() + "×" + scr.getRows() + " каб. · "
                + stats.resolutionWidthPx() + "×" + stats.resolutionHeightPx() + " px · "
                + trim(stats.physicalWidthMm()) + "×" + trim(stats.physicalHeightMm()) + " мм · "
                + UiKit.fmtPower(stats.totalPowerW(), powerUnitKw) + " · " + trim(stats.totalWeightKg()) + " кг";
        float subSize = fitFontToWidth(g2, sub, Font.PLAIN, 24f, 11f, availW);
        g2.setColor(Palette.MUTED);
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, subSize));
        g2.drawString(sub, pad, 84);

        paintScheme(g2, scr, type, power, c.width, c.height, pad, pad + headerH, workspace,
                powerChains, signalChains, sceneControllers, powerUnitKw);
        g2.dispose();
        return img;
    }

    /** Сохраняет изображение в JPEG с высоким качеством и {@code dpi} в метаданных
     *  JFIF (иначе большинство просмотрщиков/принтеров считают файл 72dpi по
     *  умолчанию — см. {@link #renderImage(Screen, CabinetType, boolean, int, Workspace,
     *  List, List, boolean, double)} про парный параметр {@code dpiScale}: этот метод
     *  только подписывает уже отрисованные пиксели, размер картинки увеличивает ТОТ). */
    public static void writeJpeg(BufferedImage img, File file, int dpi) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.92f);
            writer.setOutput(ios);
            ImageTypeSpecifier typeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(img.getType());
            IIOMetadata metadata = writer.getDefaultImageMetadata(typeSpecifier, param);
            setJfifDpi(metadata, dpi);
            writer.write(null, new IIOImage(img, null, metadata), param);
        } finally {
            writer.dispose();
        }
    }

    /** Отдельная картинка (PNG, см. {@code CurrentSchemeExporter#exportPng}) с той же
     *  таблицей "Экран/Main/Backup", что рисует авто-блок «Легенда портов» на холсте
     *  (см. {@code SchemaCanvasPanel#drawPortLegendContent}, {@link
     *  AppModel#signalPortLegendRows}) — но БЕЗ клипа по размеру блока (картинка ровно
     *  по размеру содержимого) и БЕЗ равного деления Main/Backup: тут место не
     *  ограничено, поэтому каждая колонка — по своему самому длинному значению, а не
     *  поровну между двумя, как в компактном блоке на холсте. {@code dpiScale} —
     *  тот же множитель качества, что у {@link #renderImage(Screen, CabinetType,
     *  boolean, int, Workspace, List, List, boolean, double)}. */
    public static BufferedImage renderPortLegendImage(String sceneName,
            List<AppModel.SignalPortLegendRow> rows, double dpiScale) {
        Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, 20);
        Font headerFont = new Font(Font.SANS_SERIF, Font.BOLD, 15);
        Font rowFont = new Font(Font.SANS_SERIF, Font.PLAIN, 15);

        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D pg = probe.createGraphics();
        pg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        String title = "Легенда портов" + (sceneName != null && !sceneName.isEmpty() ? " — " + sceneName : "");
        pg.setFont(titleFont);
        int titleW = pg.getFontMetrics().stringWidth(title);
        int titleH = pg.getFontMetrics().getHeight();

        pg.setFont(headerFont);
        java.awt.FontMetrics headerFm = pg.getFontMetrics();
        int col1W = headerFm.stringWidth("Экран");
        int col2W = headerFm.stringWidth("Main");
        int col3W = headerFm.stringWidth("Backup");
        int headerH = headerFm.getHeight();

        pg.setFont(rowFont);
        java.awt.FontMetrics rowFm = pg.getFontMetrics();
        for (AppModel.SignalPortLegendRow row : rows) {
            col1W = Math.max(col1W, rowFm.stringWidth(row.screenName()));
            col2W = Math.max(col2W, rowFm.stringWidth(row.main()));
            col3W = Math.max(col3W, rowFm.stringWidth(row.backup()));
        }
        int rowH = rowFm.getHeight() + 10;
        pg.dispose();

        int pad = 24;
        int colGap = 32;
        col1W += 6;
        col2W += 6;
        col3W += 6;
        int tableW = col1W + colGap + col2W + colGap + col3W;
        int w = Math.max(titleW, tableW) + pad * 2;
        int col1X = pad;
        int col2X = col1X + col1W + colGap;
        int col3X = col2X + col2W + colGap;

        int titleY = pad + titleH - 6;
        int headerY = titleY + 24 + headerH;
        int firstRowY = headerY + 16;
        int h = rows.isEmpty() ? headerY + pad
                : firstRowY + (rows.size() - 1) * rowH + rowFm.getDescent() + pad;

        BufferedImage img = new BufferedImage(Math.max(1, (int) Math.round(w * dpiScale)),
                Math.max(1, (int) Math.round(h * dpiScale)), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.scale(dpiScale, dpiScale);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Palette.BG);
        g2.fillRect(0, 0, w, h);

        g2.setColor(Palette.TEXT);
        g2.setFont(titleFont);
        g2.drawString(title, pad, titleY);

        g2.setFont(headerFont);
        g2.drawString("Экран", col1X, headerY);
        g2.drawString("Main", col2X, headerY);
        g2.drawString("Backup", col3X, headerY);
        g2.setColor(new Color(0, 0, 0, 120));
        g2.drawLine(pad, headerY + 6, w - pad, headerY + 6);

        g2.setFont(rowFont);
        g2.setColor(Palette.TEXT);
        int y = firstRowY;
        for (AppModel.SignalPortLegendRow row : rows) {
            g2.drawString(row.screenName(), col1X, y);
            g2.drawString(row.main(), col2X, y);
            g2.drawString(row.backup(), col3X, y);
            y += rowH;
        }

        g2.dispose();
        return img;
    }

    /** Экраны сцены сверху — не декоративные прямоугольники, а тот же вид отдельными
     *  кабинетами, что и в детальном обзоре сцены ({@code SceneCanvasPanel}, режим
     *  «Кабинеты по отдельности», см. {@link #paintScheme}) — риггеру нужно видеть
     *  форму сборки (вырезы, треугольные угловые кабинеты и т.п.), а не голый
     *  силуэт (баг-репорт: "должны рисоваться не прямоугольники, а вид как при
     *  включенных кабинеты по отдельности"). Раскладка экранов на картинке — их
     *  РЕАЛЬНЫЕ X/Y из окна «Настройка» (один общий масштаб на всю площадку, как в
     *  {@code SceneCanvasPanel.boundsMm}/{@code scaleFor}), а не список слева
     *  направо — баг-репорт: "раскладка экранов пусть соответствует той что в окне
     *  сетапа, не просто же так инженер их там расставляет". Внутри каждого экрана
     *  подписи "строка,столбец" на кабинетах НЕ рисуются (баг-репорт: "нумерацию
     *  кабинетов уберём") — в масштабе площадки целиком они всё равно нечитаемы и
     *  для сводной таблицы не нужны; номер экрана (чип в углу) — это ДРУГАЯ
     *  нумерация, по строкам таблицы, оставлена для сверки картинки с таблицей.
     *  Таблица ниже: №, экран, размеры, разрешение, вес, нагрузка, тип монтажа,
     *  примечания (баг-репорт: "нужно указывать названия экранов и примечания к
     *  ним, а также электрическую нагрузку") — примечания берутся из общего поля
     *  {@link Screen#getNotes()} (не из {@code riggingNotes}/{@code structureNotes},
     *  которые видны только при соответствующем типе монтажа — баг-репорт: "для
     *  экранов сейчас негде писать примечания"). По образцу {@link
     *  #renderPortLegendImage} (тот же приём измерения через "пробный" {@link
     *  Graphics2D}, шрифты/отступы/разделитель). Габариты/разрешение — по
     *  НОМИНАЛЬНОЙ сетке экрана, вес/нагрузка — по фактически стоящим (не скрытым)
     *  кабинетам (см. {@link ScreenLogic#stats}). */
    public static BufferedImage renderScreensOverviewImage(String sceneName, AppModel model,
            List<Screen> screens, double dpiScale) {
        record Row(String number, String name, String size, String resolution, String weight, String load,
                   String mount, String notes, double widthMm, double heightMm) {
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < screens.size(); i++) {
            Screen s = screens.get(i);
            CabinetType type = model.typeOf(s);
            ScreenStats st = ScreenLogic.stats(s, type, model.getWorkspace());
            String notes = s.getNotes();
            rows.add(new Row(String.valueOf(i + 1),
                    s.getName() == null || s.getName().isEmpty() ? "—" : s.getName(),
                    trim(st.physicalWidthMm()) + "×" + trim(st.physicalHeightMm()) + " мм",
                    st.resolutionWidthPx() + "×" + st.resolutionHeightPx() + " px",
                    trim(st.totalWeightKg()) + " кг",
                    formatLoad(st.totalPowerW()),
                    s.getMountType().getLabel(),
                    notes == null || notes.isBlank() ? "—" : notes,
                    st.physicalWidthMm(), st.physicalHeightMm()));
        }

        Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, 20);
        Font headerFont = new Font(Font.SANS_SERIF, Font.BOLD, 15);
        Font rowFont = new Font(Font.SANS_SERIF, Font.PLAIN, 15);
        Font screenNumberFont = new Font(Font.SANS_SERIF, Font.BOLD, 16);

        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D pg = probe.createGraphics();
        pg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        String title = "Экраны" + (sceneName != null && !sceneName.isEmpty() ? " — " + sceneName : "");
        pg.setFont(titleFont);
        int titleW = pg.getFontMetrics().stringWidth(title);
        int titleH = pg.getFontMetrics().getHeight();

        pg.setFont(headerFont);
        java.awt.FontMetrics headerFm = pg.getFontMetrics();
        String[] headers = {"№", "Экран", "Размеры", "Разрешение", "Вес", "Нагрузка", "Тип монтажа", "Примечания"};
        int[] colW = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            colW[i] = headerFm.stringWidth(headers[i]);
        }
        int headerH = headerFm.getHeight();

        // "Примечания" — единственная колонка переменной, потенциально большой
        // длины (свободный текст) — ограничиваем её отдельным максимумом, а не
        // даём разрастись до ширины самой длинной записи, иначе одно длинное
        // примечание растягивало бы всю картинку на всю его длину.
        int notesMaxW = 420;
        pg.setFont(rowFont);
        java.awt.FontMetrics rowFm = pg.getFontMetrics();
        for (Row r : rows) {
            colW[0] = Math.max(colW[0], rowFm.stringWidth(r.number()));
            colW[1] = Math.max(colW[1], rowFm.stringWidth(r.name()));
            colW[2] = Math.max(colW[2], rowFm.stringWidth(r.size()));
            colW[3] = Math.max(colW[3], rowFm.stringWidth(r.resolution()));
            colW[4] = Math.max(colW[4], rowFm.stringWidth(r.weight()));
            colW[5] = Math.max(colW[5], rowFm.stringWidth(r.load()));
            colW[6] = Math.max(colW[6], rowFm.stringWidth(r.mount()));
            colW[7] = Math.min(notesMaxW, Math.max(colW[7], rowFm.stringWidth(r.notes())));
        }
        int rowH = rowFm.getHeight() + 10;
        pg.dispose();

        int pad = 24;
        int colGap = 28;
        int[] colX = new int[headers.length];
        colX[0] = pad;
        for (int i = 0; i < headers.length; i++) {
            colW[i] += 6;
            if (i > 0) {
                colX[i] = colX[i - 1] + colW[i - 1] + colGap;
            }
        }
        int tableW = colX[headers.length - 1] + colW[headers.length - 1];

        // Раскладка экранов — их РЕАЛЬНЫЕ X/Y (см. javadoc метода), один общий
        // масштаб на всю площадку (как SceneCanvasPanel.boundsMm/scaleFor), а не
        // независимый масштаб на каждый экран — иначе относительное расположение
        // и размеры экранов друг относительно друга исказились бы. planMaxW/H —
        // целевой размер этой области картинки (масштаб подбирается вписыванием
        // общего бокса экранов в эти пределы), нижняя/верхняя граница масштаба —
        // подстраховка от вырожденных случаев (один кабинет или экраны, разнесённые
        // на десятки метров).
        int planMaxW = 900;
        int planMaxH = 480;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double[][] ext = new double[screens.size()][];
        for (int i = 0; i < screens.size(); i++) {
            Screen s = screens.get(i);
            CabinetType type = model.typeOf(s);
            double[] e = type != null ? ScreenLogic.cabinetExtentMm(s, type, model.getWorkspace())
                    : new double[]{0, 0, 100, 100};
            ext[i] = e;
            minX = Math.min(minX, s.getPosXMm() + e[0]);
            minY = Math.min(minY, s.getPosYMm() + e[1]);
            maxX = Math.max(maxX, s.getPosXMm() + e[2]);
            maxY = Math.max(maxY, s.getPosYMm() + e[3]);
        }
        boolean hasScreens = !rows.isEmpty();
        double boundW = hasScreens ? Math.max(1, maxX - minX) : 1;
        double boundH = hasScreens ? Math.max(1, maxY - minY) : 1;
        double scale = hasScreens
                ? Math.max(0.02, Math.min(3.0, Math.min(planMaxW / boundW, planMaxH / boundH))) : 1;
        int planPxW = hasScreens ? Math.max(40, (int) Math.round(boundW * scale)) : 0;
        int planPxH = hasScreens ? Math.max(40, (int) Math.round(boundH * scale)) : 0;

        int w = Math.max(titleW, Math.max(tableW, planPxW)) + pad * 2;
        int titleY = pad + titleH - 6;
        int screensY = titleY + 24;
        int tableTop = rows.isEmpty() ? screensY : screensY + planPxH + 40;
        int headerY = tableTop + headerH;
        int firstRowY = headerY + 16;
        int h = rows.isEmpty() ? headerY + pad
                : firstRowY + (rows.size() - 1) * rowH + rowFm.getDescent() + pad;

        BufferedImage img = new BufferedImage(Math.max(1, (int) Math.round(w * dpiScale)),
                Math.max(1, (int) Math.round(h * dpiScale)), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.scale(dpiScale, dpiScale);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Palette.BG);
        g2.fillRect(0, 0, w, h);

        g2.setColor(Palette.TEXT);
        g2.setFont(titleFont);
        g2.drawString(title, pad, titleY);

        for (int i = 0; i < screens.size(); i++) {
            Screen scr = screens.get(i);
            CabinetType type = model.typeOf(scr);
            double[] e = ext[i];
            int sx = pad + (int) Math.round((scr.getPosXMm() + e[0] - minX) * scale);
            int sy = screensY + (int) Math.round((scr.getPosYMm() + e[1] - minY) * scale);
            int sw = Math.max(2, (int) Math.round((e[2] - e[0]) * scale));
            int sh = Math.max(2, (int) Math.round((e[3] - e[1]) * scale));
            g2.setColor(Palette.BG);
            g2.fillRect(sx, sy, sw, sh);
            if (type != null) {
                int cellW = Math.max(1, (int) Math.round(type.getWidthMm() * scale));
                int cellH = Math.max(1, (int) Math.round(type.getHeightMm() * scale));
                int gx = pad + (int) Math.round((scr.getPosXMm() - minX) * scale);
                int gy = screensY + (int) Math.round((scr.getPosYMm() - minY) * scale);
                Graphics2D clipped = (Graphics2D) g2.create();
                clipped.clipRect(sx, sy, sw, sh);
                paintScheme(clipped, scr, type, false, cellW, cellH, gx, gy, model.getWorkspace(),
                        List.of(), List.of(), List.of(), false, false);
                clipped.dispose();
            }
            g2.setColor(Palette.BORDER);
            g2.drawRect(sx, sy, sw, sh);
            g2.setColor(Palette.TEXT);
            g2.setFont(screenNumberFont);
            String number = rows.get(i).number();
            java.awt.FontMetrics numFm = g2.getFontMetrics();
            int chipW = numFm.stringWidth(number) + 10, chipH = numFm.getHeight() + 4;
            g2.setColor(new Color(0, 0, 0, 190));
            g2.fillRoundRect(sx + 3, sy + 3, chipW, chipH, 6, 6);
            g2.setColor(Color.WHITE);
            g2.drawString(number, sx + 8, sy + 3 + numFm.getAscent() + 1);
        }

        g2.setFont(headerFont);
        g2.setColor(Palette.TEXT);
        for (int i = 0; i < headers.length; i++) {
            g2.drawString(headers[i], colX[i], headerY);
        }
        g2.setColor(new Color(0, 0, 0, 120));
        g2.drawLine(pad, headerY + 6, w - pad, headerY + 6);

        g2.setFont(rowFont);
        g2.setColor(Palette.TEXT);
        int y = firstRowY;
        for (Row r : rows) {
            g2.drawString(r.number(), colX[0], y);
            g2.drawString(r.name(), colX[1], y);
            g2.drawString(r.size(), colX[2], y);
            g2.drawString(r.resolution(), colX[3], y);
            g2.drawString(r.weight(), colX[4], y);
            g2.drawString(r.load(), colX[5], y);
            g2.drawString(r.mount(), colX[6], y);
            g2.drawString(clipToWidth(g2, r.notes(), notesMaxW), colX[7], y);
            y += rowH;
        }

        g2.dispose();
        return img;
    }

    /** Электрическая нагрузка экрана для таблицы (см. {@link #renderScreensOverviewImage}) —
     *  в кВт с одним знаком после запятой при 1 кВт и больше (обычный диапазон для
     *  экрана из нескольких кабинетов), иначе в Вт целыми — единица подбирается по
     *  значению, а не по глобальной настройке юнита (у {@link SchemeRenderer} нет
     *  доступа к {@code SettingsManager}, как и у остального этого класса). */
    private static String formatLoad(double watts) {
        return watts >= 1000 ? String.format("%.1f кВт", watts / 1000.0) : Math.round(watts) + " Вт";
    }

    /** Известный рецепт для javax.imageio (нет прямого API "setDpi") — JFIF-узел
     *  {@code app0JFIF} с {@code resUnits=1} (dots per inch) и Xdensity/Ydensity =
     *  DPI; {@code getDefaultImageMetadata} для JPEG отдаёт ПУСТОЕ дерево (без уже
     *  существующего app0JFIF), поэтому просто добавляем узел, не заменяем. */
    private static void setJfifDpi(IIOMetadata metadata, int dpi) throws IOException {
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
        // getDefaultImageMetadata для JPEG уже отдаёт ПОЛНОЕ дерево с
        // JPEGvariety/app0JFIF (resUnits=0 — "без единиц, только соотношение
        // сторон", Xdensity=Ydensity=1) — если добавить ВТОРОЙ app0JFIF рядом
        // вместо правки существующего, mergeTree молча съедает оба узла без
        // ошибки, но сам писатель JPEG использует ПЕРВЫЙ (дефолтный, resUnits=0)
        // при кодировании — записанный DPI тогда тихо игнорируется (проверено
        // побайтовым чтением метаданных обратно). Поэтому правим узел IN PLACE,
        // создаём новый только если его в дефолтном дереве вдруг не оказалось.
        org.w3c.dom.NodeList jfifNodes = root.getElementsByTagName("app0JFIF");
        IIOMetadataNode jfif;
        if (jfifNodes.getLength() > 0) {
            jfif = (IIOMetadataNode) jfifNodes.item(0);
        } else {
            jfif = new IIOMetadataNode("app0JFIF");
            jfif.setAttribute("majorVersion", "1");
            jfif.setAttribute("minorVersion", "2");
            jfif.setAttribute("thumbWidth", "0");
            jfif.setAttribute("thumbHeight", "0");
            org.w3c.dom.NodeList varietyNodes = root.getElementsByTagName("JPEGvariety");
            IIOMetadataNode variety;
            if (varietyNodes.getLength() > 0) {
                variety = (IIOMetadataNode) varietyNodes.item(0);
            } else {
                // Корень нативного формата JPEG ОБЯЗАН иметь ровно двух детей —
                // JPEGvariety и markerSequence (иначе mergeTree бросает
                // IIOInvalidTreeException "must be present").
                variety = new IIOMetadataNode("JPEGvariety");
                root.insertBefore(variety, root.getFirstChild());
            }
            variety.appendChild(jfif);
        }
        jfif.setAttribute("resUnits", "1");
        jfif.setAttribute("Xdensity", Integer.toString(dpi));
        jfif.setAttribute("Ydensity", Integer.toString(dpi));
        try {
            metadata.mergeTree(format, root);
        } catch (javax.imageio.metadata.IIOInvalidTreeException ex) {
            throw new IOException("Не удалось записать DPI в метаданные JPEG", ex);
        }
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }
}
