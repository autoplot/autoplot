/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import org.autoplot.pngwalk.CreatePngWalk;
import org.virbo.autoplot.ScriptContext;
import org.virbo.autoplot.dom.Application;
import java.io.File;
import org.das2.datum.DatumRangeUtil;


/**
 * Test pngwalk tool generation and tool.
 * @author jbf
 */
public class Test033 {


    private static void makePngWalk1() throws Exception {
        Application dom= ScriptContext.getDocumentModel();

        String pwd= new File("pngwalk").getAbsoluteFile().toString();

        CreatePngWalk.Params pp= new CreatePngWalk.Params();
        pp.outputFolder= pwd;
        pp.product= "product";
        pp.timeFormat= "$Y$m$d";
        pp.timeRangeStr= "2006-dec-2 to 2006-dec-22";

        ScriptContext.load( "file:/home/jbf/ct/hudson/vap/cassini_kp.vap" );

        System.err.println("writing pngwalk at "+pwd );
        CreatePngWalk.doIt( dom, pp );

    }

    /**
     * another pngwalk on top of first.
     * @throws Exception
     */
    private static void makePngWalk2() throws Exception {
        Application dom= ScriptContext.getDocumentModel();

        String pwd= new File("pngwalk").getAbsoluteFile().toString();

        CreatePngWalk.Params pp= new CreatePngWalk.Params();
        pp.outputFolder= pwd;
        pp.product= "product2";
        pp.timeFormat= "$Y$m$d-$(H,span=2)";
        pp.timeRangeStr= "2006-dec-2 to 2006-dec-5";

        ScriptContext.load( "file:/home/jbf/ct/hudson/vap/cassini_kp.vap" );

        System.err.println("writing pngwalk at "+pwd );
        CreatePngWalk.doIt( dom, pp );

    }

    /**
     * ascii files.
     * @throws Exception
     */
    private static void makePngWalk3() throws Exception {
        Application dom= ScriptContext.getDocumentModel();

        String pwd= new File("pngwalkAscii").getAbsoluteFile().toString();

        CreatePngWalk.Params pp= new CreatePngWalk.Params();
        pp.outputFolder= pwd;
        pp.product= "product3";
        pp.timeFormat= "$Y$m";
        pp.timeRangeStr= "2005 through 2007";

        ScriptContext.load( "file:///home/jbf/ct/hudson/vap/kp_dst.vap" );

        System.err.println("writing pngwalk at "+pwd );
        CreatePngWalk.doIt( dom, pp );

    }
    
    /**
     * pngwalk where context is used to show one slice vs another.
     * @throws Exception 
     */
    private static void makePngWalk5() throws Exception {
        Application dom= ScriptContext.getDocumentModel();

        String pwd= new File("pngwalk5").getAbsoluteFile().toString();

        CreatePngWalk.Params pp= new CreatePngWalk.Params();
        pp.outputFolder= pwd;
        pp.product= "product5";
        pp.timeFormat= "$Y$m$d-$(H,span=6)";
        pp.timeRangeStr= "1984-01-14 through 1984-01-23";

        ScriptContext.load( "file:/home/jbf/ct/hudson/vap/lanl/lanlGeoEpDemo4.vap" );

        dom.getPlots(1).getXaxis().setRange( DatumRangeUtil.parseTimeRange("1984-01-14 through 1984-01-23") );
        System.err.println("writing pngwalk at "+pwd );
        CreatePngWalk.doIt( dom, pp );
    }


    /**
     * new features for pngwalk from https://sourceforge.net/tracker/index.php?func=detail&aid=2984095&group_id=199733&atid=970685
     * include context
     * @throws Exception
     */
    private static void makePngWalk4() throws Exception {
        Application dom= ScriptContext.getDocumentModel();

        String pwd= new File("pngwalk4").getAbsoluteFile().toString();

        CreatePngWalk.Params pp= new CreatePngWalk.Params();
        pp.outputFolder= pwd;
        pp.product= "product4";
        pp.timeFormat= "$Y$m";
        pp.timeRangeStr= "2005 through 2007";
        pp.rescalex="-300%,400%";
        pp.autorange= true;
        pp.version= "v1.2";

        ScriptContext.load( "file:///home/jbf/ct/hudson/vap/kp_dst.vap" );

        System.err.println("writing pngwalk at "+pwd );
        CreatePngWalk.doIt( dom, pp );

    }


    public static void main( String[] args ) throws Exception {

        long t0;

        ScriptContext.getDocumentModel().getOptions().setAutolayout(false);
        
        t0= System.currentTimeMillis();
        makePngWalk1();
        System.err.printf( "test 001: done in %9.2f sec\n", ( System.currentTimeMillis()-t0 ) / 1000. );

        t0= System.currentTimeMillis();
        makePngWalk2();
        System.err.printf( "test 002: done in %9.2f sec\n", ( System.currentTimeMillis()-t0 ) / 1000. );

        t0= System.currentTimeMillis();
        makePngWalk3();
        System.err.printf( "test 003: done in %9.2f sec\n", ( System.currentTimeMillis()-t0 ) / 1000. );

        t0= System.currentTimeMillis();
        makePngWalk4();
        System.err.printf( "test 004: done in %9.2f sec\n", ( System.currentTimeMillis()-t0 ) / 1000. );

        t0= System.currentTimeMillis();
        makePngWalk5();
        System.err.printf( "test 005: done in %9.2f sec\n", ( System.currentTimeMillis()-t0 ) / 1000. );
        
        System.exit(0);
    }
}
