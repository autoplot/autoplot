/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource;

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
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import org.das2.components.DasProgressPanel;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.event.DataRangeSelectionEvent;
import org.das2.graph.GraphUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.dsops.Ops;

/**
 * Introduce tool to make it easy to browse around a list of events.
 * This is in the events list format, and will probably become part of the
 * timerange GUI.
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
        //currentDataSetSelector.setRecent( AutoplotUtil.getUrls(applicationModel.getRecent()) );
    }

    /**
     * return the dataSetSelector, so that a button for bookmarks can be added.
     * @return 
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
    
    /**
     * populate the list.
     */
    private void fillList() {
        DefaultListModel lm= new DefaultListModel();
        if ( tsb!=null ) {
            lm.add(lm.getSize(),"Load Previous Set...");
        }
        if ( currentDataSet==null ) {
            if ( tsb==null ) {
                lm.add(lm.getSize(),"(no intervals loaded)");                
            } else {
                lm.add(lm.getSize(),"("+tsb.getTimeRange()+" contains no intervals)");
            }
        } else {
            for ( int i=0; i<currentDataSet.length(); i++ ) {
                String ss= getRange(i).toString();
                lm.add(lm.getSize(),ss);
            }
        }
        if ( tsb!=null ) {
            lm.add(lm.getSize(),"Load Next Set...");
        }
        intervalsList.setModel( lm );
        intervalsList.setCellRenderer( listCellRenderer );
    }
    
    ListCellRenderer listCellRenderer= new ListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel result= new JLabel(String.valueOf(value));
            
            result.setIcon(null);
            
            Color background, foreground;
            
            if (isSelected) {
                background = list.getSelectionBackground();
                foreground = list.getSelectionForeground();
            } else {
                background = list.getBackground();
                foreground = list.getForeground();
            }
            
            result.setOpaque(true);
            result.setForeground(foreground);
            result.setBackground(background);
            
            if ( currentDataSet==null ) {
                return result;
            }
            
            QDataSet rec;
            
            if ( tsb==null ) {
                rec= currentDataSet.slice(index);
            } else {
                if ( index==0 ) {
                    return result;
                } else if ( index==currentDataSet.length() ) {
                    return result;
                }
                if ( index-1 >= currentDataSet.length() ) {
                    return result;
                } else {
                    rec= currentDataSet.slice(index-1);                
                }
            }
            
            QDataSet bds= (QDataSet) currentDataSet.property(QDataSet.BUNDLE_1);
            Units eu= (Units) bds.property(QDataSet.UNITS,3);
            String s= eu.createDatum(rec.value(3)).toString();            
            
            if ( hasIcons ) {
                result.setText( String.valueOf(value)+": "+s );
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

        QDataSet xmins;
        QDataSet xmaxs;
        QDataSet colors;
        QDataSet msgs;

        Color defaultColor= new Color( 240,240,240,0 ); // transparent
        
        if ( vds==null ) {
            return null;
        }
        if ( vds.rank()==2 ) {
            QDataSet dep0= (QDataSet) vds.property(QDataSet.DEPEND_0);
            if ( dep0==null ) {
                xmins= DataSetOps.unbundle( vds,0 );
                xmaxs= DataSetOps.unbundle( vds,1 );

                if ( vds.length(0)>3 ) {
                    colors= DataSetOps.unbundle( vds,2 );
                } else {
                    colors= Ops.replicate( defaultColor.getRGB(), xmins.length() );
                }
                
            } else if ( dep0.rank()==2 ) {
                if ( SemanticOps.isBins(dep0) ) {
                    xmins= DataSetOps.slice1( dep0, 0 );
                    xmaxs= DataSetOps.slice1( dep0, 1 );
                    colors= Ops.replicate( 0x808080, xmins.length() );
                    Units u0= SemanticOps.getUnits(xmins );
                    Units u1= SemanticOps.getUnits(xmaxs );
                    if ( !u1.isConvertableTo(u0) && u1.isConvertableTo(u0.getOffsetUnits()) ) {
                        xmaxs= Ops.add( xmins, xmaxs );
                    }
                } else {
                    throw new IllegalArgumentException( "DEPEND_0 is rank 2 but not bins" );
                }
                
            } else  if ( dep0.rank() == 1 ) {
                Datum width= SemanticOps.guessXTagWidth( dep0, null ).divide(2);
                xmins= Ops.subtract( dep0, org.virbo.dataset.DataSetUtil.asDataSet(width) );
                xmaxs= Ops.add( dep0, org.virbo.dataset.DataSetUtil.asDataSet(width) );
                colors= Ops.replicate( defaultColor.getRGB(), xmins.length() );

            } else {
                throw new IllegalArgumentException( "rank 2 dataset must have dep0 of rank 1 or rank 2 bins" );
            }

            msgs= DataSetOps.unbundle( vds, vds.length(0)-1 );

        } else if ( vds.rank()==1 ) {
            QDataSet dep0= (QDataSet) vds.property(QDataSet.DEPEND_0);
            if ( dep0==null ) {
                xmins= vds;
                xmaxs= vds;
                msgs= vds;
            } else if ( dep0.rank() == 2  ) {
                if ( SemanticOps.isBins(dep0) ) {
                    xmins= DataSetOps.slice1( dep0, 0 );
                    xmaxs= DataSetOps.slice1( dep0, 1 );
                    Units u0= SemanticOps.getUnits(xmins );
                    Units u1= SemanticOps.getUnits(xmaxs );
                    if ( !u1.isConvertableTo(u0) && u1.isConvertableTo(u0.getOffsetUnits()) ) {
                        xmaxs= Ops.add( xmins, xmaxs );
                    }
                    msgs= vds;
                } else {
                    throw new IllegalArgumentException( "DEPEND_0 is rank 2 but not bins" );
                }
            } else if ( dep0.rank() == 1 ) {
                Datum width= SemanticOps.guessXTagWidth( dep0, null );
                if ( width!=null ) {
                    width= width.divide(2);
                } else {
                    QDataSet sort= Ops.sort(dep0);
                    QDataSet diffs= Ops.diff( DataSetOps.applyIndex(dep0,0,sort,false) );
                    QDataSet w= Ops.reduceMin( diffs,0 );
                    width= DataSetUtil.asDatum(w);                    
                }
                xmins= Ops.subtract( dep0, org.virbo.dataset.DataSetUtil.asDataSet(width) );
                xmaxs= Ops.add( dep0, org.virbo.dataset.DataSetUtil.asDataSet(width) );                
                msgs= vds;
            } else {
                throw new IllegalArgumentException( "dataset is not correct form" );
            }
            Color c0= defaultColor;
            Color c1= new Color( c0.getRed(), c0.getGreen(), c0.getBlue(), c0.getAlpha()==255 ? 128 : c0.getAlpha() );
            int irgb= c1.getRGB();
            
            colors= Ops.replicate( irgb, xmins.length() );

        } else if ( vds.rank()==0 ) {
            xmins= Ops.replicate(vds,1); // increase rank from 0 to 1.
            xmaxs= xmins;
            Color c0= defaultColor;
            Color c1= new Color( c0.getRed(), c0.getGreen(), c0.getBlue(), c0.getAlpha()==255 ? 128 : c0.getAlpha() );
            int irgb= c1.getRGB();
            colors= Ops.replicate( irgb, xmins.length() );
            msgs= Ops.replicate(vds,1);
        } else {            
            throw new IllegalArgumentException( "dataset must be rank 0, 1 or 2" );
        }

        Units u0= SemanticOps.getUnits( xmins );
        Units u1= SemanticOps.getUnits( xmaxs );

        if ( u1.isConvertableTo( u0.getOffsetUnits() ) && !u1.isConvertableTo(u0) ) { // maxes are dt instead of stopt.
            xmaxs= Ops.add( xmins, xmaxs );
        }

        QDataSet lds= Ops.bundle( xmins, xmaxs, colors, msgs );
        
        return lds;

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        intervalsList = new javax.swing.JList();
        rescaleComboBox = new javax.swing.JComboBox();
        jLabel1 = new javax.swing.JLabel();
        prevButton = new javax.swing.JButton();
        nextButton = new javax.swing.JButton();
        currentDataSetSelector = new org.virbo.datasource.DataSetSelector();

        intervalsList.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        intervalsList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                intervalsListValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(intervalsList);

        rescaleComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "none", "-5%,105%", "-10%,110%", "-100%,200%" }));
        rescaleComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rescaleComboBoxActionPerformed(evt);
            }
        });

        jLabel1.setText("Rescale:");

        prevButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/virbo/datasource/prevPrev.png"))); // NOI18N
        prevButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prevButtonActionPerformed(evt);
            }
        });

        nextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/virbo/datasource/nextNext.png"))); // NOI18N
        nextButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextButtonActionPerformed(evt);
            }
        });

        currentDataSetSelector.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                currentDataSetSelectorActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(rescaleComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 137, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(prevButton, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(nextButton)
                .addContainerGap())
            .addComponent(currentDataSetSelector, javax.swing.GroupLayout.DEFAULT_SIZE, 438, Short.MAX_VALUE)
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
                        .addComponent(nextButton)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 234, Short.MAX_VALUE))
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
    
    private void intervalsListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_intervalsListValueChanged
        if ( evt.getValueIsAdjusting() ) {
            logger.finest("value is adjusting");
        } else {
            if ( tsb!=null ) {
                if ( intervalsList.getMinSelectionIndex()==0 ) {
                    loadTsb(-1);
                } else if ( intervalsList.getMaxSelectionIndex()==intervalsList.getModel().getSize()-1 ) {
                    loadTsb(1);   
                }
            }
            fireSelection();
            Rectangle r= intervalsList.getCellBounds(intervalsList.getMinSelectionIndex(),intervalsList.getMaxSelectionIndex());
            if ( r!=null ) intervalsList.scrollRectToVisible(r);
        }
    } 
    
    private void fireSelection() {
        int i1= intervalsList.getMinSelectionIndex();
        int i2= intervalsList.getMaxSelectionIndex();
        if ( currentDataSet==null ) return;
        DatumRange fire= null;
        String rescale= (String)rescaleComboBox.getSelectedItem();
        for ( int i=i1; i<=i2; i++ ) {
            if ( intervalsList.isSelectedIndex(i) ) {
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
        fireDataRangeSelectionListenerDataRangeSelected( new DataRangeSelectionEvent(this,fire.min(),fire.max()) );
        
    }//GEN-LAST:event_intervalsListValueChanged

    private void prevButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prevButtonActionPerformed
        int i0= intervalsList.getMinSelectionIndex();
        int i1= intervalsList.getMaxSelectionIndex();
        if ( i0==-1 ) {
            i0= 0;
            i1= 0;  
        }
        int de= i1-i0+1;
        i0= Math.max(0,i0-1);
        i1= i0 + de-1;
        int[] indexes= new int[de];
        for ( int i=i0; i<=i1; i++ ) {
            indexes[i-i0]= i;
        }
        intervalsList.setSelectedIndices(indexes);
    }//GEN-LAST:event_prevButtonActionPerformed

    private void nextButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextButtonActionPerformed
        int i0= intervalsList.getMinSelectionIndex();
        int i1= intervalsList.getMaxSelectionIndex();
        if ( i0==-1 ) {
            i0= 0;
            i1= 0;  
        }
        int de= i1-i0+1;
        i1= Math.min(intervalsList.getModel().getSize()-1,i1+1);
        i0= i1-de+1;
        int[] indexes= new int[de];
        for ( int i=i0; i<=i1; i++ ) indexes[i-i0]= i;
        intervalsList.setSelectedIndices(indexes);
    }//GEN-LAST:event_nextButtonActionPerformed

    private void rescaleComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rescaleComboBoxActionPerformed
        fireSelection();
    }//GEN-LAST:event_rescaleComboBoxActionPerformed

    /**
     * load the next or previous interval of events.
     * @param dir -1 for previous, +1 for next.
     */
    private void loadTsb(final int dir) {
        intervalsList.setEnabled(false);
        Runnable run= new Runnable() {
            @Override
            public void run() {
                DatumRange current= tsb.getTimeRange();
                if ( dir==-1 ) {
                    current= current.previous();
                } else {
                    current= current.next();
                }
                tsb.setTimeRange(current);
                ProgressMonitor mon= DasProgressPanel.createFramed(SwingUtilities.getWindowAncestor(TimeRangeToolEventsList.this),"Loading Events File...");
                try {
                    QDataSet currentDataSet1= dss.getDataSet(mon);
                    currentDataSet= makeCanonical(currentDataSet1);
                } catch ( Exception ex ) {
                    currentDataSet= null;
                } finally {
                    fillList();
                    if ( dir==-1 ) {
                        intervalsList.setSelectedIndex(intervalsList.getModel().getSize()-2);
                    } else {
                        intervalsList.setSelectedIndex(1);
                    }
                    intervalsList.setEnabled(true);
                }
            }
        };
        new Thread(run).start();
    }
    
    private void currentDataSetSelectorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_currentDataSetSelectorActionPerformed
        final String uri= (String)currentDataSetSelector.getValue();
        Runnable run= new Runnable() {
            @Override
            public void run() {
                try {
                    TimeRangeToolEventsList.this.tsb= null;
                    DataSource dsource = DataSetURI.getDataSource(uri);
                    dss= dsource;
                    ProgressMonitor mon= DasProgressPanel.createFramed(SwingUtilities.getWindowAncestor(TimeRangeToolEventsList.this),"Loading Events File...");
                    currentDataSet= dss.getDataSet(mon);
                    tsb= dsource.getCapability( TimeSeriesBrowse.class );
                    currentDataSet= makeCanonical(currentDataSet);
                    hasIcons= false;
                    if ( currentDataSet.length()>0 ) {
                        double color0= currentDataSet.value(0,2);
                        for ( int i=0; i<currentDataSet.length(); i++ ) {
                            if ( currentDataSet.value(i,2)!=color0 ) {
                                hasIcons= true;
                            }
                        }
                    }
                    currentDataSetSelector.setEnabled(true);
                    fillList( );
                } catch (Exception ex) {
                    Logger.getLogger(TimeRangeToolEventsList.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        currentDataSetSelector.setEnabled(false);
        new Thread(run).start();
           
    }//GEN-LAST:event_currentDataSetSelectorActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.virbo.datasource.DataSetSelector currentDataSetSelector;
    private javax.swing.JList intervalsList;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton nextButton;
    private javax.swing.JButton prevButton;
    private javax.swing.JComboBox rescaleComboBox;
    // End of variables declaration//GEN-END:variables
}
