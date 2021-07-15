
package org.autoplot.hapiserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletContext;
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
    
    /**
     * if HAPI_HOME has not been set, then set it.
     * @param context 
     */
    public static void maybeInitialize( ServletContext context ) {
        if ( HAPI_HOME==null ) {
            String s= context.getInitParameter(HAPI_SERVER_HOME_PROPERTY);
            setHapiHome( new File( s ) );
        }
    }
    
    private static volatile File HAPI_HOME=null;
    
    /**
     * return the root of the HAPI server.
     * @return the root of the HAPI server.
     */
    public static File getHapiHome() {
        if ( Util.HAPI_HOME==null ) {
            throw new IllegalArgumentException("Util.HAPI_HOME is not set, load info page first.");
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
     * tag "x_uri" which is the Autoplot URI that serves this data.
     */
    public static final String HAPI_SERVER_HOME_PROPERTY = "HAPI_SERVER_HOME";

    /**
     * return the HAPI protocol version.
     * @return the HAPI protocol version.
     */
    public static final String hapiVersion() {
        return "3.0";
    }
    
    /**
     * return the server implementation version.
     * @return the server implementation version. 
     */
    public static final String serverVersion() {
        return "20210715.1246";
    }
    
    static boolean isKey(String key) {
        Pattern p= Pattern.compile("\\d{8}+");
        return p.matcher(key).matches();
    }
    
    /**
     * convert IDs and NAMEs into safe names which will work on all platforms.
     * If the name is modified, it will start with an _. 
     * <li>poolTemperature -> poolTemperature
     * <li>Iowa City Conditions -> _Iowa+City+Conditions
     * @param s
     * @return 
     */
    public static final String fileSystemSafeName( String s ) {
        Pattern p= Pattern.compile("[a-zA-Z0-9\\-\\+\\*\\._]+");
        Matcher m= p.matcher(s);
        if ( m.matches() ) {
            return s;
        } else {
            String s1= s.replaceAll("\\+","2B");
            s1= s1.replaceAll(" ","\\+");
            if ( p.matcher(s1).matches() ) {
                return "_" + s1;
            } else {
                byte[] bb= s.getBytes( Charset.forName("UTF-8") );
                StringBuilder sb= new StringBuilder("_");
                for ( byte b: bb ) {
                    sb.append( String.format("%02X", b) );
                }
                return sb.toString();
            }
        }
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
        keyFile= new File( keyFile, fileSystemSafeName(id) + ".json" );
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
        keyFile= new File( keyFile, fileSystemSafeName(id) + ".json" );
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
        keyFile= new File( keyFile, fileSystemSafeName(id) + ".json" );
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
     * return true if this is valid JSON, false otherwise, and log the exception at SEVERE.
     * @param json
     * @return 
     */
    public static boolean validateJSON( String json ) {
        try {
            new JSONObject( json );
            return true;
        } catch (JSONException ex) {
            Logger.getLogger(Util.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
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
            response.setStatus(404);
            response.setContentType("application/json;charset=UTF-8");
            out.write(s);
            
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * send a "bad id" response to the client.
     * @param id the id.
     * @param ex an exception
     * @param response the response object
     * @param out the print writer for the response object.
     */
    public static void raiseMisconfiguration(String id, Exception ex, HttpServletResponse response, final PrintWriter out) {
        try {
            JSONObject jo= new JSONObject();
            jo.put("HAPI",Util.hapiVersion());
            jo.put("createdAt",String.format("%tFT%<tRZ",Calendar.getInstance(TimeZone.getTimeZone("Z"))));
            JSONObject status= new JSONObject();
            status.put( "code", 1500 );
            String msg= "unrecognized id: "+id + "error: "+ ex.getMessage();
            status.put( "message", msg );
            jo.put("status",status);
            String s= jo.toString(4);
            response.setStatus(200);
            response.setContentType("application/json;charset=UTF-8");
            out.write(s);
        } catch (JSONException jex) {
            throw new RuntimeException(jex);
        }
    }
    
    /**
     * return the total number of elements of each parameter.
     * @param info the info
     * @return an int array with the number of elements in each parameter.
     * @throws JSONException 
     */
    public static int[] getNumberOfElements( JSONObject info ) throws JSONException {
        JSONArray parameters= info.getJSONArray("parameters");
        int[] result= new int[parameters.length()];
        for ( int i=0; i<parameters.length(); i++ ) {
            int len=1;
            if ( parameters.getJSONObject(i).has("size") ) {
                JSONArray jarray1= parameters.getJSONObject(i).getJSONArray("size");
                for ( int k=0; k<jarray1.length(); k++ ) {
                    len*= jarray1.getInt(k);
                }
            }
            result[i]= len;
        }    
        return result;
    }
    
    /**
     * return a new JSONObject for the info request, with the subset of parameters.
     * @param info the root node of the info response.
     * @param parameters comma-delimited list of parameters.
     * @return the new JSONObject, with special tag __indexmap__ showing which columns are to be included in a data response.
     * @throws JSONException 
     */
    public static JSONObject subsetParams( JSONObject info, String parameters ) throws JSONException {
        info= new JSONObject( info.toString() ); // force a copy
        String[] pps= parameters.split(",");
        Map<String,Integer> map= new HashMap();  // map from name to index in dataset.
        Map<String,Integer> iMap= new HashMap(); // map from name to position in csv.
        JSONArray jsonParameters= info.getJSONArray("parameters");
        int index=0;
        int[] lens= getNumberOfElements(info);
        for ( int i=0; i<jsonParameters.length(); i++ ) {
            String name= jsonParameters.getJSONObject(i).getString("name");
            map.put( name, i ); 
            iMap.put( name, index );
            index+= lens[i];
        }
        JSONArray newParameters= new JSONArray();
        int[] indexMap= new int[pps.length];
        for ( int i=0; i<pps.length; i++ ) {
            indexMap[i]=-1;
        }
        int[] lengths= new int[pps.length]; //lengths for the new infos
        boolean hasTime= false;
        for ( int ip=0; ip<pps.length; ip++ ) {
            Integer i= map.get(pps[ip]);
            if ( i==null ) {
                throw new IllegalArgumentException("bad parameter: "+pps[ip]);
            }
            indexMap[ip]= iMap.get(pps[ip]);
            if ( i==0 ) {
                hasTime= true;
            }
            newParameters.put( ip, jsonParameters.get(i) );
            lengths[ip]= lens[i];
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
                    for ( int l=0; l<lengths[k]; l++ ) { 
                        indexMap1[c]= indexMap[k]+l;
                        c++;
                    }
                }
            }
            indexMap= indexMap1;
        }
        if ( indexMap[indexMap.length-1]==-1 ) {
            throw new IllegalArgumentException("last index of index map wasn't set--server implementation error");
        }

        jsonParameters= newParameters;
        info.put( "parameters", jsonParameters );        

        info.put( "x_indexmap", indexMap );
        return info;
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
