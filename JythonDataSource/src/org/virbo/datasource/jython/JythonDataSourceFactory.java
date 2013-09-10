/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource.jython;

import java.beans.ExceptionListener;
import java.io.BufferedReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.virbo.jythonsupport.PyQDataSet;
import org.virbo.jythonsupport.PyQDataSetAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.Py;
import org.python.core.PyList;
import org.python.core.PyStringMap;
import org.python.util.PythonInterpreter;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.LogNames;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import static org.virbo.datasource.jython.JythonDataSource.PARAM_SCRIPT;
import org.virbo.jythonsupport.JythonOps;
import org.virbo.jythonsupport.JythonUtil;

/**
 *
 * @author jbf
 */
public class JythonDataSourceFactory extends AbstractDataSourceFactory {

    private static final Logger logger= Logger.getLogger( LogNames.APDSS_JYDS );

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        JythonDataSource result = new JythonDataSource(uri,this);
        return result;
    }
    
    /**
     * get the name of the script, which is non-trivial since it can be in either the resourceURI or script=
     * @return
     */    
    private static String getScript(String suri)  {
        String jythonScript; // script to run.
        
        URISplit split= URISplit.parse(suri);
        Map <String,String> params= URISplit.parseParams(split.params);
        
        if ( params.get( PARAM_SCRIPT )!=null ) {
            // getFile( resourceURI ) //TODO: since we don't getFile(resourceURI), we can't use filePollUpdating.  Also, why do we have local variable?
            jythonScript= params.get( PARAM_SCRIPT );
        } else {
            jythonScript= split.resourceUri.toString();
        }
        return jythonScript;
    }    

    private Map<String, Object> getNames(URI uri, ProgressMonitor mon) throws Exception {
        PythonInterpreter interp = new PythonInterpreter();
        Py.getAdapter().addPostClass(new PyQDataSetAdapter());

        interp.set("monitor", mon);
        interp.execfile(JythonOps.class.getResource("imports.py").openStream(), "imports.py"); // import everything into default namespace.

        File src = DataSetURI.getFile(uri, new NullProgressMonitor());

        URISplit split = URISplit.parse(uri);
        Map<String, String> params = URISplit.parseParams(split.params);
        try {
            interp.exec("import autoplot");
            interp.exec("autoplot.params=dict()");
            for ( Entry<String,String> e : params.entrySet()) {
                String s= e.getKey();
                if (!s.equals("arg_0")) {
                    interp.exec("autoplot.params['" + s + "']=" + e.getValue() );
                }
            }

            interp.execfile(new FileInputStream(src));

            PyStringMap map = ((PyStringMap) interp.getLocals());
            PyList list = map.keys();

            HashMap result = new HashMap();

            for (int i = 0; i < list.__len__(); i++) {
                String key = (String) list.get(i);
                Object o = map.get(Py.newString(key));
                if (o instanceof PyQDataSet) {
                    result.put(key, o);
                }
            }
            return result;
        } finally {
            mon.finished();
        }


    }

    protected static Map<String,JythonUtil.Param> getParams( String suri, Map<String,String> current, ProgressMonitor mon ) throws IOException {
        String furi= getScript( suri );

        File src = DataSetURI.getFile(furi, mon );

        FileReader reader= new FileReader(src);
        try {
            String script= JythonUtil.readScript( new BufferedReader( reader ) );
            List<JythonUtil.Param> r2= JythonUtil.getGetParams( script, current );

            Map<String,JythonUtil.Param> result= new LinkedHashMap();

            for ( JythonUtil.Param r : r2 ) {
                result.put( r.name, r );
            }

            return result;
        } finally {
            reader.close();
        }
    }

    protected static Map<String,JythonUtil.Param> getParams( URI uri, Map<String,String> current, ProgressMonitor mon ) throws IOException {
        return getParams( uri.toString(), current, mon );
    }

    protected static Map<String,JythonUtil.Param> getParams( URI uri, ProgressMonitor mon ) throws IOException {
        return getParams( uri.toString(), null, mon );
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        List<CompletionContext> result = new ArrayList<CompletionContext>();
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            String ext= DataSetURI.fromUri(cc.resourceURI);
            int i= ext.lastIndexOf(".");
            if ( i!=-1 ) ext= ext.substring(i+1);
            if ( ext.equals("jyds" ) || ext.equals("jy") || ext.equals("py") ) {
                Map<String, Object> po = getNames( cc.resourceURI, mon);
                for (String n : po.keySet()) {
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, n, this, "arg_0", null, null));
                }
                Map<String,JythonUtil.Param> po2= getParams( cc.resourceURI, new NullProgressMonitor() );
                for ( String n: po2.keySet() ) {
                    JythonUtil.Param parm= po2.get(n);
                    if ( parm.doc==null ) parm.doc="";
                    if ( !parm.name.equals(parm.label) ) {
                        parm.doc+= " (named "+parm.label+" in the script)";
                    }
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, n+"=", n + " default is '"+ po2.get(n).deft + "'", po2.get(n).doc ) );
                }

            } else {
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "script=", "the name of the python script to run"));
            }
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if ( paramName.equals("script") ) {
                //TODO: filesystem completions.
            } else {
                Map<String,JythonUtil.Param> po2= getParams( cc.resourceURI, new NullProgressMonitor() );
                JythonUtil.Param pp= po2.get(paramName);
                if ( pp!=null ) {
                    if ( pp.deft instanceof Number ) {
                        result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, String.valueOf(pp.deft), paramName + " default is '"+ pp.deft + "'", pp.doc ) );
                    } else {
                        result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, String.format( "'%s'", pp.deft ), paramName + " default is '"+ pp.deft + "'", pp.doc ) );
                    }
                }
            }
        }
        return result;
    }

    /**
     * Reject when:
     *   - the URI doesn't contain a timerange but the data source ha
     * @param surl
     * @param problems
     * @param mon
     * @return 
     */
    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        
        URISplit split= URISplit.parse(surl);
        Map<String,String> uriParams= URISplit.parseParams(split.params);
        
        try {
            Map<String,JythonUtil.Param> parms= getParams( surl, new HashMap<String,String>(), mon);
            if ( parms.containsKey( JythonDataSource.PARAM_TIMERANGE ) && !uriParams.containsKey(JythonDataSource.PARAM_TIMERANGE) ) {
                problems.add(TimeSeriesBrowse.PROB_NO_TIMERANGE_PROVIDED);
                return true;
            }
        } catch ( IOException ex ) {
            problems.add(ex.toString());
            return true;
        }
        
        if (surl.contains("?")) {
            if ( split.params.length()>0 ) {
                return false;
            } else {
                return true;
            }
        } else {
            try {
                if ( split.scheme!=null && split.scheme.equals("inline") ) {
                    return false;
                }
                URL url= DataSetURI.getURL(surl);
                if ( url==null ) {
                    return true;
                }

                File src = DataSetURI.getFile( url, new NullProgressMonitor() );
                BufferedReader reader = new BufferedReader(new FileReader(src));
                String s = reader.readLine();
                boolean haveResult = false;
                while (s != null) {
                    if (s.trim().startsWith("result")) {
                        haveResult = true;
                        break;
                    }
                    if (s.trim().startsWith("data")) {
                        haveResult = true;
                        break;
                    }
                    s = reader.readLine();
                }
                reader.close();
                return !haveResult;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
                return true;
            }
        }

    }

    /**
     * Scrape through the Jython script looking for assignments.  Ideally, this would just be a list of plottable
     * result parameters, but that's impossible without actually running the script.
     * @param surl
     * @param mon
     * @return
     * @throws IOException
     */
    protected static Map<String,String> getResultParameters( String surl, ProgressMonitor mon ) throws IOException {
        File src = DataSetURI.getFile(DataSetURI.getURL(surl),mon);
        BufferedReader reader = new BufferedReader(new FileReader(src));
        String s = reader.readLine();

        Pattern assignPattern= Pattern.compile("\\s*([_a-zA-Z][_a-zA-Z0-9]*)\\s*=(.*)(#(.*))?");
        Pattern defPattern= Pattern.compile("def .*");

        boolean inDef= false;

        Map<String,String> result= new LinkedHashMap<String, String>(); // from ID to description

        while (s != null) {

            if ( inDef==false ) {
                Matcher defm= defPattern.matcher(s);
                if ( defm.matches() ) {
                    inDef= true;
                }
            } else {
                if ( s.length()>0 && !Character.isWhitespace(s.charAt(0)) ) {
                    Matcher defm= defPattern.matcher(s);
                    inDef=  defm.matches();
                }
            }

            if ( !inDef ) {
                Matcher m= assignPattern.matcher(s);
                if ( m.matches() ) {
                    String rhs= m.group(2);
                    if ( rhs.contains("getParam(") ) {
                        // reject
                    } else {
                        if ( m.group(4)!=null ) {
                            result.put(m.group(1), m.group(4) );
                        } else {
                            result.put(m.group(1), s );
                        }
                    }
                }
            }

            s = reader.readLine();
        }
        reader.close();
        return result;
    }
    
    ExceptionListener listener;
    
    public void addExeceptionListener( ExceptionListener listener ) {
        this.listener= listener;
    }

    @Override
    public <T> T getCapability(Class<T> clazz) {
        if ( clazz==TimeSeriesBrowse.class ) {
            return (T)new JythonDataSourceTimeSeriesBrowse("file:///");
        } else {
            return super.getCapability(clazz);
        }
    }




}
