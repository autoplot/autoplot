/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.util.logging.Level;
import org.das2.graph.DasAxis;
import org.das2.graph.DasPlot;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Logger;
import javax.swing.Timer;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.layout.LayoutUtil;

/**
 * Listens to changes in axis labels and colorbar visible to trigger relayout.
 * @author jbf
 */
public class LayoutListener implements PropertyChangeListener {

    ApplicationModel model;  
    Timer t;
    private static final Logger logger = Logger.getLogger("autoplot");
    public static final String PENDING_CHANGE_AUTOLAYOUT= "autolayout";

    public LayoutListener(ApplicationModel model) {
        this.model = model;
    }

    public void listenTo( DasPlot plot ) {
        plot.addPropertyChangeListener(DasPlot.PROP_TITLE, this);
        plot.getXAxis().addPropertyChangeListener(DasAxis.PROP_BOUNDS, this);
        plot.getYAxis().addPropertyChangeListener(DasAxis.PROP_BOUNDS, this);
        plot.addPropertyChangeListener(DasPlot.PROP_LEGENDPOSITION,this);
    }
    
    public void listenTo( DasAxis colorbar ) {
        colorbar.addPropertyChangeListener("visible", this);
        colorbar.addPropertyChangeListener(DasAxis.PROP_BOUNDS, this );
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if (model.dom.getOptions().isAutolayout() ) {
            logger.log(Level.FINE, "property change: {0}", evt.getPropertyName());
            if (evt.getSource() instanceof Component &&
                    ((Component) evt.getSource()).isVisible()) {
                if (t == null) {
                    logger.fine("create timer ");
                    t = new Timer(100, new ActionListener() {
                        public synchronized void actionPerformed(ActionEvent e) {
                            if ( model.dom.getOptions().isAutolayout() ) { //bug 3034795
                                logger.fine("do autolayout");
                                ApplicationController applicationController= model.getDocumentModel().getController();
                                model.dom.getController().getCanvas().getController().performingChange(LayoutListener.this,PENDING_CHANGE_AUTOLAYOUT);
                                model.canvas.performingChange(LayoutListener.this, PENDING_CHANGE_AUTOLAYOUT);
                                LayoutUtil.autolayout( applicationController.getDasCanvas(),
                                        applicationController.getRow(), applicationController.getColumn() );
                                model.canvas.changePerformed(LayoutListener.this, PENDING_CHANGE_AUTOLAYOUT);
                                model.dom.getController().getCanvas().getController().changePerformed(LayoutListener.this,PENDING_CHANGE_AUTOLAYOUT);
                            } else {
                                //TODO: maybe we want a changeCancelled.
                                model.canvas.performingChange(LayoutListener.this, PENDING_CHANGE_AUTOLAYOUT);
                                model.canvas.changePerformed(LayoutListener.this, PENDING_CHANGE_AUTOLAYOUT);
                            }
                        }
                    });
                    t.setRepeats(false);
                }
                model.canvas.registerPendingChange(this, PENDING_CHANGE_AUTOLAYOUT);
                t.restart();
            }
        }
    }
}
