
package org.autoplot.das2Stream;

import org.das2.dataset.TableDataSet;
import org.das2.dataset.TableUtil;
import org.das2.dataset.VectorDataSet;
import org.das2.dataset.VectorUtil;
import java.io.File;
import java.io.FileOutputStream;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.das2.dataset.TableDataSetAdapter;
import org.das2.dataset.VectorDataSetAdapter;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.DataSourceFormat;

/**
 * Format the data into das2streams and QStreams.
 * @author jbf
 */
public class Das2StreamDataSourceFormat implements DataSourceFormat {

    @Override
    public void formatData( String url, QDataSet data, ProgressMonitor mon) throws Exception {

        URISplit split = URISplit.parse(url);
        java.util.Map<String, String> params= URISplit.parseParams(split.params);

        boolean binary= "binary".equals( params.get( "type" ) );
         if (split.ext.equals(".qds")) {
            FileOutputStream fo=null;
            try {
                fo= new FileOutputStream( new File( split.resourceUri ) );
                if ( SemanticOps.isBundle(data) ) {
                    new org.das2.qstream.BundleStreamFormatter().format( data, fo, !binary );
                } else {
                    new org.das2.qstream.SimpleStreamFormatter().format( data, fo, !binary );
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

    @Override
    public boolean canFormat(QDataSet ds) {
        return true; // at least it should, so if it can't it's a bug elsewhere.
    }

    @Override
    public String getDescription() {
        return "QDataSet QStream and Das2Stream transfer format";
    }

}
