/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package vatest.endtoend;

import java.io.IOException;
import static java.lang.System.err;
import static java.lang.System.exit;
import static org.virbo.autoplot.ScriptContext.getDocumentModel;
import static org.virbo.autoplot.ScriptContext.load;
import static org.virbo.autoplot.ScriptContext.setCanvasSize;
import static org.virbo.autoplot.ScriptContext.writeToPng;
import static vatest.endtoend.VATestSupport.TEST_VAP;

/**
 * Test Jan's products
 * @author jbf
 */
public class Test004 {
    public static void main(String[] args) {
        try {
            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

            load( TEST_VAP + "merka_celias_test004_v1.04.vap" );
            err.println("#### model pending changes: " + getDocumentModel().getController().isPendingChanges() );
            setCanvasSize( 992, 711 );
            writeToPng( "test004.png" );
            exit(0);  // TODO: something is firing up the event thread
        } catch ( IOException | InterruptedException ex ) {
            ex.printStackTrace();
            exit(1);
        }
    }
}
