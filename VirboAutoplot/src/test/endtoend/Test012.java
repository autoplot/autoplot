/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import java.io.PrintWriter;
import static org.virbo.autoplot.ScriptContext.*;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;
import org.virbo.jythonsupport.Util;

/**
 * tests of CDF files.
 * @author jbf
 */
public class Test012 {
    private static int testid;


    public static void doTest( int testid, int id, String uri ) throws Exception {
        QDataSet ds;
        long t0= System.currentTimeMillis();

        System.err.printf( String.format( "== %03d %s ==\n", id, uri ) );

        ds= Util.getDataSet( uri );
        double t= (System.currentTimeMillis()-t0)/1000.;
        MutablePropertyDataSet hist= (MutablePropertyDataSet) Ops.autoHistogram(ds);
        hist.putProperty( QDataSet.TITLE, uri );

        String label= String.format( "test%03d_%03d", testid, id );
        hist.putProperty( QDataSet.LABEL, label );
        formatDataSet( hist, label+".qds");

        QDataSet dep0= (QDataSet) ds.property( QDataSet.DEPEND_0 );
        if ( dep0!=null ) {
            if ( Ops.total( Ops.valid(dep0) ) == 0 ) {
                Ops.total( Ops.valid(dep0) ) ;
                throw new IllegalArgumentException("no valid DEPEND_0");
            }
            MutablePropertyDataSet hist2= (MutablePropertyDataSet) Ops.autoHistogram(dep0);
            formatDataSet( hist2, label+".dep0.qds");
        } else {
            PrintWriter pw= new PrintWriter( label+".dep0.qds" );
            pw.println("no dep0");
            pw.close();
        }

        plot( ds );
        setCanvasSize( 750, 300 );
        int i= uri.lastIndexOf("/");

        getApplicationModel().waitUntilIdle(true);

        String fileUri= uri.substring(i+1);

        if ( !getDocumentModel().getPlotElements(0).getComponent().equals("") ) {
            String dsstr= String.valueOf( getDocumentModel().getDataSourceFilters(0).getController().getDataSet() );
            fileUri= fileUri + " " + dsstr +" " + getDocumentModel().getPlotElements(0).getComponent();
        }

        setTitle(fileUri);
        writeToPng( String.format( "test%03d_%03d.png", testid, id ) );

        System.err.printf( "Read in %9.3f seconds (%s): %s\n", t, label, uri );
    }
    
    public static void main(String[] args) {
        try {

            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

            testid = args.length==1 ? Integer.parseInt(args[0]) : 12; // support running test with java reader test032

            //TODO: test012_000 shows a potential bug with autoHistogram: zero-point average is formatted to "2292-04-10T00:12:43...."
            doTest( testid,0, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/c4_cp_fgm_spin_20030102_v01.cdf?B_vec_xyz_gse__C4_CP_FGM_SPIN" );

            doTest( testid,1, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/tha_l1_efw_20080402_v01.cdf?tha_efw" );
            doTest( testid,2, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/l1_h0_mpa_20020202_v02.cdf?dens_e" );
            doTest( testid,3, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/l1_h0_mpa_20020202_v02.cdf?Theta_l" );
            //rank4
            doTest( testid,4, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/l1_h0_mpa_20020202_v02.cdf?Ecounts[1]" );

            // cdf_epoch16, subsetting.
            doTest( testid,5, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/c2_waveform_wbd_200704170840_u01.cdf?WBD_Elec[1000000:1100000]" );
            // uint1, subsetting.
            doTest( testid,6, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/c2_waveform_wbd_200704170840_u01.cdf?DATA_QUALITY[::1090]" );

            doTest( testid,7, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/i8_15sec_mag_19731030_v02.cdf?F1_Average_B_15s" );
            doTest( testid,8, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/i8_15sec_mag_19731030_v02.cdf?B_Vector_GSM" );

            doTest( testid,9, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/wi_h0_mfi_19941123_v04.cdf?P1GSM" );
            doTest( testid,10, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/wi_h0_mfi_19941123_v04.cdf?ORTH_I" );

            doTest( testid,11, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/po_h0_hyd_20010117_v01.cdf?ION_DIFFERENTIAL_ENERGY_FLUX" );

            // rank 3
            doTest( testid, 12, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/po_h0_tim_19960409_v03.cdf?Flux_H" );

            // recursion bug https://sourceforge.net/tracker/index.php?func=detail&aid=2981336&group_id=199733&atid=970682
            doTest( testid,13, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/tha_l1_fgm_20100101_v01.cdf?tha_fgh[0:10000]" );

            // twins data uses extra DEPEND_x variables.
            doTest( testid,14, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/twins/twins1_l1_imager_2009011102_v01.cdf?smooth_image&interpMeta=no" );

            // this fast ees has rank 2 DEPEND_1.
            doTest( testid,15, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/fa_k0_ees_19980111_v01.cdf?el_0" );

            // this should slice by default on the enumeration (labels) dimension.
            doTest( testid,16, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/tha_l1_ffp_32_20090101_v01.cdf?tha_ffp_32" );

            // every other value is fill in Virbo data.  These should be skipped.
            doTest( testid, 17, "file:///home/jbf/ct/hudson/data.backup/cdf/virbo/poes_n17_20041228.cdf?P1_90[0:300]" );

            // this has a non-varying second dimension that is automatically removed.
            doTest( testid, 18, "ftp://cdaweb.gsfc.nasa.gov/pub/istp/canopus/asi/1999/cn_k0_asi_19991230_v01.cdf?Image" );

            // this is a hugeScatter.
            doTest( testid, 19, "/home/jbf/ct/hudson/data.backup/cdf/pw/rbsp/rbsp-a_magnetometer_emfisis-L1_20120904_v1.2.2.cdf?Mag_UVW[0:100000]" );

            // this is a hugeScatter.  4,072,000 points.
            doTest( testid, 20, "/home/jbf/ct/hudson/data.backup/cdf/pw/rbsp/rbsp-a_magnetometer_emfisis-L1_20120904_v1.2.2.cdf?Mag_UVW" );

            System.exit(0);  // TODO: something is firing up the event thread
        } catch ( Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
