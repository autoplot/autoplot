
package org.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.logging.Level;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.graph.DasAxis;
import org.das2.graph.DasAxis.Lock;
import org.das2.graph.DasColumn;
import org.das2.graph.DasRow;
import org.das2.util.LoggerManager;
import org.jdesktop.beansbinding.Converter;

/**
 * @author jbf
 */
public class AxisController extends DomNodeController {

    DasAxis dasAxis;
    private DasColumn column;
    private DasRow row;
    
    private Application dom;
    Plot plot;
    final Axis axis;
    private final static Object PENDING_RANGE_TWEAK="pendingRangeTweak";
    private boolean defaultOppositeRight= false;

    public AxisController(Application dom, Plot plot, Axis axis, DasAxis dasAxis) {
        super( axis );
        this.dom = dom;
        this.dasAxis = dasAxis;
        this.plot= plot;
        this.axis = axis;
        axis.controller = this;
        if ( plot.zaxis==axis ) {
            defaultOppositeRight= true;
        }
        bindTo();
        axis.addPropertyChangeListener(rangeChangeListener);
    }

    /**
     * checks to see that the axis is still valid and clears the autoRange property.
     */
    private final PropertyChangeListener rangeChangeListener = new PropertyChangeListener() {

        private DatumRange logCheckRange(DatumRange range, boolean log) {

            Units u = range.getUnits();
            double dmin= range.min().doubleValue(u);
            double dmax= range.max().doubleValue(u);
            
            boolean changed = false;
            if ( log && dmax <= 0.) {
                dmax = 1000;
                changed = true;
            }
            if ( log && dmin <= 0.) {
                dmin = dmax / 10000;
                changed = true;
            }
            //disable this near-zero test because with lightweight bindings we get stuck in a loop.
            //if ( !log && dmin>0 && dmin<=dmax/10000 ) {
            //    dmin = 0;
            //    changed = true;
            //}

            if (changed) {
                return new DatumRange(dmin, dmax, u);
            } else {
                return range;
            }
        }

        @Override
        public synchronized void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt);  
            // ensure that log doesn't make axis invalid, or min trivially close to zero.
            if ( dom.controller.isValueAdjusting() || valueIsAdjusting() ) return;
            if ( evt.getPropertyName().equals( Axis.PROP_RANGE )
                    || evt.getPropertyName().equals( Axis.PROP_LOG ) ) axis.setAutoRange(false);
            switch (evt.getPropertyName()) {
                case Axis.PROP_LABEL:
                    axis.setAutoLabel(false);
                    break;
                case Axis.PROP_LOG:
                    {
                        if ( isPendingChanges() ) return;
                        DatumRange oldRange = axis.range;
                        final DatumRange range = logCheckRange(axis.range, axis.log);
                        if (!range.equals(oldRange)) {
                            if ( new Exception().getStackTrace().length > 280 ) {
                                changesSupport.registerPendingChange(this,PENDING_RANGE_TWEAK);
                                changesSupport.performingChange(this, PENDING_RANGE_TWEAK);
                                axis.setLog(false);
                                changesSupport.changePerformed(this, PENDING_RANGE_TWEAK);
                            } else {
                                changesSupport.registerPendingChange(this,PENDING_RANGE_TWEAK);
                                changesSupport.performingChange(this, PENDING_RANGE_TWEAK);
                                axis.setRange(range);
                                changesSupport.changePerformed(this, PENDING_RANGE_TWEAK);
                            }
                        }       break;
                    }
                case Axis.PROP_RANGE:
                    {
                        if ( isPendingChanges() ) return;
                        DatumRange oldRange = axis.range;
                        final DatumRange range = logCheckRange(axis.range, axis.log);
                        if (!range.equals(oldRange)) {
                            if ( new Exception().getStackTrace().length > 280 ) {
                                changesSupport.registerPendingChange(this,PENDING_RANGE_TWEAK);
                                changesSupport.performingChange(this, PENDING_RANGE_TWEAK);
                                axis.setLog(false);
                                changesSupport.changePerformed(this, PENDING_RANGE_TWEAK);
                            } else {
                                changesSupport.registerPendingChange(this,PENDING_RANGE_TWEAK);
                                changesSupport.performingChange(this, PENDING_RANGE_TWEAK);
                                if ( axis.isLog() ) axis.setLog(false); // pretty sure it is.
                                axis.setRange(range);
                                changesSupport.changePerformed(this, PENDING_RANGE_TWEAK);
                            }
                        }       break;
                    }
                case Axis.PROP_SCALE:
                    if ( dasAxis!=null ) { // the scale has changed, so let's see if we can reset the range to match the scale
                        int npixels;
                        npixels= dasAxis.isHorizontal() ? dasAxis.getColumn().getWidth() : dasAxis.getRow().getHeight();
                        Datum w;
                        Units u= dasAxis.getUnits();
                        if ( u!=u.getOffsetUnits() ) {
                            w= axis.getRange().width();
                        } else if ( dasAxis.isLog() ) {
                            w= Units.log10Ratio.createDatum( Math.log10( axis.getRange().max().divide(axis.getRange().min() ).value() ) );
                        } else {
                            w= axis.getRange().width();
                        }
                        Datum oldScale= w.divide(npixels);
                        Datum newScale= (Datum)evt.getNewValue();
                        if ( !oldScale.getUnits().isConvertibleTo(newScale.getUnits()) ) {
                            return;
                        }
                        if ( !oldScale.equals(newScale) ) {
                            //System.err.println("105: need to reset scale");
                            Datum scale = (Datum)evt.getNewValue();
                            DatumRange otherRange = dasAxis.getDatumRange();
                            u= otherRange.getUnits();
                            Datum otherw;
                            if ( u!=u.getOffsetUnits() ) {
                                otherw= otherRange.width();
                            } else if ( dasAxis.isLog() ) {
                                otherw= Units.log10Ratio.createDatum( Math.log10( otherRange.max().divide( otherRange.min() ).value() ) );
                            } else {
                                otherw= otherRange.width();
                            }
                            Datum otherScale = otherw.divide(npixels);
                            
                            double expand = (scale.divide(otherScale).value() - 1) / 2;
                            if (Math.abs(expand) > 0.0001) {
                                logger.log(Level.FINER, "expand={0} scale={1} otherScale={2}", new Object[]{expand, scale, otherScale});
                                try {
                                    DatumRange newOtherRange;
                                    if ( dasAxis.isLog() ) {
                                        newOtherRange= DatumRangeUtil.rescaleLog(otherRange, 0 - expand, 1 + expand);
                                    } else {
                                        newOtherRange= DatumRangeUtil.rescale(otherRange, 0 - expand, 1 + expand);
                                    }
                                    axis.setRange(newOtherRange);
                                } catch ( IllegalArgumentException ex ) {
                                    System.err.println("here129");
                                }
                            }
                            
                            //DatumRange nr=
                        }
                    }   break;
                default:
                    break;
            }
        }
    };

    /**
     * true if the Axis is adjusting, or the DasAxis which implements.
     * @return true if the Axis is adjusting, or the DasAxis which implements.
     */
    public boolean valueIsAdjusting() {
        return super.isValueAdjusting() || dasAxis.valueIsAdjusting();
    }

    /**
     * set the range without affecting the auto state.
     * @param range the new range
     * @param log true if the axis should be log.
     */
    public void setRangeAutomatically( DatumRange range, boolean log ) {
        boolean oldAutoRange= axis.autoRange;
        axis.setRange(range);
        axis.setLog(log);
        axis.autoRange=oldAutoRange;
    }

    /**
     * set the label, leaving its autoLabel property true.
     * @param label
     */
    public void setLabelAutomatically( String label ) {
        if ( axis.getLabel().contains("%{RANGE}") && !label.contains("%{RANGE}") ) {
            return;
        }
        axis.setLabel(label);
        axis.setAutoLabel(true);
    }
    
    /**
     * reset the axis units to a new unit which is convertible.
     * @param nu 
     */
    public void resetAxisUnits( Units nu ) {
        DatumRange oldRange=dasAxis.getDatumRange();
        DatumRange newRange= DatumRange.newDatumRange( oldRange.min().doubleValue(nu), oldRange.max().doubleValue(nu), nu );
        dasAxis.resetRange(newRange);
        axis.setRange(newRange);
    }
    
    /**
     * reset the axis range, without the units check.
     * @param newRange
     */
    public void resetAxisRange( DatumRange newRange ) {
        dasAxis.resetRange(newRange);
        axis.setRange(newRange);
    }
    

    private Converter getOppositeConverter( Axis axis, final DasAxis dasAxis ) {
        return new Converter() {
            @Override
            public Object convertForward(Object s) {
                int orientation;
                if ( dasAxis.isHorizontal() ) {
                    if ( s.equals(Boolean.TRUE) ) {
                        orientation= DasAxis.TOP;
                    } else {
                        orientation=DasAxis.BOTTOM;
                    }
                } else if ( AxisController.this.defaultOppositeRight ) {
                    if ( s.equals(Boolean.TRUE) ) {
                        orientation= DasAxis.LEFT;
                    } else {
                        orientation= DasAxis.RIGHT;
                    }                    
                } else {
                    if ( s.equals(Boolean.TRUE) ) {
                        orientation= DasAxis.RIGHT;
                    } else {
                        orientation= DasAxis.LEFT;
                    }
                }
                return orientation;
            }
            @Override
            public Object convertReverse(Object t) {
                int orientation= (Integer)t;
                return orientation==DasAxis.TOP || orientation==DasAxis.RIGHT;
            }
        };
    }
    
    protected LabelConverter labelConverter;
    
    public final synchronized void bindTo() {
        ApplicationController ac = dom.controller;
        ac.bind(axis, Axis.PROP_RANGE, dasAxis, DasAxis.PROPERTY_DATUMRANGE);
        ac.bind(axis, Axis.PROP_LOG, dasAxis, DasAxis.PROP_LOG);
        labelConverter= new LabelConverter( dom, plot, axis, null, null );
        ac.bind(axis, Axis.PROP_LABEL, dasAxis, DasAxis.PROP_LABEL, labelConverter );
        ac.bind(axis, Axis.PROP_FONTSIZE, dasAxis, DasAxis.PROP_FONTSIZE );
        ac.bind(axis, Axis.PROP_DRAWTICKLABELS, dasAxis, "tickLabelsVisible");
        ac.bind(axis, Axis.PROP_FLIPPED, dasAxis, DasAxis.PROP_FLIPPED );
        ac.bind(axis, Axis.PROP_VISIBLE, dasAxis, "visible" );
        ac.bind(axis, Axis.PROP_OPPOSITE, dasAxis, "orientation", getOppositeConverter(axis,dasAxis) );
        ac.bind(axis, Axis.PROP_TICKVALUES, dasAxis, DasAxis.PROP_TICKVALUES );
        ac.bind(axis, Axis.PROP_REFERENCE, dasAxis, DasAxis.PROP_REFERENCE );
        column= dasAxis.getColumn();
        row= dasAxis.getRow();
        if ( dasAxis.isHorizontal() ) {
            column.addPropertyChangeListener(scaleListener);
        } else {
            row.addPropertyChangeListener(scaleListener);
        }
        axis.addPropertyChangeListener( Axis.PROP_RANGE, scaleListener );
        axis.addPropertyChangeListener( Axis.PROP_LOG, scaleListener );
    }

    public final synchronized void removeBindings() {
        //System.err.println("removeBindings for "+axis + " " +scaleListener );//bug2053
        axis.removePropertyChangeListener( Axis.PROP_RANGE, scaleListener );
        axis.removePropertyChangeListener( Axis.PROP_LOG, scaleListener );
        labelConverter= null;
        if ( dasAxis.isHorizontal() ) {
            //if ( this.axis.getId().equals("xaxis_2") ) {
            //    System.err.println("here2 rm xaxis_2="+this.axis+ "@"+ this.axis.hashCode() + "dasColumn=@"+dasAxis.getColumn().hashCode()+" dasAxis="+ dasAxis.getDasName() + "@" + dasAxis.hashCode());
            //    System.err.println();
            //}
            column.removePropertyChangeListener(scaleListener);
        } else {
            row.removePropertyChangeListener(scaleListener);
        }
        dom.controller.unbind(axis);
        axis.removePropertyChangeListener( rangeChangeListener );
    }
    
    /**
     * remove any references this object has before as it is deleted.
     */
    public final synchronized void removeReferences() {
        labelConverter= null;
        //this.dom= null;
        //this.axis= null;
        //System.err.println("* removeReferences for "+scaleListener + " " + this.axis);
        //this.dasAxis= null;
        //this.plot= null;
    }
    
    private final PropertyChangeListener scaleListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            logger.finer("scaleListener");
            int npixels;
            if ( dasAxis==null ) {
                return;
            }
            npixels= dasAxis.isHorizontal() ? dasAxis.getColumn().getWidth() : dasAxis.getRow().getHeight();                
            Datum w;
            Units u= dasAxis.getUnits();
            if ( u.getOffsetUnits()!=u ) {
                w= axis.getRange().width(); // we have to do this, it doesn't matter if it is log.
            } else if ( dasAxis.isLog() ) {
                w= Units.log10Ratio.createDatum( Math.log10( axis.getRange().max().divide(axis.getRange().min() ).value() ) );
            } else {
                w= axis.getRange().width();
            }
            boolean rangeWasChanged= evt.getPropertyName().equals(Axis.PROP_RANGE);
            
            Datum scale= w.divide(npixels);
            if ( !axis.getScale().equals(scale) ) {
                // when scale is bound, change the range.  When it is not bound, go ahead and just reset the scale
                List<BindingModel> bms= dom.getController().findBindings( axis, Axis.PROP_SCALE );
                if ( !rangeWasChanged && bms.size()>0 ) {
                    logger.log(Level.FINEST, "{0}: the scale is bound, so adjust the range instead", axis.id);
                    DatumRange dr= axis.getRange();
                    Datum newW= axis.getScale().multiply(npixels);
                    DatumRange newRange= DatumRangeUtil.createCentered( dr.middle(), newW );
                    axis.setRange(newRange);
                } else {
                    axis.setScale( scale );
                }
            }
        }

        @Override
        public String toString() {//bug2053
            return "scaleListener " + hashCode() + " for "+axis;
        }
        
    };
            
    public DasAxis getDasAxis() {
        return dasAxis;
    }

    void syncTo(DomNode n,List<String> exclude ) {
        Lock lock = null;
        if ( dasAxis!=null ) {
            lock= dasAxis.mutatorLock();
            lock.lock();
        }
        //TODO: should call ((DomNode)n).syncTo(n);
        Axis that = (Axis) n;
        if ( !exclude.contains( Axis.PROP_LOG ) ) axis.setLog(that.isLog());
        if ( !exclude.contains( Axis.PROP_FLIPPED ) ) axis.setFlipped(that.isFlipped());
        if ( !exclude.contains( Axis.PROP_OPPOSITE ) ) axis.setOpposite(that.isOpposite());
        if ( !exclude.contains( Axis.PROP_RANGE ) ) axis.setRange(that.getRange());
        if ( !exclude.contains( Axis.PROP_SCALE ) ) axis.setScale(that.getScale());
        if ( !exclude.contains( Axis.PROP_LABEL ) ) axis.setLabel(that.getLabel());
        if ( !exclude.contains( Axis.PROP_FONTSIZE ) ) axis.setFontSize( that.getFontSize() );
        if ( !exclude.contains( Axis.PROP_AUTORANGE ) ) axis.setAutoRange(that.isAutoRange());
        if ( !exclude.contains( Axis.PROP_AUTOLABEL ) ) axis.setAutoLabel(that.isAutoLabel());
        if ( !exclude.contains( Axis.PROP_AUTORANGEHINTS ) ) axis.setAutoRangeHints(that.getAutoRangeHints());
        if ( !exclude.contains( Axis.PROP_DRAWTICKLABELS ) ) axis.setDrawTickLabels( that.isDrawTickLabels() );
        if ( !exclude.contains( Axis.PROP_TICKVALUES ) ) axis.setTickValues( that.getTickValues() );
        if ( !exclude.contains( Axis.PROP_REFERENCE ) ) axis.setReference( that.getReference() );
        if ( !exclude.contains( Axis.PROP_VISIBLE ) ) axis.setVisible( that.isVisible() );
        if ( lock!=null ) lock.unlock();
    }
}
