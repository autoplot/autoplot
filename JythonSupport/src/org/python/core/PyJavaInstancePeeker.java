/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.python.core;

/**
 *
 * @author jbf
 */
public class PyJavaInstancePeeker {

    PyJavaInstance c;
    public PyJavaInstancePeeker( PyJavaInstance c ) {
        this.c= c;
    }
    
    public Class getInstanceClass() {
        return this.c.javaProxy.getClass();
    }
}
