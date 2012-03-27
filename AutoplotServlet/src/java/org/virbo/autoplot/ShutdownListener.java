/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.das2.system.RequestProcessor;

/**
 *
 * @author jbf
 */
public class ShutdownListener implements ServletContextListener {

    public void contextInitialized(ServletContextEvent sce) {
        System.err.println("startup !!!");
    }

    public void contextDestroyed(ServletContextEvent sce) {
        System.err.println("shutdown !!!");
        RequestProcessor.shutdown();
    }

}
