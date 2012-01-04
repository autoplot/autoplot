/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.idlsupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.dataset.SemanticOps;

/**
 * It's impossible to pass the QDataSet directly back to IDL, in fact structures
 * can't be passed.  To provide a QDataSet to IDL, extend this class to implement
 * getDataSet, then use QDataSetBridge.pro to read in a dataset and create a 
 * structure.
 * 
 * @author jbf
 */
public abstract class QDataSetBridge {

    QDataSet ds;
    Exception exception;
    
    String name;
    Map<String, QDataSet> datasets;
    Map<String, String> sliceDep;
    Map<QDataSet, String> names;
    List<Units> prefUnits; // convert to these if possible
    double fill; // use this fill value
    boolean useFill=false; // true means convert fill values

    boolean debug= false;
    
    QDataSetBridge() {
        datasets = new LinkedHashMap<String, QDataSet>();
        names = new LinkedHashMap<QDataSet, String>();
        sliceDep= new LinkedHashMap<String,String>();
        prefUnits= new ArrayList();
        System.err.println("QDataSetBridge v1.8.01");
    }

    /**
     * set the preferred units for the data.  The code will convert
     * to these units when a converter is found.
     * If a preference is indicated when it is convertible to an existing
     * preference, the existing preference is removed.
     * See clearPreferredUnits to remove all preferences.
     * Example units strings (capitalization matters):
     *    seconds since 2010-01-01T00:00
     *    days since 2010-01-01T00:00
     *    Hz
     *    kHz
     *    MHz
     *
     * @param sunits
     */
    public synchronized void setPreferredUnits( String sunit ) {
        Units unit;
        if ( sunit.contains(" since ") ) {
            unit= SemanticOps.lookupUnits(sunit);
        } else {
            unit= Units.getByName(sunit);
        }
        boolean add= true;
        for ( int i=0; i<prefUnits.size(); i++ ) {
            if ( prefUnits.get(i).isConvertableTo(unit) ) {
                if ( debug ) {
                    System.err.println("replacing preferred unit "+prefUnits.get(i)+ " with "+unit);
                }
                prefUnits.set(i,unit);
                add= false;
            }
        }
        if ( add ) {
            if ( debug ) System.err.println( "add preferred unit: "+unit );
            prefUnits.add(unit);
        }
    }

    public synchronized void clearPreferredUnits() {
        prefUnits= new ArrayList();
    }

    /**
     * set the value to return when the data is invalid.
     * @param d
     */
    public void setFillValue( double d ) {
        this.fill= d;
        this.useFill= true;
    }

    /**
     * turn on/off debug messages
     * @param debug
     */
    public void setDebug( boolean debug ) {
        System.err.println("setting debug="+debug);
        this.debug= debug;
    }

    /**
     * don't use fill value.
     */
    public void clearFillValue() {
        this.useFill= false;
    }
    /**
     * performs the read.  Note no progress status is available and this blocks until the read is done.
     * doGetDataSet(mon) should be called if progress is needed.
     * 2011-01-01: getStatus or getStatusMessage should be called afterwards to check the result of the load, this will no longer throw an exception.
     */
    public void doGetDataSet() {
        this.exception= null;
        try {
            this.ds = getDataSet( new NullProgressMonitor() );

            datasets.clear();
            name = nameFor(ds);

            datasets.put(name, ds);

            for (int i = 0; i < ds.rank(); i++) {
                QDataSet dep = (QDataSet) ds.property("DEPEND_" + i);
                if (dep != null) datasets.put(nameFor(dep), dep);
                QDataSet depslice= (QDataSet) ds.property("DEPEND_" + i, 0 );
                if ( depslice!=null ) {
                    sliceDep.put( nameFor(depslice), "DEPEND_"+i );
                }
            }
        } catch ( Exception ex ) {
            this.exception= ex;
            ex.printStackTrace(); // print the exception, because Exception handling is inconsistent with Matlab and IDL.
            return;
        }
    }

    /**
     * initiates the read on a separate thread, so this does not block and should
     * be used with caution.  See getProgressMonitor for use.
     *
     * Note because there is one exception that is stored, a QDataSetBridge object
     * is only able to load one dataset at a time.  Simultaneous loads should
     * be done with multiple QDataSetBridge objects.
     */
    public void doGetDataSet(final ProgressMonitor mon)  {
        this.exception= null;
        Runnable run = new Runnable() {

            public void run() {
                datasets.clear();
                name= "";
                try {
                    ds = getDataSet(mon);
                    if ( ds==null ) return;
                    
                } catch (Exception ex) {
                    exception= ex;
                    ex.printStackTrace();
                    mon.setProgressMessage("EXCEPTION");
                    mon.finished();
                    return;
                }

                name = nameFor(ds);
                
                datasets.put(name, ds);

                for (int i = 0; i < ds.rank(); i++) {
                    QDataSet dep = (QDataSet) ds.property("DEPEND_" + i);
                    if (dep != null) datasets.put(nameFor(dep), dep);
                    QDataSet depslice= (QDataSet) ds.property("DEPEND_" + i, 0 );
                    if ( depslice!=null ) {
                        sliceDep.put( nameFor(depslice), "DEPEND_"+i );
                    }
                }

            }
        };
         
        new Thread(run).start();

    }   

    /**
     * return the Exception from the last doGetDataSet call.
     * @return
     */
    public Exception getException() {
        return exception;
    }

    /**
     * returns 0 for last get operation successful
     * @return
     */
    public int getStatus() {
        return exception==null ? 0 : 1;
    }

    /**
     * returns "" for last operation successful, or non-empty indicating the problem.  Note
     * getException will return the Java exception for deeper inspection.
     * @return "" or the error message
     */
    public String getStatusMessage() {
        if ( exception==null ) {
            return "";
        } else {
            String s= exception.getMessage();
            if ( s!=null && s.length()>0 ) {
                return s;
            } else {
                return exception.toString(); // sorry!
            }
        }
    }

    public synchronized String nameFor(QDataSet dep0) {
        String name1 = names.get(dep0);

        if (name1 == null) {
            name1 = (String) dep0.property(QDataSet.NAME);
        }
        if (name1 == null) {
            name1 = "ds_" + names.size();
        }

        names.put(dep0, name1);

        return name1;
    }

    /**
     * implementations should provide this method for making the data accessible.
     * @param mon
     * @return
     * @throws Exception if any problem occurs with the read, it will be available to clients in getException or getStatusMessage
     */
    abstract QDataSet getDataSet(ProgressMonitor mon) throws Exception;

    /**
     * returns an object that can be used to monitor the progress of a download.
     * 
     * mon= qds->getProgressMonitor();
     * qds->doGetDataSet( mon )
     * while ( ! mon->isFinished() ) do begin
     *    print, strtrim( mon->getTaskProgress(), 2 ) + "  " + strtrim( mon->getTaskSize(), 2 )
     *    wait, 0.2   ; don't overload the thread
     * endwhile
     * 
     * @return
     */
    public ProgressMonitor getProgressMonitor() {
        return new NullProgressMonitor();
    }

    public void values(String name, double[] result) {
        if ( debug ) {
            System.err.println("reading "+name+" into double["+result.length+"]" );
        }
        QDataSet ds1 = datasets.get(name);
        copyValues( ds1, result );
    }

    public void values(String name, double[][] result) {
        if ( debug ) {
            System.err.println("reading "+name+" into double["+result.length+","+result[0].length+"]" );
        }
        QDataSet ds1 = datasets.get(name);
        copyValues( ds1, result );
    }

    public void values(String name, double[][][] result) {
        if ( debug ) {
            System.err.println("reading "+name+" into double["+result.length
                +","+result[0].length
                +","+result[0][0].length+"]" );
        }
        QDataSet ds1 = datasets.get(name);
        copyValues( ds1, result );
    }

    public void values(String name, double[][][][] result) {
        if ( debug ) {
            System.err.println("reading "+name+" into double["+result.length
                +","+result[0].length
                +","+result[0][0].length
                +","+result[0][0][0].length+"]" );
        }
        QDataSet ds1 = datasets.get(name);
        copyValues( ds1, result );
    }

    public void values(double[] result) {
        values(this.name(), result);
    }

    public void values(double[][] result) {
        values(this.name(), result);
    }

    public void values(double[][][] result) {
        values(this.name(), result);
    }

    public void values(double[][][][] result) {
        values(this.name(), result);
    }

    /**
     * returns the converter if there is one
     * @param ds1
     * @return
     */
    private UnitsConverter maybeGetConverter( QDataSet ds1 ) {
        Units u= SemanticOps.getUnits(ds1);
        UnitsConverter uc= UnitsConverter.IDENTITY;
        if ( prefUnits!=null ) {
            for ( Units prefUnit: prefUnits ) {
                if ( prefUnit.isConvertableTo(u) ) {
                    uc= u.getConverter(prefUnit);
                    if ( uc!=UnitsConverter.IDENTITY ) {
                        if ( debug ) {
                            System.err.println("Using units converter to get "+prefUnit );
                        }
                    }
                }
            }
        }
        return uc;
    }

    /* -- convert qubes to double arrays -- */
    private void copyValues( QDataSet ds1, double[] result ) {
        UnitsConverter uc= maybeGetConverter(ds1);
        QDataSet wds= DataSetUtil.weightsDataSet(ds1);
        if ( debug ) {
            System.err.println("copyValues rank1 into double using "+uc);
        }
        for (int i0 = 0; i0 < ds1.length(); i0++) {
            if ( useFill && wds.value(i0)==0 ) {
                result[i0] = fill;
            } else {
                result[i0] = uc.convert( ds1.value(i0) );
            }
        }
    }

    private void copyValues( QDataSet ds1, double[][] result ) {
        UnitsConverter uc= maybeGetConverter(ds1);
        QDataSet wds= DataSetUtil.weightsDataSet(ds1);
        for (int i0 = 0; i0 < ds1.length(); i0++) {
            for (int i1 = 0; i1 < ds1.length(i0); i1++) {
                if ( useFill && wds.value(i0, i1 )==0 ) {
                    result[i0][i1] = fill;
                } else {
                    result[i0][i1] = uc.convert( ds1.value(i0, i1) );
                }
            }
        }
    }
    private void copyValues( QDataSet ds1, double[][][] result ) {
        UnitsConverter uc= maybeGetConverter(ds1);
        QDataSet wds= DataSetUtil.weightsDataSet(ds1);
        for (int i0 = 0; i0 < ds1.length(); i0++) {
            for (int i1 = 0; i1 < ds1.length(i0); i1++) {
                for (int i2 = 0; i2 < ds1.length(i0,i1); i2++) {
                    if ( useFill && wds.value(i0, i1, i2 )==0 ) {
                        result[i0][i1][i2] = fill;
                    } else {
                        result[i0][i1][i2] = uc.convert( ds1.value(i0, i1, i2) );
                    }
                }
            }
        }
    }
    private void copyValues( QDataSet ds1, double[][][][] result ) {
        UnitsConverter uc= maybeGetConverter(ds1);
        QDataSet wds= DataSetUtil.weightsDataSet(ds1);
        for (int i0 = 0; i0 < ds1.length(); i0++) {
            for (int i1 = 0; i1 < ds1.length(i0); i1++) {
                for (int i2 = 0; i2 < ds1.length(i0,i1); i2++) {
                    for (int i3 = 0; i3 < ds1.length(i0,i1,i2); i2++) {
                        if ( useFill && wds.value(i0, i1, i2, i3 )==0 ) {
                            result[i0][i1][i2][i3] = fill;
                        } else {
                            result[i0][i1][i2][i3] = uc.convert( ds1.value(i0, i1, i2, i3 ) );
                        }
                    }
                }
            }
        }
    }

    /**
     * accessor for non-qube
     * @param name
     * @param i
     * @param result
     */
    public void slice(String name, int i, double[] result) {
        if ( debug ) {
            System.err.println("reading "+name+"["+i+"] into double["+result.length +"]" );
        }
        QDataSet ds1;
        if ( datasets.get(name)!=null ) {
            ds1 = datasets.get(name).slice(i);
        } else {
            throw new IllegalArgumentException("did not find dataset name="+name );
        }
        copyValues( ds1, result );
    }

    /**
     * accessor for non-qube
     * @param name
     * @param i
     * @param result
     */
    public void slice(String name, int i, double[][] result) {
        if ( debug ) {
            System.err.println("reading "+name+"["+i+"] into double["+result.length
                +","+result[0].length
                +"]" );
        }
        QDataSet ds1;
        if ( datasets.get(name)!=null ) {
            ds1 = datasets.get(name).slice(i);
        } else {
            throw new IllegalArgumentException("did not find dataset name="+name );
        }
        copyValues( ds1, result );
    }

    public void slice(String name, int i, double[][][] result) {
        if ( debug ) {
           System.err.println("reading "+name+"["+i+"] into double["+result.length
                +","+result[0].length
                +","+result[0][0].length
                +"]" );
        }
        QDataSet ds1;
        if ( datasets.get(name)!=null ) {
            ds1 = datasets.get(name).slice(i);
        } else {
            throw new IllegalArgumentException("did not find dataset name="+name );
        }
        copyValues( ds1, result );
    }

    public void slice(int i, double[] result) {
        slice(this.name(), i, result);
    }

    public void slice(int i, double[][] result) {
        slice(this.name(), i, result);
    }

    public void slice(int i, double[][][] result) {
        slice(this.name(), i, result);
    }

    /**
     * return an 1,2,or 3-D array of doubles or floats containing the values
     * in the specified dataset.
     * @param name
     * @return
     */
    public Object values(String name) {
        if ( debug ) {
            System.err.println("reading values for dataset " + name );
        }
        QDataSet ds1 = datasets.get(name);
        if (ds1.rank() == 1) {
            double[] result = new double[ds1.length()];
            values(name, result);
            return result;
        } else if (ds1.rank() == 2) {
            double[][] result = new double[ds1.length()][ds1.length(0)];
            values(name, result);
            return result;
        } else if (ds1.rank() == 3) {
            double[][][] result = new double[ds1.length()][ds1.length(0)][ds1.length(0, 0)];
            values(name, result);
            return result;
        } else if (ds1.rank() == 4) {
            double[][][][] result = new double[ds1.length()][ds1.length(0)][ds1.length(0, 0)][ds1.length(0,0,0)];
            values(name, result);
            return result;
        } else {
            throw new IllegalArgumentException("rank limit");
        }
    }

    /**
     * return the i-th 1 or 2-D array of doubles or floats containing the values
     * in the specified rank 2 or 3 dataset.  This is to support non-qube datasets.
     * @param name
     * @param i
     * @return
     */
    public Object slice( String name, int i ) {
        if ( debug ) {
            System.err.println("reading values for slice " + i + " of dataset " + name );
        }
        QDataSet ds1 = datasets.get(name);
        if ( ds1==null && sliceDep.containsKey(name) ) {
            return sliceDep(name,i);
        }
        if ( ds1==null ) {
            throw new IllegalArgumentException("No such dataset: "+name );
        }
        if (ds1.rank() == 1 ) {
            throw new IllegalArgumentException("dataset is rank 1, slice not allowed");
        } else if (ds1.rank() == 2) {
            double[] result = new double[ds1.length(i)];
            slice(name, i, result);
            return result;
        } else if (ds1.rank() == 3) {
            double[][] result = new double[ds1.length(i)][ds1.length(i,0)];
            slice(name, i,result);
            return result;
        } else if (ds1.rank() == 4) {
            double[][][] result = new double[ds1.length(i)][ds1.length(i,0)][ds1.length(i,0,0)];
            slice(name, i,result);
            return result;
        } else {
            throw new IllegalArgumentException("rank limit");
        }
    }

    /**
     * we have to slice the main dataset first, then copy the values of the depend dataset
     * @param name
     * @param i
     * @return
     */
    private Object sliceDep( String name, int i ) {
        QDataSet ds1= (QDataSet) datasets.get(this.name).slice(i).property(sliceDep.get(name));
        if (ds1.rank() == 1 ) {
            double[] result = new double[ds1.length()];
            copyValues( ds1, result );
            return result;
        } else if (ds1.rank() == 2) {
            double[][] result = new double[ds1.length()][ds1.length(0)];
            copyValues( ds1, result );
            return result;
        } else if (ds1.rank() == 3) {
            double[][][] result = new double[ds1.length()][ds1.length(0)][ds1.length(0,0)];
            copyValues( ds1, result );
            return result;
        } else {
            throw new IllegalArgumentException("rank limit");
        }
    }

    /**
     * return an 1,2,or 3-D array of doubles or floats containing the values
     * in the default dataset.
     * @param name
     * @return
     */
    public Object values() {
        return values(name);
    }

    /**
     * return an 1,2,or 3-D array of doubles or floats containing the values
     * in a slice of the default dataset.
     * @param name
     * @return
     */
    public Object slice(int i0 ) {
        return slice(name,i0);
    }

    public String depend(int dim) {
        QDataSet result = (QDataSet) this.ds.property("DEPEND_" + dim);
        if (result == null) return "";
        else return nameFor(result);
    }

    public String plane(int iplane) {
        QDataSet result = (QDataSet) this.ds.property("PLANE_" + iplane);
        if (result == null) return "";
        else return nameFor(result);
    }

    public String propertyAsString(String property) {
        Object result = this.ds.property(property);
        if (result == null) return "";
        else return String.valueOf(result);
    }

    public double propertyAsDouble(String property) {
        Object result = this.ds.property(property);
        if (result == null) return Double.NaN;
        else return ((Number) result).doubleValue();
    }

    public String propertyAsString(String name, String property) {
        Object result = datasets.get(name).property(property);
        if (result == null) return "";
        else return String.valueOf(result);
    }

    public double propertyAsDouble(String name, String property) {
        Object result = datasets.get(name).property(property);
        if (result == null) return Double.NaN;
        else return ((Number) result).doubleValue();
    }

    /**
     * copy the data into a 1-D array.  Rank 2 and 3 dataset array are then
     * aliased with the highest dimension the most tightly packed.
     * @param name
     * @param result
     */
    public void valuesAlias(String name, double[] result) {
        QDataSet ds1 = datasets.get(name);
        QubeDataSetIterator it = new QubeDataSetIterator(ds1);
        int iele = 0;
        while (it.hasNext()) {
            it.next();
            result[iele] = it.getValue(ds1);
            iele++;
        }
    }

    /**
     * get the string values of the data instead of the numbers.  This provides
     * a means to decode times and nominal data such as labels data sets.
     * @param name
     * @param result
     */
    public void labelsAlias(String name, String[] result) {
        QDataSet ds1 = datasets.get(name);
        QubeDataSetIterator it = new QubeDataSetIterator(ds1);
        int iele = 0;
        Units u = (Units) ds1.property(QDataSet.UNITS);
        if (u == null) u = Units.dimensionless;
        while (it.hasNext()) {
            it.next();
            result[iele] = u.createDatum(it.getValue(ds1)).toString();
            iele++;
        }
    }

    /**
     * return the lengths of the dimensions of the specified dataset.
     * @param name
     * @return
     */
    public int[] lengths(String name) {
        return DataSetUtil.qubeDims(datasets.get(name));
    }

    /**
     * return the lengths of the dimensions of the default dataset.
     * @return
     */
    public int[] lengths() {
        return DataSetUtil.qubeDims(datasets.get(name));
    }

    /**
     * return the length of the dimensions of the dataset, once sliced at index i.
     * @param name
     * @return
     */
    public int[] lengths(String name, int i) {
        QDataSet ds1= datasets.get(name);
        if ( ds1==null && sliceDep.containsKey(name) ) {
            ds1= (QDataSet) datasets.get(this.name).slice(i).property(sliceDep.get(name));
            return DataSetUtil.qubeDims(ds1);
        } else {
            if ( ds1==null ) {
                throw new IllegalArgumentException("No such dataset: "+name);
            }
            return DataSetUtil.qubeDims(ds1.slice(i));
        }
    }

    /**
     * return the length of the zeroth dimension of the main dataset.
     * @param name
     * @return
     */
    public int[] lengths(int i) {
        return lengths(name,i);
    }

    /**
     * return the length of the zeroth dimension of the dataset
     * @param name
     * @return
     */
    public int length(String name) {
        QDataSet ds1= datasets.get(name);
        if ( ds1==null ) {
            throw new IllegalArgumentException("unable to get length for slice dataset, use lengths");
        } else {
            return ds1.length();
        }
    }

    /**
     * return the length of the zeroth dimension of the main dataset.
     * @param name
     * @return
     */
    public int length() {
        return length(name);
    }

    
    /**
     * return the number of dimensions of the specified dataset.
     * @param name
     * @return
     */
    public int rank(String name) {
        return datasets.get(name).rank();
    }

    /**
     * return the number of dimensions of the default dataset.
     * @return
     */
    public int rank() {
        return datasets.get(name).rank();
    }

    public boolean isQube() {
        return DataSetUtil.isQube( datasets.get(name) );
    }

    /**
     * returns one of String, int, double, float, int[], double, float[]
     * @param name
     * @param propname
     * @return
     */
    public Object property(String name, String propname, int i ) {
        Object prop = datasets.get(name).property(propname,i);
        if (prop instanceof QDataSet) {
            return nameFor((QDataSet) prop);
        } else if (prop instanceof Units) {
            for ( Units u: prefUnits ) {
                if ( u.isConvertableTo( SemanticOps.getUnits( datasets.get(this.name) )) ) {
                    return u.toString();
                }
            }
            return prop.toString();
        } else if ( propname.equals(QDataSet.FILL_VALUE) && this.useFill ) {
            return this.fill;
        } else {
            return prop;
        }
    }

    public boolean hasProperty(String name, String propname, int i) {
        return datasets.get(name).property(propname,i) != null;
    }

    /**
     * get the properties for the named dataset
     * @param name
     * @return
     */
    public Map<String, Object> properties(String name,int i) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>(DataSetUtil.getProperties(datasets.get(name).slice(i))); //TODO: strange implementation
        for (String s : result.keySet()) {
            result.put(s, property(name, s,i));
        }
        return result;
    }


    /**
     * returns one of String, int, double, float, int[], double, float[]
     * @param name
     * @param propname
     * @return
     */
    public Object property(String name, String propname) {
        Object prop = datasets.get(name).property(propname);
        if (prop instanceof QDataSet) {
            return nameFor((QDataSet) prop);
        } else if (prop instanceof Units) {
            Units dsu=  SemanticOps.getUnits( datasets.get(name) );
            for ( Units u: prefUnits ) {
                if ( u.isConvertableTo( dsu ) ) {
                    return u.toString();
                }
            }
            return prop.toString();
        } else if ( propname.equals(QDataSet.FILL_VALUE) && this.useFill ) {
            return this.fill;
        } else {
            return prop;
        }
    }

    public boolean hasProperty(String name, String propname) {
        return datasets.get(name).property(propname) != null;
    }

    /**
     * get the properties for the named dataset
     * @param name
     * @return
     */
    public Map<String, Object> properties(String name) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>(DataSetUtil.getProperties(datasets.get(name)));
        for (String s : result.keySet()) {
            result.put(s, property(name, s));
        }
        return result;
    }

    /**
     * returns one of String, int, double, float, int[], double, float[]
     * @param name
     * @param propname
     * @return
     */
    public Object property(String propname) {
        Object prop = datasets.get(name).property(propname);
        if (prop instanceof QDataSet) {
            return nameFor((QDataSet) prop);
        } else if (prop instanceof Units) {
            return prop.toString();
        } else {
            return prop;
        }
    }

    public boolean hasProperty(String propname) {
        return datasets.get(name).property(propname) != null;
    }

    public Map<String, Object> properties() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>(DataSetUtil.getProperties(datasets.get(name)));
        for (String s : result.keySet()) {
            result.put(s, property(name, s));
        }
        return result;
    }

    /**
     * returns one of String, int, double, float, int[], double, float[]
     * @param name
     * @param propname
     * @return
     */
    public Object property(String propname,int i) {
        Object prop = datasets.get(name).property(propname,i);
        if (prop instanceof QDataSet) {
            return nameFor((QDataSet) prop);
        } else if (prop instanceof Units) {
            return prop.toString();
        } else {
            return prop;
        }
    }

    public boolean hasProperty(String propname,int i) {
        return datasets.get(name).property(propname,i) != null;
    }

    public Map<String, Object> properties(int i) {
        LinkedHashMap<String, Object> result = new LinkedHashMap(DataSetUtil.getProperties(datasets.get(name).slice(i) ));
        return result;
    }

    public String[] names() {
        return datasets.keySet().toArray(new String[datasets.size()]);
    }

    public String name() {
        return name;
    }
}
