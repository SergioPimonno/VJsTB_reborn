package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetShape;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

/**
 * Компактный редактор сетки экрана: клик (или протяжка ЛКМ) исключает/включает
 * ячейку — так задаётся непрямоугольная форма экрана (треугольная, ступенчатая
 * и т.д.). ПКМ (зажать и повести к пункту, как в Krita) открывает 2-уровневое
 * радиальное меню: первый уровень — скрыть/восстановить ячейку, «Форма» и «Тип»;
 * второй уровень (для «Форма»/«Тип») — сам выбор конкретного значения.
 */
public class ShapeEditorPanel extends JPanel {

    private static final int CELL = 22;
    private static final double MIN_ZOOM = 0.2;
    private static final double MAX_ZOOM = 3.0;

    private final AppModel model;
    private final com.vjstb.ledscheme.settings.SettingsManager settings;
    private String lastDragCabId;
    private String hoveredCabId;
    /** true, пока открыто радиальное меню (от ПКМ до выбора пункта/отмены). Клики
     *  ЛКМ по холсту при этом нужно игнорировать: JWindow меню — Window.Type.POPUP,
     *  такие окна на многих платформах не получают события мыши сами (click-through),
     *  поэтому клик по пункту меню ЛКМ доходит и до этого компонента тоже и без
     *  этой защиты переключал скрытие ячейки под меню как побочный эффект. */
    private boolean radialMenuActive;
    /** Масштаб сетки (Ctrl+колесо) — по умолчанию подгоняется под видимую область
     *  вьюпорта при выборе экрана (см. autoFit()), пока пользователь не измасштабирует
     *  вручную (тогда его выбор сохраняется до смены экрана). */
    private double zoom = 1.0;
    private boolean userZoomed;
    private String lastScreenId;

    public ShapeEditorPanel(AppModel model, com.vjstb.ledscheme.settings.SettingsManager settings) {
        this.model = model;
        this.settings = settings;
        setBackground(Palette.BG);
        setFocusable(true);
        setToolTipText(" "); // непустое значение регистрирует компонент в ToolTipManager

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (SwingUtilities.isRightMouseButton(e)) {
                    showRadialMenu(e);
                    return;
                }
                if (radialMenuActive) {
                    return;
                }
                lastDragCabId = null;
                handle(e.getPoint());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && !radialMenuActive) {
                    handle(e.getPoint());
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                CabinetInstance cab = cabinetAt(e.getPoint());
                hoveredCabId = cab != null ? cab.getId() : null;
            }

            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                if (e.isControlDown()) {
                    double factor = e.getWheelRotation() < 0 ? 1.1 : 1 / 1.1;
                    zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
                    userZoomed = true;
                    revalidate();
                    repaint();
                } else if (getParent() != null) {
                    getParent().dispatchEvent(e);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);

        // Хоткей скрыть/показать ячейку под курсором (Del/Backspace) — не дублируется
        // пунктом в радиальном меню, см. showRadialMenu().
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if ((e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE)
                        && hoveredCabId != null) {
                    model.toggleCabinetHidden(hoveredCabId);
                }
            }
        });
    }

    private int cellSize() {
        return Math.max(4, (int) Math.round(CELL * zoom));
    }

    /** Если пользователь ещё не масштабировал вручную (Ctrl+колесо) — подгоняет
     *  масштаб под текущий видимый вьюпорт при выборе экрана, чтобы вся сетка
     *  была видна сразу без прокрутки (иначе большой экран обрезался снизу/справа
     *  без какой-либо возможности увидеть его целиком). Сбрасывается при смене
     *  экрана — на новом экране снова подгоняем автоматически. */
    private void autoFitIfNeeded(Screen scr) {
        String id = scr.getId();
        if (!id.equals(lastScreenId)) {
            lastScreenId = id;
            userZoomed = false;
            zoom = 1.0;
        }
        if (userZoomed) {
            return;
        }
        Container parent = getParent();
        if (!(parent instanceof JViewport vp)) {
            return;
        }
        int vw = vp.getWidth();
        int vh = vp.getHeight();
        if (vw <= 0 || vh <= 0) {
            return;
        }
        double naturalW = scr.getCols() * CELL + 4;
        double naturalH = scr.getRows() * CELL + 4;
        double fit = Math.min(1.0, Math.min(vw / naturalW, vh / naturalH));
        zoom = Math.max(MIN_ZOOM, fit);
    }

    private CabinetInstance cabinetAt(Point p) {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return null;
        }
        int cell = cellSize();
        int col = p.x / cell;
        int row = p.y / cell;
        return scr.cabinetAt(row, col);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return null;
        }
        CabinetInstance cab = cabinetAt(e.getPoint());
        if (cab == null) {
            return "Клик (или протяжка ЛКМ) — включить/исключить ячейку (или Del/Backspace над ней)."
                    + " ПКМ (зажать и повести к пункту) — радиальное меню: скрыть/восстановить, форма, тип."
                    + " Ctrl+колесо — масштаб.";
        }
        if (cab.isHidden()) {
            return "<html>Ячейка " + cab.getDisplayRow() + "," + cab.getDisplayCol()
                    + " — исключена (нет физического кабинета)</html>";
        }
        CabinetType t = effectiveType(cab, scr);
        if (t == null) {
            return "<html>Ячейка " + cab.getDisplayRow() + "," + cab.getDisplayCol() + " — тип не задан</html>";
        }
        String shapeLabel = cab.getShapeOverride() != null ? cab.getShapeOverride().getLabel() + " (переопределена)"
                : t.getShape().getLabel();
        return "<html><b>" + t.getName() + "</b>" + (cab.getCabinetTypeId() != null ? " (переопределён)" : "")
                + "<br>" + UiKit.fmt(t.getWidthMm()) + "×" + UiKit.fmt(t.getHeightMm()) + " мм · "
                + t.getResolutionWidth() + "×" + t.getResolutionHeight() + " px"
                + "<br>" + UiKit.fmtPower(t.getPowerConsumptionW(), settings.activeProfile().isPowerUnitKw())
                + " · " + UiKit.fmt(t.getWeightKg()) + " кг"
                + "<br>форма: " + shapeLabel + "</html>";
    }

    private CabinetType effectiveType(CabinetInstance cab, Screen scr) {
        if (cab.getCabinetTypeId() != null) {
            CabinetType override = model.getWorkspace().cabinetTypeById(cab.getCabinetTypeId());
            if (override != null) {
                return override;
            }
        }
        return model.typeOf(scr);
    }

    /** Клик/протяжка ЛКМ — включить/исключить ячейку под курсором (форма экрана). */
    private void handle(Point p) {
        CabinetInstance cab = cabinetAt(p);
        if (cab == null) {
            return;
        }
        if (cab.getId().equals(lastDragCabId)) {
            return;
        }
        lastDragCabId = cab.getId();
        model.toggleCabinetHidden(cab.getId());
    }

    /** ПКМ (зажать и повести к пункту, как в Krita) — 2-уровневое радиальное меню:
     *  1 уровень — скрыть/восстановить ячейку, «Форма», «Тип»; 2 уровень (для
     *  «Форма»/«Тип») — сам выбор конкретного значения. */
    private void showRadialMenu(MouseEvent e) {
        CabinetInstance cab = cabinetAt(e.getPoint());
        if (cab == null) {
            return;
        }
        Screen scr = model.getCurrentScreen();
        String cabId = cab.getId();

        List<RadialMenu.Item> items = new ArrayList<>();
        items.add(RadialMenu.Item.leaf(cab.isHidden() ? "Восстановить ячейку" : "Скрыть ячейку",
                Palette.MUTED, () -> model.toggleCabinetHidden(cabId)));
        // Пункт "Форма" виден только если у эффективного типа этого кабинета вообще
        // есть выбор — если физически возможна только одна форма (обычный случай),
        // пункт скрывается целиком: выбирать всё равно не из чего, а прежняя ЛЮБАЯ
        // из 4 форм позволяла назначить кабинету форму, которой у него в реальности
        // не бывает (см. Task #79/v1.4).
        CabinetType effective = effectiveType(cab, scr);
        if (effective != null && effective.getAllowedShapes().size() > 1) {
            items.add(RadialMenu.Item.branch("Форма", Palette.BORDER, shapeSubmenu(cabId, effective)));
        }
        // "Угол" — только если ФАКТИЧЕСКАЯ форма ячейки (с учётом переопределения)
        // вообще непрямоугольная: прямоугольнику ориентировать нечего (Task #92/v1.5).
        CabinetShape effectiveShape = cab.getShapeOverride() != null ? cab.getShapeOverride()
                : (effective != null ? effective.getShape() : null);
        if (effectiveShape != null && effectiveShape != CabinetShape.RECTANGLE) {
            items.add(RadialMenu.Item.branch("Угол", Palette.ACCENT, rotationSubmenu(cabId, cab)));
        }
        items.add(RadialMenu.Item.branch("Тип", Palette.ACCENT, typeSubmenu(cabId, scr)));

        Point screenPt = e.getLocationOnScreen();
        radialMenuActive = true;
        // onClose срабатывает синхронно ВНУТРИ обработки того же события мыши, что
        // его вызвало (например, «свежий» клик по пункту подменю мимо самого
        // popup-окна — RadialMenu трактует его и как выбор/отмену, и это же событие
        // AWT следом доставляет и сюда, в ShapeEditorPanel, раз popup его не перехватил).
        // Если снять radialMenuActive немедленно, this же клик пройдёт проверку
        // (флаг уже false) и переключит видимость ячейки. SwingUtilities.invokeLater
        // откладывает снятие флага на СЛЕДУЮЩий проход цикла событий — защита остаётся
        // активной до конца обработки текущего клика, снимается только к следующему.
        RadialMenu.show(this, screenPt.x, screenPt.y, items,
                () -> SwingUtilities.invokeLater(() -> radialMenuActive = false));
    }

    private List<RadialMenu.Item> shapeSubmenu(String cabId, CabinetType effective) {
        List<RadialMenu.Item> items = new ArrayList<>();
        items.add(RadialMenu.Item.leaf("По умолчанию", Palette.PHASE_NONE,
                () -> applyShape(cabId, null)));
        // Только формы, физически возможные для ЭТОГО типа кабинета (см. Task #79/v1.4) —
        // раньше здесь были все 4 формы всегда, независимо от типа кабинета.
        for (CabinetShape shape : effective.getAllowedShapes()) {
            items.add(RadialMenu.Item.leaf(shape.getLabel(), Palette.signalColor(shape.ordinal()),
                    () -> applyShape(cabId, shape)));
        }
        return items;
    }

    /** 5 пунктов (Task #92/v1.5): "По умолчанию" (снять переопределение — угол типа
     *  кабинета из библиотеки), 4 стандартных угла с шагом в четверть оборота, и
     *  "Другое…" — свободный ввод произвольного угла (например, 45° для нестандартной
     *  инсталляции), не ограниченный четырьмя пресетами. */
    private List<RadialMenu.Item> rotationSubmenu(String cabId, CabinetInstance cab) {
        List<RadialMenu.Item> items = new ArrayList<>();
        items.add(RadialMenu.Item.leaf("По умолчанию", Palette.PHASE_NONE,
                () -> applyRotation(cabId, null)));
        for (int deg : new int[]{0, 90, 180, 270}) {
            items.add(RadialMenu.Item.leaf(deg + "°", Palette.signalColor(deg / 90),
                    () -> applyRotation(cabId, deg)));
        }
        items.add(RadialMenu.Item.leaf("Другое…", Palette.MUTED, () -> promptCustomRotation(cabId, cab)));
        return items;
    }

    private void promptCustomRotation(String cabId, CabinetInstance cab) {
        Integer current = cab.getRotationOverride();
        String initial = current != null ? String.valueOf(current) : "0";
        String input = JOptionPane.showInputDialog(this, "Угол, градусы (0° — прямой угол слева-снизу,"
                + " далее по часовой стрелке):", initial);
        if (input == null) {
            return;
        }
        try {
            int deg = (int) Math.round(Double.parseDouble(input.trim().replace(',', '.')));
            applyRotation(cabId, deg);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Введите число", "Проверка данных", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void applyRotation(String cabId, Integer rotationDeg) {
        model.setCabinetRotationOverride(cabId, rotationDeg);
    }

    private List<RadialMenu.Item> typeSubmenu(String cabId, Screen scr) {
        List<RadialMenu.Item> items = new ArrayList<>();
        List<CabinetType> types = model.getCabinetTypes();
        items.add(RadialMenu.Item.leaf("По умолчанию", Palette.PHASE_NONE,
                () -> applyType(cabId, null)));
        for (CabinetType t : types) {
            // Тот же typeColor(...), что и при отрисовке ячеек — иначе цвет пункта
            // меню и итоговый цвет закрашенной ячейки расходятся.
            items.add(RadialMenu.Item.leaf(t.getName(), typeColor(types, t),
                    () -> applyType(cabId, t.getId())));
        }
        return items;
    }

    private void applyType(String cabId, String typeId) {
        try {
            model.setCabinetTypeOverride(cabId, typeId);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyShape(String cabId, CabinetShape shape) {
        model.setCabinetShapeOverride(cabId, shape);
    }

    @Override
    public Dimension getPreferredSize() {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return new Dimension(120, 60);
        }
        autoFitIfNeeded(scr);
        int cell = cellSize();
        return new Dimension(scr.getCols() * cell + 4, scr.getRows() * cell + 4);
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension d = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, d.height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int cell = cellSize();
        List<CabinetType> types = model.getCabinetTypes();
        for (CabinetInstance c : scr.getCabinets()) {
            int x = c.getColIndex() * cell;
            int y = c.getRowIndex() * cell;
            Color fill;
            if (c.isHidden()) {
                fill = Palette.BG;
            } else if (c.getCabinetTypeId() != null) {
                CabinetType override = model.getWorkspace().cabinetTypeById(c.getCabinetTypeId());
                Color typeColor = override != null ? typeColor(types, override) : Palette.ACCENT;
                fill = blend(Palette.PHASE_NONE, typeColor, 0.55f);
            } else {
                fill = Palette.PHASE_NONE;
            }
            g2.setColor(fill);
            g2.fillRect(x + 1, y + 1, cell - 2, cell - 2);
            g2.setColor(c.getCabinetTypeId() != null ? Palette.ACCENT
                    : (c.isHidden() ? Palette.BORDER : Palette.MUTED));
            g2.drawRect(x + 1, y + 1, cell - 2, cell - 2);

            if (!c.isHidden()) {
                CabinetType effective = effectiveType(c, scr);
                CabinetShape shape = c.getShapeOverride() != null ? c.getShapeOverride()
                        : (effective != null ? effective.getShape() : null);
                if (shape != null && shape != CabinetShape.RECTANGLE) {
                    // Настоящий контур формы (а не декоративная метка в углу, Task
                    // #91/#92/v1.5) — так угол, только что выставленный через
                    // радиальное меню "Угол", сразу виден в этом же окне.
                    double rotationDeg = SchemeRenderer.effectiveRotationDeg(c, effective);
                    g2.setColor(Palette.TEXT);
                    SchemeRenderer.outlineCabinetShape(g2, x + 1, y + 1, cell - 2, cell - 2, shape, rotationDeg);
                }
            }
        }
        g2.dispose();
    }

    /** Стабильный цвет по позиции типа в библиотеке — та же палитра, что и в
     *  радиальном меню, чтобы цвет ячейки совпадал с цветом пункта меню для этого типа. */
    private static Color typeColor(List<CabinetType> types, CabinetType type) {
        int idx = types.indexOf(type);
        return Palette.signalColor(Math.max(0, idx));
    }

    private static Color blend(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() * (1 - t) + b.getRed() * t),
                Math.round(a.getGreen() * (1 - t) + b.getGreen() * t),
                Math.round(a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    /** Подпись типа для ячейки под курсором — используется во внешней подсказке. */
    public static String describeCell(AppModel model, CabinetInstance c) {
        if (c.isHidden()) {
            return "ячейка исключена (нет кабинета)";
        }
        if (c.getCabinetTypeId() != null) {
            CabinetType t = model.getWorkspace().cabinetTypeById(c.getCabinetTypeId());
            return t != null ? "тип: " + t.getName() : "тип экрана";
        }
        return "тип экрана";
    }
}
