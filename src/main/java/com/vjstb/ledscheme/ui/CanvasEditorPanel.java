package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CanvasPlacement;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.ScreenStats;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * Холст канваса — виртуального выходного кадра (как Advanced Output в Resolume):
 * прямоугольник размером с сам канвас (масштабируется под окно), внутри — размещённые
 * экраны сцены на своих пиксельных позициях, перетаскиваются мышью. Shift во время
 * перетаскивания — прилипание к краям уже размещённых экранов и краям канваса
 * (и, опционально, к центру канваса — см. «Персонализация»).
 */
public class CanvasEditorPanel extends JPanel {

    private static final int PADDING = 24;

    private final AppModel model;
    private final SettingsManager settings;
    private ContentCanvas canvas;
    private Runnable onChanged = () -> { };

    private CanvasPlacement dragging;
    private double dragOffXpx, dragOffYpx;
    private CanvasPlacement selected;
    /** Экранные px направляющей (см. snap()/drawSnapGuides) — null, если сейчас
     *  прилипания по этой оси нет (свободное перетаскивание или цель не найдена). */
    private Integer snapGuideScreenX;
    private Integer snapGuideScreenY;

    /** Кэш отрендеренных масок по id РАЗМЕЩЕНИЯ (не экрана — у каждого размещения
     *  своя настройка маски, см. CanvasPlacement) — тот же PixelGridRenderer.renderMask,
     *  что и настоящий экспорт (превью не «похоже», а РОВНО то же изображение,
     *  просто уменьшенное), но пересчитывать его на каждый repaint() было бы
     *  расточительно во время перетаскивания размещения мышью (repaint() при
     *  drag не проходит через setCanvas — см. ниже, поэтому кэш переживает
     *  drag не инвалидируясь, и пересчитывается только когда снаружи реально
     *  могло что-то измениться). */
    private final Map<String, BufferedImage> maskCache = new HashMap<>();

    public CanvasEditorPanel(AppModel model, SettingsManager settings) {
        this.model = model;
        this.settings = settings;
        setBackground(Palette.BG);
        setFocusable(true);
        // Лого маски — настройка профиля пользователя (см. UserProfile.maskLogoImagePath),
        // не самого канваса/модели — без этого смена лого в «Предпочтениях» не была бы
        // видна на уже открытом холсте, пока не произойдёт какое-то ДРУГОЕ изменение
        // модели (случайно сбрасывающее maskCache целиком, см. setCanvas()).
        settings.addListener(() -> {
            maskCache.clear();
            repaint();
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (canvas == null) {
                    return;
                }
                CanvasPlacement hit = placementAt(e.getPoint());
                selected = hit;
                if (hit != null) {
                    double scale = scale();
                    Screen scr = screenById(hit.getScreenId());
                    double px = PADDING + hit.getX() * scale;
                    double py = PADDING + hit.getY() * scale;
                    dragOffXpx = e.getX() - px;
                    dragOffYpx = e.getY() - py;
                    dragging = hit;
                }
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging == null || canvas == null) {
                    return;
                }
                double scale = scale();
                int nx = (int) Math.max(0, Math.round((e.getX() - dragOffXpx - PADDING) / scale));
                int ny = (int) Math.max(0, Math.round((e.getY() - dragOffYpx - PADDING) / scale));
                if (e.isShiftDown()) {
                    Screen scr = screenById(dragging.getScreenId());
                    if (scr != null) {
                        ScreenStats st = ScreenLogic.stats(scr, model.typeOf(scr), model.getWorkspace());
                        int[] snapped = snap(nx, ny, st.resolutionWidthPx(), st.resolutionHeightPx(), scale);
                        nx = snapped[0];
                        ny = snapped[1];
                    }
                } else {
                    snapGuideScreenX = null;
                    snapGuideScreenY = null;
                }
                dragging.setX(nx);
                dragging.setY(ny);
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                snapGuideScreenX = null;
                snapGuideScreenY = null;
                if (dragging != null) {
                    model.movePlacement(dragging, dragging.getX(), dragging.getY());
                    dragging = null;
                    onChanged.run();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** Обновляет отображаемый канвас. ВАЖНО: раньше безусловно сбрасывал selected —
     *  это ломало выделение при КАЖДОМ вызове, включая refreshCanvasSide() после
     *  mouseReleased (клик/перетаскивание экрана вызывает onChanged → setCanvas(тот
     *  же канвас) → выделение мгновенно терялось, поэтому кнопка «Убрать выбранный
     *  экран из канваса» не работала). Сбрасываем selected только при смене самого
     *  канваса или если выбранное размещение реально исчезло (удалено). */
    public void setCanvas(ContentCanvas canvas) {
        if (this.canvas != canvas) {
            this.selected = null;
        } else if (selected != null && (canvas == null || !canvas.getPlacements().contains(selected))) {
            this.selected = null;
        }
        this.canvas = canvas;
        // setCanvas() вызывается снаружи при КАЖДОМ изменении модели (см.
        // VisualizationStagePanel.refreshCanvasSide — общий слушатель модели), в
        // отличие от repaint() внутри mouseDragged (там снаружи ничего не менялось,
        // кроме позиции самого перетаскиваемого размещения) — поэтому кэш здесь
        // безопасно сбросить целиком: неважно, что именно изменилось (цвет маски,
        // состав кабинетов, что угодно), картинка должна быть пересчитана заново.
        maskCache.clear();
        repaint();
    }

    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged != null ? onChanged : () -> { };
    }

    /** Прилипание (nx,ny) — верхнего левого угла перетаскиваемого размещения (w×h px
     *  канваса) — к ближайшим краям других размещений и краям канваса, и опционально
     *  к центру канваса (настройка профиля). Порог и сила — из «Предпочтений»
     *  (единые для всех канвасов с прилипанием), порог задан в экранных пикселях
     *  редактора и переводится в px канваса через текущий scale. Побочный эффект —
     *  выставляет snapGuideScreenX/Y (в экранных px, для drawSnapGuides). */
    private int[] snap(int nx, int ny, int w, int h, double scale) {
        snapGuideScreenX = null;
        snapGuideScreenY = null;
        double thresholdCanvasPx = settings.activeProfile().getSnapThresholdPx() / scale;
        List<Integer> xTargets = new ArrayList<>();
        List<Integer> yTargets = new ArrayList<>();
        xTargets.add(0);
        xTargets.add(canvas.getWidthPx() - w);
        yTargets.add(0);
        yTargets.add(canvas.getHeightPx() - h);
        if (settings.activeProfile().isCanvasSnapToCenter()) {
            xTargets.add((canvas.getWidthPx() - w) / 2);
            yTargets.add((canvas.getHeightPx() - h) / 2);
        }
        for (CanvasPlacement pl : canvas.getPlacements()) {
            if (pl == dragging) {
                continue;
            }
            Screen other = screenById(pl.getScreenId());
            if (other == null) {
                continue;
            }
            ScreenStats ost = ScreenLogic.stats(other, model.typeOf(other), model.getWorkspace());
            int ow = ost.resolutionWidthPx();
            int oh = ost.resolutionHeightPx();
            xTargets.add(pl.getX());
            xTargets.add(pl.getX() + ow);
            xTargets.add(pl.getX() + ow - w);
            xTargets.add(pl.getX() - w);
            yTargets.add(pl.getY());
            yTargets.add(pl.getY() + oh);
            yTargets.add(pl.getY() + oh - h);
            yTargets.add(pl.getY() - h);
        }
        int strength = settings.activeProfile().getSnapStrengthPercent();
        int snappedX = closest(nx, xTargets, thresholdCanvasPx, strength, true, scale);
        int snappedY = closest(ny, yTargets, thresholdCanvasPx, strength, false, scale);
        return new int[]{snappedX, snappedY};
    }

    private int closest(int value, List<Integer> targets, double threshold, int strengthPercent,
                         boolean isXAxis, double scale) {
        int best = value;
        double bestDist = threshold;
        Integer bestTarget = null;
        for (int t : targets) {
            double d = Math.abs(t - value);
            if (d < bestDist) {
                bestDist = d;
                bestTarget = t;
            }
        }
        if (bestTarget != null) {
            best = (int) Math.round(SnapMath.blend(value, bestTarget, strengthPercent));
            int guideScreenPx = (int) Math.round(PADDING + bestTarget * scale);
            if (isXAxis) {
                snapGuideScreenX = guideScreenPx;
            } else {
                snapGuideScreenY = guideScreenPx;
            }
        }
        return best;
    }

    private Screen screenById(String id) {
        Scene scene = model.getCurrentScene();
        if (scene == null) {
            return null;
        }
        for (Screen s : scene.getScreens()) {
            if (s.getId().equals(id)) {
                return s;
            }
        }
        return null;
    }

    private double scale() {
        if (canvas == null) {
            return 1;
        }
        double availW = Math.max(50, getWidth() - PADDING * 2);
        double availH = Math.max(50, getHeight() - PADDING * 2);
        return Math.max(0.01, Math.min(availW / canvas.getWidthPx(), availH / canvas.getHeightPx()));
    }

    private CanvasPlacement placementAt(Point p) {
        if (canvas == null) {
            return null;
        }
        double scale = scale();
        for (int i = canvas.getPlacements().size() - 1; i >= 0; i--) {
            CanvasPlacement pl = canvas.getPlacements().get(i);
            Screen scr = screenById(pl.getScreenId());
            if (scr == null) {
                continue;
            }
            ScreenStats st = ScreenLogic.stats(scr, model.typeOf(scr), model.getWorkspace());
            int x = (int) (PADDING + pl.getX() * scale);
            int y = (int) (PADDING + pl.getY() * scale);
            int w = (int) Math.max(2, st.resolutionWidthPx() * scale);
            int h = (int) Math.max(2, st.resolutionHeightPx() * scale);
            if (p.x >= x && p.x <= x + w && p.y >= y && p.y <= y + h) {
                return pl;
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (canvas == null) {
            g2.setColor(Palette.MUTED);
            g2.setFont(getFont().deriveFont(14f));
            String msg = "Выберите или создайте канвас справа.";
            g2.drawString(msg, PADDING, PADDING + 20);
            g2.dispose();
            return;
        }

        double scale = scale();
        int cw = (int) (canvas.getWidthPx() * scale);
        int ch = (int) (canvas.getHeightPx() * scale);

        g2.setColor(new Color(0, 0, 0, 255));
        g2.fillRect(PADDING, PADDING, cw, ch);
        g2.setColor(Palette.ACCENT);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRect(PADDING, PADDING, cw, ch);

        Font labelFont = getFont().deriveFont(Font.BOLD, 12f);
        Font metaFont = getFont().deriveFont(10f);
        for (CanvasPlacement pl : canvas.getPlacements()) {
            Screen scr = screenById(pl.getScreenId());
            if (scr == null) {
                continue;
            }
            CabinetType type = model.typeOf(scr);
            ScreenStats st = ScreenLogic.stats(scr, type, model.getWorkspace());
            int x = (int) (PADDING + pl.getX() * scale);
            int y = (int) (PADDING + pl.getY() * scale);
            int w = (int) Math.max(2, st.resolutionWidthPx() * scale);
            int h = (int) Math.max(2, st.resolutionHeightPx() * scale);

            boolean isSelected = pl == selected;
            // Настоящая маска (тот же PixelGridRenderer, что и экспорт), просто
            // отмасштабированная под текущий вид канваса — вместо плоского
            // прямоугольника-заглушки видно реальный чек-борд, цвет пресета,
            // сетку и подписи кабинетов, как это будет выглядеть у контентщика.
            BufferedImage maskImg = maskCache.computeIfAbsent(pl.getId(),
                    id -> PixelGridRenderer.renderMask(scr, type, model.getWorkspace(),
                            PixelGridRenderer.GridRenderOptions.of(scr, pl, canvas, settings)));
            g2.drawImage(maskImg, x, y, w, h, null);
            g2.setColor(isSelected ? Color.WHITE : Palette.BORDER);
            g2.setStroke(new BasicStroke(isSelected ? 2.5f : 1.4f));
            g2.drawRect(x, y, w, h);

            // Мелкий HUD-лейбл поверх маски в углу — крупная подпись уже вписана
            // renderMask-ом по центру, но при сильном уменьшении масштаба канваса
            // (много экранов сразу) она нечитаема; этот всегда виден.
            String resLabel = st.resolutionWidthPx() + "×" + st.resolutionHeightPx() + " px";
            g2.setFont(labelFont);
            int nameW = g2.getFontMetrics().stringWidth(scr.getName());
            g2.setFont(metaFont);
            int resW = g2.getFontMetrics().stringWidth(resLabel);
            g2.setColor(new Color(0, 0, 0, 170));
            g2.fillRect(x, y, Math.max(nameW, resW) + 8, 32);
            g2.setColor(isSelected ? Palette.ACCENT : new Color(0xc0, 0xc8, 0xd0));
            g2.setFont(labelFont);
            g2.drawString(scr.getName(), x + 4, y + 16);
            g2.setColor(Palette.MUTED);
            g2.setFont(metaFont);
            g2.drawString(resLabel, x + 4, y + 30);
        }

        g2.setColor(Palette.MUTED);
        g2.setFont(metaFont);
        g2.drawString(canvas.getName() + " — " + canvas.getWidthPx() + "×" + canvas.getHeightPx() + " px",
                PADDING, PADDING + ch + 16);

        drawSnapGuides(g2, PADDING, PADDING, cw, ch);

        g2.dispose();
    }

    /** Направляющие линии Shift-прилипания (см. snap()) — тот же стиль, что и в
     *  SchemaCanvasPanel ("как в yEd Graph Editor"), но в границах видимого канваса,
     *  а не всего компонента (вокруг канваса есть поля с подписями). */
    private void drawSnapGuides(Graphics2D g2, int left, int top, int width, int height) {
        if (snapGuideScreenX == null && snapGuideScreenY == null) {
            return;
        }
        g2.setColor(Color.MAGENTA);
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0, new float[]{4, 4}, 0));
        if (snapGuideScreenX != null) {
            g2.drawLine(snapGuideScreenX, top, snapGuideScreenX, top + height);
        }
        if (snapGuideScreenY != null) {
            g2.drawLine(left, snapGuideScreenY, left + width, snapGuideScreenY);
        }
    }
}
