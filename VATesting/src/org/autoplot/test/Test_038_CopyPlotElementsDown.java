/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.test;

import java.io.IOException;
import util.RegexComponentChooser;
import org.netbeans.jemmy.ComponentChooser;
import javax.swing.JMenuItem;
import org.netbeans.jemmy.operators.JPopupMenuOperator;
import org.autoplot.dom.Application;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.LoggerManager;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScriptContext;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.TestOut;

/**
 *
 * @author jbf
 */
public class Test_038_CopyPlotElementsDown implements Scenario  {

    private static final Logger logger= LoggerManager.getLogger("vatesting");
    private static final ScriptContext scriptContext= ScriptContext.getInstance();

    public int runIt(Object param) {

        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());
        
        try {
            scriptContext.createGui();
            AutoplotUI app = (AutoplotUI) scriptContext.getViewWindow();
            
            Application dom = scriptContext.getDocumentModel();
            dom.getOptions().setAutolayout(false);
            
            JFrameOperator mainFrame = new JFrameOperator(app);
            scriptContext.waitUntilIdle();

            scriptContext.plot( "vap+inline:ripplesVectorTimeSeries(200)&RENDER_TYPE=hugeScatter" );
            scriptContext.waitUntilIdle();

            org.das2.graph.DasPlot c= dom.getPlots(0).getController().getDasPlot();
            javax.swing.JPopupMenu menu= c.getDasMouseInputAdapter().getPrimaryPopupMenu();
            menu.show(app, 300, 300 );
            JPopupMenuOperator op= new JPopupMenuOperator( menu );
            JMenuItem item= op.pushMenu( new ComponentChooser[] { new RegexComponentChooser("Add Plot"),
            new RegexComponentChooser("Copy Plot Elements Down") } );

            Thread.sleep(1000);

            scriptContext.writeToPng("Test_038_CopyPlotElementsDown.png");
            scriptContext.save("Test_038_CopyPlotElementsDown.vap");

            return 0;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return 1;
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return 1;
        }
    }


    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_038_CopyPlotElementsDown"};
	org.netbeans.jemmy.Test.main(params);
    }

}
