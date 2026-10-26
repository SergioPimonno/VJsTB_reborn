package com.vjstb.ledscheme.ui.receiver;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.ModuleNumbering;
import com.vjstb.ledscheme.service.ReceiverCardChecks;
import com.vjstb.ledscheme.service.ScreenModules;
import com.vjstb.ledscheme.service.UnitNames;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Холст вида «Сигнал» → «Приёмные карты» (docs/receiver-cards/PLAN.md, 6.1). Рисует модули серым, обычные кабинеты
 * смешанного экрана тёмным, поверх — карты полупрозрачной заливкой цвета «Приёмные карты», на модулях — их номера
 * выходов (D14), на месте карты — плашку с именем (D11); оранжевым — модули без карты, красным — номера сверх
 * выходов карты и шлейфы длиннее допустимого.
 *
 * <p>Рисование — статический {@link #render}: на входе только данные экрана ({@link Data}), параметры показа
 * ({@link Options}), геометрия и стиль, никакого состояния Swing. Поэтому тот же метод рисует «Карту подключения»
 * в экспорте (трек E: {@link ModuleViewStyle#print()} и свой размер клетки). Экземпляр холста лишь хранит текущие
 * {@link Data} и параметры и отдаёт мышь виду-владельцу.
 */
public final class ReceiverCardsCanvas extends ModuleGridCanvas {

    /** Всё, что нужно рендеру от экрана; строится один раз на перерисовку/серию вопросов. */
    public record Data(Screen screen, ScreenModules modules, ReceiverCardChecks.Result checks,
                       List<ReceiverCardInstance> cards, Map<String, String> names, String numberFormat) {
        /**
         * @param numbering    порядок имён К1…КN (Предпочтения → «Нумерация приёмных карт»)
         * @param numberFormat шаблон обозначения номера модуля (Предпочтения → «Обозначение номера модуля»)
         */
        public static Data of(Screen screen, Workspace workspace, ReceiverCardNumbering numbering, String numberFormat) {
            return new Data(screen, ScreenModules.of(screen, workspace), ReceiverCardChecks.check(screen, workspace),
                    UnitNames.orderedCards(screen, numbering), UnitNames.cardNames(screen, numbering), numberFormat);
        }
    }

    /**
     * Параметры показа.
     *
     * @param showRibbons  рисовать все шлейфы от места карты к модулям (флажок «шлейфы»); длинные видны всегда
     * @param activeCardId выбранная карта — толще контур
     * @param selection    протягиваемая область (подсветка)
     * @param autoPreview  пунктирный предпросмотр авторазбиения: прямоугольники будущих карт
     */
    public record Options(boolean showRibbons, String activeCardId, GridRect selection, List<GridRect> autoPreview) {
        public static final Options NONE = new Options(false, null, null, List.of());
    }

    private Data data;
    private boolean showRibbons;
    private String activeCardId;
    private Function<GridRect, List<GridRect>> previewProvider;

    ReceiverCardsCanvas() {
    }

    void setData(Data data, double aspect) {
        this.data = data;
        if (data != null) {
            setGrid(data.screen().getRows(), data.screen().getCols(), aspect);
        }
        repaint();
    }

    Data data() {
        return data;
    }

    void setShowRibbons(boolean show) {
        this.showRibbons = show;
        repaint();
    }

    void setActiveCardId(String id) {
        this.activeCardId = id;
        repaint();
    }

    /** Предпросмотр авторазбиения на время протяжки; {@code null} — нет (режим «Вручную»). */
    void setPreviewProvider(Function<GridRect, List<GridRect>> provider) {
        this.previewProvider = provider;
        repaint();
    }

    @Override
    boolean hasContent() {
        return data != null && data.modules().isModular();
    }

    @Override
    String placeholder() {
        return data == null
                ? "Нет выбранного экрана"
                : "Экран не модульный.\nОтметьте у типа кабинета флаг «Приёмная карта отдельно»\n"
                + "(Библиотеки → Кабинеты), чтобы собирать экран из модулей.";
    }

    @Override
    void paintGrid(Graphics2D g, ModuleGridGeometry geo, ModuleViewStyle style) {
        GridRect drag = dragRect();
        List<GridRect> preview = drag != null && previewProvider != null ? previewProvider.apply(drag) : List.of();
        render(g, data, new Options(showRibbons, activeCardId, drag, preview), geo, style);
    }

    /** Рисует экран в заданной геометрии. Не меняет состояние {@code g0} (работает с копией). */
    public static void render(Graphics2D g0, Data d, Options o, ModuleGridGeometry geo, ModuleViewStyle st) {
        Graphics2D g = ModuleDraw.prepare(g0);
        try {
            Screen screen = d.screen();
            ScreenModules sm = d.modules();
            double s = geo.cellMin();
            Set<String> orphan = new HashSet<>(d.checks().modulesWithoutCard());

            // 1. клетки: модули серые, обычные кабинеты тёмные, без карты — оранжевые, протяжка — голубая
            for (CabinetInstance c : screen.getCabinets()) {
                if (c.isHidden() || c.getRowIndex() >= geo.rows() || c.getColIndex() >= geo.cols()) {
                    continue;
                }
                Rectangle2D r = ModuleDraw.inset(geo.cell(c.getRowIndex(), c.getColIndex()), 1);
                RoundRectangle2D shape = new RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(), 4, 4);
                if (sm.module(c.getId()) == null) {
                    g.setColor(st.ordinaryFill);
                    g.fill(shape);
                    g.setColor(st.ordinaryStroke);
                    g.setStroke(new BasicStroke(1f));
                    g.draw(shape);
                    continue;
                }
                boolean sel = o.selection() != null && o.selection().contains(c.getRowIndex(), c.getColIndex());
                boolean lost = orphan.contains(c.getId());
                g.setColor(sel ? st.selectionFill : lost ? st.orphanFill : st.moduleFill);
                g.fill(shape);
                g.setColor(sel ? st.selectionStroke : lost ? st.orphanStroke : st.moduleStroke);
                g.setStroke(new BasicStroke(sel || lost ? 1.4f : 0.8f));
                g.draw(shape);
            }

            // 2. карты: заливка и контур по клеткам (форма может быть не прямоугольной после частичной замены)
            Map<String, Set<Long>> cellsOfCard = new HashMap<>();
            for (ReceiverCardInstance card : d.cards()) {
                Set<Long> cells = new HashSet<>();
                for (String id : card.getModuleIds()) {
                    ScreenModules.Module m = sm.module(id);
                    if (m != null && m.row() < geo.rows() && m.col() < geo.cols()) {
                        cells.add(ModuleDraw.key(m.row(), m.col()));
                    }
                }
                if (cells.isEmpty()) {
                    continue;
                }
                cellsOfCard.put(card.getId(), cells);
                boolean active = card.getId().equals(o.activeCardId());
                ModuleDraw.fillRegion(g, geo, cells, ModuleViewStyle.alpha(st.card, active ? 0.40 : 0.25), 2);
                ModuleDraw.outlineRegion(g, geo, cells, active ? st.cardActiveStroke : st.cardStroke,
                        active ? 2.8f : 1.5f, null, 2);
            }

            // 3. шлейфы, затем номера и плашки (чтобы линии не перекрывали подписи)
            for (ReceiverCardInstance card : d.cards()) {
                if (!cellsOfCard.containsKey(card.getId())) {
                    continue;
                }
                ReceiverCardChecks.CardCheck cc = d.checks().card(card.getId());
                String name = d.names().get(card.getId());
                String placeId = UnitNames.placeOf(screen, card);
                ScreenModules.Module host = sm.module(placeId);
                Set<String> longRibbon = cc != null ? new HashSet<>(cc.longRibbonModuleIds()) : Set.of();
                Set<String> overLimit = cc != null ? new HashSet<>(cc.overLimitModuleIds()) : Set.of();

                if (host != null) {
                    double hx = geo.centerX(host.col());
                    double hy = geo.centerY(host.row());
                    for (String id : card.getModuleIds()) {
                        ScreenModules.Module m = sm.module(id);
                        if (m == null || id.equals(placeId)) {
                            continue;
                        }
                        boolean bad = longRibbon.contains(id);
                        if (!bad && !o.showRibbons()) {
                            continue;
                        }
                        double px = geo.centerX(m.col());
                        double py = geo.centerY(m.row());
                        if (bad) {
                            g.setColor(st.problem);
                            g.setStroke(new BasicStroke(1.8f));
                            g.draw(new Line2D.Double(hx, hy, px, py));
                            g.draw(ModuleDraw.inset(geo.cell(m.row(), m.col()), 3));
                        } else {
                            g.setColor(st.mutedText);
                            g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                                    new float[]{3f, 2f}, 0f));
                            g.draw(new Line2D.Double(hx, hy, px, py));
                        }
                    }
                }

                List<String> order = ModuleNumbering.order(card, screen);
                for (String id : card.getModuleIds()) {
                    ScreenModules.Module m = sm.module(id);
                    if (m == null) {
                        continue;
                    }
                    int n = order.indexOf(id) + 1;
                    boolean isHost = id.equals(placeId);
                    String label = ModuleNumbering.label(d.numberFormat(), name, n);
                    boolean bad = n == 0 || overLimit.contains(id);
                    ModuleDraw.centeredText(g, label, geo.centerX(m.col()), geo.centerY(m.row()) - (isHost ? s * 0.12 : 0),
                            Math.max(8, s * 0.38), geo.cellWidth() - 6, bad ? st.problemText : st.text);
                }
                if (host != null && name != null) {
                    ModuleDraw.plate(g, name, geo.centerX(host.col()), geo.centerY(host.row()) + s * 0.27,
                            Math.max(7, Math.min(12, s * 0.22)), st.cardActiveStroke, st.plateText);
                }
            }

            // 4. предпросмотр авторазбиения — пунктиром поверх всего
            if (!o.autoPreview().isEmpty()) {
                g.setColor(st.selectionStroke);
                g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                        new float[]{5f, 3f}, 0f));
                for (GridRect t : o.autoPreview()) {
                    Rectangle2D a = geo.cell(t.getRow0(), t.getCol0());
                    Rectangle2D b = geo.cell(t.getRow1(), t.getCol1());
                    g.draw(new RoundRectangle2D.Double(a.getX() + 2, a.getY() + 2, b.getMaxX() - a.getX() - 4,
                            b.getMaxY() - a.getY() - 4, 6, 6));
                }
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Прямоугольники, на которые «Автоматически» разобьёт область: как в {@code ReceiverCardEditor.assignAuto} —
     * шаг {@code w × h} от верхнего левого угла, остаток у правого и нижнего края меньшими картами; прямоугольники без
     * единого модуля не возвращаются. Здесь — только предпросмотр: сама правка идёт через {@code AppModel}, и тест
     * сверяет, что карты встают ровно там, где был пунктир.
     */
    public static List<GridRect> autoTiles(ScreenModules sm, GridRect area, int w, int h) {
        List<GridRect> out = new java.util.ArrayList<>();
        if (w < 1 || h < 1) {
            return out;
        }
        for (int r = area.getRow0(); r <= area.getRow1(); r += h) {
            for (int c = area.getCol0(); c <= area.getCol1(); c += w) {
                GridRect tile = new GridRect(r, c, Math.min(r + h - 1, area.getRow1()), Math.min(c + w - 1, area.getCol1()));
                if (!sm.modulesIn(tile).isEmpty()) {
                    out.add(tile);
                }
            }
        }
        return out;
    }
}
