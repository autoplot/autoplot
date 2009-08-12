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
public class Test007 {


    public static void doTest( int id, String uri ) throws Exception {
        QDataSet ds;
        long t0= System.currentTimeMillis();
        ds= Util.getDataSet( uri );
        double t= (System.currentTimeMillis()-t0)/1000.;
        MutablePropertyDataSet hist= (MutablePropertyDataSet) Ops.autoHistogram(ds);
        hist.putProperty( QDataSet.TITLE, uri );
        String label= String.format( "test007_%03d", id );
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

            doTest( 0, "vap+tsds:http://timeseries.org/get.cgi?StartDate=19980101&EndDate=20090101&ppd=1&ext=bin&out=tsml&param1=NGDC_NOAA15_SEM2-33-v0" );
            doTest( 1, "vap+tsds:http://timeseries.org/cgi-bin/get.cgi?StartDate=19900301&EndDate=19900302&ppd=24&ext=bin&out=bin&param1=SourceAcronym_Subset2-1-v0");
            doTest( 2, "vap+tsds:http://timeseries.org/get3.cgi?StartDate=19900301&EndDate=19900301&ppd=24&ext=bin&out=ncml&param1=SourceAcronym_Subset2-1-v0" );
            doTest( 3, "vap+tsds:http://timeseries.org/get3.cgi?StartDate=19930101&EndDate=20031231&ppd=24&ext=bin&out=ncml&param1=Kanekal_SAMPEX_elo_1hour-1-v0" );


            System.exit(0);  // TODO: something is firing up the event thread
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
