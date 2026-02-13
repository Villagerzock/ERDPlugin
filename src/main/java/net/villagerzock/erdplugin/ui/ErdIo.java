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
import java.util.*;

public class ErdIo {
    public static final String LATEST_VERSION = "1";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public static NodeGraph loadOrEmpty(VirtualFile file) {
        try {
            JsonObject object = gson.fromJson(new String(file.getInputStream().readAllBytes()), JsonObject.class);

            JsonObject meta = ErdIo.getOrDefault(object,"meta",new JsonObject());

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
                    boolean unique = attributeObject.get("unique").getAsBoolean();
                    boolean autoIncrement = attributeObject.get("autoIncrement").getAsBoolean();
                    String defaultValue = attributeObject.has("default") ? attributeObject.get("default").getAsString() : "";

                    attributeMap.put(attribute.getKey(), new Attribute(attribute.getKey(),sqlType,primaryKey,nullable,unique,autoIncrement,defaultValue));
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

                connections.add(new NodeGraph.Connection(nodes.get(from),fromAttr,nodes.get(to),toAttr,type));
            }
            NodeGraph graph = new NodeGraph(connections,nodes,file);
            for (Node node : nodes){
                node.setChanged(graph.getChanged());
            }
            return graph;
        } catch (Throwable e) {
            System.err.println("Failed to read File: " + file.getName() + " creating new Diagram");
            e.printStackTrace();
            return new NodeGraph(file);
        }
    }

    private static <T> T getOrDefault(JsonObject object, String key, T defaultValue) {
        Class<T> typeClass = (Class<T>) defaultValue.getClass();

        JsonElement element = object.get(key);

        return gson.fromJson(element, typeClass);
    }

    public static void save(VirtualFile file, NodeGraph graph){

        JsonObject root = new JsonObject();


        JsonObject metaObject = new JsonObject();
        metaObject.addProperty("version",LATEST_VERSION);
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
                attrObject.addProperty("unique",attr.unique());
                attrObject.addProperty("autoIncrement", attr.autoIncrement());
                attrObject.addProperty("default", attr.defaultValue());

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
            connectionObject.addProperty("from", graph.getIndexOf(c.from()));
            connectionObject.addProperty("fromAttr", c.fromAttr());
            connectionObject.addProperty("to", graph.getIndexOf(c.to()));
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
