package net.villagerzock.erdplugin.ui;

import com.intellij.ui.JBColor;
import icons.DatabaseIcons;
import net.villagerzock.erdplugin.node.Attribute;
import net.villagerzock.erdplugin.node.INodeSelectable;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import java.util.List;

import static net.villagerzock.erdplugin.node.NodeGraph.*;

public class ErdCanvas extends JComponent {
    private final NodeGraph model;
    private final ErdViewState view;
    private final ErdSelectionState selection;
    private final ErdEditorPanel panel;

    private Point lastMouse = null;
    private static INodeSelectable selected = null;
    private boolean draggingNode = false;
    private Point2D draggingSelectionFrom = null;
    private boolean panning = false;
    private Connection hoveredConnection = null;

    private static Consumer<INodeSelectable> selectedNodeChanged = (n) ->{};

    private ConnectionContext currentConnection;

    public void startConnection(InternalConnectionType connectionType){
        currentConnection = new ConnectionContext(connectionType);
    }

    public static void setSelectedNodeChanged(Consumer<INodeSelectable> selectedNodeChanged) {
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
                    model.delete(selected);
                    selected = null;
                    selectedNodeChanged.accept(null);
                    panel.changed();
                    repaint();
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

                    if (selected != node)
                        selectedNodeChanged.accept(node);

                    selected = node;
                    repaint();
                    if (node == null) {
                        Connection connection = getConnection(world);
                        if (connection == null){
                            draggingSelectionFrom = world;
                            return;
                        }
                        draggingNode = true;
                        selected = connection;
                    }else {
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
                    if (selected instanceof Node selectedNode){
                        selectedNode.getPosition().setLocation(selectedNode.getPosition().getX() + (dx / view.zoom), selectedNode.getPosition().getY() + (dy / view.zoom));
                    } else if (selected instanceof Connection connection) {
                        Node from = model.nodes().get(connection.from());
                        Node to = model.nodes().get(connection.to());
                        from.getPosition().setLocation(from.getPosition().getX() + (dx / view.zoom), from.getPosition().getY() + (dy / view.zoom));
                        to.getPosition().setLocation(to.getPosition().getX() + (dx / view.zoom), to.getPosition().getY() + (dy / view.zoom));
                    }

                    panel.changed();
                }

                lastMouse = e.getPoint();
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double oldZoom = view.zoom;;
                double factor = (e.getPreciseWheelRotation() < 0) ? 1.1 : (1.0 / 1.1);
                view.zoom = clamp(view.zoom * factor, 0.3, 6.5);

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
                Connection con = getConnection(screenToWorld(e.getPoint()));;
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
                    currentConnection.getFrom().addAttribute(new Attribute(node.getName()+"_"+pk.name(), pk.type(), false, false,false,false, ""));
                    model.addConnection(new Connection(
                            model.nodes().indexOf(currentConnection.getFrom()), node.getName()+"_"+pk.name(),
                            model.nodes().indexOf(node), pk.name(),
                            ConnectionType.OneToOne
                    ));
                }
                for (Attribute pk : currentConnection.getFrom().getPrimaryKeys()){
                    node.addAttribute(new Attribute(currentConnection.getFrom().getName()+"_"+pk.name(), pk.type(), false, false, false, false, ""));
                    model.addConnection(new Connection(
                            model.nodes().indexOf(node), currentConnection.getFrom().getName()+"_"+pk.name(),
                            model.nodes().indexOf(currentConnection.getFrom()), pk.name(),
                            ConnectionType.OneToOne
                    ));
                }
            }

            case OneToMany -> {
                // FK kommt auf die MANY-Seite: "node" ist hier die MANY-Seite (ein From hat viele node)
                for (Attribute pk : currentConnection.getFrom().getPrimaryKeys()){
                    node.addAttribute(new Attribute(currentConnection.getFrom().getName()+"_"+pk.name(), pk.type(), false, false, false, false, ""));
                    model.addConnection(new Connection(
                            model.nodes().indexOf(node), currentConnection.getFrom().getName()+"_"+pk.name(),
                            model.nodes().indexOf(currentConnection.getFrom()), pk.name(),
                            ConnectionType.OneToMany
                    ));
                }
            }

            case ManyToOne -> {
                // Intern: MANY -> ONE
                // Normalisiert zu OneToMany: ONE hat viele MANY (also node hat viele from)
                // FK kommt auf die MANY-Seite: hier ist das currentConnection.getFrom() die MANY-Seite
                for (Attribute pk : node.getPrimaryKeys()){
                    currentConnection.getFrom().addAttribute(new Attribute(node.getName()+"_"+pk.name(), pk.type(), false, false, false, false, ""));
                    model.addConnection(new Connection(
                            model.nodes().indexOf(currentConnection.getFrom()), node.getName()+"_"+pk.name(),
                            model.nodes().indexOf(node), pk.name(),
                            ConnectionType.OneToMany
                    ));
                }
            }
            case ManyToMany -> {
                // Zwischen-Tabelle erstellen (Join-Table)
                Node from = currentConnection.getFrom();
                Node to = node;

                // Name: From_To (und falls schon existiert, mit Suffix eindeutiger machen)
                String baseName = from.getName() + "_" + to.getName();
                AtomicReference<String> joinName = new AtomicReference<>(baseName);
                int i = 1;
                while (model.nodes().stream().anyMatch(n -> n.getName().equals(joinName.get()))) {
                    joinName.set(baseName + "_" + (i++));
                }

                // Position: ungefähr zwischen den beiden Nodes (mit kleinem Y-Offset)
                Point2D fp = from.getPosition();
                Point2D tp = to.getPosition();
                Point2D joinPos = new Point2D.Double(
                        (fp.getX() + tp.getX()) / 2.0,
                        (fp.getY() + tp.getY()) / 2.0 + 80
                );

                Node join = new Node(
                        joinPos,
                        joinName.get(),
                        new java.util.HashMap<>(),
                        new Vector2(0, 0),
                        panel::changed
                );

                // Node ins Model hängen
                model.nodes().add(join);
                int joinIdx = model.nodes().indexOf(join);

                int fromIdx = model.nodes().indexOf(from);
                int toIdx = model.nodes().indexOf(to);

                // FK/PK Attribute von "from" in die Join-Tabelle + Connection (Join=MANY -> From=ONE)
                for (Attribute pk : from.getPrimaryKeys()) {
                    String fkName = from.getName() + "_" + pk.name(); // z.B. User_id
                    join.addAttribute(new Attribute(fkName, pk.type(), true, false, false, false, "")); // PK-Teil, NOT NULL

                    model.addConnection(new Connection(
                            joinIdx, fkName,
                            fromIdx, pk.name(),
                            ConnectionType.OneToMany
                    ));
                }

                // FK/PK Attribute von "to" in die Join-Tabelle + Connection (Join=MANY -> To=ONE)
                for (Attribute pk : to.getPrimaryKeys()) {
                    String fkName = to.getName() + "_" + pk.name(); // z.B. Role_id
                    join.addAttribute(new Attribute(fkName, pk.type(), true, false, false, false, "")); // PK-Teil, NOT NULL

                    model.addConnection(new Connection(
                            joinIdx, fkName,
                            toIdx, pk.name(),
                            ConnectionType.OneToMany
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
        return (selected instanceof Node node) ? node : null;
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

    private enum Side { LEFT, RIGHT }

    private Point2D edgePoint(Point2D pos, Vector2 size, int yOffset, Side side){
        double x = (side == Side.RIGHT) ? (pos.getX() + size.x()) : pos.getX();
        double y = pos.getY() + yOffset;
        return new Point2D.Double(x, y);
    }

    private double pathLength(List<Point2D> pts){
        double len = 0.0;
        for (int i = 0; i < pts.size() - 1; i++){
            len += pts.get(i).distance(pts.get(i + 1));
        }
        return len;
    }


    private Side[] chooseBestSidesForOverlap(
            Node from, Node to,
            Point2D fromPos, Vector2 fromSize, int fromOff,
            Point2D toPos, Vector2 toSize, int toOff,
            ConnectionIconType fromType, ConnectionIconType toType
    ){
        // expanded rects wie du sie beim Routing nutzt
        Rectangle2D fromRect = new Rectangle2D.Double(
                fromPos.getX() - MARGIN, fromPos.getY() - MARGIN,
                fromSize.x() + 2 * MARGIN, fromSize.y() + 2 * MARGIN
        );
        Rectangle2D toRect = new Rectangle2D.Double(
                toPos.getX() - MARGIN, toPos.getY() - MARGIN,
                toSize.x() + 2 * MARGIN, toSize.y() + 2 * MARGIN
        );

        Side bestStart = Side.RIGHT;
        Side bestEnd = Side.LEFT;
        double bestScore = Double.POSITIVE_INFINITY;

        for (Side sStart : Side.values()){
            for (Side sEnd : Side.values()){
                Point2D startEdge = edgePoint(fromPos, fromSize, fromOff, sStart);
                Point2D endEdge = edgePoint(toPos, toSize, toOff, sEnd);

                int dirStart = (sStart == Side.RIGHT) ? 1 : -1;
                int dirEnd = (sEnd == Side.RIGHT) ? 1 : -1;

                Point2D startSymbol = offsetX(startEdge, dirStart * getSymbolOffset(fromType));
                Point2D endSymbol = offsetX(endEdge, dirEnd * getSymbolOffset(toType));

                List<Point2D> pts = calculateOrthogonalPath(
                        startSymbol, endSymbol,
                        from, to, fromPos, fromSize, toPos, toSize
                );

                double score = pathLength(pts);

                // Penalty: wenn das erste Segment "zurück" läuft (macht dein |_0_|)
                if (pts.size() >= 2){
                    Point2D p0 = pts.get(0);
                    Point2D p1 = pts.get(1);
                    double dx = p1.getX() - p0.getX();
                    if (dirStart == 1 && dx < -0.01) score += 1_000_000;
                    if (dirStart == -1 && dx > 0.01) score += 1_000_000;
                }

                // Penalty: wenn ein Segment durch einen Node-Rect geht (nur als extra Sicherheit)
                for (int i = 0; i < pts.size() - 1; i++){
                    Line2D seg = new Line2D.Double(pts.get(i), pts.get(i + 1));
                    if (fromRect.intersectsLine(seg) || toRect.intersectsLine(seg)){
                        score += 100_000; // weniger hart als "backtrack"
                    }
                }

                if (score < bestScore){
                    bestScore = score;
                    bestStart = sStart;
                    bestEnd = sEnd;
                }
            }
        }

        return new Side[]{bestStart, bestEnd};
    }


    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            FontRenderContext frc = g2.getFontRenderContext();
            if (this.h == 0){
                FontMetrics fm = g2.getFontMetrics(g2.getFont());
                int h = fm.getHeight();
                this.h = h;
            }

            g2.setColor(getBackground());
            g2.fillRect(0,0,getWidth(),getHeight());
            g2.translate(view.panX, view.panY);
            g2.scale(view.zoom, view.zoom);

            for (Connection connection : model.connections()){
                Node from = model.nodes().get(connection.from());
                Attribute fromAttr = from.getAttributes().get(connection.fromAttr());
                int fromId = from.getAttributes().values().stream().toList().indexOf(fromAttr);

                Node to = model.nodes().get(connection.to());
                Attribute toAttr = to.getAttributes().get(connection.toAttr());
                int toId = to.getAttributes().values().stream().toList().indexOf(toAttr);

                if (from.getSize().x() == 0 || to.getSize().x() == 0) continue;

                int fromOff = -10+(h*(fromId+2) + (h/2));
                int toOff = -10+(h*(toId+2) + (h/2));

                drawConnection(from, to, g2, fromOff,toOff, connection == hoveredConnection, connection == selected, connection.type().getTypeFrom(fromAttr.nullable()), connection.type().getTypeTo(toAttr.nullable()));
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


                if (selected == node){
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
                    if (selected instanceof Connection connection){
                        if ((nodeIdx == connection.from() && Objects.equals(attribute.name(), connection.fromAttr())) || (nodeIdx == connection.to() && Objects.equals(attribute.name(), connection.toAttr()))){
                            g2.setColor(JBColor.RED.darker());
                            g2.fillRect((int) node.getPosition().getX()+2,(int) node.getPosition().getY()-10+(h*(i+2)),node.getSize().x()-4,16);
                            g2.setColor(JBColor.BLACK);
                        }
                    }else if (hoveredConnection != null){
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
    public Connection getConnection(Point2D point) {
        List<Connection> connections = model.connections();
        List<Node> nodes = model.nodes();

        final double CLICK_TOLERANCE = 2.0;

        for (Connection connection : connections) {
            Node from = nodes.get(connection.from());
            Node to = nodes.get(connection.to());

            if (from.getSize().x() == 0 || to.getSize().x() == 0) continue;

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

            ConnectionIconType fromType = connection.type().getTypeFrom(fromAttr.nullable());
            ConnectionIconType toType = connection.type().getTypeTo(toAttr.nullable());

            // === EXAKT die gleiche Side-Logik wie drawConnection ===
            double fromLeftX = fromPos.getX();
            double fromRightX = fromPos.getX() + fromSize.x();
            double toLeftX = toPos.getX();
            double toRightX = toPos.getX() + toSize.x();
            boolean overlapX = fromLeftX - MARGIN < toRightX + MARGIN && fromRightX + MARGIN > toLeftX - MARGIN;

            Side startSide, endSide;

            if (overlapX) {
                Side[] sides = chooseBestSidesForOverlap(
                        from, to,
                        fromPos, fromSize, fromOff,
                        toPos, toSize, toOff,
                        fromType, toType
                );
                startSide = sides[0];
                endSide = sides[1];
            } else {
                startSide = (toPos.getX() + toSize.x() / 2.0 > fromPos.getX() + fromSize.x() / 2.0) ? Side.RIGHT : Side.LEFT;
                endSide = (fromPos.getX() + fromSize.x() / 2.0 > toPos.getX() + toSize.x() / 2.0) ? Side.RIGHT : Side.LEFT;
            }

            Point2D startEdge = edgePoint(fromPos, fromSize, fromOff, startSide);
            Point2D endEdge = edgePoint(toPos, toSize, toOff, endSide);

            int dirStart = (startSide == Side.RIGHT) ? 1 : -1;
            int dirEnd = (endSide == Side.RIGHT) ? 1 : -1;

            // === WICHTIG: Hit-Test muss wie Zeichnen auf SYMBOL-Punkten basieren ===
            Point2D startSymbol = offsetX(startEdge, dirStart * getSymbolOffset(fromType));
            Point2D endSymbol = offsetX(endEdge, dirEnd * getSymbolOffset(toType));

            List<Point2D> waypoints = calculateOrthogonalPath(
                    startSymbol, endSymbol,
                    from, to, fromPos, fromSize, toPos, toSize
            );

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

    public enum ConnectionIconType {
        ONE,
        ZERO,
        MANY_ONE,
        MANY_ZERO
    }

    public void drawConnection(Node from, Node to, Graphics2D g2,
                               int fromOff, int toOff,
                               boolean hovered, boolean isSelected,
                               ConnectionIconType fromType, ConnectionIconType toType) {

        Point2D fromPos = from.getPosition();
        Vector2 fromSize = from.getSize();
        Point2D toPos = to.getPosition();
        Vector2 toSize = to.getSize();

// X-Overlap?
        double fromLeftX = fromPos.getX();
        double fromRightX = fromPos.getX() + fromSize.x();
        double toLeftX = toPos.getX();
        double toRightX = toPos.getX() + toSize.x();
        boolean overlapX = fromLeftX - MARGIN < toRightX + MARGIN && fromRightX + MARGIN > toLeftX - MARGIN;

        Side startSide, endSide;

        if (overlapX){
            Side[] sides = chooseBestSidesForOverlap(
                    from, to,
                    fromPos, fromSize, fromOff,
                    toPos, toSize, toOff,
                    fromType, toType
            );
            startSide = sides[0];
            endSide = sides[1];
        } else {
            // normal: Richtung zum Target-Center
            startSide = (toPos.getX() + toSize.x() / 2.0 > fromPos.getX() + fromSize.x() / 2.0) ? Side.RIGHT : Side.LEFT;
            endSide   = (fromPos.getX() + fromSize.x() / 2.0 > toPos.getX() + toSize.x() / 2.0) ? Side.RIGHT : Side.LEFT;
        }

        Point2D startEdge = edgePoint(fromPos, fromSize, fromOff, startSide);
        Point2D endEdge = edgePoint(toPos, toSize, toOff, endSide);

        int dirStart = (startSide == Side.RIGHT) ? 1 : -1;
        int dirEnd = (endSide == Side.RIGHT) ? 1 : -1;


        // Punkte wo die Linie "enden" soll: beim | / O
        Point2D startSymbol = offsetX(startEdge, dirStart * getSymbolOffset(fromType));
        Point2D endSymbol = offsetX(endEdge, dirEnd * getSymbolOffset(toType));

        // Route zwischen den Symbolpunkten berechnen (nicht bis zur Node-Kante)
        List<Point2D> waypoints = calculateOrthogonalPath(
                startSymbol, endSymbol, from, to, fromPos, fromSize, toPos, toSize
        );

        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(isSelected ? JBColor.RED.darker() : hovered ? JBColor.GREEN.darker() : JBColor.BLACK);

        // Hauptlinie zeichnen (endet beim Symbol)
        for (int i = 0; i < waypoints.size() - 1; i++) {
            Point2D p1 = waypoints.get(i);
            Point2D p2 = waypoints.get(i + 1);
            g2.draw(new Line2D.Double(p1, p2));
        }

        // End-Dekos: Node-Kante -> Symbol + Symbol selbst
        drawEndDecoration(g2, startEdge, startSymbol, toType, dirStart);
        drawEndDecoration(g2, endEdge, endSymbol, fromType, dirEnd);
    }

    private static Point2D offsetX(Point2D p, double dx){
        return new Point2D.Double(p.getX() + dx, p.getY());
    }

    private int getSymbolOffset(ConnectionIconType type){
        // Abstand von Node-Kante bis |/O
        // (bei MANY_* ist |/O der "Tip", wo der Crowfoot hinläuft)
        return 10;
    }

    private void drawEndDecoration(Graphics2D g2, Point2D edge, Point2D symbol, ConnectionIconType type, int dir){
        if (type == null) return;

        int y = (int) Math.round(edge.getY());

        // Viele: Crowfoot von Node-Kante zum |/O (symbol)
        if (type == ConnectionIconType.MANY_ONE || type == ConnectionIconType.MANY_ZERO){
            drawCrowFootFromEdgeToSymbol(g2, edge, symbol, dir);
        } else {
            // One/Zero: kleiner Stub von Node-Kante zum Symbol
            g2.draw(new Line2D.Double(edge, symbol));
        }

        // Jetzt | oder O am Symbol zeichnen
        switch (type){
            case ONE, MANY_ONE -> drawOneBar(g2, (int)Math.round(symbol.getX()), y, 6);
            case ZERO, MANY_ZERO -> drawZeroCircle(g2, (int)Math.round(symbol.getX()), y, 4);
            default -> {}
        }
    }

    private void drawOneBar(Graphics2D g2, int x, int y, int halfH){
        g2.drawLine(x, y - halfH, x, y + halfH);
    }

    private void drawZeroCircle(Graphics2D g2, int x, int y, int r){
        g2.drawOval(x - r, y - r, r * 2, r * 2);
    }

    /**
     * Crowfoot startet an der Node-Kante (edge) und "läuft" zum |/O (symbol).
     * Das sind genau die zwei 45° Linien.
     */
    private void drawCrowFootFromEdgeToSymbol(Graphics2D g2, Point2D edge, Point2D symbol, int dir){
        int halfH = 6;

        int xEdge = (int) Math.round(edge.getX());
        int y = (int) Math.round(edge.getY());

        int xTip = (int) Math.round(symbol.getX());
        int yTip = y;

        // 1) mittlere Linie (Stamm) von Node-Kante zum Tip (damit es 3 Striche sind)
        g2.drawLine(xEdge, y, xTip, yTip);

        // 2) zwei 45°-Linien: Öffnung an der Node, Spitze am Tip
        g2.drawLine(xEdge, y - halfH, xTip, yTip);
        g2.drawLine(xEdge, y + halfH, xTip, yTip);
    }



    private boolean isOnRightEdge(Point2D p, Point2D nodePos, Vector2 nodeSize){
        double rightX = nodePos.getX() + nodeSize.x();
        double leftX = nodePos.getX();
        double eps = 0.5;
        return Math.abs(p.getX() - rightX) <= Math.abs(p.getX() - leftX) + eps;
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
