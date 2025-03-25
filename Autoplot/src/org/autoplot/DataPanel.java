
/*
 * DataPanel.java
 *
 * Created on Aug 28, 2009, 9:06:25 AM
 */

package org.autoplot;

import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.autoplot.help.AutoplotHelpSystem;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.autoplot.dom.Application;
import org.autoplot.dom.ApplicationController;
import org.autoplot.dom.DataSourceController;
import org.autoplot.dom.DataSourceFilter;
import org.autoplot.dom.PlotElement;
import org.autoplot.dom.PlotElementController;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;

/**
 * GUI tab for controlling how data is handled, provides feedback for what is
 * being plotted, allows fill value to be specified, and provides the filters
 * chain of operations to apply to the data before plotting.
 * @author jbf
 */
public class DataPanel extends javax.swing.JPanel {

    private final Application dom;
    private final AutoplotUI app;
    private final ApplicationController applicationController;
    private DataSourceFilter dsf; // current focus
    
    private BindingGroup dataSourceFilterBindingGroup;
    private boolean dataSourceFilterBindingGroupIsBound= false;

    private BindingGroup plotElementBindingGroup;
    private boolean plotElementBindingGroupIsBound= false;
    
    private PlotElement plotElement;// current focus
    
    private final static Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.gui.data");

    public DataPanel( AutoplotUI app) {
        initComponents();
        plotElementFiltersPanel.setName("postOperationsPanel");
        dataSourceFiltersPanel.setName("operationsPanel");
        this.app= app;
        
        this.dom = app.getDom();
        this.applicationController= this.dom.getController();
        this.applicationController.addPropertyChangeListener( ApplicationController.PROP_PLOT_ELEMENT, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Runnable run= new Runnable() {
                    @Override
                    public void run() {
                        doPlotElementBindings();
                    }
                };
                if ( SwingUtilities.isEventDispatchThread() ) {
                    run.run();
                } else {
                    SwingUtilities.invokeLater(run);
                }
            }
        });
        this.applicationController.addPropertyChangeListener(ApplicationController.PROP_DATASOURCEFILTER, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Runnable run= new Runnable() {
                    @Override
                    public void run() {
                        doDataSourceFilterBindings();
                    }
                };
                if ( SwingUtilities.isEventDispatchThread() ) {
                    run.run();
                } else {
                    SwingUtilities.invokeLater(run);
                }
            }
        });
                
        AutoplotHelpSystem.getHelpSystem().registerHelpID(this.jPanel1, "dataPanel");

        plotElementFiltersPanel.setFilter("");
        
        doBindings();
        
        dataSetSelector.setPlayButton(false);
        
        dataSourceFiltersPanel.setVisible(false);
        jLabel2.setVisible(false);
    }

    protected void setExpertMode( boolean expert ) {
        plotElementFiltersPanel.setExpertMode(expert);
        dataSourceFiltersPanel.setExpertMode(expert);
        dataSetSelector.setEnabled(expert);
        dataSourceFiltersPanel.setEnabled(expert);
        additionalOperationsCheckBox.setEnabled(expert);
        doSuppressReset.setEnabled(expert);
    }

    /**
     * encourage making local copies for thread safety.
     * @return the current plotElement.
     */
    private PlotElement getElement() {
        return plotElement;
    }

    /**
     * to avoid use of synchronized blocks, methods must be called from the
     * event thread.  This verifies that the thread is the event thread.
     * @param caller 
     */
    private static void assertEventThread( String caller ) {
        if ( !SwingUtilities.isEventDispatchThread() ) {
            throw new IllegalArgumentException( caller + " must be called from the event thread.");
        }
    }

    /**
     * bind to the data source and plot plotElement.
     * This must be called on the event thread.
     */
    protected final void doBindings() {
        logger.fine("doBindings");
        doPlotElementBindings();
        doDataSourceFilterBindings();
    }

    @Override
    public void paint(Graphics g) {
        if ( !dataSourceFilterBindingGroupIsBound ) {
            dataSourceFilterBindingGroup.bind();
            dataSourceFilterBindingGroupIsBound= true;
        }
        if ( !plotElementBindingGroupIsBound ) {
            plotElementBindingGroup.bind();
            plotElementBindingGroupIsBound= true;
        }
        super.paint(g); //To change body of generated methods, choose Tools | Templates.
    }
    
    protected boolean adjusting = false;
    /**
     * true indicates the component is in transition.
     */
    public static final String PROP_ADJUSTING = "adjusting";

    public boolean isAdjusting() {
        return adjusting;
    }

    public void setAdjusting(boolean adjusting) {
        boolean oldAdjusting = this.adjusting;
        this.adjusting = adjusting;
        firePropertyChange(PROP_ADJUSTING, oldAdjusting, adjusting);
    }
    
    /**
     * show the context after the slicing and operations for the user's reference.
     * TODO: make sure this is released so stray datasets can be garbage collected.
     */
    private final transient PropertyChangeListener contextListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            updateProcessDataSetLabel();
        }
    };

    /**
     * if there are filters, then make sure the dialog is shown.
     */
    private final transient PropertyChangeListener filtersListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if ( dsf.getFilters().trim().length()>0 ) {
                DataPanel.this.additionalOperationsCheckBox.setSelected(true);
            }
        }
    };
    
    private final transient PropertyChangeListener fillDataSetListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            final QDataSet ds= (QDataSet)evt.getNewValue();
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    plotElementFiltersPanel.setDataSet(ds);
                }
            };
            if ( SwingUtilities.isEventDispatchThread() ) {
                run.run();
            } else {
                SwingUtilities.invokeLater(run);
            }
            
        }
    };
    
    private void updateProcessDataSetLabel() {
        PlotElement lelement= getElement();
        if ( lelement!=null ) {
            DataSourceFilter dsf1= dom.getController().getDataSourceFilterFor(lelement);
            QDataSet orig= dsf1==null ? null : dsf1.getController().getFillDataSet();
            QDataSet proc= lelement.getController().getDataSet();
            if ( orig==proc || proc==null ) {
                processDataSetLabel.setText( "" );
            } else {
                String lbl= String.valueOf( proc );
                QDataSet ds= lelement.getController().getDataSet();
                String s=  DataSetUtil.contextAsString( ds ).trim();
                processDataSetLabel.setText( "<html>These operations result in the dataset<br>"+lbl + ( s.length()==0 ? "" : ( "<br>@ "+ s ) ) );
            }
        } else {
            processDataSetLabel.setText( "" );
        }
    }

    private void doPlotElementBindings() {
        assertEventThread("doPlotElementBindings");

        BindingGroup bc = new BindingGroup();
        if (plotElementBindingGroup != null) plotElementBindingGroup.unbind();
        setAdjusting( true ); // suppress events
        if ( plotElement!=null ) {
            plotElement.getController().removePropertyChangeListener( PlotElementController.PROP_DATASET, contextListener );
        }

        PlotElement p = applicationController.getPlotElement();
        plotElement= p;

        if ( p!=null ) {
            plotElementFiltersPanel.setFilter(p.getComponent());
        } else {
            plotElementFiltersPanel.setFilter("");
        }

        Runnable run= new Runnable() {
            @Override
            public void run() {
                if ( plotElement!=null ) {
                    plotElementFiltersPanel.setFilter(plotElement.getComponent()); // because adjusting==true.
                } else {
                    plotElementFiltersPanel.setFilter(""); // because adjusting==true.
                }
            }
        };
        if ( SwingUtilities.isEventDispatchThread() ) {
            run.run();
        } else {
            SwingUtilities.invokeLater(run);
        }
        
        if ( plotElement!=null ) {
            bc.addBinding(Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, plotElement, BeanProperty.create("component"), this.plotElementFiltersPanel, BeanProperty.create( OperationsPanel.PROP_FILTER ) ) );
            bc.addBinding(Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, plotElement.getController(), BeanProperty.create("sliceAutoranges"), this.sliceAutorangesCB, BeanProperty.create("selected") ) );
        }

        plotElementBindingGroup = bc;
        plotElementBindingGroupIsBound= false;
        
        setAdjusting( false );
        
        if ( p!=null ) {
            p.getController().addPropertyChangeListener( PlotElementController.PROP_DATASET, contextListener );
        }
            
        updateProcessDataSetLabel();
        
        repaint();

    }

    /**
     * this should be called on the event thread.
     */
    private void doDataSourceFilterBindings() {
        
        assertEventThread("doDataSourceFilterBindings");
        
        if (dataSourceFilterBindingGroup != null) dataSourceFilterBindingGroup.unbind();

        if ( dsf!=null ) {
            dsf.getController().removePropertyChangeListener(DataSourceController.PROP_FILLDATASET, fillDataSetListener );
            dsf.removePropertyChangeListener( DataSourceFilter.PROP_FILTERS, filtersListener );
        }
        
        final DataSourceFilter newDsf = applicationController.getDataSourceFilter();

        if (newDsf == null) {
            dataSourceFilterBindingGroup = null;
            //dataSetLabel.setText( "(no dataset)" );
            return;
        }

        final QDataSet ds= newDsf.getController().getFillDataSet();
        //dataSetLabel.setText( ds==null ? "(no dataset)" : ds.toString() );
        
        newDsf.getController().addPropertyChangeListener( DataSourceController.PROP_FILLDATASET, fillDataSetListener );
        newDsf.addPropertyChangeListener( DataSourceFilter.PROP_FILTERS, filtersListener );
        additionalOperationsCheckBox.setSelected( newDsf.getFilters().trim().length()>0 );
        
        dataSourceFiltersPanel.setFilter( newDsf.getFilters() );
        dataSourceFiltersPanel.setDataSet( newDsf.getController().getDataSet() );
        
        plotElementFiltersPanel.setDataSet(ds);

        BindingGroup bc = new BindingGroup();
        bc.addBinding(Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, newDsf, BeanProperty.create("filters"), this.dataSourceFiltersPanel, BeanProperty.create("filter")) );
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, newDsf, BeanProperty.create("uri"), this.dataSetSelector, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, newDsf, BeanProperty.create("controller.dataSet"), this.dataSourceFiltersPanel, BeanProperty.create("dataSet")));
        
        dataSourceFilterBindingGroup = bc;
        dataSourceFilterBindingGroupIsBound= false;

        dsf= newDsf;

        repaint();
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

        jSplitPane1 = new javax.swing.JSplitPane();
        jPanel1 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        dataSetSelector = new org.autoplot.datasource.DataSetSelector();
        dataSourceFiltersPanel = new org.autoplot.OperationsPanel();
        additionalOperationsCheckBox = new javax.swing.JCheckBox();
        doSuppressReset = new javax.swing.JCheckBox();
        jPanel2 = new javax.swing.JPanel();
        sliceAutorangesCB = new javax.swing.JCheckBox();
        jLabel1 = new javax.swing.JLabel();
        processDataSetLabel = new javax.swing.JLabel();
        plotElementFiltersPanel = new org.autoplot.OperationsPanel();

        setName("dataPanel"); // NOI18N

        jSplitPane1.setDividerLocation(110);
        jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Data Source [?]"));
        jPanel1.setName("dataSourcePanel"); // NOI18N

        jLabel2.setFont(jLabel2.getFont().deriveFont(jLabel2.getFont().getSize()-4f));
        jLabel2.setText("Apply these operations to the data after loading.  Fill and valid range can be specified with putProperty filter.");

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, additionalOperationsCheckBox, org.jdesktop.beansbinding.ELProperty.create("${selected}"), jLabel2, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        dataSetSelector.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSetSelectorActionPerformed(evt);
            }
        });

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, additionalOperationsCheckBox, org.jdesktop.beansbinding.ELProperty.create("${selected}"), dataSourceFiltersPanel, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        additionalOperationsCheckBox.setSelected(false);
        additionalOperationsCheckBox.setText("Apply additional operations immediately after data is loaded");

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, dataSourceFiltersPanel, org.jdesktop.beansbinding.ELProperty.create("${visible}"), additionalOperationsCheckBox, org.jdesktop.beansbinding.BeanProperty.create("selected"));
        bindingGroup.addBinding(binding);

        additionalOperationsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                additionalOperationsCheckBoxActionPerformed(evt);
            }
        });

        doSuppressReset.setSelected(true);
        doSuppressReset.setText("Suppress Reset");
        doSuppressReset.setToolTipText("Normally the play button would reset the axis ranges and plot style, but this suppresses this behavior.");

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(dataSetSelector, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 578, Short.MAX_VALUE)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(additionalOperationsCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .add(doSuppressReset))
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(12, 12, 12)
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jLabel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(dataSourceFiltersPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(dataSetSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(additionalOperationsCheckBox)
                    .add(doSuppressReset))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jLabel2)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(dataSourceFiltersPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                .addContainerGap())
        );

        jSplitPane1.setTopComponent(jPanel1);
        jPanel1.getAccessibleContext().setAccessibleName("Data Source and Initial Processing [?]");

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Data Post Processing [?]\n"));
        jPanel2.setName("dataPostProcessingPanel"); // NOI18N

        sliceAutorangesCB.setText("Autorange after operations");
        sliceAutorangesCB.setToolTipText("Changing the slice index will re-autorange the data");
        sliceAutorangesCB.setName("sliceAutorangesCB"); // NOI18N

        jLabel1.setFont(jLabel1.getFont().deriveFont(jLabel1.getFont().getSize()-4f));
        jLabel1.setText("Apply additional operations to the dataset before plotting");
        jLabel1.setName("DataPostProcessingInstructionsLabel"); // NOI18N

        processDataSetLabel.setFont(processDataSetLabel.getFont().deriveFont(processDataSetLabel.getFont().getSize()-4f));
        processDataSetLabel.setText("(filtered dataset will go here)");

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jLabel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(sliceAutorangesCB, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(plotElementFiltersPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, processDataSetLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addContainerGap())))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(plotElementFiltersPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 197, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(sliceAutorangesCB)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(processDataSetLabel)
                .add(16, 16, 16))
        );

        jSplitPane1.setRightComponent(jPanel2);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(jSplitPane1)
                .add(0, 0, 0))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jSplitPane1)
        );

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents
    
    private void dataSetSelectorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSetSelectorActionPerformed
        final String uri= dataSetSelector.getValue();
        int modifiers= evt.getModifiers();
        boolean suppressReset= doSuppressReset.isSelected();
        if ( suppressReset ) {
            Runnable run= new Runnable() {
                public void run() {
                    app.dom.getController().plotUri( uri, false );
                }
            };
            new Thread( run, "plotUri" ).start();
        } else {
            app.doPlotGoButton( uri, modifiers );
        }
    }//GEN-LAST:event_dataSetSelectorActionPerformed

    private void additionalOperationsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_additionalOperationsCheckBoxActionPerformed
        dataSourceFiltersPanel.setVisible( additionalOperationsCheckBox.isSelected() );
        jLabel2.setVisible( additionalOperationsCheckBox.isSelected() );
        if ( !additionalOperationsCheckBox.isSelected() ) {
            dsf.setFilters("");
        }
    }//GEN-LAST:event_additionalOperationsCheckBoxActionPerformed

//    /**
//     * for testing, provide access.
//     * @return the FiltersChainPanel
//     */
//    public FiltersChainPanel getFiltersChainPanel() {
//        return plotElementFiltersPanel.getFiltersChainPanel();
//    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox additionalOperationsCheckBox;
    private org.autoplot.datasource.DataSetSelector dataSetSelector;
    private org.autoplot.OperationsPanel dataSourceFiltersPanel;
    private javax.swing.JCheckBox doSuppressReset;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JSplitPane jSplitPane1;
    private org.autoplot.OperationsPanel plotElementFiltersPanel;
    private javax.swing.JLabel processDataSetLabel;
    private javax.swing.JCheckBox sliceAutorangesCB;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables

}
