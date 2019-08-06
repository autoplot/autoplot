/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;

import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.LoggerManager;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.TestOut;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScriptContext;
import static org.autoplot.ScriptContext.createGui;
import static org.autoplot.ScriptContext.writeToPng;
import org.autoplot.datasource.DataSetSelector;

/**
 * Run through the bookmarks.  This was introduced after a shocking bug
 * wasn't appreciated until after release, and I realized there was no
 * headful tests that switched render types.
 * 
 * @author jbf
 */
public class Test_005_URISwitching implements Scenario {

    private static final Logger logger= LoggerManager.getLogger("vatesting");
    
    @Override
    public int runIt(Object o) {

        // hide Jemmy output
        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());
            
        try {
            
            System.err.println( "handlers: " + logger.getHandlers() );
            for ( Handler h: logger.getHandlers() ) {
                System.err.println( "handlers: " + h.getClass().toString() + " " + h.getLevel() + " " + h.getFormatter().getClass().toString() );
            }
            
            createGui();
            
            AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();
            
            DataSetSelector dss= app.getDataSetSelector();
            new JTextFieldOperator( dss.getEditor() ).setText("http://autoplot.org/data/hsi_qlimg_5050601_001.fits");
            new JButtonOperator( dss.getGoButton() ).clickMouse();
            Util.waitUntilBusy(500,app.getDom());
            
            writeToPng( "Test_005_URISwitching.demo012.png" );
            
            new JTextFieldOperator( dss.getEditor() ).setText("https://cdaweb.gsfc.nasa.gov/pub/data/omni/low_res_omni/omni2_1963.dat?column=field17");
            new JButtonOperator( dss.getGoButton() ).clickMouse();
            Util.waitUntilBusy(500,app.getDom());
            
            writeToPng( "Test_005_URISwitching.demo007.png" );
            
        } catch (IOException ex) {
            Logger.getLogger(Test_005_URISwitching.class.getName()).log(Level.SEVERE, null, ex);
        }
        return 0;
    }
     
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_005_URISwitching"};
	org.netbeans.jemmy.Test.main(params);
    }
}
