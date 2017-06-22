/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.dods;

import java.lang.reflect.Array;
import org.das2.qds.WritableDataSet;

/**
 * Copy java arrays into a dataset.
 * @author jbf
 */
public class ArrayUtil {
    
    public static void putValues( WritableDataSet ds, Object o ) {
        if ( o.getClass().isArray() ) {
            int n= Array.getLength(o);
            Class type= o.getClass().getComponentType();
            if ( type.isArray() ) {
                for ( int k=0; k<n; k++ ) {
                    putValues( ds, k, Array.get(o, k ) );
                }
            } else {
                for ( int k=0; k<n; k++ ) {
                    double d= Array.getDouble(o, k);
                    ds.putValue(k, d);
                }
            }
        }
    }

    public static void putValues( WritableDataSet ds, int i, Object o ) {
        if ( o.getClass().isArray() ) {
            int n= Array.getLength(o);
            Class type= o.getClass().getComponentType();
            if ( type.isArray() ) {
                for ( int k=0; k<n; k++ ) {
                    putValues( ds, i, k, Array.get(o, k ) );
                }
            } else {
                for ( int k=0; k<n; k++ ) {
                    double d= Array.getDouble(o, k);
                    ds.putValue(i, k, d);
                }
            }
        }
    }
    
    public static void putValues( WritableDataSet ds, int i, int j, Object o ) {
        if ( o.getClass().isArray() ) {
            int n= Array.getLength(o);
            Class type= o.getClass().getComponentType();
            if ( type.isArray() ) {
                throw new IllegalArgumentException("rank limit");
            } else {
                for ( int k=0; k<n; k++ ) {
                    double d= Array.getDouble(o, k);
                    ds.putValue(i, j, k, d);
                }
            }
        }
    }
    
}
