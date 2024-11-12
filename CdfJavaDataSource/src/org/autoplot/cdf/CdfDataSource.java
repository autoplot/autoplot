
package org.autoplot.cdf;

import gov.nasa.gsfc.spdf.cdfj.CDFException;
import java.beans.PropertyChangeEvent;
import java.util.logging.Level;
import org.das2.datum.Units;
import org.autoplot.metatree.IstpMetadataModel;
import org.das2.util.monitor.ProgressMonitor;
import gov.nasa.gsfc.spdf.cdfj.CDFReader;
import gov.nasa.gsfc.spdf.cdfj.ReaderFactory;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.qds.buffer.BufferDataSet;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.datum.DatumRange;
import org.das2.datum.UnitsUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.autoplot.util.TickleTimer;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DDataSet;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.IDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.RankZeroDataSet;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.ReplicateDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.MetadataModel;
import org.autoplot.datasource.ReferenceCache;
import org.das2.qds.ops.Ops;
import org.autoplot.metatree.MetadataUtil;
import org.das2.qds.DataSetIterator;
import org.das2.qds.QubeDataSetIterator;
import org.das2.util.DasMath;

/**
 * CDF data source based on Nand Lal's pure-Java
 * CDF reader.  CDF, or Common Data Format, is a NASA data format.
 *
 * @author jbf
 */
public class CdfDataSource extends AbstractDataSource {

    protected static final String PARAM_DODEP = "doDep";
    protected static final String PARAM_WHERE = "where";
    protected static final String PARAM_DEPEND0 = "depend0"; // do not use.
    protected static final String PARAM_X = "X";
    protected static final String PARAM_Y = "Y";
    protected static final String PARAM_INTERPMETA = "interpMeta";
    protected static final String PARAM_ID = "id";
    protected static final String PARAM_SLICE1 = "slice1";
    protected static final String PARAM_SLICE2 = "slice2";
    protected static final String ATTR_SLICE1_LABELS= "slice1_labels";
    protected static final String ATTR_SLICE1= "slice1";
    protected static final String ATTR_SLICE2_LABELS= "slice2_labels"; // waveform dataset
    protected static final String ATTR_SLICE2= "slice2";
    
    private static final Logger logger= LoggerManager.getLogger("apdss.cdf");
    private Map<String, Object> attributes;

    public CdfDataSource( URI uri ) {
        super(uri);
    }

    private static final int FILE_CACHE_SIZE_LIMIT= 2;
    private static final LinkedHashMap<String,CDFReader> openFiles= new LinkedHashMap();
    private static final Map<CDFReader,String> openFilesRev= new HashMap();
    private static final Map<String,Long> openFilesFresh= new HashMap();
    private static final Object lock= new Object();

    private static final int DS_CACHE_SIZE_LIMIT= 2;
    private static final LinkedHashMap<String,MutablePropertyDataSet> dsCache= new LinkedHashMap();
    private static final HashMap<String,Long> dsCacheFresh= new HashMap();
    private static final Object dslock= new Object();

    private static void cdfCacheUnload( String fileName, boolean unloadDs ) {
        synchronized (lock) {logger.log(Level.FINER, "cdfCacheUnload cdf file {0} from cache: unloadDs={1}", new Object[] { fileName, unloadDs } );            
            CDFReader cdf= openFiles.remove(fileName);
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
    
    protected static void cdfCacheReset() {
        synchronized (CdfDataSource.lock) {
            CdfDataSource.openFiles.clear();
            CdfDataSource.openFilesRev.clear();
            CdfDataSource.openFilesFresh.clear();
        }
        System.gc();
    }

    protected static String cdfCacheFileForReader( CDFReader cdf ) {
        String cdfFile;
        synchronized ( CdfDataSource.lock ) {
            cdfFile= CdfDataSource.openFilesRev.get(cdf);
        }
        return cdfFile;
    }
    
    /**
     * put the dataset into the cache, probably removing the least valuable entry from the cache.
     * @param uri
     * @param ds
     */
    protected static void dsCachePut( String uri, MutablePropertyDataSet ds ) {
        synchronized ( dslock ) { logger.log(Level.FINER, "dsCachePut uri={0} ds={1}", new Object[] { uri, ds } );
            dsCache.remove( uri ); // freshen by moving to front of the list.
            dsCache.put( uri, ds );
            dsCacheFresh.put( uri, System.currentTimeMillis() );

            while ( dsCache.size()>DS_CACHE_SIZE_LIMIT ) {
                Entry<String,MutablePropertyDataSet> first= dsCache.entrySet().iterator().next();
                dsCache.remove(first.getKey());
                logger.log( Level.FINER, "remove {0}", first.getKey());
            }
        }
    }

    public static void printCacheReport() {
        synchronized ( dslock ) {
            for ( Entry<String,MutablePropertyDataSet> entry: dsCache.entrySet() ) {
                int mem= CdfUtil.jvmMemory(entry.getValue());
                System.err.println( String.format( "%9d %s %s", mem, entry.getKey(), entry.getValue() ) );
            }
        }
    }

    /**
     * To resolve bug 1002 we unload the cache after 10 seconds.  Joe at Aerospace had a problem where
     * he couldn't kill a lingering autoplot process and then couldn't get at the file because it held a reference to the
     * file.  Now we automatically unload all the cached files.  I did look at just disabling the cache, but the file is
     * open and closed three times during the load.  See http://sourceforge.net/p/autoplot/bugs/1002.
     */
    public static final TickleTimer timer= new TickleTimer( 10000, new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            logger.log(Level.FINER, "unloading CDF cache to resolve bug 1002" );
            synchronized (lock ) {
                openFiles.clear();
                openFilesRev.clear();
                openFilesFresh.clear();
            }
        }
    });
 
    /**
     * -1 means check; 0 means no; 1 means yes, do allocate outside of the JVM memory.
     */
    private static int allocateDirect= -1;
    
    /**
     * get the abstract access object to the given CDF file.  This provides read-only access to the file, and a cache
     * is used to limit the number of references managed.
     * See bug http://sourceforge.net/p/autoplot/bugs/922/
     *
     * The result returns a CDF object which contains a read-only memory-mapped byte buffer.
     * 
     * @param fileName
     * @return the CDF reference used to access the file
     */
    public static CDFReader getCdfFile( String fileName ) {
        CDFReader cdf;
        
        if ( allocateDirect==-1 ) {
            allocateDirect= BufferDataSet.shouldAllocateDirect();
        }
        
        try {
            synchronized ( lock ) {
                cdf= openFiles.get(fileName); logger.log(Level.FINER, "cdf open files cache contained: {0}", cdf);
            }
            if ( cdf==null ) {
                synchronized (lock) {
                    File cdfFile= new File(fileName);
                    if ( !cdfFile.exists() ) throw new IllegalArgumentException("CDF file does not exist: "+fileName);
                    if ( cdfFile.length()==0 ) throw new IllegalArgumentException("CDF file length is zero: "+fileName);
                    if ( cdfFile.length()<Integer.MAX_VALUE ) {
                        if ( allocateDirect==0 ) {
                            try {
                                cdf= ReaderFactory.getReader(fileName);
                            } catch ( Exception e ) {
                                try {
                                    cdf= new CDFReader(fileName);
                                } catch ( Exception e2 ) {
                                    throw e;
                                }
                            }
                        } else {
                            cdf= new CDFReader(fileName);
                        }
                    } else {
                        cdf= ReaderFactory.getReader(fileName);
                    }
                    
                    //cdf = CDFFactory.getCDF(fileName);
                    openFiles.put(fileName, cdf);
                    openFilesRev.put(cdf, fileName);
                    openFilesFresh.put(fileName,System.currentTimeMillis()); logger.log(Level.FINER, "added cdf file {0} to cache: {1}", new Object[] { fileName, cdf } );
                    if ( openFiles.size()>FILE_CACHE_SIZE_LIMIT ) {
                        String oldest= openFiles.entrySet().iterator().next().getKey();
                        cdfCacheUnload(oldest,true);
                    }
                }
            } else {
                synchronized (lock) { // freshen reference.
                    Long date= openFilesFresh.get(fileName);
                    if ( date==null || ( new File(fileName).lastModified() > date ) ) {
                        if ( allocateDirect==0 ) {
                            try {
                                cdf= ReaderFactory.getReader(fileName);
                            } catch ( Exception e ) {
                                throw e;
                            }
                        } else {
                            cdf= new CDFReader(fileName);
                        }
                        openFiles.put(fileName, cdf);
                        openFilesRev.put(cdf, fileName);
                        openFilesFresh.put(fileName,System.currentTimeMillis());

                    } else {
                        //cdfCacheUnload(fileName,false);
                        openFiles.put(fileName, cdf);
                        openFilesRev.put(cdf, fileName);
                        openFilesFresh.put(fileName,System.currentTimeMillis());
                        logger.log(Level.FINE, "using cached open CDF {0}", fileName);
                    }
                }
            }
        } catch (Exception ex) {
            logger.log( Level.SEVERE, "An exception was caught in CdfJava openFiles caching", ex );
            throw new RuntimeException(ex);
        }
        timer.tickle("unload cdf soon");
        return cdf;
 
    }    

    @Override
    public synchronized QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        boolean useReferenceCache= "true".equals( System.getProperty( ReferenceCache.PROP_ENABLE_REFERENCE_CACHE, "false" ) );

        ReferenceCache.ReferenceCacheEntry rcent=null;
        if ( useReferenceCache ) {
            rcent= ReferenceCache.getInstance().getDataSetOrLock( getURI(), mon);
            if ( !rcent.shouldILoad( Thread.currentThread() ) ) {
                QDataSet result= rcent.park( mon );
                logger.log(Level.FINE, "reference cache used to resolve {0}", new Object[] { String.valueOf(result) } );
                logger.log(Level.FINE, "ref uri {0}", new Object[] { resourceURI } );
                return result;
            } else {
                logger.log(Level.FINE, "reference cache in use, {0} is loading {1}", new Object[] { Thread.currentThread().toString(), resourceURI } );
            }
        }
        
        try {
            
            File cdfFile;
            cdfFile = getFile(mon.getSubtaskMonitor("download file"));

            logger.log(Level.FINE, "getDataSet ({0})", getURI() );

            String fileName = cdfFile.toString();

            Map map = getParams();

            //try this some time.
            //checkCdf( cdfFile );
            
            mon.setProgressMessage("open CDF file");
            CDFReader cdf= getCdfFile(fileName);
            
            logger.log(Level.FINE, "got cdf file for {0} {1}", new Object[]{fileName, cdf});

            String svariable = (String) map.get(PARAM_ID);
            if (svariable == null) {
                svariable = (String) map.get("arg_0");
            }
            String constraint = null;

            if ( svariable==null ) {
                throw new IllegalArgumentException("CDF URI needs an argument");
            }

            int i = svariable.indexOf("[");
            if (i != -1) {
                constraint = svariable.substring(i);
                int i2= constraint.indexOf(";");
                if ( i2>-1 ) {
                    constraint= constraint.substring(0,i2);
                }
                svariable = svariable.substring(0, i);
            }
            
            String[] svariables;
            i= svariable.indexOf(";");
            if (i != -1) {
                svariables= svariable.split(";");
                svariable = svariable.substring(0, i);
            } else {
                svariables= null;
            }
                
            long numRec,numRecDepend0=-1;
            try {
                Vector depend0namev= (Vector)cdf.getAttribute(svariable,"DEPEND_0");
                if ( depend0namev!=null && depend0namev.size()==1 ) {
                    String n= depend0namev.get(0).toString();
                    numRecDepend0= cdf.getNumberOfValues(n);
                } 
                numRec= cdf.getNumberOfValues(svariable);
            } catch ( CDFException ex ) {
                throw new Exception("CDFException "+ex.getMessage());
            }
            
            String interpMeta = (String) map.get(PARAM_INTERPMETA);
            if (!"no".equals(interpMeta)) {
                //Variable variable;
                
                List<String> ss= Arrays.asList( cdf.getVariableNames() );
                if ( !ss.contains(svariable) ) {
                    throw new IllegalArgumentException("No Such Variable: "+svariable);
                }

                int[] dimensions = cdf.getDimensions(svariable);


                long[] ndimensions= new long[ dimensions.length+1 ];
                ndimensions[0]= numRec;
                for ( i=0; i<dimensions.length; i++ ) ndimensions[i+1]= dimensions[i];
                
                Map<Integer,long[]> constraints= DataSourceUtil.parseConstraint( constraint, ndimensions );
                                
                long[] recs= constraints.get(0);
                
                if ( attributes==null ) {
                    getMetadata( new NullProgressMonitor() );
                    if ( svariables!=null ) {
                        attributes= new HashMap();
                    } else {
                        attributes = readAttributes(cdf, svariable, 0);
                    }
                    if ( recs[2]==-1 ) { // if slice0
                        attributes= MetadataUtil.sliceProperties(attributes, 0);
                    }
                    if ( map.get(PARAM_SLICE1)!=null ) {
                        attributes.put( PARAM_SLICE1, map.get(PARAM_SLICE1) );
                    }
                    if ( constraint!=null ) {
                        Matcher m= Pattern.compile("\\[\\:\\,(\\d+)\\]").matcher(constraint); // TODO: this should also match ds[::5,0]
                        if ( m.matches() ) {
                            attributes.put( PARAM_SLICE1, m.group(1) );
                        }
                        m= Pattern.compile("\\[\\:\\,\\:\\,(\\d+)\\]").matcher(constraint); // TODO: this should also match ds[::5,:,0]
                        if ( m.matches() ) {
                            attributes.put( PARAM_SLICE2, m.group(1) );
                        }
                    }
                    if ( map.get(PARAM_X)!=null ) {
                        Map<String,Object> xattr= readXorYAttributes( cdf, (String)map.get(PARAM_X), 0);
                        attributes.put( QDataSet.DEPEND_0, xattr );
                    }
                    if ( map.get(PARAM_Y)!=null ) {
                        Map<String,Object> yattr= readXorYAttributes( cdf, (String)map.get(PARAM_Y), 0);
                        attributes.put( "Y", yattr );  // there's no place for this, really.
                    }
                }
            }

            // Now call the other getDataSet...
            QDataSet result= getDataSet(mon,attributes);
            
            // 2127: when data has fewer records than its DEPEND_0, then these should be fill.
            if ( numRec>0 && numRec<numRecDepend0 
                    && result instanceof BufferDataSet 
                    && result.length()<numRecDepend0 
                    && constraint==null ) { //TODO: constraint could be [0:nrec] and it should do the same thing.
                BufferDataSet resultExt= ((BufferDataSet)result);
                resultExt.grow( (int)numRecDepend0 );
                int[] size= Ops.size(result);
                BufferDataSet fillRecs= BufferDataSet.create( resultExt.rank(), resultExt.getType(), (int)( numRecDepend0-numRec ), size );
                fillRecs.putProperty(QDataSet.UNITS,result.property(QDataSet.UNITS));
                Number nfill= (Number)resultExt.property(QDataSet.FILL_VALUE);
                double fill;
                if ( nfill==null ) {
                    logger.log(Level.WARNING, "required fill value is missing: {0}", svariable);
                    fill= Double.NaN;
                } else {
                    fill= ((Number)resultExt.property(QDataSet.FILL_VALUE)).doubleValue(); // TODO: float vs double will cause noise.
                }
                DataSetIterator it= new QubeDataSetIterator(fillRecs);
                while ( it.hasNext() ) {
                    it.next();
                    it.putValue(fillRecs,fill);
                }
                resultExt.append( fillRecs );
                logger.warning("assuming virtual records because data is shorter than DEPEND_0");
                result= resultExt;
            }
            
            if ( rcent!=null ) rcent.finished(result);
            return result;
            
        } catch ( Exception ex ) {
            if ( rcent!=null ) rcent.exception(ex);
            throw ex;
            
        } 
        
    }

    /**
     * get the dataset with the attributes.  attributes may be specified separately to support 
     * CDAWebDataSource, which uses a "master" cdf to override the specs within each file.
     * @param mon
     * @param attr1
     * @return
     * @throws Exception
     */
    public QDataSet getDataSet( ProgressMonitor mon, Map<String,Object> attr1 ) throws Exception {

        String lsurl= uri.toString();
        logger.entering( "CdfDataSource", "getDataSet", new Object[] { lsurl } );
        
        MutablePropertyDataSet cached;
        synchronized ( dslock ) {
            cached= dsCache.get(lsurl);
            if ( cached!=null ) { // this cache is only populated with DEPEND_0 vars for now.
                dsCachePut( lsurl, cached ); // freshen
            }
        }

        mon.started();
        
        File cdfFile;
        cdfFile = getFile(mon.getSubtaskMonitor("download file"));

        String fileName = cdfFile.toString();
        
        Map map = getParams();

        mon.setProgressMessage("open CDF file");
        CDFReader cdf= getCdfFile(fileName);

        String svariable = (String) map.get(PARAM_ID);
        if (svariable == null) {
            svariable = (String) map.get("arg_0");
        }

        if ( svariable==null ) {
            throw new IllegalArgumentException("CDF URI needs an argument");
        }
                
        MutablePropertyDataSet result= getDataSet( cdfFile, cdf, svariable, attr1, map, mon);
        
        if ( getParam("loadErrors","F").equals("F") ) {
            if ( result.property(QDataSet.DELTA_MINUS)!=null ) result.putProperty(QDataSet.DELTA_MINUS,null);
            if ( result.property(QDataSet.DELTA_PLUS)!=null ) result.putProperty(QDataSet.DELTA_PLUS,null);
            if ( result.property(QDataSet.BIN_MINUS)!=null ) result.putProperty(QDataSet.BIN_MINUS,null);
            if ( result.property(QDataSet.BIN_PLUS)!=null ) result.putProperty(QDataSet.BIN_PLUS,null);
        }
        
        if ( "T".equals(getParam("replaceLabels","F")) ) {
            maybeReplaceLabels(result);
        }
        
        result.makeImmutable(); 
                
        logger.exiting( "CdfDataSource", "getDataSet" );
        return result;
    }
    
    /**
     * get the dataset for svariable from the CDF file, implementing virtual variables if needed.  This supports for
     * svariable:
     * <ul>
     * <li> X;Y;Z where each is read and linked together
     * <li> X[0:100] where the first 100 records are read
     * <li> X[::5] where every fifth record is read
     * <li> virtual variables.
     * </ul>
     * 
     * Note this 
     * does not check for cycles, so be careful.  
     * 
     * @param cdfFile null or the name of the cdfFile, used only in logger messages.
     * @param cdf
     * @param svariable
     * @param attr1
     * @param map
     * @param mon
     * @return
     * @throws Exception 
     */
    public static MutablePropertyDataSet getDataSet( File cdfFile, CDFReader cdf, String svariable, Map<String,Object> attr1, Map map, ProgressMonitor mon ) throws Exception {
        //latitude[0:1000];longitude[0:1000];altitude[0:1000]"
        //Pattern p= Pattern.compile( "[a-z]+(\\[(0-9:)+\\])?"); TODO: how to match?
        
        String constraint = null;
        List<String> constraints= new ArrayList<>();
        
        int ibracket = svariable.indexOf("[");
        if (ibracket != -1) {
            StringBuilder newSvariableBuilder;
            constraint = svariable.substring(ibracket);
            newSvariableBuilder = new StringBuilder( svariable.substring(0, ibracket) );
            int i2= constraint.indexOf(";");
            if ( i2>-1 ) {
                constraint= constraint.substring(0,i2);
                constraints.add( constraint );
                i2= svariable.indexOf(";");
                int i3= svariable.indexOf(";",i2+1);
                while ( i3>-1 ) {
                    int i4= svariable.indexOf("[",i2+1);
                    if ( i4==-1 ) {
                        newSvariableBuilder.append(";").append(svariable.substring(i2+1,i3));
                        constraints.add( "" );
                    } else {
                        newSvariableBuilder.append(";").append(svariable.substring(i2+1,i4));
                        constraints.add( svariable.substring(i4,i3) );
                    }
                    i2= i3;
                    i3= svariable.indexOf(";",i3+1);
                }
                int i4= svariable.indexOf("[",i2+1);
                if ( i4==-1 ) {
                    newSvariableBuilder.append(";").append(svariable.substring(i2+1));
                    constraints.add( "" );
                } else {
                    newSvariableBuilder.append(";").append(svariable.substring(i2+1,i4));
                    constraints.add( svariable.substring(i4) );
                }
            }
            svariable = newSvariableBuilder.toString();
        }

        String[] svariables;

        if ( svariable.contains(";") ) {
            svariables= svariable.split(";");
        } else {
            svariables= null;
        }

        boolean doDep= !"no".equals( map.get(PARAM_DODEP) );

        MutablePropertyDataSet result;
        if ( attr1!=null && attr1.containsKey("VIRTUAL") && ( attr1.containsKey("FUNCTION") || attr1.containsKey("FUNCT") ) ) {
            if ( svariables!=null ) {
                throw new IllegalArgumentException("virtual variables not supported for multi-variable reads");
            }
            List<QDataSet> args= new ArrayList();
            String function= (String)attr1.get("FUNCTION");
            if ( function==null ) function= (String)attr1.get("FUNCT");
            if ( attr1.get("COMPONENT_0")!=null ) args.add(loadVariableAndDependents(cdf, (String)attr1.get("COMPONENT_0"), constraint, false, true, null, -1, -1, mon.getSubtaskMonitor("c0") ) );
            if ( attr1.get("COMPONENT_1")!=null ) args.add(loadVariableAndDependents(cdf, (String)attr1.get("COMPONENT_1"), constraint, false, true, null, -1, -1, mon.getSubtaskMonitor("c1") ) );
            if ( attr1.get("COMPONENT_2")!=null ) args.add(loadVariableAndDependents(cdf, (String)attr1.get("COMPONENT_2"), constraint, false, true, null, -1, -1, mon.getSubtaskMonitor("c2") ) );
            if ( attr1.get("COMPONENT_3")!=null ) args.add(loadVariableAndDependents(cdf, (String)attr1.get("COMPONENT_3"), constraint, false, true, null, -1, -1, mon.getSubtaskMonitor("c3") ) );
            if ( attr1.get("COMPONENT_4")!=null ) args.add(loadVariableAndDependents(cdf, (String)attr1.get("COMPONENT_4"), constraint, false, true, null, -1, -1, mon.getSubtaskMonitor("c4") ) );
            try {
                result= (MutablePropertyDataSet) CdfVirtualVars.execute( attr1, function, args, mon.getSubtaskMonitor("virtual variable") );
            } catch ( IllegalArgumentException ex ) {
                throw ex;
            }
            String os1= (String)map.get(PARAM_SLICE1);
            if ( os1!=null && !os1.equals("") && cdf.getDimensions(svariable).length>0 ) {
                int is= Integer.parseInt(os1);
                result= (MutablePropertyDataSet)Ops.slice1( result, is );
            }
        } else if ( svariables!=null ) { // multi-variable read to bundle.
            String os1= (String)map.get(PARAM_SLICE1);
            if ( os1!=null && !os1.equals("") && cdf.getDimensions(svariable).length>0 ) {
                throw new IllegalArgumentException("slice is not supported for multi-variable reads");
            } else {
                QDataSet result0=null;
                for ( int i=0; i<svariables.length; i++ ) {
                    String s= svariables[i];
                    if ( !cdf.existsVariable(s) ) {
                        throw new IllegalArgumentException("variable does not exist: "+s );
                    }
                    String c= constraints.isEmpty() ? null : constraints.get(i);
                    HashMap<String,Object> attrs1 = readAttributes(cdf, s, 0);
                    QDataSet result1= loadVariableAndDependents(cdf, s, c, false, doDep, attrs1, -1, -1, mon.getSubtaskMonitor("reading "+s+" from CDF file") );
                    Pattern p= Pattern.compile("\\[\\:\\,(\\d+)\\]");
                    if ( c!=null ) {
                        Matcher m= p.matcher(c);
                        QDataSet labels= (QDataSet)attrs1.get("slice1_labels");
                        if ( m.matches() && labels!=null ) {
                            int i2= Integer.parseInt(m.group(1));
                            result1= Ops.putProperty( result1, QDataSet.LABEL, labels.slice(i2).svalue() );
                        }
                    }
                    result0= Ops.bundle( result0, result1 );
                }
                result= Ops.maybeCopy(result0);
            }
            
        } else { // typical route
            
            String os1= (String)map.get(PARAM_SLICE1);
            if ( os1!=null && !os1.equals("") && cdf.getDimensions(svariable).length>0 ) {
                int is= Integer.parseInt(os1);
                result= loadVariableAndDependents(cdf, svariable, constraint, false, doDep, attr1, is, -1, mon.getSubtaskMonitor("reading "+svariable+" from CDF file") );
            } else {
                result= loadVariableAndDependents(cdf, svariable, constraint, false, doDep, attr1, -1, -1, mon.getSubtaskMonitor("reading "+svariable+" from CDF file") );
            }
        }

        if ( logger.isLoggable(Level.FINE)&& cdfFile!=null ) {
            String fileName= cdfFile.toString();
            int islash= fileName.lastIndexOf('/');
            logger.log(Level.FINE, "reading from {0}", fileName.substring(0,islash));
            logger.log(Level.FINE, "read variable {0}?{1} got {2}", new Object[] { fileName.substring(islash), svariable, String.valueOf(result) } );
        }
        
        String sx= (String)map.get(PARAM_X);
        if ( sx==null ) sx= (String)map.get("x");
        if ( sx!=null && sx.length()>0 ) {
            String constraint1;
            int k = sx.indexOf("[");
            if (k != -1) {
                constraint1 = sx.substring(k);
                sx = sx.substring(0, k);
            } else {
                constraint1 = constraint;
            }
            QDataSet parm= loadVariableAndDependents(cdf, sx, constraint1, false, false, null, -1, result.length(), new NullProgressMonitor());
            if ( parm.length()!=result.length() ) {
                throw new IllegalArgumentException("length of X doesn't match that of data, check each variable's length.");
            }
            result = (MutablePropertyDataSet) Ops.link( parm, result );
        }

        String sy= (String)map.get(PARAM_Y);
        if ( sy==null ) sy= (String)map.get("y");
        if ( sy!=null && sy.length()>0 ) {
            String constraint1;
            int k = sy.indexOf("[");
            if (k != -1) {
                constraint1 = sy.substring(k);
                sy = sy.substring(0, k);
            } else {
                constraint1 = constraint;
            }            
            QDataSet parm= loadVariableAndDependents(cdf, sy, constraint1, false, false, null, -1, result.length(), new NullProgressMonitor() );
            if ( parm.length()==1 && parm.rank()==2 && result.rank()>1 && result.length()>1 ) {
                parm= parm.slice(0); // reform rte_1731551069
            }  // rte_1731551069: check for non-time-varying data for "Y" which needs to be reformed.  With where constraint, coerce rank 1 Y to rank 2.
            result = (MutablePropertyDataSet) Ops.link( result.property(QDataSet.DEPEND_0), parm, result );
        }
        
        String w= (String)map.get( PARAM_WHERE );
        if ( w!=null && w.length()>0 ) {
            int ieq= w.indexOf(".");
            String sparm= w.substring(0,ieq);
            String constraint1;
            int k = sparm.indexOf("[");
            if (k != -1) {
                constraint1 = sparm.substring(k);
                sparm = sparm.substring(0, k);
            } else {
                constraint1 = constraint;
            }  
            QDataSet parm= loadVariableAndDependents(cdf, sparm, constraint1, false, false, null, -1, result.length(), new NullProgressMonitor());
            if ( parm.length()==1 && parm.rank()==2 && result.rank()>1 && result.length()>1 ) {
                parm= Ops.replicate( parm.slice(0), result.length() ); // reform rte_1731551069
            }
            result = doWhereFilter( w, parm, result );
        }
        
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
        
        String interpMeta = (String) map.get(PARAM_INTERPMETA);
        
        if (!"no".equals(interpMeta)) {

            if ( svariables==null ) {
                CdfUtil.doApplyAttributes(attr1, result, (String)map.get(PARAM_SLICE1), constraint);
                if ( sy!=null || sx!=null ) {
                    result.putProperty( QDataSet.RENDER_TYPE, null );
                }
            } else {
                for ( int j=0; j<svariables.length; j++ ) {
                    HashMap<String,Object> attrs1 = readAttributes(cdf, svariables[j], 0);
                    CdfUtil.doApplyAttributes(attrs1, 
                            (MutablePropertyDataSet)result.slice(j), 
                            (String)map.get(PARAM_SLICE1), 
                            constraint );
                    if ( sy!=null || sx!=null ) {
                        result.putProperty( QDataSet.RENDER_TYPE, null );
                    }
                }
            }
            
        } else {
            QDataSet dep;
            dep= (QDataSet)result.property(QDataSet.DEPEND_0); // twins misuses DEPEND properties.
            if ( dep!=null && dep.length()!=result.length() ) result.putProperty( QDataSet.DEPEND_0, null );
            result.putProperty( QDataSet.DEPEND_1, null );
            result.putProperty( QDataSet.DEPEND_2, null );
            result.putProperty( QDataSet.DEPEND_3, null );
            if ( svariables==null ) {
                result.putProperty( QDataSet.METADATA, attr1 );
                result.putProperty( QDataSet.METADATA_MODEL, QDataSet.VALUE_METADATA_MODEL_ISTP );
            } else {
                logger.info("TODO: attributes should appear in each bundled dataset, to be consistent.");
            }
        }

        synchronized ( CdfDataSource.class ) {
            if ( attr1!=null && "waveform".equals( attr1.get("DISPLAY_TYPE") ) ) {
                QDataSet dep1=   (QDataSet) result.property( QDataSet.DEPEND_1 );
                if ( dep1!=null ) {
                    Units dep1units= SemanticOps.getUnits(dep1);
                    if ( Units.ns!=dep1units ) {
                        ArrayDataSet dep1_= ArrayDataSet.copy(dep1);
                        dep1_.putProperty( QDataSet.VALID_MIN, null );
                        dep1_.putProperty( QDataSet.VALID_MAX, null );
                        dep1_.putProperty( QDataSet.FILL_VALUE, null );
                        while ( dep1_.rank()>0 ) dep1_= (ArrayDataSet) Ops.reduceMax( dep1_, 0 );
                        if ( dep1_.value()>1e6 ) {
                            logger.log(Level.WARNING, "offset units do not appear to be in {0}, using ns", dep1units);
                            ((MutablePropertyDataSet)dep1).putProperty(QDataSet.UNITS,Units.ns);
                        }
                    }
                }
            }
        }
        
        //DataSetUtil.validate( result, new ArrayList<String>() );

        if ( !mon.isFinished() ) mon.finished();  
        
        return result;

    }

    /**
     * Replace the channel label can be replaced by a constant, single value,
     * should there be one.  This is often used with the where constraint, for
     * example https://www.rbsp-ect.lanl.gov/data_pub/rbspa/hope/level3/PA/rbspa_rel03_ect-hope-PA-L3_20130601_v6.1.0.cdf?FEDU&where=Mode_Ele.eq(0)&replaceLabels=T
     * https://sourceforge.net/p/autoplot/bugs/1664/
     * @param ds 
     */
    private static void maybeReplaceLabels( MutablePropertyDataSet ds ) {
        for ( int i=1; i<5; i++ ) {
            MutablePropertyDataSet depDs= (MutablePropertyDataSet) (QDataSet) ds.property( "DEPEND_"+i );
            MutablePropertyDataSet lablDs= (MutablePropertyDataSet) (QDataSet) ds.property( "BUNDLE_"+i );            
            if ( depDs!=null && depDs.rank()==1 && lablDs!=null) { // it can be used as labels.
                ds.putProperty( "BUNDLE_"+i, null );
            }
        }
    }

    private static boolean hasVariable( CDFReader cdf, String var ) {
        List<String> names= Arrays.asList( cdf.getVariableNames() );
        return names.contains(var);
    }

    /**
     * My version of Nand's library results in a null pointer exception for some cdf files.
     * @param cdf the reader.
     * @param attr the attribute
     * @return null or the attribute value.
     */
    private static Object getAttribute( CDFReader cdf, String attr ) {
        try {
            return cdf.getAttribute(attr);
        } catch ( NullPointerException ex ) {
            return null;
        }
    }
    
    /**
     * read variable, which might have [:,i] for a slice
     * @param cdf the cdf file
     * @param var the variable name, and [:,i] if slice is expected.
     * @param depth 
     */
    private Map<String,Object> readXorYAttributes( CDFReader cdf, String var, int depth ) {
        int i= var.indexOf("[");
        String slice1=null;
        String slice2=null;
        if ( i>-1 ) {
            Matcher m= Pattern.compile("\\[\\:\\,(\\d+)\\]").matcher(var.substring(i));
            if ( m.matches() ) {
                slice1= m.group(1);
            } else {
                m= Pattern.compile("\\[\\:\\,\\:\\,(\\d+)\\]").matcher(var.substring(i));
                if ( m.matches() ) {
                    slice2= m.group(1);
                } else {
                    logger.warning("only [:,i] or [:,:,i] supported");
                }
            }
            var= var.substring(0,i);
        }
        
        HashMap<String,Object> xyAttributes = readAttributes(cdf, var, depth);
        if ( slice1!=null ) {
            String labl_ptr_1= (String)xyAttributes.get("LABL_PTR_1");
            boolean labelsAreRead= false;
            if ( labl_ptr_1!=null ){
                try {
                    MutablePropertyDataSet v= CdfUtil.loadVariable(cdf, labl_ptr_1);                    
                    xyAttributes.put( ATTR_SLICE1_LABELS,v);
                    xyAttributes.put( ATTR_SLICE1, slice1 );
                    labelsAreRead= true;
                } catch (Exception ex) {
                    Logger.getLogger(CdfDataSource.class.getName()).log(Level.SEVERE, null, ex);
                }
            } 
            if ( !labelsAreRead ) {
                try {
                    int[] qube= cdf.getDimensions(var);
                    String[] labels= new String[qube[0]];
                    for ( int j=0; j<qube[0]; j++ ) {
                        labels[j]= "ch_"+j;
                    }
                    xyAttributes.put( ATTR_SLICE1_LABELS, Ops.labelsDataset(labels) );
                    xyAttributes.put( ATTR_SLICE1, slice1 );
                } catch (CDFException.ReaderError ex) {
                    Logger.getLogger(CdfDataSource.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        if ( slice2!=null ) {
            String labl_ptr_2= (String)xyAttributes.get("LABL_PTR_2");
            boolean labelsAreRead= false;
            if ( labl_ptr_2!=null ){
                try {
                    MutablePropertyDataSet v= CdfUtil.loadVariable(cdf, labl_ptr_2);
                    xyAttributes.put( ATTR_SLICE2_LABELS,v);
                    xyAttributes.put( ATTR_SLICE2, slice2 );
                    labelsAreRead= true;
                } catch (Exception ex) {
                    Logger.getLogger(CdfDataSource.class.getName()).log(Level.SEVERE, null, ex);
                }
            } 
            if ( !labelsAreRead ) {
                try {
                    int[] qube= cdf.getDimensions(var);
                    String[] labels= new String[qube[0]];
                    for ( int j=0; j<qube[0]; j++ ) {
                        labels[j]= "ch_"+j;
                    }
                    xyAttributes.put( ATTR_SLICE2_LABELS, Ops.labelsDataset(labels) );
                    xyAttributes.put( ATTR_SLICE2, slice2 );
                } catch (CDFException.ReaderError ex) {
                    Logger.getLogger(CdfDataSource.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }        
        return xyAttributes;
    }
    
    /* read all the variable attributes into a Map */
    private static synchronized HashMap<String, Object> readAttributes(CDFReader cdf, String var, int depth) {
        try {
            LinkedHashMap<String, Object> props = new LinkedHashMap<>();
            LinkedHashMap<String, Object> gattrs = new LinkedHashMap<>();

            Pattern p = Pattern.compile("DEPEND_[0-9]");

            String[] vv;
            try {
                vv= cdf.variableAttributeNames(var);
            } catch ( NullPointerException ex ) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                throw ex;
            }

            // do two global attr for S/C identification
            Object gattr;
            gattr= getAttribute( cdf,"Source_name");
            if ( gattr!=null && gattr.getClass().isArray() && Array.getLength(gattr)>0 ) {
                props.put( "Source_name", String.valueOf( Array.get(gattr,0) ) );
            }

            gattr= getAttribute( cdf,"Descriptor");
            if ( gattr!=null && gattr.getClass().isArray() && Array.getLength(gattr)>0 ) {
                props.put( "Descriptor", String.valueOf( Array.get(gattr,0) ) );
            }

            for ( int ipass=0; ipass<2; ipass++ ) { // first pass is for subtrees, second pass is for items
                for (String vv1 : vv) {
                    Object attrv = cdf.getAttribute(var, vv1);
                    boolean isDep = p.matcher(vv1).matches() & depth == 0;
                    if (ipass==0 && isDep) {
                        String name = (String) ((List)attrv).get(0); //TODO: still vector?
                        if (hasVariable(cdf, name)) {
                            Map<String, Object> newVal = readAttributes(cdf, name, depth + 1);
                            newVal.put("NAME", name); // tuck it away, we'll need it later.
                            props.put(vv1, newVal);
                        } else {
                            logger.log( Level.FINE, "No such variable: {0} in CDF ", name);
                        }
                    } else if (ipass==1 && !isDep) {
                        Object val= ((List)attrv).get(0);
                        if ( val==null ) {
                            continue; // v0.9 version of CDF-Java returns null in Test032_016.
                        }
                        if ( val.getClass().isArray() && Array.getLength(val)==1 ) {
                            val= Array.get(val, 0);
                        }
                        if ( vv1.equals("VALIDMAX") || vv1.equals("VALIDMIN") || vv1.equals("FILLVAL")) {                            
                            if ( val instanceof Double && Math.abs(((Double)val))>1e20 ) {
                                val= DasMath.roundNSignificantDigits((Double)val,7);
                            }
                        }                        
                        props.put(vv1, val);
                    }
                }
            }

            if ( depth==0 ) {
                try {
                    vv= cdf.globalAttributeNames();
                } catch ( NullPointerException ex ) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                    throw ex;
                }        
                for (String vv1 : vv) {
                    try {
                        Object attr = cdf.getAttribute(vv1);
                        if (attr!=null && attr.getClass().isArray() && Array.getLength(attr)>0) {
                            int n= Array.getLength(attr);
                            if (n>1) {
                                Object[] oo= new Object[n];
                                for ( int ii=0; ii<n; ii++ ) {
                                    oo[ii]= Array.get(attr,ii);
                                }
                                gattrs.put(vv1, oo);
                            } else {
                                gattrs.put(vv1, Array.get(attr,0));
                            }
                        }
                    } catch ( RuntimeException ex ) {
                        logger.log(Level.WARNING, "Runtime exception while trying to read attribute \"{0}\": {1}", new Object[]{vv1, ex.getMessage()});
                    }
                }

                props.put( "GlobalAttributes", gattrs );
            }

            Object o=props.get("UNIT_PTR");
            if ( o!=null && o instanceof String ) {
                try {
                    Object v= CdfUtil.loadVariable(cdf,(String)o);
                    props.put( "UNIT_PTR_VALUE", v );

                } catch (Exception ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
            
            o= props.get("LABL_PTR_1");
            if ( o!=null ) {
                try {
                    Object v= CdfUtil.loadVariable(cdf,(String)o);
                    props.put( ATTR_SLICE1_LABELS, v );
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
            
            o= props.get("LABL_PTR_2");
            if ( o!=null ) {
                try {
                    Object v= CdfUtil.loadVariable(cdf,(String)o);
                    props.put( ATTR_SLICE2_LABELS, v );
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
            
            boolean[] vvs= cdf.getVarys(var);
            if ( vvs.length>0 ) {
                props.put( "_Varies", vvs[0] );
            }
            props.put( "_BlockingFactor", cdf.getBlockingFactor(var) );
            props.put( "_Type", CdfUtil.getStringDataType(cdf.getType(var)) );
            props.put( "_RecCount", cdf.getNumberOfValues(var) );
            
            return props;
        } catch ( CDFException ex ) {
            return new HashMap<>();
        }
    }

    /**
     * read the delta plus or delta minus variable, reconciling the geometry with
     * the data it modifies.  For example, rbspb_$x_ect-hope-sci-L2SA_$Y$m$d_v$(v,sep).cdf
     * has rank 2 energies [3000,72], but the delta plus is [72], so this is repeated.
     * 
     * Test with:<ul>
     * <li>vap+cdfj:file:///home/jbf/ct/hudson/data.backup/cdf/po_h0_tim_19960409_v03.cdf?Flux_H
     * <li>vap+cdaweb:ds=C3_PP_CIS&id=T_p_par__C3_PP_CIS&timerange=2005-09-07+through+2005-09-19
     * <li>http://www.rbsp-ect.lanl.gov/data_pub/rbspb/hope/level2/rbspb_$x_ect-hope-sci-L2SA_$Y$m$d_v$(v,sep).cdf?FESA&timerange=2012-11-20
     * </ul>
     * 
     * @param cdf the cdf file.
     * @param ds the result which the delta plus describes.
     * @param deltaPlus the variable name.
     * @param constraints any constraints.
     * @return rank 0 dataset or dataset with rank equal to ds.
     * @throws Exception 
     */
    private static QDataSet getDeltaPlusMinus( final CDFReader cdf, QDataSet ds, final String deltaPlus, final String constraints ) throws Exception {
        QDataSet delta= loadVariableAndDependents(cdf, (String)deltaPlus, constraints, false, false, null, -1, ds.length(), new NullProgressMonitor() ); //TODO: slice1
        if ( delta.rank()>0 && delta.length()==1 && ( delta.length()!=ds.length() || ds.length()==1 ) ) {
            delta= delta.slice(0); //vap+cdaweb:ds=C3_PP_CIS&id=T_p_par__C3_PP_CIS&timerange=2005-09-07+through+2005-09-19
        }
        if ( ds.rank()==2 && delta.rank()==1 && delta.length()==ds.length(0) ) { //TODO: where was this case?
            delta= Ops.replicate( delta, ds.length() );
        }               
        return delta;
    }

    /**
     * implement isFinite until Java 8 is available.  
     * @param v
     * @return true if v is finite.
     */
    private static boolean isFinite( double v ) {
        return !(Double.isInfinite(v) || Double.isNaN(v));
    }
    
    /**
     * load the CDF variable with no slicing.
     * @param cdf
     * @param svariable
     * @param constraints
     * @param reform
     * @return
     * @throws Exception
     * @throws ParseException 
     */
    private static MutablePropertyDataSet loadVariableAndDependents(final CDFReader cdf, final String svariable, final String constraints, boolean reform) throws Exception, ParseException {
        return loadVariableAndDependents(cdf, svariable, constraints, reform, false, null, -1, -1, new NullProgressMonitor() );
    }

    /**
     * test to see if any other variables depend on this variable with DEPEND_0.
     * @param cdf the cdf
     * @param svariable the variable (e.g. Epoch)
     * @return true if another variable uses svariable as DEPEND_0.
     */
    private static boolean someonesDepend0( final CDFReader cdf, String svariable ) throws CDFException.ReaderError {
        cdf.variableAttributeNames(svariable);
        String[] ss= cdf.getVariableNames();
        for ( String s:  ss ) {
            Object o= cdf.getAttribute(s,"DEPEND_0");
            if ( o!=null ) {
                if ( o instanceof Vector ) { // TODO: library should be updated
                    if ( ((Vector)o).size()==1 ) {
                        String depend0value= String.valueOf( ((Vector)o).get(0) );
                        if ( depend0value.equals(svariable) ) {
                            logger.log(Level.FINER, "some CDF variable ({0}) uses variable as DEPEND_0: {1}", new Object[]{s, svariable});
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * tests to see if this variable has a DEPEND_0, and if so is the DEPEND_0 also 1 record; or if another
     * variable has a DEPEND_0 which is this, then does it have just 1 record.
     * @param cdf
     * @param svariable
     * @return 
     */
    private static boolean reformTest( final CDFReader cdf, final String svariable, Map<String,Object> thisAttributes ) throws CDFException.ReaderError {
        boolean result= true;
        if ( thisAttributes.containsKey("DEPEND_0") ) {
            Object o=  thisAttributes.get("DEPEND_0");
            if ( o!=null ) {
                Object mapo= thisAttributes.get("DEPEND_0");
                if ( mapo instanceof Map ) {
                    Map<String,Object> so= (Map)mapo;
                    String dep0= (String) so.get("NAME");
                    int numDep0= cdf.getNumberOfValues(dep0);
                    if ( numDep0==1 ) {
                        result= false;
                    }
                } else if ( mapo instanceof String ) {
                    String dep0= (String)mapo;
                    int numDep0= cdf.getNumberOfValues(dep0);
                    if ( numDep0==1 ) {
                        result= false;
                    }
                }
            } else {
                String[] dependents= cdf.getDependent(svariable);
                int numDep0= cdf.getNumberOfValues(dependents[0]);
                if ( numDep0==1 ) {
                    result= false;
                }
            }
        } else {
            if ( someonesDepend0(cdf, svariable) ) {
                result= false;
            }
        }
        logger.log(Level.FINE, "reformTest for {0}: {1}", new Object[]{svariable, result});
        return result;
    }
    
    /**
     * Read the variable into a QDataSet, possibly recursing to get depend variables.
     *
     * @param cdf the CDFReader
     * @param svariable the name of the variable to read
     * @param constraints null or a constraint string like "[0:10000]" to read a subset of records.
     * @param reform for depend_1, we read the one and only rec, and the rank is decreased by 1.
     * @param loadDependents if true, recurse to read variables this depends on.
     * @param thisAttributes the value of thisAttributes
     * @param slice1 if &gt;-1, then slice on the first dimension.  This is to support extracting components.
     * @param expectRec the value of expectRec 
     * @param mon the value of mon 
     * @throws CDFException
     * @throws ParseException
     * @return the org.das2.qds.MutablePropertyDataSet
     */
    private static synchronized MutablePropertyDataSet loadVariableAndDependents(
        final CDFReader cdf, final String svariable, final String constraints, boolean reform, boolean loadDependents, 
        Map<String,Object> thisAttributes, int slice1, int expectRec, ProgressMonitor mon) throws Exception, ParseException {
        
        logger.log(Level.FINE, "loadVariableAndDependents {0} constraints={1} dependVar={2} slice1={3} reform={4}", new Object[] { svariable, constraints, loadDependents, slice1, reform } );
        if ( !hasVariable(cdf, svariable) ) {
            throw new IllegalArgumentException( "No such variable: "+svariable );
        }
        if ( thisAttributes==null ) {
            thisAttributes = readAttributes(cdf, svariable, 0); //legacy, use with caution.
        }

        long numRec = cdf.getNumberOfValues(svariable);

        if ( mon==null ) mon= new NullProgressMonitor();

        String displayType= (String)thisAttributes.get("DISPLAY_TYPE");

        if (numRec == 0) {
            String funct= (String)thisAttributes.get("FUNCTION");
            if ( funct==null ) funct= (String)thisAttributes.get("FUNCT");
            if (thisAttributes.containsKey("COMPONENT_0") && funct!=null ) {
                if ( funct.startsWith("comp_themis_epoch" ) ) {
                    // themis kludge that CDAWeb supports, so we support it too.  The variable has no records, but has
                    // two attributes, COMPONENT_0 and COMPONENT_1.  These are two datasets that should be added to
                    // get the result.  Note cdf_epoch16 fixes the shortcoming that themis was working around.
                    QDataSet c0 = loadVariableAndDependents(cdf, (String) thisAttributes.get("COMPONENT_0"), constraints, true);
                    if ( thisAttributes.containsKey("COMPONENT_1")) {
                        QDataSet c1 = loadVariableAndDependents(cdf, (String) thisAttributes.get("COMPONENT_1"), constraints, false);
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
                    return getDataSet( null, cdf, svariable, thisAttributes, new HashMap<>(), mon.getSubtaskMonitor("read virtual var: "+svariable));
                    //return getDataSet( )

                }
            } else {
                // bug 1211: cdf virtual variable cannot be accessed by single record 
                throw new NoDataInIntervalException("variable " + svariable + " contains no records!");
            }
        }

        int[] dimensions = CdfUtil.getDimensions( cdf, svariable );
        long[] ndimensions= new long[ dimensions.length+1 ];
        ndimensions[0]= numRec;
        for ( int i=0; i<dimensions.length; i++ ) ndimensions[i+1]= dimensions[i];
        
        Map<Integer,long[]> mc= DataSourceUtil.parseConstraint( constraints, ndimensions );
        
        if ( mc.size()>1 ) {
            long[] slice1s= mc.get(1);
            if ( slice1s!=null && ( slice1s[0]!=-1 && slice1s[1]==-1 && slice1s[2]==-1 ) ) {
                slice1= (int)(slice1s[0]);
                if ( reform ) {
                    logger.fine("clearing reform flag because of slice1");
                    reform= false;
                }
            }
        }        
        
        long[] recs = mc.get(0);
        
        if ( numRec==1 ) {//mms1_fpi_brst_l2_dis-dist_20160111063934_v3.1.0.cdf?mms1_dis_dist_brst[100:200]
            boolean [] varies= cdf.getVarys(svariable);
            if ( CdfUtil.getEffectiveRank(varies)==cdf.getNumberOfElements(svariable) ) {
                recs[0]= 0;                
            }
            if ( CdfUtil.getEffectiveRank(varies)==0  ) {
                recs[0]= 0;
            }
        }
        
        boolean slice= recs[1]==-1;
        MutablePropertyDataSet result;

        if ( cdf.getDimensions(svariable).length>0 && slice1>-1 ) {
            int n1= cdf.getDimensions(svariable)[0];
            if ( cdf.getType(svariable)==CDFConstants.CDF_EPOCH16 ) {
                logger.fine("CDF_EPOCH16, ew...");
            } else {
                if ( slice1>=n1 ) {
                    throw new IllegalArgumentException("slice1="+slice1+" is too big for the dimension size ("+n1+")");
                }
            }
        }

        long recCount = (recs[1] - recs[0]) / recs[2];
        if ( !reform ) { // bug 2007
            if ( recs[1]==1 && ndimensions[ndimensions.length-1]>1 && ndimensions.length>0 && ndimensions[0]==1 ) {
                if ( reformTest( cdf, svariable, thisAttributes ) ) {
                    logger.fine("variable is not marked as non-time-varying, but the single record implies it should be.");
                    reform= true;
                }
            }
        }
        
        if (reform) {
            //result = CdfUtil.wrapCdfHyperDataHacked(variable, 0, -1, 1); //TODO: this doesn't handle strings properly.    
            result = CdfUtil.loadVariable(cdf,svariable, 0, -1, 1, slice1, new NullProgressMonitor() );
        } else {
            
            if ( slice ) {
                recCount= -1;
                recs[2]= 1;
            }
            result = CdfUtil.loadVariable(cdf,svariable, recs[0], recCount, recs[2], slice1, mon);
            //result = CdfUtil.wrapCdfHyperData(variable, recs[0], recCount, recs[2]);
        }
        
        if ( slice1>-1 ) {
            result.putProperty(QDataSet.NAME, svariable+"__"+slice1 );
            QDataSet ll= (QDataSet)thisAttributes.get("slice1_labels");
            if (ll!=null ) {
                result.putProperty(QDataSet.LABEL,ll.slice(slice1).svalue());
            }
            // note this will be replaced in caller code.
        } else {
            result.putProperty(QDataSet.NAME, svariable);
        }

        final boolean doUnits = true;
        Units units=null;
        if (doUnits) {
            if (thisAttributes.containsKey("UNITS")) {
                String sunits= (String) thisAttributes.get("UNITS");
                Units mu;
                if ( sunits.equalsIgnoreCase("row number") || sunits.equalsIgnoreCase("column number" ) ) { // kludge for POLAR/VIS
                    mu= Units.dimensionless;
                } else {
                    mu = Units.lookupUnits(sunits);
                }
                Units u = (Units) result.property(QDataSet.UNITS);
                if (u == null) {
                    result.putProperty(QDataSet.UNITS, mu);
                    units= mu;
                } else {
                    units= u;
                }
            } else if ( thisAttributes.containsKey("UNIT_PTR") ) {
                String svar= (String) thisAttributes.get("UNIT_PTR");
                if ( svar!=null ) {
                    logger.log(Level.FINER, "found UNIT_PTR for {0}", svariable);
                    boolean okay= true;
                    QDataSet s=null;
                    try {
                        if ( hasVariable(cdf, svar) ) {
                            s= CdfUtil.loadVariable(cdf, svar, 0, 1, 1, -1, new NullProgressMonitor() );
                            s= s.slice(0);
                            double s1= s.value(0);
                            for ( int i=1; i<s.length(); i++ ) {
                                if ( s.value(i)!=s1 ) {
                                    logger.log( Level.INFO, "units are not all the same, unable to use: {0}", svar );
                                    okay= false;
                                }
                            }
                        } else {
                            logger.log( Level.INFO, "units variable does not exist: {0}", svar);
                            okay= false;
                        }
                    } catch ( Exception ex ) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                        okay= false;
                    }
                    if ( okay ) {
                        assert s!=null;
                        units= Units.lookupUnits( DataSetUtil.getStringValue( s, s.value(0) ) );
                        result.putProperty(QDataSet.UNITS, units);
                    } else {
                        units= SemanticOps.getUnits(result); // do what we did before...
                    }
                } else {
                    units= SemanticOps.getUnits(result); // do what we did before...
                }
            } else {
                units= SemanticOps.getUnits(result); // someone has already figured out TimeLocationUnits...
            }
        } else {
            // doFill must not be true for this branch.
        }

        Object f= thisAttributes.get("FILLVAL");
        double dv= IstpMetadataModel.doubleValue( f, units, Double.NaN, IstpMetadataModel.VALUE_MIN );
        if ( !Double.isNaN(dv) ) {
            result.putProperty(QDataSet.FILL_VALUE, dv );
        }
        DatumRange vrange= IstpMetadataModel.getValidRange( thisAttributes, units );
        if ( vrange!=null ) {
            if ( vrange.width().value()<=0 ) {
                logger.fine("ignoring VALID_MIN and VALID_MAX because they are equal or out of order.");
            } else {
                QDataSet extentds= Ops.extentSimple( result,null );
                if ( isFinite( extentds.value(0) ) ) {
                    DatumRange extent= DataSetUtil.asDatumRange( extentds );
                    if ( loadDependents || extent.intersects(vrange) ) { // if this data depends on other independent data, or intersects the valid range.
                        // typical route
                        if ( UnitsUtil.isTimeLocation( vrange.getUnits() ) ) {
                            if ( extent.intersects(vrange) ) {
                                result.putProperty(QDataSet.VALID_MIN, vrange.min().doubleValue(units) );
                                result.putProperty(QDataSet.VALID_MAX, vrange.max().doubleValue(units) );
                            }
                        } else {
                            result.putProperty(QDataSet.VALID_MIN, vrange.min().doubleValue(units) );
                            result.putProperty(QDataSet.VALID_MAX, vrange.max().doubleValue(units) );                    
                        }
                    } else {
                        logger.fine("ignoring VALID_MIN and VALID_MAX because no timetags would be considered valid.");
                    }
                } else {
                    logger.fine("using VALID_MIN and VALID_MAX to indictate that all data is invalid.");
                    result.putProperty(QDataSet.VALID_MIN, vrange.min().doubleValue(units) );
                    result.putProperty(QDataSet.VALID_MAX, vrange.max().doubleValue(units) );          
                }
            }
        }

        if ( slice && loadDependents ) {
            Map dep0map= (Map) thisAttributes.get( "DEPEND_0" );
            if ( dep0map!=null ) {
                QDataSet dep0= loadVariableAndDependents(cdf, (String) dep0map.get("NAME"), constraints, false, 
                    false, null, -1, result.length(), new NullProgressMonitor());
                result.putProperty( QDataSet.CONTEXT_0, dep0 );
            }
        }

        // CDF uses DELTA_PLUS and DELTA_MINUS on a dependency to represent the BIN boundaries.
        // vap+cdfj:file:///home/jbf/ct/hudson/data.backup/cdf/po_h0_tim_19960409_v03.cdf?Flux_H has units error.
        boolean doPlusMinus= loadDependents==false;
        Object deltaPlus= thisAttributes.get( "DELTA_PLUS_VAR" );
        Object deltaMinus= thisAttributes.get( "DELTA_MINUS_VAR" );
        if ( doPlusMinus 
                && ( deltaPlus!=null && deltaPlus instanceof String && !deltaPlus.equals(svariable) ) 
                && (  deltaMinus!=null && deltaMinus instanceof String ) && !deltaPlus.equals(svariable) ) {
            if ( hasVariable( cdf, (String)deltaPlus ) ) {
                QDataSet delta; 
                try {
                    delta = getDeltaPlusMinus( cdf, result, (String)deltaPlus, constraints );//TODO: slice1
                } catch ( NoDataInIntervalException ex ) {  // file:///home/jbf/autoplot/data/u/jonn/20180615/psp_isois-epilo_l2-ic_20100104_v0.0.0.cdf
                    logger.log(Level.FINE, "DELTA_PLUS_VAR variable has no records {0}: {1}", new Object[] { svariable, deltaPlus } );                    
                    delta= null;
                }
                if ( delta!=null ) {
                    String plusAttr= loadDependents==false ? QDataSet.BIN_PLUS : QDataSet.DELTA_PLUS;
                    String minusAttr= loadDependents==false ? QDataSet.BIN_MINUS : QDataSet.DELTA_MINUS;
                    Units deltaUnits= SemanticOps.getUnits(delta);
                    if ( UnitsUtil.isRatioMeasurement(deltaUnits)
                            && deltaUnits.isConvertibleTo( SemanticOps.getUnits(result).getOffsetUnits() )
                            && ( delta.rank()==0 || result.length()==delta.length() ) ) {
                        result.putProperty( plusAttr, delta );
                        if ( !deltaMinus.equals(deltaPlus) ) {
                            delta= getDeltaPlusMinus( cdf, result, (String)deltaMinus, constraints );
                            if ( delta.length()==1 && delta.rank()==1 && delta.length()!=result.length() ) {
                               delta= delta.slice(0); //vap+cdaweb:ds=C3_PP_CIS&id=T_p_par__C3_PP_CIS&timerange=2005-09-07+through+2005-09-19
                            }
                        }
                        if ( SemanticOps.getUnits(delta).isConvertibleTo( SemanticOps.getUnits(result).getOffsetUnits() ) ) {
                            result.putProperty( minusAttr, delta );
                        } else {
                            result.putProperty( plusAttr, null );
                            logger.log(Level.FINE, "DELTA_MINUS_VAR units are not convertible: {0}", SemanticOps.getUnits(delta));
                        }
                    } else {
                        if ( !UnitsUtil.isRatioMeasurement(deltaUnits) ) {
                            logger.log(Level.FINE, "DELTA_PLUS_VAR units are not ratio measurements having a meaningful zero: {0}", new Object[] { deltaUnits } );
                        } else if ( result.length()!=delta.length() ) {
                            logger.log(Level.FINE, "DELTA_PLUS_VAR length ({0,number,#})!= data length ({1,number,#})", new Object[] { delta.length(), result.length() } );
                        } else {
                            logger.log(Level.FINE, "DELTA_PLUS_VAR units are not convertible: {0}", SemanticOps.getUnits(delta));
                        }
                    }
                }
            } else {
                logger.log(Level.FINE, "DELTA_PLUS_VAR variable is not found for {0}: {1}", new Object[] { svariable, deltaPlus } );                    
            }
        }


        int[] qubeDims= DataSetUtil.qubeDims(result);
        if ( loadDependents ) {
            for (int idep = 0; idep <= dimensions.length; idep++) {
                int sidep= idep; // Note slice1 will be implemented towards the end of this loop.
                Map depAttr = (Map) thisAttributes.get( "DEPEND_" + sidep );
                // sometime LABL_PTR_1 is a QDataSet, sometimes it's a string.  Thanks VATesting for catching this.
                Object oo= thisAttributes.get("LABL_PTR_" + sidep);
                MutablePropertyDataSet lablDs=null;
                String labl=null;
                if ( oo instanceof MutablePropertyDataSet ) {
                    labl= (String) ( (MutablePropertyDataSet) oo).property( QDataSet.NAME) ;
                } else if ( oo instanceof String ) {
                    if ( hasVariable(cdf,(String)oo) ) {
                        labl= (String)oo;
                    } else {
                        logger.log( Level.FINE, "LABL_PTR_{0} pointed to non-existant variable {1}", new Object[]{sidep, oo});
                    }
                }
                if ( labl==null ) labl= (String) thisAttributes.get("LABEL_" + sidep); // kludge for c4_cp_fgm_spin_20030102_v01.cdf?B_vec_xyz_gse__C4_CP_FGM_SPIN

                if ( labl!=null ) {
                    try {
                        lablDs= loadVariableAndDependents(cdf, labl, constraints, idep > 0);
                        //if ( idep==1 && attributes!=null ) attributes.put( "LABL_PTR_1", lablDs );
                    } catch ( Exception ex ) {
                        logger.log(Level.FINE, "unable to load LABL_PTR_"+sidep+" for "+svariable, ex );
                        thisAttributes.remove("LABL_PTR_" + sidep);
                    }
                    if ( lablDs!=null && lablDs.length()<4 && displayType==null ) {
                        logger.log(Level.FINER, "setting null displayType to time_series" );
                        displayType= "time_series";
                    }
                }

                MutablePropertyDataSet  depDs=null;

                logger.log(Level.FINER, "displayType={0}", displayType);
                if ( depAttr != null ) {

                        String depName= (String)depAttr.get("NAME");

                        if ( !hasVariable(cdf,depName) ) {
                            logger.log(Level.FINE, "unable to find variable \"{0}\" for DEPEND_{1} of {2}", new Object[]{depName, sidep, svariable});
                            continue;
                        }

                        boolean reformDep= idep > 0;  // make a rank 2 [1,ny] into rank 1 [ny]

                        if ( reformDep && cdf.recordVariance( depName ) ) {
                            reformDep= false;
                        }

                        depDs = loadVariableAndDependents(cdf, depName, constraints, reformDep, false, depAttr, -1, result.length(), null);
                        //depDs = CdfUtil.loadVariable(cdf, depName ); // EXPERIMENT--DO NOT COMMIT

                        if ( idep>0 && reformDep==false && depDs.length()==1 && ( qubeDims[0]==1 || qubeDims[0]>depDs.length() ) ) { //bugfix https://sourceforge.net/p/autoplot/bugs/471/
                            depDs= (MutablePropertyDataSet)depDs.slice(0);
                            //depDs= Ops.reform(depDs);  // This would be more explicit, but reform doesn't handle metadata properly.
                        }
                        
                        // https://cdaweb.gsfc.nasa.gov/data/erg/hep/l2/omniflux/2021/erg_hep_l2_omniflux_20210101_v03_01.cdf
                        // This file has bins-like object for DEPEND, which we can handle.
                        if ( depDs.rank()==2 && depDs.length()==2 ) { 
                            if ( qubeDims[idep]==depDs.length(0) ) {
                                depDs= Ops.maybeCopy(Ops.transpose(depDs));
                                depDs.putProperty(QDataSet.BINS_1, QDataSet.VALUE_BINS_MIN_MAX );
                            }
                        }
                        
                        if ( depDs.rank()>1 && result.rank()>2 && !cdf.recordVariance( depName ) 
                            && depDs.length()==result.length(0) && depDs.length(0)==result.length(0,0) ) {// file:///home/jbf/autoplot/data/u/jonn/20180615/psp_isois-epilo_l2-ic_20100104_v0.0.0.cdf?H_Flux_HiEnergyRes
                            depDs= Ops.replicate( depDs, result.length() );
                        }
                        
                        if ( idep==0 ) { //TODO: check for spareness property.  
                            if ( cdf.getNumberOfValues(svariable)==1 && depDs.length()>1 ) {
                                logger.fine("it looks like the variable should repeat to match DEPEND_0");
                                MutablePropertyDataSet nresult;
                                if ( result.rank()>1 ) {
                                    nresult= new ReplicateDataSet( result.slice(0), depDs.length() );
                                } else {
                                    nresult= new ReplicateDataSet( result, depDs.length() );
                                }
                                result= nresult;
                            }
                        }

                        if (DataSetUtil.isMonotonic(depDs)) {
                            depDs.putProperty(QDataSet.MONOTONIC, Boolean.TRUE);
                        } else {
                            logger.log(Level.FINE, "data has dependency which is non-monotonic: {0}", depName);
                            depDs.putProperty(QDataSet.MONOTONIC, Boolean.FALSE);
//                            if (sidep == 0) {
//                                logger.info("sorting dep0 to make depend0 monotonic");
//                                QDataSet sort = org.virbo.dataset.DataSetOps.sort(depDs);
//                                result = DataSetOps.applyIndex(result, idep, sort, false);
//                                depDs = DataSetOps.applyIndex(depDs, 0, sort, false);
//                                depDs.putProperty(QDataSet.MONOTONIC, Boolean.TRUE);
//                            }
                        }

                        if ( slice1<0 ) {
                            result.putProperty("DEPEND_" + idep, depDs);
                        } else {
                            if ( idep==1 ) {
                                if ( depDs.rank()==0 ) {
                                    result.putProperty( QDataSet.CONTEXT_0, depDs ); // rte 242122682: Hmm. I bet is always rank 0. 
                                } else {
                                    result.putProperty( QDataSet.CONTEXT_0, depDs.slice(slice1) ); 
                                }
                            } else {
                                if ( idep>1 ) {
                                    result.putProperty( "DEPEND_"+(idep-1), depDs );                    
                                } else {
                                    result.putProperty( "DEPEND_"+idep, depDs );                                                        
                                }
                            }   
                            if ( idep==0 ) thisAttributes.remove("LABL_PTR_1");
                        }

                }

                if ( lablDs!=null && ( depDs==null || depDs.rank()==2 || depDs.rank()==1 && depDs.length()<100 ) ) { // Reiner has a file where DEPEND_1 is defined, but is just 0,1,2,3,...
                    if ( depDs!=null && lablDs.rank()==1 && depDs.rank()==2 && DataSetUtil.asDatum(lablDs.slice(0)).toString().equals("channel00") ) {
                        MutablePropertyDataSet b= org.autoplot.metatree.IstpMetadataModel.maybeReduceRank2( depDs );
                        if ( b!=null ) {
                            lablDs= b;
                        }
                    }

                    // kludge for Seth's file file:///home/jbf/ct/lanl/data.backup/seth/rbspa_pre_ect-mageis-L2_20121031_v1.0.0.cdf?FEDO
                        if ( depDs!=null && lablDs.rank()==1 && depDs.rank()==2 && DataSetUtil.asDatum(lablDs.slice(0)).toString().equals("channel00") ) {
                            QDataSet wds= SemanticOps.weightsDataSet(depDs);
                            int i0;
                            int l0= (wds.length(0)-1)*1/8;
                            int l1= (wds.length(0)-1)*7/8;
                            for ( i0=0; i0<depDs.length(); i0++ ) {
                                if ( wds.value(i0,l0)>0 && wds.value(i0,l1)>0 ) break;
                            }
                            if ( i0<depDs.length() ) {
                                QDataSet ex= Ops.extent( DataSetOps.slice1(depDs,0) );
                                if ( ex.value(0)==ex.value(1) ) {
                                    lablDs= (MutablePropertyDataSet)depDs.slice(i0);
                                }
                            }
                        }

                        if ( slice1<0 ) {                            
                            QDataSet bundleDs= lablDs;
                            if ( reform && result.rank()<=idep ) { // kludge/code-to-be-generalized for https://cdaweb.gsfc.nasa.gov/pub/data/wind/3dp/3dp_k0/2019/wi_k0_3dp_20190110_v01.cdf?elect_flux
                                result.putProperty( "BUNDLE_"+(idep-1), DataSetUtil.toBundleDs(bundleDs) );
                            } else {
                                result.putProperty( "BUNDLE_"+idep, DataSetUtil.toBundleDs(bundleDs) );
                            }
                        } else {
                            if ( idep==1 ) {
                                // continue
                            } else {
                                if ( idep>1 ) {
                                    QDataSet bundleDs= lablDs;
                                    result.putProperty( "BUNDLE_"+(idep-1), DataSetUtil.toBundleDs(bundleDs) );                    
                                } else {
                                    QDataSet bundleDs= lablDs;
                                    result.putProperty( "BUNDLE_"+idep, DataSetUtil.toBundleDs(bundleDs) );                                                        
                                }
                            }

                        }
                }
            }
        }
        
        if ( mc.size()==3 && result.rank()==3 ) { // we slice on the tail down here so that the bundle has been loaded.
            long[] slice2s= mc.get(2);
            if ( slice2s!=null && ( slice2s[0]!=-1 && slice2s[1]==-1 && slice2s[2]==-1 ) ) {
                int index= (int)slice2s[0];
                result= (MutablePropertyDataSet)Ops.copy( Ops.slice2( result, index ) );
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

            Object att1 = thisAttributes.get(QDataSet.DEPEND_1);
            Object att2 = thisAttributes.get(QDataSet.DEPEND_2);
            thisAttributes.put(QDataSet.DEPEND_1, att2);
            thisAttributes.put(QDataSet.DEPEND_2, att1);
        }

        if (swapHack && slice && result.rank() == 2) { // need to swap for rank 3.
            QDataSet dep0 = (QDataSet) result.property(QDataSet.DEPEND_0);
            QDataSet dep1 = (QDataSet) result.property(QDataSet.DEPEND_1);
            result.putProperty(QDataSet.DEPEND_1, dep0);
            result.putProperty(QDataSet.DEPEND_0, dep1);

            Object att0 = thisAttributes.get(QDataSet.DEPEND_0);
            Object att1 = thisAttributes.get(QDataSet.DEPEND_1);
            thisAttributes.put(QDataSet.DEPEND_0, att1);
            thisAttributes.put(QDataSet.DEPEND_1, att0);
        }

        // last check for LANL min,max file
        //kludge for LANL_1991_080_H0_SOPA_ESP_19920308_V01.cdf?FPDO  Autoplot Test016 has one of these vap:file:///home/jbf/ct/lanl/hudson/LANL_LANL-97A_H3_SOPA_20060505_V01.cdf?FEDU.
        for ( int idep=1; idep<result.rank(); idep++ ) {
            QDataSet depDs= (QDataSet) result.property("DEPEND_"+idep);
            if ( result.length()!=2 && depDs!=null && depDs.rank() == 2 && depDs.length(0) == 2 && depDs.length()==qubeDims[idep] ) {
                logger.warning("applying min,max kludge for old LANL cdf files");
                QDataSet binmax = DataSetOps.slice1(depDs, 1);
                QDataSet binmin = DataSetOps.slice1(depDs, 0);
                MutablePropertyDataSet depDs1 = 
                    DataSetOps.makePropertiesMutable( Ops.add( binmin, Ops.divide( Ops.subtract(binmax,binmin), 2 ) ) );
                depDs1.putProperty(QDataSet.DELTA_MINUS, Ops.subtract(depDs1, binmin));
                depDs1.putProperty(QDataSet.DELTA_PLUS, Ops.subtract(binmax, depDs1));
                depDs = depDs1;
                result.putProperty("DEPEND_"+idep,depDs);
            }
        }      

        return result;
    }

    /**
     * {@inheritDoc }
     * @return an IstpMetadataModel
     * @see IstpMetadataModel
     */
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
                Map<String,String> map = getParams();
                if ( map.containsKey( PARAM_SLICE1 ) ) {
                    return null;
                }
                CDFReader cdf;
                cdf = getCdfFile( fileName );
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
                i= svariable.lastIndexOf(";");
                if ( i!=-1 ) {
                    return Collections.emptyMap();
                }
                
                if ( !hasVariable(cdf,svariable) ) {
                    throw new IllegalArgumentException("No such variable \""+svariable+"\"");
                }
                attributes= readAttributes(cdf, svariable, 0);
                
                if ( map.containsKey(PARAM_X) ) {
                    String s= map.get(PARAM_X);
                    i = s.indexOf("[");
                    if (i != -1) s = s.substring(0,i);
                    Map<String,Object> dep0m= readAttributes(cdf, s, 0);
                    attributes.put( QDataSet.DEPEND_0, dep0m );
                }
                
                if ("no".equals(map.get("interpMeta") )) {
                    attributes.remove("DEPEND_0");
                    attributes.remove("DEPEND_1");
                    attributes.remove("DEPEND_2");
                    attributes.remove("DEPEND_3");
                    attributes.remove("DEPEND_4");
                }
                if ( map.containsKey(PARAM_X) ) {
                    attributes.remove("DEPEND_0");
                }
                
                if ( map.containsKey(PARAM_Y) ) {
                    attributes.remove("DEPEND_1");
                }

                return attributes; // transient state
            } catch ( IOException | IllegalArgumentException ex ) {
                if ( ex instanceof IllegalArgumentException ) {
                    throw (IllegalArgumentException)ex;
                } else {
                    ex.printStackTrace();
                    throw ex;
                }
            }
        }
        
        Map<String,Object> result= new HashMap<>();
        result.putAll( attributes );
        if ( attributes.get(PARAM_SLICE1)!=null ) {
            if ( String.valueOf( attributes.get(PARAM_SLICE1) ).length()>0 ) {
                for ( int i=1; i<QDataSet.MAX_RANK-1; i++ ) {
                    Object o= attributes.get("DEPEND_"+(i+1));
                    if ( o!=null ) {
                        result.put( "DEPEND_"+i, o );
                    }
                }
            }
        }
        return result;
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

    /**
     * check if the file really is a CDF, and throw IllegalArgumentException if it is not.
     * NetCDF files occasionally use the extension .cdf.
     * @param cdfFile a CDF file (or not)
     * @throws IllegalArgumentException when the file is not a CDF.
     * @throws IOException when the file cannot be read.
     */
    public static void checkCdf(File cdfFile) throws IOException {
        byte[] magic= new byte[4];
        if ( cdfFile.length()<4 ) {
            throw new IllegalArgumentException("CDF file is empty");
        }
        try (InputStream in = new FileInputStream(cdfFile)) {
            int n= in.read(magic);
            if ( n==4 ) {
                if ( ( magic[0] & 0xFF )==0xCD && ( magic[1] & 0xFF )==0xF3 ) {
                    logger.fine("V2.6 or newer");
                } else if ( magic[0]==67 && magic[1]==68 && magic[2]==70 ) {
                    throw new IllegalArgumentException("File appears to be NetCDF, use vap+nc:");
                } else if ( magic[1]==72 && magic[2]==68 && magic[3]==70 ) {
                    throw new IllegalArgumentException("File appears to be NetCDF (on HDF), use vap+nc:");
                } else if ( magic[0]==0 && magic[1]==0 && magic[2]==-1 && magic[3]==-1 ) {
                    logger.fine("pre-V2.6");
                } else if ( ( magic[0] & 0xFF )==0xCC && ( magic[1] & 0xFF )==0xCC && ( magic[2] & 0xFF )==0x00 && ( magic[3] & 0xFF )==0x01  ) {
                    logger.fine("compressed");
                } else {
                    // assume it's the old version of CDF that didn't have a magic number.
                }
            }
        }
    }

}
