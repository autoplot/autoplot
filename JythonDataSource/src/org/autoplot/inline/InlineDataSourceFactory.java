
package org.autoplot.inline;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRangeUtil;
import org.das2.jythoncompletion.CompletionSupport;
import org.das2.jythoncompletion.DefaultCompletionItem;
import org.das2.jythoncompletion.JythonCompletionTask;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.util.PythonInterpreter;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSetURI.CompletionResult;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.autoplot.jythonsupport.JythonOps;
import org.autoplot.jythonsupport.JythonUtil;
import org.autoplot.jythonsupport.Util;

/**
 * Creates inline data sources.
 * @author jbf
 */
public class InlineDataSourceFactory extends AbstractDataSourceFactory {

    private static final Logger logger= org.das2.datum.LoggerManager.getLogger("jython.inline");

    public InlineDataSourceFactory() {
        DataSourceUtil.addMakeAggregationForScheme("vap+inline", new MakeAggMap() );
    }
    
    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new InlineDataSource( uri );
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        List<CompletionContext> result= new ArrayList();
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            PythonInterpreter interp = JythonUtil.createInterpreter(false);
            URL imports = JythonOps.class.getResource("/imports2023.py");
            if ( imports!=null ) {
                interp.execfile(imports.openStream(),"imports2023.py");
            } else {
                logger.warning("unable to find imports2023.py");
            }
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
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            PythonInterpreter interp = JythonUtil.createInterpreter(false);
            URL imports = JythonOps.class.getResource("/imports2023.py");
            interp.execfile(imports.openStream(),"imports2023.py");
            String frag= cc.completable;
            org.das2.jythoncompletion.CompletionContext cc1= CompletionSupport.getCompletionContext( "x="+frag, cc.completablepos+2, 0, 0, 0 );        
            List<DefaultCompletionItem> r;
            if ( cc1.contextType.equals("stringLiteralArgument") ) {
                if ( cc1.contextString.equals("getDataSet") ) {
                    String s= cc1.completable;
                    char q= s.charAt(0);
                    s= s.substring(1);
                    if ( s.charAt(s.length()-1)==q ) {
                        s= s.substring(0,s.length()-1);
                    }
                    int len= s.length();
                    List<CompletionResult> rx= DataSetURI.getCompletions( s, len, mon );
                    r= new ArrayList<>(rx.size());
                    for ( int i=0; i<rx.size(); i++ ) {
                        String t= cc1.contextString +"("+ q+rx.get(i).completion+q;
                        r.add( new DefaultCompletionItem( t, len, t, "xxx", null ) );
                    }    
                } else {
                    r= JythonCompletionTask.getLocalsCompletions( interp, cc1 );
                }
            } else {
                r=  JythonCompletionTask.getLocalsCompletions( interp, cc1 );
            }

            Collections.sort(r,new Comparator<DefaultCompletionItem>() {
                @Override
                public int compare(DefaultCompletionItem o1, DefaultCompletionItem o2) {
                    return o1.getComplete().compareTo(o2.getComplete());
                }
            });
            
            for ( DefaultCompletionItem item: r ) {
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, item.getComplete(), this, item.getComplete() ) );
            }   
        }
        
        return result;
    }

    /**
     * do the magic with the timerange.
     * @param suri the URI.
     * @param script the script, mangled to handle the timerange.
     * @return the timerange or null.
     */
    protected static String getScript( String suri, List<String> script ) {
        String scriptInline= suri.substring( "vap+inline:".length() );
                
        String[] ss= Util.guardedSplit( scriptInline, '&', '\'', '\"' );
        
        String timerange=null;
        for ( String s: ss ) {
            if ( s.startsWith("timerange=" ) ) {
                timerange= JythonUtil.maybeUnquoteString( JythonUtil.maybeQuoteString( s.substring( 10 ) ) );
            }
        }
        
        for ( int i=0; i<ss.length; i++ ) {
            String s= ss[i];
            if ( timerange!=null ) {
                if ( s.contains("getDataSet(") ) {
                    int k= s.lastIndexOf(")");
                    s= s.substring(0,k) + ",'"+timerange+"')";
                }
            }
            ss[i]= s;
        }
        
        script.addAll(Arrays.asList(ss));
        
        if ( timerange==null ) {
            return null;
        } else {
            return timerange;
        }
    };
    
    private static boolean checkRejectGetDataSet( String suri, List<String> problems, ProgressMonitor mon ) {
         
        List<String> script= new ArrayList();
        
        String timerange= getScript( suri, script );
        
        StringBuilder scriptBuilder= new StringBuilder();
        for ( String s: script ) {
            scriptBuilder.append(s).append("\n");
        }
                    
        Map<String,String> pp= JythonUtil.getGetDataSet( null, scriptBuilder.toString(), null );
        
        for ( Entry<String,String> e: pp.entrySet() ) {
            String surl1= e.getValue();
            int itr= surl1.indexOf(" ");
            if ( itr>-1 ) { // DANGER code -- these sometimes contain strings due to sloppiness.
                surl1= surl1.substring(0,itr);
            }
            URISplit delegateSplit= URISplit.parse(surl1);
            URI uri= DataSetURI.toUri( URISplit.format(delegateSplit) );
            try {
                DataSourceFactory dsf= DataSetURI.getDataSourceFactory( uri, new NullProgressMonitor());
                if ( timerange!=null ) {
                    TimeSeriesBrowse tsb= dsf.getCapability(TimeSeriesBrowse.class);
                    if ( tsb!=null ) {
                        try {
                            tsb.setURI( surl1 );
                            tsb.setTimeRange( DatumRangeUtil.parseTimeRange(timerange) );
                            surl1= tsb.getURI();
                        } catch (ParseException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                    }
                }
                if ( dsf.reject( surl1, problems, mon.getSubtaskMonitor("") ) ) {
                    return true;
                }
            } catch (IOException | IllegalArgumentException | URISyntaxException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            
        }
        
        return false;

    }
    
    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        if ( surl.length()==11 ) return true;
        mon.started();
        try {
            if ( checkRejectGetDataSet( surl, problems, mon.getSubtaskMonitor("getDataSet calls") ) ) {
                return true;
            }
            return super.reject(surl, problems, mon.getSubtaskMonitor("super.reject") ); //To change body of generated methods, choose Tools | Templates.
        } finally {
            mon.finished();
        }
    }

    @Override
    public <T> T getCapability(Class<T> clazz) {
        if ( clazz==TimeSeriesBrowse.class ) {
            return (T) new InlineTimeSeriesBrowse();
        } else {
            return super.getCapability(clazz); //To change body of generated methods, choose Tools | Templates.
        }
    }

    
    @Override
    public boolean supportsDiscovery() {
        return true;
    }

    @Override
    public boolean isFileResource() {
        return false;
    }

    @Override
    public String getDescription() {
        return "Data encoded within the URI";
    }
    
}
