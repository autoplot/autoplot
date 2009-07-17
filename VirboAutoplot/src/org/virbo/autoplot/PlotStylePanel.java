/*
 * PlotStylePanel.java
 *
 * Created on July 27, 2007, 9:41 AM
 */
package org.virbo.autoplot;

import java.beans.PropertyChangeEvent;
import org.das2.components.DatumEditor;
import org.das2.components.propertyeditor.ColorEditor;
import org.das2.components.propertyeditor.EnumerationEditor;
import org.das2.components.propertyeditor.PropertyEditor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.beans.PropertyChangeListener;
import javax.swing.SpinnerNumberModel;
import org.das2.graph.DasColorBar;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.PsymConnector;
import org.das2.graph.SpectrogramRenderer;
import org.das2.system.RequestProcessor;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.Options;
import org.virbo.autoplot.dom.Panel;
import org.virbo.autoplot.dom.PanelStyle;

/**
 *
 * @author  jbf
 */
public class PlotStylePanel extends javax.swing.JPanel {

    ApplicationModel applicationModel;
    
    EnumerationEditor psymEditor;
    EnumerationEditor lineEditor;
    EnumerationEditor edit;
    EnumerationEditor rebin;
    ColorEditor colorEditor;
    ColorEditor fillColorEditor;
    DatumEditor referenceEditor;
    BindingGroup panelBindingContext;

    Application dom;
    
    /** Creates new form PlotStylePanel */
    public PlotStylePanel(final ApplicationModel applicationModel) {
        this.applicationModel = applicationModel;
        this.dom= applicationModel.getDocumentModel();
        
        this.dom.getController().addPropertyChangeListener( ApplicationController.PROP_PANEL, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                doPanelBindings();
            }
        });
        
        initComponents();

        symSizeSpinner.setModel(new SpinnerNumberModel(2.0f, 0.09f, 10.f, 0.2f));

        psymEditor = new EnumerationEditor();
        psymEditor.setValue( DefaultPlotSymbol.BOX );
        psymPanel.add(psymEditor.getCustomEditor(), BorderLayout.CENTER);

        lineEditor = new EnumerationEditor();
        lineEditor.setValue( PsymConnector.SOLID );
        lineStylePanel.add(lineEditor.getCustomEditor(), BorderLayout.CENTER);

        lineThickSpinner.setModel(new SpinnerNumberModel(1.0f, 0.09f, 10.f, 0.2f));

        edit = new EnumerationEditor();
        edit.setValue( DasColorBar.Type.GRAYSCALE );
        colortableTypePanel.add(edit.getCustomEditor(), BorderLayout.CENTER);

        rebin = new EnumerationEditor();
        rebin.setValue( SpectrogramRenderer.RebinnerEnum.binAverage );
        rebinPanel.add(rebin.getCustomEditor(), BorderLayout.CENTER);

        colorEditor = new ColorEditor();
        colorEditor.setValue( Color.BLACK );
        colorPanel.add(colorEditor.getSmallEditor(), BorderLayout.CENTER);

        fillColorEditor = new ColorEditor();

        fillColorEditor.setValue( Color.DARK_GRAY );
        fillColorPanel.add(fillColorEditor.getSmallEditor(), BorderLayout.CENTER);

        referenceEditor = new DatumEditor();
        referenceValuePanel.add(referenceEditor.getCustomEditor());

        validate();

        Runnable run= new Runnable() {
            public void run() {
                doOptionsBindings();
                doPanelBindings();
            }
        };
        RequestProcessor.invokeLater(run);
    }

    public synchronized void doOptionsBindings( ) {
        BindingGroup bc = new BindingGroup();
        Binding b;

        b = Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom.getOptions(), BeanProperty.create( Options.PROP_DRAWGRID ), majorTicksCheckBox, BeanProperty.create("selected") );
        bc.addBinding(b);
        b = Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,  dom.getOptions(), BeanProperty.create( Options.PROP_DRAWMINORGRID ), minorGridCheckBox, BeanProperty.create("selected") );
        bc.addBinding(b);

        bc.bind();
    }
    

    public synchronized void doPanelBindings() {
        //TODO: why null?
        Panel panel= dom.getController().getPanel();
        if ( panel==null ) return;
        PanelStyle style= panel.getStyle();
        BindingGroup bc = new BindingGroup();
        Binding b;

        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style,BeanProperty.create(  "symbolSize" ), symSizeSpinner, BeanProperty.create("value")) );
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( "plotSymbol" ), psymEditor,BeanProperty.create( "value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( "lineWidth" ), lineThickSpinner, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( "symbolConnector" ), lineEditor, BeanProperty.create("value")));

        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( "color" ), colorEditor, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( "fillToReference" ), fillToReferenceCheckBox, BeanProperty.create("selected")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( "fillColor" ), fillColorEditor, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( "reference" ), referenceEditor, BeanProperty.create("value")));

        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( "colortable" ), edit, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( "rebinMethod" ), rebin, BeanProperty.create("value")));
        
        if ( panelBindingContext!=null ) panelBindingContext.unbind();
        bc.bind();
        
        repaint();
        
        panelBindingContext= bc;
        
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jPanel4 = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        colortableTypePanel = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        rebinPanel = new javax.swing.JPanel();
        moreSprectrogramPropsButton = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        lineThickSpinner = new javax.swing.JSpinner();
        symSizeSpinner = new javax.swing.JSpinner();
        colorPanel = new javax.swing.JPanel();
        jLabel6 = new javax.swing.JLabel();
        moreSeriesPropsButton = new javax.swing.JButton();
        jLabel7 = new javax.swing.JLabel();
        fillColorPanel = new javax.swing.JPanel();
        fillToReferenceCheckBox = new javax.swing.JCheckBox();
        jLabel8 = new javax.swing.JLabel();
        referenceValuePanel = new javax.swing.JPanel();
        jLabel9 = new javax.swing.JLabel();
        psymPanel = new javax.swing.JPanel();
        jLabel10 = new javax.swing.JLabel();
        lineStylePanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        majorTicksCheckBox = new javax.swing.JCheckBox();
        jLabel11 = new javax.swing.JLabel();
        minorGridCheckBox = new javax.swing.JCheckBox();
        gridOverCheckBox = new javax.swing.JCheckBox();

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("spectrogram"));

        colortableTypePanel.setLayout(new java.awt.BorderLayout());

        jLabel4.setText("colortable:");

        jLabel5.setText("rebin:");

        rebinPanel.setLayout(new java.awt.BorderLayout());

        moreSprectrogramPropsButton.setText("more...");
        moreSprectrogramPropsButton.setToolTipText("bring up property sheet with all properties for the spectrogram renderer.");
        moreSprectrogramPropsButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        moreSprectrogramPropsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moreSprectrogramPropsButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jLabel4)
                            .add(jLabel5))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(rebinPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 142, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(colortableTypePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 141, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .addContainerGap(51, Short.MAX_VALUE))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel1Layout.createSequentialGroup()
                        .add(moreSprectrogramPropsButton)
                        .addContainerGap())))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(7, 7, 7)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                    .add(jLabel4, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(colortableTypePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 28, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                    .add(jLabel5)
                    .add(rebinPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 25, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(12, 12, 12)
                .add(moreSprectrogramPropsButton)
                .addContainerGap())
        );

        jPanel1Layout.linkSize(new java.awt.Component[] {colortableTypePanel, rebinPanel}, org.jdesktop.layout.GroupLayout.VERTICAL);

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("symbol line"));

        jLabel3.setText("line thickness:");

        jLabel2.setText("symbol size:");

        colorPanel.setLayout(new java.awt.BorderLayout());

        jLabel6.setText("color:");

        moreSeriesPropsButton.setText("panel properties...");
        moreSeriesPropsButton.setToolTipText("bring up property sheet with all properties for the series renderer.");
        moreSeriesPropsButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        moreSeriesPropsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moreSeriesPropsButtonActionPerformed(evt);
            }
        });

        jLabel7.setText("fill color:");

        fillColorPanel.setLayout(new java.awt.BorderLayout());

        fillToReferenceCheckBox.setText("fill to reference");
        fillToReferenceCheckBox.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        jLabel8.setText("reference value:");

        referenceValuePanel.setLayout(new java.awt.BorderLayout());

        jLabel9.setText("plot symbol:");

        psymPanel.setLayout(new java.awt.BorderLayout());

        jLabel10.setText("line style:");

        lineStylePanel.setLayout(new java.awt.BorderLayout());

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(12, 12, 12)
                        .add(fillToReferenceCheckBox))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addContainerGap()
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
                            .add(lineThickSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 61, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 18, Short.MAX_VALUE))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel2Layout.createSequentialGroup()
                        .add(17, 17, 17)
                        .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel2Layout.createSequentialGroup()
                                .add(jLabel7)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(fillColorPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 130, Short.MAX_VALUE)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 51, Short.MAX_VALUE))
                            .add(jPanel2Layout.createSequentialGroup()
                                .add(jLabel8)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(org.jdesktop.layout.GroupLayout.TRAILING, moreSeriesPropsButton, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 130, Short.MAX_VALUE)
                                    .add(referenceValuePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 118, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))))
                .addContainerGap())
        );

        jPanel2Layout.linkSize(new java.awt.Component[] {lineThickSpinner, symSizeSpinner}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        jPanel2Layout.linkSize(new java.awt.Component[] {colorPanel, fillColorPanel, lineStylePanel, psymPanel}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

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
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(fillToReferenceCheckBox)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(fillColorPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 24, Short.MAX_VALUE)
                    .add(jLabel7))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                    .add(jLabel8)
                    .add(referenceValuePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 25, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(12, 12, 12)
                .add(moreSeriesPropsButton)
                .addContainerGap())
        );

        jPanel2Layout.linkSize(new java.awt.Component[] {fillColorPanel, jLabel7}, org.jdesktop.layout.GroupLayout.VERTICAL);

        jPanel2Layout.linkSize(new java.awt.Component[] {jLabel10, jLabel3, lineStylePanel}, org.jdesktop.layout.GroupLayout.VERTICAL);

        jPanel2Layout.linkSize(new java.awt.Component[] {colorPanel, jLabel2, jLabel6, jLabel9, psymPanel}, org.jdesktop.layout.GroupLayout.VERTICAL);

        jLabel1.setText("Plot Style");

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("plot"));

        majorTicksCheckBox.setText("major ticks");
        majorTicksCheckBox.setToolTipText("draw grid lines at major ticks\n");

        jLabel11.setText("grid:");

        minorGridCheckBox.setText("minor ticks");
        minorGridCheckBox.setToolTipText("draw grid lines at minor ticks\n");

        gridOverCheckBox.setText("overlay");
        gridOverCheckBox.setToolTipText("draw grid lines on top of data");

        org.jdesktop.layout.GroupLayout jPanel3Layout = new org.jdesktop.layout.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel11)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(gridOverCheckBox)
                    .add(jPanel3Layout.createSequentialGroup()
                        .add(majorTicksCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(minorGridCheckBox)))
                .addContainerGap(42, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(majorTicksCheckBox)
                    .add(jLabel11)
                    .add(minorGridCheckBox))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(gridOverCheckBox)
                .addContainerGap(79, Short.MAX_VALUE))
        );

        org.jdesktop.layout.GroupLayout jPanel4Layout = new org.jdesktop.layout.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel4Layout.createSequentialGroup()
                        .add(jPanel2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                            .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(jPanel3, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .add(jLabel1))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(jPanel4Layout.createSequentialGroup()
                        .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jPanel3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(jPanel2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        jScrollPane1.setViewportView(jPanel4);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 620, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 390, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void moreSprectrogramPropsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moreSprectrogramPropsButtonActionPerformed
        new PropertyEditor(dom.getController().getPanel()).showDialog(this);
    }//GEN-LAST:event_moreSprectrogramPropsButtonActionPerformed

    private void moreSeriesPropsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moreSeriesPropsButtonActionPerformed
        new PropertyEditor(dom.getController().getPanel()).showDialog(this);
    }//GEN-LAST:event_moreSeriesPropsButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel colorPanel;
    private javax.swing.JPanel colortableTypePanel;
    private javax.swing.JPanel fillColorPanel;
    private javax.swing.JCheckBox fillToReferenceCheckBox;
    private javax.swing.JCheckBox gridOverCheckBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JPanel lineStylePanel;
    private javax.swing.JSpinner lineThickSpinner;
    private javax.swing.JCheckBox majorTicksCheckBox;
    private javax.swing.JCheckBox minorGridCheckBox;
    private javax.swing.JButton moreSeriesPropsButton;
    private javax.swing.JButton moreSprectrogramPropsButton;
    private javax.swing.JPanel psymPanel;
    private javax.swing.JPanel rebinPanel;
    private javax.swing.JPanel referenceValuePanel;
    private javax.swing.JSpinner symSizeSpinner;
    // End of variables declaration//GEN-END:variables
}
