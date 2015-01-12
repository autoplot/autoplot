/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JInternalFrame;
import javax.swing.JPopupMenu;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JMenuBarOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JComboBoxOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JInternalFrameOperator;
import org.netbeans.jemmy.operators.JMenuOperator;
import org.netbeans.jemmy.operators.JPopupMenuOperator;
import org.netbeans.jemmy.operators.JPopupMenuOperator.JPopupMenuFinder;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTextComponentOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import static org.netbeans.jemmy.operators.Operator.getPopupMouseButton;
import org.virbo.autoplot.AutoplotUI;
import org.virbo.autoplot.ScriptContext;
import static org.virbo.autoplot.ScriptContext.save;
import static org.virbo.autoplot.ScriptContext.writeToPng;
import util.RegexComponentChooser;

/**
 *
 * @author kenziemclouth
 */
public class Test_3pt1_MultiPanelPlot implements Scenario {
    
    @Override
    public int runIt(Object o) {

        try {
            ScriptContext.createGui();
            
            AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();
            
            JFrameOperator mainFrame = new JFrameOperator(app);
            
            //new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );
            
            JMenuBarOperator menuBar = new JMenuBarOperator( mainFrame );
            menuBar.pushMenu("Options|Enable Feature|Layout Panel", "|");
            
            ScriptContext.waitUntilIdle();
            
            new JTabbedPaneOperator( app.getTabs() ).selectPage("layout");
            
            ScriptContext.waitUntilIdle();
            
            JButtonOperator tallerB = new JButtonOperator( mainFrame, "Taller" );
            tallerB.clickMouse(4);
            
            int pointX = mainFrame.getCenterX();
            int pointY = mainFrame.getCenterY();
            mainFrame.clickForPopup(pointX, pointY);
            mainFrame.clickForPopup(pointX-200, pointY-200);
            
            //JPopupMenu jpm = new JPopupMenu(new RegexComponentChooser("plot.*"));
            //JPopupMenuOperator popup = new JPopupMenuOperator(mainFrame);
            //popup.pushMenu("Plots|Add Plots", "|");
            //System.err.println(jpm.getLocation());
            
            
            
            
            //System.err.println((mainFrame.getComponentAt(pointX-200, pointY-200)).toString());
            //System.err.println((mainFrame.getRootPane()).toString());
            //JInternalFrame plots = new JInternalFrame((mainFrame.getComponentAt(pointX-200, pointY-200)).toString());
            //JInternalFrameOperator plotsFrame = new JInternalFrameOperator(plots);
            //plotsFrame.makeComponentVisible();
            //System.err.println(plotsFrame.contains(pointX-200, pointY-200));
            //System.err.println(plotsFrame.getCenterX());
            //System.err.println(plotsFrame.getCenterY());
            
            
            //mainFrame.moveMouse(pointX-198, pointY-195);
            //mainFrame.moveMouse(pointX, pointY-150);
            //mainFrame.moveMouse(pointX, pointY-160);  //moves mouse down to "Add Plots" option
            //mainFrame.clickMouse();

            
           
            
            //new JPopupMenuOperator(popup).pushMenu("Plot|Add Plots", "|");
            
            
            //new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );

            Thread.sleep(1000); // This is because of a bug in the locking, otherwise it will grab the current image.
            
            System.err.println("Done!");
            
            writeToPng("Test_3pt1_MultiPanelPlot.png"); // Leave artifacts for testing.
            save("Test_3pt1_MultiPanelPlot.vap");
            
            return(0);
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_3pt1_MultiPanelPlot.class.getName()).log(Level.SEVERE, null, ex);
            return(1);
        } catch (IOException ex) {
            Logger.getLogger(Test_3pt1_MultiPanelPlot.class.getName()).log(Level.SEVERE, null, ex);
            return(2);
        }
    }
    
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_3pt1_MultiPanelPlot"};
	org.netbeans.jemmy.Test.main(params);
    }
    
}
