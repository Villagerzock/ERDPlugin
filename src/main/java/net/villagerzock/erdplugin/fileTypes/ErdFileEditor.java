package net.villagerzock.erdplugin.fileTypes;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import net.villagerzock.erdplugin.ui.ErdEditorPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class ErdFileEditor extends UserDataHolderBase implements FileEditor {
    private ErdEditorPanel panel;
    private final Project project;
    private final VirtualFile file;

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private boolean modified = false;

    public ErdFileEditor(Project project, VirtualFile file){
        this.project = project;
        this.file = file;
        panel = new ErdEditorPanel(project,file);

        panel.setOnAnyChange(()->{
            setModified(true);
        });
    }

    public void setModified(boolean value) {
        if (this.modified == value) return;
        boolean old = this.modified;
        this.modified = value;
        pcs.firePropertyChange(FileEditor.getPropModified(), old, value);
        if (value){
            scheduleSaveDebounced();
        }
    }

    private final Alarm saveAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);

    private void scheduleSaveDebounced() {
        saveAlarm.cancelAllRequests();
        System.out.println("Scheduling Save!");
        saveAlarm.addRequest(() -> {
            if (!modified) return;
            System.out.println("Saving!");
            save();
        }, 500);
    }

    @Override
    public @NotNull JComponent getComponent() {
        return panel;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return panel.getPreferredFocusComponent();
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getName() {
        return "ERD";
    }

    @Override
    public void setState(@NotNull FileEditorState fileEditorState) {
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public boolean isValid() {
        return file.isValid();
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener propertyChangeListener) {
        pcs.addPropertyChangeListener(propertyChangeListener);
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener propertyChangeListener) {
        pcs.removePropertyChangeListener(propertyChangeListener);
    }

    @Override
    public @Nullable FileEditorLocation getCurrentLocation() {
        return null;
    }

    @Override
    public void dispose() {
        panel.dispose();
    }

    public boolean save(){
        boolean ok = panel.save();
        if (ok) setModified(false);
        return ok;
    }

    @Override
    public VirtualFile getFile() {
        return file;
    }
}
