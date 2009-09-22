/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.io.IOException;
import static org.virbo.autoplot.ScriptContext.*;

/**
 * Reiner's stuff
 * @author jbf
 */
public class Test003 {
    public static void main(String[] args) throws InterruptedException, IOException, Exception {

        boolean headless= true;

        try {
            if ( !headless ) createGui();
            
            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");
            getDocumentModel().getCanvases(0).setFitted(false);
            setCanvasSize( 800, 600 );
            
            load( "file:///home/jbf/ct/lanl/hudson/geo_pitch_stack_test1_local.vap" );
            setCanvasSize( 800, 600 );
            writeToPng( "test003_001.png" );
            save( "/home/jbf/tmp/test003_001.vap");

            reset();
            
            load( "file:///home/jbf/ct/hudson/vap/contextOverview2.vap" );
            setCanvasSize( 800, 600 );
            writeToPng( "test003_002.png" );
            save( "/home/jbf/tmp/test003_002.vap");

            reset();

            load( "file:///home/jbf/ct/hudson/vap/lanl_97A_sopa_panel_slices_2.vap" );
            setCanvasSize( 800, 600 );
            writeToPng( "test003_003.png" );
            save( "/home/jbf/tmp/test003_003.vap");

            if ( headless ) System.exit(0);
        } catch ( RuntimeException ex ) {
            ex.printStackTrace();
            if ( headless ) System.exit(1);
        }
    }
}
