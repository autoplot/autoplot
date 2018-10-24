/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.aggregator;

import java.io.OutputStream;
import java.net.URI;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.fsm.FileStorageModel;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceFormat;
import org.autoplot.datasource.URISplit;
import org.das2.qds.ops.Ops;

/**
 * Format the data by breaking it up into an aggregated pile of files.
 * @author jbf
 */
public class AggregatingDataSourceFormat implements DataSourceFormat {

    private static final Logger logger= LoggerManager.getLogger("apdss.format.agg");
    
    @Override
    public void formatData(String uri, QDataSet data, ProgressMonitor mon) throws Exception {
        String surl = DataSetURI.fromUri( new URI( uri ) );
        URISplit split= URISplit.parse(uri);
        Map<String,String> params= URISplit.parseParams(split.params);
        String str= params.remove(URISplit.PARAM_TIME_RANGE);
        String delegateParams= URISplit.formatParams(params);
        
        int i= AggregatingDataSourceFactory.splitIndex(surl);
        String base= surl.substring(0,i);
        
        FileStorageModel fsm = AggregatingDataSourceFactory.getFileStorageModel(surl);
        QDataSet dep0= (QDataSet) data.property(QDataSet.DEPEND_0);
        if ( dep0==null ) {
            throw new IllegalArgumentException("data must have DEPEND_0 property to be exported to aggregation");
        }
        DatumRange lviewRange= DataSetUtil.asDatumRange( Ops.extent(dep0) );
        DatumRange limit= str==null ? null : DatumRangeUtil.parseTimeRange(str);
        
        String[] ss = fsm.generateNamesFor( lviewRange );
        for ( String s: ss ) {
            DatumRange dr1= fsm.getRangeFor(s);
            if ( limit!=null && !limit.intersects(dr1) ) {
                logger.log(Level.FINE,"skipping because outside timerange {0}", s);
                continue;
            }
            QDataSet data1= Ops.trim( data, DataSetUtil.asDataSet(dr1.min()), DataSetUtil.asDataSet(dr1.max() ) );
            if ( data1.length()>0 ) {
                logger.log(Level.FINE, "formatting {0}", s);
                StringBuilder uri1= new StringBuilder(base).append(s);
                if ( delegateParams.length()>0 ) uri1.append( "?").append( delegateParams );
                String uri2= uri1.toString();
                DataSourceFormat df= DataSetURI.getDataSourceFormat(new URI(uri2) );
                df.formatData( uri2, data1, mon.getSubtaskMonitor( i, i+1, base ) );
            }
        }
    }

    @Override
    public boolean canFormat(QDataSet ds) {
        return true;
    }

    @Override
    public String getDescription() {
        return "Aggregating Data Source Format";
    }

    //@Override
    public boolean streamData(Map<String, String> params, Iterator<QDataSet> data, OutputStream out) throws Exception {
        return false;
    }
    
}
