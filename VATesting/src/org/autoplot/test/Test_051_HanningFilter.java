/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JMenuBarOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTextComponentOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.virbo.autoplot.AutoplotUI;
import org.virbo.autoplot.ScriptContext;
import static org.virbo.autoplot.ScriptContext.save;
import static org.virbo.autoplot.ScriptContext.writeToPng;
import util.RegexComponentChooser;

/**
 *
 * @author kenziemclouth
 */
public class Test_051_HanningFilter implements Scenario {
    
    @Override
    public int runIt(Object o) {

        try {
            ScriptContext.createGui();

            AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();

            JFrameOperator mainFrame = new JFrameOperator(app);
            // wait for the application to be in the "ready" state.
            new JLabelOperator(mainFrame).waitText( AutoplotUI.READY_MESSAGE );

            new JMenuBarOperator( mainFrame ).pushMenu("Options|Enable Feature|Data Panel", "|");

            JTabbedPaneOperator tabs = new JTabbedPaneOperator( app.getTabs() );
            
            //need to use different dataset for this test
            new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("http://emfisis.physics.uiowa.edu/Flight/RBSP-A/L3/2012/12/01/rbsp-a_magnetometer_1sec-gei_emfisis-L3_20121201_v1.3.2.cdf?Magnitude");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            new JMenuBarOperator( mainFrame ).pushMenu("Tools|Filters|Fourier Filtering|Hanning", "|");
        
            DialogOperator hanningFrame = new DialogOperator( new RegexComponentChooser( "Hanning Filter Parameters") );
        
            new JTextComponentOperator(hanningFrame).setText("100");
        
            new JButtonOperator( hanningFrame, "Ok" ).clickMouse();      


            Thread.sleep(1000);
            tabs.selectPage("data");
            System.err.println("Done!");

            writeToPng("Test_5pt1_HanningFilter.png"); // Leave artifacts for testing.
            save("Test_5pt1_HanningFilter.vap");
            
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
