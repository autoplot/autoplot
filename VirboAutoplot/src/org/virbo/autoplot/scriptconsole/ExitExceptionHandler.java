/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.scriptconsole;

import org.das2.system.ExceptionHandler;

/**
 * Exception handler introduced for Hudson.  Prints the exception and exits.
 * @author jbf
 */
public class ExitExceptionHandler implements ExceptionHandler {

    public void handle(Throwable t) {
        System.err.println("exception occurred.");
        t.printStackTrace();
        System.exit(1);
    }

    public void handleUncaught(Throwable t) {
        System.err.println("uncaught exception occurred.");
        t.printStackTrace();
        System.exit(1);
    }
}
