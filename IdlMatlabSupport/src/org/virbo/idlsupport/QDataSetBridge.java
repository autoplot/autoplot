/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.idlsupport;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Units;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;

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
    Map<QDataSet, String> names;

    QDataSetBridge() {
        datasets = new HashMap<String, QDataSet>();
        names = new HashMap<QDataSet, String>();
        System.err.println("QDataSetBridge v1.4.0");
    }

    /**
     * initiates the read after 
     */
    public void doGetDataSet() throws Exception {
        this.ds = getDataSet( new NullProgressMonitor() );

        datasets.clear();
        name = nameFor(ds);

        datasets.put(name, ds);

        for (int i = 0; i < ds.rank(); i++) {
            QDataSet dep = (QDataSet) ds.property("DEPEND_" + i);
            if (dep != null) datasets.put(nameFor(dep), dep);
        }
    }

    /**
     * initiates the read after 
     */
    public void doGetDataSet(final ProgressMonitor mon)  {
        Runnable run = new Runnable() {

            public void run() {
                datasets.clear();
                name= "";
                try {
                    ds = getDataSet(mon);
                } catch (Exception ex) {
                    exception= ex;
                    mon.setProgressMessage("EXCEPTION");
                    mon.finished();
                    return;
                }

                name = nameFor(ds);
                
                datasets.put(name, ds);

                for (int i = 0; i < ds.rank(); i++) {
                    QDataSet dep = (QDataSet) ds.property("DEPEND_" + i);
                    if (dep != null) datasets.put(nameFor(dep), dep);
                }

            }
        };
         
        new Thread(run).start();

    }   
    
    public Exception getException() {
        return exception;
    }
    
    public synchronized String nameFor(QDataSet dep0) {
        String name = names.get(dep0);

        if (name == null) {
            name = (String) dep0.property(QDataSet.NAME);
        }
        if (name == null) {
            name = "ds_" + names.size();
        }

        names.put(dep0, name);

        return name;
    }

    abstract QDataSet getDataSet(ProgressMonitor mon) throws Exception;

    /**
     * returns an object that can be used to monitor the progress of a download.
     * mon= qds->getProgressMonitor();
     * qds->getDataSet( mon )
     * while ( ! mon->isFinished() ) do begin
     *    print, strtrim( mon->getTaskProgress(), 2 ) + "  " + strtrim( mon->getTaskSize(), 2 )
     * endwhile
     * 
     * @return
     */
    public ProgressMonitor getProgressMonitor() {
        return new NullProgressMonitor();
    }

    public void values(String name, double[] result) {
        QDataSet ds1 = datasets.get(name);
        for (int i0 = 0; i0 < ds1.length(); i0++) {
            result[i0] = ds1.value(i0);
        }
    }

    public void values(String name, double[][] result) {
        QDataSet ds1 = datasets.get(name);
        for (int i0 = 0; i0 < ds1.length(); i0++) {
            for (int i1 = 0; i1 < ds1.length(i0); i1++) {
                result[i0][i1] = ds1.value(i0, i1);
            }
        }
    }

    public void values(String name, double[][][] result) {
        QDataSet ds1 = datasets.get(name);
        for (int i0 = 0; i0 < ds1.length(); i0++) {
            for (int i1 = 0; i1 < ds1.length(i0); i1++) {
                for (int i2 = 0; i2 < ds1.length(i0); i2++) {
                    result[i0][i1][i2] = ds1.value(i0, i1, i2);
                }
            }
        }
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

    /**
     * return an 1,2,or 3-D array of doubles or floats containing the values
     * in the specified dataset.
     * @param name
     * @return
     */
    public Object values(String name) {
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
            return prop.toString();
        } else {
            return prop;
        }
    }

    public boolean hasProperty(String name, String propname) {
        return datasets.get(name).property(propname) != null;
    }

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

    public String[] names() {
        return datasets.keySet().toArray(new String[datasets.size()]);
    }

    public String name() {
        return name;
    }
}
