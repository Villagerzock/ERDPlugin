package net.villagerzock.erdplugin.node;

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

    public enum ConnectionType {
        OneToOne,
        OneToMany
    }
    public enum InternalConnectionType {
        OneToOne,
        OneToMany,
        ManyToOne,
        ManyToMany
    }

    public record Connection(int from, String fromAttr, int to, String toAttr, ConnectionType type){}

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
