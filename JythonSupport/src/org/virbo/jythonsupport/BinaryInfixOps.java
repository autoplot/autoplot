/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.jythonsupport;

import org.python.core.PyInteger;
import org.python.core.PyObject;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;

/**
 * Operators that do element-wise binary operations that are not assigned to
 * operator symbols.
 * @author jbf
 */
public class BinaryInfixOps {

    public static PyObject eq( PyObject arg1, PyObject arg2 ) {
        QDataSet r= Ops.eq(  JythonOps.coerceToDs(arg1), JythonOps.coerceToDs(arg2) );
        return mycast( r );
    }

    public static PyObject gt( PyObject arg1, PyObject arg2 ) {
        QDataSet r= Ops.gt(  JythonOps.coerceToDs(arg1), JythonOps.coerceToDs(arg2) );
        return mycast( r );
    }

    public static PyObject ge( PyObject arg1, PyObject arg2 ) {
        QDataSet r= Ops.ge(  JythonOps.coerceToDs(arg1), JythonOps.coerceToDs(arg2) );
        return mycast( r );
    }

    public static PyObject lt( PyObject arg1, PyObject arg2 ) {
        QDataSet r= Ops.lt(  JythonOps.coerceToDs(arg1), JythonOps.coerceToDs(arg2) ) ;
        return mycast( r );
    }

    public static PyObject le( PyObject arg1, PyObject arg2 ) {
        QDataSet r= Ops.le(  JythonOps.coerceToDs(arg1), JythonOps.coerceToDs(arg2) );
        return mycast( r );
    }

    public static PyObject ne( PyObject arg1, PyObject arg2 ) {
        QDataSet r= Ops.ne(  JythonOps.coerceToDs(arg1), JythonOps.coerceToDs(arg2) );
        return mycast( r );
    }

    public static PyObject and( PyObject arg1, PyObject arg2 ) {
        QDataSet r= Ops.and(  JythonOps.coerceToDs(arg1), JythonOps.coerceToDs(arg2) );
        return mycast( r );
    }

    public static PyObject or( PyObject arg1, PyObject arg2 ) {
        QDataSet r= Ops.or(  JythonOps.coerceToDs(arg1), JythonOps.coerceToDs(arg2) );
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
