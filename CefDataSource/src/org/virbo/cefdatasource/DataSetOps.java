/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.cefdatasource;

import edu.uiowa.physics.pw.das.datum.Units;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.HighRankDataSet;
import org.virbo.dataset.QDataSet;

/**
 *
 * @author jbf
 */
public class DataSetOps {
    
    /**
     * averages all the leaf-node data together to reduce rank by one.
     * @param ds rank N dataset
     * @param total return the total, rather than the average data.
     * @return DDataSet of rank N-1.
     */
    public static DDataSet collapse( QDataSet ds ) {
        DDataSet result;
        int[] qube= (int[]) ds.property(QDataSet.QUBE);
        Units u= (Units) ds.property(QDataSet.UNITS);
        double fill= u==null ? Double.NaN : u.getFillDouble();
        
        if ( ds.rank()==4 ) {
            result= DDataSet.createRank3(qube[0], qube[1], qube[2] );
            for ( int i=0; i<ds.length(); i++ ) {
                QDataSet slice= ((HighRankDataSet)ds).slice(i);
                for ( int j=0; j<slice.length(); j++ ) {
                    for ( int k=0; k<slice.length(i); k++ ) {
                        double ssum=0;
                        double wsum=0;
                        for ( int l=0; l<ds.length(i,j); l++ ) {
                            double d= ds.value(j,k,l);
                            double w= ( u==null || u.isValid(d) ) ? 1. : 0. ;
                            ssum+= d*w;
                            wsum+= w;
                        }
                        ssum= wsum==0 ? fill : ssum / wsum;
                        result.putValue( i, j, k, ssum );
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("only rank 4 supported");
        }
        return result;
    }
}
