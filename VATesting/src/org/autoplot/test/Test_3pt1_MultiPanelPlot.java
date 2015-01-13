/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;

import java.awt.Point;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JInternalFrame;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JMenuBarOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JComboBoxOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JInternalFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
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
            
            // wait for the application to be in the "ready" state.
            new JLabelOperator(mainFrame).waitText( AutoplotUI.READY_MESSAGE );
            
            Thread.sleep(500);
            
            JMenuBarOperator menuBar = new JMenuBarOperator( mainFrame );
            menuBar.pushMenu("Options|Enable Feature|Layout Panel", "|");
            
            ScriptContext.waitUntilIdle();
            
            new JTabbedPaneOperator( app.getTabs() ).selectPage("layout");
            
            ScriptContext.waitUntilIdle();
            
            JButtonOperator tallerB = new JButtonOperator( mainFrame, "Taller" );
            tallerB.clickMouse(4); // This is the number of clicks, for example 2 is double-click.
            
            Point clickPoint= tallerB.getLocation();
            clickPoint= SwingUtilities.convertPoint( tallerB.getSource().getParent(), clickPoint, mainFrame.getSource() );
            //int pointX = tallerB.getX()+100;
            //int pointY = tallerB.getY()-100;
            mainFrame.clickForPopup(clickPoint.x+50, clickPoint.y-100 );
            //mainFrame.clickForPopup(pointX-200, pointY-200);
            
            JPopupMenuOperator popup = new JPopupMenuOperator();
            popup.pushMenuNoBlock("Plot|Add Plots", "|"); // I think because this is a "modal" dialog.
            
            Thread.sleep(200);
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

            DialogOperator frame = new DialogOperator( new RegexComponentChooser( "Add Plots") );
            
            
            JTextComponentOperator size = new JTextComponentOperator( frame, 1 ); // this will pick the first
            size.enterText("2"); // enterText, not setText, or the values don't commit. (Huh.)
            JTextComponentOperator size1 = new JTextComponentOperator( frame, 0 ); // this will pick the second because the first is "2"
            size1.enterText("3");
            new JButtonOperator(frame,"OK").clickMouse();
            
            ScriptContext.waitUntilIdle();

            while ( frame.isVisible() ) {
                Thread.sleep(100);  // Why does the press take so long???
            }

            
            mainFrame.clickForPopup(clickPoint.x+50, clickPoint.y-110 );
            JPopupMenuOperator popup2 = new JPopupMenuOperator();
            popup2.pushMenuNoBlock("Plot|Delete", "|");
            Thread.sleep(200);
            
            
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
