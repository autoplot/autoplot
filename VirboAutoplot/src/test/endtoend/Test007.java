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
 * Tests of TSDS products 
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
        
        int i= uri.lastIndexOf("/");
        setTitle(uri.substring(i+1));
        writeToPng( String.format( "test007_%03d.png", id ) );

        System.err.printf( "Read in %9.3f seconds (%s): %s\n", t, label, uri );
    }
    
    public static void main(String[] args) {
        try {

            setCanvasSize(750, 300);
            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

            doTest( 0, "vap+tsds:http://timeseries.org/get.cgi?StartDate=19980101&EndDate=20090101&ppd=1&ext=bin&out=tsml&param1=NGDC_NOAA15_SEM2-33-v0" );
            doTest( 1, "vap+tsds:http://tsds.net/cgi-bin/get.cgi?StartDate=20000101&EndDate=20010101&ext=bin&out=tsml&ppd=24&filter=mean&param1=OMNI_OMNI2-41-v1");
            doTest( 2, "vap+tsds:http://tsds.net/cgi-bin/get.cgi?StartDate=19910101&EndDate=20041231&ext=bin&out=tsml&ppd=24&filter=mean&param1=Augsburg_ULF-6-v1" );
            //doTest( 3, "vap+tsds:http://timeseries.org/get.cgi?StartDate=19930101&EndDate=20031231&ppd=1&ext=bin&out=ncml&param1=Kanekal_SAMPEX_elo_1hour-1-v0" );

            System.exit(0);  // TODO: something is firing up the event thread
        } catch ( Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
