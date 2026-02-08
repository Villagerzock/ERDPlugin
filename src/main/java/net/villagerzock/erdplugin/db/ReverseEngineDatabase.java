package net.villagerzock.erdplugin.db;

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

        // 1) Graph bauen
        NodeGraph graph = NodeGraphFromDbNamespace.build(db);

        graph.repositionNodesForConnections();

        System.out.println("nodes=" + graph.nodes().size() + " connections=" + graph.connections().size());
        for (var n : graph.nodes()) {
            System.out.println(" - " + n.getName() + " attrs=" + n.getAttributes().size());
        }


        // 2) Temp-Datei + VirtualFile erstellen
        String baseName = safeFileName(db.getName() != null ? db.getName() : "database");
        String fileName = baseName + ".erd";

        try {
            VirtualFile vf = new LightVirtualFile(baseName,false, ErdFileType.INSTANCE);

            ErdIo.save(vf, graph);

            // 4) Datei öffnen
            ApplicationManager.getApplication().invokeLater(() ->
                    FileEditorManager.getInstance(project).openFile(vf, true, true)
            );

        } catch (Exception ex) {
            ex.printStackTrace();
        }
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
