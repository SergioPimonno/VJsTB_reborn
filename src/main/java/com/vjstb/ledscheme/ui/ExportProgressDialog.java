package com.vjstb.ledscheme.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

/**
 * Окно прогресса долгого экспорта (этап «Вывод»). Баг-репорт: при «Сформировать
 * пакет документации» на 300 DPI окно просто замирало на десятки секунд, и было
 * непонятно — зависло приложение или кнопка не нажалась.
 * <p>Экспорт намеренно остаётся на EDT (он временно переключает текущую сцену модели,
 * а на неё подписан весь интерфейс — в фоновом потоке это гонки), поэтому окно не
 * может отрисоваться «само»: {@link #step} перерисовывает его синхронно через
 * {@code paintImmediately} после каждого файла.
 */
public final class ExportProgressDialog implements AutoCloseable {

    private final JDialog dialog;
    private final JProgressBar bar;
    private final JLabel label = new JLabel(" ");
    private final Component owner;
    private int done;

    public ExportProgressDialog(Component owner, String title, int total) {
        this.owner = owner;
        Window w = owner != null ? SwingUtilities.getWindowAncestor(owner) : null;
        dialog = new JDialog(w, title, java.awt.Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        bar = new JProgressBar(0, Math.max(1, total));
        bar.setStringPainted(true);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        content.add(label, BorderLayout.NORTH);
        content.add(bar, BorderLayout.CENTER);
        content.setPreferredSize(new Dimension(460, 80));
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(w);
        dialog.setVisible(true);
        if (owner != null) {
            owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        step("Подготовка…", false);
    }

    /** Отмечает начало очередного шага (файла) и сразу перерисовывает окно. */
    public void step(String what) {
        step(what, true);
    }

    private void step(String what, boolean advance) {
        if (advance) {
            done++;
            // Итог считается заранее приблизительно — не даём полосе упереться/переполниться.
            if (done >= bar.getMaximum()) {
                bar.setMaximum(done + 1);
            }
        }
        bar.setValue(done);
        bar.setString(done + " / " + bar.getMaximum());
        label.setText(what);
        JComponent content = (JComponent) dialog.getContentPane();
        content.validate();
        content.paintImmediately(0, 0, content.getWidth(), content.getHeight());
    }

    @Override
    public void close() {
        dialog.dispose();
        if (owner != null) {
            owner.setCursor(Cursor.getDefaultCursor());
        }
    }
}
