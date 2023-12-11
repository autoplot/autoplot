
package test.endtoend;

import java.io.IOException;
import org.autoplot.ScriptContext;

/**
 * Test Jan's products
 * @author jbf
 */
public class Test004 {
    public static void main(String[] args) {
        try {
            ScriptContext sc= ScriptContext.getInstance();
            sc.getDocumentModel().getOptions().setAutolayout(false);
            sc.getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

            sc.load( "/home/jbf/ct/hudson/vap/merka_celias_test004_v1.04.vap" );
            System.err.println( "#### model pending changes: " + sc.getDocumentModel().getController().isPendingChanges() );
            sc.setCanvasSize( 992, 711 );
            sc.writeToPng( "test004.png" );
            System.exit(0);  // TODO: something is firing up the event thread
        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
