package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Network;
import com.vjstb.ledscheme.model.NetworkDeviceType;
import com.vjstb.ledscheme.model.NetworkManagerPlan;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;

/**
 * «Сетевой менеджер» — третья вкладка раздела «Сигнал» (запрос пользователя:
 * многие устройства общей схемы сигнала объединяются в компьютерные сети для
 * удобства администрирования — статические адреса, проверка доступности,
 * веб-интерфейс; референс по стилю размещения — Cisco Packet Tracer). См.
 * NETWORK_MANAGER_NOTES.md за полным обоснованием дизайна.
 *
 * <p>Слева — список СЕТЕЙ этой сцены (добавить/переименовать/удалить), в
 * рамках одной сцены их может быть несколько; справа — палитра источников
 * устройств (существующие узлы общей схемы сигнала ЭТОЙ сцены, кроме экранов
 * — см. {@link #refreshSchemaPalette} — + каталог {@link NetworkDeviceType})
 * и {@link NetworkCanvasPanel} выбранной сети. Добавление — кнопкой «Добавить
 * в сеть» ИЛИ перетаскиванием элемента палитры прямо на нужное место канваса
 * (см. {@link SchemaNodeTransferable}/{@link DeviceTypeTransferable}, приём —
 * {@code TransferHandler} на самом {@link #canvas} в конструкторе).
 *
 * <p>Персистентность — как у {@code VehicleLoadVisualizerDialog}: {@link
 * Scene#getNetworkManagerPlan()}, сохраняется на каждое дискретное изменение
 * через {@code AppModel.saveNetworkManagerPlan}. В отличие от диалога (тот
 * пересоздаётся при каждом открытии) эта панель ЖИВЁТ всё время работы
 * приложения (встроена в {@code SignalStagePanel}), поэтому сама следит за
 * сменой текущей сцены через {@code model.addListener} и перезагружает план
 * заново при смене {@code Scene#getId()} — см. {@link #refresh()}. */
public class NetworkManagerPanel extends JPanel {

    private final AppModel model;
    private final SettingsManager settings;

    private final DefaultListModel<Network> networkListModel = new DefaultListModel<>();
    private final JList<Network> networkList = new JList<>(networkListModel);
    private final NetworkCanvasPanel canvas;
    private final JLabel statusLabel = UiKit.muted(" ");

    private final DefaultListModel<SchemaNode> schemaPaletteModel = new DefaultListModel<>();
    private final JList<SchemaNode> schemaPaletteList = new JList<>(schemaPaletteModel);
    private final DefaultListModel<NetworkDeviceType> devicePaletteModel = new DefaultListModel<>();
    private final JList<NetworkDeviceType> devicePaletteList = new JList<>(devicePaletteModel);

    private NetworkManagerPlan currentPlan;
    private String lastSceneId;
    /** Единый экземпляр, не пересоздаётся на каждый клик кнопки — см. её
     *  class-javadoc про то, почему (нет {@code AppModel.removeListener},
     *  повторные открытия иначе плодили бы висящие листенеры). */
    private NetworkAddressTableDialog addressTableDialog;

    public NetworkManagerPanel(AppModel model, SettingsManager settings) {
        this.model = model;
        this.settings = settings;
        this.canvas = new NetworkCanvasPanel(model, settings);

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(buildToolbar(), BorderLayout.NORTH);
        add(buildNetworkListPanel(), BorderLayout.WEST);

        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildPalettePanel(), buildCanvasHost());
        centerSplit.setResizeWeight(0.22);
        add(centerSplit, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        canvas.setOnChanged(this::persistPlan);
        canvas.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDrop() && (support.isDataFlavorSupported(NetworkCanvasPanel.SCHEMA_NODE_FLAVOR)
                        || support.isDataFlavorSupported(NetworkCanvasPanel.DEVICE_TYPE_FLAVOR));
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                Point dropPoint = support.getDropLocation().getDropPoint();
                double[] c = canvas.pxToCanvas(dropPoint);
                try {
                    if (support.isDataFlavorSupported(NetworkCanvasPanel.SCHEMA_NODE_FLAVOR)) {
                        SchemaNode node = (SchemaNode) support.getTransferable()
                                .getTransferData(NetworkCanvasPanel.SCHEMA_NODE_FLAVOR);
                        canvas.addLinkedDeviceAt(node, c[0] - NetworkCanvasPanel.DEVICE_W / 2.0,
                                c[1] - NetworkCanvasPanel.DEVICE_H / 2.0);
                    } else {
                        NetworkDeviceType type = (NetworkDeviceType) support.getTransferable()
                                .getTransferData(NetworkCanvasPanel.DEVICE_TYPE_FLAVOR);
                        canvas.addCatalogDeviceAt(type, c[0] - NetworkCanvasPanel.DEVICE_W / 2.0,
                                c[1] - NetworkCanvasPanel.DEVICE_H / 2.0);
                    }
                    return true;
                } catch (UnsupportedFlavorException | IOException ex) {
                    return false;
                }
            }
        });

        model.addListener(this::refresh);
        refresh();
    }

    // ---- глобальный тулбар (не привязан к конкретной сети) ----

    private JPanel buildToolbar() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton addressTable = new JButton("Таблица адресов…");
        addressTable.setToolTipText("Сводная таблица всех устройств всех сетей текущей сцены (с подсветкой"
                + " конфликтующих IP) — держите открытой рядом как шпаргалку во время настройки.");
        addressTable.addActionListener(e -> openAddressTable());
        row.add(addressTable);

        JButton scan = new JButton("Найти устройства (скан IP)…");
        scan.setToolTipText("Пингует диапазон IP-адресов и находит отвечающие — удобно для устройств"
                + " с неизвестным/забытым адресом. Найденное можно сразу добавить в выбранную слева сеть.");
        scan.addActionListener(e -> openScanDialog());
        row.add(scan);
        return row;
    }

    private void openAddressTable() {
        if (addressTableDialog == null) {
            addressTableDialog = new NetworkAddressTableDialog(
                    (java.awt.Window) javax.swing.SwingUtilities.getWindowAncestor(this), model);
        }
        addressTableDialog.showAndRefresh();
    }

    private void openScanDialog() {
        NetworkScanDialog dlg = new NetworkScanDialog(
                (java.awt.Window) javax.swing.SwingUtilities.getWindowAncestor(this), this::addDiscoveredDevice);
        dlg.setVisible(true);
    }

    /** Callback {@link NetworkScanDialog} — добавляет найденный сканом адрес как
     *  новое устройство в СЕЙЧАС выбранную слева сеть (см. {@link
     *  NetworkCanvasPanel#addDiscoveredDevice}). Без выбранной сети добавлять
     *  некуда — {@code canvas.devices} тогда указывает на одноразовый пустой
     *  список, не на реальную {@link Network} (см. {@link #onNetworkSelected}),
     *  добавление молча терялось бы при следующем переключении. */
    private void addDiscoveredDevice(String ip) {
        if (networkList.getSelectedValue() == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите сеть слева — устройство добавляется в неё.",
                    "Нет выбранной сети", JOptionPane.WARNING_MESSAGE);
            return;
        }
        canvas.addDiscoveredDevice(ip);
    }

    // ---- список сетей ----

    private JPanel buildNetworkListPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Сети"));
        panel.setPreferredSize(new Dimension(200, 300));

        networkList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        networkList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.getName().isBlank() ? "(без названия)" : value.getName());
            label.setIcon(new ColorSwatchIcon(networkColor(value)));
            label.setIconTextGap(6);
            label.setOpaque(true);
            label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        networkList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onNetworkSelected();
            }
        });
        panel.add(new JScrollPane(networkList), BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        JButton add = new JButton("+ Сеть");
        add.addActionListener(e -> addNetwork());
        JButton rename = new JButton("Переименовать…");
        rename.addActionListener(e -> renameSelectedNetwork());
        JButton color = new JButton("Цвет…");
        color.addActionListener(e -> pickNetworkColor());
        JButton remove = new JButton("Удалить сеть");
        remove.addActionListener(e -> removeSelectedNetwork());
        buttons.add(add);
        buttons.add(rename);
        buttons.add(color);
        buttons.add(remove);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void addNetwork() {
        if (currentPlan == null) {
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Название сети:", "Новая сеть", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        Network network = new Network();
        network.setName(name.trim());
        network.setColor(defaultColorForIndex(currentPlan.getNetworks().size()).getRGB());
        currentPlan.getNetworks().add(network);
        networkListModel.addElement(network);
        networkList.setSelectedValue(network, true);
        persistPlan();
    }

    /** Цвет по умолчанию для новой сети — по золотому углу от её порядкового индекса
     *  (см. {@link Network#getColor()} javadoc), соседние по порядку сети получают
     *  заметно разные оттенки без ручного выбора. */
    private static Color defaultColorForIndex(int index) {
        float hue = (float) ((index * 0.618033988749895) % 1.0);
        return Color.getHSBColor(hue, 0.62f, 0.92f);
    }

    private Color networkColor(Network network) {
        return network.getColor() != null ? new Color(network.getColor()) : NetworkCanvasPanel.DEFAULT_LINK_COLOR;
    }

    private void pickNetworkColor() {
        Network selected = networkList.getSelectedValue();
        if (selected == null) {
            return;
        }
        Color chosen = UiKit.showColorChooser(this, "Цвет линий связи сети «" + selected.getName() + "»",
                networkColor(selected));
        if (chosen == null) {
            return;
        }
        selected.setColor(chosen.getRGB());
        networkList.repaint();
        canvas.setLinkColor(chosen);
        persistPlan();
    }

    private void renameSelectedNetwork() {
        Network selected = networkList.getSelectedValue();
        if (selected == null) {
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Название сети:", selected.getName());
        if (name == null || name.isBlank()) {
            return;
        }
        selected.setName(name.trim());
        networkList.repaint();
        persistPlan();
    }

    private void removeSelectedNetwork() {
        Network selected = networkList.getSelectedValue();
        if (selected == null) {
            return;
        }
        if (!selected.getDevices().isEmpty()) {
            int result = JOptionPane.showConfirmDialog(this,
                    "В сети «" + selected.getName() + "» есть устройства — удалить её вместе с ними?",
                    "Удалить сеть", JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
        currentPlan.getNetworks().remove(selected);
        networkListModel.removeElement(selected);
        persistPlan();
    }

    private void onNetworkSelected() {
        Network selected = networkList.getSelectedValue();
        canvas.setDevices(selected != null ? selected.getDevices() : new ArrayList<>());
        canvas.setLinks(selected != null ? selected.getLinks() : new ArrayList<>());
        canvas.setLinkColor(selected != null ? networkColor(selected) : null);
        refreshPaletteAvailability();
    }

    // ---- палитра источников ----

    private JPanel buildPalettePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(260, 300));

        JPanel schemaSection = new JPanel(new BorderLayout(4, 4));
        schemaSection.setBorder(BorderFactory.createTitledBorder("Узлы общей схемы сигнала"));
        schemaPaletteList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        schemaPaletteList.setCellRenderer((list, value, index, isSelected, cellHasFocus) ->
                cellLabel(value.getLabel(), list, isSelected));
        schemaPaletteList.setDragEnabled(true);
        schemaPaletteList.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return TransferHandler.COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                SchemaNode sel = schemaPaletteList.getSelectedValue();
                return sel == null ? null : new SchemaNodeTransferable(sel);
            }
        });
        schemaSection.add(new JScrollPane(schemaPaletteList), BorderLayout.CENTER);
        JButton addSchemaNode = new JButton("Добавить в сеть");
        addSchemaNode.addActionListener(e -> {
            SchemaNode sel = schemaPaletteList.getSelectedValue();
            Network net = networkList.getSelectedValue();
            if (sel == null || net == null) {
                return;
            }
            canvas.addLinkedDevice(sel);
            refreshPaletteAvailability();
        });
        schemaSection.add(addSchemaNode, BorderLayout.SOUTH);

        JPanel deviceSection = new JPanel(new BorderLayout(4, 4));
        deviceSection.setBorder(BorderFactory.createTitledBorder("Каталог сетевого оборудования"));
        devicePaletteList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        devicePaletteList.setCellRenderer((list, value, index, isSelected, cellHasFocus) ->
                cellLabel(value.getName() + " (" + value.getCategory().getLabel() + ")", list, isSelected));
        devicePaletteList.setDragEnabled(true);
        devicePaletteList.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return TransferHandler.COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                NetworkDeviceType sel = devicePaletteList.getSelectedValue();
                return sel == null ? null : new DeviceTypeTransferable(sel);
            }
        });
        deviceSection.add(new JScrollPane(devicePaletteList), BorderLayout.CENTER);
        JPanel deviceButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton addDevice = new JButton("Добавить в сеть");
        addDevice.addActionListener(e -> {
            NetworkDeviceType sel = devicePaletteList.getSelectedValue();
            Network net = networkList.getSelectedValue();
            if (sel == null || net == null) {
                return;
            }
            canvas.addCatalogDevice(sel);
        });
        JButton newType = new JButton("+ Новый тип…");
        newType.setToolTipText("Библиотека не предусмотрела нужное оборудование? Задайте свой тип прямо здесь —"
                + " сохранится в личную библиотеку и сразу станет доступен, можно предложить в общую на модерацию.");
        newType.addActionListener(e -> createCustomDeviceType());
        deviceButtons.add(addDevice);
        deviceButtons.add(newType);
        deviceSection.add(deviceButtons, BorderLayout.SOUTH);

        panel.add(schemaSection);
        panel.add(deviceSection);
        return panel;
    }

    private static JLabel cellLabel(String text, JList<?> list, boolean isSelected) {
        JLabel label = new JLabel(text);
        label.setOpaque(true);
        label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
        label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
        return label;
    }

    private void createCustomDeviceType() {
        NetworkDeviceType created = new NetworkDeviceTypeDialog(
                (java.awt.Window) javax.swing.SwingUtilities.getWindowAncestor(this), null).showDialog();
        if (created == null) {
            return;
        }
        NetworkDeviceType saved;
        try {
            saved = model.addNetworkDeviceType(created);
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Проверка данных", JOptionPane.WARNING_MESSAGE);
            return;
        }
        refreshDevicePalette();
        if (settings != null) {
            ProposeDialog.show((java.awt.Window) javax.swing.SwingUtilities.getWindowAncestor(this), settings,
                    "NETWORK_DEVICE", saved.getName(), saved);
        }
    }

    private JScrollPane buildCanvasHost() {
        JScrollPane scroll = new JScrollPane(canvas);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    // ---- обновление/персист ----

    private void persistPlan() {
        Scene scene = model.getCurrentScene();
        if (scene == null || currentPlan == null) {
            return;
        }
        model.saveNetworkManagerPlan(scene, currentPlan);
        refreshPaletteAvailability();
        int total = 0;
        for (Network n : currentPlan.getNetworks()) {
            total += n.getDevices().size();
        }
        statusLabel.setText(" Сетей: " + currentPlan.getNetworks().size() + ", устройств всего: " + total);
    }

    /** Единая точка обновления — вызывается из {@code model.addListener}, то есть
     *  на КАЖДОЕ изменение модели, не только относящееся к Сетевому менеджеру
     *  (тот же приём, что {@code SignalStagePanel}/{@code SchemaPanel}). Перечитывает
     *  план заново ТОЛЬКО при смене текущей сцены ({@link #lastSceneId}) — иначе
     *  обычный клик где-то ещё в приложении откатывал бы несохранённое выделение
     *  сети/выбор в палитре. */
    private void refresh() {
        Scene scene = model.getCurrentScene();
        String sceneId = scene != null ? scene.getId() : null;
        if (!Objects.equals(sceneId, lastSceneId)) {
            lastSceneId = sceneId;
            loadPlanForCurrentScene(scene);
        }
        refreshDevicePalette();
        refreshSchemaPalette();
    }

    private void loadPlanForCurrentScene(Scene scene) {
        networkListModel.clear();
        if (scene == null) {
            currentPlan = null;
            canvas.setDevices(new ArrayList<>());
            statusLabel.setText(" Сцена не выбрана.");
            return;
        }
        NetworkManagerPlan plan = scene.getNetworkManagerPlan();
        currentPlan = plan != null ? plan : new NetworkManagerPlan();
        for (Network n : currentPlan.getNetworks()) {
            networkListModel.addElement(n);
        }
        if (!networkListModel.isEmpty()) {
            networkList.setSelectedIndex(0);
        } else {
            canvas.setDevices(new ArrayList<>());
        }
        statusLabel.setText(" Сетей: " + currentPlan.getNetworks().size());
    }

    private void refreshDevicePalette() {
        NetworkDeviceType selected = devicePaletteList.getSelectedValue();
        devicePaletteModel.clear();
        for (NetworkDeviceType t : model.getNetworkDeviceTypes()) {
            devicePaletteModel.addElement(t);
        }
        if (selected != null) {
            devicePaletteList.setSelectedValue(selected, true);
        }
    }

    /** Список узлов схемы для добавления — узлы сигнальной схемы текущей сцены, ЗА
     *  ВЫЧЕТОМ экранов (баг-репорт: "у экранов самих по себе айпишников нет, их
     *  добавлять не нужно в этом менеджере" — {@code SchemaNodeType.SCREEN}
     *  единственный тип, у которого в принципе нет сетевого адреса, остальные
     *  категории — контроллеры/медиасерверы/конвертеры/... — вполне могут быть
     *  сетевыми устройствами) и уже добавленных В ТЕКУЩУЮ ВЫБРАННУЮ сеть (не во ВСЕ
     *  сети разом — одно физическое устройство может осмысленно входить в
     *  несколько логических сетей, например при двух сетевых интерфейсах). */
    private void refreshSchemaPalette() {
        SchemaNode selected = schemaPaletteList.getSelectedValue();
        schemaPaletteModel.clear();
        Network current = networkList.getSelectedValue();
        Set<String> usedInCurrent = current == null ? Set.of()
                : current.getDevices().stream()
                        .map(NetworkDevicePlacement::getLinkedSchemaNodeId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        for (SchemaNode node : model.schemaNodesForCurrentScene(SchemaMode.SIGNAL)) {
            if (node.getType() != SchemaNodeType.SCREEN && !usedInCurrent.contains(node.getId())) {
                schemaPaletteModel.addElement(node);
            }
        }
        if (selected != null) {
            schemaPaletteList.setSelectedValue(selected, true);
        }
    }

    private void refreshPaletteAvailability() {
        refreshSchemaPalette();
    }

    /** Носитель drag-n-drop для {@link SchemaNode} из палитры схемы — тот же приём,
     *  что {@code VehicleLoadVisualizerDialog.CaseTypeTransferable}. */
    private static final class SchemaNodeTransferable implements Transferable {
        private final SchemaNode node;

        SchemaNodeTransferable(SchemaNode node) {
            this.node = node;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{NetworkCanvasPanel.SCHEMA_NODE_FLAVOR};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return NetworkCanvasPanel.SCHEMA_NODE_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return node;
        }
    }

    /** Маленький цветной квадрат-образец в ячейке списка сетей — цвет линий связи
     *  этой сети (см. {@link Network#getColor()}). */
    private static final class ColorSwatchIcon implements javax.swing.Icon {
        private static final int SIZE = 10;
        private final Color color;

        ColorSwatchIcon(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y + 2, SIZE, SIZE);
            g.setColor(color.darker());
            g.drawRect(x, y + 2, SIZE, SIZE);
        }

        @Override
        public int getIconWidth() {
            return SIZE + 2;
        }

        @Override
        public int getIconHeight() {
            return SIZE + 4;
        }
    }

    /** Носитель drag-n-drop для {@link NetworkDeviceType} из палитры каталога. */
    private static final class DeviceTypeTransferable implements Transferable {
        private final NetworkDeviceType type;

        DeviceTypeTransferable(NetworkDeviceType type) {
            this.type = type;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{NetworkCanvasPanel.DEVICE_TYPE_FLAVOR};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return NetworkCanvasPanel.DEVICE_TYPE_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return type;
        }
    }
}
