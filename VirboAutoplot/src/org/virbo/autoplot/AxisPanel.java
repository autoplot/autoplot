/*
 * PlotStylePanel.java
 *
 * Created on July 27, 2007, 9:41 AM
 */
package org.virbo.autoplot;

import org.das2.components.DatumRangeEditor;
import java.awt.BorderLayout;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.DefaultComboBoxModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import org.das2.datum.DatumRange;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasPlot;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.DataSourceController;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.autoplot.dom.Panel;
import org.virbo.autoplot.dom.Plot;
import org.virbo.autoplot.layout.LayoutUtil;

/**
 *
 * @author  jbf
 */
public class AxisPanel extends javax.swing.JPanel {

    ApplicationModel applicationModel;
    Application dom;
    ApplicationController applicationController;
    DatumRangeEditor xredit;
    DatumRangeEditor yredit;
    DatumRangeEditor zredit;
    PropertyChangeListener dsfListener;
    DataSourceFilter dsf; // current focus
    
    private final static Logger logger = Logger.getLogger("virbo.autoplot");

    /** Creates new form PlotStylePanel */
    public AxisPanel(final ApplicationModel applicationModel) {
        this.applicationModel = applicationModel;
        this.dom = applicationModel.dom;
        this.applicationController= this.dom.getController();

        this.applicationController.addPropertyChangeListener(ApplicationController.PROP_DATASOURCEFILTER, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                doDataSourceFilterBindings();
            }
        });
        
        this.applicationController.addPropertyChangeListener( ApplicationController.PROP_PLOT, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                doPlotBindings();
            }
        });

        this.applicationController.addPropertyChangeListener( ApplicationController.PROP_PANEL, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                doPanelBindings();
            }
        });
        initComponents();

        DasPlot plot = applicationController.getPlot().getController().getDasPlot();
        DasColorBar colorbar = applicationController.getPlot().getController().getDasColorBar();

        xredit = new DatumRangeEditor();
        xredit.setValue(plot.getXAxis().getDatumRange());
        xAxisRangePanel.add(xredit, BorderLayout.CENTER);

        yredit = new DatumRangeEditor();
        yredit.setValue(plot.getYAxis().getDatumRange());
        yAxisRangePanel.add(yredit, BorderLayout.CENTER);

        zredit = new DatumRangeEditor();
        zredit.setValue(colorbar.getDatumRange());
        zAxisRangePanel.add(zredit, BorderLayout.CENTER);

        xredit.addPropertyChangeListener( new PropertyChangeListener() {
           public void propertyChange(PropertyChangeEvent ev ) {
               DatumRange dr= (DatumRange)xredit.getValue();
               xLog.setEnabled( UnitsUtil.isRatioMeasurement(dr.getUnits() ) );
               if ( !xLog.isEnabled() ) xLog.setSelected(false);
           }
        });
        yredit.addPropertyChangeListener( new PropertyChangeListener() {
           public void propertyChange(PropertyChangeEvent ev ) {
               DatumRange dr= (DatumRange)yredit.getValue();
               yLog.setEnabled( UnitsUtil.isRatioMeasurement(dr.getUnits() ) );
               if ( !yLog.isEnabled() ) yLog.setSelected(false);
           }
        });
        zredit.addPropertyChangeListener( new PropertyChangeListener() {
           public void propertyChange(PropertyChangeEvent ev ) {
               DatumRange dr= (DatumRange)zredit.getValue();
               zLog.setEnabled( UnitsUtil.isRatioMeasurement(dr.getUnits() ) );
               if ( !zLog.isEnabled() ) zLog.setSelected(false);
           }
        });
        Runnable run= new Runnable() {
            public void run() {
                doPlotBindings();
                doPanelBindings();
                doApplicationBindings();
                doDataSourceFilterBindings();
            }
        };
        run.run();
        //RequestProcessor.invokeLater(run);


    }

    private void doApplicationBindings() {

        Binding b;
        BindingGroup bc = new BindingGroup();

        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom, BeanProperty.create( "options.autoranging"), this.allowAutoRangingCheckBox, BeanProperty.create( "selected")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom, BeanProperty.create( "options.autolabelling"), this.autolabellingCheckbox, BeanProperty.create( "selected")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom, BeanProperty.create( "options.autolayout"), this.autolayoutCheckbox,BeanProperty.create(  "selected")));
        bc.bind();
    }
    BindingGroup dataSourceFilterBindingGroup;



    private synchronized void doDataSourceFilterBindings() {

        if (dataSourceFilterBindingGroup != null) dataSourceFilterBindingGroup.unbind();

        if ( dsf!=null ) {
            dsf.getController().removePropertyChangeListener(dsfListener);
        }
        
        final DataSourceFilter newDsf = applicationController.getDataSourceFilter();
        
        if (newDsf == null) {
            dataSourceFilterBindingGroup = null;
            return;
        }
        
        dsf= newDsf;

    }

    BindingGroup plotBindingGroup;

    private BindingGroup doPlotBindings() {

        BindingGroup bc = new BindingGroup();
        Binding b;
        Plot p = applicationController.getPlot();

        if (plotBindingGroup != null) plotBindingGroup.unbind();
        
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("xaxis.label"), xTitleTextField, BeanProperty.create("text")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p,BeanProperty.create( "xaxis.range"), xredit, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("xaxis.log"), xLog, BeanProperty.create("selected")));

        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("yaxis.label"), yTitleTextField, BeanProperty.create("text")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("yaxis.range"), yredit,BeanProperty.create( "value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("yaxis.log"), yLog, BeanProperty.create("selected")));

        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("zaxis.label"), zTitleTextField, BeanProperty.create("text")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("zaxis.range"), zredit, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("zaxis.log"), zLog, BeanProperty.create("selected")));

        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("title"), titleTextField, BeanProperty.create("text")));

        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("isotropic"), this.isotropicCheckBox, BeanProperty.create("selected")));

        plotBindingGroup = bc;
        bc.bind();

        return bc;
    }

    BindingGroup panelBindingGroup;
    
    Panel panel;

    private void doPanelBindings() {
        BindingGroup bc = new BindingGroup();
        
        if (panelBindingGroup != null) panelBindingGroup.unbind();

        Panel p = applicationController.getPanel();
        panel= p;
        
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("legendLabel"), legendTextField, BeanProperty.create("text")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, p, BeanProperty.create("displayLegend"), legendEnableCheckbox, BeanProperty.create("selected")));
        
        panelBindingGroup = bc;
        bc.bind();
    }



    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        jScrollPane1 = new javax.swing.JScrollPane();
        jPanel3 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        xAxisPanel = new javax.swing.JPanel();
        xLog = new javax.swing.JCheckBox();
        xAxisRangePanel = new javax.swing.JPanel();
        xTitleTextField = new javax.swing.JTextField();
        zAxisPanel = new javax.swing.JPanel();
        zLog = new javax.swing.JCheckBox();
        zAxisRangePanel = new javax.swing.JPanel();
        zTitleTextField = new javax.swing.JTextField();
        yAxisPanel = new javax.swing.JPanel();
        yAxisRangePanel = new javax.swing.JPanel();
        yLog = new javax.swing.JCheckBox();
        isotropicCheckBox = new javax.swing.JCheckBox();
        yTitleTextField = new javax.swing.JTextField();
        jPanel1 = new javax.swing.JPanel();
        allowAutoRangingCheckBox = new javax.swing.JCheckBox();
        titleTextField = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        autolayoutCheckbox = new javax.swing.JCheckBox();
        autolabellingCheckbox = new javax.swing.JCheckBox();
        legendEnableCheckbox = new javax.swing.JCheckBox();
        legendTextField = new javax.swing.JTextField();

        jScrollPane1.setPreferredSize(new java.awt.Dimension(700, 600));

        jLabel1.setText("Axes");

        xAxisPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("X Axis"));

        xLog.setText("log");
        xLog.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        xAxisRangePanel.setLayout(new java.awt.BorderLayout());

        xTitleTextField.setText("jTextField1");

        org.jdesktop.layout.GroupLayout xAxisPanelLayout = new org.jdesktop.layout.GroupLayout(xAxisPanel);
        xAxisPanel.setLayout(xAxisPanelLayout);
        xAxisPanelLayout.setHorizontalGroup(
            xAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(xAxisPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(xAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, xTitleTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 312, Short.MAX_VALUE)
                    .add(xAxisRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 312, Short.MAX_VALUE)
                    .add(xLog))
                .addContainerGap())
        );
        xAxisPanelLayout.setVerticalGroup(
            xAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(xAxisPanelLayout.createSequentialGroup()
                .add(xTitleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(xAxisRangePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(xLog)
                .addContainerGap(14, Short.MAX_VALUE))
        );

        zAxisPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Colorbar"));

        zLog.setText("log");
        zLog.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        zAxisRangePanel.setLayout(new java.awt.BorderLayout());

        zTitleTextField.setText("jTextField1");

        org.jdesktop.layout.GroupLayout zAxisPanelLayout = new org.jdesktop.layout.GroupLayout(zAxisPanel);
        zAxisPanel.setLayout(zAxisPanelLayout);
        zAxisPanelLayout.setHorizontalGroup(
            zAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(zAxisPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(zAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(zLog)
                    .add(zTitleTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 286, Short.MAX_VALUE)
                    .add(zAxisRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 286, Short.MAX_VALUE))
                .add(130, 130, 130))
        );
        zAxisPanelLayout.setVerticalGroup(
            zAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, zAxisPanelLayout.createSequentialGroup()
                .add(zTitleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 16, Short.MAX_VALUE)
                .add(zAxisRangePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(zLog)
                .addContainerGap())
        );

        yAxisPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Y Axis"));

        yAxisRangePanel.setLayout(new java.awt.BorderLayout());

        yLog.setText("log");
        yLog.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        isotropicCheckBox.setText("Isotropic");
        isotropicCheckBox.setToolTipText("When units are convertable to X Axis units, automatically set y axis to ensure pixel:data ratio is the same.");
        isotropicCheckBox.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        yTitleTextField.setText("jTextField1");

        org.jdesktop.layout.GroupLayout yAxisPanelLayout = new org.jdesktop.layout.GroupLayout(yAxisPanel);
        yAxisPanel.setLayout(yAxisPanelLayout);
        yAxisPanelLayout.setHorizontalGroup(
            yAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(yAxisPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(yAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(yAxisRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 312, Short.MAX_VALUE)
                    .add(yLog)
                    .add(isotropicCheckBox)
                    .add(yTitleTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 312, Short.MAX_VALUE))
                .addContainerGap())
        );
        yAxisPanelLayout.setVerticalGroup(
            yAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(yAxisPanelLayout.createSequentialGroup()
                .add(yTitleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(yAxisRangePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(yLog)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(isotropicCheckBox)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Plot"));

        allowAutoRangingCheckBox.setText("Autoranging");
        allowAutoRangingCheckBox.setToolTipText("allow automatic axis range setting.  Range is based on metadata hints and data range.");

        titleTextField.setText("title will go here");
        titleTextField.setToolTipText("title for the selected plot.\n");

        jLabel6.setText("Title:");

        autolayoutCheckbox.setText("Autolayout");
        autolayoutCheckbox.setToolTipText("<html><p>Allow the application to reposition axes so labels are not clipped and unused space is reduced.  </P><p>Axes can be positioned manually by turning off this option, then hold shift down to enable plot corner drag anchors.</p></html>");
        autolayoutCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autolayoutCheckboxActionPerformed(evt);
            }
        });

        autolabellingCheckbox.setText("Autolabelling");
        autolabellingCheckbox.setToolTipText("allow automatic setting of axis labels based on metadata.\n");

        legendEnableCheckbox.setText("Legend Label:");
        legendEnableCheckbox.setToolTipText("when selected, the label is added to the legend of the plot.\n\n");

        legendTextField.setText("label will go here");
        legendTextField.setToolTipText("a short label indentifying the selected panel");

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, legendEnableCheckbox, org.jdesktop.beansbinding.ELProperty.create("${selected}"), legendTextField, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(allowAutoRangingCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(autolabellingCheckbox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(autolayoutCheckbox))
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel1Layout.createSequentialGroup()
                                .add(jLabel6)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(titleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                            .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel1Layout.createSequentialGroup()
                                .add(legendEnableCheckbox)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(legendTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 274, Short.MAX_VALUE)))
                        .add(23, 23, 23))))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel6)
                    .add(titleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(legendEnableCheckbox)
                    .add(legendTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(18, 18, 18)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(allowAutoRangingCheckBox)
                    .add(autolabellingCheckbox)
                    .add(autolayoutCheckbox))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        org.jdesktop.layout.GroupLayout jPanel3Layout = new org.jdesktop.layout.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jLabel1)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, xAxisPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, yAxisPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(zAxisPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel3Layout.createSequentialGroup()
                        .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .add(zAxisPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(jPanel3Layout.createSequentialGroup()
                        .add(xAxisPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(yAxisPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jScrollPane1.setViewportView(jPanel3);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 820, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 373, Short.MAX_VALUE))
        );

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

    private void autolayoutCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autolayoutCheckboxActionPerformed
        if ( autolayoutCheckbox.isSelected() ) {
            LayoutUtil.autolayout( applicationController.getDasCanvas(),
                 applicationController.getRow(), applicationController.getColumn() );
        }
    }//GEN-LAST:event_autolayoutCheckboxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox allowAutoRangingCheckBox;
    private javax.swing.JCheckBox autolabellingCheckbox;
    private javax.swing.JCheckBox autolayoutCheckbox;
    private javax.swing.JCheckBox isotropicCheckBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JCheckBox legendEnableCheckbox;
    private javax.swing.JTextField legendTextField;
    private javax.swing.JTextField titleTextField;
    private javax.swing.JPanel xAxisPanel;
    private javax.swing.JPanel xAxisRangePanel;
    private javax.swing.JCheckBox xLog;
    private javax.swing.JTextField xTitleTextField;
    private javax.swing.JPanel yAxisPanel;
    private javax.swing.JPanel yAxisRangePanel;
    private javax.swing.JCheckBox yLog;
    private javax.swing.JTextField yTitleTextField;
    private javax.swing.JPanel zAxisPanel;
    private javax.swing.JPanel zAxisRangePanel;
    private javax.swing.JCheckBox zLog;
    private javax.swing.JTextField zTitleTextField;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables


}
