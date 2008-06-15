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
import java.util.ArrayList;
import java.util.Enumeration;
import javax.swing.tree.TreeModel;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.WritableDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.metatree.NameValueTreeModel;
import dods.dap.Attribute;
import edu.uiowa.physics.pw.das.datum.Units;
import org.das2.util.monitor.NullProgressMonitor;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.virbo.dataset.DataSetUtil;
import org.virbo.datasource.Util;

/**
 *
 * @author jbf
 */
public class DodsDataSource extends AbstractDataSource {

    DodsAdapter adapter;
    String variable;
    String sMyUrl;
    /**
     * null if not specfied in url.
     */
    String constraint;
    /**
     * the metadata
     */
    Map<String,Object> metadata;
    DAS das;

    /**
     * Creates a new instance of DodsDataSetSource
     *
     * http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/genesis/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density
     * http://www.cdc.noaa.gov/cgi-bin/nph-nc/Datasets/kaplan_sst/sst.mean.anom.nc.dds?sst
     * http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/polar/hyd_h0/1997/po_h0_hyd_19970102_v01.cdf.dds?ELECTRON_DIFFERENTIAL_ENERGY_FLUX
     *
     */
    public DodsDataSource(URL url) throws IOException {

        super(url);

        parseUrl();

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

    private void parseUrl() {
        // remove the .dds (or .html) extension.
        String surl = url.toString();
        int i = surl.lastIndexOf('.');
        URL myUrl;
        sMyUrl = surl.substring(0, i);

        // get the variable
        i = surl.indexOf('?');
        variable = surl.substring(i + 1);

        final int ib = variable.indexOf('[');
        if (ib != -1) {
            constraint = "?" + variable;
            variable = variable.substring(0, ib);
        }
    }

    private String getConstraint(DodsAdapter da, Map meta, MyDDSParser parser, String variable) throws DDSException {

        StringBuffer constraint1 = new StringBuffer("?");

        constraint1.append(variable);

        int[] ii = parser.getRecDims(variable);

        for (int i = 0; i < ii.length; i++) {
            constraint1.append("[0:1:" + ii[i] + "]");
        }

        for (int i = 0; i < 3; i++) {
            String dkey = "DEPEND_" + i;
            if (meta.containsKey(dkey)) {
                Map val= (Map) meta.get(dkey);
                String var = (String) val.get("NAME");
                int[] ii2 = parser.getRecDims(var);
                constraint1.append(",").append(var).append("[0:1:" + ii2[0] + "]");
                da.setDependName(i, var);

                Map<String,Object> depMeta= getMetaData(var);
                
                Map m = new IstpMetadataModel().properties(depMeta);

                if ( m.containsKey(QDataSet.UNITS) ) {
                    da.setDimUnits( i,  (Units) m.get(QDataSet.UNITS) );
                }
                

                da.setDimProperties( i, m );
                
            }
        }


        da.setConstraint(constraint1.toString());
        return constraint1.toString();
    }

    public QDataSet getDataSet(ProgressMonitor mon) throws FileNotFoundException, MalformedURLException, IOException, ParseException, DDSException, DODSException {
        
        MyDDSParser parser = new MyDDSParser();
        parser.parse(new URL(adapter.getSource().toString() + ".dds").openStream());

        getMetaData( mon );

        Map interpretedMetadata = null;

        boolean isIstp = adapter.getSource().toString().endsWith(".cdf");
        if ( isIstp ) {
            Map m = new IstpMetadataModel().properties(metadata);
            interpretedMetadata = m;
        }

        if (isIstp) {
            String constraint1 = getConstraint(adapter, metadata, parser, variable);
            adapter.setConstraint(constraint1);

        } else {

            if (this.constraint == null) {
                StringBuffer constraint1 = new StringBuffer("?");
                constraint1.append(adapter.getVariable());
                if (!adapter.getVariable().contains("[")) {
                    int[] ii = parser.getRecDims(adapter.getVariable());
                    for (int i = 0; i < ii.length; i++) {
                        constraint1.append("[0:1:" + ii[i] + "]");
                    }
                }
                adapter.setConstraint(constraint1.toString());
            }
        }

        adapter.loadDataset(mon);
        WritableDataSet ds = (WritableDataSet) adapter.getDataSet();
        
        if ( isIstp ) {
            interpretedMetadata.remove("DEPEND_0");
            interpretedMetadata.remove("DEPEND_1");
            interpretedMetadata.remove("DEPEND_2");
            DataSetUtil.putProperties( interpretedMetadata, ds);
        }
        
        //ds.putProperty( QDataSet.UNITS, null );
        //ds.putProperty( QDataSet.DEPEND_0, null );
        return ds;

    }

    @Override
    public MetadataModel getMetadataModel() {
        if ( url.toString().contains(".cdf.dds") ) {
            return new IstpMetadataModel();
        } else {
            return super.getMetadataModel();
        }
    }


    /**
     * das must be loaded.
     * @param variable
     * @return
     */
    private Map<String,Object> getMetaData(String variable) {

        AttributeTable at = das.getAttributeTable(variable);
        if (at == null) {
            return new HashMap<String,Object>();
        } else {
            Pattern p= Pattern.compile("DEPEND_[0-9]");
            
            Enumeration n = at.getNames();

            Map<String,Object> result= new HashMap<String,Object>();
            
            while (n.hasMoreElements()) {
                Object key = n.nextElement();
                Attribute att = at.getAttribute((String) key);
                try {
                    if ( p.matcher(att.getName()).matches() ) {
                        Object val= att.getValueAt(0);
                        String name= Util.unquote((String)val);
                        Map<String,Object> newVal= getMetaData( name );
                        newVal.put( "NAME", name ); // tuck it away, we'll need it later.
                        result.put( att.getName(), newVal );
                        
                    } else {
                        result.put(att.getName(), att.getValueAt(0));
                    }
                } catch ( Exception e ) {
                    e.printStackTrace();
                }
            }

            return result;
        }
    }

    private Map<String,Object> getMetaData(ProgressMonitor mon, String variable) throws IOException, DASException, ParseException {

        MyDASParser parser = new MyDASParser();
        parser.parse(new URL(adapter.getSource().toString() + ".das").openStream());

        das = parser.getDAS();
        
        return getMetaData( variable );
    }

    @Override
    public synchronized Map<String,Object> getMetaData(ProgressMonitor mon) throws IOException, DASException, ParseException {
        if (metadata == null) {
            metadata = getMetaData(mon, adapter.getVariable());
        }

        return metadata;
    }
}
