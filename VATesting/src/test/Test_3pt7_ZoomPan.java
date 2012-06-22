/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import java.awt.event.MouseWheelEvent;
import org.das2.graph.DasPlot;
import org.netbeans.jemmy.drivers.input.MouseEventDriver;
import org.virbo.autoplot.dom.Application;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.virbo.autoplot.AutoplotUI;
import static org.virbo.autoplot.ScriptContext.*;

/**
 *
 * @author jbf
 */
public class Test_3pt7_ZoomPan {
    public static void main( String[] args ) throws Exception {
        createGui();
        AutoplotUI app = (AutoplotUI) getViewWindow();
        JFrameOperator mainFrame = new JFrameOperator(app);
        waitUntilIdle();

        Application dom= getDocumentModel();

        DasPlot p= dom.getController().getPlot().getController().getDasPlot();
        MouseWheelEvent e;
        e= new MouseWheelEvent( p, 0, System.currentTimeMillis(), 0, 300, 300, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1 );
        p.getDasMouseInputAdapter().getSecondaryModule().mouseWheelMoved(e);

        writeToPng( "Test_3pt7_ZoomPan.001.png");

        e= new MouseWheelEvent( p, 0, System.currentTimeMillis(), 0, 300, 300, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, -1 );
        p.getDasMouseInputAdapter().getSecondaryModule().mouseWheelMoved(e);

        writeToPng( "Test_3pt7_ZoomPan.002.png");

        dom.getController().getPlot().setIsotropic(true);
        e= new MouseWheelEvent( p, 0, System.currentTimeMillis(), 0, 300, 300, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1 );
        p.getXAxis().getDasMouseInputAdapter().getSecondaryModule().mouseWheelMoved(e);

        writeToPng( "Test_3pt7_ZoomPan.003.png");
        
    }
}
