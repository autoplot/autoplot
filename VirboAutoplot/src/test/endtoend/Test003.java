/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import org.das2.datum.DatumRangeUtil;
import org.virbo.autoplot.ScriptContext;

/**
 * Reiner's stuff, other vap files.
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

            ScriptContext.reset();

            ScriptContext.load( "file:///home/jbf/ct/hudson/vap/energyCompareHydra.vap" );
            ScriptContext.writeToPng( "test003_004.png" );

            ScriptContext.getDocumentModel().getPlots(0).getXaxis().setRange( DatumRangeUtil.parseTimeRangeValid("2000-01-09 10:00 to 12:00") );
            ScriptContext.writeToPng( "test003_004a.png" );

            ScriptContext.reset();

            ScriptContext.load( "file:///home/jbf/ct/hudson/vap/lanl/cpaRichHeaders.vap" );
            ScriptContext.writeToPng( "test003_006.png" );

            //ScriptContext.reset();

            //ScriptContext.load( "http://sarahandjeremy.net/~jbf/autoplot/data/gz/test_rank1.qds" ); // the .gz version of this file exists.  Autoplot should find and use it.
            //ScriptContext.writeToPng( "test003_007.png" );
            
            ScriptContext.reset();
            ScriptContext.load( "http://cdaweb.gsfc.nasa.gov/istp_public/data/crres/mea/$Y/crres_h0_mea_$Y$m$(d,span=10)_v01.cdf?B&timerange=1991-01-15" );
            ScriptContext.writeToPng( "test003_008.png" );

            if ( headless ) System.exit(0);
            
        } catch ( Exception ex ) {
            ex.printStackTrace();
            if ( headless ) System.exit(1);
        }
    }
}
