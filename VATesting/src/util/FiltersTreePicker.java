
package util;

import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JMenuBarOperator;
import org.netbeans.jemmy.operators.JTreeOperator;
import org.das2.qds.filters.AddFilterDialog;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;

/**
 *
 * @author faden@cottagesystems.com
 */
public class FiltersTreePicker {
    
    /**
     * find the filter and select it from the Tools->Filters gui.
     * @param mainFrame
     * @param path
     */
    public static void pickFilter(JFrameOperator mainFrame, String[] path  ) {
        try {
            // Implement reducex() filter
            new JMenuBarOperator( mainFrame ).pushMenuNoBlock("Tools|Additional Operations...");

            
            DialogOperator addFilterFrame = new DialogOperator( new RegexComponentChooser("Add Operation" ) );
            
            JTabbedPaneOperator op= new JTabbedPaneOperator(addFilterFrame);
            op.selectPage("By Category");
            
            JTreeOperator tree= new JTreeOperator( addFilterFrame );
            tree.clickMouse();
            tree.selectPath(tree.findPath( path ) );
            Thread.sleep(500);
            new JButtonOperator( addFilterFrame, "OK" ).clickMouse();

            Thread.sleep(500);

        } catch ( InterruptedException ex ) {
            throw new RuntimeException(ex);
        }
    }
}
