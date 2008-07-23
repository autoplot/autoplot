/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.python.core;

/**
 *
 * @author jbf
 */
public class PyMethodPeeker {
    PyMethod m;

    public PyMethodPeeker( PyMethod m ) {
        this.m= m;
    }
    
    public PyReflectedFunction getReflectedFunction() {
        return (PyReflectedFunction) m.im_func;
    }
    
}
