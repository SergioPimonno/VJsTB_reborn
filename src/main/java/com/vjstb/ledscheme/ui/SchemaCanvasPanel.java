package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
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

    private static final int NODE_W = 150;
    private static final int NODE_H = 56;
    private static final int MARGIN = 40;

    private final AppModel model;
    private final SchemaMode mode;
    private Runnable onChanged = () -> { };
    private Consumer<Screen> onScreenActivated;

    public enum Interaction { MOVE, CONNECT }

    private Interaction interaction = Interaction.MOVE;
    private SchemaNode dragNode;
    private double dragOffX, dragOffY;
    private String connectPendingId;
    private Point lastMouse;

    private SchemaNode selectedNode;
    private SchemaEdge selectedEdge;

    public SchemaCanvasPanel(AppModel model, SchemaMode mode) {
        this.model = model;
        this.mode = mode;
        setBackground(Palette.BG);
        setFocusable(true);

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
                    if (hit == null) {
                        connectPendingId = null;
                    } else if (connectPendingId == null) {
                        connectPendingId = hit.getId();
                    } else if (connectPendingId.equals(hit.getId())) {
                        connectPendingId = null;
                    } else {
                        try {
                            model.addSchemaEdge(mode, connectPendingId, hit.getId(), null);
                        } catch (RuntimeException ex) {
                            JOptionPane.showMessageDialog(SchemaCanvasPanel.this, ex.getMessage(),
                                    "Ошибка", JOptionPane.ERROR_MESSAGE);
                        }
                        connectPendingId = null;
                        onChanged.run();
                    }
                    repaint();
                    return;
                }
                if (hit != null) {
                    selectedNode = hit;
                    selectedEdge = null;
                    dragNode = hit;
                    dragOffX = e.getX() - hit.getX();
                    dragOffY = e.getY() - hit.getY();
                } else {
                    selectedNode = null;
                    selectedEdge = edgeAt(e.getPoint());
                }
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragNode != null) {
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
                if (interaction == Interaction.CONNECT && connectPendingId != null) {
                    lastMouse = e.getPoint();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragNode != null) {
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
            if (p.x >= n.getX() && p.x <= n.getX() + NODE_W && p.y >= n.getY() && p.y <= n.getY() + NODE_H) {
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

    private SchemaEdge edgeAt(Point p) {
        for (SchemaEdge edge : edges()) {
            SchemaNode a = nodeById(edge.getFromNodeId());
            SchemaNode b = nodeById(edge.getToNodeId());
            if (a == null || b == null) {
                continue;
            }
            double ax = a.getX() + NODE_W / 2.0, ay = a.getY() + NODE_H / 2.0;
            double bx = b.getX() + NODE_W / 2.0, by = b.getY() + NODE_H / 2.0;
            if (distanceToSegment(p.x, p.y, ax, ay, bx, by) < 6) {
                return edge;
            }
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
        label.addActionListener(ev -> {
            String input = JOptionPane.showInputDialog(this, "Подпись связи (например, тип кабеля):",
                    edge.getLabel() != null ? edge.getLabel() : "");
            if (input != null) {
                model.updateSchemaEdgeLabel(edge, input.trim().isEmpty() ? null : input.trim());
                onChanged.run();
                repaint();
            }
        });
        javax.swing.JMenuItem del = new javax.swing.JMenuItem("Удалить связь");
        del.addActionListener(ev -> {
            model.deleteSchemaEdge(edge);
            selectedEdge = null;
            onChanged.run();
            repaint();
        });
        menu.add(label);
        menu.add(del);
        menu.show(this, x, y);
    }

    @Override
    public Dimension getPreferredSize() {
        double maxX = 800, maxY = 500;
        for (SchemaNode n : nodes()) {
            maxX = Math.max(maxX, n.getX() + NODE_W + MARGIN);
            maxY = Math.max(maxY, n.getY() + NODE_H + MARGIN);
        }
        return new Dimension((int) maxX, (int) maxY);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

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
        Font edgeFont = getFont().deriveFont(10f);
        for (SchemaEdge edge : es) {
            SchemaNode a = nodeById(edge.getFromNodeId());
            SchemaNode b = nodeById(edge.getToNodeId());
            if (a == null || b == null) {
                continue;
            }
            boolean selected = edge == selectedEdge;
            g2.setColor(selected ? Palette.ACCENT : Palette.MUTED);
            g2.setStroke(new BasicStroke(selected ? 3f : 2f));
            double ax = a.getX() + NODE_W / 2.0, ay = a.getY() + NODE_H / 2.0;
            double bx = b.getX() + NODE_W / 2.0, by = b.getY() + NODE_H / 2.0;
            g2.drawLine((int) ax, (int) ay, (int) bx, (int) by);
            drawArrow(g2, ax, ay, bx, by);
            if (edge.getLabel() != null && !edge.getLabel().isEmpty()) {
                g2.setFont(edgeFont);
                g2.setColor(Palette.TEXT);
                g2.drawString(edge.getLabel(), (int) ((ax + bx) / 2) + 4, (int) ((ay + by) / 2) - 4);
            }
        }

        // превью соединения в режиме CONNECT
        if (interaction == Interaction.CONNECT && connectPendingId != null && lastMouse != null) {
            SchemaNode pending = nodeById(connectPendingId);
            if (pending != null) {
                g2.setColor(Palette.ACCENT);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0,
                        new float[]{5, 4}, 0));
                g2.drawLine((int) (pending.getX() + NODE_W / 2.0), (int) (pending.getY() + NODE_H / 2.0),
                        lastMouse.x, lastMouse.y);
            }
        }

        Font titleFont = getFont().deriveFont(Font.BOLD, 12f);
        Font metaFont = getFont().deriveFont(10f);
        for (SchemaNode n : ns) {
            boolean selected = n == selectedNode;
            boolean pending = n.getId().equals(connectPendingId);
            Color fill = nodeColor(n.getType());
            g2.setColor(fill);
            g2.fillRoundRect((int) n.getX(), (int) n.getY(), NODE_W, NODE_H, 10, 10);
            g2.setColor(pending ? Color.YELLOW : (selected ? Color.WHITE : Palette.BORDER));
            g2.setStroke(new BasicStroke(selected || pending ? 2.5f : 1.4f));
            g2.drawRoundRect((int) n.getX(), (int) n.getY(), NODE_W, NODE_H, 10, 10);

            String title = n.getType() == SchemaNodeType.SCREEN ? resolveScreenLabel(n) : n.getLabel();
            if (title == null || title.isEmpty()) {
                title = n.getType().getLabel();
            }
            g2.setColor(Color.BLACK);
            g2.setFont(titleFont);
            drawClipped(g2, title, (int) n.getX() + 8, (int) n.getY() + 20, NODE_W - 16);
            g2.setFont(metaFont);
            g2.setColor(new Color(0, 0, 0, 160));
            String meta = n.getType() == SchemaNodeType.SCREEN ? screenMeta(n) : n.getType().getLabel();
            drawClipped(g2, meta, (int) n.getX() + 8, (int) n.getY() + 38, NODE_W - 16);
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
        return mode == SchemaMode.POWER
                ? scr.getCols() + "×" + scr.getRows() + " каб."
                : "портов: " + model.effectiveSignalPortCount(scr);
    }

    private static void drawClipped(Graphics2D g2, String text, int x, int y, int maxWidth) {
        java.awt.FontMetrics fm = g2.getFontMetrics();
        String s = text;
        if (fm.stringWidth(s) > maxWidth) {
            while (s.length() > 1 && fm.stringWidth(s + "…") > maxWidth) {
                s = s.substring(0, s.length() - 1);
            }
            s = s + "…";
        }
        g2.drawString(s, x, y);
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
