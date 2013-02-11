/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.jythonsupport;

import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.python.core.PyArray;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.core.adapter.PyObjectAdapter;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;

/**
 *
 * @author jbf
 */
public class PyQDataSetAdapter implements PyObjectAdapter {

    public boolean canAdapt(Object arg0) {
        return ( arg0 instanceof QDataSet );
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
        //Units u= null;
        for ( int i=0; i<p.size(); i++ ) {
            Object n= p.get(i);
            //if ( u!=null || n instanceof String ) {
            //    u= EnumerationUnits.getByName("default");
            //    j[i]= ((EnumerationUnits)u).createDatum( n ).doubleValue( u );
            //} else {
                j[i]= PyQDataSet.getNumber(n).doubleValue();
            //}
        }
        QDataSet q= DDataSet.wrap( j );
        return q;
    }

    protected static QDataSet adaptArray(PyArray pyArray) {
        Object arr= pyArray.getArray();
        return DataSetUtil.asDataSet(arr);
    }
    
}
