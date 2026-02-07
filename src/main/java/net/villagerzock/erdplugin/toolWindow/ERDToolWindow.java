package net.villagerzock.erdplugin.toolWindow;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import j.G.O;
import net.villagerzock.erdplugin.node.Attribute;
import net.villagerzock.erdplugin.node.Node;
import net.villagerzock.erdplugin.ui.ErdCanvas;
import net.villagerzock.erdplugin.ui.JTableWithToolbar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class ERDToolWindow implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        Node n = ErdCanvas.getSelectedNode();
        Content content = ContentFactory.getInstance().createContent(null, null, false);
        setContent(n, content);
        toolWindow.getContentManager().addContent(content);
        ErdCanvas.setSelectedNodeChanged((node)->{
            if (node instanceof Node sel){
                setContent(sel, content);
            }else {
                setContent(null, content);
            }
        });
    }

    private void setContent(Node n, Content content) {
        if (n == null){
            content.setComponent(buildEmptyUi());
        }else {
            content.setComponent(buildUi(n));
        }
    }

    private JComponent buildEmptyUi() {
        JPanel empty = new JPanel(new BorderLayout());
        empty.add(new JLabel("Select a node to edit attributes."), BorderLayout.CENTER);

        return empty;
    }

    private @NotNull JComponent buildUi(Node node) {
        JPanel panel = new JPanel(new BorderLayout());

        JTableWithToolbar.Column<?>[] columns = {new JTableWithToolbar.Column<>("Column Name", String.class, "",false), new JTableWithToolbar.Column<>("Type", String.class, "VARCHAR(255)",false),new JTableWithToolbar.Column<>("Default Value", String.class, null, false), new JTableWithToolbar.Column<>("PK",Boolean.class, false,true), new JTableWithToolbar.Column<>("NL", Boolean.class, true,true), new JTableWithToolbar.Column<>("UQ",Boolean.class, false, true), new JTableWithToolbar.Column<>("AI",Boolean.class, false, true)};

        List<List<Object>> initialEntries = getLists(node);

        JTableWithToolbar table = new JTableWithToolbar(columns,initialEntries,(value) ->{
            Map<String,Attribute> attributes = new LinkedHashMap<>();
            for (List<Object> object : value){
                String name = (String) object.getFirst();
                String type = (String) object.get(1);
                String defaultValue = (String) object.get(2);
                boolean primaryKey = (Boolean) object.get(3);
                boolean nullable = (Boolean) object.get(4);
                boolean unique = (Boolean) object.get(5);
                boolean autoIncrement = (Boolean) object.get(6);
                attributes.put(name,new Attribute(name,type,primaryKey,nullable,unique, autoIncrement, defaultValue));
            }
            node.setAttributes(attributes);
            if (node.getChanged() != null){
                node.getChanged().run();
            }
        });

        panel.add(table, BorderLayout.CENTER);

        return panel;
    }

    private static @NotNull List<List<Object>> getLists(Node node) {
        List<List<Object>> initialEntries = new ArrayList<>();

        for (Attribute attribute : node.getAttributes().values()){
            List<Object> row = new ArrayList<>();
            row.add(attribute.name());
            row.add(attribute.type());
            row.add(attribute.defaultValue());
            row.add(attribute.primaryKey());
            row.add(attribute.nullable());
            row.add(attribute.unique());
            row.add(attribute.autoIncrement());
            initialEntries.add(row);
        }
        return initialEntries;
    }
}
