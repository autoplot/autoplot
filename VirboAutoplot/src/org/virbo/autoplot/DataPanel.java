
/*
 * DataPanel.java
 *
 * Created on Aug 28, 2009, 9:06:25 AM
 */

package org.virbo.autoplot;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.autoplot.help.AutoplotHelpSystem;
import org.das2.util.LoggerManager;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.BindingSupport;
import org.virbo.autoplot.dom.DataSourceController;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.autoplot.dom.PlotElement;
import org.virbo.autoplot.dom.PlotElementController;
import org.virbo.autoplot.util.TickleTimer;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.InputVerifier;
import org.virbo.filters.FiltersChainPanel;

/**
 * GUI tab for controlling how data is handled, provides feedback for what is
 * being plotted, allows fill value to be specified, and provides the filters
 * chain of operations to apply to the data before plotting.
 * @author jbf
 */
public class DataPanel extends javax.swing.JPanel {

    Application dom;
    AutoplotUI app;
    ApplicationController applicationController;
    DataSourceFilter dsf; // current focus
    BindingGroup dataSourceFilterBindingGroup;
    
    /**
     * To suppress extraneous GUI events when binding to combo boxes,
     * this is true while we are binding.
     */
    boolean bindingTransitionalState= false; 
    
    PlotElement element;// current focus

    private final transient PropertyChangeListener compListener; // listen to component property changes
    
    private final static Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.gui");

    public DataPanel( AutoplotUI app) {
        initComponents();
        plotElementFiltersPanel.setName("postOperationsPanel");
        dataSourceFiltersPanel.setName("operationsPanel");
        this.app= app;
        
        this.dom = app.getDocumentModel();
        this.applicationController= this.dom.getController();
        this.applicationController.addPropertyChangeListener( ApplicationController.PROP_PLOT_ELEMENT, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Runnable run= new Runnable() {
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
        
        compListener= new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                componentChanged();
            }
        };
        
        AutoplotHelpSystem.getHelpSystem().registerHelpID(this.jPanel1, "dataPanel_1");
        AutoplotHelpSystem.getHelpSystem().registerHelpID(this.jPanel2, "dataPanel_2");

        plotElementFiltersPanel.setFilter("");
        
        doBindings();

    }
    
    private void componentTextFieldFocusGained(java.awt.event.FocusEvent evt) {
        LoggerManager.logGuiEvent(evt);
        setAdjusting(true);
    }

    private void componentTextFieldFocusLost(java.awt.event.FocusEvent evt) {
        LoggerManager.logGuiEvent(evt);
        setAdjusting(false);
        componentChanged();
    }

    protected void setExpertMode( boolean expert ) {
        plotElementFiltersPanel.setExpertMode(expert);
        dataSourceFiltersPanel.setExpertMode(expert);
    }
    
    TickleTimer operationsHistoryTimer= new TickleTimer( 500, new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            //operatorsComboBox.addToRecent(filtersChainPanel.getFilter());
        }
    } );

    /**
     * encourage making local copies for thread safety.
     * @return the current plotElement.
     */
    private synchronized PlotElement getElement() {
        return element;
    }
        

    /**
     * bind to the data source and plot element.
     */
    protected final void doBindings() {
        logger.fine("doBindings");
        doPlotElementBindings();
        doDataSourceFilterBindings();
        componentChanged(); // force update
    }
    
    private void componentChanged() {
        if ( adjusting ) return;
        PlotElement lelement= getElement();
        if ( lelement==null ) {
            return;
        }
        this.operationsHistoryTimer.tickle();
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

    private BindingGroup elementBindingGroup;

    transient PropertyChangeListener dsfListener= new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Runnable run = new Runnable() {
                    @Override
                    public void run() {
                        DataPanel.this.plotElementFiltersPanel.setFilter("");
                        DataPanel.this.plotElementFiltersPanel.setDataSet(null);
                    }
                };
                if ( SwingUtilities.isEventDispatchThread() ) {
                    run.run();
                } else {
                    SwingUtilities.invokeLater(run);
                }
            }
        };

    transient MouseWheelListener sliceIndexListener=null;
    transient MouseWheelListener sliceIndexListener2=null;
    
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
    }

    private synchronized void doPlotElementBindings() {
        BindingGroup bc = new BindingGroup();
        if (elementBindingGroup != null) elementBindingGroup.unbind();
        setAdjusting( true ); // suppress events
        if ( element!=null ) {
            element.removePropertyChangeListener( PlotElement.PROP_COMPONENT, compListener );
            element.getController().removePropertyChangeListener( PlotElementController.PROP_DATASET, contextListener );
        }

        PlotElement p = applicationController.getPlotElement();
        element= p;

        plotElementFiltersPanel.setFilter(p.getComponent());

        Runnable run= new Runnable() {
            @Override
            public void run() {
                plotElementFiltersPanel.setFilter(element.getComponent()); // because adjusting==true.
            }
        };
        if ( SwingUtilities.isEventDispatchThread() ) {
            run.run();
        } else {
            SwingUtilities.invokeLater(run);
        }
        
        element.addPropertyChangeListener( PlotElement.PROP_COMPONENT, compListener );
        bc.addBinding( Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, element, BeanProperty.create("component"), this.plotElementFiltersPanel, BeanProperty.create( OperationsPanel.PROP_FILTER ) ) );
        bc.addBinding( Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, element.getController(), BeanProperty.create("sliceAutoranges"), this.sliceAutorangesCB, BeanProperty.create("selected") ) );

        elementBindingGroup = bc;
        bc.bind();
        setAdjusting( false );
        
        p.getController().addPropertyChangeListener( PlotElementController.PROP_DATASET, contextListener );
        updateProcessDataSetLabel();

    }

    private synchronized void doDataSourceFilterBindings() {

        if (dataSourceFilterBindingGroup != null) dataSourceFilterBindingGroup.unbind();

        if ( dsf!=null ) {
            dsf.getController().removePropertyChangeListener(DataSourceController.PROP_DATASOURCE,dsfListener);
            dsf.getController().removePropertyChangeListener(DataSourceController.PROP_FILLDATASET, fillDataSetListener );
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
        newDsf.getController().addPropertyChangeListener( DataSourceController.PROP_DATASOURCE, dsfListener );
        
        dataSourceFiltersPanel.setFilter( newDsf.getFilters() );
        dataSourceFiltersPanel.setDataSet( newDsf.getController().getDataSet() );
        
        plotElementFiltersPanel.setDataSet(ds);

        bindingTransitionalState= true;
        BindingGroup bc = new BindingGroup();
        bc.addBinding(Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, newDsf, BeanProperty.create("filters"), this.dataSourceFiltersPanel, BeanProperty.create("filter")) );
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, newDsf, BeanProperty.create("uri"), this.dataSetSelector, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, newDsf, BeanProperty.create("controller.dataSet"), this.dataSourceFiltersPanel, BeanProperty.create("dataSet")));
        try {
            bc.bind();
        } catch ( RuntimeException e ) {
            throw e;
        }
        dataSourceFilterBindingGroup = bc;
        bindingTransitionalState= false;
                
        dsfListener= new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                
            }
        };

        dsf= newDsf;
        newDsf.getController().addPropertyChangeListener(dsfListener);

    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel2 = new javax.swing.JPanel();
        sliceAutorangesCB = new javax.swing.JCheckBox();
        jLabel1 = new javax.swing.JLabel();
        processDataSetLabel = new javax.swing.JLabel();
        plotElementFiltersPanel = new org.virbo.autoplot.OperationsPanel();
        jPanel1 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        dataSetSelector = new org.virbo.datasource.DataSetSelector();
        dataSourceFiltersPanel = new org.virbo.autoplot.OperationsPanel();

        setName("dataPanel"); // NOI18N

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
                    .add(jLabel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 590, Short.MAX_VALUE)
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
                .add(plotElementFiltersPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 263, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(sliceAutorangesCB)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(processDataSetLabel)
                .add(16, 16, 16))
        );

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Data Source [?]"));
        jPanel1.setName("dataSourcePanel"); // NOI18N

        jLabel2.setFont(jLabel2.getFont().deriveFont(jLabel2.getFont().getSize()-4f));
        jLabel2.setText("Apply these operations to the data after loading.  Fill and valid range can be specified with putProperty filter.");

        dataSetSelector.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSetSelectorActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(dataSetSelector, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jLabel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(dataSourceFiltersPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(dataSetSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jLabel2)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(dataSourceFiltersPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 109, Short.MAX_VALUE)
                .addContainerGap())
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(jPanel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel1.getAccessibleContext().setAccessibleName("Data Source and Initial Processing [?]");
    }// </editor-fold>//GEN-END:initComponents
    
    private void dataSetSelectorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSetSelectorActionPerformed
        String uri= dataSetSelector.getValue();
        int modifiers= evt.getModifiers();
        app.doPlotGoButton( uri, modifiers );
    }//GEN-LAST:event_dataSetSelectorActionPerformed

    /**
     * for testing, provide access.
     * @return the FiltersChainPanel
     */
    public FiltersChainPanel getFiltersChainPanel() {
        return plotElementFiltersPanel.getFiltersChainPanel();
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.virbo.datasource.DataSetSelector dataSetSelector;
    private org.virbo.autoplot.OperationsPanel dataSourceFiltersPanel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private org.virbo.autoplot.OperationsPanel plotElementFiltersPanel;
    private javax.swing.JLabel processDataSetLabel;
    private javax.swing.JCheckBox sliceAutorangesCB;
    // End of variables declaration//GEN-END:variables

}
