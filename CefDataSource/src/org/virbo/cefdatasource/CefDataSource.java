/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.cefdatasource;

import org.das2.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.DasProgressMonitorReadableByteChannel;
import org.das2.util.monitor.NullProgressMonitor;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.das2.datum.EnumerationUnits;
import org.virbo.cefdatasource.CefReaderHeader.ParamStruct;
import org.virbo.dataset.ArrayDataSet;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SortDataSet;
import org.virbo.dataset.WritableDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.MetadataModel;
import org.virbo.datasource.URISplit;
import org.virbo.dsutil.DataSetBuilder;

/**
 *
 * @author jbf
 */
public class CefDataSource extends AbstractDataSource {

    Cef cef;
    String dsid;

    public CefDataSource(URI uri) {
        super(uri);
        URISplit split= URISplit.parse( DataSetURI.fromUri(uri) );
        String file= split.file.substring(split.path.length());
        int i= file.indexOf("__");
        if ( i!=-1 ) {
            dsid= file.substring(0,i);
        } else {
            dsid= null;
        }
    }

    public synchronized QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        File f = DataSetURI.getFile(uri, new NullProgressMonitor());
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
    public synchronized Map<String, Object> getMetadata( ProgressMonitor mon) throws Exception {
        String var = (String) getParams().get("arg_0");
        CefReaderHeader.ParamStruct param = cef.parameters.get(var);

        return getMetaData( param, mon );
    }

    public synchronized Map<String, Object> getMetaData( CefReaderHeader.ParamStruct param, ProgressMonitor mon) throws Exception {

        Map<String, Object> entries = new LinkedHashMap();

        for ( int i=0; i<QDataSet.MAX_RANK; i++ ) {
            String dep= (String) param.entries.get("DEPEND_"+i);
            if ( dep!=null && !dep.equals("") ) {
                Map<String,Object> depMeta= getMetaData( cef.parameters.get(dep), new NullProgressMonitor() );
                entries.put( "DEPEND_"+i, depMeta );
            }
        }

        entries.putAll(param.entries);

        Map<String, Object> restEntries = new HashMap();
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
        return entries;
    }

    /**
     * read in the vars, interpret the metadata.  
     * @param var variable to read in.
     * @param dsid, null or non-null if the table is already read in.
     * @param cmon, channel available when table must be read in.
     * @return dataset
     * @throws java.io.IOException
     * @throws java.lang.NumberFormatException
     * @throws java.text.ParseException
     */
    private MutablePropertyDataSet createDataSet(String var, MutablePropertyDataSet tds, DasProgressMonitorReadableByteChannel cmon) throws IOException, NumberFormatException, ParseException {
        CefReaderHeader.ParamStruct param = cef.parameters.get(var);

        if ( param==null ) {
            throw new IllegalArgumentException("no such dataset: "+var);
        }
        int collapseDim = 999; // >999 indicates a dimension was collapsed to reduce rank.
        int rank0; // rank before collapse

        Units u = Units.dimensionless;
        double fill = u.getFillDouble();
        String sceffill = (String) param.entries.get("FILLVAL");

        double ceffill;
        if (!param.entries.get("VALUE_TYPE").equals("ISO_TIME")) {
            try { // C1_CP_WHI_ACTIVE had "?" for fill.
                ceffill = (sceffill != null) ? Double.parseDouble(sceffill) : fill;
            } catch ( NumberFormatException ex ) {
                ceffill= fill;
            }
        } else {
            ceffill = fill;
        }

        MutablePropertyDataSet ds;
        if (param.cefFieldPos[0] == -1) { // data is inside the CEF header
            String[] data = (String[]) param.entries.get("DATA");
            double[] ddata = new double[data.length];
            for (int i = 0; i < data.length; i++) {
                try {
                    ddata[i] = Double.parseDouble(data[i]);
                    if (ddata[i] == ceffill) {
                        ddata[i] = fill;
                    }
                } catch (NumberFormatException ex) {
                    throw new NumberFormatException("format error in data of param.name=" + param.name + ": " + data[i]);
                }
            }
            ds = DDataSet.wrap(ddata);
            setDsName( var, ds );
            rank0 = ds.rank();

        // TODO: check for fill
        } else { // data should be extracted from rank 2 table.
            if (tds == null) {
                CefReaderData readerd = new CefReaderData();
                for (int i = 0; i < CefReaderData.MAX_FIELDS; i++) {
                    readerd.skipParse(i);
                }
                setParseFlags(cef, var, readerd);
                tds = readerd.cefReadData(cmon, cef);
            }

            if (param.sizes.length > 1 || param.sizes[0] > 1) {
                if (tds == null) { // create empty dataset with correct geometry
                    DataSetBuilder result = new DataSetBuilder(2, 0, param.cefFieldPos[1] - param.cefFieldPos[0] + 1, 1);
                    ds = result.getDataSet();
                } else {
                    ds = org.virbo.dataset.DataSetOps.leafTrim(tds, param.cefFieldPos[0], param.cefFieldPos[1] + 1);
                }

                ArrayDataSet dds = ArrayDataSet.copy(ds);
                dds.putProperty(QDataSet.UNITS, u);
                for (int i = 0; i < dds.length(); i++) {
                    for (int j = 0; j < dds.length(i); j++) {
                        if (dds.value(i, j) == ceffill) {
                            dds.putValue(i, j, fill); //TODO: QDataSet has QDataSet.FILL now.
                        }
                    }
                }
                ds = dds;


                setDsName( var, ds );

                if (param.sizes.length > 2) {
                    int[] sizes = new int[param.sizes.length + 1];
                    sizes[0] = ds.length();
                    int ndim = sizes.length;
                    for (int i = 1; i < sizes.length; i++) {
                        sizes[i] = param.sizes[ndim - i - 1];
                    }
                    ds = new ReformDataSet(ds, sizes);
                    rank0 = ds.rank();

                    //if (dsid.rank() == 4) {
                    //    collapseDim = 2;
                    //    dsid = DataSetOps.collapse2(dsid); // for PEACE -- this is why we have to plug in units.
                    //}
                } else {
                    rank0 = ds.rank();
                }

            } else {
                if (tds == null) {
                    ds = DDataSet.createRank1(0);
                } else {
                    ds = org.virbo.dataset.DataSetOps.slice1(tds, param.cefFieldPos[0]);
                }

                rank0 = ds.rank();

                ArrayDataSet dds = ArrayDataSet.copy(ds);
                dds.putProperty(QDataSet.UNITS, u);
                dds.putProperty(QDataSet.FILL_VALUE, ceffill );
                ds = dds;
                setDsName(var, ds);
                
            }
        }

        if (param.entries.get("VALUE_TYPE").equals("ISO_TIME")) {
            ds.putProperty(QDataSet.UNITS, Units.us2000);
            if (DataSetUtil.isMonotonic(ds)) {
                ds.putProperty(QDataSet.MONOTONIC, Boolean.TRUE);
            }
        }

        int[] qube = DataSetUtil.qubeDims(ds);

        boolean doDeps = true;
        if (doDeps) {
            String s;
            for (int i = 0; i < rank0; i++) {
                if ((s = (String) param.entries.get("DEPEND_" + i)) != null) {
                    int newDim = i; // dimension taking rank 4 collapse into account
                    if (i > collapseDim) {
                        newDim = i - 1;
                    } else if (i < collapseDim) {
                        newDim = i;
                    } else {
                        continue;
                    }
                    MutablePropertyDataSet dep0ds = createDataSet(s, tds, cmon);
                    if (dep0ds.rank() > 1) {
                        QDataSet dp01 = (QDataSet) dep0ds.property(QDataSet.DEPEND_0);
                        QDataSet dp02 = (QDataSet) ds.property(QDataSet.DEPEND_0);
                        if (dp01 != null && dp02 != null && dp01.length() == dp02.length()) {
                            dep0ds = org.virbo.dataset.DataSetOps.slice0(dep0ds, 0); // kludge for CLUSTER/PEACE
                            dep0ds.putProperty( QDataSet.CONTEXT_0, null );
                            if (dep0ds.length() > qube[newDim]) { // second kludge for CLUSTER/PEACE
                                dep0ds = org.virbo.dataset.DataSetOps.trim(dep0ds, 0, qube[newDim]);
                            }
                            //if (!org.virbo.dataset.DataSetUtil.isMonotonic(dep0ds)) {
                            //    QDataSet sort = org.virbo.dataset.DataSetOps.sort(dep0ds);
                            //    dep0ds = new SortDataSet(dep0ds, sort);
                            //    dsid = makeMonotonic(dsid, newDim, sort);
                            //    //System.err.println(org.virbo.dataset.DataSetUtil.statsString(dsid));
                            //}

                        }
                    }
                    ds.putProperty("DEPEND_" + newDim, dep0ds);

                }
            }
        }

        if (param.entries.containsKey("COORDINATE_SYSTEM") && ds.length(0)==3 ) { // TODO: C1_CP_PEACE_CP3DXPH_DNFlux has this set SR2, Frame is array>na
            String type = (String) param.entries.get("COORDINATE_SYSTEM");
            int size = 3; // this will be derived from sizes attr.
            if (size == 3) {
                EnumerationUnits units = EnumerationUnits.create(type);
                WritableDataSet dep1 = DDataSet.createRank1(3);
                dep1.putValue(0, units.createDatum("X").doubleValue(units));
                dep1.putValue(1, units.createDatum("Y").doubleValue(units));
                dep1.putValue(2, units.createDatum("Z").doubleValue(units));
                dep1.putProperty(QDataSet.UNITS, units);
                dep1.putProperty(QDataSet.COORDINATE_FRAME, type);
                ds.putProperty(QDataSet.DEPEND_1, dep1);
            }
        }
        
        try {
            Map<String, Object> m = this.getMetadata(new NullProgressMonitor());
            Map props = new CefMetadataModel().properties(m);
            DataSetUtil.putProperties(props, ds);
        } catch (Exception e) {
            e.printStackTrace();
        // do nothing...

        }

        return ds;
    }

    /**
     * Applies the sort index to the idim-th dimension of the qube dataset dsid.
     * Note this does not rearrange the tags or planes!
     * TODO: consider sorting multiple dimensions at once, to reduce excessive
     *    copying
     * @param dsid, rank 1,2, or 3 qube dataset
     * @param idim the dimension being sorted.
     * @param sort rank 1 dataset of new indeces.
     * @return new dataset that is a copy of the first, resorted.
     * @see  org.virbo.dataset.SortDataSet for similar functionality
     */
    private MutablePropertyDataSet makeMonotonic(MutablePropertyDataSet ds, int idim, QDataSet sort) {

        if (idim > 2) {
            throw new IllegalArgumentException("idim must be <=2 ");
        }
        if (ds.rank() > 3) {
            throw new IllegalArgumentException("rank limit");
        }

        int[] qube = DataSetUtil.qubeDims(ds);
        qube[idim] = sort.length();

        DDataSet cds = DDataSet.create(qube);
        org.virbo.dataset.DataSetUtil.putProperties(org.virbo.dataset.DataSetUtil.getProperties(ds), cds);

        if (idim == 0) {
            for (int i = 0; i < qube[0]; i++) {
                if (ds.rank() > 1) {
                    for (int j = 0; j < qube[1]; j++) {
                        if (ds.rank() > 2) {
                            for (int k = 0; k < qube[2]; k++) {
                                double d = ds.value((int) sort.value(i), j, k);
                                cds.putValue(i, j, k, d);
                            }
                        } else {
                            double d = ds.value((int) sort.value(i), j);
                            cds.putValue(i, j, d);
                        }
                    }
                } else {
                    double d = ds.value((int) sort.value(i));
                    cds.putValue(i, d);
                }
            }
        } else if (idim == 1) {
            for (int i = 0; i < qube[0]; i++) {
                for (int j = 0; j < qube[1]; j++) {
                    if (ds.rank() > 2) {
                        for (int k = 0; k < qube[2]; k++) {
                            double d = ds.value(i, (int) sort.value(j), k);
                            cds.putValue(i, j, k, d);
                        }
                    } else {
                        double d = ds.value(i, (int) sort.value(j));
                        cds.putValue(i, j, d);
                    }
                }
            }
        } else if (idim == 2) {
            for (int i = 0; i < qube[0]; i++) {
                for (int j = 0; j < qube[1]; j++) {
                    for (int k = 0; k < qube[2]; k++) {
                        double d = ds.value(i, j, (int) sort.value(k));
                        cds.putValue(i, j, k, d);
                    }
                }
            }
        }

        return cds;
    }

    private void setDsName(String var, MutablePropertyDataSet ds) {
        if (dsid != null && var.endsWith("__" + dsid)) {
            ds.putProperty(QDataSet.NAME, var.substring(0, var.length() - (dsid.length() + 2)));
        } else {
            ds.putProperty(QDataSet.NAME, var);
        }
    }

    private void setParseFlags(Cef cef, String var, CefReaderData readerd) {
        ParamStruct param = cef.parameters.get(var);
        if (param.cefFieldPos[0] != -1) {
            for (int i = param.cefFieldPos[0]; i < param.cefFieldPos[1] + 1; i++) {
                readerd.doParse(i);
            }
        }
        String s;
        for (int i = 0; i < 4; i++) {
            if ((s = (String) param.entries.get("DEPEND_" + i)) != null) {
                setParseFlags(cef, s, readerd);
            }
        }

    }

    @Override
    public MetadataModel getMetadataModel() {
        return new CefMetadataModel();
    }
}
