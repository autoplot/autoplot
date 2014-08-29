/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.jythonsupport;

import java.lang.reflect.Array;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.python.core.PyArray;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.core.adapter.PyObjectAdapter;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.dsops.Ops;

/**
 * Adapt QDataSet results to PyQDataSet, which provides __getitem__
 * and __setitem__.  (ds[0,0]=ds[0,0]+1)
 * @author jbf
 */
public class PyQDataSetAdapter implements PyObjectAdapter {

    public boolean canAdapt(Object arg0) {
        if ( arg0 instanceof QDataSet ) {
            return true;
        } else {
            return false;
        }
    }

    public PyObject adapt(Object arg0) {
        return new PyQDataSet((QDataSet) arg0);
    }

    // see usages elsewhere, this is sloppy.
    // TODO: consider if [ DSA, DSB ] should append( DSA, DSB ) where DSA DSB are datasets.
    /**
     * adapts list to QDataSet.
     * TODO: Consider: if element is a string, then enumeration units are used.
     * @param p
     * @return
     */
    public static QDataSet adaptList( PyList p ) {
        double[] j= new double[ p.size() ];
        QDataSet d1;
        Units u= null;
        for ( int i=0; i<p.size(); i++ ) {
            Object n= p.get(i);
            //if ( u!=null || n instanceof String ) {
            //    u= EnumerationUnits.getByName("default");
            //    j[i]= ((EnumerationUnits)u).createDatum( n ).doubleValue( u );
            //} else {
            if ( n instanceof PyObject ) {
                d1= JythonOps.dataset((PyObject)n);
            } else {
                d1= Ops.dataset(n);
            }
            if ( u==null )  u= SemanticOps.getUnits(d1);
          
            j[i]= d1.value();
            
        }
        DDataSet q= DDataSet.wrap( j );
        q.putProperty( QDataSet.UNITS, u );
        return q;
    }

    protected static QDataSet adaptArray(PyArray pyArray) {
        Object arr= pyArray.getArray();
        double[] j= new double[ pyArray.__len__() ];
        QDataSet d1;
        Units u= null;
        for ( int i=0; i<pyArray.__len__(); i++ ) {
            Object n= Array.get( arr, i );
            if ( n instanceof PyObject ) {
                d1= JythonOps.dataset((PyObject)n);
            } else {
                d1= Ops.dataset(n);
            }
            if ( u==null )  u= SemanticOps.getUnits(d1);
          
            j[i]= d1.value();   
        }
        DDataSet q= DDataSet.wrap( j );
        q.putProperty( QDataSet.UNITS, u );
        return q;
    }
    
}
