/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.jythonsupport;

import org.python.core.Py;
import org.python.core.PyArray;
import org.virbo.dataset.QubeDataSetIterator;
import org.python.core.PyFloat;
import org.python.core.PyFunction;
import org.python.core.PyInteger;
import org.python.core.PyList;
import org.python.core.PyObject;
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
        if ( arg0 instanceof PyQDataSet ) {
            return ((PyQDataSet)arg0).ds;
        } else if ( arg0 instanceof PyList ) {
            return PyQDataSetAdapter.adaptList( (PyList)arg0 ) ;
        } else if ( arg0 instanceof PyArray ) {
            return PyQDataSetAdapter.adaptArray( (PyArray) arg0 );
        } else if ( arg0 instanceof PyInteger ) {
            return DataSetUtil.asDataSet( ((Double)arg0.__tojava__( Double.class )).doubleValue() );
        } else if ( arg0 instanceof PyFloat ) {
            return DataSetUtil.asDataSet( ((Double)arg0.__tojava__( Double.class )).doubleValue() );
        } else {
            throw Py.TypeError("unable to coerce "+arg0+" to QDataSet");
        }
        
    }
    
    /**
     * run the function on a different thread
     * @param func a jython callable.
     */
    public static void invokeSometime( final PyObject func ) {
        Runnable run= new Runnable() {
            public void run() {
                func.__call__();
            }
        };
        new Thread(run).start();
    }

    /**
     * run the function on a different thread
     * @param func a jython callable.
     * @param arg an object to pass to the callable as an argument
     */
    public static void invokeSometime( final PyObject func, final PyObject arg ) {
        Runnable run= new Runnable() {
            public void run() {
                func.__call__(arg);
            }
        };
        new Thread(run).start();
    }
        
}
