package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.VehicleType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.SceneStats;
import com.vjstb.ledscheme.service.VehicleCalc;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Подбор минимально достаточной машины под кофры проекта (запрос менеджеров:
 * "какую машину минимально рекомендуется заказать") — расчёт делегирован
 * {@link VehicleCalc}, этот диалог только собирает строки "тип кофра +
 * количество" и отображает результат. См. VEHICLE_CALC_NOTES.md за мотивацию
 * и допущения (площадь — простое сравнение, без 2D-раскладки/запаса %).
 *
 * <p>Для строки с кофром "несёт кабинеты" (см. {@link CaseType#isCarriesCabinets()})
 * количество авто-подставляется при выборе типа из числа кабинетов в ОБЛАСТИ
 * РАСЧЁТА ({@link #scopeCombo} — текущая сцена / весь проект / вручную
 * выбранный набор сцен, см. {@link #cabinetTotalForScope()}) ÷ вместимость.
 * По умолчанию — текущая сцена (по прямому указанию пользователя: машина
 * обычно возит содержимое одной сцены), но одну сцену иногда нужно грузить в
 * НЕСКОЛЬКО машин, а иногда, наоборот, считать сразу весь проект или
 * произвольный набор сцен — отсюда три режима, не один. Без какого-либо
 * запаса — это ГОЛОЕ число кабинетов, запас на неполные/повреждённые/
 * запасные кофры менеджер добавляет вручную поверх авто-подставленного
 * значения, оно остаётся редактируемым — переопределение действует, пока
 * пользователь не выберет тип заново в этой же строке. Для всех остальных
 * кофров — только ручной ввод (осознанное решение, см. заметки — без
 * привязки конкретных EQUIPMENT/CABLE записей к типам кофров).
 */
public class VehicleCalculatorDialog extends JDialog {

    /** Область расчёта числа кабинетов для авто-подстановки — см. class-javadoc. */
    private enum CalcScope { SCENE, PROJECT, CUSTOM }

    private static final String[] SCOPE_LABELS = {"Текущая сцена", "Весь проект", "Несколько сцен…"};

    private final AppModel model;
    private final SettingsManager settings;

    private final JComboBox<String> scopeCombo = new JComboBox<>(SCOPE_LABELS);
    private final JLabel sceneInfoLabel = UiKit.muted(" ");
    private final RowsTableModel rowsTableModel = new RowsTableModel();
    private final JTable rowsTable = new JTable(rowsTableModel);
    private final JCheckBox weightCheck = new JCheckBox("Учитывать вес", true);
    private final JCheckBox snapCheck = new JCheckBox("Прилипание кофров в визуализаторе", true);
    private final JLabel resultLabel = new JLabel(" ");
    private final JButton visualizeBtn = new JButton("Визуализация загрузки…");
    private final JComboBox<CaseType> typeCombo = new JComboBox<>();

    private CalcScope scope = CalcScope.SCENE;
    private List<Scene> customScenes = List.of();
    private int lastScopeIndex = 0;

    private List<VehicleCalc.CaseRow> lastCaseRows = List.of();
    private VehicleType lastRecommended;

    public VehicleCalculatorDialog(Window owner, AppModel model, SettingsManager settings) {
        super(owner, "Калькулятор транспорта", ModalityType.MODELESS);
        this.model = model;
        this.settings = settings;

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        content.add(buildScopePanel(), BorderLayout.NORTH);
        content.add(buildRowsPanel(), BorderLayout.CENTER);
        content.add(buildBottomPanel(), BorderLayout.SOUTH);

        setContentPane(content);
        setPreferredSize(new Dimension(640, 500));
        pack();
        setLocationRelativeTo(owner);

        refreshScopeInfo();
        recalculate();
    }

    private JPanel buildScopePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel scopeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        scopeRow.add(new JLabel("Область расчёта:"));
        scopeCombo.addActionListener(e -> applyScopeSelection());
        scopeRow.add(scopeCombo);
        panel.add(scopeRow);
        panel.add(sceneInfoLabel);
        return panel;
    }

    /** Обрабатывает выбор в {@link #scopeCombo}. «Несколько сцен…» сразу открывает
     *  {@link #pickScenes} — если пользователь отменил выбор или не выбрал ни одной
     *  сцены, комбобокс откатывается на предыдущий пункт (нельзя молча остаться в
     *  «несколько сцен» без реального набора сцен). */
    private void applyScopeSelection() {
        int idx = scopeCombo.getSelectedIndex();
        if (idx == 2) {
            Project project = model.getCurrentProject();
            if (project == null || project.getScenes().isEmpty()) {
                sceneInfoLabel.setText(" В проекте нет сцен для выбора.");
                scopeCombo.setSelectedIndex(lastScopeIndex);
                return;
            }
            List<Scene> picked = pickScenes(project.getScenes(), customScenes);
            if (picked == null || picked.isEmpty()) {
                scopeCombo.setSelectedIndex(lastScopeIndex);
                return;
            }
            customScenes = picked;
            scope = CalcScope.CUSTOM;
        } else {
            scope = idx == 0 ? CalcScope.SCENE : CalcScope.PROJECT;
        }
        lastScopeIndex = scopeCombo.getSelectedIndex();
        recalculate();
    }

    /** Чекбокс-список сцен проекта (JList с множественным выделением в
     *  JOptionPane) — предвыделяет {@code preselected}. null — пользователь
     *  нажал «Отмена». */
    private List<Scene> pickScenes(List<Scene> allScenes, List<Scene> preselected) {
        JList<Scene> list = new JList<>(allScenes.toArray(new Scene[0]));
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer((l, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value instanceof Scene s ? s.getName() : String.valueOf(value));
            label.setOpaque(true);
            label.setBackground(isSelected ? l.getSelectionBackground() : l.getBackground());
            label.setForeground(isSelected ? l.getSelectionForeground() : l.getForeground());
            return label;
        });
        int[] indices = allScenes.stream().filter(preselected::contains).mapToInt(allScenes::indexOf).toArray();
        list.setSelectedIndices(indices);
        int result = JOptionPane.showConfirmDialog(this, new JScrollPane(list), "Выберите сцены",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return result == JOptionPane.OK_OPTION ? list.getSelectedValuesList() : null;
    }

    // ---- строки "тип кофра + количество" ----

    private JPanel buildRowsPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Кофры"));

        refreshTypeCombo();
        typeCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value instanceof CaseType t ? t.getName() : String.valueOf(value));
            label.setOpaque(true);
            label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        rowsTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(typeCombo));
        rowsTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setText(value instanceof CaseType ct ? ct.getName() : "—");
                return c;
            }
        });
        rowsTable.setRowHeight(24);
        panel.add(new JScrollPane(rowsTable), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton add = new JButton("Добавить строку");
        add.addActionListener(e -> {
            if (model.getCaseTypes().isEmpty()) {
                resultLabel.setText("Библиотека кофров пуста — сначала добавьте типы кофров.");
                return;
            }
            rowsTableModel.addRow(model.getCaseTypes().get(0));
            recalculate();
        });
        JButton remove = new JButton("Удалить строку");
        remove.addActionListener(e -> {
            int idx = rowsTable.getSelectedRow();
            if (idx >= 0) {
                rowsTableModel.removeRow(idx);
                recalculate();
            }
        });
        JButton newType = new JButton("+ Новый тип кофра…");
        newType.setToolTipText("Библиотека не предусмотрела нужный кофр? Задайте свой прямо здесь — сохранится"
                + " в личную библиотеку и сразу станет доступен ниже, можно предложить в общую на модерацию.");
        newType.addActionListener(e -> createCustomCaseType());
        buttons.add(add);
        buttons.add(remove);
        buttons.add(newType);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    /** Пересобирает содержимое {@link #typeCombo} из {@code model.getCaseTypes()} —
     *  как и остальные комбо в этом кодовой базе (см. SetupStagePanel), список НЕ
     *  живёт синхронно с библиотекой автоматически, вызывается явно после
     *  {@link #createCustomCaseType()}, чтобы только что созданный тип сразу стал
     *  выбираем, не только после переоткрытия диалога. */
    private void refreshTypeCombo() {
        Object selected = typeCombo.getSelectedItem();
        typeCombo.removeAllItems();
        for (CaseType t : model.getCaseTypes()) {
            typeCombo.addItem(t);
        }
        if (selected != null) {
            typeCombo.setSelectedItem(selected);
        }
    }

    /** Своя запись кофра, которую библиотека не предусмотрела (запрос пользователя) —
     *  сохраняется в ЛИЧНУЮ библиотеку ({@code model.addCaseType}), сразу
     *  предлагается на модерацию в общую (см. {@link ProposeDialog}, тот же приём,
     *  что и у WireLabelDialog.registerAsAdapter/registerAsLengthProfile), и сразу
     *  добавляется новой строкой в таблицу, чтобы не нужно было ещё раз искать её
     *  в комбобоксе. */
    private void createCustomCaseType() {
        CaseType created = new CaseTypeDialog(this, null).showDialog();
        if (created == null) {
            return;
        }
        CaseType saved;
        try {
            saved = model.addCaseType(created);
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Проверка данных", JOptionPane.WARNING_MESSAGE);
            return;
        }
        refreshTypeCombo();
        rowsTableModel.addRow(saved);
        recalculate();
        if (settings != null) {
            ProposeDialog.show(this, settings, "CASE", saved.getName(), saved);
        }
    }

    private JPanel buildBottomPanel() {
        JPanel bottom = new JPanel(new BorderLayout(4, 8));
        JPanel topRow = new JPanel();
        topRow.setLayout(new BoxLayout(topRow, BoxLayout.Y_AXIS));
        JPanel checksRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        weightCheck.addActionListener(e -> recalculate());
        checksRow.add(weightCheck);
        checksRow.add(snapCheck);
        topRow.add(checksRow);
        JPanel visRow = new JPanel(new BorderLayout());
        visualizeBtn.setToolTipText("Открыть визуализатор загрузки — вид сверху на кузов, кофры"
                + " перетаскиваются мышью (первая версия, цвета по типу кофра пока условные)");
        visualizeBtn.addActionListener(e -> {
            if (model.getVehicleTypes().isEmpty()) {
                resultLabel.setText("Библиотека машин пуста — сначала добавьте типы машин.");
                return;
            }
            if (lastCaseRows.isEmpty()) {
                resultLabel.setText("Сначала укажите кофры с количеством больше нуля — визуализатор"
                        + " использует именно эти строки.");
                return;
            }
            VehicleLoadVisualizerDialog dlg = new VehicleLoadVisualizerDialog(getOwner(), model, model.getCurrentScene(),
                    lastRecommended, lastCaseRows);
            dlg.setSnapEnabled(snapCheck.isSelected());
            dlg.setVisible(true);
        });
        visRow.add(visualizeBtn, BorderLayout.EAST);
        topRow.add(visRow);
        bottom.add(topRow, BorderLayout.NORTH);

        resultLabel.setVerticalAlignment(JLabel.TOP);
        bottom.add(resultLabel, BorderLayout.CENTER);

        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        closeRow.add(close);
        bottom.add(closeRow, BorderLayout.SOUTH);
        return bottom;
    }

    // ---- пересчёт ----

    private void refreshScopeInfo() {
        switch (scope) {
            case SCENE -> {
                Scene scene = model.getCurrentScene();
                if (scene == null) {
                    sceneInfoLabel.setText(" Сцена не выбрана — авто-подстановка количества для кофров"
                            + " с кабинетами недоступна, вводите количество вручную.");
                    return;
                }
                SceneStats stats = ScreenLogic.sceneStats(scene, model.getWorkspace());
                sceneInfoLabel.setText(" Кабинетов в сцене: " + stats.totalCabinetCount()
                        + " (экранов: " + stats.screenCount() + ")");
            }
            case PROJECT -> {
                Project project = model.getCurrentProject();
                if (project == null) {
                    sceneInfoLabel.setText(" Проект не выбран.");
                    return;
                }
                int total = ScreenLogic.cabinetCountAcross(project.getScenes(), model.getWorkspace());
                sceneInfoLabel.setText(" Кабинетов в проекте: " + total
                        + " (сцен: " + project.getScenes().size() + ")");
            }
            case CUSTOM -> {
                int total = ScreenLogic.cabinetCountAcross(customScenes, model.getWorkspace());
                sceneInfoLabel.setText(" Кабинетов в выбранных сценах (" + customScenes.size() + "): " + total);
            }
        }
    }

    private int cabinetTotalForScope() {
        return switch (scope) {
            case SCENE -> {
                Scene scene = model.getCurrentScene();
                yield scene == null ? 0 : ScreenLogic.sceneStats(scene, model.getWorkspace()).totalCabinetCount();
            }
            case PROJECT -> {
                Project project = model.getCurrentProject();
                yield project == null ? 0
                        : ScreenLogic.cabinetCountAcross(project.getScenes(), model.getWorkspace());
            }
            case CUSTOM -> ScreenLogic.cabinetCountAcross(customScenes, model.getWorkspace());
        };
    }

    private void recalculate() {
        refreshScopeInfo();

        List<VehicleType> candidates = model.getVehicleTypes();
        if (candidates.isEmpty()) {
            resultLabel.setText("<html>Библиотека машин пуста — сначала добавьте типы машин.</html>");
            lastCaseRows = List.of();
            lastRecommended = null;
            return;
        }
        List<VehicleCalc.CaseRow> caseRows = new ArrayList<>();
        for (RowsTableModel.Row row : rowsTableModel.rows) {
            if (row.type != null && row.count > 0) {
                caseRows.add(new VehicleCalc.CaseRow(row.type, row.count));
            }
        }
        lastCaseRows = caseRows;
        if (caseRows.isEmpty()) {
            resultLabel.setText("<html>Добавьте хотя бы один кофр с количеством больше нуля.</html>");
            lastRecommended = null;
            return;
        }

        boolean checkWeight = weightCheck.isSelected();
        Optional<VehicleType> best = VehicleCalc.recommend(candidates, caseRows, checkWeight);
        lastRecommended = best.orElse(null);
        if (best.isPresent()) {
            VehicleCalc.VehicleFitResult fit = VehicleCalc.checkFit(best.get(), caseRows, checkWeight);
            StringBuilder sb = new StringBuilder("<html>Минимально достаточная машина: <b>")
                    .append(best.get().getName()).append("</b><br>")
                    .append("Требуемая площадь пола: ").append(round1(fit.requiredFloorAreaM2())).append(" м²")
                    .append(" из ").append(round1(fit.cargoFloorAreaM2())).append(" м² доступных");
            if (checkWeight && fit.totalWeightKg() != null) {
                sb.append("<br>Вес: ").append(round1(fit.totalWeightKg())).append(" кг из ")
                        .append(round1(best.get().getPayloadKg())).append(" кг грузоподъёмности");
            }
            sb.append("<br><i>Площадь — упрощённая оценка (сумма площадей кофров с учётом штабелирования,"
                    + " без 2D-раскладки и запаса на неидеальную укладку) — см. VEHICLE_CALC_NOTES.md.</i>");
            sb.append("</html>");
            resultLabel.setText(sb.toString());
        } else {
            VehicleType closest = candidates.stream()
                    .sorted(Comparator
                            .comparingDouble((VehicleType v) -> v.getCargoLengthMm() * v.getCargoWidthMm())
                            .thenComparingDouble(VehicleType::getCargoHeightMm)
                            .thenComparingDouble(VehicleType::getPayloadKg))
                    .findFirst().orElseThrow();
            lastRecommended = closest;
            VehicleCalc.VehicleFitResult fit = VehicleCalc.checkFit(closest, caseRows, checkWeight);
            resultLabel.setText("<html>Ни одна машина из библиотеки не подходит.<br>"
                    + "Ближайшая по размеру — <b>" + closest.getName() + "</b>: " + fit.failureReason() + ".</html>");
        }
    }

    private static String round1(double v) {
        return String.valueOf(Math.round(v * 10) / 10.0);
    }

    // ---- модель таблицы строк ----

    private final class RowsTableModel extends AbstractTableModel {
        private static final class Row {
            CaseType type;
            int count;
        }

        private final List<Row> rows = new ArrayList<>();

        void addRow(CaseType initialType) {
            Row row = new Row();
            row.type = initialType;
            applyAutoCount(row);
            rows.add(row);
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void removeRow(int index) {
            rows.remove(index);
            fireTableRowsDeleted(index, index);
        }

        private void applyAutoCount(Row row) {
            if (row.type != null && row.type.isCarriesCabinets() && row.type.getCabinetsPerCase() != null
                    && row.type.getCabinetsPerCase() > 0) {
                row.count = VehicleCalc.suggestCaseCount(cabinetTotalForScope(), row.type.getCabinetsPerCase());
            }
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Тип кофра" : "Количество";
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? CaseType.class : Integer.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Row row = rows.get(rowIndex);
            return columnIndex == 0 ? row.type : row.count;
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            Row row = rows.get(rowIndex);
            if (columnIndex == 0) {
                row.type = (CaseType) value;
                applyAutoCount(row);
                fireTableRowsUpdated(rowIndex, rowIndex);
            } else {
                row.count = Math.max(0, (Integer) value);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
            recalculate();
        }
    }
}
