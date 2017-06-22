
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
import org.das2.datum.format.DatumFormatter;
import org.das2.datum.format.DefaultDatumFormatterFactory;
import org.das2.util.monitor.ProgressMonitor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.das2.qds.QubeDataSetIterator;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.DataSourceFormat;
import org.autoplot.datasource.URISplit;
import org.das2.qds.ops.Ops;

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
        
        String groupTitle;
        
        QDataSet dep0= (QDataSet) data.property( QDataSet.DEPEND_0 );
        if ( dep0!=null ) {
            dss.add(dep0);
        } else {
            throw new IllegalArgumentException("data must have a DEPEND_0");
        }
        
        boolean dep1IsOrdinal= false;
        QDataSet dep1= (QDataSet)data.property(QDataSet.DEPEND_1);
        if ( dep1!=null && dep1.rank()==1 ) {
            if ( UnitsUtil.isOrdinalMeasurement( SemanticOps.getUnits(dep1) ) ) {
                dep1IsOrdinal= true;
            } else {
                dep1IsOrdinal= true;
                for ( int i=0; dep1IsOrdinal && i<dep1.length(); i++ ) {
                    if ( dep1.value(i)!=(i+1) ) { // silly vap+cdaweb:ds=THA_L1_STATE&filter=pos&id=tha_pos&timerange=2016-10-02
                        dep1IsOrdinal= false;
                    }
                }
            }
        }
        if ( ( dep1IsOrdinal || data.property(QDataSet.DEPEND_1)==null ) && SemanticOps.isBundle(data) ) {
            for ( int i=0; i<data.length(0); i++ ) {
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
                time.put("fill", "NaN" );
                parameters.put(i,time);
            } else {
                JSONObject j1= new JSONObject();
                j1.put("name", Ops.guessName(ds,"data"+i) );
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
                } else {
                    j1.put("fill","NaN"); 
                }
                if ( ds.rank()>=2 ) {
                    j1.put("bins", getBinsFor(ds) );
                }
                parameters.put(i,j1);
            }
            i++;
        }
        
        DatumRange dr= DataSetUtil.asDatumRange( Ops.extent(dep0) );
        jo.put( "startDate", dr.min().toString() );
        jo.put( "stopDate", dr.max().toString() );
        jo.put( "sampleStartDate", dr.min().toString() );
        jo.put( "sampleStopDate", dr.max().toString() );
        jo.put( "parameters", parameters );
        
        File parentFile= infoFile.getParentFile();
        if ( parentFile==null ) throw new IllegalArgumentException("info has no parent");
        if ( !parentFile.exists() ) {
            if ( !parentFile.mkdirs() ) {
                throw new IllegalArgumentException("unable to make folder for info file.");
            }
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
        
        File dataFile= new File( new File( hapiDir, "data" ), id+".csv" );
        if ( !dataFile.getParentFile().exists() ) {
            dataFile.getParentFile().mkdirs();
        }
        DatumFormatter[] dfs= new DatumFormatter[dss.size()];
        for ( int ids=0; ids<dss.size(); ids++ ) {
            QDataSet ds= dss.get(ids);
            Units u= SemanticOps.getUnits(ds);
            if ( UnitsUtil.isTimeLocation(u) ) {
                dfs[ids]= DataSetUtil.bestFormatter(ds);
            } else if ( UnitsUtil.isNominalMeasurement(u) ) {
                dfs[ids]= DataSetUtil.bestFormatter(ds);
            } else {
                dfs[ids]= DefaultDatumFormatterFactory.getInstance().defaultFormatter();
            }
        }
        int nrec= dss.get(0).length();
        try ( FileWriter fw = new FileWriter(dataFile) ) {
            for ( int irec=0; irec<nrec; irec++ ) {
                int ids=0;
                String delim="";
                for ( QDataSet ds: dss ) {
                    DatumFormatter df= dfs[ids];
                    Units u= SemanticOps.getUnits(ds);
                    if ( ids>0 ) delim=",";
                    boolean uIsOrdinal= UnitsUtil.isOrdinalMeasurement(u);
                    if ( ds.rank()==1 ) {
                        if ( ids>0 ) fw.write( delim );
                        fw.write( df.format( u.createDatum(ds.value(irec)), u ) );
                    } else if ( ds.rank()==2 ) {
                        for ( int j=0; j<ds.length(0); j++ ) {
                            if ( ids>0 ) fw.write( delim );
                            fw.write( df.format( u.createDatum(ds.value(irec,j)), u ) );
                        }
                    } else if ( ds.rank()>2 ) {
                        QDataSet ds1= ds.slice(irec);
                        QubeDataSetIterator iter= new QubeDataSetIterator(ds1);
                        while ( iter.hasNext() ) {
                            iter.next();
                            double d= iter.getValue(ds1);
                            if ( ids>0 ) fw.write( delim );
                            if ( uIsOrdinal ) {
                                fw.write("\"");
                                fw.write( df.format( u.createDatum(d), u ) );
                                fw.write("\"");
                            } else {
                                fw.write( df.format( u.createDatum(d), u ) );
                            }
                        }
                    }
                    ids++;
                }
                fw.write( "\n" );
            }
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

    private JSONArray getBinsFor(QDataSet ds) throws JSONException {
        if ( false ) {
            throw new IllegalArgumentException("unsupported rank, must be 2");
        } else {
            JSONArray binsArray= new JSONArray();
            int[] qube= DataSetUtil.qubeDims(ds);
            for ( int i=1; i<ds.rank(); i++ ) {
                QDataSet dep= (QDataSet) ds.property("DEPEND_"+i);
                if ( dep==null ) dep= Ops.findgen(qube[i]);
                if ( dep.rank()==2 ) {
                    if ( SemanticOps.isBins( dep ) ) {
                        String n= Ops.guessName(dep,"dep"+i);
                        Units u= SemanticOps.getUnits(dep);
                        JSONObject jo= new JSONObject();
                        jo.put( "name", n );
                        jo.put( "units", u.toString() );
                        JSONArray ranges= new JSONArray();
                        for ( int j=0; j<qube[i]; j++ ) {
                            JSONArray range= new JSONArray();
                            range.put(0,dep.value(j,0));
                            range.put(1,dep.value(j,1));
                            ranges.put(j,range);
                        }
                        jo.put( "ranges", ranges );
                        binsArray.put( i-1, jo ); // -1 is because DEPEND_0 is the streaming index.                        
                    } else {
                        throw new IllegalArgumentException("independent variable must be a simple 1-D array");
                    }
                } else {
                    String n= Ops.guessName(dep,"dep"+i);
                    Units u= SemanticOps.getUnits(dep);
                    JSONObject jo= new JSONObject();
                    jo.put( "name", n );
                    jo.put( "units", u.toString() );
                    JSONArray centers= new JSONArray();
                    for ( int j=0; j<qube[i]; j++ ) {
                        centers.put(j,dep.value(j));
                    }
                    jo.put( "centers", centers );
                    
                    QDataSet binMax= (QDataSet) dep.property(QDataSet.BIN_MAX);
                    QDataSet binMin= (QDataSet) dep.property(QDataSet.BIN_MIN);
                    if ( binMin!=null && binMax!=null ) {
                        JSONArray ranges= new JSONArray();
                        for ( int j=0; j<qube[i]; j++ ) {
                            JSONArray range= new JSONArray();
                            range.put(0,binMin.value(j,0));
                            range.put(1,binMax.value(j,1));
                            ranges.put(j,range);
                        }
                        jo.put( "ranges", ranges );
                    }
                    binsArray.put( i-1, jo ); // -1 is because DEPEND_0 is the streaming index.
                }
            }
            return binsArray;
        }
    }
}
