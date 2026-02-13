package net.villagerzock.erdplugin.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
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
import net.villagerzock.erdplugin.ui.builder.BuiltDialog;
import net.villagerzock.erdplugin.util.Vector2;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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

        this.canvas = new ErdCanvas(model,viewState,selectionState,this,project);

        add(buildToolbar(project), BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(canvas);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
    }


    private JComponent buildToolbar(Project project) {
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
                Map<String, Attribute> attributeMap = new LinkedHashMap<>();
                attributeMap.put("id",new Attribute("id","INT",true,false,false,true,""));
                model.nodes().add(new Node(new Point2D.Double(-viewState.panX / viewState.zoom,-viewState.panY / viewState.zoom), newName, attributeMap, new Vector2(0,0), ErdEditorPanel.this::changed));
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
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                ApplicationManager.getApplication().invokeLater(()->{
                    SaveDialog saveDialog = new SaveDialog(project,model);

                    saveDialog.showAndGet();
                }, ModalityState.any());
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(true);
            }
        };

        AnAction exportAsImage = new AnAction("Export As Image","", AllIcons.General.Export) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                BuiltDialog dialog = BuiltDialog.builder(project)
                        .addInput("Location", Path.class)
                        .addInput("File Type", ErdCanvas.ImageType.class, ErdCanvas.ImageType.PNG)
                        .addInput("Multiplier", Integer.class, 1)
                        .build();

                ErdCanvas.ImageType fileType = dialog.getValue("File Type",ErdCanvas.ImageType.class);
                Path path = dialog.getValue("Location", Path.class);
                int mul = dialog.getValue("Multiplier",Integer.class);

                try {
                    canvas.exportAsPng(path.toFile(),fileType,mul);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        AnAction formatDiagram = new AnAction("Format Diagram","",AllIcons.Actions.ReformatCode) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                model.repositionNodesForConnections();
            }
        };

        AnAction zoomIn = new AnAction("Zoom In", "", AllIcons.Graph.ZoomIn) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                ErdEditorPanel.this.canvas.zoomMinimap(true);
            }
        };
        AnAction zoomOut = new AnAction("Zoom Out", "", AllIcons.Graph.ZoomOut) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                ErdEditorPanel.this.canvas.zoomMinimap(false);
            }
        };

        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(createTable);
        actionGroup.addSeparator();
        actionGroup.add(oneToOne);
        actionGroup.add(manyToOne);
        actionGroup.add(oneToMany);
        actionGroup.add(manyToMany);
        actionGroup.addSeparator();
        actionGroup.add(exportSql);
        actionGroup.add(exportAsImage);
        actionGroup.add(formatDiagram);
        actionGroup.addSeparator();
        actionGroup.add(zoomIn);
        actionGroup.add(zoomOut);

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("ErdEditorToolbar", actionGroup, true);
        toolbar.getComponent().setBackground(bg);

        return toolbar.getComponent();
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
