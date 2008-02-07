/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.cefdatasource;

import edu.uiowa.physics.pw.das.datum.Units;
import edu.uiowa.physics.pw.das.util.DasProgressMonitor;
import edu.uiowa.physics.pw.das.util.DasProgressMonitor;
import edu.uiowa.physics.pw.das.util.DasProgressMonitorReadableByteChannel;
import edu.uiowa.physics.pw.das.util.NullProgressMonitor;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.swing.tree.TreeModel;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SortDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURL;
import org.virbo.dsutil.DataSetBuilder;
import org.virbo.metatree.NameValueTreeModel;

/**
 *
 * @author jbf
 */
public class CefDataSource extends AbstractDataSource {

    Cef cef;

    public CefDataSource(URL url) {
        super(url);
    }

    public synchronized QDataSet getDataSet(DasProgressMonitor mon) throws Exception {

        File f = DataSetURL.getFile(url, new NullProgressMonitor());
        ReadableByteChannel c = Channels.newChannel(new FileInputStream(f));

        DasProgressMonitorReadableByteChannel cmon = new DasProgressMonitorReadableByteChannel(c, mon);
        cmon.setStreamLength(f.length());

        CefReaderHeader readerh = new CefReaderHeader();

        cef = readerh.read(cmon);

        String var = (String) getParams().get("arg_0");

        MutablePropertyDataSet ds = null;

        QDataSet dsvar = createDataSet(var, ds, cmon);

        cmon.close();

        return dsvar;
    }

    @Override
    public synchronized TreeModel getMetaData(DasProgressMonitor mon) throws Exception {
        String var = (String) getParams().get("arg_0");

        CefReaderHeader.ParamStruct param = cef.parameters.get(var);
        Map entries = new HashMap(param.entries);

        Map restEntries = new HashMap();
        for (Iterator<String> i = cef.parameters.keySet().iterator(); i.hasNext();) {
            String key = i.next();
            CefReaderHeader.ParamStruct parm = cef.parameters.get(key);
            if (parm.sizes.length == 1 && parm.sizes[0] == 1) {
                restEntries.put(key, "[*] " + parm.entries.get("CATDESC"));
            } else {
                String s = Arrays.toString(parm.sizes);
                s = s.substring(1, s.length() - 1);
                restEntries.put(key, "[*," + s + "] " + parm.entries.get("CATDESC"));
            }
        }
        entries.put("CEF", restEntries);
        return NameValueTreeModel.create("METADATA(CEF)", entries);
    }

    
    /**
     * read in the vars, interpret the metadata.  
     * @param var variable to read in.
     * @param ds, null or non-null if the table is already read in.
     * @param cmon, channel available when table must be read in.
     * @return dataset
     * @throws java.io.IOException
     * @throws java.lang.NumberFormatException
     * @throws java.text.ParseException
     */
    private MutablePropertyDataSet createDataSet(String var, MutablePropertyDataSet tds, DasProgressMonitorReadableByteChannel cmon) throws IOException, NumberFormatException, ParseException {
        CefReaderHeader.ParamStruct param = cef.parameters.get(var);

        MutablePropertyDataSet ds;
        if (param.cefFieldPos[0] == -1) {
            String[] data = (String[]) param.entries.get("DATA");
            double[] ddata = new double[data.length];
            for (int i = 0; i < data.length; i++) {
                ddata[i] = Double.parseDouble(data[i]);
            }
            ds = DDataSet.wrap(ddata);
        } else {
            if (tds == null) {
                CefReaderData readerd = new CefReaderData();
                tds = readerd.cefReadData(cmon, cef);
            }

            if (param.sizes.length > 1 || param.sizes[0] > 1) {
                if (tds == null) { // create empty dataset with correct geometry
                    DataSetBuilder result = new DataSetBuilder( 2, 0, param.cefFieldPos[1] - param.cefFieldPos[0] + 1, 1 );
                    ds = result.getDataSet();
                } else {
                    ds = org.virbo.dataset.DataSetOps.leafTrim(tds, param.cefFieldPos[0], param.cefFieldPos[1] + 1);
                }
                if (param.sizes.length > 2) {
                    int[] sizes = new int[param.sizes.length + 1];
                    sizes[0] = ds.length();
                    for (int i = 1; i < sizes.length; i++) {
                        sizes[i] = param.sizes[i - 1];
                    }
                    ds = new ReformDataSet(ds, sizes);
                    if (ds.rank() == 4) {
                        ds = DataSetOps.collapse(ds);
                    }
                }
            } else {
                if ( tds==null ) {
                    ds= DDataSet.createRank1(0);
                } else {
                    ds = org.virbo.dataset.DataSetOps.slice1(tds, param.cefFieldPos[0]);
                }
            }
        }

        if (param.entries.get("VALUE_TYPE").equals("ISO_TIME")) {
            ds.putProperty(QDataSet.UNITS, Units.us2000);
        }

        int[] qube= (int[]) ds.property(QDataSet.QUBE);
        
        String s;
        for (int i = 0; i < 3; i++) {
            if ((s = (String) param.entries.get("DEPEND_" + i)) != null) {
                MutablePropertyDataSet dep0ds = createDataSet(s, tds, cmon);
                if ( dep0ds.rank()>1 ) {
                    QDataSet dp01= (QDataSet) dep0ds.property( QDataSet.DEPEND_0 );
                    QDataSet dp02= (QDataSet) ds.property(QDataSet.DEPEND_0 );
                    if ( dp01.equals( dp02 ) ) {
                        dep0ds= org.virbo.dataset.DataSetOps.slice0( dep0ds, 0 ); // kludge for CLUSTER/PEACE
                        if ( dep0ds.length() > qube[i] ) { // second kludge for CLUSTER/PEACE
                            dep0ds= org.virbo.dataset.DataSetOps.trim( dep0ds, 0, qube[i] );
                        }
                        if ( !org.virbo.dataset.DataSetUtil.isMonotonic(dep0ds) ) {                            
                            QDataSet sort= org.virbo.dataset.DataSetOps.sort(dep0ds);
                            dep0ds= new SortDataSet( dep0ds, sort );
                            ds= makeMonotonic( ds, i, sort );
                        }
                        
                    }
                }
                ds.putProperty("DEPEND_" + i, dep0ds);
            }
        }

        return ds;
    }

    /**
     * @param ds
     * @param idim the dimension being sorted.
     * @param dep0ds
     */
    private MutablePropertyDataSet makeMonotonic( MutablePropertyDataSet ds, int idim, QDataSet sort ) {
        DDataSet cds= DDataSet.copy(ds);
            
        if ( idim==1 ) {
            for ( int i=0;i<ds.length(); i++ ) {
                for ( int j=0; j<ds.length(i); j++ ) {
                    if ( ds.rank()>2 ) {
                        for ( int k=0; k<ds.length(i,j); k++ ) {
                            double d= ds.value(i,j,k);
                            cds.putValue( i, (int)sort.value(j), k, d );                
                        }
                    } else {
                        double d= ds.value(i,j);
                        cds.putValue( i, (int)sort.value(j), d );
                    }
                }
            }
        }
        
        return cds;
    }
}
