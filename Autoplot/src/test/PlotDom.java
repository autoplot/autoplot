/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import java.io.IOException;
import org.autoplot.ScriptContext;
import org.das2.qds.DDataSet;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;

/**
 * This takes 10 seconds on my mac--why???
 * @author jeremyfaden
 */
public class PlotDom {
    private static ScriptContext scriptContext;
    
    public static void main( String[] args ) throws IOException, InterruptedException {
long t0= System.currentTimeMillis();
        scriptContext.createGui();
System.err.println( System.currentTimeMillis()-t0 );
        double[] x = new double[400];
        double[][] y= new double[3][400];
System.err.println( System.currentTimeMillis()-t0 );
        for ( int i=0; i<400; i++ ) {
            x[i]= i/10.;
            y[0][i]= Math.sin(x[i]);
            y[1][i]= Math.cos(x[i]);
            y[2][i]= Math.tan(x[i]);
        }
System.err.println( System.currentTimeMillis()-t0 );
        QDataSet ds1= DDataSet.wrap(y[0]);
        QDataSet ds2= DDataSet.wrap(y[1]);
        QDataSet ds3= DDataSet.wrap(y[2]);

        QDataSet yy= Ops.bundle( Ops.bundle( ds1, ds2 ), ds3 );

        MutablePropertyDataSet ds= (MutablePropertyDataSet) Ops.link( DDataSet.wrap(x), yy );

        ds.putProperty( QDataSet.RENDER_TYPE, "series" );
System.err.println( System.currentTimeMillis()-t0 );
        scriptContext.load("/tmp/foo.vap");
        scriptContext.plot( 0, ds );
System.err.println( System.currentTimeMillis()-t0 );
        scriptContext.plot( 1, DDataSet.wrap(x), Ops.ripples(400) );
System.err.println( System.currentTimeMillis()-t0 );
    }
}
