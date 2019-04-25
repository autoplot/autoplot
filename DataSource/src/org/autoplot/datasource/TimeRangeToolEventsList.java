package org.autoplot.datasource;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import org.das2.components.DasProgressPanel;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.event.DataRangeSelectionEvent;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.qds.ops.Ops;

/**
 * The EventsList tool makes it easy to browse around a list of events.  It
 * takes a URI for a dataset which can be represented as an events list, 
 * and fires off timeRangeSelection events when an event is selected.  
 * 
 * @author jbf
 */
public class TimeRangeToolEventsList extends javax.swing.JPanel {

    private static final Logger logger= LoggerManager.getLogger("apdss.gui");
    
    DataSource dss= null;
    QDataSet currentDataSet= null;
    TimeSeriesBrowse tsb= null;
    boolean hasIcons= false;
    
    /**
     * Creates new form TimeRangeToolEventsList
     */
    public TimeRangeToolEventsList() {
        initComponents();
        fillList();
        jTable1.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            @Override
            public void valueChanged(ListSelectionEvent evt) {
                if (evt.getValueIsAdjusting()) {
                    logger.finest("value is adjusting");
                } else {
                    ListSelectionModel lsm= jTable1.getSelectionModel();                        
                    if (tsb != null) {
                        if (lsm.getMinSelectionIndex() == 0) {
                            loadTsb(-1);
                        } else if (lsm.getMaxSelectionIndex() == jTable1.getRowCount() - 1) {
                            loadTsb(1);
                        }
                    }
                    fireTableSelection();
                    Rectangle r = jTable1.getCellRect( lsm.getMinSelectionIndex(), 0, false );
                    r= r.union(  jTable1.getCellRect( lsm.getMaxSelectionIndex(), 0, false ) );
                    if (r != null) {
                        jTable1.scrollRectToVisible(r);
                    }
                }
            }
        });
    }

    /**
     * return the dataSetSelector, so that for example a button for bookmarks 
     * can be added by clients that know about bookmarks.
     * @return the DataSetSelector component.
     */
    public DataSetSelector getDataSetSelector() {
        return this.currentDataSetSelector;
    }
    
    /**
     * return the ith range in the current list.
     * @param i
     * @return 
     */
    private DatumRange getRange( int i ) {
        QDataSet ds1= currentDataSet.slice(i).trim(0,2); // it's a shame this doesn't get the units right...
        Units tu= (Units)((QDataSet)currentDataSet.property(QDataSet.BUNDLE_1)).property(QDataSet.UNITS,0);
        if ( ds1.value(0)<ds1.value(1) ) {
            return DatumRange.newDatumRange( ds1.value(0), ds1.value(1), tu );
        } else {
            logger.log(Level.INFO, "start and stop times are out-of-order at index {0}", i);
            return DatumRange.newDatumRange( ds1.value(1), ds1.value(0), tu );
        }
    }
    
    private void fillWithEmpty( DefaultTableModel tm ) {
        for ( int i=0; i<tm.getRowCount(); i++ ) {
            for ( int j=0; j<tm.getColumnCount(); j++ ) {
                tm.setValueAt( "", i,j );
            }
        }
    }
    /**
     * populate the list.
     */
    private void fillList() {         
        final DefaultTableModel tm;
        if ( currentDataSet==null ) {
            tm= new DefaultTableModel( new String[] { "Range", "Label" }, 3 );        
            fillWithEmpty( tm );
            if ( tsb==null ) {
                tm.setValueAt( "(no intervals loaded)", 0, 0 );                
            } else {
                tm.setValueAt( "Load Previous Set...", 0, 0 );
                tm.setValueAt( "("+tsb.getTimeRange()+" contains no intervals)", 1, 0 );   
                tm.setValueAt( "Load Next Set...", 2, 0 );
            }
        } else {
            int offset= tsb==null ? 0 : 1;
            tm= new DefaultTableModel( new String[] { "Range", "Label" }, currentDataSet.length()+offset*2 );
            if ( tsb!=null ) {
                tm.setValueAt( "Load Previous Set...", 0, 0 );
                tm.setValueAt( "", 0, 1 );
            }
            QDataSet bds= (QDataSet) currentDataSet.property(QDataSet.BUNDLE_1);
            Units eu= (Units) bds.property(QDataSet.UNITS,3);
            for ( int i=0; i<currentDataSet.length(); i++ ) {
                tm.setValueAt( getRange(i).toString(), i+offset, 0 );
                String s= eu.createDatum( currentDataSet.slice(i).value(3) ).toString();    
                tm.setValueAt( s, i+offset, 1 );
            }
            if ( tsb!=null ) {
                tm.setValueAt( "Load Next Set...", currentDataSet.length()+1, 0 );
                tm.setValueAt( "", currentDataSet.length()+1, 1 );
            }
        }
        
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                jTable1.setModel(tm);
                jTable1.setDefaultRenderer( Object.class, tableCellRenderer );
            }
        });
        
        
    }
    
    TableCellRenderer tableCellRenderer= new TableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel result= new JLabel(String.valueOf(value));
            
            int index= row;
            
            result.setIcon(null);
            
            Color background, foreground;
            
            if (isSelected) {
                background = jTable1.getSelectionBackground();
                foreground = jTable1.getSelectionForeground();
            } else {
                background = jTable1.getBackground();
                foreground = jTable1.getForeground();
            }
            
            result.setOpaque(true);
            result.setForeground(foreground);
            result.setBackground(background);
            
            if ( currentDataSet==null ) {
                return result;
            }
            
            QDataSet rec;
            
            boolean nextPreviousItem= tsb!=null && ( ( index==0 ) || (index==currentDataSet.length()+1 ) );
            if ( nextPreviousItem ) {
                return result;
            } 
                
            try {
                if ( tsb==null ) {
                    rec= currentDataSet.slice(index);
                } else {
                    rec= currentDataSet.slice(index-1);                
                }
            } catch ( IndexOutOfBoundsException ex ) {
                return result; //?????
            }
            
            QDataSet bds= (QDataSet) currentDataSet.property(QDataSet.BUNDLE_1);
            Units eu= (Units) bds.property(QDataSet.UNITS,3);
            String s= eu.createDatum(rec.value(3)).toString();            
            
            if ( column==0 ) {
                result.setText( String.valueOf(value) );
            } else {
                result.setText( s );
            }
            
            if ( hasIcons && column==0) {
                int color= (int)rec.value(2);
                Icon icon= colorIcon( new Color(color), 12,12 );
                result.setIcon(icon);
            }
            
            return result;
        }
        
    };
            
    /**
     * return a block with the color and size.
     * @param w
     * @param h
     * @return 
     */
    private static Icon colorIcon( Color iconColor, int w, int h ) {
        BufferedImage image= new BufferedImage( w, h, BufferedImage.TYPE_INT_ARGB );
        Graphics g= image.getGraphics();
        g.setColor( Color.WHITE );
        g.fillRect( 0,0,w,h );
        g.setColor( new Color( iconColor.getRed(), iconColor.getGreen(), iconColor.getBlue(), 140 ) );
        g.fillRect( 0, 0, w, h );
        return new ImageIcon(image);
    }    

    /**
     * make canonical rank 2 bundle dataset of min,max,color,text
     * @param vds
     * @return
     */
    public static QDataSet makeCanonical( QDataSet vds ) {
        return Ops.createEvents(vds);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        rescaleComboBox = new javax.swing.JComboBox();
        jLabel1 = new javax.swing.JLabel();
        prevButton = new javax.swing.JButton();
        nextButton = new javax.swing.JButton();
        currentDataSetSelector = new org.autoplot.datasource.DataSetSelector();
        timeRangeTF = new javax.swing.JTextField();
        timeRangeButton = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();

        rescaleComboBox.setEditable(true);
        rescaleComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "none", "-5%,105%", "-10%,110%", "-100%,200%", "-300%,400%" }));
        rescaleComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rescaleComboBoxActionPerformed(evt);
            }
        });

        jLabel1.setText("Rescale:");
        jLabel1.setToolTipText("Expand the interval range");

        prevButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/datasource/prevPrev.png"))); // NOI18N
        prevButton.setToolTipText("Previous Interval");
        prevButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prevButtonActionPerformed(evt);
            }
        });

        nextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/datasource/nextNext.png"))); // NOI18N
        nextButton.setToolTipText("Next Interval");
        nextButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextButtonActionPerformed(evt);
            }
        });

        currentDataSetSelector.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                currentDataSetSelectorFocusGained(evt);
            }
        });
        currentDataSetSelector.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                currentDataSetSelectorActionPerformed(evt);
            }
        });

        timeRangeTF.setText(" ");
        timeRangeTF.setToolTipText("Load events from the given timerange");
        timeRangeTF.setEnabled(false);
        timeRangeTF.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeRangeTFActionPerformed(evt);
            }
        });

        timeRangeButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/datasource/calendar.png"))); // NOI18N
        timeRangeButton.setToolTipText("Time Range Tool for setting the interval range");
        timeRangeButton.setEnabled(false);
        timeRangeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeRangeButtonActionPerformed(evt);
            }
        });

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jTable1.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                jTable1FocusLost(evt);
            }
        });
        jTable1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jTable1MouseClicked(evt);
            }
        });
        jScrollPane2.setViewportView(jTable1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(rescaleComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 137, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(prevButton, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(nextButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(timeRangeTF, javax.swing.GroupLayout.PREFERRED_SIZE, 196, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timeRangeButton)
                .addContainerGap())
            .addComponent(currentDataSetSelector, javax.swing.GroupLayout.DEFAULT_SIZE, 573, Short.MAX_VALUE)
            .addComponent(jScrollPane2)
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {nextButton, prevButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(currentDataSetSelector, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(prevButton, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(rescaleComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabel1)
                        .addComponent(nextButton)
                        .addComponent(timeRangeTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(timeRangeButton)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 270, Short.MAX_VALUE)
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {nextButton, prevButton});

    }// </editor-fold>//GEN-END:initComponents

    /** Registers DataRangeSelectionListener to receive events.
     * @param listener The listener to register.
     */
    public void addDataRangeSelectionListener(org.das2.event.DataRangeSelectionListener listener) {
        listenerList.add(org.das2.event.DataRangeSelectionListener.class, listener);
    }
    
    /** Removes DataRangeSelectionListener from the list of listeners.
     * @param listener The listener to remove.
     */
    public void removeDataRangeSelectionListener(org.das2.event.DataRangeSelectionListener listener) {
        listenerList.remove(org.das2.event.DataRangeSelectionListener.class, listener);
    }
    
    
    /** Notifies all registered listeners about the event.
     *
     * @param event The event to be fired
     */
    private void fireDataRangeSelectionListenerDataRangeSelected(DataRangeSelectionEvent event) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length-2; i>=0; i-=2) {
            if (listeners[i]==org.das2.event.DataRangeSelectionListener.class) {
                ((org.das2.event.DataRangeSelectionListener)listeners[i+1]).dataRangeSelected(event);
            }
        }
    }
    
    private void fireTableSelection() {
        int i1= jTable1.getSelectionModel().getMinSelectionIndex();
        int i2= jTable1.getSelectionModel().getMaxSelectionIndex();
        if ( currentDataSet==null ) return;
        DatumRange fire= null;
        String rescale= (String)rescaleComboBox.getSelectedItem();
        for ( int i=i1; i<=i2; i++ ) {
            if ( jTable1.getSelectionModel().isSelectedIndex(i) ) {
                int isel= (tsb!=null) ? i-1 : i;
                if ( isel>=0 && ( currentDataSet==null || isel<currentDataSet.length() ) ) {
                    if ( fire==null ) {
                        fire= getRange(isel);
                    } else {
                        fire= DatumRangeUtil.union( fire, getRange(isel) );
                    }
                }
            }
        }
        if ( fire==null ) {
            return;
        }
        if ( UnitsUtil.isNominalMeasurement( fire.getUnits() ) ) {
            // Dave saw this. rte_1250873233_20150902_170348_ddm.xml
            return;
        }
        if ( fire.width().value()==0 ) {
            logger.fine("zero width.");
            Units tu;
            if ( UnitsUtil.isTimeLocation(fire.min().getUnits()) ) {
                tu= Units.seconds;
            } else {
                tu= fire.min().getUnits().getOffsetUnits();
            }
            fire= new DatumRange( fire.min().subtract(1,tu), fire.min().add(1,tu) );
        }
        if ( rescaleComboBox.getSelectedIndex()>0 ) {
            try {
                fire= DatumRangeUtil.rescale( fire,rescale );
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        fireDataRangeSelectionListenerDataRangeSelected( new DataRangeSelectionEvent(this,fire) );
        
    }                                          
    
    private void prevButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prevButtonActionPerformed
        int i0= jTable1.getSelectionModel().getMinSelectionIndex();
        int i1= jTable1.getSelectionModel().getMaxSelectionIndex();
        if ( i0==-1 ) {
            i0= 0;
            i1= 0;  
        }
        int de= i1-i0+1;
        i0= Math.max(0,i0-1);
        i1= i0 + de-1;
        jTable1.getSelectionModel().setSelectionInterval(i0,i1);

    }//GEN-LAST:event_prevButtonActionPerformed

    private void nextButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextButtonActionPerformed
        int i0= jTable1.getSelectionModel().getMinSelectionIndex();
        int i1= jTable1.getSelectionModel().getMaxSelectionIndex();
        if ( i0==-1 ) {
            i0= 0;
            i1= 0;  
        }
        int de= i1-i0+1;
        i1= Math.min( jTable1.getRowCount()-1,i1+1);
        i0= i1-de+1;
        //int[] indexes= new int[de];
        //for ( int i=i0; i<=i1; i++ ) indexes[i-i0]= i;
        jTable1.getSelectionModel().setSelectionInterval(i0,i1);
    }//GEN-LAST:event_nextButtonActionPerformed

    private void rescaleComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rescaleComboBoxActionPerformed
        //fireSelection();
        fireTableSelection();
    }//GEN-LAST:event_rescaleComboBoxActionPerformed

    /**
     * load the next or previous interval of events.
     * @param dir -1 for previous, +1 for next.
     */
    private void loadTsb(final int dir) {
        jTable1.setEnabled(false);
        Runnable run= new Runnable() {
            @Override
            public void run() {
                try {
                    DatumRange current= tsb.getTimeRange();
                    if ( dir==-1 ) {
                        current= current.previous();
                    } else {
                        current= current.next();
                    }
                    loadViaTsb( current, dir );
                } finally {
                    jTable1.setEnabled(true);
                }
            }
        };
        new Thread(run).start();
    }
    
    /**
     * Load the events for the given range.
     * @param range range to load
     * @param dir -1 was going back to it, 1 is going ahead
     */
    private void loadViaTsb( final DatumRange range, int dir ) {
        tsb.setTimeRange( range );
        ProgressMonitor mon= DasProgressPanel.createFramed(SwingUtilities.getWindowAncestor(TimeRangeToolEventsList.this),"Loading Events File...");
        try {
            QDataSet currentDataSet1= dss.getDataSet(mon.getSubtaskMonitor("Load Data"));
            currentDataSet1= makeCanonical(currentDataSet1);
            //TODO: someone is going to want to trim to this range.  QDataSet rr= Ops.trim()
            currentDataSet= SemanticOps.trim( currentDataSet1, range, null );
        } catch ( Exception ex ) {
            currentDataSet= null;
        } finally {
            mon.finished();
            fillList();
            final int i;
            if ( dir==-1 ) {
                i= jTable1.getRowCount()-2;
            } else if ( dir==1 ) {
                i= 1;
            } else {
                i= -1;
                // don't set anything
            }
            Runnable run= new Runnable() {
                public void run() {
                    if ( i>0 ) {
                        if ( currentDataSet!=null && currentDataSet.length()>0 ) {
                            jTable1.getSelectionModel().setSelectionInterval(i,i);
                            jTable1.setEnabled(true);
                        }
                    }
                    timeRangeTF.setText( range.toString() );
                }
            };
            SwingUtilities.invokeLater(run);
        }
    }
    private void currentDataSetSelectorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_currentDataSetSelectorActionPerformed
        final String uri= (String)currentDataSetSelector.getValue();
        Runnable run= new Runnable() {
            @Override
            public void run() {
                ProgressMonitor mon= DasProgressPanel.createFramed(SwingUtilities.getWindowAncestor(TimeRangeToolEventsList.this),"Loading Events File...");
                try {
                    TimeRangeToolEventsList.this.tsb= null;
                    DataSource dsource = DataSetURI.getDataSource(uri);
                    dss= dsource;
                    QDataSet currentDataSet1= dss.getDataSet(mon);
                    tsb= dsource.getCapability( TimeSeriesBrowse.class );
                    if ( tsb!=null ) {
                        timeRangeTF.setText(tsb.getTimeRange().toString());
                        timeRangeTF.setEnabled(true);
                        timeRangeButton.setEnabled(true);
                    } else {
                        timeRangeTF.setText("");
                        timeRangeTF.setEnabled(false);
                        timeRangeButton.setEnabled(false);
                    }
                    currentDataSet1= makeCanonical(currentDataSet1);
                    if ( tsb!=null ) {
                        currentDataSet1= SemanticOps.trim( currentDataSet1, tsb.getTimeRange(), null );
                    }
                    currentDataSet= currentDataSet1;
                    hasIcons= false;
                    if ( currentDataSet.length()>0 ) {
                        double color0= currentDataSet.value(0,2);
                        for ( int i=0; i<currentDataSet.length(); i++ ) {
                            if ( currentDataSet.value(i,2)!=color0 ) {
                                hasIcons= true;
                            }
                        }
                    }
                    fillList( );
                } catch (Exception ex) {                    
                    throw new RuntimeException(ex);
                } finally {
                    currentDataSetSelector.setEnabled(true);
                    if ( !mon.isFinished() ) mon.finished();
                }
            }
        };
        currentDataSetSelector.setEnabled(false);
        new Thread(run).start();
           
    }//GEN-LAST:event_currentDataSetSelectorActionPerformed

    private void timeRangeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeRangeButtonActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        TimeRangeTool tt= new TimeRangeTool();
        JTextField tf= timeRangeTF;
        tt.setSelectedRange(tf.getText());
        int r= JOptionPane.showConfirmDialog( this, tt, "Select Time Range", JOptionPane.OK_CANCEL_OPTION );
        if ( r==JOptionPane.OK_OPTION) {
            tf.setText(tt.getSelectedRange());
            String str= timeRangeTF.getText();
            final DatumRange drtr= DatumRangeUtil.parseTimeRangeValid(str);
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    loadViaTsb( drtr, 0 );
                }
            };
            new Thread(run).start();   
        }
    }//GEN-LAST:event_timeRangeButtonActionPerformed

    private void timeRangeTFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeRangeTFActionPerformed
        try {
            String str= timeRangeTF.getText();
            final DatumRange drtr= DatumRangeUtil.parseTimeRange(str);
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    loadViaTsb( drtr, 0 );
                }
            };
            new Thread(run).start();
            
        } catch (ParseException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_timeRangeTFActionPerformed

    private void jTable1MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTable1MouseClicked
        
    }//GEN-LAST:event_jTable1MouseClicked

    private void jTable1FocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_jTable1FocusLost
        //System.err.println("table focus lost");
    }//GEN-LAST:event_jTable1FocusLost

    private void currentDataSetSelectorFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_currentDataSetSelectorFocusGained
        //System.err.println("dataSetSelector focus gained");        // TODO add your handling code here:
    }//GEN-LAST:event_currentDataSetSelectorFocusGained

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.autoplot.datasource.DataSetSelector currentDataSetSelector;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTable jTable1;
    private javax.swing.JButton nextButton;
    private javax.swing.JButton prevButton;
    private javax.swing.JComboBox rescaleComboBox;
    private javax.swing.JButton timeRangeButton;
    private javax.swing.JTextField timeRangeTF;
    // End of variables declaration//GEN-END:variables
}
