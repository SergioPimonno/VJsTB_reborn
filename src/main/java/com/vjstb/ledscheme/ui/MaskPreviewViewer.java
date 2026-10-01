package com.vjstb.ledscheme.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/**
 * Окно «Просмотр» одной маски из диалога предпросмотра экспорта — с прокруткой и выбором
 * разрешения превью ({@link MaskPreviewResolution}, как «Resolution» в After Effects).
 *
 * <p>Запрос 2026-09-30 (решение D7): диалог предпросмотра больше не держит полные картинки
 * масок (до 30000×30000 px), а только задания {@link MaskImage} и маленькие миниатюры;
 * рассмотреть маску крупно — здесь. «Авто» — маска вписана в окно и нарисована ровно в этом
 * размере; дробь — показ в 100% (1 px экрана = 1 px маски), а сама маска нарисована в доле
 * разрешения и растянута без сглаживания: видно, что именно теряется, как в AE. Рендер — в
 * фоне ({@link SwingWorker}), окно не замирает.
 */
public final class MaskPreviewViewer {

    private MaskPreviewViewer() {
    }

    public static void show(Component parent, String title, MaskImage image, MaskPreviewResolution initial) {
        Window owner = parent instanceof Window w ? w : parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
        JDialog dlg = new JDialog(owner, title, java.awt.Dialog.ModalityType.MODELESS);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        ImageView view = new ImageView(image);
        JScrollPane scroll = new JScrollPane(view);
        scroll.getVerticalScrollBar().setUnitIncrement(32);
        scroll.getHorizontalScrollBar().setUnitIncrement(32);
        scroll.setPreferredSize(new Dimension(900, 640));

        JComboBox<MaskPreviewResolution> preset = new JComboBox<>(MaskPreviewResolution.values());
        preset.setSelectedItem(initial != null ? initial : MaskPreviewResolution.AUTO);
        JLabel status = new JLabel(" ");
        status.setForeground(Palette.MUTED);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(new JLabel("Разрешение превью:"));
        top.add(preset);
        top.add(new JLabel(image.width() + "×" + image.height() + " px"));
        top.add(status);

        Runnable rerender = () -> {
            MaskPreviewResolution r = (MaskPreviewResolution) preset.getSelectedItem();
            Dimension vp = scroll.getViewport().getExtentSize();
            double fit = Math.min(Math.max(50, vp.width) / (double) image.width(),
                    Math.max(50, vp.height) / (double) image.height());
            MaskPreviewResolution.Choice choice = MaskPreviewResolution.choose(r, fit, image.width(), image.height());
            view.request(choice, r == MaskPreviewResolution.AUTO ? choice.scale() : 1.0, status);
        };
        preset.addActionListener(e -> rerender.run());
        // «Авто» зависит от размера окна — перерисовываем после окончания изменения размера.
        Timer resizeDebounce = new Timer(250, e -> {
            if (preset.getSelectedItem() == MaskPreviewResolution.AUTO) {
                rerender.run();
            }
        });
        resizeDebounce.setRepeats(false);
        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeDebounce.restart();
            }
        });

        JPanel content = new JPanel(new BorderLayout());
        content.add(top, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        dlg.setContentPane(content);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
        SwingUtilities.invokeLater(rerender);
    }

    /** Холст просмотра: показывает отрендеренную картинку в масштабе {@code displayScale}
     *  нативного размера (растяжение — без сглаживания, чтобы были видны пиксели дроби). */
    private static final class ImageView extends JComponent {
        private final MaskImage image;
        private BufferedImage rendered;
        /** 0 до первого рендера — иначе пустой холст в нативном размере (до 30000 px) успел бы
         *  выставить полосы прокрутки и сбить расчёт «Авто» по размеру окна. */
        private double displayScale = 0;
        private SwingWorker<BufferedImage, Void> pending;

        ImageView(MaskImage image) {
            this.image = image;
            setOpaque(true);
            setBackground(Palette.BG);
        }

        void request(MaskPreviewResolution.Choice choice, double display, JLabel status) {
            if (pending != null) {
                pending.cancel(false);
            }
            status.setText("Рендер…");
            SwingWorker<BufferedImage, Void> w = new SwingWorker<>() {
                @Override
                protected BufferedImage doInBackground() {
                    return image.render(choice.scale());
                }

                @Override
                protected void done() {
                    if (isCancelled() || pending != this) {
                        return;
                    }
                    try {
                        rendered = get();
                        displayScale = display;
                        String note = choice.note();
                        status.setText(note != null ? "⚠ " + note : " ");
                        revalidate();
                        repaint();
                    } catch (Exception | OutOfMemoryError ex) {
                        status.setText("Не удалось построить превью: " + ex.getMessage());
                    }
                }
            };
            pending = w;
            w.execute();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Math.max(1, (int) Math.ceil(image.width() * displayScale)),
                    Math.max(1, (int) Math.ceil(image.height() * displayScale)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            if (rendered == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            Dimension d = getPreferredSize();
            g2.drawImage(rendered, 0, 0, d.width, d.height, null);
            g2.dispose();
        }
    }
}
