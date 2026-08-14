package com.vjstb.ledscheme;

import com.formdev.flatlaf.FlatLaf;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.settings.UserProfile;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.LafStyle;
import com.vjstb.ledscheme.ui.MainFrame;
import com.vjstb.ledscheme.ui.OnboardingDialog;
import com.vjstb.ledscheme.ui.Palette;
import com.vjstb.ledscheme.ui.UpdateNoticeDialog;
import com.vjstb.ledscheme.update.UpdateManager;
import com.vjstb.ledscheme.update.VersionManifest;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

/** Точка входа настольного приложения. */
public class App {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Настройки читаются ДО выбора L&F — стиль оформления (Тёмная/Светлая/
            // Darcula/IntelliJ, см. ui.LafStyle) и шрифт персистентны по профилю,
            // больше не жёстко FlatDarkLaf при каждом запуске.
            SettingsManager settings = new SettingsManager(new SettingsStore());
            UserProfile activeProfile = settings.activeProfile();
            LafStyle style = LafStyle.byId(activeProfile.getLafStyle());
            if (activeProfile.getFontFamily() != null) {
                FlatLaf.setPreferredFontFamily(activeProfile.getFontFamily());
            }
            try {
                UIManager.setLookAndFeel(style.createLaf());
            } catch (Exception e) {
                // не критично — останется системная тема
            }
            Palette.applyTheme(style.isDark());
            try {
                WorkspaceStore store = new WorkspaceStore();
                AppModel model = new AppModel(store);
                Palette.applyProfile(settings.activeProfile());
                MainFrame frame = new MainFrame(model, settings);
                frame.setVisible(true);
                if (!settings.isOnboardingCompleted()) {
                    new OnboardingDialog(frame, model, settings).setVisible(true);
                }
                checkForUpdatesInBackground(frame, settings);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(null,
                        "Не удалось запустить приложение: " + ex.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });
    }

    /** Проверка обновлений при запуске (см. class-javadoc update.VersionManifest) —
     *  фоново, не блокирует запуск/работу с уже открытым окном; сетевая ошибка
     *  (сервер недоступен и т.п.) тихо игнорируется — это необязательное
     *  уведомление, а не критичная для работы функция. Показывает
     *  {@link UpdateNoticeDialog}, только если есть версия НОВЕЕ текущей (см.
     *  {@link VersionManifest#isNewer}) и пользователь ещё не закрывал уведомление
     *  именно про неё (см. SettingsManager.getDismissedUpdateVersion). */
    private static void checkForUpdatesInBackground(MainFrame frame, SettingsManager settings) {
        new SwingWorker<List<VersionManifest.Entry>, Void>() {
            @Override
            protected List<VersionManifest.Entry> doInBackground() throws Exception {
                return VersionManifest.fetchAvailable();
            }

            @Override
            protected void done() {
                try {
                    List<VersionManifest.Entry> available = get();
                    VersionManifest.Entry newest = null;
                    for (VersionManifest.Entry e : available) {
                        if (VersionManifest.isNewer(e.version(), UpdateManager.currentVersion())
                                && (newest == null || VersionManifest.isNewer(e.version(), newest.version()))) {
                            newest = e;
                        }
                    }
                    if (newest != null && !newest.version().equals(settings.getDismissedUpdateVersion())) {
                        UpdateNoticeDialog.show(frame, settings, newest);
                    }
                } catch (Exception ignored) {
                    // сервер недоступен/сеть — не мешаем работе приложения
                }
            }
        }.execute();
    }
}
