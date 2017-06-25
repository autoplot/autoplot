/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.servlet;

import java.util.logging.Logger;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.das2.system.RequestProcessor;

/**
 * Lister shuts down resources used by das2, such as its 
 * "RequestProcessor" thread pool.
 * @author jbf
 */
public class ShutdownListener implements ServletContextListener {

    private static final Logger logger= Logger.getLogger("autoplot.servlet" );

    public void contextInitialized(ServletContextEvent sce) {
        logger.fine("startup includes das2 RequestProcessor.");
    }

    public void contextDestroyed(ServletContextEvent sce) {
        logger.fine("shutdown message received, shutting down das2 RequestProcessor.");
        RequestProcessor.shutdown();
    }

}
