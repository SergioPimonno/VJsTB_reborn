package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.service.NetworkScanService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Сканирование диапазона IP-адресов Сетевого менеджера (запрос пользователя,
 * после обсуждения доработок менеджера: "добавим возможность сканировать
 * диапазон IP для обнаружения устройств с неизвестными адресами") — ПКМ по
 * пустому месту канваса или отдельная кнопка в {@code NetworkManagerPanel}
 * открывает это окно, пользователь задаёт пару "От"/"До" (ЛЮБЫЕ два IPv4,
 * без требования совпадать в октетах — см. {@link NetworkScanService
 * #expandRange}; была короткая промежуточная версия с полем "IP + маска
 * подсети" вместо пары адресов, но пользователь предпочёл вернуть исходный
 * интерфейс: "диапазон IP выглядел лучше... убери ограничение на маску
 * /24" — просто сняли ограничение на совпадение первых трёх октетов у
 * простой пары "От"/"До", а не привязку к маскам вообще), список отвечающих
 * адресов пополняется ЖИВЬЁМ по мере готовности (см. {@link
 * NetworkScanService#scanRange}), для любого отвечающего адреса — кнопка
 * «Добавить как устройство», которая через {@link #onAddDevice} создаёт
 * новое размещение с этим IP в СЕЙЧАС ВЫБРАННОЙ сети {@code
 * NetworkManagerPanel} (сама эта панель ничего не знает про модель сети —
 * только передаёт готовый IP обратно вызывающей стороне, тот же контракт,
 * что уже был у {@code EquipmentPresetDialog.Result}/{@code
 * NetworkDeviceTypeDialog}).
 *
 * <p>Слишком широкий диапазон (см. {@link NetworkScanService
 * #MAX_SCAN_ADDRESSES}) отклоняется понятной ошибкой в {@link #statusLabel}
 * при попытке «Сканировать», а не тихо режется или зависает на часы.
 *
 * <p>Немодальный (как {@link NetworkPingDialog}) — можно продолжать работать
 * с канвасом, пока скан идёт в фоне. Закрытие окна ЛЮБЫМ способом
 * останавливает незавершённый скан ({@link WindowAdapter#windowClosing}),
 * чтобы процессы {@code ping} не оставались висеть в фоне — тот же приём,
 * что и у {@link NetworkPingDialog}.
 */
public class NetworkScanDialog extends JDialog {

    private final JTextField fromField = new JTextField("192.168.1.1", 14);
    private final JTextField toField = new JTextField("192.168.1.254", 14);
    private final JButton scanBtn = new JButton("Сканировать");
    private final JButton stopBtn = new JButton("Остановить");
    private final JButton addBtn = new JButton("Добавить как устройство");
    private final ResultsModel resultsModel = new ResultsModel();
    private final JTable resultsTable = new JTable(resultsModel);
    private final JLabel statusLabel = UiKit.muted(" ");
    private final Consumer<String> onAddDevice;

    private NetworkScanService.ScanHandle activeScan;
    private int scanTotal;
    private int scanDone;
    private int scanFound;

    public NetworkScanDialog(Window owner, Consumer<String> onAddDevice) {
        super(owner, "Сканирование IP-диапазона", ModalityType.MODELESS);
        this.onAddDevice = onAddDevice;

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ДВЕ строки, не одна (баг-репорт со скриншотом: "Сканировать"/"Остановить"
        // не помещались в одну FlowLayout-строку с полями ввода и молча пропадали
        // за краем окна — та же причина/фикс, что уже применяли для карточки
        // машины в VehicleLoadVisualizerDialog: узкая FlowLayout-строка, вписанная
        // в фиксированный preferredSize диалога, не переносит "лишние" компоненты
        // на видимую вторую строку сама по себе).
        JPanel rangeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        rangeRow.add(new JLabel("От:"));
        rangeRow.add(fromField);
        rangeRow.add(new JLabel("до:"));
        rangeRow.add(toField);
        JPanel scanButtonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        scanButtonsRow.add(scanBtn);
        scanButtonsRow.add(stopBtn);
        JPanel topRows = new JPanel();
        topRows.setLayout(new BoxLayout(topRows, BoxLayout.Y_AXIS));
        topRows.add(rangeRow);
        topRows.add(Box.createVerticalStrut(4));
        topRows.add(scanButtonsRow);
        content.add(topRows, BorderLayout.NORTH);

        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsTable.setRowHeight(22);
        resultsTable.setDefaultRenderer(Object.class, new StatusCellRenderer());
        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateAddButtonState();
            }
        });
        content.add(new JScrollPane(resultsTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        addRow.add(addBtn);
        bottom.add(addRow, BorderLayout.NORTH);
        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.add(statusLabel, BorderLayout.WEST);
        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        closeRow.add(close);
        statusRow.add(closeRow, BorderLayout.EAST);
        bottom.add(statusRow, BorderLayout.SOUTH);
        content.add(bottom, BorderLayout.SOUTH);

        scanBtn.addActionListener(e -> startScan());
        stopBtn.addActionListener(e -> stopScan());
        addBtn.addActionListener(e -> addSelectedAsDevice());
        stopBtn.setEnabled(false);
        updateAddButtonState();

        setContentPane(content);
        setPreferredSize(new Dimension(480, 420));
        pack();
        setLocationRelativeTo(owner);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopScan();
            }
        });
    }

    private void startScan() {
        List<String> ips;
        try {
            ips = NetworkScanService.expandRange(fromField.getText(), toField.getText());
        } catch (IllegalArgumentException ex) {
            statusLabel.setText(" " + ex.getMessage());
            return;
        }
        resultsModel.clear();
        scanTotal = ips.size();
        scanDone = 0;
        scanFound = 0;
        fromField.setEnabled(false);
        toField.setEnabled(false);
        scanBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        updateAddButtonState();
        updateStatus(false);
        activeScan = NetworkScanService.scanRange(ips, this::onScanResult, this::onScanDone);
    }

    private void stopScan() {
        if (activeScan != null) {
            activeScan.cancel();
            activeScan = null;
        }
    }

    private void onScanResult(NetworkScanService.ScanResult result) {
        resultsModel.addResult(result);
        scanDone++;
        if (result.reachable()) {
            scanFound++;
        }
        updateStatus(false);
    }

    private void onScanDone() {
        activeScan = null;
        fromField.setEnabled(true);
        toField.setEnabled(true);
        scanBtn.setEnabled(true);
        stopBtn.setEnabled(false);
        updateStatus(true);
    }

    private void updateStatus(boolean finished) {
        String prefix = finished ? " Скан завершён." : " Сканирование…";
        statusLabel.setText(prefix + " Проверено: " + scanDone + " из " + scanTotal + ", найдено: " + scanFound + ".");
    }

    private void updateAddButtonState() {
        int row = resultsTable.getSelectedRow();
        addBtn.setEnabled(row >= 0 && resultsModel.isReachable(row));
    }

    private void addSelectedAsDevice() {
        int row = resultsTable.getSelectedRow();
        if (row < 0 || !resultsModel.isReachable(row)) {
            return;
        }
        onAddDevice.accept(resultsModel.ipAt(row));
        JOptionPane.showMessageDialog(this, "Устройство с адресом " + resultsModel.ipAt(row)
                        + " добавлено в текущую сеть — задайте ему имя/тип в параметрах на канвасе.",
                "Добавлено", JOptionPane.INFORMATION_MESSAGE);
    }

    private static final class ResultsModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"IP", "Статус"};
        private final List<NetworkScanService.ScanResult> results = new ArrayList<>();

        void clear() {
            results.clear();
            fireTableDataChanged();
        }

        void addResult(NetworkScanService.ScanResult result) {
            results.add(result);
            int idx = results.size() - 1;
            fireTableRowsInserted(idx, idx);
        }

        boolean isReachable(int row) {
            return results.get(row).reachable();
        }

        String ipAt(int row) {
            return results.get(row).ip();
        }

        @Override
        public int getRowCount() {
            return results.size();
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
            NetworkScanService.ScanResult r = results.get(rowIndex);
            return columnIndex == 0 ? r.ip() : (r.reachable() ? "В сети" : "Нет ответа");
        }
    }

    /** Зелёный текст для отвечающих адресов, приглушённый — для не ответивших —
     *  чтобы результаты читались с одного взгляда даже при быстро растущем
     *  списке (до {@code NetworkScanService.MAX_SCAN_ADDRESSES} строк). */
    private final class StatusCellRenderer extends DefaultTableCellRenderer {
        private static final Color COLOR_UP = new Color(0x3fb950);

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                         boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                setForeground(resultsModel.isReachable(row) ? COLOR_UP : Palette.MUTED);
            }
            return c;
        }
    }
}
