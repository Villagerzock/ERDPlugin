package net.villagerzock.erdplugin.ui;

import net.villagerzock.erdplugin.node.Node;
import net.villagerzock.erdplugin.node.NodeGraph;

public class ConnectionContext {
    private final NodeGraph.InternalConnectionType selectedConnectionType;
    private Node from;

    public ConnectionContext(NodeGraph.InternalConnectionType selectedConnectionType) {
        this.selectedConnectionType = selectedConnectionType;
    }

    public void setFrom(Node from) {
        this.from = from;
    }

    public Node getFrom() {
        return from;
    }

    public NodeGraph.InternalConnectionType getType() {
        return selectedConnectionType;
    }
}
