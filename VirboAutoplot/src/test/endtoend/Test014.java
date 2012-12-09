/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.awt.Graphics2D;
import java.io.IOException;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.graph.DasAxis;
import org.das2.graph.DasCanvas;
import org.das2.graph.Painter;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import static org.virbo.autoplot.ScriptContext.*;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.RankZeroDataSet;
import org.virbo.dsops.Ops;
import org.virbo.jythonsupport.Util;


/**
 * tests of challenging guess cadence
 * @author jbf
 */
public class Test014 {


    public static void doTest( int id, String uri, QDataSet ds ) throws Exception {
     
        long t0= System.currentTimeMillis();
        String label= String.format( "test014_%03d", id );
        double t;

        if ( ds==null ) ds= Util.getDataSet( uri );

        QDataSet dep0= (QDataSet) ds.property( QDataSet.DEPEND_0 );

        t= (System.currentTimeMillis()-t0)/1000.;
        System.err.printf( "Read data in %9.3f seconds (%s): %s\n", t, label, uri );

        if ( dep0==null ) dep0= ds;

        final QDataSet cadence= DataSetUtil.guessCadenceNew( dep0, ds );
        
        t= (System.currentTimeMillis()-t0)/1000.;
        System.err.printf( "Guess cadence in %9.3f seconds (%s): %s\n", t, label, uri );
        String type= cadence==null ? null : (String) cadence.property(QDataSet.SCALE_TYPE);
        System.err.printf( "cadence= %s (scale type=%s): \n", String.valueOf(cadence), type  );

        QDataSet diff= Ops.diff(dep0);
        if ( ds.rank()==1 && dep0.rank()==1 ) { // ftp://virbo.org/tmp/poes_n17_20041228.cdf?P1_90[0:300] has every other value=fill.
            QDataSet r= Ops.where( Ops.valid(ds) );
            diff=  Ops.diff( DataSetOps.applyIndex( dep0, 0, r, false ) );
        } else {
            diff=  Ops.diff( dep0 );
        }
        System.err.printf( "diffs(dep0)[0:3]= %f %f %f %s\n",
                diff.value(0), diff.value(1), diff.value(2),
                String.valueOf(diff.property(QDataSet.UNITS)) );
        MutablePropertyDataSet hist;
        if ( "log".equals( type ) ) {
            hist= (MutablePropertyDataSet) Ops.autoHistogram( Ops.diff( Ops.log(dep0) ) );
            ((MutablePropertyDataSet)hist.property(QDataSet.DEPEND_0)).putProperty( QDataSet.UNITS, Units.logERatio );
        } else {
            hist= (MutablePropertyDataSet) Ops.autoHistogram( diff );
        }

        DasAxis xAxis= getDocumentModel().getPlots(0).getXaxis().getController().getDasAxis();
        
        // kludge: since in das2 Units.dimensionless is convertable to logERatio, force the units change to something not dimensionless.
        if ( "log".equals(type) ) {
            getDocumentModel().getPlots(0).getXaxis().setRange( DatumRange.newDatumRange( 0, 10, Units.seconds ) );
        }

        plot( hist );
        setCanvasSize( 600, 600 );

        final DasCanvas cc= getDocumentModel().getCanvases(0).getController().getDasCanvas();
        xAxis= getDocumentModel().getPlots(0).getXaxis().getController().getDasAxis();

        xAxis.setLabel("UNITS=%{UNITS}");

        Painter p = new Painter() {
            public void paint(Graphics2D g) {
                DasAxis xAxis= getDocumentModel().getPlots(0).getXaxis().getController().getDasAxis();
                if ( cadence==null ) {
                 
                } else {
                    final int ix= (int)xAxis.transform( DataSetUtil.asDatum((RankZeroDataSet)cadence) );
                    g.drawLine( ix, 0, ix, cc.getHeight() );
                }
            }
        };
        cc.addTopDecorator( p );

        int i= uri.lastIndexOf("/");

        setTitle(uri.substring(i+1)+"!c cadence="+cadence );

        writeToPng( String.format( "test014_%03d.png", id ) );

        cc.removeTopDecorator(p);
        
        hist.putProperty( QDataSet.TITLE, uri + "!c cadence="+cadence );
        
        hist.putProperty( QDataSet.LABEL, label );

        hist.putProperty( QDataSet.LABEL, label );
        
        formatDataSet( hist, label+".qds");

    }
    
    public static void main(String[] args) throws InterruptedException, IOException, Exception {
        try {

            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

            QDataSet ds;

            ds= Util.getDataSet( "vap:file:///home/jbf/ct/lanl/hudson/LANL_LANL-97A_H3_SOPA_20060505_V01.cdf?FEDU" );
            ds= (QDataSet) ds.property( QDataSet.DEPEND_1 );
            doTest( 6, "depend 1 of vap:file:///home/jbf/ct/lanl/hudson/LANL_LANL-97A_H3_SOPA_20060505_V01.cdf?FEDU", ds );

            ds= Util.getDataSet( "vap+cdf:http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/2000/po_h0_hyd_20000109_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX" );
            ds= DataSetOps.slice0(ds, 10);
            doTest( 4, "slice of Hydra DEF", ds );

            ds= Util.getDataSet( "vap:file:/home/jbf/ct/hudson/data.backup/cdf/c2_waveform_wbd_200704170840_u01.cdf?WBD_Elec[::1090]" );
            QDataSet ant= Util.getDataSet( "vap:file:/home/jbf/ct/hudson/data.backup/cdf/c2_waveform_wbd_200704170840_u01.cdf?ANTENNA[::1090]" );
            QDataSet r= Ops.where( Ops.eq( ant, DataSetUtil.asDataSet(2) ) );
            ds= DataSetOps.applyIndex( ds, 0, r, true );
            doTest( 0, "file:/home/jbf/ct/hudson/data.backup/cdf/c2_waveform_wbd_200704170840_u01.cdf?WBD_Elec[::1090]", ds );
            doTest( 1, "file:/home/jbf/ct/hudson/data.backup/cdf/i8_15sec_mag_19731030_v02.cdf?F1_Average_B_15s", null );
            doTest( 2, "file:/home/jbf/ct/hudson/data.backup/xls/2008-lion and tiger summary.xls?sheet=Samantha+tiger+lp+lofreq&firstRow=53&column=Complex_Modulus&depend0=Frequency", null );
            doTest( 3, "file:/home/jbf/ct/hudson/data.backup/dat/cl_ttag_study.dat?column=field0", null );
            doTest( 5, "file:/home/jbf/ct/hudson/data.backup/dat/power.dat.txt", null );

            doTest( 7, "file:/home/jbf/ct/hudson/data.backup/cdf/rbsp-b_WFR-spectral-matrix_emfisis-L1_20121015120844_v1.2.2.cdf?BuBu[0]", null );
            doTest( 8, "file:/home/jbf/ct/hudson/data.backup/dat/apl/jon/electron_events_safings_and_peaks.csv?column=peak_rate&depend0=begin_UTC", null );

            System.exit(0);  // TODO: something is firing up the event thread
        } catch ( Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
