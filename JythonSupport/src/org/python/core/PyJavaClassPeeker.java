/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.python.core;

/**
 * Provide access to package-private data.
 * @author jbf
 */
public class PyJavaClassPeeker {
    PyJavaClass c;
    public PyJavaClassPeeker( PyJavaClass c ) {
        this.c= c;
    }

    public Class getProxyClass() {
        return this.c.getProxyClass();
    }
}
