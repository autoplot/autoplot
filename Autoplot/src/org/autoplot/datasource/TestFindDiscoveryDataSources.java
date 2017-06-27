/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.datasource;

import java.util.List;
import javax.swing.JOptionPane;
import org.das2.components.DasProgressPanel;

/**
 * Some dataSources allow for data discovery, so the user could push a button
 * to start exploring.  This experiments with this.
 * @author jbf
 */
public class TestFindDiscoveryDataSources {
    public static void main( String[] args ) {
        DataSourceRegistry registry= DataSourceRegistry.getInstance();
        registry.discoverFactories();
        registry.discoverRegistryEntries();
        
        List<String> exts;

        System.err.println( "== Autoplot can format to ==");
        exts= registry.getFormatterExtensions();
        for ( String ext: exts ) {
            Object s= DataSourceRegistry.getInstance().getFormatByExt(ext);
            System.err.printf("%s: %s\n", ext , s);
        }

        System.err.println( "== Autoplot can read ==");
        exts= registry.getSourceExtensions();
        for ( String ext: exts ) {
            Object s= DataSourceRegistry.getInstance().getSource(ext);
            System.err.printf("%s: %s\n", ext , s);
        }

        System.err.println( "== Autoplot can discover ==");
        exts= registry.getSourceEditorExtensions();
        for ( String ext: exts ) {
            String uri= "vap+" + ext.substring(1) + ":";
            try {
                DataSourceEditorPanel p = (DataSourceEditorPanel) DataSourceEditorPanelUtil.getEditorByExt( ext );
                if ( p.reject(uri) ) {
                    System.err.printf("           (nope) %s: %s\n", ext, p);
                } else {
                    System.err.printf("%s: %s\n", ext, p);
                    //for fun, let's try to enter the GUI.
                    p.prepare( uri, null, DasProgressPanel.createFramed("entering "+ext) );
                    p.setURI(uri);
                    JOptionPane.showMessageDialog( null, p.getPanel() );
                }
            } catch (Exception ex) {
                System.err.printf("           (exception) %s  %s\n", ext, ex );
            }
        }



    }
}
