/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.hapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.print.attribute.HashAttributeSet;
import static org.autoplot.hapi.HapiServer.logger;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.TimeParser;
import org.das2.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DefaultTimeSeriesBrowse;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.dsops.Ops;
import org.virbo.dsutil.BundleBuilder;
import org.virbo.dsutil.DataSetBuilder;

/**
 * HAPI data source uses transactions with HAPI servers to collect data.
 * @author jbf
 */
public class HapiDataSource extends AbstractDataSource {

    protected final static Logger logger= Logger.getLogger("apdss.hapi");
    
    TimeSeriesBrowse tsb;
    
    public HapiDataSource(URI uri) {
        super(uri);
        tsb= new DefaultTimeSeriesBrowse();
        String str= params.get( URISplit.PARAM_TIME_RANGE );
        if ( str!=null ) {
            try {
                tsb.setURI(uri.toString());
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        addCapability( TimeSeriesBrowse.class, tsb );
    }

    public static class HapiParameter {
        String name="";
        String type="";
        
        
    }
    
    public static class HapiDoc {
        String description="";
        
    }
    
    public static final double FILL_VALUE= -1e38;
    
    private JSONObject getDocument( ) throws MalformedURLException, IOException, JSONException {
        URI server = this.resourceURI;
        String id= getParam("id","" );
        if ( id.equals("") ) throw new IllegalArgumentException("missing id");
        URL url= HapiServer.getInfoURL(server.toURL(), id);
        StringBuilder builder= new StringBuilder();
        logger.log(Level.FINE, "getDocument {0}", url.toString());
        try ( BufferedReader in= new BufferedReader( new InputStreamReader( url.openStream() ) ) ) {
            String line= in.readLine();
            while ( line!=null ) {
                builder.append(line);
                line= in.readLine();
            }
        }
        JSONObject o= new JSONObject(builder.toString());
        return o;
    }
    
    private static class ParamDescription {
        boolean hasFill= false;
        double fillValue= -1e38;
        Units units= Units.dimensionless;
        String name= "";
        String description= "";
        private ParamDescription( String name ) {
            this.name= name;
        }
    }
    
    @Override
    public QDataSet getDataSet(ProgressMonitor monitor) throws Exception {
        URI server = this.resourceURI;
        String id= getParam("id","" );
        if ( id.equals("") ) throw new IllegalArgumentException("missing id");

        String pp= getParam("parameters","");
        
        JSONObject doc= getDocument();
        
        JSONArray parameters= doc.getJSONArray("parameters");
        int nparameters= parameters.length();
                
        ParamDescription[] pds= new ParamDescription[nparameters];
        
        for ( int i=0; i<nparameters; i++ ) {
            String name= parameters.getJSONObject(i).getString("name");
            pds[i]= new ParamDescription( name );

            String type;
            if ( parameters.getJSONObject(i).has("type") ) {
                type= parameters.getJSONObject(i).getString("type");
            } else {
                type= "";
            }
            if ( name.equalsIgnoreCase("ISOTIME") || type.equalsIgnoreCase("isotime") ) {
                pds[i].units= Units.us2000;
            } else {
                if ( parameters.getJSONObject(i).has("units") ) {
                    String sunits= parameters.getJSONObject(i).getString("units");
                    pds[i].units= Units.lookupUnits(sunits);
                } else {
                    pds[i].units= Units.dimensionless;
                }
                if ( parameters.getJSONObject(i).has("fill") ) {
                    pds[i].fillValue= pds[i].units.parse(parameters.getJSONObject(i).getString("fill") ).doubleValue( pds[i].units );
                    pds[i].hasFill= true;
                } else {
                    pds[i].fillValue= FILL_VALUE; // when a value cannot be parsed, but it is not identified.
                }
                if ( parameters.getJSONObject(i).has("description") ) {
                    pds[i].description= parameters.getJSONObject(i).getString("description");
                } else {
                    pds[i].description= ""; // when a value cannot be parsed, but it is not identified.
                }
            }
        }
        DatumRange tr; // TSB = DatumRangeUtil.parseTimeRange(timeRange);
        tr= tsb.getTimeRange();
        
        URL url= HapiServer.getDataURL( server.toURL(), id, tr, pp );
        
        JSONArray parametersArray= doc.getJSONArray("parameters");
        int nparam= parametersArray.length(); // this is the actual number sent.
        if ( pp.length()>0 ) {
            String[] pps= pp.split(",");
            Map<String,Integer> map= new HashMap();
            for ( int i=0; i<nparam; i++ ) {
                map.put( parametersArray.getJSONObject(i).getString("name"), i ); // really--should name/id are two names for the same thing...
            }
            nparam= pps.length;
            ParamDescription[] subsetPds= new ParamDescription[pps.length];
            for ( int ip=0; ip<pps.length; ip++ ) {
                int i= map.get(pps[ip]);
                subsetPds[ip]= pds[i];
            }
            pds= subsetPds;
        }
        
        DataSetBuilder builder= new DataSetBuilder(2,100,nparam);

        logger.log(Level.FINE, "getDataSet {0}", url.toString());
        
        try ( BufferedReader in= new BufferedReader( new InputStreamReader( url.openStream() ) ) ) {
            String line= in.readLine();
            while ( line!=null ) {
                String[] ss= line.split(",");
                for ( int i=0; i<nparam; i++ ) {
                    try {
                        builder.putValue( -1, i, pds[i].units.parse(ss[i]) );
                    } catch ( ParseException ex ) {
                        builder.putValue( -1, i, pds[i].fillValue );
                        pds[i].hasFill= true;
                    }
                }
                builder.nextRecord();
                line= in.readLine();
            }
        } catch ( Exception e ) {
            e.printStackTrace();
            throw e;
        }
                
        QDataSet ds= builder.getDataSet();
        QDataSet depend0= Ops.slice1( ds,0 );
        if ( ds.length(0)==2 ) {
            ds= Ops.copy( Ops.slice1( ds, 1 ) );
            ds= Ops.putProperty( ds, QDataSet.DEPEND_0, depend0 );
            ds= Ops.putProperty( ds, QDataSet.NAME, Ops.safeName(pds[1].name) );
            ds= Ops.putProperty( ds, QDataSet.LABEL, pds[1].name );
            ds= Ops.putProperty( ds, QDataSet.TITLE, pds[1].description );
            ds= Ops.putProperty( ds, QDataSet.UNITS, pds[1].units );
            if ( pds[1].hasFill ) {
                ds= Ops.putProperty( ds, QDataSet.FILL_VALUE, pds[1].fillValue );
            }
            
        } else {
            BundleBuilder bdsb= new BundleBuilder(nparameters-1);
            for ( int i=1; i<nparam; i++ ) {
                bdsb.putProperty( QDataSet.NAME, i-1, Ops.safeName(pds[i].name) );
                bdsb.putProperty( QDataSet.LABEL, i-1, pds[i].name );
                bdsb.putProperty( QDataSet.TITLE, i-1, pds[i].description );
                bdsb.putProperty( QDataSet.UNITS, i-1, pds[i].units );
                if ( pds[i].hasFill ) {
                    bdsb.putProperty( QDataSet.FILL_VALUE, i-1,  pds[i].fillValue );
                }
            }
            
            ds= Ops.copy( Ops.trim1( ds, 1, ds.length(0) ) );
            ds= Ops.putProperty( ds, QDataSet.DEPEND_0, depend0 );
            ds= Ops.putProperty( ds, QDataSet.BUNDLE_1, bdsb.getDataSet() );
        }
        
        return ds;
        
    }
    
}
