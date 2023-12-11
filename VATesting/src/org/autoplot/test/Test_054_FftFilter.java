/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;

import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.TestOut;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JMenuBarOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JComboBoxOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTextComponentOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScriptContext;
import util.FiltersTreePicker;
import util.RegexComponentChooser;

/**
 *
 * @author kenziemclouth
 */
public class Test_054_FftFilter implements Scenario {
    
    @Override
    public int runIt(Object o) {

        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());
  
        ScriptContext.createGui();

        AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();

        JFrameOperator mainFrame = new JFrameOperator(app);
        
        //new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );
        
        new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("vap+cdfj:http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109");
        new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
        
        FiltersTreePicker.pickFilter( mainFrame, "Filters|Fourier Filtering|FFT with sliding window".split("\\|") );
        
        DialogOperator fftFrame = new DialogOperator( new RegexComponentChooser( "Edit Operations") );
        
        JTextComponentOperator size = new JTextComponentOperator(fftFrame);
        size.setText("100");
        
        JComboBoxOperator window = new JComboBoxOperator(fftFrame, 0);
        window.selectItem(0);
        JComboBoxOperator slide = new JComboBoxOperator(fftFrame, 1);
        slide.selectItem(1);

        new JButtonOperator( fftFrame, "OK" ).clickMouse();
       
        
        new JTabbedPaneOperator( app.getTabs() ).selectPage("data");
        
        //new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );

        System.err.println("Done!");

        return(0);
    }
    
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_054_FftFilter"};
	org.netbeans.jemmy.Test.main(params);
    }
}
