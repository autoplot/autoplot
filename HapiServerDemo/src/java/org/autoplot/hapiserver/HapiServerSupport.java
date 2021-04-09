
package org.autoplot.hapiserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.LoggerManager;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.das2.qds.QDataSet;

/**
 *
 * @author jbf
 */
public class HapiServerSupport {
    
    private static final Logger logger= LoggerManager.getLogger("hapi");
    
    private static Datum myValidTime= TimeUtil.createValid( "2200-01-01T00:00" );
    
    /**
     * return the range of available data. For example, Polar/Hydra data is available
     * from 1996-03-20 to 2008-04-15.
     * @param info
     * @return the range of available data, or null if it is not available.
     */
    public static DatumRange getRange( JSONObject info ) {
        try {
            
            if ( info.has("firstDate") && info.has("lastDate") ) { // this is deprecated behavior
                String firstDate= info.getString("firstDate");
                String lastDate= info.getString("lastDate");
                if ( firstDate!=null && lastDate!=null ) {
                    Datum t1= Units.us2000.parse(firstDate);
                    Datum t2= Units.us2000.parse(lastDate);
                    if ( t1.le(t2) ) {
                        return new DatumRange( t1, t2 );
                    } else {
                        logger.warning( "firstDate and lastDate are out of order, ignoring.");
                    }
                }
            } else if ( info.has("startDate") ) { // note startDate is required.
                String startDate= info.getString("startDate");
                String stopDate;
                if (info.has("stopDate")) {
                    stopDate = info.getString("stopDate");
                } else {
                    stopDate = "now";
                }
                DatumRange tr;
                tr= DatumRangeUtil.parseTimeRange( startDate+"/"+stopDate );
                Datum t1= tr.min();
                Datum t2= tr.max();
                if ( t1.le(t2) ) {
                    return new DatumRange( t1, t2 );
                } else {
                    logger.warning( "firstDate and lastDate are out of order, ignoring.");
                }
            }
        } catch ( JSONException | ParseException ex ) {
            logger.log( Level.WARNING, ex.getMessage(), ex );
        }
        return null;
    }
    
    public static DatumRange getExampleRange(JSONObject info) {
        DatumRange range = getRange(info);
        if (range == null) {
            logger.warning("server is missing required startDate and stopDate parameters.");
            return null;
        } else {
            DatumRange landing;
            if ( info.has("sampleStartDate") && info.has("sampleStopDate") ) {
                try {
                    landing = DatumRangeUtil.parseTimeRange( info.getString("sampleStartDate")+"/"+info.getString("sampleStopDate") );
                } catch (JSONException | ParseException ex) {
                    logger.log(Level.SEVERE, null, ex);
                    return null;
                }
            } else if (range.max().ge(myValidTime)) { // Note stopDate is required since 2017-01-17.
                logger.warning("server is missing required stopDate parameter.");
                landing = new DatumRange(range.min(), range.min().add(1, Units.days));
            } else {
                Datum end = TimeUtil.prevMidnight(range.max());
                landing = new DatumRange(end.subtract(1, Units.days), end);
            }
            return landing;
        }
    }

    /**
     * return the example time range for the dataset.
     * @param id
     * @return
     * @throws IOException
     * @throws FileNotFoundException
     * @throws JSONException 
     */
    public static DatumRange getExampleRange( String id ) throws IOException, FileNotFoundException, JSONException {
        File infoFile= new File( new File( Util.getHapiHome(), "info" ), id+".json" );
        JSONObject info= readJSON( infoFile );
        DatumRange range = getRange(info);
        if (range == null) {
            logger.warning("server is missing required startDate and stopDate parameters.");
            return null;
        } else {
            DatumRange landing;
            if (range.max().ge(myValidTime)) { // Note stopDate is required since 2017-01-17.
                logger.warning("server is missing required stopDate parameter.");
                landing = new DatumRange(range.min(), range.min().add(1, Units.days));
            } else {
                Datum end = TimeUtil.prevMidnight(range.max());
                landing = new DatumRange(end.subtract(1, Units.days), end);
            }
            return landing;
        }
    }
        
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
    
    /**
     * read the JSONObject from the file.
     * @param jsonFile file containing JSONObject.
     * @return the JSONObject
     * @throws FileNotFoundException
     * @throws IOException
     * @throws JSONException 
     */
    public static JSONObject readJSON( File jsonFile ) throws FileNotFoundException, IOException, JSONException {
        logger.entering( "HapiServerSupport", "readJSON", jsonFile );
        StringBuilder builder= new StringBuilder();
        try ( BufferedReader in= new BufferedReader( new FileReader( jsonFile ) ) ) {
            String line= in.readLine();
            while ( line!=null ) {
                builder.append(line);
                line= in.readLine();
            }
        }
        if ( builder.length()==0 ) {
            throw new IOException("file is empty: "+jsonFile);
        }
        try {
            JSONObject catalog= new JSONObject(builder.toString());
            return catalog;
        } catch ( JSONException ex ) {
            logger.log( Level.WARNING, "Exception encountered when reading "+jsonFile, ex );
            throw ex;
        } finally {
            logger.exiting( "HapiServerSupport", "readJSON" );
        }
    }
    
    private static JSONObject getCatalogNew() throws IOException, JSONException {
        try {
            File catalogFile= new File( Util.getHapiHome(), "catalog.json" );
            JSONObject catalog= readJSON(catalogFile);
            return catalog;
        } catch ( IllegalArgumentException ex ) {
            throw new IllegalArgumentException("Util.HAPI_HOME is not set, which might be because the root (hapi/index.jsp) was never loaded.");
        }
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
