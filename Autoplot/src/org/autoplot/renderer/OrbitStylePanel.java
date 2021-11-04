
package org.autoplot.renderer;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ItemEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JOptionPane;
import org.das2.graph.Renderer;
import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.autoplot.PlotStylePanel;
import org.autoplot.dom.PlotElement;
import org.autoplot.dom.PlotElementStyle;
import org.das2.components.propertyeditor.TickValuesStringSchemeEditor;
import org.das2.graph.TickCurveRenderer;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;

/**
 * Style panel for orbit renderer
 * @author jbf
 */
public final class OrbitStylePanel extends javax.swing.JPanel implements PlotStylePanel.StylePanel {

    /**
     * Creates new form DigitalStylePanel
     */
    public OrbitStylePanel() {
        initComponents();
        //note the colorPanel must have its layout set to BorderLayout.
        colorEditor.setValue(Color.BLACK);
        colorPanel.add( colorEditor.getSmallEditor() );
        colorEditor.addPropertyChangeListener( new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                update();
            }
        });
        tickDirectionComboBox.setModel( new DefaultComboBoxModel<>( new String[] { "", "outside", "right", "left" } ) );
        validate();
    }

    Renderer renderer;
    BindingGroup elementBindingContext;
        
    private String control = "";

    public static final String PROP_CONTROL = "control";

    public String getControl() {
        return control;
    }


    /**
     * set the control string
     * @param control the control string, e.g. "color=red&lineThick=0.2"
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
        controls.put( Renderer.CONTROL_KEY_FONT_SIZE, fontSizeTF.getText() );
        //controls.put( "format", formatTF.getText() );
        controls.put( Renderer.CONTROL_KEY_COLOR, Renderer.encodeColorControl( (Color)colorEditor.getValue() ) );
        //controls.put( "fillLabel", fillLabelTF.getText() );
        controls.put( Renderer.CONTROL_KEY_LINE_THICK, thickTextField.getText() );
        controls.put( "tickLength", tickLengthTextField.getText() );
        controls.put( "tickValues", tickValuesTF.getText() );
        controls.put( "tickDirection", tickDirectionComboBox.getSelectedItem().toString() );
        String s= tickStyleComboBox.getSelectedItem().toString();
        if ( s.equals("oneSided") ) {
            s= "outside";
        }
        controls.put( "tickStyle", s );
        String c= Renderer.formatControl(controls);
        this.control= c;
        firePropertyChange( Renderer.PROP_CONTROL, oldValue, c );
    }
    private void updateGUI( Renderer renderer ) {
        this.control= renderer.getControl();
        fontSizeTF.setText( renderer.getControl( Renderer.CONTROL_KEY_FONT_SIZE, fontSizeTF.getText() ) ); 
        colorEditor.setValue( renderer.getColorControl( Renderer.CONTROL_KEY_COLOR, (Color)colorEditor.getValue() ) );
        thickTextField.setText( renderer.getControl( Renderer.CONTROL_KEY_LINE_THICK, thickTextField.getText() ) );
        tickLengthTextField.setText( renderer.getControl( "tickLength", tickLengthTextField.getText() ) );
        tickValuesTF.setText( renderer.getControl( "tickValues", tickValuesTF.getText() ) );
        tickDirectionComboBox.setSelectedItem( renderer.getControl( "tickDirection", tickDirectionComboBox.getSelectedItem().toString() ) );
        if ( renderer instanceof TickCurveRenderer ) {
            TickCurveRenderer tcr= (TickCurveRenderer)renderer;
            if ( tcr.getTickStyle()==TickCurveRenderer.TickStyle.BOTH ) {
                tickStyleComboBox.setSelectedItem("both");
            } else {
                tickStyleComboBox.setSelectedItem("oneSided");
            }
        }
    }
    
    @Override
    public void doElementBindings(PlotElement element) {
        this.renderer= element.getController().getRenderer();
        updateGUI( renderer );
        
        BindingGroup bc = new BindingGroup();
        PlotElementStyle style= element.getStyle();
        
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, style, BeanProperty.create( "color" ), colorEditor, BeanProperty.create("value")));

        bc.addBinding( Bindings.createAutoBinding( AutoBinding.UpdateStrategy.READ_WRITE, 
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

        colorEditor = new org.das2.components.propertyeditor.ColorEditor();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        colorPanel = new javax.swing.JPanel();
        fontSizeTF = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        thickTextField = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        tickLengthTextField = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        tickDirectionComboBox = new javax.swing.JComboBox<>();
        jLabel7 = new javax.swing.JLabel();
        tickStyleComboBox = new javax.swing.JComboBox<>();
        jLabel8 = new javax.swing.JLabel();
        tickValuesTF = new javax.swing.JTextField();
        tickValuesButton = new javax.swing.JButton();

        setBorder(javax.swing.BorderFactory.createTitledBorder("Orbit Plot"));

        jLabel1.setText("Font Size:");

        jLabel2.setText("Color:");

        colorPanel.setLayout(new java.awt.BorderLayout());

        fontSizeTF.setToolTipText("Font size, relative to canvas font size.  For example \"0.5em\" is half of the font size. \"7pt\" is 7 points.");
        fontSizeTF.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                OrbitStylePanel.this.focusLost(evt);
            }
        });
        fontSizeTF.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fontSizeTFActionPerformed(evt);
            }
        });

        jLabel6.setText("Thick:");
        jLabel6.setToolTipText("Line thickness");

        thickTextField.setToolTipText("Line thickness in pixels/points");
        thickTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                thickTextFieldFocusLost(evt);
            }
        });
        thickTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                thickTextFieldActionPerformed(evt);
            }
        });

        jLabel3.setText("Tick Length:");

        tickLengthTextField.setToolTipText("Tick Length relative to the font size, so .66em is 2/3 of the font size.");
        tickLengthTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                tickLengthTextFieldFocusLost(evt);
            }
        });
        tickLengthTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tickLengthTextFieldActionPerformed(evt);
            }
        });

        jLabel5.setText("Tick Direction:");
        jLabel5.setToolTipText("direction of the ticks, where left is to the left along the data, and outside is to the outside of bends.");

        tickDirectionComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "default", "outside", "left", "right" }));
        tickDirectionComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                tickDirectionComboBoxItemStateChanged(evt);
            }
        });
        tickDirectionComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tickDirectionComboBoxActionPerformed(evt);
            }
        });

        jLabel7.setText("Tick Style:");

        tickStyleComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "oneSided", "both" }));
        tickStyleComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                tickStyleComboBoxItemStateChanged(evt);
            }
        });
        tickStyleComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tickStyleComboBoxActionPerformed(evt);
            }
        });

        jLabel8.setText("Tick Values:");

        tickValuesTF.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                tickValuesTFFocusLost(evt);
            }
        });
        tickValuesTF.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tickValuesTFActionPerformed(evt);
            }
        });

        tickValuesButton.setText("...");
        tickValuesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tickValuesButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel1)
                                .addGap(18, 20, Short.MAX_VALUE)
                                .addComponent(fontSizeTF, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jLabel6, javax.swing.GroupLayout.Alignment.LEADING))
                        .addGap(112, 112, 112))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                .addComponent(colorPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGroup(layout.createSequentialGroup()
                                    .addComponent(jLabel2)
                                    .addGap(134, 134, 134)))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel3)
                                .addGap(5, 5, 5)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(thickTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(tickLengthTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel5)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(tickDirectionComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 137, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel7)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(tickStyleComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel8)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(tickValuesTF, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(tickValuesButton, javax.swing.GroupLayout.PREFERRED_SIZE, 1, Short.MAX_VALUE)))
                        .addGap(0, 0, Short.MAX_VALUE))))
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {fontSizeTF, thickTextField, tickLengthTextField});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jLabel2))
                    .addComponent(colorPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(fontSizeTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(thickTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(tickLengthTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tickValuesTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tickValuesButton, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel8))
                .addGap(4, 4, 4)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(tickDirectionComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(tickStyleComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void fontSizeTFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fontSizeTFActionPerformed
        update();
    }//GEN-LAST:event_fontSizeTFActionPerformed

    private void focusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_focusLost
        update();
    }//GEN-LAST:event_focusLost

    private void thickTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_thickTextFieldActionPerformed
        update();
    }//GEN-LAST:event_thickTextFieldActionPerformed

    private void thickTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_thickTextFieldFocusLost
        update();
    }//GEN-LAST:event_thickTextFieldFocusLost

    private void tickLengthTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tickLengthTextFieldActionPerformed
        update();
    }//GEN-LAST:event_tickLengthTextFieldActionPerformed

    private void tickLengthTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_tickLengthTextFieldFocusLost
        update();
    }//GEN-LAST:event_tickLengthTextFieldFocusLost

    private void tickDirectionComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tickDirectionComboBoxActionPerformed
        update();
    }//GEN-LAST:event_tickDirectionComboBoxActionPerformed

    private void tickDirectionComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_tickDirectionComboBoxItemStateChanged
        if ( evt.getStateChange()==ItemEvent.SELECTED ) {
            update();
        }
    }//GEN-LAST:event_tickDirectionComboBoxItemStateChanged

    private void tickStyleComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tickStyleComboBoxActionPerformed
        update();
    }//GEN-LAST:event_tickStyleComboBoxActionPerformed

    private void tickStyleComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_tickStyleComboBoxItemStateChanged
        if ( evt.getStateChange()==ItemEvent.SELECTED ) {
            update();
        }
    }//GEN-LAST:event_tickStyleComboBoxItemStateChanged

    private void tickValuesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tickValuesButtonActionPerformed
        TickValuesStringSchemeEditor edit= new TickValuesStringSchemeEditor();
        edit.setValue( tickValuesTF.getText() );
        if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog( this, edit, 
                TickValuesStringSchemeEditor.EDITOR_TITLE, JOptionPane.PLAIN_MESSAGE ) ) {
            tickValuesTF.setText( edit.getValue() );
        }
    }//GEN-LAST:event_tickValuesButtonActionPerformed

    private void tickValuesTFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tickValuesTFActionPerformed
        update();
    }//GEN-LAST:event_tickValuesTFActionPerformed

    private void tickValuesTFFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_tickValuesTFFocusLost
        update();
    }//GEN-LAST:event_tickValuesTFFocusLost


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.das2.components.propertyeditor.ColorEditor colorEditor;
    private javax.swing.JPanel colorPanel;
    private javax.swing.JTextField fontSizeTF;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JTextField thickTextField;
    private javax.swing.JComboBox<String> tickDirectionComboBox;
    private javax.swing.JTextField tickLengthTextField;
    private javax.swing.JComboBox<String> tickStyleComboBox;
    private javax.swing.JButton tickValuesButton;
    private javax.swing.JTextField tickValuesTF;
    // End of variables declaration//GEN-END:variables

}
