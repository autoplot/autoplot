/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.TooManyListenersException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
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
import org.virbo.autoplot.RenderType;
import org.virbo.autoplot.RenderTypeUtil;
import org.virbo.autoplot.bookmarks.Bookmark;
import org.virbo.autoplot.bookmarks.BookmarksException;
import org.virbo.autoplot.dom.ChangesSupport.DomLock;
import org.virbo.autoplot.util.DateTimeDatumFormatter;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.xml.sax.SAXException;

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

    private static final Logger logger= Logger.getLogger( PlotController.class.getName() );

    public PlotController(Application dom, Plot domPlot, DasPlot dasPlot, DasColorBar colorbar) {
        this( dom, domPlot );
        this.dasPlot = dasPlot;
        this.dasColorBar = colorbar;
        dasPlot.addPropertyChangeListener(listener);
        dasPlot.getXAxis().addPropertyChangeListener(listener);
        dasPlot.getYAxis().addPropertyChangeListener(listener);
    }

    public PlotController( Application dom, Plot plot ) {
        super( plot );
        this.dom = dom;
        this.plot = plot;
        this.plot.addPropertyChangeListener( Plot.PROP_ISOTROPIC, new PropertyChangeListener() {
            public void propertyChange( PropertyChangeEvent e ) {
                if ( PlotController.this.plot.isIsotropic() ) checkIsotropic(null);
            }
        });
        this.plot.addPropertyChangeListener( Plot.PROP_TITLE, labelListener );
        this.plot.addPropertyChangeListener( Plot.PROP_TICKS_URI, ticksURIListener );
        dom.options.addPropertyChangeListener( Options.PROP_DAY_OF_YEAR, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                final DasAxis update= PlotController.this.plot.getXaxis().controller.dasAxis;
                updateAxisFormatter(update);
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

        ac.bind(application.getOptions(), Options.PROP_OVERRENDERING, dasPlot1, "overSize");

        ac.bind(this.plot, Plot.PROP_VISIBLE, dasPlot1, "visible" );
        ac.bind(this.plot, Plot.PROP_COLORTABLE, colorbar, "type" );
        
        dasPlot1.addPropertyChangeListener(listener);
        dasPlot1.getXAxis().addPropertyChangeListener(listener);
        dasPlot1.getYAxis().addPropertyChangeListener(listener);
        this.plot.addPropertyChangeListener( Plot.PROP_ISOTROPIC, new PropertyChangeListener() {
            public void propertyChange( PropertyChangeEvent e ) {
                if ( plot.isIsotropic() ) checkIsotropic(null);
            }
        });

        if ( plot.getTicksURI().length()>0 ) { //TODO: understand this better.  We don't have to set titles, right?  Maybe it's because implementation is handled here instead of in das2.
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

        DropTarget dropTarget = new DropTarget();
        try {
            dropTarget.addDropTargetListener( createDropTargetListener(dasPlot) );
        } catch (TooManyListenersException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        dasPlot.setDropTarget(dropTarget);

    }


    private DropTargetListener createDropTargetListener( final DasPlot p ) {
        return new DropTargetListener() {

            public void dragEnter(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported( org.virbo.autoplot.bookmarks.BookmarksManagerTransferrable.BOOKMARK_FLAVOR)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);

                } else if (dtde.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                }
            }

            public void dragOver(DropTargetDragEvent dtde) {
            }

            public void dropActionChanged(DropTargetDragEvent dtde) {
            }

            public void dragExit(DropTargetEvent dte) {
            }

            public void drop(DropTargetDropEvent dtde) {
                try {
                    Bookmark item = null;
                    List<Bookmark> items = null;
                    if (dtde.isDataFlavorSupported( org.virbo.autoplot.bookmarks.BookmarksManagerTransferrable.BOOKMARK_FLAVOR)) {
                        item = (Bookmark) dtde.getTransferable().getTransferData( org.virbo.autoplot.bookmarks.BookmarksManagerTransferrable.BOOKMARK_FLAVOR);
                    } else if (dtde.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        dtde.acceptDrop( DnDConstants.ACTION_COPY );
                        String data = (String) dtde.getTransferable().getTransferData(DataFlavor.stringFlavor);
                        if (data.length() > 19 && data.startsWith("<bookmark-list")) {
                            items = Bookmark.parseBookmarks(data);
                        } else if (data.length() > 14 && data.startsWith("<bookmark")) {
                            item = Bookmark.parseBookmark(data);
                        } else {
                            item = new Bookmark.Item(data);
                        }
                    }

                    final String uri= items!=null ? null : ((Bookmark.Item)item).getUri();

                    if ( uri==null ) {
                        dom.getController().model.showMessage( "couldn't find URI in drop target", "no URI", JOptionPane.WARNING_MESSAGE );
                    } else {
                        final Plot dp= dom.getController().getPlotFor(p);
                        final List<PlotElement> pe= dom.getController().getPlotElementsFor(plot);
                        if ( pe.size()==0 ) {
                            dom.getController().model.showMessage( "no plot elements here", "no plot elements", JOptionPane.WARNING_MESSAGE );
                        }
                        final DataSourceFilter dsf= dom.getController().getDataSourceFilterFor(pe.get(0));
                        dsf.setUri(uri);
                    }

                } catch (UnsupportedFlavorException ex) {
                    ex.printStackTrace();

                } catch (IOException ex) {
                    ex.printStackTrace();

                } catch (SAXException ex) {
                    ex.printStackTrace();

                } catch (BookmarksException ex) {
                    ex.printStackTrace();
                }
            }
        };
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
                if (plot.isIsotropic()) {
                    checkIsotropic(axis);
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
     * set the zoom so that all of the plotElements' data is visible.  Thie means finding
     * the "union" of each plotElements' plotDefault ranges.  If any plotElement's default log
     * is false, then the new setting will be false.
     */
    public void resetZoom(boolean x, boolean y, boolean z) {
        List<PlotElement> elements = dom.controller.getPlotElementsFor(plot);
        if ( elements.size()==0 ) return;
        Plot newSettings = null;

        boolean haveTsb= false;

        for (PlotElement p : elements) {
            Plot plot1 = p.getPlotDefaults();
            if ( p.isActive() && plot1.getXaxis().isAutoRange() ) {  // we use autoRange to indicate these are real settings, not just the defaults.
                if (newSettings == null) {
                    newSettings = (Plot) plot1.copy();
                } else {
                    try {
                        newSettings.xaxis.range = DatumRangeUtil.union(newSettings.xaxis.range, plot1.getXaxis().getRange());
                        newSettings.xaxis.log = newSettings.xaxis.log & plot1.xaxis.log;
                        newSettings.yaxis.range = DatumRangeUtil.union(newSettings.yaxis.range, plot1.getYaxis().getRange());
                        newSettings.yaxis.log = newSettings.yaxis.log & plot1.yaxis.log;
                        newSettings.zaxis.range = DatumRangeUtil.union(newSettings.zaxis.range, plot1.getZaxis().getRange());
                        newSettings.zaxis.log = newSettings.zaxis.log & plot1.zaxis.log;
                    } catch ( InconvertibleUnitsException ex ) {
                        logger.info("plot elements on the same plot have inconsistent units");
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
            plot.getXaxis().setLog( newSettings.getXaxis().isLog() );
            plot.getXaxis().setRange(newSettings.getXaxis().getRange());
            plot.getXaxis().setAutoRange(true);

            if ( haveTsb==true ) {
                plot.getXaxis().getController().dasAxis.setScanRange( null );
            } else {
                plot.getXaxis().getController().dasAxis.setScanRange( plot.getXaxis().getRange() );
            }

        }
        if ( y ) {
            logCheck(newSettings.getYaxis());
            plot.getYaxis().setLog( newSettings.getYaxis().isLog() );
            plot.getYaxis().setRange(newSettings.getYaxis().getRange());
            plot.getYaxis().setAutoRange(true);
        }
        if ( z ) {
            logCheck(newSettings.getZaxis());
            plot.getZaxis().setLog( newSettings.getZaxis().isLog() );
            plot.getZaxis().setRange(newSettings.getZaxis().getRange());
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
            QDataSet pds= plotElement.getController().getDataSet();
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


    synchronized void addPlotElement(PlotElement p,boolean reset) {
        Renderer rr= p.controller.getRenderer();

        if ( rr instanceof SpectrogramRenderer ) {
            ((SpectrogramRenderer)rr).setColorBar( getDasColorBar() );
        } else if ( rr instanceof SeriesRenderer ) {
            ((SeriesRenderer)rr).setColorBar( getDasColorBar() );
        }

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
        doPlotElementDefaultsChange(p);
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
        pdListen.remove(p);
        if ( !p.getPlotId().equals("") ) p.setPlotId("");
        checkRenderType();
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

        if ( DomUtil.oneFamily( dom.getController().getPlotElementsFor(plot) ) ) {
            PlotElement p= dom.getController().getPlotElementsFor(plot).get(0);
            if ( !p.getParent().equals("") && p.getController().getParentPlotElement()!=null ) {
                p = p.getController().getParentPlotElement();
            }
            if ( !p.getParent().equals("") && p.getController().getParentPlotElement()==null ) {
                logger.log(Level.WARNING, "reference to non-existent parent in {0}", p);
            }
            if ( this.plotElement!=null ) {
                this.plotElement.getController().removePropertyChangeListener( PlotElementController.PROP_DATASET, plotElementDataSetListener );
            }
            this.plotElement= p;
            this.plotElement.getController().addPropertyChangeListener( PlotElementController.PROP_DATASET, plotElementDataSetListener );
            if ( pele==null || pele.getPlotDefaults().getXaxis().isAutoRange()!=false ) { //TODO: why is this?  /home/jbf/ct/hudson/vap/geo_1.vap wants it
                if ( plot.isAutoLabel() ) plot.setTitle( p.getPlotDefaults().getTitle() );
                if ( plot.getXaxis().isAutoLabel() ) plot.getXaxis().setLabel( p.getPlotDefaults().getXaxis().getLabel() );
                if ( plot.getYaxis().isAutoLabel() ) plot.getYaxis().setLabel( p.getPlotDefaults().getYaxis().getLabel() );
                if ( plot.getZaxis().isAutoLabel() ) plot.getZaxis().setLabel( p.getPlotDefaults().getZaxis().getLabel() );
                if ( plot.getXaxis().isAutoRange() && plot.getYaxis().isAutoRange() ) {
                    plot.setIsotropic( p.getPlotDefaults().isIsotropic() );
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
            plot.getXaxis().setRange( dr );
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
            plot.getYaxis().setRange( dr );
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
            plot.getZaxis().setRange( dr );
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
            //if ( !CanvasUtil.getMostBottomPlot(dom.getController().getCanvasFor(plot))==plot ) {
            //    plot.getXaxis().setDrawTickLabels(false);
            //} //TODO: could disable tick label drawing automatically.

        } else if ( bm!=null && !shouldBindX ) {
            logger.log(Level.FINER, "remove binding: {0}", bm);
            dom.getController().deleteBinding(bm);
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
}
