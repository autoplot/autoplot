/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.virbo.autoplot.AutoplotUI;
import org.virbo.autoplot.ScriptContext;

/**
 * first testing of Jemmy API.
 * @author jbf
 */
public class First {
    public static void main( String[] args ) throws Exception {

        ScriptContext.createGui();
        
        AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();
        
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
