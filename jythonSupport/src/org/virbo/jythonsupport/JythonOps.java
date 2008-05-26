/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.jythonsupport;

import org.python.core.PyFloat;
import org.python.core.PyFunction;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.core.PyTuple;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;

/**
 * Contains operations that are only available to Jython code, and is dependent
 * on the jython libraries.
 * 
 * @author jbf
 */
public class JythonOps {
    public static QDataSet applyLambda(QDataSet ds, PyFunction f ) {
        QubeDataSetIterator it = new QubeDataSetIterator(ds);
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds));
        while (it.hasNext()) {
            it.next();
            double d = it.getValue(ds);
            PyFloat r = (PyFloat) f.__call__(new PyFloat(d));
            it.putValue( result, r.getValue() );
        }
        return result;
    }
      
    public static QDataSet applyLambda( QDataSet ds1, QDataSet ds2, PyFunction f ) {
        QubeDataSetIterator it = new QubeDataSetIterator(ds1);
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds1));
        while (it.hasNext()) {
            it.next();
            double d1 = it.getValue(ds1);
            double d2 = it.getValue(ds2);
            PyFloat r = (PyFloat) f.__call__( new PyFloat(d1), new PyFloat(d2) );
            it.putValue( result, r.getValue() );
        }
        return result;
    }
    
    public static QDataSet applyLambda( QDataSet ds1, QDataSet ds2, QDataSet ds3, PyFunction f ) {
        QubeDataSetIterator it = new QubeDataSetIterator(ds1);
        DDataSet result = DDataSet.create(DataSetUtil.qubeDims(ds1));
        while (it.hasNext()) {
            it.next();
            double d1 = it.getValue(ds1);
            double d2 = it.getValue(ds2);
            double d3 = it.getValue(ds3);
            PyFloat r = (PyFloat) f.__call__( new PyFloat(d1), new PyFloat(d2), new PyFloat(d3) );
            it.putValue( result, r.getValue() );
        }
        return result;
    }
    
    /**
     * coerce a python array into a QDataSet.
     * @param arg0
     * @return
     */
    public static QDataSet coerce( PyObject arg0 ) {
        return PyQDataSetAdapter.adaptList( (PyList)arg0 ) ;
    }
}
