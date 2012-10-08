/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.cdf;

import java.util.logging.Level;
import org.das2.datum.Units;
import org.virbo.metatree.IstpMetadataModel;
import org.das2.util.monitor.ProgressMonitor;
import gov.nasa.gsfc.voyager.cdf.CDF;
import gov.nasa.gsfc.voyager.cdf.CDFFactory;
import gov.nasa.gsfc.voyager.cdf.Variable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.das2.datum.DatumRange;
import org.das2.datum.UnitsUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.IDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.RankZeroDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.datasource.MetadataModel;
import org.virbo.dsops.Ops;
import org.virbo.metatree.MetadataUtil;

/**
 * CDF data source based on Nand Lal's pure-Java
 * CDF reader.  CDF, or Common Data Format, is a NASA data format.
 *
 * @author jbf
 */
public class CdfJavaDataSource extends AbstractDataSource {

    protected static final String PARAM_ID = "id";
    protected static final String PARAM_INTERPMETA = "interpMeta";
    protected static final String PARAM_DODEP = "doDep";
    protected static final String PARAM_SLICE1 = "slice1";

    static final Logger logger= LoggerManager.getLogger("apdss.cdfjava");
    Map<String, Object> attributes;

    CdfJavaDataSource( URI uri ) {
        super(uri);
    }

    private static final int FILE_CACHE_SIZE_LIMIT= 2;
    protected static final LinkedHashMap<String,CDF> openFiles= new LinkedHashMap();
    protected static final Map<CDF,String> openFilesRev= new HashMap();
    protected static final Map<String,Long> openFilesFresh= new HashMap();
    protected static final Object lock= new Object();

    private static final int DS_CACHE_SIZE_LIMIT= 2;
    protected static final LinkedHashMap<String,MutablePropertyDataSet> dsCache= new LinkedHashMap();
    protected static final HashMap<String,Long> dsCacheFresh= new HashMap();
    protected static final Object dslock= new Object();

    private static void cdfCacheUnload( String fileName, boolean unloadDs ) {
        synchronized (lock) {
            CDF cdf= openFiles.remove(fileName);
            openFilesRev.remove(cdf);
            openFilesFresh.remove(fileName);
            if ( unloadDs ) {
                synchronized (dslock) {
                    List<String> unload= new ArrayList();
                    for ( String ds: dsCache.keySet() ) {
                        if ( ds.startsWith(fileName) ) {
                            unload.add(ds);
                        }
                    }
                    for ( String ds: unload ) {
                        dsCache.remove(ds);
                        dsCacheFresh.remove(ds);
                    }
                }
            }
        }
    }

    /**
     * put the dataset into the cache, probably removing the least valuable entry from the cache.
     * @param uri
     * @param result
     */
    protected static void dsCachePut( String uri, MutablePropertyDataSet result ) {
        synchronized ( dslock ) {
            dsCache.remove( uri ); // freshen by moving to front of the list.
            dsCache.put( uri, result );
            dsCacheFresh.put( uri, System.currentTimeMillis() );

            while ( dsCache.size()>DS_CACHE_SIZE_LIMIT ) {
                Entry<String,MutablePropertyDataSet> first= dsCache.entrySet().iterator().next();
                dsCache.remove(first.getKey());
                logger.log( Level.FINE, "remove {0}", first.getKey());
            }
        }
    }

    public CDF getCdfFile( String fileName ) {
        CDF cdf;
        try {
            synchronized ( lock ) {
                cdf= openFiles.get(fileName);
            }
            if ( cdf==null ) {
                synchronized (lock) {
                    File cdfFile= new File(fileName);
                    if ( !cdfFile.exists() ) throw new IllegalArgumentException("CDF file does not exist: "+fileName);
                    if ( cdfFile.length()==0 ) throw new IllegalArgumentException("CDF file length is zero: "+fileName);
                    cdf = CDFFactory.getCDF(fileName);
                    openFiles.put(fileName, cdf);
                    openFilesRev.put(cdf, fileName);
                    openFilesFresh.put(fileName,System.currentTimeMillis());
                    if ( openFiles.size()>FILE_CACHE_SIZE_LIMIT ) {
                        String oldest= openFiles.entrySet().iterator().next().getKey();
                        cdfCacheUnload(oldest,true);
                    }
                }
            } else {
                synchronized (lock) { // freshen reference.
                    long date= openFilesFresh.get(fileName);
                    if ( new File(fileName).lastModified() > date ) {
                        cdf = CDFFactory.getCDF(fileName);
                        openFiles.put(fileName, cdf);
                        openFilesRev.put(cdf, fileName);
                        openFilesFresh.put(fileName,System.currentTimeMillis());

                    } else {
                        cdfCacheUnload(fileName,false);
                        openFiles.put(fileName, cdf);
                        openFilesRev.put(cdf, fileName);
                        openFilesFresh.put(fileName,System.currentTimeMillis());
                        logger.log(Level.FINE, "using cached open CDF {0}", fileName);
                    }
                }
            }
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
        return cdf;

    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        File cdfFile;
        cdfFile = getFile(mon);
        
        String fileName = cdfFile.toString();
        
        Map map = getParams();
        
        CDF cdf= getCdfFile(fileName);
        
        String svariable = (String) map.get(PARAM_ID);
        if (svariable == null) {
            svariable = (String) map.get("arg_0");
        }
        String constraint = null;

        String interpMeta = (String) map.get(PARAM_INTERPMETA);
        if (!"no".equals(interpMeta)) {
            Variable variable;
            int i = svariable.indexOf("[");
            if (i != -1) {
                constraint = svariable.substring(i);
                svariable = svariable.substring(0, i);
            }
            variable= cdf.getVariable(svariable);
            long numRec= variable.getNumberOfValues();
            
            long[] recs= DataSourceUtil.parseConstraint( constraint, numRec );
            if ( attributes==null ) {
                attributes = readAttributes(cdf, variable, 0);
                if ( recs[2]==-1 ) {
                    attributes= MetadataUtil.sliceProperties(attributes, 0);
                }
            }
        }

        // Now call the other getDataSet...
        QDataSet result= getDataSet(mon,attributes);

        String os1= (String)map.get(PARAM_SLICE1);
        if ( os1!=null && !os1.equals("") && result.rank()>1 ) {
            int is= Integer.parseInt(os1);
            result= DataSetOps.slice1(result,is);
            this.attributes= null; // they aren't relevant now.
        }
        return result;
        
    }

    /**
     * get the dataset with the attributes.  attributes may be specified separately to support CDAWebDataSource, which uses a "master" cdf to override the specs within each file.
     * @param mon
     * @param attr1
     * @return
     * @throws Exception
     */
    public QDataSet getDataSet( ProgressMonitor mon, Map<String,Object> attr1 ) throws Exception {

        String lsurl= uri.toString();
        MutablePropertyDataSet cached=null;
        synchronized ( dslock ) {
            cached= dsCache.get(lsurl);
            if ( cached!=null ) { // this cache is only populated with DEPEND_0 vars for now.
                dsCachePut( lsurl, cached ); // freshen
            }
        }

        File cdfFile;
        cdfFile = getFile(mon);

        String fileName = cdfFile.toString();
        
        Map map = getParams();

        CDF cdf= getCdfFile(fileName);

        String svariable = (String) map.get(PARAM_ID);
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

        try {
            String interpMeta = (String) map.get(PARAM_INTERPMETA);

            MutablePropertyDataSet result;
            if ( attr1!=null && attr1.containsKey("VIRTUAL") && ( attr1.containsKey("FUNCTION") || attr1.containsKey("FUNCT") ) ) {
                List<QDataSet> attr= new ArrayList();
                String function= (String)attr1.get("FUNCTION");
                if ( function==null ) function= (String)attr1.get("FUNCT");
                if ( attr1.get("COMPONENT_0")!=null ) attr.add( wrapDataSet( cdf, (String)attr1.get("COMPONENT_0"), constraint, false, true, null, mon ) );
                if ( attr1.get("COMPONENT_1")!=null ) attr.add( wrapDataSet( cdf, (String)attr1.get("COMPONENT_1"), constraint, false, true, null, mon ) );
                if ( attr1.get("COMPONENT_2")!=null ) attr.add( wrapDataSet( cdf, (String)attr1.get("COMPONENT_2"), constraint, false, true, null, mon ) );
                if ( attr1.get("COMPONENT_3")!=null ) attr.add( wrapDataSet( cdf, (String)attr1.get("COMPONENT_3"), constraint, false, true, null, mon ) );
                if ( attr1.get("COMPONENT_4")!=null ) attr.add( wrapDataSet( cdf, (String)attr1.get("COMPONENT_4"), constraint, false, true, null, mon ) );
                try {
                    Map<String,Object> qmetadata= new IstpMetadataModel().properties(attr1);
                    result= (MutablePropertyDataSet) CdfVirtualVars.execute( qmetadata, function, attr, mon );
                } catch ( IllegalArgumentException ex ) {
                    throw new IllegalArgumentException("virtual function "+function+" not supported",ex);
                }

            } else { // typical route
                result= wrapDataSet(cdf, svariable, constraint, false, true, attr1, mon );

            }

            boolean doDep= !"no".equals( map.get(PARAM_DODEP) );
            if ( !doDep ) {
                result.putProperty( QDataSet.DEPEND_0, null );
                result.putProperty( QDataSet.DEPEND_1, null );
                result.putProperty( QDataSet.DEPEND_2, null );
                result.putProperty( QDataSet.DEPEND_3, null );
                if ( attr1!=null ) {
                    attr1.remove( "DEPEND_0" );
                    attr1.remove( "DEPEND_1" );
                    attr1.remove( "DEPEND_2" );
                    attr1.remove( "DEPEND_3" );
                }
            }

            if (!"no".equals(interpMeta)) {
                MetadataModel model = new IstpMetadataModel();

                Map<String, Object> istpProps = model.properties(attr1);
                CdfUtil.maybeAddValidRange(istpProps, result);
                result.putProperty(QDataSet.FILL_VALUE, istpProps.get(QDataSet.FILL_VALUE));
                result.putProperty(QDataSet.LABEL, istpProps.get(QDataSet.LABEL)  );
                result.putProperty(QDataSet.TITLE, istpProps.get(QDataSet.TITLE)  );
                String renderType= (String)istpProps.get(QDataSet.RENDER_TYPE);
                if ( renderType!=null && renderType.equals( "time_series" ) ) {
                    // kludge for rbsp-a_WFR-waveform_emfisis-L2_20120831_v1.2.1.cdf.  This is actually a waveform.
                    // Note Seth (RBSP/ECT Team) has a file with 64 channels.  Dan's file rbsp-a_HFR-spectra_emfisis-L2_20120831_v1.2.3.cdf has 82 channels.
                    if ( result.rank()>1 && result.length(0)>QDataSet.MAX_UNIT_BUNDLE_COUNT ) {
                        logger.log(Level.FINE, "result.length(0)>QDataSet.MAX_UNIT_BUNDLE_COUNT={0}, this cannot be treated as a time_series", QDataSet.MAX_UNIT_BUNDLE_COUNT);
                        renderType=null;
                    }
                }
                if ( renderType !=null && renderType.equals("image") ) {
                    logger.fine("renderType=image not supported in CDF files");
                    renderType= null;
                }
                result.putProperty(QDataSet.RENDER_TYPE, renderType );
                if ( result.rank()<3 ) { // POLAR_H0_CEPPAD_20010117_V-L3-1-20090811-V.cdf?FEDU is "time_series"
                    if ( result.rank()==2 && result.length()>0 && result.length(0)<QDataSet.MAX_UNIT_BUNDLE_COUNT ) { //allow time_series for [n,16]
                        String rt= (String)istpProps.get("RENDER_TYPE" );
                        if ( rt!=null ) result.putProperty(QDataSet.RENDER_TYPE, rt );
                        if ( istpProps.get("RENDER_TYPE")==null ) { //goes11_k0s_mag
                            if ( result.property("DEPEND_1")==null ) {
                                result.putProperty(QDataSet.RENDER_TYPE, "time_series" );
                            }
                        }
                    }
                }
                for ( int j=0; j<QDataSet.MAX_RANK; j++ ) {
                    MutablePropertyDataSet depds= (MutablePropertyDataSet) result.property("DEPEND_"+j);
                    Map<String,Object> depProps= (Map<String, Object>) istpProps.get("DEPEND_"+j);
                    if ( depds!=null && depProps!=null ) {
                        CdfUtil.maybeAddValidRange( depProps, depds );
                        Map<String, Object> istpProps2 = model.properties(depProps);
                        depds.putProperty(QDataSet.FILL_VALUE, istpProps2.get(QDataSet.FILL_VALUE));
                        if ( !UnitsUtil.isTimeLocation( SemanticOps.getUnits(depds) ) ) {
                            depds.putProperty(QDataSet.LABEL, istpProps2.get(QDataSet.LABEL) );
                            depds.putProperty(QDataSet.TITLE, istpProps2.get(QDataSet.TITLE) );
                        }
                    }
                }
            // apply properties.
            } else {
                QDataSet dep;
                dep= (QDataSet)result.property(QDataSet.DEPEND_0); // twins misuses DEPEND properties.
                if ( dep!=null && dep.length()!=result.length() ) result.putProperty( QDataSet.DEPEND_0, null );
                result.putProperty( QDataSet.DEPEND_1, null );
                result.putProperty( QDataSet.DEPEND_2, null );
                result.putProperty( QDataSet.DEPEND_3, null );
            }

            result.putProperty( QDataSet.METADATA, attr1 );
            result.putProperty( QDataSet.METADATA_MODEL, QDataSet.VALUE_METADATA_MODEL_ISTP );

            return result;
        } catch ( Exception e ) {
            throw e;
        }

    }

    /* read all the variable attributes into a Map */
    private synchronized HashMap<String, Object> readAttributes(CDF cdf, Variable var, int depth) {
        LinkedHashMap<String, Object> props = new LinkedHashMap<String, Object>();
        Pattern p = Pattern.compile("DEPEND_[0-9]");

        String[] vv;
        try {
            vv= cdf.variableAttributeNames(var.getName());
        } catch ( NullPointerException ex ) {
            ex.printStackTrace();
            throw ex;
        }

        // do two global attr for S/C identification
        Object gattr;
        gattr= cdf.getAttribute("Source_name");
        if ( gattr!=null && gattr.getClass().isArray() && Array.getLength(gattr)>0 ) {
            props.put( "Source_name", String.valueOf( Array.get(gattr,0) ) );
        }

        gattr= cdf.getAttribute("Descriptor");
        if ( gattr!=null && gattr.getClass().isArray() && Array.getLength(gattr)>0 ) {
            props.put( "Descriptor", String.valueOf( Array.get(gattr,0) ) );
        }

        for ( int ipass=0; ipass<2; ipass++ ) { // first pass is for subtrees, second pass is for items
            for (int i = 0; i < vv.length; i++) {
                Object attrv = cdf.getAttribute( var.getName(), vv[i]);
                Entry entry = null;
                boolean isDep= p.matcher(vv[i]).matches() & depth == 0;
                if ( ipass==0 && isDep ) {
                    String name = (String) ((Vector)attrv).get(0);
                    if ( cdf.getVariable(name)!=null ) {
                        Map<String, Object> newVal = readAttributes(cdf, cdf.getVariable(name), depth + 1);
                        newVal.put("NAME", name); // tuck it away, we'll need it later.
                        props.put(vv[i], newVal);
                    } else {
                        logger.fine( "No such variable: "+ name + " in CDF " );
                    }

                } else if ( ipass==1 && !isDep ) {
                    Object val= ((Vector)attrv).get(0);
                    if ( val==null ) {
                        continue; // v0.9 version of CDF-Java returns null in Test032_016.
                    }
                    if ( val.getClass().isArray() && Array.getLength(val)==1 ) {
                        val= Array.get(val, 0);
                    }
                    props.put(vv[i], val);
                }
            }
        }

        return props;
    }

    private MutablePropertyDataSet wrapDataSet(final CDF cdf, final String svariable, final String constraints, boolean reform, boolean depend, Map<String,Object> attr ) throws Exception, ParseException {
        return wrapDataSet( cdf, svariable, constraints, reform, depend, attr, new NullProgressMonitor() );
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
    private MutablePropertyDataSet wrapDataSet(final CDF cdf, final String svariable, final String constraints, boolean reform, boolean depend, Map<String,Object> thisAttributes, ProgressMonitor mon ) throws Exception, ParseException {
        Variable variable = cdf.getVariable(svariable);
        if ( variable==null ) {
            throw new IllegalArgumentException( "No such variable: "+svariable );
        }
        if ( thisAttributes==null ) {
            thisAttributes = readAttributes(cdf, variable, 0); //legacy, use with caution.
        }

        long numRec = variable.getNumberOfValues();

        if ( mon==null ) mon= new NullProgressMonitor();

        if (numRec == 0) {
            String funct= (String)thisAttributes.get("FUNCT");
            if (thisAttributes.containsKey("COMPONENT_0") && funct!=null && funct.startsWith("comp_themis_epoch" )) {
                // themis kludge that CDAWeb supports, so we support it too.  The variable has no records, but has
                // two attributes, COMPONENT_0 and COMPONENT_1.  These are two datasets that should be added to
                // get the result.  Note cdf_epoch16 fixes the shortcoming that themis was working around.
                QDataSet c0 = wrapDataSet(cdf, (String) thisAttributes.get("COMPONENT_0"), constraints, true, false, null );
                if ( thisAttributes.containsKey("COMPONENT_1")) {
                    QDataSet c1 = wrapDataSet(cdf, (String) thisAttributes.get("COMPONENT_1"), constraints, false, false, null );
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
            result = CdfUtil.wrapCdfHyperDataHacked(cdf,variable, 0, -1, 1, new NullProgressMonitor() );
        } else {
            long recCount = (recs[1] - recs[0]) / recs[2];
            if ( slice ) {
                recCount= -1;
                recs[2]= 1;
            }
            result = CdfUtil.wrapCdfHyperDataHacked(cdf,variable, recs[0], recCount, recs[2], mon);
            //result = CdfUtil.wrapCdfHyperData(variable, recs[0], recCount, recs[2]);
        }
        result.putProperty(QDataSet.NAME, svariable);

        final boolean doUnits = true;
        Units units=null;
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
                    units= mu;
                } else {
                    units= u;
                }
            } else {
                units= Units.dimensionless;
            }
        } else {
            // doFill must not be true for this branch.
        }

        final boolean doFill= ! UnitsUtil.isTimeLocation(units);
        if ( doFill ) {
            Object f= thisAttributes.get("FILLVAL");
            double dv= IstpMetadataModel.doubleValue( f, units, Double.NaN, IstpMetadataModel.VALUE_MIN );
            if ( !Double.isNaN(dv) ) {
                result.putProperty(QDataSet.FILL_VALUE, dv );
            }
            DatumRange vrange= IstpMetadataModel.getValidRange( thisAttributes, units );
            result.putProperty(QDataSet.VALID_MIN, vrange.min().doubleValue(units) );
            result.putProperty(QDataSet.VALID_MAX, vrange.max().doubleValue(units) );
        }
        
        int[] qubeDims= DataSetUtil.qubeDims(result);
        if ( depend ) {
            for (int idep = 0; idep < QDataSet.MAX_RANK; idep++) {
                //int sidep= slice ? (idep+1) : idep; // idep taking slice into account.
                int sidep= idep;
                Map dep = (Map) thisAttributes.get( "DEPEND_" + sidep );
                String labl = (String) thisAttributes.get("LABL_PTR_" + sidep);
                if ( labl==null ) labl= (String) thisAttributes.get("LABEL_" + sidep); // kludge for c4_cp_fgm_spin_20030102_v01.cdf?B_vec_xyz_gse__C4_CP_FGM_SPIN
                if ( dep != null && qubeDims.length<=idep ) {
                    logger.log(Level.INFO, "DEPEND_{0} found but data is lower rank", idep);
                    continue;
                }
                if (dep != null && ( qubeDims[idep]>6 || labl == null) ) {
                    try {
                        String depName= (String)dep.get("NAME");

                        Variable depVar= cdf.getVariable( depName );
                        if ( depVar==null ) {
                            logger.fine("unable to find variable \""+depName+"\" for DEPEND_"+sidep + " of "+ variable );
                            continue;
                        }

                        boolean reformDep= idep > 0;  // make a rank 2 [1,ny] into rank 1 [ny]

                        if ( reformDep && cdf.getVariable( depName ).recordVariance() ) {
                            reformDep= false;
                        }

                        MutablePropertyDataSet depDs = wrapDataSet(cdf, depName, idep == 0 ? constraints : null, reformDep, false, dep, null);

                        if ( labl!=null && depDs.rank()==1 && depDs.length()<100 ) { // Reiner has a file where DEPEND_1 is defined, but is just 0,1,2,3,...
                            boolean lanlKludge= true;
                            for ( int jj=0; jj<depDs.length(); jj++ ) {
                                if ( depDs.value(jj)!=jj ) lanlKludge=false;
                            }
                            if ( lanlKludge ) {
                                QDataSet bundleDs= wrapDataSet(cdf, labl, idep == 0 ? constraints : null, true, false, null);
                                result.putProperty( "BUNDLE_"+idep, DataSetUtil.toBundleDs(bundleDs) );
                            }
                        }

                        if ( idep>0 && reformDep==false && depDs.length()==1 && qubeDims[0]>depDs.length() ) { //bugfix https://sourceforge.net/tracker/?func=detail&aid=3058406&group_id=199733&atid=970682
                            depDs= (MutablePropertyDataSet)depDs.slice(0);
                            //depDs= Ops.reform(depDs);  // This would be more explicit, but reform doesn't handle metadata properly.
                        }
                        if ( idep==0 ) { // kludge for Rockets: 40025_eepaa2_test.cdf?PA_bin
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
                        }

                        result.putProperty("DEPEND_" + idep, depDs);
                    } catch (Exception e) {
                        e.printStackTrace(); // to support lanl.
                    }
                } else {
                    if (labl != null) {
                        try {
                            if ( cdf.getVariable(labl)==null ) {
                                logger.fine("no such variable: "+labl+" referred to by variable: "+ svariable );
                            } else {
                                MutablePropertyDataSet depDs = wrapDataSet(cdf, labl, idep == 0 ? constraints : null, idep > 0, false, null);
                                result.putProperty("DEPEND_" + idep, depDs);
                            }
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
                    logger.fine("swaphack avoids runtime error");
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
                    logger.fine("swaphack avoids runtime error");
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
    public synchronized Map<String, Object> getMetadata(ProgressMonitor mon) throws IOException {
        if (attributes == null) {
            try {
                File cdfFile;
                cdfFile = getFile(mon);
                String fileName = cdfFile.toString();
                Map map = getParams();
                if ( map.containsKey( PARAM_SLICE1 ) ) {
                    return null;
                }
                CDF cdf;
                cdf = CDFFactory.getCDF(fileName);
                String svariable = (String) map.get("id");
                if (svariable == null) {
                    svariable = (String) map.get("arg_0");
                }
                if (svariable == null) {
                    throw new IllegalArgumentException("variable not specified");
                }
                int i = svariable.indexOf("[");
                if (i != -1) {
                    //constraint = svariable.substring(i);
                    svariable = svariable.substring(0, i);
                }
                Variable variable = cdf.getVariable(svariable);
                if ( variable==null ) {
                    throw new IllegalArgumentException("No such variable \""+svariable+"\"");
                }
                attributes= readAttributes(cdf, variable, 0);
                return attributes; // transient state
            } catch ( Throwable ex ) {
                if ( ex instanceof IllegalArgumentException ) {
                    throw (IllegalArgumentException)ex;
                } else {
                    throw new IOException(ex.getMessage());
                }
            }
        }
        return attributes;
    }

    private QDataSet labelToBundleDs( QDataSet depDs ) {
        IDataSet result= IDataSet.createRank2(depDs.length(),1);

        Units u= (Units) depDs.property(QDataSet.UNITS);
        for ( int i=0; i<depDs.length(); i++ ) {
            String labl1=  u.createDatum(depDs.value()).toString();
            result.putProperty( "LABEL__"+i, labl1 );
            result.putProperty( "NAME__"+i, Ops.safeName(labl1) );
            result.putValue( i, 0, 1 );
        }
        return result;

    }

    private void newDepLogic( CDF cdf, Variable variable, String constraints, Map<String, Object> thisAttributes, int idep, MutablePropertyDataSet result ) throws Exception {

        Map<String,Object> dep= (Map<String, Object>) thisAttributes.get("DEPEND_"+idep);
        String labl = (String) thisAttributes.get("LABL_PTR_" + idep);  // DANGER--this will probably change from String to Map.
        if ( labl==null ) labl= (String) thisAttributes.get("LABEL_" + idep); // kludge for c4_cp_fgm_spin_20030102_v01.cdf?B_vec_xyz_gse__C4_CP_FGM_SPIN

        if ( dep!=null ) {
            String depName= (String)dep.get("NAME");
            boolean reformDep= idep > 0;  // make a rank 2 [1,ny] into rank 1 [ny]
            if ( reformDep && cdf.getVariable( depName ).recordVariance() ) {
                reformDep= false;
            }

            MutablePropertyDataSet depDs = wrapDataSet( cdf, depName, idep == 0 ? constraints : null, reformDep, false, dep, null );

            if ( idep>0 && reformDep==false && depDs.length()==1 && variable.getNumberOfValues()>depDs.length() ) { //bugfix https://sourceforge.net/tracker/?func=detail&aid=3058406&group_id=199733&atid=970682
                depDs= (MutablePropertyDataSet)depDs.slice(0);
                //depDs= Ops.reform(depDs);  // This would be more explicit, but reform doesn't handle metadata properly.
            }
            if ( idep==0 ) { // kludge for Rockets: 40025_eepaa2_test.cdf?PA_bin
                if ( depDs.length()!=result.length() && result.length()==1 ) {
                    return;
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
//                            if (sidep == 0) {
//                                logger.info("sorting dep0 to make depend0 monotonic");
//                                QDataSet sort = org.virbo.dataset.DataSetOps.sort(depDs);
//                                result = DataSetOps.applyIndex(result, idep, sort, false);
//                                depDs = DataSetOps.applyIndex(depDs, 0, sort, false);
//                                depDs.putProperty(QDataSet.MONOTONIC, Boolean.TRUE);
//                            }
            }


            result.putProperty("DEPEND_" + idep, depDs);

        }

        if ( labl!=null ) {
            try {
                if ( cdf.getVariable(labl)==null ) {
                    throw new IllegalArgumentException("no such variable: "+labl+" referred to by variable: "+ variable.getName() );
                }
                MutablePropertyDataSet depDs = wrapDataSet(cdf, labl, idep == 0 ? constraints : null, idep > 0, false, null);
                QDataSet bds= labelToBundleDs( depDs );
                result.putProperty("BUNDLE_" + idep, bds );
            } catch (Exception e) {
                e.printStackTrace(); // to support lanl.
            }
        }
    }
}
