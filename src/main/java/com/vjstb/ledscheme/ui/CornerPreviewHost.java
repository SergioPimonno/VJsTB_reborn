package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/**
 * Рамка корнер-виджета превью сцены в правом нижнем углу холста этапов Питание/
 * Сигнал (см. {@code SignalStagePanel}/{@code PowerStagePanel}) — вынесена из
 * дубликатов в обоих этапах, чтобы размер и поведение не разъезжались.
 * <p>Виджет привязан к правому нижнему углу, а тянуть его можно за левый и верхний
 * край (и за угол) — как раз те, что от угла удалены; размер запоминается в
 * профиле (общий для обоих этапов, ключи {@link #KEY_W}/{@link #KEY_H}) и
 * временно ужимается по месту, если окно стало меньше. Внутри — {@link SceneCanvasPanel} в
 * режиме {@link SceneCanvasPanel#setViewOnly «только навигация»}: зум колесом,
 * перемещение протяжкой, без редактирования кабинетов.
 */
public final class CornerPreviewHost extends JPanel {

    public static final String KEY_W = "cornerPreview.w";
    public static final String KEY_H = "cornerPreview.h";

    private static final int MIN_W = 180;
    private static final int MIN_H = 120;
    private static final int MARGIN = 10;
    /** Толщина полосы захвата слева/сверху — пустая часть рамки вокруг превью, до
     *  него мышь дотягивается как до обычной границы окна. */
    private static final int GRIP = 6;
    private static final int LEFT = 1;
    private static final int TOP = 2;

    private final SettingsManager settings;
    private int wantedW;
    private int wantedH;

    private int dragMode;
    private int pressScreenX;
    private int pressScreenY;
    private int startW;
    private int startH;

    public CornerPreviewHost(SceneCanvasPanel preview, SettingsManager settings, int defaultW, int defaultH) {
        super(new BorderLayout());
        this.settings = settings;
        this.wantedW = (int) Math.round(settings.getLayoutProportion(KEY_W, defaultW));
        this.wantedH = (int) Math.round(settings.getLayoutProportion(KEY_H, defaultH));
        setBackground(Palette.PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Palette.BORDER),
                BorderFactory.createEmptyBorder(GRIP, GRIP, 0, 0)));
        preview.setViewOnly(true);
        preview.setToolTipText("Колесо — масштаб, протяжка мышью — перемещение, двойной клик — вся сцена."
                + " Левый и верхний край рамки — изменить размер. Кабинеты здесь не редактируются.");
        add(preview, BorderLayout.CENTER);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setCursor(cursorFor(zoneAt(e)));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                dragMode = zoneAt(e);
                pressScreenX = e.getXOnScreen();
                pressScreenY = e.getYOnScreen();
                startW = getWidth();
                startH = getHeight();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragMode == 0) {
                    return;
                }
                // Экранные координаты, не локальные: сам виджет двигается вместе с
                // курсором (привязан к правому нижнему углу), локальные дрожали бы.
                if ((dragMode & LEFT) != 0) {
                    wantedW = startW - (e.getXOnScreen() - pressScreenX);
                }
                if ((dragMode & TOP) != 0) {
                    wantedH = startH - (e.getYOnScreen() - pressScreenY);
                }
                Container parent = getParent();
                if (parent != null) {
                    // Без зажима «желаемый» размер уезжал бы за предел окна, и обратная
                    // протяжка начиналась бы не от видимого края.
                    wantedW = Math.max(MIN_W, Math.min(wantedW, Math.max(MIN_W, parent.getWidth() - 2 * MARGIN)));
                    wantedH = Math.max(MIN_H, Math.min(wantedH, Math.max(MIN_H, parent.getHeight() - 2 * MARGIN)));
                }
                placeInParent();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragMode != 0) {
                    dragMode = 0;
                    // Сохраняем то, что реально видно после протяжки (с учётом ужатия
                    // по окну), и делаем это же новым «желаемым» размером.
                    wantedW = getWidth();
                    wantedH = getHeight();
                    settings.setLayoutProportion(KEY_W, wantedW);
                    settings.setLayoutProportion(KEY_H, wantedH);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        setBounds(0, 0, wantedW, wantedH);
    }

    private int zoneAt(MouseEvent e) {
        int zone = 0;
        if (e.getX() <= GRIP + 1) {
            zone |= LEFT;
        }
        if (e.getY() <= GRIP + 1) {
            zone |= TOP;
        }
        return zone;
    }

    private static Cursor cursorFor(int zone) {
        return switch (zone) {
            case LEFT | TOP -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            case LEFT -> Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
            case TOP -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            default -> Cursor.getDefaultCursor();
        };
    }

    /** Ставит виджет в правый нижний угол родителя (null-layout {@code JLayeredPane}
     *  холста) с учётом желаемого размера, зажатого между минимумом и размером окна. */
    public void placeInParent() {
        Container parent = getParent();
        if (parent == null) {
            return;
        }
        int maxW = Math.max(MIN_W, parent.getWidth() - 2 * MARGIN);
        int maxH = Math.max(MIN_H, parent.getHeight() - 2 * MARGIN);
        // Желаемый размер не перезаписывается ужатием: если окно временно стало
        // меньше, а потом снова выросло, виджет возвращается к выбранному размеру.
        int w = Math.max(MIN_W, Math.min(wantedW, maxW));
        int h = Math.max(MIN_H, Math.min(wantedH, maxH));
        setBounds(parent.getWidth() - w - MARGIN, parent.getHeight() - h - MARGIN, w, h);
        // Баг-репорт: рамка тянулась, а холст превью внутри оставался старого размера.
        // Родитель — null-layout, setBounds только инвалидирует этот контейнер, и
        // BorderLayout внутри никто не перекладывал до случайного revalidate извне.
        validate();
        parent.repaint();
    }
}
