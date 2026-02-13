package net.villagerzock.erdplugin.ui;

import com.intellij.database.model.DasNamespace;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.util.DasUtil;
import com.intellij.database.util.DbUIUtil;
import com.intellij.database.util.DbUtil;
import com.intellij.dupLocator.resultUI.BasicTreeNode;
import com.intellij.dupLocator.resultUI.DuplicatesModel;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import icons.DatabaseIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class JSelectDatabase extends JPanel {
    private DataBaseNode lastNamspace = null;
    public JSelectDatabase(Project project, Disposable parentDisposable){
        super(new BorderLayout());

        SimpleTree tree = new SimpleTree();
        SimpleTreeStructure structure = new SimpleTreeStructure() {

            private final SimpleNode root = new RootNode(project);

            @Override
            public @NotNull Object getRootElement() {
                return root;
            }
        };
        StructureTreeModel<SimpleTreeStructure> model = new StructureTreeModel<>(structure, parentDisposable);

        AsyncTreeModel asyncModel = new AsyncTreeModel(model, parentDisposable);
        tree.setModel(asyncModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                TreePath path = e.getNewLeadSelectionPath();
                if (path == null) return;
                Object leaf = path.getLastPathComponent();


                if (leaf instanceof DefaultMutableTreeNode dmtn){
                    Object uo = dmtn.getUserObject();

                    if (uo instanceof DataBaseNode dbn && changedListener != null){
                        changedListener.accept(dbn.namespace);
                        lastNamspace = dbn;
                    }
                }
            }
        });

        add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);
    }
    private Consumer<DasNamespace> changedListener = null;

    public void setChangedListener(Consumer<DasNamespace> listener) {
        changedListener = listener;
    }

    public DasNamespace getNamespace() {
        return lastNamspace.namespace;
    }
    public DbDataSource getDataSource(){
        return lastNamspace.getSource();
    }

    public static class RootNode extends SimpleNode {
        private final Project project;

        public RootNode(Project project) {
            this.project = project;
        }

        @Override
        public SimpleNode @NotNull [] getChildren() {
            List<DataSourceNode> nodes = new ArrayList<>();
            System.out.println("Searching for DataSources");
            for (DbDataSource source : DbUtil.getDataSources(project)) {
                System.out.println("Found DataSource: " + source.getName());
                nodes.add(new DataSourceNode(this,source));
            }
            return nodes.toArray(DataSourceNode[]::new);
        }
    }
    public static class DataSourceNode extends SimpleNode{
        private final DbDataSource source;

        public DataSourceNode(RootNode parent, DbDataSource source) {
            super(parent);
            this.source = source;
        }

        @Override
        protected void update(@NotNull PresentationData presentation) {
            super.update(presentation);
            presentation.setPresentableText(source.getName());
            presentation.setIcon(source.getIcon());
        }

        @Override
        public SimpleNode @NotNull [] getChildren() {
            List<DataBaseNode> nodes = new ArrayList<>();
            for (DasNamespace namespace : DasUtil.getSchemas(source)){
                nodes.add(new DataBaseNode(this,namespace));
            }
            return nodes.toArray(DataBaseNode[]::new);
        }
    }
    public static class DataBaseNode extends SimpleNode{
        private final DasNamespace namespace;
        private final DataSourceNode source;

        public DataBaseNode(DataSourceNode parent, DasNamespace namespace) {
            super(parent);
            this.namespace = namespace;
            this.source = parent;
        }

        @Override
        protected void update(@NotNull PresentationData presentation) {
            super.update(presentation);
            presentation.setPresentableText(namespace.getName());
            presentation.setIcon(DatabaseIcons.Schema);
        }

        @Override
        public SimpleNode @NotNull [] getChildren() {
            return NO_CHILDREN;
        }

        public DbDataSource getSource() {
            return source.source;
        }
    }
}
