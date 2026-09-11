package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EdgeWaypoint;
import com.vjstb.ledscheme.model.PortDirection;
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
import com.vjstb.ledscheme.settings.ConnectorDisplayMode;
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
    private Point lastMouse;

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

    /** Разъём + название карты, из которой он взят, + id САМОЙ карты (для сигнальных
     *  cards — у power-разъёмов группировки по картам нет, тогда groupName/cardId ==
     *  null) — чтобы на схеме различать одинаковые по типу разъёмы из разных карт.
     *  cardId нужен ОТДЕЛЬНО от groupName: у нескольких экземпляров одного и того же
     *  шаблона карты (см. Task #59) название совпадает, но это РАЗНЫЕ карты — группировка
     *  «показывать имя только у первой строки группы» (Task #67) должна ориентироваться
     *  на конкретный экземпляр карты, а не на совпадение строки названия, иначе второй
     *  экземпляр карты с тем же именем остаётся вовсе без подписи. */
    private record PortEntry(CardPort port, String groupName, String cardId) { }

    /** Одно гнездо разъёма конкретного узла — попадание клика/наведения мыши. */
    private record SocketHit(SchemaNode node, CardPort port) { }

    /** Экранные координаты гнезда: центр точки-разъёма, вычисленные той же
     *  геометрией, что и отрисовка (см. {@link #computeSocketRects}) — используется
     *  и для хит-теста клика, и для привязки конца линии связи к гнезду. slotIndex —
     *  порядковый номер строки СРЕДИ РАЗВЁРНУТЫХ строк одной группы в режиме
     *  INDIVIDUAL (0 в режиме GROUPED/для групп с count==1/для IN_OUT) — только для
     *  подписи «Тип #N» на каждой отдельной строке, не участвует в хит-тесте. */
    private record SocketRect(PortEntry entry, boolean isIn, int dotX, int dotY, int slotIndex) {
        int centerX() {
            return dotX + CONNECTOR_DOT_D / 2;
        }

        int centerY() {
            return dotY + CONNECTOR_DOT_D / 2;
        }
    }

    public SchemaCanvasPanel(AppModel model, SchemaMode mode, com.vjstb.ledscheme.settings.SettingsManager settings) {
        this.model = model;
        this.mode = mode;
        this.settings = settings;
        setBackground(Palette.BG);
        setFocusable(true);
        // Переключатель "экран блоком/схемой" в Персонализации должен сразу
        // отразиться на уже открытой схеме, не только при следующем открытии панели.
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
                        selectedEdge = chipHitConnect;
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
                                        cabinetHit.cabinetInstanceId(), null);
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
                            CardPort fromPort = findPort(connectPendingId, connectPendingPortId);
                            CardPort toPort = findPort(socketHit.node().getId(), socketHit.port().getId());
                            String capError = capacityError(fromPort, connectPendingPortId,
                                    toPort, socketHit.port().getId());
                            String dirError = capError == null ? directionError(fromPort, toPort) : null;
                            if (capError != null || dirError != null) {
                                JOptionPane.showMessageDialog(SchemaCanvasPanel.this, capError != null ? capError : dirError,
                                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                            } else {
                                try {
                                    model.addSchemaEdge(mode, connectPendingId, connectPendingPortId,
                                            connectPendingCabinetInstanceId, socketHit.node().getId(),
                                            socketHit.port().getId(), null, null);
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
                                    connectPendingCabinetInstanceId, hit.getId(), null, null, null);
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
                SchemaNode resizeHit = resizeHandleAt(mp);
                if (resizeHit != null) {
                    // Хват за уголок ВСЕГДА сужает выделение до одного узла — resize
                    // осмыслен только для одного блока за раз (см. isAutoSizedScreenWiringNode
                    // и сам resize-drag ниже, которые оперируют ровно одним resizeNode).
                    selectedNodes.clear();
                    selectedNodes.add(resizeHit);
                    selectedEdge = null;
                    resizeNode = resizeHit;
                    repaint();
                    return;
                }
                WaypointHit wpHit = waypointAt(mp);
                if (wpHit != null) {
                    selectedEdge = wpHit.edge();
                    selectedNodes.clear();
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
                        selectedEdge = null;
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
                    selectedEdge = null;
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
                    selectedEdge = chipHit;
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
                    selectedEdge = segHit.edge();
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
                // Пусто (не узел, не гнездо, не чип подписи, не отрезок) — Shift/Ctrl
                // добавляет к текущему выделению по завершении протяжки (см.
                // mouseReleased), иначе выделение сбрасывается сразу (клик без
                // движения = просто снять выделение, протяжка ниже — прямоугольник-
                // «резинка», баг-репорт про выделение нескольких блоков).
                if (!(e.isShiftDown() || e.isControlDown())) {
                    selectedNodes.clear();
                }
                selectedEdge = edgeAt(mp);
                if (selectedEdge == null) {
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
        selectedEdge = null;
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

    /** Удаляет ВСЁ текущее выделение (узлы и/или связь) — при нескольких
     *  выделенных узлах ОДНИМ действием отмены (см. {@code AppModel
     *  .deleteSchemaNodes}), не по одному, иначе Ctrl+Z вернул бы только
     *  последний удалённый узел. */
    public void deleteSelected() {
        if (selectedNodes.size() > 1) {
            model.deleteSchemaNodes(new ArrayList<>(selectedNodes));
            selectedNodes.clear();
            onChanged.run();
        } else if (!selectedNodes.isEmpty()) {
            model.deleteSchemaNode(selectedNodes.iterator().next());
            selectedNodes.clear();
            onChanged.run();
        } else if (selectedEdge != null) {
            model.deleteSchemaEdge(selectedEdge);
            selectedEdge = null;
            onChanged.run();
        }
        repaint();
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
     *        провода выравнивались в одну прямую, как в yEd.</li>
     *  </ul>
     *  Раньше здесь были ещё края и ЦЕНТРЫ блоков оборудования — но центр блока
     *  почти никогда не совпадает с гнездом разъёма, куда реально приходит линия,
     *  и притяжка к нему уводила сегмент в наклон на пару градусов (баг-репорт).
     *  В отличие от snapPosition (для блоков) сравнивается ОДНА точка, а не три
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
        return new double[]{snappedX, snappedY};
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
     *  раньше (обычная прямая линия узел-узел). */
    private List<double[]> routePoints(SchemaEdge edge) {
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

    private WaypointHit waypointAt(Point p) {
        if (selectedEdge == null) {
            return null;
        }
        List<com.vjstb.ledscheme.model.EdgeWaypoint> wps = selectedEdge.getWaypoints();
        for (int i = 0; i < wps.size(); i++) {
            com.vjstb.ledscheme.model.EdgeWaypoint w = wps.get(i);
            if (Math.hypot(p.x - w.getX(), p.y - w.getY()) < 8) {
                return new WaypointHit(selectedEdge, i);
            }
        }
        return null;
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
        selectedEdge = edge;
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

    /** Сколько линий уже занято на этом гнезде другими связями (кроме exclude). */
    private int usedCount(String portId, SchemaEdge exclude) {
        int used = 0;
        for (SchemaEdge e2 : edges()) {
            if (e2 == exclude) {
                continue;
            }
            if (portId.equals(e2.getFromPortId()) || portId.equals(e2.getToPortId())) {
                used += e2.getWireCount() != null ? e2.getWireCount() : 1;
            }
        }
        return used;
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
     *  из гнёзд не найдено (обычная связь узел-узел без привязки к конкретному гнезду). */
    private String directionError(CardPort fromPort, CardPort toPort) {
        if (!settings.activeProfile().isFoolProofWiringEnabled() || fromPort == null || toPort == null) {
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

    private static double distanceToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
        double cx = ax + t * dx, cy = ay + t * dy;
        return Math.hypot(px - cx, py - cy);
    }

    private void handleRightClick(MouseEvent e) {
        Point mp = toModel(e.getPoint());
        SchemaNode hitNode = nodeAt(mp);
        if (hitNode != null) {
            // ПКМ по узлу, УЖЕ входящему в многовыделение — сохраняет его целиком
            // (меню предложит удалить ВСЕ выбранные узлы), иначе сужает выделение
            // до этого одного узла и показывает обычное подробное меню.
            if (!selectedNodes.contains(hitNode)) {
                selectedNodes.clear();
                selectedNodes.add(hitNode);
            }
            selectedEdge = null;
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
            selectedEdge = hitEdge;
            selectedNodes.clear();
            repaint();
            showEdgeMenu(hitEdge, e.getX(), e.getY());
        }
    }

    /** Контекстное меню для КЛИКА ПРАВОЙ по узлу, входящему в многовыделение (см.
     *  {@link #handleRightClick}) — только общие для группы действия (удаление),
     *  подробное меню одного узла ({@link #showNodeMenu}) для группы неприменимо
     *  (переименование/тип/карты — свойства ОДНОГО конкретного узла). */
    private void showMultiNodeMenu(int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        javax.swing.JMenuItem del = new javax.swing.JMenuItem("Удалить выбранные (" + selectedNodes.size() + ")");
        del.addActionListener(ev -> deleteSelected());
        menu.add(del);
        menu.show(this, x, y);
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
                javax.swing.JMenuItem item = new javax.swing.JMenuItem((current ? "✓ " : "") + ci.getLabel());
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
            Color initial = edge.getColor() != null ? new Color(edge.getColor()) : Palette.MUTED;
            Color chosen = UiKit.showColorChooser(this, "Цвет линии связи", initial);
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
            selectedEdge = null;
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
        g2.dispose();
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
        g2.setColor(new Color(Palette.ACCENT.getRed(), Palette.ACCENT.getGreen(), Palette.ACCENT.getBlue(), 40));
        g2.fillRect(x1, y1, w, h);
        g2.setColor(Palette.ACCENT);
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
        paint(g2, width, height, renderScreenWiring);
        g2.dispose();
        return img;
    }

    private void paint(Graphics2D g2, int width, int height, boolean renderScreenWiring) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Palette.BG);
        g2.fillRect(0, 0, width, height);

        List<SchemaNode> ns = nodes();
        List<SchemaEdge> es = edges();

        if (ns.isEmpty() && es.isEmpty()) {
            g2.setColor(Palette.MUTED);
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
            boolean selected = edge == selectedEdge;
            Color customColor = edge.getColor() != null ? new Color(edge.getColor()) : null;
            g2.setColor(selected ? Palette.ACCENT : customColor != null ? customColor : Palette.MUTED);
            float strokeWidth = selected ? 3f : 2f;
            // Пунктир — переключатель "Пунктиром" в контекстном меню связи (Task #85/v1.4),
            // например для обходного/резервного/мониторингового пути, как в референсном PDF.
            g2.setStroke(edge.isDashed()
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
            // Стрелка направления — по каждому под-сегменту РАСШИРЕННОЙ ломаной, кроме
            // тех, что лежат под дугой: границы дуги дают две неинтерактивные точки
            // излома, и стрелки встают до и после дуги, но не на ней. Без «мостиков»
            // расширенная ломаная совпадает с pts — по стрелке на сегмент, как раньше.
            WireHopGeometry.RenderRoute rr = WireHopGeometry.renderPoints(pts, hopSpans);
            List<double[]> rpts = rr.points();
            boolean[] onArc = rr.arcSegment();
            for (int i = 0; i < rpts.size() - 1; i++) {
                if (onArc[i]) {
                    continue;
                }
                drawArrow(g2, rpts.get(i)[0], rpts.get(i)[1], rpts.get(i + 1)[0], rpts.get(i + 1)[1]);
            }
            // Точки излома видны и хватаются мышью только у ВЫДЕЛЕННОЙ связи — иначе
            // маленькие кружки на каждом изломе каждой связи захламляли бы обычный вид.
            if (selected) {
                for (int i = 1; i < pts.size() - 1; i++) {
                    int wx = (int) pts.get(i)[0], wy = (int) pts.get(i)[1];
                    g2.setColor(Color.WHITE);
                    g2.fillOval(wx - 4, wy - 4, 8, 8);
                    g2.setColor(Palette.ACCENT);
                    g2.drawOval(wx - 4, wy - 4, 8, 8);
                }
            }
            g2.setStroke(new BasicStroke(strokeWidth));

            // всегда видимый кликабельный «чип» подписи — клик по нему сразу открывает
            // ввод подписи, без необходимости искать тонкую линию и знать про ПКМ
            java.awt.Rectangle chip = labelChipBounds(edge);
            if (chip != null) {
                String display = edge.displayLabel();
                boolean hasLabel = display != null && !display.isEmpty();
                g2.setColor(selected ? Palette.ACCENT : new Color(0x0d, 0x11, 0x17, hasLabel ? 235 : 170));
                g2.fillRoundRect(chip.x, chip.y, chip.width, chip.height, 8, 8);
                g2.setColor(selected ? Color.WHITE : Palette.BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(chip.x, chip.y, chip.width, chip.height, 8, 8);
                g2.setColor(hasLabel || selected ? Color.WHITE : Palette.MUTED);
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
                g2.setColor(Palette.ACCENT);
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
            Color fill = nodeColor(n.getType());
            g2.setColor(fill);
            g2.fillRoundRect((int) n.getX(), (int) n.getY(), nw, nh, 10, 10);
            g2.setColor(pending ? Color.YELLOW : (selected ? Color.WHITE : Palette.BORDER));
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
                g2.setColor(Palette.WARN);
                g2.setStroke(new BasicStroke(3f));
                g2.drawRoundRect((int) n.getX() - 1, (int) n.getY() - 1, nw + 2, nh + 2, 12, 12);
            }

            String title = n.getType() == SchemaNodeType.SCREEN ? resolveScreenLabel(n) : n.getLabel();
            if (title == null || title.isEmpty()) {
                title = model.categoryLabel(n.getType());
            }
            title = withControllerLegendTag(n, title);
            g2.setColor(Color.BLACK);
            g2.setFont(titleFont);
            // В вертикальной ориентации разъёмов (Task #2/v1.6) гнёзда занимают ВСЮ
            // ширину узла у верхнего края — обычное название узла в углу (как раньше)
            // визуально налезало на эту строку гнёзд (баг-репорт со скриншотом). Для
            // узлов, у которых вообще рисуются гнёзда, название вместо угла ставим по
            // центру блока — пользователь попросил именно так («писать посередине»).
            boolean centerTitle = verticalConnectors && n.getType() != SchemaNodeType.SCREEN
                    && !portsOf(n).isEmpty();
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
            g2.setColor(new Color(0, 0, 0, 160));
            if (n.getType() == SchemaNodeType.SCREEN) {
                if (renderScreenWiring) {
                    drawScreenWiringThumbnail(g2, n, nw, nh);
                } else {
                    drawClipped(g2, screenMeta(n), (int) n.getX() + 8, (int) n.getY() + 38, nw - 16);
                }
            } else if (n.isAutoPortLegend()) {
                drawPortLegendContent(g2, n, nw, nh);
            } else if (!portsOf(n).isEmpty()) {
                drawConnectorRows(g2, n, portsOf(n), (int) n.getX(), (int) n.getY(), nw, nh);
            } else {
                drawClipped(g2, model.categoryLabel(n.getType()), (int) n.getX() + 8, (int) n.getY() + 38, nw - 16);
            }

            if (overloaded) {
                g2.setColor(Palette.WARN);
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
                g2.setColor(new Color(0, 0, 0, 150));
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
                    g.left(), g.top());
            boolean pending = cabId.equals(connectPendingCabinetInstanceId) && n.getId().equals(connectPendingId);
            boolean hovered = hoveredCabinetSocket != null && hoveredCabinetSocket.node() == n
                    && cabId.equals(hoveredCabinetSocket.cabinetInstanceId());
            int d = Math.max(6, Math.min(r.width, r.height) / 2);
            int cx = r.x + r.width / 2, cy = r.y + r.height / 2;
            g3.setColor(pending ? Color.YELLOW : (hovered ? Color.WHITE : new Color(255, 221, 0, 210)));
            g3.fillOval(cx - d / 2, cy - d / 2, d, d);
            g3.setColor(new Color(0, 0, 0, 180));
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
                        g.left(), g.top());
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
        java.awt.Rectangle r = SchemeRenderer.cabinetScreenRect(cab, g.type(), g.cellW(), g.cellH(), g.left(), g.top());
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    private static List<PortEntry> flattenCardPorts(List<SchemaCard> cards) {
        List<PortEntry> all = new ArrayList<>();
        for (SchemaCard c : cards) {
            for (CardPort p : c.getPorts()) {
                all.add(new PortEntry(p, c.getName(), c.getId()));
            }
        }
        return all;
    }

    private static final int CONNECTOR_ROW_H = 13;
    private static final int CONNECTOR_DOT_D = 7;
    /** Отступ от верха узла до первой строки разъёмов — увеличен с 34 до 38
     *  (Task #110), т.к. у карт первая "строка" теперь шапка-рамка с названием
     *  карты, и заголовку узла нужно чуть больше запаса, чтобы рамка блока не
     *  подходила к нему вплотную (баг-репорт: рамка/шапка карты наезжала на
     *  название узла). Продублировано в AppModel.PORT_ROWS_TOP_OFFSET — держать
     *  оба значения одинаковыми. */
    private static final int PORT_ROWS_TOP_OFFSET = 38;
    /** Разъёмы одной карты рисуются внутри своего рамка-блока (а не одним общим
     *  списком строк вперемешку с другими картами, как раньше) — см. Task #109:
     *  повторный баг-репорт, что при большом количестве разъёмов подписи разных
     *  карт визуально сливались/наезжали друг на друга. CARD_HEADER_H — место под
     *  строку названия карты в шапке блока — РАВНО высоте обычной строки разъёма
     *  (CONNECTOR_ROW_H), а не отдельное произвольное число: шапка по сути и есть
     *  ещё одна строка в том же ритме, только без точки-гнезда — так гарантированно
     *  не наезжает на первую настоящую строку карты (первая попытка со своим
     *  числом ловила именно это — баг-репорт со скриншотом, где название карты
     *  накладывалось на "4×SDI..."). CARD_BLOCK_PAD — внутренний отступ рамки
     *  сверху/снизу, CARD_BLOCK_GAP — промежуток МЕЖДУ блоками соседних карт. Эти
     *  же константы задействованы в AppModel.autoFitNodeToPorts, чтобы авто-высота
     *  узла учитывала место под рамки, а не только под строки. */
    private static final int CARD_HEADER_H = CONNECTOR_ROW_H;
    private static final int CARD_BLOCK_PAD = 3;
    private static final int CARD_BLOCK_GAP = 6;
    /** Ширина одной колонки в ВЕРТИКАЛЬНОЙ ориентации разъёмов (Task #2/v1.6, часть 2,
     *  см. UserProfile.isConnectorsVertical) — аналог CONNECTOR_ROW_H, но вдоль
     *  горизонтальной оси. Уже CONNECTOR_ROW_H: подпись типа разъёма здесь повёрнута
     *  на 90° (см. drawVerticalLabel), поэтому её ЧИТАЕМАЯ длина укладывается вдоль
     *  всей высоты узла (там обычно много места), а колонке нужно вместить только
     *  «толщину» повёрнутого текста + точку гнезда, а не всю строку целиком, как в
     *  горизонтальном режиме. CARD_HEADER_W — по той же логике, что и CARD_HEADER_H:
     *  шапка блока карты — это ещё одна такая же колонка, только без гнезда. */
    private static final int CONNECTOR_COL_W = 18;
    private static final int CARD_HEADER_W = CONNECTOR_COL_W;
    /** Отступ гнезда от края узла (гориз. режим) / от края зоны разъёмов под
     *  заголовком узла (верт. режим) — общее число для обеих ориентаций. */
    private static final int SOCKET_EDGE_MARGIN = 6;
    /** Небольшая устойчивая палитра для разъёмов — цвет назначается по хэшу типа
     *  (а не по жёсткому списку известных типов вроде HDMI/CEE), чтобы работать с
     *  любым введённым пользователем названием разъёма и не требовать сопровождения
     *  списка типов при появлении новых. */
    private static final Color[] CONNECTOR_PALETTE = {
            new Color(0xf78166), new Color(0x76e3ea), new Color(0xd2a8ff), new Color(0x7ee787),
            new Color(0xffd479), new Color(0xff7b9c), new Color(0x79c0ff), new Color(0xd29922),
    };

    private static Color connectorColor(String type) {
        int idx = Math.floorMod(type == null ? 0 : type.hashCode(), CONNECTOR_PALETTE.length);
        return CONNECTOR_PALETTE[idx];
    }

    /** Геометрия гнёзд разъёмов узла — общая и для отрисовки, и для хит-теста клика/
     *  наведения, и для привязки конца линии связи к конкретному гнезду: все три
     *  должны видеть ОДНИ И ТЕ ЖЕ координаты, иначе клик и картинка разъедутся.
     *  Возвращает только те гнёзда, что реально помещаются в текущий размер узла
     *  (как и раньше — остаток показывается «+N ещё» и не кликабелен, пока узел
     *  не увеличат). */
    private List<SocketRect> computeSocketRects(List<PortEntry> ports, int x, int y, int w, int h) {
        // INDIVIDUAL (Task #2/v1.6) — независимая настройка ОТОБРАЖЕНИЯ (не путать с
        // isSocketWiringEnabled, которая решает, цепляется ли ВООБЩЕ линия связи за
        // конкретное гнездо, а не за узел целиком): группа CardPort с count>1
        // разворачивается в N отдельных строк-гнёзд вместо одной строки «N×Тип».
        // Модель не меняется — все N строк по-прежнему ссылаются на один и тот же
        // CardPort.getId(), занятость конкретного гнезда определяется на лету по
        // порядку уже существующих SchemaEdge с этим portId (см. socketPosition).
        boolean individual = settings.activeProfile().getConnectorDisplayMode(mode) == ConnectorDisplayMode.INDIVIDUAL;
        boolean vertical = settings.activeProfile().isConnectorsVertical(mode);
        List<SocketRect> rects = new ArrayList<>();
        String prevCardId = null;
        boolean started = false;
        // "along" — координата вдоль направления, в котором идут строки/колонки
        // (Y сверху вниз в горизонтальном режиме, X слева направо в вертикальном);
        // "alongMax" — граница, за которой узел уже не вмещает следующую строку/
        // колонку целиком (остаток — "+N ещё…", см. drawConnectorRows). Шаг
        // (CONNECTOR_ROW_H/CONNECTOR_COL_W) и резервирование места под шапку блока
        // карты (CARD_HEADER_H/CARD_HEADER_W) — по одной и той же логике, просто
        // вдоль разных осей (см. javadoc у CONNECTOR_COL_W).
        int along = (vertical ? x : y) + PORT_ROWS_TOP_OFFSET;
        int alongMax = (vertical ? x + w : y + h) - 4;
        int alongStep = vertical ? CONNECTOR_COL_W : CONNECTOR_ROW_H;
        int headerStep = vertical ? CARD_HEADER_W : CARD_HEADER_H;
        // "across" — зона поперёк направления строк/колонок, где физически лежат
        // гнёзда: полная ширина узла в горизонтальном режиме (гнездо у левого ИЛИ
        // правого края), полная высота зоны разъёмов (под заголовком узла) в
        // вертикальном (гнездо у верхнего ИЛИ нижнего края этой зоны).
        int acrossNear = vertical ? y + PORT_ROWS_TOP_OFFSET : x;
        int acrossFar = vertical ? y + h - 4 : x + w;
        outer:
        for (PortEntry entry : ports) {
            String cardId = entry.cardId();
            if (!started || !Objects.equals(cardId, prevCardId)) {
                // Новая карта (или, для разъёмов питания без карт — первый и единственный
                // "безрамочный" проход) — резервируем место под шапку блока и, если это
                // не первый блок вообще, промежуток перед ним (см. CARD_HEADER_H/GAP выше).
                if (started) {
                    along += CARD_BLOCK_GAP;
                }
                if (cardId != null) {
                    along += headerStep;
                }
                prevCardId = cardId;
                started = true;
            }
            PortDirection dir = entry.port().getDirection();
            // Двунаправленное гнездо (IN_OUT, например SDI Loop In/Out) физически ОДИН
            // разъём независимо от режима отображения — оно и в INDIVIDUAL остаётся
            // одной строкой с точкой с обеих сторон (см. ветку ниже), а не N строками:
            // разворачивать в отдельные гнёзда имеет смысл только для однонаправленных
            // групп однотипных разъёмов (типичный случай — распределительный блок).
            int slots = individual && dir != PortDirection.IN_OUT ? Math.max(1, entry.port().getCount()) : 1;
            for (int slot = 0; slot < slots; slot++) {
                if (along > alongMax) {
                    break outer;
                }
                if (vertical) {
                    int dotX = along;
                    if (dir == PortDirection.IN_OUT) {
                        rects.add(new SocketRect(entry, true, dotX, acrossNear + SOCKET_EDGE_MARGIN, slot));
                        rects.add(new SocketRect(entry, false, dotX,
                                acrossFar - CONNECTOR_DOT_D - SOCKET_EDGE_MARGIN, slot));
                    } else {
                        boolean isIn = dir == PortDirection.IN;
                        int dotY = isIn ? acrossNear + SOCKET_EDGE_MARGIN
                                : acrossFar - CONNECTOR_DOT_D - SOCKET_EDGE_MARGIN;
                        rects.add(new SocketRect(entry, isIn, dotX, dotY, slot));
                    }
                } else {
                    int dotY = along - CONNECTOR_DOT_D;
                    if (dir == PortDirection.IN_OUT) {
                        // Одна и та же группа CardPort (общий id и count), доступное количество
                        // НЕ удваивается — обе точки лишь два способа щёлкнуть/подвести линию к
                        // одному и тому же гнезду (см. socketAt/socketPosition, которые ищут
                        // совпадение по CardPort.getId(), а не по стороне).
                        rects.add(new SocketRect(entry, true, acrossNear + SOCKET_EDGE_MARGIN, dotY, slot));
                        rects.add(new SocketRect(entry, false,
                                acrossFar - CONNECTOR_DOT_D - SOCKET_EDGE_MARGIN, dotY, slot));
                    } else {
                        boolean isIn = dir == PortDirection.IN;
                        int dotX = isIn ? acrossNear + SOCKET_EDGE_MARGIN
                                : acrossFar - CONNECTOR_DOT_D - SOCKET_EDGE_MARGIN;
                        rects.add(new SocketRect(entry, isIn, dotX, dotY, slot));
                    }
                }
                along += alongStep;
            }
        }
        return rects;
    }

    /** Рамка-блок карты + название в шапке — границы блока получаются прямо из
     *  along-координат (Y в горизонтальном режиме, X в вертикальном) уже вычисленных
     *  {@link SocketRect} (т.е. гарантированно согласованы с местом, которое для
     *  шапки/отступов зарезервировал {@link #computeSocketRects}, без повторного
     *  дублирования этой геометрии). Идущие подряд {@link SocketRect} с одинаковым
     *  (не-null) cardId — один блок; у разъёмов питания cardId всегда null — для
     *  них блок не рисуется. */
    private void drawCardBlockBorders(Graphics2D g2, List<SocketRect> rects, int x, int y, int w, int h) {
        boolean vertical = settings.activeProfile().isConnectorsVertical(mode);
        // "across" — поперечная зона блока: полная ширина узла в горизонтальном
        // режиме, полная высота зоны разъёмов (под заголовком узла) в вертикальном —
        // одна и та же зона для ВСЕХ блоков карт узла, вдоль неё блок просто рисуется
        // на всю глубину (как и раньше в горизонтальном режиме).
        int acrossNear = vertical ? y + PORT_ROWS_TOP_OFFSET : x;
        int acrossFar = vertical ? y + h - 4 : x + w;
        String curCardId = null;
        String curCardName = null;
        int blockStart = -1;
        int blockEnd = -1;
        boolean open = false;
        for (SocketRect r : rects) {
            String cardId = r.entry().cardId();
            int alongPos = vertical ? r.dotX() : r.dotY();
            if (!open || !Objects.equals(cardId, curCardId)) {
                if (open && curCardId != null) {
                    paintCardBlockBorder(g2, vertical, acrossNear, acrossFar, blockStart, blockEnd);
                }
                curCardId = cardId;
                curCardName = r.entry().groupName();
                // computeSocketRects зарезервировал под шапку ровно ОДНУ строку/колонку
                // (CARD_HEADER_H/CARD_HEADER_W == шаг обычной строки/колонки) перед первой
                // настоящей строкой/колонкой карты — значит "виртуальная" позиция шапки
                // лежит на один такой шаг раньше первой точки-гнезда, в том же ритме, что
                // и обычные строки/колонки (гарантированно не наезжает на неё, в отличие
                // от первой попытки с произвольным числом — баг-репорт, где название карты
                // налезало на первую строку разъёмов). Рамка — с небольшим отступом ещё раньше.
                int headerAlong = alongPos - (vertical ? CARD_HEADER_W : CARD_HEADER_H);
                blockStart = headerAlong - CARD_BLOCK_PAD;
                blockEnd = alongPos + CONNECTOR_DOT_D + CARD_BLOCK_PAD;
                open = true;
                if (cardId != null) {
                    paintCardBlockHeader(g2, vertical, acrossNear, acrossFar, headerAlong, curCardName);
                }
            } else {
                blockEnd = Math.max(blockEnd, alongPos + CONNECTOR_DOT_D + CARD_BLOCK_PAD);
            }
        }
        if (open && curCardId != null) {
            paintCardBlockBorder(g2, vertical, acrossNear, acrossFar, blockStart, blockEnd);
        }
    }

    private void paintCardBlockBorder(Graphics2D g2, boolean vertical, int acrossNear, int acrossFar,
                                       int alongStart, int alongEnd) {
        g2.setColor(new Color(0, 0, 0, 60));
        g2.setStroke(new BasicStroke(1f));
        if (vertical) {
            g2.drawRoundRect(alongStart, acrossNear + 3, alongEnd - alongStart, acrossFar - acrossNear - 6, 6, 6);
        } else {
            g2.drawRoundRect(acrossNear + 3, alongStart, acrossFar - acrossNear - 6, alongEnd - alongStart, 6, 6);
        }
    }

    /** Название карты в шапке блока — в горизонтальном режиме печатается той же
     *  строкой, что и baseline обычной строки разъёма ({@code headerAlong + CONNECTOR_DOT_D}),
     *  просто на строку выше первой строки этой карты; в вертикальном — центрируется
     *  по высоте зоны разъёмов (у шапки нет своего гнезда, рядом с которым можно
     *  было бы её поставить, см. {@link #drawVerticalLabelCentered}). */
    private void paintCardBlockHeader(Graphics2D g2, boolean vertical, int acrossNear, int acrossFar,
                                       int headerAlong, String cardName) {
        if (cardName == null || cardName.isEmpty()) {
            return;
        }
        g2.setColor(new Color(0, 0, 0, 150));
        if (vertical) {
            int maxLen = (acrossFar - acrossNear) - 12;
            drawVerticalLabelCentered(g2, cardName, headerAlong + CONNECTOR_DOT_D / 2,
                    (acrossNear + acrossFar) / 2, maxLen);
        } else {
            String clipped = clipToWidth(g2, cardName, (acrossFar - acrossNear) - 16);
            g2.drawString(clipped, acrossNear + 8, headerAlong + CONNECTOR_DOT_D);
        }
    }

    /** Подпись, повёрнутая на 90° (для вертикальной ориентации разъёмов, Task #2/v1.6,
     *  часть 2, доработано после баг-репорта со скриншотом — см. Task #2/v1.6-fix:
     *  подписи, растянутые на всю высоту зоны разъёмов от общего нижнего края, у
     *  верхних (IN) гнёзд визуально проходили СКВОЗЬ саму точку-гнездо и налезали
     *  друг на друга у соседних колонок). Каждая подпись теперь стоит РЯДОМ со своим
     *  гнездом и растёт К ЦЕНТРУ блока, а не через всю его высоту:
     *  {@link #drawVerticalLabelGrowUp} — читается снизу вверх, якорь у НИЖНЕГО края
     *  (для OUT-гнёзд, растёт от гнезда вверх к центру); {@link #drawVerticalLabelGrowDown}
     *  — читается сверху вниз, якорь у ВЕРХНЕГО края (для IN-гнёзд, растёт от гнезда
     *  вниз к центру). centerX — центр колонки по X (совпадает с центром точки-гнезда),
     *  maxLen — доступная длина вдоль вертикали (отсечение троеточием — см. {@link #clipToWidth}). */
    private void drawVerticalLabelGrowUp(Graphics2D g2, String text, int centerX, int bottomY, int maxLen) {
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

    private void drawVerticalLabelGrowDown(Graphics2D g2, String text, int centerX, int topY, int maxLen) {
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

    /** Подпись, отцентрованная по вертикали вокруг centerY (растёт вверх от
     *  вычисленной нижней границы ровно настолько, чтобы её середина пришлась на
     *  centerY) — для названия карты в шапке блока: у шапки нет своего гнезда,
     *  рядом с которым можно было бы её поставить, поэтому центр всей зоны разъёмов —
     *  осмысленное умолчание (тот же принцип, что пользователь попросил применить и
     *  к названию самого узла — см. paint(), centerTitle). */
    private void drawVerticalLabelCentered(Graphics2D g2, String text, int centerX, int centerY, int maxLen) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String clipped = clipToWidth(g2, text, Math.max(0, maxLen));
        int textLen = g2.getFontMetrics().stringWidth(clipped);
        drawVerticalLabelGrowUp(g2, clipped, centerX, centerY + textLen / 2, maxLen);
    }

    /** Разъёмы узла построчно, сгруппированные визуально по картам — каждая карта
     *  рисуется своим рамка-блоком с шапкой (название карты) внутри блока
     *  оборудования, а не одним общим списком строк вперемешку с другими картами
     *  (Task #109, повторный запрос: «разъёмы входящие в одну и ту же карточку
     *  должны объединяться визуально в блок этой карточки» — раньше название
     *  карты писалось лишь ИНЛАЙНОМ у первой строки её группы (Task #67), из-за
     *  чего при большом числе разъёмов подписи разных карт визуально сливались/
     *  наезжали друг на друга, как на скриншоте с "SMODE"). У разъёмов питания
     *  (без карт, cardId==null) рамка не рисуется — они остаются плоским списком,
     *  как и раньше. Если включена «коммутация через гнёзда» — гнёзда ещё и
     *  кликабельны (см. {@link #socketAt}), наведённое подсвечивается белым кольцом. */
    private void drawConnectorRows(Graphics2D g2, SchemaNode node, List<PortEntry> ports, int x, int y, int w, int h) {
        boolean vertical = settings.activeProfile().isConnectorsVertical(mode);
        List<SocketRect> rects = computeSocketRects(ports, x, y, w, h);
        drawCardBlockBorders(g2, rects, x, y, w, h);
        int acrossNear = vertical ? y + PORT_ROWS_TOP_OFFSET : x;
        int acrossFar = vertical ? y + h - 4 : x + w;
        for (SocketRect r : rects) {
            CardPort port = r.entry().port();
            boolean hovered = hoveredSocket != null && hoveredSocket.node() == node && hoveredSocket.port() == port;
            g2.setColor(connectorColor(port.getConnectorType()));
            g2.fillOval(r.dotX(), r.dotY(), CONNECTOR_DOT_D, CONNECTOR_DOT_D);
            g2.setColor(hovered ? Color.WHITE : Color.BLACK);
            g2.setStroke(new BasicStroke(hovered ? 2f : 1f));
            int ring = hovered ? 2 : 0;
            g2.drawOval(r.dotX() - ring, r.dotY() - ring, CONNECTOR_DOT_D + ring * 2, CONNECTOR_DOT_D + ring * 2);

            // IN_OUT рисует ДВЕ точки на один и тот же порт (см. computeSocketRects) — в
            // горизонтальном режиме они на разных сторонах строки, поэтому подпись у
            // каждой отдельная и не сливается; в вертикальном обе точки в ОДНОЙ колонке —
            // рисуем подпись только у первой (isIn=true), иначе она наложилась бы сама на себя.
            if (vertical && !r.isIn() && port.getDirection() == PortDirection.IN_OUT) {
                continue;
            }

            // Название карты теперь пишется в шапке её блока (см. drawCardBlockBorders),
            // повторять его в скобках у каждой строки больше не нужно. В INDIVIDUAL-
            // режиме (Task #2/v1.6) группа с count>1 развёрнута в N отдельных строк —
            // «N×Тип» на КАЖДОЙ из них было бы неверно (выглядело бы так, будто в
            // каждой строке ещё N разъёмов), поэтому вместо этого — тип и номер
            // конкретного гнезда среди развёрнутых.
            boolean expandedRow = settings.activeProfile().getConnectorDisplayMode(mode) == ConnectorDisplayMode.INDIVIDUAL
                    && port.getCount() > 1 && port.getDirection() != PortDirection.IN_OUT;
            String label = expandedRow ? port.getConnectorType() + " #" + (r.slotIndex() + 1)
                    : port.getCount() + "×" + port.getConnectorType();
            g2.setColor(new Color(0, 0, 0, 190));
            if (vertical) {
                // Подпись стоит РЯДОМ со своим гнездом и растёт К ЦЕНТРУ зоны разъёмов —
                // не через всю высоту от общего дальнего края (см. javadoc у
                // drawVerticalLabelGrowUp/Down выше — баг-репорт со скриншотом, где
                // подписи проходили сквозь гнёзда и налезали друг на друга).
                int maxLen = (acrossFar - acrossNear) / 2 - CONNECTOR_DOT_D - 8;
                int labelCenterX = r.dotX() + CONNECTOR_DOT_D / 2;
                if (r.isIn()) {
                    drawVerticalLabelGrowDown(g2, label, labelCenterX, r.dotY() + CONNECTOR_DOT_D + 4, maxLen);
                } else {
                    drawVerticalLabelGrowUp(g2, label, labelCenterX, r.dotY() - 4, maxLen);
                }
            } else {
                int maxTextW = w - CONNECTOR_DOT_D - 16;
                String clipped = clipToWidth(g2, label, maxTextW);
                FontMetrics fm = g2.getFontMetrics();
                int textX = r.isIn() ? r.dotX() + CONNECTOR_DOT_D + 4 : r.dotX() - 4 - fm.stringWidth(clipped);
                g2.drawString(clipped, textX, r.dotY() + CONNECTOR_DOT_D);
            }
        }
        // Считаем по числу РАЗЛИЧНЫХ гнёзд (PortEntry), а не по числу точек — у
        // двунаправленного гнезда (IN_OUT) одна запись превращается в ДВЕ точки
        // (см. computeSocketRects), иначе подсчёт "+N ещё" был бы заниженным.
        long renderedEntries = rects.stream().map(SocketRect::entry).distinct().count();
        if (renderedEntries < ports.size()) {
            int remaining = ports.size() - (int) renderedEntries;
            g2.setColor(new Color(0, 0, 0, 150));
            if (vertical) {
                int hintX = rects.isEmpty() ? x + 4 : rects.get(rects.size() - 1).dotX() + CONNECTOR_COL_W;
                int hintY = acrossNear + (acrossFar - acrossNear) / 2;
                g2.drawString("+" + remaining, hintX, hintY);
            } else {
                int hintY = rects.isEmpty() ? y + PORT_ROWS_TOP_OFFSET
                        : rects.get(rects.size() - 1).dotY() + CONNECTOR_DOT_D + CONNECTOR_ROW_H;
                g2.drawString("+" + remaining + " ещё…", x + 10, hintY);
            }
        }
    }

    /** Список гнёзд узла (карты для сигнала, разъёмы для питания) — пусто, если
     *  комплектация не задана. Раньше отрисовка карт была ошибочно ограничена
     *  типами "Медиасервер"/"Контроллер" — но карты может нести ЛЮБОЙ тип узла
     *  (например, узел из пресета с картами, впоследствии переклассифицированный
     *  в "Прочее оборудование"/"Конвертер" через "Изменить тип"), и раз карты уже
     *  назначены — их гнёзда должны отрисовываться независимо от типа узла. */
    private static List<PortEntry> portsOf(SchemaNode n) {
        if (!n.getCards().isEmpty()) {
            return flattenCardPorts(n.getCards());
        }
        if (n.getMode() == SchemaMode.POWER && !n.getPowerConnectors().isEmpty()) {
            List<PortEntry> all = new ArrayList<>();
            for (CardPort p : n.getPowerConnectors()) {
                all.add(new PortEntry(p, null, null));
            }
            return all;
        }
        return List.of();
    }

    /** Экранные координаты (центр) гнезда с указанным id разъёма — null, если
     *  разъём не найден (узел изменился/разъём удалён) или portId не задан, тогда
     *  вызывающий код обычно откатывается к привязке от узла целиком. forEdge —
     *  КАКАЯ связь запрашивает точку (null — для превью ещё не созданной связи,
     *  см. mouseDragged/CONNECT): в режиме INDIVIDUAL у одного portId может быть
     *  НЕСКОЛЬКО гнёзд (развёрнутая группа, см. computeSocketRects) — тогда нужно
     *  выбрать ИМЕННО ТО, что физически соответствует этой связи (по порядку
     *  создания среди связей на этом portId, см. {@link #edgeOrdinalForPort}), а не
     *  всегда первое — иначе несколько параллельных линий одной группы визуально
     *  сходились бы в одну точку, что и обесценивало бы весь смысл INDIVIDUAL-режима. */
    private Point socketPosition(SchemaNode node, String portId, SchemaEdge forEdge) {
        if (portId == null) {
            return null;
        }
        List<SocketRect> rects = computeSocketRects(portsOf(node), (int) node.getX(), (int) node.getY(),
                (int) node.getWidth(), (int) node.getHeight());
        List<SocketRect> matches = new ArrayList<>();
        for (SocketRect r : rects) {
            if (r.entry().port().getId().equals(portId)) {
                matches.add(r);
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        // IN_OUT всегда даёт РОВНО 2 совпадения (вход+выход одного и того же
        // физического гнезда, см. computeSocketRects) — это НЕ развёрнутая группа,
        // всегда берём первое (вход), как и раньше до появления INDIVIDUAL-режима.
        if (matches.size() == 1 || matches.get(0).entry().port().getDirection() == PortDirection.IN_OUT) {
            SocketRect r = matches.get(0);
            return new Point(r.centerX(), r.centerY());
        }
        int ordinal = forEdge != null ? edgeOrdinalForPort(forEdge, portId) : usedCount(portId, null);
        ordinal = Math.max(0, Math.min(ordinal, matches.size() - 1));
        SocketRect r = matches.get(ordinal);
        return new Point(r.centerX(), r.centerY());
    }

    /** Порядковый номер этой связи среди всех рёбер сцены, ссылающихся на portId (в
     *  порядке списка — совпадает с порядком создания, т.к. новые рёбра всегда
     *  добавляются в конец) — используется только в INDIVIDUAL-режиме отображения
     *  разъёмов (см. socketPosition), чтобы параллельные кабели одной группы
     *  визуально расходились по разным гнёздам, а не сходились в одну точку. */
    private int edgeOrdinalForPort(SchemaEdge forEdge, String portId) {
        int idx = 0;
        for (SchemaEdge e : edges()) {
            if (!portId.equals(e.getFromPortId()) && !portId.equals(e.getToPortId())) {
                continue;
            }
            if (e == forEdge) {
                return idx;
            }
            idx++;
        }
        return idx;
    }

    /** Вертикальный запас вокруг гнезда для хит-теста — кликабельна вся строка по
     *  всей ширине узла, а не только несколько пикселей самой точки: пиксель-в-
     *  пиксель по 7px кружку оказался слишком неудобным «в некоторых случаях»
     *  (промах при клике воспринимался как «гнездо недоступно»). */
    private static final int SOCKET_ROW_HIT_PAD = 3;

    /** Гнездо разъёма под точкой клика/курсора — учитывает только реально
     *  отрисованные (видимые) гнёзда, как и {@link #computeSocketRects}. Кликабельна
     *  вся строка (по X — вся ширина узла, по Y — строка ± запас), не только сама
     *  точка-разъём. */
    private SocketHit socketAt(Point p) {
        boolean vertical = settings.activeProfile().isConnectorsVertical(mode);
        for (SchemaNode n : nodes()) {
            List<PortEntry> ports = portsOf(n);
            if (ports.isEmpty()) {
                continue;
            }
            int nx = (int) n.getX(), ny = (int) n.getY(), nw = (int) n.getWidth(), nh = (int) n.getHeight();
            // Кликабельна вся строка/колонка целиком (см. javadoc класса ниже) — поэтому
            // сперва грубо отсекаем узлы, где клик вообще не попадает в поперечную ось
            // (полная ширина узла в горизонтальном режиме, полная высота — в вертикальном),
            // а затем для каждого гнезда проверяем попадание вдоль ГЛАВНОЙ оси строки/колонки.
            if (vertical) {
                if (p.y < ny || p.y > ny + nh) {
                    continue;
                }
            } else {
                if (p.x < nx || p.x > nx + nw) {
                    continue;
                }
            }
            List<SocketRect> rects = computeSocketRects(ports, nx, ny, nw, nh);
            for (SocketRect r : rects) {
                boolean hit;
                if (vertical) {
                    int colLeft = r.dotX() - SOCKET_ROW_HIT_PAD;
                    int colRight = r.dotX() + CONNECTOR_DOT_D + SOCKET_ROW_HIT_PAD;
                    hit = p.x >= colLeft && p.x <= colRight;
                } else {
                    int rowTop = r.dotY() - SOCKET_ROW_HIT_PAD;
                    int rowBottom = r.dotY() + CONNECTOR_DOT_D + SOCKET_ROW_HIT_PAD;
                    hit = p.y >= rowTop && p.y <= rowBottom;
                }
                if (hit) {
                    return new SocketHit(n, r.entry().port());
                }
            }
        }
        return null;
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

    private static Color nodeColor(SchemaNodeType type) {
        return switch (type) {
            case SOURCE -> new Color(0xf78166);
            case DISTRO -> new Color(0xe3b341);
            case CONVERTER -> new Color(0x79c0ff);
            case SERVER -> new Color(0xd2a8ff);
            case CONTROLLER -> new Color(0x76e3ea);
            case SCREEN -> new Color(0x56d364);
            case MONITOR -> new Color(0xff9bce);
            default -> new Color(0xc0c8d0);
        };
    }
}
