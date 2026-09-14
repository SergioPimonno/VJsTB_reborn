package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Network;
import com.vjstb.ledscheme.model.NetworkAttachment;
import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import com.vjstb.ledscheme.model.NetworkLink;
import com.vjstb.ledscheme.model.NetworkManagerPlan;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.NetworkDeviceLabels;
import com.vjstb.ledscheme.service.NetworkIpConflicts;
import com.vjstb.ledscheme.service.NetworkTopology;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * «Таблица адресов» Сетевого менеджера — сводная таблица ВСЕХ устройств
 * ВСЕХ сетей текущей сцены разом (запрос пользователя, после обсуждения
 * доработок менеджера: "можно показывать таблицы адресов, но не экспортом
 * а отдельным окошечком" — сознательно НЕ файл (в отличие от, например,
 * {@code SpecXlsxWriter} для кабельной спецификации), а живое окно, которое
 * держат открытым рядом как шпаргалку во время монтажа/настройки сети).
 * Столбцы: Сеть / Устройство / IP / Маска / Шлюз / Связи / Примечание —
 * устройство резолвится тем же {@link NetworkDeviceLabels#resolveLabel}, что
 * и подпись блока на канвасе (общий метод, не копия — см. его javadoc).
 * «Связи» (одобрено пользователем: "подпись порта... в таблице адресов") —
 * компактный список "порт→сосед(подпись связи)" для связей ИМЕННО этой сети
 * этого устройства (см. {@link #buildRows}), та же информация, что тултип
 * порта на канвасе ({@code NetworkCanvasPanel#portTooltip}), просто списком.
 *
 * <p><b>Живая</b> — подписывается на {@code model.addListener} и перестраивает
 * содержимое на КАЖДОЕ изменение модели (та же, что и everywhere в этом
 * кодовой базе для немодальных окон, конвенция без {@code removeListener} —
 * см. {@code PreferencesDialog}/{@code HotkeysDialog}, у {@code AppModel}
 * такого метода нет вообще). Следует за текущей СЦЕНОЙ — если пользователь
 * переключит сцену, пока окно открыто, таблица сама покажет сети новой
 * сцены, без ручного обновления. {@code NetworkManagerPanel} держит ЕДИНЫЙ
 * экземпляр этого диалога (не создаёт новый на каждый клик кнопки) — именно
 * из-за отсутствия {@code removeListener}: повторные открытия иначе плодили
 * бы висящие листенеры без ограничения.
 *
 * <p><b>Подсветка конфликтов IP</b> (см. {@link NetworkIpConflicts}) — строка
 * целиком красным текстом, если в ПРЕДЕЛАХ ОДНОЙ СЕТИ у устройства совпадает
 * адрес с другим устройством той же сети (см. {@link #buildRows}, проверка
 * выполняется per-network — устройства из разных сетей не сравниваются
 * между собой, см. javadoc {@code NetworkIpConflicts}).
 *
 * <p><b>«Заполнить IP по порядку…»</b> (одобрено пользователем — печатная
 * удобность при массовой расстановке адресов на выделенных строках, НЕ
 * DHCP-подобная автоматика: адреса как были статическими, так и остаются,
 * пользователь явно выбирает начальный адрес и подтверждает) — работает по
 * ВЫДЕЛЕННЫМ строкам таблицы ({@code JTable} по умолчанию поддерживает
 * мультивыбор Ctrl/Shift+клик, отдельно настраивать не пришлось), инкрементит
 * ТОЛЬКО последний октет от введённого начального адреса — простая и самая
 * частая раскладка "подсеть.101, .102, .103..." для группы однотипных
 * устройств, найденных сканом/добавленных за один раз. */
public class NetworkAddressTableDialog extends JDialog {

    private record Row(NetworkAttachment attachment, String network, String device, String ip, String mask,
                        String gateway, String links, String note, boolean conflict) {
    }

    private final AppModel model;
    private final RowsModel tableModel = new RowsModel();
    private final JTable table = new JTable(tableModel);
    private final JLabel statusLabel = UiKit.muted(" ");
    /** Сцена/план, из которых построены ТЕКУЩИЕ строки — нужны, чтобы
     *  «Заполнить IP по порядку…» знал, что персистить после правки
     *  (мутирует {@link NetworkAttachment} прямо в объектах строк). */
    private Scene currentScene;
    private NetworkManagerPlan currentPlan;

    public NetworkAddressTableDialog(Window owner, AppModel model) {
        super(owner, "Таблица адресов", ModalityType.MODELESS);
        this.model = model;

        table.setDefaultRenderer(Object.class, new ConflictRowRenderer());
        table.setRowHeight(22);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton fillSequential = new JButton("Заполнить IP по порядку…");
        fillSequential.setToolTipText("Выделите 2 и более строк (Ctrl/Shift+клик) — заполнит их адреса подряд"
                + " от введённого начального, увеличивая только последний октет.");
        fillSequential.addActionListener(e -> fillSequentialIps());
        leftButtons.add(fillSequential);
        leftButtons.add(statusLabel);
        bottom.add(leftButtons, BorderLayout.WEST);
        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> setVisible(false));
        closeRow.add(close);
        bottom.add(closeRow, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        setPreferredSize(new Dimension(820, 360));
        pack();
        setLocationRelativeTo(owner);

        model.addListener(this::refresh);
        refresh();
    }

    /** Перестраивает содержимое из {@code model.getCurrentScene()} — вызывается
     *  из конструктора и на каждое изменение модели (см. class-javadoc). Не
     *  требует, чтобы окно было видимо — обновление происходит и в фоне, показ
     *  окна ({@link #showAndRefresh}) просто выводит уже актуальные данные на
     *  передний план. */
    private void refresh() {
        currentScene = model.getCurrentScene();
        currentPlan = currentScene != null ? currentScene.getNetworkManagerPlan() : null;
        List<Row> rows = buildRows();
        tableModel.setRows(rows);
        String suffix = currentScene == null ? " Сцена не выбрана." : "";
        statusLabel.setText(" Устройств: " + rows.size() + "." + suffix);
    }

    /** Round 8: одна строка — ОДНО подключение ({@link NetworkAttachment}), не
     *  одно устройство — устройство, состоящее в нескольких сетях, теперь
     *  занимает несколько строк (по одной на сеть), у каждой свой адрес. */
    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        if (currentPlan == null) {
            return rows;
        }
        for (NetworkDevicePlacement p : currentPlan.getDevices()) {
            String label = NetworkDeviceLabels.resolveLabel(p, model);
            for (NetworkAttachment a : p.getAttachments()) {
                Network network = NetworkTopology.networkById(currentPlan, a.getNetworkId());
                String networkName = network != null ? network.getName() : "?";
                boolean conflict = NetworkIpConflicts.hasConflict(currentPlan, p, a);
                String links = linksSummary(p, a.getNetworkId());
                rows.add(new Row(a, networkName, label, a.getIpAddress(), a.getSubnetMask(), a.getGateway(), links,
                        p.getNote(), conflict));
            }
        }
        return rows;
    }

    /** "порт→сосед(подпись)" для каждой связи ЭТОГО устройства В ЭТОЙ сети —
     *  см. class-javadoc, та же информация, что тултип порта на канвасе. */
    private String linksSummary(NetworkDevicePlacement device, String networkId) {
        StringBuilder sb = new StringBuilder();
        for (NetworkLink link : NetworkTopology.linksInNetwork(currentPlan, networkId)) {
            String otherId;
            int myPort;
            if (device.getId().equals(link.getFromDeviceId())) {
                otherId = link.getToDeviceId();
                myPort = link.getFromPort();
            } else if (device.getId().equals(link.getToDeviceId())) {
                otherId = link.getFromDeviceId();
                myPort = link.getToPort();
            } else {
                continue;
            }
            NetworkDevicePlacement other = NetworkTopology.deviceById(currentPlan, otherId);
            String target = other != null ? NetworkDeviceLabels.resolveLabel(other, model) : "?";
            String linkLabel = link.getLabel();
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(myPort).append("→").append(target);
            if (linkLabel != null && !linkLabel.isBlank()) {
                sb.append(" (").append(linkLabel).append(")");
            }
        }
        return sb.toString();
    }

    /** «Заполнить IP по порядку…» — см. class-javadoc. Простой инкремент
     *  последнего октета, БЕЗ полной 32-битной арифметики диапазона (в
     *  отличие от {@code NetworkScanService.expandRange}, тому нужно было
     *  честно пересекать границы /24 для скана — здесь типичный кейс проще
     *  и грубее: "накинуть подряд в пределах одной последней тройки цифр"). */
    private void fillSequentialIps() {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length < 2) {
            JOptionPane.showMessageDialog(this, "Выделите 2 и более строк (Ctrl/Shift+клик по строкам таблицы).",
                    "Заполнить IP по порядку", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (currentScene == null || currentPlan == null) {
            return;
        }
        String startIp = tableModel.rowAt(selectedRows[0]).ip();
        String input = JOptionPane.showInputDialog(this,
                "Начальный IP-адрес (дальше увеличивается только последний октет):", startIp);
        if (input == null || input.isBlank()) {
            return;
        }
        String[] parts = input.trim().split("\\.");
        int lastOctet;
        String prefix;
        try {
            if (parts.length != 4) {
                throw new NumberFormatException();
            }
            for (int i = 0; i < 3; i++) {
                int octet = Integer.parseInt(parts[i]);
                if (octet < 0 || octet > 255) {
                    throw new NumberFormatException();
                }
            }
            lastOctet = Integer.parseInt(parts[3]);
            if (lastOctet < 0 || lastOctet > 255) {
                throw new NumberFormatException();
            }
            prefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Некорректный IPv4-адрес: \"" + input + "\"", "Проверка данных",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (lastOctet + selectedRows.length - 1 > 255) {
            JOptionPane.showMessageDialog(this,
                    "Столько строк подряд от ." + lastOctet + " не помещается до .255 — сократите выделение"
                            + " или начните с меньшего адреса.",
                    "Проверка данных", JOptionPane.WARNING_MESSAGE);
            return;
        }
        for (int i = 0; i < selectedRows.length; i++) {
            tableModel.rowAt(selectedRows[i]).attachment().setIpAddress(prefix + (lastOctet + i));
        }
        model.saveNetworkManagerPlan(currentScene, currentPlan);
    }

    /** Выводит окно на передний план (создаётся один раз в {@code
     *  NetworkManagerPanel}, дальше только показывается/поднимается) и сразу
     *  освежает содержимое — на случай, если модель менялась, пока окно было
     *  скрыто {@code setVisible(false)} (обычный листенер и так подхватил бы
     *  это без явного вызова, но не помешает подстраховаться на момент
     *  показа). */
    public void showAndRefresh() {
        refresh();
        setVisible(true);
        toFront();
    }

    private static final class RowsModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Сеть", "Устройство", "IP", "Маска", "Шлюз", "Связи", "Примечание"};
        private List<Row> rows = List.of();

        void setRows(List<Row> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        Row rowAt(int rowIndex) {
            return rows.get(rowIndex);
        }

        boolean isConflict(int rowIndex) {
            return rows.get(rowIndex).conflict();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Row row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.network();
                case 1 -> row.device();
                case 2 -> row.ip();
                case 3 -> row.mask();
                case 4 -> row.gateway();
                case 5 -> row.links();
                default -> row.note();
            };
        }
    }

    /** Красит строку целиком (текст) в предупреждающий красный, если {@link
     *  RowsModel#isConflict} — та же семантика, что подсветка блока на
     *  канвасе (см. {@code NetworkCanvasPanel#COLOR_IP_CONFLICT}), независимая
     *  копия цвета: этот диалог не зависит от {@code NetworkCanvasPanel}. */
    private final class ConflictRowRenderer extends DefaultTableCellRenderer {
        private static final java.awt.Color CONFLICT_COLOR = new java.awt.Color(0xff5c5c);

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                         boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
            boolean conflict = tableModel.isConflict(row);
            setForeground(conflict ? CONFLICT_COLOR : (isSelected ? t.getSelectionForeground() : t.getForeground()));
            return c;
        }
    }
}
