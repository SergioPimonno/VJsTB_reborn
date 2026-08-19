package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.VehicleType;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Холст загрузки машины (первый заход — MVP, см. VEHICLE_CALC_NOTES.md): вид сверху
 * на грузовой отсек выбранной {@link VehicleType} (ограничивает доступное
 * пространство её габаритами кузова), внутри — размещённые кофры, перетаскиваются
 * мышью. По образцу {@link CanvasEditorPanel} (тот же приём: масштабируемый
 * прямоугольник-контейнер + перетаскиваемые прямоугольники-размещения), но с двумя
 * отличиями, специфичными для загрузки машины (а не канваса маски):
 * <ul>
 * <li>Перетаскивание НЕ принимает позицию, которая пересекается с ДРУГИМ кофром
 *     (или выходит за пределы кузова) — в отличие от масок, тут пересечение
 *     физически бессмысленно (два кофра не могут занимать одно место), поэтому
 *     показывать его как «можно, но не нужно» было бы неверно. «Пересечение» с
 *     ДРУГИМ типом кофра проверяется с учётом суммарного запаса по периметру
 *     ({@code CaseType#getClearanceMm()} обоих кофров, пополам с каждой
 *     стороны — см. {@link #overlaps}) — кофры физически не стоят впритирку.
 *     Запас применяется ТОЛЬКО между кофрами, не у стенок кузова.</li>
 * <li>Перетаскивание кофра почти точно НА ДРУГОЙ кофр ТОГО ЖЕ типа (вместо
 *     пересечения — штабелирование) увеличивает счётчик уровней у цели вместо
 *     создания второго прямоугольника — так штабелирование (см.
 *     {@code CaseType#maxStackCount}) показывается визуально без честной 3D-сцены.</li>
 * </ul>
 * Цвет заливки — пока жёстко {@link #COLOR_CARRIES_CABINETS}/{@link
 * #COLOR_OTHER} по {@code CaseType#isCarriesCabinets()} (запрос пользователя:
 * «кофры для кабинетов чёрные, с коммутацией синие») — временное правило до
 * реальных референсных фото, не полноценная система категорий/цветов.
 *
 * <p>Прилипание к краям соседних кофров/кузова при перетаскивании — см. {@link
 * #snapEnabled}/{@link #setSnapEnabled}, переключатель живёт в окне
 * калькулятора (`VehicleCalculatorDialog`), не в самом визуализаторе. ПКМ по
 * кофру — примечание менеджера (см. {@link #editNote}), показывается второй
 * строкой под именем кофра.
 *
 * <p>Эта панель сама не устанавливает {@code TransferHandler}/drag-source —
 * приём drag-n-drop из палитры кофров настраивает вызывающий код
 * ({@code VehicleLoadVisualizerDialog}, свой {@code TransferHandler} на
 * каждую секцию-машину), используя {@link #CASE_TYPE_FLAVOR}/{@link
 * #addPlacementAt}/{@link #pxToMm} как публичный контракт этой панели.
 *
 * <p><b>Приближение</b> (запрос пользователя) — Ctrl+колесо мыши или {@link
 * #zoomBy}/{@link #setZoom}, множитель поверх базового "вписать в вид"
 * масштаба (см. {@link #scale()}). Панель ожидает, что вызывающий код обернёт
 * её в {@code JScrollPane} (см. `VehicleLoadVisualizerDialog.VehicleSection`)
 * — {@link #getPreferredSize()} растёт вместе с зумом, чтобы прокрутка
 * появлялась, когда содержимое перестаёт помещаться в исходный размер
 * вьюпорта; обычное (без Ctrl) колесо мыши руками прокидывается в
 * вертикальный скроллбар этого JScrollPane (Swing не делает это
 * автоматически, если у дочернего компонента есть свой MouseWheelListener).
 * Шрифты подписей и толщина линий масштабируются вместе с зумом (не
 * фиксированы в pt/px) — это чисто векторная (Java2D) отрисовка с
 * антиалиасингом, поэтому приближение остаётся чётким на любом масштабе без
 * растрового пересчёта (в отличие от растрового вывода документации/масок
 * при печати, которому реально нужен фиксированный DPI).
 */
public class VehicleLoadCanvasPanel extends JPanel {

    private static final int PADDING = 24;
    static final Color COLOR_CARRIES_CABINETS = new Color(0x1a1a1a);
    static final Color COLOR_OTHER = new Color(0x1f6feb);

    /** Флейвор перетаскивания {@link CaseType} из палитры (см.
     *  VehicleLoadVisualizerDialog) на этот холст — обмен только внутри одной
     *  JVM (сам объект CaseType, не сериализация), см. {@code
     *  DataFlavor.javaJVMLocalObjectMimeType}. */
    static final DataFlavor CASE_TYPE_FLAVOR;

    static {
        try {
            CASE_TYPE_FLAVOR = new DataFlavor(
                    DataFlavor.javaJVMLocalObjectMimeType + ";class=" + CaseType.class.getName());
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** Доля площади меньшего прямоугольника, при пересечении которой (для кофров
     *  ОДНОГО типа) перетаскивание трактуется как «поставить в штабель», а не как
     *  обычное (запрещённое) пересечение — см. class-javadoc. */
    private static final double STACK_MERGE_OVERLAP_FRACTION = 0.5;

    static final class Placement {
        final CaseType type;
        double xMm;
        double yMm;
        boolean rotated;
        int stackCount = 1;
        /** Примечание менеджера (ПКМ по кофру), пусто — не задано. */
        String note = "";

        Placement(CaseType type) {
            this.type = type;
        }

        double footprintWMm() {
            return rotated ? type.getWidthMm() : type.getLengthMm();
        }

        double footprintHMm() {
            return rotated ? type.getLengthMm() : type.getWidthMm();
        }
    }

    /** Порог прилипания к краю соседнего кофра/кузова, в экранных px (переводится
     *  в мм через текущий scale) — см. {@link #snapEnabled}. */
    private static final double SNAP_THRESHOLD_PX = 10;

    /** Опорный размер вьюпорта для {@link #baseFitScale} — НЕ читается из
     *  {@code getWidth()/getHeight()} этой панели (они растут вместе с зумом,
     *  раз панель теперь живёт в JScrollPane — читать оттуда создало бы
     *  обратную связь: выросший preferred size менял бы getWidth(), которая
     *  меняла бы scale(), которая опять меняла бы preferred size). Фиксированное
     *  опорное значение — тот же размер, что и раньше был явным
     *  setPreferredSize у канваса в VehicleSection. */
    private static final int REF_VIEWPORT_W = 600;
    private static final int REF_VIEWPORT_H = 190;
    private static final double ZOOM_MIN = 0.3;
    private static final double ZOOM_MAX = 5.0;
    private static final double ZOOM_WHEEL_STEP = 1.15;

    private VehicleType vehicle;
    private final List<Placement> placements = new ArrayList<>();
    private Placement selected;
    private Placement dragging;
    private double dragOffXpx, dragOffYpx;
    private double dragStartXmm, dragStartYmm;
    private Runnable onChanged = () -> { };
    private double zoom = 1.0;
    private double baseFitScale = 1.0;
    /** Прилипание к краям соседних кофров/кузова при перетаскивании — включаемо
     *  чекбоксом в окне калькулятора (см. VehicleCalculatorDialog), по умолчанию
     *  включено. В отличие от {@link CanvasEditorPanel} (там Shift включает
     *  прилипание НА ВРЕМЯ перетаскивания) — тут это постоянный переключатель,
     *  а не модификатор клавиши. */
    private boolean snapEnabled = true;

    public VehicleLoadCanvasPanel() {
        setBackground(Palette.BG);
        setFocusable(true);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                Placement hit = placementAt(e.getPoint());
                if (SwingUtilities.isRightMouseButton(e)) {
                    selected = hit;
                    repaint();
                    if (hit != null) {
                        editNote(hit);
                    }
                    return;
                }
                selected = hit;
                if (hit != null) {
                    double scale = scale();
                    double px = PADDING + hit.xMm * scale;
                    double py = PADDING + hit.yMm * scale;
                    dragOffXpx = e.getX() - px;
                    dragOffYpx = e.getY() - py;
                    dragStartXmm = hit.xMm;
                    dragStartYmm = hit.yMm;
                    dragging = hit;
                }
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging == null || vehicle == null) {
                    return;
                }
                double scale = scale();
                double nx = (e.getX() - dragOffXpx - PADDING) / scale;
                double ny = (e.getY() - dragOffYpx - PADDING) / scale;
                if (snapEnabled) {
                    double[] snapped = snap(nx, ny, dragging.footprintWMm(), dragging.footprintHMm(),
                            dragging.type.getClearanceMm(), scale);
                    nx = snapped[0];
                    ny = snapped[1];
                }
                dragging.xMm = nx;
                dragging.yMm = ny;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragging == null) {
                    return;
                }
                resolveDrop(dragging);
                dragging = null;
                onChanged.run();
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (selected == null) {
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_R) {
                    rotateSelected();
                } else if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    removeOrShrinkSelected();
                }
            }
        });

        // Ctrl+колесо — зум; обычное колесо — прокидываем в скроллбар обёртывающего
        // JScrollPane вручную (см. class-javadoc про то, почему Swing не делает это
        // сам, раз у панели есть собственный MouseWheelListener).
        addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                double factor = e.getWheelRotation() < 0 ? ZOOM_WHEEL_STEP : 1 / ZOOM_WHEEL_STEP;
                zoomBy(factor);
            } else {
                JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
                if (sp != null) {
                    JScrollBar bar = sp.getVerticalScrollBar();
                    bar.setValue(bar.getValue() + e.getUnitsToScroll() * bar.getUnitIncrement());
                }
            }
        });
    }

    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged != null ? onChanged : () -> { };
    }

    public void setSnapEnabled(boolean snapEnabled) {
        this.snapEnabled = snapEnabled;
    }

    public double getZoom() {
        return zoom;
    }

    public void setZoom(double newZoom) {
        double clamped = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, newZoom));
        if (clamped == zoom) {
            return;
        }
        zoom = clamped;
        revalidate(); // preferred size изменился — JScrollPane должен пересчитать скроллбары
        repaint();
    }

    public void zoomBy(double factor) {
        setZoom(zoom * factor);
    }

    /** ПКМ по кофру — примечание менеджера, показывается отдельной строкой под
     *  именем кофра (см. paintComponent). Предзаполняется текущим значением,
     *  пустая строка/Отмена — примечание убирается/не меняется. */
    private void editNote(Placement p) {
        Object result = JOptionPane.showInputDialog(this,
                "Примечание к кофру \"" + p.type.getName() + "\":", "Примечание",
                JOptionPane.PLAIN_MESSAGE, null, null, p.note);
        if (result == null) {
            return;
        }
        p.note = result.toString().trim();
        onChanged.run();
        repaint();
    }

    /** Смена машины сбрасывает раскладку — пространство определяется габаритами
     *  ИМЕННО этой машины (см. class-javadoc), продолжать показывать прежние
     *  позиции кофров под другой кузов было бы вводящим в заблуждение. */
    public void setVehicle(VehicleType vehicle) {
        this.vehicle = vehicle;
        placements.clear();
        selected = null;
        dragging = null;
        zoom = 1.0;
        recomputeBaseFitScale();
        revalidate();
        repaint();
    }

    private void recomputeBaseFitScale() {
        if (vehicle == null) {
            baseFitScale = 1;
            return;
        }
        double availW = Math.max(50, REF_VIEWPORT_W - PADDING * 2);
        double availH = Math.max(50, REF_VIEWPORT_H - PADDING * 2);
        baseFitScale = Math.max(0.001,
                Math.min(availW / vehicle.getCargoLengthMm(), availH / vehicle.getCargoWidthMm()));
    }

    @Override
    public Dimension getPreferredSize() {
        if (vehicle == null) {
            return new Dimension(REF_VIEWPORT_W, REF_VIEWPORT_H);
        }
        double s = scale();
        int w = (int) (vehicle.getCargoLengthMm() * s) + PADDING * 2;
        int h = (int) (vehicle.getCargoWidthMm() * s) + PADDING * 2 + 20; // место под подпись машины снизу
        return new Dimension(Math.max(REF_VIEWPORT_W, w), Math.max(REF_VIEWPORT_H, h));
    }

    public VehicleType getVehicle() {
        return vehicle;
    }

    /** Новое размещение стартует в углу кузова (0,0) — пользователь перетаскивает
     *  на нужное место сам (кнопка «Добавить сюда»). Для драг-н-дропа из палитры
     *  прямо на нужное место — см. {@link #addPlacementAt}. */
    public void addPlacement(CaseType type) {
        addPlacementAt(type, 0, 0);
    }

    /** Восстанавливает размещение из сохранённых данных (см. {@code
     *  model.VehicleLoadPlacement}) БЕЗ проверки коллизий/штабелирования —
     *  позиция уже была валидна, когда сохранялась (см. {@link #resolveDrop}),
     *  повторная проверка тут не нужна и рискует расходиться с оригинальной
     *  раскладкой при, например, изменении clearanceMm типа кофра в библиотеке
     *  задним числом. Используется только при ЗАГРУЗКЕ плана — не путать с
     *  {@link #addPlacementAt} (новое размещение пользователем, коллизии
     *  проверяются). Не помечает {@link #selected}, не вызывает {@link
     *  #onChanged} — вызывающий код сам решает, когда это нужно (обычно не
     *  нужно, при массовой загрузке всех размещений плана сразу). */
    public void restorePlacement(CaseType type, double xMm, double yMm, boolean rotated, int stackCount,
                                  String note) {
        if (type == null) {
            return;
        }
        Placement p = new Placement(type);
        p.xMm = xMm;
        p.yMm = yMm;
        p.rotated = rotated;
        p.stackCount = Math.max(1, stackCount);
        p.note = note != null ? note : "";
        placements.add(p);
    }

    /** Размещает новый кофр как можно ближе к (xMm,yMm) — левый верхний угол,
     *  вызывающий код (drag-n-drop из палитры) обычно передаёт уже
     *  скорректированную под центрирование под курсором точку. Если рядом (в
     *  пределах {@link #STACK_MERGE_OVERLAP_FRACTION}) уже стоит кофр ТОГО ЖЕ
     *  типа — штабелируется в него, как и при обычном перетаскивании (см.
     *  {@link #resolveDrop}). Если конкретно эта точка занята чужим кофром или
     *  вне кузова — откатывается на (0,0), а не остаётся висеть в
     *  недопустимом месте (в отличие от перетаскивания СУЩЕСТВУЮЩЕГО кофра,
     *  тут нет осмысленной "позиции до перетаскивания" для отката). */
    public void addPlacementAt(CaseType type, double xMm, double yMm) {
        if (vehicle == null || type == null) {
            return;
        }
        Placement p = new Placement(type);
        double w = p.footprintWMm();
        double h = p.footprintHMm();
        double clampedX = Math.max(0, Math.min(xMm, vehicle.getCargoLengthMm() - w));
        double clampedY = Math.max(0, Math.min(yMm, vehicle.getCargoWidthMm() - h));

        for (Placement other : placements) {
            if (other.type != type) {
                continue;
            }
            double overlap = overlapArea(clampedX, clampedY, w, h, other);
            double smallerArea = Math.min(w * h, other.footprintWMm() * other.footprintHMm());
            if (smallerArea > 0 && overlap / smallerArea >= STACK_MERGE_OVERLAP_FRACTION
                    && other.stackCount < type.getMaxStackCount()) {
                other.stackCount++;
                selected = other;
                onChanged.run();
                repaint();
                return;
            }
        }

        if (fitsWithinBoundsAndNoCollision(p, clampedX, clampedY)) {
            p.xMm = clampedX;
            p.yMm = clampedY;
        } // иначе остаётся на (0,0) — начальные значения полей Placement
        placements.add(p);
        selected = p;
        onChanged.run();
        repaint();
    }

    /** Экранные px (в координатах ЭТОЙ панели, включая {@link #PADDING}) →
     *  миллиметры кузова — обратное преобразование к тому, что использует
     *  {@link #paintComponent}/{@link #placementAt}. Для drag-n-drop импорта. */
    public double[] pxToMm(Point pt) {
        double scale = scale();
        return new double[]{(pt.x - PADDING) / scale, (pt.y - PADDING) / scale};
    }

    private void rotateSelected() {
        if (selected == null) {
            return;
        }
        boolean was = selected.rotated;
        selected.rotated = !selected.rotated;
        if (!fitsWithinBoundsAndNoCollision(selected, selected.xMm, selected.yMm)) {
            selected.rotated = was; // откат — поворот не помещается
        } else {
            onChanged.run();
        }
        repaint();
    }

    private void removeOrShrinkSelected() {
        if (selected == null) {
            return;
        }
        if (selected.stackCount > 1) {
            selected.stackCount--;
        } else {
            placements.remove(selected);
            selected = null;
        }
        onChanged.run();
        repaint();
    }

    public List<Placement> getPlacements() {
        return placements;
    }

    // ---- геометрия / коллизии ----

    /** Базовый "вписать в опорный вьюпорт" масштаб ({@link #baseFitScale},
     *  пересчитывается только при смене машины — см. {@link #recomputeBaseFitScale})
     *  умноженный на {@link #zoom}. НЕ читает {@code getWidth()/getHeight()} этой
     *  панели напрямую (см. class-javadoc про обратную связь с preferred size). */
    private double scale() {
        return baseFitScale * zoom;
    }

    /** AABB-пересечение (ax,ay,aw,ah) с {@code b}, раздвинутое на суммарный
     *  зазор {@code (clearanceMm + b.type.getClearanceMm())/2} с каждой стороны
     *  (по половине запаса с каждого кофра — так пара кофров с одинаковым
     *  clearanceMm получает между собой ровно {@code clearanceMm}, а не
     *  {@code 2×clearanceMm}). Кофры физически не стоят впритирку — см.
     *  {@code CaseType#getClearanceMm()}. */
    private boolean overlaps(double ax, double ay, double aw, double ah, double clearanceMm, Placement b) {
        double gap = (clearanceMm + b.type.getClearanceMm()) / 2.0;
        double ix = ax - gap;
        double iy = ay - gap;
        double iw = aw + 2 * gap;
        double ih = ah + 2 * gap;
        return ix < b.xMm + b.footprintWMm() && ix + iw > b.xMm
                && iy < b.yMm + b.footprintHMm() && iy + ih > b.yMm;
    }

    private double overlapArea(double ax, double ay, double aw, double ah, Placement b) {
        double ox = Math.max(0, Math.min(ax + aw, b.xMm + b.footprintWMm()) - Math.max(ax, b.xMm));
        double oy = Math.max(0, Math.min(ay + ah, b.yMm + b.footprintHMm()) - Math.max(ay, b.yMm));
        return ox * oy;
    }

    /** Прилипание (nx,ny) — левого верхнего угла перетаскиваемого кофра (w×h мм,
     *  запас {@code clearanceMm}) — к ближайшим краям ДРУГИХ кофров (с учётом
     *  суммарного запаса, см. {@link #overlaps}) и краям кузова (без запаса —
     *  clearanceMm только между кофрами, не у стенок), в пределах {@link
     *  #SNAP_THRESHOLD_PX} экранных px (переведено в мм через scale). В отличие
     *  от {@link CanvasEditorPanel#snap} — без плавного "blend" (сила прилипания
     *  там завязана на настройку профиля, не подходящую этому диалогу): при
     *  попадании в порог сразу прилипает вплотную (точнее — на расстояние
     *  запаса, а не впритирку). */
    private double[] snap(double nx, double ny, double w, double h, double clearanceMm, double scale) {
        double thresholdMm = SNAP_THRESHOLD_PX / scale;
        List<Double> xTargets = new ArrayList<>();
        List<Double> yTargets = new ArrayList<>();
        xTargets.add(0.0);
        xTargets.add(vehicle.getCargoLengthMm() - w);
        yTargets.add(0.0);
        yTargets.add(vehicle.getCargoWidthMm() - h);
        for (Placement other : placements) {
            if (other == dragging) {
                continue;
            }
            double gap = (clearanceMm + other.type.getClearanceMm()) / 2.0;
            double ow = other.footprintWMm();
            double oh = other.footprintHMm();
            xTargets.add(other.xMm); // выровнять левые края (для колонки друг над другом)
            xTargets.add(other.xMm + ow - w); // выровнять правые края
            xTargets.add(other.xMm + ow + gap); // встать вплотную (с запасом) справа от соседа
            xTargets.add(other.xMm - w - gap); // встать вплотную (с запасом) слева от соседа
            yTargets.add(other.yMm);
            yTargets.add(other.yMm + oh - h);
            yTargets.add(other.yMm + oh + gap);
            yTargets.add(other.yMm - h - gap);
        }
        return new double[]{closest(nx, xTargets, thresholdMm), closest(ny, yTargets, thresholdMm)};
    }

    private double closest(double value, List<Double> targets, double threshold) {
        double best = value;
        double bestDist = threshold;
        for (double t : targets) {
            double d = Math.abs(t - value);
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        return best;
    }

    private boolean fitsWithinBoundsAndNoCollision(Placement self, double x, double y) {
        double w = self.footprintWMm();
        double h = self.footprintHMm();
        if (x < 0 || y < 0 || x + w > vehicle.getCargoLengthMm() || y + h > vehicle.getCargoWidthMm()) {
            return false;
        }
        for (Placement other : placements) {
            if (other == self) {
                continue;
            }
            if (overlaps(x, y, w, h, self.type.getClearanceMm(), other)) {
                return false;
            }
        }
        return true;
    }

    /** После отпускания мыши: если итоговая позиция пересекает другой кофр ТОГО ЖЕ
     *  типа больше чем на {@link #STACK_MERGE_OVERLAP_FRACTION} площади — это
     *  штабелирование (см. class-javadoc), сливаем в один прямоугольник со
     *  счётчиком уровней. Если помещается свободно — оставляем. Иначе (пересекает
     *  чужой кофр или вышло за пределы кузова) — откатываем на позицию ДО
     *  перетаскивания. */
    private void resolveDrop(Placement p) {
        double w = p.footprintWMm();
        double h = p.footprintHMm();
        for (Placement other : placements) {
            if (other == p || other.type != p.type) {
                continue;
            }
            double overlap = overlapArea(p.xMm, p.yMm, w, h, other);
            double smallerArea = Math.min(w * h, other.footprintWMm() * other.footprintHMm());
            if (smallerArea > 0 && overlap / smallerArea >= STACK_MERGE_OVERLAP_FRACTION) {
                if (other.stackCount < p.type.getMaxStackCount()) {
                    other.stackCount += p.stackCount;
                    if (other.stackCount > p.type.getMaxStackCount()) {
                        other.stackCount = p.type.getMaxStackCount();
                    }
                    placements.remove(p);
                    selected = other;
                } else {
                    p.xMm = dragStartXmm;
                    p.yMm = dragStartYmm;
                }
                return;
            }
        }
        if (!fitsWithinBoundsAndNoCollision(p, p.xMm, p.yMm)) {
            p.xMm = dragStartXmm;
            p.yMm = dragStartYmm;
        }
    }

    private Placement placementAt(Point pt) {
        if (vehicle == null) {
            return null;
        }
        double scale = scale();
        for (int i = placements.size() - 1; i >= 0; i--) {
            Placement p = placements.get(i);
            int x = (int) (PADDING + p.xMm * scale);
            int y = (int) (PADDING + p.yMm * scale);
            int w = (int) Math.max(2, p.footprintWMm() * scale);
            int h = (int) Math.max(2, p.footprintHMm() * scale);
            if (pt.x >= x && pt.x <= x + w && pt.y >= y && pt.y <= y + h) {
                return p;
            }
        }
        return null;
    }

    // ---- отрисовка ----

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (vehicle == null) {
            g2.setColor(Palette.MUTED);
            g2.setFont(getFont().deriveFont(14f));
            g2.drawString("Выберите машину.", PADDING, PADDING + 20);
            g2.dispose();
            return;
        }

        double scale = scale();
        int cw = (int) (vehicle.getCargoLengthMm() * scale);
        int ch = (int) (vehicle.getCargoWidthMm() * scale);

        float zoomF = (float) zoom;
        g2.setColor(Palette.PANEL);
        g2.fillRect(PADDING, PADDING, cw, ch);
        g2.setColor(Palette.ACCENT);
        g2.setStroke(new BasicStroke(clampF(2f * zoomF, 1f, 6f)));
        g2.drawRect(PADDING, PADDING, cw, ch);

        // Шрифты/толщина линий масштабируются вместе с зумом (не фиксированы в pt/px) —
        // запрос пользователя "надписи должны масштабироваться по размеру блока". Это
        // чисто векторная (Java2D) отрисовка с антиалиасингом — приближение остаётся
        // чётким на любом масштабе без растрового пересчёта.
        Font labelFont = getFont().deriveFont(Font.BOLD, clampF(11f * zoomF, 8f, 28f));
        Font noteFont = getFont().deriveFont(Font.ITALIC, clampF(10f * zoomF, 7f, 26f));
        for (Placement p : placements) {
            int x = (int) (PADDING + p.xMm * scale);
            int y = (int) (PADDING + p.yMm * scale);
            int w = (int) Math.max(2, p.footprintWMm() * scale);
            int h = (int) Math.max(2, p.footprintHMm() * scale);

            g2.setColor(p.type.isCarriesCabinets() ? COLOR_CARRIES_CABINETS : COLOR_OTHER);
            g2.fillRect(x, y, w, h);
            boolean isSelected = p == selected;
            g2.setColor(isSelected ? Color.WHITE : Palette.BORDER);
            g2.setStroke(new BasicStroke(clampF((isSelected ? 2.5f : 1.2f) * zoomF, 0.8f, 8f)));
            g2.drawRect(x, y, w, h);

            g2.setFont(labelFont);
            java.awt.FontMetrics nameFm = g2.getFontMetrics();
            String name = p.type.getName();
            // Ориентация подписи следует ФАКТИЧЕСКОМУ повороту кофра (p.rotated), а не
            // независимо для каждой строки — баг-репорт: раньше имя и примечание
            // выбирали горизонталь/вертикаль каждое по своему best-fit, из-за чего у
            // одного и того же повёрнутого кофра имя оказывалось вертикальным, а
            // примечание — горизонтальным (несогласованно, выглядело как "не работает").
            // Фит-проверка остаётся страховкой на случай, если даже предпочитаемая
            // ориентация не влезает вовсе.
            boolean nameFitsH = nameFm.stringWidth(name) < w - 4 && h > 14;
            boolean nameFitsV = nameFm.stringWidth(name) < h - 4 && w > 14;
            boolean nameVertical = p.rotated ? (nameFitsV || !nameFitsH) : (!nameFitsH && nameFitsV);
            boolean nameDrawn = nameVertical ? nameFitsV : nameFitsH;
            if (nameDrawn) {
                g2.setColor(Color.WHITE);
                if (nameVertical) {
                    drawVerticalString(g2, name, x + nameFm.getAscent() + 2, y + h - 3);
                } else {
                    g2.drawString(name, x + 3, y + nameFm.getAscent() + 1);
                }
            }

            // Примечание менеджера (ПКМ по кофру) — та же логика ориентации, что и у
            // имени (см. выше), тем же p.rotated — обе подписи поворачиваются вместе.
            if (p.note != null && !p.note.isBlank()) {
                g2.setFont(noteFont);
                java.awt.FontMetrics noteFm = g2.getFontMetrics();
                boolean noteFitsH = noteFm.stringWidth(p.note) < w - 4 && h > (nameDrawn && !nameVertical ? 28 : 14);
                boolean noteFitsV = noteFm.stringWidth(p.note) < h - 4 && w > 14;
                boolean noteVertical = p.rotated ? (noteFitsV || !noteFitsH) : (!noteFitsH && noteFitsV);
                boolean noteDrawn = noteVertical ? noteFitsV : noteFitsH;
                if (noteDrawn) {
                    g2.setColor(new Color(0xffd866));
                    if (noteVertical) {
                        // Вторая вертикальная "колонка" правее имени (если оно тоже
                        // вертикальное), чтобы не рисовать примечание поверх имени.
                        int noteColumnX = x + noteFm.getAscent() + 2
                                + (nameDrawn && nameVertical ? nameFm.getHeight() + 2 : 0);
                        drawVerticalString(g2, p.note, noteColumnX, y + h - 3);
                    } else {
                        int lineY = nameDrawn && !nameVertical
                                ? y + nameFm.getAscent() + 1 + noteFm.getAscent() + 2
                                : y + noteFm.getAscent() + 1;
                        g2.drawString(p.note, x + 3, lineY);
                    }
                }
            }

            // Счётчик уровней штабеля — отдельный бейдж в углу, рисуется НЕЗАВИСИМО от
            // имени (баг-репорт: при маленьком отпечатке "имя ×N" целиком не влезало по
            // ширине, и весь label пропадал — вместе с ×N, единственным указанием на то,
            // что здесь вообще стоит несколько кофров, а не один).
            if (p.stackCount > 1) {
                drawStackBadge(g2, x, y, w, h, p.stackCount, zoomF);
            }
        }

        g2.setColor(Palette.MUTED);
        g2.setFont(getFont().deriveFont(clampF(10f * zoomF, 8f, 20f)));
        g2.drawString(vehicle.getName() + " — кузов " + (int) vehicle.getCargoLengthMm() + "×"
                + (int) vehicle.getCargoWidthMm() + " мм", PADDING, PADDING + ch + 16);

        g2.dispose();
    }

    private static float clampF(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Текст снизу вверх вдоль вертикальной оси, начиная от (baseX,baseY) — {@code
     *  baseX} задаёт горизонтальное положение "колонки" (обычно {@code x +
     *  fm.getAscent() + отступ}), {@code baseY} — нижнюю точку строки (обычно
     *  {@code y + h - отступ}). Используется, когда горизонтальная подпись
     *  (имя кофра/примечание) не влезает по ширине отпечатка (у повёрнутого или
     *  изначально узкого кофра) — вместо того, чтобы просто не рисовать её. */
    private void drawVerticalString(Graphics2D g2, String text, int baseX, int baseY) {
        Graphics2D gv = (Graphics2D) g2.create();
        gv.translate(baseX, baseY);
        gv.rotate(-Math.PI / 2);
        gv.drawString(text, 0, 0);
        gv.dispose();
    }

    /** Бейдж "×N" (уровней в штабеле) в правом нижнем углу отпечатка — размер шрифта
     *  подстраивается под сам отпечаток (и под zoomF, чтобы верхняя граница росла
     *  вместе с приближением, а не упиралась в потолок), но не мельче {@link
     *  #MIN_BADGE_FONT_PT}, и бейдж рисуется ВСЕГДА при stackCount > 1, даже если
     *  отпечаток настолько мал, что бейдж не помещается целиком (тогда просто
     *  прижимается к левому/верхнему краю прямоугольника, а не пропадает — см.
     *  вызывающий код). */
    private static final float MIN_BADGE_FONT_PT = 9f;

    private void drawStackBadge(Graphics2D g2, int x, int y, int w, int h, int stackCount, float zoomF) {
        String text = "×" + stackCount;
        float fontSize = (float) Math.max(MIN_BADGE_FONT_PT, Math.min(13 * zoomF, Math.min(w, h) * 0.4));
        Font badgeFont = getFont().deriveFont(Font.BOLD, fontSize);
        g2.setFont(badgeFont);
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int textW = fm.stringWidth(text);
        int textAscent = fm.getAscent();
        int padX = 4;
        int padY = 2;
        int bw = textW + padX * 2;
        int bh = textAscent + padY * 2;
        int bx = x + w - bw - 1;
        int by = y + h - bh - 1;
        if (bw > w) {
            bx = x;
        }
        if (bh > h) {
            by = y;
        }
        g2.setColor(new Color(0, 0, 0, 220));
        g2.fillRoundRect(bx, by, bw, bh, 6, 6);
        g2.setColor(Color.WHITE);
        g2.drawString(text, bx + padX, by + padY + textAscent);
    }
}
