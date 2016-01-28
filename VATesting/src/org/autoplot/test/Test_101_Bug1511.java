/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;


import java.awt.AWTException;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.netbeans.jemmy.ComponentChooser;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JMenuBarOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JCheckBoxOperator;
import org.netbeans.jemmy.operators.JComponentOperator.JComponentFinder;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JPopupMenuOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTableOperator;
import org.netbeans.jemmy.operators.JTextComponentOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.virbo.autoplot.AutoplotUI;
import org.virbo.autoplot.ScriptContext;
import static org.virbo.autoplot.ScriptContext.save;
import static org.virbo.autoplot.ScriptContext.writeToPng;
import org.virbo.autoplot.dom.Application;
import util.RegexComponentChooser;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Robot;
import java.awt.Window;
import org.netbeans.jemmy.operators.JListOperator;
import org.netbeans.jemmy.operators.WindowOperator;

/**
 * Jemmy test that tests for the bugs caused in Bug1511 (http://sourceforge.net/p/autoplot/bugs/1511/)
 *
 *   1. plot http://emfisis.physics.uiowa.edu/Flight/RBSP-B/Quick-Look/2013/10/09/rbsp-b_WFR-waveform-continuous-burst_emfisis-Quick-Look_20131009T19_v1.4.1.cdf?BuSamples[0:20]
 *   2. add the filter fftPower.
 *   3. zoom in to a shorter interval.
 *   4. right-click, copy plot elements down.
 *   5. enter the editor for the lower panel. Set the component to BvSamples.
 * @author mmclouth
 */
public class Test_101_Bug1511 implements Scenario {
     @Override
    public int runIt(Object o) {

        try {
            
            ScriptContext.createGui();
            
            AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();
            
            JFrameOperator mainFrame = new JFrameOperator(app);
            
            // wait for the application to be in the "ready" state.
            new JLabelOperator(mainFrame).waitText( AutoplotUI.READY_MESSAGE );
            
            Thread.sleep(500);
            
            
            new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("vap+cdfj:http://emfisis.physics.uiowa.edu/Flight/RBSP-B/Quick-Look/2013/10/09/rbsp-b_WFR-waveform-continuous-burst_emfisis-Quick-Look_20131009T19_v1.4.1.cdf?BuSamples[0:20]");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            
            
            Thread.sleep(5000);
            ScriptContext.waitUntilIdle();
            
            JMenuBarOperator menuBar = new JMenuBarOperator( mainFrame );
            //menuBar.pushMenu("Tools|Manage and Browse Tools...", "|");
            menuBar.pushMenuNoBlock("Tools|Additional Operations...", "|");
            
            
            //Thread.sleep(100);
            //RegexComponentChooser addOppComp = new RegexComponentChooser("Tools Manager");
            //RegexComponentChooser addOppComp = new RegexComponentChooser("Add Operation ");
            //JComponentFinder findAddOp = new JComponentFinder(addOppComp);
            
            DialogOperator addOp = new DialogOperator(new RegexComponentChooser("Add Operation")) ;
            
            new JTabbedPaneOperator( addOp ).selectPage("Alphabetical");
            
            JListOperator opList = new JListOperator( addOp);
            
            int index = opList.findItemIndex("FFT");
            opList.clickOnItem( index, 1);

            Thread.sleep(500);
            JButtonOperator OKbutton = new JButtonOperator(addOp, "Okay");
            //JButtonOperator OKbutton = new JButtonOperator(addOp, "OK");
            OKbutton.clickMouse();
            
            //JTableOperator opTable = new JTableOperator( addOp);
            //opTable.selectCell(opTable.findCellRow("Filters"), 0);
            
            Thread.sleep(1000);
            
            
            DialogOperator editOp = new DialogOperator(new RegexComponentChooser("Edit Operations")) ;
            JButtonOperator OKbutton2 = new JButtonOperator(editOp, "Okay");
            OKbutton2.clickMouse();
            Thread.sleep(1000);
            //mainFrame.moveMouse(mainFrame.getCenterXForClick(), mainFrame.getCenterYForClick());
            
            Robot robot;
            try {
                robot = new Robot();
                robot.mouseMove(mainFrame.getCenterX(), mainFrame.getCenterY());
                Thread.sleep(100);
                robot.mouseWheel(-5);
                Thread.sleep(1000);
            } catch (AWTException ex) {
                Logger.getLogger(Test_101_Bug1511.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            mainFrame.clickForPopup(mainFrame.getCenterX(), mainFrame.getCenterY());
            
            JPopupMenuOperator popup = new JPopupMenuOperator();
            popup.pushMenuNoBlock("Add Plot|Copy Plot Elements Down", "|"); // I think because this is a "modal" dialog.
            Thread.sleep(5000);
            
            
            return(0);
            
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_101_Bug1511.class.getName()).log(Level.SEVERE, null, ex);
            return(1);
        } 
    }
    
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_101_Bug1511"};
	org.netbeans.jemmy.Test.main(params);
    }
    
}
