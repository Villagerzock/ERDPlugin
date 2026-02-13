package net.villagerzock.erdplugin;

import com.intellij.database.model.DasNamespace;
import com.intellij.database.model.DasObject;
import com.intellij.database.model.ObjectKind;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.psi.DbNamespaceImpl;
import com.intellij.database.util.DasUtil;
import com.intellij.database.util.DbUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.*;
import com.intellij.sql.SqlFileType;
import net.villagerzock.erdplugin.db.NodeGraphFromDbNamespace;
import net.villagerzock.erdplugin.fileTypes.ErdFileType;
import net.villagerzock.erdplugin.node.NodeGraph;
import net.villagerzock.erdplugin.ui.ErdIo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple in-memory VirtualFile.
 *
 * Notes:
 * - This does NOT integrate with the global VFS (no VirtualFileManager registration).
 * - It provides stable bytes, length and timestamp, and fresh streams per call.
 * - Operations like delete/move/rename are unsupported and will throw.
 */
public class LightVirtualFile extends VirtualFile {

    private final LightVirtualFileSystem system = new LightVirtualFileSystem();

    private final String path;

    private final DbNamespaceImpl namespace;

    // content
    private final Object lock = new Object();
    private byte[] bytes = new byte[0];
    private volatile long timeStamp = System.currentTimeMillis();

    private volatile boolean valid = true;
    private final Project project;

    public LightVirtualFile(DbNamespaceImpl namespace, String path, Project project) {
        this.namespace = namespace;
        this.path = path;
        this.project = project;

        NodeGraph graph = NodeGraphFromDbNamespace.build(namespace, this);

        graph.repositionNodesForConnections();

        ErdIo.save(this, graph);
    }

    @Override
    public @NotNull FileType getFileType() {
        return ErdFileType.INSTANCE;
    }

    @Override
    public @NotNull @NlsSafe String getName() {
        return namespace.getName();
    }

    @Override
    public @NotNull VirtualFileSystem getFileSystem() {
        return system;
    }

    @Override
    public @NonNls @NotNull String getPath() {
        // Must be unique and stable.
        // Include the file name for nicer display/debugging.
        return project.getLocationHash() + "/" + path;
    }

    @Override
    public boolean isWritable() {
        return true;
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
        return false;
    }

    /**
     * Convenience: set content directly.
     */

    public static class LightVirtualFileSystem extends VirtualFileSystem {
        private final Map<String, LightVirtualFile> CACHE = new HashMap<>();
        private final CopyOnWriteArrayList<VirtualFileListener> listeners = new CopyOnWriteArrayList<>();

        @Override
        public @NonNls @NotNull String getProtocol() {
            return "erd";
        }

        public static LightVirtualFileSystem getInstance(){
            return (LightVirtualFileSystem) VirtualFileManager.getInstance().getFileSystem("erd");
        }

        private static final Pattern PATH_PATTERN = Pattern.compile("^(?:erd://)?([^/]+)/(.*)$");

        private static @Nullable Project findOpenProjectByLocationHash(String locationHash) {
            for (Project p : ProjectManager.getInstance().getOpenProjects()) {
                if (locationHash.equals(p.getLocationHash())) return p;
            }
            return null;
        }
        @Override
        public @Nullable VirtualFile findFileByPath(@NotNull @NonNls String path) {
            // Path Build works like this: erd://<projectId>/<actualPath>
            System.out.println(path);
            return CACHE.computeIfAbsent(path, key -> {
                Matcher m = PATH_PATTERN.matcher(key);
                if (!m.matches())
                    throw new IllegalArgumentException("Path does not match expected pattern: " + key);

                String projectHash = m.group(1);
                String dbPath = m.group(2);

                Project project = findOpenProjectByLocationHash(projectHash);
                if (project == null)
                    throw new IllegalStateException("No open project found for location hash: " + projectHash);

                String[] dbPathSegments = dbPath.split("/");

                if (dbPathSegments.length <= 1)
                    throw new IllegalArgumentException("Database path must contain at least datasource and schema: " + dbPath);

                String firstSegment = dbPathSegments[0];

                DbDataSource source = null;
                for (DbDataSource s : DbUtil.getDataSources(project)) {
                    if (s.getName().equals(firstSegment)) {
                        source = s;
                        break;
                    }
                }
                if (source == null)
                    throw new IllegalArgumentException("No datasource named '" + firstSegment + "' in project: " + project.getName());

                DbNamespaceImpl namespace = null;

                for (DasObject ns : source.getDasChildren(ObjectKind.SCHEMA)) {
                    if (dbPathSegments[1].equals(ns.getName()) && ns instanceof DbNamespaceImpl impl) {
                        namespace = impl;
                        break;
                    }
                }
                if (namespace == null)
                    throw new IllegalArgumentException("No schema named '" + dbPathSegments[1] + "' in datasource '" + firstSegment + "'");

                for (int i = 2; i < dbPathSegments.length; i++) {
                    String segment = dbPathSegments[i];

                    DbNamespaceImpl next = null;

                    for (DasObject obj : namespace.getDasChildren(ObjectKind.SCHEMA)) {
                        if (obj instanceof DbNamespaceImpl ns && segment.equals(ns.getName())) {
                            next = ns;
                            break;
                        }
                    }

                    if (next == null)
                        throw new IllegalArgumentException("Schema path segment not found: '" + segment + "' (full path: " + dbPath + "')");

                    namespace = next;
                }

                return new LightVirtualFile(namespace, dbPath, project);
            });

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
            if (!(file instanceof LightVirtualFile lvf)) throw new UnsupportedOperationException();

            String oldName = lvf.namespace.getName();
            DbNamespaceImpl dbNamespace = lvf.namespace;
            dbNamespace.setName(newName);
            lvf.timeStamp = System.currentTimeMillis();

            firePropertyChanged(lvf, requestor, "name", oldName,newName);
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
            return false;
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
