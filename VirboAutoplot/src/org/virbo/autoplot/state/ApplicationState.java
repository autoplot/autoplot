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
import org.das2.datum.UnitsUtil;
import org.virbo.autoplot.dom.Panel;

/**
 *
 * @author jbf
 */
public class ApplicationState {
    
    public ApplicationState() {
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
            String scaleString="";
            if ( UnitsUtil.isTimeLocation(fin.getUnits() ) ) {
                Datum scale= DatumUtil.asOrderOneUnits( round(fin).width() );
                scaleString= " to "+scale;
            }
            
            if ( init.contains(fin) ) {
                return "zoom in"+ scaleString;
            } else if ( fin.contains(init) ) {
                return "zoom out"+ scaleString;
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
        
        
        b= that.options.canvasFont.equals( this.options.canvasFont );
        if ( !b ) buf.append(", font " + that.options.canvasFont+ " to " +( this.options.canvasFont ));

        
        if ( buf.length()==0 ) {
            return "";
        } else if ( buf.length()>50 ) {
            return buf.substring(2,50)+"...";
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

    protected Panel panel = null;
    public static final String PROP_PANEL = "panel";

    public Panel getPanel() {
        return panel;
    }

    public void setPanel(Panel panel) {
        Panel oldPanel = this.panel;
        this.panel = panel;
        propertyChangeSupport.firePropertyChange(PROP_PANEL, oldPanel, panel);
    }
    
    private DatumRange xrange;
    
    public DatumRange getXrange() {
        return this.xrange;
    }
    
    public void setXrange(DatumRange xrange) {
        this.xrange = xrange;
    }
    
    private DatumRange yrange;

    public DatumRange getYrange() {
        return this.yrange;
    }

    public void setYrange(DatumRange yrange) {
        this.yrange = yrange;
    }

    private DatumRange zrange;

    public DatumRange getZrange() {
        return this.zrange;
    }

    public void setZrange(DatumRange zrange) {
        this.zrange = zrange;
    }

    private boolean xlog;

    public boolean isXlog() {
        return this.xlog;
    }

    public void setXlog(boolean xlog) {
        this.xlog = xlog;
    }
    
    private boolean ylog;
    
    public boolean isYlog() {
        return this.ylog;
    }

    public void setYlog(boolean ylog) {
        this.ylog = ylog;
    }
    
    private boolean zlog;
    
    public boolean isZlog() {
        return this.zlog;
    }

    public void setZlog(boolean zlog) {
        this.zlog = zlog;
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
    
    private String embeddedDataSet;
    
    public String getEmbeddedDataSet() {
        return this.embeddedDataSet;
    }
    
    public void setEmbeddedDataSet(String embeddedDataSet) {
        this.embeddedDataSet = embeddedDataSet;
    }
    
    private boolean useEmbeddedDataSet=false;
    
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
    
    public boolean isShowContextOverview() {
        return this.showContextOverview;
    }
    
    public void setShowContextOverview(boolean showContextOverview) {
        //boolean oldShowContextOverview = this.showContextOverview;
        this.showContextOverview = showContextOverview;
        //propertyChangeSupport.firePropertyChange("showContextOverview", new Boolean(oldShowContextOverview), new Boolean(showContextOverview));
    }

    
    private boolean autoOverview = true;

    public static final String PROP_AUTOOVERVIEW = "autoOverview";

    public boolean isAutoOverview() {
        return this.autoOverview;
    }

    public void setAutoOverview(boolean newautoOverview) {
        //boolean oldautoOverview = autoOverview;
        this.autoOverview = newautoOverview;
        //propertyChangeSupport.firePropertyChange(PROP_AUTOOVERVIEW, oldautoOverview, newautoOverview);
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

    protected boolean autolabelling = true;
    public static final String PROP_AUTOLABELLING = "autolabelling";

    public boolean isAutolabelling() {
        return autolabelling;
    }

    public void setAutolabelling(boolean autolabelling) {
        boolean oldAutolabelling = this.autolabelling;
        this.autolabelling = autolabelling;
        propertyChangeSupport.firePropertyChange(PROP_AUTOLABELLING, oldAutolabelling, autolabelling);
    }

    protected boolean autolayout = true;
    
    public static final String PROP_AUTOLAYOUT = "autolayout";

    public boolean isAutolayout() {
        return autolayout;
    }

    public void setAutolayout(boolean autolayout) {
        boolean oldAutolayout = this.autolayout;
        this.autolayout = autolayout;
        propertyChangeSupport.firePropertyChange(PROP_AUTOLAYOUT, oldAutolayout, autolayout);
    }
    
    protected String column = null;
    public static final String PROP_COLUMN = "column";

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        String oldColumn = this.column;
        this.column = column;
        propertyChangeSupport.firePropertyChange(PROP_COLUMN, oldColumn, column);
    }

    protected String row1 = null;
    public static final String PROP_ROW1 = "row1";

    public String getRow1() {
        return row1;
    }

    public void setRow1(String row1) {
        String oldRow1 = this.row1;
        this.row1 = row1;
        propertyChangeSupport.firePropertyChange(PROP_ROW1, oldRow1, row1);
    }

    protected String row2 = null;
    public static final String PROP_ROW2 = "row2";

    public String getRow2() {
        return row2;
    }

    public void setRow2(String row2) {
        String oldRow2 = this.row2;
        this.row2 = row2;
        propertyChangeSupport.firePropertyChange(PROP_ROW2, oldRow2, row2);
    }

    protected boolean isotropic = false;
    
    public static final String PROP_ISOTROPIC = "isotropic";

    public boolean isIsotropic() {
        return isotropic;
    }

    public void setIsotropic(boolean isotropic) {
        boolean oldIsotropic = this.isotropic;
        this.isotropic = isotropic;
        propertyChangeSupport.firePropertyChange(PROP_ISOTROPIC, oldIsotropic, isotropic);
    }


    protected Options options = new Options();

    public Options getOptions() {
        return options;
    }

    public void setOptions(Options options) {
        this.options = options;
    }

}
