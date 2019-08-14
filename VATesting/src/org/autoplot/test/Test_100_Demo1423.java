/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.test;

import java.io.File;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.TestOut;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScriptContext;
import static org.autoplot.ScriptContext.writeToPng;
import util.RegexComponentChooser;

/**
 * first testing of Jemmy API.
 * @author jbf
 */
public class Test_100_Demo1423 {
    public static void main( String[] args ) throws Exception {

        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());
        
        ScriptContext.createGui();
        
        AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();
        
        ScriptContext.waitUntilIdle();
        
        JFrameOperator mainFrame = new JFrameOperator(app);

        new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );

        new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("/home/jbf/ct/hudson/script/test037/demo1423.jy");
        new JButtonOperator( app.getDataSetSelector().getGoButton() ).clickMouse();
        
        new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );

        DialogOperator popup = new DialogOperator( new RegexComponentChooser( "Run Script demo1423.jy") );           
        new JButtonOperator(popup,"OK").clickMouse();      
        
        ScriptContext.sleep(2000);
        
        ScriptContext.waitUntilIdle();
        
        ScriptContext.setStatus("waiting 12 more seconds.");
        ScriptContext.sleep(12000);
        ScriptContext.setStatus("done waiting 12 seconds.");
        
        ScriptContext.setStatus("waiting another 5 seconds.");
        ScriptContext.sleep(5000);
        ScriptContext.setStatus("done waiting 5 seconds.");
        
        ScriptContext.waitUntilIdle();
        
        writeToPng("Test_100_Demo1423.png"); // Leave artifacts for testing.

        // The script writes this file, so delete it.  It's redundant.
        new File( "test037_demo1423.png" ).delete();
        
        mainFrame.close();

        System.err.println("Done!");

//        JTextFieldOperator testField = new JTextFieldOperator(mainFrame);

	//type new value in the text field
//	testField.clearText();
//	testField.typeText("3");

    }
}
