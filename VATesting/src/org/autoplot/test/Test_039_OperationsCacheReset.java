
package org.autoplot.test;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.TestOut;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JMenuBarOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JComboBoxOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScriptContext;
import static org.autoplot.ScriptContext.save;
import static org.autoplot.ScriptContext.writeToPng;
import util.FiltersTreePicker;
import util.RegexComponentChooser;

/**
 *
 * @author mmclouth
 */
public class Test_039_OperationsCacheReset implements Scenario {
    
     @Override
    public int runIt(Object o) {

        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());
        
        try {
            
            ScriptContext.createGui();
            
            AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();
            
            JFrameOperator mainFrame = new JFrameOperator(app);
        
            // wait for the application to be in the "ready" state.
            new JLabelOperator(mainFrame).waitText( AutoplotUI.READY_MESSAGE );
            
            Thread.sleep(500);
            if ( app.getTabs().getTabByTitle("data")==null ) {
                new JMenuBarOperator(mainFrame).pushMenu("Options|Enable Feature|Data Panel", "|");
            }
            
            Thread.sleep(300);
            
            if ( app.getTabs().getTabByTitle("console")==null ) {
                new JMenuBarOperator(mainFrame).pushMenu("Options|Enable Feature|Log Console", "|");
            }
            
            Thread.sleep(500);
            
            // plot first test dataset
            new JTextFieldOperator(app.getDataSetSelector().getEditor()).setText("vap+inline:ripplesTimeSeries(2000)");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();

            JTabbedPaneOperator tabs = new JTabbedPaneOperator( app.getTabs() );
            
            {

                FiltersTreePicker.pickFilter( mainFrame, new String[] { "Filters", "Data Set Operations", "Reduce in Zeroth Dimension" } );
            
                Thread.sleep(1000);
            
                DialogOperator reduceFrame = new DialogOperator( new RegexComponentChooser( "Edit Operations") );
                new JTextFieldOperator(reduceFrame, "1").setText("360"); 
                new JComboBoxOperator(reduceFrame).selectItem("s", true, true);
                Thread.sleep(1000);
                new JButtonOperator(reduceFrame, "OK").clickMouse();
            
                tabs.selectPage("canvas");
                Thread.sleep(1000);
            
                tabs.selectPage("data");
                Thread.sleep(750);
                
                tabs.selectPage("canvas");
                
            }
            
            // plot second test dataset            
            new JTextFieldOperator(app.getDataSetSelector().getEditor()).setText("vap+inline:ripplesSpectrogramTimeSeries(2000)");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            
            ScriptContext.waitUntilIdle();
             
            {
                FiltersTreePicker.pickFilter( mainFrame, new String[] { "Filters", "Data Set Operations", "Reduce in Zeroth Dimension" } );
               
                Thread.sleep(500);

                DialogOperator reduceFrame = new DialogOperator( new RegexComponentChooser( "Edit Operations") );
                new JTextFieldOperator(reduceFrame, "1").setText("360");
                new JComboBoxOperator(reduceFrame).selectItem("s", true, true);
                Thread.sleep(250);
                new JButtonOperator(reduceFrame, "OK").clickMouse();

            }
            
            tabs.selectPage("canvas");
            Thread.sleep(1000);
            
            tabs.selectPage("data");
            Thread.sleep(750);
                

            System.err.println("Done!");
            
            writeToPng("Test_039_OperationsCacheReset.png"); // Leave artifacts for testing.
            save("Test_039_OperationsCacheReset.vap");
            
            
        return(0);
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_039_OperationsCacheReset.class.getName()).log(Level.SEVERE, null, ex);
            return(1);
        } catch (IOException ex) {
            Logger.getLogger(Test_039_OperationsCacheReset.class.getName()).log(Level.SEVERE, null, ex);
            return(2);
        }
        
    }
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_039_OperationsCacheReset"};
	org.netbeans.jemmy.Test.main(params);
    }
    
}
