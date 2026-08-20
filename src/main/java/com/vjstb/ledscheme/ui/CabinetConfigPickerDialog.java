package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.sync.CabinetConfigClient;
import com.vjstb.ledscheme.sync.LibrarySyncClient;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * «Скачать конфиг приёмной карты…» — выбор типа кабинета (из библиотеки) + герцовки
 * + требуемой яркости, скачивание подходящего .rcfgx (см. {@code CabinetConfigController}
 * на сервере). Переиспользуемый диалог — вызывается и отдельным пунктом меню
 * ({@link #show}, тип кабинета выбирает сам пользователь), и сразу после экспорта
 * NovaLCT для контроллера ({@link #showForType}, тип уже известен из схемы).
 * Показывает только реально загруженные комбинации Hz/кд — без угадывающих
 * подстановок, если для типа ничего не загружено.
 */
public class CabinetConfigPickerDialog extends JDialog {

    private final CabinetConfigClient client;
    /** null, если тип кабинета зафиксирован извне (см. {@link #showForType}) —
     *  тогда комбобокса выбора нет вовсе, {@link #presetName} несёт имя напрямую. */
    private final JComboBox<String> typeCombo;
    private final String presetName;
    private final DefaultListModel<CabinetConfigClient.SummaryDto> listModel = new DefaultListModel<>();
    private final JList<CabinetConfigClient.SummaryDto> list = new JList<>(listModel);
    private final JLabel status = new JLabel(" ");

    public static void show(Window owner, AppModel model, SettingsManager settings) {
        new CabinetConfigPickerDialog(owner, model, settings, null).setVisible(true);
    }

    public static void showForType(Window owner, AppModel model, SettingsManager settings, CabinetType preset) {
        new CabinetConfigPickerDialog(owner, model, settings, preset).setVisible(true);
    }

    private CabinetConfigPickerDialog(Window owner, AppModel model, SettingsManager settings, CabinetType preset) {
        super(owner, "Скачать конфиг приёмной карты", ModalityType.APPLICATION_MODAL);
        this.client = new CabinetConfigClient(LibrarySyncClient.resolveBaseUrl(settings));
        this.presetName = preset != null ? preset.getName() : null;

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel top = new JPanel(new BorderLayout(6, 0));
        if (preset != null) {
            typeCombo = null;
            top.add(new JLabel("Тип кабинета: " + preset.getName()), BorderLayout.WEST);
        } else {
            String[] names = model.getCabinetTypes().stream().map(CabinetType::getName).distinct().sorted()
                    .toArray(String[]::new);
            typeCombo = new JComboBox<>(names);
            typeCombo.addActionListener(e -> loadListFor(currentCabinetTypeName()));
            top.add(new JLabel("Тип кабинета:"), BorderLayout.WEST);
            top.add(typeCombo, BorderLayout.CENTER);
        }
        content.add(top, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
                if (value instanceof CabinetConfigClient.SummaryDto d) {
                    setText(d.refreshHz() + " Гц, " + d.brightnessCd() + " кд — " + d.fileName());
                }
                return this;
            }
        });
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(420, 220));
        content.add(listScroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        status.setForeground(Palette.MUTED);
        bottom.add(status, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton download = new JButton("Скачать…");
        download.addActionListener(e -> downloadSelected());
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        buttons.add(close);
        buttons.add(download);
        bottom.add(buttons, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(500, 380);
        setLocationRelativeTo(owner);

        String initialType = currentCabinetTypeName();
        if (initialType != null) {
            loadListFor(initialType);
        } else {
            status.setText("В библиотеке нет ни одного типа кабинета.");
        }
    }

    private String currentCabinetTypeName() {
        if (presetName != null) {
            return presetName;
        }
        return typeCombo.getItemCount() > 0 ? (String) typeCombo.getSelectedItem() : null;
    }

    private void loadListFor(String cabinetTypeName) {
        if (cabinetTypeName == null) {
            return;
        }
        status.setText("Загрузка…");
        new SwingWorker<List<CabinetConfigClient.SummaryDto>, Void>() {
            @Override
            protected List<CabinetConfigClient.SummaryDto> doInBackground() throws Exception {
                return client.list(cabinetTypeName);
            }

            @Override
            protected void done() {
                try {
                    List<CabinetConfigClient.SummaryDto> items = get();
                    listModel.clear();
                    items.forEach(listModel::addElement);
                    status.setText(items.isEmpty()
                            ? "Для «" + cabinetTypeName + "» пока ничего не загружено." : " ");
                } catch (Exception ex) {
                    status.setText(rootMessage(ex));
                }
            }
        }.execute();
    }

    private void downloadSelected() {
        CabinetConfigClient.SummaryDto selected = list.getSelectedValue();
        if (selected == null) {
            status.setText("Выберите файл в списке.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Файлы приёмной карты (*.rcfgx)", "rcfgx"));
        chooser.setSelectedFile(new File(selected.fileName()));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase(Locale.ROOT).endsWith(".rcfgx")) {
            target = new File(target.getParentFile(), target.getName() + ".rcfgx");
        }
        File finalTarget = target;

        status.setText("Скачивание…");
        new SwingWorker<CabinetConfigClient.DownloadedFile, Void>() {
            @Override
            protected CabinetConfigClient.DownloadedFile doInBackground() throws Exception {
                return client.download(selected.id());
            }

            @Override
            protected void done() {
                try {
                    Files.write(finalTarget.toPath(), get().content());
                    status.setText("Сохранено: " + finalTarget.getName());
                } catch (Exception ex) {
                    status.setText(rootMessage(ex));
                }
            }
        }.execute();
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
