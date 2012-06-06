/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTextAreaOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.virbo.autoplot.AutoplotUI;
import org.virbo.autoplot.ScriptContext;

/**
 *
 * @author jbf
 */
public class Third implements Scenario  {

    public int runIt(Object o) {

        ScriptContext.createGui();

        AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();

        JFrameOperator mainFrame = new JFrameOperator(app);

         //wait "Reloaded" footer
        new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );

        new JTabbedPaneOperator( app.getTabs() ).selectPage("axes");

        new JLabelOperator(mainFrame, AutoplotUI.READY_MESSAGE );

        System.err.println("Done!");

        return(0);

    }

    public static void main(String[] argv) {
	String[] params = {"test.Third"};
	org.netbeans.jemmy.Test.main(params);
    }
}
