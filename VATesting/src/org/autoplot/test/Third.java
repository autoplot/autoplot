/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.test;

import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTextAreaOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScriptContext;

/**
 *
 * @author jbf
 */
public class Third implements Scenario  {
    private static final ScriptContext scriptContext= ScriptContext.getInstance();

    public int runIt(Object o) {

        scriptContext.createGui();

        AutoplotUI app= (AutoplotUI) scriptContext.getViewWindow();

        JFrameOperator mainFrame = new JFrameOperator(app);

         //wait "Reloaded" footer
        new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );

        new JTabbedPaneOperator( app.getTabs() ).selectPage("axes");

        new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );

        System.err.println("Done!");

        return(0);

    }

    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Third"};
	org.netbeans.jemmy.Test.main(params);
    }
}
