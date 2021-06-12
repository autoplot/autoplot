/*
 * DodsDataSetSource.java
 *
 * Created on April 4, 2007, 9:37 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.autoplot.dods;

import org.autoplot.datasource.MetadataModel;
import org.autoplot.metatree.IstpMetadataModel;
import opendap.dap.AttributeTable;
import opendap.dap.DAS;
import opendap.dap.DASException;
import opendap.dap.DDSException;
import opendap.dap.parser.ParseException;
import org.das2.util.monitor.ProgressMonitor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.AbstractDataSource;
import opendap.dap.Attribute;
import java.net.URI;
import java.util.Collections;
import org.das2.util.monitor.CancelledOperationException;
import org.das2.datum.Units;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import opendap.dap.DAP2Exception;
import opendap.dap.NoSuchAttributeException;
import opendap.dap.Server.InvalidParameterException;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.qds.AbstractDataSet;
import org.das2.qds.DataSetUtil;
import org.das2.qds.MutablePropertyDataSet;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.URISplit;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.TransposeRankNDataSet;

/**
 * Read data via OpenDAP.
 * @author jbf
 */
public class DodsDataSource extends AbstractDataSource {

    DodsAdapter adapter;
    String variable;
    String sMyUrl;
    /**
     * null if not specified in URI.
     */
    String constraint;
    /**
     * the metadata
     */
    Map<String, Object> metadata;
    DAS das;

    private final static Logger logger= Logger.getLogger("apdss.opendap");

    /**
     * check for lat and lon tags, transpose if lat come before lon.
     * If lat and lon are not found or they are already in order, then just return 
     * the dataset.
     * 
     * See http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMAEROe.003/2005/OMI-Aura_L3-OMAEROe_2005m0101_v003-2011m1109t081947.he5.dds?TerrainReflectivity
     * 
     * @param v dataset that might have lat and lon. 
     */
    private MutablePropertyDataSet checkLatLon( MutablePropertyDataSet v ) {
        int lat=-1;
        int lon=-1;
        for ( int i=0; i<v.rank();i++ ) {
            QDataSet dep= (QDataSet) v.property( "DEPEND_"+i );
            if ( dep!=null ) {
                String name= (String) dep.property("NAME");
                if ( "lon".equals(name) ) lon=i;
                if ( "lat".equals(name) ) lat=i;
            }
        }
        if ( lat>-1 && lon>-1 && lat<lon ) {
            int[] order= new int[v.rank()];
            for ( int i=0;i<v.rank(); i++) order[i]= i;
            int t= order[lat];
            order[lat]= order[lon];
            order[lon]= t;
            AbstractDataSet transpose= new TransposeRankNDataSet( v, order );
            return transpose;
        } else {
            return v;
        }
    }    
    
    /**
     * Creates a new instance of DodsDataSetSource
     *
     * CDAWEB no longer supports: http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/genesis/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density
     * No longer operational: http://www.cdc.noaa.gov/cgi-bin/nph-nc/Datasets/kaplan_sst/sst.mean.anom.nc.dds?sst
     * http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMAEROe.003/2005/OMI-Aura_L3-OMAEROe_2005m0101_v003-2011m1109t081947.he5.dds?TerrainReflectivity
     * CDAWEB no longer supports: http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/polar/hyd_h0/1997/po_h0_hyd_19970102_v01.cdf.dds?ELECTRON_DIFFERENTIAL_ENERGY_FLUX
     *
     * @param uri
     * @throws java.io.IOException
     */
    public DodsDataSource(URI uri) throws IOException {

        super(uri);
        logger.entering( "org.autoplot.dods.DodsDataSource", "DodsDataSource {0}", uri );

        // remove the .dds (or .html) extension.
        String surl = uri.getRawSchemeSpecificPart();
        int k= surl.lastIndexOf("?");
        int i = k==-1 ? surl.lastIndexOf('.')  : surl.lastIndexOf('.',k);
        sMyUrl = surl.substring(0, i);

        // get the variable
        i = surl.indexOf('?');
        String variableConstraint;
        if ( i!=-1 ) {
            String s= surl.substring(i + 1);
            s= DataSetURI.maybePlusToSpace(s);
            variableConstraint = URISplit.uriDecode(s);
            StringTokenizer tok= new StringTokenizer(variableConstraint,"[<>",true);
            String name= tok.nextToken();
            
            if ( tok.hasMoreTokens() ) { // get the variable name from the constraint if it's like name[0:100], but not for name>1e7.
                String delim= tok.nextToken();
                if ( delim.equals("[") ) {
                    variable=name;
                } 
                constraint= "?" + variableConstraint;
            } else {
                variable= name;
            }

        }

        URL myUrl;
        try {
            myUrl = new URL(sMyUrl);
            adapter = new DodsAdapter(myUrl, variable);
            if (constraint != null) {
                adapter.setConstraint(constraint);
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
        logger.exiting( "org.autoplot.dods.DodsDataSource", "DodsDataSource {0}", uri );
    }

    private String getIstpConstraint(DodsAdapter da, Map meta, MyDDSParser parser, String variable) throws DDSException {

        StringBuilder constraint1 = new StringBuilder("?");

        constraint1.append(variable);

        String dimsStr=null;
        if ( da.getConstraint()!=null ) {
            int i= da.getConstraint().indexOf('[');
            if (i!=-1) {
                dimsStr= da.getConstraint().substring(i);
                constraint1.append( dimsStr );
            }
        }  else {
            int[] ii = parser.getRecDims(variable);

            if (ii != null) {
                for (int i = 0; i < ii.length; i++) {
                    dimsStr= ""; //TODO: what?
                    constraint1.append(dimsStr);
                }
            }
        }

        for (int i = 0; i < 3; i++) {
            String dkey = "DEPEND_" + i;
            if (meta.containsKey(dkey)) {
                Map val = (Map) meta.get(dkey);
                String var = (String) val.get("NAME");
                //int[] ii2 = parser.getRecDims(var);
                constraint1.append(",").append(var);
                if ( dimsStr!=null) constraint1.append(dimsStr);
                da.setDependName(i, var);

                Map<String, Object> depMeta = getMetaData(var);

                Map m = new IstpMetadataModel().properties(depMeta);

                if (m.containsKey(QDataSet.UNITS)) {
                    da.setDimUnits(i, (Units) m.get(QDataSet.UNITS));
                }

                da.setDimProperties(i, m);

            }
        }


        da.setConstraint(constraint1.toString());
        return constraint1.toString();
    }

    private String getDependsConstraint( DodsAdapter da, Map meta, MyDDSParser parser, String variable, String[] depVars ) throws DDSException {

        StringBuilder constraint1 = new StringBuilder("?");

        constraint1.append(variable);

        String dimsStr=null;
        if ( da.getConstraint()!=null ) {
            int i= da.getConstraint().indexOf('[');
            if (i!=-1) {
                dimsStr= da.getConstraint().substring(i);
                constraint1.append( dimsStr );
            }
        }  else {
            int[] ii = parser.getRecDims(variable);

            if (ii != null) {
                for (int i = 0; i < ii.length; i++) {
                    dimsStr= ""; //TODO: what?
                    constraint1.append(dimsStr);
                }
            }
        }

        for (int i = 0; i < depVars.length; i++) {
                String var = depVars[i];
                //int[] ii2 = parser.getRecDims(var);
                constraint1.append(",").append(var);
                if ( dimsStr!=null && i==0 ) {
                    int i2= dimsStr.indexOf("]");
                    constraint1.append(dimsStr.substring(0,i2+1));
                }
                da.setDependName(i, var);

                //Map<String, Object> depMeta = getMetadata(var);

                //Map m = new IstpMetadataModel().properties(depMeta);

                //if (m.containsKey(QDataSet.UNITS)) {
                //    da.setDimUnits(i, (Units) m.get(QDataSet.UNITS));
                //}

                //da.setDimProperties(i, m);

        }


        da.setConstraint(constraint1.toString());
        return constraint1.toString();
    }
    
    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws FileNotFoundException, MalformedURLException, 
        IOException, ParseException, DDSException, CancelledOperationException, DASException, InvalidParameterException, DAP2Exception {

        logger.entering( "org.autoplot.dods.DodsDataSource", "getDataSet" );
        mon.setTaskSize(-1);
        mon.started();

        String surl= adapter.getSource().toString();
        
        if ( surl==null ) {
            throw new NullPointerException("adapter to URL failed");
        }
        
        mon.setProgressMessage( "parse " + surl+".dds" );

        try {
            MyDDSParser parser = new MyDDSParser();
            URL url= new URL( surl + ".dds");
            logger.log(Level.FINE, "getDataSet opening {0}", url);
            try ( InputStream in= url.openStream() ) {
                parser.parse(in);
            } catch ( FileNotFoundException ex ) {
                throw new FileNotFoundException( "OpenDAP Server unavailable, file not found: \n"+ex.getMessage());
            }

            getMetadata( mon.getSubtaskMonitor("metadata") );

            Map<String,Object> interpretedMetadata = null;

            boolean isIstp = surl.endsWith(".cdf");
            if (isIstp) {
                Map<String,Object> m = new IstpMetadataModel().properties(metadata);
                interpretedMetadata = m;
            }

            if (isIstp) {
                    String constraint1 = getIstpConstraint(adapter, metadata, parser, variable);
                    adapter.setConstraint(constraint1);

            } else {

                String[] deps= parser.getDepends(variable);
                if ( deps!=null ) {
                    String constraint1= getDependsConstraint( adapter, metadata, parser, variable, deps );
                    adapter.setConstraint(constraint1);
                } else {
                    if (this.constraint == null && adapter.getVariable()!=null ) {
                        StringBuilder constraint1 = new StringBuilder("?");
                        constraint1.append(adapter.getVariable());
                        if (!adapter.getVariable().contains("[")) {
                            int[] ii = parser.getRecDims(adapter.getVariable());
                            if (ii != null) {
                                for (int i = 0; i < ii.length; i++) {
                                    constraint1.append("[0:1:").append(ii[i]).append("]");
                                }
                            }
                        }
                        adapter.setConstraint(constraint1.toString());
                    }
                }
            }

            try {
                adapter.loadDataset( mon.getSubtaskMonitor("loadDataset"), metadata );
                
            } catch ( NullPointerException ex ) {
                RuntimeException n= new RuntimeException("Strange NullPointerException occurs with Java 8 Webstart.  This will be resolved, but use the single-jar version of Autoplot instead.",ex);
                throw n;
            }
            
            MutablePropertyDataSet ds = (MutablePropertyDataSet) adapter.getDataSet(metadata);
                
            ds= checkLatLon( ds );
            
            Object val;
            val= metadata.get("missing_value");
            if ( val!=null ) {
                try {
                    double dfill= Double.parseDouble( (String)val );
                    ds.putProperty(QDataSet.FILL_VALUE, dfill );
                    if ( dfill!=0 ) {  // kludge for rounding errors.  I don't know why the normal fuzz isn't working.
                        double check= Math.abs( ( ds.value(0,0) - dfill )/ dfill );
                        if ( check < 0.00001 ) {
                            ds.putProperty( QDataSet.FILL_VALUE, ds.value(0,0) );
                        }
                    }
                } catch ( NumberFormatException ex ) {
                    logger.log( Level.INFO, "When parsing missing_value", ex );
                }
            }
            val= metadata.get("title");
            if ( val!=null ) {
                ds.putProperty( QDataSet.TITLE, String.valueOf(val) );
            } else {
                val= metadata.get("long_name");
                ds.putProperty( QDataSet.TITLE, String.valueOf(val) );
            }
               
            String sunits = (String) metadata.get("units");
            ds= DodsAdapter.checkTimeUnits( sunits, ds );
            
            // check depends
            for ( int i= 0; i<ds.rank(); i++ ) {
                QDataSet dep= (QDataSet) ds.property( "DEPEND_"+i );
                if ( dep!=null ) {
                    String n=(String)dep.property(QDataSet.NAME);
                    adapter.setVariable(n);
                    Map<String,Object> m= getMetaData(n);
                    //adapter.loadDataset(mon, m);
                    adapter.setDependName( i,null );
                    dep= adapter.getDataSet(m);
                    ds.putProperty( "DEPEND_"+i, dep );
                }
            }
            
            if (isIstp ) {
                assert interpretedMetadata!=null;
                interpretedMetadata.remove("DEPEND_0");
                interpretedMetadata.remove("DEPEND_1");
                interpretedMetadata.remove("DEPEND_2");
                DataSetUtil.putProperties(interpretedMetadata, ds);
            }

            try {
                AttributeTable at = das.getAttributeTable(variable);
                Map<String,Object> meta= new LinkedHashMap<>();
                Enumeration en= at.getNames();
                while ( en.hasMoreElements() ) {
                    String n= String.valueOf(en.nextElement());
                    Attribute a= at.getAttribute(n);
                    Iterator i= a.getValuesIterator();
                    if ( i.hasNext() ) {
                        Object o= i.next();
                        meta.put( n, o );
                        if ( n.equals("_FillValue") ) {
                            try {
                                double d= Double.parseDouble(String.valueOf(o));
                                ds.putProperty( QDataSet.FILL_VALUE, d );
                            } catch ( NumberFormatException ex ) {
                                logger.fine("unable to parse fill value");
                            }
                        }
                    }
                }
                ds.putProperty(QDataSet.METADATA,meta);
            } catch ( NoSuchAttributeException ex ) {
                logger.log(Level.WARNING,ex.getMessage(),ex);
            }

            if ( DataSetURI.fromUri(uri).contains(".cdf.dds") ) {
                ds.putProperty( QDataSet.METADATA_MODEL, QDataSet.VALUE_METADATA_MODEL_ISTP );
            }

            //ds.putProperty( QDataSet.UNITS, null );
            //ds.putProperty( QDataSet.DEPEND_0, null );
            return ds;
            
        } finally {
            logger.exiting( "org.autoplot.dods.DodsDataSource", "getDataSet" );
            mon.finished();

        }
    }

    @Override
    public MetadataModel getMetadataModel() {
        if ( DataSetURI.fromUri(uri).contains(".cdf.dds")) {
            return new IstpMetadataModel();
        } else {
            return super.getMetadataModel();
        }
    }

    /**
     * DAS must be loaded.
     * @param variable
     * @return
     */
    protected Map<String, Object> getMetaData(String variable) {
        try {
            AttributeTable at = das.getAttributeTable(variable);
            return getMetadata(at);
        } catch ( NoSuchAttributeException ex ) {
            return Collections.emptyMap();
        }
    }
     
    private Map<String,Object> getMetadata( AttributeTable at ) {
        
        if (at == null) {
            return new HashMap<>();
        } else {
            Pattern p = Pattern.compile("DEPEND_[0-9]");
            Pattern p2 = Pattern.compile("LABL_PTR_([0-9])");
            Enumeration n = at.getNames();

            Map<String, Object> result = new HashMap<>();

            while (n.hasMoreElements()) {
                Object key = n.nextElement();
                Attribute att = at.getAttribute((String) key);
                Matcher m;
                try {
                    int type = att.getType();
                    if (type == Attribute.CONTAINER) {
                        Object val= getMetadata( att.getContainer() );
                        result.put( att.getName(), val );
                    } else {
                        String val = att.getValueAt(0);
                        val = DataSourceUtil.unquote(val);
                        if (p.matcher(att.getName()).matches()) {
                            String name = val;
                            Map<String, Object> newVal = getMetaData(name);
                            newVal.put("NAME", name); // tuck it away, we'll need it later.
                            result.put(att.getName(), newVal);
                        } else if ((m = p2.matcher(att.getName())).matches()) {
                            String name = val;
                            Map<String, Object> newVal = getMetaData(name);
                            newVal.put("NAME", name); // tuck it away, we'll need it later.
                            result.put("DEPEND_" + m.group(1), newVal);

                        } else {
                            if ( val.length()>0 ) {
                                result.put(att.getName(), val);
                            } else {
                                logger.log(Level.FINE, "skipping {0}  because length=0", att.getName());
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.log( Level.WARNING, null, e );
                }
            }

            return result;
        }
    }


    @Override
    public Map<String, Object> getMetadata(ProgressMonitor mon) throws IOException, DASException, ParseException {
        if (metadata == null) {
            MyDASParser parser = new MyDASParser();
            URL url = new URL(adapter.getSource().toString() + ".das");
            logger.log(Level.FINE, "getMetadata opening {0}", url);
            try ( InputStream in= url.openStream() ) {
                parser.parse( in );

                das = parser.getDAS();
                if ( variable==null ) {
                    variable= (String) das.getNames().nextElement();
                    adapter.setVariable(variable);
                }
                metadata = getMetaData(variable);  
            }
        }

        return metadata;
    }
}
