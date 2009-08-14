/*
 * CdfFileDataSource.java
 *
 * Created on July 23, 2007, 8:15 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.cdfdatasource;

import org.das2.datum.Units;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.datum.UnitsConverter;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.RankZeroDataSet;
import org.virbo.dataset.WritableDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.datasource.MetadataModel;
import org.virbo.dsops.Ops;
import org.virbo.metatree.MetadataUtil;

/**
 *
 * @author jbf
 */
public class CdfFileDataSource extends AbstractDataSource {

    Map properties;
    Map<String, Object> attributes;

    private final static Logger logger= Logger.getLogger(CdfFileDataSource.class.getName());

    /** Creates a new instance of CdfFileDataSource */
    public CdfFileDataSource(URL url) {
        super(url);
    }

    /* read all the variable attributes into a Map */
    private HashMap<String, Object> readAttributes(CDF cdf, Variable var, int depth) {
        LinkedHashMap<String, Object> properties = new LinkedHashMap<String, Object>();
        Pattern p = Pattern.compile("DEPEND_[0-9]");

        Vector v = cdf.getAttributes();
        for ( int ipass=0; ipass<2; ipass++ ) { // first pass is for subtrees, second pass is for items
            for (int ivar = 0; ivar < v.size(); ivar++) {
                Attribute attr = (Attribute) v.get(ivar);
                Entry entry = null;
                try {
                    entry = attr.getEntry(var);
                    boolean isDep= p.matcher(attr.getName()).matches() & depth == 0;
                    if ( ipass==0 && isDep ) {
                        Object val = entry.getData();
                        String name = (String) val;
                        Map<String, Object> newVal = readAttributes(cdf, cdf.getVariable(name), depth + 1);
                        newVal.put("NAME", name); // tuck it away, we'll need it later.
                        properties.put(attr.getName(), newVal);

                    } else if ( ipass==1 && !isDep ) {
                        properties.put(attr.getName(), entry.getData());
                    }
                } catch (CDFException e) {
                }
            }
        }

        return properties;
    }

    public synchronized QDataSet getDataSet(ProgressMonitor mon) throws IOException, CDFException, ParseException {
        File cdfFile;
        cdfFile = getFile(mon);

        String fileName = cdfFile.toString();
        //if (System.getProperty("os.name").startsWith("Windows")) {
        //    fileName = CdfUtil.win95Name(cdfFile);
        //}
        Map map = getParams();

        CDF cdf = CDF.open(fileName, CDF.READONLYoff);
        String svariable = (String) map.get("id");
        if (svariable == null) {
            svariable = (String) map.get("arg_0");
        }

        String constraint = null;
        int i = svariable.indexOf("[");
        if (i != -1) {
            constraint = svariable.substring(i);
            svariable = svariable.substring(0, i);
        }

        try {
            Variable variable = cdf.getVariable(svariable);
            String interpMeta = (String) map.get("interpMeta");
            if (!"no".equals(interpMeta)) {
                long numRec= variable.getNumWrittenRecords();
                long[] recs= DataSourceUtil.parseConstraint( constraint, numRec );
                attributes = readAttributes(cdf, variable, 0);
                if ( recs[2]==-1 ) {
                    attributes= MetadataUtil.sliceProperties(attributes, 0);
                }
            }
            WritableDataSet result = wrapDataSet(cdf, svariable, constraint, false);
            cdf.close();

            if (!"no".equals(interpMeta)) {
                MetadataModel model = new IstpMetadataModel();
                Map<String, Object> istpProps = model.properties(attributes);
                Units pu= (Units) istpProps.get(QDataSet.UNITS);
                Units u= (Units) result.property( QDataSet.UNITS );
                UnitsConverter uc;
                if ( u==Units.cdfEpoch ) {
                    uc= UnitsConverter.IDENTITY;
                } else {
                    uc= UnitsConverter.getConverter( pu, u );
                }
                Number nn= (Number)istpProps.get(QDataSet.VALID_MAX);
                if ( nn!=null ) result.putProperty(QDataSet.VALID_MAX, uc.convert(nn) );
                nn= (Number)istpProps.get(QDataSet.VALID_MIN);
                if ( nn!=null ) result.putProperty(QDataSet.VALID_MIN, uc.convert(nn) );
                result.putProperty(QDataSet.FILL_VALUE, istpProps.get(QDataSet.FILL_VALUE));
            // apply properties.
            }
            return result;
        } catch (CDFException ex) {
            throw new IllegalArgumentException("no such variable: " + svariable);
        }

    }

    
    /**
     * @param reform for depend_1, we read the one and only rec, and the rank is decreased by 1.
     */
    private WritableDataSet wrapDataSet(final CDF cdf, final String svariable, final String constraints, boolean reform) throws CDFException, ParseException {
        Variable variable = cdf.getVariable(svariable);

        HashMap thisAttributes = readAttributes(cdf, variable, 0);

        long varType = variable.getDataType();
        long numRec = variable.getNumWrittenRecords();


        if (numRec == 0) {
            if (thisAttributes.containsKey("COMPONENT_0")) {
                // themis kludge that CDAWeb supports, so we support it too.  The variable has no records, but has
                // two attributes, COMPONENT_0 and COMPONENT_1.  These are two datasets that should be added to
                // get the result.  Note cdf_epoch16 fixes the shortcoming that themis was working around.
                QDataSet c0 = wrapDataSet(cdf, (String) thisAttributes.get("COMPONENT_0"), constraints, true);
                if (thisAttributes.containsKey("COMPONENT_1")) {
                    QDataSet c1 = wrapDataSet(cdf, (String) thisAttributes.get("COMPONENT_1"), constraints, false);
                    if (c0.rank() == 1 && CdfDataSetUtil.validCount(c0, 2) == 1 && c1.length() > 1) { // it should have been rank 0.
                        c0 = DataSetOps.slice0(c0, 0);
                        // Convert the units to the more precise Units.us2000.  We may still truncate here, and the only way
                        // to avoid this would be to introduce a new Basis that is equal to c0.
                        if (Units.cdfEpoch == c0.property(QDataSet.UNITS)) {
                            double value = ((RankZeroDataSet) c0).value();
                            double valueUs2000 = Units.cdfEpoch.convertDoubleTo(Units.us2000, value);
                            c0 = DataSetUtil.asDataSet(Units.us2000.createDatum(valueUs2000));
                        }
                    }
                    c0 = CdfDataSetUtil.add(c0, c1);
                }
                return DDataSet.maybeCopy(c0);
            } else {
                throw new IllegalArgumentException("variable " + svariable + " contains no records!");
            }
        }

        long[] recs = DataSourceUtil.parseConstraint(constraints, numRec);
        boolean slice= recs[1]==-1;
        WritableDataSet result;
        if (reform) {
            //result = CdfUtil.wrapCdfHyperDataHacked(variable, 0, -1, 1); //TODO: this doesn't handle strings properly.
            result = CdfUtil.wrapCdfHyperData(variable, 0, -1, 1);
        } else {
            long recCount = (recs[1] - recs[0]) / recs[2];
            if ( slice ) {
                recCount= -1;
                recs[2]= 1;
            }
            result = CdfUtil.wrapCdfHyperDataHacked(variable, recs[0], recCount, recs[2]);
            //result = CdfUtil.wrapCdfHyperData(variable, recs[0], recCount, recs[2]);
        }
        result.putProperty(QDataSet.NAME, svariable);

        final boolean doUnits = true;
        if (doUnits) {
            if (thisAttributes.containsKey("UNITS")) {
                String sunits= (String) thisAttributes.get("UNITS");
                Units mu;
                if ( sunits.equalsIgnoreCase("row number") || sunits.equalsIgnoreCase("column number" ) ) { // kludge for POLAR/VIS
                    mu= Units.dimensionless;
                } else {
                    mu = MetadataUtil.lookupUnits(sunits);
                }
                Units u = (Units) result.property(QDataSet.UNITS);
                if (u == null) {
                    result.putProperty(QDataSet.UNITS, mu);
                }
            }
        }

        int[] qubeDims= DataSetUtil.qubeDims(result);
        for (int idep = 0; idep < 3; idep++) {
            int sidep= slice ? (idep+1) : idep; // idep taking slice into account.
            Map dep = (Map) thisAttributes.get( "DEPEND_" + sidep );
            String labl = (String) thisAttributes.get("LABL_PTR_" + sidep);
            if ( labl==null ) labl= (String) thisAttributes.get("LABEL_" + sidep); // kludge for c4_cp_fgm_spin_20030102_v01.cdf?B_vec_xyz_gse__C4_CP_FGM_SPIN
            if ( dep != null && qubeDims.length<=idep ) {
                logger.info("DEPEND_"+idep+" found but data is lower rank");
                continue;
            }
            if (dep != null && ( qubeDims[idep]>6 || labl == null) ) {
                try {
                    WritableDataSet depDs = wrapDataSet(cdf, (String) dep.get("NAME"), idep == 0 ? constraints : null, idep > 0);
                    //kludge for LANL_1991_080_H0_SOPA_ESP_19920308_V01.cdf?FPDO
                    if (depDs.rank() == 2 && depDs.length(0) == 2) {
                        WritableDataSet depDs1 = (WritableDataSet) Ops.reduceMean(depDs, 1);
                        QDataSet binmax = DataSetOps.slice1(depDs, 1);
                        QDataSet binmin = DataSetOps.slice1(depDs, 0);
                        depDs1.putProperty(QDataSet.DELTA_MINUS, Ops.subtract(depDs1, binmin));
                        depDs1.putProperty(QDataSet.DELTA_PLUS, Ops.subtract(binmax, depDs1));
                        depDs = depDs1;
                    }

                    if (DataSetUtil.isMonotonic(depDs)) {
                        depDs.putProperty(QDataSet.MONOTONIC, Boolean.TRUE);
                    } else {
                        if (sidep == 0) {
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
                if (labl != null) {
                    try {
                        WritableDataSet depDs = wrapDataSet(cdf, labl, idep == 0 ? constraints : null, idep > 0);
                        result.putProperty("DEPEND_" + idep, depDs);
                    } catch (Exception e) {
                        e.printStackTrace(); // to support lanl.
                    }
                }
            }
        }

        boolean swapHack = false; // TODO: figure out where this was needed.

        if ( result.rank() == 3) {
            int n1 = result.length(0);
            int n2 = result.length(0, 0);
            QDataSet dep1 = (QDataSet) result.property(QDataSet.DEPEND_1);
            QDataSet dep2 = (QDataSet) result.property(QDataSet.DEPEND_2);
            if (n1 != n2 && dep1 != null && dep1.length() == n2) {
                if (dep2 != null && dep2.length() == n1) {
                    swapHack = true;
                    System.err.println("swaphack avoids runtime error");
                }
            }
        }

        if ( slice && result.rank()==2 ) {
            int n0 = result.length();
            int n1 = result.length(0);
            QDataSet dep0 = (QDataSet) result.property(QDataSet.DEPEND_0);
            QDataSet dep1 = (QDataSet) result.property(QDataSet.DEPEND_1);
            if (n0 != n1 && dep0 != null && dep0.length() == n1) {
                if (dep1 != null && dep1.length() == n0) {
                    swapHack = true;
                    System.err.println("swaphack avoids runtime error");
                }
            }
        }

        if (swapHack && result.rank() == 3) { // need to swap for rank 3.
            QDataSet dep1 = (QDataSet) result.property(QDataSet.DEPEND_1);
            QDataSet dep2 = (QDataSet) result.property(QDataSet.DEPEND_2);
            result.putProperty(QDataSet.DEPEND_2, dep1);
            result.putProperty(QDataSet.DEPEND_1, dep2);

            Object att1 = attributes.get(QDataSet.DEPEND_1);
            Object att2 = attributes.get(QDataSet.DEPEND_2);
            attributes.put(QDataSet.DEPEND_1, att2);
            attributes.put(QDataSet.DEPEND_2, att1);
        }

        if (swapHack && slice && result.rank() == 2) { // need to swap for rank 3.
            QDataSet dep0 = (QDataSet) result.property(QDataSet.DEPEND_0);
            QDataSet dep1 = (QDataSet) result.property(QDataSet.DEPEND_1);
            result.putProperty(QDataSet.DEPEND_1, dep0);
            result.putProperty(QDataSet.DEPEND_0, dep1);

            Object att0 = attributes.get(QDataSet.DEPEND_0);
            Object att1 = attributes.get(QDataSet.DEPEND_1);
            attributes.put(QDataSet.DEPEND_0, att1);
            attributes.put(QDataSet.DEPEND_1, att0);
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
    public synchronized Map<String, Object> getMetaData(ProgressMonitor mon) {
        if (attributes == null) {
            return null; // transient state
        }
        return attributes;
    }
}
