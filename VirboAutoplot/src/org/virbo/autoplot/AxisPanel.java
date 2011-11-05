/*
 * PlotStylePanel.java
 *
 * Created on July 27, 2007, 9:41 AM
 */
package org.virbo.autoplot;

import java.awt.event.FocusEvent;
import org.das2.components.DatumRangeEditor;
import java.awt.BorderLayout;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.autoplot.help.AutoplotHelpSystem;
import org.das2.datum.DatumRange;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasPlot;
import org.das2.system.RequestProcessor;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.autoplot.dom.PlotElement;
import org.virbo.autoplot.dom.Plot;

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
        
        this.applicationController.addPropertyChangeListener( ApplicationController.PROP_PLOT, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                doPlotBindings();
            }
        });

        this.applicationController.addPropertyChangeListener( ApplicationController.PROP_PLOT_ELEMENT, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                doPanelBindings();
            }
        });
        initComponents();

        DasPlot plot = applicationController.getPlot().getController().getDasPlot();
        DasColorBar colorbar = applicationController.getPlot().getController().getDasColorBar();

        xredit = new DatumRangeEditor();
        xredit.setValue(plot.getXAxis().getDatumRange());
        xredit.addFocusListener( createDatumRangeEditorListener(xredit) );
        xredit.setToolTipText("X axis range");
        xAxisRangePanel.add(xredit, BorderLayout.CENTER);

        yredit = new DatumRangeEditor();
        yredit.setValue(plot.getYAxis().getDatumRange());
        yredit.addFocusListener( createDatumRangeEditorListener(yredit) );
        yredit.setToolTipText("Y axis range");
        yAxisRangePanel.add(yredit, BorderLayout.CENTER);

        zredit = new DatumRangeEditor();
        zredit.setValue(colorbar.getDatumRange());
        zredit.addFocusListener( createDatumRangeEditorListener(zredit) );
        zredit.setToolTipText("Z axis range");
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
            }
        };
        SwingUtilities.invokeLater(run);

        AutoplotHelpSystem.getHelpSystem().registerHelpID(this, "axisPanel");

    }

    private FocusListener createDatumRangeEditorListener( final DatumRangeEditor edit ) {
        return new FocusListener() {
            public void focusGained(FocusEvent e) {
            }
            public void focusLost(FocusEvent e) {
                edit.setValue( edit.getValue() );
            }
        };
    }

    private void doApplicationBindings() {

        Binding b;
        //BindingGroup bc = new BindingGroup();

        //bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom, BeanProperty.create( "options.autoranging"), this.allowAutoRangingCheckBox, BeanProperty.create( "selected")));
        //bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom, BeanProperty.create( "options.autolabelling"), this.autolabellingCheckbox, BeanProperty.create( "selected")));
        //bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom, BeanProperty.create( "options.autolayout"), this.autolayoutCheckbox,BeanProperty.create(  "selected")));
        //bc.bind();
    }

    BindingGroup plotBindingGroup;

    private BindingGroup doPlotBindings() {

        BindingGroup bc = new BindingGroup();
        Binding b;
        Plot p = applicationController.getPlot();

        if (plotBindingGroup != null) plotBindingGroup.unbind();
        //http://www.infoq.com/news/2007/09/beans-binding
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("xaxis.label"), xTitleTextField, BeanProperty.create("text_ON_ACTION_OR_FOCUS_LOST")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p,BeanProperty.create( "xaxis.range"), xredit, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("xaxis.log"), xLog, BeanProperty.create("selected")));

        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("yaxis.label"), yTitleTextField, BeanProperty.create("text_ON_ACTION_OR_FOCUS_LOST")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("yaxis.range"), yredit,BeanProperty.create( "value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("yaxis.log"), yLog, BeanProperty.create("selected")));

        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("zaxis.label"), zTitleTextField, BeanProperty.create("text_ON_ACTION_OR_FOCUS_LOST")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("zaxis.range"), zredit, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("zaxis.log"), zLog, BeanProperty.create("selected")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("zaxis.visible"), cbVisibleCB, BeanProperty.create("selected")));

        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("title"), titleTextField, BeanProperty.create("text_ON_ACTION_OR_FOCUS_LOST")));

        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("isotropic"), this.isotropicCheckBox, BeanProperty.create("selected")));

        plotBindingGroup = bc;
        bc.bind();

        return bc;
    }

    BindingGroup panelBindingGroup;
    
    PlotElement panel;

    private void doPanelBindings() {
        BindingGroup bc = new BindingGroup();
        
        if (panelBindingGroup != null) panelBindingGroup.unbind();

        PlotElement p = applicationController.getPlotElement();
        panel= p;
        
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("legendLabel"), legendTextField, BeanProperty.create("text_ON_ACTION_OR_FOCUS_LOST")));
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

        xAxisPanel = new javax.swing.JPanel();
        xLog = new javax.swing.JCheckBox();
        xAxisRangePanel = new javax.swing.JPanel();
        xTitleTextField = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        zAxisPanel = new javax.swing.JPanel();
        zLog = new javax.swing.JCheckBox();
        zAxisRangePanel = new javax.swing.JPanel();
        zTitleTextField = new javax.swing.JTextField();
        cbVisibleCB = new javax.swing.JCheckBox();
        jLabel3 = new javax.swing.JLabel();
        yAxisPanel = new javax.swing.JPanel();
        yAxisRangePanel = new javax.swing.JPanel();
        yLog = new javax.swing.JCheckBox();
        isotropicCheckBox = new javax.swing.JCheckBox();
        yTitleTextField = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        titleTextField = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        legendEnableCheckbox = new javax.swing.JCheckBox();
        legendTextField = new javax.swing.JTextField();

        xAxisPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("X Axis [?]"));
        xAxisPanel.setToolTipText("click title for help");

        xLog.setText("Log");
        xLog.setToolTipText("X axis logarithmic scale");
        xLog.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        xAxisRangePanel.setLayout(new java.awt.BorderLayout());

        xTitleTextField.setText("jTextField1");
        xTitleTextField.setToolTipText("X axis title");

        jLabel1.setText("Label:");

        org.jdesktop.layout.GroupLayout xAxisPanelLayout = new org.jdesktop.layout.GroupLayout(xAxisPanel);
        xAxisPanel.setLayout(xAxisPanelLayout);
        xAxisPanelLayout.setHorizontalGroup(
            xAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(xAxisPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(xAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(xAxisRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 227, Short.MAX_VALUE)
                    .add(xLog)
                    .add(xAxisPanelLayout.createSequentialGroup()
                        .add(jLabel1)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(xTitleTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 177, Short.MAX_VALUE)))
                .addContainerGap())
        );
        xAxisPanelLayout.setVerticalGroup(
            xAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(xAxisPanelLayout.createSequentialGroup()
                .add(xAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(xTitleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel1))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(xAxisRangePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(xLog)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        zAxisPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Colorbar [?]"));
        zAxisPanel.setToolTipText("click title for help");

        zLog.setText("Log");
        zLog.setToolTipText("colorbar logarithmic scale");
        zLog.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        zAxisRangePanel.setLayout(new java.awt.BorderLayout());

        zTitleTextField.setText("jTextField1");
        zTitleTextField.setToolTipText("Colorbar title");

        cbVisibleCB.setText("Visible");
        cbVisibleCB.setToolTipText("hide/show colorbar");

        jLabel3.setText("Label:");

        org.jdesktop.layout.GroupLayout zAxisPanelLayout = new org.jdesktop.layout.GroupLayout(zAxisPanel);
        zAxisPanel.setLayout(zAxisPanelLayout);
        zAxisPanelLayout.setHorizontalGroup(
            zAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(zAxisPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(zAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(zAxisRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                    .add(zAxisPanelLayout.createSequentialGroup()
                        .add(zLog)
                        .add(18, 18, 18)
                        .add(cbVisibleCB))
                    .add(zAxisPanelLayout.createSequentialGroup()
                        .add(jLabel3)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(zTitleTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)))
                .addContainerGap())
        );
        zAxisPanelLayout.setVerticalGroup(
            zAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(zAxisPanelLayout.createSequentialGroup()
                .add(zAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(zTitleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel3))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(zAxisRangePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(zAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(zLog)
                    .add(cbVisibleCB))
                .addContainerGap(22, Short.MAX_VALUE))
        );

        yAxisPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Y Axis [?]"));
        yAxisPanel.setToolTipText("click title for help");

        yAxisRangePanel.setLayout(new java.awt.BorderLayout());

        yLog.setText("Log");
        yLog.setToolTipText("Y axis logarithmic scale");
        yLog.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        isotropicCheckBox.setText("Isotropic");
        isotropicCheckBox.setToolTipText("When units are convertable to X Axis units, automatically set y axis to ensure pixel:data ratio is the same.");
        isotropicCheckBox.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        yTitleTextField.setText("jTextField1");
        yTitleTextField.setToolTipText("Y axis title");

        jLabel2.setText("Label:");

        org.jdesktop.layout.GroupLayout yAxisPanelLayout = new org.jdesktop.layout.GroupLayout(yAxisPanel);
        yAxisPanel.setLayout(yAxisPanelLayout);
        yAxisPanelLayout.setHorizontalGroup(
            yAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(yAxisPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(yAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(yAxisRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 227, Short.MAX_VALUE)
                    .add(yLog)
                    .add(isotropicCheckBox)
                    .add(yAxisPanelLayout.createSequentialGroup()
                        .add(jLabel2)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(yTitleTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 177, Short.MAX_VALUE)))
                .addContainerGap())
        );
        yAxisPanelLayout.setVerticalGroup(
            yAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(yAxisPanelLayout.createSequentialGroup()
                .add(yAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(yTitleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel2))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(yAxisRangePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(yLog)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(isotropicCheckBox)
                .addContainerGap(14, Short.MAX_VALUE))
        );

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Plot [?]"));
        jPanel1.setToolTipText("click title for help");

        titleTextField.setText("title will go here");
        titleTextField.setToolTipText("title for the selected plot.\n");

        jLabel6.setText("Title:");

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
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel1Layout.createSequentialGroup()
                        .add(jLabel6)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(titleTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 258, Short.MAX_VALUE))
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel1Layout.createSequentialGroup()
                        .add(legendEnableCheckbox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(legendTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 183, Short.MAX_VALUE)))
                .addContainerGap())
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
                .addContainerGap(48, Short.MAX_VALUE))
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(yAxisPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, xAxisPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(zAxisPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jPanel1, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                    .add(xAxisPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                    .add(yAxisPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                    .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                    .add(zAxisPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
        );

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox cbVisibleCB;
    private javax.swing.JCheckBox isotropicCheckBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JPanel jPanel1;
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
