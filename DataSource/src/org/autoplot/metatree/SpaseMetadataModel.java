/*
 * SpaseMetadataModel.java
 *
 * Created on November 7, 2007, 6:57 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.autoplot.metatree;

import java.util.HashMap;
import java.util.Map;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.MetadataModel;

/**
 *
 * @author jbf
 */
public class SpaseMetadataModel extends MetadataModel {
    
    /** Creates a new instance of SpaseMetadataModel */
    public SpaseMetadataModel() {
    }
   
    
    public Map<String,Object> properties( Map<String,Object> meta ) {
        HashMap<String,Object> result= new HashMap<String,Object>();
        
        Map<String,Object> param= (Map<String, Object>) MetadataModel.getNode( meta, new String[] { "Spase", "NumericalData", "PhysicalParameter" } );
        result.put( QDataSet.TITLE, param.get( "Name" ) );
        result.put( QDataSet.LABEL, ""+param.get("Name")+ " (" + (String) param.get( "Units" ) + ")"  );
        
        return result;
    }

    @Override
    public String getLabel() {
        return "SPASE";
    }
    
    
}
