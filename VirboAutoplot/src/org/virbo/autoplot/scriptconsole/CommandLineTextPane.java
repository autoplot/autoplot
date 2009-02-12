/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.scriptconsole;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;

/**
 * command line component with history and keybindings.
 * @author jbf
 */
public class CommandLineTextPane extends JTextPane {

    public CommandLineTextPane() {
        ActionMap map = getActionMap();

        Action evalAction= new AbstractAction("eval") {
            public void actionPerformed( ActionEvent e ) {
                fireActionPerformed( e );
            }
        };

        map.put("eval", evalAction);
        setActionMap(map);

        InputMap imap= getInputMap();
        imap.put( KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "eval" );
 
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
