/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.cdf;

import java.lang.reflect.Array;
import org.virbo.dataset.AbstractDataSet;
import org.virbo.dataset.QDataSet;

/**
 * echo ArrayDataSet, but [0,3,2,1] transpose
 * @author jbf
 */
public abstract class TrArrayDataSet extends AbstractDataSet {
    
    float fill= Float.NaN;
    double dfill= Double.NaN;
    long lfill= Long.MAX_VALUE;

    /**
     * return the array as ArrayDataSet  The array must be a 1-D array and the
     * dimensions of the result are provided in qube.
     * @param array 1-D array
     * @param qube dimensions of the dataset
     * @param copy copy the data so that original data is not modified with putValue
     * @return ArrayDataSet
     */
    public static TrArrayDataSet wrap( Object array, int[] qube, boolean copy ) {
        Object arr;
        //check type
        if ( !array.getClass().isArray() ) throw new IllegalArgumentException("input must be an array");
        Class c= array.getClass().getComponentType();
        if ( c.isArray() ) throw new IllegalArgumentException("input must be 1-D array");
        if ( copy ) {
            arr= Array.newInstance( c, Array.getLength(array) );
            System.arraycopy( array, 0, arr, 0, Array.getLength(array) );
        } else {
            arr= array;
        }
        if ( c==double.class ) return TrDDataSet.wrap( (double[])array, qube );
        if ( c==float.class ) return TrFDataSet.wrap( (float[])array, qube );
        if ( c==long.class ) return TrLDataSet.wrap( (long[])array, qube ); 
        //if ( c==int.class ) return TrIDataSet.wrap( (int[])array, qube );
        //if ( c==short.class ) return TrSDataSet.wrap( (short[])array, qube );
        //if ( c==byte.class ) return TrBDataSet.wrap( (byte[])array, qube );

        throw new IllegalArgumentException("component type not supported: "+c );

    }

    public Class getComponentType() {
        return getBack().getClass().getComponentType();
    }

    /**
     * provide access to the backing array.
     * @return
     */
    protected abstract Object getBack();

    public int jvmMemory() {
        int sizePer;
        Class component= this.getComponentType();
        if ( component==double.class ) {
            sizePer= 8;
        } else if ( component==float.class ) {
            sizePer= 4;
        } else if ( component==long.class ) {
            sizePer= 8;
        } else if ( component==int.class ) {
            sizePer= 4;
        } else if ( component==short.class ) {
            sizePer= 2;
        } else if ( component==byte.class ) {
            sizePer= 1;
        } else {
            throw new IllegalArgumentException("not supported "+component );
        }
        return Array.getLength( this.getBack() ) * sizePer;
    }

    /**
     * check for fill property and set local variable.
     */
    protected void checkFill() {
        Number f= (Number) properties.get(QDataSet.FILL_VALUE);
        if ( f!=null ) {
            fill= f.floatValue();
            dfill= f.doubleValue();
        } else {
            fill= Float.NaN;
            dfill= Double.NaN;
        }
    }
    
    /**
     * the slice operator would be better implemented here, but there is no
     * transposed version of the class.
     * @param i
     * @return
     */
    //@Override
    //public QDataSet slice(int i) {
    //    //System.err.println("\n\nUsing Slice0DataSet to implement slice\n\n");
    //    return new Slice0DataSet(this, i);
    //}
}
