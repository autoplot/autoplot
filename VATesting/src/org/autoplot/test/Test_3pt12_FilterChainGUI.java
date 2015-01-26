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
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JSpinnerOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.netbeans.jemmy.operators.JTreeOperator;
import org.virbo.autoplot.AutoplotUI;
import org.virbo.autoplot.ScriptContext;
import static org.virbo.autoplot.ScriptContext.save;
import static org.virbo.autoplot.ScriptContext.writeToPng;
import util.RegexComponentChooser;

/**
 *
 * @author kenziemclouth
 */
public class Test_3pt12_FilterChainGUI implements Scenario {
    
    @Override
    public int runIt(Object o) {

        try {
            ScriptContext.createGui();
            
            AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();
            
            JFrameOperator mainFrame = new JFrameOperator(app);
        
            // wait for the application to be in the "ready" state.
            new JLabelOperator(mainFrame).waitText( AutoplotUI.READY_MESSAGE );
            
            Thread.sleep(500);
            
            new JTextFieldOperator(app.getDataSetSelector().getEditor()).setText("vap+inline:ripples(100,110)+randn(100)/50+outerProduct(ones(100),randn(110)/50)");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            
            new JMenuBarOperator( mainFrame ).pushMenu("Options|Enable Feature|Data Panel", "|");
            
            Thread.sleep(500);
            new JLabelOperator(mainFrame).waitText( AutoplotUI.READY_MESSAGE );
            
            new JTabbedPaneOperator( app.getTabs() ).selectPage("data");

            JButtonOperator search = new JButtonOperator(mainFrame,7);
            search.pushNoBlock();
            
            //add the "slice0()" filter
            DialogOperator editFilterFrame = new DialogOperator( new RegexComponentChooser( "Edit Filters") );           
            new JButtonOperator(editFilterFrame).push();           
            DialogOperator addFilterFrame = new DialogOperator( new RegexComponentChooser( "Add Filter") );           
            JTreeOperator filterTree = new JTreeOperator(addFilterFrame);
            filterTree.clickOnPath(filterTree.getPathForRow(2), 2);  //open Data Set Operations branch
            filterTree.selectRow(11); //select Slice0
            new JButtonOperator(addFilterFrame,"OK").clickMouse();           
            new JSpinnerOperator(editFilterFrame).getIncreaseOperator().clickMouse(10);
            new JButtonOperator(editFilterFrame, "OK").clickMouse();
            
            search.pushNoBlock();
            
            //add the "add" filter
            editFilterFrame = new DialogOperator( new RegexComponentChooser( "Edit Filters") );           
            new JButtonOperator(editFilterFrame).push();
            addFilterFrame = new DialogOperator( new RegexComponentChooser( "Add Filter") );
            filterTree = new JTreeOperator(addFilterFrame);
            filterTree.selectRow(7); //select Add
            new JButtonOperator(addFilterFrame,"OK").clickMouse();
            new JTextFieldOperator(editFilterFrame, "1.").setText("5");
            new JButtonOperator(editFilterFrame, "OK").clickMouse();
            
            new JTabbedPaneOperator( app.getTabs() ).selectPage("canvas");
            Thread.sleep(5000);
            System.err.println("Done!");
            
            writeToPng("Test_3pt12_FilterChainGUI.png"); // Leave artifacts for testing.
            save("Test_3pt12_FilterChainGUI.vap");
            
            
            
            
            
            
            
            
            
            
            
            
            
            return(0);
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_3pt12_FilterChainGUI.class.getName()).log(Level.SEVERE, null, ex);
            return(1);
        } catch (IOException ex) {
            Logger.getLogger(Test_3pt12_FilterChainGUI.class.getName()).log(Level.SEVERE, null, ex);
            return(2);
        }
        
    }
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_3pt12_FilterChainGUI"};
	org.netbeans.jemmy.Test.main(params);
    }
    
}
