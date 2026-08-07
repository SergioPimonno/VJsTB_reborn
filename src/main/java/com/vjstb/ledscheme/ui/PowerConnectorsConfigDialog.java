package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;

/**
 * Разъёмы питания оборудования (щиты, дистрибьюторы и т.п. в схеме ПИТАНИЯ):
 * плоский список «тип разъёма (PowerCon/CEE/Schuko…) + направление + количество».
 * В отличие от {@link CardsConfigDialog} (видеокарты медиасерверов/контроллеров)
 * тут нет группировки по картам — силовое оборудование не имеет сменных карт,
 * достаточно просто перечислить разъёмы ввода/вывода. Работает и с узлом схемы,
 * и с пресетом библиотеки — через {@link PowerConnectorsHost}.
 */
public class PowerConnectorsConfigDialog extends JDialog {

    /** Абстракция над источником разъёмов — узел схемы или пресет библиотеки.
     *  getLoadDeratingPercent/setLoadDeratingPercent (Task #86/#87) значимы только
     *  для узла схемы (запас — свойство конкретного силового блока, не пресета
     *  библиотеки) — у пресета остаются заглушками по умолчанию. */
    public interface PowerConnectorsHost {
        List<CardPort> getConnectors();
        CardPort addConnector(String connectorType, PortDirection direction, int count, int phaseCount,
                               Double breakerAmps);
        /** Правит уже существующий разъём НА МЕСТЕ (сохраняя id) — иначе провод схемы,
         *  уже подключённый к нему, потерял бы соединение при remove+add. */
        void updateConnector(String portId, String connectorType, PortDirection direction, int count,
                              int phaseCount, Double breakerAmps);
        void removeConnector(String portId);
        default Double getLoadDeratingPercent() {
            return null;
        }
        default void setLoadDeratingPercent(Double percent) {
        }
        default boolean supportsDerating() {
            return false;
        }
        /** Запас (%) по умолчанию для ЭТОГО конкретного узла (зависит от типа узла —
         *  см. PowerCalc.defaultDeratingPercentFor), пока явно не переопределён. */
        default double defaultDeratingPercent() {
            return com.vjstb.ledscheme.service.PowerCalc.defaultDeratingPercent();
        }
    }

    public static PowerConnectorsHost forNode(AppModel model, SchemaNode node) {
        return new PowerConnectorsHost() {
            @Override
            public List<CardPort> getConnectors() {
                return node.getPowerConnectors();
            }

            @Override
            public CardPort addConnector(String connectorType, PortDirection direction, int count, int phaseCount,
                                          Double breakerAmps) {
                return model.addPowerConnectorToNode(node, connectorType, direction, count, phaseCount, breakerAmps);
            }

            @Override
            public void updateConnector(String portId, String connectorType, PortDirection direction, int count,
                                         int phaseCount, Double breakerAmps) {
                model.updatePowerConnectorOnNode(node, portId, connectorType, direction, count, phaseCount,
                        breakerAmps);
            }

            @Override
            public void removeConnector(String portId) {
                model.removePowerConnectorFromNode(node, portId);
            }

            @Override
            public Double getLoadDeratingPercent() {
                return node.getLoadDeratingPercent();
            }

            @Override
            public void setLoadDeratingPercent(Double percent) {
                model.setSchemaNodeLoadDeratingPercent(node, percent);
            }

            @Override
            public boolean supportsDerating() {
                return true;
            }

            @Override
            public double defaultDeratingPercent() {
                return com.vjstb.ledscheme.service.PowerCalc.defaultDeratingPercentFor(node.getType());
            }
        };
    }

    public static PowerConnectorsHost forPreset(AppModel model, EquipmentPreset preset) {
        return new PowerConnectorsHost() {
            @Override
            public List<CardPort> getConnectors() {
                return preset.getPowerConnectors();
            }

            @Override
            public CardPort addConnector(String connectorType, PortDirection direction, int count, int phaseCount,
                                          Double breakerAmps) {
                return model.addPowerConnectorToPreset(preset, connectorType, direction, count, phaseCount,
                        breakerAmps);
            }

            @Override
            public void updateConnector(String portId, String connectorType, PortDirection direction, int count,
                                         int phaseCount, Double breakerAmps) {
                model.updatePowerConnectorOnPreset(preset, portId, connectorType, direction, count, phaseCount,
                        breakerAmps);
            }

            @Override
            public void removeConnector(String portId) {
                model.removePowerConnectorFromPreset(preset, portId);
            }
        };
    }

    private final PowerConnectorsHost host;
    private final DefaultListModel<CardPort> listModel = new DefaultListModel<>();
    private final JList<CardPort> list = new JList<>(listModel);
    private final JLabel totalsLabel = new JLabel();

    private final JComboBox<String> connectorCombo = new JComboBox<>();
    private final JComboBox<PortDirection> directionCombo = new JComboBox<>(PortDirection.values());
    private final JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 64, 1));
    /** Сколько фаз реально разведено на добавляемой группе разъёмов (Task #80) —
     *  физически имеет смысл только 1 или 3 (однофазный/трёхфазный разъём), поэтому
     *  вместо произвольного спиннера — выпадающий список из этих двух вариантов и
     *  «Иное…» для редкого случая (запрашивает число отдельным диалогом). */
    private final JComboBox<String> phaseCombo = new JComboBox<>(new String[]{"1", "3", "Иное…"});
    private int customPhaseCount = 2;
    private boolean suppressPhaseComboEvents;
    /** Номинал автомата, А — если ниже номинала самого разъёма (Task #86); пусто —
     *  берётся номинал разъёма как есть. */
    private final javax.swing.JTextField breakerField = new javax.swing.JTextField();
    private javax.swing.JTextField deratingField;
    private final AppModel model;
    /** id разъёма, редактируемого сейчас (см. editSelected/addOrSaveConnector) —
     *  null означает обычный режим «добавить новый». */
    private String editingPortId;
    private JButton addOrSaveButton;
    private JButton editButton;

    public PowerConnectorsConfigDialog(Window owner, String title, PowerConnectorsHost host, AppModel model) {
        super(owner, "Разъёмы питания — " + title, ModalityType.APPLICATION_MODAL);
        this.host = host;
        this.model = model;
        // Кабели пользовательской библиотеки (см. AppModel.cableTypesForMode) —
        // добавляются в конец встроенного списка типов, чтобы сохранённый ранее
        // кастомный переходник (например «CEE 32A → 6×PowerCon») сразу предлагался
        // при заведении нового разъёма распределения.
        java.util.LinkedHashSet<String> types = new java.util.LinkedHashSet<>(java.util.List.of(
                "PowerCon TRUE1", "PowerCon 20A", "CEE 16A", "CEE 32A", "CEE 63A", "CEE 125A",
                "Schuko", "IEC C13", "IEC C19", "Powerlock"));
        if (model != null) {
            for (com.vjstb.ledscheme.model.CableType c : model.cableTypesForMode(com.vjstb.ledscheme.model.SchemaMode.POWER)) {
                types.add(c.getLabel());
            }
        }
        connectorCombo.setModel(new javax.swing.DefaultComboBoxModel<>(types.toArray(new String[0])));
        connectorCombo.setEditable(true);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
                if (value instanceof CardPort p) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(p.getCount()).append("× ").append(escape(p.getConnectorType())).append(" (")
                            .append(p.getDirection().getLabel().toLowerCase()).append(')');
                    if (p.getPhaseCount() > 1) {
                        sb.append(", ").append(p.getPhaseCount()).append(" фазы");
                    }
                    if (p.getBreakerAmps() != null) {
                        sb.append(", автомат ").append(UiKit.fmt(p.getBreakerAmps())).append('А');
                    }
                    setText(sb.toString());
                }
                return this;
            }
        });
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(380, 140));
        content.add(listScroll, BorderLayout.NORTH);

        JPanel mid = new JPanel();
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));

        phaseCombo.addActionListener(e -> onPhaseComboChanged());

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 4));
        form.setBorder(BorderFactory.createTitledBorder("Добавить разъём"));
        form.add(new JLabel("Тип разъёма"));
        form.add(connectorCombo);
        form.add(new JLabel("Направление"));
        form.add(directionCombo);
        form.add(new JLabel("Количество"));
        form.add(countSpinner);
        MathFields.enableExpressions(countSpinner);
        form.add(new JLabel("Фаз разведено"));
        form.add(phaseCombo);
        form.add(new JLabel("Автомат, А (необязательно)"));
        form.add(breakerField);
        mid.add(form);

        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        addOrSaveButton = new JButton("+ Добавить разъём");
        addOrSaveButton.addActionListener(e -> addOrSaveConnector());
        addRow.add(addOrSaveButton);
        editButton = new JButton("✎ Редактировать выбранный разъём");
        editButton.addActionListener(e -> toggleEditSelected());
        addRow.add(editButton);
        mid.add(Box.createVerticalStrut(4));
        mid.add(addRow);

        if (host.supportsDerating()) {
            mid.add(Box.createVerticalStrut(8));
            JPanel deratingRow = new JPanel(new BorderLayout(6, 0));
            deratingRow.setBorder(BorderFactory.createTitledBorder("Запас нагрузки для этого блока"));
            deratingField = new javax.swing.JTextField();
            Double d = host.getLoadDeratingPercent();
            deratingField.setText(d != null ? UiKit.fmt(d) : "");
            JButton applyDerating = new JButton("Применить");
            applyDerating.addActionListener(e -> applyDerating());
            deratingRow.add(new JLabel("%, пусто — по умолчанию для этого типа узла ("
                    + UiKit.fmt(host.defaultDeratingPercent()) + "%): "),
                    BorderLayout.WEST);
            deratingRow.add(deratingField, BorderLayout.CENTER);
            deratingRow.add(applyDerating, BorderLayout.EAST);
            mid.add(deratingRow);
        }
        content.add(mid, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        totalsLabel.setForeground(Palette.MUTED);
        bottom.add(totalsLabel, BorderLayout.WEST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton remove = new JButton("Удалить выбранный разъём");
        remove.addActionListener(e -> removeSelected());
        UiKit.bindDeleteKey(list, this::removeSelected);
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        btns.add(remove);
        btns.add(close);
        bottom.add(btns, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        refresh();
        pack();
        setLocationRelativeTo(owner);
    }

    private void onPhaseComboChanged() {
        if (suppressPhaseComboEvents) {
            return;
        }
        if ("Иное…".equals(phaseCombo.getSelectedItem())) {
            String input = JOptionPane.showInputDialog(this, "Число фаз:", customPhaseCount);
            if (input == null) {
                // Отмена — возвращаем предыдущий выбор, а не оставляем "Иное…" без числа.
                setPhaseCountField(customPhaseCount == 1 || customPhaseCount == 3 ? customPhaseCount : 1);
                return;
            }
            try {
                int n = Integer.parseInt(input.trim());
                if (n < 1) {
                    throw new NumberFormatException();
                }
                customPhaseCount = n;
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Введите целое число фаз (не меньше 1)",
                        "Проверка данных", JOptionPane.WARNING_MESSAGE);
                setPhaseCountField(1);
            }
        }
    }

    private int selectedPhaseCount() {
        Object sel = phaseCombo.getSelectedItem();
        if ("1".equals(sel)) {
            return 1;
        }
        if ("3".equals(sel)) {
            return 3;
        }
        return customPhaseCount;
    }

    private void setPhaseCountField(int phases) {
        suppressPhaseComboEvents = true;
        try {
            if (phases == 1) {
                phaseCombo.setSelectedItem("1");
            } else if (phases == 3) {
                phaseCombo.setSelectedItem("3");
            } else {
                customPhaseCount = phases;
                phaseCombo.setSelectedItem("Иное…");
            }
        } finally {
            suppressPhaseComboEvents = false;
        }
    }

    /** ЛКМ по кнопке «Редактировать…»: первый клик загружает выбранный разъём в
     *  форму и переключает форму/кнопки в режим редактирования; повторный клик по
     *  той же (теперь «Отменить…») кнопке отменяет редактирование без сохранения. */
    private void toggleEditSelected() {
        if (editingPortId != null) {
            cancelEdit();
            return;
        }
        CardPort sel = list.getSelectedValue();
        if (sel == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите разъём в списке выше",
                    "Редактирование", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        editingPortId = sel.getId();
        connectorCombo.getEditor().setItem(sel.getConnectorType());
        directionCombo.setSelectedItem(sel.getDirection());
        countSpinner.setValue(sel.getCount());
        setPhaseCountField(sel.getPhaseCount());
        breakerField.setText(sel.getBreakerAmps() != null ? UiKit.fmt(sel.getBreakerAmps()) : "");
        addOrSaveButton.setText("💾 Сохранить изменения");
        editButton.setText("✖ Отменить редактирование");
    }

    private void cancelEdit() {
        editingPortId = null;
        connectorCombo.getEditor().setItem("");
        breakerField.setText("");
        addOrSaveButton.setText("+ Добавить разъём");
        editButton.setText("✎ Редактировать выбранный разъём");
    }

    private void addOrSaveConnector() {
        String connector = String.valueOf(connectorCombo.getEditor().getItem()).trim();
        if (connector.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Укажите тип разъёма", "Проверка данных", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String breakerText = breakerField.getText().trim().replace(',', '.');
        Double breakerAmps = null;
        if (!breakerText.isEmpty()) {
            try {
                breakerAmps = Double.parseDouble(breakerText);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Автомат: введите число (А) или оставьте пустым",
                        "Проверка данных", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        PortDirection dir = (PortDirection) directionCombo.getSelectedItem();
        int count = (Integer) countSpinner.getValue();
        int phases = selectedPhaseCount();
        if (editingPortId != null) {
            String portId = editingPortId;
            try {
                host.updateConnector(portId, connector, dir, count, phases, breakerAmps);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                return;
            }
            cancelEdit();
        } else {
            host.addConnector(connector, dir, count, phases, breakerAmps);
            breakerField.setText("");
        }
        refresh();
    }

    private void applyDerating() {
        String text = deratingField.getText().trim().replace(',', '.');
        if (text.isEmpty()) {
            host.setLoadDeratingPercent(null);
            return;
        }
        try {
            host.setLoadDeratingPercent(Double.parseDouble(text));
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Запас: введите число (%) или оставьте пустым для значения"
                    + " по умолчанию", "Проверка данных", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void removeSelected() {
        CardPort sel = list.getSelectedValue();
        if (sel != null) {
            host.removeConnector(sel.getId());
            refresh();
        }
    }

    private void refresh() {
        listModel.clear();
        int totalIn = 0, totalOut = 0;
        for (CardPort p : host.getConnectors()) {
            listModel.addElement(p);
            if (p.getDirection() != PortDirection.OUT) {
                totalIn += p.getCount();
            }
            if (p.getDirection() != PortDirection.IN) {
                totalOut += p.getCount();
            }
        }
        totalsLabel.setText("Итого: вход " + totalIn + " · выход " + totalOut
                + " · разъёмов: " + host.getConnectors().size());
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
