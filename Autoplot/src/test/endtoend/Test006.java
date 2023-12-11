/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import java.io.PrintWriter;
import static org.autoplot.ScriptContext2023.*;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.autoplot.jythonsupport.Util;

/**
 * Misc tests
 * @author jbf
 */
public class Test006 {


    public static Exception doTest( int id, String uri, Exception ex ) throws Exception {
        try {
            QDataSet ds;
            long t0= System.currentTimeMillis();
            ds= Util.getDataSet( uri );
            double t= (System.currentTimeMillis()-t0)/1000.;
            MutablePropertyDataSet hist= (MutablePropertyDataSet) Ops.autoHistogram(ds);
            hist.putProperty( QDataSet.TITLE, uri );
            String label= String.format( "test006_%03d", id );
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

            System.err.printf( "Read in %9.3f seconds (%s): %s\n", t, label, uri );
            } catch ( Exception e ) {
                e.printStackTrace();
                if ( ex!=null ) ex= e;
            }
            return ex;
    }
    
    public static void main(String[] args)  {
        try {

            Exception e=null; // FINDBUGS okay
            e= doTest( 0, "ftp://spdf.gsfc.nasa.gov/pub/data/omni/low_res_omni/omni2_$Y.dat?column=field17&timerange=1963&timeFormat=$Y+$j+$H&time=field0&validMax=999", e );
            //e= doTest( 0, "ftp://nssdcftp.gsfc.nasa.gov/spacecraft_data/omni/omni2_$Y.dat?column=field17&timerange=1963&timeFormat=$Y+$j+$H&time=field0&validMax=999", e );
            e= doTest( 1, "vap+dat:file:///home/jbf/ct/hudson/data.backup/dat/A1050412.TXT?skip=23&timeFormat=$y$m$d+$H$M&column=E1&time=YYMMDD", e );
            //e= doTest( 1, "http://goes.ngdc.noaa.gov/data/avg/2004/A1050412.TXT?skip=23&timeFormat=$y$m$d+$H$M&column=E1&time=YYMMDD", e );
            e= doTest( 2, "vap+dat:file:///home/jbf/ct/hudson/data.backup/dat/V11979066.CSV?depend0=field0&column=field13", e );
            e= doTest( 3, "file:///home/jbf/ct/hudson/data.backup/dat/power.dat.txt", e );  //case where guess cadence is failing, though it shouldn't.
            e= doTest( 4, "vap+csv:file:///home/jbf/ct/hudson/data.backup/dat/V11979066.CSV?depend0=field0&column=field13", e );

            //doTest( 2, "vap:http://vho.nasa.gov/mission/soho/celias_pm_30sec/1998.txt?time=YY&column=GSE_X&timeFormat=$y+$b+$d+$(ignore):$H:$M:$S" );

            if ( e!=null ) {
                throw e;
            }
            
            System.exit(0);  // TODO: something is firing up the event thread
        } catch ( Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
