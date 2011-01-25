/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.io.IOException;
import org.virbo.autoplot.*;

/**
 * Reiner's stuff
 * @author jbf
 */
public class Test003 {
    public static void main(String[] args)  {

        boolean headless= true;

        try {
            if ( !headless ) ScriptContext.createGui();
            
            ScriptContext.getDocumentModel().getOptions().setAutolayout(false);
            ScriptContext.getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");
            ScriptContext.getDocumentModel().getCanvases(0).setFitted(false);
            ScriptContext.setCanvasSize( 800, 600 );
            
            ScriptContext.load( "file:///home/jbf/ct/hudson/vap/geo_pitch_stack_test1_local_1.vap" );
            ScriptContext.setCanvasSize( 800, 600 );
            ScriptContext.writeToPng( "test003_001.png" );

            ScriptContext.reset();
            
            ScriptContext.load( "file:///home/jbf/ct/hudson/vap/contextOverview2_v1.03a.vap" );
            ScriptContext.setCanvasSize( 800, 600 );
            ScriptContext.writeToPng( "test003_002.png" );

            ScriptContext.reset();

            ScriptContext.load( "file:///home/jbf/ct/hudson/vap/lanl_97A_sopa_panel_slices_3_v1.03a.vap" );
            ScriptContext.setCanvasSize( 800, 600 );
            ScriptContext.writeToPng( "test003_003.png" );

            if ( headless ) System.exit(0);
            
        } catch ( Exception ex ) {
            ex.printStackTrace();
            if ( headless ) System.exit(1);
        }
    }
}
