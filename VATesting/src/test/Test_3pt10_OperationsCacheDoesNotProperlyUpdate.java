/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import java.io.IOException;
import org.virbo.autoplot.dom.Application;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.virbo.autoplot.AutoplotUI;
import org.netbeans.jemmy.Scenario;
import static org.virbo.autoplot.ScriptContext.*;

/**
 *
 * @author jbf
 */
public class Test_3pt10_OperationsCacheDoesNotProperlyUpdate implements Scenario  {

    public int runIt(Object param) {
        try {
            createGui();
            AutoplotUI app = (AutoplotUI) getViewWindow();

            waitUntilIdle();
            Application dom = getDocumentModel();

            plot( "vap+das2server:http://www-pw.physics.uiowa.edu/das/das2Server?dataset=juno/waves/flight/survey.dsdf&start_time=2012-07-21T12:00:00.000Z&end_time=2012-07-21T24:00:00.000Z" );
            waitUntilIdle();

            dom.getPlotElements(0).setComponent("|dbAboveBackgroundDim1(10)");
            waitUntilIdle();

            dom.getPlots(0).getXaxis().getController().getDasAxis().scanPrevious();
            waitUntilIdle();

            writeToPng("Test_3pt10_OperationsCacheDoesNotProperlyUpdate.png");
            save("Test_3pt10_OperationsCacheDoesNotProperlyUpdate.vap");

            return 0;
        } catch (IOException ex) {
            Logger.getLogger("autoplot.testing").log(Level.SEVERE, null, ex);
            return 1;
        } catch (InterruptedException ex) {
            Logger.getLogger("autoplot.testing").log(Level.SEVERE, null, ex);
            return 1;
        }
    }


    public static void main(String[] argv) {
	String[] params = {"test.Test_3pt10_OperationsCacheDoesNotProperlyUpdate"};
	org.netbeans.jemmy.Test.main(params);
    }

}
