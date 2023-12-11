/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;

import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComponent;
import org.netbeans.jemmy.ComponentChooser;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.TestOut;
import org.netbeans.jemmy.operators.ComponentOperator;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JMenuBarOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AddPlotElementDialog;
import org.autoplot.AutoplotUI;
import org.autoplot.ScreenshotsTool;
import org.autoplot.ScriptContext;
import util.RegexComponentChooser;

/**
 * Demonstrate https://sourceforge.net/p/autoplot/feature-requests/476/
 * 
 * @author Jeremy Faden
 */
public class Test_043_DsfFilter implements Scenario {
    private static final ScriptContext scriptContext= ScriptContext.getInstance();

    public static int getComponentIndex( ComponentOperator child ) {
        JComponent container= (JComponent)child.getParent();
        for ( int i=0; i<container.getComponentCount(); i++ ) {
            if ( container.getComponent(i)==child.getSource() ) {
                return i;
            }
        }
        return -1;
    }
    
    @Override
    public int runIt(Object o) {

        try {
            scriptContext.createGui();
            
            scriptContext.waitUntilIdle();
            
            ScreenshotsTool st= new ScreenshotsTool( scriptContext.getApplication(), "Test_043_DsfFilter/", true );
                    
            AutoplotUI app= (AutoplotUI) scriptContext.getViewWindow();
            
            JFrameOperator mainFrame = new JFrameOperator(app);

            new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("http://autoplot.org/data/agg/hyd/$Y/po_h0_hyd_$Y$m$d_v$v.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=2000-01-01");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();

            Thread.sleep(1000);
            scriptContext.waitUntilIdle();
            
            st.takePicture( 52, "Here we've plotted a dataset with TSB, or the Time Series Browse capability.  Changing the time will load more data." );
            
            JMenuBarOperator menuop= new JMenuBarOperator(mainFrame);

            menuop.pushMenuNoBlock( 
                    new ComponentChooser[] { new RegexComponentChooser("File"),
                    new RegexComponentChooser("Add Plot...") } );

            
            DialogOperator addPlotFrame = new DialogOperator( "Add Plot" );
            AddPlotElementDialog dia= (AddPlotElementDialog) addPlotFrame.getSource();
            
            dia.setDepCount(1);
            dia.getSecondaryDataSetSelector().setValue("http://autoplot.org/data/agg/hyd/$Y/po_h0_hyd_$Y$m$d_v$v.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=2000-01-01");
            
            st.takePicture( 91, "Use the additional operators check box to add operations as the data is loaded.", dia, new java.awt.Point(300,300), MouseEvent.BUTTON1 );
            
            dia.setShowAdditionalOperations(true);
            
            dia.setUsePrimaryFilters(true);
            dia.setPrimaryFilter("|slice1(0)");
            
            dia.setUseSecondaryFilters(true);
            dia.setSecondaryFilter("|slice1(2)");
            
            st.takePicture( 95, "Use the additional operators check box to add operations as the data is loaded." );
            
            new JButtonOperator(addPlotFrame,"Plot Below").push();
            
            st.takePicture( 99, "Copy plots down will copy the plot, and correctly connect the TSB capabilty of the lower plot as well." );
            
            scriptContext.writeToPng("Test_043_DsfFiler.png"); // Leave artifacts for testing.
            scriptContext.save("Test_043_DsfFiler.png");
            
            Thread.sleep(1000);
            st.requestFinish(true);
            
            return(0);

        } catch (IOException ex) {
            Logger.getLogger(Test_043_DsfFilter.class.getName()).log(Level.SEVERE, null, ex);
            return(2);
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_043_DsfFilter.class.getName()).log(Level.SEVERE, null, ex);
            return(3);
        }
    }
    
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_043_DsfFilter"};
        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());
	org.netbeans.jemmy.Test.main(params);
    }
}
