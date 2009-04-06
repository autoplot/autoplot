/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import org.das2.components.propertyeditor.PropertyEditor;
import org.das2.event.DasMouseInputAdapter;
import org.das2.event.MouseModule;
import org.das2.event.PointSlopeDragRenderer;
import org.das2.graph.DasAxis;
import org.das2.graph.DasPlot;
import org.virbo.autoplot.ApplicationModel.RenderType;
import org.virbo.autoplot.GuiSupport;
import org.virbo.autoplot.layout.LayoutConstants;

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

    /**
     * support for binding two plot axes.
     * @param dstPlot
     * @param plot
     * @param axis
     * @throws java.lang.IllegalArgumentException
     */
    private void bindToPlotPeer(Plot dstPlot, Plot plot, Axis axis) throws IllegalArgumentException {
        Axis targetAxis;
        if (plot.getXaxis() == axis) {
            targetAxis = dstPlot.getXaxis();
        } else if (plot.getYaxis() == axis) {
            targetAxis = dstPlot.getYaxis();
        } else if (plot.getZaxis() == axis) {
            targetAxis = dstPlot.getZaxis();
        } else {
            throw new IllegalArgumentException("this axis and plot don't go together");
        }
        controller.bind(targetAxis, Axis.PROP_RANGE, axis, Axis.PROP_RANGE);
        controller.bind(targetAxis, Axis.PROP_LOG, axis, Axis.PROP_LOG);
    }

    protected void addAxisContextMenuItems(final DasPlot dasPlot, final PlotController plotController, final Plot plot, final Axis axis) {

        final DasAxis dasAxis = axis.getController().getDasAxis();
        final DasMouseInputAdapter mouseAdapter = dasAxis.getDasMouseInputAdapter();

        mouseAdapter.removeMenuItem("Properties");

        JMenuItem item;

        mouseAdapter.addMenuItem(new JMenuItem(new AbstractAction("Axis Properties") {

            public void actionPerformed(ActionEvent e) {
                PropertyEditor pp = new PropertyEditor(axis);
                pp.showDialog(dasAxis.getCanvas());
            }
        }));

        mouseAdapter.addMenuItem(new JSeparator());

        if (axis == plot.getXaxis()) {
            JMenu addPlotMenu = new JMenu("Add Plot");
            mouseAdapter.addMenuItem(addPlotMenu);

            item = new JMenuItem(new AbstractAction("Bound Plot Below") {

                public void actionPerformed(ActionEvent e) {
                    controller.copyPlot(plot, true, false, true);
                }
            });
            item.setToolTipText("add a new plot below.  The plot's x axis will be bound to this plot's x axis");
            addPlotMenu.add(item);

        }

        item = new JMenuItem(new AbstractAction("Remove Bindings") {

            public void actionPerformed(ActionEvent e) {
                controller.unbind(axis);  // TODO: check for application timerange
            }
        });
        item.setToolTipText("remove any plot and panel property bindings");
        mouseAdapter.addMenuItem(item);


        JMenu bindingMenu = new JMenu("Add Binding");

        mouseAdapter.addMenuItem(bindingMenu);

        if (axis == plot.getXaxis()) {
            item = new JMenuItem(new AbstractAction("Bind to Application Time Range") {

                public void actionPerformed(ActionEvent e) {
                    controller.bind(controller.application, Application.PROP_TIMERANGE, axis, Axis.PROP_RANGE);
                }
            });
            bindingMenu.add(item);
        }


        item = new JMenuItem(new AbstractAction("Bind to Plot Above") {

            public void actionPerformed(ActionEvent e) {
                Plot dstPlot = controller.getPlotAbove(plot);
                if (dstPlot == null) {
                    controller.setStatus("warning: no plot above");
                } else {
                    bindToPlotPeer(dstPlot, plot, axis);
                }
            }
        });
        bindingMenu.add(item);
        item = new JMenuItem(new AbstractAction("Bind to Plot Below") {

            public void actionPerformed(ActionEvent e) {
                Plot dstPlot = controller.getPlotBelow(plot);
                if (dstPlot == null) {
                    controller.setStatus("warning: no plot below");
                } else {
                    bindToPlotPeer(dstPlot, plot, axis);
                }
            }
        });
        bindingMenu.add(item);

        JMenu connectorMenu = new JMenu("Add Connector");

        mouseAdapter.addMenuItem(connectorMenu);

        item = new JMenuItem(new AbstractAction("Connector to Plot Above") {

            public void actionPerformed(ActionEvent e) {
                Plot dstPlot = controller.getPlotAbove(plot);
                if (dstPlot == null) {
                    controller.setStatus("warning: no plot above");
                } else {
                    controller.addConnector(dstPlot, plot);
                }
            }
        });
        connectorMenu.add(item);
    }

    void addPlotContextMenuItems(final DasPlot plot, final PlotController plotController, final Plot domPlot) {
        plot.getDasMouseInputAdapter().addMouseModule(new MouseModule(plot, new PointSlopeDragRenderer(plot, plot.getXAxis(), plot.getYAxis()), "Slope"));

        plot.getDasMouseInputAdapter().removeMenuItem("Dump Data");
        plot.getDasMouseInputAdapter().removeMenuItem("Properties");

        JMenuItem item;

        plot.getDasMouseInputAdapter().addMenuItem(new JMenuItem(new AbstractAction("Plot Properties") {

            public void actionPerformed(ActionEvent e) {
                PropertyEditor pp = new PropertyEditor(domPlot);
                pp.showDialog(plot.getCanvas());
            }
        }));

        plot.getDasMouseInputAdapter().addMenuItem(new JMenuItem(new AbstractAction("Panel Properties") {

            public void actionPerformed(ActionEvent e) {
                Panel p = controller.getPanel();
                PropertyEditor pp = new PropertyEditor(p);
                pp.showDialog(plot.getCanvas());
            }
        }));

        plot.getDasMouseInputAdapter().addMenuItem(new JSeparator());

        JMenu addPlotMenu = new JMenu("Add Plot");
        plot.getDasMouseInputAdapter().addMenuItem(addPlotMenu);

        item = new JMenuItem(new AbstractAction("Copy Panels") {

            public void actionPerformed(ActionEvent e) {
                controller.copyPlotAndPanels(domPlot, null, true, false);
            }
        });
        item.setToolTipText("make a new plot, and copy the panels into it.  New plot is bound by the x axis.");
        addPlotMenu.add(item);

        item = new JMenuItem(new AbstractAction("Context Overview") {

            public void actionPerformed(ActionEvent e) {
                Plot that = controller.copyPlotAndPanels(domPlot, null, false, false);
                controller.bind(domPlot.zaxis, Axis.PROP_RANGE, that.zaxis, Axis.PROP_RANGE);
                controller.bind(domPlot.zaxis, Axis.PROP_LOG, that.zaxis, Axis.PROP_LOG);
                controller.bind(domPlot.zaxis, Axis.PROP_LABEL, that.zaxis, Axis.PROP_LABEL);
                controller.addConnector(domPlot, that);
            }
        });

        item.setToolTipText("make a new plot, and copy the panels into it.  The plot is not bound,\n" +
                "and a connector is drawn between the two.  The panel uris are bound as well.");
        addPlotMenu.add(item);

        JMenu editPlotMenu = new JMenu("Edit Plot");
        plot.getDasMouseInputAdapter().addMenuItem(editPlotMenu);

        controller.fillEditPlotMenu(editPlotMenu, domPlot);

        JMenu panelMenu = new JMenu("Edit Panel");

        plot.getDasMouseInputAdapter().addMenuItem(panelMenu);

        item = new JMenuItem(new AbstractAction("Move to Plot Above") {

            public void actionPerformed(ActionEvent e) {
                Panel panel = controller.getPanel();
                Plot plot = controller.getPlotFor(panel);
                Plot dstPlot = controller.getPlotAbove(plot);
                if (dstPlot == null) {
                    dstPlot = controller.addPlot(LayoutConstants.ABOVE);
                    panel.setPlotId(dstPlot.getId());
                    controller.bind(plot.getXaxis(), Axis.PROP_RANGE, dstPlot.getXaxis(), Axis.PROP_RANGE);
                } else {
                    panel.setPlotId(dstPlot.getId());
                }
            }
        });
        panelMenu.add(item);

        item = new JMenuItem(new AbstractAction("Move to Plot Below") {

            public void actionPerformed(ActionEvent e) {
                Panel panel = controller.getPanel();
                Plot plot = controller.getPlotFor(panel);
                Plot dstPlot = controller.getPlotBelow(plot);
                if (dstPlot == null) {
                    dstPlot = controller.addPlot(LayoutConstants.BELOW);
                    panel.setPlotId(dstPlot.getId());
                    controller.bind(plot.getXaxis(), Axis.PROP_RANGE, dstPlot.getXaxis(), Axis.PROP_RANGE);
                } else {
                    panel.setPlotId(dstPlot.getId());
                }
            }
        });
        panelMenu.add(item);

        item = new JMenuItem(new AbstractAction("Delete Panel") {

            public void actionPerformed(ActionEvent e) {
                Panel panel = controller.getPanel();
                controller.deletePanel(panel);
            }
        });
        panelMenu.add(item);

        plot.getDasMouseInputAdapter().addMenuItem(new JSeparator());

        plot.getDasMouseInputAdapter().addMenuItem(new JMenuItem(new AbstractAction("Reset Zoom") {

            public void actionPerformed(ActionEvent e) {
                plotController.resetZoom();
            }
        }));


        plot.getDasMouseInputAdapter().addMenuItem(GuiSupport.createEZAccessMenu(domPlot));
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
