/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.scriptconsole;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;

/**
 * Generally-useful command line component with history and keybindings.
 * @author jbf
 */
public class CommandLineTextPane extends JTextPane {

    List<String> history;
    int historyIndex;
    String pendingEntry;
    private static final int HIST_LENGTH=20;

    private String packHistoryCommands( List<String> history ) {
        StringBuilder build= new StringBuilder();
        for ( int i=0; i<history.size(); i++ ) {
            build.append(history.get(i));
            build.append("\\n");
        }
        return build.toString();
    }

    public CommandLineTextPane() {
        ActionMap map = getActionMap();

        history= new LinkedList<String>();
        historyIndex=0;
        pendingEntry="";

        Action evalAction= new AbstractAction("eval") {
            public void actionPerformed( ActionEvent e ) {
                history.add( getText() );
                while ( history.size()>HIST_LENGTH ) history.remove(0);
                historyIndex= history.size();
                pendingEntry= "";
                Preferences prefs= Preferences.userNodeForPackage( CommandLineTextPane.class );
                prefs.put( "lastCommands", packHistoryCommands( history.subList(0,historyIndex) ) );
                try {
                    prefs.flush();
                } catch ( BackingStoreException ex ) {
                    
                }
                fireActionPerformed( e );
            }
        };

        Action histNextAction= new AbstractAction("histNext") {
            public void actionPerformed( ActionEvent e ) {
                if ( historyIndex<history.size() ) {
                    historyIndex++;
                    if ( historyIndex==history.size() ) {
                        setText(pendingEntry);
                    } else {
                        setText( history.get(historyIndex) );
                    }
                }
            }
        };

        Action histPrevAction= new AbstractAction("histPrev") {
            public void actionPerformed( ActionEvent e ) {
                if ( historyIndex>0 ) { 
                   if ( historyIndex==history.size() ) {
                        pendingEntry= getText();
                   }
                   historyIndex--;
                   setText( history.get(historyIndex) );
                }
            }
        };


        map.put("eval", evalAction);
        map.put("histPrev",histPrevAction);
        map.put("histNext",histNextAction);
        
        setActionMap(map);

        InputMap imap= getInputMap();
        imap.put( KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "eval" );
        imap.put( KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "histPrev" );
        imap.put( KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "histNext" );

        loadFromPrefs();
    }

    private List<String> unpackHistoryCommands( String history ) {
        String[] hh= history.split("\\\\n");
        return Arrays.asList( hh );
    }

    private void loadFromPrefs() {
        Preferences prefs= Preferences.userNodeForPackage( CommandLineTextPane.class );
        String last= prefs.get( "lastCommands", "" );
        if ( last.trim().length()>0 ) {
            history.addAll( unpackHistoryCommands(last) );
            historyIndex= history.size();
        }
    }


    /**
     * Adds an <code>ActionListener</code> to the button.
     * @param l the <code>ActionListener</code> to be added
     */
    public synchronized void addActionListener(ActionListener l) {
        listenerList.add(ActionListener.class, l);
    }

    /**
     * Removes an <code>ActionListener</code> from the button.
     * If the listener is the currently set <code>Action</code>
     * for the button, then the <code>Action</code>
     * is set to <code>null</code>.
     *
     * @param l the listener to be removed
     */
    public synchronized void removeActionListener(ActionListener l) {
	    listenerList.remove(ActionListener.class, l);
    }

    
    /**
     * Notifies all listeners that have registered interest for
     * notification on this event type.  The event instance 
     * is lazily created using the <code>event</code> 
     * parameter.
     *
     * @param event  the <code>ActionEvent</code> object
     * @see EventListenerList
     */
    protected void fireActionPerformed(ActionEvent event) {
        // Guaranteed to return a non-null array
        Object[] listeners = listenerList.getListenerList();
        ActionEvent e = null;
        // Process the listeners last to first, notifying
        // those that are interested in this event
        for (int i = listeners.length-2; i>=0; i-=2) {
            if (listeners[i]==ActionListener.class) {
                // Lazily create the event:
                if (e == null) {
                      e = new ActionEvent( this,
                                          ActionEvent.ACTION_PERFORMED,
                                          "",
                                          event.getWhen(),
                                          event.getModifiers());
                }
                ((ActionListener)listeners[i+1]).actionPerformed(e);
            }          
        }
    }

}
