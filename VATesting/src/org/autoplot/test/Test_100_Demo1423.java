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
import org.autoplot.ScriptContext2023;
import util.RegexComponentChooser;

/**
 * first testing of Jemmy API.
 * @author jbf
 */
public class Test_100_Demo1423 {
    
    private static final ScriptContext2023 scriptContext= ScriptContext2023.getInstance();
    
    public static void main( String[] args ) throws Exception {

        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());
        
        scriptContext.createGui();
        
        AutoplotUI app= (AutoplotUI) scriptContext.getViewWindow();
        
        scriptContext.waitUntilIdle();
        
        JFrameOperator mainFrame = new JFrameOperator(app);

        new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );

        new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("/home/jbf/ct/hudson/script/test037/demo1423.jy");
        new JButtonOperator( app.getDataSetSelector().getGoButton() ).clickMouse();
        
        new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );

        DialogOperator popup = new DialogOperator( new RegexComponentChooser( "Run Script demo1423.jy") );           
        new JButtonOperator(popup,"OK").clickMouse();      
        
        scriptContext.setStatus("waiting 4 more seconds.");
        scriptContext.sleep(4000);
        scriptContext.setStatus("done waiting 4 seconds.");
        
        scriptContext.waitUntilIdle();
        
        scriptContext.setStatus("waiting 12 more seconds.");
        scriptContext.sleep(12000);
        scriptContext.setStatus("done waiting 12 seconds.");
        
        scriptContext.setStatus("waiting another 5 seconds.");
        scriptContext.sleep(5000);
        scriptContext.setStatus("done waiting 5 seconds.");
        
        scriptContext.waitUntilIdle();
        
        scriptContext.writeToPng("Test_100_Demo1423.png"); // Leave artifacts for testing.

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
