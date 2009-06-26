/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

/**
 *
 * @author jbf
 */
public class ApplicationControllerSupport {

    ApplicationController controller;

    ApplicationControllerSupport(ApplicationController controller) {
        this.controller = controller;
    }

    void plot( Plot plot, Panel panel, String primaryUri) {
        if ( panel==null ) panel = controller.addPanel(plot, null ); // timeseriesbrowse
        panel.getController().getDataSourceFilter().setUri(primaryUri);
    }

    void plot( Plot plot, Panel panel, String secondaryUri, String primaryUri) {
        DataSourceFilter dsf1 = controller.addDataSourceFilter();
        DataSourceFilter dsf2 = controller.addDataSourceFilter();
        if ( panel==null ) panel = controller.addPanel(plot, null ); // timeseriesbrowse
        panel.getController().getDataSourceFilter().setUri("vap+internal:" + dsf1.getId() + "," + dsf2.getId());
        dsf1.setUri(secondaryUri);
        dsf2.setUri(primaryUri);
    }

    void plot( Plot plot, Panel panel, String secondaryUri, String teriaryUri, String primaryUri) {
        DataSourceFilter dsf1 = controller.addDataSourceFilter();
        DataSourceFilter dsf2 = controller.addDataSourceFilter();
        DataSourceFilter dsf3 = controller.addDataSourceFilter();
        if (panel==null) panel = controller.addPanel(plot, null ); // timeseriesbrowse
        panel.getController().getDataSourceFilter().setUri("vap+internal:" +  dsf1.getId() + "," + dsf2.getId()+","+dsf3.getId() );
        dsf1.setUri(secondaryUri);
        dsf2.setUri(teriaryUri);
        dsf3.setUri(primaryUri);
    }

    public Panel addScatter(String suri1, String suri2) {
        DataSourceFilter dsf1 = controller.addDataSourceFilter();
        DataSourceFilter dsf2 = controller.addDataSourceFilter();
        Panel panel = controller.addPanel(controller.getPlot(), dsf1); // timeseriesbrowse
        panel.setDataSourceFilterId(dsf1.getId() + "," + dsf2.getId());
        dsf1.setUri(suri1);
        dsf2.setUri(suri2);
        return panel;
    }
}
