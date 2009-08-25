/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import org.virbo.dataset.MutablePropertyDataSet;
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
}
