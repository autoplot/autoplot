/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.text.ParseException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.JoinDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.WritableDataSet;
import org.virbo.dsops.Ops;

/**
 *
 * @author jbf
 */
public class TestSupport {
    public static MutablePropertyDataSet sampleDataRank2( int len0, int len1 ) {
        MutablePropertyDataSet rank2Rand= (MutablePropertyDataSet) Ops.add( Ops.randomn(-12345, len0,len1),
                    Ops.sin( Ops.add( Ops.outerProduct( Ops.linspace( 0, 1000.,len0), Ops.replicate(1,len1)),
                                        Ops.outerProduct( Ops.replicate(1,len0), Ops.linspace(0,10,len1) ) ) ) );
        return rank2Rand;
    }
    /**
     * return rank 1 dataset that is a 1-D random walk.
     * @param len0
     * @return
     */
    public static MutablePropertyDataSet sampleDataRank1( int len0 ) {
        MutablePropertyDataSet rank1Rand= (MutablePropertyDataSet) Ops.accum( Ops.randomn(-12345,len0) );
        try {
            rank1Rand.putProperty(QDataSet.DEPEND_0, Ops.timegen("2050-001T00:00", "75.23 ms", len0));
        } catch (ParseException ex) {
            Logger.getLogger(TestSupport.class.getName()).log(Level.SEVERE, null, ex);
        }
        Random r= new Random(-12345);
        // simulate data dropout.
        WritableDataSet wds= (WritableDataSet)rank1Rand;
        int len= Math.max( 5,len0/100 );
        for ( int i=0; i<5; i++ ) {
            int idx= r.nextInt(len0-len);
            for ( int j=idx; j<idx+len; j++ ) {
                wds.putValue( j,-1e38 );
            }
        }
        wds.putProperty( QDataSet.VALID_MIN, -1e30 );
        return rank1Rand;
    }

    public static MutablePropertyDataSet sampleQube1( double off1, double off2, int len1, int len2 ) {
        DDataSet result= (DDataSet) Ops.randn(len1,len2);
        result.putProperty( QDataSet.DEPEND_0, Ops.add( DataSetUtil.asDataSet(off1), Ops.dindgen(len1) ) );
        result.putProperty( QDataSet.DEPEND_1, Ops.add( DataSetUtil.asDataSet(off2), Ops.dindgen(len2) ) );
        return result;
    }

    public static MutablePropertyDataSet sampleRank3Join( ) {
        JoinDataSet jds= new JoinDataSet(3);

        MutablePropertyDataSet qds= sampleQube1( 0, 2.2, 6, 5 );
        jds.join( qds );
        qds= sampleQube1( qds.length(), 3.3, 5, 4 );
        jds.join( qds );
        
        return jds;
    }
}
