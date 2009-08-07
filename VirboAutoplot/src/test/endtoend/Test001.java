/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.io.IOException;
import org.virbo.autoplot.ScriptContext;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;
import org.virbo.jythonsupport.Util;

/**
 *
 * @author jbf
 */
public class Test001 {
    public static void main(String[] args) throws InterruptedException, IOException, Exception {
        try {
            ScriptContext.getDocumentModel().getOptions().setAutolayout(false);
            ScriptContext.getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");
            QDataSet ds= Util.getDataSet( "http://www.autoplot.org/data/fireworks.wav" );
            ScriptContext.plot( 0, ds );
            ScriptContext.plot( 1, Ops.fftWindow( ds, 512 ) );
            ScriptContext.setCanvasSize( 800, 600 );
            ScriptContext.writeToPng( "test001.png" );
            System.exit(0);  // TODO: something is firing up the event thread
        } catch ( RuntimeException ex ) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
