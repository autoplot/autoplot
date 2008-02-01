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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.swing.tree.TreeModel;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURL;
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
        
        QDataSet dsvar = createDataSet( var, ds, cmon );

        cmon.close();
        
        return dsvar;
    }

    @Override
    public synchronized TreeModel getMetaData(DasProgressMonitor mon) throws Exception {
        String var = (String) getParams().get("arg_0");

        CefReaderHeader.ParamStruct param = cef.parameters.get(var);
        Map entries= new HashMap( param.entries );
        
        Map restEntries= new HashMap();
        for ( Iterator<String> i=cef.parameters.keySet().iterator();  i.hasNext();  ) {
            String key= i.next();
            CefReaderHeader.ParamStruct parm= cef.parameters.get(key);
            if ( parm.sizes.length==1 && parm.sizes[0]==1 ) {
                restEntries.put( key, "[*] " + parm.entries.get("CATDESC") );
            } else {
                String s= Arrays.toString(parm.sizes);
                s= s.substring(1,s.length()-1);
                restEntries.put( key, "[*,"+s+"] "+ parm.entries.get("CATDESC") );
            }
        }
        entries.put( "CEF", restEntries );
        return NameValueTreeModel.create( "METADATA(CEF)", entries );
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
    private MutablePropertyDataSet createDataSet( String var, MutablePropertyDataSet tds, DasProgressMonitorReadableByteChannel cmon) throws IOException, NumberFormatException, ParseException {
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
            if ( tds==null ) {
                CefReaderData readerd = new CefReaderData();
                tds = readerd.cefReadData(cmon, cef);
            }
            
            if ( param.sizes.length>1 || param.sizes[0]>1 ) {
                ds= DataSetOps.leafTrim( tds, param.cefFieldPos[0], param.cefFieldPos[1]+1 );
            } else {
                ds = DataSetOps.slice1(tds, param.cefFieldPos[0]);
            }
            
            if (param.entries.get("VALUE_TYPE").equals("ISO_TIME")) {
                ds.putProperty(QDataSet.UNITS, Units.us2000);
            }
        }
        String s;
        if ( ( s= (String)param.entries.get( "DEPEND_0" ) )!= null ) {
            MutablePropertyDataSet dep0ds= createDataSet( s, tds, cmon );
            ds.putProperty( QDataSet.DEPEND_0, dep0ds );
        }

        return ds;
    }
    
    
}
