/*
 * HelpDisplay.java
 *
 * Created on July 20, 2007, 10:03 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import javax.swing.JFrame;
import javax.swing.JLabel;

/**
 *
 * @author jbf
 */
public class HelpDisplay {
    
    JFrame frame;
    JLabel label;
    String resource;
    
    static HelpDisplay instance;
    
    public static synchronized HelpDisplay getDefault(  ) {
        if ( instance==null ) instance= new HelpDisplay(  );
        return instance;
    }
    
    /** Creates a new instance of HelpDisplay */
    private HelpDisplay(  ) {
        frame= new JFrame("autoplotter help");
        frame.setVisible(false);
        frame.setDefaultCloseOperation( JFrame.HIDE_ON_CLOSE );
        
        label= new JLabel( "<html><body><em>Help will go here</em></body></html>" );
        label.setAlignmentY( Component.TOP_ALIGNMENT );
        
        frame.getContentPane().add( label );
        frame.pack();
        
    }
    
    private String readStream( InputStream in ) throws IOException {
        StringBuffer buf= new StringBuffer(1000);
        BufferedReader reader= new BufferedReader( new InputStreamReader( in ) );
        String s= reader.readLine();
        while ( s!=null ) {
            buf.append(s);
            s= reader.readLine();
        }
        return buf.toString();
    }
    
    public void display( String resource ) {
        this.resource= resource;
        
        String helpHtml;
        try {
            helpHtml= readStream( HelpDisplay.class.getResourceAsStream( resource ) );
        } catch ( IOException e ) {
            helpHtml= "unable to read help.";
        }
        
        label.setText( helpHtml );
        
        frame.pack();
        frame.setVisible(true);
    }
}
