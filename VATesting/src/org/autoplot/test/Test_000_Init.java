/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;

import java.awt.AWTException;
import java.awt.Robot;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.TestOut;
import org.autoplot.AutoplotUI;
import static org.autoplot.ScriptContext.createGui;
import static org.autoplot.ScriptContext.getApplicationModel;
import static org.autoplot.ScriptContext.getViewWindow;
import org.autoplot.scriptconsole.DumpRteExceptionHandler;

/**
 * Verify that Jemmy will work, set the mouse position for tests.
 * @author jbf
 */
public class Test_000_Init implements Scenario {

    @Override
    public int runIt(Object param) {
        
        // hide Jemmy output
        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());
            
        getApplicationModel().setExceptionHandler( new DumpRteExceptionHandler() );
            
        createGui();
        AutoplotUI app = (AutoplotUI) getViewWindow();
        
        try {
            new Robot().mouseMove(app.getX() + 100, app.getY() + 100);
        } catch (AWTException ex) {
            Logger.getLogger(Test_037_ZoomPan.class.getName()).log(Level.SEVERE, null, ex);
        }
        return 0;
        
    }
    
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_000_Init"};
	org.netbeans.jemmy.Test.main(params);
    }    

}
