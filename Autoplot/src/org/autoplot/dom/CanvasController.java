
package org.autoplot.dom;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.geom.GeneralPath;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasColumn;
import org.das2.graph.DasDevicePosition;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;
import org.das2.graph.GraphUtil;
import org.das2.graph.Painter;
import org.das2.graph.Renderer;
import org.das2.graph.SelectionUtil;
import org.das2.util.LoggerManager;
import org.autoplot.dom.ChangesSupport.DomLock;
import org.autoplot.layout.LayoutConstants;
import org.das2.graph.DasAxis;

/**
 * Controller for canvases.
 * @author jbf
 */
public class CanvasController extends DomNodeController {

    protected static final Logger logger= org.das2.util.LoggerManager.getLogger( "autoplot.dom.canvas" );
    
    DasCanvas dasCanvas;
    private final Application dom;
    private final Canvas canvas;
    private final Timer repaintSoonTimer;
    
    /**
     * the setSizeTimer makes sure that the canvas preferred size is set on the event thread.
     */
    private final Timer setSizeTimer;
        
    public CanvasController(Application dom, Canvas canvas) {
        super(canvas);
        this.dom = dom;
        this.canvas = canvas;
        canvas.controller = this;
        repaintSoonTimer= new Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               dasCanvas.repaint();
            }
        });
        repaintSoonTimer.setRepeats(false);
        
        setSizeTimer= new Timer( 100,  new ActionListener() {
            @Override
            public void actionPerformed( ActionEvent e ) {
                setDasCanvasSize();
            }
        } );
        setSizeTimer.setRepeats(false);
        
    }
    
    /**
     * support legacy column property of canvas
     * @param column
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

    /**
     * copy over the current size in the DOM to the DasCanvas.  This is done
     * on the event thread.
     */
    private void setDasCanvasSize() {
        int w= Math.min( 4000, CanvasController.this.canvas.getWidth());
        int h= Math.min( 4000, CanvasController.this.canvas.getHeight());
        Dimension d= new Dimension( w,h );
        logger.log(Level.FINER, "setDasCanvasSize {0}", d);
        dasCanvas.setPreferredSize( d );
        dasCanvas.setSize( d );
    }
    
    protected void setDasCanvas(final DasCanvas canvas) {
        assert (dasCanvas != null);
        this.dasCanvas = canvas;

        ApplicationController ac = dom.controller;

        dasCanvas.addComponentListener(new ComponentListener() {
            @Override
            public void componentResized(ComponentEvent e) {
                if ( setSizeTimer.isRunning() ) {
                    return;
                }
                int w= dasCanvas.getWidth();
                int h= dasCanvas.getHeight();
                logger.log(Level.FINER, "got componentResize {0}x{1}", new Object[]{w, h});
                if (CanvasController.this.canvas.getWidth() != w) {
                    CanvasController.this.canvas.setWidth(w);
                }
                if (CanvasController.this.canvas.getHeight() != h) {
                    CanvasController.this.canvas.setHeight(h);
                }
            }
            @Override
            public void componentMoved(ComponentEvent e) {
            }
            @Override
            public void componentShown(ComponentEvent e) {
            }
            @Override
            public void componentHidden(ComponentEvent e) {
            }
        });

        this.canvas.addPropertyChangeListener(Canvas.PROP_WIDTH, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt);  
                setSizeTimer.restart();
                if ( "true".equals( System.getProperty("java.awt.headless","false") ) ) {
                    setDasCanvasSize();
                }
            }
        });
        this.canvas.addPropertyChangeListener(Canvas.PROP_HEIGHT, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt);  
                setSizeTimer.restart();
                if ( "true".equals( System.getProperty("java.awt.headless","false") ) ) {
                    setDasCanvasSize();
                }
            }
        });
        ac.bind( this.canvas, Canvas.PROP_FITTED, dasCanvas, "fitted");
        ac.bind( this.canvas, Canvas.PROP_FONT, dasCanvas, DasCanvas.PROP_BASEFONT, DomUtil.STRING_TO_FONT);  //TODO: bind this to the dasCanvas.
        ac.bind( this.canvas, Canvas.PROP_BACKGROUND, dasCanvas, "background" );
        ac.bind( this.canvas, Canvas.PROP_FOREGROUND, dasCanvas, "foreground" );

    }
    
    public DasCanvas getDasCanvas() {
        return dasCanvas;
    }

    /**
     * place to register a drop target listener that should be registered with all new components.
     */
    DropTargetListener dropTargetListener;

    public DropTargetListener getDropTargetListener() {
        return dropTargetListener;
    }

    public void setDropTargetListener( DropTargetListener list ) {
        DropTargetListener old= this.dropTargetListener;
        this.dropTargetListener= list;
        propertyChangeSupport.firePropertyChange( "dropTargetListener", old, list );
    }

    /**
     * get the column to the left or right.
     * @param r
     * @param dir LayoutConstants.LEFT or LayoutConstants.RIGHT
     * @return the column or null if no such column exists.
     */
    Column getColumn( Column r, Object dir ) {
        int idx= canvas.columns.indexOf(r);
        if ( idx==-1 ) throw new IllegalArgumentException("canvas doesn't contain this column");
        if ( dir==LayoutConstants.LEFT ) {
            if ( idx==0 ) return null;
            return canvas.columns.get(idx-1);
        } else if ( dir==LayoutConstants.RIGHT ){
            if ( idx==canvas.columns.size()-1 ) return null;
            return canvas.columns.get(idx+1);
        } else {
            throw new IllegalArgumentException("dir must be LEFT or RIGHT");
        }
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

    public Row getRowFor(Plot domPlot) {
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

    private static void resetToFollow( List<Row> rows, String overlap, Row row ) {
        String[] overlaps= overlap.split(" ");
        double min=1;
        double max=0;
        for (String overlap1 : overlaps) {
            for (Row row1 : rows) {
                if (row1.getId().equals(overlap1)) {
                    if (row1.getController().getDasRow().getMinimum() < min) {
                        min = row1.getController().getDasRow().getMinimum();
                    }
                    if (row1.getController().getDasRow().getMaximum() > max) {
                        max = row1.getController().getDasRow().getMaximum();
                    }
                }
            }
        }
        row.getController().dasRow.setMinimum(min);
        row.getController().dasRow.setMaximum(max);
    }

    /**
     * reset this stack of rows, trying to preserve weights.
     * @param rows the rows.
     */
    private static void removeGapsAndOverlaps( Application dom, List<Row> rows, Row newRow, boolean preserveOverlaps) {

        if ( rows.isEmpty() ) return;
        
        int[] weights = new int[rows.size()]; // in per milli.

        int totalWeight = 0;

        String[] overlaps= new String[rows.size()];
        double[] mins= new double[rows.size()];
        double[] maxs= new double[rows.size()];
        int[] count= new int[rows.size()];

        for (int i = 0; i < rows.size(); i++) {
            try {
                double nmin = DasDevicePosition.parseLayoutStr(rows.get(i).getTop())[0];
                double nmax = DasDevicePosition.parseLayoutStr(rows.get(i).getBottom())[0];
                if ( rows.get(i).getController().dasRow.getParent().getHeight()<10 ) {
                        //TODO: why don't we do this right off the bat?  Hudson test autoplot-test034
                        dom.controller.getDasCanvas().setSize( dom.getCanvases(0).getWidth(), dom.getCanvases(0).getHeight() );
                        dom.controller.getDasCanvas().setFont( Font.decode( dom.getOptions().getCanvasFont() ) );
                        logger.log(Level.FINEST, "height<10 branch, now {0}", rows.get(i).getController().dasRow.getParent().getHeight());
                } 
                mins[i]= rows.get(i).getController().dasRow.getDMinimum();
                maxs[i]= rows.get(i).getController().dasRow.getDMaximum(); // bugfix: need to take em offsets into account
                weights[i] = (int) Math.round((nmax - nmin) * 1000);
            } catch (ParseException ex) {
                weights[i] = 200;
            }
            totalWeight += weights[i];
        }

        if ( preserveOverlaps ) {
            // look for overlaps--at least two panels.
            for ( int i=0; i<rows.size(); i++ ) {
                for ( int j=0; j<rows.size(); j++ ) {
                    if ( i==j ) continue;
                    if ( rows.get(i)==newRow || rows.get(j)==newRow ) {
                        continue;
                    }
                    if ( maxs[i]>mins[j] && mins[i]<maxs[j] ) {
                        count[i]++;
                        if ( overlaps[i]==null ) {
                            overlaps[i]= rows.get(j).id;
                        } else {
                            overlaps[i]= overlaps[i] + " " + rows.get(j).id;
                        }
                    }
                }
            }
        }

        for ( int i=0; i<rows.size(); i++ ) {
            if ( count[i]>1 ) {
                totalWeight-= weights[i];
                weights[i]= 0;
            }
        }

        if ( totalWeight==0 ) { // bug reset after "make stack plot".  Avoid the RTE... TODO: I don't really understand what's going on here
            weights[0]= 100;
            totalWeight= 100;
        }

        // normalize to per thousand.
        for (int i = 0; i < rows.size(); i++) {
            if ( totalWeight==0 ) {
                logger.severe("here total weights are zero");
            }
            weights[i] = 1000 * weights[i] / totalWeight;
        }
        totalWeight = 1000;

        int t = 0;

        for (int idx = 0; idx < weights.length; idx++) {
            DasRow dasRow;
            dasRow = rows.get(idx).controller.getDasRow();
            dasRow.setMinimum(1. * t / totalWeight);
            dasRow.setMaximum(1. * (t + weights[idx]) / totalWeight);
            t += weights[idx];
        }

        for ( int i=0; i<rows.size(); i++ ) {
            if ( count[i]>1 ) {
                // get the new range.  Note this is all really kludgy and likely
                // to change.  An alternate way to do this would be to add the N
                // rows to a parent row, and then layout the parent row just like
                // all the others.  This will require more information than is
                // stored in the DOM, and I will try to experiment with this.
                resetToFollow( rows, overlaps[i], rows.get(i) );
            }
        }
    }

    /**
     * reset this stack of columns, trying to preserve weights.
     * @param columns the columns
     */
    static void removeGapsAndOverlapsInColumns(List<Column> columns) {

        if ( columns.isEmpty() ) return;
        
        int[] weights = new int[columns.size()]; // in per milli.

        int totalWeight = 0;
        for (int i = 0; i < columns.size(); i++) {
            try {
                double nmin = DasDevicePosition.parseLayoutStr(columns.get(i).getRight())[0];
                double nmax = DasDevicePosition.parseLayoutStr(columns.get(i).getLeft())[0];
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

    /**
     * return the list of rows attached to the marginRow.
     * @return the list of rows attached to the marginRow.
     */    
    private List<Row> getRowsWithMarginParent() {
        List<Row> result= new ArrayList(canvas.getRows().length);
        for ( Row r: canvas.getRows() ) {
            if ( r.getParent().equals(canvas.getMarginRow().getId()) ) {
                result.add(r);
            }
        }
        return result;
    }
    
    /**
     * remove the gaps and overlaps of the plots attached to the marginRow.
     */
    void removeGaps() {
        removeGapsAndOverlaps( this.dom, getRowsWithMarginParent(), null, true);
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
        DomLock lock = changesSupport.mutatorLock();
        lock.lock("Insert Gap For");
        try {
            List<Row> rows = getRowsWithMarginParent();

            int ipos;
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
            removeGapsAndOverlaps( this.dom, rows, row, true );
        } finally {
            lock.unlock();
        }
        repaintSoon();
    }

    /**
     * make a gap in the columns to make way for the new column <tt>column</tt>.
     * @param column the column to insert.  Its position will be the gap.
     * @param tcolumn above or below this column
     * @param position LayoutUtil.RIGHT or LayoutUtil.LEFT.
     */
    void insertGapFor(Column column, Column tcolumn, Object position) {
        DomLock lock = changesSupport.mutatorLock();
        lock.lock( "Insert Gap For");
        try {
            List<Column> columns = new ArrayList<Column>(Arrays.asList(canvas.getColumns()));

            int ipos;
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
        } finally {
            lock.unlock();
        }
        repaintSoon();
    }

    /**
     * insert the row into the other rows by shrinking them to make room.
     * @param trow row to position above or below, or null if we don't care.
     * @param position LayoutConstants.ABOVE, LayoutConstants.BELOW
     * @return the new Row.
     */
    protected Row addInsertRow(Row trow, Object position) {
        final Row row = new Row();

        DomLock lock = changesSupport.mutatorLock();
        lock.lock("Add Insert Row");
        try {
            row.setParent(canvas.getMarginRow().getId());
            new RowController(row).createDasPeer(this.canvas, canvas.getMarginRow().getController().getDasRow());

            this.dom.getController().assignId(row);
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

            this.dom.getController().assignId(row);
        } finally {
            lock.unlock();
        }

        return row;

    }

    /**
     * insert the column into the other columns by shrinking them to make room.
     * @param tcolumn column to position right or left, or null if we don't care.
     * @param position LayoutConstants.RIGHT, LayoutConstants.LEFT
     * @return the new Column.
     */
    protected Column addInsertColumn(Column tcolumn, Object position) {
        final Column column = new Column();

        DomLock lock = changesSupport.mutatorLock();
        lock.lock( "Add Insert Column");
        try {

            column.setParent(canvas.getMarginColumn().getId());
            new ColumnController(column).createDasPeer(this.canvas, canvas.getMarginColumn().getController().getDasColumn());

            if (tcolumn != null) {
                insertGapFor(column, tcolumn, position);
            }

            List<Column> columns = new ArrayList<Column>(Arrays.asList(canvas.getColumns()));

            int ipos = columns.size();
            if (tcolumn != null) {
                if (position == LayoutConstants.RIGHT) {
                    ipos = columns.indexOf(tcolumn) + 1;
                } else {
                    ipos = columns.indexOf(tcolumn);
                }
            }
            columns.add(ipos, column);

            canvas.setColumns(columns.toArray(new Column[columns.size()]));

            this.dom.getController().assignId(column);
        } finally {
            lock.unlock();
        }

        return column;

    }

    /**
     * add rows below the current plot.
     * @param count
     * @return
     */
    public List<Row> addRows(int count) {
        return addRows( count, LayoutConstants.BELOW );
    }

    /**
     * add rows below the current plot.
     * @param count
     * @param dir
     * @return
     */
    public List<Row> addRows(int count, Object dir ) {
        Row trow;
        if (dom.getController().getPlot() != null) {
            trow = getRowFor(dom.getController().getPlot());
        } else {
            trow = canvas.getRows(canvas.getRows().length - 1);
        }

        List<Row> rows = new ArrayList();
        for (int i = 0; i < count; i++) {
            Row newRow= addInsertRow(trow,  dir );
            rows.add(newRow);
            trow= newRow;
        }
        return rows;
    }

    /**
     * add columns to the current plot.
     * @param count number of columns to add
     * @return a list of the new Columns.
     */
    public List<Column> addColumns(int count) {
        List<Column> result = new ArrayList();

        DomLock lock = changesSupport.mutatorLock();
        lock.lock( "Add Columns");
        try {

            List<Column> columns = new ArrayList<Column>(Arrays.asList(canvas.getColumns()));

            for (int i = 0; i < count; i++) {
                final Column column = new Column();

                column.setParent(canvas.getMarginRow().getId());

                new ColumnController(column).createDasPeer(this.canvas, canvas.getMarginColumn().getController().getDasColumn());
                this.dom.getController().assignId(column);
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
        } finally {
            lock.unlock();
        }

        return result;

    }

    /**
     * add a column to the application to the right of the other columns.
     * @return the column
     */
    public Column addColumn() {
        return addInsertColumn( null,null );
    }

    /**
     * add a row to the application, below.
     * @return the row
     */
    public Row addRow() {
        return addInsertRow(null, null);
    }

    protected void deleteRow(Row row) {
        DomLock lock = changesSupport.mutatorLock();
        lock.lock("Delete Row");
        try {
            List<Row> rows = new ArrayList<>(Arrays.asList(canvas.getRows()));

            rows.remove(row);
            row.getController().getDasRow().removeListeners();
            
            canvas.setRows(rows.toArray(new Row[rows.size()]));
        } finally {
            lock.unlock();
        }
    }

    protected void deleteColumn(Column column) {
        DomLock lock = changesSupport.mutatorLock();
        lock.lock("Delete Column");
        try {
            List<Column> columns = new ArrayList<>(Arrays.asList(canvas.getColumns()));

            columns.remove(column);
            column.getController().getDasColumn().removeListeners();

            canvas.setColumns(columns.toArray(new Column[columns.size()]));
        } finally {
            lock.unlock();
        }
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
                logger.log( Level.WARNING, null, ex );
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

    private List<DomNode> currentSelectionItems;
    private long currentSelectionBirthtime=0;

    /**
     * flash the selected plots and plotElements, by temporarily 
     * adding a painter to the canvas.
     * @param selectedItems the items to flash.
     */
    public void indicateSelection( List<DomNode> selectedItems ) {

        if ( !dasCanvas.isShowing() ) return;

        final List<Shape> sel= new ArrayList<>();
        final List<Rectangle> clip= new ArrayList<>();

        final long t1= System.currentTimeMillis();

        synchronized (this) {
            if ( currentSelectionItems!=null ) {
                return;
            } 
            currentSelectionItems= selectedItems;
            
            if ( t1-currentSelectionBirthtime > 3000 ) { // kludge where these are lost.
                currentSelectionItems= selectedItems;
            }
            
            currentSelectionBirthtime= t1;
        }

        logger.log(Level.FINER,"get highlite area");
        for ( Object o: selectedItems ) {
            if ( o instanceof Plot ) {
                DasPlot p= ((Plot)o).getController().getDasPlot();
                GeneralPath result= new GeneralPath();
                GraphUtil.reducePath( SelectionUtil.getSelectionArea( p ).getPathIterator(null), result, 10 );
                sel.add( result );
                clip.add( p.getBounds() );
            } else if ( o instanceof PlotElement ) {
                Renderer rend= ((PlotElement)o).getController().getRenderer();
                if ( rend==null ) return;// transitional case
                DasPlot p= rend.getParent();
                if ( p==null ) return; // transitional case
                Rectangle r= p.getBounds();
                GeneralPath result= new GeneralPath();
                Shape gp= SelectionUtil.getSelectionArea( rend );
                if ( gp!=null ) {
                    GraphUtil.reducePath( gp.getPathIterator(null), result, 10 );
                    sel.add( result );
                    clip.add( r );
                } else {
                    logger.warning("reducePath contract broken by renderer that returns null.");
                }
            }
            logger.log(Level.FINER,"got highlite area in {0} millis", (System.currentTimeMillis()-t1));
        }
        
        
        final Painter p= new Painter() {
            @Override
            public void paint(Graphics2D g) {
                long t0= System.currentTimeMillis();
                
                logger.log(Level.FINER,"enter paint decorator");
                
                // note it's non-trivial to apply transforms as the plot moves.
                if ( dasCanvas.lisPaintingForPrint() ) {
                    logger.fine("not painting select");
                    return;
                }
                g.setColor( new Color( 255, 255, 0, 100 ) );
                Shape clip0= g.getClip();
                for ( int i=0; i<sel.size(); i++ ) {
                    Shape s= sel.get(i);
                    Rectangle c= clip.get(i);
                    if ( s!=null ) {
                        g.clip(c);
                        g.fill(s);
                        g.setClip(clip0);
                    } else {
                        g.clip(c);
                        Stroke stroke0= g.getStroke();
                        g.setStroke( new BasicStroke(10.f) );
                        g.draw(c);
                        g.setClip(clip0);
                        g.setStroke(stroke0);
                    }
                }
                logger.log(Level.FINER, "paint decorator in {0} ms", (System.currentTimeMillis()-t0));
            }
        };
        
        logger.log(Level.FINER, "set up decorator {0} {1}", new Object[]{p, System.currentTimeMillis()-t1});
            
        boolean doDecorate= true;
        if ( doDecorate ) {
            logger.log(Level.FINER, "create decorator {0} {1}", new Object[]{p, System.currentTimeMillis()-t1});
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    logger.log(Level.FINER, "add decorator {0} {1}", new Object[]{p, System.currentTimeMillis()-t1});
                    dasCanvas.addTopDecorator( p );
                    Timer clearSelectionTimer= new Timer( 300, new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            logger.log(Level.FINEST, "rm decorator {0} {1}", new Object[]{p, System.currentTimeMillis()-t1});
                            dasCanvas.removeTopDecorator( p );
                            currentSelectionItems= null;
                            logger.log(Level.FINER, "done rm decorator {0} {1}", new Object[]{p, System.currentTimeMillis()-t1});
                        }
                    });
                    clearSelectionTimer.setRepeats(false);
                    clearSelectionTimer.restart();
                    logger.log(Level.FINER, "done add decorator {0} {1}", new Object[]{p, System.currentTimeMillis()-t1});
                }
            };
            SwingUtilities.invokeLater( run );
            //if ( SwingUtilities.isEventDispatchThread() ) {
            //    run.run();
            //} else {
            //    SwingUtilities.invokeLater( run );
            //}
            logger.log(Level.FINER, "highlite selection in {0}ms", (System.currentTimeMillis()-t1));
        }

    }

    ApplicationController getApplicationController() {
        return this.dom.getController();
    }

    @Override
    public String toString() {
        return "" + canvas + " controller";
    }

    /**
     * add a column with the spec (e.g. "30%+1em,60%-4em").  If another column with 
     * the same spec is found, then just return that column.
     * @param spec spec like "30%+1em,60%-4em"
     * @return a column that implements.
     */
    public Column maybeAddColumn(String spec) {
        String[] ss= spec.split(",");
        if ( ss.length!=2 ) {
            throw new IllegalArgumentException("spec format error, expected comma: "+spec);
        }
        try {
            for ( Column c: canvas.getColumns() ) {
                if ( c.controller.isLayoutEqual( spec ) ) return c;
            }
        } catch ( ParseException ex ) { 
            throw new RuntimeException(ex);
        }
        final Column column = new Column();
        DomLock lock = changesSupport.mutatorLock();
        lock.lock("Maybe Add Column");
        try {
            column.setParent("");
            new ColumnController(column).createDasPeer(this.canvas, null );

            this.dom.getController().assignId(column);

            List<Column> columns = new ArrayList<Column>(Arrays.asList(canvas.getColumns()));
            columns.add(column);
            canvas.setColumns(columns.toArray(new Column[columns.size()]));

            this.dom.getController().assignId(column);
            column.setLeft(ss[0]);
            column.setRight(ss[1]);
            
        } finally {
            lock.unlock();         
        }
        return column;        
    }

    /**
     * add a row with the spec (e.g. "30%+1em,60%-4em").  If another row with 
     * the same spec is found, then just return that row.
     * @param spec spec like "30%+1em,60%-4em"
     * @return a row that implements.
     */
    public Row maybeAddRow(String spec) {
        String[] ss= spec.split(",");
        if ( ss.length!=2 ) {
            throw new IllegalArgumentException("spec format error, expected comma: "+spec);
        }
        try {
            for ( Row r: canvas.getRows() ) {
                if ( r.controller.isLayoutEqual( spec ) ) return r;
            }
        } catch ( ParseException ex ) { 
            throw new RuntimeException(ex);
        }        
        
        final Row row = new Row();
        
        DomLock lock = changesSupport.mutatorLock();
        lock.lock("Maybe Add Row");
        try {
            row.setParent("");
            new RowController(row).createDasPeer(this.canvas, null );

            this.dom.getController().assignId(row);

            List<Row> rows = new ArrayList<Row>(Arrays.asList(canvas.getRows()));
            rows.add(row);
            canvas.setRows(rows.toArray(new Row[rows.size()]));

            this.dom.getController().assignId(row);
            row.setTop(ss[0]);
            row.setBottom(ss[1]);
            
        } finally {
            lock.unlock();         
        }
        
        return row;
    }

}
