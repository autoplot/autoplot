/*
 * ApplicationState.java
 *
 * Created on August 8, 2007, 10:22 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.autoplot.state;

import edu.uiowa.physics.pw.das.datum.DatumRange;
import edu.uiowa.physics.pw.das.graph.DefaultPlotSymbol;
import edu.uiowa.physics.pw.das.graph.PsymConnector;
import java.awt.Color;

/**
 *
 * @author jbf
 */
public class ApplicationState {
    
    /** Creates a new instance of ApplicationState */
    public ApplicationState() {
    }
    
    /**
     * Holds value of property surl.
     */
    private String surl;
    
    /**
     * Getter for property surl.
     * @return Value of property surl.
     */
    public String getSurl() {
        return this.surl;
    }
    
    /**
     * Setter for property surl.
     * @param surl New value of property surl.
     */
    public void setSurl(String surl) {
        this.surl = surl;
    }
    
    /**
     * Holds value of property xrange.
     */
    private DatumRange xrange;
    
    /**
     * Getter for property xrange.
     * @return Value of property xrange.
     */
    public DatumRange getXrange() {
        return this.xrange;
    }
    
    /**
     * Setter for property xrange.
     * @param xrange New value of property xrange.
     */
    public void setXrange(DatumRange xrange) {
        this.xrange = xrange;
    }
    
    /**
     * Holds value of property yrange.
     */
    private DatumRange yrange;
    
    /**
     * Getter for property yrange.
     * @return Value of property yrange.
     */
    public DatumRange getYrange() {
        return this.yrange;
    }
    
    /**
     * Setter for property yrange.
     * @param yrange New value of property yrange.
     */
    public void setYrange(DatumRange yrange) {
        this.yrange = yrange;
    }
    
    /**
     * Holds value of property zrange.
     */
    private DatumRange zrange;
    
    /**
     * Getter for property zrange.
     * @return Value of property zrange.
     */
    public DatumRange getZrange() {
        return this.zrange;
    }
    
    /**
     * Setter for property zrange.
     * @param zrange New value of property zrange.
     */
    public void setZrange(DatumRange zrange) {
        this.zrange = zrange;
    }
    
    /**
     * Holds value of property xlog.
     */
    private boolean xlog;
    
    /**
     * Getter for property xlog.
     * @return Value of property xlog.
     */
    public boolean isXlog() {
        return this.xlog;
    }
    
    /**
     * Setter for property xlog.
     * @param xlog New value of property xlog.
     */
    public void setXlog(boolean xlog) {
        this.xlog = xlog;
    }
    
    /**
     * Holds value of property ylog.
     */
    private boolean ylog;
    
    /**
     * Getter for property ylog.
     * @return Value of property ylog.
     */
    public boolean isYlog() {
        return this.ylog;
    }
    
    /**
     * Setter for property ylog.
     * @param ylog New value of property ylog.
     */
    public void setYlog(boolean ylog) {
        this.ylog = ylog;
    }
    
    /**
     * Holds value of property zlog.
     */
    private boolean zlog;
    
    /**
     * Getter for property zlog.
     * @return Value of property zlog.
     */
    public boolean isZlog() {
        return this.zlog;
    }
    
    /**
     * Setter for property zlog.
     * @param zlog New value of property zlog.
     */
    public void setZlog(boolean zlog) {
        this.zlog = zlog;
    }
    
    /**
     * Holds value of property symbolSize.
     */
    private double symbolSize;
    
    /**
     * Getter for property symbolSize.
     * @return Value of property symbolSize.
     */
    public double getSymbolSize() {
        return this.symbolSize;
    }
    
    /**
     * Setter for property symbolSize.
     * @param symbolSize New value of property symbolSize.
     */
    public void setSymbolSize(double symbolSize) {
        this.symbolSize = symbolSize;
    }
    
    /**
     * Holds value of property lineWidth.
     */
    private double lineWidth;
    
    /**
     * Getter for property lineWidth.
     * @return Value of property lineWidth.
     */
    public double getLineWidth() {
        return this.lineWidth;
    }
    
    /**
     * Setter for property lineWidth.
     * @param lineWidth New value of property lineWidth.
     */
    public void setLineWidth(double lineWidth) {
        this.lineWidth = lineWidth;
    }
    
    /**
     * Holds value of property colortable.
     */
    private String colortable;
    
    /**
     * Getter for property colortable.
     * @return Value of property colortable.
     */
    public String getColortable() {
        return this.colortable;
    }
    
    /**
     * Setter for property colortable.
     * @param colortable New value of property colortable.
     */
    public void setColortable(String colortable) {
        this.colortable = colortable;
    }
    
    private String describe( DatumRange init, DatumRange fin ) {
        if ( init.getUnits().isConvertableTo( fin.getUnits() ) ) {
            if ( init.contains(fin) ) {
                return "zoom in";
            } else if ( fin.contains(init) ) {
                return "zoom out";
            } else if ( init.intersects(fin) ) {
                return "pan";
            } else {
                return "scan";
            }
        } else {
            return ""+init+" -> "+ fin;
        }
        
        
    }
    
    /**
     * return a string containing the diffs, comma delineated.
     */
    public String diffs( ApplicationState that ) {
        StringBuffer buf= new StringBuffer();
        boolean same= true;
        boolean b;
        b= ( that.surl==this.surl || ( that.surl!=null && that.surl.equals(this.surl) ) ) ;
        if ( !b ) buf.append(", surl "+ that.surl + " to " + this.surl);
        b=  that.colortable.equals(this.colortable) ;
        if ( !b ) buf.append(", colortable " + that.colortable + " to "+ this.colortable );
        b=  that.lineWidth==this.lineWidth ;
        if ( !b ) buf.append(", lineWidth " + that.lineWidth+ " to " +this.lineWidth);
        b=  that.symbolSize==this.symbolSize ;
        if ( !b ) buf.append(", symbolSize " + that.symbolSize+ " to " +this.symbolSize);
        b= that.xlog==this.xlog ;
        if ( !b ) buf.append(", xlog " + that.xlog+ " to " +this.xlog);
        b=  that.xrange.equals(this.xrange) ;
        if ( !b ) buf.append(", xrange "+ describe( that.xrange , this.xrange ) );
        b= that.ylog==this.ylog ;
        if ( !b ) buf.append(", ylog " + that.ylog+ " to " +this.ylog);
        b= that.yrange.equals(this.yrange) ;
        if ( !b ) buf.append(", yrange " + describe( that.yrange, this.yrange ) );
        b= that.zlog==this.zlog ;
        if ( !b ) buf.append(", zlog " + that.zlog+ " to " +this.zlog);
        b= that.zrange.equals(this.zrange) ;
        if ( !b ) buf.append(", zrange " + describe( that.zrange, this.zrange ) );
        b= that.validRange.equals( this.validRange );
        if ( !b ) buf.append(", validRange " + that.validRange+ " to " +( this.validRange ) );
        b= that.fill.equals( this.fill );
        if ( !b ) buf.append(", fill " + that.fill+ " to " +( this.fill ));
        
        b= that.plotSymbol.equals( this.plotSymbol ) ;
        if ( !b ) buf.append(", plotSymbol " + that.plotSymbol+ " to " +( this.plotSymbol ));
        b= that.symbolConnector.equals( this.symbolConnector ) ;
        if ( !b ) buf.append(", symbolConnector " + that.symbolConnector+ " to " +( this.symbolConnector ));
        
        b= that.color.equals( this.color );
        if ( !b ) buf.append(", color " + that.color+ " to " +( this.color ));
        b= that.fillColor.equals( this.fillColor );
        if ( !b ) buf.append(", fillColor " + that.fillColor+ " to " +( this.fillColor ));
        
        b= that.fillToReference==this.fillToReference;
        if ( !b ) buf.append(", fillToReference " + that.fillToReference+ " to " +( this.fillToReference ));
        
        b= that.reference.equals( this.reference );
        if ( !b ) buf.append(", reference " + that.reference+ " to " +( this.reference ));
        
        
        if ( buf.length()==0 ) {
            return "";
        } else {
            return buf.substring(2);
        }
    }
    
    public boolean equals( Object o ) {
        if ( o instanceof ApplicationState ) {
            ApplicationState that= (ApplicationState)o;
            String diffs= diffs(that);
            return diffs.length()==0;
        } else {
            return false;
        }
    }
    
    /**
     * Holds value of property validRange.
     */
    private String validRange="";
    
    /**
     * Getter for property validRange.
     * @return Value of property validRange.
     */
    public String getValidRange() {
        return this.validRange;
    }
    
    /**
     * Setter for property validRange.
     * @param validRange New value of property validRange.
     */
    public void setValidRange(String validRange) {
        this.validRange = validRange;
    }
    
    /**
     * Holds value of property fill.
     */
    private String fill="";
    
    /**
     * Getter for property fill.
     * @return Value of property fill.
     */
    public String getFill() {
        return this.fill;
    }
    
    /**
     * Setter for property fill.
     * @param fill New value of property fill.
     */
    public void setFill(String fill) {
        this.fill = fill;
    }
    
    /**
     * Holds value of property fillToReference.
     */
    private boolean fillToReference;
    
    /**
     * Getter for property fillBelow.
     * @return Value of property fillBelow.
     */
    public boolean isFillToReference() {
        return this.fillToReference;
    }
    
    /**
     * Setter for property fillBelow.
     * @param fillBelow New value of property fillBelow.
     */
    public void setFillToReference(boolean fillToReference) {
        this.fillToReference = fillToReference;
    }
    
    /**
     * Holds value of property color.
     */
    private Color color;
    
    /**
     * Getter for property color.
     * @return Value of property color.
     */
    public Color getColor() {
        return this.color;
    }
    
    /**
     * Setter for property color.
     * @param color New value of property color.
     */
    public void setColor(Color color) {
        this.color = color;
    }
    
    /**
     * Holds value of property fillColor.
     */
    private Color fillColor;
    
    /**
     * Getter for property fillColor.
     * @return Value of property fillColor.
     */
    public Color getFillColor() {
        return this.fillColor;
    }
    
    /**
     * Setter for property fillColor.
     * @param fillColor New value of property fillColor.
     */
    public void setFillColor(Color fillColor) {
        this.fillColor = fillColor;
    }
    
    /**
     * Holds value of property plotSymbol.
     */
    private String plotSymbol= DefaultPlotSymbol.NONE.toString();
    
    /**
     * Getter for property plotSymbol.
     * @return Value of property plotSymbol.
     */
    public String getPlotSymbol() {
        return this.plotSymbol;
    }
    
    /**
     * Setter for property plotSymbol.
     * @param plotSymbol New value of property plotSymbol.
     */
    public void setPlotSymbol(String plotSymbol) {
        this.plotSymbol = plotSymbol;
    }
    
    /**
     * Holds value of property symbolConnector.
     */
    private String symbolConnector= PsymConnector.NONE.toString();
    
    /**
     * Getter for property symbolConnector.
     * @return Value of property symbolConnector.
     */
    public String getSymbolConnector() {
        return this.symbolConnector;
    }
    
    /**
     * Setter for property symbolConnector.
     * @param symbolConnector New value of property symbolConnector.
     */
    public void setSymbolConnector(String symbolConnector) {
        this.symbolConnector = symbolConnector;
    }
    
    /**
     * Holds value of property reference.
     */
    private String reference= "fill";
    
    /**
     * Getter for property reference.
     * @return Value of property reference.
     */
    public String getReference() {
        return this.reference;
    }
    
    /**
     * Setter for property reference.
     * @param reference New value of property reference.
     */
    public void setReference(String reference) {
        this.reference = reference;
    }
    
    /**
     * Holds value of property embeddedDataSet.
     */
    private String embeddedDataSet;
    
    /**
     * Getter for property embeddedDataSet.
     * @return Value of property embeddedDataSet.
     */
    public String getEmbeddedDataSet() {
        return this.embeddedDataSet;
    }
    
    /**
     * Setter for property embeddedDataSet.
     * @param embeddedDataSet New value of property embeddedDataSet.
     */
    public void setEmbeddedDataSet(String embeddedDataSet) {
        this.embeddedDataSet = embeddedDataSet;
    }
    
    /**
     * Holds value of property useEmbeddedDataSet.
     */
    private boolean useEmbeddedDataSet=false;
    
    /**
     * Getter for property useEmbeddedDataSet.
     * @return Value of property useEmbeddedDataSet.
     */
    public boolean isUseEmbeddedDataSet() {
        return this.useEmbeddedDataSet;
    }
    
    /**
     * Setter for property useEmbeddedDataSet.
     * @param useEmbeddedDataSet New value of property useEmbeddedDataSet.
     */
    public void setUseEmbeddedDataSet(boolean useEmbeddedDataSet) {
        this.useEmbeddedDataSet = useEmbeddedDataSet;
    }
    
    /**
     * Holds value of property showContextOverview.
     */
    private boolean showContextOverview;
    
    /**
     * Utility field used by bound properties.
     */
    private java.beans.PropertyChangeSupport propertyChangeSupport =  new java.beans.PropertyChangeSupport(this);
    
    /**
     * Adds a PropertyChangeListener to the listener list.
     * @param l The listener to add.
     */
    public void addPropertyChangeListener(java.beans.PropertyChangeListener l) {
        propertyChangeSupport.addPropertyChangeListener(l);
    }
    
    /**
     * Removes a PropertyChangeListener from the listener list.
     * @param l The listener to remove.
     */
    public void removePropertyChangeListener(java.beans.PropertyChangeListener l) {
        propertyChangeSupport.removePropertyChangeListener(l);
    }
    
    /**
     * Getter for property showContextOverview.
     * @return Value of property showContextOverview.
     */
    public boolean isShowContextOverview() {
        return this.showContextOverview;
    }
    
    /**
     * Setter for property showContextOverview.
     * @param showContextOverview New value of property showContextOverview.
     */
    public void setShowContextOverview(boolean showContextOverview) {
        boolean oldShowContextOverview = this.showContextOverview;
        this.showContextOverview = showContextOverview;
        propertyChangeSupport.firePropertyChange("showContextOverview", new Boolean(oldShowContextOverview), new Boolean(showContextOverview));
    }

    /**
     * Holds value of property backgroundColor.
     */
    private Color backgroundColor;

    /**
     * Getter for property backgroundColor.
     * @return Value of property backgroundColor.
     */
    public Color getBackgroundColor() {
        return this.backgroundColor;
    }

    /**
     * Setter for property backgroundColor.
     * @param backgroundColor New value of property backgroundColor.
     */
    public void setBackgroundColor(Color backgroundColor) {
        Color oldBackgroundColor = this.backgroundColor;
        this.backgroundColor = backgroundColor;
        propertyChangeSupport.firePropertyChange ("backgroundColor", oldBackgroundColor, backgroundColor);
    }

    /**
     * Holds value of property foregroundColor.
     */
    private Color foregroundColor;

    /**
     * Getter for property foregroundColor.
     * @return Value of property foregroundColor.
     */
    public Color getForegroundColor() {
        return this.foregroundColor;
    }

    /**
     * Setter for property foregroundColor.
     * @param foregroundColor New value of property foregroundColor.
     */
    public void setForegroundColor(Color foregroundColor) {
        Color oldForegroundColor = this.foregroundColor;
        this.foregroundColor = foregroundColor;
        propertyChangeSupport.firePropertyChange ("foregroundColor", oldForegroundColor, foregroundColor);
    }
    
}
