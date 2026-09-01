package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Network;
import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import com.vjstb.ledscheme.model.NetworkManagerPlan;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.NetworkDeviceLabels;
import com.vjstb.ledscheme.service.NetworkIpConflicts;
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
 * Столбцы: Сеть / Устройство / IP / Маска / Шлюз / Примечание — устройство
 * резолвится тем же {@link NetworkDeviceLabels#resolveLabel}, что и подпись
 * блока на канвасе (общий метод, не копия — см. его javadoc).
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
 */
public class NetworkAddressTableDialog extends JDialog {

    private record Row(String network, String device, String ip, String mask, String gateway, String note,
                        boolean conflict) {
    }

    private final AppModel model;
    private final RowsModel tableModel = new RowsModel();
    private final JTable table = new JTable(tableModel);
    private final JLabel statusLabel = UiKit.muted(" ");

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
        bottom.add(statusLabel, BorderLayout.WEST);
        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> setVisible(false));
        closeRow.add(close);
        bottom.add(closeRow, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        setPreferredSize(new Dimension(720, 360));
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
        List<Row> rows = buildRows();
        tableModel.setRows(rows);
        Scene scene = model.getCurrentScene();
        String suffix = scene == null ? " Сцена не выбрана." : "";
        statusLabel.setText(" Устройств: " + rows.size() + "." + suffix);
    }

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        Scene scene = model.getCurrentScene();
        NetworkManagerPlan plan = scene != null ? scene.getNetworkManagerPlan() : null;
        if (plan == null) {
            return rows;
        }
        for (Network network : plan.getNetworks()) {
            List<NetworkDevicePlacement> devices = network.getDevices();
            for (NetworkDevicePlacement p : devices) {
                boolean conflict = NetworkIpConflicts.hasConflict(devices, p);
                rows.add(new Row(network.getName(), NetworkDeviceLabels.resolveLabel(p, model),
                        p.getIpAddress(), p.getSubnetMask(), p.getGateway(), p.getNote(), conflict));
            }
        }
        return rows;
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
        private static final String[] COLUMNS = {"Сеть", "Устройство", "IP", "Маска", "Шлюз", "Примечание"};
        private List<Row> rows = List.of();

        void setRows(List<Row> rows) {
            this.rows = rows;
            fireTableDataChanged();
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
