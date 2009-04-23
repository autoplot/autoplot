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
        return new PyQDataSet( Ops.eq(  JythonOps.coerce(arg1), JythonOps.coerce(arg2) ) );
    }

    public static PyQDataSet gt( PyObject arg1, PyObject arg2 ) {
        return new PyQDataSet( Ops.gt(  JythonOps.coerce(arg1), JythonOps.coerce(arg2) ) );
    }

    public static PyQDataSet ge( PyObject arg1, PyObject arg2 ) {
        return new PyQDataSet( Ops.ge(  JythonOps.coerce(arg1), JythonOps.coerce(arg2) ) );
    }

    public static PyQDataSet lt( PyObject arg1, PyObject arg2 ) {
        return new PyQDataSet( Ops.lt(  JythonOps.coerce(arg1), JythonOps.coerce(arg2) ) );
    }

    public static PyQDataSet le( PyObject arg1, PyObject arg2 ) {
        return new PyQDataSet( Ops.le(  JythonOps.coerce(arg1), JythonOps.coerce(arg2) ) );
    }

    public static PyQDataSet ne( PyObject arg1, PyObject arg2 ) {
        return new PyQDataSet( Ops.ne(  JythonOps.coerce(arg1), JythonOps.coerce(arg2) ) );
    }

    public static PyQDataSet and( PyObject arg1, PyObject arg2 ) {
        return new PyQDataSet( Ops.and(  JythonOps.coerce(arg1), JythonOps.coerce(arg2) ) );
    }

    public static PyQDataSet or( PyObject arg1, PyObject arg2 ) {
        return new PyQDataSet( Ops.or(  JythonOps.coerce(arg1), JythonOps.coerce(arg2) ) );
    }
    
}
