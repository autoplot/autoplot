/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.test;

import org.das2.datum.DatumRange;
import org.netbeans.jemmy.Scenario;
import java.awt.event.MouseWheelEvent;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.LoggerManager;
import org.das2.graph.DasPlot;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.TestOut;
import org.autoplot.dom.Application;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.autoplot.AutoplotUI;
import static org.autoplot.ScriptContext.*;
import org.autoplot.scriptconsole.DumpRteExceptionHandler;

/**
 * verify the zoom pan functions of the mousewheel.
 * @author jbf
 */
public class Test_037_ZoomPan implements Scenario {

    private static final Logger logger= LoggerManager.getLogger("vatesting");
    
    private static boolean close( double t, double d ) {
        return Math.abs( (t-d)/t ) < 0.10;
    }

    @Override
    public int runIt(Object o) {
        DatumRange dr0;
        DatumRange dr1;
        DatumRange dr2;

        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());

        try {
            
            getApplicationModel().setExceptionHandler( new DumpRteExceptionHandler() );
            
            createGui();
            AutoplotUI app = (AutoplotUI) getViewWindow();
            Application dom = getDocumentModel();
            dom.getOptions().setAutolayout(false);
            
            JFrameOperator mainFrame = new JFrameOperator(app);
            waitUntilIdle();
            
            DasPlot p = dom.getController().getPlot().getController().getDasPlot();
            MouseWheelEvent e;
            e = new MouseWheelEvent(p, 0, System.currentTimeMillis(), 0, 300, 300, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1);
            p.getDasMouseInputAdapter().getSecondaryModule().mouseWheelMoved(e);
            writeToPng("Test_037_ZoomPan.001.png");
            dr0= p.getXAxis().getDatumRange();

            e = new MouseWheelEvent(p, 0, System.currentTimeMillis(), 0, 300, 300, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, -1);
            p.getDasMouseInputAdapter().getSecondaryModule().mouseWheelMoved(e);
            writeToPng("Test_037_ZoomPan.002.png");
            dr1= p.getXAxis().getDatumRange();

            dom.getController().getPlot().setIsotropic(true);
            e = new MouseWheelEvent(p, 0, System.currentTimeMillis(), 0, 300, 300, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1);
            p.getXAxis().getDasMouseInputAdapter().getSecondaryModule().mouseWheelMoved(e);
            writeToPng("Test_037_ZoomPan.003.png");
            dr2= p.getXAxis().getDatumRange();

            if ( dr0.width().value()==125.0
                    && dr1.width().value()==100.0
                    && ( close( dr2.width().value(), 155.45 ) 
                    || close( dr2.width().value(), 171.397 ) 
                    || close( dr2.width().value(), 177.201 ) ) ) { // autolayout=false -> 156.88488 ) ) {
                return 0;
            } else {
                System.err.println( "dr0.width= "+dr0.width());
                System.err.println( "dr1.width= "+dr1.width());
                System.err.println( "dr2.width= "+dr2.width());
                return 1;
            }
            
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return 1;
        }

    }

    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_037_ZoomPan"};
	org.netbeans.jemmy.Test.main(params);
    }
}
