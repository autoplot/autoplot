/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import java.awt.Toolkit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.graph.DasAxis;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.virbo.autoplot.AutoplotUI;
import org.virbo.autoplot.ScriptContext;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.BindingModel;
import util.NameComponentChooser;
import util.RegexComponentChooser;

/**
 *
 * @author jbf
 */
public class Test_3pt4_TimeSeriesBrowse implements Scenario {

    public int runIt(Object o) {

        try {
            ScriptContext.createGui();
            AutoplotUI app = (AutoplotUI) ScriptContext.getViewWindow();
            Application dom = ScriptContext.getDocumentModel();
            dom.getOptions().setAutolayout(false);
            
            JFrameOperator mainFrame = new JFrameOperator(app);
            ScriptContext.waitUntilIdle();

            new JTextFieldOperator(app.getDataSetSelector().getEditor()).setText("vap+cdfj:http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109"); // TODO: try vap+cdf:
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            ScriptContext.waitUntilIdle();

            DasAxis xaxis = ScriptContext.getDocumentModel().getPlots(0).getXaxis().getController().getDasAxis();
            xaxis.scanNext(); // no gui test, but that's okay...
            new JButtonOperator( mainFrame, new NameComponentChooser("browse") ).clickMouse();
                    //app.getDataSetSelector().getBrowseButton()).clickMouse();

            DialogOperator diaFrame = new DialogOperator( new RegexComponentChooser( "Editing .*") );

            new JButtonOperator( diaFrame, "Plot Below" ).clickMouse();

            System.err.println("Boo, sleep because testing server isn't stopping properly."); //TODO: fix this!
            Thread.sleep(2000);
            
            ScriptContext.writeToPng( "Test_3pt4_TimeSeriesBrowse.001.png");
        
            BindingModel[] bms= ScriptContext.getDocumentModel().getBindings();
            for ( BindingModel bm: bms ) {
                System.err.println(bm);
            }

            if ( ScriptContext.getDocumentModel().getBindings().length==4 &&
                    ScriptContext.getDocumentModel().getBindings(1).getSrcProperty().equals("timeRange")) {
                return 0;
            } else {
                return 1;
            }
        } catch ( Exception ex ) {
            Logger.getLogger(Test_3pt4_TimeSeriesBrowse.class.getName()).log(Level.SEVERE, null, ex);
            return -1;
        }        
    }

    public static void main(String[] argv) {
	String[] params = {"test.Test_3pt4_TimeSeriesBrowse"};
	org.netbeans.jemmy.Test.main(params);
    }

}
