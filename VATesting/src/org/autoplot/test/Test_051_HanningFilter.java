/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.TestOut;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JMenuBarOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTextComponentOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScriptContext;
import static org.autoplot.ScriptContext.save;
import static org.autoplot.ScriptContext.writeToPng;
import util.FiltersTreePicker;
import util.RegexComponentChooser;

/**
 *
 * @author kenziemclouth
 */
public class Test_051_HanningFilter implements Scenario {
    
    @Override
    public int runIt(Object o) {

        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());

        System.err.println("$Date: 2006-07-22 21:42:37 -0700 (Sat, 22 Jul 2006) $");
        try {
            ScriptContext.createGui();

            AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();

            JFrameOperator mainFrame = new JFrameOperator(app);
            // wait for the application to be in the "ready" state.
            new JLabelOperator(mainFrame).waitText( AutoplotUI.READY_MESSAGE );

            new JMenuBarOperator( mainFrame ).pushMenu("Options|Enable Feature|Data Panel", "|");
            
            Thread.sleep(3000);

            JTabbedPaneOperator tabs = new JTabbedPaneOperator( app.getTabs() );
            
            //need to use different dataset for this test
            new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("http://emfisis.physics.uiowa.edu/Flight/RBSP-A/L3/2012/12/01/rbsp-a_magnetometer_1sec-gei_emfisis-L3_20121201_v1.3.3.cdf?Magnitude");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            
            Thread.sleep(3000);
            
            ScriptContext.waitUntilIdle();
            
            FiltersTreePicker.pickFilter( mainFrame, "Filters|Fourier Filtering|Hanning".split("\\|") );
        
            DialogOperator hanningFrame = new DialogOperator( new RegexComponentChooser( "Edit Operations") );
        
            new JTextComponentOperator(hanningFrame).setText("128");
        
            new JButtonOperator( hanningFrame, "Ok" ).clickMouse();      

            Thread.sleep(10000);
            
            tabs.selectPage("data");
            System.err.println("Done!");

            writeToPng("Test_051_HanningFilter.png"); // Leave artifacts for testing.
            save("Test_051_HanningFilter.vap");
            
            return(0);
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_051_HanningFilter.class.getName()).log(Level.SEVERE, null, ex);
            return(1);
        } catch (IOException ex) {
            Logger.getLogger(Test_051_HanningFilter.class.getName()).log(Level.SEVERE, null, ex);
            return(2);
        }
    }
    
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_051_HanningFilter"};
	org.netbeans.jemmy.Test.main(params);
    }    
}
