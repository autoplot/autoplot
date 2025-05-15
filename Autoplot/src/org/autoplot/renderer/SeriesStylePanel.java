/*
 * PlotStylePanel.java
 *
 * Created on July 27, 2007, 9:41 AM
 */
package org.autoplot.renderer;

import java.awt.event.FocusEvent;
import org.das2.components.DatumEditor;
import org.das2.components.propertyeditor.ColorEditor;
import org.das2.components.propertyeditor.EnumerationEditor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Map;
import javax.swing.SpinnerNumberModel;
import org.autoplot.help.AutoplotHelpSystem;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.PsymConnector;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.autoplot.PlotStylePanel;
import org.autoplot.dom.PlotElement;
import org.autoplot.dom.PlotElementController;
import org.autoplot.dom.PlotElementStyle;
import org.das2.graph.ErrorBarType;
import org.das2.qds.QDataSet;

/**
 *
 * @author  jbf
 */
public final class SeriesStylePanel extends javax.swing.JPanel implements PlotStylePanel.StylePanel {

    EnumerationEditor psymEditor;
    EnumerationEditor lineEditor;
    EnumerationEditor edit;
    EnumerationEditor rebin;
    EnumerationEditor errorBarStyle;
    
    ColorEditor colorEditor;
    ColorEditor fillColorEditor;
    DatumEditor referenceEditor;
    BindingGroup elementBindingContext;
    PlotElement plotElement;

    /** Creates new form PlotStylePanel */
    public SeriesStylePanel( ) {
                
        initComponents();

        symSizeSpinner.setModel(new SpinnerNumberModel(2.0f, 0.09f, 20.f, 0.2f));

        psymEditor = new EnumerationEditor();
        psymEditor.setValue( DefaultPlotSymbol.BOX );
        psymPanel.add(psymEditor.getCustomEditor(), BorderLayout.CENTER);

        lineEditor = new EnumerationEditor();
        lineEditor.setValue( PsymConnector.SOLID );
        lineStylePanel.add(lineEditor.getCustomEditor(), BorderLayout.CENTER);

        lineThickSpinner.setModel(new SpinnerNumberModel(1.0f, 0.09f, 20.f, 0.2f));

        colorEditor = new ColorEditor();
        colorEditor.setValue( Color.BLACK );
        colorPanel.add(colorEditor.getSmallEditor(), BorderLayout.CENTER);

        fillColorEditor = new ColorEditor();

        fillColorEditor.setValue( Color.DARK_GRAY );
        fillColorPanel.add(fillColorEditor.getSmallEditor(), BorderLayout.CENTER);

        referenceEditor = new DatumEditor();
        java.awt.Component refedit= referenceEditor.getCustomEditor();
        refedit.addFocusListener( new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                referenceEditor.stopCellEditing();
            }
        });
        referenceValuePanel.add(refedit);

        errorBarStyle = new EnumerationEditor();
        errorBarStyle.setValue( ErrorBarType.BAR );
        Component myc= errorBarStyle.getCustomEditor();
        myc.setEnabled(false);
        errorBarStylePanel.add(myc, BorderLayout.CENTER);
        errorBarStylePanel.setEnabled(false);
        
        validate();
                
        AutoplotHelpSystem.getHelpSystem().registerHelpID(this, PlotStylePanel.STYLEPANEL_HELP_ID );

    }

    @Override
    public void releaseElementBindings() {
        if ( elementBindingContext!=null ) {
            elementBindingContext.unbind();
            elementBindingContext= null;
            this.plotElement.getStyle().removePropertyChangeListener( "drawError", showErrorCheckBoxPCL );
        }
    }

    @Override
    public synchronized void doElementBindings( final PlotElement element) {
        PlotElementStyle style= element.getStyle();
        BindingGroup bc = new BindingGroup();

        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style,BeanProperty.create( PlotElementStyle.PROP_SYMBOL_SIZE ), symSizeSpinner, BeanProperty.create("value")) );
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( PlotElementStyle.PROP_PLOT_SYMBOL ), psymEditor,BeanProperty.create( "value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( PlotElementStyle.PROP_LINE_WIDTH ), lineThickSpinner, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( PlotElementStyle.PROP_SYMBOL_CONNECTOR ), lineEditor, BeanProperty.create("value")));

        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( PlotElementStyle.PROP_COLOR ), colorEditor, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( PlotElementStyle.PROP_FILL_TO_REFERENCE ), fillToReferenceCheckBox, BeanProperty.create("selected")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( PlotElementStyle.PROP_FILLCOLOR ), fillColorEditor, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( PlotElementStyle.PROP_REFERENCE ), referenceEditor, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( PlotElementStyle.PROP_FILL_DIRECTION ), fillDirectionComboBox, BeanProperty.create("selectedItem")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( PlotElementStyle.PROP_DRAWERROR ), showErrorCheckBox, BeanProperty.create("selected")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( PlotElementStyle.PROP_ERRORBARTYPE ), errorBarStyle, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( PlotElementStyle.PROP_SHOWLIMITS ), showLimitsCheckBox, BeanProperty.create("selected")));
        element.getController().addPropertyChangeListener( PlotElementController.PROP_DATASET, limitsPCL );
        
        if ( elementBindingContext!=null ) {
            releaseElementBindings();
            PlotElement oldPlotElement= this.plotElement;
            oldPlotElement.getController().removePropertyChangeListener( PlotElementController.PROP_DATASET, limitsPCL );
        }
        
        style.addPropertyChangeListener( "drawError", showErrorCheckBoxPCL );
        
        this.plotElement= element;
        
        bc.bind();
        
        repaint();
        
        elementBindingContext= bc;

    }
    
    PropertyChangeListener limitsPCL= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            boolean limitsEnabled= false;
            boolean errorsEnabled= false;
            QDataSet ds= plotElement.getController().getDataSet();
            if ( ds!=null ) {
                Map<String,Object> meta= (Map<String,Object>) ds.property(QDataSet.METADATA);
                if ( meta!=null ) {
                    if ( meta.containsKey("LIMITS_WARN_MAX") || meta.containsKey("LIMITS_WARN_MIN") 
                        || meta.containsKey("LIMITS_NOMINAL_MIN") || meta.containsKey("LIMITS_NOMINAL_MAX") ) {
                        limitsEnabled= true;
                    }
                }
                if ( ds.property(QDataSet.DELTA_PLUS)!=null ) {
                    errorsEnabled= true;
                } else if ( ds.property(QDataSet.BIN_PLUS)!=null ) {
                    errorsEnabled= true;
                } else if ( ds.property(QDataSet.BIN_MAX)!=null ) {
                    errorsEnabled= true;
                }
            }
            showLimitsCheckBox.setEnabled(limitsEnabled);
            showErrorCheckBox.setEnabled(errorsEnabled);
            if ( errorsEnabled==false ) {
                showErrorCheckBox.setSelected(false);
            }
        }
    };
    
    PropertyChangeListener showErrorCheckBoxPCL= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            boolean enabled= SeriesStylePanel.this.showErrorCheckBox.isSelected();
            errorBarStylePanel.setEnabled(enabled);
            errorBarStyle.getCustomEditor().setEnabled(enabled);
            styleLabel.setEnabled(enabled);
           // styleLabel.setVisible(enabled);
        }
    };    

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel2 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        lineThickSpinner = new javax.swing.JSpinner();
        symSizeSpinner = new javax.swing.JSpinner();
        colorPanel = new javax.swing.JPanel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        fillColorPanel = new javax.swing.JPanel();
        fillToReferenceCheckBox = new javax.swing.JCheckBox();
        jLabel8 = new javax.swing.JLabel();
        referenceValuePanel = new javax.swing.JPanel();
        jLabel9 = new javax.swing.JLabel();
        psymPanel = new javax.swing.JPanel();
        jLabel10 = new javax.swing.JLabel();
        lineStylePanel = new javax.swing.JPanel();
        showLimitsCheckBox = new javax.swing.JCheckBox();
        jLabel1 = new javax.swing.JLabel();
        fillDirectionComboBox = new javax.swing.JComboBox<>();
        showErrorCheckBox = new javax.swing.JCheckBox();
        styleLabel = new javax.swing.JLabel();
        errorBarStylePanel = new javax.swing.JPanel();

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Series [?]"));

        jLabel3.setText("Line Thickness:");
        jLabel3.setToolTipText("thickness of the plot trace");

        jLabel2.setText("Symbol Size:");
        jLabel2.setToolTipText("size of the plot symbols");

        lineThickSpinner.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                lineThickSpinnerMouseWheelMoved(evt);
            }
        });

        symSizeSpinner.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                symSizeSpinnerMouseWheelMoved(evt);
            }
        });

        colorPanel.setLayout(new java.awt.BorderLayout());

        jLabel6.setText("Color:");
        jLabel6.setToolTipText("color of the line and plot symbols");

        jLabel7.setText("Fill Color:");
        jLabel7.setToolTipText("Fill with this color");

        fillColorPanel.setToolTipText("fill with this color");
        fillColorPanel.setLayout(new java.awt.BorderLayout());

        fillToReferenceCheckBox.setText("Fill To Reference");
        fillToReferenceCheckBox.setToolTipText("Fill from the plot trace to a reference value");
        fillToReferenceCheckBox.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        fillToReferenceCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fillToReferenceCheckBoxActionPerformed(evt);
            }
        });

        jLabel8.setText("Reference Value:");
        jLabel8.setToolTipText("Fill to this value");

        referenceValuePanel.setToolTipText("fill to this value");
        referenceValuePanel.setLayout(new java.awt.BorderLayout());

        jLabel9.setText("Plot Symbol:");
        jLabel9.setToolTipText("type of symbol, or none.");

        psymPanel.setLayout(new java.awt.BorderLayout());

        jLabel10.setText("Line Style:");
        jLabel10.setToolTipText("style of the plot trace, or none");

        lineStylePanel.setLayout(new java.awt.BorderLayout());

        showLimitsCheckBox.setText("Show Limits");
        showLimitsCheckBox.setToolTipText("Show nominal and warning limits if found in data.  See http://autoplot.org/developer.limits");
        showLimitsCheckBox.setEnabled(false);

        jLabel1.setText("Fill Direction:");

        fillDirectionComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "above", "below", "both" }));
        fillDirectionComboBox.setSelectedIndex(2);

        showErrorCheckBox.setText("Show Error Bars");
        showErrorCheckBox.setEnabled(false);
        showErrorCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showErrorCheckBoxActionPerformed(evt);
            }
        });

        styleLabel.setText("Style:");
        styleLabel.setEnabled(false);

        errorBarStylePanel.setLayout(new java.awt.BorderLayout());

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(21, 21, 21)
                        .add(styleLabel)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(errorBarStylePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 112, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(showErrorCheckBox)
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jLabel9)
                            .add(jLabel6)
                            .add(jLabel2)
                            .add(jLabel3)
                            .add(jLabel10))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                            .add(lineStylePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 125, Short.MAX_VALUE)
                            .add(psymPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 104, Short.MAX_VALUE)
                            .add(colorPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 130, Short.MAX_VALUE)
                            .add(symSizeSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 60, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(lineThickSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 61, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                    .add(fillToReferenceCheckBox)
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(12, 12, 12)
                        .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                            .add(jPanel2Layout.createSequentialGroup()
                                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(jLabel8)
                                    .add(jLabel7))
                                .add(12, 12, 12)
                                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                                    .add(fillColorPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 104, Short.MAX_VALUE)
                                    .add(referenceValuePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                            .add(jPanel2Layout.createSequentialGroup()
                                .add(jLabel1)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(fillDirectionComboBox, 0, 137, Short.MAX_VALUE))))
                    .add(showLimitsCheckBox))
                .addContainerGap(239, Short.MAX_VALUE))
        );

        jPanel2Layout.linkSize(new java.awt.Component[] {colorPanel, lineStylePanel, psymPanel}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        jPanel2Layout.linkSize(new java.awt.Component[] {lineThickSpinner, symSizeSpinner}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, jLabel6, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 29, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, colorPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 29, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                    .add(psymPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 28, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel9))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel2)
                    .add(symSizeSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jLabel10)
                    .add(lineStylePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 30, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel3)
                    .add(lineThickSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(fillToReferenceCheckBox)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(fillColorPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 24, Short.MAX_VALUE)
                    .add(jLabel7))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(referenceValuePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 25, Short.MAX_VALUE)
                    .add(jLabel8, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel1)
                    .add(fillDirectionComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(8, 8, 8)
                .add(showLimitsCheckBox)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(showErrorCheckBox)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(styleLabel)
                    .add(errorBarStylePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 25, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(81, Short.MAX_VALUE))
        );

        jPanel2Layout.linkSize(new java.awt.Component[] {fillColorPanel, jLabel7}, org.jdesktop.layout.GroupLayout.VERTICAL);

        jPanel2Layout.linkSize(new java.awt.Component[] {jLabel10, jLabel3, lineStylePanel}, org.jdesktop.layout.GroupLayout.VERTICAL);

        jPanel2Layout.linkSize(new java.awt.Component[] {colorPanel, jLabel2, jLabel6, jLabel9, psymPanel}, org.jdesktop.layout.GroupLayout.VERTICAL);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void fillToReferenceCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fillToReferenceCheckBoxActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);                
    }//GEN-LAST:event_fillToReferenceCheckBoxActionPerformed

    private void showErrorCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showErrorCheckBoxActionPerformed
        boolean enabled= showErrorCheckBox.isSelected();
        errorBarStyle.getCustomEditor().setEnabled( enabled );
        styleLabel.setEnabled( enabled );
    }//GEN-LAST:event_showErrorCheckBoxActionPerformed

    private void symSizeSpinnerMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_symSizeSpinnerMouseWheelMoved
        double n= ((Double)symSizeSpinner.getValue()) + evt.getWheelRotation() * 0.2;
        if ( n>20 ) n=20;
        if ( n<0 ) n=0;
        symSizeSpinner.setValue( n );
        
    }//GEN-LAST:event_symSizeSpinnerMouseWheelMoved

    private void lineThickSpinnerMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_lineThickSpinnerMouseWheelMoved
        double n= ((Double)lineThickSpinner.getValue()) + evt.getWheelRotation() * 0.2;
        if ( n>20 ) n=20;
        if ( n<0 ) n=0;
        lineThickSpinner.setValue( n );
    }//GEN-LAST:event_lineThickSpinnerMouseWheelMoved

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel colorPanel;
    private javax.swing.JPanel errorBarStylePanel;
    private javax.swing.JPanel fillColorPanel;
    private javax.swing.JComboBox<String> fillDirectionComboBox;
    private javax.swing.JCheckBox fillToReferenceCheckBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel lineStylePanel;
    private javax.swing.JSpinner lineThickSpinner;
    private javax.swing.JPanel psymPanel;
    private javax.swing.JPanel referenceValuePanel;
    private javax.swing.JCheckBox showErrorCheckBox;
    private javax.swing.JCheckBox showLimitsCheckBox;
    private javax.swing.JLabel styleLabel;
    private javax.swing.JSpinner symSizeSpinner;
    // End of variables declaration//GEN-END:variables
}
