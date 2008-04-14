/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.cefdatasource;

import edu.uiowa.physics.pw.das.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import edu.uiowa.physics.pw.das.util.DasProgressMonitorReadableByteChannel;
import org.das2.util.monitor.NullProgressMonitor;
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
import org.virbo.cefdatasource.CefReaderHeader.ParamStruct;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
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

    public synchronized QDataSet getDataSet(ProgressMonitor mon) throws Exception {

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
    public synchronized TreeModel getMetaData(ProgressMonitor mon) throws Exception {
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

        int collapseDim = 999; // >999 indicates a dimension was collapsed to reduce rank.
        int rank0; // rank before collapse

        Units u = Units.dimensionless;
        double fill = u.getFillDouble();
        String sceffill = (String) param.entries.get("FILLVAL");

        double ceffill;
        if (!param.entries.get("VALUE_TYPE").equals("ISO_TIME")) {
            ceffill = (sceffill != null) ? Double.parseDouble(sceffill) : fill;
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
            ds.putProperty(QDataSet.NAME, var);
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

                DDataSet dds = DDataSet.copy(ds);
                dds.putProperty(QDataSet.UNITS, u);
                for (int i = 0; i < dds.length(); i++) {
                    for (int j = 0; j < dds.length(i); j++) {
                        if (dds.value(i, j) == ceffill) {
                            dds.putValue(i, j, fill);
                        }
                    }
                }
                ds = dds;


                ds.putProperty(QDataSet.NAME, var);

                if (param.sizes.length > 2) {
                    int[] sizes = new int[param.sizes.length + 1];
                    sizes[0] = ds.length();
                    int ndim = sizes.length;
                    for (int i = 1; i < sizes.length; i++) {
                        sizes[i] = param.sizes[ndim - i - 1];
                    }
                    ds = new ReformDataSet(ds, sizes);
                    rank0 = ds.rank();

                    if (ds.rank() == 4) {
                        collapseDim = 2;
                        ds = DataSetOps.collapse2(ds); // for PEACE -- this is why we have to plug in units.
                    }
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

                DDataSet dds = DDataSet.copy(ds);
                dds.putProperty(QDataSet.UNITS, u);
                for (int i = 0; i < dds.length(); i++) {
                    for (int j = 0; j < dds.length(i); j++) {
                        if (dds.value(i, j) == ceffill) {
                            dds.putValue(i, j, fill);
                        }
                    }
                }
                ds = dds;

                ds.putProperty(QDataSet.NAME, var);
            }
        }

        if (param.entries.get("VALUE_TYPE").equals("ISO_TIME")) {
            ds.putProperty(QDataSet.UNITS, Units.us2000);
            if ( DataSetUtil.isMonotonic(ds) ) ds.putProperty(QDataSet.MONOTONIC, Boolean.TRUE );
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
                            if (dep0ds.length() > qube[newDim]) { // second kludge for CLUSTER/PEACE
                                dep0ds = org.virbo.dataset.DataSetOps.trim(dep0ds, 0, qube[newDim]);
                            }
                            if (!org.virbo.dataset.DataSetUtil.isMonotonic(dep0ds)) {
                                QDataSet sort = org.virbo.dataset.DataSetOps.sort(dep0ds);
                                dep0ds = new SortDataSet(dep0ds, sort);
                                ds = makeMonotonic(ds, newDim, sort);
                                System.err.println(org.virbo.dataset.DataSetUtil.statsString(ds));
                            }

                        }
                    }
                    ds.putProperty("DEPEND_" + newDim, dep0ds);

                }
            }
        }

        try {
            TreeModel m = this.getMetaData(new NullProgressMonitor());
            Map props = new CefMetadataModel().properties(m);
            DataSetUtil.putProperties(props, ds);
        } catch (Exception e) {
            e.printStackTrace();
        // do nothing...

        }

        return ds;
    }

    /**
     * Applies the sort index to the idim-th dimension of the qube dataset ds.
     * Note this does not rearrange the tags or planes!
     * TODO: consider sorting multiple dimensions at once, to reduce excessive
     *    copying
     * @param ds, rank 1,2, or 3 qube dataset
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
}
