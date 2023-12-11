/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import java.io.PrintWriter;
import static org.autoplot.ScriptContext.*;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.autoplot.jythonsupport.Util;

/**
 * Tests of CEF read performance
 * @author jbf
 */
public class Test015 {


    public static void doTest( int id, String uri ) throws Exception {
        QDataSet ds;
        long t0= System.currentTimeMillis();
        ds= Util.getDataSet( uri );
        double t= (System.currentTimeMillis()-t0)/1000.;
        MutablePropertyDataSet hist= (MutablePropertyDataSet) Ops.autoHistogram(ds);
        hist.putProperty( QDataSet.TITLE, uri );
        String label= String.format( "test015_%03d", id );
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
        writeToPng( String.format( "test015_%03d.png", id ) );

        System.err.printf( "Read in %9.3f seconds (%s): %s\n", t, label, uri );
    }
    
    public static void main(String[] args) {
        try {
            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");
            
            doTest( 0, "vap+cef:file:///home/jbf/ct/hudson/data.backup/cef/C1_CP_PEA_CP3DXPH_DNFlux__20020811_140000_20020811_150000_V061018.cef?Data__C1_CP_PEA_CP3DXPH_DNFlux" );
            doTest( 1, "vap+cef:file:///home/jbf/ct/hudson/data.backup/cef/C1_CP_CIS-CODIF_HS_H1_PSD__20020117_000000_20020117_240000_V070824.cef?3d_ions__C1_CP_CIS-CODIF_HS_H1_PSD");
            doTest( 2, "vap+cef:file:///home/jbf/ct/hudson/data.backup/cef/C1_CP_EDI_EGD__20050215_V03.cef?tof__C1_CP_EDI_EGD" );
            
            //doTest( 4, "vap+tsds:http://timeseries.org/get3.cgi?StartDate=19950101&EndDate=20050201&ppd=24&ext=bin&out=ncml&param1=Kanekal_SAMPEX_elo_1hour-1-v0" );

            System.exit(0);  // TODO: something is firing up the event thread
        } catch ( Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
