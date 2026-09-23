package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.schemalayout.PortRoleResolver;
import com.vjstb.ledscheme.service.schemalayout.ThruResolver;
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
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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

        /** Тип узла-владельца карты — нужен только для "угадывания" роли гнёзд
         *  БЕЗ явного {@link CardPort#getRole()}/{@link
         *  com.vjstb.ledscheme.model.InterfaceType#getDefaultRole()} (см.
         *  {@link PortRoleResolver#resolveForLibrary}, docs/schema-ports-rework/
         *  PLAN.md, задача T5.1): один и тот же тип интерфейса (Ethernet/Fiber)
         *  означает LED-данные на контроллере/конвертере/экране и обычную IP-сеть
         *  везде остальном. {@code null} по умолчанию — тип узла неизвестен
         *  (например, "Прочее оборудование"), эвристика тогда просто не отличает
         *  LED-данные от обычной сети, откатываясь на сетевой/видео вариант. */
        default SchemaNodeType nodeTypeOrNull() {
            return null;
        }
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

            @Override
            public SchemaNodeType nodeTypeOrNull() {
                return node.getType();
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

            @Override
            public SchemaNodeType nodeTypeOrNull() {
                // Библиотека контроллеров (SmartLCT-аналог) существует только для
                // узлов-контроллеров — в отличие от пресетов, у ControllerType нет
                // отдельного поля категории.
                return SchemaNodeType.CONTROLLER;
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

            @Override
            public SchemaNodeType nodeTypeOrNull() {
                return preset.getCategory();
            }
        };
    }

    /** Сентинел «роль по умолчанию (из библиотеки/эвристика)» в комбобоксе — {@code
     *  null}, чтобы не проставлять {@link CardPort#setRole} лишний раз без нужды
     *  (см. addPendingPort/applyRoleToSelectedPending ниже). */
    private static final InterfaceRole[] ROLE_OPTIONS_WITH_DEFAULT;
    static {
        InterfaceRole[] values = InterfaceRole.values();
        ROLE_OPTIONS_WITH_DEFAULT = new InterfaceRole[values.length + 1];
        System.arraycopy(values, 0, ROLE_OPTIONS_WITH_DEFAULT, 1, values.length);
    }

    /** Три состояния транзита в комбобоксе — см. {@link CardPort#getThru()} и
     *  {@link ThruResolver}: {@code null} элемент = "авто" (угадывается по составу
     *  карты), TRUE/FALSE — явно "да"/"нет". */
    private static final Boolean[] THRU_OPTIONS = {null, Boolean.TRUE, Boolean.FALSE};

    private final CardsHost host;
    private final AppModel model;
    private final DefaultListModel<SchemaCard> listModel = new DefaultListModel<>();
    private final JList<SchemaCard> list = new JList<>(listModel);
    private final JLabel totalsLabel = new JLabel();

    private final JTextField nameField = new JTextField();
    final InterfaceTypeVersionPicker connectorPicker;
    final javax.swing.JComboBox<PortDirection> directionCombo = new javax.swing.JComboBox<>(PortDirection.values());
    final JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 64, 1));
    /** Роль и транзит добавляемой ПРЯМО СЕЙЧАС группы разъёмов (docs/schema-ports-
     *  rework/PLAN.md, задача T5.1) — по умолчанию "не задана"/"авто", т.е. группа
     *  наследует роль из вида интерфейса библиотеки/эвристики (см.
     *  {@link PortRoleResolver#resolveForLibrary}) и авто-угадывание транзита (см.
     *  {@link ThruResolver}), как и до появления этих полей. */
    // Пакетная видимость (не private) — только для CardsConfigDialogRoleThruTest
    // (тот же пакет, docs/schema-ports-rework/PLAN.md задача T5.1): дымовой Swing-тест
    // ведёт диалог через его реальные комбобоксы/список набора карты, как
    // PowerStagePanelChainRowClickTest ведёт реальные компоненты через dispatchEvent,
    // а не проверяет вручную вынесенную "чистую" копию логики массового назначения.
    final JComboBox<InterfaceRole> roleCombo = new JComboBox<>(ROLE_OPTIONS_WITH_DEFAULT);
    final JComboBox<Boolean> thruCombo = new JComboBox<>(THRU_OPTIONS);

    /** Группы разъёмов набираемой (ещё не сохранённой) карты. */
    final List<CardPort> pendingPorts = new ArrayList<>();
    private final DefaultListModel<CardPort> pendingModel = new DefaultListModel<>();
    final JList<CardPort> pendingList = new JList<>(pendingModel);

    /** id карты, редактируемой сейчас (см. toggleEditSelected/addCard) — null
     *  означает обычный режим «набрать и сохранить новую карту». */
    private String editingCardId;
    private JButton saveButton;
    private JButton editButton;

    public CardsConfigDialog(Window owner, AppModel model, SchemaNode node) {
        this(owner, labelOf(node), forNode(model, node), model);
    }

    public CardsConfigDialog(Window owner, String title, CardsHost host, AppModel model) {
        this(owner, title, host, model, false);
    }

    /** {@code readOnly} — только просмотр списка карт без права добавлять/менять/
     *  удалять (используется для карт общих/расшаренных элементов библиотеки:
     *  раньше такие элементы вообще не давали открыть этот диалог, теперь можно
     *  хотя бы посмотреть комплектацию — правка идёт через отдельную личную копию,
     *  см. LibrariesStagePanel — «Скопировать и править…»). */
    public CardsConfigDialog(Window owner, String title, CardsHost host, AppModel model, boolean readOnly) {
        super(owner, "Комплектация карт — " + title + (readOnly ? " (только просмотр)" : ""),
                ModalityType.APPLICATION_MODAL);
        this.host = host;
        this.model = model;
        this.connectorPicker = new InterfaceTypeVersionPicker(model);

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

        roleCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
                setText(value == null ? "по умолчанию (из библиотеки)" : ((InterfaceRole) value).getLabel());
                return this;
            }
        });
        thruCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
                setText(value == null ? "авто" : Boolean.TRUE.equals(value) ? "да" : "нет");
                return this;
            }
        });

        JPanel portForm = new JPanel(new GridLayout(0, 2, 6, 4));
        portForm.setBorder(BorderFactory.createTitledBorder("Добавить группу разъёмов в карту"));
        portForm.add(new JLabel("Разъём"));
        portForm.add(connectorPicker);
        portForm.add(new JLabel("Направление"));
        portForm.add(directionCombo);
        portForm.add(new JLabel("Количество"));
        portForm.add(countSpinner);
        MathFields.enableExpressions(countSpinner);
        portForm.add(new JLabel("Роль"));
        portForm.add(roleCombo);
        portForm.add(new JLabel("Транзит"));
        portForm.add(thruCombo);
        mid.add(portForm);

        JButton addPort = new JButton("+ Добавить группу разъёмов");
        addPort.addActionListener(e -> addPendingPort());
        mid.add(Box.createVerticalStrut(4));
        mid.add(addPort);

        // Множественное выделение — нужно для массового назначения роли/транзита
        // сразу нескольким группам (docs/schema-ports-rework/PLAN.md, задача T5.1),
        // а не по одной; "Убрать выбранную группу"/Delete ниже тоже работают по
        // всему выделению не хуже прежнего (JList сам поддерживает это в обоих режимах).
        pendingList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        pendingList.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
                if (value instanceof CardPort p) {
                    setText(pendingPortLabel(p));
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

        JButton assignRoleBtn = new JButton("Назначить роль выделенным ▸");
        assignRoleBtn.addActionListener(e -> showAssignRoleMenu(assignRoleBtn));
        JButton assignThruBtn = new JButton("Транзит выделенным ▸");
        assignThruBtn.addActionListener(e -> showAssignThruMenu(assignThruBtn));
        JPanel massAssignRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        massAssignRow.add(assignRoleBtn);
        massAssignRow.add(assignThruBtn);
        mid.add(Box.createVerticalStrut(2));
        mid.add(massAssignRow);

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
        if (readOnly) {
            connectorPicker.setEnabled(false);
            directionCombo.setEnabled(false);
            countSpinner.setEnabled(false);
            roleCombo.setEnabled(false);
            thruCombo.setEnabled(false);
            addPort.setEnabled(false);
            removePort.setEnabled(false);
            assignRoleBtn.setEnabled(false);
            assignThruBtn.setEnabled(false);
            nameField.setEnabled(false);
            saveButton.setEnabled(false);
            editButton.setEnabled(false);
            remove.setEnabled(false);
        }
        refresh();
        pack();
        setLocationRelativeTo(owner);
    }

    void addPendingPort() {
        String connector = connectorPicker.getValue();
        if (connector.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Укажите тип разъёма", "Проверка данных", JOptionPane.WARNING_MESSAGE);
            return;
        }
        PortDirection dir = (PortDirection) directionCombo.getSelectedItem();
        int count = (Integer) countSpinner.getValue();
        CardPort port = new CardPort(connector, dir, count);
        // null остаётся null (роль/транзит по умолчанию — из библиотеки/эвристики/
        // авто-угадывания), не подставляем какое-то значение просто потому, что
        // комбобокс сейчас на сентинеле — иначе КАЖДАЯ добавленная группа получала
        // бы "явный" thru=false вместо настоящего null (docs/schema-ports-rework/
        // PLAN.md, задача T5.1: авто-угадывание должно оставаться доступным).
        port.setRole((InterfaceRole) roleCombo.getSelectedItem());
        port.setThru((Boolean) thruCombo.getSelectedItem());
        pendingPorts.add(port);
        refreshPending();
    }

    void removePendingPort() {
        int[] indices = pendingList.getSelectedIndices();
        if (indices.length == 0) {
            return;
        }
        // В обратном порядке — иначе индексы "уезжают" после первого же remove().
        for (int i = indices.length - 1; i >= 0; i--) {
            pendingPorts.remove(indices[i]);
        }
        refreshPending();
    }

    private void refreshPending() {
        pendingModel.clear();
        for (CardPort p : pendingPorts) {
            pendingModel.addElement(p);
        }
    }

    /** Подпись строки в списке набираемой карты: явная роль/транзит — обычным
     *  шрифтом, УГАДАННые (когда {@link CardPort#getRole()}/{@link
     *  CardPort#getThru()} ещё {@code null}) — курсивом с пометкой, чтобы было видно,
     *  что это не сохранённое в библиотеке значение, а вычисленное на лету (docs/
     *  schema-ports-rework/PLAN.md, задача T5.1). Транзит имеет смысл только для
     *  OUT-групп (см. {@link ThruResolver} class-javadoc) — для IN/IN_OUT не
     *  показывается вовсе, а не выводится бессмысленное "нет". */
    String pendingPortLabel(CardPort p) {
        StringBuilder sb = new StringBuilder("<html>");
        sb.append(p.getCount()).append("× ").append(escape(p.getConnectorType()))
                .append(" (").append(p.getDirection().getLabel().toLowerCase()).append(')');
        InterfaceRole role = PortRoleResolver.resolveForLibrary(p, host.nodeTypeOrNull(), model.getInterfaceTypes());
        if (p.getRole() != null) {
            sb.append(" · Роль: ").append(role.getLabel());
        } else {
            sb.append(" · <i>Роль: ").append(role.getLabel()).append(" (угадано)</i>");
        }
        if (p.getDirection() == PortDirection.OUT) {
            boolean thru = ThruResolver.isThru(p, pendingPorts, null);
            if (p.getThru() != null) {
                sb.append(" · Транзит: ").append(thru ? "да" : "нет");
            } else {
                sb.append(" · <i>Транзит: ").append(thru ? "да" : "нет").append(" (авто)</i>");
            }
        }
        sb.append("</html>");
        return sb.toString();
    }

    /** Меню массового назначения роли (docs/schema-ports-rework/PLAN.md, задача
     *  T5.1) — применяется КО ВСЕМ группам, выделенным в pendingList сейчас; пустое
     *  выделение — предупреждение вместо тихого бездействия (легко забыть выделить
     *  хоть что-то, кнопка при этом остаётся активной всегда). */
    void showAssignRoleMenu(Component invoker) {
        List<CardPort> selected = pendingList.getSelectedValuesList();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Сначала выделите группы разъёмов в списке выше",
                    "Назначение роли", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        JMenuItem byDefault = new JMenuItem("по умолчанию (из библиотеки)");
        byDefault.addActionListener(e -> applyRoleToSelectedPending(selected, null));
        menu.add(byDefault);
        menu.addSeparator();
        for (InterfaceRole role : InterfaceRole.values()) {
            JMenuItem item = new JMenuItem(role.getLabel());
            item.addActionListener(e -> applyRoleToSelectedPending(selected, role));
            menu.add(item);
        }
        menu.show(invoker, 0, invoker.getHeight());
    }

    void applyRoleToSelectedPending(List<CardPort> selected, InterfaceRole role) {
        for (CardPort p : selected) {
            p.setRole(role);
        }
        refreshPending();
    }

    /** Меню массового назначения транзита — та же логика, что {@link
     *  #showAssignRoleMenu}, но всего три варианта (см. {@link #THRU_OPTIONS}). */
    void showAssignThruMenu(Component invoker) {
        List<CardPort> selected = pendingList.getSelectedValuesList();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Сначала выделите группы разъёмов в списке выше",
                    "Назначение транзита", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        JMenuItem auto = new JMenuItem("авто");
        auto.addActionListener(e -> applyThruToSelectedPending(selected, null));
        JMenuItem yes = new JMenuItem("да");
        yes.addActionListener(e -> applyThruToSelectedPending(selected, Boolean.TRUE));
        JMenuItem no = new JMenuItem("нет");
        no.addActionListener(e -> applyThruToSelectedPending(selected, Boolean.FALSE));
        menu.add(auto);
        menu.add(yes);
        menu.add(no);
        menu.show(invoker, 0, invoker.getHeight());
    }

    void applyThruToSelectedPending(List<CardPort> selected, Boolean thru) {
        for (CardPort p : selected) {
            p.setThru(thru);
        }
        refreshPending();
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
