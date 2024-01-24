
package org.autoplot.dom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.das2.util.LoggerManager;

/**
 * Support methods for synchronizing two Application trees with different
 * geometries.
 * @author jbf on Feb 3, 2009
 */
public class ApplicationControllerSyncSupport {
    ApplicationController controller;
    Application application;
    private static final Logger logger= LoggerManager.getLogger( "autoplot.dom");
    
    ApplicationControllerSyncSupport( ApplicationController controller ) {
        this.controller= controller;
        this.application= controller.application;
    }

    protected void syncToCanvases( Canvas[] canvases, Map<String,String> layoutIds ) {
        if ( canvases.length!=application.canvases.size() ) throw new IllegalArgumentException("not implemented");
        //TODO: multiple canvases not supported
        for (int i = 0; i < canvases.length; i++) {
            application.canvases.get(i).controller.syncTo(canvases[i],new ArrayList<String>(),layoutIds);
        }
    }

    protected void syncToPlotElements(PlotElement[] elements, Map<String, String> nameMap) {
        while (application.plotElements.size() < elements.length) {
            int i = application.plotElements.size();
            String idd = elements[i].getPlotId();
            Plot p = null;
            for (int j = 0; j < application.getPlots().length; j++) {
                if (application.getPlots(j).getId().equals(idd)) {
                    p = application.getPlots(j);
                }
            }
            DataSourceFilter dsf= null;
            for (int j = 0; j < application.getDataSourceFilters().length; j++) {
                if ( application.getDataSourceFilters(j).getId().equals( elements[i].getDataSourceFilterId() ) ) {
                    dsf = application.getDataSourceFilters(j);
                }
            }
            controller.addPlotElement(p, dsf);
        }
        while (application.plotElements.size() > elements.length && elements.length>0 ) { // application cannot have zero plots.  Test elements.length, otherwise code would hang here.
            controller.deletePlotElement(application.plotElements.get(application.plotElements.size() - 1));
        }
        for (int i = 0; i < elements.length; i++) {
            //application.plotElements.get(i).getStyle().syncTo(plotElements[i].getStyle());
            application.plotElements.get(i).syncTo(elements[i], 
					Arrays.asList(PlotElement.PROP_PLOTID, PlotElement.PROP_DATASOURCEFILTERID, 
							PlotElement.PROP_RENDERTYPE, PlotElement.PROP_STYLE, PlotElement.PROP_RENDERCONTROL ) );
            application.plotElements.get(i).setPlotId(nameMap.get(elements[i].getPlotId())); //bug 2992903
            application.plotElements.get(i).setRenderType(elements[i].getRenderType()); // create das2 peers after setting the plotid.
            application.plotElements.get(i).setAutoRenderType(elements[i].isAutoRenderType()); // we still might want to set this automatically.
            application.plotElements.get(i).getController().maybeCreateDasPeer();
            application.plotElements.get(i).getController().setResetRanges(false);
            application.plotElements.get(i).getController().setDsfReset(false);
            application.plotElements.get(i).setRenderControl( elements[i].getRenderControl() ); // OrbitPlot relies completely on control.
            application.plotElements.get(i).getStyle().syncTo(elements[i].getStyle());
            //application.plotElements.get(i).getController().resetRenderType( plotElements[i].getRenderType() );
            application.plotElements.get(i).setDataSourceFilterId(nameMap.get(elements[i].getDataSourceFilterId()));
            application.plotElements.get(i).getController().setResetPlotElement(false);
        }
    }

    protected void syncToPlots( Plot[] plots, Map<String,String> nameMap ) {
        List<Diff> diffs= DomUtil.getArrayDiffs( "plots", plots, application.getPlots() );
        for ( Diff d: diffs ) {
            if ( d instanceof ArrayNodeDiff ) {
                ArrayNodeDiff and= (ArrayNodeDiff)d;
                if ( and.getAction()==ArrayNodeDiff.Action.Delete ) {
                    // disconnect from das2 peer
                    Plot domPlot= (Plot) and.getNode();
                    List<PlotElement> eles= controller.getPlotElementsFor(domPlot);
                    if ( domPlot.controller!=null ) {
                        domPlot.controller.deleteDasPeer( );
                    }
                    controller.unbind(domPlot);
                    controller.unbind(domPlot.getXaxis());
                    controller.unbind(domPlot.getYaxis());
                    controller.unbind(domPlot.getZaxis());
                    for ( PlotElement pe: eles ) {
                        domPlot.controller.removePlotElement(pe);
                        pe.plotId="";
                        pe.getController().renderer= null;
                        pe.getController().setResetPlotElement(true);
                    }
                }
            }
            // TODO: this bypasses the child nodes' sync to method.
            if ( !(d instanceof PropertyChangeDiff ) ) {
                d.doDiff(application);
            }
        }

        for ( int i=0; i<application.getPlots().length; i++ ) {
            application.getPlots(i).syncTo(plots[i]);
        }

        for ( Plot p: application.getPlots() ) {
            if ( p.controller==null ) {
                Row row;
                if ( p.getRowId().equals("") ) {
                    row= application.controller.getCanvas().marginRow;
                } else {
                    row= (Row) DomUtil.getElementById( application, p.getRowId() );
                    if ( row==null ) row= application.controller.getCanvas().marginRow;
                }
                Column col;
                if ( p.getColumnId().equals("") ) {
                    col= application.controller.getCanvas().marginColumn;
                } else {
                    col=  (Column) DomUtil.getElementById( application, p.getColumnId() );
                    if ( col==null ) col= application.controller.getCanvas().marginColumn;
                }
                logger.log( Level.FINE, "adding controller for new node {0}", p);

                final PlotController pc= new PlotController( application, p );
                final Row frow= row;
                final Column fcol= col;
                pc.createDasPeer( frow.controller.getCanvas(), frow, fcol );
                if ( SwingUtilities.isEventDispatchThread() ) {
                    logger.finer("sync called on event thread");
                }
            }
            nameMap.put( p.getId(), p.getId() );  //DANGER--this is intentionally the same.
        }
    }

    protected void syncConnectors( Connector[] connectors ) {
        List<Connector> addConnectors= new ArrayList<>();
        List<Connector> deleteConnectors= new ArrayList<>();

        List<Connector> thisConnectors= Arrays.asList(application.getConnectors());
        List<Connector> thatConnectors= Arrays.asList(connectors);

        for ( Connector c: thatConnectors ) {
            if ( !thisConnectors.contains(c) ) addConnectors.add(c);
        }

        for ( Connector c: application.connectors ) {
            if ( !thatConnectors.contains(c) ) deleteConnectors.add(c);
        }

        for ( Connector c:addConnectors ) {
            Plot plotA= (Plot)DomUtil.getElementById(application, c.plotA );
            Plot plotB= (Plot)DomUtil.getElementById(application, c.plotB) ;
            controller.addConnector( plotA, plotB );
        }

        for ( Connector c:deleteConnectors ) {
            controller.deleteConnector( c );
        }
        
        for ( int i=0; i<connectors.length; i++ ) {
            application.connectors.get(i).syncTo(connectors[i]);
        }

    }

    protected void syncAnnotations(Annotation[] annotations) {

        while ( application.annotations.size() < annotations.length) {
            controller.addAnnotation( new Annotation() );
        }
        while (application.annotations.size() > annotations.length) {
            controller.deleteAnnotation( application.annotations.get(application.annotations.size()-1) );
        }
        
        for ( int i=0; i<annotations.length; i++ ) {
            application.annotations.get(i).syncTo(annotations[i]);
        }
    }
    

    protected void syncBindings( BindingModel[] bindings ,Map<String,String> idMap) {
        List<Diff> diffs= DomUtil.getArrayDiffs( "bindings", bindings, application.getBindings() );
        for ( Diff d: diffs ) {
            if ( d instanceof ArrayNodeDiff ) {
                ArrayNodeDiff and= (ArrayNodeDiff)d;
                if ( and.getAction()==ArrayNodeDiff.Action.Delete ) {
                    // disconnect from das2 peer
                    BindingModel domBinding= (BindingModel) and.getNode();
                    controller.removeBinding(domBinding); //there was a nasty little bug where this would reset app.timerange.
                } if ( and.getAction()==ArrayNodeDiff.Action.Insert ) {
                    BindingModel c= (BindingModel) and.getNode();
                    DomNode src= DomUtil.getElementById(application,idMap.get(c.srcId));
                    DomNode dst= DomUtil.getElementById(application,idMap.get(c.dstId));
                    if ( src==null || dst==null ) {
                        logger.info("src or dst was null");
                    } else {
                        try {
                            controller.bind( src, c.srcProperty, dst, c.dstProperty  );
                        } catch ( IllegalArgumentException ex ) {
                            logger.log(Level.INFO, "unable to bind property: {0}", ex);
                        }
                    }
                }
            } else {
                d.doDiff(application);
            }
        }
    }
    
    protected void syncBindings( BindingModel[] bindings ) {
        syncBindings(bindings,new HashMap<String, String>());
    }

    protected void syncToDataSourceFilters(DataSourceFilter[] dataSourceFilters, Map<String, String> nameMap) {
        while (application.dataSourceFilters.size() < dataSourceFilters.length) {
            controller.addDataSourceFilter();
        }
        while (application.dataSourceFilters.size() > dataSourceFilters.length) {
            int n= application.dataSourceFilters.size();
            DataSourceFilter dsf = application.dataSourceFilters.get(n - 1);
            List<PlotElement> elements = controller.getPlotElementsFor(dsf);
            for (PlotElement element : elements) {
                element.setDataSourceFilterId(""); // make it an orphan -- it should get deleted
            }
            if ( application.dataSourceFilters.size()==n ) { // TODO: check where this is not deleted. hang observed 2015-12-03 because it wasn't removed after paste plot.
                DataSourceFilter[] dsfs= application.getDataSourceFilters(); // paste plot, then use undo to revert to the old state.
                application.setDataSourceFilters(Arrays.copyOf(dsfs,n-1));
            }
        }
        for (int i = 0; i < dataSourceFilters.length; i++) {
            application.dataSourceFilters.get(i).syncTo(dataSourceFilters[i]);
            nameMap.put(dataSourceFilters[i].getId(), application.dataSourceFilters.get(i).getId()); // Note this is always data_i->data_i.  It must be like this or a bug will occur.
        }
    }

}
