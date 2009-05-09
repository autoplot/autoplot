/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package datasourcetests;

import org.das2.util.monitor.AbstractProgressMonitor;

/**
 * This is a poor implementation, since the culling should be done
 * with a separate thread.
 * @author jbf
 */
public class ConsoleProgressMonitor extends AbstractProgressMonitor {

    long lt0=0;

    @Override
    public void setTaskProgress(long position) throws IllegalArgumentException {
        if ( (System.currentTimeMillis()-lt0) > 100 ) {
            System.err.println(""+ position+" of "+getTaskSize() );
            lt0= System.currentTimeMillis();
        }
    }

}
