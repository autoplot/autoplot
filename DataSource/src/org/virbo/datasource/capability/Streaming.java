/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource.capability;

import java.util.Iterator;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;

/**
 * allows the records to be read in as they are available.  Note
 * when the Iterator hasNext method may block until more records are 
 * available.
 * @author jbf
 */
public interface Streaming {
    Iterator<QDataSet> streamDataSet( ProgressMonitor mon ) throws Exception;
}
