
package org.autoplot.hapiserver;

import java.util.ArrayList;
import java.util.List;
import org.das2.datum.Units;
import org.json.JSONObject;
import org.virbo.dataset.QDataSet;

/**
 *
 * @author jbf
 */
public class HapiServerSupport {
    /**
     * return the list of datasets available at the server
     * @return list of dataset ids
     */
    public static List<String> getCatalog( ) {
        List<String> result= new ArrayList<String>();
        result.add( "0B000800408DD710" );
        result.add( "8500080044259C10" );
        result.add( "610008002FE00410" );
        result.add( "AC00080040250510" );
        result.add( "Iowa City Conditions" );
        result.add( "Spectrum" );
        return result;
    }
    
    public static class ParamDescription {
        boolean hasFill= false;
        double fill= -1e38;
        String units= "";
        String name= "";
        String description= "";
        String type= "";
        int length= 0;
        int[] size= new int[0]; // array of scalars
        QDataSet depend1= null; // for spectrograms
        ParamDescription( String name ) {
            this.name= name;
        }
    }
    
}
