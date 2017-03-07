/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.hapi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.das2.datum.DatumRange;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.util.monitor.ProgressMonitor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.DataSourceFormat;
import org.virbo.datasource.URISplit;
import org.virbo.dsops.Ops;

/**
 * Format the QDataSet into HAPI server info and data responses.
 * @author jbf
 */
public class HapiDataSourceFormat implements DataSourceFormat {
    
    @Override
    public void formatData(String uri, QDataSet data, ProgressMonitor mon) throws Exception {
        // file:///home/jbf/hapi?id=mydata
        URISplit split= URISplit.parse(uri);
        Map<String,String> params= URISplit.parseParams(split.params);
        String s= split.file;
        if ( s.startsWith("file://") ) {
            s= s.substring(7);
        } else {
            throw new IllegalArgumentException("uri must start with file://");
        }
        int ix= s.lastIndexOf(".hapi");
        if ( ix==-1 ) {
            throw new IllegalArgumentException("uri must end in .hapi");
        }
        
        File hapiDir= new File( s.substring(0,ix) );
        
        String id= params.get("id");
        if ( id==null || id.length()==0 ) id="data";
        File infoFile= new File( new File( hapiDir, "info" ), id+".json" );
        
        JSONObject jo= new JSONObject();
        jo.put("HAPI","1.1");
        jo.put("createdAt",TimeUtil.now().toString());
        JSONArray parameters= new JSONArray();
        
        List<QDataSet> dss= new ArrayList<>();
        
        String groupTitle= null;
        
        QDataSet dep0= (QDataSet) data.property( QDataSet.DEPEND_0 );
        if ( dep0!=null ) {
            dss.add(dep0);
        }
        if ( data.property(QDataSet.DEPEND_1)==null && SemanticOps.isBundle(data) ) {
            for ( int i=0; i<data.length(); i++ ) {
                dss.add(Ops.unbundle(data,i));
            }
        } else {
            dss.add(data);
        }
        
        groupTitle= (String) data.property(QDataSet.TITLE);
        if ( groupTitle==null ) groupTitle= (String) data.property(QDataSet.LABEL);
        if ( groupTitle==null ) groupTitle= Ops.guessName(data);
        
        int i=0;
        for ( QDataSet ds: dss ) {
            Units u= SemanticOps.getUnits(ds);
            if ( UnitsUtil.isTimeLocation(u) ) {
                JSONObject time= new JSONObject();
                time.put("length", 24 );
                time.put("name", "Time" );
                time.put("type", "isotime" );
                parameters.put(i,time);
            } else {
                JSONObject j1= new JSONObject();
                j1.put("name", Ops.guessName(ds) );
                j1.put("description", ds.property( QDataSet.TITLE ) );
                if ( u!=null && u!=Units.dimensionless ) {
                    j1.put("units", SemanticOps.getUnits(ds) );
                }
                j1.put("type", "double" );
                if ( ds.rank()>1 ) {
                    j1.put("size", DataSetUtil.qubeDims(ds.slice(0)) );
                }
                Number f= (Number)ds.property(QDataSet.FILL_VALUE);
                if ( f!=null ) {
                    j1.put("fill",f); //TODO: check that this is properly handled as Object.
                }                
                parameters.put(i,j1);
            }
            i++;
        }
        
        DatumRange dr= DataSetUtil.asDatumRange( Ops.extent(dep0) );
        jo.put( "startDate", dr.min().toString() );
        jo.put( "stopDate", dr.max().toString() );
        jo.put( "parameters", parameters );
        
        if ( !infoFile.getParentFile().exists() ) {
            infoFile.getParentFile().mkdirs();
        }
        
        try ( FileWriter fw = new FileWriter(infoFile) ) {
            fw.write( jo.toString(4) );
        }
        
        updateCatalog(hapiDir, id, groupTitle);
        
        File capabilitiesFile= new File( hapiDir, "capabilities.json" );
        JSONObject c= new JSONObject();
        c.put("HAPI","1.1");
        JSONArray f= new JSONArray();
        f.put( 0, "csv" );
        f.put( 1, "binary" );
        c.put( "outputFormats", f );
        try ( FileWriter fw = new FileWriter(capabilitiesFile) ) {
            c.write( fw );
            fw.write( c.toString(4) );
        }
    }

    private void updateCatalog(File hapiDir, String id, String groupTitle) throws JSONException, IOException {
        File catalogFile= new File( hapiDir, "catalog.json" );
        JSONObject catalog;
        JSONArray catalogArray;
        if ( catalogFile.exists() ) {
            StringBuilder builder= new StringBuilder();
            try ( BufferedReader in= new BufferedReader( new InputStreamReader( new FileInputStream(catalogFile) ) ) ) {
                String line= in.readLine();
                while ( line!=null ) {                
                    builder.append(line);
                    line= in.readLine();
                }
            }
            catalog= new JSONObject(builder.toString());
            catalogArray= catalog.getJSONArray("catalog");
        } else {
            catalog= new JSONObject();
            catalog.put( "HAPI", "1.1" );
            catalogArray= new JSONArray();
            catalog.put( "catalog", catalogArray );
        }
        JSONObject item;
        int itemIndex=-1;
        for ( int j=0; j<catalogArray.length(); j++ ) {
            JSONObject item1= catalogArray.getJSONObject(j);
            if ( item1.get("id").equals(id) ) {
                itemIndex= j;
            }
        }
        
        if ( itemIndex==-1 ) {
            item= new JSONObject();
            item.put("id", id);
            item.put("title",groupTitle);
            catalogArray.put(catalogArray.length(),item);
        } else {
            item= catalogArray.getJSONObject(itemIndex);
            item.put("id", id);
            item.put("title",groupTitle);
        }
        try ( FileWriter fw = new FileWriter(catalogFile) ) {
            fw.write( catalog.toString(4) );
        }
    }

    @Override
    public boolean canFormat(QDataSet ds) {
        if ( SemanticOps.isJoin(ds) ) { 
            return false;
        }   
        return true;
    }

    @Override
    public String getDescription() {
        return "HAPI Info response";
    }
}
