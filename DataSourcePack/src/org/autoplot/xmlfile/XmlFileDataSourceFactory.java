
package org.autoplot.xmlfile;

import org.autoplot.spase.VOTableReader;
import org.autoplot.spase.*;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.URISplit;
import org.das2.qds.ops.Ops;
import org.das2.util.monitor.NullProgressMonitor;

/**
 *
 * @author jbf
 */
public class XmlFileDataSourceFactory extends AbstractDataSourceFactory implements DataSourceFactory {
    
    private static final Logger logger= LoggerManager.getLogger("apdss.xml");
    
    public XmlFileDataSourceFactory() {
    }
    
    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new SpaseRecordDataSource(uri);
    }
    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc,org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        
        File f= DataSetURI.getFile( cc.surl, mon );

        Object type= new XMLTypeCheck().calculateType(f);

        if ( type==XMLTypeCheck.TYPE_VOTABLE ) {
            QDataSet bds= new VOTableReader().readHeader(f.toString(), mon);
            if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_NAME) ) {
                List<CompletionContext> result= new ArrayList<>();
                for ( int i=0; i<bds.length(); i++ ) {
                    String label= (String)bds.property(QDataSet.LABEL,i);
                    String name= (String)bds.property(QDataSet.NAME,i);
                    String title= (String)bds.property(QDataSet.TITLE,i);
                    String c= label;
                    if ( !Ops.safeName(label).equals(label) ) {
                        label= label+" ("+name+")";
                    } 
                    if ( title==null ) {
                        title= label;
                    }
                    int lim= Math.max(20,120-label.length());
                    if ( title.length()>lim ) title= title.substring(0,lim-3)+"...";
                    CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, c, this, "arg_0", title, title, true );
                    result.add( cc1 );  
                }
                return result;
            } else {
                return Collections.emptyList();
            }
        } else if ( type==XMLTypeCheck.TYPE_SPASE ) {
            return new SpaseRecordDataSourceFactory().getCompletions(cc, new NullProgressMonitor() );

        } else {
            return Collections.emptyList();                
        }
            
    }
    
    public String editPanel(String surl) throws Exception {
        return surl;
    }
    
    
    @Override
    public boolean reject( String surl, List<String> problems, ProgressMonitor mon ) throws IllegalArgumentException {
        
        try {
            File f= DataSetURI.getFile( surl, mon);
            
            Object type= new XMLTypeCheck().calculateType(f);

            if ( type==null ) {
                return true;
            } else {
                if ( type==XMLTypeCheck.TYPE_VOTABLE ) {
                    URISplit split= URISplit.parse(surl);
                    Map<String,String> parms= URISplit.parseParams(split.params);
                    if ( parms.get( URISplit.PARAM_ARG_0 )==null ) {
                        QDataSet bds= new VOTableReader().readHeader(f.toString(), mon);
                        // check for VOTable that is events file, for backward compatibility.
                        int ifirstTimeStart=-1;
                        int ifirstTimeStop=-1;
                        for ( int i=0; i<bds.length(); i++ ) {
                            if ( bds.property(QDataSet.UNITS,i)!=null && UnitsUtil.isTimeLocation( (Units)bds.property(QDataSet.UNITS,i) ) ) {
                                if ( ifirstTimeStart==-1 ) {
                                    ifirstTimeStart=i;
                                } else {
                                    if ( i==ifirstTimeStart+1 ) {
                                        ifirstTimeStop= ifirstTimeStart+1;
                                        break;
                                    }
                                }
                            }
                        }
                        if ( ifirstTimeStop>-1 ) {
                            return false; // this is an "events" file, used in Autoplot for quite a while.
                        } else {
                            return true;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            
        } catch ( Exception ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return true;
        }
    }

    public String urlForServer(String surl) {
        return surl; //TODO
    }
    
}
