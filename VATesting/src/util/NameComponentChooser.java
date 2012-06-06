/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package util;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import javax.swing.JLabel;
import javax.swing.text.JTextComponent;
import org.netbeans.jemmy.ComponentChooser;

/**
 * Search for a component based on the name.
 * @author jbf
 */
public class NameComponentChooser implements ComponentChooser {

    String regex;

    public NameComponentChooser( String regex ) {
        this.regex= regex;
    }

    public boolean checkComponent(Component comp) {
        String n= comp.getName();
        return ( n!=null && n.matches(regex) );
    }

    public String getDescription() {
        return "Regex Name";
    }

}
