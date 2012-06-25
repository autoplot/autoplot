/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource.jython;

import java.beans.ExceptionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.dataset.CacheTag;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.core.PyException;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.Caching;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.jythonsupport.JythonOps;
import org.virbo.jythonsupport.JythonUtil;

/**
 * Use a jython script to read and process data from a number of sources.
 * Special parameters:
 *   timerange  if used then TimeSeriesBrowse is added.
 * @author jbf
 */
public class JythonDataSource extends AbstractDataSource implements Caching {

    ExceptionListener listener;
    private Map<String, Object> metadata;
    private final static String PARAM_SCRIPT= "script";
    private static final Logger logger= Logger.getLogger("vap.jythondatasource");
    private boolean notCheckedTsb= true;

    public JythonDataSource(URI uri, JythonDataSourceFactory factory) {
        super(uri);
        addCability(Caching.class, this); //TODO: check for parameter inputs!
        this.listener = factory.listener;

        if ( true ) {
            try {
                File jythonScript= getScript();  // this assumes the user can go without progress feedback.
                TimeSeriesBrowse tsb1= checkForTimeSeriesBrowse( uri.toString(), jythonScript );
                if ( tsb1!=null ) {
                    addCability( TimeSeriesBrowse.class, tsb1 );
                    tsb= tsb1;
                    notCheckedTsb= false;
                }
            } catch (ParseException ex) {
                System.err.println(ex);
            } catch ( IOException ex ) {
                System.err.println(ex);
                // behave as before...
            }
        }
    }

    private File getScript() throws IOException {
        File jythonScript; // script to run.
        URI resourceURI;     // optional resource URI that is argument to script, excluding script argument.

        if ( params.get( PARAM_SCRIPT )!=null ) {
            // getFile( resourceURI ) //TODO: since we don't getFile(resourceURI), we can't use filePollUpdating.  Also, why do we have local variable?
            jythonScript= getFile( new URL(params.get( PARAM_SCRIPT )), new NullProgressMonitor() );
        } else {
            resourceURI= null;
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
            res = JythonOps.coerceToDs((PyList) result);
        } else {
            res = (QDataSet) result.__tojava__(QDataSet.class);
        }
        return res;
    }

    @Override
    public synchronized QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        mon.started();

        File jythonScript;   // script to run.
        String resourceURI;  // optional resource URI that is argument to script, excluding script argument.

        String suri= DataSetURI.fromUri(uri);
        if ( tsb!=null ) {
            suri= tsb.getURI();
        }

        URISplit split= URISplit.parse(suri);
        Map<String,String> paramsl= URISplit.parseParams(split.params); // abstract datasource params don't update.

        if ( split.scheme.equals("inline") ) { // note this is handled elsewhere, in InlineDataSource
            return getInlineDataSet(new URI(uri.getRawSchemeSpecificPart()));
        }

        if ( params.get( PARAM_SCRIPT )!=null ) {
            jythonScript= getFile( new URL(params.get( PARAM_SCRIPT )), new NullProgressMonitor() );
            mon.setProgressMessage( "loading "+uri );
            split.params= null;
            resourceURI= DataSetURI.fromUri( DataSetURI.getResourceURI(URISplit.format(split)) );

        } else {
            resourceURI= null;
            jythonScript= getFile(new NullProgressMonitor());
        }

        boolean allowCaching= !( "F".equals( params.get("allowCaching") ) );

        if ( !allowCaching ) interp= null;
        
        PyException causedBy = null;
        try {
            if ( interp == null ) { // caching might leave the interpretter open.  This needs to be tweaked--the TSB could set interp to null for example.
                mon.started();
                mon.setProgressMessage( "initialize Jython interpreter...");
                interp = JythonUtil.createInterpreter(false);
                mon.setProgressMessage( "done initializing Jython interpreter");
                interp.set("monitor", mon);

                interp.exec("params=dict()");
                for ( Entry<String,String> e : paramsl.entrySet()) {
                    String s= e.getKey();
                    if (!s.equals("arg_0") && !s.equals("script") ) {
                        String sval= e.getValue();
                        
                        sval= maybeQuoteString( sval );
                        logger.log(Level.FINE, "params[''{0}'']={1}", new Object[]{s, sval});
                        interp.exec("params['" + s + "']=" + sval);
                    }
                }

                
                if ( resourceURI!=null ) {
                    interp.set("resourceURI", resourceURI); // legacy
                    interp.exec("params['resourceURI']="+ maybeQuoteString( resourceURI ) );
                }

                mon.setProgressMessage( "executing script");
                
                LineNumberReader reader=null;
                try {
                    boolean debug = false;  //TODO: exceptions will have the wrong line number in this mode.
                    if (debug) {
                        FileReader fr= new FileReader( jythonScript );
                        reader = new LineNumberReader( fr );
                        String[] nextLine= new String[1];

                        String s = nextExec( reader, nextLine );
                        long t0= System.currentTimeMillis();
                        while (s != null) {
                            Logger.getLogger("virbo.jythondatasource").log(Level.FINE, "{0}: {1}", new Object[]{reader.getLineNumber(), s});
                            interp.exec(s);
                            System.err.printf("line=%d time=%dms  %s\n", reader.getLineNumber(), (System.currentTimeMillis()-t0), s );
                            if ( mon.isCancelled() ) break;
                            mon.setProgressMessage("exec line "+reader.getLineNumber() );
                            s = nextExec( reader, nextLine );
                            t0= System.currentTimeMillis();
                        }
                        fr.close();

                    } else {
                        FileInputStream in = new FileInputStream( jythonScript );
                        interp.execfile(in);
                        in.close();
                    }
                    mon.setProgressMessage( "done executing script");
                } catch (PyException ex) {
                    if ( reader!=null ) {
                        //ex.lineno= ex.lineno+iline;
                        System.err.println("debugging line number="+reader.getLineNumber());
                    }
                    causedBy = ex;
                    ex.printStackTrace();
                    if (listener != null) {
                        listener.exceptionThrown(ex);
                    }
                } catch (Exception ex) {
                    throw ex;
                }
                reader=null;
                
                if (causedBy == null && allowCaching ) {
                    cacheDate = resourceDate(this.uri);
                    cacheUrl = cacheUrl(this.uri);
                } 
            } else {
                System.err.println("using existing interpreter to provide caching");

            }

            String expr = params.get("arg_0");

            PyObject result;

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
                result = interp.eval(expr);
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
                        String val= dict.get( Py.java2py(key) ).toString();
                        metadata.put(name,val);
                    }
                }
            } catch ( PyException ex ) {
                // symbol "metadata" is not found.
            }
            

            QDataSet res;
            if (result instanceof PyList) {
                res = JythonOps.coerceToDs((PyList) result);
            } else {
                res = (QDataSet) result.__tojava__(QDataSet.class);
            }

            if ( label!=null && res instanceof MutablePropertyDataSet ) {
                if ( res.property( QDataSet.LABEL )==null ) {
                   ((MutablePropertyDataSet)res).putProperty( QDataSet.LABEL, label );
                }
            }


            if ( notCheckedTsb ) {
                PyObject tr= interp.eval("getParam(\'timerange\','x')");
                TimeSeriesBrowse tsb1= checkForTimeSeriesBrowse( uri.toString(), jythonScript );
                if ( tsb1!=null ) {
                    if ( !(tr.toString().equals("x")) ) {
                        tsb1.setTimeRange( DatumRangeUtil.parseTimeRange(tr.toString()) );
                    }
                    addCability( TimeSeriesBrowse.class, tsb1 );
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
                    }
                }
            }

            if (causedBy != null) {
                interp = null;
                cacheUrl = null;
                cacheDate = null;
                Logger.getLogger("virbo.jythonDataSouce").log(Level.WARNING, "exception in processing: {0}", causedBy);
                throw causedBy;
            }

            if ( !allowCaching ) {
                interp= null;
            }

            mon.finished();
            return res;

        } catch (PyException ex) {

            String msg = "PyException: " + ex;
            if (causedBy != null) {
                throw causedBy;
            }
            interp = null;
            cacheUrl = null;
            cacheDate = null;

            throw ex;
        } finally {
            mon.finished();
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
        File src = DataSetURI.getFile( uri.toString(), true, new NullProgressMonitor()); //TODO: this is probably wrong, because it should always be the script...
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

    public boolean satisfies(String surl) {
        if ( surl.startsWith("vap+inline:") ) return false;
        try {
            return useCache(new URI(surl));
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    public void resetURI(String surl) {
        try {
            this.uri = new URI(surl);
            URISplit split = URISplit.parse(uri);
            params = URISplit.parseParams(split.params);
            resourceURI = new URI(split.file);
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }


    }

    private String maybeQuoteString(String sval) {
        boolean isNumber= false;
        try {
            Double.parseDouble(sval); 
        } catch ( NumberFormatException ex ) {
            isNumber= false;
        }

        if ( sval.length()>0 && !isNumber && !sval.equals("True") && !sval.equals("False") ) {
            if ( !( sval.startsWith("'") && sval.endsWith("'") ) ) {
                sval= String.format( "'%s'", sval );
            }
        }
        return sval;

    }

    public class JythonDataSourceTimeSeriesBrowse implements TimeSeriesBrowse {

        DatumRange timeRange;
        String uri;

        JythonDataSourceTimeSeriesBrowse( String uri ) {
            this.uri= uri;
        }

        public void setTimeRange(DatumRange dr) {
            if ( this.timeRange==null || !(this.timeRange.equals(dr)) ) {
                synchronized ( JythonDataSource.this ) {
                    interp= null; // no caching...  TODO: this probably needs work.  For example, if we zoom in.
                }
            }
            this.timeRange= dr;
            URISplit split= URISplit.parse(uri);
            Map<String,String> params= URISplit.parseParams(split.params);
            params.put( "timerange", dr.toString() );
            split.params= URISplit.formatParams(params);
            this.uri= URISplit.format(split);
        }

        public DatumRange getTimeRange() {
            return this.timeRange;
        }

        public void setTimeResolution(Datum d) {
            // do nothing.
        }

        public Datum getTimeResolution() {
            return null;
        }

        public String getURI() {
            return uri;
        }

        public void setURI(String suri) throws ParseException {
            this.uri= suri;
            this.timeRange= URISplit.parseTimeRange(uri);
        }

    }
    /**
     * allow scripts to implement TimeSeriesBrowse if they check for the parameter "timerange"
     * @param jythonScript
     */
    private TimeSeriesBrowse checkForTimeSeriesBrowse( String uri, File jythonScript ) throws IOException, ParseException {
        BufferedReader reader = new LineNumberReader( new FileReader( jythonScript ) );

        String line= reader.readLine();
        Pattern s= Pattern.compile(".*getParam\\(\\s*\\'timerange\\',\\s*\\'([-0-9a-zA-Z:/]+)\\'\\s*(,\\s*\\'.*\\')?\\s*\\).*");  //TODO: default time strings must not contain whitespace.
        while ( line!=null ) {
            Matcher m= s.matcher(line);
            if ( m.matches() ) {
                TimeSeriesBrowse tsb1= new JythonDataSourceTimeSeriesBrowse(uri);
                String str= m.group(1);
                DatumRange tr= DatumRangeUtil.parseTimeRange(str);
                tsb1.setTimeRange(tr);
                reader.close();
                return tsb1;
            } else if ( line.contains("timerange") && line.contains("getParam") ) {
                System.err.println("warning: getParam('timerange') default cannot contain spaces!"); //TODO: come on...
            }
            line= reader.readLine();
        }
        reader.close();
        return null;

    }
}
