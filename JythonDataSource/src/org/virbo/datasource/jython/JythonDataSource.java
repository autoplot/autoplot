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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.core.PyException;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.URLSplit;
import org.virbo.datasource.capability.Caching;
import org.virbo.jythonsupport.JythonOps;
import org.virbo.jythonsupport.JythonUtil;

/**
 *
 * @author jbf
 */
public class JythonDataSource extends AbstractDataSource implements Caching {

    ExceptionListener listener;
    private Map<String, Object> metadata;
    private final static String PARAM_SCRIPT= "script";
    private static Logger logger= Logger.getLogger("vap.jythondatasource");

    public JythonDataSource(URI uri, JythonDataSourceFactory factory) {
        super(uri);
        addCability(Caching.class, this);
        this.listener = factory.listener;

    }

    private File getScript() throws IOException {
        File jythonScript; // script to run.
        URI resourceURI;     // optional resource URI that is argument to script, excluding script argument.

        if ( params.get( PARAM_SCRIPT )!=null ) {
            jythonScript= getFile( new URL(params.get( PARAM_SCRIPT )), new NullProgressMonitor() );
        } else {
            resourceURI= null;
            jythonScript= getFile(new NullProgressMonitor());
        }
        return jythonScript;
    }

    @Override
    public synchronized QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        mon.started();

        File jythonScript;   // script to run.
        String resourceURI;  // optional resource URI that is argument to script, excluding script argument.
        
        if ( params.get( PARAM_SCRIPT )!=null ) {
            jythonScript= getFile( new URL(params.get( PARAM_SCRIPT )), new NullProgressMonitor() );
            mon.setProgressMessage( "loading "+uri );
            URLSplit split= URLSplit.parse(uri.toString());
            split.params= null;
            resourceURI= URLSplit.format(split);

        } else {
            resourceURI= null;
            jythonScript= getFile(new NullProgressMonitor());
        }
        
        PyException causedBy = null;
        try {
            if (interp == null) { // caching.
                mon.started();
                mon.setProgressMessage( "initialize Jython interpreter...");
                interp = JythonUtil.createInterpreter(false);
                mon.setProgressMessage( "done initializing Jython interpreter");
                interp.set("monitor", mon);

                interp.exec("params=dict()");
                for (String s : params.keySet()) {
                    if (!s.equals("arg_0") && !s.equals("script") ) {
                        String sval= params.get(s);
                        if ( sval.length()>0 && Character.isJavaIdentifierStart(sval.charAt(0)) ) {
                            sval= String.format( "'%s'", sval );
                        }
                        logger.fine("params['" + s + "']=" + sval);
                        interp.exec("params['" + s + "']=" + sval);
                    }
                }

                interp.exec("def getParam( x, default ):\n  if params.has_key(x):\n     return params[x]\n  else:\n     return default\n");
                
                interp.set("resourceURI", resourceURI);
                
                mon.setProgressMessage( "executing script");
                try {
                    boolean debug = false;  //TODO: exceptions will have the wrong line number in this mode.
                    if (debug) {
                        int i = 0;
                        BufferedReader reader = new BufferedReader(new FileReader( jythonScript ) );
                        String s = reader.readLine();
                        i++;
                        while (s != null) {
                            Logger.getLogger("virbo.jythondatasource").fine("" + i + ": " + s);
                            interp.exec(s);
                            s = reader.readLine();
                            i++;
                        }
                    } else {
                        interp.execfile(new FileInputStream( jythonScript ));
                    }
                    mon.setProgressMessage( "done executing script");
                } catch (PyException ex) {
                    causedBy = ex;
                    ex.printStackTrace();
                    if (listener != null) {
                        listener.exceptionThrown(ex);
                    }
                } catch (Exception ex) {
                    throw ex;
                }

                if (causedBy == null) {
                    cacheDate = resourceDate(this.uri);
                    cacheUrl = cacheUrl(this.uri);
                }
            }

            String expr = params.get("arg_0");

            PyObject result;
            
            if (expr == null) {
                try {
                    result = interp.eval("data");
                } catch ( PyException ex ) {
                    try {
                        result = interp.eval("result"); // legacy
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
                res = JythonOps.coerce((PyList) result);
            } else {
                res = (QDataSet) result.__tojava__(QDataSet.class);
            }

            if (causedBy != null) {
                interp = null;
                cacheUrl = null;
                cacheDate = null;
                Logger.getLogger("virbo.jythonDataSouce").warning("exception in processing: " + causedBy);
            }

            mon.finished();
            return res;

        } catch (PyException ex) {

            String msg = "PyException: " + ex;
            if (causedBy != null) {
                msg += "\ncaused by:\n" + causedBy;
            }
            interp = null;
            cacheUrl = null;
            cacheDate = null;

            throw new RuntimeException(msg);
        } finally {
            mon.finished();
        }
    }

    @Override
    public Map<String, Object> getMetaData(ProgressMonitor mon) throws Exception {
        return metadata;
    }
    
    
    PythonInterpreter interp = null;

    private String cacheUrl(URI uri) {
        URLSplit split = URLSplit.parse(uri.toString());
        Map<String, String> params = URLSplit.parseParams(split.params);
        params.remove("arg_0");
        split.params = URLSplit.formatParams(params);
        return URLSplit.format(split);
    }

    private Date resourceDate(URI uri) throws IOException {
        File src = DataSetURL.getFile(uri, new NullProgressMonitor());
        return new Date(src.lastModified());
    }
    Date cacheDate = null;
    String cacheUrl = null;

    private boolean useCache(URI uri) {
        try {
            if ((cacheDate != null && !resourceDate(uri).after(cacheDate)) && (cacheUrl != null && cacheUrl.equals(cacheUrl(uri)))) {
                return true;
            }
            return false;
        } catch (IOException ex) {
            return false;
        }
    }

    public boolean satisfies(String surl) {
        try {
            return useCache(new URI(surl));
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    public void resetURL(String surl) {
        try {
            this.uri = new URI(surl);
            URLSplit split = URLSplit.parse(uri.toString());
            params = URLSplit.parseParams(split.params);
            resourceURI = new URI(split.file);
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }


    }
}
