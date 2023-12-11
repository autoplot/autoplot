/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.test;

import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScriptContext;

/**
 *
 * @author jbf
 */
public class Second implements Scenario {
    public int runIt(Object param) {
        ScriptContext.createGui();

        AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();

        JFrameOperator mainFrame = new JFrameOperator(app);

         //wait "Reloaded" footer
        new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );

        new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("vap+inline:ripples(200)");
        new JButtonOperator( app.getDataSetSelector().getGoButton() ).press();

        new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );

        System.err.println("Done!");

        return(0);
    }

    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Second"};
	org.netbeans.jemmy.Test.main(params);
    }
}
