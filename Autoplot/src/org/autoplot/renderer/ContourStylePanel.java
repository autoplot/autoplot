/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.renderer;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.DefaultComboBoxModel;
import org.das2.graph.Renderer;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.autoplot.PlotStylePanel;
import org.autoplot.dom.PlotElement;
import org.das2.graph.ContoursRenderer;

/**
 * GUI for controlling a ContoursRenderer.
 * @author faden@cottagesystems.com
 */
public class ContourStylePanel extends javax.swing.JPanel implements PlotStylePanel.StylePanel {

    /**
     * Creates new form ContourStylePanel
     */
    public ContourStylePanel() {
        initComponents();
        colorEditor1.setValue(Color.BLACK);
        colorPanel.add( colorEditor1.getSmallEditor() );
        colorEditor1.addPropertyChangeListener( new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                update();
            }
        });
        formatComboBox.setModel(new DefaultComboBoxModel<>( new String[] { "","%.1f",".2f","%d","%.1e" } ));
        labelOrientationComboBox.setModel(new DefaultComboBoxModel<>( new String[] { "","N" } ));
    }

    BindingGroup elementBindingContext;
    Renderer renderer;
    
    private String control = "";

    public static final String PROP_CONTROL = "control";

    public String getControl() {
        return control;
    }

    /**
     * set the control string
     * @param control the control string, e.g. "levels=3,4,5&color=red"
     */
    public void setControl(String control) {
        String oldControl = this.control;
        this.control = control;
        this.renderer.setControl(control);
        updateGUI(renderer);
        firePropertyChange(PROP_CONTROL, oldControl, control);
    }

    private void update() {
        String oldValue= this.control;
        Map<String,String> controls= new LinkedHashMap();
        controls.put( ContoursRenderer.CONTROL_KEY_LEVELS, levelsTextField.getText() );
        controls.put( ContoursRenderer.CONTROL_KEY_LABELS, Renderer.encodeBooleanControl( drawLabelsCheckBox.isSelected() ) );
        controls.put( Renderer.CONTROL_KEY_LINE_THICK, String.format("%.1f",lineThickSpinner.getValue()) );
        controls.put( ContoursRenderer.CONTROL_KEY_LABEL_CADENCE, labelCadenceComboBox.getSelectedItem().toString() );
        controls.put( Renderer.CONTROL_KEY_COLOR, Renderer.encodeColorControl( (Color)colorEditor1.getValue() ) );
        controls.put( ContoursRenderer.CONTROL_KEY_FORMAT, formatComboBox.getSelectedItem().toString() );
        controls.put( Renderer.CONTROL_KEY_FONT_SIZE, fontSizeComboBox.getSelectedItem().toString() );
        controls.put( ContoursRenderer.CONTROL_KEY_LABEL_ORIENT, labelOrientationComboBox.getSelectedItem().toString() );
        String c= Renderer.formatControl(controls);
        this.control= c;
        firePropertyChange( Renderer.PROP_CONTROL, oldValue, c );
    }
    
    private void updateGUI( Renderer renderer ) {
        if ( renderer==null ) {
            System.err.println("renderer was null");
            return;
        }
        this.control= renderer.getControl();
        levelsTextField.setText( renderer.getControl( ContoursRenderer.CONTROL_KEY_LEVELS, "0." ) );
        drawLabelsCheckBox.setSelected( renderer.getBooleanControl( ContoursRenderer.CONTROL_KEY_LABELS, false ) );
        lineThickSpinner.setValue( renderer.getDoubleControl( Renderer.CONTROL_KEY_LINE_THICK, 1.0 ) );
        labelCadenceComboBox.setSelectedItem( renderer.getControl( ContoursRenderer.CONTROL_KEY_LABEL_CADENCE, "100px") );    
        colorEditor1.setValue( renderer.getColorControl( Renderer.CONTROL_KEY_COLOR, Color.BLACK ) );
        formatComboBox.setSelectedItem( renderer.getControl( ContoursRenderer.CONTROL_KEY_FORMAT, "" ) );
        fontSizeComboBox.setSelectedItem( renderer.getControl( Renderer.CONTROL_KEY_FONT_SIZE, "" ) );
        labelOrientationComboBox.setSelectedItem( renderer.getControl( ContoursRenderer.CONTROL_KEY_LABEL_ORIENT, "" ));
    }
    

    @Override
    public void doElementBindings(PlotElement element) {
        this.renderer= element.getController().getRenderer();
        updateGUI( renderer );
        
        BindingGroup bc = new BindingGroup();

        bc.addBinding( Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, 
                element, BeanProperty.create(  PlotElement.PROP_RENDERCONTROL ), 
                this, BeanProperty.create( Renderer.PROP_CONTROL ) ) );
        
        if ( elementBindingContext!=null ) {
            releaseElementBindings();
        }
        
        bc.bind();
        
        repaint();
        
        elementBindingContext= bc;

    }

    @Override
    public void releaseElementBindings() {
        if ( elementBindingContext!=null ) {
            elementBindingContext.unbind();
            elementBindingContext= null;
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

        colorEditor1 = new org.das2.components.propertyeditor.ColorEditor();
        jPanel1 = new javax.swing.JPanel();
        drawLabelsCheckBox = new javax.swing.JCheckBox();
        jLabel6 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        lineThickSpinner = new javax.swing.JSpinner();
        levelsTextField = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        colorPanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        formatComboBox = new javax.swing.JComboBox<>();
        jLabel5 = new javax.swing.JLabel();
        fontSizeComboBox = new javax.swing.JComboBox<>();
        jLabel7 = new javax.swing.JLabel();
        labelCadenceComboBox = new javax.swing.JComboBox<>();
        jLabel8 = new javax.swing.JLabel();
        labelOrientationComboBox = new javax.swing.JComboBox<>();

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Contour"));

        drawLabelsCheckBox.setText("Draw Labels");
        drawLabelsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                drawLabelsCheckBoxActionPerformed(evt);
            }
        });

        jLabel6.setText("Color:");
        jLabel6.setToolTipText("color of the line and plot symbols");

        jLabel2.setText("Thick:");

        lineThickSpinner.setModel(new javax.swing.SpinnerNumberModel(1.0d, 0.0d, 10.0d, 0.1d));
        lineThickSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                lineThickSpinnerStateChanged(evt);
            }
        });

        levelsTextField.setText("0");
        levelsTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                levelsTextFieldFocusLost(evt);
            }
        });
        levelsTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                levelsTextFieldActionPerformed(evt);
            }
        });

        jLabel3.setFont(jLabel3.getFont().deriveFont((jLabel3.getFont().getStyle() | java.awt.Font.ITALIC), jLabel3.getFont().getSize()-2));
        jLabel3.setText("a comma-separated list of trace locations:");

        colorPanel.setLayout(new java.awt.BorderLayout());

        jLabel1.setText("Levels:");

        jLabel4.setText("Format:");

        formatComboBox.setEditable(true);
        formatComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "%d", "%.1f", "%.2f", "%e" }));
        formatComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                formatComboBoxItemStateChanged(evt);
            }
        });

        jLabel5.setText("Font Size:");

        fontSizeComboBox.setEditable(true);
        fontSizeComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "8pt", "12pt", ".8em", "1.2em" }));
        fontSizeComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                fontSizeComboBoxItemStateChanged(evt);
            }
        });

        jLabel7.setText("Label Cadence:");

        labelCadenceComboBox.setEditable(true);
        labelCadenceComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "100px", "15em" }));
        labelCadenceComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                labelCadenceComboBoxItemStateChanged(evt);
            }
        });

        jLabel8.setText("Label Orientation:");

        labelOrientationComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "\"\"", "up" }));
        labelOrientationComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                labelOrientationComboBoxItemStateChanged(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(12, 12, 12)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel4)
                                    .addComponent(jLabel5))
                                .addGap(47, 47, 47)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(formatComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addComponent(fontSizeComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addContainerGap(35, Short.MAX_VALUE))))
                            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                                    .addComponent(jLabel8)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(labelOrientationComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                                    .addComponent(jLabel7)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                    .addComponent(labelCadenceComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addComponent(jLabel2)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(lineThickSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 61, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addComponent(jLabel6)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(colorPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 244, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(drawLabelsCheckBox))
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addComponent(levelsTextField)
                            .addComponent(jLabel3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addContainerGap())))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(levelsTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(lineThickSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel6, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(colorPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(drawLabelsCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(formatComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(4, 4, 4)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(fontSizeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(labelCadenceComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(labelOrientationComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(37, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void drawLabelsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_drawLabelsCheckBoxActionPerformed
        update();
    }//GEN-LAST:event_drawLabelsCheckBoxActionPerformed

    private void lineThickSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_lineThickSpinnerStateChanged
        update();
    }//GEN-LAST:event_lineThickSpinnerStateChanged

    private void levelsTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_levelsTextFieldActionPerformed
        update();
    }//GEN-LAST:event_levelsTextFieldActionPerformed

    private void levelsTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_levelsTextFieldFocusLost
        update();
    }//GEN-LAST:event_levelsTextFieldFocusLost

    private void formatComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_formatComboBoxItemStateChanged
        update();
    }//GEN-LAST:event_formatComboBoxItemStateChanged

    private void fontSizeComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_fontSizeComboBoxItemStateChanged
        update();
    }//GEN-LAST:event_fontSizeComboBoxItemStateChanged

    private void labelCadenceComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_labelCadenceComboBoxItemStateChanged
        update();
    }//GEN-LAST:event_labelCadenceComboBoxItemStateChanged

    private void labelOrientationComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_labelOrientationComboBoxItemStateChanged
        update();
    }//GEN-LAST:event_labelOrientationComboBoxItemStateChanged


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.das2.components.propertyeditor.ColorEditor colorEditor1;
    private javax.swing.JPanel colorPanel;
    private javax.swing.JCheckBox drawLabelsCheckBox;
    private javax.swing.JComboBox<String> fontSizeComboBox;
    private javax.swing.JComboBox<String> formatComboBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JComboBox<String> labelCadenceComboBox;
    private javax.swing.JComboBox<String> labelOrientationComboBox;
    private javax.swing.JTextField levelsTextField;
    private javax.swing.JSpinner lineThickSpinner;
    // End of variables declaration//GEN-END:variables
}
