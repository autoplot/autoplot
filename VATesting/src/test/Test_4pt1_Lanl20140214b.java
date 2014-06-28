/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.das2.graph.SpectrogramRenderer;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.virbo.autoplot.AutoplotUI;
import static org.virbo.autoplot.ScriptContext.*;
import org.virbo.autoplot.dom.Application;

/**
 * Demos new failure for ect-rept-sci-L3_20130915.
 * @author jbf
 */
public class Test_4pt1_Lanl20140214b implements Scenario {
    
    private static final Logger logger= LoggerManager.getLogger("vatesting");
    
    public int runIt(Object param) {
        try {
            createGui();
            Application dom= getDocumentModel();
            
            AutoplotUI app= (AutoplotUI) getViewWindow();

            //wait "Reloaded" footer
            waitUntilIdle();

            new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("http://www.rbsp-ect.lanl.gov/data_pub/rbspa/rept/level3/rbspa_rel02_ect-rept-sci-L3_$Y$m$d_v$(v,seo).cdf?FEDU&timerange=20130915");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();

            waitUntilIdle();
            
            writeToPng( "Test_4pt1_Lanl20140214b.001.png");

            Util.switchToTab(app,"style");
            // how to push droplist?
            Util.switchToTab(app,"canvas");
            
            dom.getPlotElements(0).getStyle().setRebinMethod( SpectrogramRenderer.RebinnerEnum.lanlNearestNeighbor );
            
            writeToPng( "Test_4pt1_Lanl20140214b.001a.png");
            
            waitUntilIdle();

            Util.pushContextMenu( dom.getPlots(0).getController().getDasPlot(),
                    new String[] { "Plot Style", "Series" } );
            
            waitUntilIdle();
            
            writeToPng( "Test_4pt1_Lanl20140214b.002.png");
            
            Util.pushContextMenu( dom.getPlots(0).getController().getDasPlot(),
                    new String[] { "Plot Style", "Series" } );
            
            waitUntilIdle();
            
             
            return 0;
            
        } catch ( Exception e ) {
            logger.log( Level.SEVERE, e.getMessage(), e );
            return 1;
        }
    }

    public static void main(String[] argv) {
	String[] params = {"test.Test_4pt1_Lanl20140214b"};
        Util.setLAF();
	org.netbeans.jemmy.Test.main(params);
    }
}