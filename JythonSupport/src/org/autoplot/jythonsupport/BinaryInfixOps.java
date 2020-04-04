/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.jythonsupport;

import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.python.core.PyInteger;
import org.python.core.PyObject;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.das2.qds.ops.Ops;
import org.python.core.PyString;

/**
 * Operators that do element-wise binary operations that are not assigned to
 * operator symbols.
 * @author jbf
 */
public class BinaryInfixOps {

    /**
     * if ds1 is enumeration, then check if o2 could be interpreted as 
     * such, otherwise return the existing interpretation.
     * @param ds1 null, or the context in which we interpret o2.
     * @param o2 the String, QDataSet, array, etc.
     * @param ds2 fall-back interpretation of o2.
     * @return o2, possibly re-interpreted in the context of ds1.
     */
    private static QDataSet enumerationUnitsCheck( QDataSet ds1, PyObject o2, QDataSet ds2 ) {
        if ( ds1==null ) return ds2;
        Units u= SemanticOps.getUnits(ds1);
        if ( u instanceof EnumerationUnits ) {
            return JythonOps.dataset( o2, u );
        } else {
            return ds2;
        }
    }

    private static QDataSet[] datasetCoerce( PyObject arg1, PyObject arg2 ) {
        QDataSet jarg1;
        jarg1 = JythonOps.dataset(arg1);
        QDataSet jarg2;
        if ( arg2 instanceof PyString ) {
            try {
                jarg2 = Ops.dataset(arg2);
            } catch (IllegalArgumentException ex) {
                Units u= SemanticOps.getUnits(jarg1);
                try {
                    if ( u instanceof EnumerationUnits ) {
                        jarg2 = Ops.dataset( ((EnumerationUnits)u).createDatum(arg2.toString()));
                    } else {
                        jarg2 = Ops.dataset(u.parse(arg2.toString()));
                    }
                } catch (ParseException ex1) {
                    throw new IllegalArgumentException("unable to interpret argument: "+arg2);
                }
            }
        } else {
            jarg2 = JythonOps.dataset(arg2);
        }
        jarg2= enumerationUnitsCheck( jarg1, arg2, jarg2 );
        jarg1= enumerationUnitsCheck( jarg2, arg1, jarg1 );
        return new QDataSet[] { jarg1, jarg2 };
    }
    
    /**
     * perform eq, allowing string arguments to be converted to enumerations.
     * @param arg1 None, a QDataSet, String, array, scalar, etc
     * @param arg2 None, a QDataSet, String, array, scalar, etc
     * @return PyInteger for rank 0 inputs, or PyQDataSet
     */
    public static PyObject eq( PyObject arg1, PyObject arg2 ) {
        QDataSet[] jargs2= datasetCoerce( arg1, arg2 );
        QDataSet jarg1= jargs2[0];
        QDataSet jarg2= jargs2[1];
        if ( jarg1==null || jarg2==null ) return new PyInteger( jarg1==jarg2 ? 1 : 0 );
        QDataSet r= Ops.eq(  jarg1, jarg2 );
        return mycast( r );
    }

    public static PyObject ne( PyObject arg1, PyObject arg2 ) {
        QDataSet[] jargs2= datasetCoerce( arg1, arg2 );
        QDataSet jarg1= jargs2[0];
        QDataSet jarg2= jargs2[1];
        if ( jarg1==null || jarg2==null ) return new PyInteger( jarg1!=jarg2 ? 1 : 0 );
        QDataSet r= Ops.ne( jarg1, jarg2 );
        return mycast( r );
    }

    public static PyObject gt( PyObject arg1, PyObject arg2 ) {
        QDataSet r= Ops.gt(  JythonOps.dataset(arg1), JythonOps.dataset(arg2) );
        return mycast( r );
    }

    public static PyObject ge( PyObject arg1, PyObject arg2 ) {
        QDataSet r= Ops.ge(  JythonOps.dataset(arg1), JythonOps.dataset(arg2) );
        return mycast( r );
    }

    public static PyObject lt( PyObject arg1, PyObject arg2 ) {
        QDataSet r= Ops.lt(  JythonOps.dataset(arg1), JythonOps.dataset(arg2) ) ;
        return mycast( r );
    }

    public static PyObject le( PyObject arg1, PyObject arg2 ) {
        QDataSet r= Ops.le(  JythonOps.dataset(arg1), JythonOps.dataset(arg2) );
        return mycast( r );
    }

    public static PyObject and( PyObject arg1, PyObject arg2 ) {
        QDataSet r= Ops.and(  JythonOps.dataset(arg1), JythonOps.dataset(arg2) );
        return mycast( r );
    }

    public static PyObject or( PyObject arg1, PyObject arg2 ) {
        QDataSet r= Ops.or(  JythonOps.dataset(arg1), JythonOps.dataset(arg2) );
        return mycast( r );
    }

    private static PyObject mycast( QDataSet r ) {
        if ( r.rank()==0 ) {
            return new PyInteger( r.value()==0 ? 0 : 1 );
        } else {
            return new PyQDataSet( r );
        }
    }

    
}
