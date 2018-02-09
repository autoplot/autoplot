
package org.autoplot.dom;

import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TooManyListenersException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.InconvertibleUnitsException;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.datum.format.DateTimeDatumFormatter;
import org.das2.event.BoxZoomMouseModule;
import org.das2.event.DasMouseInputAdapter;
import org.das2.event.MouseModule;
import org.das2.event.ZoomPanMouseModule;
import org.das2.graph.DasAxis;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasColumn;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;
import org.das2.graph.Renderer;
import org.das2.graph.SeriesRenderer;
import org.das2.graph.SpectrogramRenderer;
import org.das2.graph.TickVDescriptor;
import org.das2.util.LoggerManager;
import org.jdesktop.beansbinding.Converter;
import org.autoplot.AutoplotUtil;
import org.autoplot.MouseModuleType;
import org.autoplot.RenderType;
import org.autoplot.RenderTypeUtil;
import org.autoplot.dom.ChangesSupport.DomLock;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.URISplit;
import org.autoplot.util.TickleTimer;
import org.das2.qds.ops.Ops;

/**
 * Manages a Plot node, for example listening for autoRange updates 
 * and layout changes.
 * @author jbf
 */
public class PlotController extends DomNodeController {

    Application dom;
    Plot plot;
    private DasPlot dasPlot;
    private DasColorBar dasColorBar;
    private LabelConverter titleConverter;
            
    /**
     * if the user clicks on the axis "previous" button, which interval should 
     * be shown.
     */
    private DatumRange scanPrevRange= null;
    /**
     * if the user clicks on the axis "next" button, which interval should 
     * be shown.
     */
    private DatumRange scanNextRange= null;

    /**
     * the plot elements we listen to for autoranging.
     */
    public List<PlotElement> pdListen= new LinkedList();

    private static final String PENDING_ADD_DAS_PEER = "addDasPeer";

    private static final Logger logger= org.das2.util.LoggerManager.getLogger( "autoplot.dom.plotcontroller" );

    public PlotController(Application dom, Plot domPlot, DasPlot dasPlot, DasColorBar colorbar) {
        this( dom, domPlot );
        this.dasPlot = dasPlot;
        this.dasColorBar = colorbar;
        dasPlot.addPropertyChangeListener(listener);
        dasPlot.getXAxis().addPropertyChangeListener(listener);
        dasPlot.getYAxis().addPropertyChangeListener(listener);
    }

    public PlotController( final Application dom, Plot plot ) {
        super( plot );
        this.dom = dom;
        this.plot = plot;
        this.plot.addPropertyChangeListener( Plot.PROP_TITLE, labelListener );
        this.plot.addPropertyChangeListener( Plot.PROP_TICKS_URI, ticksURIListener );
        this.plot.addPropertyChangeListener( Plot.PROP_ID, idListener );
        this.plot.getXaxis().addPropertyChangeListener( autorangeListener );
        this.plot.getYaxis().addPropertyChangeListener( autorangeListener );
        this.plot.getZaxis().addPropertyChangeListener( autorangeListener );
        dom.options.addPropertyChangeListener( Options.PROP_DAY_OF_YEAR, dayOfYearListener );
        dom.options.addPropertyChangeListener( Options.PROP_MOUSEMODULE, mouseModuleListener );
        plot.controller= this;
    }

    public PropertyChangeListener rowColListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt);  
            if ( dasPlot!=null && evt.getPropertyName().equals(Plot.PROP_ROWID) ) {
                String id= (String)evt.getNewValue();
                Row row=  ( id.length()==0 ) ? null : (Row) DomUtil.getElementById( dom, id );
                if ( row==null ) row= dom.controller.getCanvas().marginRow;
                DasRow dasRow= row.controller.getDasRow();
                dasPlot.setRow(dasRow);
                plot.getXaxis().getController().getDasAxis().setRow(dasRow);
                plot.getYaxis().getController().getDasAxis().setRow(dasRow);
                plot.getZaxis().getController().getDasAxis().setRow(dasRow);
            } else if ( dasPlot!=null && evt.getPropertyName().equals(Plot.PROP_COLUMNID) ) {
                String id= (String)evt.getNewValue();
                Column col= ( id.length()==0 ) ? null : (Column) DomUtil.getElementById( dom, id );
                if ( col==null ) col= dom.controller.getCanvas().marginColumn;
                DasColumn dasColumn= col.controller.getDasColumn();
                dasPlot.setColumn(dasColumn);
                plot.getXaxis().getController().getDasAxis().setColumn(dasColumn);
                plot.getYaxis().getController().getDasAxis().setColumn(dasColumn);
                // need to remove old column if no one is listening to it
                DasColumn c= DasColorBar.getColorBarColumn(dasColumn);
                dasColorBar.setColumn(c);
            }
        }

    };

    public Plot getPlot() {
        return plot;
    }

    /**
     * true indicates that the controller is allowed to automatically add
     * bindings to the plot axes.
     */
    public static final String PROP_AUTOBINDING = "autoBinding";

    protected boolean autoBinding = true;

    public boolean isAutoBinding() {
        return autoBinding;
    }

    public void setAutoBinding(boolean autoBinding) {
        boolean oldAutoBinding = this.autoBinding;
        this.autoBinding = autoBinding;
        propertyChangeSupport.firePropertyChange(PROP_AUTOBINDING, oldAutoBinding, autoBinding);
    }

//    /**
//     * return the Canvas containing this plot, or null if this cannot be resolved.
//     * 
//     * @return
//     */
//    private Canvas getCanvasForPlot() {
//        Canvas[] cc= dom.getCanvases();
//        for ( Canvas c: cc ) {
//            for ( Row r: c.getRows() ) {
//                if ( r.getId().equals(plot.getRowId()) ) {
//                    return c;
//                }
//            }
//        }
//        return null;
//    }

    private PropertyChangeListener labelListener= new PropertyChangeListener() {
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"labelListener");               
            if ( evt.getPropertyName().equals(Plot.PROP_TITLE) ) {
                plot.setAutoLabel(false);
            }
         }
    };

    private PropertyChangeListener ticksURIListener= new PropertyChangeListener() {
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"ticksURIListener");  
            if ( evt.getPropertyName().equals(Plot.PROP_TICKS_URI) ) {
                if ( ((String)evt.getNewValue()).length()>0 ) {
                    logger.log(Level.FINE, "prop_ticks_uri={0}", evt.getNewValue());
                    String dasAddress= "class:org.autoplot.tca.UriTcaSource:" + evt.getNewValue();
                    //TODO: check for time series browse here and set to time axis.
                    plot.getXaxis().getController().getDasAxis().setDataPath(dasAddress);
                    plot.getXaxis().getController().getDasAxis().setDrawTca(true);
                    plot.getXaxis().setLabel("%{RANGE}");
                } else {
                    plot.getXaxis().getController().getDasAxis().setDataPath("");
                    plot.getXaxis().getController().getDasAxis().setDrawTca(false);
                    plot.getXaxis().setLabel("");
                }
            }
         }
    };
    
    private PropertyChangeListener idListener=new PropertyChangeListener() {
        @Override
        public void propertyChange( PropertyChangeEvent evt ) {
            LoggerManager.logPropertyChangeEvent(evt,"idListener");  
            if ( dom.controller.isValueAdjusting() ) return;
            DomLock lock = dom.controller.mutatorLock();
            lock.lock( "Changing plot id" );
            try {
                for ( BindingModel b: dom.getBindings() ) {
                    if ( b.getSrcId().equals(evt.getOldValue() ) ) {
                        b.srcId= (String)evt.getNewValue();
                    } 
                    if ( b.getDstId().equals(evt.getOldValue() ) ) {
                        b.dstId= (String)evt.getNewValue();
                    }
                }
                for ( PlotElement pe: dom.plotElements ) {
                    if ( pe.getPlotId().equals(evt.getOldValue()) ) {
                        pe.setPlotId((String) evt.getNewValue());
                    }
                } 
            } finally {
                lock.unlock();
            }
        }
    };
    
    private PropertyChangeListener dayOfYearListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"dayOfYearListener");  
            final DasAxis update= PlotController.this.plot.getXaxis().controller.dasAxis;
            updateAxisFormatter(update);
        }
     };

    private PropertyChangeListener mouseModuleListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"mouseModuleListener");  
            DasPlot p= dasPlot;
            MouseModuleType mm= (MouseModuleType) evt.getNewValue();
            MouseModule m= null;
            if ( null!=mm ) switch (mm) {
                case boxZoom:
                    m= p.getDasMouseInputAdapter().getModuleByLabel("Box Zoom");
                    break;
                case crosshairDigitizer:
                    m= p.getDasMouseInputAdapter().getModuleByLabel("Crosshair Digitizer");
                    break;
                case zoomX:
                    m= p.getDasMouseInputAdapter().getModuleByLabel("Zoom X");
                    break;
                default:
                    break;
            }
            if ( m!=null ) {
                p.getDasMouseInputAdapter().setPrimaryModule( m );
            } else {
                logger.log( Level.WARNING, "logger note recognized: {0}", mm);
            }
        }
    };
    
    /**
     * listen to changes in the autoRange property of the axes, and to the autoRangeHints.
     */
    private PropertyChangeListener autorangeListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"autorangeListener");  
            if ( dom.getController().isValueAdjusting() ) {
                logger.fine("autorangeListener cannot work while isValueAdjusting");
                return;
            }
            if ( dom.options.autoranging ) {
                if ( evt.getPropertyName().equals("autoRange") && evt.getNewValue().equals(Boolean.TRUE) ) {
                    resetZoom( getPlot().getXaxis().isAutoRange(),
                            getPlot().getYaxis().isAutoRange(),
                            getPlot().getZaxis().isAutoRange() );
                } else if ( evt.getPropertyName().equals("autoRangeHints") ) {
                    resetZoom( getPlot().getXaxis().isAutoRange(),
                            getPlot().getYaxis().isAutoRange(),
                            getPlot().getZaxis().isAutoRange() );
                } else if ( evt.getPropertyName().equals("range") ) {
                    if ( !evt.getNewValue().equals(evt.getOldValue()) ) {
                        redoAutoranging();
                    }
                }
            }            
        }
    };
    
    /**
     * experiment with re-autoranging after the xaxis is adjusted.
     */
    private void redoAutoranging() {
        boolean alwaysAutorange = false;
        if (alwaysAutorange) {
            System.err.println(String.format("line307 %s %s %s", getPlot().getXaxis().isAutoRange(),
                getPlot().getYaxis().isAutoRange(),
                getPlot().getZaxis().isAutoRange()));
            boolean mustAutoRange = getPlot().getXaxis().isAutoRange()
                || getPlot().getYaxis().isAutoRange();
            if (mustAutoRange) {
                List<PlotElement> pes = getApplication().getController().getPlotElementsFor(plot);
                for (PlotElement pe : pes) {
                    try {
                        QDataSet b = AutoplotUtil.bounds(pe.getController().getDataSet(), pe.getRenderType());
                        if (getPlot().getYaxis().isAutoRange()) {
                            pe.getPlotDefaults().getYaxis().setRange(DataSetUtil.asDatumRange(b.slice(1)));
                        }
                        if (getPlot().getXaxis().isAutoRange()) {
                            pe.getPlotDefaults().getXaxis().setRange(DataSetUtil.asDatumRange(b.slice(0)));
                        }
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
                resetZoom(getPlot().getXaxis().isAutoRange(),
                    getPlot().getYaxis().isAutoRange(),
                    getPlot().getZaxis().isAutoRange());
            }
        }
    }
    
    /**
     * 
     * @param bounds rank 1, two-element bounds
     * @return true if the bounds are valid.
     */
    private static boolean validBounds( QDataSet bounds ) {
        QDataSet wds= DataSetUtil.weightsDataSet(bounds);
        return !(wds.value(0)==0 || wds.value(1)==0);
    }
    
    /**
     * A new dataset has been loaded or the axis is focused on a new range.
     * bug 1627: out-of-memory caused here by careless copying of data.  This is improved, but more could be done.
     * @param dr0 the new range for the axis.
     * @param ds the dataset to find next or previous focus.
     */
    private void updateNextPrevious( final DatumRange dr0, QDataSet ds ) {
        logger.log(Level.FINE, "updateRadius: {0}", dr0);
        if ( ds!=null && SemanticOps.isBundle(ds) ) {
            logger.log(Level.FINE, "unbundling: {0}", ds);
            QDataSet xds= SemanticOps.xtagsDataSet(ds);
            if ( ds.rank()==2 ) {
                ds= Ops.unbundle( ds, ds.length(0)-1 );
            } else {
                ds= SemanticOps.getDependentDataSet(ds);
            }
            ds= Ops.link( xds, ds );
        }
                        
        DatumRange dr= dr0;
        int count; // limits the number of steps we can take.
        int STEP_LIMIT=10000;
        if ( ds!=null &&  ds.rank()>0 && UnitsUtil.isIntervalOrRatioMeasurement(SemanticOps.getUnits(ds) ) ) {
            try {
                QDataSet bounds= SemanticOps.bounds(ds).slice(0);
                if ( !validBounds(bounds) || !SemanticOps.getUnits(bounds).isConvertibleTo(dr.getUnits() ) || !DataSetUtil.asDatumRange(bounds).contains(dr) ) {
                    dr= dr.next();
                } else {
                    DatumRange limit= DataSetUtil.asDatumRange(bounds);
                    if ( !DatumRangeUtil.isAcceptable(limit,false) ) {
                        throw new IllegalArgumentException("limit is not acceptable"); // see 10 lines down
                    }
                    limit= DatumRangeUtil.union( limit, dr0 );
                    dr= dr.next();
                    count= 0;
                    while ( dr.intersects(limit) ) {
                        count++;
                        if ( count>STEP_LIMIT ) {
                            logger.warning("step limit in nextprev https://sourceforge.net/p/autoplot/bugs/1209/");
                            dr= dr0.next();
                            break;
                        }
                        QDataSet ds1= SemanticOps.trim( ds, dr, null);
                        if ( ds1==null || ds1.length()==0 ) {
                            dr= dr.next();
                        } else {
                            //QDataSet box= SemanticOps.bounds(ds1);
                            //Datum min= DataSetUtil.asDatum( box.slice(0).slice(0) );
                            //dr= DatumRangeUtil.union( min, min.add(dr.width()) );
                            //dr= DatumRangeUtil.rescale( dr, -0.05, 0.95 );
                            logger.log(Level.FINE, "found next data after {0} steps", count);
                            break;
                        }
                    }
                }
            } catch ( InconvertibleUnitsException ex ) {
                logger.log(Level.FINE, ex.getMessage() );
                dr= dr.next();
            } catch ( IllegalArgumentException ex ) {
                logger.log(Level.FINE, ex.getMessage() );
                dr= dr.next();
            }
        } else {
            dr= dr.next();
        }
        scanNextRange= dr;
        
        dr= dr0;
        if ( ds!=null &&  ds.rank()>0 ) {
            try {
                QDataSet bounds= SemanticOps.bounds(ds).slice(0);
                if ( !validBounds(bounds) || !SemanticOps.getUnits(bounds).isConvertibleTo(dr.getUnits() ) || !DataSetUtil.asDatumRange(bounds).contains(dr) ) {
                    dr= dr.previous();
                } else {
                    DatumRange limit= DataSetUtil.asDatumRange(bounds);
                    if ( !DatumRangeUtil.isAcceptable(limit,false) ) {
                        throw new IllegalArgumentException("limit is not acceptable"); // see 10 lines down
                    }
                    limit= DatumRangeUtil.union( limit, dr0 );
                    dr= dr.previous();
                    count= 0;
                    while ( dr.intersects(limit) ) {
                        count++;
                        if ( count>STEP_LIMIT ) {
                            logger.warning("step limit in nextprev https://sourceforge.net/p/autoplot/bugs/1209/");
                            dr= dr0.previous();
                            break;
                        }
                        QDataSet ds1= SemanticOps.trim( ds, dr, null);
                        if ( ds1==null || ds1.length()==0 ) {
                            dr= dr.previous();
                        } else {
                            //There's a bug with this where scan is the previous instead of step, and this makes scan quite different than step, since it's non-integer

                            //QDataSet box= SemanticOps.bounds(ds1);
                            //Datum max= DataSetUtil.asDatum( box.slice(0).slice(1) );
                            //dr= DatumRangeUtil.union( max.subtract(dr.width()), max );
                            //dr= DatumRangeUtil.rescale( dr, 0.05, 1.05 );
                            logger.log(Level.FINE, "found previous data after {0} steps", count);
                            break;
                        }
                    }
                }
            } catch ( InconvertibleUnitsException ex ) {
                logger.log(Level.FINE, ex.getMessage() );
                dr= dr.previous();
            } catch ( IllegalArgumentException ex ) {
                logger.log(Level.FINE, ex.getMessage() );
                dr= dr.previous();
            }
        } else {
            dr= dr.previous();
        }
        scanPrevRange= dr;
        
        Runnable run= new Runnable() {
            @Override
            public void run() {
                if ( scanNextRange.min().equals(dr0.max()) ) {
                    getPlot().getXaxis().getController().getDasAxis()
                            .setNextActionLabel("step >>","<html>step to next interval<br>"+scanNextRange);
                } else {
                    getPlot().getXaxis().getController().getDasAxis()
                            .setNextActionLabel("scan >>","<html>scan to <br>"+scanNextRange);
                }
                if ( scanPrevRange.max().equals(dr0.min()) ) {
                    getPlot().getXaxis().getController().getDasAxis()
                            .setPreviousActionLabel("<< step","<html>step to previous interval<br>"+scanPrevRange);
                } else {
                    getPlot().getXaxis().getController().getDasAxis()
                            .setPreviousActionLabel("<< scan","<html>scan to <br>"+scanPrevRange);
                }
            }
        };
        SwingUtilities.invokeLater(run);
        
    }
    
    /**
     * fancy class for the purpose of restoring the feedback from elsewhere in 
     * the system once the mouse action is complete.
     */
    private class MyFeedback implements DasMouseInputAdapter.Feedback {
        String myLastMessage="";
        String otherLastMessage="";
        @Override
        public void setMessage(String message) {
            if ( message.equals("") ) {
                if ( getApplication().getController().getStatus().equals(myLastMessage) ) {
                    getApplication().getController().setStatus(otherLastMessage);
                }
            } else {
                otherLastMessage= getApplication().getController().getStatus();
                getApplication().getController().setStatus(message);
            }
            myLastMessage= message;
        }
    }
    
    /**
     * Create the das2 GUI components that implement the plot.  This should
     * be called from the event thread.
     * @param canvas the canvas containing the item
     * @param domRow the row indicating the vertical position
     * @param domColumn the column indicating the horizontal position
     */
    protected void createDasPeer( final Canvas canvas, final Row domRow, final Column domColumn) {

        Runnable run = new Runnable() {
            @Override
            public void run() {
                createDasPeerImmediately( canvas, domRow, domColumn );
            }
        };
        if ( SwingUtilities.isEventDispatchThread() ) {
            run.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(run);
            } catch (InterruptedException | InvocationTargetException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }
    
    private void createDasPeerImmediately( Canvas canvas, Row domRow, Column domColumn ) {
                
        Application application= dom;

        DatumRange x = this.plot.xaxis.range;
        DatumRange y = this.plot.yaxis.range;
        final DasAxis xaxis = new DasAxis(x.min(), x.max(), DasAxis.HORIZONTAL);
        DasAxis yaxis = new DasAxis(y.min(), y.max(), DasAxis.VERTICAL);

        xaxis.setEnableHistory(false);
        //xaxis.setUseDomainDivider(true);
        yaxis.setEnableHistory(false);
        //yaxis.setUseDomainDivider(true);
        
        final TickleTimer nextPrevTickleTimer= new TickleTimer( 300, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                final DatumRange dr= (DatumRange)xaxis.getDatumRange();
                List<PlotElement> pele= getApplication().getController().getPlotElementsFor(plot);
                final QDataSet ds= pele.size()> 0 ? pele.get(0).getController().getDataSet() : null;
                updateNextPrevious(dr,ds);
            }
        });
        
        xaxis.addPropertyChangeListener( DasAxis.PROPERTY_DATUMRANGE, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt,"xaxis datumrange");  
                if ( dom.getOptions().isScanEnabled() ) {
                    nextPrevTickleTimer.tickle();
                }
            }
        });
        
        xaxis.setNextAction( "scan", new AbstractAction( "scannext" ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
                List<PlotElement> pele= getApplication().getController().getPlotElementsFor(plot);
                DatumRange dr= xaxis.getDatumRange();
                if ( pele.isEmpty() || scanNextRange==null ) {    
                    xaxis.setDatumRange(dr.next());
                } else {
                    dr= scanNextRange;
                    if ( !dr.min().equals( xaxis.getDatumRange().max() ) ) {
                        xaxis.setAnimated(true); // yeah, return of animated axes!
                        xaxis.setDataRange( dr.min(), dr.max() );
                        xaxis.setAnimated(false);
                    }
                    xaxis.setDatumRange(dr);
                }
            }
        });

        xaxis.setPreviousAction( "scan", new AbstractAction( "scanprev" ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);                
                List<PlotElement> pele= getApplication().getController().getPlotElementsFor(plot);
                DatumRange dr= xaxis.getDatumRange();
                if ( pele.isEmpty() || scanPrevRange==null ) {    
                    xaxis.setDatumRange(dr.previous());
                } else {
                    dr= scanPrevRange;
                    if ( xaxis.isLog() && dr.min().value()<=0 ) {
                        logger.fine("cannot scan to non-positive with log xaxis");
                        return;
                    }
                    if ( !dr.max().equals( xaxis.getDatumRange().min() ) ) {
                        xaxis.setAnimated(true);
                        xaxis.setDataRange( dr.min(), dr.max() );
                        xaxis.setAnimated(false);
                    }
                    xaxis.setDatumRange(dr);
                }
            }
        });

        if (UnitsUtil.isTimeLocation(xaxis.getUnits())) {
            xaxis.setUserDatumFormatter(new DateTimeDatumFormatter(dom.getController().getApplication().getOptions().isDayOfYear() ? DateTimeDatumFormatter.OPT_DOY : 0 )); //See kludge in TimeSeriesBrowseController
        } else {
            xaxis.setUserDatumFormatter(null);
        }

        if (UnitsUtil.isTimeLocation(yaxis.getUnits())) {
            yaxis.setUserDatumFormatter(new DateTimeDatumFormatter(dom.getController().getApplication().getOptions().isDayOfYear() ? DateTimeDatumFormatter.OPT_DOY : 0 ));
        } else {
            yaxis.setUserDatumFormatter(null);
        }
        
        plot.setRowId(domRow.getId());
        DasRow row = domRow.controller.getDasRow();
        plot.addPropertyChangeListener( Plot.PROP_ROWID, rowColListener );
        plot.addPropertyChangeListener( Plot.PROP_COLUMNID, rowColListener );

        DasColumn col= domColumn.controller.getDasColumn();
        
        final DasPlot dasPlot1 = new DasPlot(xaxis, yaxis);

        dasPlot1.setPreviewEnabled(true);

        DatumRange colorRange = new DatumRange(0, 100, Units.dimensionless);
        DasColorBar colorbar = new DasColorBar(colorRange.min(), colorRange.max(), false);
        colorbar.addFocusListener(application.controller.focusAdapter);
        colorbar.setFillColor(new java.awt.Color(0, true));
        colorbar.setEnableHistory(false);
        //colorbar.setUseDomainDivider(true);

        DasCanvas dasCanvas = canvas.controller.getDasCanvas();

        dasCanvas.add(dasPlot1, row, col);

        // the axes need to know about the plotId, so they can do reset axes units properly.
        dasPlot1.getXAxis().setPlot(dasPlot1);
        dasPlot1.getYAxis().setPlot(dasPlot1);

        BoxZoomMouseModule boxmm = (BoxZoomMouseModule) dasPlot1.getDasMouseInputAdapter().getModuleByLabel("Box Zoom");
        dasPlot1.getDasMouseInputAdapter().setPrimaryModule(boxmm);

        //dasPlot1.getDasMouseInputAdapter().addMouseModule( new AnnotatorMouseModule(dasPlot1) ) ;

        MouseModuleType m= dom.getOptions().getMouseModule();
        MouseModule mm= null;
        if ( null!=m ) switch (m) {
            case boxZoom:
                // do nothing
                break;
            case crosshairDigitizer:
                mm= dasPlot1.getDasMouseInputAdapter().getModuleByLabel("Crosshair Digitizer");
                break;
            case zoomX:
                mm= dasPlot1.getDasMouseInputAdapter().getModuleByLabel("Zoom X");
                break;
            default:
                break;
        }
        if ( mm!=null ) dasPlot1.getDasMouseInputAdapter().setPrimaryModule(mm);

        DasMouseInputAdapter.Feedback feedback= new MyFeedback();
        dasPlot1.getDasMouseInputAdapter().setFeedback( feedback );
        
        dasCanvas.add(colorbar, dasPlot1.getRow(), DasColorBar.getColorBarColumn(dasPlot1.getColumn()));

        MouseModule zoomPan = new ZoomPanMouseModule(dasPlot1, dasPlot1.getXAxis(), dasPlot1.getYAxis());
        dasPlot1.getDasMouseInputAdapter().setSecondaryModule(zoomPan);

        MouseModule zoomPanX = new ZoomPanMouseModule(dasPlot1.getXAxis(), dasPlot1.getXAxis(), null);
        dasPlot1.getXAxis().getDasMouseInputAdapter().setSecondaryModule(zoomPanX);
        dasPlot1.getXAxis().getDasMouseInputAdapter().setFeedback( feedback );

        MouseModule zoomPanY = new ZoomPanMouseModule(dasPlot1.getYAxis(), null, dasPlot1.getYAxis());
        dasPlot1.getYAxis().getDasMouseInputAdapter().setSecondaryModule(zoomPanY);
        dasPlot1.getYAxis().getDasMouseInputAdapter().setFeedback( feedback );

        MouseModule zoomPanZ = new ZoomPanMouseModule(colorbar, null, colorbar);
        colorbar.getDasMouseInputAdapter().setSecondaryModule(zoomPanZ);
        colorbar.getDasMouseInputAdapter().setFeedback( feedback );

        dasCanvas.revalidate();
        dasCanvas.repaint();

        ApplicationController ac= application.controller;
        ac.layoutListener.listenTo(dasPlot1);
        ac.layoutListener.listenTo(colorbar);

        //TODO: clean up in an addDasPeer way
        new AxisController(application, this.plot, this.plot.getXaxis(), xaxis);
        new AxisController(application, this.plot, this.plot.getYaxis(), yaxis);
        new AxisController(application, this.plot, this.plot.getZaxis(), colorbar);

        bindTo(dasPlot1);
        
        dasPlot1.addFocusListener(ac.focusAdapter);
        dasPlot1.getXAxis().addFocusListener(ac.focusAdapter);
        dasPlot1.getYAxis().addFocusListener(ac.focusAdapter);
        dasPlot1.addPropertyChangeListener(DasPlot.PROP_FOCUSRENDERER, ac.rendererFocusListener);

        ac.bind(application.getOptions(), Options.PROP_DRAWGRID, dasPlot1, "drawGrid");
        ac.bind(application.getOptions(), Options.PROP_DRAWMINORGRID, dasPlot1, "drawMinorGrid");
        ac.bind(application.getOptions(), Options.PROP_FLIPCOLORBARLABEL, this.plot.getZaxis().getController().dasAxis, "flipLabel");
        ac.bind(application.getOptions(), Options.PROP_FLIPCOLORBARLABEL, this.plot.getYaxis().getController().dasAxis, "flipLabel");
        ac.bind(application.getOptions(), Options.PROP_TICKLEN, dasPlot1.getXAxis(), "tickLength");
        ac.bind(application.getOptions(), Options.PROP_TICKLEN, dasPlot1.getYAxis(), "tickLength");
        ac.bind(application.getOptions(), Options.PROP_TICKLEN, colorbar, "tickLength");
        ac.bind(application.getOptions(), Options.PROP_MULTILINETEXTALIGNMENT, dasPlot1, DasPlot.PROP_MULTILINETEXTALIGNMENT);

        ac.bind(this.plot, Plot.PROP_LEGENDPOSITION, dasPlot1, DasPlot.PROP_LEGENDPOSITION );
        ac.bind(this.plot, Plot.PROP_DISPLAYLEGEND, dasPlot1, DasPlot.PROP_DISPLAYLEGEND );

        ac.bind(application.getOptions(), Options.PROP_OVERRENDERING, dasPlot1, "overSize");

        ac.bind(this.plot, Plot.PROP_VISIBLE, dasPlot1, "plotVisible" );
        ac.bind(this.plot, Plot.PROP_COLORTABLE, colorbar, "type" );

        dasPlot1.addPropertyChangeListener(listener);
        dasPlot1.getXAxis().addPropertyChangeListener(listener);
        dasPlot1.getYAxis().addPropertyChangeListener(listener);
        
        if ( plot.getTicksURI().length()>0 ) { //TODO: understand this better.  We don't have to set titles, right?  Maybe it's because implementation is handled here instead of in das2.
            logger.fine("setLabel(%{RANGE})");
            String dasAddress= "class:org.autoplot.tca.UriTcaSource:" + plot.getTicksURI();
            dasPlot1.getXAxis().setDataPath(dasAddress);
            dasPlot1.getXAxis().setDrawTca(true);
            plot.getXaxis().setLabel("%{RANGE}"); //TODO: this is really only necessary for time locations.
        }

        this.dasPlot = dasPlot1;
        this.dasColorBar = colorbar;

        dasPlot.setEnableRenderPropertiesAction(false);
        //dasPlot.getDasMouseInputAdapter().removeMenuItem("Render Properties");

        application.controller.maybeAddContextMenus( this );

        boolean headless= "true".equals( System.getProperty("java.awt.headless") );
        if ( !headless && canvas.controller.getDropTargetListener()!=null ) {
            DropTarget dropTarget = new DropTarget();
            dropTarget.setComponent(dasPlot);
            try {
                dropTarget.addDropTargetListener( canvas.controller.getDropTargetListener() );
            } catch (TooManyListenersException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
            dasPlot.setDropTarget(dropTarget);
        }

        updateAxisFormatter( dasPlot1.getXAxis() );

    }


    /**
     * get the axis in the DOM for the dasAxis implementation.
     * @return null if the axis is not from this plot.
     */
    private Axis getDomAxis( DasAxis axis ) {
        Axis domAxis;
        if ( plot.xaxis.controller.dasAxis == axis ) {
            domAxis= plot.xaxis;
        } else if ( plot.yaxis.controller.dasAxis==axis ) {
            domAxis= plot.yaxis;
        } else if ( plot.zaxis.controller.dasAxis==axis ) {
            domAxis= plot.zaxis;
        } else {
            domAxis= null;
        }
        return domAxis;
    }

    private void updateAxisFormatter( DasAxis axis ) {
        logger.fine("updateAxisFormatter()");
        if ( UnitsUtil.isTimeLocation(axis.getUnits()) && !axis.getLabel().contains("%{RANGE}") ) {
            axis.setUserDatumFormatter(new DateTimeDatumFormatter(  dom.getController().getApplication().getOptions().isDayOfYear() ? DateTimeDatumFormatter.OPT_DOY : 0 ));
        } else {
            TickVDescriptor.setDayOfYear( dom.getController().getApplication().getOptions().isDayOfYear() );
            axis.setTickV(null);
            axis.setUserDatumFormatter(null);
        }
    }

    private PropertyChangeListener listener = new PropertyChangeListener() {
        @Override
        public String toString() {
            return ""+PlotController.this;
        }
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"listener");
            if (evt.getSource() instanceof DasAxis) {
                DasAxis axis = (DasAxis) evt.getSource();
                Axis domAxis= getDomAxis(axis);
                if ( domAxis==null ) return;
                if ( evt.getPropertyName().equals("Frame.active") ) return;
                if ( evt.getPropertyName().equals(DasAxis.PROP_UNITS)
                        || evt.getPropertyName().equals(DasAxis.PROPERTY_DATUMRANGE ) ) {
                    if ( axis.isDrawTca() && domAxis.getLabel().length()==0 ) {
                        domAxis.setLabel("%{RANGE}");
                    }
                }
                if ( evt.getPropertyName().equals(DasAxis.PROP_UNITS) 
                        || evt.getPropertyName().equals(DasAxis.PROPERTY_DATUMRANGE )
                        || evt.getPropertyName().equals(DasAxis.PROP_LABEL) ) {
                    updateAxisFormatter(axis);
                }

            } else if ( evt.getPropertyName().equals( DasPlot.PROP_FOCUSRENDERER ) ) {

                List<PlotElement> eles= PlotController.this.dom.controller.getPlotElementsFor(plot);
                PlotElement fe= null;
                for ( PlotElement ele: eles ) {
                    if ( ele.getController().getRenderer()== evt.getNewValue() ) {
                        fe= ele;
                    }
                }
                if ( fe!=null ) PlotController.this.dom.controller.setPlotElement( fe );

            }

        }
    };

    public DasColorBar getDasColorBar() {
        return dasColorBar;
    }

    public DasPlot getDasPlot() {
        return dasPlot;
    }

    /**
     * set log to false if the axis contains 0 or negative min.
     * @param a
     */
    private static void logCheck( Axis a ) {
        if ( a.isLog() && a.getRange().min().doubleValue( a.getRange().getUnits() ) <= 0 ) {
            a.setLog(false);
        }
    }

    /**
     * introduced when a linear digitized trace was put on top of a log spectrogram.
     * We need to be more sophistocated about how we resolve the two.
     * Axes that have autorange=false indicate that they do not care about this 
     * dimension.
     * @param a1 an axis with range, log, and autoRange properties.
     * @param a2 an axis with range, log, and autoRange properties.
     * @return
     */
    private Axis resolveSettings( Axis a1, Axis a2 ) {
        Axis result;
        if ( !a1.isAutoRange() && a2.isAutoRange() ) {
            return a2;
        } else if ( !a2.isAutoRange() && a1.isAutoRange() ) {
            return a1;
        }
        result = new Axis();
        result.range = DatumRangeUtil.union( a1.range, a2.range );
        if ( a1.log==a2.log ) {
            result.log= a1.log;
        } else {
            result.log= result.range.min().doubleValue(result.range.getUnits()) > 0;
        }
        result.autoRange= a1.autoRange && a2.isAutoRange();
        return result;
    }
    
    /**
     * only autorange when the settings have completely changed.  Chris and Craig
     * @param axis
     * @param newSettings
     * @return 
     */
    private Axis reluctantRanging( Axis axis, Axis newSettings ) {
        try {
            if ( axis.getRange().rescale(-1,2).intersects( newSettings.getRange() ) ) {
                double d1= DatumRangeUtil.normalize( axis.getRange(), newSettings.getRange().min(), axis.isLog() );
                double d2= DatumRangeUtil.normalize( axis.getRange(), newSettings.getRange().max(), axis.isLog() );
                if ( Math.abs(d2-d1)>0.1 ) {
                    return axis;
                } else {
                    return newSettings;
                }
            } else {
                return newSettings;
            }
        } catch ( InconvertibleUnitsException ex ) {
            return newSettings;
        }
    }
    
    /**
     * implement hints like width=40&includeZero=T.  
     * <blockquote><pre>
     * includeZero   T or F     make sure that zero is within the result.
     * width         30nT       use this width.  This is a formatted datum which 
     *                          is parsed with the units of the axis, or with the number of cycles for log.
     * log           T or F     force log or linear axis
     * widths        30nT,300nT,3000nT   use one of these widths
     * center        0          constrain the center to be this location
     * (not yet) reluctant     T or F     use the old range if it is acceptable.
     * </pre></blockquote>

     * @param axis the axis to which we are applying the hints.
     * @param hintsString the string, ampersand-delimited.
     */
    private void doHints( Axis axis, String hintsString ) {
        Map<String,String> hints= URISplit.parseParams(hintsString);
        DatumRange range=axis.getRange();
        boolean log= axis.isLog();

        boolean includeZero= "T".equals(hints.get("includeZero"));
        String width= hints.get("width");
        String widths= hints.get("widths");
        String center= hints.get("center");
        String logHint= hints.get("log");

        if ( logHint!=null && UnitsUtil.isRatioMeasurement( axis.getRange().getUnits() ) ) {
            if ( logHint.equals("T") ) {
                if ( range.min().value() <= 0. ) {
                    if ( range.max().value()>0 ) {
                        double m= range.max().value();
                        range= new DatumRange( m/1e3, m, range.getUnits() );
                    } else {
                        range= new DatumRange( 1, 1000, range.getUnits() );
                    }
                }
            }
            log= logHint.equals("T");
        }
        
        if ( width!=null ) {
            Units u= range.getUnits().getOffsetUnits();
            try {
                if ( log ) {
                    Datum w= Units.log10Ratio.parse(width);
                    w= w.divide(2);
                    Datum currentCenter= DatumRangeUtil.rescaleLog( range, 0.5, 0.5 ).min();
                    range= new DatumRange( currentCenter.divide( Math.pow(10,w.value()) ), currentCenter.multiply( Math.pow(10,w.value()) ) );
                } else {
                    Datum w= u.parse(width);
                    w= w.divide(2);
                    Datum currentCenter= DatumRangeUtil.rescale( range, 0.5, 0.5 ).min();
                    range= new DatumRange( currentCenter.subtract(w), currentCenter.add(w) );
                }
            } catch (ParseException | InconvertibleUnitsException ex ) {
                logger.log(Level.WARNING, null, ex);
            }
        }
        if ( widths!=null ) {
            String[] wss= widths.split("\\,");
            Datum limit= log ? Units.log10Ratio.createDatum( Math.log10( range.max().divide(range.min()).value() ) ) : range.width();
            Units u= range.getUnits().getOffsetUnits();
            for ( String ws: wss ) {
                try {
                    if ( log ) {
                        Datum w= Units.log10Ratio.parse(ws);
                        if ( w.gt(limit) || ws.equals(wss[wss.length-1])) {
                            w= w.divide(2);
                            Datum currentCenter= DatumRangeUtil.rescaleLog( range, 0.5, 0.5 ).min();
                            range= new DatumRange( currentCenter.divide( Math.pow(10,w.value()) ), currentCenter.multiply( Math.pow(10,w.value()) ) );
                            break;
                        }
                    } else {
                        Datum w= u.parse(ws);
                        if ( w.gt(limit) || ws.equals(wss[wss.length-1])) { 
                            w= w.divide(2);
                            Datum currentCenter= DatumRangeUtil.rescale( range, 0.5, 0.5 ).min();
                            range= new DatumRange( currentCenter.subtract(w), currentCenter.add(w) );
                            break;
                        }
                    }
                } catch (ParseException | InconvertibleUnitsException ex ) {
                    logger.log( Level.WARNING, null, ex );
                }
            }
        }
        if ( includeZero && UnitsUtil.isRatioMeasurement(range.getUnits() ) ) {
            Datum z= range.getUnits().createDatum(0);
            if ( widths==null && width==null ) {
                range= DatumRangeUtil.union( range, z ); //TODO: consider extra 10%
            } else {
                if ( range.min().value()>0 ) {
                    double n= DatumRangeUtil.normalize( range, z );
                    range= DatumRangeUtil.rescale( range, n, n+1 );
                } else if ( range.max().value()<0. ) {
                    double n= DatumRangeUtil.normalize( range, z );
                    range= DatumRangeUtil.rescale( range, n-1, n );                    
                }
            }
        }
        if ( center!=null ) {
            Units u= range.getUnits();
            try {
                if ( log ) {
                    double w= Math.log10( range.max().divide(range.min() ).value() );
                    w= w/2;
                    Datum currentCenter;
                    currentCenter = u.parse(center);
                    range= new DatumRange( currentCenter.divide( Math.pow(10,w) ), currentCenter.multiply( Math.pow(10,w) ) );
                } else {
                    Datum w= range.width();
                    w= w.divide(2);
                    Datum currentCenter= u.parse(center);
                    range= new DatumRange( currentCenter.subtract(w), currentCenter.add(w) );
                }
            } catch (ParseException | InconvertibleUnitsException ex ) {
                logger.log(Level.WARNING, null, ex);
            }              
        }
        axis.setRange( range );
        axis.setLog(log);
    }
    
    /**
     * set the zoom so that all of the plotElements' data is visible.  This means finding
     * the "union" of the plotElements' plotDefault ranges.  If any plotElement's default log
     * is false, then the new setting will be false.
     * @param x reset zoom in the x dimension.
     * @param y reset zoom in the y dimension.
     * @param z reset zoom in the z dimension.
     */
    public void resetZoom(boolean x, boolean y, boolean z) {
        List<PlotElement> elements = dom.controller.getPlotElementsFor(plot);
        if ( elements.isEmpty() ) return;
        Plot newSettings = null;

        boolean haveTsb= false;

//        System.err.println("== resetZoom Elements==" );
//        for ( PlotElement p: elements ) {
//            System.err.println( p  +  " y= " + p.getPlotDefaults().getYaxis().getRange() );
//        }

        boolean warnedAboutUnits= false;
        for (PlotElement p : elements) {
            Plot plot1 = p.getPlotDefaults();
            if (newSettings == null) {
                newSettings = (Plot) plot1.copy();
            } else {
                try {
                    newSettings.xaxis= resolveSettings( newSettings.xaxis,plot1.getXaxis() );
                } catch ( InconvertibleUnitsException ex ) {
                    if ( !warnedAboutUnits ) {
                        logger.log( Level.FINE, "plot elements on the same xaxis have inconsistent units: {0} {1}",
                                new Object[]{newSettings.xaxis.range.getUnits().toString(), plot1.getXaxis().getRange().getUnits().toString()});
                        warnedAboutUnits= true;
                    }
                }
                try {
                    newSettings.yaxis= resolveSettings( newSettings.yaxis, plot1.getYaxis() );
                } catch ( InconvertibleUnitsException ex ) {
                    if ( !warnedAboutUnits ) {
                        logger.log( Level.FINE, "plot elements on the same yaxis have inconsistent units: {0} {1}",
                                new Object[]{newSettings.yaxis.range.getUnits().toString(), plot1.getYaxis().getRange().getUnits().toString()});
                        warnedAboutUnits= true;
                    }
                }
                try {
                    newSettings.zaxis= resolveSettings( newSettings.zaxis, plot1.getZaxis() );
                } catch ( InconvertibleUnitsException ex ) {
                    if ( !warnedAboutUnits ) {
                        logger.log( Level.FINE, "plot elements on the same zaxis have inconsistent units: {0} {1}",
                                new Object[]{newSettings.zaxis.range.getUnits().toString(), plot1.getZaxis().getRange().getUnits().toString()});
                        warnedAboutUnits= true;
                    }
                }
            }
            DataSourceFilter dsf= this.dom.controller.getDataSourceFilterFor(p);
            if ( dsf!=null && dsf.getController()!=null && dsf.getController().getTsb()!=null ) {
                haveTsb= true;
            }
        }
        
        if ( newSettings==null ) {
            plot.getXaxis().setAutoRange(true);
            plot.getYaxis().setAutoRange(true);
            plot.getZaxis().setAutoRange(true);
            return;
        }

        if ( x ) {
            logCheck(newSettings.getXaxis());
            Axis newAxis= newSettings.getXaxis();
            if ( plot.getXaxis().getAutoRangeHints().length()>0 ) {
                doHints( newAxis, plot.getXaxis().getAutoRangeHints() );
            }
            if ( dom.options.getAutorangeType().equals( Options.VALUE_AUTORANGE_TYPE_RELUCTANT) ) {
                newAxis= reluctantRanging( plot.getXaxis(), newAxis );
            }
            plot.getXaxis().setLog( newAxis.isLog() );
            plot.getXaxis().setRange( newAxis.getRange());
            plot.getXaxis().setAutoRange(true);

            if ( haveTsb==true ) {
                plot.getXaxis().getController().dasAxis.setScanRange( null );
            } else {
                plot.getXaxis().getController().dasAxis.setScanRange( plot.getXaxis().getRange() );
            }

        }
        
        if ( y ) {
            logCheck(newSettings.getYaxis());
            Axis newAxis= newSettings.getYaxis();
            if ( plot.getYaxis().getAutoRangeHints().length()>0 ) {
                doHints( newAxis, plot.getYaxis().getAutoRangeHints() );
            }
            if ( dom.options.getAutorangeType().equals( Options.VALUE_AUTORANGE_TYPE_RELUCTANT) ) {
                newAxis= reluctantRanging( plot.getYaxis(), newAxis );
            }
            plot.getYaxis().setLog( newAxis.isLog() );
            plot.getYaxis().setRange( newAxis.getRange());
            plot.getYaxis().setAutoRange(true);
            plot.getYaxis().getController().dasAxis.setScanRange(  newAxis.getRange() );
        }
        
        if ( z ) {
            logCheck(newSettings.getZaxis());
            Axis newAxis= newSettings.getZaxis();
            if ( plot.getZaxis().getAutoRangeHints().length()>0 ) {
                doHints( newAxis, plot.getZaxis().getAutoRangeHints() );
            }
            if ( dom.options.getAutorangeType().equals( Options.VALUE_AUTORANGE_TYPE_RELUCTANT) ) {
                newAxis= reluctantRanging( plot.getZaxis(), newAxis );
            }
            plot.getZaxis().setLog( newAxis.isLog() );
            plot.getZaxis().setRange( newAxis.getRange() );
            plot.getZaxis().setAutoRange(true);
            plot.getZaxis().getController().dasAxis.setScanRange(  newAxis.getRange() );
        }
    }
    
    private final PropertyChangeListener plotDefaultsListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"plotDefaultsListener");
            PlotElement pele= (PlotElement)evt.getSource();
            List<PlotElement> pp= PlotController.this.dom.getController().getPlotElementsFor(plot);
            if ( pp.contains(pele) ) {
                pp.remove(pele);
            } else {
                //System.err.println("Plot "+plot+" doesn't contain the source plotElement "+plotElement +" see bug 2992903" ); //bug 2992903
                return;
            }
            
            if ( pele.isAutoRenderType() && pp.isEmpty() ) {
                PlotController.this.setAutoBinding(true);
            }
            doPlotElementDefaultsChange(pele);
        }
    };

    private final PropertyChangeListener renderTypeListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"renderTypeListener");
            checkRenderType();
        }
    };

    PlotElement plotElement;

    private final PropertyChangeListener plotElementDataSetListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"plotElementDataSetListener");
            if ( plotElement==null ) {
                System.err.println("whoops, getting there");
                return;
            }
            QDataSet ds1;
            if ( titleConverter.plotElement!=null ) {
                ds1= titleConverter.plotElement.getController().getDataSet();
            } else {
                ds1= null;
            }
            logger.log(Level.FINE, "titleConverter should use for dataset: {0}", ds1);
                    
            String t= (String)titleConverter.convertForward( plot.getTitle() );

            dasPlot.setTitle( t );
            dasPlot.getYAxis().setLabel( (String)plot.getYaxis().getController().labelConverter.convertForward( plot.getYaxis().getLabel() ) );
            dasPlot.getXAxis().setLabel( (String)plot.getXaxis().getController().labelConverter.convertForward( plot.getXaxis().getLabel() ) );
            QDataSet pds= plotElement.getController().getDataSet();
            logger.log( Level.FINE, "{0} dataSetListener", plot);
            if ( pds!=null && UnitsUtil.isIntervalOrRatioMeasurement(SemanticOps.getUnits(pds)) ) {
                updateNextPrevious( plot.getXaxis().getRange(), pds );
            }
        }
    };

    /**
     * check to see if the render type needs a colorbar by default.  If the
     * changes are happening automatically, then return without doing anything.
     */
    private void checkRenderType() {
        if ( dom.getController().isValueAdjusting() ) return;
        boolean needsColorbar = false;
        for (PlotElement p : dom.getController().getPlotElementsFor(plot)) {
            if (RenderTypeUtil.needsColorbar(p.getRenderType())) {
                needsColorbar = true;
            }
        }
        plot.getZaxis().setVisible(needsColorbar);
    }

    void addPlotElement(PlotElement p) {
        addPlotElement(p,true);
    }

    synchronized List<Integer> indecesOfPlotElements( ) {
        List<Integer> indeces= new ArrayList<>(dom.plotElements.size());
        for ( int i=0; i<dom.plotElements.size(); i++ ) {
            if ( dom.getPlotElements(i).getPlotId().equals(this.plot.getId()) ) {
                indeces.add(i);
            }
        }
        return indeces;
    }

    synchronized void moveToStackBottom( PlotElement p ) {
        final DomLock lock= dom.getController().mutatorLock();
        lock.lock("Move to Stack Bottom");
        try {
            if (!p.getPlotId().equals(this.plot.getId())) {
                throw new IllegalArgumentException("this is not my plot");
            }
            PlotElement[] newPes= dom.getPlotElements(); // verified this makes a copy.

            // find the bottom most element of plot.
            int bottom;
            for ( bottom=0; bottom<newPes.length; bottom++ ) {
                if ( newPes[bottom].getPlotId().equals(p.getPlotId()) ) break;
            }

            int ploc;
            for ( ploc=0; ploc<newPes.length; ploc++ ) {
                if ( newPes[ploc]==p ) break;
            }

            if ( ploc>bottom ) {
                for ( int i=ploc; i>bottom; i-- ) {
                    newPes[i]= newPes[i-1];
                }
                newPes[bottom]= p;
            }

            //for ( int i=0; i<newPes.length; i++ ) {
            //    System.err.println( "moveToStackBottom: " + dom.getPlotElements(i) + "(" + dom.getPlotElements(i).getPlotId() + ")" + " " + newPes[i] +  "(" + newPes[i].getPlotId()+ ")"  );
            //}
            dom.setPlotElements(newPes);

        } finally {
            lock.unlock();
        }

    }

    /**
     * move the plot element to the bottom.
     * @param p
     */
    public void toBottom( PlotElement p ) {
        moveToStackBottom(p);
        DasPlot pp= p.getController().getDasPlot();
        Renderer r= p.getController().getRenderer();
        pp.removeRenderer(r);
        pp.addRenderer(0,r);
    }

    /**
     * move the plot element to the top.
     * @param p the plot element
     */
    public void toTop( PlotElement p ) {
        moveToStackTop(p);
        DasPlot pp= p.getController().getDasPlot();
        Renderer r= p.getController().getRenderer();
        pp.removeRenderer(r);
        pp.addRenderer(r);
    }
    
    /**
     * move the plot element to the top of the stack, or the highest index in the dom.
     * This does not affect the View (das2), only the model!
     * @param p
     */
    synchronized void moveToStackTop( PlotElement p ) {
        final DomLock lock= dom.getController().mutatorLock();
        lock.lock("Move to Stack Top");
        try {
            if (!p.getPlotId().equals(this.plot.getId())) {
                throw new IllegalArgumentException("this is not my plot");
            }
            PlotElement[] newPes= dom.getPlotElements(); // verified this makes a copy.

            // find the topmost element of plot.
            int top;
            for ( top=newPes.length-1; top>=0; top-- ) {
                if ( newPes[top].getPlotId().equals(p.getPlotId()) ) break;
            }

            int ploc;
            for ( ploc=0; ploc<newPes.length; ploc++ ) {
                if ( newPes[ploc]==p ) break;
            }

            if ( ploc<top ) {
                for ( int i=ploc; i<top; i++ ) {
                    newPes[i]= newPes[i+1];
                }
                newPes[top]= p;
            }

            //for ( int i=0; i<newPes.length; i++ ) {
            //    System.err.println( "moveToStackTop: " + dom.getPlotElements(i) + "(" + dom.getPlotElements(i).getPlotId() + ")" + " " + newPes[i] +  "(" + newPes[i].getPlotId()+ ")"  );
            //}
            dom.setPlotElements(newPes);

        } finally {
            lock.unlock();
        }

    }


    void bindPEToColorbar( PlotElement pe ) {
        if ( this.plot==null ) {
            throw new NullPointerException("plotController.plot is null"); // bug 1419: I think I see this on the server
        }
        if ( pe.style==null ) {
            throw new NullPointerException("Style pe.style is null"); // bug 1419: I think I see this on the server
        }
        this.dom.controller.bind( pe.style, PlotElementStyle.PROP_COLORTABLE, this.plot, Plot.PROP_COLORTABLE );
    }

    /**
     * add the plot element to the plot, including the renderer and bindings.
     * @param p
     * @param reset
     */
    synchronized void addPlotElement(PlotElement p,boolean reset) {
        
        if ( p==null ) throw new NullPointerException("PlotElement p is null"); // bug 1419: I think I see this on the server
        
        Renderer rr= p.controller.getRenderer();

        if ( rr instanceof SpectrogramRenderer ) {
            ((SpectrogramRenderer)rr).setColorBar( getDasColorBar() );
        } else if ( rr instanceof SeriesRenderer ) {
            ((SeriesRenderer)rr).setColorBar( getDasColorBar() );
        }

        bindPEToColorbar( p );

        boolean toTop= rr!=null && !( rr instanceof SpectrogramRenderer );
        if ( rr!=null ) {
            if ( !toTop ) { // kludge to put on the bottom
                dasPlot.addRenderer(0,rr);
            } else {
                dasPlot.addRenderer(rr);
            }
        }
        RenderType rt = p.getRenderType();
        //p.setPlotId(plot.getId());
        p.plotId= plot.getId();
        if ( reset ) p.controller.doResetRenderType(rt);

        if ( !dom.controller.isValueAdjusting() ) {
            doPlotElementDefaultsChange(p);
        } else {
            //TODO: there's a copy of this code in doPlotElementDefaultsChange
            if ( this.plotElement!=null ) {
                this.plotElement.getController().removePropertyChangeListener( PlotElementController.PROP_DATASET, plotElementDataSetListener );
            }
            this.plotElement= p;
            // this used to happen in doPlotElementDefaultsChange
            p.getController().addPropertyChangeListener( PlotElementController.PROP_DATASET, plotElementDataSetListener );
        }
        if ( !pdListen.contains(p) ) {
            p.addPropertyChangeListener( PlotElement.PROP_PLOT_DEFAULTS, plotDefaultsListener );
            p.addPropertyChangeListener( PlotElement.PROP_RENDERTYPE, renderTypeListener );
            pdListen.add(p);
        }
        p.setPlotId(plot.getId());
        checkRenderType();

        if ( rr!=null && toTop ) {
            moveToStackTop(p);
        }

//        DasPlot pl= p.controller.getDasPlot();
//        if ( pl!=null ) {
//            DasCanvas c= pl.getCanvas();
//            System.err.println("==AFTER===");
//            for ( DasCanvasComponent cc: c.getCanvasComponents() ) {
//                if ( cc instanceof DasColorBar ) System.err.println(cc);
//            }
//        }
    }

    /**
     * add a context overview.  This uses controllers, and should be rewritten
     * so that it doesn't.
     * @return the new plot which is the overview.
     */
    public Plot contextOverview( ) {
        DomLock lock= changesSupport.mutatorLock();
        lock.lock("Context Overview");
        Plot that;
        try {
            Plot domPlot= this.plot;
            ApplicationController controller= dom.getController();
            that = controller.copyPlotAndPlotElements(domPlot, null, false, false);
            that.setTitle( "" );
            controller.bind(domPlot.getYaxis(), Axis.PROP_LOG, that.getYaxis(), Axis.PROP_LOG);
            controller.bind(domPlot.getZaxis(), Axis.PROP_RANGE, that.getZaxis(), Axis.PROP_RANGE);
            controller.bind(domPlot.getZaxis(), Axis.PROP_LOG, that.getZaxis(), Axis.PROP_LOG);
            controller.bind(domPlot.getZaxis(), Axis.PROP_LABEL, that.getZaxis(), Axis.PROP_LABEL);
            controller.addConnector(domPlot, that);

            controller.setPlot(that);
            AutoplotUtil.resetZoomY(dom);

            double nmin,nmax;
            if ( domPlot.getYaxis().isLog() ) {
                nmin= DatumRangeUtil.normalizeLog( that.getYaxis().getRange(), domPlot.getYaxis().getRange().min() );
                nmax= DatumRangeUtil.normalizeLog( that.getYaxis().getRange(), domPlot.getYaxis().getRange().max() );
            } else {
                nmin= DatumRangeUtil.normalize( that.getYaxis().getRange(), domPlot.getYaxis().getRange().min() );
                nmax= DatumRangeUtil.normalize( that.getYaxis().getRange(), domPlot.getYaxis().getRange().max() );
            }
            if ( nmax-nmin > 0.9 ) {
                that.getYaxis().setRange( domPlot.getYaxis().getRange() );
            }

            AutoplotUtil.resetZoomX(dom);

            //that.getController().resetZoom(true, true, false);
        } finally {
            lock.unlock();
        }
        return that;
    }

    private void removeBindingsPEToColorbar( PlotElement pe ) {
        this.dom.controller.unbind( pe.style, PlotElementStyle.PROP_COLORTABLE, this.plot, Plot.PROP_COLORTABLE );
    }

    /**
     * remove the plot element from this plot: remove renderer, remove bindings, etc.
     * @param p
     */
    synchronized void removePlotElement(PlotElement p) {
        Renderer rr= p.controller.getRenderer();
        // setPlotId re-enters this code, so we need to rem
        if ( rr!=null && dasPlot.containsRenderer(rr) ) dasPlot.removeRenderer(rr);
        if ( rr instanceof SpectrogramRenderer ) {
            ((SpectrogramRenderer)rr).setColorBar(null);
        } else if ( rr instanceof SeriesRenderer ) {
            ((SeriesRenderer)rr).setColorBar(null);
        }

        doPlotElementDefaultsChange(null);
        p.removePropertyChangeListener( PlotElement.PROP_PLOT_DEFAULTS, plotDefaultsListener );
        p.removePropertyChangeListener( PlotElement.PROP_RENDERTYPE, renderTypeListener );
        removeBindingsPEToColorbar(p);
        pdListen.remove(p);
        if ( !p.getPlotId().equals("") ) p.setPlotId("");
        checkRenderType();
    }

//    private static String getCommonPart( String wordA, String wordB ) {
//        StringBuilder common= new StringBuilder();
//        StringBuilder commonTest= new StringBuilder();
//        for(int i=0;i<wordA.length();i++){
//            for(int j=0;j<wordB.length();j++){
//                if( i<wordA.length() && wordA.charAt(i)==wordB.charAt(j)){
//                    commonTest.append( wordA.charAt(i) );
//                    i++;
//                }  else {
//                    if ( commonTest.length()>common.length() ) {
//                        common= commonTest;
//                    }
//                    commonTest= new StringBuilder();
//                }
//            }
//        }
//        if ( commonTest.length()>common.length() ) {
//            common= commonTest;
//            commonTest= new StringBuilder();
//        }
//        return common.toString();
//    }

    private Plot getPlotDefaultsOneFamily( List<PlotElement> pes ) {
        Plot result= null;

        if ( pes.size()>0 ) result= pes.get(0).getPlotDefaults(); // more like old behavior
        
        return result;
    }


    /**
     * check all the plotElements' plot defaults, so that properties marked as automatic can be reset.
     * @param plotElement
     */
    private void doPlotElementDefaultsChange( PlotElement pele ) {

        if ( pele!=null && isAutoBinding() ) doCheckBindings( plot, pele.getPlotDefaults() );

        List<BindingModel> bms= dom.getController().findBindings( dom, Application.PROP_TIMERANGE, null, Axis.PROP_RANGE );
        BindingModel existingBinding= dom.getController().findBinding( dom, Application.PROP_TIMERANGE, plot.xaxis, Axis.PROP_RANGE );
        if ( bms.contains(existingBinding) ) {
            if ( bms.size()>1 ) {
                plot.getXaxis().setAutoRange(false);
            }
        }

        List<PlotElement> pes= dom.getController().getPlotElementsFor(plot);
        if ( DomUtil.oneFamily( pes ) ) {
            Plot defaults= getPlotDefaultsOneFamily(pes);
            PlotElement p= pes.get(0);

            if ( !plot.controller.getApplication().getController().isValueAdjusting() ) {
                // I would reset the autorange property here for unbound axes before v2013a_16.  I'm not sure why...
                if ( !this.dom.getController().isBoundAxis(plot.getXaxis()) ) {
                    if ( !defaults.getXaxis().getRange().getUnits().isConvertibleTo( plot.getXaxis().getRange().getUnits() ) ) {
                        plot.getXaxis().setAutoRange(true);
                    }
                }

                if ( !this.dom.getController().isBoundAxis(plot.getYaxis()) ) {
                    if ( !defaults.getYaxis().getRange().getUnits().isConvertibleTo( plot.getYaxis().getRange().getUnits() ) ) {
                        plot.getYaxis().setAutoRange(true);
                    }
                }

                if ( !this.dom.getController().isBoundAxis(plot.getZaxis()) ) {
                    if ( !defaults.getZaxis().getRange().getUnits().isConvertibleTo( plot.getZaxis().getRange().getUnits() ) ) {
                        plot.getZaxis().setAutoRange(true);
                    }
                }
                
            } else {
                logger.fine("value is adjusting, no reset autorange");
            }

            if ( this.plotElement!=null ) {
                this.plotElement.getController().removePropertyChangeListener( PlotElementController.PROP_DATASET, plotElementDataSetListener );
            }
            this.plotElement= p;
            this.plotElement.getController().addPropertyChangeListener( PlotElementController.PROP_DATASET, plotElementDataSetListener );
            if ( true ) {   // bugfix 1157: simplify logic, was: if ( pele==null || defaults.getXaxis().isAutoRange()!=false ) { //TODO: why is this?  /home/jbf/ct/hudson/vap/geo_1.vap wants it
                if ( defaults!=null ) {
                    if ( plot.isAutoLabel() ) plot.getController().setTitleAutomatically( defaults.getTitle() );
                    if ( plot.getXaxis().isAutoLabel() && defaults.getXaxis().isAutoLabel() ) plot.getXaxis().getController().setLabelAutomatically( defaults.getXaxis().getLabel() );
                    if ( plot.getYaxis().isAutoLabel() && defaults.getYaxis().isAutoLabel() ) plot.getYaxis().getController().setLabelAutomatically( defaults.getYaxis().getLabel() );
                    if ( plot.getZaxis().isAutoLabel() && defaults.getZaxis().isAutoLabel() ) plot.getZaxis().getController().setLabelAutomatically( defaults.getZaxis().getLabel() );
                    if ( plot.getXaxis().isAutoRange() && plot.getYaxis().isAutoRange() )  plot.setIsotropic( defaults.isIsotropic() );
                } else {
                    if ( plot.isAutoLabel() ) plot.getController().setTitleAutomatically( p.getPlotDefaults().getTitle() ); // stack of traces
                    if ( plot.getXaxis().isAutoLabel() ) plot.getXaxis().getController().setLabelAutomatically( "" );
                    if ( plot.getYaxis().isAutoLabel() ) plot.getYaxis().getController().setLabelAutomatically( "" );
                    if ( plot.getZaxis().isAutoLabel() ) plot.getZaxis().getController().setLabelAutomatically( "" );
                }
            }
        }

        //if ( dom.getController().getPlotElementsFor(plot).isEmpty() ) {
            //System.err.println("should this happen?  see bug 2992903");
        //}

        if ( dom.getOptions().isAutoranging() ) {
            resetZoom( plot.getXaxis().isAutoRange() && pele!=null && pele.getPlotDefaults().getXaxis().isAutoRange(),
                plot.getYaxis().isAutoRange(), 
                plot.getZaxis().isAutoRange() );
        }
    }

    /** 
     * see https://sourceforge.net/tracker/?func=detail&aid=3104572&group_id=199733&atid=970682  We've loaded an old
     * vap file and we need to convert units.dimensionless to a correct unit.
     * @param e
     */
    protected void doPlotElementDefaultsUnitsChange( PlotElement e ) {
        DatumRange elerange;
        DatumRange range;
        elerange= e.getPlotDefaults().getXaxis().getRange();
        range=  plot.getXaxis().getRange();
        if ( elerange.getUnits() != range.getUnits() && range.getUnits()==Units.dimensionless ) {
            DatumRange dr;
            if ( UnitsUtil.isTimeLocation(elerange.getUnits()) ) {
                dr= DatumRangeUtil.parseTimeRangeValid( "2010-01-01" );
            } else {
                dr= new DatumRange( range.min().doubleValue(Units.dimensionless), range.max().doubleValue(Units.dimensionless), elerange.getUnits() );
            }
            boolean auto= plot.getXaxis().autoRange;
            plot.getXaxis().setRange( dr );
            plot.getXaxis().setAutoRange(auto);
        }
        elerange= e.getPlotDefaults().getYaxis().getRange();
        range=  plot.getYaxis().getRange();
        if ( !UnitsUtil.isTimeLocation(elerange.getUnits()) && elerange.getUnits() != range.getUnits() && range.getUnits()==Units.dimensionless ) {
            DatumRange dr;
            if ( UnitsUtil.isTimeLocation(elerange.getUnits()) ) {
                dr= DatumRangeUtil.parseTimeRangeValid( "2010-01-01" );
            } else {
                dr= new DatumRange( range.min().doubleValue(Units.dimensionless), range.max().doubleValue(Units.dimensionless), elerange.getUnits() );
            }
            boolean auto= plot.getYaxis().autoRange;
            plot.getYaxis().setRange( dr );
            plot.getYaxis().setAutoRange(auto);
        }
        elerange= e.getPlotDefaults().getZaxis().getRange();
        range=  plot.getZaxis().getRange();
        if ( !UnitsUtil.isTimeLocation(elerange.getUnits()) && elerange.getUnits() != range.getUnits() && range.getUnits()==Units.dimensionless  ) {
            DatumRange dr;
            if ( UnitsUtil.isTimeLocation(elerange.getUnits()) ) {
                dr= DatumRangeUtil.parseTimeRangeValid( "2010-01-01" );
            } else {
                dr= new DatumRange( range.min().doubleValue(Units.dimensionless), range.max().doubleValue(Units.dimensionless), elerange.getUnits() );
            }
            boolean auto= plot.getZaxis().autoRange;
            plot.getZaxis().setRange( dr );
            plot.getZaxis().setAutoRange(auto);
        }

    }

    /**
     * See https://sourceforge.net/p/autoplot/bugs/1649/
     * @param newSettingsXaxis
     * @return 
     */
    private boolean shouldBindX( Axis newSettingsXaxis ) {
        boolean shouldBindX= false;
        DatumRange xrange= newSettingsXaxis.getRange();  
        if ( dom.timeRange.getUnits().isConvertibleTo(xrange.getUnits()) &&
                UnitsUtil.isTimeLocation(xrange.getUnits()) ) {
            if ( dom.controller.isConnected( plot ) ) {
                logger.log(Level.FINER, "not binding because plot is connected: {0}", plot);
                // don't bind a connected plot.
            } else if ( dom.timeRange.intersects( xrange ) ) {
                try {
                    double reqOverlap= UnitsUtil.isTimeLocation( dom.timeRange.getUnits() ) ? 0.01 : 0.8;
                    DatumRange droverlap= DatumRangeUtil.sloppyIntersection( xrange, dom.timeRange );
                    double overlap= droverlap.width().divide(dom.timeRange.width()).doubleValue(Units.dimensionless);
                    if ( overlap > 1.0 ) overlap= 1/overlap;
                    if ( overlap > reqOverlap ) {
                        shouldBindX= true;
                        logger.log( Level.FINER, "binding axis because there is significant overlap dom.timerange={0}", dom.timeRange.toString());
                        dom.getController().setStatus("binding axis because there is significant overlap");
                    }
                } catch ( InconvertibleUnitsException ex ) {
                    shouldBindX= false;
                } catch ( IllegalArgumentException ex ) {
                    shouldBindX= false;  //logERatio
                }                    
            }
        }
        return shouldBindX;
    }
    
    /**
     * after autoranging, we need to check to see if a plotElement's plot looks like
     * it should be automatically bound or unbound.
     *
     * We unbind if changing this plot's axis settings will make another plot
     * invalid.
     *
     * We bind if the axis setting is similar to the application timerange.
     * @param plot the plot whose binds we are checking.  Bindings with this node may be added or removed.
     * @param newSettings the new plot settings from autoranging.
     */
    private void doCheckBindings( Plot plot, Plot newSettings ) {
        logger.entering( "org.virbo.autoplot.PlotController", "doCheckBindings" );
        boolean shouldBindX= false;
        boolean shouldSetAxisRange= false; // true indicates that the dom.timeRange already contains the range
        List<BindingModel> bms= dom.getController().findBindings( dom, Application.PROP_TIMERANGE, null, Axis.PROP_RANGE );
        BindingModel bm= dom.getController().findBinding( dom, Application.PROP_TIMERANGE, plot.getXaxis(), Axis.PROP_RANGE );
        if ( bm!=null ) bms.remove(bm);

        if ( ! plot.isAutoBinding() ) {
            return;
        }

        boolean needToAutorangeAfterAll= false;
        
        // if we aren't autoranging, then only change the bindings if there will be a conflict.
        if ( plot.getXaxis().isAutoRange()==false ) {
            shouldBindX= bm!=null; // this binding is no longer set.
            if ( bm!=null && !newSettings.getXaxis().getRange().getUnits().isConvertibleTo( plot.getXaxis().getRange().getUnits() ) ) {
                shouldBindX= false;
                logger.finer("remove timerange binding that would cause inconvertable units");
            }
            if (!shouldBindX) shouldBindX= shouldBindX(newSettings.getXaxis());
            needToAutorangeAfterAll= UnitsUtil.isTimeLocation( plot.getXaxis().getRange().getUnits() ) && !shouldBindX;
        }

        if ( newSettings.getXaxis().isLog()==false && ( needToAutorangeAfterAll || plot.getXaxis().isAutoRange() ) ) {
            if ( bms.isEmpty() && UnitsUtil.isTimeLocation( newSettings.getXaxis().getRange().getUnits() ) ) {
                logger.finer("binding axis to timeRange because no one is using it");
                DatumRange tr= plot.getXaxis().getRange(); // it's already been set for TimeSeriesBrowse    
                if ( UnitsUtil.isTimeLocation( tr.getUnits() ) ) {
                    dom.setTimeRange( tr ) ; // newSettings.getXaxis().getRange() ); // TimeSeriesBrowse                   
                } else {
                    dom.setTimeRange( newSettings.getXaxis().getRange() );      // fall back to old logic.
                }
                shouldBindX= true;
                shouldSetAxisRange= true;
            }
            if ( !plot.getXaxis().isAutoRange() ) {
                plot.getXaxis().setAutoRange(true); // setting the time range would clear autoRange here.
            }
            shouldBindX= shouldBindX(newSettings.getXaxis());
        }

        if ( shouldBindX && !plot.getColumnId().equals( dom.getCanvases(0).getMarginColumn().getId() ) ) {
            logger.log(Level.FINER, "not binding because plot is not attached to marginRow: {0}", plot.getXaxis());
            //TODO: Reiner has a two-column canvas that has each plot bound.  It might be
            //  nice to support this.
            shouldBindX= false;
            dom.getController().setStatus("not binding axis because plot is not attached to marginRow");
        }
        
        if ( bm==null && shouldBindX ) {
            logger.log(Level.FINER, "add binding: {0}", plot.getXaxis());
            plot.getXaxis().setLog(false);

            dom.getController().bind( dom, Application.PROP_TIMERANGE, plot.getXaxis(), Axis.PROP_RANGE );

            BindingModel b= dom.getController().findBinding( dom, Application.PROP_TIMERANGE, plot, Plot.PROP_CONTEXT );
            if ( b!=null ) dom.getController().deleteBinding(b); // https://sourceforge.net/p/autoplot/bugs/868/
            //if ( !CanvasUtil.getMostBottomPlot(dom.getController().getCanvasFor(plot))==plot ) {
            //    plot.getXaxis().setDrawTickLabels(false);
            //} //TODO: could disable tick label drawing automatically.

        } else if ( bm!=null && !shouldBindX ) {
            logger.log(Level.FINER, "remove binding: {0}", bm);
            //BindingModel b= dom.getController().findBinding( dom, Application.PROP_TIMERANGE, plot, Plot.PROP_CONTEXT );
            //if ( b!=null ) dom.getController().deleteBinding(b); // 3516161 https://sourceforge.net/p/autoplot/bugs/868/
            plot.setContext( dom.getTimeRange() );
            dom.getController().deleteBinding(bm);
        }
        
        if ( needToAutorangeAfterAll ) {
            plot.getXaxis().setAutoRange(true); // the order matters now, because this will cause the setting to change.
        }
        
        if ( !shouldBindX ) {
            List<BindingModel> b= dom.getController().findBindings( dom, Application.PROP_TIMERANGE );
            if ( b.isEmpty() && UnitsUtil.isTimeLocation( plot.getContext().getUnits() ) ) {
                DatumRange dr= plot.getContext();
                dom.setTimeRange(dr);
            }
            dom.getController().bind( dom, Application.PROP_TIMERANGE, plot, Plot.PROP_CONTEXT );
        }

        plot.setAutoBinding(false);
        
    }

    /**
     * delete the das peer that implements this node.
     */
    void deleteDasPeer() {
        final DasPlot p = getDasPlot();
        final DasColorBar cb = getDasColorBar();
        final DasCanvas c= p.getCanvas();
        p.getDasMouseInputAdapter().setFeedback( null );
        if ( c!=null ) {
            SwingUtilities.invokeLater( new Runnable() {
                @Override
                public void run() {
                    c.remove(p);
                    c.remove(cb);
                }
            } );
        }
    }

    private synchronized void bindTo(DasPlot p) {
        ApplicationController ac= dom.controller;
        titleConverter= new LabelConverter( dom, plot, null, null, null );
        ac.bind( this.plot, Plot.PROP_TITLE, p, DasPlot.PROP_TITLE, titleConverter );
        Converter plotContextConverter= new Converter() {
            @Override
            public Object convertForward(Object s) {
                if ( s==Application.DEFAULT_TIME_RANGE ) {
                    return null;
                } else {
                    return s;
                }                
            }
            @Override
            public Object convertReverse(Object t) {
                if ( t==null ) {
                    return Application.DEFAULT_TIME_RANGE;
                } else {
                    return t;
                }
            }
        };
        ac.bind( this.plot, Plot.PROP_CONTEXT, p, DasPlot.PROP_CONTEXT, plotContextConverter );
        ac.bind( this.plot, Plot.PROP_ISOTROPIC, p, DasPlot.PROP_ISOTROPIC );
        ac.bind( this.plot, Plot.PROP_DISPLAYTITLE, p, DasPlot.PROP_DISPLAYTITLE );
        ac.bind( this.plot, Plot.PROP_DISPLAYLEGEND, p, DasPlot.PROP_DISPLAYLEGEND );
        ac.bind( this.plot, Plot.PROP_FONTSIZE, p, DasPlot.PROP_FONTSIZE );
        ac.bind( dom.options, Options.PROP_PRINTINGLOGLEVEL, p, DasPlot.PROP_PRINTINGLOGLEVEL );
        ac.bind( dom.options, Options.PROP_DISPLAYLOGLEVEL, p, DasPlot.PROP_LOG_LEVEL );
        ac.bind( dom.options, Options.PROP_LOGMESSAGETIMEOUTSEC, p, DasPlot.PROP_LOG_TIMEOUT_SEC );
        
    }

    /**
     * remove the bindings created in bindTo.
     */
    protected void removeBindings() {
        ApplicationController ac= dom.controller;
        DasPlot p= this.dasPlot;
        // these were unbound already.
//        ac.unbind( this.plot, Plot.PROP_TITLE, p, DasPlot.PROP_TITLE );
//        ac.unbind( this.plot, Plot.PROP_CONTEXT, p, DasPlot.PROP_CONTEXT );
//        ac.unbind( this.plot, Plot.PROP_ISOTROPIC, p, DasPlot.PROP_ISOTROPIC );
//        ac.unbind( this.plot, Plot.PROP_DISPLAYTITLE, p, DasPlot.PROP_DISPLAYTITLE );
//        ac.unbind( this.plot, Plot.PROP_DISPLAYLEGEND, p, DasPlot.PROP_DISPLAYLEGEND );
        //int i= dom.options.boundCount();
        ac.unbind(dom.options, Options.PROP_DRAWGRID, p, "drawGrid");
        ac.unbind(dom.options, Options.PROP_DRAWMINORGRID, p, "drawMinorGrid");
        ac.unbind(dom.options, Options.PROP_FLIPCOLORBARLABEL, this.plot.getZaxis().getController().dasAxis, "flipLabel");
        ac.unbind(dom.options, Options.PROP_FLIPCOLORBARLABEL, this.plot.getYaxis().getController().dasAxis, "flipLabel");
        ac.unbind(dom.options, Options.PROP_TICKLEN, p.getXAxis(), "tickLength");
        ac.unbind(dom.options, Options.PROP_TICKLEN, p.getYAxis(), "tickLength");
        ac.unbind(dom.options, Options.PROP_TICKLEN, this.dasColorBar, "tickLength");
        ac.unbind( dom.options, Options.PROP_MULTILINETEXTALIGNMENT, p, DasPlot.PROP_MULTILINETEXTALIGNMENT );
        ac.unbind( dom.options, Options.PROP_PRINTINGLOGLEVEL, p, DasPlot.PROP_PRINTINGLOGLEVEL );
        ac.unbind( dom.options, Options.PROP_DISPLAYLOGLEVEL, p, DasPlot.PROP_LOG_LEVEL );
        ac.unbind( dom.options, Options.PROP_LOGMESSAGETIMEOUTSEC, p, DasPlot.PROP_LOG_TIMEOUT_SEC );
        ac.unbind( dom.options, Options.PROP_OVERRENDERING, p, DasPlot.PROP_OVERSIZE );
        dom.options.removePropertyChangeListener( Options.PROP_DAY_OF_YEAR, dayOfYearListener );
        dom.options.removePropertyChangeListener( Options.PROP_MOUSEMODULE, mouseModuleListener );
        //System.err.println("removeBindings "+i+" -> "+dom.options.boundCount() );
    }
    
    public BindingModel[] getBindings() {
        return dom.controller.getBindingsFor(plot);
    }

    public BindingModel getBindings(int index) {
        return getBindings()[index];
    }


    protected JMenuItem plotElementPropsMenuItem = null;
    public static final String PROP_PLOTELEMENTPROPSMENUITEM = "plotElementPropsMenuItem";

    public JMenuItem getPlotElementPropsMenuItem() {
        return plotElementPropsMenuItem;
    }

    public void setPlotElementPropsMenuItem(JMenuItem pelePropsMenuItem) {
        JMenuItem old = this.plotElementPropsMenuItem;
        this.plotElementPropsMenuItem = pelePropsMenuItem;
        propertyChangeSupport.firePropertyChange(PROP_PLOTELEMENTPROPSMENUITEM, old, pelePropsMenuItem);
    }

    public Application getApplication() {
        return dom;
    }
    @Override
    public String toString() {
        return this.plot + " controller";
    }

    /**
     * set the title, leaving autoLabel true.
     * @param title
     */
    public void setTitleAutomatically(String title) {
        plot.setTitle(title);
        plot.setAutoLabel(true);
    }

    private JMenuItem[] expertMenuItems;

    /**
     * provide spot to locate the menu items that are hidden in basic mode.
     * @param items
     */
    public void setExpertMenuItems( JMenuItem[] items ) {
        this.expertMenuItems= Arrays.copyOf( items, items.length );
    }

    public JMenuItem[] getExpertMenuItems() {
        return Arrays.copyOf( expertMenuItems, expertMenuItems.length );
    }

    public void setExpertMode( boolean expert ) {
        for ( JMenuItem mi: expertMenuItems ) {
            mi.setVisible(expert);
        }
    }

    /**
     * return the row for this plot.  This just calls dom.canvases.get(0).getController().getRowFor(this.plot);
     * @return
     */
    public Row getRow() {
        return dom.canvases.get(0).getController().getRowFor(this.plot);
    }

    /**
     * return the row for this plot.  This just calls dom.canvases.get(0).getController().getRowFor(this.plot);
     * @return
     */
    public Column getColumn() {
        return dom.canvases.get(0).getController().getColumnFor(this.plot);
    }
}
