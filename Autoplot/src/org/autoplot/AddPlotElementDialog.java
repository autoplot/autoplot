/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * AddPlotElementDialog.java
 *
 * Created on Apr 6, 2009, 10:54:02 AM
 */

package org.autoplot;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import org.autoplot.bookmarks.Bookmark;
import org.autoplot.bookmarks.BookmarksManager;
import org.autoplot.datasource.DataSetSelector;

/**
 * Allow additional plots to be added, and plot one URI against another.
 * @author jbf
 */
public class AddPlotElementDialog extends javax.swing.JDialog {

    /** 
     * Creates new form AddPlotElementDialog
     * @param parent the parent
     * @param modal true if the dialog should be modal
     */
    public AddPlotElementDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();
        messagesLabel.setVisible(false);
        invalidate();
        setLocationRelativeTo(parent);
        secondaryDataSetSelector.setVisible(secondaryCheckBox.isSelected());
        tertiaryCheckBox.setVisible(secondaryCheckBox.isSelected());
        tertiaryDataSetSelector.setVisible(secondaryCheckBox.isSelected()&&tertiaryCheckBox.isSelected());
        
        addAdditionalVisibleListener( null, primaryFiltersCB, primaryFiltersComboBox );
        addAdditionalVisibleListener( secondaryCheckBox, secondaryFiltersCB, secondaryFiltersComboBox );
        addAdditionalVisibleListener( tertiaryCheckBox, tertiaryFiltersCB, tertiaryFiltersComboBox );
        
        secondaryFiltersCB.setVisible(secondaryDataSetSelector.isVisible());
        
        if ( parent instanceof AutoplotUI ) {
            DataSetSelector source= null;
            source= ((AutoplotUI)parent).getDataSetSelector();
            primaryDataSetSelector.setTimeRange( source.getTimeRange() );
            secondaryDataSetSelector.setTimeRange( source.getTimeRange() );
            tertiaryDataSetSelector.setTimeRange( source.getTimeRange() );
        }
        
        Icon bookmarkIcon= new javax.swing.ImageIcon(getClass().getResource("/resources/purplebookmark.png") );

        primaryDataSetSelector.replacePlayButton( bookmarkIcon, new AbstractAction("bookmarks") {
            @Override
            public void actionPerformed(ActionEvent e) {
                doBookmarks(primaryDataSetSelector);
            }
        });

        secondaryDataSetSelector.replacePlayButton( bookmarkIcon, new AbstractAction("bookmarks") {
            @Override
            public void actionPerformed(ActionEvent e) {
                doBookmarks(secondaryDataSetSelector);
            }
        });

        tertiaryDataSetSelector.replacePlayButton( bookmarkIcon, new AbstractAction("bookmarks") {
            @Override
            public void actionPerformed(ActionEvent e) {
                doBookmarks(tertiaryDataSetSelector);
            }
        });
    }
    
    /**
     * add additional text to the dialog.
     * @param text 
     */
    public void setMessagesLabelText( String text ) {
        if ( text==null || text.trim().length()==0 ) {
            messagesLabel.setVisible(false);
        } else {
            messagesLabel.setText(text);
            messagesLabel.setVisible(true);
        }
        revalidate();
    }
    
    /**
     * show and hide the filters/process fields, which are not normally used.
     * @param enabled
     * @param useFilters
     * @param filtersCB 
     */
    private void addAdditionalVisibleListener( final JCheckBox enabled, final JCheckBox useFilters, final JComboBox filtersCB ) {
        ActionListener al= new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doUpdateAdditionalVisible( enabled, useFilters, filtersCB );
            }
        };
        if ( enabled!=null ) enabled.addActionListener(al);
        doShowAdditionalFiltersCB.addActionListener(al);
        doUpdateAdditionalVisible( enabled, useFilters, filtersCB );
    }
    
    /**
     * show/hide the additional operations GUI component
     * @param enabled is the component enabled, such as Plot Against (X)"
     * @param useFilters additional operations
     * @param filtersCB the filter.
     */
    private void doUpdateAdditionalVisible( final JCheckBox enabled, final JCheckBox useFilters, final JComboBox filtersCB ) {
        boolean v= ( enabled==null || enabled.isSelected() ) && doShowAdditionalFiltersCB.isSelected();
        useFilters.setVisible( v );
        filtersCB.setVisible( v );
    }
    
    private void doBookmarks( DataSetSelector sel ) {
        BookmarksManager man= new BookmarksManager( (Frame)SwingUtilities.getWindowAncestor(this), true, "Bookmarks" );
        man.setHidePlotButtons(true);
        man.setPrefNode( "bookmarks", "autoplot.default.bookmarks",  "https://autoplot.org/data/bookmarks.xml" );
        //man.setPrefNode("tca","autoplot.default.tca.bookmarks", "https://autoplot.org/data/tca.demos.xml");
        //man.setPrefNode("tca");
        man.setVisible(true);
        Bookmark book= man.getSelectedBookmark();
        if ( book!=null && book instanceof Bookmark.Item ) {
            sel.setValue( ((Bookmark.Item)book).getUri() );
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
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        primaryDataSetSelector = new org.autoplot.datasource.DataSetSelector();
        secondaryCheckBox = new javax.swing.JCheckBox();
        secondaryDataSetSelector = new org.autoplot.datasource.DataSetSelector();
        tertiaryCheckBox = new javax.swing.JCheckBox();
        tertiaryDataSetSelector = new org.autoplot.datasource.DataSetSelector();
        overplotButton = new javax.swing.JButton();
        plotBelowButton = new javax.swing.JButton();
        plotButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        primaryFiltersCB = new javax.swing.JCheckBox();
        primaryFiltersComboBox = new javax.swing.JComboBox<>();
        secondaryFiltersComboBox = new javax.swing.JComboBox<>();
        secondaryFiltersCB = new javax.swing.JCheckBox();
        tertiaryFiltersComboBox = new javax.swing.JComboBox<>();
        tertiaryFiltersCB = new javax.swing.JCheckBox();
        doShowAdditionalFiltersCB = new javax.swing.JCheckBox();
        messagesLabel = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setName("addPlotElementDialog"); // NOI18N

        secondaryCheckBox.setText("Plot Against (X):");
        secondaryCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                secondaryCheckBoxActionPerformed(evt);
            }
        });

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, secondaryCheckBox, org.jdesktop.beansbinding.ELProperty.create("${selected}"), secondaryDataSetSelector, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        tertiaryCheckBox.setText("And Against (Y):");

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, secondaryCheckBox, org.jdesktop.beansbinding.ELProperty.create("${selected}"), tertiaryCheckBox, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        tertiaryCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tertiaryCheckBoxActionPerformed(evt);
            }
        });

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, tertiaryCheckBox, org.jdesktop.beansbinding.ELProperty.create("${selected}"), tertiaryDataSetSelector, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        overplotButton.setText("Overplot");
        overplotButton.setToolTipText("Add this to the current plot as an overplot");
        overplotButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overplotButtonActionPerformed(evt);
            }
        });

        plotBelowButton.setText("Plot Below");
        plotBelowButton.setToolTipText("Plot below the current plot, possibly inserting a plot.  Holding shift will plot above.");
        plotBelowButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                plotBelowButtonActionPerformed(evt);
            }
        });

        plotButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/datasource/go.png"))); // NOI18N
        plotButton.setText("Plot");
        plotButton.setToolTipText("Replace the current plot with this");
        plotButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                plotButtonActionPerformed(evt);
            }
        });

        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        jLabel1.setText("Plot the Data Set URI:");

        primaryFiltersCB.setText("Additional Operations: ");

        primaryFiltersComboBox.setEditable(true);
        primaryFiltersComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { " " }));

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, primaryFiltersCB, org.jdesktop.beansbinding.ELProperty.create("${selected}"), primaryFiltersComboBox, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        secondaryFiltersComboBox.setEditable(true);
        secondaryFiltersComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { " " }));

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, secondaryFiltersCB, org.jdesktop.beansbinding.ELProperty.create("${selected}"), secondaryFiltersComboBox, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        secondaryFiltersCB.setText("Additional Operations: ");

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, secondaryCheckBox, org.jdesktop.beansbinding.ELProperty.create("${selected}"), secondaryFiltersCB, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        tertiaryFiltersComboBox.setEditable(true);
        tertiaryFiltersComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { " " }));

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, tertiaryFiltersCB, org.jdesktop.beansbinding.ELProperty.create("${selected}"), tertiaryFiltersComboBox, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        tertiaryFiltersCB.setText("Additional Operations: ");

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, tertiaryCheckBox, org.jdesktop.beansbinding.ELProperty.create("${selected}"), tertiaryFiltersCB, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        doShowAdditionalFiltersCB.setText("Show \"Additional Operations\" fields, where filters can be applied immediately after loading.");
        doShowAdditionalFiltersCB.setName("showAdditionalOperations"); // NOI18N

        messagesLabel.setText("messages");

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(primaryDataSetSelector, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 678, Short.MAX_VALUE)
                    .add(secondaryDataSetSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(jLabel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 503, Short.MAX_VALUE)
                        .add(175, 175, 175))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(0, 0, Short.MAX_VALUE)
                        .add(cancelButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(overplotButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(plotBelowButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(plotButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 96, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(tertiaryDataSetSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .add(layout.createSequentialGroup()
                        .add(12, 12, 12)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createSequentialGroup()
                                .add(secondaryFiltersCB)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(secondaryFiltersComboBox, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .add(layout.createSequentialGroup()
                                .add(primaryFiltersCB)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(primaryFiltersComboBox, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .add(layout.createSequentialGroup()
                                .add(tertiaryFiltersCB)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(tertiaryFiltersComboBox, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(secondaryCheckBox)
                            .add(tertiaryCheckBox)
                            .add(doShowAdditionalFiltersCB))
                        .add(0, 0, Short.MAX_VALUE))
                    .add(messagesLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(messagesLabel)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(primaryDataSetSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(1, 1, 1)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(primaryFiltersCB)
                    .add(primaryFiltersComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(secondaryCheckBox)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(secondaryDataSetSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(secondaryFiltersCB)
                    .add(secondaryFiltersComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(tertiaryCheckBox)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(tertiaryDataSetSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(tertiaryFiltersCB)
                    .add(tertiaryFiltersComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(18, 18, 18)
                .add(doShowAdditionalFiltersCB)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(plotButton)
                    .add(plotBelowButton)
                    .add(overplotButton)
                    .add(cancelButton))
                .addContainerGap())
        );

        bindingGroup.bind();

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void secondaryCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_secondaryCheckBoxActionPerformed
        if ( secondaryCheckBox.isSelected() ) {
            secondaryDataSetSelector.setValue(primaryDataSetSelector.getValue());
        }
        secondaryDataSetSelector.setVisible(secondaryCheckBox.isSelected());
        tertiaryCheckBox.setVisible(secondaryCheckBox.isSelected());
        tertiaryDataSetSelector.setVisible(secondaryCheckBox.isSelected()&&tertiaryCheckBox.isSelected());
}//GEN-LAST:event_secondaryCheckBoxActionPerformed

    private void overplotButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overplotButtonActionPerformed
        setModifiers( KeyEvent.SHIFT_MASK );
        cancelled= false;
        setVisible(false);
}//GEN-LAST:event_overplotButtonActionPerformed

    private void plotBelowButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_plotBelowButtonActionPerformed
        cancelled= false;
        setModifiers( KeyEvent.CTRL_MASK | ( evt.getModifiers() & ActionEvent.SHIFT_MASK ) );
        setVisible(false);
}//GEN-LAST:event_plotBelowButtonActionPerformed

    private void plotButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_plotButtonActionPerformed
        cancelled= false;
        setModifiers( evt.getModifiers() );
        setVisible(false);
}//GEN-LAST:event_plotButtonActionPerformed

    private void tertiaryCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tertiaryCheckBoxActionPerformed
        if ( tertiaryCheckBox.isSelected() ) {
            tertiaryDataSetSelector.setValue(primaryDataSetSelector.getValue());
        }
        tertiaryDataSetSelector.setVisible(tertiaryCheckBox.isSelected());
    }//GEN-LAST:event_tertiaryCheckBoxActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        cancelled= true;
        setVisible(false);
}//GEN-LAST:event_cancelButtonActionPerformed

    protected int modifiers = 0;
    public static final String PROP_MODIFIERS = "modifiers";

    public int getModifiers() {
        return modifiers;
    }

    public void setModifiers(int modifiers) {
        int oldModifiers = this.modifiers;
        this.modifiers = modifiers;
        firePropertyChange(PROP_MODIFIERS, oldModifiers, modifiers);
    }

    /**
     * return the number of depend datasets added, 0, or -1 if no dataset
     * should be plotted.
     * @return
     */
    public int getDepCount() {
        if ( tertiaryCheckBox.isSelected() ) {
            return 2;
        } else if (  secondaryCheckBox.isSelected() ) {
            return 1;
        } else {
            return 0;
        } 
    }

    public void setDepCount(int i) {
        //primaryCheckBox.setSelected( i>-1 );
        primaryDataSetSelector.setVisible(i>-1);
        secondaryCheckBox.setVisible(i>-1);
        secondaryCheckBox.setSelected( i>0 );
        secondaryDataSetSelector.setVisible(i>0);
        tertiaryCheckBox.setVisible(i>0);
        tertiaryCheckBox.setSelected( i>1 );
        tertiaryDataSetSelector.setVisible(i>1);
    }

    /**
     * if true, then show the additional operations fields.
     * @param show if true, then show the additional operations fields.
     */
    public void setShowAdditionalOperations( boolean show ) {
        this.doShowAdditionalFiltersCB.setSelected(show);
        doUpdateAdditionalVisible( null, primaryFiltersCB, primaryFiltersComboBox );
        doUpdateAdditionalVisible( secondaryCheckBox, secondaryFiltersCB, secondaryFiltersComboBox );
        doUpdateAdditionalVisible( tertiaryCheckBox, tertiaryFiltersCB, tertiaryFiltersComboBox );
    }

    public void setUsePrimaryFilters( boolean show ) {
        this.primaryFiltersCB.setSelected(show);
    }
    
    public void setPrimaryFilter( String f ) {
        this.primaryFiltersComboBox.setSelectedItem(f);
    }

    public void setUseSecondaryFilters( boolean show ) {
        this.secondaryFiltersCB.setSelected(show);
    }
    
    public void setSecondaryFilter( String f ) {
        this.secondaryFiltersComboBox.setSelectedItem(f);
    }

    protected boolean cancelled = true;
    public static final String PROP_CANCELLED = "cancelled";

    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * @return the primaryDataSetSelector
     */
    public org.autoplot.datasource.DataSetSelector getPrimaryDataSetSelector() {
        return primaryDataSetSelector;
    }

    /**
     * @return the secondaryDataSetSelector
     */
    public org.autoplot.datasource.DataSetSelector getSecondaryDataSetSelector() {
        return secondaryDataSetSelector;
    }

    /**
     * @return the tertiaryDataSetSelector
     */
    public org.autoplot.datasource.DataSetSelector getTertiaryDataSetSelector() {
        return tertiaryDataSetSelector;
    }

    public String getPrimaryFilters() {
        return (String)primaryFiltersComboBox.getSelectedItem();
    }
        
    public String getSecondaryFilters() {
        return (String)secondaryFiltersComboBox.getSelectedItem();
    }

    public String getTertiaryFilters() {
        return (String)tertiaryFiltersComboBox.getSelectedItem();
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JCheckBox doShowAdditionalFiltersCB;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel messagesLabel;
    private javax.swing.JButton overplotButton;
    private javax.swing.JButton plotBelowButton;
    private javax.swing.JButton plotButton;
    private org.autoplot.datasource.DataSetSelector primaryDataSetSelector;
    private javax.swing.JCheckBox primaryFiltersCB;
    private javax.swing.JComboBox<String> primaryFiltersComboBox;
    private javax.swing.JCheckBox secondaryCheckBox;
    private org.autoplot.datasource.DataSetSelector secondaryDataSetSelector;
    private javax.swing.JCheckBox secondaryFiltersCB;
    private javax.swing.JComboBox<String> secondaryFiltersComboBox;
    private javax.swing.JCheckBox tertiaryCheckBox;
    private org.autoplot.datasource.DataSetSelector tertiaryDataSetSelector;
    private javax.swing.JCheckBox tertiaryFiltersCB;
    private javax.swing.JComboBox<String> tertiaryFiltersComboBox;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables


    /**
     * set the filter (process string) 
     * @param i
     * @param filters the process string, like "|slice1(1)"
     */
    void setFilter(int i, String filters) {
        if ( filters.trim().length()==0 ) return;
        this.doShowAdditionalFiltersCB.setSelected(true);
        switch (i) {
            case 0:
                primaryFiltersComboBox.setSelectedItem(filters);
                primaryFiltersCB.setSelected(true);
                doUpdateAdditionalVisible( null, primaryFiltersCB, primaryFiltersComboBox );
                break;
            case 1:
                secondaryFiltersComboBox.setSelectedItem(filters);
                secondaryFiltersCB.setSelected(true);
                doUpdateAdditionalVisible( secondaryCheckBox, secondaryFiltersCB, secondaryFiltersComboBox );
                break;
            case 2:
                tertiaryFiltersComboBox.setSelectedItem(filters);
                tertiaryFiltersCB.setSelected(true);
                doUpdateAdditionalVisible( tertiaryCheckBox, tertiaryFiltersCB, tertiaryFiltersComboBox );
                break;
            default:
                break;
        }
    }

}
