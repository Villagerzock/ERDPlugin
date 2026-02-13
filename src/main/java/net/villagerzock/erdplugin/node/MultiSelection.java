package net.villagerzock.erdplugin.node;

import java.util.ArrayList;
import java.util.List;

public class MultiSelection implements INodeSelectable {
    private final List<Node> nodes = new ArrayList<>();
    private final List<NodeGraph.Connection> connections = new ArrayList<>();
    @Override
    public boolean isSelected(INodeSelectable other) {
        for (Node node : nodes){
            if (other == node) return true;
        }
        for (NodeGraph.Connection connection : connections){
            if (other == connection) return true;
        }
        return this == other;
    }

    @Override
    public void mergeInto(MultiSelection multiSelection) {
        for (Node node : nodes){
            multiSelection.addNode(node);
        }
        for (NodeGraph.Connection connection : connections){
            multiSelection.addConnection(connection);
        }
    }

    @Override
    public void moveBy(double dx, double dy) {
        for (Node node : nodes){
            node.moveBy(dx,dy);
        }
    }

    public void addNode(Node node){
        if (!nodes.contains(node)){
            nodes.add(node);
        }
    }
    public void addConnection(NodeGraph.Connection connection){
        if (!connections.contains(connection)){
            connections.add(connection);
        }
    }

    public void reset() {
        nodes.clear();
        connections.clear();
    }

    public boolean hasNode(Node node){
        return nodes.contains(node);
    }

    public boolean hasConnection(NodeGraph.Connection connection){
        return connections.contains(connection);
    }
}
