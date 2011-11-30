/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author jbf
 */
public class ApplicationControllerSupport {

    ApplicationController controller;

    ApplicationControllerSupport(ApplicationController controller) {
        this.controller = controller;
    }

//    /**
//     * remove unused Dsfs, recursing if vap+internal is not used.
//     * this is not used, because other code handles this.
//     * see also DomUtil.dataSourceUsages, which uses controllers.
//     * @return
//     */
//    void cleanOrphanedDsfs( ) {
//        Application dom= controller.application;
//        Map<String,Integer> useCount= new HashMap();
//        for ( PlotElement pe: dom.plotElements ) {
//            String s= pe.getDataSourceFilterId();
//            Integer i= useCount.get(s);
//            if ( i==null ) i= 1; else i=i+1;
//            useCount.put( s,i );
//        }
//        for ( DataSourceFilter dsf: dom.dataSourceFilters ) {
//            String uri= dsf.getUri();
//            if ( uri==null ) uri=""; //(Artifact 3373605)
//            if ( uri.startsWith("vap+internal:") ) {
//                String[] ids= uri.substring(13).split(",");
//                for ( String s: ids ) {
//                    Integer i= useCount.get(s);
//                    if ( i==null ) i= 1; else i=i+1;
//                    useCount.put( s,i );
//                }
//            }
//        }
//        boolean recurse= false;
//        for ( int i=dom.dataSourceFilters.size()-1; i>=0; i-- ) {
//            DataSourceFilter dsf= dom.dataSourceFilters.get(i);
//            String s= dsf.id;
//            Integer c= useCount.get(s);
//            if ( c==null ) {
//                if ( dsf.getUri().startsWith("vap+internal:") ) {
//                    recurse= true;
//                }
//                controller.deleteDataSourceFilter(dsf);
//            }
//        }
//        if ( recurse ) { // recurse in case any are free now.
//            cleanOrphanedDsfs();
//        }
//
//    }

    void plot( Plot plot, PlotElement panel, String primaryUri) {
        if ( panel==null ) panel = controller.addPlotElement(plot, null ); // timeseriesbrowse
        panel.getController().getDataSourceFilter().setUri(""); // this has the side effect of removing parents
        panel.getController().getDataSourceFilter().setUri(primaryUri);
    }

    void plot( Plot plot, PlotElement panel, String secondaryUri, String primaryUri) {
        DataSourceFilter dsf1 = controller.addDataSourceFilter();
        DataSourceFilter dsf2 = controller.addDataSourceFilter();
        if ( panel==null ) panel = controller.addPlotElement(plot, null ); // timeseriesbrowse
        panel.getController().getDataSourceFilter().setUri("");
        panel.getController().getDataSourceFilter().setUri("vap+internal:" + dsf1.getId() + "," + dsf2.getId());
        dsf1.setUri(secondaryUri);
        dsf2.setUri(primaryUri);
    }

    void plot( Plot plot, PlotElement panel, String secondaryUri, String teriaryUri, String primaryUri) {
        DataSourceFilter dsf1 = controller.addDataSourceFilter();
        DataSourceFilter dsf2 = controller.addDataSourceFilter();
        DataSourceFilter dsf3 = controller.addDataSourceFilter();
        if (panel==null) panel = controller.addPlotElement(plot, null ); // timeseriesbrowse
        panel.getController().getDataSourceFilter().setUri("");
        panel.getController().getDataSourceFilter().setUri("vap+internal:" +  dsf1.getId() + "," + dsf2.getId()+","+dsf3.getId() );
        dsf1.setUri(secondaryUri);
        dsf2.setUri(teriaryUri);
        dsf3.setUri(primaryUri);
    }

    public PlotElement addScatter(String suri1, String suri2) {
        DataSourceFilter dsf1 = controller.addDataSourceFilter();
        DataSourceFilter dsf2 = controller.addDataSourceFilter();
        PlotElement panel = controller.addPlotElement(controller.getPlot(), dsf1); // timeseriesbrowse
        panel.setDataSourceFilterId(dsf1.getId() + "," + dsf2.getId());
        dsf1.setUri(suri1);
        dsf2.setUri(suri2);
        return panel;
    }
}
