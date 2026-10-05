package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * Что печатать в экспорте таблицы экранов: легенда кабинетов, статистика сцены, фермы и легенда ферм
 * (запросы 2026-10-02 и 2026-10-05). Выбор запоминается в профиле. Один диалог на оба места вызова —
 * кнопку «Экспорт экранов» этапа «Настройка» и строку «Экраны» этапа «Вывод»; общий вывод берёт последний
 * сохранённый выбор без вопросов ({@link #fromProfile}).
 */
public record ScreensExportOptions(boolean legend, boolean stats, boolean trusses, boolean trussLegend) {

    /** Последний сохранённый выбор (по умолчанию — всё включено). */
    public static ScreensExportOptions fromProfile(SettingsManager settings) {
        var p = settings.activeProfile();
        return new ScreensExportOptions(p.isScreensExportLegend(), p.isScreensExportStats(),
                p.isScreensExportTrusses(), p.isScreensExportTrussLegend());
    }

    /** Показывает диалог выбора, сохраняет его в профиль и возвращает; {@code null} — отмена. */
    public static ScreensExportOptions ask(Component parent, SettingsManager settings) {
        ScreensExportOptions current = fromProfile(settings);
        JCheckBox legendBox = new JCheckBox("Легенда кабинетов", current.legend());
        JCheckBox statsBox = new JCheckBox("Статистика по сцене", current.stats());
        JCheckBox trussBox = new JCheckBox("Фермы (над экранами на подвесе)", current.trusses());
        JCheckBox trussLegendBox = new JCheckBox("Легенда ферм", current.trussLegend());
        JPanel options = new JPanel(new GridLayout(0, 1, 0, 4));
        options.add(new JLabel("Что напечатать в таблице экранов?"));
        options.add(legendBox);
        options.add(statsBox);
        options.add(trussBox);
        options.add(trussLegendBox);
        if (JOptionPane.showConfirmDialog(parent, options, "Экспорт таблицы экранов",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return null;
        }
        ScreensExportOptions chosen = new ScreensExportOptions(legendBox.isSelected(), statsBox.isSelected(),
                trussBox.isSelected(), trussLegendBox.isSelected());
        settings.setScreensExportOptions(chosen.legend(), chosen.stats(), chosen.trusses(), chosen.trussLegend());
        return chosen;
    }
}
