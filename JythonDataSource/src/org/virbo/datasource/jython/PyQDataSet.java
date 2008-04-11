/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource.jython;

import org.python.core.Py;
import org.python.core.PyJavaInstance;
import org.python.core.PyObject;
import org.virbo.dataset.QDataSet;

/**
 *
 * @author jbf
 */
public class PyQDataSet extends PyJavaInstance {
    QDataSet ds;
    PyQDataSet( QDataSet ds ) {
        super(ds);
        this.ds= ds;
    }
    
    public PyQDataSet __add__( PyObject ds1 ) {
        Object o= ds1.__tojava__(QDataSet.class);
        if ( o==null || o==Py.NoConversion ) throw Py.TypeError("must be a QDataSet");
        QDataSet that= ((QDataSet)o);
        return new PyQDataSet( Ops.add( ds, that ) );
    }

    @Override
    public PyObject __rsub__(PyObject arg0) {
        Object o= arg0.__tojava__(QDataSet.class);
        if ( o==null || o==Py.NoConversion ) throw Py.TypeError("must be a QDataSet");
        QDataSet that= ((QDataSet)o);
        return new PyQDataSet( Ops.add( that, Ops.negate(ds) ) );
    }

    @Override
    public PyObject __sub__(PyObject arg0) {
        Object o= arg0.__tojava__(QDataSet.class);
        if ( o==null || o==Py.NoConversion ) throw Py.TypeError("must be a QDataSet");
        QDataSet that= ((QDataSet)o);
        return new PyQDataSet( Ops.add( ds, Ops.negate(that) ) );
    }
    
    
    
}
