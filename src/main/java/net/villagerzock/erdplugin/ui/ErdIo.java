package net.villagerzock.erdplugin.ui;

import com.google.gson.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import net.villagerzock.erdplugin.node.Attribute;
import net.villagerzock.erdplugin.node.Node;
import net.villagerzock.erdplugin.node.NodeGraph;
import net.villagerzock.erdplugin.util.Vector2;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class ErdIo {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public static NodeGraph loadOrEmpty(VirtualFile file) {
        try {
            JsonObject object = gson.fromJson(new String(file.getInputStream().readAllBytes()), JsonObject.class);

            JsonArray nodesArray = object.getAsJsonArray("nodes");

            List<Node> nodes = new ArrayList<>();
            for (JsonElement element : nodesArray){
                if (!(element instanceof JsonObject nodeObject)) continue;

                JsonArray positionObject = nodeObject.getAsJsonArray("position");
                Point2D position = new Point2D.Double(positionObject.get(0).getAsDouble(),positionObject.get(1).getAsDouble());

                String name = nodeObject.get("name").getAsString();

                JsonObject attributes = nodeObject.getAsJsonObject("attributes");

                Map<String, Attribute> attributeMap = new LinkedHashMap<>();

                for (Map.Entry<String, JsonElement> attribute : attributes.entrySet()){

                    JsonObject attributeObject = attribute.getValue().getAsJsonObject();


                    String sqlType = attributeObject.get("type").getAsString();
                    boolean primaryKey = attributeObject.get("primaryKey").getAsBoolean();
                    boolean nullable = attributeObject.get("nullable").getAsBoolean();

                    attributeMap.put(attribute.getKey(), new Attribute(attribute.getKey(),sqlType,primaryKey,nullable));
                }

                nodes.add(new Node(position,name,attributeMap,new Vector2(0,0),null));
            }

            JsonArray connectionsArray = object.getAsJsonArray("connections");

            List<NodeGraph.Connection> connections = new ArrayList<>();

            for (JsonElement connection : connectionsArray){
                if (!(connection instanceof JsonObject connectionObject)) continue;

                int from = connectionObject.get("from").getAsInt();;
                String fromAttr = connectionObject.get("fromAttr").getAsString();
                int to = connectionObject.get("to").getAsInt();;
                String toAttr = connectionObject.get("toAttr").getAsString();
                NodeGraph.ConnectionType type = NodeGraph.ConnectionType.valueOf(connectionObject.get("type").getAsString());

                if (!nodes.get(from).getAttributes().containsKey(fromAttr) || !nodes.get(to).getAttributes().containsKey(toAttr)) continue;

                connections.add(new NodeGraph.Connection(from,fromAttr,to,toAttr,type));
            }
            NodeGraph graph = new NodeGraph(connections,nodes);
            for (Node node : nodes){
                node.setChanged(graph.getChanged());
            }
            return graph;
        } catch (Throwable e) {
            return new NodeGraph();
        }
    }
    public static void save(VirtualFile file, NodeGraph graph){

        JsonObject root = new JsonObject();

        // nodes
        JsonArray nodesArray = new JsonArray();
        for (Node node : graph.nodes()) {
            JsonObject nodeObject = new JsonObject();

            // position: [x, y]
            JsonArray position = new JsonArray();
            position.add(node.getPosition().getX());
            position.add(node.getPosition().getY());
            nodeObject.add("position", position);

            // name
            nodeObject.addProperty("name", node.getName());

            // attributes
            JsonObject attributes = new JsonObject();
            for (Map.Entry<String, Attribute> entry : node.getAttributes().entrySet()) {
                Attribute attr = entry.getValue();

                JsonObject attrObject = new JsonObject();
                attrObject.addProperty("type", attr.type());        // matches valueOf(...) on load
                attrObject.addProperty("primaryKey", attr.primaryKey());
                attrObject.addProperty("nullable", attr.nullable());

                attributes.add(entry.getKey(), attrObject);
            }
            nodeObject.add("attributes", attributes);

            nodesArray.add(nodeObject);
        }
        root.add("nodes", nodesArray);

        // connections
        JsonArray connectionsArray = new JsonArray();
        for (NodeGraph.Connection c : graph.connections()) {
            JsonObject connectionObject = new JsonObject();
            connectionObject.addProperty("from", c.from());
            connectionObject.addProperty("fromAttr", c.fromAttr());
            connectionObject.addProperty("to", c.to());
            connectionObject.addProperty("toAttr", c.toAttr());
            connectionObject.addProperty("type", c.type().name());          // matches valueOf(...) on load
            connectionsArray.add(connectionObject);
        }
        root.add("connections", connectionsArray);

        // write pretty (optional)
        String jsonString = gson.toJson(root);
        ApplicationManager.getApplication().runWriteAction(()->{
            try {
                VfsUtil.saveText(file,jsonString);
            }catch (IOException e){
                throw new RuntimeException(e);
            }
        });
    }
}
