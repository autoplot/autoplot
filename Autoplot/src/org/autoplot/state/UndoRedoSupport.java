/*
 * UndoRedoSupport.java
 *
 * Created on August 8, 2007, 7:44 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.autoplot.state;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.system.RequestProcessor;
import org.autoplot.ApplicationModel;
import org.autoplot.AutoplotUtil;
import org.autoplot.dom.Application;
import org.autoplot.dom.BindingModel;
import org.autoplot.dom.Diff;
import org.autoplot.dom.DomUtil;
import org.autoplot.dom.Plot;
import org.autoplot.datasource.AutoplotSettings;

/**
 * UndoRedoSupport keeps track of a series of application states, providing
 * "undo" and "redo" operations.
 * @author jbf
 */
public class UndoRedoSupport {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.dom.vap");
    ApplicationModel applicationModel;

    /** 
     * Creates a new instance of UndoRedoSupport 
     * @param applicationModel the model which contains basic information about any Autoplot application.
     */
    public UndoRedoSupport(ApplicationModel applicationModel) {
        this.applicationModel = applicationModel;
        applicationModel.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent ev) {
                if (ev.getPropertyName().equals(ApplicationModel.PROP_VAPFILE)) {
                    resetHistory();
                }
            }
        });
    }

    /**
     * create all the menu items for a JMenu offering undo targets.
     * @param undoMultipleMenu the menu
     */
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
                @Override
                public void actionPerformed(ActionEvent e) {
                    org.das2.util.LoggerManager.logGuiEvent(e);
                    undo(ii);
                }
            });
            item.setToolTipText(prevState.docString);
            if ( lstateStack.get(i-1).thumb!=null ) item.setIcon( new ImageIcon( lstateStack.get(i-1).thumb ) ); // not sure why, but...
            undoMultipleMenu.add(item);
        }
    }

    /**
     * A model containing a state, including a description of what changed and
     * a thumbnail.
     */
    public static class StateStackElement {

        private final Application state;
        String deltaDesc; // one-line description
        String docString; // more verbose description, describing the transition to this state.
        BufferedImage thumb;

        /**
         * create an element
         * @param state the new state
         * @param deltaDesc what changed to get to this state
         * @param docString documentation string 
         */
        public StateStackElement(Application state, String deltaDesc, String docString ) {
            this.state = state;
            this.deltaDesc = deltaDesc;
            this.docString= docString;
        }
        @Override
        public String toString() {
            return deltaDesc;
        }
    }
    
    LinkedList<StateStackElement> stateStack = new LinkedList<>();
    
    /**
     * points at the last saved state index + 1;
     */
    int stateStackPos = 0;
    private String redoLabel = null;
    
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

    /**
     * get the action to trigger undo.
     * @return action that can be attached to button.
     */
    public Action getUndoAction() {
        return new AbstractAction("Undo") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                Runnable run= new Runnable() {
                    @Override
                    public void run() {
                        undo();
                    }
                };
                new Thread( run, "undoLaterThread" ).start();
            }
        };
    }

    /**
     * undo the last state change.
     */
    public void undo() {
        undo(1);
    }

    /**
     * reset the application to an old state from the state stack.
     * @param level the number of states to undo (1 is jump to the last state).
     */
    public synchronized void undo(int level) {
        logger.log(Level.FINE, "undo {0}", level);
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
        redoLabel= getRedoLabel();
        propertyChangeSupport.firePropertyChange(PROP_REDOLABEL, oldRedoLabel, redoLabel);
        propertyChangeSupport.firePropertyChange( PROP_DEPTH, oldDepth, stateStackPos );

    }

    /**
     * get the action to trigger redo.
     * @return action that can be attached to button.
     */
    public Action getRedoAction() {
        return new AbstractAction("redo") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                Runnable run= new Runnable() {
                    @Override
                    public void run() {
                        redo();
                    }
                };
                new Thread( run, "redoLaterThread" ).start();
            }
        };
    }

    /**
     * redo the state change that was undone, popping up the state stack one position.
     */
    public synchronized void redo() {
        logger.fine("redo");        
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
        redoLabel= getRedoLabel();
        propertyChangeSupport.firePropertyChange(PROP_REDOLABEL, oldRedoLabel, redoLabel);
        propertyChangeSupport.firePropertyChange( PROP_DEPTH, oldDepth, stateStackPos );
    }

    /**
     * request that a state be pushed onto the state stack.
     * @param ev
     */
    public void pushState(PropertyChangeEvent ev) {
        pushState(ev,null);
    }

    /**
     * remove extra xaxis differences that are redundant because of bindings to dom.timerange.
     * @param dom the model, presumably containing the same plots and bindings.
     * @param diffs
     * @return
     */
    private static List<Diff> removeTimeRangeBindings( Application dom, List<Diff> diffs ) {
        dom= (Application) dom.copy();
        diffs= new ArrayList( diffs );
        List<Diff> timeRangeBound= new ArrayList();
        for (Diff s : diffs) {
            Pattern pattern= Pattern.compile("plots\\[(\\d+)\\].xaxis.range");
            Matcher m= pattern.matcher(s.propertyName());
            if ( m.matches() ) {
                try {
                    Plot p= dom.getPlots( Integer.parseInt(m.group(1) ) );
                    BindingModel bm= DomUtil.findBinding( dom, p.getXaxis(), "range", dom, "timeRange" );
                    //BindingModel bm= dom.getController().findBinding( p.getXaxis(), "range", dom, "timeRange" );
                    if ( bm!=null ) {
                        timeRangeBound.add(s);
                    }
                } catch ( IndexOutOfBoundsException ex ) {
                    logger.severe("IndexOutOfBounds error that needs to be fixed because needs synchronization");
                }
            }
        }
        diffs.removeAll(timeRangeBound);
        return diffs;
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

        diffs= removeTimeRangeBindings( this.applicationModel.getDocumentModel(), diffs );

        for (Diff s : diffs) {
            if (s.getDescription().contains("plotDefaults")) {
                continue;
            }
            String thisDiffFocus=null;
            int i= s.propertyName().indexOf('.');
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

    /**
     * name of the property containing the number of states kept
     */
    public static final String PROP_SIZE_LIMIT="sizeLimit";

    private int sizeLimit=50;

    /**
     * get the number of states which will be kept
     * @return size 
     */
    public int getSizeLimit() {
        return sizeLimit;
    }

    /**
     * set the number of states which will be kept
     * @param size 
     */
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

    /**
     * push the current state of the application onto the stack.
     * @param ev
     * @param label
     */
    public void pushState( PropertyChangeEvent ev, String label ) {
        logger.log(Level.FINE, "pushState: {0}", label);
        synchronized ( this ) {
            if (ignoringUpdates) {
                return;
            }
        }
        Application state = applicationModel.createState(false);
        BufferedImage thumb= applicationModel.getThumbnail(50);
        int oldDepth;
        int newDepth;
        
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
                if ( diffss.isEmpty() ) return;
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
            newDepth= stateStackPos;
            removeOldStates();
        } // synchronized(this)

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
            try ( OutputStream out= new GZIPOutputStream( new FileOutputStream(f3) ) ) {
                StatePersistence.saveState( out, state, "");
            } catch (IOException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }

            logger.fine( String.format( "saved state file in %d ms", ( System.currentTimeMillis()-t0 ) ) );
            
        }


        propertyChangeSupport.firePropertyChange(PROP_DEPTH, oldDepth, newDepth);

    }

    /**
     * get the longer description for the action, intended to be used for the tooltip.
     * @return a human-readable description
     */
    public synchronized String getUndoDescription() {
        if (stateStackPos > 1) {
            return stateStack.get(stateStackPos - 1).docString;
        } else {
            return null;
        }
    }
    /**
     * returns a label describing the undo operation, or null if the operation
     * doesn't exist.
     * @return the level
     */
    public synchronized String getUndoLabel() {
        if (stateStackPos > 1) {
            return "Undo " + stateStack.get(stateStackPos - 1).deltaDesc;
        } else {
            return null;
        }
    }

    /**
     * get the longer description for the action, intended to be used for the tooltip.
     * @return
     */
    public synchronized String getRedoDescription() {
        if (stateStackPos < stateStack.size()) {
            return stateStack.get(stateStackPos).docString;
        } else {
            return null;
        }
    }

    /**
     * returns a label describing the redo operation, or null if the operation
     * doesn't exist.
     * @return 
     */
    public synchronized String getRedoLabel() {
        if (stateStackPos < stateStack.size()) {
            return "Redo " + stateStack.get(stateStackPos).deltaDesc;
        } else {
            return null;
        }
    }


    /**
     * reset the history, for example after a vap file is loaded.
     */
    public void resetHistory() {
        int oldDepth;
        synchronized (this) {
            oldDepth= stateStackPos;
            stateStack = new LinkedList<>();
            stateStackPos = 0;
        }
        propertyChangeSupport.firePropertyChange( PROP_DEPTH, oldDepth, 0 );
    }
    
    /**
     * Holds value of property ignoringUpdates.
     */
    private boolean ignoringUpdates;

    /**
     * Getter for property ignoringUpdates.
     * @return Value of property ignoringUpdates.
     */
    public synchronized boolean isIgnoringUpdates() {
        return this.ignoringUpdates;
    }

    /**
     * Setter for property ignoringUpdates.
     * @param ignoringUpdates New value of property ignoringUpdates.
     */
    public synchronized void setIgnoringUpdates(boolean ignoringUpdates) {
        this.ignoringUpdates = ignoringUpdates;
    }

    /**
     * property name of the current depth
     */
    public static final String PROP_DEPTH = "depth";

    /**
     * get the current depth, where depth can be lower than the number of
     * states held, when redos can be done.
     * @return the current depth.
     */
    public synchronized int getDepth() {
        return stateStackPos;
    }

    /**
     * the number of states to keep in the states folder.  Presently this is only implemented so that 0 disables the feature.
     */
    public static final String PROP_SAVE_STATE_DEPTH= "saveStateDepth";
    private int saveStateDepth= 0;

    /**
     * get the current number of persistent states stored in autoplot_data/state
     * @return 
     */
    public int getSaveStateDepth() {
        return saveStateDepth;
    }

    /**
     * set the number of persistent states to keep, or 0 will disable the feature.
     * @param depth zero or the number of states to keep.
     */
    public void setSaveStateDepth( int depth ) {
        this.saveStateDepth= depth;
    }

    /**
     * allow scripts to peek into undo/redo stack for debugging.
     * @param pos
     * @return
     */
    public synchronized StateStackElement peekAt( int pos ) {
        return stateStack.get(pos);
    }

    /**
     * used for feedback.
     * @param i
     * @return
     */
    public String getLongUndoDescription( int i ) {
        
        StateStackElement e1;
        StateStackElement e0;
                
        synchronized (this) {
            e1= stateStack.get(i);
            e0= stateStack.get(i-1);
        }
        
        List<Diff> diffss = e1.state.diffs(e0.state);
        diffss= removeTimeRangeBindings( e0.state, diffss );
        StringBuilder docBuf= new StringBuilder();
        for ( int j=0; j<diffss.size(); j++ ) {
            Diff s= diffss.get(j);
            if ( s.getDescription().contains("plotDefaults" ) ) continue;
            if ( j>0 ) docBuf.append(";\n");
            docBuf.append(s.getDescription());

        }
        return docBuf.toString();

    }
}
