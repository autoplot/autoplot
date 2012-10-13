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
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.system.RequestProcessor;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.Diff;
import org.virbo.datasource.AutoplotSettings;

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
                if (ev.getPropertyName().equals(ApplicationModel.PROP_VAPFILE)) {
                    resetHistory();
                }
            }
        });
    }

    public void refreshUndoMultipleMenu(JMenu undoMultipleMenu) {
        undoMultipleMenu.removeAll();

        int lstateStackPos;
        List<StateStackElement> lstateStack;
        synchronized (this) {
            lstateStackPos= stateStackPos;
            lstateStack= new ArrayList( stateStack );
        }

        for (int i = lstateStackPos - 1; i > Math.max(0, lstateStackPos - 10); i--) {
            StateStackElement prevState = lstateStack.get(i);
            String label = prevState.deltaDesc;
            final int ii = lstateStackPos - i;
            JMenuItem item= new JMenuItem(new AbstractAction(label) {
                public void actionPerformed(ActionEvent e) {
                    undo(ii);
                }
            });
            item.setToolTipText(prevState.docString);
            if ( lstateStack.get(i-1).thumb!=null ) item.setIcon( new ImageIcon( lstateStack.get(i-1).thumb ) ); // not sure why, but...
            undoMultipleMenu.add(item);
        }
    }

    public static class StateStackElement {

        Application state;
        String deltaDesc;
        String docString; // verbose description
        BufferedImage thumb;

        public StateStackElement(Application state, String deltaDesc, String docString, BufferedImage thumb ) {
            this( state, deltaDesc, docString );
            this.thumb= thumb;
        }

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

    public synchronized void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }

    public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public synchronized void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }

    public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
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

    public synchronized void undo(int level) {
        String oldRedoLabel= getRedoLabel();
        int oldDepth= stateStackPos;
        stateStackPos -= level;
        if (stateStackPos < 0) {
            stateStackPos = 0;
        }
        if (stateStackPos > 0) {
            StateStackElement elephant = stateStack.get(stateStackPos - 1);
            ignoringUpdates = true;
            applicationModel.setRestoringState(true);
            applicationModel.restoreState(elephant.state);
            applicationModel.setRestoringState(false);
            ignoringUpdates = false;
            RequestProcessor.invokeLater( new Runnable() { public void run() {
                AutoplotUtil.reloadAll( applicationModel.getDocumentModel() );
            } } );
        }
        propertyChangeSupport.firePropertyChange(PROP_REDOLABEL, oldRedoLabel, redoLabel);
        propertyChangeSupport.firePropertyChange( PROP_DEPTH, oldDepth, stateStackPos );

    }

    public Action getRedoAction() {
        return new AbstractAction("redo") {
            public void actionPerformed(ActionEvent e) {
                redo();
            }
        };
    }

    public synchronized void redo() {
        String oldRedoLabel= getRedoLabel();
        int oldDepth= stateStackPos;
        if (stateStackPos >= stateStack.size()) {
            stateStackPos = stateStack.size() - 1;
        }
        if (stateStackPos < stateStack.size()) {
            StateStackElement elephant = stateStack.get(stateStackPos);
            ignoringUpdates = true;
            applicationModel.setRestoringState(true);
            applicationModel.restoreState(elephant.state);
            applicationModel.setRestoringState(false);
            ignoringUpdates = false;
            stateStackPos++;
        }
        propertyChangeSupport.firePropertyChange(PROP_REDOLABEL, oldRedoLabel, redoLabel);
        propertyChangeSupport.firePropertyChange( PROP_DEPTH, oldDepth, stateStackPos );
    }

    public void pushState(PropertyChangeEvent ev) {
        pushState(ev,null);
    }

    /**
     * provide a human-readable description of the given diffs.
     * @param diffs list of differences to describe.
     * @param element really only provided to contain the output.
     * @return
     */
    private StateStackElement describeChanges( List<Diff> diffs, StateStackElement element ) {

        String docString;
        String labelStr;

        StringBuilder docBuf = new StringBuilder();
        int count = 0;
        boolean axisRangeOnly = true;
        boolean zaxisRangeOnly = true;
        boolean axisAuto = false;
        boolean timeRange= false;
        String focus=null;

        for (Diff s : diffs) {
            if (s.getDescription().contains("plotDefaults")) {
                continue;
            }
            String thisDiffFocus=null;
            int i= s.propertyName().indexOf(".");
            if ( i>-1 ) thisDiffFocus= s.propertyName().substring(0,i);
            if ( focus==null ) {
                focus= thisDiffFocus;
            } else {
                if ( !focus.equals(thisDiffFocus) && !s.propertyName().equals("timeRange") ) {
                    focus=""; // indicate there is no one element
                } else if ( s.propertyName().equals("timeRange") ) {
                    timeRange= true;
                }
            }
            count++;
            docBuf.append("<br>");
            docBuf.append(s.getDescription());
            if (s.propertyName().endsWith("axis.range") || s.propertyName().equals("timeRange") ) {
                if (s.propertyName().endsWith("zaxis.range")) {
                    axisRangeOnly = false;
                } else {
                    zaxisRangeOnly = false;
                }
            } else {
                if (s.propertyName().endsWith("autoRange")) {
                    axisAuto = true;
                } else {
                    axisRangeOnly = false;
                    zaxisRangeOnly = false;
                }
            }
        }
        if (focus==null ) focus= "";

        docString = docBuf.length() > 4 ? docBuf.substring(4) : "";
        docString = "<html>" + docString + "</html>";
        if (diffs.isEmpty()) {
            //state.diffs(elephant.state);  for debugging
            element.deltaDesc = "unidentified change";
            element.docString = "change was detected but could not be identified.";
            return element;
        } else if (zaxisRangeOnly && focus.length()>0 && count > 1) {
            if (axisAuto) {
                labelStr = focus + " first Z range change";
            } else {
                labelStr = focus + " Z range change"; // (this shouldn't happen, because count will equal 1.)
            }
        } else if (axisRangeOnly && focus.length()>0 && count > 1) {
            if (axisAuto) {
                labelStr = focus + " first range change";
            } else {
                labelStr = focus + " range changes";
            }
        } else if (count > 3) {
            labelStr = "" + count + " changes";
        } else {
            StringBuilder buf = new StringBuilder();
            for (Diff s : diffs) {
                if (s.getDescription().contains("plotDefaults")) {
                    continue;
                }
                buf.append(", ").append(s.getLabel());
            }
            labelStr = buf.length() > 2 ? buf.substring(2) : "";
        }
        if (labelStr.length() > 30) {
            StringTokenizer tok = new StringTokenizer(labelStr, ".,[", true);
            StringBuilder buf = new StringBuilder();
            while (tok.hasMoreTokens()) {
                String ss = tok.nextToken();
                buf.append(ss.substring(0, Math.min(ss.length(), 12)));
            }
            labelStr = buf.toString();
        }

        element.deltaDesc = labelStr;
        element.docString = docString;
        return element;
    }

    public static final String PROP_SIZE_LIMIT="sizeLimit";

    private int sizeLimit=50;

    public int getSizeLimit() {
        return sizeLimit;
    }

    public void setSizeLimit(int size) {
        int oldSize= this.sizeLimit;
        this.sizeLimit = size;
        removeOldStates();
        this.propertyChangeSupport.firePropertyChange( PROP_SIZE_LIMIT, oldSize, size );
    }



    /**
     * remove old states from the bottom of the stack, adjusting stateStackPos as well.
     */
    private synchronized void removeOldStates( ) {
        int len=sizeLimit;
        while ( stateStack.size()>len ) {
            stateStack.remove(0);
            stateStackPos--;
        }
    }

    public void pushState( PropertyChangeEvent ev, String label ) {
        if (ignoringUpdates) {
            return;
        }
        Application state = applicationModel.createState(false);
        BufferedImage thumb= applicationModel.getThumbnail(50);
        int oldDepth;
        synchronized (this) {
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
            StateStackElement element= new StateStackElement( state, labelStr, docString );

            if (elephant != null) {
                List<Diff> diffss = elephant.state.diffs(state); //TODO: documentation/getDescription seem to be inverses.  All state changes should be described in the forward direction.
                element= describeChanges( diffss, element );
                if ( label!=null && element.deltaDesc.endsWith(" changes" ) ) {
                    element.deltaDesc= label;
                }
            }

            oldDepth= stateStackPos;

            element.thumb= thumb;
            stateStack.add(stateStackPos, element );

            while (stateStack.size() > (1 + stateStackPos)) {
                stateStack.removeLast();
            }
            stateStackPos++;

            removeOldStates();
        }

        if ( saveStateDepth>0 ) {
            long t0= System.currentTimeMillis();
            File f2= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA), "state/" );
            if ( !f2.exists() ) {
                boolean ok= f2.mkdirs();
                if ( !ok ) {
                    throw new RuntimeException("unable to create folder "+ f2 );
                }
            }
            File f3= new File( f2, TimeParser.create( "state_$Y$m$d_$H$M$S.vap.gz" ).format( TimeUtil.now(), null ) );
            try {
                OutputStream out= new GZIPOutputStream( new FileOutputStream(f3) );
                StatePersistence.saveState( out, state, "");
                out.close();
            } catch (IOException ex) {
                Logger.getLogger(UndoRedoSupport.class.getName()).log(Level.SEVERE, null, ex);
            }

            System.err.println( String.format( "saved state file in %d ms", ( System.currentTimeMillis()-t0 ) ) );
            
        }


        propertyChangeSupport.firePropertyChange(PROP_DEPTH, oldDepth, stateStackPos);

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
        int oldDepth= stateStackPos;
        stateStack = new LinkedList<StateStackElement>();
        stateStackPos = 0;
        propertyChangeSupport.firePropertyChange( PROP_DEPTH, oldDepth, stateStackPos );
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

    public static final String PROP_DEPTH = "depth";

    public int getDepth() {
        return stateStackPos;
    }

    /**
     * the number of states to keep in the states folder.  Presently this is only implemented so that 0 disables the feature.
     */
    public static final String PROP_SAVE_STATE_DEPTH= "saveStateDepth";
    private int saveStateDepth= 0;

    public int getSaveStateDepth() {
        return saveStateDepth;
    }

    public void setSaveStateDepth( int depth ) {
        this.saveStateDepth= depth;
    }

    /**
     * allow scripts to peek into undo/redo stack for debugging.
     * @param pos
     * @return
     */
    public StateStackElement peekAt( int pos ) {
        return stateStack.get(pos);
    }

    /**
     * used for feedback.
     * @param i
     * @return
     */
    public String getLongUndoDescription( int i ) {
        //if (  stateStack.get(i).deltaDesc.matches("(\\d+) changes") ) {
            List<Diff> diffss = stateStack.get(i).state.diffs(stateStack.get(i-1).state);
            StringBuffer docBuf= new StringBuffer();
            for ( int j=0; j<diffss.size(); j++ ) {
                Diff s= diffss.get(j);
                if ( s.getDescription().contains("plotDefaults" ) ) continue;
                if ( j>0 ) docBuf.append(";\n");
                docBuf.append(s.getDescription());
                
            }
            return docBuf.toString();
        //}
    }
}
