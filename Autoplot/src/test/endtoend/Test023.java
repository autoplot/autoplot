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
 * Tests of HDF5 products
 * @author jbf
 */
public class Test023 {


    public static void doTest( int id, String uri ) throws Exception {
        QDataSet ds;
        long t0= System.currentTimeMillis();
        ds= Util.getDataSet( uri );
        double t= (System.currentTimeMillis()-t0)/1000.;
        MutablePropertyDataSet hist= (MutablePropertyDataSet) Ops.autoHistogram(ds);
        hist.putProperty( QDataSet.TITLE, uri );
        String label= String.format( "test023_%03d", id );
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
        writeToPng( String.format( "test023_%03d.png", id ) );

        System.err.printf( "Read in %9.3f seconds (%s): %s\n", t, label, uri );
    }
    
    public static void main(String[] args)  {
        try {

            setCanvasSize(750, 300);
            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

            doTest( 0, "file:///home/jbf/ct/hudson/data.backup/h5/SourceAcronym_Subset1-1-v0.h5?ExtendibleArray" );
            //doTest( 1, "file:///home/jbf/ct/hudson/data/ncml/ap_service_provider_agg.ncml?Elastischer_Modul" ); // drop support for ncml

            System.exit(0);  // TODO: something is firing up the event thread
        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
