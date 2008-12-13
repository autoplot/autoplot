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
import javax.beans.binding.Binding;
import javax.beans.binding.BindingContext;
import javax.swing.DefaultComboBoxModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasPlot;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.DataSourceController;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.autoplot.dom.Panel;
import org.virbo.autoplot.dom.Plot;

/**
 *
 * @author  jbf
 */
public class AxisPanel extends javax.swing.JPanel {

    ApplicationModel applicationModel;
    Application dom;
    DatumRangeEditor xredit;
    DatumRangeEditor yredit;
    DatumRangeEditor zredit;
    private final static Logger logger = Logger.getLogger("virbo.autoplot");

    /** Creates new form PlotStylePanel */
    public AxisPanel(final ApplicationModel applicationModel) {
        this.applicationModel = applicationModel;
        this.dom = applicationModel.dom;

        this.dom.addPropertyChangeListener(Application.PROP_PANEL, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                setPanel(AxisPanel.this.dom.getPanel());
            }
        });

        this.dom.addPropertyChangeListener( Application.PROP_PLOT, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                setPlot(AxisPanel.this.dom.getPlot());
            }
        });
        initComponents();

        DasPlot plot = dom.getPlot().getController().getDasPlot();
        DasColorBar colorbar = dom.getPlot().getController().getDasColorBar();

        xredit = new DatumRangeEditor();
        xredit.setValue(plot.getXAxis().getDatumRange());
        xAxisRangePanel.add(xredit, BorderLayout.CENTER);

        yredit = new DatumRangeEditor();
        yredit.setValue(plot.getYAxis().getDatumRange());
        yAxisRangePanel.add(yredit, BorderLayout.CENTER);

        zredit = new DatumRangeEditor();
        zredit.setValue(colorbar.getDatumRange());
        zAxisRangePanel.add(zredit, BorderLayout.CENTER);

        doPlotBindings();

        doApplicationBindings();

        doPanelBindings();

    }

    private void doApplicationBindings() {

        Binding b;
        BindingContext bc = new BindingContext();

        b = bc.addBinding(dom, "${options.autoOverview}", this.autoContextOverview, "selected");
        b = bc.addBinding(dom, "${options.autoranging}", this.allowAutoRangingCheckBox, "selected");
        b = bc.addBinding(dom, "${options.autolabelling}", this.autolabellingCheckbox, "selected");
        b = bc.addBinding(dom, "${options.autolayout}", this.autolayoutCheckbox, "selected");
        bc.bind();
    }
    BindingContext panelBindingContext;

    private void doPanelBindings() {

        Panel panel = dom.getPanel();

        if (panelBindingContext != null) panelBindingContext.unbind();

        if (panel == null) {
            panelBindingContext = null;
            return;
        }

        Binding b;

        BindingContext bc = new BindingContext();
        b = bc.addBinding(panel, "${dataSourceFilter.fill}", this.fillValueComboBox, "selectedItem");
        b = bc.addBinding(panel, "${dataSourceFilter.validRange}", this.validRangeComboBox, "selectedItem");

        b = bc.addBinding(panel, "${dataSourceFilter.sliceDimension}", this.sliceTypeComboBox, "selectedIndex");
        b = bc.addBinding(panel, "${dataSourceFilter.sliceIndex}", this.sliceIndexSpinner, "value");

        b = bc.addBinding(panel, "${dataSourceFilter.transpose}", this.transposeCheckBox, "selected");

        bc.bind();
        panelBindingContext = bc;


        final DataSourceFilter dsf = panel.getDataSourceFilter();

        sliceIndexSpinner.setModel(new SpinnerNumberModel(0, 0, 100, 1));
        sliceIndexSpinner.addMouseWheelListener(new MouseWheelListener() {

            public void mouseWheelMoved(MouseWheelEvent e) {
                int pos = (Integer) sliceIndexSpinner.getValue();
                pos -= e.getWheelRotation();
                if (pos < 0) pos = 0;
                int maxpos = dsf.getController().getMaxSliceIndex(dsf.getSliceDimension());
                if (pos >= maxpos) pos = maxpos - 1;
                sliceIndexSpinner.setValue(pos);
            }
        });

//TODO:removePropertyChangeListener
        dsf.getController().addPropertyChangeListener(new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(DataSourceController.PROP_DEPNAMES)) {
                    final String[] depNames = (String[]) dsf.getController().getDepnames().toArray(); //TODO: what if panel changes...
                    for (int i = 0; i < depNames.length; i++) {
                        depNames[i] = depNames[i] + " (" + dsf.getController().getMaxSliceIndex(i) + " bins)";
                    }
                    SwingUtilities.invokeLater(new Runnable() {

                        public void run() {
                            sliceTypeComboBox.setModel(new DefaultComboBoxModel(depNames));
                            sliceTypeComboBox.setSelectedIndex(dsf.getSliceDimension());
                        }
                    });
                }
                if (evt.getPropertyName().equals(DataSourceFilter.PROP_SLICEDIMENSION)) {
                    SwingUtilities.invokeLater(new Runnable() {

                        public void run() {
                            sliceTypeComboBox.setSelectedIndex(dsf.getSliceDimension());
                        }
                    });
                }
            }
        });

    }
    BindingContext plotBindingContext;

    private BindingContext doPlotBindings() {

        BindingContext bc = new BindingContext();
        Binding b;
        Plot p = dom.getPlot();
        b = bc.addBinding(p, "${xaxis.label}", xTitleTextField, "text");
        b = bc.addBinding(p, "${xaxis.range}", xredit, "value");
        b = bc.addBinding(p, "${xaxis.log}", xLog, "selected");

        b = bc.addBinding(p, "${yaxis.label}", yTitleTextField, "text");
        b = bc.addBinding(p, "${yaxis.range}", yredit, "value");
        b = bc.addBinding(p, "${yaxis.log}", yLog, "selected");

        b = bc.addBinding(p, "${zaxis.label}", zTitleTextField, "text");
        b = bc.addBinding(p, "${zaxis.range}", zredit, "value");
        b = bc.addBinding(p, "${zaxis.log}", zLog, "selected");

        b = bc.addBinding(p, "${title}", titleTextField, "text");

        b = bc.addBinding(p, "${isotropic}", this.isotropicCheckBox, "selected");

        if (plotBindingContext != null) plotBindingContext.unbind();
        plotBindingContext = bc;
        bc.bind();

        return bc;
    }

    private void setPanel(Panel p) {
        doPanelBindings();
    }

    private void setPlot(Plot p) {
        doPlotBindings();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        xAxisPanel = new javax.swing.JPanel();
        xLog = new javax.swing.JCheckBox();
        xAxisRangePanel = new javax.swing.JPanel();
        showOverviewPlot = new javax.swing.JCheckBox();
        autoContextOverview = new javax.swing.JCheckBox();
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
        jLabel2 = new javax.swing.JLabel();
        validRangeLabel = new javax.swing.JLabel();
        fillValueLabel = new javax.swing.JLabel();
        fillValueComboBox = new javax.swing.JComboBox();
        validRangeComboBox = new javax.swing.JComboBox();
        allowAutoRangingCheckBox = new javax.swing.JCheckBox();
        titleTextField = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        autolayoutCheckbox = new javax.swing.JCheckBox();
        autolabellingCheckbox = new javax.swing.JCheckBox();
        jPanel2 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        sliceTypeComboBox = new javax.swing.JComboBox();
        sliceIndexSpinner = new javax.swing.JSpinner();
        jLabel5 = new javax.swing.JLabel();
        transposeCheckBox = new javax.swing.JCheckBox();

        jLabel1.setText("Axes");

        xAxisPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("X Axis"));

        xLog.setText("log");
        xLog.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        xAxisRangePanel.setLayout(new java.awt.BorderLayout());

        showOverviewPlot.setText("Show context overview plot");
        showOverviewPlot.setToolTipText("A second plot below the main plot is made visible, so that main plot can be seen in context.  The overview plot is useful for navagating as well.\n"); // NOI18N
        showOverviewPlot.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        autoContextOverview.setText("Auto");
        autoContextOverview.setToolTipText("Automatically show context overview plot when zooming in along a horizontal axis."); // NOI18N

        xTitleTextField.setText("jTextField1");

        org.jdesktop.layout.GroupLayout xAxisPanelLayout = new org.jdesktop.layout.GroupLayout(xAxisPanel);
        xAxisPanel.setLayout(xAxisPanelLayout);
        xAxisPanelLayout.setHorizontalGroup(
            xAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(xAxisPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(xAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, xTitleTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 272, Short.MAX_VALUE)
                    .add(xAxisRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 272, Short.MAX_VALUE)
                    .add(xLog)
                    .add(xAxisPanelLayout.createSequentialGroup()
                        .add(showOverviewPlot)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(autoContextOverview)))
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
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(xAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(showOverviewPlot)
                    .add(autoContextOverview))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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
                    .add(zAxisRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 272, Short.MAX_VALUE)
                    .add(zTitleTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 272, Short.MAX_VALUE))
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
        isotropicCheckBox.setEnabled(false);

        yTitleTextField.setText("jTextField1");

        org.jdesktop.layout.GroupLayout yAxisPanelLayout = new org.jdesktop.layout.GroupLayout(yAxisPanel);
        yAxisPanel.setLayout(yAxisPanelLayout);
        yAxisPanelLayout.setHorizontalGroup(
            yAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(yAxisPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(yAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(yAxisRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 272, Short.MAX_VALUE)
                    .add(yLog)
                    .add(isotropicCheckBox)
                    .add(yTitleTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 272, Short.MAX_VALUE))
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

        jLabel2.setText("<html><em>identify fill within the dataset so that the points are not considered in the autorange, and are not plotted.</em></html>");

        validRangeLabel.setText("Valid Range:");
        validRangeLabel.setToolTipText("measurements within this range are considered valid.  This field may be changed to exclude outliers or data that has not automatically been detected as fill.");

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

        titleTextField.setText("jTextField1");

        jLabel6.setText("Title:");

        autolayoutCheckbox.setText("Autolayout");
        autolayoutCheckbox.setToolTipText("<html><p>Allow the application to reposition axes so labels are not clipped and unused space is reduced.  </P><p>Axes can be positioned manually by turning off this option, then hold shift down to enable plot corner drag anchors.</p></html>");

        autolabellingCheckbox.setText("Autolabelling");
        autolabellingCheckbox.setToolTipText("allow automatic setting of axis labels based on metadata.\n");

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jLabel6)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(titleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 318, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(allowAutoRangingCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(autolabellingCheckbox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(autolayoutCheckbox))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, jLabel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 369, Short.MAX_VALUE)
                    .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                        .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel1Layout.createSequentialGroup()
                            .add(fillValueLabel)
                            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(fillValueComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel1Layout.createSequentialGroup()
                            .add(validRangeLabel)
                            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
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
                    .add(allowAutoRangingCheckBox)
                    .add(autolabellingCheckbox)
                    .add(autolayoutCheckbox))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jLabel2)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(validRangeLabel)
                    .add(validRangeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(fillValueLabel)
                    .add(fillValueComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(61, Short.MAX_VALUE))
        );

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("rank reduction"));

        jLabel3.setText("<html><em>specify how datasets with rank 3 dimensionality should be reduced for display</em></html>");
        jLabel3.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        jLabel4.setText("Slice Dimension:");

        sliceTypeComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "first", "second", "last" }));
        sliceTypeComboBox.setSelectedIndex(2);
        sliceTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sliceTypeComboBoxActionPerformed(evt);
            }
        });

        jLabel5.setText("Slice Index:");

        transposeCheckBox.setText("transpose");
        transposeCheckBox.setToolTipText("Transpose the rank 2 dataset after slicing.\n"); // NOI18N
        transposeCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                transposeCheckBoxActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jLabel3, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 369, Short.MAX_VALUE)
                    .add(transposeCheckBox)
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jLabel4)
                            .add(jLabel5))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(sliceIndexSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 104, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(sliceTypeComboBox, 0, 242, Short.MAX_VALUE))))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(jLabel3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 49, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel4)
                    .add(sliceTypeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(3, 3, 3)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel5)
                    .add(sliceIndexSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(transposeCheckBox)
                .addContainerGap(13, Short.MAX_VALUE))
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, yAxisPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(xAxisPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jLabel1)
                    .add(zAxisPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(jPanel2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        layout.linkSize(new java.awt.Component[] {jPanel1, jPanel2}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jPanel2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(layout.createSequentialGroup()
                        .add(xAxisPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(yAxisPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(zAxisPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void validRangeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_validRangeComboBoxActionPerformed
        String s = (String) validRangeComboBox.getSelectedItem();
        if (s.equals("(none)")) s = "";
        dom.getPanel().getDataSourceFilter().setValidRange(s);
    }//GEN-LAST:event_validRangeComboBoxActionPerformed

    private void fillValueComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fillValueComboBoxActionPerformed
        String s = (String) fillValueComboBox.getSelectedItem();
        if (s.equals("(none)")) s = "";
        dom.getPanel().getDataSourceFilter().setFill(s);
    }//GEN-LAST:event_fillValueComboBoxActionPerformed

    private void sliceTypeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sliceTypeComboBoxActionPerformed
        logger.fine("set slice dimension " + sliceTypeComboBox.getSelectedIndex());
        DataSourceFilter dsf = dom.getPanel().getDataSourceFilter();
        dsf.setSliceDimension(sliceTypeComboBox.getSelectedIndex());
        int max = dsf.getController().getMaxSliceIndex(dsf.getSliceDimension());
        if (max > 0) max--; // make inclusive, was exclusive.
        this.sliceIndexSpinner.setModel(new SpinnerNumberModel(0, 0, max, 1));
    //applicationModel.getPanelController().updateFill(true,true);
    }//GEN-LAST:event_sliceTypeComboBoxActionPerformed

    private void transposeCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_transposeCheckBoxActionPerformed
        //applicationModel.updateFill(true);
}//GEN-LAST:event_transposeCheckBoxActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox allowAutoRangingCheckBox;
    private javax.swing.JCheckBox autoContextOverview;
    private javax.swing.JCheckBox autolabellingCheckbox;
    private javax.swing.JCheckBox autolayoutCheckbox;
    private javax.swing.JComboBox fillValueComboBox;
    private javax.swing.JLabel fillValueLabel;
    private javax.swing.JCheckBox isotropicCheckBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JCheckBox showOverviewPlot;
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
    // End of variables declaration//GEN-END:variables
}
