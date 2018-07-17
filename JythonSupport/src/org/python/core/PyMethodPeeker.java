
package org.python.core;

/**
 * Provide access to package-private data.
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
