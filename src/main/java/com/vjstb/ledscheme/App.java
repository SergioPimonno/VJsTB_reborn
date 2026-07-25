package com.vjstb.ledscheme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.MainFrame;
import com.vjstb.ledscheme.ui.OnboardingDialog;
import com.vjstb.ledscheme.ui.Palette;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Точка входа настольного приложения. */
public class App {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } catch (Exception e) {
                // не критично — останется системная тема
            }
            try {
                WorkspaceStore store = new WorkspaceStore();
                AppModel model = new AppModel(store);
                SettingsManager settings = new SettingsManager(new SettingsStore());
                Palette.applyProfile(settings.activeProfile());
                MainFrame frame = new MainFrame(model, settings);
                frame.setVisible(true);
                if (!settings.isOnboardingCompleted()) {
                    new OnboardingDialog(frame, settings).setVisible(true);
                }
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(null,
                        "Не удалось запустить приложение: " + ex.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });
    }
}
