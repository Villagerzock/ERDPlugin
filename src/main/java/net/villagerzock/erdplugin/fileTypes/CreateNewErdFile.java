package net.villagerzock.erdplugin.fileTypes;

import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import net.villagerzock.erdplugin.ui.ErdIo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Properties;

public final class CreateNewErdFile extends CreateFileFromTemplateAction {
    @Override
    protected void buildDialog(@NotNull Project project, @NotNull PsiDirectory psiDirectory, CreateFileFromTemplateDialog.@NotNull Builder builder) {
        builder.setTitle("New ERD Diagram")
                .addKind("ERD Diagram",null,"ERD Diagram.erd");
    }

    @Override
    protected @NlsContexts.Command String getActionName(PsiDirectory psiDirectory, @NonNls @NotNull String s, @NonNls String s1) {
        return "ERD Diagram";
    }

    @Override
    protected PsiFile createFileFromTemplate(String name, FileTemplate template, PsiDirectory dir) {
        Project project = dir.getProject();

        Properties props = FileTemplateManager.getInstance(project).getDefaultProperties();
        props.setProperty("LATEST_VERSION", ErdIo.LATEST_VERSION);

        return super.createFileFromTemplate(name, template, dir);
    }
}
