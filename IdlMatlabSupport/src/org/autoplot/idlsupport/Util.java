/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.idlsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.das2.util.AboutUtil;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.DataSetSelectorSupport;
import org.autoplot.datasource.DataSourceEditorPanel;
import org.autoplot.datasource.DataSourceEditorPanelUtil;
import org.autoplot.datasource.DataSourceRegistry;

/**
 *
 * @author jbf
 */
public class Util {
    public static boolean isMap( Object o ) {
        return o instanceof Map;
    }

    public static boolean isQDataSet( Object o ) {
        return o instanceof QDataSet;
    }
    
    public static String getPlugins(  ) {
        return DataSetSelectorSupport.getPluginsText();
    }

    /**
     * In IDL, you would say:
     * <code>Util= OBJ_NEW('IDLJavaObject$Static$Util', 'org.autoplot.idlsupport.Util')
     * print, Util.getVersions()
     * </code>
     * @return 
     */
    public static String getVersions(  ) {
        return AboutUtil.getAboutHtml();
    }
    
    /**
     * Jared at Iowa was having the problem that loggers were on by default.
     */
    public static void silenceLoggers() {
        Logger.getLogger( "das2.system" ).setLevel( Level.WARNING );
        Logger.getLogger( "das2.gui" ).setLevel( Level.WARNING );
        Logger.getLogger( "das2.graphics" ).setLevel( Level.WARNING );
        Logger.getLogger( "das2.graphics.renderer" ).setLevel( Level.WARNING );
        Logger.getLogger( "das2.dataOperations" ).setLevel( Level.WARNING );
        Logger.getLogger( "das2.filesystem" ).setLevel( Level.WARNING );
        Logger.getLogger( "das2.dasml" ).setLevel( Level.WARNING );
        Logger.getLogger( "das2" ).setLevel( Level.WARNING );
        Logger.getAnonymousLogger().setLevel( Level.WARNING );
    }
    /**
     * Jared at Iowa was having the problem that loggers were on by default.
     */
    public static void verboseLoggers() {
        Logger.getLogger( "das2.system" ).setLevel( Level.ALL );
        Logger.getLogger( "das2.gui" ).setLevel( Level.ALL );
        Logger.getLogger( "das2.graphics" ).setLevel( Level.ALL );
        Logger.getLogger( "das2.graphics.renderer" ).setLevel( Level.ALL );
        Logger.getLogger( "das2.dataOperations" ).setLevel( Level.ALL );
        Logger.getLogger( "das2.filesystem" ).setLevel( Level.ALL );
        Logger.getLogger( "das2.dasml" ).setLevel( Level.ALL );
        Logger.getLogger( "das2" ).setLevel( Level.ALL );
        Logger.getAnonymousLogger().setLevel( Level.ALL );
    }
    
    /**
     * normally a Util class is just a bunch of static methods, but its easier
     * to get at the methods if we can create an instance.
     */
    public Util() {
        
    }
    
    /**
     * return an array of the sources that can be discovered.
     * @return an array of the sources that can be discovered.
     */
    public static String[] getDiscoverySources() {
        DataSourceRegistry registry= DataSourceRegistry.getInstance();
        List<String> exts= registry.getSourceEditorExtensions();
        List<String> result= new ArrayList();
        
        for ( String ext: exts ) {
            String uri= "vap+" + ext.substring(1) + ":";
            try {
                DataSourceEditorPanel p = (DataSourceEditorPanel) DataSourceEditorPanelUtil.getEditorByExt( ext );
                if ( p.reject(uri) ) {
                    System.err.printf("           (nope) %s: %s\n", ext, p);
                } else {
                    result.add(uri);
                }
            } catch (Exception ex) {
                System.err.printf("           (exception) %s  %s\n", ext, ex );
            }
        }
        return result.toArray( new String[result.size()] );
        
    }
    
    /**
     * bring up the editor for this URI, or partial URI (vap+cdaweb:).  This
     * was introduced to test Java GUIs in IDL.
     * @param uri
     * @return
     * @throws Exception 
     */
    public static String enterEditor( String uri ) throws Exception {
        DataSourceEditorPanel p = (DataSourceEditorPanel) DataSourceEditorPanelUtil.getDataSourceEditorPanel( uri );
        if ( p.reject(uri) ) {
            System.err.printf("           (nope) %s: %s\n", uri, p);
            return uri;
        } else {
            //for fun, let's try to enter the GUI.
            p.prepare( uri, null, new NullProgressMonitor() );
            p.setURI(uri);
            JOptionPane.showMessageDialog( null, p.getPanel() );
            return p.getURI();
        }
    }
}
