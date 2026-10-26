package com.vjstb.ledscheme.ui.receiver;

import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.PowerSupplyLayout;
import com.vjstb.ledscheme.ui.Palette;
import com.vjstb.ledscheme.ui.UiKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

/**
 * Правая панель вида «Блоки питания» (D17, D18, D21): «Тип блока питания» → «Как назначать» → «Блоки питания экрана».
 *
 * <p>В отличие от карт, у блоков в «Автоматически» нет полей Г × В: размер прямоугольника блока подбирает сам
 * {@code PowerSupplyEditor} (не больше числа шлейфов, все модули в пределах кабеля, картам хватает выхода), а панель
 * только показывает итог строкой «Подобрано: 4×2 мод. на блок, блоков: 9» — пользователь сразу видит, что выбрано,
 * и если проверки не прошли даже для 1×1, об этом сказано в той же строке.
 */
final class PowerAssignPanel extends JPanel {

    /** Что вид должен узнавать от панели. */
    interface Host {
        void assignSettingsChanged();

        void supplySelected(String supplyId);
    }

    private final Host host;
    private final DefaultListModel<PowerSupplyType> typeModel = new DefaultListModel<>();
    private final JList<PowerSupplyType> typeList = new JList<>(typeModel);
    private final JScrollPane typeScroll = new JScrollPane(typeList);
    private final JRadioButton manual = new JRadioButton();
    private final JRadioButton auto = new JRadioButton();
    private final JLabel autoInfo = new JLabel(" ");
    private final JLabel emptyTypes = new JLabel();
    private final DefaultListModel<SupplyRow> supplyModel = new DefaultListModel<>();
    private final JList<SupplyRow> supplyList = new JList<>(supplyModel);
    private final JLabel emptySupplies = new JLabel("Пока нет блоков");
    private boolean updating;

    PowerAssignPanel(Host host) {
        super(new BorderLayout());
        this.host = host;

        typeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        typeList.setCellRenderer(AssignParts.typeRenderer(PowerSupplyType::getName, PowerAssignPanel::typeParams));
        typeList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updating) {
                host.assignSettingsChanged();
            }
        });
        typeScroll.setPreferredSize(new Dimension(200, 120));
        emptyTypes.setForeground(Palette.MUTED);
        emptyTypes.setText("<html><div style='width:210px'>В библиотеке нет блоков питания. Добавьте их в «Библиотеки» → "
                + "«Блоки питания».</div></html>");
        JPanel typeBody = new JPanel(new BorderLayout(0, 4));
        typeBody.add(typeScroll, BorderLayout.CENTER);
        typeBody.add(emptyTypes, BorderLayout.SOUTH);

        ButtonGroup group = new ButtonGroup();
        group.add(manual);
        group.add(auto);
        manual.setSelected(true);
        manual.addActionListener(e -> host.assignSettingsChanged());
        auto.addActionListener(e -> host.assignSettingsChanged());
        JLabel autoHelp = new JLabel("<html><div style='width:210px'>размер блока подбирается сам: прямоугольники не больше "
                + "числа шлейфов, все модули в пределах кабеля, картам хватает выходов; из подходящих — где блоков "
                + "меньше всего (если не проходит — блоки мельче, их больше)</div></html>");
        autoHelp.setForeground(Palette.MUTED);
        autoHelp.setFont(autoHelp.getFont().deriveFont(autoHelp.getFont().getSize2D() - 1f));
        autoHelp.setBorder(BorderFactory.createEmptyBorder(2, 24, 0, 0));
        autoInfo.setBorder(BorderFactory.createEmptyBorder(2, 24, 0, 0));
        JPanel how = UiKit.vbox();
        how.add(AssignParts.optionRow(manual, "Вручную", "один блок на выделенные модули"));
        how.add(new JSeparator());
        how.add(AssignParts.optionRow(auto, "Автоматически", "область разбивается на блоки по числу модулей"));
        autoHelp.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
        how.add(autoHelp);
        how.add(autoInfo);

        supplyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        supplyList.setCellRenderer(new SupplyRenderer());
        supplyList.addListSelectionListener(e -> {
            SupplyRow row = supplyList.getSelectedValue();
            if (!e.getValueIsAdjusting() && !updating && row != null) {
                host.supplySelected(row.id());
            }
        });
        emptySupplies.setForeground(Palette.MUTED);
        JPanel supplyBody = new JPanel(new BorderLayout(0, 4));
        supplyBody.add(supplyList, BorderLayout.CENTER);
        supplyBody.add(emptySupplies, BorderLayout.SOUTH);

        JPanel column = UiKit.vbox();
        column.add(UiKit.dynamicSection("Тип блока питания", typeBody));
        column.add(UiKit.vgap(8));
        column.add(UiKit.dynamicSection("Как назначать", how));
        column.add(UiKit.vgap(8));
        column.add(UiKit.dynamicSection("Блоки питания экрана", supplyBody));
        add(column, BorderLayout.NORTH);
    }

    /** «8+К шл. · 300 Вт · 700 мм» (D17): шлейфы (+К — есть отдельный выход на карту), мощность, длина кабеля. */
    static String typeParams(PowerSupplyType t) {
        List<String> parts = new ArrayList<>();
        // Число шлейфов — главное, по чему выбирают блок (баг-репорт: в списке были видны только ватты), поэтому
        // оно есть в строке всегда: не указано в библиотеке — «шлейфов: ?», чтобы было видно, что поле надо заполнить.
        if (t.getOutputs() != null) {
            parts.add(t.getOutputs() + (t.isCardOutput() ? "+К" : "") + " шл.");
        } else {
            parts.add(t.isCardOutput() ? "шлейфов: ? +К" : "шлейфов: ?");
        }
        if (t.getRatedPowerW() != null) {
            parts.add(Math.round(t.getRatedPowerW()) + " Вт");
        }
        if (t.getCableLengthMm() != null) {
            parts.add(Math.round(t.getCableLengthMm()) + " мм");
        }
        return parts.isEmpty() ? "не указано" : String.join(" · ", parts);
    }

    void setTypes(List<PowerSupplyType> types) {
        String keep = selectedTypeId();
        updating = true;
        typeModel.clear();
        types.forEach(typeModel::addElement);
        int idx = -1;
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).getId().equals(keep)) {
                idx = i;
            }
        }
        if (idx < 0 && !types.isEmpty()) {
            idx = 0;
        }
        if (idx >= 0) {
            typeList.setSelectedIndex(idx);
        }
        emptyTypes.setVisible(types.isEmpty());
        typeScroll.setPreferredSize(new Dimension(200, Math.max(40, Math.min(types.size(), 6) * 26 + 6)));
        updating = false;
        revalidate();
    }

    String selectedTypeId() {
        PowerSupplyType t = typeList.getSelectedValue();
        return t != null ? t.getId() : null;
    }

    boolean autoMode() {
        return auto.isSelected();
    }

    /** Строка «Подобрано: …» под переключателем; пустая — скрывается. */
    void setAutoInfo(String text) {
        autoInfo.setText(text == null || text.isBlank() ? " " : text);
    }

    String autoInfoText() {
        return autoInfo.getText().trim();
    }

    /** Заполняет «Блоки питания экрана» и подсвечивает выбранный. */
    void setSupplies(PowerSuppliesCanvas.Data d, Workspace ws, String activeId) {
        updating = true;
        supplyModel.clear();
        int sel = -1;
        int ci = 0;
        for (PowerSupplyLayout.Supply s : d != null ? d.checks().layout().supplies()
                : List.<PowerSupplyLayout.Supply>of()) {
            List<String> cards = new ArrayList<>();
            for (PowerSupplyLayout.CardFeed cf : s.cards()) {
                String n = d.cardNames().get(cf.cardId());
                if (n != null) {
                    cards.add(n);
                }
            }
            String left = s.name() + " · " + (s.type() != null ? s.type().getName() : "тип не найден")
                    + (cards.isEmpty() ? "" : " · " + String.join(", ", cards));
            int longN = s.longModuleIds().size();
            String right = s.used() + (s.outputs() != null ? "/" + s.outputs() : "")
                    + (s.hasDedicatedCard() ? " +К" : "") + (longN > 0 ? " ⚠" + longN : "");
            StringBuilder tip = new StringBuilder();
            if (s.over() > 0) {
                tip.append("Потребителей больше, чем выходов. ");
            }
            if (longN > 0 && s.type() != null && s.type().getCableLengthMm() != null) {
                tip.append(longN).append(" мод. дальше ").append(Math.round(s.type().getCableLengthMm())).append(" мм");
            }
            if (s.instance().getId().equals(activeId)) {
                sel = supplyModel.size();
            }
            supplyModel.addElement(new SupplyRow(s.instance().getId(), left, right, s.hasProblem(), ci++, tip.toString()));
        }
        if (sel >= 0) {
            supplyList.setSelectedIndex(sel);
        } else {
            supplyList.clearSelection();
        }
        emptySupplies.setVisible(supplyModel.isEmpty());
        updating = false;
        revalidate();
        repaint();
    }

    // ------------------------------------------------------------------ для тестов

    JList<PowerSupplyType> typeList() {
        return typeList;
    }

    JRadioButton manualRadio() {
        return manual;
    }

    JRadioButton autoRadio() {
        return auto;
    }

    JList<SupplyRow> supplyList() {
        return supplyList;
    }

    // ------------------------------------------------------------------ список блоков

    record SupplyRow(String id, String left, String right, boolean warn, int colorIndex, String tooltip) {
        @Override
        public String toString() {
            return left + "  " + right;
        }
    }

    private static final class SupplyRenderer implements ListCellRenderer<SupplyRow> {
        @Override
        public Component getListCellRendererComponent(JList<? extends SupplyRow> list, SupplyRow row, int index,
                                                      boolean sel, boolean focus) {
            JPanel p = new JPanel(new BorderLayout(6, 0));
            p.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
            p.setBackground(sel ? list.getSelectionBackground() : list.getBackground());
            ModuleViewStyle st = ModuleViewStyle.live();
            Color c = st.area(row.colorIndex());
            JLabel swatch = new JLabel() {
                @Override
                protected void paintComponent(java.awt.Graphics g) {
                    g.setColor(st.areaFill(row.colorIndex()));
                    g.fillRect(0, 3, 9, 9);
                    g.setColor(c);
                    g.drawRect(0, 3, 8, 8);
                }
            };
            swatch.setPreferredSize(new Dimension(10, 14));
            JLabel left = new JLabel(row.left());
            JLabel right = new JLabel(row.right());
            Color fg = sel ? list.getSelectionForeground() : list.getForeground();
            left.setForeground(fg);
            right.setForeground(row.warn() ? (sel ? fg : st.problemText) : (sel ? fg : Palette.MUTED));
            p.add(swatch, BorderLayout.WEST);
            p.add(left, BorderLayout.CENTER);
            p.add(right, BorderLayout.EAST);
            p.setToolTipText(row.tooltip().isEmpty() ? null : row.tooltip());
            return p;
        }
    }
}
