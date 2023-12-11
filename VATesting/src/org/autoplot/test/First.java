/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.test;

import java.util.logging.Logger;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScriptContext2023;

/**
 * first testing of Jemmy API.
 * @author jbf
 */
public class First {
    public static void main( String[] args ) throws Exception {
        
        // See logging.properties in the root of this project.
        Logger.getLogger("vatesting").warning("warning");
        Logger.getLogger("vatesting").info("info");
        Logger.getLogger("vatesting").fine("fine");
        
        ScriptContext2023 scriptContext= ScriptContext2023.getInstance();
        
        scriptContext.createGui();
        
        AutoplotUI app= (AutoplotUI) scriptContext.getViewWindow();
        
        JFrameOperator mainFrame = new JFrameOperator(app);

         //wait "Reloaded" footer
        new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );

        new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("vap+inline:ripples(200)");
        new JButtonOperator( app.getDataSetSelector().getGoButton() ).press();
        
        new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );

        mainFrame.close();

        System.err.println("Done!");

//        JTextFieldOperator testField = new JTextFieldOperator(mainFrame);

	//type new value in the text field
//	testField.clearText();
//	testField.typeText("3");

    }
}
