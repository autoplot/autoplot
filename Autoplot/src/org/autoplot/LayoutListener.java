
package org.autoplot;

import java.util.logging.Level;
import org.das2.graph.DasAxis;
import org.das2.graph.DasPlot;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.logging.Logger;
import javax.swing.Timer;
import org.das2.graph.DasCanvas;
import org.autoplot.dom.ApplicationController;
import org.autoplot.dom.Canvas;
import org.autoplot.dom.CanvasController;
import org.autoplot.layout.LayoutUtil;

/**
 * Listens to changes in axis labels and colorbar visible to trigger relayout.
 * @author jbf
 */
public class LayoutListener implements PropertyChangeListener {

    ApplicationModel model;  
    Timer t;
    private static final Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.dom.layout");
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

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        final Canvas canvas= model.dom.getController().getCanvas();
        final CanvasController cc= canvas.getController();
        final DasCanvas dasCanvas= cc.getDasCanvas();
        
        if (model.dom.getOptions().isAutolayout() && dasCanvas.getWidth()>0 ) {
            logger.log(Level.FINER, "property change: {0}", evt.getPropertyName());
            if (evt.getSource() instanceof Component &&
                    ((Component) evt.getSource()).isVisible()) {
                if (t == null) {
                    logger.finer("create timer ");
                    t = new Timer(100, new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            synchronized ( LayoutListener.this ) {
                                if ( model.dom.getOptions().isAutolayout() ) { //bug 3034795 (now 411)
                                    logger.finer("do autolayout");
                                    ApplicationController applicationController= model.getDocumentModel().getController();
                                    cc.performingChange(LayoutListener.this,PENDING_CHANGE_AUTOLAYOUT);
                                    dasCanvas.performingChange(LayoutListener.this, PENDING_CHANGE_AUTOLAYOUT);
//                                    try {
//                                        ScriptContext.save( String.format( "/tmp/ap/%09d.before.vap", System.currentTimeMillis() ) );
//                                    } catch (IOException ex) {
//                                        Logger.getLogger(LayoutListener.class.getName()).log(Level.SEVERE, null, ex);
//                                    }
//                                    System.err.println( "marginRowBefore: "+applicationController.getRow() );
                                    LayoutUtil.autolayout( dasCanvas,
                                            applicationController.getRow(), applicationController.getColumn() );
//                                    System.err.println( "marginRowAfter: "+applicationController.getRow() );
//                                    try {
//                                        ScriptContext.save( String.format( "/tmp/ap/%09d._after.vap", System.currentTimeMillis() ) );
//                                    } catch (IOException ex) {
//                                        Logger.getLogger(LayoutListener.class.getName()).log(Level.SEVERE, null, ex);
//                                    }
                                    dasCanvas.changePerformed(LayoutListener.this, PENDING_CHANGE_AUTOLAYOUT);
                                    cc.changePerformed(LayoutListener.this,PENDING_CHANGE_AUTOLAYOUT);
                                } else {
                                    // the timer was tickled, but in the meantime the autolayout was set to false.
                                    dasCanvas.performingChange(LayoutListener.this, PENDING_CHANGE_AUTOLAYOUT);
                                    dasCanvas.changePerformed(LayoutListener.this, PENDING_CHANGE_AUTOLAYOUT);
                                    cc.performingChange(LayoutListener.this, PENDING_CHANGE_AUTOLAYOUT);
                                    cc.changePerformed(LayoutListener.this,PENDING_CHANGE_AUTOLAYOUT);
                                }
                            }
                        }
                    });
                    t.setRepeats(false);
                }
                
                synchronized ( LayoutListener.this ) {
                    if ( dasCanvas.isPendingChanges(PENDING_CHANGE_AUTOLAYOUT) ) {
                        logger.finer("autolayout is already pending");
                    } else {
                        cc.registerPendingChange(LayoutListener.this,PENDING_CHANGE_AUTOLAYOUT);
                        dasCanvas.registerPendingChange(LayoutListener.this, PENDING_CHANGE_AUTOLAYOUT);
                        t.restart();
                    }
                }
                

            }
        }
    }
}
