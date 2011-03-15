/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.cdf;

import gov.nasa.gsfc.voyager.cdf.CDF;
import gov.nasa.gsfc.voyager.cdf.CDFFactory;
import gov.nasa.gsfc.voyager.cdf.Variable;
import java.io.File;
import java.lang.reflect.Array;
import java.net.URI;
import java.text.ParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.virbo.dataset.DataSetUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.RankZeroDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.datasource.MetadataModel;
import org.virbo.dsops.Ops;
import org.virbo.metatree.IstpMetadataModel;
import org.virbo.metatree.MetadataUtil;

/**
 *
 * @author jbf
 */
public class CdfJavaDataSource extends AbstractDataSource {

    Logger logger= Logger.getLogger("org.virbo.cdfjava");
    Map<String, Object> attributes;

    CdfJavaDataSource( URI uri ) {
        super(uri);
    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        File cdfFile;
        cdfFile = getFile(mon);

        String fileName = cdfFile.toString();
        //if (System.getProperty("os.name").startsWith("Windows")) {
        //    fileName = CdfUtil.win95Name(cdfFile);
        //}
        Map map = getParams();

        CDF cdf;
        try {
            cdf = CDFFactory.getCDF(fileName);
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
        String svariable = (String) map.get("id");
        if (svariable == null) {
            svariable = (String) map.get("arg_0");
        }

        if ( svariable==null ) {
            throw new IllegalArgumentException("CDF URI needs an argument");
        }

        String constraint = null;
        int i = svariable.indexOf("[");
        if (i != -1) {
            constraint = svariable.substring(i);
            svariable = svariable.substring(0, i);
        }
        svariable= svariable.replaceAll(" ", "+");

        try {
            Variable variable = cdf.getVariable(svariable);
            String interpMeta = (String) map.get("interpMeta");
            if (!"no".equals(interpMeta)) {
                long numRec= variable.getNumberOfValues();
                long[] recs= DataSourceUtil.parseConstraint( constraint, numRec );
                attributes = readAttributes(cdf, variable, 0);
                if ( recs[2]==-1 ) {
                    attributes= MetadataUtil.sliceProperties(attributes, 0);
                }
            }
            MutablePropertyDataSet result = wrapDataSet(cdf, svariable, constraint, false, true);
            //cdf.close();

            if (!"no".equals(interpMeta)) {
                MetadataModel model = new IstpMetadataModel();

                Map<String, Object> istpProps = model.properties(attributes);
                maybeAddValidRange(istpProps, result);
                result.putProperty(QDataSet.FILL_VALUE, istpProps.get(QDataSet.FILL_VALUE));
                result.putProperty(QDataSet.LABEL, istpProps.get("FIELDNAM") );
                result.putProperty(QDataSet.TITLE, istpProps.get("CATDESC" ) );
                if ( result.rank()<3 ) { // POLAR_H0_CEPPAD_20010117_V-L3-1-20090811-V.cdf?FEDU is "time_series"
                    if ( result.rank()==2 && result.length()>0 && result.length(0)<QDataSet.MAX_UNIT_BUNDLE_COUNT ) { //allow time_series for [n,16]
                        result.putProperty(QDataSet.RENDER_TYPE, istpProps.get("RENDER_TYPE" ) );
                    }
                }
                for ( int j=0; j<QDataSet.MAX_RANK; j++ ) {
                    MutablePropertyDataSet depds= (MutablePropertyDataSet) result.property("DEPEND_"+j);
                    Map<String,Object> depProps= (Map<String, Object>) istpProps.get("DEPEND_"+j);
                    if ( depds!=null && depProps!=null ) {
                        maybeAddValidRange( depProps, depds );
                        depds.putProperty(QDataSet.FILL_VALUE, depProps.get(QDataSet.FILL_VALUE));
                        depds.putProperty(QDataSet.LABEL, depProps.get("FIELDNAM") );
                        depds.putProperty(QDataSet.TITLE, depProps.get("CATDESC" ) );
                    }
                }
            // apply properties.
            }

            //result.putProperty( QDataSet.METADATA, attributes );
            result.putProperty( QDataSet.METADATA_MODEL, QDataSet.VALUE_METADATA_MODEL_ISTP );

            return result;
        } catch ( Exception e ) {
            throw e;
        }


    }

    /**
     * add the valid range only if it looks like it is correct.  It must contain some of the data.
     */
    private void maybeAddValidRange( Map<String,Object> props, MutablePropertyDataSet ds ) {

        Units pu= (Units) props.get(QDataSet.UNITS);
        Units u= (Units) ds.property( QDataSet.UNITS );

        UnitsConverter uc;
        if ( pu==null || u==null ) {
            uc= UnitsConverter.IDENTITY;
        } else if ( u==Units.cdfEpoch ) {
            uc= UnitsConverter.IDENTITY;
        } else {
            uc= UnitsConverter.getConverter( pu, u );
        }

        double dmin=Double.NEGATIVE_INFINITY;
        double dmax=Double.POSITIVE_INFINITY;
        if ( ds.rank()==1 ) {
            QDataSet range= Ops.extent(ds);
            dmin= uc.convert(range.value(0));
            dmax= uc.convert(range.value(1));
        }

        Number nmin= (Number)props.get(QDataSet.VALID_MIN);
        double vmin= nmin==null ?  Double.POSITIVE_INFINITY : nmin.doubleValue();
        Number nmax= (Number)props.get(QDataSet.VALID_MAX);
        double vmax= nmax==null ?  Double.POSITIVE_INFINITY : nmax.doubleValue();

        boolean intersects= false;
        if ( dmax>vmin && dmin<vmax ) {
            intersects= true;
        }

        if ( intersects )  {
            if ( nmax!=null ) ds.putProperty(QDataSet.VALID_MAX, uc.convert(nmax) );
            if ( nmin!=null ) ds.putProperty(QDataSet.VALID_MIN, uc.convert(nmin) );
        }
    }

    /* read all the variable attributes into a Map */
    private HashMap<String, Object> readAttributes(CDF cdf, Variable var, int depth) {
        LinkedHashMap<String, Object> props = new LinkedHashMap<String, Object>();
        Pattern p = Pattern.compile("DEPEND_[0-9]");

        String[] vv= cdf.variableAttributeNames(var.getName());

        for ( int ipass=0; ipass<2; ipass++ ) { // first pass is for subtrees, second pass is for items
            for (int i = 0; i < vv.length; i++) {
                Object attrv = cdf.getAttribute( var.getName(), vv[i]);
                Entry entry = null;
                boolean isDep= p.matcher(vv[i]).matches() & depth == 0;
                if ( ipass==0 && isDep ) {
                    String name = (String) ((Vector)attrv).get(0);
                    Map<String, Object> newVal = readAttributes(cdf, cdf.getVariable(name), depth + 1);
                    newVal.put("NAME", name); // tuck it away, we'll need it later.
                    props.put(vv[i], newVal);

                } else if ( ipass==1 && !isDep ) {
                    Object val= ((Vector)attrv).get(0);
                    if ( val.getClass().isArray() && Array.getLength(val)==1 ) {
                        val= Array.get(val, 0);
                    }
                    props.put(vv[i], val);
                }
            }
        }

        return props;
    }


    /**
     * Read the variable into a QDataSet, possibly recursing to get depend variables.
     * @param cdf
     * @param svariable the name of the variable to read
     * @param constraints null or a constraint string like "[0:10000]" to read a subset of records.
     * @param reform for depend_1, we read the one and only rec, and the rank is decreased by 1.
     * @param depend if true, recurse to read variables this depends on.
     * @return
     * @throws CDFException
     * @throws ParseException
     */
    private MutablePropertyDataSet wrapDataSet(final CDF cdf, final String svariable, final String constraints, boolean reform, boolean depend) throws Exception, ParseException {
        Variable variable = cdf.getVariable(svariable);

        HashMap thisAttributes = readAttributes(cdf, variable, 0);

        long numRec = variable.getNumberOfValues();

        if (numRec == 0) {
            if (thisAttributes.containsKey("COMPONENT_0")) {
                // themis kludge that CDAWeb supports, so we support it too.  The variable has no records, but has
                // two attributes, COMPONENT_0 and COMPONENT_1.  These are two datasets that should be added to
                // get the result.  Note cdf_epoch16 fixes the shortcoming that themis was working around.
                QDataSet c0 = wrapDataSet(cdf, (String) thisAttributes.get("COMPONENT_0"), constraints, true, false);
                if (thisAttributes.containsKey("COMPONENT_1")) {
                    QDataSet c1 = wrapDataSet(cdf, (String) thisAttributes.get("COMPONENT_1"), constraints, false, false);
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
                    if ( c0.property( QDataSet.UNITS )!=null && c1.property( QDataSet.UNITS )!=null ) {
                        c0 = Ops.add(c0, c1);  //tha_l2_esa_20071101_v01.cdf?tha_peif_velocity_gseQ
                    }
                }
                return DDataSet.maybeCopy(c0);
            } else {
                throw new IllegalArgumentException("variable " + svariable + " contains no records!");
            }
        }

        long[] recs = DataSourceUtil.parseConstraint(constraints, numRec);
        boolean slice= recs[1]==-1;
        MutablePropertyDataSet result;
        if (reform) {
            //result = CdfUtil.wrapCdfHyperDataHacked(variable, 0, -1, 1); //TODO: this doesn't handle strings properly.
            result = CdfUtil.wrapCdfHyperDataHacked(cdf,variable, 0, -1, 1);
        } else {
            long recCount = (recs[1] - recs[0]) / recs[2];
            if ( slice ) {
                recCount= -1;
                recs[2]= 1;
            }
            result = CdfUtil.wrapCdfHyperDataHacked(cdf,variable, recs[0], recCount, recs[2]);
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
                    mu = SemanticOps.lookupUnits(sunits);
                }
                Units u = (Units) result.property(QDataSet.UNITS);
                if (u == null) {
                    result.putProperty(QDataSet.UNITS, mu);
                }
            }
        }

        int[] qubeDims= DataSetUtil.qubeDims(result);
        if ( depend ) {
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

                        boolean reformDep= idep > 0;
                        if ( reformDep && cdf.getVariable( (String)dep.get("NAME") ).recordVariance() ) {
                            reformDep= false;
                        }
                        MutablePropertyDataSet depDs = wrapDataSet(cdf, (String) dep.get("NAME"), idep == 0 ? constraints : null, reformDep, false);
                        
                        if ( idep>0 && reformDep==false && depDs.length()==1 && qubeDims[0]>depDs.length() ) { //bugfix https://sourceforge.net/tracker/?func=detail&aid=3058406&group_id=199733&atid=970682
                            depDs= (MutablePropertyDataSet)depDs.slice(0);
                            //depDs= Ops.reform(depDs);  // This would be more explicit, but reform doesn't handle metadata properly.
                        }
                        if ( idep==0 && depDs!=null ) { // kludge for Rockets: 40025_eepaa2_test.cdf?PA_bin
                            if ( depDs.length()!=result.length() && result.length()==1 ) {
                                continue;
                            }
                        }
                        //kludge for LANL_1991_080_H0_SOPA_ESP_19920308_V01.cdf?FPDO
                        if (depDs.rank() == 2 && depDs.length(0) == 2) {
                            MutablePropertyDataSet depDs1 = (MutablePropertyDataSet) Ops.reduceMean(depDs, 1);
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

                        if ( "Data_No".equals( dep.get("NAME") ) ) {
                            // kludge for UIowa Radio and Plasma Wave Group.
                            // They have a variable "DELTA_T" that gives time between measurements,
                            // and "Translation" gives offset for the waveform (e.g. 500KHz-525KHz)
                            // see .../po_h7_pwi_1996040423_v01.cdf
                            Variable deltaTVar= cdf.getVariable("Delta_T");
                            if ( deltaTVar!=null ) {
                                double deltaT;
                                try {
                                    deltaT = cdf.get1D(deltaTVar.getName())[0];
                                } catch (Throwable ex) {
                                    throw new RuntimeException(ex);
                                }
                                depDs= DDataSet.maybeCopy( Ops.multiply( DataSetUtil.asDataSet(deltaT), depDs ) );
                                depDs.putProperty(QDataSet.TITLE, "time offset");
                                try {
                                    Object o= cdf.getAttribute(deltaTVar.getName(),"UNITS");
                                    Units units= SemanticOps.lookupUnits( o.toString() );
                                    depDs.putProperty(QDataSet.UNITS,units );
                                } catch ( Exception e ) {
                                    depDs.putProperty(QDataSet.UNITS, Units.milliseconds );
                                }
                                Map<String,Object> fixAttr= (Map<String, Object>) attributes.get("DEPEND_" + idep);
                                fixAttr.put( "CATDESC", "time offset" );
                                fixAttr.put( "FIELDNAM", "time_offset" );
                                fixAttr.put( "LABLAXIS", "time_offset" );
                            }

                            Variable offsetVar= cdf.getVariable("Translation");
                            if ( offsetVar!=null ) {
                                MutablePropertyDataSet transDs= wrapDataSet( cdf, "Translation", constraints, reform, false );
                                if ( transDs.property(QDataSet.UNITS)==null ) {
                                    transDs.putProperty( QDataSet.UNITS, Units.kiloHertz );
                                }
                                Map <String,Object> user= new HashMap();
                                user.put( "FFT_Translation", transDs );
                                depDs.putProperty( QDataSet.USER_PROPERTIES, user );
                            }
                        }

                        result.putProperty("DEPEND_" + idep, depDs);
                    } catch (Exception e) {
                        e.printStackTrace(); // to support lanl.
                    }
                } else {
                    if (labl != null) {
                        try {
                            MutablePropertyDataSet depDs = wrapDataSet(cdf, labl, idep == 0 ? constraints : null, idep > 0, false);
                            result.putProperty("DEPEND_" + idep, depDs);
                        } catch (Exception e) {
                            e.printStackTrace(); // to support lanl.
                        }
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
    public MetadataModel getMetadataModel() {
        return new IstpMetadataModel();
    }

    @Override
    public synchronized Map<String, Object> getMetadata(ProgressMonitor mon) {
        if (attributes == null) {
            return null; // transient state
        }
        return attributes;
    }


}
