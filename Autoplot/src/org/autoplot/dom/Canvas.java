/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.dom;

import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * The state of the canvas which is the area on which plots are drawn.
 * @author jbf
 */
public class Canvas extends DomNode {

    protected CanvasController controller;

    public Canvas() {
        marginRow= new Row();
        marginRow.setTop("2em");
        marginRow.setBottom("100%-3em");
        marginColumn= new Column();
        marginColumn.setLeft("5em");
        marginColumn.setRight("100%-3em");
    }
    
    
    protected int height = 480;
    public static final String PROP_HEIGHT = "height";

    public int getHeight() {
        return height;
    }

    /**
     * set height before firing off changes
     * @param height 
     */
    public void setHeight(int height) {
        int oldHeight = this.height;
        this.height = height;
        propertyChangeSupport.firePropertyChange(PROP_HEIGHT, oldHeight, height);
    }
    protected int width = 640;
    public static final String PROP_WIDTH = "width";

    public int getWidth() {
        return width;
    }

   /**
    * set width before firing off changes
    * @param width 
    */
    public void setWidth(int width) {
        int oldWidth = this.width;
        this.width = width;
        propertyChangeSupport.firePropertyChange(PROP_WIDTH, oldWidth, width);
    }
    
    /**
     * set both the width and height before firing off changes.
     * @param width the canvas width in pixels.
     * @param height the canvas height in pixels.
     */
    public void setSize( int width, int height ) {
        int oldWidth= this.width;
        int oldHeight= this.height;
        if ( this.controller!=null ) {
            this.controller.dasCanvas.setSize( new Dimension(width,height) );
        }
        if ( width!=oldWidth ) {
            this.width= width;
        }
        if ( height!=oldHeight ) {
            this.height= height;
        }
        if ( width!=oldWidth ) {
            propertyChangeSupport.firePropertyChange(PROP_WIDTH, oldWidth, width);
        }
        if ( height!=oldHeight ) {
            propertyChangeSupport.firePropertyChange(PROP_HEIGHT, oldHeight, height);
        }
    }
    
    protected boolean fitted = true;
    /**
     * boolean property indicates that the canvas is resizable.
     */
    public static final String PROP_FITTED = "fitted";

    public boolean isFitted() {
        return fitted;
    }

    public void setFitted(boolean fitted) {
        boolean oldfitted = this.fitted;
        this.fitted = fitted;
        propertyChangeSupport.firePropertyChange(PROP_FITTED, oldfitted, fitted);
    }

    protected String font = "sans-10";
    public static final String PROP_FONT = "font";

    public String getFont() {
        return font;
    }

    public void setFont(String font) {
        String oldFont = this.font;
        this.font = font;
        propertyChangeSupport.firePropertyChange(PROP_FONT, oldFont, font);
    }

    public static final String PROP_ROWS = "rows";
    protected List<Row> rows = new LinkedList<Row>();

    public Row[] getRows() {
        return rows.toArray(new Row[rows.size()]);
    }

    public void setRows(Row[] rows) {
        Row[] oldRows = this.rows.toArray(new Row[this.rows.size()]);
        this.rows = Arrays.asList(rows);
        propertyChangeSupport.firePropertyChange(PROP_ROWS, oldRows, rows);
    }

    public Row getRows(int index) {
        return this.rows.get(index);
    }

    public void setRows(int index, Row newRows) {
        Row oldRows = this.rows.get(index);
        this.rows.set(index, newRows);
        propertyChangeSupport.fireIndexedPropertyChange(PROP_ROWS, index, oldRows, newRows);
    }

    public static final String PROP_COLUMNS = "columns";
    protected List<Column> columns = new LinkedList<Column>();

    public Column[] getColumns() {
        return columns.toArray(new Column[columns.size()]);
    }

    public void setColumns(Column[] columns) {
        Column[] oldColumns = this.columns.toArray(new Column[this.columns.size()]);
        this.columns = Arrays.asList(columns);
        propertyChangeSupport.firePropertyChange(PROP_COLUMNS, oldColumns, columns);
    }

    public Column getColumns(int index) {
        return this.columns.get(index);
    }

    public void setColumns(int index, Column newColumns) {
        Column oldColumns = this.columns.get(index);
        this.columns.set(index, newColumns);
        propertyChangeSupport.fireIndexedPropertyChange(PROP_COLUMNS, index, oldColumns, newColumns);
    }

    protected Row marginRow;
    public static final String PROP_MARGINROW = "marginRow";

    public Row getMarginRow() {
        return marginRow;
    }

    public void setMarginRow(Row marginRow) {
        Row oldMarginRow = this.marginRow;
        this.marginRow = marginRow;
        propertyChangeSupport.firePropertyChange(PROP_MARGINROW, oldMarginRow, marginRow);
    }

    protected Column marginColumn;
    public static final String PROP_MARGINCOLUMN = "marginColumn";

    public Column getMarginColumn() {
        return marginColumn;
    }

    public void setMarginColumn(Column marginColumn) {
        Column oldMarginColumn = this.marginColumn;
        this.marginColumn = marginColumn;
        propertyChangeSupport.firePropertyChange(PROP_MARGINCOLUMN, oldMarginColumn, marginColumn);
    }

    private Color foreground = Color.BLACK;

    public static final String PROP_FOREGROUND = "foreground";

    public Color getForeground() {
        return foreground;
    }

    public void setForeground(Color foreground) {
        Color oldForeground = this.foreground;
        this.foreground = foreground;
        propertyChangeSupport.firePropertyChange(PROP_FOREGROUND, oldForeground, foreground);
    }

    private Color background = Color.WHITE;

    public static final String PROP_BACKGROUND = "background";

    public Color getBackground() {
        return background;
    }

    public void setBackground(Color background) {
        Color oldBackground = this.background;
        this.background = background;
        propertyChangeSupport.firePropertyChange(PROP_BACKGROUND, oldBackground, background);
    }
        

    public CanvasController getController() {
        return controller;
    }

    @Override
    public List<DomNode> childNodes() {
        ArrayList<DomNode> result = new ArrayList<DomNode>();
        result.add( marginRow );
        result.add( marginColumn );
        result.addAll( rows );
        result.addAll( columns );
        return result;
    }


    @Override
    public DomNode copy() {
        Canvas that= (Canvas)super.copy();
        that.controller= null;

        that.marginRow= (Row) this.marginRow.copy();
        that.marginColumn= (Column) this.marginColumn.copy();

        Row[] rowsCopy= this.getRows();
        for ( int i=0; i<rowsCopy.length; i++ ) {
            rowsCopy[i]= (Row) rowsCopy[i].copy();
        }
        that.setRows( rowsCopy );

        Column[] columnsCopy= this.getColumns();
        for ( int i=0; i<columnsCopy.length; i++ ) {
            columnsCopy[i]= (Column) columnsCopy[i].copy();
        }
        that.setColumns( columnsCopy );

        return that;
    }
    
    @Override
    public void syncTo(DomNode n) {
        if ( !( n instanceof Canvas ) ) throw new IllegalArgumentException("node should be a Canvas");                        
        if ( controller!=null ) {
            controller.syncTo((Canvas)n,new ArrayList<String>(),new HashMap<String, String>());
        } else {
            DomUtil.syncTo( this, n );
        }
    }

    @Override
    public void syncTo(DomNode n,List<String> exclude) {
        if ( !( n instanceof Canvas ) ) throw new IllegalArgumentException("node should be a Canvas");                        
        if ( controller!=null ) {
            controller.syncTo((Canvas)n,exclude,new HashMap<String, String>());
        } else {
            DomUtil.syncTo( this, n, exclude );
        }
    }

    @Override
    public List<Diff> diffs(DomNode node) {
        return DomUtil.getDiffs( this, node );
    }


}
