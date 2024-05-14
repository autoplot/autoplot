
package org.autoplot.datasource.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * JTextArea with light-gray prompt indicating its use.  This
 * is shown when the text area is empty.
 * @author jbf
 */
public class PromptTextArea extends JTextArea {
    
    JLabel promptLabel= new JLabel("enter text here");
    
    public PromptTextArea(  ) {
        this("enter text here");
    }
    
    public PromptTextArea( String label ) {
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
            promptLabel.setBounds( 0, 0, getWidth(), promptLabel.getFont().getSize() );
            scratch.translate( getInsets().left, 0 );
            promptLabel.paint(scratch);
            scratch.dispose();
        }        
    }



    public static void main( String[] args ) {
        JFrame f= new JFrame();
        JPanel p= new JPanel();
        p.setLayout( new BorderLayout() );
        PromptTextArea ta= new PromptTextArea("Enter something here");
        p.add( ta );
        p.add( new JTextField("set focus here"), BorderLayout.SOUTH );
        f.setContentPane(p);
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
