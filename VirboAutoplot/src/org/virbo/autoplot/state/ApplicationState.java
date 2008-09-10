/*
 * ApplicationState.java
 *
 * Created on August 8, 2007, 10:22 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.autoplot.state;

import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumUtil;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.PsymConnector;
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
    
    private java.awt.Dimension canvasSize = new java.awt.Dimension( 640, 480 );

    public static final String PROP_CANVASSIZE = "canvasSize";

    public java.awt.Dimension getCanvasSize() {
        return this.canvasSize;
    }

    public void setCanvasSize(java.awt.Dimension newcanvasSize) {
        java.awt.Dimension oldcanvasSize = canvasSize;
        this.canvasSize = newcanvasSize;
        propertyChangeSupport.firePropertyChange(PROP_CANVASSIZE, oldcanvasSize, newcanvasSize);
    }

    protected boolean canvasFitted = true;
    
    /**
     * boolean property indicates that the canvas is resizable.
     */
    public static final String PROP_CANVASFITTED = "canvasFitted";

    public boolean isCanvasFitted() {
        return canvasFitted;
    }

    public void setCanvasFitted(boolean canvasFitted) {
        boolean oldCanvasFitted = this.canvasFitted;
        this.canvasFitted = canvasFitted;
        propertyChangeSupport.firePropertyChange(PROP_CANVASFITTED, oldCanvasFitted, canvasFitted);
    }

    private DatumRange round( DatumRange range ) {
        Datum w= range.width();
        String s;
        double d;
        Datum w0= DatumUtil.asOrderOneUnits(w);
        Datum base= w0;
        Units hu= w0.getUnits();
        if ( range.getUnits().isConvertableTo(Units.us2000) ) {
            base= TimeUtil.prevMidnight(range.min());
        } else {
            base= w.getUnits().createDatum(0);
        }
        double min10= Math.round( ( range.min().subtract(base) ).doubleValue(w0.getUnits()));
        double max10= Math.round( ( range.max().subtract(base) ).doubleValue(w0.getUnits()));
        return new DatumRange( base.add( Datum.create( min10, hu ) ), base.add( Datum.create(max10,hu) ) );
    }
    
//    private String scale( DatumRange range ) {
//        Datum w= range.width();
//        String s;
//        int d;
//        if ( w.getUnits().isConvertableTo(Units.seconds) ) {
//            Datum w0= DatumUtil.asOrderOneUnits(w);
//            d= w0.doubleValue(w0.getUnits());
//        }
//        return s;
//    }
    
    private String describe( DatumRange init, DatumRange fin ) {
        if ( init.getUnits().isConvertableTo( fin.getUnits() ) ) {
            if ( init.contains(fin) ) {
                return "zoom in to "+ DatumUtil.asOrderOneUnits( round(fin).width() );
            } else if ( fin.contains(init) ) {
                return "zoom out to "+ DatumUtil.asOrderOneUnits( round(fin).width() );
            } else if ( init.intersects(fin) ) {
                return "pan"; //+ ( init.min().lt(fin.min() ) ? "right" : "left" ); duh--need to know axis orientation
            } else {
                return "scan"; // + ( init.min().lt(fin.min() ) ? "right" : "left" );
            }
        } else {
            return ""+round(init)+" -> "+ round(fin);
        }
        
        
    }
    
    /**
     * trim the string on the left, leaving the right visible.
     * @param s
     * @return "..."+s.substring()
     */
    private static String abbreviateRight( String s, int len ) {
        if ( s==null ) return "<null>";
        if ( s.length()>len ) {
            s= "..."+s.substring(s.length()-len);
        }
        return s;
    }
    
    /**
     * return a string containing the diffs, comma delineated.
     * @param that the other state to compare.  That appears on the left, this is on the right
     * @return string describing state changes.
     */
    public String diffs( ApplicationState that ) {
        StringBuffer buf= new StringBuffer();
        boolean same= true;
        boolean b;
        b= ( that.surl==this.surl || ( that.surl!=null && that.surl.equals(this.surl) ) ) ;
        if ( !b ) buf.append(", surl "+ abbreviateRight(that.surl, 20) + " to " + abbreviateRight(this.surl,20) );
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
        } else if ( buf.length()>50 ) {
            int changes= 1 + buf.toString().split(",").length;
            return ""+changes + " " + ( changes>1 ? "changes" : "change" );
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
        //propertyChangeSupport.firePropertyChange("showContextOverview", new Boolean(oldShowContextOverview), new Boolean(showContextOverview));
    }

    
    private boolean autoOverview = true;

    public static final String PROP_AUTOOVERVIEW = "autoOverview";

    public boolean isAutoOverview() {
        return this.autoOverview;
    }

    public void setAutoOverview(boolean newautoOverview) {
        boolean oldautoOverview = autoOverview;
        this.autoOverview = newautoOverview;
        //propertyChangeSupport.firePropertyChange(PROP_AUTOOVERVIEW, oldautoOverview, newautoOverview);
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
        this.foregroundColor = new Color( foregroundColor.getRGB() ); //otherwise can't serialize
        propertyChangeSupport.firePropertyChange ("foregroundColor", oldForegroundColor, foregroundColor);
    }

    
    private boolean autoranging = true;

    public static final String PROP_AUTORANGING = "autoranging";

    public boolean isAutoranging() {
        return this.autoranging;
    }

    public void setAutoranging(boolean newautoranging) {
        boolean oldautoranging = autoranging;
        this.autoranging = newautoranging;
        propertyChangeSupport.firePropertyChange(PROP_AUTORANGING, oldautoranging, newautoranging);
    }

    
    private String title = "";

    public static final String PROP_TITLE = "title";

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String newtitle) {
        String oldtitle = title;
        this.title = newtitle;
        propertyChangeSupport.firePropertyChange(PROP_TITLE, oldtitle, newtitle);
    }
    
    protected String xLabel = "";
    public static final String PROP_XLABEL = "xLabel";

    public String getXLabel() {
	return xLabel;
    }

    public void setXLabel(String xLabel) {
	String oldXLabel = this.xLabel;
	this.xLabel = xLabel;
	propertyChangeSupport.firePropertyChange(PROP_XLABEL, oldXLabel, xLabel);
    }
    
    
    protected String yLabel = "";
    public static final String PROP_YLABEL = "yLabel";

    public String getYLabel() {
	return yLabel;
    }

    public void setYLabel(String yLabel) {
	String oldYLabel = this.yLabel;
	this.yLabel = yLabel;
	propertyChangeSupport.firePropertyChange(PROP_YLABEL, oldYLabel, yLabel);
    }

    protected String zLabel = "";
    public static final String PROP_ZLABEL = "zLabel";

    public String getZLabel() {
        return zLabel;
    }

    public void setZLabel(String zLabel) {
        String oldZLabel = this.zLabel;
        this.zLabel = zLabel;
        propertyChangeSupport.firePropertyChange(PROP_ZLABEL, oldZLabel, zLabel);
    }

    protected Options options = new Options();

    public Options getOptions() {
        return options;
    }

    public void setOptions(Options options) {
        this.options = options;
    }

}
