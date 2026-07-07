package edu.csusm.mailfilter.ui;

import edu.csusm.mailfilter.model.Msg;

import javax.swing.table.AbstractTableModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MailTableModel extends AbstractTableModel {
    private final String[] cols = {"Subject", "From", "Received", "Reasons"};
    private List<Msg> rows = new ArrayList<>();

    public void set(List<Msg> list) {
        rows = new ArrayList<>(Objects.requireNonNullElse(list, List.of()));
        fireTableDataChanged();
    }

    public Msg getMsgAt(int rowIndex) {
        return rows.get(rowIndex);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return cols.length;
    }

    @Override
    public String getColumnName(int column) {
        return cols[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        Msg msg = rows.get(row);

        return switch (column) {
            case 0 -> msg.subject;
            case 1 -> msg.from;
            case 2 -> msg.received;
            case 3 -> String.join("; ", msg.reasons);
            default -> "";
        };
    }
}