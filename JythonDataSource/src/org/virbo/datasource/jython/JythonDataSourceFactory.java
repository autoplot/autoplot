/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource.jython;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.das2.util.monitor.NullProgressMonitor;
import org.python.core.Py;
import org.python.core.PyList;
import org.python.core.PyStringMap;
import org.python.util.PythonInterpreter;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;

/**
 *
 * @author jbf
 */
public class JythonDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URL url) throws Exception {
        return new JythonDataSource(url);
    }

    @Override
    public List<String> extensions() {
        return Collections.singletonList("jy");
    }

    @Override
    public List<String> mimeTypes() {
        return Collections.emptyList();
    }

    private Map<String,Object> getNames( URL url ) throws IOException {
        PythonInterpreter interp = new PythonInterpreter();
        Py.getAdapter().addPostClass(new PyQDataSetAdapter());

        interp.execfile(JythonDataSource.class.getResource("imports.py").openStream(), "imports.py");

        File src= DataSetURL.getFile( url, new NullProgressMonitor());
        
        try {
            interp.execfile(new FileInputStream( src ) );

            PyStringMap map= ((PyStringMap)interp.getLocals());
            PyList list= map.keys();
        
            HashMap result= new HashMap();
        
            for ( int i=0; i<list.__len__(); i++ ) {
                String key= (String)list.get(i);
                Object o= map.get( Py.newString(key) );
                if ( o instanceof PyQDataSet ) {
                    result.put( key, o );
                }
            }
            return result;
        } catch ( Exception e ) {
            HashMap result= new HashMap();
            result.put( "EXCEPTION", e.getMessage() );
            return result;
        }
        
        
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc) {
        try {
            Map<String,Object> po= getNames( DataSetURL.getURL( CompletionContext.get( CompletionContext.CONTEXT_FILE, cc ) ) );
            List<CompletionContext> result= new ArrayList<CompletionContext>();
            for ( String n: po.keySet() ) {
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, n,this, "arg_0" ) );
            }
            return result;
        } catch ( IOException e ) {
            return Collections.singletonList( new CompletionContext(cc.context, e.getMessage() ) );
        }
    }

    @Override
    public boolean reject(String surl) {
        try {
            if ( surl.contains("?") ) return false;
            Map<String,Object> po= getNames( DataSetURL.getURL(surl) );
            if ( po.get("result")!=null ) return false;
            return true;
        } catch ( IOException e ) {
            return false; // it's not the operator's fault.
        }
            
    }
}
