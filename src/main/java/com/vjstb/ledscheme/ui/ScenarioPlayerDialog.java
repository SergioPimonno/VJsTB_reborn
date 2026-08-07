package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Scenario;
import com.vjstb.ledscheme.model.ScenarioStep;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/** Проигрыватель интерактивного сценария (см. {@link Scenario}) — картинка шага
 *  с подсвеченным (пульсирующим) хотспотом, клик по которому продвигает на
 *  следующий шаг; шаг без хотспота продвигается кнопкой «Далее». Короткий
 *  fade-in при смене картинки — не полноценный кросс-фейд между старой/новой
 *  картинкой (не нужно держать в памяти обе сразу), но ощущается как переход,
 *  не как резкая подмена кадра. */
public class ScenarioPlayerDialog extends JDialog {

    public static void show(Window owner, Scenario scenario) {
        new ScenarioPlayerDialog(owner, scenario).setVisible(true);
    }

    private final Scenario scenario;
    private int stepIndex;
    private final StepView stepView = new StepView();
    private final JLabel bodyLabel = new JLabel();
    private final JLabel stepCounter = new JLabel();
    private final JButton backBtn = new JButton("← Назад");
    private final JButton nextBtn = new JButton("Далее →");
    private String currentBodyHtml = "";
    private Runnable rewrapBody;

    public ScenarioPlayerDialog(Window owner, Scenario scenario) {
        super(owner, scenario.getTitle() == null || scenario.getTitle().isBlank()
                ? "Интерактивный пример" : scenario.getTitle(), ModalityType.MODELESS);
        this.scenario = scenario;

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        stepView.setPreferredSize(new Dimension(640, 380));
        content.add(stepView, BorderLayout.CENTER);

        bodyLabel.setVerticalAlignment(SwingConstants.TOP);
        JScrollPane bodyScroll = new JScrollPane(bodyLabel);
        bodyScroll.setBorder(BorderFactory.createEmptyBorder());
        bodyScroll.setPreferredSize(new Dimension(640, 90));
        content.add(bodyScroll, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);
        rewrapBody = UiKit.bindHtmlWrapWidth(bodyLabel, bodyScroll, () -> currentBodyHtml);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 12, 10, 12));
        stepCounter.setForeground(Palette.MUTED);
        bottom.add(stepCounter, BorderLayout.WEST);
        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        backBtn.addActionListener(e -> goTo(stepIndex - 1));
        nextBtn.addActionListener(e -> goTo(stepIndex + 1));
        JButton closeBtn = new JButton("Закрыть");
        closeBtn.addActionListener(e -> dispose());
        navButtons.add(backBtn);
        navButtons.add(nextBtn);
        navButtons.add(closeBtn);
        bottom.add(navButtons, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        stepView.setOnHotspotClicked(() -> goTo(stepIndex + 1));

        setSize(720, 620);
        setLocationRelativeTo(owner);
        goTo(0);
    }

    @Override
    public void dispose() {
        stepView.stopTimers();
        super.dispose();
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public boolean isNextEnabled() {
        return nextBtn.isEnabled();
    }

    public boolean isBackEnabled() {
        return backBtn.isEnabled();
    }

    public void goToNext() {
        goTo(stepIndex + 1);
    }

    public void goToPrevious() {
        goTo(stepIndex - 1);
    }

    /** Имитирует клик по картинке в ОТНОСИТЕЛЬНЫХ координатах (0..1) — тем же
     *  путём, что и настоящий клик мышью (реальный хит-тест хотспота в
     *  {@code StepView}, не обходной способ продвинуть шаг). */
    public void simulateImageClickAt(double relativeX, double relativeY) {
        stepView.simulateClickAt(relativeX, relativeY);
    }

    private void goTo(int index) {
        if (index < 0 || index >= scenario.getSteps().size()) {
            return;
        }
        stepIndex = index;
        ScenarioStep step = scenario.getSteps().get(index);
        stepView.setStep(step);
        currentBodyHtml = step.getBodyHtml() != null ? step.getBodyHtml() : "";
        rewrapBody.run();
        boolean isLast = index == scenario.getSteps().size() - 1;
        boolean mustClickHotspot = step.hasHotspot() && stepView.hasImage();
        backBtn.setEnabled(index > 0);
        nextBtn.setEnabled(!isLast && !mustClickHotspot);
        nextBtn.setToolTipText(mustClickHotspot
                ? "Кликните по подсвеченной области на картинке, чтобы перейти дальше" : null);
        String title = step.getTitle();
        stepCounter.setText("Шаг " + (index + 1) + " из " + scenario.getSteps().size()
                + (title != null && !title.isBlank() ? " — " + title : ""));
    }

    /** Картинка текущего шага + подсвеченный хотспот поверх неё (если есть), с
     *  пересчётом клика/хотспота между экранными пикселями и относительными
     *  координатами картинки (0..1) — независимо от масштаба показа letterbox. */
    private static final class StepView extends JPanel {
        private BufferedImage image;
        private ScenarioStep step;
        private Runnable onHotspotClicked = () -> { };
        private float pulseAlpha = 0.3f;
        private float fadeAlpha = 1f;
        private final Timer pulseTimer;
        private final Timer fadeTimer;

        StepView() {
            setOpaque(true);
            setBackground(Color.BLACK);
            pulseTimer = new Timer(40, e -> {
                double t = (System.currentTimeMillis() % 1200) / 1200.0;
                pulseAlpha = (float) (0.25 + 0.25 * Math.abs(Math.sin(t * Math.PI)));
                if (step != null && step.hasHotspot()) {
                    repaint();
                }
            });
            pulseTimer.start();
            fadeTimer = new Timer(15, e -> {
                fadeAlpha = Math.min(1f, fadeAlpha + 0.08f);
                repaint();
                if (fadeAlpha >= 1f) {
                    ((Timer) e.getSource()).stop();
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    handleClick(e.getX(), e.getY());
                }
            });
        }

        private void handleClick(int screenX, int screenY) {
            Rectangle2D.Double hotspot = hotspotScreenRect();
            if (hotspot != null && hotspot.contains(screenX, screenY)) {
                onHotspotClicked.run();
            }
        }

        /** Тестовый хук — те же координаты и та же проверка попадания в хотспот,
         *  что и реальный клик мышью (см. {@link #handleClick}), просто пересчитанные
         *  из относительных координат КАРТИНКИ, а не абсолютных экранных пикселей. */
        void simulateClickAt(double relativeX, double relativeY) {
            Rectangle drawRect = imageDrawRect();
            if (drawRect == null) {
                return;
            }
            handleClick(drawRect.x + (int) (relativeX * drawRect.width),
                    drawRect.y + (int) (relativeY * drawRect.height));
        }

        void stopTimers() {
            pulseTimer.stop();
            fadeTimer.stop();
        }

        void setOnHotspotClicked(Runnable r) {
            this.onHotspotClicked = r;
        }

        boolean hasImage() {
            return image != null;
        }

        void setStep(ScenarioStep step) {
            this.step = step;
            this.image = decode(step.getImageBase64());
            fadeAlpha = 0f;
            fadeTimer.restart();
            repaint();
        }

        private static BufferedImage decode(String base64) {
            if (base64 == null || base64.isBlank()) {
                return null;
            }
            try {
                return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
            } catch (Exception unreadableImage) {
                return null;
            }
        }

        /** Прямоугольник, в котором реально нарисована картинка внутри панели —
         *  вписана с сохранением пропорций (letterbox), не растянута на всю панель. */
        private Rectangle imageDrawRect() {
            if (image == null || getWidth() <= 0 || getHeight() <= 0) {
                return null;
            }
            double scale = Math.min((double) getWidth() / image.getWidth(), (double) getHeight() / image.getHeight());
            int w = (int) (image.getWidth() * scale);
            int h = (int) (image.getHeight() * scale);
            return new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
        }

        private Rectangle2D.Double hotspotScreenRect() {
            if (step == null || !step.hasHotspot()) {
                return null;
            }
            Rectangle drawRect = imageDrawRect();
            if (drawRect == null) {
                return null;
            }
            return new Rectangle2D.Double(
                    drawRect.x + step.getHotspotX() * drawRect.width,
                    drawRect.y + step.getHotspotY() * drawRect.height,
                    step.getHotspotWidth() * drawRect.width,
                    step.getHotspotHeight() * drawRect.height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Rectangle drawRect = imageDrawRect();
            if (image != null && drawRect != null) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAlpha));
                g2.drawImage(image, drawRect.x, drawRect.y, drawRect.width, drawRect.height, null);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            } else {
                g2.setColor(Palette.MUTED);
                g2.setFont(getFont().deriveFont(14f));
                g2.drawString("Нет картинки для этого шага", 16, 24);
            }
            Rectangle2D.Double hotspot = hotspotScreenRect();
            if (hotspot != null) {
                g2.setColor(new Color(255, 221, 0, (int) (pulseAlpha * 255)));
                g2.fill(hotspot);
                g2.setColor(new Color(255, 221, 0, 220));
                g2.setStroke(new BasicStroke(2.5f));
                g2.draw(hotspot);
            }
            g2.dispose();
        }
    }
}
