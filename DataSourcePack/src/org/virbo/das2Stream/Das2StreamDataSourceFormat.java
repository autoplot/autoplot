/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.das2Stream;

import org.das2.dataset.TableDataSet;
import org.das2.dataset.TableUtil;
import org.das2.dataset.VectorDataSet;
import org.das2.dataset.VectorUtil;
import java.io.File;
import java.io.FileOutputStream;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.das2.dataset.TableDataSetAdapter;
import org.das2.dataset.VectorDataSetAdapter;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.DataSourceFormat;

/**
 * Format the data into das2streams.
 * @author jbf
 */
public class Das2StreamDataSourceFormat implements DataSourceFormat {

    public void formatData( String url, QDataSet data, ProgressMonitor mon) throws Exception {

        URISplit split = URISplit.parse(url);
        java.util.Map<String, String> params= URISplit.parseParams(split.params);

        boolean binary= "binary".equals( params.get( "type" ) );
        if (split.ext.equals(".qds")) {
            FileOutputStream fo=null;
            try {
                fo= new FileOutputStream( new File( split.resourceUri ) );
                if ( binary ) {
                    new org.virbo.qstream.SimpleStreamFormatter().format( data, fo, false );
                } else {
                    new org.virbo.qstream.SimpleStreamFormatter().format( data, fo, true );
                }
            } finally {
                if ( fo!=null ) fo.close();
            }
        } else {
            if (data.rank()==3 ) {
                TableDataSet tds = TableDataSetAdapter.create(data);
                FileOutputStream fo = new FileOutputStream( new File( split.resourceUri ) );
                if ( binary ) {
                    TableUtil.dumpToBinaryStream(tds, fo);
                } else {
                    TableUtil.dumpToAsciiStream(tds, fo);
                }
                fo.close();
            } else if (data.rank() == 2) {
                TableDataSet tds = TableDataSetAdapter.create(data);
                FileOutputStream fo = new FileOutputStream( new File( split.resourceUri ) );
                if ( binary ) {
                    TableUtil.dumpToBinaryStream(tds, fo);
                } else {
                    TableUtil.dumpToAsciiStream(tds, fo);
                }
                fo.close();
            } else if (data.rank() == 1) {
                VectorDataSet vds = VectorDataSetAdapter.create(data);
                FileOutputStream fo = new FileOutputStream( new File( split.resourceUri ) );
                if ( binary ) {
                    VectorUtil.dumpToBinaryStream(vds, fo);
                } else {
                    VectorUtil.dumpToAsciiStream(vds, fo);
                }
                fo.close();
            }
        }
    }

    public boolean canFormat(QDataSet ds) {
        return true; // at least it should, so if it can't it's a bug elsewhere.
    }

    public String getDescription() {
        return "QDataSet transfer format";
    }

}
