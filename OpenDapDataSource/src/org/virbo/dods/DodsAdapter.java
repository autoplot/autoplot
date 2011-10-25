/*
 * DodsAdapter.java
 *
 * Created on January 29, 2007, 5:59 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.dods;

import dods.dap.BaseType;
import dods.dap.DArray;
import dods.dap.DArrayDimension;
import dods.dap.DConnect;
import dods.dap.DDS;
import dods.dap.DDSException;
import dods.dap.DFloat32;
import dods.dap.DFloat64;
import dods.dap.DGrid;
import dods.dap.DODSException;
import dods.dap.DSequence;
import dods.dap.DStructure;
import dods.dap.Float32PrimitiveVector;
import dods.dap.Float64PrimitiveVector;
import dods.dap.NoSuchVariableException;
import dods.dap.PrimitiveVector;
import dods.dap.StatusUI;
import dods.dap.parser.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import org.das2.util.monitor.CancelledOperationException;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.dataset.WritableDataSet;
import org.virbo.dsops.Ops;
import org.virbo.metatree.MetadataUtil;

/**
 *
 * @author jbf
 */
public class DodsAdapter {

    /**
     * http://www.cdc.noaa.gov/cgi-bin/nph-nc/Datasets/kaplan_sst/sst.mean.anom.nc
     */
    private URL source;
    /**
     * sst
     */
    private String variable;
    /**
     *?sst[0:100:1811][0:10:35][0:10:71]
     */
    private String constraint;
    private DDS dds;
    private HashMap properties;

    /** Creates a new instance of DodsAdapter */
    public DodsAdapter(URL source, String variable) {
        this.source = source;
        //this.variable= doEscapes(variable); // TODO: why was this introduced?
        this.variable = variable;
        properties = new HashMap();
    }

    void setVariable(String variable) {
        this.variable= variable;
    }

    private String doEscapes(String s) {
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isJavaIdentifierPart(ch) || ch == '%') {
                result.append(ch);
            } else {
                String s2 = Integer.toHexString(ch);
                result.append("%" + s2.substring(s2.length() - 2));
            }
        }
        return result.toString();
    }

    public void setConstraint(String c) {
        if (!c.startsWith("?")) {
            throw new IllegalArgumentException("constraint must start with question mark(?)");
        }
        this.constraint = c;
    }

    public String getConstraint() {
        return this.constraint;
    }
    
    private long getSizeForType( DArray v, boolean streaming ) {
        PrimitiveVector pv = v.getPrimitiveVector();
        if (pv instanceof Float32PrimitiveVector) {
            return 4 * ( streaming ? v.getFirstDimension().getSize() : 1 );
        } else if (pv instanceof Float64PrimitiveVector) {
            return 8 * ( streaming ? v.getFirstDimension().getSize() : 1 );
        } else {
            return 1;
        }
    }

    private long getSizeForType(BaseType v, boolean streaming ) {
        if (v instanceof DFloat64) {
            return 8;
        } else if (v instanceof DFloat32 ) {
            return 4;
        } else if (v instanceof DArray) {
            return getSizeForType((DArray) v, streaming );
        } else {
            throw new IllegalArgumentException("not supported: "+v);
        }
    }

    private long calcSize( Map<String,Object> attr ) throws MalformedURLException, IOException, ParseException {
        try {
            DDS dds = new DDS();
            dds.parse(new URL(this.getSource().toString() + ".dds" + constraint).openStream());

            // calculate size
            Enumeration variables = dds.getVariables();
            long size = 0;
            while (variables.hasMoreElements()) {
                Object o = variables.nextElement();
                if (o instanceof DSequence) {
                    Enumeration enume1 = ((DSequence) o).getVariables();
                    int j = 0;
                    while (enume1.hasMoreElements()) {
                        Object ele = enume1.nextElement();
                        if (ele instanceof DStructure) {
                            DStructure ds = (DStructure) ele;

                            Enumeration enume2 = ds.getVariables();
                            int jj = 0;
                            while (enume2.hasMoreElements()) {
                                Object k = enume2.nextElement();
                                j += getSizeForType((BaseType) k,true);
                            }
                        } else if ( ele instanceof DSequence ) {
                            j+= 0;
                        } else if (ele instanceof BaseType) {
                            j += getSizeForType((BaseType) ele,true);

                        } else {
                            throw new IllegalArgumentException("huh");
                        }
                    }
                    String srecCount= (String) attr.get("recCount" );
                    if ( srecCount!=null ) {
                        size= j * Long.parseLong(srecCount);
                    } else {
                        size = -1; // we don't know number of records.
                    }
                } else {
                    DArray v = (DArray) o;
                    Enumeration dimensions = v.getDimensions();
                    long s1 = getSizeForType(v, false);
                    s1 *= 2;   // not sure why
                    while (dimensions.hasMoreElements()) {
                        DArrayDimension d = (DArrayDimension) dimensions.nextElement();
                        s1 *= d.getSize();
                    }
                    size += s1;
                }
            }
            return size;
        } catch (DDSException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Load the dataset.  
     * @param mon
     * @param attr look for hints in attr about the length of the load.  Virbo/TSDS put a recCount for sequences.
     * @throws java.io.FileNotFoundException
     * @throws java.net.MalformedURLException
     * @throws java.io.IOException
     * @throws dods.dap.parser.ParseException
     * @throws dods.dap.DDSException
     * @throws dods.dap.DODSException
     * @throws org.das2.CancelledOperationException
     */
    public void loadDataset(final ProgressMonitor mon, Map<String,Object> attr ) throws FileNotFoundException, MalformedURLException,
            IOException, ParseException, DDSException, DODSException,
            CancelledOperationException {

        if ( constraint==null ) {
            constraint="";
        }
        
        long size = calcSize(  attr );
        mon.setTaskSize(size);

        DConnect url = new DConnect(source.toString(), true);
        StatusUI sui = new StatusUI() {

            long byteCount = 0;

            public void incrementByteCount(int bytes) {
                byteCount += bytes;
                mon.setTaskProgress(byteCount);
            }

            public boolean userCancelled() {
                return mon.isCancelled();
            }

            public void finished() {
                mon.finished();
            }
        };

        mon.started();
        try {
            dds = url.getData(constraint, sui);
        } catch (DODSException ex) {
            if (mon.isCancelled()) {
                throw new CancelledOperationException("Dods load cancelled");
            } else {
                throw ex;
            }
        }

    }
    int sliceIndex = 0;

    public void setSliceIndex(int index) {
        this.sliceIndex = index;
    }

    private enum Type { spectrogram, vectors, scalars };

    /**
     * This is the code that converts the OpenDAP structures and data types into QDataSet
     * @param attributes
     * @return
     */
    public QDataSet getDataSet(Map<String, Object> attributes) {
        DodsVarDataSet zds;

        if (attributes == null) attributes = new HashMap<String, Object>();
        BaseType btvar;
        try {
            btvar = dds.getVariable(variable);
            String type = btvar.getTypeName();
            if (type.equals("Grid")) {
                DGrid zgrid = (DGrid) btvar;
                DArray z = (DArray) zgrid.getVar(0);

                zds = DodsVarDataSet.newDataSet(z, properties);
                if (zds.property(QDataSet.UNITS) == null) {
                    zds.putProperty(QDataSet.UNITS, units);
                }
                for (int idim = 0; idim < z.numDimensions(); idim++) {
                    DArray t = (DArray) zgrid.getVar(idim + 1);
                    HashMap tprops = new HashMap();
                    tprops.put(QDataSet.UNITS, dimUnits[idim]);
                    if (dimProperties[idim] != null) {
                        tprops.putAll(dimProperties[idim]);
                    }
                    DodsVarDataSet tds = DodsVarDataSet.newDataSet(t, tprops);
                    zds.putProperty("DEPEND_" + idim, tds);
                }

            } else if (type.equals("Array")) {
                DArray z = (DArray) btvar;

                zds = DodsVarDataSet.newDataSet(z, properties);
                if (zds.property(QDataSet.UNITS) == null) {
                    zds.putProperty(QDataSet.UNITS, units);
                }
                for (int idim = 0; idim < z.numDimensions(); idim++) {
                    if (dependName[idim] != null) {
                        DArray t = (DArray) dds.getVariable(dependName[idim]);
                        HashMap tprops = new HashMap();
                        tprops.put(QDataSet.UNITS, dimUnits[idim]);
                        if (dimProperties[idim] != null) tprops.putAll(dimProperties[idim]);
                        DodsVarDataSet tds = DodsVarDataSet.newDataSet(t, tprops);
                        if (DataSetUtil.isMonotonic(tds)) {
                            tds.putProperty(QDataSet.MONOTONIC, Boolean.TRUE);
                        }
                        zds.putProperty("DEPEND_" + idim, tds);
                    }
                }
            } else if (type.equals("Sequence")) {
                DSequence dseq = (DSequence) btvar;
                int cols = dseq.elementCount(true);
                int rows = dseq.getRowCount();

                //DDataSet result = DDataSet.createRank2(rows, cols);
                WritableDataSet[] dss = new WritableDataSet[cols];
                String[] labels = new String[cols];
                
                Type t= Type.scalars;
                for (int i = 0; i < rows; i++) {
                    Vector v = dseq.getRow(i);
                    int j = 0;
                    for (Object ele : v) {
                        if (ele instanceof DStructure) {
                            DStructure ds = (DStructure) ele;
                            Enumeration enume = ds.getVariables();
                            while (enume.hasMoreElements()) {
                                Object k = enume.nextElement();
                                if (i == 0) {
                                    if (((BaseType) k) instanceof DArray) {
                                        dss[j] = DDataSet.createRank2(rows, ((DArray) k).getLength());
                                        t= Type.spectrogram;
                                    } else {
                                        dss[j] = DDataSet.createRank1(rows);
                                    }
                                    labels[j] = ((BaseType) k).getName();
                                    dss[j].putProperty( QDataSet.NAME, labels[j] );
                                }
                                putValue(dss[j], i, (BaseType) k);
                                j++;
                            }
                        } else if (ele instanceof BaseType) {
                            if (i == 0) {
                                if (((BaseType) ele) instanceof DArray) {
                                    dss[j] = DDataSet.createRank2(rows, ((DArray) ele).getLength());
                                    t= Type.spectrogram;
                                } else {
                                    dss[j] = DDataSet.createRank1(rows);
                                }
                                labels[j] = ((BaseType) ele).getName();
                                dss[j].putProperty( QDataSet.NAME, labels[j] );
                            }
                            putValue( dss[j], i, (BaseType) ele);
                            j++;
                        } else {
                            throw new IllegalArgumentException("huh");
                        }
                    }
                }
                
                if ( cols>2 && t==Type.scalars ) {
                    t= Type.vectors;
                }
                
                MutablePropertyDataSet zresult=null;
                
                if ( t==Type.spectrogram || t==Type.scalars ) {
                    dss[cols-1].putProperty( QDataSet.DEPEND_0, dss[0] );
                    zresult= dss[cols-1];
                    if ( t==Type.spectrogram ) dss[cols-1].putProperty( QDataSet.DEPEND_1, DataSetOps.slice0(dss[1], 0) );
                } else if ( t==Type.vectors ) {
                    DDataSet rresult= DDataSet.createRank2(rows, cols-1 );
                    for ( int j=0; j<cols-1; j++ ) {
                        QDataSet ds= dss[j+1];
                        for ( int i=0; i<rows; i++ ) {
                            rresult.putValue( i, j, ds.value(i) );
                        }
                    }
                    rresult.putProperty(QDataSet.DEPEND_1, DataSetOps.trim( Ops.labels(labels), 1, cols-1 ) );
                    rresult.putProperty(QDataSet.DEPEND_0, dss[0] );
                    zresult= rresult;
                }

                if ( zresult==null ) {
                    throw new IllegalArgumentException( "Unsupported type: "+ t );
                }
                MutablePropertyDataSet dep0 = (MutablePropertyDataSet) zresult.property( QDataSet.DEPEND_0 );
                
                String sunits = (String) MetadataUtil.getNode(attributes, new String[]{labels[0], "units"});
                if (sunits != null) {
                    if (sunits.contains("since")) {
                        Units u;
                        try {
                            u = SemanticOps.lookupTimeUnits(sunits);
                            dep0.putProperty(QDataSet.UNITS, u);
                        } catch (java.text.ParseException ex) {
                            Logger.getLogger(DodsAdapter.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }

                return zresult;

            } else {

                throw new IllegalStateException("not supported dds type:" + type);
            }
        } catch (NoSuchVariableException ex) {
            throw new RuntimeException(ex);
        }

        QDataSet ds = zds;
        if (zds.rank() == 3) {
            DDataSet reduce = DDataSet.createRank2(zds.length(), zds.length(0));
            for (int i = 0; i < zds.length(); i++) {
                for (int j = 0; j < zds.length(0); j++) {
                    reduce.putValue(i, j, zds.value(i, j, 0));
                }
            }
            ds = reduce;
        } else if (zds.rank() == 4) {
            DDataSet reduce = DDataSet.createRank2(zds.length(), zds.length(0));
            for (int i = 0; i < zds.length(); i++) {
                for (int j = 0; j < zds.length(0); j++) {
                    reduce.putValue(i, j, zds.value(i, j, 0, 0));
                }
            }
            ds = reduce;
        }


        return ds;
    }

    /**
     * Holds value of property depend0Name.
     */
    private String depend0Name;

    /**
     * Getter for property depend0Name.
     * @return Value of property depend0Name.
     */
    public String getDepend0Name() {
        return this.depend0Name;
    }

    /**
     * Setter for property depend0Name.
     * @param depend0Name New value of property depend0Name.
     */
    public void setDepend0Name(String depend0Name) {
        this.depend0Name = depend0Name;
    }
    /**
     * Holds value of property depend1Name.
     */
    private String depend1Name;

    /**
     * Getter for property depend1Name.
     * @return Value of property depend1Name.
     */
    public String getDepend1Name() {
        return this.depend1Name;
    }

    /**
     * Setter for property depend1Name.
     * @param depend1Name New value of property depend1Name.
     */
    public void setDepend1Name(String depend1Name) {
        this.depend1Name = depend1Name;
    }
    /**
     * Holds value of property addOffset.
     */
    private double addOffset = 0.0;

    /**
     * Getter for property addOffset.
     * @return Value of property addOffset.
     */
    public double getAddOffset() {
        return this.addOffset;
    }

    /**
     * Setter for property addOffset.
     * @param addOffset New value of property addOffset.
     */
    public void setAddOffset(double addOffset) {
        this.addOffset = addOffset;
        properties.put("add_offset", new Double(addOffset));
    }
    /**
     * Holds value of property scaleFactor.
     */
    private double scaleFactor = 1.0;

    /**
     * Getter for property scaleFactor.
     * @return Value of property scaleFactor.
     */
    public double getScaleFactor() {
        return this.scaleFactor;
    }

    /**
     * Setter for property scaleFactor.
     * @param scaleFactor New value of property scaleFactor.
     */
    public void setScaleFactor(double scaleFactor) {
        this.scaleFactor = scaleFactor;
        properties.put("scale_factor", new Double(scaleFactor));
    }

    public void setValidRange(double min, double max) {
        properties.put("valid_range", "" + min + "," + max);
    }
    /**
     * Holds value of property dimUnits.
     */
    private Units[] dimUnits = new Units[8];

    /**
     * Indexed getter for property dimUnits, which specifies the units of a dimension tag.
     * @param index Index of the property.
     * @return Value of the property at <CODE>index</CODE>.
     */
    public Units getDimUnits(int index) {
        return this.dimUnits[index];
    }

    /**
     * Specifies the units of a dimension tag.
     * @param index Index of the property.
     * @param dimUnits New value of the property at <CODE>index</CODE>.
     */
    public void setDimUnits(int index, Units dimUnits) {
        this.dimUnits[index] = dimUnits;
    }

    public void putAllProperties(Map p) {
        properties.putAll(p);
    }
    private HashMap[] dimProperties = new HashMap[8];

    public void setDimProperties(int dim, Map p) {
        dimProperties[dim] = new HashMap(p);
    }

    public HashMap getDimProperties(int i) {
        return dimProperties[i];
    }
    /**
     * das2 Unit object for the dataset.
     */
    private Units units;

    /**
     * Getter for property units.
     * @return Value of property units.
     */
    public Units getUnits() {
        return this.units;
    }

    /**
     * Setter for property units.
     * @param units New value of property units.
     */
    public void setUnits(Units units) {
        this.units = units;
    }
    /**
     * Holds value of property dependName.
     */
    private String[] dependName = new String[8];

    /**
     * Indexed getter for property dependName.
     * @param index Index of the property.
     * @return Value of the property at <CODE>index</CODE>.
     */
    public String getDependName(int index) {
        return this.dependName[index];
    }

    /**
     * Indexed setter for property dependName.
     * @param index Index of the property.
     * @param dependName New value of the property at <CODE>index</CODE>.
     */
    public void setDependName(int index, String dependName) {
        this.dependName[index] = dependName;
    }

    public URL getSource() {
        return this.source;
    }

    public String getVariable() {
        return this.variable;
    }

    private void putValue(WritableDataSet result, int i, BaseType value) {
        if (value instanceof DFloat64) {
            result.putValue(i, ((DFloat64) value).getValue());
        } else if ( value instanceof DFloat32 ) {
            result.putValue(i, ((DFloat32) value).getValue());
        } else if (value instanceof DArray) {
            ArrayUtil.putValues(result, i, ((DArray) value).getPrimitiveVector().getInternalStorage());
        } else {
            throw new IllegalArgumentException("not supported: " + value);
        }
    }

    private void putValue(WritableDataSet result, int i, int j, BaseType value) {
        if (value instanceof DFloat64) {
            result.putValue(i, j, ((DFloat64) value).getValue());
        } else if ( value instanceof DFloat32 ) {
            result.putValue(i, j, ((DFloat32) value).getValue());
        } else if (value instanceof DArray) {
            ArrayUtil.putValues(result, i, j, ((DArray) value).getPrimitiveVector().getInternalStorage());
        } else {
            throw new IllegalArgumentException("not supported: " + value);
        }
    }
}
