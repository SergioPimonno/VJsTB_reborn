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

    /** Абстракция над источником разъёмов — узел схемы или пресет библиотеки. */
    public interface PowerConnectorsHost {
        List<CardPort> getConnectors();
        void addConnector(String connectorType, PortDirection direction, int count);
        void removeConnector(String portId);
    }

    public static PowerConnectorsHost forNode(AppModel model, SchemaNode node) {
        return new PowerConnectorsHost() {
            @Override
            public List<CardPort> getConnectors() {
                return node.getPowerConnectors();
            }

            @Override
            public void addConnector(String connectorType, PortDirection direction, int count) {
                model.addPowerConnectorToNode(node, connectorType, direction, count);
            }

            @Override
            public void removeConnector(String portId) {
                model.removePowerConnectorFromNode(node, portId);
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
            public void addConnector(String connectorType, PortDirection direction, int count) {
                model.addPowerConnectorToPreset(preset, connectorType, direction, count);
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

    private final JComboBox<String> connectorCombo = new JComboBox<>(new String[]{
            "PowerCon TRUE1", "PowerCon 20A", "CEE 16A", "CEE 32A", "CEE 63A", "CEE 125A",
            "Schuko", "IEC C13", "IEC C19", "Powerlock"});
    private final JComboBox<PortDirection> directionCombo = new JComboBox<>(PortDirection.values());
    private final JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 64, 1));

    public PowerConnectorsConfigDialog(Window owner, String title, PowerConnectorsHost host) {
        super(owner, "Разъёмы питания — " + title, ModalityType.APPLICATION_MODAL);
        this.host = host;
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
                    setText(p.getCount() + "× " + escape(p.getConnectorType()) + " ("
                            + (p.getDirection() == PortDirection.IN ? "вход" : "выход") + ")");
                }
                return this;
            }
        });
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(380, 140));
        content.add(listScroll, BorderLayout.NORTH);

        JPanel mid = new JPanel();
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 4));
        form.setBorder(BorderFactory.createTitledBorder("Добавить разъём"));
        form.add(new JLabel("Тип разъёма"));
        form.add(connectorCombo);
        form.add(new JLabel("Направление"));
        form.add(directionCombo);
        form.add(new JLabel("Количество"));
        form.add(countSpinner);
        mid.add(form);

        JButton add = new JButton("+ Добавить разъём");
        add.addActionListener(e -> addConnector());
        mid.add(Box.createVerticalStrut(4));
        mid.add(add);
        content.add(mid, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        totalsLabel.setForeground(Palette.MUTED);
        bottom.add(totalsLabel, BorderLayout.WEST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton remove = new JButton("Удалить выбранный разъём");
        remove.addActionListener(e -> removeSelected());
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

    private void addConnector() {
        String connector = String.valueOf(connectorCombo.getEditor().getItem()).trim();
        if (connector.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Укажите тип разъёма", "Проверка данных", JOptionPane.WARNING_MESSAGE);
            return;
        }
        PortDirection dir = (PortDirection) directionCombo.getSelectedItem();
        int count = (Integer) countSpinner.getValue();
        host.addConnector(connector, dir, count);
        refresh();
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
            if (p.getDirection() == PortDirection.IN) {
                totalIn += p.getCount();
            } else {
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
