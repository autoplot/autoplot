/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource.jython;

import java.util.Random;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetIterator;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.IndexGenDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SortDataSet;
import org.virbo.dataset.WritableDataSet;
import org.virbo.dsutil.DataSetBuilder;

/**
 *
 * @author jbf
 */
public class Ops {

    private static final void putValue(WritableDataSet ds, DataSetIterator it, double v) {
        switch (ds.rank()) {
            case 1:
                ds.putValue(it.getIndex(0), v);
                return;
            case 2:
                ds.putValue(it.getIndex(0), it.getIndex(1), v);
                return;
            case 3:
                ds.putValue(it.getIndex(0), it.getIndex(1), it.getIndex(2), v);
                return;
            default:
                throw new IllegalArgumentException("rank limit");
        }
    }

    public static QDataSet add(QDataSet ds1, QDataSet ds2) {
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds1));

        DataSetIterator it1 = DataSetIterator.create(ds1);
        DataSetIterator it2 = DataSetIterator.create(ds2);
        while (it1.hasNext()) {
            double d1 = it1.next();
            double d2 = it2.next();
            putValue(result, it1, d1 + d2);
        }
        return result;
    }

    public static QDataSet negate(QDataSet ds1) {
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds1));

        DataSetIterator it = DataSetIterator.create(ds1);
        while (it.hasNext()) {
            double d = it.next();
            putValue(result, it, -d);
        }
        return result;
    }

    public static QDataSet pow(QDataSet ds, double pow) {
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds));

        DataSetIterator it = DataSetIterator.create(ds);
        while (it.hasNext()) {
            double d = it.next();
            putValue(result, it, Math.pow(d, pow));
        }
        return result;
    }

    public static QDataSet pow(QDataSet ds, QDataSet pow) {
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds));

        DataSetIterator it1 = DataSetIterator.create(ds);
        DataSetIterator it2 = DataSetIterator.create(pow);
        while (it1.hasNext()) {
            double d1 = it1.next();
            double d2 = it2.next();
            putValue(result, it2, Math.pow(d1,d2));
        }
        return result;
    }
    
    public static QDataSet exp( QDataSet ds ) {
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds));

        DataSetIterator it1 = DataSetIterator.create(ds);
        while (it1.hasNext()) {
            double d1 = it1.next();
            putValue( result, it1, Math.pow(Math.E,d1) );
        }
        return result;        
    }
    
    public static QDataSet log( QDataSet ds ) {
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds));

        DataSetIterator it1 = DataSetIterator.create(ds);
        while (it1.hasNext()) {
            double d1 = it1.next();
            putValue( result, it1, Math.log(d1) );
        }
        return result;        
    }

    public static QDataSet log10( QDataSet ds ) {
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds));

        DataSetIterator it1 = DataSetIterator.create(ds);
        while (it1.hasNext()) {
            double d1 = it1.next();
            putValue( result, it1, Math.log10(d1) );
        }
        return result;        
    }
    
    public static QDataSet multiply(QDataSet ds1, QDataSet ds2) {
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds1));

        DataSetIterator it1 = DataSetIterator.create(ds1);
        DataSetIterator it2 = DataSetIterator.create(ds2);
        while (it1.hasNext()) {
            double d1 = it1.next();
            double d2 = it2.next();
            putValue(result, it1, d1 * d2);
        }
        return result;
    }

// comparators
    public static QDataSet gt(QDataSet ds1, QDataSet ds2) {
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds1));

        DataSetIterator it1 = DataSetIterator.create(ds1);
        DataSetIterator it2 = DataSetIterator.create(ds2);
        while (it1.hasNext()) {
            double d1 = it1.next();
            double d2 = it2.next();
            putValue(result, it1, d1 > d2 ? 1.0 : 0.0);
        }
        return result;
    }

    public static QDataSet eq(QDataSet ds1, QDataSet ds2) {
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds1));

        DataSetIterator it1 = DataSetIterator.create(ds1);
        DataSetIterator it2 = DataSetIterator.create(ds2);
        while (it1.hasNext()) {
            double d1 = it1.next();
            double d2 = it2.next();
            putValue(result, it1, d1 == d2 ? 1.0 : 0.0);
        }
        return result;
    }

    public static QDataSet dindgen( int size ) {
        return new IndexGenDataSet(size);
    }
    
    public static QDataSet dindgen( int len0, int len1 ) {
        int size= len0 * len1;
        double[] back= new double[ size ];
        for ( int i=0; i<size; i++ ) back[i]= i;
        return DDataSet.wrap( back, 2, len0, len1, 1 );   
    }
    
    public static QDataSet dindgen( int len0, int len1, int len2 ) {
        int size= len0 * len1 * len2;
        double[] back= new double[ size ];
        for ( int i=0; i<size; i++ ) back[i]= i;
        return DDataSet.wrap( back, 3, len0, len1, len2);
    }

    public static QDataSet randomn( long seed, int len0 ) {
        int size= len0;
        double[] back= new double[ size ];
        Random r= new Random(seed);
        for ( int i=0; i<size; i++ ) back[i]= r.nextGaussian();
        return DDataSet.wrap( back, 1, len0, 1, 1 );   
    }
    
    public static QDataSet randomn( long seed, int len0, int len1 ) {
        int size= len0 * len1;
        double[] back= new double[ size ];
        Random r= new Random(seed);
        for ( int i=0; i<size; i++ ) back[i]= r.nextGaussian();
        return DDataSet.wrap( back, 2, len0, len1, 1 );   
    }
    
    public static QDataSet randomn( long seed, int len0, int len1, int len2 ) {
        int size= len0 * len1 * len2;
        double[] back= new double[ size ];
        Random r= new Random(seed);
        for ( int i=0; i<size; i++ ) back[i]= r.nextGaussian();
        return DDataSet.wrap( back, 3, len0, len1, len2);
    }
    
    public static QDataSet sin(QDataSet ds) {
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds));
        for (int i0 = 0; i0 < result.length(); i0++) {
            result.putValue(i0, Math.sin(ds.value(i0)));
        }
        return result;
    }

    /**
     * returns a dataset containing the indeces of where the dataset is non-zero.
     * For a rank 1 dataset, returns a rank 1 dataset with indeces for the values.
     * For a higher rank dataset, returns a rank 2 qube dataset with ds.rank()
     * elements in the first dimension.
     * 
     * @param ds
     * @return a rank 1 or rank 2 dataset.
     */
    public static QDataSet where(QDataSet ds) {
        DataSetBuilder builder;

        DataSetIterator iter = DataSetIterator.create(ds);

        if (ds.rank() == 1) {
            builder = new DataSetBuilder(1, 100, 1, 1);
            int count = 0;
            while (iter.hasNext()) {
                if (iter.next() != 0.) {
                    builder.putValue(count, iter.getIndex(0) );
                    builder.nextRecord();
                }
            }
        } else {
            builder = new DataSetBuilder(2, 100, ds.rank(), 1);
            int count = 0;
            while (iter.hasNext()) {
                if (iter.next() != 0.) {
                    builder.putValue(count, 0, iter.getIndex(0));
                    if (ds.rank() > 1) {
                        builder.putValue(count, 1, iter.getIndex(1));
                    }
                    if (ds.rank() > 2) {
                        builder.putValue(count, 2, iter.getIndex(2));
                    }
                    builder.nextRecord();
                }
            }
        }

        builder.putProperty( QDataSet.CADENCE, 1.0 );
        return builder.getDataSet();
    }
    
    public static QDataSet sort( QDataSet ds ) {
        return DataSetOps.sort(ds);
    }
}
