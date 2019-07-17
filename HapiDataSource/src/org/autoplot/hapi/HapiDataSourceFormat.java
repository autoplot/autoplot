
package org.autoplot.hapi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import org.das2.datum.LoggerManager;
import org.das2.datum.format.TimeDatumFormatter;
import org.das2.qds.FloatReadAccess;
import org.das2.qds.ops.Ops;
import org.das2.qstream.AsciiTimeTransferType;
import org.das2.qstream.DoubleTransferType;
import org.das2.qstream.IntegerTransferType;
import org.das2.qstream.TransferType;

/**
 * Format the QDataSet into HAPI server info and data responses.
 * @author jbf
 */
public class HapiDataSourceFormat implements DataSourceFormat {
    
    private static final Logger logger= LoggerManager.getLogger("apdss.hapi");
    
    @Override
    public void formatData(String uri, QDataSet data, ProgressMonitor mon) throws Exception {
        // file:///home/jbf/hapi?id=mydata
        logger.log(Level.FINE, "formatData {0} {1}", new Object[]{uri, data});
        
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
        hapiDir= new File( hapiDir, "hapi" );
        
        if ( !hapiDir.exists() ) {
            logger.log(Level.FINE, "mkdir {0}", hapiDir);
            if ( !hapiDir.mkdirs() ) {
                throw new IOException("failed to mkdirs: "+hapiDir);
            }
        }
        
        String id= params.get("id");
        if ( id==null || id.length()==0 ) id="data";
        
        String format= params.get("format");
        if ( format==null || format.length()==0 ) format="csv";
        
        File infoFile= new File( new File( hapiDir, "info" ), id+".json" );
        
        JSONObject jo= new JSONObject();
        jo.put("HAPI","2.0");
        //jo.put("createdAt",TimeUtil.now().toString());
        jo.put("modificationDate", TimeUtil.now().toString());
        jo.put( "status", getHapiStatusObject() );
        
        JSONArray parameters= new JSONArray();
        
        List<QDataSet> dss= new ArrayList<>();
        List<FloatReadAccess> ffds= new ArrayList<>();

        String groupTitle;
        
        QDataSet dep0= (QDataSet) data.property( QDataSet.DEPEND_0 );
        if ( dep0!=null ) {
            dss.add(dep0);
            ffds.add(null);
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
        
        FloatReadAccess fra= data.capability(FloatReadAccess.class); // note this might be null
        if ( ( dep1IsOrdinal || data.property(QDataSet.DEPEND_1)==null ) && SemanticOps.isBundle(data) ) {
            for ( int i=0; i<data.length(0); i++ ) {
                dss.add(Ops.unbundle(data,i));
                ffds.add(fra);
            }
        } else {
            dss.add(data);
            ffds.add(fra);
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
                time.put("fill", JSONObject.NULL );
                time.put("units", "UTC" );
                parameters.put(i,time);
            } else {
                JSONObject j1= new JSONObject();
                j1.put("name", Ops.guessName(ds,"data"+i) );
                j1.put("description", ds.property( QDataSet.TITLE ) );
                if ( u==Units.dimensionless ) {
                    j1.put("units", JSONObject.NULL );
                } else {
                    j1.put("units", u.toString() );
                }
                j1.put("type", "double" );
                if ( ds.rank()>1 ) {
                    j1.put("size", DataSetUtil.qubeDims(ds.slice(0)) );
                }
                Number f= (Number)ds.property(QDataSet.FILL_VALUE);
                if ( f!=null ) {
                    j1.put("fill",f.toString()); //TODO: check that this is properly handled as Object.
                } else {
                    j1.put("fill",JSONObject.NULL ); 
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
        c.put("HAPI","2.0");
        JSONArray f= new JSONArray();
        f.put( 0, "csv" );
        f.put( 1, "binary" );
        c.put( "outputFormats", f );
        
        c.put( "status", getHapiStatusObject() );
        try ( FileWriter fw = new FileWriter(capabilitiesFile) ) {
            c.write( fw );
            fw.write( c.toString(4) );
        }
        
        String ext= format.equals("binary") ? ".binary" : ".csv";
        File dataFile= new File( new File( hapiDir, "data" ), id+ ext );
        if ( !dataFile.getParentFile().exists() ) {
            dataFile.getParentFile().mkdirs();
        }

        if ( format.equals("binary") ) {
            TransferType[] tts= new TransferType[dss.size()];
            int nbytes= 0;
            for ( int ids=0; ids<dss.size(); ids++ ) {
                QDataSet ds= dss.get(ids);
                Units u= SemanticOps.getUnits(ds);
                if ( UnitsUtil.isTimeLocation(u) ) {
                    tts[ids]= new AsciiTimeTransferType(24,u);
                } else if ( UnitsUtil.isNominalMeasurement(u) ) {
                    tts[ids]= new IntegerTransferType();
                } else {
                    tts[ids]= new DoubleTransferType();
                }
                nbytes+= tts[ids].sizeBytes();
            }
            int nrec= dss.get(0).length();
            try ( FileOutputStream out= new FileOutputStream(dataFile) ) {
                FileChannel channel= out.getChannel();
                ByteBuffer buf= ByteBuffer.allocate(nbytes);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                for ( int irec=0; irec<nrec; irec++ ) {
                    for ( int ids=0; ids<dss.size(); ids++ ) {
                        QDataSet ds= dss.get(ids);
                        TransferType tt= tts[ids];
                        //Units u= SemanticOps.getUnits(ds);
                        //boolean uIsOrdinal= UnitsUtil.isOrdinalMeasurement(u);
                        //fra= ffds.get(ids); // not used b/c no float transfer types.
                        if ( ds.rank()==1 ) {
                            tt.write( ds.value(irec), buf );
                        } else if ( ds.rank()==2 ) {
                            for ( int j=0; j<ds.length(0); j++ ) {
                                tt.write( ds.value(irec,j), buf );
                            }
                        } else if ( ds.rank()>2 ) {
                            QDataSet ds1= ds.slice(irec);
                            QubeDataSetIterator iter= new QubeDataSetIterator(ds1);
                            while ( iter.hasNext() ) {
                                iter.next();
                                double d= iter.getValue(ds1);
                                tt.write( d, buf );
                            }
                        }
                    }
                    buf.flip();
                    channel.write(buf);
                    buf.flip();
                }
            }
        } else {
            DatumFormatter[] dfs= new DatumFormatter[dss.size()];
            for ( int ids=0; ids<dss.size(); ids++ ) {
                QDataSet ds= dss.get(ids);
                Units u= SemanticOps.getUnits(ds);
                if ( UnitsUtil.isTimeLocation(u) ) {
                    //dfs[ids]= DataSetUtil.bestFormatter(ds);
                    dfs[ids]= new TimeDatumFormatter("yyyy-MM-dd'T'HH:mm:ss.SSS'Z')");
                } else if ( UnitsUtil.isNominalMeasurement(u) ) {
                    dfs[ids]= DataSetUtil.bestFormatter(ds);
                } else {
                    dfs[ids]= DefaultDatumFormatterFactory.getInstance().defaultFormatter();
                }
            }

            int nrec= dss.get(0).length();
            try ( FileWriter fw = new FileWriter(dataFile) ) {
                for ( int irec=0; irec<nrec; irec++ ) {
                    String delim="";
                    for ( int ids=0; ids<dss.size(); ids++ ) {
                        QDataSet ds= dss.get(ids);
                        DatumFormatter df= dfs[ids];
                        Units u= SemanticOps.getUnits(ds);
                        if ( ids>0 ) delim=",";
                        boolean uIsOrdinal= UnitsUtil.isOrdinalMeasurement(u);
                        fra= ffds.get(ids);
                        if ( ds.rank()==1 ) {
                            if ( ids>0 ) fw.write( delim );
                            if ( fra!=null ) {
                                fw.write( String.valueOf( fra.fvalue(irec) ) );
                            } else {
                                fw.write( df.format( u.createDatum(ds.value(irec)), u ) );
                            }
                        } else if ( ds.rank()==2 ) {
                            if ( fra!=null ) {
                                for ( int j=0; j<ds.length(0); j++ ) {
                                    if ( ids>0 ) fw.write( delim );
                                    fw.write( String.valueOf( fra.fvalue(irec,j) ) );
                                }
                            } else {
                                for ( int j=0; j<ds.length(0); j++ ) {
                                    if ( ids>0 ) fw.write( delim );
                                    fw.write( df.format( u.createDatum(ds.value(irec,j)), u ) );
                                }                            
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
                    }
                    fw.write( "\n" );
                }
            }
        }
    }

    private JSONObject getHapiStatusObject() throws JSONException {
        JSONObject jo1= new JSONObject();
        jo1.put("code", 1200 );
        jo1.put("message", "OK request successful");
        return jo1;
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
            catalog.put( "HAPI", "2.0" );
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
        
        catalog.put( "status", getHapiStatusObject() );
        
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
        return !SemanticOps.isJoin(ds);
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
                String desc= (String)dep.property(QDataSet.TITLE);
                if ( desc==null ) desc= (String)dep.property(QDataSet.LABEL);
                if ( dep.rank()==2 ) {
                    if ( SemanticOps.isBins( dep ) ) {
                        String n= Ops.guessName(dep,"dep"+i);
                        Units u= SemanticOps.getUnits(dep);
                        JSONObject jo= new JSONObject();
                        jo.put( "name", n );
                        if ( u==Units.dimensionless ) {
                            jo.put( "units", JSONObject.NULL );
                        } else {
                            jo.put( "units", u.toString() );
                        }
                        JSONArray ranges= new JSONArray();
                        for ( int j=0; j<qube[i]; j++ ) {
                            JSONArray range= new JSONArray();
                            range.put(0,dep.value(j,0));
                            range.put(1,dep.value(j,1));
                            ranges.put(j,range);
                        }
                        jo.put( "ranges", ranges );
                        if ( desc!=null ) jo.put( "description", desc );
                        binsArray.put( i-1, jo ); // -1 is because DEPEND_0 is the streaming index.                        
                    } else {
                        throw new IllegalArgumentException("independent variable must be a simple 1-D array");
                    }
                } else {
                    String n= Ops.guessName(dep,"dep"+i);
                    Units u= SemanticOps.getUnits(dep);
                    JSONObject jo= new JSONObject();
                    jo.put( "name", n );
                    if ( u==Units.dimensionless ) {
                        jo.put( "units", JSONObject.NULL );
                    } else {
                        jo.put( "units", u.toString() );
                    }
                    JSONArray centers= new JSONArray();
                    for ( int j=0; j<qube[i]; j++ ) {
                        centers.put(j,dep.value(j));
                    }
                    jo.put( "centers", centers );
                    if ( desc!=null ) jo.put( "description", desc );
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

    
    //@Override
    public boolean streamData(Map<String, String> params, Iterator<QDataSet> dataIt, OutputStream out) throws Exception {
        String format= params.get("format");
        if ( format==null || format.length()==0 ) format="csv";
        
        WritableByteChannel channel= null;
        OutputStreamWriter fw=null;
        
        if ( format.equals("binary") ) {
            channel= Channels.newChannel(out);
        } else if ( format.equals("csv") ) {
            fw= new OutputStreamWriter(out);
        }
        
        while ( dataIt.hasNext() ) {
            QDataSet data= dataIt.next();
            
            List<QDataSet> dss= new ArrayList<>();
            List<FloatReadAccess> ffds= new ArrayList<>();

            QDataSet dep0= (QDataSet) data.property( QDataSet.CONTEXT_0 );
            if ( dep0!=null ) {
                dss.add(dep0);
                ffds.add(null);
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
            
            FloatReadAccess fra= data.capability(FloatReadAccess.class); // note this might be null
            if ( ( dep1IsOrdinal || data.property(QDataSet.DEPEND_1)==null ) && SemanticOps.isBundle(data) ) {
                for ( int i=0; i<data.length(0); i++ ) {
                    dss.add(Ops.unbundle(data,i));
                    ffds.add(fra);
                }
            } else {
                dss.add(data);
                ffds.add(fra);
            }
            
            if ( format.equals("binary") ) {
                TransferType[] tts= new TransferType[dss.size()];
                int nbytes= 0;
                for ( int ids=0; ids<dss.size(); ids++ ) {
                    QDataSet ds= dss.get(ids);
                    Units u= SemanticOps.getUnits(ds);
                    if ( UnitsUtil.isTimeLocation(u) ) {
                        tts[ids]= new AsciiTimeTransferType(24,u);
                    } else if ( UnitsUtil.isNominalMeasurement(u) ) {
                        tts[ids]= new IntegerTransferType();
                    } else {
                        tts[ids]= new DoubleTransferType();
                    }
                    switch (ds.rank()) {
                        case 0:
                            nbytes+= tts[ids].sizeBytes();
                            break;
                        case 1:
                            nbytes+= tts[ids].sizeBytes()*ds.length();
                            break;
                        default:
                            throw new IllegalArgumentException("not supported!");
                    }
                }

                ByteBuffer buf= ByteBuffer.allocate(nbytes);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                for ( int ids=0; ids<dss.size(); ids++ ) {
                    QDataSet ds= dss.get(ids);
                    TransferType tt= tts[ids];
                    //Units u= SemanticOps.getUnits(ds);
                    //boolean uIsOrdinal= UnitsUtil.isOrdinalMeasurement(u);
                    //fra= ffds.get(ids); // not used b/c no float transfer types.
                    if ( ds.rank()==0 ) {
                        tt.write( ds.value(), buf );
                    } else if ( ds.rank()==1 ) {
                        for ( int j=0; j<ds.length(); j++ ) {
                            tt.write( ds.value(j), buf );
                        }
                    } else if ( ds.rank()>1 ) {
                        QDataSet ds1= ds;
                        QubeDataSetIterator iter= new QubeDataSetIterator(ds1);
                        while ( iter.hasNext() ) {
                            iter.next();
                            double d= iter.getValue(ds1);
                            tt.write( d, buf );
                        }
                    }
                }
                buf.flip();
                assert channel!=null;
                channel.write(buf);
                buf.flip();
                
            } else {
                DatumFormatter[] dfs= new DatumFormatter[dss.size()];
                for ( int ids=0; ids<dss.size(); ids++ ) {
                    QDataSet ds= dss.get(ids);
                    Units u= SemanticOps.getUnits(ds);
                    if ( UnitsUtil.isTimeLocation(u) ) {
                        //dfs[ids]= DataSetUtil.bestFormatter(ds);
                        dfs[ids]= new TimeDatumFormatter("yyyy-MM-dd'T'HH:mm:ss.SSS'Z')");
                    } else if ( UnitsUtil.isNominalMeasurement(u) ) {
                        dfs[ids]= DataSetUtil.bestFormatter(ds);
                    } else {
                        dfs[ids]= DefaultDatumFormatterFactory.getInstance().defaultFormatter();
                    }
                }

//                int nrec= dss.get(0).length();

                assert fw!=null;
                
//                for ( int irec=0; irec<nrec; irec++ ) {
                    String delim="";
                    for ( int ids=0; ids<dss.size(); ids++ ) {
                        QDataSet ds= dss.get(ids);
                        DatumFormatter df= dfs[ids];
                        Units u= SemanticOps.getUnits(ds);
                        if ( ids>0 ) delim=",";
                        boolean uIsOrdinal= UnitsUtil.isOrdinalMeasurement(u);
                        fra= ffds.get(ids);
                        if ( ds.rank()==0 ) {
                            if ( ids>0 ) fw.write( delim );
                            if ( fra!=null ) {
                                fw.write( String.valueOf( fra.fvalue() ) );
                            } else {
                                fw.write( df.format( u.createDatum(ds.value()), u ) );
                            }
                        } else if ( ds.rank()==1 ) {
                            if ( fra!=null ) {
                                for ( int j=0; j<ds.length(); j++ ) {
                                    if ( ids>0 ) fw.write( delim );
                                    fw.write( String.valueOf( fra.fvalue(j) ) );
                                }
                            } else {
                                for ( int j=0; j<ds.length(); j++ ) {
                                    if ( ids>0 ) fw.write( delim );
                                    fw.write( df.format( u.createDatum(ds.value(j)), u ) );
                                }                            
                            }
                        } else if ( ds.rank()>1 ) {
                            QDataSet ds1= ds;
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
                    }
                    fw.write( "\n" );
                //}
            }
        }
        
        if ( fw!=null ) fw.close();
        if ( channel!=null ) channel.close();
        
        return true;
    }
}
