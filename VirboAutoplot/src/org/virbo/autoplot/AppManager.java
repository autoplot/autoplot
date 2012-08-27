/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;

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
    }

    public void closeApplication( Object app ) {
        this.apps.remove(app);
        if ( this.apps.isEmpty() ) {
            System.exit(0); //TODO: findbugs DM_EXIT--and I wonder what happens when Autoplot is used on a Tomcat web server?  Otherwise this is appropriate for swing apps.
        }
    }

    public Object getApplication( int i ) {
        return this.apps.get(i);
    }

    public void quit(  ) {
        System.exit(0);
    }

    public WindowListener getWindowListener( final Object app, final Action closeAction ) {
        return new WindowListener() {

            public void windowOpened(WindowEvent e) {
            }

            public void windowClosing(WindowEvent e) {
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
}
