
package org.autoplot.pdsppi;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.DataSetOps;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.qds.ops.Ops;
import org.autoplot.spase.VOTableReader;
import org.xml.sax.SAXException;

/**
 * Read data from PDS PPI, using their web interface.  Data is communicated 
 * in VOTABLEs.
 * 
 * @author jbf
 */
public class PDSPPIDataSource extends AbstractDataSource {

    private static final Logger logger= LoggerManager.getLogger("apdss.pdsppi");
    
    /**
     * no dataset can have more than MAX_BUNDLE_COUNT datasets.  
     */
    public static final int MAX_BUNDLE_COUNT= 12;
    
    PDSPPIDataSource( URI uri ) {
        super(uri);
        //addCability( TimeSeriesBrowse.class, new PDSPPITimeSeriesBrowse(uri.toString()) );
    }
    
    @Override
    public org.das2.qds.QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        
        try {
            TimeSeriesBrowse tsb= getCapability( TimeSeriesBrowse.class );
            if ( tsb!=null ) {
                String luri= tsb.getURI();
                if ( luri!=null ) {
                    URISplit split = URISplit.parse(luri);
                    params = URISplit.parseParams(split.params);
                }
            }

            String id= (String) getParams().get("id");
            String param= (String) getParams().get("param");
            if ( param==null ) {
                 param= (String) getParams().get("ds");
            }
            if ( id==null ) throw new IllegalArgumentException("id not specified");
            if ( param==null ) throw new IllegalArgumentException("ds not specified");

            param= param.replaceAll("\\+"," ");

            String url= PDSPPIDB.PDSPPI + "ditdos/write?f=vo&id=pds://"+id;
            VOTableReader read= new VOTableReader();
            mon.setProgressMessage("downloading data");
            logger.log(Level.FINE, "getDataSet {0}", url);
            File f= DataSetURI.downloadResourceAsTempFile( new URL(url), 3600, mon.getSubtaskMonitor("download file") );
            mon.setProgressMessage("reading data");

            String error= PDSPPIDB.getInstance().checkXML(f);
            if ( error!=null ) {
                throw new NoDataInIntervalException(error);
            }

            QDataSet ds= read.readTable( f.toString(), mon.getSubtaskMonitor("read table") );
            if ( ds.length()==0 ) {
                throw new NoDataInIntervalException("result contains no records");
            }
            QDataSet result= DataSetOps.unbundle( ds, param );

            if ( result.property(QDataSet.DEPEND_0)==null ) {
                QDataSet bds= (QDataSet) ds.property(QDataSet.BUNDLE_1);
                int i= DataSetOps.indexOfBundledDataSet( ds, param );
                String n= (String) bds.property(QDataSet.DEPENDNAME_0,i);
                if ( n!=null ) {
                    result= Ops.link( DataSetOps.unbundle(ds,n), result );
                } else {
                    if ( i>0 ) {
                        QDataSet dep0check= DataSetOps.unbundle( ds,i-1 );
                        Units tu= SemanticOps.getUnits(dep0check);
                        if ( UnitsUtil.isTimeLocation(tu) ) {
                            result= Ops.putProperty( result, QDataSet.DEPEND_0, dep0check );
                        }
                    }
                }
            }

            if ( result.rank()>1 ) {
                if ( result.length(0)>MAX_BUNDLE_COUNT ) {
                    result= Ops.putProperty( result, QDataSet.BUNDLE_1, null );
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

        } finally {
            mon.finished();
        }
        
    }

    @Override
    public <T> T getCapability(Class<T> clazz) {
        return super.getCapability(clazz); //To change body of generated methods, choose Tools | Templates.
    }
    
}
