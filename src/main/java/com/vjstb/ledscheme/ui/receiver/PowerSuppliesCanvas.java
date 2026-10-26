package com.vjstb.ledscheme.ui.receiver;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.PowerSupplyChecks;
import com.vjstb.ledscheme.service.PowerSupplyLayout;
import com.vjstb.ledscheme.service.ScreenModules;
import com.vjstb.ledscheme.service.UnitNames;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Холст вида «Питание» → «Блоки питания» (docs/receiver-cards/PLAN.md, 6.2, D21). Блок питает МОДУЛИ: область каждого
 * блока — цвет заливки и контур по границе его модулей (может быть непрямоугольной), на модуле — номер его шлейфа в
 * блоке, на месте блока — плашка «БПn» (+«+К», если блок питает карту). Карты показаны пунктирным контуром и плашкой
 * на своём месте, чтобы было видно, в области какого блока они стоят и потому от какого питаются (D19). Оранжевым
 * выделены модули без блока, красным — шлейфы сверх числа выходов и кабели длиннее допустимого.
 *
 * <p>Как и {@link ReceiverCardsCanvas}, рисует статический {@link #render} без состояния Swing — им же пользуется
 * экспорт «Карты подключения».
 */
public final class PowerSuppliesCanvas extends ModuleGridCanvas {

    /** Всё, что нужно рендеру от экрана. */
    public record Data(Screen screen, ScreenModules modules, PowerSupplyChecks.Result checks,
                       List<ReceiverCardInstance> cards, Map<String, String> cardNames) {
        public static Data of(Screen screen, Workspace workspace, ReceiverCardNumbering numbering) {
            return new Data(screen, ScreenModules.of(screen, workspace),
                    PowerSupplyChecks.check(screen, workspace, numbering),
                    UnitNames.orderedCards(screen, numbering), UnitNames.cardNames(screen, numbering));
        }
    }

    /**
     * @param showCables     рисовать все кабели от места блока к модулям и картам (флажок «кабели питания»); длинные
     *                       видны всегда
     * @param activeSupplyId выбранный блок — контур крупнее и в цвете текста
     * @param selection      протягиваемая область (подсветка)
     */
    public record Options(boolean showCables, String activeSupplyId, GridRect selection) {
        public static final Options NONE = new Options(false, null, null);
    }

    private Data data;
    private boolean showCables;
    private String activeSupplyId;

    PowerSuppliesCanvas() {
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

    void setShowCables(boolean show) {
        this.showCables = show;
        repaint();
    }

    void setActiveSupplyId(String id) {
        this.activeSupplyId = id;
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
        render(g, data, new Options(showCables, activeSupplyId, dragRect()), geo, style);
    }

    /** Рисует экран в заданной геометрии. Не меняет состояние {@code g0}. */
    public static void render(Graphics2D g0, Data d, Options o, ModuleGridGeometry geo, ModuleViewStyle st) {
        Graphics2D g = ModuleDraw.prepare(g0);
        try {
            Screen screen = d.screen();
            ScreenModules sm = d.modules();
            PowerSupplyLayout.Result layout = d.checks().layout();
            double s = geo.cellMin();
            Set<String> orphan = new HashSet<>(d.checks().modulesWithoutSupply());
            Map<String, Integer> indexOfSupply = new HashMap<>();
            int idx = 0;
            for (PowerSupplyLayout.Supply sup : layout.supplies()) {
                indexOfSupply.put(sup.instance().getId(), idx++);
            }

            // 1. клетки: цвет области блока, оранжевый — без питания, голубой — протяжка
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
                PowerSupplyLayout.Supply owner = layout.ofModule(c.getId());
                Color fill = st.moduleFill;
                Color stroke = st.moduleStroke;
                float w = 0.8f;
                if (owner != null) {
                    fill = st.areaFill(indexOfSupply.get(owner.instance().getId()));
                } else if (orphan.contains(c.getId())) {
                    fill = st.orphanFill;
                    stroke = st.orphanStroke;
                    w = 1.4f;
                }
                if (sel) {
                    fill = st.selectionFill;
                    stroke = st.selectionStroke;
                    w = 1.4f;
                }
                g.setColor(fill);
                g.fill(shape);
                g.setColor(stroke);
                g.setStroke(new BasicStroke(w));
                g.draw(shape);
            }

            // 2. контуры областей блоков
            for (PowerSupplyLayout.Supply sup : layout.supplies()) {
                Set<Long> cells = new HashSet<>();
                for (PowerSupplyLayout.ModuleFeed mf : sup.modules()) {
                    ScreenModules.Module m = sm.module(mf.moduleId());
                    if (m != null && m.row() < geo.rows() && m.col() < geo.cols()) {
                        cells.add(ModuleDraw.key(m.row(), m.col()));
                    }
                }
                if (cells.isEmpty()) {
                    continue;
                }
                boolean active = sup.instance().getId().equals(o.activeSupplyId());
                ModuleDraw.outlineRegion(g, geo, cells, active ? st.text : st.area(indexOfSupply.get(sup.instance().getId())),
                        active ? 3.2f : 2.4f, null, 1.5);
            }

            // 3. карты — пунктирным контуром (они здесь справочные: показывают, чья область питает карту)
            for (ReceiverCardInstance card : d.cards()) {
                Set<Long> cells = new HashSet<>();
                for (String id : card.getModuleIds()) {
                    ScreenModules.Module m = sm.module(id);
                    if (m != null && m.row() < geo.rows() && m.col() < geo.cols()) {
                        cells.add(ModuleDraw.key(m.row(), m.col()));
                    }
                }
                if (!cells.isEmpty()) {
                    ModuleDraw.outlineRegion(g, geo, cells, st.cardStroke, 1.2f, new float[]{5f, 3f}, 4);
                }
            }

            // 4. кабели, номера шлейфов, плашки. Флажок «кабели питания» рисует кабели выбранного блока, а если блок не
            // выбран — всех блоков, но не больше 400 линий: от одного блока на весь экран тысячи линий закрывали вид.
            int totalFed = 0;
            for (PowerSupplyLayout.Supply sup : layout.supplies()) {
                totalFed += sup.modules().size();
            }
            for (PowerSupplyLayout.Supply sup : layout.supplies()) {
                boolean cablesHere = o.showCables() && (o.activeSupplyId() != null
                        ? o.activeSupplyId().equals(sup.instance().getId()) : totalFed <= 400);
                int ci = indexOfSupply.get(sup.instance().getId());
                ScreenModules.Module host = sm.module(sup.placeModuleId());
                Set<String> longSet = new HashSet<>(sup.longModuleIds());
                Integer outputs = sup.outputs();
                if (host != null) {
                    double hx = geo.centerX(host.col());
                    double hy = geo.centerY(host.row());
                    for (PowerSupplyLayout.ModuleFeed mf : sup.modules()) {
                        ScreenModules.Module m = sm.module(mf.moduleId());
                        if (m == null || m == host) {
                            continue;
                        }
                        boolean bad = longSet.contains(mf.moduleId());
                        if (!bad && !cablesHere) {
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
                            g.setColor(st.area(ci));
                            g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                                    new float[]{3f, 2f}, 0f));
                            g.draw(new Line2D.Double(hx, hy, px, py));
                        }
                    }
                    if (cablesHere) {
                        g.setColor(st.cardStroke);
                        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                                new float[]{2f, 2f}, 0f));
                        for (PowerSupplyLayout.CardFeed cf : sup.cards()) {
                            ReceiverCardInstance card = screen.receiverCardById(cf.cardId());
                            ScreenModules.Module cm = card != null ? sm.module(UnitNames.placeOf(screen, card)) : null;
                            if (cm != null) {
                                g.draw(new Line2D.Double(hx, hy, geo.centerX(cm.col()), geo.centerY(cm.row()) + s * 0.27));
                            }
                        }
                    }
                }
                for (PowerSupplyLayout.ModuleFeed mf : sup.modules()) {
                    ScreenModules.Module m = sm.module(mf.moduleId());
                    if (m == null) {
                        continue;
                    }
                    boolean over = outputs != null && mf.output() > outputs;
                    ModuleDraw.centeredText(g, Integer.toString(mf.output()), geo.centerX(m.col()), geo.centerY(m.row()),
                            Math.max(8, s * 0.38), geo.cellWidth() - 6, over ? st.problemText : st.areaText(ci));
                }
            }
            for (ReceiverCardInstance card : d.cards()) {
                ScreenModules.Module cm = sm.module(UnitNames.placeOf(screen, card));
                String name = d.cardNames().get(card.getId());
                if (cm == null || name == null) {
                    continue;
                }
                boolean powered = layout.ofCard(card.getId()) != null;
                ModuleDraw.plate(g, name, geo.centerX(cm.col()), geo.centerY(cm.row()) + s * 0.27,
                        Math.max(7, Math.min(12, s * 0.22)), powered ? st.cardActiveStroke : st.orphanStroke,
                        powered ? st.plateText : Color.BLACK);
            }
            for (PowerSupplyLayout.Supply sup : layout.supplies()) {
                ScreenModules.Module host = sm.module(sup.placeModuleId());
                if (host == null) {
                    continue;
                }
                String label = sup.name() + (sup.feedsCards() ? " +К" : "");
                ModuleDraw.plate(g, label, geo.centerX(host.col()), geo.centerY(host.row()) - s * 0.3,
                        Math.max(7, Math.min(12, s * 0.22)),
                        sup.hasProblem() ? st.problem : ModuleViewStyle.darker(st.area(indexOfSupply.get(sup.instance().getId())), 0.8),
                        Color.WHITE);
            }
        } finally {
            g.dispose();
        }
    }
}
