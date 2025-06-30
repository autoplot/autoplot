/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.datasource.jython;

import java.beans.ExceptionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.CancelledOperationException;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.datum.CacheTag;
import org.das2.datum.DatumRangeUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.Py;
import org.python.core.PyArray;
import org.python.core.PyDictionary;
import org.python.core.PyException;
import org.python.core.PyFloat;
import org.python.core.PyInteger;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.core.PyStringMap;
import org.python.util.PythonInterpreter;
import org.das2.qds.DataSetOps;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.LogNames;
import org.autoplot.datasource.ReferenceCache;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.Caching;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.qds.ops.Ops;
import org.autoplot.jythonsupport.JythonOps;
import org.autoplot.jythonsupport.JythonRefactory;
import org.autoplot.jythonsupport.JythonUtil;
import org.autoplot.jythonsupport.PyQDataSet;

/**
 * Use a jython script to read and process data from a number of sources.
 * Special parameters:
 * <li>  timerange  if used then TimeSeriesBrowse is added.
 * @author jbf
 */
public final class JythonDataSource extends AbstractDataSource implements Caching {

    ExceptionListener listener;
    private Map<String, Object> metadata;
    public final static String PARAM_SCRIPT= "script";
    protected final static String PARAM_TIMERANGE= "timerange";
    protected final static String PARAM_RESOURCE_URI= "resourceURI";

    private static final Logger logger= LoggerManager.getLogger( LogNames.APDSS_JYDS );
    private boolean notCheckedTsb= true;

    public JythonDataSource(URI uri, JythonDataSourceFactory factory) {
        super(uri);
        addCapability(Caching.class, this); //TODO: check for parameter inputs!
        this.listener = factory.listener;

        if ( true ) {
            try {
                File jythonScript= getScript();  // this assumes the user can go without progress feedback.
                JythonDataSourceTimeSeriesBrowse tsb1= JythonDataSourceTimeSeriesBrowse.checkForTimeSeriesBrowse( uri.toString(), jythonScript );
                if ( tsb1!=null ) {
                    tsb1.setJythonDataSource(this);
                    addCapability( TimeSeriesBrowse.class, tsb1 );
                    tsb= tsb1;
                    notCheckedTsb= false;
                }
            } catch (ParseException | IOException ex) {
                logger.severe( ex.toString() );
            }
        }
    }

    /**
     * get the name of the script, which is non-trivial since it can be in either the resourceURI or script=
     * @return
     * @throws IOException 
     */
    private File getScript() throws IOException {
        File jythonScript; // script to run.
        
        if ( params.get( PARAM_SCRIPT )!=null ) {
            // getFile( resourceURI ) //TODO: since we don't getFile(resourceURI), we can't use filePollUpdating.  Also, why do we have local variable?
            jythonScript= getFile( new URL(params.get( PARAM_SCRIPT )), new NullProgressMonitor() );
        } else {
            jythonScript= getFile(new NullProgressMonitor());
        }
        return jythonScript;
    }

    private String nextExec( LineNumberReader reader, String[] nextLine ) throws IOException {
        StringBuilder s;
        if ( nextLine[0]!=null ) {
            s= new StringBuilder(nextLine[0]);
            nextLine[0]= null;
        } else {
            String ss= reader.readLine();
            if ( ss==null ) ss="";
            s = new StringBuilder(ss);
        }
        String stest= s.toString();
        if ( ( stest.startsWith("def ") || stest.startsWith("if") || stest.startsWith("else") ) ) {
            String s1= reader.readLine();
            while ( s1!=null && ( s1.length()==0 || Character.isWhitespace(s1.charAt(0)) ) ) {
                s.append("\n").append(s1);
                s1= reader.readLine();
            }
            while ( s1!=null && s1.startsWith("else") ) {  // TODO: under implementation, use python parser for ideal solution
                s.append("\n").append(s1);
                s1= reader.readLine();
                while ( s1!=null && ( s1.length()==0 || Character.isWhitespace(s1.charAt(0)) ) ) {
                   s.append("\n").append(s1);
                    s1= reader.readLine();
                }
            }
            nextLine[0]= s1;
        }

        return s.toString();
    }

    private synchronized QDataSet getInlineDataSet(URI uri) throws Exception {

        interp = JythonUtil.createInterpreter(false);
        PyObject result= interp.eval(uri.getRawSchemeSpecificPart());

        QDataSet res;

        if (result instanceof PyList) {
            res = JythonOps.dataset((PyList) result);
        } else {
            res = (QDataSet) result.__tojava__(QDataSet.class);
        }
        return res;
    }

    @Override
    public synchronized QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        mon.started();

        File jythonScript;   // script to run.
        String lresourceURI;  // optional resource URI that is argument to script, excluding script argument.

        String suri= DataSetURI.fromUri(uri);
        if ( tsb!=null ) {
            //tsb.setURI(suri);
            suri= tsb.getURI();
        }

        URISplit split= URISplit.parse(suri);
        Map<String,String> paramsl= URISplit.parseParams(split.params); // abstract datasource params don't update.
        
        if ( split.scheme.equals("inline") ) { // note this is handled elsewhere, in InlineDataSource
            return getInlineDataSet(new URI(uri.getRawSchemeSpecificPart()));
        }
        
        boolean useReferenceCache= "true".equals(System.getProperty(org.autoplot.datasource.ReferenceCache.PROP_ENABLE_REFERENCE_CACHE, "false" ) );

        suri= URISplit.makeCanonical(suri);
        
        URISplit split1= URISplit.parse(suri);
        Map<String,String> params1= URISplit.parseParams(split1.params);
        split1.params= URISplit.formatParams(params1);
        suri= URISplit.format( split1 ); // 
        
        params1.remove("arg_0");
        split1.params= URISplit.formatParams(params1);
        String lockUri= URISplit.format(split1);
        
        ReferenceCache.ReferenceCacheEntry rcent=null;
        if ( useReferenceCache ) {
            rcent= ReferenceCache.getInstance().getDataSetOrLock( lockUri, mon);
            if ( !rcent.shouldILoad( Thread.currentThread() ) ) {
                rcent.park( mon );
                ReferenceCache.ReferenceCacheEntry entry= ReferenceCache.getInstance().getReferenceCacheEntry(suri);
                if ( entry!=null ) { 
                    QDataSet result= ReferenceCache.getInstance().getDataSet(suri); // get a strong reference before a GC
                    if ( result==null ) {
                        logger.fine("garbage collector got the data before a non-weak reference could be made");
                        logger.log(Level.FINE, "miss {0}", suri);
                        rcent= null;
                        mon= new NullProgressMonitor(); // we can't reuse monitor after finished is called.
                    } else if ( result==ReferenceCache.NULL ) {
                        return null;
                    } else {
                        return result;
                    }
                } else {
                    logger.log(Level.FINE, "referenceCache doesn''t know the URI: {0}", suri);
                    //What's to be done here?  It could be the name was wrong, so should
                    //we just assume this is an error?
                    rcent= null;  // go through as before.
                    mon= new NullProgressMonitor(); // we can't reuse monitor after finished is called.
                }
            } else {
                logger.log(Level.FINE, "reference cache in use, {0} is loading {1}", new Object[] { Thread.currentThread().toString(), resourceURI } );
            }
        }
        
        boolean allowCaching= !( "F".equals( params.get("allowCaching") ) );

        if ( !allowCaching ) interp= null;
        
        PyException causedBy = null;
        try {

            if ( params.get( PARAM_SCRIPT )!=null ) {
                jythonScript= getFile( new URL(params.get( PARAM_SCRIPT )), new NullProgressMonitor() );
                mon.setProgressMessage( "loading "+uri );
                split.params= null;
                lresourceURI= DataSetURI.fromUri( DataSetURI.getResourceURI(URISplit.format(split)) );
                
            } else {
                lresourceURI= null;
                jythonScript= getFile(new NullProgressMonitor());
            }

            if ( interp == null ) { // caching might leave the interpretter open.  This needs to be tweaked--the TSB could set interp to null for example.

                logger.log(Level.FINE, "running script {0} {1}", new Object[] { jythonScript, paramsl } );

                mon.setProgressMessage( "initialize Jython interpreter...");

                interp = JythonUtil.createInterpreter(false);

                mon.setProgressMessage( "done initializing Jython interpreter");
                try {
                    interp.set("monitor", mon);
                } catch ( ConcurrentModificationException ex ) {
                    logger.warning("avoiding strange concurrent modification bug that occurs within Jython on the server...");
                    Thread.yield();
                    interp.set("monitor", mon);
                    logger.warning("done.");
                }

                interp.set("PWD", URISplit.parse( jythonScript.toURI() ).path );
                interp.exec("import autoplot2025 as autoplot");
                interp.exec("autoplot.params=dict()");
                for ( Entry<String,String> e : paramsl.entrySet()) {
                    String s= e.getKey();
                    if (!s.equals("arg_0") && !s.equals("script") ) {
                        String sval= e.getValue();
                        if ( sval.length()>0 ) {
                            int iq= sval.indexOf('?');
                            int ie= sval.indexOf('=');
                            if ( iq>-1 && ie>-1 && iq<ie ) {
                                logger.log(Level.INFO, "double question mark detected in URI: {0}", suri);
                            }
                            sval= JythonUtil.maybeQuoteString( sval );
                            logger.log(Level.FINE, "autoplot.params[''{0}'']={1}", new Object[]{s, sval});
                            interp.exec("autoplot.params['" + s + "']=" + sval);
                        }
                    }
                }
                
                if ( lresourceURI!=null ) {
                    interp.set( PARAM_RESOURCE_URI, lresourceURI); // legacy
                    interp.exec("autoplot.params['"+PARAM_RESOURCE_URI+"']="+ JythonUtil.maybeQuoteString( lresourceURI ) );
                }

                mon.setProgressMessage( "executing script");
                
                LineNumberReader reader=null;
                try {
                    boolean debug = false;  //TODO: exceptions will have the wrong line number in this mode.
                    if (debug) {
                        try ( Reader fr = new InputStreamReader( JythonRefactory.fixImports( new FileInputStream( jythonScript ),jythonScript.getName() ) ) ) {
                            reader = new LineNumberReader( fr );
                            String[] nextLine= new String[1];
                            
                            String s = nextExec( reader, nextLine );
                            long t0= System.currentTimeMillis();
                            while (s != null) {
                                logger.log(Level.FINEST, "{0}: {1}", new Object[]{reader.getLineNumber(), s});
                                interp.exec(s);
                                logger.finest( String.format( "line=%d time=%dms  %s\n", reader.getLineNumber(), (System.currentTimeMillis()-t0), s ) );
                                if ( mon.isCancelled() ) break;
                                mon.setProgressMessage("exec line "+reader.getLineNumber() );
                                s = nextExec( reader, nextLine );
                                t0= System.currentTimeMillis();
                            }
                        }

                    } else {
                        InputStream in = new FileInputStream( jythonScript );
                        try {
                            in= JythonRefactory.fixImports(in,jythonScript.getName());
                            
                            logger.log(Level.FINE, "executing script {0}", jythonScript.getName());
                            interp.execfile(in,jythonScript.getName());
                            logger.log(Level.FINE, "done executing script {0}", jythonScript.getName());
                            
                        } catch ( PyException ex ) {
                            if ( ex.toString().contains("checkForComodification") ) {
                                in.close();
                                in = new FileInputStream( jythonScript );
                                logger.warning("avoiding second strange concurrent modification bug that occurs within Jython on the server.  Run the whole thing again.");
                                Thread.sleep(200);
                                in= JythonRefactory.fixImports(in,jythonScript.getName());
                                interp.execfile(in,jythonScript.getName());
                            } else {
                                throw ex; // This exception is caught again 6 lines down
                            }
                        }
                        in.close();
                    }
                    mon.setProgressMessage( "done executing script");
                } catch (PyException ex) {
                    if ( reader!=null ) {
                        //ex.lineno= ex.lineno+iline;
                        logger.log(Level.FINE, "debugging line number={0}", reader.getLineNumber());
                    }
                    causedBy = ex;
                    Object javaClass= ex.value.__tojava__(Exception.class);
                    // since FileNotFoundException is a special exception where we don't want to interrupt the user with a popup, handle it specially.
                    if ( javaClass instanceof java.io.FileNotFoundException ) {
                        throw (Exception)javaClass;
                    } else if ( javaClass instanceof NoDataInIntervalException ) {
                        throw (Exception)javaClass;
                    } else if ( javaClass instanceof CancelledOperationException ) {
                        throw (Exception)javaClass;
                    } else if ( javaClass instanceof org.das2.util.monitor.CancelledOperationException ) { //TODO: why are there two?
                        throw (Exception)javaClass;
                    }
                    logger.warning( ex.toString() );
                    if (listener != null) {
                        listener.exceptionThrown(ex);
                    }
                } catch (Exception ex) {
                    throw ex;
                }
                
                if (causedBy == null && allowCaching ) {
                    cacheDate = resourceDate(this.uri);
                    cacheUrl = cacheUrl(this.uri);
                } 
            } else {
                logger.fine("using existing interpreter to provide caching");

            }

            String expr = params.get("arg_0");

            PyObject result=null;

            String label= null;
            
            if (expr == null) {
                try {
                    result = interp.eval("result"); // legacy
                } catch ( PyException ex ) {
                    try {
                        result = interp.eval("data"); 
                    } catch ( PyException ex2 ) {
                        if ( causedBy!=null ) {
                            throw ex2;
                        } else {
                            throw new IllegalArgumentException("neither \"data\" nor \"result\" is defined");
                        }
                    }
                }
            } else {
                Object o= interp.get("outputParams");
                if ( o!=null && o instanceof PyDictionary ) {
                    PyDictionary dict= (PyDictionary)o;
                    result= dict.get(Py.newString(expr));
                }
                if ( result==null || result==Py.None ) {
                    result = interp.eval(expr);
                }
                label= expr;
            }
            
            metadata= new LinkedHashMap<String,Object>();
            
            PyObject pymeta;
            try {
                pymeta= interp.eval("metadata");
                if ( pymeta instanceof PyDictionary ) {
                    PyDictionary dict= ((PyDictionary)pymeta);
                    PyList keys= dict.keys();
                    
                    for ( Iterator i= keys.iterator(); i.hasNext();  ) {
                        Object key= i.next();
                        String name= key.toString();
                        Object o= dict.get(  Py.java2py(key) );
                        if ( o instanceof PyList ) {
                            String[] arr= new String[ ((PyList)o).__len__() ];
                            for ( int i2=0; i2<arr.length; i2++ ) {
                                arr[i2]= ((PyList)o).__getitem__(i2).toString();
                            }
                            metadata.put(name,arr);
                        } else {
                            String val= o.toString();
                            metadata.put(name,val);
                        }
                        
                    }
                }
            } catch ( PyException ex ) {
                // symbol "metadata" is not found.
            }
            

            QDataSet res;
            if (result instanceof PyList) {
                res = JythonOps.dataset((PyList) result);
            } else if ( result instanceof PyArray ) {
                res = JythonOps.dataset((PyArray) result);
            } else if ( result instanceof PyInteger ) {
                res = JythonOps.dataset((PyInteger) result);
            } else if ( result instanceof PyFloat ) {
                res = JythonOps.dataset((PyFloat) result);
            } else {
                try {
                    res = (QDataSet) result.__tojava__(QDataSet.class);
                } catch ( ClassCastException ex ) {
                    Object os= (Object) result.__tojava__(Object.class);
                    throw new IllegalArgumentException("variable is not a dataset: "+expr + " ("+os.toString()+")" );
                }
            }

            if ( label!=null && res instanceof MutablePropertyDataSet && !((MutablePropertyDataSet)res).isImmutable() ) {
                if ( res.property( QDataSet.LABEL )==null ) {
                   ((MutablePropertyDataSet)res).putProperty( QDataSet.LABEL, label );
                }
            }


            if ( notCheckedTsb ) {
                PyObject tr= interp.eval("getParam(\'timerange\','x')");
                JythonDataSourceTimeSeriesBrowse tsb1= JythonDataSourceTimeSeriesBrowse.checkForTimeSeriesBrowse( uri.toString(), jythonScript );
                if ( tsb1!=null ) {
                    tsb1.setJythonDataSource(this);
                    if ( !(tr.toString().equals("x")) ) {
                        tsb1.setTimeRange( DatumRangeUtil.parseTimeRange(tr.toString()) );
                    }
                    addCapability( TimeSeriesBrowse.class, tsb1 );
                    tsb= tsb1;
                }
                notCheckedTsb= false;
            }

            // add cache tag to avoid unnecessary reads.
            if ( tsb!=null && res!=null ) {
                // check for cache tag from script
                QDataSet dep0= (QDataSet) res.property(QDataSet.DEPEND_0);
                if ( dep0!=null ) {
                    CacheTag tag= (CacheTag) dep0.property(QDataSet.CACHE_TAG);
                    if ( tag==null ) {
                        tag= new CacheTag( tsb.getTimeRange(), null ); // note if the script reduces data, then it must specify cache tag. 
                        MutablePropertyDataSet mdep0= DataSetOps.makePropertiesMutable(dep0);
                        mdep0.putProperty( QDataSet.CACHE_TAG, tag );
                        MutablePropertyDataSet mres= DataSetOps.makePropertiesMutable(res);
                        mres.putProperty( QDataSet.DEPEND_0, mdep0 );
                        res= mres;
                    } else {
                        logger.log(Level.FINE, "result reports cache tag: {0}", tag);
                    }
                }
            }

            if ( rcent!=null ) {
                URISplit t= URISplit.parse(suri);
                Map<String,String> m= URISplit.parseParams( t.params );
                String s= m.remove("arg_0");

                PyStringMap locals= (PyStringMap) interp.getLocals();
                PyList keys= locals.keys();
                PyList values= locals.values();
                
                boolean useOutputParams= false;
                Object o= interp.get("outputParams");
                if ( o!=null && o instanceof PyDictionary ) {
                    if ( ((PyDictionary)o).__len__()>0 ) {
                        useOutputParams= true;
                    }
                }
                if ( useOutputParams==false ) {
                    logger.fine("loading local datasets to cache");
                    for ( int i=0; i<keys.size(); i++ ) {
                        String key= (String)keys.get(i);
                        Object value= values.get(i);
                        if ( value instanceof PyQDataSet ) {
                            value= ((PyQDataSet)value).getQDataSet();
                        } 
                        if ( value instanceof QDataSet || value==null ) {
                            m.put( "arg_0", String.valueOf( key ) );
                            t.params= URISplit.formatParams(m);
                            String uri1= URISplit.makeCanonical( URISplit.format( t ) );
                            ReferenceCache.getInstance().offerDataSet(uri1, (QDataSet)value );
                            logger.log(Level.FINE, "Also adding to reference cache: {0}->{1}", new Object[]{uri1, value});
                        }
                    }
                } else {
                    logger.fine("loading output params to cache");
                    PyDictionary dict= (PyDictionary)o;
                    assert dict!=null;
                    keys= dict.keys();
                    for ( int i=0; i<keys.size(); i++ ) {
                        String key= (String)keys.get(i);
                        Object value= dict.get(Py.newString(key));
                        if ( value instanceof PyQDataSet ) {
                            value= ((PyQDataSet)value).getQDataSet();
                        } 
                        if ( value instanceof QDataSet || value==null ) {
                            m.put( "arg_0", String.valueOf( key ) );
                            t.params= URISplit.formatParams(m);
                            String uri1= URISplit.makeCanonical( URISplit.format( t ) );
                            ReferenceCache.getInstance().offerDataSet(uri1, (QDataSet)value );
                            logger.log(Level.FINE, "Also adding to reference cache: {0}->{1}", new Object[]{uri1, value});
                        }
                    }
                }
                if ( s==null ) {
                    rcent.finished(res);
                } else {
                    rcent.finished( Ops.dataset( "1971-01-01T00:00" ) );
                }                
            }
            
            if (causedBy != null) {
                interp = null;
                cacheUrl = null;
                cacheDate = null;
                logger.log(Level.WARNING, "exception in processing: {0}", causedBy);
                throw causedBy;
            }

            if ( !allowCaching ) {
                logger.log(Level.FINE, "reset caching because allowCaching is false" );
                interp= null;
            }

            return res;

        } catch (PyException ex) {
            
            if ( rcent!=null ) rcent.exception(ex);
            
            if (causedBy != null) {
                logger.log(Level.FINE, "rethrow causedBy" );
                throw causedBy;
            }
            
            logger.log(Level.FINE, "resetting caching because of PyException" );
            interp = null;
            cacheUrl = null;
            cacheDate = null;

            throw ex;
            
        } catch ( Exception ex ) {
            if ( rcent!=null ) rcent.exception(ex);
            throw ex;
            
        } finally {
            if ( !mon.isFinished() ) mon.finished();
        }
    }

    @Override
    public Map<String, Object> getMetadata(ProgressMonitor mon) throws Exception {
        return metadata;
    }
    
    
    PythonInterpreter interp = null;
    TimeSeriesBrowse tsb= null; // if the script has getParam('timerange','2011-001')

    private String cacheUrl(URI uri) {
        URISplit split = URISplit.parse(uri);
        Map<String, String> params2 = URISplit.parseParams(split.params);
        params2.remove("arg_0");
        split.params = URISplit.formatParams(params2);
        return URISplit.format(split);
    }

    private Date resourceDate(URI uri) throws IOException {
        File src = DataSetURI.getFile( DataSetURI.fromUri(uri), true, new NullProgressMonitor()); //TODO: this is probably wrong, because it should always be the script...
        return new Date(src.lastModified());
    }
    Date cacheDate = null;
    String cacheUrl = null;

    private synchronized boolean useCache(URI uri) {
        try {
            if ((cacheDate != null && !resourceDate(uri).after(cacheDate)) && (cacheUrl != null && cacheUrl.equals(cacheUrl(uri)))) {
                if ( uri.toString().contains("allowCaching=F") ) {
                    return false;
                } else {
                    return true;
                }
            }
            return false;
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public boolean satisfies(String surl) {
        URISplit split= URISplit.parse(surl);
        if ( !"vap+jyds".equals( split.vapScheme ) ) {
            return false;
        }
        try {
            return useCache( DataSetURI.getURI(surl) );
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    @Override
    public void resetURI(String surl) {
        try {
            this.uri = DataSetURI.getURI(surl);
            URISplit split = URISplit.parse(uri);
            params = URISplit.parseParams(split.params);
            resourceURI = DataSetURI.toUri(split.file);
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * explicitly reset the interpreter, which is cached to provide results.
     */
    @Override
    public synchronized void reset() {
        logger.fine("JythonDataSource.reset() clears cache");
        interp= null;
    }

}
