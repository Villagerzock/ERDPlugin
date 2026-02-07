package net.villagerzock.db;

import com.intellij.database.psi.DbNamespaceImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class ReverseEngineDatabase extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        System.out.println("Reverse Engineering Database");

        PsiElement[] selection;
        try {
            selection = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
        }catch (Throwable ignored){
            selection = null;
        }
        if (selection == null || selection.length == 0) return;

        DbNamespaceImpl db = getDatabase(selection);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiElement[] selection;
        try {
            selection = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
        }catch (Throwable ignored){
            selection = null;
        }
        Presentation p = e.getPresentation();
        if (selection == null || selection.length == 0) {
            p.setVisible(false);
            return;
        }



        p.setVisible(getDatabase(selection) != null);
    }

    private DbNamespaceImpl getDatabase(PsiElement[] selection){
        for (PsiElement psi : selection){
            System.out.println("DB Selection: " + psi.getClass().getName() + " -> " + psi);
            if (psi instanceof DbNamespaceImpl db){
                return db;
            }
        }
        return null;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
