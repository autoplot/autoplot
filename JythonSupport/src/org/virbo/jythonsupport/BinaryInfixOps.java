/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.jythonsupport;

import org.python.core.PyObject;
import org.virbo.dsops.Ops;

/**
 * Operators that do element-wise binary operations that are not assigned to
 * operator symbols.
 * @author jbf
 */
public class BinaryInfixOps {

    public static PyQDataSet eq( PyObject arg1, PyObject arg2 ) {
        return new PyQDataSet( Ops.eq(  JythonOps.coerceToDs(arg1), JythonOps.coerceToDs(arg2) ) );
    }

    public static PyQDataSet gt( PyObject arg1, PyObject arg2 ) {
        return new PyQDataSet( Ops.gt(  JythonOps.coerceToDs(arg1), JythonOps.coerceToDs(arg2) ) );
    }

    public static PyQDataSet ge( PyObject arg1, PyObject arg2 ) {
        return new PyQDataSet( Ops.ge(  JythonOps.coerceToDs(arg1), JythonOps.coerceToDs(arg2) ) );
    }

    public static PyQDataSet lt( PyObject arg1, PyObject arg2 ) {
        return new PyQDataSet( Ops.lt(  JythonOps.coerceToDs(arg1), JythonOps.coerceToDs(arg2) ) );
    }

    public static PyQDataSet le( PyObject arg1, PyObject arg2 ) {
        return new PyQDataSet( Ops.le(  JythonOps.coerceToDs(arg1), JythonOps.coerceToDs(arg2) ) );
    }

    public static PyQDataSet ne( PyObject arg1, PyObject arg2 ) {
        return new PyQDataSet( Ops.ne(  JythonOps.coerceToDs(arg1), JythonOps.coerceToDs(arg2) ) );
    }

    public static PyQDataSet and( PyObject arg1, PyObject arg2 ) {
        return new PyQDataSet( Ops.and(  JythonOps.coerceToDs(arg1), JythonOps.coerceToDs(arg2) ) );
    }

    public static PyQDataSet or( PyObject arg1, PyObject arg2 ) {
        return new PyQDataSet( Ops.or(  JythonOps.coerceToDs(arg1), JythonOps.coerceToDs(arg2) ) );
    }
    
}
