/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.util.jemmy;

import java.awt.Component;
import org.netbeans.jemmy.ComponentChooser;

/**
 * Search for a component based on the name.
 * @author jbf
 */
public class NameComponentChooser implements ComponentChooser {

    String name;

    public NameComponentChooser( String regex ) {
        this.name= regex;
    }

    @Override
    public boolean checkComponent(Component comp) {
        String n= comp.getName();
        return ( n!=null && n.equals(name) );
    }

    @Override
    public String getDescription() {
        return "Regex Name";
    }

}
