package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Network;
import com.vjstb.ledscheme.model.NetworkAttachment;
import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import com.vjstb.ledscheme.model.NetworkDeviceType;
import com.vjstb.ledscheme.model.NetworkLink;
import com.vjstb.ledscheme.model.NetworkLinkWaypoint;
import com.vjstb.ledscheme.model.NetworkManagerPlan;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.LocalNetworkInterfaces;
import com.vjstb.ledscheme.service.NetworkDeviceLabels;
import com.vjstb.ledscheme.service.NetworkIpConflicts;
import com.vjstb.ledscheme.service.NetworkTopology;
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
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Холст Сетевого менеджера (см. {@code NetworkManagerPanel},
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
 * #resolveLabel}/{@link #resolveColor}/{@link #effectiveEthernetPortCount} —
 * переименование узла в схеме, смена цвета/числа портов типа в библиотеке
 * сразу видно здесь, копия не хранится.
 *
 * <p><b>Порты и связи</b> (запрос пользователя: "нужно уметь рисовать линки и
 * подключать по портам... для подробных схем и сложной маршрутизации") —
 * порты нумеруются СКВОЗНЫМ номером 1..N: сначала Ethernet-порты (см. {@link
 * #effectiveEthernetPortCount}), затем оптические (см. {@link
 * #effectiveOpticalPortCount}) — Ethernet КАТАЛОЖНЫХ устройств (свитчи/
 * роутеры из библиотеки) рисуются вдоль ВЕРХНЕГО края блока, СВЯЗАННЫХ со
 * схемой — вдоль НИЖНЕГО (запрос пользователя: "для сетевого оборудования
 * давай рисовать порты сверху", уточнение после первой версии: "у узлов
 * схемы порты рисуются снизу"); оптические — вдоль ПРАВОГО в обоих случаях
 * (см. {@link #portCenterCanvasUnits}). Клик по порту НАЧИНАЕТ связь (см. {@link
 * #linkingFrom}), клик по порту ДРУГОГО устройства ЗАВЕРШАЕТ её (создаёт
 * {@link NetworkLink} — только между устройствами ОДНОЙ сети, см. {@link
 * #createLink}), клик по пустому месту или {@code Esc} отменяет — модель
 * "клик-клик", не перетаскивание (перетаскивание уже занято перемещением
 * блока). Пока связь строится — курсор становится crosshair, начальный порт
 * подсвечивается увеличенным кольцом, порт под курсором (если это валидная
 * цель — другое устройство) подсвечивается тоже, пунктирная линия тянется до
 * курсора.
 *
 * <p><b>Занятые порты — попарно раскрашены по связи</b> (запрос
 * пользователя: "занятые порты должны попарно краситься в цвета") — оба
 * конца одной {@link NetworkLink} красятся ОДНИМ цветом (золотой угол по
 * индексу связи в {@code plan.getLinks()}, см. {@link #linkPairColor}),
 * чтобы на плотном свитче с десятком связей сразу было видно, какой порт с
 * каким физически спарен — не нужно прослеживать линию через весь канвас.
 *
 * <p><b>Излом линии связи</b> (баг-репорт: "как в блоксхемах добавить
 * возможность ломать линию") — двойной клик по линии добавляет точку излома
 * (см. {@link NetworkLinkWaypoint}, {@link #insertWaypoint}), точки излома
 * видны/перетаскиваемы только у ВЫДЕЛЕННОЙ связи ({@link #selectedLink},
 * выбирается обычным левым кликом по линии), тот же UX, что у {@code
 * SchemaCanvasPanel} для {@code SchemaEdge#getWaypoints()}. ПКМ по точке —
 * «Убрать точку», ПКМ по линии — «Выпрямить»/«Удалить связь».
 *
 * <p><b>Shift-снаппинг</b> (запрос пользователя: "снаппинг к блокам и
 * существующим узлам излома") — блок и точка излома притягиваются к другим
 * блокам/точкам излома ЛЮБОЙ сети (см. {@link #snapDevicePosition}/{@link
 * #snapWaypointPosition}), те же настройки профиля, что снаппинг общей схемы.
 *
 * <p><b>Растягивание блока</b> (запрос пользователя: "блоки должны мочь
 * растягиваться (как в общей схеме)") — хват за юго-восточный уголок, точная
 * копия UX {@code SchemaCanvasPanel} (см. {@link #resizeHandleAt}/{@link
 * #resizeNode} — тут же используется и как Shift-снаппинг размера, {@link
 * #snapResize}). Размер хранится на {@link NetworkDevicePlacement#getWidth()}/
 * {@code #getHeight()}, по умолчанию совпадает со старыми константами {@link
 * #MIN_DEVICE_W}/{@link #MIN_DEVICE_H} (минимум одновременно) — у проектов,
 * сохранённых раньше, раскладка не меняется, пока пользователь сам не
 * потянет за уголок.
 *
 * <p><b>Round 8 — членство в сети, не владение</b> (запрос пользователя:
 * "один блок может добавляться в поле 1 раз, но может принадлежать разным
 * сеткам при условии, что у него больше 1 порта и соответственно адреса") —
 * устройства/связи теперь общий список {@link NetworkManagerPlan#getDevices()}/
 * {@code #getLinks()}, а не собственность одной {@link Network} (см. её
 * class-javadoc). Принадлежность сетям — список {@link NetworkAttachment} на
 * самом устройстве ({@link NetworkDevicePlacement#getAttachments()}), IP/
 * маска/шлюз — тоже там, РАЗНЫЕ для разных сетей одного устройства. Все
 * резолвы "чья это сеть" идут через {@link NetworkTopology} — устройство/
 * связь сами по себе не хранят такой ссылки, она вычисляется живьём. Каждая
 * сеть рисуется как полупрозрачная цветная подложка вокруг СВОИХ устройств
 * ({@link #drawNetworkBackground}/{@link #networkBoundsCanvasUnits}); "текущая"
 * (выбранная слева в {@code NetworkManagerPanel}) сеть — визуально ярче
 * (сплошная обводка) — она же единственная цель для НОВЫХ устройств (см.
 * {@link #placeAndAdd}). Устройство, уже стоящее на поле, подключается к
 * ДОПОЛНИТЕЛЬНОЙ сети через ПКМ → «Подключить к сети…» (см. {@link
 * #attachToNetwork}), а не повторным перетаскиванием из палитры — палитра
 * ({@code NetworkManagerPanel.refreshSchemaPalette}) исключает узел, уже
 * присутствующий на поле ГДЕ УГОДНО, а не только в текущей сети.
 *
 * <p><b>Round 9 — «Admin Laptop» и цвет по статусу связи.</b> {@link
 * #addAdminLaptop} заводит блок, представляющий машину пользователя (запрос:
 * "в поле должен автоматически формироваться блок Admin Laptop... с которого
 * и будет осуществляться пинг" — пинг и так ВСЕГДА идёт с этой машины, блок
 * только визуализирует это в топологии), кнопка «+ Мой компьютер» в {@code
 * NetworkManagerPanel}; IP синхронизируется на каждое обновление панели (см.
 * {@link #refreshAdminLaptopAddresses}), во всём остальном — обычный блок.
 * {@link #linkStatusColor} красит связь по статусу фонового пинга (см. {@link
 * #setAvailability}, опрос ведёт {@code NetworkManagerPanel} — канвас только
 * рисует готовый результат), а не по цвету сети, ЕСЛИ статус известен для
 * ОБОИХ концов (запрос: "если есть информация" — нет данных не рисуется как
 * отдельное "неизвестное" состояние, просто остаётся цвет сети); тот же
 * статус — маленькой точкой перед строкой адреса на самом блоке.
 *
 * <p>ПКМ по блоку — {@link JPopupMenu} (Пинг/Веб-интерфейс/Параметры
 * устройства/Подключить-Параметры-Отключить по сетям/Удалить устройство).
 * «Пинг…» открывает {@link NetworkPingDialog} — живой, непрерывный, см. её
 * class-javadoc.
 *
 * <p><b>Добавление устройств</b> — кнопками ИЛИ drag-n-drop из палитры
 * ({@code NetworkManagerPanel}, два {@link DataFlavor}: {@link
 * #SCHEMA_NODE_FLAVOR}/{@link #DEVICE_TYPE_FLAVOR}) — эта панель НЕ ставит
 * свой {@code TransferHandler} сама, только экспонирует {@link
 * #pxToCanvas}/{@link #addLinkedDeviceAt}/{@link #addCatalogDeviceAt} как
 * публичный контракт (вызывающая сторона настраивает приём drop).
 *
 * <p><b>Автоперенос сетей из общей схемы</b> (запрос пользователя, после
 * отказа от статуса портов Novastar как нерелевантной фичи — см.
 * NETWORK_MANAGER_NOTES.md) — {@link #previewSchemaImport}/{@link
 * #applySchemaImport}, первый потребитель заготовки {@code AppModel
 * #networkGraphFromScene} (docs/schema-ports-rework/PLAN.md, T5.4). Группы —
 * связные компоненты графа схемы (устройства/коммутаторы, соединённые
 * NETWORK-связями), каждая становится отдельной {@link Network}; связи между
 * импортированными устройствами получают автоматически ПЕРВЫЙ свободный порт
 * с каждой стороны. Дедупликация — та же, что {@code NetworkManagerPanel
 * #refreshSchemaPalette}: узел, для которого уже есть {@link
 * NetworkDevicePlacement} где угодно в плане, повторно не предлагается.
 * Однонаправленно, как и вся связь со схемой в этом классе (Round 1) —
 * только ДОБАВЛЯЕТ новое, никогда не двигает/не переименовывает то, что уже
 * было перенесено раньше. UI — кнопка «Перенести из схемы…» и диалог
 * предпросмотра в {@code NetworkManagerPanel}. */
public class NetworkCanvasPanel extends JPanel {

    private static final int PADDING = 24;
    /** Размер НОВОГО блока и минимум при растягивании — прежние фиксированные
     *  константы канваса, теперь только дефолт/пол (см. class-javadoc
     *  "Растягивание блока"). */
    static final double MIN_DEVICE_W = 130;
    static final double MIN_DEVICE_H = 56;
    private static final int RESIZE_HANDLE_PX = 14;
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
    /** Блок «Admin Laptop» (см. {@link #addAdminLaptop}) — отдельный от всех
     *  категорий каталога и от обычных связанных устройств цвет, чтобы
     *  собственная машина пользователя визуально не путалась с медиасервером/
     *  контроллером на той же схеме. */
    private static final Color COLOR_ADMIN_LAPTOP = new Color(0x39c5cf);
    private static final Color COLOR_PORT = new Color(0xe6edf3);
    /** Отступ вокруг фактических устройств/точек излома сети при построении её
     *  цветной фоновой подложки (см. {@link #networkBoundsCanvasUnits}) — в
     *  координатах канваса, не px. */
    private static final double NETWORK_BG_PADDING = 20;
    /** Цвет связи, чья сеть не резолвится (защитно — не должно происходить в
     *  норме, см. {@link NetworkTopology#networkOfLink}). */
    private static final Color COLOR_LINK_ORPHAN = new Color(0x8b949e);
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
    /** Точка-индикатор статуса доступности (фоновый пинг, см. {@link
     *  #setAvailability}) — рисуется перед строкой адреса подключения, ТОЛЬКО
     *  если для этого IP есть данные (запрос пользователя: "цвета линий
     *  должны обозначать текущий статус соединения, ЕСЛИ ЕСТЬ ИНФОРМАЦИЯ") —
     *  нет данных = точка вообще не рисуется, а не рисуется серой "неизвестно"
     *  версией, той же логике следует {@link #linkStatusColor}. */
    private static final Color COLOR_STATUS_UP = new Color(0x3fb950);
    private static final Color COLOR_STATUS_DOWN = new Color(0xf85149);

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
    /** Живая ссылка на {@code Scene#getNetworkManagerPlan()} — устройства/связи
     *  ВСЕХ сетей сцены (см. class-javadoc Round 8). */
    private NetworkManagerPlan plan = new NetworkManagerPlan();
    /** Сеть, выбранная слева в {@code NetworkManagerPanel} — единственная цель
     *  для НОВЫХ устройств ({@link #placeAndAdd}) и визуально выделенная
     *  подложка (см. {@link #drawNetworkBackground}). {@code null}, если сеть
     *  не выбрана. */
    private Network currentNetwork;
    /** Результат последнего фонового опроса доступности (запрос пользователя:
     *  "цвета линий должны обозначать текущий статус соединения, если есть
     *  информация") — ip → доступен/недоступен, {@code null}/отсутствие ключа
     *  значит "нет данных" (адрес ещё не опрашивался, или опрос выключен).
     *  Опрашивает и передаёт сюда {@code NetworkManagerPanel} (владеет
     *  таймером — канвас сам никакие процессы не запускает, только рисует
     *  готовый результат), см. {@link #setAvailability}. */
    private java.util.Map<String, Boolean> availability = java.util.Map.of();
    private NetworkDevicePlacement selected;
    private NetworkLink selectedLink;
    private NetworkDevicePlacement dragging;
    private double dragOffXpx, dragOffYpx;
    /** Устройство, которое сейчас растягивают за юго-восточный уголок (см.
     *  class-javadoc "Растягивание блока") — {@code null}, если растягивание
     *  не идёт. Отдельно от {@link #dragging} (перемещение и растягивание —
     *  разные жесты, как у {@code SchemaCanvasPanel}). */
    private NetworkDevicePlacement resizing;
    private NetworkLink draggingWaypointLink;
    private int draggingWaypointIndex = -1;
    private PortHit linkingFrom;
    private Point linkCursorPoint;
    private PortHit hoveredPort;
    private Runnable onChanged = () -> { };
    private double zoom = 1.0;
    /** Направляющие линии Shift-снаппинга (точки излома И размера блока) — в
     *  КООРДИНАТАХ КАНВАСА (не px), {@code null} = сейчас ни к чему не
     *  привязано, тот же приём, что {@code SchemaCanvasPanel#snapGuideX/snapGuideY}. */
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

                NetworkDevicePlacement resizeHit = resizeHandleAt(e.getPoint());
                if (resizeHit != null) {
                    selected = resizeHit;
                    selectedLink = null;
                    resizing = resizeHit;
                    repaint();
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
                if (resizing != null) {
                    double s = scale();
                    double candidateW = Math.max(MIN_DEVICE_W, (e.getX() - PADDING) / s - resizing.getXMm());
                    double candidateH = Math.max(MIN_DEVICE_H, (e.getY() - PADDING) / s - resizing.getYMm());
                    if (e.isShiftDown()) {
                        double[] snapped = snapResize(resizing, candidateW, candidateH);
                        candidateW = snapped[0];
                        candidateH = snapped[1];
                    } else {
                        snapGuideX = null;
                        snapGuideY = null;
                    }
                    resizing.setWidth(candidateW);
                    resizing.setHeight(candidateH);
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
                    setToolTipText(portTooltip(hover));
                } else if (resizeHandleAt(e.getPoint()) != null) {
                    setToolTipText(null);
                    setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
                    return;
                } else {
                    setToolTipText(null);
                }
                if (linkingFrom == null) {
                    setCursor(Cursor.getDefaultCursor());
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
                if (resizing != null) {
                    resizing = null;
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

    /** Передаёт канвасу план ЦЕЛИКОМ (живая ссылка на {@code
     *  Scene#getNetworkManagerPlan()} — мутации канваса сразу отражаются в
     *  модели, персист по {@link #onChanged}) плюс "текущую" (выбранную слева
     *  в {@code NetworkManagerPanel}) сеть — см. {@link #currentNetwork}.
     *  Вызывается и при смене сцены, и при смене выбора в списке сетей. */
    public void setPlan(NetworkManagerPlan plan, Network current) {
        this.plan = plan != null ? plan : new NetworkManagerPlan();
        this.currentNetwork = current;
        selected = null;
        selectedLink = null;
        dragging = null;
        resizing = null;
        linkingFrom = null;
        revalidate();
        repaint();
    }

    /** Передаёт результат фонового опроса доступности (см. {@link
     *  #availability}) — вызывается снаружи ({@code NetworkManagerPanel}) по
     *  готовности каждого раунда опроса. */
    public void setAvailability(java.util.Map<String, Boolean> availability) {
        this.availability = availability != null ? availability : java.util.Map.of();
        repaint();
    }

    /** {@code true}, если на поле уже есть блок «Admin Laptop» — используется
     *  вызывающей стороной, чтобы не плодить второй (кнопка «+ Мой
     *  компьютер» тогда просто выделяет существующий). */
    public boolean hasAdminLaptop() {
        for (NetworkDevicePlacement p : plan.getDevices()) {
            if (p.isAdminLaptop()) {
                return true;
            }
        }
        return false;
    }

    /** Заводит на поле блок «Admin Laptop» (запрос пользователя: "в поле
     *  должен автоматически формироваться блок Admin Laptop, представляющий
     *  текущее устройство") — имя из {@link LocalNetworkInterfaces#hostname()},
     *  IP текущего активного интерфейса сразу в подключение к {@link
     *  #currentNetwork} (тот же {@link #placeAndAdd}, что и у любого другого
     *  нового устройства — дальше блок ничем не отличается от обычного,
     *  включая возможность вручную подключить его ЕЩЁ к одной сети, если на
     *  машине несколько интерфейсов, см. {@link LocalNetworkInterfaces}
     *  class-javadoc). */
    public void addAdminLaptop() {
        NetworkDevicePlacement p = new NetworkDevicePlacement();
        p.setAdminLaptop(true);
        p.setCustomLabel(LocalNetworkInterfaces.hostname());
        p.setEthernetPortCount(1);
        placeAndAdd(p, nextSpotX(), 0);
        NetworkAttachment attachment = p.attachmentFor(currentNetwork != null ? currentNetwork.getId() : null);
        if (attachment != null) {
            attachment.setIpAddress(LocalNetworkInterfaces.primaryIp());
        }
    }

    /** Синхронизирует IP блока «Admin Laptop» (если он есть на поле) с
     *  фактическим текущим адресом машины — вызывается из {@code
     *  NetworkManagerPanel#refresh()} на каждое изменение модели (не из
     *  {@link #paintComponent}: опрос сетевых интерфейсов ОС не бесплатен,
     *  дёргать его на каждый repaint при перетаскивании было бы расточительно).
     *  Возвращает {@code true}, если что-то реально изменилось — вызывающая
     *  сторона персистит план только тогда, а не на каждый вызов. */
    public boolean refreshAdminLaptopAddresses() {
        String ip = LocalNetworkInterfaces.primaryIp();
        boolean changed = false;
        for (NetworkDevicePlacement p : plan.getDevices()) {
            if (!p.isAdminLaptop()) {
                continue;
            }
            for (NetworkAttachment a : p.getAttachments()) {
                if (!ip.equals(a.getIpAddress())) {
                    a.setIpAddress(ip);
                    changed = true;
                }
            }
        }
        if (changed) {
            repaint();
        }
        return changed;
    }

    /** Цвет подложки/линий связи ЭТОЙ сети — см. class-javadoc Round 3/7/8.
     *  {@code null} ({@link Network#getColor()} ещё не задан) резолвится по
     *  тому же золотому углу от порядкового индекса сети, что {@code
     *  NetworkManagerPanel} использует при создании новой сети — так цвет
     *  подложки на канвасе и значок-образец в списке сетей слева всегда
     *  совпадают, даже для старых проектов без явно сохранённого цвета. */
    private Color resolveNetworkColor(Network network) {
        return network.getColor() != null ? new Color(network.getColor())
                : defaultColorForIndex(plan.getNetworks().indexOf(network));
    }

    /** Цвет по умолчанию для сети по её порядковому индексу — золотой угол,
     *  соседние по порядку сети получают заметно разные оттенки без ручного
     *  выбора. Package-visible: {@code NetworkManagerPanel} использует тот же
     *  метод при создании новой сети, чтобы не дублировать формулу. */
    static Color defaultColorForIndex(int index) {
        float hue = (float) (Math.max(0, index) * 0.618033988749895 % 1.0);
        return Color.getHSBColor(hue, 0.62f, 0.92f);
    }

    /** Цвет связи ПО СТАТУСУ (запрос пользователя: "цвета линий должны
     *  обозначать текущий статус соединения, если есть информация") — зелёный,
     *  если оба конца связи ({@code fromDevice}/{@code toDevice}, их IP В
     *  ЭТОЙ КОНКРЕТНОЙ сети {@code owner}) отвечают на пинг, красный — если
     *  ХОТЯ БЫ один точно не отвечает, {@code null} — данных недостаточно
     *  (адрес не задан, ещё не опрашивался, или сеть связи не резолвится) —
     *  вызывающая сторона тогда падает обратно на цвет сети (см. {@link
     *  #paintComponent}), а не рисует "серый — неизвестно": пользователь
     *  явно оговорил "если есть информация", отсутствие данных не должно
     *  выглядеть как отдельное состояние. */
    private Color linkStatusColor(NetworkLink link, Network owner) {
        if (owner == null || availability.isEmpty()) {
            return null;
        }
        Boolean fromOk = attachmentReachable(NetworkTopology.deviceById(plan, link.getFromDeviceId()), owner.getId());
        Boolean toOk = attachmentReachable(NetworkTopology.deviceById(plan, link.getToDeviceId()), owner.getId());
        if (Boolean.FALSE.equals(fromOk) || Boolean.FALSE.equals(toOk)) {
            return COLOR_STATUS_DOWN;
        }
        if (Boolean.TRUE.equals(fromOk) && Boolean.TRUE.equals(toOk)) {
            return COLOR_STATUS_UP;
        }
        return null;
    }

    /** {@code null} — нет данных (устройство не найдено, нет подключения к
     *  этой сети, адрес пуст, или сам адрес ещё не встречался в опросе). */
    private Boolean attachmentReachable(NetworkDevicePlacement device, String networkId) {
        if (device == null) {
            return null;
        }
        NetworkAttachment a = device.attachmentFor(networkId);
        if (a == null || a.getIpAddress() == null || a.getIpAddress().isBlank()) {
            return null;
        }
        return availability.get(a.getIpAddress().trim());
    }

    /** Цвет ПАРЫ портов конкретной связи (запрос пользователя: "занятые порты
     *  должны попарно краситься в цвета") — золотой угол по индексу связи в
     *  {@code plan.getLinks()}, независимая палитра от цвета сети (та уже
     *  занята подложкой/линией — этот цвет только про "какие два порта
     *  физически спарены", не про принадлежность сети). */
    private Color linkPairColor(NetworkLink link) {
        int index = plan.getLinks().indexOf(link);
        float hue = (float) ((index * 0.618033988749895 + 0.15) % 1.0);
        return Color.getHSBColor(hue, 0.55f, 0.95f);
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
     *  ни с чем на общей схеме. Для drag-n-drop — см. {@link #addCatalogDeviceAt}. */
    public void addCatalogDevice(NetworkDeviceType type) {
        addCatalogDeviceAt(type, nextSpotX(), 0);
    }

    public void addCatalogDeviceAt(NetworkDeviceType type, double x, double y) {
        NetworkDevicePlacement p = new NetworkDevicePlacement();
        p.setDeviceTypeId(type.getId());
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
     *  позже через ПКМ. IP сразу попадает в подключение к {@link
     *  #currentNetwork} (см. {@link #placeAndAdd}). */
    public void addDiscoveredDeviceAt(String ipAddress, double x, double y) {
        NetworkDevicePlacement p = new NetworkDevicePlacement();
        placeAndAdd(p, x, y);
        NetworkAttachment attachment = p.attachmentFor(currentNetwork != null ? currentNetwork.getId() : null);
        if (attachment != null) {
            attachment.setIpAddress(ipAddress);
        }
    }

    /** Добавляет устройство на поле И сразу подключает к {@link
     *  #currentNetwork} (единственная цель для новых устройств — см.
     *  class-javadoc Round 8). Без выбранной сети добавлять некуда —
     *  вызывающая сторона ({@code NetworkManagerPanel}) уже проверяет выбор,
     *  здесь просто защитная страховка. */
    private void placeAndAdd(NetworkDevicePlacement p, double x, double y) {
        if (currentNetwork == null) {
            return;
        }
        p.setXMm(Math.max(0, x));
        p.setYMm(Math.max(0, y));
        p.getAttachments().add(new NetworkAttachment(currentNetwork.getId()));
        plan.getDevices().add(p);
        selected = p;
        selectedLink = null;
        onChanged.run();
        repaint();
    }

    /** Следующее свободное место по X — считается по ВСЕМ устройствам плана
     *  (все сети на одном общем канвасе, см. class-javadoc Round 7/8), иначе
     *  первое устройство новой/пустой сети стартовало бы в (0,0) и оказалось
     *  бы под уже существующими блоками другой сети. */
    private double nextSpotX() {
        return plan.getDevices().size() * (MIN_DEVICE_W + 30);
    }

    /** «Выровнять сеть» (одобрено пользователем при обсуждении доработок
     *  менеджера) — переставляет устройства ОДНОЙ сети в аккуратную сетку
     *  (примерно квадратную по числу колонок, {@code ceil(sqrt(n))}), не
     *  трогая устройства ДРУГИХ сетей — тот же принцип, что {@code
     *  AppModel#autoArrangeScreensInScene} (массовая перестановка, без
     *  undo — слишком крупное изменение позиций для отменяемой истории
     *  канваса). Якорь — текущий левый верхний угол СВОЕЙ же сети (минимум
     *  X/Y её устройств ДО перестановки), чтобы сеть осталась примерно там
     *  же на канвасе, а не прыгала в (0,0) — намеренно НЕ пытается объехать
     *  другие сети, если они физически перекрываются (см. NETWORK_MANAGER
     *  _NOTES.md, "явно отложено" — автоматическое разрешение коллизий
     *  МЕЖДУ сетями сознательно не реализовывалось). Ячейка сетки — по
     *  максимальной ширине/высоте СРЕДИ устройств этой сети (после Round 8
     *  они разного размера, растягиваются), с отступом {@code gap}. */
    public void autoArrangeNetwork(String networkId) {
        List<NetworkDevicePlacement> devices = NetworkTopology.devicesInNetwork(plan, networkId);
        if (devices.isEmpty()) {
            return;
        }
        double gap = 30;
        double anchorX = Double.MAX_VALUE, anchorY = Double.MAX_VALUE;
        double maxW = 0, maxH = 0;
        for (NetworkDevicePlacement d : devices) {
            anchorX = Math.min(anchorX, d.getXMm());
            anchorY = Math.min(anchorY, d.getYMm());
            maxW = Math.max(maxW, d.getWidth());
            maxH = Math.max(maxH, d.getHeight());
        }
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(devices.size())));
        for (int i = 0; i < devices.size(); i++) {
            NetworkDevicePlacement d = devices.get(i);
            int row = i / cols;
            int col = i % cols;
            d.setXMm(anchorX + col * (maxW + gap));
            d.setYMm(anchorY + row * (maxH + gap));
        }
        selected = null;
        onChanged.run();
        repaint();
    }

    // ---- Автоперенос сетей из общей схемы ----

    /** Одна будущая сеть при автопереносе (запрос пользователя после отказа от
     *  статуса портов Novastar: "собери план по автопереносу сетей из общей
     *  схемы в менеджер") — {@code devices}/{@code switches} те же записи,
     *  что отдаёт {@link AppModel.NetworkGraph}, {@code links} — только связи
     *  МЕЖДУ узлами ЭТОЙ группы. Группа = связная компонента графа (устройства
     *  и коммутаторы, соединённые NETWORK-связями схемы напрямую или через
     *  коммутатор) — коммутатор без единого кабеля тоже своя отдельная
     *  однонодовая группа. */
    public record ImportGroup(String suggestedName, List<AppModel.NetworkGraphDevice> devices,
            List<AppModel.NetworkGraphDevice> switches, List<AppModel.NetworkGraphLink> links) {
    }

    /** {@code alreadyImportedCount} — сколько узлов графа схемы отфильтровано,
     *  т.к. уже есть в плане (см. {@code NetworkManagerPanel
     *  #refreshSchemaPalette} — тот же принцип дедупликации "где угодно в
     *  плане", не по одной сети) — только для текста диалога, не влияет на
     *  {@code newGroups}. */
    public record SchemaImportPreview(List<ImportGroup> newGroups, int alreadyImportedCount) {
    }

    /** Строит предпросмотр автопереноса — ЧИСТАЯ функция, без побочных
     *  эффектов на план (см. {@link #applySchemaImport} за применением).
     *  Источник графа — {@link AppModel#networkGraphFromScene} (заготовка
     *  docs/schema-ports-rework/PLAN.md, T5.4, до этого не имела ни одного
     *  вызывающего места в основном коде). Разбивка на группы — по связным
     *  компонентам (решение пользователя: "по связным компонентам графа"), а
     *  не в одну сеть и не поштучным опросом — так автоперенос отражает
     *  физическую топологию схемы напрямую. */
    public SchemaImportPreview previewSchemaImport() {
        Scene scene = model.getCurrentScene();
        if (scene == null) {
            return new SchemaImportPreview(List.of(), 0);
        }
        AppModel.NetworkGraph graph = model.networkGraphFromScene(scene);
        java.util.Set<String> usedAnywhere = plan.getDevices().stream()
                .map(NetworkDevicePlacement::getLinkedSchemaNodeId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        List<AppModel.NetworkGraphDevice> newDevices = graph.devices().stream()
                .filter(d -> !usedAnywhere.contains(d.nodeId())).toList();
        List<AppModel.NetworkGraphDevice> newSwitches = graph.switches().stream()
                .filter(d -> !usedAnywhere.contains(d.nodeId())).toList();
        int alreadyImportedCount = (graph.devices().size() - newDevices.size())
                + (graph.switches().size() - newSwitches.size());

        java.util.Map<String, AppModel.NetworkGraphDevice> allById = new java.util.LinkedHashMap<>();
        newDevices.forEach(d -> allById.put(d.nodeId(), d));
        newSwitches.forEach(d -> allById.put(d.nodeId(), d));
        java.util.Set<String> switchIds = newSwitches.stream()
                .map(AppModel.NetworkGraphDevice::nodeId).collect(java.util.stream.Collectors.toSet());

        List<AppModel.NetworkGraphLink> relevantLinks = graph.links().stream()
                .filter(l -> allById.containsKey(l.fromNodeId()) && allById.containsKey(l.toNodeId())).toList();

        java.util.Map<String, String> parent = new java.util.LinkedHashMap<>();
        for (String id : allById.keySet()) {
            parent.put(id, id);
        }
        for (AppModel.NetworkGraphLink l : relevantLinks) {
            unionComponents(parent, l.fromNodeId(), l.toNodeId());
        }
        java.util.Map<String, List<String>> components = new java.util.LinkedHashMap<>();
        for (String id : allById.keySet()) {
            components.computeIfAbsent(findComponent(parent, id), k -> new ArrayList<>()).add(id);
        }

        List<ImportGroup> groups = new ArrayList<>();
        int autoIndex = 1;
        for (List<String> ids : components.values()) {
            List<AppModel.NetworkGraphDevice> gDevices = new ArrayList<>();
            List<AppModel.NetworkGraphDevice> gSwitches = new ArrayList<>();
            for (String id : ids) {
                (switchIds.contains(id) ? gSwitches : gDevices).add(allById.get(id));
            }
            List<AppModel.NetworkGraphLink> gLinks = relevantLinks.stream()
                    .filter(l -> ids.contains(l.fromNodeId()) && ids.contains(l.toNodeId())).toList();
            String name = gSwitches.size() == 1 ? gSwitches.get(0).label() : "Импорт " + autoIndex++;
            groups.add(new ImportGroup(name, gDevices, gSwitches, gLinks));
        }
        return new SchemaImportPreview(groups, alreadyImportedCount);
    }

    private static String findComponent(java.util.Map<String, String> parent, String id) {
        String root = id;
        while (!parent.get(root).equals(root)) {
            root = parent.get(root);
        }
        while (!parent.get(id).equals(root)) {
            String next = parent.get(id);
            parent.put(id, root);
            id = next;
        }
        return root;
    }

    private static void unionComponents(java.util.Map<String, String> parent, String a, String b) {
        String rootA = findComponent(parent, a);
        String rootB = findComponent(parent, b);
        if (!rootA.equals(rootB)) {
            parent.put(rootA, rootB);
        }
    }

    /** Применяет ВЫБРАННЫЕ пользователем группы предпросмотра — на группу:
     *  новая {@link Network} (цвет — {@link #defaultColorForIndex}, тот же
     *  приём, что {@code NetworkManagerPanel#addNetwork}), устройства/
     *  коммутаторы группы — {@link NetworkDevicePlacement} со {@link
     *  NetworkDevicePlacement#setLinkedSchemaNodeId}, связи группы —
     *  {@link NetworkLink} на ПЕРВЫЙ свободный порт с каждой стороны (решение
     *  пользователя). Раскладка — {@link #nextSpotX} на добавление плюс
     *  {@link #autoArrangeNetwork} в конце каждой группы (та же сетка, что и
     *  кнопка «Выровнять сеть» — не новый алгоритм). Возвращает связи, для
     *  которых не хватило свободных портов (обе стороны уже заняты) — просто
     *  пропускаются, не создаются «за пределами видимых портов», вызывающая
     *  сторона может показать это одной строкой статуса. */
    public List<AppModel.NetworkGraphLink> applySchemaImport(List<ImportGroup> groups) {
        List<AppModel.NetworkGraphLink> skippedLinks = new ArrayList<>();
        for (ImportGroup group : groups) {
            Network network = new Network();
            network.setName(group.suggestedName());
            network.setColor(defaultColorForIndex(plan.getNetworks().size()).getRGB());
            plan.getNetworks().add(network);

            java.util.Map<String, NetworkDevicePlacement> placementByNodeId = new java.util.LinkedHashMap<>();
            for (AppModel.NetworkGraphDevice d : group.devices()) {
                placementByNodeId.put(d.nodeId(), placeImported(d.nodeId(), network));
            }
            for (AppModel.NetworkGraphDevice s : group.switches()) {
                placementByNodeId.put(s.nodeId(), placeImported(s.nodeId(), network));
            }

            for (AppModel.NetworkGraphLink link : group.links()) {
                NetworkDevicePlacement from = placementByNodeId.get(link.fromNodeId());
                NetworkDevicePlacement to = placementByNodeId.get(link.toNodeId());
                if (from == null || to == null) {
                    continue;
                }
                Integer fromPort = firstFreePort(from);
                Integer toPort = firstFreePort(to);
                if (fromPort == null || toPort == null) {
                    skippedLinks.add(link);
                    continue;
                }
                NetworkLink nl = new NetworkLink();
                nl.setFromDeviceId(from.getId());
                nl.setFromPort(fromPort);
                nl.setToDeviceId(to.getId());
                nl.setToPort(toPort);
                plan.getLinks().add(nl);
            }

            autoArrangeNetwork(network.getId());
        }
        return skippedLinks;
    }

    private NetworkDevicePlacement placeImported(String schemaNodeId, Network network) {
        NetworkDevicePlacement p = new NetworkDevicePlacement();
        p.setLinkedSchemaNodeId(schemaNodeId);
        p.setXMm(nextSpotX());
        p.setYMm(0);
        p.getAttachments().add(new NetworkAttachment(network.getId()));
        plan.getDevices().add(p);
        return p;
    }

    private Integer firstFreePort(NetworkDevicePlacement device) {
        int total = effectiveTotalPortCount(device);
        for (int port = 1; port <= total; port++) {
            if (linkAtPort(device, port) == null) {
                return port;
            }
        }
        return null;
    }

    // ---- ПКМ меню устройства/связи/точки излома ----

    private void showDeviceMenu(NetworkDevicePlacement device, int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        List<NetworkAttachment> attachments = device.getAttachments();

        if (attachments.size() <= 1) {
            JMenuItem ping = new JMenuItem("Пинг…");
            String ip = attachments.isEmpty() ? "" : attachments.get(0).getIpAddress();
            ping.setEnabled(ip != null && !ip.isBlank());
            ping.addActionListener(e -> pingDevice(ip));
            menu.add(ping);
        } else {
            JMenu pingMenu = new JMenu("Пинг");
            for (NetworkAttachment a : attachments) {
                String label = networkNameOrPlaceholder(a.getNetworkId()) + ": "
                        + (a.getIpAddress().isBlank() ? "(нет адреса)" : a.getIpAddress());
                JMenuItem item = new JMenuItem(label);
                item.setEnabled(!a.getIpAddress().isBlank());
                item.addActionListener(e -> pingDevice(a.getIpAddress()));
                pingMenu.add(item);
            }
            menu.add(pingMenu);
        }

        JMenuItem openWeb = new JMenuItem("Открыть веб-интерфейс");
        openWeb.setEnabled(device.isHasWebInterface() && !device.getWebInterfaceUrl().isBlank());
        openWeb.addActionListener(e -> UiKit.openUrl(this, device.getWebInterfaceUrl()));
        menu.add(openWeb);

        menu.addSeparator();

        JMenuItem params = new JMenuItem("Параметры устройства…");
        params.addActionListener(e -> editParams(device));
        menu.add(params);

        List<Network> attachedNetworks = new ArrayList<>();
        List<Network> freeNetworks = new ArrayList<>();
        for (Network n : plan.getNetworks()) {
            if (device.attachmentFor(n.getId()) != null) {
                attachedNetworks.add(n);
            } else {
                freeNetworks.add(n);
            }
        }

        JMenu connectMenu = new JMenu("Подключить к сети");
        boolean hasFreePorts = hasFreePorts(device);
        connectMenu.setEnabled(!freeNetworks.isEmpty() && hasFreePorts);
        if (!freeNetworks.isEmpty() && !hasFreePorts) {
            connectMenu.setToolTipText("У устройства нет свободных портов — ограничьте порты существующего"
                    + " подключения в его параметрах (\"Параметры подключения…\")");
        }
        for (Network n : freeNetworks) {
            JMenuItem item = new JMenuItem(networkDisplayName(n));
            item.addActionListener(e -> attachToNetwork(device, n));
            connectMenu.add(item);
        }
        menu.add(connectMenu);

        JMenu attachmentParamsMenu = new JMenu("Параметры подключения");
        attachmentParamsMenu.setEnabled(!attachedNetworks.isEmpty());
        for (Network n : attachedNetworks) {
            JMenuItem item = new JMenuItem(networkDisplayName(n));
            item.addActionListener(e -> editAttachment(device, n));
            attachmentParamsMenu.add(item);
        }
        menu.add(attachmentParamsMenu);

        JMenu disconnectMenu = new JMenu("Отключить от сети");
        disconnectMenu.setEnabled(!attachedNetworks.isEmpty());
        for (Network n : attachedNetworks) {
            JMenuItem item = new JMenuItem(networkDisplayName(n));
            item.addActionListener(e -> detachFromNetwork(device, n.getId()));
            disconnectMenu.add(item);
        }
        menu.add(disconnectMenu);

        menu.addSeparator();
        JMenuItem remove = new JMenuItem("Удалить устройство");
        remove.addActionListener(e -> deleteDeviceEntirely(device));
        menu.add(remove);

        menu.show(this, x, y);
    }

    private static String networkDisplayName(Network n) {
        return n.getName() == null || n.getName().isBlank() ? "(без названия)" : n.getName();
    }

    private String networkNameOrPlaceholder(String networkId) {
        Network n = NetworkTopology.networkById(plan, networkId);
        return n != null ? networkDisplayName(n) : "?";
    }

    /** Число портов, ещё НЕ отданных ни одному подключению устройства — "все
     *  порты" (пустой {@link NetworkAttachment#getPorts()}) считается как
     *  занявшее их ВСЕ. Используется только для включения/отключения пункта
     *  «Подключить к сети» — не строгая валидация (см. её же javadoc). */
    private boolean hasFreePorts(NetworkDevicePlacement device) {
        int total = effectiveTotalPortCount(device);
        int claimed = 0;
        for (NetworkAttachment a : device.getAttachments()) {
            claimed += a.getPorts().isEmpty() ? total : a.getPorts().size();
        }
        return claimed < total;
    }

    private void attachToNetwork(NetworkDevicePlacement device, Network network) {
        NetworkAttachment attachment = new NetworkAttachment(network.getId());
        NetworkAttachmentDialog dlg = new NetworkAttachmentDialog(
                (java.awt.Window) SwingUtilities.getWindowAncestor(this), network.getName(), device, attachment);
        if (dlg.showDialog()) {
            device.getAttachments().add(attachment);
            onChanged.run();
            repaint();
        }
    }

    private void editAttachment(NetworkDevicePlacement device, Network network) {
        NetworkAttachment attachment = device.attachmentFor(network.getId());
        if (attachment == null) {
            return;
        }
        NetworkAttachmentDialog dlg = new NetworkAttachmentDialog(
                (java.awt.Window) SwingUtilities.getWindowAncestor(this), network.getName(), device, attachment);
        if (dlg.showDialog()) {
            onChanged.run();
            repaint();
        }
    }

    /** Отключает {@code device} от сети {@code networkId} — снимает ТОЛЬКО это
     *  подключение (и связи, чья сеть резолвится именно в эту, см. {@link
     *  NetworkTopology#networkOfLink}), устройство остаётся на поле, если у
     *  него есть ДРУГИЕ подключения. Если это было последнее — удаляет
     *  устройство целиком (см. {@link #deleteDeviceEntirely}), т.к. блок без
     *  единой сети на канвасе бессмыслен (то же поведение, что было раньше
     *  единственным — "Убрать из сети" всегда снимало устройство целиком,
     *  пока сеть была ровно одна на устройство). */
    private void detachFromNetwork(NetworkDevicePlacement device, String networkId) {
        List<NetworkLink> toRemove = new ArrayList<>();
        for (NetworkLink l : plan.getLinks()) {
            boolean touches = device.getId().equals(l.getFromDeviceId()) || device.getId().equals(l.getToDeviceId());
            if (!touches) {
                continue;
            }
            Network owner = NetworkTopology.networkOfLink(plan, l);
            if (owner != null && networkId.equals(owner.getId())) {
                toRemove.add(l);
            }
        }
        plan.getLinks().removeAll(toRemove);
        if (selectedLink != null && toRemove.contains(selectedLink)) {
            selectedLink = null;
        }
        device.getAttachments().removeIf(a -> networkId.equals(a.getNetworkId()));
        if (device.getAttachments().isEmpty()) {
            deleteDeviceEntirely(device);
            return;
        }
        onChanged.run();
        repaint();
    }

    /** Удаляет устройство целиком, из ВСЕХ сетей разом, вместе со всеми его
     *  связями — «Удалить устройство» в ПКМ-меню. */
    private void deleteDeviceEntirely(NetworkDevicePlacement device) {
        plan.getDevices().remove(device);
        plan.getLinks().removeIf(l -> device.getId().equals(l.getFromDeviceId())
                || device.getId().equals(l.getToDeviceId()));
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
            plan.getLinks().remove(link);
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

    /** Создаёт связь порт-в-порт — ТОЛЬКО между устройствами ОДНОЙ и той же
     *  сети (резолвится по сети, покрывающей КАЖДЫЙ порт, см. {@link
     *  NetworkTopology#networkOfPort} — не по устройству целиком, у
     *  многосетевых устройств разные порты могут принадлежать разным сетям).
     *  Попытка соединить порты разных сетей отклоняется с предупреждением. */
    private void createLink(PortHit from, PortHit to) {
        Network ownerFrom = NetworkTopology.networkOfPort(plan, from.device(), from.port());
        Network ownerTo = NetworkTopology.networkOfPort(plan, to.device(), to.port());
        if (ownerFrom == null || ownerTo == null || !ownerFrom.getId().equals(ownerTo.getId())) {
            JOptionPane.showMessageDialog(this,
                    "Нельзя соединить порты из разных сетей — только в пределах одной сети.",
                    "Разные сети", JOptionPane.WARNING_MESSAGE);
            return;
        }
        NetworkLink link = new NetworkLink();
        link.setFromDeviceId(from.device().getId());
        link.setFromPort(from.port());
        link.setToDeviceId(to.device().getId());
        link.setToPort(to.port());
        plan.getLinks().add(link);
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

    /** Shift-снаппинг перетаскиваемого БЛОКА устройства — точная копия {@code
     *  SchemaCanvasPanel#snapPosition} (три кандидата на измерение: левый
     *  край/центр/правый край по X, верх/центр/низ по Y, сравниваются с
     *  такими же координатами ДРУГИХ блоков любой сети) плюс точки излома
     *  ЛЮБОЙ связи. Побочный эффект — выставляет {@link #snapGuideX}/{@link
     *  #snapGuideY}. */
    private double[] snapDevicePosition(NetworkDevicePlacement moving, double candidateX, double candidateY) {
        snapGuideX = null;
        snapGuideY = null;
        double threshold = settings.activeProfile().getSnapThresholdPx();
        int strength = settings.activeProfile().getSnapStrengthPercent();
        double w = moving.getWidth(), h = moving.getHeight();
        double[] xCandidates = {candidateX, candidateX + w / 2, candidateX + w};
        double[] yCandidates = {candidateY, candidateY + h / 2, candidateY + h};
        double bestDx = threshold, bestDy = threshold;
        double snappedX = candidateX, snappedY = candidateY;

        for (NetworkDevicePlacement other : plan.getDevices()) {
            if (other == moving) {
                continue;
            }
            double ow = other.getWidth(), oh = other.getHeight();
            double[] oxs = {other.getXMm(), other.getXMm() + ow / 2, other.getXMm() + ow};
            double[] oys = {other.getYMm(), other.getYMm() + oh / 2, other.getYMm() + oh};
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
        for (NetworkLink link : plan.getLinks()) {
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

    /** Shift-снаппинг перетаскиваемой точки излома — точная копия {@code
     *  SchemaCanvasPanel#snapWaypointPosition}, кандидаты — центры портов
     *  устройств ЛЮБОЙ сети и точки излома ЛЮБОЙ связи. */
    private double[] snapWaypointPosition(NetworkLink movingLink, int movingIndex,
                                           double candidateX, double candidateY) {
        snapGuideX = null;
        snapGuideY = null;
        double threshold = settings.activeProfile().getSnapThresholdPx();
        int strength = settings.activeProfile().getSnapStrengthPercent();
        double bestDx = threshold, bestDy = threshold;
        double snappedX = candidateX, snappedY = candidateY;

        for (NetworkDevicePlacement device : plan.getDevices()) {
            int n = effectiveTotalPortCount(device);
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
        for (NetworkLink link : plan.getLinks()) {
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

    /** Shift-снаппинг размера при растягивании — точная копия {@code
     *  SchemaCanvasPanel#snapResize}: ширина/высота притягиваются к таким же
     *  ширинам/высотам ДРУГИХ блоков любой сети (не к позициям — размер
     *  меряется независимо от X/Y). */
    private double[] snapResize(NetworkDevicePlacement moving, double candidateW, double candidateH) {
        snapGuideX = null;
        snapGuideY = null;
        double threshold = settings.activeProfile().getSnapThresholdPx();
        int strength = settings.activeProfile().getSnapStrengthPercent();
        double bestDw = threshold, bestDh = threshold;
        double snappedW = candidateW, snappedH = candidateH;
        double s = scale();

        for (NetworkDevicePlacement other : plan.getDevices()) {
            if (other == moving) {
                continue;
            }
            double dw = Math.abs(candidateW - other.getWidth());
            if (dw < bestDw) {
                bestDw = dw;
                snappedW = SnapMath.blend(candidateW, other.getWidth(), strength);
                snapGuideX = moving.getXMm() + snappedW;
            }
            double dh = Math.abs(candidateH - other.getHeight());
            if (dh < bestDh) {
                bestDh = dh;
                snappedH = SnapMath.blend(candidateH, other.getHeight(), strength);
                snapGuideY = moving.getYMm() + snappedH;
            }
        }
        return new double[]{snappedW, snappedH};
    }

    private void pingDevice(String ip) {
        String host = ip == null ? "" : ip.trim();
        if (host.isEmpty()) {
            return;
        }
        new NetworkPingDialog((java.awt.Window) SwingUtilities.getWindowAncestor(this), host).setVisible(true);
    }

    private void editParams(NetworkDevicePlacement device) {
        Integer typeEthernet = null;
        Integer typeOptical = null;
        if (device.getDeviceTypeId() != null) {
            NetworkDeviceType type = model.getWorkspace().networkDeviceTypeById(device.getDeviceTypeId());
            if (type != null) {
                typeEthernet = Math.max(0, type.getEthernetPortCount());
                typeOptical = Math.max(0, type.getOpticalPortCount());
            }
        }
        NetworkDeviceParamsDialog dlg = new NetworkDeviceParamsDialog(
                (java.awt.Window) SwingUtilities.getWindowAncestor(this), device, resolveLabel(device),
                typeEthernet, typeOptical);
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

    private Color resolveColor(NetworkDevicePlacement p) {
        if (p.isAdminLaptop()) {
            return COLOR_ADMIN_LAPTOP;
        }
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

    /** Действующее число Ethernet-портов — из каталожного типа для устройств
     *  оттуда, иначе из собственного поля размещения (см. class-javadoc про
     *  различие с портами вывода видео контроллера). */
    private int effectiveEthernetPortCount(NetworkDevicePlacement p) {
        if (p.getDeviceTypeId() != null) {
            NetworkDeviceType type = model.getWorkspace().networkDeviceTypeById(p.getDeviceTypeId());
            return type != null ? Math.max(0, type.getEthernetPortCount()) : 0;
        }
        return Math.max(0, p.getEthernetPortCount());
    }

    private int effectiveOpticalPortCount(NetworkDevicePlacement p) {
        if (p.getDeviceTypeId() != null) {
            NetworkDeviceType type = model.getWorkspace().networkDeviceTypeById(p.getDeviceTypeId());
            return type != null ? Math.max(0, type.getOpticalPortCount()) : 0;
        }
        return Math.max(0, p.getOpticalPortCount());
    }

    private int effectiveTotalPortCount(NetworkDevicePlacement p) {
        return effectiveEthernetPortCount(p) + effectiveOpticalPortCount(p);
    }

    // ---- геометрия/хит-тесты ----

    /** Центр порта {@code portIndex1Based} (нумерация сквозная: 1..ethernet,
     *  дальше — оптические) в КООРДИНАТАХ КАНВАСА (до масштаба/PADDING).
     *  Сторона ethernet-группы зависит от происхождения блока (запрос
     *  пользователя, уточнение после первой версии: "порты сверху рисуем у
     *  сетевого оборудования, у узлов схемы порты рисуются снизу") —
     *  КАТАЛОЖНЫЕ устройства ({@link NetworkDevicePlacement#getDeviceTypeId()}
     *  задан — свитчи/роутеры из библиотеки) вдоль ВЕРХНЕГО края, СВЯЗАННЫЕ со
     *  схемой (и bare-устройства без каталожного типа) — вдоль НИЖНЕГО, как
     *  было до появления оптики. Оптические порты — вдоль ПРАВОГО края в
     *  обоих случаях, см. class-javadoc. */
    private double[] portCenterCanvasUnits(NetworkDevicePlacement p, int portIndex1Based) {
        int ethCount = effectiveEthernetPortCount(p);
        double w = p.getWidth(), h = p.getHeight();
        if (portIndex1Based <= ethCount) {
            double spacing = w / (ethCount + 1);
            double yEdge = p.getDeviceTypeId() != null ? p.getYMm() : p.getYMm() + h;
            return new double[]{p.getXMm() + spacing * portIndex1Based, yEdge};
        }
        int opticalIndex = portIndex1Based - ethCount;
        int optCount = effectiveOpticalPortCount(p);
        double spacing = h / (optCount + 1);
        return new double[]{p.getXMm() + w, p.getYMm() + spacing * opticalIndex};
    }

    private PortHit portAt(Point pt) {
        double s = scale();
        List<NetworkDevicePlacement> devices = plan.getDevices();
        for (int i = devices.size() - 1; i >= 0; i--) {
            NetworkDevicePlacement p = devices.get(i);
            int n = effectiveTotalPortCount(p);
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
        List<NetworkDevicePlacement> devices = plan.getDevices();
        for (int i = devices.size() - 1; i >= 0; i--) {
            NetworkDevicePlacement p = devices.get(i);
            int x = (int) (PADDING + p.getXMm() * s);
            int y = (int) (PADDING + p.getYMm() * s);
            int w = (int) (p.getWidth() * s);
            int h = (int) (p.getHeight() * s);
            if (pt.x >= x && pt.x <= x + w && pt.y >= y && pt.y <= y + h) {
                return p;
            }
        }
        return null;
    }

    /** Юго-восточный хват растягивания — попадание только у ВЫДЕЛЕННОГО блока
     *  (тот же приём, что видимость точек излома только у выделенной связи),
     *  иначе канвас с десятками блоков был бы захламлён хватами отовсюду. */
    private NetworkDevicePlacement resizeHandleAt(Point pt) {
        if (selected == null) {
            return null;
        }
        double s = scale();
        double hx = selected.getXMm() + selected.getWidth() - RESIZE_HANDLE_PX / s;
        double hy = selected.getYMm() + selected.getHeight() - RESIZE_HANDLE_PX / s;
        int x = (int) (PADDING + hx * s);
        int y = (int) (PADDING + hy * s);
        int x2 = (int) (PADDING + (selected.getXMm() + selected.getWidth()) * s);
        int y2 = (int) (PADDING + (selected.getYMm() + selected.getHeight()) * s);
        if (pt.x >= x && pt.x <= x2 && pt.y >= y && pt.y <= y2) {
            return selected;
        }
        return null;
    }

    /** Полный маршрут связи в КООРДИНАТАХ КАНВАСА: порт-источник, точки излома по
     *  порядку, порт-приёмник — {@code null}, если один из концов ссылается на
     *  уже удалённое устройство (защитно, не должно происходить в норме — {@link
     *  #deleteDeviceEntirely} чистит связи вместе с устройством). */
    private List<double[]> routePoints(NetworkLink link) {
        NetworkDevicePlacement from = NetworkTopology.deviceById(plan, link.getFromDeviceId());
        NetworkDevicePlacement to = NetworkTopology.deviceById(plan, link.getToDeviceId());
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
        for (NetworkLink link : plan.getLinks()) {
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
        for (NetworkDevicePlacement p : plan.getDevices()) {
            maxX = Math.max(maxX, p.getXMm() + p.getWidth());
            maxY = Math.max(maxY, p.getYMm() + p.getHeight());
        }
        int w = (int) (maxX * s) + PADDING * 2;
        int h = (int) (maxY * s) + PADDING * 2;
        return new Dimension(Math.max(REF_VIEWPORT_W, w), Math.max(REF_VIEWPORT_H, h));
    }

    /** Границы сети в КООРДИНАТАХ КАНВАСА (до масштаба/{@link #PADDING}) — живьём
     *  пересчитываются из фактических координат СВОИХ устройств (см. {@link
     *  NetworkTopology#devicesInNetwork}) и точек излома СВОИХ связей (не
     *  кэшируется — тот же принцип, что {@link #resolveLabel}), с отступом
     *  {@link #NETWORK_BG_PADDING}. {@code null} для сети без устройств
     *  (пустая, только что созданная) — рисовать подложку не вокруг чего. */
    private java.awt.geom.Rectangle2D networkBoundsCanvasUnits(Network network) {
        List<NetworkDevicePlacement> devs = NetworkTopology.devicesInNetwork(plan, network.getId());
        if (devs.isEmpty()) {
            return null;
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (NetworkDevicePlacement p : devs) {
            minX = Math.min(minX, p.getXMm());
            minY = Math.min(minY, p.getYMm());
            maxX = Math.max(maxX, p.getXMm() + p.getWidth());
            maxY = Math.max(maxY, p.getYMm() + p.getHeight());
        }
        for (NetworkLink link : NetworkTopology.linksInNetwork(plan, network.getId())) {
            for (NetworkLinkWaypoint w : link.getWaypoints()) {
                minX = Math.min(minX, w.getX());
                minY = Math.min(minY, w.getY());
                maxX = Math.max(maxX, w.getX());
                maxY = Math.max(maxY, w.getY());
            }
        }
        return new java.awt.geom.Rectangle2D.Double(minX - NETWORK_BG_PADDING, minY - NETWORK_BG_PADDING,
                (maxX - minX) + NETWORK_BG_PADDING * 2, (maxY - minY) + NETWORK_BG_PADDING * 2);
    }

    /** Полупрозрачная цветная подложка вокруг устройств одной сети (запрос
     *  пользователя: "разные сети... должны быть в одном экране, но визуально
     *  отличаться, типа как отдельные цветные подложки") — цвет берётся из
     *  {@link #resolveNetworkColor}, "текущая" (см. {@link #currentNetwork})
     *  сеть рисуется заметно ярче/непрозрачнее и сплошной обводкой (визуально
     *  однозначно, куда сейчас попадут новые устройства), остальные —
     *  приглушённо, пунктирной обводкой. */
    private void drawNetworkBackground(Graphics2D g2, Network network, double s, float zoomF) {
        java.awt.geom.Rectangle2D bounds = networkBoundsCanvasUnits(network);
        if (bounds == null) {
            return;
        }
        Color color = resolveNetworkColor(network);
        boolean active = network == currentNetwork;
        int x = (int) (PADDING + bounds.getX() * s);
        int y = (int) (PADDING + bounds.getY() * s);
        int w = (int) (bounds.getWidth() * s);
        int h = (int) (bounds.getHeight() * s);

        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), active ? 48 : 26));
        g2.fillRoundRect(x, y, w, h, 18, 18);
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), active ? 220 : 130));
        g2.setStroke(active
                ? new BasicStroke(clampF(2.2f * zoomF, 1.5f, 4f))
                : new BasicStroke(clampF(1.3f * zoomF, 1f, 3f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                        0, new float[]{6f, 4f}, 0));
        g2.drawRoundRect(x, y, w, h, 18, 18);

        String label = networkDisplayName(network);
        g2.setFont(getFont().deriveFont(Font.BOLD, clampF(13f * zoomF, 10f, 22f)));
        g2.setColor(color.darker());
        g2.drawString(label, x + 10, y + g2.getFontMetrics().getAscent() + 6);
    }

    /** Рисует одну связь (включая точки излома выделенной связи) заданным
     *  цветом сети. */
    private void drawLink(Graphics2D g2, NetworkLink link, Color linkColor, double s, float zoomF) {
        List<double[]> pts = routePoints(link);
        if (pts == null) {
            return;
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

    /** Отрисовка в изображение заданного размера — экспорт «карты сети» в
     *  JPEG (запрос пользователя, см. {@code ui.stage.CurrentSchemeExporter}),
     *  та же схема, что {@code SchemaCanvasPanel}/{@code SceneCanvasPanel}:
     *  вызывающая сторона строит ОДНОРАЗОВЫЙ экземпляр канваса (свежий {@code
     *  zoom=1.0} — логический масштаб 1:1 независимо от того, что сейчас
     *  выставлено в интерактивном виде у пользователя), передаёт план через
     *  {@link #setPlan} и берёт размер из {@link #getPreferredSize()}, как
     *  тут. {@code dpiScale} — множитель качества ({@code dpi/72.0}), не
     *  интерактивный zoom — геометрия считается в тех же логических {@code
     *  width}/{@code height}, просто растеризуется в больше физических
     *  пикселей (тот же приём, что {@code SchemaCanvasPanel#renderImage}). */
    public java.awt.image.BufferedImage renderImage(int width, int height, double dpiScale) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                Math.max(1, (int) Math.round(width * dpiScale)), Math.max(1, (int) Math.round(height * dpiScale)),
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.scale(dpiScale, dpiScale);
        g2.setColor(Palette.BG);
        g2.fillRect(0, 0, width, height);
        paint(g2);
        g2.dispose();
        return img;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        paint(g2);
        g2.dispose();
    }

    /** Тело отрисовки, общее для интерактивного вида ({@link #paintComponent})
     *  и статического экспорта ({@link #renderImage}) — не трогает
     *  {@code getWidth()}/{@code getHeight()} панели напрямую (кроме {@link
     *  #drawSnapGuides}, который на СВЕЖЕМ экспортном экземпляре не
     *  вызывается содержательно — {@code snapGuideX}/{@code snapGuideY} там
     *  всегда {@code null}, перетаскивание не идёт). */
    private void paint(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        double s = scale();
        float zoomF = (float) zoom;
        Font labelFont = getFont().deriveFont(Font.BOLD, clampF(12f * zoomF, 9f, 24f));
        Font ipFont = getFont().deriveFont(Font.PLAIN, clampF(10f * zoomF, 8f, 20f));

        // Цветные подложки сетей -- САМЫЙ нижний слой, под связями и блоками.
        for (Network network : plan.getNetworks()) {
            drawNetworkBackground(g2, network, s, zoomF);
        }

        // Связи -- под блоками, чтобы порты/блоки оставались кликабельны поверх линий.
        // Цвет -- по СТАТУСУ соединения, если он известен (запрос пользователя: "цвета
        // линий должны обозначать текущий статус соединения, если есть информация"),
        // иначе -- по сети, которой принадлежит связь (резолв живьём по порту-источнику,
        // см. NetworkTopology#networkOfLink, не по хранимой ссылке -- её у связи нет).
        for (NetworkLink link : plan.getLinks()) {
            Network owner = NetworkTopology.networkOfLink(plan, link);
            Color statusColor = linkStatusColor(link, owner);
            Color linkColor = statusColor != null ? statusColor
                    : owner != null ? resolveNetworkColor(owner) : COLOR_LINK_ORPHAN;
            drawLink(g2, link, linkColor, s, zoomF);
        }

        List<NetworkDevicePlacement> devices = plan.getDevices();
        for (NetworkDevicePlacement p : devices) {
            int x = (int) (PADDING + p.getXMm() * s);
            int y = (int) (PADDING + p.getYMm() * s);
            int w = (int) (p.getWidth() * s);
            int h = (int) (p.getHeight() * s);

            boolean ipConflict = NetworkIpConflicts.hasAnyConflict(plan, p);

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
            if (isSelected) {
                // Хват растягивания -- виден только у выделенного блока (см. resizeHandleAt).
                int handle = (int) (RESIZE_HANDLE_PX * Math.min(1.0, zoomF));
                g2.setColor(Color.WHITE);
                g2.fillRect(x + w - handle, y + h - handle, handle, handle);
                g2.setColor(Palette.BORDER);
                g2.drawRect(x + w - handle, y + h - handle, handle, handle);
            }

            g2.setFont(labelFont);
            g2.setColor(Color.WHITE);
            String label = resolveLabel(p);
            int textY = y + g2.getFontMetrics().getAscent() + 4;
            g2.drawString(clip(g2, label, w - 8), x + 6, textY);

            // Round 8: адрес живёт на подключении к КАЖДОЙ сети -- своя строка на
            // подключение, а не одна общая (устройство может состоять в нескольких сетях
            // сразу с разными адресами, см. class-javadoc).
            g2.setFont(ipFont);
            int lineHeight = g2.getFontMetrics().getHeight();
            int lineY = textY + lineHeight;
            for (NetworkAttachment att : p.getAttachments()) {
                String ip = att.getIpAddress();
                if (ip == null || ip.isBlank()) {
                    continue;
                }
                boolean conflict = NetworkIpConflicts.hasConflict(plan, p, att);
                if (lineY <= y + h - 4) {
                    // Точка-индикатор доступности -- ТОЛЬКО если для этого адреса есть
                    // данные опроса (запрос пользователя: "если есть информация"), иначе
                    // текст просто начинается без неё, без нейтрального "неизвестно"-цвета.
                    int textX = x + 6;
                    Boolean reachable = availability.get(ip.trim());
                    if (reachable != null) {
                        int dotSize = 7;
                        g2.setColor(reachable ? COLOR_STATUS_UP : COLOR_STATUS_DOWN);
                        g2.fillOval(textX, lineY - dotSize, dotSize, dotSize);
                        textX += dotSize + 4;
                    }
                    g2.setColor(conflict ? COLOR_IP_CONFLICT : new Color(0xd8dee6));
                    String text = networkNameOrPlaceholder(att.getNetworkId()) + ": " + ip + (conflict ? "  ⚠" : "");
                    g2.drawString(clip(g2, text, w - (textX - x) - 4), textX, lineY);
                }
                lineY += lineHeight;
            }

            // Порты -- Ethernet сверху, оптика справа (см. class-javadoc); активный
            // (источник строящейся связи) и валидная цель под курсором -- крупнее и
            // ярче; занятые -- красятся парой по связи (см. linkPairColor).
            int n = effectiveTotalPortCount(p);
            for (int port = 1; port <= n; port++) {
                double[] c = portCenterCanvasUnits(p, port);
                int px = (int) (PADDING + c[0] * s);
                int py = (int) (PADDING + c[1] * s);
                boolean isPendingSource = linkingFrom != null && linkingFrom.device() == p
                        && linkingFrom.port() == port;
                boolean isHoveredTarget = hoveredPort != null && hoveredPort.device() == p
                        && hoveredPort.port() == port;
                int size = (isPendingSource || isHoveredTarget) ? PORT_HIGHLIGHT_SIZE_PX : PORT_SIZE_PX;
                Color fill = isPendingSource ? COLOR_LINK_PENDING
                        : isHoveredTarget ? COLOR_PORT_TARGET_HOVER : portOccupancyColor(p, port);
                g2.setColor(fill);
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
    }

    /** Цвет заливки порта в состоянии покоя — {@link #COLOR_PORT} (нейтральный),
     *  если порт свободен, иначе цвет пары связи, которая его занимает (см.
     *  {@link #linkPairColor}, запрос пользователя "занятые порты должны
     *  попарно краситься в цвета"). */
    private Color portOccupancyColor(NetworkDevicePlacement device, int port) {
        NetworkLink link = linkAtPort(device, port);
        return link != null ? linkPairColor(link) : COLOR_PORT;
    }

    /** Связь, занимающая {@code port} устройства {@code device} — {@code null},
     *  если порт свободен. Используется и для цвета порта ({@link
     *  #portOccupancyColor}), и для тултипа ({@link #portTooltip}). */
    private NetworkLink linkAtPort(NetworkDevicePlacement device, int port) {
        for (NetworkLink link : plan.getLinks()) {
            boolean matchesFrom = device.getId().equals(link.getFromDeviceId()) && link.getFromPort() == port;
            boolean matchesTo = device.getId().equals(link.getToDeviceId()) && link.getToPort() == port;
            if (matchesFrom || matchesTo) {
                return link;
            }
        }
        return null;
    }

    /** Подсказка при наведении на порт (одобрено пользователем: "подпись
     *  порта («Порт 3 → Stage Left») — в тултипе") — просто номер для
     *  свободного порта, номер + куда ведёт связь (имя устройства на другом
     *  конце, живьём резолвленное через {@link #resolveLabel}, + подпись
     *  самой связи в скобках, если задана) для занятого. */
    private String portTooltip(PortHit hit) {
        String base = "Порт " + hit.port();
        NetworkLink link = linkAtPort(hit.device(), hit.port());
        if (link == null) {
            return base;
        }
        boolean isFrom = hit.device().getId().equals(link.getFromDeviceId());
        String otherId = isFrom ? link.getToDeviceId() : link.getFromDeviceId();
        NetworkDevicePlacement other = NetworkTopology.deviceById(plan, otherId);
        String target = other != null ? resolveLabel(other) : "?";
        String linkLabel = link.getLabel();
        return base + " → " + target + (linkLabel != null && !linkLabel.isBlank() ? " (" + linkLabel + ")" : "");
    }

    /** Направляющие линии Shift-снаппинга (точки излома и размера блока) —
     *  яркая пунктирная линия через всю видимую область, тот же приём (и даже
     *  тот же цвет), что {@code SchemaCanvasPanel#drawSnapGuides}. {@link
     *  #snapGuideX}/{@link #snapGuideY} — координаты канваса, переводятся в px
     *  через {@code PADDING}+масштаб, в отличие от {@code SchemaCanvasPanel}
     *  (там весь {@code Graphics2D} уже промасштабирован глобально, тут —
     *  нет, см. class-javadoc). */
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
