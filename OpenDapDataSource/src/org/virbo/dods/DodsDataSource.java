/*
 * DodsDataSetSource.java
 *
 * Created on April 4, 2007, 9:37 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.dods;

import org.virbo.datasource.MetadataModel;
import org.virbo.metatree.IstpMetadataModel;
import dods.dap.AttributeTable;
import dods.dap.DAS;
import dods.dap.DASException;
import dods.dap.DDSException;
import dods.dap.DODSException;
import dods.dap.parser.ParseException;
import org.das2.util.monitor.ProgressMonitor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import dods.dap.Attribute;
import java.net.URI;
import org.das2.util.monitor.CancelledOperationException;
import org.das2.datum.Units;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.datasource.URISplit;

/**
 *
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

    private final static Logger logger= Logger.getLogger("autoplot.dodsdatasource");

    /**
     * Creates a new instance of DodsDataSetSource
     *
     * http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/genesis/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density
     * http://www.cdc.noaa.gov/cgi-bin/nph-nc/Datasets/kaplan_sst/sst.mean.anom.nc.dds?sst
     * http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/polar/hyd_h0/1997/po_h0_hyd_19970102_v01.cdf.dds?ELECTRON_DIFFERENTIAL_ENERGY_FLUX
     *
     */
    public DodsDataSource(URI uri) throws IOException {

        super(uri);

        // remove the .dds (or .html) extension.
        String surl = uri.getRawSchemeSpecificPart();
        int k= surl.lastIndexOf("?");
        int i = k==-1 ? surl.lastIndexOf('.')  : surl.lastIndexOf('.',k);
        sMyUrl = surl.substring(0, i);

        // get the variable
        i = surl.indexOf('?');
        String variableConstraint= null;
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

    public QDataSet getDataSet(ProgressMonitor mon) throws FileNotFoundException, MalformedURLException, IOException, ParseException, DDSException, DODSException, CancelledOperationException {

        mon.setTaskSize(-1);
        mon.started();

        String label= adapter.getSource().toString();
        mon.setProgressMessage( "parse " + label+".dds" );

        try {
            MyDDSParser parser = new MyDDSParser();
            parser.parse( new URL(adapter.getSource().toString() + ".dds").openStream());

            getMetadata(mon);

            Map<String,Object> interpretedMetadata = null;

            boolean isIstp = adapter.getSource().toString().endsWith(".cdf");
            if (isIstp) {
                Map<String,Object> m = new IstpMetadataModel().properties(metadata);
                interpretedMetadata = m;
            }

            if (isIstp) {
                    String constraint1 = getIstpConstraint(adapter, metadata, parser, variable);
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

            adapter.loadDataset( mon, metadata );
            MutablePropertyDataSet ds = (MutablePropertyDataSet) adapter.getDataSet(metadata);

            if (isIstp) {
                interpretedMetadata.remove("DEPEND_0");
                interpretedMetadata.remove("DEPEND_1");
                interpretedMetadata.remove("DEPEND_2");
                DataSetUtil.putProperties(interpretedMetadata, ds);
            }

            synchronized (this) {
                AttributeTable at = das.getAttributeTable(variable);
                ds.putProperty(QDataSet.METADATA,at);
            }

            if ( DataSetURI.fromUri(uri).contains(".cdf.dds") ) {
                ds.putProperty( QDataSet.METADATA_MODEL, QDataSet.VALUE_METADATA_MODEL_ISTP );
            }

            //ds.putProperty( QDataSet.UNITS, null );
            //ds.putProperty( QDataSet.DEPEND_0, null );
            return ds;
        } finally {
            
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
    private synchronized Map<String, Object> getMetaData(String variable) {
        AttributeTable at = das.getAttributeTable(variable);
        return getMetaData(at);
    }
     
    private Map<String,Object> getMetaData( AttributeTable at ) {
        
        if (at == null) {
            return new HashMap<String, Object>();
        } else {
            Pattern p = Pattern.compile("DEPEND_[0-9]");
            Pattern p2 = Pattern.compile("LABL_PTR_([0-9])");
            Enumeration n = at.getNames();

            Map<String, Object> result = new HashMap<String, Object>();

            while (n.hasMoreElements()) {
                Object key = n.nextElement();
                Attribute att = at.getAttribute((String) key);
                Matcher m = null;
                try {
                    int type = att.getType();
                    if (type == Attribute.CONTAINER) {
                        Object val= getMetaData( att.getContainer() );
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
                                logger.fine("skipping "+att.getName()+"  because length=0");
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return result;
        }
    }


    @Override
    public synchronized Map<String, Object> getMetadata(ProgressMonitor mon) throws IOException, DASException, ParseException {
        if (metadata == null) {
            MyDASParser parser = new MyDASParser();
            URL url = new URL(adapter.getSource().toString() + ".das");
            parser.parse(url.openStream());

            das = parser.getDAS();
            if ( variable==null ) {
                variable= (String) das.getNames().nextElement();
                adapter.setVariable(variable);
            }
            metadata = getMetaData(variable);  
        }

        return metadata;
    }
}
