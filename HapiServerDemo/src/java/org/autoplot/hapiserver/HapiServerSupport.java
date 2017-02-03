
package org.autoplot.hapiserver;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
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
    public static List<String> getCatalogIds( ) {
        List<String> result= new ArrayList<String>();
        result.add( "0B000800408DD710" );
        result.add( "0B000800408DD710.noStream" );
        result.add( "8500080044259C10" );
        result.add( "610008002FE00410" );
        result.add( "AC00080040250510" );
        result.add( "Iowa City Conditions" );
        result.add( "Iowa City Forecast" );
        result.add( "Spectrum" );
        result.add( "PowerWheel");
        result.add( "PowerWheelRank2");
        result.add( "PowerOnesDigitSegments");
        return result;
    }
    
    public static JSONArray getCatalog() throws JSONException {
        JSONArray array= new JSONArray();
        array.put( new JSONObject().put("id","0B000800408DD710").put("title","Sensor 0B") );
        array.put( new JSONObject().put("id","0B000800408DD710.noStream").put("title","Sensor 0B (no streaming)") );
        array.put( new JSONObject().put("id","8500080044259C10").put("title","Sensor 85") );
        array.put( new JSONObject().put("id","610008002FE00410").put("title","Sensor 61") );
        array.put( new JSONObject().put("id","AC00080040250510").put("title","Sensor AC") );
        array.put( new JSONObject().put("id","Iowa City Conditions").put("title","Iowa City Conditions") );
        array.put( new JSONObject().put("id","Iowa City Forecast").put("title","Iowa City Forecast") );
        array.put( new JSONObject().put("id","Spectrum").put("title","Example Spectrum") );
        array.put( new JSONObject().put("id","PowerWheel").put("title","Spinning Wheel on Power Meter") );
        array.put( new JSONObject().put("id","PowerWheelRank2").put("title","Spinning Wheel on Power Meter, by cell") );
        return array;
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
