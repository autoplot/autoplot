/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.virbo.autoplot.AutoplotUI;
import static org.virbo.autoplot.ScriptContext.*;
import org.virbo.autoplot.dom.Application;

/**
 * Demos new failure for ect-rept-sci-L3_20130915.
 * @author jbf
 */
public class Test_4pt1_Lanl20140214b implements Scenario {
    public int runIt(Object param) {
        try {
            createGui();
            Application dom= getDocumentModel();
            
            AutoplotUI app= (AutoplotUI) getViewWindow();

            //wait "Reloaded" footer
            waitUntilIdle();

            new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("http://www.rbsp-ect.lanl.gov/data_pub/rbspa/rept/level3/rbspa_rel02_ect-rept-sci-L3_20130915_v4.0.0.cdf?FEDU");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();

            waitUntilIdle();
            
            writeToPng( "Test_4pt1_Lanl20140214b.001.png");

            Util.pushContextMenu( dom.getPlots(0).getController().getDasPlot(),
                    new String[] { "Plot Style", "Series" } );
            
            waitUntilIdle();
            
            writeToPng( "Test_4pt1_Lanl20140214b.002.png");
             
            waitUntilIdle();
             
            return 0;
            
        } catch ( Exception e ) {
            return 1;
        }
    }

    public static void main(String[] argv) {
	String[] params = {"test.Test_4pt1_Lanl20140214b"};
        Util.setLAF();
	org.netbeans.jemmy.Test.main(params);
    }
}