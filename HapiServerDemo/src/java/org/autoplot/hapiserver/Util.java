
package org.autoplot.hapiserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletResponse;
import org.das2.qds.DataSetUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * helpful functions.
 * @author jbf
 */
public class Util {
    
    private static final Logger LOGGER= Logger.getLogger("hapi");
    
    /**
     * return the duration in a easily-human-consumable form.
     * @param dt the duration in milliseconds.
     * @return a duration like "2.6 hours"
     */
    public static String getDurationForHumans( long dt ) {
        if ( dt<2*1000 ) {
            return dt+" milliseconds";
        } else if ( dt<2*60000 ) {
            return String.format( Locale.US, "%.1f",dt/1000.)+" seconds";
        } else if ( dt<2*3600000 ) {
            return String.format( Locale.US, "%.1f",dt/60000.)+" minutes";
        } else if ( dt<2*86400000 ) {
            return String.format( Locale.US, "%.1f",dt/3600000.)+" hours";
        } else {
            return String.format( Locale.US, "%.1f",dt/86400000.)+" days";
        }
    }
    
    private static volatile File HAPI_HOME=null;
    
    /**
     * return the root of the HAPI server.
     * @return the root of the HAPI server.
     */
    public static File getHapiHome() {
        if ( Util.HAPI_HOME==null ) {
            throw new IllegalArgumentException("Util.HAPI_HOME is not set.");
        } else {
            return Util.HAPI_HOME;
        }
    }
    
    public static void setHapiHome( File f ) {
        File HAPI_HOME_ = Util.HAPI_HOME;
        if ( HAPI_HOME_==null ) {
            synchronized ( Util.class ) {
                HAPI_HOME_ = Util.HAPI_HOME;
                if ( HAPI_HOME_==null ) {
                    Util.HAPI_HOME= f;
                }
            }
        } else {
            // it has been set already.
        }
    }
    
    /**
     * This should point to the name of the directory containing HAPI configuration.
     * This directory should contain catalog.json, capabilities.json and a 
     * subdirectory "info" which contains files with the name &lt;ID&gt;.json,
     * each containing the info response.  Note these should also contain a
     * tag "uri" which is the Autoplot URI that serves this data.
     */
    public static final String HAPI_SERVER_HOME_PROPERTY = "HAPI_SERVER_HOME";

    public static final String hapiVersion() {
        return "2.0";
    }
    
    static boolean isKey(String key) {
        Pattern p= Pattern.compile("\\d{8}+");
        return p.matcher(key).matches();
    }
    
    /**
     * this key can create place for the data
     * @param id
     * @param key
     * @return 
     */
    static boolean keyCanCreate(String id,String key) {
        if ( !isKey(key) ) {
            throw new IllegalArgumentException("is not a key: "+key);
        }
        File keyFile= new File( getHapiHome(), "keys" );
        if ( !keyFile.exists() ) return false;
        keyFile= new File( keyFile, id + ".json" );
        if ( !keyFile.exists() ) return false;
        try {
            JSONObject jo= HapiServerSupport.readJSON(keyFile);
            if ( jo.has(key) ) {
                jo= jo.getJSONObject(key);
                return jo.getBoolean("create");
            } else {
                return false;
            }
        } catch ( IOException | JSONException ex ) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * this key can modify or add records to the data
     * @param id
     * @param key
     * @return 
     */
    static boolean keyCanModify(String id,String key) {
        if ( !isKey(key) ) {
            throw new IllegalArgumentException("is not a key: "+key);
        }
        File keyFile= new File( getHapiHome(), "keys" );
        if ( !keyFile.exists() ) return false;
        keyFile= new File( keyFile, id + ".json" );
        if ( !keyFile.exists() ) return false;
        try {
            JSONObject jo= HapiServerSupport.readJSON(keyFile);
            if ( jo.has(key) ) {
                jo= jo.getJSONObject(key);
                return jo.getBoolean("modify") || jo.getBoolean("create");
            } else {
                return false;
            }
        } catch ( IOException | JSONException ex ) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * this key can delete records from the data
     * @param id
     * @param key
     * @return 
     */
    static boolean keyCanDelete(String id,String key) {
        if ( !isKey(key) ) {
            throw new IllegalArgumentException("is not a key: "+key);
        }
        File keyFile= new File( getHapiHome(), "keys" );
        if ( !keyFile.exists() ) return false;
        keyFile= new File( keyFile, id + ".json" );
        if ( !keyFile.exists() ) return false;
        try {
            JSONObject jo= HapiServerSupport.readJSON(keyFile);
            if ( jo.has(key) ) {
                jo= jo.getJSONObject(key);
                return jo.getBoolean("delete") || jo.getBoolean("create");
            } else {
                return false;
            }
        } catch ( IOException | JSONException ex ) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * transfers the data from one channel to another.  src and dest are
     * closed after the operation is complete.
     * @param src
     * @param dest
     * @throws java.io.IOException
     */
    public static void transfer( InputStream src, OutputStream dest ) throws IOException {
        final byte[] buffer = new byte[ 16 * 1024 ];

        int i= src.read(buffer);
        while ( i != -1) {
            dest.write(buffer,0,i);
            i= src.read(buffer);
        }
        dest.close();
        src.close();
    }
    
    /**
     * send a "bad id" response to the client.
     * @param id the id.
     * @param response the response object
     * @param out the print writer for the response object.
     */
    public static void raiseBadId(String id, HttpServletResponse response, final PrintWriter out) {
        try {
            JSONObject jo= new JSONObject();
            jo.put("HAPI",Util.hapiVersion());
            jo.put("createdAt",String.format("%tFT%<tRZ",Calendar.getInstance(TimeZone.getTimeZone("Z"))));
            JSONObject status= new JSONObject();
            status.put( "code", 1406 );
            String msg= "unrecognized id: "+id;
            status.put( "message", msg );
            jo.put("status",status);
            String s= jo.toString(4);
            try {
                //response.setStatus(404);
                response.sendError(404,"Bad request - unknown dataset id (HAPI 1406)");
            } catch (IOException ex) {
                Logger.getLogger(Util.class.getName()).log(Level.SEVERE, null, ex);
            }
            out.write(s);
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * return a new JSONObject for the info request, with the subset of parameters.
     * @param jo
     * @param parameters comma-delimited list of parameters.
     * @return
     * @throws JSONException 
     */
    public static JSONObject subsetParams( JSONObject jo, String parameters ) throws JSONException {
        jo= new JSONObject( jo.toString() );
        String[] pps= parameters.split(",");
        Map<String,Integer> map= new HashMap();
        JSONArray jsonParameters= jo.getJSONArray("parameters");
        for ( int i=0; i<jsonParameters.length(); i++ ) {
            map.put( jsonParameters.getJSONObject(i).getString("name"), i ); // really--should name/id are two names for the same thing...
        }
        JSONArray newParameters= new JSONArray();
        int[] indexMap= new int[pps.length];
        int[] lengths= new int[pps.length];
        boolean hasTime= false;
        for ( int ip=0; ip<pps.length; ip++ ) {
            Integer i= map.get(pps[ip]);
            if ( i==null ) {
                throw new IllegalArgumentException("bad parameter: "+pps[ip]);
            }
            indexMap[ip]= i;
            if ( i==0 ) {
                hasTime= true;
            }
            newParameters.put( ip, jsonParameters.get(i) );
            lengths[ip]= 1;
            if ( jsonParameters.getJSONObject(i).has("size") ) {
                JSONArray jarray1= jsonParameters.getJSONObject(i).getJSONArray("size");
                for ( int k=0; k<jarray1.length(); k++ ) {
                    lengths[ip]*= jarray1.getInt(k);
                }
            }
        }

        // add time if it was missing.  This demonstrates a feature that is burdensome to implementors, I believe.
        if ( !hasTime ) {
            int[] indexMap1= new int[1+indexMap.length];
            int[] lengths1= new int[1+lengths.length];
            indexMap1[0]= 0;
            System.arraycopy( indexMap, 0, indexMap1, 1, indexMap.length );
            lengths1[0]= 1;
            System.arraycopy( lengths, 0, lengths1, 1, indexMap.length );
            indexMap= indexMap1;
            lengths= lengths1;
            for ( int k=newParameters.length()-1; k>=0; k-- ) {
                newParameters.put( k+1, newParameters.get(k) );
            }
            newParameters.put(0,jsonParameters.get(0));
        }

        // unpack the resort where the lengths are greater than 1.
        int[] indexMap1= new int[ DataSetUtil.sum(lengths) ];
        int c= 0;
        if ( indexMap1.length>indexMap.length ) {
            for ( int k=0; k<lengths.length; k++ ) {
                if ( lengths[k]==1 ) {
                    indexMap1[c]= indexMap[k];
                    c++;
                } else {
                    for ( int l=0; l<lengths[k]; l++ ) { //TODO: there's a bug here if there is anything after the spectrogram, but I'm hungry for turkey...
                        indexMap1[c]= indexMap[k]+l;
                        c++;
                    }
                    if ( k<lengths.length-1 ) {
                        throw new IllegalArgumentException("not properly implemented");
                    }
                }
            }
            indexMap= indexMap1;
        }

        jsonParameters= newParameters;
        jo.put( "parameters", jsonParameters );        

        jo.put( "__indexmap__", indexMap );
        return jo;
    }

    /**
     * split, but not when comma is within quotes.
     * @param line for example 'a,b,"c,d"'
     * @param nf number of fields, or -1 for no constraint
     * @return ['a','b','c,d']
     */
    public static String[] csvSplit(String line, int nf) {
        String[] result = line.split(",", -2);
        if (result.length == nf) {
            return result;
        } else {
            int j0 = 0;
            StringBuilder b = new StringBuilder();
            boolean withinQuote = false;
            for (String result1 : result) {
                String[] f1 = result1.split("\"", -2);
                b.append(f1[0]);
                for (int k = 1; k < f1.length; k++) {
                    b.append(f1[k]);
                    withinQuote = !withinQuote;
                }
                if (!withinQuote) {
                    result[j0] = b.toString();
                    b = new StringBuilder();
                    j0++;
                } else {
                    b.append(",");
                }
            }
            if (nf > -1) {
                if (j0 < nf) {
                    throw new IllegalArgumentException("expected " + nf + " fields");
                } else if (j0 != nf) {
                    LOGGER.log(Level.WARNING, "expected {0} fields, got {1}", new Object[]{nf, j0});
                }
            }
            return Arrays.copyOfRange(result, 0, j0);
        }
    }
    
    public static void main( String[] args ) {
        String line= "a,b,\"c,d\"";
        String[] ss;
        ss= csvSplit( line, -1 );
        for ( String s: ss ) {
            System.err.println( s );
        }
    }
}
