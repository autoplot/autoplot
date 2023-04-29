
package org.autoplot.jythonsupport.ui;

import java.awt.BorderLayout;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.swing.JOptionPane;
import org.autoplot.jythonsupport.JythonUtil;
import org.autoplot.jythonsupport.Param;
import org.python.parser.ast.Dict;

/**
 *
 * @author jbf
 */
public class GetParamTool extends javax.swing.JPanel {

    /**
     * Creates new form GetParamTool
     */
    public GetParamTool() {
        initComponents();
        constraintsPanel.setLayout( new BorderLayout() );
    }
    
    private String maybeAddQuotes( String s ) {
        if ( !s.startsWith("'") ) {
            if ( !s.endsWith("'") ) {
                return "'"+s+"'";
            } else {
                return "'"+s;
            }
        } else if ( s.endsWith("'") ) {
            return "'"+s;
        } else {
            return s;
        }
    }
    
    private String maybeRemoveQuotes( String s ) {
        if ( s.startsWith("'") || s.startsWith("\"") ) { 
            if ( s.endsWith("'") || s.endsWith("\"") ) {
                return s.substring(1,s.length()-1);
            } else {
                return s.substring(1);
            }
        } else if ( s.endsWith("'") || s.endsWith("\"") ) {
            return s.substring(0,s.length()-1);
        } else {
            return s;
        }
    }
    
    /**
     * return the command implementing this GetParam
     * @return 
     */
    public String getValue() {
        StringBuilder sb= new StringBuilder("getParam(");
        sb.append(parameterNameTextField.getText());
        sb.append(",");
        sb.append(defaultValueTextField.getText());
        if ( noneRadioButton.isSelected() ) {
            String descript= descriptionTextField.getText().trim();
            if ( descript.length()>0 ) {
                sb.append(",");
                sb.append(maybeAddQuotes(descript));
            }
        } else {
            String descript= descriptionTextField.getText().trim();
            sb.append(",");
            sb.append(maybeAddQuotes(descript));
            sb.append(",");
            if ( moreButton.isSelected() ) {
                StringBuilder constraints= new StringBuilder("{");
                if ( minCheckBox.isSelected() ) {
                    constraints.append("\"min\":");
                    constraints.append(minTextField.getText());
                    constraints.append(",");
                }
                if ( maxCheckBox.isSelected() ) {
                    constraints.append("\"max\":");
                    constraints.append(maxTextField.getText());
                    constraints.append(",");
                }
                if ( examplesCheckBox.isSelected() ) {
                    constraints.append("\"examples\":[");
                    constraints.append(examplesTextField.getText());
                    constraints.append("],");
                }
                if ( valuesCheckBox.isSelected() ) {
                    constraints.append("\"values\":[");
                    constraints.append(valuesTextField.getText());
                    constraints.append("],");
                }
                if ( regexCheckBox.isSelected() ) {
                    constraints.append("\"regex\":");
                    constraints.append(maybeAddQuotes(regexTextField.getText()));
                    constraints.append(",");
                }
                sb.append( constraints.substring(0, constraints.length()-1 ) ); // remove the trailing comma
                sb.append( "}" );
            } else {
                sb.append( "[" ).append( valuesTextFieldNotMoreTF.getText() ).append("]");
                
            }
        }
        sb.append(")");
        return sb.toString();
        
    }
    
    public Object valueOf( Map<String,Object> s, String p, String deft ) {
        Object o= s.get(p);
        if ( o==null ) {
            return deft;
        } else {
            return o;
        }
    }
    
    /**
     * set the getParam call to be editted.
     * @param getParamCall
     */
    public void setValue( String getParamCall ) {
        String s= getParamCall;
        if ( s.startsWith("getParam(") ) s= s.substring(9);
        if ( s.endsWith(")") ) s= s.substring(0,s.length()-1);
        String[] ss= org.autoplot.jythonsupport.Util.guardedSplit( s, ',', '\'', '"' );
        parameterNameTextField.setText(maybeRemoveQuotes(ss[0]));
        defaultValueTextField.setText(maybeRemoveQuotes(ss[1]));
        if ( ss.length>2 ) {
            descriptionTextField.setText(maybeRemoveQuotes(ss[2]));
        }
        if ( ss.length>3 ) {
            String s4= String.join(",",Arrays.copyOfRange( ss, 3, ss.length ) ).trim();
            if ( s4.startsWith("[") ) {
                allowedValuesRB.setSelected(true);
                valuesTextFieldNotMoreTF.setText(s4.substring(1,s4.length()-2));
            } else {
                moreButton.setSelected(true);
                List<Param> pp= JythonUtil.getGetParams(s);
                Param p= pp.get(0);
                Map<String,Object> constraints= p.constraints;
                minTextField.setText((String)constraints.get("min"));
                maxTextField.setText((String)constraints.get("max"));
                valuesTextField.setText(String.valueOf(constraints.get("values")));
                examplesTextField.setText(String.valueOf(constraints.get("examples")));
                regexTextField.setText( (String)valueOf(constraints,"regex","") );
            }
        }
    }
    
    public static void main( String[] args ) {
        GetParamTool o= new GetParamTool();
        o.setValue("getParam(\"aaa\",10,\"This comma, filled string\", [10,20 ])" );
        o.updateConstraints();
        JOptionPane.showMessageDialog( null, o );
        System.err.println( o.getValue() );
    }

    private void updateConstraints() {
        constraintsPanel.removeAll();
        if ( allowedValuesRB.isSelected() ) {
            constraintsPanel.add( allowedValuesPanel, BorderLayout.CENTER );
        } else if ( moreButton.isSelected() ) {
            constraintsPanel.add( moreConstraintsPanel, BorderLayout.CENTER );
        }
        constraintsPanel.revalidate();
        
    }
    
    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of
     * this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        moreConstraintsPanel = new javax.swing.JPanel();
        minCheckBox = new javax.swing.JCheckBox();
        maxCheckBox = new javax.swing.JCheckBox();
        minTextField = new javax.swing.JTextField();
        maxTextField = new javax.swing.JTextField();
        examplesCheckBox = new javax.swing.JCheckBox();
        examplesTextField = new javax.swing.JTextField();
        valuesCheckBox = new javax.swing.JCheckBox();
        valuesTextField = new javax.swing.JTextField();
        regexCheckBox = new javax.swing.JCheckBox();
        regexTextField = new javax.swing.JTextField();
        allowedValuesPanel = new javax.swing.JPanel();
        valuesTextFieldNotMoreTF = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        parameterNameTextField = new javax.swing.JTextField();
        defaultValueTextField = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        descriptionTextField = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        allowedValuesRB = new javax.swing.JRadioButton();
        moreButton = new javax.swing.JRadioButton();
        constraintsPanel = new javax.swing.JPanel();
        jComboBox1 = new javax.swing.JComboBox<>();
        noneRadioButton = new javax.swing.JRadioButton();

        minCheckBox.setText("Min:");
        minCheckBox.setToolTipText("Minimum allowed value");

        maxCheckBox.setText("Max:");
        maxCheckBox.setToolTipText("Maximum allowed value");

        minTextField.setText("0");

        maxTextField.setText("100");

        examplesCheckBox.setText("Examples, where any of these are valid example values.");
        examplesCheckBox.setToolTipText("Example values, typically shown in droplist along with a field where any value can be entered.");

        examplesTextField.setText("jTextField6");

        valuesCheckBox.setText("Values, the script can only accept these values.");
        valuesCheckBox.setToolTipText("Enumeration of allowed values");

        valuesTextField.setText("jTextField7");

        regexCheckBox.setText("Regex:");

        regexTextField.setText("jTextField8");

        javax.swing.GroupLayout moreConstraintsPanelLayout = new javax.swing.GroupLayout(moreConstraintsPanel);
        moreConstraintsPanel.setLayout(moreConstraintsPanelLayout);
        moreConstraintsPanelLayout.setHorizontalGroup(
            moreConstraintsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(moreConstraintsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(moreConstraintsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(valuesTextField)
                    .addComponent(examplesTextField)))
            .addGroup(moreConstraintsPanelLayout.createSequentialGroup()
                .addComponent(minCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(minTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 115, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(305, 305, 305))
            .addGroup(moreConstraintsPanelLayout.createSequentialGroup()
                .addGroup(moreConstraintsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(moreConstraintsPanelLayout.createSequentialGroup()
                        .addComponent(maxCheckBox)
                        .addGap(27, 27, 27)
                        .addComponent(maxTextField)
                        .addGap(221, 221, 221))
                    .addComponent(examplesCheckBox)
                    .addGroup(moreConstraintsPanelLayout.createSequentialGroup()
                        .addComponent(regexCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(regexTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 255, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(valuesCheckBox))
                .addContainerGap(84, Short.MAX_VALUE))
        );
        moreConstraintsPanelLayout.setVerticalGroup(
            moreConstraintsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(moreConstraintsPanelLayout.createSequentialGroup()
                .addGap(2, 2, 2)
                .addGroup(moreConstraintsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(minTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(minCheckBox))
                .addGap(2, 2, 2)
                .addGroup(moreConstraintsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(maxCheckBox)
                    .addComponent(maxTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(examplesCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(examplesTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(valuesCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(valuesTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(moreConstraintsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(regexCheckBox)
                    .addComponent(regexTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 119, Short.MAX_VALUE))
        );

        valuesTextFieldNotMoreTF.setToolTipText("Enter a list of comma-separated values");
        valuesTextFieldNotMoreTF.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                valuesTextFieldNotMoreTFActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout allowedValuesPanelLayout = new javax.swing.GroupLayout(allowedValuesPanel);
        allowedValuesPanel.setLayout(allowedValuesPanelLayout);
        allowedValuesPanelLayout.setHorizontalGroup(
            allowedValuesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(allowedValuesPanelLayout.createSequentialGroup()
                .addComponent(valuesTextFieldNotMoreTF, javax.swing.GroupLayout.PREFERRED_SIZE, 436, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        allowedValuesPanelLayout.setVerticalGroup(
            allowedValuesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(allowedValuesPanelLayout.createSequentialGroup()
                .addComponent(valuesTextFieldNotMoreTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 81, Short.MAX_VALUE))
        );

        jLabel1.setText("Default Value:");

        jLabel2.setText("Parameter Name:");

        parameterNameTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                parameterNameTextFieldActionPerformed(evt);
            }
        });

        jLabel3.setText("Description:");

        descriptionTextField.setText(" ");

        jLabel4.setText("Constraints:");

        buttonGroup1.add(allowedValuesRB);
        allowedValuesRB.setText("Allowed Values");
        allowedValuesRB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allowedValuesRBActionPerformed(evt);
            }
        });

        buttonGroup1.add(moreButton);
        moreButton.setText("More...");
        moreButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moreButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout constraintsPanelLayout = new javax.swing.GroupLayout(constraintsPanel);
        constraintsPanel.setLayout(constraintsPanelLayout);
        constraintsPanelLayout.setHorizontalGroup(
            constraintsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        constraintsPanelLayout.setVerticalGroup(
            constraintsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 285, Short.MAX_VALUE)
        );

        jComboBox1.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "String", "Integer", "Real", "Boolean", " ", " " }));

        buttonGroup1.add(noneRadioButton);
        noneRadioButton.setSelected(true);
        noneRadioButton.setText("None");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel4)
                        .addGap(14, 14, 14)
                        .addComponent(noneRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(allowedValuesRB)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(moreButton)
                        .addGap(0, 76, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel2)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(parameterNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 81, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jLabel3)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(defaultValueTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(12, 12, 12)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(descriptionTextField)
                            .addComponent(constraintsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(parameterNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(1, 1, 1)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(defaultValueTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(descriptionTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(allowedValuesRB)
                    .addComponent(moreButton)
                    .addComponent(noneRadioButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(constraintsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void allowedValuesRBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allowedValuesRBActionPerformed
        updateConstraints();
    }//GEN-LAST:event_allowedValuesRBActionPerformed

    private void moreButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moreButtonActionPerformed
        updateConstraints();
    }//GEN-LAST:event_moreButtonActionPerformed

    private void parameterNameTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_parameterNameTextFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_parameterNameTextFieldActionPerformed

    private void valuesTextFieldNotMoreTFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_valuesTextFieldNotMoreTFActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_valuesTextFieldNotMoreTFActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel allowedValuesPanel;
    private javax.swing.JRadioButton allowedValuesRB;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JPanel constraintsPanel;
    private javax.swing.JTextField defaultValueTextField;
    private javax.swing.JTextField descriptionTextField;
    private javax.swing.JCheckBox examplesCheckBox;
    private javax.swing.JTextField examplesTextField;
    private javax.swing.JComboBox<String> jComboBox1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JCheckBox maxCheckBox;
    private javax.swing.JTextField maxTextField;
    private javax.swing.JCheckBox minCheckBox;
    private javax.swing.JTextField minTextField;
    private javax.swing.JRadioButton moreButton;
    private javax.swing.JPanel moreConstraintsPanel;
    private javax.swing.JRadioButton noneRadioButton;
    private javax.swing.JTextField parameterNameTextField;
    private javax.swing.JCheckBox regexCheckBox;
    private javax.swing.JTextField regexTextField;
    private javax.swing.JCheckBox valuesCheckBox;
    private javax.swing.JTextField valuesTextField;
    private javax.swing.JTextField valuesTextFieldNotMoreTF;
    // End of variables declaration//GEN-END:variables
}
