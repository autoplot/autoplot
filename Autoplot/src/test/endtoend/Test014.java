
package test.endtoend;

import java.awt.Graphics2D;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.autoplot.ScriptContext2023;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.graph.DasAxis;
import org.das2.graph.DasCanvas;
import org.das2.graph.Painter;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import static org.autoplot.ScriptContext2023.*;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.autoplot.jythonsupport.Util;
import org.das2.datum.Datum;
import org.das2.datum.DatumUtil;
import org.das2.qds.SemanticOps;


/**
 * tests of challenging guess cadence
 * @author jbf
 */
public class Test014 {

    private static final Set<Integer> usedIds= new HashSet<Integer>();

    private static ScriptContext2023 scriptContext= ScriptContext2023.getInstance();
    
    public static void doTest( int id, String uri, QDataSet ds ) throws Exception {

        System.err.println( String.format("== doTest(%d,%s) ==",id,uri ) );

        if ( usedIds.contains(id) ) throw new IllegalArgumentException("id "+id+" used twice, test code needs attention");
        usedIds.add(id);
        
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
        String sunits= cadence==null ? null : String.valueOf( SemanticOps.getUnits(cadence) );
        System.err.printf( "cadence= %s (scale type=%s) (units=%s): \n", String.valueOf(cadence), type, sunits );

        QDataSet diff;
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
        
        // kludge: since in das2 Units.dimensionless is convertable to logERatio, force the units change to something not dimensionless.
        if ( "log".equals(type) ) {
            scriptContext.getDocumentModel().getPlots(0).getXaxis().setRange( DatumRange.newDatumRange( 0, 10, Units.seconds ) );
        }

        scriptContext.plot( hist );
        hist=Ops.copy(hist);
                
        scriptContext.setCanvasSize( 600, 600 );

        final DasCanvas cc= scriptContext.getDocumentModel().getCanvases(0).getController().getDasCanvas();
        DasAxis xAxis= scriptContext.getDocumentModel().getPlots(0).getXaxis().getController().getDasAxis();

        xAxis.setLabel("UNITS=%{UNITS}");

        Painter p = new Painter() {
            @Override
            public void paint(Graphics2D g) {
                DasAxis xAxis= scriptContext.getDocumentModel().getPlots(0).getXaxis().getController().getDasAxis();
                if ( cadence==null ) {
                 
                } else {
                    final int ix= (int)xAxis.transform( DataSetUtil.asDatum(cadence) );
                    g.drawLine( ix, 0, ix, cc.getHeight() );
                }
            }
        };
        cc.addTopDecorator( p );

        int i= uri.lastIndexOf("/");

        scriptContext.setTitle(uri.substring(i+1)+"!c cadence="+cadence );

        scriptContext.writeToPng( String.format( "test014_%03d.png", id ) );

        cc.removeTopDecorator(p);
        
        hist.putProperty( QDataSet.TITLE, uri + "!c cadence="+cadence );
        
        hist.putProperty( QDataSet.LABEL, label );

        hist.putProperty( QDataSet.LABEL, label );
        
        formatDataSet( hist, label+".qds");

    }
    
    public static void main(String[] args) throws InterruptedException, IOException, Exception {
        try {

            System.err.println("== datum formatting experiments ==");
            Datum d= DatumUtil.parse("150.00 ms");
            System.err.println("as millisecond unit: "+ d);
            d= Units.nanoseconds.createDatum(1.50e8);
            System.err.println("as nanoseconds unit: "+ d);
            System.err.println("with asOrderOneUnits call: "+ DatumUtil.asOrderOneUnits(d));
            System.err.println("== done, datum formatting experiments, thanks ==");
            
            
            scriptContext.getDocumentModel().getOptions().setAutolayout(false);
            scriptContext.getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

            QDataSet ds;

            //DataSetUtil.guessCadenceNew(ds, ds)
                    
            ds= Util.getDataSet( "file:///home/jbf/ct/hudson/data/qds/cadence.qds" );
            doTest( 9, "file:///home/jbf/ct/hudson/data/qds/cadence.qds", ds );

            ds= Util.getDataSet( "vap+cdf:file:///home/jbf/ct/jenkins/data/cdf/LANL_LANL-97A_H3_SOPA_20060505_V01.cdf?FEDU" );
            ds= (QDataSet) ds.property( QDataSet.DEPEND_1 );
            doTest( 6, "depend 1 of vap+cdf:file:///home/jbf/ct/jenkins/data/cdf/LANL_LANL-97A_H3_SOPA_20060505_V01.cdf?FEDU", ds );

            ds= Util.getDataSet( "vap+cdf:http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/2000/po_h0_hyd_20000109_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX" );
            ds= DataSetOps.slice0(ds, 10);
            doTest( 4, "slice of Hydra DEF", ds );

            ds= Util.getDataSet( "vap+cdf:file:/home/jbf/ct/hudson/data.backup/cdf/c2_waveform_wbd_200704170840_u01.cdf?WBD_Elec[::1090]" );
            QDataSet ant= Util.getDataSet( "vap+cdf:file:/home/jbf/ct/hudson/data.backup/cdf/c2_waveform_wbd_200704170840_u01.cdf?ANTENNA[::1090]" );
            QDataSet r= Ops.where( Ops.eq( ant, DataSetUtil.asDataSet(2) ) );
            ds= DataSetOps.applyIndex( ds, 0, r, true );
            doTest( 0, "file:/home/jbf/ct/hudson/data.backup/cdf/c2_waveform_wbd_200704170840_u01.cdf?WBD_Elec[::1090]", ds );
            doTest( 1, "file:/home/jbf/ct/hudson/data.backup/cdf/i8_15sec_mag_19731030_v02.cdf?F1_Average_B_15s", null );
            doTest( 2, "file:/home/jbf/ct/hudson/data.backup/xls/2008-lion and tiger summary.xls?sheet=Samantha+tiger+lp+lofreq&firstRow=53&column=Complex_Modulus&depend0=Frequency", null );
            doTest( 3, "file:/home/jbf/ct/hudson/data.backup/dat/cl_ttag_study.dat?column=field0", null );
            doTest( 5, "file:/home/jbf/ct/hudson/data.backup/dat/power.dat.txt", null );

            doTest( 7, "file:/home/jbf/ct/hudson/data.backup/cdf/rbsp-b_WFR-spectral-matrix_emfisis-L1_20121015120844_v1.2.2.cdf?BuBu[0]", null );
            doTest( 8, "file:/home/jbf/ct/hudson/data.backup/dat/apl/jon/electron_events_safings_and_peaks.csv?column=peak_rate&depend0=begin_UTC", null );

            doTest( 10, "file:/home/jbf/ct/hudson/data.backup/cdf/mms1_fpi_brst_l2_dis-dist_20160111063934_v3.1.0.cdf?mms1_dis_dist_brst", null );
            
            doTest( 11, "file:/home/jbf/ct/hudson/data/d2s/testStream.d2s",null); // See /home/jbf/project/das2/u/larry/20180726/
            doTest( 12, "file:/home/jbf/ct/hudson/data/test014/test014_012.qds",null); 
            
            System.exit(0);  // TODO: something is firing up the event thread
            
        } catch ( Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
