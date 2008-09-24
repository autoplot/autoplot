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
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.datasource.DataSourceFormat;

/**
 * Format the data into das2streams.
 * @author jbf
 */
public class Das2StreamDataSourceFormat implements DataSourceFormat {

    public void formatData(File url, java.util.Map<String, String> params, QDataSet data, ProgressMonitor mon) throws Exception {
        DataSetURL.URLSplit split = DataSetURL.parse(url.toURI().toString());
        if (split.ext.equals(".qds")) {
            new org.virbo.qstream.SimpleStreamFormatter().format( data, new FileOutputStream(url), true );
        } else {
            if (data.rank() == 2) {
                TableDataSet tds = TableDataSetAdapter.create(data);
                FileOutputStream fo = new FileOutputStream(url);
                TableUtil.dumpToAsciiStream(tds, fo);
                fo.close();
            } else if (data.rank() == 1) {
                VectorDataSet vds = VectorDataSetAdapter.create(data);
                FileOutputStream fo = new FileOutputStream(url);
                VectorUtil.dumpToAsciiStream(vds, fo);
                fo.close();
            }
        }
    }
}
