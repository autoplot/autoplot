
package org.autoplot;

import java.awt.Component;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import org.das2.util.LoggerManager;

/**
 * Count the number of open applications and call exit when there are zero.
 * 
 * @author jbf
 */
public class AppManager {

    private static final Logger logger= LoggerManager.getLogger("autoplot.appmanager");
    
    private static AppManager instance;

    private AppManager() {
        
    }
    
    
    public synchronized static AppManager getInstance() {
        if ( instance==null ) {
            instance= new AppManager();
        }
        return instance;
    }

    List<Object> apps= new ArrayList();

    public void addApplication( Object app ) {
        logger.log(Level.FINE, "addApplication({0})", app);        
        this.apps.add(app);
        if ( app instanceof JFrame ) {
            ((JFrame)app).setDefaultCloseOperation( JFrame.DO_NOTHING_ON_CLOSE );
        }
    }
    
    /**
     * return true if the application is registered.  This was introduced
     * so that ScriptContext could check that it hasn't been closed.  (This 
     * listens to window closing events.)
     * @param app
     * @return true if the app is still registered.
     */
    public synchronized boolean isRunningApplication( Object app ) {
        for ( Object o: apps ) {
            if ( o==app ) {
                return true;
            }
        }
        return false;
    }

    public void closeApplication( Object app ) {
        logger.log(Level.FINE, "closeApplication({0})", app);
        if ( app instanceof AutoplotUI ) { // there's a bug here--we need to associate just with autoplot app.
            boolean resetMain= false;
            if ( ScriptContext.getViewWindow()==null ) {
                resetMain= true;
            }
            if ( requestClose(app) ) {
                this.appCloseCallbacks.remove(app);        
                this.apps.remove(app);
                if ( this.apps.isEmpty() ) {
                    quit();
                }
            }
            if ( resetMain ) {
                for ( Object o: this.apps ) {
                    if ( o instanceof AutoplotUI ) {
                        ScriptContext.setView((AutoplotUI)o); //TODO: if there are running apps, this will cause problems...
                        ScriptContext.setApplicationModel(((AutoplotUI)o).applicationModel);
                    }
                }
            }
        } else {
            //TODO: why are there two branches?  These should be nothing Autoplot-specific in here.
            this.apps.remove(app);
            if ( this.apps.size()==1 && this.apps.get(0) instanceof JFrame && ( (JFrame)this.apps.get(0) ).isVisible()==false ) {
                // when the PngWalkTool is started first, AutoplotUI is also started but hidden.
                this.apps.clear();
            }
            if ( this.apps.isEmpty() ) {
                quit();
            }
        }
    }

    public Object getApplication( int i ) {
        return this.apps.get(i);
    }

    private boolean allowExit=true;
    
    /**
     * if true, then the ApplicationManager may explicitly call System.exit.
     * @return 
     */
    public boolean isAllowExit() {
        return allowExit; 
    }
    
    /**
     * some applications, like web applications and using Autoplot within Python, need to disable quitting 
     * so that System.exit is not called.  Note that once an application does not allow quitting, it can 
     * not be turned back on.
     * @param allowExit 
     */
    public void setAllowExit( boolean allowExit ) {
        if ( !this.allowExit && allowExit ) throw new IllegalArgumentException("allowExit cannot be turned on");
        this.allowExit= allowExit;
    }
      
    /**
     * quit with the exit status of 0.
     */
    public void quit(  ) {
        if ( this.allowExit ) {
            System.exit(0); //TODO: findbugs DM_EXIT--and I wonder what happens when Autoplot is used on a Tomcat web server?  Otherwise this is appropriate for swing apps.
        }
    }
    
    /**
     * quit with the exit status.  By convention, 0 means okay, and non-0 means something wrong.
     * @param status 
     */
    public void quit( int status ) {
        if ( this.allowExit ) {
            System.err.println("about to exit with status "+status);
            System.exit(status); //TODO: findbugs DM_EXIT--and I wonder what happens when Autoplot is used on a Tomcat web server?  Otherwise this is appropriate for swing apps.
            System.err.println("should not get here");
        }
    }
    

    /**
     * returns true if quit can be called, exiting the program.  If the callback throws an exception, then a warning is displayed.  I expect
     * this will often occur in scripts.
     * @return
     */
    public boolean requestQuit() {
        logger.log(Level.FINE, "requestQuit()");        
        boolean okay= true;
        for ( Entry<Object,Map<String,CloseCallback>> closeCallbacks : appCloseCallbacks.entrySet() ) {
            Object app= closeCallbacks.getKey();
            okay= okay && requestClose(app);
        }
        return okay;
    }
    
    /**
     * returns true if close can be called, exiting the program.  If the callback throws an exception, then a warning is displayed.  I expect
     * this will often occur in scripts.
     * @param app the app that is closing.
     * @return true if the callback okays the close.
     */
    public boolean requestClose( Object app ) {
        logger.log(Level.FINE, "requestClose({0})", app);        
        boolean okay= true;
        Map<String,CloseCallback> closeCallbacks= appCloseCallbacks.get(app);
        if ( closeCallbacks==null ) return true;
        for ( Entry<String,CloseCallback> ent: closeCallbacks.entrySet() ) {
            try {
                if ( app instanceof Frame && ((Frame)app).isDisplayable() ) GuiSupport.raiseApplicationWindow( (Frame)app );
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
            @Override
            public void windowOpened(WindowEvent e) {
            }
            @Override
            public void windowClosing(WindowEvent e) {
                boolean okay= requestClose(app);
                if ( !okay ) {
                    return;
                } else {
                    appCloseCallbacks.remove(app); // kludge, otherwise this is called twice.
                }
                if ( closeAction!=null ) {
                    closeAction.actionPerformed( new ActionEvent(this,e.getID(),"close") );
                }
                e.getWindow().dispose();
            }
            @Override
            public void windowClosed(WindowEvent e) {
                closeApplication(app);
            }
            @Override
            public void windowIconified(WindowEvent e) {
            }
            @Override
            public void windowDeiconified(WindowEvent e) {
            }
            @Override
            public void windowActivated(WindowEvent e) {
            }
            @Override
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
         * @return true if the callback okays the close.
         */
        boolean checkClose();
    }

    HashMap<Object,Map<String,CloseCallback>> appCloseCallbacks= new LinkedHashMap();
    //HashMap<String,CloseCallback> appCloseCallbacks= new LinkedHashMap();

    /**
     * add a close callback which can prevent a close.  The callback
     * can open a dialog requesting that the user save a file, for example.
     * @param app to associate the callback.
     * @param id
     * @param c 
     */
    public synchronized void addCloseCallback( Object app, String id, CloseCallback c ) {
        Map<String,CloseCallback> appCallbacks= this.appCloseCallbacks.get(app);
        if ( appCallbacks==null ) {
            appCallbacks= new LinkedHashMap();
        }
        appCallbacks.put(id,c);
        this.appCloseCallbacks.put( app, appCallbacks );
    }
}
