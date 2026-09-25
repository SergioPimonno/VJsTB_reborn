package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EdgeRouteMode;
import com.vjstb.ledscheme.model.EdgeWaypoint;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.NodeOrientation;
import com.vjstb.ledscheme.model.NodeSide;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.PortPlacement;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.PowerConnectorType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.settings.ArrowPlacement;
import com.vjstb.ledscheme.settings.ConnectorDisplayMode;
import com.vjstb.ledscheme.settings.SchemaRenderMode;
import com.vjstb.ledscheme.settings.SchemaStylePreset;
import com.vjstb.ledscheme.settings.WireHopStyle;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/**
 * Холст общей схемы площадки (yEd-подобный): узлы-оборудование (источник,
 * распределение, конвертер, медиасервер, контроллер, прочее) и узлы-ссылки на
 * реальные экраны сцены, соединённые линиями. Один и тот же класс используется
 * и для схемы питания, и для схемы сигнала — какая именно определяется полем mode.
 */
public class SchemaCanvasPanel extends JPanel {

    private static final int MARGIN = 40;
    private static final int RESIZE_HANDLE = 14;
    private static final double MIN_NODE_W = 110;
    private static final double MIN_NODE_H = 44;
    private static final double MIN_SCALE = 0.3;
    private static final double MAX_SCALE = 2.5;
    private final AppModel model;
    private final SchemaMode mode;
    private final com.vjstb.ledscheme.settings.SettingsManager settings;
    private Runnable onChanged = () -> { };
    private Consumer<Screen> onScreenActivated;

    public enum Interaction { MOVE, CONNECT }

    private Interaction interaction = Interaction.MOVE;
    private SchemaNode dragNode;
    private double dragOffX, dragOffY;
    /** Стартовые (x,y) КАЖДОГО выделенного узла на момент начала перетаскивания
     *  {@link #dragNode} — позволяет тащить ВСЕ выделенные узлы разом, сохраняя
     *  их взаимное расположение (см. mouseDragged): дельта считается по anchor'у
     *  ({@link #dragNode}), затем применяется ко всем остальным от их собственных
     *  стартовых координат. Очищается по отпусканию кнопки. */
    private final java.util.Map<SchemaNode, double[]> dragStartPositions = new java.util.LinkedHashMap<>();
    private SchemaNode resizeNode;
    /** Точка излома связи, которую сейчас тащат мышью (см. Task #85/v1.4) — null,
     *  если ничего не тащат. */
    private SchemaEdge draggingWaypointEdge;
    private int draggingWaypointIndex = -1;
    /** Отрезок маршрута ВЫДЕЛЕННОЙ связи, который тащат целиком — null, если не
     *  тащат. Двигаются обе его точки излома ({@link #draggingSegmentWpA} и
     *  {@link #draggingSegmentWpB}, индексы в {@code edge.getWaypoints()}) на одну
     *  дельту; крайние отрезки, упирающиеся в гнездо/узел, за целое не тащатся —
     *  при необходимости пользователь сам добавит точку излома. */
    private SchemaEdge draggingSegmentEdge;
    private int draggingSegmentWpA = -1;
    private int draggingSegmentWpB = -1;
    private Point draggingSegmentPressMp;
    private double[] draggingSegmentStartA;
    private double[] draggingSegmentStartB;
    /** Был ли реальный сдвиг за время нажатия на отрезок — как у чипа подписи
     *  ({@link #draggingLabelMoved}): клик без движения не должен ни писать снимок
     *  в отмену, ни менять маршрут, просто оставляет связь выделенной. */
    private boolean draggingSegmentMoved;
    /** Чип подписи связи, который сейчас тащат мышью (Task #3) — null, если ничего
     *  не тащат. Отличие от точки излома: короткий клик без реального сдвига должен
     *  по-прежнему открывать редактор подписи (см. draggingLabelMoved), а не просто
     *  сбрасывать смещение в исходное. */
    private SchemaEdge draggingLabelEdge;
    private double draggingLabelStartDx;
    private double draggingLabelStartDy;
    private Point draggingLabelPressMp;
    private boolean draggingLabelMoved;

    /** Перетаскивание ГРУППЫ гнёзд на другую сторону/позицию (docs/schema-ports-
     *  rework/PLAN.md, задача T3.3) — начинается нажатием на пин В РЕЖИМЕ
     *  «Перемещение» (см. mousePressed: проверяется РАНЬШЕ обычного перетаскивания
     *  узла, симметрично {@link #handleRightClick}, который так же проверяет
     *  {@link #socketAt} раньше {@link #nodeAt}). {@code null} — сейчас не тащат.
     *  В режиме «Соединение» нажатие на пин по-прежнему начинает связь (см. ветку
     *  {@code Interaction.CONNECT} выше в mousePressed) — эти два поля друг друга
     *  не касаются. */
    private SchemaNode draggingGroupNode;
    private CardPort draggingGroupPort;
    private Point draggingGroupPressMp;
    private boolean draggingGroupMoved;
    /** Текущее превью цели драга группы (см. {@link
     *  com.vjstb.ledscheme.service.schemalayout.GroupDropTarget}) — пересчитывается
     *  на каждый mouseDragged, используется и для рамки-подсказки в paint(), и как
     *  окончательное значение на mouseReleased (сторона+порядок фиксируются РОВНО
     *  там, куда указывало последнее превью, что видел пользователь). */
    private com.vjstb.ledscheme.model.NodeSide draggingGroupPreviewSide;
    private Double draggingGroupPreviewOrder;

    private String connectPendingId;
    /** Гнездо (CardPort), от которого начато соединение — только когда включена
     *  настройка «коммутация через гнёзда разъёмов»; null — соединение идёт от
     *  узла целиком, как раньше. */
    private String connectPendingPortId;
    /** Кабинет-«гнездо» (см. AppModel.chainEndpointSocketCabinetIds), от которого
     *  начато соединение — независимая ось от {@link #connectPendingPortId} (у одного
     *  начатого соединения задан не более чем один из двух). null — как раньше. */
    private String connectPendingCabinetInstanceId;
    private SocketHit hoveredSocket;
    private CabinetSocketHit hoveredCabinetSocket;
    /** Связь под курсором в режиме «Перемещение» (docs/schema-ports-rework/PLAN.md,
     *  задача T4.5/D14) — нужна ТОЛЬКО чтобы решить, показывать ли пустой чип-
     *  приглашение «+ подпись» (см. {@link #shouldShowEmptyLabelChip}); саму связь
     *  под курсором (для перетаскивания/меню) по-прежнему находит {@link #edgeAt}
     *  напрямую в момент клика, этот кэш — только для перерисовки при наведении. */
    private SchemaEdge hoveredEdge;
    private Point lastMouse;
    /** true — текущая отрисовка идёт в {@link #renderImage} (экспорт), а не на
     *  живой холст (docs/schema-ports-rework/PLAN.md, задача T4.5/D14) — пустой чип-
     *  приглашение «+ подпись» в экспорт не идёт вовсе (некликабелен на статичной
     *  картинке, только шум), см. {@link #shouldShowEmptyLabelChip}. */
    private boolean exporting;

    /** Масштаб отрисовки схемы — 1.0 = как раньше (не было вовсе); Ctrl+колесо
     *  меняет его (см. mouseWheelMoved), применяется как Graphics2D.scale в paint(). */
    private double scale = 1.0;
    /** Активные направляющие линии привязки (Shift-перетаскивание, см.
     *  snapPosition) — модельные координаты; null — сейчас не привязано ни к чему
     *  по этой оси. Рисуются в paint() и сбрасываются при отпускании/без Shift. */
    private Double snapGuideX;
    private Double snapGuideY;

    /** Зажатая СКМ — перемещение вьюпорта охватывающего JScrollPane (см.
     *  mousePressed/mouseDragged/mouseReleased ниже); null — сейчас не тащим. */
    private Point panStartScreen;
    private Point panStartViewPosition;

    /** Множественное выделение узлов (баг-репорт: "возможность выделять несколько
     *  блоков... для перетаскивания и удаления") — клик по узлу с зажатым Shift
     *  или Ctrl добавляет/убирает его из выделения без начала перетаскивания;
     *  протяжка мышью по ПУСТОМУ месту холста рисует прямоугольник-«резинку»
     *  (см. {@link #rubberBandStart}) и выделяет все узлы, пересекшиеся с ним.
     *  Порядок вставки (LinkedHashSet) не используется для логики, просто
     *  предсказуем при отладке. */
    private final Set<SchemaNode> selectedNodes = new LinkedHashSet<>();
    private SchemaEdge selectedEdge;
    /** Многовыделение связей (баг-репорт пользователя 2026-09-18: "не работает
     *  групповое выделение линий, только по одной") — Shift/Ctrl+клик по связи
     *  добавляет/убирает её из набора, как уже работало для {@link #selectedNodes}.
     *  {@link #selectedEdge} остаётся «главной»/последней кликнутой связью для
     *  одиночных детальных операций, для которых многовыделение не имеет смысла —
     *  перетаскивание ТОЧКИ ИЗЛОМА или ОТРЕЗКА, редактирование подписи (см. {@link
     *  #waypointAt}/{@link #segmentAt}/{@link #edgeLabelChipAt} — не тронуты, как и
     *  видимость точек излома только у {@code selectedEdge}). {@link #selectedEdges}
     *  — источник истины для подсветки (какие связи рисуются акцентным цветом) и
     *  групповых операций (удаление, «Перетрассировать выделенные» — см. {@link
     *  #rerouteSelected}/{@link #getSelectedEdges}); ВСЕГДА содержит {@code
     *  selectedEdge}, если тот не {@code null} (инвариант поддерживается на каждом
     *  присваивании {@code selectedEdge}, а не проверяется отдельно). */
    private final Set<SchemaEdge> selectedEdges = new LinkedHashSet<>();

    /** Устанавливает ОДНУ связь как единственное выделение (обычный клик без
     *  Shift/Ctrl, либо программное выделение) — синхронизирует {@link
     *  #selectedEdge}/{@link #selectedEdges} одним вызовом вместо ручной
     *  поддержки инварианта в каждом месте. {@code null} снимает выделение
     *  связей целиком (клик попал во что-то другое — узел, гнездо, resize-хват
     *  и т.п. — те продолжают явно вызывать {@code selectSingleEdge(null)}, а не
     *  просто {@code selectedEdge = null}, чтобы не забыть про {@link
     *  #selectedEdges}). */
    private void selectSingleEdge(SchemaEdge edge) {
        selectedEdge = edge;
        selectedEdges.clear();
        if (edge != null) {
            selectedEdges.add(edge);
        }
    }

    /** Shift/Ctrl+клик по связи — добавить/убрать её из многовыделения, без
     *  сброса остальных (баг-репорт пользователя 2026-09-18: "не работает
     *  групповое выделение линий, только по одной") — тот же приём, что уже был
     *  у {@link #selectedNodes} (см. обработчик клика по узлу чуть выше). {@link
     *  #selectedEdge} после такого клика — сама связь, если её ДОБАВИЛИ (новая
     *  «главная» для детальных операций типа перетаскивания точки излома), иначе
     *  (убрали) — любая оставшаяся связь набора, либо {@code null}, если набор
     *  опустел. */
    private void toggleEdgeSelection(SchemaEdge edge) {
        if (!selectedEdges.remove(edge)) {
            selectedEdges.add(edge);
            selectedEdge = edge;
        } else {
            selectedEdge = selectedEdges.isEmpty() ? null : selectedEdges.iterator().next();
        }
    }

    /** Внутренний буфер обмена схемы (Ctrl+C / Ctrl+V по многовыделению) — общий
     *  для всех холстов схемы в сессии (static), поэтому скопировать можно на одной
     *  панели, а вставить на другой (в т.ч. в другом режиме — {@code mode}
     *  перештамповывается при вставке в {@code AppModel.pasteSchemaNodes}). Хранит
     *  ГЛУБОКИЕ копии узлов (с полной комплектацией карт/разъёмов, см.
     *  {@link SchemaNode#copy()}) и связей строго МЕЖДУ ними на момент Ctrl+C —
     *  ссылок на живую модель тут нет, поэтому копия переживает удаление оригинала. */
    private static final List<SchemaNode> clipboardNodes = new ArrayList<>();
    private static final List<SchemaEdge> clipboardEdges = new ArrayList<>();
    /** Сколько раз подряд вставляли текущий буфер — каждая следующая вставка
     *  смещается чуть дальше (см. {@link #PASTE_OFFSET}), чтобы копии не ложились
     *  ровно одна на другую (как в yEd). Сбрасывается при каждом Ctrl+C. */
    private static int clipboardPasteSequence = 0;
    /** Смещение вставленных копий от оригинала, модельные пиксели (на каждую
     *  последующую вставку подряд — кратное). */
    private static final double PASTE_OFFSET = 24;
    /** Начало/текущая точка прямоугольника-«резинки» выделения (модельные
     *  координаты) — null, если сейчас не тянется. См. {@link #selectedNodes}. */
    private Point rubberBandStart;
    private Point rubberBandCurrent;

    /** Прямоугольник значка "⚠" (в экранных координатах) для каждого перегруженного
     *  узла на ПОСЛЕДНЕЙ отрисовке — используется только для наведения мыши
     *  (getToolTipText), пересчитывается заново в каждом paintComponent. */
    private final java.util.Map<SchemaNode, java.awt.Rectangle> overloadIconRects = new java.util.HashMap<>();

    /** Одно гнездо разъёма конкретного узла — попадание клика/наведения мыши. */
    private record SocketHit(SchemaNode node, CardPort port) { }

    /** Все цвета/толщины текущей отрисовки (docs/schema-ports-rework/PLAN.md,
     *  задача T3.1) — пересчитывается в начале каждого {@link #paint(Graphics2D,
     *  int, int, boolean)} (см. {@link #currentStyle()}), т.к. "Экранный" пресет
     *  следует живой теме/акценту {@link Palette}, которые могут смениться между
     *  кадрами. Поле, а не параметр во всех приватных методах отрисовки — тот же
     *  приём, что уже используют {@link #mode}/{@link #model}. Дефолт на случай
     *  вызова геттеров вне {@code paint()} (например, {@link #nodeColor}, см. его
     *  javadoc). */
    private SchemaStyle style = SchemaStyle.screen();

    public SchemaCanvasPanel(AppModel model, SchemaMode mode, com.vjstb.ledscheme.settings.SettingsManager settings) {
        this.model = model;
        this.mode = mode;
        this.settings = settings;
        setBackground(Palette.BG);
        setFocusable(true);
        // Переключатель "экран блоком/схемой" в Персонализации должен сразу
        // отразиться на уже открытой схеме, не только при следующем открытии панели
        // (тот же слушатель ловит и смену пресета оформления схемы, см. SchemaStyle).
        settings.addListener(this::repaint);
        // Непустое значение включает механизм подсказок Swing вообще — сам текст
        // подставляется динамически через переопределённый getToolTipText(MouseEvent)
        // ниже (наведение на конкретный значок "⚠" перегрузки узла).
        setToolTipText("");

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    // Зажатая СКМ — перемещение по схеме без слайдеров (по просьбе
                    // пользователя, привычно из графических/CAD-редакторов).
                    javax.swing.JScrollPane sp = (javax.swing.JScrollPane)
                            SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, SchemaCanvasPanel.this);
                    if (sp != null) {
                        panStartScreen = e.getLocationOnScreen();
                        panStartViewPosition = sp.getViewport().getViewPosition();
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    }
                    return;
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    handleRightClick(e);
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                Point mp = toModel(e.getPoint());
                SchemaNode hit = nodeAt(mp);
                if (interaction == Interaction.CONNECT) {
                    // Чип подписи связи ("+ подпись"/уже назначенная подпись) должен
                    // открывать редактор подписи независимо от текущего инструмента
                    // (баг-репорт: назначение подписи работало только в режиме
                    // "Перемещение", т.к. этот CONNECT-блок всегда завершался return
                    // раньше, чем управление доходило до проверки чипа ниже,
                    // применявшейся только в ветке MOVE — см. Task #95/v1.5).
                    SchemaEdge chipHitConnect = edgeLabelChipAt(mp);
                    if (chipHitConnect != null) {
                        selectedNodes.clear();
                        selectSingleEdge(chipHitConnect);
                        repaint();
                        editEdgeLabel(chipHitConnect);
                        return;
                    }
                    CabinetSocketHit cabinetHit = cabinetSocketAt(mp);
                    if (cabinetHit != null) {
                        if (connectPendingId == null) {
                            connectPendingId = cabinetHit.node().getId();
                            connectPendingPortId = null;
                            connectPendingCabinetInstanceId = cabinetHit.cabinetInstanceId();
                        } else if (connectPendingId.equals(cabinetHit.node().getId())
                                && cabinetHit.cabinetInstanceId().equals(connectPendingCabinetInstanceId)) {
                            connectPendingId = null;
                            connectPendingCabinetInstanceId = null;
                        } else {
                            try {
                                model.addSchemaEdge(mode, connectPendingId, connectPendingPortId,
                                        connectPendingCabinetInstanceId, cabinetHit.node().getId(), null,
                                        cabinetHit.cabinetInstanceId(), null,
                                        settings.activeProfile().getNewEdgeRouteMode());
                                onChanged.run();
                            } catch (RuntimeException ex) {
                                JOptionPane.showMessageDialog(SchemaCanvasPanel.this, ex.getMessage(),
                                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                            }
                            connectPendingId = null;
                            connectPendingPortId = null;
                            connectPendingCabinetInstanceId = null;
                        }
                        repaint();
                        return;
                    }
                    boolean socketMode = settings.activeProfile().isSocketWiringEnabled(mode);
                    SocketHit socketHit = socketMode ? socketAt(mp) : null;
                    if (socketHit != null) {
                        if (connectPendingId == null) {
                            connectPendingId = socketHit.node().getId();
                            connectPendingPortId = socketHit.port().getId();
                            connectPendingCabinetInstanceId = null;
                        } else if (connectPendingId.equals(socketHit.node().getId())) {
                            connectPendingId = null;
                            connectPendingPortId = null;
                            connectPendingCabinetInstanceId = null;
                        } else {
                            SchemaNode fromNode = nodeById(connectPendingId);
                            CardPort fromPort = findPort(connectPendingId, connectPendingPortId);
                            CardPort toPort = findPort(socketHit.node().getId(), socketHit.port().getId());
                            String capError = capacityError(fromPort, connectPendingPortId,
                                    toPort, socketHit.port().getId());
                            String dirError = capError == null
                                    ? directionError(fromNode, fromPort, socketHit.node(), toPort) : null;
                            if (capError != null || dirError != null) {
                                JOptionPane.showMessageDialog(SchemaCanvasPanel.this, capError != null ? capError : dirError,
                                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                            } else {
                                try {
                                    model.addSchemaEdge(mode, connectPendingId, connectPendingPortId,
                                            connectPendingCabinetInstanceId, socketHit.node().getId(),
                                            socketHit.port().getId(), null, null,
                                            settings.activeProfile().getNewEdgeRouteMode());
                                    onChanged.run();
                                } catch (RuntimeException ex) {
                                    JOptionPane.showMessageDialog(SchemaCanvasPanel.this, ex.getMessage(),
                                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                                }
                            }
                            connectPendingId = null;
                            connectPendingPortId = null;
                            connectPendingCabinetInstanceId = null;
                        }
                        repaint();
                        return;
                    }
                    if (hit == null) {
                        connectPendingId = null;
                        connectPendingPortId = null;
                        connectPendingCabinetInstanceId = null;
                    } else if (connectPendingId == null) {
                        connectPendingId = hit.getId();
                        connectPendingPortId = null;
                        connectPendingCabinetInstanceId = null;
                    } else if (connectPendingId.equals(hit.getId())) {
                        connectPendingId = null;
                        connectPendingPortId = null;
                        connectPendingCabinetInstanceId = null;
                    } else {
                        try {
                            model.addSchemaEdge(mode, connectPendingId, connectPendingPortId,
                                    connectPendingCabinetInstanceId, hit.getId(), null, null, null,
                                    settings.activeProfile().getNewEdgeRouteMode());
                        } catch (RuntimeException ex) {
                            JOptionPane.showMessageDialog(SchemaCanvasPanel.this, ex.getMessage(),
                                    "Ошибка", JOptionPane.ERROR_MESSAGE);
                        }
                        connectPendingId = null;
                        connectPendingPortId = null;
                        connectPendingCabinetInstanceId = null;
                        onChanged.run();
                    }
                    repaint();
                    return;
                }
                // Нажатие на пин В РЕЖИМЕ «Перемещение» — начало перетаскивания ГРУППЫ
                // гнёзд (не узла целиком), проверяется раньше resizeHandleAt/nodeAt
                // ниже — симметрично handleRightClick, который так же ставит socketAt
                // впереди nodeAt (PLAN.md, задача T3.3).
                // Перетаскивание ГРУППЫ гнёзд — только MODERN (docs/schema-ports-
                // rework/PLAN.md, задача T5.5): в CLASSIC гнёзда не группируются
                // через PortPlacement/NodePortLayout, нажатие на пин должно вести
                // себя как раньше — то есть просто не совпасть ни с чем здесь и
                // провалиться до обычного перетаскивания узла целиком ниже.
                SocketHit groupDragHit = classicMode() ? null : socketAt(mp);
                if (groupDragHit != null) {
                    selectedNodes.clear();
                    selectedNodes.add(groupDragHit.node());
                    selectSingleEdge(null);
                    draggingGroupNode = groupDragHit.node();
                    draggingGroupPort = groupDragHit.port();
                    draggingGroupPressMp = mp;
                    draggingGroupMoved = false;
                    draggingGroupPreviewSide = null;
                    draggingGroupPreviewOrder = null;
                    repaint();
                    return;
                }
                SchemaNode resizeHit = resizeHandleAt(mp);
                if (resizeHit != null) {
                    // Хват за уголок ВСЕГДА сужает выделение до одного узла — resize
                    // осмыслен только для одного блока за раз (см. isAutoSizedScreenWiringNode
                    // и сам resize-drag ниже, которые оперируют ровно одним resizeNode).
                    selectedNodes.clear();
                    selectedNodes.add(resizeHit);
                    selectSingleEdge(null);
                    resizeNode = resizeHit;
                    repaint();
                    return;
                }
                WaypointHit wpHit = waypointAt(mp);
                if (wpHit != null) {
                    selectSingleEdge(wpHit.edge());
                    selectedNodes.clear();
                    materializeAutoRouteIfNeeded(wpHit.edge());
                    draggingWaypointEdge = wpHit.edge();
                    draggingWaypointIndex = wpHit.index();
                    repaint();
                    return;
                }
                if (hit != null) {
                    // Shift/Ctrl+клик по узлу — добавить/убрать его из выделения БЕЗ
                    // начала перетаскивания (баг-репорт: "возможность выделять несколько
                    // блоков... для перетаскивания и удаления") — как и в большинстве
                    // редакторов схем, такой клик не двигает блок сам по себе.
                    if (e.isShiftDown() || e.isControlDown()) {
                        if (!selectedNodes.remove(hit)) {
                            selectedNodes.add(hit);
                        }
                        selectSingleEdge(null);
                        repaint();
                        return;
                    }
                    // Обычный клик по узлу, УЖЕ входящему в многовыделение — не сбрасывает
                    // его (тащить нужно ВСЮ группу), иначе (клик вне текущего выделения,
                    // либо выделения не было) — сужает выделение до этого одного узла.
                    if (!selectedNodes.contains(hit)) {
                        selectedNodes.clear();
                        selectedNodes.add(hit);
                    }
                    selectSingleEdge(null);
                    dragNode = hit;
                    dragOffX = mp.x - hit.getX();
                    dragOffY = mp.y - hit.getY();
                    dragStartPositions.clear();
                    for (SchemaNode n : selectedNodes) {
                        dragStartPositions.put(n, new double[]{n.getX(), n.getY()});
                    }
                    repaint();
                    return;
                }
                SchemaEdge chipHit = edgeLabelChipAt(mp);
                if (chipHit != null) {
                    selectedNodes.clear();
                    selectSingleEdge(chipHit);
                    // Не открываем редактор подписи сразу по нажатию — короткий клик
                    // без сдвига мыши откроет его в mouseReleased (см. draggingLabelMoved),
                    // а реальное перетаскивание сместит чип (Task #3).
                    draggingLabelEdge = chipHit;
                    draggingLabelStartDx = chipHit.getLabelDx();
                    draggingLabelStartDy = chipHit.getLabelDy();
                    draggingLabelPressMp = mp;
                    draggingLabelMoved = false;
                    repaint();
                    return;
                }
                // Перетаскивание целого отрезка маршрута ВЫДЕЛЕННОЙ связи (после
                // узла/гнезда/чипа — те приоритетнее). Двигаются обе точки излома
                // отрезка на одну дельту; крайние отрезки segmentAt не отдаёт.
                SegmentHit segHit = segmentAt(mp);
                if (segHit != null) {
                    selectedNodes.clear();
                    selectSingleEdge(segHit.edge());
                    materializeAutoRouteIfNeeded(segHit.edge());
                    draggingSegmentEdge = segHit.edge();
                    draggingSegmentWpA = segHit.wpA();
                    draggingSegmentWpB = segHit.wpB();
                    var wa = draggingSegmentEdge.getWaypoints().get(draggingSegmentWpA);
                    var wb = draggingSegmentEdge.getWaypoints().get(draggingSegmentWpB);
                    draggingSegmentStartA = new double[]{wa.getX(), wa.getY()};
                    draggingSegmentStartB = new double[]{wb.getX(), wb.getY()};
                    draggingSegmentPressMp = mp;
                    draggingSegmentMoved = false;
                    repaint();
                    return;
                }
                // Клик прямо по связи (не узел/гнездо/чип/отрезок — те приоритетнее):
                // Shift/Ctrl добавляет/убирает её из многовыделения БЕЗ сброса узлов
                // (баг-репорт пользователя 2026-09-18: "не работает групповое
                // выделение линий, только по одной") — тот же приём, что уже был у
                // {@link #selectedNodes}. Пусто (не узел, не связь) — Shift/Ctrl
                // добавляет к текущему выделению по завершении протяжки (см.
                // mouseReleased), иначе выделение сбрасывается сразу (клик без
                // движения = просто снять выделение, протяжка ниже — прямоугольник-
                // «резинка», баг-репорт про выделение нескольких блоков).
                SchemaEdge plainEdgeHit = edgeAt(mp);
                if (plainEdgeHit != null && (e.isShiftDown() || e.isControlDown())) {
                    toggleEdgeSelection(plainEdgeHit);
                    repaint();
                    return;
                }
                if (!(e.isShiftDown() || e.isControlDown())) {
                    selectedNodes.clear();
                    selectSingleEdge(null);
                }
                if (plainEdgeHit != null) {
                    selectSingleEdge(plainEdgeHit);
                } else {
                    rubberBandStart = mp;
                    rubberBandCurrent = mp;
                }
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (panStartScreen != null) {
                    javax.swing.JScrollPane sp = (javax.swing.JScrollPane)
                            SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, SchemaCanvasPanel.this);
                    if (sp != null) {
                        Point now = e.getLocationOnScreen();
                        int dx = now.x - panStartScreen.x;
                        int dy = now.y - panStartScreen.y;
                        int maxX = Math.max(0, getWidth() - sp.getViewport().getWidth());
                        int maxY = Math.max(0, getHeight() - sp.getViewport().getHeight());
                        Point newPos = new Point(
                                Math.max(0, Math.min(maxX, panStartViewPosition.x - dx)),
                                Math.max(0, Math.min(maxY, panStartViewPosition.y - dy)));
                        sp.getViewport().setViewPosition(newPos);
                    }
                    return;
                }
                Point mp = toModel(e.getPoint());
                if (resizeNode != null) {
                    double newW = Math.max(MIN_NODE_W, mp.x - resizeNode.getX());
                    double newH = Math.max(MIN_NODE_H, mp.y - resizeNode.getY());
                    // Shift во время растягивания — привязка ПРАВОЙ и НИЖНЕЙ граней
                    // (хват за юго-восточный уголок) к краям/центрам других узлов, как
                    // при перетаскивании. Без Shift — свободный размер, как раньше.
                    if (e.isShiftDown()) {
                        double[] snapped = snapResize(resizeNode, newW, newH);
                        newW = snapped[0];
                        newH = snapped[1];
                    } else {
                        snapGuideX = null;
                        snapGuideY = null;
                    }
                    // Узел-экран в режиме "схема расключения" — тянуть можно за любую
                    // ось, но обводка блока всегда пересчитывается так, чтобы миниатюра
                    // заполняла её БЕЗ пустого поля (см. AppModel.screenWiringHeightForWidth/
                    // screenWiringWidthForHeight, запрос: "размер блока должен
                    // выравниваться в зависимости от текущего размера схемы расключения
                    // с учётом шапки") — берём вариант, что просит БОЛЬШУЮ площадь
                    // (обычно тот, куда пользователь реально потянул).
                    if (resizeNode.getType() == SchemaNodeType.SCREEN
                            && settings.activeProfile().isSchemaScreensAsWiringDiagram()) {
                        Screen scr = screenById(resizeNode.getScreenRefId());
                        if (scr != null) {
                            Double hForW = model.screenWiringHeightForWidth(scr, newW);
                            Double wForH = model.screenWiringWidthForHeight(scr, newH);
                            if (hForW != null && wForH != null) {
                                if (newW * hForW >= wForH * newH) {
                                    newH = Math.max(MIN_NODE_H, hForW);
                                } else {
                                    newW = Math.max(MIN_NODE_W, wForH);
                                }
                            }
                        }
                    }
                    resizeNode.setWidth(newW);
                    resizeNode.setHeight(newH);
                    revalidate();
                    repaint();
                } else if (draggingWaypointEdge != null) {
                    com.vjstb.ledscheme.model.EdgeWaypoint w =
                            draggingWaypointEdge.getWaypoints().get(draggingWaypointIndex);
                    double candidateX = mp.x;
                    double candidateY = mp.y;
                    // Shift во время перетаскивания точки излома провода — привязка к
                    // краям/центрам узлов и к другим точкам излома (см. Task с уточнением:
                    // привязки нужны именно узлам ПРОВОДОВ, а не блокам оборудования —
                    // те двигаются свободно и без Shift, как раньше).
                    if (e.isShiftDown()) {
                        double[] snapped = snapWaypointPosition(draggingWaypointEdge, draggingWaypointIndex,
                                candidateX, candidateY);
                        candidateX = snapped[0];
                        candidateY = snapped[1];
                    } else {
                        snapGuideX = null;
                        snapGuideY = null;
                    }
                    w.setX(candidateX);
                    w.setY(candidateY);
                    repaint();
                } else if (draggingSegmentEdge != null) {
                    // Свободный 2D-перенос всего отрезка: дельта от точки нажатия,
                    // обе точки излома едут на неё от своих стартовых координат.
                    double dx = mp.x - draggingSegmentPressMp.x;
                    double dy = mp.y - draggingSegmentPressMp.y;
                    if (!draggingSegmentMoved && Math.hypot(dx, dy) > 3) {
                        draggingSegmentMoved = true;
                    }
                    if (e.isShiftDown()) {
                        // Привязка по «якорной» точке A (как при групповом драге
                        // узлов), вторая точка B смещается на ту же итоговую дельту —
                        // отрезок не искажается.
                        double[] snapped = snapWaypointPosition(draggingSegmentEdge, draggingSegmentWpA,
                                draggingSegmentStartA[0] + dx, draggingSegmentStartA[1] + dy);
                        dx = snapped[0] - draggingSegmentStartA[0];
                        dy = snapped[1] - draggingSegmentStartA[1];
                    } else {
                        snapGuideX = null;
                        snapGuideY = null;
                    }
                    var wa = draggingSegmentEdge.getWaypoints().get(draggingSegmentWpA);
                    var wb = draggingSegmentEdge.getWaypoints().get(draggingSegmentWpB);
                    wa.setX(draggingSegmentStartA[0] + dx);
                    wa.setY(draggingSegmentStartA[1] + dy);
                    wb.setX(draggingSegmentStartB[0] + dx);
                    wb.setY(draggingSegmentStartB[1] + dy);
                    repaint();
                } else if (draggingLabelEdge != null) {
                    double dx = draggingLabelStartDx + (mp.x - draggingLabelPressMp.x);
                    double dy = draggingLabelStartDy + (mp.y - draggingLabelPressMp.y);
                    if (!draggingLabelMoved
                            && Math.hypot(mp.x - draggingLabelPressMp.x, mp.y - draggingLabelPressMp.y) > 3) {
                        draggingLabelMoved = true;
                    }
                    draggingLabelEdge.setLabelDx(dx);
                    draggingLabelEdge.setLabelDy(dy);
                    repaint();
                } else if (draggingGroupNode != null) {
                    lastMouse = mp;
                    if (!draggingGroupMoved
                            && Math.hypot(mp.x - draggingGroupPressMp.x, mp.y - draggingGroupPressMp.y) > 3) {
                        draggingGroupMoved = true;
                    }
                    if (draggingGroupMoved) {
                        var side = com.vjstb.ledscheme.service.schemalayout.GroupDropTarget.sideFor(
                                draggingGroupNode.getX(), draggingGroupNode.getY(),
                                draggingGroupNode.getWidth(), draggingGroupNode.getHeight(), mp.x, mp.y);
                        List<com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Pin> otherPins = new ArrayList<>();
                        for (var p : nodeLayout(draggingGroupNode).pins()) {
                            if (p.port() != draggingGroupPort) {
                                otherPins.add(p);
                            }
                        }
                        draggingGroupPreviewSide = side;
                        draggingGroupPreviewOrder = com.vjstb.ledscheme.service.schemalayout.GroupDropTarget.orderFor(
                                side, draggingGroupNode.getX(), draggingGroupNode.getY(), otherPins, mp.x, mp.y);
                    }
                    repaint();
                } else if (dragNode != null) {
                    double candidateX = mp.x - dragOffX;
                    double candidateY = mp.y - dragOffY;
                    // Shift во время перетаскивания — привязка к краям/центрам других
                    // узлов (как в yEd): без Shift положение свободное, как раньше.
                    // Работает и при групповом выделении: привязка считается по
                    // якорному узлу (dragNode), остальные выделённые двигаются на ту же
                    // дельту (см. ниже), поэтому взаимное расположение группы не
                    // искажается. Сами перетаскиваемые узлы исключены из целей
                    // привязки внутри snapPosition (иначе якорь липнул бы к соседям
                    // по группе на их старых позициях).
                    if (e.isShiftDown()) {
                        double[] snapped = snapPosition(dragNode, candidateX, candidateY);
                        candidateX = snapped[0];
                        candidateY = snapped[1];
                    } else {
                        snapGuideX = null;
                        snapGuideY = null;
                    }
                    // Двигаем ВСЕ выделенные узлы разом, от дельты anchor'а (dragNode) —
                    // сохраняет их взаимное расположение (баг-репорт про множественное
                    // выделение/перетаскивание).
                    double[] anchorStart = dragStartPositions.get(dragNode);
                    double dx = anchorStart != null ? candidateX - anchorStart[0] : 0;
                    double dy = anchorStart != null ? candidateY - anchorStart[1] : 0;
                    for (SchemaNode n : selectedNodes) {
                        double[] start = dragStartPositions.get(n);
                        if (start == null) {
                            continue;
                        }
                        n.setX(Math.max(0, start[0] + dx));
                        n.setY(Math.max(0, start[1] + dy));
                        keepOrthogonalWaypointsForNode(n);
                    }
                    revalidate();
                    repaint();
                } else if (rubberBandStart != null) {
                    rubberBandCurrent = mp;
                    repaint();
                } else if (interaction == Interaction.CONNECT && connectPendingId != null) {
                    lastMouse = mp;
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Point mp = toModel(e.getPoint());
                if (interaction == Interaction.CONNECT) {
                    if (connectPendingId != null) {
                        lastMouse = mp;
                    }
                    CabinetSocketHit cabinetHover = cabinetSocketAt(mp);
                    if (!java.util.Objects.equals(cabinetHover, hoveredCabinetSocket)) {
                        hoveredCabinetSocket = cabinetHover;
                        repaint();
                    }
                    SocketHit hover = cabinetHover == null && settings.activeProfile().isSocketWiringEnabled(mode)
                            ? socketAt(mp) : null;
                    if (!java.util.Objects.equals(hover, hoveredSocket)) {
                        hoveredSocket = hover;
                    }
                    setCursor(Cursor.getPredefinedCursor(
                            hover != null || cabinetHover != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    repaint();
                } else if (interaction == Interaction.MOVE) {
                    boolean overHandle = resizeHandleAt(mp) != null;
                    boolean overSegment = !overHandle && waypointAt(mp) == null && segmentAt(mp) != null;
                    setCursor(Cursor.getPredefinedCursor(
                            overHandle ? Cursor.SE_RESIZE_CURSOR
                                    : overSegment ? Cursor.MOVE_CURSOR : Cursor.DEFAULT_CURSOR));
                    SchemaEdge hover = edgeAt(mp);
                    if (hover != hoveredEdge) {
                        hoveredEdge = hover;
                        repaint();
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (panStartScreen != null) {
                    panStartScreen = null;
                    panStartViewPosition = null;
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    return;
                }
                if (resizeNode != null) {
                    model.resizeSchemaNode(resizeNode, resizeNode.getWidth(), resizeNode.getHeight());
                    resizeNode = null;
                    onChanged.run();
                } else if (draggingWaypointEdge != null) {
                    model.setSchemaEdgeWaypoints(draggingWaypointEdge, draggingWaypointEdge.getWaypoints());
                    draggingWaypointEdge = null;
                    draggingWaypointIndex = -1;
                    onChanged.run();
                } else if (draggingSegmentEdge != null) {
                    if (draggingSegmentMoved) {
                        // Одна запись в отмену на весь драг (снимок делает
                        // setSchemaEdgeWaypoints → «Правка маршрута связи»).
                        model.setSchemaEdgeWaypoints(draggingSegmentEdge, draggingSegmentEdge.getWaypoints());
                        onChanged.run();
                    }
                    // Клик без сдвига — связь просто осталась выделенной (см. mousePressed),
                    // маршрут не трогаем и снимок не пишем.
                    draggingSegmentEdge = null;
                    draggingSegmentWpA = -1;
                    draggingSegmentWpB = -1;
                    draggingSegmentPressMp = null;
                    draggingSegmentStartA = null;
                    draggingSegmentStartB = null;
                    draggingSegmentMoved = false;
                    snapGuideX = null;
                    snapGuideY = null;
                } else if (draggingLabelEdge != null) {
                    if (draggingLabelMoved) {
                        model.setSchemaEdgeLabelOffset(draggingLabelEdge,
                                draggingLabelEdge.getLabelDx(), draggingLabelEdge.getLabelDy());
                    } else {
                        // Клик без сдвига — вернуть смещение как было (на случай
                        // микро-дрожания курсора) и открыть редактор подписи, как раньше.
                        draggingLabelEdge.setLabelDx(draggingLabelStartDx);
                        draggingLabelEdge.setLabelDy(draggingLabelStartDy);
                        editEdgeLabel(draggingLabelEdge);
                    }
                    draggingLabelEdge = null;
                    draggingLabelPressMp = null;
                    draggingLabelMoved = false;
                } else if (draggingGroupNode != null) {
                    // Клик без сдвига — просто выделение узла (уже сделано в
                    // mousePressed), раскладку не трогаем, как и у чипа подписи/
                    // отрезка маршрута выше.
                    if (draggingGroupMoved && draggingGroupPreviewSide != null) {
                        model.setPortPlacement(draggingGroupNode, draggingGroupPort.getId(),
                                draggingGroupPreviewSide, draggingGroupPreviewOrder);
                        onChanged.run();
                    }
                    draggingGroupNode = null;
                    draggingGroupPort = null;
                    draggingGroupPressMp = null;
                    draggingGroupMoved = false;
                    draggingGroupPreviewSide = null;
                    draggingGroupPreviewOrder = null;
                } else if (dragNode != null) {
                    if (selectedNodes.size() > 1) {
                        java.util.Map<SchemaNode, double[]> positions = new java.util.LinkedHashMap<>();
                        for (SchemaNode n : selectedNodes) {
                            positions.put(n, new double[]{n.getX(), n.getY()});
                        }
                        model.moveSchemaNodes(positions);
                    } else {
                        model.moveSchemaNode(dragNode, dragNode.getX(), dragNode.getY());
                    }
                    dragNode = null;
                    dragStartPositions.clear();
                    onChanged.run();
                } else if (rubberBandStart != null) {
                    finishRubberBandSelection();
                    rubberBandStart = null;
                    rubberBandCurrent = null;
                    repaint();
                }
                snapGuideX = null;
                snapGuideY = null;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    Point mp = toModel(e.getPoint());
                    SchemaNode hit = nodeAt(mp);
                    if (hit != null && hit.getType() == SchemaNodeType.SCREEN && hit.getScreenRefId() != null
                            && onScreenActivated != null) {
                        Screen scr = screenById(hit.getScreenRefId());
                        if (scr != null) {
                            onScreenActivated.accept(scr);
                        }
                        return;
                    }
                    // Двойной клик по пустому месту на линии связи (не по узлу, не по
                    // чипу подписи) — добавляет точку излома маршрута прямо там, где
                    // кликнули (см. Task #85/v1.4). Работает только в режиме
                    // «Перемещение» — в режиме «Соединение» двойной клик там же ничего
                    // особого не значит, но лучше не путать с логикой соединения гнёзд.
                    if (hit == null && interaction == Interaction.MOVE && edgeLabelChipAt(mp) == null) {
                        SchemaEdge edgeHit = edgeAt(mp);
                        if (edgeHit != null) {
                            insertWaypoint(edgeHit, mp);
                        }
                    }
                }
            }

            /** Ctrl+колесо — масштаб схемы (нет способа приблизить/отдалить схему
             *  сейчас вовсе); обычное колесо/Shift+колесо не трогаем — это стандартная
             *  прокрутка JScrollPane, вокруг которого построен холст, и должна
             *  продолжать работать как раньше. */
            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                if (e.isControlDown()) {
                    double delta = -e.getPreciseWheelRotation() * 0.1;
                    double newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale + delta));
                    if (newScale != scale) {
                        scale = newScale;
                        revalidate();
                        repaint();
                    }
                } else {
                    javax.swing.JScrollPane sp = (javax.swing.JScrollPane)
                            SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, SchemaCanvasPanel.this);
                    if (sp != null) {
                        javax.swing.JScrollBar bar = e.isShiftDown() ? sp.getHorizontalScrollBar()
                                : sp.getVerticalScrollBar();
                        bar.setValue(bar.getValue() + e.getUnitsToScroll() * bar.getUnitIncrement());
                    }
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);

        // Ctrl+C / Ctrl+V (Cmd на macOS) — копирование и вставка выделенной группы
        // блоков. WHEN_FOCUSED, как и Delete (см. UiKit.bindDeleteKey) — клавиша
        // ловится только пока холст схемы в фокусе, не конфликтует с копированием
        // где-либо ещё. Действия сами проверяют, есть ли что копировать/вставлять.
        int menuMask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        javax.swing.InputMap im = getInputMap(WHEN_FOCUSED);
        javax.swing.ActionMap am = getActionMap();
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, menuMask), "schema-copy");
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, menuMask), "schema-paste");
        am.put("schema-copy", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                copySelectedNodes();
            }
        });
        am.put("schema-paste", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                pasteClipboardNodes();
            }
        });
        // Ctrl+R — «Повернуть по часовой» (PLAN.md, задача T3.3), на многовыделение,
        // тем же WHEN_FOCUSED, что и копирование/вставка выше.
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, menuMask), "schema-rotate");
        am.put("schema-rotate", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                rotateSelectedNodes();
            }
        });
        // Esc — отмена перетаскивания ГРУППЫ гнёзд без применения (PLAN.md, задача
        // T3.3: "Esc отменяет"). Мышь может остаться зажатой ещё какое-то время —
        // mouseDragged/mouseReleased дальше молча ничего не делают, раз
        // draggingGroupNode уже null (см. их ветки "else if (draggingGroupNode != null)").
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "schema-cancel-group-drag");
        am.put("schema-cancel-group-drag", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (draggingGroupNode != null) {
                    draggingGroupNode = null;
                    draggingGroupPort = null;
                    draggingGroupPressMp = null;
                    draggingGroupMoved = false;
                    draggingGroupPreviewSide = null;
                    draggingGroupPreviewOrder = null;
                    repaint();
                }
            }
        });
    }

    /** Ctrl+R / пункт меню «Повернуть по часовой» — на пустом выделении не делает
     *  ничего (нет узла, для которого была бы осмысленна ориентация). */
    private void rotateSelectedNodes() {
        // Ориентация блока не существует в CLASSIC (docs/schema-ports-rework/
        // PLAN.md, задача T5.5) — Ctrl+R там ничего не делает, а не молча меняет
        // данные без видимого эффекта.
        if (classicMode() || selectedNodes.isEmpty()) {
            return;
        }
        model.rotateSchemaNodes(selectedNodes);
        onChanged.run();
        repaint();
    }

    /** Ctrl+C — глубокая копия выделенных узлов и связей строго между ними в
     *  статический буфер обмена схемы ({@link #clipboardNodes}). Комплектация
     *  карт/разъёмов входит в копию (см. {@link SchemaNode#copy()}). Модель не
     *  меняется. Если выделения нет — ничего не делает (буфер сохраняется). */
    public void copySelectedNodes() {
        if (selectedNodes.isEmpty()) {
            return;
        }
        clipboardNodes.clear();
        clipboardEdges.clear();
        clipboardPasteSequence = 0;
        Set<String> ids = new java.util.HashSet<>();
        for (SchemaNode n : selectedNodes) {
            clipboardNodes.add(n.copy());
            ids.add(n.getId());
        }
        for (SchemaEdge e : edges()) {
            if (ids.contains(e.getFromNodeId()) && ids.contains(e.getToNodeId())) {
                clipboardEdges.add(e.copy());
            }
        }
    }

    /** Ctrl+V — вставляет копии из буфера обмена схемы со смещением (см.
     *  {@link AppModel#pasteSchemaNodes}) и делает вставленные узлы новым
     *  выделением, чтобы их можно было сразу перетащить на место. Повторные
     *  вставки подряд смещаются каскадом. Пустой буфер — no-op. */
    public void pasteClipboardNodes() {
        if (clipboardNodes.isEmpty()) {
            return;
        }
        clipboardPasteSequence++;
        double off = PASTE_OFFSET * clipboardPasteSequence;
        List<SchemaNode> pasted = model.pasteSchemaNodes(mode, clipboardNodes, clipboardEdges, off, off);
        selectedNodes.clear();
        selectedNodes.addAll(pasted);
        selectSingleEdge(null);
        onChanged.run();
        repaint();
    }

    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged != null ? onChanged : () -> { };
    }

    public void setOnScreenActivated(Consumer<Screen> listener) {
        this.onScreenActivated = listener;
    }

    public Interaction getInteraction() {
        return interaction;
    }

    public void setInteraction(Interaction interaction) {
        this.interaction = interaction;
        this.connectPendingId = null;
        this.connectPendingPortId = null;
        this.connectPendingCabinetInstanceId = null;
        repaint();
    }

    /** "Главный" выделенный узел — только когда выделен РОВНО один (иначе, при
     *  множественном выделении, null: нет однозначного "того самого" узла, см.
     *  {@link #getSelectedNodes()} для полного набора). */
    public SchemaNode getSelectedNode() {
        return selectedNodes.size() == 1 ? selectedNodes.iterator().next() : null;
    }

    /** Полный набор выделенных узлов (см. {@link #selectedNodes}) — неизменяемый
     *  снимок, пустой, если ничего не выделено. */
    public Set<SchemaNode> getSelectedNodes() {
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(selectedNodes));
    }

    public SchemaEdge getSelectedEdge() {
        return selectedEdge;
    }

    /** Полный набор выделенных связей (см. {@link #selectedEdges}) — неизменяемый
     *  снимок, пустой, если ничего не выделено; всегда содержит {@link
     *  #getSelectedEdge()}, если тот не {@code null}. */
    public Set<SchemaEdge> getSelectedEdges() {
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(selectedEdges));
    }

    /** Только для тестов — открывает {@link #toggleEdgeSelection} (Shift/Ctrl+клик
     *  по связи), без необходимости эмулировать реальное {@code MouseEvent}. */
    public void toggleEdgeSelectionForTest(SchemaEdge edge) {
        toggleEdgeSelection(edge);
    }

    /** Только для тестов — открывает {@link #selectSingleEdge}. */
    public void selectSingleEdgeForTest(SchemaEdge edge) {
        selectSingleEdge(edge);
    }

    /** Удаляет ВСЁ текущее выделение (узлы и/или связи) — при нескольких
     *  выделенных узлах/связях ОДНИМ действием отмены (см. {@code AppModel
     *  .deleteSchemaNodes}/{@code deleteSchemaEdges}), не по одному, иначе Ctrl+Z
     *  вернул бы только последний удалённый элемент. */
    public void deleteSelected() {
        if (selectedNodes.size() > 1) {
            model.deleteSchemaNodes(new ArrayList<>(selectedNodes));
            selectedNodes.clear();
            onChanged.run();
        } else if (!selectedNodes.isEmpty()) {
            model.deleteSchemaNode(selectedNodes.iterator().next());
            selectedNodes.clear();
            onChanged.run();
        } else if (selectedEdges.size() > 1) {
            model.deleteSchemaEdges(new ArrayList<>(selectedEdges));
            selectSingleEdge(null);
            onChanged.run();
        } else if (selectedEdge != null) {
            model.deleteSchemaEdge(selectedEdge);
            selectSingleEdge(null);
            onChanged.run();
        }
        repaint();
    }

    /** «Перетрассировать выделенные» (docs/schema-ports-rework/PLAN.md, задача T4.4/
     *  §2.6) — выделены связи (одна или несколько, см. {@link #selectedEdges}):
     *  перетрассировываются они все; выделены узлы (без выделенных связей): все
     *  связи ЭТОЙ схемы, у которых любой из выделенных узлов на любом конце; ничего
     *  не выделено — ничего не делает (по аналогии с {@link #deleteSelected}, но
     *  без «нечего перетрассировывать» диалога — кнопка просто бездействует, как и
     *  «Удалить выбранное» в этом случае). */
    public void rerouteSelected() {
        List<SchemaEdge> targets = new ArrayList<>();
        if (!selectedEdges.isEmpty()) {
            targets.addAll(selectedEdges);
        } else if (!selectedNodes.isEmpty()) {
            for (SchemaEdge e : edges()) {
                boolean touches = selectedNodes.stream()
                        .anyMatch(n -> n.getId().equals(e.getFromNodeId()) || n.getId().equals(e.getToNodeId()));
                if (touches) {
                    targets.add(e);
                }
            }
        }
        if (!targets.isEmpty()) {
            model.rerouteEdges(targets);
            onChanged.run();
            repaint();
        }
    }

    /** «Перетрассировать все» — все связи текущего режима схемы (сигнал/питание),
     *  независимо от выделения (см. {@link #rerouteSelected}). */
    public void rerouteAll() {
        List<SchemaEdge> targets = new ArrayList<>(edges());
        if (!targets.isEmpty()) {
            model.rerouteEdges(targets);
            onChanged.run();
            repaint();
        }
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

    private List<SchemaNode> nodes() {
        return model.schemaNodesForCurrentScene(mode);
    }

    private List<SchemaEdge> edges() {
        return model.schemaEdgesForCurrentScene(mode);
    }

    /** Завершает протяжку прямоугольника-«резинки» (см. {@link #rubberBandStart}) —
     *  добавляет к {@link #selectedNodes} все узлы, чей прямоугольник ПЕРЕСЕКАЕТСЯ
     *  с областью протяжки (не обязательно ЦЕЛИКОМ внутри — как в большинстве
     *  редакторов схем). Протяжка короче нескольких пикселей игнорируется — это
     *  был просто клик по пустому месту без реального намерения выделить область
     *  (выделение уже сброшено в mousePressed, если не был зажат Shift/Ctrl). */
    private void finishRubberBandSelection() {
        double x1 = Math.min(rubberBandStart.x, rubberBandCurrent.x);
        double y1 = Math.min(rubberBandStart.y, rubberBandCurrent.y);
        double x2 = Math.max(rubberBandStart.x, rubberBandCurrent.x);
        double y2 = Math.max(rubberBandStart.y, rubberBandCurrent.y);
        if (x2 - x1 < 3 && y2 - y1 < 3) {
            return;
        }
        for (SchemaNode n : nodes()) {
            double nx1 = n.getX(), ny1 = n.getY();
            double nx2 = nx1 + n.getWidth(), ny2 = ny1 + n.getHeight();
            if (nx1 < x2 && nx2 > x1 && ny1 < y2 && ny2 > y1) {
                selectedNodes.add(n);
            }
        }
    }

    /** Экранная точка мыши → координата в модельном (немасштабированном)
     *  пространстве, где хранятся координаты узлов/точек излома — все хит-тесты и
     *  запись позиций работают в этом пространстве независимо от текущего scale. */
    private Point toModel(Point screenPt) {
        return new Point((int) Math.round(screenPt.x / scale), (int) Math.round(screenPt.y / scale));
    }

    /** Привязка перетаскиваемого узла к краю/центру другого узла сцены (Shift во
     *  время перетаскивания — см. mouseDragged), как в yEd Graph Editor: кандидатные
     *  координаты (левый край/центр/правый край по X, верх/центр/низ по Y)
     *  сравниваются с такими же координатами остальных узлов, и если расстояние
     *  меньше порога (настройка профиля, единая для всех канвасов с прилипанием) —
     *  позиция подтягивается к линии другого узла — на всю силу («сила
     *  прилипания» = 100%) или частично (см. SnapMath.blend). Побочный эффект —
     *  выставляет snapGuideX/snapGuideY (ТОЧНУЮ, не смешанную координату цели) для
     *  отрисовки направляющей. */
    private double[] snapPosition(SchemaNode moving, double candidateX, double candidateY) {
        snapGuideX = null;
        snapGuideY = null;
        double threshold = settings.activeProfile().getSnapThresholdPx();
        int strength = settings.activeProfile().getSnapStrengthPercent();
        double w = moving.getWidth(), h = moving.getHeight();
        double[] xCandidates = {candidateX, candidateX + w / 2, candidateX + w};
        double[] yCandidates = {candidateY, candidateY + h / 2, candidateY + h};
        double bestDx = threshold, bestDy = threshold;
        double snappedX = candidateX, snappedY = candidateY;
        for (SchemaNode other : nodes()) {
            // Пропускаем сам перетаскиваемый узел И остальные узлы текущего
            // группового перетаскивания — они двигаются вместе с якорем, липнуть
            // к ним (к их ещё не обновлённым позициям) нельзя.
            if (other == moving || dragStartPositions.containsKey(other)) {
                continue;
            }
            double ow = other.getWidth(), oh = other.getHeight();
            double[] oxs = {other.getX(), other.getX() + ow / 2, other.getX() + ow};
            double[] oys = {other.getY(), other.getY() + oh / 2, other.getY() + oh};
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
        return new double[]{snappedX, snappedY};
    }

    /** Привязка размеров растягиваемого узла (Shift во время resize за юго-восточный
     *  уголок — см. mouseDragged). Двигаются ПРАВАЯ грань (x = moving.getX()+w) и
     *  НИЖНЯЯ (y = moving.getY()+h); они сравниваются с левым/центром/правым краем и
     *  верхом/центром/низом остальных узлов — как в snapPosition, но кандидат по
     *  каждой оси один (сам уголок), а не три. Побочный эффект — snapGuideX/snapGuideY
     *  для отрисовки направляющей. Возвращает {snappedW, snappedH}. */
    private double[] snapResize(SchemaNode moving, double candidateW, double candidateH) {
        snapGuideX = null;
        snapGuideY = null;
        double threshold = settings.activeProfile().getSnapThresholdPx();
        int strength = settings.activeProfile().getSnapStrengthPercent();
        double rightEdge = moving.getX() + candidateW;
        double bottomEdge = moving.getY() + candidateH;
        double bestDx = threshold, bestDy = threshold;
        double snappedW = candidateW, snappedH = candidateH;
        for (SchemaNode other : nodes()) {
            if (other == moving) {
                continue;
            }
            double ow = other.getWidth(), oh = other.getHeight();
            double[] oxs = {other.getX(), other.getX() + ow / 2, other.getX() + ow};
            double[] oys = {other.getY(), other.getY() + oh / 2, other.getY() + oh};
            for (double ox : oxs) {
                double d = Math.abs(rightEdge - ox);
                if (d < bestDx) {
                    bestDx = d;
                    snappedW = Math.max(MIN_NODE_W,
                            SnapMath.blend(candidateW, ox - moving.getX(), strength));
                    snapGuideX = ox;
                }
            }
            for (double oy : oys) {
                double d = Math.abs(bottomEdge - oy);
                if (d < bestDy) {
                    bestDy = d;
                    snappedH = Math.max(MIN_NODE_H,
                            SnapMath.blend(candidateH, oy - moving.getY(), strength));
                    snapGuideY = oy;
                }
            }
        }
        return new double[]{snappedW, snappedH};
    }

    /** Привязка перетаскиваемой точки излома провода (Shift во время перетаскивания —
     *  см. mouseDragged). Тянет точку так, чтобы прилегающие к ней сегменты линии
     *  вставали строго по декартовым осям:
     *  <ul>
     *    <li>к X/Y СОСЕДНИХ точек маршрута этой же линии (предыдущей и следующей) —
     *        тогда сегмент до соседа становится ровно вертикальным / горизонтальным;</li>
     *    <li>к X/Y точек ДРУГИХ линий (начал, концов, изломов) — чтобы соседние
     *        провода выравнивались в одну прямую, как в yEd;</li>
     *    <li>к X/Y РАМОК блоков оборудования (все 4 стороны каждого узла) — чтобы
     *        провод можно было провести строго вдоль края соседнего блока.</li>
     *  </ul>
     *  ЦЕНТРЫ блоков в кандидаты сознательно не входят (как и раньше) — центр
     *  блока почти никогда не совпадает с гнездом разъёма, куда реально приходит
     *  линия, и притяжка к нему уводила сегмент в наклон на пару градусов
     *  (баг-репорт). Рамки без центра эту проблему не имеют — блок либо ровно
     *  задевает нужную сторону, либо нет, наклона не возникает.
     *  <p>До задачи T3.2 (docs/schema-ports-rework/PLAN.md) рамки блоков тоже были
     *  кандидатами, но их убрали вместе с центром одним махом. После переноса
     *  гнёзд на рамку (D1) число реальных точек-гнёзд на схеме резко выросло и
     *  разбросалось по краям блоков — без рамок как отдельного, крупного и
     *  предсказуемого ориентира кандидатами остаются только другие провода, и
     *  привязка стала выглядеть так, будто тянет "куда-то не туда" (отзыв
     *  пользователя 2026-09-16: "привязки срабатывали очень криво, непонятно
     *  куда"). Рамки возвращены, центр — нет.
     *  <p>В отличие от snapPosition (для блоков) сравнивается ОДНА точка, а не три
     *  кандидата на измерение — у точки излома нет ширины/высоты. */
    private double[] snapWaypointPosition(SchemaEdge movingEdge, int movingIndex,
                                           double candidateX, double candidateY) {
        snapGuideX = null;
        snapGuideY = null;
        double threshold = settings.activeProfile().getSnapThresholdPx();
        int strength = settings.activeProfile().getSnapStrengthPercent();
        double bestDx = threshold, bestDy = threshold;
        double snappedX = candidateX, snappedY = candidateY;

        // 1. Соседние точки маршрута этой же линии — выравнивание прилегающих
        //    сегментов по осям. movingIndex — индекс в списке waypoints, в
        //    routePoints он сдвинут на 1 (нулевой элемент — начало линии), так что
        //    соседи это routePoints[movingIndex] и routePoints[movingIndex + 2].
        List<double[]> route = routePoints(movingEdge);
        if (route != null && route.size() >= 3 && movingIndex + 2 < route.size()) {
            for (double[] nb : new double[][]{route.get(movingIndex), route.get(movingIndex + 2)}) {
                double dx = Math.abs(candidateX - nb[0]);
                if (dx < bestDx) {
                    bestDx = dx;
                    snappedX = SnapMath.blend(candidateX, nb[0], strength);
                    snapGuideX = nb[0];
                }
                double dy = Math.abs(candidateY - nb[1]);
                if (dy < bestDy) {
                    bestDy = dy;
                    snappedY = SnapMath.blend(candidateY, nb[1], strength);
                    snapGuideY = nb[1];
                }
            }
        }

        // 2. Точки других линий (начала/концы/изломы) — чтобы соседние провода
        //    вставали в одну прямую.
        for (SchemaEdge edge : edges()) {
            if (edge == movingEdge) {
                continue;
            }
            List<double[]> other = routePoints(edge);
            if (other == null) {
                continue;
            }
            for (double[] p : other) {
                double dx = Math.abs(candidateX - p[0]);
                if (dx < bestDx) {
                    bestDx = dx;
                    snappedX = SnapMath.blend(candidateX, p[0], strength);
                    snapGuideX = p[0];
                }
                double dy = Math.abs(candidateY - p[1]);
                if (dy < bestDy) {
                    bestDy = dy;
                    snappedY = SnapMath.blend(candidateY, p[1], strength);
                    snapGuideY = p[1];
                }
            }
        }

        // 3. Рамки блоков (все 4 стороны каждого узла) — крупный, предсказуемый
        //    ориентир в дополнение к точкам других линий (см. javadoc метода).
        //    ЦЕНТР намеренно не добавляется.
        for (SchemaNode node : nodes()) {
            double left = node.getX(), right = node.getX() + node.getWidth();
            double top = node.getY(), bottom = node.getY() + node.getHeight();
            for (double x : new double[]{left, right}) {
                double dx = Math.abs(candidateX - x);
                if (dx < bestDx) {
                    bestDx = dx;
                    snappedX = SnapMath.blend(candidateX, x, strength);
                    snapGuideX = x;
                }
            }
            for (double y : new double[]{top, bottom}) {
                double dy = Math.abs(candidateY - y);
                if (dy < bestDy) {
                    bestDy = dy;
                    snappedY = SnapMath.blend(candidateY, y, strength);
                    snapGuideY = y;
                }
            }
        }
        return new double[]{snappedX, snappedY};
    }

    /** Открывает {@link #snapWaypointPosition} тесту (аналогично {@code
     *  socketPositionForTest}) — приватность самого метода не нужна тесту, но
     *  ломать инкапсуляцию наружу нет смысла, у теста прямой доступ по пакету. */
    double[] snapWaypointPositionForTest(SchemaEdge movingEdge, int movingIndex,
                                          double candidateX, double candidateY) {
        return snapWaypointPosition(movingEdge, movingIndex, candidateX, candidateY);
    }

    private SchemaNode nodeAt(Point p) {
        List<SchemaNode> ns = nodes();
        for (int i = ns.size() - 1; i >= 0; i--) {
            SchemaNode n = ns.get(i);
            if (p.x >= n.getX() && p.x <= n.getX() + n.getWidth() && p.y >= n.getY() && p.y <= n.getY() + n.getHeight()) {
                return n;
            }
        }
        return null;
    }

    /** Узел, чей уголок изменения размера (правый нижний) попадает под точку клика. */
    private SchemaNode resizeHandleAt(Point p) {
        List<SchemaNode> ns = nodes();
        for (int i = ns.size() - 1; i >= 0; i--) {
            SchemaNode n = ns.get(i);
            double hx = n.getX() + n.getWidth() - RESIZE_HANDLE;
            double hy = n.getY() + n.getHeight() - RESIZE_HANDLE;
            if (p.x >= hx && p.x <= n.getX() + n.getWidth() && p.y >= hy && p.y <= n.getY() + n.getHeight()) {
                return n;
            }
        }
        return null;
    }

    private SchemaNode nodeById(String id) {
        for (SchemaNode n : nodes()) {
            if (n.getId().equals(id)) {
                return n;
            }
        }
        return null;
    }

    // Подписи связей — это «чипы» высотой ~20px (шрифт EDGE_FONT + отступы); при
    // старом шаге в 16px соседние чипы у одной пары узлов лежали внахлёст друг на
    // друга (расстояние между центрами меньше суммы их полувысот). 28px даёт зазор.
    private static final double EDGE_OFFSET_STEP = 28;

    /** Если между одной и той же парой узлов несколько связей (разного типа/цвета),
     *  без разнесения их линии/подписи легли бы друг на друга неразличимо. Индекс
     *  связи внутри своей пары (порядок не важен — важно, что каждая получает свой
     *  сдвиг) + их общее число в паре. */
    private int[] edgeSlot(SchemaEdge edge) {
        String key = pairKey(edge.getFromNodeId(), edge.getToNodeId());
        int idx = 0, total = 0;
        for (SchemaEdge e : edges()) {
            if (pairKey(e.getFromNodeId(), e.getToNodeId()).equals(key)) {
                if (e == edge) {
                    idx = total;
                }
                total++;
            }
        }
        return new int[]{idx, total};
    }

    private static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    /** Концы связи (ax,ay,bx,by) со сдвигом перпендикулярно линии, если у пары узлов
     *  несколько связей — иначе они рисовались бы друг поверх друга. Гнездо разъёма
     *  (если связь заведена через конкретный CardPort — см. socketPosition) даёт
     *  ТОЧНЫЙ якорь линии; для конца БЕЗ гнезда (узел без разъёмов вовсе — например
     *  узел-ссылка на экран, либо связь узел-узел без выбора конкретного разъёма)
     *  линия больше не бьёт в геометрический центр блока (что выглядело так, будто
     *  линия "протыкает" блок насквозь), а обрезается по границе прямоугольника —
     *  точке излома на пересечении луча "центр → противоположный конец связи (или
     *  первая/последняя точка излома пользователя)" с рамкой узла.
     *  <p>Перпендикулярный сдвиг НЕ применяется, если у связи ТОЧНЫЙ якорь на ОБОИХ
     *  концах (гнездо кабинета или конкретный CardPort с обеих сторон) — тогда все
     *  связи, ссылающиеся на одну и ту же пару гнёзд (например, несколько кабинетов
     *  экрана, автоматически подключённых к одной и той же группе «6×CEE 16A» —
     *  см. AppModel#autoPopulateSchema), должны визуально сходиться РОВНО в эту
     *  группу, а не веером расходиться в стороны (баг-репорт) — сдвиг остаётся
     *  только для старых связей «узел-узел» без выбранных гнёзд ни на одном конце,
     *  где он и был изначально задуман (несколько разных физических кабелей между
     *  одной парой блоков оборудования, которым иначе физически негде разойтись). */
    private double[] endpointsFor(SchemaEdge edge) {
        SchemaNode a = nodeById(edge.getFromNodeId());
        SchemaNode b = nodeById(edge.getToNodeId());
        if (a == null || b == null) {
            return null;
        }
        Point aSocket = edge.getFromCabinetInstanceId() != null
                ? cabinetSocketPosition(a, edge.getFromCabinetInstanceId())
                : socketPosition(a, edge.getFromPortId(), edge);
        Point bSocket = edge.getToCabinetInstanceId() != null
                ? cabinetSocketPosition(b, edge.getToCabinetInstanceId())
                : socketPosition(b, edge.getToPortId(), edge);
        List<com.vjstb.ledscheme.model.EdgeWaypoint> wps = edge.getWaypoints();

        double[] aCenter = {a.getX() + a.getWidth() / 2.0, a.getY() + a.getHeight() / 2.0};
        double[] bCenter = {b.getX() + b.getWidth() / 2.0, b.getY() + b.getHeight() / 2.0};
        double[] aAim = !wps.isEmpty() ? new double[]{wps.get(0).getX(), wps.get(0).getY()}
                : (bSocket != null ? new double[]{bSocket.x, bSocket.y} : bCenter);
        double[] bAim = !wps.isEmpty() ? new double[]{wps.get(wps.size() - 1).getX(), wps.get(wps.size() - 1).getY()}
                : (aSocket != null ? new double[]{aSocket.x, aSocket.y} : aCenter);

        double ax, ay, bx, by;
        if (aSocket != null) {
            ax = aSocket.x;
            ay = aSocket.y;
        } else {
            double[] p = clipToBorder(a, aCenter, aAim);
            ax = p[0];
            ay = p[1];
        }
        if (bSocket != null) {
            bx = bSocket.x;
            by = bSocket.y;
        } else {
            double[] p = clipToBorder(b, bCenter, bAim);
            bx = p[0];
            by = p[1];
        }
        int[] slot = aSocket != null && bSocket != null ? new int[]{0, 1} : edgeSlot(edge);
        int idx = slot[0], total = slot[1];
        if (total > 1) {
            double dx = bx - ax, dy = by - ay;
            double len = Math.hypot(dx, dy);
            if (len > 0.001) {
                double nx = -dy / len, ny = dx / len;
                double offset = (idx - (total - 1) / 2.0) * EDGE_OFFSET_STEP;
                ax += nx * offset;
                ay += ny * offset;
                bx += nx * offset;
                by += ny * offset;
            }
        }
        return new double[]{ax, ay, bx, by};
    }

    /** Точка пересечения луча "center → toward" с рамкой прямоугольника узла —
     *  используется вместо голого центра для конца связи БЕЗ конкретного гнезда
     *  (см. endpointsFor), чтобы линия визуально начиналась/заканчивалась строго на
     *  границе блока, а не била в его геометрический центр. Стандартный приём
     *  пересечения луча из центра прямоугольника с его границей: минимальный
     *  масштаб, на котором луч достигает вертикальной ИЛИ горизонтальной стороны. */
    private static double[] clipToBorder(SchemaNode node, double[] center, double[] toward) {
        double halfW = node.getWidth() / 2.0;
        double halfH = node.getHeight() / 2.0;
        double dx = toward[0] - center[0];
        double dy = toward[1] - center[1];
        if (Math.abs(dx) < 1e-6 && Math.abs(dy) < 1e-6) {
            return center;
        }
        double scaleX = Math.abs(dx) > 1e-6 ? halfW / Math.abs(dx) : Double.POSITIVE_INFINITY;
        double scaleY = Math.abs(dy) > 1e-6 ? halfH / Math.abs(dy) : Double.POSITIVE_INFINITY;
        double scale = Math.min(scaleX, scaleY);
        return new double[]{center[0] + dx * scale, center[1] + dy * scale};
    }

    /** Полный маршрут связи в экранных координатах: начало, все точки излома по
     *  порядку, конец — прямые отрезки между соседними точками рисуются как одна
     *  ломаная линия (см. Task #85/v1.4). Без точек излома — те же 2 точки, что и
     *  раньше (обычная прямая линия узел-узел).
     *  <p>{@link EdgeRouteMode#AUTO} (docs/schema-ports-rework/PLAN.md, задача T4.4)
     *  считается заново каждый раз через {@link #autoRoutePoints} — сохранённые
     *  {@code edge.getWaypoints()} у такой связи не используются вовсе (см. {@link
     *  SchemaEdge#effectiveRouteMode()}); если авто-трассировка невозможна (узел
     *  одного из концов исчез — не про геометрию, а про целостность данных), тихо
     *  откатывается на путь ниже (та же прямая/по изломам линия, что и для {@link
     *  EdgeRouteMode#MANUAL}/{@link EdgeRouteMode#STRAIGHT}). Гнёзда-кабинеты
     *  расключения экрана И обычные связи узел-узел БЕЗ гнезда вовсе — тоже
     *  полноценно трассируются (закрытые баг-репорты пользователя 2026-09-18:
     *  "линии не перетрассировываются по прямым углам, режим авто" и "если
     *  подключать линию к блоку экрана без режима кабинеты-тоже гнёзда, то линия
     *  не трассируется под углом") — см. {@link #routeEndpointFor}. */
    private List<double[]> routePoints(SchemaEdge edge) {
        // CLASSIC (docs/schema-ports-rework/PLAN.md, задача T5.5) никогда не
        // трассирует через OrthogonalRouter — до этого плана EdgeRouteMode.AUTO
        // не существовало вовсе, все связи шли по сохранённым изломам/прямой
        // линией (ветка ниже). Само значение routeMode при этом не трогаем —
        // переключение обратно в MODERN должно увидеть его как ни в чём не бывало.
        if (!classicMode() && edge.effectiveRouteMode() == EdgeRouteMode.AUTO) {
            List<double[]> auto = autoRoutePoints(edge);
            if (auto != null) {
                return auto;
            }
        }
        double[] ends = endpointsFor(edge);
        if (ends == null) {
            return null;
        }
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{ends[0], ends[1]});
        for (com.vjstb.ledscheme.model.EdgeWaypoint w : edge.getWaypoints()) {
            pts.add(new double[]{w.getX(), w.getY()});
        }
        pts.add(new double[]{ends[2], ends[3]});
        return pts;
    }

    /** Ортогональная трассировка ОДНОЙ связи через {@code OrthogonalRouter} (T4.1) —
     *  {@code null}, если один из узлов связи не найден (устаревшая ссылка, не
     *  геометрия — сторона находится ВСЕГДА, см. {@link #routeEndpointFor}: у
     *  обычного гнезда/кабинета/даже безгнездовой связи узел-узел). Список препятствий — прямоугольники ВСЕХ
     *  остальных узлов ТОГО ЖЕ режима схемы (сигнал/питание не смешиваются, как и
     *  везде в холсте), кроме двух узлов самой связи — иначе усы упирались бы в
     *  собственный же блок, у которого гнездо стоит ровно на границе (см. javadoc
     *  {@code OrthogonalRouter}). Отступ от ЧУЖИХ блоков — та же настройка длины
     *  уса ({@code UserProfile.getSchemaRouteStubPx()}), что и у самого гнезда
     *  (пожелание пользователя 2026-09-18: "чтобы этот отступ применялся к блокам
     *  целиком", а не только к точке входа в гнездо) — раньше был отдельной жёстко
     *  зашитой константой (10px), несвязанной с настройкой уса. */
    private List<double[]> autoRoutePoints(SchemaEdge edge) {
        SchemaNode a = nodeById(edge.getFromNodeId());
        SchemaNode b = nodeById(edge.getToNodeId());
        if (a == null || b == null) {
            return null;
        }
        boolean aIsCabinetEnd = edge.getFromCabinetInstanceId() != null;
        boolean bIsCabinetEnd = edge.getToCabinetInstanceId() != null;
        // Ориентировочная точка КАЖДОГО конца — используется только как "куда
        // смотреть" для гнезда-БЕЗ-гнезда на ДРУГОМ конце (см. routeEndpointFor
        // ниже) — тот же порядок отката, что и aSocket/bSocket в endpointsFor.
        double[] aRef = referencePointFor(a, edge.getFromPortId(), edge.getFromCabinetInstanceId());
        double[] bRef = referencePointFor(b, edge.getToPortId(), edge.getToCabinetInstanceId());
        RouteEndpoint ea = routeEndpointFor(a, edge.getFromPortId(), edge.getFromCabinetInstanceId(), edge, bRef);
        RouteEndpoint eb = routeEndpointFor(b, edge.getToPortId(), edge.getToCabinetInstanceId(), edge, aRef);
        if (ea == null || eb == null) {
            return null;
        }
        double stub = settings.activeProfile().getSchemaRouteStubPx();
        List<com.vjstb.ledscheme.service.schemalayout.OrthogonalRouter.Obstacle> obstacles = new ArrayList<>();
        for (SchemaNode n : nodes()) {
            // Гнездо-кабинет (см. routeEndpointFor) может лежать ГЛУБОКО внутри
            // своего же узла (миниатюра расключения экрана) — для него собственный
            // узел по-прежнему приходится исключать целиком, иначе исходная точка
            // трассировки сама оказалась бы "внутри" препятствия и авто-трассировка
            // сразу отказала бы (откат на fallback/прямую).
            if ((n == a && aIsCabinetEnd) || (n == b && bIsCabinetEnd)) {
                continue;
            }
            if (n == a || n == b) {
                // Обычное гнездо лежит РОВНО на границе узла — раньше свой же узел
                // исключался из препятствий ЦЕЛИКОМ (иначе внешнее раздутие
                // затянуло бы гнездо, лежащее на НЕраздутой границе, "внутрь"
                // препятствия). Но полное исключение позволяло связующему участку
                // маршрута срезать напрямую ЧЕРЕЗ/ПОД телом своего же узла, если
                // гнездо на одной стороне, а маршрут удобнее вести с другой (баг-
                // репорт пользователя 2026-09-18: "если гнездо слева, а линия идёт
                // справа или снизу, то она заходит под блок"). Раздутие на ВСЕ 4
                // стороны (как у чужих блоков) тоже не годится — тогда сторона,
                // где стоит само гнездо, "съела" бы гнездо и его ус целиком. Вместо
                // этого — раздуваем только 3 стороны, отличные от стороны гнезда
                // (см. {@link #selfObstacleWithClearance}) — тот же отступ, что и
                // от чужих блоков, для обхода СВОЕГО ЖЕ блока с других сторон (баг-
                // репорт пользователя 2026-09-18: "отступ линии снизу/сверху блока
                // не работает" — раньше тут было 0 со всех сторон), но без риска
                // проглотить собственное гнездо на четвёртой стороне.
                obstacles.add(selfObstacleWithClearance(n, n == a ? ea.side() : eb.side(), stub));
                continue;
            }
            obstacles.add(new com.vjstb.ledscheme.service.schemalayout.OrthogonalRouter.Obstacle(
                    n.getX() - stub, n.getY() - stub, n.getWidth() + 2 * stub, n.getHeight() + 2 * stub));
        }
        var result = com.vjstb.ledscheme.service.schemalayout.OrthogonalRouter.route(
                ea.x(), ea.y(), ea.side(), eb.x(), eb.y(), eb.side(), obstacles, stub);
        return result.points();
    }

    /** Прямоугольник СВОЕГО ЖЕ узла как препятствие (см. {@link #autoRoutePoints}) —
     *  раздут отступом {@code margin} на 3 стороны, кроме той, где стоит {@code
     *  pinSide} (гнездо этого конца связи). Та сторона остаётся РОВНО на истинной
     *  границе узла (без раздутия): гнездо лежит на ней, а ус уходит ДАЛЬШЕ
     *  наружу — оба варианта строго вне (или на границе) прямоугольника даже без
     *  запаса, {@code Obstacle.containsInterior} использует строгое неравенство.
     *  Раздутие ТРЁХ остальных сторон — баг-репорт пользователя 2026-09-18:
     *  "отступ линии снизу/сверху блока не работает" — маршрут, огибающий свой же
     *  блок с ЛЮБОЙ другой стороны (не той, где гнездо), должен держать тот же
     *  отступ, что и от чужих блоков, а не идти вплотную. */
    private static com.vjstb.ledscheme.service.schemalayout.OrthogonalRouter.Obstacle selfObstacleWithClearance(
            SchemaNode n, NodeSide pinSide, double margin) {
        double left = n.getX();
        double top = n.getY();
        double right = n.getX() + n.getWidth();
        double bottom = n.getY() + n.getHeight();
        if (pinSide != NodeSide.LEFT) {
            left -= margin;
        }
        if (pinSide != NodeSide.RIGHT) {
            right += margin;
        }
        if (pinSide != NodeSide.TOP) {
            top -= margin;
        }
        if (pinSide != NodeSide.BOTTOM) {
            bottom += margin;
        }
        return new com.vjstb.ledscheme.service.schemalayout.OrthogonalRouter.Obstacle(left, top, right - left, bottom - top);
    }

    /** Точка привязки конца связи для орто-трассировки + сторона, определяющая
     *  направление "уса" ({@link com.vjstb.ledscheme.service.schemalayout.
     *  OrthogonalRouter}) — обычное гнездо ({@link #pinFor}), гнездо-кабинет на
     *  миниатюре расключения экрана ({@code cabinetInstanceId != null}), либо (баг-
     *  репорт пользователя 2026-09-18: "если подключать линию к блоку экрана без
     *  режима кабинеты-тоже гнёзда, то линия не трассируется под углом") ОБЫЧНАЯ
     *  связь узел-узел БЕЗ гнезда вовсе ({@code portId == null}) — та же точка на
     *  границе, что рисует {@link #endpointsFor}/{@link #clipToBorder} (луч из
     *  центра узла к {@code aim} — грубой точке ДРУГОГО конца связи, только чтобы
     *  знать, в какую сторону смотреть). Во всех трёх случаях, где у гнезда нет
     *  готовой стороны из раскладки блока (кабинет, безгнездовая связь), сторона —
     *  ближайшая грань рамки узла к точке ({@link #nearestSide}). Ус в этом случае
     *  просто идёт от точки наружу по этой стороне — визуально не хуже прежней
     *  прямой линии (тот же путь внутри блока не виден за его заливкой), а с точки
     *  выхода из блока трассировка уже полноценно огибает препятствия под 90°, как
     *  и для обычных гнёзд. */
    private record RouteEndpoint(double x, double y, NodeSide side) {
    }

    private RouteEndpoint routeEndpointFor(SchemaNode node, String portId, String cabinetInstanceId,
                                            SchemaEdge forEdge, double[] aim) {
        if (cabinetInstanceId != null) {
            Point p = cabinetSocketPosition(node, cabinetInstanceId);
            if (p == null) {
                return null;
            }
            return new RouteEndpoint(p.x, p.y, nearestSide(node, p.x, p.y));
        }
        if (portId == null) {
            double[] center = {node.getX() + node.getWidth() / 2.0, node.getY() + node.getHeight() / 2.0};
            double[] p = clipToBorder(node, center, aim);
            return new RouteEndpoint(p[0], p[1], nearestSide(node, p[0], p[1]));
        }
        var pin = pinFor(node, portId, forEdge);
        if (pin == null) {
            return null;
        }
        return new RouteEndpoint(node.getX() + pin.x(), node.getY() + pin.y(), pin.side());
    }

    /** Грубая точка конца связи для {@code aim} на ДРУГОМ конце (см.
     *  {@link #routeEndpointFor}) — тот же порядок отката, что и aSocket/bSocket в
     *  {@link #endpointsFor}: настоящее гнездо/кабинет, если есть, иначе просто
     *  центр узла. Не обязана быть идеально точной — используется только чтобы
     *  {@link #clipToBorder} знал, в какую сторону "смотреть" безгнездовым концом. */
    private double[] referencePointFor(SchemaNode node, String portId, String cabinetInstanceId) {
        Point p = cabinetInstanceId != null ? cabinetSocketPosition(node, cabinetInstanceId)
                : portId != null ? socketPosition(node, portId, null) : null;
        if (p != null) {
            return new double[]{p.x, p.y};
        }
        return new double[]{node.getX() + node.getWidth() / 2.0, node.getY() + node.getHeight() / 2.0};
    }

    /** Ближайшая грань рамки узла {@code node} к точке {@code (px, py)} — нужна
     *  гнёздам-кабинетам (см. {@link #routeEndpointFor}), у которых нет собственной
     *  стороны из раскладки {@link com.vjstb.ledscheme.service.schemalayout.NodePortLayout}. */
    private static NodeSide nearestSide(SchemaNode node, double px, double py) {
        double left = px - node.getX();
        double right = node.getX() + node.getWidth() - px;
        double top = py - node.getY();
        double bottom = node.getY() + node.getHeight() - py;
        double min = Math.min(Math.min(left, right), Math.min(top, bottom));
        if (min == left) {
            return NodeSide.LEFT;
        }
        if (min == right) {
            return NodeSide.RIGHT;
        }
        return min == top ? NodeSide.TOP : NodeSide.BOTTOM;
    }

    /** Радиус полукруглого «мостика»-обхода на пересечении линий (логические px,
     *  масштабируется зумом холста вместе со всей отрисовкой). Заметно крупнее
     *  стрелки направления (та выступает ~5px в сторону от линии) — при близких
     *  размерах дуга и треугольник на схеме сливаются в одно пятно. */
    private static final int HOP_RADIUS = 9;

    /** Для каждой связи — список её дуг-обходов {@code {segIndex, t}} в местах
     *  пересечения с другими связями, где ЭТА связь лежит сверху (её сегмент в
     *  точке пересечения длиннее; при равенстве длин — связь, которая раньше в
     *  {@code es}, детерминированный tie-break). Пары перебираются в лоб
     *  (сегмент×сегмент): связей на схеме десятки, это микросекунды. Считается
     *  каждую перерисовку — геометрия связей всё равно уже пересчитана в
     *  {@code routeCache}. */
    private static Map<SchemaEdge, List<double[]>> computeWireHops(
            List<SchemaEdge> es, Map<SchemaEdge, List<double[]>> routeCache) {
        Map<SchemaEdge, List<double[]>> hopMap = new IdentityHashMap<>();
        for (int ei = 0; ei < es.size(); ei++) {
            List<double[]> r1 = routeCache.get(es.get(ei));
            if (r1 == null || r1.size() < 2) {
                continue;
            }
            for (int ej = ei + 1; ej < es.size(); ej++) {
                List<double[]> r2 = routeCache.get(es.get(ej));
                if (r2 == null || r2.size() < 2) {
                    continue;
                }
                for (int s1 = 0; s1 < r1.size() - 1; s1++) {
                    double a1x = r1.get(s1)[0], a1y = r1.get(s1)[1];
                    double a2x = r1.get(s1 + 1)[0], a2y = r1.get(s1 + 1)[1];
                    double len1 = WireHopGeometry.segLen(a1x, a1y, a2x, a2y);
                    for (int s2 = 0; s2 < r2.size() - 1; s2++) {
                        double b1x = r2.get(s2)[0], b1y = r2.get(s2)[1];
                        double b2x = r2.get(s2 + 1)[0], b2y = r2.get(s2 + 1)[1];
                        double[] cp = WireHopGeometry.crossParams(a1x, a1y, a2x, a2y, b1x, b1y, b2x, b2y);
                        if (cp == null) {
                            continue;
                        }
                        double len2 = WireHopGeometry.segLen(b1x, b1y, b2x, b2y);
                        boolean firstOnTop = WireHopGeometry.aIsOnTop(len1, len2);
                        SchemaEdge top = es.get(firstOnTop ? ei : ej);
                        int seg = firstOnTop ? s1 : s2;
                        double t = firstOnTop ? cp[0] : cp[1];
                        hopMap.computeIfAbsent(top, k -> new ArrayList<>()).add(new double[]{seg, t});
                    }
                }
            }
        }
        for (List<double[]> v : hopMap.values()) {
            v.sort((p, q) -> p[0] != q[0] ? Double.compare(p[0], q[0]) : Double.compare(p[1], q[1]));
        }
        return hopMap;
    }

    /** Точка на середине ОБЩЕЙ длины ломаной (по пройденному пути, а не просто
     *  геометрический центр между началом и концом) — чтобы подпись не залезала в
     *  угол излома при сильно изогнутом маршруте. */
    private static double[] midOfRoute(List<double[]> pts) {
        double total = 0;
        for (int i = 0; i < pts.size() - 1; i++) {
            total += Math.hypot(pts.get(i + 1)[0] - pts.get(i)[0], pts.get(i + 1)[1] - pts.get(i)[1]);
        }
        double half = total / 2;
        double walked = 0;
        for (int i = 0; i < pts.size() - 1; i++) {
            double ax = pts.get(i)[0], ay = pts.get(i)[1];
            double bx = pts.get(i + 1)[0], by = pts.get(i + 1)[1];
            double segLen = Math.hypot(bx - ax, by - ay);
            if (walked + segLen >= half || i == pts.size() - 2) {
                double t = segLen > 0.0001 ? (half - walked) / segLen : 0;
                t = Math.max(0, Math.min(1, t));
                return new double[]{ax + t * (bx - ax), ay + t * (by - ay)};
            }
            walked += segLen;
        }
        return pts.get(0);
    }

    private SchemaEdge edgeAt(Point p) {
        for (SchemaEdge edge : edges()) {
            List<double[]> pts = routePoints(edge);
            if (pts == null) {
                continue;
            }
            for (int i = 0; i < pts.size() - 1; i++) {
                if (distanceToSegment(p.x, p.y, pts.get(i)[0], pts.get(i)[1],
                        pts.get(i + 1)[0], pts.get(i + 1)[1]) < 8) {
                    return edge;
                }
            }
        }
        return null;
    }

    /** Точка излома под курсором (для перетаскивания) — попадание только у
     *  ВЫДЕЛЕННОЙ связи, т.к. только её точки излома вообще видны и кликабельны
     *  (см. отрисовку выше). */
    private record WaypointHit(SchemaEdge edge, int index) { }

    /** Ищет по ТЕКУЩЕМУ РАСЧЁТНОМУ маршруту ({@link #routePoints}), а не напрямую по
     *  {@code selectedEdge.getWaypoints()} — для {@link EdgeRouteMode#MANUAL}-связи
     *  это те же точки в том же порядке (без изменений в поведении), а для {@link
     *  EdgeRouteMode#AUTO} даёт возможность вообще НАЙТИ излом, раз уж он есть на
     *  картинке, хотя в модели для него пока нет {@link com.vjstb.ledscheme.model.EdgeWaypoint}
     *  (см. {@link #materializeAutoRouteIfNeeded}, PLAN.md T4.4/§2.6). {@code index} —
     *  индекс, который получит эта точка В СПИСКЕ ИЗЛОМОВ ПОСЛЕ материализации (для
     *  MANUAL — просто её текущий индекс). ЧИСТАЯ функция — не мутирует модель, можно
     *  дёргать хоть на каждое движение мыши (см. вызов из mouseMoved). */
    private WaypointHit waypointAt(Point p) {
        if (selectedEdge == null) {
            return null;
        }
        List<double[]> pts = routePoints(selectedEdge);
        if (pts == null) {
            return null;
        }
        for (int i = 1; i < pts.size() - 1; i++) {
            double[] w = pts.get(i);
            if (Math.hypot(p.x - w[0], p.y - w[1]) < 8) {
                return new WaypointHit(selectedEdge, i - 1);
            }
        }
        return null;
    }

    /** Если {@code edge} сейчас {@link EdgeRouteMode#AUTO} — фиксирует ТЕКУЩИЙ
     *  посчитанный маршрут как обычные {@link com.vjstb.ledscheme.model.EdgeWaypoint}
     *  и переводит связь в {@link EdgeRouteMode#MANUAL} (PLAN.md §2.6: "перетаскивание
     *  излома или отрезка у AUTO-связи превращает её в MANUAL с текущим маршрутом в
     *  качестве изломов, одна запись отмены" — запись отмены даёт {@link
     *  AppModel#convertEdgeToManualWithRoute}). Вызывать РОВНО в момент начала
     *  перетаскивания (после {@link #waypointAt}/{@link #segmentAt} нашли, ЧТО тащить,
     *  но до первого чтения {@code edge.getWaypoints()} по индексу) — НЕ из mouseMoved/
     *  hover-проверки (там только читают {@link #waypointAt}/{@link #segmentAt}, не
     *  меняя модель, иначе наведение мышью на AUTO-связь незаметно конвертировало бы
     *  её и засоряло историю отмены). Для уже-MANUAL/STRAIGHT связи или связи без
     *  посчитанного авто-маршрута — не делает ничего. */
    private void materializeAutoRouteIfNeeded(SchemaEdge edge) {
        if (edge.effectiveRouteMode() != EdgeRouteMode.AUTO) {
            return;
        }
        List<double[]> pts = routePoints(edge);
        if (pts == null || pts.size() < 3) {
            return;
        }
        List<EdgeWaypoint> wps = new ArrayList<>();
        for (int i = 1; i < pts.size() - 1; i++) {
            wps.add(new EdgeWaypoint(pts.get(i)[0], pts.get(i)[1]));
        }
        model.convertEdgeToManualWithRoute(edge, wps);
    }

    /** Режим «ортогональные связи» (docs/schema-ports-rework/PLAN.md, задача T4.4/
     *  §2.6, настройка {@code orthogonalEdgeEditing}, по умолчанию вкл.) — при
     *  перемещении узла {@code node} у ВСЕХ его {@link EdgeRouteMode#MANUAL}-связей
     *  с конкретным гнездом на этом конце сдвигает БЛИЖНИЙ (первый/последний) излом
     *  ПО ОДНОЙ ОСИ так, чтобы отрезок от него к пину остался перпендикулярен стороне
     *  пина — без этого при переносе блока вбок отрезок к сохранённому излому просто
     *  наклонялся бы. {@link EdgeRouteMode#AUTO}-связи тут не нужны — они и так
     *  пересчитываются с нуля на новую позицию узла при каждой отрисовке ({@link
     *  #routePoints}). Ничего не мутирует, кроме координат самого излома — как и
     *  сам перенос узла (см. вызывающий {@code mouseDragged}), фиксация в истории
     *  отмены произойдёт ОДНИМ действием при отпускании кнопки мыши ({@code
     *  AppModel#moveSchemaNode}/{@code moveSchemaNodes}, тот же приём, что и у
     *  перетаскивания излома, см. {@link #materializeAutoRouteIfNeeded}). */
    private void keepOrthogonalWaypointsForNode(SchemaNode node) {
        if (!settings.activeProfile().isOrthogonalEdgeEditing()) {
            return;
        }
        for (SchemaEdge edge : edges()) {
            if (edge.effectiveRouteMode() != EdgeRouteMode.MANUAL || edge.getWaypoints().isEmpty()) {
                continue;
            }
            if (node.getId().equals(edge.getFromNodeId())) {
                alignNearEndWaypoint(edge, node, edge.getFromPortId(), true);
            }
            if (node.getId().equals(edge.getToNodeId())) {
                alignNearEndWaypoint(edge, node, edge.getToPortId(), false);
            }
        }
    }

    private void alignNearEndWaypoint(SchemaEdge edge, SchemaNode node, String portId, boolean nearStart) {
        var pin = pinFor(node, portId, edge);
        if (pin == null) {
            return;
        }
        double pinX = node.getX() + pin.x(), pinY = node.getY() + pin.y();
        List<EdgeWaypoint> wps = edge.getWaypoints();
        EdgeWaypoint w = nearStart ? wps.get(0) : wps.get(wps.size() - 1);
        if (pin.side() == NodeSide.LEFT || pin.side() == NodeSide.RIGHT) {
            w.setY(pinY);
        } else {
            w.setX(pinX);
        }
    }

    /** Отрезок маршрута ВЫДЕЛЕННОЙ связи под курсором, оба конца которого — точки
     *  излома (значит, его можно тащить целиком). Крайние отрезки, упирающиеся в
     *  гнездо/узел, сюда не попадают. Поля {@code wpA}/{@code wpB} — индексы этих
     *  двух точек в {@code edge.getWaypoints()}. */
    private record SegmentHit(SchemaEdge edge, int wpA, int wpB) { }

    private SegmentHit segmentAt(Point p) {
        if (selectedEdge == null) {
            return null;
        }
        List<double[]> pts = routePoints(selectedEdge);
        if (pts == null || pts.size() < 4) { // нужно минимум две точки излома
            return null;
        }
        // Зона чипа подписи (с запасом) зарезервирована под клик по подписи — иначе
        // промах мимо чипа по лежащему под ним среднему отрезку хватал бы отрезок
        // вместо открытия редактора подписи.
        java.awt.Rectangle chip = labelChipBounds(selectedEdge);
        if (chip != null) {
            java.awt.Rectangle grown = new java.awt.Rectangle(chip);
            grown.grow(8, 8);
            if (grown.contains(p)) {
                return null;
            }
        }
        // Индекс сегмента i соединяет точку маршрута i с i+1; точка 0 — гнездо/узел
        // начала, последняя — конца. «Средний» сегмент (обе стороны — изломы) —
        // i от 1 до pts.size()-3; точка маршрута i отвечает точке излома i-1.
        for (int i = 1; i <= pts.size() - 3; i++) {
            double d = distanceToSegment(p.x, p.y,
                    pts.get(i)[0], pts.get(i)[1], pts.get(i + 1)[0], pts.get(i + 1)[1]);
            if (d < 6) {
                return new SegmentHit(selectedEdge, i - 1, i);
            }
        }
        return null;
    }

    /** Добавляет новую точку излома в связь на месте клика — вставляется в список
     *  ровно на позицию отрезка ломаной, к которому клик ближе всего, чтобы новая
     *  точка встала в правильное место маршрута, а не всегда в конец списка. */
    private void insertWaypoint(SchemaEdge edge, Point p) {
        List<double[]> pts = routePoints(edge);
        if (pts == null) {
            return;
        }
        int insertAt = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < pts.size() - 1; i++) {
            double d = distanceToSegment(p.x, p.y, pts.get(i)[0], pts.get(i)[1], pts.get(i + 1)[0], pts.get(i + 1)[1]);
            if (d < best) {
                best = d;
                insertAt = i;
            }
        }
        List<com.vjstb.ledscheme.model.EdgeWaypoint> newWps = new ArrayList<>();
        for (com.vjstb.ledscheme.model.EdgeWaypoint w : edge.getWaypoints()) {
            newWps.add(w.copy());
        }
        newWps.add(insertAt, new com.vjstb.ledscheme.model.EdgeWaypoint(p.x, p.y));
        model.setSchemaEdgeWaypoints(edge, newWps);
        selectSingleEdge(edge);
        selectedNodes.clear();
        onChanged.run();
        repaint();
    }

    private static final Font EDGE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 10);

    /** Границы кликабельного «чипа» подписи связи (на середине ЛОМАНОЙ линии, не
     *  просто между началом и концом) — используются и при отрисовке, и при
     *  хит-тесте клика, чтобы не разъезжались. */
    private java.awt.Rectangle labelChipBounds(SchemaEdge edge) {
        List<double[]> pts = routePoints(edge);
        if (pts == null) {
            return null;
        }
        double[] mid = midOfRoute(pts);
        // Смещение, заданное перетаскиванием чипа (Task #3) — 0,0 по умолчанию,
        // т.е. поведение не меняется для всех связей, у которых чип не двигали.
        int mx = (int) (mid[0] + edge.getLabelDx());
        int my = (int) (mid[1] + edge.getLabelDy());
        String display = edge.displayLabel();
        boolean hasLabel = display != null && !display.isEmpty();
        String text = hasLabel ? display : "+ подпись";
        java.awt.FontMetrics fm = getFontMetrics(EDGE_FONT);
        int w = fm.stringWidth(text) + 14;
        int h = fm.getHeight() + 6;
        return new java.awt.Rectangle(mx - w / 2, my - h / 2, w, h);
    }

    /** Показывать ли ПУСТОЙ чип-приглашение «+ подпись» для связи без назначенной
     *  подписи (docs/schema-ports-rework/PLAN.md, задача T4.5, D14) — да, если это
     *  живой холст (не {@link #exporting}) И связь либо выделена, либо под курсором.
     *  Связь с УЖЕ назначенной подписью сюда не попадает вовсе — её чип решается
     *  отдельно в цикле отрисовки (видна всегда, это содержимое схемы). */
    private boolean shouldShowEmptyLabelChip(SchemaEdge edge, boolean selected) {
        return !exporting && (selected || edge == hoveredEdge);
    }

    private SchemaEdge edgeLabelChipAt(Point p) {
        for (SchemaEdge edge : edges()) {
            java.awt.Rectangle r = labelChipBounds(edge);
            if (r != null && r.contains(p)) {
                return edge;
            }
        }
        return null;
    }

    private void editEdgeLabel(SchemaEdge edge) {
        CardPort fromPort = findPort(edge.getFromNodeId(), edge.getFromPortId());
        CardPort toPort = findPort(edge.getToNodeId(), edge.getToPortId());
        String lockedType = null;
        Integer maxCount = null;
        // Ограничение — минимум из ДВУХ концов связи: гнездо на одном узле может
        // иметь больше свободных разъёмов, чем гнездо на другом (например, щит на
        // 4×CEE32A, но у конкретной проходной всего один физический ввод 32А —
        // тогда лимит для ЭТОЙ связи правомерно 1, даже если у щита ещё есть
        // свободные разъёмы для ДРУГИХ проходных). Показываем, КАКОЙ именно узел
        // сейчас определяет предел — иначе кажется багом, когда лимит меньше
        // ожидаемого числа на противоположном конце (см. Task #70).
        String maxCountReason = null;
        if (fromPort != null) {
            lockedType = fromPort.getConnectorType();
            int remaining = fromPort.getCount() - usedCount(edge.getFromPortId(), edge);
            maxCount = Math.max(1, remaining);
            maxCountReason = limitReason(edge.getFromNodeId(), fromPort, remaining);
        }
        if (toPort != null) {
            if (lockedType == null) {
                lockedType = toPort.getConnectorType();
            }
            int remaining = toPort.getCount() - usedCount(edge.getToPortId(), edge);
            int toMax = Math.max(1, remaining);
            if (maxCount == null || toMax < maxCount) {
                maxCount = toMax;
                maxCountReason = limitReason(edge.getToNodeId(), toPort, remaining);
            }
        }
        // Узел-ссылка на реальный экран не имеет гнёзд (CardPort) вовсе — раньше
        // связи, ведущие к экрану, вообще не проверялись на лимит числа линий (для
        // сигнала — совсем никак, для питания — см. screenChainCapacity). "N
        // вводных"/"N цепочек" экрана — это фактическое число независимых линий
        // (силовых цепочек, либо цепочек сигнала СЧИТАЯ backup — см.
        // screenChainCapacity), уже расключённых на кабинетах экрана, а значит и
        // максимум того, сколько отдельных линий схема вправе подвести к этому
        // экрану суммарно от всех источников (см. также screenUsedCount ниже).
        for (String nodeId : new String[]{edge.getFromNodeId(), edge.getToNodeId()}) {
            SchemaNode node = nodeById(nodeId);
            Integer capacity = node != null ? screenChainCapacity(node) : null;
            if (capacity == null) {
                continue;
            }
            int used = screenUsedCount(nodeId, edge);
            int rem = Math.max(1, capacity - used);
            if (maxCount == null || rem < maxCount) {
                maxCount = rem;
                String unit = mode == SchemaMode.POWER ? "вводных" : "цепочек (мейн+резерв)";
                maxCountReason = node.getLabel() + ": свободно " + Math.max(0, capacity - used)
                        + " из " + capacity + " " + unit;
            }
        }
        // Гнездо распределения промаркировано только "голым" номиналом кабеля (см.
        // PowerConnectorsConfigDialog) без адаптера — WireLabelDialog сам предложит
        // и голый номинал, и переходник(и) под тип разъёма кабинета экрана на другом
        // конце связи (см. connectorHints/lockedOptionsFor), а не молча подменит
        // значение без права выбора.
        Set<PowerConnectorType> hints = connectorHintsFor(edge);
        // INDIVIDUAL-режим (Task #2/v1.6): каждая точка-гнездо — отдельный физический
        // разъём, поэтому «сколько кабелей» перестаёт быть выбором инженера — всегда 1
        // (см. WireLabelDialog(..., forceSingleCable)). Проверяем именно эту связь, а не
        // узел целиком — связь без привязки к гнезду (fromPortId/toPortId оба null)
        // продолжает работать как обычная связь узел-узел, режим на неё не влияет.
        boolean forceSingleCable = settings.activeProfile().getConnectorDisplayMode(mode) == ConnectorDisplayMode.INDIVIDUAL
                && (edge.getFromPortId() != null || edge.getToPortId() != null);
        WireLabelDialog dlg = new WireLabelDialog(SwingUtilities.getWindowAncestor(this), model, settings, mode, edge,
                hints, lockedType, maxCount, maxCountReason, settings.activeProfile().isFoolProofWiringEnabled(),
                forceSingleCable);
        dlg.setVisible(true);
        if (!dlg.isConfirmed()) {
            return;
        }
        if (dlg.isClearRequested()) {
            model.updateSchemaEdgeLabel(edge, null);
        } else {
            try {
                model.updateSchemaEdgeWire(edge, dlg.getCount(), dlg.getWireType(), dlg.getLengthM());
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        onChanged.run();
        repaint();
    }

    /** Если связь ведёт к узлу-ссылке на реальный экран — типы разъёма питания
     *  кабинетов ЭТОГО экрана (сужает/подбирает список доступных кабелей в
     *  WireLabelDialog: ввод в кабинет — это адаптер вида CEE16A→TrueCON или
     *  CEE16A→PowerCon, а не сам разъём кабинета напрямую). Возвращает МНОЖЕСТВО,
     *  а не одно значение — экран может смешивать несколько типов кабинетов
     *  (переопределение типа по ячейке, см. Task #28), тогда список кабелей должен
     *  предложить варианты под ВСЕ реально присутствующие типы, а не молча выбрать
     *  только первый попавшийся (что могло бы подсунуть неверный тип адаптера для
     *  части кабинетов экрана). Для питания единственная связь узла-экрана
     *  трактуется как ввод в кабинет, поэтому оба конца проверяются одинаково —
     *  какой из них экран, не важно. */
    private Set<PowerConnectorType> connectorHintsFor(SchemaEdge edge) {
        if (mode != SchemaMode.POWER) {
            return Set.of();
        }
        for (String nodeId : new String[]{edge.getFromNodeId(), edge.getToNodeId()}) {
            SchemaNode n = nodeById(nodeId);
            if (n == null || n.getType() != SchemaNodeType.SCREEN || n.getScreenRefId() == null) {
                continue;
            }
            Screen scr = screenById(n.getScreenRefId());
            if (scr == null) {
                continue;
            }
            CabinetType defaultType = model.typeOf(scr);
            Set<PowerConnectorType> types = new LinkedHashSet<>();
            for (CabinetInstance c : scr.getCabinets()) {
                if (c.isHidden()) {
                    continue;
                }
                CabinetType effective = ScreenLogic.effectiveType(c, defaultType, model.getWorkspace());
                if (effective != null) {
                    types.add(effective.getPowerConnectorType());
                }
            }
            if (!types.isEmpty()) {
                return types;
            }
        }
        return Set.of();
    }

    /** Для узла-ссылки на экран (SchemaNodeType.SCREEN) — сколько всего независимых
     *  линий (питания или сигнала, смотря по текущему режиму схемы) может физически
     *  прийти в этот узел: то же число, что фактически расключено на кабинетах
     *  экрана в «Расключение экрана» — для питания это силовые цепочки, для
     *  сигнала — цепочки контроллера, ОСНОВНЫЕ и РЕЗЕРВНЫЕ вместе (backup-цепочка —
     *  такая же реальная линия коммутации, ограничивающая число входов экрана, как
     *  и основная — раньше связь схемы сигнала, ведущая к экрану, вообще не
     *  проверялась на этот лимит). null — понятие неприменимо (не экран, экран не
     *  найден). */
    private Integer screenChainCapacity(SchemaNode node) {
        if (node.getType() != SchemaNodeType.SCREEN || node.getScreenRefId() == null) {
            return null;
        }
        Screen scr = screenById(node.getScreenRefId());
        if (scr == null) {
            return null;
        }
        return switch (mode) {
            case POWER -> model.powerChainsTouchingScreen(scr).size();
            case SIGNAL -> {
                // Резервный ПОРТ (chain.getBackupPortNumber()) — это loop-through на
                // том же ряду кабинетов основной цепочки, а не отдельный объект
                // SignalChain (в отличие от отдельной backup-цепочки, которая уже
                // считается сама по себе) — физически это ВТОРОЙ кабель до экрана,
                // который .size() ниже не видит вообще. Раньше это означало, что
                // экран с одной цепочкой + резервным портом на ней показывал
                // ёмкость "1", хотя по факту уже разведено 2 отдельных кабеля.
                List<SignalChain> chains = model.signalChainsTouchingScreen(scr);
                int backupPorts = 0;
                for (SignalChain c : chains) {
                    if (c.getBackupPortNumber() != null) {
                        backupPorts++;
                    }
                }
                yield chains.size() + backupPorts;
            }
        };
    }

    /** Сколько линий уже подведено к узлу-экрану ДРУГИМИ связями схемы (кроме
     *  exclude) — суммарно от всех источников, а не по одному конкретному гнезду,
     *  т.к. у узла-экрана нет отдельных гнёзд (см. screenChainCapacity). */
    private int screenUsedCount(String nodeId, SchemaEdge exclude) {
        int used = 0;
        for (SchemaEdge e2 : edges()) {
            if (e2 == exclude) {
                continue;
            }
            if (nodeId.equals(e2.getFromNodeId()) || nodeId.equals(e2.getToNodeId())) {
                used += e2.getWireCount() != null ? e2.getWireCount() : 1;
            }
        }
        return used;
    }

    /** Находит гнездо (карта или разъём питания) узла по id порта — используется
     *  для автоопределения типа кабеля и ограничения числа линий по факту
     *  доступных разъёмов группы (см. Task #60). */
    private CardPort findPort(String nodeId, String portId) {
        if (nodeId == null || portId == null) {
            return null;
        }
        SchemaNode n = nodeById(nodeId);
        if (n == null) {
            return null;
        }
        for (SchemaCard c : n.getCards()) {
            for (CardPort p : c.getPorts()) {
                if (p.getId().equals(portId)) {
                    return p;
                }
            }
        }
        for (CardPort p : n.getPowerConnectors()) {
            if (p.getId().equals(portId)) {
                return p;
            }
        }
        return null;
    }

    /** Человекочитаемая причина лимита — какой узел и сколько разъёмов у него
     *  свободно из скольки всего (см. Task #70). */
    private String limitReason(String nodeId, CardPort port, int remaining) {
        SchemaNode n = nodeById(nodeId);
        String name = n != null ? n.getLabel() : "?";
        return name + ": свободно " + Math.max(0, remaining) + " из " + port.getCount();
    }

    /** Сколько линий уже занято на этом гнезде другими связями (кроме exclude) —
     *  см. {@link com.vjstb.ledscheme.service.schemalayout.SchemaUsage#usedCount}
     *  (перенесено туда в задаче T2.3, здесь только тонкая обёртка над {@link
     *  #edges()}, docs/schema-ports-rework/PLAN.md). */
    private int usedCount(String portId, SchemaEdge exclude) {
        return com.vjstb.ledscheme.service.schemalayout.SchemaUsage.usedCount(edges(), portId, exclude);
    }

    /** Проверка при создании НОВОЙ связи через гнёзда: нельзя подключить кабель,
     *  если на одном из гнёзд уже заняты все разъёмы группы (например, в проходной
     *  с 6×CEE16 на выход нельзя завести седьмой кабель). */
    private String capacityError(CardPort fromPort, String fromPortId, CardPort toPort, String toPortId) {
        if (fromPort != null && fromPort.getCount() - usedCount(fromPortId, null) <= 0) {
            return "Гнездо «" + fromPort.getConnectorType() + "» уже занято всеми "
                    + fromPort.getCount() + " линиями";
        }
        if (toPort != null && toPort.getCount() - usedCount(toPortId, null) <= 0) {
            return "Гнездо «" + toPort.getConnectorType() + "» уже занято всеми "
                    + toPort.getCount() + " линиями";
        }
        return null;
    }

    /** "Защита от дурака" (см. Personalization) — при создании НОВОЙ связи через
     *  гнёзда запрещает соединять ВХОД со ВХОДОМ или ВЫХОД с ВЫХОДОМ, если у обоих
     *  гнёзд направление вообще определено (сравнение по {@link CardPort#getDirection()}).
     *  Двунаправленное гнездо ({@link PortDirection#IN_OUT} — например, SDI loop-through)
     *  совместимо с чем угодно (может сыграть роль недостающей стороны), поэтому
     *  запрет срабатывает, только если ОБА гнезда СТРОГО одного однонаправленного
     *  направления. Ничего не проверяет, если настройка выключена или хотя бы одно
     *  из гнёзд не найдено (обычная связь узел-узел без привязки к конкретному гнезду).
     *  <p>Роль {@link InterfaceRole#NETWORK} (docs/schema-ports-rework/PLAN.md, D11/
     *  T3.4) отключает проверку направления вовсе, даже если оба гнезда СТРОГО
     *  однонаправленные: сетевое оборудование в общей схеме — это конечные блоки
     *  (свитчи, серверы), а не полноценная топология сети с известным направлением
     *  трафика по каждому порту, поэтому "вход"/"выход" здесь не несёт смысла,
     *  который бы стоило защищать (см. DIALOG.md, "Свитчи"). Роль читается из уже
     *  посчитанной раскладки узла ({@link #nodeLayout}), а не напрямую с {@link
     *  CardPort#getRole()} — иначе не сработало бы на гнёздах, получивших роль
     *  NETWORK через библиотеку/эвристику (см. {@link
     *  com.vjstb.ledscheme.service.schemalayout.PortRoleResolver}), а не явным
     *  переопределением на самом гнезде (типичный случай — Ethernet у медиасервера
     *  вроде Disguise D3). */
    private String directionError(SchemaNode fromNode, CardPort fromPort, SchemaNode toNode, CardPort toPort) {
        if (!settings.activeProfile().isFoolProofWiringEnabled() || fromPort == null || toPort == null) {
            return null;
        }
        if (roleOf(fromNode, fromPort) == InterfaceRole.NETWORK || roleOf(toNode, toPort) == InterfaceRole.NETWORK) {
            return null;
        }
        PortDirection fd = fromPort.getDirection();
        PortDirection td = toPort.getDirection();
        boolean bothIn = fd == PortDirection.IN && td == PortDirection.IN;
        boolean bothOut = fd == PortDirection.OUT && td == PortDirection.OUT;
        if (bothIn || bothOut) {
            String dir = bothIn ? "входа" : "выхода";
            return "Нельзя соединить два " + dir + " напрямую — проверьте направление гнёзд"
                    + " (можно отключить в Персонализации: «Защита от дурака»)";
        }
        return null;
    }

    /** Открывает {@link #directionError} тесту (аналогично {@code
     *  snapWaypointPositionForTest}) — "защита от дурака" не завязана на реальные
     *  мышиные события, чистая функция от узлов/гнёзд. */
    String directionErrorForTest(SchemaNode fromNode, CardPort fromPort, SchemaNode toNode, CardPort toPort) {
        return directionError(fromNode, fromPort, toNode, toPort);
    }

    /** Роль гнезда КАК ОНА ПОСЧИТАНА раскладкой узла (см. {@link #nodeLayout}) — то
     *  же значение, что определяет сторону рамки и (см. {@link #edgeDefaultColor})
     *  цвет линии. {@code null}, если узел/гнездо не найдены в текущей раскладке. */
    private InterfaceRole roleOf(SchemaNode node, CardPort port) {
        if (node == null || port == null) {
            return null;
        }
        for (var pin : nodeLayout(node).pins()) {
            if (pin.port().getId().equals(port.getId())) {
                return pin.role();
            }
        }
        return null;
    }

    /** Цвет связи БЕЗ пользовательского {@code edge.getColor()} (docs/schema-ports-
     *  rework/PLAN.md, D9/§2.6, задача T4.4): для сигнала — цвет РОЛИ гнезда-
     *  источника ({@link SchemaStyle#roleLineColor}), для питания — цвет НОМИНАЛА
     *  разъёма-источника ({@link SchemaStyle#powerNominalLineColor}); если источник —
     *  обычный узел без конкретного гнезда (нет `fromPortId`, коммутация через гнёзда
     *  выключена) или для этой роли/номинала в активном пресете цвета нет —
     *  {@link SchemaStyle#defaultEdgeColor}, как и раньше у ЛЮБОЙ связи без своего
     *  цвета. Источник (не приёмник) — связь визуально "начинается" от него, поэтому
     *  его роль интуитивно и определяет цвет линии, как её тип сигнала/питания. */
    private Color edgeDefaultColor(SchemaEdge edge) {
        CardPort fromPort = findPort(edge.getFromNodeId(), edge.getFromPortId());
        if (mode == SchemaMode.POWER) {
            Color nominal = fromPort != null ? style.powerNominalLineColor(fromPort.getConnectorType()) : null;
            return nominal != null ? nominal : style.defaultEdgeColor;
        }
        InterfaceRole role = roleOf(nodeById(edge.getFromNodeId()), fromPort);
        Color roleColor = role != null ? style.roleLineColor(role) : null;
        return roleColor != null ? roleColor : style.defaultEdgeColor;
    }

    /** T6.1 (docs/schema-ports-rework/PLAN.md, «Печатный» пресет, §2.7): синхро на
     *  печатном пресете рисуется не только фиолетовым, но и пунктиром — так эта
     *  роль остаётся различимой и в чёрно-белой распечатке, где цвет теряется, а
     *  штрих остаётся. Пользовательский переключатель связи «Пунктиром» (Task #85)
     *  не про роль — про конкретную резервную/мониторинговую связь; тут не
     *  подменяем его, а ДОБАВЛЯЕМ пунктир поверх, когда роль требует. Только
     *  MODERN — в CLASSIC роли вообще не участвуют в отрисовке (см. paintClassic). */
    private boolean autoDashedForPrintSync(SchemaEdge edge) {
        if (classicMode() || mode != SchemaMode.SIGNAL) {
            return false;
        }
        if (settings.activeProfile().getSchemaStylePreset() != SchemaStylePreset.PRINT) {
            return false;
        }
        CardPort fromPort = findPort(edge.getFromNodeId(), edge.getFromPortId());
        return roleOf(nodeById(edge.getFromNodeId()), fromPort) == InterfaceRole.SYNC;
    }

    private static double distanceToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
        double cx = ax + t * dx, cy = ay + t * dy;
        return Math.hypot(px - cx, py - cy);
    }

    private void handleRightClick(MouseEvent e) {
        Point mp = toModel(e.getPoint());
        // ПКМ по пину — меню ГРУППЫ гнёзд (PLAN.md, задача T3.3), а не общее меню
        // блока: проверяется ПЕРВЫМ, потому что пины лежат ровно на границе узла и
        // иначе всегда проигрывали бы hit-test по прямоугольнику узла (nodeAt ниже).
        // Меню ГРУППЫ гнёзд — только MODERN (docs/schema-ports-rework/PLAN.md,
        // задача T5.5): до этого плана правого клика по гнезду отдельно не было
        // вовсе, ПКМ по узлу с гнёздами сразу открывал обычное меню узла ниже.
        SocketHit socketHit = classicMode() ? null : socketAt(mp);
        if (socketHit != null) {
            showGroupMenu(socketHit.node(), socketHit.port(), e.getX(), e.getY());
            return;
        }
        SchemaNode hitNode = nodeAt(mp);
        if (hitNode != null) {
            // ПКМ по узлу, УЖЕ входящему в многовыделение — сохраняет его целиком
            // (меню предложит удалить ВСЕ выбранные узлы), иначе сужает выделение
            // до этого одного узла и показывает обычное подробное меню.
            if (!selectedNodes.contains(hitNode)) {
                selectedNodes.clear();
                selectedNodes.add(hitNode);
            }
            selectSingleEdge(null);
            repaint();
            if (selectedNodes.size() > 1) {
                showMultiNodeMenu(e.getX(), e.getY());
            } else {
                showNodeMenu(hitNode, e.getX(), e.getY());
            }
            return;
        }
        // Точки излома видны/хватаются только у уже ВЫДЕЛЕННОЙ связи (см. waypointAt),
        // поэтому этот хит-тест что-то находит, только если ПКМ пришёлся по излому связи,
        // которая уже была выделена левым кликом — в этом случае показываем отдельное
        // меню одной точки, а не общее меню связи.
        WaypointHit wpHit = waypointAt(mp);
        if (wpHit != null) {
            showWaypointMenu(wpHit.edge(), wpHit.index(), e.getX(), e.getY());
            return;
        }
        SchemaEdge hitEdge = edgeAt(mp);
        if (hitEdge != null) {
            // ПКМ по связи, УЖЕ входящей в многовыделение — сохраняет его целиком
            // (меню предложит удалить/перетрассировать ВСЕ выбранные связи), иначе
            // сужает выделение до этой одной связи и показывает подробное меню —
            // тот же приём, что уже был у узлов (баг-репорт пользователя
            // 2026-09-18: "не работает групповое выделение линий").
            if (!selectedEdges.contains(hitEdge)) {
                selectSingleEdge(hitEdge);
            }
            selectedNodes.clear();
            repaint();
            if (selectedEdges.size() > 1) {
                showMultiEdgeMenu(e.getX(), e.getY());
            } else {
                showEdgeMenu(hitEdge, e.getX(), e.getY());
            }
        }
    }

    /** Контекстное меню для КЛИКА ПРАВОЙ по связи, входящей в многовыделение (см.
     *  {@link #handleRightClick}) — только общие для группы действия (удаление,
     *  перетрассировка), подробное меню одной связи ({@link #showEdgeMenu}) для
     *  группы неприменимо (цвет/подпись/маршрут — свойства ОДНОЙ конкретной связи).
     *  Тот же приём, что {@link #showMultiNodeMenu} для узлов. */
    private void showMultiEdgeMenu(int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        javax.swing.JMenuItem reroute = new javax.swing.JMenuItem(
                "Перетрассировать выбранные (" + selectedEdges.size() + ")");
        reroute.addActionListener(ev -> rerouteSelected());
        menu.add(reroute);
        menu.addSeparator();
        javax.swing.JMenuItem del = new javax.swing.JMenuItem("Удалить выбранные (" + selectedEdges.size() + ")");
        del.addActionListener(ev -> deleteSelected());
        menu.add(del);
        menu.show(this, x, y);
    }

    /** Контекстное меню для КЛИКА ПРАВОЙ по узлу, входящему в многовыделение (см.
     *  {@link #handleRightClick}) — только общие для группы действия (удаление),
     *  подробное меню одного узла ({@link #showNodeMenu}) для группы неприменимо
     *  (переименование/тип/карты — свойства ОДНОГО конкретного узла). */
    private void showMultiNodeMenu(int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        addBlockLayoutMenuItems(menu, selectedNodes);
        menu.addSeparator();
        javax.swing.JMenuItem del = new javax.swing.JMenuItem("Удалить выбранные (" + selectedNodes.size() + ")");
        del.addActionListener(ev -> deleteSelected());
        menu.add(del);
        menu.show(this, x, y);
    }

    /** «Ориентация ▸ / Повернуть по часовой / Только задействованные гнёзда / Вернуть
     *  раскладку по умолчанию» — общая часть меню блока (PLAN.md, задача T3.3), одна
     *  и та же что для одного узла ({@link #showNodeMenu}), что для многовыделения
     *  ({@link #showMultiNodeMenu}: пункты применяются ко ВСЕМ узлам {@code targets}
     *  одним действием отмены, см. {@code AppModel.setSchemaNodesOrientation} и
     *  соседние мутаторы). На многовыделении с разными текущими значениями галочки
     *  ориентации/«только занятые» намеренно не проставляются (нет одного общего
     *  состояния, которое было бы честно показать) — {@code single} различает эти
     *  два случая. */
    private void addBlockLayoutMenuItems(JPopupMenu menu, Collection<SchemaNode> targets) {
        // Ориентация/«только задействованные»/раскладка гнёзд — понятия, которых
        // до этого плана не было (docs/schema-ports-rework/PLAN.md, задача T5.5):
        // в CLASSIC пункты меню просто не добавляются, а не показываются
        // неработающими — тот же принцип, что и для панели SchemaPanel.
        if (classicMode()) {
            return;
        }
        SchemaNode single = targets.size() == 1 ? targets.iterator().next() : null;

        javax.swing.JMenu orientationMenu = new javax.swing.JMenu("Ориентация");
        for (NodeOrientation o : NodeOrientation.values()) {
            boolean current = single != null
                    && (single.getOrientation() == o || (single.getOrientation() == null && o == NodeOrientation.RIGHT));
            javax.swing.JMenuItem item = new javax.swing.JMenuItem((current ? "✓ " : "") + o.getLabel());
            item.addActionListener(ev -> {
                model.setSchemaNodesOrientation(targets, o);
                onChanged.run();
                repaint();
            });
            orientationMenu.add(item);
        }
        menu.add(orientationMenu);

        javax.swing.JMenuItem rotate = new javax.swing.JMenuItem("Повернуть по часовой");
        rotate.addActionListener(ev -> rotateSelectedNodes());
        menu.add(rotate);

        javax.swing.JCheckBoxMenuItem onlyUsed = new javax.swing.JCheckBoxMenuItem(
                "Только задействованные гнёзда", single != null && single.isOnlyUsedPorts());
        onlyUsed.addActionListener(ev -> {
            model.setOnlyUsedPorts(targets, onlyUsed.isSelected());
            onChanged.run();
            repaint();
        });
        menu.add(onlyUsed);

        boolean anyPlacements = targets.stream().anyMatch(n -> !n.getPortPlacements().isEmpty());
        javax.swing.JMenuItem resetLayout = new javax.swing.JMenuItem("Вернуть раскладку гнёзд по умолчанию");
        resetLayout.setEnabled(anyPlacements);
        resetLayout.addActionListener(ev -> {
            model.resetPortPlacements(targets);
            onChanged.run();
            repaint();
        });
        menu.add(resetLayout);
    }

    private static final Map<NodeSide, String> SIDE_LABELS = new java.util.LinkedHashMap<>();
    static {
        SIDE_LABELS.put(NodeSide.TOP, "Сверху");
        SIDE_LABELS.put(NodeSide.RIGHT, "Справа");
        SIDE_LABELS.put(NodeSide.BOTTOM, "Снизу");
        SIDE_LABELS.put(NodeSide.LEFT, "Слева");
    }

    /** Меню ГРУППЫ гнёзд (ПКМ по пину, PLAN.md, задача T3.3) — «Свернуть/Развернуть/
     *  Авто», «Сторона ▸», «Роль в этом проекте ▸», «Транзит ▸» — все читают/пишут
     *  через один и тот же {@link PortPlacement} группы (см. {@code AppModel}
     *  мутаторы {@code setGroupCollapsed}/{@code setPortPlacement}/{@code
     *  setPortRoleOverride}/{@code setPortThruOverride}), каждый пункт — отдельное
     *  действие отмены (симметрично тому, как правится один разъём в других
     *  диалогах конфигурации карт). */
    private void showGroupMenu(SchemaNode node, CardPort port, int x, int y) {
        String portId = port.getId();
        PortPlacement current = node.findPortPlacement(portId);
        JPopupMenu menu = new JPopupMenu();

        Boolean collapsed = current != null ? current.getCollapsed() : null;
        javax.swing.JMenuItem collapseAuto = new javax.swing.JMenuItem((collapsed == null ? "✓ " : "") + "Авто");
        collapseAuto.addActionListener(ev -> applyGroupCollapsed(node, portId, null));
        javax.swing.JMenuItem collapseYes = new javax.swing.JMenuItem(
                (Boolean.TRUE.equals(collapsed) ? "✓ " : "") + "Свернуть");
        collapseYes.addActionListener(ev -> applyGroupCollapsed(node, portId, Boolean.TRUE));
        javax.swing.JMenuItem collapseNo = new javax.swing.JMenuItem(
                (Boolean.FALSE.equals(collapsed) ? "✓ " : "") + "Развернуть");
        collapseNo.addActionListener(ev -> applyGroupCollapsed(node, portId, Boolean.FALSE));
        menu.add(collapseAuto);
        menu.add(collapseYes);
        menu.add(collapseNo);
        menu.addSeparator();

        NodeSide currentSide = current != null ? current.getSide() : null;
        Double currentOrder = current != null ? current.getOrder() : null;
        javax.swing.JMenu sideMenu = new javax.swing.JMenu("Сторона");
        javax.swing.JMenuItem sideAuto = new javax.swing.JMenuItem((currentSide == null ? "✓ " : "") + "Авто");
        sideAuto.addActionListener(ev -> {
            model.setPortPlacement(node, portId, null, currentOrder);
            onChanged.run();
            repaint();
        });
        sideMenu.add(sideAuto);
        for (var entry : SIDE_LABELS.entrySet()) {
            NodeSide side = entry.getKey();
            javax.swing.JMenuItem item = new javax.swing.JMenuItem((side == currentSide ? "✓ " : "") + entry.getValue());
            item.addActionListener(ev -> {
                model.setPortPlacement(node, portId, side, currentOrder);
                onChanged.run();
                repaint();
            });
            sideMenu.add(item);
        }
        menu.add(sideMenu);

        InterfaceRole currentRole = current != null ? current.getRoleOverride() : null;
        javax.swing.JMenu roleMenu = new javax.swing.JMenu("Роль в этом проекте");
        javax.swing.JMenuItem roleAuto = new javax.swing.JMenuItem((currentRole == null ? "✓ " : "") + "Авто (из библиотеки)");
        roleAuto.addActionListener(ev -> applyGroupRole(node, portId, null));
        roleMenu.add(roleAuto);
        for (InterfaceRole role : InterfaceRole.values()) {
            javax.swing.JMenuItem item = new javax.swing.JMenuItem((role == currentRole ? "✓ " : "") + role.getLabel());
            item.addActionListener(ev -> applyGroupRole(node, portId, role));
            roleMenu.add(item);
        }
        menu.add(roleMenu);

        Boolean currentThru = current != null ? current.getThruOverride() : null;
        javax.swing.JMenu thruMenu = new javax.swing.JMenu("Транзит");
        javax.swing.JMenuItem thruAuto = new javax.swing.JMenuItem((currentThru == null ? "✓ " : "") + "Авто");
        thruAuto.addActionListener(ev -> applyGroupThru(node, portId, null));
        javax.swing.JMenuItem thruYes = new javax.swing.JMenuItem(
                (Boolean.TRUE.equals(currentThru) ? "✓ " : "") + "Да");
        thruYes.addActionListener(ev -> applyGroupThru(node, portId, Boolean.TRUE));
        javax.swing.JMenuItem thruNo = new javax.swing.JMenuItem(
                (Boolean.FALSE.equals(currentThru) ? "✓ " : "") + "Нет");
        thruNo.addActionListener(ev -> applyGroupThru(node, portId, Boolean.FALSE));
        thruMenu.add(thruAuto);
        thruMenu.add(thruYes);
        thruMenu.add(thruNo);
        menu.add(thruMenu);

        menu.show(this, x, y);
    }

    private void applyGroupCollapsed(SchemaNode node, String portId, Boolean collapsed) {
        model.setGroupCollapsed(node, portId, collapsed);
        onChanged.run();
        repaint();
    }

    private void applyGroupRole(SchemaNode node, String portId, InterfaceRole role) {
        model.setPortRoleOverride(node, portId, role);
        onChanged.run();
        repaint();
    }

    private void applyGroupThru(SchemaNode node, String portId, Boolean thru) {
        model.setPortThruOverride(node, portId, thru);
        onChanged.run();
        repaint();
    }

    private void showNodeMenu(SchemaNode node, int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        if (node.getType() != SchemaNodeType.SCREEN) {
            addRenameMenuItem(menu, node);
            javax.swing.JMenu typeMenu = new javax.swing.JMenu("Изменить тип (внешний вид)");
            for (SchemaNodeType t : SchemaNodeType.values()) {
                if (t == SchemaNodeType.SCREEN) {
                    continue;
                }
                javax.swing.JMenuItem item = new javax.swing.JMenuItem(model.categoryLabel(t));
                item.setBackground(nodeColor(t));
                item.setOpaque(true);
                item.addActionListener(ev -> {
                    model.updateSchemaNode(node, node.getLabel(), t, node.getScreenRefId());
                    onChanged.run();
                    repaint();
                });
                typeMenu.add(item);
            }
            menu.add(typeMenu);
        }
        // Ручная привязка блока-контроллера к реальному экземпляру контроллера сцены
        // (см. AppModel.linkSchemaNodeToController) — тот же эффект, что и у блока,
        // заведённого автозаполнением (AppModel.autoPopulateSchema): подпись узла
        // получает "(Контроллер N)" в скобках (см. withControllerLegendTag), а
        // сопоставить блок со строкой легенды портов становится можно. Нужна тем, кто
        // автозаполнением не пользуется и заводит блоки контроллеров вручную.
        if (node.getType() == SchemaNodeType.CONTROLLER) {
            Scene scene = model.getCurrentScene();
            List<com.vjstb.ledscheme.model.ControllerInstance> controllers =
                    scene != null ? model.controllersInScene(scene) : List.of();
            javax.swing.JMenu linkMenu = new javax.swing.JMenu("Связать с…");
            linkMenu.setEnabled(!controllers.isEmpty());
            for (com.vjstb.ledscheme.model.ControllerInstance ci : controllers) {
                boolean current = ci.getId().equals(node.getControllerInstanceRefId());
                // Тип контроллера в скобках — чтобы при нескольких одинаково названных
                // "Контроллер N" было понятно, какой из них какой модели (запрос
                // пользователя, чат 2026-09-24).
                String typeName = model.getEquipmentPresets().stream()
                        .filter(pr -> pr.getId().equals(ci.getControllerTypeId()))
                        .map(com.vjstb.ledscheme.model.EquipmentPreset::getName).findFirst().orElse(null);
                javax.swing.JMenuItem item = new javax.swing.JMenuItem((current ? "✓ " : "") + ci.getLabel()
                        + (typeName != null ? " (" + typeName + ")" : ""));
                item.addActionListener(ev -> {
                    model.linkSchemaNodeToController(node, ci.getId());
                    onChanged.run();
                    repaint();
                });
                linkMenu.add(item);
            }
            if (node.getControllerInstanceRefId() != null) {
                if (!controllers.isEmpty()) {
                    linkMenu.addSeparator();
                }
                javax.swing.JMenuItem unlink = new javax.swing.JMenuItem("Отвязать");
                unlink.addActionListener(ev -> {
                    model.linkSchemaNodeToController(node, null);
                    onChanged.run();
                    repaint();
                });
                linkMenu.add(unlink);
            }
            menu.add(linkMenu);
        }
        // Карты (сигнальные гнёзда) доступны любому не-экранному узлу СИГНАЛЬНОЙ
        // схемы — симметрично тому, как "Разъёмы питания…" ниже доступны любому
        // не-экранному узлу схемы ПИТАНИЯ. Раньше это было ограничено только типами
        // "Медиасервер"/"Контроллер", из-за чего для остальных типов (конвертер,
        // прочее оборудование) не было способа добавить/отредактировать карты
        // вручную — хотя они могли УЖЕ иметь карты (например, из пресета).
        if (node.getMode() == SchemaMode.SIGNAL && node.getType() != SchemaNodeType.SCREEN && !node.isAutoPortLegend()) {
            javax.swing.JMenuItem cards = new javax.swing.JMenuItem("Комплектация карт…");
            cards.addActionListener(ev -> {
                CardsConfigDialog dlg = new CardsConfigDialog(SwingUtilities.getWindowAncestor(this), model, node);
                dlg.setVisible(true);
                onChanged.run();
                repaint();
            });
            menu.add(cards);
        }
        // Блок сетевого оборудования из библиотеки (docs/schema-ports-rework/PLAN.md,
        // D11/T3.4) — число портов Ethernet/Fiber могли поправить в библиотеке УЖЕ
        // ПОСЛЕ того, как блок поставили на схему; пункт пересобирает карту "Сеть"
        // по актуальным данным {@code NetworkDeviceType} (см. AppModel#refreshNetworkDevicePorts).
        if (node.getNetworkDeviceTypeId() != null) {
            javax.swing.JMenuItem refreshPorts = new javax.swing.JMenuItem("Обновить порты из библиотеки");
            refreshPorts.addActionListener(ev -> {
                try {
                    model.refreshNetworkDevicePorts(node);
                    onChanged.run();
                    repaint();
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            });
            menu.add(refreshPorts);
        }
        if (node.getMode() == SchemaMode.POWER && node.getType() != SchemaNodeType.SCREEN) {
            javax.swing.JMenuItem connectors = new javax.swing.JMenuItem("Разъёмы питания…");
            connectors.addActionListener(ev -> {
                String title = node.getLabel() == null || node.getLabel().isEmpty()
                        ? model.categoryLabel(node.getType()) : node.getLabel();
                PowerConnectorsConfigDialog dlg = new PowerConnectorsConfigDialog(
                        SwingUtilities.getWindowAncestor(this), title,
                        PowerConnectorsConfigDialog.forNode(model, node), model);
                dlg.setVisible(true);
                onChanged.run();
                repaint();
            });
            menu.add(connectors);
        }
        if (node.getType() == SchemaNodeType.SCREEN && onScreenActivated != null) {
            javax.swing.JMenuItem open = new javax.swing.JMenuItem("Открыть цепочки этого экрана");
            open.addActionListener(ev -> {
                Screen scr = screenById(node.getScreenRefId());
                if (scr != null) {
                    onScreenActivated.accept(scr);
                }
            });
            menu.add(open);
        }
        if (hasPorts(node)) {
            menu.addSeparator();
            addBlockLayoutMenuItems(menu, List.of(node));
        }
        javax.swing.JMenuItem del = new javax.swing.JMenuItem("Удалить узел");
        del.addActionListener(ev -> {
            model.deleteSchemaNode(node);
            selectedNodes.remove(node);
            onChanged.run();
            repaint();
        });
        menu.add(del);
        menu.show(this, x, y);
    }

    private void addRenameMenuItem(JPopupMenu menu, SchemaNode node) {
        javax.swing.JMenuItem rename = new javax.swing.JMenuItem("Переименовать");
        rename.addActionListener(ev -> {
            String input = JOptionPane.showInputDialog(this, "Подпись узла:", node.getLabel());
            if (input != null) {
                model.updateSchemaNode(node, input.trim(), node.getType(), node.getScreenRefId());
                onChanged.run();
                repaint();
            }
        });
        menu.add(rename);
    }

    private void showEdgeMenu(SchemaEdge edge, int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        javax.swing.JMenuItem label = new javax.swing.JMenuItem("Подпись связи…");
        label.addActionListener(ev -> editEdgeLabel(edge));
        menu.add(label);

        javax.swing.JCheckBoxMenuItem dashedItem = new javax.swing.JCheckBoxMenuItem("Пунктиром", edge.isDashed());
        dashedItem.addActionListener(ev -> {
            model.setSchemaEdgeDashed(edge, dashedItem.isSelected());
            onChanged.run();
            repaint();
        });
        menu.add(dashedItem);

        javax.swing.JMenuItem colorItem = new javax.swing.JMenuItem("Цвет линии…");
        colorItem.addActionListener(ev -> {
            Color initial = edge.getColor() != null ? new Color(edge.getColor()) : style.defaultEdgeColor;
            Color chosen = UiKit.showColorChooser(this, "Цвет линии связи", initial, settings);
            if (chosen != null) {
                model.setSchemaEdgeColor(edge, chosen.getRGB());
                onChanged.run();
                repaint();
            }
        });
        menu.add(colorItem);
        if (edge.getColor() != null) {
            javax.swing.JMenuItem resetColor = new javax.swing.JMenuItem("Сбросить цвет линии");
            resetColor.addActionListener(ev -> {
                model.setSchemaEdgeColor(edge, null);
                onChanged.run();
                repaint();
            });
            menu.add(resetColor);
        }
        if (edge.getLabelDx() != 0 || edge.getLabelDy() != 0) {
            javax.swing.JMenuItem resetLabelPos = new javax.swing.JMenuItem("Вернуть подпись на линию");
            resetLabelPos.addActionListener(ev -> {
                model.setSchemaEdgeLabelOffset(edge, 0, 0);
                onChanged.run();
                repaint();
            });
            menu.add(resetLabelPos);
        }

        // «Маршрут ▸» (docs/schema-ports-rework/PLAN.md, задача T4.4/§2.6) — прямое
        // переключение режима прокладки, в отличие от «Выпрямить» ниже (которое
        // только стирает изломы, не трогая сам режим).
        javax.swing.JMenu routeMenu = new javax.swing.JMenu("Маршрут");
        EdgeRouteMode currentRouteMode = edge.effectiveRouteMode();
        javax.swing.JRadioButtonMenuItem autoItem =
                new javax.swing.JRadioButtonMenuItem("Авто под 90°", currentRouteMode == EdgeRouteMode.AUTO);
        autoItem.addActionListener(ev -> {
            model.setEdgeRouteMode(edge, EdgeRouteMode.AUTO);
            onChanged.run();
            repaint();
        });
        routeMenu.add(autoItem);
        javax.swing.JRadioButtonMenuItem manualItem =
                new javax.swing.JRadioButtonMenuItem("Вручную", currentRouteMode == EdgeRouteMode.MANUAL);
        manualItem.addActionListener(ev -> {
            model.setEdgeRouteMode(edge, EdgeRouteMode.MANUAL);
            onChanged.run();
            repaint();
        });
        routeMenu.add(manualItem);
        javax.swing.JRadioButtonMenuItem straightItem =
                new javax.swing.JRadioButtonMenuItem("Прямая", currentRouteMode == EdgeRouteMode.STRAIGHT);
        straightItem.addActionListener(ev -> {
            model.setEdgeRouteMode(edge, EdgeRouteMode.STRAIGHT);
            onChanged.run();
            repaint();
        });
        routeMenu.add(straightItem);
        menu.add(routeMenu);

        javax.swing.JMenuItem straighten = new javax.swing.JMenuItem("Выпрямить");
        straighten.setEnabled(!edge.getWaypoints().isEmpty());
        straighten.addActionListener(ev -> {
            model.setSchemaEdgeWaypoints(edge, List.of());
            onChanged.run();
            repaint();
        });
        menu.add(straighten);

        javax.swing.JMenuItem del = new javax.swing.JMenuItem("Удалить связь");
        del.addActionListener(ev -> {
            model.deleteSchemaEdge(edge);
            selectSingleEdge(null);
            onChanged.run();
            repaint();
        });
        menu.add(del);
        menu.show(this, x, y);
    }

    private void showWaypointMenu(SchemaEdge edge, int index, int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        javax.swing.JMenuItem del = new javax.swing.JMenuItem("Удалить точку излома");
        del.addActionListener(ev -> {
            List<EdgeWaypoint> updated = new ArrayList<>(edge.getWaypoints());
            updated.remove(index);
            model.setSchemaEdgeWaypoints(edge, updated);
            onChanged.run();
            repaint();
        });
        menu.add(del);
        menu.show(this, x, y);
    }

    /** Подсказка при наведении на значок "⚠" перегруженного узла (Task #102) —
     *  раньше пользователю приходилось открывать «Разъёмы питания…», чтобы понять,
     *  чем именно вызвано предупреждение; теперь достаточно навести курсор. */
    @Override
    public String getToolTipText(MouseEvent e) {
        // overloadIconRects хранит координаты в МОДЕЛЬНОМ пространстве (как и все
        // остальные хит-тесты) — курсор нужно перевести через текущий scale, иначе
        // подсказка перестаёт совпадать со значком при отличном от 1.0 масштабе.
        Point mp = toModel(e.getPoint());
        for (var entry : overloadIconRects.entrySet()) {
            if (entry.getValue().contains(mp)) {
                SchemaNode n = entry.getKey();
                Scene loadScene = model.getCurrentScene();
                if (loadScene == null) {
                    return null;
                }
                com.vjstb.ledscheme.service.SchemaLoadCalc.NodeLoad load =
                        com.vjstb.ledscheme.service.SchemaLoadCalc.evaluate(n, loadScene, model);
                boolean kw = settings.activeProfile().isPowerUnitKw();
                return "<html>Перегрузка узла «" + escapeHtml(n.getLabel() != null && !n.getLabel().isEmpty()
                        ? n.getLabel() : model.categoryLabel(n.getType())) + "»<br>"
                        + "Нагрузка через исходящие связи: " + UiKit.fmtPower(load.loadWatts(), kw) + "<br>"
                        + "Ёмкость входных разъёмов: " + UiKit.fmtPower(load.capacityWatts(), kw) + "<br>"
                        + "Подтвердить/изменить запас — «Разъёмы питания…» этого узла.</html>";
            }
        }
        return null;
    }

    private static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public Dimension getPreferredSize() {
        double maxX = 800, maxY = 500;
        for (SchemaNode n : nodes()) {
            maxX = Math.max(maxX, n.getX() + n.getWidth() + MARGIN);
            maxY = Math.max(maxY, n.getY() + n.getHeight() + MARGIN);
        }
        return new Dimension((int) Math.round(maxX * scale), (int) Math.round(maxY * scale));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        // Масштаб — только для ИНТЕРАКТИВНОГО вида (Ctrl+колесо, см. mouseWheelMoved);
        // renderImage (экспорт пакета документации) вызывает paint(...) напрямую в
        // обход этого метода и всегда рендерит в полном/логическом масштабе 1:1,
        // независимо от того, что сейчас видно на экране у инженера.
        g2.scale(scale, scale);
        int logicalW = (int) Math.ceil(getWidth() / scale);
        int logicalH = (int) Math.ceil(getHeight() / scale);
        paint(g2, logicalW, logicalH, settings.activeProfile().isSchemaScreensAsWiringDiagram());
        drawSnapGuides(g2, logicalW, logicalH);
        drawRubberBand(g2);
        drawGroupDragPreview(g2);
        g2.dispose();
    }

    /** Подсветка целевой стороны при перетаскивании группы гнёзд (см. {@link
     *  #draggingGroupNode}, PLAN.md, задача T3.3) — толстая линия вдоль всей
     *  стороны, куда попадёт группа, если отпустить сейчас, плюс метка в самой
     *  точке курсора (порядок вставки среди других групп ТОЙ же стороны на глаз не
     *  проверить, но само место — да). Ничего не рисует, пока порог сдвига (см.
     *  {@link #draggingGroupMoved}) не пройден — короткий клик остаётся просто
     *  выделением узла, без визуального шума. */
    private void drawGroupDragPreview(Graphics2D g2) {
        if (draggingGroupNode == null || !draggingGroupMoved || draggingGroupPreviewSide == null) {
            return;
        }
        double x = draggingGroupNode.getX(), y = draggingGroupNode.getY();
        double w = draggingGroupNode.getWidth(), h = draggingGroupNode.getHeight();
        g2.setColor(style.accent);
        g2.setStroke(new BasicStroke(3f));
        switch (draggingGroupPreviewSide) {
            case TOP -> g2.drawLine((int) x, (int) y, (int) (x + w), (int) y);
            case BOTTOM -> g2.drawLine((int) x, (int) (y + h), (int) (x + w), (int) (y + h));
            case LEFT -> g2.drawLine((int) x, (int) y, (int) x, (int) (y + h));
            case RIGHT -> g2.drawLine((int) (x + w), (int) y, (int) (x + w), (int) (y + h));
        }
        if (lastMouse != null) {
            int r = 5;
            g2.fillOval(lastMouse.x - r, lastMouse.y - r, r * 2, r * 2);
        }
    }

    /** Прямоугольник-«резинка» протяжки выделения (см. {@link #selectedNodes}) —
     *  полупрозрачная заливка + пунктирная рамка, как в большинстве редакторов
     *  схем; ничего не рисует, пока протяжка не идёт. */
    private void drawRubberBand(Graphics2D g2) {
        if (rubberBandStart == null || rubberBandCurrent == null) {
            return;
        }
        int x1 = Math.min(rubberBandStart.x, rubberBandCurrent.x);
        int y1 = Math.min(rubberBandStart.y, rubberBandCurrent.y);
        int w = Math.abs(rubberBandCurrent.x - rubberBandStart.x);
        int h = Math.abs(rubberBandCurrent.y - rubberBandStart.y);
        g2.setColor(style.rubberBandFill);
        g2.fillRect(x1, y1, w, h);
        g2.setColor(style.rubberBandBorder);
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0, new float[]{4, 4}, 0));
        g2.drawRect(x1, y1, w, h);
    }

    /** Направляющие линии привязки (Shift-перетаскивание, см. snapPosition) — яркая
     *  пунктирная линия через всю видимую область, как в yEd Graph Editor, показывает
     *  С ЧЕМ ИМЕННО сейчас выровнен перетаскиваемый узел. */
    private void drawSnapGuides(Graphics2D g2, int width, int height) {
        if (snapGuideX == null && snapGuideY == null) {
            return;
        }
        g2.setColor(Color.MAGENTA);
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0, new float[]{4, 4}, 0));
        if (snapGuideX != null) {
            int x = (int) Math.round(snapGuideX);
            g2.drawLine(x, 0, x, height);
        }
        if (snapGuideY != null) {
            int y = (int) Math.round(snapGuideY);
            g2.drawLine(0, y, width, y);
        }
    }

    /** Рендерит схему в изображение заданного размера — не зависит от реального
     *  размера/видимости компонента (используется при экспорте пакета документации,
     *  см. OutputStagePanel, где панель никогда не добавляется в контейнер).
     *  renderScreenWiring — false: обычный блок экрана (имя + краткая статистика),
     *  как в редакторе; true — "тестовая" версия, где в ТОМ ЖЕ прямоугольнике узла
     *  вместо текста рисуется уменьшенная схема расключения этого экрана (см.
     *  drawScreenWiringThumbnail) — геометрия схемы (позиции/размеры узлов, линии
     *  связей) в обоих вариантах одна и та же, отличается только содержимое внутри
     *  блоков экранов. */
    public BufferedImage renderImage(int width, int height, boolean renderScreenWiring) {
        return renderImage(width, height, renderScreenWiring, 1.0);
    }

    /** {@code dpiScale} — множитель качества экспорта (см. {@code UserProfile#getDocExportDpi},
     *  1.0 = прежнее поведение) — весь рисунок равномерно увеличивается через
     *  {@link Graphics2D#scale}, планировка (позиции/размеры узлов) считается всё
     *  так же в логических {@code width}×{@code height}, просто на бОльшее число
     *  физических пикселей итоговой картинки. */
    public BufferedImage renderImage(int width, int height, boolean renderScreenWiring, double dpiScale) {
        BufferedImage img = new BufferedImage(Math.max(1, (int) Math.round(width * dpiScale)),
                Math.max(1, (int) Math.round(height * dpiScale)), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.scale(dpiScale, dpiScale);
        exporting = true;
        try {
            paint(g2, width, height, renderScreenWiring);
        } finally {
            exporting = false;
        }
        g2.dispose();
        return img;
    }

    /** {@code true}, если в «Предпочтениях» выбран классический (дорефакторинговый,
     *  «как до этого плана») рендер и хит-тестинг общей схемы (docs/schema-ports-
     *  rework/PLAN.md, задача T5.5, решение D16) — по умолчанию {@code false}
     *  (см. {@link com.vjstb.ledscheme.settings.UserProfile#getSchemaRenderMode()}). */
    private boolean classicMode() {
        return settings.activeProfile().getSchemaRenderMode() == SchemaRenderMode.CLASSIC;
    }

    private void paint(Graphics2D g2, int width, int height, boolean renderScreenWiring) {
        style = SchemaStyle.forPreset(settings.activeProfile().getSchemaStylePreset());
        if (classicMode()) {
            // Классический режим (T5.5) — полностью отдельный путь отрисовки/
            // хит-тестинга, см. paintClassic() и соседние *Classic-методы в конце
            // файла; сюда доходит только выбор пресета оформления (style) — он
            // общий для обоих режимов (T3.1 — ортогональная переработка).
            paintClassic(g2, width, height, renderScreenWiring);
            return;
        }
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(style.background);
        g2.fillRect(0, 0, width, height);

        List<SchemaNode> ns = nodes();
        List<SchemaEdge> es = edges();

        if (ns.isEmpty() && es.isEmpty()) {
            g2.setColor(style.mutedText);
            g2.setFont(getFont().deriveFont(14f));
            String msg = "Схема пока пуста. Добавьте узлы оборудования справа.";
            g2.drawString(msg, MARGIN, MARGIN + 20);
            g2.dispose();
            return;
        }

        // связи — под узлами
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setFont(EDGE_FONT);
        java.awt.FontMetrics edgeFm = g2.getFontMetrics();
        // Маршрут каждой связи считаем один раз — и для отрисовки, и для поиска
        // пересечений («мостики», см. ниже) нужна одна и та же ломаная.
        Map<SchemaEdge, List<double[]>> routeCache = new IdentityHashMap<>();
        for (SchemaEdge edge : es) {
            routeCache.put(edge, routePoints(edge));
        }
        WireHopStyle hopStyle = settings.activeProfile().getSchemaWireHopStyle();
        Map<SchemaEdge, List<double[]>> hopMap = hopStyle != WireHopStyle.NONE
                ? computeWireHops(es, routeCache) : null;
        WireHopGeometry.ArcShape arcShape = hopStyle == WireHopStyle.TRUNCATED
                ? WireHopGeometry.ArcShape.FLAT_TOP : WireHopGeometry.ArcShape.CUBIC;
        for (SchemaEdge edge : es) {
            List<double[]> pts = routeCache.get(edge);
            if (pts == null) {
                continue;
            }
            // Подсветка — ЛЮБАЯ связь из многовыделения (docs/schema-ports-rework/
            // PLAN.md доводка, баг-репорт пользователя 2026-09-18: "не работает
            // групповое выделение линий"); точки излома (ниже) — только у "главной"
            // {@link #selectedEdge}, тащить сразу несколько смысла не имеет.
            boolean selected = selectedEdges.contains(edge);
            Color customColor = edge.getColor() != null ? new Color(edge.getColor()) : null;
            g2.setColor(selected ? style.accent : customColor != null ? customColor : edgeDefaultColor(edge));
            float strokeWidth = selected ? 3f : 2f;
            // Пунктир — переключатель "Пунктиром" в контекстном меню связи (Task #85/v1.4),
            // например для обходного/резервного/мониторингового пути, как в референсном PDF;
            // ИЛИ автоматически для роли "Синхро" на печатном пресете (T6.1, см.
            // autoDashedForPrintSync) — сложение, а не замена одного другим.
            g2.setStroke(edge.isDashed() || autoDashedForPrintSync(edge)
                    ? new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{7, 5}, 0)
                    : new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // Ломаная линия через точки излома (см. EdgeWaypoint) вместо одной прямой —
            // ортогональная/произвольная маршрутизация. Если включены «мостики» и на
            // этой связи есть пересечения, где она сверху — рисуем её единым путём с
            // дугами-обходами (см. WireHopGeometry), иначе обычными отрезками. Форма
            // дуги — из настройки (полукруглая / усечённая с плоской вершиной);
            // близкие пересечения сливаются в один расширенный пролёт.
            List<double[]> hops = hopMap == null ? null : hopMap.get(edge);
            List<WireHopGeometry.HopSpan> hopSpans = hops == null || hops.isEmpty()
                    ? List.of()
                    : WireHopGeometry.hopSpans(pts, hops, HOP_RADIUS);
            if (!hopSpans.isEmpty()) {
                g2.draw(WireHopGeometry.hoppedPathFromSpans(pts, hopSpans, HOP_RADIUS, arcShape));
            } else {
                for (int i = 0; i < pts.size() - 1; i++) {
                    g2.drawLine((int) Math.round(pts.get(i)[0]), (int) Math.round(pts.get(i)[1]),
                            (int) Math.round(pts.get(i + 1)[0]), (int) Math.round(pts.get(i + 1)[1]));
                }
            }
            // Стрелка направления (docs/schema-ports-rework/PLAN.md, задача T4.5, D15):
            // по умолчанию — ОДНА, на последнем (ближайшем к приёмнику) отрезке
            // расширенной ломаной, кроме тех, что лежат под дугой (границы дуги дают
            // две неинтерактивные точки излома — стрелка встаёт до/после дуги, не на
            // ней); настройка «на каждом отрезке» — прежнее поведение целиком (читается
            // как «поток идёт по всей линии», не только «откуда куда»).
            WireHopGeometry.RenderRoute rr = WireHopGeometry.renderPoints(pts, hopSpans);
            List<double[]> rpts = rr.points();
            boolean[] onArc = rr.arcSegment();
            for (int i : arrowSegmentIndices(rpts.size(), onArc, settings.activeProfile().getSchemaArrowPlacement())) {
                drawArrow(g2, rpts.get(i)[0], rpts.get(i)[1], rpts.get(i + 1)[0], rpts.get(i + 1)[1]);
            }
            // Точки излома видны и хватаются мышью только у "ГЛАВНОЙ" связи
            // ({@link #selectedEdge}, не у всего многовыделения {@link
            // #selectedEdges}) — тащить точку излома сразу у нескольких связей не
            // имеет смысла (у каждой свои собственные точки), да и {@link
            // #waypointAt} ищет только среди точек ИМЕННО {@code selectedEdge}.
            if (edge == selectedEdge) {
                for (int i = 1; i < pts.size() - 1; i++) {
                    int wx = (int) pts.get(i)[0], wy = (int) pts.get(i)[1];
                    g2.setColor(Color.WHITE);
                    g2.fillOval(wx - 4, wy - 4, 8, 8);
                    g2.setColor(style.accent);
                    g2.drawOval(wx - 4, wy - 4, 8, 8);
                }
            }
            g2.setStroke(new BasicStroke(strokeWidth));

            // Кликабельный «чип» подписи — клик сразу открывает ввод подписи, без
            // необходимости искать тонкую линию и знать про ПКМ. У связи с УЖЕ
            // назначенной подписью чип виден всегда (это содержимое схемы — то же,
            // что видно и в экспорте). Пустое приглашение «+ подпись» — только пока
            // с этой связью реально взаимодействуют (наведение/выделение) и НИКОГДА в
            // экспорте (там некликабельно, только шум) — PLAN.md D14, задача T4.5:
            // до этой правки чип рисовался ВСЕГДА для КАЖДОЙ связи, что на плотной
            // схеме перекрывало соседние линии сплошным полем приглашений.
            java.awt.Rectangle chip = labelChipBounds(edge);
            String display = edge.displayLabel();
            boolean hasLabel = display != null && !display.isEmpty();
            boolean showChip = hasLabel || shouldShowEmptyLabelChip(edge, selected);
            if (chip != null && showChip) {
                g2.setColor(selected ? style.accent : hasLabel ? style.labelChipBackground : style.labelChipBackgroundEmpty);
                g2.fillRoundRect(chip.x, chip.y, chip.width, chip.height, 8, 8);
                g2.setColor(selected ? style.selectedOutline : style.labelChipBorder);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(chip.x, chip.y, chip.width, chip.height, 8, 8);
                g2.setColor(hasLabel || selected ? style.labelChipText : style.labelChipTextEmpty);
                String text = hasLabel ? display : "+ подпись";
                g2.drawString(text, chip.x + 7, chip.y + chip.height - edgeFm.getDescent() - 2);
                g2.setFont(EDGE_FONT);
            }
        }

        // превью соединения в режиме CONNECT
        if (interaction == Interaction.CONNECT && connectPendingId != null && lastMouse != null) {
            SchemaNode pending = nodeById(connectPendingId);
            if (pending != null) {
                Point socket = connectPendingCabinetInstanceId != null
                        ? cabinetSocketPosition(pending, connectPendingCabinetInstanceId)
                        : socketPosition(pending, connectPendingPortId, null);
                int px, py;
                if (socket != null) {
                    px = socket.x;
                    py = socket.y;
                } else {
                    double[] center = {pending.getX() + pending.getWidth() / 2.0, pending.getY() + pending.getHeight() / 2.0};
                    double[] clipped = clipToBorder(pending, center, new double[]{lastMouse.x, lastMouse.y});
                    px = (int) clipped[0];
                    py = (int) clipped[1];
                }
                g2.setColor(style.accent);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0,
                        new float[]{5, 4}, 0));
                g2.drawLine(px, py, lastMouse.x, lastMouse.y);
            }
        }

        Font titleFont = getFont().deriveFont(Font.BOLD, 12f);
        Font metaFont = getFont().deriveFont(10f);
        layoutCache.clear();
        overloadIconRects.clear();
        for (SchemaNode n : ns) {
            boolean selected = selectedNodes.contains(n);
            boolean pending = n.getId().equals(connectPendingId);
            int nw = (int) n.getWidth(), nh = (int) n.getHeight();
            Color fill = style.nodeFill(n.getType());
            g2.setColor(fill);
            g2.fillRoundRect((int) n.getX(), (int) n.getY(), nw, nh, 10, 10);
            g2.setColor(pending ? style.pendingOutline : (selected ? style.selectedOutline : style.nodeBorder));
            g2.setStroke(new BasicStroke(selected || pending ? 2.5f : 1.4f));
            g2.drawRoundRect((int) n.getX(), (int) n.getY(), nw, nh, 10, 10);

            // Перегрузка силового узла (Task #87) — суммарная нагрузка, уходящая через
            // исходящие связи узла, превышает ёмкость его входных разъёмов (см.
            // SchemaLoadCalc). Отдельный контур поверх обычной рамки + значок в углу —
            // не заменяет обычное выделение, а накладывается на него.
            boolean overloaded = false;
            if (mode == SchemaMode.POWER && n.getType() != SchemaNodeType.SCREEN
                    && settings.activeProfile().isLoadTrackingEnabled()) {
                Scene loadScene = model.getCurrentScene();
                if (loadScene != null) {
                    overloaded = com.vjstb.ledscheme.service.SchemaLoadCalc.evaluate(n, loadScene, model).overloaded();
                }
            }
            if (overloaded) {
                g2.setColor(style.warn);
                g2.setStroke(new BasicStroke(3f));
                g2.drawRoundRect((int) n.getX() - 1, (int) n.getY() - 1, nw + 2, nh + 2, 12, 12);
            }

            String title = n.getType() == SchemaNodeType.SCREEN ? resolveScreenLabel(n) : n.getLabel();
            if (title == null || title.isEmpty()) {
                title = model.categoryLabel(n.getType());
            }
            title = withControllerLegendTag(n, title);
            g2.setColor(style.titleText);
            g2.setFont(titleFont);
            // Гнёзда теперь на РАМКЕ блока (docs/schema-ports-rework/PLAN.md, задача
            // T3.2) — для узла с гнёздами название ставим ПО ЦЕНТРУ, в отведённой под
            // него полосе СРАЗУ ПОСЛЕ строки гнёзд верхней стороны, если она есть (см.
            // SchemaLayoutMetrics.TITLE_BAND) — раньше центрирование включалось только
            // в вертикальном режиме (где строка гнёзд сверху была всегда), теперь оно
            // не зависит от ориентации: она может увести "верхнюю" роль на любую из
            // четырёх сторон рамки. Узлы без гнёзд (экран, легенда портов, прочее без
            // комплектации) — как раньше, название в углу.
            boolean hasPorts = hasPorts(n);
            if (hasPorts) {
                boolean hasTopPins = nodeLayout(n).pins().stream()
                        .anyMatch(p -> p.side() == com.vjstb.ledscheme.model.NodeSide.TOP);
                double topOffset = hasTopPins
                        ? com.vjstb.ledscheme.service.schemalayout.SchemaLayoutMetrics.HORIZONTAL_SIDE_DEPTH : 0;
                String clippedTitle = clipToWidth(g2, title, nw - 16);
                int titleW = g2.getFontMetrics().stringWidth(clippedTitle);
                int titleX = (int) n.getX() + (nw - titleW) / 2;
                int titleY = (int) (n.getY() + topOffset
                        + com.vjstb.ledscheme.service.schemalayout.SchemaLayoutMetrics.TITLE_BAND - 6);
                g2.drawString(clippedTitle, titleX, titleY);
            } else {
                drawClipped(g2, title, (int) n.getX() + 8, (int) n.getY() + 20, nw - 16);
            }
            g2.setFont(metaFont);
            g2.setColor(style.metaText);
            if (n.getType() == SchemaNodeType.SCREEN) {
                if (renderScreenWiring) {
                    drawScreenWiringThumbnail(g2, n, nw, nh);
                } else {
                    drawClipped(g2, screenMeta(n), (int) n.getX() + 8, (int) n.getY() + 38, nw - 16);
                }
            } else if (n.isAutoPortLegend()) {
                drawPortLegendContent(g2, n, nw, nh);
            } else if (n.isAutoLineLegend()) {
                drawLineLegendContent(g2, n, nw, nh);
            } else if (hasPorts) {
                drawNodeSockets(g2, n);
                drawEdgeBundleMarkers(g2, n, es);
            } else {
                drawClipped(g2, model.categoryLabel(n.getType()), (int) n.getX() + 8, (int) n.getY() + 38, nw - 16);
            }

            if (overloaded) {
                g2.setColor(style.warn);
                g2.setFont(titleFont);
                int iconX = (int) n.getX() + nw - 20;
                int iconY = (int) n.getY() + 16;
                g2.drawString("⚠", iconX, iconY);
                // Небольшой запас вокруг символа — попадание курсором в сам глиф
                // (не только в его базовую линию) для подсказки ниже.
                overloadIconRects.put(n, new java.awt.Rectangle(iconX - 2, iconY - 14, 20, 18));
            }

            // Уголок изменения размера — маленький треугольник в правом нижнем углу,
            // виден только у выделенного узла, чтобы не загромождать обычный вид.
            if (selected) {
                int hx = (int) n.getX() + nw, hy = (int) n.getY() + nh;
                int[] xs = {hx - RESIZE_HANDLE, hx, hx};
                int[] ys = {hy, hy - RESIZE_HANDLE, hy};
                g2.setColor(style.resizeHandle);
                g2.fillPolygon(xs, ys, 3);
            }
        }

        g2.dispose();
    }

    private String resolveScreenLabel(SchemaNode n) {
        Screen scr = screenById(n.getScreenRefId());
        return scr != null ? scr.getName() : "(экран удалён)";
    }

    /** Дописывает к подписи узла-контроллера, автозаполненного из реального
     *  экземпляра (см. {@link SchemaNode#getControllerInstanceRefId()}), в скобках то
     *  же обозначение ("Контроллер N"), которым его называет легенда портов (см. {@link
     *  AppModel#signalPortLegendRows(Scene)}) — подпись узла ("MCTRL4k" и т.п.) редактируется
     *  свободно и от метки контроллера не зависит, без этого сопоставить блок на холсте
     *  со строкой легенды было нечем. Ничего не делает для узлов без связи с реальным
     *  контроллером или если метка уже совпадает с подписью (не дублировать "N (N)"). */
    private String withControllerLegendTag(SchemaNode n, String title) {
        if (n.getType() != SchemaNodeType.CONTROLLER || n.getControllerInstanceRefId() == null) {
            return title;
        }
        Scene scene = model.getCurrentScene();
        String legendLabel = scene != null
                ? model.controllerInstanceLabel(scene, n.getControllerInstanceRefId()) : null;
        if (legendLabel == null || legendLabel.isEmpty() || legendLabel.equals(title)) {
            return title;
        }
        return title + " (" + legendLabel + ")";
    }

    private String screenMeta(SchemaNode n) {
        Screen scr = screenById(n.getScreenRefId());
        if (scr == null) {
            return "ссылка недействительна";
        }
        if (mode == SchemaMode.POWER) {
            String sockets = inSocketsSummary(n.getPowerConnectors());
            return scr.getCols() + "×" + scr.getRows() + " каб. · " + model.powerChainsTouchingScreen(scr).size()
                    + " вводных" + (sockets.isEmpty() ? "" : " (" + sockets + ")");
        }
        // Резерв порта (витая пара) подразумевается по умолчанию на используемом
        // контроллере — в блок-схеме площадки это лишняя детализация, не нужно
        // отдельно выводить её здесь (в отличие от самой прописи сигнала экрана).
        // Резерв целого контроллера — структурный факт про оборудование площадки,
        // его в общей схеме показать стоит.
        int controllerBackups = 0;
        for (com.vjstb.ledscheme.model.ControllerInstance ci : model.controllersInScene(model.getCurrentScene())) {
            if (ci.getBackupControllerId() != null) {
                controllerBackups++;
            }
        }
        String signalSockets = "";
        for (SchemaCard c : n.getCards()) {
            if ("Вводы сигнала".equals(c.getName())) {
                signalSockets = inSocketsSummary(c.getPorts());
                break;
            }
        }
        return "портов: " + model.effectiveSignalPortCount(scr) + " · " + model.signalChainsTouchingScreen(scr).size()
                + " вводных" + (signalSockets.isEmpty() ? "" : " (" + signalSockets + ")")
                + (controllerBackups > 0 ? " · резерв контроллера: " + controllerBackups : "");
    }

    /** Краткая сводка ВХОДНЫХ гнёзд узла-экрана вида «2×PowerCon, 1×Ethernet» —
     *  показывает автоматически отслеженные гнёзда (см. AppModel.addPowerChain/
     *  addSignalChain) прямо в блоке экрана, без переключения на подробную схему
     *  расключения. */
    private static String inSocketsSummary(List<CardPort> ports) {
        StringBuilder sb = new StringBuilder();
        for (CardPort p : ports) {
            if (p.getDirection() != PortDirection.IN) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(p.getCount()).append('×').append(p.getConnectorType());
        }
        return sb.toString();
    }

    /** "Тестовая" замена обычного текста статистики узла-экрана (см. renderScreenWiring
     *  в {@link #paint}) — рисует уменьшенную схему расключения ЭТОГО экрана (сетка
     *  кабинетов + цепочки текущего режима) внутри того же прямоугольника узла, где
     *  обычно был бы текст "20×10 каб. · N вводных". Масштаб подбирается так, чтобы
     *  вся сетка кабинетов экрана поместилась в доступную область узла (под заголовком
     *  с именем экрана) — если узел маленький, сетка мелкая (как и было бы у любой
     *  миниатюры); чтобы получить более подробную картинку, нужно заранее увеличить
     *  узел в редакторе (см. уголок изменения размера). Не выходит за границы узла —
     *  область рисования обрезается по прямоугольнику узла. */
    /** Геометрия миниатюры расключения узла-экрана — вынесена из
     *  {@link #drawScreenWiringThumbnail} отдельным методом, чтобы гнёзда-кабинеты
     *  (см. {@link #cabinetSocketRect}) могли получить ТУ ЖЕ позицию/масштаб ячейки
     *  без дублирования математики (иначе рисунок и хит-тест/привязка линии могли бы
     *  незаметно разойтись при будущей правке одного из двух мест). null — миниатюру
     *  показать нечем (нет экрана/типа кабинета/сетки, либо узел слишком мал). */
    private record ThumbGeometry(Screen screen, CabinetType type, int left, int top, int cellW, int cellH) {
    }

    private ThumbGeometry wiringThumbGeometry(SchemaNode n) {
        Screen scr = screenById(n.getScreenRefId());
        if (scr == null) {
            return null;
        }
        CabinetType t = model.typeOf(scr);
        if (t == null || t.getWidthMm() <= 0 || t.getHeightMm() <= 0 || scr.getCols() <= 0 || scr.getRows() <= 0) {
            return null;
        }
        int nw = (int) n.getWidth(), nh = (int) n.getHeight();
        int pad = 4;
        int top = (int) n.getY() + 34;
        int left = (int) n.getX() + pad;
        int availW = nw - pad * 2;
        int gridBottom = (int) (n.getY() + nh) - pad;
        int availH = gridBottom - top;
        if (availW < 10 || availH < 10) {
            return null;
        }
        // Границы могут выходить за номинальную сетку (кабинет вытащен свободным
        // смещением, Task #7/v1.6) — считаем масштаб/якорь по ФАКТИЧЕСКОМУ охвату,
        // иначе такой кабинет обрезается клипом узла или рисуется поверх соседей
        // не вписавшись в миниатюру (баг-репорт: "блок схема не включает в себя
        // смещённый кабинет").
        double[] ext = ScreenLogic.cabinetExtentMm(scr, t, model.getWorkspace());
        double extW = ext[2] - ext[0], extH = ext[3] - ext[1];
        double scale = Math.min(availW / extW, availH / extH);
        if (scale <= 0) {
            return null;
        }
        int cellW = Math.max(1, (int) Math.round(t.getWidthMm() * scale));
        int cellH = Math.max(1, (int) Math.round(t.getHeightMm() * scale));
        // left/top должны оставаться якорем ИМЕННО ячейки (col=0,row=0) — как и
        // ожидает paintWiringDiagram/cabX/cabY (та же развязка "номинальный якорь
        // сетки" vs "расширенная рамка охвата", что и в SceneCanvasPanel.screenGridX/
        // screenGridY) — поэтому сдвигаем left/top НАЗАД на -ext[0]/-ext[1], давая
        // место кабинетам, ушедшим в отрицательные локальные координаты (выше/левее
        // номинального угла), вместо того чтобы сдвигать саму точку (0,0).
        left -= (int) Math.round(ext[0] * scale);
        top -= (int) Math.round(ext[1] * scale);
        return new ThumbGeometry(scr, t, left, top, cellW, cellH);
    }

    private void drawScreenWiringThumbnail(Graphics2D g2, SchemaNode n, int nw, int nh) {
        ThumbGeometry g = wiringThumbGeometry(n);
        if (g == null) {
            return;
        }
        Scene scene = model.getCurrentScene();
        List<PowerChain> powerChains = scene != null ? scene.getPowerChains() : List.of();
        List<SignalChain> signalChains = scene != null ? scene.getSignalChains() : List.of();
        Graphics2D clipped = (Graphics2D) g2.create();
        clipped.clipRect((int) n.getX(), (int) n.getY(), nw, nh);
        SchemeRenderer.paintWiringDiagram(clipped, g.screen(), g.type(), mode == SchemaMode.POWER,
                g.cellW(), g.cellH(), g.left(), g.top(), model.getWorkspace(), powerChains, signalChains,
                model.controllersInScene(scene), settings.activeProfile().isPowerUnitKw());
        drawChainEndpointSockets(clipped, n, g);
        clipped.dispose();
    }

    /** Содержимое авто-блока «Легенда портов» (см. {@link SchemaNode#isAutoPortLegend()},
     *  {@link AppModel#signalPortLegendRows(Scene)}) — таблица "Экран/Main/Backup".
     *  Колонка "Экран" — по ширине самого длинного имени экрана (+ отступ), чтобы не
     *  тратить на неё больше места, чем реально нужно; оставшаяся ширина делится
     *  ПОРОВНУ между Main и Backup. Строки, не поместившиеся по высоте узла, просто
     *  обрезаются (растянуть блок ниже — уголком, как у любого другого узла). Ничего
     *  не показывает в режиме ПИТАНИЯ — деление на main/backup имеет смысл только для
     *  сигнала. */
    private void drawPortLegendContent(Graphics2D g2, SchemaNode n, int nw, int nh) {
        int left = (int) n.getX() + 8;
        int top = (int) n.getY() + 38;
        int maxY = (int) n.getY() + nh - 4;
        int tableW = nw - 16;
        if (mode == SchemaMode.POWER) {
            drawClipped(g2, "легенда портов доступна в режиме «Сигнал»", left, top, tableW);
            return;
        }
        Scene scene = model.getCurrentScene();
        List<AppModel.SignalPortLegendRow> rows = scene != null ? model.signalPortLegendRows(scene) : List.of();
        if (rows.isEmpty()) {
            drawClipped(g2, "нет расключённых экранов", left, top, tableW);
            return;
        }
        Graphics2D clipped = (Graphics2D) g2.create();
        clipped.clipRect((int) n.getX(), (int) n.getY(), nw, nh);
        Font base = clipped.getFont();
        Font boldFont = base.deriveFont(Font.BOLD);

        clipped.setFont(boldFont);
        int col1W = clipped.getFontMetrics().stringWidth("Экран");
        clipped.setFont(base);
        java.awt.FontMetrics regularFm = clipped.getFontMetrics();
        for (AppModel.SignalPortLegendRow row : rows) {
            col1W = Math.max(col1W, regularFm.stringWidth(row.screenName()));
        }
        col1W += 14;
        int gap = 6;
        int portColsW = Math.max(60, tableW - col1W - 2 * gap);
        col1W = tableW - portColsW - 2 * gap;
        int col2W = portColsW / 2;
        int col3W = portColsW - col2W;
        int col1 = left;
        int col2 = col1 + col1W + gap;
        int col3 = col2 + col2W + gap;

        int y = top;
        int lineH = clipped.getFontMetrics().getHeight() + 4;
        clipped.setFont(boldFont);
        drawClipped(clipped, "Экран", col1, y, col1W);
        drawClipped(clipped, "Main", col2, y, col2W);
        drawClipped(clipped, "Backup", col3, y, col3W);
        clipped.setColor(new Color(0, 0, 0, 100));
        clipped.drawLine((int) n.getX() + 6, y + 4, (int) n.getX() + nw - 6, y + 4);
        y += lineH;
        clipped.setFont(base);
        clipped.setColor(new Color(0, 0, 0, 170));
        for (AppModel.SignalPortLegendRow row : rows) {
            if (y > maxY) {
                break;
            }
            drawClipped(clipped, row.screenName(), col1, y, col1W);
            drawClipped(clipped, row.main(), col2, y, col2W);
            drawClipped(clipped, row.backup(), col3, y, col3W);
            y += lineH;
        }
        clipped.dispose();
    }

    /** Содержимое авто-блока «Легенда линий» (docs/schema-ports-rework/PLAN.md, задача
     *  T5.4, см. {@link SchemaNode#isAutoLineLegend()}) — список "цветной штрих —
     *  подпись": роли (режим сигнала, {@link AppModel#lineLegendRoles}) или номиналы
     *  разъёмов (режим питания, {@link AppModel#lineLegendPowerNominals}) — только те,
     *  что РЕАЛЬНО используются связями текущей сцены (не весь набор ролей/номиналов),
     *  чтобы легенда не разрасталась строками про то, чего на схеме и так нет. Цвет
     *  каждой строки — тот же {@link SchemaStyle#roleLineColor}/{@link
     *  SchemaStyle#powerNominalLineColor}, что реально красит линию (см. {@link
     *  #edgeDefaultColor}), с тем же откатом на {@link SchemaStyle#defaultEdgeColor}. */
    private void drawLineLegendContent(Graphics2D g2, SchemaNode n, int nw, int nh) {
        int left = (int) n.getX() + 8;
        int top = (int) n.getY() + 38;
        int maxY = (int) n.getY() + nh - 4;
        int tableW = nw - 16;
        Scene scene = model.getCurrentScene();
        List<String> labels = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        if (scene != null) {
            if (mode == SchemaMode.SIGNAL) {
                for (InterfaceRole role : model.lineLegendRoles(scene)) {
                    Color c = style.roleLineColor(role);
                    labels.add(role.getLabel());
                    colors.add(c != null ? c : style.defaultEdgeColor);
                }
            } else {
                for (String nominal : model.lineLegendPowerNominals(scene)) {
                    Color c = style.powerNominalLineColor(nominal);
                    labels.add(nominal);
                    colors.add(c != null ? c : style.defaultEdgeColor);
                }
            }
        }
        if (labels.isEmpty()) {
            drawClipped(g2, "нет связей с определённым цветом", left, top, tableW);
            return;
        }
        Graphics2D clipped = (Graphics2D) g2.create();
        clipped.clipRect((int) n.getX(), (int) n.getY(), nw, nh);
        int swatch = 12;
        int y = top;
        int lineH = Math.max(swatch + 4, clipped.getFontMetrics().getHeight() + 4);
        for (int i = 0; i < labels.size(); i++) {
            if (y - swatch > maxY) {
                break;
            }
            clipped.setColor(colors.get(i));
            clipped.fillRect(left, y - swatch + 2, swatch, swatch);
            clipped.setColor(style.nodeBorder);
            clipped.drawRect(left, y - swatch + 2, swatch, swatch);
            clipped.setColor(new Color(0, 0, 0, 170));
            drawClipped(clipped, labels.get(i), left + swatch + 6, y, tableW - swatch - 6);
            y += lineH;
        }
        clipped.dispose();
    }

    /** Кабинеты-«гнёзда» (см. AppModel.chainEndpointSocketCabinetIds) поверх миниатюры
     *  расключения — видны, только когда включены ОБА тумблера: «коммутация через
     *  гнёзда разъёмов» и «вводные кабинеты цепочек — тоже гнёзда подключения» (см.
     *  Preferences). Чисто наложение поверх уже нарисованной миниатюры — сама миниатюра
     *  не меняется, никакой новой геометрии кроме уже вычисленной {@code g}. */
    private void drawChainEndpointSockets(Graphics2D g2, SchemaNode n, ThumbGeometry g) {
        if (!settings.activeProfile().isSocketWiringEnabled(mode)
                || !settings.activeProfile().isChainEndpointSocketsEnabled(mode)) {
            return;
        }
        Set<String> socketIds = model.chainEndpointSocketCabinetIds(mode, g.screen());
        if (socketIds.isEmpty()) {
            return;
        }
        Graphics2D g3 = (Graphics2D) g2.create();
        g3.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (String cabId : socketIds) {
            CabinetInstance cab = g.screen().cabinetById(cabId);
            if (cab == null) {
                continue;
            }
            java.awt.Rectangle r = SchemeRenderer.cabinetScreenRect(cab, g.type(), g.cellW(), g.cellH(),
                    g.left(), g.top(), model.getWorkspace());
            boolean pending = cabId.equals(connectPendingCabinetInstanceId) && n.getId().equals(connectPendingId);
            boolean hovered = hoveredCabinetSocket != null && hoveredCabinetSocket.node() == n
                    && cabId.equals(hoveredCabinetSocket.cabinetInstanceId());
            int d = Math.max(6, Math.min(r.width, r.height) / 2);
            int cx = r.x + r.width / 2, cy = r.y + r.height / 2;
            g3.setColor(pending ? style.cabinetSocketPending : (hovered ? style.cabinetSocketHovered : style.cabinetSocketFill));
            g3.fillOval(cx - d / 2, cy - d / 2, d, d);
            g3.setColor(style.cabinetSocketBorder);
            g3.setStroke(new BasicStroke(1.2f));
            g3.drawOval(cx - d / 2, cy - d / 2, d, d);
        }
        g3.dispose();
    }

    /** Гнездо-кабинет под точкой клика/курсора — только когда сама миниатюра
     *  расключения показана (см. {@link #wiringThumbGeometry}) и оба тумблера гнёзд
     *  включены (см. {@link #drawChainEndpointSockets}). Хит-тест — по прямоугольнику
     *  ячейки кабинета целиком (как и рисуется), без отдельного запаса — ячейка и так
     *  обычно достаточно крупная цель. */
    private record CabinetSocketHit(SchemaNode node, String cabinetInstanceId) {
    }

    private CabinetSocketHit cabinetSocketAt(Point p) {
        if (!settings.activeProfile().isSocketWiringEnabled(mode)
                || !settings.activeProfile().isChainEndpointSocketsEnabled(mode)) {
            return null;
        }
        for (SchemaNode n : nodes()) {
            if (n.getType() != SchemaNodeType.SCREEN) {
                continue;
            }
            ThumbGeometry g = wiringThumbGeometry(n);
            if (g == null) {
                continue;
            }
            Set<String> socketIds = model.chainEndpointSocketCabinetIds(mode, g.screen());
            for (String cabId : socketIds) {
                CabinetInstance cab = g.screen().cabinetById(cabId);
                if (cab == null) {
                    continue;
                }
                java.awt.Rectangle r = SchemeRenderer.cabinetScreenRect(cab, g.type(), g.cellW(), g.cellH(),
                        g.left(), g.top(), model.getWorkspace());
                if (r.contains(p)) {
                    return new CabinetSocketHit(n, cabId);
                }
            }
        }
        return null;
    }

    /** Экранный центр кабинета-«гнезда» {@code cabinetInstanceId} на миниатюре
     *  расключения узла {@code n} — null, если миниатюра не показана/кабинет не
     *  найден (узел изменился), тогда вызывающий код (см. endpointsFor) откатывается
     *  к обычной привязке от узла целиком. */
    private Point cabinetSocketPosition(SchemaNode n, String cabinetInstanceId) {
        if (cabinetInstanceId == null) {
            return null;
        }
        ThumbGeometry g = wiringThumbGeometry(n);
        if (g == null) {
            return null;
        }
        CabinetInstance cab = g.screen().cabinetById(cabinetInstanceId);
        if (cab == null) {
            return null;
        }
        java.awt.Rectangle r = SchemeRenderer.cabinetScreenRect(cab, g.type(), g.cellW(), g.cellH(), g.left(), g.top(),
                model.getWorkspace());
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    /** Диаметр рисуемой точки-гнезда — независим от шага раскладки {@link
     *  com.vjstb.ledscheme.service.schemalayout.SchemaLayoutMetrics#ROW_STEP} (та
     *  же независимость, что была у прежних CONNECTOR_DOT_D/CONNECTOR_ROW_H). */
    private static final int PIN_DOT_D = 7;
    /** Запас вокруг гнезда для хит-теста клика/наведения. */
    private static final int SOCKET_HIT_PAD = 8;

    /** Цвет точки-гнезда по типу разъёма — см. {@link SchemaStyle#connectorDotColor}
     *  (docs/schema-ports-rework/PLAN.md, задача T3.1 — раньше палитра/хэш были
     *  захардкожены прямо здесь, теперь часть пресета оформления). */
    private Color connectorColor(String type) {
        return style.connectorDotColor(type);
    }

    /** Список гнёзд узла (карты для сигнала, разъёмы для питания) — пусто, если
     *  комплектация не задана. Раньше отрисовка карт была ошибочно ограничена
     *  типами "Медиасервер"/"Контроллер" — но карты может нести ЛЮБОЙ тип узла
     *  (например, узел из пресета с картами, впоследствии переклассифицированный
     *  в "Прочее оборудование"/"Конвертер" через "Изменить тип"), и раз карты уже
     *  назначены — их гнёзда должны отрисовываться независимо от типа узла. */
    private static boolean hasPorts(SchemaNode n) {
        return !n.getCards().isEmpty() || !n.getPowerConnectors().isEmpty();
    }

    private static List<com.vjstb.ledscheme.service.schemalayout.NodePortLayout.CardGroup> cardGroupsOf(SchemaNode n) {
        List<com.vjstb.ledscheme.service.schemalayout.NodePortLayout.CardGroup> groups = new ArrayList<>();
        for (SchemaCard c : n.getCards()) {
            groups.add(new com.vjstb.ledscheme.service.schemalayout.NodePortLayout.CardGroup(
                    c.getId(), c.getName(), c.getPorts()));
        }
        if (!n.getPowerConnectors().isEmpty()) {
            groups.add(new com.vjstb.ledscheme.service.schemalayout.NodePortLayout.CardGroup(
                    null, null, n.getPowerConnectors()));
        }
        return groups;
    }

    /** Раскладка гнёзд узла на рамке блока (docs/schema-ports-rework/PLAN.md,
     *  задача T3.2) — общая для отрисовки, хит-теста клика/наведения и привязки
     *  конца линии связи к конкретному гнезду: все три должны видеть ОДНУ И ТУ ЖЕ
     *  геометрию, иначе клик и картинка разъедутся (тот же принцип, что был у
     *  прежнего {@code computeSocketRects}). Сама раскладка — целиком в {@link
     *  com.vjstb.ledscheme.service.schemalayout.NodePortLayout}; здесь только
     *  сборка входных данных узла и кэш на время ОДНОГО кадра отрисовки ({@link
     *  #layoutCache}, очищается в начале {@link #paint(Graphics2D, int, int,
     *  boolean)}) — раскладка запрашивается много раз за кадр (сама отрисовка узла
     *  + оба конца КАЖДОЙ связи, ссылающейся на него, + хит-тест клика). */
    private final Map<SchemaNode, com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Result> layoutCache =
            new IdentityHashMap<>();

    private com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Result nodeLayout(SchemaNode n) {
        return layoutCache.computeIfAbsent(n, node -> {
            List<com.vjstb.ledscheme.service.schemalayout.NodePortLayout.CardGroup> groups = cardGroupsOf(node);
            if (groups.isEmpty()) {
                return new com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Result(List.of(), List.of(),
                        List.of(), new com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Size(
                                node.getWidth(), node.getHeight()));
            }
            com.vjstb.ledscheme.model.NodeOrientation orientation = node.getOrientation() != null
                    ? node.getOrientation() : settings.activeProfile().getDefaultOrientation(mode);
            Boolean defaultCollapsed = switch (settings.activeProfile().getGroupDisplay(mode)) {
                case ALWAYS_COLLAPSED -> Boolean.TRUE;
                case ALWAYS_EXPANDED -> Boolean.FALSE;
                case AUTO -> null;
            };
            var in = new com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Input(mode, node.getType(),
                    orientation, groups, edges(), node.getPortPlacements(), node.isOnlyUsedPorts(), defaultCollapsed,
                    model.getInterfaceTypes(), com.vjstb.ledscheme.service.schemalayout.TextMeasure.awt());
            return com.vjstb.ledscheme.service.schemalayout.NodePortLayout.layout(in, node.getWidth(), node.getHeight());
        });
    }

    /** Гнёзда узла НА РАМКЕ блока — отсеки карт (шапка/скобка), точки-гнёзда,
     *  строки "ещё …" при нехватке места/включённой «только задействованные» (см.
     *  {@link com.vjstb.ledscheme.service.schemalayout.NodePortLayout}, docs/
     *  schema-ports-rework/PLAN.md, задача T3.2). Координаты раскладки — в системе
     *  узла [0,w]×[0,h], здесь переводятся в абсолютные координаты холста. */
    private void drawNodeSockets(Graphics2D g2, SchemaNode n) {
        var layout = nodeLayout(n);
        double ox = n.getX(), oy = n.getY();
        for (var bay : layout.bays()) {
            drawBayBackground(g2, bay, layout.pins(), ox, oy, (int) n.getWidth(), (int) n.getHeight());
        }
        for (var bay : layout.bays()) {
            drawBay(g2, bay, layout.pins(), ox, oy, (int) n.getWidth(), (int) n.getHeight());
        }
        drawGroupBrackets(g2, layout, ox, oy, (int) n.getHeight());
        for (var pin : layout.pins()) {
            drawPin(g2, n, pin, ox, oy);
        }
        for (var overflow : layout.overflow()) {
            drawOverflow(g2, overflow, ox, oy, (int) n.getWidth(), (int) n.getHeight());
        }
    }

    /** Общее название развёрнутой группы гнёзд на TOP/BOTTOM — рисуется ОДИН раз
     *  над/под всей группой колонок, а не на каждой (см. {@link #pinLabel}: там для
     *  TOP/BOTTOM оставлен только номер слота именно ПОТОМУ, что полное название
     *  показывает эта скобка — иначе близко стоящие колонки визуально сливались бы,
     *  докcs/schema-ports-rework/PLAN.md, задача T3.2). Группы находятся простым
     *  проходом по уже готовым пинам — соседние пины с {@code slotCount()>1} и тем
     *  же {@code CardPort} всегда идут подряд (гарантия {@link
     *  com.vjstb.ledscheme.service.schemalayout.NodePortLayout}). */
    private void drawGroupBrackets(Graphics2D g2, com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Result layout,
                                    double ox, double oy, int nh) {
        List<com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Pin> pins = layout.pins();
        int i = 0;
        while (i < pins.size()) {
            var p = pins.get(i);
            boolean topOrBottom = p.side() == com.vjstb.ledscheme.model.NodeSide.TOP
                    || p.side() == com.vjstb.ledscheme.model.NodeSide.BOTTOM;
            if (!topOrBottom || p.slotCount() <= 1) {
                i++;
                continue;
            }
            int runEnd = i + 1;
            while (runEnd < pins.size() && pins.get(runEnd).side() == p.side() && pins.get(runEnd).port() == p.port()) {
                runEnd++;
            }
            double xStart = ox + pins.get(i).x();
            double xEnd = ox + pins.get(runEnd - 1).x();
            boolean top = p.side() == com.vjstb.ledscheme.model.NodeSide.TOP;
            int bracketY = (int) (oy + (top
                    ? com.vjstb.ledscheme.service.schemalayout.SchemaLayoutMetrics.HORIZONTAL_SIDE_DEPTH - 10
                    : nh - com.vjstb.ledscheme.service.schemalayout.SchemaLayoutMetrics.HORIZONTAL_SIDE_DEPTH + 4));
            g2.setColor(style.cardBlockHeaderText);
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine((int) xStart, bracketY, (int) xEnd, bracketY);
            String label = clipToWidth(g2, p.port().getConnectorType(), (int) (xEnd - xStart) + 20);
            int tw = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, (int) ((xStart + xEnd) / 2 - tw / 2.0), bracketY + 9);
            i = runEnd;
        }
    }

    /** Рамка-подложка отсека карты — визуально выделяет границы карты внутри блока
     *  (была в дорефакторинговой версии — {@code computeSocketRects}/{@code
     *  drawCardBlockBorders}, потерялась при переходе на {@link
     *  com.vjstb.ledscheme.service.schemalayout.NodePortLayout} в T3.2; вернул
     *  обратно по отзыву пользователя: "не нравится что пропала подложка карт,
     *  обозначающая границы карточек внутри блока... облегчали чтение", реплика
     *  2026-09-17). Как и {@link #drawBay}, ничего не рисует для {@code bay.label()
     *  == null} (питание/единственная карта узла — рамка от самого блока уже
     *  достаточна). Для TOP/BOTTOM глубина фиксирована ({@link
     *  com.vjstb.ledscheme.service.schemalayout.SchemaLayoutMetrics
     *  #HORIZONTAL_SIDE_DEPTH}); для LEFT/RIGHT — по факту содержимого этого
     *  ОТСЕКА (шапка + подписи его пинов), тем же {@link FontMetrics}, что и сама
     *  отрисовка текста — без изменения публичного API раскладки ради одной
     *  декоративной рамки. */
    private void drawBayBackground(Graphics2D g2, com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Bay bay,
                                    List<com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Pin> pins,
                                    double ox, double oy, int nw, int nh) {
        if (bay.label() == null) {
            return;
        }
        FontMetrics fm = g2.getFontMetrics();
        boolean horizontal = bay.side() == com.vjstb.ledscheme.model.NodeSide.TOP
                || bay.side() == com.vjstb.ledscheme.model.NodeSide.BOTTOM;
        g2.setColor(style.cardBlockBorder);
        g2.setStroke(new BasicStroke(1f));
        if (horizontal) {
            int depth = (int) com.vjstb.ledscheme.service.schemalayout.SchemaLayoutMetrics.HORIZONTAL_SIDE_DEPTH;
            int y = bay.side() == com.vjstb.ledscheme.model.NodeSide.TOP ? (int) oy : (int) (oy + nh - depth);
            int x = (int) (ox + bay.alongStart());
            int w = Math.max(1, (int) (bay.alongEnd() - bay.alongStart()));
            g2.drawRect(x, y, w, depth);
        } else {
            int depth = bayDepth(fm, bay, pins);
            int x = bay.side() == com.vjstb.ledscheme.model.NodeSide.LEFT ? (int) ox : (int) (ox + nw - depth);
            int y = (int) (oy + bay.alongStart());
            int h = Math.max(1, (int) (bay.alongEnd() - bay.alongStart()));
            g2.drawRect(x, y, depth, h);
        }
    }

    /** Глубина рамки-подложки LEFT/RIGHT отсека — максимум ширины его собственных
     *  подписей (шапка карты + подписи ЕГО пинов, не всей стороны), плюс тот же
     *  отступ, что у самого текста ({@link
     *  com.vjstb.ledscheme.service.schemalayout.SchemaLayoutMetrics#LABEL_PAD}
     *  дважды — с обеих сторон подписи). */
    private int bayDepth(FontMetrics fm, com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Bay bay,
                          List<com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Pin> pins) {
        int max = fm.stringWidth(bay.label());
        for (var p : pins) {
            if (p.side() == bay.side() && Objects.equals(p.cardId(), bay.cardId())) {
                max = Math.max(max, fm.stringWidth(pinLabel(p)));
            }
        }
        int pad = (int) com.vjstb.ledscheme.service.schemalayout.SchemaLayoutMetrics.LABEL_PAD;
        return Math.max((int) com.vjstb.ledscheme.service.schemalayout.SchemaLayoutMetrics.VERTICAL_SIDE_DEPTH_MIN,
                max + pad * 2);
    }

    /** Шапка/скобка отсека карты — только название (текст ВСЕГДА горизонтальный,
     *  PLAN.md §2.4), без рамки-заливки: для LEFT/RIGHT — надпись у ближнего края
     *  над первым гнездом отсека; для TOP/BOTTOM — надпись со скобкой по ширине
     *  группы, второй строкой за номерами гнёзд (см. {@code SchemaLayoutMetrics
     *  .HORIZONTAL_SIDE_DEPTH}). Питание/единственная карта узла — без шапки
     *  ({@code bay.label() == null}, см. NodePortLayout). Если весь отсек TOP/BOTTOM
     *  — это ОДНА группа (развёрнутая или свёрнутая), шапку карты не рисуем вовсе:
     *  для развёрнутой группы на том же месте уже рисует скобку с названием типа
     *  разъёма {@link #drawGroupBrackets}, для свёрнутой — сам пин показывает полную
     *  подпись ("N×Тип") {@link #drawPin}; в обоих случаях шапка карты — дублирующая
     *  подпись В ТОЙ ЖЕ "второй строке" под/над гнёздами, а не отдельная полезная
     *  строка (см. DIALOG.md/PLAN.md, задача T3.2, найдено пиксельным просмотром
     *  рендера Disguise D3: сперва "Basic Set" наложилось на скобку "Ethernet Cat6"
     *  у развёрнутой группы, затем — на собственную подпись "3×Ethernet Cat6" у
     *  свёрнутой). */
    private void drawBay(Graphics2D g2, com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Bay bay,
                          List<com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Pin> pins,
                          double ox, double oy, int nw, int nh) {
        if (bay.label() == null) {
            return;
        }
        boolean topOrBottom = bay.side() == com.vjstb.ledscheme.model.NodeSide.TOP
                || bay.side() == com.vjstb.ledscheme.model.NodeSide.BOTTOM;
        if (topOrBottom && baySpanIsSingleGroup(pins, bay)) {
            return;
        }
        g2.setColor(style.cardBlockHeaderText);
        switch (bay.side()) {
            case LEFT -> {
                String clipped = clipToWidth(g2, bay.label(), nw - 16);
                g2.drawString(clipped, (int) ox + 4, (int) (oy + bay.alongStart()) + 9);
            }
            case RIGHT -> {
                String clipped = clipToWidth(g2, bay.label(), nw - 16);
                int w = g2.getFontMetrics().stringWidth(clipped);
                g2.drawString(clipped, (int) (ox + nw) - 4 - w, (int) (oy + bay.alongStart()) + 9);
            }
            case TOP, BOTTOM -> {
                String clipped = clipToWidth(g2, bay.label(), (int) (bay.alongEnd() - bay.alongStart()));
                int tw = g2.getFontMetrics().stringWidth(clipped);
                int textX = (int) (ox + (bay.alongStart() + bay.alongEnd()) / 2 - tw / 2.0);
                boolean top = bay.side() == com.vjstb.ledscheme.model.NodeSide.TOP;
                int bracketY = (int) (oy + (top
                        ? com.vjstb.ledscheme.service.schemalayout.SchemaLayoutMetrics.HORIZONTAL_SIDE_DEPTH - 10
                        : nh - com.vjstb.ledscheme.service.schemalayout.SchemaLayoutMetrics.HORIZONTAL_SIDE_DEPTH + 4));
                int textY = bracketY + (top ? 9 : 9);
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine((int) (ox + bay.alongStart()), bracketY, (int) (ox + bay.alongEnd()), bracketY);
                g2.drawString(clipped, textX, textY);
            }
        }
    }

    /** {@code true}, если ВСЕ пины отсека (та же сторона и та же карта, что у
     *  {@code bay}) принадлежат ОДНОЙ группе {@link CardPort} — см. javadoc {@link
     *  #drawBay} про то, почему в этом случае шапку карты не рисуем. */
    private static boolean baySpanIsSingleGroup(
            List<com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Pin> pins,
            com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Bay bay) {
        CardPort sole = null;
        for (var p : pins) {
            if (p.side() != bay.side() || !Objects.equals(p.cardId(), bay.cardId())) {
                continue;
            }
            if (sole == null) {
                sole = p.port();
            } else if (sole != p.port()) {
                return false;
            }
        }
        return sole != null;
    }

    private void drawPin(Graphics2D g2, SchemaNode node, com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Pin pin,
                          double ox, double oy) {
        int cx = (int) Math.round(ox + pin.x());
        int cy = (int) Math.round(oy + pin.y());
        boolean used = com.vjstb.ledscheme.service.schemalayout.SchemaUsage.isPortUsed(edges(), pin.port().getId());
        boolean hovered = hoveredSocket != null && hoveredSocket.node() == node && hoveredSocket.port() == pin.port();
        // Занятое гнездо — залито цветом типа разъёма; свободное — базовым цветом
        // на контраст с темой (style.socketEmptyFill), иначе на тёмном фоне блока
        // видно только тонкое чёрное кольцо, которое с ним сливается (отзыв
        // пользователя 2026-09-16, docs/schema-ports-rework/PLAN.md).
        g2.setColor(used ? connectorColor(pin.port().getConnectorType()) : style.socketEmptyFill);
        g2.fillOval(cx - PIN_DOT_D / 2, cy - PIN_DOT_D / 2, PIN_DOT_D, PIN_DOT_D);
        g2.setColor(hovered ? style.socketRingHovered : style.socketRingDefault);
        g2.setStroke(new BasicStroke(hovered ? 2f : 1f));
        int ring = hovered ? 2 : 0;
        g2.drawOval(cx - PIN_DOT_D / 2 - ring, cy - PIN_DOT_D / 2 - ring, PIN_DOT_D + ring * 2, PIN_DOT_D + ring * 2);

        String label = pinLabel(pin);
        g2.setColor(style.socketLabelText);
        FontMetrics fm = g2.getFontMetrics();
        switch (pin.side()) {
            case LEFT -> g2.drawString(clipToWidth(g2, label, 160), cx + PIN_DOT_D, cy + fm.getAscent() / 2 - 1);
            case RIGHT -> {
                String clipped = clipToWidth(g2, label, 160);
                g2.drawString(clipped, cx - PIN_DOT_D - fm.stringWidth(clipped), cy + fm.getAscent() / 2 - 1);
            }
            case TOP -> {
                String clipped = clipToWidth(g2, label, topBottomLabelMaxWidth(pin));
                g2.drawString(clipped, cx - fm.stringWidth(clipped) / 2, cy + PIN_DOT_D + fm.getAscent());
            }
            case BOTTOM -> {
                String clipped = clipToWidth(g2, label, topBottomLabelMaxWidth(pin));
                g2.drawString(clipped, cx - fm.stringWidth(clipped) / 2, cy - PIN_DOT_D - 2);
            }
        }
    }

    /** Максимальная ширина подписи пина TOP/BOTTOM — свёрнутая группа (один пин,
     *  полный текст "N×Тип") получает столько же места, сколько LEFT/RIGHT: {@link
     *  com.vjstb.ledscheme.service.schemalayout.NodePortLayout} уже зарезервировал
     *  под неё СВОЙ, достаточно широкий шаг (см. его {@code groupStep} для
     *  свёрнутых горизонтальных групп) — раньше здесь был жёстко зашит один и тот же
     *  узкий предел 40px и для длинного "1×Genlock Blackburst", и для короткого
     *  номера слота развёрнутой группы, из-за чего первое обрезалось почти всегда
     *  (баг-репорт пользователя, DIALOG.md/PLAN.md, задача T3.2). Развёрнутая группа
     *  (несколько узких колонок) по-прежнему получает узкий предел — там подпись
     *  это только номер слота, а полное название рисует {@link #drawGroupBrackets}
     *  один раз на всю группу. */
    private static int topBottomLabelMaxWidth(com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Pin pin) {
        return pin.slotCount() <= 1 ? 160 : 24;
    }

    /** Подпись гнезда: "N×Тип" (свёрнуто) или "Тип #N" (развёрнуто) — см. §2.4
     *  PLAN.md. У ПИТАНИЯ дополнительно фазы и автомат («CEE 32A · 3ф · авт. 40А»,
     *  задача T3.2) — тех же полей {@link CardPort#getPhaseCount()}/{@link
     *  CardPort#getBreakerAmps()}, что уже показывает {@code
     *  PowerConnectorsConfigDialog}, просто теперь и на самой схеме, не только в
     *  диалоге комплектации. */
    private String pinLabel(com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Pin pin) {
        CardPort port = pin.port();
        boolean topOrBottom = pin.side() == com.vjstb.ledscheme.model.NodeSide.TOP
                || pin.side() == com.vjstb.ledscheme.model.NodeSide.BOTTOM;
        String base;
        if (pin.slotCount() <= 1) {
            base = port.getCount() + "×" + port.getConnectorType();
        } else if (topOrBottom) {
            // TOP/BOTTOM ставит гнёзда КОЛОНКАМИ вплотную друг к другу — полная подпись
            // "Тип #N" на КАЖДОЙ колонке гарантированно налезала бы на соседние (см.
            // DIALOG.md/PLAN.md, задача T3.2, найдено пиксельным просмотром рендера
            // Blackmagic: 6 колонок "Genlock (SDI) #N" слились в нечитаемое пятно).
            // Только номер слота — общее название группы показывает {@link
            // #drawGroupBracket} ОДИН раз на всю группу, как заголовок отсека карты.
            base = String.valueOf(pin.slotIndex() + 1);
        } else {
            base = port.getConnectorType() + " #" + (pin.slotIndex() + 1);
        }
        if (mode != SchemaMode.POWER) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base);
        if (port.getPhaseCount() > 1) {
            sb.append(" · ").append(port.getPhaseCount()).append("ф");
        }
        if (port.getBreakerAmps() != null) {
            sb.append(" · авт. ").append(formatAmps(port.getBreakerAmps())).append("А");
        }
        return sb.toString();
    }

    private static String formatAmps(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private void drawOverflow(Graphics2D g2, com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Overflow o,
                               double ox, double oy, int nw, int nh) {
        g2.setColor(style.cardBlockHeaderText);
        switch (o.side()) {
            case LEFT -> g2.drawString(o.text(), (int) ox + 4, (int) (oy + nh) - 6);
            case RIGHT -> {
                int w = g2.getFontMetrics().stringWidth(o.text());
                g2.drawString(o.text(), (int) (ox + nw) - 4 - w, (int) (oy + nh) - 6);
            }
            case TOP -> g2.drawString(o.text(), (int) ox + 4,
                    (int) (oy + com.vjstb.ledscheme.service.schemalayout.SchemaLayoutMetrics.HORIZONTAL_SIDE_DEPTH) - 2);
            case BOTTOM -> g2.drawString(o.text(), (int) ox + 4, (int) (oy + nh) - 2);
        }
    }

    /** Экранные координаты (центр) гнезда с указанным id разъёма — null, если
     *  разъём не найден (узел изменился/разъём удалён) или portId не задан, тогда
     *  вызывающий код обычно откатывается к привязке от узла целиком. forEdge —
     *  КАКАЯ связь запрашивает точку (null — для превью ещё не созданной связи,
     *  см. mouseDragged/CONNECT): у развёрнутой группы одному portId соответствует
     *  НЕСКОЛЬКО гнёзд — тогда нужно выбрать ИМЕННО ТО, что физически соответствует
     *  этой связи (по порядку создания среди связей на этом portId, см. {@link
     *  com.vjstb.ledscheme.service.schemalayout.SchemaUsage#edgeOrdinalForPort}), а
     *  не всегда первое — иначе несколько параллельных линий одной группы визуально
     *  сходились бы в одну точку. IN_OUT (сквозной проход) даёт РОВНО одно гнездо
     *  на слот (см. PLAN.md D-решения) — попадает в ту же ветку "одно совпадение",
     *  отдельного случая для него больше не нужно (было нужно, пока IN_OUT рисовал
     *  ДВЕ точки на гнездо). */
    private Point socketPosition(SchemaNode node, String portId, SchemaEdge forEdge) {
        // Развилка MODERN/CLASSIC (docs/schema-ports-rework/PLAN.md, задача T5.5) —
        // единая точка входа для отрисовки (endpointsFor/routePoints), превью
        // соединения и хит-теста; см. socketPositionClassic в конце файла.
        return classicMode() ? socketPositionClassic(node, portId, forEdge) : socketPositionModern(node, portId, forEdge);
    }

    private Point socketPositionModern(SchemaNode node, String portId, SchemaEdge forEdge) {
        var p = pinFor(node, portId, forEdge);
        return p == null ? null : new Point((int) Math.round(node.getX() + p.x()), (int) Math.round(node.getY() + p.y()));
    }

    /** Пин раскладки для гнезда — общая логика для {@link #socketPosition} (нужна
     *  только точка) и {@link #autoRoutePoints} (нужна ещё и {@link
     *  com.vjstb.ledscheme.model.NodeSide сторона} для {@code OrthogonalRouter},
     *  docs/schema-ports-rework/PLAN.md, задача T4.4). См. javadoc {@link
     *  #socketPosition} про выбор конкретного пина у развёрнутой группы. */
    private com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Pin pinFor(
            SchemaNode node, String portId, SchemaEdge forEdge) {
        if (portId == null) {
            return null;
        }
        List<com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Pin> matches = new ArrayList<>();
        for (var p : nodeLayout(node).pins()) {
            if (p.port().getId().equals(portId)) {
                matches.add(p);
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        int ordinal = forEdge != null
                ? com.vjstb.ledscheme.service.schemalayout.SchemaUsage.edgeOrdinalForPort(edges(), forEdge, portId)
                : usedCount(portId, null);
        ordinal = Math.max(0, Math.min(ordinal, matches.size() - 1));
        return matches.get(ordinal);
    }

    /** Число связей, сходящихся в ОДНО свёрнутое гнездо узла (docs/schema-ports-
     *  rework/PLAN.md, D8/задача T4.3 {@link
     *  com.vjstb.ledscheme.service.schemalayout.EdgeBundles}) — 0, если гнездо не
     *  свёрнуто (тогда у раскладки несколько отдельных пинов на этот portId, см.
     *  {@link #pinFor}, и сливать нечего — у каждой связи уже свой пин) либо на
     *  него приходится меньше двух связей (пучок из одной не нужен). Найдено при
     *  разборе вопроса пользователя 2026-09-18 (DIALOG.md, реплика 5, T6.2 —
     *  «шина/Ring»): геометрия пучка (T4.3) была готова и покрыта тестами, но
     *  ни разу не вызывалась из отрисовки холста (T4.4 забыл её подключить) —
     *  этот метод и {@link #drawEdgeBundleMarkers} закрывают этот пробел, что и
     *  снимает нужду в отдельном блоке-«шине»: слияние нескольких линий уже
     *  визуально то же самое, что просили под именем "Ring". */
    private int bundleCount(SchemaNode node, String portId) {
        if (classicMode() || portId == null) {
            return 0;
        }
        int pinMatches = 0;
        for (var p : nodeLayout(node).pins()) {
            if (p.port().getId().equals(portId)) {
                pinMatches++;
            }
        }
        if (pinMatches != 1) {
            return 0;
        }
        int count = 0;
        for (SchemaEdge e : edges()) {
            boolean asFrom = node.getId().equals(e.getFromNodeId()) && portId.equals(e.getFromPortId());
            boolean asTo = node.getId().equals(e.getToNodeId()) && portId.equals(e.getToPortId());
            if (asFrom || asTo) {
                count++;
            }
        }
        return count >= 2 ? count : 0;
    }

    /** Рисует короткий общий ствол и подпись "×N" для каждого гнезда узла {@code
     *  node}, в которое сходится 2+ связи (см. {@link #bundleCount}) — один раз на
     *  гнездо, а не на связь (иначе подпись повторялась бы N раз друг на друге).
     *  Индивидуальные маршруты связей при этом не меняются — они и так сходятся в
     *  ту же самую точку (общий пин свёрнутой группы), ствол здесь только
     *  визуально подчёркивает слияние и даёт число, которого в самих линиях нет. */
    private void drawEdgeBundleMarkers(Graphics2D g2, SchemaNode node, List<SchemaEdge> es) {
        if (classicMode()) {
            return;
        }
        Set<String> portIds = new LinkedHashSet<>();
        for (SchemaEdge edge : es) {
            if (node.getId().equals(edge.getFromNodeId()) && edge.getFromPortId() != null) {
                portIds.add(edge.getFromPortId());
            }
            if (node.getId().equals(edge.getToNodeId()) && edge.getToPortId() != null) {
                portIds.add(edge.getToPortId());
            }
        }
        for (String portId : portIds) {
            int count = bundleCount(node, portId);
            if (count < 2) {
                continue;
            }
            var pin = pinFor(node, portId, null);
            if (pin == null) {
                continue;
            }
            double pinX = node.getX() + pin.x();
            double pinY = node.getY() + pin.y();
            // НЕ рисуем сам ствол EdgeBundles.trunk() как отрезок — тот предполагает,
            // что все связи и сами подходят строго по нормали стороны гнезда (как
            // после орто-трассировки). Пока это не так для гнёзд-кабинетов расключения
            // экрана (см. javadoc autoRoutePoints — туда OrthogonalRouter ещё не
            // дотянулся, связи подходят под произвольным углом) — жёстко направленный
            // отрезок создавал бы ложный "залом", не соответствующий ни одной реальной
            // линии (баг-репорт пользователя 2026-09-18, DIALOG.md). Кружок без
            // направления + подпись рядом читаются верно при любом угле подхода.
            var bundle = com.vjstb.ledscheme.service.schemalayout.EdgeBundles.bundleFor(pinX, pinY, pin.side(), count);
            double[] labelAt = bundle.trunk()[0];
            g2.setColor(style.accent);
            int dotD = PIN_DOT_D + 4;
            g2.fillOval((int) Math.round(pinX - dotD / 2.0), (int) Math.round(pinY - dotD / 2.0), dotD, dotD);
            g2.setFont(EDGE_FONT);
            g2.setColor(style.mutedText);
            g2.drawString(bundle.label(), (int) Math.round(labelAt[0]) + 4, (int) Math.round(labelAt[1]) - 3);
        }
    }

    /** Только для тестов — открывает {@link #bundleCount} (T4.3/T4.4 доводка, T6.2). */
    public int bundleSizeForTest(SchemaNode node, String portId) {
        return bundleCount(node, portId);
    }

    /** Гнездо разъёма под точкой клика/курсора — учитывает только реально
     *  отрисованные (видимые) гнёзда, как и {@link #nodeLayout}. Кликабельна
     *  область {@link #SOCKET_HIT_PAD} вокруг самой точки (гнёзда лежат на рамке
     *  блока, не растянуты в строку на всю ширину, как в прежнем горизонтальном
     *  режиме — см. DIALOG.md/PLAN.md). */
    private SocketHit socketAt(Point p) {
        // Развилка MODERN/CLASSIC (docs/schema-ports-rework/PLAN.md, задача T5.5) —
        // см. socketAtClassic в конце файла.
        return classicMode() ? socketAtClassic(p) : socketAtModern(p);
    }

    private SocketHit socketAtModern(Point p) {
        for (SchemaNode n : nodes()) {
            if (!hasPorts(n)) {
                continue;
            }
            int nx = (int) n.getX(), ny = (int) n.getY(), nw = (int) n.getWidth(), nh = (int) n.getHeight();
            if (p.x < nx - SOCKET_HIT_PAD || p.x > nx + nw + SOCKET_HIT_PAD
                    || p.y < ny - SOCKET_HIT_PAD || p.y > ny + nh + SOCKET_HIT_PAD) {
                continue;
            }
            for (var pin : nodeLayout(n).pins()) {
                int cx = (int) Math.round(n.getX() + pin.x());
                int cy = (int) Math.round(n.getY() + pin.y());
                if (Math.abs(p.x - cx) <= SOCKET_HIT_PAD && Math.abs(p.y - cy) <= SOCKET_HIT_PAD) {
                    return new SocketHit(n, pin.port());
                }
            }
        }
        return null;
    }

    /** Только для тестов (docs/schema-ports-rework/PLAN.md, задача T3.2, пункт
     *  приёмки "клик по пину и привязка линии совпадают с нарисованным") — открывает
     *  {@link #socketPosition} и {@link #nodeLayout} пакетному тесту без рефлексии;
     *  сама раскладка/привязка остаются приватными для остального кода. */
    Point socketPositionForTest(SchemaNode node, String portId, SchemaEdge forEdge) {
        return socketPosition(node, portId, forEdge);
    }

    /** Только для тестов — см. {@link #socketPositionForTest}. */
    com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Result nodeLayoutForTest(SchemaNode node) {
        return nodeLayout(node);
    }

    /** Только для тестов (docs/schema-ports-rework/PLAN.md, задача T4.4) — открывает
     *  {@link #routePoints} (переключение AUTO/MANUAL/STRAIGHT, ортогональная
     *  трассировка). */
    List<double[]> routePointsForTest(SchemaEdge edge) {
        return routePoints(edge);
    }

    /** Только для тестов — открывает {@link #materializeAutoRouteIfNeeded} (T4.4:
     *  "перетаскивание излома у AUTO-связи превращает её в MANUAL"). */
    void materializeAutoRouteIfNeededForTest(SchemaEdge edge) {
        materializeAutoRouteIfNeeded(edge);
    }

    /** Только для тестов — открывает {@link #keepOrthogonalWaypointsForNode} (T4.4:
     *  режим «ортогональные связи» при переносе блока). */
    void keepOrthogonalWaypointsForNodeForTest(SchemaNode node) {
        keepOrthogonalWaypointsForNode(node);
    }

    /** Только для тестов — открывает {@link #edgeDefaultColor} (T4.4/D9: разрешение
     *  цвета связи без пользовательского {@code edge.getColor()}). */
    Color edgeDefaultColorForTest(SchemaEdge edge) {
        return edgeDefaultColor(edge);
    }

    /** Только для тестов — открывает {@link #autoDashedForPrintSync} (T6.1). */
    public boolean autoDashedForPrintSyncForTest(SchemaEdge edge) {
        return autoDashedForPrintSync(edge);
    }

    /** Только для тестов — открывает {@link #shouldShowEmptyLabelChip} (T4.5/D14). */
    boolean shouldShowEmptyLabelChipForTest(SchemaEdge edge, boolean selected) {
        return shouldShowEmptyLabelChip(edge, selected);
    }

    /** Только для тестов — имитирует наведение курсора на связь (см. {@link
     *  #hoveredEdge}) без реальных мышиных событий. */
    void setHoveredEdgeForTest(SchemaEdge edge) {
        this.hoveredEdge = edge;
    }

    /** Только для тестов — имитирует состояние "идёт экспорт" (см. {@link
     *  #exporting}) без вызова {@link #renderImage}, который сбрасывает флаг сразу
     *  по завершении отрисовки и не даёт проверить его эффект отдельно. */
    void setExportingForTest(boolean exporting) {
        this.exporting = exporting;
    }

    // ======================================================================
    //  Классический режим отрисовки (docs/schema-ports-rework/PLAN.md, задача
    //  T5.5, решение D16) — дорефакторинговые приватные методы раскладки/
    //  отрисовки/хит-тестинга гнёзд, перенесённые ОДИН В ОДИН из версии
    //  SchemaCanvasPanel на master ДО этого плана (коммит 5fad735, см. DIALOG.md
    //  реплика 4): гнёзда строкой у одного из двух краёв узла (см.
    //  isConnectorsVertical) — не на рамке через NodePortLayout, без ролей,
    //  ориентации, орто-трассировки и перетаскивания групп. Переключается
    //  глобально через UserProfile.schemaRenderMode (см. classicMode() выше) —
    //  ни одна строка MODERN-пути (NodePortLayout/OrthogonalRouter/раздел выше)
    //  этим блоком не затронута. Цвета по-прежнему берутся из общего SchemaStyle
    //  (T3.1) — вынос цветов из литералов в объект стиля ортогонален этой задаче
    //  (это более ранняя переработка), поэтому пресет «Печатный» (§2.7 PLAN.md)
    //  работает и в классическом режиме тоже; проверено, что "Экранный" пресет
    //  задаёт ТЕ ЖЕ цвета, что были здесь захардкожены до T3.1.
    // ======================================================================

    /** Одна карта/группа разъёмов классической раскладки — см. {@code PortEntry}
     *  версии до этого плана. */
    private record PortEntryClassic(CardPort port, String groupName, String cardId) {
    }

    /** Одно гнездо классической раскладки — см. {@code SocketRect} версии до
     *  этого плана. */
    private record SocketRectClassic(PortEntryClassic entry, boolean isIn, int dotX, int dotY, int slotIndex) {
        int centerX() {
            return dotX + CLASSIC_CONNECTOR_DOT_D / 2;
        }

        int centerY() {
            return dotY + CLASSIC_CONNECTOR_DOT_D / 2;
        }
    }

    private static final int CLASSIC_CONNECTOR_ROW_H = 13;
    private static final int CLASSIC_CONNECTOR_DOT_D = 7;
    private static final int CLASSIC_PORT_ROWS_TOP_OFFSET = 38;
    private static final int CLASSIC_CARD_HEADER_H = CLASSIC_CONNECTOR_ROW_H;
    private static final int CLASSIC_CARD_BLOCK_PAD = 3;
    private static final int CLASSIC_CARD_BLOCK_GAP = 6;
    private static final int CLASSIC_CONNECTOR_COL_W = 18;
    private static final int CLASSIC_CARD_HEADER_W = CLASSIC_CONNECTOR_COL_W;
    private static final int CLASSIC_SOCKET_EDGE_MARGIN = 6;
    private static final int CLASSIC_SOCKET_ROW_HIT_PAD = 3;

    private static List<PortEntryClassic> flattenCardPortsClassic(List<SchemaCard> cards) {
        List<PortEntryClassic> all = new ArrayList<>();
        for (SchemaCard c : cards) {
            for (CardPort p : c.getPorts()) {
                all.add(new PortEntryClassic(p, c.getName(), c.getId()));
            }
        }
        return all;
    }

    /** Список гнёзд узла в классической раскладке — см. {@code portsOf} версии
     *  до этого плана. */
    private static List<PortEntryClassic> portsOfClassic(SchemaNode n) {
        if (!n.getCards().isEmpty()) {
            return flattenCardPortsClassic(n.getCards());
        }
        if (n.getMode() == SchemaMode.POWER && !n.getPowerConnectors().isEmpty()) {
            List<PortEntryClassic> all = new ArrayList<>();
            for (CardPort p : n.getPowerConnectors()) {
                all.add(new PortEntryClassic(p, null, null));
            }
            return all;
        }
        return List.of();
    }

    /** Геометрия гнёзд разъёмов узла в классической раскладке — см. {@code
     *  computeSocketRects} версии до этого плана. */
    private List<SocketRectClassic> computeSocketRectsClassic(List<PortEntryClassic> ports, int x, int y, int w, int h) {
        boolean individual = settings.activeProfile().getConnectorDisplayMode(mode) == ConnectorDisplayMode.INDIVIDUAL;
        boolean vertical = settings.activeProfile().isConnectorsVertical(mode);
        List<SocketRectClassic> rects = new ArrayList<>();
        String prevCardId = null;
        boolean started = false;
        int along = (vertical ? x : y) + CLASSIC_PORT_ROWS_TOP_OFFSET;
        int alongMax = (vertical ? x + w : y + h) - 4;
        int alongStep = vertical ? CLASSIC_CONNECTOR_COL_W : CLASSIC_CONNECTOR_ROW_H;
        int headerStep = vertical ? CLASSIC_CARD_HEADER_W : CLASSIC_CARD_HEADER_H;
        int acrossNear = vertical ? y + CLASSIC_PORT_ROWS_TOP_OFFSET : x;
        int acrossFar = vertical ? y + h - 4 : x + w;
        outer:
        for (PortEntryClassic entry : ports) {
            String cardId = entry.cardId();
            if (!started || !Objects.equals(cardId, prevCardId)) {
                if (started) {
                    along += CLASSIC_CARD_BLOCK_GAP;
                }
                if (cardId != null) {
                    along += headerStep;
                }
                prevCardId = cardId;
                started = true;
            }
            PortDirection dir = entry.port().getDirection();
            int slots = individual && dir != PortDirection.IN_OUT ? Math.max(1, entry.port().getCount()) : 1;
            for (int slot = 0; slot < slots; slot++) {
                if (along > alongMax) {
                    break outer;
                }
                if (vertical) {
                    int dotX = along;
                    if (dir == PortDirection.IN_OUT) {
                        rects.add(new SocketRectClassic(entry, true, dotX, acrossNear + CLASSIC_SOCKET_EDGE_MARGIN, slot));
                        rects.add(new SocketRectClassic(entry, false, dotX,
                                acrossFar - CLASSIC_CONNECTOR_DOT_D - CLASSIC_SOCKET_EDGE_MARGIN, slot));
                    } else {
                        boolean isIn = dir == PortDirection.IN;
                        int dotY = isIn ? acrossNear + CLASSIC_SOCKET_EDGE_MARGIN
                                : acrossFar - CLASSIC_CONNECTOR_DOT_D - CLASSIC_SOCKET_EDGE_MARGIN;
                        rects.add(new SocketRectClassic(entry, isIn, dotX, dotY, slot));
                    }
                } else {
                    int dotY = along - CLASSIC_CONNECTOR_DOT_D;
                    if (dir == PortDirection.IN_OUT) {
                        rects.add(new SocketRectClassic(entry, true, acrossNear + CLASSIC_SOCKET_EDGE_MARGIN, dotY, slot));
                        rects.add(new SocketRectClassic(entry, false,
                                acrossFar - CLASSIC_CONNECTOR_DOT_D - CLASSIC_SOCKET_EDGE_MARGIN, dotY, slot));
                    } else {
                        boolean isIn = dir == PortDirection.IN;
                        int dotX = isIn ? acrossNear + CLASSIC_SOCKET_EDGE_MARGIN
                                : acrossFar - CLASSIC_CONNECTOR_DOT_D - CLASSIC_SOCKET_EDGE_MARGIN;
                        rects.add(new SocketRectClassic(entry, isIn, dotX, dotY, slot));
                    }
                }
                along += alongStep;
            }
        }
        return rects;
    }

    /** Рамки-блоки карт — см. {@code drawCardBlockBorders} версии до этого плана. */
    private void drawCardBlockBordersClassic(Graphics2D g2, List<SocketRectClassic> rects, int x, int y, int w, int h) {
        boolean vertical = settings.activeProfile().isConnectorsVertical(mode);
        int acrossNear = vertical ? y + CLASSIC_PORT_ROWS_TOP_OFFSET : x;
        int acrossFar = vertical ? y + h - 4 : x + w;
        String curCardId = null;
        String curCardName = null;
        int blockStart = -1;
        int blockEnd = -1;
        boolean open = false;
        for (SocketRectClassic r : rects) {
            String cardId = r.entry().cardId();
            int alongPos = vertical ? r.dotX() : r.dotY();
            if (!open || !Objects.equals(cardId, curCardId)) {
                if (open && curCardId != null) {
                    paintCardBlockBorderClassic(g2, vertical, acrossNear, acrossFar, blockStart, blockEnd);
                }
                curCardId = cardId;
                curCardName = r.entry().groupName();
                int headerAlong = alongPos - (vertical ? CLASSIC_CARD_HEADER_W : CLASSIC_CARD_HEADER_H);
                blockStart = headerAlong - CLASSIC_CARD_BLOCK_PAD;
                blockEnd = alongPos + CLASSIC_CONNECTOR_DOT_D + CLASSIC_CARD_BLOCK_PAD;
                open = true;
                if (cardId != null) {
                    paintCardBlockHeaderClassic(g2, vertical, acrossNear, acrossFar, headerAlong, curCardName);
                }
            } else {
                blockEnd = Math.max(blockEnd, alongPos + CLASSIC_CONNECTOR_DOT_D + CLASSIC_CARD_BLOCK_PAD);
            }
        }
        if (open && curCardId != null) {
            paintCardBlockBorderClassic(g2, vertical, acrossNear, acrossFar, blockStart, blockEnd);
        }
    }

    private void paintCardBlockBorderClassic(Graphics2D g2, boolean vertical, int acrossNear, int acrossFar,
                                              int alongStart, int alongEnd) {
        g2.setColor(style.cardBlockBorder);
        g2.setStroke(new BasicStroke(1f));
        if (vertical) {
            g2.drawRoundRect(alongStart, acrossNear + 3, alongEnd - alongStart, acrossFar - acrossNear - 6, 6, 6);
        } else {
            g2.drawRoundRect(acrossNear + 3, alongStart, acrossFar - acrossNear - 6, alongEnd - alongStart, 6, 6);
        }
    }

    private void paintCardBlockHeaderClassic(Graphics2D g2, boolean vertical, int acrossNear, int acrossFar,
                                              int headerAlong, String cardName) {
        if (cardName == null || cardName.isEmpty()) {
            return;
        }
        g2.setColor(style.cardBlockHeaderText);
        if (vertical) {
            int maxLen = (acrossFar - acrossNear) - 12;
            drawVerticalLabelCenteredClassic(g2, cardName, headerAlong + CLASSIC_CONNECTOR_DOT_D / 2,
                    (acrossNear + acrossFar) / 2, maxLen);
        } else {
            String clipped = clipToWidth(g2, cardName, (acrossFar - acrossNear) - 16);
            g2.drawString(clipped, acrossNear + 8, headerAlong + CLASSIC_CONNECTOR_DOT_D);
        }
    }

    private void drawVerticalLabelGrowUpClassic(Graphics2D g2, String text, int centerX, int bottomY, int maxLen) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String clipped = clipToWidth(g2, text, Math.max(0, maxLen));
        FontMetrics fm = g2.getFontMetrics();
        Graphics2D g2r = (Graphics2D) g2.create();
        g2r.translate(centerX + fm.getAscent() / 2.0, bottomY);
        g2r.rotate(-Math.PI / 2);
        g2r.drawString(clipped, 0, 0);
        g2r.dispose();
    }

    private void drawVerticalLabelGrowDownClassic(Graphics2D g2, String text, int centerX, int topY, int maxLen) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String clipped = clipToWidth(g2, text, Math.max(0, maxLen));
        FontMetrics fm = g2.getFontMetrics();
        Graphics2D g2r = (Graphics2D) g2.create();
        g2r.translate(centerX - fm.getAscent() / 2.0, topY);
        g2r.rotate(Math.PI / 2);
        g2r.drawString(clipped, 0, 0);
        g2r.dispose();
    }

    private void drawVerticalLabelCenteredClassic(Graphics2D g2, String text, int centerX, int centerY, int maxLen) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String clipped = clipToWidth(g2, text, Math.max(0, maxLen));
        int textLen = g2.getFontMetrics().stringWidth(clipped);
        drawVerticalLabelGrowUpClassic(g2, clipped, centerX, centerY + textLen / 2, maxLen);
    }

    /** Разъёмы узла построчно в классической раскладке — см. {@code
     *  drawConnectorRows} версии до этого плана. */
    private void drawConnectorRowsClassic(Graphics2D g2, SchemaNode node, List<PortEntryClassic> ports,
                                           int x, int y, int w, int h) {
        boolean vertical = settings.activeProfile().isConnectorsVertical(mode);
        List<SocketRectClassic> rects = computeSocketRectsClassic(ports, x, y, w, h);
        drawCardBlockBordersClassic(g2, rects, x, y, w, h);
        int acrossNear = vertical ? y + CLASSIC_PORT_ROWS_TOP_OFFSET : x;
        int acrossFar = vertical ? y + h - 4 : x + w;
        for (SocketRectClassic r : rects) {
            CardPort port = r.entry().port();
            boolean hovered = hoveredSocket != null && hoveredSocket.node() == node && hoveredSocket.port() == port;
            g2.setColor(connectorColor(port.getConnectorType()));
            g2.fillOval(r.dotX(), r.dotY(), CLASSIC_CONNECTOR_DOT_D, CLASSIC_CONNECTOR_DOT_D);
            g2.setColor(hovered ? style.socketRingHovered : style.socketRingDefault);
            g2.setStroke(new BasicStroke(hovered ? 2f : 1f));
            int ring = hovered ? 2 : 0;
            g2.drawOval(r.dotX() - ring, r.dotY() - ring,
                    CLASSIC_CONNECTOR_DOT_D + ring * 2, CLASSIC_CONNECTOR_DOT_D + ring * 2);

            if (vertical && !r.isIn() && port.getDirection() == PortDirection.IN_OUT) {
                continue;
            }

            boolean expandedRow = settings.activeProfile().getConnectorDisplayMode(mode) == ConnectorDisplayMode.INDIVIDUAL
                    && port.getCount() > 1 && port.getDirection() != PortDirection.IN_OUT;
            String label = expandedRow ? port.getConnectorType() + " #" + (r.slotIndex() + 1)
                    : port.getCount() + "×" + port.getConnectorType();
            g2.setColor(style.socketLabelText);
            if (vertical) {
                int maxLen = (acrossFar - acrossNear) / 2 - CLASSIC_CONNECTOR_DOT_D - 8;
                int labelCenterX = r.dotX() + CLASSIC_CONNECTOR_DOT_D / 2;
                if (r.isIn()) {
                    drawVerticalLabelGrowDownClassic(g2, label, labelCenterX, r.dotY() + CLASSIC_CONNECTOR_DOT_D + 4, maxLen);
                } else {
                    drawVerticalLabelGrowUpClassic(g2, label, labelCenterX, r.dotY() - 4, maxLen);
                }
            } else {
                int maxTextW = w - CLASSIC_CONNECTOR_DOT_D - 16;
                String clipped = clipToWidth(g2, label, maxTextW);
                FontMetrics fm = g2.getFontMetrics();
                int textX = r.isIn() ? r.dotX() + CLASSIC_CONNECTOR_DOT_D + 4 : r.dotX() - 4 - fm.stringWidth(clipped);
                g2.drawString(clipped, textX, r.dotY() + CLASSIC_CONNECTOR_DOT_D);
            }
        }
        long renderedEntries = rects.stream().map(SocketRectClassic::entry).distinct().count();
        if (renderedEntries < ports.size()) {
            int remaining = ports.size() - (int) renderedEntries;
            g2.setColor(style.cardBlockHeaderText);
            if (vertical) {
                int hintX = rects.isEmpty() ? x + 4 : rects.get(rects.size() - 1).dotX() + CLASSIC_CONNECTOR_COL_W;
                int hintY = acrossNear + (acrossFar - acrossNear) / 2;
                g2.drawString("+" + remaining, hintX, hintY);
            } else {
                int hintY = rects.isEmpty() ? y + CLASSIC_PORT_ROWS_TOP_OFFSET
                        : rects.get(rects.size() - 1).dotY() + CLASSIC_CONNECTOR_DOT_D + CLASSIC_CONNECTOR_ROW_H;
                g2.drawString("+" + remaining + " ещё…", x + 10, hintY);
            }
        }
    }

    /** Экранные координаты гнезда в классической раскладке — см. {@code
     *  socketPosition} версии до этого плана. */
    private Point socketPositionClassic(SchemaNode node, String portId, SchemaEdge forEdge) {
        if (portId == null) {
            return null;
        }
        List<SocketRectClassic> rects = computeSocketRectsClassic(portsOfClassic(node), (int) node.getX(), (int) node.getY(),
                (int) node.getWidth(), (int) node.getHeight());
        List<SocketRectClassic> matches = new ArrayList<>();
        for (SocketRectClassic r : rects) {
            if (r.entry().port().getId().equals(portId)) {
                matches.add(r);
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() == 1 || matches.get(0).entry().port().getDirection() == PortDirection.IN_OUT) {
            SocketRectClassic r = matches.get(0);
            return new Point(r.centerX(), r.centerY());
        }
        int ordinal = forEdge != null
                ? com.vjstb.ledscheme.service.schemalayout.SchemaUsage.edgeOrdinalForPort(edges(), forEdge, portId)
                : usedCount(portId, null);
        ordinal = Math.max(0, Math.min(ordinal, matches.size() - 1));
        SocketRectClassic r = matches.get(ordinal);
        return new Point(r.centerX(), r.centerY());
    }

    /** Гнездо разъёма под точкой клика/курсора в классической раскладке — см.
     *  {@code socketAt} версии до этого плана. */
    private SocketHit socketAtClassic(Point p) {
        boolean vertical = settings.activeProfile().isConnectorsVertical(mode);
        for (SchemaNode n : nodes()) {
            List<PortEntryClassic> ports = portsOfClassic(n);
            if (ports.isEmpty()) {
                continue;
            }
            int nx = (int) n.getX(), ny = (int) n.getY(), nw = (int) n.getWidth(), nh = (int) n.getHeight();
            if (vertical) {
                if (p.y < ny || p.y > ny + nh) {
                    continue;
                }
            } else {
                if (p.x < nx || p.x > nx + nw) {
                    continue;
                }
            }
            List<SocketRectClassic> rects = computeSocketRectsClassic(ports, nx, ny, nw, nh);
            for (SocketRectClassic r : rects) {
                boolean hit;
                if (vertical) {
                    int colLeft = r.dotX() - CLASSIC_SOCKET_ROW_HIT_PAD;
                    int colRight = r.dotX() + CLASSIC_CONNECTOR_DOT_D + CLASSIC_SOCKET_ROW_HIT_PAD;
                    hit = p.x >= colLeft && p.x <= colRight;
                } else {
                    int rowTop = r.dotY() - CLASSIC_SOCKET_ROW_HIT_PAD;
                    int rowBottom = r.dotY() + CLASSIC_CONNECTOR_DOT_D + CLASSIC_SOCKET_ROW_HIT_PAD;
                    hit = p.y >= rowTop && p.y <= rowBottom;
                }
                if (hit) {
                    return new SocketHit(n, r.entry().port());
                }
            }
        }
        return null;
    }

    /** Классический рендер (docs/schema-ports-rework/PLAN.md, задача T5.5) — см.
     *  javadoc раздела выше. Отличия от MODERN {@link #paint}, помимо самой
     *  раскладки гнёзд, воспроизводят поведение "как было до этого плана":
     *  заголовок узла центрируется только при включённой вертикальной раскладке
     *  (правило T3.2 "название под верхней строкой гнёзд" сюда не относится);
     *  чип подписи связи всегда виден (D14/T4.5 ещё не существовало); стрелка —
     *  на каждом отрезке маршрута (D15/T4.5 ещё не существовало); цвет связи по
     *  умолчанию — фиксированный {@link SchemaStyle#defaultEdgeColor}, без
     *  разрешения по роли гнезда (D9/T4.4 — {@link #edgeDefaultColor}); маршрут
     *  связи никогда не считается через {@code OrthogonalRouter} (см. ветку
     *  classicMode() в {@link #routePoints}). */
    private void paintClassic(Graphics2D g2, int width, int height, boolean renderScreenWiring) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(style.background);
        g2.fillRect(0, 0, width, height);

        List<SchemaNode> ns = nodes();
        List<SchemaEdge> es = edges();

        if (ns.isEmpty() && es.isEmpty()) {
            g2.setColor(style.mutedText);
            g2.setFont(getFont().deriveFont(14f));
            String msg = "Схема пока пуста. Добавьте узлы оборудования справа.";
            g2.drawString(msg, MARGIN, MARGIN + 20);
            g2.dispose();
            return;
        }

        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setFont(EDGE_FONT);
        java.awt.FontMetrics edgeFm = g2.getFontMetrics();
        Map<SchemaEdge, List<double[]>> routeCache = new IdentityHashMap<>();
        for (SchemaEdge edge : es) {
            routeCache.put(edge, routePoints(edge));
        }
        WireHopStyle hopStyle = settings.activeProfile().getSchemaWireHopStyle();
        Map<SchemaEdge, List<double[]>> hopMap = hopStyle != WireHopStyle.NONE
                ? computeWireHops(es, routeCache) : null;
        WireHopGeometry.ArcShape arcShape = hopStyle == WireHopStyle.TRUNCATED
                ? WireHopGeometry.ArcShape.FLAT_TOP : WireHopGeometry.ArcShape.CUBIC;
        for (SchemaEdge edge : es) {
            List<double[]> pts = routeCache.get(edge);
            if (pts == null) {
                continue;
            }
            // Подсветка — ЛЮБАЯ связь из многовыделения, точки излома (ниже) — только
            // у "главной" {@link #selectedEdge} (см. симметричный комментарий в MODERN-
            // пути выше, доводка T4.4, баг-репорт пользователя 2026-09-18).
            boolean selected = selectedEdges.contains(edge);
            Color customColor = edge.getColor() != null ? new Color(edge.getColor()) : null;
            g2.setColor(selected ? style.accent : customColor != null ? customColor : style.defaultEdgeColor);
            float strokeWidth = selected ? 3f : 2f;
            g2.setStroke(edge.isDashed()
                    ? new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{7, 5}, 0)
                    : new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            List<double[]> hops = hopMap == null ? null : hopMap.get(edge);
            List<WireHopGeometry.HopSpan> hopSpans = hops == null || hops.isEmpty()
                    ? List.of()
                    : WireHopGeometry.hopSpans(pts, hops, HOP_RADIUS);
            if (!hopSpans.isEmpty()) {
                g2.draw(WireHopGeometry.hoppedPathFromSpans(pts, hopSpans, HOP_RADIUS, arcShape));
            } else {
                for (int i = 0; i < pts.size() - 1; i++) {
                    g2.drawLine((int) Math.round(pts.get(i)[0]), (int) Math.round(pts.get(i)[1]),
                            (int) Math.round(pts.get(i + 1)[0]), (int) Math.round(pts.get(i + 1)[1]));
                }
            }
            // Стрелка — на КАЖДОМ отрезке (настройки T4.5/D15 "у приёмника /
            // на каждом отрезке" до этого плана не было — старое поведение и
            // есть нынешняя настройка "на каждом отрезке").
            WireHopGeometry.RenderRoute rr = WireHopGeometry.renderPoints(pts, hopSpans);
            List<double[]> rpts = rr.points();
            boolean[] onArc = rr.arcSegment();
            for (int i = 0; i < rpts.size() - 1; i++) {
                if (onArc[i]) {
                    continue;
                }
                drawArrow(g2, rpts.get(i)[0], rpts.get(i)[1], rpts.get(i + 1)[0], rpts.get(i + 1)[1]);
            }
            if (edge == selectedEdge) {
                for (int i = 1; i < pts.size() - 1; i++) {
                    int wx = (int) pts.get(i)[0], wy = (int) pts.get(i)[1];
                    g2.setColor(Color.WHITE);
                    g2.fillOval(wx - 4, wy - 4, 8, 8);
                    g2.setColor(style.accent);
                    g2.drawOval(wx - 4, wy - 4, 8, 8);
                }
            }
            g2.setStroke(new BasicStroke(strokeWidth));

            // Чип подписи — ВСЕГДА виден (D14/T4.5 "только при наведении/
            // выделении" до этого плана не было).
            java.awt.Rectangle chip = labelChipBounds(edge);
            if (chip != null) {
                String display = edge.displayLabel();
                boolean hasLabel = display != null && !display.isEmpty();
                g2.setColor(selected ? style.accent : (hasLabel ? style.labelChipBackground : style.labelChipBackgroundEmpty));
                g2.fillRoundRect(chip.x, chip.y, chip.width, chip.height, 8, 8);
                g2.setColor(selected ? style.selectedOutline : style.labelChipBorder);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(chip.x, chip.y, chip.width, chip.height, 8, 8);
                g2.setColor(hasLabel || selected ? style.labelChipText : style.labelChipTextEmpty);
                String text = hasLabel ? display : "+ подпись";
                g2.drawString(text, chip.x + 7, chip.y + chip.height - edgeFm.getDescent() - 2);
                g2.setFont(EDGE_FONT);
            }
        }

        if (interaction == Interaction.CONNECT && connectPendingId != null && lastMouse != null) {
            SchemaNode pending = nodeById(connectPendingId);
            if (pending != null) {
                Point socket = connectPendingCabinetInstanceId != null
                        ? cabinetSocketPosition(pending, connectPendingCabinetInstanceId)
                        : socketPosition(pending, connectPendingPortId, null);
                int px, py;
                if (socket != null) {
                    px = socket.x;
                    py = socket.y;
                } else {
                    double[] center = {pending.getX() + pending.getWidth() / 2.0, pending.getY() + pending.getHeight() / 2.0};
                    double[] clipped = clipToBorder(pending, center, new double[]{lastMouse.x, lastMouse.y});
                    px = (int) clipped[0];
                    py = (int) clipped[1];
                }
                g2.setColor(style.accent);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0,
                        new float[]{5, 4}, 0));
                g2.drawLine(px, py, lastMouse.x, lastMouse.y);
            }
        }

        Font titleFont = getFont().deriveFont(Font.BOLD, 12f);
        Font metaFont = getFont().deriveFont(10f);
        boolean verticalConnectors = settings.activeProfile().isConnectorsVertical(mode);
        overloadIconRects.clear();
        for (SchemaNode n : ns) {
            boolean selected = selectedNodes.contains(n);
            boolean pending = n.getId().equals(connectPendingId);
            int nw = (int) n.getWidth(), nh = (int) n.getHeight();
            Color fill = style.nodeFill(n.getType());
            g2.setColor(fill);
            g2.fillRoundRect((int) n.getX(), (int) n.getY(), nw, nh, 10, 10);
            g2.setColor(pending ? style.pendingOutline : (selected ? style.selectedOutline : style.nodeBorder));
            g2.setStroke(new BasicStroke(selected || pending ? 2.5f : 1.4f));
            g2.drawRoundRect((int) n.getX(), (int) n.getY(), nw, nh, 10, 10);

            boolean overloaded = false;
            if (mode == SchemaMode.POWER && n.getType() != SchemaNodeType.SCREEN
                    && settings.activeProfile().isLoadTrackingEnabled()) {
                Scene loadScene = model.getCurrentScene();
                if (loadScene != null) {
                    overloaded = com.vjstb.ledscheme.service.SchemaLoadCalc.evaluate(n, loadScene, model).overloaded();
                }
            }
            if (overloaded) {
                g2.setColor(style.warn);
                g2.setStroke(new BasicStroke(3f));
                g2.drawRoundRect((int) n.getX() - 1, (int) n.getY() - 1, nw + 2, nh + 2, 12, 12);
            }

            String title = n.getType() == SchemaNodeType.SCREEN ? resolveScreenLabel(n) : n.getLabel();
            if (title == null || title.isEmpty()) {
                title = model.categoryLabel(n.getType());
            }
            title = withControllerLegendTag(n, title);
            g2.setColor(style.titleText);
            g2.setFont(titleFont);
            // Название по центру блока — ТОЛЬКО когда включена вертикальная
            // раскладка гнёзд (как до этого плана — см. javadoc paintClassic).
            boolean centerTitle = verticalConnectors && n.getType() != SchemaNodeType.SCREEN
                    && !portsOfClassic(n).isEmpty();
            if (centerTitle) {
                String clippedTitle = clipToWidth(g2, title, nw - 16);
                int titleW = g2.getFontMetrics().stringWidth(clippedTitle);
                int titleX = (int) n.getX() + (nw - titleW) / 2;
                int titleY = (int) n.getY() + nh / 2 + g2.getFontMetrics().getAscent() / 2 - 2;
                g2.drawString(clippedTitle, titleX, titleY);
            } else {
                drawClipped(g2, title, (int) n.getX() + 8, (int) n.getY() + 20, nw - 16);
            }
            g2.setFont(metaFont);
            g2.setColor(style.metaText);
            if (n.getType() == SchemaNodeType.SCREEN) {
                if (renderScreenWiring) {
                    drawScreenWiringThumbnail(g2, n, nw, nh);
                } else {
                    drawClipped(g2, screenMeta(n), (int) n.getX() + 8, (int) n.getY() + 38, nw - 16);
                }
            } else if (n.isAutoPortLegend()) {
                drawPortLegendContent(g2, n, nw, nh);
            } else if (n.isAutoLineLegend()) {
                // Легенда линий (T5.4) — самостоятельная сущность холста, отдельная
                // от старого/нового способа раскладки гнёзд как такового (не
                // обращается к NodePortLayout) — по решению из отчёта T5.5 оставлена
                // работающей в обоих режимах.
                drawLineLegendContent(g2, n, nw, nh);
            } else if (!portsOfClassic(n).isEmpty()) {
                drawConnectorRowsClassic(g2, n, portsOfClassic(n), (int) n.getX(), (int) n.getY(), nw, nh);
            } else {
                drawClipped(g2, model.categoryLabel(n.getType()), (int) n.getX() + 8, (int) n.getY() + 38, nw - 16);
            }

            if (overloaded) {
                g2.setColor(style.warn);
                g2.setFont(titleFont);
                int iconX = (int) n.getX() + nw - 20;
                int iconY = (int) n.getY() + 16;
                g2.drawString("⚠", iconX, iconY);
                overloadIconRects.put(n, new java.awt.Rectangle(iconX - 2, iconY - 14, 20, 18));
            }

            if (selected) {
                int hx = (int) n.getX() + nw, hy = (int) n.getY() + nh;
                int[] xs = {hx - RESIZE_HANDLE, hx, hx};
                int[] ys = {hy, hy - RESIZE_HANDLE, hy};
                g2.setColor(style.resizeHandle);
                g2.fillPolygon(xs, ys, 3);
            }
        }

        g2.dispose();
    }

    /** Только для тестов (docs/schema-ports-rework/PLAN.md, задача T5.5) —
     *  открывает {@link #socketPositionClassic}. */
    Point socketPositionClassicForTest(SchemaNode node, String portId, SchemaEdge forEdge) {
        return socketPositionClassic(node, portId, forEdge);
    }

    /** Только для тестов — открывает {@link #socketAtClassic}, но возвращает
     *  найденный {@link CardPort} (публичный тип модели), а не приватный {@code
     *  SocketHit} — он недоступен по имени вызывающему тесту в другом файле
     *  того же пакета. {@code null}, если под точкой ничего не нашлось. */
    CardPort socketAtClassicPortForTest(Point p) {
        SocketHit hit = socketAtClassic(p);
        return hit == null ? null : hit.port();
    }

    /** Только для тестов — узел, которому принадлежит гнездо под точкой в
     *  классической раскладке (см. {@link #socketAtClassicPortForTest}). */
    SchemaNode socketAtClassicNodeForTest(Point p) {
        SocketHit hit = socketAtClassic(p);
        return hit == null ? null : hit.node();
    }

    private static String clipToWidth(Graphics2D g2, String text, int maxWidth) {
        FontMetrics fm = g2.getFontMetrics();
        String s = text;
        if (fm.stringWidth(s) > maxWidth) {
            while (s.length() > 1 && fm.stringWidth(s + "…") > maxWidth) {
                s = s.substring(0, s.length() - 1);
            }
            s = s + "…";
        }
        return s;
    }

    private static void drawClipped(Graphics2D g2, String text, int x, int y, int maxWidth) {
        g2.drawString(clipToWidth(g2, text, maxWidth), x, y);
    }

    /** Индексы сегментов расширенной ломаной {@code [i, i+1)}, на которых нужно
     *  нарисовать стрелку направления (docs/schema-ports-rework/PLAN.md, задача
     *  T4.5, D15) — {@link ArrowPlacement#SEGMENTS}: все, кроме лежащих под дугой
     *  мостика; {@link ArrowPlacement#TARGET} (по умолчанию): ровно один — ПОСЛЕДНИЙ
     *  (ближайший к приёмнику) НЕ-дуговой сегмент, если такой есть. Чистая функция —
     *  без Graphics2D, чтобы тест мог проверить набор индексов напрямую (флагом
     *  отрисовки, не OCR по итоговой картинке, см. критерий приёмки T4.5). */
    private static List<Integer> arrowSegmentIndices(int pointCount, boolean[] onArc, ArrowPlacement placement) {
        List<Integer> indices = new ArrayList<>();
        if (placement == ArrowPlacement.SEGMENTS) {
            for (int i = 0; i < pointCount - 1; i++) {
                if (!onArc[i]) {
                    indices.add(i);
                }
            }
        } else {
            for (int i = pointCount - 2; i >= 0; i--) {
                if (!onArc[i]) {
                    indices.add(i);
                    break;
                }
            }
        }
        return indices;
    }

    /** Только для тестов — см. {@link #arrowSegmentIndices}. */
    static List<Integer> arrowSegmentIndicesForTest(int pointCount, boolean[] onArc, ArrowPlacement placement) {
        return arrowSegmentIndices(pointCount, onArc, placement);
    }

    private static void drawArrow(Graphics2D g2, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double len = Math.hypot(dx, dy);
        // Стрелка рисуется на каждом сегменте маршрута; на совсем коротком стубе
        // (между близким изломом и гнездом) треугольник был бы длиннее самого
        // сегмента — такие пропускаем.
        if (len < 14) {
            return;
        }
        double ux = dx / len, uy = dy / len;
        double tipX = ax + ux * (len / 2 + 6), tipY = ay + uy * (len / 2 + 6);
        double backX = ax + ux * (len / 2 - 6), backY = ay + uy * (len / 2 - 6);
        double leftX = backX - uy * 5, leftY = backY + ux * 5;
        double rightX = backX + uy * 5, rightY = backY - ux * 5;
        int[] xs = {(int) tipX, (int) leftX, (int) rightX};
        int[] ys = {(int) tipY, (int) leftY, (int) rightY};
        g2.fillPolygon(xs, ys, 3);
    }

    /** Цвет заливки по типу узла — для меню "Изменить тип" (пункт контекстного
     *  меню, не сама схема): всегда "Экранный" пресет, независимо от активного
     *  оформления схемы ({@link SchemaStyle#print()}) — это чисто UI-переключатель
     *  типа, а не элемент самой схемы (docs/schema-ports-rework/PLAN.md, задача T3.1). */
    private static Color nodeColor(SchemaNodeType type) {
        return SchemaStyle.screen().nodeFill(type);
    }
}
