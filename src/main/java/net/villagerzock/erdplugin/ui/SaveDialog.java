package net.villagerzock.erdplugin.ui;

import com.intellij.database.console.session.DatabaseSession;
import com.intellij.database.console.session.DatabaseSessionManager;
import com.intellij.database.dataSource.DatabaseConnectionCore;
import com.intellij.database.dataSource.LocalDataSource;
import com.intellij.database.datagrid.DataRequest;
import com.intellij.database.model.DasNamespace;
import com.intellij.database.model.RawDataSource;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.remote.jdbc.RemoteStatement;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.sql.SqlFileType;
import net.villagerzock.erdplugin.DatabaseDiffCalculator;
import net.villagerzock.erdplugin.node.NodeGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class SaveDialog extends DialogWrapper {
    private final JPanel jPanel;
    private final JSelectDatabase database;
    private final Project project;
    private final NodeGraph graph;

    protected SaveDialog(@Nullable Project project, NodeGraph graph) {
        super(project, true);
        this.graph = graph;
        this.project = project;
        jPanel = new JPanel(new BorderLayout());

        database = new JSelectDatabase(project, getDisposable());
        database.setPreferredSize(new Dimension(100,300));
        jPanel.add(database,BorderLayout.NORTH);

        Document document = EditorFactory.getInstance().createDocument("");

        Editor ed = EditorFactory.getInstance().createEditor(document,project,SqlFileType.INSTANCE,true);
        if (!(ed instanceof EditorEx editor)) return;
        editor.setViewer(true);
        editor.getSettings().setLineNumbersShown(true);
        editor.getSettings().setCaretRowShown(false);
        editor.getSettings().setWhitespacesShown(false);
        editor.getSettings().setUseSoftWraps(false);
        editor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(SqlFileType.INSTANCE,editor.getColorsScheme(),project));
        database.setChangedListener(namespace -> {
            setOKActionEnabled(true);
            String newText = DatabaseDiffCalculator.calculateDiffScript(namespace.getName(),graph);
            ModalityState ms = ModalityState.stateForComponent(jPanel);

            ApplicationManager.getApplication().invokeLater(() -> {
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    document.setText(newText);
                });
            }, ms);
        });

        jPanel.add(ed.getComponent(),BorderLayout.CENTER);

        init();
        setOKActionEnabled(false);

    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (getNamespace() == null){
            return new ValidationInfo("Select a Database");
        }
        return null;
    }

    public DasNamespace getNamespace(){
        return database.getNamespace();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return jPanel;
    }

    @Override
    public @NlsContexts.DialogTitle String getTitle() {
        return "Save ERD To Database";
    }

    @Override
    public @Nullable Dimension getInitialSize() {
        return new Dimension(600,800);
    }

    @Override
    protected void doOKAction() {
        int result = Messages.showYesNoDialog("As of right Now this action will delete ALL Data in " + getNamespace().getName() + " though this behaviour may change in future updates.","Are You Sure?", AllIcons.General.QuestionDialog);

        if (result == 0){
            new Task.Backgroundable(project, "Applying DB diff",true){
                @Override
                public void run(@NotNull ProgressIndicator progressIndicator) {
                    try {
                        sendReq(database.getDataSource(),DatabaseDiffCalculator.calculateDiffStatements(getNamespace().getName(),graph), progressIndicator);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }.queue();
            close(0,true);
        }
    }

    private void sendReq(DbDataSource dataSource, List<String> statements, @NotNull ProgressIndicator progressIndicator) throws ExecutionException, InterruptedException {
        LocalDataSource local = ReadAction.compute(()->{
            RawDataSource ds = dataSource.getDelegateDataSource();
            return (ds instanceof LocalDataSource l) ? l : null;
        });
        if (local == null) throw new IllegalStateException("DbDataSource is not Local");

        DatabaseSession session = DatabaseSessionManager.getSession(project,local);

        CompletableFuture<Void> done = new CompletableFuture<>();

        DataRequest.RawRequest req = new DataRequest.RawRequest(session) {
            @Override
            public void processRaw(Context context, DatabaseConnectionCore core) throws Exception {
                var conn = core.getRemoteConnection();

                try {
                    int total = statements.size();
                    for (int i = 0; i < total; i++){
                        if (progressIndicator.isCanceled()) throw new InterruptedException("Cancelled");
                        progressIndicator.setFraction((double) i / total);
                        progressIndicator.setText("Running Statement (" + (i+1) + "/" + total + ")");

                        String stmt = statements.get(i);
                        if (stmt == null || stmt.isBlank()) continue;

                        try {
                            RemoteStatement st = conn.createStatement();
                            st.execute(stmt);
                        }catch (Exception e){
                            throw new RuntimeException("SQL failed at statement #" + (i+1) + ":\n" + stmt, e);
                        }
                    }

                    progressIndicator.setFraction(1.0);
                    done.complete(null);
                }catch (Throwable t){
                    done.completeExceptionally(t);
                    if (t instanceof Exception e) throw e;
                    throw new RuntimeException(t);
                }
            }
        };

        session.getMessageBus().getDataProducer().processRequest(req);
        done.get();
    }
}
