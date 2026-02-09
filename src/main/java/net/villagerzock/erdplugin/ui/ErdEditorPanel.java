package net.villagerzock.erdplugin.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import icons.DatabaseIcons;
import net.villagerzock.erdplugin.ErdIcons;
import net.villagerzock.erdplugin.node.Attribute;
import net.villagerzock.erdplugin.node.Node;
import net.villagerzock.erdplugin.node.NodeGraph;
import net.villagerzock.erdplugin.util.Vector2;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
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

        AnAction createTable = new AnAction("Create Table","", AllIcons.General.Add) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                String newName = Messages.showInputDialog("Enter Name:", "Create Table", Messages.getQuestionIcon(), "", new InputValidatorEx() {
                    @Override
                    public @NlsContexts.DetailedDescription @Nullable String getErrorText(@NonNls String s) {
                        for (Node node : model.nodes()){
                            if (node.getName().equals(s))
                                return "A Table named '" + s + "' already exists.";
                        }
                        return s.isBlank() ? "Table name cannot be Blank" : !Character.isAlphabetic(s.charAt(0)) ? "First Character needs to be Alphabetic" : null;
                    }
                });
                if (newName == null){
                    return;
                }
                model.nodes().add(new Node(new Point2D.Double(-viewState.panX / viewState.zoom,-viewState.panY / viewState.zoom), newName, new LinkedHashMap<>(), new Vector2(0,0), ErdEditorPanel.this::changed));
                changed();
            }
        };

        AnAction oneToOne = new AnAction("1:1","", ErdIcons.ONE_TO_ONE) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                canvas.startConnection(NodeGraph.InternalConnectionType.OneToOne);
            }
        };

        AnAction manyToOne = new AnAction("M:1","", ErdIcons.MANY_TO_ONE) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                canvas.startConnection(NodeGraph.InternalConnectionType.ManyToOne);
            }
        };

        AnAction oneToMany = new AnAction("1:M","", ErdIcons.ONE_TO_MANY) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                canvas.startConnection(NodeGraph.InternalConnectionType.OneToMany);
            }
        };

        AnAction manyToMany = new AnAction("M:M","",ErdIcons.MANY_TO_MANY) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                canvas.startConnection(NodeGraph.InternalConnectionType.ManyToMany);
            }
        };
        AnAction exportSql = new AnAction("Save To Table (Coming Soon)", "", AllIcons.Actions.MenuSaveall) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {}

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(false);
            }
        };

        AnAction formatDiagram = new AnAction("Format Diagram","",AllIcons.Actions.ReformatCode) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                model.repositionNodesForConnections();
            }
        };

        DefaultActionGroup actionGroup = new DefaultActionGroup(createTable,oneToOne,manyToOne,oneToMany,manyToMany,exportSql,formatDiagram);

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("ErdEditorToolbar", actionGroup, true);
        return toolbar.getComponent();
    }

    private String export() {
        String createTable = """
                CREATE TABLE IF NOT EXISTS `%s` (
                %s);
                
                """;

        String alterTable = """
                ALTER TABLE `%s`
                \tADD CONSTRAINT `fk_%s_%s`
                \tFOREIGN KEY (`%s`)
                \tREFERENCES `%s`(`%s`)
                \tON DELETE %s
                \tON UPDATE RESTRICT;
                
                """;

        StringBuilder finalSql = new StringBuilder();

        for (Node node : model.nodes()){
            String name = node.getName();
            StringBuilder attributes = new StringBuilder();
            boolean first = true;

            for (Attribute attribute : node.getAttributes().values()) {
                if (!first) attributes.append(",\n");
                first = false;

                attributes.append("\t")
                        .append("`")
                        .append(attribute.name())
                        .append("` ")
                        .append(attribute.type());

                if (attribute.primaryKey()) {
                    attributes.append(" PRIMARY KEY");
                } else if (attribute.nullable()) {
                    attributes.append(" NULL");
                } else {
                    attributes.append(" NOT NULL");
                }
            }

            attributes.append("\n");
            finalSql.append(String.format(createTable,name,attributes));
        }

        for (NodeGraph.Connection connection : model.connections()){
            Node fkTable = model.nodes().get(connection.from());
            Node pkTable = model.nodes().get(connection.to());
            String fkTableName = fkTable.getName();
            String pkTableName = pkTable.getName();

            String fkAttrName = connection.fromAttr();
            String pkAttrName = connection.toAttr();

            String onDelete = fkTable.getAttributes().get(fkAttrName).nullable() ? "SET NULL" : "RESTRICT";

            finalSql.append(
                    String.format(
                            alterTable,
                            fkTableName,
                            fkTableName,
                            pkTableName,
                            fkAttrName,
                            pkTableName,
                            pkAttrName,
                            onDelete
                    )
            );
        }

        return finalSql.toString();
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
