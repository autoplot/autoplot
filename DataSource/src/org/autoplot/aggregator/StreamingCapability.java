/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.aggregator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.capability.Streaming;

/**
 *
 * @author jbf
 */
public class StreamingCapability implements Streaming {

    private static final Logger logger= LoggerManager.getLogger("apdss.agg");
    
    URI uri;
    AggregatingDataSource dss;
    
    
    public StreamingCapability( URI uri, AggregatingDataSource dss ) {
        this.uri= uri;
        this.dss= dss;
    }
    
    @Override
    public Iterator<QDataSet> streamDataSet(ProgressMonitor mon) throws Exception {
        return new StreamIterator( mon );
    }
    
    private class StreamIterator implements Iterator<QDataSet> {

        ProgressMonitor mon;
        String[] granules;
        int igranule;
        QDataSet currentDataSet;
        int currentIndex;
        
        private StreamIterator( ProgressMonitor mon ) throws Exception {
            this.mon= mon;
            String[] ss = dss.getFsm().getBestNamesFor( dss.getViewRange(), mon.getSubtaskMonitor( dss.getSParams() ) );
            this.mon.setTaskSize( ss.length*10 );
            
            granules= ss;
            
            igranule= 0;
            currentIndex= 0;
                
            loadNext();
        }
        
        private void loadNext() throws Exception {
            
            if ( granules.length==0 ) {
                return;
            }
            
            String scompUrl = dss.getFsm().getFileSystem().getRootURI().toString() + granules[igranule];
            if (!dss.getSParams().equals("")) {
                scompUrl += "?" + dss.getSParams();
            }
            
            URI delegateUri=null;
            try {
                delegateUri= DataSetURI.getURIValid(scompUrl);
            } catch (URISyntaxException ex) {
                logger.log(Level.SEVERE, null, ex);
                throw new IllegalArgumentException(ex);
            }

            DataSource delegateDataSource = dss.delegateDataSourceFactory.getDataSource(delegateUri);            
            currentDataSet= delegateDataSource.getDataSet( mon.getSubtaskMonitor(igranule*10,igranule*10+10,"g"+igranule) );
            currentIndex= 0;
        }
        
        @Override
        public boolean hasNext() {
            if ( currentDataSet==null ) return false;
            if ( currentIndex==currentDataSet.length() ) {
                igranule++;
                if ( igranule==granules.length ) {
                    return false;
                }
                try {
                    loadNext();
                } catch ( Exception e ) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public QDataSet next()  {
            QDataSet result= currentDataSet.slice(currentIndex);
            currentIndex++;
            return result;
        }

        @Override
        public void remove() {
            
        }
        
    }

    @Override
    public String toString() {
        return "stream of "+uri;
    }
    
}
