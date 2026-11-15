import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Turns the diagram descriptions in this folder (*.uml) into two files each: an editable
 * diagrams.net file (.drawio) and an SVG preview (.svg), both written to the parent folder.
 * Both outputs come from the same description, so they always show the same diagram.
 *
 * Run from the project root:  java docs/uml/src/UmlGenerator.java
 *
 * Description format, one element per line, fields separated by '|', ';' is a line break in text:
 *   diagram|file-name|title|width|height
 *   actor|id|x|y|label                      usecase|id|x|y|w|h|label
 *   frame|id|x|y|w|h|label                  class|id|x|y|w|h|title|member;member;...
 *   box|id|x|y|w|h|label                    note|id|x|y|w|h|text
 *   state|id|x|y|w|h|label                  start|id|x|y          end|id|x|y
 *   node|id|x|y|w|h|label                   lifeline|id|x|y|w|h|label
 *   edge|from|to|label|kind                 kind: assoc, arrow, dashed, inherit
 *   msg|from|to|y|label|kind                kind: call, return, self
 */
public class UmlGenerator {

    record Shape(String kind, String id, double x, double y, double w, double h, String label, String members) {
        double cx() {
            return x + w / 2;
        }

        double cy() {
            return y + h / 2;
        }
    }

    record Edge(String from, String to, String label, String kind) {
    }

    record Message(String from, String to, double y, String label, String kind) {
    }

    private static final String FONT = "font-family=\"Segoe UI, Helvetica, Arial, sans-serif\" font-size=\"12\"";
    private static final int LIFELINE_HEAD = 40;

    private String fileName;
    private String title;
    private int width;
    private int height;
    private final Map<String, Shape> shapes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private final List<Message> messages = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        Path source = Path.of("docs", "uml", "src");
        Path target = Path.of("docs", "uml");
        List<Path> descriptions;
        try (Stream<Path> files = Files.list(source)) {
            descriptions = files.filter(file -> file.toString().endsWith(".uml")).sorted().toList();
        }
        for (Path description : descriptions) {
            UmlGenerator generator = new UmlGenerator();
            generator.read(description);
            Files.writeString(target.resolve(generator.fileName + ".drawio"), generator.toDrawio(),
                    StandardCharsets.UTF_8);
            Files.writeString(target.resolve(generator.fileName + ".svg"), generator.toSvg(), StandardCharsets.UTF_8);
            System.out.println("generated " + generator.fileName + ".drawio and .svg");
        }
    }

    private void read(Path description) throws IOException {
        for (String raw : Files.readAllLines(description, StandardCharsets.UTF_8)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\\|", -1);
            switch (f[0]) {
                case "diagram" -> {
                    fileName = f[1];
                    title = f[2];
                    width = Integer.parseInt(f[3]);
                    height = Integer.parseInt(f[4]);
                }
                case "actor" -> add(new Shape("actor", f[1], num(f[2]), num(f[3]), 40, 70, f[4], ""));
                case "start", "end" -> add(new Shape(f[0], f[1], num(f[2]), num(f[3]), 24, 24, "", ""));
                case "class" -> add(new Shape("class", f[1], num(f[2]), num(f[3]), num(f[4]), num(f[5]), f[6], f[7]));
                case "usecase", "frame", "box", "note", "state", "node", "lifeline" ->
                        add(new Shape(f[0], f[1], num(f[2]), num(f[3]), num(f[4]), num(f[5]), f[6], ""));
                case "edge" -> edges.add(new Edge(f[1], f[2], f[3], f[4]));
                case "msg" -> messages.add(new Message(f[1], f[2], num(f[3]), f[4], f[5]));
                default -> throw new IllegalArgumentException("Unknown element in " + description + ": " + line);
            }
        }
    }

    private void add(Shape shape) {
        if (shapes.put(shape.id(), shape) != null) {
            throw new IllegalArgumentException("Duplicate id " + shape.id());
        }
    }

    private Shape shape(String id) {
        Shape shape = shapes.get(id);
        if (shape == null) {
            throw new IllegalArgumentException("Unknown id " + id + " in " + fileName);
        }
        return shape;
    }

    private static double num(String text) {
        return Double.parseDouble(text.trim());
    }

    private static String xml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String n(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    // ---------------------------------------------------------------- diagrams.net output

    private String toDrawio() {
        StringBuilder out = new StringBuilder();
        out.append("<mxfile host=\"app.diagrams.net\" agent=\"UmlGenerator\" version=\"24.0.0\">\n");
        out.append("  <diagram id=\"").append(fileName).append("\" name=\"").append(xml(title)).append("\">\n");
        out.append("    <mxGraphModel dx=\"1200\" dy=\"800\" grid=\"1\" gridSize=\"10\" guides=\"1\" tooltips=\"1\" ")
                .append("connect=\"1\" arrows=\"1\" fold=\"1\" page=\"1\" pageScale=\"1\" pageWidth=\"").append(width)
                .append("\" pageHeight=\"").append(height).append("\" math=\"0\" shadow=\"0\">\n      <root>\n")
                .append("        <mxCell id=\"0\"/>\n        <mxCell id=\"1\" parent=\"0\"/>\n");
        for (Shape s : shapes.values()) {
            String value = switch (s.kind()) {
                case "class" -> classHtml(s);
                default -> s.label().replace(";", "<br/>");
            };
            out.append("        <mxCell id=\"").append(s.id()).append("\" value=\"").append(xml(value))
                    .append("\" style=\"").append(drawioStyle(s.kind())).append("\" vertex=\"1\" parent=\"1\">\n")
                    .append("          <mxGeometry x=\"").append(n(s.x())).append("\" y=\"").append(n(s.y()))
                    .append("\" width=\"").append(n(s.w())).append("\" height=\"").append(n(s.h()))
                    .append("\" as=\"geometry\"/>\n        </mxCell>\n");
        }
        int counter = 0;
        for (Edge e : edges) {
            shape(e.from());
            shape(e.to());
            String style = switch (e.kind()) {
                case "assoc" -> "endArrow=none;html=1;rounded=0;";
                case "arrow" -> "endArrow=open;endSize=10;html=1;rounded=0;";
                case "dashed" -> "endArrow=open;endSize=10;dashed=1;html=1;rounded=0;";
                case "inherit" -> "endArrow=block;endFill=0;endSize=14;html=1;rounded=0;";
                default -> throw new IllegalArgumentException("Unknown edge kind " + e.kind());
            };
            out.append("        <mxCell id=\"e").append(++counter).append("\" value=\"")
                    .append(xml(e.label().replace(";", "<br/>"))).append("\" style=\"").append(style)
                    .append("\" edge=\"1\" parent=\"1\" source=\"").append(e.from()).append("\" target=\"")
                    .append(e.to()).append("\">\n          <mxGeometry relative=\"1\" as=\"geometry\"/>\n")
                    .append("        </mxCell>\n");
        }
        for (Message m : messages) {
            double x1 = shape(m.from()).cx();
            double x2 = shape(m.to()).cx();
            String style = switch (m.kind()) {
                case "call", "self" -> "html=1;verticalAlign=bottom;endArrow=block;endFill=1;rounded=0;";
                case "return" -> "html=1;verticalAlign=bottom;endArrow=open;dashed=1;rounded=0;";
                default -> throw new IllegalArgumentException("Unknown message kind " + m.kind());
            };
            out.append("        <mxCell id=\"m").append(++counter).append("\" value=\"")
                    .append(xml(m.label().replace(";", "<br/>"))).append("\" style=\"").append(style)
                    .append("\" edge=\"1\" parent=\"1\">\n          <mxGeometry relative=\"1\" as=\"geometry\">\n")
                    .append("            <mxPoint x=\"").append(n(x1)).append("\" y=\"").append(n(m.y()))
                    .append("\" as=\"sourcePoint\"/>\n");
            if (m.kind().equals("self")) {
                out.append("            <mxPoint x=\"").append(n(x1)).append("\" y=\"").append(n(m.y() + 24))
                        .append("\" as=\"targetPoint\"/>\n            <Array as=\"points\">\n")
                        .append("              <mxPoint x=\"").append(n(x1 + 40)).append("\" y=\"").append(n(m.y()))
                        .append("\"/>\n              <mxPoint x=\"").append(n(x1 + 40)).append("\" y=\"")
                        .append(n(m.y() + 24)).append("\"/>\n            </Array>\n");
            } else {
                out.append("            <mxPoint x=\"").append(n(x2)).append("\" y=\"").append(n(m.y()))
                        .append("\" as=\"targetPoint\"/>\n");
            }
            out.append("          </mxGeometry>\n        </mxCell>\n");
        }
        out.append("      </root>\n    </mxGraphModel>\n  </diagram>\n</mxfile>\n");
        return out.toString();
    }

    private static String classHtml(Shape s) {
        StringBuilder html = new StringBuilder("<p style=\"margin:4px 0 0 0;text-align:center\">");
        String[] titleLines = s.label().split(";");
        for (int index = 0; index < titleLines.length; index++) {
            boolean name = index == titleLines.length - 1;
            html.append(name ? "<b>" : "<i>").append(titleLines[index]).append(name ? "</b>" : "</i><br/>");
        }
        html.append("</p><hr size=\"1\"/><p style=\"margin:0 0 0 6px\">");
        html.append(String.join("<br/>", s.members().split(";")));
        return html.append("</p>").toString();
    }

    private static String drawioStyle(String kind) {
        return switch (kind) {
            case "actor" -> "shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;";
            case "usecase" -> "ellipse;whiteSpace=wrap;html=1;fillColor=#ffffff;";
            case "frame" -> "rounded=0;whiteSpace=wrap;html=1;fillColor=none;verticalAlign=top;align=left;"
                    + "spacingLeft=8;fontStyle=1;";
            case "class" -> "verticalAlign=top;align=left;overflow=fill;fontSize=12;fontFamily=Helvetica;html=1;"
                    + "whiteSpace=wrap;fillColor=#ffffff;";
            case "box" -> "rounded=0;whiteSpace=wrap;html=1;fillColor=#f5f7fb;";
            case "note" -> "shape=note;whiteSpace=wrap;html=1;size=14;align=left;spacingLeft=6;fillColor=#fff8dc;";
            case "state" -> "rounded=1;whiteSpace=wrap;html=1;arcSize=30;fontStyle=1;fillColor=#ffffff;";
            case "start" -> "ellipse;fillColor=#000000;strokeColor=#000000;html=1;";
            case "end" -> "ellipse;html=1;shape=endState;fillColor=#000000;strokeColor=#000000;";
            case "node" -> "shape=cube;whiteSpace=wrap;html=1;boundedLbl=1;backgroundOutline=1;darkOpacity=0.05;"
                    + "darkOpacity2=0.1;size=12;verticalAlign=top;align=left;spacingLeft=6;fontStyle=1;"
                    + "fillColor=#eef2ff;";
            case "lifeline" -> "shape=umlLifeline;perimeter=lifelinePerimeter;whiteSpace=wrap;html=1;container=0;"
                    + "collapsible=0;recursiveResize=0;outlineConnect=0;size=" + LIFELINE_HEAD + ";";
            default -> throw new IllegalArgumentException("Unknown shape kind " + kind);
        };
    }

    // ---------------------------------------------------------------- SVG output

    private String toSvg() {
        StringBuilder out = new StringBuilder();
        out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width).append("\" height=\"")
                .append(height).append("\" viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\" role=\"img\" aria-label=\"").append(xml(title)).append("\">\n");
        out.append("<title>").append(xml(title)).append("</title>\n<defs>\n")
                .append("<marker id=\"open\" markerWidth=\"12\" markerHeight=\"10\" refX=\"11\" refY=\"5\" ")
                .append("orient=\"auto\"><path d=\"M1,1 L11,5 L1,9\" fill=\"none\" stroke=\"#111\" ")
                .append("stroke-width=\"1.2\"/></marker>\n")
                .append("<marker id=\"solid\" markerWidth=\"12\" markerHeight=\"10\" refX=\"11\" refY=\"5\" ")
                .append("orient=\"auto\"><path d=\"M1,1 L11,5 L1,9 Z\" fill=\"#111\"/></marker>\n")
                .append("<marker id=\"hollow\" markerWidth=\"16\" markerHeight=\"14\" refX=\"15\" refY=\"7\" ")
                .append("orient=\"auto\"><path d=\"M1,1 L15,7 L1,13 Z\" fill=\"#fff\" stroke=\"#111\"/></marker>\n")
                .append("</defs>\n<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");
        for (Shape s : shapes.values()) {
            if (s.kind().equals("frame") || s.kind().equals("node")) {
                svgShape(out, s);
            }
        }
        for (Shape s : shapes.values()) {
            if (!s.kind().equals("frame") && !s.kind().equals("node")) {
                svgShape(out, s);
            }
        }
        for (Edge e : edges) {
            svgEdge(out, e);
        }
        for (Message m : messages) {
            svgMessage(out, m);
        }
        return out.append("</svg>\n").toString();
    }

    private void svgShape(StringBuilder out, Shape s) {
        String box = "x=\"" + n(s.x()) + "\" y=\"" + n(s.y()) + "\" width=\"" + n(s.w()) + "\" height=\"" + n(s.h())
                + "\" stroke=\"#111\"";
        switch (s.kind()) {
            case "actor" -> {
                double cx = s.cx();
                out.append("<g stroke=\"#111\" fill=\"none\" stroke-width=\"1.4\"><circle cx=\"").append(n(cx))
                        .append("\" cy=\"").append(n(s.y() + 10)).append("\" r=\"9\" fill=\"#fff\"/>")
                        .append(line(cx, s.y() + 19, cx, s.y() + 46)).append(line(cx - 16, s.y() + 28, cx + 16,
                                s.y() + 28))
                        .append(line(cx, s.y() + 46, cx - 14, s.y() + 68)).append(line(cx, s.y() + 46, cx + 14,
                                s.y() + 68))
                        .append("</g>\n");
                text(out, s.label(), cx, s.y() + s.h() + 16, "middle", false);
            }
            case "usecase" -> {
                out.append("<ellipse cx=\"").append(n(s.cx())).append("\" cy=\"").append(n(s.cy())).append("\" rx=\"")
                        .append(n(s.w() / 2)).append("\" ry=\"").append(n(s.h() / 2))
                        .append("\" fill=\"#fff\" stroke=\"#111\"/>\n");
                centered(out, s.label(), s, false);
            }
            case "frame" -> {
                out.append("<rect ").append(box).append(" fill=\"none\"/>\n");
                text(out, s.label(), s.x() + 10, s.y() + 18, "start", true);
            }
            case "node" -> {
                out.append("<path d=\"M").append(n(s.x())).append(',').append(n(s.y())).append(" l12,-10 h")
                        .append(n(s.w())).append(" v").append(n(s.h())).append(" l-12,10\" fill=\"#dfe6f7\" ")
                        .append("stroke=\"#111\"/>\n<rect ").append(box).append(" fill=\"#eef2ff\"/>\n");
                text(out, s.label(), s.x() + 10, s.y() + 18, "start", true);
            }
            case "class" -> {
                out.append("<rect ").append(box).append(" fill=\"#fff\"/>\n");
                String[] titleLines = s.label().split(";");
                double y = s.y() + 17;
                for (int index = 0; index < titleLines.length; index++) {
                    boolean name = index == titleLines.length - 1;
                    out.append("<text x=\"").append(n(s.cx())).append("\" y=\"").append(n(y)).append("\" ")
                            .append(FONT).append(" text-anchor=\"middle\"")
                            .append(name ? " font-weight=\"bold\"" : " font-style=\"italic\"").append('>')
                            .append(xml(titleLines[index])).append("</text>\n");
                    y += 15;
                }
                double ruleY = y - 8;
                out.append(line(s.x(), ruleY, s.x() + s.w(), ruleY)).append('\n');
                text(out, s.members(), s.x() + 8, ruleY + 16, "start", false);
            }
            case "box" -> {
                out.append("<rect ").append(box).append(" fill=\"#f5f7fb\"/>\n");
                centered(out, s.label(), s, false);
            }
            case "note" -> {
                out.append("<path d=\"M").append(n(s.x())).append(',').append(n(s.y())).append(" h")
                        .append(n(s.w() - 14)).append(" l14,14 v").append(n(s.h() - 14)).append(" h")
                        .append(n(-s.w())).append(" Z\" fill=\"#fff8dc\" stroke=\"#111\"/>\n");
                text(out, s.label(), s.x() + 8, s.y() + 18, "start", false);
            }
            case "state" -> {
                out.append("<rect ").append(box).append(" rx=\"16\" fill=\"#fff\"/>\n");
                centered(out, s.label(), s, true);
            }
            case "start" -> out.append("<circle cx=\"").append(n(s.cx())).append("\" cy=\"").append(n(s.cy()))
                    .append("\" r=\"11\" fill=\"#111\"/>\n");
            case "end" -> out.append("<circle cx=\"").append(n(s.cx())).append("\" cy=\"").append(n(s.cy()))
                    .append("\" r=\"11\" fill=\"#fff\" stroke=\"#111\"/><circle cx=\"").append(n(s.cx()))
                    .append("\" cy=\"").append(n(s.cy())).append("\" r=\"7\" fill=\"#111\"/>\n");
            case "lifeline" -> {
                out.append("<rect x=\"").append(n(s.x())).append("\" y=\"").append(n(s.y())).append("\" width=\"")
                        .append(n(s.w())).append("\" height=\"").append(LIFELINE_HEAD)
                        .append("\" fill=\"#f5f7fb\" stroke=\"#111\"/>\n<line x1=\"").append(n(s.cx()))
                        .append("\" y1=\"").append(n(s.y() + LIFELINE_HEAD)).append("\" x2=\"").append(n(s.cx()))
                        .append("\" y2=\"").append(n(s.y() + s.h()))
                        .append("\" stroke=\"#555\" stroke-dasharray=\"6 4\"/>\n");
                centered(out, s.label(), new Shape("box", s.id(), s.x(), s.y(), s.w(), LIFELINE_HEAD, "", ""), true);
            }
            default -> throw new IllegalArgumentException("Unknown shape kind " + s.kind());
        }
    }

    private static String line(double x1, double y1, double x2, double y2) {
        return "<line x1=\"" + n(x1) + "\" y1=\"" + n(y1) + "\" x2=\"" + n(x2) + "\" y2=\"" + n(y2)
                + "\" stroke=\"#111\"/>";
    }

    private static void centered(StringBuilder out, String label, Shape s, boolean bold) {
        int lines = label.split(";").length;
        text(out, label, s.cx(), s.cy() - (lines - 1) * 7.5 + 4, "middle", bold);
    }

    private static void text(StringBuilder out, String label, double x, double y, String anchor, boolean bold) {
        String[] lines = label.split(";");
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].isEmpty()) {
                continue;
            }
            out.append("<text x=\"").append(n(x)).append("\" y=\"").append(n(y + index * 15)).append("\" ")
                    .append(FONT).append(" text-anchor=\"").append(anchor).append('"')
                    .append(bold ? " font-weight=\"bold\"" : "").append(" fill=\"#111\">").append(xml(lines[index]))
                    .append("</text>\n");
        }
    }

    /** The point where the straight line from the centre of the shape towards (tx, ty) leaves the shape. */
    private static double[] border(Shape s, double tx, double ty) {
        double dx = tx - s.cx();
        double dy = ty - s.cy();
        if (dx == 0 && dy == 0) {
            return new double[] {s.cx(), s.cy()};
        }
        double halfW = s.w() / 2;
        double halfH = s.kind().equals("actor") ? s.h() / 2 + 4 : s.h() / 2;
        double scale;
        if (s.kind().equals("usecase") || s.kind().equals("start") || s.kind().equals("end")) {
            scale = 1 / Math.sqrt(dx * dx / (halfW * halfW) + dy * dy / (halfH * halfH));
        } else {
            scale = Math.min(dx == 0 ? Double.MAX_VALUE : halfW / Math.abs(dx),
                    dy == 0 ? Double.MAX_VALUE : halfH / Math.abs(dy));
        }
        return new double[] {s.cx() + dx * scale, s.cy() + dy * scale};
    }

    private void svgEdge(StringBuilder out, Edge e) {
        Shape from = shape(e.from());
        Shape to = shape(e.to());
        double[] a = border(from, to.cx(), to.cy());
        double[] b = border(to, from.cx(), from.cy());
        String marker = switch (e.kind()) {
            case "assoc" -> "";
            case "arrow", "dashed" -> " marker-end=\"url(#open)\"";
            case "inherit" -> " marker-end=\"url(#hollow)\"";
            default -> throw new IllegalArgumentException("Unknown edge kind " + e.kind());
        };
        out.append("<line x1=\"").append(n(a[0])).append("\" y1=\"").append(n(a[1])).append("\" x2=\"")
                .append(n(b[0])).append("\" y2=\"").append(n(b[1])).append("\" stroke=\"#111\"")
                .append(e.kind().equals("dashed") ? " stroke-dasharray=\"6 4\"" : "").append(marker).append("/>\n");
        if (!e.label().isEmpty()) {
            label(out, e.label(), (a[0] + b[0]) / 2, (a[1] + b[1]) / 2 - 5);
        }
    }

    private void svgMessage(StringBuilder out, Message m) {
        double x1 = shape(m.from()).cx();
        double x2 = shape(m.to()).cx();
        boolean dashed = m.kind().equals("return");
        String marker = dashed ? "url(#open)" : "url(#solid)";
        if (m.kind().equals("self")) {
            out.append("<path d=\"M").append(n(x1)).append(',').append(n(m.y())).append(" h40 v24 h-40\" ")
                    .append("fill=\"none\" stroke=\"#111\" marker-end=\"").append(marker).append("\"/>\n");
            text(out, m.label(), x1 + 48, m.y() + 16, "start", false);
            return;
        }
        out.append("<line x1=\"").append(n(x1)).append("\" y1=\"").append(n(m.y())).append("\" x2=\"").append(n(x2))
                .append("\" y2=\"").append(n(m.y())).append("\" stroke=\"#111\"")
                .append(dashed ? " stroke-dasharray=\"6 4\"" : "").append(" marker-end=\"").append(marker)
                .append("\"/>\n");
        int lines = m.label().split(";").length;
        label(out, m.label(), (x1 + x2) / 2, m.y() - 6 - (lines - 1) * 15);
    }

    /** Text with a white halo so that it stays readable where it crosses a line. */
    private static void label(StringBuilder out, String label, double x, double y) {
        String[] lines = label.split(";");
        for (int index = 0; index < lines.length; index++) {
            out.append("<text x=\"").append(n(x)).append("\" y=\"").append(n(y + index * 15)).append("\" ")
                    .append(FONT).append(" text-anchor=\"middle\" fill=\"#111\" stroke=\"#fff\" ")
                    .append("stroke-width=\"4\" paint-order=\"stroke\">").append(xml(lines[index]))
                    .append("</text>\n");
        }
    }
}
