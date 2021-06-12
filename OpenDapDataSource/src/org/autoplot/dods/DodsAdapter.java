/*
 * DodsAdapter.java
 *
 * Created on January 29, 2007, 5:59 AM
 *
 */
package org.autoplot.dods;

import opendap.dap.BaseType;
import opendap.dap.DArray;
import opendap.dap.DArrayDimension;
import opendap.dap.DConnect;
import opendap.dap.DDS;
import opendap.dap.DFloat32;
import opendap.dap.DFloat64;
import opendap.dap.DGrid;
import opendap.dap.DDSException;
import opendap.dap.DSequence;
import opendap.dap.DStructure;
import opendap.dap.Float32PrimitiveVector;
import opendap.dap.Float64PrimitiveVector;
import opendap.dap.Int16PrimitiveVector;
import opendap.dap.Int32PrimitiveVector;
import opendap.dap.NoSuchVariableException;
import opendap.dap.PrimitiveVector;
import opendap.dap.StatusUI;
import opendap.dap.parser.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import opendap.dap.DAP2Exception;
import org.das2.util.monitor.CancelledOperationException;
import org.das2.qds.DDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.WritableDataSet;
import org.das2.qds.ops.Ops;
import org.autoplot.metatree.MetadataUtil;

/**
 *
 * @author jbf
 */
public class DodsAdapter {
    
    private final static Logger logger= Logger.getLogger("apdss.opendap");

    /**
     * http://www.cdc.noaa.gov/cgi-bin/nph-nc/Datasets/kaplan_sst/sst.mean.anom.nc
     */
    private final URL source;
    
    /**
     * sst
     */
    private String variable;
    
    /**
     *?sst[0:100:1811][0:10:35][0:10:71]
     */
    private String constraint;
    
    private DDS dds;
    private final HashMap<String,Object> properties;

    /** Creates a new instance of DodsAdapter
     * @param source the base URL, like http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMAEROe.003/2005/OMI-Aura_L3-OMAEROe_2005m0101_v003-2011m1109t081947.he5
     * @param variable the variable to read, like TerrainReflectivity
     */
    public DodsAdapter(URL source, String variable) {
        logger.entering("org.autoplot.dods.DodsAdapter", "DodsAdapter" );
        this.source = source;
        this.variable = variable;
        properties = new HashMap<>();
        logger.exiting("org.autoplot.dods.DodsAdapter", "DodsAdapter" );
    }

    void setVariable(String variable) {
        this.variable= variable;
    }

    /**
     * get the variable, such as "sst"
     * @return the variable
     */
    public String getVariable() {
        return this.variable;
    }
    
    public void setConstraint(String c) {
        if (!c.startsWith("?")) {
            throw new IllegalArgumentException("constraint must start with question mark(?)");
        }
        this.constraint = c;
    }
    
    /**
     * get the constraint, such as "?sst[0:100:1811][0:10:35][0:10:71]"
     * @return the constraint
     */
    public String getConstraint() {
        return this.constraint;
    }
    
    private long getSizeForType( DArray v, boolean streaming ) {
        PrimitiveVector pv = v.getPrimitiveVector();
        Enumeration e= v.getDimensions();
        int n=1;
        while ( e.hasMoreElements() ) {
            DArrayDimension a= (DArrayDimension)e.nextElement();
            n= n * a.getSize();
        }
        if (pv instanceof Float32PrimitiveVector) {
            return 4 * n * ( streaming ? v.getFirstDimension().getSize() : 1 );
        } else if (pv instanceof Float64PrimitiveVector) {
            return 8 * n *( streaming ? v.getFirstDimension().getSize() : 1 );
        } else if (pv instanceof Int32PrimitiveVector ) {
            return 4 * n *( streaming ? v.getFirstDimension().getSize() : 1 );
        } else if (pv instanceof Int16PrimitiveVector ) {
            return 2 * n *( streaming ? v.getFirstDimension().getSize() : 1 );
        } else {
            return n;
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
            logger.entering("org.autoplot.dods.DodsAdapter", "calcSize" );
            
            DDS ldds = new DDS();
            
            URL url= new URL(this.getSource().toString() + ".dds" + constraint);
            
            logger.log(Level.FINE, "calcSize opening {0}", url);
            try ( InputStream in = url.openStream() ) {
                ldds.parse(in);
            }

            // calculate size
            Enumeration variables = ldds.getVariables();
            long size = 0;
            while (variables.hasMoreElements()) {
                Object o = variables.nextElement();
                String n;
                if (o instanceof DSequence) {
                    Enumeration enume1 = ((DSequence) o).getVariables();
                    n= ((DSequence)o).getName();
                    int j = 0;
                    while (enume1.hasMoreElements()) {
                        Object ele = enume1.nextElement();
                        if (ele instanceof DStructure) {
                            DStructure ds = (DStructure) ele;

                            Enumeration enume2 = ds.getVariables();
                            while (enume2.hasMoreElements()) {
                                Object k = enume2.nextElement();
                                long s= getSizeForType((BaseType) k,true);
                                j += s;
                                logger.log(Level.FINE, "   calcSize {0}: {1}", new Object[]{ ((BaseType)k).getName(), s });                    
                            }
                        } else if ( ele instanceof DSequence ) {
                            j+= 0;
                        } else if (ele instanceof BaseType) {
                            long s= getSizeForType((BaseType) ele,true);
                            j += s;
                            logger.log(Level.FINE, "   calcSize {0}: {1}", new Object[]{ ((BaseType)ele).getName(), s });                    
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
                } else if ( o instanceof DGrid ) {
                    DGrid dg= (DGrid) o;
                    n= dg.getName();
                    Enumeration enume1= dg.getVariables();
                    int j= 0;
                    while ( enume1.hasMoreElements() ) {
                        Object ele= enume1.nextElement();
                        if (ele instanceof DStructure) {
                            DStructure ds = (DStructure) ele;

                            Enumeration enume2 = ds.getVariables();
                            while (enume2.hasMoreElements()) {
                                Object k = enume2.nextElement();
                                long s= getSizeForType((BaseType) k,false);
                                j += s;
                                logger.log(Level.FINE, "   calcSize {0}: {1}", new Object[]{ ((BaseType)k).getName(), s });
                            }
                        } else if ( ele instanceof DSequence ) {
                            j+= 0;
                        } else if ( ele instanceof BaseType ) {
                            j += getSizeForType((BaseType) ele,false);
                        }
                    }
                    size= j;
                    
                } else {
                    DArray v = (DArray) o;
                    n= ((DArray)o).getName();
                    Enumeration dimensions = v.getDimensions();
                    long s1 = getSizeForType(v, false);
                    s1 *= 2;   // not sure why
                    while (dimensions.hasMoreElements()) {
                        DArrayDimension d = (DArrayDimension) dimensions.nextElement();
                        s1 *= d.getSize();
                    }
                    size += s1;
                }
                logger.log(Level.FINE, "calcSize {0}: {1}", new Object[]{n, size});
            }
            
            logger.exiting("org.autoplot.dods.DodsAdapter", "calcSize" );
            
            return size;
        } catch (DDSException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * adapt the das2 progress monitor to what openDap is expecting.
     * @param mon das monitor.
     * @return Dods monitor.
     */
    private StatusUI adaptStatusUI( final ProgressMonitor mon ) {
        return new StatusUI() {
            long byteCount = 0;
            @Override
            public void incrementByteCount(int bytes) {
                byteCount += bytes;
                mon.setTaskProgress(byteCount);
                if ( mon.getTaskSize()==-1 ) {
                    mon.setProgressMessage( String.format("%d KBytes loaded",byteCount/1024 ) );
                }
            }
            @Override
            public boolean userCancelled() {
                return mon.isCancelled();
            }
            @Override
            public void finished() {
                mon.finished();
            }
        };
    }
    
    /**
     * Load the dataset.  
     * @param mon progress monitor
     * @param attr look for hints in attr about the length of the load.  Virbo/TSDS put a recCount for sequences.
     * @throws java.io.FileNotFoundException
     * @throws java.net.MalformedURLException
     * @throws java.io.IOException
     * @throws opendap.dap.parser.ParseException
     * @throws opendap.dap.DDSException
     * @throws org.das2.util.monitor.CancelledOperationException
     */
    public void loadDataset(final ProgressMonitor mon, Map<String,Object> attr ) throws FileNotFoundException, MalformedURLException,
            IOException, ParseException, DDSException, DDSException,
            CancelledOperationException, DAP2Exception {

        logger.entering("org.autoplot.dods.DodsAdapter", "loadDataset" );
        
        if ( constraint==null ) {
            constraint="";
        }
        
        long size = calcSize(  attr );
        mon.setTaskSize(size);
        if ( mon.isCancelled() ) throw new CancelledOperationException("OpenDap load cancelled");
        
        logger.log(Level.FINE, "constructing dconnect on {0}", source.toString() );
        DConnect dconnect = new DConnect(source.toString(), true);
        StatusUI statusUI = adaptStatusUI(mon);
        
        mon.started();
        
        try {
            logger.log(Level.FINE, "calling dconnect.getData constraint={0}", constraint);
            if ( mon.isCancelled() ) throw new CancelledOperationException("OpenDap load cancelled");
            dds = dconnect.getData(constraint, statusUI);
            logger.log(Level.FINE, "called dconnect.getData -> {0}", dds );
            if ( dds==null ) {
                System.err.println( "Webstart/Opendap interaction results in dconnect.getData -> null");
                System.err.println( "opendap.Version.getVersionString()="+opendap.Version.getVersionString() );
                throw new IllegalArgumentException("unable to load data, for unknown reason.");
            }
            
        } catch (DDSException ex) {
            if (mon.isCancelled()) {
                logger.log( Level.FINE, ex.getMessage(), ex );
                throw new CancelledOperationException("Dods load cancelled");
            } else {
                logger.log( Level.SEVERE, ex.getMessage(), ex );
                throw ex;
            }

        } finally {
            if ( !mon.isFinished() ) mon.finished();
            logger.exiting("org.autoplot.dods.DodsAdapter", "loadDataset" );
            
        }
       

    }

    private enum Type { spectrogram, vectors, scalars };

    /**
     * This is the code that converts the OpenDAP structures and data types into QDataSet
     * @param attributes
     * @return
     */
    public QDataSet getDataSet(Map<String, Object> attributes) {
        MutablePropertyDataSet zds;

        logger.entering("org.autoplot.dods.DodsAdapter", "getDataSet" );
        if (attributes == null) attributes = new HashMap<>();
        BaseType btvar;
        try {
            btvar = dds.getVariable(variable);
            String type = btvar.getTypeName();
            if (type.equals("Grid")) {
                DGrid zgrid = (DGrid) btvar;
                DArray z = (DArray) zgrid.getVar(0);
                if ( properties.isEmpty() ) {
                    zds = DodsVarDataSet.newDataSet(z, attributes);
                } else {
                    zds = DodsVarDataSet.newDataSet(z, properties);
                }
                
                if (zds.property(QDataSet.UNITS) == null) {
                    zds.putProperty(QDataSet.UNITS, units);
                }
                for (int idim = 0; idim < z.numDimensions(); idim++) {
                    DArray t = (DArray) zgrid.getVar(idim + 1);
                    HashMap tprops = new HashMap();
                    tprops.put(QDataSet.UNITS, dimUnits[idim]);
                    if (dimProperties[idim] != null) {
                        String[] ss= DataSetUtil.dimensionProperties();
                        for ( String s: ss ) {
                            if ( dimProperties[idim].containsKey(s) ) tprops.put( s, dimProperties[idim].get(s) );
                        }
                    }
                    DodsVarDataSet tds = DodsVarDataSet.newDataSet(t, tprops);
                    zds.putProperty("DEPEND_" + idim, tds);
                }

            } else if (type.equals("Array")) {
                DArray z = (DArray) btvar;
                if ( properties.isEmpty() ) {
                    zds = DodsVarDataSet.newDataSet(z, attributes);
                } else {
                    zds = DodsVarDataSet.newDataSet(z, properties);
                }
                if (zds.property(QDataSet.UNITS) == null) {
                    zds.putProperty(QDataSet.UNITS, units);
                }
                if (zds.property(QDataSet.UNITS) == null) {
                    String s= String.valueOf( attributes.get("units") );
                    zds= checkTimeUnits( s, zds );
                }
                for (int idim = 0; idim < z.numDimensions(); idim++) {
                    if (dependName[idim] != null) {
                        DArray t = (DArray) dds.getVariable(dependName[idim]);
                        HashMap tprops = new HashMap();
                        tprops.put(QDataSet.UNITS, dimUnits[idim]);
                        String[] ss= DataSetUtil.dimensionProperties();
                        for ( String s: ss ) {
                            if ( dimProperties[idim]!=null && dimProperties[idim].containsKey(s) ) tprops.put( s, dimProperties[idim].get(s) );
                        }
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
                            throw new IllegalArgumentException("only BaseType and DStructure supported");
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
                    rresult.putProperty(QDataSet.DEPEND_1, DataSetOps.trim( Ops.labelsDataset(labels), 1, cols-1 ) );
                    rresult.putProperty(QDataSet.DEPEND_0, dss[0] );
                    zresult= rresult;
                }

                if ( zresult==null ) {
                    throw new IllegalArgumentException( "Unsupported type: "+ t );
                }
                MutablePropertyDataSet dep0 = (MutablePropertyDataSet) zresult.property( QDataSet.DEPEND_0 );
                
                String sunits = (String) MetadataUtil.getNode(attributes, new String[]{labels[0], "units"});
                checkTimeUnits( sunits, dep0);
                
                return zresult;

            } else {
                throw new IllegalStateException("not supported dds type:" + type);
                
            }
        } catch (NoSuchVariableException ex) {
            throw new RuntimeException(ex);
            
        } finally {
            logger.exiting("org.autoplot.dods.DodsAdapter", "getDataSet" );
        }

        QDataSet ds = zds;

        return ds;
    }

    /**
     * check for time units and attach them to the data.
     * @param sunits labels for the units, or null.
     * @param dep0 the data
     * @return dep0, which may have been rewritten to support "days since 1970-01-01T00:00:00Z"
     */
    protected static MutablePropertyDataSet checkTimeUnits(String sunits, MutablePropertyDataSet dep0) {
        if (sunits != null) {
            if (sunits.contains("since")) {
                Units u;
                try {
                    u = Units.lookupTimeUnits(sunits);
                    dep0.putProperty(QDataSet.UNITS, u);
                } catch (java.text.ParseException ex) {
                    if ( sunits.equals("days since 1-1-1 00:00:0.0") ) {
                        dep0= Ops.maybeCopy( Ops.subtract( dep0, DataSetUtil.asDataSet(719529) ) ); // from https://www.epochconverter.com/seconds-days-since-y0
                        try {
                            dep0.putProperty( QDataSet.UNITS, Units.lookupTimeUnits( "days since 1970-01-01T00:00:00Z" ) );
                        } catch (java.text.ParseException ex1) {
                            logger.log(Level.SEVERE, null, ex1);
                        }
                    } else {
                        logger.log(Level.SEVERE, null, ex);
                    }                    
                }
            }
        }
        return dep0;
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
        properties.put("add_offset", addOffset );
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
        properties.put("scale_factor", scaleFactor );
    }

    public void setValidRange(double min, double max) {
        properties.put("valid_range", "" + min + "," + max);
    }
    /**
     * Holds value of property dimUnits.
     */
    private final Units[] dimUnits = new Units[8];

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
    private final HashMap[] dimProperties = new HashMap[8];

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
    private final String[] dependName = new String[8];

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

//    private void putValue(WritableDataSet result, int i, int j, BaseType value) {
//        if (value instanceof DFloat64) {
//            result.putValue(i, j, ((DFloat64) value).getValue());
//        } else if ( value instanceof DFloat32 ) {
//            result.putValue(i, j, ((DFloat32) value).getValue());
//        } else if (value instanceof DArray) {
//            ArrayUtil.putValues(result, i, j, ((DArray) value).getPrimitiveVector().getInternalStorage());
//        } else {
//            throw new IllegalArgumentException("not supported: " + value);
//        }
//    }
}
