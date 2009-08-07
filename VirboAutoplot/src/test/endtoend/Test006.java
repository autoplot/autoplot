/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import org.virbo.autoplot.ScriptContext;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;
import org.virbo.jythonsupport.Util;

/**
 *
 * @author jbf
 */
public class Test006 {


    public static void doTest( int id, String uri ) throws Exception {
        QDataSet ds;
        long t0= System.currentTimeMillis();
        ds= Util.getDataSet( uri );
        double t= (System.currentTimeMillis()-t0)/1000.;
        MutablePropertyDataSet hist= (MutablePropertyDataSet) Ops.autoHistogram(ds);
        hist.putProperty( QDataSet.TITLE, uri );
        String label= String.format( "test006_%03d", id );
        hist.putProperty( QDataSet.LABEL, label );
        ScriptContext.formatDataSet( hist, label+".qds");

        QDataSet dep0= (QDataSet) ds.property( QDataSet.DEPEND_0 );
        if ( dep0!=null ) {
            MutablePropertyDataSet hist2= (MutablePropertyDataSet) Ops.autoHistogram(dep0);
            ScriptContext.formatDataSet( hist2, label+".dep0.qds");
        } else {
            PrintWriter pw= new PrintWriter( label+".dep0.qds" );
            pw.println("no dep0");
            pw.close();
        }

        System.err.printf( "Read in %9.3f seconds (%s): %s\n", t, label, uri );
    }
    
    public static void main(String[] args) throws InterruptedException, IOException, Exception {
        try {

            doTest( 0, "ftp://nssdcftp.gsfc.nasa.gov/spacecraft_data/omni/omni2_$Y.dat?column=field17&timerange=1963&timeFormat=$Y+$j+$H&time=field0&validMax=999" );
            doTest( 1, "http://goes.ngdc.noaa.gov/data/avg/2004/A1050412.TXT?skip=23&timeFormat=$y$m$d+$H$M&column=E1&time=YYMMDD" );
            doTest( 2, "file:///home/jbf/ct/hudson/data.backup/dat/V11979066.CSV?depend0=field0&column=field13" );
            doTest( 3, "file:///home/jbf/ct/hudson/data.backup/dat/power.dat.txt" );

            //doTest( 2, "vap:http://vho.nasa.gov/mission/soho/celias_pm_30sec/1998.txt?time=YY&column=GSE_X&timeFormat=$y+$b+$d+$(ignore):$H:$M:$S" );


            System.exit(0);  // TODO: something is firing up the event thread
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
