
package org.autoplot.hapi;

import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * utility classes for Autoplot's HAPI handling
 * @author jbf
 */
public final class HapiUtil {
    
    public static final String KEY_DEFINITIONS= "definitions";
    public static final String KEY_PARAMETERS= "parameters";
    public static final String KEY_REF= "$ref";
    
    private static void resolveRefRecursive( JSONObject definitions, JSONObject param ) throws JSONException {
        Iterator keys= param.keys();
        while ( keys.hasNext() ) {
            String k= (String)keys.next();
            Object o= param.get(k);
            if ( o instanceof JSONObject ) {
                JSONObject maybeRef= (JSONObject)o;
                if ( maybeRef.has(KEY_REF) ) {
                    String theRef= maybeRef.getString(KEY_REF);
                    if ( theRef.startsWith("#/definitions/") ) {
                        String theDefinitionsRef= theRef.substring(14);
                        if ( definitions.has(theDefinitionsRef) ) {
                            Object deref= definitions.get(theDefinitionsRef);
                            param.put( k, deref );
                        } else {
                            throw new IllegalArgumentException("reference not found within definitions: "+theRef);
                        }
                    } else {
                        throw new IllegalArgumentException("references may only be to nodes within definitions: "+theRef);
                    }
                } else {
                    resolveRefRecursive( definitions, maybeRef );
                }
            }
        }
    }
    
    /**
     * resolve references within the JSON.  These references must be
     * to the definitions node.  
     * TODO: This is not complete, and does not go into bins object.
     * 
     * @param jo the JSONObject returned by the "info" request.
     * @return
     * @throws JSONException 
     * @see https://sourceforge.net/p/autoplot/feature-requests/696/
     */
    public static JSONObject resolveRefs( JSONObject jo ) throws JSONException {
        if ( !jo.has(KEY_DEFINITIONS) ) {
            return jo;
        }
        JSONObject definitions= jo.getJSONObject(KEY_DEFINITIONS);
        JSONArray ja= jo.getJSONArray(KEY_PARAMETERS);
        
        for ( int i=0; i<ja.length(); i++ ) {
            JSONObject param= ja.getJSONObject(i);
            resolveRefRecursive( definitions, param );
        }
        return jo;
    }
}
