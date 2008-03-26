/*
 * TestSpase.java
 *
 * Created on November 7, 2007, 8:32 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.spase;

import org.das2.util.monitor.NullProgressMonitor;
import java.net.URL;
import java.util.Map;
import javax.swing.tree.TreeModel;

/**
 *
 * @author jbf
 */
public class TestSpase {
    
    /** Creates a new instance of TestSpase */
    public TestSpase() {
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        SpaseRecordDataSource ds= new SpaseRecordDataSource( new URL( "file:/L:/ct/virbo/autoplot/sample2.xml" ) );
        TreeModel meta= ds.getMetaData(new NullProgressMonitor());
        SpaseMetadataModel metamodel= new SpaseMetadataModel();
        Map props= metamodel.properties( meta );
        
        
    }
    
}
