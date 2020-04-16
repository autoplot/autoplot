
package org.hapiserver;

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.autoplot.hapi.HapiDataSource;
import org.das2.util.filesystem.FileSystem;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Utility classes for interacting with a HAPI server.  JSON
 * responses are presented using JSON, and data responses are record-by-record.
 * @author jbf
 */
public class HapiClient {
    
    private static final Logger logger= Logger.getLogger("org.hapiserver");
    
    private static final Lock lock= new ReentrantLock();
    
    private HapiClient() {
        
    }
    
    /**
     * use cache of HAPI responses, to allow for use in offline mode.
     * @return 
     */
    protected static boolean useCache() {
        return ( "true".equals( System.getProperty("hapiServerCache","false") ) );
    }
    
    /**
     * allow cached files to be used for no more than 1 hour.
     * @return 
     */
    protected static long cacheAgeLimitMillis() {
        return 3600000;
    }
    
    /**
     * read the file into a string.  
     * @param f non-empty file
     * @return String containing file contents.
     * @throws IOException 
     */
    public static String readFromFile( File f ) throws IOException {
        StringBuilder builder= new StringBuilder();
        try ( BufferedReader in= new BufferedReader( new InputStreamReader( new FileInputStream(f) ) ) ) {
            String line= in.readLine();
            while ( line!=null ) {
                builder.append(line);
                builder.append("\n");
                line= in.readLine();
            }
        }
        if ( builder.length()==0 ) {
            throw new IOException("file is empty:" + f );
        }
        String result=builder.toString();
        return result;
    }
        
    /**
     * return the resource, if cached, or null if the data is not cached.
     * @param url
     * @param type "json" (the extension) or "" for no additional extension.
     * @return the data or null.
     * @throws IOException 
     */
    public static String readFromCachedURL( URL url, String type ) throws IOException {
        
        String hapiCache= HapiDataSource.getHapiCache();
        
        String u= url.getProtocol() + "/" + url.getHost() + "/" + url.getPath();
        if ( url.getQuery()!=null ) {
            Pattern p= Pattern.compile("id=(.+)");
            Matcher m= p.matcher(url.getQuery());
            if ( m.matches() ) {
                u= u + "/" + m.group(1);
                if ( type.length()>0 ) u= u+ "." + type;
            } else {
                throw new IllegalArgumentException("query not supported, implementation error");
            }
        } else {
            if ( type.length()>0 ) u= u + "." + type;
        }
        String su= hapiCache + u;
        File f= new File(su);
        if ( f.exists() && f.canRead() ) {
            if ( ( System.currentTimeMillis() - f.lastModified() < cacheAgeLimitMillis() ) || FileSystem.settings().isOffline() ) {
                logger.log(Level.FINE, "read from hapi cache: {0}", url);
                String r= readFromFile( f );
                return r;
            } else {
                logger.log(Level.FINE, "old cache item will not be used: {0}", url);
                return null;
            }
        } else {
            return null;
        }
    }

    
    /**
     * write the data (for example, an info response) to a cache file.  This is called
     * from readFromURL to cache the data.
     * @param url the resource location, query param id is handled specially, but others are ignored.
     * @param type "json" (the extension), or "" if no extension should be added.
     * @param data the data.
     * @throws IOException 
     */
    public static void writeToCachedURL( URL url, String type, String data ) throws IOException {
        
        String hapiCache= HapiDataSource.getHapiCache();
        
        String u= url.getProtocol() + "/" + url.getHost() + "/" + url.getPath();
        String q= url.getQuery();
        if ( q!=null ) {
            if ( q.contains("resolve_references=false&") ) {
                q= q.replace("resolve_references=false&","");
            }
            Pattern p= Pattern.compile("id=(.+)");
            Matcher m= p.matcher(q);
            if ( m.matches() ) {
                u= u + "/" + m.group(1);
                if ( type.length()>0 ) {
                    u= u + "." + type;
                }
            } else {
                throw new IllegalArgumentException("query not supported, implementation error");
            }
        } else {
            if ( type.length()>0 ) {
                u= u + "." + type;
            }
        }
        
        String su= hapiCache + u;
        File f= new File(su);
        if ( f.exists() ) {
            if ( !f.delete() ) {
                throw new IOException("unable to delete file " + f );
            }
        }
        if ( !f.getParentFile().exists() ) {
            if ( !f.getParentFile().mkdirs() ) {
                throw new IOException("unable to make parent directories");
            }
        }
        if ( !f.exists() ) {
            logger.log(Level.FINE, "write to hapi cache: {0}", url);
            try ( BufferedWriter w= new BufferedWriter( new FileWriter(f) ) ) {
                w.write(data);
            }
        } else {
            throw new IOException("unable to write to file: "+f);
        }
    }
    
    /**
     * read data from the URL.  
     * @param url the URL to read from
     * @param type the extension to use for the cache file (JSON, bin, txt).
     * @return non-empty string
     * @throws IOException 
     */
    public static String readFromURL( URL url, String type ) throws IOException {
        
        if ( FileSystem.settings().isOffline() ) {
            String s= readFromCachedURL( url, type );
            if ( s!=null ) return s;
        }
        logger.log(Level.FINE, "GET {0}", new Object[] { url } );
        URLConnection urlc= url.openConnection();
        urlc.setConnectTimeout( FileSystem.settings().getConnectTimeoutMs() );
        urlc.setReadTimeout( FileSystem.settings().getReadTimeoutMs() );
        StringBuilder builder= new StringBuilder();
        try ( BufferedReader in= new BufferedReader( new InputStreamReader( urlc.getInputStream() ) ) ) {
            String line= in.readLine();
            while ( line!=null ) {
                builder.append(line);
                builder.append("\n");
                line= in.readLine();
            }
        } catch ( IOException ex ) {
            if ( urlc instanceof HttpURLConnection ) {
                StringBuilder builder2= new StringBuilder();
                try ( BufferedReader in2= new BufferedReader( new InputStreamReader( ((HttpURLConnection)urlc).getErrorStream() ) ) ) {
                    String line= in2.readLine();
                    while ( line!=null ) {
                        builder2.append(line);
                        builder2.append("\n");
                        line= in2.readLine();
                    }
                    String s2= builder2.toString().trim();
                    if ( type.equals("json") && s2.length()>0 && s2.charAt(0)=='{' ) {
                        logger.warning("incorrect error code returned, content is JSON");
                        return s2;
                    }
                } catch ( IOException ex2 ) {
                    logger.log( Level.FINE, ex2.getMessage(), ex2 );
                }
            }
            logger.log( Level.FINE, ex.getMessage(), ex );
            lock.lock();
            try {
                if ( useCache() ) {
                    String s= readFromCachedURL( url, type );
                    if ( s!=null ) return s;
                } else {
                    throw ex;
                }
            } finally {
                lock.unlock();
            }
        }
        
        if ( builder.length()==0 ) {
            throw new IOException("empty response from "+url );
        }
        String result=builder.toString();
        
        lock.lock();
        try {
            if ( useCache() ) {
                writeToCachedURL( url, type, result );
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * get the info for the id
     * @param server
     * @param id HAPI dataset identifier, matching [a-zA-Z_]+[a-zA-Z0-9_/]*
     * @return the JSON for info, see 
     * @throws java.io.IOException 
     * @throws org.json.JSONException 
     */
    public static org.json.JSONObject getInfo( URL server, String id ) 
            throws IOException, JSONException {
        if ( EventQueue.isDispatchThread() ) {
            logger.warning("HAPI network call on event thread");
        }        
        URL url;
        try {
            url= new URL( server, "info?id="+id );
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(ex);
        }
        
        String s= readFromURL(url, "json");
        
        JSONObject o= new JSONObject(s);
        return o;
    }
    
    /**
     * return the catalog as a JSONObject.  For example:
     * <code><pre>
     * jo= getCatalog( URL( "https://jfaden.net/HapiServerDemo/hapi/catalog" ) )
     * for j in jo.getJsonArray( 'catalog' ):
     *    print j.get('id')
     * </pre></code>
     * @param server
     * @return 
     * @throws java.io.IOException 
     * @throws org.json.JSONException 
     */
    public static org.json.JSONObject getCatalog( URL server ) throws IOException, JSONException {
        if ( EventQueue.isDispatchThread() ) {
            logger.warning("HAPI network call on event thread");
        }        
        URL url;
        url= new URL( server, "catalog" );
        String s= readFromURL(url, "json");
        JSONObject o= new JSONObject(s);
        return o;
    }
    
    /**
     * return the catalog as a JSONArray.
     * <code><pre>
     * jo= getCatalogArray( URL( "https://jfaden.net/HapiServerDemo/hapi/catalog" ) )
     * for i in xrange(jo.length()):
     *    print jo.get(i,'id')
     * </pre></code>
     * @param server
     * @return
     * @throws IOException
     * @throws JSONException 
     */
    public static org.json.JSONArray getCatalogArray( URL server ) throws IOException, JSONException {
        JSONObject jo= getCatalog(server);
        JSONArray ja= jo.getJSONArray("catalog");
        return ja;
    }
    /**
     * return the data record-by-record, using the CSV response.
     * @param server
     * @param id
     * @param startTime
     * @param endTime
     * @return Iterator, which will return records until the stream is empty.
     * @throws java.io.IOException  
     * @throws org.json.JSONException should the server return an invalid response.
     */
    public static Iterator<HapiRecord> getDataCSV( 
            URL server, 
            String id, 
            String startTime,
            String endTime ) throws IOException, JSONException {
        
        JSONObject info= getInfo( server, id );
        
        URL dataURL= new URL( server, "data?id="+id + "&time.min="+startTime + "&time.max="+endTime );
        
        InputStream ins= dataURL.openStream();
        BufferedReader reader= new BufferedReader( new InputStreamReader(ins) );
        return new HapiClientIterator( info, reader );
        
    }
    
    /**
     * return the data record-by-record
     * @param server the HAPI server
     * @param id the dataset id
     * @param startTime the start time
     * @param endTime the end time
     * @return the records in an iterator
     * @throws java.io.IOException
     * @throws org.json.JSONException should the server return an invalid response
     */
    public static Iterator<HapiRecord> getData( 
            URL server, 
            String id, 
            String startTime,
            String endTime ) throws IOException, JSONException {
        return getDataCSV(server, id, startTime, endTime);
    }
    
    /**
     * return the time as milliseconds since 1970-01-01T00:00Z.  This
     * will include leap seconds.
     * @param time
     * @return number of milliseconds since 1970-01-01T00:00Z.
     */
    public static long toMillisecondsSince1970( String time ) {
        TemporalAccessor ta = DateTimeFormatter.ISO_INSTANT.parse(time);
        Instant i = Instant.from(ta);
        Date d = Date.from(i);
        return d.getTime();
    }
    
    /**
     * fast parser requires that each character of string is a digit.
     * @param s
     * @return 
     */
    private static int parseInt( String s ) {
        int result;
        switch ( s.length() ) {
            case 2:
                result= 10 * ( s.charAt(0)-48 ) + (s.charAt(1)-48);
                return result;
            case 3:
                result= 100 * ( s.charAt(0)-48 ) + 10 * ( s.charAt(1)-48 ) + (s.charAt(2)-48);
                return result;
            default:        
                result=0;
                for ( int i=0; i<s.length(); i++ ) {
                    result= 10 * result + (s.charAt(i)-48);
                }
                return result;
        }
    }

    private final static int[][] daysInMonth = {
        {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31, 0},
        {0, 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31, 0}
    };
    
    private final static int[][] dayOffset = {
        {0, 0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365},
        {0, 0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335, 366}
    };
    
    private static boolean isLeapYear( int year ) {
        if ( year<1800 || year>2400 ) {
            throw new IllegalArgumentException("year must be between 1800 and 2400");
        }
        return (year % 4)==0 && ( year%400==0 || year%100!=0 );
    }
    
    /**
     * return the doy of year of the month and day for the year.  For example
     * dayOfYear( 2000, 5, 29 ) -> 150.
     * @param year the year
     * @param month the month, from 1 to 12.
     * @param day the day in the month.
     * @return the day of year.
     */
    public static int dayOfYear( int year, int month, int day ) {
        if ( month==1 ) {
            return day;
        }
        if ( month<1 ) throw new IllegalArgumentException("month must be greater than 0.");
        if ( month>12 ) throw new IllegalArgumentException("month must be less than 12.");
        int leap= isLeapYear(year) ? 1: 0;
        return dayOffset[leap][month] + day;
    }
    
    /**
     * normalize the decomposed time by expressing day of year and month
     * and day of month, and moving hour="24" into the next day.
     * @param time 
     */
    private static void normalizeTime( int[] time ) {
        if ( time[3]==24 ) {
            time[2]+= 1;
            time[3]= 0;
        }
        
        if ( time[3]>24 ) throw new IllegalArgumentException("time[3] is greater than 24 (hours)");
        if ( time[1]>12 ) throw new IllegalArgumentException("time[1] is greater than 12 (months)");
        
        int leap= isLeapYear(time[0]) ? 1: 0;
        
        int d= daysInMonth[leap][time[1]];
        while ( time[2]>d ) {
            time[1]++;
            time[2]-= d;
            d= daysInMonth[leap][time[1]];
            if ( time[1]>12 ) throw new IllegalArgumentException("time[2] is too big");
        }
        
    }
    
    /**
     * return array [ year, months, days, hours, minutes, seconds, nanoseconds ]
     * @param time
     * @return the decomposed time
     */
    public static int[] isoTimeToArray( String time ) {
        int[] result;
        if ( time.length()==4 ) {
            result= new int[] { Integer.parseInt(time), 1, 1, 0, 0, 0, 0 };
        } else {
            if ( time.length()<8 ) throw new IllegalArgumentException("time must have 4 or greater than 7 elements");
            if ( time.charAt(8)=='T' ) {
                result= new int[] { 
                    parseInt(time.substring(0,4)),
                    1, 
                    parseInt(time.substring(5,8)),  // days
                    0, 
                    0, 
                    0,
                    0 
                };
                time= time.substring(9);
            } else {
                result= new int[] { 
                    parseInt(time.substring(0,4)), 
                    parseInt(time.substring(5,7)),
                    parseInt(time.substring(8,10)),
                    0,
                    0,
                    0,
                    0
                };
                time= time.substring(11);
            }
            if ( time.endsWith("Z") ) time= time.substring(0,time.length()-1);
            if ( time.length()>=2 ) {
                result[3]= parseInt(time.substring(0,2));
            }
            if ( time.length()>=5 ) {
                result[4]= parseInt(time.substring(3,5));
            }
            if ( time.length()>=8 ) {
                result[5]= parseInt(time.substring(6,8));
            } 
            if ( time.length()>9 ) {
                result[6]= (int)( Math.pow( 10, 18-time.length() ) ) * parseInt(time.substring(9));
            }
            normalizeTime(result);
        }
        return result;        
    }
    
    
}
