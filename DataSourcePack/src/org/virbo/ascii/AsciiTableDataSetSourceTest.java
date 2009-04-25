/*
 * AsciiTableDataSetSourceTest.java
 *
 * Created on December 1, 2007, 4:00 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.ascii;

import org.das2.util.monitor.NullProgressMonitor;
import java.net.URL;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSource;

/**
 *
 * @author jbf
 */
public class AsciiTableDataSetSourceTest {
    
    /** Creates a new instance of AsciiTableDataSetSourceTest */
    public static void main(String[] args) throws Exception {
        
        DataSource ds;
        QDataSet d;
        String url;
        
        /*String url= "file:///T:/timer.dat?skip=1&time=field0&column=field1";
        DataSource ds= DataSetURL.getDataSource(new URL(url));
        QDataSet d= ds.getDataSet( new NullProgressMonitor() );
         */
        //url= "file:///N:/data/examples/asciitable/omni2_1965.dat?fixedColumns=0to11,54to60&time=field0&timeFormat=%Y %j %H";
        //url= "file:///N:/data/examples/asciitable/omni2_1965.dat?timeFormat=%Y %j %H&time=field0&column=field27";
        url= "file:///media/mini/data.backup/examples/dat/omni2_1965.dat?timeFormat=$Y+$j&skip=22";
        ds= new AsciiTableDataSourceFactory().getDataSource( new URL(url) );
        d= ds.getDataSet( new NullProgressMonitor() );
        
        System.err.println(d);
    }
    
}
