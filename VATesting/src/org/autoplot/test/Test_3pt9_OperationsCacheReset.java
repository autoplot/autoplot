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
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.virbo.autoplot.AutoplotUI;
import org.virbo.autoplot.ScriptContext;
import static org.virbo.autoplot.ScriptContext.save;
import static org.virbo.autoplot.ScriptContext.writeToPng;
import util.RegexComponentChooser;

/**
 *
 * @author mmclouth
 */
public class Test_3pt9_OperationsCacheReset implements Scenario {
    
     @Override
    public int runIt(Object o) {

        try {
            
            ScriptContext.createGui();
            
            AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();
            
            JFrameOperator mainFrame = new JFrameOperator(app);
        
            // wait for the application to be in the "ready" state.
            new JLabelOperator(mainFrame).waitText( AutoplotUI.READY_MESSAGE );
            
            Thread.sleep(500);
            
            // plot first test dataset
            new JTextFieldOperator(app.getDataSetSelector().getEditor()).setText("vap+inline:ripplesTimeSeries(2000)");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            
            // Implement reducex() filter
            new JMenuBarOperator( mainFrame ).pushMenu("Tools|filters|Data Set Operations|Reduce in Zeroth Dimension", "|");
            
            DialogOperator reduceFrame = new DialogOperator( new RegexComponentChooser( "Reducex Filter Parameters") );
            new JTextFieldOperator(reduceFrame, "1").setText("360"); 
            new JComboBoxOperator(reduceFrame).selectItem("s", true, true);
            Thread.sleep(250);
            new JButtonOperator(reduceFrame, "OK").clickMouse();
            
            JTabbedPaneOperator tabs = new JTabbedPaneOperator( app.getTabs() );
            tabs.selectPage("canvas");
            Thread.sleep(1000);
            
            tabs.selectPage("data");
            Thread.sleep(750);
                
            tabs.selectPage("canvas");
                
            // plot second test dataset            
            new JTextFieldOperator(app.getDataSetSelector().getEditor()).setText("vap+inline:ripplesSpectrogramTimeSeries(2000)");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
             
            // Implement reducex() filter
            new JMenuBarOperator( mainFrame ).pushMenu("Tools|filters|Data Set Operations|Reduce in Zeroth Dimension", "|");
            
            reduceFrame = new DialogOperator( new RegexComponentChooser( "Reducex Filter Parameters") );
            new JTextFieldOperator(reduceFrame, "1").setText("360");
            new JComboBoxOperator(reduceFrame).selectItem("s", true, true);
            Thread.sleep(250);
            new JButtonOperator(reduceFrame, "OK").clickMouse();

            tabs.selectPage("canvas");
            Thread.sleep(1000);
            
            tabs.selectPage("data");
            Thread.sleep(750);
                

            System.err.println("Done!");
            
            writeToPng("Test_3pt9_OperationsCacheReset.png"); // Leave artifacts for testing.
            save("Test_3pt9_OperationsCacheReset.vap");
            
            
        return(0);
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_3pt9_OperationsCacheReset.class.getName()).log(Level.SEVERE, null, ex);
            return(1);
        } catch (IOException ex) {
            Logger.getLogger(Test_3pt9_OperationsCacheReset.class.getName()).log(Level.SEVERE, null, ex);
            return(2);
        }
        
    }
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_3pt9_OperationsCacheReset"};
	org.netbeans.jemmy.Test.main(params);
    }
    
}
