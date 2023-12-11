/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.test;

import java.io.IOException;
import org.autoplot.dom.Application;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.LoggerManager;
import org.netbeans.jemmy.JemmyProperties;
import org.autoplot.AutoplotUI;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.TestOut;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import static org.autoplot.ScriptContext.*;

/**
 * Test_033
 * @author jbf
 */
public class Test_033_OperationsCacheDoesNotProperlyUpdate implements Scenario  {

    private static final Logger logger= LoggerManager.getLogger("vatesting");
    
    public int runIt(Object param) {

        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());
        
        try {
            createGui();
            AutoplotUI app = (AutoplotUI) getViewWindow();

            Application dom = getDocumentModel();
            dom.getOptions().setAutolayout(false);
            
            waitUntilIdle();

            
            // plot test dataset
            new JTextFieldOperator(app.getDataSetSelector().getEditor()).setText("vap+inline:ripples(100,110)+randn(100)/50+outerProduct(ones(100),randn(110)/50)");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();

            waitUntilIdle();

            dom.getPlotElements(0).setComponent("|dbAboveBackgroundDim1(10)");
            waitUntilIdle();

            dom.getPlots(0).getXaxis().getController().getDasAxis().scanPrevious();
            waitUntilIdle();

            writeToPng("Test_033_OperationsCacheDoesNotProperlyUpdate.png");
            save("Test_033_OperationsCacheDoesNotProperlyUpdate.vap");

            return 0;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return 1;
        } 
        //catch (InterruptedException ex) {
        //    Logger.getLogger(Test_033_OperationsCacheDoesNotProperlyUpdate.class.getName()).log(Level.SEVERE, null, ex);
        //    return(2);
        //}
    }


    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_033_OperationsCacheDoesNotProperlyUpdate"};
	org.netbeans.jemmy.Test.main(params);
    }

}
