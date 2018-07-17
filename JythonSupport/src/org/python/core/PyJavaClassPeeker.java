
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
