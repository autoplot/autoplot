/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import javax.swing.JApplet;

/**
 *
 * @author jbf
 */
public class TestApplet extends JApplet {

    /**
     * Initialization method that will be called after the applet is loaded
     * into the browser.
     */
    public void init() {
        System.err.println("init TestApplet 20090112.1...");
    }

    @Override
    public void destroy() {
        super.destroy();
        System.err.println("destroy...");
    }

    @Override
    public void start() {
        super.start();
        System.err.println("start...");
    }

    @Override
    public void stop() {
        super.stop();
        System.err.println("stop...");
    }

    
    // TODO overwrite start(), stop() and destroy() methods

}
