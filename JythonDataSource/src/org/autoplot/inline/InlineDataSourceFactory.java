/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.inline;

import java.net.URI;
import java.util.List;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;

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
//        PythonInterpreter interp = JythonUtil.createInterpreter(false);
//        URL imports = JythonOps.class.getResource("imports.py");
//        interp.execfile(imports.openStream());
//        String frag= "ripple";
//        org.das2.jythoncompletion.CompletionContext cc1= 
//                CompletionSupport.getCompletionContext( "x="+frag, cc.completablepos+2, 0, 0, 0 );
//        //String frag= cc.completable;
//        //TODO: where to get CompletionResultSet?
//        
//        JythonCompletionTask.getLocalsCompletions( interp, cc1, null );
//        PyStringMap locals= (PyStringMap) interp.getLocals();
//        
//        return super.getCompletions(cc, mon);
        return super.getCompletions(cc, mon);
    }



}
