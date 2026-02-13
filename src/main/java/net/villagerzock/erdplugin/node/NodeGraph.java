package net.villagerzock.erdplugin.node;

import com.intellij.openapi.vfs.VirtualFile;
import net.villagerzock.erdplugin.ui.ErdCanvas;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NodeGraph {
    public NodeGraph(List<Connection> connections, List<Node> nodes, VirtualFile file) {
        this.connections = connections;
        this.nodes = nodes;
        this.file = file;
    }
    public NodeGraph(VirtualFile file) {
        this.file = file;
        this.connections = new ArrayList<>();
        this.nodes = new ArrayList<>();
    }

    public boolean isForeignKey(Node node, String name) {
        for (Connection connection : connections){
            if ((connection.from == node && Objects.equals(connection.fromAttr, name)) || (connection.to == node && Objects.equals(connection.toAttr, name))){
                return true;
            }
        }
        return false;
    }

    public void deleteNode(Node selectedNode) {
        connections.removeIf(connection -> connection.from == selectedNode || connection.to == selectedNode);
        nodes.remove(selectedNode);
    }

    public void delete(INodeSelectable selectable){
        if (selectable instanceof Node node){
            deleteNode(node);
        }else if (selectable instanceof Connection connection){
            deleteConnection(connection);
        }
    }

    private void deleteConnection(Connection connection) {
        connections.remove(connection);
    }

    public VirtualFile getFile() {
        return file;
    }

    public Rectangle2D getBounds() {
        double minX = Integer.MAX_VALUE;
        double minY = Integer.MAX_VALUE;

        double maxX = Integer.MIN_VALUE;
        double maxY = Integer.MIN_VALUE;

        for (Node node : nodes){
            minX = Math.min(node.getPosition().getX()-20,minX);
            minY = Math.min(node.getPosition().getY()-20,minY);

            maxX = Math.max((node.getPosition().getX()+node.getSize().x()) + 20, maxX);
            maxY = Math.max((node.getPosition().getY()+node.getSize().y()) + 20, maxY);
        }

        return new Rectangle2D.Double(minX,minY,maxX-minX,maxY-minY);
    }

    public enum ConnectionType {
        OneToOne(ErdCanvas.ConnectionIconType.ONE, ErdCanvas.ConnectionIconType.ONE, ErdCanvas.ConnectionIconType.ZERO, ErdCanvas.ConnectionIconType.ZERO),
        OneToMany(ErdCanvas.ConnectionIconType.ONE, ErdCanvas.ConnectionIconType.MANY_ONE, ErdCanvas.ConnectionIconType.ZERO, ErdCanvas.ConnectionIconType.MANY_ZERO)
        ;

        private final ErdCanvas.ConnectionIconType notNullTypeFrom;
        private final ErdCanvas.ConnectionIconType notNullTypeTo;

        private final ErdCanvas.ConnectionIconType nullableTypeFrom;
        private final ErdCanvas.ConnectionIconType nullableTypeTo;

        ConnectionType(ErdCanvas.ConnectionIconType notNullTypeFrom, ErdCanvas.ConnectionIconType notNullTypeTo, ErdCanvas.ConnectionIconType nullableTypeFrom, ErdCanvas.ConnectionIconType nullableTypeTo) {
            this.notNullTypeFrom = notNullTypeFrom;
            this.notNullTypeTo = notNullTypeTo;
            this.nullableTypeFrom = nullableTypeFrom;
            this.nullableTypeTo = nullableTypeTo;
        }

        public ErdCanvas.ConnectionIconType getTypeFrom(boolean nullable) {
            return nullable ? nullableTypeFrom : notNullTypeFrom;
        }

        public ErdCanvas.ConnectionIconType getTypeTo(boolean nullable) {
            return nullable ? nullableTypeTo : notNullTypeTo;
        }
    }
    public enum InternalConnectionType {
        OneToOne,
        OneToMany,
        ManyToOne,
        ManyToMany
    }

    public record Connection(Node from, String fromAttr, Node to, String toAttr, ConnectionType type) implements INodeSelectable {
        @Override
        public void moveBy(double dx, double dy) {
            from.moveBy(dx,dy);
            to.moveBy(dx,dy);
        }

        @Override
        public boolean isMinimapSelected(INodeSelectable selected) {
            if (selected instanceof Node node){
                return node == from || node == to;
            }
            return INodeSelectable.super.isMinimapSelected(selected);
        }

        @Override
        public void mergeInto(MultiSelection multiSelection) {
            multiSelection.addConnection(this);
        }
    }

    private final List<Connection> connections;

    private final List<Node> nodes;
    private final VirtualFile file;
    private Runnable changed;

    public void setChanged(Runnable changed) {
        this.changed = changed;
    }

    public Runnable getChanged() {
        return changed;
    }

    public List<Node> nodes(){
        return nodes;
    }
    public List<Connection> connections(){
        return connections;
    }

    public void addConnection(Connection connection){
        connections.add(connection);
    }

    public int getIndexOf(Node node){
        return nodes.indexOf(node);
    }


    // FOR REFORMATING

    public void repositionNodesForConnections() {
        if (nodes.isEmpty()) return;

        // --- Tuning ---
        final int iterations = 220;
        final double padding = 24.0;          // Mindestabstand zwischen Nodes
        final double idealEdgeLength = 260.0; // Ziel-Länge einer Connection
        final double repulsionStrength = 85000.0;
        final double attractionStrength = 0.012; // je kleiner, desto "weicher"
        final double damping = 0.85;          // Bewegung dämpfen (0..1)
        final double maxStep = 45.0;          // max. Bewegung pro Iteration
        final double cooling = 0.985;         // "Temperatur" sinkt pro Iteration

        // Fallback size für Nodes die noch nicht gerendert wurden (size == 0,0)
        final int fallbackW = 220;
        final int fallbackH = 140;

        // --- Helper: Node size (mit fallback) ---
        java.util.function.IntFunction<java.awt.geom.Rectangle2D.Double> rectOf = (idx) -> {
            Node n = nodes.get(idx);
            var p = n.getPosition();
            var s = n.getSize();
            double w = (s.x() <= 0 ? fallbackW : s.x());
            double h = (s.y() <= 0 ? fallbackH : s.y());
            return new java.awt.geom.Rectangle2D.Double(
                    p.getX(), p.getY(),
                    w, h
            );
        };

        // --- Initial: falls alle auf (0,0) kleben, grob in Grid verteilen ---
        boolean allSame = true;
        double x0 = nodes.get(0).getPosition().getX();
        double y0 = nodes.get(0).getPosition().getY();
        for (int i = 1; i < nodes.size(); i++) {
            var p = nodes.get(i).getPosition();
            if (p.getX() != x0 || p.getY() != y0) { allSame = false; break; }
        }
        if (allSame) {
            int cols = (int) Math.ceil(Math.sqrt(nodes.size()));
            double gx = 0, gy = 0;
            for (int i = 0; i < nodes.size(); i++) {
                int c = i % cols;
                int r = i / cols;
                var n = nodes.get(i);
                var s = n.getSize();
                double w = (s.x() <= 0 ? fallbackW : s.x());
                double h = (s.y() <= 0 ? fallbackH : s.y());
                n.getPosition().setLocation(gx + c * (w + 120.0), gy + r * (h + 120.0));
            }
        }

        // Velocity arrays
        double[] vx = new double[nodes.size()];
        double[] vy = new double[nodes.size()];

        double temperature = 1.0;

        for (int it = 0; it < iterations; it++) {
            double[] fx = new double[nodes.size()];
            double[] fy = new double[nodes.size()];

            // --- Repulsion (O(n^2)) ---
            for (int i = 0; i < nodes.size(); i++) {
                var pi = nodes.get(i).getPosition();
                var si = nodes.get(i).getSize();
                double wi = (si.x() <= 0 ? fallbackW : si.x());
                double hi = (si.y() <= 0 ? fallbackH : si.y());
                double cix = pi.getX() + wi / 2.0;
                double ciy = pi.getY() + hi / 2.0;

                for (int j = i + 1; j < nodes.size(); j++) {
                    var pj = nodes.get(j).getPosition();
                    var sj = nodes.get(j).getSize();
                    double wj = (sj.x() <= 0 ? fallbackW : sj.x());
                    double hj = (sj.y() <= 0 ? fallbackH : sj.y());
                    double cjx = pj.getX() + wj / 2.0;
                    double cjy = pj.getY() + hj / 2.0;

                    double dx = cix - cjx;
                    double dy = ciy - cjy;

                    // kleine jitter, damit 0-dist nicht explodiert
                    if (dx == 0 && dy == 0) { dx = 0.001; dy = 0.001; }

                    double dist2 = dx * dx + dy * dy;
                    double dist = Math.sqrt(dist2);

                    // Repulsion Kraft
                    double f = repulsionStrength / (dist2 + 1.0);

                    double ux = dx / dist;
                    double uy = dy / dist;

                    fx[i] += ux * f;
                    fy[i] += uy * f;
                    fx[j] -= ux * f;
                    fy[j] -= uy * f;
                }
            }

            // --- Attraction entlang Connections ---
            for (Connection c : connections) {
                Node a = c.from();
                Node b = c.to();

                var pa = a.getPosition();
                var pb = b.getPosition();

                var sa = a.getSize();
                var sb = b.getSize();

                double wa = (sa.x() <= 0 ? fallbackW : sa.x());
                double ha = (sa.y() <= 0 ? fallbackH : sa.y());
                double wb = (sb.x() <= 0 ? fallbackW : sb.x());
                double hb = (sb.y() <= 0 ? fallbackH : sb.y());

                double ax = pa.getX() + wa / 2.0;
                double ay = pa.getY() + ha / 2.0;
                double bx = pb.getX() + wb / 2.0;
                double by = pb.getY() + hb / 2.0;

                double dx = bx - ax;
                double dy = by - ay;

                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 0.001) dist = 0.001;

                double ux = dx / dist;
                double uy = dy / dist;

                // "Spring": zieht zusammen wenn zu weit, drückt leicht auseinander wenn zu nahe
                double stretch = dist - idealEdgeLength;
                double f = attractionStrength * stretch;

                fx[getIndexOf(a)] += ux * f;
                fy[getIndexOf(a)] += uy * f;
                fx[getIndexOf(b)] -= ux * f;
                fy[getIndexOf(b)] -= uy * f;
            }

            // --- Integrate (Velocity + Damping + clamp step) ---
            for (int i = 0; i < nodes.size(); i++) {
                vx[i] = (vx[i] + fx[i]) * damping;
                vy[i] = (vy[i] + fy[i]) * damping;

                // Temperatur / Cooling
                vx[i] *= temperature;
                vy[i] *= temperature;

                // clamp step
                double step = Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i]);
                if (step > maxStep) {
                    double s = maxStep / step;
                    vx[i] *= s;
                    vy[i] *= s;
                }

                var p = nodes.get(i).getPosition();
                p.setLocation(p.getX() + vx[i], p.getY() + vy[i]);
            }

            // --- Collision resolve (Rects + padding) ---
            // ein paar Durchläufe pro Iteration reichen
            for (int pass = 0; pass < 2; pass++) {
                boolean any = false;

                for (int i = 0; i < nodes.size(); i++) {
                    for (int j = i + 1; j < nodes.size(); j++) {
                        var ri = rectOf.apply(i);
                        var rj = rectOf.apply(j);

                        // padding erweitern
                        java.awt.geom.Rectangle2D.Double ei = new java.awt.geom.Rectangle2D.Double(
                                ri.x - padding, ri.y - padding, ri.width + padding * 2, ri.height + padding * 2
                        );
                        java.awt.geom.Rectangle2D.Double ej = new java.awt.geom.Rectangle2D.Double(
                                rj.x - padding, rj.y - padding, rj.width + padding * 2, rj.height + padding * 2
                        );

                        if (!ei.intersects(ej)) continue;

                        any = true;

                        // overlap in x/y bestimmen
                        double ixCenterX = ri.getCenterX();
                        double ixCenterY = ri.getCenterY();
                        double jxCenterX = rj.getCenterX();
                        double jxCenterY = rj.getCenterY();

                        double dx = ixCenterX - jxCenterX;
                        double dy = ixCenterY - jxCenterY;

                        if (dx == 0 && dy == 0) { dx = 0.001; dy = 0.001; }

                        double overlapX = (ri.width / 2.0 + rj.width / 2.0 + padding) - Math.abs(dx);
                        double overlapY = (ri.height / 2.0 + rj.height / 2.0 + padding) - Math.abs(dy);

                        // schiebe entlang der kleineren Achse auseinander (klassisch, sieht "geordnet" aus)
                        if (overlapX < overlapY) {
                            double push = overlapX / 2.0 + 0.5;
                            double sx = Math.signum(dx) * push;

                            nodes.get(i).getPosition().setLocation(nodes.get(i).getPosition().getX() + sx, nodes.get(i).getPosition().getY());
                            nodes.get(j).getPosition().setLocation(nodes.get(j).getPosition().getX() - sx, nodes.get(j).getPosition().getY());
                        } else {
                            double push = overlapY / 2.0 + 0.5;
                            double sy = Math.signum(dy) * push;

                            nodes.get(i).getPosition().setLocation(nodes.get(i).getPosition().getX(), nodes.get(i).getPosition().getY() + sy);
                            nodes.get(j).getPosition().setLocation(nodes.get(j).getPosition().getX(), nodes.get(j).getPosition().getY() - sy);
                        }
                    }
                }

                if (!any) break;
            }

            temperature *= cooling;
        }

        // --- Normalize: alles in positive Koordinaten schieben ---
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        for (int i = 0; i < nodes.size(); i++) {
            var r = rectOf.apply(i);
            minX = Math.min(minX, r.getMinX());
            minY = Math.min(minY, r.getMinY());
        }
        if (minX < 0 || minY < 0) {
            double shiftX = (minX < 0) ? (-minX + padding) : 0;
            double shiftY = (minY < 0) ? (-minY + padding) : 0;
            for (Node n : nodes) {
                var p = n.getPosition();
                p.setLocation(p.getX() + shiftX, p.getY() + shiftY);
            }
        }

        if (changed != null) changed.run();
    }
}
