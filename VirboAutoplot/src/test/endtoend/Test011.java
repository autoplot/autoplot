/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.dsops.Ops;

/**
 * performance and function of QDataSet operations.
 * @author jbf
 */
public class Test011 {

    static long t0= System.currentTimeMillis();

    private static void timer( String id ) {
        System.out.printf("%s (millis): %5d \n",id,System.currentTimeMillis()-t0);
        t0= System.currentTimeMillis();
    }

    public static void main(String[] args) throws InterruptedException, IOException, Exception {

        timer("reset");
        QDataSet ds= Ops.findgen(4000,30);
        timer("ds=findgen(4000,30)");

        for ( int i=0; i<4000; i++ ) {
            String cmd= "_s0(40)"; //String.format("_s0(%d)", i );
            QDataSet ds1= DataSetOps.sprocess( cmd, ds);
        }

        timer("sprocess ds 4000 times");

        QDataSet ds2= Ops.fftPower(ds);

        timer("fftPower(ds)");

        Ops.add( ds, ds );
        timer("Ops.add( ds,ds )");

        QDataSet ds3= Ops.zeros(40000,256);
        timer("Ops.zeroes(40000,256)");

        double total=0;
        for ( int i=0; i<ds3.length(); i++ ) {
            for ( int j=0; j<ds3.length(0); j++ ) {
                total+= ds3.value(i,j);
            }
        }
        timer("access each ds3.value(i,j)");

        total=0;
        QubeDataSetIterator it= new QubeDataSetIterator(ds3);
        while( it.hasNext() ) {
            it.next();
            total+= it.getValue(ds3);
        }
        timer("iterator over ds3 to access");

        if ( true ) {
            System.exit(0);
        } else {
            System.exit(1);
        }
    }
}
