/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.util.logging.Logger;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.das2.system.RequestProcessor;

/**
 *
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
