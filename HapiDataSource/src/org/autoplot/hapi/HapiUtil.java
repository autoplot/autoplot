
package org.autoplot.hapi;

import java.util.Iterator;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * utility classes for Autoplot's HAPI handling
 * @author jbf
 */
public final class HapiUtil {
    
    private static final Logger logger= LoggerManager.getLogger("apdss.hapi");
    
    public static final String KEY_DEFINITIONS= "definitions";
    public static final String KEY_PARAMETERS= "parameters";
    public static final String KEY_REF= "$ref";
    public static final String KEY_PARAMETER= "parameter";
    public static final String KEY_BINS= "bins";
    public static final String KEY_RANGES= "ranges";
    public static final String KEY_CENTERS= "centers";
    public static final String KEY_LENGTH= "length";
    public static final String KEY_SIZE= "size";
    public static final String KEY_LABEL= "label";
    public static final String KEY_FILL = "fill";
    public static final String KEY_UNITS = "units";
    public static final String KEY_TYPE = "type";
    
    /**
     * extentions supported follow
     */
    public static final String KEY_X_COLOR_LOOKUP = "x_colorLookup";
    
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
            } else if ( o instanceof JSONArray ) {
                JSONArray ja= (JSONArray)o;
                for ( int i=0; i<ja.length(); i++ ) {
                    Object o1= ja.get(i);
                    if ( o1 instanceof JSONObject ) {
                        JSONObject maybeRef= (JSONObject)o1;
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
                    } else if ( o1 instanceof JSONArray ) {
                        logger.fine("not resolving array of array, but this is easy to do with a small refactoring");
                    } else {
                        logger.fine("not resolving array of things.");
                    }
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
