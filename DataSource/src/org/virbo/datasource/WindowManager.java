/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource;

import java.awt.Container;
import java.awt.Dialog;
import java.awt.Window;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Keep track of window positions
 * @author jbf
 */
public class WindowManager {
    
    private static final Logger logger= Logger.getLogger("apdss.windowmanager");
    
    private static final WindowManager instance= new WindowManager();
    
    public static WindowManager getInstance() {
        return instance;
    }
    
    public void recallWindowSizePosition( Window window ) {
        Container c= window.getParent();
        String name= window.getName(); 
        final Preferences prefs= Preferences.userNodeForPackage(WindowManager.class);
        if ( prefs.getInt( "window."+name+".screenwidth", 0 )==java.awt.Toolkit.getDefaultToolkit().getScreenSize().width ) {
            int w= prefs.getInt( "window."+name+".width", -9999 );
            int h= prefs.getInt( "window."+name+".height", -9999 );
            if ( w>10 && h>10 ) {
                window.setSize( w, h );
            }   
            if ( c!=null ) {
                int x= prefs.getInt( "window."+name+".rlocationx", -9999 );
                int y= prefs.getInt( "window."+name+".rlocationy", -9999 );        
                if ( x>-9999 && y>-9999 ) {
                    window.setLocation( c.getX()+x, c.getY()+y );
                }
            } else {
                int x= prefs.getInt( "window."+name+".locationx", -9999 );
                int y= prefs.getInt( "window."+name+".locationy", -9999 );        
                if ( x>-9999 && y>-9999 ) {
                    window.setLocation( x, y );
                }
            }
        }
    }
    
    /**
     * record the final position of the dialog.
     * @param window 
     */
    public void recordWindowSizePosition( Window window ) {
        int x= window.getLocation().x;
        int y= window.getLocation().y;
        int w= window.getWidth();
        int h= window.getHeight();
        
        Container c= window.getParent();
        String name= window.getName(); 
        
        final Preferences prefs= Preferences.userNodeForPackage(WindowManager.class);
        logger.log( Level.FINE, "saving last location {0} {1} {2} {3}", new Object[]{x, y, h, w});
        // so that we know these settings are still valid.
        prefs.putInt( "window."+name+".screenwidth", java.awt.Toolkit.getDefaultToolkit().getScreenSize().width ); 
        if ( c!=null ) {
            prefs.putInt( "window."+name+".rlocationx", x-c.getX() );
            prefs.putInt( "window."+name+".rlocationy", y-c.getY() );
        } else {
            prefs.putInt( "window."+name+".locationx", x );
            prefs.putInt( "window."+name+".locationy", y );            
        }
        prefs.putInt( "window."+name+".width", w );
        prefs.putInt( "window."+name+".height", h );
        
    }

    /**
     * convenient method for showing dialog which is modal.
     * @param dia the dialog that is set to be modal.
     */
    public void showModalDialog( Dialog dia ) {
        if ( !dia.isModal() ) throw new IllegalArgumentException("dialog should be modal");
        WindowManager.getInstance().recallWindowSizePosition(dia);
        dia.setVisible(true);
        WindowManager.getInstance().recordWindowSizePosition(dia);        
    }

}
