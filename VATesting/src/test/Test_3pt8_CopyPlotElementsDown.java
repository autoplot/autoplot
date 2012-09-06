/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import java.io.IOException;
import util.RegexComponentChooser;
import org.netbeans.jemmy.ComponentChooser;
import javax.swing.JMenuItem;
import org.netbeans.jemmy.operators.JPopupMenuOperator;
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
public class Test_3pt8_CopyPlotElementsDown implements Scenario  {

    public int runIt(Object param) {
        try {
            createGui();
            AutoplotUI app = (AutoplotUI) getViewWindow();
            JFrameOperator mainFrame = new JFrameOperator(app);
            waitUntilIdle();
            Application dom = getDocumentModel();

            plot( "vap+inline:ripplesVectorTimeSeries(200)&RENDER_TYPE=hugeScatter" );
            waitUntilIdle();

            org.das2.graph.DasPlot c= dom.getPlots(0).getController().getDasPlot();
            javax.swing.JPopupMenu menu= c.getDasMouseInputAdapter().getPrimaryPopupMenu();
            menu.show(app, 300, 300 );
            JPopupMenuOperator op= new JPopupMenuOperator( menu );
            JMenuItem item= op.pushMenu( new ComponentChooser[] { new RegexComponentChooser("Add Plot"),
            new RegexComponentChooser("Copy Plot Elements Down") } );

            Thread.sleep(1000);

            writeToPng("Test_3pt8_CopyPlotElementsDown.png");

            return 0;
        } catch (IOException ex) {
            Logger.getLogger(Test_3pt8_CopyPlotElementsDown.class.getName()).log(Level.SEVERE, null, ex);
            return 1;
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_3pt8_CopyPlotElementsDown.class.getName()).log(Level.SEVERE, null, ex);
            return 1;
        }
    }


    public static void main(String[] argv) {
	String[] params = {"test.Test_3pt8_CopyPlotElementsDown"};
	org.netbeans.jemmy.Test.main(params);
    }

}
