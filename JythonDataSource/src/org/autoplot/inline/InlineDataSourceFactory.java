/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.inline;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.das2.jythoncompletion.CompletionSupport;
import org.das2.jythoncompletion.DefaultCompletionItem;
import org.das2.jythoncompletion.JythonCompletionTask;
import org.das2.util.monitor.ProgressMonitor;
import org.python.util.PythonInterpreter;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;
import org.virbo.jythonsupport.JythonOps;
import org.virbo.jythonsupport.JythonUtil;

/**
 *
 * @author jbf
 */
public class InlineDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new InlineDataSource( uri );
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        List<CompletionContext> result= new ArrayList();
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            PythonInterpreter interp = JythonUtil.createInterpreter(false);
            URL imports = JythonOps.class.getResource("imports.py");
            interp.execfile(imports.openStream());
            String frag= cc.completable;
            org.das2.jythoncompletion.CompletionContext cc1= CompletionSupport.getCompletionContext( "x="+frag, cc.completablepos+2, 0, 0, 0 );        
            List<DefaultCompletionItem> r=  JythonCompletionTask.getLocalsCompletions( interp, cc1 );

            Collections.sort(r,new Comparator<DefaultCompletionItem>() {
                @Override
                public int compare(DefaultCompletionItem o1, DefaultCompletionItem o2) {
                    return o1.getComplete().compareTo(o2.getComplete());
                }
            });
            
            for ( DefaultCompletionItem item: r ) {
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, item.getComplete(), this, "arg_0" ) );
            }   
        }
        
        return result;
    }

    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        if ( surl.length()==11 ) return true;
        return super.reject(surl, problems, mon); //To change body of generated methods, choose Tools | Templates.
    }



}
