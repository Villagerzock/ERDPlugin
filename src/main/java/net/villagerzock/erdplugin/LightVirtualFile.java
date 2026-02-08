package net.villagerzock.erdplugin;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple in-memory VirtualFile.
 *
 * Notes:
 * - This does NOT integrate with the global VFS (no VirtualFileManager registration).
 * - It provides stable bytes, length and timestamp, and fresh streams per call.
 * - Operations like delete/move/rename are unsupported and will throw.
 */
public class LightVirtualFile extends VirtualFile {
    private final UUID id = UUID.randomUUID();

    private final LightVirtualFileSystem system = new LightVirtualFileSystem();

    private volatile String name;
    private final boolean readOnly;

    // content
    private final Object lock = new Object();
    private byte[] bytes = new byte[0];
    private volatile long timeStamp = System.currentTimeMillis();

    private volatile boolean valid = true;
    private final FileType fileType;

    public LightVirtualFile(@NotNull String name, boolean readOnly, FileType fileType) {
        this.name = name;
        this.readOnly = readOnly;
        this.fileType = fileType;
    }

    @Override
    public @NotNull FileType getFileType() {
        return fileType;
    }

    @Override
    public @NotNull @NlsSafe String getName() {
        return name;
    }

    @Override
    public @NotNull VirtualFileSystem getFileSystem() {
        return system;
    }

    @Override
    public @NonNls @NotNull String getPath() {
        // Must be unique and stable.
        // Include the file name for nicer display/debugging.
        return system.getProtocol() + ":///" + id + "/" + name;
    }

    @Override
    public boolean isWritable() {
        return !readOnly;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public @Nullable VirtualFile getParent() {
        return null;
    }

    @Override
    public VirtualFile @NotNull [] getChildren() {
        return EMPTY_ARRAY;
    }

    @Override
    public @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
        if (!isWritable()) throw new IOException("File is read-only: " + getPath());

        // Collect bytes in memory, commit on close.
        return new ByteArrayOutputStream() {
            private boolean closed;

            @Override
            public void close() throws IOException {
                if (closed) return;
                closed = true;
                super.close();

                byte[] newBytes = toByteArray();
                synchronized (lock) {
                    bytes = newBytes;
                    timeStamp = (newTimeStamp > 0 ? newTimeStamp : System.currentTimeMillis());
                }

                system.fireContentsChanged(LightVirtualFile.this, requestor);
            }
        };
    }

    @Override
    public byte @NotNull [] contentsToByteArray() throws IOException {
        synchronized (lock) {
            return bytes.clone();
        }
    }

    @Override
    public long getTimeStamp() {
        return timeStamp;
    }

    @Override
    public long getModificationStamp() {
        return getTimeStamp();
    }

    @Override
    public long getLength() {
        synchronized (lock) {
            return bytes.length;
        }
    }

    @Override
    public void refresh(boolean asynchronous, boolean recursive, @Nullable Runnable postRunnable) {
        // nothing to refresh (in-memory), but run callback if provided
        if (postRunnable != null) postRunnable.run();
    }

    @Override
    public @NotNull InputStream getInputStream() throws IOException {
        synchronized (lock) {
            return new ByteArrayInputStream(bytes);
        }
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * Convenience: set content directly.
     */

    public class LightVirtualFileSystem extends VirtualFileSystem {
        private final CopyOnWriteArrayList<VirtualFileListener> listeners = new CopyOnWriteArrayList<>();

        @Override
        public @NonNls @NotNull String getProtocol() {
            return "virtual";
        }

        @Override
        public @Nullable VirtualFile findFileByPath(@NotNull @NonNls String path) {
            // Extremely simple mapping: we only have one file instance.
            // If you want a real VFS, build a registry map<path, file>.
            String myPath = LightVirtualFile.this.getPath();
            if (path.equals(myPath)) return LightVirtualFile.this;

            // allow passing without protocol prefix
            if (!path.contains("://") && (getProtocol() + "://" + path).equals(myPath)) return LightVirtualFile.this;

            return null;
        }

        @Override
        public void refresh(boolean asynchronous) {
            // no-op for in-memory
        }

        @Override
        public @Nullable VirtualFile refreshAndFindFileByPath(@NotNull String path) {
            return findFileByPath(path);
        }

        @Override
        public void addVirtualFileListener(@NotNull VirtualFileListener listener) {
            listeners.add(listener);
        }

        @Override
        public void removeVirtualFileListener(@NotNull VirtualFileListener listener) {
            listeners.remove(listener);
        }

        @Override
        protected void deleteFile(Object requestor, @NotNull VirtualFile file) throws IOException {
            throw new UnsupportedOperationException("deleteFile is not supported for " + getProtocol());
        }

        @Override
        protected void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException {
            throw new UnsupportedOperationException("moveFile is not supported for " + getProtocol());
        }

        @Override
        protected void renameFile(Object requestor, @NotNull VirtualFile file, @NotNull String newName) throws IOException {
            if (file != LightVirtualFile.this) throw new UnsupportedOperationException();
            if (!LightVirtualFile.this.isWritable()) throw new IOException("File is read-only: " + LightVirtualFile.this.getPath());

            String oldName = LightVirtualFile.this.name;
            LightVirtualFile.this.name = newName;
            LightVirtualFile.this.timeStamp = System.currentTimeMillis();

            firePropertyChanged(LightVirtualFile.this, requestor, "name", oldName,newName);
        }

        @Override
        protected @NotNull VirtualFile createChildFile(Object requestor, @NotNull VirtualFile parent, @NotNull String name) throws IOException {
            throw new UnsupportedOperationException("createChildFile is not supported for " + getProtocol());
        }

        @Override
        protected @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile parent, @NotNull String dir) throws IOException {
            throw new UnsupportedOperationException("createChildDirectory is not supported for " + getProtocol());
        }

        @Override
        protected @NotNull VirtualFile copyFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent, @NotNull String copyName) throws IOException {
            throw new UnsupportedOperationException("copyFile is not supported for " + getProtocol());
        }

        @Override
        public boolean isReadOnly() {
            return LightVirtualFile.this.isReadOnly();
        }

        // ---- very light "event" hooks (best-effort) ----
        // VirtualFileListener is normally driven by the platform VFS event system.
        // Since this VFS isn't registered globally, these listeners only help if YOU attach them.

        private void fireContentsChanged(@NotNull VirtualFile file, Object requestor) {
            // VirtualFileListener doesn't have a single "contentsChanged" method in all versions;
            // some versions use events instead. So we keep this as a placeholder hook.
            // If you need real VFS events, you must integrate with VirtualFileManager / VfsUtil.
            long currentMillis = System.currentTimeMillis()-1;
            for (VirtualFileListener listener : listeners){
                listener.contentsChanged(new VirtualFileEvent(requestor,file, null, currentMillis,System.currentTimeMillis()));
            }
        }

        private void firePropertyChanged(@NotNull VirtualFile file, Object requestor, String property, Object oldVal, Object newVal) {
            // Same caveat as above.
            long currentMillis = System.currentTimeMillis()-1;
            for (VirtualFileListener listener : listeners){
                listener.propertyChanged(new VirtualFilePropertyEvent(requestor,file,property,oldVal,newVal));
            }
        }
    }
}
