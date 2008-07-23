/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import edu.uiowa.physics.pw.das.graph.DasAxis;
import edu.uiowa.physics.pw.das.graph.DasColorBar;
import edu.uiowa.physics.pw.das.graph.DasPlot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.logging.Logger;
import javax.swing.Timer;
import org.virbo.autoplot.layout.LayoutUtil;

/**
 * Listens to changes in axis labels and colorbar visible to trigger relayout.
 * @author jbf
 */
public class AutoLayoutListener implements PropertyChangeListener {

    ApplicationModel model;
    Timer t;
    static Logger logger= Logger.getLogger("virbo.autoplot.autolayout");
    
    AutoLayoutListener( ApplicationModel model ) {
        this.model= model;
        model.plot.addPropertyChangeListener( DasPlot.PROP_TITLE, this );
        model.plot.getXAxis().addPropertyChangeListener( DasAxis.PROP_BOUNDS, this );
        
        //model.plot.getYAxis().addPropertyChangeListener( DasAxis.PROP_LABEL, this );
        model.plot.getYAxis().addPropertyChangeListener( DasAxis.PROP_BOUNDS, this );
        
        model.colorbar.addPropertyChangeListener( "visible", this );
        model.colorbar.addPropertyChangeListener( DasAxis.PROP_BOUNDS, this );

    }
    
    public void propertyChange(PropertyChangeEvent evt) {
        if ( model.autolayout ) {
            logger.fine("property change: "+evt.getPropertyName() );
            if ( t==null ) { 
                logger.fine("create timer " );
                t= new Timer( 100, new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        logger.fine("do autolayout");
                        model.canvas.performingChange( this, "autolayout" );
                        LayoutUtil.autolayout( model.canvas, model.plot.getRow(), model.plot.getColumn() );
                        model.canvas.changePerformed( this, "autolayout" );
                    }
                } );
                t.setRepeats(false);
            }
            model.canvas.registerPendingChange( this, "autolayout" );
            t.restart();
        }
    }

}
