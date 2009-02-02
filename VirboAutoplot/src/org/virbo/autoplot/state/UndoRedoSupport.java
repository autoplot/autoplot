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
import java.beans.PropertyChangeSupport;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.Diff;

/**
 *
 * @author jbf
 */
public class UndoRedoSupport {

    ApplicationModel applicationModel;

    /** Creates a new instance of UndoRedoSupport */
    public UndoRedoSupport(ApplicationModel applicationModel) {
        this.applicationModel = applicationModel;
        applicationModel.addPropertyChangeListener(new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent ev) {
                if (ev.getPropertyName().equals(ApplicationModel.PROPERTY_FILE)) {
                    resetHistory();
                }
            }
        });
    }

    public void refreshUndoMultipleMenu(JMenu undoMultipleMenu) {
        undoMultipleMenu.removeAll();
        for (int i = stateStackPos - 1; i > Math.max(0, stateStackPos - 10); i--) {
            StateStackElement prevState = stateStack.get(i);
            String label = prevState.deltaDesc;
            final int ii = stateStackPos - i;
            JMenuItem item= new JMenuItem(new AbstractAction(label) {
                public void actionPerformed(ActionEvent e) {
                    undo(ii);
                }
            });
            item.setToolTipText(prevState.docString);
            undoMultipleMenu.add(item);
        }
    }

    class StateStackElement {

        Application state;
        String deltaDesc;
        String docString; // verbose description

        public StateStackElement(Application state, String deltaDesc, String docString ) {
            this.state = state;
            this.deltaDesc = deltaDesc;
            this.docString= docString;
        }

        public String toString() {
            return deltaDesc;
        }
    }
    LinkedList<StateStackElement> stateStack = new LinkedList<StateStackElement>();
    /**
     * points at the last saved state index + 1;
     */
    int stateStackPos = 0;
    protected String redoLabel = null;
    public static final String PROP_REDOLABEL = "redoLabel";
    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public Action getUndoAction() {
        return new AbstractAction("Undo") {
            public void actionPerformed(ActionEvent e) {
                undo();
            }
        };
    }

    public void undo() {
        undo(1);
    }

    public void undo(int level) {
        String oldRedoLabel= getRedoLabel();        
        stateStackPos -= level;
        if (stateStackPos < 0) {
            stateStackPos = 0;
        }
        if (stateStackPos > 0) {
            StateStackElement elephant = stateStack.get(stateStackPos - 1);
            ignoringUpdates = true;
            applicationModel.setRestoringState(true);
            applicationModel.restoreState(elephant.state, false, false);
            applicationModel.setRestoringState(false);
            ignoringUpdates = false;
        }
        propertyChangeSupport.firePropertyChange(PROP_REDOLABEL, oldRedoLabel, redoLabel);

    }

    public Action getRedoAction() {
        return new AbstractAction("redo") {

            public void actionPerformed(ActionEvent e) {
                redo();
            }
        };
    }

    public void redo() {
        if (stateStackPos >= stateStack.size()) {
            stateStackPos = stateStack.size() - 1;
        }
        if (stateStackPos < stateStack.size()) {
            StateStackElement elephant = stateStack.get(stateStackPos);
            ignoringUpdates = true;
            applicationModel.restoreState(elephant.state, false, false);
            ignoringUpdates = false;
            stateStackPos++;
        }
    }

    public void pushState(PropertyChangeEvent ev) {
        if (ignoringUpdates) {
            return;
        }
        Application state = applicationModel.createState(false);
        StateStackElement elephant;

        if (stateStackPos > 0) {
            elephant = stateStack.get(stateStackPos - 1);
        } else {
            elephant = null;
        }

        if (elephant != null && state.equals(elephant.state)) {
            return;
        }
        String labelStr = "initial";
        String docString= "initial state of application";
        if (elephant != null) {
            List<Diff> diffss = state.diffs(elephant.state);
                StringBuffer docBuf= new StringBuffer();
                for (Diff s : diffss) {
                    docBuf.append("<br>"+s.toString());
                }
                docString= docBuf.length()>4 ? docBuf.substring(4) : "";
                docString= "<html>"+docString+"</html>";

            if (diffss.size() == 0) {
                state.diffs(elephant.state);
                labelStr = "unidentified change";
                docString= "change was detected but could not be identified.";
            } else if (diffss.size() > 6) {
                labelStr = "" + diffss.size() + " changes";
            } else {
                StringBuffer buf = new StringBuffer();
                for (Diff s : diffss) {
                    buf.append(", " + s.toString());
                }
                labelStr = buf.length() > 2 ? buf.substring(2) : "";
            }
            if (labelStr.length() > 30) {
                StringTokenizer tok = new StringTokenizer(labelStr, ".,[", true);
                StringBuffer buf = new StringBuffer();
                while (tok.hasMoreTokens()) {
                    String ss = tok.nextToken();
                    buf.append(ss.substring(0, Math.min(ss.length(), 12)));
                }
                labelStr = buf.toString();
            }
        }

        stateStack.add(stateStackPos, new StateStackElement(state, labelStr,docString));

        while (stateStack.size() > (1 + stateStackPos)) {
            stateStack.removeLast();
        }
        stateStackPos++;
    }

    public String getUndoDescription() {
        if (stateStackPos > 1) {
            return stateStack.get(stateStackPos - 1).docString;
        } else {
            return null;
        }
    }
    /**
     * returns a label describing the undo operation, or null if the operation
     * doesn't exist.
     */
    public String getUndoLabel() {
        if (stateStackPos > 1) {
            return "Undo " + stateStack.get(stateStackPos - 1).deltaDesc;
        } else {
            return null;
        }
    }

    public String getRedoDescription() {
        if (stateStackPos < stateStack.size()) {
            return stateStack.get(stateStackPos).docString;
        } else {
            return null;
        }
    }

    /**
     * returns a label describing the redo operation, or null if the operation
     * doesn't exist.
     */
    public String getRedoLabel() {
        if (stateStackPos < stateStack.size()) {
            return "Redo " + stateStack.get(stateStackPos).deltaDesc;
        } else {
            return null;
        }
    }


    public void resetHistory() {
        stateStack = new LinkedList<StateStackElement>();
        stateStackPos = 0;
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
