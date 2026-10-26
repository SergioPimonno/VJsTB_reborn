package com.vjstb.ledscheme.ui.receiver;

import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.ReceiverCardChecks;
import com.vjstb.ledscheme.ui.Palette;
import com.vjstb.ledscheme.ui.UiKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
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
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Правая панель вида «Приёмные карты» (D9, D16): «Тип карты» → «Как назначать» → «Карты экрана».
 *
 * <p>«Как назначать» без перетаскивания: «Вручную» (протяжка по модулям — одна карта на выделение) и
 * «Автоматически» (поля «Г × В мод.» прямо под переключателем; фокус в поле сам включает режим — пользователь,
 * кликнувший в поле, уже решил, как назначать). Неверное число не затирает прежнее: разбиение продолжает идти по
 * последним верным Г × В, а под полями стоит подсказка.
 *
 * <p>Панель хранит только выбор пользователя (тип, режим, Г × В); сами карты и их список она получает от вида.
 */
final class CardAssignPanel extends JPanel {

    /** Что вид должен узнавать от панели. */
    interface Host {
        /** Сменились тип, режим или Г × В (подсказка и пунктир-предпросмотр зависят от них). */
        void assignSettingsChanged();

        /** В списке «Карты экрана» выбрана карта. */
        void cardSelected(String cardId);
    }

    private final Host host;
    private final DefaultListModel<ReceiverCardType> typeModel = new DefaultListModel<>();
    private final JList<ReceiverCardType> typeList = new JList<>(typeModel);
    private final JScrollPane typeScroll = new JScrollPane(typeList);
    private final JRadioButton manual = new JRadioButton();
    private final JRadioButton auto = new JRadioButton();
    private final JTextField wField = new JTextField("4", 3);
    private final JTextField hField = new JTextField("3", 3);
    private final JLabel error = new JLabel(" ");
    private final CardsTableModel tableModel = new CardsTableModel();
    private final JTable table = new JTable(tableModel);
    private final JLabel stats = new JLabel(" ");
    private final JLabel emptyTypes = new JLabel();
    private int autoW = 4;
    private int autoH = 3;
    private boolean updating;

    CardAssignPanel(Host host) {
        super(new BorderLayout());
        this.host = host;

        typeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        typeList.setCellRenderer(AssignParts.typeRenderer(ReceiverCardType::getName, CardAssignPanel::typeParams));
        typeList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updating) {
                host.assignSettingsChanged();
            }
        });
        typeScroll.setPreferredSize(new Dimension(200, 120));
        emptyTypes.setForeground(Palette.MUTED);
        emptyTypes.setText("<html><div style='width:210px'>В библиотеке нет приёмных карт. Добавьте их в «Библиотеки» → "
                + "«Приёмные карты».</div></html>");
        JPanel typeBody = new JPanel(new BorderLayout(0, 4));
        typeBody.add(typeScroll, BorderLayout.CENTER);
        typeBody.add(emptyTypes, BorderLayout.SOUTH);

        ButtonGroup group = new ButtonGroup();
        group.add(manual);
        group.add(auto);
        manual.setSelected(true);
        manual.addActionListener(e -> host.assignSettingsChanged());
        auto.addActionListener(e -> host.assignSettingsChanged());
        wField.setToolTipText("Модулей в карте по горизонтали");
        hField.setToolTipText("Модулей в карте по вертикали");
        JPanel size = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        size.setOpaque(false);
        size.setBorder(BorderFactory.createEmptyBorder(2, 20, 0, 0));
        size.add(new JLabel("Г"));
        size.add(wField);
        size.add(new JLabel("× В"));
        size.add(hField);
        size.add(new JLabel("мод."));
        error.setForeground(new Color(0xe24b4a));
        error.setBorder(BorderFactory.createEmptyBorder(2, 24, 0, 0));
        for (JTextField f : new JTextField[]{wField, hField}) {
            f.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    if (!auto.isSelected()) {
                        auto.setSelected(true);
                        host.assignSettingsChanged();
                    }
                }
            });
            f.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    sizeEdited();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    sizeEdited();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    sizeEdited();
                }
            });
        }
        JPanel how = UiKit.vbox();
        how.add(AssignParts.optionRow(manual, "Вручную", "одна карта на выделенную область"));
        how.add(new JSeparator());
        how.add(AssignParts.optionRow(auto, "Автоматически", "область разбивается на карты по числу модулей"));
        size.setAlignmentX(Component.LEFT_ALIGNMENT);
        how.add(size);
        error.setAlignmentX(Component.LEFT_ALIGNMENT);
        how.add(error);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(false);
        table.setRowHeight(22);
        table.setShowGrid(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new CellRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(58);
        table.getColumnModel().getColumn(2).setPreferredWidth(78);
        table.getColumnModel().getColumn(3).setPreferredWidth(62);
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (!e.getValueIsAdjusting() && !updating && row >= 0) {
                host.cardSelected(tableModel.rows.get(row).id());
            }
        });
        stats.setForeground(Palette.MUTED);
        stats.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
        JPanel tableBody = new JPanel(new BorderLayout());
        tableBody.add(table.getTableHeader(), BorderLayout.NORTH);
        tableBody.add(table, BorderLayout.CENTER);
        tableBody.add(stats, BorderLayout.SOUTH);

        JPanel column = UiKit.vbox();
        column.add(UiKit.dynamicSection("Тип карты", typeBody));
        column.add(UiKit.vgap(8));
        column.add(UiKit.dynamicSection("Как назначать", how));
        column.add(UiKit.vgap(8));
        column.add(UiKit.dynamicSection("Карты экрана", tableBody));
        add(column, BorderLayout.NORTH);
    }

    private static String typeParams(ReceiverCardType t) {
        List<String> parts = new ArrayList<>();
        if (t.getModuleOutputs() != null) {
            parts.add(t.getModuleOutputs() + " вых.");
        }
        if (t.getRibbonLengthMm() != null) {
            parts.add(Math.round(t.getRibbonLengthMm()) + " мм");
        }
        return parts.isEmpty() ? "не указано" : String.join(" · ", parts);
    }

    private void sizeEdited() {
        if (updating) {
            return;
        }
        Integer w = parsePositive(wField.getText());
        Integer h = parsePositive(hField.getText());
        if (w == null || h == null) {
            error.setText("Укажите целое число от 1");
            return;
        }
        error.setText(" ");
        autoW = w;
        autoH = h;
        host.assignSettingsChanged();
    }

    private static Integer parsePositive(String s) {
        try {
            int v = Integer.parseInt(s.trim());
            return v >= 1 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ состояние для вида

    /** Типы карт из библиотеки; выбор сохраняется, если тип ещё есть, иначе выбирается первый. */
    void setTypes(List<ReceiverCardType> types) {
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
        ReceiverCardType t = typeList.getSelectedValue();
        return t != null ? t.getId() : null;
    }

    boolean autoMode() {
        return auto.isSelected();
    }

    int autoW() {
        return autoW;
    }

    int autoH() {
        return autoH;
    }

    /** Заполняет «Карты экрана» и подсвечивает выбранную. */
    void setCards(ReceiverCardsCanvas.Data d, Workspace ws, String activeId) {
        updating = true;
        List<Row> rows = new ArrayList<>();
        long modulesInCards = 0;
        for (ReceiverCardInstance card : d != null ? d.cards() : List.<ReceiverCardInstance>of()) {
            ReceiverCardChecks.CardCheck cc = d.checks().card(card.getId());
            if (cc == null) {
                continue;
            }
            ReceiverCardType type = card.getReceiverCardTypeId() != null
                    ? ws.receiverCardTypeById(card.getReceiverCardTypeId()) : null;
            String name = d.names().get(card.getId()) + " · " + (type != null ? type.getName() : "тип не найден")
                    + (card.getManualPortOrder() != null ? " · вручную" : "");
            int longN = cc.longRibbonCount();
            String modules = cc.moduleCount() + (cc.outputs() != null ? "/" + cc.outputs() : "")
                    + (longN > 0 ? " ⚠" + longN : "");
            rows.add(new Row(card.getId(), name, modules, cc.overOutputs() || longN > 0,
                    cc.resolutionWidthPx() + "×" + cc.resolutionHeightPx(), cc.widthExceeded() || cc.heightExceeded(),
                    grouped(cc.pixels()), cc.pixelsExceeded()));
            modulesInCards += cc.moduleCount();
        }
        tableModel.rows = rows;
        tableModel.fireTableDataChanged();
        int sel = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(activeId)) {
                sel = i;
            }
        }
        if (sel >= 0) {
            table.setRowSelectionInterval(sel, sel);
        } else {
            table.clearSelection();
        }
        table.getTableHeader().setVisible(!rows.isEmpty());
        stats.setText(d == null ? "Нет выбранного экрана" : rows.isEmpty()
                ? "Пока нет карт"
                : "Карт: " + rows.size() + " · модулей на картах: " + modulesInCards
                + " · без карты: " + d.checks().modulesWithoutCard().size());
        table.setPreferredSize(new Dimension(table.getPreferredSize().width, rows.size() * table.getRowHeight()));
        updating = false;
        revalidate();
        repaint();
    }

    private static String grouped(long n) {
        return String.format(java.util.Locale.ROOT, "%,d", n).replace(',', ' ');
    }

    // ------------------------------------------------------------------ для тестов

    JList<ReceiverCardType> typeList() {
        return typeList;
    }

    JRadioButton manualRadio() {
        return manual;
    }

    JRadioButton autoRadio() {
        return auto;
    }

    JTextField widthField() {
        return wField;
    }

    JTextField heightField() {
        return hField;
    }

    String errorText() {
        return error.getText().trim();
    }

    JTable cardsTable() {
        return table;
    }

    String statsText() {
        return stats.getText();
    }

    // ------------------------------------------------------------------ таблица

    private record Row(String id, String name, String modules, boolean modulesWarn, String resolution,
                       boolean resolutionWarn, String pixels, boolean pixelsWarn) {
    }

    private static final class CardsTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Карта", "Модули", "Разрешение", "Пиксели"};
        List<Row> rows = new ArrayList<>();

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLUMNS[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            Row row = rows.get(r);
            return switch (c) {
                case 0 -> row.name();
                case 1 -> row.modules();
                case 2 -> row.resolution();
                default -> row.pixels();
            };
        }
    }

    private final class CellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int r,
                                                       int c) {
            super.getTableCellRendererComponent(t, value, sel, false, r, c);
            Row row = tableModel.rows.get(r);
            setHorizontalAlignment(c == 0 ? LEFT : RIGHT);
            boolean warn = switch (c) {
                case 1 -> row.modulesWarn();
                case 2 -> row.resolutionWarn();
                case 3 -> row.pixelsWarn();
                default -> false;
            };
            if (!sel) {
                setForeground(warn ? Palette.WARN : t.getForeground());
            }
            setToolTipText(c == 0 ? row.name() : null);
            return this;
        }
    }
}
