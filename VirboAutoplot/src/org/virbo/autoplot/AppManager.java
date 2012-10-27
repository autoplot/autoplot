/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/**
 * Count the number of open applications and call exit when there are zero.
 * 
 * @author jbf
 */
public class AppManager {

    private static AppManager instance;

    public synchronized static AppManager getInstance() {
        if ( instance==null ) {
            instance= new AppManager();
        }
        return instance;
    }

    List<Object> apps= new ArrayList();

    public void addApplication( Object app ) {
        this.apps.add(app);
        if ( app instanceof JFrame ) {
            ((JFrame)app).setDefaultCloseOperation( JFrame.DO_NOTHING_ON_CLOSE );
        }
    }

    public void closeApplication( Object app ) {
        if ( app instanceof AutoplotUI ) { // there's a bug here--we need to associate just with autoplot app.
            if ( requestQuit() ) {
                this.apps.remove(app);
                if ( this.apps.isEmpty() ) {
                    quit();
                }
            }
        } else {
            this.apps.remove(app);
            if ( this.apps.isEmpty() ) {
                quit();
            }
        }
    }

    public Object getApplication( int i ) {
        return this.apps.get(i);
    }

    public void quit(  ) {
        System.exit(0); //TODO: findbugs DM_EXIT--and I wonder what happens when Autoplot is used on a Tomcat web server?  Otherwise this is appropriate for swing apps.
    }

    /**
     * returns true if quit can be called, exiting the program.  If the callback throws an exception, then a warning is displayed.  I expect
     * this will often occur in scripts.
     * @return
     */
    public boolean requestQuit() {
        System.err.println("request quit");
        boolean okay= true;
        for ( Entry<String,CloseCallback> ent: closeCallbacks.entrySet() ) {
            try {
                okay= okay && ent.getValue().checkClose();
            } catch ( Exception e ) {
                Object parent = this.apps.size()>0 ? this.apps.get(0) : null;
                if ( ! ( parent instanceof Component ) ) {
                    parent=null;
                }
                JOptionPane.showMessageDialog( (Component)parent, String.format( "<html>Unable to call closeCallback id=\"%s\",<br>because of exception:<br>%s", ent.getKey(), e ) );
            }
        }
        return okay;
    }

    public WindowListener getWindowListener( final Object app, final Action closeAction ) {
        return new WindowListener() {

            public void windowOpened(WindowEvent e) {
            }

            public void windowClosing(WindowEvent e) {
                boolean okay= requestQuit();
                if ( !okay ) {
                    return;
                }
                if ( closeAction!=null ) {
                    closeAction.actionPerformed( new ActionEvent(this,e.getID(),"close") );
                }
                e.getWindow().dispose();
            }

            public void windowClosed(WindowEvent e) {
                closeApplication(app);
            }

            public void windowIconified(WindowEvent e) {
            }

            public void windowDeiconified(WindowEvent e) {
            }

            public void windowActivated(WindowEvent e) {
            }

            public void windowDeactivated(WindowEvent e) {
            }
        };

    }

    public WindowListener getWindowListener( final Object app ) {
        return getWindowListener(app,null);
    }

    /**
     * return the number of running applications this AppManager is managing.
     * @return
     */
    public int getApplicationCount() {
        return apps.size();
    }

    /**
     * anything that wants an opportunity to cancel the close request should register here.
     */
    public interface CloseCallback {
        /**
         * return true if the callback okays the close.
         * @return
         */
        boolean checkClose();
    }

    HashMap<String,CloseCallback> closeCallbacks= new LinkedHashMap();

    public synchronized void addCloseCallback( String id, CloseCallback c ) {
        closeCallbacks.remove(id);
        closeCallbacks.put(id,c);
    }
}
