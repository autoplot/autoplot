
/*
 * TimeRangeEditor.java
 *
 * Created on Jun 4, 2011, 9:33:57 AM
 */

package org.autoplot.datasource;

import java.awt.CardLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultEditorKit;
import org.das2.DasApplication;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.LoggerManager;
import org.das2.datum.UnitsUtil;
import org.autoplot.datasource.ui.PromptComboBoxEditor;

/**
 * Standard control for controlling a DatumRange containing times, with
 * next and previous buttons, and a launcher into the TimeRangeTool.
 * @author jbf
 */
public class TimeRangeEditor extends javax.swing.JPanel {

    private static final Logger logger= LoggerManager.getLogger("apdss.gui");
    public TimeRangeEditor() {
        initComponents();

        timeRangeToolButton.setActionCommand("timeRangeTool");
        nextButton.setActionCommand("next");
        prevButton.setActionCommand("previous");
        browseButton.setActionCommand("inspect");
        
        recentComboBox.setName("timeRangeEditor");
        recentComboBox.setMinimumSize( new Dimension( 200,30) ); // long items in history cause problems.
        
        recentComboBox.setPreferenceNode(RecentComboBox.PREF_NODE_TIMERANGE);
        recentComboBox.setEditor( new PromptComboBoxEditor("Time range to view (e.g. 2010-01-01)") );
        recentComboBox.setToolTipText("Recently entered time ranges");
        ((JComponent)recentComboBox.getEditor().getEditorComponent()).setToolTipText("Time Range, right-click for examples");
        recentComboBox.addFocusListener( new FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                parseRange();
            }
        });
        recentComboBox.addActionListener( new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                if ( !suppressRecentComboBoxActionEvents ) {
                    parseRange();
                }
            }
        });

        recentComboBox.setVerifier( new TimeRangeVerifier() );
        browseButton.setVisible(false);
        
        revalidate();
        addMousePopupListener();
    }

    DatumRange range= DatumRangeUtil.parseTimeRangeValid( "2010-01-01" );
    
    /**
     * this is the range which is displayed, and will be the same as range
     * when rescale is "" or "0%,100%"
     */
    DatumRange controlRange= range;

    public static final String PROP_USE_DOY= "useDoy";

    /**
     * use DOY instead of Y-M-D
     */
    boolean useDoy=false;

    /**
     * true if day of year should be used instead of year, month, day.
     * @return true if day of year should be used
     */
    public boolean isUseDoy() {
        return useDoy;
    }

    /**
     * prefer use of day of year rather than year, month, day.
     * @param useDoy 
     */
    public void setUseDoy(boolean useDoy) {
        boolean old= this.useDoy;
        this.useDoy = useDoy;
        DatumRangeUtil.setUseDoy( useDoy );
        firePropertyChange( PROP_USE_DOY,old,useDoy);
    }

    private String rescale = "";

    public static final String PROP_RESCALE = "rescale";

    public String getRescale() {
        return rescale;
    }

    /**
     * Add extra time to the range, and account for this extra time
     * with the next and previous buttons.  Example values are:
     * <li>"" the default behavior
     * <li>0%,100% also the default behavior
     * <li>-10%,110% add ten percent before and after the interval
     * <li>0%-1hr,100%+1hr add an hour before and after the interval
     * Note the GUI will always report the 0%,100% time, without the context.
     * @param rescale 
     */
    public void setRescale(String rescale) {
        String oldRescale = this.rescale;
        this.rescale = rescale;
        DatumRange oldRange= this.range;
        try {
            this.range= DatumRangeUtil.rescale( controlRange, rescale );
        } catch (ParseException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        firePropertyChange(PROP_RESCALE, oldRescale, rescale);
        firePropertyChange(PROP_RANGE, oldRange, range );
    }

    public static final String PROP_RANGE= "range";

    /**
     * get the timerange.
     * @return the timerange.
     */
    public DatumRange getRange() {
        return range;
    }
    
    /**
     * avoid ringing and extra parsing caused by roundoff in parsing.
     */
    private boolean suppressRecentComboBoxActionEvents= false;

    /**
     * set the range for the controller.  Note that if the rescale range
     * is not "" or "0%,100%" then the controlRange will be different.
     * @param value 
     */
    public void setRange( final DatumRange value ) {
        if ( !UnitsUtil.isTimeLocation(value.getUnits()) ) return;
        final DatumRange oldValue= this.range;
        final DatumRange oldControlRange= this.controlRange;
        this.range= value;
        try {
            if ( this.rescale.length()==0 ) {
                this.controlRange= value;
            } else {
                this.controlRange= DatumRangeUtil.rescaleInverse(value, rescale );
            }
        } catch (ParseException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        if (oldValue != value && oldValue != null && !oldValue.equals(value)) {
            if ( !suppressRecentComboBoxActionEvents ) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        TimeRangeEditor.super.firePropertyChange( PROP_RANGE, oldValue, value);
                        TimeRangeEditor.super.firePropertyChange( PROP_CONTROL_RANGE, oldControlRange, TimeRangeEditor.this.controlRange);
                    }
                } );
            }
        }
        this.suppressRecentComboBoxActionEvents= true;
        if ( value==noOneListening ) {
            this.recentComboBox.setSelectedItem("");
        } else {
            this.recentComboBox.setSelectedItem( controlRange.toString() );
        }
        this.suppressRecentComboBoxActionEvents= false;
    }
    
    public static final String PROP_CONTROL_RANGE= "controlRange";
    
    /**
     * set the range displayed, regardless of the rescaling.
     * @param value
     */
    public void setControlRange( final DatumRange value ) {
        if ( !UnitsUtil.isTimeLocation(value.getUnits()) ) return;
        final DatumRange oldValue= this.controlRange;
        final DatumRange oldRange= this.range;
        this.controlRange= value;
        try {
            if ( this.rescale.length()==0 ) {
                this.range= value;
            } else {
                this.range= DatumRangeUtil.rescale(value, rescale );
            }
        } catch (ParseException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        if (oldValue != value && oldValue != null && !oldValue.equals(value)) {
            if ( !suppressRecentComboBoxActionEvents ) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        TimeRangeEditor.super.firePropertyChange( PROP_CONTROL_RANGE, oldValue, value);
                        TimeRangeEditor.super.firePropertyChange( PROP_RANGE, oldRange, TimeRangeEditor.this.range );
                    }
                } );
            }
        }
        this.suppressRecentComboBoxActionEvents= true;
        if ( value==noOneListening ) {
            this.recentComboBox.setSelectedItem("");
        } else {
            this.recentComboBox.setSelectedItem( value.toString() );
        }
        this.suppressRecentComboBoxActionEvents= false;        
    }

    DatumRange noOneListening= range;

    /**
     * special marker object indicates that the "no one listening" message should be shown.
     * @param dr
     */
    public void setNoOneListeningRange( DatumRange dr ) {
        this.noOneListening= dr;
    }
    
    /**
     * special marker object indicates that the "no one listening" message should be shown.
     * @return special marker object
     */
    public DatumRange getNoOneListeningRange() {
        return this.noOneListening;
    }

    /**
     * return true if the string appears to be a URI.
     * @param text
     * @return
     */
    private static boolean isUri( String text ) {
        boolean isUri= false;
        if ( text.startsWith("/") ) {
            isUri= true;
        }
        if ( !isUri  ) {
            int icolon= text.indexOf(':');
            if ( icolon>-1 ) {
                String pref= text.substring(0,icolon);
                if ( Character.isLetter(pref.charAt(0)) && Pattern.matches( "[a-zA-Z_\\+0-9]*", pref ) ) {
                    isUri= true;
                }
            }
        }
        return isUri;
    }

    private void parseRange() {
        DatumRange dr;
        DatumRange value= this.controlRange;

        String text= (String)recentComboBox.getSelectedItem();
        if ( text==null || text.equals("") ) return;
        
        try {
            String rangeString= text;
            dr= DatumRangeUtil.parseTimeRange(rangeString);
            setControlRange(dr);
        } catch ( ParseException e ) {
            boolean isUri= isUri( text );
            if ( isUri ) {
                if ( peer!=null ) {
                    peer.setValue(text);
                    peer.maybePlot(true);
                    return;
                } else {
                    showErrorUsage( text, "Appears to be a dataset location, and this expects timeranges" );
                }
            }
            //timeRangeTextField.setText( range.toString() ); // I think we can just leave the value there.
            if ( UnitsUtil.isTimeLocation(value.getUnits()) ) { // go ahead and handle non-times.
                showErrorUsage( text, "<html>" +e.getMessage() );
            } else {
                //showErrorUsage( text, "unable to parse range" );
            }
        } catch ( IllegalArgumentException e ) {
            if ( value!=null ) {
                setControlRange( value ); // cause reformat of old Datum
                if ( e.getMessage().contains("min > max") ) {
                    showErrorUsage( text, "min cannot be greater than max" );
                } else {
                    showErrorUsage( text, e.getMessage() );
                }
            }
        }
    }

    @Override
    public final void revalidate() {
        super.revalidate(); //To change body of generated methods, choose Tools | Templates.
    }

    
    /**
     * prevent displaying same message so many times...
     */
    private String lastErrorText= null;
    private long lastErrorTime= 0;

    private void showErrorUsage( String text, String why ) {
        if ( !DasApplication.getDefaultApplication().isHeadless() ) {
            if ( text!=null && text.equals(lastErrorText)
                    && (System.currentTimeMillis()-lastErrorTime)<5000 ) {
                return;
            }
            if ( why!=null ) {
                JOptionPane.showMessageDialog( this, "<html>Unable to accept \""+text+"\"<br>"+why+"<html>" );
            } else {
                JOptionPane.showMessageDialog( this, "<html>Unable to accept \""+text+"\"</html>" );
            }
            lastErrorText= text;
            lastErrorTime= System.currentTimeMillis();
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        prevButton = new javax.swing.JButton();
        nextButton = new javax.swing.JButton();
        browseButton = new javax.swing.JButton();
        recentComboBox = new org.autoplot.datasource.RecentComboBox();
        timeRangeToolButton = new javax.swing.JButton();

        setPreferredSize(new java.awt.Dimension(384, 39));

        prevButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/datasource/prevPrev.png"))); // NOI18N
        prevButton.setToolTipText("Step to the previous interval");
        prevButton.setMaximumSize(new java.awt.Dimension(34, 20));
        prevButton.setMinimumSize(new java.awt.Dimension(34, 20));
        prevButton.setPreferredSize(new java.awt.Dimension(34, 20));
        prevButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prevButtonActionPerformed(evt);
            }
        });

        nextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/datasource/nextNext.png"))); // NOI18N
        nextButton.setToolTipText("Step to the next interval");
        nextButton.setMaximumSize(new java.awt.Dimension(34, 24));
        nextButton.setMinimumSize(new java.awt.Dimension(34, 24));
        nextButton.setPreferredSize(new java.awt.Dimension(34, 24));
        nextButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextButtonActionPerformed(evt);
            }
        });

        browseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/datasource/fileMag.png"))); // NOI18N
        browseButton.setToolTipText("Inspect data source");
        browseButton.setEnabled(false);
        browseButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        browseButton.setMaximumSize(new java.awt.Dimension(20, 20));
        browseButton.setMinimumSize(new java.awt.Dimension(20, 20));
        browseButton.setPreferredSize(new java.awt.Dimension(20, 20));
        browseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButtonActionPerformed(evt);
            }
        });

        recentComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "2010-01-01", "2010-01-02" }));
        recentComboBox.setMinimumSize(new java.awt.Dimension(120, 27));

        timeRangeToolButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/datasource/calendar.png"))); // NOI18N
        timeRangeToolButton.setToolTipText("Time Range Tool");
        timeRangeToolButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeRangeToolButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(recentComboBox, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 196, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(timeRangeToolButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(prevButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 40, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(nextButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 34, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(browseButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 30, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );

        layout.linkSize(new java.awt.Component[] {nextButton, prevButton}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(browseButton, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 27, Short.MAX_VALUE)
            .add(nextButton, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(prevButton, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(timeRangeToolButton, 0, 0, Short.MAX_VALUE)
            .add(recentComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
        );

        layout.linkSize(new java.awt.Component[] {browseButton, nextButton, prevButton, recentComboBox, timeRangeToolButton}, org.jdesktop.layout.GroupLayout.VERTICAL);

    }// </editor-fold>//GEN-END:initComponents

    private void nextButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextButtonActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        setControlRange( controlRange.next() );
    }//GEN-LAST:event_nextButtonActionPerformed

    private void prevButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prevButtonActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        setControlRange( controlRange.previous() );
    }//GEN-LAST:event_prevButtonActionPerformed

    DataSetSelector peer;

    /**
     * provide a shortcut to a DataSetSelector editor.
     * @param peer 
     */
    public void setDataSetSelectorPeer( DataSetSelector peer ) {
        this.peer= peer;
        if ( peer!=null ) {
            browseButton.setEnabled(true);
            browseButton.setVisible(true);
        } else {
            browseButton.setEnabled(false);
            browseButton.setVisible(false);
        }
    }

    private void browseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseButtonActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        if ( peer!=null ) {
            String surl = (String) peer.getLastValue().trim();//TODO:check
            
            if ( peer.isExpertMode() ) {
                peer.setValue(surl);
            }
            
            //boolean wasRejected= false;
            DataSourceEditorPanel edit;
            
            // hooks for browsing, such as "vap+internal"
            for (String browseTriggerRegex : peer.browseTriggers.keySet()) {
                if (Pattern.matches(browseTriggerRegex, surl)) {
                    logger.finest("matches browse trigger");
                    Action action = peer.browseTriggers.get(browseTriggerRegex);
                    action.actionPerformed( new ActionEvent(this, 123, "dataSetSelect") );
                    return;
                }
            }  
            
            try {
                edit = DataSourceEditorPanelUtil.getDataSourceEditorPanel(DataSetURI.getURIValid(surl));
                if ( edit!=null && edit.reject(surl) ) {
                    edit= null;
                    //wasRejected= true;
                }
            } catch (URISyntaxException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                edit= null;
            } catch ( Exception ex ) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                edit= null;
            }
            if ( edit==null ) {
                JOptionPane.showMessageDialog( prevButton, "<html>The selected plot element has no editor:<br>"+surl, "no editor", JOptionPane.OK_OPTION );
            } else {
                peer.browseSourceType();
            }
        }
    }//GEN-LAST:event_browseButtonActionPerformed

    private void timeRangeToolButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeRangeToolButtonActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        TimeRangeTool t=new TimeRangeTool();
        t.setSelectedRange(controlRange.toString());//TODO: goofy
        if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog( SwingUtilities.getWindowAncestor(this), t, "Select time range",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE, 
                new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/datasource/calendar.png"))) ) {
            String str= t.getSelectedRange();
            try {
                setControlRange( DatumRangeUtil.parseTimeRangeValid(str) );
            } catch ( IllegalArgumentException ex ) {
                logger.log(Level.FINE, "unable to parse time/orbit: {0}", str);
            }
            recentComboBox.actionPerformed( new ActionEvent(this,0,"triggerSaveRecent",0) );
        }

    }//GEN-LAST:event_timeRangeToolButtonActionPerformed

    public static void main( String[] args ) {
        TimeRangeEditor p= new TimeRangeEditor();
        p.addPropertyChangeListener(TimeRangeEditor.PROP_RANGE, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                System.err.println(evt.getOldValue()+" -> "+ evt.getNewValue() ); // logger okay
            }
        } );
        JFrame f= new JFrame();
        f.setContentPane( p );
        f.pack();
        f.setVisible(true);
        f.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton browseButton;
    private javax.swing.JButton nextButton;
    private javax.swing.JButton prevButton;
    private org.autoplot.datasource.RecentComboBox recentComboBox;
    private javax.swing.JButton timeRangeToolButton;
    // End of variables declaration//GEN-END:variables

    public PropertyChangeListener getUriFocusListener() {
        return new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                browseButton.setToolTipText( "<html>Edit data source<br>"+evt.getNewValue().toString()+"</html>" );
            }
        };
    }

    private String alternatePeer;
    private String alternatePeerCard=null;

    public void setAlternatePeer(String label, String card ) {
        this.alternatePeer= label;
        this.alternatePeerCard= card;
    }

    
    private void addMousePopupListener() {
        recentComboBox.getEditor().getEditorComponent().addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                if ( e.isPopupTrigger() ) showPopup(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if ( e.isPopupTrigger() ) showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if ( e.isPopupTrigger() ) showPopup(e);
            }

        });

    }
    private void showPopup( MouseEvent e ) {
        getPopupMenu().show( this, e.getX(), e.getY() );
    }

    private JMenuItem exampleTime( final String s, final String toolTip ) {
        JMenuItem mi= new JMenuItem( new AbstractAction(s) {
            @Override
            public void actionPerformed( ActionEvent e ) {
                recentComboBox.setSelectedItem(s);
            }
        });
        mi.setToolTipText(toolTip);
        return mi;
    }

    private JPopupMenu getPopupMenu() {
        JPopupMenu result= new JPopupMenu();
        JMenuItem cutItem = result.add(new DefaultEditorKit.CutAction());
        cutItem.setText("Cut");
        JMenuItem copyItem = result.add(new DefaultEditorKit.CopyAction());
        copyItem.setText("Copy");
        JMenuItem pasteItem = result.add(new DefaultEditorKit.PasteAction());
        pasteItem.setText("Paste");

        JMenu examplesMenu= new JMenu("Examples");
        examplesMenu.add( exampleTime( "2010 Jan", "Month of January" ) );
        examplesMenu.add( exampleTime( "2010-01-01", "January 1, 2010" ) );
        examplesMenu.add( exampleTime( "2010-01-01/2010-01-04", "ISO8601 range" ) );
        examplesMenu.add( exampleTime( "P5D", "Last 5 Days to now" ) );
        examplesMenu.add( exampleTime( "orbit:rbspa-pp:30", "Orbit 30 from rbspa-pp orbits file" ) );
        examplesMenu.add( exampleTime( "orbit:http://das2.org/wiki/index.php/Orbits/rbspa-pp:30", "Orbit 30 from any orbits file" ) );

        result.add( examplesMenu );
        
        if ( this.alternatePeerCard!=null ) {
            result.add( new JSeparator() );
            result.add(new AbstractAction( alternatePeer ) {
                @Override
                public void actionPerformed(ActionEvent ev) {
                    org.das2.util.LoggerManager.logGuiEvent(ev);                    
                    Container trp= TimeRangeEditor.this.getParent();
                    if ( trp.getLayout() instanceof CardLayout ) {
                        setCardSelected(false);
                    }
                    
                }
            } );
        }

        return result;

    }
    
    private boolean cardSelected = false;

    public static final String PROP_CARDSELECTED = "cardSelected";

    public boolean isCardSelected() {
        return cardSelected;
    }

    public void setCardSelected(boolean cardSelected) {
        boolean oldCardSelected = this.cardSelected;
        this.cardSelected = cardSelected;
        firePropertyChange(PROP_CARDSELECTED, oldCardSelected, cardSelected);
    }
    
    public void setCardSelectedNoEventKludge(boolean cardSelected) {
        boolean oldCardSelected = this.cardSelected;
        this.cardSelected = cardSelected;
    }

    /**
     * re-layout the GUI to make it thinner.
     */
    public void makeThinner() {
        removeAll();
        setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );
        add( recentComboBox );
        javax.swing.JPanel p2= new javax.swing.JPanel();
        p2.setLayout( new BoxLayout( p2, BoxLayout.X_AXIS ) );
        p2.add( timeRangeToolButton );
        p2.add( prevButton );
        p2.add( nextButton );
        recentComboBox.setMaximumSize( recentComboBox.getPreferredSize() );
        prevButton.setPreferredSize(nextButton.getPreferredSize());
        prevButton.setMinimumSize(nextButton.getMinimumSize());
        prevButton.setMaximumSize(nextButton.getMaximumSize());
        add( p2 );
        setMinimumSize( new Dimension( recentComboBox.getMinimumSize().width, recentComboBox.getMinimumSize().height + prevButton.getMinimumSize().height + 5 ));
        setPreferredSize(getMinimumSize());
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        prevButton.setEnabled(enabled);
        nextButton.setEnabled(enabled);
        timeRangeToolButton.setEnabled(enabled);
        recentComboBox.setEnabled(enabled);
    }
    
    

}
