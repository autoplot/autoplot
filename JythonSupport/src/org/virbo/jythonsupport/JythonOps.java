/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.jythonsupport;

import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumUtil;
import org.das2.datum.Units;
import org.python.core.Py;
import org.python.core.PyArray;
import org.virbo.dataset.QubeDataSetIterator;
import org.python.core.PyFloat;
import org.python.core.PyFunction;
import org.python.core.PyInteger;
import org.python.core.PyJavaInstance;
import org.python.core.PyList;
import org.python.core.PyNone;
import org.python.core.PyObject;
import org.python.core.PySingleton;
import org.python.core.PyString;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;

/**
 * Contains operations that are only available to Jython code, and is dependent
 * on the jython libraries.
 *
 * History:
 *   2011-01-29 jbf: coerce command renamed to coerceToDs
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

//    public static QDataSet coerce( PyObject arg0 ) {
//        System.err.println("======================================================");
//        System.err.println("coerce( PyObject ) command that makes a QDataSet is deprecated--use coerceToDs( PyObject ) instead.");
//        System.err.println("native python coerce command will be available soon.  Contact faden @ cottagesystems.com if you need assistance.");
//        System.err.println("  sleeping for 3 seconds.");
//        System.err.println("======================================================");
//        try {
//            Thread.sleep(3000);
//        } catch (InterruptedException ex) {
//            Logger.getLogger(Util.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        return coerceToDs( arg0 );
//    }

    /**
     * @deprecated use dataset command.
     * @param arg0
     * @return 
     */
    public static QDataSet coerceToDs( PyObject arg0 ) {
        return dataset( arg0 );
    }
    
    /**
     * coerce a python array into a QDataSet.
     * @param arg0 Python object or Datum
     * @return QDataSet
     */
    public static QDataSet dataset( PyObject arg0 ) {
        if ( arg0 instanceof PyQDataSet ) {
            return ((PyQDataSet)arg0).rods;
        } else if ( arg0 instanceof PyList ) {
            return PyQDataSetAdapter.adaptList( (PyList)arg0 ) ;
        } else if ( arg0 instanceof PyArray ) {
            return PyQDataSetAdapter.adaptArray( (PyArray) arg0 );
        } else if ( arg0 instanceof PyInteger ) {
            return DataSetUtil.asDataSet( ((Double)arg0.__tojava__( Double.class )).doubleValue() );
        } else if ( arg0 instanceof PyFloat ) {
            return DataSetUtil.asDataSet( ((Double)arg0.__tojava__( Double.class )).doubleValue() );
        } else if ( arg0 instanceof PyJavaInstance && ( ((PyJavaInstance)arg0).__tojava__(Datum.class) instanceof Datum ) ) {
            return DataSetUtil.asDataSet( (Datum)((PyJavaInstance)arg0).__tojava__(org.das2.datum.Datum.class) );
        } else if ( arg0 instanceof PyJavaInstance && ( ((PyJavaInstance)arg0).__tojava__(DatumRange.class) instanceof DatumRange ) ) {
            return DataSetUtil.asDataSet( (DatumRange)((PyJavaInstance)arg0).__tojava__(org.das2.datum.DatumRange.class) );

        } else if ( arg0 instanceof PyString ) {
            try {
               return Ops.dataset(arg0.toString());
            } catch (IllegalArgumentException ex) {
               throw Py.SyntaxError( "unable to parse string: "+arg0 );
            }
        } else if ( arg0 instanceof PyNone ) {
            // In python code, support test like "ds!=None"
            return null;
        } else {
            throw Py.TypeError("JythonOps is unable to coerce "+arg0+" to QDataSet");
        }
        
    }

    public static Datum datum( PyObject arg0 ) {
        if ( arg0 instanceof PyQDataSet ) {
            QDataSet ds= ((PyQDataSet)arg0).rods;
            if ( ds.rank()>0 ) {
                throw new IllegalArgumentException("QDataSet is not rank zero and cannot be converted to datum: "+ds);
            } else {
                return DataSetUtil.asDatum(ds);
            }
        } else if ( arg0 instanceof PyInteger ) {
            return Units.dimensionless.createDatum(((PyInteger)arg0).getValue());
        } else if ( arg0 instanceof PyFloat ) {
            return Units.dimensionless.createDatum(((PyFloat)arg0).getValue());
        } else if ( arg0 instanceof PyJavaInstance && ( ((PyJavaInstance)arg0).__tojava__(Datum.class) instanceof Datum ) ) {
            return (Datum)((PyJavaInstance)arg0).__tojava__(org.das2.datum.Datum.class);
        } else if ( arg0 instanceof PyString ) {
            try {
               return DataSetUtil.asDatum( DataSetUtil.asDataSet(DatumUtil.parse(arg0.toString())) ); //TODO: someone is going to want lookupUnits that will allocate new units.
            } catch (ParseException ex) {
               throw Py.SyntaxError( "unable to parse string: "+arg0 );
            }
        } else {
            throw Py.TypeError("unable to coerce "+arg0+" to Datum");
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
