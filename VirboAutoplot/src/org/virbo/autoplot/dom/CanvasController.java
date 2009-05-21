/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasColumn;
import org.das2.graph.DasDevicePosition;
import org.das2.graph.DasRow;
import org.das2.system.MutatorLock;
import org.virbo.autoplot.layout.LayoutConstants;

/**
 *
 * @author jbf
 */
public class CanvasController extends DomNodeController {

    DasCanvas dasCanvas;
    private Application application;
    private Canvas canvas;
    private static AtomicInteger rowIdNum = new AtomicInteger(0);
    private static AtomicInteger columnIdNum = new AtomicInteger(0);
    public final String MARGINROWID = "marginRow_" + rowIdNum.incrementAndGet();
    public final String MARGINCOLUMNID = "marginColumn_" + columnIdNum.incrementAndGet();

    public CanvasController(Application dom, Canvas canvas) {
        super(canvas);
        this.application = dom;
        this.canvas = canvas;
        canvas.controller = this;
        canvas.getMarginRow().setId(MARGINROWID);
        canvas.getMarginColumn().setId(MARGINCOLUMNID);
    }

    /**
     * support legacy column property of canvas
     * @param row
     */
    public void setColumn(String column) {
        String[] ss = column.split(",");
        canvas.getMarginColumn().setLeft(ss[0]);
        canvas.getMarginColumn().setRight(ss[1]);
    }

    /**
     * support legacy row property of canvas
     * @param row
     */
    public void setRow(String row) {
        String[] ss = row.split(",");
        canvas.getMarginRow().setTop(ss[0]);
        canvas.getMarginRow().setBottom(ss[1]);
    }

    protected void setDasCanvas(DasCanvas canvas) {
        assert (dasCanvas != null);
        this.dasCanvas = canvas;

        ApplicationController ac = application.controller;

        ac.bind(this.canvas, Canvas.PROP_WIDTH, dasCanvas, "preferredWidth"); //TODO: seven second delay
        ac.bind(this.canvas, Canvas.PROP_HEIGHT, dasCanvas, "preferredHeight");
        ac.bind(this.canvas, Canvas.PROP_FITTED, dasCanvas, "fitted");

    }

    public DasCanvas getDasCanvas() {
        return dasCanvas;
    }

    Row getRowFor(Plot domPlot) {
        for (Row row : canvas.getRows()) {
            if (row.getId().equals(domPlot.getRowId())) return row;
        }
        throw new IllegalArgumentException("no row found for " + domPlot);
    }

    Row getRowFor(DasRow dasRow) {
        for (Row row : canvas.getRows()) {
            if (row.controller.getDasRow() == dasRow) return row;
        }
        throw new IllegalArgumentException("no dom row found for " + dasRow);
    }

    /**
     * reset this stack of rows, trying to preserve weights.
     * @param rows
     */
    static void removeGapsAndOverlaps(List<Row> rows) {

        int[] weights = new int[rows.size()]; // in per milli.

        int totalWeight = 0;
        for (int i = 0; i < rows.size(); i++) {
            try {
                double nmin = DasDevicePosition.parseFormatStr(rows.get(i).getTop())[0];
                double nmax = DasDevicePosition.parseFormatStr(rows.get(i).getBottom())[0];
                weights[i] = (int) Math.round((nmax - nmin) * 1000);

            } catch (ParseException ex) {
                weights[i] = 200;
            }
            totalWeight += weights[i];
        }

        // normalize to per thousand.
        for (int i = 0; i < rows.size(); i++) {
            weights[i] = 1000 * weights[i] / totalWeight;
        }
        totalWeight = 1000;

        int t = 0;
        double emIn = rows.size() < 2 ? 0. : 2.;

        for (int idx = 0; idx < weights.length; idx++) {
            DasRow dasRow;
            dasRow = rows.get(idx).controller.getDasRow();
            dasRow.setMinimum(1. * t / totalWeight);
            dasRow.setMaximum(1. * (t + weights[idx]) / totalWeight);
            t += weights[idx];
            dasRow.setEmMinimum(emIn);
            dasRow.setEmMaximum(-emIn);
        }

    }

    void removeGaps() {
        removeGapsAndOverlaps(Arrays.asList(canvas.getRows()));
    }

    /**
     * make a gap in the rows to make way for the new row <tt>row</tt>.
     * @param row the row to insert.  Its position will be the gap.
     * @param trow above or below this row
     * @param position LayoutUtil.BELOW or LayoutUtil.ABOVE.
     */
    void insertGapFor(Row row, Row trow, Object position) {
        MutatorLock lock = changesSupport.mutatorLock();
        lock.lock();

        List<Row> rows = new ArrayList<Row>(Arrays.asList(canvas.getRows()));

        int ipos = 0;
        if (position == LayoutConstants.BELOW) {
            ipos = rows.indexOf(trow) + 1;
        } else {
            ipos = rows.indexOf(trow);
        }

        rows.add(ipos, row);
        row.syncTo(trow, Arrays.asList("id"));
        List<Diff> d = row.diffs(trow, Arrays.asList("id"));
        if (d.size() > 0) {
            row.syncTo(trow, Arrays.asList("id")); // kludge to get around bug where das2 essentially vetos the top
        }
        removeGapsAndOverlaps(rows);

        lock.unlock();

    }

    /**
     * insert the row into the other rows by shrinking them to make room.
     * @param trow row to position above or below, or null if we don't care.
     * @param position LayoutConstants.ABOVE, LayoutConstants.BELOW
     */
    protected Row addInsertRow(Row trow, Object position) {
        MutatorLock lock = changesSupport.mutatorLock();
        lock.lock();

        final Row row = new Row();

        row.setParent(canvas.getMarginRow().getId());
        new RowController(row).createDasPeer(this.canvas, canvas.getMarginRow().getController().getDasRow());

        if (trow != null) insertGapFor(row, trow, position);

        List<Row> rows = new ArrayList<Row>(Arrays.asList(canvas.getRows()));

        int ipos = rows.size();
        if (trow != null) {
            if (position == LayoutConstants.BELOW) {
                ipos = rows.indexOf(trow) + 1;
            } else {
                ipos = rows.indexOf(trow);
            }
        }
        rows.add(ipos, row);

        canvas.setRows(rows.toArray(new Row[rows.size()]));

        row.setId("row_" + rowIdNum.getAndIncrement());

        lock.unlock();

        return row;

    }

    protected Row addRow() {
        return addInsertRow(null, null);
    }

    protected void deleteRow(Row row) {
        MutatorLock lock = changesSupport.mutatorLock();
        lock.lock();

        List<Row> rows = new ArrayList<Row>(Arrays.asList(canvas.getRows()));

        rows.remove(row);

        canvas.setRows(rows.toArray(new Row[rows.size()]));
        lock.unlock();
    }
    
    protected void deleteColumn(Column column) {
        MutatorLock lock = changesSupport.mutatorLock();
        lock.lock();

        List<Column> columns = new ArrayList<Column>(Arrays.asList(canvas.getColumns()));

        columns.remove(column);

        canvas.setColumns(columns.toArray(new Column[columns.size()]));
        lock.unlock();
    }

    protected void syncTo(Canvas canvas) {

        List<Diff> diffs =  canvas.diffs(this.canvas);
        for (Diff d : diffs) {
            try {
                if (d instanceof ArrayNodeDiff) {
                    ArrayNodeDiff and = (ArrayNodeDiff) d;
                }
                d.doDiff(this.canvas);
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                d.doDiff(this.canvas);
            }
        }
        for (Row r : this.canvas.getRows()) {
            if (r.controller == null) {
                new RowController(r).createDasPeer(this.canvas, this.canvas.getMarginRow().getController().getDasRow());
            }
        }
        for (Column r : this.canvas.getColumns()) {
            if (r.controller == null) {
                new ColumnController(r).createDasPeer(this.canvas, this.canvas.getMarginColumn().getController().getDasColumn());
            }
        }
    }

    public String toString() {
        return "" + canvas + " controller";
    }
}
