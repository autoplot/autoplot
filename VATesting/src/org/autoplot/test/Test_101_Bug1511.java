
package org.autoplot.test;


import java.awt.AWTException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JMenuBarOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JPopupMenuOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScriptContext2023;
import util.RegexComponentChooser;
import java.awt.Robot;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.TestOut;
import org.netbeans.jemmy.operators.JListOperator;
import org.netbeans.jemmy.operators.JTreeOperator;


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

    private static final ScriptContext2023 scriptContext= ScriptContext2023.getInstance();

     @Override
    public int runIt(Object o) {
    
        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());

        try {
            
            scriptContext.createGui();
            
            AutoplotUI app= (AutoplotUI) scriptContext.getViewWindow();
            
            JFrameOperator mainFrame = new JFrameOperator(app);
            
            // wait for the application to be in the "ready" state.
            new JLabelOperator(mainFrame).waitText( AutoplotUI.READY_MESSAGE );
            
            Thread.sleep(500);
            
            
            new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("vap+cdfj:http://emfisis.physics.uiowa.edu/Flight/RBSP-B/Quick-Look/2013/10/09/rbsp-b_WFR-waveform-continuous-burst_emfisis-Quick-Look_20131009T19_v1.4.1.cdf?BuSamples[0:20]");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
           
            
            Thread.sleep(5000);
            scriptContext.waitUntilIdle();
            
            JMenuBarOperator menuBar = new JMenuBarOperator( mainFrame );

            menuBar.pushMenuNoBlock("Tools|Additional Operations...", "|");
            
            
            
            DialogOperator addOp = new DialogOperator(new RegexComponentChooser("Add Operation")) ;
            
            new JTabbedPaneOperator( addOp ).selectPage("Alphabetical");
            
            JListOperator opList = new JListOperator( addOp);
            
            int index = opList.findItemIndex("FFT Power Spectrum with sliding window");
            opList.clickOnItem( index, 1);

            Thread.sleep(500);
            JButtonOperator OKbutton = new JButtonOperator(addOp, "OK");

            OKbutton.clickMouse();
            
            
            Thread.sleep(3000);
            
            
            DialogOperator editOp = new DialogOperator(new RegexComponentChooser("Edit Operations")) ;
            JButtonOperator OKbutton2 = new JButtonOperator(editOp, "OK");
            OKbutton2.clickMouse();
            Thread.sleep(6000);

            
            Robot robot;
            try {
                robot = new Robot();
                robot.mouseMove(mainFrame.getCenterX(), mainFrame.getCenterY());
                Thread.sleep(100);
                robot.mouseWheel(-2);
                Thread.sleep(1000);
            } catch (AWTException ex) {
                Logger.getLogger(Test_101_Bug1511.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            mainFrame.clickForPopup(mainFrame.getCenterX(), mainFrame.getCenterY());
            
            JPopupMenuOperator popup = new JPopupMenuOperator();
            popup.pushMenuNoBlock("Add Plot|Copy Plot Elements Down", "|"); 
            Thread.sleep(6000);
            
            mainFrame.clickMouse(mainFrame.getCenterX(), mainFrame.getCenterY()-200, 2);
            
            new JButtonOperator(app.getDataSetSelector().getBrowseButton()).pushNoBlock();
            DialogOperator editor = new DialogOperator(mainFrame, 0) ;

            JTreeOperator tree = new JTreeOperator(editor);
            
            Thread.sleep(1000);
            
            System.out.print(tree.getRowCount());
            tree.selectRow(tree.findRow("BvSamples"));
            
            Thread.sleep(500);
            
            new JButtonOperator(editor, "Plot").push();

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
