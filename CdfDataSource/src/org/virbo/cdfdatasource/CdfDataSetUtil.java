/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.cdfdatasource;

import org.das2.datum.Basis;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.virbo.dataset.DataSetIterator;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.dataset.RankZeroDataSet;
import org.virbo.dsops.CoerceUtil;
import org.virbo.dsops.Ops;

/**
 * 
 * @author jbf
 */
public class CdfDataSetUtil {
    
    /**
     * returns true if two datasets have the same number of elements in each dimension.
     * @param ds1
     * @param ds2
     * @return
     */
    static boolean equalGeom( QDataSet ds1, QDataSet ds2 ) {
        int[] qube1=  DataSetUtil.qubeDims(ds1);
        int[] qube2=  DataSetUtil.qubeDims(ds2);
        for ( int i=0; i<qube2.length; i++ ) {
            if ( qube1[i]!=qube2[i] ) return false;
        }
        return true;
    }
    
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
    
    public static MutablePropertyDataSet add( QDataSet ds1, QDataSet ds2 ) {
        QDataSet[] operands= new QDataSet[2];
        CoerceUtil.coerce( ds1, ds2, false, operands );
        
        Units u1= (Units) ds1.property( QDataSet.UNITS );
        Units u2= (Units) ds2.property( QDataSet.UNITS );
        
        Basis b1= u1.getBasis();
        Basis b2= u2.getBasis();
        Units newUnits;
        
        MutablePropertyDataSet result;
        if ( b1==Basis.physicalZero ) {
            newUnits= u2;
            final UnitsConverter uc= Units.getConverter( u1.getOffsetUnits(), u2.getOffsetUnits() );
            result= Ops.applyBinaryOp( operands[0], operands[1], new Ops.BinaryOp() {
                public double op(double d1, double d2) {
                    return uc.convert(d1) + d2;
                }
            });            
        } else if ( b2==Basis.physicalZero ) {
            newUnits= u1;
            final UnitsConverter uc= Units.getConverter( u2.getOffsetUnits(), u1.getOffsetUnits() );
            result= Ops.applyBinaryOp( operands[0], operands[1], new Ops.BinaryOp() {
                public double op(double d1, double d2) {
                    return d1 + uc.convert(d2);
                }
            });
        } else {
            throw new IllegalArgumentException("units cannot be added: "+u1+"+"+u2 );
        }
        
        result.putProperty( QDataSet.UNITS, newUnits );
        return result;
        
    }
    
}
