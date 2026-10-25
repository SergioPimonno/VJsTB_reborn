package com.vjstb.ledscheme.service.graphimport;

import com.vjstb.ledscheme.service.graphimport.ImportedGraph.GraphEdge;
import com.vjstb.ledscheme.service.graphimport.ImportedGraph.GraphNode;
import com.vjstb.ledscheme.service.graphimport.ImportedGraph.Kind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Читатель yEd {@code .graphml} (формат yFiles: {@code y:ShapeNode}, {@code y:ImageNode}, {@code y:GroupNode},
 * {@code y:PolyLineEdge}). Берёт только то, что нужно для переноса схемы: подпись, положение, размер, цвет заливки
 * блока; подпись, цвет, пунктир и стрелки связи. Вложенные группы (папки) раскрываются: блок внутри группы получает
 * {@code parentId}, а его координаты в yEd УЖЕ абсолютные (проверено на файлах пользователя: дети группы «FOH»
 * лежат внутри её рамки без сдвига).
 *
 * <p>Картинки, вшитые в файл (самая тяжёлая часть — base64 в {@code y:Resources}), не читаются. Внешние сущности и
 * DTD в разборе отключены: файл приходит от пользователя из любого источника.
 */
public final class GraphMlReader {

    private static final String YNS = "http://www.yworks.com/xml/graphml";

    private GraphMlReader() {
    }

    public static ImportedGraph read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            String name = file.getFileName().toString();
            int dot = name.lastIndexOf('.');
            return read(in, dot > 0 ? name.substring(0, dot) : name);
        }
    }

    public static ImportedGraph read(InputStream in, String name) throws IOException {
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            doc = f.newDocumentBuilder().parse(in);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Не удалось прочитать .graphml: " + e.getMessage(), e);
        }
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        collect(doc.getDocumentElement(), null, nodes, edges);
        return new ImportedGraph(name, nodes, edges);
    }

    /** Обходит элемент: {@code node} (с вложенным {@code graph} — группа), {@code edge}; прочее — вглубь. */
    private static void collect(Element parent, String parentId, List<GraphNode> nodes, List<GraphEdge> edges) {
        for (Node c = parent.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (!(c instanceof Element e)) {
                continue;
            }
            String tag = e.getLocalName();
            if ("node".equals(tag)) {
                nodes.add(readNode(e, parentId));
                for (Element sub : children(e, "graph")) {
                    collect(sub, e.getAttribute("id"), nodes, edges);
                }
            } else if ("edge".equals(tag)) {
                edges.add(readEdge(e));
            } else if ("graph".equals(tag) || "graphml".equals(tag)) {
                collect(e, parentId, nodes, edges);
            }
        }
    }

    private static GraphNode readNode(Element node, String parentId) {
        Kind kind = Kind.EQUIPMENT;
        Element gfx = null;
        for (Element data : children(node, "data")) {
            Element real = firstYChild(data);
            if (real == null) {
                continue;
            }
            String t = real.getLocalName();
            if ("ProxyAutoBoundsNode".equals(t)) {
                Element inner = descendantY(real, "GroupNode");
                gfx = inner != null ? inner : real;
                kind = Kind.GROUP;
            } else if ("GroupNode".equals(t)) {
                gfx = real;
                kind = Kind.GROUP;
            } else if ("ImageNode".equals(t) || "SVGNode".equals(t)) {
                gfx = real;
                kind = Kind.IMAGE;
            } else if ("ShapeNode".equals(t) || "GenericNode".equals(t) || "TableNode".equals(t)) {
                gfx = real;
            }
            if (gfx != null) {
                break;
            }
        }
        double x = 0;
        double y = 0;
        double w = 80;
        double h = 30;
        Integer fill = null;
        String label = "";
        if (gfx != null) {
            Element geo = descendantY(gfx, "Geometry");
            if (geo != null) {
                x = num(geo, "x", 0);
                y = num(geo, "y", 0);
                w = num(geo, "width", 80);
                h = num(geo, "height", 30);
            }
            Element fillEl = descendantY(gfx, "Fill");
            if (fillEl != null) {
                fill = rgb(fillEl.getAttribute("color"));
            }
            label = joinLabels(gfx, " ", "NodeLabel");
        }
        return new GraphNode(node.getAttribute("id"), parentId, kind, label, x, y, w, h, fill);
    }

    private static GraphEdge readEdge(Element edge) {
        Element gfx = null;
        for (Element data : children(edge, "data")) {
            Element real = firstYChild(data);
            if (real != null && real.getLocalName().endsWith("Edge")) {
                gfx = real;
                break;
            }
        }
        Integer color = null;
        boolean dashed = false;
        boolean arrowSource = false;
        boolean arrowTarget = false;
        String label = "";
        if (gfx != null) {
            Element ls = descendantY(gfx, "LineStyle");
            if (ls != null) {
                color = rgb(ls.getAttribute("color"));
                String type = ls.getAttribute("type");
                dashed = type != null && !type.isEmpty() && !"line".equals(type);
            }
            Element arrows = descendantY(gfx, "Arrows");
            if (arrows != null) {
                arrowSource = isArrow(arrows.getAttribute("source"));
                arrowTarget = isArrow(arrows.getAttribute("target"));
            }
            label = joinLabels(gfx, " / ", "EdgeLabel");
        }
        return new GraphEdge(edge.getAttribute("source"), edge.getAttribute("target"), label, color, dashed,
                arrowSource, arrowTarget);
    }

    private static boolean isArrow(String v) {
        return v != null && !v.isEmpty() && !"none".equals(v);
    }

    /** Подписи ({@code y:NodeLabel}/{@code y:EdgeLabel}) одним текстом: строки подписи и несколько подписей — через
     *  {@code sep}, пустые пропускаются. */
    private static String joinLabels(Element gfx, String sep, String labelTag) {
        List<String> parts = new ArrayList<>();
        collectLabels(gfx, labelTag, parts);
        return String.join(sep, parts);
    }

    private static void collectLabels(Element e, String labelTag, List<String> out) {
        for (Node c = e.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (!(c instanceof Element el)) {
                continue;
            }
            if (YNS.equals(el.getNamespaceURI()) && labelTag.equals(el.getLocalName())) {
                for (String line : el.getTextContent().split("\\R")) {
                    String t = line.trim();
                    if (!t.isEmpty()) {
                        out.add(t);
                    }
                }
            } else {
                collectLabels(el, labelTag, out);
            }
        }
    }

    // ---- DOM-помощники ----

    private static List<Element> children(Element e, String localName) {
        List<Element> out = new ArrayList<>();
        for (Node c = e.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element el && localName.equals(el.getLocalName())) {
                out.add(el);
            }
        }
        return out;
    }

    /** Первый дочерний элемент пространства имён yFiles (содержимое {@code data}); {@code Realizers} раскрывается. */
    private static Element firstYChild(Element data) {
        for (Node c = data.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element el && YNS.equals(el.getNamespaceURI())) {
                if ("Realizers".equals(el.getLocalName())) {
                    Element inner = firstYChild(el);
                    if (inner != null) {
                        return inner;
                    }
                    continue;
                }
                return el;
            }
        }
        return null;
    }

    private static Element descendantY(Element e, String localName) {
        for (Node c = e.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (!(c instanceof Element el)) {
                continue;
            }
            if (YNS.equals(el.getNamespaceURI()) && localName.equals(el.getLocalName())) {
                return el;
            }
            Element deeper = descendantY(el, localName);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }

    private static double num(Element e, String attr, double dflt) {
        try {
            return Double.parseDouble(e.getAttribute(attr));
        } catch (NumberFormatException ex) {
            return dflt;
        }
    }

    /** {@code #RRGGBB} → RGB; прозрачный/пустой/неразобранный — {@code null}. */
    static Integer rgb(String hex) {
        if (hex == null || hex.length() != 7 || hex.charAt(0) != '#') {
            return null;
        }
        try {
            return Integer.parseInt(hex.substring(1), 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
