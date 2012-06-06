/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.graph.DasAxis;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.virbo.autoplot.AutoplotUI;
import org.virbo.autoplot.ScriptContext;

/**
 *
 * @author jbf
 */
public class Test_3pt4_TimeSeriesBrowse implements Scenario {

    public int runIt(Object o) {

        try {
            ScriptContext.createGui();
            AutoplotUI app = (AutoplotUI) ScriptContext.getViewWindow();
            JFrameOperator mainFrame = new JFrameOperator(app);
            ScriptContext.waitUntilIdle();

            new JTextFieldOperator(app.getDataSetSelector().getEditor()).setText("vap+cdfj:http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109"); // TODO: try vap+cdf:
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            ScriptContext.waitUntilIdle();

            DasAxis xaxis = ScriptContext.getDocumentModel().getPlots(0).getXaxis().getController().getDasAxis();
            xaxis.scanNext(); // no gui test, but that's okay...
            new JButtonOperator(app.getDataSetSelector().getBrowseButton()).clickMouse();

            DialogOperator diaFrame = new DialogOperator("Editing URI vap+cdfj:http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109" );

            new JButtonOperator( diaFrame, "Plot Below" ).clickMouse();

            return 0;

            
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_3pt4_TimeSeriesBrowse.class.getName()).log(Level.SEVERE, null, ex);
            return -1;
        }
        
    }

    public static void main(String[] argv) {
	String[] params = {"test.Test_3pt4_TimeSeriesBrowse"};
	org.netbeans.jemmy.Test.main(params);
    }

}
