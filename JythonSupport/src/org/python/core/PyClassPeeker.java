/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.python.core;

/**
 *
 * @author jbf
 */
public class PyClassPeeker {

    PyClass c;
    public PyClassPeeker(PyClass pyClass) {
        this.c= pyClass;
    }

    public Class getJavaClass( ) {
        return this.c.proxyClass;
    }
}
