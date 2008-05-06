/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource.jython;

import edu.uiowa.physics.pw.das.dataset.TableDataSet;
import edu.uiowa.physics.pw.das.datum.EnumerationUnits;
import edu.uiowa.physics.pw.das.datum.Units;
import edu.uiowa.physics.pw.das.math.fft.ComplexArray;
import edu.uiowa.physics.pw.das.math.fft.FFTUtil;
import edu.uiowa.physics.pw.das.math.fft.GeneralFFT;
import edu.uiowa.physics.pw.das.math.fft.WaveformToSpectrum;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetAdapter;
import org.virbo.dataset.DataSetIterator;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.IndexGenDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.VectorDataSetAdapter;
import org.virbo.dataset.WritableDataSet;
import org.virbo.dsutil.DataSetBuilder;

/**
 *
 * @author jbf
 */
public class Ops {

    interface UnaryOp {

        double op(double d1);
    }

    private static final QDataSet applyUnaryOp(QDataSet ds1, UnaryOp op) {
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds1));

        QubeDataSetIterator it1 = new QubeDataSetIterator(ds1);
        while (it1.hasNext()) {
            it1.next();
            double d1 = getValue(ds1, it1);
            putValue(result, it1, op.op(d1));
        }
        return result;
    }

    interface BinaryOp {

        double op(double d1, double d2);
    }

    /**
     * apply the binary operator element-for-element of the two datasets, minding
     * dataset geometry, fill values, etc.
     * TODO: mind fill values.
     * @param ds1
     * @param ds2
     * @param op
     * @return
     */
    private static final QDataSet applyBinaryOp(QDataSet ds1, QDataSet ds2, BinaryOp op) {
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds1));

        QubeDataSetIterator it1 = new QubeDataSetIterator(ds1);
        QubeDataSetIterator it2 = new QubeDataSetIterator(ds2);
        while (it1.hasNext()) {
            it1.next();
            double d1 = getValue(ds1, it1);
            it2.next();
            double d2 = getValue(ds2, it2);
            putValue(result, it1, op.op(d1, d2));
        }
        return result;
    }

    private static final QDataSet applyBinaryOp(QDataSet ds1, double d2, BinaryOp op) {
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds1));

        QubeDataSetIterator it1 = new QubeDataSetIterator(ds1);
        while (it1.hasNext()) {
            it1.next();
            putValue(result, it1, op.op(getValue(ds1, it1), d2));
        }
        return result;
    }

    private static final double getValue(QDataSet ds, QubeDataSetIterator it) {
        switch (ds.rank()) {
            case 1:
                return ds.value(it.index(0));
            case 2:
                return ds.value(it.index(0), it.index(1));
            case 3:
                return ds.value(it.index(0), it.index(1), it.index(2));
            default:
                throw new IllegalArgumentException("rank limit");
        }
    }

    private static final void putValue(WritableDataSet ds, QubeDataSetIterator it, double v) {
        switch (ds.rank()) {
            case 1:
                ds.putValue(it.index(0), v);
                return;
            case 2:
                ds.putValue(it.index(0), it.index(1), v);
                return;
            case 3:
                ds.putValue(it.index(0), it.index(1), it.index(2), v);
                return;
            default:
                throw new IllegalArgumentException("rank limit");
        }
    }

    /**
     * add the two datasets have the same geometry.
     * @param ds1
     * @param ds2
     * @return
     */
    public static QDataSet add(QDataSet ds1, QDataSet ds2) {
        return applyBinaryOp(ds1, ds2, new BinaryOp() {
            public double op(double d1, double d2) {
                return d1 + d2;
            }
        });
    }

    /**
     * return a dataset with each element negated.
     * @param ds1
     * @return
     */
    public static QDataSet negate(QDataSet ds1) {
        return applyUnaryOp(ds1, new UnaryOp() {

            public double op(double d1) {
                return -d1;
            }
        });
    }

    /**
     * return the magnitudes of vectors in a rank 2 or greater dataset.  The last
     * index must be a cartesian dimension, so it must have a depend dataset
     * either named "cartesian" or having the property CARTESIAN_FRAME
     * 
     * @param ds of Rank N.
     * @return ds of Rank N-1.
     */
    public static QDataSet magnitude( QDataSet ds ) {
        int r= ds.rank();
        QDataSet depn= (QDataSet) ds.property( "DEPEND_"+(r-1) );
        boolean isCart= false;
        if ( depn!=null ) {
            if ( depn.property( QDataSet.COORDINATE_FRAME ) !=null ) {
                isCart= true;
            } else if ( "cartesian".equals( depn.property( QDataSet.NAME ) ) ) {
                isCart= true;
            } else {
            }
        }
        if ( isCart ) {
            ds= pow( ds, 2 );
            ds= total( ds, r-1, false );
            ds= sqrt( ds );
            return ds;
        } else {
            throw new IllegalArgumentException("last dim must have COORDINATE_FRAME property");
        }

    }
    
    /**
     * reduce the dataset's rank by totalling all the elements along a dimension.
     * Only QUBEs are supported presently.
     * 
     * @param ds rank N qube dataset.
     * @param dim zero-based index number.
     * @param normalize return the average instead of the total.
     * @return
     */
    public static QDataSet total( QDataSet ds, int dim, boolean normalize ) {
       int[] qube= DataSetUtil.qubeDims(ds);
       int[] newQube= DataSetOps.removeElement( qube, dim );
       QDataSet wds= DataSetUtil.weightsDataSet(ds);
       DDataSet result= DDataSet.create(newQube);
       QubeDataSetIterator it1= new QubeDataSetIterator( result );
       double fill= (Double)wds.property(QDataSet.FILL_VALUE);
       while ( it1.hasNext() ) {
           it1.next();
           int n= ds.length(dim);
           double s= 0;
           double w= 0;
           QubeDataSetIterator it0= new QubeDataSetIterator( ds );
           for ( int i=0; i<ds.rank(); i++ ) {
               int ndim= i<dim ? i : i-1;    
               if (i!=dim) {
                   it0.setIndexIteratorFactory( i, new QubeDataSetIterator.SingletonIteratorFactory(it1.index(ndim)));
               } 
           }
           while ( it0.hasNext() ) {
               it0.next();
               double w1= getValue( wds, it0 ); 
               s+= w1*getValue( ds, it0 );
               w+= w1;
           }
           putValue( result, it1, w>0 ? s : fill );
       }
       
       return result;
    }
    
    /**
     * element-wise sqrt.
     * @param ds
     * @return
     */
    public static QDataSet sqrt( QDataSet ds ) {
        return pow( ds, 0.5 );
    }
    
    /**
     * element-wise abs.  For vectors, this returns the length of each element.
     * @param ds1
     * @return
     */
    public static QDataSet abs(QDataSet ds1) {
        return applyUnaryOp(ds1, new UnaryOp() {
            public double op(double d1) {
                return Math.abs(d1);
            }
        });
    }

    /**
     * element-wise pow.  (** in FORTRAN, ^ in IDL)
     * @param ds1
     * @param pow
     * @return
     */
    public static QDataSet pow(QDataSet ds1, double pow) {
        return applyBinaryOp(ds1, pow, new BinaryOp() {

            public double op(double d1, double d2) {
                return Math.pow(d1, d2);
            }
        });
    }

    /**
     * element-wise pow (** in FORTRAN, ^ in IDL) of two datasets with the same
     * geometry.
     * @param ds1
     * @param pow
     * @return
     */
    public static QDataSet pow(QDataSet ds1, QDataSet pow) {
        return applyBinaryOp(ds1, pow, new BinaryOp() {

            public double op(double d1, double d2) {
                return Math.pow(d1, d2);
            }
        });
    }

    /**
     * element-wise exponentiate e**x.
     * @param ds
     * @return
     */
    public static QDataSet exp(QDataSet ds) {
        return applyUnaryOp(ds, new UnaryOp() {

            public double op(double d1) {
                return Math.pow(Math.E, d1);
            }
        });
    }

    /**
     * element-wise natural logarythm.
     * @param ds
     * @return
     */
    public static QDataSet log(QDataSet ds) {
        return applyUnaryOp(ds, new UnaryOp() {

            public double op(double d1) {
                return Math.log(d1);
            }
        });
    }

    /**
     * element-wise base 10 logarythm.
     * @param ds
     * @return
     */
    public static QDataSet log10(QDataSet ds) {
        return applyUnaryOp(ds, new UnaryOp() {

            public double op(double d1) {
                return Math.log10(d1);
            }
        });
    }

    /**
     * element-wise multiply of two datasets with the same geometry.
     * @param ds
     * @return
     */
    public static QDataSet multiply(QDataSet ds1, QDataSet ds2) {
        return applyBinaryOp(ds1, ds2, new BinaryOp() {

            public double op(double d1, double d2) {
                return d1 * d2;
            }
        });
    }

// comparators
    /**
     * element-wise equality test.  1.0 is returned where the two datasets are
     * equal.  invalid measurements are always unequal.
     * @param ds
     * @return
     */
    public static QDataSet eq(QDataSet ds1, QDataSet ds2) {
        return applyBinaryOp(ds1, ds2, new BinaryOp() {

            public double op(double d1, double d2) {
                return d1 == d2 ? 1.0 : 0.0;
            }
        });
    }

    /**
     * element-wise not equal test.  invalid measurements are always unequal.
     * @param ds1
     * @param ds2
     * @return
     */
    public static QDataSet ne(QDataSet ds1, QDataSet ds2) {
        return applyBinaryOp(ds1, ds2, new BinaryOp() {

            public double op(double d1, double d2) {
                return d1 != d2 ? 1.0 : 0.0;
            }
        });
    }

    public static QDataSet gt(QDataSet ds1, QDataSet ds2) {
        return applyBinaryOp(ds1, ds2, new BinaryOp() {

            public double op(double d1, double d2) {
                return d1 > d2 ? 1.0 : 0.0;
            }
        });
    }

    public static QDataSet ge(QDataSet ds1, QDataSet ds2) {
        return applyBinaryOp(ds1, ds2, new BinaryOp() {

            public double op(double d1, double d2) {
                return d1 >= d2 ? 1.0 : 0.0;
            }
        });
    }

    public static QDataSet lt(QDataSet ds1, QDataSet ds2) {
        return applyBinaryOp(ds1, ds2, new BinaryOp() {

            public double op(double d1, double d2) {
                return d1 < d2 ? 1.0 : 0.0;
            }
        });
    }

    public static QDataSet le(QDataSet ds1, QDataSet ds2) {
        return applyBinaryOp(ds1, ds2, new BinaryOp() {

            public double op(double d1, double d2) {
                return d1 <= d2 ? 1.0 : 0.0;
            }
        });
    }

    // logic operators
    public static QDataSet or(QDataSet ds1, QDataSet ds2) {
        return applyBinaryOp(ds1, ds2, new BinaryOp() {

            public double op(double d1, double d2) {
                return d1 != 0 || d2 != 0 ? 1.0 : 0.0;
            }
        });
    }

    public static QDataSet and(QDataSet ds1, QDataSet ds2) {
        return applyBinaryOp(ds1, ds2, new BinaryOp() {

            public double op(double d1, double d2) {
                return d1 != 0 && d2 != 0 ? 1.0 : 0.0;
            }
        });
    }

    public static QDataSet not(QDataSet ds1) {
        return applyUnaryOp(ds1, new UnaryOp() {

            public double op(double d1) {
                return d1 != 0 ? 0.0 : 1.0;
            }
        });
    }

    // IDL,Matlab - inspired routines
    /**
     * returns rank 1 dataset with values [0,1,2,...]
     * @param size
     * @return
     */
    public static QDataSet dindgen(int size) {
        return new IndexGenDataSet(size);
    }

    /**
     * returns rank 2 dataset with values increasing [ [0,1,2], [ 3,4,5] ]
     * @param len0
     * @param len1
     * @return
     */
    public static QDataSet dindgen(int len0, int len1) {
        int size = len0 * len1;
        double[] back = new double[size];
        for (int i = 0; i < size; i++) {
            back[i] = i;
        }
        return DDataSet.wrap(back, 2, len0, len1, 1);
    }

     /**
     * returns rank 3 dataset with values increasing
     * @param len0
     * @param len1
     * @param len2
     * @return
     */
    public static QDataSet dindgen(int len0, int len1, int len2) {
        int size = len0 * len1 * len2;
        double[] back = new double[size];
        for (int i = 0; i < size; i++) {
            back[i] = i;
        }
        return DDataSet.wrap(back, 3, len0, len1, len2);
    }

    public static QDataSet findgen(int size) {
        return new IndexGenDataSet(size);
    }

    public static QDataSet findgen(int len0, int len1) {
        int size = len0 * len1;
        double[] back = new double[size];
        for (int i = 0; i < size; i++) {
            back[i] = i;
        }
        return DDataSet.wrap(back, 2, len0, len1, 1);
    }

    public static QDataSet findgen(int len0, int len1, int len2) {
        int size = len0 * len1 * len2;
        double[] back = new double[size];
        for (int i = 0; i < size; i++) {
            back[i] = i;
        }
        return DDataSet.wrap(back, 3, len0, len1, len2);
    }

    /**
     * returns a rank 1 dataset of random numbers of a guassian (normal) distribution.
     * System.currentTimeMillis() may be used for the seed.
     * @param seed
     * @param len0
     * @return
     */
    public static QDataSet randomn(long seed, int len0) {
        int size = len0;
        double[] back = new double[size];
        Random r = new Random(seed);
        for (int i = 0; i < size; i++) {
            back[i] = r.nextGaussian();
        }
        return DDataSet.wrap(back, 1, len0, 1, 1);
    }

    /**
     * returns a rank 2 dataset of random numbers of a guassian (normal) distribution.
     * @param seed
     * @param len0
     * @param len1
     * @return
     */
    public static QDataSet randomn(long seed, int len0, int len1) {
        int size = len0 * len1;
        double[] back = new double[size];
        Random r = new Random(seed);
        for (int i = 0; i < size; i++) {
            back[i] = r.nextGaussian();
        }
        return DDataSet.wrap(back, 2, len0, len1, 1);
    }

    /**
     * returns a rank 3 dataset of random numbers of a guassian (normal) distribution.
     * @param seed
     * @param len0
     * @param len1
     * @param len2
     * @return
     */
    public static QDataSet randomn(long seed, int len0, int len1, int len2) {
        int size = len0 * len1 * len2;
        double[] back = new double[size];
        Random r = new Random(seed);
        for (int i = 0; i < size; i++) {
            back[i] = r.nextGaussian();
        }
        return DDataSet.wrap(back, 3, len0, len1, len2);
    }

    /**
     * element-wise sin.
     * @param ds
     * @return
     */
    public static QDataSet sin(QDataSet ds) {
        return applyUnaryOp(ds, new UnaryOp() {

            public double op(double d1) {
                return Math.sin(d1);
            }
        });
    }

    /**
     * element-wise cos.
     * @param ds
     * @return
     */
    public static QDataSet cos(QDataSet ds) {
        return applyUnaryOp(ds, new UnaryOp() {

            public double op(double d1) {
                return Math.cos(d1);
            }
        });
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
                    builder.putValue(count, iter.getIndex(0));
                    builder.nextRecord();
                }
            }
            builder.putProperty(QDataSet.MONOTONIC, Boolean.TRUE);
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

        builder.putProperty(QDataSet.CADENCE, 1.0);

        return builder.getDataSet();
    }

    /**
     * returns a rank 1 dataset of indeces that sort the rank 1 dataset ds.
     * @param ds
     * @return
     */
    public static QDataSet sort(QDataSet ds) {
        return DataSetOps.sort(ds);
    }

    /**
     * Performs an FFT on the provided rank 1 dataset.  A rank 2 dataset of 
     * complex numbers is returned.
     * @param ds a rank 1 dataset.
     * @return a rank 2 dataset of complex numbers.
     */
    public static QDataSet fft(QDataSet ds) {
        GeneralFFT fft = GeneralFFT.newDoubleFFT(ds.length());
        Units u = (Units) ds.property(QDataSet.UNITS);
        if (u == null) {
            u = Units.dimensionless;
        }
        ComplexArray.Double cc= FFTUtil.fft(fft, VectorDataSetAdapter.create(ds), u);
        DDataSet result= DDataSet.createRank2( ds.length(), 2 );
        for ( int i=0; i<ds.length(); i++ ) {
            result.putValue(i,0,cc.getReal(i));
            result.putValue(i,1,cc.getImag(i));
        }
        
        QDataSet dep0= (QDataSet) ds.property( QDataSet.DEPEND_0 );
        double cadence= dep0==null ? 1.0 : DataSetUtil.guessCadence(dep0);
        
        double[] tags= FFTUtil.getFrequencyDomainTags( cadence, ds.length() );
        result.putProperty( QDataSet.DEPEND_0, DDataSet.wrap(tags) );
        
        EnumerationUnits u1= EnumerationUnits.create("complexCoordinates");
        DDataSet dep1= DDataSet.createRank1( 2 );
        dep1.putValue( 0, u1.createDatum("real").doubleValue(u1) );
        dep1.putValue( 1, u1.createDatum("imag").doubleValue(u1) );
        dep1.putProperty( QDataSet.COORDINATE_FRAME, "ComplexNumber" );
        dep1.putProperty( QDataSet.UNITS, u1 );
        
        result.putProperty( QDataSet.DEPEND_1, dep1 );
        return result;
    }
    
    /**
     * perform ffts on the rank 1 dataset to make a rank2 spectrogram.
     * @param ds rank 1 dataset
     * @param len the window length
     * @return rank 2 dataset.
     */
    public static QDataSet fftWindow( QDataSet ds, int len ) {
        TableDataSet result= WaveformToSpectrum.getTableDataSet( VectorDataSetAdapter.create(ds), len) ;
        return DataSetAdapter.create(result);
    }
    
    public static double PI = Math.PI;
    public static double E = Math.E;
}
