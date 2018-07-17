
package org.python.core;

/**
 * Provide access to package-private data.
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
