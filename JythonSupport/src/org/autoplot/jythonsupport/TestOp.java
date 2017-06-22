/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.jythonsupport;

import java.util.logging.Level;
import org.das2.qds.ops.Ops;
import java.util.Random;
import java.util.logging.Logger;
import org.das2.qds.QDataSet;

/**
 *
 * @author jbf
 */
public class TestOp {

    private static final Logger logger= Logger.getLogger("jython");

    public static final int SIZE = 3000000;
    
    public static void main( String[] args ) {
        for ( int i=0; i<10; i++ ) {
            doRand(  );
        }
    }

    private static void doRand() {
        long t0 = System.currentTimeMillis();
        QDataSet rand = Ops.randn(SIZE);
        System.err.print(System.currentTimeMillis() - t0);
        t0 = System.currentTimeMillis();
        Random n = new Random();
        for (int i = 0; i < SIZE; i++) {
            n.nextGaussian();
        }
        System.err.print("  ");
        logger.log(Level.FINE, "{0}", (System.currentTimeMillis() - t0));
    }
    
    
}
