package net.villagerzock.erdplugin.ui;

import com.intellij.ui.JBColor;
import icons.DatabaseIcons;
import net.villagerzock.erdplugin.node.Attribute;
import net.villagerzock.erdplugin.node.INodeSelectable;
import net.villagerzock.erdplugin.node.Node;
import net.villagerzock.erdplugin.node.NodeGraph;
import net.villagerzock.erdplugin.util.Vector2;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class GraphRenderer {

    private static final double MARGIN = 20.0;

    private final NodeGraph model;
    private final JComponent componentForIcons; // für Icon.paintIcon(...)
    private INodeSelectable selected;
    private NodeGraph.Connection hoveredConnection;

    private int fontH;

    public GraphRenderer(NodeGraph model, JComponent componentForIcons) {
        this.model = model;
        this.componentForIcons = componentForIcons;
    }

    public void setSelected(INodeSelectable selected) {
        this.selected = selected;
    }

    public void setHoveredConnection(NodeGraph.Connection hoveredConnection) {
        this.hoveredConnection = hoveredConnection;
    }

    /**
     * bounds = WORLD rectangle, der gerendert werden soll.
     * Das Rendering wird so transformiert, dass bounds in die Clip-Fläche von g passt.
     * (Für Canvas: bounds = worldViewport; Für Export: bounds = model.getBounds() + padding)
     */
    public void paintGraph(Graphics g, Rectangle2D bounds, boolean withBackground) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Rectangle clip = g2.getClipBounds();
            if (clip == null) {
                // Fallback: falls kein Clip gesetzt ist, nehmen wir bounds als Zielgröße (kommt bei Images selten vor,
                // aber sicher ist sicher)
                clip = new Rectangle(0, 0, (int) Math.ceil(bounds.getWidth()), (int) Math.ceil(bounds.getHeight()));
                g2.setClip(clip);
            }

            FontRenderContext frc = g2.getFontRenderContext();
            if (fontH == 0) {
                FontMetrics fm = g2.getFontMetrics(g2.getFont());
                fontH = fm.getHeight();
            }


            if (withBackground){
                // Background (screen space)
                g2.setColor(componentForIcons.getBackground());
                g2.fillRect(clip.x, clip.y, clip.width, clip.height);

                // World -> Screen transform so, dass "bounds" in clip passt
                AffineTransform oldTx = g2.getTransform();
                applyWorldToClipTransform(g2, bounds, clip);

                // Grid in WORLD coords (nach transform)
                drawGrid(g2, bounds);
            }

            // Connections
            for (NodeGraph.Connection connection : model.connections()) {
                Node from = connection.from();
                Node to = connection.to();

                if (from.getSize().x() == 0 || to.getSize().x() == 0) continue;

                Attribute fromAttr = from.getAttributes().get(connection.fromAttr());
                Attribute toAttr = to.getAttributes().get(connection.toAttr());
                if (fromAttr == null || toAttr == null) continue;

                int fromId = from.getAttributes().values().stream().toList().indexOf(fromAttr);
                int toId = to.getAttributes().values().stream().toList().indexOf(toAttr);

                int fromOff = -10 + (fontH * (fromId + 2) + (fontH / 2));
                int toOff = -10 + (fontH * (toId + 2) + (fontH / 2));

                drawConnection(
                        from, to, g2,
                        fromOff, toOff,
                        connection == hoveredConnection,
                        INodeSelectable.isSelected(selected, connection),
                        connection.type().getTypeFrom(fromAttr.nullable()),
                        connection.type().getTypeTo(toAttr.nullable())
                );
            }

            // Nodes
            for (Node node : model.nodes()) {
                renderNode(g2, frc, node);
            }

        } finally {
            g2.dispose();
        }
    }

    private static void applyWorldToClipTransform(Graphics2D g2, Rectangle2D world, Rectangle clip) {
        double sx = clip.getWidth() / world.getWidth();
        double sy = clip.getHeight() / world.getHeight();

        // uniform scale (sollte bei Canvas sowieso gleich sein)
        double s = Math.min(sx, sy);

        // center if aspect differs
        double extraX = (clip.getWidth() - world.getWidth() * s) / 2.0;
        double extraY = (clip.getHeight() - world.getHeight() * s) / 2.0;

        AffineTransform tx = new AffineTransform();
        tx.translate(clip.getX() + extraX, clip.getY() + extraY);
        tx.scale(s, s);
        tx.translate(-world.getX(), -world.getY());

        g2.transform(tx);
    }

    private void drawGrid(Graphics2D g2, Rectangle2D worldBounds) {
        int spacing = 50;

        // alpha “gefühlt” wie vorher: je weiter rausgezoomt, desto transparenter
        // Hier haben wir keinen direkten zoom; wir approximieren über scale aus transform:
        double scale = g2.getTransform().getScaleX();
        float alpha = (float) clamp(scale / 2.0, 0.1, 1.0);

        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        g2.setColor(new JBColor(Color.BLACK.brighter().brighter(), Color.WHITE.darker().darker()));

        // stroke 1px in screen => 1/scale in world
        g2.setStroke(new BasicStroke((float) (1.0 / Math.max(scale, 1e-6))));

        double minX = worldBounds.getMinX();
        double maxX = worldBounds.getMaxX();
        double minY = worldBounds.getMinY();
        double maxY = worldBounds.getMaxY();

        double startX = Math.floor(minX / spacing) * spacing;
        for (double x = startX; x <= maxX; x += spacing) {
            g2.draw(new Line2D.Double(x, minY, x, maxY));
        }

        double startY = Math.floor(minY / spacing) * spacing;
        for (double y = startY; y <= maxY; y += spacing) {
            g2.draw(new Line2D.Double(minX, y, maxX, y));
        }

        g2.setComposite(old);
    }

    private void renderNode(Graphics2D g2, FontRenderContext frc, Node node) {
        TextLayout title = new TextLayout(node.getName(), g2.getFont(), frc);
        int width = Math.max((int) title.getAdvance() + 4, 10);

        for (Attribute attribute : node.getAttributes().values()) {
            String attributeText = String.format("%s %s", attribute.name(), attribute.type());
            if (attributeText.isBlank()) continue;
            TextLayout layout = new TextLayout(attributeText, g2.getFont(), frc);
            float w = layout.getAdvance() + 12;
            if (w > width) width = (int) w;
        }

        node.setSize(new Vector2(width + 20, fontH * (node.getAttributes().size() + 1) + 20));

        int x = (int) node.getPosition().getX();
        int y = (int) node.getPosition().getY();

        g2.setColor(new JBColor(Color.LIGHT_GRAY.brighter(), Color.DARK_GRAY.darker()));
        g2.fillRoundRect(x, y, node.getSize().x(), node.getSize().y(), 10, 10);

        g2.setColor(new JBColor(Color.LIGHT_GRAY, Color.DARK_GRAY));
        g2.drawLine(x, (int) (y + fontH + 2), x + node.getSize().x(), (int) (y + fontH + 2));

        if (INodeSelectable.isSelected(selected, node)) {
            g2.setColor(new JBColor(Color.LIGHT_GRAY.darker().darker(), Color.DARK_GRAY.brighter().brighter()));
        } else {
            g2.setColor(new JBColor(Color.LIGHT_GRAY, Color.DARK_GRAY));
        }
        g2.drawRoundRect(x, y, node.getSize().x(), node.getSize().y(), 10, 10);

        g2.setColor(JBColor.BLACK);
        DatabaseIcons.Table.paintIcon(componentForIcons, g2, x + 2, y + 2);
        g2.drawString(node.getName(), x + 20, y + fontH - 2);
        for (int i = 0; i < node.getAttributes().size(); i++) {
            Attribute attribute = node.getAttributes().values().toArray(Attribute[]::new)[i];

            int attributeWidth = 4;
            if (!attribute.type().isBlank()) {
                TextLayout layout = new TextLayout(attribute.type(), g2.getFont(), frc);
                attributeWidth = (int) (layout.getAdvance() + 4);
            }

            boolean isForeignKey = model.isForeignKey(node, attribute.name());
            Icon icon = attribute.primaryKey()
                    ? (isForeignKey ? DatabaseIcons.ColGoldBlueKey : DatabaseIcons.ColGoldKey)
                    : (isForeignKey ? DatabaseIcons.ColBlueKey : DatabaseIcons.Col);

            // Highlight endpoints
            if (selected instanceof NodeGraph.Connection connection) {
                if ((node == connection.from() && Objects.equals(attribute.name(), connection.fromAttr()))
                        || (node == connection.to() && Objects.equals(attribute.name(), connection.toAttr()))) {
                    g2.setColor(JBColor.RED.darker());
                    g2.fillRect(x + 2, y - 10 + (fontH * (i + 2)), node.getSize().x() - 4, 16);
                    g2.setColor(JBColor.BLACK);
                }
            } else if (hoveredConnection != null) {
                if ((node == hoveredConnection.from() && Objects.equals(attribute.name(), hoveredConnection.fromAttr()))
                        || (node == hoveredConnection.to() && Objects.equals(attribute.name(), hoveredConnection.toAttr()))) {
                    g2.setColor(JBColor.GREEN.darker());
                    g2.fillRect(x + 2, y - 10 + (fontH * (i + 2)), node.getSize().x() - 4, 16);
                    g2.setColor(JBColor.BLACK);
                }
            }

            icon.paintIcon(componentForIcons, g2, x + 2, y - 10 + (fontH * (i + 2)));
            g2.drawString(attribute.name(), x + 20, y + (fontH * (i + 2)) + 4);
            g2.drawString(attribute.type(), (x + node.getSize().x()) - (attributeWidth + 4), y + (fontH * (i + 2)) + 4);
        }
    }

    // ===== Connections (nutzt ErdCanvas.ConnectionIconType) =====

    private enum Side { LEFT, RIGHT }

    private Point2D edgePoint(Point2D pos, Vector2 size, int yOffset, Side side) {
        double x = (side == Side.RIGHT) ? (pos.getX() + size.x()) : pos.getX();
        double y = pos.getY() + yOffset;
        return new Point2D.Double(x, y);
    }

    private static Point2D offsetX(Point2D p, double dx) {
        return new Point2D.Double(p.getX() + dx, p.getY());
    }

    private double pathLength(List<Point2D> pts) {
        double len = 0.0;
        for (int i = 0; i < pts.size() - 1; i++) len += pts.get(i).distance(pts.get(i + 1));
        return len;
    }

    private Side[] chooseBestSidesForOverlap(
            Node from, Node to,
            Point2D fromPos, Vector2 fromSize, int fromOff,
            Point2D toPos, Vector2 toSize, int toOff,
            ErdCanvas.ConnectionIconType fromType, ErdCanvas.ConnectionIconType toType
    ) {
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

        for (Side sStart : Side.values()) {
            for (Side sEnd : Side.values()) {
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

                if (pts.size() >= 2) {
                    Point2D p0 = pts.get(0);
                    Point2D p1 = pts.get(1);
                    double dx = p1.getX() - p0.getX();
                    if (dirStart == 1 && dx < -0.01) score += 1_000_000;
                    if (dirStart == -1 && dx > 0.01) score += 1_000_000;
                }

                for (int i = 0; i < pts.size() - 1; i++) {
                    Line2D seg = new Line2D.Double(pts.get(i), pts.get(i + 1));
                    if (fromRect.intersectsLine(seg) || toRect.intersectsLine(seg)) score += 100_000;
                }

                if (score < bestScore) {
                    bestScore = score;
                    bestStart = sStart;
                    bestEnd = sEnd;
                }
            }
        }
        return new Side[]{bestStart, bestEnd};
    }

    public void drawConnection(
            Node from, Node to, Graphics2D g2,
            int fromOff, int toOff,
            boolean hovered, boolean isSelected,
            ErdCanvas.ConnectionIconType fromType, ErdCanvas.ConnectionIconType toType
    ) {
        Point2D fromPos = from.getPosition();
        Vector2 fromSize = from.getSize();
        Point2D toPos = to.getPosition();
        Vector2 toSize = to.getSize();

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

        Point2D startSymbol = offsetX(startEdge, dirStart * getSymbolOffset(fromType));
        Point2D endSymbol = offsetX(endEdge, dirEnd * getSymbolOffset(toType));

        List<Point2D> waypoints = calculateOrthogonalPath(
                startSymbol, endSymbol, from, to, fromPos, fromSize, toPos, toSize
        );

        double scale = g2.getTransform().getScaleX();
        float stroke = (float) (1.5 / Math.max(scale, 1e-6));
        g2.setStroke(new BasicStroke(stroke));

        g2.setColor(isSelected ? JBColor.RED.darker() : hovered ? JBColor.GREEN.darker() : JBColor.BLACK);

        for (int i = 0; i < waypoints.size() - 1; i++) {
            Point2D p1 = waypoints.get(i);
            Point2D p2 = waypoints.get(i + 1);
            g2.draw(new Line2D.Double(p1, p2));
        }

        drawEndDecoration(g2, startEdge, startSymbol, toType, dirStart);
        drawEndDecoration(g2, endEdge, endSymbol, fromType, dirEnd);
    }

    private int getSymbolOffset(ErdCanvas.ConnectionIconType type) {
        return 10;
    }

    private void drawEndDecoration(Graphics2D g2, Point2D edge, Point2D symbol, ErdCanvas.ConnectionIconType type, int dir) {
        if (type == null) return;

        int y = (int) Math.round(edge.getY());

        if (type == ErdCanvas.ConnectionIconType.MANY_ONE || type == ErdCanvas.ConnectionIconType.MANY_ZERO) {
            drawCrowFootFromEdgeToSymbol(g2, edge, symbol);
        } else {
            g2.draw(new Line2D.Double(edge, symbol));
        }

        switch (type) {
            case ONE, MANY_ONE -> drawOneBar(g2, (int) Math.round(symbol.getX()), y, 6);
            case ZERO, MANY_ZERO -> drawZeroCircle(g2, (int) Math.round(symbol.getX()), y, 4);
            default -> {}
        }
    }

    private void drawOneBar(Graphics2D g2, int x, int y, int halfH) {
        g2.drawLine(x, y - halfH, x, y + halfH);
    }

    private void drawZeroCircle(Graphics2D g2, int x, int y, int r) {
        g2.drawOval(x - r, y - r, r * 2, r * 2);
    }

    private void drawCrowFootFromEdgeToSymbol(Graphics2D g2, Point2D edge, Point2D symbol) {
        int halfH = 6;

        int xEdge = (int) Math.round(edge.getX());
        int y = (int) Math.round(edge.getY());

        int xTip = (int) Math.round(symbol.getX());

        g2.drawLine(xEdge, y, xTip, y);
        g2.drawLine(xEdge, y - halfH, xTip, y);
        g2.drawLine(xEdge, y + halfH, xTip, y);
    }

    private List<Point2D> calculateOrthogonalPath(
            Point2D start, Point2D end,
            Node from, Node to,
            Point2D fromPos, Vector2 fromSize,
            Point2D toPos, Vector2 toSize
    ) {
        List<Point2D> waypoints = new ArrayList<>();
        waypoints.add(start);

        double startX = start.getX();
        double startY = start.getY();
        double endX = end.getX();
        double endY = end.getY();

        if (Math.abs(startY - endY) < 1.0) {
            waypoints.add(end);
            return waypoints;
        }

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

        double midX = (startX + endX) / 2;

        if (intersectsVerticalLine(fromRect, midX) || intersectsVerticalLine(toRect, midX)) {
            boolean startsRight = startX > fromPos.getX() + fromSize.x() / 2;

            if (startsRight) {
                midX = Math.max(
                        fromPos.getX() + fromSize.x() + MARGIN,
                        toPos.getX() + toSize.x() + MARGIN
                );
            } else {
                midX = Math.min(
                        fromPos.getX() - MARGIN,
                        toPos.getX() - MARGIN
                );
            }
        }

        waypoints.add(new Point2D.Double(midX, startY));
        waypoints.add(new Point2D.Double(midX, endY));
        waypoints.add(end);

        return waypoints;
    }

    private boolean intersectsVerticalLine(Rectangle2D rect, double x) {
        return x >= rect.getMinX() && x <= rect.getMaxX();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}