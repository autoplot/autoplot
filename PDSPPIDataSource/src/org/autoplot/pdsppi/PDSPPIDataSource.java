/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.pdsppi;

import java.io.File;
import java.net.URI;
import java.net.URL;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURI;
import org.virbo.spase.VOTableReader;

/**
 *
 * @author jbf
 */
public class PDSPPIDataSource extends AbstractDataSource {

    PDSPPIDataSource( URI uri ) {
        super(uri);
    }
    
    @Override
    public org.virbo.dataset.QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        String id= (String) getParams().get("id");
        String param= (String) getParams().get("ds");
        String url= "http://ppi.pds.nasa.gov/ditdos/write?f=vo&id="+id;
        VOTableReader read= new VOTableReader();
        File f= DataSetURI.downloadResourceAsTempFile( new URL(url), mon );
        QDataSet ds= read.readTable( f.toString(), mon );
        return DataSetOps.unbundle( ds, param );
        
    }
    
}
