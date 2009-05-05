/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Timer;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasColumn;
import org.das2.graph.DasDevicePosition;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;
import org.das2.system.MutatorLock;
import org.virbo.autoplot.layout.LayoutConstants;

/**
 *
 * @author jbf
 */
public class CanvasController {
    public final String MARGINROWID = "marginRow";
    public final String MARGINCOLUMNID = "marginColumn";

    DasCanvas dasCanvas;
    private Application application;
    private Canvas canvas;
    private ChangesSupport changesSupport;

    private AtomicInteger rowIdNum = new AtomicInteger(0);
    private AtomicInteger columnIdNum = new AtomicInteger(0);

    public CanvasController(Application dom, Canvas canvas) {
        this.application = dom;
        this.canvas = canvas;
        canvas.setController(this);
        changesSupport = new ChangesSupport(propertyChangeSupport,this);
    }

    protected void setDasCanvas(DasCanvas canvas) {
        assert (dasCanvas != null);
        this.dasCanvas = canvas;

        ApplicationController ac = application.getController();

        ac.bind(this.canvas, Canvas.PROP_SIZE, dasCanvas, "size"); //TODO: check this
        ac.bind(this.canvas, Canvas.PROP_FITTED, dasCanvas, "fitted");

    }

    public DasCanvas getDasCanvas() {
        return dasCanvas;
    }

    public synchronized void registerPendingChange(Object client, Object lockObject) {
        changesSupport.registerPendingChange(client, lockObject);
    }

    public synchronized void performingChange(Object client, Object lockObject) {
        changesSupport.performingChange(client, lockObject);
    }

    public boolean isPendingChanges() {
        return changesSupport.isPendingChanges();
    }

    public synchronized void changePerformed(Object client, Object lockObject) {
        changesSupport.changePerformed(client, lockObject);
    }

    Row getRowFor(Plot domPlot) {
        for ( Row row: canvas.getRows() ) {
            if ( row.getId().equals( domPlot.getRowId() ) ) return row;
        }
        throw new IllegalArgumentException("no row found for "+domPlot );
    }

    Row getRowFor( DasRow dasRow ) {
        for ( Row row: canvas.getRows() ) {
            if ( row.controller.getDasRow()==dasRow ) return row;
        }
        throw new IllegalArgumentException("no dom row found for "+dasRow );
    }

    /**
     * reset this stack of rows, trying to preserve weights.
     * @param rows
     */
    static void removeGapsAndOverlaps( List<Row> rows ) {

        int[] weights= new int[rows.size()]; // in per milli.

        int totalWeight=0;
        for ( int i=0; i<rows.size(); i++ ) {
            try {
                double nmin= DasDevicePosition.parseFormatStr(rows.get(i).getTop())[0];
                double nmax= DasDevicePosition.parseFormatStr(rows.get(i).getBottom())[0];
                weights[i]= (int)Math.round((nmax-nmin)*1000);

            } catch ( ParseException ex ) {
                weights[i]= 200;
            }
                totalWeight+= weights[i];
        }

        // normalize to per thousand.
        for ( int i=0; i<rows.size(); i++ ) {
            weights[i]= 1000 * weights[i] / totalWeight;
        }
        totalWeight= 1000;

        int t=0;
        double emIn= rows.size()<2 ? 0. : 2.;

        for ( int idx=0; idx<weights.length; idx++ ) {
            DasRow dasRow;
            dasRow= rows.get(idx).getController().getDasRow();
            dasRow.setMinimum( 1.* t / totalWeight );
            dasRow.setMaximum( 1.* (t+weights[idx]) / totalWeight );
            t+= weights[idx];
            dasRow.setEmMinimum(emIn);
            dasRow.setEmMaximum(-emIn);
        }
        
    }

    void removeGaps() {
        removeGapsAndOverlaps( Arrays.asList(canvas.getRows()) );
    }

    /**
     * make a gap in the rows to make way for the new row <tt>row</tt>.
     * @param row the row to insert.  Its position will be the gap.
     * @param trow above or below this row
     * @param position LayoutUtil.BELOW or LayoutUtil.ABOVE.
     */
    void insertGapFor( Row row, Row trow, Object position ) {
        MutatorLock lock= changesSupport.mutatorLock();
        lock.lock();

        List<Row> rows = new ArrayList<Row>(Arrays.asList(canvas.getRows()));

        int ipos=0;
        if ( position==LayoutConstants.BELOW ) {
            ipos= rows.indexOf(trow)+1;
        } else {
            ipos= rows.indexOf(trow);
        }

        rows.add( ipos, row );
        row.syncTo(trow,Arrays.asList("id"));
        List<Diff> d= row.diffs(trow,Arrays.asList("id"));
        if ( d.size()>0 ) {
            row.syncTo(trow,Arrays.asList("id")); // kludge to get around bug where das2 essentially vetos the top
        }
        removeGapsAndOverlaps( rows );
        
        lock.unlock();

    }

    /**
     * insert the row into the other rows by shrinking them to make room.
     * @param position LayoutConstants.ABOVE, LayoutConstants.BELOW
     */
    protected Row addInsertRow( Row trow, Object position ) {
        MutatorLock lock= changesSupport.mutatorLock();
        lock.lock();

        final Row row = new Row();
        row.setParent(MARGINROWID);
        row.getController().createDasPeer(this.canvas,application.getController().outerRow);

        insertGapFor( row, trow, position );

        List<Row> rows = new ArrayList<Row>(Arrays.asList(canvas.getRows()));

        int ipos=0;
        if ( position==LayoutConstants.BELOW ) {
            ipos= rows.indexOf(trow)+1;
        } else {
            ipos= rows.indexOf(trow);
        }
        rows.add(ipos, row);

        canvas.setRows( rows.toArray( new Row[rows.size()] ) );

        row.setId( "row_"+rowIdNum.getAndIncrement() );
        row.addPropertyChangeListener( canvas.childListener );

        lock.unlock();
        
        return row;

    }

    protected Row addRow( ) {
        MutatorLock lock= changesSupport.mutatorLock();
        lock.lock();

        List<Row> rows = new ArrayList<Row>(Arrays.asList(canvas.getRows()));
        final Row row = new Row();
        
        row.getController().createDasPeer(this.canvas,application.getController().outerRow);
        row.setParent(MARGINROWID);
        rows.add(row);

        canvas.setRows( rows.toArray(new Row[rows.size()]) );
        row.setId( "row_"+rowIdNum.getAndIncrement() );
        row.addPropertyChangeListener( canvas.childListener );

        lock.unlock();
        return row;
    }

    protected void deleteRow( Row row ) {
        MutatorLock lock= changesSupport.mutatorLock();
        lock.lock();

        List<Row> rows = new ArrayList<Row>(Arrays.asList(canvas.getRows()));

        rows.remove(row);

        canvas.setRows(rows.toArray(new Row[rows.size()]));
        lock.unlock();
    }

    protected void bindTo(final DasRow outerRow, final DasColumn outerColumn) {
        outerRow.addPropertyChangeListener(new PropertyChangeListener() {
            public String toString() {
                return "" + CanvasController.this;
            }
            public void propertyChange(PropertyChangeEvent evt) {
                if (!outerRow.isValueIsAdjusting()) {
                    canvas.setRow(DasRow.formatLayoutStr(outerRow, true) + "," + DasRow.formatLayoutStr(outerRow, false));
                }
            }
        });

        outerColumn.addPropertyChangeListener(new PropertyChangeListener() {

            public String toString() {
                return "" + CanvasController.this ;
            }

            public void propertyChange(PropertyChangeEvent evt) {
                if (!outerColumn.isValueIsAdjusting()) {
                    canvas.setColumn(DasRow.formatLayoutStr(outerColumn, true) + "," + DasRow.formatLayoutStr(outerColumn, false));
                }
            }
        });

        canvas.addPropertyChangeListener(Canvas.PROP_ROW, new PropertyChangeListener() {
            public String toString() {
                return "" + CanvasController.this ;
            }
            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    DasRow.parseLayoutStr(outerRow, canvas.getRow());
                } catch (ParseException ex) {
                    Logger.getLogger(CanvasController.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });

        canvas.addPropertyChangeListener(Canvas.PROP_COLUMN, new PropertyChangeListener() {
            public String toString() {
                return ""+CanvasController.this;
            }
            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    DasColumn.parseLayoutStr(outerColumn, canvas.getColumn());
                } catch (ParseException ex) {
                    Logger.getLogger(CanvasController.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }
    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public String toString() {
        return "" + canvas + " controller";
    }
}
