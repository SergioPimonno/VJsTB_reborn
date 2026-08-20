package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.sync.LibrarySyncClient;
import com.vjstb.ledscheme.update.UpdateManager;
import com.vjstb.ledscheme.update.VersionManifest;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.nio.file.Path;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

/**
 * «Обновить версию» — список версий из {@link VersionManifest} (без автосравнения:
 * пользователь сам выбирает нужную, см. класс-джавадок VersionManifest), скачивание
 * и установка через {@link UpdateManager}. На Windows при запуске из jar — полная
 * автоматическая подмена файла и перезапуск; иначе скачанный файл просто
 * открывается для ручной установки (см. UpdateManager.supportsAutoApply).
 */
public class UpdateDialog extends JDialog {

    private final JComboBox<VersionManifest.Entry> versionCombo = new JComboBox<>();
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JLabel status = new JLabel(" ");
    private final JButton updateBtn = new JButton("Обновить");

    public static void show(Window owner, SettingsManager settings) {
        new UpdateDialog(owner, settings).setVisible(true);
    }

    private UpdateDialog(Window owner, SettingsManager settings) {
        super(owner, "Обновление версии", ModalityType.APPLICATION_MODAL);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JLabel currentLabel = new JLabel("Текущая версия: " + UpdateManager.currentVersion());
        currentLabel.setAlignmentX(LEFT_ALIGNMENT);
        content.add(currentLabel);
        content.add(Box.createVerticalStrut(10));

        JPanel comboRow = new JPanel(new BorderLayout(6, 0));
        comboRow.setAlignmentX(LEFT_ALIGNMENT);
        comboRow.add(new JLabel("Доступные версии:"), BorderLayout.WEST);
        versionCombo.setEnabled(false);
        comboRow.add(versionCombo, BorderLayout.CENTER);
        content.add(comboRow);
        content.add(Box.createVerticalStrut(10));

        status.setForeground(Palette.MUTED);
        status.setAlignmentX(LEFT_ALIGNMENT);
        status.setText("Загрузка списка версий…");
        content.add(status);

        progress.setAlignmentX(LEFT_ALIGNMENT);
        progress.setVisible(false);
        content.add(progress);
        content.add(Box.createVerticalStrut(10));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        updateBtn.setEnabled(false);
        updateBtn.addActionListener(e -> onUpdateClicked(owner));
        buttons.add(close);
        buttons.add(updateBtn);
        content.add(buttons);

        setContentPane(content);
        setSize(420, 220);
        setLocationRelativeTo(owner);

        loadVersions(settings);
    }

    private void loadVersions(SettingsManager settings) {
        new SwingWorker<List<VersionManifest.Entry>, Void>() {
            @Override
            protected List<VersionManifest.Entry> doInBackground() throws Exception {
                return UpdateManager.fetchAvailableVersions(LibrarySyncClient.resolveBaseUrl(settings));
            }

            @Override
            protected void done() {
                try {
                    List<VersionManifest.Entry> entries = get();
                    if (entries.isEmpty()) {
                        status.setText("Список версий пуст или недоступен.");
                        return;
                    }
                    for (VersionManifest.Entry e : entries) {
                        versionCombo.addItem(e);
                    }
                    versionCombo.setEnabled(true);
                    updateBtn.setEnabled(true);
                    status.setText("Выберите версию и нажмите «Обновить».");
                } catch (Exception ex) {
                    status.setText("Не удалось получить список версий: " + rootMessage(ex));
                }
            }
        }.execute();
    }

    private void onUpdateClicked(Window owner) {
        VersionManifest.Entry entry = (VersionManifest.Entry) versionCombo.getSelectedItem();
        if (entry == null) {
            return;
        }
        boolean autoApply = UpdateManager.supportsAutoApply();
        String question = autoApply
                ? "<html>Скачать версию " + entry.version() + " и перезапустить приложение автоматически?</html>"
                : "<html>Скачать версию " + entry.version() + "? Скачанный файл откроется для ручной установки"
                        + " (автоматический перезапуск поддерживается только на Windows при запуске из jar-файла).</html>";
        int confirm = JOptionPane.showConfirmDialog(this, question, "Подтверждение",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        updateBtn.setEnabled(false);
        versionCombo.setEnabled(false);
        progress.setVisible(true);
        progress.setIndeterminate(true);
        status.setText("Скачивание…");

        new SwingWorker<Path, Long>() {
            @Override
            protected Path doInBackground() throws Exception {
                return UpdateManager.download(entry.releaseTag(), pct -> {
                    if (pct >= 0) {
                        publish(pct);
                    }
                });
            }

            @Override
            protected void process(List<Long> chunks) {
                long last = chunks.get(chunks.size() - 1);
                progress.setIndeterminate(false);
                progress.setValue((int) last);
            }

            @Override
            protected void done() {
                try {
                    Path downloaded = get();
                    if (autoApply) {
                        UpdateManager.applyAndRestartWindows(downloaded);
                        JOptionPane.showMessageDialog(UpdateDialog.this,
                                "Приложение перезапустится с новой версией.", "Обновление",
                                JOptionPane.INFORMATION_MESSAGE);
                        System.exit(0);
                    } else {
                        UpdateManager.openDownloaded(downloaded);
                        status.setText("Скачано. Установите вручную из открывшегося файла.");
                        progress.setVisible(false);
                        updateBtn.setEnabled(true);
                        versionCombo.setEnabled(true);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(UpdateDialog.this,
                            "Не удалось обновиться: " + rootMessage(ex), "Ошибка", JOptionPane.ERROR_MESSAGE);
                    status.setText("Ошибка обновления.");
                    progress.setVisible(false);
                    updateBtn.setEnabled(true);
                    versionCombo.setEnabled(true);
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
