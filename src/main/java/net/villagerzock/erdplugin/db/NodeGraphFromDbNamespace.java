package net.villagerzock.erdplugin.db;

import com.intellij.database.model.DasColumn;
import com.intellij.database.model.DasObject;
import com.intellij.database.model.ObjectKind;
import com.intellij.database.psi.DbColumnImpl;
import com.intellij.database.psi.DbNamespaceImpl;
import com.intellij.database.types.DasType;
import com.intellij.database.util.DasUtil;
import com.intellij.database.util.DbUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.JBIterable;
import net.villagerzock.erdplugin.node.Attribute;
import net.villagerzock.erdplugin.node.Node;
import net.villagerzock.erdplugin.node.NodeGraph;
import net.villagerzock.erdplugin.util.Vector2;

import java.awt.geom.Point2D;
import java.lang.reflect.Method;
import java.util.*;

public final class NodeGraphFromDbNamespace {

    private NodeGraphFromDbNamespace() {}

    public static NodeGraph build(DbNamespaceImpl db, VirtualFile file) {
        System.out.println("[RE] buildFromNamespace: " + db.getName() + " (" + db.getClass().getName() + ")");

        NodeGraph graph = new NodeGraph(file);

        Object rawTables = db.getDasChildren(ObjectKind.TABLE);
        List<Object> tables = toList(rawTables);

        System.out.println("[RE] getDasChildren(TABLE) raw=" + (rawTables == null ? "null" : rawTables.getClass().getName()));
        System.out.println("[RE] foundTables=" + tables.size());

        for (Object t : tables) {
            System.out.println("[RE]  foundTable: " + t.getClass().getName() + " name=" + invoke(t, "getName") + " toString=" + t);
        }

        // Layout
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(tables.size())));
        double x0 = 50, y0 = 50, dx = 420, dy = 260;

        Map<Object, Integer> tableToIndex = new IdentityHashMap<>();
        Map<String, Integer> tableNameToIndex = new HashMap<>();

        for (int i = 0; i < tables.size(); i++) {
            Object table = tables.get(i);

            String tableName = str(invoke(table, "getName"));
            if (tableName == null || tableName.isBlank()) tableName = "Table" + i;

            System.out.println("[RE] processingTable: " + tableName + " (" + table.getClass().getName() + ")");

            Object rawCols = invoke(table, "getDasChildren", ObjectKind.COLUMN);
            List<Object> columns = toList(rawCols);

            System.out.println("[RE]  getDasChildren(COLUMN) raw=" + (rawCols == null ? "null" : rawCols.getClass().getName()));
            System.out.println("[RE]  foundColumns=" + columns.size());

            for (Object c : columns) {
                System.out.println("[RE]   foundColumn: " + c.getClass().getName() + " name=" + invoke(c, "getName") + " toString=" + c);
            }

            Set<String> pkCols = extractPrimaryKeyColumnNames(table);
            System.out.println("[RE]  pkCols=" + pkCols);

            Map<String, Attribute> attrs = new LinkedHashMap<>();
            for (Object col : columns) {
                String colName = str(invoke(col, "getName"));
                if (colName == null || colName.isBlank()) {
                    System.out.println("[RE]   skipColumn (no name): " + col);
                    continue;
                }

                String type = extractTypeString(col);
                boolean nullable = extractNullable(col);
                boolean pk = false;

                DasObject dasObject = DbUtil.getDasObject(col);

                if (dasObject instanceof DasColumn impl){
                    pk = DasUtil.isPrimary(impl);
                }

                System.out.println("[RE]   foundAttr: " + colName
                        + " type=" + type
                        + " nullable=" + nullable
                        + " pk=" + pk);

                attrs.put(colName, new Attribute(colName, type, pk, nullable, false, false, null));
            }

            int gx = i % cols;
            int gy = i / cols;

            Node node = new Node(
                    new Point2D.Double(x0 + gx * dx, y0 + gy * dy),
                    tableName,
                    attrs,
                    new Vector2(0, 0),
                    graph.getChanged()
            );

            tableNameToIndex.put(tableName.toLowerCase(Locale.ROOT), graph.nodes().size());

            graph.nodes().add(node);
            tableToIndex.put(table, graph.nodes().size() - 1);

            System.out.println("[RE] addedNode: " + tableName + " attrs=" + attrs.size());
        }

        // Foreign keys
        for (Object fromTable : tables) {
            String fromName = String.valueOf(invoke(fromTable, "getName"));
            Integer fromIdx = tableNameToIndex.get(fromName.toLowerCase(Locale.ROOT));
            if (fromIdx >= 0 && fromIdx < graph.nodes().size())
                System.out.println("From Name is: " + fromName + " and in GraphTable is: " + graph.nodes().get(fromIdx).getName());
            if (fromIdx == null) continue;

            List<Object> fks = toList(invoke(fromTable, "getDasChildren", ObjectKind.FOREIGN_KEY));
            System.out.println("[RE] FK scan table=" + fromName + " fks=" + fks.size());

            for (Object fk : fks) {
                String ddl = String.valueOf(fk);

                // 1) Zieltable (am zuverlässigsten aus SQL)
                String refTableName = parseRefTableFromDdl(ddl);
                if (refTableName == null) {
                    Object refTable = invoke(fk, "getRefTable");
                    refTableName = refTable != null ? String.valueOf(invoke(refTable, "getName")) : null;
                }
                if (refTableName == null) continue;

                Integer toIdx = tableNameToIndex.get(refTableName.toLowerCase(Locale.ROOT));
                if (toIdx == null) continue;

                // 2) Columns aus SQL
                List<String> fkCols = parseFkColsFromDdl(ddl);
                List<String> refCols = parseRefColsFromDdl(ddl);

                System.out.println("[RE]  FK ddl=" + ddl.replace("\n", " "));
                System.out.println("[RE]   fkCols=" + fkCols);
                System.out.println("[RE]   refCols=" + refCols);
                System.out.println("[RE]   refTable=" + refTableName);

                int pairs = Math.min(fkCols.size(), refCols.size());
                for (int i = 0; i < pairs; i++) {
                    graph.addConnection(new NodeGraph.Connection(
                            graph.nodes().get(fromIdx), fkCols.get(i),
                            graph.nodes().get(toIdx), refCols.get(i),
                            NodeGraph.ConnectionType.OneToMany
                    ));
                }
            }
        }

        System.out.println("[RE] Graph result: nodes=" + graph.nodes().size() + " connections=" + graph.connections().size());
        return graph;
    }
    @SafeVarargs
    private static List<Object> firstNonEmpty(List<Object>... lists) {
        for (List<Object> l : lists) {
            if (l != null && !l.isEmpty()) {
                return l;
            }
        }
        return new ArrayList<>();
    }

    private static List<String> multiRefToNames(Object multiRef) {
        if (multiRef == null) return List.of();

        // MultiRef ist selber oft Iterable, aber nicht immer -> best effort
        List<Object> resolved = firstNonEmpty(
                toList(invoke(multiRef, "resolve")),
                toList(invoke(multiRef, "resolveAll")),
                toList(invoke(multiRef, "resolveTargets")),
                toList(invoke(multiRef, "getTargets")),
                toList(invoke(multiRef, "getElements")),
                toList(invoke(multiRef, "getResults")),
                toList(invoke(multiRef, "toList")) // selten, aber manchmal vorhanden
        );

        // Fallback: wenn MultiRef direkt iterable ist
        if (resolved.isEmpty()) {
            resolved = toList(multiRef);
        }

        ArrayList<String> names = new ArrayList<>();
        for (Object o : resolved) {
            String n = str(invoke(o, "getName"));
            if (n != null && !n.isBlank()) names.add(n);
        }
        return names;
    }


    /* ---------------- PK ---------------- */

    private static Set<String> extractPrimaryKeyColumnNames(Object table) {
        Set<String> out = new HashSet<>();

        Object rawPks = invoke(table, "getDasChildren", ObjectKind.KEY);
        List<Object> pks = toList(rawPks);

        System.out.println("[RE]  getDasChildren(PRIMARY_KEY) count=" + pks.size() + " raw=" + (rawPks == null ? "null" : rawPks.getClass().getName()));

        for (Object pk : pks) {
            List<String> names = extractNames(firstNonNull(
                    invoke(pk, "getColumns"),
                    invoke(pk, "getColumnNames"),
                    invoke(pk, "getKeyColumns")
            ));
            for (String n : names) out.add(n.toLowerCase(Locale.ROOT));
        }

        // fallback: column flag
        if (out.isEmpty()) {
            List<Object> cols = toList(invoke(table, "getDasChildren", ObjectKind.COLUMN));
            for (Object col : cols) {
                Object b = invoke(col, "isPrimaryKey");
                if (b instanceof Boolean bb && bb) {
                    String n = str(invoke(col, "getName"));
                    if (n != null) out.add(n.toLowerCase(Locale.ROOT));
                }
            }
        }

        return out;
    }

    /* ---------------- Column helpers ---------------- */

    private static String extractTypeString(Object col) {
        Object dt = invoke(col, "getDataType");
        if (dt != null) {
            String spec = str(invoke(dt, "getSpecification"));
            if (spec != null && !spec.isBlank()) return spec;

            String name = str(invoke(dt, "getName"));
            if (name != null && !name.isBlank()) return name;

            return dt.toString();
        }
        String typeName = str(invoke(col, "getTypeName"));
        return (typeName != null && !typeName.isBlank()) ? typeName : "UNKNOWN";
    }

    private static boolean extractNullable(Object col) {
        Object notNull = invoke(col, "isNotNull");
        if (notNull instanceof Boolean b) return !b;
        Object nullable = invoke(col, "isNullable");
        if (nullable instanceof Boolean b) return b;
        return true;
    }

    /* ---------------- utils ---------------- */

    private static Object invoke(Object target, String methodName, Object... args) {
        if (target == null) return null;
        try {
            for (Method m : target.getClass().getMethods()) {
                if (!m.getName().equals(methodName)) continue;
                if (m.getParameterCount() != args.length) continue;
                m.setAccessible(true);
                return m.invoke(target, args);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static List<Object> toList(Object o) {
        if (o == null) return new ArrayList<>();

        if (o instanceof JBIterable<?> jb) {
            ArrayList<Object> out = new ArrayList<>();
            for (Object x : jb) out.add(x);
            return out;
        }
        if (o instanceof Iterable<?> it) {
            ArrayList<Object> out = new ArrayList<>();
            for (Object x : it) out.add(x);
            return out;
        }
        if (o instanceof Collection<?> c) return new ArrayList<>((Collection<Object>) c);

        if (o.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(o);
            ArrayList<Object> out = new ArrayList<>(len);
            for (int i = 0; i < len; i++) out.add(java.lang.reflect.Array.get(o, i));
            return out;
        }

        // not iterable
        ArrayList<Object> single = new ArrayList<>();
        single.add(o);
        return single;
    }

    private static List<String> extractNames(Object maybeList) {
        List<String> out = new ArrayList<>();
        for (Object o : toList(maybeList)) {
            String n = str(invoke(o, "getName"));
            if (n == null) n = String.valueOf(o);
            if (n != null && !n.isBlank()) out.add(n);
        }
        return out;
    }

    private static Object firstNonNull(Object... vals) {
        for (Object v : vals) if (v != null) return v;
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static List<String> parseFkColsFromDdl(String ddl) {
        // foreign key (a, b) references ...
        return parseColsAfterKeyword(ddl, "foreign key");
    }

    private static List<String> parseRefColsFromDdl(String ddl) {
        // references users (id, ...)
        int idx = ddl.toLowerCase(Locale.ROOT).indexOf("references");
        if (idx < 0) return List.of();
        String tail = ddl.substring(idx);
        return parseColsAfterKeyword(tail, "references");
    }

    private static String parseRefTableFromDdl(String ddl) {
        String lower = ddl.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("references");
        if (idx < 0) return null;

        // after "references" until "("
        String tail = ddl.substring(idx + "references".length()).trim();
        int paren = tail.indexOf('(');
        if (paren < 0) return null;

        String tablePart = tail.substring(0, paren).trim();
        // remove schema/catalog if present: schema.table
        int dot = tablePart.lastIndexOf('.');
        if (dot >= 0) tablePart = tablePart.substring(dot + 1);

        tablePart = tablePart.replace("`", "").replace("\"", "").trim();
        return tablePart.isBlank() ? null : tablePart;
    }

    private static List<String> parseColsAfterKeyword(String ddl, String keyword) {
        String lower = ddl.toLowerCase(Locale.ROOT);
        int k = lower.indexOf(keyword);
        if (k < 0) return List.of();

        int open = lower.indexOf('(', k);
        if (open < 0) return List.of();
        int close = lower.indexOf(')', open);
        if (close < 0) return List.of();

        String inside = ddl.substring(open + 1, close).trim();
        if (inside.isBlank()) return List.of();

        String[] parts = inside.split(",");
        ArrayList<String> cols = new ArrayList<>();
        for (String p : parts) {
            String c = p.trim().replace("`", "").replace("\"", "");
            if (!c.isBlank()) cols.add(c);
        }
        return cols;
    }

}
