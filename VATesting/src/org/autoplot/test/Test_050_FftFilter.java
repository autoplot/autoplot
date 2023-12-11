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
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JComboBoxOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTextComponentOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScriptContext2023;
import util.FiltersTreePicker;
import util.RegexComponentChooser;

/**
 *
 * @author kenziemclouth
 */
public class Test_050_FftFilter implements Scenario {
    private static final ScriptContext2023 scriptContext= ScriptContext2023.getInstance();

    @Override
    public int runIt(Object o) {

        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());
  
        try {
            scriptContext.createGui();
            
            AutoplotUI app= (AutoplotUI) scriptContext.getViewWindow();
            
            JFrameOperator mainFrame = new JFrameOperator(app);

            
            new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("https://emfisis.physics.uiowa.edu/Flight/RBSP-A/L3/2012/12/01/rbsp-a_magnetometer_1sec-gei_emfisis-L3_20121201_v1.3.4.cdf?Magnitude");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            
            scriptContext.waitUntilIdle();
            
            FiltersTreePicker.pickFilter( mainFrame, "Filters|Fourier Filtering|FFT Power Spectrum with sliding window".split("\\|") );
            
            DialogOperator fftFrame = new DialogOperator( new RegexComponentChooser( "Edit Operations") );
            
            Thread.sleep(100);
            
            JTextComponentOperator size = new JTextComponentOperator(fftFrame);
            size.setText("100");
            
            JComboBoxOperator window = new JComboBoxOperator(fftFrame, 0);
            window.selectItem(0);
            
            Thread.sleep(500);
            
            JComboBoxOperator slide = new JComboBoxOperator(fftFrame, 1);
            slide.selectItem(2);
                    
            new JButtonOperator( fftFrame, "OK" ).clickMouse();
            
            Thread.sleep(1000); // This is because of a bug in the locking, otherwise it will grab the current image.
            new JTabbedPaneOperator( app.getTabs() ).selectPage("data");
            System.err.println("Done!");
            
            scriptContext.writeToPng("Test_050_FftFilter.png"); // Leave artifacts for testing.
            scriptContext.save("Test_050_FftFilter.vap");
            
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
	String[] params = {"org.autoplot.test.Test_050_FftFilter"};
	org.netbeans.jemmy.Test.main(params);
    }
}
