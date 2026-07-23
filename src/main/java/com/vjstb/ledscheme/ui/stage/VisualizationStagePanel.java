package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.ui.ContextBar;
import com.vjstb.ledscheme.ui.SceneCanvasPanel;
import com.vjstb.ledscheme.ui.SchemeRenderer;
import com.vjstb.ledscheme.ui.UiKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Этап «Визуализация»: раскладка экранов сцены по их координатам. Генерация масок
 * (аналог Pixel Grid) и экспорт пресетов (Resolume/Novastar/AfterEffects) —
 * запланированы отдельно, здесь только заглушка-напоминание.
 */
public class VisualizationStagePanel extends JPanel {

    private static final int SIDE_WIDTH = 300;

    private final AppModel model;
    private final SceneCanvasPanel sceneCanvas;

    public VisualizationStagePanel(AppModel model) {
        this.model = model;
        this.sceneCanvas = new SceneCanvasPanel(model);

        setLayout(new BorderLayout());
        add(new ContextBar(model, false), BorderLayout.NORTH);
        add(sceneCanvas, BorderLayout.CENTER);
        JPanel side = buildSide();
        add(side, BorderLayout.EAST);
        // Перенос строк в HTML-подсказке зависит от РЕАЛЬНОЙ ширины, которая на момент
        // конструктора ещё не установлена — пересчитываем предел высоты после первой
        // раскладки, иначе текст может обрезаться (заниженная оценка высоты).
        SwingUtilities.invokeLater(() -> {
            UiKit.recapHeight(presetsSection);
            side.revalidate();
        });

        model.addListener(() -> {
            sceneCanvas.revalidate();
            sceneCanvas.repaint();
            refreshOverlapWarning();
        });
        refreshOverlapWarning();
    }

    private javax.swing.JComponent presetsSection;
    private JLabel overlapWarning;
    private JButton fixOverlapBtn;

    private JPanel buildSide() {
        JPanel body = UiKit.vboxFixedWidth(SIDE_WIDTH);
        body.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        overlapWarning = new JLabel(
                "<html>⚠ Экраны сцены перекрываются. Проверьте позиции X/Y в Сетапе"
                        + " или расставьте автоматически (в ряд по нижнему краю).</html>");
        overlapWarning.setForeground(Color.RED);
        overlapWarning.setVisible(false);
        body.add(overlapWarning);
        body.add(UiKit.vgap(6));

        fixOverlapBtn = new JButton("Расставить без наложения");
        fixOverlapBtn.addActionListener(e -> {
            model.autoArrangeScreensInScene();
            sceneCanvas.revalidate();
            sceneCanvas.repaint();
            refreshOverlapWarning();
        });
        fixOverlapBtn.setVisible(false);
        body.add(fixOverlapBtn);
        body.add(UiKit.vgap(12));

        JButton export = new JButton("Экспорт раскладки в JPEG…");
        export.addActionListener(e -> exportLayout());
        body.add(export);

        body.add(UiKit.vgap(12));
        presetsSection = UiKit.dynamicSection("Пресеты", UiKit.muted(
                "<html>Экспорт пресетов и масок для Resolume, Novastar, After Effects<br>"
                        + "(генерация pixel-map / ID-масок по аналогии с Pixel Grid) —<br>"
                        + "планируется отдельным этапом.</html>"));
        UiKit.recapHeight(presetsSection); // первичная (возможно неточная) оценка, уточним в invokeLater
        body.add(presetsSection);
        body.add(javax.swing.Box.createVerticalGlue());

        return body;
    }

    private void refreshOverlapWarning() {
        if (overlapWarning == null) {
            return;
        }
        boolean overlap = model.currentSceneHasOverlap();
        overlapWarning.setVisible(overlap);
        fixOverlapBtn.setVisible(overlap);
    }

    private void exportLayout() {
        if (model.getCurrentScene() == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите сцену", "Нет сцены", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(model.getCurrentScene().getName() + "_раскладка.jpg"));
        fc.setFileFilter(new FileNameExtensionFilter("JPEG", "jpg", "jpeg"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            int w = Math.max(800, sceneCanvas.getWidth());
            int h = Math.max(600, sceneCanvas.getHeight());
            BufferedImage img = sceneCanvas.renderImage(w, h);
            File target = fc.getSelectedFile();
            if (!target.getName().toLowerCase().endsWith(".jpg") && !target.getName().toLowerCase().endsWith(".jpeg")) {
                target = new File(target.getParentFile(), target.getName() + ".jpg");
            }
            SchemeRenderer.writeJpeg(img, target);
            JOptionPane.showMessageDialog(this, "Сохранено: " + target.getAbsolutePath(), "Экспорт",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ошибка сохранения: " + ex.getMessage(), "Экспорт",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
