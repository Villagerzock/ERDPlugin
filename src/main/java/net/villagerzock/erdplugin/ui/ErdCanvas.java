package net.villagerzock.erdplugin.ui;

import com.intellij.ui.JBColor;
import icons.DatabaseIcons;
import net.villagerzock.erdplugin.node.Attribute;
import net.villagerzock.erdplugin.node.Node;
import net.villagerzock.erdplugin.node.NodeGraph;
import net.villagerzock.erdplugin.util.Vector2;
import org.w3c.dom.Attr;

import javax.swing.*;
import javax.xml.crypto.Data;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import java.util.List;

public class ErdCanvas extends JComponent {
    private final NodeGraph model;
    private final ErdViewState view;
    private final ErdSelectionState selection;
    private final ErdEditorPanel panel;

    private Point lastMouse = null;
    private static Node selectedNode = null;
    private boolean draggingNode = false;
    private Point2D draggingSelectionFrom = null;
    private boolean panning = false;
    private NodeGraph.Connection hoveredConnection = null;

    private static Consumer<Node> selectedNodeChanged = (n) ->{};

    private ConnectionContext currentConnection;

    public void startConnection(NodeGraph.InternalConnectionType connectionType){
        currentConnection = new ConnectionContext(connectionType);
    }

    public static void setSelectedNodeChanged(Consumer<Node> selectedNodeChanged) {
        ErdCanvas.selectedNodeChanged = selectedNodeChanged;
    }

    public ErdCanvas(NodeGraph model, ErdViewState view, ErdSelectionState selection, ErdEditorPanel panel) {
        this.model = model;
        this.view = view;
        this.selection = selection;
        this.panel = panel;
        setFocusable(true);

        KeyAdapter key = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE){
                    model.deleteNode(selectedNode);
                }
            }
        };

        addKeyListener(key);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();

                lastMouse = e.getPoint();

                if (SwingUtilities.isMiddleMouseButton(e)){
                    panning = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }else if (SwingUtilities.isLeftMouseButton(e)){
                    Point2D world = screenToWorld(e.getPoint());
                    Node node = hitNode(world);

                    if (selectedNode != node)
                        selectedNodeChanged.accept(node);

                    selectedNode = node;
                    repaint();
                    if (node == null) {
                        draggingSelectionFrom = screenToWorld(e.getPoint());
                        return;
                    }

                    if (currentConnection == null){
                        draggingNode = true;
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    }else if (currentConnection.getFrom() == null){
                        currentConnection.setFrom(node);
                    }else {
                        runNewConnection(node);
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                panning = false;
                draggingNode = false;
                draggingSelectionFrom = null;
                setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastMouse == null) lastMouse = e.getPoint();
                int dx = e.getX() - lastMouse.x;
                int dy = e.getY() - lastMouse.y;

                if (panning){
                    view.panX += dx;
                    view.panY += dy;
                }else if (draggingNode){
                    selectedNode.getPosition().setLocation(selectedNode.getPosition().getX() + (dx / view.zoom), selectedNode.getPosition().getY() + (dy / view.zoom));

                    panel.changed();
                }

                lastMouse = e.getPoint();
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double oldZoom = view.zoom;;
                double factor = (e.getPreciseWheelRotation() < 0) ? 1.1 : (1.0 / 1.1);
                view.zoom = clamp(view.zoom * factor, 1, 6.5);

                Point p = e.getPoint();
                Point2D before = screenToWorld(p, oldZoom);
                Point2D after = screenToWorld(p);

                view.panX += (after.getX()-before.getX()) * view.zoom;
                view.panY += (after.getY()-before.getY()) * view.zoom;

                repaint();
                e.consume();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                NodeGraph.Connection con = getConnection(screenToWorld(e.getPoint()));;
                if (con != hoveredConnection){
                    hoveredConnection = con;
                    repaint();
                }
            }
        };

        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    private void runNewConnection(Node node) {
        switch (currentConnection.getType()){
            case OneToOne -> {
                for (Attribute pk : node.getPrimaryKeys()){
                    currentConnection.getFrom().addAttribute(new Attribute(node.getName()+"_"+pk.name(), pk.type(), false, false));
                    model.addConnection(new NodeGraph.Connection(
                            model.nodes().indexOf(currentConnection.getFrom()), node.getName()+"_"+pk.name(),
                            model.nodes().indexOf(node), pk.name(),
                            NodeGraph.ConnectionType.OneToOne
                    ));
                }
                for (Attribute pk : currentConnection.getFrom().getPrimaryKeys()){
                    node.addAttribute(new Attribute(currentConnection.getFrom().getName()+"_"+pk.name(), pk.type(), false, false));
                    model.addConnection(new NodeGraph.Connection(
                            model.nodes().indexOf(node), currentConnection.getFrom().getName()+"_"+pk.name(),
                            model.nodes().indexOf(currentConnection.getFrom()), pk.name(),
                            NodeGraph.ConnectionType.OneToOne
                    ));
                }
            }

            case OneToMany -> {
                // FK kommt auf die MANY-Seite: "node" ist hier die MANY-Seite (ein From hat viele node)
                for (Attribute pk : currentConnection.getFrom().getPrimaryKeys()){
                    node.addAttribute(new Attribute(currentConnection.getFrom().getName()+"_"+pk.name(), pk.type(), false, false));
                    model.addConnection(new NodeGraph.Connection(
                            model.nodes().indexOf(node), currentConnection.getFrom().getName()+"_"+pk.name(),
                            model.nodes().indexOf(currentConnection.getFrom()), pk.name(),
                            NodeGraph.ConnectionType.OneToMany
                    ));
                }
            }

            case ManyToOne -> {
                // Intern: MANY -> ONE
                // Normalisiert zu OneToMany: ONE hat viele MANY (also node hat viele from)
                // FK kommt auf die MANY-Seite: hier ist das currentConnection.getFrom() die MANY-Seite
                for (Attribute pk : node.getPrimaryKeys()){
                    currentConnection.getFrom().addAttribute(new Attribute(node.getName()+"_"+pk.name(), pk.type(), false, false));
                    model.addConnection(new NodeGraph.Connection(
                            model.nodes().indexOf(currentConnection.getFrom()), node.getName()+"_"+pk.name(),
                            model.nodes().indexOf(node), pk.name(),
                            NodeGraph.ConnectionType.OneToMany
                    ));
                }
            }
            default -> {
                return;
            }
        }
        panel.changed();
        currentConnection = null;
    }

    public static Node getSelectedNode() {
        return selectedNode;
    }

    public void fitToContent(){
        repaint();
    }

    public void zoomBy(double factor){
        view.zoom *= factor;
        repaint();
    }

    public Node hitNode(Point2D worldPos){
        for (int i = model.nodes().size()-1; i>=0;i--){
            Node node = model.nodes().get(i);
            if (
                    worldPos.getX() >= node.getPosition().getX()
                    && worldPos.getY() >= node.getPosition().getY()
                    && worldPos.getX() <= node.getPosition().getX() + node.getSize().x()
                    && worldPos.getY() <= node.getPosition().getY() + node.getSize().y()
            ){
                return node;
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(getBackground());
            g2.fillRect(0,0,getWidth(),getHeight());
            g2.translate(view.panX, view.panY);
            g2.scale(view.zoom, view.zoom);

            FontRenderContext frc = g2.getFontRenderContext();
            if (this.h == 0){
                FontMetrics fm = g2.getFontMetrics(g2.getFont());
                int h = fm.getHeight();
                this.h = h;
            }

            for (NodeGraph.Connection connection : model.connections()){
                Node from = model.nodes().get(connection.from());
                Attribute fromAttr = from.getAttributes().get(connection.fromAttr());
                int fromId = from.getAttributes().values().stream().toList().indexOf(fromAttr);

                Node to = model.nodes().get(connection.to());
                Attribute toAttr = to.getAttributes().get(connection.toAttr());
                int toId = to.getAttributes().values().stream().toList().indexOf(toAttr);

                if (from.getSize().x() == 0 || to.getSize().x() == 0) continue;

                int fromOff = -10+(h*(fromId+2) + (h/2));
                int toOff = -10+(h*(toId+2) + (h/2));

                drawConnection(from, to, g2, fromOff,toOff, connection == hoveredConnection);
            }

            for (Node node : model.nodes()){
                TextLayout l = new TextLayout(node.getName(), g2.getFont(), frc);
                int width = Math.max((int)l.getAdvance() + 4,10);

                for (Attribute attribute : node.getAttributes().values()){
                    String attributeText = String.format("%s %s",attribute.name(), attribute.type());
                    if (attributeText.isBlank()) continue;
                    TextLayout layout = new TextLayout(attributeText, g2.getFont(), frc);
                    float w = layout.getAdvance() + 4;
                    if (w > width){
                        width =(int) w;
                    }
                }

                node.setSize(new Vector2(width+20,h*(node.getAttributes().size() + 1)+20));

                g2.setColor(new JBColor(Color.LIGHT_GRAY.brighter(), Color.DARK_GRAY.darker()));
                g2.fillRoundRect((int) node.getPosition().getX(), (int) node.getPosition().getY(),node.getSize().x(),node.getSize().y(), 10,10);

                g2.setColor(new JBColor(Color.LIGHT_GRAY, Color.DARK_GRAY));
                g2.drawLine((int) node.getPosition().getX(), (int) (node.getPosition().getY() + h + 2), (int) (node.getPosition().getX() + node.getSize().x()), (int) (node.getPosition().getY() + h + 2));


                if (selectedNode == node){
                    g2.setColor(new JBColor(Color.LIGHT_GRAY.darker().darker(), Color.DARK_GRAY.brighter().brighter()));
                }else {
                    g2.setColor(new JBColor(Color.LIGHT_GRAY, Color.DARK_GRAY));
                }

                g2.drawRoundRect((int) node.getPosition().getX(), (int) node.getPosition().getY(),node.getSize().x(),node.getSize().y(), 10,10);

                g2.setColor(JBColor.BLACK);
                DatabaseIcons.Table.paintIcon(this, g2, (int) node.getPosition().getX()+2, (int) node.getPosition().getY()+2);
                g2.drawString(node.getName(), (int) node.getPosition().getX()+20, (int) node.getPosition().getY()+h-2);

                for (int i = 0; i< node.getAttributes().size(); i++){
                    Attribute attribute = node.getAttributes().values().toArray(Attribute[]::new)[i];
                    String attributeText = String.format("%s %s",attribute.name(), attribute.type());
                    boolean isForeignKey = model.isForeignKey(model.nodes().indexOf(node), attribute.name());
                    Icon icon = attribute.primaryKey() ? isForeignKey ? DatabaseIcons.ColGoldBlueKey : DatabaseIcons.ColGoldKey : isForeignKey ? DatabaseIcons.ColBlueKey : DatabaseIcons.Col;
                    int nodeIdx = model.nodes().indexOf(node);
                    if (hoveredConnection != null){
                        if ((nodeIdx == hoveredConnection.from() && Objects.equals(attribute.name(), hoveredConnection.fromAttr())) || (nodeIdx == hoveredConnection.to() && Objects.equals(attribute.name(), hoveredConnection.toAttr()))){
                            g2.setColor(JBColor.GREEN.darker());
                            g2.fillRect((int) node.getPosition().getX()+2,(int) node.getPosition().getY()-10+(h*(i+2)),node.getSize().x()-4,16);
                            g2.setColor(JBColor.BLACK);
                        }
                    }

                    icon.paintIcon(this, g2, (int) node.getPosition().getX()+2, (int) node.getPosition().getY()-10+(h*(i+2)));
                    g2.drawString(attributeText,(int) node.getPosition().getX()+20, (int) node.getPosition().getY()+(h*(i+2))+4);
                }
            }
        }finally {
            g2.dispose();
        }
    }

    private int h;

    /**
     * Findet die Connection unter einem bestimmten Punkt
     */
    public NodeGraph.Connection getConnection(Point2D point) {
        List<NodeGraph.Connection> connections = model.connections();
        List<Node> nodes = model.nodes();

        final double CLICK_TOLERANCE = 5.0;

        for (NodeGraph.Connection connection : connections) {
            Node from = nodes.get(connection.from());
            Node to = nodes.get(connection.to());

            // Überspringe wenn Nodes noch keine Größe haben
            if (from.getSize().x() == 0 || to.getSize().x() == 0) {
                continue;
            }

            // Berechne die GLEICHEN Offsets wie beim Zeichnen
            Attribute fromAttr = from.getAttributes().get(connection.fromAttr());
            int fromId = from.getAttributes().values().stream().toList().indexOf(fromAttr);

            Attribute toAttr = to.getAttributes().get(connection.toAttr());
            int toId = to.getAttributes().values().stream().toList().indexOf(toAttr);

            int fromOff = -10 + (h * (fromId + 2) + (h / 2));
            int toOff = -10 + (h * (toId + 2) + (h / 2));

            Point2D fromPos = from.getPosition();
            Point2D toPos = to.getPosition();
            Vector2 fromSize = from.getSize();
            Vector2 toSize = to.getSize();

            // Verwende die korrekten Offsets!
            Point2D start = getConnectionPoint(fromPos, fromSize, toPos, fromOff);
            Point2D end = getConnectionPoint(toPos, toSize, fromPos, toOff);

            List<Point2D> waypoints = calculateOrthogonalPath(
                    start, end, from, to, fromPos, fromSize, toPos, toSize
            );

            // Prüfe jedes Liniensegment
            for (int i = 0; i < waypoints.size() - 1; i++) {
                Point2D p1 = waypoints.get(i);
                Point2D p2 = waypoints.get(i + 1);

                if (isPointNearLine(point, p1, p2, CLICK_TOLERANCE)) {
                    return connection;
                }
            }
        }

        return null;
    }

    private boolean isPointNearLine(Point2D point, Point2D lineStart, Point2D lineEnd, double tolerance) {
        double x = point.getX();
        double y = point.getY();
        double x1 = lineStart.getX();
        double y1 = lineStart.getY();
        double x2 = lineEnd.getX();
        double y2 = lineEnd.getY();

        double dx = x2 - x1;
        double dy = y2 - y1;

        if (dx == 0 && dy == 0) {
            return point.distance(lineStart) <= tolerance;
        }

        double t = ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));

        double nearestX = x1 + t * dx;
        double nearestY = y1 + t * dy;

        double distance = Math.sqrt((x - nearestX) * (x - nearestX) + (y - nearestY) * (y - nearestY));

        return distance <= tolerance;
    }

    private static final double MARGIN = 20.0; // Abstand zu Nodes beim Ausweichen

    public void drawConnection(Node from, Node to, Graphics2D g2, int fromOff, int toOff, boolean hovered) {
        Point2D fromPos = from.getPosition();
        Vector2 fromSize = from.getSize();
        Point2D toPos = to.getPosition();
        Vector2 toSize = to.getSize();

        // Berechne Start- und Endpunkte an den Node-Rändern (nur links/rechts)
        Point2D start = getConnectionPoint(fromPos, fromSize, toPos, fromOff);
        Point2D end = getConnectionPoint(toPos, toSize, fromPos, toOff);

        // Berechne die Wegpunkte für die orthogonale Verbindung
        List<Point2D> waypoints = calculateOrthogonalPath(
                start, end, from, to, fromPos, fromSize, toPos, toSize
        );

        // Zeichne die Linien
        g2.setStroke(new BasicStroke(1.5f));
        for (int i = 0; i < waypoints.size() - 1; i++) {
            Point2D p1 = waypoints.get(i);
            Point2D p2 = waypoints.get(i + 1);
            g2.setColor(hovered ? JBColor.GREEN.darker() : JBColor.BLACK);
            g2.draw(new Line2D.Double(p1, p2));
        }
    }

    /**
     * Berechnet den Verbindungspunkt am linken oder rechten Rand eines Nodes
     */
    private Point2D getConnectionPoint(Point2D nodePos, Vector2 nodeSize,
                                       Point2D targetPos, int yOffset) {
        double nodeCenterX = nodePos.getX() + nodeSize.x() / 2;
        double targetCenterX = targetPos.getX();

        // Y-Position mit Offset
        double yPos = nodePos.getY() + yOffset;

        // Bestimme ob links oder rechts basierend auf der relativen Position
        if (targetCenterX > nodeCenterX) {
            // Rechte Seite
            return new Point2D.Double(
                    nodePos.getX() + nodeSize.x(),
                    yPos
            );
        } else {
            // Linke Seite
            return new Point2D.Double(
                    nodePos.getX(),
                    yPos
            );
        }
    }

    /**
     * Berechnet orthogonale Wegpunkte zwischen Start und Ziel
     */
    private List<Point2D> calculateOrthogonalPath(Point2D start, Point2D end,
                                                  Node from, Node to,
                                                  Point2D fromPos, Vector2 fromSize,
                                                  Point2D toPos, Vector2 toSize) {
        List<Point2D> waypoints = new ArrayList<>();
        waypoints.add(start);

        double startX = start.getX();
        double startY = start.getY();
        double endX = end.getX();
        double endY = end.getY();

        // Prüfe ob direkte horizontale Linie möglich ist
        if (Math.abs(startY - endY) < 1.0) {
            // Horizontale Linie
            waypoints.add(end);
            return waypoints;
        }

        // Berechne erweiterte Rechtecke für Kollisionserkennung
        Rectangle2D fromRect = new Rectangle2D.Double(
                fromPos.getX() - MARGIN,
                fromPos.getY() - MARGIN,
                fromSize.x() + 2 * MARGIN,
                fromSize.y() + 2 * MARGIN
        );

        Rectangle2D toRect = new Rectangle2D.Double(
                toPos.getX() - MARGIN,
                toPos.getY() - MARGIN,
                toSize.x() + 2 * MARGIN,
                toSize.y() + 2 * MARGIN
        );

        // Standard: Horizontale Linie vom Start, dann vertikal, dann horizontal zum Ziel
        double midX = (startX + endX) / 2;

        // Prüfe ob Mittellinie die Nodes schneidet und weiche aus
        if (intersectsVerticalLine(fromRect, midX) ||
                intersectsVerticalLine(toRect, midX)) {

            // Bestimme Ausweichrichtung basierend auf Startrichtung
            boolean startsRight = startX > fromPos.getX() + fromSize.x() / 2;

            if (startsRight) {
                // Von rechts kommend - weiche rechts aus
                midX = Math.max(
                        fromPos.getX() + fromSize.x() + MARGIN,
                        toPos.getX() + toSize.x() + MARGIN
                );
            } else {
                // Von links kommend - weiche links aus
                midX = Math.min(
                        fromPos.getX() - MARGIN,
                        toPos.getX() - MARGIN
                );
            }
        }

        // Füge Wegpunkte hinzu: horizontal -> vertikal -> horizontal
        waypoints.add(new Point2D.Double(midX, startY));
        waypoints.add(new Point2D.Double(midX, endY));
        waypoints.add(end);

        return waypoints;
    }

    private boolean intersectsVerticalLine(Rectangle2D rect, double x) {
        return x >= rect.getMinX() && x <= rect.getMaxX();
    }


    private Point2D screenToWorld(Point point) {
        return screenToWorld(point, view.zoom);
    }
    private Point2D screenToWorld(Point p, double z){
        double wx = (p.x - view.panX) / z;
        double wy = (p.y - view.panY) / z;
        return new Point2D.Double(wx,wy);
    }
    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
