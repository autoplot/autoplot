/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

/**
 * Textfield with help text when empty and not focused.
 * @author jbf
 */
public class PromptTextField extends JTextField {

    JLabel promptLabel= new JLabel("enter text here");

    public PromptTextField( String label ) {
        super(40);
        this.promptLabel.setText( label );
        this.promptLabel.setForeground( Color.GRAY );
        this.addFocusListener( createFocusListener() );
        promptLabel.addNotify();
    }

    public void setPromptText( String text ) {
        promptLabel.setText(text);
        repaint();
    }

    public String getPromptText() {
        return promptLabel.getText();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        String txt= getText();
        if ( ( txt==null || txt.length()==0 ) && !hasFocus() ) {
            Graphics scratch = g.create();
            promptLabel.setBounds( 0, 0, getWidth(), getHeight() );
            scratch.translate( getInsets().left, 0 );
            promptLabel.paint(scratch);
            scratch.dispose();
            
        }        
    }



    public static void main( String[] args ) {
        JFrame f= new JFrame();
        f.add( new PromptTextField( "Enter text") );
        f.pack();
        f.setVisible(true);

    }

    private FocusListener createFocusListener() {
        return new FocusListener() {
            public void focusGained(FocusEvent e) {
                repaint( getBounds() );
            }
            public void focusLost(FocusEvent e) {
                repaint( getBounds() );
            }
        };
    }
}
