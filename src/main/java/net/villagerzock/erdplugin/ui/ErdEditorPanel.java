package net.villagerzock.erdplugin.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import net.villagerzock.erdplugin.node.Node;
import net.villagerzock.erdplugin.node.NodeGraph;
import net.villagerzock.erdplugin.util.Vector2;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class ErdEditorPanel extends JPanel {

    private final Project project;
    private final VirtualFile file;

    private final NodeGraph model;
    private final ErdViewState viewState;
    private final ErdSelectionState selectionState;



    private final ErdCanvas canvas;

    public ErdEditorPanel(Project project, VirtualFile file){
        super(new BorderLayout());
        this.project = project;
        this.file = file;

        this.model = ErdIo.loadOrEmpty(file);
        this.model.setChanged(this::changed);
        this.viewState = new ErdViewState();
        this.selectionState = new ErdSelectionState();

        this.canvas = new ErdCanvas(model,viewState,selectionState,this);

        add(buildToolbar(), BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(canvas);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
    }


    private JComponent buildToolbar() {
        JPanel bar = new JPanel();

        JBColor bg = new JBColor(Color.LIGHT_GRAY.brighter().brighter(), Color.DARK_GRAY.darker().darker());

        bar.setBackground(bg);
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));

        JButton createTable = new JButton("Create Table");
        createTable.setBackground(bg);
        createTable.addActionListener(e -> {
            String newName;
            do {
                newName = Messages.showInputDialog("Enter Name:","Create Table",Messages.getQuestionIcon());
            }while (newName == null || newName.isEmpty());
            model.nodes().add(new Node(new Point2D.Double(-viewState.panX / viewState.zoom,-viewState.panY / viewState.zoom), newName, new LinkedHashMap<>(), new Vector2(0,0), this::changed));
            changed();
        });
        bar.add(createTable);

        JButton oneToOne = new JButton("1:1");
        oneToOne.setBackground(bg);
        oneToOne.addActionListener(e -> canvas.startConnection(NodeGraph.InternalConnectionType.OneToOne));
        bar.add(oneToOne);

        JButton manyToOne = new JButton("M:1");
        manyToOne.setBackground(bg);
        manyToOne.addActionListener(e -> canvas.startConnection(NodeGraph.InternalConnectionType.ManyToOne));
        bar.add(manyToOne);

        JButton oneToMany = new JButton("1:M");
        oneToMany.setBackground(bg);
        oneToMany.addActionListener(e -> canvas.startConnection(NodeGraph.InternalConnectionType.OneToMany));
        bar.add(oneToMany);

        bar.add(Box.createHorizontalStrut(12));

        JButton exportSql = new JButton("Export SQL");
        exportSql.setBackground(bg);
        bar.add(exportSql);

        bar.add(Box.createHorizontalGlue());
        return bar;
    }
    public boolean save() {
        try {
            ErdIo.save(file, model);
            return true;
        }catch (Throwable t){
            return false;
        }
    }

    public void dispose() {

    }

    public @Nullable JComponent getPreferredFocusComponent() {
        return this.canvas;
    }

    private Runnable onAnyChange;

    public void setOnAnyChange(Runnable r) {
        this.onAnyChange = r;
    }

    public void changed() {
        if (onAnyChange != null) onAnyChange.run();
    }
}
