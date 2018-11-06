/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.renderer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.logging.Logger;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import org.autoplot.AutoplotUtil;
import org.autoplot.dom.Annotation;
import org.autoplot.dom.Canvas;
import org.autoplot.dom.Column;
import org.autoplot.dom.PlotElementStyle;
import org.autoplot.dom.Row;
import org.das2.components.DatumRangeEditor;
import org.das2.components.propertyeditor.ColorEditor;
import org.das2.components.propertyeditor.EnumerationEditor;
import org.das2.datum.LoggerManager;
import org.das2.graph.AnchorPosition;
import org.das2.graph.AnchorType;
import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;

/**
 * Friendly editor for Annotation objects.
 * @author jbf
 */
public class AnnotationEditorPanel extends javax.swing.JPanel {

    private static final Logger logger= LoggerManager.getLogger("autoplot.gui");
    
    ColorEditor backgroundEditor;
    ColorEditor foregroundEditor;
    ColorEditor textColorEditor;
    EnumerationEditor anchorPositionEditor;
    BindingGroup bindings;
    DatumRangeEditor xrangeEditor, yrangeEditor;
    AnchorType anchorType;
            
    /**
     * Creates new form AnnotationEditorPanel
     */
    public AnnotationEditorPanel() {
        initComponents();
        anchorType= AnchorType.CANVAS;
        anchorPositionEditor= new EnumerationEditor( AnchorPosition.N );
        backgroundEditor= new ColorEditor(Color.WHITE);
        foregroundEditor= new ColorEditor(Color.BLACK);
        textColorEditor= new ColorEditor(Color.BLACK);
        anchorPositionPanel.add(anchorPositionEditor.getCustomEditor());
        backgroundColorPanel.add(backgroundEditor.getSmallEditor());
        foregroundColorPanel.add(foregroundEditor.getSmallEditor());
        textColorPanel.add(textColorEditor.getSmallEditor());
        xrangeEditor= new DatumRangeEditor();
        yrangeEditor= new DatumRangeEditor();
        xrangePanel.add( xrangeEditor.getCustomEditor() );
        yrangePanel.add( yrangeEditor.getCustomEditor() );
        this.validate();
    }

    public AnchorType getAnchorType() {
        return anchorType;
    }

    public void setAnchorType(AnchorType anchorType) {
        AnchorType oldValue= this.anchorType;
        this.anchorType = anchorType;
        
        if ( anchorType==AnchorType.DATA ) {
            anchorToPanel.removeAll();
            anchorToPanel.add( dataControlPanel, BorderLayout.CENTER );
            this.dataAnchorTypeButton.setSelected( true );
        } else if ( anchorType==AnchorType.PLOT ) {
            anchorToPanel.removeAll();
            anchorToPanel.add( plotControlPanel, BorderLayout.CENTER );
            logger.warning("strange plot anchor type is not supported.");
        } else if ( anchorType==AnchorType.CANVAS ) {
            anchorToPanel.removeAll();
            anchorToPanel.add( canvasControlPanel, BorderLayout.CENTER );
            this.canvasAnchorTypeButton.setSelected( true );
        } else {
            return;
        }
        this.validate();
        firePropertyChange( "anchorType", oldValue, anchorType );
    }
    
    
    private void addBinding( BindingGroup bc, Object ann, String srcprop, Object dest, String destprop ) {
        bc.addBinding( Bindings.createAutoBinding( AutoBinding.UpdateStrategy.READ_WRITE, ann, BeanProperty.create( srcprop ), dest, BeanProperty.create(destprop)));
    }
    
    /**
     * bind to this annotation
     * @param ann 
     */
    public void doBindings( final Annotation ann ) {
        if ( bindings!=null ) throw new IllegalArgumentException("already bound");
        
        plotIdComboBox.setModel( new DefaultComboBoxModel<String>( new String[] { ann.getPlotId() } ) );
        
        ArrayList<String> rows= new ArrayList<>();
        rows.add("");
        Canvas c= null;
        if ( ann.getController()!=null ) {
            c= ann.getController().getCanvas();
            rows.add(c.getMarginRow().getId());
            for ( Row r: c.getRows() ) {
                rows.add( r.getId() );
            }
        }
        rowIdComboBox.setModel( new DefaultComboBoxModel<>( rows.toArray( new String[rows.size()] ) ) );
        rowIdComboBox.setSelectedItem( ann.getRowId() );
        
        ArrayList<String> columns= new ArrayList<>();
        columns.add("");
        if ( ann.getController()!=null ) {
            c= ann.getController().getCanvas();
            columns.add(c.getMarginColumn().getId());
            for ( Column c1: c.getColumns() ) {
                columns.add( c1.getId() );
            }
        }
        columnIdComboBox.setModel( new DefaultComboBoxModel<>( columns.toArray( new String[columns.size()] ) ) );
        columnIdComboBox.setSelectedItem( ann.getColumnId() );
                
        BindingGroup bc = new BindingGroup();

        addBinding( bc, ann, Annotation.PROP_TEXT, textField, "text" );
        addBinding( bc, ann, Annotation.PROP_ANCHORPOSITION, anchorPositionEditor, "value" );        
        addBinding( bc, ann, Annotation.PROP_ANCHORTYPE, this, "anchorType" );
        addBinding( bc, ann, Annotation.PROP_OVERRIDECOLORS, customColorsCheckBox, "selected" );
        addBinding( bc, ann, Annotation.PROP_BACKGROUND, backgroundEditor, "value" );
        addBinding( bc, ann, Annotation.PROP_FOREGROUND, foregroundEditor, "value" );
        addBinding( bc, ann, Annotation.PROP_TEXTCOLOR, textColorEditor, "value" );
        addBinding( bc, ann, Annotation.PROP_XRANGE, xrangeEditor, "value" );
        addBinding( bc, ann, Annotation.PROP_YRANGE, yrangeEditor, "value" );
        addBinding( bc, ann, Annotation.PROP_PLOTID, plotIdComboBox, "selectedItem");
        addBinding( bc, ann, Annotation.PROP_ROWID, rowIdComboBox, "selectedItem" );
        addBinding( bc, ann, Annotation.PROP_COLUMNID, columnIdComboBox, "selectedItem" );
        addBinding( bc, ann, Annotation.PROP_ANCHOROFFSET, anchorOffsetTF, "selectedItem" );
        bc.bind();
        
        bindings= bc;
        
        if ( ann.getUrl().trim().length()>0 ) {
            useUrl.setSelected(true);
            textField.setText(ann.getUrl());
        }

        useUrl.addActionListener( new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ( useUrl.isSelected() ) {
                    textField.setText(ann.getUrl());
                } else {
                    textField.setText(ann.getText());
                }
            }
        });
    }
    
    /**
     * remove all the bindings and references to objects.
     */
    public void releaseBindings() {
        if ( bindings!=null ) {
            bindings.unbind();
            bindings= null;
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        buttonGroup1 = new javax.swing.ButtonGroup();
        dataControlPanel = new javax.swing.JPanel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        xrangePanel = new javax.swing.JPanel();
        yrangePanel = new javax.swing.JPanel();
        plotControlPanel = new javax.swing.JPanel();
        jLabel9 = new javax.swing.JLabel();
        plotIdComboBox = new javax.swing.JComboBox<>();
        canvasControlPanel = new javax.swing.JPanel();
        jLabel10 = new javax.swing.JLabel();
        rowIdComboBox = new javax.swing.JComboBox<>();
        jLabel11 = new javax.swing.JLabel();
        columnIdComboBox = new javax.swing.JComboBox<>();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        anchorPositionPanel = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        canvasAnchorTypeButton = new javax.swing.JRadioButton();
        dataAnchorTypeButton = new javax.swing.JRadioButton();
        customColorsCheckBox = new javax.swing.JCheckBox();
        anchorToPanel = new javax.swing.JPanel();
        customColorsPanel = new javax.swing.JPanel();
        textColorPanel = new javax.swing.JPanel();
        backgroundColorPanel = new javax.swing.JPanel();
        jLabel5 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        foregroundColorPanel = new javax.swing.JPanel();
        textField = new javax.swing.JTextField();
        jLabel13 = new javax.swing.JLabel();
        anchorOffsetTF = new javax.swing.JComboBox<>();
        useUrl = new javax.swing.JCheckBox();

        jLabel6.setText("x:");

        jLabel7.setText("Two ranges define a box in data space.");

        jLabel8.setText("y:");

        xrangePanel.setLayout(new java.awt.BorderLayout());

        yrangePanel.setLayout(new java.awt.BorderLayout());

        javax.swing.GroupLayout dataControlPanelLayout = new javax.swing.GroupLayout(dataControlPanel);
        dataControlPanel.setLayout(dataControlPanelLayout);
        dataControlPanelLayout.setHorizontalGroup(
            dataControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dataControlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(dataControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(dataControlPanelLayout.createSequentialGroup()
                        .addComponent(jLabel6)
                        .addGap(3, 3, 3)
                        .addComponent(xrangePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jLabel8)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(yrangePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(6, 6, 6))
                    .addComponent(jLabel7)))
        );
        dataControlPanelLayout.setVerticalGroup(
            dataControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, dataControlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel7)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(dataControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(dataControlPanelLayout.createSequentialGroup()
                        .addGroup(dataControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel6)
                            .addComponent(jLabel8))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(yrangePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(xrangePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        jLabel9.setText("Plot containing annotation:");

        plotIdComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        javax.swing.GroupLayout plotControlPanelLayout = new javax.swing.GroupLayout(plotControlPanel);
        plotControlPanel.setLayout(plotControlPanelLayout);
        plotControlPanelLayout.setHorizontalGroup(
            plotControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(plotControlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel9)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(plotIdComboBox, 0, 128, Short.MAX_VALUE)
                .addContainerGap())
        );
        plotControlPanelLayout.setVerticalGroup(
            plotControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(plotControlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(plotControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel9)
                    .addComponent(plotIdComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jLabel10.setText("Row:");

        rowIdComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jLabel11.setText("Column:");

        columnIdComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        javax.swing.GroupLayout canvasControlPanelLayout = new javax.swing.GroupLayout(canvasControlPanel);
        canvasControlPanel.setLayout(canvasControlPanelLayout);
        canvasControlPanelLayout.setHorizontalGroup(
            canvasControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(canvasControlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel10)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(rowIdComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel11)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(columnIdComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(12, Short.MAX_VALUE))
        );

        canvasControlPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {columnIdComboBox, rowIdComboBox});

        canvasControlPanelLayout.setVerticalGroup(
            canvasControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(canvasControlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(canvasControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel10)
                    .addComponent(rowIdComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel11)
                    .addComponent(columnIdComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jLabel1.setText("Annotation Text:");

        jLabel2.setText("Position:");

        anchorPositionPanel.setLayout(new java.awt.BorderLayout());

        jLabel3.setText("Anchor To:");

        buttonGroup1.add(canvasAnchorTypeButton);
        canvasAnchorTypeButton.setSelected(true);
        canvasAnchorTypeButton.setText("Canvas");
        canvasAnchorTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                canvasAnchorTypeButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(canvasAnchorTypeButton)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(canvasAnchorTypeButton))
        );

        buttonGroup1.add(dataAnchorTypeButton);
        dataAnchorTypeButton.setText("Data");
        dataAnchorTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataAnchorTypeButtonActionPerformed(evt);
            }
        });

        customColorsCheckBox.setText("Custom Colors");

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, customColorsPanel, org.jdesktop.beansbinding.ELProperty.create("${enabled}"), customColorsCheckBox, org.jdesktop.beansbinding.BeanProperty.create("selected"));
        bindingGroup.addBinding(binding);

        anchorToPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        anchorToPanel.setLayout(new java.awt.BorderLayout());

        textColorPanel.setLayout(new java.awt.BorderLayout());

        backgroundColorPanel.setLayout(new java.awt.BorderLayout());

        jLabel5.setText("Background:");

        jLabel4.setText("Text Color:");

        jLabel12.setText("Foreground:");

        foregroundColorPanel.setLayout(new java.awt.BorderLayout());

        javax.swing.GroupLayout customColorsPanelLayout = new javax.swing.GroupLayout(customColorsPanel);
        customColorsPanel.setLayout(customColorsPanelLayout);
        customColorsPanelLayout.setHorizontalGroup(
            customColorsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(customColorsPanelLayout.createSequentialGroup()
                .addGroup(customColorsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel5)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(customColorsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(backgroundColorPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(textColorPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
            .addGroup(customColorsPanelLayout.createSequentialGroup()
                .addComponent(jLabel12)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(foregroundColorPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        customColorsPanelLayout.setVerticalGroup(
            customColorsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(customColorsPanelLayout.createSequentialGroup()
                .addGroup(customColorsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(textColorPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(customColorsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel12, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(foregroundColorPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(customColorsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(backgroundColorPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 12, Short.MAX_VALUE))
        );

        textField.setText("jTextField1");

        jLabel13.setText("Anchor Offset:");
        jLabel13.setToolTipText("<html>The offset from the anchor position, in ems or pixels. <br>The offset direction depends on the anchor position. <br>For example, if the anchor is \"N\" then offsets move towards <br>the south and east.");

        anchorOffsetTF.setEditable(true);
        anchorOffsetTF.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "0em,0em", "1em,1em", "10px,10px", " " }));

        useUrl.setText("use image URL instead");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(anchorPositionPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(textField, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(anchorToPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(dataAnchorTypeButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(22, 22, 22)
                        .addComponent(customColorsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(customColorsCheckBox)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel13)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(anchorOffsetTF, javax.swing.GroupLayout.PREFERRED_SIZE, 203, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 90, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(useUrl)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(useUrl))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(textField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(dataAnchorTypeButton))
                    .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(anchorToPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 34, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(anchorPositionPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel13)
                    .addComponent(anchorOffsetTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(customColorsCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(customColorsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(77, 77, 77))
        );

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

    private void dataAnchorTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataAnchorTypeButtonActionPerformed
        if ( dataAnchorTypeButton.isSelected() ) {
            setAnchorType(AnchorType.DATA);
        }
    }//GEN-LAST:event_dataAnchorTypeButtonActionPerformed

    private void canvasAnchorTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_canvasAnchorTypeButtonActionPerformed
        if ( canvasAnchorTypeButton.isSelected() ) {
            setAnchorType(AnchorType.CANVAS);
        }
    }//GEN-LAST:event_canvasAnchorTypeButtonActionPerformed

    public static void main( String[] args ) {
        AnnotationEditorPanel p= new AnnotationEditorPanel();
        Annotation a= new Annotation();
        p.doBindings(a);
        AutoplotUtil.showMessageDialog( null, p, "tester", JOptionPane.OK_OPTION );
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox<String> anchorOffsetTF;
    private javax.swing.JPanel anchorPositionPanel;
    private javax.swing.JPanel anchorToPanel;
    private javax.swing.JPanel backgroundColorPanel;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JRadioButton canvasAnchorTypeButton;
    private javax.swing.JPanel canvasControlPanel;
    private javax.swing.JComboBox<String> columnIdComboBox;
    private javax.swing.JCheckBox customColorsCheckBox;
    private javax.swing.JPanel customColorsPanel;
    private javax.swing.JRadioButton dataAnchorTypeButton;
    private javax.swing.JPanel dataControlPanel;
    private javax.swing.JPanel foregroundColorPanel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel plotControlPanel;
    private javax.swing.JComboBox<String> plotIdComboBox;
    private javax.swing.JComboBox<String> rowIdComboBox;
    private javax.swing.JPanel textColorPanel;
    private javax.swing.JTextField textField;
    private javax.swing.JCheckBox useUrl;
    private javax.swing.JPanel xrangePanel;
    private javax.swing.JPanel yrangePanel;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables
}
