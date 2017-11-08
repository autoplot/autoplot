
package org.autoplot.dom;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
//import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
//import org.das2.datum.UnitsUtil;

/**
 * The state of an axis, X, Y, or a Z axis colorbar, such as range and the
 * scale type.
 * @author jbf
 */
public class Axis extends DomNode {

    public static final DatumRange DEFAULT_RANGE=  new DatumRange(0, 100, Units.dimensionless);

    protected DatumRange range = DEFAULT_RANGE;
    
    public static final String PROP_RANGE = "range";

    public DatumRange getRange() {
        return range;
    }

    /**
     * set the range property.  Right now, if the axis is log, and the range
     * contains negative values, then the axis will be in an invalid state.
     * We cannot change the range setting automatically because the next setting
     * may be the log property, then the order of property setter calls would
     * matter.  TODO: consider mutatorLock...  TODO: consider making the axis
     * linear...
     * @param range
     */
    public void setRange(DatumRange range) {
        if ( range==null ) {
            logger.log( Level.WARNING, "range set to null!");
            return;
        }
//        System.err.println("range="+range);
//        if ( this.controller!=null 
//            && this.controller.dasAxis.isHorizontal()
//            && !org.das2.datum.UnitsUtil.isTimeLocation( range.getUnits() ) 
//            && range.width().value()==1. ) {
//            logger.log( Level.WARNING, "breakpoint here in setRange");
//        }
//        if ( this.controller!=null 
//            && this.controller.dasAxis.isHorizontal()
//            && org.das2.datum.UnitsUtil.isTimeLocation( range.getUnits() ) 
//            && range.intersects(org.das2.datum.DatumRangeUtil.parseTimeRangeValid("2013-10-09T19:00:00/2013-10-09T19:40:00") ) ) {
//            logger.log( Level.WARNING, "breakpoint here in setRange");
//        }        
//        if ( this.id.startsWith("xaxis_0") && !UnitsUtil.isTimeLocation(range.getUnits()) && range.max().value()>=20000 ) {
//            logger.log( Level.WARNING, "breakpoint here in setRange");
//        }
        DatumRange oldRange = this.range;
        this.range= range;
        propertyChangeSupport.firePropertyChange(PROP_RANGE, oldRange, range);
    }
    
    private Datum scale = Units.dimensionless.createDatum(.1);

    public static final String PROP_SCALE = "scale";

    public Datum getScale() {
        return scale;
    }

    public void setScale(Datum scale) {
        Datum oldScale = this.scale;
        this.scale = scale;
        propertyChangeSupport.firePropertyChange(PROP_SCALE, oldScale, scale);
    }

    protected boolean log = false;
    public static final String PROP_LOG = "log";

    public boolean isLog() {
        return log;
    }


    /**
     * set the log property.  If the value makes the range invalid (log and zero),
     * then the range is adjusted to make it valid.  This works because the order
     * of property setters doesn't matter.
     * @param log
     */
    public void setLog(boolean log) {
        boolean oldLog = this.log;
        this.log = log;
        propertyChangeSupport.firePropertyChange(PROP_LOG, oldLog, log);
    }
    protected String label = "";
    /**
     * concise label for the axis.
     */
    public static final String PROP_LABEL = "label";

    /**
     * concise label for the axis.
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * concise label for the axis.
     * @param label the label
     */
    public void setLabel(String label) {
        String oldLabel = this.label;
        this.label = label;
        propertyChangeSupport.firePropertyChange(PROP_LABEL, oldLabel, label);
    }
    
    private String fontSize = "1em";

    public static final String PROP_FONTSIZE = "fontSize";

    public String getFontSize() {
        return fontSize;
    }

    /**
     * set the font size relative to the canvas font size.  For example 
     * "2em" will be twice the size.  "" is an alias for 1em.
     * 
     * @param fontSize 
     */
    public void setFontSize(String fontSize) {
        String oldFontSize = this.fontSize;
        this.fontSize = fontSize;
        propertyChangeSupport.firePropertyChange(PROP_FONTSIZE, oldFontSize, fontSize);
    }

    protected boolean drawTickLabels = true;
    public static final String PROP_DRAWTICKLABELS = "drawTickLabels";

    public boolean isDrawTickLabels() {
        return drawTickLabels;
    }

    public void setDrawTickLabels(boolean drawTickLabels) {
        boolean oldDrawTickLabels = this.drawTickLabels;
        this.drawTickLabels = drawTickLabels;
        propertyChangeSupport.firePropertyChange(PROP_DRAWTICKLABELS, oldDrawTickLabels, drawTickLabels);
    }

    /**
     * draw the axis on the opposite side usually used.  This is on the
     * right side or top.
     */
    private boolean opposite = false;

    public static final String PROP_OPPOSITE = "opposite";

    public boolean isOpposite() {
        return opposite;
    }

    public void setOpposite(boolean opposite) {
        boolean oldOpposite = this.opposite;
        this.opposite = opposite;
        propertyChangeSupport.firePropertyChange(PROP_OPPOSITE, oldOpposite, opposite);
    }

    /**
     * false indicates the component will not be drawn.  Note the x and y axes
     * are only drawn if the plot is drawn, and the colorbar may be drawn
     * if the plot is not drawn.
     */
    public static final String PROP_VISIBLE = "visible";
    protected boolean visible = true;

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        boolean oldVisible = this.visible;
        this.visible = visible;
        propertyChangeSupport.firePropertyChange(PROP_VISIBLE, oldVisible, visible);
    }

    /**
     * true indicates the axis hasn't been changed and may/should be autoranged.
     */
    public static final String PROP_AUTORANGE = "autoRange";
    protected boolean autoRange = false;

    public boolean isAutoRange() {
        return autoRange;
    }

    public void setAutoRange(boolean autorange) {
        if ( this.controller!=null ) {
            logger.log(Level.FINEST, "{0}.setAutoRange({1})", new Object[]{this.id, autorange});
        }
        boolean oldAutorange = this.autoRange;
        this.autoRange = autorange;
        propertyChangeSupport.firePropertyChange(PROP_AUTORANGE, oldAutorange, autorange);
    }

    /**
     * when the dimension is autoranged, consider these hints.  These
     * could include:
     * <li>includeZero
     * <li>log=T or log=F
     * <li>center=DATUM
     * <li>width=DATUM, note percent increase can be used with log.
     * <li>reluctant=true
     * <li>units=UNITS, explicitly set the units.
     * These are formed by combining them with ampersands, so for example:
     * <code>log=F&width=40</code>
     * would have the two hints.
     */
    private String autoRangeHints = "";

    public static final String PROP_AUTORANGEHINTS = "autoRangeHints";

    public String getAutoRangeHints() {
        return autoRangeHints;
    }

    public void setAutoRangeHints(String autoRangeHints) {
        String oldAutoRangeHints = this.autoRangeHints;
        this.autoRangeHints = autoRangeHints;
        propertyChangeSupport.firePropertyChange(PROP_AUTORANGEHINTS, oldAutoRangeHints, autoRangeHints);
    }

    /**
     * true indicates the axis label hasn't been changed by a human and may/should be autoranged.
     */
    public static final String PROP_AUTOLABEL = "autoLabel";
    protected boolean autoLabel = false;

    public boolean isAutoLabel() {
        return autoLabel;
    }

    public void setAutoLabel(boolean autolabel) {
        boolean oldAutolabel = this.autoLabel;
        this.autoLabel = autolabel;
        propertyChangeSupport.firePropertyChange(PROP_AUTOLABEL, oldAutolabel, autolabel);
    }

    /**
     * true indicates that the axis should be top to bottom or right to left.
     */
    private boolean flipped = false;
    public static final String PROP_FLIPPED = "flipped";

    public boolean isFlipped() {
        return flipped;
    }

    public void setFlipped(boolean flipped) {
        boolean oldFlipped = this.flipped;
        this.flipped = flipped;
        propertyChangeSupport.firePropertyChange(PROP_FLIPPED, oldFlipped, flipped);
    }


    AxisController controller;

    public AxisController getController() {
        return controller;
    }

    @Override
    public void syncTo(DomNode n) {
        super.syncTo(n);
        if ( !( n instanceof Axis ) ) throw new IllegalArgumentException("node should be an Axis");                        
        if ( controller!=null ) {
            controller.syncTo(n,new ArrayList<String>());
        } else {
            syncTo(n,new ArrayList<String>() );
        }
    }

    @Override
    public void syncTo(DomNode n, List<String> exclude ) {
        super.syncTo(n,exclude);
        if ( !( n instanceof Axis ) ) throw new IllegalArgumentException("node should be an Axis");                        
        if ( controller!=null ) {
            controller.syncTo(n,exclude);
        } else {
            Axis that = (Axis) n;
            if ( !exclude.contains( PROP_LOG ) ) this.setLog(that.isLog());
            if ( !exclude.contains( PROP_FLIPPED ) ) this.setFlipped(that.isFlipped());
            if ( !exclude.contains( PROP_OPPOSITE ) ) this.setOpposite(that.isOpposite());
            if ( !exclude.contains( PROP_RANGE ) ) this.setRange(that.getRange());
            if ( !exclude.contains( PROP_SCALE ) ) this.setScale(that.getScale());
            if ( !exclude.contains( PROP_LABEL ) ) this.setLabel(that.getLabel());
            if ( !exclude.contains( PROP_FONTSIZE ) ) this.setFontSize(that.getFontSize());
            if ( !exclude.contains( PROP_AUTORANGE ) ) this.setAutoRange(that.isAutoRange());
            if ( !exclude.contains( PROP_AUTORANGEHINTS ) ) this.setAutoRangeHints(that.getAutoRangeHints());
            if ( !exclude.contains( PROP_AUTOLABEL ) ) this.setAutoLabel(that.isAutoLabel());
            if ( !exclude.contains( PROP_DRAWTICKLABELS ) ) this.setDrawTickLabels(that.isDrawTickLabels());
            if ( !exclude.contains( PROP_VISIBLE ) ) this.setVisible(that.isVisible());
        }
    }

    @Override
    public DomNode copy() {        
        Axis result= (Axis) super.copy();
        result.controller= null;
        return result;
    }



    @Override
    public List<Diff> diffs(DomNode node) {
        if ( !( node instanceof Axis ) ) throw new IllegalArgumentException("node should be an Axis");                                
        Axis that = (Axis) node;
        List<Diff> result = new ArrayList<>();
        boolean b;

        b= that.log==this.log ;
        if ( !b ) result.add( new PropertyChangeDiff( PROP_LOG , that.log, this.log) );
        b= that.flipped==this.flipped;
        if ( !b ) result.add( new PropertyChangeDiff( PROP_FLIPPED , that.flipped, this.flipped) );
        b=  that.opposite==this.opposite;
        if ( !b ) result.add( new PropertyChangeDiff( PROP_OPPOSITE, that.opposite, this.opposite) );
        b=  that.range.equals(this.range) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_RANGE, that.range , this.range ) );
        b=  that.scale.equals(this.scale) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_SCALE, that.scale , this.scale ) );
        b=  that.label.equals(this.label) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_LABEL, that.label , this.label ) );
        b=  that.fontSize.equals(this.fontSize) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_FONTSIZE, that.fontSize , this.fontSize ) );
        b=  that.autoRange==this.autoRange;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_AUTORANGE, that.autoRange , this.autoRange ) );
        b=  that.autoRangeHints.equals(this.autoRangeHints);
        if ( !b ) result.add(new PropertyChangeDiff( PROP_AUTORANGEHINTS, that.autoRangeHints, this.autoRangeHints ) );
        b=  that.autoLabel==this.autoLabel;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_AUTOLABEL, that.autoLabel , this.autoLabel ) );
        b=  that.drawTickLabels==this.drawTickLabels;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_DRAWTICKLABELS, that.drawTickLabels, this.drawTickLabels ) );
        b=  that.visible==this.visible;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_VISIBLE, that.visible, this.visible ) );

        return result;
    }
}
