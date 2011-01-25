/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.cdf;

import org.virbo.dataset.DataSetIterator;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;

/**
 *
 * @author jbf
 */
public class CdfDataSetUtil {
    /**
     * return the number of valid measurements in the dataset, stopping
     * at limit when it is hit.
     * @param ds1
     * @param limit
     * @return the number of valid measurements
     */
    public static int validCount( QDataSet ds1, int limit ) {
        QDataSet weights= DataSetUtil.weightsDataSet(ds1);
        DataSetIterator iter= new QubeDataSetIterator(weights);
        int validCount= 0;
        while ( validCount<limit && iter.hasNext() ) {
            iter.next();
            if ( iter.getValue(weights)>0. ) validCount++;
        }
        return validCount;
    }
}
