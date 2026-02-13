package net.villagerzock.erdplugin.node;

public class NodeGraphSnapshot {
    private final Node[] nodes;
    private final NodeGraph.Connection[] connections;

    private NodeGraphSnapshot(Node[] nodes, NodeGraph.Connection[] connections) {
        this.nodes = nodes;
        this.connections = connections;
    }

    public void loadInto(NodeGraph graph){
        graph.nodes().clear();
        for (Node node : nodes){
            graph.nodes().add(node);
            node.setChanged(graph.getChanged());
        }

        for (NodeGraph.Connection connection : connections){
            graph.connections().add(connection);
        }
    }

    public static NodeGraphSnapshot of(NodeGraph nodeGraph){
        Node[] nodes = nodeGraph.nodes().toArray(Node[]::new);
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = nodes[i].deepCopy();
        }
        NodeGraph.Connection[] connections = nodeGraph.connections().toArray(NodeGraph.Connection[]::new);
        for (int i = 0; i < connections.length; i++){
            NodeGraph.Connection connection = connections[i];
            Node from = nodes[nodeGraph.getIndexOf(connection.from())];
            Node to = nodes[nodeGraph.getIndexOf(connection.to())];
            String fromAttr = connection.fromAttr();
            String toAttr = connection.toAttr();
            NodeGraph.ConnectionType type = connection.type();

            connections[i] = new NodeGraph.Connection(from,fromAttr,to,toAttr,type);
        }

        return new NodeGraphSnapshot(nodes,connections);
    }
}
