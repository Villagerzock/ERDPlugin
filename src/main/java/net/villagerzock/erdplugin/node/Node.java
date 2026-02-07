package net.villagerzock.erdplugin.node;

import net.villagerzock.erdplugin.util.Vector2;
import org.w3c.dom.Attr;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class Node implements INodeSelectable {

    private Point2D position;
    private String name;
    private Map<String, Attribute> attributes;
    private Vector2 size;
    private Runnable changed;

    public Node(Point2D position, String name, Map<String, Attribute> attributes, Vector2 size, Runnable changed) {
        this.position = position;
        this.name = name;
        this.attributes = attributes;
        this.size = size;
        this.changed = changed;
    }

    public void addAttribute(Attribute attribute) {
        attributes.put(attribute.name(), attribute);
    }

    // getters/setters

    public Point2D getPosition() {
        return position;
    }

    public void setPosition(Point2D position) {
        this.position = position;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Attribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Attribute> attributes) {
        this.attributes = attributes;
    }

    public Vector2 getSize() {
        return size;
    }

    public void setSize(Vector2 size) {
        this.size = size;
    }

    public Runnable getChanged() {
        return changed;
    }

    public void setChanged(Runnable changed) {
        this.changed = changed;
    }

    public List<Attribute> getPrimaryKeys(){
        List<Attribute> primaryKeys = new ArrayList<>();
        for (Attribute attribute : attributes.values()){
            if (attribute.primaryKey()){
                primaryKeys.add(attribute);
            }
        }
        return primaryKeys;
    }
}
