/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRangeUtil;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.KeyChain;
import org.das2.util.filesystem.WebFileSystem;
import org.das2.util.monitor.CancelledOperationException;
import org.autoplot.ScriptContext;
import org.autoplot.datasource.DataSetURI;

/**
 * Reiner's stuff, other vap files.
 * @author jbf
 */
public class Test003 {

    public static void doit(int id, String uri) {
        try {
            System.err.println( String.format( "== %03d == ", id ) );
            long t0= System.currentTimeMillis();
            System.err.println("uri: "+uri);
            ScriptContext.load(uri);
            ScriptContext.writeToPng("test003_" + String.format("%03d", id) + ".png");
            System.err.println( String.format( "test003_%03d read in %.2f sec", id, ( System.currentTimeMillis()-t0 )/1000. ) );
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {

        boolean headless = true;
        
        boolean loginBeforeTest= false;
        if ( loginBeforeTest ) {
            try {
                DataSetURI.getExplicitExt("foo.dat"); // trigger initialization of Autoplot settings.
                KeyChain.getDefault().getUserInfo( new URL("http://ectsoc@www.rbsp-ect.lanl.gov/") );
                KeyChain.getDefault().getUserInfo( new URL("http://ectrept@www.rbsp-ect.lanl.gov/") );
                KeyChain.getDefault().getUserInfo( new URL("http://www.rbsp-ect.lanl.gov/") );
                KeyChain.getDefault().getUserInfo( new URL("http://ectmageis@www.rbsp-ect.lanl.gov/") );
                KeyChain.getDefault().getUserInfo( new URL("http://ecthope@www.rbsp-ect.lanl.gov/") );
            } catch (CancelledOperationException ex) {
                TestSupport.logger.log(Level.SEVERE, ex.getMessage(), ex);
            } catch (MalformedURLException ex) {
                TestSupport.logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        
        try {
            if (!headless) {
                ScriptContext.createGui();
            }

            ScriptContext.getDocumentModel().getOptions().setAutolayout(false);
            ScriptContext.getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");
            
            //doit(26, "http://www.rbsp-ect.lanl.gov/data_pub/autoplot/scripts/rbsp_ect-rept-lvt.jyds" );
            doit(26, "http://www.rbsp-ect.lanl.gov/data_pub/autoplot/scripts//rbsp_ect-rept-lvt.jyds?timerange=2012-10-15" );
            //doit(27, "http://www.rbsp-ect.lanl.gov/data_pub/autoplot/scripts/rbsp_ect-mageis-lvt.jyds" );
            doit(27, "http://www.rbsp-ect.lanl.gov/data_pub/autoplot/scripts/rbsp_ect-mageis-lvt.jyds" );
            doit(28, "http://www.rbsp-ect.lanl.gov/data_pub/autoplot/scripts/rbsp_ephem.jyds?R=T&L=T&MLT=T&ILT=T" );
            
            //these require a keychain.txt file.
            doit(9,  "http://ectsoc@www.rbsp-ect.lanl.gov/data_prot/rbspa/mageis/level1/plots/dr-elec-L1/rbspa_rel01_ect-mageis-dr-elec-L1.vap"); //see TODO in keychain
            doit(10, "http://ectsoc@www.rbsp-ect.lanl.gov/data_prot/rbspa/mageis/level1/plots/mr-elec-L1/rbspa_rel01_ect-mageis-mr-elec-L1.vap");
            //doit(11, "http://ectsoc@www.rbsp-ect.lanl.gov/data_prot/rbspa/mageis/level2/plots/sci-L2-elec/rbspa_pre_ect-mageis-sci-L2-elec.vap");

            doit(12, "file:///home/jbf/ct/lanl/rbsp/fixvaps20130423/rbspa_pre_ect-mageis-sci-elec-L2.vap");

 // temporarily disabled 2013-08-08 email from Friedel to faden@cottagesystems.com   doit(13, "http://ectsoc@www.rbsp-ect.lanl.gov/data_prot/rbspa/hope/level1/plots/hk-cem-L1/rbspa_pre_ect-hope-hk-cem-L1.vap");

            //HOPE
//            doit(14, "http://ectsoc@www.rbsp-ect.lanl.gov/data_prot/rbspa/hope/level1/plots/hk-hv-L1/rbspa_pre_ect-hope-hk-hv-L1.vap");
//why twice?            doit(15, "http://ectsoc@www.rbsp-ect.lanl.gov/data_prot/rbspa/hope/level1/plots/hk-tv-L1/rbspa_pre_ect-hope-hk-tv-L1.vap");

//            doit(17, "http://ectsoc@www.rbsp-ect.lanl.gov/data_prot/rbspa/hope/level1/plots/sci-cnts2-L1/rbspa_pre_ect-hope-sci-cnts2-L1.vap");

            //doit(16, "http://ectsoc@www.rbsp-ect.lanl.gov/data_prot/rbspa/hope/level2/plots/sci-L2/rbspa_rel01_ect-hope-sci-L2.vap");
            doit(16, "file:///home/jbf/ct/hudson/vap/lanl/rbspa_pre_ect-hope-sci-L2.vap");
            //doit(16, "http://ectsoc@www.rbsp-ect.lanl.gov/data_prot/rbspa/hope/level1/plots/sci-cnts-L1/rbspa_pre_ect-hope-sci-cnts-L1.vap");
            //doit(18, "http://ectsoc@www.rbsp-ect.lanl.gov/data_prot/rbspa/hope/level2/plots/sci-L2/rbspa_pre_ect-hope-sci-L2.vap?timerange=20121101");

            //REPT
            doit(19, "file:///home/jbf/ct/hudson/vap/lanl/rbspa_rel01_ect-rept-hk-bias-L1.vap?timerange=20121202");
            doit(20, "http://ectsoc@www.rbsp-ect.lanl.gov/data_prot/rbspa/rept/level1/plots/hk-fc-L1/rbspa_rel01_ect-rept-hk-fc-L1.vap?timerange=20120918");
            doit(21, "http://ectsoc@www.rbsp-ect.lanl.gov/data_prot/rbspa/rept/level1/plots/hk-tvc-L1/rbspa_rel01_ect-rept-hk-tvc-L1.vap?timerange=20120919");

            doit(22, "http://ectsoc@www.rbsp-ect.lanl.gov/data_prot/rbspa/rept/level1/plots/sci-all-L1/rbspa_rel01_ect-rept-sci-all-L1.vap?timerange=20121004");

            //REPT L24            doit(23, "http://ectsoc@www.rbsp-ect.lanl.gov/data_prot/rbspa/rept/level2/plots/sci-L2_orbit/rbspa_pre_ect-rept-sci-L2.vap?timerange=20121010");
            //doit(24, "http://ectsoc@www.rbsp-ect.lanl.gov/data_prot/rbspa/rept/level2/plots/sci-elec-L2/rbspa_pre_ect-rept-sci-elec-L2.vap");
            doit(24, "file:///home/jbf/ct/lanl/rbsp/fixvaps20130423/rbspa_pre_ect-rept-sci-elec-L2.vap");
            doit(25, "file:///home/jbf/ct/lanl/rbsp/fixvaps20130423/rbspa_pre_ect-rept-sci-ion-L2.vap");
            
            
            ScriptContext.getDocumentModel().getCanvases(0).setFitted(false);
            ScriptContext.setCanvasSize(800, 600);

            ScriptContext.load("file:///home/jbf/ct/hudson/vap/geo_pitch_stack_test1_local_1.vap");
            ScriptContext.setCanvasSize(800, 600);
            ScriptContext.writeToPng("test003_001.png");

            ScriptContext.reset();

            ScriptContext.load("file:///home/jbf/ct/hudson/vap/contextOverview2_v1.03a.vap");
            ScriptContext.setCanvasSize(800, 600);
            ScriptContext.writeToPng("test003_002.png");

            ScriptContext.reset();

            ScriptContext.load("file:///home/jbf/ct/hudson/vap/lanl_97A_sopa_panel_slices_3_v1.03a.vap");
            ScriptContext.setCanvasSize(800, 600);
            ScriptContext.writeToPng("test003_003.png");

            ScriptContext.reset();

            ScriptContext.load("file:///home/jbf/ct/hudson/vap/energyCompareHydra.vap");
            ScriptContext.writeToPng("test003_004.png");

            ScriptContext.getDocumentModel().getPlots(0).getXaxis().setRange(DatumRangeUtil.parseTimeRangeValid("2000-01-09 10:00 to 12:00"));
            ScriptContext.writeToPng("test003_004a.png");

            ScriptContext.reset();

            ScriptContext.load("file:///home/jbf/ct/hudson/vap/lanl/cpaRichHeaders.vap");
            ScriptContext.writeToPng("test003_006.png");

            //ScriptContext.reset();

            //ScriptContext.load( "http://sarahandjeremy.net/~jbf/autoplot/data/gz/test_rank1.qds" ); // the .gz version of this file exists.  Autoplot should find and use it.
            //ScriptContext.writeToPng( "test003_007.png" );

            ScriptContext.reset();
            ScriptContext.load("http://cdaweb.gsfc.nasa.gov/istp_public/data/crres/particle_mea/mea_h0_cdaweb/$Y/crres_h0_mea_$Y$m$(d,span=10)_v01.cdf?B&timerange=1991-01-15");
            ScriptContext.writeToPng("test003_008.png");

            System.err.println("Local Cache Dir: "+FileSystem.settings().getLocalCacheDir());
            FileSystem[] fss= FileSystem.peekInstances();
            for ( FileSystem fs: fss ) {
                if ( fs instanceof WebFileSystem ) {
                    System.err.println( fs.toString() + ":\t" + ( ((WebFileSystem)fs).isOffline() ? "offline" : "ok" ) );
                }
            }
                    
            if (headless) {
                System.exit(0);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            if (headless) {
                System.exit(1);
            }
        }
    }
}
