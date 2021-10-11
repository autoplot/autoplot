
package org.autoplot.asdatasource;

import java.util.Map;
import org.autoplot.datasource.DataSourceFormat;
import org.autoplot.datasource.URISplit;
import org.das2.graph.Auralizor;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.das2.qds.ops.Ops;
import org.das2.util.monitor.ProgressMonitor;

/**
 * "format" the data by streaming it out to the sound card.
 * @author jbf
 */
public class AudioSystemDataSourceFormat implements DataSourceFormat {

    
    @Override
    public void formatData(String uri, QDataSet data, ProgressMonitor mon) throws Exception {
        
        if ( data==null ) {
            throw new IllegalArgumentException( "data is null" );
        }
        
        if ( SemanticOps.isRank2Waveform(data) ) {
            data= Ops.flattenWaveform(data);
        }
        
        Auralizor auralizor= new Auralizor(data);
        
        URISplit split= URISplit.parse(uri);
        Map<String,String> params= URISplit.parseParams(split.params);
            
        boolean doscale= !"F".equals( params.get("scale") );
        
        auralizor.setScale(doscale);
        
        auralizor.playSound();
    }

    @Override
    public boolean canFormat(QDataSet ds) {
        return ds.rank()==1 || SemanticOps.isRank2Waveform(ds);
    }

    @Override
    public String getDescription() {
        return "stream data to audio system";
    }
    
}
