/*
 * CdfFileDataSource.java
 *
 * Created on July 23, 2007, 8:15 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.cdfdatasource;

import edu.uiowa.physics.pw.das.datum.Units;
import org.virbo.metatree.IstpMetadataModel;
import org.das2.util.monitor.ProgressMonitor;
import gsfc.nssdc.cdf.Attribute;
import gsfc.nssdc.cdf.CDF;
import gsfc.nssdc.cdf.CDFException;
import gsfc.nssdc.cdf.Entry;
import gsfc.nssdc.cdf.Variable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SortDataSet;
import org.virbo.dataset.WritableDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.MetadataModel;
import org.virbo.metatree.MetadataUtil;

/**
 *
 * @author jbf
 */
public class CdfFileDataSource extends AbstractDataSource {

    HashMap properties;
    HashMap<String, Object> attributes;

    /** Creates a new instance of CdfFileDataSource */
    public CdfFileDataSource(URL url) {
        super(url);
    }

    /* read all the variable attributes into a HashMap */
    private HashMap<String, Object> readAttributes(CDF cdf, Variable var, int depth) {
        HashMap<String, Object> properties = new HashMap<String, Object>();
        Pattern p = Pattern.compile("DEPEND_[0-9]");

        Vector v = cdf.getAttributes();
        for (int i = 0; i < v.size(); i++) {
            Attribute attr = (Attribute) v.get(i);
            Entry entry = null;
            try {
                entry = attr.getEntry(var);


                if (p.matcher(attr.getName()).matches() & depth == 0) {
                    Object val = entry.getData();
                    String name = (String) val;
                    Map<String, Object> newVal = readAttributes(cdf, cdf.getVariable(name), depth + 1);
                    newVal.put("NAME", name); // tuck it away, we'll need it later.
                    properties.put(attr.getName(), newVal);

                } else {
                    properties.put(attr.getName(), entry.getData());
                }
            } catch (CDFException e) {
            }
        }

        return properties;
    }

    public org.virbo.dataset.QDataSet getDataSet(ProgressMonitor mon) throws IOException, CDFException, ParseException {
        File cdfFile;
        cdfFile = getFile(mon);

        String fileName = cdfFile.toString();
        if (System.getProperty("os.name").startsWith("Windows")) {
            fileName = CdfUtil.win95Name(cdfFile);
        }
        Map map = getParams();

        CDF cdf = CDF.open(fileName, CDF.READONLYon);
        String svariable = (String) map.get("id");
        if (svariable == null) {
            svariable = (String) map.get("arg_0");
        }

        String constraint= null;
        int i= svariable.indexOf("[");
        if ( i!=-1 ) {
            constraint= svariable.substring(i);
            svariable= svariable.substring(0,i);
        }
        
        try {
            Variable variable = cdf.getVariable(svariable);
            attributes = readAttributes(cdf, variable, 0);

            WritableDataSet result = wrapDataSet(cdf, svariable, constraint, false);
            cdf.close();

            return result;
        } catch (CDFException ex) {
            throw new IllegalArgumentException("no such variable: "+svariable);
        }

    }

    /**
     * returns [ start, stride, stop ]
     * @param constraint
     * @return
     */
    private long[] parseConstraint( String constraint, long recCount ) throws ParseException {
        if ( constraint==null ) {
            return new long[] { 0, 1, recCount };
        } else {
            Pattern p= Pattern.compile("\\[(\\d+):(\\d+)\\]");
            Matcher m= p.matcher(constraint);
            if ( m.matches() ) {
                return new long[] { Integer.parseInt(m.group(1)), 1, Integer.parseInt(m.group(2)) };
            } else {
                throw new ParseException("no match!", 0);
            }
        }
    }
    
    
    /**
     * @param reform for depend_1, we read the one and only rec, and the rank is decreased by 1.
     */
    private WritableDataSet wrapDataSet(final CDF cdf, final String svariable, final String constraints, boolean reform) throws CDFException, ParseException {
        Variable variable = cdf.getVariable(svariable);

        long varType = variable.getDataType();
        long numRec = variable.getNumWrittenRecords();

        if (numRec == 0) {
            throw new IllegalArgumentException("variable " + svariable + " contains no records!");
        }
        
        long[] recs= parseConstraint( constraints, numRec );
        
        WritableDataSet result;
        if (reform) {
            result = CdfUtil.wrapCdfHyperData(variable, 0, -1);
        } else {
            result = CdfUtil.wrapCdfHyperData(variable, recs[0], recs[2]-recs[0] );
        }
        result.putProperty(QDataSet.NAME, svariable);
        HashMap thisAttributes = readAttributes(cdf, variable, 0);

        final boolean doUnits = true;
        if (doUnits && thisAttributes.containsKey("UNITS")) {
            Units mu = MetadataUtil.lookupUnits((String) thisAttributes.get("UNITS"));
            Units u = (Units) result.property(QDataSet.UNITS);
            if (u == null) {
                result.putProperty(QDataSet.UNITS, mu);
            }
        }

        for (int idep = 0; idep < 3; idep++) {
            Map dep = (Map) thisAttributes.get("DEPEND_" + idep);
            if (dep != null) {
                try {
                    WritableDataSet depDs = wrapDataSet(cdf, (String) dep.get("NAME"), idep==0 ? constraints : null, idep > 0);
                    if (DataSetUtil.isMonotonic(depDs)) {
                        depDs.putProperty(QDataSet.MONOTONIC, Boolean.TRUE);
                    } else {
                        if (idep == 0) {
                            System.err.println("sorting dep0 to make depend0 monotonic");
                            QDataSet sort = org.virbo.dataset.DataSetOps.sort(depDs);
                            result = DataSetOps.applyIndex(result, idep, sort, false);
                            depDs = DataSetOps.applyIndex(depDs, 0, sort, false);
                            depDs.putProperty(QDataSet.MONOTONIC, Boolean.TRUE);
                        }
                    }
                    result.putProperty("DEPEND_" + idep, depDs);
                } catch (Exception e) {
                    e.printStackTrace(); // to support lanl.
                }
            } else {
                String s = (String) thisAttributes.get("LABL_PTR_" + idep);
                if (s != null) {
                    try {
                        WritableDataSet depDs = wrapDataSet(cdf, s, idep==0 ? constraints : null, idep > 0);
                        result.putProperty("DEPEND_" + idep, depDs);
                    } catch (Exception e) {
                        e.printStackTrace(); // to support lanl.
                    }
                }
            }
        }

        if (result.rank() == 3) { // need to swap for rank 3.
            QDataSet dep1 = (QDataSet) result.property(QDataSet.DEPEND_1);
            QDataSet dep2 = (QDataSet) result.property(QDataSet.DEPEND_2);
            result.putProperty(QDataSet.DEPEND_2, dep1);
            result.putProperty(QDataSet.DEPEND_1, dep2);

            Object att1 = attributes.get(QDataSet.DEPEND_1);
            Object att2 = attributes.get(QDataSet.DEPEND_2);
            attributes.put(QDataSet.DEPEND_1, att2);
            attributes.put(QDataSet.DEPEND_2, att1);
        }

        return result;
    }

    @Override
    public boolean asynchronousLoad() {
        return true;
    }

    @Override
    public MetadataModel getMetadataModel() {
        return new IstpMetadataModel();
    }

    @Override
    public Map<String, Object> getMetaData(ProgressMonitor mon) {
        if (attributes == null) {
            return null; // transient state
        }
        return attributes;
    }
}
