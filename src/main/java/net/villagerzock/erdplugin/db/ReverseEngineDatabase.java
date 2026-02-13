package net.villagerzock.erdplugin.db;

import com.intellij.database.model.DasObject;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.psi.DbElement;
import com.intellij.database.psi.DbNamespaceImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.PsiElement;
import net.villagerzock.erdplugin.LightVirtualFile;
import net.villagerzock.erdplugin.fileTypes.ErdFileType;
import net.villagerzock.erdplugin.ui.ErdIo;
import net.villagerzock.erdplugin.node.Attribute;
import net.villagerzock.erdplugin.node.Node;
import net.villagerzock.erdplugin.node.NodeGraph;
import net.villagerzock.erdplugin.util.Vector2;
import org.jetbrains.annotations.NotNull;

import java.awt.geom.Point2D;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ReverseEngineDatabase extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        System.out.println("Reverse Engineering Database");

        Project project = e.getProject();
        if (project == null) return;

        PsiElement[] selection = safeSelection(e);
        if (selection == null || selection.length == 0) return;

        DbNamespaceImpl db = getDatabase(selection);
        if (db == null) return;


        // 2) Temp-Datei + VirtualFile erstellen
        String baseName = safeFileName(db.getName() != null ? db.getName() : "database");
        String fileName = baseName + ".erd";

        try {
            VirtualFile vf = LightVirtualFile.LightVirtualFileSystem.getInstance().findFileByPath("erd://" + project.getLocationHash() + "/" + getSchemaPath(db));



            // 4) Datei öffnen
            ApplicationManager.getApplication().invokeLater(() ->
                    FileEditorManager.getInstance(project).openFile(vf, true, true)
            );

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private String getSchemaPath(DbNamespaceImpl db) {
        List<String> pathParts = new ArrayList<>();

        DasObject parent = db;
        DbDataSource dataSource = null;

        while (parent != null && !(parent instanceof DbDataSource)) {
            pathParts.add(parent.getName());
            parent = parent.getDasParent();
        }

        if (parent == null)
            throw new IllegalStateException("DbNamespaceImpl has no DbDataSource in parent chain: " + db.getName());

        dataSource = (DbDataSource) parent;

        StringBuilder path = new StringBuilder();

        // datasource name first, because parser expects it as segment[0]
        path.append(dataSource.getName());

        // then schema / namespace path
        for (int i = pathParts.size() - 1; i >= 0; i--) {
            path.append("/").append(pathParts.get(i));
        }

        return path.toString();
    }


    @Override
    public void update(@NotNull AnActionEvent e) {
        Presentation p = e.getPresentation();

        PsiElement[] selection = safeSelection(e);
        if (selection == null || selection.length == 0) {
            p.setVisible(false);
            return;
        }

        p.setVisible(getDatabase(selection) != null);
    }

    private PsiElement[] safeSelection(AnActionEvent e) {
        try {
            return e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private DbNamespaceImpl getDatabase(PsiElement[] selection) {
        for (PsiElement psi : selection) {
            // debug optional:
            // System.out.println("DB Selection: " + psi.getClass().getName() + " -> " + psi);
            if (psi instanceof DbNamespaceImpl db) return db;
        }
        return null;
    }

    private static String safeFileName(String in) {
        return in.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
