package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.sync.LibrarySyncClient;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

/**
 * «Синхронизировать библиотеку…» — тянет дельту общей библиотеки с сервера (см.
 * {@link LibrarySyncClient}) и применяет её через {@link AppModel#applyLibrarySyncItems}.
 * Только чтение: анонимный запрос, без логина (это первый, минимальный шаг
 * синхронизации — отправка предложений будет отдельной фичей). Структура — по
 * образцу {@link UpdateDialog} (JDialog + SwingWorker, авто-запуск при открытии).
 */
public class LibrarySyncDialog extends JDialog {

    private final JLabel status = new JLabel("Синхронизация…");
    private final JProgressBar progress = new JProgressBar();

    public static void show(Window owner, AppModel model, SettingsManager settings) {
        new LibrarySyncDialog(owner, model, settings).setVisible(true);
    }

    private LibrarySyncDialog(Window owner, AppModel model, SettingsManager settings) {
        super(owner, "Синхронизация библиотеки", ModalityType.APPLICATION_MODAL);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        status.setAlignmentX(LEFT_ALIGNMENT);
        content.add(status);
        content.add(Box.createVerticalStrut(10));

        progress.setIndeterminate(true);
        progress.setAlignmentX(LEFT_ALIGNMENT);
        content.add(progress);
        content.add(Box.createVerticalStrut(10));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        buttons.add(close);
        content.add(buttons);

        setContentPane(content);
        setSize(380, 160);
        setLocationRelativeTo(owner);

        runSync(model, settings);
    }

    private void runSync(AppModel model, SettingsManager settings) {
        long since = settings.getSettings().getLibrarySyncGlobalSeq();
        new SwingWorker<LibrarySyncClient.ChangesResult, Void>() {
            @Override
            protected LibrarySyncClient.ChangesResult doInBackground() throws Exception {
                return new LibrarySyncClient(LibrarySyncClient.resolveBaseUrl(settings)).fetchChanges(since);
            }

            @Override
            protected void done() {
                progress.setIndeterminate(false);
                progress.setValue(100);
                try {
                    LibrarySyncClient.ChangesResult result = get();
                    AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(result.items());
                    if (result.latestGlobalSeq() > since) {
                        settings.setLibrarySyncGlobalSeq(result.latestGlobalSeq());
                    }
                    if (summary.added() == 0 && summary.updated() == 0 && summary.deleted() == 0) {
                        status.setText("Библиотека уже актуальна.");
                    } else {
                        String deletedPart = summary.deleted() > 0 ? ", удалено: " + summary.deleted() : "";
                        status.setText("Добавлено: " + summary.added() + ", обновлено: " + summary.updated()
                                + deletedPart + ".");
                    }
                } catch (Exception ex) {
                    status.setText("Не удалось синхронизировать: " + rootMessage(ex));
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
