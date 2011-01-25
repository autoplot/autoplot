/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.idlsupport;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.AboutUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetSelectorSupport;

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
}
