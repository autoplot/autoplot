
package org.autoplot.hapiserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    public static List<String> getCatalogIds( ) throws IOException {
        try {
            JSONArray catalog= getCatalog();
            List<String> result= new ArrayList<>(catalog.length());
            for ( int i=0; i<catalog.length(); i++ ) {
                JSONObject jo= catalog.getJSONObject(i);
                result.add(jo.getString("id"));
            }
            return result;
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    public static JSONArray getCatalog() throws JSONException, IOException {
        JSONArray array= new JSONArray();
        JSONObject catalog= getCatalogNew();
        JSONArray cat= catalog.getJSONArray("catalog");
        for ( int i=0; i<cat.length(); i++ ) {
            array.put( cat.get(i) );
        }
        return array;
    }
    
    private static JSONObject getCatalogNew() throws IOException, JSONException {
        StringBuilder builder= new StringBuilder();
        File catalogFile= new File( Util.getHapiHome(), "catalog.json" );
        try ( BufferedReader in= new BufferedReader( new FileReader( catalogFile ) ) ) {
            String line= in.readLine();
            while ( line!=null ) {
                builder.append(line);
                line= in.readLine();
            }
        }
        JSONObject catalog= new JSONObject(builder.toString());
        return catalog;
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
