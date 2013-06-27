/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.pngwalk;

import org.das2.util.ArgumentList;

/**
 *
 * @author jbf
 */
public class TestException {
    public static void main( String[] args ) {
        System.err.println("TestException 20130627");
        final ArgumentList alm = new ArgumentList("TestException");
        alm.addOptionalSwitchArgument( "runtime", "t", "runtime", "", "throw a runtime exception");
        alm.process(args);

        if ( alm.getBooleanValue("runtime") ) {
            throw new RuntimeException("runtime exception");
        }
        
        System.exit(0);
    }
}
