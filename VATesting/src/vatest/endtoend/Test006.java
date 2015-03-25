/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package vatest.endtoend;

import java.io.PrintWriter;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.err;
import static java.lang.System.exit;
import static org.virbo.autoplot.ScriptContext.*;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import static org.virbo.dataset.QDataSet.DEPEND_0;
import static org.virbo.dataset.QDataSet.LABEL;
import static org.virbo.dataset.QDataSet.TITLE;
import static org.virbo.dsops.Ops.autoHistogram;
import static org.virbo.jythonsupport.Util.getDataSet;

/**
 * Misc tests
 * @author jbf
 */
public class Test006 {


    public static Exception doTest( int id, String uri, Exception ex ) throws Exception {
        try {
            QDataSet ds;
            long t0= currentTimeMillis();
            ds= getDataSet( uri );
            double t= (currentTimeMillis()-t0)/1000.;
            MutablePropertyDataSet hist= (MutablePropertyDataSet) autoHistogram(ds);
            hist.putProperty(TITLE, uri );
            String label= format( "test006_%03d", id );
            hist.putProperty(LABEL, label );
            formatDataSet( hist, label+".qds");

            QDataSet dep0= (QDataSet) ds.property(DEPEND_0);
            if ( dep0!=null ) {
                MutablePropertyDataSet hist2= (MutablePropertyDataSet) autoHistogram(dep0);
                formatDataSet( hist2, label+".dep0.qds");
            } else {
                try (PrintWriter pw = new PrintWriter( label+".dep0.qds" )) {
                    pw.println("no dep0");
                }
            }

            err.printf( "Read in %9.3f seconds (%s): %s\n", t, label, uri );
            } catch ( Exception e ) {
                e.printStackTrace();
                if ( ex!=null ) ex= e;
            }
            return ex;
    }
    
    public static void main(String[] args)  {
        try {

            Exception e=null;
            e= doTest( 0, "ftp://spdf.gsfc.nasa.gov/pub/data/omni/low_res_omni/omni2_$Y.dat?column=field17&timerange=1963&timeFormat=$Y+$j+$H&time=field0&validMax=999", e );
            e= doTest( 1, "http://goes.ngdc.noaa.gov/data/avg/2004/A1050412.TXT?skip=23&timeFormat=$y$m$d+$H$M&column=E1&time=YYMMDD", e );
            e= doTest( 2, "vap+dat:file:///home/jbf/ct/hudson/data.backup/dat/V11979066.CSV?depend0=field0&column=field13", e );
            e= doTest( 3, "file:///home/jbf/ct/hudson/data.backup/dat/power.dat.txt", e );  //case where guess cadence is failing, though it shouldn't.
            e= doTest( 4, "vap+csv:file:///home/jbf/ct/hudson/data.backup/dat/V11979066.CSV?depend0=field0&column=field13", e );

            //doTest( 2, "vap:http://vho.nasa.gov/mission/soho/celias_pm_30sec/1998.txt?time=YY&column=GSE_X&timeFormat=$y+$b+$d+$(ignore):$H:$M:$S" );

            if ( e!=null ) {
                throw e;
            }
            
            exit(0);  // TODO: something is firing up the event thread
        } catch ( Exception ex) {
            ex.printStackTrace();
            exit(1);
        }
    }
}
