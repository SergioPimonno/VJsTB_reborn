package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.UserProfile;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

/**
 * «Параметры экспорта» этапа «Вывод»: формат схем (JPG/PNG/WebP/PDF), DPI и
 * качество сжатия. Раньше на самом этапе был только выпадающий DPI; по запросу
 * пользователя всё вынесено сюда. Сохраняется в профиль (переживает перезапуск) по
 * «Сохранить»; «Отмена» ничего не меняет.
 */
public final class ExportSettingsDialog extends JDialog {

    private static final Integer[] DPI_CHOICES = {72, 150, 200, 300, 600};

    private final SettingsManager settings;
    private final JComboBox<SchemeImageWriter.Format> formatCombo =
            new JComboBox<>(SchemeImageWriter.Format.values());
    private final JComboBox<Integer> dpiCombo = new JComboBox<>(DPI_CHOICES);
    private final JSlider qualitySlider = new JSlider(10, 100);
    private final JLabel qualityValue = new JLabel();
    private final JLabel formatNote = new JLabel();
    private boolean saved;

    private ExportSettingsDialog(Component owner, SettingsManager settings) {
        super(SwingUtilities.getWindowAncestor(owner), "Параметры экспорта", ModalityType.APPLICATION_MODAL);
        this.settings = settings;
        UserProfile p = settings.activeProfile();

        formatCombo.setSelectedItem(SchemeImageWriter.Format.fromId(p.getDocExportFormat()));
        dpiCombo.setEditable(true);
        dpiCombo.setSelectedItem(p.getDocExportDpi());
        dpiCombo.setToolTipText("Плотность пикселей схем — 72 (экран) … 300 (печать). Чем выше, тем крупнее"
                + " картинка и дольше экспорт. НЕ влияет на PNG-маски — их размер жёстко привязан к реальному"
                + " разрешению LED-панели.");
        qualitySlider.setValue(p.getDocExportQuality());
        qualitySlider.addChangeListener(e -> qualityValue.setText(qualitySlider.getValue() + "%"));
        qualityValue.setText(qualitySlider.getValue() + "%");
        formatCombo.addActionListener(e -> syncFormat());
        formatNote.setForeground(Palette.MUTED);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 0;
        form.add(new JLabel("Формат схем"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        form.add(formatCombo, c);

        c.gridy = 1;
        c.gridx = 1;
        form.add(formatNote, c);

        c.gridy = 2;
        c.gridx = 0;
        c.gridwidth = 1;
        c.weightx = 0;
        form.add(new JLabel("Качество, DPI"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        form.add(dpiCombo, c);

        c.gridy = 3;
        c.gridx = 0;
        c.gridwidth = 1;
        c.weightx = 0;
        form.add(new JLabel("Сжатие (JPG/WebP)"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(qualitySlider, c);
        c.gridx = 2;
        c.weightx = 0;
        form.add(qualityValue, c);

        c.gridy = 4;
        c.gridx = 0;
        c.gridwidth = 3;
        JLabel masksNote = new JLabel("Маски всегда сохраняются в PNG; сводка — TXT, спецификация — XLSX.");
        masksNote.setForeground(Palette.MUTED);
        form.add(masksNote, c);

        JButton ok = new JButton("Сохранить");
        ok.addActionListener(e -> save());
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(ok);
        buttons.add(cancel);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(ok);
        syncFormat();
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    private void syncFormat() {
        SchemeImageWriter.Format f = (SchemeImageWriter.Format) formatCombo.getSelectedItem();
        boolean lossy = f != null && f.lossy();
        qualitySlider.setEnabled(lossy);
        qualityValue.setEnabled(lossy);
        formatNote.setText(f == SchemeImageWriter.Format.WEBP
                ? "Схемы длиннее 16383 px будут уменьшены (ограничение формата)."
                : f == SchemeImageWriter.Format.PDF
                        ? "Одна страница на схему, размер страницы — по DPI."
                        : " ");
    }

    private void save() {
        int dpi;
        try {
            Object v = dpiCombo.getEditor().getItem();
            dpi = Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException ex) {
            dpi = -1;
        }
        if (dpi < 36 || dpi > 1200) {
            javax.swing.JOptionPane.showMessageDialog(this, "DPI — целое число от 36 до 1200.",
                    "Неверное значение", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        SchemeImageWriter.Format f = (SchemeImageWriter.Format) formatCombo.getSelectedItem();
        settings.setDocExportOptions(f.name(), dpi, qualitySlider.getValue());
        saved = true;
        dispose();
    }

    /** Показывает окно модально; true — пользователь сохранил изменения. */
    public static boolean show(Component owner, SettingsManager settings) {
        ExportSettingsDialog d = new ExportSettingsDialog(owner, settings);
        d.setVisible(true);
        return d.saved;
    }
}
