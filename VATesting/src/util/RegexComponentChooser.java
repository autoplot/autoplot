/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package util;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.text.JTextComponent;
import org.netbeans.jemmy.ComponentChooser;

/**
 * Search for a component based on the name.
 * @author jbf
 */
public class RegexComponentChooser implements ComponentChooser {

    String regex;

    public RegexComponentChooser( String regex ) {
        this.regex= regex;
    }

    public boolean checkComponent(Component comp) {
        String n= null;
        if ( n==null && comp instanceof Dialog ) {
            n= ((Dialog)comp).getTitle();
        } else if ( n==null && comp instanceof Frame ) {
            n= ((Frame)comp).getTitle();
        } else if ( n==null && comp instanceof JTextComponent ) {
            n= ((JTextComponent)comp).getText();
        } else if ( n==null && comp instanceof JLabel ) {
            n= ((JLabel)comp).getText();
        } else if ( n==null && comp instanceof JButton ) {
            n= ((JButton)comp).getText();
        }

        return ( n!=null && n.matches(regex) );
    }

    public String getDescription() {
        return "Regex in Text or Title of Dialog or Frame";
    }

}
