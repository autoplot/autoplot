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
import static vatest.endtoend.VATestSupport.TEST_DATA;
import static vatest.endtoend.VATestSupport.TEST_VAP;

/**
 * Andrew's server
 * @author jbf
 */
public class Test008 {

    public static final boolean debug= false;

    public static void doTest( int id, String uri ) throws Exception {
        QDataSet ds;
        long t0= currentTimeMillis();
        ds= getDataSet( uri );
        double t= (currentTimeMillis()-t0)/1000.;
        MutablePropertyDataSet hist= (MutablePropertyDataSet) autoHistogram(ds);
        hist.putProperty(TITLE, uri );
        String label= format( "test008_%03d", id );
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

        plot( ds );
        setCanvasSize( 750, 300 );
        int i= uri.lastIndexOf("/");
        setTitle(uri.substring(i+1));
        writeToPng(format( "test008_%03d.png", id ) );

        err.printf( "Read in %9.3f seconds (%s): %s\n", t, label, uri );
    }
    
    public static void main(String[] args) {
        try {

            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

            doTest( 0, TEST_DATA + "/dat/ACE_sis_level2_data_1hr_1283.txt?time=year&timeFormat=%Y+%j+%H+%M+%S&depend1Labels=5:13&rank2=5:13&skip=2&validMin=2e-09" );
            
            load( TEST_VAP + "/ace_sis_level2_data_server_v1.03.vap" );
            setCanvasSize( 750, 300 );
            writeToPng( "test008_001.png" );

            doTest( 2, TEST_DATA + "/dat/ACE_sis_level2_data_1hr_1283.txt?time=year&timeFormat=%Y+%j+%H+%M+%S&intervalTag=start&depend1Labels=5:13&rank2=5:13&skip=2&validMin=2e-09" );

            exit(0);  // TODO: something is firing up the event thread
        } catch (Exception ex) {
            ex.printStackTrace();
            exit(1);
        }
    }
}
