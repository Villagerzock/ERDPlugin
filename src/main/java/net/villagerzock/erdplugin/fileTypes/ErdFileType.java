package net.villagerzock.erdplugin.fileTypes;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import icons.DatabaseIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ErdFileType implements FileType {
    @Override
    public @NonNls @NotNull String getName() {
        return "ERD";
    }

    @Override
    public @NlsContexts.Label @NotNull String getDescription() {
        return "ERD Diagram File";
    }

    @Override
    public @NlsSafe @NotNull String getDefaultExtension() {
        return "erd";
    }

    @Override
    public @Nullable Icon getIcon() {
        return DatabaseIcons.ToolWindowDatabase;
    }

    @Override
    public boolean isBinary() {
        return false;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public @NonNls @Nullable String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) {
        return "UTF-8";
    }
}
