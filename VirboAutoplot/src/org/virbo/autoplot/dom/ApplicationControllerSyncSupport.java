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
import java.util.logging.Logger;

/**
 * Support methods for synchronizing two Application trees with different
 * geometries.
 * @author jbf on Feb 3, 2009
 */
public class ApplicationControllerSyncSupport {
    ApplicationController controller;
    Application application;
    
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

    protected void syncToPlots( Plot[] plots, Map<String,String> nameMap ) {
        List<Diff> diffs= DomUtil.getArrayDiffs( "plots", plots, application.getPlots() );
        for ( Diff d: diffs ) {
            if ( d instanceof ArrayNodeDiff ) {
                ArrayNodeDiff and= (ArrayNodeDiff)d;
                if ( and.getAction()==ArrayNodeDiff.Action.Delete ) {
                    // disconnect from das2 peer
                    Plot domPlot= (Plot) and.getNode();
                    if ( domPlot.controller!=null ) {
                        domPlot.controller.deleteDasPeer( );
                    }
                    controller.unbind(domPlot);
                    controller.unbind(domPlot.getXaxis());
                    controller.unbind(domPlot.getYaxis());
                    controller.unbind(domPlot.getZaxis());
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
                new PlotController( application, p ).createDasPeer( row.controller.getCanvas(), row, col );
            }
            nameMap.put( p.getId(), p.getId() );  //DANGER--this is intentionally the same.
        }
    }

    protected void syncToPlotsAndPanels( Plot[] plots, Panel[] panels, DataSourceFilter[] dataSourceFilters ,Map<String,String> nameMap) {

        syncToPlots(plots,nameMap);

        while (application.dataSourceFilters.size() < dataSourceFilters.length) {
            controller.addDataSourceFilter();
        }
        while (application.dataSourceFilters.size() > dataSourceFilters.length) {
            DataSourceFilter dsf= application.dataSourceFilters.get(application.dataSourceFilters.size() - 1);
            List<Panel> panelss= controller.getPanelsFor(dsf);
            for ( Panel panell:panelss ) {
                panell.setDataSourceFilterId(""); // make it an orphan -- it should get deleted
            }
        }
        for (int i = 0; i < dataSourceFilters.length; i++) {
            application.dataSourceFilters.get(i).syncTo(dataSourceFilters[i]);
            nameMap.put( dataSourceFilters[i].getId(), application.dataSourceFilters.get(i).getId() );
        }

        while (application.panels.size() < panels.length) {
            int i = application.panels.size();
            String idd = panels[i].getPlotId();
            Plot p = null;
            for (int j = 0; j < plots.length; j++) {
                if (plots[j].getId().equals(idd)) p = plots[j];
            }
            controller.addPanel(p,null);
        }
        while (application.panels.size() > panels.length) {
            controller.deletePanel( application.panels.get(application.panels.size() - 1));
        }

        for (int i = 0; i < panels.length; i++) {
            application.panels.get(i).syncTo(panels[i], 
                    Arrays.asList( Panel.PROP_PLOTID, Panel.PROP_DATASOURCEFILTERID, Panel.PROP_RENDERTYPE ) );
            application.panels.get(i).setPlotId( nameMap.get(panels[i].getPlotId() ) );
            application.panels.get(i).setRenderType( panels[i].getRenderType() );
            //application.panels.get(i).getController().resetRenderType( panels[i].getRenderType() );
            application.panels.get(i).setDataSourceFilterId( nameMap.get(panels[i].getDataSourceFilterId()) );
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
                    controller.deleteBinding(domBinding);
                } if ( and.getAction()==ArrayNodeDiff.Action.Insert ) {
                    BindingModel c= (BindingModel) and.getNode();
                    DomNode src= DomUtil.getElementById(application,idMap.get(c.srcId));
                    DomNode dst= DomUtil.getElementById(application,idMap.get(c.dstId));
                    if ( src==null || dst==null ) {
                        Logger.getLogger( ApplicationControllerSupport.class.getName() ).finer("node was null");
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
                Logger.getLogger( ApplicationControllerSupport.class.getName() ).finer("node was null");
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

}
