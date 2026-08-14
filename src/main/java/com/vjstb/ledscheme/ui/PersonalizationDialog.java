package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.UserProfile;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * Диалог персонализации: выбор/создание/удаление профиля настроек и палитра
 * цветов (фазы питания, цвет блока без фазы, акцент, цвета цепочек сигнала).
 * Изменения применяются и сохраняются сразу — отдельной кнопки «Сохранить» нет,
 * как и для остальных данных приложения (единая логика автосохранения).
 * Горячие клавиши и поведенческие переключатели — в отдельных окошках, см.
 * {@link HotkeysDialog} и {@link PreferencesDialog}: каждый раздел персонализации
 * открывается и настраивается независимо от остальных.
 */
public class PersonalizationDialog extends javax.swing.JDialog {

    private final SettingsManager settings;
    private final JComboBox<UserProfile> profileCombo = new JComboBox<>();
    private final List<JButton> signalSwatches = new ArrayList<>();
    private JButton phase1Swatch;
    private JButton phase2Swatch;
    private JButton phase3Swatch;
    private JButton phaseNoneSwatch;
    private JButton accentSwatch;

    public PersonalizationDialog(Window owner, SettingsManager settings) {
        super(owner, "Персонализация — цвета и профили", ModalityType.MODELESS);
        this.settings = settings;

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        content.add(buildProfileRow());
        content.add(Box.createVerticalStrut(10));
        content.add(buildStylePanel());
        content.add(Box.createVerticalStrut(10));
        content.add(buildColorsPanel());
        content.add(Box.createVerticalStrut(10));

        JButton reset = new JButton("Сбросить к встроенным цветам");
        reset.setAlignmentX(Component.LEFT_ALIGNMENT);
        reset.addActionListener(e -> {
            settings.updateActiveProfileColors(null, null, null, null, null, null);
            Palette.applyProfile(settings.activeProfile());
            refreshSwatches();
        });
        content.add(reset);

        content.add(Box.createVerticalStrut(10));
        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        closeRow.add(close);
        content.add(closeRow);

        setContentPane(content);
        refreshProfileCombo();
        refreshSwatches();
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildProfileRow() {
        JPanel row = new JPanel(new java.awt.BorderLayout(6, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        profileCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof UserProfile p) {
                    setText(p.getName());
                }
                return this;
            }
        });
        profileCombo.addActionListener(e -> {
            UserProfile sel = (UserProfile) profileCombo.getSelectedItem();
            if (sel != null && !sel.getId().equals(settings.activeProfile().getId())) {
                settings.setActiveProfile(sel.getId());
                Palette.applyProfile(settings.activeProfile());
                refreshSwatches();
            }
        });
        row.add(new JLabel("Профиль:"), java.awt.BorderLayout.WEST);
        row.add(profileCombo, java.awt.BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton addBtn = new JButton("Новый");
        addBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Название профиля:", "Новый профиль");
            if (name != null && !name.trim().isEmpty()) {
                settings.createProfile(name.trim());
                Palette.applyProfile(settings.activeProfile());
                refreshProfileCombo();
                refreshSwatches();
            }
        });
        JButton renameBtn = new JButton("Переименовать");
        renameBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Новое название профиля:",
                    settings.activeProfile().getName());
            if (name != null && !name.trim().isEmpty()) {
                settings.renameActiveProfile(name.trim());
                refreshProfileCombo();
            }
        });
        JButton delBtn = new JButton("Удалить");
        delBtn.addActionListener(e -> {
            if (settings.getSettings().getProfiles().size() <= 1) {
                JOptionPane.showMessageDialog(this, "Нельзя удалить последний профиль", "Профили",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (JOptionPane.showConfirmDialog(this, "Удалить профиль «" + settings.activeProfile().getName() + "»?",
                    "Подтверждение", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                settings.deleteProfile(settings.activeProfile().getId());
                Palette.applyProfile(settings.activeProfile());
                refreshProfileCombo();
                refreshSwatches();
            }
        });
        btns.add(addBtn);
        btns.add(renameBtn);
        btns.add(delBtn);
        row.add(btns, java.awt.BorderLayout.EAST);
        return row;
    }

    /** Стиль отрисовки (см. {@link LafStyle} — 4 варианта, все из уже подключённого
     *  {@code flatlaf}) + шрифт приложения ({@code FlatLaf.setPreferredFontFamily},
     *  живо обновляется через {@code FlatLaf.updateUI()} без перезапуска). Независимая
     *  от акцентных цветов ось персонализации — см. class-javadoc Palette. */
    private JPanel buildStylePanel() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        JComboBox<LafStyle> styleCombo = new JComboBox<>(LafStyle.values());
        styleCombo.setSelectedItem(LafStyle.byId(settings.activeProfile().getLafStyle()));
        styleCombo.addActionListener(e -> {
            LafStyle style = (LafStyle) styleCombo.getSelectedItem();
            if (style == null) {
                return;
            }
            try {
                javax.swing.UIManager.setLookAndFeel(style.createLaf());
            } catch (Exception ignored) {
                return;
            }
            Palette.applyTheme(style.isDark());
            for (Window w : Window.getWindows()) {
                javax.swing.SwingUtilities.updateComponentTreeUI(w);
                w.repaint();
            }
            settings.setLafStyle(style.getId());
        });
        JPanel styleRow = new JPanel(new java.awt.BorderLayout(8, 0));
        styleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        styleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        styleRow.add(new JLabel("Стиль оформления"), java.awt.BorderLayout.CENTER);
        styleRow.add(styleCombo, java.awt.BorderLayout.EAST);
        body.add(styleRow);

        String[] fonts = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        String[] fontOptions = new String[fonts.length + 1];
        fontOptions[0] = "По умолчанию";
        System.arraycopy(fonts, 0, fontOptions, 1, fonts.length);
        JComboBox<String> fontCombo = new JComboBox<>(fontOptions);
        String currentFont = settings.activeProfile().getFontFamily();
        fontCombo.setSelectedItem(currentFont != null ? currentFont : "По умолчанию");
        fontCombo.addActionListener(e -> {
            String sel = (String) fontCombo.getSelectedItem();
            String family = "По умолчанию".equals(sel) ? null : sel;
            com.formdev.flatlaf.FlatLaf.setPreferredFontFamily(family);
            com.formdev.flatlaf.FlatLaf.updateUI();
            settings.setFontFamily(family);
        });
        JPanel fontRow = new JPanel(new java.awt.BorderLayout(8, 0));
        fontRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        fontRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        fontRow.add(new JLabel("Шрифт приложения"), java.awt.BorderLayout.CENTER);
        fontRow.add(fontCombo, java.awt.BorderLayout.EAST);
        body.add(fontRow);

        return (JPanel) UiKit.section("Стиль оформления", body);
    }

    private JPanel buildColorsPanel() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        phase1Swatch = swatch();
        phase2Swatch = swatch();
        phase3Swatch = swatch();
        phaseNoneSwatch = swatch();
        accentSwatch = swatch();

        body.add(colorRow("Фаза L1 (питание)", phase1Swatch));
        body.add(colorRow("Фаза L2 (питание)", phase2Swatch));
        body.add(colorRow("Фаза L3 (питание)", phase3Swatch));
        body.add(colorRow("Кабинет без фазы (цвет блока)", phaseNoneSwatch));
        body.add(colorRow("Акцент (выделение, активные элементы)", accentSwatch));

        JPanel signalRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        for (int i = 0; i < Palette.defaultSignalColors().length; i++) {
            JButton sw = swatch();
            int idx = i;
            sw.addActionListener(e -> pickSignalColor(idx));
            signalSwatches.add(sw);
            signalRow.add(sw);
        }
        JPanel signalWrap = new JPanel(new java.awt.BorderLayout());
        signalWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        signalWrap.add(new JLabel("Цвета цепочек сигнала:"), java.awt.BorderLayout.NORTH);
        signalWrap.add(signalRow, java.awt.BorderLayout.CENTER);
        body.add(signalWrap);

        return (JPanel) UiKit.section("Цвета", body);
    }

    private JPanel colorRow(String title, JButton swatchBtn) {
        JPanel row = new JPanel(new java.awt.BorderLayout(8, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.add(new JLabel(title), java.awt.BorderLayout.CENTER);
        row.add(swatchBtn, java.awt.BorderLayout.EAST);
        return row;
    }

    private JButton swatch() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(36, 22));
        b.setBorder(BorderFactory.createLineBorder(Palette.BORDER, 1));
        return b;
    }

    private void pickSignalColor(int index) {
        Color current = Palette.SIGNAL[index % Palette.SIGNAL.length];
        Color picked = JColorChooser.showDialog(this, "Цвет цепочки сигнала №" + (index + 1), current);
        if (picked == null) {
            return;
        }
        List<Integer> cols = new ArrayList<>();
        for (Color c : Palette.SIGNAL) {
            cols.add(c.getRGB() & 0xFFFFFF);
        }
        while (cols.size() <= index) {
            cols.add(Palette.defaultSignalColors()[cols.size() % Palette.defaultSignalColors().length].getRGB() & 0xFFFFFF);
        }
        cols.set(index, picked.getRGB() & 0xFFFFFF);
        UserProfile p = settings.activeProfile();
        settings.updateActiveProfileColors(p.getPhase1Color(), p.getPhase2Color(), p.getPhase3Color(),
                p.getPhaseNoneColor(), p.getAccentColor(), cols);
        Palette.applyProfile(settings.activeProfile());
        refreshSwatches();
    }

    private void refreshProfileCombo() {
        DefaultComboBoxModel<UserProfile> m = new DefaultComboBoxModel<>();
        for (UserProfile p : settings.getSettings().getProfiles()) {
            m.addElement(p);
        }
        profileCombo.setModel(m);
        profileCombo.setSelectedItem(settings.activeProfile());
    }

    private void refreshSwatches() {
        phase1Swatch.setBackground(Palette.PHASE1);
        phase2Swatch.setBackground(Palette.PHASE2);
        phase3Swatch.setBackground(Palette.PHASE3);
        phaseNoneSwatch.setBackground(Palette.PHASE_NONE);
        accentSwatch.setBackground(Palette.ACCENT);
        for (int i = 0; i < signalSwatches.size(); i++) {
            signalSwatches.get(i).setBackground(Palette.signalColor(i));
        }

        phase1Swatch.removeActionListener(phase1Listener);
        phase1Listener = e -> applySingle(1);
        phase1Swatch.addActionListener(phase1Listener);

        phase2Swatch.removeActionListener(phase2Listener);
        phase2Listener = e -> applySingle(2);
        phase2Swatch.addActionListener(phase2Listener);

        phase3Swatch.removeActionListener(phase3Listener);
        phase3Listener = e -> applySingle(3);
        phase3Swatch.addActionListener(phase3Listener);

        phaseNoneSwatch.removeActionListener(phaseNoneListener);
        phaseNoneListener = e -> applySingle(0);
        phaseNoneSwatch.addActionListener(phaseNoneListener);

        accentSwatch.removeActionListener(accentListener);
        accentListener = e -> applyAccent();
        accentSwatch.addActionListener(accentListener);
    }

    private java.awt.event.ActionListener phase1Listener;
    private java.awt.event.ActionListener phase2Listener;
    private java.awt.event.ActionListener phase3Listener;
    private java.awt.event.ActionListener phaseNoneListener;
    private java.awt.event.ActionListener accentListener;

    private void applySingle(int phase) {
        Color current = Palette.phaseColor(phase);
        Color picked = JColorChooser.showDialog(this,
                phase == 0 ? "Цвет блока без фазы" : "Цвет фазы L" + phase, current);
        if (picked == null) {
            return;
        }
        UserProfile p = settings.activeProfile();
        Integer p1 = phase == 1 ? rgb(picked) : p.getPhase1Color();
        Integer p2 = phase == 2 ? rgb(picked) : p.getPhase2Color();
        Integer p3 = phase == 3 ? rgb(picked) : p.getPhase3Color();
        Integer pn = phase == 0 ? rgb(picked) : p.getPhaseNoneColor();
        settings.updateActiveProfileColors(p1, p2, p3, pn, p.getAccentColor(), p.getSignalColors());
        Palette.applyProfile(settings.activeProfile());
        refreshSwatches();
    }

    private void applyAccent() {
        Color picked = JColorChooser.showDialog(this, "Акцентный цвет", Palette.ACCENT);
        if (picked == null) {
            return;
        }
        UserProfile p = settings.activeProfile();
        settings.updateActiveProfileColors(p.getPhase1Color(), p.getPhase2Color(), p.getPhase3Color(),
                p.getPhaseNoneColor(), rgb(picked), p.getSignalColors());
        Palette.applyProfile(settings.activeProfile());
        refreshSwatches();
    }

    private static Integer rgb(Color c) {
        return c.getRGB() & 0xFFFFFF;
    }
}
