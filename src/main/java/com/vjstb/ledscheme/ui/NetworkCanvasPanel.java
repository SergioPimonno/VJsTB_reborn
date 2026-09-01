package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import com.vjstb.ledscheme.model.NetworkDeviceType;
import com.vjstb.ledscheme.model.NetworkLink;
import com.vjstb.ledscheme.model.NetworkLinkWaypoint;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.NetworkDeviceLabels;
import com.vjstb.ledscheme.service.NetworkIpConflicts;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
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
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Холст одной сети Сетевого менеджера (см. {@code NetworkManagerPanel},
 * NETWORK_MANAGER_NOTES.md) — вид сверху, свободное размещение блоков
 * устройств (стиль Cisco Packet Tracer, по прямому указанию пользователя),
 * без штабелирования/коллизий/запаса, в отличие от {@link
 * VehicleLoadCanvasPanel} (та же общая структура — mm→px через {@link
 * #scale()}, {@code Ctrl}+колесо зум, перетаскивание мышью — упрощена там,
 * где для сети эти понятия не нужны). {@code xMm}/{@code yMm} на {@link
 * NetworkDevicePlacement} тут не физические миллиметры, а просто координаты
 * канваса — как и у {@code SchemaNode#getX()}/{@code #getY()}.
 *
 * <p>Устройство отображает ЖИВЬЁМ резолвленное имя/категорию/число портов —
 * по {@link NetworkDevicePlacement#getLinkedSchemaNodeId()} (существующий
 * узел общей схемы сигнала) или {@link NetworkDevicePlacement#getDeviceTypeId()}
 * (новый блок из каталога {@link NetworkDeviceType}), см. {@link
 * #resolveLabel}/{@link #resolveColor}/{@link #effectivePortCount} —
 * переименование узла в схеме, смена цвета/числа портов типа в библиотеке
 * сразу видно здесь, копия не хранится. Число портов у КАТАЛОЖНЫХ устройств
 * — паспортная величина ТИПА ({@link NetworkDeviceType#getPortCount()}), у
 * СВЯЗАННЫХ (нет типа) — {@link NetworkDevicePlacement#getPortCount()}
 * per-instance (редактируется в {@code NetworkDeviceParamsDialog}).
 *
 * <p><b>Порты и связи</b> (запрос пользователя: "нужно уметь рисовать линки и
 * подключать по портам... для подробных схем и сложной маршрутизации") —
 * порты рисуются мелкими квадратами вдоль НИЖНЕГО края блока ({@link
 * #portCenterCanvasUnits}). Клик по порту НАЧИНАЕТ связь (см. {@link
 * #linkingFrom}), клик по порту ДРУГОГО устройства ЗАВЕРШАЕТ её (создаёт
 * {@link NetworkLink}), клик по пустому месту или {@code Esc} отменяет —
 * модель "клик-клик", не перетаскивание (перетаскивание уже занято
 * перемещением блока). Пока связь строится — курсор становится crosshair,
 * начальный порт подсвечивается увеличенным кольцом, порт под курсором (если
 * это валидная цель — другое устройство) подсвечивается тоже, пунктирная
 * линия тянется до курсора (баг-репорт: "непонятно строится ли связь или
 * нет" — раньше подсказывающая линия рисовалась ПОЛНОСТЬЮ ПРОЗРАЧНОЙ из-за
 * бага в конструкторе {@link Color} с альфа-каналом, см. {@link
 * #COLOR_LINK_PENDING}).
 *
 * <p><b>Излом линии связи</b> (баг-репорт: "как в блоксхемах добавить
 * возможность ломать линию") — двойной клик по линии добавляет точку излома
 * (см. {@link NetworkLinkWaypoint}, {@link #insertWaypoint}), точки излома
 * видны/перетаскиваемы только у ВЫДЕЛЕННОЙ связи ({@link #selectedLink},
 * выбирается обычным левым кликом по линии), тот же UX, что у {@code
 * SchemaCanvasPanel} для {@code SchemaEdge#getWaypoints()}. ПКМ по точке —
 * «Убрать точку», ПКМ по линии — «Выпрямить»/«Удалить связь».
 *
 * <p><b>Shift-снаппинг точки излома</b> (запрос пользователя: "для точек
 * излома линий надо добавить снаппинг при shift") — точная копия механики
 * {@code SchemaCanvasPanel#snapWaypointPosition} для {@code
 * SchemaEdge#getWaypoints()}: пока {@code Shift} зажат во время
 * перетаскивания точки, она притягивается к БЛИЖАЙШЕМУ порту любого
 * устройства этой сети (см. {@link #portCenterCanvasUnits}) или к другой
 * точке излома любой связи (не только текущей) — так соседние связи можно
 * выстроить в прямые линии, как в блок-схемах. Сила притяжения и радиус
 * поиска берутся из ТЕХ ЖЕ настроек профиля, что и снаппинг блок-схем
 * ({@link SettingsManager#activeProfile()}{@code
 * .getSnapThresholdPx()/getSnapStrengthPercent()}) — единая настройка на всё
 * приложение, не своя копия для Сетевого менеджера. Без {@code Shift} —
 * перетаскивание свободное, как раньше.
 *
 * <p><b>Shift-снаппинг БЛОКА устройства</b> (запрос пользователя: "для блоков
 * в менеджере сетевом тоже должен работать снаппинг к блокам и существующим
 * узлам излома") — та же механика на перетаскивание самого блока (не только
 * точки излома выше): {@link #snapDevicePosition} — точная копия {@code
 * SchemaCanvasPanel#snapPosition} (три кандидата на измерение — левый край/
 * центр/правый край по X, верх/центр/низ по Y, сравниваются с такими же
 * координатами ДРУГИХ блоков), плюс, по тому же запросу, ЕЩЁ один источник
 * кандидатов — точки излома ЛЮБОЙ связи (без ширины/высоты, единственная
 * точка, а не тройка). Те же настройки профиля, та же магентовая
 * направляющая линия, тот же {@code Esc}-независимый сброс на отпускание
 * кнопки мыши.
 *
 * <p><b>Цвет связи — по сети</b> (баг-репорт: "цвет линии должен зависеть от
 * цвета выбранного для сети") — см. {@link #setLinkColor}, вызывается из
 * {@code NetworkManagerPanel} при выборе сети в списке; сама панель не знает
 * о {@code Network}, только получает готовый цвет.
 *
 * <p>ПКМ по блоку — {@link JPopupMenu} (Пинг/Открыть веб-интерфейс/Параметры
 * сети/Убрать из сети). «Пинг…» открывает {@link NetworkPingDialog} — живой,
 * непрерывный (не разовые 4 пакета), см. её class-javadoc.
 *
 * <p><b>Добавление устройств</b> — кнопками ИЛИ drag-n-drop из палитры
 * ({@code NetworkManagerPanel}, два {@link DataFlavor}: {@link
 * #SCHEMA_NODE_FLAVOR}/{@link #DEVICE_TYPE_FLAVOR}) — эта панель НЕ ставит
 * свой {@code TransferHandler} сама, только экспонирует {@link
 * #pxToCanvas}/{@link #addLinkedDeviceAt}/{@link #addCatalogDeviceAt} как
 * публичный контракт (вызывающая сторона настраивает приём drop). */
public class NetworkCanvasPanel extends JPanel {

    private static final int PADDING = 24;
    static final double DEVICE_W = 130;
    static final double DEVICE_H = 56;
    private static final int PORT_HIT_RADIUS_PX = 7;
    private static final int PORT_SIZE_PX = 8;
    private static final int PORT_HIGHLIGHT_SIZE_PX = 14;
    private static final double LINK_HIT_THRESHOLD_PX = 5;
    private static final int WAYPOINT_HANDLE_PX = 8;
    private static final Color COLOR_SWITCH = new Color(0x2e9c4f);
    private static final Color COLOR_ROUTER = new Color(0xb5651d);
    private static final Color COLOR_AP = new Color(0x8250df);
    private static final Color COLOR_FIREWALL = new Color(0xcf222e);
    private static final Color COLOR_SERVER = new Color(0x57606a);
    private static final Color COLOR_OTHER = new Color(0x3d444d);
    private static final Color COLOR_LINKED = new Color(0x1f6feb);
    private static final Color COLOR_PORT = new Color(0xe6edf3);
    /** Цвет линии связи по умолчанию — используется, пока {@link
     *  Network#getColor()} не задан (сети старых проектов, ещё не выбравшие
     *  цвет) — см. {@link #setLinkColor}. Package-visible: {@code
     *  NetworkManagerPanel} использует его же как fallback для значка-образца
     *  цвета в списке сетей. */
    static final Color DEFAULT_LINK_COLOR = new Color(0xf0b429);
    /** Цвет связи, которая ЕЩЁ СТРОИТСЯ (тянется за курсором) — сознательно НЕ
     *  зависит от цвета сети (в отличие от уже созданных связей), чтобы
     *  "процесс построения" визуально всегда читался одинаково независимо от
     *  того, какого цвета сама сеть. БАГ ПЕРВОЙ ВЕРСИИ: было {@code new
     *  Color(0xf0b429, true)} — конструктор с {@code hasAlpha=true}
     *  трактует 0xf0b429 как ARGB, где альфа-байт (биты 24-31) НУЛЕВОЙ (0xf0b429
     *  как 32-битное число — это 0x00F0B429) — линия рисовалась ПОЛНОСТЬЮ
     *  ПРОЗРАЧНОЙ, отсюда баг-репорт "непонятно строится ли связь". Просто
     *  непрозрачный цвет без флага alpha. */
    private static final Color COLOR_LINK_PENDING = new Color(0xffd23f);
    private static final Color COLOR_PORT_TARGET_HOVER = new Color(0x3fb950);
    /** Обводка/подпись IP у устройства с конфликтующим адресом (см. {@link
     *  NetworkIpConflicts}) — намеренно яркий "ошибочный" красный, заметно
     *  отличается от {@link #COLOR_FIREWALL} (это категория оборудования, не
     *  предупреждение) и от {@link #COLOR_LINK_PENDING} (жёлтый — процесс, а не
     *  ошибка). */
    private static final Color COLOR_IP_CONFLICT = new Color(0xff5c5c);

    private static final double SCALE = 1.0;
    private static final int REF_VIEWPORT_W = 640;
    private static final int REF_VIEWPORT_H = 360;
    private static final double ZOOM_MIN = 0.4;
    private static final double ZOOM_MAX = 3.0;
    private static final double ZOOM_WHEEL_STEP = 1.15;

    /** Флейвор перетаскивания {@link SchemaNode} из палитры узлов схемы — обмен
     *  только внутри одной JVM, тот же приём, что {@code
     *  VehicleLoadCanvasPanel#CASE_TYPE_FLAVOR}. */
    static final DataFlavor SCHEMA_NODE_FLAVOR;
    /** Флейвор перетаскивания {@link NetworkDeviceType} из палитры каталога. */
    static final DataFlavor DEVICE_TYPE_FLAVOR;

    static {
        try {
            SCHEMA_NODE_FLAVOR = new DataFlavor(
                    DataFlavor.javaJVMLocalObjectMimeType + ";class=" + SchemaNode.class.getName());
            DEVICE_TYPE_FLAVOR = new DataFlavor(
                    DataFlavor.javaJVMLocalObjectMimeType + ";class=" + NetworkDeviceType.class.getName());
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private record PortHit(NetworkDevicePlacement device, int port) {
    }

    private record WaypointHit(NetworkLink link, int index) {
    }

    private final AppModel model;
    private final SettingsManager settings;
    private List<NetworkDevicePlacement> devices = new ArrayList<>();
    private List<NetworkLink> links = new ArrayList<>();
    private Color linkColor = DEFAULT_LINK_COLOR;
    private NetworkDevicePlacement selected;
    private NetworkLink selectedLink;
    private NetworkDevicePlacement dragging;
    private double dragOffXpx, dragOffYpx;
    private NetworkLink draggingWaypointLink;
    private int draggingWaypointIndex = -1;
    private PortHit linkingFrom;
    private Point linkCursorPoint;
    private PortHit hoveredPort;
    private Runnable onChanged = () -> { };
    private double zoom = 1.0;
    /** Направляющие линии Shift-снаппинга точки излома (см. class-javadoc) — в
     *  КООРДИНАТАХ КАНВАСА (не px), {@code null} = сейчас ни к чему не привязано,
     *  тот же приём, что {@code SchemaCanvasPanel#snapGuideX/snapGuideY}. */
    private Double snapGuideX;
    private Double snapGuideY;

    public NetworkCanvasPanel(AppModel model, SettingsManager settings) {
        this.model = model;
        this.settings = settings;
        setBackground(Palette.BG);
        setFocusable(true);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();

                if (SwingUtilities.isRightMouseButton(e)) {
                    NetworkDevicePlacement deviceHit = deviceAt(e.getPoint());
                    if (deviceHit != null) {
                        selected = deviceHit;
                        selectedLink = null;
                        repaint();
                        showDeviceMenu(deviceHit, e.getX(), e.getY());
                        return;
                    }
                    WaypointHit wpHit = waypointAt(e.getPoint());
                    if (wpHit != null) {
                        showWaypointMenu(wpHit.link(), wpHit.index(), e.getX(), e.getY());
                        return;
                    }
                    NetworkLink linkHit = linkAt(e.getPoint());
                    if (linkHit != null) {
                        selected = null;
                        selectedLink = linkHit;
                        repaint();
                        showLinkMenu(linkHit, e.getX(), e.getY());
                    }
                    return;
                }

                PortHit portHit = portAt(e.getPoint());
                if (portHit != null) {
                    if (linkingFrom == null) {
                        linkingFrom = portHit;
                        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    } else if (linkingFrom.device() != portHit.device()) {
                        createLink(linkingFrom, portHit);
                        linkingFrom = null;
                        setCursor(Cursor.getDefaultCursor());
                    } else {
                        linkingFrom = null; // клик по порту того же устройства -- отмена
                        setCursor(Cursor.getDefaultCursor());
                    }
                    repaint();
                    return;
                }
                if (linkingFrom != null) {
                    linkingFrom = null; // клик по пустому месту во время рисования связи -- отмена
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                    return;
                }

                WaypointHit wpHit = waypointAt(e.getPoint());
                if (wpHit != null) {
                    draggingWaypointLink = wpHit.link();
                    draggingWaypointIndex = wpHit.index();
                    return;
                }

                NetworkDevicePlacement hit = deviceAt(e.getPoint());
                if (hit != null) {
                    selected = hit;
                    selectedLink = null;
                    double s = scale();
                    double px = PADDING + hit.getXMm() * s;
                    double py = PADDING + hit.getYMm() * s;
                    dragOffXpx = e.getX() - px;
                    dragOffYpx = e.getY() - py;
                    dragging = hit;
                    repaint();
                    return;
                }

                selected = null;
                selectedLink = linkAt(e.getPoint());
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)
                        && deviceAt(e.getPoint()) == null && portAt(e.getPoint()) == null) {
                    NetworkLink linkHit = linkAt(e.getPoint());
                    if (linkHit != null) {
                        insertWaypoint(linkHit, e.getPoint());
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging != null) {
                    double s = scale();
                    double candidateX = Math.max(0, (e.getX() - dragOffXpx - PADDING) / s);
                    double candidateY = Math.max(0, (e.getY() - dragOffYpx - PADDING) / s);
                    if (e.isShiftDown()) {
                        double[] snapped = snapDevicePosition(dragging, candidateX, candidateY);
                        candidateX = snapped[0];
                        candidateY = snapped[1];
                    } else {
                        snapGuideX = null;
                        snapGuideY = null;
                    }
                    dragging.setXMm(candidateX);
                    dragging.setYMm(candidateY);
                    repaint();
                    return;
                }
                if (draggingWaypointLink != null) {
                    double[] c = pxToCanvas(e.getPoint());
                    double candidateX = c[0];
                    double candidateY = c[1];
                    if (e.isShiftDown()) {
                        double[] snapped = snapWaypointPosition(draggingWaypointLink, draggingWaypointIndex,
                                candidateX, candidateY);
                        candidateX = snapped[0];
                        candidateY = snapped[1];
                    } else {
                        snapGuideX = null;
                        snapGuideY = null;
                    }
                    NetworkLinkWaypoint w = draggingWaypointLink.getWaypoints().get(draggingWaypointIndex);
                    w.setX(candidateX);
                    w.setY(candidateY);
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                PortHit hover = portAt(e.getPoint());
                if (linkingFrom != null) {
                    linkCursorPoint = e.getPoint();
                    hoveredPort = hover != null && hover.device() != linkingFrom.device() ? hover : null;
                    repaint();
                } else if (hover != null) {
                    setToolTipText("Порт " + hover.port());
                } else {
                    setToolTipText(null);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragging != null) {
                    dragging = null;
                    snapGuideX = null;
                    snapGuideY = null;
                    onChanged.run();
                    repaint();
                }
                if (draggingWaypointLink != null) {
                    draggingWaypointLink = null;
                    draggingWaypointIndex = -1;
                    snapGuideX = null;
                    snapGuideY = null;
                    onChanged.run();
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && linkingFrom != null) {
                    linkingFrom = null;
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                }
            }
        });

        addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                double factor = e.getWheelRotation() < 0 ? ZOOM_WHEEL_STEP : 1 / ZOOM_WHEEL_STEP;
                setZoom(zoom * factor);
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

    /** Переключает канвас на другую сеть (см. {@code NetworkManagerPanel}) — списки
     *  устройств/связей берутся напрямую из {@code network.getDevices()}/{@code
     *  getLinks()} (та же ссылка, не копия — мутации канваса сразу отражаются в
     *  модели, персист по {@link #onChanged}). */
    public void setDevices(List<NetworkDevicePlacement> devices) {
        this.devices = devices != null ? devices : new ArrayList<>();
        selected = null;
        selectedLink = null;
        dragging = null;
        linkingFrom = null;
        revalidate();
        repaint();
    }

    public void setLinks(List<NetworkLink> links) {
        this.links = links != null ? links : new ArrayList<>();
        repaint();
    }

    /** Цвет линий связи ТЕКУЩЕЙ сети (см. class-javadoc) — {@code null} сбрасывает
     *  на {@link #DEFAULT_LINK_COLOR}. */
    public void setLinkColor(Color color) {
        this.linkColor = color != null ? color : DEFAULT_LINK_COLOR;
        repaint();
    }

    public List<NetworkDevicePlacement> getDevices() {
        return devices;
    }

    public List<NetworkLink> getLinks() {
        return links;
    }

    private double scale() {
        return SCALE * zoom;
    }

    public void setZoom(double newZoom) {
        double clamped = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, newZoom));
        if (clamped == zoom) {
            return;
        }
        zoom = clamped;
        revalidate();
        repaint();
    }

    public void zoomBy(double factor) {
        setZoom(zoom * factor);
    }

    /** Экранные px (координаты ЭТОЙ панели, включая {@link #PADDING}) → координаты
     *  канваса — для drag-n-drop импорта из палитры (см. {@code
     *  NetworkManagerPanel}), тот же приём, что {@code
     *  VehicleLoadCanvasPanel#pxToMm}. */
    public double[] pxToCanvas(Point pt) {
        double s = scale();
        return new double[]{(pt.x - PADDING) / s, (pt.y - PADDING) / s};
    }

    /** Добавляет устройство, связанное с существующим узлом схемы сигнала — стартует
     *  в углу канваса, пользователь перетаскивает на нужное место сам (кнопка
     *  «Добавить в сеть» в палитре). Для drag-n-drop прямо на нужное место — см.
     *  {@link #addLinkedDeviceAt}. */
    public void addLinkedDevice(SchemaNode node) {
        addLinkedDeviceAt(node, nextSpotX(), 0);
    }

    public void addLinkedDeviceAt(SchemaNode node, double x, double y) {
        NetworkDevicePlacement p = new NetworkDevicePlacement();
        p.setLinkedSchemaNodeId(node.getId());
        placeAndAdd(p, x, y);
    }

    /** Добавляет НОВОЕ устройство из каталога {@link NetworkDeviceType} — не связано
     *  ни с чем на общей схеме. Число портов сразу берётся из типа (см. {@link
     *  #effectivePortCount}, паспортная величина — но само поле {@code
     *  NetworkDevicePlacement.portCount} для каталожных устройств не используется
     *  при отрисовке, инициализация тут — просто разумное стартовое значение на
     *  случай, если тип потом удалят из библиотеки). Для drag-n-drop — см. {@link
     *  #addCatalogDeviceAt}. */
    public void addCatalogDevice(NetworkDeviceType type) {
        addCatalogDeviceAt(type, nextSpotX(), 0);
    }

    public void addCatalogDeviceAt(NetworkDeviceType type, double x, double y) {
        NetworkDevicePlacement p = new NetworkDevicePlacement();
        p.setDeviceTypeId(type.getId());
        p.setPortCount(Math.max(1, type.getPortCount()));
        placeAndAdd(p, x, y);
    }

    /** Добавляет НОВОЕ устройство БЕЗ связи со схемой и БЕЗ каталожного типа —
     *  только с уже известным IP (запрос пользователя: "сканировать диапазон
     *  IP для обнаружения устройств с неизвестными адресами", см. {@code
     *  ui.NetworkScanDialog}). Стартует в углу канваса — тот же приём, что
     *  {@link #addLinkedDevice}/{@link #addCatalogDevice}, вызывающая
     *  сторона (кнопка «Добавить как устройство», не drag-n-drop) не задаёт
     *  явную позицию. */
    public void addDiscoveredDevice(String ipAddress) {
        addDiscoveredDeviceAt(ipAddress, nextSpotX(), 0);
    }

    /** {@code customLabel} остаётся пустым — {@link #resolveLabel} покажет
     *  заглушку "(устройство)", пользователь переименовывает и/или задаёт тип
     *  позже через ПКМ → «Параметры сети…» (число портов там останется 4 по
     *  умолчанию — источник для него такой же, как у любого связанного
     *  устройства без каталожного типа, см. {@code
     *  NetworkDevicePlacement#portCount} javadoc). */
    public void addDiscoveredDeviceAt(String ipAddress, double x, double y) {
        NetworkDevicePlacement p = new NetworkDevicePlacement();
        p.setIpAddress(ipAddress);
        placeAndAdd(p, x, y);
    }

    private void placeAndAdd(NetworkDevicePlacement p, double x, double y) {
        p.setXMm(Math.max(0, x));
        p.setYMm(Math.max(0, y));
        devices.add(p);
        selected = p;
        selectedLink = null;
        onChanged.run();
        repaint();
    }

    private double nextSpotX() {
        return devices.size() * (DEVICE_W + 30);
    }

    // ---- ПКМ меню устройства/связи/точки излома ----

    private void showDeviceMenu(NetworkDevicePlacement device, int x, int y) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem ping = new JMenuItem("Пинг…");
        ping.setEnabled(!device.getIpAddress().isBlank());
        ping.addActionListener(e -> pingDevice(device));
        menu.add(ping);

        JMenuItem openWeb = new JMenuItem("Открыть веб-интерфейс");
        // Баг-репорт: "добавь галочку есть ли веб интерфейс, если стоит галочка --
        // можно управлять через него" -- непустой URL сам по себе НЕ означает
        // управляемость (см. NetworkDevicePlacement#hasWebInterface javadoc), пункт
        // активен только при обоих условиях сразу.
        openWeb.setEnabled(device.isHasWebInterface() && !device.getWebInterfaceUrl().isBlank());
        openWeb.addActionListener(e -> UiKit.openUrl(this, device.getWebInterfaceUrl()));
        menu.add(openWeb);

        JMenuItem params = new JMenuItem("Параметры сети…");
        params.addActionListener(e -> editParams(device));
        menu.add(params);

        menu.addSeparator();
        JMenuItem remove = new JMenuItem("Убрать из сети");
        remove.addActionListener(e -> removeDevice(device));
        menu.add(remove);

        menu.show(this, x, y);
    }

    private void removeDevice(NetworkDevicePlacement device) {
        devices.remove(device);
        links.removeIf(l -> l.getFromDeviceId().equals(device.getId()) || l.getToDeviceId().equals(device.getId()));
        if (selected == device) {
            selected = null;
        }
        onChanged.run();
        repaint();
    }

    private void showLinkMenu(NetworkLink link, int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem straighten = new JMenuItem("Выпрямить");
        straighten.setEnabled(!link.getWaypoints().isEmpty());
        straighten.addActionListener(e -> {
            link.getWaypoints().clear();
            onChanged.run();
            repaint();
        });
        menu.add(straighten);
        JMenuItem remove = new JMenuItem("Удалить связь");
        remove.addActionListener(e -> {
            links.remove(link);
            if (selectedLink == link) {
                selectedLink = null;
            }
            onChanged.run();
            repaint();
        });
        menu.add(remove);
        menu.show(this, x, y);
    }

    private void showWaypointMenu(NetworkLink link, int index, int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem remove = new JMenuItem("Убрать точку");
        remove.addActionListener(e -> {
            link.getWaypoints().remove(index);
            onChanged.run();
            repaint();
        });
        menu.add(remove);
        menu.show(this, x, y);
    }

    private void createLink(PortHit from, PortHit to) {
        NetworkLink link = new NetworkLink();
        link.setFromDeviceId(from.device().getId());
        link.setFromPort(from.port());
        link.setToDeviceId(to.device().getId());
        link.setToPort(to.port());
        links.add(link);
        selectedLink = link;
        onChanged.run();
        repaint();
    }

    /** Вставляет новую точку излома в связь на месте клика — вставляется в список
     *  ровно на позицию отрезка ломаной, к которому клик ближе всего (тот же
     *  алгоритм, что {@code SchemaCanvasPanel#insertWaypoint}). */
    private void insertWaypoint(NetworkLink link, Point p) {
        List<double[]> pts = routePoints(link);
        if (pts == null) {
            return;
        }
        double s = scale();
        int insertAt = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < pts.size() - 1; i++) {
            double ax = PADDING + pts.get(i)[0] * s, ay = PADDING + pts.get(i)[1] * s;
            double bx = PADDING + pts.get(i + 1)[0] * s, by = PADDING + pts.get(i + 1)[1] * s;
            double d = distanceToSegment(p.x, p.y, ax, ay, bx, by);
            if (d < best) {
                best = d;
                insertAt = i;
            }
        }
        double[] c = pxToCanvas(p);
        link.getWaypoints().add(insertAt, new NetworkLinkWaypoint(c[0], c[1]));
        selected = null;
        selectedLink = link;
        onChanged.run();
        repaint();
    }

    /** Shift-снаппинг перетаскиваемого БЛОКА устройства (см. class-javadoc) — точная
     *  копия {@code SchemaCanvasPanel#snapPosition} (та же тройка кандидатов на
     *  измерение: левый край/центр/правый край по X, верх/центр/низ по Y,
     *  сравниваются с такими же координатами ДРУГИХ блоков) плюс, по прямому
     *  запросу пользователя ("снаппинг... к блокам и существующим узлам
     *  излома"), ЕЩЁ один источник кандидатов — точки излома ЛЮБОЙ связи (без
     *  ширины/высоты, single-point, как в {@link #snapWaypointPosition}, только
     *  тут они выступают целью, а не перетаскиваемой точкой). Побочный эффект —
     *  выставляет {@link #snapGuideX}/{@link #snapGuideY}. */
    private double[] snapDevicePosition(NetworkDevicePlacement moving, double candidateX, double candidateY) {
        snapGuideX = null;
        snapGuideY = null;
        double threshold = settings.activeProfile().getSnapThresholdPx();
        int strength = settings.activeProfile().getSnapStrengthPercent();
        double[] xCandidates = {candidateX, candidateX + DEVICE_W / 2, candidateX + DEVICE_W};
        double[] yCandidates = {candidateY, candidateY + DEVICE_H / 2, candidateY + DEVICE_H};
        double bestDx = threshold, bestDy = threshold;
        double snappedX = candidateX, snappedY = candidateY;

        for (NetworkDevicePlacement other : devices) {
            if (other == moving) {
                continue;
            }
            double[] oxs = {other.getXMm(), other.getXMm() + DEVICE_W / 2, other.getXMm() + DEVICE_W};
            double[] oys = {other.getYMm(), other.getYMm() + DEVICE_H / 2, other.getYMm() + DEVICE_H};
            for (double ox : oxs) {
                for (double xc : xCandidates) {
                    double d = Math.abs(xc - ox);
                    if (d < bestDx) {
                        bestDx = d;
                        snappedX = SnapMath.blend(candidateX, candidateX + (ox - xc), strength);
                        snapGuideX = ox;
                    }
                }
            }
            for (double oy : oys) {
                for (double yc : yCandidates) {
                    double d = Math.abs(yc - oy);
                    if (d < bestDy) {
                        bestDy = d;
                        snappedY = SnapMath.blend(candidateY, candidateY + (oy - yc), strength);
                        snapGuideY = oy;
                    }
                }
            }
        }
        for (NetworkLink link : links) {
            for (NetworkLinkWaypoint wp : link.getWaypoints()) {
                for (double xc : xCandidates) {
                    double d = Math.abs(xc - wp.getX());
                    if (d < bestDx) {
                        bestDx = d;
                        snappedX = SnapMath.blend(candidateX, candidateX + (wp.getX() - xc), strength);
                        snapGuideX = wp.getX();
                    }
                }
                for (double yc : yCandidates) {
                    double d = Math.abs(yc - wp.getY());
                    if (d < bestDy) {
                        bestDy = d;
                        snappedY = SnapMath.blend(candidateY, candidateY + (wp.getY() - yc), strength);
                        snapGuideY = wp.getY();
                    }
                }
            }
        }
        return new double[]{snappedX, snappedY};
    }

    /** Shift-снаппинг перетаскиваемой точки излома (см. class-javadoc) — точная
     *  копия {@code SchemaCanvasPanel#snapWaypointPosition}, только кандидаты
     *  свои: центры портов устройств этой сети (вместо краёв/центров узлов
     *  схемы — у портов нет ширины/высоты, только точка) и точки излома ЛЮБОЙ
     *  связи, включая другие связи (кроме самой перетаскиваемой точки). Побочный
     *  эффект — выставляет {@link #snapGuideX}/{@link #snapGuideY} (координаты
     *  ЦЕЛИ, не смешанные) для {@link #paintComponent}. */
    private double[] snapWaypointPosition(NetworkLink movingLink, int movingIndex,
                                           double candidateX, double candidateY) {
        snapGuideX = null;
        snapGuideY = null;
        double threshold = settings.activeProfile().getSnapThresholdPx();
        int strength = settings.activeProfile().getSnapStrengthPercent();
        double bestDx = threshold, bestDy = threshold;
        double snappedX = candidateX, snappedY = candidateY;

        for (NetworkDevicePlacement device : devices) {
            int n = effectivePortCount(device);
            for (int port = 1; port <= n; port++) {
                double[] c = portCenterCanvasUnits(device, port);
                double dx = Math.abs(candidateX - c[0]);
                if (dx < bestDx) {
                    bestDx = dx;
                    snappedX = SnapMath.blend(candidateX, c[0], strength);
                    snapGuideX = c[0];
                }
                double dy = Math.abs(candidateY - c[1]);
                if (dy < bestDy) {
                    bestDy = dy;
                    snappedY = SnapMath.blend(candidateY, c[1], strength);
                    snapGuideY = c[1];
                }
            }
        }
        for (NetworkLink link : links) {
            List<NetworkLinkWaypoint> wps = link.getWaypoints();
            for (int i = 0; i < wps.size(); i++) {
                if (link == movingLink && i == movingIndex) {
                    continue;
                }
                NetworkLinkWaypoint wp = wps.get(i);
                double dx = Math.abs(candidateX - wp.getX());
                if (dx < bestDx) {
                    bestDx = dx;
                    snappedX = SnapMath.blend(candidateX, wp.getX(), strength);
                    snapGuideX = wp.getX();
                }
                double dy = Math.abs(candidateY - wp.getY());
                if (dy < bestDy) {
                    bestDy = dy;
                    snappedY = SnapMath.blend(candidateY, wp.getY(), strength);
                    snapGuideY = wp.getY();
                }
            }
        }
        return new double[]{snappedX, snappedY};
    }

    private void pingDevice(NetworkDevicePlacement device) {
        String host = device.getIpAddress().trim();
        if (host.isEmpty()) {
            return;
        }
        new NetworkPingDialog((java.awt.Window) SwingUtilities.getWindowAncestor(this), host).setVisible(true);
    }

    private void editParams(NetworkDevicePlacement device) {
        Integer typePortCount = null;
        if (device.getDeviceTypeId() != null) {
            NetworkDeviceType type = model.getWorkspace().networkDeviceTypeById(device.getDeviceTypeId());
            if (type != null) {
                typePortCount = Math.max(1, type.getPortCount());
            }
        }
        NetworkDeviceParamsDialog dlg = new NetworkDeviceParamsDialog(
                (java.awt.Window) SwingUtilities.getWindowAncestor(this), device, resolveLabel(device), typePortCount);
        if (dlg.showDialog()) {
            onChanged.run();
            repaint();
        }
    }

    // ---- резолв живых данных ----

    /** Делегирует {@link NetworkDeviceLabels} (вынесено оттуда, второй
     *  потребитель — {@code NetworkAddressTableDialog}, см. её/его javadoc). */
    private String resolveLabel(NetworkDevicePlacement p) {
        return NetworkDeviceLabels.resolveLabel(p, model);
    }

    private NetworkDevicePlacement findDeviceById(String id) {
        for (NetworkDevicePlacement p : devices) {
            if (p.getId().equals(id)) {
                return p;
            }
        }
        return null;
    }

    private Color resolveColor(NetworkDevicePlacement p) {
        if (p.getDeviceTypeId() != null) {
            NetworkDeviceType type = model.getWorkspace().networkDeviceTypeById(p.getDeviceTypeId());
            if (type != null && type.getCategory() != null) {
                return switch (type.getCategory()) {
                    case SWITCH -> COLOR_SWITCH;
                    case ROUTER -> COLOR_ROUTER;
                    case ACCESS_POINT -> COLOR_AP;
                    case FIREWALL -> COLOR_FIREWALL;
                    case SERVER -> COLOR_SERVER;
                    case OTHER -> COLOR_OTHER;
                };
            }
        }
        return COLOR_LINKED;
    }

    /** Действующее число портов — из каталожного типа для устройств оттуда,
     *  иначе из собственного поля размещения (см. class-javadoc). */
    private int effectivePortCount(NetworkDevicePlacement p) {
        if (p.getDeviceTypeId() != null) {
            NetworkDeviceType type = model.getWorkspace().networkDeviceTypeById(p.getDeviceTypeId());
            if (type != null) {
                return Math.max(1, type.getPortCount());
            }
        }
        return Math.max(1, p.getPortCount());
    }

    // ---- геометрия/хит-тесты ----

    /** Центр порта {@code portIndex1Based} (1..effectivePortCount) в КООРДИНАТАХ
     *  КАНВАСА (до масштаба/PADDING) — вдоль нижнего края блока, равномерно
     *  распределены. */
    private double[] portCenterCanvasUnits(NetworkDevicePlacement p, int portIndex1Based) {
        int n = effectivePortCount(p);
        double spacing = DEVICE_W / (n + 1);
        return new double[]{p.getXMm() + spacing * portIndex1Based, p.getYMm() + DEVICE_H};
    }

    private PortHit portAt(Point pt) {
        double s = scale();
        for (int i = devices.size() - 1; i >= 0; i--) {
            NetworkDevicePlacement p = devices.get(i);
            int n = effectivePortCount(p);
            for (int port = 1; port <= n; port++) {
                double[] c = portCenterCanvasUnits(p, port);
                int px = (int) (PADDING + c[0] * s);
                int py = (int) (PADDING + c[1] * s);
                if (Math.abs(pt.x - px) <= PORT_HIT_RADIUS_PX && Math.abs(pt.y - py) <= PORT_HIT_RADIUS_PX) {
                    return new PortHit(p, port);
                }
            }
        }
        return null;
    }

    private NetworkDevicePlacement deviceAt(Point pt) {
        double s = scale();
        for (int i = devices.size() - 1; i >= 0; i--) {
            NetworkDevicePlacement p = devices.get(i);
            int x = (int) (PADDING + p.getXMm() * s);
            int y = (int) (PADDING + p.getYMm() * s);
            int w = (int) (DEVICE_W * s);
            int h = (int) (DEVICE_H * s);
            if (pt.x >= x && pt.x <= x + w && pt.y >= y && pt.y <= y + h) {
                return p;
            }
        }
        return null;
    }

    /** Полный маршрут связи в КООРДИНАТАХ КАНВАСА: порт-источник, точки излома по
     *  порядку, порт-приёмник — {@code null}, если один из концов ссылается на
     *  уже удалённое устройство (защитно, не должно происходить в норме — {@link
     *  #removeDevice} чистит связи вместе с устройством). */
    private List<double[]> routePoints(NetworkLink link) {
        NetworkDevicePlacement from = findDeviceById(link.getFromDeviceId());
        NetworkDevicePlacement to = findDeviceById(link.getToDeviceId());
        if (from == null || to == null) {
            return null;
        }
        List<double[]> pts = new ArrayList<>();
        pts.add(portCenterCanvasUnits(from, link.getFromPort()));
        for (NetworkLinkWaypoint w : link.getWaypoints()) {
            pts.add(new double[]{w.getX(), w.getY()});
        }
        pts.add(portCenterCanvasUnits(to, link.getToPort()));
        return pts;
    }

    private NetworkLink linkAt(Point pt) {
        double s = scale();
        for (NetworkLink link : links) {
            List<double[]> pts = routePoints(link);
            if (pts == null) {
                continue;
            }
            for (int i = 0; i < pts.size() - 1; i++) {
                double ax = PADDING + pts.get(i)[0] * s, ay = PADDING + pts.get(i)[1] * s;
                double bx = PADDING + pts.get(i + 1)[0] * s, by = PADDING + pts.get(i + 1)[1] * s;
                if (distanceToSegment(pt.x, pt.y, ax, ay, bx, by) <= LINK_HIT_THRESHOLD_PX) {
                    return link;
                }
            }
        }
        return null;
    }

    /** Точка излома под курсором — попадание только у ВЫДЕЛЕННОЙ связи (см.
     *  {@link #selectedLink}), т.к. только её точки вообще видны/кликабельны. */
    private WaypointHit waypointAt(Point pt) {
        if (selectedLink == null) {
            return null;
        }
        double s = scale();
        List<NetworkLinkWaypoint> wps = selectedLink.getWaypoints();
        for (int i = 0; i < wps.size(); i++) {
            NetworkLinkWaypoint w = wps.get(i);
            int wx = (int) (PADDING + w.getX() * s);
            int wy = (int) (PADDING + w.getY() * s);
            if (Math.hypot(pt.x - wx, pt.y - wy) < WAYPOINT_HANDLE_PX) {
                return new WaypointHit(selectedLink, i);
            }
        }
        return null;
    }

    private static double distanceToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double lenSq = dx * dx + dy * dy;
        double t = lenSq == 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / lenSq;
        t = Math.max(0, Math.min(1, t));
        double cx = ax + t * dx, cy = ay + t * dy;
        return Math.hypot(px - cx, py - cy);
    }

    @Override
    public Dimension getPreferredSize() {
        double s = scale();
        double maxX = 0, maxY = 0;
        for (NetworkDevicePlacement p : devices) {
            maxX = Math.max(maxX, p.getXMm() + DEVICE_W);
            maxY = Math.max(maxY, p.getYMm() + DEVICE_H);
        }
        int w = (int) (maxX * s) + PADDING * 2;
        int h = (int) (maxY * s) + PADDING * 2;
        return new Dimension(Math.max(REF_VIEWPORT_W, w), Math.max(REF_VIEWPORT_H, h));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        double s = scale();
        float zoomF = (float) zoom;
        Font labelFont = getFont().deriveFont(Font.BOLD, clampF(12f * zoomF, 9f, 24f));
        Font ipFont = getFont().deriveFont(Font.PLAIN, clampF(10f * zoomF, 8f, 20f));

        // Связи -- под блоками, чтобы порты/блоки оставались кликабельны поверх линий.
        for (NetworkLink link : links) {
            List<double[]> pts = routePoints(link);
            if (pts == null) {
                continue;
            }
            boolean isSelected = link == selectedLink;
            g2.setColor(isSelected ? Color.WHITE : linkColor);
            g2.setStroke(new BasicStroke(clampF((isSelected ? 3f : 2f) * zoomF, 1f, 6f)));
            for (int i = 0; i < pts.size() - 1; i++) {
                int x1 = (int) (PADDING + pts.get(i)[0] * s), y1 = (int) (PADDING + pts.get(i)[1] * s);
                int x2 = (int) (PADDING + pts.get(i + 1)[0] * s), y2 = (int) (PADDING + pts.get(i + 1)[1] * s);
                g2.drawLine(x1, y1, x2, y2);
            }
            if (isSelected) {
                g2.setStroke(new BasicStroke(1f));
                for (NetworkLinkWaypoint w : link.getWaypoints()) {
                    int wx = (int) (PADDING + w.getX() * s), wy = (int) (PADDING + w.getY() * s);
                    g2.setColor(Color.WHITE);
                    g2.fillOval(wx - WAYPOINT_HANDLE_PX / 2, wy - WAYPOINT_HANDLE_PX / 2,
                            WAYPOINT_HANDLE_PX, WAYPOINT_HANDLE_PX);
                    g2.setColor(Palette.BORDER);
                    g2.drawOval(wx - WAYPOINT_HANDLE_PX / 2, wy - WAYPOINT_HANDLE_PX / 2,
                            WAYPOINT_HANDLE_PX, WAYPOINT_HANDLE_PX);
                }
            }
        }

        for (NetworkDevicePlacement p : devices) {
            int x = (int) (PADDING + p.getXMm() * s);
            int y = (int) (PADDING + p.getYMm() * s);
            int w = (int) (DEVICE_W * s);
            int h = (int) (DEVICE_H * s);

            boolean ipConflict = NetworkIpConflicts.hasConflict(devices, p);

            g2.setColor(resolveColor(p));
            g2.fillRoundRect(x, y, w, h, 8, 8);
            boolean isSelected = p == selected;
            boolean isLinkSource = linkingFrom != null && linkingFrom.device() == p;
            g2.setColor(isSelected || isLinkSource ? Color.WHITE : ipConflict ? COLOR_IP_CONFLICT : Palette.BORDER);
            g2.setStroke(new BasicStroke((isSelected || isLinkSource || ipConflict) ? 2.5f : 1.2f));
            g2.drawRoundRect(x, y, w, h, 8, 8);
            if (isLinkSource) {
                // Дополнительная пунктирная обводка ВСЕГО блока -- источник связи должен
                // быть заметен даже если сам маленький порт-квадрат легко не заметить
                // (баг-репорт "непонятно строится ли связь").
                g2.setColor(COLOR_LINK_PENDING);
                g2.setStroke(new BasicStroke(clampF(2f * zoomF, 1f, 4f), BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND, 0, new float[]{5f, 4f}, 0));
                g2.drawRoundRect(x - 3, y - 3, w + 6, h + 6, 10, 10);
            }

            g2.setFont(labelFont);
            g2.setColor(Color.WHITE);
            String label = resolveLabel(p);
            g2.drawString(clip(g2, label, w - 8), x + 6, y + g2.getFontMetrics().getAscent() + 4);

            if (!p.getIpAddress().isBlank()) {
                g2.setFont(ipFont);
                g2.setColor(ipConflict ? COLOR_IP_CONFLICT : new Color(0xd8dee6));
                // Баг-репорт/запрос: "конфликты IP -- хорошая идея, добавим" -- заметка
                // "⚠ конфликт" прямо в подписи, не только цвет обводки -- цвет один и тот
                // же красный уже занят категорией FIREWALL у каталожных устройств, так что
                // сама по себе красная РАМКА могла бы быть неоднозначной; текст снимает
                // всякую двусмысленность.
                String ipText = p.getIpAddress() + (ipConflict ? "  ⚠ конфликт IP" : "");
                g2.drawString(clip(g2, ipText, w - 8), x + 6, y + h - 6);
            }

            // Порты -- маленькие квадраты вдоль нижнего края; активный (источник строящейся
            // связи) и валидная цель под курсором -- крупнее и ярче, чтобы состояние "идёт
            // построение связи" было однозначно видно, а не терялось на мелком квадрате.
            int n = effectivePortCount(p);
            for (int port = 1; port <= n; port++) {
                double[] c = portCenterCanvasUnits(p, port);
                int px = (int) (PADDING + c[0] * s);
                int py = (int) (PADDING + c[1] * s);
                boolean isPendingSource = linkingFrom != null && linkingFrom.device() == p
                        && linkingFrom.port() == port;
                boolean isHoveredTarget = hoveredPort != null && hoveredPort.device() == p
                        && hoveredPort.port() == port;
                int size = (isPendingSource || isHoveredTarget) ? PORT_HIGHLIGHT_SIZE_PX : PORT_SIZE_PX;
                g2.setColor(isPendingSource ? COLOR_LINK_PENDING
                        : isHoveredTarget ? COLOR_PORT_TARGET_HOVER : COLOR_PORT);
                g2.fillRect(px - size / 2, py - size / 2, size, size);
                g2.setColor(isPendingSource || isHoveredTarget ? Color.WHITE : Palette.BORDER);
                g2.setStroke(new BasicStroke(isPendingSource || isHoveredTarget ? 2f : 1f));
                g2.drawRect(px - size / 2, py - size / 2, size, size);
            }
        }

        // Связь в процессе рисования -- поверх всего, толстым пунктиром до курсора,
        // ярким контрастным цветом, независимым от цвета уже готовых связей сети.
        if (linkingFrom != null && linkCursorPoint != null) {
            double[] a = portCenterCanvasUnits(linkingFrom.device(), linkingFrom.port());
            g2.setColor(COLOR_LINK_PENDING);
            g2.setStroke(new BasicStroke(clampF(3f * zoomF, 2f, 6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    0, new float[]{7f, 5f}, 0));
            g2.drawLine((int) (PADDING + a[0] * s), (int) (PADDING + a[1] * s), linkCursorPoint.x, linkCursorPoint.y);
        }

        drawSnapGuides(g2, s);
        g2.dispose();
    }

    /** Направляющие линии Shift-снаппинга точки излома — яркая пунктирная линия
     *  через всю видимую область, тот же приём (и даже тот же цвет), что {@code
     *  SchemaCanvasPanel#drawSnapGuides}, показывает, С ЧЕМ ИМЕННО сейчас
     *  выровнена перетаскиваемая точка. {@link #snapGuideX}/{@link #snapGuideY} —
     *  координаты канваса, переводятся в px через {@code PADDING}+масштаб, в
     *  отличие от {@code SchemaCanvasPanel} (там весь {@code Graphics2D} уже
     *  промасштабирован глобально, тут — нет, см. class-javadoc). */
    private void drawSnapGuides(Graphics2D g2, double s) {
        if (snapGuideX == null && snapGuideY == null) {
            return;
        }
        g2.setColor(Color.MAGENTA);
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0, new float[]{4, 4}, 0));
        if (snapGuideX != null) {
            int x = (int) Math.round(PADDING + snapGuideX * s);
            g2.drawLine(x, 0, x, getHeight());
        }
        if (snapGuideY != null) {
            int y = (int) Math.round(PADDING + snapGuideY * s);
            g2.drawLine(0, y, getWidth(), y);
        }
    }

    private static String clip(Graphics2D g2, String text, int maxWidth) {
        if (g2.getFontMetrics().stringWidth(text) <= maxWidth) {
            return text;
        }
        String s = text;
        while (!s.isEmpty() && g2.getFontMetrics().stringWidth(s + "…") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    private static float clampF(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
