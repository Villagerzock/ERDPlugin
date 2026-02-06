package net.villagerzock.erdplugin.ui;

import com.intellij.ui.JBColor;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import j.G.O;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class JTableWithToolbar extends JPanel {

    public record Column<T>(String name, Class<T> type, T defaultValue, boolean hasForcedWidth) {}

    private final Column<?>[] columns;
    private final List<List<Object>> rows;
    private final AttributableTableModel model = new AttributableTableModel();

    private final JBTable table;

    private class AttributableTableModel extends AbstractTableModel {
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int c) { return columns[c].name(); }
        @Override public Class<?> getColumnClass(int c) { return columns[c].type(); }
        @Override public boolean isCellEditable(int r, int c) { return true; }

        @Override public Object getValueAt(int r, int c) { return rows.get(r).get(c); }

        @Override
        public void setValueAt(Object v, int r, int c) {
            rows.get(r).set(c, v);
            changedListener.accept(rows);
            fireTableCellUpdated(r, c);
        }
    }

    private final Consumer<List<List<Object>>> changedListener;


    public JTableWithToolbar(Column<?>[] columns, List<List<Object>> initialEntries, @NotNull Consumer<List<List<Object>>> changedListener) {
        super(new BorderLayout());
        this.rows = initialEntries;
        this.columns = columns;
        this.changedListener = changedListener;
        table = new JBTable(model);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);


        JPanel decorated = ToolbarDecorator.createDecorator(table)
                .setAddAction(e -> addRow(List.of()))
                .setRemoveAction(e -> {
                    int viewRow = table.getSelectedRow();
                    if (viewRow < 0) return;
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    removeRow(modelRow);
                })
                .setMoveUpAction(e -> {
                    int viewRow = table.getSelectedRow();
                    if (viewRow < 0) return;
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    moveUp(modelRow);
                    int newView = table.convertRowIndexToView(Math.max(0, modelRow - 1));
                    table.getSelectionModel().setSelectionInterval(newView, newView);
                })
                .setMoveDownAction(e -> {
                    int viewRow = table.getSelectedRow();
                    if (viewRow < 0) return;
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    moveDown(modelRow);
                    int newView = table.convertRowIndexToView(Math.min(rows.size() - 1, modelRow + 1));
                    table.getSelectionModel().setSelectionInterval(newView, newView);
                })
                .createPanel();
        // Wichtig: NUR decorated adden
        add(decorated, BorderLayout.CENTER);

        for (int i = 0; i<columns.length; i++){
            packColumnToHeader(table,i,16,columns[i].hasForcedWidth);
        }
    }

    public void removeRow(int row) {
        if (row < 0 || row >= rows.size()) return;
        rows.remove(row);
        changedListener.accept(rows);
        model.fireTableRowsDeleted(row, row);
    }

    public void addRow(List<Object> row) {
        List<Object> finalRow = new ArrayList<>(columns.length);
        for (int i = 0; i < columns.length; i++) {
            finalRow.add(i < row.size() ? row.get(i) : columns[i].defaultValue());
        }
        rows.add(finalRow);
        int i = rows.size() - 1;
        changedListener.accept(rows);
        model.fireTableRowsInserted(i, i);
    }

    public void moveUp(int row) {
        if (row <= 0 || row >= rows.size()) return;
        var tmp = rows.get(row - 1);
        rows.set(row - 1, rows.get(row));
        rows.set(row, tmp);
        changedListener.accept(rows);
        model.fireTableDataChanged();
    }

    public void moveDown(int row) {
        if (row < 0 || row >= rows.size() - 1) return;
        var tmp = rows.get(row + 1);
        rows.set(row + 1, rows.get(row));
        rows.set(row, tmp);
        changedListener.accept(rows);
        model.fireTableDataChanged();
    }


    public static void packColumnToHeader(JTable table, int columnIndex, int paddingPx, boolean force) {
        TableColumn column = table.getColumnModel().getColumn(columnIndex);

        TableCellRenderer headerRenderer = column.getHeaderRenderer();
        if (headerRenderer == null) {
            JTableHeader header = table.getTableHeader();
            headerRenderer = header.getDefaultRenderer();
        }

        Object headerValue = column.getHeaderValue();
        Component c = headerRenderer.getTableCellRendererComponent(
                table, headerValue, false, false, -1, columnIndex
        );

        int width = c.getPreferredSize().width + paddingPx;

        column.setPreferredWidth(width);
        column.setMinWidth(width); // optional: damit er nicht kleiner wird

        if (force){
            column.setWidth(width);
            column.setResizable(false);
            column.setMaxWidth(width);
        }
        // column.setMaxWidth(width); // optional: damit er auch nicht größer wird
    }

}
