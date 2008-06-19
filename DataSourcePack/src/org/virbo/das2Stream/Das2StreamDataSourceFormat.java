/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.das2Stream;

import edu.uiowa.physics.pw.das.dataset.TableDataSet;
import edu.uiowa.physics.pw.das.dataset.TableUtil;
import edu.uiowa.physics.pw.das.dataset.VectorDataSet;
import edu.uiowa.physics.pw.das.dataset.VectorUtil;
import java.io.File;
import java.io.FileOutputStream;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.TableDataSetAdapter;
import org.virbo.dataset.VectorDataSetAdapter;
import org.virbo.datasource.datasource.DataSourceFormat;

/**
 * Format the data into das2streams.
 * @author jbf
 */
public class Das2StreamDataSourceFormat implements DataSourceFormat {

    public void formatData(File url, QDataSet data, ProgressMonitor mon) throws Exception {
        if ( data.rank() == 2 ) {
            TableDataSet tds = TableDataSetAdapter.create( data );
            FileOutputStream fo = new FileOutputStream( url );
            TableUtil.dumpToAsciiStream(tds, fo);
            fo.close();
        } else if ( data.rank() == 1) {
            VectorDataSet vds = VectorDataSetAdapter.create(data);
            FileOutputStream fo = new FileOutputStream( url );
            VectorUtil.dumpToAsciiStream(vds, fo);
            fo.close();
        }
    }
}
