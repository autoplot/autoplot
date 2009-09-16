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
import org.virbo.dataset.TableDataSetAdapter;
import org.virbo.dataset.VectorDataSetAdapter;
import org.virbo.datasource.URLSplit;
import org.virbo.datasource.datasource.DataSourceFormat;

/**
 * Format the data into das2streams.
 * @author jbf
 */
public class Das2StreamDataSourceFormat implements DataSourceFormat {

    public void formatData( String url, QDataSet data, ProgressMonitor mon) throws Exception {

        URLSplit split = URLSplit.parse(url.toString());
        java.util.Map<String, String> params= URLSplit.parseParams(split.params);

        boolean binary= "binary".equals( params.get( "type" ) );
        if (split.ext.equals(".qds")) {
            if ( binary ) {
                new org.virbo.qstream.SimpleStreamFormatter().format( data, new FileOutputStream( new File( split.resourceUri ) ), false );
            } else {
                new org.virbo.qstream.SimpleStreamFormatter().format( data, new FileOutputStream(  new File( split.resourceUri ) ), true );
            }
        } else {
            if (data.rank() == 2) {
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
}
