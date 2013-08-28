/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.PrintWriter;
import static org.virbo.autoplot.ScriptContext.*;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;
import org.virbo.jythonsupport.Util;

/**
 * Andrew's server
 * @author jbf
 */
public class Test008 {

    public static final boolean debug= false;

    public static void doTest( int id, String uri ) throws Exception {
        QDataSet ds;
        long t0= System.currentTimeMillis();
        ds= Util.getDataSet( uri );
        double t= (System.currentTimeMillis()-t0)/1000.;
        MutablePropertyDataSet hist= (MutablePropertyDataSet) Ops.autoHistogram(ds);
        hist.putProperty( QDataSet.TITLE, uri );
        String label= String.format( "test008_%03d", id );
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
        writeToPng( String.format( "test008_%03d.png", id ) );

        System.err.printf( "Read in %9.3f seconds (%s): %s\n", t, label, uri );
    }
    
    public static void main(String[] args) {
        try {

            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

            doTest( 0, "file:///home/jbf/ct/hudson/data.backup/dat/ACE_sis_level2_data_1hr_1283.txt?time=year&timeFormat=%Y+%j+%H+%M+%S&depend1Labels=5:13&rank2=5:13&skip=2&validMin=2e-09" );
            
            load( "/home/jbf/ct/hudson/vap/ace_sis_level2_data_server_v1.03.vap" );
            setCanvasSize( 750, 300 );
            writeToPng( "test008_001.png" );

            doTest( 2, "file:///home/jbf/ct/hudson/data.backup/dat/ACE_sis_level2_data_1hr_1283.txt?time=year&timeFormat=%Y+%j+%H+%M+%S&intervalTag=start&depend1Labels=5:13&rank2=5:13&skip=2&validMin=2e-09" );

            System.exit(0);  // TODO: something is firing up the event thread
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
