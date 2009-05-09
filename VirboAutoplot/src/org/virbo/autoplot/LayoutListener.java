/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import org.das2.graph.DasAxis;
import org.das2.graph.DasPlot;
import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Logger;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.layout.LayoutUtil;
import org.virbo.autoplot.util.TickleTimer;

/**
 * Listens to changes in axis labels and colorbar visible to trigger relayout.
 * @author jbf
 */
public class LayoutListener implements PropertyChangeListener {

    final ApplicationModel model;
    TickleTimer t;
    static Logger logger = Logger.getLogger("virbo.autoplot.autolayout");
    private static final String PENDING_CHANGE_AUTOLAYOUT = "autolayout";

    public LayoutListener(final ApplicationModel model) {
        this.model = model;
        t = new TickleTimer(100, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                logger.fine("do autolayout");
                ApplicationController applicationController = model.getDocumentModel().getController();
                model.dom.getController().getCanvas().getController().performingChange(LayoutListener.this, PENDING_CHANGE_AUTOLAYOUT);
                model.canvas.performingChange(LayoutListener.this, PENDING_CHANGE_AUTOLAYOUT);
                LayoutUtil.autolayout(applicationController.getDasCanvas(),
                        applicationController.getRow(), applicationController.getColumn());
                model.canvas.changePerformed(LayoutListener.this, PENDING_CHANGE_AUTOLAYOUT);
                model.dom.getController().getCanvas().getController().changePerformed(LayoutListener.this, PENDING_CHANGE_AUTOLAYOUT);

            }
        });
    }

    public void listenTo(DasPlot plot) {
        plot.addPropertyChangeListener(DasPlot.PROP_TITLE, this);
        plot.getXAxis().addPropertyChangeListener(DasAxis.PROP_BOUNDS, this);
        plot.getYAxis().addPropertyChangeListener(DasAxis.PROP_BOUNDS, this);
    }

    public void listenTo(DasAxis colorbar) {
        colorbar.addPropertyChangeListener("visible", this);
        colorbar.addPropertyChangeListener(DasAxis.PROP_BOUNDS, this);
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if (model.dom.getOptions().isAutolayout() && !model.dom.getController().isValueAdjusting()) {
            logger.fine("property change: " + evt.getPropertyName());
            if (evt.getSource() instanceof Component &&
                    ((Component) evt.getSource()).isVisible()) {
                model.canvas.registerPendingChange(this, PENDING_CHANGE_AUTOLAYOUT);
                t.tickle(evt.getPropertyName());
            }
        }
    }
}
