/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource.datasource;

import java.io.File;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;

/**
 *
 * @author jbf
 */
public interface DataSourceFormat {
    void formatData( File url, QDataSet data, ProgressMonitor mon ) throws Exception;
}
