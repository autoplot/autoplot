/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.das2.graph.SpectrogramRenderer;
import org.das2.util.filesystem.FileSystem;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.TestOut;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScriptContext;
import org.autoplot.dom.Application;

/**
 * Demos new failure for ect-rept-sci-L3_20130915.
 * @author jbf
 */
public class Test_041_Lanl20140214b implements Scenario {
    
    private static final Logger logger= LoggerManager.getLogger("vatesting");
    private static final ScriptContext scriptContext= ScriptContext.getInstance();

    public int runIt(Object param) {

        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());

        try {
            System.err.println( "## v20141212" );
            
            scriptContext.createGui();
            Application dom= scriptContext.getDocumentModel();
            
            AutoplotUI app= (AutoplotUI) scriptContext.getViewWindow();

            //wait "Reloaded" footer
            
            scriptContext.waitUntilIdle();

            new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("http://www.rbsp-ect.lanl.gov/data_pub/rbspa/rept/level3/rbspa_$x_ect-rept-sci-L3_$Y$m$d_v$(v,sep).cdf?FEDU&timerange=20130915");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            
            Thread.sleep(6000); // TODO: have a look at the waitUntilIdle and convince self that it shouldn't be considered when blocking.
            scriptContext.waitUntilIdle();

            // TODO: There's a strange bug where waitUntilIdle doesn't wait.  I can demo that the TSB code blocks properly, but this should be investigated more.
            int i=200;
            File f= new File( FileSystem.settings().getLocalCacheDir(), "/http/www.rbsp-ect.lanl.gov/data_pub/rbspa/rept/level3/rbspa_rel03_ect-rept-sci-L3_20130915_v5.0.0.cdf" );
            while ( i>0 && !f.exists() ) {
                System.err.println("sleeping while file is downloading, because waitUntilIdle function failed. "+i);
                Thread.sleep(1000);
                i=i-1;
                scriptContext.waitUntilIdle();
            }
            
            scriptContext.writeToPng( "Test_041_Lanl20140214b.001.png");

            Util.switchToTab(app,"style");
            // how to push droplist?
            Util.switchToTab(app,"canvas");
            
            dom.getPlotElements(0).getStyle().setRebinMethod( SpectrogramRenderer.RebinnerEnum.lanlNearestNeighbor );
            
            scriptContext.writeToPng( "Test_041_Lanl20140214b.001a.png");
            
            scriptContext.waitUntilIdle();

            Util.pushContextMenu( dom.getPlots(0).getController().getDasPlot(),
                    new String[] { "Plot Element Type", "Series" } );
            
            scriptContext.waitUntilIdle();
            
            scriptContext.writeToPng( "Test_041_Lanl20140214b.002.png");
            
            Util.pushContextMenu( dom.getPlots(0).getController().getDasPlot(),
                    new String[] { "Plot Element Type", "Series" } );
            
            scriptContext.waitUntilIdle();
            
            Thread.sleep(1000);
             
            return 0;
            
        } catch ( Exception e ) {
            logger.log( Level.SEVERE, e.getMessage(), e );
            return 1;
        }
    }

    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_041_Lanl20140214b"};
        Util.setLAF();
	org.netbeans.jemmy.Test.main(params);
    }
}