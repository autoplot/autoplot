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


    public static void doTest( int id, String uri ) throws Exception {
        QDataSet ds;
        long t0= System.currentTimeMillis();
        ds= Util.getDataSet( uri );
        double t= (System.currentTimeMillis()-t0)/1000.;
        MutablePropertyDataSet hist= (MutablePropertyDataSet) Ops.autoHistogram(ds);
        hist.putProperty( QDataSet.TITLE, uri );
        String label= String.format( "test012_%03d", id );
        hist.putProperty( QDataSet.LABEL, label );
        formatDataSet( hist, label+".qds");

        QDataSet dep0= (QDataSet) ds.property( QDataSet.DEPEND_0 );
        if ( dep0!=null ) {
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
        setTitle(uri.substring(i+1));
        writeToPng( String.format( "test012_%03d.png", id ) );

        System.err.printf( "Read in %9.3f seconds (%s): %s\n", t, label, uri );
    }
    
    public static void main(String[] args) throws InterruptedException, IOException, Exception {
        try {

            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

            doTest( 0, "vap:http://cdaweb.gsfc.nasa.gov/istp_public/data/cluster/c4/cp/2003/c4_cp_fgm_spin_20030102_v01.cdf?B_vec_xyz_gse__C4_CP_FGM_SPIN" );
            doTest( 1, "vap:file:///home/jbf/ct/hudson/data/cdf/tha_l1_efw_20080402_v01.cdf?tha_efw" );
            doTest( 2, "vap:file:///home/jbf/ct/hudson/data/cdf/l1_h0_mpa_20020202_v02.cdf?dens_e" );
            doTest( 3, "vap:file:///home/jbf/ct/hudson/data/cdf/l1_h0_mpa_20020202_v02.cdf?Theta_l" );
            //rank4
            doTest( 4, "vap:file:///home/jbf/ct/hudson/data/cdf/l1_h0_mpa_20020202_v02.cdf?Ecounts[1]" );

            // cdf_epoch16, subsetting.
            doTest( 5, "vap:file:///home/jbf/ct/hudson/data/cdf/c2_waveform_wbd_200704170840_u01.cdf?WBD_Elec[1000000:1100000]" );
            // uint1, subsetting.
            doTest( 6, "vap:file:///home/jbf/ct/hudson/data/cdf/c2_waveform_wbd_200704170840_u01.cdf?DATA_QUALITY[::1090]" );

            doTest( 7, "vap:file:///home/jbf/ct/hudson/data/cdf/i8_15sec_mag_19731030_v02.cdf?F1_Average_B_15s" );
            doTest( 8, "vap:file:///home/jbf/ct/hudson/data/cdf/i8_15sec_mag_19731030_v02.cdf?B_Vector_GSM" );

            doTest( 9, "vap:file:///home/jbf/ct/hudson/data/cdf/wi_h0_mfi_19941123_v04.cdf?P1GSM" );
            doTest( 10, "vap:file:///home/jbf/ct/hudson/data/cdf/wi_h0_mfi_19941123_v04.cdf?ORTH_I" );

            doTest( 11, "vap:file:///home/jbf/ct/hudson/data/cdf/po_h0_hyd_20010117_v01.cdf?ION_DIFFERENTIAL_ENERGY_FLUX" );

            // rank 3
            doTest( 12, "vap:file:///home/jbf/ct/hudson/data/cdf/po_h0_tim_19960409_v03.cdf?Flux_H" );


            System.exit(0);  // TODO: something is firing up the event thread
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
