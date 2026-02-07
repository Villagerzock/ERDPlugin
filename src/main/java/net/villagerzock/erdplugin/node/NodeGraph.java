package net.villagerzock.erdplugin.node;

import net.villagerzock.erdplugin.ui.ErdCanvas;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NodeGraph {
    public NodeGraph(List<Connection> connections, List<Node> nodes) {
        this.connections = connections;
        this.nodes = nodes;
    }
    public NodeGraph() {
        this.connections = new ArrayList<>();
        this.nodes = new ArrayList<>();
    }

    public boolean isForeignKey(int i, String name) {
        for (Connection connection : connections){
            if ((connection.from == i && Objects.equals(connection.fromAttr, name)) || (connection.to == i && Objects.equals(connection.toAttr, name))){
                return true;
            }
        }
        return false;
    }

    public void deleteNode(Node selectedNode) {
        int id = nodes.indexOf(selectedNode);
        connections.removeIf(connection -> connection.from == id || connection.to == id);
        nodes.remove(id);
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

    public record Connection(int from, String fromAttr, int to, String toAttr, ConnectionType type) implements INodeSelectable {}

    private final List<Connection> connections;

    private final List<Node> nodes;
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

}
