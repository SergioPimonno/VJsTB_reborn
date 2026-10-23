package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.room.RoomItemSpec;
import com.vjstb.ledscheme.service.room.RoomLibraryAdapter;
import com.vjstb.ledscheme.service.room.RoomPlanCalc;
import com.vjstb.ledscheme.service.room.RoomUnits;
import com.vjstb.ledscheme.service.room.RoomWarning;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Окно «Планнер пультовой…» (меню «Инструменты», рядом с калькулятором транспорта) — немодальное
 * окно, как {@link VehicleCalculatorDialog}; решение пользователя 2026-10-23, план —
 * {@code docs/control-room-planner/PLAN.md}. Прототип поведения — {@code planner.html} рядом с планом.
 *
 * <p><b>Состояние реализации:</b> это КАРКАС первого этапа. Готово: у каждой сцены свой список
 * раскладок (создать, переименовать, дублировать, удалить; их может быть несколько), расчёт по
 * данным раскладки (итоги и замечания) и вкладка «Библиотека» с цветовой пометкой позиций без
 * габаритов (запрос пользователя 2026-10-23). Редактор раскладки (башни, план сверху и виды,
 * трассы, станки, мониторы) — следующие треки плана; до них раскладка создаётся пустой.
 *
 * <p>Окно подписано на модель ({@link AppModel#addListener}) и отписывается при закрытии: при
 * смене сцены список раскладок перечитывается, при синхронизации библиотеки пересчитывается
 * вкладка «Библиотека».
 */
public class ControlRoomPlannerDialog extends JDialog {

    private static ControlRoomPlannerDialog instance;

    private static final Color MISSING_BG = new Color(0xFF, 0xE8, 0xB0);

    private final AppModel model;
    private final AppModel.Listener modelListener;
    private final JLabel sceneLabel = new JLabel(" ");
    private final JComboBox<ControlRoomLayout> layoutBox = new JComboBox<>();
    private final JButton addBtn = new JButton("Новая…");
    private final JButton renameBtn = new JButton("Переименовать…");
    private final JButton dupBtn = new JButton("Дублировать");
    private final JButton delBtn = new JButton("Удалить");
    private final JTextArea summary = new JTextArea();
    private final LibraryTableModel libModel = new LibraryTableModel();
    private final JCheckBox onlyMissing = new JCheckBox("Только без габаритов");
    private final JLabel libCount = new JLabel(" ");
    private boolean updating;

    /** Открыть окно (или вынести уже открытое на передний план). */
    public static void showWindow(Window owner, AppModel model, SettingsManager settings) {
        if (instance != null && instance.isDisplayable()) {
            instance.toFront();
            instance.refresh();
            return;
        }
        instance = new ControlRoomPlannerDialog(owner, model);
        instance.setVisible(true);
    }

    public ControlRoomPlannerDialog(Window owner, AppModel model) {
        super(owner, "Планнер пультовой", ModalityType.MODELESS);
        this.model = model;

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        bar.add(new JLabel("Раскладка:"));
        layoutBox.setPrototypeDisplayValue(new ControlRoomLayout("Раскладка пультовой для длинного названия"));
        layoutBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> l, Object v, int i, boolean sel,
                                                          boolean focus) {
                return super.getListCellRendererComponent(l, v instanceof ControlRoomLayout c ? c.getName() : "", i,
                        sel, focus);
            }
        });
        layoutBox.addActionListener(e -> {
            if (!updating) {
                renderSummary();
            }
        });
        bar.add(layoutBox);
        bar.add(addBtn);
        bar.add(renameBtn);
        bar.add(dupBtn);
        bar.add(delBtn);
        bar.add(sceneLabel);
        addBtn.addActionListener(e -> addLayout());
        renameBtn.addActionListener(e -> renameLayout());
        dupBtn.addActionListener(e -> duplicateLayout());
        delBtn.addActionListener(e -> deleteLayout());
        addBtn.setToolTipText("Новая пустая раскладка у текущей сцены. У каждой сцены свой список раскладок.");

        summary.setEditable(false);
        summary.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        summary.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Раскладка", new JScrollPane(summary));
        tabs.addTab("Библиотека", buildLibraryTab());

        JPanel content = new JPanel(new BorderLayout(0, 4));
        content.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        content.add(bar, BorderLayout.NORTH);
        content.add(tabs, BorderLayout.CENTER);
        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(980, 640);
        setLocationRelativeTo(owner);

        modelListener = this::refresh;
        model.addListener(modelListener);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                model.removeListener(modelListener);
                if (instance == ControlRoomPlannerDialog.this) {
                    instance = null;
                }
            }
        });
        refresh();
    }

    private JPanel buildLibraryTab() {
        JTable table = new JTable(libModel);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(24);
        int[] widths = {260, 120, 90, 230, 70, 90, 280};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        DefaultTableCellRenderer paint = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row,
                                                           int col) {
                Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                RoomItemSpec spec = libModel.at(t.convertRowIndexToModel(row));
                if (!sel) {
                    c.setBackground(spec.complete() ? t.getBackground() : MISSING_BG);
                    c.setForeground(spec.complete() ? t.getForeground() : Color.BLACK);
                }
                return c;
            }
        };
        table.setDefaultRenderer(Object.class, paint);
        onlyMissing.addActionListener(e -> renderLibrary());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(onlyMissing);
        top.add(libCount);
        top.add(UiKit.muted("Янтарным выделены позиции без габаритов. Заполнить их можно в админке (импорт из CSV)."));
        JPanel p = new JPanel(new BorderLayout());
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    // ---- список раскладок ----

    private ControlRoomLayout selected() {
        return (ControlRoomLayout) layoutBox.getSelectedItem();
    }

    private void refresh() {
        updating = true;
        try {
            String keepId = selected() != null ? selected().getId() : null;
            List<ControlRoomLayout> layouts = new ArrayList<>(model.controlRoomLayouts());
            layoutBox.setModel(new DefaultComboBoxModel<>(layouts.toArray(new ControlRoomLayout[0])));
            ControlRoomLayout pick = layouts.isEmpty() ? null : layouts.get(0);
            for (ControlRoomLayout l : layouts) {
                if (l.getId().equals(keepId)) {
                    pick = l;
                }
            }
            layoutBox.setSelectedItem(pick);
        } finally {
            updating = false;
        }
        boolean hasScene = model.getCurrentScene() != null;
        sceneLabel.setText(hasScene ? "   Сцена: " + model.getCurrentScene().getName() : "   Сцена не выбрана");
        setTitle("Планнер пультовой" + (hasScene ? " — " + model.getCurrentScene().getName() : ""));
        addBtn.setEnabled(hasScene);
        boolean any = selected() != null;
        renameBtn.setEnabled(any);
        dupBtn.setEnabled(any);
        delBtn.setEnabled(any);
        renderSummary();
        renderLibrary();
    }

    private void addLayout() {
        String name = JOptionPane.showInputDialog(this, "Название раскладки:", "Новая раскладка",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;
        }
        ControlRoomLayout l = model.addControlRoomLayout(name);
        refresh();
        layoutBox.setSelectedItem(l);
    }

    private void renameLayout() {
        ControlRoomLayout l = selected();
        if (l == null) {
            return;
        }
        String name = (String) JOptionPane.showInputDialog(this, "Новое название:", "Переименовать раскладку",
                JOptionPane.PLAIN_MESSAGE, null, null, l.getName());
        if (name != null) {
            model.renameControlRoomLayout(l, name);
            refresh();
        }
    }

    private void duplicateLayout() {
        ControlRoomLayout l = selected();
        if (l != null) {
            ControlRoomLayout copy = model.duplicateControlRoomLayout(l);
            refresh();
            layoutBox.setSelectedItem(copy);
        }
    }

    private void deleteLayout() {
        ControlRoomLayout l = selected();
        if (l == null) {
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this, "Удалить раскладку «" + l.getName() + "» из этой сцены?",
                "Удаление раскладки", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok == JOptionPane.OK_OPTION) {
            model.deleteControlRoomLayout(l);
            refresh();
        }
    }

    // ---- содержимое ----

    private void renderSummary() {
        ControlRoomLayout l = selected();
        if (l == null) {
            summary.setText("У этой сцены пока нет раскладок.\n\nНажмите «Новая…», чтобы создать первую. "
                    + "Раскладок может быть несколько (варианты, разные площадки), у каждой сцены свой список.");
            summary.setCaretPosition(0);
            return;
        }
        RoomPlanCalc.Result r = RoomPlanCalc.calc(l, RoomLibraryAdapter.of(model));
        RoomPlanCalc.Totals t = r.totals();
        StringBuilder sb = new StringBuilder();
        sb.append("Раскладка «").append(l.getName()).append("»\n");
        sb.append("Площадка: ").append(RoomUnits.num(l.getRoomWidthCm() / 100, 2)).append(" × ")
                .append(RoomUnits.num(l.getRoomDepthCm() / 100, 2)).append(" м\n\n");
        sb.append("Башен: ").append(t.towers()).append(" · юниты занято ").append(t.unitsUsed()).append(" из ")
                .append(t.unitsTotal()).append("\n");
        sb.append("Станков: ").append(t.deck().count());
        if (t.deck().count() > 0) {
            sb.append(" · пол ").append(RoomUnits.num(t.deck().widthCm() / 100, 1)).append(" × ")
                    .append(RoomUnits.num(t.deck().depthCm() / 100, 1)).append(" м, ")
                    .append(RoomUnits.num(t.deck().areaM2(), 1)).append(" м²");
        }
        sb.append("\nВес: ").append(RoomUnits.num(t.kg(), 0)).append(" кг · нагрузка ")
                .append(RoomUnits.num(t.powerW() / 1000, 2)).append(" кВт (")
                .append(RoomUnits.num(t.powerW() / 230, 1)).append(" А) · кофров к перевозке: ").append(t.cases())
                .append("\n\nЗамечания\n");
        if (r.warnings().isEmpty()) {
            sb.append("  всё в порядке\n");
        }
        for (RoomWarning w : r.warnings()) {
            String tag = switch (w.level()) {
                case ERROR -> "ОШИБКА";
                case WARNING -> "предупр.";
                case INFO -> "к сведению";
            };
            sb.append("  [").append(tag).append("] ").append(w.text()).append("\n");
        }
        sb.append("\n— Каркас первого этапа: редактор раскладки (башни, план и виды, трассы, станки, мониторы)\n")
                .append("  появится в следующих этапах, см. docs/control-room-planner/PLAN.md.\n");
        summary.setText(sb.toString());
        summary.setCaretPosition(0);
    }

    private void renderLibrary() {
        List<RoomItemSpec> all = RoomLibraryAdapter.of(model).all();
        int missing = 0;
        List<RoomItemSpec> shown = new ArrayList<>();
        for (RoomItemSpec s : all) {
            if (!s.complete()) {
                missing++;
            }
            if (!onlyMissing.isSelected() || !s.complete()) {
                shown.add(s);
            }
        }
        libModel.set(shown);
        libCount.setText("Позиций: " + all.size() + ", без габаритов: " + missing);
    }

    private static String dims(RoomItemSpec s) {
        String cm = RoomUnits.num(s.widthCm(), 1) + "×" + RoomUnits.num(s.heightCm(), 1) + "×"
                + RoomUnits.num(s.depthCm(), 1) + " см";
        return switch (s.type()) {
            case RACK -> s.rackUnits() + "U" + (s.widthTwelfths() < 12 ? " · " + s.widthTwelfths() + "/12" : "") + " · " + cm;
            case CASE -> s.rackUnits() + "U · снаружи " + cm;
            default -> cm;
        };
    }

    private static String typeLabel(RoomItemSpec s) {
        return switch (s.type()) {
            case CASE -> "Кофр";
            case RACK -> "В стойку";
            case LOOSE -> "Мелочь";
            case FURNITURE -> "Мебель";
            case DECK -> "Станок";
            case MONITOR -> "Монитор";
        };
    }

    private static final class LibraryTableModel extends AbstractTableModel {
        private final String[] cols = {"Позиция", "Категория", "Тип", "Размеры", "Вес, кг", "Мощность, Вт", "Габариты"};
        private List<RoomItemSpec> rows = List.of();

        void set(List<RoomItemSpec> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        RoomItemSpec at(int r) {
            return rows.get(r);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int c) {
            return cols[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            RoomItemSpec s = rows.get(r);
            return switch (c) {
                case 0 -> s.name();
                case 1 -> s.category();
                case 2 -> typeLabel(s);
                case 3 -> dims(s);
                case 4 -> RoomUnits.num(s.weightKg(), 2);
                case 5 -> RoomUnits.num(s.powerW(), 0);
                default -> s.complete() ? "указаны" : "нет: " + String.join(", ", s.missing());
            };
        }
    }
}
