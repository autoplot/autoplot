/*
 * DodsDataSetSource.java
 *
 * Created on April 4, 2007, 9:37 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.dods;

import org.virbo.metatree.IstpMetadataModel;
import dods.dap.AttributeTable;
import dods.dap.DAS;
import dods.dap.DASException;
import dods.dap.DDSException;
import dods.dap.DODSException;
import dods.dap.parser.ParseException;
import edu.uiowa.physics.pw.das.util.DasProgressMonitor;
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
import edu.uiowa.physics.pw.das.util.NullProgressMonitor;
import java.util.HashMap;
import java.util.Map;
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
    TreeModel metadata;
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
                String var = Util.unquote((String) meta.get(dkey));
                int[] ii2 = parser.getRecDims(var);
                constraint1.append(",").append(var).append("[0:1:" + ii2[0] + "]");
                da.setDependName(i, var);

                TreeModel depMeta= getMetaData(var);
                
                Map m = new IstpMetadataModel().properties(depMeta);
                da.setDimProperties( i, m );
                
            }
        }


        da.setConstraint(constraint1.toString());
        return constraint1.toString();
    }

    public QDataSet getDataSet(DasProgressMonitor mon) throws FileNotFoundException, MalformedURLException, IOException, ParseException, DDSException, DODSException {
        //if (sMyUrl.endsWith(".cdf")) {
        MyDDSParser parser = new MyDDSParser();
        parser.parse(new URL(adapter.getSource().toString() + ".dds").openStream());

        getMetaData(new NullProgressMonitor());

        Map interpretedMetadata = null;

        boolean isIstp = false;
        if (metadata != null) {
            Map m = new IstpMetadataModel().properties(metadata);
            if (m.containsKey(QDataSet.DEPEND_0)) {
                isIstp = true;
                interpretedMetadata = m;
            }
        }

        if (isIstp) {
            String constraint1 = getConstraint(adapter, interpretedMetadata, parser, variable);
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
        //ds.putProperty( QDataSet.UNITS, null );
        //ds.putProperty( QDataSet.DEPEND_0, null );
        return ds;

    }

    public boolean asynchronousLoad() {
        return true;
    }

    public static DataSourceFactory getFactory() {
        return new DodsDataSourceFactory();
    }

    /**
     * das must be loaded.
     * @param variable
     * @return
     */
    private TreeModel getMetaData(String variable) {
        TreeModel treeresult;

        AttributeTable at = das.getAttributeTable(variable);
        if (at == null) {
            treeresult = NameValueTreeModel.create("metadata(dds)", new HashMap());
            return treeresult;
        } else {

            Enumeration n = at.getNames();

            ArrayList names = new ArrayList();
            ArrayList values = new ArrayList();

            while (n.hasMoreElements()) {
                Object key = n.nextElement();
                Attribute att = at.getAttribute((String) key);
                names.add(att.getName());
                values.add(att.getValueAt(0));
            }

            treeresult = NameValueTreeModel.create("metadata(dds)", names, values);
            return treeresult;
        }
    }

    private TreeModel getMetaData(DasProgressMonitor mon, String variable) throws IOException, DASException, ParseException {

        TreeModel treeresult;

        MyDASParser parser = new MyDASParser();
        parser.parse(new URL(adapter.getSource().toString() + ".das").openStream());

        das = parser.getDAS();
        
        return getMetaData( variable );
    }

    @Override
    public synchronized TreeModel getMetaData(DasProgressMonitor mon) throws IOException, DASException, ParseException {
        if (metadata == null) {
            metadata = getMetaData(mon, adapter.getVariable());
        }

        return metadata;
    }
}
