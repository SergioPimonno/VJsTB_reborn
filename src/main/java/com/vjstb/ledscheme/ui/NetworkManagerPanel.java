package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.Network;
import com.vjstb.ledscheme.model.NetworkDeviceType;
import com.vjstb.ledscheme.model.NetworkManagerPlan;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.NetworkScanService;
import com.vjstb.ledscheme.service.NetworkTopology;
import com.vjstb.ledscheme.service.novastar.NovastarPortStatusService;
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
 * и {@link NetworkCanvasPanel} — ОДИН общий канвас для ВСЕХ сетей сцены
 * одновременно (запрос пользователя: "разные сети должны быть в одном экране,
 * но визуально отличаться, типа как отдельные цветные подложки" — правит более
 * раннее решение "список слева + один активный канвас на выбранную сеть", см.
 * NETWORK_MANAGER_NOTES.md Round 7). Выбор сети в списке слева не переключает,
 * ЧТО показано на канвасе — там уже видно всё; выбор задаёт, КУДА попадёт
 * новое устройство ({@link #canvas}{@code .setPlan}), и какая сеть
 * рисуется как визуально "активная" подложка. Добавление — кнопкой «Добавить
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

    /** Период фонового опроса доступности (запрос пользователя: "фоновый
     *  автоопрос", см. {@link #pollAvailability}) — компромисс между
     *  свежестью данных и нагрузкой (короткий пинг на каждый адрес плана,
     *  параллельно, через {@link NetworkScanService#scanRange}). */
    private static final int AVAILABILITY_POLL_MS = 15_000;
    private final javax.swing.Timer availabilityTimer;
    /** Незавершённый предыдущий раунд опроса — новый тик таймера ЕГО
     *  отменяет, а не запускает параллельно ещё один (адресов может быть
     *  немного больше, чем укладывается в {@link #AVAILABILITY_POLL_MS}, на
     *  медленной/загруженной сети). */
    private NetworkScanService.ScanHandle activeAvailabilityScan;

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
                if (networkList.getSelectedValue() == null) {
                    // Все сети теперь на одном канвасе (см. NETWORK_MANAGER_NOTES.md Round 7) --
                    // без выбранной слева сети непонятно, в какую из них добавлять перетащенное.
                    JOptionPane.showMessageDialog(NetworkManagerPanel.this,
                            "Сначала выберите сеть слева — устройство добавляется в неё.",
                            "Нет выбранной сети", JOptionPane.WARNING_MESSAGE);
                    return false;
                }
                Point dropPoint = support.getDropLocation().getDropPoint();
                double[] c = canvas.pxToCanvas(dropPoint);
                try {
                    if (support.isDataFlavorSupported(NetworkCanvasPanel.SCHEMA_NODE_FLAVOR)) {
                        SchemaNode node = (SchemaNode) support.getTransferable()
                                .getTransferData(NetworkCanvasPanel.SCHEMA_NODE_FLAVOR);
                        canvas.addLinkedDeviceAt(node, c[0] - NetworkCanvasPanel.MIN_DEVICE_W / 2.0,
                                c[1] - NetworkCanvasPanel.MIN_DEVICE_H / 2.0);
                    } else {
                        NetworkDeviceType type = (NetworkDeviceType) support.getTransferable()
                                .getTransferData(NetworkCanvasPanel.DEVICE_TYPE_FLAVOR);
                        canvas.addCatalogDeviceAt(type, c[0] - NetworkCanvasPanel.MIN_DEVICE_W / 2.0,
                                c[1] - NetworkCanvasPanel.MIN_DEVICE_H / 2.0);
                    }
                    return true;
                } catch (UnsupportedFlavorException | IOException ex) {
                    return false;
                }
            }
        });

        model.addListener(this::refresh);
        refresh();

        availabilityTimer = new javax.swing.Timer(AVAILABILITY_POLL_MS, e -> pollAvailability());
        availabilityTimer.setInitialDelay(2000);
        availabilityTimer.start();
    }

    /** Один раунд фонового опроса доступности (Round 9, запрос пользователя:
     *  "фоновый автоопрос") — собирает ВСЕ непустые IP всех подключений плана
     *  (устройство с несколькими сетями даёт несколько адресов, дубликаты
     *  между сетями опрашиваются один раз через {@code Set}) и пингует их
     *  ОДНОКРАТНО и ПАРАЛЛЕЛЬНО тем же примитивом, что уже использует скан
     *  диапазона ({@link NetworkScanService#scanRange} — короткий пинг по
     *  коду возврата, без чтения вывода, без shell-обёртки). Результат
     *  целиком (не по одному адресу) отдаётся канвасу по завершении раунда
     *  ({@link NetworkCanvasPanel#setAvailability}) — не отражает
     *  дискретные "приход результата", чтобы линии на экране не перекрашивались
     *  по одной за кадром, а обновлялись разом. */
    private void pollAvailability() {
        if (currentPlan == null) {
            return;
        }
        Set<String> ips = new java.util.LinkedHashSet<>();
        for (NetworkDevicePlacement d : currentPlan.getDevices()) {
            for (com.vjstb.ledscheme.model.NetworkAttachment a : d.getAttachments()) {
                String ip = a.getIpAddress();
                if (ip != null && !ip.isBlank()) {
                    ips.add(ip.trim());
                }
            }
        }
        if (activeAvailabilityScan != null) {
            activeAvailabilityScan.cancel();
            activeAvailabilityScan = null;
        }
        if (ips.isEmpty()) {
            canvas.setAvailability(java.util.Map.of());
            return;
        }
        java.util.Map<String, Boolean> result = new java.util.HashMap<>();
        activeAvailabilityScan = NetworkScanService.scanRange(new ArrayList<>(ips),
                r -> result.put(r.ip(), r.reachable()),
                () -> canvas.setAvailability(new java.util.HashMap<>(result)));

        pollNovastarStatuses();
    }

    /** Опрос статуса видео-портов контроллеров с включённой галочкой (см. {@code
     *  NetworkDeviceParamsDialog#novastarStatusCheck}) — в ОТДЕЛЬНОМ фоновом
     *  потоке (не в пуле {@link NetworkScanService}, у того своя семантика
     *  ограниченного пула на короткие пинги; здесь на каждый порт КАЖДОГО
     *  такого контроллера — минимум два TCP round-trip'а, суммарно может
     *  занять заметное время — ни в коем случае не на EDT, тот же тик
     *  таймера иначе подвесил бы весь интерфейс приложения). Результат
     *  передаётся канвасу целиком через {@code SwingUtilities.invokeLater} —
     *  тот же принцип, что {@link #pollAvailability}. Экспериментально, см.
     *  {@code service.novastar.NovastarPacket} class-javadoc. */
    private void pollNovastarStatuses() {
        if (currentPlan == null) {
            canvas.setNovastarStatuses(java.util.Map.of());
            return;
        }
        java.util.Map<String, String> hostByDeviceId = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> portCountByDeviceId = new java.util.LinkedHashMap<>();
        for (NetworkDevicePlacement d : currentPlan.getDevices()) {
            if (!d.isNovastarStatusEnabled()) {
                continue;
            }
            String ip = firstNonBlankIp(d);
            ControllerType type = controllerTypeForDevice(d);
            if (ip == null || type == null) {
                continue;
            }
            hostByDeviceId.put(d.getId(), ip);
            portCountByDeviceId.put(d.getId(), type.effectivePortCount());
        }
        if (hostByDeviceId.isEmpty()) {
            canvas.setNovastarStatuses(java.util.Map.of());
            return;
        }
        new Thread(() -> {
            java.util.Map<String, java.util.Map<Integer, NovastarPortStatusService.PortStatus>> statuses =
                    new java.util.HashMap<>();
            for (var entry : hostByDeviceId.entrySet()) {
                int portCount = portCountByDeviceId.get(entry.getKey());
                statuses.put(entry.getKey(), NovastarPortStatusService.readAll(entry.getValue(), portCount, 800));
            }
            javax.swing.SwingUtilities.invokeLater(() -> canvas.setNovastarStatuses(statuses));
        }, "novastar-port-status-poll").start();
    }

    private String firstNonBlankIp(NetworkDevicePlacement device) {
        for (com.vjstb.ledscheme.model.NetworkAttachment a : device.getAttachments()) {
            if (a.getIpAddress() != null && !a.getIpAddress().isBlank()) {
                return a.getIpAddress().trim();
            }
        }
        return null;
    }

    /** Резолвит {@link ControllerType} КОНТРОЛЛЕРА, с которым связан {@code
     *  device} (через узел общей схемы → {@code ControllerInstance} →
     *  {@code ControllerType}) — источник числа ВИДЕО-портов для {@link
     *  NovastarPortStatusService#readAll}, независимый от {@code
     *  device.getEthernetPortCount()} (тот — порт управления, см. javadoc
     *  {@code NetworkDevicePlacement#isNovastarStatusEnabled}). {@code null},
     *  если устройство не связано с узлом схемы, узел не связан с
     *  контроллером, или тип контроллера не найден в библиотеке. */
    private ControllerType controllerTypeForDevice(NetworkDevicePlacement device) {
        Scene scene = model.getCurrentScene();
        if (scene == null || device.getLinkedSchemaNodeId() == null) {
            return null;
        }
        SchemaNode node = null;
        for (SchemaNode n : scene.getSchemaNodes()) {
            if (n.getId().equals(device.getLinkedSchemaNodeId())) {
                node = n;
                break;
            }
        }
        if (node == null || node.getControllerInstanceRefId() == null) {
            return null;
        }
        for (ControllerInstance ci : model.controllersInScene(scene)) {
            if (ci.getId().equals(node.getControllerInstanceRefId())) {
                return model.getWorkspace().controllerTypeById(ci.getControllerTypeId());
            }
        }
        return null;
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

        JButton adminLaptop = new JButton("+ Мой компьютер");
        adminLaptop.setToolTipText("Добавляет блок, представляющий эту машину (имя и текущий IP — живьём"
                + " с неё) — пинг и так всегда идёт отсюда, блок просто показывает это место в топологии.");
        adminLaptop.addActionListener(e -> addAdminLaptop());
        row.add(adminLaptop);

        JButton exportScheme = new JButton("Экспорт карты сети…");
        exportScheme.setToolTipText("Сохранить карту ВСЕХ сетей текущей сцены (как сейчас на канвасе) в JPEG —"
                + " папка спрашивается каждый раз, стартовая папка и качество берутся из настроек пакета"
                + " документации (этап «Вывод»).");
        exportScheme.addActionListener(e -> exportSchemeToJpeg());
        row.add(exportScheme);
        return row;
    }

    /** «Экспорт карты сети…» (одобрено пользователем) — та же схема, что
     *  {@code SignalStagePanel#exportCurrentScheme}/{@code
     *  PowerStagePanel}: свежий ОДНОРАЗОВЫЙ {@link NetworkCanvasPanel} (не
     *  {@link #canvas}, а именно новый экземпляр — тот же приём, что у
     *  {@code SchemaCanvasPanel}/{@code SceneCanvasPanel}, экспорт всегда в
     *  логическом масштабе 1:1, независимо от текущего интерактивного zoom),
     *  заполненный ТЕМ ЖЕ {@link #currentPlan} через {@link
     *  NetworkCanvasPanel#setPlan} (без "текущей" сети — на статическом
     *  экспорте нет смысла подсвечивать активную подложку ярче), без {@code
     *  setAvailability} (статус доступности — для живого вида, не для
     *  сохранённого снимка). */
    private void exportSchemeToJpeg() {
        if (currentPlan == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите сцену", "Нет сцены",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        Scene scene = model.getCurrentScene();
        String name = (scene != null ? scene.getName() : "Сцена") + " Сетевой менеджер";
        com.vjstb.ledscheme.ui.stage.CurrentSchemeExporter.export(this, model, settings, name, dpiScale -> {
            NetworkCanvasPanel export = new NetworkCanvasPanel(model, settings);
            export.setPlan(currentPlan, null);
            Dimension size = export.getPreferredSize();
            return export.renderImage(size.width, size.height, dpiScale);
        });
    }

    /** «+ Мой компьютер» — см. {@link NetworkCanvasPanel#addAdminLaptop}
     *  class-javadoc (Round 9). Один блок на план — повторный клик просто
     *  сообщает, что он уже есть, вместо того чтобы завести второй. */
    private void addAdminLaptop() {
        if (currentPlan == null) {
            return;
        }
        if (canvas.hasAdminLaptop()) {
            JOptionPane.showMessageDialog(this, "Блок «Мой компьютер» уже есть на поле.",
                    "Мой компьютер", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (networkList.getSelectedValue() == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите сеть слева — устройство добавляется в неё.",
                    "Нет выбранной сети", JOptionPane.WARNING_MESSAGE);
            return;
        }
        canvas.addAdminLaptop();
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
        JButton arrange = new JButton("Выровнять сеть");
        arrange.setToolTipText("Расставляет устройства ЭТОЙ сети аккуратной сеткой — другие сети на канвасе"
                + " не трогает.");
        arrange.addActionListener(e -> arrangeSelectedNetwork());
        JButton remove = new JButton("Удалить сеть");
        remove.addActionListener(e -> removeSelectedNetwork());
        buttons.add(add);
        buttons.add(rename);
        buttons.add(color);
        buttons.add(arrange);
        buttons.add(remove);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void arrangeSelectedNetwork() {
        Network selected = networkList.getSelectedValue();
        if (selected == null) {
            return;
        }
        canvas.autoArrangeNetwork(selected.getId());
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
        network.setColor(NetworkCanvasPanel.defaultColorForIndex(currentPlan.getNetworks().size()).getRGB());
        currentPlan.getNetworks().add(network);
        networkListModel.addElement(network);
        networkList.setSelectedValue(network, true);
        persistPlan();
    }

    /** Цвет подложки/линий сети в списке слева — тот же резолв (явный цвет ИЛИ
     *  золотой угол от индекса для старых сетей без сохранённого цвета), что
     *  канвас использует для своей подложки ({@code NetworkCanvasPanel
     *  #resolveNetworkColor}), чтобы значок в списке и подложка на канвасе
     *  всегда совпадали. */
    private Color networkColor(Network network) {
        if (network.getColor() != null) {
            return new Color(network.getColor());
        }
        int idx = currentPlan != null ? currentPlan.getNetworks().indexOf(network) : 0;
        return NetworkCanvasPanel.defaultColorForIndex(idx);
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
        canvas.repaint();
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
        canvas.repaint();
        persistPlan();
    }

    /** Удаляет сеть — если в ней есть устройства, спрашивает подтверждение (та
     *  же формулировка, что раньше). Round 8: устройство больше не "живёт"
     *  внутри сети физически, поэтому удаление сети — это СНЯТИЕ подключения
     *  ({@code NetworkAttachment}) у каждого её устройства (+ чистка связей,
     *  чья сеть — именно эта), а не удаление списка; устройство пропадает с
     *  канваса совсем, только если это подключение было у него ЕДИНСТВЕННЫМ
     *  (тот же принцип, что {@code NetworkCanvasPanel#detachFromNetwork}). */
    private void removeSelectedNetwork() {
        Network selected = networkList.getSelectedValue();
        if (selected == null || currentPlan == null) {
            return;
        }
        String networkId = selected.getId();
        List<NetworkDevicePlacement> devicesInNetwork = NetworkTopology.devicesInNetwork(currentPlan, networkId);
        if (!devicesInNetwork.isEmpty()) {
            int result = JOptionPane.showConfirmDialog(this,
                    "В сети «" + selected.getName() + "» есть устройства — удалить её вместе с ними?",
                    "Удалить сеть", JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        currentPlan.getLinks().removeAll(NetworkTopology.linksInNetwork(currentPlan, networkId));
        List<NetworkDevicePlacement> toFullyRemove = new ArrayList<>();
        for (NetworkDevicePlacement device : devicesInNetwork) {
            device.getAttachments().removeIf(a -> networkId.equals(a.getNetworkId()));
            if (device.getAttachments().isEmpty()) {
                toFullyRemove.add(device);
            }
        }
        currentPlan.getDevices().removeAll(toFullyRemove);
        for (NetworkDevicePlacement device : toFullyRemove) {
            currentPlan.getLinks().removeIf(l -> device.getId().equals(l.getFromDeviceId())
                    || device.getId().equals(l.getToDeviceId()));
        }

        currentPlan.getNetworks().remove(selected);
        networkListModel.removeElement(selected);
        persistPlan();
    }

    private void onNetworkSelected() {
        Network selected = networkList.getSelectedValue();
        canvas.setPlan(currentPlan, selected);
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
        statusLabel.setText(" Сетей: " + currentPlan.getNetworks().size()
                + ", устройств всего: " + currentPlan.getDevices().size());
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
        // Живой IP блока "Admin Laptop" (Round 9) -- персистим, только если он реально
        // изменился, иначе КАЖДОЕ изменение модели где угодно в приложении (этот метод
        // вызывается из model.addListener, см. class-javadoc) сохраняло бы план заново.
        if (canvas.refreshAdminLaptopAddresses()) {
            persistPlan();
        }
    }

    private void loadPlanForCurrentScene(Scene scene) {
        networkListModel.clear();
        if (scene == null) {
            currentPlan = null;
            canvas.setPlan(null, null);
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
            canvas.setPlan(currentPlan, null);
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
     *  сетевыми устройствами) и уже добавленных НА ПОЛЕ ГДЕ УГОДНО (Round 8, баг-
     *  репорт: "я добавил мктрлки в сеть админ, но в контенте в списке доступных
     *  они таже видны... один блок может добавляться в поле 1 раз, но может
     *  принадлежать разным сеткам" — раньше фильтр смотрел только на ТЕКУЩУЮ
     *  выбранную сеть, из-за чего один и тот же узел схемы можно было перетащить
     *  из палитры ПОВТОРНО в другую сеть и получить ВТОРОЙ физический блок для
     *  того же устройства; теперь узел исчезает из палитры после ПЕРВОГО
     *  добавления НЕЗАВИСИМО от сети — если устройство должно входить ещё в одну
     *  сеть, это ПКМ → «Подключить к сети…» на уже стоящем блоке, см. {@code
     *  NetworkCanvasPanel#attachToNetwork}, а не повторное перетаскивание). */
    private void refreshSchemaPalette() {
        SchemaNode selected = schemaPaletteList.getSelectedValue();
        schemaPaletteModel.clear();
        Set<String> usedAnywhere = currentPlan == null ? Set.of()
                : currentPlan.getDevices().stream()
                        .map(NetworkDevicePlacement::getLinkedSchemaNodeId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        for (SchemaNode node : model.schemaNodesForCurrentScene(SchemaMode.SIGNAL)) {
            if (node.getType() != SchemaNodeType.SCREEN && !usedAnywhere.contains(node.getId())) {
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
