/*
 * SpaseMetadataModel.java
 *
 * Created on November 7, 2007, 6:57 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.spase;

import java.util.HashMap;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.MetadataModel;

/**
 *
 * @author jbf
 */
public class SpaseMetadataModel extends MetadataModel {
    
    /** Creates a new instance of SpaseMetadataModel */
    public SpaseMetadataModel() {
    }
   
    
    public Map<String,Object> properties( TreeModel meta ) {
        HashMap<String,Object> result= new HashMap<String,Object>();
        
        TreeModel meta2= copyTree( meta );
        
        result.put( QDataSet.TITLE, this.getNodeValue( meta2, new String[] { "Spase", "NumericalData", "PhysicalParameter", "Name" } ) );
        return result;
    }
    
    
}
