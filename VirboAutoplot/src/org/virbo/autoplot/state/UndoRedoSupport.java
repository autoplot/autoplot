/*
 * UndoRedoSupport.java
 *
 * Created on August 8, 2007, 7:44 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.autoplot.state;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedList;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.virbo.autoplot.ApplicationModel;

/**
 *
 * @author jbf
 */
public class UndoRedoSupport {
    
    ApplicationModel applicationModel;
    
    /** Creates a new instance of UndoRedoSupport */
    public UndoRedoSupport( ApplicationModel applicationModel ) {
        this.applicationModel= applicationModel;
        applicationModel.addPropertyChangeListener( new PropertyChangeListener() {
            public void propertyChange( PropertyChangeEvent ev ) {
                if ( ev.getPropertyName().equals( ApplicationModel.PROPERTY_FILE ) ) {
                    resetHistory();
                }
            }
        } );
    }
    
    class StateStackElement {
        ApplicationState state;
        String deltaDesc;
        public StateStackElement( ApplicationState state, String deltaDesc ) {
            this.state= state;
            this.deltaDesc= deltaDesc;
        }
        public String toString() {
            return deltaDesc;
        }
    }
    
    LinkedList<StateStackElement> stateStack= new LinkedList<StateStackElement>();
    
    /**
     * points at the last saved state index + 1;
     */
    int stateStackPos=0;
    
    public Action getUndoAction() {
        return new AbstractAction("Undo") {
            public void actionPerformed( ActionEvent e ) {
                stateStackPos--;
                if ( stateStackPos<0 ) stateStackPos=0;
                if ( stateStackPos>0 ) {
                    StateStackElement elephant= stateStack.get( stateStackPos-1 );
                    ignoringUpdates= true;
                    applicationModel.restoreState(elephant.state, false, true);
                    ignoringUpdates= false;
                }
            }
            
        };
    }
    
    public Action getRedoAction() {
        return new AbstractAction("redo") {
            public void actionPerformed( ActionEvent e ) {
                if ( stateStackPos>=stateStack.size() ) stateStackPos=stateStack.size()-1;
                if ( stateStackPos<stateStack.size() ) {
                    StateStackElement elephant= stateStack.get( stateStackPos );
                    ignoringUpdates= true;
                    applicationModel.restoreState(elephant.state, false, true);
                    ignoringUpdates= false;
                    stateStackPos++;
                }
            }
        };
    }
    
    public void pushState( PropertyChangeEvent ev ) {
        if ( ignoringUpdates ) return;
        
        ApplicationState state= applicationModel.createState(false);
        StateStackElement elephant;
        
        if ( stateStackPos>0 ) {
            elephant= stateStack.get(stateStackPos-1);
        } else {
            elephant= null;
        }
        
        if ( elephant!=null && state.equals(elephant.state) ) return;
        
        String labelStr= "initial";
        if ( elephant!=null ) {
            labelStr= state.diffs(elephant.state);
        }
        
        stateStack.add(stateStackPos,new StateStackElement(state,labelStr) );
        
        while( stateStack.size() > (1+stateStackPos) ) {
            stateStack.removeLast();
        }
        stateStackPos++;
    }

    /**
     * returns a label describing the undo operation, or null if the operation
     * doesn't exist.
     */
    public String getUndoLabel() {
        if ( stateStackPos>1 ) {
            return "Undo "+stateStack.get(stateStackPos-1).deltaDesc;
        } else {
            return null;
        }
    }
    
        /**
     * returns a label describing the redo operation, or null if the operation
     * doesn't exist.
     */
    public String getRedoLabel() {
        if ( stateStackPos<stateStack.size() ) {
            return "Redo "+stateStack.get(stateStackPos).deltaDesc;
        } else {
            return null;
        }
    }

    public void resetHistory() {
        stateStack= new LinkedList<StateStackElement>();
        stateStackPos= 0;
    }
    
    /**
     * Holds value of property ignoringUpdates.
     */
    private boolean ignoringUpdates;

    /**
     * Getter for property ignoringUpdates.
     * @return Value of property ignoringUpdates.
     */
    public boolean isIgnoringUpdates() {
        return this.ignoringUpdates;
    }

    /**
     * Setter for property ignoringUpdates.
     * @param ignoringUpdates New value of property ignoringUpdates.
     */
    public void setIgnoringUpdates(boolean ignoringUpdates) {
        this.ignoringUpdates = ignoringUpdates;
    }
    
}
