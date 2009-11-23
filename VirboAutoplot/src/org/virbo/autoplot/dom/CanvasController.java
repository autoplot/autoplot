/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.geom.Area;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasColumn;
import org.das2.graph.DasDevicePosition;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;
import org.das2.graph.Painter;
import org.das2.graph.Renderer;
import org.das2.graph.SelectionUtil;
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
    private Timer repaintSoonTimer;

    public CanvasController(Application dom, Canvas canvas) {
        super(canvas);
        this.application = dom;
        this.canvas = canvas;
        canvas.controller = this;
        repaintSoonTimer= new Timer(100, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               dasCanvas.repaint();
            }
        });
        repaintSoonTimer.setRepeats(false);
    }

    /**
     * support legacy column property of canvas
     * @param row
     */
    public void setColumn(String column) {
        String[] ss = column.split(",");
        canvas.getMarginColumn().setLeft(ss[0]);
        canvas.getMarginColumn().setRight(ss[1]);
        canvas.getMarginColumn().setLeft(ss[0]);
    }

    /**
     * support legacy row property of canvas
     * @param row
     */
    public void setRow(String row) {
        String[] ss = row.split(",");
        canvas.getMarginRow().setTop(ss[0]);
        canvas.getMarginRow().setBottom(ss[1]);
        canvas.getMarginRow().setTop(ss[0]);
    }

    protected void setDasCanvas(final DasCanvas canvas) {
        assert (dasCanvas != null);
        this.dasCanvas = canvas;

        ApplicationController ac = application.controller;

        dasCanvas.addComponentListener(new ComponentListener() {

            public void componentResized(ComponentEvent e) {
                if (CanvasController.this.canvas.getWidth() != dasCanvas.getWidth()) {
                    CanvasController.this.canvas.setWidth(dasCanvas.getWidth());
                }
                if (CanvasController.this.canvas.getHeight() != dasCanvas.getHeight()) {
                    CanvasController.this.canvas.setHeight(dasCanvas.getHeight());
                }
            }

            public void componentMoved(ComponentEvent e) {
            }

            public void componentShown(ComponentEvent e) {
            }

            public void componentHidden(ComponentEvent e) {
            }
        });

        this.canvas.addPropertyChangeListener(Canvas.PROP_WIDTH, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                dasCanvas.setPreferredWidth(CanvasController.this.canvas.getWidth());
            }
        });
        this.canvas.addPropertyChangeListener(Canvas.PROP_HEIGHT, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                dasCanvas.setPreferredHeight(CanvasController.this.canvas.getHeight());
            }
        });
        ac.bind(this.canvas, Canvas.PROP_FITTED, dasCanvas, "fitted");
        ac.bind(this.canvas, Canvas.PROP_FONT, dasCanvas, DasCanvas.PROP_BASEFONT, DomUtil.STRING_TO_FONT);  //TODO: bind this to the dasCanvas.

    }

    public DasCanvas getDasCanvas() {
        return dasCanvas;
    }

    /**
     * get the row above or below.
     * @param r
     * @param dir LayoutConstants.ABOVE or LayoutConstants.BELOW
     * @return the row or null if no such row exists.
     */
    Row getRow( Row r, Object dir ) {
        int idx= canvas.rows.indexOf(r);
        if ( idx==-1 ) throw new IllegalArgumentException("canvas doesn't contain this row");
        if ( dir==LayoutConstants.ABOVE ) {
            if ( idx==0 ) return null;
            return canvas.rows.get(idx-1);
        } else if ( dir==LayoutConstants.BELOW ){
            if ( idx==canvas.rows.size()-1 ) return null;
            return canvas.rows.get(idx+1);
        } else {
            throw new IllegalArgumentException("dir must be ABOVE or BELOW");
        }
    }

    Row getRowFor(Plot domPlot) {
        if ( domPlot.getRowId().equals( canvas.marginRow.getId() ) ) return canvas.marginRow;
        for (Row row : canvas.getRows()) {
            if (row.getId().equals(domPlot.getRowId())) {
                return row;
            }
        }
        throw new IllegalArgumentException("no row found for " + domPlot);
    }

    Row getRowFor(DasRow dasRow) {
        for (Row row : canvas.getRows()) {
            if (row.controller.getDasRow() == dasRow) {
                return row;
            }
        }
        throw new IllegalArgumentException("no dom row found for " + dasRow);
    }

    Column getColumnFor(Plot domPlot) {
        if ( domPlot.getColumnId().equals( canvas.marginColumn.getId() ) ) return canvas.marginColumn;
        for (Column column : canvas.getColumns()) {
            if (column.getId().equals(domPlot.getColumnId())) {
                return column;
            }
        }
        throw new IllegalArgumentException("no column found for " + domPlot);
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

    /**
     * reset this stack of columns, trying to preserve weights.
     * @param columns
     */
    static void removeGapsAndOverlapsInColumns(List<Column> columns) {

        int[] weights = new int[columns.size()]; // in per milli.

        int totalWeight = 0;
        for (int i = 0; i < columns.size(); i++) {
            try {
                double nmin = DasDevicePosition.parseFormatStr(columns.get(i).getRight())[0];
                double nmax = DasDevicePosition.parseFormatStr(columns.get(i).getLeft())[0];
                weights[i] = (int) Math.round((nmax - nmin) * 1000);

            } catch (ParseException ex) {
                weights[i] = 200;
            }
            totalWeight += weights[i];
        }

        // normalize to per thousand.
        for (int i = 0; i < columns.size(); i++) {
            weights[i] = 1000 * weights[i] / totalWeight;
        }
        totalWeight = 1000;

        int t = 0;
        double emIn = columns.size() < 2 ? 0. : 2.;

        for (int idx = 0; idx < weights.length; idx++) {
            DasColumn dasColumn;
            dasColumn = columns.get(idx).controller.getDasColumn();
            dasColumn.setMinimum(1. * t / totalWeight);
            dasColumn.setMaximum(1. * (t + weights[idx]) / totalWeight);
            t += weights[idx];
            dasColumn.setEmMinimum(emIn);
            dasColumn.setEmMaximum(-emIn);
        }
    }

    void removeGaps() {
        removeGapsAndOverlaps(Arrays.asList(canvas.getRows()));
        repaintSoon();
    }

    /**
     * the das2 canvas doesn't always repaint when it should, so this
     * allows clients to force a repaint in about 100 ms.
     */
    void repaintSoon() {
        repaintSoonTimer.restart();
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
        List<Diff> d = DomUtil.getDiffs( row, trow, Arrays.asList("id") );
        if (d.size() > 0) {
            row.syncTo(trow, Arrays.asList("id")); // kludge to get around bug where das2 essentially vetos the top
        }
        removeGapsAndOverlaps(rows);

        lock.unlock();
        repaintSoon();
    }

    /**
     * make a gap in the columns to make way for the new column <tt>column</tt>.
     * @param column the column to insert.  Its position will be the gap.
     * @param tcolumn above or below this column
     * @param position LayoutUtil.RIGHT or LayoutUtil.LEFT.
     */
    void insertGapFor(Column column, Column tcolumn, Object position) {
        MutatorLock lock = changesSupport.mutatorLock();
        lock.lock();

        List<Column> columns = new ArrayList<Column>(Arrays.asList(canvas.getColumns()));

        int ipos = 0;
        if (position == LayoutConstants.BELOW) {
            ipos = columns.indexOf(tcolumn) + 1;
        } else {
            ipos = columns.indexOf(tcolumn);
        }

        columns.add(ipos, column);
        column.syncTo(tcolumn, Arrays.asList("id"));
        List<Diff> d = DomUtil.getDiffs( column, tcolumn, Arrays.asList("id") );
        if (d.size() > 0) {
            column.syncTo(tcolumn, Arrays.asList("id")); // kludge to get around bug where das2 essentially vetos the top
        }
        removeGapsAndOverlapsInColumns(columns);

        lock.unlock();
        repaintSoon();
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

        if (trow != null) {
            insertGapFor(row, trow, position);
        }

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

        this.application.getController().assignId(row);

        lock.unlock();

        return row;

    }

    /**
     * insert the column into the other columns by shrinking them to make room.
     * @param tcolumn column to position above or below, or null if we don't care.
     * @param position LayoutConstants.ABOVE, LayoutConstants.BELOW
     */
    protected Column addInsertColumn(Column tcolumn, Object position) {
        MutatorLock lock = changesSupport.mutatorLock();
        lock.lock();

        final Column column = new Column();

        column.setParent(canvas.getMarginColumn().getId());
        new ColumnController(column).createDasPeer(this.canvas, canvas.getMarginColumn().getController().getDasColumn());

        if (tcolumn != null) {
            insertGapFor(column, tcolumn, position);
        }

        List<Column> columns = new ArrayList<Column>(Arrays.asList(canvas.getColumns()));

        int ipos = columns.size();
        if (tcolumn != null) {
            if (position == LayoutConstants.BELOW) {
                ipos = columns.indexOf(tcolumn) + 1;
            } else {
                ipos = columns.indexOf(tcolumn);
            }
        }
        columns.add(ipos, column);

        canvas.setColumns(columns.toArray(new Column[columns.size()]));

        this.application.getController().assignId(column);

        lock.unlock();

        return column;

    }

    /**
     * add rows below the current plot.
     * @param count
     * @return
     */
    public List<Row> addRows(int count) {
        Row trow;
        if (application.getController().getPlot() != null) {
            trow = getRowFor(application.getController().getPlot());
        } else {
            trow = canvas.getRows(canvas.getRows().length - 1);
        }

        List<Row> rows = new ArrayList();
        for (int i = 0; i < count; i++) {
            rows.add(addInsertRow(trow,  LayoutConstants.BELOW ));
        }
        return rows;
    }

    /**
     * insert the row into the other rows by shrinking them to make room.
     * @param trow row to position above or below, or null if we don't care.
     * @param position LayoutConstants.RIGHT, LayoutConstants.LEFT
     */
    public List<Column> addColumns(int count) {
        MutatorLock lock = changesSupport.mutatorLock();
        lock.lock();

        List<Column> result = new ArrayList();
        List<Column> columns = new ArrayList<Column>(Arrays.asList(canvas.getColumns()));

        for (int i = 0; i < count; i++) {
            final Column column = new Column();

            column.setParent(canvas.getMarginRow().getId());

            new ColumnController(column).createDasPeer(this.canvas, canvas.getMarginColumn().getController().getDasColumn());
            this.application.getController().assignId(column);
            result.add(column);

            int lpm = 1000 * i / count;
            int rpm = 1000 * (i + 1) / count;
            int lem= i*50/(count-1);
            int rem= 50*(count-1-i)/(count-1);
            column.setLeft("" + lpm / 10. + "%+"+lem/10.+"em");
            column.setRight("" + rpm / 10. + "%-"+rem/10.+"em");

        }

        columns.addAll(result);

        canvas.setColumns(columns.toArray(new Column[columns.size()]));

        lock.unlock();

        return result;

    }

    /**
     * add a row to the application, below.
     * @return
     */
    public Row addRow() {
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

    protected void syncTo(Canvas canvas, List<String> exclude, Map<String, String> layoutIds) {

        // here we are looking for where the incorrect canvas is attached to the row.
        List<Diff> diffs = canvas.diffs(this.canvas);
        for (Diff d : diffs) {
            if (exclude.contains(d.propertyName())) {
                continue;
            }
            try {
                if (d instanceof ArrayNodeDiff) {
                    ArrayNodeDiff and = (ArrayNodeDiff) d;
                }
                d.doDiff(this.canvas);
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                d.doDiff(this.canvas); // for debugging TODO remove
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

    public void indicateSelection( List<DomNode> selectedItems ) {

        if ( !dasCanvas.isShowing() ) return;
        final List<Shape> sel= new ArrayList<Shape>();
        final List<Rectangle> clip= new ArrayList<Rectangle>();

        for ( Object o: selectedItems ) {
            Area sel1=null;
            if ( o instanceof Plot ) {
                DasPlot p= ((Plot)o).getController().getDasPlot();
                sel.add( SelectionUtil.getSelectionArea( p ) );
                clip.add( p.getBounds() );
            } else if ( o instanceof Panel ) {
                Renderer rend= ((Panel)o).getController().getRenderer();
                DasPlot p= rend.getParent();
                Rectangle r= p.getBounds();
                sel.add( SelectionUtil.getSelectionArea( rend ) );
                clip.add( r );
            }

            if (sel1!=null) sel.add(sel1);
        }

        final Painter p= new Painter() {
            public void paint(Graphics2D g) {
                g.setColor( new Color( 255, 255, 0, 100 ) );
                Shape clip0= g.getClip();
                for ( int i=0; i<sel.size(); i++ ) {
                    Shape s= sel.get(i);
                    Rectangle c= clip.get(i);
                    if ( s!=null ) {
                        g.clip(c);
                        g.fill(s);
                        g.clip(clip0);
                    }
                }
            }
        };

        SwingUtilities.invokeLater(  new Runnable() {
            public void run() {
                dasCanvas.addTopDecorator( p );
                Timer clearSelectionTimer= new Timer( 300, new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        dasCanvas.removeTopDecorator( p );
                    }
                });
                clearSelectionTimer.setRepeats(false);
                clearSelectionTimer.restart();
            }
        } );

    }

    ApplicationController getApplicationController() {
        return this.application.getController();
    }

    public String toString() {
        return "" + canvas + " controller";
    }
}
