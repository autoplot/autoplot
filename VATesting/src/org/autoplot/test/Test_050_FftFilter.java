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
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JMenuBarOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JComboBoxOperator;
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
public class Test_050_FftFilter implements Scenario {
    
    @Override
    public int runIt(Object o) {

        try {
            ScriptContext.createGui();
            
            AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();
            
            JFrameOperator mainFrame = new JFrameOperator(app);

            
            new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("http://emfisis.physics.uiowa.edu/Flight/RBSP-A/L3/2012/12/01/rbsp-a_magnetometer_1sec-gei_emfisis-L3_20121201_v1.3.2.cdf?Magnitude");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            
            ScriptContext.waitUntilIdle();
            
            JMenuBarOperator menuBar = new JMenuBarOperator( mainFrame );
            menuBar.pushMenu("Tools|Filters|Fourier Filtering|FFT", "|");
            
            DialogOperator fftFrame = new DialogOperator( new RegexComponentChooser( "FFT Power Filter Parameters") );
            
            JTextComponentOperator size = new JTextComponentOperator(fftFrame);
            size.setText("100");
            
            JComboBoxOperator window = new JComboBoxOperator(fftFrame, 0);
            window.selectItem(0);
            JComboBoxOperator slide = new JComboBoxOperator(fftFrame, 1);
            slide.selectItem(2);
                    
            new JButtonOperator( fftFrame, "Ok" ).clickMouse();
            
            Thread.sleep(1000); // This is because of a bug in the locking, otherwise it will grab the current image.
            new JTabbedPaneOperator( app.getTabs() ).selectPage("data");
            System.err.println("Done!");
            
            writeToPng("Test_5pt0_FftFilter.png"); // Leave artifacts for testing.
            save("Test_5pt0_FftFilter.vap");
            
            return(0);
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_050_FftFilter.class.getName()).log(Level.SEVERE, null, ex);
            return(1);
        } catch (IOException ex) {
            Logger.getLogger(Test_050_FftFilter.class.getName()).log(Level.SEVERE, null, ex);
            return(2);
        }
    }
    
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_5pt0_FftFilter"};
	org.netbeans.jemmy.Test.main(params);
    }
}
