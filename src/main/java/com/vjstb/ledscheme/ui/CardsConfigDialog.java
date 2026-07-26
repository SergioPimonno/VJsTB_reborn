package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;

/**
 * Комплектация карт ввода/вывода (медиасервер/видеопроцессор наподобие Barco E2,
 * PixelHue Q8, или карты видеовхода Novastar H-серии): список установленных карт
 * + добавление/удаление. Работает и с узлом общей схемы, и с пресетом библиотеки
 * оборудования — через {@link CardsHost}. Одна карта может сочетать несколько
 * групп разъёмов разного типа/направления (например 2×HDMI2.1 IN + 2×DP1.2 IN на
 * одной карте) — перед сохранением карты пользователь набирает список таких групп.
 * Суммарные IN/OUT считаются по всем картам.
 */
public class CardsConfigDialog extends JDialog {

    /** Абстракция над источником карт — узел схемы или пресет библиотеки. */
    public interface CardsHost {
        List<SchemaCard> getCards();
        void addCard(String name, List<CardPort> ports);
        /** Правит уже существующую карту НА МЕСТЕ (сохраняя id) — не удалять и
         *  добавлять заново, иначе провод схемы, подключённый к порту этой карты,
         *  потерял бы соединение (см. PowerConnectorsConfigDialog.updateConnector). */
        void updateCard(String cardId, String name, List<CardPort> ports);
        void removeCard(String cardId);
    }

    public static CardsHost forNode(AppModel model, SchemaNode node) {
        return new CardsHost() {
            @Override
            public List<SchemaCard> getCards() {
                return node.getCards();
            }

            @Override
            public void addCard(String name, List<CardPort> ports) {
                model.addCardToNode(node, name, ports);
            }

            @Override
            public void updateCard(String cardId, String name, List<CardPort> ports) {
                model.updateCardOnNode(node, cardId, name, ports);
            }

            @Override
            public void removeCard(String cardId) {
                model.removeCardFromNode(node, cardId);
            }
        };
    }

    public static CardsHost forController(AppModel model, ControllerType controllerType) {
        return new CardsHost() {
            @Override
            public List<SchemaCard> getCards() {
                return controllerType.getCards();
            }

            @Override
            public void addCard(String name, List<CardPort> ports) {
                model.addCardToController(controllerType, name, ports);
            }

            @Override
            public void updateCard(String cardId, String name, List<CardPort> ports) {
                model.updateCardOnController(controllerType, cardId, name, ports);
            }

            @Override
            public void removeCard(String cardId) {
                model.removeCardFromController(controllerType, cardId);
            }
        };
    }

    public static CardsHost forPreset(AppModel model, com.vjstb.ledscheme.model.EquipmentPreset preset) {
        return new CardsHost() {
            @Override
            public List<SchemaCard> getCards() {
                return preset.getCards();
            }

            @Override
            public void addCard(String name, List<CardPort> ports) {
                model.addCardToPreset(preset, name, ports);
            }

            @Override
            public void updateCard(String cardId, String name, List<CardPort> ports) {
                model.updateCardOnPreset(preset, cardId, name, ports);
            }

            @Override
            public void removeCard(String cardId) {
                model.removeCardFromPreset(preset, cardId);
            }
        };
    }

    private final CardsHost host;
    private final DefaultListModel<SchemaCard> listModel = new DefaultListModel<>();
    private final JList<SchemaCard> list = new JList<>(listModel);
    private final JLabel totalsLabel = new JLabel();

    private final JTextField nameField = new JTextField();
    private final javax.swing.JComboBox<String> connectorCombo = new javax.swing.JComboBox<>(
            new String[]{"HDMI 2.1", "HDMI 2.0", "DisplayPort 1.4", "DisplayPort 1.2", "SDI", "DVI",
                    "Fiber", "Ethernet", "USB-C", "Thunderbolt", "Генлок"});
    private final javax.swing.JComboBox<PortDirection> directionCombo = new javax.swing.JComboBox<>(PortDirection.values());
    private final JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 64, 1));

    /** Группы разъёмов набираемой (ещё не сохранённой) карты. */
    private final List<CardPort> pendingPorts = new ArrayList<>();
    private final DefaultListModel<CardPort> pendingModel = new DefaultListModel<>();
    private final JList<CardPort> pendingList = new JList<>(pendingModel);

    /** id карты, редактируемой сейчас (см. toggleEditSelected/addCard) — null
     *  означает обычный режим «набрать и сохранить новую карту». */
    private String editingCardId;
    private JButton saveButton;
    private JButton editButton;

    public CardsConfigDialog(Window owner, AppModel model, SchemaNode node) {
        this(owner, labelOf(node), forNode(model, node));
    }

    public CardsConfigDialog(Window owner, String title, CardsHost host) {
        super(owner, "Комплектация карт — " + title, ModalityType.APPLICATION_MODAL);
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
                if (value instanceof SchemaCard c) {
                    setText("<html><b>" + escape(c.getName()) + "</b> — " + escape(c.portsSummary()) + "</html>");
                }
                return this;
            }
        });
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(420, 140));
        content.add(listScroll, BorderLayout.NORTH);

        JPanel mid = new JPanel();
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));

        JPanel portForm = new JPanel(new GridLayout(0, 2, 6, 4));
        portForm.setBorder(BorderFactory.createTitledBorder("Добавить группу разъёмов в карту"));
        portForm.add(new JLabel("Разъём"));
        portForm.add(connectorCombo);
        portForm.add(new JLabel("Направление"));
        portForm.add(directionCombo);
        portForm.add(new JLabel("Количество"));
        portForm.add(countSpinner);
        MathFields.enableExpressions(countSpinner);
        mid.add(portForm);

        JButton addPort = new JButton("+ Добавить группу разъёмов");
        addPort.addActionListener(e -> addPendingPort());
        mid.add(Box.createVerticalStrut(4));
        mid.add(addPort);

        pendingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pendingList.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
                if (value instanceof CardPort p) {
                    setText(p.getCount() + "× " + p.getConnectorType() + " ("
                            + (p.getDirection() == PortDirection.IN ? "вход" : "выход") + ")");
                }
                return this;
            }
        });
        JScrollPane pendingScroll = new JScrollPane(pendingList);
        pendingScroll.setPreferredSize(new Dimension(420, 70));
        mid.add(Box.createVerticalStrut(6));
        mid.add(pendingScroll);
        JButton removePort = new JButton("Убрать выбранную группу");
        removePort.addActionListener(e -> removePendingPort());
        UiKit.bindDeleteKey(pendingList, this::removePendingPort);
        mid.add(Box.createVerticalStrut(4));
        mid.add(removePort);

        JPanel nameForm = new JPanel(new GridLayout(0, 2, 6, 4));
        nameForm.setBorder(BorderFactory.createTitledBorder("Название и сохранение карты"));
        nameField.putClientProperty("JTextField.placeholderText", "например, «Видеовход 1»…");
        nameForm.add(new JLabel("Название карты"));
        nameForm.add(nameField);
        mid.add(Box.createVerticalStrut(6));
        mid.add(nameForm);

        saveButton = new JButton("+ Сохранить карту");
        saveButton.addActionListener(e -> addCard());
        mid.add(Box.createVerticalStrut(4));
        mid.add(saveButton);
        content.add(mid, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        totalsLabel.setForeground(Palette.MUTED);
        bottom.add(totalsLabel, BorderLayout.WEST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        editButton = new JButton("✎ Редактировать выбранную карту");
        editButton.addActionListener(e -> toggleEditSelected());
        JButton remove = new JButton("Удалить выбранную карту");
        remove.addActionListener(e -> removeSelected());
        UiKit.bindDeleteKey(list, this::removeSelected);
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        btns.add(editButton);
        btns.add(remove);
        btns.add(close);
        bottom.add(btns, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        refresh();
        pack();
        setLocationRelativeTo(owner);
    }

    private void addPendingPort() {
        String connector = String.valueOf(connectorCombo.getEditor().getItem()).trim();
        if (connector.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Укажите тип разъёма", "Проверка данных", JOptionPane.WARNING_MESSAGE);
            return;
        }
        PortDirection dir = (PortDirection) directionCombo.getSelectedItem();
        int count = (Integer) countSpinner.getValue();
        pendingPorts.add(new CardPort(connector, dir, count));
        refreshPending();
    }

    private void removePendingPort() {
        int idx = pendingList.getSelectedIndex();
        if (idx >= 0) {
            pendingPorts.remove(idx);
            refreshPending();
        }
    }

    private void refreshPending() {
        pendingModel.clear();
        for (CardPort p : pendingPorts) {
            pendingModel.addElement(p);
        }
    }

    private void addCard() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Укажите название карты", "Проверка данных",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (pendingPorts.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Добавьте хотя бы одну группу разъёмов", "Проверка данных",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (editingCardId != null) {
            String cardId = editingCardId;
            try {
                host.updateCard(cardId, name, new ArrayList<>(pendingPorts));
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                return;
            }
            cancelEdit();
        } else {
            host.addCard(name, new ArrayList<>(pendingPorts));
            nameField.setText("");
        }
        pendingPorts.clear();
        refreshPending();
        refresh();
    }

    /** ЛКМ по кнопке «Редактировать…»: первый клик загружает выбранную карту (её
     *  название и группы разъёмов — КОПИЯМИ, сохраняя id каждой группы, если её не
     *  уберут из списка ниже) в форму набора карты; повторный клик по той же
     *  (теперь «Отменить…») кнопке отменяет редактирование без сохранения. */
    private void toggleEditSelected() {
        if (editingCardId != null) {
            cancelEdit();
            return;
        }
        SchemaCard sel = list.getSelectedValue();
        if (sel == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите карту в списке выше",
                    "Редактирование", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        editingCardId = sel.getId();
        nameField.setText(sel.getName());
        pendingPorts.clear();
        for (CardPort p : sel.getPorts()) {
            pendingPorts.add(p.copy());
        }
        refreshPending();
        saveButton.setText("💾 Сохранить изменения карты");
        editButton.setText("✖ Отменить редактирование");
    }

    private void cancelEdit() {
        editingCardId = null;
        nameField.setText("");
        pendingPorts.clear();
        refreshPending();
        saveButton.setText("+ Сохранить карту");
        editButton.setText("✎ Редактировать выбранную карту");
    }

    private void removeSelected() {
        SchemaCard sel = list.getSelectedValue();
        if (sel != null) {
            host.removeCard(sel.getId());
            refresh();
        }
    }

    private void refresh() {
        listModel.clear();
        int totalIn = 0, totalOut = 0;
        for (SchemaCard c : host.getCards()) {
            listModel.addElement(c);
            totalIn += c.totalInputs();
            totalOut += c.totalOutputs();
        }
        totalsLabel.setText("Итого: IN " + totalIn + " · OUT " + totalOut + " · карт: " + host.getCards().size());
    }

    private static String labelOf(SchemaNode node) {
        return node.getLabel() == null || node.getLabel().isEmpty() ? node.getType().getLabel() : node.getLabel();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
