package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomEquipmentType;
import com.vjstb.ledscheme.model.RoomFurnitureType;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.room.RoomItemSpec;
import com.vjstb.ledscheme.service.room.RoomLibraryEdit;
import com.vjstb.ledscheme.service.room.RoomLibraryAdapter;
import com.vjstb.ledscheme.service.room.RoomUnits;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.ui.room.EdtStallWatchdog;
import com.vjstb.ledscheme.ui.room.PlannerEditorPanel;
import com.vjstb.ledscheme.ui.room.RoomTheme;
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
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Окно «Планер пультовой…» (меню «Инструменты», рядом с калькулятором транспорта) — немодальное
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


    private final AppModel model;
    private final SettingsManager settings;
    private final AppModel.Listener modelListener;
    private final JLabel sceneLabel = new JLabel(" ");
    private final JComboBox<ControlRoomLayout> layoutBox = new JComboBox<>();
    private final JButton addBtn = new JButton("Новая…");
    private final JButton renameBtn = new JButton("Переименовать…");
    private final JButton dupBtn = new JButton("Дублировать");
    private final JButton delBtn = new JButton("Удалить");
    private final PlannerEditorPanel editor;
    private final RoomLibraryUi libraryUi;
    /** Журнал зависаний интерфейса, пока окно открыто из меню (в тестах не запускается). */
    private EdtStallWatchdog stallWatchdog;
    private final LibraryTableModel libModel = new LibraryTableModel();
    private final JCheckBox onlyMissing = new JCheckBox("Только без габаритов");
    private final JLabel libCount = new JLabel(" ");
    private final JTable libTable = new JTable(libModel);
    private final JButton dimsBtn = new JButton("Указать габариты…");
    private final JButton newItemBtn = new JButton("Новая позиция ▾");
    private final JButton editItemBtn = new JButton("Изменить…");
    private final JButton deleteItemBtn = new JButton("Удалить…");
    private final JButton proposeBtn = new JButton("Предложить на сервер…");
    private RoomLibraryAdapter libAdapter;
    private boolean updating;

    /** Открыть окно (или вынести уже открытое на передний план). */
    public static void showWindow(Window owner, AppModel model, SettingsManager settings) {
        if (instance != null && instance.isDisplayable()) {
            instance.toFront();
            instance.refresh();
            return;
        }
        instance = new ControlRoomPlannerDialog(owner, model, settings);
        // ~/.led-scheme/logs/planner-stalls.log — длительность и стек каждого зависания интерфейса (см. EdtStallWatchdog)
        instance.stallWatchdog = EdtStallWatchdog.start(java.nio.file.Path.of(System.getProperty("user.home"), ".led-scheme",
                "logs", "planner-stalls.log"));
        instance.setVisible(true);
    }

    public ControlRoomPlannerDialog(Window owner, AppModel model) {
        this(owner, model, null);
    }

    /** {@code settings == null} (тесты) отключает только «Предложить на сервер…». */
    public ControlRoomPlannerDialog(Window owner, AppModel model, SettingsManager settings) {
        super(owner, "Планер пультовой", ModalityType.MODELESS);
        this.model = model;
        this.settings = settings;
        libraryUi = new RoomLibraryUi(this, model, settings);
        editor = new PlannerEditorPanel(model, libraryUi);

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
                editor.showLayout(selected());
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

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Редактор", editor);
        tabs.addTab("Библиотека (габариты)", buildLibraryTab());

        JPanel content = new JPanel(new BorderLayout(0, 4));
        content.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        content.add(bar, BorderLayout.NORTH);
        content.add(tabs, BorderLayout.CENTER);
        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1280, 800);
        setLocationRelativeTo(owner);

        modelListener = this::refresh;
        model.addListener(modelListener);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                model.removeListener(modelListener);
                model.flushPendingSave();
                if (stallWatchdog != null) {
                    stallWatchdog.stop();
                }
                if (instance == ControlRoomPlannerDialog.this) {
                    instance = null;
                }
            }
        });
        refresh();
    }

    private JPanel buildLibraryTab() {
        JTable table = libTable;
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
                    c.setBackground(spec.complete() ? t.getBackground() : RoomTheme.current().warnBg);
                    c.setForeground(spec.complete() ? t.getForeground() : RoomTheme.current().ink);
                }
                return c;
            }
        };
        table.setDefaultRenderer(Object.class, paint);
        onlyMissing.addActionListener(e -> renderLibrary());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(onlyMissing);
        top.add(libCount);
        top.add(UiKit.muted("Янтарным выделены позиции без габаритов — укажите их сами или предложите на сервер."));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        actions.add(dimsBtn);
        actions.add(newItemBtn);
        actions.add(editItemBtn);
        actions.add(deleteItemBtn);
        actions.add(proposeBtn);
        dimsBtn.addActionListener(e -> specifyDimensions());
        editItemBtn.addActionListener(e -> editPersonalItem());
        deleteItemBtn.addActionListener(e -> deletePersonalItem());
        proposeBtn.addActionListener(e -> proposeSelected());
        dimsBtn.setToolTipText("Указать (или поправить) габариты выбранной позиции. Они сохранятся у вас и будут действовать, "
                + "пока в общей библиотеке нет данных.");
        proposeBtn.setToolTipText("Отправить габариты администратору: они появятся в общей библиотеке у всех.");
        JPopupMenu newMenu = new JPopupMenu();
        JMenuItem newDeck = new JMenuItem("Станок…");
        newDeck.addActionListener(e -> libraryUi.createFurniture(RoomFurnitureType.Kind.DECK));
        JMenuItem newMon = new JMenuItem("Монитор…");
        newMon.addActionListener(e -> libraryUi.createFurniture(RoomFurnitureType.Kind.MONITOR));
        JMenuItem newEquip = new JMenuItem("Блок в стойку или мелочь…");
        JMenuItem newFurn = new JMenuItem("Стол, стул, мебель…");
        newEquip.addActionListener(e -> libraryUi.createEquipment());
        newFurn.addActionListener(e -> libraryUi.createFurniture(RoomFurnitureType.Kind.FURNITURE));
        JMenuItem newCase = new JMenuItem("Рэк-кофр…");
        newCase.addActionListener(e -> libraryUi.createCase());
        newMenu.add(newDeck);
        newMenu.add(newFurn);
        newMenu.add(newMon);
        newMenu.addSeparator();
        newMenu.add(newEquip);
        newMenu.add(newCase);
        newItemBtn.addActionListener(e -> newMenu.show(newItemBtn, 0, newItemBtn.getHeight()));
        table.getSelectionModel().addListSelectionListener(e -> updateLibraryButtons());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && selectedLibraryRef() != null) {
                    specifyDimensions();
                }
            }
        });
        JPanel north = new JPanel(new BorderLayout());
        north.add(top, BorderLayout.NORTH);
        north.add(actions, BorderLayout.SOUTH);
        JPanel p = new JPanel(new BorderLayout());
        p.add(north, BorderLayout.NORTH);
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
        setTitle("Планер пультовой" + (hasScene ? " — " + model.getCurrentScene().getName() : ""));
        addBtn.setEnabled(hasScene);
        boolean any = selected() != null;
        renameBtn.setEnabled(any);
        dupBtn.setEnabled(any);
        delBtn.setEnabled(any);
        editor.showLayout(selected());
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

    // ---- вкладка «Библиотека»: габариты и свои позиции ----

    private RoomLibRef selectedLibraryRef() {
        int row = libTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return libModel.at(libTable.convertRowIndexToModel(row)).ref();
    }

    private void updateLibraryButtons() {
        RoomLibRef ref = selectedLibraryRef();
        dimsBtn.setEnabled(ref != null);
        editItemBtn.setEnabled(ref != null && libraryUi.canEdit(ref));
        deleteItemBtn.setEnabled(ref != null && libraryUi.canEdit(ref));
        proposeBtn.setEnabled(ref != null && libraryUi.canPropose(ref));
    }

    private void specifyDimensions() {
        libraryUi.specifyDimensions(selectedLibraryRef());
    }

    private void editPersonalItem() {
        libraryUi.edit(selectedLibraryRef());
    }

    private void deletePersonalItem() {
        libraryUi.delete(selectedLibraryRef());
    }

    private void proposeSelected() {
        libraryUi.propose(selectedLibraryRef());
    }

    private void renderLibrary() {
        libAdapter = RoomLibraryAdapter.of(model);
        RoomLibRef keep = selectedLibraryRef();
        List<RoomItemSpec> all = libAdapter.all();
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
        java.util.Set<RoomLibRef> personal = new java.util.HashSet<>();
        for (RoomItemSpec s : shown) {
            if (libAdapter.usesPersonalPhysical(s.ref())) {
                personal.add(s.ref());
            }
        }
        libModel.set(shown, personal);
        libCount.setText("Позиций: " + all.size() + ", без габаритов: " + missing);
        if (keep != null) {
            for (int i = 0; i < shown.size(); i++) {
                if (keep.equals(shown.get(i).ref())) {
                    int view = libTable.convertRowIndexToView(i);
                    libTable.getSelectionModel().setSelectionInterval(view, view);
                    break;
                }
            }
        }
        updateLibraryButtons();
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
            case DEVICE -> "Отдельно";
        };
    }

    private static final class LibraryTableModel extends AbstractTableModel {
        private final String[] cols = {"Позиция", "Категория", "Тип", "Размеры", "Вес, кг", "Мощность, Вт", "Габариты"};
        private List<RoomItemSpec> rows = List.of();
        private java.util.Set<RoomLibRef> personal = java.util.Set.of();

        void set(List<RoomItemSpec> rows, java.util.Set<RoomLibRef> personal) {
            this.rows = rows;
            this.personal = personal;
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
                default -> s.complete() ? (personal.contains(s.ref()) ? "указаны (ваши)" : "указаны")
                        : "нет: " + String.join(", ", s.missing());
            };
        }
    }
}
