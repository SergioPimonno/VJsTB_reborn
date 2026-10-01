package com.vjstb.ledscheme.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.Window;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Окно прогресса долгого экспорта (этап «Вывод»). Баг-репорт: при «Сформировать
 * пакет документации» на 300 DPI окно просто замирало на десятки секунд, и было
 * непонятно — зависло приложение или кнопка не нажалась.
 * <p>Экспорт намеренно остаётся на EDT (он временно переключает текущую сцену модели,
 * а на неё подписан весь интерфейс — в фоновом потоке это гонки), поэтому окно не
 * может отрисоваться «само»: {@link #step} перерисовывает его синхронно через
 * {@code paintImmediately} после каждого файла.
 * <p>2026-09-30 (запрос «убрать ограничение 16k», решение D7): запись одной маски
 * 30000×8000 полосами — это уже не секунды, а минуты, и «замороженное» окно на всё это
 * время недопустимо. Для таких шагов — {@link #runInBackground}: тяжёлая часть идёт в
 * {@link SwingWorker}, а EDT продолжает разбирать события (вложенный цикл
 * {@link SecondaryLoop}) — окна перерисовываются, работает кнопка «Отмена». Чтобы
 * пользователь не правил модель, пока фон её читает, окно-владелец на это время
 * отключается ({@code setEnabled(false)}); вызывающий код остаётся последовательным.
 */
public final class ExportProgressDialog implements AutoCloseable {

    /** Тяжёлая часть шага, выполняемая в фоне; получает приёмник хода/отмены. */
    @FunctionalInterface
    public interface BackgroundTask<T> {
        T run(MaskImage.WriteProgress progress) throws Exception;
    }

    private final JDialog dialog;
    private final JProgressBar bar;
    private final JLabel label = new JLabel(" ");
    private final JButton cancelButton = new JButton("Отмена");
    private final Component owner;
    private int done;
    private volatile boolean cancelled;

    public ExportProgressDialog(Component owner, String title, int total) {
        this.owner = owner;
        Window w = owner instanceof Window ow ? ow : owner != null ? SwingUtilities.getWindowAncestor(owner) : null;
        dialog = new JDialog(w, title, java.awt.Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        bar = new JProgressBar(0, Math.max(1, total));
        bar.setStringPainted(true);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        content.add(label, BorderLayout.NORTH);
        content.add(bar, BorderLayout.CENTER);
        cancelButton.setVisible(false);
        cancelButton.addActionListener(e -> {
            cancelled = true;
            cancelButton.setEnabled(false);
            cancelButton.setText("Отменяется…");
        });
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.add(cancelButton);
        content.add(south, BorderLayout.SOUTH);
        content.setPreferredSize(new Dimension(460, 110));
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

    /** Пользователь нажал «Отмена» (кнопка видна только во время {@link #runInBackground}). */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Выполняет {@code task} в фоновом потоке, не замораживая интерфейс (см. class-javadoc), и
     * возвращает его результат; исключение задачи пробрасывается как есть, отмена —
     * {@link CancellationException}. Вызывать с EDT; вне EDT (тесты) задача выполняется сразу
     * в текущем потоке. Подпись шага дополняется процентом записанных строк.
     */
    public <T> T runInBackground(String what, BackgroundTask<T> task) throws Exception {
        if (cancelled) {
            throw new CancellationException("Экспорт отменён");
        }
        MaskImage.WriteProgress progress = new MaskImage.WriteProgress() {
            private int lastPercent = -1;

            @Override
            public void progress(int rowsDone, int totalRows) {
                int percent = totalRows > 0 ? (int) (100L * rowsDone / totalRows) : 100;
                if (percent != lastPercent) {
                    lastPercent = percent;
                    SwingUtilities.invokeLater(() -> label.setText(what + " — " + percent + "%"));
                }
            }

            @Override
            public boolean cancelled() {
                return cancelled;
            }
        };
        if (!SwingUtilities.isEventDispatchThread()) {
            return task.run(progress);
        }
        label.setText(what);
        cancelButton.setVisible(true);
        dialog.getContentPane().validate();
        Window ownerWindow = dialog.getOwner();
        boolean reenable = ownerWindow != null && ownerWindow.isEnabled();
        if (reenable) {
            ownerWindow.setEnabled(false);
        }
        SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.run(progress);
            }

            @Override
            protected void done() {
                loop.exit();
            }
        };
        try {
            worker.execute();
            loop.enter();
            try {
                return worker.get();
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof Exception e) {
                    throw e;
                }
                if (cause instanceof OutOfMemoryError oom) {
                    // Понятный текст вместо падения EDT: вызывающие показывают getMessage().
                    throw new java.io.IOException("Недостаточно памяти: " + what
                            + ". Закройте другие программы или уменьшите размер канваса.", oom);
                }
                if (cause instanceof Error err) {
                    throw err;
                }
                throw ex;
            }
        } finally {
            if (reenable) {
                ownerWindow.setEnabled(true);
            }
            cancelButton.setVisible(false);
        }
    }

    @Override
    public void close() {
        dialog.dispose();
        if (owner != null) {
            owner.setCursor(Cursor.getDefaultCursor());
        }
    }
}
