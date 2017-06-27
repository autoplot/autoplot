/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.PrintWriter;
import static org.autoplot.ScriptContext.*;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.autoplot.jythonsupport.Util;

/**
 * Tests of Jython scripting
 * @author jbf
 */
public class Test025 {


    public static void doTest( int id, String uri ) throws Exception {
        QDataSet ds;
        long t0= System.currentTimeMillis();
        ds= Util.getDataSet( uri );
        if ( ds==null ) throw new NullPointerException("URI results in null: "+uri);
        double t= (System.currentTimeMillis()-t0)/1000.;
        MutablePropertyDataSet hist= (MutablePropertyDataSet) Ops.autoHistogram(ds);
        hist.putProperty( QDataSet.TITLE, uri );
        String label= String.format( "test025_%03d", id );
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
        writeToPng( String.format( "test025_%03d.png", id ) );

        System.err.printf( "Read in %9.3f seconds (%s): %s\n", t, label, uri );
    }
    
    public static void main(String[] args)  {
        try {

            setCanvasSize(750, 300);
            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");
            getDocumentModel().getCanvases(0).getMarginColumn().setLeft("0%+5em");
            getDocumentModel().getCanvases(0).getMarginRow().setTop("0%+2em");
            getDocumentModel().getCanvases(0).getMarginRow().setBottom("100%-3em");
            
            doTest( 0, "file:///home/jbf/ct/hudson/jyds/test025_000.jyds?ds1" );
            doTest( 1, "file:///home/jbf/ct/hudson/jyds/test025_000.jyds?ds2" );
            setCanvasSize(500,500);
            doTest( 2, "file:///home/jbf/ct/hudson/jyds/lambda.jyds?yy" );
            doTest( 3, "file:///home/jbf/ct/hudson/jyds/lambda.jyds?zz" );
            doTest( 4, "file:///home/jbf/ct/hudson/jyds/lambda.jyds?zz2" );
            doTest( 5, "http://autoplot.org/data/imageDiff.jyds" );
            doTest( 6, "file:///home/jbf/ct/hudson/jyds/test025_001.jyds?ds2");
            System.exit(0);  // TODO: something is firing up the event thread
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
