/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import org.virbo.datasource.DataSourceRegistry;
import static org.virbo.autoplot.ScriptContext.*;

/**
 * tests of the Java-based cdf reader
 * @author jbf
 */
public class Test032 {
    public static void main( String[] args ) {

        getDocumentModel().getOptions().setAutolayout(false);
        getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

        DataSourceRegistry.getInstance().registerExtension( "org.virbo.cdf.CdfJavaDataSourceFactory", "cdf", "CDF files using java based reader" );

        Test012.main( new String[] { "032" } );
    }
}
