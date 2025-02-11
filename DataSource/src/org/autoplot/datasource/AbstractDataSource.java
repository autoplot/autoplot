/*
 * AbstractDataSource.java
 *
 * Created on April 1, 2007, 7:12 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.autoplot.datasource;

import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.autoplot.datasource.DataSetURI.fromUri;
import org.das2.qds.buffer.BufferDataSet;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.capability.Updating;
import org.das2.qds.ops.CoerceUtil;
import org.das2.qds.ops.Ops;

/**
 * Base class for file-based DataSources that keeps track of the uri, makes
 * the parameters available, manages capabilities and has do-nothing
 * implementations for rarely-used methods of DataSource.
 *
 * Also this provides the filePollUpdating parameter and Updating capability.
 *
 * @author jbf
 */
public abstract class AbstractDataSource implements DataSource {

    protected static final Logger logger= Logger.getLogger("apdss");

    protected URI uri;
    /**
     * available to subclasses for convenience.  This is the name of the file,
     * without the parameters.
     */
    protected URI resourceURI;

    public AbstractDataSource(java.net.URI uri) {
        this(uri,true);
    }
    
    public AbstractDataSource(java.net.URI uri, boolean validCheck) {
        this.uri = uri;
        String s = DataSetURI.fromUri(uri);
        if ( !s.startsWith("vap") ) {
            logger.fine( "uri didn't start with vap!" );
        }
        URISplit split = URISplit.parse(s);

        if ( split.params!=null && split.params.contains("?") ) {
            if ( !"vap+inline".equals(split.vapScheme) && !"vap+jyds".equals(split.vapScheme) ) {
                logger.log(Level.WARNING, "URI contains two question marks:{0}", uri);
            }
        }
        params = URISplit.parseParams(split.params,validCheck);

        String f= split.file;
        if ( split.scheme!=null ) {
            try {
                resourceURI = DataSetURI.toUri(f);
            } catch (Exception e) {
                //URI syntax exception
                logger.fine(e.toString()); // InlineDataSource is subclass, need to fix this...
            }
        }
    }

    /**
     * returns the uri's canonical extension for convenience.
     * The extension does contain the initial period and is folded to lower case.  
     * Returns an empty string if no extension is found.
     * 
     * Note that this is not necessarily the extension associated with the DataSource.  For example,
     * ImageDataSource has a canonical extension of ".jpg", but for a png file this will return .png.
     * 
     * @param url the URL.
     * @return lower-case extension with a period, or empty string.
     */
    protected String getExt(URL url) {
        try {
            return getExt(url.toURI());
        } catch (URISyntaxException e) {
            logger.fine("Failed to convert URL to URI.");
            return "";
        }
    }

    protected String getExt(URI uri) {
        String s = uri.getPath();
        int i = s.lastIndexOf('.');
        if (i == -1) {
            return "";
        } else {
            return s.substring(i).toLowerCase();
        }
    }
    
    /**
     * available to subclasses for convenience.  
     */
    protected Map<String, String> params;

    @Override
    public abstract QDataSet getDataSet(ProgressMonitor mon) throws Exception;

    @Override
    public boolean asynchronousLoad() {
        return true;
    }

    @Override
    public String toString() {
        return DataSetURI.fromUri(uri);
    }

    @Override
    public String getURI() {
        return DataSetURI.fromUri(uri);
    }


    FilePollUpdating pollingUpdater;

    /**
     * get an input stream from the data source.
     * @param mon
     * @return
     * @throws IOException 
     */
    protected InputStream getInputStream( ProgressMonitor mon ) throws IOException {
        if ( uri.getScheme().equals("jar") ) {
            return uri.toURL().openStream(); //TODO: experiment, make this production-quality
        } else {
            return new FileInputStream( getFile(mon) );
        }
    }
    
    /**
     * make the remote file available.
     * @param mon
     * @return 
     * @throws java.io.IOException
     */
    protected File getFile(ProgressMonitor mon) throws IOException {
        if ( resourceURI==null || resourceURI.toString().equals("")  ) {
            throw new IllegalArgumentException("expected file but didn't find one, check URI for question mark");
        }
        return getFile( resourceURI, mon );
    }

    // Practically identical to the URL version below...
    protected File getFile(URI uri, ProgressMonitor mon) throws IOException {
        //DataSetURI.getFile(uri, mon);
        String suri= fromUri( uri );
        File f = DataSetURI.getFile(suri,"T".equals(params.get("allowHtml")),mon);
        if (params.containsKey("filePollUpdates")) {
            int poll= (int)(Double.parseDouble(params.get("filePollUpdates")) );
            pollingUpdater= new FilePollUpdating(uri,poll);
            pollingUpdater.startPolling();
            capabilities.put(Updating.class,pollingUpdater );
        }
        return f;
    }
    /**
     * make the remote file available.  If the parameter "filePollUpdates" is set to
     * a float, a thread will be started to monitor the local file for updates.
     * This is done by monitoring for file length and modification time changes.
     * @param url
     * @param mon
     * @return 
     * @throws java.io.IOException
     */
    protected File getFile( URL url, ProgressMonitor mon ) throws IOException {
        File f = DataSetURI.getFile( url, mon );
        if (params.containsKey("filePollUpdates")) {
            try {
                int poll= (int)(Double.parseDouble(params.get("filePollUpdates")) );
                pollingUpdater= new FilePollUpdating(url.toURI(),poll);
                pollingUpdater.startPolling();
                capabilities.put(Updating.class,pollingUpdater );
            } catch (URISyntaxException ex) {
                Logger.getLogger(AbstractDataSource.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return f;
    }

    /**
     * get the file, allowing content to be html.
     * @param url
     * @param mon
     * @return
     * @throws IOException
     * @see #getFile(java.net.URI, org.das2.util.monitor.ProgressMonitor) which checks for "allowHtml=T"
     */
    protected File getHtmlFile( URL url, ProgressMonitor mon ) throws IOException {
        File f = DataSetURI.getHtmlFile( url, mon );
        if (params.containsKey("filePollUpdates")) {
            try {
                int poll= (int)(Double.parseDouble(params.get("filePollUpdates")) );
                pollingUpdater= new FilePollUpdating(url.toURI(),poll);
                pollingUpdater.startPolling();
                capabilities.put(Updating.class,pollingUpdater );
            } catch (URISyntaxException ex) {
                Logger.getLogger(AbstractDataSource.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return f;
    }
    /**
     * return the parameters from the URI (a copy).
     * @return the parameters from the URI.
     */
    protected Map<String,String> getParams() {
        logger.log(Level.FINER, "getParams()");
        return new LinkedHashMap(params);
    }

    /**
     * return the named parameter, or the default.  
     * Note arg_0, arg_1, etc are for unnamed positional parameters.  It's recommended
     * that there be only one positional parameter.
     * @param name the parameter name
     * @param dflt the default, which is returned when the parameter is not found.
     * @return the parameter value, or dflt when the parameter is not found.
     */
    protected final String getParam( String name, String dflt ) {
        logger.log(Level.FINER, "getParam(\"{0}\")", name);
        String result= params.get(name);
        if (result!=null ) {
            return result;
        } else {
            return dflt;
        }
    }

    /**
     * abstract class version returns an empty tree.  Override this method
     * to provide metadata.
     * @param mon progress monitor
     * @return 
     * @throws java.lang.Exception
     */
    @Override
    public Map<String, Object> getMetadata(ProgressMonitor mon) throws Exception {
        return new HashMap<>();
    }

    /**
     * return a MetadataModel object that can make the metadata canonical.
     * For example, ISTPMetadataModel interprets the metadata returned from CDF files,
     * but this same model can be used with HDF files.  This returns a null model
     * that does no interpretation, and some data sources will override this.
     * @return 
     */
    @Override
    public MetadataModel getMetadataModel() {
        return MetadataModel.createNullModel();
    }

    /**
     * return metadata in canonical form using the metadata model.  If there
     * are no properties or a null model, then an empty map is returned.
     * Note, getMetadataModel should return non-null, and getMetadata should return non-null,
     * but this guards against the mistake.
     * @return
     */
    @Override
    public Map<String, Object> getProperties() {
        try {
            Map<String, Object> meta = getMetadata(new NullProgressMonitor());
            if ( meta==null || getMetadataModel()==null ) {
                logger.log( Level.FINE, "handling case where metadata or metadataModel is null: {0}, but this should be fixed.", this);
                meta= getMetadata(new NullProgressMonitor());
                //return Collections.emptyMap();
            }
            if ( meta==null || getMetadataModel()==null ) {
                return Collections.emptyMap();
            }
            return getMetadataModel().properties(meta);
        } catch (Exception e) {
            logger.log( Level.SEVERE, "exception in getProperties", e );
            return Collections.singletonMap("Exception", (Object) e);
        }
    }
    
    /**
     * 
     * @param result the dataset
     * @param parm the param used to filter.
     * @param op one of gt,lt,eq,ne,within
     * @param d rank 0 value or rank 1 range for the "within" operator.
     * @return the dataset with the filter applied.  (Note this may or may not be the same object.)
     */
    private static MutablePropertyDataSet applyFilter( 
        MutablePropertyDataSet result, QDataSet parm, String op, QDataSet d ) 
        throws NoDataInIntervalException {
        QDataSet r;
        if ( parm.rank()>1 && parm.rank()<result.rank() ) {
            QDataSet[] operands= new QDataSet[2];
            CoerceUtil.coerce( result, parm, false, operands );
            parm= operands[1];
        }
        if ( parm.rank()>1 ) {
            switch (op) {
                case "gt":
                    r= Ops.where( Ops.le( parm,d ) );
                    break;
                case "lt":
                    r= Ops.where( Ops.ge( parm,d ) );
                    break;
                case "eq":
                    r= Ops.where( Ops.ne( parm,d ) );
                    break;
                case "ne":
                    r= Ops.where( Ops.eq( parm,d ) );                        
                    break;
                case "within":
                    r= Ops.where( Ops.without( parm,d ) );
                    break;
                default:
                    throw new IllegalArgumentException("where can only contain .eq, .ne, .gt, or .lt");
            }
            double fill= Double.NaN;
            result= BufferDataSet.maybeCopy(result);
            if ( parm.rank()==2 && result.rank()==2 ) {
                for ( int jj=0; jj<r.length(); jj++ ) {
                    ((BufferDataSet)result).putValue((int)r.value(jj,0),(int)r.value(jj,1),fill);
                }
            } else if ( parm.rank()==3 && result.rank()==3 ) {
                for ( int jj=0; jj<r.length(); jj++ ) {
                    ((BufferDataSet)result).putValue((int)r.value(jj,0),(int)r.value(jj,1),(int)r.value(jj,2),fill);
                }
            } else {
                throw new IllegalArgumentException("where can only apply filter and dataset have same dimensions");  
            }
        } else if ( parm.rank()<2 ) {
            switch (op) {
                case "gt":
                    r= Ops.where( Ops.gt( parm,d ) );
                    break;
                case "lt":
                    r= Ops.where( Ops.lt( parm,d ) );
                    break;
                case "eq":
                    r= Ops.where( Ops.eq( parm,d ) );
                    break;
                case "ne":
                    r= Ops.where( Ops.ne( parm,d ) );
                    break;
                case "within":
                    r= Ops.where( Ops.within( parm,d ) );
                    break;
                default:
                    throw new IllegalArgumentException("where can only contain .eq, .ne, .gt, or .lt");
            }
            if ( r.length()==0 ) {
                throw new NoDataInIntervalException("'where' argument removes all data");
            } else {
                result= DataSetOps.applyIndex( result, 0, r, true );
                // check to see if rank 2 depend can now be rank 1.  This might be the reason we used where...
                for ( int ii=1; ii<result.rank(); ii++ ) {
                    String sdep= "DEPEND_"+ii;
                    QDataSet dep= (QDataSet) result.property(sdep);
                    if ( dep!=null && dep.rank()==2 && DataSetUtil.isConstant(dep) ) {
                        result.putProperty(sdep,dep.slice(0) );
                    }
                }
            }
        }
        return result;
    }
    
    
    /**
     * implement where constraint.  This was extracted from the CdfDataSource to support HDF5 files as well, and soon .txt files.
     * 
     * @param w where constraint, like vol.gt(10).  Note the variable name (vol) is ignored.
     * @param parm parameter to test
     * @param result the result
     * @return the result (same as result param)
     * @throws NoDataInIntervalException
     * @throws ParseException 
     */
    protected static MutablePropertyDataSet doWhereFilter( 
        String w, QDataSet parm, MutablePropertyDataSet result) 
        throws NoDataInIntervalException, ParseException {
        Pattern p= Pattern.compile("\\.([elgn][qte])\\(");
        Matcher m= p.matcher(w);
        int ieq;
        if ( !m.find() ) {
            Pattern p2= Pattern.compile("\\.within\\(");
            Matcher m2= p2.matcher(w);
            if ( !m2.find() ) {
                throw new IllegalArgumentException("where can only contain .eq, .ne, .gt, .lt, .within");
            } else {
                ieq= w.indexOf(".within(");
                String sval= w.substring(ieq+8).replaceAll("\\+"," ");
                if ( sval.endsWith(")") ) sval= sval.substring(0,sval.length()-1);
                Units du= SemanticOps.getUnits(parm);
                DatumRange dr= DatumRangeUtil.parseDatumRange(sval,du);
                result= applyFilter( result, parm, "within", DataSetUtil.asDataSet(dr) );
                
            }
            
        } else {
            ieq= m.start();
            String op= m.group(1);
            String sval= w.substring(ieq+4);
            if ( sval.endsWith(")") ) sval= sval.substring(0,sval.length()-1);
            parm= Ops.reform(parm); // TODO: Nasty kludge why did we see it in the first place vap+cdfj:file:///home/jbf/ct/hudson/data.backup/cdf/c4_cp_fgm_spin_20030102_v01.cdf?B_vec_xyz_gse__C4_CP_FGM_SPIN&where=range__C4_CP_FGM_SPIN.eq(3)
            QDataSet d;
            switch (parm.rank()) {
                case 2:
                    if ( sval.equals("mode") && ( op.equals("eq") || op.equals("ne") ) ) {
                        QDataSet hash= Ops.hashcodes(parm);
                        QDataSet mode= Ops.mode(hash);
                        d= mode;
                        parm= hash;
                    } else {
                        Units du= SemanticOps.getUnits(parm);
                        d= DataSetUtil.asDataSet( du.parse(sval) );
                    }   break;
                case 1:
                    switch (sval) {
                        case "mode":
                            QDataSet mode= Ops.mode(parm);
                            d= mode;
                            break;
                        case "median":
                            QDataSet median= Ops.median(parm);
                            d= median;
                            break;
                        case "mean":
                            QDataSet mean= Ops.mean(parm);
                            d= mean;
                            break;
                        default:
                            Units du= SemanticOps.getUnits(parm);
                            d= DataSetUtil.asDataSet(du.parse(sval));
                            break;
                    }   break;
                default:
                    throw new IllegalArgumentException("param is rank>2");
            }
            
            result= applyFilter(result, parm, op, d );
            
        }
        return result;
    }
    
    private HashMap<Class, Object> capabilities = new HashMap<>();

    /**
     * attempt to get a capability.  null will be returned if the 
     * capability doesn't exist.
     * @param <T> the capability 
     * @param clazz the capability class.
     * @return null or an implementation of a capability.
     */
    @Override
    public <T> T getCapability(Class<T> clazz) {
        return (T) capabilities.get(clazz);
    }

    /**
     * attach a capability
     * @param <T> the capability 
     * @param clazz the capability class.
     * @param o an implementation.
     * @deprecated use addCapability
     */
    public <T> void addCability(Class<T> clazz, T o) {
        capabilities.put(clazz, o);
    }
    
    /**
     * attach a capability
     * @param <T> the capability 
     * @param clazz the capability class.
     * @param o an implementation.
     */
    public <T> void addCapability( Class<T> clazz, T o) {
        capabilities.put(clazz, o);   
    }
}
