/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot;

import java.awt.BorderLayout;
import java.awt.Color;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.graph.AnchorType;
import org.autoplot.dom.Annotation;
import org.autoplot.jythonsupport.ui.JLinkyLabel;
import org.das2.components.propertyeditor.EnumerationEditor;
import org.das2.graph.AnchorPosition;
import org.das2.graph.BorderType;
import org.das2.graph.GraphUtil;
import org.das2.components.GrannyTextEditor;

/**
 *
 * @author jbf
 */
public class AddAnnotationDialog extends javax.swing.JPanel {

    private static Logger logger= Logger.getLogger("autoplot.gui");
     
    EnumerationEditor anchorPositionEnumEditor;
    
    /**
     * Creates new form AddAnnotationDialog
     */
    public AddAnnotationDialog() {
        initComponents();
        
        anchorPositionEnumEditor = new EnumerationEditor();
        anchorPositionEnumEditor.setValue( AnchorPosition.NE );
        anchorPositionPanel.setLayout( new BorderLayout() );
        anchorPositionPanel.add(anchorPositionEnumEditor.getCustomEditor(), BorderLayout.CENTER);

        borderTypeEnumEditor.setValue( BorderType.ROUNDED_RECTANGLE );
        borderTypePanel.add( borderTypeEnumEditor.getCustomEditor() );
             
        JLinkyLabel ll= new JLinkyLabel( null,
                "<html>This <a href='https://github.com/autoplot/documentation/blob/master/docs/annotations.md'>web page</a> "
                        + "shows how the annotations are controlled.");
        
        linkyLabelPanel.add( ll, BorderLayout.CENTER );
        
        validate();
        
    }

    public String getText() {
        return jTextField1.getText();
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

        borderTypeEnumEditor = new org.das2.components.propertyeditor.EnumerationEditor();
        jTextField1 = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        pointAtCB = new javax.swing.JCheckBox();
        xDatumField = new javax.swing.JTextField();
        yDatumField = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        anchorPositionPanel = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        borderTypePanel = new javax.swing.JPanel();
        linkyLabelPanel = new javax.swing.JPanel();
        jButton1 = new javax.swing.JButton();
        previewPanel = new javax.swing.JPanel();

        jTextField1.setText("Annotation 1");

        jLabel2.setText("Annotation Text: ");

        pointAtCB.setText("Point At:");
        pointAtCB.setToolTipText("Point at this data location, and annotation will move with data.");

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, pointAtCB, org.jdesktop.beansbinding.ELProperty.create("${selected}"), xDatumField, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, pointAtCB, org.jdesktop.beansbinding.ELProperty.create("${selected}"), yDatumField, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        jLabel1.setText("Position:");
        jLabel1.setToolTipText("Position of anchor relative to corner or data point");

        javax.swing.GroupLayout anchorPositionPanelLayout = new javax.swing.GroupLayout(anchorPositionPanel);
        anchorPositionPanel.setLayout(anchorPositionPanelLayout);
        anchorPositionPanelLayout.setHorizontalGroup(
            anchorPositionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        anchorPositionPanelLayout.setVerticalGroup(
            anchorPositionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 15, Short.MAX_VALUE)
        );

        jLabel3.setText("Border Type:");

        borderTypePanel.setLayout(new java.awt.BorderLayout());

        linkyLabelPanel.setToolTipText("");
        linkyLabelPanel.setLayout(new java.awt.BorderLayout());

        jButton1.setText("...");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout previewPanelLayout = new javax.swing.GroupLayout(previewPanel);
        previewPanel.setLayout(previewPanelLayout);
        previewPanelLayout.setHorizontalGroup(
            previewPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        previewPanelLayout.setVerticalGroup(
            previewPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 132, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(previewPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(linkyLabelPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jTextField1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(borderTypePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(pointAtCB)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(xDatumField, javax.swing.GroupLayout.PREFERRED_SIZE, 148, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(yDatumField, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(anchorPositionPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                        .addGap(0, 214, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(pointAtCB)
                    .addComponent(xDatumField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(yDatumField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(anchorPositionPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(borderTypePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addComponent(previewPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(linkyLabelPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        GrannyTextEditor edit= AutoplotUI.newGrannyTextEditorWithMacros();
        edit.setValue( jTextField1.getText() );
        if ( JOptionPane.OK_OPTION==
                JOptionPane.showConfirmDialog( this, edit, GrannyTextEditor.EDITOR_TITLE, JOptionPane.OK_CANCEL_OPTION ) ) {
            jTextField1.setText( edit.getValue() );
        }
    }//GEN-LAST:event_jButton1ActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel anchorPositionPanel;
    private org.das2.components.propertyeditor.EnumerationEditor borderTypeEnumEditor;
    private javax.swing.JPanel borderTypePanel;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JPanel linkyLabelPanel;
    private javax.swing.JCheckBox pointAtCB;
    private javax.swing.JPanel previewPanel;
    private javax.swing.JTextField xDatumField;
    private javax.swing.JTextField yDatumField;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables

    /**
     * make the settings of the Annotation consistent with the settings of this dialog.
     * @param ann 
     */
    void configure(Annotation ann) {
        ann.setText(jTextField1.getText());
        ann.setAnchorType( pointAtCB.isSelected() ? AnchorType.PLOT : AnchorType.CANVAS );
        ann.setAnchorPosition((AnchorPosition) anchorPositionEnumEditor.getValue());
        ann.setBorderType((BorderType) borderTypeEnumEditor.getValue());
        if ( ann.getBorderType()==BorderType.NONE ) {
            ann.setBackground( new Color( 0, 0, 0, 0  ) );
            ann.setOverrideColors(true);
        } 
        if ( pointAtCB.isSelected() ) {
            try {
                Datum x= this.x.getUnits().parse( xDatumField.getText()) ;
                ann.setPointAtX( x );
                ann.setXrange( new DatumRange( x, x ) );
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            try {
                Datum y= this.y.getUnits().parse( yDatumField.getText() );
                ann.setPointAtY( y );
                ann.setYrange( new DatumRange( y, y ) );
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            ann.setShowArrow(true);                    
        }
    }

    Datum x=null;
    Datum y=null;
    
    void setPointAtX(Datum invTransform) {
        this.xDatumField.setText( invTransform.toString() );
        this.x= invTransform;
        pointAtCB.setEnabled(true);
        if ( this.y!=null ) {
            pointAtCB.setEnabled(true);
        }
    }

    void setPointAtY(Datum invTransform) {
        this.yDatumField.setText( invTransform.toString() );
        this.y= invTransform;
        if ( this.x!=null ) {
            pointAtCB.setEnabled(true);
        }
    }
}
