/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;

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
import util.RegexComponentChooser;

/**
 *
 * @author kenziemclouth
 */
public class Test_5_FftFilter implements Scenario {
    
    public int runIt(Object o) {

        ScriptContext.createGui();

        AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();

        JFrameOperator mainFrame = new JFrameOperator(app);
        
        //new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );
        
        new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("vap+cdfj:http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109");
        new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
        
        JMenuBarOperator menuBar = new JMenuBarOperator( mainFrame );
        menuBar.pushMenu("Tools|Filters|Fourier Filtering|FFT", "|");
        
        DialogOperator fftFrame = new DialogOperator( new RegexComponentChooser( "FFT Power Filter Parameters") );
        
        JTextComponentOperator size = new JTextComponentOperator(fftFrame);
        size.setText("100");
        
        JComboBoxOperator window = new JComboBoxOperator(fftFrame, 0);
        window.selectItem(0);
        JComboBoxOperator slide = new JComboBoxOperator(fftFrame, 1);
        slide.selectItem(1);

        
        
        new JButtonOperator( fftFrame, "Ok" ).clickMouse();
       
        
        new JTabbedPaneOperator( app.getTabs() ).selectPage("data");
        
        //new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );

        System.err.println("Done!");

        return(0);
    }
    
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_5_FftFilter"};
	org.netbeans.jemmy.Test.main(params);
    }
}
