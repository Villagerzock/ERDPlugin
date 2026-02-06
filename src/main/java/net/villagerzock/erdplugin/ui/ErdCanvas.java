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
import java.awt.geom.Point2D;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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

            FontMetrics fm = g2.getFontMetrics(g2.getFont());
            int h = fm.getHeight();

            for (NodeGraph.Connection connection : model.connections()){
                Node from = model.nodes().get(connection.from());

                Node to = model.nodes().get(connection.to());

                if (from.getSize().x() == 0 || to.getSize().x() == 0) continue;

                boolean right = (from.getPosition().getX() + from.getSize().x() / 2f) < (to.getPosition().getX() + to.getSize().x() / 2f);

                int fromX = (int) (right ? from.getPosition().getX() + from.getSize().x() : from.getPosition().getX());
                int fromY = (int) (from.getPosition().getY() + from.getSize().y() / 2f);

                right = (to.getPosition().getX() + to.getSize().x() / 2f) < (from.getPosition().getX() + from.getSize().x() / 2f);

                int toX = (int) (right ? to.getPosition().getX() + to.getSize().x() : to.getPosition().getX());
                int toY = (int) (to.getPosition().getY() + to.getSize().y() / 2f);

                g2.setColor(JBColor.BLACK);
                g2.drawLine(fromX,fromY, toX,toY);
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
                    icon.paintIcon(this, g2, (int) node.getPosition().getX()+2, (int) node.getPosition().getY()-10+(h*(i+2)));
                    g2.drawString(attributeText,(int) node.getPosition().getX()+20, (int) node.getPosition().getY()+(h*(i+2))+4);
                }
            }
        }finally {
            g2.dispose();
        }
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
