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
import java.util.LinkedHashSet;
import java.util.List;
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

    private final AppModel model;
    private final SchemaMode mode;
    private final com.vjstb.ledscheme.settings.SettingsManager settings;
    private Runnable onChanged = () -> { };
    private Consumer<Screen> onScreenActivated;

    public enum Interaction { MOVE, CONNECT }

    private Interaction interaction = Interaction.MOVE;
    private SchemaNode dragNode;
    private double dragOffX, dragOffY;
    private SchemaNode resizeNode;
    /** Точка излома связи, которую сейчас тащат мышью (см. Task #85/v1.4) — null,
     *  если ничего не тащат. */
    private SchemaEdge draggingWaypointEdge;
    private int draggingWaypointIndex = -1;
    private String connectPendingId;
    /** Гнездо (CardPort), от которого начато соединение — только когда включена
     *  настройка «коммутация через гнёзда разъёмов»; null — соединение идёт от
     *  узла целиком, как раньше. */
    private String connectPendingPortId;
    private SocketHit hoveredSocket;
    private Point lastMouse;

    private SchemaNode selectedNode;
    private SchemaEdge selectedEdge;

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
     *  и для хит-теста клика, и для привязки конца линии связи к гнезду. */
    private record SocketRect(PortEntry entry, boolean isIn, int dotX, int dotY) {
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

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (SwingUtilities.isRightMouseButton(e)) {
                    handleRightClick(e);
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                SchemaNode hit = nodeAt(e.getPoint());
                if (interaction == Interaction.CONNECT) {
                    boolean socketMode = settings.activeProfile().isSocketWiringEnabled();
                    SocketHit socketHit = socketMode ? socketAt(e.getPoint()) : null;
                    if (socketHit != null) {
                        if (connectPendingId == null) {
                            connectPendingId = socketHit.node().getId();
                            connectPendingPortId = socketHit.port().getId();
                        } else if (connectPendingId.equals(socketHit.node().getId())) {
                            connectPendingId = null;
                            connectPendingPortId = null;
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
                                            socketHit.node().getId(), socketHit.port().getId(), null);
                                    onChanged.run();
                                } catch (RuntimeException ex) {
                                    JOptionPane.showMessageDialog(SchemaCanvasPanel.this, ex.getMessage(),
                                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                                }
                            }
                            connectPendingId = null;
                            connectPendingPortId = null;
                        }
                        repaint();
                        return;
                    }
                    if (hit == null) {
                        connectPendingId = null;
                        connectPendingPortId = null;
                    } else if (connectPendingId == null) {
                        connectPendingId = hit.getId();
                        connectPendingPortId = null;
                    } else if (connectPendingId.equals(hit.getId())) {
                        connectPendingId = null;
                        connectPendingPortId = null;
                    } else {
                        try {
                            model.addSchemaEdge(mode, connectPendingId, connectPendingPortId, hit.getId(), null, null);
                        } catch (RuntimeException ex) {
                            JOptionPane.showMessageDialog(SchemaCanvasPanel.this, ex.getMessage(),
                                    "Ошибка", JOptionPane.ERROR_MESSAGE);
                        }
                        connectPendingId = null;
                        connectPendingPortId = null;
                        onChanged.run();
                    }
                    repaint();
                    return;
                }
                SchemaNode resizeHit = resizeHandleAt(e.getPoint());
                if (resizeHit != null) {
                    selectedNode = resizeHit;
                    selectedEdge = null;
                    resizeNode = resizeHit;
                    repaint();
                    return;
                }
                WaypointHit wpHit = waypointAt(e.getPoint());
                if (wpHit != null) {
                    selectedEdge = wpHit.edge();
                    selectedNode = null;
                    draggingWaypointEdge = wpHit.edge();
                    draggingWaypointIndex = wpHit.index();
                    repaint();
                    return;
                }
                if (hit != null) {
                    selectedNode = hit;
                    selectedEdge = null;
                    dragNode = hit;
                    dragOffX = e.getX() - hit.getX();
                    dragOffY = e.getY() - hit.getY();
                    repaint();
                    return;
                }
                SchemaEdge chipHit = edgeLabelChipAt(e.getPoint());
                if (chipHit != null) {
                    selectedNode = null;
                    selectedEdge = chipHit;
                    repaint();
                    editEdgeLabel(chipHit);
                    return;
                }
                selectedNode = null;
                selectedEdge = edgeAt(e.getPoint());
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (resizeNode != null) {
                    resizeNode.setWidth(Math.max(MIN_NODE_W, e.getX() - resizeNode.getX()));
                    resizeNode.setHeight(Math.max(MIN_NODE_H, e.getY() - resizeNode.getY()));
                    revalidate();
                    repaint();
                } else if (draggingWaypointEdge != null) {
                    com.vjstb.ledscheme.model.EdgeWaypoint w =
                            draggingWaypointEdge.getWaypoints().get(draggingWaypointIndex);
                    w.setX(e.getX());
                    w.setY(e.getY());
                    repaint();
                } else if (dragNode != null) {
                    dragNode.setX(Math.max(0, e.getX() - dragOffX));
                    dragNode.setY(Math.max(0, e.getY() - dragOffY));
                    revalidate();
                    repaint();
                } else if (interaction == Interaction.CONNECT && connectPendingId != null) {
                    lastMouse = e.getPoint();
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (interaction == Interaction.CONNECT) {
                    if (connectPendingId != null) {
                        lastMouse = e.getPoint();
                    }
                    SocketHit hover = settings.activeProfile().isSocketWiringEnabled() ? socketAt(e.getPoint()) : null;
                    if (!java.util.Objects.equals(hover, hoveredSocket)) {
                        hoveredSocket = hover;
                        setCursor(Cursor.getPredefinedCursor(
                                hover != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    }
                    repaint();
                } else if (interaction == Interaction.MOVE) {
                    boolean overHandle = resizeHandleAt(e.getPoint()) != null;
                    setCursor(Cursor.getPredefinedCursor(
                            overHandle ? Cursor.SE_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (resizeNode != null) {
                    model.resizeSchemaNode(resizeNode, resizeNode.getWidth(), resizeNode.getHeight());
                    resizeNode = null;
                    onChanged.run();
                } else if (draggingWaypointEdge != null) {
                    model.setSchemaEdgeWaypoints(draggingWaypointEdge, draggingWaypointEdge.getWaypoints());
                    draggingWaypointEdge = null;
                    draggingWaypointIndex = -1;
                    onChanged.run();
                } else if (dragNode != null) {
                    model.moveSchemaNode(dragNode, dragNode.getX(), dragNode.getY());
                    dragNode = null;
                    onChanged.run();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    SchemaNode hit = nodeAt(e.getPoint());
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
                    if (hit == null && interaction == Interaction.MOVE && edgeLabelChipAt(e.getPoint()) == null) {
                        SchemaEdge edgeHit = edgeAt(e.getPoint());
                        if (edgeHit != null) {
                            insertWaypoint(edgeHit, e.getPoint());
                        }
                    }
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
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
        repaint();
    }

    public SchemaNode getSelectedNode() {
        return selectedNode;
    }

    public SchemaEdge getSelectedEdge() {
        return selectedEdge;
    }

    public void deleteSelected() {
        if (selectedNode != null) {
            model.deleteSchemaNode(selectedNode);
            selectedNode = null;
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
     *  несколько связей — иначе они рисовались бы друг поверх друга. */
    private double[] endpointsFor(SchemaEdge edge) {
        SchemaNode a = nodeById(edge.getFromNodeId());
        SchemaNode b = nodeById(edge.getToNodeId());
        if (a == null || b == null) {
            return null;
        }
        Point aSocket = socketPosition(a, edge.getFromPortId());
        Point bSocket = socketPosition(b, edge.getToPortId());
        double ax = aSocket != null ? aSocket.x : a.getX() + a.getWidth() / 2.0;
        double ay = aSocket != null ? aSocket.y : a.getY() + a.getHeight() / 2.0;
        double bx = bSocket != null ? bSocket.x : b.getX() + b.getWidth() / 2.0;
        double by = bSocket != null ? bSocket.y : b.getY() + b.getHeight() / 2.0;
        int[] slot = edgeSlot(edge);
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
        selectedNode = null;
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
        int mx = (int) mid[0];
        int my = (int) mid[1];
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
        // связи, ведущие к экрану, вообще не проверялись на лимит числа линий.
        // "N вводных" экрана — это фактическое число независимых силовых цепочек,
        // уже расключённых на кабинетах экрана (Питание → расключение), а значит и
        // максимум того, сколько отдельных линий схема вправе подвести к этому
        // экрану суммарно от всех источников (см. также screenUsedCount ниже).
        for (String nodeId : new String[]{edge.getFromNodeId(), edge.getToNodeId()}) {
            SchemaNode node = nodeById(nodeId);
            Integer capacity = node != null ? screenPowerCapacity(node) : null;
            if (capacity == null) {
                continue;
            }
            int used = screenUsedCount(nodeId, edge);
            int rem = Math.max(1, capacity - used);
            if (maxCount == null || rem < maxCount) {
                maxCount = rem;
                maxCountReason = node.getLabel() + ": свободно " + Math.max(0, capacity - used)
                        + " из " + capacity + " вводных";
            }
        }
        WireLabelDialog dlg = new WireLabelDialog(SwingUtilities.getWindowAncestor(this), mode, edge,
                connectorHintsFor(edge), lockedType, maxCount, maxCountReason);
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

    /** Для узла-ссылки на экран (SchemaNodeType.SCREEN) в схеме ПИТАНИЯ — сколько
     *  всего независимых силовых линий может физически прийти в этот узел: то же
     *  число, что показано в подписи узла как "N вводных" (фактическое число
     *  силовых цепочек, уже расключённых на кабинетах экрана в «Расключение
     *  экрана» → Питание) — пользователь явно указал, что это одно и то же число.
     *  null — понятие неприменимо (не экран, не питание, экран не найден). */
    private Integer screenPowerCapacity(SchemaNode node) {
        if (mode != SchemaMode.POWER || node.getType() != SchemaNodeType.SCREEN || node.getScreenRefId() == null) {
            return null;
        }
        Screen scr = screenById(node.getScreenRefId());
        return scr == null ? null : model.powerChainsTouchingScreen(scr).size();
    }

    /** Сколько линий уже подведено к узлу-экрану ДРУГИМИ связями схемы (кроме
     *  exclude) — суммарно от всех источников, а не по одному конкретному гнезду,
     *  т.к. у узла-экрана нет отдельных гнёзд (см. screenPowerCapacity). */
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
     *  Ничего не проверяет, если настройка выключена или хотя бы одно из гнёзд не
     *  найдено (обычная связь узел-узел без привязки к конкретному гнезду). */
    private String directionError(CardPort fromPort, CardPort toPort) {
        if (!settings.activeProfile().isFoolProofWiringEnabled() || fromPort == null || toPort == null) {
            return null;
        }
        if (fromPort.getDirection() == toPort.getDirection()) {
            String dir = fromPort.getDirection() == PortDirection.IN ? "входа" : "выхода";
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
        SchemaNode hitNode = nodeAt(e.getPoint());
        if (hitNode != null) {
            selectedNode = hitNode;
            selectedEdge = null;
            repaint();
            showNodeMenu(hitNode, e.getX(), e.getY());
            return;
        }
        // Точки излома видны/хватаются только у уже ВЫДЕЛЕННОЙ связи (см. waypointAt),
        // поэтому этот хит-тест что-то находит, только если ПКМ пришёлся по излому связи,
        // которая уже была выделена левым кликом — в этом случае показываем отдельное
        // меню одной точки, а не общее меню связи.
        WaypointHit wpHit = waypointAt(e.getPoint());
        if (wpHit != null) {
            showWaypointMenu(wpHit.edge(), wpHit.index(), e.getX(), e.getY());
            return;
        }
        SchemaEdge hitEdge = edgeAt(e.getPoint());
        if (hitEdge != null) {
            selectedEdge = hitEdge;
            selectedNode = null;
            repaint();
            showEdgeMenu(hitEdge, e.getX(), e.getY());
        }
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
                javax.swing.JMenuItem item = new javax.swing.JMenuItem(t.getLabel());
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
        // Карты (сигнальные гнёзда) доступны любому не-экранному узлу СИГНАЛЬНОЙ
        // схемы — симметрично тому, как "Разъёмы питания…" ниже доступны любому
        // не-экранному узлу схемы ПИТАНИЯ. Раньше это было ограничено только типами
        // "Медиасервер"/"Контроллер", из-за чего для остальных типов (конвертер,
        // прочее оборудование) не было способа добавить/отредактировать карты
        // вручную — хотя они могли УЖЕ иметь карты (например, из пресета).
        if (node.getMode() == SchemaMode.SIGNAL && node.getType() != SchemaNodeType.SCREEN) {
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
                        ? node.getType().getLabel() : node.getLabel();
                PowerConnectorsConfigDialog dlg = new PowerConnectorsConfigDialog(
                        SwingUtilities.getWindowAncestor(this), title,
                        PowerConnectorsConfigDialog.forNode(model, node));
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
            selectedNode = null;
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

    @Override
    public Dimension getPreferredSize() {
        double maxX = 800, maxY = 500;
        for (SchemaNode n : nodes()) {
            maxX = Math.max(maxX, n.getX() + n.getWidth() + MARGIN);
            maxY = Math.max(maxY, n.getY() + n.getHeight() + MARGIN);
        }
        return new Dimension((int) maxX, (int) maxY);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        paint(g2, getWidth(), getHeight(), settings.activeProfile().isSchemaScreensAsWiringDiagram());
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
        BufferedImage img = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
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
        for (SchemaEdge edge : es) {
            List<double[]> pts = routePoints(edge);
            if (pts == null) {
                continue;
            }
            boolean selected = edge == selectedEdge;
            g2.setColor(selected ? Palette.ACCENT : Palette.MUTED);
            float strokeWidth = selected ? 3f : 2f;
            // Пунктир — переключатель "Пунктиром" в контекстном меню связи (Task #85/v1.4),
            // например для обходного/резервного/мониторингового пути, как в референсном PDF.
            g2.setStroke(edge.isDashed()
                    ? new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{7, 5}, 0)
                    : new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // Ломаная линия через точки излома (см. EdgeWaypoint) вместо одной прямой —
            // ортогональная/произвольная маршрутизация, стрелка — только на последнем
            // отрезке (указывает на конечный узел), не на каждом изломе.
            for (int i = 0; i < pts.size() - 1; i++) {
                double ax = pts.get(i)[0], ay = pts.get(i)[1];
                double bx = pts.get(i + 1)[0], by = pts.get(i + 1)[1];
                g2.drawLine((int) ax, (int) ay, (int) bx, (int) by);
                if (i == pts.size() - 2) {
                    drawArrow(g2, ax, ay, bx, by);
                }
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
                Point socket = socketPosition(pending, connectPendingPortId);
                int px = socket != null ? socket.x : (int) (pending.getX() + pending.getWidth() / 2.0);
                int py = socket != null ? socket.y : (int) (pending.getY() + pending.getHeight() / 2.0);
                g2.setColor(Palette.ACCENT);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0,
                        new float[]{5, 4}, 0));
                g2.drawLine(px, py, lastMouse.x, lastMouse.y);
            }
        }

        Font titleFont = getFont().deriveFont(Font.BOLD, 12f);
        Font metaFont = getFont().deriveFont(10f);
        for (SchemaNode n : ns) {
            boolean selected = n == selectedNode;
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
                title = n.getType().getLabel();
            }
            g2.setColor(Color.BLACK);
            g2.setFont(titleFont);
            drawClipped(g2, title, (int) n.getX() + 8, (int) n.getY() + 20, nw - 16);
            g2.setFont(metaFont);
            g2.setColor(new Color(0, 0, 0, 160));
            if (n.getType() == SchemaNodeType.SCREEN) {
                if (renderScreenWiring) {
                    drawScreenWiringThumbnail(g2, n, nw, nh);
                } else {
                    drawClipped(g2, screenMeta(n), (int) n.getX() + 8, (int) n.getY() + 38, nw - 16);
                }
            } else if (!portsOf(n).isEmpty()) {
                drawConnectorRows(g2, n, portsOf(n), (int) n.getX(), (int) n.getY(), nw, nh);
            } else {
                drawClipped(g2, n.getType().getLabel(), (int) n.getX() + 8, (int) n.getY() + 38, nw - 16);
            }

            if (overloaded) {
                g2.setColor(Palette.WARN);
                g2.setFont(titleFont);
                g2.drawString("⚠", (int) n.getX() + nw - 20, (int) n.getY() + 16);
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

    /** Краткая сводка ВХОДНЫХ гнёзд узла-экрана вида «2×PowerCon, 1×Cat6/RJ45» —
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
    private void drawScreenWiringThumbnail(Graphics2D g2, SchemaNode n, int nw, int nh) {
        Screen scr = screenById(n.getScreenRefId());
        if (scr == null) {
            return;
        }
        CabinetType t = model.typeOf(scr);
        if (t == null || t.getWidthMm() <= 0 || t.getHeightMm() <= 0 || scr.getCols() <= 0 || scr.getRows() <= 0) {
            return;
        }
        int pad = 4;
        int top = (int) n.getY() + 34;
        int left = (int) n.getX() + pad;
        int availW = nw - pad * 2;
        // Полоса контроллеров (см. SchemeRenderer.drawControllerSummaryBar) — только
        // для сигнала, отнимает фиксированную полосу СНИЗУ у сетки кабинетов, если
        // вообще помещается хоть с какой-то разумной высотой сетки.
        int barH = mode == SchemaMode.POWER ? 0 : SchemeRenderer.controllerSummaryBarHeight(scr);
        int gridBottom = (int) (n.getY() + nh) - pad;
        int availH = gridBottom - top - barH;
        if (availH < 20) {
            barH = 0;
            availH = gridBottom - top;
        }
        if (availW < 10 || availH < 10) {
            return;
        }
        double scale = Math.min(availW / (scr.getCols() * t.getWidthMm()), availH / (scr.getRows() * t.getHeightMm()));
        if (scale <= 0) {
            return;
        }
        int cellW = Math.max(1, (int) Math.round(t.getWidthMm() * scale));
        int cellH = Math.max(1, (int) Math.round(t.getHeightMm() * scale));
        Scene scene = model.getCurrentScene();
        List<PowerChain> powerChains = scene != null ? scene.getPowerChains() : List.of();
        List<SignalChain> signalChains = scene != null ? scene.getSignalChains() : List.of();
        Graphics2D clipped = (Graphics2D) g2.create();
        clipped.clipRect((int) n.getX(), (int) n.getY(), nw, nh);
        SchemeRenderer.paintWiringDiagram(clipped, scr, t, mode == SchemaMode.POWER, cellW, cellH, left, top,
                model.getWorkspace(), powerChains, signalChains);
        if (barH > 0) {
            SchemeRenderer.drawControllerSummaryBar(clipped, scr, model.getWorkspace(),
                    left, top + scr.getRows() * cellH + 2, availW);
        }
        clipped.dispose();
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
    private static List<SocketRect> computeSocketRects(List<PortEntry> ports, int x, int y, int w, int h) {
        List<SocketRect> rects = new ArrayList<>();
        int rowY = y + 34;
        int maxY = y + h - 4;
        for (PortEntry entry : ports) {
            if (rowY > maxY) {
                break;
            }
            boolean isIn = entry.port().getDirection() == PortDirection.IN;
            int dotX = isIn ? x + 6 : x + w - CONNECTOR_DOT_D - 6;
            int dotY = rowY - CONNECTOR_DOT_D;
            rects.add(new SocketRect(entry, isIn, dotX, dotY));
            rowY += CONNECTOR_ROW_H;
        }
        return rects;
    }

    /** Разъёмы узла построчно — гнёзда с цветной точкой по типу разъёма, входы
     *  слева, выходы справа (как в патч-панели): раньше вся комплектация сжималась
     *  в одну обрезаемую строку («2×CEE 63…»), теперь каждая группа разъёмов —
     *  своя строка, обрезается по ширине только САМА строка, а не всё вместе.
     *  Название карты в скобках — иначе одинаковые по типу разъёмы из РАЗНЫХ карт
     *  неразличимы на вид. Если включена «коммутация через гнёзда» — гнёзда ещё и
     *  кликабельны (см. {@link #socketAt}), наведённое подсвечивается белым кольцом. */
    private void drawConnectorRows(Graphics2D g2, SchemaNode node, List<PortEntry> ports, int x, int y, int w, int h) {
        List<SocketRect> rects = computeSocketRects(ports, x, y, w, h);
        String prevCardId = null;
        for (SocketRect r : rects) {
            CardPort port = r.entry().port();
            boolean hovered = hoveredSocket != null && hoveredSocket.node() == node && hoveredSocket.port() == port;
            g2.setColor(connectorColor(port.getConnectorType()));
            g2.fillOval(r.dotX(), r.dotY(), CONNECTOR_DOT_D, CONNECTOR_DOT_D);
            g2.setColor(hovered ? Color.WHITE : Color.BLACK);
            g2.setStroke(new BasicStroke(hovered ? 2f : 1f));
            int ring = hovered ? 2 : 0;
            g2.drawOval(r.dotX() - ring, r.dotY() - ring, CONNECTOR_DOT_D + ring * 2, CONNECTOR_DOT_D + ring * 2);

            // Название карты пишется только у ПЕРВОЙ строки её группы портов — если
            // подряд идут несколько строк одной и той же карты (например 2×HDMI +
            // 2×DisplayPort на одной карте видеовхода), повторять её название на
            // каждой строке избыточно и загромождает вид (см. Task #67). Группировка —
            // по id САМОЙ карты, а не по строке названия: у нескольких ЭКЗЕМПЛЯРОВ
            // одного шаблона (Task #59) название совпадает, но это разные карты —
            // сравнение по названию ошибочно "склеивало" второй экземпляр с первым,
            // оставляя его вовсе без подписи (баг, найденный пользователем визуально).
            String groupName = r.entry().groupName();
            String cardId = r.entry().cardId();
            boolean sameCardAsPrev = cardId != null && cardId.equals(prevCardId);
            String label = port.getCount() + "×" + port.getConnectorType()
                    + (groupName != null && !groupName.isEmpty() && !sameCardAsPrev ? " (" + groupName + ")" : "");
            prevCardId = cardId;
            int maxTextW = w - CONNECTOR_DOT_D - 16;
            g2.setColor(new Color(0, 0, 0, 190));
            String clipped = clipToWidth(g2, label, maxTextW);
            FontMetrics fm = g2.getFontMetrics();
            int textX = r.isIn() ? r.dotX() + CONNECTOR_DOT_D + 4 : r.dotX() - 4 - fm.stringWidth(clipped);
            g2.drawString(clipped, textX, r.dotY() + CONNECTOR_DOT_D);
        }
        if (rects.size() < ports.size()) {
            int remaining = ports.size() - rects.size();
            int hintY = rects.isEmpty() ? y + 34 : rects.get(rects.size() - 1).dotY() + CONNECTOR_DOT_D + CONNECTOR_ROW_H;
            g2.setColor(new Color(0, 0, 0, 150));
            g2.drawString("+" + remaining + " ещё…", x + 10, hintY);
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
     *  вызывающий код обычно откатывается к привязке от узла целиком. */
    private Point socketPosition(SchemaNode node, String portId) {
        if (portId == null) {
            return null;
        }
        List<SocketRect> rects = computeSocketRects(portsOf(node), (int) node.getX(), (int) node.getY(),
                (int) node.getWidth(), (int) node.getHeight());
        for (SocketRect r : rects) {
            if (r.entry().port().getId().equals(portId)) {
                return new Point(r.centerX(), r.centerY());
            }
        }
        return null;
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
        for (SchemaNode n : nodes()) {
            List<PortEntry> ports = portsOf(n);
            if (ports.isEmpty()) {
                continue;
            }
            int nx = (int) n.getX(), ny = (int) n.getY(), nw = (int) n.getWidth(), nh = (int) n.getHeight();
            if (p.x < nx || p.x > nx + nw) {
                continue;
            }
            List<SocketRect> rects = computeSocketRects(ports, nx, ny, nw, nh);
            for (SocketRect r : rects) {
                int rowTop = r.dotY() - SOCKET_ROW_HIT_PAD;
                int rowBottom = r.dotY() + CONNECTOR_DOT_D + SOCKET_ROW_HIT_PAD;
                if (p.y >= rowTop && p.y <= rowBottom) {
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
        if (len < 1) {
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
            default -> new Color(0xc0c8d0);
        };
    }
}
