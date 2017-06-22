/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.fits;

import java.util.HashMap;
import java.util.Map;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.MetadataModel;

/**
 *
 * @author jbf
 */
public class FitsMetadataModel extends MetadataModel {

    @Override
    public Map<String, Object> properties(Map<String, Object> meta) {
	Map<String,Object> result= new HashMap<String,Object>();
        Map<String,Object> dep0= new HashMap<String,Object>();
        Map<String,Object> dep1= new HashMap<String,Object>();
        
        dep0.put( QDataSet.LABEL, meta.get( "CTYPE1" ) );
        dep1.put( QDataSet.LABEL, meta.get( "CTYPE2" ) );
        result.put( QDataSet.DEPEND_0, dep0 );
        result.put( QDataSet.DEPEND_1, dep1 );
        if ( meta.get("INSTRUME")!=null && meta.get("DATE_OBS")!=null ) {
            result.put( QDataSet.TITLE, meta.get("INSTRUME") + " "+ meta.get("DATE_OBS") );
        }
        
	return result;
    }

    @Override
    public String getLabel() {
        return "FITS";
    }
    
    

}
