/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.dnd.DropTarget;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TooManyListenersException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.InconvertibleUnitsException;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.event.BoxZoomMouseModule;
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
import org.jdesktop.beansbinding.Converter;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.autoplot.MouseModuleType;
import org.virbo.autoplot.RenderType;
import org.virbo.autoplot.RenderTypeUtil;
import org.virbo.autoplot.dom.ChangesSupport.DomLock;
import org.das2.datum.format.DateTimeDatumFormatter;
import org.virbo.autoplot.util.RunLaterListener;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;

/**
 * Manages a Plot node, for example listening for autoRange updates and layout
 * changes.
 * @author jbf
 */
public class PlotController extends DomNodeController {

    Application dom;
    Plot plot;
    private DasPlot dasPlot;
    private DasColorBar dasColorBar;

    /**
     * the plot elements we listen to for autoranging.
     */
    public List<PlotElement> pdListen= new LinkedList();

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
        this.plot.addPropertyChangeListener( Plot.PROP_ID, new PropertyChangeListener() {
            public void propertyChange( PropertyChangeEvent evt ) {
                if ( dom.controller.isValueAdjusting() ) return;
                DomLock lock = dom.controller.mutatorLock();
                lock.lock( "Changing plot id" );
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
                lock.unlock();
            }
        });
        dom.options.addPropertyChangeListener( Options.PROP_DAY_OF_YEAR, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                final DasAxis update= PlotController.this.plot.getXaxis().controller.dasAxis;
                updateAxisFormatter(update);
            }
        });
        dom.options.addPropertyChangeListener( Options.PROP_MOUSEMODULE, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                DasPlot p= dasPlot;
                MouseModuleType mm= (MouseModuleType) evt.getNewValue();
                MouseModule m= null;
                String l= null;
                if ( mm==MouseModuleType.boxZoom ) {
                    m= p.getDasMouseInputAdapter().getModuleByLabel("Box Zoom");
                } else if ( mm==MouseModuleType.crosshairDigitizer ) {
                    m= p.getDasMouseInputAdapter().getModuleByLabel("Crosshair Digitizer");
                } else if ( mm==MouseModuleType.zoomX ) {
                    m= p.getDasMouseInputAdapter().getModuleByLabel("Zoom X");
                }
                if ( m!=null ) {
                    p.getDasMouseInputAdapter().setPrimaryModule( m );
                } else {
                    logger.log( Level.WARNING, "logger note recognized: {0}", mm);
                }
            }
        });
        plot.controller= this;
    }

    public PropertyChangeListener rowColListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
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

    /**
     * return the Canvas containing this plot, or null if this cannot be resolved.
     * 
     * @return
     */
    private Canvas getCanvasForPlot() {
        Canvas[] cc= dom.getCanvases();
        for ( Canvas c: cc ) {
            for ( Row r: c.getRows() ) {
                if ( r.getId().equals(plot.getRowId()) ) {
                    return c;
                }
            }
        }
        return null;
    }

    private PropertyChangeListener labelListener= new PropertyChangeListener() {
         public void propertyChange(PropertyChangeEvent evt) {
            if ( evt.getPropertyName().equals(Plot.PROP_TITLE) ) {
                plot.setAutoLabel(false);
            }
         }
    };

    private PropertyChangeListener ticksURIListener= new PropertyChangeListener() {
         public void propertyChange(PropertyChangeEvent evt) {
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

    protected void createDasPeer( Canvas canvas, Row domRow ,Column domColumn) {

        Application application= dom;

        DatumRange x = this.plot.xaxis.range;
        DatumRange y = this.plot.yaxis.range;
        DasAxis xaxis = new DasAxis(x.min(), x.max(), DasAxis.HORIZONTAL);
        DasAxis yaxis = new DasAxis(y.min(), y.max(), DasAxis.VERTICAL);

        xaxis.setEnableHistory(false);
        //xaxis.setUseDomainDivider(true);
        yaxis.setEnableHistory(false);
        //yaxis.setUseDomainDivider(true);

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
        if ( m==MouseModuleType.boxZoom ) {
            // do nothing
        } else if ( m==MouseModuleType.crosshairDigitizer ) {
            mm= dasPlot1.getDasMouseInputAdapter().getModuleByLabel("Crosshair Digitizer");
        } else if ( m==MouseModuleType.zoomX ) {
            mm= dasPlot1.getDasMouseInputAdapter().getModuleByLabel("Zoom X");
        }
        if ( mm!=null ) dasPlot1.getDasMouseInputAdapter().setPrimaryModule(mm);

        dasCanvas.add(colorbar, dasPlot1.getRow(), DasColorBar.getColorBarColumn(dasPlot1.getColumn()));

        MouseModule zoomPan = new ZoomPanMouseModule(dasPlot1, dasPlot1.getXAxis(), dasPlot1.getYAxis());
        dasPlot1.getDasMouseInputAdapter().setSecondaryModule(zoomPan);

        MouseModule zoomPanX = new ZoomPanMouseModule(dasPlot1.getXAxis(), dasPlot1.getXAxis(), null);
        dasPlot1.getXAxis().getDasMouseInputAdapter().setSecondaryModule(zoomPanX);

        MouseModule zoomPanY = new ZoomPanMouseModule(dasPlot1.getYAxis(), null, dasPlot1.getYAxis());
        dasPlot1.getYAxis().getDasMouseInputAdapter().setSecondaryModule(zoomPanY);

        MouseModule zoomPanZ = new ZoomPanMouseModule(colorbar, null, colorbar);
        colorbar.getDasMouseInputAdapter().setSecondaryModule(zoomPanZ);

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
        ac.bind(application.getOptions(), Options.PROP_TICKLEN, dasPlot1.getXAxis(), "tickLength");
        ac.bind(application.getOptions(), Options.PROP_TICKLEN, dasPlot1.getYAxis(), "tickLength");
        ac.bind(application.getOptions(), Options.PROP_TICKLEN, colorbar, "tickLength");

        ac.bind(this.plot, Plot.PROP_LEGENDPOSITION, dasPlot1, DasPlot.PROP_LEGENDPOSITION );
        ac.bind(this.plot, Plot.PROP_DISPLAYLEGEND, dasPlot1, DasPlot.PROP_DISPLAYLEGEND );

        ac.bind(application.getOptions(), Options.PROP_OVERRENDERING, dasPlot1, "overSize");

        ac.bind(this.plot, Plot.PROP_VISIBLE, dasPlot1, "visible" );
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
                logger.log(Level.SEVERE, null, ex);
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
            axis.setUserDatumFormatter(null);
        }
    }

    private PropertyChangeListener listener = new PropertyChangeListener() {
        @Override
        public String toString() {
            return ""+PlotController.this;
        }
        public void propertyChange(PropertyChangeEvent e) {
            if (e.getSource() instanceof DasAxis) {
                DasAxis axis = (DasAxis) e.getSource();
                Axis domAxis= getDomAxis(axis);
                if ( domAxis==null ) return;
                if ( e.getPropertyName().equals("Frame.active") ) return;
                if ( e.getPropertyName().equals(DasAxis.PROP_UNITS)
                        || e.getPropertyName().equals(DasAxis.PROPERTY_DATUMRANGE ) ) {
                    if ( axis.getDrawTca() && domAxis.getLabel().length()==0 ) {
                        domAxis.setLabel("%{RANGE}");
                    }
                }
                if ( e.getPropertyName().equals(DasAxis.PROP_UNITS) 
                        || e.getPropertyName().equals(DasAxis.PROPERTY_DATUMRANGE )
                        || e.getPropertyName().equals(DasAxis.PROP_LABEL) ) {
                    updateAxisFormatter(axis);
                }

                // we can safely ignore these events.
                if (((DasAxis) e.getSource()).valueIsAdjusting()) {
                    return;
                }

            } else if ( e.getPropertyName().equals( DasPlot.PROP_FOCUSRENDERER ) ) {

                List<PlotElement> eles= PlotController.this.dom.controller.getPlotElementsFor(plot);
                PlotElement fe= null;
                for ( PlotElement ele: eles ) {
                    if ( ele.getController().getRenderer()== e.getNewValue() ) {
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
     * @param a1
     * @param a2
     * @return
     */
    private Axis resolveSettings( Axis a1, Axis a2 ) {
        Axis result= new Axis();
        result.range = DatumRangeUtil.union( a1.range, a2.range );
        if ( a1.log==a2.log ) {
            result.log= a1.log;
        } else {
            result.log= result.range.min().doubleValue(result.range.getUnits()) > 0;
        }
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
     * set the zoom so that all of the plotElements' data is visible.  Thie means finding
     * the "union" of each plotElements' plotDefault ranges.  If any plotElement's default log
     * is false, then the new setting will be false.
     */
    public void resetZoom(boolean x, boolean y, boolean z) {
        List<PlotElement> elements = dom.controller.getPlotElementsFor(plot);
        if ( elements.size()==0 ) return;
        Plot newSettings = null;

        boolean haveTsb= false;

//        System.err.println("== resetZoom Elements==" );
//        for ( PlotElement p: elements ) {
//            System.err.println( p  +  " y= " + p.getPlotDefaults().getYaxis().getRange() );
//        }

        boolean warnedAboutUnits= false;
        for (PlotElement p : elements) {
            Plot plot1 = p.getPlotDefaults();
            if ( plot1.getXaxis().isAutoRange() ) {  // we use autoRange to indicate these are real settings, not just the defaults.
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
                if ( dsf!=null && dsf.getController()!=null && dsf.getController().tsb!=null ) {
                    haveTsb= true;
                }
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
            if ( dom.options.getAutorangeType().equals( Options.VALUE_AUTORANGE_TYPE_RELUCTANT) ) {
                newAxis= reluctantRanging( plot.getYaxis(), newAxis );
            }
            plot.getYaxis().setLog( newAxis.isLog() );
            plot.getYaxis().setRange( newAxis.getRange());
            plot.getYaxis().setAutoRange(true);
        }
        
        if ( z ) {
            logCheck(newSettings.getZaxis());
            Axis newAxis= newSettings.getZaxis();
            if ( dom.options.getAutorangeType().equals( Options.VALUE_AUTORANGE_TYPE_RELUCTANT) ) {
                newAxis= reluctantRanging( plot.getZaxis(), newAxis );
            }
            plot.getZaxis().setLog( newAxis.isLog() );
            plot.getZaxis().setRange( newAxis.getRange() );
            plot.getZaxis().setAutoRange(true);
        }
    }
    
    PropertyChangeListener plotDefaultsListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            PlotElement pele= (PlotElement)evt.getSource();
            List<PlotElement> pp= PlotController.this.dom.getController().getPlotElementsFor(plot);
            if ( pp.contains(pele) ) {
                pp.remove(pele);
            } else {
                //System.err.println("Plot "+plot+" doesn't contain the source plotElement "+plotElement +" see bug 2992903" ); //bug 2992903
                return;
            }
            
            if ( pele.isAutoRenderType() && pp.size()==0 ) {
                PlotController.this.setAutoBinding(true);
            }
            doPlotElementDefaultsChange(pele);
        }
    };

    PropertyChangeListener renderTypeListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            checkRenderType();
        }
    };

    PlotElement plotElement;

    private PropertyChangeListener plotElementDataSetListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            String contextStr;
            String shortContextStr;
            if ( plotElement==null ) {
                System.err.println("whoops, getting there");
                return;
            }
            QDataSet pds= plotElement.getController().getDataSet();
            logger.log( Level.FINE, "{0} dataSetListener", plot);
            if ( pds!=null ) {
                contextStr= DataSetUtil.contextAsString(pds);
                shortContextStr= contextStr;
                if ( !contextStr.equals("") ) {
                    String[] ss= contextStr.split("=");
                    if ( ss.length==2 ) {
                        shortContextStr= ss[1];
                    }
                }
            } else {
                contextStr= "";
                shortContextStr= "";
            }
            if ( plot.getTitle().contains("CONTEXT" ) ) {
                String title= insertString( plot.getTitle(), "CONTEXT", contextStr );
                dasPlot.setTitle(title);
            }
            if ( plot.getYaxis().getLabel().contains("CONTEXT") ) {
                String title= insertString( plot.getYaxis().getLabel(), "CONTEXT", shortContextStr );
                dasPlot.getYAxis().setLabel(title);
            }
            if ( plot.getXaxis().getLabel().contains("CONTEXT") ) {
                String title= insertString( plot.getXaxis().getLabel(), "CONTEXT", shortContextStr );
                dasPlot.getXAxis().setLabel(title);
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
        dasColorBar.setVisible(needsColorbar);
        plot.getZaxis().setVisible(needsColorbar);
    }

    void addPlotElement(PlotElement p) {
        addPlotElement(p,true);
    }

    synchronized List<Integer> indecesOfPlotElements( ) {
        List<Integer> indeces= new ArrayList<Integer>(dom.plotElements.size());
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


    private Map<String,String> pendingDefaultsChanges= new HashMap();

    void bindPEToColorbar( PlotElement pe ) {
        this.dom.controller.bind( pe.style, PlotElementStyle.PROP_COLORTABLE, this.plot, Plot.PROP_COLORTABLE );
    }

    /**
     * add the plot element to the plot, including the renderer and bindings.
     * @param p
     * @param reset
     */
    synchronized void addPlotElement(PlotElement p,boolean reset) {
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
     * @param domPlot
     * @returns the new plot which is the overview.
     */
    public Plot contextOverview( ) {
        DomLock lock= changesSupport.mutatorLock();
        lock.lock("Context Overview");
        Plot domPlot= this.plot;
        ApplicationController controller= dom.getController();
        Plot that = controller.copyPlotAndPlotElements(domPlot, null, false, false);
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
        lock.unlock();
        return that;
    }

    void removeBindingsPEToColorbar( PlotElement pe ) {
        this.dom.controller.unbind( pe.style, PlotElementStyle.PROP_COLORTABLE, this.plot, Plot.PROP_COLORTABLE );
    }

    /**
     * remove the plot element from this plot: remove renderer, remove bindings, etc.
     * @param p
     */
    synchronized void removePlotElement(PlotElement p) {
        Renderer rr= p.controller.getRenderer();
        if ( rr!=null ) dasPlot.removeRenderer(rr);
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
                if ( !this.dom.getController().isBoundAxis(plot.getXaxis()) ) {
                    plot.getXaxis().setAutoRange(true);
                }

                if ( !this.dom.getController().isBoundAxis(plot.getYaxis()) ) {
                    plot.getYaxis().setAutoRange(true);
                }

                if ( !this.dom.getController().isBoundAxis(plot.getZaxis()) ) {
                    plot.getZaxis().setAutoRange(true);
                }
            } else {
                logger.warning("value is adjusting, no reset autorange");
            }

            if ( this.plotElement!=null ) {
                this.plotElement.getController().removePropertyChangeListener( PlotElementController.PROP_DATASET, plotElementDataSetListener );
            }
            this.plotElement= p;
            this.plotElement.getController().addPropertyChangeListener( PlotElementController.PROP_DATASET, plotElementDataSetListener );
            if ( pele==null || defaults.getXaxis().isAutoRange()!=false ) { //TODO: why is this?  /home/jbf/ct/hudson/vap/geo_1.vap wants it
                if ( defaults!=null ) {
                    if ( plot.isAutoLabel() ) plot.getController().setTitleAutomatically( defaults.getTitle() );
                    if ( plot.getXaxis().isAutoLabel() ) plot.getXaxis().getController().setLabelAutomatically( defaults.getXaxis().getLabel() );
                    if ( plot.getYaxis().isAutoLabel() ) plot.getYaxis().getController().setLabelAutomatically( defaults.getYaxis().getLabel() );
                    if ( plot.getZaxis().isAutoLabel() ) plot.getZaxis().getController().setLabelAutomatically( defaults.getZaxis().getLabel() );
                    if ( plot.getXaxis().isAutoRange() && plot.getYaxis().isAutoRange() ) {
                        plot.setIsotropic( defaults.isIsotropic() );
                    }
                } else {
                    if ( plot.isAutoLabel() ) plot.getController().setTitleAutomatically( p.getPlotDefaults().getTitle() ); // stack of traces
                    if ( plot.getXaxis().isAutoLabel() ) plot.getXaxis().getController().setLabelAutomatically( "" );
                    if ( plot.getYaxis().isAutoLabel() ) plot.getYaxis().getController().setLabelAutomatically( "" );
                    if ( plot.getZaxis().isAutoLabel() ) plot.getZaxis().getController().setLabelAutomatically( "" );
                }
            }
        }

        if ( dom.getController().getPlotElementsFor(plot).size()==0 ) {
            //System.err.println("should this happen?  see bug 2992903");
        }

        if ( ( pele==null || pele.getPlotDefaults().getXaxis().isAutoRange()!=false ) && dom.getOptions().isAutoranging() ) {
            resetZoom( plot.getXaxis().isAutoRange(), plot.getYaxis().isAutoRange(), plot.getZaxis().isAutoRange() );
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
        boolean shouldBindX= false;
        boolean shouldSetAxisRange= false; // true indicates that the dom.timeRange already contains the range
        List<BindingModel> bms= dom.getController().findBindings( dom, Application.PROP_TIMERANGE, null, Axis.PROP_RANGE );
        BindingModel bm= dom.getController().findBinding( dom, Application.PROP_TIMERANGE, plot.getXaxis(), Axis.PROP_RANGE );
        if ( bm!=null ) bms.remove(bm);

        if ( ! plot.isAutoBinding() ) {
            return;
        }

        // if we aren't autoranging, then only change the bindings if there will be a conflict.
        if ( plot.getXaxis().isAutoRange()==false ) {
            shouldBindX= bm!=null;
            if ( bm!=null && !newSettings.getXaxis().getRange().getUnits().isConvertableTo( plot.getXaxis().getRange().getUnits() ) ) {
                shouldBindX= false;
                logger.finer("remove timerange binding that would cause inconvertable units");
            }
             plot.getXaxis().setAutoRange(true);
        }

        if ( newSettings.getXaxis().isLog()==false && plot.getXaxis().isAutoRange() ) {
            if ( bms.size()==0 && UnitsUtil.isTimeLocation( newSettings.getXaxis().getRange().getUnits() ) ) {
                logger.finer("binding axis to timeRange because no one is using it");
                dom.setTimeRange( newSettings.getXaxis().getRange() );
                shouldBindX= true;
                shouldSetAxisRange= true;
            }
            if ( !plot.getXaxis().isAutoRange() ) {
                plot.getXaxis().setAutoRange(true); // setting the time range would clear autoRange here.
            }
            DatumRange xrange= newSettings.getXaxis().getRange();
            if ( dom.timeRange.getUnits().isConvertableTo(xrange.getUnits()) &&
                    UnitsUtil.isTimeLocation(xrange.getUnits()) ) {
                if ( dom.controller.isConnected( plot ) ) {
                    logger.log(Level.FINER, "not binding because plot is connected: {0}", plot);
                    // don't bind a connected plot.
                } else if ( dom.timeRange.intersects( xrange ) ) {
                    // we want to support the case where we've zoomed in on a range and want to
                    // add another parameter.  We need to be more aggressive about binding in this
                    // case.
                    double reqOverlap= UnitsUtil.isTimeLocation( dom.timeRange.getUnits() ) ? 0.01 : 0.8;
                    DatumRange droverlap= DatumRangeUtil.sloppyIntersection( xrange, dom.timeRange );
                    try {
                        double overlap= droverlap.width().divide(dom.timeRange.width()).doubleValue(Units.dimensionless);
                        if ( overlap > 1.0 ) overlap= 1/overlap;
                        if ( !shouldBindX && overlap > reqOverlap ) {
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
            if ( b!=null ) dom.getController().deleteBinding(b); // 3516161
            //if ( !CanvasUtil.getMostBottomPlot(dom.getController().getCanvasFor(plot))==plot ) {
            //    plot.getXaxis().setDrawTickLabels(false);
            //} //TODO: could disable tick label drawing automatically.

        } else if ( bm!=null && !shouldBindX ) {
            logger.log(Level.FINER, "remove binding: {0}", bm);
            BindingModel b= dom.getController().findBinding( dom, Application.PROP_TIMERANGE, plot, Plot.PROP_CONTEXT );
            //if ( b!=null ) dom.getController().deleteBinding(b); // 3516161
            plot.setContext( dom.getTimeRange() );
            dom.getController().deleteBinding(bm);
        }

        if ( !shouldBindX ) {
            List<BindingModel> b= dom.getController().findBindings( dom, Application.PROP_TIMERANGE );
            if ( b.size()==0 && UnitsUtil.isTimeLocation( plot.getContext().getUnits() ) ) {
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
        if ( c!=null ) {
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    c.remove(p);
                    c.remove(cb);
                }
            } );
        }
    }

    /**
     * adjust the plot axes so it remains isotropic.
     * @param axis if non-null, the axis that changed, and the other should be adjusted.
     */
    private void checkIsotropic(DasAxis axis) {
        Datum scalex = dasPlot.getXAxis().getDatumRange().width().divide(dasPlot.getXAxis().getDLength());
        Datum scaley = dasPlot.getYAxis().getDatumRange().width().divide(dasPlot.getYAxis().getDLength());

        if ( ! scalex.getUnits().isConvertableTo(scaley.getUnits())
                || dasPlot.getXAxis().isLog()
                || dasPlot.getYAxis().isLog() ) {
            return;
        }

        if ( axis==null ) {
            axis= scalex.gt(scaley) ?  dasPlot.getXAxis()  : dasPlot.getYAxis() ;
        }

        if ( (axis == dasPlot.getXAxis() || axis == dasPlot.getYAxis()) ) {
            DasAxis otherAxis = dasPlot.getYAxis();
            if (axis == dasPlot.getYAxis()) {
                otherAxis = dasPlot.getXAxis();
            }
            Datum scale = axis.getDatumRange().width().divide(axis.getDLength());
            DatumRange otherRange = otherAxis.getDatumRange();
            Datum otherScale = otherRange.width().divide(otherAxis.getDLength());
            double expand = (scale.divide(otherScale).doubleValue(Units.dimensionless) - 1) / 2;
            if (Math.abs(expand) > 0.0001) {
                DatumRange newOtherRange = DatumRangeUtil.rescale(otherRange, 0 - expand, 1 + expand);
                otherAxis.setDatumRange(newOtherRange);
            }
        }
    }

    Converter contextConverter= new Converter() {
        @Override
        public Object convertForward(Object value) {
            String title= (String)value;
            if ( title.contains("%{CONTEXT}" ) ) {
                QDataSet context;
                String contextStr="";
                if ( plotElement!=null && plotElement.getController()!=null ) {
                    QDataSet ds= plotElement.getController().getDataSet();
                    if ( ds!=null ) {
                        contextStr= DataSetUtil.contextAsString(ds);
                    }
                }
                title= title.replaceAll("%\\{CONTEXT\\}", contextStr );
            }
            return title;
        }

        @Override
        public Object convertReverse(Object value) {
            String title= (String)value;
            String ptitle=  plot.getTitle();
            if (ptitle.contains("%{CONTEXT}") ) {
                String[] ss= ptitle.split("%\\{CONTEXT\\}",-2);
                if ( title.startsWith(ss[0]) && title.endsWith(ss[1]) ) {
                    return ptitle;
                }
            }
            return title;
        }
    };

    Converter labelContextConverter( final Axis axis ) {
        return new Converter() {
            @Override
            public Object convertForward(Object value) {
                String title= (String)value;
                if ( title.contains("%{CONTEXT}" ) ) {
                    QDataSet context;
                    String contextStr="";
                    if ( plotElement!=null && plotElement.getController()!=null ) {
                        QDataSet ds= plotElement.getController().getDataSet();
                        if ( ds!=null ) {
                            contextStr= DataSetUtil.contextAsString(ds);
                            if ( !contextStr.equals("") ) {
                                String[] ss= contextStr.split("=");
                                if ( ss.length==2 ) {
                                    contextStr= ss[1]; // shorten if it is of the form A=B to just B
                                }
                            }
                        }
                    }
                    title= title.replaceAll("%\\{CONTEXT\\}", contextStr );
                }
                return title;
            }

            @Override
            public Object convertReverse(Object value) {
                String title= (String)value;
                String ptitle=  axis.getLabel();
                if (ptitle.contains("%{CONTEXT}") ) {
                    String[] ss= ptitle.split("%\\{CONTEXT\\}",-2);
                    if ( title.startsWith(ss[0]) && title.endsWith(ss[1]) ) {
                        return ptitle;
                    }
                }
                return title;
            }
        };
    }

    private synchronized void bindTo(DasPlot p) {
        ApplicationController ac= dom.controller;
        ac.bind( this.plot, Plot.PROP_TITLE, p, DasPlot.PROP_TITLE, contextConverter ); // %{CONTEXT} indicates the DataSet CONTEXT property, not the control.
        ac.bind( this.plot, Plot.PROP_CONTEXT, p, DasPlot.PROP_CONTEXT );
        ac.bind( this.plot, Plot.PROP_ISOTROPIC, p, DasPlot.PROP_ISOTROPIC );
        ac.bind( this.plot, Plot.PROP_DISPLAYTITLE, p, DasPlot.PROP_DISPLAYTITLE );
        ac.bind( this.plot, Plot.PROP_DISPLAYLEGEND, p, DasPlot.PROP_DISPLAYLEGEND );
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
        this.expertMenuItems= items;
    }

    public JMenuItem[] getExpertMenuItems() {
        return this.expertMenuItems;
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
