/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

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
            ScriptContext.getDocumentModel().getOptions().setAutolayout(false);
            ScriptContext.getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

            ScriptContext.load( "/home/jbf/ct/hudson/vap/merka_celias_test004_v1.04.vap" );
            System.err.println( "#### model pending changes: " + ScriptContext.getDocumentModel().getController().isPendingChanges() );
            ScriptContext.setCanvasSize( 992, 711 );
            ScriptContext.writeToPng( "test004.png" );
            System.exit(0);  // TODO: something is firing up the event thread
        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
