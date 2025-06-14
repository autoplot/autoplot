
package org.autoplot.datasource;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
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
import org.das2.graph.DasAxis;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasColumn;
import org.das2.graph.DasRow;

/**
 * GUI for creating valid time ranges by calendar times, orbit, or NRT
 * @author jbf
 */
public final class TimeRangeTool extends javax.swing.JPanel {

    private static final Logger logger= LoggerManager.getLogger("apdss.gui");

    /**
     * true when the last focus on the Calendar tab was the timeRange field.
     */
    private boolean timeRangeFocus= true;

    private String orbit=""; // save the orbit so it isn't clobbered by the GUI.
        
    /**
     * datumrange where we started.
     */
    private DatumRange pendingTimeRange;
    private DasAxis dasAxis;
    
    private final Preferences prefs;
    private static final String PREF_SPACECRAFT = "spacecraft";

    private final InputVerifier verifier= new InputVerifier() {
        @Override
        public boolean verify(String value) {
            try {
                DatumRangeUtil.parseTimeRange(value);
                return true;
            } catch ( ParseException ex ) {
                return false;
            } catch ( IllegalArgumentException ex ) {
                return false;
            }
        }
    };
        
    private static String interpretIso8601Range( String range ) {
        int ispace= range.indexOf(' ');
        if ( ispace>-1 ) {
            range= range.substring(0,ispace);
        }
        try {
            int[] digits= DatumRangeUtil.parseISO8601Duration( range );
            
            String[] sdigits= new String[] { "years", "months", "days", "hours", "minutes", "seconds" };
            StringBuilder result= new StringBuilder();
            for ( int i=0; i<digits.length; i++ ) {
                if ( digits[i]>0 ) {
                    int d= digits[i];
                    String sd= d==1 ? sdigits[i].substring(0,sdigits[i].length()-1) :  sdigits[i];
                    if ( result.length()>0 ) result.append(", ");
                    result.append( digits[i] ).append(" ").append(sd);
                }
            }
            return "last " + result.toString();
        } catch (ParseException ex) {
            if ( range.startsWith("now/now+") ) {
                return "next " + interpretIso8601Range( range.substring(8) ).substring(5);
            } else {
                switch ( range ) {
                    case "P1D/lastday": {
                        return "24 hours leading up to the last day boundary";
                    }
                    case "P1Y/lastyear": {
                        return "last calendar year";
                    }
                    case "lastyear/P1Y": {
                        return "the current year";
                    }
                    case "lastday/P1D": {
                        return "the current day (in UT)";
                    }
                    case "P2M/lastmonth": {
                        return interpretIso8601Range( range.substring(0,3) ).substring(5) + " leading up to the last month boundary";
                    }
                    default: {
                        try {
                            DatumRange dr= DatumRangeUtil.parseDatumRange(range);
                            return dr.toString();
                        } catch (ParseException ex1) {
                            return range + ("(parse error)");
                        }
                    }
                }   
            }
        }
        
    }
    
    
    /** Creates new form TimeRangeTool */
    public TimeRangeTool() {
        initComponents();
        nrtComboBox.setRenderer( new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component parent= super.getListCellRendererComponent( list, value, index, isSelected, cellHasFocus );
                if ( parent instanceof JLabel ) {
                    String s= interpretIso8601Range((String)value);
                    if ( s.equals(value) ) {
                        ((JLabel) parent).setText(s);
                    } else {
                        ((JLabel) parent).setText(value+": "+s);
                    }
                } 
                return parent;
            }
        });
        interpretationLabel.setText( interpretIso8601Range( nrtComboBox.getSelectedItem().toString() ));
        
        final DasCanvas canvas= new DasCanvas( 528, 89 );
        DasRow row= new DasRow( canvas, 0.3, 0.5 );
        DasColumn column= new DasColumn( canvas, 0.1, 0.9 );
        dasAxis= new DasAxis( DatumRangeUtil.parseTimeRangeValid( nrtComboBox.getSelectedItem().toString() ), DasAxis.HORIZONTAL ) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics g= graphics.create();
                super.paintComponent(graphics);
                g.translate(-getX(), -getY());
                g.setClip(null);
                int inow= (int)dasAxis.transform( TimeUtil.now() );
                int iy= (int)dasAxis.getRow().getDMaximum();
                g.setColor(Color.BLUE);
                g.fillPolygon( new int[] {inow-7,inow,inow+7,inow-7}, 
                        new int[] { iy-11,iy, iy-11, iy-11 }, 4 );
                g.setColor(Color.BLUE.darker());
                g.drawPolygon( new int[] {inow-7,inow,inow+7,inow-7}, 
                        new int[] { iy-11,iy, iy-11, iy-11 }, 4 );
            }
            
        };
        dasAxis.removeMouseListener( dasAxis.getDasMouseInputAdapter() );
        dasAxis.setAnimated(true);
        canvas.add( dasAxis, row, column );
        
        jPanel5.setLayout( new BorderLayout() );
        
        jPanel5.add( canvas, BorderLayout.CENTER );
        jPanel5.revalidate();
        
        scComboBox.setModel( new DefaultComboBoxModel(getSpacecraft()) );

        prefs = AutoplotSettings.settings().getPreferences( TimeRangeTool.class );
        final String sc= prefs.get(PREF_SPACECRAFT, "rbspa-pp" );
        
        scComboBox.setSelectedItem( sc );
        Runnable run= new Runnable() {
            @Override
            public void run() {
                resetSpacecraft(sc);
            }
        };
        new Thread(run,"loadOrbits").start();
        feedbackLabel.setText("loading orbits for rbspa");
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
        Runnable resetRecentRunnable= new Runnable() {
            @Override
            public void run() {
                resetRecent();
            }
        };
        new Thread(resetRecentRunnable,"resetRecent").start();
        DefaultListModel mm= new DefaultListModel();
        mm.add(0,"Loading recent time ranges...");
        recentTimesList.setModel( mm );
        
    }
    
    /**
     * set the selected range.  When the range is an orbit range (for example orbit:rbspa-pp:300),
     * the orbit list will be set.
     * @param s a timerange parseable with <code>DatumRangeUtil.parseTimeRange(s);</code>
     */
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
            if ( ss[1].startsWith("http://") 
                || ss[1].startsWith("https://") 
                || ss[1].startsWith("sftp://")
                || ss[1].startsWith("ftp://")
                || ss[1].startsWith("file:/") ) {
                int i= ss[1].indexOf(':',6);
                if ( i==-1 ) {
                    scComboBox.setSelectedItem(ss[1]);
                } else {
                    scComboBox.setSelectedItem(ss[1].substring(0,i) );
                    orbit= ss[1].substring(i+1);
                }
            } else {
                int i= ss[1].indexOf(':');
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

    /**
     * return the range selected, the type of which will depend on which tab is visible.
     * @return the range selected 
     */
    public String getSelectedRange() {
        int idx= jTabbedPane1.getSelectedIndex();
        switch (idx) {
            case 0:
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
            case 1:
                String sc= (String)scComboBox.getSelectedItem();
                String orb= (String)orbitList.getSelectedValue();
                List indexes= orbitList.getSelectedValuesList();
                if ( indexes.size()>1 ) {
                    StringBuilder orbits= new StringBuilder();
                    int i= orb.indexOf(':');
                    if ( i>-1 ) orbits.append(orb.substring(0,i)); else orbits.append(orb);
                    orbits.append("-");
                    orb= String.valueOf( indexes.get(indexes.size()-1) );
                    i= orb.indexOf(':');
                    if ( i>-1 ) orbits.append(orb.substring(0,i)); else orbits.append(orb);
                    orb= orbits.toString();
                } else {
                    if ( orb==null ) {
                        orbitList.setSelectedIndex(0);
                        orb= orbitList.getSelectedValue().toString();
                    }
                    int i= orb.indexOf(':');
                    if ( i>-1 ) orb= orb.substring(0,i);
                }
                return "orbit:"+sc+":"+orb;
            case 2: {
                String s= (String)nrtComboBox.getSelectedItem();
                int i= s.indexOf(' ');
                if ( i==-1 ) {
                    return s;
                } else {
                    return s.substring(0,i);
                }
            }
            case 3:  {
                String s= (String)recentTimesList.getSelectedValue();
                if ( s.startsWith("Loading")) {
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
                        s= timeRangeTextField.getText();
                    }
                }
                return s;
            }
            default:
                throw new IllegalArgumentException("not implemented");
        }
    }

    private String[] getSpacecraft() {
        Map<String,String> scs= Orbits.getSpacecraftIdExamples();
        String[] ss= scs.keySet().toArray( new String[scs.size()] );
        int n= ss.length;
        String[] result= new String[ n + extraSpacecraft.length ];
        System.arraycopy(ss, 0, result, 0, n);
        System.arraycopy(extraSpacecraft, 0, result, n, extraSpacecraft.length);
        return result;
    }
    
    private static String[] extraSpacecraft= new String[0];
    
    /**
     * Add additional spacecraft and orbit files to the 
     * this must be called before the GUI is created.
     * 
     * These will be the names of local orbit/event files or missions not
     * hard-coded into Autoplot.
     * 
     * @param scs 
     */
    public static void setAdditionalSpacecraftForOrbit( String[] scs ) {
        extraSpacecraft= Arrays.copyOf(scs,scs.length);
    }
    
    private void resetSpacecraft( final String sc  ) {
        resetSpacecraft(sc, null);
    }
    
    private void resetSpacecraft( final String sc, DatumRange focusRange  ) {
        logger.log(Level.FINE, "resetSpacecraft({0})", sc);

        final DefaultListModel mm= new DefaultListModel();
        final Orbits finalOrbits;
        
        prefs.put(PREF_SPACECRAFT, sc );
        
        String focusItem= null;
        Orbits o;
        try {
            o= Orbits.getOrbitsFor(sc);
            final List<String> ss= new ArrayList();
            String orb= o.first();
            boolean gotFocus= focusRange==null;

            while ( orb!=null ) {
                try {
                    String str = String.valueOf(o.getDatumRange(orb));
                    ss.add(orb+": "+str);
                    if ( !gotFocus && o.getDatumRange(orb).intersects(focusRange) ) {
                        if ( DatumRangeUtil.normalize( focusRange, o.getDatumRange(orb).max() ) < 0.5 ) { // check for round-off errors or overlaps.
                            logger.fine("trivial overlap ignored");
                        } else {
                            gotFocus=true;
                            focusItem= orb+": "+str;
                        }
                    }
                } catch (ParseException ex) { // this won't happen
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
                orb= o.next(orb);
            }

            for ( String s: ss ) {
                mm.addElement(s);
            }
                
        } catch ( IllegalArgumentException ex ) {
            o= null;
            mm.addElement(ex.getMessage());
        }
        
        finalOrbits= o;
        
        final String ffocusItem=focusItem;
        SwingUtilities.invokeLater( new Runnable() {
            @Override
            public void run() {
                orbitList.setModel( mm );
                if ( finalOrbits!=null && orbit.length()>0 ) {
                    try { // is enterred orbit legal orbit
                        finalOrbits.getDatumRange(orbit);
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
                    feedbackLabel.setText( "orbits from "+sc );
                } else {
                    if ( finalOrbits==null ) {
                        feedbackLabel.setText( "orbits from ERROR" );
                    } else {
                        feedbackLabel.setText( "orbits from "+finalOrbits.getURL()  );
                    }
                }
            }
        } );
        
    }
    
    /**
     * Read in the recent entries from recent.timerange.txt.  This should not be called on the event thread.
     */
    private void resetRecent() {
        int RECENT_SIZE=20;
        
        File bookmarksFolder= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "bookmarks" );
        File recentFile= new File( bookmarksFolder, "recent.timerange.txt" );
        List<String> items= new ArrayList( RECENT_SIZE+2 );
        
        try {
            if ( recentFile.exists() ) {
                try (BufferedReader r = new BufferedReader(new FileReader(recentFile))) {
                    String s= r.readLine();
                    while ( s!=null ) {
                        if ( verifier!=null ) {
                            if ( !verifier.verify(s) ) {
                                logger.log(Level.INFO, "invalid time found in recent.timerange.txt, dropping: {0}", s);
                                s= r.readLine();
                                continue;
                            }
                        }
                        items.add(s);
                        s= r.readLine();
                    }
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

            final DefaultListModel dlm= new DefaultListModel();
            for ( int i=0; i<items.size(); i++ ) {
                dlm.add( i, items.get(i) );
            }
            
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    recentTimesList.setModel( dlm );
                }
            });
            

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
        jButton1 = new javax.swing.JButton();
        nextIntervalButton = new javax.swing.JButton();
        prevIntervalButton = new javax.swing.JButton();
        zoomOutButton = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        scComboBox = new javax.swing.JComboBox();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        orbitFeedbackLabel = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        orbitList = new javax.swing.JList();
        feedbackLabel = new javax.swing.JLabel();
        orbitNumberTF = new javax.swing.JTextField();
        jPanel3 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        nrtComboBox = new javax.swing.JComboBox();
        jPanel5 = new javax.swing.JPanel();
        interpretationLabel = new javax.swing.JLabel();
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

        jButton1.setText("Copy");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        nextIntervalButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/nextNext.png"))); // NOI18N
        nextIntervalButton.setText("Next Interval");
        nextIntervalButton.setToolTipText("Scan to the next interval");
        nextIntervalButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextIntervalButtonActionPerformed(evt);
            }
        });

        prevIntervalButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/prevPrev.png"))); // NOI18N
        prevIntervalButton.setText("Previous Interval");
        prevIntervalButton.setToolTipText("Scan to the previous interval");
        prevIntervalButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prevIntervalButtonActionPerformed(evt);
            }
        });

        zoomOutButton.setText("Zoom Out");
        zoomOutButton.setToolTipText("Zoom out, making the span three times as wide with the same center");
        zoomOutButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomOutButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jLabel3, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jLabel7)
                    .add(jLabel6)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(12, 12, 12)
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(timeRangeTextField)
                            .add(jPanel1Layout.createSequentialGroup()
                                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(jPanel1Layout.createSequentialGroup()
                                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                            .add(jLabel8)
                                            .add(jLabel9))
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                            .add(stopTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 371, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                            .add(startTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 390, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                                    .add(jPanel1Layout.createSequentialGroup()
                                        .add(prevIntervalButton)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(zoomOutButton)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(nextIntervalButton)))
                                .add(0, 0, Short.MAX_VALUE)
                                .add(jButton1)))))
                .addContainerGap())
        );

        jPanel1Layout.linkSize(new java.awt.Component[] {startTextField, stopTextField}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

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
                    .add(startTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jButton1))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel9)
                    .add(stopTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(nextIntervalButton)
                    .add(prevIntervalButton)
                    .add(zoomOutButton))
                .addContainerGap(203, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Calendar", jPanel1);

        jLabel2.setText("Time Ranges by Orbit");

        scComboBox.setEditable(true);
        scComboBox.setToolTipText("Id of the orbits file or URL to orbits file");
        scComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                scComboBoxItemStateChanged(evt);
            }
        });

        jLabel4.setText("Spacecraft:");

        jLabel5.setText("Orbit:");

        orbitFeedbackLabel.setFont(orbitFeedbackLabel.getFont().deriveFont(orbitFeedbackLabel.getFont().getSize()-2f));
        orbitFeedbackLabel.setText("Shows selected timerange for orbit");

        jLabel10.setFont(jLabel10.getFont().deriveFont(jLabel10.getFont().getSize()-4f));
        jLabel10.setText("Select from predefined orbits, or spacecraft may be a URL to any three-column text file.");

        orbitList.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Loading orbits..." };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        orbitList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                orbitListValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(orbitList);

        feedbackLabel.setFont(feedbackLabel.getFont().deriveFont(feedbackLabel.getFont().getSize()-2f));
        feedbackLabel.setText("jLabel11");

        orbitNumberTF.setToolTipText("Enter orbit id and press Enter key");
        orbitNumberTF.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                orbitNumberTFActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jLabel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(feedbackLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(jLabel4, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 107, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(18, 18, 18)
                        .add(scComboBox, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jLabel10, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 591, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel2Layout.createSequentialGroup()
                        .add(jLabel5)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jScrollPane1)
                            .add(jPanel2Layout.createSequentialGroup()
                                .add(orbitFeedbackLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(orbitNumberTF, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 96, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))))
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
                .add(3, 3, 3)
                .add(feedbackLabel)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel5)
                    .add(orbitFeedbackLabel)
                    .add(orbitNumberTF, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 282, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabbedPane1.addTab("Orbits", jPanel2);

        jLabel1.setText("Near Real Time timeranges");

        nrtComboBox.setEditable(true);
        nrtComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "PT2H", "P1D", "P5D", "P30D", "now/now+P1D", "now/now+P5D", "now/now+P30D", "P1D/lastday", "lastday/P1D", "P2M/lastmonth", "lastyear/P1Y" }));
        nrtComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nrtComboBoxActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel5Layout = new org.jdesktop.layout.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 528, Short.MAX_VALUE)
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 89, Short.MAX_VALUE)
        );

        interpretationLabel.setText("---------");

        org.jdesktop.layout.GroupLayout jPanel3Layout = new org.jdesktop.layout.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jLabel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(jPanel3Layout.createSequentialGroup()
                .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel3Layout.createSequentialGroup()
                        .add(12, 12, 12)
                        .add(nrtComboBox, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .add(jPanel3Layout.createSequentialGroup()
                        .addContainerGap()
                        .add(jPanel5, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(jPanel3Layout.createSequentialGroup()
                        .addContainerGap()
                        .add(interpretationLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(nrtComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(interpretationLabel)
                .add(9, 9, 9)
                .add(jPanel5, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(233, Short.MAX_VALUE))
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
            .add(recentTimesListSP, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 603, Short.MAX_VALUE)
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(recentTimesListSP, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("Recent Times", jPanel4);

        jTabbedPane1.setSelectedIndex(1);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jTabbedPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 603, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jTabbedPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 435, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void scComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_scComboBoxItemStateChanged
        if ( evt.getStateChange()==java.awt.event.ItemEvent.DESELECTED ) {
            return;
        }
        final String sc= (String) scComboBox.getSelectedItem();
        Runnable run= new Runnable() {
            @Override
            public void run() {
                resetSpacecraft( sc, pendingTimeRange );
            }
        };
        new Thread( run, String.format( "loadOrbits-%010d",System.currentTimeMillis() ) ).start();

        if ( sc.contains(":") ) {
            feedbackLabel.setText("loading orbits from "+sc );
        } else {
            feedbackLabel.setText("loading orbits for "+sc );
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

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        stopTextField.setText( startTextField.getText() );
    }//GEN-LAST:event_jButton1ActionPerformed

    private void nextIntervalButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextIntervalButtonActionPerformed
        try {
            String min= startTextField.getText();
            String max= stopTextField.getText();
            Datum tmin= TimeUtil.create(min);
            Datum tmax= TimeUtil.create(max);
            DatumRange dr= new DatumRange( tmin, tmax );
            String s= timeRangeTextField.getText();
            DatumRange dr0= DatumRangeUtil.parseTimeRange(s);
            if ( dr.equals(dr0) ) {
                dr= dr0;
            }            
            dr= dr.next();
            startTextField.setText(dr.min().toString());
            stopTextField.setText(dr.max().toString());
            timeRangeTextField.setText( dr.toString() );
        } catch (ParseException ex) {
            Logger.getLogger(TimeRangeTool.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }//GEN-LAST:event_nextIntervalButtonActionPerformed

    private void prevIntervalButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prevIntervalButtonActionPerformed
        try {
            String min= startTextField.getText();
            String max= stopTextField.getText();
            Datum tmin= TimeUtil.create(min);
            Datum tmax= TimeUtil.create(max);
            DatumRange dr= new DatumRange( tmin, tmax );
            String s= timeRangeTextField.getText();
            DatumRange dr0= DatumRangeUtil.parseTimeRange(s);
            if ( dr.equals(dr0) ) {
                dr= dr0;
            }
            dr= dr.previous();
            startTextField.setText(dr.min().toString());
            stopTextField.setText(dr.max().toString());
            timeRangeTextField.setText( dr.toString() );
        } catch (ParseException ex) {
            Logger.getLogger(TimeRangeTool.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_prevIntervalButtonActionPerformed

    private void zoomOutButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomOutButtonActionPerformed
        try {
            String min= startTextField.getText();
            String max= stopTextField.getText();
            Datum tmin= TimeUtil.create(min);
            Datum tmax= TimeUtil.create(max);
            DatumRange dr= new DatumRange( tmin, tmax );
            String s= timeRangeTextField.getText();
            DatumRange dr0= DatumRangeUtil.parseTimeRange(s);
            if ( dr.equals(dr0) ) {
                dr= dr0;
            }
            DatumRange pp= dr.previous();
            DatumRange pn= dr.next();
            dr= pp.union(dr).union(pn);
            startTextField.setText(dr.min().toString());
            stopTextField.setText(dr.max().toString());
            timeRangeTextField.setText( dr.toString() );
        } catch (ParseException ex) {
            Logger.getLogger(TimeRangeTool.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_zoomOutButtonActionPerformed

    private void orbitNumberTFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_orbitNumberTFActionPerformed
        String orbit1= orbitNumberTF.getText();
        String s= showOrbit(orbit1);
        //Orbits o= Orbits.getOrbitsFor((String)scComboBox.getSelectedItem());
        setSelectedRange("orbit:"+(String)scComboBox.getSelectedItem()+":"+s);
    }//GEN-LAST:event_orbitNumberTFActionPerformed

    private void nrtComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nrtComboBoxActionPerformed
        interpretationLabel.setText( interpretIso8601Range( nrtComboBox.getSelectedItem().toString() ));
        try {
            dasAxis.setDatumRange( DatumRangeUtil.parseTimeRange( nrtComboBox.getSelectedItem().toString() ) );
        } catch ( ParseException ex ) {
            
        }
    }//GEN-LAST:event_nrtComboBoxActionPerformed

    /**
     * shows the orbit timerange, clipping off text past the first colon.
     * @param sorbit like "172: 2012-11-02 07:00 to 11:20"
     * @return canonical orbit string like "172"
     * TODO: this is sometimes called on the event thread, and will read files. NO NO!
     */
    private String showOrbit( String sorbit ) {
        try {
            Orbits o= Orbits.getOrbitsFor((String)scComboBox.getSelectedItem());
            int i= sorbit.indexOf(':');
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
        } catch ( IllegalArgumentException ex ) {
            return "";
        }
    }

    public static void main( String[] args ) {
        TimeRangeTool trt= new TimeRangeTool();
        JOptionPane.showConfirmDialog(null,trt);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel feedbackLabel;
    private javax.swing.JLabel interpretationLabel;
    private javax.swing.JButton jButton1;
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
    private javax.swing.JPanel jPanel5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JButton nextIntervalButton;
    private javax.swing.JComboBox nrtComboBox;
    private javax.swing.JLabel orbitFeedbackLabel;
    private javax.swing.JList orbitList;
    private javax.swing.JTextField orbitNumberTF;
    private javax.swing.JButton prevIntervalButton;
    private javax.swing.JList recentTimesList;
    private javax.swing.JScrollPane recentTimesListSP;
    private javax.swing.JComboBox scComboBox;
    private javax.swing.JTextField startTextField;
    private javax.swing.JTextField stopTextField;
    private javax.swing.JTextField timeRangeTextField;
    private javax.swing.JButton zoomOutButton;
    // End of variables declaration//GEN-END:variables

}
