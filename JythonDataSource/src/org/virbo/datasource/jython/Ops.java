/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource.jython;

import org.virbo.dataset.DDataSet;
import org.virbo.dataset.IndexGenDataSet;
import org.virbo.dataset.QDataSet;

/**
 *
 * @author jbf
 */
public class Ops {

    public static QDataSet add(QDataSet ds1, QDataSet ds2) {
        DDataSet result = DDataSet.copy(ds1);

        if (result.rank() == 1) {
            for (int i0 = 0; i0 < result.length(); i0++) {
                result.putValue(i0, ds1.value(i0) + ds2.value(i0));
            }
        } else if (result.rank() == 2) {
            for (int i0 = 0; i0 < result.length(); i0++) {
                for (int i1 = 0; i1 < result.length(); i1++) {
                    result.putValue(i0, i1, ds1.value(i0, i1) + ds2.value(i0, i1));
                }
            }
        } else if (result.rank() == 3) {
            for (int i0 = 0; i0 < result.length(); i0++) {
                for (int i1 = 0; i1 < result.length(); i1++) {
                    for (int i2 = 0; i2 < result.length(); i2++) {
                        result.putValue(i0, i1, i2, ds1.value(i0, i1,i2) + ds2.value(i0, i1,i2 ));
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("rank limit");
        }
        return result;
    }
    
    public static QDataSet negate( QDataSet ds1 ) {
        DDataSet result = DDataSet.copy(ds1);

        if (result.rank() == 1) {
            for (int i0 = 0; i0 < result.length(); i0++) {
                result.putValue(i0, -1*ds1.value(i0) );
            }
        } else if (result.rank() == 2) {
            for (int i0 = 0; i0 < result.length(); i0++) {
                for (int i1 = 0; i1 < result.length(); i1++) {
                    result.putValue(i0, i1, -1 * ds1.value(i0, i1) );
                }
            }
        } else if (result.rank() == 3) {
            for (int i0 = 0; i0 < result.length(); i0++) {
                for (int i1 = 0; i1 < result.length(); i1++) {
                    for (int i2 = 0; i2 < result.length(); i2++) {
                        result.putValue(i0, i1, i2, -1 * ds1.value(i0, i1,i2) );
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("rank limit");
        }
        return result;
    }
    
    public static QDataSet findgen( int size ) {
        return new IndexGenDataSet( size );
    }
    
    public static QDataSet sin( QDataSet ds ) {
        DDataSet result= DDataSet.copy(ds);
        for (int i0 = 0; i0 < result.length(); i0++) {
            result.putValue(i0, Math.sin(ds.value(i0)) );
        }
        return result;
    }
            
}
