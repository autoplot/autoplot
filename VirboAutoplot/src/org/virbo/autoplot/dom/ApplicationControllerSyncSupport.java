/*
 * 
 * 
 */

package org.virbo.autoplot.dom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.virbo.autoplot.LogNames;

/**
 * Support methods for synchronizing two Application trees with different
 * geometries.
 * @author jbf on Feb 3, 2009
 */
public class ApplicationControllerSyncSupport {
    ApplicationController controller;
    Application application;
    private static final Logger logger= LoggerManager.getLogger(LogNames.AUTOPLOT_DOM);
    
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
            application.plotElements.get(i).syncTo(elements[i], Arrays.asList(PlotElement.PROP_PLOTID, PlotElement.PROP_DATASOURCEFILTERID, PlotElement.PROP_RENDERTYPE, PlotElement.PROP_STYLE ) );
            application.plotElements.get(i).setPlotId(nameMap.get(elements[i].getPlotId())); //bug 2992903
            application.plotElements.get(i).setRenderType(elements[i].getRenderType()); // create das2 peers after setting the plotid.
            application.plotElements.get(i).getController().maybeCreateDasPeer();
            application.plotElements.get(i).getStyle().syncTo(elements[i].getStyle());
            //application.plotElements.get(i).getController().resetRenderType( plotElements[i].getRenderType() );
            application.plotElements.get(i).setDataSourceFilterId(nameMap.get(elements[i].getDataSourceFilterId()));
            application.plotElements.get(i).getController().setDsfReset(false);
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
                new PlotController( application, p ).createDasPeer( row.controller.getCanvas(), row, col );
            }
            nameMap.put( p.getId(), p.getId() );  //DANGER--this is intentionally the same.
        }
    }

    protected void syncConnectors( Connector[] connectors ) {
        List<Connector> addConnectors= new ArrayList<Connector>();
        List<Connector> deleteConnectors= new ArrayList<Connector>();

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

    }


    protected void syncBindingsNew( BindingModel[] bindings ,Map<String,String> idMap) {
        List<Diff> diffs= DomUtil.getArrayDiffs( "bindings", bindings, application.getBindings() );
        for ( Diff d: diffs ) {
            if ( d instanceof ArrayNodeDiff ) {
                ArrayNodeDiff and= (ArrayNodeDiff)d;
                if ( and.getAction()==ArrayNodeDiff.Action.Delete ) {
                    // disconnect from das2 peer
                    BindingModel domBinding= (BindingModel) and.getNode();
                    controller.deleteBinding(domBinding); //there was a nasty little bug where this would reset app.timerange.
                } if ( and.getAction()==ArrayNodeDiff.Action.Insert ) {
                    BindingModel c= (BindingModel) and.getNode();
                    DomNode src= DomUtil.getElementById(application,idMap.get(c.srcId));
                    DomNode dst= DomUtil.getElementById(application,idMap.get(c.dstId));
                    if ( src==null || dst==null ) {
                        logger.finer("node was null");
                    } else {
                        controller.bind( src, c.srcProperty, dst, c.dstProperty  );
                    }
                }
            } else {
                d.doDiff(application);
            }
        }
    }
    
    protected void syncBindings( BindingModel[] bindings ) {
        syncBindingsNew(bindings,new HashMap<String, String>());
        /*
        List<BindingModel> addBindings= new ArrayList<BindingModel>();
        List<BindingModel> deleteBindings= new ArrayList<BindingModel>();

        List<BindingModel> thisBindings= Arrays.asList(application.getBindings());
        List<BindingModel> thatBindings= Arrays.asList(bindings);

        for ( BindingModel c: thatBindings ) {
            if ( !thisBindings.contains(c) ) addBindings.add(c);
        }

        for ( BindingModel c: application.bindings ) {
            if ( !thatBindings.contains(c) ) deleteBindings.add(c);
        }

        for ( BindingModel c:addBindings ) {
            DomNode src= DomUtil.getElementById(application,c.srcId);
            DomNode dst= DomUtil.getElementById(application,c.dstId);
            if ( src==null || dst==null ) {
                logger.finer("node was null");
            } else {
                controller.bind( src, c.srcProperty, dst, c.dstProperty  );
            }
        }

        for ( BindingModel c:deleteBindings ) {
            DomNode src= DomUtil.getElementById(application,c.srcId);
            DomNode dst= DomUtil.getElementById(application,c.dstId);
            controller.deleteBinding( controller.findBinding( src, c.srcProperty, dst, c.dstProperty  ) );
        }
         */

    }

    protected void syncToDataSourceFilters(DataSourceFilter[] dataSourceFilters, Map<String, String> nameMap) {
        while (application.dataSourceFilters.size() < dataSourceFilters.length) {
            controller.addDataSourceFilter();
        }
        while (application.dataSourceFilters.size() > dataSourceFilters.length) {
            DataSourceFilter dsf = application.dataSourceFilters.get(application.dataSourceFilters.size() - 1);
            List<PlotElement> elements = controller.getPlotElementsFor(dsf);
            for (PlotElement element : elements) {
                element.setDataSourceFilterId(""); // make it an orphan -- it should get deleted
            }
        }
        for (int i = 0; i < dataSourceFilters.length; i++) {
            application.dataSourceFilters.get(i).syncTo(dataSourceFilters[i]);
            nameMap.put(dataSourceFilters[i].getId(), application.dataSourceFilters.get(i).getId()); // Note this is always data_i->data_i.  It must be like this or a bug will occur.
        }
    }

}
