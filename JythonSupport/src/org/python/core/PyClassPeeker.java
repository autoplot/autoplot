
package org.python.core;

/**
 * Provide access to package-private data.
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
