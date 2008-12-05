/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

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
    static Logger logger = Logger.getLogger("virbo.autoplot.autolayout");

    public LayoutListener(ApplicationModel model) {
        this.model = model;
    }

    public void listenTo( DasPlot plot ) {
        plot.addPropertyChangeListener(DasPlot.PROP_TITLE, this);
        plot.getXAxis().addPropertyChangeListener(DasAxis.PROP_BOUNDS, this);
        plot.getYAxis().addPropertyChangeListener(DasAxis.PROP_BOUNDS, this);
    }
    
    public void listenTo( DasAxis colorbar ) {
        colorbar.addPropertyChangeListener("visible", this);
        colorbar.addPropertyChangeListener(DasAxis.PROP_BOUNDS, this );
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if (model.dom.isAutolayout() ) {
            logger.fine("property change: " + evt.getPropertyName());
            if (evt.getSource() instanceof Component &&
                    ((Component) evt.getSource()).isVisible()) {
                if (t == null) {
                    logger.fine("create timer ");
                    t = new Timer(100, new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            logger.fine("do autolayout");
                            ApplicationController applicationController= model.getDocumentModel().getController();
                            model.canvas.performingChange(this, "autolayout");
                            LayoutUtil.autolayout( applicationController.getDasCanvas(), 
                                    applicationController.getRow(), applicationController.getColumn() );
                            model.canvas.changePerformed(this, "autolayout");
                        }
                    });
                    t.setRepeats(false);
                }
                model.canvas.registerPendingChange(this, "autolayout");
                t.restart();
            }
        }
    }
}
