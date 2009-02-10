/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.cdfdatasource;

import java.text.ParseException;
import org.das2.datum.Basis;
import org.das2.datum.Datum;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetIterator;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.dataset.RankZeroDataSet;
import org.virbo.dataset.WritableDataSet;
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
        coerce( ds1, ds2, false, operands );
        
        Units u1= (Units) ds1.property( QDataSet.UNITS );
        Units u2= (Units) ds2.property( QDataSet.UNITS );
        
        Basis b1= u1.getBasis();
        Basis b2= u2.getBasis();
        Basis newBasis;
        Units newUnits;
        
        MutablePropertyDataSet result;
        if ( b1==Basis.physicalZero ) {
            newBasis= b2;
            newUnits= u2;
            final UnitsConverter uc= Units.getConverter( u1.getOffsetUnits(), u2.getOffsetUnits() );
            result= Ops.applyBinaryOp( operands[0], operands[1], new Ops.BinaryOp() {
                public double op(double d1, double d2) {
                    return uc.convert(d1) + d2;
                }
            });            
        } else if ( b2==Basis.physicalZero ) {
            newBasis= b1;
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
    
    /**
     * increase rank of datasets so that element-wise operations can be performed.  For example, 
     * if a rank 1 and a rank 2 dataset are to be combined and both have equal dim 0 length, then the
     * rank 1 is promoted to rank 2 by repeating its values.  This implements the rule that dimensions
     * in QDataSet have nested context.  The second dimension elements are to be understood in the context of
     * the first dimension element. (Except for qubes the order is arbitrary.)
     * @param ds1 the first operand
     * @param ds2 the second operand
     * @param createResult if true, then a dataset is created where the result can be installed.
     * @param result an empty dataset where the results can be inserted, or null if createResult is false.
     * @return
     */
    public static WritableDataSet coerce( QDataSet ds1, QDataSet ds2, boolean createResult, QDataSet[] operands ) {
        if ( ds1.rank()==ds2.rank() && equalGeom( ds1, ds2 ) ) {

        } else if ( ds1.rank() < ds2.rank() ) {
            if ( ds1.rank()==0 ) {
                ds1= increaseRank0( (RankZeroDataSet) ds1, ds2 );
            } else if ( ds1.rank()==1 ) {
                ds1= increaseRank1( ds1, ds2 );
            } else if ( ds1.rank()==2 ) {
                ds1= increaseRank2( ds1, ds2 );
            } else {
                throw new IllegalArgumentException("rank limit");
            }
            
        } else {
            if ( ds2.rank()==0 ) {
                ds2= increaseRank0( (RankZeroDataSet) ds2, ds1 );
            } else if ( ds2.rank()==1 ) {
                ds2= increaseRank1( ds2, ds1 );
            } else if ( ds2.rank()==2 ) {
                ds2= increaseRank2( ds2, ds1 );
            } else {
                throw new IllegalArgumentException("rank limit");
            }
        }
        operands[0]= ds1;
        operands[1]= ds2;
        DDataSet result=null;
        if ( createResult ) result= DDataSet.create(DataSetUtil.qubeDims(ds1));
        return result;
    }
    
    /**
     * increase the rank of a rank zero dataset by adding join dimensions that repeat the lower rank elements.
     * @param ds a rank 0 dataset
     * @param ds2
     * @return a dataset with the same geometry as ds2.
     */
    static QDataSet increaseRank0( final RankZeroDataSet ds, QDataSet ds2 ) {
        final int rank=ds2.rank();
        final int length0=ds2.length();
        final int length1=ds2.length(0);
        final int length2=ds2.length(0,0);
        
        return new QDataSet() {
            public int rank() {
                return rank;
            }

            public double value(int i) {
                return ds.value();
            }

            public double value(int i0, int i1) {
                return ds.value();
            }

            public double value(int i0, int i1, int i2) {
                return ds.value();
            }

            public Object property(String name) {
                return ds.property(name);
            }

            public Object property(String name, int i) {
                return ds.property(name);
            }

            public Object property(String name, int i0, int i1) {
                return ds.property(name);
            }

            public int length() {
                return length0;
            }

            public int length(int i) {
                return length1;
            }

            public int length(int i, int j) {
                return length2;
            }
        };
    }
    
    /**
     * increase the rank of a rank one dataset by adding join dimensions that repeat the lower rank elements.
     * @param ds a rank 1 dataset
     * @param ds2
     * @return a dataset with the same geometry as ds2.
     */
    static QDataSet increaseRank1( final QDataSet ds, QDataSet ds2 ) {
        final int rank=ds2.rank();
        final int length0=ds2.length();
        final int length1=ds2.length(0);
        final int length2=ds2.length(0,0);
        
        return new QDataSet() {
            public int rank() {
                return rank;
            }

            public double value(int i) {
                return ds.value(i);
            }

            public double value(int i0, int i1) {
                return ds.value(i0);
            }

            public double value(int i0, int i1, int i2) {
                return ds.value(i0);
            }

            public Object property(String name) {
                return ds.property(name);
            }

            public Object property(String name, int i) {
                return ds.property(name,i);
            }

            public Object property(String name, int i0, int i1) {
                return ds.property(name,i0);
            }

            public int length() {
                return length0;
            }

            public int length(int i) {
                return length1;
            }

            public int length(int i, int j) {
                return length2;
            }
        };
    }
    
    /**
     * increase the rank of a rank two dataset by adding join dimensions that repeat the lower rank elements.
     * @param ds a rank 2 dataset
     * @param ds2
     * @return a dataset with the same geometry as ds2.
     */
    static QDataSet increaseRank2( final QDataSet ds, QDataSet ds2 ) {
        final int rank=ds2.rank();
        final int length0=ds2.length();
        final int length1=ds2.length(0);
        final int length2=ds2.length(0,0);
        
        return new QDataSet() {
            public int rank() {
                return rank;
            }

            public double value(int i) {
                throw new IllegalArgumentException("rank to low");
            }

            public double value(int i0, int i1) {
                return ds.value(i0,i1);
            }

            public double value(int i0, int i1, int i2) {
                return ds.value(i0,i1);
            }

            public Object property(String name) {
                return ds.property(name);
            }

            public Object property(String name, int i) {
                return ds.property(name,i);
            }

            public Object property(String name, int i0, int i1) {
                return ds.property(name,i0,i1);
            }

            public int length() {
                return length0;
            }

            public int length(int i) {
                return length1;
            }

            public int length(int i, int j) {
                return length2;
            }
        };
    }

    /**
     * convert a Datum into a rank 0 data set.
     * @param d
     * @return
     */
    protected static RankZeroDataSet toRank0DataSet( final Datum d ) {
        return new RankZeroDataSet() {

            public double value() {
                return d.doubleValue( d.getUnits() );
            }

            public Object property(String name) {
                if ( name.equals(QDataSet.UNITS ) ) {
                    return d.getUnits();
                } else {
                    return null;
                }
            }

            public int rank() {
                return 0;
            }

            public double value(int i) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public double value(int i0, int i1) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public double value(int i0, int i1, int i2) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public Object property(String name, int i) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public Object property(String name, int i0, int i1) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public int length() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public int length(int i) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public int length(int i, int j) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

        };
    }
    public static void main( String[] args ) {
        MutablePropertyDataSet ds1= (MutablePropertyDataSet)Ops.findgen(10);
        ds1.putProperty( QDataSet.UNITS, Units.minutes );

        RankZeroDataSet ds2= toRank0DataSet( TimeUtil.createValid("2000-01-20T00:00") );

        QDataSet result= add(ds1, ds2);
        Units u= (Units) result.property(QDataSet.UNITS);
        for ( int i=0; i<result.length(); i++ ) {
            System.err.println( u.createDatum( result.value(i) ) );
        }
    }
}
