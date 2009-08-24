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

        sliceIndexSpinner.addMouseWheelListener(new MouseWheelListener() {

            public void mouseWheelMoved(MouseWheelEvent e) {
                int pos = (Integer) sliceIndexSpinner.getValue();
                pos -= e.getWheelRotation();
                if (pos < 0) pos = 0;
                int maxpos = dsf.getController().getMaxSliceIndex(dsf.getSliceDimension());
                if ( maxpos==0 ) return;
                if (pos >= maxpos) pos = maxpos - 1;
                sliceIndexSpinner.setValue(pos);
            }
        });


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

    private void updateSliceTypeComboBox( DataSourceFilter dsf, boolean immediately ) {

        final String[] depNames1 = new String[4];

        final String[] depNames = (String[]) dsf.getController().getDepnames().toArray( new String[ dsf.getController().getDepnames().size()] ); //TODO: what if panelId changes...
        for (int i = 0; i < depNames.length; i++) {
            depNames1[i] = depNames[i] + " (" + dsf.getController().getMaxSliceIndex(i) + " bins)";
        }
        if ( dsf.getSliceDimension()>=depNames.length ) {
            dsf.setSliceDimension(0); // we used to not care, so old vap files were sloppy.
        }
        if ( immediately ) {
            sliceTypeComboBox.setModel(new DefaultComboBoxModel(depNames1));
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    sliceTypeComboBox.setModel(new DefaultComboBoxModel(depNames1));
                }
            });
        }
    }

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

        updateSliceTypeComboBox(newDsf,true);

        BindingGroup bc = new BindingGroup();
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,newDsf, BeanProperty.create("fill"), this.fillValueComboBox, BeanProperty.create("selectedItem")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,newDsf, BeanProperty.create("validRange"), this.validRangeComboBox, BeanProperty.create("selectedItem")));

        //bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,newDsf, BeanProperty.create("sliceDimension"), this.sliceTypeComboBox, BeanProperty.create("selectedIndex")));
        //bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,newDsf, BeanProperty.create( "sliceIndex"), this.sliceIndexSpinner, BeanProperty.create("value")));

        //bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,newDsf, BeanProperty.create("transpose"), this.transposeCheckBox, BeanProperty.create("selected")));

        try {
            bc.bind();
        } catch ( RuntimeException e ) {
            throw e;
        }
        dataSourceFilterBindingGroup = bc;

        if ( newDsf!=null ) {
            int max= newDsf.getController().getMaxSliceIndex(newDsf.getSliceDimension());
            if ( max>0 ) sliceIndexSpinner.setModel( new SpinnerNumberModel( 0, 0, max-1, 1) );
        }

        dsfListener= new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(DataSourceController.PROP_DEPNAMES)) {
                    updateSliceTypeComboBox( newDsf, false );
                }
            }
        };
        
        dsf= newDsf;
        newDsf.getController().addPropertyChangeListener(dsfListener);

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
    private PropertyChangeListener panelListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if ( evt.getPropertyName().equals( Panel.PROP_COMPONENT ) ) {
                componentTextField.setText((String) evt.getNewValue());
                componentChanged();
            }
        }
    };
    Panel panel;

    private void doPanelBindings() {
        BindingGroup bc = new BindingGroup();
        if ( panel!=null ) {
            panel.removePropertyChangeListener(panelListener);
        }
        if (panelBindingGroup != null) panelBindingGroup.unbind();

        Panel p = applicationController.getPanel();
        panel= p;
        panel.addPropertyChangeListener(panelListener);
        
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("legendLabel"), legendTextField, BeanProperty.create("text")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, p, BeanProperty.create("displayLegend"), legendEnableCheckbox, BeanProperty.create("selected")));
        componentTextField.setText(p.getComponent());

        componentChanged();
        
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
        validRangeLabel = new javax.swing.JLabel();
        fillValueLabel = new javax.swing.JLabel();
        fillValueComboBox = new javax.swing.JComboBox();
        validRangeComboBox = new javax.swing.JComboBox();
        allowAutoRangingCheckBox = new javax.swing.JCheckBox();
        titleTextField = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        autolayoutCheckbox = new javax.swing.JCheckBox();
        autolabellingCheckbox = new javax.swing.JCheckBox();
        legendEnableCheckbox = new javax.swing.JCheckBox();
        legendTextField = new javax.swing.JTextField();
        jPanel2 = new javax.swing.JPanel();
        sliceTypeComboBox = new javax.swing.JComboBox();
        sliceIndexSpinner = new javax.swing.JSpinner();
        jLabel5 = new javax.swing.JLabel();
        transposeCheckBox = new javax.swing.JCheckBox();
        jLabel3 = new javax.swing.JLabel();
        componentTextField = new javax.swing.JTextField();
        doSliceCheckBox = new javax.swing.JCheckBox();

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
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, xTitleTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 289, Short.MAX_VALUE)
                    .add(xAxisRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 289, Short.MAX_VALUE)
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
                    .add(zAxisRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 289, Short.MAX_VALUE)
                    .add(zTitleTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 289, Short.MAX_VALUE))
                .addContainerGap())
        );
        zAxisPanelLayout.setVerticalGroup(
            zAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, zAxisPanelLayout.createSequentialGroup()
                .add(zTitleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
                    .add(yAxisRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 289, Short.MAX_VALUE)
                    .add(yLog)
                    .add(isotropicCheckBox)
                    .add(yTitleTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 289, Short.MAX_VALUE))
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

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("dataset"));

        validRangeLabel.setText("Valid Range:");
        validRangeLabel.setToolTipText("measurements within this range are considered valid.  This field may be changed to exclude outliers or data that has not automatically been detected as fill.\n");

        fillValueLabel.setText("Fill Value:");
        fillValueLabel.setToolTipText("This value is used to identify invalid data that should not be plotted.");

        fillValueComboBox.setEditable(true);
        fillValueComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "", "-1e31", "0.0", "-1" }));
        fillValueComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fillValueComboBoxActionPerformed(evt);
            }
        });

        validRangeComboBox.setEditable(true);
        validRangeComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "", "-1e30 to 1e30", "-1 to 101", "0 to 1e38" }));
        validRangeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                validRangeComboBoxActionPerformed(evt);
            }
        });

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
                                .add(legendEnableCheckbox)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(legendTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 243, Short.MAX_VALUE))
                            .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel1Layout.createSequentialGroup()
                                .add(jLabel6)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(titleTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 333, Short.MAX_VALUE)))
                        .add(11, 11, 11))
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(validRangeLabel)
                            .add(fillValueLabel))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(fillValueComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(validRangeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
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
                .add(18, 18, 18)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(allowAutoRangingCheckBox)
                    .add(autolabellingCheckbox)
                    .add(autolayoutCheckbox))
                .add(18, 18, 18)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(validRangeLabel)
                    .add(validRangeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(fillValueLabel)
                    .add(fillValueComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(38, Short.MAX_VALUE))
        );

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("rank reduction"));

        sliceTypeComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "first", "second", "last" }));
        sliceTypeComboBox.setSelectedIndex(2);
        sliceTypeComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                sliceTypeComboBoxItemStateChanged(evt);
            }
        });
        sliceTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sliceTypeComboBoxActionPerformed(evt);
            }
        });

        sliceIndexSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                sliceIndexSpinnerStateChanged(evt);
            }
        });

        jLabel5.setText("Slice Index:");

        transposeCheckBox.setText("Transpose");
        transposeCheckBox.setToolTipText("Transpose the rank 2 dataset after slicing.\n"); // NOI18N
        transposeCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                transposeCheckBoxActionPerformed(evt);
            }
        });

        jLabel3.setText("Component:");
        jLabel3.setToolTipText("process string that specifies component to plot, or how a data set's dimensionality should be reduced before display.");

        componentTextField.setText("jTextField1");
        componentTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                componentTextFieldActionPerformed(evt);
            }
        });

        doSliceCheckBox.setText("Slice Dimension");
        doSliceCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                doSliceCheckBoxActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(jLabel3)
                        .add(18, 18, 18)
                        .add(componentTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 269, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(doSliceCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(sliceTypeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 215, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(12, 12, 12)
                        .add(jLabel5)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(sliceIndexSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 104, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(transposeCheckBox))
                .addContainerGap(8, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel3)
                    .add(componentTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(doSliceCheckBox)
                    .add(sliceTypeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel5)
                    .add(sliceIndexSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(transposeCheckBox))
        );

        org.jdesktop.layout.GroupLayout jPanel3Layout = new org.jdesktop.layout.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jLabel1)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, yAxisPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, xAxisPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, zAxisPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel2, 0, 415, Short.MAX_VALUE)
                    .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(462, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel3Layout.createSequentialGroup()
                        .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(12, 12, 12)
                        .add(jPanel2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(jPanel3Layout.createSequentialGroup()
                        .add(xAxisPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(yAxisPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(zAxisPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(226, Short.MAX_VALUE))
        );

        jScrollPane1.setViewportView(jPanel3);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 782, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 482, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
        );

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

    private void validRangeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_validRangeComboBoxActionPerformed
        String s = (String) validRangeComboBox.getSelectedItem();
        if (s.equals("(none)")) s = "";
        applicationController.getDataSourceFilter().setValidRange(s);
    }//GEN-LAST:event_validRangeComboBoxActionPerformed

    private void fillValueComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fillValueComboBoxActionPerformed
        String s = (String) fillValueComboBox.getSelectedItem();
        if (s.equals("(none)")) s = "";
        applicationController.getDataSourceFilter().setFill(s);
    }//GEN-LAST:event_fillValueComboBoxActionPerformed

    private void sliceTypeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sliceTypeComboBoxActionPerformed
        if ( adjusting ) return; // TODO: probably get rid of this entirely.
        logger.fine("set slice dimension " + sliceTypeComboBox.getSelectedIndex());
        //DataSourceFilter dsf = applicationController.getDataSourceFilter();
        //dsf.setSliceDimension(sliceTypeComboBox.getSelectedIndex());
        int max = dsf.getController().getMaxSliceIndex(sliceTypeComboBox.getSelectedIndex());
        if (max > 0) max--; // make inclusive, was exclusive.
        this.sliceIndexSpinner.setModel(new SpinnerNumberModel(0, 0, max, 1));

        updateComponent();
    }//GEN-LAST:event_sliceTypeComboBoxActionPerformed

    private void transposeCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_transposeCheckBoxActionPerformed
        updateComponent();
}//GEN-LAST:event_transposeCheckBoxActionPerformed

    private void autolayoutCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autolayoutCheckboxActionPerformed
        if ( autolayoutCheckbox.isSelected() ) {
            LayoutUtil.autolayout( applicationController.getDasCanvas(),
                 applicationController.getRow(), applicationController.getColumn() );
        }
    }//GEN-LAST:event_autolayoutCheckboxActionPerformed

    private void componentTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_componentTextFieldActionPerformed
        applicationController.getPanel().setComponent( componentTextField.getText() );
    }//GEN-LAST:event_componentTextFieldActionPerformed

    private void doSliceCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_doSliceCheckBoxActionPerformed
        updateComponent();
    }//GEN-LAST:event_doSliceCheckBoxActionPerformed

    private void sliceIndexSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_sliceIndexSpinnerStateChanged
        updateComponent();
    }//GEN-LAST:event_sliceIndexSpinnerStateChanged

    private void sliceTypeComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_sliceTypeComboBoxItemStateChanged
        updateComponent();
    }//GEN-LAST:event_sliceTypeComboBoxItemStateChanged

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox allowAutoRangingCheckBox;
    private javax.swing.JCheckBox autolabellingCheckbox;
    private javax.swing.JCheckBox autolayoutCheckbox;
    private javax.swing.JTextField componentTextField;
    private javax.swing.JCheckBox doSliceCheckBox;
    private javax.swing.JComboBox fillValueComboBox;
    private javax.swing.JLabel fillValueLabel;
    private javax.swing.JCheckBox isotropicCheckBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JCheckBox legendEnableCheckbox;
    private javax.swing.JTextField legendTextField;
    private javax.swing.JSpinner sliceIndexSpinner;
    private javax.swing.JComboBox sliceTypeComboBox;
    private javax.swing.JTextField titleTextField;
    private javax.swing.JCheckBox transposeCheckBox;
    private javax.swing.JComboBox validRangeComboBox;
    private javax.swing.JLabel validRangeLabel;
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

    private void updateComponent() {
        if ( adjusting ) return;
        String sprocess="";
        if ( doSliceCheckBox.isSelected() ) {
            sprocess+="|slice"+sliceTypeComboBox.getSelectedIndex() + "(" + sliceIndexSpinner.getValue() + ")";
        }
        if ( transposeCheckBox.isSelected() ) {
            sprocess+="|transpose";
        }
        panel.setComponent(sprocess);
    }

    private void componentChanged() {
        if ( adjusting ) return;
        String scomp= panel.getComponent();
        Pattern slicePattern= Pattern.compile("\\|slice(\\d+)\\((\\d+)\\)(\\|transpose)?");
        Matcher m= slicePattern.matcher(scomp);
        if ( m.matches() ) {
            System.err.println("matches");
            setAdjusting(true);
            doSliceCheckBox.setSelected(true);
            sliceIndexSpinner.setValue(Integer.parseInt(m.group(2)));
            sliceTypeComboBox.setSelectedIndex(Integer.parseInt(m.group(1)));
            transposeCheckBox.setSelected( m.group(3)!=null );
            setAdjusting(false);
        } else {
            System.err.println("dont match");
        }
        doSliceCheckBox.setEnabled(m.matches());
        sliceTypeComboBox.setEnabled(m.matches());
        
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

}
