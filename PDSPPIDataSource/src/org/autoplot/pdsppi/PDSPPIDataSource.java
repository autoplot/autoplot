
package org.autoplot.pdsppi;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURI;
import org.virbo.dsops.Ops;
import org.virbo.spase.VOTableReader;

/**
 * Read data from PDS PPI, using their web interface.  Data is communicated 
 * in VOTABLEs.
 * 
 * @author jbf
 */
public class PDSPPIDataSource extends AbstractDataSource {

    private static final Logger logger= LoggerManager.getLogger("apdss.pdsppi");
    
    PDSPPIDataSource( URI uri ) {
        super(uri);
    }
    
    @Override
    public org.virbo.dataset.QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        String id= (String) getParams().get("id");
        String param= (String) getParams().get("param");
        if ( param==null ) {
             param= (String) getParams().get("ds");
        }
        if ( id==null ) throw new IllegalArgumentException("id not specified");
        if ( param==null ) throw new IllegalArgumentException("ds not specified");
        
        param= param.replaceAll("\\+"," ");
        
        String url= "http://ppi.pds.nasa.gov/ditdos/write?f=vo&id=pds://"+id;
        VOTableReader read= new VOTableReader();
        mon.setProgressMessage("downloading data");
        logger.log(Level.FINE, "getDataSet {0}", url);
        File f= DataSetURI.downloadResourceAsTempFile( new URL(url), 3600, mon );
        mon.setProgressMessage("reading data");
        QDataSet ds= read.readTable( f.toString(), mon );
        QDataSet result= DataSetOps.unbundle( ds, param );
        
        if ( result.property(QDataSet.DEPEND_0)==null ) {
            QDataSet bds= (QDataSet) ds.property(QDataSet.BUNDLE_1);
            int i= DataSetOps.indexOfBundledDataSet( ds, param );
            String n= (String) bds.property(QDataSet.DEPENDNAME_0,i);
            if ( n!=null ) {
                result= Ops.link( DataSetOps.unbundle(ds,n), result );
            }
        }
        
        QDataSet ah= Ops.autoHistogram(result);
        Map<String,Object> up= (Map<String,Object>) ah.property(QDataSet.USER_PROPERTIES);
        Map<Double,Integer> outl= (Map<Double,Integer>) up.get("outliers");
        Integer outlierCount= (Integer) up.get("outlierCount");
        if ( outlierCount>10 ) { //TODO: review this code.  PDSPPI should be doing this.
            for ( Entry<Double,Integer> out: outl.entrySet() ) {
                if ( out.getValue()>outlierCount*8/10 ) { // ID FILL
                    logger.log(Level.FINE, "identified fill: {0}", out.getKey());
                    ((MutablePropertyDataSet)result).putProperty(QDataSet.FILL_VALUE, out.getKey() );
                    break;
                }
            }
        }
        return result;
        
    }
    
}
