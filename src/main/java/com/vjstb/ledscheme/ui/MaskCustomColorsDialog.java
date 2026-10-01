package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Диалог «Свои цвета…» для маски экрана — запрос пользователя 2026-09-30: «своя пара цветов
 * для маски». Два образца (Цвет 1 / Цвет 2) и превью шахматки ровно так, как маска чередует
 * клетки кабинетов по чётности (см. {@link com.vjstb.ledscheme.model.Screen#maskColor(int)}).
 * Клик по образцу открывает ту же палитру, что и для линий схем
 * ({@link UiKit#showColorChooser}), но с ОТДЕЛЬНОЙ памятью «недавних» цветов
 * ({@link RecentColorsChooserPanel.Channel#MASKS}) — цвета масок не смешиваются с цветами
 * линий. Сам диалог ничего не пишет в модель: возвращает пару, а вызывающий код (таблица
 * гридов) применяет её через {@code AppModel#setMaskCustomColors} — одна запись отмены.
 */
public final class MaskCustomColorsDialog {

    private MaskCustomColorsDialog() {
    }

    /** Показывает модальный диалог; возвращает {@code {цвет1, цвет2}} или {@code null}, если
     *  пользователь отменил. */
    public static Color[] show(Component owner, String screenName, Color initialA, Color initialB,
                               SettingsManager settings) {
        Color[] colors = {initialA, initialB};
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(owner),
                "Свои цвета маски — " + screenName, JDialog.ModalityType.APPLICATION_MODAL);

        JPanel preview = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int cols = 6;
                int rows = 3;
                int cw = getWidth() / cols;
                int ch = getHeight() / rows;
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        g.setColor(colors[(r + c) % 2]);
                        g.fillRect(c * cw, r * ch, cw, ch);
                    }
                }
            }
        };
        preview.setPreferredSize(new Dimension(240, 90));
        preview.setBorder(BorderFactory.createLineBorder(Palette.BORDER));

        JPanel swatches = new JPanel(new GridLayout(2, 1, 0, 8));
        JButton[] buttons = new JButton[2];
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            JButton b = new JButton();
            buttons[i] = b;
            b.setPreferredSize(new Dimension(170, 28));
            b.addActionListener(e -> {
                Color picked = UiKit.showColorChooser(dlg, idx == 0 ? "Цвет 1" : "Цвет 2", colors[idx],
                        settings, RecentColorsChooserPanel.Channel.MASKS);
                if (picked != null) {
                    colors[idx] = new Color(picked.getRGB() & 0xFFFFFF);
                    refreshSwatch(b, idx, colors[idx]);
                    preview.repaint();
                }
            });
            refreshSwatch(b, i, colors[i]);
            swatches.add(b);
        }

        JPanel center = new JPanel(new BorderLayout(12, 0));
        center.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        center.add(swatches, BorderLayout.WEST);
        center.add(preview, BorderLayout.CENTER);

        Color[][] result = {null};
        JButton ok = new JButton("OK");
        ok.addActionListener(e -> {
            result[0] = new Color[]{colors[0], colors[1]};
            dlg.dispose();
        });
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> dlg.dispose());
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttonRow.add(cancel);
        buttonRow.add(ok);

        JLabel hint = new JLabel("Клетки кабинетов маски чередуют Цвет 1 и Цвет 2.");
        hint.setForeground(Palette.MUTED);
        hint.setBorder(BorderFactory.createEmptyBorder(8, 12, 0, 12));

        JPanel content = new JPanel(new BorderLayout());
        content.add(hint, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(buttonRow, BorderLayout.SOUTH);
        dlg.setContentPane(content);
        dlg.getRootPane().setDefaultButton(ok);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
        return result[0];
    }

    private static void refreshSwatch(JButton b, int idx, Color c) {
        b.setText((idx == 0 ? "Цвет 1  " : "Цвет 2  ") + hex(c));
        b.setBackground(c);
        b.setOpaque(true);
        b.setForeground(luminance(c) > 140 ? Color.BLACK : Color.WHITE);
    }

    /** {@code #RRGGBB} — для подписи образца и подсказок. */
    static String hex(Color c) {
        return String.format("#%06X", c.getRGB() & 0xFFFFFF);
    }

    private static double luminance(Color c) {
        return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
    }
}
