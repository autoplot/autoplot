/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.PrintWriter;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;
import org.virbo.jythonsupport.Util;
import static org.virbo.autoplot.ScriptContext.*;

/**
 * Tests of inline data source.
 * @author jbf
 */
public class Test028 {

    private static final String test="test028";

    public static void doTest( int id, String uri ) throws Exception {
        QDataSet ds;
        System.err.printf( "== %d %s ==\n", id, uri );
        long t0= System.currentTimeMillis();
        ds= Util.getDataSet( uri );
        double t= (System.currentTimeMillis()-t0)/1000.;
        MutablePropertyDataSet hist= (MutablePropertyDataSet) Ops.autoHistogram(ds);
        hist.putProperty( QDataSet.TITLE, uri );
        String label= String.format( test + "_%03d", id );
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
        writeToPng( String.format( test + "_%03d.png", id ) );

        System.err.printf( "Read in %9.3f seconds (%s): %s\n", t, label, uri );
    }
    
    public static void main(String[] args)  {
        try {

            setCanvasSize(640,480);
            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-7em");
            setCanvasSize(640,480);
            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-7em");

            doTest( 0, "vap+inline:1,2;3,4;5,6;7,2;9,0" );
            doTest( 2, "vap+inline:1,3;2,4" );
            doTest( 3, "vap+inline:ripples(100,100)" );
            doTest( 4, "vap+inline:linspace(0,1,100),linspace(0,1,100),ripples(100,100)" );
            doTest( 5, "vap+inline:ripples(100,100)+randomn(0,100,100)/10" );
            doTest( 6, "vap+inline:ripples(100,100)+randomn(0,100,100)/10&RENDER_TYPE=nnSpectrogram" );
            doTest( 7, "vap+inline:getDataSet('http://autoplot.org/data/autoplot.ncml')&RENDER_TYPE=nnSpectrogram" );
            System.exit(0);  // TODO: something is firing up the event thread
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
