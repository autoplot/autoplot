/*
 * TimeRangeTool.java
 *
 * Created on Oct 26, 2012, 3:32:19 AM
 */

package org.virbo.datasource;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.LoggerManager;
import org.das2.datum.Orbits;
import org.das2.datum.TimeUtil;
import org.das2.datum.format.TimeDatumFormatter;

/**
 * GUI for creating valid time ranges by calendar times, orbit, or NRT
 * @author jbf
 */
public class TimeRangeTool extends javax.swing.JPanel {

    private static final Logger logger= LoggerManager.getLogger("apdss.gui");

    /**
     * true when the last focus on the Calendar tab was the timeRange field.
     */
    boolean timeRangeFocus= true;

    String orbit=""; // save the orbit so it isn't clobbered by the GUI.
    
    /**
     * datumrange where we started.
     */
    private DatumRange pendingTimeRange;

    /** Creates new form TimeRangeTool */
    public TimeRangeTool() {
        initComponents();
        scComboBox.setModel( new DefaultComboBoxModel(getSpacecraft()) );
        scComboBox.setSelectedItem( "rbspa-pp" );
        resetSpacecraft("rbspa-pp");
        timeRangeTextField.addPropertyChangeListener( "text", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    DatumRange dr= DatumRangeUtil.parseTimeRange(timeRangeTextField.getText());
                    startTextField.setText( TimeDatumFormatter.DEFAULT.format( dr.min() ) );
                    stopTextField.setText( TimeDatumFormatter.DEFAULT.format( dr.max() ) );
                } catch ( ParseException ex ) {
                    // do nothing
                }

            }
        });
        timeRangeTextField.addFocusListener( new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                try {
                    DatumRange dr= DatumRangeUtil.parseTimeRange(timeRangeTextField.getText());
                    startTextField.setText( TimeDatumFormatter.DEFAULT.format( dr.min() ) );
                    stopTextField.setText( TimeDatumFormatter.DEFAULT.format( dr.max() ) );
                } catch ( ParseException ex ) {
                    // do nothing
                }
            }
        });
        resetRecent();
    }
    
    public void setSelectedRange( String s ) {
        DatumRange dr= null;
        try {
            dr= DatumRangeUtil.parseTimeRange(s);
            pendingTimeRange= dr;
        } catch ( ParseException ex ) {
            logger.log(Level.FINE, "failed to parse as timerange: {0}", s);
        }
        resetSpacecraft( scComboBox.getSelectedItem().toString(), dr );
        if ( s.startsWith("orbit:") ) {
            String[] ss= s.split(":",2);
            if ( ss[1].startsWith("http://") || ss[1].startsWith("https://") || ss[1].startsWith("ftp://") ) {
                int i= ss[1].indexOf(":",6);
                if ( i==-1 ) {
                    scComboBox.setSelectedItem(ss[1]);
                } else {
                    scComboBox.setSelectedItem(ss[1].substring(0,i) );
                    orbit= ss[1].substring(i+1);
                }
            } else {
                int i= ss[1].indexOf(":");
                if ( i==-1 ) {
                    scComboBox.setSelectedItem(ss[1]);
                    orbitList.setSelectedValue( ss[1], true );
                } else {
                    scComboBox.setSelectedItem(ss[1].substring(0,i) );
                    orbit= ss[1].substring(i+1);
                    orbitList.setSelectedValue( ss[1].substring(0,i), true );
                }
            }
            if ( dr!=null ) {
                startTextField.setText( TimeDatumFormatter.DEFAULT.format( dr.min() ) );
                stopTextField.setText( TimeDatumFormatter.DEFAULT.format( dr.max() ) );
                timeRangeTextField.setText( dr.toString() );
            } else {
                timeRangeTextField.setText( s );
            }
            resetSpacecraft((String)scComboBox.getSelectedItem(),pendingTimeRange);

            jTabbedPane1.setSelectedIndex(1);
        } else if ( s.startsWith("p") ) {
            nrtComboBox.setSelectedItem(s);
            jTabbedPane1.setSelectedIndex(2);
        } else {
            jTabbedPane1.setSelectedIndex(0);
            if ( dr!=null ) {
                timeRangeTextField.setText(dr.toString());
                startTextField.setText( TimeDatumFormatter.DEFAULT.format( dr.min() ) );
                stopTextField.setText( TimeDatumFormatter.DEFAULT.format( dr.max() ) );
            } else {
                timeRangeTextField.setText(s);
                startTextField.setText( "" );
                stopTextField.setText( "" );                
            }
        }
    }

    public String getSelectedRange() {
        int idx= jTabbedPane1.getSelectedIndex();
        if ( idx==0 ) {
            try {
                if ( timeRangeFocus ) {
                    String txt= timeRangeTextField.getText();
                    return DatumRangeUtil.parseTimeRange(txt).toString();
                } else {
                    String min= startTextField.getText();
                    String max= stopTextField.getText();
                    Datum tmin= TimeUtil.create(min);
                    Datum tmax= TimeUtil.create(max);
                    return new DatumRange( tmin, tmax ).toString();
                }
            } catch ( ParseException ex ) {
                return timeRangeTextField.getText();
            }
        } else if ( idx==1 ) {
            String sc= (String)scComboBox.getSelectedItem();
            String orb= (String)orbitList.getSelectedValue();
            int i= orb.indexOf(":");
            if ( i>-1 ) orb= orb.substring(0,i);
            return "orbit:"+sc+":"+orb;
        } else if ( idx==2 ) {
            String s= (String)nrtComboBox.getSelectedItem();
            int i= s.indexOf(" ");
            return s.substring(0,i);
        } else if ( idx==3 ) {
            String s= (String)recentTimesList.getSelectedValue();
            return s;
        } else {
            throw new IllegalArgumentException("not implemented");
        }
    }

    private String[] getSpacecraft() {
        return new String[] { "rbspa-pp", "rbspb-pp", "crres", "cassini", "marsx" };
    }
    private void resetSpacecraft( final String sc  ) {
        resetSpacecraft(sc, null);
    }
    
    private void resetSpacecraft( final String sc, DatumRange focusRange  ) {
        logger.log(Level.FINE, "resetSpacecraft({0})", sc);

        final Orbits o= Orbits.getOrbitsFor(sc);
        final List<String> ss= new ArrayList();
        String orb= o.first();
        boolean gotFocus= focusRange==null;
        String focusItem= null;

        while ( orb!=null ) {
            try {
                String str = String.valueOf(o.getDatumRange(orb));
                ss.add(orb+": "+str);
                if ( !gotFocus && o.getDatumRange(orb).intersects(focusRange) ) {
                    gotFocus=true;
                    focusItem= orb+": "+str;
                }
            } catch (ParseException ex) { // this won't happen
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
            orb= o.next(orb);
        }

        final DefaultListModel mm= new DefaultListModel();
        for ( String s: ss ) {
            mm.addElement(s);
        }
        final String ffocusItem= focusItem;
                
        SwingUtilities.invokeLater( new Runnable() {
            @Override
            public void run() {
                orbitList.setModel( mm );
                if ( orbit.length()>0 ) {
                    try { // is enterred orbit legal orbit
                        o.getDatumRange(orbit);
                        if ( ffocusItem!=null ) {
                            orbitList.setSelectedValue( ffocusItem, true );
                        }
                    } catch ( ParseException ex ) {
                        // orbit wasn't found for this spacecraft
                    }
                } else {
                    orbitList.setSelectedIndex( 0 );
                }
                String s=  (String)orbitList.getSelectedValue();
                if ( s!=null ) showOrbit( s );
                if ( sc.contains(":") ) {
                    scFeedbackTF.setText( "orbits from "+sc );
                } else {
                    scFeedbackTF.setText( "orbits from "+o.getURL() );
                }
            }
        } );

    }
    
    InputVerifier verifier=null;
        
    private void resetRecent() {
        int RECENT_SIZE=20;
        
        File bookmarksFolder= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "bookmarks" );
        File recentFile= new File( bookmarksFolder, "recent.timerange.txt" );
        List<String> items= new ArrayList( RECENT_SIZE+2 );
        
        try {
            if ( recentFile.exists() ) {
                BufferedReader r = new BufferedReader(new FileReader(recentFile));
                try {
                    String s= r.readLine();
                    while ( s!=null ) {
                        if ( verifier!=null ) {
                            if ( !verifier.verify(s) ) {
                                s= r.readLine();
                                continue;
                            }
                        }
                        items.add(s);
                        s= r.readLine();
                    }
                } finally {
                    r.close();
                }
            }

            Collections.reverse(items);
            
            //remove repeat items
            List nitems= new ArrayList( items.size() );
            for ( int i=0; i<items.size(); i++ ) {
                String item= items.get(i);
                if ( !nitems.contains(item) ) nitems.add(item);
            }
            items= nitems;
            
            int n= items.size();
            if ( n>RECENT_SIZE ) items= items.subList(0,RECENT_SIZE);

            DefaultListModel dlm= new DefaultListModel();
            for ( int i=0; i<items.size(); i++ ) {
                dlm.add( i, items.get(i) );
            }
            recentTimesList.setModel( dlm );

        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
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

        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        timeRangeTextField = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        startTextField = new javax.swing.JTextField();
        stopTextField = new javax.swing.JTextField();
        jPanel2 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        scComboBox = new javax.swing.JComboBox();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        orbitFeedbackLabel = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        scFeedbackTF = new javax.swing.JTextField();
        jScrollPane1 = new javax.swing.JScrollPane();
        orbitList = new javax.swing.JList();
        jPanel3 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        nrtComboBox = new javax.swing.JComboBox();
        jPanel4 = new javax.swing.JPanel();
        recentTimesListSP = new javax.swing.JScrollPane();
        recentTimesList = new javax.swing.JList();

        jTabbedPane1.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                jTabbedPane1StateChanged(evt);
            }
        });

        jLabel3.setText("Select Time By Calendar Dates");

        jLabel7.setText("Enter Time Range:");
        jLabel7.setToolTipText("This time range is interpretted a flexible parser that understands many time formats");

        timeRangeTextField.setText("jTextField1");
        timeRangeTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                timeRangeTextFieldFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                timeRangeTextFieldFocusLost(evt);
            }
        });

        jLabel6.setText("Or Enter Separate Times (ISO8601):");
        jLabel6.setToolTipText("Enter the start and stop times separately, using ISO8601 standard time representation.");

        jLabel8.setText("Begin:");

        jLabel9.setText("End:");

        startTextField.setText("jTextField2");
        startTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                startTextFieldFocusGained(evt);
            }
        });

        stopTextField.setText("jTextField3");
        stopTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                stopTextFieldFocusGained(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jLabel3, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 489, Short.MAX_VALUE)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jLabel7)
                    .add(jLabel6)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(12, 12, 12)
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(timeRangeTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 457, Short.MAX_VALUE)
                            .add(jPanel1Layout.createSequentialGroup()
                                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(jLabel8)
                                    .add(jLabel9))
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(stopTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 392, Short.MAX_VALUE)
                                    .add(startTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 423, Short.MAX_VALUE))))))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jLabel3)
                .add(18, 18, 18)
                .add(jLabel7)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(timeRangeTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(jLabel6)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel8)
                    .add(startTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel9)
                    .add(stopTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(182, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Calendar", jPanel1);

        jLabel2.setText("Time Ranges by Orbit");

        scComboBox.setEditable(true);
        scComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        scComboBox.setToolTipText("Id of the orbits file or URL to orbits file");
        scComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                scComboBoxItemStateChanged(evt);
            }
        });

        jLabel4.setText("Spacecraft:");

        jLabel5.setText("Orbit:");

        orbitFeedbackLabel.setFont(orbitFeedbackLabel.getFont().deriveFont(orbitFeedbackLabel.getFont().getSize()-4f));
        orbitFeedbackLabel.setText("Shows selected timerange for orbit");

        jLabel10.setFont(jLabel10.getFont().deriveFont(jLabel10.getFont().getSize()-4f));
        jLabel10.setText("Select from predefined orbits, or spacecraft may be a URL to any three-column text file.");

        scFeedbackTF.setFont(scFeedbackTF.getFont().deriveFont(scFeedbackTF.getFont().getSize()-4f));
        scFeedbackTF.setText("jTextField1");
        org.virbo.datasource.ui.Util.makeJTextFieldLookLikeJLabel(scFeedbackTF);

        orbitList.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        orbitList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                orbitListValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(orbitList);

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jLabel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel2Layout.createSequentialGroup()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel2Layout.createSequentialGroup()
                        .addContainerGap()
                        .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jPanel2Layout.createSequentialGroup()
                                .add(jLabel5)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(orbitFeedbackLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel2Layout.createSequentialGroup()
                                .add(jLabel4, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 107, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .add(18, 18, 18)
                                .add(scComboBox, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel2Layout.createSequentialGroup()
                        .addContainerGap()
                        .add(jLabel10, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 462, Short.MAX_VALUE))
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel2Layout.createSequentialGroup()
                        .add(24, 24, 24)
                        .add(scFeedbackTF))
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(0, 0, Short.MAX_VALUE)
                        .add(jScrollPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 450, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(jLabel2)
                .add(3, 3, 3)
                .add(jLabel10)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel4)
                    .add(scComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(2, 2, 2)
                .add(scFeedbackTF, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel5)
                    .add(orbitFeedbackLabel))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 218, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(22, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Orbits", jPanel2);

        jLabel1.setText("Near Real Time timeranges");

        nrtComboBox.setEditable(true);
        nrtComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "P1D  (last day)", "P5D  (last five days)", "P30D  (last thirty days)" }));

        org.jdesktop.layout.GroupLayout jPanel3Layout = new org.jdesktop.layout.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jLabel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 489, Short.MAX_VALUE)
            .add(jPanel3Layout.createSequentialGroup()
                .add(12, 12, 12)
                .add(nrtComboBox, 0, 462, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(nrtComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(312, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("NRT", jPanel3);

        recentTimesList.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        recentTimesList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                recentTimesListValueChanged(evt);
            }
        });
        recentTimesListSP.setViewportView(recentTimesList);

        org.jdesktop.layout.GroupLayout jPanel4Layout = new org.jdesktop.layout.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(recentTimesListSP, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 486, Short.MAX_VALUE)
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(recentTimesListSP, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 362, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("Recent Times", jPanel4);

        jTabbedPane1.setSelectedIndex(1);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jTabbedPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 494, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jTabbedPane1)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void scComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_scComboBoxItemStateChanged
        final String sc= (String) scComboBox.getSelectedItem();
        Runnable run= new Runnable() {
            @Override
            public void run() {
                resetSpacecraft( sc, pendingTimeRange );
            }
        };
        new Thread( run,"loadOrbits" ).start();

        if ( sc.contains(":") ) {
            scFeedbackTF.setText( "loading orbits from "+sc );
        } else {
            scFeedbackTF.setText( "loading orbits for " + sc );
        }

    }//GEN-LAST:event_scComboBoxItemStateChanged

    private void timeRangeTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_timeRangeTextFieldFocusLost
        try {
            DatumRange dr= DatumRangeUtil.parseTimeRange(timeRangeTextField.getText());
            startTextField.setText( TimeDatumFormatter.DEFAULT.format( dr.min() ) );
            stopTextField.setText( TimeDatumFormatter.DEFAULT.format( dr.max() ) );
            pendingTimeRange= dr;
        }  catch ( ParseException ex ) {

        }
    }//GEN-LAST:event_timeRangeTextFieldFocusLost

    private void timeRangeTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_timeRangeTextFieldFocusGained
        timeRangeFocus= true;
    }//GEN-LAST:event_timeRangeTextFieldFocusGained

    private void startTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_startTextFieldFocusGained
        timeRangeFocus= false;
    }//GEN-LAST:event_startTextFieldFocusGained

    private void stopTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_stopTextFieldFocusGained
        timeRangeFocus= false;
    }//GEN-LAST:event_stopTextFieldFocusGained

    private void jTabbedPane1StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_jTabbedPane1StateChanged
       if ( jTabbedPane1.getSelectedIndex()==1 ) {
           if ( pendingTimeRange!=null ) {
               resetSpacecraft( (String)scComboBox.getSelectedItem(), pendingTimeRange );
           }
       } else if ( jTabbedPane1.getSelectedIndex()==3 ) {
           if ( pendingTimeRange!=null ) {
               ListModel m= recentTimesList.getModel();
               for ( int i=0; i<m.getSize(); i++ ) {
                   try {
                       DatumRange tr= DatumRangeUtil.parseTimeRange((String)m.getElementAt(i));
                       if ( tr.intersects(pendingTimeRange) ) {
                           recentTimesList.setSelectedIndex(i);
                           break;
                       }
                   } catch ( ParseException ex ) {
                       logger.log(Level.WARNING,null,ex);
                   }
               }
               if ( recentTimesList.getSelectedIndex()==-1 ) {
                   recentTimesList.setSelectedIndex(0);
               }
           }
       }
    }//GEN-LAST:event_jTabbedPane1StateChanged

    private void orbitListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_orbitListValueChanged
        String sel= (String) orbitList.getSelectedValue();
        if ( sel!=null ) {
            orbit= showOrbit(sel);
        } else {
            orbitFeedbackLabel.setText( "Any orbit number can be entered by editing the text." );            
        }
    }//GEN-LAST:event_orbitListValueChanged

    private void recentTimesListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_recentTimesListValueChanged
        
    }//GEN-LAST:event_recentTimesListValueChanged

    /**
     * shows the orbit timerange, clipping off text past the first colon.
     * @param sorbit like "172: 2012-11-02 07:00 to 11:20"
     * @return canonical orbit string like "172"
     */
    private String showOrbit( String sorbit ) {
        Orbits o= Orbits.getOrbitsFor((String)scComboBox.getSelectedItem());
        int i= sorbit.indexOf(":");
        if ( i>-1 ) sorbit= sorbit.substring(0,i);
        DatumRange dr;
        try {
            dr = o.getDatumRange(sorbit);
            orbitFeedbackLabel.setText( sorbit + ": "+ dr.toString() ); // note result is not an orbit datum range.
            pendingTimeRange= dr;
        } catch (ParseException ex) {
            orbitFeedbackLabel.setText("No such orbit found: "+ sorbit);
        }
        return sorbit;
    }

    public static void main( String[] args ) {
        TimeRangeTool trt= new TimeRangeTool();
        JOptionPane.showConfirmDialog(null,trt);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JComboBox nrtComboBox;
    private javax.swing.JLabel orbitFeedbackLabel;
    private javax.swing.JList orbitList;
    private javax.swing.JList recentTimesList;
    private javax.swing.JScrollPane recentTimesListSP;
    private javax.swing.JComboBox scComboBox;
    private javax.swing.JTextField scFeedbackTF;
    private javax.swing.JTextField startTextField;
    private javax.swing.JTextField stopTextField;
    private javax.swing.JTextField timeRangeTextField;
    // End of variables declaration//GEN-END:variables

}
